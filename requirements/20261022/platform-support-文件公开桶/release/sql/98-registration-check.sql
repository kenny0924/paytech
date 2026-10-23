-- 文件公开桶 · 块 134 休眠注册后只读核对（阶段 7 第 4 步；root；不写数据）。期望 2 / 2 / 2 / 12 / 56 / 11 / 72，且全部休眠。
SET NAMES utf8mb4;
SELECT 'R01 permission ACTIVE' AS item, COUNT(*) AS actual, 2 AS expect FROM mor_platform.t_permission
  WHERE permission_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'ACTIVE'
UNION ALL SELECT 'R02 role_permission REVOKED', COUNT(*), 2 FROM mor_platform.t_role_permission
  WHERE role_permission_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'REVOKED'
UNION ALL SELECT 'R03 console_resource DISABLED', COUNT(*), 2 FROM mor_platform.t_console_resource
  WHERE resource_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'DISABLED'
UNION ALL SELECT 'R04 gateway_route DISABLED', COUNT(*), 12 FROM mor_platform.t_gateway_route
  WHERE route_id BETWEEN 900000000000134001 AND 900000000000134999 AND status = 'DISABLED'
UNION ALL SELECT 'R05 route_header', COUNT(*), 56 FROM mor_platform.t_gateway_route_header
  WHERE route_header_id BETWEEN 900000000000134001 AND 900000000000134999
UNION ALL SELECT 'R06 query_param', COUNT(*), 11 FROM mor_platform.t_gateway_route_query_param
  WHERE route_query_param_id BETWEEN 900000000000134001 AND 900000000000134999
UNION ALL SELECT 'R07 guard_rule', COUNT(*), 72 FROM mor_platform.t_gateway_guard_rule
  WHERE guard_rule_id BETWEEN 900000000000134001 AND 900000000000134999
UNION ALL SELECT 'R08 块 130 写入规则编辑 / 删除仍为旧路径（10 尚未执行）', COUNT(*), 2 FROM mor_platform.t_gateway_route
  WHERE route_id IN (900000000000130411, 900000000000130412)
   AND external_path_template = '/api/operations/storage-admin/v1/routes/{sourceSystem}';
