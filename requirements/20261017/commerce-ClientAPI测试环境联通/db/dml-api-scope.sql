-- Client API 联通（2026-10-17）：mor_platform API Scope 目录补齐 Commerce Client API 设计第 3.3 节的 Scope。
-- orders.read 已由接入自助验收夹具建立（9300000000000001），此处不重复。
-- 执行前核对：SELECT api_scope_id, scope_code FROM t_api_scope
--   WHERE api_scope_id BETWEEN 9300000000000002 AND 9300000000000005
--      OR scope_code IN ('checkouts.write','orders.cancel','refunds.read','refunds.write');  -- 期望 0 行
-- 回滚（仅在没有凭证授权引用时）：
--   DELETE FROM t_api_scope WHERE api_scope_id BETWEEN 9300000000000002 AND 9300000000000005;

INSERT INTO t_api_scope (api_scope_id, scope_code, display_name, description, version, create_time, update_time) VALUES
  (9300000000000002, 'checkouts.write', '创建结账', 'Client API V1：创建 Hosted Checkout 订单', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9300000000000003, 'orders.cancel', '取消订单', 'Client API V1：取消未支付订单', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9300000000000004, 'refunds.read', '查询退款', 'Client API V1：查询可退金额与退款', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9300000000000005, 'refunds.write', '创建退款', 'Client API V1：对已支付订单创建退款', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
