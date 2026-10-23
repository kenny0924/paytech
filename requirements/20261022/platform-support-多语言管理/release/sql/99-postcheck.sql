-- 多语言管理 · 激活后只读核对（第 6 节第 12 步；首批语言包之前）。ok=1 为期望；角色 / 路由版本以 00-precheck R01 / R02 为基准
-- （阶段 6 记录：角色 510 = 47、ops-bff 私有路由 = 1；若备份时的记录值不同，以记录值 + 1 判断 V01 / V02）。
SELECT 'P01 seven t_i18n_* tables' AS item, COUNT(*) AS actual, 7 AS expect, COUNT(*) = 7 AS ok
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME LIKE 't\_i18n\_%'
UNION ALL SELECT 'P02 mor_platform table count', COUNT(*), 71, COUNT(*) = 71
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform'
UNION ALL SELECT 'P03 locales en / zh-CN ENABLED', COUNT(*), 2, COUNT(*) = 2
  FROM mor_platform.t_i18n_locale WHERE locale_code IN ('en', 'zh-CN') AND status = 'ENABLED'
UNION ALL SELECT 'P04 applications (4 seeded)', COUNT(*), 4, COUNT(*) = 4
  FROM mor_platform.t_i18n_application WHERE application_id BETWEEN 900000000000135011 AND 900000000000135014
UNION ALL SELECT 'P05 permissions 135101-103 ACTIVE', COUNT(*), 3, COUNT(*) = 3
  FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000135101 AND 900000000000135103 AND status = 'ACTIVE'
UNION ALL SELECT 'P06 PLATFORM_ADMIN grants 135201-203 ACTIVE', COUNT(*), 3, COUNT(*) = 3
  FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000135201 AND 900000000000135203 AND status = 'ACTIVE'
UNION ALL SELECT 'P07 console resources 135301-311 ACTIVE', COUNT(*), 11, COUNT(*) = 11
  FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000135301 AND 900000000000135311 AND status = 'ACTIVE'
UNION ALL SELECT 'P08 gateway routes 135401-427 ACTIVE', COUNT(*), 27, COUNT(*) = 27
  FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000135401 AND 900000000000135427 AND status = 'ACTIVE'
UNION ALL SELECT 'P09 route headers / query params / guard rules', 
  (SELECT COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000135501 AND 900000000000135699)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000135701 AND 900000000000135799)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000135801 AND 900000000000135999), 287,
  (SELECT COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000135501 AND 900000000000135699)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000135701 AND 900000000000135799)
  + (SELECT COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000135801 AND 900000000000135999) = 287
UNION ALL SELECT 'P10 client manifest route -> client-console-bff target', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_route r JOIN mor_platform.t_gateway_target t ON t.target_id = r.target_id
 WHERE r.route_id = 900000000000135427 AND t.target_service_code = 'client-console-bff'
UNION ALL SELECT 'P11 policies I18N_BUNDLE_JSON / OPERATIONS_BFF_IMPORT ACTIVE', COUNT(*), 2, COUNT(*) = 2
  FROM mor_platform.t_file_scan_policy WHERE policy_id IN (900000000000135021, 900000000000135023) AND status = 'ACTIVE'
UNION ALL SELECT 'P12 platform-support x PUBLIC -> mor-platform-public / I18N_BUNDLE_JSON', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
 WHERE r.source_system = 'platform-support' AND r.access_level = 'PUBLIC' AND b.bucket_name = 'mor-platform-public'
   AND r.scan_policy_code = 'I18N_BUNDLE_JSON'
UNION ALL SELECT 'P13 ops-bff PRIVATE route -> OPERATIONS_BFF_IMPORT / 900', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_route WHERE route_id = 239087906591948800 AND scan_policy_code = 'OPERATIONS_BFF_IMPORT'
   AND max_upload_seconds = 900
UNION ALL SELECT 'P14 CARD_BIN_CSV still ACTIVE (unreferenced, rollback target)', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_scan_policy WHERE policy_code = 'CARD_BIN_CSV' AND status = 'ACTIVE'
UNION ALL SELECT 'P15 no FAILED I18N compensation events', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_compensation_event WHERE event_type = 'I18N_RELEASE_ACTIVATE' AND execute_status = 'FAILED'
UNION ALL SELECT 'P16 default PUBLIC routes unchanged (* and client-console-bff -> S3_TEST)', COUNT(*), 2, COUNT(*) = 2
  FROM mor_platform.t_file_storage_route r JOIN mor_platform.t_file_storage_bucket b ON b.bucket_id = r.bucket_id
 WHERE r.access_level = 'PUBLIC' AND r.source_system IN ('*', 'client-console-bff') AND b.bucket_name = 'mor-test-platform-public-k7q2'
UNION ALL SELECT 'P17 PUBLIC routes total (2 default + platform-support)', COUNT(*), 3, COUNT(*) = 3
  FROM mor_platform.t_file_storage_route WHERE access_level = 'PUBLIC';

SELECT 'V01 role 510 version (expect R01 + 1; stage 6: 48)' AS item, CAST(version AS CHAR) AS value
  FROM mor_platform.t_role WHERE role_id = 900000000000000510
UNION ALL SELECT 'V02 ops-bff PRIVATE route version (expect R02 + 1; stage 6: 2)', CAST(version AS CHAR)
  FROM mor_platform.t_file_storage_route WHERE route_id = 239087906591948800
UNION ALL SELECT 'V03 ACTIVE gateway routes total (expect R05 + 27; stage 6: 569)', CAST(COUNT(*) AS CHAR)
  FROM mor_platform.t_gateway_route WHERE status = 'ACTIVE';
