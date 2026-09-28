-- Commerce 订单取消时间；人工审查并执行，应用和测试不得自动迁移。
-- 仅准备文件，尚未执行。目标为获授权的 Commerce 自有数据库。
-- 执行前确认 cancelled_at 尚不存在；本文件不是可重复执行脚本。
-- 部署依赖：先应用已批准的 creation-mode.sql，再应用本文件，最后启动本轮代码。
-- 在停止旧取消写入的迁移窗口执行，避免回填后旧代码又产生缺少订单取消时间的行。
ALTER TABLE `t_commerce_order`
  ADD COLUMN `cancelled_at` DATETIME(6) NULL COMMENT '本订单或 Checkout 会话进入取消终态的 UTC 业务时刻，重复取消不改写。' AFTER `paid_at`;

-- 将既有已取消会话的原时间复制到对应已取消订单，不改写状态、版本、金额或会话事实。
-- 无可信来源时保留 NULL，不使用 update_time、termination_requested_at 或执行时间猜测。
UPDATE `t_commerce_order` o
INNER JOIN `t_checkout_session` s ON s.`order_id` = o.`order_id`
SET o.`cancelled_at` = s.`cancelled_at`
WHERE o.`order_status` = 'CANCELLED' AND o.`cancelled_at` IS NULL AND o.`paid_at` IS NULL
  AND s.`session_status` = 'CANCELLED' AND s.`cancelled_at` IS NOT NULL;

-- 部署前人工处理缺少原始取消时间的历史异常；本查询应返回空集。
SELECT `order_id`, `order_no`
FROM `t_commerce_order`
WHERE `order_status` = 'CANCELLED' AND `cancelled_at` IS NULL;
