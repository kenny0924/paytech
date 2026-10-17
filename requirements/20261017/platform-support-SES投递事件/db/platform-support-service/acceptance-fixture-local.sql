-- Platform SES 投递事件：本机验收合成数据（阶段 5，只在本机 mor_platform_e2e 执行，客户端须 utf8mb4）
-- 3 个发送账号展示「投递事件」列的静态状态：正常接收 / 等待首次拉取 / 未配置。均无密钥 Token，拉取任务不会选中它们。
-- 「拉取失败」由验收脚本经接口新建的 E2E_SES_EVT_LIVE 走真实链路产生（终端指向本机不可达端口）。
-- 1 条通知记录（4 个投递、5 次尝试）覆盖送达、硬退信、软退信 + 投诉、旧尝试送达（结果未知经事件确认）。
-- 编码以 E2E_SES_EVT 开头，来源系统 e2e-ses-events；验收后执行 acceptance-cleanup-local.sql 删除。
START TRANSACTION;
SET @t = UTC_TIMESTAMP(6);
SET @q = 'https://sqs.us-east-1.amazonaws.com/000000000000/e2e-ses-events';
INSERT INTO t_notification_provider (provider_id, provider_code, medium, provider_type, status, region, from_address,
  event_queue_url, event_poll_time, event_poll_error, event_receive_time, version, create_time, update_time) VALUES
 (980000000000002001,'E2E_SES_EVT_OK','EMAIL','AMAZON_SES','ACTIVE','us-east-1','no-reply@e2e.test',
  @q, @t - INTERVAL 1 MINUTE, NULL, @t - INTERVAL 5 MINUTE, 1, @t, @t),
 (980000000000002002,'E2E_SES_EVT_WAIT','EMAIL','AMAZON_SES','ACTIVE','us-east-1','no-reply@e2e.test',
  @q, NULL, NULL, NULL, 1, @t, @t),
 (980000000000002003,'E2E_SES_EVT_NONE','EMAIL','AMAZON_SES','DISABLED','us-east-1','no-reply@e2e.test',
  NULL, NULL, NULL, NULL, 1, @t, @t);
INSERT INTO t_notification_template (template_id, template_code, medium, status, version, create_time, update_time) VALUES
 (980000000000002011,'E2E_SES_EVT_RECORD','EMAIL','DISABLED',1,@t,@t);
INSERT INTO t_notification_request (request_id, tenant_id, source_system, source_event_key, notification_template_id,
  default_locale, default_parameters, notification_route_id, create_time, update_time) VALUES
 (980000000000002021,NULL,'e2e-ses-events','order:E2E-0001',980000000000002011,'zh-CN',
  '{"orderNo":"E2E-SECRET-ORDER"}',980000000000002031,@t - INTERVAL 1 HOUR,@t);
INSERT INTO t_notification_delivery (delivery_id, request_id, address_vault_token, locale, parameters, status,
  attempt_count, next_retry_time, version, create_time, update_time) VALUES
 (980000000000002022,980000000000002021,'vlt_e2esesaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',NULL,NULL,'DELIVERED',1,NULL,3,@t - INTERVAL 1 HOUR,@t),
 (980000000000002023,980000000000002021,'vlt_e2esesbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',NULL,NULL,'FAILED',1,NULL,3,@t - INTERVAL 1 HOUR,@t),
 (980000000000002024,980000000000002021,'vlt_e2esescccccccccccccccccccccccccccccccccc',NULL,NULL,'SENT',1,NULL,2,@t - INTERVAL 1 HOUR,@t),
 (980000000000002025,980000000000002021,'vlt_e2esesddddddddddddddddddddddddddddddddd',NULL,NULL,'DELIVERED',2,NULL,5,@t - INTERVAL 1 HOUR,@t);
INSERT INTO t_notification_attempt (attempt_id, delivery_id, provider_id, provider_request_no, provider_message_id, status,
  response_code, resolution_source, resolution_code, started_time, finished_time, resolved_time,
  event_outcome, event_outcome_code, event_outcome_time, complaint_code, complaint_time, create_time, update_time) VALUES
 (980000000000002041,980000000000002022,980000000000002001,'ses-980000000000002041','e2e-ses-msg-1','ACCEPTED','ACCEPTED',
  'SEND_RESPONSE',NULL,@t - INTERVAL 60 MINUTE,@t - INTERVAL 60 MINUTE,@t - INTERVAL 60 MINUTE,
  'DELIVERED',NULL,@t - INTERVAL 59 MINUTE,NULL,NULL,@t,@t),
 (980000000000002042,980000000000002023,980000000000002001,'ses-980000000000002042','e2e-ses-msg-2','ACCEPTED','ACCEPTED',
  'SEND_RESPONSE',NULL,@t - INTERVAL 60 MINUTE,@t - INTERVAL 60 MINUTE,@t - INTERVAL 60 MINUTE,
  'HARD_BOUNCE','Permanent/General',@t - INTERVAL 58 MINUTE,NULL,NULL,@t,@t),
 (980000000000002043,980000000000002024,980000000000002001,'ses-980000000000002043','e2e-ses-msg-3','ACCEPTED','ACCEPTED',
  'SEND_RESPONSE',NULL,@t - INTERVAL 60 MINUTE,@t - INTERVAL 60 MINUTE,@t - INTERVAL 60 MINUTE,
  'SOFT_BOUNCE','Transient/MailboxFull',@t - INTERVAL 57 MINUTE,'abuse',@t - INTERVAL 30 MINUTE,@t,@t),
 (980000000000002044,980000000000002025,980000000000002001,'ses-980000000000002044',NULL,'ACCEPTED',NULL,
  'PROVIDER_EVENT','EVENT_DELIVERY',@t - INTERVAL 60 MINUTE,NULL,@t - INTERVAL 40 MINUTE,
  'DELIVERED',NULL,@t - INTERVAL 40 MINUTE,NULL,NULL,@t,@t),
 (980000000000002045,980000000000002025,980000000000002001,'ses-980000000000002045','e2e-ses-msg-5','ACCEPTED','ACCEPTED',
  'SEND_RESPONSE',NULL,@t - INTERVAL 50 MINUTE,@t - INTERVAL 50 MINUTE,@t - INTERVAL 50 MINUTE,
  NULL,NULL,NULL,NULL,NULL,@t,@t);
COMMIT;
