-- 2026-10-27 全球合规入驻与资格切片 1（client-master，在途入驻数据清理；发布决策 P-8）。
-- 需求：requirements/20261027/client-master-全球合规入驻与资格切片1/release/runbook.md 第 1 节 P-8、第 3 节 ⑤。
-- 目标库：mor_client_master（mor-test）、mor_client_master_v2e2e（本地 e2e）。由开发者审核后用具 DML 权限的账号手工执行，应用不会执行本文件。
--
-- 清理口径（P-8）：只删除在途入驻，即 status = 'PENDING' 的 Client 及只属于它们的数据：
--   申请、申请材料、申请声明、业务画像与分类、CLIENT 角色、Client 与 Partner 关系、存卡设置、Client BCR 记录与版本、
--   申请下的主体关系，以及只被这些 Client / 关系引用的 Party（资料、个人 / 机构资料、法律标识与身份键、地址、联系方式、扩展属性），
--   这些申请尽调 Case 的消费记录（t_consumed_event / t_consumed_aggregate_version），和指向它们的补偿任务
--   （CLIENT_REVIEW_REGISTRATION 按 submission、CLIENT_REVIEW_WITHDRAWAL 按申请、CLIENT_REVIEW_CLASSIFICATION 按 Case、
--   CLIENT_TENANT_PROJECTION 按 BCR 版本）。
--   ACTIVE（及 SUSPENDED / TERMINATED）Client 及其全部数据、Operating Entity、Local Partner、Partner 角色、实体范围、来源关系、
--   卖方披露与消费者法律文件不动；仍被保留数据引用的 Party 不删。
-- 已知残留（不影响运行）：client-master 只记录申请当前一轮的尽调 Case；更早轮次（退回后重提前）的 Case 的消费记录无法在本库识别，
--   保留。这些 Case 由 compliance 清理删除，Case ID 不复用。
--
-- 前置：已执行 2026-10-27-onboarding-global.sql（本文件引用其新表与新列）；client-master 服务已停止；已 mysqldump 备份整库。
-- 执行：mysql 客户端整文件执行。第 0 段预检只读；任一【停止条件】不满足时由 cm_cleanup_assert SIGNAL 中止，此时尚未写入。
--       第 2 段在一个事务内删除；COMMIT 前由 cm_cleanup_assert('POST') 断言无残留引用、保留数据计数不变，否则 SIGNAL 中止，
--       mysql 客户端退出时未提交事务回滚。存储过程只在事务外创建 / 删除（DDL 隐式提交）。可重跑：已清理的库再执行时删除集合为空。
-- 回滚：从执行前备份恢复（删除不可逆）。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ===== 0. 预检（只读）=====
-- 停止条件检查过程（事务外创建）；先确认 DDL 已执行。
DROP PROCEDURE IF EXISTS `cm_cleanup_assert`;
DELIMITER //
CREATE PROCEDURE `cm_cleanup_assert`(IN stage VARCHAR(8))
BEGIN
  IF stage = 'DDL' THEN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                    AND table_name = 't_client_application' AND column_name = 'previous_case_id') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: run 2026-10-27-onboarding-global.sql (DDL) first';
    END IF;
  ELSEIF stage = 'PRE' THEN
    IF EXISTS (SELECT 1 FROM `t_compensation_event` WHERE `execute_status` = 'PROCESSING') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: compensation events are PROCESSING; stop client-master-service first';
    END IF;
    IF EXISTS (SELECT 1 FROM `t_client` c JOIN `tmp_cm_client` t ON t.`client_id` = c.`client_id` WHERE c.`activation_time` IS NOT NULL)
       OR EXISTS (SELECT 1 FROM `t_client_application` a JOIN `tmp_cm_app` t ON t.`application_id` = a.`application_id`
                   WHERE a.`status` = 'APPROVED') THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a PENDING client was activated or has an APPROVED application';
    END IF;
    IF EXISTS (SELECT 1 FROM `t_client_entity_affiliation` a JOIN `tmp_cm_client` t ON t.`client_id` = a.`client_id`) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a PENDING client has Operating Entity affiliations';
    END IF;
    IF EXISTS (SELECT 1 FROM `t_entity_source_relation` s JOIN `tmp_cm_client` t ON t.`client_id` = s.`source_client_id`) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a PENDING client is the source of an Operating Entity';
    END IF;
    IF EXISTS (SELECT 1 FROM `t_operating_entity_application` o JOIN `tmp_cm_client` t ON t.`client_id` = o.`source_client_id`) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a PENDING client is the source of an Operating Entity application';
    END IF;
    IF EXISTS (SELECT 1 FROM `tmp_cm_client` c JOIN `tmp_cm_kept_party` k ON k.`party_id` = c.`party_id`) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a PENDING client party is still used by kept data';
    END IF;
    IF EXISTS (SELECT 1 FROM `t_party_profile` f JOIN `tmp_cm_kept_party` k ON k.`party_id` = f.`party_id`
                WHERE f.`draft_application_id` IN (SELECT `application_id` FROM `tmp_cm_app`)) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a kept party profile is a draft of a PENDING application';
    END IF;
  ELSE
    IF EXISTS (SELECT 1 FROM `t_client` WHERE `status` = 'PENDING')
       OR EXISTS (SELECT 1 FROM `t_client_application` a WHERE NOT EXISTS (SELECT 1 FROM `t_client` c WHERE c.`client_id` = a.`client_id`))
       OR EXISTS (SELECT 1 FROM `t_client_business_profile` b WHERE NOT EXISTS (SELECT 1 FROM `t_client` c WHERE c.`client_id` = b.`client_id`))
       OR EXISTS (SELECT 1 FROM `t_party_role` r WHERE r.`role_code` = 'CLIENT'
                   AND NOT EXISTS (SELECT 1 FROM `t_client` c WHERE CAST(c.`client_id` AS CHAR) = r.`subject_id`))
       OR EXISTS (SELECT 1 FROM `t_application_document` d WHERE d.`client_application_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `t_client_application` a WHERE a.`application_id` = d.`client_application_id`))
       OR EXISTS (SELECT 1 FROM `t_party_relation` r WHERE r.`client_application_id` IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM `t_client_application` a WHERE a.`application_id` = r.`client_application_id`))
       OR EXISTS (SELECT 1 FROM `t_party_profile` f WHERE NOT EXISTS (SELECT 1 FROM `t_party` p WHERE p.`party_id` = f.`party_id`))
       OR EXISTS (SELECT 1 FROM `t_party_profile_identifier` i WHERE NOT EXISTS (SELECT 1 FROM `t_party_profile` f WHERE f.`profile_id` = i.`profile_id`))
       OR EXISTS (SELECT 1 FROM `t_party_address` i WHERE NOT EXISTS (SELECT 1 FROM `t_party_profile` f WHERE f.`profile_id` = i.`profile_id`))
       OR EXISTS (SELECT 1 FROM `t_party_contact` i WHERE NOT EXISTS (SELECT 1 FROM `t_party_profile` f WHERE f.`profile_id` = i.`profile_id`))
       OR EXISTS (SELECT 1 FROM `t_party_identity_key` k WHERE NOT EXISTS (SELECT 1 FROM `t_party` p WHERE p.`party_id` = k.`party_id`)) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: dangling rows after cleanup; transaction not committed';
    END IF;
    IF (SELECT COUNT(*) FROM `t_client`) <> @cm_kept_clients
       OR (SELECT COUNT(*) FROM `t_operating_entity`) <> @cm_oe OR (SELECT COUNT(*) FROM `t_local_partner`) <> @cm_lp
       OR (SELECT COUNT(*) FROM `t_partner_role`) <> @cm_partner_role OR (SELECT COUNT(*) FROM `t_operating_entity_scope`) <> @cm_oe_scope
       OR (SELECT COUNT(*) FROM `t_entity_source_relation`) <> @cm_source_rel OR (SELECT COUNT(*) FROM `t_seller_disclosure`) <> @cm_disclosure
       OR (SELECT COUNT(*) FROM `t_client_entity_affiliation`) <> @cm_affiliation THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: kept data counts changed; transaction not committed';
    END IF;
  END IF;
END //
DELIMITER ;
CALL `cm_cleanup_assert`('DDL');

-- ===== 1. 删除集合（会话临时表，不隐式提交）与预检 =====
DROP TEMPORARY TABLE IF EXISTS `tmp_cm_client`, `tmp_cm_app`, `tmp_cm_case`, `tmp_cm_relation`, `tmp_cm_party`, `tmp_cm_profile`,
  `tmp_cm_bcr`, `tmp_cm_bcv`, `tmp_cm_kept_party`;
CREATE TEMPORARY TABLE `tmp_cm_client` (`client_id` BIGINT NOT NULL PRIMARY KEY, `party_id` BIGINT NOT NULL,
  `client_key` VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL, KEY (`party_id`), KEY (`client_key`));
CREATE TEMPORARY TABLE `tmp_cm_app` (`application_id` BIGINT NOT NULL PRIMARY KEY,
  `app_key` VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL, `submission_key` VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NULL,
  KEY (`app_key`), KEY (`submission_key`));
CREATE TEMPORARY TABLE `tmp_cm_case` (`case_key` VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cm_relation` (`relation_id` BIGINT NOT NULL PRIMARY KEY, `related_party_id` BIGINT NOT NULL);
CREATE TEMPORARY TABLE `tmp_cm_party` (`party_id` BIGINT NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cm_kept_party` (`party_id` BIGINT NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cm_profile` (`profile_id` BIGINT NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cm_bcr` (`history_id` BIGINT NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cm_bcv` (`version_key` VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL PRIMARY KEY);

INSERT INTO `tmp_cm_client` (`client_id`, `party_id`, `client_key`)
SELECT `client_id`, `party_id`, CAST(`client_id` AS CHAR) FROM `t_client` WHERE `status` = 'PENDING';
INSERT INTO `tmp_cm_app` (`application_id`, `app_key`, `submission_key`)
SELECT a.`application_id`, CAST(a.`application_id` AS CHAR), CAST(a.`current_submission_id` AS CHAR)
  FROM `t_client_application` a JOIN `tmp_cm_client` c ON c.`client_id` = a.`client_id`;
INSERT IGNORE INTO `tmp_cm_case` (`case_key`)
SELECT CAST(a.`due_diligence_case_id` AS CHAR) FROM `t_client_application` a JOIN `tmp_cm_app` t ON t.`application_id` = a.`application_id`
 WHERE a.`due_diligence_case_id` IS NOT NULL;
INSERT IGNORE INTO `tmp_cm_case` (`case_key`)
SELECT CAST(a.`previous_case_id` AS CHAR) FROM `t_client_application` a JOIN `tmp_cm_app` t ON t.`application_id` = a.`application_id`
 WHERE a.`previous_case_id` IS NOT NULL;
INSERT IGNORE INTO `tmp_cm_case` (`case_key`)
SELECT CAST(b.`classification_case_id` AS CHAR) FROM `t_client_business_profile` b JOIN `tmp_cm_client` c ON c.`client_id` = b.`client_id`
 WHERE b.`classification_case_id` IS NOT NULL;
INSERT INTO `tmp_cm_relation` (`relation_id`, `related_party_id`)
SELECT r.`relation_id`, r.`related_party_id` FROM `t_party_relation` r
 WHERE r.`client_application_id` IN (SELECT `application_id` FROM `tmp_cm_app`)
    OR r.`organization_party_id` IN (SELECT `party_id` FROM `tmp_cm_client`);
INSERT INTO `tmp_cm_bcr` (`history_id`)
SELECT b.`history_id` FROM `t_business_change_record` b JOIN `tmp_cm_client` c ON c.`client_key` = b.`aggregate_root_id`
 WHERE b.`aggregate_root_type` = 'CLIENT';
INSERT INTO `tmp_cm_bcv` (`version_key`)
SELECT CAST(v.`change_version_id` AS CHAR) FROM `t_business_change_version` v JOIN `tmp_cm_bcr` b ON b.`history_id` = v.`history_id`;

-- 候选 Party：PENDING Client 主体 + 其申请关系中的相关人员。
INSERT IGNORE INTO `tmp_cm_party` (`party_id`) SELECT `party_id` FROM `tmp_cm_client`;
INSERT IGNORE INTO `tmp_cm_party` (`party_id`) SELECT `related_party_id` FROM `tmp_cm_relation`;
-- 被保留数据引用的 Party：其他 Client、OE、Local Partner、不删除的关系、不删除的角色、不删除的材料、OE / Partner 申请引用的资料。
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`)
SELECT `party_id` FROM `t_client` WHERE `client_id` NOT IN (SELECT `client_id` FROM `tmp_cm_client`);
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`) SELECT `party_id` FROM `t_operating_entity`;
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`) SELECT `party_id` FROM `t_local_partner`;
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`)
SELECT `organization_party_id` FROM `t_party_relation` WHERE `relation_id` NOT IN (SELECT `relation_id` FROM `tmp_cm_relation`);
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`)
SELECT `related_party_id` FROM `t_party_relation` WHERE `relation_id` NOT IN (SELECT `relation_id` FROM `tmp_cm_relation`);
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`)
SELECT `party_id` FROM `t_party_role`
 WHERE NOT (`role_code` = 'CLIENT' AND `subject_id` IN (SELECT `client_key` FROM `tmp_cm_client`));
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`)
SELECT `subject_party_id` FROM `t_application_document`
 WHERE `subject_party_id` IS NOT NULL
   AND (`client_application_id` IS NULL OR `client_application_id` NOT IN (SELECT `application_id` FROM `tmp_cm_app`));
INSERT IGNORE INTO `tmp_cm_kept_party` (`party_id`)
SELECT f.`party_id` FROM `t_party_profile` f
 WHERE f.`profile_id` IN (SELECT `party_profile_id` FROM `t_operating_entity_application` WHERE `party_profile_id` IS NOT NULL)
    OR f.`profile_id` IN (SELECT `target_party_profile_id` FROM `t_partner_application` WHERE `target_party_profile_id` IS NOT NULL);

-- 0.1 Client 与申请现状
SELECT `status` AS client_status, COUNT(*) AS client_count FROM `t_client` GROUP BY `status` ORDER BY `status`;
SELECT c.`status` AS client_status, a.`status` AS application_status, COUNT(*) AS application_count
  FROM `t_client_application` a JOIN `t_client` c ON c.`client_id` = a.`client_id` GROUP BY 1, 2 ORDER BY 1, 2;
-- 0.2 删除集合预览
SELECT 'pending_clients' AS item, COUNT(*) AS n FROM `tmp_cm_client`
UNION ALL SELECT 'applications', COUNT(*) FROM `tmp_cm_app`
UNION ALL SELECT 'case_ids', COUNT(*) FROM `tmp_cm_case`
UNION ALL SELECT 'party_relations', COUNT(*) FROM `tmp_cm_relation`
UNION ALL SELECT 'bcr_records', COUNT(*) FROM `tmp_cm_bcr`
UNION ALL SELECT 'bcr_versions', COUNT(*) FROM `tmp_cm_bcv`;
SELECT COUNT(*) AS candidate_parties, SUM(EXISTS (SELECT 1 FROM `tmp_cm_kept_party` k WHERE k.`party_id` = p.`party_id`)) AS candidate_parties_kept
  FROM `tmp_cm_party` p;
-- 0.3 【停止条件】下列全部满足才继续，否则 SIGNAL 中止：无 PROCESSING 补偿任务；PENDING Client 未激活且无 APPROVED 申请；
--     PENDING Client 无 OE 关联、不是 OE 来源；PENDING Client 的 Party 不被保留数据使用；保留 Party 的资料不是 PENDING 申请的草稿。
CALL `cm_cleanup_assert`('PRE');

-- 保留数据基线（COMMIT 前比对）
SELECT COUNT(*) - (SELECT COUNT(*) FROM `tmp_cm_client`) INTO @cm_kept_clients FROM `t_client`;
SELECT COUNT(*) INTO @cm_oe FROM `t_operating_entity`;
SELECT COUNT(*) INTO @cm_lp FROM `t_local_partner`;
SELECT COUNT(*) INTO @cm_partner_role FROM `t_partner_role`;
SELECT COUNT(*) INTO @cm_oe_scope FROM `t_operating_entity_scope`;
SELECT COUNT(*) INTO @cm_source_rel FROM `t_entity_source_relation`;
SELECT COUNT(*) INTO @cm_disclosure FROM `t_seller_disclosure`;
SELECT COUNT(*) INTO @cm_affiliation FROM `t_client_entity_affiliation`;

DELETE FROM `tmp_cm_party` WHERE `party_id` IN (SELECT `party_id` FROM `tmp_cm_kept_party`);
INSERT INTO `tmp_cm_profile` (`profile_id`)
SELECT f.`profile_id` FROM `t_party_profile` f JOIN `tmp_cm_party` p ON p.`party_id` = f.`party_id`;

-- ===== 2. 删除（一个事务）=====
START TRANSACTION;
DELETE d FROM `t_application_document` d JOIN `tmp_cm_app` t ON t.`application_id` = d.`client_application_id`;
DELETE d FROM `t_application_declaration` d JOIN `tmp_cm_app` t ON t.`application_id` = d.`client_application_id`;
DELETE r FROM `t_party_relation` r JOIN `tmp_cm_relation` t ON t.`relation_id` = r.`relation_id`;
DELETE b FROM `t_client_business_classification` b
  JOIN `t_client_business_profile` p ON p.`business_profile_id` = b.`business_profile_id`
  JOIN `tmp_cm_client` t ON t.`client_id` = p.`client_id`;
DELETE p FROM `t_client_business_profile` p JOIN `tmp_cm_client` t ON t.`client_id` = p.`client_id`;
DELETE a FROM `t_client_application` a JOIN `tmp_cm_app` t ON t.`application_id` = a.`application_id`;
DELETE r FROM `t_client_partner_relation` r JOIN `tmp_cm_client` t ON t.`client_id` = r.`client_id`;
DELETE s FROM `t_client_stored_card_setting` s JOIN `tmp_cm_client` t ON t.`client_id` = s.`client_id`;
DELETE r FROM `t_party_role` r JOIN `tmp_cm_client` t ON t.`client_key` = r.`subject_id` WHERE r.`role_code` = 'CLIENT';
DELETE v FROM `t_business_change_version` v JOIN `tmp_cm_bcr` t ON t.`history_id` = v.`history_id`;
DELETE b FROM `t_business_change_record` b JOIN `tmp_cm_bcr` t ON t.`history_id` = b.`history_id`;
DELETE c FROM `t_client` c JOIN `tmp_cm_client` t ON t.`client_id` = c.`client_id`;
DELETE f FROM `t_party_attribute` f JOIN `tmp_cm_profile` t ON t.`profile_id` = f.`profile_id`;
DELETE f FROM `t_party_contact` f JOIN `tmp_cm_profile` t ON t.`profile_id` = f.`profile_id`;
DELETE f FROM `t_party_address` f JOIN `tmp_cm_profile` t ON t.`profile_id` = f.`profile_id`;
DELETE f FROM `t_party_profile_identifier` f JOIN `tmp_cm_profile` t ON t.`profile_id` = f.`profile_id`;
DELETE f FROM `t_individual_profile` f JOIN `tmp_cm_profile` t ON t.`profile_id` = f.`profile_id`;
DELETE f FROM `t_organization_profile` f JOIN `tmp_cm_profile` t ON t.`profile_id` = f.`profile_id`;
DELETE f FROM `t_party_profile` f JOIN `tmp_cm_profile` t ON t.`profile_id` = f.`profile_id`;
DELETE k FROM `t_party_identity_key` k JOIN `tmp_cm_party` t ON t.`party_id` = k.`party_id`;
DELETE p FROM `t_party` p JOIN `tmp_cm_party` t ON t.`party_id` = p.`party_id`;
DELETE e FROM `t_consumed_event` e JOIN `tmp_cm_case` t ON t.`case_key` = e.`aggregate_id` WHERE e.`aggregate_type` = 'DD_CASE';
DELETE e FROM `t_consumed_aggregate_version` e JOIN `tmp_cm_case` t ON t.`case_key` = e.`aggregate_id` WHERE e.`aggregate_type` = 'DD_CASE';
DELETE e FROM `t_compensation_event` e JOIN `tmp_cm_app` t ON t.`submission_key` = e.`event_key`
 WHERE e.`event_type` = 'CLIENT_REVIEW_REGISTRATION';
DELETE e FROM `t_compensation_event` e JOIN `tmp_cm_app` t ON t.`app_key` = e.`event_key`
 WHERE e.`event_type` = 'CLIENT_REVIEW_WITHDRAWAL';
DELETE e FROM `t_compensation_event` e JOIN `tmp_cm_case` t ON t.`case_key` = e.`event_key`
 WHERE e.`event_type` = 'CLIENT_REVIEW_CLASSIFICATION';
DELETE e FROM `t_compensation_event` e JOIN `tmp_cm_bcv` t ON t.`version_key` = e.`event_key`
 WHERE e.`event_type` = 'CLIENT_TENANT_PROJECTION';
CALL `cm_cleanup_assert`('POST');
COMMIT;

DROP PROCEDURE IF EXISTS `cm_cleanup_assert`;
DROP TEMPORARY TABLE IF EXISTS `tmp_cm_client`, `tmp_cm_app`, `tmp_cm_case`, `tmp_cm_relation`, `tmp_cm_party`, `tmp_cm_profile`,
  `tmp_cm_bcr`, `tmp_cm_bcv`, `tmp_cm_kept_party`;

-- ===== 3. 核验（只读）=====
-- 3.1 期望：只剩非 PENDING Client，数量等于清理前非 PENDING 数
SELECT `status` AS client_status, COUNT(*) AS client_count FROM `t_client` GROUP BY `status` ORDER BY `status`;
-- 3.2 期望：OE / Local Partner / 角色 / 范围 / 来源关系 / 卖方披露 / OE 关联与清理前相同
SELECT 't_operating_entity' AS t, COUNT(*) AS n FROM `t_operating_entity`
UNION ALL SELECT 't_local_partner', COUNT(*) FROM `t_local_partner`
UNION ALL SELECT 't_partner_role', COUNT(*) FROM `t_partner_role`
UNION ALL SELECT 't_operating_entity_scope', COUNT(*) FROM `t_operating_entity_scope`
UNION ALL SELECT 't_entity_source_relation', COUNT(*) FROM `t_entity_source_relation`
UNION ALL SELECT 't_seller_disclosure', COUNT(*) FROM `t_seller_disclosure`
UNION ALL SELECT 't_client_entity_affiliation', COUNT(*) FROM `t_client_entity_affiliation`
UNION ALL SELECT 't_party_role', COUNT(*) FROM `t_party_role`
UNION ALL SELECT 't_party', COUNT(*) FROM `t_party`
UNION ALL SELECT 't_client_application', COUNT(*) FROM `t_client_application`;
-- 3.3 期望全 0：孤儿行
SELECT 'orphan_application' AS item, COUNT(*) AS n FROM `t_client_application` a WHERE NOT EXISTS (SELECT 1 FROM `t_client` c WHERE c.`client_id` = a.`client_id`)
UNION ALL SELECT 'orphan_document', COUNT(*) FROM `t_application_document` d WHERE d.`client_application_id` IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM `t_client_application` a WHERE a.`application_id` = d.`client_application_id`)
UNION ALL SELECT 'orphan_relation', COUNT(*) FROM `t_party_relation` r WHERE NOT EXISTS (SELECT 1 FROM `t_party` p WHERE p.`party_id` = r.`related_party_id`)
   OR NOT EXISTS (SELECT 1 FROM `t_party` p WHERE p.`party_id` = r.`organization_party_id`)
UNION ALL SELECT 'orphan_profile', COUNT(*) FROM `t_party_profile` f WHERE NOT EXISTS (SELECT 1 FROM `t_party` p WHERE p.`party_id` = f.`party_id`)
UNION ALL SELECT 'orphan_individual', COUNT(*) FROM `t_individual_profile` i WHERE NOT EXISTS (SELECT 1 FROM `t_party_profile` f WHERE f.`profile_id` = i.`profile_id`)
UNION ALL SELECT 'orphan_organization', COUNT(*) FROM `t_organization_profile` i WHERE NOT EXISTS (SELECT 1 FROM `t_party_profile` f WHERE f.`profile_id` = i.`profile_id`)
UNION ALL SELECT 'orphan_identity_key', COUNT(*) FROM `t_party_identity_key` k WHERE NOT EXISTS (SELECT 1 FROM `t_party` p WHERE p.`party_id` = k.`party_id`)
UNION ALL SELECT 'orphan_client_role', COUNT(*) FROM `t_party_role` r WHERE r.`role_code` = 'CLIENT'
   AND NOT EXISTS (SELECT 1 FROM `t_client` c WHERE CAST(c.`client_id` AS CHAR) = r.`subject_id`)
UNION ALL SELECT 'orphan_client_bcr', COUNT(*) FROM `t_business_change_record` b WHERE b.`aggregate_root_type` = 'CLIENT'
   AND NOT EXISTS (SELECT 1 FROM `t_client` c WHERE CAST(c.`client_id` AS CHAR) = b.`aggregate_root_id`);
-- 3.4 展示：剩余消费记录与补偿任务
SELECT `aggregate_type`, COUNT(*) AS consumed_events FROM `t_consumed_event` GROUP BY `aggregate_type`;
SELECT `event_type`, `execute_status`, COUNT(*) AS compensation_events FROM `t_compensation_event` GROUP BY `event_type`, `execute_status`;
