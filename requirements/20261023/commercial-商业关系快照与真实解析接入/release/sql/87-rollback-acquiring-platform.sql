-- 回滚（mor_acquiring）：撤销 41-acquiring-remove-platform.sql（acquiring 旧镜像已换回之后执行）。
-- 被改写的存量订单 = PARTNER + local_partner_id = PayFac Partner + 订单上下文没有 Partner 合同字段（REAL 建单的 PARTNER 订单带分成 / 服务费
-- 版本；新 Mock 订单的 local_partner_id 是 107），据此精确改回 PLATFORM。期望行数 = 41 改写的行数（盘点时 46）。
-- 列注释恢复为执行前 mysqldump（2026-10-25T11:12Z）原文。
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE t_commerce_order SET entity_source_type = 'PLATFORM', local_partner_id = NULL
 WHERE entity_source_type = 'PARTNER' AND local_partner_id = 900000000001370001
   AND order_id IN (SELECT o.order_id FROM (SELECT c.order_id FROM t_order_context c WHERE c.partner_share_version_id IS NULL) o);
SELECT ROW_COUNT() AS `orders_reverted_expect_46`;
COMMIT;
ALTER TABLE `t_commerce_order`
  MODIFY COLUMN `entity_source_type` varchar(64) DEFAULT NULL COMMENT '订单经营主体来源于平台、Client 或 Local Partner 的闭合类型。允许值：PLATFORM、CLIENT、PARTNER。';
