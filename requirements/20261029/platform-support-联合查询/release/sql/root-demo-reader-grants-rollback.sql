-- 联合查询 · 撤销演示只读授权（root 执行）。先把数据源 MOR_PAYMENT_REPLICA / MOR_BILLING_REPLICA / MOR_ACCOUNT_REPLICA 停用（运营 API），
-- 否则其模板执行会报权限错误。只撤这三条，其余库的 SELECT 保持。重复执行会报 1141（无此授权），可忽略。
REVOKE SELECT ON `mor_payment`.* FROM 'data_query_demo_reader'@'%';
REVOKE SELECT ON `mor_billing`.* FROM 'data_query_demo_reader'@'%';
REVOKE SELECT ON `mor_account`.* FROM 'data_query_demo_reader'@'%';
SHOW GRANTS FOR 'data_query_demo_reader'@'%';
