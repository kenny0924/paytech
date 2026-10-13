-- 回滚 activate.sql：恢复为停用 / REVOKED，递增版本使缓存失效；只在 mor_platform_external_api 执行，核对 30/5/2/1 后 COMMIT。
START TRANSACTION;
SET @plt_ext_rollback_time=UTC_TIMESTAMP(6);
SELECT role_id INTO @plt_ext_admin FROM t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 FOR UPDATE;
UPDATE t_gateway_route SET status='DISABLED',version=version+1,update_time=@plt_ext_rollback_time
 WHERE DATABASE()='mor_platform_external_api' AND route_id BETWEEN 900000000000071001 AND 900000000000071030 AND status='ACTIVE';
SET @r=ROW_COUNT();
UPDATE t_console_resource SET status='DISABLED',version=version+1,update_time=@plt_ext_rollback_time
 WHERE DATABASE()='mor_platform_external_api' AND resource_id BETWEEN 900000000000071301 AND 900000000000071305 AND status='ACTIVE';
SET @c=ROW_COUNT();
UPDATE t_role_permission SET status='REVOKED',version=version+1,update_time=@plt_ext_rollback_time
 WHERE DATABASE()='mor_platform_external_api' AND role_permission_id IN (900000000000071201,900000000000071202) AND status='ACTIVE';
SET @g=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@plt_ext_rollback_time WHERE DATABASE()='mor_platform_external_api' AND role_id=@plt_ext_admin;
SELECT @r routes_30,@c resources_5,@g grants_2,ROW_COUNT() role_1;
