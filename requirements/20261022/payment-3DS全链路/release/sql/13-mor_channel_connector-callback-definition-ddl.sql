-- 发布副本（3DS 全链路 + 卡 BIN 库合并发布，阶段 6）。目标库：mor_channel_connector。来源：Code-MoR/requirements/20261022/payment-3DS全链路/db/channel-connector/ddl.sql（SHA-256 6d4ef918697782f1，3DS 需求 db）。
-- 以下为来源文件全文逐字副本，未改动业务语句。
-- 需求：payment-3DS全链路（requirements/20261022/payment-3DS全链路）
-- 目标库：mor_channel_connector（channel-connector 自有库）
-- 文件性质：未执行草案（阶段 3 评审稿）。执行前须经用户授权；执行后同步 PDManer 与 channel-connector-schema.sql。
-- 内容：
--   1) t_channel 新增 callback_definition_json：渠道支付通知的验签、字段与应答定义（可空，未配置的渠道不接收通知）。
--   2) t_channel_callback_item.target_type 注释增加允许值 PAYMENT_ATTEMPT（结构不变）：支付结果通知映射到 Payment Attempt。
--   3) t_channel_callback.received_at 注释由 Payment 改为 channel-connector（表随 2026-10 渠道迁入归 channel-connector，结构不变）。
-- 前置：t_channel 不含 callback_definition_json；两张回调表已按 channel-connector-schema.sql 建成且为空。
-- 可重跑：否（ADD COLUMN 重复执行报错停止）。
-- 回滚：应用回滚后新列不再被读取，保留；如需删除执行 ALTER TABLE t_channel DROP COLUMN callback_definition_json。注释回滚执行原注释 MODIFY。

ALTER TABLE `t_channel`
  ADD COLUMN `callback_definition_json` JSON NULL COMMENT '支付渠道的支付结果通知定义：验签（algorithm 本期只允许 RSA_SHA256、签名请求头名、账号参数中的公钥名）、通知字段路径（merchantReference、channelTransactionRef、status、eventTime）与成功应答（contentType、body）；序列化后最多 4096 UTF-8 字节，禁止保存凭据或密钥；为空表示该渠道不接收通知，入站请求返回 404。' AFTER `endpoints`;

ALTER TABLE `t_channel_callback_item`
  MODIFY COLUMN `target_type` VARCHAR(64) NOT NULL COMMENT '回调项映射到的业务对象闭合类型；PAYMENT_ATTEMPT 表示支付结果通知按商户订单号映射到 Payment Attempt（target_id 为 Attempt ID，跨库引用不建外键）。允许值：PAYMENT_ATTEMPT、PAYMENT_ACTION、CHANNEL_REFUND、CHANNEL_DISPUTE_NOTICE、CHANNEL_DISPUTE_ACTION、UNKNOWN。';

ALTER TABLE `t_channel_callback`
  MODIFY COLUMN `received_at` DATETIME(6) NOT NULL COMMENT 'channel-connector 首次收到服务商回调或争议通知的业务时刻。';
