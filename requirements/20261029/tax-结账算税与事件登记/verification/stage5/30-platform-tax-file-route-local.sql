-- 本机专用（阶段 5，只在 mor_platform_v2e2e 执行）：tax-service 私有文件写入路由，供简化税票上传（reasonCode TAX_RECEIPT）。
-- 对应 db/platform-registration.sql 第 C 段；ID 使用本机专用段 900000000000990801，不是阶段 6 注册块。
-- 桶、扫描策略与上传期限复制默认私有路由 (*, PRIVATE)（本机为 CLIENT_ONBOARDING_FORMAT，允许 application/pdf）。
-- 回滚：30-platform-tax-file-route-local-rollback.sql。
SET NAMES utf8mb4;
INSERT INTO t_file_storage_route (route_id, source_system, access_level, bucket_id, scan_policy_code, max_upload_seconds, version, create_time, update_time)
SELECT 900000000000990801, 'tax-service', 'PRIVATE', bucket_id, scan_policy_code, max_upload_seconds, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM t_file_storage_route WHERE source_system = '*' AND access_level = 'PRIVATE';
SELECT route_id, source_system, access_level, bucket_id, scan_policy_code FROM t_file_storage_route WHERE route_id = 900000000000990801;
