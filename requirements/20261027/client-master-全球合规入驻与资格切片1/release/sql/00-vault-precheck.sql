-- mor_vault · dml.sql 执行前只读核对：五项计数必须全为 0；同时记录执行前最大策略版本号。
SET NAMES utf8mb4;
USE `mor_vault`;
SELECT 'data_type_id_used' AS item, COUNT(*) AS n FROM `t_vault_data_type` WHERE `data_type_id` BETWEEN 9100000000000760 AND 9100000000000769
UNION ALL SELECT 'data_type_code_used', COUNT(*) FROM `t_vault_data_type` WHERE `data_type_code` = 'LEGAL_IDENTIFIER_V1'
UNION ALL SELECT 'access_rule_id_used', COUNT(*) FROM `t_vault_access_rule` WHERE `access_rule_id` BETWEEN 9100000000000760 AND 9100000000000769
UNION ALL SELECT 'access_rule_key_used', COUNT(*) FROM `t_vault_access_rule` WHERE `data_type_code` = 'LEGAL_IDENTIFIER_V1'
UNION ALL SELECT 'policy_change_id_used', COUNT(*) FROM `t_vault_policy_change` WHERE `policy_change_id` BETWEEN 9100000000000760 AND 9100000000000769;
SELECT 'policy_version_before' AS item, MAX(`policy_version_no`) AS n FROM `t_vault_policy_change`;
