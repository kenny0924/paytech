-- Platform 块 138 启用，只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 生成（block138-client-spec.json）。
-- 前置：registration-block138.sql 已提交；BFF / Web 新镜像 healthy；Web 已不再调用 form-options。同时停用被 form-schema 取代的 client-onboarding-c03（主会话决定 5）。
-- 预期影响：路由 5、资源 0、授权 0、c03 0、角色 0。
START TRANSACTION;
SET @b138_toggle_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@b138_toggle_time
 WHERE route_id BETWEEN 900000000000138441 AND 900000000000138445 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
COMMIT;
