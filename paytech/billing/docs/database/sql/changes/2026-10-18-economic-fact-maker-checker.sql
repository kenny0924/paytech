-- billing：经济事实 Maker-Checker 登记与 EconomicFactRecorded 发布（重构第 11a 步，design/04 §2）。审阅后手工执行。
-- 需求：requirements/20261017/architecture-V2骨架与模块搬迁/tasks/11a-billing-minimal.md 第 2 节第 1、6 项。
-- 前提：目标库为 mor_billing，且按第 07 步 billing-schema.sql 建表；t_economic_fact、t_economic_fact_line、t_compensation_event 均为空表
--       （第 07 步起只有读代码、无生产写入；t_compensation_event 为 Finance 旧补偿模型、从未被 Foundation 组件使用）。
-- 影响：
--   1. t_economic_fact：交易锚点 11 列改为可空（人工 / 成本调整没有订单锚点）；status 取值 DRAFT / APPROVED / SUPERSEDED；
--      新增 fact_version、channel_account_id、maker_id、checker_id、approved_time、published_at、update_time 与两个账簿主体访问索引。
--   2. t_economic_fact_line：新增 payer_role、payer_id、payee_role、payee_id；来源规则 6 列改为可空（人工 / 成本调整行没有来源规则）。
--   3. t_compensation_event：改为 paytech-compensation-event 现行模型（COMPENSATION_EVENT_GUIDE 第 3 节），删除 execution_type、
--      source_type、source_id、source_version、schema_version、payload_hash 与旧索引，新增 sub_event_type 与身份唯一键。
--   执行后三张表结构与 billing-schema.sql 逐列一致；应用与测试不执行本文件。
-- 回退（仅限尚无业务数据时）：按第 07 步 billing-schema.sql 中三张表的原定义 DROP 后重建。
SET NAMES utf8mb4;

ALTER TABLE `t_economic_fact`
  MODIFY COLUMN `order_id` BIGINT NULL COMMENT '与 Commerce Order Context 共同构成商业事实锚的订单稳定业务ID；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。',
  MODIFY COLUMN `order_context_id` BIGINT NULL COMMENT '与订单共同冻结且不可替换的 Commerce Order Context 稳定业务ID；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。',
  MODIFY COLUMN `context_format` VARCHAR(32) NULL COMMENT '新交易使用TERM_PRICE，历史数据保持LEGACY_SCOPE并读取原始冻结证据；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。允许值：TERM_PRICE、LEGACY_SCOPE。',
  MODIFY COLUMN `commercial_context_time` DATETIME(6) NULL COMMENT 'Commerce 冻结并用于精确查询 CCT 商业条款的 UTC 微秒时间；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。',
  MODIFY COLUMN `tax_snapshot_id` BIGINT NULL COMMENT '本次经济计算引用的本库不可变税务计算快照；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。',
  MODIFY COLUMN `payment_intent_id` BIGINT NULL COMMENT '该订单在 Payment 中的稳定支付意图业务ID；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。',
  MODIFY COLUMN `consumer_country_code` CHAR(2) NULL COMMENT '商业上下文冻结的 ISO 3166-1 alpha-2 消费者国家代码；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。取值来源：ISO 3166-1 alpha-2 国家代码；非固定枚举。',
  MODIFY COLUMN `payment_method` VARCHAR(64) NULL COMMENT 'Payment Attempt 冻结的实际支付方式目录代码；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。取值来源：Payment 支付方式目录；非固定枚举。',
  MODIFY COLUMN `industry_code` VARCHAR(64) NULL COMMENT 'Commerce Order Context 冻结的平台业务行业分类代码；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。取值来源：CCT 行业分类目录；非固定枚举。',
  MODIFY COLUMN `mcc_code` CHAR(4) NULL COMMENT 'Commerce Order Context 冻结且保留前导零的四位 MCC；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。取值来源：ISO 18245 商户类别码；非固定枚举。',
  MODIFY COLUMN `client_agreement_id` BIGINT NULL COMMENT 'CCT准确Client/OE合同的稳定ID；新交易成熟输入取自同一命中Client Price，不能使用当前合同覆盖原事实；交易类事实必填，MANUAL_ADJUSTMENT 与 COST_ADJUSTMENT 人工登记为空。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '经济事实登记状态：Maker 提交为 DRAFT，Checker 批准为 APPROVED，被更正事实替代后为 SUPERSEDED；更正以新事实与 supersedes_fact_id 表达。允许值：DRAFT、APPROVED、SUPERSEDED。',
  ADD COLUMN `fact_version` BIGINT NOT NULL DEFAULT 0 COMMENT '批准时定稿的事实版本：首次登记为 1，替代事实为被替代事实版本加 1，DRAFT 为 0；EconomicFactRecorded 的聚合版本。' AFTER `status`,
  ADD COLUMN `channel_account_id` BIGINT NULL COMMENT 'billing 在事实头给出的 Finance 渠道清算账户（reconciliation t_channel_account.id），COST_ADJUSTMENT 必填；不建立跨库关系。' AFTER `fact_version`,
  ADD COLUMN `maker_id` BIGINT NULL COMMENT '提交人工或成本调整事实的 Platform Actor 稳定业务ID；系统生成的交易事实为空。' AFTER `channel_account_id`,
  ADD COLUMN `checker_id` BIGINT NULL COMMENT '批准该事实且不同于编制人的 Platform Actor 稳定业务ID；未批准为空。' AFTER `maker_id`,
  ADD COLUMN `approved_time` DATETIME(6) NULL COMMENT 'Checker 批准的 UTC 微秒时间，也是事件发生时间；DRAFT 为空。' AFTER `checker_id`,
  ADD COLUMN `published_at` DATETIME(6) NULL COMMENT 'EconomicFactRecorded 首次收到 Broker 确认的 UTC 微秒时间；未发布为空。' AFTER `approved_time`,
  ADD COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  ADD KEY `idx_econ_book_oe` (`entity_no`, `currency`, `id`) COMMENT '支持访问路径：entity_no、currency、id',
  ADD KEY `idx_econ_book_fe` (`finance_entity_id`, `currency`, `id`) COMMENT '支持访问路径：finance_entity_id、currency、id',
  COMMENT = '按支付、退款、重复扣款、重复扣款补偿、争议、人工调整或实际成本调整冻结一笔经济事实、必要业务锚和 PAYMENT 的 Client 合同余额到期时间；内容一经提交不可修改，只有 Maker-Checker 状态、版本定稿与事件发布时间推进。';

ALTER TABLE `t_economic_fact_line`
  MODIFY COLUMN `party_type` VARCHAR(64) NULL COMMENT '该组件收入、费用、应付或权益归属的业务角色；人工与成本调整行等于收方角色。允许值：CLIENT、LOCAL_PARTNER、CHANNEL、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY、CONSUMER、PLATFORM。',
  ADD COLUMN `payer_role` VARCHAR(64) NULL COMMENT '人工与成本调整行的付方业务角色；规则计算产生的交易事实行为空。允许值：CLIENT、LOCAL_PARTNER、CHANNEL、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY、CONSUMER、PLATFORM。' AFTER `party_id`,
  ADD COLUMN `payer_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '按payer_role解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；与payer_role同时为空或非空。' AFTER `payer_role`,
  ADD COLUMN `payee_role` VARCHAR(64) NULL COMMENT '人工与成本调整行的收方业务角色；规则计算产生的交易事实行为空。允许值：CLIENT、LOCAL_PARTNER、CHANNEL、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY、CONSUMER、PLATFORM。' AFTER `payer_id`,
  ADD COLUMN `payee_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '按payee_role解释的稳定字符串；与payee_role同时为空或非空。' AFTER `payee_role`,
  MODIFY COLUMN `source_rule_type` VARCHAR(64) NULL COMMENT '提供本组件计算参数的规则族类别；规则计算产生的组件必填，人工与成本调整行为空。允许值：CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、TAX_RULE、CHANNEL_COST、CCT_PRICE_COMPONENT。',
  MODIFY COLUMN `source_rule_version_id` BIGINT NULL COMMENT '来源规则系统中实际用于计算的不可变版本ID；规则计算产生的组件必填，人工与成本调整行为空。',
  MODIFY COLUMN `source_component_code` VARCHAR(64) NULL COMMENT '来源规则内稳定识别该计算组成的代码；规则计算产生的组件必填，人工与成本调整行为空。取值来源：来源规则组件代码；非固定枚举。',
  MODIFY COLUMN `source_trigger_code` VARCHAR(64) NULL COMMENT '来源规则声明的业务动作或计费触发条件；规则计算产生的组件必填，人工与成本调整行为空。取值来源：来源规则触发代码；非固定枚举。',
  MODIFY COLUMN `source_calculation_base` VARCHAR(64) NULL COMMENT '来源规则声明的金额计算基数语义；规则计算产生的组件必填，人工与成本调整行为空。取值来源：来源规则计算基础目录；非固定枚举。',
  MODIFY COLUMN `calculation_method` VARCHAR(64) NULL COMMENT '把冻结参数应用于基数并得到原始金额的受控方法；规则计算产生的组件必填，人工与成本调整行为空。取值来源：Finance 经济计算方法目录；非固定枚举。',
  COMMENT = '逐项冻结 Client Fee、税、Partner Share、Platform Service Charge、Channel Cost、权益和舍入等实际计算输入、规则来源与结果；人工与成本调整行冻结付方、收方、金额与方向而不带来源规则；PLATFORM_SERVICE_CHARGE 的 party_id 必须逐值等于 Header finance_entity_id，并通过 transaction_economics_id 父链校验。';

ALTER TABLE `t_compensation_event`
  DROP INDEX `uk_finance_comp_event`,
  DROP INDEX `uk_finance_comp_source`,
  DROP INDEX `idx_fin_comp_claim`,
  DROP INDEX `idx_fin_comp_created`,
  DROP COLUMN `execution_type`,
  DROP COLUMN `source_type`,
  DROP COLUMN `source_id`,
  DROP COLUMN `source_version`,
  DROP COLUMN `schema_version`,
  DROP COLUMN `payload_hash`,
  MODIFY COLUMN `id` BIGINT NOT NULL COMMENT '本库补偿事件记录的 Snowflake 主键，不作为跨系统事件标识。' FIRST,
  MODIFY COLUMN `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Handler 读取本服务权威事实使用的稳定业务键，最长 128 个字符；去首尾空白、拒绝控制字符，大小写敏感。' AFTER `id`,
  MODIFY COLUMN `event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全应用唯一注册的 Handler 类型，最长 64 个字符，大小写敏感。取值来源：本服务 compensation-event-catalog.yaml；非固定枚举。' AFTER `event_key`,
  ADD COLUMN `sub_event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一 Handler 下的受控业务子类型；无业务子类型时为占位值 -；与 event_key、event_type 共同组成唯一身份。取值来源：本服务补偿子类型目录；非固定枚举。' AFTER `event_type`,
  MODIFY COLUMN `payload_json` VARCHAR(4096) NULL COMMENT '可选最小 JSON Object，通常为空；最多 4 KiB，不得保存可从本库恢复的上下文、敏感明文或凭据。' AFTER `sub_event_type`,
  MODIFY COLUMN `execute_status` VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '本地补偿任务执行状态；成功后删除记录。允许值：INIT、PROCESSING、ERROR、FAILED。' AFTER `payload_json`,
  MODIFY COLUMN `execute_count` INTEGER NOT NULL DEFAULT 0 COMMENT '已开始的 Handler 尝试次数，非负，Claim 时增加。' AFTER `execute_status`,
  MODIFY COLUMN `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务下次允许 Claim 的 UTC 微秒时间，由 Handler 退避策略更新。' AFTER `execute_count`,
  MODIFY COLUMN `lock_owner` VARCHAR(64) NULL COMMENT '当前持有数据库 Claim Lease 的服务实例标识；未被认领时为空。' AFTER `next_execute_at`,
  MODIFY COLUMN `lock_until` DATETIME(6) NULL COMMENT 'Claim Lease 到期并可被其他实例接管的 UTC 微秒时间；无活动 Lease 时为空。' AFTER `lock_owner`,
  MODIFY COLUMN `last_error_code` VARCHAR(64) NULL COMMENT '最近一次执行失败的稳定错误分类代码；尚无错误时为空。取值来源：本服务补偿执行错误代码目录；非固定枚举。' AFTER `lock_until`,
  MODIFY COLUMN `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次执行失败的脱敏摘要，最长 1000 字符，禁止保存敏感明文。' AFTER `last_error_code`,
  MODIFY COLUMN `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim 和 Lease 使用的正乐观锁版本；登记初始值固定为 1，仅当前 Lease 持有者可回写执行结果。' AFTER `last_error_summary`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿记录与业务事实原子登记的 UTC 微秒时间。' AFTER `lock_version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿记录最近更新的 UTC 微秒时间，由写入口维护。' AFTER `create_time`,
  ADD UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '保证业务唯一性：event_key、event_type、sub_event_type',
  ADD KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '支持访问路径：execute_status、next_execute_at、id',
  ADD KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '支持访问路径：execute_status、lock_until、id',
  COMMENT = '与本库业务事实在同一事务登记的本地可靠执行记录（paytech-compensation-event 现行模型，COMPENSATION_EVENT_GUIDE 第 3 节）；成功后删除，FAILED 保留并可受控重开';
