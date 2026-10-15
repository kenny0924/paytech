-- Operations 通知发送账号维护：已执行认证邮件初始化块的环境补齐 SES_PRIMARY（待执行，需授权）。
-- 前置：ddl.sql 已执行。预期影响 1 行；发件地址与密钥在运营后台录入。
-- 回退：UPDATE t_notification_provider SET provider_type='', region=NULL, version=version+1, update_time=UTC_TIMESTAMP(6)
--       WHERE provider_code='SES_PRIMARY';（仅在同时回退 DDL 前使用）
START TRANSACTION;
UPDATE t_notification_provider
   SET provider_type = 'AMAZON_SES', region = 'me-central-1', version = version + 1, update_time = UTC_TIMESTAMP(6)
 WHERE provider_code = 'SES_PRIMARY' AND provider_type = '';
SELECT ROW_COUNT() AS updated_rows;
-- 核对 updated_rows = 1 后 COMMIT，否则 ROLLBACK。
