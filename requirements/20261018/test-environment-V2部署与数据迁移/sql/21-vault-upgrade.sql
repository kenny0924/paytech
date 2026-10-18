-- 21-vault-upgrade.sql · mor_vault 原库升级到 V2
-- 差异来源：演练库对 mor-test 转储与 paytech/vault/docs/database/sql/vault-schema.sql 逐表比对（scripts/schema_diff.py）：
--   6 张表的列、类型、默认值、排序规则与索引全部一致，Schema 无需变更。V2 改名 / 拆出的调用方需要访问规则（数据变更）。
-- 影响（策略表，Vault 按刷新间隔全量重载，不需重启）：
--   1) 新建数据类型 PAYMENT_CARD_DEK（REFERENCE、SECRET、TEXT、人工加解密关闭；按 requirements/20261015/payment-Card加密存储
--      部署前清单）。mor-test 现有 7 个类型中没有它，card-service 存卡前必须存在。
--   2) 7 条访问规则（与第 14 步本机 30-vault-v2-callers.sql 同一组合）：
--      client-master-service：EMAIL_ADDRESS_V1 / PHONE_E164_V1 的 PROTECT、REVEAL（CLIENT_ONBOARDING_CONTACT；原 client-contract-service）；
--      card-service：PAYMENT_CARD_DEK 的 PROTECT（CARD_STORAGE）与 REVEAL（CARD_USE）；
--      channel-connector-service：CHANNEL_ACCOUNT_CONFIG_V1 的 REVEAL（CHANNEL_EXECUTION）。
--   3) 1 条策略变更记录（版本号 = 当前最大值 + 1，mor-test 转储时为 15 → 16）。
--   保留：client-contract-service 的 4 条旧规则与全部审计 / 访问事件不动（历史与回滚需要），阶段 3 再决定停用。
-- ID 段：数据类型 9100000000000700，规则 9100000000000701—0707，变更记录 9100000000000700；与 mor-test 现有
--   种子段 9100000000000599—0610 及 Snowflake ID 不重叠。scripts/migrate.sh 执行前核对这些 ID 与 caller 规则均不存在。
-- 回滚：DELETE FROM t_vault_access_rule WHERE access_rule_id BETWEEN 9100000000000701 AND 9100000000000707;
--       DELETE FROM t_vault_data_type WHERE data_type_id = 9100000000000700;
--       DELETE FROM t_vault_policy_change WHERE policy_change_id = 9100000000000700;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SET @vault_v2_time = UTC_TIMESTAMP(6);
START TRANSACTION;
SELECT MAX(`policy_version_no`) + 1 INTO @vault_v2_policy_version FROM `t_vault_policy_change` FOR UPDATE;
INSERT INTO `t_vault_data_type` (`data_type_id`, `data_type_code`, `token_mode`, `data_class`, `value_format`, `value_pattern`,
  `description`, `manual_protect_enabled`, `manual_reveal_enabled`, `status`, `version`, `create_time`, `update_time`) VALUES
  (9100000000000700, 'PAYMENT_CARD_DEK', 'REFERENCE', 'SECRET', 'TEXT', NULL, '支付卡数据加密密钥（card-service 存卡与用卡）', 0, 0,
   'ACTIVE', 1, @vault_v2_time, @vault_v2_time);
INSERT INTO `t_vault_access_rule` (`access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`, `version`,
  `create_time`, `update_time`) VALUES
  (9100000000000701, 'client-master-service', 'PROTECT', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000702, 'client-master-service', 'PROTECT', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000703, 'client-master-service', 'REVEAL', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000704, 'client-master-service', 'REVEAL', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000705, 'card-service', 'PROTECT', 'PAYMENT_CARD_DEK', 'CARD_STORAGE', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000706, 'card-service', 'REVEAL', 'PAYMENT_CARD_DEK', 'CARD_USE', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000707, 'channel-connector-service', 'REVEAL', 'CHANNEL_ACCOUNT_CONFIG_V1', 'CHANNEL_EXECUTION', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time);
INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
  `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
  (9100000000000700, @vault_v2_policy_version, 'SEED', 'POLICY', 'V2_CALLERS', NULL,
   '{"dataTypes": ["PAYMENT_CARD_DEK"], "accessRules": 7, "callers": ["client-master-service", "card-service", "channel-connector-service"]}',
   'mor-test 切换到 V2：调用方改名与拆分（requirements/20261018/test-environment-V2部署与数据迁移）', 'SYSTEM',
   'vault-policy-v2-callers-mor-test', @vault_v2_time);
COMMIT;
