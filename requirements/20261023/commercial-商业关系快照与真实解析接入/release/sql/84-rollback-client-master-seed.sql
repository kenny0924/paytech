-- 回滚（mor_client_master）：撤销 21-client-master-ae001-seed.sql（83 之后执行）。按种子的固定 ID 删除，并删除这些主档在发布后由
-- Admin API 写入的 AE001 根 BCR 快照。**不可逆部分**：切 REAL 后冻结了这些 ID（affiliation_id、entity_scope_id、entity_source_relation_id、
-- 法律文件 / 卖方披露 ID、OE Party）的订单与 commercial 合同引用会悬空（跨库逻辑引用，不改写订单）；回滚前先确认是否已有 REAL 订单：
--   SELECT COUNT(*) FROM mor_acquiring.t_order_context WHERE affiliation_id BETWEEN 900000000001381000 AND 900000000001381999;
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM `t_client_entity_affiliation` WHERE `affiliation_id` BETWEEN 900000000001381101 AND 900000000001381107;
SELECT ROW_COUNT() AS `affiliation_deleted_expect_7`;
DELETE FROM `t_seller_disclosure` WHERE `disclosure_id` = 900000000001381014;
DELETE FROM `t_consumer_legal_document` WHERE `legal_document_id` BETWEEN 900000000001381011 AND 900000000001381013;
DELETE FROM `t_operating_entity_scope` WHERE `scope_id` = 900000000001381005;
DELETE FROM `t_operating_entity_application` WHERE `application_id` = 900000000001381004;
DELETE FROM `t_entity_source_relation` WHERE `source_relation_id` = 900000000001381003;
DELETE FROM `t_operating_entity` WHERE `entity_no` = 'AE001' AND `party_id` = 880000000702;
SELECT ROW_COUNT() AS `operating_entity_deleted_expect_1`;
DELETE FROM `t_business_change_version` WHERE `history_id` = 900000000001381040;
DELETE FROM `t_business_change_record` WHERE `history_id` = 900000000001381040;
DELETE FROM `t_organization_profile` WHERE `profile_id` IN (881000000701, 881000000702);
DELETE FROM `t_party_profile` WHERE `profile_id` IN (881000000701, 881000000702);
DELETE FROM `t_party` WHERE `party_id` IN (880000000701, 880000000702);
SELECT ROW_COUNT() AS `party_deleted_expect_2`;
COMMIT;
