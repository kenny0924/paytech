-- tax 结账算税与事件登记 · mor_acquiring 迁移后核对（只读；runbook ⑨）。目标库：mor_acquiring。
SET NAMES utf8mb4;

SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_order_tax_calculation') AS `new_table_1`,
       (SELECT COUNT(*) FROM t_commerce_order WHERE amount_status IS NULL) AS `null_amount_status_0`,
       (SELECT COUNT(*) FROM t_order_tax_calculation) AS `calc_rows_0_before_new_orders`;
SELECT amount_status, COUNT(*) AS `orders` FROM t_commerce_order GROUP BY amount_status;  -- 存量 51 行全部 FINAL（total_amount 均非空）
SELECT column_name, is_nullable, column_type FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 't_order_context'
  AND column_name IN ('finance_tax_snapshot_id', 'finance_tax_snapshot_hash', 'initial_tax_calculation_id') ORDER BY column_name;  -- 三列均 YES
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS `cols` FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 't_consumer_contract' AND index_name = 'uk_consumer_contract' GROUP BY index_name;  -- order_id,tax_calculation_id
