-- 外部 API 第 2 步 2e：在本机独立库启用注册数据（30 路由 / 5 资源 / 2 条 PLATFORM_ADMIN 授权，并递增角色版本）。
-- 只在 mor_platform_external_api 执行；同一连接、不用 --force。核对 30/5/2/1 后 COMMIT，否则 ROLLBACK。
START TRANSACTION;
SET @plt_ext_activation_time=UTC_TIMESTAMP(6);
SELECT role_id,version INTO @plt_ext_admin,@plt_ext_admin_version FROM t_role
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE' FOR UPDATE;
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_external_api' AND route_id BETWEEN 900000000000071001 AND 900000000000071030
   AND channel='OPERATIONS' AND status='DISABLED' AND version=1;
SET @plt_ext_routes=ROW_COUNT();
UPDATE t_console_resource SET status='ACTIVE',version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_external_api' AND resource_id BETWEEN 900000000000071301 AND 900000000000071305
   AND channel='OPERATIONS' AND status='DISABLED' AND version=1;
SET @plt_ext_resources=ROW_COUNT();
UPDATE t_role_permission SET status='ACTIVE',version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_external_api' AND role_permission_id IN (900000000000071201,900000000000071202)
   AND role_id=@plt_ext_admin AND status='REVOKED' AND version=1;
SET @plt_ext_grants=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_external_api' AND role_id=@plt_ext_admin AND version=@plt_ext_admin_version;
SET @plt_ext_role=ROW_COUNT();
SELECT @plt_ext_routes routes_30,@plt_ext_resources resources_5,@plt_ext_grants grants_2,@plt_ext_role role_1;
