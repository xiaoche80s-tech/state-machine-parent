## Context

当前 `StateMachine.executeLoop()` 在 `findNextState()` 返回空时（第 196-198 行）直接将实例标记为 `COMPLETED`。这个逻辑混淆了两种不同的结束条件：

1. **正常完成**：当前状态没有任何出边过渡（真正的终端状态）
2. **路由失败**：当前状态定义了出边过渡，但所有过渡的条件都不匹配

第二种情况被错误地标记为 `COMPLETED`，导致实例在中间状态显示"已完成"，造成运维误判。

## Goals / Non-Goals

**Goals:**
- 正确区分"正常完成"和"路由失败"两种结束条件
- 路由失败时标记为 `FAILED`，错误信息包含当前状态名和可用过渡目标
- 保持 `COMPLETED` 语义：仅当状态无任何出边时触发

**Non-Goals:**
- 不修改前端状态展示逻辑（已有 FAILED 展示）
- 不改变 `findNextState()` 的遍历顺序或条件匹配算法
- 不涉及数据库 schema 变更

## Decisions

### 决策 1：如何判断"有出边但无匹配"

在 `findNextState()` 返回空后，增加一步检查：遍历 transitions 判断当前状态是否有任何出边定义。如果有出边但无匹配条件 → `FAILED`；如果无出边 → `COMPLETED`。

**理由**：最小改动，不需要修改 `findNextState()` 的返回值类型或接口契约。将"是否存在出边"的判断放在调用方，而不是嵌入 `findNextState()`。

**替代方案**：让 `findNextState()` 返回一个包含更多信息的结果对象（如 `Optional<String> + boolean hasTransitions`）。被拒绝——增加复杂度，且当前只有一个调用点。

### 决策 2：错误信息格式

路由失败时，错误消息格式为：`No matching transition from state '{stateName}'. Available transitions: {to1}, {to2}, ...`。如果没有任何出边则不列出。

**理由**：便于排查——开发者看到状态名就知道卡在哪个节点，看到可用过渡就知道定义了哪些路径但条件不匹配。

### 决策 3：是否保存 ROUTE 快照

路由失败时不保存 ROUTE 快照（因为没有成功的路由），与当前行为一致。已在 `executeLoop()` 的 catch 块中有 `saveRouteFailed()` 模式可参考，但此处是逻辑性失败而非异常，直接更新状态即可。

## Risks / Trade-offs

- **[Risk] 兼容性影响**：之前被误标为 `COMPLETED` 的实例在新版本中将变为 `FAILED`。已有数据不会回退，只影响新版本运行的实例。
  → **Mitigation**: 这是修复行为，不是破坏性变更。用户之前看到的"已完成"是 bug 产物。

- **[Risk] 误杀合法终端状态**：如果用户定义了一个状态但忘记加任何过渡，原本会 `COMPLETED`，现在也会 `COMPLETED`（因为无出边）。逻辑正确，不受影响。

- **[Trade-off] 性能**：每次 `findNextState()` 返回空后需要额外遍历一次 transitions 来检查是否有出边。O(n) 开销，n 是过渡总数，通常 < 20，可忽略。
