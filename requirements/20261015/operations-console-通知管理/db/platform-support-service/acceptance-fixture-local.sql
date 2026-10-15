-- Operations 通知管理：本机验收合成数据（阶段 5，只在本机 mor_platform_e2e 执行，客户端须 utf8mb4）
-- 2 个发送账号（启用 / 停用，无发送适配器）与 1 条普通通知记录（2 个投递、3 次尝试）；邮箱只有合成 Token。
-- 编码以 E2E_ 开头，来源系统 e2e-notification-admin；验收后执行 acceptance-cleanup-local.sql 删除。
START TRANSACTION;
SET @ntf_e2e_time = UTC_TIMESTAMP(6);
INSERT INTO t_notification_provider VALUES
 (980000000000001001,'E2E_NOTIFY_EMAIL','EMAIL','ACTIVE',1,@ntf_e2e_time,@ntf_e2e_time),
 (980000000000001002,'E2E_NOTIFY_OFF','EMAIL','DISABLED',1,@ntf_e2e_time,@ntf_e2e_time);
INSERT INTO t_notification_template VALUES
 (980000000000001011,'E2E_NOTIFY_RECORD','EMAIL','DISABLED',1,@ntf_e2e_time,@ntf_e2e_time);
INSERT INTO t_notification_request VALUES
 (980000000000001021,NULL,'e2e-notification-admin','refund:E2E-0001',980000000000001011,'zh-CN',
  '{"name":"E2E-SECRET-NAME","amount":"10.00"}',980000000000001031,@ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time);
INSERT INTO t_notification_delivery VALUES
 (980000000000001022,980000000000001021,'vlt_e2eaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',NULL,NULL,'SENT',1,NULL,2,
  @ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time),
 (980000000000001023,980000000000001021,'vlt_e2ebbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','en-US','{"name":"E2E-SECRET-B"}','FAILED',2,NULL,3,
  @ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time);
INSERT INTO t_notification_attempt VALUES
 (980000000000001024,980000000000001022,980000000000001001,'e2e-req-1','e2e-msg-1','ACCEPTED','ACCEPTED','SEND_RESPONSE',NULL,
  @ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time,@ntf_e2e_time),
 (980000000000001025,980000000000001023,980000000000001001,'e2e-req-2',NULL,'FAILED','THROTTLED','SEND_RESPONSE',NULL,
  @ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time - INTERVAL 1 HOUR,@ntf_e2e_time,@ntf_e2e_time),
 (980000000000001026,980000000000001023,980000000000001001,'e2e-req-3',NULL,'FAILED','REJECTED','SEND_RESPONSE',NULL,
  @ntf_e2e_time - INTERVAL 50 MINUTE,@ntf_e2e_time - INTERVAL 50 MINUTE,@ntf_e2e_time - INTERVAL 50 MINUTE,@ntf_e2e_time,@ntf_e2e_time);
COMMIT;
