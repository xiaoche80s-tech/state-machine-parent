## Why

当前 `ConsoleController` 依赖 Spring MVC 的 `DispatcherServlet`，导致 Web 控制台只能在引入 `spring-boot-starter-web` 的 Spring MVC 应用中使用。在 WebFlux、Jersey、或纯 Servlet 环境下，控制台不可用。

项目作为一个可嵌入的 Spring Boot Starter 库，应尽可能降低对宿主应用的技术栈要求。改为原生 Servlet 实现后，只要有 `javax.servlet` API 即可工作，与 Spring Boot 2.7.x 的 Servlet 容器天然兼容。

## What Changes

- 删除 `ConsoleController`（`@Controller`），新增 `StateMachineConsoleServlet`（`extends HttpServlet`）
- 通过 `ServletRegistrationBean` 动态注册 Servlet，不再依赖 `DispatcherServlet` 路由
- API 路由从 RESTful 风格（`/api/machines/{name}/versions`）改为 Druid 风格扁平路径（`/api/versions.json?name=xxx`），消除路径参数
- POST 接口使用 JSON body 传参
- 静态资源从 `resources/static/statemachine/` 挪到 `resources/console/`，由 Servlet 自己从 classpath 读取
- 前端 `api.js` 全部使用相对路径，零配置适配 `server.servlet.context-path` 和网关前缀
- 自动配置条件从 `@ConditionalOnClass(DispatcherServlet.class)` 放宽为 `@ConditionalOnClass(Servlet.class)`
- 新增 `url-pattern` 配置项，默认 `/statemachine/*`，支持自定义

## Capabilities

### New Capabilities
- `console-servlet`: Web 控制台以原生 HttpServlet 实现，解除 Spring MVC 依赖，通过 ServletRegistrationBean 动态注册

### Modified Capabilities
- `console`: 控制台路由方式变更（RESTful → 扁平 .json 路径），静态资源目录变更

## Impact

- `ConsoleController.java` — 删除
- `StateMachineConsoleServlet.java` — 新增（management 包下）
- `StateMachineAutoConfiguration.java` — `ConsoleConfiguration` 内部类重写
- `StateMachineProperties.java` — `Console` 内部类新增 `urlPattern` 字段
- `resources/static/statemachine/` → `resources/console/` — 静态资源目录迁移
- `api.js` — 所有 API 路径改为相对路径 + `.json` 后缀
- `app.js` — 检查是否有硬编码路径需要调整
- `index.html` — css/js 引用改为相对路径
- `state-machine-demo` — 可能需要适配新的 API 路径
