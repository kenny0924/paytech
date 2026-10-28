-- 附录（单独授权，不属于主发布）：REQ-01 data-query 模板专用只读账号 dq_commercial_golive_reader，只在 mor-test MySQL 以 root 执行，客户端 utf8mb4。
-- 范围：只 SELECT mor_commercial 中三条 golive 模板涉及的 5 张表（db/data-query-templates.sql：t_agreement、t_agreement_capability、
--       t_commercial_term_plan、t_commercial_term_version、t_product_capability），表级授权，不授予库级权限。
-- 口令：执行脚本从 test-environment/private/ 下新建的 0600 文件读取，经会话变量 @dq_reader_password 传入（≥ 32 字符），不落 SQL 文件、不打印；
--       同一口令随后只在 Platform data-query 后台新建数据源时输入一次，由 Platform 写入 Vault（DATA_SOURCE_PASSWORD_V1），t_data_source 只存 Token。
-- 可重跑：CREATE USER IF NOT EXISTS；GRANT 幂等。回滚：appendix-data-query-reader-rollback.sql。
-- 备选（待裁决）：mor-test 已有数据源 MOR_COMMERCIAL_REPLICA（账号 data_query_demo_reader，库级 SELECT mor_commercial.*，ACTIVE / PASSED），
--       若接受库级只读，可不建本账号，直接在该数据源上登记三条模板。
SET NAMES utf8mb4;
SELECT IF(CHAR_LENGTH(@dq_reader_password) >= 32, 0, (SELECT 1 UNION ALL SELECT 2)) AS password_must_be_set;
SET @ddl = CONCAT('CREATE USER IF NOT EXISTS ''dq_commercial_golive_reader''@''%'' IDENTIFIED BY ', QUOTE(@dq_reader_password));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @dq_reader_password = NULL;
GRANT SELECT ON mor_commercial.t_agreement TO 'dq_commercial_golive_reader'@'%';
GRANT SELECT ON mor_commercial.t_agreement_capability TO 'dq_commercial_golive_reader'@'%';
GRANT SELECT ON mor_commercial.t_commercial_term_plan TO 'dq_commercial_golive_reader'@'%';
GRANT SELECT ON mor_commercial.t_commercial_term_version TO 'dq_commercial_golive_reader'@'%';
GRANT SELECT ON mor_commercial.t_product_capability TO 'dq_commercial_golive_reader'@'%';
SHOW GRANTS FOR 'dq_commercial_golive_reader'@'%';
