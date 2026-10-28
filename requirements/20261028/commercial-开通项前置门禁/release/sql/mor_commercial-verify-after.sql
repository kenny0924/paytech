-- REQ-01 开通项前置门禁 · mor_commercial 迁移后核对（READ ONLY 会话；迁移脚本执行后、起新 commercial 镜像前执行）。
-- 期望：execution_status 列存在；status 只有 ACTIVE / DISABLED；ACTIVE 行 execution_status 非空、DISABLED 行为空；
--       现网 15 行全部 ACTIVE + ENABLED（与阶段 6 读数一致；若执行当天行数不同，以 mor_commercial-precheck.sql 当天读数为准，要求 ENABLED 数 = 迁移前 ENABLED 数）。
SELECT 'by_status' AS t, status, IFNULL(execution_status, '(null)') AS execution_status, COUNT(*) AS n
  FROM mor_commercial.t_agreement_capability GROUP BY status, execution_status ORDER BY 2, 3;
SELECT 'violations' AS t, COUNT(*) AS n FROM mor_commercial.t_agreement_capability
 WHERE status NOT IN ('ACTIVE', 'DISABLED')
    OR (status = 'ACTIVE' AND execution_status IS NULL)
    OR (status = 'DISABLED' AND execution_status IS NOT NULL);
SELECT 'index' AS t, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = 'mor_commercial' AND TABLE_NAME = 't_agreement_capability' GROUP BY INDEX_NAME ORDER BY INDEX_NAME;
SELECT 'consumed_tables' AS t, COUNT(*) AS n FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'mor_commercial' AND TABLE_NAME IN ('t_consumed_event', 't_consumed_aggregate_version');
