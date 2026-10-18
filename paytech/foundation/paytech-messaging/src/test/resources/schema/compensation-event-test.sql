-- 测试专用：SequencedPublishAutoTest 在 paytech_foundation_test 库重建补偿任务表，结构与 paytech-compensation-event 测试 Schema 一致。
DROP TABLE IF EXISTS t_compensation_event;

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
