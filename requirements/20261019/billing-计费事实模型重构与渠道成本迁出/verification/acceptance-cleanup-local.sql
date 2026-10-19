-- 阶段 5 本机验收清理：只删除 local_acceptance.py 本次运行写入的行（ledger / account / billing / channel-connector 四库），
-- 不碰 mor_platform_v2e2e（验收不写 Platform）。
-- 由 `python3 -I local_acceptance.py --cleanup` 读取最新运行记录后，在每段前拼接：
--   SET @book = <ledgerBookId>; SET @calendar = <fiscalCalendarId>; SET @entity_no = '<entityNo>'; SET @plan_code = '<planCode>';
-- 并把 /*FACT_IDS*/ 替换为本次 billing 事实 ID 列表（逗号分隔）。各段分别以 ledger_app / account_app / billing_app /
-- channel_connector_app 连接各自的库执行。库名不写在 SQL 内，由 e2e_http 按 E2E_TARGET 选择：local 为 *_v2e2e，test（阶段 7）为 mor_*。
-- 顺序按逻辑依赖由子到父（库内无物理外键）。变量为 NULL 时对应语句不删除任何行。回滚不适用（只删除验收数据）。
-- @channel_seed 段只在 `--cleanup --drop-seed` 时执行：删除任务卡 E 第 3 步以 root 执行的
-- 50-channel-connector-payermax-mock.sql 种子（语句取自该文件头部的回滚说明），使 mor_channel_connector_v2e2e 回到建表后的 0 行。

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
-- 人工调整（验收不提交，正常无行；防御性清理）
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
-- AccountPosted 发布任务、BillingFactRecorded 消费幂等记录（聚合类型 BILLING_FACT）
DELETE FROM t_compensation_event WHERE event_key LIKE CONCAT('ACCOUNT_POSTING:OE.', @entity_no, '.%');
DELETE FROM t_consumed_event WHERE aggregate_type = 'BILLING_FACT' AND aggregate_id IN (/*FACT_IDS*/);
DELETE FROM t_consumed_aggregate_version WHERE aggregate_type = 'BILLING_FACT' AND aggregate_id IN (/*FACT_IDS*/);
-- 流水、批次、桶移动、事实处理记录、Owner 账户、入账流
DELETE e FROM t_account_entry e JOIN t_account_posting p ON p.id = e.posting_id
  WHERE p.entity_type = 'OPERATING_ENTITY' AND p.entity_id = @entity_no;
DELETE FROM t_account_posting WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;
DELETE FROM t_balance_movement WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;
DELETE FROM t_balance_applied_fact WHERE fact_id IN (/*FACT_IDS*/) OR entity_no = @entity_no;
DELETE FROM t_owner_account WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;
DELETE FROM t_account_posting_stream WHERE entity_type = 'OPERATING_ENTITY' AND entity_id = @entity_no;

-- @billing
-- BillingFactRecorded 发布任务（event_key = BILLING_FACT:{factId}:BillingFactRecorded:{factVersion}）、行链接、明细行、引用、定价、事实头
DELETE FROM t_compensation_event WHERE event_key REGEXP CONCAT('^BILLING_FACT:(', REPLACE('/*FACT_IDS*/', ',', '|'), '):');
DELETE lk FROM t_billing_fact_line_link lk JOIN t_billing_fact_line l ON l.id IN (lk.source_line_id, lk.target_line_id)
  WHERE l.fact_id IN (/*FACT_IDS*/);
DELETE FROM t_billing_fact_line WHERE fact_id IN (/*FACT_IDS*/);
DELETE FROM t_billing_fact_ref WHERE fact_id IN (/*FACT_IDS*/);
DELETE FROM t_billing_fact_pricing WHERE fact_id IN (/*FACT_IDS*/);
DELETE FROM t_billing_fact WHERE id IN (/*FACT_IDS*/) AND entity_no = @entity_no;

-- @channel
-- 渠道成本：组成、范围、版本、方案（只删本次方案代码；会话变量与列的排序规则不同，按二进制比较先取方案主键）
SET @plan_id = (SELECT id FROM t_channel_cost_plan WHERE plan_code = BINARY @plan_code);
DELETE c FROM t_channel_cost_component c JOIN t_channel_cost_scope s ON s.id = c.scope_id
  JOIN t_channel_cost_plan_ver v ON v.id = s.cost_ver_id WHERE v.plan_id = @plan_id;
DELETE s FROM t_channel_cost_scope s JOIN t_channel_cost_plan_ver v ON v.id = s.cost_ver_id WHERE v.plan_id = @plan_id;
DELETE FROM t_channel_cost_plan_ver WHERE plan_id = @plan_id;
DELETE FROM t_channel_cost_plan WHERE id = @plan_id;

-- @channel_seed
DELETE FROM t_mid_capability_detail WHERE mid_capability_detail_id BETWEEN 9500000000000401 AND 9500000000000405;
DELETE FROM t_mid_capability WHERE mid_capability_id = 9500000000000301;
DELETE FROM t_channel_mid WHERE mid_id = 9500000000000201;
DELETE FROM t_channel_account WHERE channel_account_id = 9500000000000101;
DELETE FROM t_payment_channel WHERE channel_id = 9500000000000001;
