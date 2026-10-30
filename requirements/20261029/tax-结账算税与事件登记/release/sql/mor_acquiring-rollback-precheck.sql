-- tax 结账算税与事件登记 · mor_acquiring 回滚前置检查（只读；runbook R7 前执行）。目标库：mor_acquiring。
-- 回滚只在"迁移后还没有新订单"时可行。任一计数非 0 = 已有新单写入新列：mor_acquiring-rollback.sql 的第一条 ALTER 会因
-- finance_tax_snapshot_* 恢复 NOT NULL 失败（ERROR 1138 / 1265，不做任何改动）。此时不得强行回滚，只回退镜像不可行
-- （旧 acquiring 镜像插入订单不写 amount_status 会失败），须保留新库结构与新 acquiring 镜像，另行评审数据处理方案。
SET NAMES utf8mb4;

SELECT (SELECT COUNT(*) FROM t_order_context WHERE finance_tax_snapshot_id IS NULL OR finance_tax_snapshot_hash IS NULL) AS `new_orders_without_snapshot_0`,
       (SELECT COUNT(*) FROM (SELECT order_id FROM t_consumer_contract GROUP BY order_id HAVING COUNT(*) > 1) d) AS `orders_with_multi_contract_0`,
       (SELECT COUNT(*) FROM t_order_tax_calculation) AS `tax_calc_rows`,
       (SELECT COUNT(*) FROM t_commerce_order WHERE amount_status <> 'FINAL' OR create_time > '2026-10-29 00:00:00') AS `orders_after_release`;
-- 影响说明：回滚后 t_order_tax_calculation 随表删除（tax 中的计算、交易、税票保留）；已建但未付的新订单无法再由旧镜像正确支付。
