-- Recovery candidate; only for the immediately activated baseline with no subsequent configuration writes.
-- Verify exact IDs/codes against seed-manifest.json and backup first. This retains inserted records and audit history.
-- Use ONE connection. COMMIT only after affected_rows = 18,4,4,1; otherwise ROLLBACK and reassess changed rows.
START TRANSACTION;
SELECT role_id,role_code,status,version FROM t_role
 WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM'
   AND system_role=1 AND status='ACTIVE' FOR UPDATE;
SET @plt_deactivation_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED',version=version+1,update_time=@plt_deactivation_time
 WHERE route_id BETWEEN 900000000000041001 AND 900000000000041018 AND status='ACTIVE' AND version=2
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=18 AND status='ACTIVE');
SET @plt_deactivated_routes=ROW_COUNT();
UPDATE t_console_resource SET status='DISABLED',version=version+1,update_time=@plt_deactivation_time
 WHERE resource_id BETWEEN 900000000000041301 AND 900000000000041304 AND status='ACTIVE' AND version=2
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=18 AND status='ACTIVE');
SET @plt_deactivated_resources=ROW_COUNT();
UPDATE t_role_permission SET status='REVOKED',version=version+1,update_time=@plt_deactivation_time
 WHERE role_permission_id BETWEEN 900000000000041201 AND 900000000000041204
   AND role_id=900000000000000510 AND status='ACTIVE' AND version=2
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=18 AND status='ACTIVE');
SET @plt_deactivated_grants=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@plt_deactivation_time
 WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM'
   AND system_role=1 AND status='ACTIVE' AND version=18;
SET @plt_deactivated_role=ROW_COUNT();
SELECT @plt_deactivated_routes routes_18,@plt_deactivated_resources resources_4,
       @plt_deactivated_grants grants_4,@plt_deactivated_role role_1;
-- Manual decision: COMMIT only for 18/4/4/1, otherwise ROLLBACK. Versions advance; never revert to 17.
