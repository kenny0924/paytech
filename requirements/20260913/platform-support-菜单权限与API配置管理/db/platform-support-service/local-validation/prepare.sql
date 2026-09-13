-- 只用于 local-environment 127.0.0.1:13306。
-- 如果库名已存在，CREATE DATABASE 必须失败并停止；禁止 DROP/重置任何库。
CREATE DATABASE `mor_platform_config_validation`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_platform_config_validation`.* TO 'platform_app'@'%';
