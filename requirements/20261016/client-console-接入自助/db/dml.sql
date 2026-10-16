-- Client Console 接入自助：Platform 增量 DML（执行文件，可重复执行）
-- 目标库：Platform Support 业务库（本机 mor_platform_e2e 已执行；mor_platform 是自动测试独占库，不执行；mor-test 未授权，未执行）
-- 内容：① 4 个 CLIENT 工作区开发者权限码（与完整初始化 DML 同 ID，不存在才插入）
--       ② 为已开通（非 PENDING）CLIENT 工作区的 TENANT_OWNER 系统角色补录这 4 个权限（已存在则跳过）
-- 新开通的工作区由首次 ACTIVE 投影写入，不依赖本文件。
-- 回滚：DELETE FROM t_role_permission WHERE permission_id BETWEEN 900000000000058101 AND 900000000000058104;
--       DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000058101 AND 900000000000058104;
-- role_permission_id = role_id * 10 + 序号：落在 Snowflake 现有取值范围之外，不与运行期生成的 ID 冲突。

START TRANSACTION;

SET @now = UTC_TIMESTAMP(6);

INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time)
SELECT v.id, v.code, v.resource_type, v.action_code, v.risk_level, 'ACTIVE', 1, @now, @now
FROM (
  SELECT 900000000000058101 id, 'CLIENT_API_CREDENTIAL_READ' code, 'CLIENT_API_CREDENTIAL' resource_type, 'READ' action_code, 'LOW' risk_level
  UNION ALL SELECT 900000000000058102, 'CLIENT_API_CREDENTIAL_MANAGE', 'CLIENT_API_CREDENTIAL', 'MANAGE', 'CRITICAL'
  UNION ALL SELECT 900000000000058103, 'CLIENT_WEBHOOK_READ', 'CLIENT_WEBHOOK', 'READ', 'LOW'
  UNION ALL SELECT 900000000000058104, 'CLIENT_WEBHOOK_MANAGE', 'CLIENT_WEBHOOK', 'MANAGE', 'HIGH'
) v
WHERE NOT EXISTS (SELECT 1 FROM t_permission p WHERE p.permission_code = v.code);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time)
SELECT r.role_id * 10 + n.seq, r.role_id, p.permission_id, 'ACTIVE', 1, @now, @now
FROM t_role r
JOIN t_access_tenant t ON t.tenant_id = r.tenant_id
JOIN (
  SELECT 1 seq, 'CLIENT_API_CREDENTIAL_READ' code
  UNION ALL SELECT 2, 'CLIENT_API_CREDENTIAL_MANAGE'
  UNION ALL SELECT 3, 'CLIENT_WEBHOOK_READ'
  UNION ALL SELECT 4, 'CLIENT_WEBHOOK_MANAGE'
) n
JOIN t_permission p ON p.permission_code = n.code
WHERE r.tenant_scope = 'TENANT' AND r.role_code = 'TENANT_OWNER' AND r.system_role = 1
  AND t.tenant_type = 'CLIENT' AND t.owner_status <> 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM t_role_permission x WHERE x.role_id = r.role_id AND x.permission_id = p.permission_id);

COMMIT;
