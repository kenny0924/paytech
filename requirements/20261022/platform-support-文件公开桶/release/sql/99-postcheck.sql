-- 文件公开桶 · mor-test 发布后只读核对（阶段 7 第 9 步：六个镜像替换、01 / 02 / 10 / 11 / test-activate 之后、验收之前；root；不写数据）。
-- 期望值按阶段 6 预检（2026-10-22T20:48Z）推算：PLATFORM_ADMIN 版本 45 → 46，块 130 两条路由版本和 4 → 6。
-- 若阶段 7 备份时的 00-precheck.out 中 R05 / R06 已不同，按“新值 + 1 / + 2”判断。任何一行 actual ≠ expect 即停止排查。
SET NAMES utf8mb4;

SELECT 'P01 t_file_version 已建' AS item, COUNT(*) AS actual, 1 AS expect
  FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_version'
UNION ALL SELECT 'P02 10 个新列齐全', COUNT(*), 10
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_platform' AND (
       (TABLE_NAME='t_file_object' AND COLUMN_NAME IN ('access_level','current_version_no'))
    OR (TABLE_NAME='t_file_upload' AND COLUMN_NAME IN ('access_level','replaces_file_id','replacement_no','base_object_version'))
    OR (TABLE_NAME='t_file_storage_bucket' AND COLUMN_NAME IN ('access_level','public_base_url'))
    OR (TABLE_NAME='t_file_storage_route' AND COLUMN_NAME='access_level')
    OR (TABLE_NAME='t_file_scan_result' AND COLUMN_NAME='file_version_no'))
UNION ALL SELECT 'P03 三个变宽唯一键为新列组合', COUNT(*), 3 FROM (
  SELECT TABLE_NAME t, INDEX_NAME i, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) c FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA='mor_platform' AND INDEX_NAME IN ('uk_upload_storage_target','uk_file_storage_route_source','uk_file_scan_attempt')
   GROUP BY TABLE_NAME, INDEX_NAME) x
  WHERE (t, i, c) IN (('t_file_upload','uk_upload_storage_target','storage_provider,storage_container_ref,storage_object_ref,replacement_no'),
                      ('t_file_storage_route','uk_file_storage_route_source','source_system,access_level'),
                      ('t_file_scan_result','uk_file_scan_attempt','file_id,file_version_no,scan_requirement_id,attempt_no'))
UNION ALL SELECT 'P04 新索引 6 个（版本表 4 + 文件 1 + 上传 1）', COUNT(DISTINCT INDEX_NAME), 6 FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA='mor_platform' AND INDEX_NAME IN ('uk_file_version_no','uk_file_version_upload','idx_file_version_scan',
    'idx_file_version_scan_policy_id','idx_file_create_time','idx_file_upload_replaces')
UNION ALL SELECT 'P05 mor_platform 表数', COUNT(*), 64 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform'
UNION ALL SELECT 'P06 每个文件都有 v1 版本行', COUNT(*), 0 FROM mor_platform.t_file_object f
  WHERE NOT EXISTS (SELECT 1 FROM mor_platform.t_file_version v WHERE v.file_id = f.file_id AND v.version_no = 1)
UNION ALL SELECT 'P07 v1 版本与文件镜像一致（versionId / 摘要 / 大小 / 类型 / 策略 / 扫描状态）', COUNT(*), 0
  FROM mor_platform.t_file_object f JOIN mor_platform.t_file_version v ON v.file_id = f.file_id AND v.version_no = f.current_version_no
  WHERE NOT (v.object_version <=> f.object_version AND v.content_hash <=> f.content_hash AND v.size_bytes <=> f.size_bytes
    AND v.mime_type <=> f.mime_type AND v.scan_policy_id <=> f.scan_policy_id AND v.scan_status <=> f.scan_status)
UNION ALL SELECT 'P08 AVAILABLE 文件的当前版本为 CURRENT', COUNT(*), 0
  FROM mor_platform.t_file_object f JOIN mor_platform.t_file_version v ON v.file_id = f.file_id AND v.version_no = f.current_version_no
  WHERE f.scan_status = 'AVAILABLE' AND v.version_status <> 'CURRENT'
UNION ALL SELECT 'P09 每个文件最多一个 CURRENT 版本', COUNT(*), 0 FROM (
  SELECT file_id FROM mor_platform.t_file_version WHERE version_status = 'CURRENT' GROUP BY file_id HAVING COUNT(*) > 1) x
UNION ALL SELECT 'P10 验收前：存量文件、上传、桶、路由全部 PRIVATE', (
    (SELECT COUNT(*) FROM mor_platform.t_file_object WHERE access_level <> 'PRIVATE')
  + (SELECT COUNT(*) FROM mor_platform.t_file_upload WHERE access_level <> 'PRIVATE')
  + (SELECT COUNT(*) FROM mor_platform.t_file_storage_bucket WHERE access_level <> 'PRIVATE')
  + (SELECT COUNT(*) FROM mor_platform.t_file_storage_route WHERE access_level <> 'PRIVATE')), 0
UNION ALL SELECT 'P11 三条写入路由不变（含 operations-console-bff → CARD_BIN_CSV）', COUNT(*), 3 FROM mor_platform.t_file_storage_route
  WHERE (source_system, access_level, scan_policy_code) IN (('*','PRIVATE','CLIENT_ONBOARDING_FORMAT'),
    ('client-console-bff','PRIVATE','CLIENT_ONBOARDING_FORMAT'), ('operations-console-bff','PRIVATE','CARD_BIN_CSV'))
UNION ALL SELECT 'P12 块 130 写入规则编辑 / 删除已为 /{sourceSystem}/{accessLevel}', COUNT(*), 2 FROM mor_platform.t_gateway_route
  WHERE route_id IN (900000000000130411, 900000000000130412) AND status = 'ACTIVE'
   AND external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}/{accessLevel}'
   AND downstream_path_template = '/storage-admin/v1/routes/{sourceSystem}/{accessLevel}'
UNION ALL SELECT 'P13 块 130 两条路由版本和（4 + 2）', SUM(version), 6 FROM mor_platform.t_gateway_route
  WHERE route_id IN (900000000000130411, 900000000000130412)
UNION ALL SELECT 'P14 块 134 permission 2', COUNT(*), 2 FROM mor_platform.t_permission
  WHERE permission_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'ACTIVE'
UNION ALL SELECT 'P15 块 134 role_permission 2 ACTIVE', COUNT(*), 2 FROM mor_platform.t_role_permission
  WHERE role_permission_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'ACTIVE'
UNION ALL SELECT 'P16 块 134 console_resource 2 ACTIVE', COUNT(*), 2 FROM mor_platform.t_console_resource
  WHERE resource_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'ACTIVE'
UNION ALL SELECT 'P17 块 134 gateway_route 12 ACTIVE', COUNT(*), 12 FROM mor_platform.t_gateway_route
  WHERE route_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'ACTIVE'
UNION ALL SELECT 'P18 块 134 route_header 56', COUNT(*), 56 FROM mor_platform.t_gateway_route_header
  WHERE route_header_id BETWEEN 900000000000134001 AND 900000000000134999
UNION ALL SELECT 'P19 块 134 query_param 11', COUNT(*), 11 FROM mor_platform.t_gateway_route_query_param
  WHERE route_query_param_id BETWEEN 900000000000134001 AND 900000000000134999
UNION ALL SELECT 'P20 块 134 guard_rule 72', COUNT(*), 72 FROM mor_platform.t_gateway_guard_rule
  WHERE guard_rule_id BETWEEN 900000000000134001 AND 900000000000134999
UNION ALL SELECT 'P21 PLATFORM_ADMIN 版本（45 + 1）', version, 46 FROM mor_platform.t_role WHERE role_id = 900000000000000510
UNION ALL SELECT 'P22 工作流附件内嵌预览 query_param 127030 仍在', COUNT(*), 1 FROM mor_platform.t_gateway_route_query_param
  WHERE route_query_param_id = 900000000000127030
UNION ALL SELECT 'P23 FILE_SCAN / FILE_PUBLIC_SYNC 没有失败或卡住的补偿事件', COUNT(*), 0 FROM mor_platform.t_compensation_event
  WHERE event_type IN ('FILE_SCAN','FILE_PUBLIC_SYNC') AND (execute_status IN ('FAILED','ERROR') OR execute_count >= 3);
-- 记录用
SELECT version_status AS item, COUNT(*) AS value FROM mor_platform.t_file_version GROUP BY version_status
UNION ALL SELECT 'file rows', COUNT(*) FROM mor_platform.t_file_object
UNION ALL SELECT 'upload rows', COUNT(*) FROM mor_platform.t_file_upload
UNION ALL SELECT 'compensation events (all)', COUNT(*) FROM mor_platform.t_compensation_event;
