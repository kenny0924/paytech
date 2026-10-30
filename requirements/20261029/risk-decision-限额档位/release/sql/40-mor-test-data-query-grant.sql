-- REQ-04 · data-query 只读账号对限额档位表的表级授权（runbook ⑩，DBA / root 执行；须在 ④ 建表之后）。
-- 只授 t_client_limit_tier：GOLIVE_LIMIT_TIER 模板只读这一张表（不读变更记录、名单、累计事件，technique.md S-09）。
GRANT SELECT ON mor_risk_decision.t_client_limit_tier TO 'data_query_demo_reader'@'%';
SHOW GRANTS FOR 'data_query_demo_reader'@'%';
