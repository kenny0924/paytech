-- Platform SES 投递事件：删除 mor-test 验收数据（mor_platform，客户端须 utf8mb4）。
-- 范围：来源系统 e2esesevents 的通知记录及其投递、尝试；模板 E2E_SES_EVT_TEST 及其内容、路由。
-- 保留：审计事件（追加记录）、Vault 中模拟收件地址的密文、补偿事件的已完成记录。
START TRANSACTION;
DELETE a FROM t_notification_attempt a
  JOIN t_notification_delivery d ON d.delivery_id = a.delivery_id
  JOIN t_notification_request r ON r.request_id = d.request_id
 WHERE r.source_system = 'e2esesevents';
DELETE d FROM t_notification_delivery d
  JOIN t_notification_request r ON r.request_id = d.request_id
 WHERE r.source_system = 'e2esesevents';
DELETE FROM t_notification_request WHERE source_system = 'e2esesevents';
DELETE c FROM t_notification_template_content c JOIN t_notification_template t ON t.template_id = c.template_id
 WHERE t.template_code = 'E2E_SES_EVT_TEST';
DELETE r FROM t_notification_route r JOIN t_notification_template t ON t.template_id = r.notification_template_id
 WHERE t.template_code = 'E2E_SES_EVT_TEST';
DELETE FROM t_notification_template WHERE template_code = 'E2E_SES_EVT_TEST';
COMMIT;
