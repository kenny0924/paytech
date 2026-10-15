-- Operations 通知发送账号维护：本机验收数据清理（阶段 5，只在本机 mor_platform_e2e 执行）
-- 删除验收新建的 E2E_PROV_ 账号；它们未被路由引用。审计事件为追加记录，保留。
-- Vault 中为合成密钥生成的密文同样保留（Vault V1 无 Retire）；合成值不对应任何真实凭据。
START TRANSACTION;
DELETE FROM t_notification_provider WHERE provider_code LIKE 'E2E!_PROV!_%' ESCAPE '!';
SELECT ROW_COUNT() AS providers;
COMMIT;
