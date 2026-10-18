-- Code-MoR Dispute schema: verbatim slice of the approved Risk & Compliance schema by table ownership (design/03 §2.2).
-- 只适用于人工审核后的空 mor_dispute 数据库；不得作为应用启动迁移或已有数据库升级脚本。
-- Source: paytech/dispute/docs/database/dispute.pdma.json

CREATE TABLE `t_dispute_case` (
  `dispute_case_id` BIGINT NOT NULL COMMENT '争议案件的稳定业务标识。',
  `case_no` VARCHAR(64) NOT NULL COMMENT '争议案件编号的业务值。',
  `channel_dispute_notice_id` BIGINT NOT NULL COMMENT 'Channel 争议通知的稳定业务标识。',
  `channel_dispute_source_version` INT NOT NULL COMMENT 'Channel Dispute 来源版本的整数值。',
  `channel_id` BIGINT NOT NULL COMMENT 'Channel 渠道的稳定业务标识。',
  `mid_id` BIGINT NOT NULL COMMENT 'MID 商户号的稳定业务标识。',
  `external_dispute_id` VARCHAR(128) NOT NULL COMMENT '外部争议标识的业务值。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '支付 Intent的稳定业务标识。',
  `payment_attempt_id` BIGINT NOT NULL COMMENT '支付 Attempt的稳定业务标识。',
  `order_id` BIGINT NOT NULL COMMENT '订单的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 客户的稳定业务标识。',
  `disputed_amount` DECIMAL(24,8) NOT NULL COMMENT '争议金额，精度为 24 位、小数位为 8 位。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 alpha-3 争议币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `current_outcome_id` BIGINT NULL COMMENT '当前争议结果的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '争议状态的稳定业务代码。允许值：EVIDENCE_REQUIRED、UNDER_REVIEW、SUBMITTED、WON、LOST、CLOSED。',
  `reason_code` VARCHAR(64) NULL COMMENT '争议原因的稳定业务代码。取值来源：Channel 争议原因目录；非固定枚举。',
  `client_response_due_time` DATETIME(6) NOT NULL COMMENT 'Client 响应截止时间对应的 UTC 微秒时间。',
  `channel_response_due_time` DATETIME(6) NOT NULL COMMENT 'Channel 响应截止时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '争议案件并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`dispute_case_id`),
  UNIQUE KEY `uk_dispute_case_no` (`case_no`) COMMENT '保证业务唯一性：case_no',
  UNIQUE KEY `uk_dispute_notice` (`channel_dispute_notice_id`) COMMENT '保证业务唯一性：channel_dispute_notice_id',
  UNIQUE KEY `uk_dispute_external` (`channel_id`, `mid_id`, `external_dispute_id`) COMMENT '保证业务唯一性：channel_id、mid_id、external_dispute_id',
  UNIQUE KEY `uk_dispute_outcome_ptr` (`current_outcome_id`, `dispute_case_id`) COMMENT '保证业务唯一性：current_outcome_id、dispute_case_id',
  KEY `idx_dispute_attempt` (`payment_attempt_id`) COMMENT '支持访问路径：payment_attempt_id',
  KEY `idx_dispute_client` (`client_id`, `status`) COMMENT '支持访问路径：client_id、status'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Channel Dispute Notice 形成的 Risk 案件、金额、截止时间和当前 Outcome 指针';

CREATE TABLE `t_dispute_state_change` (
  `change_id` BIGINT NOT NULL COMMENT '争议状态变更的稳定业务标识。',
  `dispute_case_id` BIGINT NOT NULL COMMENT '争议案件的稳定业务标识。',
  `from_status` VARCHAR(64) NULL COMMENT '原状态的稳定业务代码。允许值：EVIDENCE_REQUIRED、UNDER_REVIEW、SUBMITTED、WON、LOST、CLOSED。',
  `to_status` VARCHAR(64) NOT NULL COMMENT '新状态的稳定业务代码。允许值：EVIDENCE_REQUIRED、UNDER_REVIEW、SUBMITTED、WON、LOST、CLOSED。',
  `reason_code` VARCHAR(64) NULL COMMENT '变更原因的稳定业务代码。取值来源：Risk 争议状态原因目录；非固定枚举。',
  `trigger_type` VARCHAR(64) NOT NULL COMMENT '触发类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `trigger_id` VARCHAR(128) NOT NULL COMMENT '触发标识的业务值。',
  `actor_id` BIGINT NULL COMMENT '操作人的稳定业务标识。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '发生时间对应的 UTC 微秒时间。',
  `aggregate_version` INT NOT NULL COMMENT '聚合版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_dispute_state` (`dispute_case_id`, `aggregate_version`) COMMENT '保证业务唯一性：dispute_case_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存争议案件的状态迁移、触发来源和聚合版本';

CREATE TABLE `t_dispute_evidence_package` (
  `package_id` BIGINT NOT NULL COMMENT '争议证据包的稳定业务标识。',
  `dispute_case_id` BIGINT NOT NULL COMMENT '争议案件的稳定业务标识。',
  `version_no` INT NOT NULL COMMENT '证据包版本的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT '证据包状态的稳定业务代码。允许值：DRAFT、READY、SUBMITTED、SUPERSEDED。',
  `content_hash` CHAR(64) NOT NULL COMMENT '证据包内容哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`package_id`),
  UNIQUE KEY `uk_dispute_package` (`dispute_case_id`, `version_no`) COMMENT '保证业务唯一性：dispute_case_id、version_no',
  UNIQUE KEY `uk_dispute_package_identity` (`package_id`, `dispute_case_id`) COMMENT '保证业务唯一性：package_id、dispute_case_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次按需 RFI 收集并在提交后冻结的争议证据包版本';

CREATE TABLE `t_dispute_evidence_ref` (
  `evidence_id` BIGINT NOT NULL COMMENT '争议证据引用的稳定业务标识。',
  `package_id` BIGINT NOT NULL COMMENT '争议证据包的稳定业务标识。',
  `evidence_type` VARCHAR(64) NOT NULL COMMENT '证据类型的稳定业务代码。取值来源：Risk 争议证据类型目录；非固定枚举。',
  `file_id` BIGINT NOT NULL COMMENT 'Platform 文件的稳定业务标识。',
  `content_hash` CHAR(64) NOT NULL COMMENT '文件内容哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`evidence_id`),
  UNIQUE KEY `uk_dispute_evidence` (`package_id`, `evidence_type`, `file_id`) COMMENT '保证业务唯一性：package_id、evidence_type、file_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存争议证据包内 Platform 文件的稳定标识和内容哈希';

CREATE TABLE `t_dispute_submission_request` (
  `request_id` BIGINT NOT NULL COMMENT '争议提交请求的稳定业务标识。',
  `dispute_case_id` BIGINT NOT NULL COMMENT '争议案件的稳定业务标识。',
  `package_id` BIGINT NOT NULL COMMENT '争议证据包的稳定业务标识。',
  `request_no` INT NOT NULL COMMENT '请求序号的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT '提交请求状态的稳定业务代码。允许值：CREATED、SUBMITTED、UNKNOWN、SUCCEEDED、FAILED、CANCELLED。',
  `active_slot` BOOLEAN NULL COMMENT '进行中请求占位，1 表示是，0 表示否，NULL 表示不适用。',
  `channel_dispute_action_id` BIGINT NULL COMMENT 'Channel 争议动作的稳定业务标识。',
  `action_source_version` INT NULL COMMENT 'Action 来源版本的整数值。',
  `requested_time` DATETIME(6) NOT NULL COMMENT '请求时间对应的 UTC 微秒时间。',
  `resolved_time` DATETIME(6) NULL COMMENT '收口时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '提交请求并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uk_dispute_request` (`dispute_case_id`, `request_no`) COMMENT '保证业务唯一性：dispute_case_id、request_no',
  UNIQUE KEY `uk_dispute_req_active` (`dispute_case_id`, `active_slot`) COMMENT '保证业务唯一性：dispute_case_id、active_slot',
  UNIQUE KEY `uk_dispute_channel_act` (`channel_dispute_action_id`) COMMENT '保证业务唯一性：channel_dispute_action_id',
  KEY `idx_dispute_req_package` (`package_id`, `dispute_case_id`) COMMENT '支持访问路径：package_id、dispute_case_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Risk 向 Payment 发出的证据提交指令及准确 Channel Action 回绑';

CREATE TABLE `t_dispute_outcome` (
  `outcome_id` BIGINT NOT NULL COMMENT '争议结果的稳定业务标识。',
  `dispute_case_id` BIGINT NOT NULL COMMENT '争议案件的稳定业务标识。',
  `outcome_version` INT NOT NULL COMMENT '结果版本的整数值。',
  `outcome` VARCHAR(64) NOT NULL COMMENT '争议结果的稳定业务代码。允许值：WON、LOST。',
  `reason_code` VARCHAR(64) NULL COMMENT '结果原因的稳定业务代码。取值来源：Risk 统一争议结果原因目录；非固定枚举。',
  `decision_source_type` VARCHAR(64) NOT NULL COMMENT '决定来源类型的稳定业务代码。允许值：CHANNEL_ACTION、CLIENT_DISPOSITION、OPERATIONS_DISPOSITION、OPERATIONS_CORRECTION。',
  `decision_source_id` BIGINT NOT NULL COMMENT '决定来源的稳定业务标识。',
  `source_version` INT NOT NULL COMMENT '来源版本的整数值。',
  `channel_dispute_action_id` BIGINT NULL COMMENT 'Channel 争议动作的稳定业务标识。',
  `decided_time` DATETIME(6) NOT NULL COMMENT '决定时间对应的 UTC 微秒时间。',
  `supersedes_outcome_id` BIGINT NULL COMMENT '被替代结果的稳定业务标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`outcome_id`),
  UNIQUE KEY `uk_dispute_outcome` (`dispute_case_id`, `outcome_version`) COMMENT '保证业务唯一性：dispute_case_id、outcome_version',
  UNIQUE KEY `uk_dispute_out_source` (`decision_source_type`, `decision_source_id`, `source_version`) COMMENT '保证业务唯一性：decision_source_type、decision_source_id、source_version',
  UNIQUE KEY `uk_dispute_outcome_identity` (`outcome_id`, `dispute_case_id`) COMMENT '保证业务唯一性：outcome_id、dispute_case_id',
  UNIQUE KEY `uk_dispute_out_prev` (`supersedes_outcome_id`, `dispute_case_id`) COMMENT '保证业务唯一性：supersedes_outcome_id、dispute_case_id',
  KEY `idx_dispute_out_action` (`channel_dispute_action_id`) COMMENT '支持访问路径：channel_dispute_action_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Channel 或已授权处置形成的 WON/LOST 结果及 Operations Correction 不可变版本';

CREATE TABLE `t_dispute_liability_decision` (
  `outcome_id` BIGINT NOT NULL COMMENT '责任决定与争议结果共享的稳定业务标识。',
  `decision_evaluation_id` BIGINT NULL COMMENT '决策评估的稳定业务标识。',
  `action_effect_id` BIGINT NULL COMMENT 'Action Effect 结果的稳定业务标识。',
  `recommended_principal_bearer` VARCHAR(64) NULL COMMENT '建议本金承担方的稳定业务代码。允许值：CLIENT、PLATFORM、CHANNEL。',
  `principal_bearer` VARCHAR(64) NOT NULL COMMENT '本金承担方的稳定业务代码。允许值：CLIENT、PLATFORM、CHANNEL。',
  `channel_fee_bearer` VARCHAR(64) NOT NULL COMMENT 'Channel 费用承担方的稳定业务代码。允许值：CLIENT、PLATFORM、CHANNEL。',
  `platform_error_flag` BOOLEAN NOT NULL COMMENT '是否平台错误，1 表示是，0 表示否。',
  `channel_recovery_required` BOOLEAN NOT NULL COMMENT '是否需要 Channel 追偿，1 表示是，0 表示否。',
  `security_deposit_use_allowed` BOOLEAN NOT NULL COMMENT '是否允许使用 Client 预存保证金，1 表示是，0 表示否。',
  `override_reason_code` VARCHAR(64) NULL COMMENT '覆盖原因的稳定业务代码。取值来源：Risk 争议责任覆盖原因目录；非固定枚举。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '责任原因的稳定业务代码。取值来源：Risk 争议责任原因目录；非固定枚举。',
  `decided_by` BIGINT NULL COMMENT '决定人的稳定业务标识。',
  `decided_time` DATETIME(6) NOT NULL COMMENT '决定时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`outcome_id`),
  KEY `idx_liability_eval` (`decision_evaluation_id`) COMMENT '支持访问路径：decision_evaluation_id',
  KEY `idx_liability_effect` (`action_effect_id`) COMMENT '支持访问路径：action_effect_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存争议 Outcome 的责任分类、规则 Evaluation/Action Effect 或人工授权证据';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿事件的稳定业务标识。',
  `event_key` VARCHAR(128) NOT NULL COMMENT '事件键的业务值。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型的稳定业务代码。取值来源：Risk & Compliance 应用全局唯一 Handler 目录；非固定枚举。',
  `sub_event_type` VARCHAR(64) NOT NULL COMMENT '事件子类型的稳定业务代码。取值来源：对应 Handler 的 Risk 业务子类型目录；非固定枚举。',
  `payload_json` VARCHAR(4096) NULL COMMENT '可选且不超过 4 KiB UTF-8 的最小非敏感 JSON Object；默认从本库权威事实重建。',
  `execute_status` VARCHAR(64) NOT NULL DEFAULT 'INIT' COMMENT '执行状态的稳定业务代码。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '执行次数的整数值。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次执行时间对应的 UTC 微秒时间。',
  `lock_owner` VARCHAR(64) NULL COMMENT '租约持有者的业务值。',
  `lock_until` DATETIME(6) NULL COMMENT '租约截止时间对应的 UTC 微秒时间。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近错误代码的稳定业务代码。取值来源：Risk 补偿执行错误目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近脱敏错误摘要的业务值。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim 与 Lease 条件更新使用的正长整数版本，登记初始值固定为 1。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间对应的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间对应的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '保证业务唯一性：event_key、event_type、sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '支持访问路径：execute_status、next_execute_at、id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '支持访问路径：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存与业务事务原子登记的 MQ 发布或本地可恢复动作及 Claim Lease';
