-- Operations Webhook 管理：本机验收数据清理（只在 mor_platform_e2e）。只删两个合成租户及其 Webhook 数据与补偿任务；审计事件与 Vault 合成密文保留。
START TRANSACTION;
DELETE a FROM t_webhook_attempt a JOIN t_webhook_delivery d ON d.delivery_id=a.delivery_id WHERE d.tenant_id IN (880000000000079001,880000000000079002);
DELETE c FROM t_compensation_event c JOIN t_webhook_delivery d ON c.event_key=CAST(d.delivery_id AS CHAR)
 WHERE c.event_type='WEBHOOK_DELIVERY' AND d.tenant_id IN (880000000000079001,880000000000079002);
DELETE FROM t_webhook_delivery WHERE tenant_id IN (880000000000079001,880000000000079002);
DELETE FROM t_webhook_event WHERE tenant_id IN (880000000000079001,880000000000079002);
DELETE s FROM t_webhook_signing_secret s JOIN t_webhook_endpoint e ON e.endpoint_id=s.endpoint_id WHERE e.tenant_id IN (880000000000079001,880000000000079002);
DELETE FROM t_webhook_endpoint WHERE tenant_id IN (880000000000079001,880000000000079002);
DELETE FROM t_access_tenant WHERE tenant_id IN (880000000000079001,880000000000079002) AND tenant_type='CLIENT';
COMMIT;
