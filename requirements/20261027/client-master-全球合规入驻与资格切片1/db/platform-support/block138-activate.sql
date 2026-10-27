-- Platform 块 138 启用，只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 生成（block138-spec.json）。
-- 前置：registration-block138.sql 已提交；BFF / Web 新镜像 healthy；Web 已不再调用 form-options。同时停用被 form-schema 取代的 client-onboarding-c03（主会话决定 5）。
-- 预期影响：路由 18、资源 7、授权 7、c03 1、角色 1。
START TRANSACTION;
SET @b138_toggle_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@b138_toggle_time
 WHERE route_id BETWEEN 900000000000138401 AND 900000000000138418 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@b138_toggle_time
 WHERE resource_id BETWEEN 900000000000138301 AND 900000000000138307 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@b138_toggle_time
 WHERE role_permission_id BETWEEN 900000000000138201 AND 900000000000138207 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@b138_toggle_time
 WHERE route_code IN ('client-onboarding-c03') AND status='ACTIVE';
SELECT ROW_COUNT() AS form_options_route;
UPDATE t_role SET version=version+1, update_time=@b138_toggle_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
