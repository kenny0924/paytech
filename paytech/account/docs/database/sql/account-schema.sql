-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: paytech/account/docs/database/account.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT 'Finance 数据库内补偿事件记录的 Snowflake 主键，不作为跨系统事件标识。',
  `event_key` VARCHAR(255) NOT NULL COMMENT '按来源事实和事件类型确定性生成的全局稳定事件标识。',
  `event_type` VARCHAR(128) NOT NULL COMMENT '业务事实事件或本地恢复任务的稳定类型代码。取值来源：Finance 事件契约目录；非固定枚举。',
  `execution_type` VARCHAR(64) NOT NULL COMMENT '事件由 MQ 发布器或本地处理器执行。允许值：MQ_PUBLISH、LOCAL_HANDLER。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '拥有该事件的 Finance 聚合根或不可变业务事实类型。取值来源：Finance 事件来源事实类型目录；非固定枚举。',
  `source_id` BIGINT NOT NULL COMMENT '拥有该事件的 Finance 聚合根或不可变事实标识。',
  `source_version` BIGINT NOT NULL COMMENT '来源聚合根版本或不可变事实固定版本。',
  `schema_version` INTEGER NOT NULL COMMENT '事件 Payload 契约的正整数版本。',
  `payload_json` JSON NOT NULL COMMENT '最大六十四 KiB、不可变且不含敏感明文的 Canonical JSON 事件信封。',
  `payload_hash` CHAR(64) NOT NULL COMMENT 'Canonical Payload 的 SHA-256 哈希，创建后不得修改。',
  `execute_status` VARCHAR(64) NOT NULL COMMENT '补偿事件当前 Claim、重试或人工处置状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INTEGER NOT NULL DEFAULT 0 COMMENT '已经开始执行该事件的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL COMMENT '事件下次允许被 Claim 的 UTC 微秒时间。',
  `lock_owner` VARCHAR(128) NULL COMMENT '当前多实例 Claim Lease 的执行节点稳定标识。',
  `lock_until` DATETIME(6) NULL COMMENT '当前 Claim Lease 到期的 UTC 微秒时间。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近一次失败的受控且不含敏感信息的错误代码。取值来源：Finance 补偿执行错误代码目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次失败的有界脱敏摘要，禁止保存敏感明文。',
  `lock_version` BIGINT NOT NULL DEFAULT 0 COMMENT '仅用于 Claim 和 Lease 条件更新的技术乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '补偿事件与业务事实原子登记的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '补偿事件执行状态最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_comp_event` (`event_key`) COMMENT '保证业务唯一性：event_key',
  UNIQUE KEY `uk_finance_comp_source` (`source_type`, `source_id`, `source_version`, `event_type`) COMMENT '保证业务唯一性：source_type、source_id、source_version、event_type',
  KEY `idx_fin_comp_claim` (`execute_status`, `next_execute_at`, `lock_until`) COMMENT '支持访问路径：execute_status、next_execute_at、lock_until',
  KEY `idx_fin_comp_created` (`create_time`) COMMENT '支持访问路径：create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='与 Finance 业务事实在同一事务登记 MQ 发布或本地处理任务，并通过 Claim、Lease 和乐观锁可靠重试';
