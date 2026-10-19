-- 阶段 5 本机验收清理：只删除 local_acceptance.py 本次运行创建的行（mor_ledger_v2e2e / mor_account_v2e2e），不碰 mor_platform_v2e2e。
-- 由 `python3 -I local_acceptance.py --cleanup` 读取最新运行记录后在前面拼接：
--   SET @book = <ledgerBookId>; SET @calendar = <fiscalCalendarId>; SET @finance_entity = <financeEntityId>; SET @entity_no = '<entityNo>';
-- 先执行 ledger 段（以 ledger_app 连接 mor_ledger_v2e2e），再执行「@account」标记后的 account 段（以 account_app 连接 mor_account_v2e2e）。
-- 顺序按逻辑依赖由子到父（库内无物理外键）。变量为 NULL 时对应语句不删除任何行。
-- 手工执行时须先设置上述四个变量；回滚不适用（只删除验收数据）。

-- ledger：过账产生的补偿事件（来源为本账簿的分录、指令或人工调整）
DELETE FROM t_compensation_event
 WHERE source_id IN (SELECT id FROM t_journal_entry WHERE ledger_book_id = @book)
    OR source_id IN (SELECT id FROM t_accounting_instruction WHERE ledger_book_id = @book)
    OR source_id IN (SELECT id FROM t_manual_adjustment WHERE ledger_book_id = @book);

-- ledger：分录行、分录
DELETE jl FROM t_journal_line jl JOIN t_journal_entry je ON je.id = jl.journal_entry_id WHERE je.ledger_book_id = @book;
DELETE FROM t_journal_entry WHERE ledger_book_id = @book;

-- ledger：指令行、指令
DELETE il FROM t_instruction_line il JOIN t_accounting_instruction ai ON ai.id = il.instruction_id WHERE ai.ledger_book_id = @book;
DELETE FROM t_accounting_instruction WHERE ledger_book_id = @book;

-- ledger：人工调整审批、调整行、调整
DELETE aa FROM t_adjustment_approval aa JOIN t_manual_adjustment ma ON ma.id = aa.adjustment_id WHERE ma.ledger_book_id = @book;
DELETE ml FROM t_manual_adjustment_line ml JOIN t_manual_adjustment ma ON ma.id = ml.adjustment_id WHERE ma.ledger_book_id = @book;
DELETE FROM t_manual_adjustment WHERE ledger_book_id = @book;

-- ledger：期间余额、账户、分录序列、账簿
DELETE FROM t_ledger_period_balance WHERE ledger_book_id = @book;
DELETE FROM t_ledger_account WHERE ledger_book_id = @book;
DELETE FROM t_book_sequence WHERE ledger_book_id = @book;
DELETE FROM t_ledger_book WHERE id = @book;

-- ledger：期间、日历、财务主体
DELETE FROM t_accounting_period WHERE calendar_id = @calendar;
DELETE FROM t_fiscal_calendar WHERE id = @calendar;
DELETE FROM t_finance_entity WHERE id = @finance_entity;

-- @account
-- account：本次 OE 的余额投影（验收只读余额，正常情况下无行；防御性清理）
DELETE FROM t_balance_applied_entry WHERE balance_id IN (
  SELECT id FROM t_account_balance WHERE book_entity_type = 'OPERATING_ENTITY' AND book_entity_id = @entity_no);
DELETE FROM t_balance_movement WHERE book_entity_type = 'OPERATING_ENTITY' AND book_entity_id = @entity_no;
DELETE FROM t_balance_discrepancy WHERE ledger_book_id = @book OR (book_entity_type = 'OPERATING_ENTITY' AND book_entity_id = @entity_no);
DELETE FROM t_account_balance WHERE book_entity_type = 'OPERATING_ENTITY' AND book_entity_id = @entity_no;
