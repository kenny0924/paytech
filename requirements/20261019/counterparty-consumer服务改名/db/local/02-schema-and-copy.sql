-- counterparty 改名 · 本机 · 第 2 步：在 mor_counterparty 按当前 Schema 建表，再从 mor_consumer 逐表复制。
-- 表结构逐字取自 paytech/counterparty/docs/database/sql/counterparty-schema.sql（与原 consumer-schema.sql 相同，只改名）。
-- 以 root 手工执行；目标库必须为空（CREATE TABLE 不带 IF NOT EXISTS，重复执行会失败而不是覆盖）。
-- 执行期间 consumer-service 与 counterparty-service 均不得运行，避免复制过程中旧库仍有写入或新库被提前写入。
SET NAMES utf8mb4;
USE `mor_counterparty`;

CREATE TABLE `t_customer` (
  `customer_id` BIGINT NOT NULL COMMENT 'Commerce Customer 的内部唯一标识，创建后不可修改；Payment 已存卡以此关联。',
  `customer_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供 Client API 使用的不透明公开 Customer 编号，cus_ 前缀加随机串，创建后不可修改。',
  `client_id` BIGINT NOT NULL COMMENT '所属平台 Client 的内部唯一标识，创建后不可修改。',
  `consumer_reference` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Client 命名空间内的不透明、非 PII 消费者引用原值，由 Client 服务端传入，创建后不可修改；不得为 Email、Phone、证件号等个人原值。',
  `active_reference` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '有效时等于 consumer_reference，删除时置 NULL；与 client_id 组成唯一索引，保证同一引用同时最多一个有效 Customer。',
  `customer_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Customer 状态，删除不可撤销。允许值：ACTIVE、DELETED。',
  `deleted_time` DATETIME(6) NULL COMMENT '删除的 UTC 微秒精度时间；有效时为 NULL。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`) COMMENT '唯一索引：customer_no',
  UNIQUE KEY `uk_customer_active_reference` (`client_id`, `active_reference`) COMMENT '唯一索引：client_id、active_reference'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client 命名空间内的 Customer：身份只来自 Client 服务端传入的 consumer_reference，首期只保存身份与状态；删除不可撤销，同一引用再次使用时新建。';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '本地补偿事件记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件或本地恢复动作的全局稳定幂等键。',
  `event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本地补偿 Handler 的全应用唯一类型。取值来源：Commerce Event/Handler Catalog；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一补偿 Handler 下区分具体业务子动作的大小写敏感类型。',
  `payload_json` JSON NULL COMMENT '补偿事件执行所需的最小非敏感结构化载荷，不超过 4 KiB UTF-8 JSON Object，且不得包含 Vault Token。',
  `execute_status` VARCHAR(64) NOT NULL DEFAULT 'INIT' COMMENT '本地补偿事件当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '补偿事件已经尝试执行的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿事件下一次具备执行资格的 UTC 微秒精度时间。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前持有数据库 Claim Lease 的服务实例标识。',
  `lock_until` DATETIME(6) NULL COMMENT '数据库 Claim Lease 到期并可被其他实例重新认领的时刻。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿事件最近一次执行失败的稳定错误分类代码。',
  `last_error_summary` TEXT NULL COMMENT '补偿事件最近一次执行失败且不含敏感信息的摘要。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT '补偿事件 Claim Lease 并发更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compensation_event` (`event_key`, `event_type`, `sub_event_type`) COMMENT '唯一索引：event_key、event_type、sub_event_type',
  KEY `idx_compensation_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '访问索引：execute_status、next_execute_at、id',
  KEY `idx_compensation_lease` (`execute_status`, `lock_until`, `id`) COMMENT '访问索引：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='业务变更同事务写入的本地可靠执行记录；身份固定为 event_key、event_type、sub_event_type，PROCESSING 必须同时持有 lock_owner 且 lock_until 晚于当前时间，XXL-Job 仅负责唤醒和分片扫描。';

START TRANSACTION;

INSERT INTO `mor_counterparty`.`t_customer`
  (`customer_id`, `customer_no`, `client_id`, `consumer_reference`, `active_reference`, `customer_status`,
   `deleted_time`, `version`, `create_time`, `update_time`)
SELECT `customer_id`, `customer_no`, `client_id`, `consumer_reference`, `active_reference`, `customer_status`,
       `deleted_time`, `version`, `create_time`, `update_time`
FROM `mor_consumer`.`t_customer`;

INSERT INTO `mor_counterparty`.`t_compensation_event`
  (`id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`,
   `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`,
   `create_time`, `update_time`)
SELECT `id`, `event_key`, `event_type`, `sub_event_type`, `payload_json`, `execute_status`, `execute_count`,
       `next_execute_at`, `lock_owner`, `lock_until`, `last_error_code`, `last_error_summary`, `lock_version`,
       `create_time`, `update_time`
FROM `mor_consumer`.`t_compensation_event`;

COMMIT;
