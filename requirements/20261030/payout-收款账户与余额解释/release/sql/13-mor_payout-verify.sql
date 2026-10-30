-- payout S1 · mor_payout DDL 执行后核对（root 或 payout_app 只读会话，库 mor_payout）。
-- 期望：恰好 3 张表；列数 t_beneficiary_account 31（含生成列 active_key）、t_beneficiary_account_event 13、t_compensation_event 15；
--   索引（不含主键）5 / 1 / 3（2026-10-30 演练读数）；生成列 active_key = IF(status IN (REJECTED, CLOSED), id, 0)；三表均 0 行；无 FOREIGN KEY / CHECK（DATABASE_DESIGN_GUIDE 5.1）。
SELECT table_name, table_rows, table_collation FROM information_schema.tables WHERE table_schema = 'mor_payout' ORDER BY table_name;
SELECT table_name, COUNT(*) AS `columns` FROM information_schema.columns WHERE table_schema = 'mor_payout' GROUP BY table_name ORDER BY table_name;
SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS `columns`
FROM information_schema.statistics WHERE table_schema = 'mor_payout' AND index_name <> 'PRIMARY'
GROUP BY table_name, index_name, non_unique ORDER BY table_name, index_name;
SELECT column_name, generation_expression FROM information_schema.columns
WHERE table_schema = 'mor_payout' AND table_name = 't_beneficiary_account' AND extra LIKE '%GENERATED%';
SELECT COUNT(*) AS `fk_or_check` FROM information_schema.table_constraints
WHERE table_schema = 'mor_payout' AND constraint_type IN ('FOREIGN KEY', 'CHECK');
SELECT (SELECT COUNT(*) FROM t_beneficiary_account) AS `accounts`, (SELECT COUNT(*) FROM t_beneficiary_account_event) AS `events`,
       (SELECT COUNT(*) FROM t_compensation_event) AS `compensation_events`;
