-- 外部 API 第 2 步 2e：本机独立端到端验证库（F3 已授权）。只在 127.0.0.1:13306 执行，root 连接。
-- 不触碰测试同用的 mor_platform；应用只获得本库 DML 权限。
CREATE DATABASE `mor_platform_external_api` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_platform_external_api`.* TO `platform_app`@`%`;
