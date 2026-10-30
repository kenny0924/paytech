-- 联合查询阶段 5（本机 mor_platform_v2e2e 专用，非注册块）：在 REQ-05 本机启用（40-data-query-admin-local.sql）之上，
-- 再启用平台管理员角色的 DATA_QUERY_EXECUTE 授权（131204，原 REVOKED；联合查询 J1—J3 与单模板执行需要），
-- 块 131 其余 10 条路由（删除 131407 除外，原 DISABLED：数据源详情 / 修改、模板详情 / 修改 / 版本列表 / 版本详情 / 恢复 / 校验 /
-- 试运行、单模板执行 131418），以及数据查询菜单组与其下菜单、按钮（131301—131305，原 DISABLED；块 144 的两个菜单挂在 131301 下）。
-- 回滚：60-data-query-local-rollback.sql。
START TRANSACTION;
UPDATE t_role_permission SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE role_permission_id = 900000000000131204 AND status = 'REVOKED';
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE route_id IN (900000000000131402, 900000000000131404, 900000000000131409, 900000000000131411, 900000000000131413,
  900000000000131414, 900000000000131415, 900000000000131416, 900000000000131417, 900000000000131418) AND status = 'DISABLED';
UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = UTC_TIMESTAMP(6)
WHERE resource_id BETWEEN 900000000000131301 AND 900000000000131305 AND status = 'DISABLED';
COMMIT;
SELECT route_id, route_code, status FROM t_gateway_route WHERE route_id BETWEEN 900000000000131401 AND 900000000000131418 ORDER BY 1;
SELECT role_permission_id, status FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000131201 AND 900000000000131204;
SELECT resource_id, resource_code, status FROM t_console_resource WHERE resource_id BETWEEN 900000000000131301 AND 900000000000131305;
