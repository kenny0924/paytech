-- 需求：requirements/20261014/risk-支付反欺诈一期（阶段 4）。目标库：Risk（本机 mor_risk）。
-- 性质：增量执行文件，由 PDManer 模型生成的完整 Schema（risk-compliance-schema.sql，83 表）对照编写；不得重复执行。
-- 前置：库为本需求前的 81 表 Schema；t_transaction_risk_assessment、t_risk_list、t_risk_list_entry 没有数据（此前无生产代码写入）。
-- 回滚：DROP 两张新表；按旧 Schema 重建 t_transaction_risk_assessment；ALTER 删除新增列并恢复 source_effect_id NOT NULL 与旧注释。

-- 1. 交易风险评估快照：此前无代码写入，按新结构重建。
DROP TABLE `t_transaction_risk_assessment`;

CREATE TABLE `t_transaction_risk_assessment` (
  `assessment_id` BIGINT NOT NULL COMMENT '交易风险评估快照的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '支付 Intent的稳定业务标识。',
  `payment_attempt_id` BIGINT NOT NULL COMMENT '支付 Attempt的稳定业务标识。',
  `risk_action_id` BIGINT NOT NULL COMMENT 'Payment 风控 Action的稳定业务标识。',
  `order_id` BIGINT NOT NULL COMMENT '订单的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 客户的稳定业务标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Payment Intent 冻结的 Operating Entity 编号。取值来源：CCT Operating Entity；非固定枚举。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '支付方式代码的稳定业务代码。取值来源：Payment Method Catalog；非固定枚举。',
  `capture_mode` VARCHAR(64) NOT NULL COMMENT '捕获模式的稳定业务代码。允许值：AUTOMATIC、MANUAL。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '支付金额，精度为 24 位、小数位为 8 位。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 alpha-3 支付币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT '行业代码的稳定业务代码。取值来源：CCT 行业目录；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT 'Payment Intent 冻结且保留前导零的四位 MCC。取值来源：CCT 批准 MCC 目录；非固定枚举。',
  `consumer_country_code` CHAR(2) NULL COMMENT 'Payment Intent 冻结的 ISO 3166-1 alpha-2 消费者国家；未提供时为空。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `consumer_reference` VARCHAR(128) NULL COMMENT '消费者引用的业务值。',
  `consumer_email_vault_token` VARCHAR(128) NULL COMMENT '消费者 Email Token的业务值。',
  `consumer_phone_vault_token` VARCHAR(128) NULL COMMENT '消费者 Phone Token的业务值。',
  `client_ip_address` VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '首次评估冻结的消费者 IP 原值；受限字段，不进入日志、MQ 或稳定输入摘要。',
  `user_agent` VARCHAR(512) NULL COMMENT '首次评估冻结的浏览器 User-Agent；仅记录，不参与规则或稳定输入摘要。',
  `card_fingerprint` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment Card 提供的跨交易稳定卡指纹；非卡支付为空。',
  `card_bin` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '卡号前 6 至 8 位数字；非卡支付为空。',
  `authentication_requirement` VARCHAR(64) NULL COMMENT '认证要求的稳定业务代码。允许值：NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED。',
  `stable_input_hash` CHAR(64) NOT NULL COMMENT '稳定输入哈希的 SHA-256 小写十六进制值。',
  `restricted_input_hash` CHAR(64) NOT NULL COMMENT '受限输入哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`assessment_id`),
  UNIQUE KEY `uk_txn_risk_eval` (`evaluation_id`) COMMENT '保证业务唯一性：evaluation_id',
  UNIQUE KEY `uk_txn_risk_attempt` (`payment_attempt_id`) COMMENT '保证业务唯一性：payment_attempt_id',
  KEY `idx_txn_risk_order` (`order_id`) COMMENT '支持访问路径：order_id',
  KEY `idx_txn_risk_client` (`client_id`, `create_time`) COMMENT '支持访问路径：client_id、create_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个 Payment Attempt 风控评估使用的 Payment 最小输入、稳定输入摘要和认证要求';

-- 2. Feature 定义（实时累计、名单命中）。
CREATE TABLE `t_velocity_feature_definition` (
  `feature_version_id` BIGINT NOT NULL COMMENT '与所属 Feature Version 共享的主键。',
  `event_code` VARCHAR(64) NOT NULL COMMENT '事件编码的稳定业务代码。允许值：PAYMENT_ATTEMPT_ASSESSED、PAYMENT_ATTEMPT_REJECTED。',
  `anchor_subject_type` VARCHAR(64) NOT NULL COMMENT '锚定主体类型的稳定业务代码。允许值：CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS、CARD_FINGERPRINT。',
  `metric` VARCHAR(64) NOT NULL COMMENT '累计算法的稳定业务代码。允许值：COUNT、SUM_AMOUNT、DISTINCT_COUNT、LAST_AMOUNT、SECONDS_SINCE_LAST。',
  `distinct_subject_type` VARCHAR(64) NULL COMMENT '去重主体类型的稳定业务代码。允许值：CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS、CARD_FINGERPRINT。',
  `window_seconds` INT NOT NULL COMMENT '窗口秒数的整数值。',
  `include_current` BOOLEAN NOT NULL COMMENT '是否计入本次，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`feature_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个实时累计 Feature Version 的事件、锚定主体、算法和窗口；创建后不可修改';

CREATE TABLE `t_list_feature_definition` (
  `feature_version_id` BIGINT NOT NULL COMMENT '与所属 Feature Version 共享的主键。',
  `list_id` BIGINT NOT NULL COMMENT '风险名单的稳定业务标识。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS、CARD_FINGERPRINT、CARD_BIN。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`feature_version_id`),
  KEY `idx_list_feature_list` (`list_id`) COMMENT '支持访问路径：list_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个名单命中 Feature Version 的名单与主体类型，主体取值由类型对应的支付输入字段决定；创建后不可修改';

-- 3. 决策发布冻结决策流版本。
ALTER TABLE `t_decision_release`
  ADD COLUMN `decision_flow_version_id` BIGINT NULL COMMENT '决策流版本的稳定业务标识。' AFTER `point_version_id`,
  ADD KEY `idx_release_flow_ver` (`decision_flow_version_id`) COMMENT '支持访问路径：decision_flow_version_id';

-- 4. 变量增加 VELOCITY、LIST 来源及 Feature 关联。
ALTER TABLE `t_risk_variable`
  MODIFY COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '来源类型，允许 INPUT、CONSTANT、COLLECTION、NODE_OUTPUT、VELOCITY、LIST；创建后固定。允许值：INPUT、CONSTANT、COLLECTION、NODE_OUTPUT、VELOCITY、LIST。',
  ADD COLUMN `feature_version_id` BIGINT NULL COMMENT 'VELOCITY 与 LIST 来源创建时生成的不可变 Feature Version；其他来源为空。' AFTER `source_contract_version`,
  ADD KEY `idx_risk_variable_feature` (`feature_version_id`) COMMENT '支持由 Feature Version 反查所属变量';

-- 5. 名单用途与卡维度主体。
ALTER TABLE `t_risk_list`
  MODIFY COLUMN `supported_subject_type` VARCHAR(64) NOT NULL COMMENT '支持主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT、CARD_BIN。',
  ADD COLUMN `list_purpose` VARCHAR(64) NOT NULL COMMENT '名单用途的稳定业务代码。允许值：BLOCK、ALLOW。' AFTER `matching_mode`;

ALTER TABLE `t_risk_list_entry`
  MODIFY COLUMN `subject_type` VARCHAR(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT、CARD_BIN。',
  MODIFY COLUMN `source_effect_id` BIGINT NULL COMMENT '来源 Action Effect的稳定业务标识。',
  ADD COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '成员来源的稳定业务代码。允许值：ACTION、MANUAL。' AFTER `subject_key`,
  ADD COLUMN `reason` VARCHAR(500) NULL COMMENT '加入原因的业务值。' AFTER `source_effect_id`,
  ADD COLUMN `created_by` BIGINT NULL COMMENT '人工添加人的稳定业务标识。' AFTER `reason`,
  ADD COLUMN `deactivated_by` BIGINT NULL COMMENT '人工失效人的稳定业务标识。' AFTER `created_by`,
  ADD COLUMN `deactivated_reason` VARCHAR(500) NULL COMMENT '失效原因的业务值。' AFTER `deactivated_by`;
ALTER TABLE `t_risk_list_entry` COMMENT='保存名单内规范化主体的生效区间、来源 Action 或人工维护记录和替代链';

-- 6. 风险事件主体增加卡维度。
ALTER TABLE `t_velocity_event_subject`
  MODIFY COLUMN `subject_type` VARCHAR(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT、CARD_BIN。';

-- =====================================================================
-- 修订（2026-10-15，统一评估接口与可配置输出，见 ../design-amendment-unified-evaluation.md）
-- 7. 放行结束节点的附加输出（如认证要求）。回滚：DROP TABLE `t_decision_flow_end_output`。
CREATE TABLE `t_decision_flow_end_output` (
  `output_id` BIGINT NOT NULL COMMENT 'Risk 服务侧 Snowflake 生成的结束节点输出行身份。',
  `flow_version_id` BIGINT NOT NULL COMMENT '所属不可变流程版本；与结束节点同属此版本。',
  `node_id` BIGINT NOT NULL COMMENT '本库同一流程版本的放行结束节点。',
  `output_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '决策点合同声明的附加输出编码，如 AUTHENTICATION_REQUIREMENT。取值来源：决策点工程合同的附加输出声明；非固定枚举。',
  `output_value` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该输出在决策点合同中允许的取值，如 THREE_DS_REQUIRED；由应用按合同校验。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行在 Risk 数据库中创建的 UTC 微秒时间。',
  PRIMARY KEY (`output_id`),
  UNIQUE KEY `uk_flow_end_output` (`node_id`, `output_code`) COMMENT '保证业务唯一性：node_id、output_code',
  KEY `idx_flow_end_output_version` (`flow_version_id`) COMMENT '支持访问路径：flow_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存放行结束节点配置的附加输出；编码与取值由决策点合同声明，创建后不可修改';
