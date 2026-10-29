-- 2026-10-29 client-master 业务信息补采：回退 changes/2026-10-29-business-info-collection.sql（Schema v3 增量 DML）。
-- 需求：requirements/20261028/client-master-业务信息补采/technique.md 第 9.4、13 节。
-- 效果（一个事务）：删除 ID 段 9281200000000000—9281200000999999 内的 Schema 字段、9 个 v3 Schema 与 FULFILLMENT_TYPE 目录项（4 行）；
--   再把 9 个旧版本 Schema（9270000000900301—309）中状态为 RETIRED 的行恢复为 ACTIVE、active_slot = 1（先删 v3 释放 uk_schema_active 槽位）。
-- 顺序：在两个 Web 与 client-master 镜像回退之前执行；DDL 的 4 个新列保留无害，需删除时再执行 2026-10-29-business-info-collection-ddl-rollback.sql。
-- 停止条件（cm_bizinfo_rollback_assert SIGNAL，尚未写入）——有任一引用时拒绝执行，只能向前修复：
--   * t_client_application.schema_id 引用 v3 Schema（已按 v3 表单创建的申请）；
--   * t_client_business_profile.fulfillment_type 非空（资料引用 FULFILLMENT_TYPE 目录项）。
-- 已知限制：若发布前旧版本为 DRAFT（以 DRAFT 写入的生产口径），本回退会把它恢复为 ACTIVE；此时执行后须按发布口径手工改回 DRAFT。
-- 幂等：重复执行为空操作。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ===== 1. 预检（只读）=====
SET @cm_bizinfo_rb_applications = (SELECT COUNT(*) FROM `t_client_application` WHERE `schema_id` BETWEEN 9281200000900000 AND 9281200000999999);
SET @cm_bizinfo_rb_fulfillment = (SELECT COUNT(*) FROM `t_client_business_profile` WHERE `fulfillment_type` IS NOT NULL);
SELECT @cm_bizinfo_rb_applications AS v3_applications_expect_0, @cm_bizinfo_rb_fulfillment AS fulfillment_values_expect_0;
SELECT `schema_id`, `version_no`, `status`, `active_slot` FROM `t_onboarding_schema`
 WHERE `schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309)
    OR `schema_id` BETWEEN 9281200000900000 AND 9281200000999999 ORDER BY `schema_id`;

DROP PROCEDURE IF EXISTS `cm_bizinfo_rollback_assert`;
DELIMITER //
CREATE PROCEDURE `cm_bizinfo_rollback_assert`()
BEGIN
  IF @cm_bizinfo_rb_applications + @cm_bizinfo_rb_fulfillment <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: applications or business profiles still reference the business-info v3 schemas; fix forward instead';
  END IF;
END //
DELIMITER ;
CALL `cm_bizinfo_rollback_assert`();
DROP PROCEDURE IF EXISTS `cm_bizinfo_rollback_assert`;

-- ===== 2. 回退（一个事务）=====
START TRANSACTION;
DELETE FROM `t_onboarding_schema_field` WHERE `schema_field_id` BETWEEN 9281200000000000 AND 9281200000999999;
DELETE FROM `t_onboarding_schema` WHERE `schema_id` BETWEEN 9281200000000000 AND 9281200000999999;
UPDATE `t_onboarding_schema` SET `status` = 'ACTIVE', `active_slot` = 1, `update_time` = UTC_TIMESTAMP(6)
 WHERE `schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309)
   AND `status` = 'RETIRED';
DELETE FROM `t_catalog_item` WHERE `catalog_item_id` BETWEEN 9281200000000000 AND 9281200000999999;
COMMIT;

-- ===== 3. 核验（只读）：本段剩余行全为 0；9 个旧版本为 ACTIVE / 1 =====
SELECT (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_field_id` BETWEEN 9281200000000000 AND 9281200000999999) AS fields,
       (SELECT COUNT(*) FROM `t_onboarding_schema` WHERE `schema_id` BETWEEN 9281200000000000 AND 9281200000999999) AS `schema_count`,
       (SELECT COUNT(*) FROM `t_catalog_item` WHERE `catalog_item_id` BETWEEN 9281200000000000 AND 9281200000999999) AS catalog_items;
SELECT `schema_id`, `version_no`, `status`, `active_slot` FROM `t_onboarding_schema`
 WHERE `schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309)
 ORDER BY `schema_id`;
