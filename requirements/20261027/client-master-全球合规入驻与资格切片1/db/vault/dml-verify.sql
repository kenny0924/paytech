-- mor_vault · dml.sql 执行后只读核验。期望：1 行类型（STABLE/STANDARD/TEXT/0/0/ACTIVE）、2 行规则（ACTIVE）、1 行变更（版本 = 执行前最大值 + 1，且为当前最大值）。
SET NAMES utf8mb4;
USE `mor_vault`;
SELECT `data_type_code`, `token_mode`, `data_class`, `value_format`, `manual_protect_enabled`, `manual_reveal_enabled`, `status`
  FROM `t_vault_data_type` WHERE `data_type_id` = 9100000000000760;
SELECT `access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`
  FROM `t_vault_access_rule` WHERE `access_rule_id` IN (9100000000000761, 9100000000000762) ORDER BY `access_rule_id`;
SELECT `policy_change_id`, `policy_version_no`, `change_type`, `target_key`,
       (SELECT MAX(`policy_version_no`) FROM `t_vault_policy_change`) AS `current_max_version`
  FROM `t_vault_policy_change` WHERE `policy_change_id` = 9100000000000760;
-- 生效核验（Vault 刷新后，约 10 s）：client-master 入驻草稿写入证件成功；t_vault_record 出现 data_type = 'LEGAL_IDENTIFIER_V1' 的 STABLE 记录。
