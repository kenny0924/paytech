-- data-query 本地测试被查库 mor_data_query_fixture 与两个账号（2026-10-22，需求 requirements/20261022/platform-support-数据查询服务，Q12）。
--
-- 用途：只供本机 Platform data-query 的 HTTP 入口测试作为“被查库”；不进入 mor-test 或任何共享环境。
--   - data_query_fixture_reader：只有本库 SELECT，测试中作为可启用的只读数据源账号。
--   - data_query_fixture_writer：本库 SELECT, INSERT, UPDATE，只用来验证“含写权限的账号不能启用”。
--   - t_client_fixture / t_refund_fixture：小表与固定数据，供分页、总数、条件、集合与关联查询使用。
--   - t_number_fixture：0–999 共 1000 行，三表 CROSS JOIN 约 10^9 行，用于查询超时取消测试（SLEEP 被只读校验拒绝）。
--
-- 适用：已经初始化过的本地 MySQL（新建的 MySQL 卷由 mysql/init/05-data-query-fixture.sh 执行同一正文）。
-- 本文件只经人工审查后人工执行；Compose、Java 启动、Maven 与 JUnit 都不会执行它。
--
-- 执行方式（在仓库根目录；密码来自 local-environment/.env.local，经 stdin 传入，不回显）：
--
--   set -a; source local-environment/.env.local; set +a
--   { printf "SET @reader_password = '%s', @writer_password = '%s';\n" \
--       "$MOR_DATA_QUERY_FIXTURE_READER_PASSWORD" "$MOR_DATA_QUERY_FIXTURE_WRITER_PASSWORD"; \
--     cat local-environment/mysql/manual/2026-10-22-data-query-fixture.sql; } \
--     | docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot --default-character-set=utf8mb4
--
-- 任一会话变量缺失时 QUOTE(NULL) 生成非法语句，PREPARE 失败，mysql 客户端在第一个错误处停止，账号与数据都不改动。
--
-- 幂等性：可重复执行。账号先 DROP USER IF EXISTS 再创建（密码与授权以本次为准）；库与表 IF NOT EXISTS；
-- 三张表的数据先清空再写入固定行，执行后数据总是下面这一份。
--
-- 回滚（同样以 root 人工执行）：
--   DROP USER IF EXISTS 'data_query_fixture_reader'@'%', 'data_query_fixture_writer'@'%';
--   DROP DATABASE IF EXISTS mor_data_query_fixture;
--
-- 正文（从下一行的 BEGIN 标记到 END 标记）必须与 mysql/init/05-data-query-fixture.sh 中的 SQL 保持逐字一致，
-- scripts/verify-local-environment.mjs 会比对。

-- BEGIN data-query fixture
SET @statement = CONCAT('CREATE USER ''data_query_fixture_reader''@''%'' IDENTIFIED BY ', QUOTE(@reader_password));
PREPARE create_reader FROM @statement;
SET @statement = CONCAT('CREATE USER ''data_query_fixture_writer''@''%'' IDENTIFIED BY ', QUOTE(@writer_password));
PREPARE create_writer FROM @statement;

DROP USER IF EXISTS 'data_query_fixture_reader'@'%', 'data_query_fixture_writer'@'%';
EXECUTE create_reader;
EXECUTE create_writer;
DEALLOCATE PREPARE create_reader;
DEALLOCATE PREPARE create_writer;

CREATE DATABASE IF NOT EXISTS mor_data_query_fixture CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT SELECT ON mor_data_query_fixture.* TO 'data_query_fixture_reader'@'%';
GRANT SELECT, INSERT, UPDATE ON mor_data_query_fixture.* TO 'data_query_fixture_writer'@'%';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_client_fixture (
  client_id BIGINT NOT NULL COMMENT 'Client ID',
  client_no VARCHAR(32) NOT NULL COMMENT 'Client number',
  client_name VARCHAR(128) NOT NULL COMMENT 'Client display name',
  client_type VARCHAR(32) NOT NULL COMMENT 'Client type: INDIVIDUAL or ORGANIZATION',
  create_time DATETIME(6) NOT NULL COMMENT 'Creation time (UTC)',
  PRIMARY KEY (client_id),
  UNIQUE KEY uk_client_no (client_no)
) ENGINE = InnoDB COMMENT = 'data-query test fixture: clients';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_refund_fixture (
  refund_id BIGINT NOT NULL COMMENT 'Refund ID',
  refund_no VARCHAR(32) NOT NULL COMMENT 'Refund number',
  client_id BIGINT NOT NULL COMMENT 'Client ID (t_client_fixture.client_id)',
  refund_status VARCHAR(32) NOT NULL COMMENT 'Refund status: PROCESSING, SUCCEEDED or FAILED',
  refund_amount DECIMAL(18, 2) NOT NULL COMMENT 'Refund amount in major units',
  currency CHAR(3) NOT NULL COMMENT 'ISO 4217 currency code',
  create_time DATETIME(6) NOT NULL COMMENT 'Creation time (UTC)',
  PRIMARY KEY (refund_id),
  UNIQUE KEY uk_refund_no (refund_no),
  KEY idx_client_id_create_time (client_id, create_time)
) ENGINE = InnoDB COMMENT = 'data-query test fixture: refunds';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_number_fixture (
  n INT NOT NULL COMMENT 'Sequence number 0-999',
  PRIMARY KEY (n)
) ENGINE = InnoDB COMMENT = 'data-query test fixture: 1000-row sequence for CROSS JOIN timeout tests';

DELETE FROM mor_data_query_fixture.t_refund_fixture;
DELETE FROM mor_data_query_fixture.t_client_fixture;
DELETE FROM mor_data_query_fixture.t_number_fixture;

INSERT INTO mor_data_query_fixture.t_client_fixture (client_id, client_no, client_name, client_type, create_time) VALUES
  (1001, 'FX-CL-1001', 'Fixture Organization One', 'ORGANIZATION', '2026-10-01 08:00:00.000000'),
  (1002, 'FX-CL-1002', 'Fixture Individual Two', 'INDIVIDUAL', '2026-10-02 08:00:00.000000'),
  (1003, 'FX-CL-1003', 'Fixture Organization Three', 'ORGANIZATION', '2026-10-03 08:00:00.000000');

INSERT INTO mor_data_query_fixture.t_refund_fixture
  (refund_id, refund_no, client_id, refund_status, refund_amount, currency, create_time) VALUES
  (2001, 'FX-RF-2001', 1001, 'SUCCEEDED', 10.00, 'USD', '2026-10-05 09:00:00.000000'),
  (2002, 'FX-RF-2002', 1001, 'SUCCEEDED', 25.50, 'USD', '2026-10-06 09:00:00.000000'),
  (2003, 'FX-RF-2003', 1001, 'PROCESSING', 7.25, 'USD', '2026-10-07 09:00:00.000000'),
  (2004, 'FX-RF-2004', 1001, 'FAILED', 100.00, 'USD', '2026-10-08 09:00:00.000000'),
  (2005, 'FX-RF-2005', 1002, 'SUCCEEDED', 3.99, 'USD', '2026-10-09 09:00:00.000000'),
  (2006, 'FX-RF-2006', 1002, 'PROCESSING', 42.00, 'USD', '2026-10-10 09:00:00.000000'),
  (2007, 'FX-RF-2007', 1003, 'SUCCEEDED', 150.00, 'AED', '2026-10-11 09:00:00.000000'),
  (2008, 'FX-RF-2008', 1003, 'SUCCEEDED', 60.00, 'USD', '2026-10-12 09:00:00.000000'),
  (2009, 'FX-RF-2009', 1003, 'FAILED', 12.00, 'AED', '2026-10-13 09:00:00.000000'),
  (2010, 'FX-RF-2010', 1003, 'PROCESSING', 0.01, 'USD', '2026-10-14 09:00:00.000000');

INSERT INTO mor_data_query_fixture.t_number_fixture (n)
WITH RECURSIVE sequence_numbers (n) AS (
  SELECT 0
  UNION ALL
  SELECT n + 1 FROM sequence_numbers WHERE n < 999
)
SELECT n FROM sequence_numbers;
-- END data-query fixture
