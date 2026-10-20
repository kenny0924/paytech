-- counterparty 改名 · mor-test · 回滚：先停 counterparty-service，再删除新库与新账号；mor_consumer / consumer_app 未被修改，
-- 用旧镜像重新启动 consumer-service 即恢复改名前状态（XXL-Job 执行器 consumer-service 的登记保留不删，可直接复用）。
-- 注意：若 counterparty-service 已在新库产生新数据，回滚会丢弃这些数据；需先导出 mor_counterparty 并评估是否回灌旧库。
DROP DATABASE IF EXISTS `mor_counterparty`;
DROP USER IF EXISTS 'counterparty_app'@'%';
