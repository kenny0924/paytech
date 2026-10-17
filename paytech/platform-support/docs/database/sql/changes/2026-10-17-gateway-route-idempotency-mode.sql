-- Gateway 处理 Idempotency-Key：t_gateway_route 增加请求幂等模式（待执行，需授权）。
-- 需求：requirements/20261017/gateway-请求幂等IdempotencyKey/technique.md 第 6 节、第 11 节。
-- 目标库：Platform Support（mor_platform / mor_platform_e2e）。
-- 预检：SELECT COUNT(*) FROM information_schema.COLUMNS
--         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_gateway_route' AND COLUMN_NAME = 'idempotency_mode';
--       结果须为 0；否则说明已执行，跳过本文件。
-- 执行顺序：先执行本文件，再部署读写该列的新 Platform；旧 Platform 不读该列，加列后仍可正常运行。
-- 影响：已有路由全部取默认值 NONE，Gateway 行为不变；不修改其他表或数据。
-- 回退：先把所有路由改回 NONE 并回退新 Platform，再执行
--       ALTER TABLE `t_gateway_route` DROP COLUMN `idempotency_mode`;
SET NAMES utf8mb4;

ALTER TABLE `t_gateway_route`
  ADD COLUMN `idempotency_mode` VARCHAR(64) NOT NULL DEFAULT 'NONE' COMMENT '请求幂等模式的稳定业务代码。允许值：NONE、REQUIRED。' AFTER `response_cookie_mode`;
