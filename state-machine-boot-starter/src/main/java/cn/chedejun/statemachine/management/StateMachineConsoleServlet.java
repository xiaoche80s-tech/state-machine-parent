package cn.chedejun.statemachine.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.chedejun.statemachine.application.InstanceExecutionService;
import cn.chedejun.statemachine.core.StateMachineRegistry;
import cn.chedejun.statemachine.domain.engine.StateMachine;
import cn.chedejun.statemachine.domain.repository.InstanceRepository;
import cn.chedejun.statemachine.domain.repository.SnapshotRepository;
import cn.chedejun.statemachine.domain.shared.BusinessId;
import cn.chedejun.statemachine.domain.shared.InstanceId;
import cn.chedejun.statemachine.domain.shared.InstanceStatus;
import cn.chedejun.statemachine.domain.shared.MachineName;
import cn.chedejun.statemachine.domain.shared.StateName;
import cn.chedejun.statemachine.domain.data.DefinitionData;
import cn.chedejun.statemachine.domain.data.InstanceData;
import cn.chedejun.statemachine.domain.data.SnapshotData;
import cn.chedejun.statemachine.management.dto.InstanceDTO;
import cn.chedejun.statemachine.management.dto.MachineDefinitionDTO;
import cn.chedejun.statemachine.management.dto.SnapshotDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 状态机控制台 Servlet，提供 Web UI 和 REST API。
 * <p>
 * 参考 Druid StatViewServlet 模式，通过 ServletRegistrationBean 动态注册，
 * 解除对 Spring MVC DispatcherServlet 的依赖。
 */
public class StateMachineConsoleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(StateMachineConsoleServlet.class);
    private static final int MAX_PAGE_SIZE = 200;

    private final StateMachineRegistry registry;
    private final InstanceRepository instanceRepository;
    private final SnapshotRepository snapshotRepository;
    private final InstanceExecutionService<Object> executionService;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public StateMachineConsoleServlet(StateMachineRegistry registry,
                                       InstanceRepository instanceRepository,
                                       SnapshotRepository snapshotRepository,
                                       InstanceExecutionService<Object> executionService,
                                       ObjectMapper objectMapper) {
        this.registry = registry;
        this.instanceRepository = instanceRepository;
        this.snapshotRepository = snapshotRepository;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
    }

    // ==================== GET ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || "".equals(path)) {
            // 无尾斜杠时重定向，确保浏览器以正确 base 解析相对路径
            String requestURI = req.getRequestURI();
            if (!requestURI.endsWith("/")) {
                resp.sendRedirect(requestURI + "/");
                return;
            }
            path = "/";
        }

        resp.setCharacterEncoding("utf-8");

        // 首页
        if ("/".equals(path)) {
            serveResource("/index.html", resp);
            return;
        }

        // API 接口
        if (path.startsWith("/api/") && path.endsWith(".json")) {
            resp.setContentType("application/json;charset=utf-8");
            try {
                String json = process(path, req);
                resp.getWriter().write(json);
            } catch (Exception e) {
                log.error("[state-machine] 处理 API 请求失败: {}", path, e);
                resp.setStatus(500);
                resp.getWriter().write(errorJson(e.getMessage()));
            }
            return;
        }

        // 静态资源
        if (path.startsWith("/css/") || path.startsWith("/js/")) {
            serveResource(path, resp);
            return;
        }

        resp.sendError(404);
    }

    /**
     * 根据路径分发到对应的 API 处理方法。
     */
    private String process(String path, HttpServletRequest req) throws Exception {
        switch (path) {
            case "/api/machines.json":
                return listMachines();
            case "/api/versions.json":
                return getVersions(req.getParameter("name"));
            case "/api/instances.json":
                return getInstances(req);
            case "/api/instance.json":
                return getInstanceDetail(req.getParameter("id"));
            default:
                return errorJson("未知接口: " + path);
        }
    }

    // ==================== POST ====================

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getPathInfo();
        resp.setCharacterEncoding("utf-8");
        resp.setContentType("application/json;charset=utf-8");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(req.getInputStream(), Map.class);

            String json;
            switch (path) {
                case "/api/resume.json":
                    json = resumeInstance(body);
                    break;
                case "/api/retry.json":
                    json = retryInstance(body);
                    break;
                case "/api/advance.json":
                    json = advanceInstance(body);
                    break;
                default:
                    json = errorJson("未知接口: " + path);
            }
            resp.getWriter().write(json);
        } catch (Exception e) {
            log.error("[state-machine] 处理 POST 请求失败: {}", path, e);
            resp.setStatus(500);
            resp.getWriter().write(errorJson(e.getMessage()));
        }
    }

    // ==================== API 实现 ====================

    private String listMachines() throws Exception {
        List<Map<String, Object>> result = registry.getMachineNames().stream().map(name -> {
            List<DefinitionData> versions = registry.getVersions(name);
            MachineName machineName = MachineName.of(name);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("versionCount", versions.size());
            item.put("runningInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.RUNNING));
            item.put("failedInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.FAILED));
            item.put("suspendedInstances", instanceRepository.countByMachineNameAndStatus(machineName, InstanceStatus.SUSPENDED));
            return item;
        }).collect(Collectors.toList());
        return objectMapper.writeValueAsString(result);
    }

    private String getVersions(String name) throws Exception {
        if (name == null || name.isEmpty()) {
            return errorJson("缺少参数: name");
        }
        List<MachineDefinitionDTO> result = registry.getVersions(name).stream().map(r -> {
            try {
                List<Map<String, Object>> states = objectMapper.readValue(r.statesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                List<Map<String, Object>> transitions = objectMapper.readValue(r.transitionsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
                Map<String, Object> rp = objectMapper.readValue(r.retryPolicyJson(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), states, transitions, rp, r.registeredAt());
            } catch (Exception e) {
                return new MachineDefinitionDTO(r.id(), r.name().value(), r.version(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), r.registeredAt());
            }
        }).collect(Collectors.toList());
        return objectMapper.writeValueAsString(result);
    }

    private String getInstances(HttpServletRequest req) throws Exception {
        String name = req.getParameter("name");
        if (name == null || name.isEmpty()) {
            return errorJson("缺少参数: name");
        }
        String status = req.getParameter("status");
        String businessId = req.getParameter("businessId");
        String instanceId = req.getParameter("instanceId");
        int page = parseIntParam(req.getParameter("page"), 0);
        int size = parseIntParam(req.getParameter("size"), 20);
        size = Math.min(size, MAX_PAGE_SIZE);

        MachineName machineName = MachineName.of(name);

        // 校验 status 参数
        InstanceStatus statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = InstanceStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                return errorJson("无效的 status 值: " + status + "。合法值: RUNNING, COMPLETED, FAILED, SUSPENDED");
            }
        }

        BusinessId businessIdObj = (businessId != null && !businessId.isEmpty()) ? BusinessId.of(businessId) : null;
        InstanceId instanceIdObj = (instanceId != null && !instanceId.isEmpty()) ? InstanceId.of(instanceId) : null;

        boolean hasFilters = statusEnum != null || businessIdObj != null || instanceIdObj != null;
        List<InstanceData> records = hasFilters
            ? instanceRepository.findByMachineNameWithFilters(machineName, statusEnum, businessIdObj, instanceIdObj, page * size, size)
            : instanceRepository.findByMachineName(machineName, page * size, size);
        long total = hasFilters
            ? instanceRepository.countByMachineNameWithFilters(machineName, statusEnum, businessIdObj, instanceIdObj)
            : instanceRepository.countByMachineNameWithFilters(machineName, null, null, null);

        List<InstanceDTO> dtos = records.stream().map(r -> new InstanceDTO(
            r.id().value(), r.machineName().value(), r.definitionVersion(),
            r.currentState().value(), r.status().name(),
            r.businessId() != null ? r.businessId().value() : null,
            r.retryCount(), r.errorMessage(), r.createdAt(), r.updatedAt()
        )).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instances", dtos);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return objectMapper.writeValueAsString(result);
    }

    private String getInstanceDetail(String id) throws Exception {
        if (id == null || id.isEmpty()) {
            return errorJson("缺少参数: id");
        }
        Optional<InstanceData> inst = instanceRepository.findById(InstanceId.of(id));
        if (!inst.isPresent()) {
            return errorJson("实例不存在");
        }
        List<SnapshotDTO> snaps = snapshotRepository.findByInstanceId(InstanceId.of(id)).stream()
            .map(s -> new SnapshotDTO(s.id().value(), s.stateName().value(), s.inputJson(), s.outputJson(),
                s.status().name(), s.errorMessage(), s.attempt(), s.snapshotType(), s.executedAt()))
            .collect(Collectors.toList());
        InstanceData r = inst.get();
        InstanceDTO dto = new InstanceDTO(r.id().value(), r.machineName().value(), r.definitionVersion(),
            r.currentState().value(), r.status().name(),
            r.businessId() != null ? r.businessId().value() : null,
            r.retryCount(), r.errorMessage(), r.createdAt(), r.updatedAt());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instance", dto);
        result.put("snapshots", snaps);
        return objectMapper.writeValueAsString(result);
    }

    @SuppressWarnings("unchecked")
    private String resumeInstance(Map<String, Object> body) throws Exception {
        String id = (String) body.get("id");
        if (id == null || id.isEmpty()) {
            return errorJson("缺少参数: id");
        }
        Optional<InstanceData> inst = instanceRepository.findById(InstanceId.of(id));
        if (!inst.isPresent()) {
            return errorResultJson(false, "实例不存在", null);
        }
        String expectedState = (String) body.get("expectedCurrentState");
        if (expectedState == null || expectedState.trim().isEmpty()) {
            return errorResultJson(false, "缺少 expectedCurrentState", null);
        }
        Optional<? extends StateMachine<?>> machineOpt = registry.getLatest(inst.get().machineName().value());
        if (!machineOpt.isPresent()) {
            return errorResultJson(false, "状态机不存在: " + inst.get().machineName().value(), null);
        }
        try {
            String contextJson = (String) body.get("contextJson");
            StateMachine<Object> m = (StateMachine<Object>) machineOpt.get();

            if (contextJson != null && !contextJson.trim().isEmpty()) {
                final String cj = contextJson;
                executionService.resumeByInstanceId(m, InstanceId.of(id),
                    StateName.of(expectedState), ctx -> {
                        try {
                            objectMapper.readerForUpdating(ctx).readValue(cj);
                        } catch (Exception e) {
                            log.error("[state-machine] 恢复实例 {} 时解析 contextJson 失败", id, e);
                            throw new RuntimeException("解析 contextJson 失败: " + e.getMessage(), e);
                        }
                    });
            } else {
                executionService.resumeByInstanceId(m, InstanceId.of(id),
                    StateName.of(expectedState), c -> {});
            }
            Optional<InstanceData> updated = instanceRepository.findById(InstanceId.of(id));
            String currentState = updated.map(i -> i.currentState().value()).orElse("unknown");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已恢复执行");
            result.put("currentState", currentState);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[state-machine] 恢复实例失败 id={}", id, e);
            return errorResultJson(false, e.getMessage(), null);
        }
    }

    @SuppressWarnings("unchecked")
    private String retryInstance(Map<String, Object> body) throws Exception {
        String id = (String) body.get("id");
        if (id == null || id.isEmpty()) {
            return errorJson("缺少参数: id");
        }
        Optional<InstanceData> inst = instanceRepository.findById(InstanceId.of(id));
        if (!inst.isPresent()) {
            return errorJson("实例不存在");
        }
        Optional<? extends StateMachine<?>> machineOpt = registry.getLatest(inst.get().machineName().value());
        if (!machineOpt.isPresent()) {
            return errorJson("状态机不存在: " + inst.get().machineName().value());
        }
        try {
            StateMachine<Object> m = (StateMachine<Object>) machineOpt.get();
            executionService.retry(m, InstanceId.of(id));
            Optional<InstanceData> updated = instanceRepository.findById(InstanceId.of(id));
            Map<String, String> result = new LinkedHashMap<>();
            result.put("message", "Instance re-executed from state: " + updated.map(i -> i.currentState().value()).orElse("unknown"));
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[state-machine] 重试实例失败 id={}", id, e);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("message", "Re-execution failed: " + e.getMessage());
            return objectMapper.writeValueAsString(result);
        }
    }

    /**
     * 推进失败的实例：用空 action 替代原失败的 action，继续状态机流转
     * 控制台场景主要用于测试/调试，实际业务应通过 Java API 调用 advance
     */
    @SuppressWarnings("unchecked")
    private String advanceInstance(Map<String, Object> body) throws Exception {
        String id = (String) body.get("id");
        if (id == null || id.isEmpty()) {
            return errorJson("缺少参数: id");
        }
        Optional<InstanceData> inst = instanceRepository.findById(InstanceId.of(id));
        if (!inst.isPresent()) {
            return errorJson("实例不存在");
        }
        if (inst.get().status() != InstanceStatus.FAILED) {
            return errorJson("只能对 FAILED 状态的实例执行 advance，当前状态: " + inst.get().status());
        }
        Optional<? extends StateMachine<?>> machineOpt = registry.getLatest(inst.get().machineName().value());
        if (!machineOpt.isPresent()) {
            return errorJson("状态机不存在: " + inst.get().machineName().value());
        }
        try {
            StateMachine<Object> m = (StateMachine<Object>) machineOpt.get();
            String contextJson = (String) body.get("contextJson");

            // 控制台 advance 使用空 action，仅用于推进状态
            cn.chedejun.statemachine.core.Action<Object> action = ctx -> {
                log.info("[state-machine] ADVANCE 执行空操作 instanceId={}", id);
            };

            java.util.function.Consumer<Object> merger = ctx -> {
                if (contextJson != null && !contextJson.trim().isEmpty()) {
                    try {
                        objectMapper.readerForUpdating(ctx).readValue(contextJson);
                    } catch (Exception e) {
                        throw new RuntimeException("解析 contextJson 失败: " + e.getMessage(), e);
                    }
                }
            };

            executionService.advance(m, InstanceId.of(id), action, merger);

            Optional<InstanceData> updated = instanceRepository.findById(InstanceId.of(id));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "已推进执行");
            result.put("currentState", updated.map(i -> i.currentState().value()).orElse("unknown"));
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("[state-machine] 推进实例失败 id={}", id, e);
            return errorResultJson(false, e.getMessage(), null);
        }
    }

    // ==================== 静态资源 ====================

    private void serveResource(String path, HttpServletResponse resp) throws IOException {
        String resourcePath = "/console" + path;
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            resp.sendError(404);
            return;
        }

        // 设置 Content-Type
        if (path.endsWith(".html")) {
            resp.setContentType("text/html;charset=utf-8");
        } else if (path.endsWith(".css")) {
            resp.setContentType("text/css;charset=utf-8");
        } else if (path.endsWith(".js")) {
            resp.setContentType("application/javascript;charset=utf-8");
        } else if (path.endsWith(".json")) {
            resp.setContentType("application/json;charset=utf-8");
        }

        // 缓存头
        resp.setHeader("Cache-Control", "max-age=3600");

        try (InputStream in = is; OutputStream out = resp.getOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
    }

    // ==================== 工具方法 ====================

    private int parseIntParam(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String errorJson(String message) {
        try {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", message);
            return objectMapper.writeValueAsString(err);
        } catch (Exception e) {
            return "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
        }
    }

    private String errorResultJson(boolean success, String error, String currentState) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("error", error);
            if (currentState != null) {
                result.put("currentState", currentState);
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + error.replace("\"", "\\\"") + "\"}";
        }
    }
}
