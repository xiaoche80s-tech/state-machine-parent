## 1. 替换 String.isBlank()

- [x] 1.1 ConsoleController.java 中 2 处 isBlank() 替换为 JDK 8 兼容写法
- [x] 1.2 Transition.java 中 2 处 isBlank() 替换
- [x] 1.3 StateMachineBuilder.java 中 1 处 isBlank() 替换
- [x] 1.4 State.java 中 1 处 isBlank() 替换
- [x] 1.5 InstanceExecutionService.java 中 1 处 isBlank() 替换
- [x] 1.6 值对象文件（StateName、InstanceId、DefinitionId、MachineName、SnapshotId）中 isBlank() 替换

## 2. 替换 Optional.isEmpty()

- [x] 2.1 ConsoleController.java 中 2 处 isEmpty() on Optional 替换为 `!isPresent()`

## 3. 替换 InputStream.readAllBytes()

- [x] 3.1 DdlInitializer.java 中 readAllBytes() 替换为 ByteArrayOutputStream 读取

## 4. 构建验证

- [x] 4.1 运行 `mvn clean compile` 验证 JDK 8 编译成功
