-- READ ONLY. Review against the selected Owner database; no cross-database queries.

-- Missing tables/columns are a baseline mismatch, not zero rows. Stop on any SQL error.

SELECT DATABASE() AS selected_database;

-- Metadata only: absent additions yield no metadata; existing tables require exact comparison.

SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('t_authentication_challenge','t_file_upload','t_file_scan_policy','t_file_scan_requirement','t_file_object','t_file_owner_ref','t_file_scan_result') ORDER BY TABLE_NAME,ORDINAL_POSITION;

SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('t_authentication_challenge','t_file_upload','t_file_scan_policy','t_file_scan_requirement','t_file_object','t_file_owner_ref','t_file_scan_result') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;
