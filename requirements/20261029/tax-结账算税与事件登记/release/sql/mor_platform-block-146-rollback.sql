-- =====================================================================================================================
-- tax 结账算税与事件登记 · 注册块 146 · 回滚（按 ID 删除，子表先删；由 db/platform-registration.sql 第 E 段生成；runbook R5）。
-- 保护：① 路由仍 ACTIVE 时拒绝（先执行 -deactivate.sql）；② tax-service 已上传过文件（t_file_upload.source_system = 'tax-service'）
-- 时拒绝——已开具税票的文件记录依赖该写入路由所指的桶，此时只停用（-deactivate.sql），不删除块。拒绝方式：标量子查询返回 2 行
-- → ERROR 1242，事务未开始、不做任何改动。若运营已把 TAX_VIEW 组合进其他角色，本文件一并删除这些授权行。重复执行为空操作。
-- =====================================================================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

SELECT (SELECT 1 UNION ALL SELECT 2) AS `refuse_routes_active` FROM t_gateway_route WHERE route_id BETWEEN 900000000000146401 AND 900000000000146402 AND status = 'ACTIVE' LIMIT 1;
SELECT (SELECT 1 UNION ALL SELECT 2) AS `refuse_tax_files_exist` FROM t_file_upload WHERE source_system = 'tax-service' LIMIT 1;

START TRANSACTION;
DELETE FROM t_file_storage_route WHERE route_id = 900000000000146801;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000146701 AND 900000000000146712;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000146501 AND 900000000000146508;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000146401 AND 900000000000146402;
DELETE FROM t_console_resource WHERE resource_id = 900000000000146301;
DELETE FROM t_role_permission WHERE permission_id = 900000000000146101;
DELETE FROM t_permission WHERE permission_id = 900000000000146101;
COMMIT;
