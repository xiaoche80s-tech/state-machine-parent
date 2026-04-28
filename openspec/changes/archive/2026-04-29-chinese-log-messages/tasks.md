## Context

19 处日志消息分布在 7 个文件中，全部使用英文。需要改为中文。

## Tasks

### Task 1: InstanceExecutionService 日志中文化

**Files:**
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java`

- [x] **Step 1: 替换 4 处日志为中文**

修改以下日志消息：

- 行 211: `log.error("状态 '{}' 执行失败 (实例 {}, 第 {} 次尝试)", stateName, instanceId, attempt, e);`
- 行 228: `log.error("状态 '{}' 耗尽 {} 次重试 (实例 {})", stateName, retryCount + 1, instanceId);`
- 行 270: `log.error("状态转换从 '{}' 失败 (实例 {})", stateName, instanceId, e);`
- 行 292: `log.warn("序列化对象失败", e);`

- [x] **Step 2: 验证编译**

Run: `mvn compile -pl state-machine-boot-starter`
Expected: BUILD SUCCESS

### Task 2: 其他 6 个文件日志中文化

**Files:**
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/core/StateMachineRegistry.java`
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/management/ConsoleController.java`
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/management/StateMachineEndpoint.java`
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/autoconfigure/StateMachineAutoConfiguration.java`
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/persistence/DdlInitializer.java`
- Modify: `state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/infrastructure/persistence/JdbcInstanceRepository.java`

- [x] **Step 1: StateMachineRegistry 替换 2 处**

- 行 56: `log.warn("序列化状态机重试策略失败 {}:{}", name, version, e);`
- 行 70: `log.info("注册状态机 {}:{}", name, version);`

- [x] **Step 2: ConsoleController 替换 3 处**

- 行 140: `log.error("恢复实例 {} 时解析 contextJson 失败", id, e);`
- 行 152: `log.error("恢复实例失败 id={}", id, e);`
- 行 170: `log.error("重试实例失败 id={}", id, e);`

- [x] **Step 3: StateMachineEndpoint 替换 1 处**

- 行 60: `log.warn("解析状态机定义失败 {}", name, e);`

- [x] **Step 4: StateMachineAutoConfiguration 替换 3 处**

- 行 86: `log.info("自动配置门面: {}", beanName);`
- 行 116: `log.info("管理端点已启用");`
- 行 131: `log.info("控制台已启用 /statemachine");`

- [x] **Step 5: DdlInitializer 替换 4 处**

- 行 22: `log.info("DDL 自动创建已禁用 (ddl-auto={})", ddlAuto);`
- 行 31: `log.warn("数据库 '{}' 无 DDL 脚本，回退使用 H2", dbType);`
- 行 37: `log.info("数据表初始化完成，使用 {}", resourcePath);`
- 行 40: `log.error("数据表初始化失败", e);`

- [x] **Step 6: JdbcInstanceRepository 替换 2 处**

- 行 28: `log.warn("按 ID 查询实例失败 id={}", id, e);`
- 行 40: `log.warn("按 businessId 查询实例失败 businessId={} machineName={}", businessId.value(), machineName.value(), e);`

- [x] **Step 7: 验证编译和测试**

Run: `mvn test -pl state-machine-boot-starter`
Expected: BUILD SUCCESS, 69 tests pass

### Task 3: 提交

- [x] **Step 1: 提交**

```bash
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/application/InstanceExecutionService.java
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/core/StateMachineRegistry.java
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/management/ConsoleController.java
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/management/StateMachineEndpoint.java
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/autoconfigure/StateMachineAutoConfiguration.java
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/persistence/DdlInitializer.java
git add state-machine-boot-starter/src/main/java/cn/chedejun/statemachine/infrastructure/persistence/JdbcInstanceRepository.java
git commit -m "$(cat <<'EOF'
refactor: 将所有日志消息改为中文

19 处 log.info/warn/error 英文消息替换为中文文本，
提升生产排障可读性。日志级别、占位符、异常堆栈不变。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```
