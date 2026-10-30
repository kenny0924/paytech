-- tax 结账算税与事件登记 · 发布前只读预检（runbook ④）。root 只读会话执行（--init-command="SET SESSION TRANSACTION READ ONLY"），不指定库。
-- 期望值为阶段 6（2026-10-29T19:37Z）读数；任一不符即停，回到阶段 6 重新核对。
SET NAMES utf8mb4;

-- 1) mor_tax：旧 24 张表、全部 0 行（重建前提）；新表不存在
SELECT COUNT(*) AS `tables_expect_24`, SUM(table_name = 't_tax_calculation') AS `new_table_expect_0`
FROM information_schema.tables WHERE table_schema = 'mor_tax';
SELECT (SELECT COUNT(*) FROM mor_tax.t_compensation_event) + (SELECT COUNT(*) FROM mor_tax.t_fiscal_number_sequence)
     + (SELECT COUNT(*) FROM mor_tax.t_fiscal_record) + (SELECT COUNT(*) FROM mor_tax.t_fiscal_record_line)
     + (SELECT COUNT(*) FROM mor_tax.t_place_of_supply_rule) + (SELECT COUNT(*) FROM mor_tax.t_tax_authority)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_category) + (SELECT COUNT(*) FROM mor_tax.t_tax_category_mapping)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_evidence) + (SELECT COUNT(*) FROM mor_tax.t_tax_evidence_rule)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_exchange_rate) + (SELECT COUNT(*) FROM mor_tax.t_tax_jurisdiction)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_liability_summary) + (SELECT COUNT(*) FROM mor_tax.t_tax_line)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_payment) + (SELECT COUNT(*) FROM mor_tax.t_tax_payment_event)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_period) + (SELECT COUNT(*) FROM mor_tax.t_tax_rate)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_registration) + (SELECT COUNT(*) FROM mor_tax.t_tax_return)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_return_line) + (SELECT COUNT(*) FROM mor_tax.t_tax_rule)
     + (SELECT COUNT(*) FROM mor_tax.t_tax_snapshot) + (SELECT COUNT(*) FROM mor_tax.t_tax_treatment) AS `old_rows_expect_0`;

-- 2) AE001 纳税主体（tax 初始化代入值；acquiring MOCK 配置 AE001_PARTY_ID 同为 880000000702）
SELECT entity_no, party_id, status FROM mor_client_master.t_operating_entity WHERE entity_no = 'AE001';  -- 880000000702 ACTIVE

-- 3) mor_acquiring：新表与新列不存在；存量订单无 OPEN；回滚前置值
SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'mor_acquiring' AND table_name = 't_order_tax_calculation') AS `new_table_expect_0`,
       (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'mor_acquiring'
          AND ((table_name = 't_commerce_order' AND column_name = 'amount_status')
            OR (table_name = 't_order_context' AND column_name = 'initial_tax_calculation_id')
            OR (table_name = 't_consumer_contract' AND column_name = 'tax_calculation_id'))) AS `new_columns_expect_0`;
SELECT order_status, COUNT(*) AS `orders` FROM mor_acquiring.t_commerce_order GROUP BY order_status ORDER BY order_status;  -- CANCELLED 11 / EXPIRED 30 / PAID 10
SELECT tax_category_code, COUNT(*) AS `items` FROM mor_acquiring.t_order_item GROUP BY tax_category_code;                 -- STANDARD 81

-- 4) mor_billing：ref_type 注释尚无 TAX_CALCULATION
SELECT LOCATE('TAX_CALCULATION', column_comment) AS `tax_calculation_in_comment_expect_0`
FROM information_schema.columns WHERE table_schema = 'mor_billing' AND table_name = 't_billing_fact_ref' AND column_name = 'ref_type';

-- 5) 应用账号库级 DML 覆盖新表（无需新授权）
SELECT grantee, table_schema, GROUP_CONCAT(privilege_type ORDER BY privilege_type) AS `privs`
FROM information_schema.schema_privileges WHERE table_schema IN ('mor_tax', 'mor_acquiring', 'mor_billing')
  AND grantee IN ('''tax_app''@''%''', '''acquiring_app''@''%''', '''billing_app''@''%''') GROUP BY grantee, table_schema;  -- 各 DELETE,INSERT,SELECT,UPDATE
