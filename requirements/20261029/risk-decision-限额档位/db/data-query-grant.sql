-- =====================================================================================================================
-- REQ-04 data-query 只读授权（technique.md 8.5，F-12）。发布动作，由主会话 / DBA 执行；阶段 4 未在任何库执行。
-- mor-test 的只读账号 data_query_demo_reader 为表级授权（REQ-03 已有 t_client_underwriting）；本需求只追加新档位表一张，
-- GOLIVE_LIMIT_TIER 模板只读该表。本机 e2e：对 mor_risk_decision_v2e2e 执行同样授权（库名按环境替换）。
-- 顺序：risk-decision 前滚 DDL（t_client_limit_tier 已存在）→ 本授权 → 登记 GOLIVE_LIMIT_TIER（data-query-templates.sql）。
-- 回滚：停用模板 → REVOKE（见末尾）。
-- =====================================================================================================================

-- 授权（mor-test，MySQL root）
GRANT SELECT ON `mor_risk_decision`.`t_client_limit_tier` TO 'data_query_demo_reader'@'%';

-- 核对：应包含上一行与 REQ-03 的 t_client_underwriting 授权
SHOW GRANTS FOR 'data_query_demo_reader'@'%';

-- 回滚（停用 GOLIVE_LIMIT_TIER 模板之后）：
-- REVOKE SELECT ON `mor_risk_decision`.`t_client_limit_tier` FROM 'data_query_demo_reader'@'%';
