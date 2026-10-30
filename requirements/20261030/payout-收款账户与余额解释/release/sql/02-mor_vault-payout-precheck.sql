-- payout S1 · mor_vault 执行前只读预检（root 只读会话，库 mor_vault）。
-- 期望：第 1 行全 0（主键 9100000000000880—885、两个数据类型编码、payout-service 规则、PAYOUT_* 规则、本策略变更键均未占用）；
--   第 2 行给出当前最大策略版本（2026-10-30 mor-test 读数 21，本机 25）——20-mor_vault-payout.sql 按 MAX + 1 计算，不写死版本号；
--   第 3 行：两个数据类型尚无任何 Vault 记录（0）。任一不符 → 停。
SELECT (SELECT COUNT(*) FROM t_vault_data_type WHERE data_type_id BETWEEN 9100000000000880 AND 9100000000000885) AS `data_type_ids`,
       (SELECT COUNT(*) FROM t_vault_access_rule WHERE access_rule_id BETWEEN 9100000000000880 AND 9100000000000885) AS `rule_ids`,
       (SELECT COUNT(*) FROM t_vault_policy_change WHERE policy_change_id BETWEEN 9100000000000880 AND 9100000000000885) AS `change_ids`,
       (SELECT COUNT(*) FROM t_vault_data_type WHERE data_type_code IN ('PAYOUT_BENEFICIARY_V1', 'PAYOUT_BANK_ACCOUNT_KEY_V1')) AS `data_type_codes`,
       (SELECT COUNT(*) FROM t_vault_access_rule WHERE caller_service = 'payout-service' OR data_type_code LIKE 'PAYOUT\_%') AS `payout_rules`,
       (SELECT COUNT(*) FROM t_vault_policy_change WHERE target_key = 'PAYOUT_BENEFICIARY' OR request_id = 'vault-policy-payout-beneficiary') AS `change_keys`;
SELECT MAX(policy_version_no) AS `current_policy_version`, COUNT(*) AS `policy_changes`,
       (SELECT COUNT(*) FROM t_vault_data_type) AS `data_types`, (SELECT COUNT(*) FROM t_vault_access_rule) AS `access_rules`
FROM t_vault_policy_change;
SELECT COUNT(*) AS `payout_records` FROM t_vault_record WHERE data_type IN ('PAYOUT_BENEFICIARY_V1', 'PAYOUT_BANK_ACCOUNT_KEY_V1');
