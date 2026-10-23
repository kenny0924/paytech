-- billing 支付事实自动登记 · 合成验收数据清理【提案，全部注释】。
-- ！！执行需要用户单独授权（mor-test 写操作，root）。未授权时不得取消注释。
-- 建议（release.md 第 7.4 节）：保留合成事实作为演示数据，不执行本文件。原因：
--   1. 已过账分录按设计不可变；ledger 的期间余额（t_ledger_period_balance）、账簿序列（t_book_sequence）、AccountPosted 严格有序消费
--      版本（t_consumed_aggregate_version）与 account 的 Owner 账户累计（t_owner_account.debit_total / credit_total / net_balance /
--      balance_version）、过账流序列（t_account_posting_stream.last_sequence）都是聚合值，删除明细后它们与明细不一致，
--      需要逐项回算；回算错误会让后续真实过账在严格有序校验或试算平衡处失败。
--   2. 合成 ID 段 9999000000000001xx / 2xx 与外部交易号 SYN-BILLB-* 足以在控制台与对账中辨认。
--   3. 余额成熟任务（BalanceMaturityJob）会在 balance_available_time 后把 PENDING 转 AVAILABLE，产生新的余额移动与 AccountPosted /
--      分录；保留则需接受这些后续行，删除则需在成熟前完成。
-- 若仍决定删除：先停 billing、account、ledger、reconciliation 消费（或确认 DLQ / 分区队列为空且无进行中任务），按 backup.sh 备份，
-- 再按下面顺序执行；@fact 由 check_chain.sql 求得。下列语句只覆盖明细行，聚合值的回算语句须按当时数据另写并复核，不在本提案内。

-- SET @attempt := '999900000000000104';  -- PARTNER 场景 999900000000000204
-- SET @fact := (SELECT id FROM mor_billing.t_billing_fact WHERE source_system = 'acquiring' AND source_event_key = CONCAT('PAYMENT_ATTEMPT:', @attempt));
-- SELECT @fact;  -- 必须非空且只对应一条合成事实

-- ---- reconciliation（叶子：预期明细） ----
-- DELETE FROM mor_reconciliation.t_platform_txn WHERE billing_fact_id = @fact AND status = 'OPEN';
-- DELETE FROM mor_reconciliation.t_consumed_event WHERE event_id = CONCAT('BILLING_FACT:', @fact, ':BillingFactRecorded:1');

-- ---- ledger（分录不可变：仅在用户明确接受回算风险时） ----
-- DELETE l FROM mor_ledger.t_journal_line l JOIN mor_ledger.t_journal_entry j ON j.id = l.journal_entry_id
--   JOIN mor_ledger.t_accounting_instruction i ON i.id = j.instruction_id WHERE i.economic_fact_id = @fact;
-- DELETE j FROM mor_ledger.t_journal_entry j JOIN mor_ledger.t_accounting_instruction i ON i.id = j.instruction_id WHERE i.economic_fact_id = @fact;
-- DELETE il FROM mor_ledger.t_instruction_line il JOIN mor_ledger.t_accounting_instruction i ON i.id = il.instruction_id WHERE i.economic_fact_id = @fact;
-- DELETE FROM mor_ledger.t_accounting_instruction WHERE economic_fact_id = @fact;
-- -- 另需回算：t_ledger_period_balance（period_debit / period_credit / closing_balance / as_of_sequence）、t_book_sequence；
-- -- t_consumed_event / t_consumed_aggregate_version 的 AccountPosted 记录不能删（删了会让下一笔真实 AccountPosted 判为缺口）。
-- -- 按需创建的 t_ledger_account（清算账户 / Partner 等明细户）可保留。

-- ---- account（同上：仅在接受回算风险时） ----
-- DELETE e FROM mor_account.t_account_entry e JOIN mor_account.t_account_posting p ON p.id = e.posting_id WHERE p.fact_id = @fact;
-- DELETE FROM mor_account.t_account_posting WHERE fact_id = @fact;
-- -- t_balance_movement（成熟任务写入，source_ref 取值按 BalanceMaturityServiceImpl 当时实现核对后再写删除条件）
-- DELETE FROM mor_account.t_balance_applied_fact WHERE fact_id = @fact;
-- -- 另需回算：t_owner_account 的 debit_total / credit_total / net_balance / balance_version；t_account_posting_stream.last_sequence 不能回退
-- -- （ledger 已消费该序列）。按需创建的 Owner 账户（CLIENT_PAYABLE / PARTNER_PAYABLE 的 PENDING 桶）可保留。

-- ---- billing（事实与明细；BillingFactRecorded 已被下游消费，删除后下游已无引用时才可执行） ----
-- DELETE FROM mor_billing.t_billing_fact_line WHERE fact_id = @fact;
-- DELETE FROM mor_billing.t_billing_fact_ref WHERE fact_id = @fact;
-- DELETE FROM mor_billing.t_billing_fact_pricing WHERE fact_id = @fact;
-- DELETE FROM mor_billing.t_compensation_event WHERE event_key = CONCAT('BILLING_FACT:', @fact, ':BillingFactRecorded:1');
-- DELETE FROM mor_billing.t_billing_fact WHERE id = @fact AND origin_type = 'AUTO' AND source_event_key LIKE 'PAYMENT_ATTEMPT:99990000000000%';
-- DELETE FROM mor_billing.t_consumed_event WHERE event_id LIKE 'ORDER:99990000000000%:OrderPaymentSucceeded:%';
