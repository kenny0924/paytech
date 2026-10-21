-- commercial 开通与定价模型调整（一期）增量 DDL：mor_commercial，以 root 人工执行，执行一次；在 migration.sql 之前执行。
-- 依据：technique.md §3；目标定义与 paytech/commercial/docs/database/sql/commercial-schema.sql（由 commercial.pdma.json 生成）逐列一致。
-- 只 ADD / MODIFY，不 DROP：新增 2 张表；t_product_capability、t_product_fee_event 增 change_version；t_product_fee_event 增挂接两列；
-- t_agreement_capability 增 require_3ds；t_commercial_term_plan 增 owner_type、template_name；t_commercial_term_version 增
-- source_template_version_id、signature_exempt，agreement_id 改为可空（标准价目模板版本不冻结合同），status 增 CLIENT_SIGNED（注释）；
-- t_commercial_term_price 增 source_template_price_id；其余为列 / 表注释改写。新列全部带默认值或可空，旧应用版本可继续读写。
-- 回滚（只在新代码未写入新语义数据前）：DROP TABLE t_term_version_acceptance, t_product_capability_dimension；
--   ALTER TABLE ... DROP COLUMN 本文件新增的列；t_commercial_term_version.agreement_id 改回 NOT NULL 前须确认没有模板版本。
SET NAMES utf8mb4;

ALTER TABLE `t_agreement_capability`
  ADD COLUMN `require_3ds` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 表示该开通项要求 3DS 认证，0 表示不要求；执行归收单与支付。' AFTER `rolling_reserve_days`,
  COMMENT='合同在准确OE下的开通项与保证金条款；uk_agreement_capability(agreement_id,entity_no,capability_code)；require_3ds 为开通项的 3DS 要求；所属产品必须已在合同订阅；capability_code引用时须为ACTIVE目录项；CLIENT合同entity_no等于合同entity_no，PARTNER合同须在合同覆盖OE内；状态ENABLED/SUSPENDED/DISABLED，有效区间半开；保证金比例与天数成组为空或非空，执行归account/risk；应用校验，不创建 CHECK/FK；所属产品必须已在 agreement_product 订阅；capability_code 引用时目录项须为 ACTIVE。；CLIENT 合同的 entity_no 必须等于 agreement.entity_no；PARTNER 合同的 entity_no 必须在 agreement_operating_entity 内。；CHECK (effective_to IS NULL OR effective_from < effective_to)，解析时要求 status=ENABLED 且 effective_from <= contextAsOfTime < effective_to。；rolling_reserve_rate 与 rolling_reserve_days 成组为空或成组非空；比例取值 0 至 1，保证金执行归 account / risk。';

ALTER TABLE `t_agreement_capability_limit`
  MODIFY COLUMN `limit_code` VARCHAR(64) NOT NULL COMMENT '开通项接受条件的维度代码，须在该可开通项可配维度内；同维度多行按 OR，异维度按 AND，未写维度不限。允许值：CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_REGION、ISSUER_COUNTRY、CURRENCY、CONSUMER_COUNTRY、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR。',
  MODIFY COLUMN `limit_value` VARCHAR(128) NOT NULL COMMENT '该维度的一个规范化取值：币种为 ISO 4217，国家为 ISO 3166-1 alpha-2，其他为大写稳定代码。',
  COMMENT='开通项接受条件，一值一行；uk_agreement_capability_limit(agreement_capability_id,limit_code,limit_value)；limit_code为可用于开通的PricingDimension维度代码（CURRENCY必填），同维度多行按OR、异维度按AND，未写维度不限；3DS要求在agreement_capability.require_3ds；金额限额归风控决策，不在此表；随开通项冻结，不单独审计；limit_code 须在该可开通项 product_capability_dimension 内且可用于开通；必填维度（CURRENCY）必须有值；CURRENCY 用 ISO 4217，国家类用 ISO 3166-1 alpha-2，其他为大写稳定代码。';

ALTER TABLE `t_product_capability`
  MODIFY COLUMN `capability_code` VARCHAR(64) NOT NULL COMMENT '全局唯一且稳定的可开通项代码，如 CARD、TOKEN_STORAGE、PAYOUT_LOCAL。取值来源：产品发版维护的能力目录；非固定枚举。',
  MODIFY COLUMN `capability_kind` VARCHAR(32) NOT NULL COMMENT '可开通项所属的能力种类；PAYMENT_METHOD 绑定 Payment Method，FEATURE 为功能项（如存卡）；创建后不可修改。允许值：PAYMENT_METHOD、FEATURE、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE。',
  MODIFY COLUMN `dimension_code` VARCHAR(64) NULL COMMENT '已废弃，恒为 NULL；细分维度改为合同开通项接受条件。允许值：CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_REGION、ISSUER_COUNTRY、THREE_DS_RESULT、CURRENCY、CONSUMER_COUNTRY、TRANSACTION_TYPE、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR、DISPUTE_STAGE。',
  MODIFY COLUMN `dimension_value` VARCHAR(128) NULL COMMENT '已废弃，恒为 NULL；细分维度改为合同开通项接受条件。',
  ADD COLUMN `change_version` BIGINT NOT NULL DEFAULT 1 COMMENT '运营维护目录行的乐观锁版本，初始 1，每次写入加一。' AFTER `status`,
  COMMENT='L1可开通项目录；uk_product_capability_code(capability_code)；capability_code全局唯一稳定，代码与种类创建后不可改；PAYMENT_METHOD种类payment_method_code必填，其他种类（含FEATURE）为空；dimension_code与dimension_value已废弃恒为NULL；运营维护，写入以change_version做CAS，BCR聚合根PRODUCT_CATALOG；capability_kind=PAYMENT_METHOD 要求 payment_method_code IS NOT NULL，其他种类要求 payment_method_code IS NULL。；dimension_code 与 dimension_value 已废弃，恒为 NULL；capability_code 与 capability_kind 创建后不可修改，RETIRED 阻止新增开通，已开通不受影响。';

ALTER TABLE `t_product_fee_event`
  ADD COLUMN `capability_kind` VARCHAR(32) NULL COMMENT '该计费事件挂接的开通项种类；为空表示无需开通的基础产品事件。允许值：PAYMENT_METHOD、FEATURE、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE。' AFTER `billing_fact_type`,
  ADD COLUMN `capability_code` VARCHAR(64) NULL COMMENT 'capability_kind=FEATURE 时精确挂接的功能开通项代码，其他种类为空（任一该种类开通项）。取值来源：t_product_capability.capability_code 中 FEATURE 种类可开通项；非固定枚举。' AFTER `capability_kind`,
  ADD COLUMN `change_version` BIGINT NOT NULL DEFAULT 1 COMMENT '运营维护目录行的乐观锁版本，初始 1，每次写入加一。' AFTER `capability_code`,
  COMMENT='L2计费事件目录；uk_product_fee_event(fee_event_code,term_type)；fee_event_code为组件trigger_code值域，term_type为方案term_type值域，一个事件可在哪类条款下收费一值一行；capability_kind为空表示无需开通的基础产品事件，FEATURE时capability_code指向同产品功能开通项，其他种类capability_code为空；运营维护挂接，写入以change_version做CAS，BCR聚合根PRODUCT_CATALOG；fee_event_code 是 commercial_term_component.trigger_code 的值域，term_type 是 commercial_term_plan.term_type 的值域；均为值引用，不建关系。；capability_kind=FEATURE 时 capability_code 必须是同产品下的 FEATURE 可开通项；其他种类与空种类 capability_code 为空。';

CREATE TABLE IF NOT EXISTS `t_product_capability_dimension` (
  `capability_dimension_id` BIGINT NOT NULL COMMENT '可开通项可配维度行的内部唯一标识。',
  `capability_code` VARCHAR(64) NOT NULL COMMENT '可配维度所属的可开通项代码。取值来源：t_product_capability.capability_code 可开通项目录；非固定枚举。',
  `dimension_code` VARCHAR(64) NOT NULL COMMENT '合同开通该项时可配置接受条件的维度代码，须可用于开通。允许值：CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_REGION、ISSUER_COUNTRY、CURRENCY、CONSUMER_COUNTRY、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR。',
  `required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 表示合同开通该项时必须给出该维度条件，0 表示可选。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`capability_dimension_id`),
  UNIQUE KEY `uk_capability_dimension` (`capability_code`, `dimension_code`) COMMENT 'Unique index for product_capability_dimension on capability_code, dimension_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='可开通项的可配维度；uk_capability_dimension(capability_code,dimension_code)；dimension_code须为可用于开通接受条件的PricingDimension；required=1表示合同开通时必须给出该维度条件；随可开通项整体替换，审计归PRODUCT_CATALOG；随可开通项整体替换；dimension_code 须为可用于开通接受条件的维度。';

ALTER TABLE `t_commercial_term_plan`
  ADD COLUMN `owner_type` VARCHAR(32) NOT NULL DEFAULT 'CLIENT' COMMENT '方案 Owner 类型；CLIENT 属于 CLIENT 合同，LOCAL_PARTNER 版本冻结 PARTNER 合同，PLATFORM_TEMPLATE 为按 OE 维护的标准价目模板，不参与运行时取价。允许值：CLIENT、LOCAL_PARTNER、PLATFORM_TEMPLATE。' AFTER `term_type`,
  ADD COLUMN `template_name` VARCHAR(128) NULL COMMENT '平台标准价目模板的显示名，如标准价 · AE001；非模板方案为空。' AFTER `owner_type`,
  COMMENT='Client Pricing、Partner Share 或 Platform Service Charge 的稳定方案根；uk_comm_term_code(term_type,plan_code)、uk_comm_term_agreement(agreement_id,term_type)、uk_comm_term_partner(local_partner_id,term_type)；owner_type为CLIENT、LOCAL_PARTNER或PLATFORM_TEMPLATE；PLATFORM_TEMPLATE为平台标准价目模板，agreement_id/client_id/local_partner_id为空、entity_no非空、template_name非空，不参与运行时取价；CLIENT_PRICING时agreement_id/entity_no/client_id非空且由同一CLIENT合同派生，local_partner_id为空；Partner两类时仅local_partner_id非空，agreement_id/entity_no/client_id为空；不限制Client全局只有一个Plan；Owner XOR：term_type=CLIENT_PRICING 要求 client_id IS NOT NULL AND local_partner_id IS NULL；term_type IN (PARTNER_SHARE,PLATFORM_SERVICE_CHARGE) 要求 client_id IS NULL AND local_partner_id IS NOT NULL。；term_type、client_id 与 local_partner_id 创建后不可修改；MVP 每份 Client Agreement 一个 Client Pricing Plan，每个 Local Partner 可分别拥有一个 Partner Share Plan 和一个 PLATFORM_SERVICE_CHARGE Plan，通过 local_partner_id + term_type 复合唯一。；CLIENT_PRICING 的 agreement_id/entity_no/client_id 必须与同一 Client Agreement 一致，local_partner_id 为空；Partner 两类只填 local_partner_id，Version 冻结其 Partner Agreement。；PARTNER_BUY_RATE 属于 Partner 合同，Owner 规则同 PARTNER_SHARE；结构同 CLIENT_PRICING，按画像 × 币种定价。；owner_type=PLATFORM_TEMPLATE 时 agreement_id、client_id、local_partner_id 为空，entity_no 与 template_name 非空；价格命中、费率卡与 Checkout 解析排除模板方案。';

ALTER TABLE `t_commercial_term_version`
  MODIFY COLUMN `agreement_id` BIGINT NULL COMMENT '版本冻结的商业合同；标准价目模板版本为空。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '版本生命周期状态；合同方案非免签版本 APPROVED 后须登记客户签署进入 CLIENT_SIGNED 才能排期。允许值：DRAFT、PENDING_APPROVAL、APPROVED、CLIENT_SIGNED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED。',
  ADD COLUMN `source_template_version_id` BIGINT NULL COMMENT '基于标准价目模板生成时的来源模板版本标识；手工或复制创建为空。' AFTER `fee_tax_treatment`,
  ADD COLUMN `signature_exempt` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 表示迁移前已排期或生效的存量版本免客户签署，0 表示须签署。' AFTER `source_template_version_id`,
  COMMENT='商业条款版本；uk_comm_term_ver(term_plan_id,version_no)、uk_comm_term_active(term_plan_id,active_slot)；仅 ACTIVE slot=1；模板版本 agreement_id 为空；合同方案非免签版本须经 CLIENT_SIGNED 才能排期；Client Pricing 引用同 Client 的 CLIENT Agreement，Partner Share 与 Platform Service Charge 引用同 Local Partner 的 PARTNER Agreement；同一交易命中的两类 Partner Term 必须属于同一 Agreement；Agreement 覆盖全部 Price 的准确 OE，计划有效区间不得超出合同；APPROVED 后 Owner、Agreement、Version No、effective_from、舍入方式、Price、Component 与 Band 冻结；CHECK ((status=''ACTIVE'' AND active_slot=1) OR (status<>''ACTIVE'' AND active_slot IS NULL))；CHECK (effective_to IS NULL OR effective_from < effective_to)；同一 Root 已批准或生效的有效区间不得重叠。；CLIENT_PRICING 必须引用同一 Client 的 CLIENT Agreement；PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 必须引用同一 Local Partner 的 PARTNER Agreement，同一交易命中的两类 Partner Term 必须属于同一 Agreement。；Agreement 必须覆盖全部 Price 的准确 Operating Entity，Version 有效区间不得超出 Agreement；APPROVED 后 Version、Price、Component 与 Band 业务字段不可修改。；本期 pricing_model 只接受 BLENDED；激活前 Version 下每个开通项至少命中一条 Price。；合同方案非免签版本 APPROVED 后须登记 term_version_acceptance 进入 CLIENT_SIGNED 才能排期；模板版本 agreement_id 为空、无签署要求；signature_exempt=1 仅用于迁移前已排期或生效的存量版本。';

ALTER TABLE `t_commercial_term_price`
  ADD COLUMN `source_template_price_id` BIGINT NULL COMMENT '基于模板生成或补齐时的来源模板价格标识，用于破标比较；手工维护为空。' AFTER `cutoff_second_of_day`;

CREATE TABLE IF NOT EXISTS `t_term_version_acceptance` (
  `version_acceptance_id` BIGINT NOT NULL COMMENT '价格版本客户签署记录的内部唯一标识。',
  `term_version_id` BIGINT NOT NULL COMMENT '签署的价格版本；一期一个版本一次签署。',
  `party_id` BIGINT NOT NULL COMMENT '签署方 Party：Client 方案为合同 CLIENT 角色 Party，Partner 方案为合同 LOCAL_PARTNER 角色 Party。',
  `acceptance_method` VARCHAR(32) NOT NULL COMMENT '价格协议签署方式，同合同签署。允许值：E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE。',
  `accepted_at` DATETIME(6) NOT NULL COMMENT '签署成立的 UTC 微秒时间。',
  `acceptance_reference` VARCHAR(128) NOT NULL COMMENT '全局唯一的签署编号；同编号同内容重放返回原结果。',
  `file_id` BIGINT NOT NULL COMMENT '已签署价格附件的 Platform Support 文件逻辑引用，与 content_hash 成组冻结。',
  `content_hash` CHAR(64) NOT NULL COMMENT '价格附件 SHA-256 小写 64 位 Hex。',
  `recorded_by` VARCHAR(64) NOT NULL COMMENT '登记签署证据的 Operations Account 标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`version_acceptance_id`),
  UNIQUE KEY `uk_version_acceptance` (`term_version_id`) COMMENT 'Unique index for term_version_acceptance on term_version_id',
  UNIQUE KEY `uk_version_acceptance_ref` (`acceptance_reference`) COMMENT 'Unique index for term_version_acceptance on acceptance_reference'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='价格版本的客户签署记录；uk_version_acceptance(term_version_id)、uk_version_acceptance_ref(acceptance_reference)；一期一个版本一次签署；party_id为合同CLIENT（Client方案）或LOCAL_PARTNER（Partner方案）角色Party；file_id+content_hash为已签署价格附件；登记后版本APPROVED→CLIENT_SIGNED；追加式证据，随方案BCR快照审计；版本须为 APPROVED 的合同方案版本；party_id 须为合同 CLIENT（Client 方案）或 LOCAL_PARTNER（Partner 方案）角色 Party；登记后不可修改。';

