-- REQ-12 业务信息补采 · mor-test 只读预检（runbook 第 ③ 步）。只有 SELECT；在 READ ONLY 会话中执行。
-- 期望（2026-10-29 阶段 6 读数，见 precheck/mor_client_master-precheck-20261029.out）：新列 0；本 ID 段（9281200000…）0；
--   9 个 ACTIVE（8 个 v2 + SA 自由职业者 v1，ID 9270000000900301—309）；字段 602；FULFILLMENT_TYPE 目录 0；COMMERCIAL_ 目录码 0；
--   新增 4 个 field_key 在任一 Schema 中 0。任一不符即停止，先查清是否已被他人执行或数据已变。
SELECT @@transaction_read_only AS read_only_session;
SELECT COUNT(*) AS new_columns FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_business_profile'
   AND COLUMN_NAME IN ('expected_payment_methods', 'refund_policy_url', 'fulfillment_type', 'fulfillment_days');
SELECT (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `schema_field_id` BETWEEN 9281200000000000 AND 9281200000999999) AS fields_in_range,
       (SELECT COUNT(*) FROM `t_onboarding_schema` WHERE `schema_id` BETWEEN 9281200000000000 AND 9281200000999999) AS schemas_in_range,
       (SELECT COUNT(*) FROM `t_catalog_item` WHERE `catalog_item_id` BETWEEN 9281200000000000 AND 9281200000999999) AS catalog_in_range,
       (SELECT COUNT(*) FROM `t_catalog_item` WHERE `catalog_code` = 'FULFILLMENT_TYPE' OR `catalog_code` LIKE 'COMMERCIAL\_%') AS reserved_catalog_rows,
       (SELECT COUNT(*) FROM `t_onboarding_schema_field` WHERE `field_key` IN ('business.expectedPaymentMethods', 'business.refundPolicyUrl',
          'business.fulfillmentType', 'business.fulfillmentDays')) AS new_field_keys;
SELECT `schema_id`, `jurisdiction_code`, `party_type`, `organization_type_code`, `version_no`, `status`, `active_slot`,
       (SELECT COUNT(*) FROM `t_onboarding_schema_field` f WHERE f.`schema_id` = s.`schema_id`) AS fields,
       (SELECT MAX(`display_order`) FROM `t_onboarding_schema_field` f WHERE f.`schema_id` = s.`schema_id` AND f.`section` = 'BUSINESS') AS business_max_order
  FROM `t_onboarding_schema` s ORDER BY `schema_id`;
SELECT (SELECT COUNT(*) FROM `t_onboarding_schema_field`) AS fields, (SELECT COUNT(*) FROM `t_catalog_item`) AS catalog_items,
       (SELECT MIN(`schema_field_id`) FROM `t_onboarding_schema_field` WHERE `schema_id` BETWEEN 9270000000900301 AND 9270000000900309) AS v2_field_id_min,
       (SELECT MAX(`schema_field_id`) FROM `t_onboarding_schema_field` WHERE `schema_id` BETWEEN 9270000000900301 AND 9270000000900309) AS v2_field_id_max;
-- 在途申请与冻结版本（只记录，不作停止条件）：在途申请继续用冻结版本，v3 发布不影响。
SELECT a.`status`, s.`version_no`, a.`schema_id`, COUNT(*) AS applications FROM `t_client_application` a
  LEFT JOIN `t_onboarding_schema` s ON s.`schema_id` = a.`schema_id` GROUP BY a.`status`, s.`version_no`, a.`schema_id` ORDER BY 1, 2, 3;
SELECT COUNT(*) AS business_profiles FROM `t_client_business_profile`;
