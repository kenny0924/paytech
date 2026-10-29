-- REQ-05 阶段 5（本机专用）：本机 mor_commercial_v2e2e 的只读账号，供 Platform data-query 数据源 GOLIVE_COMMERCIAL_LOCAL 使用
-- （相当于 mor-test 的 MOR_COMMERCIAL_REPLICA）。只有本库 SELECT。密码由执行者经会话变量 @reader_password 传入，不写入本文件。
-- 回滚：DROP USER IF EXISTS 'golive_e2e_reader'@'%';（并在 data-query 后台停用 / 删除该数据源）
SET @statement = CONCAT('CREATE USER IF NOT EXISTS ''golive_e2e_reader''@''%'' IDENTIFIED BY ', QUOTE(@reader_password));
PREPARE create_reader FROM @statement;
EXECUTE create_reader;
DEALLOCATE PREPARE create_reader;
GRANT SELECT ON mor_commercial_v2e2e.* TO 'golive_e2e_reader'@'%';
SHOW GRANTS FOR 'golive_e2e_reader'@'%';
