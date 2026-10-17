-- Platform 邮件投递事件（Amazon SES）：结构变更
-- 目标库：Platform Support（本机 mor_platform / mor_platform_e2e；mor-test mor_platform）。执行需单独授权。
-- 性质：增量执行文件。只加可空列与改列注释，旧 Platform 不读新列，可在换镜像前执行。不可重复执行（重复 ADD COLUMN 会报错）。
-- 回退：不提供删列脚本；新列保留对旧版本无影响。

ALTER TABLE `t_notification_provider`
  ADD COLUMN `event_queue_url` VARCHAR(512) NULL COMMENT '接收投递事件的 SQS 队列 URL；为空则不拉取事件。' AFTER `credential_update_time`,
  ADD COLUMN `event_poll_time` DATETIME(6) NULL COMMENT '最近一次成功拉取事件队列的 UTC 微秒时间；运行状态，不推进 version。' AFTER `event_queue_url`,
  ADD COLUMN `event_poll_error` VARCHAR(64) NULL COMMENT '最近一次拉取失败的安全错误码；成功拉取后清空。允许值：SQS_ACCESS_DENIED、SQS_QUEUE_NOT_FOUND、SQS_THROTTLED、SQS_UNAVAILABLE、SQS_CLIENT_ERROR、CREDENTIAL_UNAVAILABLE。' AFTER `event_poll_time`,
  ADD COLUMN `event_receive_time` DATETIME(6) NULL COMMENT '最近一次从事件队列收到消息的 UTC 微秒时间；运行状态，不推进 version。' AFTER `event_poll_error`;

ALTER TABLE `t_notification_attempt`
  MODIFY COLUMN `resolution_source` VARCHAR(32) NULL COMMENT '结果确认来源。允许值：SEND_RESPONSE、PROVIDER_QUERY、PROVIDER_EVENT。',
  ADD COLUMN `event_outcome` VARCHAR(32) NULL COMMENT '供应商投递事件的结果，优先级只升不降。允许值：SOFT_BOUNCE、DELIVERED、HARD_BOUNCE、REJECTED。' AFTER `resolved_time`,
  ADD COLUMN `event_outcome_code` VARCHAR(64) NULL COMMENT '投递事件的安全分类码，如 Permanent/General、Reject/Bad content；送达为空。' AFTER `event_outcome`,
  ADD COLUMN `event_outcome_time` DATETIME(6) NULL COMMENT '投递事件结果发生的 UTC 微秒时间（取自供应商事件）。' AFTER `event_outcome_code`,
  ADD COLUMN `complaint_code` VARCHAR(64) NULL COMMENT '收件人投诉的安全分类码，如 abuse；首个投诉为准。' AFTER `event_outcome_time`,
  ADD COLUMN `complaint_time` DATETIME(6) NULL COMMENT '收件人投诉发生的 UTC 微秒时间（取自供应商事件）。' AFTER `complaint_code`;
