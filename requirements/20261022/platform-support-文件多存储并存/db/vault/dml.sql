-- mor_vault · 文件存储实例凭据的数据类型与访问规则（requirements/20261022/platform-support-文件多存储并存，technique.md 第 4.2、5 节）
-- 性质：发布执行文件，未执行。目标库 mor_vault（本机 local 与 mor-test 同一文件）；Vault 按刷新间隔全量重载策略，不需重启。
-- 影响：
--   1) 1 个数据类型 FILE_STORAGE_CREDENTIAL_V1：REFERENCE / SECRET / JSON，值为 {"accessKeyId","secretAccessKey"}。
--   2) 2 条访问规则：platform-support-service PROTECT（用途 FILE_STORAGE_SETUP，运营新建实例与更换密钥）、
--      REVEAL（用途 FILE_STORAGE_ACCESS，每次存储操作前读取，不缓存）。
--   3) 1 条策略变更记录（版本号 = 当前最大值 + 1）。
-- 执行前核对：t_vault_data_type / t_vault_access_rule / t_vault_policy_change 中不存在 ID 9100000000000740—9100000000000742，
--   不存在 FILE_STORAGE_CREDENTIAL_V1，不存在 platform-support-service 对该类型的规则。
-- 执行后核验：SELECT data_type_code, token_mode, data_class, value_format, status FROM t_vault_data_type WHERE data_type_id = 9100000000000740;
--             SELECT caller_service, action, purpose_code, status FROM t_vault_access_rule WHERE access_rule_id IN (9100000000000741, 9100000000000742);
-- 回滚（只在没有 FILE_STORAGE_CREDENTIAL_V1 记录被 t_file_storage_instance 引用时执行）：
--   DELETE FROM t_vault_access_rule WHERE access_rule_id IN (9100000000000741, 9100000000000742);
--   DELETE FROM t_vault_data_type WHERE data_type_id = 9100000000000740;
--   DELETE FROM t_vault_policy_change WHERE policy_change_id = 9100000000000740;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SET @file_storage_credential_time = UTC_TIMESTAMP(6);
START TRANSACTION;
SELECT MAX(`policy_version_no`) + 1 INTO @file_storage_credential_policy_version FROM `t_vault_policy_change` FOR UPDATE;
INSERT INTO `t_vault_data_type` (`data_type_id`, `data_type_code`, `token_mode`, `data_class`, `value_format`, `value_pattern`, `description`,
  `manual_protect_enabled`, `manual_reveal_enabled`, `status`, `version`, `create_time`, `update_time`) VALUES
  (9100000000000740, 'FILE_STORAGE_CREDENTIAL_V1', 'REFERENCE', 'SECRET', 'JSON', NULL,
   '文件存储实例访问密钥（Platform 存储管理，{"accessKeyId","secretAccessKey"}）', 0, 0, 'ACTIVE', 1,
   @file_storage_credential_time, @file_storage_credential_time);
INSERT INTO `t_vault_access_rule` (`access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`, `version`,
  `create_time`, `update_time`) VALUES
  (9100000000000741, 'platform-support-service', 'PROTECT', 'FILE_STORAGE_CREDENTIAL_V1', 'FILE_STORAGE_SETUP', 'ACTIVE', 1,
   @file_storage_credential_time, @file_storage_credential_time),
  (9100000000000742, 'platform-support-service', 'REVEAL', 'FILE_STORAGE_CREDENTIAL_V1', 'FILE_STORAGE_ACCESS', 'ACTIVE', 1,
   @file_storage_credential_time, @file_storage_credential_time);
INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
  `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
  (9100000000000740, @file_storage_credential_policy_version, 'SEED', 'POLICY', 'PLATFORM_FILE_STORAGE_CREDENTIAL', NULL,
   '{"dataTypes": ["FILE_STORAGE_CREDENTIAL_V1"], "accessRules": 2, "callers": ["platform-support-service"], "purposes": ["FILE_STORAGE_SETUP", "FILE_STORAGE_ACCESS"]}',
   '文件存储管理：Platform 在运营新建实例与更换密钥时 Protect 存储账号密钥，每次存储操作前 Reveal（requirements/20261022/platform-support-文件多存储并存）', 'SYSTEM',
   'vault-policy-file-storage-credential', @file_storage_credential_time);
COMMIT;
