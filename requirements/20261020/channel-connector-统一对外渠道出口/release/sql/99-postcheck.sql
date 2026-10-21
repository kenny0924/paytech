-- 统一对外渠道出口第一段 · 发布后只读核对（阶段 7：第 6 步回归后执行一次，第 9 步删表后再执行一次）。
-- 只输出计数与状态，不输出凭据列。

-- Q1 表数：channel-connector 28；Platform 删表前 57、删表后 47。
SELECT table_schema, COUNT(*) AS table_count
  FROM information_schema.tables
 WHERE table_schema IN ('mor_platform', 'mor_channel_connector')
 GROUP BY table_schema ORDER BY table_schema;

-- Q2 channel-connector 协议表行数（= 迁移行数 + 验收新增；验收写入范围见 release.md 第 9 节）。
SELECT 't_standard_api' AS t, COUNT(*) AS row_count FROM mor_channel_connector.t_standard_api
UNION ALL SELECT 't_api_field', COUNT(*) FROM mor_channel_connector.t_api_field
UNION ALL SELECT 't_integration', COUNT(*) FROM mor_channel_connector.t_integration
UNION ALL SELECT 't_integration_api', COUNT(*) FROM mor_channel_connector.t_integration_api
UNION ALL SELECT 't_integration_script', COUNT(*) FROM mor_channel_connector.t_integration_script
UNION ALL SELECT 't_integration_standard_api', COUNT(*) FROM mor_channel_connector.t_integration_standard_api
UNION ALL SELECT 't_integration_verify_account', COUNT(*) FROM mor_channel_connector.t_integration_verify_account
UNION ALL SELECT 't_integration_verify_case', COUNT(*) FROM mor_channel_connector.t_integration_verify_case
UNION ALL SELECT 't_integration_verify_run', COUNT(*) FROM mor_channel_connector.t_integration_verify_run
UNION ALL SELECT 't_integration_verify_run_step', COUNT(*) FROM mor_channel_connector.t_integration_verify_run_step;

-- Q3 新版本写入的变更记录（外部 API 新类型）与 change_detail 填充情况。
SELECT target_type, change_type, COUNT(*) AS records, SUM(change_detail IS NOT NULL) AS with_detail
  FROM mor_channel_connector.t_channel_config_change
 GROUP BY target_type, change_type ORDER BY target_type, change_type;

-- Q4 channel-connector 本地补偿（验证运行）：成功后记录即删除；期望 0 行，或只有刚创建的 INIT。
SELECT event_type, execute_status, COUNT(*) AS events, MAX(update_time) AS last_update
  FROM mor_channel_connector.t_compensation_event GROUP BY event_type, execute_status;

-- Q5 Vault：发布后 CHANNEL_ACCOUNT_CONFIG_V1 的 REVEAL 调用方分布（期望发布后只有 channel-connector-service）。
SELECT caller_service, action, purpose, outcome, COUNT(*) AS events, MAX(create_time) AS last_event
  FROM mor_vault.t_vault_access_event
 WHERE data_type = 'CHANNEL_ACCOUNT_CONFIG_V1' AND create_time >= UTC_TIMESTAMP(6) - INTERVAL 1 DAY
 GROUP BY caller_service, action, purpose, outcome ORDER BY last_event;

-- Q6 card 读卡票据（可选验收 C3 执行后：used_at 非空，签发方 payment-service）。
SELECT caller, purpose, COUNT(*) AS tickets, SUM(used_at IS NOT NULL) AS used
  FROM mor_card.t_card_material_ticket GROUP BY caller, purpose;
