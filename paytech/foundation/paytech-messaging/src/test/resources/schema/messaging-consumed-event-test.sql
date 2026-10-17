-- 测试专用：在 paytech_foundation_test 库重建 messaging 两张表，内容与 src/main/resources/schema/messaging-consumed-event.sql 一致。
DROP TABLE IF EXISTS t_consumed_event;
DROP TABLE IF EXISTS t_consumed_aggregate_version;

CREATE TABLE t_consumed_event (
    event_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件 ID（信封 eventId，默认等于 eventKey）',
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型（PascalCase，登记于事件目录）',
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型（大写 Code）',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识（分区键）',
    aggregate_version BIGINT NOT NULL COMMENT '本事件携带的聚合版本',
    consumed_at DATETIME(6) NOT NULL COMMENT '消费事务提交时间（UTC）',
    PRIMARY KEY (event_id),
    KEY idx_consumed_event_aggregate (aggregate_type, aggregate_id, aggregate_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '已消费领域事件：按事件 ID 去重';

CREATE TABLE t_consumed_aggregate_version (
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型（大写 Code）',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识（分区键）',
    last_version BIGINT NOT NULL COMMENT '本服务已处理的最大聚合版本',
    update_time DATETIME(6) NOT NULL COMMENT '最近推进时间（UTC）',
    PRIMARY KEY (aggregate_type, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '已消费聚合版本：判定乱序、重放与缺口';

DROP TABLE IF EXISTS t_test_handled_event;

CREATE TABLE t_test_handled_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '测试自增主键',
    event_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件 ID',
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识',
    aggregate_version BIGINT NOT NULL COMMENT '聚合版本',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '测试 Handler 在消费事务内写入的业务行';
