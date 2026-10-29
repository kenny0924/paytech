-- REQ-12 · 发布后只读核对（runbook ⑦ / ⑨）。READ ONLY 会话执行。
-- DRAFT 后期望：v3 9 行 DRAFT、v2 9 行 ACTIVE、v3 字段 363、FULFILLMENT_TYPE 4 行 DRAFT；ACTIVE 后：v3 9 行 ACTIVE / 1、v2 9 行 RETIRED、字段 965、目录 ACTIVE。
SELECT @@transaction_read_only AS read_only_session;
SELECT CASE WHEN `schema_id` BETWEEN 9281200000900000 AND 9281200000999999 THEN 'v3' WHEN `schema_id` BETWEEN 9270000000900000 AND 9270000000999999 THEN 'v2' ELSE 'v1' END AS generation,
       `status`, `active_slot`, COUNT(*) AS schema_count FROM `t_onboarding_schema` GROUP BY 1, 2, 3 ORDER BY 1, 2;
SELECT (SELECT COUNT(*) FROM `t_onboarding_schema_field`) AS fields,
       (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_id` BETWEEN 9281200000900000 AND 9281200000999999) AS v3_fields;
SELECT `item_code`, `status`, `display_order` FROM `t_catalog_item` WHERE `catalog_code` = 'FULFILLMENT_TYPE' ORDER BY `display_order`;
SELECT s.`schema_id`, s.`version_no`, f.`field_key`, f.`widget_code`, f.`catalog_code`, f.`requirement_level`, f.`display_group`, f.`display_order`, f.`visible_when`
  FROM `t_onboarding_schema` s JOIN `t_onboarding_schema_field` f ON f.`schema_id` = s.`schema_id`
 WHERE f.`field_key` IN ('business.expectedPaymentMethods', 'business.refundPolicyUrl', 'business.fulfillmentType', 'business.fulfillmentDays')
 ORDER BY s.`schema_id`, f.`display_order`;
