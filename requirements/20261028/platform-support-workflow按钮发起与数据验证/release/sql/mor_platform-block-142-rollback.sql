-- =====================================================================================================================
-- REQ-05 注册块 142 · 回滚（按 ID 删除，子表先删；路由已 ACTIVE 时删除后重新发布 Gateway 快照）
-- 回滚顺序见 technique.md 9.3：XXL 停用 → ops-web → 本文件 → ops-bff → Platform 旧镜像 → 回滚 SQL。重复执行为空操作。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000142601 AND 900000000000142618;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000142001 AND 900000000000142003;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000142501 AND 900000000000142514;
DELETE FROM t_gateway_route WHERE route_id IN (900000000000142401, 900000000000142402, 900000000000142403);
COMMIT;
