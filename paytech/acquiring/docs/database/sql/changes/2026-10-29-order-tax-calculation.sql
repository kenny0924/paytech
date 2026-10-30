-- 订单接入 tax 真实算税：新表 t_order_tax_calculation（按 revision 追加的订单税务计算）；t_commerce_order 加 amount_status；
-- t_order_context 的 finance_tax_snapshot_id / hash 改可空并加 initial_tax_calculation_id；t_consumer_contract 加 tax_calculation_id，
-- 唯一键 uk_consumer_contract 由 (order_id) 改为 (order_id, tax_calculation_id)。
-- 需求：requirements/20261029/tax-结账算税与事件登记/requirement.md R4、R5；technique.md 第 8.2、10.2、11.1 节，S-04、S-05。
-- 目标库：acquiring（mor_acquiring）。设计源：paytech/acquiring/docs/database/acquiring.pdma.json。
-- 偏离 technique 8.2：t_order_tax_calculation 增加 expires_at（FINAL 计算的报价有效期，支付门禁不再回查 tax，见 9.1）。
-- 执行顺序：tax 先上线可被调用；再执行本文件并同批部署写入新列 / 新表的 acquiring 镜像（amount_status NOT NULL 无默认值，
--   旧镜像插入订单不写该列会失败，新旧镜像不能与本文件交错）。
-- 存量：t_commerce_order 现有行按旧推导回填 amount_status（total_amount 非空为 FINAL，否则 ESTIMATED）；t_order_context 保留原零税
--   快照值，initial_tax_calculation_id 为 NULL；t_consumer_contract 现有行 tax_calculation_id 为 NULL（每单一份，新唯一键不冲突）。
--   不为存量订单补算税额或补建计算行（requirement 第 5 节）。
-- 执行后核对：
--   SHOW CREATE TABLE t_order_tax_calculation;
--   SELECT COUNT(*) FROM t_commerce_order WHERE amount_status IS NULL;  -- 0
--   SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
--     AND TABLE_NAME = 't_order_context' AND COLUMN_NAME IN ('finance_tax_snapshot_id','finance_tax_snapshot_hash','initial_tax_calculation_id');
--   SHOW INDEX FROM t_consumer_contract WHERE Key_name = 'uk_consumer_contract';  -- order_id、tax_calculation_id 两列
-- 回退：2026-10-29-order-tax-calculation-rollback.sql（须先回退 acquiring 镜像；有新单写入新列时回退会被约束阻断）。
SET NAMES utf8mb4;

CREATE TABLE `t_order_tax_calculation` (
  `order_tax_calculation_id` BIGINT NOT NULL COMMENT '订单一次税务计算记录的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `revision` INT NOT NULL COMMENT '订单内税务计算的追加序号，建单为 1，每次消费者资料提交重算加 1；同订单最大 revision 即当前有效报价，也是发给 tax 的 sourceRevision。',
  `tax_calculation_id` BIGINT NOT NULL COMMENT 'tax 返回的不可变税务计算标识，不建立跨库外键。',
  `calculation_stage` VARCHAR(32) NOT NULL COMMENT '计算阶段：PROVISIONAL 为建单按 Client 声明国家的暂估，不可支付；FINAL 为消费者提交资料后按账单国家的最终计算。允许值：PROVISIONAL、FINAL。',
  `tax_status` VARCHAR(32) NOT NULL COMMENT 'tax 计算结果状态：READY 已按规则计算，BLOCKED 无法正确计税、订单不可支付，NOT_MANAGED 纳税主体不由平台计税、税额取 Client 自报值。允许值：READY、BLOCKED、NOT_MANAGED。',
  `basis_country_code` CHAR(2) NOT NULL COMMENT '本次计算采用的国家证据：PROVISIONAL 为 Client 声明的消费者国家，FINAL 为消费者账单国家；ISO 3166-1 alpha-2。',
  `tax_amount` DECIMAL(24,8) NULL COMMENT '本次计算的消费者实收税额汇总，订单币种主单位；BLOCKED 时为 NULL。',
  `rounding_amount` DECIMAL(24,8) NULL COMMENT '本次计算的头部舍入差额，订单币种主单位；逐行取整时为 0，BLOCKED 时为 NULL。',
  `total_amount` DECIMAL(24,8) NULL COMMENT '本次计算的消费者应付总额，等于订单净额加税额加舍入差额；BLOCKED 时为 NULL。',
  `block_reason_code` VARCHAR(64) NULL COMMENT 'BLOCKED 时 tax 返回的第一条阻断原因代码，其他状态为 NULL。取值来源：tax TaxBlockReason；非固定枚举。',
  `expires_at` DATETIME(6) NULL COMMENT 'FINAL 计算的报价有效截止 UTC 时刻，过期后不可支付；PROVISIONAL 与无有效期的结果为 NULL。',
  `lines_json` JSON NULL COMMENT '逐行税额 JSON 数组，元素为 clientItemId、netAmount、taxAmount、totalAmount（十进制字符串），与订单商品行一一对应；BLOCKED 时为 NULL。',
  `components_json` JSON NULL COMMENT '按辖区与注册汇总的税组件 JSON 数组，元素为 jurisdictionCode、registrationId、taxSubjectPartyId、taxAmount（十进制字符串），供支付成功事件直接携带；NOT_MANAGED 为空数组，BLOCKED 时为 NULL。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_tax_calculation_id`),
  UNIQUE KEY `uk_order_tax_calc` (`order_id`, `revision`) COMMENT '唯一索引：order_id、revision',
  UNIQUE KEY `uk_order_tax_calc_ref` (`tax_calculation_id`) COMMENT '唯一索引：tax_calculation_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='订单税务计算按 revision 追加、不可变：建单暂估为 revision 1，消费者提交资料后每次重算追加一行；最大 revision 即当前有效报价，订单金额只在消费者接受前随新 revision 更新；支付只能基于最新且 FINAL、READY 或 NOT_MANAGED、未过期的计算。';

-- 先以可空加列，回填后再改 NOT NULL，避免存量行在 ADD COLUMN 时取隐式默认值。
ALTER TABLE `t_commerce_order`
  ADD COLUMN `amount_status` VARCHAR(32) NULL COMMENT '订单当前有效报价的金额状态：ESTIMATED 为建单暂估或消费者尚未完成重算，FINAL 为可支付的最终金额，BLOCKED 为无法正确计税、税额与总额为空且不可支付。允许值：ESTIMATED、FINAL、BLOCKED。' AFTER `total_amount`;

UPDATE `t_commerce_order` SET `amount_status` = CASE WHEN `total_amount` IS NULL THEN 'ESTIMATED' ELSE 'FINAL' END
WHERE `amount_status` IS NULL;

ALTER TABLE `t_commerce_order`
  MODIFY COLUMN `tax_amount` DECIMAL(24,8) NULL COMMENT '当前有效报价的消费者实收税额汇总，只在消费者接受前随新的税务计算 revision 更新；BLOCKED 时为空。',
  MODIFY COLUMN `rounding_amount` DECIMAL(24,8) NULL COMMENT '当前有效报价为保持币种精度的舍入差额，随税务计算 revision 更新；BLOCKED 时为空。',
  MODIFY COLUMN `total_amount` DECIMAL(24,8) NULL COMMENT '当前有效报价的消费者应付总额，只在消费者接受前随税务计算 revision 更新；BLOCKED 时为空。',
  MODIFY COLUMN `amount_status` VARCHAR(32) NOT NULL COMMENT '订单当前有效报价的金额状态：ESTIMATED 为建单暂估或消费者尚未完成重算，FINAL 为可支付的最终金额，BLOCKED 为无法正确计税、税额与总额为空且不可支付。允许值：ESTIMATED、FINAL、BLOCKED。',
  COMMENT = '基础创建时固定商品净额/币种/期限与OE；税额、舍入额与总额为当前有效报价，随 t_order_tax_calculation 新 revision 在消费者接受前更新，接受后不再变化；不延长到期或跨OE恢复。';

ALTER TABLE `t_order_context`
  MODIFY COLUMN `finance_tax_snapshot_id` BIGINT NULL COMMENT '历史订单的旧 Finance 税务快照标识（零税 Mock）；2026-10-29 起新订单为 NULL，税务计算见 initial_tax_calculation_id 与 t_order_tax_calculation，本列后续删除。',
  MODIFY COLUMN `finance_tax_snapshot_hash` CHAR(64) NULL COMMENT '历史订单的旧 Finance 税务快照摘要；2026-10-29 起新订单为 NULL，本列后续删除。',
  ADD COLUMN `initial_tax_calculation_id` BIGINT NULL COMMENT '建单时取得的 tax 暂估计算标识，即 t_order_tax_calculation revision 1 的 tax_calculation_id；历史订单为 NULL，不建立跨库外键。' AFTER `finance_tax_snapshot_hash`;

ALTER TABLE `t_consumer_contract`
  ADD COLUMN `tax_calculation_id` BIGINT NULL COMMENT '合同渲染所依据的 tax 计算标识；金额随重算变化时重新渲染一份合同并绑定新计算，消费者接受的合同必须绑定当前有效计算；历史订单为 NULL。' AFTER `order_legal_id`,
  DROP INDEX `uk_consumer_contract`,
  ADD UNIQUE KEY `uk_consumer_contract` (`order_id`, `tax_calculation_id`) COMMENT '唯一索引：order_id、tax_calculation_id',
  COMMENT = '呈现给消费者的不可变合同文件事实；文件标识、内容哈希、Locale 与呈现时间共同封存；同一订单每个税务计算至多一份，金额变化时追加新合同，最新一份为当前合同。';
