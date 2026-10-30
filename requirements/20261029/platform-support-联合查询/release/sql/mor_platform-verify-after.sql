-- 联合查询 · 前滚 DDL 后只读核对：新表 17 列 + 2 个索引、joint_json 列在 constraints_json 之后且可空、旧数据 joint_json 全空。
SELECT COUNT(*) AS key_type_columns FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_key_type';
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_key_type' GROUP BY INDEX_NAME, NON_UNIQUE ORDER BY INDEX_NAME;
SELECT COLUMN_NAME, ORDINAL_POSITION, DATA_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_template_version' AND COLUMN_NAME IN ('constraints_json', 'joint_json', 'restored_from_version_no') ORDER BY ORDINAL_POSITION;
SELECT COUNT(*) AS versions, SUM(joint_json IS NOT NULL) AS with_joint FROM t_query_template_version;
