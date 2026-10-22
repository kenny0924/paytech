-- acquiring 订单与会话过期收尾 · mor-test 只读预检（阶段 6 编写；阶段 7 S1 复跑）。只有 SELECT / SHOW，不写任何库。
-- 执行：scripts/precheck.sh（root 口令只经 MYSQL_PWD 传入容器）。期望值见 release.md 第 3 节。

-- P1 订单表所需列与索引（本需求无 DDL，只确认已存在）
SELECT 'P1 columns' AS chk, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='mor_acquiring' AND TABLE_NAME='t_commerce_order'
  AND COLUMN_NAME IN ('order_status','status_reason_code','termination_requested_at','expires_at','paid_at','cancelled_at','version')
ORDER BY COLUMN_NAME;
SELECT 'P1 index' AS chk, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols, MAX(NON_UNIQUE) AS non_unique
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='mor_acquiring' AND TABLE_NAME='t_commerce_order' AND INDEX_NAME='idx_order_expiry'
GROUP BY INDEX_NAME;

-- P2 状态列注释（EXPIRED 已在允许值内）
SELECT 'P2 comments' AS chk, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE (TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME) IN (
  ('mor_acquiring','t_commerce_order','order_status'),
  ('mor_acquiring','t_checkout_session','session_status'),
  ('mor_acquiring','t_order_payment_projection','intent_status'),
  ('mor_payment','t_payment_intent','intent_status'));

-- P3 到期仍 CREATED / OPEN 的订单（发布后第一轮扫描处理对象）
SELECT 'P3 due total' AS chk, order_status, COUNT(*) AS n, MIN(expires_at) AS oldest, MAX(expires_at) AS newest
FROM mor_acquiring.t_commerce_order
WHERE order_status IN ('CREATED','OPEN') AND expires_at <= UTC_TIMESTAMP(6)
GROUP BY order_status;

-- P4 按分支分布：有无支付投影 / 接受证据 / 已写终止请求
SELECT 'P4 due by branch' AS chk,
       o.order_status,
       (p.order_id IS NOT NULL) AS has_projection,
       (EXISTS (SELECT 1 FROM mor_acquiring.t_consumer_acceptance a JOIN mor_acquiring.t_consumer_contract c
               ON c.consumer_contract_id=a.consumer_contract_id WHERE c.order_id=o.order_id)) AS has_acceptance,
       (o.termination_requested_at IS NOT NULL) AS termination_requested,
       COUNT(*) AS n
FROM mor_acquiring.t_commerce_order o
LEFT JOIN mor_acquiring.t_order_payment_projection p ON p.order_id=o.order_id
WHERE o.order_status IN ('CREATED','OPEN') AND o.expires_at <= UTC_TIMESTAMP(6)
GROUP BY 2,3,4,5 ORDER BY 2,3,4,5;

-- P5 有投影的到期订单：投影 Intent 状态与 Payment 侧 Intent 状态 / 活动尝试
SELECT 'P5 due with projection' AS chk, p.intent_status AS projection_status, i.intent_status AS payment_status,
       (i.active_attempt_id IS NOT NULL) AS has_active_attempt, (i.successful_attempt_id IS NOT NULL) AS has_success, COUNT(*) AS n
FROM mor_acquiring.t_commerce_order o
JOIN mor_acquiring.t_order_payment_projection p ON p.order_id=o.order_id
LEFT JOIN mor_payment.t_payment_intent i ON i.payment_intent_id=p.payment_intent_id
WHERE o.order_status IN ('CREATED','OPEN') AND o.expires_at <= UTC_TIMESTAMP(6)
GROUP BY 2,3,4,5;

-- P6 无投影的到期订单在 Payment 是否有按来源的 Intent（按来源查询分支）
SELECT 'P6 due no projection, payment intent by source' AS chk, i.intent_status, COUNT(*) AS n
FROM mor_acquiring.t_commerce_order o
LEFT JOIN mor_acquiring.t_order_payment_projection p ON p.order_id=o.order_id
LEFT JOIN mor_payment.t_payment_intent i ON i.source_type='COMMERCE_ORDER' AND i.source_id=o.order_id
WHERE o.order_status IN ('CREATED','OPEN') AND o.expires_at <= UTC_TIMESTAMP(6) AND p.order_id IS NULL
GROUP BY 2;

-- P7 到期订单的 Client 分布与到期时长分布（影响面）
SELECT 'P7 due by client' AS chk, o.client_id, COUNT(*) AS n FROM mor_acquiring.t_commerce_order o
WHERE o.order_status IN ('CREATED','OPEN') AND o.expires_at <= UTC_TIMESTAMP(6) GROUP BY o.client_id ORDER BY n DESC;
SELECT 'P7 due by age' AS chk,
       CASE WHEN TIMESTAMPDIFF(HOUR, expires_at, UTC_TIMESTAMP(6)) < 24 THEN '<24h'
            WHEN TIMESTAMPDIFF(DAY, expires_at, UTC_TIMESTAMP(6)) < 7 THEN '1-7d' ELSE '>=7d' END AS age, COUNT(*) AS n
FROM mor_acquiring.t_commerce_order WHERE order_status IN ('CREATED','OPEN') AND expires_at <= UTC_TIMESTAMP(6) GROUP BY 2;

-- P8 未来 24 小时内将到期的 CREATED / OPEN 订单（发布后陆续进入扫描）与全表状态分布
SELECT 'P8 due within 24h' AS chk, COUNT(*) AS n FROM mor_acquiring.t_commerce_order
WHERE order_status IN ('CREATED','OPEN') AND expires_at > UTC_TIMESTAMP(6) AND expires_at <= UTC_TIMESTAMP(6) + INTERVAL 24 HOUR;
SELECT 'P8 order status' AS chk, order_status, COUNT(*) AS n FROM mor_acquiring.t_commerce_order GROUP BY order_status;

-- P9 现有 EXPIRED 订单与原因码（发布前应为 0：仓库此前无写 EXPIRED 的代码）
SELECT 'P9 expired/reason' AS chk, order_status, status_reason_code, COUNT(*) AS n FROM mor_acquiring.t_commerce_order
WHERE order_status IN ('EXPIRED','CANCELLED') GROUP BY 2,3;

-- P10 acquiring 未完成补偿任务（替换前后核对；本需求新增事件同属 CLIENT_WEBHOOK_REQUESTED）
SELECT 'P10 acquiring compensation' AS chk, event_type, sub_event_type, execute_status, COUNT(*) AS n FROM mor_acquiring.t_compensation_event GROUP BY 2,3,4;

-- P11 Payment Intent 状态分布与未完成补偿任务（PAY_RESULT_QUERY 等由 compensationEventScan 执行）
SELECT 'P11 payment intents' AS chk, intent_status, COUNT(*) AS n FROM mor_payment.t_payment_intent GROUP BY 2;
SELECT 'P11 payment compensation' AS chk, event_type, sub_event_type, execute_status, COUNT(*) AS n FROM mor_payment.t_compensation_event GROUP BY 2,3,4;

-- P12 扫描语句执行计划（技术方案 3.4 #1；EXP-C 遗留：生产数据量下可能 filesort）
EXPLAIN SELECT order_id FROM mor_acquiring.t_commerce_order
WHERE order_status IN ('CREATED','OPEN') AND expires_at <= UTC_TIMESTAMP(6) AND order_id > 0 AND MOD(order_id, 1) = 0
ORDER BY order_id LIMIT 200;
