-- 文件多存储并存 · 存储登记后只读核对（阶段 7 第 8 步 stage7_register_storage.py 之后；root；不写任何数据）。
-- 期望：实例 MINIO（MINIO / STATIC / ACTIVE，Token 已存、表内无密钥原值——原值核对由验收脚本在本机内存中完成）、
--       桶 mor-platform-files ACTIVE 且最近检查 OK、默认路由 * → 该桶 + CLIENT_ONBOARDING_FORMAT、900 秒。
SET NAMES utf8mb4;
SELECT '实例 MINIO ACTIVE（MINIO / STATIC / Token 已存 / 带私有 CA）' AS item, COUNT(*) AS actual, 1 AS expect
  FROM mor_platform.t_file_storage_instance
 WHERE instance_code='MINIO' AND storage_type='MINIO' AND credential_mode='STATIC' AND status='ACTIVE'
   AND credential_vault_token IS NOT NULL AND endpoint='https://minio.mor-test.localhost:29400' AND region='us-east-1'
   AND path_style_access=1 AND trusted_certificate_pem LIKE '-----BEGIN CERTIFICATE-----%'
UNION ALL SELECT '桶 mor-platform-files ACTIVE 且最近检查 OK', COUNT(*), 1
  FROM mor_platform.t_file_storage_bucket b JOIN mor_platform.t_file_storage_instance i ON i.instance_id=b.instance_id
 WHERE i.instance_code='MINIO' AND b.bucket_name='mor-platform-files' AND b.status='ACTIVE' AND b.last_check_result='OK'
UNION ALL SELECT '默认路由 * → MINIO / mor-platform-files / CLIENT_ONBOARDING_FORMAT / 900', COUNT(*), 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id=r.bucket_id
  JOIN mor_platform.t_file_storage_instance i ON i.instance_id=b.instance_id
 WHERE r.source_system='*' AND i.instance_code='MINIO' AND b.bucket_name='mor-platform-files'
   AND r.scan_policy_code='CLIENT_ONBOARDING_FORMAT' AND r.max_upload_seconds=900
UNION ALL SELECT '旧记录的实例与桶均已登记且可读', COUNT(*), 0 FROM (
  SELECT DISTINCT storage_provider, storage_container_ref FROM mor_platform.t_file_object) f
  WHERE NOT EXISTS (SELECT 1 FROM mor_platform.t_file_storage_instance i JOIN mor_platform.t_file_storage_bucket b ON b.instance_id=i.instance_id
    WHERE i.instance_code=f.storage_provider AND b.bucket_name=f.storage_container_ref AND i.status='ACTIVE' AND b.status IN ('ACTIVE','READ_ONLY'))
UNION ALL SELECT 'Platform 对 FILE_STORAGE_CREDENTIAL_V1 的 PROTECT 成功次数 ≥ 1', LEAST(COUNT(*), 1), 1 FROM mor_vault.t_vault_access_event
  WHERE caller_service='platform-support-service' AND data_type='FILE_STORAGE_CREDENTIAL_V1' AND action='PROTECT' AND outcome='SUCCESS';
SELECT instance_code, storage_type, credential_mode, status, version, credential_update_time FROM mor_platform.t_file_storage_instance;
SELECT b.bucket_name, b.status, b.last_check_result, b.last_check_time, b.version FROM mor_platform.t_file_storage_bucket b;
SELECT source_system, scan_policy_code, max_upload_seconds, version FROM mor_platform.t_file_storage_route;
SELECT action, purpose, outcome, result_code, COUNT(*) AS n FROM mor_vault.t_vault_access_event
 WHERE caller_service='platform-support-service' AND data_type='FILE_STORAGE_CREDENTIAL_V1' GROUP BY action, purpose, outcome, result_code;
