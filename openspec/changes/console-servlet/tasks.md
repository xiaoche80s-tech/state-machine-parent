## 1. 配置属性

- [x] 1.1 `StateMachineProperties.Console` 新增 `urlPattern` 字段（默认 `/statemachine/*`），添加 getter/setter

## 2. 新建 StateMachineConsoleServlet

- [x] 2.1 在 `management` 包下创建 `StateMachineConsoleServlet extends HttpServlet`
- [x] 2.2 构造函数接收 5 个依赖：`StateMachineRegistry`, `InstanceRepository`, `SnapshotRepository`, `InstanceExecutionService<Object>`, `ObjectMapper`
- [x] 2.3 实现 `doGet()`：路由分发（index.html / api/*.json / css/js 静态资源 / 404）
- [x] 2.4 实现 `process()` 方法：switch 匹配 4 个 GET API（machines.json / versions.json / instances.json / instance.json），迁移 `ConsoleController` 对应业务逻辑
- [x] 2.5 实现 `doPost()`：读 JSON body，switch 匹配 2 个 POST API（resume.json / retry.json），迁移 `ConsoleController` 对应业务逻辑
- [x] 2.6 实现 `serveResource()` 方法：从 classpath `/console/` 读文件，根据后缀设 Content-Type，写 response

## 3. 自动配置改造

- [x] 3.1 `ConsoleConfiguration` 条件注解从 `@ConditionalOnClass(DispatcherServlet.class)` 改为 `@ConditionalOnClass(javax.servlet.Servlet.class)`
- [x] 3.2 `ConsoleConfiguration` 的 `@Bean` 方法改为返回 `ServletRegistrationBean<StateMachineConsoleServlet>`，构造函数注入依赖，使用 `properties.getConsole().getUrlPattern()` 作为 URL 映射
- [x] 3.3 删除 `ConsoleController.java`

## 4. 静态资源迁移

- [x] 4.1 将 `resources/static/statemachine/` 下所有文件移动到 `resources/console/`
- [x] 4.2 删除空的 `resources/static/` 目录（如果没有其他用途）

## 5. 前端适配

- [x] 5.1 改写 `api.js`：所有 API 路径改为相对路径 + `.json` 后缀（`api/machines.json` 等），POST 请求改为 JSON body 传参
- [x] 5.2 检查 `app.js` 中是否有硬编码的绝对路径（如 `/statemachine`），改为相对路径或移除
- [x] 5.3 检查 `index.html` 中 css/js 引用是否全部为相对路径（当前已是相对路径，确认无遗漏）

## 6. Demo 适配

- [x] 6.1 更新 `state-machine-demo` 的 `application.yml`（如有 context-path 配置，确认控制台可正常访问）
- [x] 6.2 启动 demo 验证控制台页面加载、API 调用、恢复/重试操作正常

## 7. 测试

- [x] 7.1 运行 `mvn test` 确保所有现有测试通过（非集成测试全部通过，集成测试因 MySQL 不可达跳过）
- [x] 7.2 启动 demo 端到端验证：概览页、状态机详情、实例列表、实例详情、重试、恢复
