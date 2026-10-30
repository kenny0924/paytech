-- mor_vault · payout 收款账户的数据类型与访问规则（requirements/20261030/payout-收款账户与余额解释，technique.md 第 9 节、阶段 2 S-01）
-- 性质：评审稿（阶段 3），待执行文件。目标库 mor_vault（本机 local 与 mor-test 同一文件）。未执行。
-- 主键 9100000000000880—0885：阶段 6 只读核对未占用后再执行（client-master 切片 1 用到 0760—0769）。
-- 发布顺序：先于 payout-service 首次部署（提交账户即 Protect）。Vault 按刷新间隔（默认 10 s）重载策略，不需重启。
-- 影响：
--   1) PAYOUT_BENEFICIARY_V1：REFERENCE / STANDARD / JSON，收款账户完整原值（户名、账号或 IBAN、银行代码、SWIFT BIC、账户类型）。
--   2) PAYOUT_BANK_ACCOUNT_KEY_V1：STABLE / STANDARD / TEXT，规范化账号（payout 去空白、转大写）；只作等值查重键，不 Reveal。
--   3) 访问规则 3 条：payout-service PROTECT 两类（用途 PAYOUT_BENEFICIARY_SUBMIT）、REVEAL 前者（用途 PAYOUT_BENEFICIARY_REVIEW，运营核验；S2 出款执行另加用途）。
--   4) 策略变更 1 行（版本号 = 当前最大值 + 1）。
-- 回滚：payout 尚无 Token 引用时 DELETE 本文件三类行；已有引用时只把访问规则改 DISABLED。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SET @payout_vault_time = UTC_TIMESTAMP(6);
START TRANSACTION;
SELECT MAX(`policy_version_no`) + 1 INTO @payout_vault_policy_version FROM `t_vault_policy_change` FOR UPDATE;
INSERT INTO `t_vault_data_type` (`data_type_id`, `data_type_code`, `token_mode`, `data_class`, `value_format`, `value_pattern`, `description`,
  `manual_protect_enabled`, `manual_reveal_enabled`, `status`, `version`, `create_time`, `update_time`) VALUES
  (9100000000000880, 'PAYOUT_BENEFICIARY_V1', 'REFERENCE', 'STANDARD', 'JSON', NULL,
   'payout 收款账户原值 JSON（户名、账号或 IBAN、银行代码、SWIFT BIC、账户类型）；运营核验与出款执行前读取', 0, 0, 'ACTIVE', 1,
   @payout_vault_time, @payout_vault_time),
  (9100000000000881, 'PAYOUT_BANK_ACCOUNT_KEY_V1', 'STABLE', 'STANDARD', 'TEXT', NULL,
   'payout 规范化收款账号（去空白、转大写）；STABLE Token 只作同 Owner 查重与跨 Owner 共用提示，不读取', 0, 0, 'ACTIVE', 1,
   @payout_vault_time, @payout_vault_time);
INSERT INTO `t_vault_access_rule` (`access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`, `version`,
  `create_time`, `update_time`) VALUES
  (9100000000000882, 'payout-service', 'PROTECT', 'PAYOUT_BENEFICIARY_V1', 'PAYOUT_BENEFICIARY_SUBMIT', 'ACTIVE', 1, @payout_vault_time, @payout_vault_time),
  (9100000000000883, 'payout-service', 'PROTECT', 'PAYOUT_BANK_ACCOUNT_KEY_V1', 'PAYOUT_BENEFICIARY_SUBMIT', 'ACTIVE', 1, @payout_vault_time, @payout_vault_time),
  (9100000000000884, 'payout-service', 'REVEAL', 'PAYOUT_BENEFICIARY_V1', 'PAYOUT_BENEFICIARY_REVIEW', 'ACTIVE', 1, @payout_vault_time, @payout_vault_time);
INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
  `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
  (9100000000000885, @payout_vault_policy_version, 'SEED', 'POLICY', 'PAYOUT_BENEFICIARY', NULL,
   '{"dataTypes": ["PAYOUT_BENEFICIARY_V1", "PAYOUT_BANK_ACCOUNT_KEY_V1"], "accessRules": 3, "callers": ["payout-service"], "purposes": ["PAYOUT_BENEFICIARY_SUBMIT", "PAYOUT_BENEFICIARY_REVIEW"]}',
   '结算出款 S1：payout 提交收款账户时 Protect 原值与规范化账号（STABLE 兼作查重键），运营核验时 Reveal（requirements/20261030/payout-收款账户与余额解释）', 'SYSTEM',
   'vault-policy-payout-beneficiary', @payout_vault_time);
COMMIT;
