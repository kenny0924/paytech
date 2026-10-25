-- mor-test 专用（mor_client_master，root 手工执行）：AE001 主档种子。**需用户决定 D-1 批准后才执行**（见 evidence/data-inventory.md）。
-- 原因（盘点 F-1）：mor-test client-master 没有任何 Operating Entity / Local Partner / 准入范围 / Affiliation / 卖方披露 / 消费者法律文件，
--   AE001 只存在于 acquiring 固定 Mock 与 commercial 合同里；commercial 合同里的 OE 签约方是占位 Party 880000000702（资料 881000000702），
--   PLATFORM_SIGNATORY 是占位 Party 880000000701（资料 881000000701），ledger 的 PLATFORM_HQ 财务主体 legal_party_id 也是 880000000701。
--   没有本文件，20 / 22 两个正式文件在 mor-test 上执行后“我们的 PayFac” Partner 不会建立（AE001 缺失时 ② 不插入），切 REAL 必然失败。
-- 做法：沿用占位 Party 号，使现有 9 份 AE001 Client 合同与 DEMO-PT-001 / DEMO2-PT-101 的签约方、ledger PLATFORM_HQ 无需改写；
--   形态照阶段 5 本机种子（verification/local-e2e/10-client-master-seed.sql，候选查询通过的最小主档）：
--   AE001 × 消费者国家 US × USD × RETAIL / 5999 × locale en（mor-test 46 笔存量订单全是 US / en / USD，Client 分类全是 RETAIL / 5999）。
--   AE001 来源先按迁移前形态写 PLATFORM（application 同），随后 22-client-master-data.sql（正式数据文件原样）把它迁到 PayFac Partner
--   900000000001370001，从而在 mor-test 上执行的仍是阶段 4 / 5 验证过的迁移逻辑。
-- 合成内容（演示数据，不是真实法律事实）：两个 Party 的机构资料、AE001 的入驻申请（尽调 Case / 资格决定 ID 为合成值，compliance 中不存在）、
--   三份消费者法律文件（正文文件 ID 为合成值，Platform 文件服务中不存在；acquiring 的 Document 端口本期仍是 Mock）、卖方披露文本、
--   各 content_hash（SHA-256 of 固定说明串）。公开文本（卖方披露）在 D-1 中由用户确认或替换。
-- 资料批准时间 2026-10-13：必须不晚于现有合同中这两个 Party 最早的签署时间（2026-10-20 02:50:52，00-precheck 输出），commercial 校验
--   profile.approvedTime ≤ acceptedTime。
-- Affiliation：给 mor-test 上 7 个有 ACTIVE AE001 Client 合同的 Client（CL-F1105E、DEMO-CL-001/002、DEMO2-CL-101/102/103/901）。
--   其他 Client 没有 Affiliation，切 REAL 后建单得到 CLIENT_CONFIGURATION_NOT_READY / CHECKOUT_NOT_ELIGIBLE（见盘点 R-3）。
-- ID：Party / 资料沿用占位号；其余 9000000000013810xx（避开数据文件的 900000000001370001—900000000001379999，回滚按段删除）。
-- 顺序：20-client-master-ddl.sql 之后、22-client-master-data.sql 之前；与 22 同一窗口紧接执行（中间旧 client-master 镜像看到 PLATFORM
--   来源的 AE001 无影响：acquiring 仍是 MOCK，没有候选查询调用方）。
-- 失败即停：写入前由 relsnap_seed_assert 检查目标为空，否则 SIGNAL；全部写入在一个事务内，任一语句失败 mysql 客户端退出、事务回滚。
-- 回滚：84-rollback-client-master-seed.sql（须先执行 83 回滚数据文件）。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @t = '2026-10-13 00:00:00.000000';
SET @actor = 221546852372267008;   -- mor-test 运营管理员账号（现有 Partner 合同 create_by / acceptance_recorded_by 相同）

DROP PROCEDURE IF EXISTS `relsnap_seed_assert`;
DELIMITER //
CREATE PROCEDURE `relsnap_seed_assert`()
BEGIN
  IF (SELECT COUNT(*) FROM `information_schema`.`columns` WHERE `table_schema` = DATABASE() AND `table_name` = 't_operating_entity'
        AND `column_name` = 'acquiring_qualification') = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'run 20-client-master-ddl.sql first (acquiring_qualification missing)';
  END IF;
  IF EXISTS (SELECT 1 FROM `t_operating_entity`) OR EXISTS (SELECT 1 FROM `t_entity_source_relation`)
     OR EXISTS (SELECT 1 FROM `t_operating_entity_application`) OR EXISTS (SELECT 1 FROM `t_operating_entity_scope`)
     OR EXISTS (SELECT 1 FROM `t_local_partner`) OR EXISTS (SELECT 1 FROM `t_client_entity_affiliation`)
     OR EXISTS (SELECT 1 FROM `t_seller_disclosure`) OR EXISTS (SELECT 1 FROM `t_consumer_legal_document`) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'client-master already has OE / partner master data; re-inventory before seeding';
  END IF;
  IF EXISTS (SELECT 1 FROM `t_party` WHERE `party_id` IN (880000000701, 880000000702))
     OR EXISTS (SELECT 1 FROM `t_party_profile` WHERE `profile_id` IN (881000000701, 881000000702))
     OR EXISTS (SELECT 1 FROM `t_business_change_record` WHERE `history_id` BETWEEN 900000000001381000 AND 900000000001381999
                   OR (`record_type` = 'OPERATING_ENTITY' AND `record_id` = 'AE001')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'seed party / profile / BCR ids already used';
  END IF;
  IF (SELECT COUNT(*) FROM `t_client` WHERE `status` = 'ACTIVE' AND `client_id` IN (237610139979456513, 237575207592763393,
        237574764993028097, 237573476041465857, 237573382420406273, 237573267559391233, 237573040244891649)) <> 7 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'expected 7 ACTIVE clients with AE001 agreements';
  END IF;
END//
DELIMITER ;
CALL `relsnap_seed_assert`();

START TRANSACTION;

INSERT INTO `t_party` (`party_id`, `party_no`, `party_type`, `create_time`, `update_time`) VALUES
  (880000000702, 'PTY-OE-AE001', 'ORGANIZATION', @t, @t),
  (880000000701, 'PTY-PLATFORM-HQ', 'ORGANIZATION', @t, @t);

INSERT INTO `t_party_profile` (`profile_id`, `party_id`, `content_hash`, `create_time`, `update_time`, `draft_application_id`, `approved_time`) VALUES
  (881000000702, 880000000702, SHA2('mor-test relsnap seed 2026-10-25: party profile 881000000702 (AE001)', 256), @t, @t, NULL, @t),
  (881000000701, 880000000701, SHA2('mor-test relsnap seed 2026-10-25: party profile 881000000701 (PLATFORM_HQ)', 256), @t, @t, NULL, @t);

INSERT INTO `t_organization_profile` (`profile_id`, `legal_name`, `trade_name`, `incorporation_country`, `organization_type`,
  `incorporation_date`, `create_time`, `update_time`) VALUES
  (881000000702, 'AE001 Demo Operating Entity FZ-LLC', NULL, 'AE', 'LIMITED_COMPANY', NULL, @t, @t),
  (881000000701, 'Paytech Platform HQ Demo Ltd', NULL, 'AE', 'LIMITED_COMPANY', NULL, @t, @t);

INSERT INTO `t_operating_entity` (`entity_no`, `party_id`, `operating_country`, `status`, `acquiring_qualification`, `create_time`, `update_time`)
VALUES ('AE001', 880000000702, 'AE', 'ACTIVE', 'PAYFAC', @t, @t);

INSERT INTO `t_entity_source_relation` (`source_relation_id`, `entity_no`, `source_type`, `source_client_id`, `source_partner_id`,
  `client_source_basis`, `status`, `active_source_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`) VALUES
  (900000000001381003, 'AE001', 'PLATFORM', NULL, NULL, NULL, 'ACTIVE', 1, @t, NULL, @t, @t);

INSERT INTO `t_operating_entity_application` (`application_id`, `application_no`, `application_type`, `base_entity_change_version`,
  `entity_no`, `party_profile_id`, `source_type`, `source_client_id`, `source_partner_id`, `client_source_basis`, `status`, `active_slot`,
  `due_diligence_case_id`, `qualification_decision_id`, `qualification_decision_version`, `qualification_type`, `submitted_time`,
  `submission_hash_schema_version`, `submission_content_hash`, `approved_source_relation_id`, `create_time`, `update_time`) VALUES
  (900000000001381004, 'OEA-AE001-MORTEST-SEED', 'ONBOARDING', 1, 'AE001', 881000000702, 'PLATFORM', NULL, NULL, NULL, 'APPROVED', NULL,
   900000000001381031, 900000000001381032, 1, 'ENTITY_UNDERWRITING', @t, 1,
   SHA2('mor-test relsnap seed 2026-10-25: AE001 onboarding application', 256), 900000000001381003, @t, @t);

INSERT INTO `t_operating_entity_scope` (`scope_id`, `entity_no`, `application_id`, `consumer_country_code`, `currency`, `industry_code`,
  `mcc_code`, `status`, `active_slot`, `effective_from`, `effective_to`, `create_time`, `update_time`) VALUES
  (900000000001381005, 'AE001', 900000000001381004, 'US', 'USD', 'RETAIL', '5999', 'ACTIVE', 1, @t, NULL, @t, @t);

INSERT INTO `t_consumer_legal_document` (`legal_document_id`, `document_code`, `document_type`, `entity_no`, `country_code`, `locale`,
  `content_file_id`, `content_hash`, `status`, `effective_from`, `effective_to`, `approved_time`, `published_time`, `create_by`,
  `create_time`, `update_time`) VALUES
  (900000000001381011, 'AE001-US-EN-TERMS', 'CONSUMER_TERMS', 'AE001', 'US', 'en', 900000000001381021,
   SHA2('mor-test relsnap seed 2026-10-25: AE001 US en CONSUMER_TERMS', 256), 'ACTIVE', @t, NULL, @t, @t, @actor, @t, @t),
  (900000000001381012, 'AE001-US-EN-REFUND', 'REFUND_POLICY', 'AE001', 'US', 'en', 900000000001381022,
   SHA2('mor-test relsnap seed 2026-10-25: AE001 US en REFUND_POLICY', 256), 'ACTIVE', @t, NULL, @t, @t, @actor, @t, @t),
  (900000000001381013, 'AE001-US-EN-PRIVACY', 'PRIVACY_NOTICE', 'AE001', 'US', 'en', 900000000001381023,
   SHA2('mor-test relsnap seed 2026-10-25: AE001 US en PRIVACY_NOTICE', 256), 'ACTIVE', @t, NULL, @t, @t, @actor, @t, @t);

-- 卖方披露公开文本：D-1 由用户确认或替换（会展示给消费者并冻结进 acquiring t_order_legal.public_*）。
INSERT INTO `t_seller_disclosure` (`disclosure_id`, `entity_no`, `country_code`, `locale`, `public_legal_name`, `public_registration_number`,
  `public_tax_registration_number`, `public_address_text`, `public_support_email`, `public_support_phone`, `status`, `effective_from`,
  `effective_to`, `approved_time`, `published_time`, `create_by`, `create_time`, `update_time`) VALUES
  (900000000001381014, 'AE001', 'US', 'en', 'AE001 Demo Operating Entity FZ-LLC', 'MORTEST-REG-AE001', 'MORTEST-TRN-AE001',
   'Demo Address, Dubai, United Arab Emirates', 'support-ae001@example.invalid', NULL, 'ACTIVE', @t, NULL, @t, @t, @actor, @t, @t);

INSERT INTO `t_client_entity_affiliation` (`affiliation_id`, `client_id`, `entity_no`, `priority_no`, `status`, `active_slot`,
  `effective_from`, `effective_to`, `create_time`, `update_time`) VALUES
  (900000000001381101, 237610139979456513, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t),
  (900000000001381102, 237575207592763393, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t),
  (900000000001381103, 237574764993028097, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t),
  (900000000001381104, 237573476041465857, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t),
  (900000000001381105, 237573382420406273, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t),
  (900000000001381106, 237573267559391233, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t),
  (900000000001381107, 237573040244891649, 'AE001', 10, 'ACTIVE', 1, @t, NULL, @t, @t);

-- AE001 根 BCR V1（候选 verify 的根集合与 Admin API 的 CAS 都依赖它）；22 的迁移会推进为 V2。Affiliation 的审计根类型是
-- CLIENT_ENTITY_AFFILIATION（不是 Client 根），线上候选根集合不读它，本文件不为 Affiliation / 法律文件 / 披露补建 BCR（阶段 5 种子同样不建）。
INSERT INTO `t_business_change_record` (`history_id`, `record_type`, `record_id`, `aggregate_root_type`, `aggregate_root_id`,
  `parent_history_id`, `current_version`, `create_time`, `update_time`) VALUES
  (900000000001381040, 'OPERATING_ENTITY', 'AE001', 'OPERATING_ENTITY', 'AE001', NULL, 1, @t, @t);

COMMIT;

DROP PROCEDURE `relsnap_seed_assert`;

-- 执行后核对：
--   SELECT entity_no, party_id, acquiring_qualification, status FROM t_operating_entity;                     -- AE001 / 880000000702 / PAYFAC
--   SELECT entity_no, source_type, status FROM t_entity_source_relation;                                     -- AE001 PLATFORM（22 之后为 PARTNER）
--   SELECT COUNT(*) FROM t_client_entity_affiliation WHERE entity_no='AE001' AND status='ACTIVE';            -- 7
