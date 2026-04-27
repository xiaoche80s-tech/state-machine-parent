CREATE TABLE IF NOT EXISTS state_machine_definitions (
    id VARCHAR(64) PRIMARY KEY, name VARCHAR(128) NOT NULL, version VARCHAR(32) NOT NULL,
    states CLOB, transitions CLOB, retry_policy CLOB,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT uk_def_name_version UNIQUE (name, version)
);
CREATE TABLE IF NOT EXISTS state_machine_instances (
    id VARCHAR(64) PRIMARY KEY, definition_id VARCHAR(64), machine_name VARCHAR(128) NOT NULL,
    definition_version VARCHAR(32), current_state VARCHAR(64), business_id VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING', retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP, error_message CLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS state_machine_snapshots (
    id VARCHAR(64) PRIMARY KEY, instance_id VARCHAR(64) NOT NULL, state_name VARCHAR(64) NOT NULL,
    input CLOB, output CLOB, status VARCHAR(16) NOT NULL, error_message CLOB,
    attempt INT DEFAULT 1, snapshot_type VARCHAR(16) NOT NULL DEFAULT 'NODE',
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
