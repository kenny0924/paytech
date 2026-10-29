-- =====================================================================================================================
-- REQ-03 data-query 只读数据源 MOR_RISK_DECISION_REPLICA（technique.md 10.2，S-10）。发布动作，由主会话执行；阶段 4 未在任何库执行。
-- 第 1 步（DBA，MySQL root）：给现有只读账号加 risk-decision 库只读授权（与其他 6 个 MOR_*_REPLICA 一致）。
--   mor-test：mysql 容器内 root 执行下方 GRANT；本机 e2e：对 mor_risk_decision_v2e2e 执行同样授权（库名按环境替换）。
--   授权扩大到风控库的约束：GOLIVE_UNDERWRITING_APPROVED 模板只读 t_client_underwriting，不得为本数据源登记读取规则、评估等其他表的模板。
-- 第 2 步（运营控制台"数据查询 → 数据源 → 新建"，非 DML）：
--   编码 MOR_RISK_DECISION_REPLICA；类型 MYSQL；主机 mysql；端口 3306；库 mor_risk_decision；账号 data_query_demo_reader；
--   口令沿用 Vault 中该账号（DATA_SOURCE_PASSWORD_V1，只在新建时输入一次）；连接检查三步通过后启用。
-- 第 3 步（运营控制台"数据查询 → 模板 → 新建"）：按 data-query-templates.sql 头部登记 GOLIVE_UNDERWRITING_APPROVED。
-- 回滚：停用模板与数据源 → REVOKE（见末尾）。
-- =====================================================================================================================

-- 第 1 步：授权（mor-test）
GRANT SELECT ON `mor_risk_decision`.* TO 'data_query_demo_reader'@'%';

-- 核对
SHOW GRANTS FOR 'data_query_demo_reader'@'%';

-- 回滚（停用模板与数据源之后）：
-- REVOKE SELECT ON `mor_risk_decision`.* FROM 'data_query_demo_reader'@'%';
