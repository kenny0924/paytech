-- mor_client_master · 遗留行脱敏核验（只读）。P-11 决定（主会话 2026-10-27）：遗留 ACTIVE Client 的个人证件号脱敏，不做 Token 化。
-- 在 client-master DDL + cm-dev 遗留回填 / 在途清理 SQL 之后执行。**全部计数必须为 0**（最后一段只做展示）。
-- 个人号码判定：属于 INDIVIDUAL Party，或标识类型在 t_rule_setting 中 VAULT_REQUIRED = '1'（两者取并集，目录未加载时仍有效）。
-- 合法的身份键值：Vault STABLE Token（43 位 Base64URL）或 'LEGACY-REDACTED-<主键>'。
SET NAMES utf8mb4;
USE `mor_client_master`;
SET @token_re = '^[A-Za-z0-9_-]{43}$';
WITH personal_type AS (
  SELECT scope_code AS identifier_type FROM t_rule_setting
   WHERE scope_type = 'IDENTIFIER_TYPE' AND rule_code = 'VAULT_REQUIRED' AND rule_value = '1' AND status = 'ACTIVE')
SELECT 'identity_key_plaintext' AS check_item, COUNT(*) AS violations
  FROM t_party_identity_key k JOIN t_party p ON p.party_id = k.party_id
 WHERE (p.party_type = 'INDIVIDUAL' OR k.identifier_type IN (SELECT identifier_type FROM personal_type))
   AND NOT (k.identifier_key_value REGEXP @token_re OR k.identifier_key_value = CONCAT('LEGACY-REDACTED-', k.identity_key_id))
UNION ALL
SELECT 'profile_public_identifier_personal', COUNT(*)
  FROM t_party_profile_identifier i JOIN t_party_profile f ON f.profile_id = i.profile_id JOIN t_party p ON p.party_id = f.party_id
 WHERE (p.party_type = 'INDIVIDUAL' OR i.identifier_type IN (SELECT identifier_type FROM personal_type))
   AND i.public_identifier_value IS NOT NULL
UNION ALL
SELECT 'profile_masked_not_last4', COUNT(*)
  FROM t_party_profile_identifier i
 WHERE i.identifier_masked_value IS NOT NULL AND i.identifier_masked_value NOT REGEXP '^[*]{4}.{0,4}$'
UNION ALL
SELECT 'application_type_blank', COUNT(*) FROM t_client_application WHERE application_type = '' OR application_type IS NULL
UNION ALL
SELECT 'application_jurisdiction_blank', COUNT(*) FROM t_client_application WHERE jurisdiction_code = '' OR jurisdiction_code IS NULL
UNION ALL
SELECT 'application_schema_zero', COUNT(*) FROM t_client_application WHERE schema_id = 0;
-- 展示（不判定）：遗留键与遗留申请分布
SELECT 'legacy_redacted_keys' AS item, COUNT(*) AS n FROM t_party_identity_key WHERE identifier_key_value LIKE 'LEGACY-REDACTED-%'
UNION ALL SELECT 'legacy_jurisdiction_apps', COUNT(*) FROM t_client_application WHERE jurisdiction_code = 'LEGACY'
UNION ALL SELECT 'apps_schema_null', COUNT(*) FROM t_client_application WHERE schema_id IS NULL
UNION ALL SELECT CONCAT('clients_', status), COUNT(*) FROM t_client GROUP BY status;
