-- 仅供本机 mor-test / 127.0.0.1:23306 / mor_platform；阶段 6 准备，尚未执行。
-- 必须持有既有 risk-operations-rollout.lock，停止其他控制面写入并完成备份。
-- mysql --batch --skip-reconnect 执行，禁止 --force；失败断开会话回滚。
SET @release_saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
CREATE TEMPORARY TABLE risk_list_release_gate (checkpoint VARCHAR(100) NOT NULL);
INSERT INTO risk_list_release_gate SELECT IF(DATABASE()='mor_platform','exact database',NULL);
START TRANSACTION;
SELECT resource_id FROM t_console_resource
WHERE resource_id BETWEEN 900000000000000565 AND 900000000000000571 ORDER BY resource_id FOR UPDATE;
SELECT route_id FROM t_gateway_route WHERE route_id BETWEEN 900000000000001024 AND 900000000000001033 ORDER BY route_id FOR UPDATE;
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_console_resource
WHERE resource_id BETWEEN 900000000000000565 AND 900000000000000570 AND channel='OPERATIONS')=6,
'exact six existing Operations resources',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000000565 AND 900000000000000570 AND status='DISABLED' AND version=5)=6,'old resources hidden',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_console_resource WHERE resource_id=900000000000000571 AND resource_code='OPERATIONS_RISK_RULE_SET_DELETE' AND status='DISABLED' AND version=1)=1,'delete button hidden',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001033 AND route_code='operations-risk-rule-set-delete' AND status='ACTIVE' AND version=2)=1,'delete route enabled; runtime convergence is a separate prerequisite',NULL);
UPDATE t_console_resource SET status='ACTIVE',version=version+1,update_time=UTC_TIMESTAMP(6) WHERE resource_id BETWEEN 900000000000000565 AND 900000000000000570 AND status='DISABLED' AND version=5;
INSERT INTO risk_list_release_gate SELECT IF(ROW_COUNT()=6,'six resources restored',NULL);
UPDATE t_console_resource SET status='ACTIVE',version=version+1,update_time=UTC_TIMESTAMP(6) WHERE resource_id=900000000000000571 AND status='DISABLED' AND version=1;
INSERT INTO risk_list_release_gate SELECT IF(ROW_COUNT()=1,'delete button exposed',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001024 AND route_code='operations-risk-rule-authoring-context' AND status='ACTIVE' AND version=4)=1,'old route 900000000000001024 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001025 AND route_code='operations-risk-rule-set-list' AND status='ACTIVE' AND version=6)=1,'old route 900000000000001025 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001026 AND route_code='operations-risk-rule-set-detail' AND status='ACTIVE' AND version=4)=1,'old route 900000000000001026 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001027 AND route_code='operations-risk-rule-set-save' AND status='ACTIVE' AND version=6)=1,'old route 900000000000001027 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001028 AND route_code='operations-risk-rule-set-freeze' AND status='ACTIVE' AND version=10)=1,'old route 900000000000001028 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001029 AND route_code='operations-risk-test-candidate-list' AND status='ACTIVE' AND version=6)=1,'old route 900000000000001029 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001030 AND route_code='operations-risk-test-candidate-detail' AND status='ACTIVE' AND version=6)=1,'old route 900000000000001030 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001031 AND route_code='operations-risk-test-candidate-create' AND status='ACTIVE' AND version=4)=1,'old route 900000000000001031 restored',NULL);
INSERT INTO risk_list_release_gate SELECT IF((SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000001032 AND route_code='operations-risk-test-candidate-validate' AND status='ACTIVE' AND version=4)=1,'old route 900000000000001032 restored',NULL);
COMMIT;
DROP TEMPORARY TABLE risk_list_release_gate;
SET SESSION sql_mode = @release_saved_sql_mode;
