## Why

流程详情快照目前只显示 NODE 类型的快照记录，ROUTE 类型（状态转换记录）未被展示。用户无法在 UI 中看到状态机实例的状态流转路径，只能看到每个状态的动作执行结果，缺少状态之间如何转换的关键信息。

## What Changes

- 在实例详情快照时间线中展示 ROUTE 类型快照
- ROUTE 快照以区别于 NODE 的视觉样式呈现，展示 fromState → toState 的转换关系
- 保持现有 NODE 快照的展示不变

## Capabilities

### New Capabilities
- `route-snapshot-display`: 在流程详情时间线中渲染 ROUTE 类型快照，展示状态转换路径

### Modified Capabilities
<!-- 无现有能力需要修改 -->

## Impact

- `index.html`: 快照时间线模板需要增加对 ROUTE 类型的渲染逻辑
- `app.js`: 可能需要辅助函数来格式化 ROUTE 快照的展示数据
- 不影响后端 API 和数据结构，`snapshotType` 字段已存在于 API 响应中
