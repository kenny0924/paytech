-- 待人工执行：仅限已核对的本地 127.0.0.1:13306/mor_commerce。
-- 用户批准并完成全库备份后应用；本文件不会由应用或测试执行。
-- 前置：旧 15 表 Schema，t_commerce_order 中尚无 context_format/entity_no/preparation_command_id/termination_requested_at。
-- 保留所有旧列及旧数据；不转换旧 OE、Assignment、Scope、JSON、Hash 或金额。
-- 临时 DEFAULT 只给已有订单标明原解释格式，随后移除；旧恢复 Writer 和新 Writer 均显式写格式。
-- 本地迁移失败立即停止，不能 --force、重跑已成功的 ALTER 或删除已有表；恢复需按备份及之后新增数据单独判断。
SET NAMES utf8mb4;

ALTER TABLE `t_commerce_order`
    ADD COLUMN `context_format` VARCHAR(32) NOT NULL DEFAULT 'LEGACY_SCOPE' COMMENT 'TERM_PRICE使用entityNo和条款版本；LEGACY_SCOPE保留历史身份及原Scope读取，不回写旧订单。允许值：TERM_PRICE、LEGACY_SCOPE。',
    ADD COLUMN `preparation_command_id` BIGINT NULL COMMENT '同一Order的在途或已完成准备命令，跨幂等键重试必须复用，保证固定后不换OE。',
    ADD COLUMN `termination_requested_at` DATETIME(6) NULL COMMENT '取消或到期首次请求的UTC时间，串行阻止新副作用；不表示远端未知Intent已终止。',
    ADD COLUMN `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CCT唯一不可变OE编号，创建时经营国家二位代码加001–999，如AE001；不是数字代理ID，编号不参与路由。',
    MODIFY COLUMN `entity_source_type` VARCHAR(64) NULL COMMENT '订单经营主体来源于平台、Client 或 Local Partner 的闭合类型。允许值：PLATFORM、CLIENT、PARTNER。',
    MODIFY COLUMN `industry_code` VARCHAR(64) NULL COMMENT '订单上下文采用的平台行业目录代码。',
    MODIFY COLUMN `mcc_code` VARCHAR(4) NULL COMMENT '订单上下文采用的四位 Merchant Category Code。',
    MODIFY COLUMN `tax_amount` DECIMAL(24,8) NULL COMMENT '订单创建时冻结的消费者实收税额汇总金额。',
    MODIFY COLUMN `rounding_amount` DECIMAL(24,8) NULL COMMENT '订单金额计算为保持币种精度而冻结的舍入差额。',
    MODIFY COLUMN `total_amount` DECIMAL(24,8) NULL COMMENT '消费者最终应付的订单冻结总金额。',
    MODIFY COLUMN `operating_entity_id` BIGINT NULL COMMENT '仅 LEGACY_SCOPE 历史订单的原数字 OE 标识；新 TERM_PRICE 订单为空，不由 entity_no 转换或回填。';

ALTER TABLE `t_commerce_order` ALTER COLUMN `context_format` DROP DEFAULT;

ALTER TABLE `t_order_item`
    MODIFY COLUMN `line_tax_amount` DECIMAL(24,8) NULL COMMENT '仅旧 LEGACY_SCOPE 不可变 Item 的原行税额；新流程为空，按 Finance Tax Snapshot 读取。',
    MODIFY COLUMN `line_total_amount` DECIMAL(24,8) NULL COMMENT '仅旧 LEGACY_SCOPE 不可变 Item 的原最终行金额；新流程为空，不回写历史商品行。';
