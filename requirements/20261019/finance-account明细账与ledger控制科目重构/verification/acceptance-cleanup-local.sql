-- 阶段 5 本机验收清理：只删除 local_acceptance.py 本次运行写入的行（mor_ledger_v2e2e / mor_account_v2e2e / mor_billing_v2e2e），
-- 不碰 mor_platform_v2e2e（验收不写 Platform；块 99 注册与激活属任务卡授权的 SQL，不在本文件回滚）。
-- 由 `python3 -I local_acceptance.py --cleanup` 读取最新运行记录后，在每段前拼接：
--   SET @book = <ledgerBookId>; SET @calendar = <fiscalCalendarId>; SET @entity_no = '<entityNo>';
-- 并把 /*FACT_IDS*/ 替换为本次 billing 事实 ID 列表（逗号分隔）。各段分别以 ledger_app / account_app / billing_app 连接各自的库执行。
-- 库名不写在 SQL 内，由 e2e_http 按 E2E_TARGET 选择：local 为 *_v2e2e，test（阶段 7）为 mor_ledger / mor_account / mor_billing。
-- 顺序按逻辑依赖由子到父（库内无物理外键）。变量为 NULL 时对应语句不删除任何行。回滚不适用（只删除验收数据）。

-- @ledger
-- 过账与关账产生的补偿事件（来源为本账簿的分录、指令、人工调整）
DELETE FROM t_compensation_event
 WHERE source_id IN (SELECT id FROM t_journal_entry WHERE ledger_book_id = @book)
    OR source_id IN (SELECT id FROM t_accounting_instruction WHERE ledger_book_id = @book)
    OR source_id IN (SELECT id FROM t_manual_adjustment WHERE ledger_book_id = @book);
-- AccountPosted 消费幂等记录（本次 OE 的过账流）
DELETE FROM t_consumed_event WHERE aggregate_type = 'ACCOUNT_POSTING' AND aggregate_id LIKE CONCAT('OE.', @entity_no, '.%');
DELETE FROM t_consumed_aggregate_version WHERE aggregate_type = 'ACCOUNT_POSTING' AND aggregate_id LIKE CONCAT('OE.', @entity_no, '.%');
-- 分录行、分录、指令行、指令
DELETE jl FROM t_journal_line jl JOIN t_journal_entry je ON je.id = jl.journal_entry_id WHERE je.ledger_book_id = @book;
DELETE FROM t_journal_entry WHERE ledger_book_id = @book;
DELETE il FROM t_instruction_line il JOIN t_accounting_instruction ai ON ai.id = il.instruction_id WHERE ai.ledger_book_id = @book;
DELETE FROM t_accounting_instruction WHERE ledger_book_id = @book;
-- 人工调整（验收只做被拒绝的提交，正常无行；防御性清理）
DELETE aa FROM t_adjustment_approval aa JOIN t_manual_adjustment ma ON ma.id = aa.adjustment_id WHERE ma.ledger_book_id = @book;
DELETE ml FROM t_manual_adjustment_line ml JOIN t_manual_adjustment ma ON ma.id = ml.adjustment_id WHERE ma.ledger_book_id = @book;
DELETE FROM t_manual_adjustment WHERE ledger_book_id = @book;
-- 关账流程（S5）：审批、步骤、账簿证据、流程
DELETE ca FROM t_close_approval ca JOIN t_period_close pc ON pc.id = ca.period_close_id
  JOIN t_accounting_period ap ON ap.id = pc.period_id WHERE ap.calendar_id = @calendar;
DELETE cs FROM t_close_step cs JOIN t_period_close pc ON pc.id = cs.period_close_id
  JOIN t_accounting_period ap ON ap.id = pc.period_id WHERE ap.calendar_id = @calendar;
DELETE FROM t_period_close_book WHERE ledger_book_id = @book;
DELETE pc FROM t_period_close pc JOIN t_accounting_period ap ON ap.id = pc.period_id WHERE ap.calendar_id = @calendar;
-- 试算（关账步骤生成）：异常、行、运行；期间余额
DELETE te FROM t_trial_balance_exception te JOIN t_trial_balance_run tr ON tr.id = te.run_id WHERE tr.ledger_book_id = @book;
DELETE tl FROM t_trial_balance_line tl JOIN t_trial_balance_run tr ON tr.id = tl.run_id WHERE tr.ledger_book_id = @book;
DELETE FROM t_ledger_period_balance WHERE ledger_book_id = @book;
DELETE FROM t_trial_balance_run WHERE ledger_book_id = @book;
-- 账户、分录序列、账簿、期间、日历
DELETE FROM t_ledger_account WHERE ledger_book_id = @book;
DELETE FROM t_book_sequence WHERE ledger_book_id = @book;
DELETE FROM t_ledger_book WHERE id = @book;
DELETE FROM t_accounting_period WHERE calendar_id = @calendar;
DELETE FROM t_fiscal_calendar WHERE id = @calendar;

-- @account
-- AccountPosted 发布任务、事实消费幂等记录
DELETE FROM t_compensation_event WHERE event_key LIKE CONCAT('ACCOUNT_POSTING:OE.', @entity_no, '.%');
DELETE FROM t_consumed_event WHERE aggregate_type = 'ECONOMIC_FACT' AND aggregate_id IN (/*FACT_IDS*/);
DELETE FROM t_consumed_aggregate_version WHERE aggregate_type = 'ECONOMIC_FACT' AND aggregate_id IN (/*FACT_IDS*/);
-- 流水、批次、桶移动、事实处理记录、Owner 账户、入账流
DELETE e FROM t_account_entry e JOIN t_account_posting p ON p.id = e.posting_id
  WHERE p.entity_type = 'OPERATING_ENTITY' AND p.entity_id = @entity_no;
DELETE FROM t_account_posting WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;
DELETE FROM t_balance_movement WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;
DELETE FROM t_balance_applied_fact WHERE fact_id IN (/*FACT_IDS*/) OR entity_no = @entity_no;
DELETE FROM t_owner_account WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;
DELETE FROM t_account_posting_stream WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;

-- @billing
-- EconomicFactRecorded 发布任务、事实行关联、事实行、事实
DELETE FROM t_compensation_event WHERE event_key REGEXP CONCAT('^ECONOMIC_FACT:(', REPLACE('/*FACT_IDS*/', ',', '|'), '):');
DELETE lk FROM t_economic_fact_line_link lk JOIN t_economic_fact_line l ON l.id IN (lk.source_component_id, lk.target_component_id)
  WHERE l.transaction_economics_id IN (/*FACT_IDS*/);
DELETE FROM t_economic_fact_line WHERE transaction_economics_id IN (/*FACT_IDS*/);
DELETE FROM t_economic_fact WHERE id IN (/*FACT_IDS*/) AND entity_no = @entity_no;
