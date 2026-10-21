-- 统一对外渠道出口第一段 · mor-test 发布前只读核对（阶段 6 已执行一次，阶段 7 第 1 步与备份时各重跑一次）。
-- 只含 SELECT / CHECKSUM TABLE；不读取凭据列（t_integration_verify_account.credentials / parameters 不输出）。
-- 执行：root，连接库 mysql；输出写备份目录 00-precheck.out。

-- P1 库表数：期望 mor_platform 57、mor_channel_connector 18、mor_card 8。
SELECT table_schema, COUNT(*) AS table_count
  FROM information_schema.tables
 WHERE table_schema IN ('mor_platform', 'mor_channel_connector', 'mor_card')
 GROUP BY table_schema ORDER BY table_schema;

-- P2 目标库尚无 10 张协议表（期望 0 行），t_channel_config_change 尚无 change_detail（期望 0）。
SELECT table_name AS unexpected_existing_table
  FROM information_schema.tables
 WHERE table_schema = 'mor_channel_connector'
   AND table_name IN ('t_standard_api', 't_api_field', 't_integration', 't_integration_api', 't_integration_script',
                      't_integration_standard_api', 't_integration_verify_account', 't_integration_verify_case',
                      't_integration_verify_run', 't_integration_verify_run_step');
SELECT COUNT(*) AS change_detail_column_exists
  FROM information_schema.columns
 WHERE table_schema = 'mor_channel_connector' AND table_name = 't_channel_config_change' AND column_name = 'change_detail';

-- P3 源库 10 表行数与最近写入时间（冻结后用于判断有无新写入）。
SELECT 't_standard_api' AS t, COUNT(*) AS row_count, MAX(update_time) AS last_write FROM mor_platform.t_standard_api
UNION ALL SELECT 't_api_field', COUNT(*), MAX(update_time) FROM mor_platform.t_api_field
UNION ALL SELECT 't_integration', COUNT(*), MAX(update_time) FROM mor_platform.t_integration
UNION ALL SELECT 't_integration_api', COUNT(*), MAX(update_time) FROM mor_platform.t_integration_api
UNION ALL SELECT 't_integration_script', COUNT(*), MAX(update_time) FROM mor_platform.t_integration_script
UNION ALL SELECT 't_integration_standard_api', COUNT(*), MAX(update_time) FROM mor_platform.t_integration_standard_api
UNION ALL SELECT 't_integration_verify_account', COUNT(*), MAX(update_time) FROM mor_platform.t_integration_verify_account
UNION ALL SELECT 't_integration_verify_case', COUNT(*), MAX(update_time) FROM mor_platform.t_integration_verify_case
UNION ALL SELECT 't_integration_verify_run', COUNT(*), MAX(update_time) FROM mor_platform.t_integration_verify_run
UNION ALL SELECT 't_integration_verify_run_step', COUNT(*), MAX(create_time) FROM mor_platform.t_integration_verify_run_step;

-- P4 源库校验和（导入后与目标库逐表对照）。
CHECKSUM TABLE mor_platform.t_standard_api, mor_platform.t_api_field, mor_platform.t_integration,
  mor_platform.t_integration_api, mor_platform.t_integration_script, mor_platform.t_integration_standard_api,
  mor_platform.t_integration_verify_account, mor_platform.t_integration_verify_case,
  mor_platform.t_integration_verify_run, mor_platform.t_integration_verify_run_step;

-- P5 接入方与出站地址（不含凭据）；验证运行状态分布（RUNNING 须为 0 才能冻结）。
SELECT integration_code, version, endpoints, create_time, update_time FROM mor_platform.t_integration ORDER BY integration_code;
SELECT run_status, COUNT(*) AS runs FROM mor_platform.t_integration_verify_run GROUP BY run_status ORDER BY run_status;
SELECT i.integration_code, s.standard_api_code, isa.version
  FROM mor_platform.t_integration_standard_api isa
  JOIN mor_platform.t_integration i ON i.integration_id = isa.integration_id
  JOIN mor_platform.t_standard_api s ON s.standard_api_id = isa.standard_api_id
 ORDER BY 1, 2;

-- P6 Platform 侧未完成的验证运行补偿事件（期望 0；非 0 时等其完成再冻结）。
SELECT event_type, execute_status, COUNT(*) AS events
  FROM mor_platform.t_compensation_event
 WHERE event_type = 'INTEGRATION_VERIFY_RUN'
 GROUP BY event_type, execute_status;

-- P7 channel-connector 现有配置与变更记录（迁移前基线）。
SELECT 't_payment_channel' AS t, COUNT(*) AS row_count FROM mor_channel_connector.t_payment_channel
UNION ALL SELECT 't_channel_account', COUNT(*) FROM mor_channel_connector.t_channel_account
UNION ALL SELECT 't_channel_mid', COUNT(*) FROM mor_channel_connector.t_channel_mid
UNION ALL SELECT 't_mid_capability', COUNT(*) FROM mor_channel_connector.t_mid_capability
UNION ALL SELECT 't_channel_config_change', COUNT(*) FROM mor_channel_connector.t_channel_config_change
UNION ALL SELECT 't_compensation_event', COUNT(*) FROM mor_channel_connector.t_compensation_event;
SELECT channel_code, channel_status, create_time FROM mor_channel_connector.t_payment_channel ORDER BY channel_code;
SELECT event_type, execute_status, COUNT(*) AS events FROM mor_channel_connector.t_compensation_event GROUP BY event_type, execute_status;

-- P8 Vault：channel-connector 的 REVEAL CHANNEL_ACCOUNT_CONFIG_V1 [CHANNEL_EXECUTION] 规则（期望 1 行 ACTIVE）。
SELECT caller_service, action, data_type_code, purpose_code, status
  FROM mor_vault.t_vault_access_rule
 WHERE data_type_code = 'CHANNEL_ACCOUNT_CONFIG_V1'
 ORDER BY caller_service, action;

-- P9 card 读卡票据：总数、未使用且未过期数（发布窗口内应为 0 才不影响在途支付）。
SELECT COUNT(*) AS tickets,
       SUM(used_at IS NULL) AS unused,
       SUM(used_at IS NULL AND expires_at > UTC_TIMESTAMP(6)) AS unused_unexpired,
       MAX(create_time) AS last_issued
  FROM mor_card.t_card_material_ticket;

-- P10 XXL：channel-connector / platform-support 执行器分组与任务（补偿兜底扫描是否登记）。
SELECT g.app_name, j.id, j.executor_handler, j.trigger_status
  FROM xxl_job.xxl_job_group g
  LEFT JOIN xxl_job.xxl_job_info j ON j.job_group = g.id
 WHERE g.app_name IN ('channel-connector-service', 'platform-support-service')
 ORDER BY g.app_name, j.id;
