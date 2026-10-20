-- counterparty 改名 · 本机（local-environment，127.0.0.1:13306）· 第 1 步：建库与账号。
-- 以 root 手工执行；可重复执行（IF NOT EXISTS）。旧库 mor_consumer 与账号 consumer_app 保留不动。
-- 权限与现 consumer_app 对 mor_consumer 一致：SELECT, INSERT, UPDATE, DELETE。
-- 密码与 local-environment/.env.example 的 MOR_COUNTERPARTY_DB_PASSWORD 及 application-mock.yml 默认值一致
-- （本机占位口令，与 consumer 现行做法相同）；已存在的账号不重置密码。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `mor_counterparty`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'counterparty_app'@'%' IDENTIFIED BY 'local-counterparty-change-me-2026';
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_counterparty`.* TO 'counterparty_app'@'%';
