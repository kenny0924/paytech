-- Operations 通知管理：删除本机验收数据（合成数据与验收中经接口新建的 E2E_ 模板及其内容、路由）。
-- 审计事件为追加记录，不删除。
START TRANSACTION;
DELETE FROM t_notification_attempt WHERE attempt_id BETWEEN 980000000000001024 AND 980000000000001026;
DELETE FROM t_notification_delivery WHERE delivery_id BETWEEN 980000000000001022 AND 980000000000001023;
DELETE FROM t_notification_request WHERE request_id = 980000000000001021;
DELETE c FROM t_notification_template_content c JOIN t_notification_template t ON t.template_id = c.template_id
 WHERE t.template_code LIKE 'E2E!_NOTIFY%' ESCAPE '!';
DELETE r FROM t_notification_route r JOIN t_notification_template t ON t.template_id = r.notification_template_id
 WHERE t.template_code LIKE 'E2E!_NOTIFY%' ESCAPE '!';
DELETE FROM t_notification_template WHERE template_code LIKE 'E2E!_NOTIFY%' ESCAPE '!';
DELETE FROM t_notification_provider WHERE provider_id BETWEEN 980000000000001001 AND 980000000000001002;
COMMIT;
