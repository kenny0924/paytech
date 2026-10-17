-- Operations Webhook 管理：本机验收合成数据（只在 mor_platform_e2e）。两个合成 CLIENT 租户，名称带验收前缀便于搜索。
START TRANSACTION;
INSERT INTO t_access_tenant(tenant_id,tenant_type,owner_ref_id,tenant_name,owner_status,security_status,version,create_time,update_time) VALUES
 (880000000000079001,'CLIENT',880000000000079101,'E2E Webhook Acme Trading','ACTIVE','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
 (880000000000079002,'CLIENT',880000000000079102,'E2E Webhook Acme Paused','ACTIVE','SUSPENDED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
COMMIT;
