-- mor_vault · dml.sql 回滚。
-- 前提：client-master 尚无 LEGAL_IDENTIFIER_V1 Token 引用（下句计数为 0）。若已有记录，不删除类型，改为执行文件末尾的「停用」段。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SELECT 'legal_identifier_records' AS item, COUNT(*) AS n FROM `t_vault_record` WHERE `data_type` = 'LEGAL_IDENTIFIER_V1';
START TRANSACTION;
DELETE FROM `t_vault_access_rule` WHERE `access_rule_id` IN (9100000000000761, 9100000000000762);
DELETE FROM `t_vault_data_type` WHERE `data_type_id` = 9100000000000760;
DELETE FROM `t_vault_policy_change` WHERE `policy_change_id` = 9100000000000760;
COMMIT;
-- 停用（已有 Token 时代替上面的删除；只停用不删除，保留策略历史）：
-- START TRANSACTION;
-- SELECT MAX(`policy_version_no`) + 1 INTO @v FROM `t_vault_policy_change` FOR UPDATE;
-- UPDATE `t_vault_access_rule` SET `status` = 'DISABLED', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6)
--   WHERE `access_rule_id` IN (9100000000000761, 9100000000000762);
-- INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
--   `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
--   (9100000000000763, @v, 'RULE_UPDATE', 'POLICY', 'CLIENT_ONBOARDING_LEGAL_IDENTIFIER', NULL, '{"disabledRules": 2}',
--    '回滚全球合规入驻切片 1 证件 Vault 化', 'SYSTEM', 'vault-policy-client-onboarding-legal-identifier-disable', UTC_TIMESTAMP(6));
-- COMMIT;
