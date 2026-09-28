-- Commerce Order 创建接入方式；人工审查并执行，不由应用启动或测试自动迁移。
-- 尚未执行；现有订单均来自 Hosted Checkout，因此已有行采用相同默认值。
-- 只增加一列，不更改订单金额、状态、内部版本、Payment Intent 或历史 Session。
-- 上线新的创建/查询代码前，先在获授权的目标 Commerce 数据库应用本 SQL。
-- 执行前确认 creation_mode 尚不存在；本文件不是可重复执行脚本。
ALTER TABLE `t_commerce_order`
  ADD COLUMN `creation_mode` VARCHAR(32) NOT NULL DEFAULT 'HOSTED_CHECKOUT' COMMENT '服务端根据创建入口固定的订单接入方式，创建后不可修改；HOSTED_CHECKOUT为托管收银台，DIRECT_PAY为预留直接支付接入；已有订单及默认值为HOSTED_CHECKOUT。允许值：HOSTED_CHECKOUT、DIRECT_PAY。' AFTER `client_order_id`;
