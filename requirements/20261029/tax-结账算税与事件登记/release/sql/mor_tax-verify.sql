-- tax 结账算税与事件登记 · mor_tax 重建与初始化后核对（只读，可在 READ ONLY 会话执行；runbook ⑥）。目标库：mor_tax。
SET NAMES utf8mb4;

-- 1) 表：16 张新表，旧表不存在
SELECT COUNT(*) AS `tables_expect_16`,
       SUM(table_name IN ('t_tax_category','t_tax_market','t_tax_registration','t_tax_policy','t_tax_calculation','t_tax_calculation_line',
         't_tax_calculation_component','t_tax_evidence','t_tax_transaction','t_tax_reporting_entry','t_tax_period','t_tax_document',
         't_tax_document_sequence','t_compensation_event','t_consumed_event','t_consumed_aggregate_version')) AS `expected_names_16`,
       SUM(table_name IN ('t_tax_snapshot','t_tax_line','t_fiscal_record','t_tax_rule')) AS `old_tables_0`
FROM information_schema.tables WHERE table_schema = DATABASE();

-- 2) 初始化：税类 1、地区包 3、注册 3、准入 3、序列 3（重建后首次执行；验收后计算 / 交易 / 单据类表会有运行时数据）
SELECT (SELECT COUNT(*) FROM t_tax_category) AS `category_1`, (SELECT COUNT(*) FROM t_tax_policy) AS `policy_3`,
       (SELECT COUNT(*) FROM t_tax_registration) AS `registration_3`, (SELECT COUNT(*) FROM t_tax_market) AS `market_3`,
       (SELECT COUNT(*) FROM t_tax_document_sequence) AS `sequence_3`;

-- 3) 纳税主体 = mor-test AE001 Party（阶段 6：880000000702，MOCK 与 REAL 一致）
SELECT DISTINCT subject_party_id AS `party_expect_880000000702` FROM t_tax_registration
UNION SELECT DISTINCT subject_party_id FROM t_tax_market
UNION SELECT DISTINCT issuer_party_id FROM t_tax_document_sequence;

-- 4) 准入与地区包：三地 ENABLED、地区包 APPROVED、序列 next_number
SELECT m.jurisdiction_code, m.collection_status, m.managed_by, p.package_code, p.status AS `policy_status`,
       JSON_UNQUOTE(JSON_EXTRACT(p.policy_json, '$.treatments[0].result.rate')) AS `rate`, s.series_code, s.next_number
FROM t_tax_market m JOIN t_tax_policy p ON p.id = m.policy_id
JOIN t_tax_document_sequence s ON s.registration_id = m.registration_id ORDER BY m.jurisdiction_code;  -- AE 0.05 / EG 0.14 / SA 0.15
