-- mor_vault · 数据源账号密码的数据类型与访问规则（requirements/20261022/platform-support-数据查询服务，technique.md 第 5.3 节）
-- 性质：发布执行文件。目标库 mor_vault（本机 local 与 mor-test 同一文件）；Vault 按刷新间隔全量重载策略，不需重启。
--   本机 local 已于 2026-10-22 执行；mor-test 未执行。
-- 影响：
--   1) 1 个数据类型 DATA_SOURCE_PASSWORD_V1：REFERENCE / SECRET / TEXT，值为被查库只读账号的密码原文。
--   2) 2 条访问规则：platform-support-service PROTECT（用途 DATA_SOURCE_CONFIGURATION，运营新建或修改数据源时保存密码）、
--      REVEAL（用途 DATA_SOURCE_CONNECTION，建立连接池与连接测试前读取）。
--   3) 1 条策略变更记录（版本号 = 当前最大值 + 1）。
-- 执行前核对：t_vault_data_type / t_vault_access_rule / t_vault_policy_change 中不存在 ID 9100000000000750—9100000000000752，
--   不存在 DATA_SOURCE_PASSWORD_V1，不存在 platform-support-service 对该类型的规则。
-- 执行后核验：SELECT data_type_code, token_mode, data_class, value_format, status FROM t_vault_data_type WHERE data_type_id = 9100000000000750;
--             SELECT caller_service, action, purpose_code, status FROM t_vault_access_rule WHERE access_rule_id IN (9100000000000751, 9100000000000752);
-- 回滚（只在没有 DATA_SOURCE_PASSWORD_V1 记录被 t_data_source 引用时执行）：
--   DELETE FROM t_vault_access_rule WHERE access_rule_id IN (9100000000000751, 9100000000000752);
--   DELETE FROM t_vault_data_type WHERE data_type_id = 9100000000000750;
--   DELETE FROM t_vault_policy_change WHERE policy_change_id = 9100000000000750;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_vault`;
SET @data_source_password_time = UTC_TIMESTAMP(6);
START TRANSACTION;
SELECT MAX(`policy_version_no`) + 1 INTO @data_source_password_policy_version FROM `t_vault_policy_change` FOR UPDATE;
INSERT INTO `t_vault_data_type` (`data_type_id`, `data_type_code`, `token_mode`, `data_class`, `value_format`, `value_pattern`, `description`,
  `manual_protect_enabled`, `manual_reveal_enabled`, `status`, `version`, `create_time`, `update_time`) VALUES
  (9100000000000750, 'DATA_SOURCE_PASSWORD_V1', 'REFERENCE', 'SECRET', 'TEXT', NULL,
   '数据查询数据源的只读账号密码（Platform data-query）', 0, 0, 'ACTIVE', 1,
   @data_source_password_time, @data_source_password_time);
INSERT INTO `t_vault_access_rule` (`access_rule_id`, `caller_service`, `action`, `data_type_code`, `purpose_code`, `status`, `version`,
  `create_time`, `update_time`) VALUES
  (9100000000000751, 'platform-support-service', 'PROTECT', 'DATA_SOURCE_PASSWORD_V1', 'DATA_SOURCE_CONFIGURATION', 'ACTIVE', 1,
   @data_source_password_time, @data_source_password_time),
  (9100000000000752, 'platform-support-service', 'REVEAL', 'DATA_SOURCE_PASSWORD_V1', 'DATA_SOURCE_CONNECTION', 'ACTIVE', 1,
   @data_source_password_time, @data_source_password_time);
INSERT INTO `t_vault_policy_change` (`policy_change_id`, `policy_version_no`, `change_type`, `target_type`, `target_key`, `before_snapshot`,
  `after_snapshot`, `reason`, `actor_id`, `request_id`, `create_time`) VALUES
  (9100000000000750, @data_source_password_policy_version, 'SEED', 'POLICY', 'PLATFORM_DATA_SOURCE_PASSWORD', NULL,
   '{"dataTypes": ["DATA_SOURCE_PASSWORD_V1"], "accessRules": 2, "callers": ["platform-support-service"], "purposes": ["DATA_SOURCE_CONFIGURATION", "DATA_SOURCE_CONNECTION"]}',
   '数据查询：Platform 在运营新建或修改数据源时 Protect 只读账号密码，建立连接前 Reveal（requirements/20261022/platform-support-数据查询服务）', 'SYSTEM',
   'vault-policy-data-source-password', @data_source_password_time);
COMMIT;
