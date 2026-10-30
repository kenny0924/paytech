-- payout S1 · mor_vault 执行后核对（root 只读会话，库 mor_vault）。
-- 期望：数据类型 2 行（REFERENCE/STANDARD/JSON、STABLE/STANDARD/TEXT，均 ACTIVE、人工加解密 0）；规则 3 行 payout-service（PROTECT ×2 用途
--   PAYOUT_BENEFICIARY_SUBMIT、REVEAL ×1 用途 PAYOUT_BENEFICIARY_REVIEW），均 ACTIVE；策略变更 1 行，版本 = 执行前最大值 + 1 且为当前最大值。
-- 生效：Vault 按 VAULT_POLICY_REFRESH_INTERVAL（mor-test 未设置 = 默认 10 s）全量重读三张策略表，不需重启；10 s 后 payout Protect 才会被接受。
SELECT data_type_id, data_type_code, token_mode, data_class, value_format, manual_protect_enabled, manual_reveal_enabled, status, version
FROM t_vault_data_type WHERE data_type_id IN (9100000000000880, 9100000000000881) ORDER BY data_type_id;
SELECT access_rule_id, caller_service, action, data_type_code, purpose_code, status, version
FROM t_vault_access_rule WHERE access_rule_id BETWEEN 9100000000000882 AND 9100000000000884 ORDER BY access_rule_id;
SELECT policy_change_id, policy_version_no, change_type, target_type, target_key, request_id,
       policy_version_no = (SELECT MAX(policy_version_no) FROM t_vault_policy_change) AS `is_latest`
FROM t_vault_policy_change WHERE policy_change_id = 9100000000000885;
