-- 多语言管理 · 停用块 135（回滚用，mor_platform）：授权 ACTIVE → REVOKED、菜单 / 按钮与路由 ACTIVE → DISABLED，角色 510 版本加一。注册行保留。
-- 本文件不含 COMMIT（apply_sql.sh deactivate-dry / deactivate）。预期 3 / 11 / 27 / 1。执行后等 Gateway Snapshot 刷新（≤ 60 秒）。
-- 停用后两个 manifest 路由不再匹配：两个控制台的语言包读取失败，按设计回退到构建内置的基准文案（ops-web 中文、client-web 英文）。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
START TRANSACTION;
SET @i18n_deactivation_time = UTC_TIMESTAMP(6);
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=@i18n_deactivation_time
 WHERE role_permission_id BETWEEN 900000000000135201 AND 900000000000135203 AND status='ACTIVE';
SET @grants = ROW_COUNT();
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=@i18n_deactivation_time
 WHERE resource_id BETWEEN 900000000000135301 AND 900000000000135311 AND status='ACTIVE';
SET @resources = ROW_COUNT();
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@i18n_deactivation_time
 WHERE route_id BETWEEN 900000000000135401 AND 900000000000135427 AND status='ACTIVE';
SET @routes = ROW_COUNT();
UPDATE t_role SET version=version+1, update_time=@i18n_deactivation_time WHERE role_id=900000000000000510;
SET @role = ROW_COUNT();
SELECT @grants AS grants_3, @resources AS resources_11, @routes AS routes_27, @role AS role_1;
SELECT IF(@grants = 3 AND @resources = 11 AND @routes = 27 AND @role = 1,
  'deactivate 3/11/27/1 ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
