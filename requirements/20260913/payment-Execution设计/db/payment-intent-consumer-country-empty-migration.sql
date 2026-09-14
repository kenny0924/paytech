-- Payment 禁止反向调用 Commerce 的配套空表迁移；尚未执行。
-- 执行前须停止 Payment 写入、备份并确认 t_payment_intent 和 CREATE_INTENT_V1 命令均为空。
-- 仅适用于经明确授权的本地 Payment 数据库；有历史数据时禁止执行或猜测国家回填。
-- 旧 Execution 库另需先按 instrument-token-empty-migration.sql 完成工具模型迁移。
-- 应用和测试不会执行此文件；不能将更新模型视为数据库执行授权。

ALTER TABLE `t_payment_intent`
  ADD COLUMN `consumer_country_code` CHAR(2) NOT NULL COMMENT 'Commerce 创建 Intent 时传入并冻结的消费者国家，用于支付定价；后续不得覆盖。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `mcc_code`;
