-- 需求：requirements/20261014/risk-支付反欺诈一期 阶段 7 回退（数据库部分）。目标库：mor-test 的 mor_risk。
-- 用途：新 Risk 镜像已运行过、不能整库还原备份时（可能已有尽调等其他写入），把 Schema 与支付初始化数据退回发布前状态，
--       使旧镜像（9f9f0141be7c，提交 7e9eddc）可再次启动。若新镜像从未启动且 Risk 停机期间无写入，优先按 release.md 整库还原备份。
-- 前置：① 已停止新 Risk 容器；② 已执行 test-acceptance-cleanup.sql（COMMIT），支付决策点下没有规则集、流程、发布、评估，
--       名单与 Feature 表为空——否则 DROP 与删列会丢失这些数据；③ 第 0 段核对全部为 0。
-- 定义来源：发布前从 test 读取的 SHOW CREATE TABLE（2026-10-15），见 release.md 第 1 节。
-- 执行：mysql 客户端 --default-character-set=utf8mb4。DDL 隐式提交，逐段执行并核对。

-- 0. 核对（只读）：均应为 0。
SELECT (SELECT COUNT(*) FROM t_transaction_risk_assessment) tra, (SELECT COUNT(*) FROM t_risk_list) lists,
  (SELECT COUNT(*) FROM t_risk_list_entry) entries, (SELECT COUNT(*) FROM t_velocity_event_subject) subjects,
  (SELECT COUNT(*) FROM t_decision_release WHERE decision_flow_version_id IS NOT NULL) flow_releases,
  (SELECT COUNT(*) FROM t_risk_variable WHERE feature_version_id IS NOT NULL OR source_type IN ('VELOCITY', 'LIST')) feature_vars,
  (SELECT COUNT(*) FROM t_decision_flow_end_output) outputs,
  (SELECT COUNT(*) FROM t_rule_set WHERE decision_point_id = 210002) payment_rule_sets,
  (SELECT COUNT(*) FROM t_decision_flow WHERE decision_point_id = 210002) payment_flows;

-- 1. 支付初始化数据（dml.sql 插入的行，按主键）。
START TRANSACTION;
DELETE FROM t_risk_variable_member WHERE member_id IN (900000000000094001, 900000000000094002, 900000000000094003);
DELETE FROM t_risk_variable WHERE variable_id BETWEEN 900000000000093001 AND 900000000000093013;
DELETE FROM t_action_version WHERE action_version_id IN (240006, 240007, 240008);
DELETE FROM t_decision_point_version WHERE point_version_id = 220002;
DELETE FROM t_decision_point WHERE decision_point_id = 210002;
COMMIT;

-- 2. 新表。
DROP TABLE `t_decision_flow_end_output`;
DROP TABLE `t_velocity_feature_definition`;
DROP TABLE `t_list_feature_definition`;

-- 3. 变更列恢复为发布前定义。
ALTER TABLE `t_decision_release`
  DROP KEY `idx_release_flow_ver`,
  DROP COLUMN `decision_flow_version_id`;

ALTER TABLE `t_risk_variable`
  DROP KEY `idx_risk_variable_feature`,
  DROP COLUMN `feature_version_id`,
  MODIFY COLUMN `source_type` varchar(64) NOT NULL COMMENT '来源类型，允许 INPUT、CONSTANT、COLLECTION、NODE_OUTPUT；创建后固定。允许值：INPUT、CONSTANT、COLLECTION、NODE_OUTPUT。';

ALTER TABLE `t_risk_list`
  DROP COLUMN `list_purpose`,
  MODIFY COLUMN `supported_subject_type` varchar(64) NOT NULL COMMENT '支持主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY。';

ALTER TABLE `t_risk_list_entry`
  DROP COLUMN `source_type`,
  DROP COLUMN `reason`,
  DROP COLUMN `created_by`,
  DROP COLUMN `deactivated_by`,
  DROP COLUMN `deactivated_reason`,
  MODIFY COLUMN `subject_type` varchar(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY。',
  MODIFY COLUMN `source_effect_id` bigint NOT NULL COMMENT '来源 Action Effect的稳定业务标识。',
  COMMENT='保存名单内规范化主体的生效区间、来源 Action 和替代链';

ALTER TABLE `t_velocity_event_subject`
  MODIFY COLUMN `subject_type` varchar(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY。';

-- 4. 交易评估快照表恢复为发布前结构（表为空）：DROP 后按发布前 SHOW CREATE TABLE 重建，语句见文件末尾附录。
DROP TABLE `t_transaction_risk_assessment`;

-- 附录：发布前 t_transaction_risk_assessment 定义（来自 test SHOW CREATE TABLE）。
CREATE TABLE `t_transaction_risk_assessment` (
  `assessment_id` bigint NOT NULL COMMENT '交易风险评估快照的稳定业务标识。',
  `evaluation_id` bigint NOT NULL COMMENT '决策评估的稳定业务标识。',
  `payment_intent_id` bigint NOT NULL COMMENT '支付 Intent的稳定业务标识。',
  `payment_attempt_id` bigint NOT NULL COMMENT '支付 Attempt的稳定业务标识。',
  `order_id` bigint NOT NULL COMMENT '订单的稳定业务标识。',
  `order_context_id` bigint NOT NULL COMMENT '订单上下文的稳定业务标识。',
  `client_id` bigint NOT NULL COMMENT 'Client 客户的稳定业务标识。',
  `operating_entity_id` bigint NOT NULL COMMENT '经营主体的稳定业务标识。',
  `assignment_id` bigint NOT NULL COMMENT '经营主体分配的稳定业务标识。',
  `payment_method_code` varchar(64) NOT NULL COMMENT '支付方式代码的稳定业务代码。取值来源：Payment Method Catalog；非固定枚举。',
  `amount` decimal(24,8) NOT NULL COMMENT '支付金额，精度为 24 位、小数位为 8 位。',
  `currency` char(3) NOT NULL COMMENT 'ISO 4217 alpha-3 支付币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `industry_code` varchar(64) NOT NULL COMMENT '行业代码的稳定业务代码。取值来源：CCT 行业目录；非固定枚举。',
  `mcc_code` varchar(4) NOT NULL COMMENT 'Order Context 冻结且保留前导零的四位 MCC。取值来源：CCT 批准 MCC 目录；非固定枚举。',
  `consumer_reference` varchar(128) DEFAULT NULL COMMENT '消费者引用的业务值。',
  `consumer_email_vault_token` varchar(128) DEFAULT NULL COMMENT '消费者 Email Token的业务值。',
  `consumer_phone_vault_token` varchar(128) DEFAULT NULL COMMENT '消费者 Phone Token的业务值。',
  `network_evidence_id` bigint NOT NULL COMMENT '网络证据的稳定业务标识。',
  `network_evidence_version` int NOT NULL COMMENT '网络证据版本的整数值。',
  `restricted_input_hash` char(64) NOT NULL COMMENT '受限输入哈希的 SHA-256 小写十六进制值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`assessment_id`),
  UNIQUE KEY `uk_txn_risk_eval` (`evaluation_id`) COMMENT '保证业务唯一性：evaluation_id',
  UNIQUE KEY `uk_txn_risk_attempt` (`payment_attempt_id`) COMMENT '保证业务唯一性：payment_attempt_id',
  KEY `idx_txn_risk_order` (`order_id`) COMMENT '支持访问路径：order_id',
  KEY `idx_txn_risk_client` (`client_id`,`create_time`) COMMENT '支持访问路径：client_id、create_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个 Payment Attempt 使用的 Order Context、消费者与网络最小输入'
;
