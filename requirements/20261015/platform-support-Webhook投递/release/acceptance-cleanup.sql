-- Client Webhook：mor-test 验收数据清理（阶段 7 最后执行，只在 mor-test mor_platform）。
-- 只删除验收用的合成租户（880000000000079001，owner_ref_id 880000000000079101）及其 Webhook 数据与补偿任务；
-- 审计事件与 Vault 中的合成密钥密文保留。先确认补偿任务均已结束（execute_status 不为 INIT/PROCESSING）。
START TRANSACTION;
DELETE a FROM t_webhook_attempt a JOIN t_webhook_delivery d ON d.delivery_id=a.delivery_id WHERE d.tenant_id=880000000000079001;
DELETE c FROM t_compensation_event c JOIN t_webhook_delivery d ON c.event_key=CAST(d.delivery_id AS CHAR)
 WHERE c.event_type='WEBHOOK_DELIVERY' AND d.tenant_id=880000000000079001;
DELETE FROM t_webhook_delivery WHERE tenant_id=880000000000079001;
DELETE FROM t_webhook_event WHERE tenant_id=880000000000079001;
DELETE s FROM t_webhook_signing_secret s JOIN t_webhook_endpoint e ON e.endpoint_id=s.endpoint_id WHERE e.tenant_id=880000000000079001;
DELETE FROM t_webhook_endpoint WHERE tenant_id=880000000000079001;
DELETE FROM t_access_tenant WHERE tenant_id=880000000000079001 AND tenant_type='CLIENT' AND owner_ref_id=880000000000079101;
COMMIT;
