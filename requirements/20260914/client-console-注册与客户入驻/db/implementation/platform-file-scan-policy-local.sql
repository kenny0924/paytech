-- 本机 mor_platform：入驻材料格式检查策略（用户 2026-10-14 授权写入本机扫描配置）。
-- 只做格式检查（PLATFORM_FORMAT / FORMAT），不含病毒扫描；生产启用前须另建含 ANTI_MALWARE 要求的新修订。
-- 前置：两个 ID 与策略代码 CLIENT_ONBOARDING_FORMAT 均不存在；在同一会话内执行。
-- 回滚：DELETE FROM t_file_scan_requirement WHERE requirement_id = 9200000000000002;
--       DELETE FROM t_file_scan_policy WHERE policy_id = 9200000000000001;
--       （已有文件引用该策略时不得回滚。）
START TRANSACTION;

INSERT INTO t_file_scan_policy (policy_id, policy_code, revision_no, rule_schema_version, allowed_mime_rules, max_size_bytes,
    effective_from, effective_to, status, active_slot, version, create_time, update_time)
VALUES (9200000000000001, 'CLIENT_ONBOARDING_FORMAT', 1, 1,
    JSON_OBJECT('mimeTypes', JSON_ARRAY('application/pdf', 'image/jpeg', 'image/png')), 10485760,
    NULL, NULL, 'ACTIVE', 1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO t_file_scan_requirement (requirement_id, policy_id, requirement_no, scanner_code, scan_type, minimum_scanner_version,
    accepted_result_codes, version, create_time, update_time)
VALUES (9200000000000002, 9200000000000001, 1, 'PLATFORM_FORMAT', 'FORMAT', '1',
    JSON_ARRAY('FORMAT_VALID'), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

COMMIT;
