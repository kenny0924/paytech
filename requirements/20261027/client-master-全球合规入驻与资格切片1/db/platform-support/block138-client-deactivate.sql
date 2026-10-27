-- Platform 块 138 停用（启用的逆操作），只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 生成（block138-client-spec.json）。
-- 注册行保留；恢复 client-onboarding-c03（只在旧 BFF / client-master 仍提供 form-options 时有意义）。
-- 预期影响：路由 5、资源 0、授权 0、c03 0、角色 0。
START TRANSACTION;
SET @b138_toggle_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=@b138_toggle_time
 WHERE route_id BETWEEN 900000000000138441 AND 900000000000138445 AND status='ACTIVE';
SELECT ROW_COUNT() AS routes;
COMMIT;
