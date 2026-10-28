-- 国家包法务修订 · mor-test 只读预检（runbook 第 ② 步）。只有 SELECT；在 READ ONLY 会话中执行。
-- 期望（2026-10-28 阶段 6 读取值）：新列 0；本 ID 段 0；8 个 v1 ACTIVE / 1；字段 275、规则 79、属性定义 10、标识类型 8、目录项 61；
--   新增属性代码 0；v2 / 自由职业者 Schema 键 0。任一不符即停止，先查清是否已被他人执行或数据已变。
SELECT COUNT(*) AS new_columns FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'draft_revision' AND TABLE_NAME IN ('t_client_application', 't_application_declaration');
SELECT (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_field_id` BETWEEN 9270000000900000 AND 9270000000999999) AS fields_in_range,
       (SELECT COUNT(*) FROM `t_onboarding_schema` WHERE `schema_id` BETWEEN 9270000000900000 AND 9270000000999999) AS schemas_in_range,
       (SELECT COUNT(*) FROM `t_catalog_item` WHERE `catalog_item_id` BETWEEN 9270000000900000 AND 9270000000999999) AS catalog_in_range,
       (SELECT COUNT(*) FROM `t_rule_setting` WHERE `rule_setting_id` BETWEEN 9270000000900000 AND 9270000000999999) AS rules_in_range,
       (SELECT COUNT(*) FROM `t_identifier_type` WHERE `identifier_type_id` BETWEEN 9270000000900000 AND 9270000000999999) AS identifier_types_in_range,
       (SELECT COUNT(*) FROM `t_attribute_definition` WHERE `attribute_code` IN ('AE_ERN', 'AE_REGISTRATION_AUTHORITY_NAME', 'AE_LICENSE_ERN', 'AE_LICENSE_AUTHORITY_NAME', 'SA_CR_ANNUAL_CONFIRMATION_DATE', 'EG_VAT_REGISTRATION_NUMBER', 'SA_FREELANCE_DOCUMENT_NUMBER', 'SA_FREELANCE_DOCUMENT_EXPIRY_DATE')) AS new_attribute_codes;
SELECT `schema_id`, `jurisdiction_code`, `party_type`, `organization_type_code`, `version_no`, `status`, `active_slot`
  FROM `t_onboarding_schema` ORDER BY `schema_id`;
SELECT (SELECT COUNT(*) FROM `t_onboarding_schema_field`) AS fields, (SELECT COUNT(*) FROM `t_rule_setting`) AS rules,
       (SELECT COUNT(*) FROM `t_attribute_definition`) AS attributes, (SELECT COUNT(*) FROM `t_identifier_type`) AS identifier_types,
       (SELECT COUNT(*) FROM `t_catalog_item`) AS catalog_items;
-- 申请与声明现状（只记录，不作停止条件）：存量申请 draft_revision 取默认 0、存量声明为空按 0 解释，部署后仍有效。
SELECT a.`status`, s.`version_no`, COUNT(*) AS applications FROM `t_client_application` a
  LEFT JOIN `t_onboarding_schema` s ON s.`schema_id` = a.`schema_id` GROUP BY a.`status`, s.`version_no` ORDER BY 1, 2;
