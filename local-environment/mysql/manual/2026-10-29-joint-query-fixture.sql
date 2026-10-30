-- 联合查询本地测试链路表（2026-10-29，需求 requirements/20261029/platform-support-联合查询，technique.md §12 T8）。
--
-- 用途：只供本机 Platform 联合查询的 HTTP 入口测试，在已有被查库 mor_data_query_fixture 中建一组小链路表：
--   订单 → 收银台会话；退款申请 → 订单；支付意图（source_type / source_id 以 VARCHAR 保存订单 ID）→ 支付尝试 → 支付动作
--   （渠道交易号）；计费事实（order_ref 以 VARCHAR 保存订单 ID）。订单带 Client 维度列与可屏蔽的买家邮箱；两个 Client 使用
--   相同的客户订单号（CLIENT_ORDER_ID 命中多个 Client）。慢查询与大结果复用 t_number_fixture。不进入 mor-test 或任何共享环境。
--
-- 前提：已按 2026-10-22-data-query-fixture.sql 建好 mor_data_query_fixture 与两个账号；两个账号的库级授权（reader SELECT，
-- writer SELECT, INSERT, UPDATE）自动覆盖新表，本文件不改账号与授权。
-- 本文件只经人工审查后人工执行；Compose、Java 启动、Maven 与 JUnit 都不会执行它。
--
-- 执行方式（在仓库根目录；root 密码来自 local-environment/.env.local，不回显）：
--
--   set -a; source local-environment/.env.local; set +a
--   docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot --default-character-set=utf8mb4 \
--     < local-environment/mysql/manual/2026-10-29-joint-query-fixture.sql
--
-- 幂等性：可重复执行。表 IF NOT EXISTS；数据先清空再写入固定行，执行后数据总是下面这一份。
-- 回滚（同样以 root 人工执行）：
--   DROP TABLE IF EXISTS mor_data_query_fixture.t_jq_billing_fact, mor_data_query_fixture.t_jq_payment_action,
--     mor_data_query_fixture.t_jq_payment_attempt, mor_data_query_fixture.t_jq_payment_intent,
--     mor_data_query_fixture.t_jq_refund, mor_data_query_fixture.t_jq_checkout_session, mor_data_query_fixture.t_jq_order;

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_jq_order (
  order_id BIGINT NOT NULL COMMENT 'Order ID',
  order_no VARCHAR(40) NOT NULL COMMENT 'Order number',
  client_order_id VARCHAR(64) NOT NULL COMMENT 'Client order ID, unique only within one client',
  client_id BIGINT NOT NULL COMMENT 'Client ID (dimension key)',
  checkout_session_id BIGINT NOT NULL COMMENT 'Checkout session ID',
  buyer_email VARCHAR(128) NOT NULL COMMENT 'Synthetic buyer email, masked by the template column declaration',
  PRIMARY KEY (order_id),
  UNIQUE KEY uk_jq_order_no (order_no),
  UNIQUE KEY uk_jq_client_order (client_id, client_order_id)
) ENGINE = InnoDB COMMENT = 'joint-query test fixture: orders';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_jq_checkout_session (
  checkout_session_id BIGINT NOT NULL COMMENT 'Checkout session ID',
  order_id BIGINT NOT NULL COMMENT 'Order ID',
  session_status VARCHAR(32) NOT NULL COMMENT 'Session status',
  PRIMARY KEY (checkout_session_id),
  KEY idx_jq_session_order (order_id)
) ENGINE = InnoDB COMMENT = 'joint-query test fixture: checkout sessions';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_jq_refund (
  refund_request_id BIGINT NOT NULL COMMENT 'Refund request ID',
  refund_request_no VARCHAR(40) NOT NULL COMMENT 'Refund request number rfd_ + 30 alphanumerics',
  order_id BIGINT NOT NULL COMMENT 'Order ID',
  refund_amount DECIMAL(18, 2) NOT NULL COMMENT 'Refund amount in major units',
  PRIMARY KEY (refund_request_id),
  UNIQUE KEY uk_jq_refund_no (refund_request_no),
  KEY idx_jq_refund_order (order_id)
) ENGINE = InnoDB COMMENT = 'joint-query test fixture: refund requests';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_jq_payment_intent (
  payment_intent_id BIGINT NOT NULL COMMENT 'Payment intent ID',
  source_type VARCHAR(32) NOT NULL COMMENT 'Source type, COMMERCE_ORDER for orders',
  source_id VARCHAR(32) NOT NULL COMMENT 'Source ID as a decimal string (VARCHAR order ID)',
  intent_status VARCHAR(32) NOT NULL COMMENT 'Intent status',
  PRIMARY KEY (payment_intent_id),
  KEY idx_jq_intent_source (source_type, source_id)
) ENGINE = InnoDB COMMENT = 'joint-query test fixture: payment intents';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_jq_payment_attempt (
  payment_attempt_id BIGINT NOT NULL COMMENT 'Payment attempt ID',
  payment_intent_id BIGINT NOT NULL COMMENT 'Payment intent ID',
  attempt_status VARCHAR(32) NOT NULL COMMENT 'Attempt status',
  PRIMARY KEY (payment_attempt_id),
  KEY idx_jq_attempt_intent (payment_intent_id)
) ENGINE = InnoDB COMMENT = 'joint-query test fixture: payment attempts';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_jq_payment_action (
  payment_action_id BIGINT NOT NULL COMMENT 'Payment action ID',
  payment_attempt_id BIGINT NOT NULL COMMENT 'Payment attempt ID',
  action_type VARCHAR(32) NOT NULL COMMENT 'Action type',
  external_transaction_id VARCHAR(64) NULL COMMENT 'Channel transaction ID',
  PRIMARY KEY (payment_action_id),
  KEY idx_jq_action_attempt (payment_attempt_id)
) ENGINE = InnoDB COMMENT = 'joint-query test fixture: payment actions';

CREATE TABLE IF NOT EXISTS mor_data_query_fixture.t_jq_billing_fact (
  billing_fact_id BIGINT NOT NULL COMMENT 'Billing fact ID',
  order_ref VARCHAR(32) NOT NULL COMMENT 'Order ID as a decimal string (VARCHAR reference)',
  fact_amount DECIMAL(18, 2) NOT NULL COMMENT 'Fact amount in major units',
  PRIMARY KEY (billing_fact_id),
  KEY idx_jq_billing_order (order_ref)
) ENGINE = InnoDB COMMENT = 'joint-query test fixture: billing facts';

DELETE FROM mor_data_query_fixture.t_jq_billing_fact;
DELETE FROM mor_data_query_fixture.t_jq_payment_action;
DELETE FROM mor_data_query_fixture.t_jq_payment_attempt;
DELETE FROM mor_data_query_fixture.t_jq_payment_intent;
DELETE FROM mor_data_query_fixture.t_jq_refund;
DELETE FROM mor_data_query_fixture.t_jq_checkout_session;
DELETE FROM mor_data_query_fixture.t_jq_order;

INSERT INTO mor_data_query_fixture.t_jq_order
  (order_id, order_no, client_order_id, client_id, checkout_session_id, buyer_email) VALUES
  (5001, 'ord_000000000000000000000000005001', 'CO-1', 3101, 6001, 'buyer.one@example.test'),
  (5002, 'ord_000000000000000000000000005002', 'CO-1', 3102, 6002, 'buyer.two@example.test'),
  (5003, 'ord_000000000000000000000000005003', 'CO-3', 3101, 6003, 'buyer.three@example.test');

INSERT INTO mor_data_query_fixture.t_jq_checkout_session (checkout_session_id, order_id, session_status) VALUES
  (6001, 5001, 'COMPLETED'),
  (6002, 5002, 'COMPLETED'),
  (6003, 5003, 'OPEN');

INSERT INTO mor_data_query_fixture.t_jq_refund (refund_request_id, refund_request_no, order_id, refund_amount) VALUES
  (7001, 'rfd_A00000000000000000000000007001', 5001, 10.00),
  (7002, 'rfd_A00000000000000000000000007002', 5001, 5.50);

INSERT INTO mor_data_query_fixture.t_jq_payment_intent (payment_intent_id, source_type, source_id, intent_status) VALUES
  (8001, 'COMMERCE_ORDER', '5001', 'SUCCEEDED'),
  (8002, 'COMMERCE_ORDER', '5002', 'SUCCEEDED');

INSERT INTO mor_data_query_fixture.t_jq_payment_attempt (payment_attempt_id, payment_intent_id, attempt_status) VALUES
  (9001, 8001, 'FAILED'),
  (9002, 8001, 'SUCCEEDED'),
  (9003, 8002, 'SUCCEEDED');

INSERT INTO mor_data_query_fixture.t_jq_payment_action
  (payment_action_id, payment_attempt_id, action_type, external_transaction_id) VALUES
  (9101, 9001, 'SALE', 'EXT-AAA'),
  (9102, 9002, 'SALE', 'EXT-BBB'),
  (9103, 9003, 'SALE', NULL);

INSERT INTO mor_data_query_fixture.t_jq_billing_fact (billing_fact_id, order_ref, fact_amount) VALUES
  (9501, '5001', 100.00),
  (9502, '5002', 50.00);
