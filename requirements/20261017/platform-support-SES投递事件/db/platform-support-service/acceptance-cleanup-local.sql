-- Platform SES 投递事件：删除本机验收数据（合成数据与验收脚本经接口新建的 E2E_SES_EVT_LIVE 账号）。
-- 审计事件为追加记录，不删除；LIVE 账号的合成密钥留在本机 Vault（假值，无对应 AWS 账号）。
START TRANSACTION;
DELETE FROM t_notification_attempt WHERE attempt_id BETWEEN 980000000000002041 AND 980000000000002045;
DELETE FROM t_notification_delivery WHERE delivery_id BETWEEN 980000000000002022 AND 980000000000002025;
DELETE FROM t_notification_request WHERE request_id = 980000000000002021;
DELETE FROM t_notification_template WHERE template_id = 980000000000002011;
DELETE FROM t_notification_provider WHERE provider_code LIKE 'E2E!_SES!_EVT!_%' ESCAPE '!';
COMMIT;
