-- 需求：Finance 期间关账（P6 结转规则）
-- 性质：本地执行脚本；同时追加到 finance-service/docs/database/sql/finance-initial-data.sql（唯一初始化 DML，阶段 4 修改）。
-- 目标库：本地 mor_finance；固定 ID 4001—4007（低于 1,000,000，属初始化保留行）。
-- 内容：STANDARD 科目表（2001）全部 REVENUE/EXPENSE 叶子科目 → CURRENT_PERIOD_RESULT（3020）/ RETAINED_EARNINGS（3021）。
-- 回滚：DELETE FROM t_close_account_rule WHERE id BETWEEN 4001 AND 4007;
INSERT INTO `t_close_account_rule`
  (`id`, `chart_id`, `source_account_id`, `current_result_account_id`, `retained_earnings_account_id`, `effective_from`, `effective_to`, `create_time`)
VALUES
  (4001, 2001, 3023, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4002, 2001, 3024, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4003, 2001, 3025, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4004, 2001, 3026, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4005, 2001, 3027, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4006, 2001, 3028, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4007, 2001, 3029, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6));
