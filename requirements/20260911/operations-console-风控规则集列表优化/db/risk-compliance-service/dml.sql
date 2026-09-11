-- 阶段 3 评审稿，未执行；目标 mor_risk，先应用同目录 ddl.sql。
-- 环境相关输入：在同一 mysql --batch 会话中，先设置 @risk_rule_set_manifest_json。
-- 值为审阅过的完整 JSON 数组；每项只有 ruleSetId、decisionPointId、ruleSetCode，
-- ID 为十进制字符串。ruleSetId 必须由项目 Snowflake 能力离线生成并单独核对唯一性。
-- 不在这里用 MAX(id)+1、随机数、版本 ID 或 SQL 时间公式冒充新根身份。
-- test manifest 在阶段 6 按目标环境生成并冻结；local 研发验证需要时可在阶段 4 单独准备并确认。
-- 当前文件没有默认数据或示例可执行 ID，不同环境不得混用 manifest。
-- 保持 Risk 停写；先备份版本/Hash/幂等摘要。禁止 mysql --force，失败必须断开会话回滚。
SET @risk_saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,NO_ZERO_DATE,NO_ZERO_IN_DATE';
CREATE TEMPORARY TABLE risk_rule_set_dml_gate (checkpoint VARCHAR(100) NOT NULL);
INSERT INTO risk_rule_set_dml_gate
SELECT IF(DATABASE() = 'mor_risk', 'approved database name', NULL);
INSERT INTO risk_rule_set_dml_gate
SELECT IF(COALESCE(JSON_VALID(@risk_rule_set_manifest_json), 0) = 1, 'manifest JSON provided', NULL);
INSERT INTO risk_rule_set_dml_gate
SELECT IF(JSON_TYPE(CAST(@risk_rule_set_manifest_json AS JSON)) = 'ARRAY', 'manifest is array', NULL);
INSERT INTO risk_rule_set_dml_gate
SELECT IF(NOT EXISTS (
  SELECT 1 FROM (
    SELECT JSON_EXTRACT(@risk_rule_set_manifest_json, CONCAT('$[', item.ordinal_no - 1, ']')) entry
    FROM JSON_TABLE(@risk_rule_set_manifest_json, '$[*]' COLUMNS (ordinal_no FOR ORDINALITY)) item
  ) entries
  WHERE NOT COALESCE(JSON_TYPE(entry) = 'OBJECT' AND JSON_LENGTH(entry) = 3
    AND JSON_TYPE(JSON_EXTRACT(entry, '$.ruleSetId')) = 'STRING'
    AND JSON_TYPE(JSON_EXTRACT(entry, '$.decisionPointId')) = 'STRING'
    AND JSON_TYPE(JSON_EXTRACT(entry, '$.ruleSetCode')) = 'STRING'
    AND CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(entry, '$.ruleSetId'))) <= 19
    AND CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(entry, '$.decisionPointId'))) <= 19
    AND CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(entry, '$.ruleSetCode'))) <= 64, 0)),
  'manifest exact object fields and string types', NULL);
CREATE TEMPORARY TABLE risk_rule_set_manifest (
  rule_set_id_text VARCHAR(32) NOT NULL,
  decision_point_id_text VARCHAR(32) NOT NULL,
  rule_set_code VARCHAR(64) NOT NULL,
  PRIMARY KEY (rule_set_id_text),
  UNIQUE KEY uk_manifest_code (decision_point_id_text, rule_set_code)
);
INSERT INTO risk_rule_set_manifest
SELECT rule_set_id, point_id, rule_code
FROM JSON_TABLE(@risk_rule_set_manifest_json, '$[*]' COLUMNS (
  rule_set_id VARCHAR(32) PATH '$.ruleSetId' ERROR ON EMPTY ERROR ON ERROR,
  point_id VARCHAR(32) PATH '$.decisionPointId' ERROR ON EMPTY ERROR ON ERROR,
  rule_code VARCHAR(64) PATH '$.ruleSetCode' ERROR ON EMPTY ERROR ON ERROR
)) manifest;
INSERT INTO risk_rule_set_dml_gate
SELECT IF(NOT EXISTS (SELECT 1 FROM risk_rule_set_manifest
  WHERE NOT REGEXP_LIKE(rule_set_id_text, '^[1-9][0-9]{0,18}$', 'c')
     OR NOT REGEXP_LIKE(decision_point_id_text, '^[1-9][0-9]{0,18}$', 'c')
     OR CAST(rule_set_id_text AS DECIMAL(20,0)) > 9223372036854775807
     OR CAST(decision_point_id_text AS DECIMAL(20,0)) > 9223372036854775807
     OR NOT REGEXP_LIKE(rule_set_code, '^[A-Z][A-Z0-9_]{0,63}$', 'c')),
  'manifest canonical IDs and codes', NULL);
SELECT COUNT(*) INTO @risk_rule_set_manifest_count FROM risk_rule_set_manifest;
START TRANSACTION;
INSERT INTO risk_rule_set_dml_gate
SELECT IF(NOT EXISTS (SELECT 1 FROM t_rule_set), 'root empty, never rerun over live roots', NULL);
INSERT INTO risk_rule_set_dml_gate
SELECT IF(NOT EXISTS (SELECT 1 FROM t_rule_set_version
  WHERE NOT REGEXP_LIKE(rule_set_code, '^[A-Z][A-Z0-9_]{0,63}$', 'c')
     OR version_no < 1), 'historical code and version format valid', NULL);
INSERT INTO risk_rule_set_dml_gate
SELECT IF(@risk_rule_set_manifest_count =
  (SELECT COUNT(*) FROM (SELECT decision_point_id, rule_set_code
     FROM t_rule_set_version GROUP BY decision_point_id, rule_set_code) history)
  AND NOT EXISTS (SELECT 1 FROM risk_rule_set_manifest manifest
    WHERE NOT EXISTS (SELECT 1 FROM t_rule_set_version history
      WHERE history.decision_point_id = CAST(manifest.decision_point_id_text AS UNSIGNED)
        AND BINARY history.rule_set_code = BINARY manifest.rule_set_code)),
  'manifest exactly covers historical identities', NULL);
INSERT INTO risk_rule_set_dml_gate
SELECT IF(NOT EXISTS (
  SELECT 1 FROM t_rule_set_version latest
  WHERE NOT EXISTS (SELECT 1 FROM t_rule_set_version newer
      WHERE newer.decision_point_id = latest.decision_point_id
        AND newer.rule_set_code = latest.rule_set_code AND newer.version_no > latest.version_no)
    AND (latest.status NOT IN ('DRAFT','FROZEN')
      OR NOT EXISTS (SELECT 1 FROM t_decision_point point
                     WHERE point.decision_point_id = latest.decision_point_id))),
  'current heads and point ownership valid', NULL);
INSERT INTO t_rule_set (
  rule_set_id, decision_point_id, rule_set_code, current_rule_set_version_id, status, version,
  deleted_by, deleted_time, delete_reason_code, delete_trigger_id, create_time, update_time)
SELECT CAST(manifest.rule_set_id_text AS UNSIGNED), latest.decision_point_id, latest.rule_set_code,
       latest.rule_set_version_id, 'CONFIGURABLE', 1,
       NULL, NULL, NULL, NULL, earliest.create_time, latest.update_time
FROM risk_rule_set_manifest manifest
JOIN t_rule_set_version latest
  ON latest.decision_point_id = CAST(manifest.decision_point_id_text AS UNSIGNED)
 AND BINARY latest.rule_set_code = BINARY manifest.rule_set_code
JOIN (SELECT decision_point_id, rule_set_code, MIN(version_no) first_no, MAX(version_no) last_no
      FROM t_rule_set_version GROUP BY decision_point_id, rule_set_code) bounds
  ON bounds.decision_point_id = latest.decision_point_id
 AND bounds.rule_set_code = latest.rule_set_code AND bounds.last_no = latest.version_no
JOIN t_rule_set_version earliest
  ON earliest.decision_point_id = bounds.decision_point_id
 AND earliest.rule_set_code = bounds.rule_set_code AND earliest.version_no = bounds.first_no;
INSERT INTO risk_rule_set_dml_gate
SELECT IF((SELECT COUNT(*) FROM t_rule_set) = (SELECT COUNT(*) FROM risk_rule_set_manifest),
  'backfill count matches manifest', NULL);
COMMIT;
DROP TEMPORARY TABLE risk_rule_set_manifest;
DROP TEMPORARY TABLE risk_rule_set_dml_gate;
SET SESSION sql_mode = @risk_saved_sql_mode;
-- 本脚本不更新已有版本、Point、Release、检查、幂等或执行表。
-- 根为空但历史也为空时须显式提供 []；运行后初始化/回填均不得由 Java 自动补齐。
