-- 发布副本：paytech/acquiring/docs/database/sql/changes/2026-10-24-remove-platform-entity-source.sql 原文，只在开头加 mor-test 变量。
-- mor-test 值：PayFac Partner = 22-client-master-data.sql 固定的 900000000001370001（盘点：46 条 PLATFORM 订单全是 AE001、local_partner_id 为空）。
SET @payfac_local_partner_id = 900000000001370001;
-- 记录改写时刻（回滚 87 / 89 以它为界区分被改写的存量行与之后新建的行）。
SELECT UTC_TIMESTAMP(6) AS `relsnap_rewrite_time`;
-- 删除经营主体来源 PLATFORM（全系统只保留 CLIENT / PARTNER，不保留历史）：订单来源 PLATFORM 改写为 PARTNER，并指向
-- “我们的 PayFac” Local Partner；同时更新 t_commerce_order.entity_source_type 注释。
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/requirement.md 第 7.4 节、AC-13；technique.md 第 3.4.1 节（acquiring DML）。
-- 目标库：acquiring（mor_acquiring）。
-- 预检：
--   SELECT COUNT(*) FROM t_commerce_order WHERE entity_source_type = 'PLATFORM';
--   不为 0 时，先在 client-master 执行 2026-10-24-relationship-snapshot-data.sql，取得“我们的 PayFac” Local Partner ID
--   （该文件固定为 900000000001370001），在本文件执行前设置 SET @payfac_local_partner_id = <该 ID>;
--   未设置时 UPDATE 不生效（PARTNER 来源订单必须带 local_partner_id）。
-- 执行顺序：与 client-master / commercial / risk-decision 的 PLATFORM 删除同批；先执行本文件，再部署不认 PLATFORM 的新 acquiring 镜像。
-- 已知影响（用户 2026-10-24 决定不保留历史）：改写后的存量订单为 PARTNER 来源，但 t_order_context 的 partner_share_version_id /
--   platform_charge_version_id / platform_service_party_id 与 t_order_legal 的 partner_agreement_id /
--   partner_agreement_operating_entity_id 仍为空（这些订单创建时没有 Partner 合同），与“PARTNER 三项成组非空”的表注释不一致；
--   这些订单已完成支付准备，Intent 创建只读冻结字段，不会再按来源重新校验；已发布的 OrderPaymentSucceeded 不重发。
-- 执行后核对：
--   SELECT COUNT(*) FROM t_commerce_order WHERE entity_source_type = 'PLATFORM';                              -- 0
--   SELECT COUNT(*) FROM t_commerce_order WHERE entity_source_type = 'PARTNER' AND local_partner_id IS NULL;   -- 0
-- 回退（须同时回退 acquiring 镜像；改写过的行无法与原本就是 PARTNER 的行区分，按 local_partner_id 回退）：
--   UPDATE t_commerce_order SET entity_source_type = 'PLATFORM', local_partner_id = NULL
--    WHERE entity_source_type = 'PARTNER' AND local_partner_id = <@payfac_local_partner_id>
--      AND order_id IN (SELECT o.order_id FROM (SELECT c.order_id FROM t_order_context c WHERE c.partner_share_version_id IS NULL) o);
--   ALTER TABLE t_commerce_order MODIFY COLUMN `entity_source_type` VARCHAR(64) NULL
--     COMMENT '订单经营主体来源于平台、Client 或 Local Partner 的闭合类型。允许值：PLATFORM、CLIENT、PARTNER。';
SET NAMES utf8mb4;

START TRANSACTION;

UPDATE t_commerce_order
SET entity_source_type = 'PARTNER', local_partner_id = @payfac_local_partner_id
WHERE entity_source_type = 'PLATFORM' AND @payfac_local_partner_id IS NOT NULL;

COMMIT;

ALTER TABLE t_commerce_order MODIFY COLUMN `entity_source_type` VARCHAR(64) NULL
  COMMENT '订单经营主体来源于 Client 或 Local Partner 的闭合类型；平台自有 OE 经平台 PayFac Partner 以 PARTNER 来源提供。允许值：CLIENT、PARTNER。';
