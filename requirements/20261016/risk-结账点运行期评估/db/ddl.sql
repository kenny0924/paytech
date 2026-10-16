-- 需求：requirements/20261016/risk-结账点运行期评估（阶段 3 待执行文件，未在任何环境执行）。目标库：Risk（mor_risk）。
-- 性质：增量执行文件；阶段 4 以 PDManer 模型生成的完整 Schema（risk-compliance-schema.sql，表数仍为 85）对照核对后定稿；不得重复执行。
-- 前置：t_checkout_input_snapshot 为旧结构且 0 行（执行前 SELECT COUNT(*) 确认，非 0 立即停止）。
-- 回滚：DROP 本表后按旧 Schema（risk-compliance-schema.sql 本需求前版本 1034 行起）重建；新表中已写入的结账快照不可恢复。

DROP TABLE `t_checkout_input_snapshot`;

CREATE TABLE `t_checkout_input_snapshot` (
  `snapshot_id` BIGINT NOT NULL COMMENT '结账风控输入快照的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '正式决策评估的稳定业务标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 预分配订单的稳定业务标识；结账风控点的业务身份。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 客户的稳定业务标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT 为本次结账确定的 Operating Entity 编号。取值来源：CCT Operating Entity；非固定枚举。',
  `entity_source_type` VARCHAR(64) NOT NULL COMMENT '经营主体来源的稳定业务代码。允许值：PLATFORM、CLIENT、PARTNER。',
  `local_partner_id` BIGINT NULL COMMENT 'PARTNER 来源主体所属 Local Partner 的稳定业务标识；其他来源为空。',
  `consumer_country_code` CHAR(2) NOT NULL COMMENT '订单上的 ISO 3166-1 alpha-2 消费者国家。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 alpha-3 订单币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `merchandise_amount` DECIMAL(24,8) NOT NULL COMMENT '优惠前商品总额，订单币种主单位，精度为 24 位、小数位为 8 位。',
  `discount_amount` DECIMAL(24,8) NOT NULL COMMENT '商品优惠总额，订单币种主单位，精度为 24 位、小数位为 8 位。',
  `net_amount` DECIMAL(24,8) NOT NULL COMMENT '税前商品净额，订单币种主单位，精度为 24 位、小数位为 8 位。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT '行业代码的稳定业务代码。取值来源：CCT 行业目录；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT 'CCT 确定且保留前导零的四位 MCC。取值来源：CCT 批准 MCC 目录；非固定枚举。',
  `line_count` INT NOT NULL COMMENT '订单商品明细行数的正整数值。',
  `stable_input_hash` CHAR(64) NOT NULL COMMENT '业务身份与全部输入字段的稳定输入哈希，SHA-256 小写十六进制值；重放内容比较使用。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uk_checkout_eval` (`evaluation_id`) COMMENT '保证业务唯一性：evaluation_id',
  UNIQUE KEY `uk_checkout_order` (`order_id`) COMMENT '保证业务唯一性：order_id',
  KEY `idx_checkout_client` (`client_id`, `create_time`) COMMENT '支持访问路径：client_id、create_time',
  KEY `idx_checkout_entity` (`entity_no`, `create_time`) COMMENT '支持访问路径：entity_no、create_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一次 CHECKOUT_PREPARE 正式评估使用的 Commerce 订单与商业事实输入及稳定输入摘要';
