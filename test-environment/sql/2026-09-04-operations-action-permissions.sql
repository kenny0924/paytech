-- 一次性手工增量：Operations 五个操作独立授权。
-- 前置：17 条 Operations Route 已存在；新 Permission/保留 Grant ID 段不存在。
-- 已有两个 MANAGE 授权按原业务能力等价展开，不向未持有旧权限的角色补授。
-- 操作员须先检查旧授权只属于当前 INTERNAL Tenant 或 Operations 系统角色。
SET NAMES utf8mb4;
START TRANSACTION;
SET @change_time = UTC_TIMESTAMP(6);

-- 与角色/人员命令使用相同的 Tenant 锁；应用发布完成前业务写入失败关闭。
SELECT tenant_id FROM t_access_tenant WHERE tenant_type='INTERNAL' AND owner_ref_id=1 FOR UPDATE;

INSERT INTO t_permission (permission_id, permission_code, resource_type, action_code, risk_level, status, version, create_time, update_time) VALUES
    (900000000000000526, 'OPERATIONS_ACCOUNT_CREATE', 'USER_ACCOUNT', 'CREATE', 'HIGH', 'ACTIVE', 1, @change_time, @change_time),
    (900000000000000527, 'OPERATIONS_ACCOUNT_DISABLE', 'USER_ACCOUNT', 'DISABLE', 'HIGH', 'ACTIVE', 1, @change_time, @change_time),
    (900000000000000528, 'OPERATIONS_ACCOUNT_ASSIGN_ROLES', 'USER_ACCOUNT', 'ASSIGN_ROLES', 'HIGH', 'ACTIVE', 1, @change_time, @change_time),
    (900000000000000529, 'OPERATIONS_ROLE_CREATE', 'ROLE', 'CREATE', 'HIGH', 'ACTIVE', 1, @change_time, @change_time),
    (900000000000000530, 'OPERATIONS_ROLE_UPDATE', 'ROLE', 'UPDATE', 'HIGH', 'ACTIVE', 1, @change_time, @change_time);

-- 7001 起的 ID 为本次手工变更保留段，执行前确认与所有已有 Grant 不冲突。
INSERT INTO t_role_permission (role_permission_id, role_id, permission_id, status, version, create_time, update_time)
SELECT 900000000000007000 + ROW_NUMBER() OVER (ORDER BY rp.role_id, p.permission_id),
       rp.role_id, p.permission_id, 'ACTIVE', 1, @change_time, @change_time
FROM t_role_permission rp
JOIN t_role r ON r.role_id=rp.role_id
JOIN t_permission p ON (rp.permission_id=900000000000000521 AND p.permission_id IN (900000000000000526,900000000000000527,900000000000000528))
                   OR (rp.permission_id=900000000000000525 AND p.permission_id IN (900000000000000529,900000000000000530))
WHERE rp.status='ACTIVE' AND
      ((r.tenant_scope='TENANT' AND r.tenant_id=(SELECT tenant_id FROM t_access_tenant WHERE tenant_type='INTERNAL' AND owner_ref_id=1))
       OR (r.tenant_scope='PLATFORM' AND r.system_role=1 AND r.role_code IN ('PLATFORM_ADMIN','OPERATIONS_VIEWER')));

UPDATE t_role r JOIN (SELECT DISTINCT role_id FROM t_role_permission
                     WHERE permission_id IN (900000000000000521,900000000000000525) AND status='ACTIVE') affected
  ON affected.role_id=r.role_id
SET r.version=r.version+1, r.update_time=@change_time;

UPDATE t_role_permission SET status='REVOKED',version=version+1,update_time=@change_time
WHERE permission_id IN (900000000000000521,900000000000000525) AND status='ACTIVE';

UPDATE t_permission SET status='INACTIVE',version=version+1,update_time=@change_time
WHERE permission_id IN (900000000000000521,900000000000000525) AND status='ACTIVE';

UPDATE t_gateway_route SET permission_id=CASE route_code
    WHEN 'operations-account-create' THEN 900000000000000526
    WHEN 'operations-account-disable' THEN 900000000000000527
    WHEN 'operations-account-roles' THEN 900000000000000528
    WHEN 'operations-role-permission-options' THEN 900000000000000529
    WHEN 'operations-role-create' THEN 900000000000000529
    WHEN 'operations-role-detail' THEN 900000000000000530
    WHEN 'operations-role-update' THEN 900000000000000530 END,
    version=version+1,update_time=@change_time
WHERE channel='OPERATIONS' AND route_code IN ('operations-account-create','operations-account-disable','operations-account-roles',
      'operations-role-permission-options','operations-role-create','operations-role-detail','operations-role-update');

UPDATE t_console_resource SET permission_id=CASE resource_code
    WHEN 'OPERATIONS_ACCOUNT_CREATE' THEN 900000000000000526
    WHEN 'OPERATIONS_ACCOUNT_DISABLE' THEN 900000000000000527
    WHEN 'OPERATIONS_ACCOUNT_ASSIGN_ROLES' THEN 900000000000000528
    WHEN 'OPERATIONS_ROLE_CREATE' THEN 900000000000000529
    WHEN 'OPERATIONS_ROLE_EDIT' THEN 900000000000000530 END,
    version=version+1,update_time=@change_time
WHERE channel='OPERATIONS' AND resource_code IN ('OPERATIONS_ACCOUNT_CREATE','OPERATIONS_ACCOUNT_DISABLE',
      'OPERATIONS_ACCOUNT_ASSIGN_ROLES','OPERATIONS_ROLE_CREATE','OPERATIONS_ROLE_EDIT');
COMMIT;

SELECT resource_code,permission_id FROM t_console_resource WHERE channel='OPERATIONS' AND resource_type='BUTTON' ORDER BY resource_id;
