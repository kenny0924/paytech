-- REQ-03 · DBA：data-query 只读账号授权（runbook ⑨；须在 ① DDL 之后执行——表级授权要求表已存在）。root 执行，mor-test 实例级。
-- 最小权限：只授 t_client_underwriting 一张表（GOLIVE_UNDERWRITING_APPROVED 只读这张表，决定 3.3 / S-10）；不授 mor_risk_decision.*，
-- 因为同库还有决策配置、评估输入等受限数据。现网该账号对其他 6 个库是库级 SELECT（演示用），本次不改。
GRANT SELECT ON `mor_risk_decision`.`t_client_underwriting` TO 'data_query_demo_reader'@'%';
SHOW GRANTS FOR 'data_query_demo_reader'@'%';
