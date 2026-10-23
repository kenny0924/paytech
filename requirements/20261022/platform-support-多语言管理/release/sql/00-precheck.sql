-- 多语言管理 · mor-test 执行前只读核对（不写任何数据；root 或只读账号，客户端 utf8mb4；库名全部显式限定）。
-- 第一段：ok=1 表示符合期望；任一 ok=0 即停（S/G/F 组）。第二段：记录值（角色与路由版本、公开桶地址等），阶段 7 后续核对引用。
-- 阶段 6 于 2026-10-23 在 mor-test-mysql-1 执行，输出见 00-precheck-20261023.out。
SELECT 'S01 t_i18n_* tables absent' AS item, COUNT(*) AS actual, 0 AS expect, COUNT(*) = 0 AS ok
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME LIKE 't\_i18n\_%'
UNION ALL SELECT 'S02 mor_platform table count', COUNT(*), 64, COUNT(*) = 64
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform'
-- 块 135 注册 ID 段在 7 张表均为空
UNION ALL SELECT 'G01 t_permission 1351xx empty', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000135001 AND 900000000000135999
UNION ALL SELECT 'G02 t_role_permission 1352xx empty', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000135001 AND 900000000000135999
UNION ALL SELECT 'G03 t_console_resource 1353xx empty', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000135001 AND 900000000000135999
UNION ALL SELECT 'G04 t_gateway_route 1354xx empty', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000135001 AND 900000000000135999
UNION ALL SELECT 'G05 t_gateway_route_header 1355xx-1356xx empty', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000135001 AND 900000000000135999
UNION ALL SELECT 'G06 t_gateway_route_query_param 1357xx empty', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000135001 AND 900000000000135999
UNION ALL SELECT 'G07 t_gateway_guard_rule 1358xx-1359xx empty', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000135001 AND 900000000000135999
-- 代码与路径未被占用
UNION ALL SELECT 'G08 permission codes I18N_VIEW/EDIT/PUBLISH unused', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_permission WHERE permission_code IN ('I18N_VIEW', 'I18N_EDIT', 'I18N_PUBLISH')
UNION ALL SELECT 'G09 resource codes OPERATIONS_I18N* unused', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_console_resource WHERE resource_code LIKE 'OPERATIONS\_I18N%'
UNION ALL SELECT 'G10 frontend routes /i18n/* unused', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_console_resource WHERE frontend_route LIKE '/i18n/%'
UNION ALL SELECT 'G11 route codes operations-i18n-* / client-i18n-manifest unused', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route WHERE route_code LIKE 'operations-i18n-%' OR route_code = 'client-i18n-manifest'
UNION ALL SELECT 'G12 external paths unused', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route
 WHERE external_path_template LIKE '/api/operations/localization-admin/%'
    OR external_path_template IN ('/api/operations/i18n/v1/manifest', '/api/client/i18n/v1/manifest')
UNION ALL SELECT 'G13 ops-bff target 900000000000000001 ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_target WHERE target_id = 900000000000000001
   AND target_service_code = 'operations-console-bff' AND status = 'ACTIVE'
UNION ALL SELECT 'G14 client-console-bff target exactly one ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_target WHERE target_service_code = 'client-console-bff' AND status = 'ACTIVE'
UNION ALL SELECT 'G15 role 510 PLATFORM_ADMIN ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_role WHERE role_id = 900000000000000510 AND role_code = 'PLATFORM_ADMIN' AND status = 'ACTIVE'
UNION ALL SELECT 'G16 top-level OPERATIONS menu sort 70 free', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_console_resource WHERE channel = 'OPERATIONS' AND parent_resource_id IS NULL AND sort_order = 70
-- 文件检查策略与写入路由（dml.sql 第 3 部分的前置）
UNION ALL SELECT 'F01 policies I18N_BUNDLE_JSON / OPERATIONS_BFF_IMPORT absent', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_scan_policy WHERE policy_code IN ('I18N_BUNDLE_JSON', 'OPERATIONS_BFF_IMPORT')
UNION ALL SELECT 'F02 scan policy IDs 135021-135025 free', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_scan_policy WHERE policy_id BETWEEN 900000000000135021 AND 900000000000135025
UNION ALL SELECT 'F03 scan requirement IDs 135021-135025 free', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_scan_requirement WHERE requirement_id BETWEEN 900000000000135021 AND 900000000000135025
UNION ALL SELECT 'F04 route 135025 / platform-support x PUBLIC absent', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_storage_route
 WHERE route_id = 900000000000135025 OR (source_system = 'platform-support' AND access_level = 'PUBLIC')
UNION ALL SELECT 'F05 ops-bff PRIVATE route 239087906591948800 / CARD_BIN_CSV / 900', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_route
 WHERE route_id = 239087906591948800 AND source_system = 'operations-console-bff' AND access_level = 'PRIVATE'
   AND scan_policy_code = 'CARD_BIN_CSV' AND max_upload_seconds = 900
UNION ALL SELECT 'F06 ops-bff PRIVATE routes total', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_route WHERE source_system = 'operations-console-bff' AND access_level = 'PRIVATE'
UNION ALL SELECT 'F07 MINIO/mor-platform-public ACTIVE PUBLIC with base URL', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_bucket b JOIN mor_platform.t_file_storage_instance i ON i.instance_id = b.instance_id
 WHERE i.instance_code = 'MINIO' AND i.status = 'ACTIVE' AND b.bucket_name = 'mor-platform-public'
   AND b.access_level = 'PUBLIC' AND b.status = 'ACTIVE'
   AND b.public_base_url = 'https://minio.mor-test.localhost:29400/mor-platform-public'
UNION ALL SELECT 'F08 CARD_BIN_CSV r1 ACTIVE (rollback target)', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_scan_policy WHERE policy_code = 'CARD_BIN_CSV' AND status = 'ACTIVE'
UNION ALL SELECT 'F09 no I18N_RELEASE_ACTIVATE compensation events', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_compensation_event WHERE event_type = 'I18N_RELEASE_ACTIVATE'
-- 现有默认 PUBLIC 规则（code-mor-42，2026-10-23 07:43Z）：第 3 部分之前 platform-support 的 PUBLIC 上传会回落到 `*`（AWS 桶）
UNION ALL SELECT 'F10 * x PUBLIC -> S3_TEST/mor-test-platform-public-k7q2 / PUBLIC_DOCUMENT', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
  JOIN mor_platform.t_file_storage_instance i ON i.instance_id = b.instance_id
 WHERE r.source_system = '*' AND r.access_level = 'PUBLIC' AND i.instance_code = 'S3_TEST'
   AND b.bucket_name = 'mor-test-platform-public-k7q2' AND r.scan_policy_code = 'PUBLIC_DOCUMENT'
UNION ALL SELECT 'F11 client-console-bff x PUBLIC -> same bucket / PUBLIC_IMAGE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
  JOIN mor_platform.t_file_storage_instance i ON i.instance_id = b.instance_id
 WHERE r.source_system = 'client-console-bff' AND r.access_level = 'PUBLIC' AND i.instance_code = 'S3_TEST'
   AND b.bucket_name = 'mor-test-platform-public-k7q2' AND r.scan_policy_code = 'PUBLIC_IMAGE'
UNION ALL SELECT 'F12 PUBLIC routes total (only the two above)', COUNT(*), 2, COUNT(*) = 2
  FROM mor_platform.t_file_storage_route WHERE access_level = 'PUBLIC'
UNION ALL SELECT 'F13 no platform-support PUBLIC files yet', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_object WHERE source_system = 'platform-support' AND access_level = 'PUBLIC';

-- 记录值（不判定）：阶段 7 的 postcheck 以此为基准
SELECT 'R01 role 510 version' AS item, CAST(version AS CHAR) AS value
  FROM mor_platform.t_role WHERE role_id = 900000000000000510
UNION ALL SELECT 'R02 ops-bff PRIVATE route version', CAST(version AS CHAR)
  FROM mor_platform.t_file_storage_route WHERE route_id = 239087906591948800
UNION ALL SELECT 'R03 client-console-bff target_id', CAST(target_id AS CHAR)
  FROM mor_platform.t_gateway_target WHERE target_service_code = 'client-console-bff'
UNION ALL SELECT 'R04 PUBLIC routes (source/bucket/policy)', COALESCE(GROUP_CONCAT(CONCAT(r.source_system, '>', b.bucket_name, '>', r.scan_policy_code)
    ORDER BY r.source_system SEPARATOR '; '), '-')
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
 WHERE r.access_level = 'PUBLIC'
UNION ALL SELECT 'R05 ACTIVE gateway routes total', CAST(COUNT(*) AS CHAR)
  FROM mor_platform.t_gateway_route WHERE status = 'ACTIVE'
UNION ALL SELECT 'R06 files from operations-console-bff (BIN CSV) total', CAST(COUNT(*) AS CHAR)
  FROM mor_platform.t_file_object WHERE source_system = 'operations-console-bff'
UNION ALL SELECT 'R07 open uploads from operations-console-bff (CREATED)', CAST(COUNT(*) AS CHAR)
  FROM mor_platform.t_file_upload WHERE source_system = 'operations-console-bff' AND status = 'CREATED';
