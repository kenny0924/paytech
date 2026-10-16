-- Finance 期间重开与年末结转：模型调整（2026-10-17，阶段 3 R1 与步骤类型）。
-- 执行前核对：t_period_close 存在 uk_period_reopen_target；SELECT COUNT(*) FROM t_period_close WHERE close_type='REOPEN' 为 0。
-- 回滚（仅当每个 reopens_close_id 至多一行时可执行）：
--   ALTER TABLE `t_period_close` DROP INDEX `idx_period_reopen_target`,
--     ADD UNIQUE KEY `uk_period_reopen_target` (`reopens_close_id`) COMMENT '保证业务唯一性：reopens_close_id';
--   ALTER TABLE `t_close_step` MODIFY COLUMN `step_type` VARCHAR(64) NOT NULL COMMENT '关账流程中的固定执行步骤。允许值：LOCK_SEQUENCE、CHECK_ASYNC_AND_JOURNAL、RECONCILE_ACCOUNT_BALANCES、GENERATE_TRIAL_BALANCE、RESOLVE_EXCEPTIONS、POST_PROFIT_CLOSE、FINAL_TRIAL_BALANCE。';
SET NAMES utf8mb4;

-- R1：被驳回的重开不应阻止对同一原关账再次发起重开；并发由 uk_period_close_active 保证。
ALTER TABLE `t_period_close`
  DROP INDEX `uk_period_reopen_target`,
  ADD KEY `idx_period_reopen_target` (`reopens_close_id`) COMMENT '支持访问路径：reopens_close_id';

-- 重开流程步骤类型（列结构不变，仅更新允许值注释）。
ALTER TABLE `t_close_step` MODIFY COLUMN `step_type` VARCHAR(64) NOT NULL COMMENT '关账或重开流程中的固定执行步骤。允许值：LOCK_SEQUENCE、CHECK_ASYNC_AND_JOURNAL、RECONCILE_ACCOUNT_BALANCES、GENERATE_TRIAL_BALANCE、RESOLVE_EXCEPTIONS、POST_PROFIT_CLOSE、FINAL_TRIAL_BALANCE、PLAN_REOPEN_REVERSAL、POST_REOPEN_REVERSAL。';
