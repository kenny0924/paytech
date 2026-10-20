-- 阶段 5 本机验收清理：只删除 local_acceptance.py 本次运行创建的行。不碰块 92 与激活状态，不删 Vault 记录（只计数）。
-- 由 `python3 -I local_acceptance.py --cleanup` 读取最新运行记录，把占位符替换为本次 ID 后执行：
--   /*CHANNELS*/  本次新建的渠道 ID（逗号分隔，十进制）；/*PLANS*/ 本次新建的成本方案代码（带引号，逗号分隔）
--   /*ACCOUNTS*/  本次 IAM 新建的合成人员 Account ID；/*ROLES*/ 本次 IAM 新建的合成角色 ID
-- 前段以 channel_connector_app 连接 mor_channel_connector_v2e2e；「@platform」标记后的段以 platform_app 连接 mor_platform_v2e2e。
-- 顺序按逻辑依赖由子到父（库内无物理外键）。占位符为 NULL 时对应语句不删除任何行。
-- Platform 审计事件（t_audit_event）是审计留痕，不删。回滚不适用（只删除验收数据）。

-- channel-connector：变更记录（渠道、账号、MID、能力组、成本版本）
DELETE FROM t_channel_config_change
 WHERE (target_type = 'CHANNEL' AND target_id IN (/*CHANNELS*/))
    OR (target_type = 'CHANNEL_ACCOUNT' AND target_id IN (SELECT channel_account_id FROM t_channel_account WHERE channel_id IN (/*CHANNELS*/)))
    OR (target_type = 'CHANNEL_MID' AND target_id IN (SELECT mid_id FROM t_channel_mid WHERE channel_id IN (/*CHANNELS*/)))
    OR (target_type = 'MID_CAPABILITY' AND target_id IN (SELECT c.mid_capability_id FROM t_mid_capability c
         JOIN t_channel_mid m ON m.mid_id = c.mid_id WHERE m.channel_id IN (/*CHANNELS*/)))
    OR (target_type = 'CHANNEL_COST_VERSION' AND target_id IN (SELECT v.id FROM t_channel_cost_plan_ver v
         JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/)));

-- channel-connector：成本组成、范围、版本、方案
DELETE cc FROM t_channel_cost_component cc JOIN t_channel_cost_scope s ON s.id = cc.scope_id
  JOIN t_channel_cost_plan_ver v ON v.id = s.cost_ver_id JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/);
DELETE s FROM t_channel_cost_scope s JOIN t_channel_cost_plan_ver v ON v.id = s.cost_ver_id
  JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/);
DELETE v FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/);
DELETE FROM t_channel_cost_plan WHERE plan_code IN (/*PLANS*/);

-- channel-connector：能力明细、能力组、MID、账号、渠道
DELETE d FROM t_mid_capability_detail d JOIN t_mid_capability c ON c.mid_capability_id = d.mid_capability_id
  JOIN t_channel_mid m ON m.mid_id = c.mid_id WHERE m.channel_id IN (/*CHANNELS*/);
DELETE c FROM t_mid_capability c JOIN t_channel_mid m ON m.mid_id = c.mid_id WHERE m.channel_id IN (/*CHANNELS*/);
DELETE FROM t_channel_mid WHERE channel_id IN (/*CHANNELS*/);
DELETE FROM t_channel_account WHERE channel_id IN (/*CHANNELS*/);
DELETE FROM t_payment_channel WHERE channel_id IN (/*CHANNELS*/);

-- @platform
-- Platform：合成人员的会话、刷新代际、挑战、口令、成员与成员角色、账号；合成角色的授权与角色
DELETE g FROM t_refresh_token_generation g JOIN t_auth_session s ON s.session_id = g.session_id WHERE s.account_id IN (/*ACCOUNTS*/);
DELETE FROM t_auth_session WHERE account_id IN (/*ACCOUNTS*/);
DELETE FROM t_authentication_challenge WHERE account_id IN (/*ACCOUNTS*/);
DELETE FROM t_password_credential WHERE account_id IN (/*ACCOUNTS*/);
DELETE r FROM t_tenant_member_role r JOIN t_tenant_member m ON m.member_id = r.tenant_member_id WHERE m.account_id IN (/*ACCOUNTS*/);
DELETE FROM t_tenant_member_role WHERE role_id IN (/*ROLES*/);
DELETE FROM t_tenant_member WHERE account_id IN (/*ACCOUNTS*/);
DELETE FROM t_user_account WHERE account_id IN (/*ACCOUNTS*/);
DELETE FROM t_role_permission WHERE role_id IN (/*ROLES*/);
DELETE FROM t_role WHERE role_id IN (/*ROLES*/);
