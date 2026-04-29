## 1. 版本降级

- [ ] 1.1 修改根 pom.xml：java.version 17→8，spring-boot.version 3.2.5→2.7.18
- [ ] 1.2 修改 state-machine-boot-starter/pom.xml：同步版本配置
- [ ] 1.3 修改 state-machine-demo/pom.xml：同步版本配置
- [ ] 1.4 验证 `mvn clean compile` 通过（此时可能有语法错误，先确认依赖可解析）

## 2. record → 传统 class 转换

- [ ] 2.1 转换 domain/shared 值对象 record（BusinessId, DefinitionId, InstanceId, MachineName, SnapshotId, StateName）为不可变 class
- [ ] 2.2 转换 domain/data 数据 record（DefinitionData, InstanceData, SnapshotData）为不可变 class
- [ ] 2.3 转换 domain/event 事件 record（InstanceCompletedEvent, InstanceFailedEvent, InstanceStartedEvent, InstanceSuspendedEvent）为不可变 class
- [ ] 2.4 转换 management/dto 传输对象 record（InstanceDTO, MachineDefinitionDTO, MachineDTO, SnapshotDTO）为不可变 class
- [ ] 2.5 转换 core/ExecuteResult record 为不可变 class
- [ ] 2.6 验证所有转换后的 class 包含正确的 equals/hashCode/toString

## 3. var → 显式类型替换

- [ ] 3.1 替换 state-machine-boot-starter/src/main/java 中 20 处 var
- [ ] 3.2 替换 state-machine-boot-starter/src/test/java 中 43 处 var
- [ ] 3.3 替换 state-machine-demo/src/main/java 中 6 处 var

## 4. 集合工厂方法替换

- [ ] 4.1 替换所有 List.of() → Collections.unmodifiableList(Arrays.asList())
- [ ] 4.2 替换所有 Set.of() → Collections.unmodifiableSet(new HashSet<>(Arrays.asList()))
- [ ] 4.3 替换所有 Map.of() → Collections.unmodifiableMap(new HashMap<>(...))

## 5. Spring Boot 3→2.7 API 适配

- [ ] 5.1 检查并修复 AutoConfiguration.imports 文件格式（SB2 使用 spring.factories）
- [ ] 5.2 检查并修复条件注解 API 差异
- [ ] 5.3 检查并修复 Actuator 端点 API 差异
- [ ] 5.4 检查并修复 JDBC/JdbcTemplate API 差异

## 6. 测试验证

- [ ] 6.1 修复测试中的 JDK 9+ 语法
- [ ] 6.2 运行 `mvn test` 确保全部通过
- [ ] 6.3 运行 demo 项目验证端到端功能
