-- Operations 商务控制台（commercial 六层产品定价模型 E 线）：本机全链路激活，只写不执行
-- 只在本机 mor_platform_v2e2e 执行，客户端须 utf8mb4；mor-test 与其他环境另行授权，不沿用本文件。
-- 前置：已执行 registration-dml.sql 的块 95（权限 5、授权 5、菜单 / 按钮 8、路由 24）并 COMMIT；commercial-service 已注册到同一 Nacos Namespace。
-- 激活 24 条路由、8 个菜单 / 按钮资源、PLATFORM_ADMIN 的 5 个商务授权，并递增角色版本使会话重新计算授权。
-- 预期影响行数：24 / 8 / 5 / 1。
-- 注意：本机管理员同时持有 COMMERCIAL_PRICING_MANAGE 与 COMMERCIAL_PRICING_APPROVE；commercial 仍要求批准人 ≠ 提交人，
-- 走查审批需要第二个持有 APPROVE 的账号。
START TRANSACTION;
SET @commercial_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@commercial_act_time
 WHERE route_id BETWEEN 900000000000095401 AND 900000000000095424 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@commercial_act_time
 WHERE resource_id BETWEEN 900000000000095301 AND 900000000000095308 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@commercial_act_time
 WHERE role_permission_id BETWEEN 900000000000095201 AND 900000000000095205 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@commercial_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：同一组 ID 反向置 DISABLED / REVOKED，version+1，并再次递增角色 900000000000000510 的 version。
