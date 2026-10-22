-- 发布副本（3DS 全链路 + 卡 BIN 库合并发布，阶段 6）。目标库：mor_payment。来源：Code-MoR/requirements/20261022/payment-3DS全链路/db/payment/ddl.sql（SHA-256 cdcf0a0ad0f1c33a，3DS 需求 db）。
-- 以下为来源文件全文逐字副本，未改动业务语句。
-- 需求：payment-3DS全链路（requirements/20261022/payment-3DS全链路）
-- 目标库：mor_payment（Payment 自有库）
-- 文件性质：未执行草案（阶段 3 评审稿）。执行前须经用户授权；执行后同步 PDManer 与 payment-schema.sql。
-- 内容：
--   1) 新建 paytech-messaging 标准消费表 t_consumed_event、t_consumed_aggregate_version：Payment 首次作为 MQ 消费者
--      （消费 channel-connector 的 ChannelPaymentNotified），列、类型、主键与索引与 acquiring 及 messaging-consumed-event.sql 一致。
--   2) t_payment_action.interaction_type 注释：本期写入 THREE_DS_CHALLENGE 与 REDIRECT（结构不变）。
-- 前置：两张表不存在；t_payment_action.interaction_type 定义与 payment-schema.sql 一致。
-- 可重跑：否（CREATE TABLE 不带 IF NOT EXISTS，重复执行会报错停止）。
-- 回滚：应用回滚后两张表不再被读取，保留不删；如需删除，确认无消费记录依赖后 DROP。注释回滚执行原注释的 MODIFY。

CREATE TABLE `t_consumed_event` (
  `event_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件信封 eventId（默认等于 eventKey），按其去重。',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已消费领域事件的类型（PascalCase，登记于 paytech-messaging 事件目录）。',
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `aggregate_version` BIGINT NOT NULL COMMENT '本条已消费领域事件携带的聚合版本。',
  `consumed_at` DATETIME(6) NOT NULL COMMENT '消费事务提交的 UTC 微秒精度时间。',
  PRIMARY KEY (`event_id`),
  KEY `idx_consumed_event_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`) COMMENT '访问索引：aggregate_type、aggregate_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费领域事件：paytech-messaging 消费端按事件 ID 去重的标准表（design/08 §4），列、类型、主键与索引与 paytech-messaging 的 messaging-consumed-event.sql 一致；事件与版本合法性由消费拦截器保证。';

CREATE TABLE `t_consumed_aggregate_version` (
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `last_version` BIGINT NOT NULL COMMENT '本服务已处理的该聚合最大领域事件版本。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费聚合版本：paytech-messaging 消费端按聚合记录已处理的最大版本，用于判定乱序、重放与缺口（design/08 §4）；列、类型与主键与 messaging-consumed-event.sql 一致。';

ALTER TABLE `t_payment_action`
  MODIFY COLUMN `interaction_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原 Action 的消费者交互类型，无交互时为空。允许值：REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE。本期按渠道返回写 THREE_DS_CHALLENGE 或 REDIRECT；完成后保留历史值，可据此判断是否发生过挑战。';
