-- payout S1 · mor_vault 回滚（root，库 mor_vault）。先停 payout-service 或确认其不再 Protect / Reveal。
-- 两种情形（technique 第 9 节、20 文件头）：
--   A 尚无任何 PAYOUT_* Vault 记录：删除本需求 6 行（规则 3、数据类型 2、策略变更 1），回到执行前逐行相同；本文件即 A。
--   B 已有记录（payout 已提交过账户）：不删除——t_vault_access_event / t_vault_protect_request 以外键引用记录，账户 Token 仍需可读；
--     只把三条规则改 DISABLED（经运营后台 Vault 访问规则页或另行审批的 UPDATE），本文件在 B 下拒绝执行。
-- 保护：存在 PAYOUT_* 记录 → ERROR 1242，不做改动。另：本需求的策略变更行若已不是最新版本（之后又有人改过策略），删除它会让当前最大
--   版本号不变而审计链出现空号——同样拒绝，改走 B。重复执行：6 行已不存在时为空操作。
-- 生效：Vault 10 s 内重读策略（版本号回到执行前最大值），不需重启。
USE `mor_vault`;
SET @payout_vault_record_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL
  WHERE EXISTS (SELECT 1 FROM t_vault_record WHERE data_type IN ('PAYOUT_BENEFICIARY_V1', 'PAYOUT_BANK_ACCOUNT_KEY_V1')));
SET @payout_vault_latest_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL
  WHERE EXISTS (SELECT 1 FROM t_vault_policy_change WHERE policy_change_id = 9100000000000885)
    AND (SELECT policy_version_no FROM t_vault_policy_change WHERE policy_change_id = 9100000000000885)
        <> (SELECT MAX(policy_version_no) FROM t_vault_policy_change));
START TRANSACTION;
DELETE FROM `t_vault_access_rule` WHERE access_rule_id IN (9100000000000882, 9100000000000883, 9100000000000884)
  AND caller_service = 'payout-service';
DELETE FROM `t_vault_data_type` WHERE data_type_id IN (9100000000000880, 9100000000000881)
  AND data_type_code IN ('PAYOUT_BENEFICIARY_V1', 'PAYOUT_BANK_ACCOUNT_KEY_V1');
DELETE FROM `t_vault_policy_change` WHERE policy_change_id = 9100000000000885 AND request_id = 'vault-policy-payout-beneficiary';
COMMIT;
SELECT (SELECT COUNT(*) FROM t_vault_data_type WHERE data_type_id BETWEEN 9100000000000880 AND 9100000000000885) AS `data_types_left`,
       (SELECT COUNT(*) FROM t_vault_access_rule WHERE access_rule_id BETWEEN 9100000000000880 AND 9100000000000885) AS `rules_left`,
       (SELECT COUNT(*) FROM t_vault_policy_change WHERE policy_change_id BETWEEN 9100000000000880 AND 9100000000000885) AS `changes_left`,
       (SELECT MAX(policy_version_no) FROM t_vault_policy_change) AS `policy_version_now`;
