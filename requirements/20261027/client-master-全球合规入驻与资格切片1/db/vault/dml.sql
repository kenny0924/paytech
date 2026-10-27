-- mor_vault · 法定证件号的数据类型与访问规则（requirements/20261027/client-master-全球合规入驻与资格切片1，technique.md 第 2.3 节、R3 / S-12）
-- 性质：发布执行文件。目标库 mor_vault（本机 local 与 mor-test 同一文件）；Vault 按刷新间隔（默认 10 s）全量重载策略，不需重启。
--   本机 local：未执行（2026-10-27 只读核对：policy_version_no 最大 24，ID 9100000000000760—0769 未占用）。mor-test：未执行。
-- 发布顺序：本文件先于 client-master 新版本执行（client-master 入驻写证件即调用 Protect）。
-- 影响：
--   1) 1 个数据类型 LEGAL_IDENTIFIER_V1：STABLE / STANDARD / TEXT，值为 client-master 规范化后的证件号原文
--      （规范化规则由 client-master 按标识类型执行：去空白与分隔符、转大写；Vault 不做规范化）。
--      STABLE 指纹 = HMAC(数据类型 + 精确原值)，同号在不同标识类型 / 签发域下得到同一 Token，
--      唯一性由 client-master t_party_identity_key (identifier_type, issuer_scope, token) 承担。手动加解密开关均关闭。
--   2) 2 条访问规则：client-master-service PROTECT（用途 CLIENT_ONBOARDING_IDENTIFIER，草稿 / 人员写入证件时保存）、
--      REVEAL（用途 CLIENT_ONBOARDING_IDENTIFIER，本切片无回显，预留给运营核验与后续国家核验）。
--   3) 1 条策略变更记录（版本号 = 当前最大值 + 1）。
--   本切片 compliance 不调用 Vault（technique.md 2.2「出站」行），RISK_SCREENING_SEARCH_TERM_V1 不在本文件。
-- 执行前核对：先执行 db/vault/dml-check.sql，五项计数必须全为 0；任一 INSERT 主键 / 唯一键冲突时 mysql 客户端中止，事务未提交即回滚。
-- 执行后核验：db/vault/dml-verify.sql（期望 1 个类型、2 条规则、1 条变更记录，版本号 = 执行前最大值 + 1）。
-- 回滚：db/vault/dml-rollback.sql（只在 client-master 尚无 LEGAL_IDENTIFIER_V1 Token 引用时执行；已有 Token 时改为停用规则）。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SET @legal_identifier_time = UTC_TIMESTAMP(6);
START TRANSACTION;
SELECT MAX(`policy_version_no`) + 1 INTO @legal_identifier_policy_version FROM `t_vault_policy_change` FOR UPDATE;
INSERT INTO `t_vault_data_type` (`data_type_id`, `data_type_code`, `token_mode`, `data_class`, `value_format`, `value_pattern`, `description`,
  `manual_protect_enabled`, `manual_reveal_enabled`, `status`, `version`, `create_time`, `update_time`) VALUES
  (9100000000000760, 'LEGAL_IDENTIFIER_V1', 'STABLE', 'STANDARD', 'TEXT', NULL,
   '法定证件号（登记号、税号、身份证件号、护照号等，client-master 规范化后原文；入驻证件唯一性与核验）', 0, 0, 'ACTIVE', 1,
   @legal_identifier_time, @legal_identifier_time);
INSERT INTO `t_vault_access_rule` (`access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`, `version`,
  `create_time`, `update_time`) VALUES
  (9100000000000761, 'client-master-service', 'PROTECT', 'LEGAL_IDENTIFIER_V1', 'CLIENT_ONBOARDING_IDENTIFIER', 'ACTIVE', 1,
   @legal_identifier_time, @legal_identifier_time),
  (9100000000000762, 'client-master-service', 'REVEAL', 'LEGAL_IDENTIFIER_V1', 'CLIENT_ONBOARDING_IDENTIFIER', 'ACTIVE', 1,
   @legal_identifier_time, @legal_identifier_time);
INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
  `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
  (9100000000000760, @legal_identifier_policy_version, 'SEED', 'POLICY', 'CLIENT_ONBOARDING_LEGAL_IDENTIFIER', NULL,
   '{"dataTypes": ["LEGAL_IDENTIFIER_V1"], "accessRules": 2, "callers": ["client-master-service"], "purposes": ["CLIENT_ONBOARDING_IDENTIFIER"]}',
   '全球合规入驻切片 1：client-master 在入驻草稿与人员写入时 Protect 规范化证件号（STABLE 兼作唯一键），Reveal 预留核验（requirements/20261027/client-master-全球合规入驻与资格切片1）', 'SYSTEM',
   'vault-policy-client-onboarding-legal-identifier', @legal_identifier_time);
COMMIT;
