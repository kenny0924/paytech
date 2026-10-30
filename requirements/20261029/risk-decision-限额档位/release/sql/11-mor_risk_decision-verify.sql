-- REQ-04 · mor_risk_decision 前滚后核对（runbook ④；READ ONLY 会话可执行）。
SET NAMES utf8mb4;
-- 新表 3；表数 = 预检 + 3
SELECT COUNT(*) AS `new_tables` FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_client_limit_tier', 't_client_limit_tier_change', 't_client_allowed_daily_total');
SELECT COUNT(*) AS `tables_after` FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';
-- 索引（表、索引、唯一）
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS `cols` FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('t_client_limit_tier', 't_client_limit_tier_change', 't_client_allowed_daily_total')
  GROUP BY TABLE_NAME, INDEX_NAME, NON_UNIQUE ORDER BY TABLE_NAME, INDEX_NAME;
-- 四张 TIER 名单：300001—300004 / TIER / CLIENT / EXACT / ACTIVE
SELECT list_id, list_code, list_purpose, supported_subject_type, matching_mode, status FROM t_risk_list WHERE list_purpose = 'TIER' ORDER BY list_id;
-- 新表为空（迁移前）
SELECT (SELECT COUNT(*) FROM t_client_limit_tier) AS `tiers`, (SELECT COUNT(*) FROM t_client_limit_tier_change) AS `changes`,
       (SELECT COUNT(*) FROM t_client_allowed_daily_total) AS `daily_totals`;
-- 五处注释已更新（含新代码值）
SELECT TABLE_NAME, COLUMN_NAME, (COLUMN_COMMENT LIKE '%DAILY_SUM_AMOUNT%' OR COLUMN_COMMENT LIKE '%UTC 当日%' OR COLUMN_COMMENT LIKE '%TIER%'
  OR COLUMN_COMMENT LIKE '%_ALLOWED%') AS `comment_updated` FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 't_velocity_feature_definition' AND COLUMN_NAME IN ('metric', 'window_seconds', 'event_code'))
    OR (TABLE_NAME = 't_risk_list' AND COLUMN_NAME = 'list_purpose') OR (TABLE_NAME = 't_risk_list_entry' AND COLUMN_NAME = 'source_type'))
  ORDER BY TABLE_NAME, COLUMN_NAME;
