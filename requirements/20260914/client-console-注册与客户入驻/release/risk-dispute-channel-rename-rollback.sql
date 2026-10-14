-- mor-test Risk 争议 4 表 回滚 Channel→Provider（回退旧 Risk 镜像前执行）（D2，用户 2026-10-14 批准发布清单）。由 generate_dispute_rename.py 生成，勿手改。
-- 前置：4 表均为 0 行；DDL 自动提交，逐条执行，不使用 --force。
ALTER TABLE `t_dispute_case`
  DROP INDEX `uk_dispute_notice`,
  DROP INDEX `uk_dispute_external`,
  CHANGE COLUMN `channel_dispute_notice_id` `provider_dispute_notice_id` bigint NOT NULL COMMENT 'Provider 争议通知的稳定业务标识。' AFTER `case_no`,
  CHANGE COLUMN `channel_dispute_source_version` `provider_dispute_source_version` int NOT NULL COMMENT 'Provider Dispute 来源版本的整数值。' AFTER `provider_dispute_notice_id`,
  CHANGE COLUMN `channel_id` `provider_id` bigint NOT NULL COMMENT 'Provider 渠道的稳定业务标识。' AFTER `provider_dispute_source_version`,
  MODIFY COLUMN `reason_code` varchar(64) DEFAULT NULL COMMENT '争议原因的稳定业务代码。取值来源：Provider 争议原因目录；非固定枚举。' AFTER `status`,
  CHANGE COLUMN `channel_response_due_time` `provider_response_due_time` datetime(6) NOT NULL COMMENT 'Provider 响应截止时间对应的 UTC 微秒时间。' AFTER `client_response_due_time`,
  ADD UNIQUE KEY `uk_dispute_notice` (`provider_dispute_notice_id`) COMMENT '保证业务唯一性：provider_dispute_notice_id',
  ADD UNIQUE KEY `uk_dispute_external` (`provider_id`,`mid_id`,`external_dispute_id`) COMMENT '保证业务唯一性：provider_id、mid_id、external_dispute_id',
  COMMENT = '保存 Provider Dispute Notice 形成的 Risk 案件、金额、截止时间和当前 Outcome 指针';

ALTER TABLE `t_dispute_liability_decision`
  MODIFY COLUMN `recommended_principal_bearer` varchar(64) DEFAULT NULL COMMENT '建议本金承担方的稳定业务代码。允许值：CLIENT、PLATFORM、PROVIDER。' AFTER `action_effect_id`,
  MODIFY COLUMN `principal_bearer` varchar(64) NOT NULL COMMENT '本金承担方的稳定业务代码。允许值：CLIENT、PLATFORM、PROVIDER。' AFTER `recommended_principal_bearer`,
  CHANGE COLUMN `channel_fee_bearer` `provider_fee_bearer` varchar(64) NOT NULL COMMENT 'Provider 费用承担方的稳定业务代码。允许值：CLIENT、PLATFORM、PROVIDER。' AFTER `principal_bearer`,
  CHANGE COLUMN `channel_recovery_required` `provider_recovery_required` tinyint(1) NOT NULL COMMENT '是否需要 Provider 追偿，1 表示是，0 表示否。' AFTER `platform_error_flag`;

ALTER TABLE `t_dispute_outcome`
  DROP INDEX `idx_dispute_out_action`,
  MODIFY COLUMN `decision_source_type` varchar(64) NOT NULL COMMENT '决定来源类型的稳定业务代码。允许值：PROVIDER_ACTION、CLIENT_DISPOSITION、OPERATIONS_DISPOSITION、OPERATIONS_CORRECTION。' AFTER `reason_code`,
  CHANGE COLUMN `channel_dispute_action_id` `provider_dispute_action_id` bigint DEFAULT NULL COMMENT 'Provider 争议动作的稳定业务标识。' AFTER `source_version`,
  ADD KEY `idx_dispute_out_action` (`provider_dispute_action_id`) COMMENT '支持访问路径：provider_dispute_action_id',
  COMMENT = '保存 Provider 或已授权处置形成的 WON/LOST 结果及 Operations Correction 不可变版本';

ALTER TABLE `t_dispute_submission_request`
  DROP INDEX `uk_dispute_channel_act`,
  CHANGE COLUMN `channel_dispute_action_id` `provider_dispute_action_id` bigint DEFAULT NULL COMMENT 'Provider 争议动作的稳定业务标识。' AFTER `active_slot`,
  ADD UNIQUE KEY `uk_dispute_provider_act` (`provider_dispute_action_id`) COMMENT '保证业务唯一性：provider_dispute_action_id',
  COMMENT = '保存 Risk 向 Payment 发出的证据提交指令及准确 Provider Action 回绑';
