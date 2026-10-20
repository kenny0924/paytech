-- mor-test：清理 2026-10-20 验收写入的 AE001 演示数据（运行记录 chart-acceptance-20261020032732.json），为三级科目树重建做准备。
-- 只删除本次运行写入的行；各段分别连接 mor_ledger / mor_account / mor_billing，以 root 执行，每段一个事务。
-- 用户 2026-10-20「按常见财务系统设计成3级，清理数据后重建」。执行前整库备份。

-- @ledger
START TRANSACTION;
SET @book = 238080764435066880; SET @calendar = 238080746789629952; SET @demo_chart_account = 238080716917796864;
DELETE FROM t_consumed_event WHERE aggregate_type = 'ACCOUNT_POSTING' AND aggregate_id LIKE 'OE.AE001.%';
DELETE FROM t_consumed_aggregate_version WHERE aggregate_type = 'ACCOUNT_POSTING' AND aggregate_id LIKE 'OE.AE001.%';
DELETE jl FROM t_journal_line jl JOIN t_journal_entry je ON je.id = jl.journal_entry_id WHERE je.ledger_book_id = @book;
DELETE FROM t_journal_entry WHERE ledger_book_id = @book;
DELETE il FROM t_instruction_line il JOIN t_accounting_instruction ai ON ai.id = il.instruction_id WHERE ai.ledger_book_id = @book;
DELETE FROM t_accounting_instruction WHERE ledger_book_id = @book;
DELETE FROM t_ledger_period_balance WHERE ledger_book_id = @book;
DELETE FROM t_account_no_sequence WHERE ledger_book_id = @book;
DELETE FROM t_ledger_account WHERE ledger_book_id = @book;
DELETE FROM t_book_sequence WHERE ledger_book_id = @book;
DELETE FROM t_ledger_book WHERE id = @book;
DELETE FROM t_accounting_period WHERE calendar_id = @calendar;
DELETE FROM t_fiscal_calendar WHERE id = @calendar;
-- 验收新增的演示科目 CLIENT_RESERVE_PAYABLE（已停用，无账户）及其 4 条变更记录
DELETE FROM t_chart_account_change WHERE chart_account_id = @demo_chart_account;
DELETE FROM t_chart_account WHERE id = @demo_chart_account AND account_code = 'CLIENT_RESERVE_PAYABLE';
COMMIT;

-- @account
START TRANSACTION;
DELETE FROM t_consumed_event WHERE aggregate_type = 'BILLING_FACT' AND aggregate_id IN (238080801055563776,238080872018993152,238080945616445440,238080998888300544,238081075757309952,238081128672649216,238081186541461504,238081232800440320);
DELETE FROM t_consumed_aggregate_version WHERE aggregate_type = 'BILLING_FACT' AND aggregate_id IN (238080801055563776,238080872018993152,238080945616445440,238080998888300544,238081075757309952,238081128672649216,238081186541461504,238081232800440320);
DELETE e FROM t_account_entry e JOIN t_account_posting p ON p.id = e.posting_id WHERE p.entity_type = 'OPERATING_ENTITY' AND p.entity_id = 'AE001';
DELETE FROM t_account_posting WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = 'AE001';
DELETE FROM t_balance_movement WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = 'AE001';
DELETE FROM t_balance_applied_fact WHERE fact_id IN (238080801055563776,238080872018993152,238080945616445440,238080998888300544,238081075757309952,238081128672649216,238081186541461504,238081232800440320);
DELETE FROM t_owner_account WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = 'AE001';
DELETE FROM t_owner_account_no_sequence WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = 'AE001';
DELETE FROM t_account_posting_stream WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = 'AE001';
COMMIT;

-- @billing
START TRANSACTION;
DELETE FROM t_billing_fact_line WHERE fact_id IN (238080801055563776,238080872018993152,238080945616445440,238080998888300544,238081075757309952,238081128672649216,238081186541461504,238081232800440320);
DELETE FROM t_billing_fact WHERE id IN (238080801055563776,238080872018993152,238080945616445440,238080998888300544,238081075757309952,238081128672649216,238081186541461504,238081232800440320) AND entity_no = 'AE001';
COMMIT;
