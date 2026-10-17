-- Client Console 团队成员与邀请：Platform 增量 DML（执行文件，可重复执行）
-- 目标库：Platform Support 业务库（本机 mor_platform_e2e、mor-test 均未授权，未执行；mor_platform 是自动测试独占库，不执行）
-- 内容：① 2 个团队权限码 CLIENT_TEAM_READ / CLIENT_TEAM_MANAGE（与完整初始化 DML 同 ID，不存在才插入）
--       ② 邀请邮件即时发送模板 CLIENT_TENANT_INVITATION（zh-CN，参数 ${link}，路由到 SES_PRIMARY），与初始化 DML 同 ID；
--          同时需在 notification.transient-template-codes 中登记（application.yml 已更新）
--       ③ 已开通（已有 TENANT_OWNER 系统角色、非 PENDING）的 CLIENT 工作区补建 TENANT_ADMIN / TENANT_DEVELOPER / TENANT_VIEWER
--          三个系统角色（已存在按 uk_role_scope 跳过）
--       ④ 四个系统角色按固定矩阵补齐权限（含 TENANT_OWNER 的两个团队权限；已存在按 uk_role_permission 跳过）
-- 只回填已有 TENANT_OWNER 的工作区：尚未首次 ACTIVE 的工作区由投影一次创建四个角色，回填不能提前占用其角色编码。
-- 新行 ID：t_role、t_role_permission 各自取固定高位区间 [9100000000000000000, …]，从该区间当前最大值依次递增
--          （Snowflake 运行期 ID 远低于该区间；不沿用 role_id × 10，以免对新角色再次放大后超出 BIGINT）。
-- 前置：t_notification_provider 中存在 SES_PRIMARY（认证邮件初始化块 900000000000070001）。
-- 回滚：DELETE FROM t_role_permission WHERE role_permission_id >= 9100000000000000000;
--       DELETE FROM t_role WHERE role_id >= 9100000000000000000;
--       DELETE FROM t_role_permission WHERE permission_id IN (900000000000058105, 900000000000058106);
--       DELETE FROM t_permission WHERE permission_id IN (900000000000058105, 900000000000058106);
--       DELETE FROM t_notification_route WHERE route_id = 900000000000070033;
--       DELETE FROM t_notification_template_content WHERE content_id = 900000000000070023;
--       DELETE FROM t_notification_template WHERE template_id = 900000000000070013;
--       （回滚前须确认没有成员持有新角色、没有 PENDING 邀请引用新角色。）

-- 固定会话字符集与排序规则：CAST(... AS CHAR) 取连接排序规则，须与表列 utf8mb4_0900_ai_ci 一致（mysql 命令行默认 general_ci 会报 1267）。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

START TRANSACTION;

SET @now = UTC_TIMESTAMP(6);

-- ① 团队权限码
INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time)
SELECT v.id, v.code, v.resource_type, v.action_code, v.risk_level, 'ACTIVE', 1, @now, @now
FROM (
  SELECT 900000000000058105 id, 'CLIENT_TEAM_READ' code, 'CLIENT_TEAM' resource_type, 'READ' action_code, 'LOW' risk_level
  UNION ALL SELECT 900000000000058106, 'CLIENT_TEAM_MANAGE', 'CLIENT_TEAM', 'MANAGE', 'HIGH'
) v
WHERE NOT EXISTS (SELECT 1 FROM t_permission p WHERE p.permission_code = v.code);

-- ② 邀请邮件模板、中文内容与路由
INSERT INTO t_notification_template (template_id,template_code,medium,status,version,create_time,update_time)
SELECT 900000000000070013, 'CLIENT_TENANT_INVITATION', 'EMAIL', 'ACTIVE', 1, @now, @now
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_notification_template WHERE template_code = 'CLIENT_TENANT_INVITATION');

INSERT INTO t_notification_template_content (content_id,template_id,locale,subject_template,body_template,version,create_time,update_time)
SELECT 900000000000070023, t.template_id, 'zh-CN', 'PAYTECH · 邀请您加入工作区',
  CONCAT('邀请您加入工作区', CHAR(10), CHAR(10), '您被邀请加入一个 PAYTECH Client 工作区。请打开以下链接，',
    '使用收到本邮件的邮箱登录或注册后接受邀请：', CHAR(10), '${link}', CHAR(10), CHAR(10),
    '邀请 7 天内有效。如果您不认识发起人，请忽略此邮件。'),
  1, @now, @now
FROM t_notification_template t
WHERE t.template_code = 'CLIENT_TENANT_INVITATION'
  AND NOT EXISTS (SELECT 1 FROM t_notification_template_content c WHERE c.template_id = t.template_id AND c.locale = 'zh-CN');

INSERT INTO t_notification_route (route_id,notification_template_id,primary_provider_id,fallback_provider_id,status,version,create_time,update_time)
SELECT 900000000000070033, t.template_id, p.provider_id, NULL, 'ACTIVE', 1, @now, @now
FROM t_notification_template t JOIN t_notification_provider p ON p.provider_code = 'SES_PRIMARY'
WHERE t.template_code = 'CLIENT_TENANT_INVITATION'
  AND NOT EXISTS (SELECT 1 FROM t_notification_route r WHERE r.notification_template_id = t.template_id);

-- ③ 已开通工作区补建三个系统角色
SET @role_base = (SELECT COALESCE(MAX(role_id), 9099999999999999999) FROM t_role WHERE role_id >= 9100000000000000000);

INSERT INTO t_role (role_id,tenant_scope,tenant_id,scope_key,role_code,role_name,status,system_role,version,create_time,update_time)
SELECT @role_base + ROW_NUMBER() OVER (ORDER BY t.tenant_id, v.seq), 'TENANT', t.tenant_id, CAST(t.tenant_id AS CHAR),
  v.code, v.name, 'ACTIVE', 1, 1, @now, @now
FROM t_access_tenant t
JOIN (
  SELECT 1 seq, 'TENANT_ADMIN' code, '管理员' name
  UNION ALL SELECT 2, 'TENANT_DEVELOPER', '开发者'
  UNION ALL SELECT 3, 'TENANT_VIEWER', '只读成员'
) v
WHERE t.tenant_type = 'CLIENT' AND t.owner_status <> 'PENDING'
  AND EXISTS (SELECT 1 FROM t_role o WHERE o.tenant_scope = 'TENANT' AND o.tenant_id = t.tenant_id
    AND o.scope_key = CAST(t.tenant_id AS CHAR) AND o.role_code = 'TENANT_OWNER' AND o.system_role = 1)
  AND NOT EXISTS (SELECT 1 FROM t_role x WHERE x.scope_key = CAST(t.tenant_id AS CHAR) AND x.role_code = v.code);

-- ④ 四个系统角色的固定权限矩阵（与 ClientTeamPermissions 一致）
SET @role_permission_base = (SELECT COALESCE(MAX(role_permission_id), 9099999999999999999)
  FROM t_role_permission WHERE role_permission_id >= 9100000000000000000);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time)
SELECT @role_permission_base + ROW_NUMBER() OVER (ORDER BY r.role_id, p.permission_id), r.role_id, p.permission_id,
  'ACTIVE', 1, @now, @now
FROM t_role r
JOIN t_access_tenant t ON t.tenant_id = r.tenant_id
JOIN (
  SELECT 'TENANT_OWNER' role_code, 'CLIENT_TEAM_READ' permission_code
  UNION ALL SELECT 'TENANT_OWNER', 'CLIENT_TEAM_MANAGE'
  UNION ALL SELECT 'TENANT_OWNER', 'CLIENT_API_CREDENTIAL_READ'
  UNION ALL SELECT 'TENANT_OWNER', 'CLIENT_API_CREDENTIAL_MANAGE'
  UNION ALL SELECT 'TENANT_OWNER', 'CLIENT_WEBHOOK_READ'
  UNION ALL SELECT 'TENANT_OWNER', 'CLIENT_WEBHOOK_MANAGE'
  UNION ALL SELECT 'TENANT_ADMIN', 'CLIENT_TEAM_READ'
  UNION ALL SELECT 'TENANT_ADMIN', 'CLIENT_TEAM_MANAGE'
  UNION ALL SELECT 'TENANT_ADMIN', 'CLIENT_API_CREDENTIAL_READ'
  UNION ALL SELECT 'TENANT_ADMIN', 'CLIENT_API_CREDENTIAL_MANAGE'
  UNION ALL SELECT 'TENANT_ADMIN', 'CLIENT_WEBHOOK_READ'
  UNION ALL SELECT 'TENANT_ADMIN', 'CLIENT_WEBHOOK_MANAGE'
  UNION ALL SELECT 'TENANT_DEVELOPER', 'CLIENT_TEAM_READ'
  UNION ALL SELECT 'TENANT_DEVELOPER', 'CLIENT_API_CREDENTIAL_READ'
  UNION ALL SELECT 'TENANT_DEVELOPER', 'CLIENT_API_CREDENTIAL_MANAGE'
  UNION ALL SELECT 'TENANT_DEVELOPER', 'CLIENT_WEBHOOK_READ'
  UNION ALL SELECT 'TENANT_DEVELOPER', 'CLIENT_WEBHOOK_MANAGE'
  UNION ALL SELECT 'TENANT_VIEWER', 'CLIENT_TEAM_READ'
  UNION ALL SELECT 'TENANT_VIEWER', 'CLIENT_API_CREDENTIAL_READ'
  UNION ALL SELECT 'TENANT_VIEWER', 'CLIENT_WEBHOOK_READ'
) m ON m.role_code = r.role_code
JOIN t_permission p ON p.permission_code = m.permission_code AND p.status = 'ACTIVE'
WHERE r.tenant_scope = 'TENANT' AND r.system_role = 1 AND r.scope_key = CAST(t.tenant_id AS CHAR)
  AND t.tenant_type = 'CLIENT' AND t.owner_status <> 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM t_role_permission x WHERE x.role_id = r.role_id AND x.permission_id = p.permission_id);

COMMIT;
