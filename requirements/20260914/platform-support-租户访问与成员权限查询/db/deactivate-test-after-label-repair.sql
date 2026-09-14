-- Stage 7 recovery after the three UTF-8 display-name corrections; NOT APPLIED.
-- Supersedes deactivate-test.sql for this deployed version only; preserve all data.
SET NAMES utf8mb4;
-- Target: desktop-linux / mor-test-mysql-1 / 127.0.0.1:23306 / mor_platform.
-- Only valid immediately after this release activation, with no later catalog/role changes.
-- Lock and inspect exact current role version 20; inspect all affected row counts before COMMIT.
START TRANSACTION;
SELECT role_id,role_code,status,version FROM t_role
 WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM'
   AND system_role=1 AND status='ACTIVE' FOR UPDATE;
SET @plt_tenant_deactivation_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED',version=version+1,updated_by=221546852372267008,update_time=@plt_tenant_deactivation_time
 WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000061001 AND 900000000000061008
   AND target_id=900000000000000001 AND channel='OPERATIONS' AND status='ACTIVE' AND version=2
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=20 AND status='ACTIVE');
SET @plt_tenant_routes=ROW_COUNT();
UPDATE t_console_resource SET status='DISABLED',version=version+1,update_time=@plt_tenant_deactivation_time
 WHERE DATABASE()='mor_platform' AND resource_id BETWEEN 900000000000061301 AND 900000000000061303
   AND channel='OPERATIONS' AND status='ACTIVE' AND version=3
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=20 AND status='ACTIVE');
SET @plt_tenant_resources=ROW_COUNT();
UPDATE t_role_permission SET status='REVOKED',version=version+1,update_time=@plt_tenant_deactivation_time
 WHERE DATABASE()='mor_platform' AND role_permission_id BETWEEN 900000000000061201 AND 900000000000061203
   AND role_id=900000000000000510 AND permission_id BETWEEN 900000000000061101 AND 900000000000061103
   AND status='ACTIVE' AND version=2
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=20 AND status='ACTIVE');
SET @plt_tenant_grants=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@plt_tenant_deactivation_time
 WHERE DATABASE()='mor_platform' AND role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM'
   AND system_role=1 AND status='ACTIVE' AND version=20;
SET @plt_tenant_role=ROW_COUNT();
SELECT @plt_tenant_routes routes_8,@plt_tenant_resources resources_3,@plt_tenant_grants grants_3,@plt_tenant_role role_1;
-- Manual decision: COMMIT only for 8/3/3/1; otherwise ROLLBACK. Versions advance, never reset.
