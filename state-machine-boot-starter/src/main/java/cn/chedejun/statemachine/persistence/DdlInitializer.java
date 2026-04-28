package cn.chedejun.statemachine.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

public class DdlInitializer {
    private static final Logger log = LoggerFactory.getLogger(DdlInitializer.class);
    private final DataSource dataSource;
    private final String ddlAuto;

    public DdlInitializer(DataSource dataSource, String ddlAuto) {
        this.dataSource = dataSource;
        this.ddlAuto = ddlAuto;
        initialize();
    }

    private void initialize() {
        if (!"update".equalsIgnoreCase(ddlAuto)) {
            log.info("[state-machine] DDL 自动创建已禁用 (ddl-auto={})", ddlAuto);
            return;
        }
        try {
            JdbcTemplate template = new JdbcTemplate(dataSource);
            String dbType = detectDbType(template);
            String resourcePath = "/ddl/" + dbType + ".sql";
            var stream = getClass().getResourceAsStream(resourcePath);
            if (stream == null) {
                log.warn("[state-machine] 数据库 '{}' 无 DDL 脚本，回退使用 H2", dbType);
                stream = getClass().getResourceAsStream("/ddl/h2.sql");
            }
            if (stream != null) {
                String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                for (String stmt : sql.split(";")) { String t = stmt.trim(); if (!t.isEmpty()) template.execute(t); }
                log.info("[state-machine] 数据表初始化完成，使用 {}", resourcePath);
            }
        } catch (Exception e) {
            log.error("[state-machine] 数据表初始化失败", e);
            throw new RuntimeException("Failed to initialize state machine tables", e);
        }
    }

    private String detectDbType(JdbcTemplate template) {
        try {
            String url = template.getDataSource().getConnection().getMetaData().getURL().toLowerCase();
            if (url.contains("mysql")) return "mysql";
            if (url.contains("postgresql") || url.contains("postgres")) return "postgresql";
            return "h2";
        } catch (Exception e) { return "h2"; }
    }
}
