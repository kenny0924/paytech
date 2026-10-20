-- counterparty 改名 · 本机 · 回滚：删除新库与新账号。旧库 mor_consumer / consumer_app 未被本迁移修改，回滚后
-- 切回旧分支的 consumer-service 即可继续使用。只在确认 counterparty-service 已停止后执行。
DROP DATABASE IF EXISTS `mor_counterparty`;
DROP USER IF EXISTS 'counterparty_app'@'%';
