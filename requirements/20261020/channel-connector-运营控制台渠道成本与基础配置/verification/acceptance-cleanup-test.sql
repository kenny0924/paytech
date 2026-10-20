-- 阶段 7 mor-test 验收清理（mor_channel_connector）：只删除 E2E_TARGET=test 的 local_acceptance.py / local_browser.py 本次运行创建的行。
-- 由 `E2E_TARGET=test python3 -I local_acceptance.py --cleanup` 读取最新 channel-acceptance-test-*.json，把占位符替换为本次 ID，
-- 以 channel_connector_app 连接 mor_channel_connector（127.0.0.1:23306）执行；手工执行时先把占位符换成运行记录中的 ID：
--   /*CHANNELS*/ 本次新建渠道 ID（逗号分隔）；/*PLANS*/ 本次新建方案代码（带引号，逗号分隔）
-- 顺序：先 SELECT 核对待删行数，再按子到父 DELETE，每条 DELETE 后输出 ROW_COUNT()；最后再 SELECT 核对为 0。
-- Platform 侧（合成人员停用、合成角色清空权限）经 IAM 接口完成，不在本文件；Vault Token 不删；Platform 审计不回滚。回滚不适用。

-- 1. 核对（期望与运行记录一致）
SELECT 'channels' AS t, COUNT(*) FROM t_payment_channel WHERE channel_id IN (/*CHANNELS*/)
UNION ALL SELECT 'accounts', COUNT(*) FROM t_channel_account WHERE channel_id IN (/*CHANNELS*/)
UNION ALL SELECT 'mids', COUNT(*) FROM t_channel_mid WHERE channel_id IN (/*CHANNELS*/)
UNION ALL SELECT 'capabilities', COUNT(*) FROM t_mid_capability c JOIN t_channel_mid m ON m.mid_id = c.mid_id WHERE m.channel_id IN (/*CHANNELS*/)
UNION ALL SELECT 'plans', COUNT(*) FROM t_channel_cost_plan WHERE plan_code IN (/*PLANS*/)
UNION ALL SELECT 'versions', COUNT(*) FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/)
UNION ALL SELECT 'plans_of_other_channels', COUNT(*) FROM t_channel_cost_plan WHERE plan_code IN (/*PLANS*/) AND channel_id NOT IN (/*CHANNELS*/);

-- 2. 删除
DELETE FROM t_channel_config_change
 WHERE (target_type = 'CHANNEL' AND target_id IN (/*CHANNELS*/))
    OR (target_type = 'CHANNEL_ACCOUNT' AND target_id IN (SELECT channel_account_id FROM t_channel_account WHERE channel_id IN (/*CHANNELS*/)))
    OR (target_type = 'CHANNEL_MID' AND target_id IN (SELECT mid_id FROM t_channel_mid WHERE channel_id IN (/*CHANNELS*/)))
    OR (target_type = 'MID_CAPABILITY' AND target_id IN (SELECT c.mid_capability_id FROM t_mid_capability c
         JOIN t_channel_mid m ON m.mid_id = c.mid_id WHERE m.channel_id IN (/*CHANNELS*/)))
    OR (target_type = 'CHANNEL_COST_VERSION' AND target_id IN (SELECT v.id FROM t_channel_cost_plan_ver v
         JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/)));
SELECT 'del_config_change', ROW_COUNT();
DELETE cc FROM t_channel_cost_component cc JOIN t_channel_cost_scope s ON s.id = cc.scope_id
  JOIN t_channel_cost_plan_ver v ON v.id = s.cost_ver_id JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/);
SELECT 'del_cost_component', ROW_COUNT();
DELETE s FROM t_channel_cost_scope s JOIN t_channel_cost_plan_ver v ON v.id = s.cost_ver_id
  JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/);
SELECT 'del_cost_scope', ROW_COUNT();
DELETE v FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id WHERE p.plan_code IN (/*PLANS*/);
SELECT 'del_cost_version', ROW_COUNT();
DELETE FROM t_channel_cost_plan WHERE plan_code IN (/*PLANS*/);
SELECT 'del_cost_plan', ROW_COUNT();
DELETE d FROM t_mid_capability_detail d JOIN t_mid_capability c ON c.mid_capability_id = d.mid_capability_id
  JOIN t_channel_mid m ON m.mid_id = c.mid_id WHERE m.channel_id IN (/*CHANNELS*/);
SELECT 'del_capability_detail', ROW_COUNT();
DELETE c FROM t_mid_capability c JOIN t_channel_mid m ON m.mid_id = c.mid_id WHERE m.channel_id IN (/*CHANNELS*/);
SELECT 'del_capability', ROW_COUNT();
DELETE FROM t_channel_mid WHERE channel_id IN (/*CHANNELS*/);
SELECT 'del_mid', ROW_COUNT();
DELETE FROM t_channel_account WHERE channel_id IN (/*CHANNELS*/);
SELECT 'del_account', ROW_COUNT();
DELETE FROM t_payment_channel WHERE channel_id IN (/*CHANNELS*/);
SELECT 'del_channel', ROW_COUNT();

-- 3. 复核（期望全部为 0）
SELECT 'left_channels', COUNT(*) FROM t_payment_channel WHERE channel_id IN (/*CHANNELS*/)
UNION ALL SELECT 'left_plans', COUNT(*) FROM t_channel_cost_plan WHERE plan_code IN (/*PLANS*/)
UNION ALL SELECT 'left_changes', COUNT(*) FROM t_channel_config_change WHERE target_type = 'CHANNEL' AND target_id IN (/*CHANNELS*/);
