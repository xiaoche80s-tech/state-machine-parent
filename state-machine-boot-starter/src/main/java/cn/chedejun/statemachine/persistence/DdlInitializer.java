package cn.chedejun.statemachine.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
            InputStream stream = getClass().getResourceAsStream(resourcePath);
            if (stream == null) {
                log.warn("[state-machine] 数据库 '{}' 无 DDL 脚本，回退使用 H2", dbType);
                stream = getClass().getResourceAsStream("/ddl/h2.sql");
            }
            if (stream != null) {
                String sql = readStream(stream);
                for (String stmt : sql.split(";")) { String t = stmt.trim(); if (!t.isEmpty()) template.execute(t); }
                log.info("[state-machine] 数据表初始化完成，使用 {}", resourcePath);
                // 迁移已有表的 executed_at 精度至微秒级
                migrateSnapshotTimestampPrecision(template, dbType);
            }
        } catch (Exception e) {
            log.error("[state-machine] 数据表初始化失败", e);
            throw new RuntimeException("Failed to initialize state machine tables", e);
        }
    }

    private String readStream(InputStream stream) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = stream.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private String detectDbType(JdbcTemplate template) {
        try {
            String url = template.getDataSource().getConnection().getMetaData().getURL().toLowerCase();
            if (url.contains("mysql")) return "mysql";
            if (url.contains("postgresql") || url.contains("postgres")) return "postgresql";
            return "h2";
        } catch (Exception e) { return "h2"; }
    }

    /**
     * 将已有快照表的 executed_at 列精度提升至微秒级（TIMESTAMP(6)）。
     * 使用 try-catch 实现幂等：列已为 TIMESTAMP(6) 时静默忽略。
     */
    private void migrateSnapshotTimestampPrecision(JdbcTemplate template, String dbType) {
        try {
            String alterSql;
            switch (dbType) {
                case "mysql":
                    alterSql = "ALTER TABLE state_machine_snapshots MODIFY COLUMN executed_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)";
                    break;
                case "postgresql":
                    alterSql = "ALTER TABLE state_machine_snapshots ALTER COLUMN executed_at TYPE TIMESTAMP(6)";
                    break;
                case "h2":
                    alterSql = "ALTER TABLE state_machine_snapshots ALTER COLUMN executed_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)";
                    break;
                default:
                    return;
            }
            template.execute(alterSql);
            log.info("[state-machine] 已将 snapshots.executed_at 精度提升至微秒级");
        } catch (Exception e) {
            log.debug("[state-machine] executed_at 精度迁移跳过 (可能已是最新): {}", e.getMessage());
        }
    }
}
