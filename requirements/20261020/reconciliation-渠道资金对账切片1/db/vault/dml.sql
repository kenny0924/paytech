-- mor_vault · reconciliation-service 银行账号 PROTECT（requirements/20261020/reconciliation-渠道资金对账切片1，technique.md D-04、2.8）
-- 影响（策略表，Vault 按刷新间隔全量重载，不需重启）：
--   1) 1 个数据类型 CASH_ACCOUNT_NUMBER_V1：Token 模式 REFERENCE、数据级别 STANDARD、格式 REGEX [A-Za-z0-9]{4,34}（与
--      CreateCashAccountRequestBody.accountNumber 校验一致，含 IBAN）；人工加 / 解密关闭。
--   2) 1 条访问规则：reconciliation-service 对 CASH_ACCOUNT_NUMBER_V1 的 PROTECT，用途 RECON_CASH_ACCOUNT。本期没有 Reveal 消费者，不加 REVEAL。
--   3) 1 条策略变更记录（版本号 = 当前最大值 + 1）。
-- 目标库：mor_vault（mor-test；本机 local 同一文件）。执行前核对 ID 9100000000000730 在三张表均不存在、数据类型编码与
-- caller/action/dataType/purpose 组合不存在（本机 2026-10-20 已核对为空）。
-- 本机 local 与 mor-test 的 Vault 都只从数据库读取访问策略（local-environment/README.md、test-environment/README.md）；
-- policy.local.json / test-environment/vault/policy.json 仅为转写来源、不再被读取（本需求已同步改，便于对照）。
-- 执行记录：本机 mor_vault 已于 2026-10-20 阶段 5 执行本文件（delivery.md「环境操作记录」）；mor-test 按 release/release.md 第 4 节执行。
-- 回滚：DELETE FROM t_vault_access_rule WHERE access_rule_id = 9100000000000730;
--       DELETE FROM t_vault_data_type WHERE data_type_id = 9100000000000730;
--       DELETE FROM t_vault_policy_change WHERE policy_change_id = 9100000000000730;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SET @recon_cash_time = UTC_TIMESTAMP(6);
START TRANSACTION;
SELECT MAX(`policy_version_no`) + 1 INTO @recon_cash_policy_version FROM `t_vault_policy_change` FOR UPDATE;
INSERT INTO `t_vault_data_type` (`data_type_id`, `data_type_code`, `token_mode`, `data_class`, `value_format`, `value_pattern`, `description`,
  `manual_protect_enabled`, `manual_reveal_enabled`, `status`, `version`, `create_time`, `update_time`) VALUES
  (9100000000000730, 'CASH_ACCOUNT_NUMBER_V1', 'REFERENCE', 'STANDARD', 'REGEX', '[A-Za-z0-9]{4,34}', '平台银行账户账号（对账银行账户，4–34 位字母或数字，含 IBAN）',
   0, 0, 'ACTIVE', 1, @recon_cash_time, @recon_cash_time);
INSERT INTO `t_vault_access_rule` (`access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`, `version`,
  `create_time`, `update_time`) VALUES
  (9100000000000730, 'reconciliation-service', 'PROTECT', 'CASH_ACCOUNT_NUMBER_V1', 'RECON_CASH_ACCOUNT', 'ACTIVE', 1, @recon_cash_time, @recon_cash_time);
INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
  `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
  (9100000000000730, @recon_cash_policy_version, 'SEED', 'POLICY', 'RECONCILIATION_CASH_ACCOUNT_PROTECT', NULL,
   '{"dataTypes": ["CASH_ACCOUNT_NUMBER_V1"], "accessRules": 1, "callers": ["reconciliation-service"], "purposes": ["RECON_CASH_ACCOUNT"]}',
   '对账银行账户：reconciliation 在银行账户新建入口 Protect 银行账号，只存令牌与末四位（requirements/20261020/reconciliation-渠道资金对账切片1）', 'SYSTEM',
   'vault-policy-recon-cash-account-protect', @recon_cash_time);
-- 预期：3 行 INSERT（数据类型 1、访问规则 1、变更记录 1）；核对后 COMMIT。
COMMIT;
