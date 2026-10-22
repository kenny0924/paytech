-- 文件多存储并存第二段 · 第 1 步与五个镜像替换之后的只读核对（阶段 7 第 7 步；root；不写任何数据）。
-- 这时 CCT 第 2 步与 Platform 删 Owner 都还没执行：file_id、owner 列与 t_file_owner_ref 仍在。最终形态见 98-final-check.sql。
SET NAMES utf8mb4;
SELECT 'CCT file_address 已加（可空 VARCHAR(255)）' AS item, COUNT(*) AS actual, 1 AS expect
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND COLUMN_NAME='file_address' AND IS_NULLABLE='YES' AND CHARACTER_MAXIMUM_LENGTH=255
UNION ALL SELECT 'CCT file_id 已放宽为可空', COUNT(*), 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND COLUMN_NAME='file_id' AND IS_NULLABLE='YES'
UNION ALL SELECT 'CCT 新旧唯一键共 6 个', COUNT(DISTINCT INDEX_NAME), 6
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND INDEX_NAME IN ('uk_app_doc_client','uk_app_doc_partner','uk_app_doc_entity',
                      'uk_app_doc_client_addr','uk_app_doc_partner_addr','uk_app_doc_entity_addr')
UNION ALL SELECT 'CCT 材料全部有地址', COUNT(*), 0 FROM mor_client_master.t_application_document WHERE file_address IS NULL
UNION ALL SELECT 'CCT 旧材料地址与 file_id 一致', COUNT(*), 0 FROM mor_client_master.t_application_document
  WHERE file_id IS NOT NULL AND file_address <> CONCAT('/files/v1/', file_id)
UNION ALL SELECT 'CCT 原 52 行仍在', COUNT(*), 52 FROM mor_client_master.t_application_document WHERE file_id IS NOT NULL
UNION ALL SELECT 'CCT 地址都能对应 Platform 文件', COUNT(*), 0 FROM mor_client_master.t_application_document d
  WHERE NOT EXISTS (SELECT 1 FROM mor_platform.t_file_object f WHERE CONCAT('/files/v1/', f.file_id) = d.file_address)
UNION ALL SELECT 'Platform owner 列与 t_file_owner_ref 仍在（删除是最后一步）', COUNT(*), 4 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='mor_platform' AND ((TABLE_NAME='t_file_upload' AND COLUMN_NAME IN ('owner_system','owner_type','owner_ref_id'))
   OR (TABLE_NAME='t_file_owner_ref' AND COLUMN_NAME='owner_ref_row_id'))
UNION ALL SELECT '旧 55 个文件仍 AVAILABLE / ACTIVE', COUNT(*), 55 FROM mor_platform.t_file_object
  WHERE source_system='platform-support' AND scan_status='AVAILABLE' AND object_status='ACTIVE';
-- 记录用：替换后新链路产生的数据
SELECT source_system AS item, COUNT(*) AS value FROM mor_platform.t_file_upload GROUP BY source_system
UNION ALL SELECT CONCAT('audit ', action_code), COUNT(*) FROM mor_platform.t_audit_event
  WHERE action_code IN ('FILE_DOWNLOAD_AUTHORIZE','OPERATIONS_REVIEW_FILE_DOWNLOAD') GROUP BY action_code;
