-- mor_vault（本机 Vault 容器 mor-local-vault-service-1 使用的库）：为 V2 改名 / 拆出的调用方补访问规则。
-- Vault 策略来自数据库（internal/pkg/policy，按间隔全量刷新），local-environment/vault/policy.local.json 已不被读取；
-- 本文件是实际生效的变更，policy.local.json 的同步改动只作评审对照（报告给出 diff）。
-- 调用方名 = 各服务 vault.client.caller-service（未配置时取 spring.application.name）：
--   client-master-service      ← 原 client-contract-service：入驻联系人 Email / Phone 的 Protect / Reveal（ClientDraftContactClientImpl）
--   card-service               ← 原 payment-orchestration-service：卡 DEK Protect CARD_STORAGE、Reveal CARD_USE（CardKeyClientImpl）
--   channel-connector-service  ：渠道账号配置执行用 Reveal CHANNEL_EXECUTION（ChannelAccountConfigReader）
-- 旧调用方规则保留不动（旧架构全链路仍引用）。数据类型 6 个均已 ACTIVE，无需新增。
-- 执行前核对（只读，2026-10-18 已核）：t_vault_access_rule 中下列 caller 0 行；MAX(policy_version_no)=19。
-- ID 段 9100000000000300—0306、变更记录 9100000000000299 与既有种子段不重叠。用 mysql --default-character-set=utf8mb4 执行。
-- 预期：7 行规则、1 行变更记录（版本 20）。Vault 下一次策略刷新后生效（无需重启）。
-- 回滚：DELETE FROM t_vault_access_rule WHERE access_rule_id BETWEEN 9100000000000300 AND 9100000000000306;
--       DELETE FROM t_vault_policy_change WHERE policy_change_id = 9100000000000299;
USE mor_vault;
SET @vault_v2_time = UTC_TIMESTAMP(6);
START TRANSACTION;
INSERT INTO t_vault_access_rule (access_rule_id,caller_service,action,data_type_code,purpose_code,status,version,create_time,update_time) VALUES
  (9100000000000300, 'client-master-service', 'PROTECT', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000301, 'client-master-service', 'PROTECT', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000302, 'client-master-service', 'REVEAL', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000303, 'client-master-service', 'REVEAL', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000304, 'card-service', 'PROTECT', 'PAYMENT_CARD_DEK', 'CARD_STORAGE', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000305, 'card-service', 'REVEAL', 'PAYMENT_CARD_DEK', 'CARD_USE', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time),
  (9100000000000306, 'channel-connector-service', 'REVEAL', 'CHANNEL_ACCOUNT_CONFIG_V1', 'CHANNEL_EXECUTION', 'ACTIVE', 1, @vault_v2_time, @vault_v2_time);
INSERT INTO t_vault_policy_change (policy_change_id,policy_version_no,change_type,target_type,target_key,before_snapshot,after_snapshot,reason,actor_id,request_id,create_time) VALUES
  (9100000000000299, 20, 'SEED', 'POLICY', 'V2_CALLERS', NULL, '{"accessRules": 7, "callers": ["client-master-service", "card-service", "channel-connector-service"]}', 'V2 架构调用方改名与拆分（重构任务卡 14 本机全链路）', 'SYSTEM', 'vault-policy-v2-callers-local', @vault_v2_time);
COMMIT;
