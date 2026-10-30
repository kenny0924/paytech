-- 联合查询 · 子行键类型回滚（修订 c）：先把引用它们的模板恢复到上一版本（运营 API restoration），否则模板当前版本的 joint 引用缺失的键类型。
-- 只删除本修订的 3 行（按编码，兼容 SQL 与 K2 两种建法）。重复执行为空操作。
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM t_query_key_type WHERE key_type_code IN ('ACCOUNT_ENTRY_ID', 'JOURNAL_LINE_ID', 'STATEMENT_LINE_ID');
COMMIT;
