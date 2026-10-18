-- Platform Support：Operations 通知管理按时间查询发送记录所需索引。审阅后手工执行。
-- 需求：requirements/20261015/operations-console-通知管理/technique.md 第 3.1 节。
-- 前提：t_notification_request 已按当前 Platform Support Schema 创建，且不存在同名索引。
-- 影响：在线加二级索引，不修改数据；应用不执行本文件。
-- 回退：ALTER TABLE `t_notification_request` DROP KEY `idx_notify_request_create_time`;
SET NAMES utf8mb4;

ALTER TABLE `t_notification_request`
  ADD KEY `idx_notify_request_create_time` (`create_time`) COMMENT '支持访问路径：create_time（运营按时间查询发送记录）';
