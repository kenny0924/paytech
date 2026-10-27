-- 2026-10-27 全球合规入驻与资格切片 1（client-master，遗留行回填与个人证件号脱敏；发布决策 P-11）。
-- 需求：requirements/20261027/client-master-全球合规入驻与资格切片1/release/runbook.md 第 1 节 P-8 / P-11、第 3.1 节。
-- 目标库：mor_client_master（mor-test）、mor_client_master_v2e2e（本地 e2e）。由开发者审核后用具 DML 权限的账号手工执行，应用不会执行本文件。
--
-- 口径（P-11）：DDL 保留的遗留行必须满足新列语义；个人证件号脱敏，不做 Token 化（遗留号码不再参与新入驻的唯一性比对）。
--   1. 个人号码判定：属于 INDIVIDUAL Party，或标识类型在 t_rule_setting 中 VAULT_REQUIRED = '1'（与脱敏核验相同的并集）。
--      t_party_identity_key.identifier_key_value = 'LEGACY-REDACTED-<identity_key_id>'；
--      t_party_profile_identifier.public_identifier_value = NULL，identifier_masked_value = '****' + 原号码末 4 位，identifier_vault_token 保持为空。
--      机构公开登记号保持明文。已是 Vault Token（43 位 Base64URL）或已脱敏的行不改。
--   2. 申请：application_type = 'ONBOARDING'（DDL 已由列默认值写入，此处兜底）；jurisdiction_code 取 Client 的注册 / 居住国
--      （个人：residence_country，否则 RESIDENTIAL 地址国家；机构：incorporation_country，否则 REGISTERED 地址国家），
--      映射到 country_code 相同的 ACTIVE 辖区（优先 jurisdiction_code = 国家码），否则 'LEGACY'；schema_id = NULL（不为 0）；
--      已提交过的申请（submitted_time 非空）submission_round = 1，其余保持 0。
--   3. t_client.jurisdiction_code 为空时取该 Client 最近一次申请的 jurisdiction_code（含 LEGACY）。
--   遗留 Party 资料的 jurisdiction_code、地址的 address_format_code 保持为空（遗留资料不是按国家包采集的）。
-- 前置：已执行 2026-10-27-onboarding-global.sql（DDL）、2026-10-27-onboarding-global-cleanup.sql 与
--       2026-10-27-onboarding-country-pack.sql；client-master 服务已停止。
-- 停止条件：DDL 未执行，或没有任何 ACTIVE 辖区（国家包未执行会把全部遗留申请回填为 LEGACY）时 cm_backfill_assert SIGNAL 中止，尚未写入。
-- 执行：mysql 客户端整文件执行；写入在一个事务内，任一语句失败时连接断开即回滚。可重跑：只处理尚未满足新语义的行。
-- 回滚：从执行前备份恢复（脱敏不可逆）。
-- 核验：执行后运行 requirements/20261027/client-master-全球合规入驻与资格切片1/db/client-master/legacy-redaction-verify.sql，前 6 项全 0。
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @cm_token_re = '^[A-Za-z0-9_-]{43}$';

-- ===== 0. 预检（只读）=====
DROP PROCEDURE IF EXISTS `cm_backfill_assert`;
DELIMITER //
CREATE PROCEDURE `cm_backfill_assert`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                  AND table_name = 't_client_application' AND column_name = 'schema_id' AND is_nullable = 'YES') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: run 2026-10-27-onboarding-global.sql (DDL with nullable schema_id) first';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM `t_jurisdiction` WHERE `status` = 'ACTIVE') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: no ACTIVE jurisdiction; run 2026-10-27-onboarding-country-pack.sql first';
  END IF;
END //
DELIMITER ;
CALL `cm_backfill_assert`();
DROP PROCEDURE IF EXISTS `cm_backfill_assert`;

-- 0.1 待回填行数（回填后再执行应全 0）
DROP TEMPORARY TABLE IF EXISTS `tmp_cm_personal_type`;
CREATE TEMPORARY TABLE `tmp_cm_personal_type` (`identifier_type` VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NOT NULL PRIMARY KEY);
INSERT INTO `tmp_cm_personal_type` (`identifier_type`)
SELECT DISTINCT `scope_code` FROM `t_rule_setting`
 WHERE `scope_type` = 'IDENTIFIER_TYPE' AND `rule_code` = 'VAULT_REQUIRED' AND `rule_value` = '1' AND `status` = 'ACTIVE';
SELECT COUNT(*) AS identity_key_to_redact
  FROM `t_party_identity_key` k JOIN `t_party` p ON p.`party_id` = k.`party_id`
 WHERE (p.`party_type` = 'INDIVIDUAL' OR k.`identifier_type` IN (SELECT `identifier_type` FROM `tmp_cm_personal_type`))
   AND NOT (k.`identifier_key_value` REGEXP @cm_token_re OR k.`identifier_key_value` = CONCAT('LEGACY-REDACTED-', k.`identity_key_id`));
SELECT COUNT(*) AS profile_identifier_to_redact
  FROM `t_party_profile_identifier` i JOIN `t_party_profile` f ON f.`profile_id` = i.`profile_id` JOIN `t_party` p ON p.`party_id` = f.`party_id`
 WHERE (p.`party_type` = 'INDIVIDUAL' OR i.`identifier_type` IN (SELECT `identifier_type` FROM `tmp_cm_personal_type`))
   AND i.`public_identifier_value` IS NOT NULL;
SELECT COUNT(*) AS applications, COALESCE(SUM(`application_type` = ''), 0) AS app_type_blank,
       COALESCE(SUM(`jurisdiction_code` = ''), 0) AS app_jurisdiction_blank, COALESCE(SUM(`schema_id` = 0), 0) AS app_schema_zero,
       COALESCE(SUM(`submission_round` = 0 AND `submitted_time` IS NOT NULL), 0) AS app_round_zero_submitted
  FROM `t_client_application`;

-- 申请辖区：Client 主体当前资料的国家 → ACTIVE 辖区。
DROP TEMPORARY TABLE IF EXISTS `tmp_cm_client_country`;
CREATE TEMPORARY TABLE `tmp_cm_client_country` (`client_id` BIGINT NOT NULL PRIMARY KEY,
  `country_code` CHAR(2) COLLATE utf8mb4_0900_ai_ci NULL, `jurisdiction_code` VARCHAR(16) COLLATE utf8mb4_0900_ai_ci NULL);
INSERT INTO `tmp_cm_client_country` (`client_id`, `country_code`)
SELECT c.`client_id`,
       CASE p.`party_type`
         WHEN 'INDIVIDUAL' THEN COALESCE(ip.`residence_country`,
           (SELECT MAX(ad.`country_code`) FROM `t_party_address` ad WHERE ad.`profile_id` = f.`profile_id` AND ad.`address_type` = 'RESIDENTIAL'))
         ELSE COALESCE(op.`incorporation_country`,
           (SELECT MAX(ad.`country_code`) FROM `t_party_address` ad WHERE ad.`profile_id` = f.`profile_id` AND ad.`address_type` = 'REGISTERED'))
       END
  FROM `t_client` c
  JOIN `t_party` p ON p.`party_id` = c.`party_id`
  LEFT JOIN `t_party_profile` f ON f.`party_id` = c.`party_id` AND f.`current_slot` = 1
  LEFT JOIN `t_individual_profile` ip ON ip.`profile_id` = f.`profile_id`
  LEFT JOIN `t_organization_profile` op ON op.`profile_id` = f.`profile_id`;
UPDATE `tmp_cm_client_country` t
   SET t.`jurisdiction_code` = COALESCE(
         (SELECT j.`jurisdiction_code` FROM `t_jurisdiction` j WHERE j.`status` = 'ACTIVE' AND j.`country_code` = t.`country_code`
           ORDER BY j.`jurisdiction_code` <> j.`country_code`, j.`jurisdiction_code` LIMIT 1),
         'LEGACY');
SELECT `country_code`, `jurisdiction_code`, COUNT(*) AS clients FROM `tmp_cm_client_country` GROUP BY 1, 2 ORDER BY 1, 2;

-- ===== 1. 回填（一个事务）=====
START TRANSACTION;
UPDATE `t_party_identity_key` k JOIN `t_party` p ON p.`party_id` = k.`party_id`
   SET k.`identifier_key_value` = CONCAT('LEGACY-REDACTED-', k.`identity_key_id`), k.`update_time` = UTC_TIMESTAMP(6)
 WHERE (p.`party_type` = 'INDIVIDUAL' OR k.`identifier_type` IN (SELECT `identifier_type` FROM `tmp_cm_personal_type`))
   AND NOT (k.`identifier_key_value` REGEXP @cm_token_re OR k.`identifier_key_value` = CONCAT('LEGACY-REDACTED-', k.`identity_key_id`));
-- 先由原号码计算末 4 位脱敏值，再清空明文（同一 UPDATE 内按书写顺序赋值）。
UPDATE `t_party_profile_identifier` i JOIN `t_party_profile` f ON f.`profile_id` = i.`profile_id` JOIN `t_party` p ON p.`party_id` = f.`party_id`
   SET i.`identifier_masked_value` = CONCAT('****', RIGHT(i.`public_identifier_value`, 4)),
       i.`public_identifier_value` = NULL,
       i.`update_time` = UTC_TIMESTAMP(6)
 WHERE (p.`party_type` = 'INDIVIDUAL' OR i.`identifier_type` IN (SELECT `identifier_type` FROM `tmp_cm_personal_type`))
   AND i.`public_identifier_value` IS NOT NULL;
UPDATE `t_client_application` SET `application_type` = 'ONBOARDING' WHERE `application_type` = '';
UPDATE `t_client_application` SET `schema_id` = NULL WHERE `schema_id` = 0;
UPDATE `t_client_application` SET `submission_round` = 1 WHERE `submission_round` = 0 AND `submitted_time` IS NOT NULL;
UPDATE `t_client_application` a JOIN `tmp_cm_client_country` t ON t.`client_id` = a.`client_id`
   SET a.`jurisdiction_code` = t.`jurisdiction_code`
 WHERE a.`jurisdiction_code` = '';
UPDATE `t_client` c
   SET c.`jurisdiction_code` = (SELECT a.`jurisdiction_code` FROM `t_client_application` a WHERE a.`client_id` = c.`client_id`
                                 ORDER BY a.`create_time` DESC, a.`application_id` DESC LIMIT 1)
 WHERE c.`jurisdiction_code` IS NULL
   AND EXISTS (SELECT 1 FROM `t_client_application` a WHERE a.`client_id` = c.`client_id`);
COMMIT;

-- ===== 2. 核验（只读）=====
-- 2.1 期望全 0（与 legacy-redaction-verify.sql 前 6 项同口径）
SELECT 'identity_key_plaintext' AS check_item, COUNT(*) AS violations
  FROM `t_party_identity_key` k JOIN `t_party` p ON p.`party_id` = k.`party_id`
 WHERE (p.`party_type` = 'INDIVIDUAL' OR k.`identifier_type` IN (SELECT `identifier_type` FROM `tmp_cm_personal_type`))
   AND NOT (k.`identifier_key_value` REGEXP @cm_token_re OR k.`identifier_key_value` = CONCAT('LEGACY-REDACTED-', k.`identity_key_id`))
UNION ALL
SELECT 'application_blank_or_zero', COUNT(*) FROM `t_client_application`
 WHERE `application_type` = '' OR `jurisdiction_code` = '' OR `schema_id` = 0;
SELECT COUNT(*) AS profile_public_identifier_personal
  FROM `t_party_profile_identifier` i JOIN `t_party_profile` f ON f.`profile_id` = i.`profile_id` JOIN `t_party` p ON p.`party_id` = f.`party_id`
 WHERE (p.`party_type` = 'INDIVIDUAL' OR i.`identifier_type` IN (SELECT `identifier_type` FROM `tmp_cm_personal_type`))
   AND i.`public_identifier_value` IS NOT NULL;
-- 2.2 展示：申请辖区、轮次与 Client 辖区分布
SELECT `application_type`, `jurisdiction_code`, `status`, `submission_round`, (`schema_id` IS NULL) AS schema_null, COUNT(*) AS applications
  FROM `t_client_application` GROUP BY 1, 2, 3, 4, 5 ORDER BY 1, 2, 3, 4;
SELECT `jurisdiction_code`, `status`, COUNT(*) AS clients FROM `t_client` GROUP BY 1, 2 ORDER BY 1, 2;
-- 2.3 展示：遗留标识类型不在标识类型目录中的行（新代码按目录解释类型，非 0 时须确认读取路径）
SELECT 't_party_identity_key' AS t, k.`identifier_type`, COUNT(*) AS n FROM `t_party_identity_key` k
 WHERE NOT EXISTS (SELECT 1 FROM `t_identifier_type` it WHERE it.`identifier_type_code` = k.`identifier_type`) GROUP BY 1, 2
UNION ALL
SELECT 't_party_profile_identifier', i.`identifier_type`, COUNT(*) FROM `t_party_profile_identifier` i
 WHERE i.`identifier_type` IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM `t_identifier_type` it WHERE it.`identifier_type_code` = i.`identifier_type`) GROUP BY 1, 2;

DROP TEMPORARY TABLE IF EXISTS `tmp_cm_personal_type`, `tmp_cm_client_country`;
