-- REQ-04 · 回收 40 的表级授权（回滚 R10）。
REVOKE SELECT ON mor_risk_decision.t_client_limit_tier FROM 'data_query_demo_reader'@'%';
SHOW GRANTS FOR 'data_query_demo_reader'@'%';
