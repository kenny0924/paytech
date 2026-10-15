-- Platform Support：Client Webhook 投递的 5 张新表。审阅后手工执行。
-- 需求：requirements/20261015/platform-support-Webhook投递/technique.md 第 3 节。
-- 前提：目标库为 Platform Support 库，且不存在同名表；PDManer 中的 8 张旧 Webhook 表从未建表，无需 DROP。
-- 影响：只建空表，不修改任何已有表或数据；应用与测试不执行本文件。
-- 回退（仅限建表后尚无业务数据时）：
--   DROP TABLE `t_webhook_attempt`; DROP TABLE `t_webhook_delivery`; DROP TABLE `t_webhook_event`;
--   DROP TABLE `t_webhook_signing_secret`; DROP TABLE `t_webhook_endpoint`;
SET NAMES utf8mb4;

CREATE TABLE `t_webhook_endpoint` (
  `endpoint_id` BIGINT NOT NULL COMMENT 'Webhook 端点主键。',
  `tenant_id` BIGINT NOT NULL COMMENT '端点所属的 CLIENT Access Tenant。',
  `url` VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Client 的 HTTPS 接收地址明文；不含 userinfo 与 fragment，修改后下一次尝试即使用新地址。',
  `event_types` JSON NOT NULL COMMENT '订阅的公开事件类型字符串数组，非空且不重复；取消订阅后尚未完成的投递被取消。',
  `status` VARCHAR(32) NOT NULL COMMENT '端点状态；停用后不再生成新投递，未完成的投递被取出时取消。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`endpoint_id`),
  KEY `idx_webhook_endpoint_tenant` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='CLIENT 租户的 Webhook 接收地址与订阅事件类型；每个 Client 最多 5 个，由运营代配置';

CREATE TABLE `t_webhook_signing_secret` (
  `signing_secret_id` BIGINT NOT NULL COMMENT 'Webhook 签名密钥主键。',
  `endpoint_id` BIGINT NOT NULL COMMENT '所属 Webhook 端点。',
  `rotation_no` INT NOT NULL COMMENT '端点内第几把密钥，从 1 开始。',
  `secret_vault_token` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'WEBHOOK_SIGNING_SECRET_V1 的 REFERENCE Token；Purpose 为 WEBHOOK_SIGNING；仅发送前 Reveal，禁止日志及补偿 Payload。',
  `status` VARCHAR(32) NOT NULL COMMENT '密钥状态；轮换后旧密钥变为 RETIRED。允许值：ACTIVE、RETIRED。',
  `signing_slot` INT NULL COMMENT 'ACTIVE 时为 1，否则为空；与端点组成唯一键，保证同时只有一把 ACTIVE。',
  `valid_until` DATETIME(6) NULL COMMENT 'RETIRED 后仍参与签名的 UTC 截止时间：宽限轮换为轮换时间加 1 小时，立即作废为轮换时间；ACTIVE 时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`signing_secret_id`),
  UNIQUE KEY `uk_webhook_secret_rotation` (`endpoint_id`, `rotation_no`) COMMENT '保证业务唯一性：endpoint_id、rotation_no',
  UNIQUE KEY `uk_webhook_secret_active` (`endpoint_id`, `signing_slot`) COMMENT '保证业务唯一性：endpoint_id、signing_slot'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='端点签名密钥的 Vault 引用与轮换宽限；同一端点同时只有一把 ACTIVE，宽限内的 RETIRED 密钥也参与签名';

CREATE TABLE `t_webhook_event` (
  `event_id` BIGINT NOT NULL COMMENT 'Platform 内部事件主键，不作为公开 eventId。',
  `tenant_id` BIGINT NOT NULL COMMENT '按来源 Client 解析得到的 CLIENT Access Tenant。',
  `source_system` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '通过白名单的调用方服务名。取值来源：内部 HTTP 调用方服务名白名单；非固定枚举。',
  `source_event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '来源冻结的公开 eventId；同一 Client 与来源系统内永久唯一。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '公开事件类型。取值来源：Webhook 公开事件类型目录；非固定枚举。',
  `payload_schema_version` INT NOT NULL COMMENT 'Envelope 的 schemaVersion。',
  `source_version` INT NOT NULL COMMENT '来源资源版本；只用于重复提交比较。',
  `source_payload_hash` CHAR(64) NOT NULL COMMENT 'data.object 的 RFC 8785 规范字节 SHA-256 小写十六进制。',
  `payload_hash` CHAR(64) NOT NULL COMMENT '完整 Envelope 的 RFC 8785 规范字节 SHA-256 小写十六进制；每次发送前用于校验重新规范化的字节。',
  `payload` JSON NOT NULL COMMENT '冻结的公开 Envelope；不含 Token、PII 或内部标识，规范化后不超过 4096 字节。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_webhook_event_source` (`tenant_id`, `source_event_key`, `source_system`) COMMENT '保证业务唯一性：tenant_id、source_event_key、source_system'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='来源系统提交的冻结公开事件；受理后不可变，Retry 与 Replay 发送同一 Envelope';

CREATE TABLE `t_webhook_delivery` (
  `delivery_id` BIGINT NOT NULL COMMENT 'Webhook 投递主键；也是补偿任务的事件键。',
  `webhook_event_id` BIGINT NOT NULL COMMENT '所属 Webhook 事件。',
  `tenant_id` BIGINT NOT NULL COMMENT '与事件、端点相同的 CLIENT Access Tenant；用于列表查询。',
  `endpoint_id` BIGINT NOT NULL COMMENT '投递目标端点。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '冗余事件类型，用于列表筛选与订阅复核。取值来源：Webhook 公开事件类型目录；非固定枚举。',
  `status` VARCHAR(32) NOT NULL COMMENT '投递状态；SUCCEEDED、DEAD、CANCELLED 为终态。允许值：CREATED、PROCESSING、RETRY_WAIT、SUCCEEDED、DEAD、CANCELLED。',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已创建的尝试数量，初始为 0。',
  `last_attempt_time` DATETIME(6) NULL COMMENT '最近一次尝试开始的 UTC 微秒时间；尚未尝试时为空。',
  `replay_of_delivery_id` BIGINT NULL COMMENT '人工 Replay 时指向原投递；原始投递为空。',
  `requested_by` BIGINT NULL COMMENT '人工 Replay 的 Operations 账号；原始投递为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`delivery_id`),
  KEY `idx_webhook_delivery_event` (`webhook_event_id`) COMMENT '支持访问路径：webhook_event_id',
  KEY `idx_webhook_delivery_tenant` (`tenant_id`, `create_time`) COMMENT '支持访问路径：tenant_id、create_time',
  KEY `idx_webhook_delivery_endpoint` (`endpoint_id`, `create_time`) COMMENT '支持访问路径：endpoint_id、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一个事件到一个端点的投递，含原始投递与人工 Replay；重试节奏与执行租约由补偿任务承担';

CREATE TABLE `t_webhook_attempt` (
  `attempt_id` BIGINT NOT NULL COMMENT 'Webhook 投递尝试主键。',
  `delivery_id` BIGINT NOT NULL COMMENT '所属 Webhook 投递。',
  `attempt_no` INT NOT NULL COMMENT '投递内的尝试序号，从 1 开始。',
  `request_url` VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '本次实际请求的端点地址。',
  `signing_secret_id` BIGINT NOT NULL COMMENT '本次签名使用的 ACTIVE 密钥。',
  `grace_secret_id` BIGINT NULL COMMENT '宽限期内参与签名的 RETIRED 密钥；无宽限时为空。',
  `status` VARCHAR(32) NOT NULL COMMENT '尝试状态；PROCESSING 遗留表示发送中断，恢复时记为 FAILED。允许值：PROCESSING、SUCCEEDED、FAILED。',
  `http_status` INT NULL COMMENT '收到响应时的 HTTP 状态码。',
  `failure_code` VARCHAR(64) NULL COMMENT '失败原因代码；成功时为空。允许值：HTTP_STATUS、TIMEOUT、CONNECT_FAILED、TLS_FAILED、DNS_FAILED、BLOCKED_ADDRESS、INTERRUPTED。',
  `duration_ms` INT NULL COMMENT '从发出请求到结束的毫秒数；未完成时为空。',
  `started_time` DATETIME(6) NOT NULL COMMENT '尝试开始的 UTC 微秒时间。',
  `finished_time` DATETIME(6) NULL COMMENT '尝试结束的 UTC 微秒时间；PROCESSING 时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`attempt_id`),
  UNIQUE KEY `uk_webhook_attempt_no` (`delivery_id`, `attempt_no`) COMMENT '保证业务唯一性：delivery_id、attempt_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='每次 HTTP 投递尝试的地址、签名密钥与结果；发送前登记为 PROCESSING，结束后写入结果';
