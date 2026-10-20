-- 阶段 5 本机全链路（任务卡 14）：为 V2 服务新建 *_v2e2e 专用库并授权既有应用账号。评审后由主会话以 root 执行。
-- 为什么不用 mor_*：各服务 auto 测试以 mor_* 为独占库（全局 AfterEach 清空表、拒绝脏环境复用），全链路数据写入会让并行会话的测试失败；
--                   旧 *_e2e（mor_platform_e2e / mor_payment_e2e / mor_vault_e2e）属于旧架构全链路，保留不动。
-- 账号：全部已存在（01-create-databases-and-users.sh 或各 *-test 的 init/ddl.sql 创建），本文件不建账号、不改口令。
-- 权限：与各 *-test init/ddl.sql 相同的 SELECT/INSERT/UPDATE/DELETE；建表由 root 执行 apply-e2e.sh 完成。
-- 回滚：DROP DATABASE 下列 15 个库（库内只有本步全链路数据）；REVOKE 随库删除失效，无需单独执行。
CREATE DATABASE IF NOT EXISTS mor_client_master_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_commercial_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_compliance_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_risk_decision_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_acquiring_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_counterparty_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_payment_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_card_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_channel_connector_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_billing_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_ledger_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_account_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_reconciliation_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_tax_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_platform_v2e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT SELECT, INSERT, UPDATE, DELETE ON mor_client_master_v2e2e.* TO 'client_master_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_commercial_v2e2e.* TO 'commercial_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_compliance_v2e2e.* TO 'compliance_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_risk_decision_v2e2e.* TO 'risk_decision_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_acquiring_v2e2e.* TO 'acquiring_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_counterparty_v2e2e.* TO 'counterparty_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_payment_v2e2e.* TO 'payment_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_card_v2e2e.* TO 'card_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_channel_connector_v2e2e.* TO 'channel_connector_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_billing_v2e2e.* TO 'billing_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_ledger_v2e2e.* TO 'ledger_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_account_v2e2e.* TO 'account_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_reconciliation_v2e2e.* TO 'reconciliation_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_tax_v2e2e.* TO 'tax_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_platform_v2e2e.* TO 'platform_app'@'%';
