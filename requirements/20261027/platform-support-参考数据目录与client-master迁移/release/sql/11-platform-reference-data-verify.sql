-- 参考数据目录发布：mor_platform 变更后只读核对（rel-dev 2026-10-27，期望值来自 mor-test 备份演练，见 verification/rel-build-report.md 第 4 节）。
SELECT 'tables' AS k, GROUP_CONCAT(table_name ORDER BY table_name) AS v FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name IN ('t_currency_spec','t_country_region','t_ref_currency','t_ref_country','t_ref_country_subdivision','t_ref_industry','t_ref_mcc');
-- 期望：t_ref_country,t_ref_country_subdivision,t_ref_currency,t_ref_industry,t_ref_mcc（无旧表）
SELECT 'routines_left' AS k, COUNT(*) AS v FROM information_schema.routines WHERE routine_schema = DATABASE() AND routine_name IN ('ref_data_assert','ref_data_rollback_assert');
-- 期望：0
SELECT 'currency' AS k, id, currency_code, numeric_code, name, symbol, status, version FROM t_ref_currency ORDER BY currency_code;
-- 期望（mor-test）：AED 238762073381748736 784 ACTIVE v2（原行回填，不新建）、EGP 1004 818、SAR 1003 682、USD 1001 840；全部 ACTIVE
SELECT 'country' AS k, COUNT(*) AS total, SUM(alpha3_code IS NOT NULL AND numeric_code IS NOT NULL AND name IS NOT NULL) AS complete,
 SUM(calling_code IS NOT NULL) AS with_calling_code, SUM(default_currency_code IS NOT NULL) AS with_currency, SUM(status = 'ACTIVE') AS active FROM t_ref_country;
-- 期望：249 / 249 / 242 / 248 / 249
SELECT 'subdivision' AS k, country_code, COUNT(*) AS n, SUM(local_name IS NULL) AS no_local_name FROM t_ref_country_subdivision GROUP BY country_code ORDER BY country_code;
-- 期望：AE 7、EG 27、SA 13，no_local_name 全 0
SELECT 'industry' AS k, industry_level, COUNT(*) AS n FROM t_ref_industry GROUP BY industry_level;
-- 期望：1:10、2:32
SELECT 'industry_default_mcc_not_active' AS k, COUNT(*) AS n FROM t_ref_industry i LEFT JOIN t_ref_mcc m ON m.mcc_code = i.default_mcc_code AND m.status = 'ACTIVE'
 WHERE i.industry_level = 2 AND m.id IS NULL;
-- 期望：0
SELECT 'mcc' AS k, COUNT(*) AS n, SUM(network_high_risk = 1) AS high_risk FROM t_ref_mcc;
-- 期望：298 / 14
SELECT 'old_api_currency_json_bytes' AS k, LENGTH(JSON_OBJECT('items', JSON_ARRAYAGG(JSON_OBJECT('currency', currency_code, 'minorUnit', minor_unit, 'roundingMode', rounding_mode,
 'status', status, 'updateTime', DATE_FORMAT(update_time, '%Y-%m-%dT%H:%i:%s.%fZ'), 'version', version)))) AS approx_bytes FROM t_ref_currency;
-- 期望：约 550 字节，远小于 ledger 严格上限 8192（旧接口 /platform/v1/currency-specs 只多 SAR / EGP 两行）
