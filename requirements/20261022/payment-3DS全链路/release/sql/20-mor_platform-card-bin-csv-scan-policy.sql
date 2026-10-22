-- 发布副本（3DS 全链路 + 卡 BIN 库合并发布，阶段 6）。目标库：mor_platform。来源：requirements/20261022/card-BIN库与卡属性/db/platform-support/dml.sql（SHA-256 3c0afbdb286f4ac3，BIN 需求 db）。
-- 以下为来源文件第 2 段（第 269—287 行）逐字副本；前置：新 Platform 已部署，未改动业务语句。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 第 2 段：BIN 库 CSV 扫描策略。只允许 text/csv（Platform 新增识别：无 PDF/PNG/JPEG 文件头、全文合法 UTF-8、不含 NUL），
-- 上限 50 MiB；FORMAT 要求沿用 PLATFORM_FORMAT / 最低版本 1 / 只接受 FORMAT_VALID。
-- ID 取块 132 的 9xx 段（900000000000132901—902）。前置：policy_code CARD_BIN_CSV 未被占用。
-- 可重跑：否。回退：DELETE FROM t_file_scan_requirement WHERE requirement_id = 900000000000132902;
--       DELETE FROM t_file_scan_policy WHERE policy_id = 900000000000132901;（须先确认没有写入路由引用 CARD_BIN_CSV）
-- ---------------------------------------------------------------------------
START TRANSACTION;
SET @card_bin_scan_time = UTC_TIMESTAMP(6);
INSERT INTO t_file_scan_policy (policy_id, policy_code, revision_no, rule_schema_version, allowed_mime_rules, max_size_bytes,
  effective_from, effective_to, status, active_slot, version, create_time, update_time) VALUES
  (900000000000132901, 'CARD_BIN_CSV', 1, 1, '{"mimeTypes":["text/csv"]}', 52428800, @card_bin_scan_time, NULL, 'ACTIVE', 1, 1,
   @card_bin_scan_time, @card_bin_scan_time);
INSERT INTO t_file_scan_requirement (requirement_id, policy_id, requirement_no, scanner_code, scan_type, minimum_scanner_version,
  accepted_result_codes, version, create_time, update_time) VALUES
  (900000000000132902, 900000000000132901, 1, 'PLATFORM_FORMAT', 'FORMAT', '1', '["FORMAT_VALID"]', 1, @card_bin_scan_time,
   @card_bin_scan_time);
COMMIT;
