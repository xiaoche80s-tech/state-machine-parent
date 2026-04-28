## Context

当前执行快照时间线对所有快照使用相同的渲染模板，不区分 `snapshotType`。ROUTE 快照（状态转换记录）的 `stateName` 存储的是 fromState，`output` 存储的是目标状态名（JSON 包裹的字符串，如 `"process"`）。用户无法从 UI 中直观看到状态流转路径。

## Goals / Non-Goals

**Goals:**
- 在时间线中为 ROUTE 快照使用独特的视觉样式，展示 fromState → toState 的转换
- 与现有 NODE 快照样式区分开
- 复用现有数据，无需后端改动

**Non-Goals:**
- 不修改后端 API 或数据库结构
- 不改变 NODE 快照的现有展示
- 不改变快照的排序或过滤逻辑

## Decisions

1. **前端模板分支渲染**：在 `v-for` 中使用 `v-if` / `v-else` 区分 NODE 和 ROUTE 快照。ROUTE 快照渲染为紧凑的转换箭头样式，显示 `fromState → toState`。
2. **数据解析**：ROUTE 快照的 `output` 字段是 JSON 包裹的目标状态名（如 `"process"`），需要 `JSON.parse(s.output)` 解包。若解析失败则回退显示原始值。
3. **样式**：使用现有的 timeline-item 结构，但用不同的 marker 图标（箭头而非勾/叉）和更紧凑的内容区，不展示 IN/OUT JSON 块。

## Risks / Trade-offs

- [ROUTE output 解析失败] → 回退显示原始 output 值，不影响页面渲染
- [紧凑样式可能信息不足] → 未来可考虑展开显示 context 详情，当前保持简洁
