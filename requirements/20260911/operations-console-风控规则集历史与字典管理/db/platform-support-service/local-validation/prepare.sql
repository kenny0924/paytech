-- RCO-16 阶段 5：待单独授权执行，只使用 local-environment 127.0.0.1:13306。
-- 新建独立验证库；名称已存在时必须停止，禁止 DROP / 重置原 mor_platform。
CREATE DATABASE `mor_platform_rco16_validation`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- 初始化由本机管理员执行；测试应用只获本库数据访问权，不获 DDL 权限。
GRANT SELECT, INSERT, UPDATE, DELETE ON `mor_platform_rco16_validation`.* TO 'platform_app'@'%';
