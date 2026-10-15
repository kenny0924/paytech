-- Vault 策略初始化数据：本机 local（mor_vault，Docker vault profile 与本机全链路）（2026-10-14 阶段 3 评审稿，待执行文件）
-- 来源：local-environment/vault/policy.local.json（policyVersion=local-e2e-v1）逐条转写，保证现有服务的 Protect/Reveal 不变。
-- 字符集：用 mysql --default-character-set=utf8mb4 执行；客户端默认 latin1 会把中文（注释、名称、理由）写成乱码。
-- 2 个数据类型、6 条访问规则、1 条变更记录（版本 1）；共 9 行 INSERT，0 UPDATE / DELETE。
-- 前置：已执行 ddl.sql。数据级别默认 STANDARD、人工加/解密开关默认关闭，由运营在页面上按需调整。
-- 本文件不含 COMMIT：核对行数后手工 COMMIT，不符则 ROLLBACK。回滚：删除下列 ID 的行。
SET @vault_seed_time=UTC_TIMESTAMP(6);
START TRANSACTION;

INSERT INTO t_vault_data_type (data_type_id,data_type_code,token_mode,data_class,description,manual_protect_enabled,manual_reveal_enabled,status,version,create_time,update_time) VALUES
  (9100000000000100, 'EMAIL_ADDRESS_V1', 'STABLE', 'STANDARD', 'EMAIL_ADDRESS_V1', 0, 0, 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000101, 'PHONE_E164_V1', 'STABLE', 'STANDARD', 'PHONE_E164_V1', 0, 0, 'ACTIVE', 1, @vault_seed_time, @vault_seed_time);

INSERT INTO t_vault_access_rule (access_rule_id,caller_service,action,data_type_code,purpose_code,status,version,create_time,update_time) VALUES
  (9100000000000200, 'client-contract-service', 'PROTECT', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000201, 'client-contract-service', 'PROTECT', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000202, 'client-contract-service', 'REVEAL', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000203, 'client-contract-service', 'REVEAL', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000204, 'platform-support-service', 'PROTECT', 'EMAIL_ADDRESS_V1', 'PLATFORM_LOGIN_EMAIL_V1', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000205, 'platform-support-service', 'REVEAL', 'EMAIL_ADDRESS_V1', 'PLATFORM_LOGIN_EMAIL_V1', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time);

INSERT INTO t_vault_policy_change (policy_change_id,policy_version_no,change_type,target_type,target_key,before_snapshot,after_snapshot,reason,actor_id,request_id,create_time) VALUES
  (9100000000000199, 1, 'SEED', 'POLICY', 'INITIAL', NULL, '{"dataTypes": ["EMAIL_ADDRESS_V1", "PHONE_E164_V1"], "accessRules": 6}', '初始化策略：转写 local-e2e-v1', 'SYSTEM', 'vault-policy-seed-local', @vault_seed_time);

-- 预期：2 行 t_vault_data_type、6 行 t_vault_access_rule、1 行 t_vault_policy_change；核对后 COMMIT。
