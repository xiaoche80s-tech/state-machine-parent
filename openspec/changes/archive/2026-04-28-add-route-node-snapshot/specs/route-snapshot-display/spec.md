## ADDED Requirements

### Requirement: 时间线中渲染 ROUTE 快照
当快照的 `snapshotType` 为 `ROUTE` 时，系统必须以区别于 NODE 的样式展示状态转换信息。每个 ROUTE 快照 SHALL 显示 fromState → toState 的转换路径，不展示 IN/OUT JSON 块。

#### Scenario: 成功路由的 ROUTE 快照显示
- **WHEN** 用户打开包含 ROUTE 快照的实例详情
- **THEN** 时间线中 ROUTE 快照以箭头样式显示 fromState → toState

#### Scenario: 失败路由的 ROUTE 快照显示
- **WHEN** ROUTE 快照状态为 FAILED
- **THEN** 显示 fromState 及错误信息，标记转换失败

### Requirement: 快照计数包含 ROUTE
实例详情页的快照计数 SHALL 包含所有类型的快照（NODE + ROUTE），显示总数。

#### Scenario: 混合快照计数
- **WHEN** 一个实例有 3 个 NODE 快照和 2 个 ROUTE 快照
- **THEN** 快照计数显示 "5 步"

### Requirement: ROUTE 快照输出解析
对于 ROUTE 快照，系统 MUST 将 `output` 字段（JSON 包裹的目标状态名）解析为纯文本状态名。若 JSON 解析失败，回退显示原始值。

#### Scenario: 正常解析目标状态
- **WHEN** ROUTE 快照的 output 为 `"process"`
- **THEN** 显示为 process（无引号）

#### Scenario: JSON 解析失败回退
- **WHEN** ROUTE 快照的 output 不是合法 JSON
- **THEN** 直接显示原始 output 值
