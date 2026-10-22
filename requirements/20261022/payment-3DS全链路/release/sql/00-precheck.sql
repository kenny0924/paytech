-- 3DS 全链路 + 卡 BIN 库合并发布：mor-test 执行前只读核对（阶段 6 编写并试跑；阶段 7 备份后再跑一次，结果须与阶段 6 相同）。
-- 只含 SELECT；以 root 连接 mor-test-mysql-1，默认库 mysql，客户端 utf8mb4。每行 ok=1 为符合期望；任一行 ok=0 停止发布，回到 release.md「需要决定」。
-- 期望值依据 2026-10-22 阶段 6 只读核对（release.md 第 3 节）。

SELECT item, actual, expected, COALESCE(ok, CAST(actual AS CHAR) = CAST(expected AS CHAR)) AS ok FROM (
-- 1. 新增对象均不存在（DDL 未执行）
SELECT '01 mor_card BIN 4 表不存在' AS item, COUNT(*) AS actual, 0 AS expected, COUNT(*) = 0 AS ok
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_card'
   AND TABLE_NAME IN ('t_card_bin_library_version', 't_card_bin_range', 't_card_bin_import_error', 't_card_bin_library_activation')
UNION ALL
SELECT '02 mor_payment 消费表 2 张不存在', COUNT(*), 0, COUNT(*) = 0
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_payment' AND TABLE_NAME IN ('t_consumed_event', 't_consumed_aggregate_version')
UNION ALL
SELECT '03 mor_payment Token 卡属性 8 列不存在', COUNT(*), 0, COUNT(*) = 0
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_payment' AND TABLE_NAME = 't_payment_instrument_token'
   AND COLUMN_NAME IN ('card_brand', 'funding_type', 'card_level', 'card_product', 'issuer_country', 'issuer_region',
                       'bin_library_version_no', 'bin_match_length')
UNION ALL
SELECT '04 mor_payment Token 锚点列 card_fingerprint 存在', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_payment' AND TABLE_NAME = 't_payment_instrument_token' AND COLUMN_NAME = 'card_fingerprint'
UNION ALL
SELECT '05 mor_payment 交互类型列定义 varchar(32) ascii_bin 可空', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_payment' AND TABLE_NAME = 't_payment_action' AND COLUMN_NAME = 'interaction_type'
   AND COLUMN_TYPE = 'varchar(32)' AND IS_NULLABLE = 'YES' AND COLLATION_NAME = 'ascii_bin'
UNION ALL
SELECT '06 mor_risk_decision 评估快照 3 列不存在', COUNT(*), 0, COUNT(*) = 0
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_risk_decision' AND TABLE_NAME = 't_transaction_risk_assessment'
   AND COLUMN_NAME IN ('issuer_country_code', 'card_brand', 'funding_type')
UNION ALL
SELECT '07 mor_risk_decision 锚点列 card_bin 存在', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_risk_decision' AND TABLE_NAME = 't_transaction_risk_assessment' AND COLUMN_NAME = 'card_bin'
UNION ALL
SELECT '08 mor_channel_connector t_channel.callback_definition_json 不存在', COUNT(*), 0, COUNT(*) = 0
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_channel_connector' AND TABLE_NAME = 't_channel' AND COLUMN_NAME = 'callback_definition_json'
UNION ALL
SELECT '09 mor_channel_connector 锚点列 endpoints 存在', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_channel_connector' AND TABLE_NAME = 't_channel' AND COLUMN_NAME = 'endpoints'
UNION ALL
SELECT '10 mor_channel_connector target_type varchar(64) NOT NULL utf8mb4', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_channel_connector' AND TABLE_NAME = 't_channel_callback_item'
   AND COLUMN_NAME = 'target_type' AND COLUMN_TYPE = 'varchar(64)' AND IS_NULLABLE = 'NO' AND CHARACTER_SET_NAME = 'utf8mb4'
UNION ALL
SELECT '11 mor_channel_connector received_at datetime(6) NOT NULL', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_channel_connector' AND TABLE_NAME = 't_channel_callback'
   AND COLUMN_NAME = 'received_at' AND COLUMN_TYPE = 'datetime(6)' AND IS_NULLABLE = 'NO'
UNION ALL
SELECT '12 mor_platform t_country_region 不存在', COUNT(*), 0, COUNT(*) = 0
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME = 't_country_region'
UNION ALL
SELECT '13 表数 mor_card / payment / channel_connector / risk_decision / platform = 8/15/28/54/62',
       CONCAT_WS('/', SUM(TABLE_SCHEMA = 'mor_card'), SUM(TABLE_SCHEMA = 'mor_payment'), SUM(TABLE_SCHEMA = 'mor_channel_connector'),
                 SUM(TABLE_SCHEMA = 'mor_risk_decision'), SUM(TABLE_SCHEMA = 'mor_platform')), '8/15/28/54/62',
       SUM(TABLE_SCHEMA = 'mor_card') = 8 AND SUM(TABLE_SCHEMA = 'mor_payment') = 15 AND SUM(TABLE_SCHEMA = 'mor_channel_connector') = 28
       AND SUM(TABLE_SCHEMA = 'mor_risk_decision') = 54 AND SUM(TABLE_SCHEMA = 'mor_platform') = 62
  FROM information_schema.TABLES WHERE TABLE_SCHEMA IN ('mor_card', 'mor_payment', 'mor_channel_connector', 'mor_risk_decision', 'mor_platform')

UNION ALL
SELECT '14 文件多存储第二段删除 DDL 已执行：t_file_upload 无 owner_* 三列、无 t_file_owner_ref、t_application_document 无 file_id',
       (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME = 't_file_upload'
          AND COLUMN_NAME IN ('owner_system', 'owner_type', 'owner_ref_id'))
     + (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'mor_platform' AND TABLE_NAME = 't_file_owner_ref')
     + (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_client_master' AND TABLE_NAME = 't_application_document' AND COLUMN_NAME = 'file_id'),
       0, NULL
UNION ALL
SELECT '15 t_application_document 只按 file_address 关联', COUNT(*), 1, COUNT(*) = 1
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_client_master' AND TABLE_NAME = 't_application_document' AND COLUMN_NAME = 'file_address'

-- 2. Platform 号段 132 / 133 与代码、路径空闲
UNION ALL
SELECT '20 mor_platform 块 132/133 九张表 ID 段全空',
       (SELECT COUNT(*) FROM mor_platform.t_permission WHERE permission_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_role_permission WHERE role_permission_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_console_resource WHERE resource_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_gateway_route_header WHERE route_header_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_gateway_target WHERE target_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_file_scan_policy WHERE policy_id BETWEEN 900000000000132001 AND 900000000000133999)
     + (SELECT COUNT(*) FROM mor_platform.t_file_scan_requirement WHERE requirement_id BETWEEN 900000000000132001 AND 900000000000133999)
       , 0, NULL
UNION ALL
SELECT '21 权限码 CARD_BIN_LIBRARY_READ / MANAGE 未占用', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_permission WHERE permission_code IN ('CARD_BIN_LIBRARY_READ', 'CARD_BIN_LIBRARY_MANAGE')
UNION ALL
SELECT '22 扫描策略 CARD_BIN_CSV 未占用', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_scan_policy WHERE policy_code = 'CARD_BIN_CSV'
UNION ALL
SELECT '23 路由码 channel-payment-callback 与 BIN / 回调外部路径未占用', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_route WHERE route_code = 'channel-payment-callback'
    OR external_path_template LIKE '/api/channel-callbacks/%' OR external_path_template LIKE '%/bin-library%'
UNION ALL
SELECT '24 Target channel-connector-service / 渠道 CHANNEL_CALLBACK 未占用', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_gateway_target WHERE target_service_code = 'channel-connector-service' OR channel = 'CHANNEL_CALLBACK'
UNION ALL
SELECT '25 菜单路由 /card-bin-library 未占用', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_console_resource WHERE frontend_route = '/card-bin-library'
UNION ALL
SELECT '26 块 132 依赖：ops-bff Target 900000000000000001 ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_target WHERE target_id = 900000000000000001 AND target_service_code = 'operations-console-bff' AND status = 'ACTIVE'
UNION ALL
SELECT '27 块 132 依赖：父菜单 平台配置 900000000000000562 ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_console_resource WHERE resource_id = 900000000000000562 AND status = 'ACTIVE'
UNION ALL
SELECT '28 PLATFORM_ADMIN 角色版本 = 44', MAX(version), 44, MAX(version) = 44
  FROM mor_platform.t_role WHERE role_id = 900000000000000510
UNION ALL
SELECT '29 Gateway 路由总数 / ACTIVE = 526 / 521', CONCAT(COUNT(*), '/', SUM(status = 'ACTIVE')), '526/521',
       COUNT(*) = 526 AND SUM(status = 'ACTIVE') = 521
  FROM mor_platform.t_gateway_route
UNION ALL
SELECT '30 ops-bff 文件写入路由不存在（发布时在存储管理页登记）', COUNT(*), 0, COUNT(*) = 0
  FROM mor_platform.t_file_storage_route WHERE source_system = 'operations-console-bff'
UNION ALL
SELECT '31 写入路由目标桶 mor-platform-files ACTIVE', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_file_storage_bucket WHERE bucket_id = 238885048240128000 AND bucket_name = 'mor-platform-files' AND status = 'ACTIVE'

-- 3. Risk 220003 与变量 93014—016 空闲；支付点现状
UNION ALL
SELECT '40 Risk 点版本 220003 / 210002 版本 2 未占用', COUNT(*), 0, COUNT(*) = 0
  FROM mor_risk_decision.t_decision_point_version WHERE point_version_id = 220003 OR (decision_point_id = 210002 AND version_no = 2)
UNION ALL
SELECT '41 Risk 220002 摘要与初始化数据一致', COUNT(*), 1, COUNT(*) = 1
  FROM mor_risk_decision.t_decision_point_version WHERE point_version_id = 220002 AND version_no = 1 AND status = 'FROZEN'
   AND content_hash = '265ad2859a6b79e54aa040769255544300ecee9add59b412202f4eccce911e52'
UNION ALL
SELECT '42 Risk 变量 93014—016 与三个变量码未占用', COUNT(*), 0, COUNT(*) = 0
  FROM mor_risk_decision.t_risk_variable WHERE variable_id BETWEEN 900000000000093014 AND 900000000000093016
    OR variable_code IN ('PAY_INPUT_ISSUER_COUNTRY', 'PAY_INPUT_CARD_BRAND', 'PAY_INPUT_FUNDING_TYPE')
UNION ALL
SELECT '43 支付点 ACTIVE 发布 = release 11（点版本 220002）', GROUP_CONCAT(release_no), '11',
       COUNT(*) = 1 AND MAX(release_id) = 236518950932942848 AND MAX(point_version_id) = 220002
  FROM mor_risk_decision.t_decision_release WHERE decision_point_id = 210002 AND status = 'ACTIVE'
UNION ALL
SELECT '44 R-11：THREE_DS_AMOUNT（金额 > 500 → THREE_DS_REQUIRED）在生效流程中', COUNT(*), 1, COUNT(*) = 1
  FROM mor_risk_decision.t_risk_rule r
  JOIN mor_risk_decision.t_decision_flow_node n ON n.rule_set_version_id = r.rule_set_version_id
 WHERE n.flow_version_id = 236518950786142209 AND r.rule_code = 'THREE_DS_AMOUNT' AND r.enabled_flag = 1

-- 4. 渠道现状（configure_3ds 与回调定义的前置）
UNION ALL
SELECT '50 渠道 PAYERMAX 存在（PAYMENT）', COUNT(*), 1, COUNT(*) = 1
  FROM mor_channel_connector.t_channel WHERE channel_code = 'PAYERMAX' AND channel_type = 'PAYMENT'
UNION ALL
SELECT '51 渠道 PAYERMAX_MOCK 不存在（回调定义 DML 在 mor-test 只影响 1 行）', COUNT(*), 0, COUNT(*) = 0
  FROM mor_channel_connector.t_channel WHERE channel_code = 'PAYERMAX_MOCK'
UNION ALL
SELECT '52 PAYERMAX 无技术账号与 MID', (SELECT COUNT(*) FROM mor_channel_connector.t_channel_account a JOIN mor_channel_connector.t_channel c ON c.channel_id = a.channel_id WHERE c.channel_code = 'PAYERMAX')
     + (SELECT COUNT(*) FROM mor_channel_connector.t_channel_mid m JOIN mor_channel_connector.t_channel c ON c.channel_id = m.channel_id WHERE c.channel_code = 'PAYERMAX'), 0, NULL
UNION ALL
SELECT '53 PAYERMAX 原子 API ORDER_AND_PAY 仍为收银台版（无 cardInfo）', COUNT(*), 1, COUNT(*) = 1
  FROM mor_channel_connector.t_channel_api a JOIN mor_channel_connector.t_channel c ON c.channel_id = a.channel_id
 WHERE c.channel_code = 'PAYERMAX' AND a.api_code = 'ORDER_AND_PAY' AND a.definition_json NOT LIKE '%cardInfo%'
UNION ALL
SELECT '54 标准 API 版本 PAY/PAY_QUERY/REFUND/CAPTURE = 3/1/2/2', CONCAT_WS('/', MAX(IF(standard_api_code = 'PAY', version, NULL)),
       MAX(IF(standard_api_code = 'PAY_QUERY', version, NULL)), MAX(IF(standard_api_code = 'REFUND', version, NULL)),
       MAX(IF(standard_api_code = 'CAPTURE', version, NULL))), '3/1/2/2',
       MAX(IF(standard_api_code = 'PAY', version, NULL)) = 3 AND MAX(IF(standard_api_code = 'PAY_QUERY', version, NULL)) = 1
       AND MAX(IF(standard_api_code = 'REFUND', version, NULL)) = 2 AND MAX(IF(standard_api_code = 'CAPTURE', version, NULL)) = 2
  FROM mor_channel_connector.t_standard_api
UNION ALL
SELECT '55 标准 API PAY 尚无 payment.threeDsRequired', COUNT(*), 0, COUNT(*) = 0
  FROM mor_channel_connector.t_api_field f JOIN mor_channel_connector.t_standard_api s ON s.standard_api_id = f.api_id
 WHERE s.standard_api_code = 'PAY' AND f.field_path = 'payment.threeDsRequired'
UNION ALL
SELECT '56 code-mor-46 字段 REFUND / CAPTURE paymentChannelRef 存在（不得被覆盖）', COUNT(*), 2, COUNT(*) = 2
  FROM mor_channel_connector.t_api_field f JOIN mor_channel_connector.t_standard_api s ON s.standard_api_id = f.api_id
 WHERE s.standard_api_code IN ('REFUND', 'CAPTURE') AND f.field_path IN ('refund.paymentChannelRef', 'capture.paymentChannelRef')
UNION ALL
SELECT '57 t_mid_capability.scheme_code 全为 *（3 行）', CONCAT(COUNT(*), ' rows; non-* ', SUM(scheme_code <> '*')), '3 rows; non-* 0',
       COUNT(*) = 3 AND SUM(scheme_code <> '*') = 0
  FROM mor_channel_connector.t_mid_capability
UNION ALL
SELECT '58 尚无 SECURITY.3DS_MODE 能力明细', COUNT(*), 0, COUNT(*) = 0
  FROM mor_channel_connector.t_mid_capability_detail WHERE capability_code = 'SECURITY.3DS_MODE'
UNION ALL
SELECT '59 回调两表为空', (SELECT COUNT(*) FROM mor_channel_connector.t_channel_callback) + (SELECT COUNT(*) FROM mor_channel_connector.t_channel_callback_item), 0, NULL

-- 5. 交易数据现状（影响面）
UNION ALL
SELECT '60 mor_payment 意图 / 尝试 / 动作 / Token / 补偿 全为 0',
       CONCAT_WS('/', (SELECT COUNT(*) FROM mor_payment.t_payment_intent), (SELECT COUNT(*) FROM mor_payment.t_payment_attempt),
                 (SELECT COUNT(*) FROM mor_payment.t_payment_action), (SELECT COUNT(*) FROM mor_payment.t_payment_instrument_token),
                 (SELECT COUNT(*) FROM mor_payment.t_compensation_event)), '0/0/0/0/0', NULL
UNION ALL
SELECT '61 mor_card 补偿事件为 0', COUNT(*), 0, COUNT(*) = 0 FROM mor_card.t_compensation_event
UNION ALL
SELECT '62 mor_platform 工作流 #28 disposition 查询参数仍在（42879401 发布，127030 / 路由 127432 版本 3）', COUNT(*), 1, COUNT(*) = 1
  FROM mor_platform.t_gateway_route_query_param q JOIN mor_platform.t_gateway_route r ON r.route_id = q.route_id
 WHERE q.route_query_param_id = 900000000000127030 AND q.route_id = 900000000000127432 AND q.parameter_name = 'disposition' AND r.version = 3

-- 6. XXL 执行器与任务（不新增）
UNION ALL
SELECT '70 XXL compensationEventScan 已登记且启用（payment / card / channel-connector）', COUNT(*), 3, COUNT(*) = 3
  FROM xxl_job.xxl_job_info j JOIN xxl_job.xxl_job_group g ON g.id = j.job_group
 WHERE g.app_name IN ('payment-service', 'card-service', 'channel-connector-service')
   AND j.executor_handler = 'compensationEventScan' AND j.trigger_status = 1
) AS checks;
