-- 参考数据目录发布：mor_client_master 只读核对（SELECT only）。rel-dev 2026-10-27。
SELECT 'retired_catalog_items' AS k, catalog_code, COUNT(*) AS n FROM t_catalog_item WHERE catalog_code IN ('INDUSTRY','CURRENCY','STATE_OR_PROVINCE') OR catalog_code LIKE 'REF\_%' GROUP BY catalog_code;
SELECT 'schema_field_catalog' AS k, catalog_code, COUNT(*) AS n FROM t_onboarding_schema_field WHERE catalog_code IN ('CURRENCY','REF_CURRENCY','STATE_OR_PROVINCE','REF_COUNTRY_SUBDIVISION','INDUSTRY') GROUP BY catalog_code;
SELECT 'address_component_catalog' AS k, address_format_code, component, catalog_code FROM t_address_format_component WHERE catalog_code IS NOT NULL ORDER BY 2,3;
SELECT 'party_address_state_patterns' AS k,
  CASE WHEN state_or_province IS NULL THEN 'NULL'
       WHEN REGEXP_LIKE(state_or_province, '^(AE|EG)_[A-Z0-9]+$', 'c') THEN 'AE/EG underscore (will convert)'
       WHEN REGEXP_LIKE(state_or_province, '^[A-Z]{2}_[A-Z0-9]+$', 'c') THEN 'other underscore (not converted)'
       WHEN REGEXP_LIKE(state_or_province, '^[A-Z]{2}-[A-Z0-9]{1,3}$', 'c') THEN 'ISO already'
       ELSE 'free text' END AS pattern, COUNT(*) AS n
 FROM t_party_address GROUP BY pattern ORDER BY pattern;
SELECT 'party_address_underscore_values' AS k, state_or_province, COUNT(*) AS n FROM t_party_address WHERE REGEXP_LIKE(state_or_province, '^[A-Z]{2}_[A-Z0-9]+$', 'c') GROUP BY state_or_province ORDER BY 2;
SELECT 'classification_industries' AS k, industry_code, mcc_code, COUNT(*) AS n FROM t_client_business_classification GROUP BY industry_code, mcc_code ORDER BY 2,3;
SELECT 'country_pack_rerun_guard' AS k, COUNT(*) AS n FROM t_catalog_item;
