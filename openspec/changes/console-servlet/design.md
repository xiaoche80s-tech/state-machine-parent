## 架构设计

参考 Druid `StatViewServlet` 模式，将 Web 控制台从 Spring MVC `@Controller` 改写为原生 `HttpServlet`，通过 `ServletRegistrationBean` 动态注册。

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    宿主 Spring Boot 应用                      │
│                                                             │
│  context-path: /app-a                                       │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  StateMachineConsoleServlet (extends HttpServlet)    │   │
│  │  映射: /statemachine/* (可配)                        │   │
│  │                                                      │   │
│  │  GET  请求:                                          │   │
│  │    /                → index.html                     │   │
│  │    /api/*.json      → process(url) → JSON 响应       │   │
│  │    /css/*, /js/*    → 从 classpath 读静态资源         │   │
│  │                                                      │   │
│  │  POST 请求:                                          │   │
│  │    /api/*.json      → 读 JSON body → 执行操作        │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  注册方式: ServletRegistrationBean                           │
│  条件: @ConditionalOnClass(Servlet.class)                    │
└─────────────────────────────────────────────────────────────┘
```

### 请求路径解析

Servlet 容器自动处理 `context-path`，Servlet 内部只需关注 `pathInfo`：

```
完整 URL: http://domain/app-a/statemachine/api/machines.json

req.getContextPath()   → "/app-a"          (context-path，Servlet 不关心)
req.getServletPath()   → "/statemachine"   (Servlet 映射前缀)
req.getPathInfo()      → "/api/machines.json"  ← Servlet 只读这个
```

### 路由表

所有路由扁平化，无路径参数，通过查询参数传值：

| HTTP 方法 | pathInfo | 处理逻辑 |
|-----------|----------|----------|
| GET | `/` 或 `""` | serveResource("index.html") |
| GET | `/api/machines.json` | listMachines() |
| GET | `/api/versions.json?name=xxx` | getVersions(name) |
| GET | `/api/instances.json?name=xxx&status=...&businessId=...&instanceId=...&page=...&size=...` | getInstances(...) |
| GET | `/api/instance.json?id=xxx` | getInstanceDetail(id) |
| GET | `/css/*`, `/js/*` | serveResource(path) |
| POST | `/api/resume.json` | resumeInstance(JSON body) |
| POST | `/api/retry.json` | retryInstance(JSON body) |

### Servlet 内部路由实现

```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
    String path = req.getPathInfo();
    if (path == null) path = "/";

    if ("/".equals(path) || "".equals(path)) {
        serveResource("/index.html", resp);
        return;
    }

    if (path.startsWith("/api/") && path.endsWith(".json")) {
        resp.setContentType("application/json;charset=utf-8");
        String json = process(path, req);
        resp.getWriter().write(json);
        return;
    }

    if (path.startsWith("/css/") || path.startsWith("/js/")) {
        serveResource(path, resp);
        return;
    }

    resp.sendError(404);
}

private String process(String path, HttpServletRequest req) {
    switch (path) {
        case "/api/machines.json":   return listMachines();
        case "/api/versions.json":   return getVersions(req.getParameter("name"));
        case "/api/instances.json":  return getInstances(req);
        case "/api/instance.json":   return getInstanceDetail(req.getParameter("id"));
        default: return errorJson("Unknown API: " + path);
    }
}

@Override
protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
    String path = req.getPathInfo();
    resp.setContentType("application/json;charset=utf-8");

    Map<String, Object> body = objectMapper.readValue(req.getInputStream(), Map.class);

    String json;
    switch (path) {
        case "/api/resume.json": json = resumeInstance(body); break;
        case "/api/retry.json":  json = retryInstance(body);  break;
        default: json = errorJson("Unknown API: " + path);
    }
    resp.getWriter().write(json);
}
```

### 依赖注入

构造函数注入，在 `@Bean` 方法中传入：

```java
@Bean
public ServletRegistrationBean<StateMachineConsoleServlet> consoleServlet(
        StateMachineRegistry registry,
        InstanceRepository instanceRepo,
        SnapshotRepository snapshotRepo,
        InstanceExecutionService<Object> executionService,
        ObjectMapper objectMapper) {

    StateMachineConsoleServlet servlet = new StateMachineConsoleServlet(
        registry, instanceRepo, snapshotRepo, executionService, objectMapper);

    String urlPattern = properties.getConsole().getUrlPattern();
    ServletRegistrationBean<StateMachineConsoleServlet> bean =
        new ServletRegistrationBean<>(servlet, urlPattern);
    bean.setLoadOnStartup(1);
    return bean;
}
```

### 静态资源服务

资源位于 classpath 的 `/console/` 目录下，Servlet 自己读取并写入响应：

```java
private void serveResource(String path, HttpServletResponse resp)
        throws IOException {
    String resourcePath = "/console" + path;
    InputStream is = getClass().getResourceAsStream(resourcePath);
    if (is == null) {
        resp.sendError(404);
        return;
    }

    // 根据后缀设置 Content-Type
    if (path.endsWith(".html")) resp.setContentType("text/html;charset=utf-8");
    else if (path.endsWith(".css")) resp.setContentType("text/css;charset=utf-8");
    else if (path.endsWith(".js")) resp.setContentType("application/javascript;charset=utf-8");

    // 缓存头
    resp.setHeader("Cache-Control", "max-age=3600");

    // 写出
    try (InputStream in = is; OutputStream out = resp.getOutputStream()) {
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
    }
}
```

### 前端 API 路径

全部使用相对路径，零配置：

```javascript
// api.js
async getMachines() {
    return fetch('api/machines.json').then(r => r.json());
}

async getVersions(name) {
    return fetch('api/versions.json?name=' + encodeURIComponent(name))
        .then(r => r.json());
}

async getInstances(name, status, businessId, instanceId, page, size) {
    let url = 'api/instances.json?name=' + encodeURIComponent(name);
    if (status) url += '&status=' + encodeURIComponent(status);
    if (businessId) url += '&businessId=' + encodeURIComponent(businessId);
    if (instanceId) url += '&instanceId=' + encodeURIComponent(instanceId);
    url += '&page=' + page + '&size=' + size;
    return fetch(url).then(r => r.json());
}

async getInstanceDetail(id) {
    return fetch('api/instance.json?id=' + encodeURIComponent(id))
        .then(r => r.json());
}

async retryInstance(id) {
    return fetch('api/retry.json', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: id })
    }).then(r => r.json());
}

async resumeInstance(id, expectedCurrentState, contextJson) {
    return fetch('api/resume.json', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            id: id,
            expectedCurrentState: expectedCurrentState,
            contextJson: contextJson
        })
    }).then(r => r.json());
}
```

### index.html 静态资源引用

```html
<!-- 全部相对路径 -->
<link rel="stylesheet" href="css/style.css">
<script src="js/vue.global.prod.js"></script>
<script src="js/mermaid.min.js"></script>
<script src="js/api.js"></script>
<script src="js/app.js"></script>
```

### 配置属性

```yaml
state-machine:
  console:
    enabled: true                    # 开关，默认 true
    url-pattern: /statemachine/*     # Servlet 映射，默认 /statemachine/*
```

```java
// StateMachineProperties.Console
public static class Console {
    private boolean enabled = true;
    private String urlPattern = "/statemachine/*";
    // getters + setters
}
```

### 自动配置条件

```java
@Configuration
@ConditionalOnClass(javax.servlet.Servlet.class)
@ConditionalOnProperty(prefix = "state-machine.console",
    name = "enabled", havingValue = "true", matchIfMissing = true)
static class ConsoleConfiguration {
    // @Bean ServletRegistrationBean
}
```

从 `DispatcherServlet.class` 放宽到 `Servlet.class`，解除 Spring MVC 强依赖。
