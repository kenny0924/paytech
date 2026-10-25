-- 回滚（mor_commercial）：只在执行过 30-commercial-payfac-agreement-fallback.sql（SQL 兜底）时使用；内容即正式草稿文件头的回滚段。
-- 若 PayFac Partner 合同是经运营 API 创建的，不用本文件：经运营入口终止合同 / 停用方案（保留审计），见 data-inventory.md 第 5 节。
-- 须与 client-master 83 / acquiring 回滚同一窗口；已按该合同冻结的订单（t_order_legal.partner_agreement_id 等）引用会悬空（不可逆）。
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM t_business_change_record WHERE history_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_commercial_term_component WHERE term_component_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_commercial_term_price WHERE term_price_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_commercial_term_version WHERE term_version_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_commercial_term_plan WHERE term_plan_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_agreement_party WHERE agreement_party_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_agreement_document_ref WHERE agreement_document_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_agreement_operating_entity WHERE agreement_operating_entity_id BETWEEN 900000000001371000 AND 900000000001371099;
DELETE FROM t_agreement WHERE agreement_id BETWEEN 900000000001371000 AND 900000000001371099;
SELECT ROW_COUNT() AS `agreement_deleted_expect_1`;
COMMIT;
