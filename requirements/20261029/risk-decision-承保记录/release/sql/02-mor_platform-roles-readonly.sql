-- REQ-03 · mor-test 角色组合只读清单（runbook ⑩ 依据）。READ ONLY 会话执行。
SET NAMES utf8mb4;
-- 1) 运营侧角色（排除 Client 工作区 TENANT_* 系统角色与 INACTIVE 的 QA / 冒烟角色）与 ACTIVE 授权数、成员数
SELECT r.role_id, r.role_code, r.role_name, r.status, r.system_role, r.tenant_scope,
       (SELECT COUNT(*) FROM t_role_permission rp WHERE rp.role_id = r.role_id AND rp.status = 'ACTIVE') AS active_permissions
FROM t_role r WHERE r.status = 'ACTIVE' AND r.role_code NOT LIKE 'TENANT\_%' ORDER BY r.role_id;
-- 2) 持有任意风控权限（RISK_% / DECISION_%）的角色
SELECT r.role_code, p.permission_code, rp.status
FROM t_role_permission rp JOIN t_role r ON r.role_id = rp.role_id JOIN t_permission p ON p.permission_id = rp.permission_id
WHERE p.permission_code LIKE 'RISK%' OR p.permission_code LIKE 'DECISION%'
ORDER BY r.role_code, p.permission_code;
-- 3) 风控相关权限清单
SELECT permission_id, permission_code, resource_type, action_code, risk_level, status FROM t_permission
WHERE permission_code LIKE 'RISK%' OR permission_code LIKE 'DECISION%' ORDER BY permission_id;
-- 4) 平台角色成员数（ACTIVE 成员角色）
SELECT r.role_code, COUNT(*) AS members FROM t_tenant_member_role mr JOIN t_role r ON r.role_id = mr.role_id
WHERE r.tenant_scope = 'PLATFORM' AND mr.status = 'ACTIVE' GROUP BY r.role_code;
