CREATE TABLE IF NOT EXISTS state_machine_definitions (
    id VARCHAR(64) PRIMARY KEY, name VARCHAR(128) NOT NULL, version VARCHAR(32) NOT NULL,
    states JSON, transitions JSON, retry_policy JSON, registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name_version (name, version)
);
CREATE TABLE IF NOT EXISTS state_machine_instances (
    id VARCHAR(64) PRIMARY KEY, definition_id VARCHAR(64), machine_name VARCHAR(128) NOT NULL,
    definition_version VARCHAR(32), current_state VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING', retry_count INT DEFAULT 0,
    next_retry_at TIMESTAMP NULL, error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS state_machine_snapshots (
    id VARCHAR(64) PRIMARY KEY, instance_id VARCHAR(64) NOT NULL, state_name VARCHAR(64) NOT NULL,
    input JSON, output JSON, status VARCHAR(16) NOT NULL, error_message TEXT,
    attempt INT DEFAULT 1, executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
