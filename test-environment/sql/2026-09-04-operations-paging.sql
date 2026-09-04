-- 手工增量：只允许现有 Operations 角色列表 Route 接收 page、size 查询参数。
-- 仅用于已完成当前 Platform 目录初始化的 mor-test，不重跑 initialize、Bootstrap 或完整初始化 DML。
-- 前置：固定 Route ID/Code、GET 路径、Target 与权限语义必须匹配；两个稳定 Query ID 未被其他配置占用。
-- 使用默认遇错停止的 MySQL 批处理客户端，不使用 --force；任一步失败必须回滚本事务。
-- Route 缺失或语义不匹配时，后续插入的 route_id 为 NULL，触发 NOT NULL 失败；ID/参数冲突触发唯一约束失败。
-- 不执行 INSERT IGNORE 或覆盖冲突；只有精确匹配的既有子行才视为已完成。
-- Query 子项属于 Route 聚合：同一事务实际新增一条或两条时只推进一次 Route Version，重跑不推进。
SET NAMES utf8mb4;

START TRANSACTION;
SET @operations_paging_time = UTC_TIMESTAMP(6);
SET @operations_paging_route_id = (
    SELECT route_id FROM t_gateway_route
    WHERE route_id = 900000000000001008
      AND CAST(route_code AS BINARY) = CAST('operations-role-list' AS BINARY)
      AND channel = 'OPERATIONS' AND http_method = 'GET'
      AND external_path_template = '/api/operations/iam/v1/roles'
      AND downstream_path_template = '/iam/v1/roles'
      AND target_id = 900000000000000001
      AND security_profile_code = 'OPERATIONS_JWT' AND authorization_mode = 'HUMAN_PERMISSION'
      AND permission_id = 900000000000000522 AND required_scope_code IS NULL
    FOR UPDATE
);

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004004, @operations_paging_route_id, 'page'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param
    WHERE route_query_param_id = 900000000000004004 AND route_id = @operations_paging_route_id
      AND CAST(parameter_name AS BINARY) = CAST('page' AS BINARY)
);
SET @operations_paging_page_added = ROW_COUNT();

INSERT INTO t_gateway_route_query_param (route_query_param_id, route_id, parameter_name)
SELECT 900000000000004005, @operations_paging_route_id, 'size'
WHERE NOT EXISTS (
    SELECT 1 FROM t_gateway_route_query_param
    WHERE route_query_param_id = 900000000000004005 AND route_id = @operations_paging_route_id
      AND CAST(parameter_name AS BINARY) = CAST('size' AS BINARY)
);
SET @operations_paging_size_added = ROW_COUNT();

-- 保留 Route 状态、权限、Cookie、Header、Guard、限额及全部其他配置。
UPDATE t_gateway_route
SET version = version + 1, update_time = @operations_paging_time
WHERE route_id = @operations_paging_route_id
  AND @operations_paging_page_added + @operations_paging_size_added > 0;

SELECT @operations_paging_page_added + @operations_paging_size_added AS inserted_query_parameters,
       ROW_COUNT() AS updated_routes;

COMMIT;

-- 操作员复核：角色 Route 仍只有一个；此次新增的白名单应为 page、size。
SELECT route.route_id, route.route_code, route.version, parameter.route_query_param_id, parameter.parameter_name
FROM t_gateway_route AS route
JOIN t_gateway_route_query_param AS parameter ON parameter.route_id = route.route_id
WHERE route.route_id = 900000000000001008 AND route.route_code = 'operations-role-list'
ORDER BY parameter.parameter_name, parameter.route_query_param_id;
