-- 多语言管理 · 首批语言包之后只读核对（第 6 节第 13 步之后）。ok=1 为期望。
-- operations-console：命名空间 2（i18n-admin、enum）× 语言 2 = 4 个语言包；client-console：命名空间 8 × 语言 2 = 16 个。
SELECT 'B01 operations-console namespaces' AS item, COUNT(*) AS actual, 2 AS expect, COUNT(*) = 2 AS ok
  FROM mor_platform.t_i18n_namespace n JOIN mor_platform.t_i18n_application a ON a.application_id = n.application_id
 WHERE a.application_code = 'operations-console'
UNION ALL SELECT 'B02 client-console namespaces', COUNT(*), 8, COUNT(*) = 8
  FROM mor_platform.t_i18n_namespace n JOIN mor_platform.t_i18n_application a ON a.application_id = n.application_id
 WHERE a.application_code = 'client-console'
UNION ALL SELECT 'B03 operations-console ACTIVE keys', COUNT(*), 409, COUNT(*) = 409
  FROM mor_platform.t_i18n_key k JOIN mor_platform.t_i18n_application a ON a.application_id = k.application_id
 WHERE a.application_code = 'operations-console' AND k.status = 'ACTIVE'
UNION ALL SELECT 'B04 client-console ACTIVE keys', COUNT(*), 608, COUNT(*) = 608
  FROM mor_platform.t_i18n_key k JOIN mor_platform.t_i18n_application a ON a.application_id = k.application_id
 WHERE a.application_code = 'client-console' AND k.status = 'ACTIVE'
UNION ALL SELECT 'B05 zh-CN translations (409 + 608)', COUNT(*), 1017, COUNT(*) = 1017
  FROM mor_platform.t_i18n_translation t JOIN mor_platform.t_i18n_key k ON k.key_id = t.key_id
  JOIN mor_platform.t_i18n_application a ON a.application_id = k.application_id
 WHERE a.application_code IN ('operations-console', 'client-console') AND t.locale_code = 'zh-CN' AND t.text <> ''
UNION ALL SELECT 'B06 current release READY (both apps)', COUNT(*), 2, COUNT(*) = 2
  FROM mor_platform.t_i18n_application a JOIN mor_platform.t_i18n_release r ON r.release_id = a.current_release_id
 WHERE a.application_code IN ('operations-console', 'client-console') AND r.status = 'READY'
UNION ALL SELECT 'B07 bundles of current releases (4 + 16)', COUNT(*), 20, COUNT(*) = 20
  FROM mor_platform.t_i18n_release_bundle b JOIN mor_platform.t_i18n_application a ON a.current_release_id = b.release_id
 WHERE a.application_code IN ('operations-console', 'client-console')
UNION ALL SELECT 'B08 bundle files PUBLIC / AVAILABLE in MINIO/mor-platform-public', COUNT(*), 20, COUNT(*) = 20
  FROM mor_platform.t_i18n_release_bundle b JOIN mor_platform.t_i18n_application a ON a.current_release_id = b.release_id
  JOIN mor_platform.t_file_object o ON CONCAT('/files/v1/', o.file_id) = b.file_address
 WHERE a.application_code IN ('operations-console', 'client-console') AND o.access_level = 'PUBLIC'
   AND o.scan_status = 'AVAILABLE' AND o.storage_container_ref = 'mor-platform-public' AND o.mime_type = 'application/json'
   AND o.source_system = 'platform-support' AND o.content_hash = b.content_hash AND o.storage_provider = 'MINIO'
UNION ALL SELECT 'B11 no language bundle written to S3_TEST (did not fall back to the default * PUBLIC route)', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_i18n_release_bundle b JOIN mor_platform.t_file_object o ON CONCAT('/files/v1/', o.file_id) = b.file_address
 WHERE o.storage_provider <> 'MINIO' OR o.storage_container_ref <> 'mor-platform-public'
UNION ALL SELECT 'B12 platform-support PUBLIC files all in MINIO/mor-platform-public', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_object WHERE source_system = 'platform-support' AND access_level = 'PUBLIC'
   AND (storage_provider <> 'MINIO' OR storage_container_ref <> 'mor-platform-public')
UNION ALL SELECT 'B09 partner-console / checkout untouched (no release)', COUNT(*), 2, COUNT(*) = 2
  FROM mor_platform.t_i18n_application WHERE application_code IN ('partner-console', 'checkout') AND current_release_id IS NULL
UNION ALL SELECT 'B10 no FAILED I18N compensation events', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_compensation_event WHERE event_type = 'I18N_RELEASE_ACTIVATE' AND execute_status = 'FAILED';

SELECT a.application_code, r.release_no, r.status, r.key_count, r.ready_time, JSON_EXTRACT(r.coverage, '$') AS coverage
  FROM mor_platform.t_i18n_release r JOIN mor_platform.t_i18n_application a ON a.application_id = r.application_id
 ORDER BY a.application_code, r.release_no;
