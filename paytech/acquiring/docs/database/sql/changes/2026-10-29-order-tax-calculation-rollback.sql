-- 回退 2026-10-29-order-tax-calculation.sql。目标库：acquiring（mor_acquiring）。
-- 前置条件：acquiring 已回退到不写新列 / 新表的镜像；没有新订单写入过新列。新订单的 finance_tax_snapshot_* 为 NULL，
--   且可能每单多份合同，恢复 NOT NULL 与 uk_consumer_contract(order_id) 会被约束拒绝：第一条 ALTER 即失败、不做任何改动。
--   出现该失败说明新订单已存在，不得强行回退，需另行评审数据处理方案。
-- 回退前核对（均应为 0）：
--   SELECT COUNT(*) FROM t_order_context WHERE finance_tax_snapshot_id IS NULL OR finance_tax_snapshot_hash IS NULL;
--   SELECT COUNT(*) FROM (SELECT order_id FROM t_consumer_contract GROUP BY order_id HAVING COUNT(*) > 1) d;
-- 回退后 t_order_tax_calculation 中的计算记录随表删除（tax 中的计算保留，不受影响）。
SET NAMES utf8mb4;

ALTER TABLE `t_order_context`
  DROP COLUMN `initial_tax_calculation_id`,
  MODIFY COLUMN `finance_tax_snapshot_id` BIGINT NOT NULL COMMENT 'Finance 对订单税务计算结果的不可变快照标识。',
  MODIFY COLUMN `finance_tax_snapshot_hash` CHAR(64) NOT NULL COMMENT 'Finance 税务快照 calculation_hash 的精确副本，按 CODE-MOR:FINANCE:TAX_CALCULATION:1 对 Header、全部 Evidence 和 Tax Line 生成 SHA-256 小写 64 位十六进制，用于校验按标识返回的不可变税务结果。';

ALTER TABLE `t_consumer_contract`
  DROP INDEX `uk_consumer_contract`,
  ADD UNIQUE KEY `uk_consumer_contract` (`order_id`) COMMENT '唯一索引：order_id',
  DROP COLUMN `tax_calculation_id`,
  COMMENT = '呈现给消费者的不可变合同文件事实；文件标识、内容哈希、Locale 与呈现时间共同封存。';

ALTER TABLE `t_commerce_order`
  DROP COLUMN `amount_status`,
  MODIFY COLUMN `tax_amount` DECIMAL(24,8) NULL COMMENT '订单创建时冻结的消费者实收税额汇总金额。',
  MODIFY COLUMN `rounding_amount` DECIMAL(24,8) NULL COMMENT '订单金额计算为保持币种精度而冻结的舍入差额。',
  MODIFY COLUMN `total_amount` DECIMAL(24,8) NULL COMMENT '消费者最终应付的订单冻结总金额。',
  COMMENT = '基础创建时固定商品净额/币种/期限；支付准备选择提交后固定OE，Context建立时一次补齐税额和最终金额；不延长到期或跨OE恢复。';

DROP TABLE `t_order_tax_calculation`;
