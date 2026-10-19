-- 仅供人工审阅并初始化 local-environment 的空本地 Schema；应用和测试绝不自动执行，已有库勿重复建表或重置。
-- 2026-10-19 第三轮：全部 20 张商务表与生成 Schema 一致（含六张产品/开通表与 t_commercial_term_price.currency）。
-- 完整表定义逐字摘自 paytech/commercial/docs/database/sql/commercial-schema.sql；t_consumed_event 逐字摘自
-- paytech/foundation/paytech-messaging/src/main/resources/schema/messaging-consumed-event.sql（每库一份，design/01 §3）。
-- commercial_app 为仓库隔离的本地测试账号，密码与 application-mock.yml 的默认值及 local-environment/.env.example 一致；真实/共享环境不得使用本文件。
CREATE DATABASE IF NOT EXISTS mor_commercial CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'commercial_app'@'%' IDENTIFIED BY 'local-commercial-change-me-2026';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_commercial.* TO 'commercial_app'@'%';
USE mor_commercial;
SET NAMES utf8mb4;

CREATE TABLE `t_agreement` (
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `agreement_no` VARCHAR(64) NOT NULL COMMENT '可在业务操作中识别合同的唯一编号。',
  `agreement_name` VARCHAR(255) NOT NULL COMMENT '合同对外展示和业务识别使用的名称。',
  `agreement_type` VARCHAR(64) NOT NULL COMMENT '区分 Client 合同与 Local Partner 合同的类型代码。允许值：CLIENT、PARTNER。',
  `business_mode` VARCHAR(16) NULL COMMENT 'agreement_type=CLIENT 时必填的合同业务模式，PARTNER 时为空；创建后不可修改。允许值：MOR、PSP。',
  `fund_mode` VARCHAR(32) NULL COMMENT 'agreement_type=CLIENT 时必填的资金模式，PARTNER 时为空；MOR 只允许 SETTLE_THROUGH_PLATFORM，FEES_ONLY 只允许 OE 来源 CLIENT，本期只接受 SETTLE_THROUGH_PLATFORM。允许值：SETTLE_THROUGH_PLATFORM、FEES_ONLY。',
  `pricing_authority` VARCHAR(16) NULL COMMENT 'agreement_type=CLIENT 时必填的定价权归属；PARTNER 表示价目由推荐伙伴提出、平台运营录入审批，Partner 门户只读；本期只接受 PLATFORM。允许值：PLATFORM、PARTNER。',
  `referral_partner_id` BIGINT NULL COMMENT '推荐该合同的 Local Partner，client-master 跨库引用只存值；签署时冻结，pricing_authority=PARTNER 时必须非空。',
  `client_id` BIGINT NULL COMMENT 'agreement_type=CLIENT 时必须非空的本库 Client 标识；PARTNER 时必须为空。',
  `local_partner_id` BIGINT NULL COMMENT 'agreement_type=PARTNER 时必须非空的本库 Local Partner 标识；CLIENT 时必须为空。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `agreement_format` VARCHAR(32) NOT NULL COMMENT '区分新同Party共签与旧逐角色签署合同，创建后不可修改。允许值：MERGED_PARTY、LEGACY_ROLE_SIGNATURE。',
  `status` VARCHAR(64) NOT NULL COMMENT '该独立合同当前所处的法律生命周期状态。允许值：DRAFT、PENDING_SIGNATURE、SIGNED、ACTIVE、EXPIRED、TERMINATED、CANCELLED。',
  `effective_from` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `signed_time` DATETIME(6) NULL COMMENT '合同完成必要签署的 UTC 微秒精度时刻。',
  `activated_time` DATETIME(6) NULL COMMENT '合同正式进入 ACTIVE 状态的 UTC 微秒精度时刻。',
  `create_by` BIGINT NOT NULL COMMENT '创建不可变审计快照的正式 Human 或 System Actor 标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_id`),
  UNIQUE KEY `uk_agreement_no` (`agreement_no`) COMMENT 'Unique index for agreement on agreement_no',
  KEY `idx_agreement_client_time` (`client_id`, `effective_from`, `effective_to`) COMMENT 'Access index for agreement on client_id, effective_from, effective_to',
  KEY `idx_agreement_partner_time` (`local_partner_id`, `effective_from`, `effective_to`) COMMENT 'Access index for agreement on local_partner_id, effective_from, effective_to',
  KEY `idx_agreement_referral` (`referral_partner_id`) COMMENT 'Access index for agreement on referral_partner_id',
  KEY `idx_agreement_entity_no` (`entity_no`) COMMENT 'Access index for agreement on entity_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同；uk_agreement_no(agreement_no)；agreement_type IN (CLIENT,PARTNER)，CLIENT 要求 client_id 非空且 local_partner_id 为空，PARTNER 反之；一行是一份独立法律合同，不使用 Root/Version、Active Slot 或业务聚合 Hash；有效区间合法，历史解析必须按签署、激活和有效区间而非当前状态；新CLIENT合同准确绑定一个Client/OE，PARTNER的entity_no为空并保留多OE关联；同Client/OE适用区间不冲突，agreement_format区分新共签与历史逐角色签署；business_mode为MOR/PSP，CLIENT合同必填、PARTNER合同为空，创建后不可改；fund_mode与pricing_authority为CLIENT合同必填、PARTNER为空，MOR只允许SETTLE_THROUGH_PLATFORM，FEES_ONLY只允许OE来源CLIENT；referral_partner_id为跨库Local Partner值引用，pricing_authority=PARTNER时必填，签署时冻结；Owner XOR：CLIENT 合同 client_id 非空且 local_partner_id 为空，PARTNER 反之；签约 Owner 为对应 Party。；状态时间：DRAFT/PENDING_SIGNATURE/CANCELLED 两时间为空；SIGNED 仅 signed_time；ACTIVE/EXPIRED 两者非空；TERMINATED 的 activated_time 可空；signed_time<=activated_time 且 effective_from<=activated_time；EXPIRED/TERMINATED 须填 effective_to。；有效期半开区间 [effective_from,effective_to)。；历史 As-Of 按 signed/activated/effective 区间选取，不按当前 status 排除历史行；零条或多条候选 Fail Closed。；状态推进与替换按 Owner、类型、OE 稳定顺序锁定相关 Root 并复查区间冲突；替换在同一事务收窄旧合同 effective_to 并写 BCR/事件，不回溯收窄、不覆盖旧内容。；CLIENT 合同 business_mode、fund_mode、pricing_authority 非空且创建后不可改，PARTNER 三者为空；MOR 只允许 SETTLE_THROUGH_PLATFORM，FEES_ONLY 只允许 OE 来源 CLIENT；pricing_authority=PARTNER 须有 referral_partner_id（client-master 跨库值引用，签署时冻结）；本期只接受 SETTLE_THROUGH_PLATFORM 与 PLATFORM。';

CREATE TABLE `t_agreement_party` (
  `agreement_party_id` BIGINT NOT NULL COMMENT '合同内一条Party角色记录的稳定标识，同Party允许多角色。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `party_profile_id` BIGINT NOT NULL COMMENT '申请或合同冻结引用的 Party 资料版本标识。',
  `party_role` VARCHAR(64) NOT NULL COMMENT '法律Party在合同中承担的业务角色，不是Party个人或机构类型。允许值：CLIENT、LOCAL_PARTNER、PLATFORM_SIGNATORY、OPERATING_ENTITY。',
  `acceptance_method` VARCHAR(64) NULL COMMENT '签约方完成合同接受时采用的受控方式代码。允许值：E_SIGNATURE、CLICK_ACCEPTANCE、MANUAL_SIGNATURE。',
  `accepted_by_actor_id` BIGINT NULL COMMENT '代表签约方完成合同接受的外部 Actor 稳定标识。',
  `accepted_time` DATETIME(6) NULL COMMENT '接受事实正式成立的 UTC 微秒精度时刻。',
  `evidence_document_id` BIGINT NULL COMMENT '合同接受方式矩阵要求的必填文件引用证据标识。',
  `acceptance_reference` VARCHAR(128) NULL COMMENT '平台生成的全局唯一、稳定合同接受事件编号。',
  `acceptance_ref_slot` TINYINT(1) NULL COMMENT '未签时全部NULL；组内最小主键行签后为1，其他角色行NULL；禁止同引用跨组复用。允许值：1 或 NULL。取值规则：同合同Party最小角色主键行签后为1，其他角色及未签时为NULL。',
  `acceptance_recorded_by` BIGINT NULL COMMENT '登记最终签署证据的可信Actor，区别于实际签署人。',
  `acceptance_recorded_at` DATETIME(6) NULL COMMENT '首次登记最终签署结果的UTC时间，区别于实际签署时间。',
  `create_by` BIGINT NOT NULL COMMENT '创建不可变审计快照的正式 Human 或 System Actor 标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。创建时等于create_time；待签时冻结身份，最终签署结果仅允许一次整组原子补齐并更新此时间。',
  PRIMARY KEY (`agreement_party_id`),
  UNIQUE KEY `uk_agreement_party_role` (`agreement_id`, `party_id`, `party_role`) COMMENT 'Unique index for agreement_party on agreement_id, party_id, party_role',
  UNIQUE KEY `uk_agreement_party_slot` (`agreement_id`, `party_id`, `acceptance_ref_slot`) COMMENT 'Unique index for agreement_party on agreement_id, party_id, acceptance_ref_slot',
  UNIQUE KEY `uk_acceptance_ref_slot` (`acceptance_reference`, `acceptance_ref_slot`) COMMENT 'Unique index for agreement_party on acceptance_reference, acceptance_ref_slot',
  KEY `idx_agreement_party` (`party_id`) COMMENT 'Access index for agreement_party on party_id',
  KEY `idx_agreement_profile` (`party_profile_id`) COMMENT 'Access index for agreement_party on party_profile_id',
  KEY `idx_agreement_party_b059d04e` (`evidence_document_id`) COMMENT 'Access index for agreement_party on evidence_document_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同参与方角色及同Party共享的最终签署结果；uk_agreement_party_role(agreement_id,party_id,party_role)、uk_agreement_party_slot(agreement_id,party_id,acceptance_ref_slot)、uk_acceptance_ref_slot(acceptance_reference,acceptance_ref_slot)；待签时冻结身份/角色/资料版本；同合同Party的全部角色一次原子签署；组内最小主键行签后slot=1，其余NULL，跨合同/Party不得复用参与方级签署引用；party_profile_id 必须属于 party_id 且提交签署时已封存；CLIENT/LOCAL_PARTNER 签约方必须分别匹配合同 Owner，PLATFORM_SIGNATORY 为平台授权签约主体，OPERATING_ENTITY 必须属于合同覆盖经营主体。；包含 PLATFORM_SERVICE_CHARGE 的 PARTNER Agreement 必须恰好一个 PLATFORM_SIGNATORY；零个或多个平台签约方时 CCT-I13/CCT-I14 均 Fail Closed。；进入 PENDING_SIGNATURE 后签约方不得修改，同一合同 Party 的全部角色必须一次原子写入相同的最终签署结果；仅最小行占 acceptance_ref_slot=1。；Acceptance Method Matrix：CLICK_ACCEPTANCE 的 evidence_document_id 必须指向唯一 SIGNING_DOCUMENT；全部 CLICK_ACCEPTANCE 的合同进入 SIGNED 无需 EXECUTED_DOCUMENT；E_SIGNATURE 和 MANUAL_SIGNATURE 必须指向 EXECUTED_DOCUMENT，任一 E_SIGNATURE 或 MANUAL_SIGNATURE 存在时进入 SIGNED 前必须恰有一份 EXECUTED_DOCUMENT。；accepted_by_actor_id 是实际在线接受 Actor，create_by 是登记证据 Actor；CLICK_ACCEPTANCE 必须填写 accepted_by_actor_id，E_SIGNATURE/MANUAL_SIGNATURE 按受控签署记录填写，二者不得混同。；最终接受证据仅允许从待签补齐一次，补齐后不得修改或删除。';

CREATE TABLE `t_agreement_document_ref` (
  `agreement_document_id` BIGINT NOT NULL COMMENT '合同文件引用记录的内部唯一标识。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `document_type` VARCHAR(64) NOT NULL COMMENT '文档用途或法律类别的受控业务代码。允许值：SIGNING_DOCUMENT、ANNEX、EXECUTED_DOCUMENT、TERMINATION_NOTICE。',
  `file_id` BIGINT NOT NULL COMMENT 'Platform Support 合同文件的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键。',
  `content_hash` CHAR(64) NOT NULL COMMENT '输入为 Platform Support 返回的合同文件 SHA-256，与 file_id 成组冻结。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。INSERT 时必须等于 create_time；合同文件引用冻结后不得更新。',
  PRIMARY KEY (`agreement_document_id`),
  UNIQUE KEY `uk_agreement_doc_file` (`agreement_id`, `document_type`, `file_id`) COMMENT 'Unique index for agreement_document_ref on agreement_id, document_type, file_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同文件引用；uk_agreement_doc_file(agreement_id,document_type,file_id)；document_type IN (SIGNING_DOCUMENT,ANNEX,EXECUTED_DOCUMENT,TERMINATION_NOTICE)；文件 Hash 为 SHA-256 小写 64 字符十六进制文本，签署文件唯一、附件可多份、最终执行文件至多一份；每份合同必须且只能有一份 SIGNING_DOCUMENT，可有多份 ANNEX，EXECUTED_DOCUMENT 至多一份；文件与内容哈希成组冻结。';

CREATE TABLE `t_agreement_operating_entity` (
  `agreement_operating_entity_id` BIGINT NOT NULL COMMENT 'Partner合同覆盖OE的稳定关联ID；历史Client关联只读保留。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_operating_entity_id`),
  UNIQUE KEY `uk_agreement_entity` (`agreement_id`, `entity_no`) COMMENT 'Unique index for agreement_operating_entity on agreement_id, entity_no',
  KEY `idx_agreement_oe_entity` (`entity_no`) COMMENT 'Access index for agreement_operating_entity on entity_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Partner合同的多OE关联；uk_agreement_entity(agreement_id,entity_no)；新CLIENT合同直接引用OE，不新写此关系；新PARTNER合同提交签署前至少一行并冻结；旧CLIENT关联由历史Reader保留，不回写订单';

CREATE TABLE `t_agreement_product` (
  `agreement_product_id` BIGINT NOT NULL COMMENT '合同订阅产品行的内部唯一标识。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '合同订阅的产品代码；产品 business_mode_scope 必须包含合同 business_mode，基座产品不写行。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_product_id`),
  UNIQUE KEY `uk_agreement_product` (`agreement_id`, `product_code`) COMMENT 'Unique index for agreement_product on agreement_id, product_code',
  KEY `idx_agreement_product_code` (`product_code`) COMMENT 'Access index for agreement_product on product_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同订阅的可选产品模块；uk_agreement_product(agreement_id,product_code)；产品的business_mode_scope必须包含合同business_mode；base_product=1的基座产品无需订阅、不写行；应用校验，不创建 CHECK/FK；business_mode_scope=BOTH 兼容 MOR 与 PSP 合同；产品 RETIRED 后禁止新订阅，历史订阅可读。';

CREATE TABLE `t_agreement_capability` (
  `agreement_capability_id` BIGINT NOT NULL COMMENT '合同在准确 OE 下开通项行的内部唯一标识。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '开通项适用的准确 OE；CLIENT 合同须等于合同 entity_no，PARTNER 合同须在合同覆盖 OE 内。',
  `capability_code` VARCHAR(64) NOT NULL COMMENT '开通的可开通项代码；引用时目录项须为 ACTIVE。取值来源：t_product_capability.capability_code 可开通项目录；非固定枚举。',
  `status` VARCHAR(32) NOT NULL COMMENT '开通项状态；SUSPENDED 为风控或合规临时停用且可恢复，DISABLED 为运营关闭。允许值：ENABLED、SUSPENDED、DISABLED。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '开通生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '开通生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `rolling_reserve_rate` DECIMAL(18,10) NULL COMMENT '滚动保证金按交易金额计提的小数比例，取值范围为 0 至 1；无保证金时为空。',
  `rolling_reserve_days` INT NULL COMMENT '滚动保证金的持有天数；与比例成组为空或非空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_capability_id`),
  UNIQUE KEY `uk_agreement_capability` (`agreement_id`, `entity_no`, `capability_code`) COMMENT 'Unique index for agreement_capability on agreement_id, entity_no, capability_code',
  KEY `idx_agreement_capability_lookup` (`entity_no`, `capability_code`, `status`) COMMENT 'Access index for agreement_capability on entity_no, capability_code, status',
  KEY `idx_agreement_capability_code` (`capability_code`) COMMENT 'Access index for agreement_capability on capability_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同在准确OE下的开通项与保证金条款；uk_agreement_capability(agreement_id,entity_no,capability_code)；所属产品必须已在合同订阅；capability_code引用时须为ACTIVE目录项；CLIENT合同entity_no等于合同entity_no，PARTNER合同须在合同覆盖OE内；状态ENABLED/SUSPENDED/DISABLED，有效区间半开；保证金比例与天数成组为空或非空，执行归account/risk；应用校验，不创建 CHECK/FK；所属产品必须已在 agreement_product 订阅；capability_code 引用时目录项须为 ACTIVE。；CLIENT 合同的 entity_no 必须等于 agreement.entity_no；PARTNER 合同的 entity_no 必须在 agreement_operating_entity 内。；CHECK (effective_to IS NULL OR effective_from < effective_to)，解析时要求 status=ENABLED 且 effective_from <= contextAsOfTime < effective_to。；rolling_reserve_rate 与 rolling_reserve_days 成组为空或成组非空；比例取值 0 至 1，保证金执行归 account / risk。';

CREATE TABLE `t_agreement_capability_limit` (
  `agreement_capability_limit_id` BIGINT NOT NULL COMMENT '开通项约束行的内部唯一标识。',
  `agreement_capability_id` BIGINT NOT NULL COMMENT '合同在准确 OE 下开通项行的内部唯一标识。',
  `limit_code` VARCHAR(64) NOT NULL COMMENT '开通项约束的类别代码；同码多行按 OR，异码按 AND。允许值：CURRENCY、CONSUMER_COUNTRY、REQUIRE_3DS。',
  `limit_value` VARCHAR(128) NOT NULL COMMENT '约束的规范化值：ISO 4217 币种、ISO 3166-1 alpha-2 国家或固定值 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_capability_limit_id`),
  UNIQUE KEY `uk_agreement_capability_limit` (`agreement_capability_id`, `limit_code`, `limit_value`) COMMENT 'Unique index for agreement_capability_limit on agreement_capability_id, limit_code, limit_value'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='开通项约束，一值一行；uk_agreement_capability_limit(agreement_capability_id,limit_code,limit_value)；同码多行按OR、异码按AND；金额限额归风控决策，不在此表；随开通项冻结，不单独审计；CURRENCY 用 ISO 4217，CONSUMER_COUNTRY 用 ISO 3166-1 alpha-2，REQUIRE_3DS 固定为 1。';

CREATE TABLE `t_product` (
  `product_id` BIGINT NOT NULL COMMENT '产品目录行的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '可售产品模块的稳定代码，创建后不可修改。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `product_name` VARCHAR(128) NOT NULL COMMENT '供 Operations 识别产品的展示名称。',
  `business_mode_scope` VARCHAR(16) NOT NULL COMMENT '该产品允许被哪种合同业务模式订阅。允许值：MOR_ONLY、PSP_ONLY、BOTH。',
  `base_product` TINYINT(1) NOT NULL COMMENT '1 表示基座产品，所有实例必装且合同无需订阅；0 表示可选模块；不允许 NULL。',
  `status` VARCHAR(64) NOT NULL COMMENT '产品目录行的生命周期状态；RETIRED 阻止新合同订阅，历史订阅继续可读。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_code` (`product_code`) COMMENT 'Unique index for product on product_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='L0产品，即可售模块；uk_product_code(product_code)；product_code创建后不可改；状态DRAFT/ACTIVE/RETIRED，RETIRED阻止新合同订阅、历史订阅可读；随产品发版维护，不接入BCR；base_product 只允许 0 或 1，基座产品对所有合同默认生效。';

CREATE TABLE `t_product_capability` (
  `product_capability_id` BIGINT NOT NULL COMMENT '可开通项目录行的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '可开通项所属产品的稳定代码。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `capability_code` VARCHAR(64) NOT NULL COMMENT '全局唯一且稳定的可开通项代码，如 CARD_VISA、PAYOUT_LOCAL。取值来源：产品发版维护的能力目录；非固定枚举。',
  `capability_name` VARCHAR(128) NOT NULL COMMENT '供 Operations 识别可开通项的展示名称。',
  `capability_kind` VARCHAR(32) NOT NULL COMMENT '可开通项所属的能力种类，决定其是否绑定 Payment Method。允许值：PAYMENT_METHOD、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE。',
  `payment_method_code` VARCHAR(64) NULL COMMENT 'capability_kind=PAYMENT_METHOD 时必填的 Payment Method 稳定代码，其他种类为空。取值来源：Payment 拥有的 Payment Method 稳定代码目录；非固定枚举。',
  `dimension_code` VARCHAR(64) NULL COMMENT '把开通项细分到画像维度的单一条件维度，与 dimension_value 成组为空或非空。允许值：CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_REGION、ISSUER_COUNTRY、THREE_DS_RESULT、CURRENCY、CONSUMER_COUNTRY、TRANSACTION_TYPE、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR、DISPUTE_STAGE。',
  `dimension_value` VARCHAR(128) NULL COMMENT '开通项细分维度的单个规范化值，如 AMEX。',
  `status` VARCHAR(64) NOT NULL COMMENT '可开通项目录行的生命周期状态；合同引用时须为 ACTIVE。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`product_capability_id`),
  UNIQUE KEY `uk_product_capability_code` (`capability_code`) COMMENT 'Unique index for product_capability on capability_code',
  KEY `idx_product_capability_product` (`product_code`, `status`) COMMENT 'Access index for product_capability on product_code, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='L1可开通项目录；uk_product_capability_code(capability_code)；capability_code全局唯一稳定；PAYMENT_METHOD种类payment_method_code必填，其他种类为空；dimension_code与dimension_value成组为空或非空；随产品发版维护，不接入BCR；capability_kind=PAYMENT_METHOD 要求 payment_method_code IS NOT NULL，其他种类要求 payment_method_code IS NULL。；dimension_code 与 dimension_value 成组为空或成组非空。';

CREATE TABLE `t_product_fee_event` (
  `product_fee_event_id` BIGINT NOT NULL COMMENT '计费事件目录行的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '计费事件所属产品的稳定代码。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `fee_event_code` VARCHAR(64) NOT NULL COMMENT '触发收费的业务动作代码，是组件 trigger_code 的值域。取值来源：产品发版维护的计费事件目录；非固定枚举。',
  `term_type` VARCHAR(64) NOT NULL COMMENT '该计费事件可在哪类商业条款下收费；一值一行，本表登记即为方案 term_type 值域。取值来源：本表登记；非固定枚举。',
  `billing_fact_type` VARCHAR(64) NOT NULL COMMENT '该计费事件对应的 billing 事实类型代码。取值来源：billing 事实类型目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`product_fee_event_id`),
  UNIQUE KEY `uk_product_fee_event` (`fee_event_code`, `term_type`) COMMENT 'Unique index for product_fee_event on fee_event_code, term_type',
  KEY `idx_product_fee_event_product` (`product_code`) COMMENT 'Access index for product_fee_event on product_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='L2计费事件目录；uk_product_fee_event(fee_event_code,term_type)；fee_event_code为组件trigger_code值域，term_type为方案term_type值域，一个事件可在哪类条款下收费一值一行；随产品发版维护，不接入BCR；fee_event_code 是 commercial_term_component.trigger_code 的值域，term_type 是 commercial_term_plan.term_type 的值域；均为值引用，不建关系。';

CREATE TABLE `t_pricing_profile` (
  `pricing_profile_id` BIGINT NOT NULL COMMENT '定价画像聚合在本库内部使用的唯一标识。',
  `pricing_profile_code` VARCHAR(64) NOT NULL COMMENT 'CCT 拥有的全局唯一稳定代码；创建后不可修改、不可回收复用，跨聚合和跨系统关联必须使用该代码。',
  `profile_name` VARCHAR(128) NOT NULL COMMENT '供 Operations 识别定价画像的展示名称。',
  `profile_kind` VARCHAR(32) NOT NULL COMMENT '定价画像描述的定价对象种类；ACTIVE 后冻结，决定 payment_method_code 是否必填及可用条件维度。允许值：PAYMENT_METHOD、PAYOUT_RAIL、FX_PAIR、DISPUTE、PERIODIC。',
  `payment_method_code` VARCHAR(64) NULL COMMENT 'profile_kind=PAYMENT_METHOD 时必填并精确绑定的 Payment Method 稳定代码，其他种类为空；由 Payment 目录拥有，CCT 不建立 Payment Method 主表。取值来源：Payment 拥有的 Payment Method 稳定代码目录；非固定枚举。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`pricing_profile_id`),
  UNIQUE KEY `uk_pricing_profile_code` (`pricing_profile_code`) COMMENT 'Unique index for pricing_profile on pricing_profile_code',
  KEY `idx_pricing_profile_lookup` (`status`, `payment_method_code`, `pricing_profile_code`) COMMENT 'Access index for pricing_profile on status, payment_method_code, pricing_profile_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='全局可复用的定价画像；uk_pricing_profile_code(pricing_profile_code)；状态 DRAFT/ACTIVE/RETIRED；profile_kind=PAYMENT_METHOD 时精确绑定一个 Payment Method 稳定代码，其他种类为空；ACTIVE 后 Code、种类、Payment Method 与 Condition 永久冻结，条件变化创建新 Profile/Code；CCT 不建立 Payment Method 主表；状态只允许 DRAFT、ACTIVE、RETIRED；DRAFT 可在 Profile Root BCR/CAS 下编辑，ACTIVE 后 pricing_profile_code、payment_method_code 与全部 Condition 永久冻结。；Condition 变化必须创建新 Profile 与新 Code；RETIRED 只阻止新 Commercial Term Version 引用，历史引用继续可读，Code 不得回收复用。；profile_kind=PAYMENT_METHOD 要求 payment_method_code IS NOT NULL，其他种类要求 payment_method_code IS NULL；CCT 不建立 Payment Method 主表，也不复制名称、图标、路由能力或 Channel 配置。；条件维度须与种类相容：出款类只用 PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY，FX 只用 CURRENCY_PAIR。';

CREATE TABLE `t_pricing_profile_cond` (
  `profile_condition_id` BIGINT NOT NULL COMMENT '定价画像条件行的内部唯一标识。',
  `pricing_profile_id` BIGINT NOT NULL COMMENT '所属定价画像的本库内部标识；Condition 是聚合内部 Child，因此不使用 Code 代替父内关联。',
  `dimension_code` VARCHAR(64) NOT NULL COMMENT '受版本化应用/API 合同约束的定价维度代码；不建立 Dimension Definition 表。允许值：CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_REGION、ISSUER_COUNTRY、THREE_DS_RESULT、CURRENCY、CONSUMER_COUNTRY、TRANSACTION_TYPE、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR、DISPUTE_STAGE。',
  `condition_value` VARCHAR(128) NOT NULL COMMENT '该维度的一个规范化离散值；同维度多行按 OR，不同维度按 AND，不保存 CSV 或通配符。取值来源：对应 Dimension 的事实 Owner 规范化值域；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`profile_condition_id`),
  UNIQUE KEY `uk_pricing_profile_cond_value` (`pricing_profile_id`, `dimension_code`, `condition_value`) COMMENT 'Unique index for pricing_profile_cond on pricing_profile_id, dimension_code, condition_value',
  KEY `idx_pricing_cond_lookup` (`dimension_code`, `condition_value`, `pricing_profile_id`) COMMENT 'Access index for pricing_profile_cond on dimension_code, condition_value, pricing_profile_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='定价画像的离散等值条件；uk_pricing_profile_cond_value(pricing_profile_id,dimension_code,condition_value)；同维度多行按 OR、不同维度按 AND；缺少维度表示不限制，不保存 *、CSV、Operator、Range、Dimension Definition 或 Condition Value 子表；物理名 t_pricing_profile_cond 用于满足 32 字符门禁；每行保存一个 dimension_code + condition_value 离散等值条件；同维度多行按 OR，不同维度按 AND，缺少维度表示不限制。；不保存 CSV、*、Operator、Range，不建立 Dimension Definition、Profile Version 或 Condition Value 子表；维度和值由版本化应用/API 合同校验。；Condition 是 Profile 聚合内部 Child，使用 pricing_profile_id 关联，并由 Profile Root BCR/CAS 与冻结规则保护。';

CREATE TABLE `t_commercial_term_plan` (
  `term_plan_id` BIGINT NOT NULL COMMENT 'Client Pricing、Partner Share 或 Platform Service Charge 方案根的内部唯一标识。',
  `plan_code` VARCHAR(64) NOT NULL COMMENT '定价、分润或平台服务费计划的唯一稳定业务编号。',
  `term_type` VARCHAR(64) NOT NULL COMMENT '方案所属的商业条款类型；必须在计费事件目录登记，CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 继续有效；PARTNER_BUY_RATE 为 Partner 合同下按画像 × 币种的买入价，结构同 CLIENT_PRICING。取值来源：t_product_fee_event.term_type 登记目录，示例 CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、PARTNER_BUY_RATE；非固定枚举。',
  `agreement_id` BIGINT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `client_id` BIGINT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `local_partner_id` BIGINT NULL COMMENT 'Local Partner 角色的内部唯一标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`term_plan_id`),
  UNIQUE KEY `uk_comm_term_code` (`term_type`, `plan_code`) COMMENT 'Unique index for commercial_term_plan on term_type, plan_code',
  UNIQUE KEY `uk_comm_term_agreement` (`agreement_id`, `term_type`) COMMENT 'Unique index for commercial_term_plan on agreement_id, term_type',
  UNIQUE KEY `uk_comm_term_partner` (`local_partner_id`, `term_type`) COMMENT 'Unique index for commercial_term_plan on local_partner_id, term_type',
  KEY `idx_commercial_term_pla_27ac6d7b` (`entity_no`) COMMENT 'Access index for commercial_term_plan on entity_no',
  KEY `idx_commercial_term_pla_098917d9` (`client_id`) COMMENT 'Access index for commercial_term_plan on client_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client Pricing、Partner Share 或 Platform Service Charge 的稳定方案根；uk_comm_term_code(term_type,plan_code)、uk_comm_term_agreement(agreement_id,term_type)、uk_comm_term_partner(local_partner_id,term_type)；CLIENT_PRICING时agreement_id/entity_no/client_id非空且由同一CLIENT合同派生，local_partner_id为空；Partner两类时仅local_partner_id非空，agreement_id/entity_no/client_id为空；不限制Client全局只有一个Plan；Owner XOR：term_type=CLIENT_PRICING 要求 client_id IS NOT NULL AND local_partner_id IS NULL；term_type IN (PARTNER_SHARE,PLATFORM_SERVICE_CHARGE) 要求 client_id IS NULL AND local_partner_id IS NOT NULL。；term_type、client_id 与 local_partner_id 创建后不可修改；MVP 每份 Client Agreement 一个 Client Pricing Plan，每个 Local Partner 可分别拥有一个 Partner Share Plan 和一个 PLATFORM_SERVICE_CHARGE Plan，通过 local_partner_id + term_type 复合唯一。；CLIENT_PRICING 的 agreement_id/entity_no/client_id 必须与同一 Client Agreement 一致，local_partner_id 为空；Partner 两类只填 local_partner_id，Version 冻结其 Partner Agreement。；PARTNER_BUY_RATE 属于 Partner 合同，Owner 规则同 PARTNER_SHARE；结构同 CLIENT_PRICING，按画像 × 币种定价。';

CREATE TABLE `t_commercial_term_version` (
  `term_version_id` BIGINT NOT NULL COMMENT '商业条款不可变业务版本的内部唯一标识。',
  `term_plan_id` BIGINT NOT NULL COMMENT 'Client Pricing、Partner Share 或 Platform Service Charge 方案根的内部唯一标识。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `version_no` BIGINT NOT NULL COMMENT '同一业务聚合内部不可变版本的序号。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、PENDING_APPROVAL、APPROVED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED。',
  `active_slot` TINYINT(1) NULL COMMENT '以 1 或 NULL 实现条件稀疏唯一约束的技术槽位，具体占槽含义由表级规则定义。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `rounding_mode` VARCHAR(64) NOT NULL COMMENT '金额计算采用的确定性十进制舍入规则。允许值：HALF_UP。',
  `pricing_model` VARCHAR(32) NOT NULL COMMENT '该版本采用的定价模型；本期应用只接受 BLENDED。允许值：BLENDED、INTERCHANGE_PLUS_PLUS。',
  `fee_tax_treatment` VARCHAR(16) NOT NULL COMMENT '该版本费用金额是否已包含税额。允许值：INCLUSIVE、EXCLUSIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`term_version_id`),
  UNIQUE KEY `uk_comm_term_ver` (`term_plan_id`, `version_no`) COMMENT 'Unique index for commercial_term_version on term_plan_id, version_no',
  UNIQUE KEY `uk_comm_term_active` (`term_plan_id`, `active_slot`) COMMENT 'Unique index for commercial_term_version on term_plan_id, active_slot',
  KEY `idx_commercial_term_ver_c99980b6` (`term_plan_id`, `status`, `effective_from`, `effective_to`) COMMENT 'Access index for commercial_term_version on term_plan_id, status, effective_from, effective_to',
  KEY `idx_comm_term_agreement` (`agreement_id`) COMMENT 'Access index for commercial_term_version on agreement_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='商业条款版本；uk_comm_term_ver(term_plan_id,version_no)、uk_comm_term_active(term_plan_id,active_slot)；仅 ACTIVE slot=1；Client Pricing 引用同 Client 的 CLIENT Agreement，Partner Share 与 Platform Service Charge 引用同 Local Partner 的 PARTNER Agreement；同一交易命中的两类 Partner Term 必须属于同一 Agreement；Agreement 覆盖全部 Price 的准确 OE，计划有效区间不得超出合同；APPROVED 后 Owner、Agreement、Version No、effective_from、舍入方式、Price、Component 与 Band 冻结；CHECK ((status=''ACTIVE'' AND active_slot=1) OR (status<>''ACTIVE'' AND active_slot IS NULL))；CHECK (effective_to IS NULL OR effective_from < effective_to)；同一 Root 已批准或生效的有效区间不得重叠。；CLIENT_PRICING 必须引用同一 Client 的 CLIENT Agreement；PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 必须引用同一 Local Partner 的 PARTNER Agreement，同一交易命中的两类 Partner Term 必须属于同一 Agreement。；Agreement 必须覆盖全部 Price 的准确 Operating Entity，Version 有效区间不得超出 Agreement；APPROVED 后 Version、Price、Component 与 Band 业务字段不可修改。；本期 pricing_model 只接受 BLENDED；激活前 Version 下每个开通项至少命中一条 Price。';

CREATE TABLE `t_commercial_term_price` (
  `term_price_id` BIGINT NOT NULL COMMENT 'Commercial Term Version 下准确 OE、币种与定价画像价格绑定的内部唯一标识。',
  `term_version_id` BIGINT NOT NULL COMMENT '商业条款不可变业务版本的内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该价格准确适用且必须被所属 Agreement 覆盖的 Operating Entity；不允许 NULL 或通配值。',
  `currency` CHAR(3) NOT NULL COMMENT '该价格适用的交易币种，即费用币种；同一画像按币种各一条 Price，不做通配，组件金额同币种。取值来源：ISO 4217；非固定枚举。',
  `pricing_profile_code` VARCHAR(64) NOT NULL COMMENT '关联 ACTIVE 定价画像的全局稳定代码；不得保存或向外传播 Profile 内部 ID。取值来源：本库 ACTIVE 定价画像代码目录；非固定枚举。',
  `priority_no` INT NOT NULL COMMENT '同一 Commercial Term Version、准确 OE 与币种下、相同具体度候选的确定性顺序，数值越小越优先。',
  `settlement_business_days` INT NULL COMMENT 'Client 合同约定的 T+N 工作日结算到期天数。',
  `business_calendar_code` VARCHAR(64) NULL COMMENT '计算 Client 合同 T+N 工作日结算到期日使用的受控业务日历代码。取值来源：Finance工作日日历目录；非固定枚举。',
  `settlement_timezone` VARCHAR(64) NULL COMMENT '解释 Client 合同工作日和截止秒数使用的 IANA 时区代码。取值来源：IANA时区目录，本期仅UTC；非固定枚举。',
  `cutoff_second_of_day` INT NULL COMMENT '解释 Client 合同结算基准日的本地日内截止秒数，范围为 0 至 86399。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`term_price_id`),
  UNIQUE KEY `uk_term_price_profile` (`term_version_id`, `entity_no`, `currency`, `pricing_profile_code`) COMMENT 'Unique index for commercial_term_price on term_version_id, entity_no, currency, pricing_profile_code',
  UNIQUE KEY `uk_term_price_priority` (`term_version_id`, `entity_no`, `currency`, `priority_no`) COMMENT 'Unique index for commercial_term_price on term_version_id, entity_no, currency, priority_no',
  KEY `idx_term_price_profile` (`pricing_profile_code`, `term_version_id`) COMMENT 'Access index for commercial_term_price on pricing_profile_code, term_version_id',
  KEY `idx_term_price_oe` (`entity_no`, `currency`, `term_version_id`) COMMENT 'Access index for commercial_term_price on entity_no, currency, term_version_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同版本、准确 OE、交易币种与定价画像的价格绑定；uk_term_price_profile(term_version_id,entity_no,currency,pricing_profile_code)、uk_term_price_priority(term_version_id,entity_no,currency,priority_no)；currency 为 ISO 4217 交易币种即费用币种，同一画像按币种各一条 Price，不做通配；Profile Code 引用 ACTIVE Profile；OE 必须被 Agreement 覆盖；先按不同 Dimension 数量选择最高具体度，再按最小 Priority 唯一解析，零命中或仍有歧义均 Fail Closed；CLIENT_PRICING四项结算输入全非空，初期UTC/86399，N按工作日；Partner两类四项全空；费用和成熟输入必须取自同一命中Price，批准后不可修改；每条 Price 属于准确 term_version_id + entity_no + currency，以 pricing_profile_code 关联一个 ACTIVE 定价画像；画像可跨 Client、Version 与 OE 复用。；OE 必须被 Version 引用的 Agreement 覆盖；不允许 NULL、* 或全 OE 通配。；命中顺序：币种精确 → 支付方式精确 → 条件等值 → 维度数多者先 → priority_no 小者先；零命中、事实缺失或歧义均 Fail Closed。；PLATFORM_SERVICE_CHARGE 使用受控 Calculation Base；不得用其他费用残差倒算。；CLIENT 的四个结算字段全部非空（初期 UTC、86399），PARTNER 全部为空；settlement_business_days>=0，cutoff_second_of_day 在 0–86399。；费用币种 = 交易币种，同一画像按币种各一条 Price，不做通配；组件 currency 非空时等于本 Price 币种；激活前每个开通项 × 约束币种至少命中一条 Price。';

CREATE TABLE `t_commercial_term_component` (
  `term_component_id` BIGINT NOT NULL COMMENT '商业条款计算组件的内部唯一标识。',
  `term_price_id` BIGINT NOT NULL COMMENT 'Commercial Term Version 下准确 OE、币种与定价画像价格绑定的内部唯一标识。',
  `component_no` INT NOT NULL COMMENT '同一商业价格内从 1 开始且连续的稳定计算顺序。',
  `component_code` VARCHAR(64) NOT NULL COMMENT '同一商业价格内唯一的业务组件代码。',
  `component_type` VARCHAR(64) NOT NULL COMMENT '区分百分比、固定金额与阶梯组件的闭合类型代码。允许值：PERCENTAGE、FIXED、TIERED。',
  `trigger_code` VARCHAR(64) NOT NULL COMMENT '触发该组件计算的计费事件代码，必须在计费事件目录登记。取值来源：t_product_fee_event.fee_event_code 登记目录；非固定枚举。',
  `calculation_base` VARCHAR(64) NOT NULL COMMENT '组件计算基数；BUY_RATE_DIFFERENCE = Client 实收费用 − 同场景 PARTNER_BUY_RATE 命中金额，负值取 0，仅 PARTNER_SHARE 组件可用。允许值：GROSS_AMOUNT、NET_AMOUNT、ACTUAL_CLIENT_FEE、FIXED_PER_EVENT、BUY_RATE_DIFFERENCE。',
  `refund_behavior` VARCHAR(32) NOT NULL COMMENT '原交易退款时该组件费用的处理方式；非支付类事件的组件填 KEEP。允许值：RETURN_ALL、RETURN_PRORATA、KEEP。',
  `rate` DECIMAL(18,10) NULL COMMENT '百分比费用、分润或阶梯档位使用的小数费率，取值范围为 0 至 1。',
  `fixed_amount` DECIMAL(24,8) NULL COMMENT '固定费用或阶梯档位使用的金额参数；不适用时为空。',
  `currency` CHAR(3) NULL COMMENT '固定金额、金额上下限或阶梯边界存在时必填的精确 ISO 4217 币种；纯百分比且无上下限时为空。取值来源：ISO 4217；非固定枚举。',
  `min_amount` DECIMAL(24,8) NULL COMMENT '百分比组件计算结果至少收取或分配的最低金额。',
  `max_amount` DECIMAL(24,8) NULL COMMENT '百分比组件计算结果允许收取或分配的最高金额。',
  `band_basis` VARCHAR(32) NULL COMMENT '仅 TIERED 组件非空；TRANSACTION_AMOUNT 按单笔金额匹配档位，PERIOD_VOLUME 按周期累计交易量匹配且本期拒绝激活。允许值：TRANSACTION_AMOUNT、PERIOD_VOLUME。',
  `band_mode` VARCHAR(64) NULL COMMENT '仅 TIERED 组件非空；WHOLE_BASE 将命中档位参数用于完整基数，PROGRESSIVE 对各档位区间分别计算，RETROACTIVE 按周期累计命中档位回溯整期且只允许与 PERIOD_VOLUME 组合。允许值：WHOLE_BASE、PROGRESSIVE、RETROACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`term_component_id`),
  UNIQUE KEY `uk_comm_term_component` (`term_price_id`, `component_code`) COMMENT 'Unique index for commercial_term_component on term_price_id, component_code',
  UNIQUE KEY `uk_comm_term_component_no` (`term_price_id`, `component_no`) COMMENT 'Unique index for commercial_term_component on term_price_id, component_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client Fee、Partner Share 或 Platform Service Charge 计算组件；uk_comm_term_component(term_price_id,component_code)、uk_comm_term_component_no(term_price_id,component_no)；类型 PERCENTAGE/FIXED/TIERED；多行 Component 按 component_no 稳定计算并相加；TIERED 的头部 rate/fixed_amount 为空、band_basis=TRANSACTION_AMOUNT/PERIOD_VOLUME、band_mode=WHOLE_BASE/PROGRESSIVE/RETROACTIVE 且至少一档，RETROACTIVE 只配 PERIOD_VOLUME，其他类型不得有 Band；PERCENTAGE、FIXED、TIERED 必须满足批准的 rate、fixed_amount、currency、min_amount、max_amount 与 band_mode 矩阵。；多个 Component 按 component_no 稳定计算并相加；PERCENTAGE/FIXED 不得拥有 Band，TIERED 头部 rate/fixed_amount 为空、band_mode 非空且至少拥有一档。；CLIENT_PRICING 使用闭合计算基数；PARTNER_SHARE 使用 calculation_base=ACTUAL_CLIENT_FEE 或 BUY_RATE_DIFFERENCE；PLATFORM_SERVICE_CHARGE 使用闭合计算基数，现实场景可使用 GROSS_AMOUNT。；BUY_RATE_DIFFERENCE = Client 实收费用 − 同场景 PARTNER_BUY_RATE 命中金额，负值取 0；仅 PARTNER_SHARE 组件可用。；TIERED 要求 band_basis 非空，其他类型 band_basis 为空；RETROACTIVE 只允许与 PERIOD_VOLUME 组合，本期 band_basis=PERIOD_VOLUME 拒绝激活。；trigger_code 必须在 product_fee_event 登记；非支付类事件的组件 refund_behavior=KEEP。；所属 Version 进入 APPROVED 后结构化 Component 与 Band 不可修改；调整条款必须创建新 Version。';

CREATE TABLE `t_commercial_term_component_band` (
  `term_band_id` BIGINT NOT NULL COMMENT '商业条款阶梯组件档位的内部唯一标识。',
  `term_component_id` BIGINT NOT NULL COMMENT '商业条款计算组件的内部唯一标识。',
  `band_no` INT NOT NULL COMMENT '同一阶梯组件内从 1 开始且连续的稳定档位顺序。',
  `lower_bound` DECIMAL(24,8) NOT NULL COMMENT '阶梯档位适用金额区间的包含下界。',
  `upper_bound` DECIMAL(24,8) NULL COMMENT '档位不包含上界；最后一档必须为空，其他档位必须与下一档下界连续。',
  `rate` DECIMAL(18,10) NULL COMMENT '百分比费用、分润或阶梯档位使用的小数费率，取值范围为 0 至 1。',
  `fixed_amount` DECIMAL(24,8) NULL COMMENT '固定费用或阶梯档位使用的金额参数；不适用时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`term_band_id`),
  UNIQUE KEY `uk_comm_term_band_no` (`term_component_id`, `band_no`) COMMENT 'Unique index for commercial_term_component_band on term_component_id, band_no',
  UNIQUE KEY `uk_comm_term_band_lower` (`term_component_id`, `lower_bound`) COMMENT 'Unique index for commercial_term_component_band on term_component_id, lower_bound'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='阶梯定价组件的金额区间与档位参数；uk_comm_term_band_no(term_component_id,band_no)、uk_comm_term_band_lower(term_component_id,lower_bound)；区间为 [lower_bound,upper_bound)，从 0 开始、连续、无重叠且最后一档上界为空；每档按组件矩阵填写 rate 或 fixed_amount，PROGRESSIVE 首期只允许 rate；区间使用 [lower_bound,upper_bound)，第一档从 0 开始、连续且无重叠，最后一档 upper_bound 为空。；每档 rate 与 fixed_amount 恰好一个非空；PROGRESSIVE 首期只允许 rate，固定金额组合使用独立 FIXED Component。；Band 继承 Component 的 currency 与 calculation_base；同一组件 band_no 从 1 连续编号，所属 Version APPROVED 后不可修改。';

CREATE TABLE `t_business_change_record` (
  `history_id` BIGINT NOT NULL COMMENT '一条受审计业务行稳定历史身份的内部唯一标识。',
  `record_type` VARCHAR(64) NOT NULL COMMENT '仅标识已批准接入 BCR 的审计对象类型；不在白名单内的业务表和技术表不得登记。允许值：PARTY、CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、PARTY_PROFILE、CLIENT_BUSINESS_PROFILE、COMMERCIAL_TERM_VERSION、CLIENT_APPLICATION、PARTNER_APPLICATION、OPERATING_ENTITY_APPLICATION、PARTY_RELATION、CLIENT_ENTITY_AFFILIATION、ENTITY_SOURCE_RELATION、AGREEMENT、CONSUMER_LEGAL_DOCUMENT、SELLER_DISCLOSURE、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE。',
  `record_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被审计业务行在其 record_type 下的稳定业务标识。',
  `aggregate_root_type` VARCHAR(64) NOT NULL COMMENT '该审计对象所属且负责并发串行化的业务聚合根类型；只能使用已批准的 Root 类型。允许值：PARTY、CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、CLIENT_APPLICATION、PARTNER_APPLICATION、OPERATING_ENTITY_APPLICATION、PARTY_RELATION、CLIENT_ENTITY_AFFILIATION、ENTITY_SOURCE_RELATION、AGREEMENT、CONSUMER_LEGAL_DOCUMENT、SELLER_DISCLOSURE、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE。',
  `aggregate_root_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该变更记录所属业务聚合根的稳定业务标识。',
  `parent_history_id` BIGINT NULL COMMENT '同一聚合内部直接父业务行对应的变更主记录标识。',
  `current_version` BIGINT NOT NULL COMMENT 'Root 记录中是唯一 expected_change_version CAS 令牌；受审计 Child 中只指向最近一次快照采用的 Root 版本，不执行独立 Child CAS。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_bcr_record` (`record_type`, `record_id`) COMMENT 'Unique index for business_change_record on record_type, record_id',
  KEY `idx_business_change_rec_6c86a4f4` (`aggregate_root_type`, `aggregate_root_id`, `history_id`) COMMENT 'Access index for business_change_record on aggregate_root_type, aggregate_root_id, history_id',
  KEY `idx_business_change_rec_6d3f5c71` (`parent_history_id`) COMMENT 'Access index for business_change_record on parent_history_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='白名单业务对象的稳定审计身份与当前修订；uk_bcr_record(record_type,record_id)；record_type 与 aggregate_root_type 使用第 7 节闭合白名单；Root 的 Record Tuple 等于 Aggregate Root Tuple 且 Parent 为空，归属不可修改；仅 Root current_version 是 CCT 聚合 CAS 令牌；current_version > 0；Record Type 白名单由 record_type 字段闭合枚举定义；白名单外不创建 BCR。；非审计 Child 使用冻结的结构化事实或所属版本保留历史，完整目录见 BCR_EXCLUDED_BUSINESS_TABLES。；CREATE 原子协议：创建受审计聚合时，必须在同一事务插入结构化业务数据、Root business_change_record(current_version=1)、Root Version 1 CREATE Snapshot、白名单内且本次确需审计的 Child Record/Snapshot 与本地 compensation_event。；Root UPDATE CAS：外部命令必须携带 expected_change_version，固定 next_version=expected_change_version+1；Root business_change_record 必须以 current_version=expected_change_version 条件 CAS 到 next_version，影响行数必须为 1。；受审计 Child 版本指针：只有 Record Type 白名单内且本次发生需留档变化的 Child 才追加最终 Snapshot，并把 current_version 推进到 Root next_version；Root CAS 已串行化同聚合写入，Child current_version 不是独立 CAS Token。；非审计 Child：白名单外 Child 不创建 BCR、不接收 expected_change_version，也不写重复 Snapshot；其 Draft 改动由所属 Root CAS 保护，冻结后历史由结构化 Version 或追加式领域证据保留。；DELETE 顺序：删除白名单内受审计业务行前必须先追加包含稳定身份与删除前安全字段的 DELETE Snapshot，再删除结构化行；Record 永久保留。；System Transition 协议：定时激活、过期、撤销和系统回调必须作为锁内读取 expected_change_version 的 System Command，执行同一 Root CAS/History/Event 协议，不得绕过审计版本。；Multi-Root 原子协议：受影响 Root 必须按 (aggregate_root_type,aggregate_root_id) 固定顺序锁定和 CAS；每个 Root 独立计算 next_version 并写 Root/白名单内 Changed Child Snapshot，所有 Snapshot 使用同一 _meta.triggerId 关联。；整体回滚：任一 Root CAS、业务校验、结构化业务更新、应写 History Snapshot 或 compensation_event 登记失败，整个本地事务必须回滚。；Event 原子与版本：权威业务变化、business_change_record/business_change_version History 与每个 compensation_event 必须在同一本地事务提交；每个 Event.source_version 必须等于它所代表 Root 的 next_version，不得借用其他 Root 版本。';

CREATE TABLE `t_business_change_version` (
  `change_version_id` BIGINT NOT NULL COMMENT '不可变业务行审计快照的内部唯一标识。',
  `history_id` BIGINT NOT NULL COMMENT '该不可变快照所属 business_change_record 的内部标识。',
  `version_no` BIGINT NOT NULL COMMENT '本次变化提交后的聚合根版本坐标，不是连续的行版本号，同一业务行允许跳号。',
  `operation_type` VARCHAR(64) NOT NULL COMMENT '本次不可变审计快照对应的业务行操作类型。允许值：CREATE、UPDATE、DELETE。',
  `snapshot_json` JSON NOT NULL COMMENT '按 Record Type 白名单生成的不可变 JSON 审计视图，含 Schema、Trigger、Reason 与完整性哈希。',
  `snapshot_format` VARCHAR(32) NOT NULL COMMENT '新快照使用ENTITY_NO，历史快照保留LEGACY_NUMERIC；判别不改变原JSON或Hash。允许值：ENTITY_NO、LEGACY_NUMERIC。',
  `create_by` BIGINT NOT NULL COMMENT '创建不可变审计快照的外部 Actor 稳定标识，是逻辑引用且不建立跨库物理外键。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`change_version_id`),
  UNIQUE KEY `uk_bcv_history_ver` (`history_id`, `version_no`) COMMENT 'Unique index for business_change_version on history_id, version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变行级审计快照；uk_bcv_history_ver(history_id,version_no)；CREATE/UPDATE/DELETE，只 INSERT，Snapshot 白名单与 Canonical Integrity Hash，最大 64 KiB，无 update_time；snapshot_format明确ENTITY_NO/LEGACY_NUMERIC，旧Snapshot及Hash不得重写；version_no > 0；operation_type IN (CREATE,UPDATE,DELETE)；64 KiB';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿执行记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '领域事件或本地恢复动作的全局稳定幂等键。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '需要发布或本地恢复执行的领域事件类型代码。取值来源：CCT 唯一 Compensation Handler Catalog；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '同一 Handler 下的业务子类型；无业务子类型时使用短横线占位。取值来源：所属 Handler 的子类型目录，无业务子类型时固定短横线占位；非固定枚举。',
  `payload_json` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '可空或最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。',
  `execute_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT' COMMENT '补偿任务当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '补偿任务已经尝试执行的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿任务下一次允许被 Claim 的 UTC 微秒精度时刻。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前 Claim 补偿任务的执行实例标识。',
  `lock_until` DATETIME(6) NULL COMMENT '当前补偿任务执行租约到期的 UTC 微秒精度时刻。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿执行最近一次失败的稳定错误分类代码。取值来源：CCT Compensation Error Catalog；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '补偿执行最近一次失败的脱敏错误摘要，不保存敏感原文。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT '补偿任务 Claim 与完成回写使用的独立技术 CAS 版本，不是业务 Revision 或来源版本。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT 'Unique index for compensation_event on event_key, event_type, sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT 'Access index for compensation_event on execute_status, next_execute_at, id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT 'Access index for compensation_event on execute_status, lock_until, id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='本服务 Handler 的可恢复任务；uk_comp_event_identity(event_key,event_type,sub_event_type)；状态 INIT/PROCESSING/ERROR/FAILED；可选最小 JSON Object 最大 4 KiB UTF-8，禁止敏感值、Vault Token 和已落库上下文；Claim/Lease 使用 lock_version，安全默认值与执行协议服从现行补偿指南；Payload 最小化：payload_json 可空或为最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。；Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。；Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。；登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。；成功删除：Handler 只有取得确定成功结果后才允许删除 compensation_event；MQ Handler 必须等待 Broker Confirm。；失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。；Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。';

CREATE TABLE t_consumed_event (
    event_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件 ID（信封 eventId，默认等于 eventKey）',
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型（PascalCase，登记于事件目录）',
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型（大写 Code）',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识（分区键）',
    aggregate_version BIGINT NOT NULL COMMENT '本事件携带的聚合版本',
    consumed_at DATETIME(6) NOT NULL COMMENT '消费事务提交时间（UTC）',
    PRIMARY KEY (event_id),
    KEY idx_consumed_event_aggregate (aggregate_type, aggregate_id, aggregate_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '已消费领域事件：按事件 ID 去重';
