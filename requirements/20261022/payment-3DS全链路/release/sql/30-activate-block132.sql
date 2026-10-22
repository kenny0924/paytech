-- 卡 BIN 库（块 132）mor-test 激活。目标库 mor_platform，客户端 utf8mb4。
-- 语句取自 22-mor_platform-registration-block132.sql 文件头的 Activation 段；另按工作流发布先例（20261021 release/test-activate.sql）
-- 递增 PLATFORM_ADMIN 角色版本，使已登录会话重新计算授权与导航。期望影响行数：授权 2 / 菜单 2 / 路由 9 / 角色 1。
-- 前置：22 已执行；card、ops-bff、ops-web 新镜像 healthy；ops-bff 写入路由已在存储管理页登记（release.md 第 6 节 S5）。
-- 本文件不含 COMMIT：scripts/apply_sql.sh <备份目录> activate132-dry 试运行（断开即回滚），activate132 追加 COMMIT 提交。
-- 回滚：90-rollback-deactivate.sql 的块 132 段。
START TRANSACTION;
SET @bin_activation_time = UTC_TIMESTAMP(6);
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@bin_activation_time
 WHERE DATABASE()='mor_platform' AND role_permission_id IN (900000000000132201,900000000000132202) AND status='REVOKED';
SET @bin_grants = ROW_COUNT();
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@bin_activation_time
 WHERE DATABASE()='mor_platform' AND resource_id IN (900000000000132301,900000000000132302) AND status='DISABLED';
SET @bin_resources = ROW_COUNT();
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@bin_activation_time
 WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000132401 AND 900000000000132409 AND status='DISABLED';
SET @bin_routes = ROW_COUNT();
UPDATE t_role SET version=version+1, update_time=@bin_activation_time
 WHERE DATABASE()='mor_platform' AND role_id=900000000000000510;
SET @bin_role = ROW_COUNT();
SELECT @bin_grants AS grants_2, @bin_resources AS resources_2, @bin_routes AS routes_9, @bin_role AS role_1,
       (SELECT version FROM t_role WHERE role_id=900000000000000510) AS platform_admin_version_after;
-- guard：任一数不符时本语句报错，mysql 客户端停止，未提交的事务随断开回滚。
SELECT IF(@bin_grants=2 AND @bin_resources=2 AND @bin_routes=9 AND @bin_role=1, 'counts ok',
          (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
