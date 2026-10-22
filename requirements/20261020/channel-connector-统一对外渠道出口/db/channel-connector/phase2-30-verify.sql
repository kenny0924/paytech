-- channel-connector 第二段合表后核对（需求 requirements/20261020/channel-connector-统一对外渠道出口，技术方案第 5.2 节第 4 步）。
-- 只读：全部为 SELECT；在 phase2-20-migrate.sql 成功后于 mor_channel_connector 手工执行。
-- 依赖迁移脚本留下的辅助表 tmp_phase2_channel_map（对应关系，内容应与 precheck 第 6 段逐行一致）与 tmp_phase2_row_count（迁移前行数）。
-- 期望结果写在每段注释中；全部符合后执行文件末尾被注释的清理语句。

SET NAMES utf8mb4;
USE mor_channel_connector;

-- 1. 表集合：期望 table_count = 28、legacy_count = 0（t_payment_channel、t_integration、t_integration_* 均不存在）。
SELECT
  SUM(table_name NOT LIKE 'tmp\_phase2\_%') AS table_count,
  SUM(table_name IN ('t_payment_channel', 't_integration') OR table_name LIKE 't\_integration\_%') AS legacy_count,
  SUM(table_name IN ('t_channel', 't_channel_api', 't_channel_script', 't_channel_standard_api', 't_channel_verify_account',
    't_channel_verify_case', 't_channel_verify_run', 't_channel_verify_run_step', 't_channel_call_grant')) AS phase2_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE();

-- 2. 行数：期望每行 before_count = after_count（t_channel 的 before_count 为对应关系行数）；t_channel_call_grant 为 0。
SELECT r.table_name, r.row_count AS before_count, a.after_count, r.row_count = a.after_count AS matched
FROM tmp_phase2_row_count r
JOIN (
  SELECT 't_channel' AS table_name, COUNT(*) AS after_count FROM t_channel
  UNION ALL SELECT 't_channel_api', COUNT(*) FROM t_channel_api
  UNION ALL SELECT 't_channel_script', COUNT(*) FROM t_channel_script
  UNION ALL SELECT 't_channel_standard_api', COUNT(*) FROM t_channel_standard_api
  UNION ALL SELECT 't_channel_verify_account', COUNT(*) FROM t_channel_verify_account
  UNION ALL SELECT 't_channel_verify_case', COUNT(*) FROM t_channel_verify_case
  UNION ALL SELECT 't_channel_verify_run', COUNT(*) FROM t_channel_verify_run
  UNION ALL SELECT 't_channel_verify_run_step', COUNT(*) FROM t_channel_verify_run_step
  UNION ALL SELECT 't_channel_account', COUNT(*) FROM t_channel_account
  UNION ALL SELECT 't_channel_mid', COUNT(*) FROM t_channel_mid
  UNION ALL SELECT 't_channel_cost_plan', COUNT(*) FROM t_channel_cost_plan
  UNION ALL SELECT 't_channel_config_change', COUNT(*) FROM t_channel_config_change
  UNION ALL SELECT 't_standard_api', COUNT(*) FROM t_standard_api
  UNION ALL SELECT 't_api_field', COUNT(*) FROM t_api_field
) a ON a.table_name = r.table_name
UNION ALL
SELECT 't_channel_call_grant', 0, COUNT(*), COUNT(*) = 0 FROM t_channel_call_grant
ORDER BY table_name;

-- 3. 引用完整性：每个 channel_id 都在 t_channel。期望 0 行。
SELECT 't_channel_api' AS source_table, channel_api_id AS row_id, channel_id FROM t_channel_api
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_script', channel_script_id, channel_id FROM t_channel_script
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_standard_api', channel_standard_api_id, channel_id FROM t_channel_standard_api
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_verify_account', verify_account_id, channel_id FROM t_channel_verify_account
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_verify_case', verify_case_id, channel_id FROM t_channel_verify_case
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_verify_run', verify_run_id, channel_id FROM t_channel_verify_run
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_account', channel_account_id, channel_id FROM t_channel_account
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_mid', mid_id, channel_id FROM t_channel_mid
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_cost_plan', id, channel_id FROM t_channel_cost_plan
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_call_grant', call_grant_id, channel_id FROM t_channel_call_grant
WHERE channel_id NOT IN (SELECT channel_id FROM t_channel)
UNION ALL SELECT 't_channel_verify_run_step', verify_run_step_id, verify_run_id FROM t_channel_verify_run_step
WHERE verify_run_id NOT IN (SELECT verify_run_id FROM t_channel_verify_run);

-- 4. 对应关系核对：逐行输出，应与 precheck 第 6 段一致（match_type、integration_id、target_channel_id、channel_code、target_status）。
--    problem 列期望全部为 NULL：渠道存在、编码一致、类型 PAYMENT；INTEGRATION_ONLY 为 DRAFT；CHANNEL_ONLY 无出站地址且版本 1；
--    MATCHED 与 INTEGRATION_ONLY 的出站地址非空。
SELECT m.match_type, m.integration_id, m.channel_id AS target_channel_id, m.channel_code, c.channel_status AS target_status,
       JSON_LENGTH(c.endpoints) AS endpoint_count, c.version,
       CASE
         WHEN c.channel_id IS NULL THEN 'CHANNEL_MISSING'
         WHEN CAST(c.channel_code AS BINARY) <> CAST(m.channel_code AS BINARY) THEN 'CODE_MISMATCH'
         WHEN c.channel_type <> 'PAYMENT' THEN 'TYPE_NOT_PAYMENT'
         WHEN m.match_type = 'INTEGRATION_ONLY' AND c.channel_status <> 'DRAFT' THEN 'STATUS_NOT_DRAFT'
         WHEN m.match_type = 'CHANNEL_ONLY' AND (c.endpoints IS NOT NULL OR c.version <> 1) THEN 'CHANNEL_ONLY_NOT_EMPTY'
         WHEN m.match_type <> 'CHANNEL_ONLY' AND c.endpoints IS NULL THEN 'ENDPOINTS_MISSING'
       END AS problem
FROM tmp_phase2_channel_map m
LEFT JOIN t_channel c ON c.channel_id = m.channel_id
ORDER BY m.channel_code;

-- 期望 0 行：t_channel 中没有不在对应关系表里的渠道。
SELECT c.channel_id, c.channel_code FROM t_channel c
WHERE c.channel_id NOT IN (SELECT channel_id FROM tmp_phase2_channel_map);

-- 5. 变更记录：期望不再有 INTEGRATION_API / INTEGRATION_SCRIPT / INTEGRATION_IMPLEMENTATION；剩余的 INTEGRATION 只来自已删除的接入方，
--    其行数等于 precheck 第 7 段的 unmapped_integration_rows 合计。
SELECT target_type, COUNT(*) AS row_count
FROM t_channel_config_change
GROUP BY target_type
ORDER BY target_type;

-- 6. 结构：列（期望与 channel-connector-schema.sql 一致；t_channel 12 列且顺序为 channel_id、channel_code、channel_name、channel_type、
--    channel_status、description、endpoints、version、created_by、updated_by、create_time、update_time；channel_code 为 ascii_bin；
--    channel_type、version 无默认值；t_channel_account.entity_no 可空；改名表不再有 integration_* 列）。
SELECT table_name, ordinal_position, column_name, column_type, is_nullable, column_default, character_set_name, collation_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (table_name IN ('t_channel', 't_channel_api', 't_channel_script', 't_channel_standard_api', 't_channel_verify_account',
    't_channel_verify_case', 't_channel_verify_run', 't_channel_call_grant')
    OR (table_name = 't_channel_account' AND column_name = 'entity_no'))
ORDER BY table_name, ordinal_position;

-- 期望 0 行：任何表都不再有 integration_* 列。
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema = DATABASE() AND column_name LIKE 'integration\_%';

-- 索引：期望 uk_channel_code、idx_channel_type_status、uk_channel_api_code、uk_channel_script_code、uk_channel_standard_api、
-- idx_channel_std_api_standard、uk_verify_account_code(channel_id, account_code)、uk_verify_case_code(channel_id, case_code)、
-- idx_verify_run_channel、uk_channel_call_grant、idx_channel_call_grant_channel 存在；uk_payment_channel_code、uk_integration_*、
-- idx_integ_std_api_standard、idx_verify_run_integration 不存在。
SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('t_channel', 't_channel_api', 't_channel_script', 't_channel_standard_api', 't_channel_verify_account',
    't_channel_verify_case', 't_channel_verify_run', 't_channel_verify_run_step', 't_channel_call_grant')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

-- 7. 全部符合后手工执行清理（不在本只读脚本中执行）：
-- DROP TABLE tmp_phase2_channel_map;
-- DROP TABLE tmp_phase2_row_count;
