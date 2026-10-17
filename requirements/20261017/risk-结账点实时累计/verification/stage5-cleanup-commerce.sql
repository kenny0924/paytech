-- 需求：requirements/20261017/risk-结账点实时累计（阶段 5 本机验收后执行）。目标库：本机 mor_commerce。
-- 作用：删除本机验收创建的订单（client_order_id 以 s5v- 开头）及其上下文、法律事实、明细、会话与投影。
-- 与上一需求脚本相比补充 t_order_legal（按 order_context_id 关联）。
-- 用法：先原样执行（末尾 ROLLBACK）核对行数，再改为 COMMIT 执行一次。

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_stage5_order AS
SELECT order_id FROM t_commerce_order WHERE client_order_id LIKE 's5v-%';
SELECT COUNT(*) AS orders_selected FROM tmp_stage5_order;

DELETE x FROM t_checkout_network_evidence x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS network_evidence;
DELETE x FROM t_checkout_session x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS sessions;
DELETE x FROM t_consumer_context x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS consumer_contexts;
DELETE x FROM t_consumer_contract x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS consumer_contracts;
DELETE x FROM t_order_funds_availability_projection x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS funds_projection;
DELETE x FROM t_order_payment_projection x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS payment_projection;
DELETE x FROM t_order_refund_position x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS refund_positions;
DELETE x FROM t_refund_request x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS refund_requests;
DELETE x FROM t_order_item x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS items;
DELETE l FROM t_order_legal l JOIN t_order_context c ON c.order_context_id = l.order_context_id JOIN tmp_stage5_order o ON o.order_id = c.order_id;
SELECT ROW_COUNT() AS order_legal;
DELETE x FROM t_order_context x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS contexts;
DELETE x FROM t_commerce_order x JOIN tmp_stage5_order o USING (order_id);
SELECT ROW_COUNT() AS orders;

DROP TEMPORARY TABLE tmp_stage5_order;

ROLLBACK;
