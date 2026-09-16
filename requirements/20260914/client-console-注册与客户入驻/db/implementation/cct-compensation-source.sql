-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: cct-compensation-source.json
SET NAMES utf8mb4;

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿执行记录的内部唯一标识。',
  `event_key` VARCHAR(128) NOT NULL COMMENT '领域事件或本地恢复动作的全局稳定幂等键。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '需要发布或本地恢复执行的领域事件类型代码。取值来源：CCT Domain Event Catalog；非固定枚举。',
  `execution_type` VARCHAR(64) NOT NULL COMMENT '区分 MQ 发布与本地 Handler 执行的方式代码。允许值：MQ_PUBLISH、LOCAL_HANDLER。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '产生该待执行事件或本地恢复动作的 CCT 业务记录类型代码。取值来源：CCT Compensation Source Catalog；非固定枚举。',
  `source_id` BIGINT NOT NULL COMMENT '在 source_type 命名空间内定位补偿来源业务记录的标识。',
  `source_version` BIGINT NOT NULL COMMENT '产生该事件或本地恢复动作的 CCT 来源业务记录版本坐标，并参与来源四元组唯一约束。',
  `schema_version` INT NOT NULL COMMENT '解释事件或任务 Payload JSON 结构的版本号。',
  `payload_json` JSON NOT NULL COMMENT '领域事件或本地恢复动作的受控 JSON Payload，大小不得超过 64 KiB。',
  `payload_hash` CHAR(64) NOT NULL COMMENT '输入为 schema_version 对应的非敏感 Canonical Payload，最大 64 KiB。',
  `execute_status` VARCHAR(64) NOT NULL COMMENT '补偿任务当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL COMMENT '补偿任务已经尝试执行的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL COMMENT '补偿任务下一次允许被 Claim 的 UTC 微秒精度时刻。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前 Claim 补偿任务的执行实例标识。',
  `lock_until` DATETIME(6) NULL COMMENT '当前补偿任务执行租约到期的 UTC 微秒精度时刻。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿执行最近一次失败的稳定错误分类代码。取值来源：CCT Compensation Error Catalog；非固定枚举。',
  `last_error_summary` TEXT NULL COMMENT '补偿执行最近一次失败的脱敏错误摘要，不保存敏感原文。',
  `lock_version` BIGINT NOT NULL COMMENT '补偿任务 Claim 与完成回写使用的独立技术 CAS 版本，不是业务 Revision 或来源版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_key` (`event_key`) COMMENT 'Unique index for compensation_event on event_key',
  UNIQUE KEY `uk_comp_source_fact` (`source_type`, `source_id`, `source_version`, `event_type`) COMMENT 'Unique index for compensation_event on source_type, source_id, source_version, event_type',
  KEY `idx_compensation_event_642be450` (`execute_status`, `next_execute_at`, `lock_until`) COMMENT 'Access index for compensation_event on execute_status, next_execute_at, lock_until'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='MQ 发布或 after-commit 本地任务；uk_comp_event_key(event_key)、uk_comp_source_fact(source_type,source_id,source_version,event_type)；MQ_PUBLISH/LOCAL_HANDLER，状态 INIT/PROCESSING/ERROR/FAILED；Payload 不可变且最大 64 KiB；Claim/Lease 使用 lock_version；Payload 不可变：schema_version、payload_json 与 payload_hash 创建后不得修改；重试必须复用相同 Event Envelope。；Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。；Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。；登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。；成功删除：MQ_PUBLISH 只有收到 Broker Confirm 后、LOCAL_HANDLER 只有取得确定成功结果后，才允许删除 compensation_event。；失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。；Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。';
