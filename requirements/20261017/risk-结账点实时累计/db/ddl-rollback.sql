-- 需求：requirements/20261017/risk-结账点实时累计 ddl.sql 的回滚（未在任何环境执行）。目标库：Risk（mor_risk）。
-- 前置：已换回旧 Risk 镜像，或确认不再需要新索引。注释回滚不影响数据；索引回滚只影响查询耗时。
-- 注意：若库中已有 CHECKOUT_* 事件编码的 Feature 定义，注释回滚后与数据不符，应保持新注释（见 design.md 第 9 节）。

ALTER TABLE `t_velocity_feature_definition`
  MODIFY COLUMN `event_code` VARCHAR(64) NOT NULL COMMENT '事件编码的稳定业务代码。允许值：PAYMENT_ATTEMPT_ASSESSED、PAYMENT_ATTEMPT_REJECTED。';

ALTER TABLE `t_list_feature_definition`
  COMMENT='冻结一个名单命中 Feature Version 的名单与主体类型，主体取值由类型对应的支付输入字段决定；创建后不可修改';

ALTER TABLE `t_velocity_event_subject`
  DROP INDEX `idx_velocity_subject`,
  ADD INDEX `idx_velocity_subject` (`subject_type`, `subject_key`, `event_code`, `occurred_time`)
    COMMENT '支持访问路径：subject_type、subject_key、event_code、occurred_time',
  ALGORITHM=INPLACE, LOCK=NONE;
