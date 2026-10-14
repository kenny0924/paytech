-- mor-test Risk 争议 4 表 Provider→Channel（D2，用户 2026-10-14 批准发布清单）。由 generate_dispute_rename.py 生成，勿手改。
-- 前置：4 表均为 0 行；DDL 自动提交，逐条执行，不使用 --force。
ALTER TABLE `t_dispute_case`
  DROP INDEX `uk_dispute_notice`,
  DROP INDEX `uk_dispute_external`,
  CHANGE COLUMN `provider_dispute_notice_id` `channel_dispute_notice_id` BIGINT NOT NULL COMMENT 'Channel 争议通知的稳定业务标识。' AFTER `case_no`,
  CHANGE COLUMN `provider_dispute_source_version` `channel_dispute_source_version` INT NOT NULL COMMENT 'Channel Dispute 来源版本的整数值。' AFTER `channel_dispute_notice_id`,
  CHANGE COLUMN `provider_id` `channel_id` BIGINT NOT NULL COMMENT 'Channel 渠道的稳定业务标识。' AFTER `channel_dispute_source_version`,
  MODIFY COLUMN `reason_code` VARCHAR(64) NULL COMMENT '争议原因的稳定业务代码。取值来源：Channel 争议原因目录；非固定枚举。' AFTER `status`,
  CHANGE COLUMN `provider_response_due_time` `channel_response_due_time` DATETIME(6) NOT NULL COMMENT 'Channel 响应截止时间对应的 UTC 微秒时间。' AFTER `client_response_due_time`,
  ADD UNIQUE KEY `uk_dispute_notice` (`channel_dispute_notice_id`) COMMENT '保证业务唯一性：channel_dispute_notice_id',
  ADD UNIQUE KEY `uk_dispute_external` (`channel_id`, `mid_id`, `external_dispute_id`) COMMENT '保证业务唯一性：channel_id、mid_id、external_dispute_id',
  COMMENT = '保存 Channel Dispute Notice 形成的 Risk 案件、金额、截止时间和当前 Outcome 指针';

ALTER TABLE `t_dispute_liability_decision`
  MODIFY COLUMN `recommended_principal_bearer` VARCHAR(64) NULL COMMENT '建议本金承担方的稳定业务代码。允许值：CLIENT、PLATFORM、CHANNEL。' AFTER `action_effect_id`,
  MODIFY COLUMN `principal_bearer` VARCHAR(64) NOT NULL COMMENT '本金承担方的稳定业务代码。允许值：CLIENT、PLATFORM、CHANNEL。' AFTER `recommended_principal_bearer`,
  CHANGE COLUMN `provider_fee_bearer` `channel_fee_bearer` VARCHAR(64) NOT NULL COMMENT 'Channel 费用承担方的稳定业务代码。允许值：CLIENT、PLATFORM、CHANNEL。' AFTER `principal_bearer`,
  CHANGE COLUMN `provider_recovery_required` `channel_recovery_required` BOOLEAN NOT NULL COMMENT '是否需要 Channel 追偿，1 表示是，0 表示否。' AFTER `platform_error_flag`;

ALTER TABLE `t_dispute_outcome`
  DROP INDEX `idx_dispute_out_action`,
  MODIFY COLUMN `decision_source_type` VARCHAR(64) NOT NULL COMMENT '决定来源类型的稳定业务代码。允许值：CHANNEL_ACTION、CLIENT_DISPOSITION、OPERATIONS_DISPOSITION、OPERATIONS_CORRECTION。' AFTER `reason_code`,
  CHANGE COLUMN `provider_dispute_action_id` `channel_dispute_action_id` BIGINT NULL COMMENT 'Channel 争议动作的稳定业务标识。' AFTER `source_version`,
  ADD KEY `idx_dispute_out_action` (`channel_dispute_action_id`) COMMENT '支持访问路径：channel_dispute_action_id',
  COMMENT = '保存 Channel 或已授权处置形成的 WON/LOST 结果及 Operations Correction 不可变版本';

ALTER TABLE `t_dispute_submission_request`
  DROP INDEX `uk_dispute_provider_act`,
  CHANGE COLUMN `provider_dispute_action_id` `channel_dispute_action_id` BIGINT NULL COMMENT 'Channel 争议动作的稳定业务标识。' AFTER `active_slot`,
  ADD UNIQUE KEY `uk_dispute_channel_act` (`channel_dispute_action_id`) COMMENT '保证业务唯一性：channel_dispute_action_id',
  COMMENT = '保存 Risk 向 Payment 发出的证据提交指令及准确 Channel Action 回绑';
