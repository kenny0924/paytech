-- 10-create-v2-databases.sql · V2 服务库、最小权限账号与迁移归档库
-- 影响：在 mor-test MySQL 新建 14 个 V2 服务库（若不存在）与对应 *_app 账号，授本库 SELECT/INSERT/UPDATE/DELETE；
--       新建归档库 mor_v2_migration_archive（不建应用账号，只有 root 可读）。不改动已有库、账号与口令。
-- 口令：${MOR_*_DB_PASSWORD} 是 test-environment/.env.test 的变量名占位，由 scripts/migrate.sh 读取 .env.test 后替换，
--       替换结果只经标准输入交给 mysql，不落盘、不打印。口令格式同 initialize.py：[A-Za-z0-9._@%+=:-]{16,128}。
-- 与 initialize.py --create-service-databases 等价（同库名、账号、权限），另加归档库；二者重复执行均无副作用。
-- 前置：root 连接。已存在的账号（如旧 init 脚本建的 payment_app，带 mor_payment ALL）权限不收回，见报告待确认。
-- 回滚：新建库在 30–33 执行前为空：DROP DATABASE <库>; DROP USER '<账号>'@'%';（只删本文件新建且无数据的对象）。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `mor_client_master` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'client_master_app'@'%' IDENTIFIED BY '${MOR_CLIENT_MASTER_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_client_master`.* TO 'client_master_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_commercial` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'commercial_app'@'%' IDENTIFIED BY '${MOR_COMMERCIAL_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_commercial`.* TO 'commercial_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_acquiring` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'acquiring_app'@'%' IDENTIFIED BY '${MOR_ACQUIRING_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_acquiring`.* TO 'acquiring_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_consumer` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'consumer_app'@'%' IDENTIFIED BY '${MOR_CONSUMER_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_consumer`.* TO 'consumer_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_payment` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'payment_app'@'%' IDENTIFIED BY '${MOR_PAYMENT_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_payment`.* TO 'payment_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_channel_connector` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'channel_connector_app'@'%' IDENTIFIED BY '${MOR_CHANNEL_CONNECTOR_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_channel_connector`.* TO 'channel_connector_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_card` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'card_app'@'%' IDENTIFIED BY '${MOR_CARD_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_card`.* TO 'card_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_risk_decision` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'risk_decision_app'@'%' IDENTIFIED BY '${MOR_RISK_DECISION_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_risk_decision`.* TO 'risk_decision_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_compliance` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'compliance_app'@'%' IDENTIFIED BY '${MOR_COMPLIANCE_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_compliance`.* TO 'compliance_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_ledger` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'ledger_app'@'%' IDENTIFIED BY '${MOR_LEDGER_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_ledger`.* TO 'ledger_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_billing` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'billing_app'@'%' IDENTIFIED BY '${MOR_BILLING_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_billing`.* TO 'billing_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_account` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'account_app'@'%' IDENTIFIED BY '${MOR_ACCOUNT_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_account`.* TO 'account_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_reconciliation` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'reconciliation_app'@'%' IDENTIFIED BY '${MOR_RECONCILIATION_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_reconciliation`.* TO 'reconciliation_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_tax` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'tax_app'@'%' IDENTIFIED BY '${MOR_TAX_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_tax`.* TO 'tax_app'@'%';

CREATE DATABASE IF NOT EXISTS `mor_v2_migration_archive` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
