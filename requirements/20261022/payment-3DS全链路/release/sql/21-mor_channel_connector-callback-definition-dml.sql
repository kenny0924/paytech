-- 发布副本（3DS 全链路 + 卡 BIN 库合并发布，阶段 6）。目标库：mor_channel_connector。来源：Code-MoR/requirements/20261022/payment-3DS全链路/db/channel-connector/dml.sql（SHA-256 846e897cdae08667，3DS 需求 db；mor-test 无 PAYERMAX_MOCK，预期影响 1 行，见 release.md 需要决定 N3）。
-- 以下为来源文件全文逐字副本，未改动业务语句。
-- 需求：payment-3DS全链路（requirements/20261022/payment-3DS全链路）
-- 目标库：mor_channel_connector
-- 文件性质：未执行草案（阶段 3 评审稿）。须在 channel-connector/ddl.sql 执行、且新版 channel-connector 部署后执行。
-- 内容：为 PAYERMAX、PAYERMAX_MOCK 两个支付渠道写入支付结果通知定义（按 channel_code 更新，各 1 行）。
--   签名原文按“sign 头 + 原始 Body + 账号参数 payermaxPublicKey”设计，PayerMax 文档未写明通知签名原文，待 UAT 实测；
--   实测不符时只改本行定义中的 signature 部分。
-- 前置：两个渠道存在、channel_type=PAYMENT，callback_definition_json 为空（执行前查询确认）。
-- 可重跑：是（UPDATE 为同值覆盖；version 每次 +1）。
-- 回滚：UPDATE t_channel SET callback_definition_json = NULL, version = version + 1, update_time = UTC_TIMESTAMP(6)
--       WHERE channel_code IN ('PAYERMAX', 'PAYERMAX_MOCK');

START TRANSACTION;

SET @payermax_callback_definition = JSON_OBJECT(
  'signature', JSON_OBJECT('algorithm', 'RSA_SHA256', 'headerName', 'sign', 'publicKeyParameter', 'payermaxPublicKey'),
  'fields', JSON_OBJECT('merchantReference', 'data.outTradeNo', 'channelTransactionRef', 'data.tradeToken',
                        'status', 'data.status', 'eventTime', 'data.completeTime'),
  'acknowledgement', JSON_OBJECT('contentType', 'application/json', 'body', '{"code":"SUCCESS","msg":"Success"}'));

UPDATE t_channel
SET callback_definition_json = @payermax_callback_definition, version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE channel_code IN ('PAYERMAX', 'PAYERMAX_MOCK') AND channel_type = 'PAYMENT';

-- 预期影响 2 行；不是 2 行时 ROLLBACK 并核对渠道目录。
SELECT channel_code, JSON_EXTRACT(callback_definition_json, '$.signature.algorithm') AS algorithm
FROM t_channel WHERE channel_code IN ('PAYERMAX', 'PAYERMAX_MOCK');

COMMIT;
