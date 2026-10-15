-- 需求：20261015 payment-接入Risk统一评估（技术方案第 4 节）
-- 性质：增量执行文件，只修改一个字段的注释；列类型、可空、默认值与位置保持原样，不影响数据，可重复执行。
-- 目标库：Payment 业务库。新建库使用已同步的 Schema 与测试 init/ddl.sql；已有库（含本机测试库）注释不同不影响运行与测试。
-- 执行：仅在已有环境需要同步注释时，由用户授权后手工执行；应用不自动执行。
-- 回滚：如需恢复原注释，执行文件末尾注释中的语句。

ALTER TABLE `t_payment_action`
  MODIFY COLUMN `transaction_assessment_id` BIGINT NULL
  COMMENT 'Risk 统一评估返回的评估 ID（evaluationId）；仅 PAY_RISK 取得结论后填写，已知后不可替换；渠道动作为空。';

-- 回滚：
-- ALTER TABLE `t_payment_action`
--   MODIFY COLUMN `transaction_assessment_id` BIGINT NULL
--   COMMENT 'Risk 返回的原交易评估稳定标识；仅 PAY_RISK 可填，取得可信原评估身份前为空，已知后不可替换；渠道动作为空。';
