## ADDED Requirements

### Requirement: 日志消息使用中文

系统所有日志消息（log.info/warn/error/debug）SHALL 使用中文文本，以提升生产环境排障可读性。

#### Scenario: 状态机执行日志
- **WHEN** 状态机执行失败
- **THEN** 日志消息包含中文描述，如 "状态 '{}' 执行失败 (实例 {}, 第 {} 次尝试)"

#### Scenario: 状态机注册日志
- **WHEN** 状态机注册
- **THEN** 日志消息包含中文描述，如 "注册状态机 {}:version"

#### Scenario: 数据库操作日志
- **WHEN** 数据库查询失败
- **THEN** 日志消息包含中文描述，如 "按 ID 查询实例失败 id={}"
