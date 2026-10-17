-- 需求：requirements/20261017/risk-结账点实时累计（阶段 3 待执行文件，未在任何环境执行）。目标库：Risk（mor_risk）。
-- 性质：增量执行文件；阶段 4 以 PDManer 模型生成的完整 Schema（risk-compliance-schema.sql，表数仍为 85）对照核对后定稿；可重复执行（结果相同）。
-- 前置：三张表均存在；idx_velocity_subject 为旧的 4 列定义（执行前 SHOW INDEX 确认）。
-- 兼容：旧 Risk 代码不受影响（只改注释；索引追加尾列不改变任何查询结果），可在旧 Risk 运行时执行。
-- 回滚：ddl-rollback.sql。

-- 1. 事件编码允许值增加结账两项（只改注释，元数据变更）。
ALTER TABLE `t_velocity_feature_definition`
  MODIFY COLUMN `event_code` VARCHAR(64) NOT NULL COMMENT '事件编码的稳定业务代码。允许值：PAYMENT_ATTEMPT_ASSESSED、PAYMENT_ATTEMPT_REJECTED、CHECKOUT_ASSESSED、CHECKOUT_REJECTED。';

-- 2. 名单 Feature 表注释：主体取值不再限于支付输入（只改注释）。
ALTER TABLE `t_list_feature_definition`
  COMMENT='冻结一个名单命中 Feature Version 的名单与主体类型，主体取值由类型对应的风控点输入字段决定；创建后不可修改';

-- 3. 窗口查询索引追加 velocity_event_id：“最近一次”可按索引倒序取第一条，不再排序（在线重建）。
ALTER TABLE `t_velocity_event_subject`
  DROP INDEX `idx_velocity_subject`,
  ADD INDEX `idx_velocity_subject` (`subject_type`, `subject_key`, `event_code`, `occurred_time`, `velocity_event_id`)
    COMMENT '支持访问路径：subject_type、subject_key、event_code、occurred_time、velocity_event_id',
  ALGORITHM=INPLACE, LOCK=NONE;
