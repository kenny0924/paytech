-- =============================================================================
-- Platform Support 文件公开桶与固定访问地址 回填 DML
-- 需求：requirements/20261022/platform-support-文件公开桶/technique.md 第 4.3 节
--
-- 状态：在同目录 ddl.sql 之后人工执行；应用与测试不自动执行。可重复执行（已有 v1 的文件跳过）。
--
-- 内容：为每个 t_file_object 插入版本 1 行（version_id 取 file_id），镜像当前存储版本、摘要、大小、类型、加密引用、
-- 冻结策略、扫描状态与可用时间；version_status 由 scan_status 推导：AVAILABLE→CURRENT，REJECTED / QUARANTINED→REJECTED，
-- 其余（PENDING_SCAN、SCANNING）→CANDIDATE。新列的默认值即存量取值（PRIVATE、current_version_no=1、replacement_no=0、
-- file_version_no=1），无需另行更新。
--
-- 回退：DELETE FROM t_file_version WHERE version_no = 1 AND version_id = file_id;（在 ddl.sql 的回退之前执行；回退 DDL 删除整表时可省略）
-- =============================================================================

-- 1. 执行前核验 -----------------------------------------------------------------

SELECT COUNT(*) AS file_rows FROM t_file_object;
SELECT COUNT(*) AS files_without_v1 FROM t_file_object f
WHERE NOT EXISTS (SELECT 1 FROM t_file_version v WHERE v.file_id = f.file_id AND v.version_no = 1);

-- 2. 回填 v1 ----------------------------------------------------------------------

INSERT INTO t_file_version (version_id, file_id, version_no, upload_id, object_version, content_hash, size_bytes,
  mime_type, storage_encryption_key_ref, scan_policy_id, scan_status, version_status, available_time, version,
  create_time, update_time)
SELECT f.file_id, f.file_id, 1, f.upload_id, f.object_version, f.content_hash, f.size_bytes, f.mime_type,
  f.storage_encryption_key_ref, f.scan_policy_id, f.scan_status,
  CASE f.scan_status
    WHEN 'AVAILABLE' THEN 'CURRENT'
    WHEN 'REJECTED' THEN 'REJECTED'
    WHEN 'QUARANTINED' THEN 'REJECTED'
    ELSE 'CANDIDATE'
  END,
  f.available_time, 1, f.create_time, f.update_time
FROM t_file_object f
WHERE NOT EXISTS (SELECT 1 FROM t_file_version v WHERE v.file_id = f.file_id AND v.version_no = 1);

-- 3. 执行后核验：两数应相等，缺 v1 的文件数应为 0 -----------------------------------

SELECT (SELECT COUNT(*) FROM t_file_object) AS file_rows,
  (SELECT COUNT(*) FROM t_file_version WHERE version_no = 1) AS v1_rows;
SELECT COUNT(*) AS files_without_v1 FROM t_file_object f
WHERE NOT EXISTS (SELECT 1 FROM t_file_version v WHERE v.file_id = f.file_id AND v.version_no = 1);
SELECT version_status, scan_status, COUNT(*) AS version_rows FROM t_file_version GROUP BY version_status, scan_status
ORDER BY version_status, scan_status;
