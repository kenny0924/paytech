-- 多语言管理 · 激活块 135（mor_platform）：PLATFORM_ADMIN 的 3 个授权 REVOKED → ACTIVE、11 个菜单 / 按钮与 27 条路由 DISABLED → ACTIVE，
-- 角色 510 版本加一使已登录会话重新计算授权与导航（同块 134 激活）。来源：../../db/dml.sql 文件头“Activation”段 + 角色版本。
-- 前提：platform-support、两个 BFF、两个 Web 新镜像都已 healthy；10 / 11 / 12 已提交。执行后等 Gateway Snapshot 刷新（≤ 60 秒）。
-- 本文件不含 COMMIT（apply_sql.sh activate-dry / activate）。预期 3 / 11 / 27 / 1。回滚：21-deactivate-block135.sql。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
START TRANSACTION;
SET @i18n_activation_time = UTC_TIMESTAMP(6);
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@i18n_activation_time
 WHERE role_permission_id BETWEEN 900000000000135201 AND 900000000000135203 AND status='REVOKED';
SET @grants = ROW_COUNT();
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@i18n_activation_time
 WHERE resource_id BETWEEN 900000000000135301 AND 900000000000135311 AND status='DISABLED';
SET @resources = ROW_COUNT();
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@i18n_activation_time
 WHERE route_id BETWEEN 900000000000135401 AND 900000000000135427 AND status='DISABLED';
SET @routes = ROW_COUNT();
UPDATE t_role SET version=version+1, update_time=@i18n_activation_time WHERE role_id=900000000000000510;
SET @role = ROW_COUNT();
SELECT @grants AS grants_3, @resources AS resources_11, @routes AS routes_27, @role AS role_1;
SELECT IF(@grants = 3 AND @resources = 11 AND @routes = 27 AND @role = 1,
  'activate 3/11/27/1 ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
