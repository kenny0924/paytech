-- 2026-10-27 client-master 国家包法务修订（AE / SA / EG 增量 DML；需求 S-1—S-7 的数据部分）。
-- 需求：requirements/20261027/client-master-国家包法务修订/requirement.md、technique.md 第 3.1、3.2 节；法源 design-discuss/client-onboarding/design.md 第 9.5a 节。
-- 内容（固定 ID 段 9270000000900000—9270000000999999，不与切片 1 的 …100000—…899999 重叠）：
--   t_identifier_type +2、t_attribute_definition +8、t_catalog_item +1、t_rule_setting +12；
--   t_onboarding_schema +9（8 个现有 Schema 的 version_no=2，ID = v1 + 200300；新增 SA / INDIVIDUAL / FREELANCER v1，ID 9270000000900309），
--   对应 v1 改为 RETIRED、active_slot 置空（共 8 行更新）；
--   t_onboarding_schema_field：v2 字段 = 执行时线上 v1 字段的复制（ID = v1 + 110000，保留参考数据需求改过的 REF_ 目录码等线上取值）
--   去掉每个 Schema 的差异删除项，再加 32 行新增字段（9270000000920000 段；自由职业者专属 9270000000950000 段）；
--   自由职业者 Schema 的其余字段 = SA 个人 v2 字段的复制（ID = SA 个人 v2 ID + 20000）。按初始化 DML 计共 327 行。
--   差异：v1 的 declaration.PLATFORM_TERMS 拆为 PLATFORM_TERMS_ACCEPTANCE、PRIVACY_NOTICE_ACKNOWLEDGED（机构另加 RELATED_PERSON_DATA_AUTHORITY）；
--   AE 机构加 AE_ERN 与“其他”签发机关名称 AE_REGISTRATION_AUTHORITY_NAME，AE 个人独资加 AE_LICENSE_ERN 与 AE_LICENSE_AUTHORITY_NAME
--   （属性定义的 party_type 只能是一种主体类型，故机构与个人各一组代码）；SA 机构去掉 organization.licenseExpiryDate、加 CR 年度确认日；
--   EG 机构以 EG_UNIFIED_CR / EG_LOCAL_CR（沿用 v1 的 ORGANIZATION_ID 组，二选一；每份资料只有一个主标识）替换 EG_COMMERCIAL_REGISTER，加增值税登记号。
--   本文件与 client-master-initial-data.sql 的最终状态一致；空库仍用初始化 DML。
-- 前置：
--   1. 已执行 changes/2026-10-27-legal-pack-revision-ddl.sql（t_client_application / t_application_declaration 的 draft_revision 列）。
--   2. 已部署识别 RuleScopeType.DECLARATION / ATTRIBUTE、RuleCode.UBO_THRESHOLD_COMPARISON / DOCUMENT_VERSION / DATE_NOT_PAST 与声明版本判定的 client-master 新镜像。
--      旧镜像按枚举映射 scope_type，读到 DECLARATION / ATTRIBUTE 行会失败（运营规则查看 findAll），所以本文件不得先于新镜像执行。
--   3. 已执行切片 1 国家包与参考数据迁移（changes/2026-10-27-reference-data-catalog.sql）；不重跑 2026-10-27-onboarding-country-pack.sql。
-- 状态口径：@cm_legal_status 缺省 ACTIVE（本地与 mor-test 验收）：v2 与自由职业者 Schema 以 ACTIVE 发布，v1 退役，AE_OTHER 目录项 ACTIVE。
--   生产执行前法务其余项未确认时，同一会话先 SET @cm_legal_status = 'DRAFT';：v2 与 AE_OTHER 以 DRAFT 写入，v1 保持不变；
--   之后以 ACTIVE 重跑本文件即发布（退役 v1，把本 ID 段内的 DRAFT Schema 与目录项改为 ACTIVE）。
--   标识类型、属性定义与规则开关总以 ACTIVE 写入（只经 v2 Schema 字段被使用；规则表只有 ACTIVE / RETIRED）。
-- 执行顺序：DDL → client-master 新镜像 → 本文件 → 两个 BFF → 两个 Web → 语言包（technique 第 5 节）。
-- 幂等：静态行与复制行先写会话临时表，再 INSERT … SELECT … ON DUPLICATE KEY UPDATE（不改已存在行）；v1 退役与 DRAFT 发布按当前状态条件更新；
--   重跑为空操作（v1 已退役后，复制仍取 v1 字段，按主键去重）。
-- 停止条件（cm_legal_assert SIGNAL，尚未写入）：@cm_legal_status 非 ACTIVE / DRAFT；8 个 v1 Schema 行缺失或键不符；
--   线上 v1 字段 ID 不在 9270000000800001—9270000000809999（否则 +110000 会越出本段）；新增字段键已出现在对应 v1 中（复制后会重复）；
--   同一业务唯一键已被本 ID 段以外的行占用。
-- 回退：changes/2026-10-27-legal-pack-revision-rollback.sql（存在引用 v2 / 自由职业者 Schema 的申请或引用新代码的资料时拒绝执行）。
-- 新语言包键（需随语言包发布，en / zh-CN）：onboardingSchema.identifierType.*、onboardingSchema.attribute.*、onboardingSchema.declaration.*（3 项）、
--   onboardingSchema.catalog.REGISTRATION_AUTHORITY.AE_OTHER；见本文件新增行的 label_key。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @cm_legal_status = COALESCE(@cm_legal_status, 'ACTIVE');

DROP TEMPORARY TABLE IF EXISTS `tmp_legal_identifier_type`, `tmp_legal_attribute_definition`, `tmp_legal_catalog_item`, `tmp_legal_rule_setting`, `tmp_legal_onboarding_schema`, `tmp_legal_onboarding_schema_field`, `tmp_legal_removed_field`, `tmp_legal_freelancer_field`;
CREATE TEMPORARY TABLE `tmp_legal_identifier_type` LIKE `t_identifier_type`;
CREATE TEMPORARY TABLE `tmp_legal_attribute_definition` LIKE `t_attribute_definition`;
CREATE TEMPORARY TABLE `tmp_legal_catalog_item` LIKE `t_catalog_item`;
CREATE TEMPORARY TABLE `tmp_legal_rule_setting` LIKE `t_rule_setting`;
CREATE TEMPORARY TABLE `tmp_legal_onboarding_schema` LIKE `t_onboarding_schema`;
CREATE TEMPORARY TABLE `tmp_legal_onboarding_schema_field` LIKE `t_onboarding_schema_field`;
CREATE TEMPORARY TABLE `tmp_legal_freelancer_field` LIKE `t_onboarding_schema_field`;
CREATE TEMPORARY TABLE `tmp_legal_removed_field` (`schema_id` BIGINT NOT NULL, `field_key` VARCHAR(128) NOT NULL, `applies_to_subject` VARCHAR(32) NOT NULL);

-- ===== 1. 修订行（写入会话临时表）=====
-- t_identifier_type（2 行；EG_COMMERCIAL_REGISTER 保留 ACTIVE 供 v1 申请）
INSERT INTO `tmp_legal_identifier_type` (`identifier_type_id`, `identifier_type_code`, `jurisdiction_code`, `party_type`, `label_key`, `format_regex`, `normalization_rule`, `checksum_rule`, `issuer_scope_mode`, `verification_check_code`, `status`, `create_time`, `update_time`) VALUES
  (9270000000900001, 'EG_UNIFIED_CR', 'EG', 'ORGANIZATION', 'onboardingSchema.identifierType.EG_UNIFIED_CR', '[0-9]{15}', 'STANDARD', 'NONE', 'NONE', NULL, 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900002, 'EG_LOCAL_CR', 'EG', 'ORGANIZATION', 'onboardingSchema.identifierType.EG_LOCAL_CR', '[0-9]{1,15}', 'STANDARD', 'NONE', 'AUTHORITY', NULL, 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000');

-- t_attribute_definition（8 行）
INSERT INTO `tmp_legal_attribute_definition` (`attribute_code`, `jurisdiction_code`, `party_type`, `data_type`, `catalog_code`, `sensitivity`, `vault_data_type`, `max_length`, `format_regex`, `label_key`, `status`, `create_time`, `update_time`) VALUES
  ('AE_ERN', 'AE', 'ORGANIZATION', 'TEXT', NULL, 'PUBLIC', NULL, 30, '[A-Z0-9-]{1,30}', 'onboardingSchema.attribute.AE_ERN', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  ('AE_REGISTRATION_AUTHORITY_NAME', 'AE', 'ORGANIZATION', 'TEXT', NULL, 'PUBLIC', NULL, 128, NULL, 'onboardingSchema.attribute.AE_REGISTRATION_AUTHORITY_NAME', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  ('AE_LICENSE_ERN', 'AE', 'INDIVIDUAL', 'TEXT', NULL, 'PUBLIC', NULL, 30, '[A-Z0-9-]{1,30}', 'onboardingSchema.attribute.AE_LICENSE_ERN', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  ('AE_LICENSE_AUTHORITY_NAME', 'AE', 'INDIVIDUAL', 'TEXT', NULL, 'PUBLIC', NULL, 128, NULL, 'onboardingSchema.attribute.AE_LICENSE_AUTHORITY_NAME', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  ('SA_CR_ANNUAL_CONFIRMATION_DATE', 'SA', 'ORGANIZATION', 'DATE', NULL, 'PUBLIC', NULL, NULL, NULL, 'onboardingSchema.attribute.SA_CR_ANNUAL_CONFIRMATION_DATE', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  ('EG_VAT_REGISTRATION_NUMBER', 'EG', 'ORGANIZATION', 'TEXT', NULL, 'PUBLIC', NULL, 30, '[A-Z0-9-]{1,30}', 'onboardingSchema.attribute.EG_VAT_REGISTRATION_NUMBER', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  ('SA_FREELANCE_DOCUMENT_NUMBER', 'SA', 'INDIVIDUAL', 'TEXT', NULL, 'PUBLIC', NULL, 34, '[Ff][Ll][ -]?[0-9A-Za-z]{1,30}', 'onboardingSchema.attribute.SA_FREELANCE_DOCUMENT_NUMBER', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  ('SA_FREELANCE_DOCUMENT_EXPIRY_DATE', 'SA', 'INDIVIDUAL', 'DATE', NULL, 'PUBLIC', NULL, NULL, NULL, 'onboardingSchema.attribute.SA_FREELANCE_DOCUMENT_EXPIRY_DATE', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000');

-- t_catalog_item（1 行，排在 AE 签发机关最后）
INSERT INTO `tmp_legal_catalog_item` (`catalog_item_id`, `catalog_code`, `item_code`, `jurisdiction_code`, `label_key`, `client_types`, `display_order`, `status`, `create_time`, `update_time`) VALUES
  (9270000000900101, 'REGISTRATION_AUTHORITY', 'AE_OTHER', 'AE', 'onboardingSchema.catalog.REGISTRATION_AUTHORITY.AE_OTHER', NULL, 90, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000');

-- t_rule_setting（12 行：受益人门槛比较方式 ×3、新标识类型开关、声明文档版本 ×2、自由职业文件到期日不得早于当天 ×1）
INSERT INTO `tmp_legal_rule_setting` (`rule_setting_id`, `scope_type`, `scope_code`, `rule_code`, `rule_value`, `status`, `create_time`, `update_time`) VALUES
  (9270000000900201, 'JURISDICTION', 'AE', 'UBO_THRESHOLD_COMPARISON', 'GTE', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900202, 'JURISDICTION', 'SA', 'UBO_THRESHOLD_COMPARISON', 'GTE', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900203, 'JURISDICTION', 'EG', 'UBO_THRESHOLD_COMPARISON', 'GTE', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900204, 'IDENTIFIER_TYPE', 'EG_UNIFIED_CR', 'ACQUIRE_MODE', 'MANUAL_ENTRY', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900205, 'IDENTIFIER_TYPE', 'EG_UNIFIED_CR', 'VAULT_REQUIRED', '0', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900206, 'IDENTIFIER_TYPE', 'EG_UNIFIED_CR', 'EXPIRY_REQUIRED', '0', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900207, 'IDENTIFIER_TYPE', 'EG_LOCAL_CR', 'ACQUIRE_MODE', 'MANUAL_ENTRY', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900208, 'IDENTIFIER_TYPE', 'EG_LOCAL_CR', 'VAULT_REQUIRED', '0', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900209, 'IDENTIFIER_TYPE', 'EG_LOCAL_CR', 'EXPIRY_REQUIRED', '0', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900210, 'DECLARATION', 'PLATFORM_TERMS_ACCEPTANCE', 'DOCUMENT_VERSION', '2026-10-draft', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900211, 'DECLARATION', 'PRIVACY_NOTICE_ACKNOWLEDGED', 'DOCUMENT_VERSION', '2026-10-draft', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900212, 'ATTRIBUTE', 'SA_FREELANCE_DOCUMENT_EXPIRY_DATE', 'DATE_NOT_PAST', '1', 'ACTIVE', '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000');

-- t_onboarding_schema（9 行：8 个 v2 + SA 自由职业者 v1）
INSERT INTO `tmp_legal_onboarding_schema` (`schema_id`, `jurisdiction_code`, `party_type`, `role_type`, `organization_type_code`, `version_no`, `status`, `active_slot`, `approved_by`, `approved_time`, `create_time`, `update_time`) VALUES
  (9270000000900301, 'AE', 'ORGANIZATION', 'CLIENT', '*', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900302, 'AE', 'ORGANIZATION', 'CLIENT', 'FZ_LLC', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900303, 'AE', 'INDIVIDUAL', 'CLIENT', '*', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900304, 'AE', 'INDIVIDUAL', 'CLIENT', 'SOLE_ESTABLISHMENT', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900305, 'SA', 'ORGANIZATION', 'CLIENT', '*', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900306, 'SA', 'INDIVIDUAL', 'CLIENT', '*', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900307, 'EG', 'ORGANIZATION', 'CLIENT', '*', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900308, 'EG', 'INDIVIDUAL', 'CLIENT', '*', 2, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000900309, 'SA', 'INDIVIDUAL', 'CLIENT', 'FREELANCER', 1, IF(@cm_legal_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_legal_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_legal_status = 'ACTIVE', '2026-10-27 00:00:00.000000', NULL), '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000');

-- v1 → v2 删除的字段（按 schema_id + field_key + applies_to_subject）
INSERT INTO `tmp_legal_removed_field` (`schema_id`, `field_key`, `applies_to_subject`) VALUES
    (9270000000700001, 'declaration.PLATFORM_TERMS', 'PRIMARY'),
    (9270000000700002, 'declaration.PLATFORM_TERMS', 'PRIMARY'),
    (9270000000700003, 'declaration.PLATFORM_TERMS', 'PRIMARY'),
    (9270000000700004, 'declaration.PLATFORM_TERMS', 'PRIMARY'),
    (9270000000700005, 'declaration.PLATFORM_TERMS', 'PRIMARY'),
    (9270000000700005, 'organization.licenseExpiryDate', 'PRIMARY'),
    (9270000000700006, 'declaration.PLATFORM_TERMS', 'PRIMARY'),
    (9270000000700007, 'declaration.PLATFORM_TERMS', 'PRIMARY'),
    (9270000000700007, 'identifier.EG_COMMERCIAL_REGISTER', 'PRIMARY'),
    (9270000000700008, 'declaration.PLATFORM_TERMS', 'PRIMARY');

-- v2 字段 1/3：复制线上 v1 字段（去掉删除项），ID +110000，schema_id +200300
INSERT INTO `tmp_legal_onboarding_schema_field` (`schema_field_id`, `schema_id`, `section`, `display_group`, `field_key`, `widget_code`, `catalog_code`, `requirement_level`, `requirement_group`, `applies_to_subject`, `validation_pattern`, `min_length`, `max_length`, `visible_when`, `label_key`, `help_key`, `display_order`, `create_time`, `update_time`)
SELECT f.`schema_field_id` + 110000, f.`schema_id` + 200300, f.`section`, f.`display_group`, f.`field_key`, f.`widget_code`, f.`catalog_code`,
       f.`requirement_level`, f.`requirement_group`, f.`applies_to_subject`, f.`validation_pattern`, f.`min_length`, f.`max_length`,
       f.`visible_when`, f.`label_key`, f.`help_key`, f.`display_order`, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'
  FROM `t_onboarding_schema_field` f
 WHERE f.`schema_id` IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008)
   AND NOT EXISTS (SELECT 1 FROM `tmp_legal_removed_field` r
                    WHERE r.`schema_id` = f.`schema_id` AND r.`field_key` = f.`field_key` AND r.`applies_to_subject` = f.`applies_to_subject`);

-- v2 字段 2/3：新增字段（32 行，含自由职业者专属）
INSERT INTO `tmp_legal_onboarding_schema_field` (`schema_field_id`, `schema_id`, `section`, `display_group`, `field_key`, `widget_code`, `catalog_code`, `requirement_level`, `requirement_group`, `applies_to_subject`, `validation_pattern`, `min_length`, `max_length`, `visible_when`, `label_key`, `help_key`, `display_order`, `create_time`, `update_time`) VALUES
  (9270000000920101, 9270000000900301, 'BASIC', 'REGISTRATION', 'attributes.AE_REGISTRATION_AUTHORITY_NAME', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 128, 'organization.registrationAuthorityCode=AE_OTHER', 'onboardingSchema.attribute.AE_REGISTRATION_AUTHORITY_NAME', NULL, 1065, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920102, 9270000000900301, 'BASIC', 'REGISTRATION', 'attributes.AE_ERN', 'TEXT', NULL, 'OPTIONAL', NULL, 'PRIMARY', '[A-Z0-9-]{1,30}', NULL, 30, NULL, 'onboardingSchema.attribute.AE_ERN', NULL, 1085, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920103, 9270000000900301, 'DECLARATIONS', 'DECLARATIONS', 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.RELATED_PERSON_DATA_AUTHORITY', NULL, 5365, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920104, 9270000000900301, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5390, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920105, 9270000000900301, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5395, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920201, 9270000000900302, 'BASIC', 'REGISTRATION', 'attributes.AE_REGISTRATION_AUTHORITY_NAME', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 128, 'organization.registrationAuthorityCode=AE_OTHER', 'onboardingSchema.attribute.AE_REGISTRATION_AUTHORITY_NAME', NULL, 1065, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920202, 9270000000900302, 'BASIC', 'REGISTRATION', 'attributes.AE_ERN', 'TEXT', NULL, 'OPTIONAL', NULL, 'PRIMARY', '[A-Z0-9-]{1,30}', NULL, 30, NULL, 'onboardingSchema.attribute.AE_ERN', NULL, 1085, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920203, 9270000000900302, 'DECLARATIONS', 'DECLARATIONS', 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.RELATED_PERSON_DATA_AUTHORITY', NULL, 5375, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920204, 9270000000900302, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5400, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920205, 9270000000900302, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5405, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920301, 9270000000900303, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5280, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920302, 9270000000900303, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5285, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920401, 9270000000900304, 'BASIC', 'LICENSE', 'attributes.AE_LICENSE_ERN', 'TEXT', NULL, 'OPTIONAL', NULL, 'PRIMARY', '[A-Z0-9-]{1,30}', NULL, 30, NULL, 'onboardingSchema.attribute.AE_LICENSE_ERN', NULL, 1175, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920402, 9270000000900304, 'BASIC', 'LICENSE', 'attributes.AE_LICENSE_AUTHORITY_NAME', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 128, 'attributes.AE_LICENSE_AUTHORITY=AE_OTHER', 'onboardingSchema.attribute.AE_LICENSE_AUTHORITY_NAME', NULL, 1185, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920403, 9270000000900304, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5310, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920404, 9270000000900304, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5315, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920501, 9270000000900305, 'BASIC', 'REGISTRATION', 'attributes.SA_CR_ANNUAL_CONFIRMATION_DATE', 'DATE', NULL, 'OPTIONAL', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.attribute.SA_CR_ANNUAL_CONFIRMATION_DATE', NULL, 1070, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920502, 9270000000900305, 'DECLARATIONS', 'DECLARATIONS', 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.RELATED_PERSON_DATA_AUTHORITY', NULL, 5385, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920503, 9270000000900305, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5410, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920504, 9270000000900305, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5415, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920601, 9270000000900306, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5290, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920602, 9270000000900306, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5295, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920701, 9270000000900307, 'BASIC', 'IDENTIFIERS', 'identifier.EG_UNIFIED_CR', 'IDENTIFIER', NULL, 'REQUIRED', 'ORGANIZATION_ID', 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.identifierType.EG_UNIFIED_CR', NULL, 1090, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920702, 9270000000900307, 'BASIC', 'IDENTIFIERS', 'identifier.EG_LOCAL_CR', 'IDENTIFIER', NULL, 'REQUIRED', 'ORGANIZATION_ID', 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.identifierType.EG_LOCAL_CR', NULL, 1095, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920703, 9270000000900307, 'BASIC', 'TAX', 'attributes.EG_VAT_REGISTRATION_NUMBER', 'TEXT', NULL, 'OPTIONAL', NULL, 'PRIMARY', '[A-Z0-9-]{1,30}', NULL, 30, NULL, 'onboardingSchema.attribute.EG_VAT_REGISTRATION_NUMBER', NULL, 1105, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920704, 9270000000900307, 'DECLARATIONS', 'DECLARATIONS', 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.RELATED_PERSON_DATA_AUTHORITY', NULL, 5365, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920705, 9270000000900307, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5390, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920706, 9270000000900307, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5395, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920801, 9270000000900308, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PLATFORM_TERMS_ACCEPTANCE', NULL, 5280, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000920802, 9270000000900308, 'DECLARATIONS', 'DECLARATIONS', 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'DECLARATION', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.declaration.PRIVACY_NOTICE_ACKNOWLEDGED', NULL, 5285, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000950001, 9270000000900309, 'BASIC', 'LICENSE', 'attributes.SA_FREELANCE_DOCUMENT_NUMBER', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '[Ff][Ll][ -]?[0-9A-Za-z]{1,30}', NULL, 34, NULL, 'onboardingSchema.attribute.SA_FREELANCE_DOCUMENT_NUMBER', NULL, 1125, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000'),
  (9270000000950002, 9270000000900309, 'BASIC', 'LICENSE', 'attributes.SA_FREELANCE_DOCUMENT_EXPIRY_DATE', 'DATE', NULL, 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.attribute.SA_FREELANCE_DOCUMENT_EXPIRY_DATE', NULL, 1126, '2026-10-27 00:00:00.000000', '2026-10-27 00:00:00.000000');

-- v2 字段 3/3：自由职业者 Schema = SA 个人 v2 字段复制（ID +20000）；临时表不能在同一语句中引用两次，先经中转表
INSERT INTO `tmp_legal_freelancer_field` (`schema_field_id`, `schema_id`, `section`, `display_group`, `field_key`, `widget_code`, `catalog_code`, `requirement_level`, `requirement_group`, `applies_to_subject`, `validation_pattern`, `min_length`, `max_length`, `visible_when`, `label_key`, `help_key`, `display_order`, `create_time`, `update_time`)
SELECT `schema_field_id` + 20000, 9270000000900309, `section`, `display_group`, `field_key`, `widget_code`, `catalog_code`,
       `requirement_level`, `requirement_group`, `applies_to_subject`, `validation_pattern`, `min_length`, `max_length`,
       `visible_when`, `label_key`, `help_key`, `display_order`, `create_time`, `update_time`
  FROM `tmp_legal_onboarding_schema_field` WHERE `schema_id` = 9270000000900306;
INSERT INTO `tmp_legal_onboarding_schema_field` SELECT * FROM `tmp_legal_freelancer_field`;

-- ===== 2. 预检（只读）=====
SET @cm_legal_v1_schemas = (SELECT COUNT(*) FROM `t_onboarding_schema` WHERE `version_no` = 1 AND `role_type` = 'CLIENT' AND (`schema_id`, `jurisdiction_code`, `party_type`, `organization_type_code`) IN (
    (9270000000700001, 'AE', 'ORGANIZATION', '*'),
    (9270000000700002, 'AE', 'ORGANIZATION', 'FZ_LLC'),
    (9270000000700003, 'AE', 'INDIVIDUAL', '*'),
    (9270000000700004, 'AE', 'INDIVIDUAL', 'SOLE_ESTABLISHMENT'),
    (9270000000700005, 'SA', 'ORGANIZATION', '*'),
    (9270000000700006, 'SA', 'INDIVIDUAL', '*'),
    (9270000000700007, 'EG', 'ORGANIZATION', '*'),
    (9270000000700008, 'EG', 'INDIVIDUAL', '*')));
SET @cm_legal_foreign_field_ids = (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_id` IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008)
    AND `schema_field_id` NOT BETWEEN 9270000000800001 AND 9270000000809999);
SET @cm_legal_added_in_v1 = (SELECT COUNT(*) FROM `t_onboarding_schema_field` f
    WHERE f.`schema_id` IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008) AND (f.`schema_id` + 200300, f.`field_key`, f.`applies_to_subject`) IN (
      (9270000000900301, 'attributes.AE_REGISTRATION_AUTHORITY_NAME', 'PRIMARY'), (9270000000900301, 'attributes.AE_ERN', 'PRIMARY'), (9270000000900301, 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'PRIMARY'), (9270000000900301, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900301, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY'), (9270000000900302, 'attributes.AE_REGISTRATION_AUTHORITY_NAME', 'PRIMARY'), (9270000000900302, 'attributes.AE_ERN', 'PRIMARY'), (9270000000900302, 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'PRIMARY'), (9270000000900302, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900302, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY'), (9270000000900303, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900303, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY'), (9270000000900304, 'attributes.AE_LICENSE_ERN', 'PRIMARY'), (9270000000900304, 'attributes.AE_LICENSE_AUTHORITY_NAME', 'PRIMARY'), (9270000000900304, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900304, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY'), (9270000000900305, 'attributes.SA_CR_ANNUAL_CONFIRMATION_DATE', 'PRIMARY'), (9270000000900305, 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'PRIMARY'), (9270000000900305, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900305, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY'), (9270000000900306, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900306, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY'), (9270000000900307, 'identifier.EG_UNIFIED_CR', 'PRIMARY'), (9270000000900307, 'identifier.EG_LOCAL_CR', 'PRIMARY'), (9270000000900307, 'attributes.EG_VAT_REGISTRATION_NUMBER', 'PRIMARY'), (9270000000900307, 'declaration.RELATED_PERSON_DATA_AUTHORITY', 'PRIMARY'), (9270000000900307, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900307, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY'), (9270000000900308, 'declaration.PLATFORM_TERMS_ACCEPTANCE', 'PRIMARY'), (9270000000900308, 'declaration.PRIVACY_NOTICE_ACKNOWLEDGED', 'PRIMARY')));
SET @cm_legal_conflicts = 0;
SET @cm_legal_conflicts = @cm_legal_conflicts + (SELECT COUNT(*) FROM `t_identifier_type` x JOIN `tmp_legal_identifier_type` s ON x.`identifier_type_code` <=> s.`identifier_type_code` AND x.`identifier_type_id` <> s.`identifier_type_id`);
SET @cm_legal_conflicts = @cm_legal_conflicts + (SELECT COUNT(*) FROM `t_rule_setting` x JOIN `tmp_legal_rule_setting` s ON x.`scope_type` <=> s.`scope_type` AND x.`scope_code` <=> s.`scope_code` AND x.`rule_code` <=> s.`rule_code` AND x.`rule_setting_id` <> s.`rule_setting_id`);
SET @cm_legal_conflicts = @cm_legal_conflicts + (SELECT COUNT(*) FROM `t_catalog_item` x JOIN `tmp_legal_catalog_item` s ON x.`catalog_code` <=> s.`catalog_code` AND x.`jurisdiction_code` <=> s.`jurisdiction_code` AND x.`item_code` <=> s.`item_code` AND x.`catalog_item_id` <> s.`catalog_item_id`);
SET @cm_legal_conflicts = @cm_legal_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema` x JOIN `tmp_legal_onboarding_schema` s ON x.`jurisdiction_code` <=> s.`jurisdiction_code` AND x.`party_type` <=> s.`party_type` AND x.`role_type` <=> s.`role_type` AND x.`organization_type_code` <=> s.`organization_type_code` AND x.`version_no` <=> s.`version_no` AND x.`schema_id` <> s.`schema_id`);
-- 活动槽位：v1 将在同一事务内退役，不计为冲突
SET @cm_legal_conflicts = @cm_legal_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema` x JOIN `tmp_legal_onboarding_schema` s ON x.`jurisdiction_code` <=> s.`jurisdiction_code` AND x.`party_type` <=> s.`party_type` AND x.`role_type` <=> s.`role_type` AND x.`organization_type_code` <=> s.`organization_type_code` AND x.`active_slot` <=> s.`active_slot` AND x.`schema_id` <> s.`schema_id` AND s.`active_slot` IS NOT NULL AND x.`schema_id` NOT IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008));
SET @cm_legal_conflicts = @cm_legal_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema_field` x JOIN `tmp_legal_onboarding_schema_field` s ON x.`schema_id` <=> s.`schema_id` AND x.`field_key` <=> s.`field_key` AND x.`applies_to_subject` <=> s.`applies_to_subject` AND x.`schema_field_id` <> s.`schema_field_id`);
SELECT @cm_legal_status AS legal_status, @cm_legal_v1_schemas AS v1_schemas_expect_8, @cm_legal_foreign_field_ids AS v1_fields_outside_id_range_expect_0,
       @cm_legal_added_in_v1 AS added_keys_already_in_v1_expect_0, @cm_legal_conflicts AS unique_key_conflicts_expect_0;
-- 已存在的修订行（首次执行期望全 0，重跑期望等于 pack_rows）
SELECT 't_identifier_type' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_identifier_type` x WHERE x.`identifier_type_id` = s.`identifier_type_id`)) AS present_rows FROM `tmp_legal_identifier_type` s;
SELECT 't_attribute_definition' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_attribute_definition` x WHERE x.`attribute_code` = s.`attribute_code`)) AS present_rows FROM `tmp_legal_attribute_definition` s;
SELECT 't_catalog_item' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_catalog_item` x WHERE x.`catalog_item_id` = s.`catalog_item_id`)) AS present_rows FROM `tmp_legal_catalog_item` s;
SELECT 't_rule_setting' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_rule_setting` x WHERE x.`rule_setting_id` = s.`rule_setting_id`)) AS present_rows FROM `tmp_legal_rule_setting` s;
SELECT 't_onboarding_schema' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema` x WHERE x.`schema_id` = s.`schema_id`)) AS present_rows FROM `tmp_legal_onboarding_schema` s;
SELECT 't_onboarding_schema_field' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema_field` x WHERE x.`schema_field_id` = s.`schema_field_id`)) AS present_rows FROM `tmp_legal_onboarding_schema_field` s;
SELECT `schema_id`, COUNT(*) AS v2_fields FROM `tmp_legal_onboarding_schema_field` GROUP BY `schema_id` ORDER BY `schema_id`;

DROP PROCEDURE IF EXISTS `cm_legal_assert`;
DELIMITER //
CREATE PROCEDURE `cm_legal_assert`()
BEGIN
  IF @cm_legal_status NOT IN ('ACTIVE', 'DRAFT') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: @cm_legal_status must be ACTIVE or DRAFT';
  END IF;
  IF @cm_legal_v1_schemas <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: the 8 country-pack v1 schemas (9270000000700001-008) are missing or changed';
  END IF;
  IF @cm_legal_foreign_field_ids <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a v1 schema field id is outside 9270000000800001-809999; the +110000 copy mapping would leave the ID range';
  END IF;
  IF @cm_legal_added_in_v1 <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a field added by this revision already exists in the live v1 schema';
  END IF;
  IF @cm_legal_conflicts <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a business unique key of the revision is taken by a row outside the revision ID range';
  END IF;
END //
DELIMITER ;
CALL `cm_legal_assert`();
DROP PROCEDURE IF EXISTS `cm_legal_assert`;

-- ===== 3. 写入（一个事务；已存在的行不变）=====
START TRANSACTION;
INSERT INTO `t_identifier_type` SELECT * FROM `tmp_legal_identifier_type` ON DUPLICATE KEY UPDATE `update_time` = `t_identifier_type`.`update_time`;
INSERT INTO `t_attribute_definition` SELECT * FROM `tmp_legal_attribute_definition` ON DUPLICATE KEY UPDATE `update_time` = `t_attribute_definition`.`update_time`;
INSERT INTO `t_catalog_item` SELECT * FROM `tmp_legal_catalog_item` ON DUPLICATE KEY UPDATE `update_time` = `t_catalog_item`.`update_time`;
INSERT INTO `t_rule_setting` SELECT * FROM `tmp_legal_rule_setting` ON DUPLICATE KEY UPDATE `update_time` = `t_rule_setting`.`update_time`;
-- 先退役 v1 释放 uk_schema_active 槽位，再写 v2（仅 ACTIVE 口径）
UPDATE `t_onboarding_schema` SET `status` = 'RETIRED', `active_slot` = NULL, `update_time` = UTC_TIMESTAMP(6)
 WHERE `schema_id` IN (9270000000700001, 9270000000700002, 9270000000700003, 9270000000700004, 9270000000700005, 9270000000700006, 9270000000700007, 9270000000700008) AND `status` IN ('ACTIVE', 'DRAFT') AND @cm_legal_status = 'ACTIVE';
INSERT INTO `t_onboarding_schema` SELECT * FROM `tmp_legal_onboarding_schema` ON DUPLICATE KEY UPDATE `update_time` = `t_onboarding_schema`.`update_time`;
-- 先前以 DRAFT 写入的本段 Schema 与目录项，在 ACTIVE 口径下发布（人工改为 RETIRED 的行不动）
UPDATE `t_onboarding_schema` SET `status` = 'ACTIVE', `active_slot` = 1, `approved_time` = UTC_TIMESTAMP(6), `update_time` = UTC_TIMESTAMP(6)
 WHERE `schema_id` BETWEEN 9270000000900000 AND 9270000000999999 AND `status` = 'DRAFT' AND @cm_legal_status = 'ACTIVE';
UPDATE `t_catalog_item` SET `status` = 'ACTIVE', `update_time` = UTC_TIMESTAMP(6)
 WHERE `catalog_item_id` BETWEEN 9270000000900000 AND 9270000000999999 AND `status` = 'DRAFT' AND @cm_legal_status = 'ACTIVE';
INSERT INTO `t_onboarding_schema_field` SELECT * FROM `tmp_legal_onboarding_schema_field` ON DUPLICATE KEY UPDATE `update_time` = `t_onboarding_schema_field`.`update_time`;
COMMIT;

-- ===== 4. 核验（只读）：present_rows 必须等于 pack_rows；v2 字段数与第 2 节一致 =====
SELECT 't_identifier_type' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_identifier_type` x WHERE x.`identifier_type_id` = s.`identifier_type_id`)) AS present_rows FROM `tmp_legal_identifier_type` s;
SELECT 't_attribute_definition' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_attribute_definition` x WHERE x.`attribute_code` = s.`attribute_code`)) AS present_rows FROM `tmp_legal_attribute_definition` s;
SELECT 't_catalog_item' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_catalog_item` x WHERE x.`catalog_item_id` = s.`catalog_item_id`)) AS present_rows FROM `tmp_legal_catalog_item` s;
SELECT 't_rule_setting' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_rule_setting` x WHERE x.`rule_setting_id` = s.`rule_setting_id`)) AS present_rows FROM `tmp_legal_rule_setting` s;
SELECT 't_onboarding_schema' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema` x WHERE x.`schema_id` = s.`schema_id`)) AS present_rows FROM `tmp_legal_onboarding_schema` s;
SELECT 't_onboarding_schema_field' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema_field` x WHERE x.`schema_field_id` = s.`schema_field_id`)) AS present_rows FROM `tmp_legal_onboarding_schema_field` s;
SELECT s.`schema_id`, s.`jurisdiction_code`, s.`party_type`, s.`organization_type_code`, s.`version_no`, s.`status`, s.`active_slot`,
       (SELECT COUNT(*) FROM `t_onboarding_schema_field` f WHERE f.`schema_id` = s.`schema_id`) AS fields
  FROM `t_onboarding_schema` s ORDER BY s.`jurisdiction_code`, s.`party_type`, s.`organization_type_code`, s.`version_no`;
SELECT `field_key`, COUNT(*) AS `schema_count` FROM `t_onboarding_schema_field` WHERE `schema_id` BETWEEN 9270000000900000 AND 9270000000999999 AND `field_key` LIKE 'declaration.%' GROUP BY `field_key` ORDER BY `field_key`;

DROP TEMPORARY TABLE IF EXISTS `tmp_legal_identifier_type`, `tmp_legal_attribute_definition`, `tmp_legal_catalog_item`, `tmp_legal_rule_setting`, `tmp_legal_onboarding_schema`, `tmp_legal_onboarding_schema_field`, `tmp_legal_removed_field`, `tmp_legal_freelancer_field`;
