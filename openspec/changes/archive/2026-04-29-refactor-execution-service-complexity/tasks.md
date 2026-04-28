## Context

InstanceExecutionService.java 复杂度重构，包含 P1（拆分 executeLoop）、P2（提取 resume）、P3（save 返回值消除冗余查询）。

## Tasks

### Task 1: 实施重构

**Files:**
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java`

- [x] **Step 1: 重写 InstanceExecutionService**

- 提取 `executeAction()` 私有方法（Action 执行 + 成功/失败快照 + 重试循环）
- 提取 `resume()` 公共方法（resumeByBusinessId 和 resumeByInstanceId 的公共逻辑）
- 提取 `buildTransitionError()` 辅助方法
- executeLoop 入口查一次 current，后续复用 save 返回值

- [x] **Step 2: 修复编译错误**

- `buildTransitionError` 参数改为 `List<? extends Transition>`
- lambda 引用 current 使用 final 变量捕获

- [x] **Step 3: 验证编译和测试**

Run: `mvn test -pl state-machine-boot-starter`
Expected: BUILD SUCCESS, 69 tests pass

- [x] **Step 4: 提交**

```bash
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java
git commit -m "refactor: 降低 InstanceExecutionService 复杂度（P1+P2+P3）"
```
