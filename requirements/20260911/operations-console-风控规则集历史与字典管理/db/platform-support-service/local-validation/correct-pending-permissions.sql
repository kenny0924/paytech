-- 待确认、未执行：仅修正本轮新建的 local-environment 独立验证库。
-- 执行前停止本库应用/测试，备份以下 7 条 t_role_permission 和 1 条 t_role；核验 ID、状态、版本。
-- 使用 mysql --batch --skip-reconnect，不使用 --force；任何失败立即断开，事务回滚。
-- 不用于 mor_platform 或 test；不新增权限，不开放 Route/Resource，不删除记录。
SET @rco16_permission_old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
CREATE TEMPORARY TABLE rco16_permission_gate (checkpoint VARCHAR(100) NOT NULL);
INSERT INTO rco16_permission_gate SELECT IF(DATABASE()='mor_platform_rco16_validation','exact validation database',NULL);
START TRANSACTION;
SELECT role_id FROM t_role WHERE role_id=900000000000000510 FOR UPDATE;
SELECT role_permission_id FROM t_role_permission WHERE role_permission_id IN (900000000000000587, 900000000000012201, 900000000000012202, 900000000000012203, 900000000000012204, 900000000000012205, 900000000000012206) ORDER BY role_permission_id FOR UPDATE;
INSERT INTO rco16_permission_gate SELECT IF((SELECT COUNT(*) FROM t_user_account)=0 AND (SELECT COUNT(*) FROM t_auth_session)=0,'empty validation business baseline',NULL);
INSERT INTO rco16_permission_gate SELECT IF((SELECT COUNT(*) FROM t_role WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND status='ACTIVE' AND version=2)=1,'exact administrator role version 2',NULL);
INSERT INTO rco16_permission_gate SELECT IF((SELECT COUNT(*) FROM t_role_permission rp WHERE ((rp.role_permission_id=900000000000000587 AND rp.permission_id=900000000000000541) OR (rp.role_permission_id=900000000000012201 AND rp.permission_id=900000000000012101) OR (rp.role_permission_id=900000000000012202 AND rp.permission_id=900000000000012102) OR (rp.role_permission_id=900000000000012203 AND rp.permission_id=900000000000012103) OR (rp.role_permission_id=900000000000012204 AND rp.permission_id=900000000000012104) OR (rp.role_permission_id=900000000000012205 AND rp.permission_id=900000000000012105) OR (rp.role_permission_id=900000000000012206 AND rp.permission_id=900000000000012106)) AND rp.role_id=900000000000000510 AND rp.status='ACTIVE' AND rp.version=1)=7,'exact seven owned pending bindings',NULL);
INSERT INTO rco16_permission_gate SELECT IF(NOT EXISTS (
 SELECT 1 FROM t_role_permission rp WHERE rp.role_permission_id IN (900000000000000587, 900000000000012201, 900000000000012202, 900000000000012203, 900000000000012204, 900000000000012205, 900000000000012206) AND (
  EXISTS (SELECT 1 FROM t_gateway_route gr JOIN t_gateway_target gt ON gt.target_id=gr.target_id AND gt.channel=gr.channel AND gt.status='ACTIVE' WHERE gr.permission_id=rp.permission_id AND gr.channel='OPERATIONS' AND gr.status='ACTIVE' AND gr.authorization_mode='HUMAN_PERMISSION')
  OR EXISTS (SELECT 1 FROM t_console_resource cr WHERE cr.permission_id=rp.permission_id AND cr.channel='OPERATIONS' AND cr.status='ACTIVE')
 )), 'all seven bindings have no active entry', NULL);
UPDATE t_role_permission SET status='REVOKED',version=version+1,update_time=UTC_TIMESTAMP(6)
 WHERE role_permission_id IN (900000000000000587, 900000000000012201, 900000000000012202, 900000000000012203, 900000000000012204, 900000000000012205, 900000000000012206) AND role_id=900000000000000510 AND status='ACTIVE' AND version=1;
INSERT INTO rco16_permission_gate SELECT IF(ROW_COUNT()=7,'exact seven binding changes',NULL);
UPDATE t_role SET version=version+1,update_time=UTC_TIMESTAMP(6)
 WHERE role_id=900000000000000510 AND role_code='PLATFORM_ADMIN' AND status='ACTIVE' AND version=2;
INSERT INTO rco16_permission_gate SELECT IF(ROW_COUNT()=1,'administrator version increment',NULL);
COMMIT;
DROP TEMPORARY TABLE rco16_permission_gate;
SET SESSION sql_mode = @rco16_permission_old_sql_mode;
