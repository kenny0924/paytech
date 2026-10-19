-- channel-connector：接收渠道成本配置四表（需求 requirements/20261019/billing-计费事实模型重构与渠道成本迁出，技术方案第 2.3 节）。审阅后手工执行。
-- 目标库：mor_channel_connector。前提：本库尚无这四张表；mor_billing 中同名四表为 0 行（无需搬数据，由 db/billing/ddl.sql DROP）。
-- 影响：CREATE 4 张；结构取自执行前 billing-schema.sql 的四表，差异只有：t_channel_cost_scope.mid_id 改为引用本库 t_channel_mid.id（应用层校验存在且
-- 归属同一 Channel 与 entity_no），注释中的 Finance 改为本库。执行后 mor_channel_connector 为 17 张表，与 channel-connector-schema.sql、
-- channel-connector.pdma.json 逐列一致。执行顺序：先本文件，再 db/billing/ddl.sql（顺序无数据依赖，按发布顺序 channel-connector 在前）。
-- 回退：DROP TABLE t_channel_cost_component, t_channel_cost_scope, t_channel_cost_plan_ver, t_channel_cost_plan;
SET NAMES utf8mb4;

CREATE TABLE `t_channel_cost_plan` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本方案 Snowflake 主键。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment Channel（本库 t_payment_channel.id）。',
  `plan_code` VARCHAR(64) NOT NULL COMMENT '跨版本稳定识别渠道成本方案的业务代码。取值来源：channel-connector 渠道成本方案代码；非固定枚举。',
  `current_ver_id` BIGINT NULL COMMENT '当前发布的本库渠道成本方案版本。',
  `status` VARCHAR(64) NOT NULL COMMENT '渠道成本方案稳定身份的当前生命周期状态。允许值：DRAFT、ACTIVE、INACTIVE。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '切换当前成本版本和状态时使用的乐观锁版本号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_plan` (`channel_id`) COMMENT '保证业务唯一性：channel_id',
  UNIQUE KEY `uk_channel_cost_code` (`plan_code`) COMMENT '保证业务唯一性：plan_code',
  UNIQUE KEY `uk_channel_cost_current` (`current_ver_id`) COMMENT '保证业务唯一性：current_ver_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存每个 Payment Channel 的预计渠道成本规则稳定身份和当前版本指针；billing 经命中接口取组件并冻结证据，本库不算金额。';

CREATE TABLE `t_channel_cost_plan_ver` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本方案版本 Snowflake 主键。',
  `plan_id` BIGINT NOT NULL COMMENT '该版本所属的本库渠道成本方案稳定身份。',
  `cost_ver` INT NOT NULL COMMENT '同一渠道成本方案下严格递增的业务版本号。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '该成本版本开始参与预计成本匹配的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '该成本版本停止参与预计成本匹配的 UTC 微秒时间，长期有效时为空。',
  `content_hash` CHAR(64) NOT NULL COMMENT '覆盖版本、全部适用范围和成本组件的不可变内容摘要。',
  `status` VARCHAR(64) NOT NULL COMMENT '该成本版本写入时冻结的审批和发布状态。允许值：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、INACTIVE。',
  `maker_id` BIGINT NOT NULL COMMENT '创建该成本版本的 Platform Actor 稳定业务ID。',
  `checker_id` BIGINT NULL COMMENT '复核该成本版本的另一 Platform Actor 稳定业务ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_ver` (`plan_id`, `cost_ver`) COMMENT '保证业务唯一性：plan_id、cost_ver'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存成本规则发布批次的生效期、内容摘要及双人审批状态，当前版本仅由方案根指针确定；APPROVED 版本到达生效时间后由到期切换 Job 原子切换为当前版本。';

CREATE TABLE `t_channel_cost_scope` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本适用范围 Snowflake 主键。',
  `cost_ver_id` BIGINT NOT NULL COMMENT '该适用范围所属的本库渠道成本方案版本。',
  `scope_no` INT NOT NULL COMMENT '同一成本版本内稳定且唯一的适用范围序号。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment Channel MID（本库 t_channel_mid.id）；写入时校验存在、归属方案的 Channel 且 entity_no 一致。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该成本适用范围限定的 Operating Entity 稳定业务ID，须等于 MID 归属的 OE。',
  `channel_agreement_ref` VARCHAR(128) NOT NULL COMMENT '本 MID 与经营主体范围实际采用的 Channel 合同或费率文件稳定引用。',
  `country_code` CHAR(2) NOT NULL COMMENT '该成本适用范围限定的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2 国家代码；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT '该成本适用范围限定的 ISO 4217 三位币种代码，也是成本金额的币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '该成本适用范围限定的 Payment 支付方式目录代码。取值来源：Payment 支付方式目录；非固定枚举。',
  `action_type` VARCHAR(64) NOT NULL COMMENT '该成本适用范围限定的支付、退款或争议动作类别。取值来源：Payment 动作类型目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_scope_no` (`cost_ver_id`, `scope_no`) COMMENT '保证业务唯一性：cost_ver_id、scope_no',
  UNIQUE KEY `uk_channel_cost_scope` (`cost_ver_id`, `mid_id`, `entity_no`, `country_code`, `currency`, `payment_method_code`, `action_type`) COMMENT '保证业务唯一性：cost_ver_id、mid_id、entity_no、country_code、currency、payment_method_code、action_type',
  KEY `idx_channel_cost_match` (`mid_id`, `entity_no`, `country_code`, `currency`) COMMENT '支持访问路径：mid_id、entity_no、country_code、currency'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='冻结成本版本对 MID、经营主体、国家、币种、支付方式和动作的确定性匹配范围。';

CREATE TABLE `t_channel_cost_component` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本组件 Snowflake 主键。',
  `scope_id` BIGINT NOT NULL COMMENT '该成本组件所属的本库渠道成本适用范围。',
  `component_code` VARCHAR(64) NOT NULL COMMENT '同一适用范围内稳定识别成本组成的业务代码。取值来源：channel-connector 渠道成本组件代码；非固定枚举。',
  `component_type` VARCHAR(64) NOT NULL COMMENT '说明该组件采用的渠道收费语义。允许值：PROCESSING_FEE、CROSS_BORDER_FEE、REFUND_FEE、DISPUTE_FEE。',
  `calculation_base` VARCHAR(64) NOT NULL COMMENT '该成本组件声明的金额计算基数语义。允许值：TRANSACTION_AMOUNT。',
  `rate_value` DECIMAL(18,10) NULL COMMENT '零至一之间的渠道成本十进制费率。',
  `fixed_amount` DECIMAL(24,8) NULL COMMENT '渠道成本规则声明的非负固定收费金额。',
  `currency` CHAR(3) NULL COMMENT '固定金额使用的 ISO 4217 三位币种代码，须等于范围币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `min_amount` DECIMAL(24,8) NULL COMMENT '该成本组件计算结果允许采用的金额下限。',
  `max_amount` DECIMAL(24,8) NULL COMMENT '该成本组件计算结果允许采用的金额上限。',
  `rounding_mode` VARCHAR(64) NOT NULL COMMENT '把渠道成本原始金额归整到币种精度时采用的固定方式。允许值：HALF_UP。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_component` (`scope_id`, `component_code`) COMMENT '保证业务唯一性：scope_id、component_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='冻结渠道成本适用范围下的百分比、固定费、跨境费、退款费或争议费参数；金额由 billing 按证据计算。';
