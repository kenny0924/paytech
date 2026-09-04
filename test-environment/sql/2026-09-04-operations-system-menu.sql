-- 手工增量：系统管理 > 人员管理、角色管理、审计日志。
-- 只用于已完成 Platform 当前目录初始化的 mor-test；不要重跑 initialize 或 Bootstrap。
-- 使用默认遇错停止的 MySQL 批处理客户端，不使用 --force；任一步失败必须回滚本事务。
-- 固定 ID 与稳定 Code 同时匹配，冲突直接报错；重复执行不重复插入、不推进未变化资源的版本。
-- 已有 Permission/Role Grant/Resource 的停用状态保持不变，不恢复已撤销的权限。
SET NAMES utf8mb4;

START TRANSACTION;
SET @operations_menu_time = UTC_TIMESTAMP(6);
SET @operations_menu_admin_role_id = (
    SELECT role_id FROM t_role
    WHERE role_id = 900000000000000510 AND role_code = 'PLATFORM_ADMIN'
      AND tenant_scope = 'PLATFORM' AND tenant_id IS NULL AND scope_key = 'PLATFORM' AND system_role = 1
);
SET @operations_menu_viewer_role_id = (
    SELECT role_id FROM t_role
    WHERE role_id = 900000000000000511 AND role_code = 'OPERATIONS_VIEWER'
      AND tenant_scope = 'PLATFORM' AND tenant_id IS NULL AND scope_key = 'PLATFORM' AND system_role = 1
);
SET @operations_menu_role_permission_id = (
    SELECT permission_id FROM t_permission
    WHERE permission_id = 900000000000000522 AND permission_code = 'OPERATIONS_ROLE_READ'
      AND resource_type = 'ROLE' AND action_code = 'READ'
);

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time)
SELECT 900000000000000524, 'OPERATIONS_SYSTEM_READ', 'OPERATIONS_SYSTEM', 'READ', 'LOW', 'ACTIVE', 1,
       @operations_menu_time, @operations_menu_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_id = 900000000000000524 AND permission_code = 'OPERATIONS_SYSTEM_READ'
      AND resource_type = 'OPERATIONS_SYSTEM' AND action_code = 'READ' AND risk_level = 'LOW'
);

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000537, @operations_menu_admin_role_id, 900000000000000524, 'ACTIVE', 1,
       @operations_menu_time, @operations_menu_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE role_id = @operations_menu_admin_role_id AND permission_id = 900000000000000524
);

INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000000538, @operations_menu_viewer_role_id, 900000000000000524, 'ACTIVE', 1,
       @operations_menu_time, @operations_menu_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_role_permission WHERE role_id = @operations_menu_viewer_role_id AND permission_id = 900000000000000524
);

-- 父菜单先写入，满足同 Channel 的自引用外键；目录节点无页面或组件。
INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000555, 'OPERATIONS_SYSTEM', 'OPERATIONS', 'MENU', NULL, '系统管理', NULL, NULL,
       900000000000000524, 10, 'ACTIVE', 1, @operations_menu_time, @operations_menu_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource
    WHERE resource_id = 900000000000000555 AND resource_code = 'OPERATIONS_SYSTEM' AND channel = 'OPERATIONS'
      AND resource_type = 'MENU' AND parent_resource_id IS NULL AND frontend_route IS NULL AND component_key IS NULL
      AND permission_id = 900000000000000524
);

INSERT INTO t_console_resource (resource_id, resource_code, channel, resource_type, parent_resource_id, display_name, frontend_route, component_key, permission_id, sort_order, status, version, create_time, update_time)
SELECT 900000000000000556, 'OPERATIONS_ROLES', 'OPERATIONS', 'MENU', 900000000000000555, '角色管理', '/roles', 'OPERATIONS_ROLES',
       @operations_menu_role_permission_id, 20, 'ACTIVE', 1, @operations_menu_time, @operations_menu_time
WHERE NOT EXISTS (
    SELECT 1 FROM t_console_resource
    WHERE resource_id = 900000000000000556 AND resource_code = 'OPERATIONS_ROLES' AND channel = 'OPERATIONS'
      AND resource_type = 'MENU' AND parent_resource_id = 900000000000000555
      AND frontend_route = '/roles' AND component_key = 'OPERATIONS_ROLES' AND permission_id = @operations_menu_role_permission_id
);

-- 仅调整已有两个页面的显示名称、父菜单和同级顺序；路由、权限、状态及三个按钮均不变。
UPDATE t_console_resource AS resource
JOIN (
    SELECT 900000000000000550 AS id, 'OPERATIONS_ACCOUNTS' AS code, '人员管理' AS label,
           '/accounts' AS route, 900000000000000520 AS permission_id, 10 AS sort_order
    UNION ALL
    SELECT 900000000000000551, 'OPERATIONS_AUDIT', '审计日志', '/audit', 900000000000000523, 30
) AS expected ON resource.resource_id = expected.id AND resource.resource_code = expected.code
SET resource.parent_resource_id = 900000000000000555,
    resource.display_name = expected.label,
    resource.sort_order = expected.sort_order,
    resource.version = resource.version + 1,
    resource.update_time = @operations_menu_time
WHERE resource.channel = 'OPERATIONS' AND resource.resource_type = 'MENU'
  AND resource.frontend_route = expected.route AND resource.component_key = expected.code
  AND resource.permission_id = expected.permission_id
  AND (NOT (resource.parent_resource_id <=> 900000000000000555)
       OR resource.display_name <> expected.label OR resource.sort_order <> expected.sort_order);

SELECT ROW_COUNT() AS updated_existing_menus;

COMMIT;

-- 仅供操作员复核：应为一个目录、三个页面、三个原按钮；不包含账号或其他秘密。
SELECT resource_id, resource_code, parent_resource_id, display_name, frontend_route, component_key, sort_order, status
FROM t_console_resource
WHERE channel = 'OPERATIONS' AND resource_id IN (900000000000000550, 900000000000000551,
    900000000000000552, 900000000000000553, 900000000000000554, 900000000000000555, 900000000000000556)
ORDER BY parent_resource_id, sort_order, resource_id;
