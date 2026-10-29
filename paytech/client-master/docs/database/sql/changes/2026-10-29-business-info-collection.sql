-- 2026-10-29 client-master 业务信息补采（REQ-12）：采集 Schema v3 增量 DML。
-- 需求：requirements/20261028/client-master-业务信息补采/requirement.md R2、technique.md 第 9.4 节。
-- 内容（固定 ID 段 9281200000000000—9281200000999999，不与国家包 9270000000… 段重叠）：
--   t_catalog_item +4：client-master 自有目录 FULFILLMENT_TYPE（INSTANT、PRE_ORDER、SUBSCRIPTION、SERVICE；辖区 *，client_types 为空），
--     ID 9281200000600001—600004；
--   t_onboarding_schema +9：8 个 v2 与 SA / INDIVIDUAL / FREELANCER v1 各发布下一版本（ID = 旧版本 + 11200000000000，即 9281200000900301—309；
--     version_no = 旧版本 + 1），旧版本改为 RETIRED、active_slot 置空（9 行更新）；
--   t_onboarding_schema_field：v3 字段 = 执行时线上旧版本字段的复制（ID = 旧字段 + 11200000000000，保留线上取值），再每个 Schema 加 4 行
--     （ID 9281200000800000 + 序号 × 10 + n，序号 1—9 对应 Schema 末三位 301—309，n = 1—4）。按初始化 DML 计 327 + 36 = 363 行。
--   新增字段（section=BUSINESS，applies_to_subject=PRIMARY，display_order = 该 Schema BUSINESS 分区最大序号 + 10 / 20 / 30 / 40）：
--     business.expectedPaymentMethods：MULTI_SELECT，目录 COMMERCIAL_PAYMENT_METHOD（commercial 产品能力目录，外部目录），REQUIRED，组 OPERATIONS；
--     business.refundPolicyUrl：URL，正则 ^https://\S+$，机构 Schema EVENTUALLY、个人 Schema（含 AE 个人独资、SA 自由职业者）OPTIONAL，组 OPERATIONS；
--     business.fulfillmentType：SELECT，目录 FULFILLMENT_TYPE，REQUIRED，组 FULFILLMENT（新组）；
--     business.fulfillmentDays：TEXT，正则 1—365，REQUIRED，visible_when business.fulfillmentType=PRE_ORDER，组 FULFILLMENT。
--   本文件与 client-master-initial-data.sql 的最终状态一致；空库仍用初始化 DML。
-- 前置：
--   1. 已执行 changes/2026-10-29-business-info-collection-ddl.sql（t_client_business_profile 4 列；停止条件 7 核对）。
--   2. 已部署认识 MULTI_SELECT 与 COMMERCIAL_PAYMENT_METHOD 的 client-master 新镜像（旧镜像按枚举映射 widget_code，读到 MULTI_SELECT 会失败）。
--   3. 已部署认识 MULTI_SELECT 的 client-console-web 与 operations-console-web，并发布含新文案键的两个语言包（旧门户无法渲染多选控件）。
--   4. 已执行 2026-10-27 国家包法务修订（v2 与 SA 自由职业者 v1 为 ACTIVE）。
-- 状态口径：@cm_bizinfo_status 缺省 ACTIVE（本地与 mor-test 验收）：v3 以 ACTIVE 发布、旧版本退役、FULFILLMENT_TYPE 目录项 ACTIVE。
--   生产或需先写入核对时，同一会话先 SET @cm_bizinfo_status = 'DRAFT';：v3 与目录项以 DRAFT 写入，旧版本保持不变；
--   之后以 ACTIVE 重跑本文件即发布（退役旧版本，把本 ID 段内的 DRAFT Schema 与目录项改为 ACTIVE，approved_time 写执行时间）。
-- 执行顺序：DDL → client-master 新镜像 → client-console-web、operations-console-web → 两个语言包 → 本文件（technique 第 13 节）。
-- 幂等：静态行与复制行先写会话临时表，再 INSERT … SELECT … ON DUPLICATE KEY UPDATE（不改已存在行）；退役与发布按当前状态条件更新；
--   重跑为空操作（旧版本退役后复制仍取旧版本字段，按主键去重）。
-- 停止条件（cm_bizinfo_assert SIGNAL，尚未写入）：
--   1. @cm_bizinfo_status 不是 ACTIVE / DRAFT；
--   2. 9 个旧版本 Schema 缺失、键或版本号不符，或不是 ACTIVE（ACTIVE 重跑时允许旧版本已 RETIRED 且对应 v3 已 ACTIVE）；
--   3. 待复制的旧版本字段 ID 不在 9270000000900000—9270000000999999（否则 + 11200000000000 会越出本段）；
--   4. 新增 4 个 field_key 已出现在任一旧版本 Schema（复制后会重复）；
--   5. 本 ID 段内已有非本脚本的行，或业务唯一键被本 ID 段以外的行占用；
--   6. t_catalog_item 已有本脚本以外的 FULFILLMENT_TYPE 行，或出现 COMMERCIAL_ 开头的目录码；
--   7. t_client_business_profile 缺少 4 个新列（DDL 未执行）。
-- 回退：changes/2026-10-29-business-info-collection-rollback.sql（存在引用 v3 的申请或已填写交付方式的资料时拒绝执行）。
-- 新语言包键（随两个语言包发布，en / zh-CN）：onboardingSchema.group.FULFILLMENT、onboardingSchema.field.business.*（4 项）、
--   onboardingSchema.help.business.*（4 项）、onboardingSchema.catalog.FULFILLMENT_TYPE.*（4 项）、onboardingSchema.paymentMethod.<capability_code>。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @cm_bizinfo_status = COALESCE(@cm_bizinfo_status, 'ACTIVE');

DROP TEMPORARY TABLE IF EXISTS `tmp_bizinfo_catalog_item`, `tmp_bizinfo_onboarding_schema`, `tmp_bizinfo_onboarding_schema_field`;
CREATE TEMPORARY TABLE `tmp_bizinfo_catalog_item` LIKE `t_catalog_item`;
CREATE TEMPORARY TABLE `tmp_bizinfo_onboarding_schema` LIKE `t_onboarding_schema`;
CREATE TEMPORARY TABLE `tmp_bizinfo_onboarding_schema_field` LIKE `t_onboarding_schema_field`;

-- ===== 1. 发布行（写入会话临时表）=====
-- t_catalog_item（4 行：交付方式目录）
INSERT INTO `tmp_bizinfo_catalog_item` (`catalog_item_id`, `catalog_code`, `item_code`, `jurisdiction_code`, `label_key`, `client_types`, `display_order`, `status`, `create_time`, `update_time`) VALUES
  (9281200000600001, 'FULFILLMENT_TYPE', 'INSTANT', '*', 'onboardingSchema.catalog.FULFILLMENT_TYPE.INSTANT', NULL, 10, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000600002, 'FULFILLMENT_TYPE', 'PRE_ORDER', '*', 'onboardingSchema.catalog.FULFILLMENT_TYPE.PRE_ORDER', NULL, 20, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000600003, 'FULFILLMENT_TYPE', 'SUBSCRIPTION', '*', 'onboardingSchema.catalog.FULFILLMENT_TYPE.SUBSCRIPTION', NULL, 30, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000600004, 'FULFILLMENT_TYPE', 'SERVICE', '*', 'onboardingSchema.catalog.FULFILLMENT_TYPE.SERVICE', NULL, 40, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000');

-- t_onboarding_schema（9 行：8 个 v3 + SA 自由职业者 v2）
INSERT INTO `tmp_bizinfo_onboarding_schema` (`schema_id`, `jurisdiction_code`, `party_type`, `role_type`, `organization_type_code`, `version_no`, `status`, `active_slot`, `approved_by`, `approved_time`, `create_time`, `update_time`) VALUES
  (9281200000900301, 'AE', 'ORGANIZATION', 'CLIENT', '*', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900302, 'AE', 'ORGANIZATION', 'CLIENT', 'FZ_LLC', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900303, 'AE', 'INDIVIDUAL', 'CLIENT', '*', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900304, 'AE', 'INDIVIDUAL', 'CLIENT', 'SOLE_ESTABLISHMENT', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900305, 'SA', 'ORGANIZATION', 'CLIENT', '*', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900306, 'SA', 'INDIVIDUAL', 'CLIENT', '*', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900307, 'EG', 'ORGANIZATION', 'CLIENT', '*', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900308, 'EG', 'INDIVIDUAL', 'CLIENT', '*', 3, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000900309, 'SA', 'INDIVIDUAL', 'CLIENT', 'FREELANCER', 2, IF(@cm_bizinfo_status = 'ACTIVE', 'ACTIVE', 'DRAFT'), IF(@cm_bizinfo_status = 'ACTIVE', 1, NULL), NULL, IF(@cm_bizinfo_status = 'ACTIVE', '2026-10-29 00:00:00.000000', NULL), '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000');

-- v3 字段 1/2：复制线上旧版本字段，ID 与 schema_id 各 + 11200000000000
INSERT INTO `tmp_bizinfo_onboarding_schema_field` (`schema_field_id`, `schema_id`, `section`, `display_group`, `field_key`, `widget_code`, `catalog_code`, `requirement_level`, `requirement_group`, `applies_to_subject`, `validation_pattern`, `min_length`, `max_length`, `visible_when`, `label_key`, `help_key`, `display_order`, `create_time`, `update_time`)
SELECT f.`schema_field_id` + 11200000000000, f.`schema_id` + 11200000000000, f.`section`, f.`display_group`, f.`field_key`, f.`widget_code`,
       f.`catalog_code`, f.`requirement_level`, f.`requirement_group`, f.`applies_to_subject`, f.`validation_pattern`, f.`min_length`,
       f.`max_length`, f.`visible_when`, f.`label_key`, f.`help_key`, f.`display_order`, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'
  FROM `t_onboarding_schema_field` f
 WHERE f.`schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309);

-- v3 字段 2/2：新增字段（36 行，每个 Schema 4 行）
INSERT INTO `tmp_bizinfo_onboarding_schema_field` (`schema_field_id`, `schema_id`, `section`, `display_group`, `field_key`, `widget_code`, `catalog_code`, `requirement_level`, `requirement_group`, `applies_to_subject`, `validation_pattern`, `min_length`, `max_length`, `visible_when`, `label_key`, `help_key`, `display_order`, `create_time`, `update_time`) VALUES
  (9281200000800011, 9281200000900301, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2200, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800012, 9281200000900301, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'EVENTUALLY', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2210, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800013, 9281200000900301, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2220, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800014, 9281200000900301, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2230, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800021, 9281200000900302, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2210, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800022, 9281200000900302, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'EVENTUALLY', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2220, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800023, 9281200000900302, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2230, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800024, 9281200000900302, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2240, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800031, 9281200000900303, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2250, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800032, 9281200000900303, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'OPTIONAL', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2260, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800033, 9281200000900303, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2270, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800034, 9281200000900303, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2280, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800041, 9281200000900304, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2280, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800042, 9281200000900304, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'OPTIONAL', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2290, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800043, 9281200000900304, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2300, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800044, 9281200000900304, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2310, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800051, 9281200000900305, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2210, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800052, 9281200000900305, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'EVENTUALLY', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2220, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800053, 9281200000900305, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2230, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800054, 9281200000900305, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2240, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800061, 9281200000900306, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2260, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800062, 9281200000900306, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'OPTIONAL', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2270, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800063, 9281200000900306, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2280, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800064, 9281200000900306, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2290, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800071, 9281200000900307, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2200, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800072, 9281200000900307, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'EVENTUALLY', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2210, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800073, 9281200000900307, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2220, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800074, 9281200000900307, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2230, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800081, 9281200000900308, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2250, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800082, 9281200000900308, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'OPTIONAL', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2260, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800083, 9281200000900308, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2270, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800084, 9281200000900308, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2280, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800091, 9281200000900309, 'BUSINESS', 'OPERATIONS', 'business.expectedPaymentMethods', 'MULTI_SELECT', 'COMMERCIAL_PAYMENT_METHOD', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, 512, NULL, 'onboardingSchema.field.business.expectedPaymentMethods', 'onboardingSchema.help.business.expectedPaymentMethods', 2260, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800092, 9281200000900309, 'BUSINESS', 'OPERATIONS', 'business.refundPolicyUrl', 'URL', NULL, 'OPTIONAL', NULL, 'PRIMARY', '^https://\\S+$', NULL, 2048, NULL, 'onboardingSchema.field.business.refundPolicyUrl', 'onboardingSchema.help.business.refundPolicyUrl', 2270, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800093, 9281200000900309, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentType', 'SELECT', 'FULFILLMENT_TYPE', 'REQUIRED', NULL, 'PRIMARY', NULL, NULL, NULL, NULL, 'onboardingSchema.field.business.fulfillmentType', 'onboardingSchema.help.business.fulfillmentType', 2280, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000'),
  (9281200000800094, 9281200000900309, 'BUSINESS', 'FULFILLMENT', 'business.fulfillmentDays', 'TEXT', NULL, 'REQUIRED', NULL, 'PRIMARY', '^(36[0-5]|3[0-5][0-9]|[12][0-9]{2}|[1-9][0-9]?)$', NULL, 3, 'business.fulfillmentType=PRE_ORDER', 'onboardingSchema.field.business.fulfillmentDays', 'onboardingSchema.help.business.fulfillmentDays', 2290, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000');

-- ===== 2. 预检（只读）=====
SET @cm_bizinfo_v2_keyed = (SELECT COUNT(*) FROM `t_onboarding_schema` WHERE `role_type` = 'CLIENT' AND (`schema_id`, `jurisdiction_code`, `party_type`, `organization_type_code`, `version_no`) IN (
    (9270000000900301, 'AE', 'ORGANIZATION', '*', 2),
    (9270000000900302, 'AE', 'ORGANIZATION', 'FZ_LLC', 2),
    (9270000000900303, 'AE', 'INDIVIDUAL', '*', 2),
    (9270000000900304, 'AE', 'INDIVIDUAL', 'SOLE_ESTABLISHMENT', 2),
    (9270000000900305, 'SA', 'ORGANIZATION', '*', 2),
    (9270000000900306, 'SA', 'INDIVIDUAL', '*', 2),
    (9270000000900307, 'EG', 'ORGANIZATION', '*', 2),
    (9270000000900308, 'EG', 'INDIVIDUAL', '*', 2),
    (9270000000900309, 'SA', 'INDIVIDUAL', 'FREELANCER', 1)));
-- 旧版本须为 ACTIVE；已退役的只允许是本脚本 ACTIVE 发布后的重跑（对应 v3 已 ACTIVE）
SET @cm_bizinfo_v2_not_active = (SELECT COUNT(*) FROM `t_onboarding_schema` o WHERE o.`schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309) AND o.`status` <> 'ACTIVE'
    AND NOT EXISTS (SELECT 1 FROM `t_onboarding_schema` n WHERE n.`schema_id` = o.`schema_id` + 11200000000000 AND n.`status` = 'ACTIVE'));
SET @cm_bizinfo_foreign_field_ids = (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309)
    AND `schema_field_id` NOT BETWEEN 9270000000900000 AND 9270000000999999);
SET @cm_bizinfo_added_in_v2 = (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309)
    AND `field_key` IN ('business.expectedPaymentMethods', 'business.refundPolicyUrl', 'business.fulfillmentType', 'business.fulfillmentDays'));
SET @cm_bizinfo_conflicts = 0;
-- 业务唯一键被本 ID 段以外的行占用
SET @cm_bizinfo_conflicts = @cm_bizinfo_conflicts + (SELECT COUNT(*) FROM `t_catalog_item` x JOIN `tmp_bizinfo_catalog_item` s ON x.`catalog_code` <=> s.`catalog_code` AND x.`jurisdiction_code` <=> s.`jurisdiction_code` AND x.`item_code` <=> s.`item_code` AND x.`catalog_item_id` <> s.`catalog_item_id`);
SET @cm_bizinfo_conflicts = @cm_bizinfo_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema` x JOIN `tmp_bizinfo_onboarding_schema` s ON x.`jurisdiction_code` <=> s.`jurisdiction_code` AND x.`party_type` <=> s.`party_type` AND x.`role_type` <=> s.`role_type` AND x.`organization_type_code` <=> s.`organization_type_code` AND x.`version_no` <=> s.`version_no` AND x.`schema_id` <> s.`schema_id`);
-- 活动槽位：旧版本将在同一事务内退役，不计为冲突
SET @cm_bizinfo_conflicts = @cm_bizinfo_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema` x JOIN `tmp_bizinfo_onboarding_schema` s ON x.`jurisdiction_code` <=> s.`jurisdiction_code` AND x.`party_type` <=> s.`party_type` AND x.`role_type` <=> s.`role_type` AND x.`organization_type_code` <=> s.`organization_type_code` AND x.`active_slot` <=> s.`active_slot` AND x.`schema_id` <> s.`schema_id` AND s.`active_slot` IS NOT NULL AND x.`schema_id` NOT IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309));
SET @cm_bizinfo_conflicts = @cm_bizinfo_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema_field` x JOIN `tmp_bizinfo_onboarding_schema_field` s ON x.`schema_id` <=> s.`schema_id` AND x.`field_key` <=> s.`field_key` AND x.`applies_to_subject` <=> s.`applies_to_subject` AND x.`schema_field_id` <> s.`schema_field_id`);
-- 本 ID 段内的已有行须与本脚本同 ID 同业务键
SET @cm_bizinfo_conflicts = @cm_bizinfo_conflicts + (SELECT COUNT(*) FROM `t_catalog_item` x WHERE x.`catalog_item_id` BETWEEN 9281200000000000 AND 9281200000999999
    AND NOT EXISTS (SELECT 1 FROM `tmp_bizinfo_catalog_item` s WHERE s.`catalog_item_id` = x.`catalog_item_id` AND s.`catalog_code` = x.`catalog_code` AND s.`item_code` = x.`item_code` AND s.`jurisdiction_code` = x.`jurisdiction_code`));
SET @cm_bizinfo_conflicts = @cm_bizinfo_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema` x WHERE x.`schema_id` BETWEEN 9281200000000000 AND 9281200000999999
    AND NOT EXISTS (SELECT 1 FROM `tmp_bizinfo_onboarding_schema` s WHERE s.`schema_id` = x.`schema_id` AND s.`jurisdiction_code` = x.`jurisdiction_code` AND s.`party_type` = x.`party_type` AND s.`role_type` = x.`role_type` AND s.`organization_type_code` = x.`organization_type_code` AND s.`version_no` = x.`version_no`));
SET @cm_bizinfo_conflicts = @cm_bizinfo_conflicts + (SELECT COUNT(*) FROM `t_onboarding_schema_field` x WHERE x.`schema_field_id` BETWEEN 9281200000000000 AND 9281200000999999
    AND NOT EXISTS (SELECT 1 FROM `tmp_bizinfo_onboarding_schema_field` s WHERE s.`schema_field_id` = x.`schema_field_id` AND s.`schema_id` = x.`schema_id` AND s.`field_key` = x.`field_key` AND s.`applies_to_subject` = x.`applies_to_subject`));
SET @cm_bizinfo_catalog_foreign = (SELECT COUNT(*) FROM `t_catalog_item` x WHERE (x.`catalog_code` = 'FULFILLMENT_TYPE' AND x.`catalog_item_id` NOT BETWEEN 9281200000600001 AND 9281200000600004)
    OR x.`catalog_code` LIKE 'COMMERCIAL\_%');
SET @cm_bizinfo_ddl_columns = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_business_profile'
    AND COLUMN_NAME IN ('expected_payment_methods', 'refund_policy_url', 'fulfillment_type', 'fulfillment_days'));
SELECT @cm_bizinfo_status AS bizinfo_status, @cm_bizinfo_v2_keyed AS previous_schemas_expect_9, @cm_bizinfo_v2_not_active AS previous_not_active_expect_0,
       @cm_bizinfo_foreign_field_ids AS previous_fields_outside_id_range_expect_0, @cm_bizinfo_added_in_v2 AS added_keys_already_present_expect_0,
       @cm_bizinfo_conflicts AS unique_key_conflicts_expect_0, @cm_bizinfo_catalog_foreign AS foreign_catalog_rows_expect_0,
       @cm_bizinfo_ddl_columns AS ddl_columns_expect_4;
-- 已存在的发布行（首次执行期望全 0，重跑期望等于 pack_rows）
SELECT 't_catalog_item' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_catalog_item` x WHERE x.`catalog_item_id` = s.`catalog_item_id`)) AS present_rows FROM `tmp_bizinfo_catalog_item` s;
SELECT 't_onboarding_schema' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema` x WHERE x.`schema_id` = s.`schema_id`)) AS present_rows FROM `tmp_bizinfo_onboarding_schema` s;
SELECT 't_onboarding_schema_field' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema_field` x WHERE x.`schema_field_id` = s.`schema_field_id`)) AS present_rows FROM `tmp_bizinfo_onboarding_schema_field` s;
SELECT `schema_id`, COUNT(*) AS v3_fields FROM `tmp_bizinfo_onboarding_schema_field` GROUP BY `schema_id` ORDER BY `schema_id`;

DROP PROCEDURE IF EXISTS `cm_bizinfo_assert`;
DELIMITER //
CREATE PROCEDURE `cm_bizinfo_assert`()
BEGIN
  IF @cm_bizinfo_status NOT IN ('ACTIVE', 'DRAFT') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: @cm_bizinfo_status must be ACTIVE or DRAFT';
  END IF;
  IF @cm_bizinfo_v2_keyed <> 9 OR @cm_bizinfo_v2_not_active <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: the 9 current schemas (9270000000900301-309) are missing, changed or not ACTIVE';
  END IF;
  IF @cm_bizinfo_foreign_field_ids <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a current schema field id is outside 9270000000900000-999999; the copy mapping would leave the ID range';
  END IF;
  IF @cm_bizinfo_added_in_v2 <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: a business-info field already exists in a current schema';
  END IF;
  IF @cm_bizinfo_conflicts <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: the 9281200000 ID range holds foreign rows or a business unique key is taken outside it';
  END IF;
  IF @cm_bizinfo_catalog_foreign <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: t_catalog_item holds other FULFILLMENT_TYPE rows or a COMMERCIAL_ catalog code';
  END IF;
  IF @cm_bizinfo_ddl_columns <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: run 2026-10-29-business-info-collection-ddl.sql first (t_client_business_profile lacks the 4 columns)';
  END IF;
END //
DELIMITER ;
CALL `cm_bizinfo_assert`();
DROP PROCEDURE IF EXISTS `cm_bizinfo_assert`;

-- ===== 3. 写入（一个事务；已存在的行不变）=====
START TRANSACTION;
INSERT INTO `t_catalog_item` SELECT * FROM `tmp_bizinfo_catalog_item` ON DUPLICATE KEY UPDATE `update_time` = `t_catalog_item`.`update_time`;
-- 先退役旧版本释放 uk_schema_active 槽位，再写 v3（仅 ACTIVE 口径）
UPDATE `t_onboarding_schema` SET `status` = 'RETIRED', `active_slot` = NULL, `update_time` = UTC_TIMESTAMP(6)
 WHERE `schema_id` IN (9270000000900301, 9270000000900302, 9270000000900303, 9270000000900304, 9270000000900305, 9270000000900306, 9270000000900307, 9270000000900308, 9270000000900309) AND `status` IN ('ACTIVE', 'DRAFT') AND @cm_bizinfo_status = 'ACTIVE';
INSERT INTO `t_onboarding_schema` SELECT * FROM `tmp_bizinfo_onboarding_schema` ON DUPLICATE KEY UPDATE `update_time` = `t_onboarding_schema`.`update_time`;
-- 先前以 DRAFT 写入的本段 Schema 与目录项，在 ACTIVE 口径下发布（人工改为 RETIRED 的行不动）
UPDATE `t_onboarding_schema` SET `status` = 'ACTIVE', `active_slot` = 1, `approved_time` = UTC_TIMESTAMP(6), `update_time` = UTC_TIMESTAMP(6)
 WHERE `schema_id` BETWEEN 9281200000900000 AND 9281200000999999 AND `status` = 'DRAFT' AND @cm_bizinfo_status = 'ACTIVE';
UPDATE `t_catalog_item` SET `status` = 'ACTIVE', `update_time` = UTC_TIMESTAMP(6)
 WHERE `catalog_item_id` BETWEEN 9281200000600000 AND 9281200000699999 AND `status` = 'DRAFT' AND @cm_bizinfo_status = 'ACTIVE';
INSERT INTO `t_onboarding_schema_field` SELECT * FROM `tmp_bizinfo_onboarding_schema_field` ON DUPLICATE KEY UPDATE `update_time` = `t_onboarding_schema_field`.`update_time`;
COMMIT;

-- ===== 4. 核验（只读）：present_rows 必须等于 pack_rows；v3 字段数与第 2 节一致 =====
SELECT 't_catalog_item' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_catalog_item` x WHERE x.`catalog_item_id` = s.`catalog_item_id`)) AS present_rows FROM `tmp_bizinfo_catalog_item` s;
SELECT 't_onboarding_schema' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema` x WHERE x.`schema_id` = s.`schema_id`)) AS present_rows FROM `tmp_bizinfo_onboarding_schema` s;
SELECT 't_onboarding_schema_field' AS t, COUNT(*) AS pack_rows, SUM(EXISTS (SELECT 1 FROM `t_onboarding_schema_field` x WHERE x.`schema_field_id` = s.`schema_field_id`)) AS present_rows FROM `tmp_bizinfo_onboarding_schema_field` s;
SELECT s.`schema_id`, s.`jurisdiction_code`, s.`party_type`, s.`organization_type_code`, s.`version_no`, s.`status`, s.`active_slot`,
       (SELECT COUNT(*) FROM `t_onboarding_schema_field` f WHERE f.`schema_id` = s.`schema_id`) AS fields
  FROM `t_onboarding_schema` s ORDER BY s.`jurisdiction_code`, s.`party_type`, s.`organization_type_code`, s.`version_no`;
SELECT `field_key`, `requirement_level`, COUNT(*) AS `schema_count` FROM `t_onboarding_schema_field`
 WHERE `schema_id` BETWEEN 9281200000900000 AND 9281200000999999 AND `field_key` IN ('business.expectedPaymentMethods', 'business.refundPolicyUrl', 'business.fulfillmentType', 'business.fulfillmentDays')
 GROUP BY `field_key`, `requirement_level` ORDER BY `field_key`, `requirement_level`;

DROP TEMPORARY TABLE IF EXISTS `tmp_bizinfo_catalog_item`, `tmp_bizinfo_onboarding_schema`, `tmp_bizinfo_onboarding_schema_field`;
