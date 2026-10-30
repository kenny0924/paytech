-- =====================================================================================================================
-- 联合查询注册块 144 · 回滚（按 ID 删除，子表先删；路由已 ACTIVE 时删除后重新发布 Gateway 快照）
-- 顺序：ops-web → 本文件 → ops-bff → Platform 旧镜像 → 2026-10-29-joint-query-rollback.sql。重复执行为空操作。
-- 键类型被模板 joint_json 按编码引用：删除前先确认没有需要保留的联合查询配置（回滚 DDL 会一并删除 joint_json）。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000144701 AND 900000000000144748;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000144601 AND 900000000000144605;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000144501 AND 900000000000144536;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000144401 AND 900000000000144408;
DELETE FROM t_console_resource WHERE resource_id IN (900000000000144301, 900000000000144302);
DELETE FROM t_query_key_type WHERE key_type_id BETWEEN 900000000000144101 AND 900000000000144129;
COMMIT;
