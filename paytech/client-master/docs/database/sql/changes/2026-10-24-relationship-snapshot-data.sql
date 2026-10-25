-- 2026-10-24 商业关系快照与真实解析接入（client-master，数据变更；mor-test 用，不保留 PLATFORM 历史）。
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md 第 3.4.1 节 DML ①—⑤。
-- 前置：已执行 2026-10-24-relationship-snapshot.sql。执行后才能上线新版 client-master（新代码不认 PLATFORM）。
-- 手工 ID：900000000001370000 + ROW_NUMBER()，每张表独立号段（行数 < 1 万）：
--   t_local_partner          900000000001370001（我们的 PayFac Partner）
--   t_business_change_record 900000000001370001（PayFac Partner 根）、900000000001370002 起（缺根的 OE 补建根）
--   t_partner_role           900000000001370001 起
--   t_party_role             900000000001370001 起
-- 审计：本文件推进 / 补建 BCR 根版本，但不写 t_business_change_version 快照（快照完整性哈希按 RFC 8785 规范化计算，不在 SQL 中重算）；
--       本次迁移以本文件和执行记录为审计依据。之后的运营修改经 client-master Admin API 写快照。
-- 执行前核对（阶段 6 先列清单）：
--   SELECT entity_no, source_relation_id, status FROM t_entity_source_relation WHERE source_type = 'PLATFORM';
--   SELECT application_id, entity_no, status FROM t_operating_entity_application WHERE source_type = 'PLATFORM';
--   SELECT entity_no, party_id FROM t_operating_entity WHERE entity_no = 'AE001';      -- 必须存在
--   SELECT COUNT(*) FROM t_local_partner WHERE party_id = (SELECT party_id FROM t_operating_entity WHERE entity_no = 'AE001');  -- 必须为 0
--   SELECT COUNT(*) FROM t_partner_role; SELECT COUNT(*) FROM t_party_role;             -- 必须为 0
-- 执行前备份 t_operating_entity、t_local_partner、t_entity_source_relation、t_operating_entity_application、t_business_change_record。
-- 失败即停：任何写入前由 relsnap_assert_migratable 检查（存在 PLATFORM 行但 AE001 缺失、AE001 的 Party 已被某 Partner 占用，
-- 或 PayFac Partner ID 已被占用时 SIGNAL 中止）；②—⑤ 在一个事务内执行，COMMIT 前由 relsnap_assert_no_platform 断言两表
-- 不再有 PLATFORM 行，否则 SIGNAL 中止，mysql 客户端退出时未提交事务回滚。存储过程只在事务外创建 / 删除（DDL 隐式提交）。
-- ALTER 隐式提交；中止后修正数据再执行（① 已提交时重跑安全：回填只处理 NULL，MODIFY 可重复执行）。
--
-- 回退（须与 commercial / acquiring / risk-decision 代码回退同时进行；之后再按 DDL 文件回退段删表删列）：
--   START TRANSACTION;
--   DELETE FROM t_party_role WHERE party_role_id BETWEEN 900000000001370001 AND 900000000001379999;
--   DELETE FROM t_partner_role WHERE partner_role_id BETWEEN 900000000001370001 AND 900000000001379999;
--   UPDATE t_operating_entity_application SET source_type = 'PLATFORM', source_partner_id = NULL, update_time = UTC_TIMESTAMP(6)
--    WHERE source_type = 'PARTNER' AND source_partner_id = 900000000001370001;
--   UPDATE t_business_change_record SET current_version = current_version - 1, update_time = UTC_TIMESTAMP(6)
--    WHERE record_type = 'OPERATING_ENTITY' AND parent_history_id IS NULL AND history_id NOT BETWEEN 900000000001370001 AND 900000000001379999
--      AND record_id IN (SELECT entity_no FROM t_entity_source_relation WHERE source_type = 'PARTNER' AND source_partner_id = 900000000001370001);
--   UPDATE t_entity_source_relation SET source_type = 'PLATFORM', source_partner_id = NULL, update_time = UTC_TIMESTAMP(6)
--    WHERE source_type = 'PARTNER' AND source_partner_id = 900000000001370001;
--   DELETE FROM t_business_change_record WHERE history_id BETWEEN 900000000001370001 AND 900000000001379999;
--   DELETE FROM t_local_partner WHERE local_partner_id = 900000000001370001;
--   COMMIT;
--   -- 迁移后经 Admin API 修改过的 OE / Partner 根版本与快照不回退，回退前先核对 t_business_change_version 中 version_no 更大的行。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @payfac_partner_id = 900000000001370001;
SET @id_base = 900000000001370000;

-- 失败即停的前置与后置检查（事务外创建）。
DROP PROCEDURE IF EXISTS `relsnap_assert_migratable`;
DROP PROCEDURE IF EXISTS `relsnap_assert_no_platform`;
DELIMITER //
CREATE PROCEDURE `relsnap_assert_migratable`()
BEGIN
  DECLARE platform_rows BIGINT;
  DECLARE ae001_party BIGINT;
  SELECT (SELECT COUNT(*) FROM `t_entity_source_relation` WHERE `source_type` = 'PLATFORM')
       + (SELECT COUNT(*) FROM `t_operating_entity_application` WHERE `source_type` = 'PLATFORM') INTO platform_rows;
  SELECT MAX(`party_id`) INTO ae001_party FROM `t_operating_entity` WHERE `entity_no` = 'AE001';
  IF platform_rows > 0 AND ae001_party IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'PLATFORM source rows exist but AE001 is missing; abort before any write';
  END IF;
  IF ae001_party IS NOT NULL AND EXISTS (SELECT 1 FROM `t_local_partner` WHERE `party_id` = ae001_party) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a Local Partner already uses the AE001 party; abort before any write';
  END IF;
  IF EXISTS (SELECT 1 FROM `t_local_partner` WHERE `local_partner_id` = 900000000001370001)
     OR EXISTS (SELECT 1 FROM `t_partner_role` WHERE `partner_role_id` BETWEEN 900000000001370001 AND 900000000001379999)
     OR EXISTS (SELECT 1 FROM `t_party_role` WHERE `party_role_id` BETWEEN 900000000001370001 AND 900000000001379999) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'relationship snapshot ID range already used; abort before any write';
  END IF;
END//
CREATE PROCEDURE `relsnap_assert_no_platform`()
BEGIN
  IF EXISTS (SELECT 1 FROM `t_entity_source_relation` WHERE `source_type` = 'PLATFORM')
     OR EXISTS (SELECT 1 FROM `t_operating_entity_application` WHERE `source_type` = 'PLATFORM') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'PLATFORM source rows remain after migration; transaction not committed';
  END IF;
END//
DELIMITER ;
CALL `relsnap_assert_migratable`();

-- ① 回填收单资质并改为 NOT NULL：存量一律 PAYFAC，保持现有候选不被过滤，运营再按实情调整。
UPDATE `t_operating_entity` SET `acquiring_qualification` = 'PAYFAC', `update_time` = UTC_TIMESTAMP(6)
 WHERE `acquiring_qualification` IS NULL;
ALTER TABLE `t_operating_entity`
  MODIFY COLUMN `acquiring_qualification` VARCHAR(64) NOT NULL COMMENT 'Operating Entity 经收单行认定的收单资质档位：MERCHANT 只能作为自身商户收单、不能承接 Client；MERCHANT_RESALE_APPROVED 经收单行批准转售、只承接 MOR；PAYFAC 支付服务商资质、可承接 MOR 与 PSP。允许值：MERCHANT、MERCHANT_RESALE_APPROVED、PAYFAC。';

START TRANSACTION;

-- ② 我们的 PayFac Partner：绑定 AE001 自身的 Party（满足 Partner Party = OE Party），ACTIVE，并建 LOCAL_PARTNER 根 V1。
INSERT INTO `t_local_partner` (`local_partner_id`, `partner_no`, `party_id`, `status`, `activation_time`, `termination_time`,
  `create_time`, `update_time`)
SELECT @payfac_partner_id, 'LP-PAYFAC-AE001', e.`party_id`, 'ACTIVE', UTC_TIMESTAMP(6), NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM `t_operating_entity` e WHERE e.`entity_no` = 'AE001';
INSERT INTO `t_business_change_record` (`history_id`, `record_type`, `record_id`, `aggregate_root_type`, `aggregate_root_id`,
  `parent_history_id`, `current_version`, `create_time`, `update_time`)
SELECT @id_base + 1, 'LOCAL_PARTNER', CAST(p.`local_partner_id` AS CHAR), 'LOCAL_PARTNER', CAST(p.`local_partner_id` AS CHAR), NULL, 1,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM `t_local_partner` p WHERE p.`local_partner_id` = @payfac_partner_id;

-- ③ PLATFORM 来源改为 PARTNER 并指向 PayFac Partner；受影响 OE 的根版本推进（已有根 +1，缺根补建 V1）。
--    PayFac Partner 不存在（AE001 缺失）时整步不生效。
CREATE TEMPORARY TABLE `tmp_platform_entity` AS
SELECT DISTINCT s.`entity_no` FROM `t_entity_source_relation` s
 WHERE s.`source_type` = 'PLATFORM'
   AND EXISTS (SELECT 1 FROM `t_local_partner` p WHERE p.`local_partner_id` = @payfac_partner_id);
UPDATE `t_entity_source_relation` SET `source_type` = 'PARTNER', `source_partner_id` = @payfac_partner_id,
       `update_time` = UTC_TIMESTAMP(6)
 WHERE `source_type` = 'PLATFORM' AND `entity_no` IN (SELECT `entity_no` FROM `tmp_platform_entity`);
UPDATE `t_operating_entity_application` SET `source_type` = 'PARTNER', `source_partner_id` = @payfac_partner_id,
       `update_time` = UTC_TIMESTAMP(6)
 WHERE `source_type` = 'PLATFORM' AND `entity_no` IN (SELECT `entity_no` FROM `tmp_platform_entity`);
UPDATE `t_business_change_record` SET `current_version` = `current_version` + 1, `update_time` = UTC_TIMESTAMP(6)
 WHERE `record_type` = 'OPERATING_ENTITY' AND `aggregate_root_type` = 'OPERATING_ENTITY' AND `parent_history_id` IS NULL
   AND `record_id` IN (SELECT `entity_no` FROM `tmp_platform_entity`);
INSERT INTO `t_business_change_record` (`history_id`, `record_type`, `record_id`, `aggregate_root_type`, `aggregate_root_id`,
  `parent_history_id`, `current_version`, `create_time`, `update_time`)
SELECT @id_base + 1 + ROW_NUMBER() OVER (ORDER BY m.`entity_no`), 'OPERATING_ENTITY', m.`entity_no`, 'OPERATING_ENTITY', m.`entity_no`,
       NULL, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM `tmp_platform_entity` m
 WHERE NOT EXISTS (SELECT 1 FROM `t_business_change_record` r
                    WHERE r.`record_type` = 'OPERATING_ENTITY' AND r.`record_id` = m.`entity_no`);
DROP TEMPORARY TABLE `tmp_platform_entity`;

-- ④ PARTNER 来源关系引用的 Local Partner（含 PayFac Partner）回填 OE_PROVIDER 角色。
INSERT INTO `t_partner_role` (`partner_role_id`, `local_partner_id`, `role_code`, `status`, `active_slot`, `create_time`, `update_time`)
SELECT @id_base + ROW_NUMBER() OVER (ORDER BY p.`local_partner_id`), p.`local_partner_id`, 'OE_PROVIDER', 'ACTIVE', 1,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM (SELECT DISTINCT s.`source_partner_id` AS `local_partner_id` FROM `t_entity_source_relation` s
         JOIN `t_local_partner` lp ON lp.`local_partner_id` = s.`source_partner_id`
        WHERE s.`source_type` = 'PARTNER') p;

-- ⑤ 统一角色索引：Client、Operating Entity 与 OE_PROVIDER。
INSERT INTO `t_party_role` (`party_role_id`, `party_id`, `role_code`, `subject_id`, `status`, `active_slot`, `effective_from`,
  `effective_to`, `create_time`, `update_time`)
SELECT @id_base + ROW_NUMBER() OVER (ORDER BY r.`role_code`, r.`subject_id`), r.`party_id`, r.`role_code`, r.`subject_id`, 'ACTIVE', 1,
       r.`effective_from`, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
  FROM (SELECT c.`party_id`, 'CLIENT' AS `role_code`, CAST(c.`client_id` AS CHAR) AS `subject_id`, c.`create_time` AS `effective_from`
          FROM `t_client` c
        UNION ALL
        SELECT e.`party_id`, 'OPERATING_ENTITY', e.`entity_no`, e.`create_time` FROM `t_operating_entity` e
        UNION ALL
        SELECT lp.`party_id`, 'OE_PROVIDER', CAST(lp.`local_partner_id` AS CHAR), pr.`create_time`
          FROM `t_partner_role` pr JOIN `t_local_partner` lp ON lp.`local_partner_id` = pr.`local_partner_id`
         WHERE pr.`role_code` = 'OE_PROVIDER' AND pr.`status` = 'ACTIVE') r;

-- 后置检查：两表不得残留 PLATFORM；失败时 SIGNAL 中止，本事务不提交。
CALL `relsnap_assert_no_platform`();

COMMIT;

DROP PROCEDURE `relsnap_assert_migratable`;
DROP PROCEDURE `relsnap_assert_no_platform`;

-- 执行后核对：
--   SELECT COUNT(*) FROM t_entity_source_relation WHERE source_type = 'PLATFORM';           -- 期望 0
--   SELECT COUNT(*) FROM t_operating_entity_application WHERE source_type = 'PLATFORM';     -- 期望 0
--   SELECT COUNT(*) FROM t_operating_entity WHERE acquiring_qualification IS NULL;          -- 期望 0（列已 NOT NULL）
--   SELECT * FROM t_local_partner WHERE local_partner_id = 900000000001370001;
--   SELECT record_type, record_id, current_version FROM t_business_change_record
--    WHERE record_type IN ('OPERATING_ENTITY','LOCAL_PARTNER') ORDER BY record_type, record_id;
--   SELECT role_code, COUNT(*) FROM t_party_role GROUP BY role_code;
