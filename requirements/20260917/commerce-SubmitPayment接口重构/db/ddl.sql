-- Commerce SubmitPayment 去请求幂等：部署代码前人工应用到目标 Commerce 库。
-- 仅放宽旧列，不删除或更新历史记录；新写入省略该列。
-- 本文件未执行，不授权自动迁移或应用启动时执行。
ALTER TABLE t_consumer_acceptance
    MODIFY COLUMN command_idempotency_id BIGINT NULL
    COMMENT '历史受控命令标识；新支付接受不再记录。';
