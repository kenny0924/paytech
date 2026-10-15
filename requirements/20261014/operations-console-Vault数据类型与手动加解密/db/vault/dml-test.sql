-- Vault 策略初始化数据：test 环境（mor-test 的 mor_vault）（2026-10-14 阶段 3 评审稿，待执行文件）
-- 来源：test-environment/vault/policy.json（policyVersion=test-deployment-v2）逐条转写，保证现有服务的 Protect/Reveal 不变。
-- 字符集：用 mysql --default-character-set=utf8mb4 执行；客户端默认 latin1 会把中文（注释、名称、理由）写成乱码。
-- 4 个数据类型、11 条访问规则、1 条变更记录（版本 1）；共 16 行 INSERT，0 UPDATE / DELETE。
-- 前置：已执行 ddl.sql。数据级别默认 STANDARD、人工加/解密开关默认关闭，由运营在页面上按需调整。
-- 本文件不含 COMMIT：核对行数后手工 COMMIT，不符则 ROLLBACK。回滚：删除下列 ID 的行。
SET @vault_seed_time=UTC_TIMESTAMP(6);
START TRANSACTION;

INSERT INTO t_vault_data_type (data_type_id,data_type_code,token_mode,data_class,description,manual_protect_enabled,manual_reveal_enabled,status,version,create_time,update_time) VALUES
  (9100000000000500, 'EMAIL_ADDRESS_V1', 'STABLE', 'STANDARD', 'EMAIL_ADDRESS_V1', 0, 0, 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000501, 'PHONE_E164_V1', 'STABLE', 'STANDARD', 'PHONE_E164_V1', 0, 0, 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000502, 'TEST_REFERENCE_V1', 'REFERENCE', 'STANDARD', 'TEST_REFERENCE_V1', 0, 0, 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000503, 'TEST_STABLE_V1', 'STABLE', 'STANDARD', 'TEST_STABLE_V1', 0, 0, 'ACTIVE', 1, @vault_seed_time, @vault_seed_time);

INSERT INTO t_vault_access_rule (access_rule_id,caller_service,action,data_type_code,purpose_code,status,version,create_time,update_time) VALUES
  (9100000000000600, 'client-contract-service', 'PROTECT', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000601, 'client-contract-service', 'PROTECT', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000602, 'client-contract-service', 'REVEAL', 'EMAIL_ADDRESS_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000603, 'client-contract-service', 'REVEAL', 'PHONE_E164_V1', 'CLIENT_ONBOARDING_CONTACT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000604, 'platform-support-service', 'PROTECT', 'EMAIL_ADDRESS_V1', 'PLATFORM_LOGIN_EMAIL_V1', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000605, 'platform-support-service', 'REVEAL', 'EMAIL_ADDRESS_V1', 'PLATFORM_LOGIN_EMAIL_V1', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000606, 'vault-test-peer', 'PROTECT', 'TEST_STABLE_V1', 'TEST_DEPLOYMENT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000607, 'vault-test-smoke', 'PROTECT', 'TEST_REFERENCE_V1', 'TEST_DEPLOYMENT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000608, 'vault-test-smoke', 'PROTECT', 'TEST_STABLE_V1', 'TEST_DEPLOYMENT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000609, 'vault-test-smoke', 'REVEAL', 'TEST_REFERENCE_V1', 'TEST_DEPLOYMENT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time),
  (9100000000000610, 'vault-test-smoke', 'REVEAL', 'TEST_STABLE_V1', 'TEST_DEPLOYMENT', 'ACTIVE', 1, @vault_seed_time, @vault_seed_time);

INSERT INTO t_vault_policy_change (policy_change_id,policy_version_no,change_type,target_type,target_key,before_snapshot,after_snapshot,reason,actor_id,request_id,create_time) VALUES
  (9100000000000599, 1, 'SEED', 'POLICY', 'INITIAL', NULL, '{"dataTypes": ["EMAIL_ADDRESS_V1", "PHONE_E164_V1", "TEST_REFERENCE_V1", "TEST_STABLE_V1"], "accessRules": 11}', '初始化策略：转写 test-deployment-v2', 'SYSTEM', 'vault-policy-seed-test', @vault_seed_time);

-- 预期：4 行 t_vault_data_type、11 行 t_vault_access_rule、1 行 t_vault_policy_change；核对后 COMMIT。
