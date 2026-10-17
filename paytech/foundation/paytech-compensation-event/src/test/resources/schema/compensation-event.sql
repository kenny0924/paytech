DROP TABLE IF EXISTS t_compensation_recovery_audit;
DROP TABLE IF EXISTS t_compensation_event;
DROP TABLE IF EXISTS t_test_business_source;

CREATE TABLE t_test_business_source (
    event_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    sub_event_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    business_value VARCHAR(128) NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (event_key, sub_event_type)
);

CREATE TABLE t_compensation_event (
    id BIGINT NOT NULL,
    event_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    event_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    sub_event_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    payload_json VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    execute_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT',
    execute_count INT NOT NULL DEFAULT 0,
    next_execute_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lock_owner VARCHAR(64) NULL,
    lock_until DATETIME(6) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_summary VARCHAR(1000) NULL,
    lock_version BIGINT NOT NULL DEFAULT 1,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_comp_event_identity (event_key, event_type, sub_event_type),
    KEY idx_comp_event_ready (execute_status, next_execute_at, id),
    KEY idx_comp_event_lease (execute_status, lock_until, id)
);

CREATE TABLE t_compensation_recovery_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    event_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    event_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    sub_event_type VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    actor VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    ticket VARCHAR(128) NOT NULL,
    previous_error_code VARCHAR(64) NULL,
    previous_error_summary VARCHAR(1000) NULL,
    previous_execute_count INT NOT NULL,
    previous_lock_version BIGINT NOT NULL,
    new_lock_version BIGINT NOT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comp_recovery_event_ticket (event_id, ticket)
);
