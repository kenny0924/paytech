-- 3DS 全链路 + 卡 BIN 库合并发布：mor-test 执行后只读核对（release.md 第 6 节 S9，激活与回调定义完成之后、验收写入之前）。
-- 只含 SELECT；root，默认库 mysql，客户端 utf8mb4。每行 ok=1 为符合期望。
SELECT item, actual, expected, CAST(actual AS CHAR) = CAST(expected AS CHAR) AS ok FROM (
SELECT '01 表数 card/payment/channel_connector/risk_decision/platform' AS item,
       CONCAT_WS('/', SUM(TABLE_SCHEMA = 'mor_card'), SUM(TABLE_SCHEMA = 'mor_payment'), SUM(TABLE_SCHEMA = 'mor_channel_connector'),
                 SUM(TABLE_SCHEMA = 'mor_risk_decision'), SUM(TABLE_SCHEMA = 'mor_platform')) AS actual, '12/17/28/54/63' AS expected
  FROM information_schema.TABLES WHERE TABLE_SCHEMA IN ('mor_card', 'mor_payment', 'mor_channel_connector', 'mor_risk_decision', 'mor_platform')
UNION ALL
SELECT '02 mor_payment Token 卡属性 8 列', COUNT(*), 8 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'mor_payment' AND TABLE_NAME = 't_payment_instrument_token'
   AND COLUMN_NAME IN ('card_brand', 'funding_type', 'card_level', 'card_product', 'issuer_country', 'issuer_region', 'bin_library_version_no', 'bin_match_length')
UNION ALL
SELECT '03 mor_payment 交互类型注释含 REDIRECT 写入说明', COUNT(*), 1 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'mor_payment' AND TABLE_NAME = 't_payment_action' AND COLUMN_NAME = 'interaction_type'
   AND COLUMN_COMMENT LIKE '%本期按渠道返回写 THREE_DS_CHALLENGE 或 REDIRECT%' AND COLLATION_NAME = 'ascii_bin' AND IS_NULLABLE = 'YES'
UNION ALL
SELECT '04 mor_risk_decision 评估快照 3 列', COUNT(*), 3 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'mor_risk_decision' AND TABLE_NAME = 't_transaction_risk_assessment' AND COLUMN_NAME IN ('issuer_country_code', 'card_brand', 'funding_type')
UNION ALL
SELECT '05 Risk 220003 FROZEN（210002 版本 2）', COUNT(*), 1 FROM mor_risk_decision.t_decision_point_version
 WHERE point_version_id = 220003 AND decision_point_id = 210002 AND version_no = 2 AND status = 'FROZEN'
UNION ALL
SELECT '06 Risk 变量 93014—016 ACTIVE', COUNT(*), 3 FROM mor_risk_decision.t_risk_variable
 WHERE variable_id BETWEEN 900000000000093014 AND 900000000000093016 AND status = 'ACTIVE'
UNION ALL
SELECT '07 支付点 ACTIVE 发布仍为 release 11（验收前不改）', GROUP_CONCAT(release_no), '11' FROM mor_risk_decision.t_decision_release
 WHERE decision_point_id = 210002 AND status = 'ACTIVE'
UNION ALL
SELECT '10 mor_channel_connector callback_definition_json 列', COUNT(*), 1 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'mor_channel_connector' AND TABLE_NAME = 't_channel' AND COLUMN_NAME = 'callback_definition_json' AND DATA_TYPE = 'json'
UNION ALL
SELECT '11 target_type 注释含 PAYMENT_ATTEMPT', COUNT(*), 1 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = 'mor_channel_connector' AND TABLE_NAME = 't_channel_callback_item' AND COLUMN_NAME = 'target_type' AND COLUMN_COMMENT LIKE '%PAYMENT_ATTEMPT%'
UNION ALL
SELECT '12 PAYERMAX 回调定义 RSA_SHA256（mor-test 只有 PAYERMAX）', GROUP_CONCAT(channel_code), 'PAYERMAX' FROM mor_channel_connector.t_channel
 WHERE JSON_UNQUOTE(JSON_EXTRACT(callback_definition_json, '$.signature.algorithm')) = 'RSA_SHA256'
UNION ALL
SELECT '13 code-mor-46 paymentChannelRef 两处仍在', COUNT(*), 2 FROM mor_channel_connector.t_api_field f
  JOIN mor_channel_connector.t_standard_api s ON s.standard_api_id = f.api_id
 WHERE s.standard_api_code IN ('REFUND', 'CAPTURE') AND f.field_path IN ('refund.paymentChannelRef', 'capture.paymentChannelRef')
UNION ALL
SELECT '20 mor_card 种子：v1 ACTIVE / 号段 17 / 激活 1',
       CONCAT_WS('/', (SELECT COUNT(*) FROM mor_card.t_card_bin_library_version WHERE version_id = 1001 AND status = 'ACTIVE' AND active_slot = 1),
                 (SELECT COUNT(*) FROM mor_card.t_card_bin_range WHERE version_id = 1001),
                 (SELECT COUNT(*) FROM mor_card.t_card_bin_library_activation WHERE activation_id = 1201)), '1/17/1'
UNION ALL
SELECT '30 Platform 国家区域 249 行', COUNT(*), 249 FROM mor_platform.t_country_region
UNION ALL
SELECT '31 扫描策略 CARD_BIN_CSV ACTIVE + FORMAT 要求', CONCAT((SELECT COUNT(*) FROM mor_platform.t_file_scan_policy WHERE policy_code = 'CARD_BIN_CSV' AND status = 'ACTIVE'), '/',
       (SELECT COUNT(*) FROM mor_platform.t_file_scan_requirement WHERE requirement_id = 900000000000132902)), '1/1'
UNION ALL
SELECT '32 块 132 行数 权限/授权/菜单/路由/头/参数/守卫',
       CONCAT_WS('/', (SELECT COUNT(*) FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000132001 AND 900000000000132999),
                 (SELECT COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000132001 AND 900000000000132999 AND status = 'ACTIVE'),
                 (SELECT COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000132001 AND 900000000000132999 AND status = 'ACTIVE'),
                 (SELECT COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000132001 AND 900000000000132999 AND status = 'ACTIVE'),
                 (SELECT COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000132001 AND 900000000000132999),
                 (SELECT COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000132001 AND 900000000000132999),
                 (SELECT COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000132001 AND 900000000000132999)),
       '2/2/2/9/34/8/54'
UNION ALL
SELECT '33 块 133 行数 Target/路由 ACTIVE/头/守卫',
       CONCAT_WS('/', (SELECT COUNT(*) FROM mor_platform.t_gateway_target WHERE target_id = 900000000000133801 AND channel = 'CHANNEL_CALLBACK' AND status = 'ACTIVE'),
                 (SELECT COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id = 900000000000133401 AND status = 'ACTIVE'),
                 (SELECT COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000133001 AND 900000000000133999),
                 (SELECT COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000133001 AND 900000000000133999)),
       '1/1/2/4'
UNION ALL
SELECT '34 Gateway 路由总数 / ACTIVE = 536 / 531', CONCAT(COUNT(*), '/', SUM(status = 'ACTIVE')), '536/531' FROM mor_platform.t_gateway_route
UNION ALL
SELECT '35 PLATFORM_ADMIN 角色版本 = 45', MAX(version), 45 FROM mor_platform.t_role WHERE role_id = 900000000000000510
UNION ALL
SELECT '36 ops-bff 写入路由 → mor-platform-files + CARD_BIN_CSV / 900 秒', COUNT(*), 1 FROM mor_platform.t_file_storage_route
 WHERE source_system = 'operations-console-bff' AND bucket_id = 238885048240128000 AND scan_policy_code = 'CARD_BIN_CSV' AND max_upload_seconds = 900
UNION ALL
SELECT '37 既有写入路由未改（* 与 client-console-bff）', COUNT(*), 2 FROM mor_platform.t_file_storage_route
 WHERE route_id IN (238885821824974848, 239050450635145216) AND version = 1
UNION ALL
SELECT '38 mor_platform 工作流 #28 disposition 查询参数仍在（42879401 发布，127030 / 路由 127432 版本 3）', COUNT(*), 1
  FROM mor_platform.t_gateway_route_query_param q JOIN mor_platform.t_gateway_route r ON r.route_id = q.route_id
 WHERE q.route_query_param_id = 900000000000127030 AND q.route_id = 900000000000127432 AND q.parameter_name = 'disposition' AND r.version = 3
UNION ALL
SELECT '40 XXL compensationEventScan 三个执行器仍启用', COUNT(*), 3 FROM xxl_job.xxl_job_info j JOIN xxl_job.xxl_job_group g ON g.id = j.job_group
 WHERE g.app_name IN ('payment-service', 'card-service', 'channel-connector-service') AND j.executor_handler = 'compensationEventScan' AND j.trigger_status = 1
UNION ALL
SELECT '41 XXL 三个执行器 1 分钟内有心跳', COUNT(*), 3 FROM xxl_job.xxl_job_registry
 WHERE registry_key IN ('payment-service', 'card-service', 'channel-connector-service') AND update_time > NOW() - INTERVAL 90 SECOND
) AS checks;
