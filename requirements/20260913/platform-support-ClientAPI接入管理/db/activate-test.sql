-- REVIEWED CANDIDATE; authorized by user deployment request. Target: mor-test / mor_platform only.
-- Execute in ONE connection after disabled dml.sql registration and all three new images are healthy.
-- No COMMIT here: inspect affected_rows = 9,4,4,1, then COMMIT in the same connection; otherwise ROLLBACK.
-- If current versions differ, stop and review the actual delta. Never reset versions to force execution.
START TRANSACTION;
SELECT role_id,role_code,status,version FROM t_role
 WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM'
   AND system_role=1 AND status='ACTIVE' FOR UPDATE;
SET @plt_activation_time=UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=@plt_activation_time
 WHERE route_id BETWEEN 900000000000051001 AND 900000000000051009
   AND status='DISABLED' AND version=1
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=18 AND status='ACTIVE');
SET @plt_activated_routes=ROW_COUNT();
UPDATE t_console_resource SET status='ACTIVE',version=version+1,update_time=@plt_activation_time
 WHERE resource_id BETWEEN 900000000000051301 AND 900000000000051304
   AND status='DISABLED' AND version=1
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=18 AND status='ACTIVE');
SET @plt_activated_resources=ROW_COUNT();
UPDATE t_role_permission SET status='ACTIVE',version=version+1,update_time=@plt_activation_time
 WHERE role_permission_id BETWEEN 900000000000051201 AND 900000000000051204
   AND role_id=900000000000000510 AND status='REVOKED' AND version=1
   AND EXISTS (SELECT 1 FROM t_role WHERE role_id=900000000000000510 AND version=18 AND status='ACTIVE');
SET @plt_activated_grants=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@plt_activation_time
 WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM'
   AND system_role=1 AND status='ACTIVE' AND version=18;
SET @plt_activated_role=ROW_COUNT();
SELECT @plt_activated_routes routes_9,@plt_activated_resources resources_4,
       @plt_activated_grants grants_4,@plt_activated_role role_1;
-- Manual decision: COMMIT only for 9/4/4/1, otherwise ROLLBACK. Do not disconnect before deciding.
