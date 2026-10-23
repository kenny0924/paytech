-- billing 支付事实自动登记 · 阶段 7 链路核对（只读，只有 SELECT）。发布一条合成 OrderPaymentSucceeded 后约 30 秒执行。
-- 用法（root 口令只经 MYSQL_PWD）：先改下面两个变量（PLATFORM 场景 attempt 999900000000000104 / order 999900000000000101；
-- PARTNER 场景 999900000000000204 / 999900000000000201），再
--   docker exec -i -e MYSQL_PWD=<root> mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t < acceptance/check_chain.sql
-- Broker 侧 DLQ 与消费者：python3 -I acceptance/queue_status.py（管理 API，只读）。

SET @attempt := '999900000000000104';
SET @order := '999900000000000101';
SET @event_id := CONCAT('ORDER:', @order, ':OrderPaymentSucceeded:3');

-- ========== billing ==========
SELECT id, fact_type, origin_type, source_system, source_event_key, source_version, status, fact_version, client_id, entity_no,
       local_partner_id, finance_entity_id, currency, gross_amount, business_time, balance_available_time, approved_time, published_at
FROM mor_billing.t_billing_fact WHERE source_system = 'acquiring' AND source_event_key = CONCAT('PAYMENT_ATTEMPT:', @attempt);
SET @fact := (SELECT id FROM mor_billing.t_billing_fact WHERE source_system = 'acquiring' AND source_event_key = CONCAT('PAYMENT_ATTEMPT:', @attempt));
SELECT 'fact id' AS k, @fact AS v;

SELECT * FROM mor_billing.t_billing_fact_pricing WHERE fact_id = @fact;
SELECT ref_type, ref_id, external_reference_type, external_transaction_id FROM mor_billing.t_billing_fact_ref WHERE fact_id = @fact ORDER BY ref_type;
SELECT line_no, component_type, payer_role, payer_id, payee_role, payee_id, basis_amount, rate_value, raw_amount, posted_amount,
       effect_direction, source_rule_type, source_rule_version_id, source_price_id, source_rule_component_id
FROM mor_billing.t_billing_fact_line WHERE fact_id = @fact ORDER BY line_no;
-- 期望：Σ CLIENT_PRINCIPAL + Σ CLIENT_FEE = gross_amount；Partner 场景另有 PARTNER_SHARE、PLATFORM_SERVICE_CHARGE；无 SELLER_TAX。
SELECT 'principal+fee = gross' AS k,
       (SELECT SUM(posted_amount) FROM mor_billing.t_billing_fact_line WHERE fact_id = @fact AND component_type IN ('CLIENT_PRINCIPAL', 'CLIENT_FEE')) AS lines_sum,
       (SELECT gross_amount FROM mor_billing.t_billing_fact WHERE id = @fact) AS gross;
SELECT event_key, event_type, execute_status, execute_count, last_error_code, update_time
FROM mor_billing.t_compensation_event WHERE event_key = CONCAT('BILLING_FACT:', @fact, ':BillingFactRecorded:1');
SELECT * FROM mor_billing.t_consumed_event WHERE event_id = @event_id;

-- ========== account ==========
SELECT id, fact_id, fact_version, fact_type, entity_no, finance_entity_id, currency, apply_status, posting_id, balance_available_time,
       maturity_status, matured_time, applied_time
FROM mor_account.t_balance_applied_fact WHERE fact_id = @fact;
SELECT id, entity_type, entity_id, currency, shard_no, posting_sequence, source_type, fact_id, entry_count, posted_time, published_time
FROM mor_account.t_account_posting WHERE fact_id = @fact ORDER BY id;
SELECT p.entity_type, p.entity_id, e.line_no, e.fact_line_no, e.entry_side, e.amount, o.account_no, o.account_code, o.owner_type,
       o.owner_id, o.balance_bucket, o.net_balance
FROM mor_account.t_account_posting p JOIN mor_account.t_account_entry e ON e.posting_id = p.id
JOIN mor_account.t_owner_account o ON o.id = e.account_id
WHERE p.fact_id = @fact ORDER BY p.id, e.line_no;
-- AccountPosted 发布任务（每个 posting 一条）。
SELECT c.event_key, c.execute_status, c.execute_count, c.last_error_code, c.update_time
FROM mor_account.t_compensation_event c JOIN mor_account.t_account_posting p
  ON c.event_key LIKE CONCAT('ACCOUNT_POSTING:%:AccountPosted:', p.posting_sequence)
 AND c.event_key LIKE CONCAT('ACCOUNT_POSTING:%', p.entity_id, '.', p.currency, '.', p.shard_no, ':%')
WHERE p.fact_id = @fact;

-- ========== ledger ==========
SELECT i.id, b.entity_type, b.entity_id, i.instruction_type, i.origin_type, i.source_system, i.source_event_key, i.status,
       i.accounting_rule_bundle_ver_id
FROM mor_ledger.t_accounting_instruction i JOIN mor_ledger.t_ledger_book b ON b.id = i.ledger_book_id
WHERE i.economic_fact_id = @fact ORDER BY i.id;
SELECT j.id journal_id, b.entity_type, b.entity_id, i.instruction_type, j.journal_sequence, j.posting_date, j.entry_type,
       SUM(IF(l.entry_side = 'DEBIT', l.amount, 0)) AS debit, SUM(IF(l.entry_side = 'CREDIT', l.amount, 0)) AS credit
FROM mor_ledger.t_accounting_instruction i JOIN mor_ledger.t_journal_entry j ON j.instruction_id = i.id
JOIN mor_ledger.t_ledger_book b ON b.id = j.ledger_book_id JOIN mor_ledger.t_journal_line l ON l.journal_entry_id = j.id
WHERE i.economic_fact_id = @fact GROUP BY j.id, b.entity_type, b.entity_id, i.instruction_type, j.journal_sequence, j.posting_date, j.entry_type;
SELECT j.id journal_id, l.line_no, l.entry_side, l.amount, ca.account_code, ca.account_name, la.owner_type, la.owner_id, la.balance_bucket
FROM mor_ledger.t_accounting_instruction i JOIN mor_ledger.t_journal_entry j ON j.instruction_id = i.id
JOIN mor_ledger.t_journal_line l ON l.journal_entry_id = j.id JOIN mor_ledger.t_ledger_account la ON la.id = l.account_id
LEFT JOIN mor_ledger.t_chart_account ca ON ca.id = la.chart_account_id
WHERE i.economic_fact_id = @fact ORDER BY j.id, l.line_no;

-- ========== reconciliation ==========
SELECT id, clearing_account_id, direction, txn_source, billing_fact_id, entity_no, source_ref_type, source_ref_id,
       channel_transaction_ref_id, reference_type, external_transaction_id, currency, expected_amount, expected_fee, status, business_time
FROM mor_reconciliation.t_platform_txn WHERE billing_fact_id = @fact;

-- ========== 补偿任务异常（全库，验收窗口内不应出现新的 FAILED） ==========
SELECT 'billing' AS db, execute_status, COUNT(*) FROM mor_billing.t_compensation_event GROUP BY execute_status
UNION ALL SELECT 'account', execute_status, COUNT(*) FROM mor_account.t_compensation_event GROUP BY execute_status
UNION ALL SELECT 'ledger', execute_status, COUNT(*) FROM mor_ledger.t_compensation_event GROUP BY execute_status
UNION ALL SELECT 'reconciliation', execute_status, COUNT(*) FROM mor_reconciliation.t_compensation_event GROUP BY execute_status;
