-- 附录回滚：删除 dq_commercial_golive_reader（先在 Platform data-query 后台把使用该账号的数据源停用，并停用 / 改挂其上的 3 条 golive 模板）。
-- 只在 mor-test MySQL 以 root 执行。可重跑。Vault 中的口令 Token 由 Platform 数据源停用后保留（无删除接口），账号删除后该口令失效。
DROP USER IF EXISTS 'dq_commercial_golive_reader'@'%';
SELECT COUNT(*) AS remaining FROM mysql.user WHERE user = 'dq_commercial_golive_reader';
