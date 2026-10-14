-- mor-test 启用外部 API（30 路由）与沙箱验证（14 路由）登记：44 路由 / 5 菜单资源 / 2 条 PLATFORM_ADMIN 授权，并递增角色版本。
-- 前置：两份 registration-dml.sql 已在 test mor_platform 提交，且新 Gateway/Platform/BFF/Web 均 healthy。
-- 只在 mor-test 的 mor_platform 执行；同一连接、--default-character-set=utf8mb4、不用 --force。核对 44/5/2/1 后 COMMIT，否则 ROLLBACK。
START TRANSACTION;
SET @rel_time=UTC_TIMESTAMP(6);
SELECT role_id,version INTO @rel_admin,@rel_admin_version FROM t_role
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE' FOR UPDATE;
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=@rel_time
 WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000071001 AND 900000000000071044
   AND channel='OPERATIONS' AND status='DISABLED' AND version=1;
SET @rel_routes=ROW_COUNT();
UPDATE t_console_resource SET status='ACTIVE',version=version+1,update_time=@rel_time
 WHERE DATABASE()='mor_platform' AND resource_id BETWEEN 900000000000071301 AND 900000000000071305
   AND channel='OPERATIONS' AND status='DISABLED' AND version=1;
SET @rel_resources=ROW_COUNT();
UPDATE t_role_permission SET status='ACTIVE',version=version+1,update_time=@rel_time
 WHERE DATABASE()='mor_platform' AND role_permission_id IN (900000000000071201,900000000000071202)
   AND role_id=@rel_admin AND status='REVOKED' AND version=1;
SET @rel_grants=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@rel_time
 WHERE DATABASE()='mor_platform' AND role_id=@rel_admin AND version=@rel_admin_version;
SET @rel_role=ROW_COUNT();
SELECT @rel_routes routes_44,@rel_resources resources_5,@rel_grants grants_2,@rel_role role_1;
-- 结果为 44/5/2/1 时 COMMIT，否则 ROLLBACK。
