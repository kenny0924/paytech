-- account-service Mock 测试环境准备（管理员手工执行；应用与测试从不自动执行）。
-- 共库过渡（任务卡 07）：本步 account-app 连接 mor_ledger 且只读，表结构由 ledger 的 init/ddl.sql 建立；
-- mor_account 与 t_account_balance 等余额表在第 11b 步新建，届时本文件改为建 mor_account。
-- 测试夹具经测试专用的 ledger_app 连接写入共库的 t_ledger_book / t_account，本账号从不写入。
CREATE USER IF NOT EXISTS 'account_app'@'%' IDENTIFIED BY 'bc991f08e64ba1551ce6e2f8e610defc65e511dfbaf1e37c';
GRANT SELECT ON mor_ledger.* TO 'account_app'@'%';
