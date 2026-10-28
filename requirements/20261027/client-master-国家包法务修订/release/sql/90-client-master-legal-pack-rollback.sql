-- 2026-10-27 client-master 国家包法务修订：回退 changes/2026-10-27-legal-pack-revision.sql（增量 DML）。
-- 需求：requirements/20261027/client-master-国家包法务修订/technique.md 第 5 节。
-- 效果（一个事务）：删除 ID 段 9270000000900000—9270000000999999 内的 Schema 字段、Schema（8 个 v2 与 SA 自由职业者 v1）、目录项 AE_OTHER、
--   规则开关与新标识类型，按代码删除新增属性定义（AE_ERN, AE_REGISTRATION_AUTHORITY_NAME, AE_LICENSE_ERN, AE_LICENSE_AUTHORITY_NAME, SA_CR_ANNUAL_CONFIRMATION_DATE, EG_VAT_REGISTRATION_NUMBER, SA_FREELANCE_DOCUMENT_NUMBER, SA_FREELANCE_DOCUMENT_EXPIRY_DATE）；
--   再把 8 个 v1 Schema（9270000000700001—008）中状态为 RETIRED 的行恢复为 ACTIVE、active_slot = 1（先删 v2 释放 uk_schema_active 槽位）。
-- 顺序：在 client-master 新镜像回退之前执行（旧镜像不能读取 scope_type=DECLARATION 的规则行）；DDL 的 2 个新列保留，无害。
-- 停止条件（cm_legal_rollback_assert SIGNAL，尚未写入）——有任一引用时拒绝执行，须先由业务确认处理这些申请或资料：
--   * t_client_application.schema_id 引用本段 Schema（已按 v2 / 自由职业者表单创建的申请）；
--   * t_party_attribute 引用新增属性代码，或 AE_LICENSE_AUTHORITY 取值为 AE_OTHER；
--   * t_organization_profile.registration_authority_code 为 AE_OTHER（v1 表单也能选到该目录项）；
--   * t_party_profile_identifier / t_party_identity_key 引用新标识类型；
--   * t_application_declaration 含新增声明类型。
-- 已知限制：若修订前 v1 为 DRAFT（国家包以 DRAFT 写入的生产口径），本回退会把它恢复为 ACTIVE；此时执行后须按发布口径手工改回 DRAFT。
-- 幂等：重复执行为空操作。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ===== 1. 预检（只读）=====
SET @cm_legal_rb_applications = (SELECT COUNT(*) FROM `t_client_application` WHERE `schema_id` BETWEEN 9270000000900000 AND 9270000000999999);
SET @cm_legal_rb_attributes = (SELECT COUNT(*) FROM `t_party_attribute`
    WHERE `attribute_code` IN ('AE_ERN', 'AE_REGISTRATION_AUTHORITY_NAME', 'AE_LICENSE_ERN', 'AE_LICENSE_AUTHORITY_NAME', 'SA_CR_ANNUAL_CONFIRMATION_DATE', 'EG_VAT_REGISTRATION_NUMBER', 'SA_FREELANCE_DOCUMENT_NUMBER', 'SA_FREELANCE_DOCUMENT_EXPIRY_DATE') OR (`attribute_code` = 'AE_LICENSE_AUTHORITY' AND `value_text` = 'AE_OTHER'));
SET @cm_legal_rb_authorities = (SELECT COUNT(*) FROM `t_organization_profile` WHERE `registration_authority_code` = 'AE_OTHER');
SET @cm_legal_rb_identifiers = (SELECT COUNT(*) FROM `t_party_profile_identifier` WHERE `identifier_type` IN ('EG_UNIFIED_CR', 'EG_LOCAL_CR'))
    + (SELECT COUNT(*) FROM `t_party_identity_key` WHERE `identifier_type` IN ('EG_UNIFIED_CR', 'EG_LOCAL_CR'));
SET @cm_legal_rb_declarations = (SELECT COUNT(*) FROM `t_application_declaration` WHERE `declaration_type` IN ('PLATFORM_TERMS_ACCEPTANCE', 'PRIVACY_NOTICE_ACKNOWLEDGED', 'RELATED_PERSON_DATA_AUTHORITY'));
SELECT @cm_legal_rb_applications AS v2_applications_expect_0, @cm_legal_rb_attributes AS new_attribute_values_expect_0,
       @cm_legal_rb_authorities AS ae_other_authorities_expect_0, @cm_legal_rb_identifiers AS new_identifier_types_expect_0,
       @cm_legal_rb_declarations AS new_declarations_expect_0;
SELECT `schema_id`, `version_no`, `status`, `active_slot` FROM `t_onboarding_schema`
 WHERE `schema_id` IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008) OR `schema_id` BETWEEN 9270000000900000 AND 9270000000999999 ORDER BY `schema_id`;

DROP PROCEDURE IF EXISTS `cm_legal_rollback_assert`;
DELIMITER //
CREATE PROCEDURE `cm_legal_rollback_assert`()
BEGIN
  IF @cm_legal_rb_applications + @cm_legal_rb_attributes + @cm_legal_rb_authorities + @cm_legal_rb_identifiers + @cm_legal_rb_declarations <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: applications or party data still reference the legal-pack revision; resolve them before rollback';
  END IF;
END //
DELIMITER ;
CALL `cm_legal_rollback_assert`();
DROP PROCEDURE IF EXISTS `cm_legal_rollback_assert`;

-- ===== 2. 回退（一个事务）=====
START TRANSACTION;
DELETE FROM `t_onboarding_schema_field` WHERE `schema_field_id` BETWEEN 9270000000900000 AND 9270000000999999;
DELETE FROM `t_onboarding_schema` WHERE `schema_id` BETWEEN 9270000000900000 AND 9270000000999999;
UPDATE `t_onboarding_schema` SET `status` = 'ACTIVE', `active_slot` = 1, `update_time` = UTC_TIMESTAMP(6)
 WHERE `schema_id` IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008) AND `status` = 'RETIRED';
DELETE FROM `t_catalog_item` WHERE `catalog_item_id` BETWEEN 9270000000900000 AND 9270000000999999;
DELETE FROM `t_rule_setting` WHERE `rule_setting_id` BETWEEN 9270000000900000 AND 9270000000999999;
DELETE FROM `t_identifier_type` WHERE `identifier_type_id` BETWEEN 9270000000900000 AND 9270000000999999;
DELETE FROM `t_attribute_definition` WHERE `attribute_code` IN ('AE_ERN', 'AE_REGISTRATION_AUTHORITY_NAME', 'AE_LICENSE_ERN', 'AE_LICENSE_AUTHORITY_NAME', 'SA_CR_ANNUAL_CONFIRMATION_DATE', 'EG_VAT_REGISTRATION_NUMBER', 'SA_FREELANCE_DOCUMENT_NUMBER', 'SA_FREELANCE_DOCUMENT_EXPIRY_DATE');
COMMIT;

-- ===== 3. 核验（只读）：本段剩余行全为 0；8 个 v1 为 ACTIVE / 1 =====
SELECT (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_field_id` BETWEEN 9270000000900000 AND 9270000000999999) AS fields,
       (SELECT COUNT(*) FROM `t_onboarding_schema` WHERE `schema_id` BETWEEN 9270000000900000 AND 9270000000999999) AS `schema_count`,
       (SELECT COUNT(*) FROM `t_catalog_item` WHERE `catalog_item_id` BETWEEN 9270000000900000 AND 9270000000999999) AS catalog_items,
       (SELECT COUNT(*) FROM `t_rule_setting` WHERE `rule_setting_id` BETWEEN 9270000000900000 AND 9270000000999999) AS rule_settings,
       (SELECT COUNT(*) FROM `t_identifier_type` WHERE `identifier_type_id` BETWEEN 9270000000900000 AND 9270000000999999) AS identifier_types,
       (SELECT COUNT(*) FROM `t_attribute_definition` WHERE `attribute_code` IN ('AE_ERN', 'AE_REGISTRATION_AUTHORITY_NAME', 'AE_LICENSE_ERN', 'AE_LICENSE_AUTHORITY_NAME', 'SA_CR_ANNUAL_CONFIRMATION_DATE', 'EG_VAT_REGISTRATION_NUMBER', 'SA_FREELANCE_DOCUMENT_NUMBER', 'SA_FREELANCE_DOCUMENT_EXPIRY_DATE')) AS attribute_definitions;
SELECT `schema_id`, `version_no`, `status`, `active_slot` FROM `t_onboarding_schema` WHERE `schema_id` IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008) ORDER BY `schema_id`;
