-- 联合查询 · mor-test 演示只读授权（root 执行，阶段 7 第 ⑦ 步；本机 / 自动测试不用）。
-- data_query_demo_reader 现有：mor_acquiring / mor_channel_connector / mor_client_master / mor_commercial / mor_ledger / mor_reconciliation 库级 SELECT，
-- mor_risk_decision.t_client_underwriting 表级 SELECT（2026-10-30 只读核对）。本脚本只加三个库的库级 SELECT，供数据源
-- MOR_PAYMENT_REPLICA / MOR_BILLING_REPLICA / MOR_ACCOUNT_REPLICA 使用。不建账号、不改口令、不授予任何写权限。可重跑（GRANT 幂等）。
-- 回滚：root-demo-reader-grants-rollback.sql。
GRANT SELECT ON `mor_payment`.* TO 'data_query_demo_reader'@'%';
GRANT SELECT ON `mor_billing`.* TO 'data_query_demo_reader'@'%';
GRANT SELECT ON `mor_account`.* TO 'data_query_demo_reader'@'%';
SHOW GRANTS FOR 'data_query_demo_reader'@'%';
