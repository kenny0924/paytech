-- 回滚 30-platform-tax-file-route-local.sql（mor_platform_v2e2e）。先确认 tax-service 已上传文件的处理方式：
-- SELECT COUNT(*) FROM t_file_upload WHERE source_system = 'tax-service';  -- 非 0 时只保留路由，不删除（已上传文件仍引用该桶）。
DELETE FROM t_file_storage_route WHERE route_id = 900000000000990801 AND source_system = 'tax-service';
