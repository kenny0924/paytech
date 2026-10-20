-- counterparty 改名 · mor-test · 第 1 步：建库与最小权限账号（只写不执行，由阶段 6–7 按 runbook 执行）。
-- 影响：新建 mor_counterparty 与 counterparty_app，授本库 SELECT/INSERT/UPDATE/DELETE；不改动 mor_consumer / consumer_app。
-- 口令：${MOR_COUNTERPARTY_DB_PASSWORD} 是 test-environment/.env.test 的变量名占位，不是真实口令。
--       先运行 test-environment/scripts/prepare.py（V2_DATABASE_PASSWORDS 已登记该键，已有 .env.test 时只追加缺失项），
--       再由执行脚本读取 .env.test 替换占位，结果只经标准输入交给容器内 mysql，不落盘、不打印。
--       格式同 initialize.py：[A-Za-z0-9._@%+=:-]{16,128}。
-- 等价做法：test-environment/scripts/initialize.py --create-service-databases（同库名、账号、权限，重复执行无副作用）。
-- 前置：root 连接容器内 mysql:3306。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `mor_counterparty` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'counterparty_app'@'%' IDENTIFIED BY '${MOR_COUNTERPARTY_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_counterparty`.* TO 'counterparty_app'@'%';
