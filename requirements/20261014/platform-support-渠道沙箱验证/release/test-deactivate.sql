-- 撤回 test-activate.sql：44 路由 / 5 菜单停用，2 条授权 REVOKED，递增角色版本使缓存失效。版本只增不减，不删除登记。
-- 适用前提：本期 44 路由与 5 资源 version=2、2 条授权 version=2，且之后没有其他配置改动；不符时停止并核对差异。
-- 只在 mor-test 的 mor_platform 执行；同一连接、--default-character-set=utf8mb4。核对 44/5/2/1 后 COMMIT，否则 ROLLBACK。
START TRANSACTION;
SET @rel_time=UTC_TIMESTAMP(6);
SELECT role_id,version INTO @rel_admin,@rel_admin_version FROM t_role
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 FOR UPDATE;
UPDATE t_gateway_route SET status='DISABLED',version=version+1,update_time=@rel_time
 WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000071001 AND 900000000000071044 AND status='ACTIVE' AND version=2;
SET @r=ROW_COUNT();
UPDATE t_console_resource SET status='DISABLED',version=version+1,update_time=@rel_time
 WHERE DATABASE()='mor_platform' AND resource_id BETWEEN 900000000000071301 AND 900000000000071305 AND status='ACTIVE' AND version=2;
SET @c=ROW_COUNT();
UPDATE t_role_permission SET status='REVOKED',version=version+1,update_time=@rel_time
 WHERE DATABASE()='mor_platform' AND role_permission_id IN (900000000000071201,900000000000071202) AND status='ACTIVE' AND version=2;
SET @g=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@rel_time WHERE DATABASE()='mor_platform' AND role_id=@rel_admin AND version=@rel_admin_version;
SELECT @r routes_44,@c resources_5,@g grants_2,ROW_COUNT() role_1;
