-- 统一对外渠道出口第一段 · 数据复制核对（只读；阶段 7 第 5 步，导入后由 root 执行，连接库 mysql）。
-- 依据技术方案第 3.4 节第 3 步：逐表行数、CHECKSUM TABLE；15 个 JSON / 脚本源码列逐行 SHA-256（按主键对齐）。
-- 只输出计数与校验和，不输出列值。期望：V1 每行 rows_equal = 1；V2 两库同名表校验和相同；V3 每行 mismatched = 0。
-- 这是人工核对用的 root 跨库只读查询，应用不跨库访问。

-- V1 行数
SELECT t.name AS table_name, t.src AS platform_rows, t.dst AS channel_connector_rows, t.src = t.dst AS rows_equal
  FROM (
    SELECT 't_standard_api' AS name, (SELECT COUNT(*) FROM mor_platform.t_standard_api) AS src, (SELECT COUNT(*) FROM mor_channel_connector.t_standard_api) AS dst
    UNION ALL SELECT 't_api_field', (SELECT COUNT(*) FROM mor_platform.t_api_field), (SELECT COUNT(*) FROM mor_channel_connector.t_api_field)
    UNION ALL SELECT 't_integration', (SELECT COUNT(*) FROM mor_platform.t_integration), (SELECT COUNT(*) FROM mor_channel_connector.t_integration)
    UNION ALL SELECT 't_integration_api', (SELECT COUNT(*) FROM mor_platform.t_integration_api), (SELECT COUNT(*) FROM mor_channel_connector.t_integration_api)
    UNION ALL SELECT 't_integration_script', (SELECT COUNT(*) FROM mor_platform.t_integration_script), (SELECT COUNT(*) FROM mor_channel_connector.t_integration_script)
    UNION ALL SELECT 't_integration_standard_api', (SELECT COUNT(*) FROM mor_platform.t_integration_standard_api), (SELECT COUNT(*) FROM mor_channel_connector.t_integration_standard_api)
    UNION ALL SELECT 't_integration_verify_account', (SELECT COUNT(*) FROM mor_platform.t_integration_verify_account), (SELECT COUNT(*) FROM mor_channel_connector.t_integration_verify_account)
    UNION ALL SELECT 't_integration_verify_case', (SELECT COUNT(*) FROM mor_platform.t_integration_verify_case), (SELECT COUNT(*) FROM mor_channel_connector.t_integration_verify_case)
    UNION ALL SELECT 't_integration_verify_run', (SELECT COUNT(*) FROM mor_platform.t_integration_verify_run), (SELECT COUNT(*) FROM mor_channel_connector.t_integration_verify_run)
    UNION ALL SELECT 't_integration_verify_run_step', (SELECT COUNT(*) FROM mor_platform.t_integration_verify_run_step), (SELECT COUNT(*) FROM mor_channel_connector.t_integration_verify_run_step)
  ) t;

-- V2 校验和（两库同名表逐行对照；表结构逐列一致已在阶段 6 核对）
CHECKSUM TABLE
  mor_platform.t_standard_api, mor_channel_connector.t_standard_api,
  mor_platform.t_api_field, mor_channel_connector.t_api_field,
  mor_platform.t_integration, mor_channel_connector.t_integration,
  mor_platform.t_integration_api, mor_channel_connector.t_integration_api,
  mor_platform.t_integration_script, mor_channel_connector.t_integration_script,
  mor_platform.t_integration_standard_api, mor_channel_connector.t_integration_standard_api,
  mor_platform.t_integration_verify_account, mor_channel_connector.t_integration_verify_account,
  mor_platform.t_integration_verify_case, mor_channel_connector.t_integration_verify_case,
  mor_platform.t_integration_verify_run, mor_channel_connector.t_integration_verify_run,
  mor_platform.t_integration_verify_run_step, mor_channel_connector.t_integration_verify_run_step;

-- V3 15 个 JSON / 脚本源码列逐行 SHA-256（按主键左连接；缺行或摘要不同都计入 mismatched）
SELECT 't_integration.endpoints' AS col, COUNT(*) AS src_rows,
       SUM(d.integration_id IS NULL OR NOT (SHA2(CAST(s.endpoints AS CHAR), 256) <=> SHA2(CAST(d.endpoints AS CHAR), 256))) AS mismatched
  FROM mor_platform.t_integration s LEFT JOIN mor_channel_connector.t_integration d ON d.integration_id = s.integration_id
UNION ALL
SELECT 't_integration_api.definition_json', COUNT(*),
       SUM(d.integration_api_id IS NULL OR NOT (SHA2(CAST(s.definition_json AS CHAR), 256) <=> SHA2(CAST(d.definition_json AS CHAR), 256)))
  FROM mor_platform.t_integration_api s LEFT JOIN mor_channel_connector.t_integration_api d ON d.integration_api_id = s.integration_api_id
UNION ALL
SELECT 't_integration_script.parameter_definition', COUNT(*),
       SUM(d.integration_script_id IS NULL OR NOT (SHA2(CAST(s.parameter_definition AS CHAR), 256) <=> SHA2(CAST(d.parameter_definition AS CHAR), 256)))
  FROM mor_platform.t_integration_script s LEFT JOIN mor_channel_connector.t_integration_script d ON d.integration_script_id = s.integration_script_id
UNION ALL
SELECT 't_integration_script.source_code', COUNT(*),
       SUM(d.integration_script_id IS NULL OR NOT (SHA2(CAST(s.source_code AS CHAR), 256) <=> SHA2(CAST(d.source_code AS CHAR), 256)))
  FROM mor_platform.t_integration_script s LEFT JOIN mor_channel_connector.t_integration_script d ON d.integration_script_id = s.integration_script_id
UNION ALL
SELECT 't_integration_standard_api.flow_definition_json', COUNT(*),
       SUM(d.integration_standard_api_id IS NULL OR NOT (SHA2(CAST(s.flow_definition_json AS CHAR), 256) <=> SHA2(CAST(d.flow_definition_json AS CHAR), 256)))
  FROM mor_platform.t_integration_standard_api s LEFT JOIN mor_channel_connector.t_integration_standard_api d ON d.integration_standard_api_id = s.integration_standard_api_id
UNION ALL
SELECT 't_integration_verify_account.parameters', COUNT(*),
       SUM(d.verify_account_id IS NULL OR NOT (SHA2(CAST(s.parameters AS CHAR), 256) <=> SHA2(CAST(d.parameters AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_account s LEFT JOIN mor_channel_connector.t_integration_verify_account d ON d.verify_account_id = s.verify_account_id
UNION ALL
SELECT 't_integration_verify_account.credentials', COUNT(*),
       SUM(d.verify_account_id IS NULL OR NOT (SHA2(CAST(s.credentials AS CHAR), 256) <=> SHA2(CAST(d.credentials AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_account s LEFT JOIN mor_channel_connector.t_integration_verify_account d ON d.verify_account_id = s.verify_account_id
UNION ALL
SELECT 't_integration_verify_case.steps_json', COUNT(*),
       SUM(d.verify_case_id IS NULL OR NOT (SHA2(CAST(s.steps_json AS CHAR), 256) <=> SHA2(CAST(d.steps_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_case s LEFT JOIN mor_channel_connector.t_integration_verify_case d ON d.verify_case_id = s.verify_case_id
UNION ALL
SELECT 't_integration_verify_run.case_snapshot_json', COUNT(*),
       SUM(d.verify_run_id IS NULL OR NOT (SHA2(CAST(s.case_snapshot_json AS CHAR), 256) <=> SHA2(CAST(d.case_snapshot_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_run s LEFT JOIN mor_channel_connector.t_integration_verify_run d ON d.verify_run_id = s.verify_run_id
UNION ALL
SELECT 't_integration_verify_run.config_versions_json', COUNT(*),
       SUM(d.verify_run_id IS NULL OR NOT (SHA2(CAST(s.config_versions_json AS CHAR), 256) <=> SHA2(CAST(d.config_versions_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_run s LEFT JOIN mor_channel_connector.t_integration_verify_run d ON d.verify_run_id = s.verify_run_id
UNION ALL
SELECT 't_integration_verify_run.endpoints_json', COUNT(*),
       SUM(d.verify_run_id IS NULL OR NOT (SHA2(CAST(s.endpoints_json AS CHAR), 256) <=> SHA2(CAST(d.endpoints_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_run s LEFT JOIN mor_channel_connector.t_integration_verify_run d ON d.verify_run_id = s.verify_run_id
UNION ALL
SELECT 't_integration_verify_run_step.resolved_input_json', COUNT(*),
       SUM(d.verify_run_step_id IS NULL OR NOT (SHA2(CAST(s.resolved_input_json AS CHAR), 256) <=> SHA2(CAST(d.resolved_input_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_run_step s LEFT JOIN mor_channel_connector.t_integration_verify_run_step d ON d.verify_run_step_id = s.verify_run_step_id
UNION ALL
SELECT 't_integration_verify_run_step.result_json', COUNT(*),
       SUM(d.verify_run_step_id IS NULL OR NOT (SHA2(CAST(s.result_json AS CHAR), 256) <=> SHA2(CAST(d.result_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_run_step s LEFT JOIN mor_channel_connector.t_integration_verify_run_step d ON d.verify_run_step_id = s.verify_run_step_id
UNION ALL
SELECT 't_integration_verify_run_step.assertions_json', COUNT(*),
       SUM(d.verify_run_step_id IS NULL OR NOT (SHA2(CAST(s.assertions_json AS CHAR), 256) <=> SHA2(CAST(d.assertions_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_run_step s LEFT JOIN mor_channel_connector.t_integration_verify_run_step d ON d.verify_run_step_id = s.verify_run_step_id
UNION ALL
SELECT 't_integration_verify_run_step.trace_json', COUNT(*),
       SUM(d.verify_run_step_id IS NULL OR NOT (SHA2(CAST(s.trace_json AS CHAR), 256) <=> SHA2(CAST(d.trace_json AS CHAR), 256)))
  FROM mor_platform.t_integration_verify_run_step s LEFT JOIN mor_channel_connector.t_integration_verify_run_step d ON d.verify_run_step_id = s.verify_run_step_id;

-- V4 目标库结构：28 张表，change_detail 存在
SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'mor_channel_connector') AS channel_connector_tables,
       (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = 'mor_channel_connector' AND table_name = 't_channel_config_change' AND column_name = 'change_detail') AS change_detail_exists;
