-- 本机全链路 A6：开放 PLT-16/PLT-17 配置管理（Gateway 目标/路由新建与修改、权限目录、页面资源），
-- 供后续通过管理接口登记入驻路由。只在本机 mor_platform 执行；回滚见文件末尾注释。
START TRANSACTION;
SET @a6_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@a6_time
 WHERE route_id BETWEEN 900000000000041001 AND 900000000000041018 AND status='DISABLED';
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@a6_time
 WHERE resource_id BETWEEN 900000000000041301 AND 900000000000041304 AND status='DISABLED';
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@a6_time
 WHERE role_permission_id BETWEEN 900000000000041201 AND 900000000000041204 AND status='REVOKED';
UPDATE t_role SET version=version+1, update_time=@a6_time WHERE role_id=900000000000000510;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，并再次递增角色 900000000000000510 的 version。
