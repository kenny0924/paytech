-- 工作流（块 127）：mor-test 启用（阶段 7，只在 mor-test mor_platform 执行，客户端 utf8mb4）。
-- 前置：sql/01、02 已执行；sql/10-registration-block127.sql（416 行）已执行并 COMMIT；platform、ops-bff、ops-web 新镜像已 healthy。
-- 启用 32 条路由（127401—127432）、10 个菜单组 / 菜单 / 按钮（127301—127310）、PLATFORM_ADMIN 的 7 个授权（127201—127207），
-- 并递增角色 900000000000000510 的版本使会话重新计算授权与导航。期望影响行数：32 / 10 / 7 / 1。
-- 本文件不含 COMMIT：
--   · 试运行：scripts/apply_sql.sh <B> activate-dry  —— 客户端断开即隐式回滚，只看四个数；
--   · 提交：  scripts/apply_sql.sh <B> activate      —— 在本文件后追加 COMMIT；末尾 guard 不符时报错，客户端停止并断开，事务回滚。
-- 每条语句带 DATABASE()='mor_platform' 保护。回滚：sql/90-rollback-deactivate.sql。
START TRANSACTION;
SET @wf_activation_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@wf_activation_time
 WHERE DATABASE()='mor_platform' AND route_id BETWEEN 900000000000127401 AND 900000000000127432 AND status='DISABLED';
SET @wf_routes = ROW_COUNT();
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@wf_activation_time
 WHERE DATABASE()='mor_platform' AND resource_id BETWEEN 900000000000127301 AND 900000000000127310 AND status='DISABLED';
SET @wf_resources = ROW_COUNT();
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@wf_activation_time
 WHERE DATABASE()='mor_platform' AND role_permission_id BETWEEN 900000000000127201 AND 900000000000127207 AND status='REVOKED';
SET @wf_grants = ROW_COUNT();
UPDATE t_role SET version=version+1, update_time=@wf_activation_time
 WHERE DATABASE()='mor_platform' AND role_id=900000000000000510;
SET @wf_role = ROW_COUNT();
SELECT @wf_routes AS routes_32, @wf_resources AS resources_10, @wf_grants AS grants_7, @wf_role AS role_1,
       (SELECT version FROM t_role WHERE role_id=900000000000000510) AS platform_admin_version_after;
-- guard：四个数不符时本语句报错（子查询多行），mysql 客户端停止，未提交的事务随断开回滚。
SELECT IF(@wf_routes=32 AND @wf_resources=10 AND @wf_grants=7 AND @wf_role=1, 'counts ok',
          (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
