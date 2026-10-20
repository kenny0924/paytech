-- mor_vault · channel-connector-service 账号配置 PROTECT 规则（requirements/20261020/channel-connector-运营控制台渠道成本与基础配置，technique.md 第 5 节）
-- 影响（策略表，Vault 按刷新间隔全量重载，不需重启）：
--   1) 1 条访问规则：channel-connector-service 对 CHANNEL_ACCOUNT_CONFIG_V1 的 PROTECT，用途 CHANNEL_CONFIGURATION。
--      数据类型 CHANNEL_ACCOUNT_CONFIG_V1 已存在（REFERENCE / JSON），不新增类型；既有 REVEAL 规则不动。
--   2) 1 条策略变更记录（版本号 = 当前最大值 + 1）。
-- 目标库：mor_vault（本机 local 与 mor-test 同一文件）；执行前核对 access_rule_id 9100000000000720 与该 caller/action/dataType/purpose 组合均不存在。
-- 本机 local 若 Vault 从 local-environment/vault/policy.local.json 读取策略，则改 JSON（同需求已改），不执行本文件。
-- 回滚：DELETE FROM t_vault_access_rule WHERE access_rule_id = 9100000000000720;
--       DELETE FROM t_vault_policy_change WHERE policy_change_id = 9100000000000720;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SET @channel_config_time = UTC_TIMESTAMP(6);
START TRANSACTION;
SELECT MAX(`policy_version_no`) + 1 INTO @channel_config_policy_version FROM `t_vault_policy_change` FOR UPDATE;
INSERT INTO `t_vault_access_rule` (`access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`, `version`,
  `create_time`, `update_time`) VALUES
  (9100000000000720, 'channel-connector-service', 'PROTECT', 'CHANNEL_ACCOUNT_CONFIG_V1', 'CHANNEL_CONFIGURATION', 'ACTIVE', 1, @channel_config_time, @channel_config_time);
INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
  `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
  (9100000000000720, @channel_config_policy_version, 'SEED', 'POLICY', 'CHANNEL_CONNECTOR_ACCOUNT_CONFIG_PROTECT', NULL,
   '{"accessRules": 1, "callers": ["channel-connector-service"], "dataTypes": ["CHANNEL_ACCOUNT_CONFIG_V1"], "purposes": ["CHANNEL_CONFIGURATION"]}',
   '渠道基础配置管理：channel-connector 在账号配置写入口 Protect 账号参数与凭据（requirements/20261020/channel-connector-运营控制台渠道成本与基础配置）', 'SYSTEM',
   'vault-policy-channel-account-config-protect', @channel_config_time);
COMMIT;
