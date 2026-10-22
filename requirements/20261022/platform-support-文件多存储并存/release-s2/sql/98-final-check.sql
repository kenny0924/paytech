-- 文件多存储并存第二段 · 最终形态只读核对（CCT 第 2 步与 Platform 删 Owner 都执行之后；root；不写任何数据）。
SET NAMES utf8mb4;
SELECT 'CCT file_id 已删除' AS item, COUNT(*) AS actual, 0 AS expect
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document' AND COLUMN_NAME='file_id'
UNION ALL SELECT 'CCT file_address NOT NULL', COUNT(*), 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND COLUMN_NAME='file_address' AND IS_NULLABLE='NO'
UNION ALL SELECT 'CCT 唯一键恢复原名且含 file_address', COUNT(DISTINCT INDEX_NAME), 3
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND INDEX_NAME IN ('uk_app_doc_client','uk_app_doc_partner','uk_app_doc_entity') AND COLUMN_NAME='file_address'
UNION ALL SELECT 'CCT 不再有 *_addr 唯一键', COUNT(DISTINCT INDEX_NAME), 0
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mor_client_master' AND TABLE_NAME='t_application_document'
   AND INDEX_NAME LIKE 'uk\_app\_doc\_%\_addr'
UNION ALL SELECT 'Platform owner 三列已删除', COUNT(*), 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_upload' AND COLUMN_NAME IN ('owner_system','owner_type','owner_ref_id')
UNION ALL SELECT 'Platform t_file_owner_ref 已删除', COUNT(*), 0 FROM information_schema.TABLES
  WHERE TABLE_SCHEMA='mor_platform' AND TABLE_NAME='t_file_owner_ref'
UNION ALL SELECT 'mor_platform 表数 63 − 1', COUNT(*), 62 FROM information_schema.TABLES WHERE TABLE_SCHEMA='mor_platform';
