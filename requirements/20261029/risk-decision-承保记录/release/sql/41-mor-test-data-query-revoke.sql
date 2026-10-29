-- REQ-03 · DBA：回收 40 的授权（回滚 ⑨；先经运营 API 把模板与数据源置 INACTIVE）。
-- 若要回滚 mor_risk_decision 表（90），须先执行本文件：DROP TABLE 会留下孤立的表级授权行。
REVOKE SELECT ON `mor_risk_decision`.`t_client_underwriting` FROM 'data_query_demo_reader'@'%';
SHOW GRANTS FOR 'data_query_demo_reader'@'%';
