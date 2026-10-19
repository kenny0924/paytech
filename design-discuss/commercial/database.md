# commercial 数据库模型（`mor_commercial`）设计

> 状态：设计稿（2026-10-19）。依据 [design.md](./design.md) 第 4、5 节与[决定记录](../../agent-docs/decisions/2026-10-19-commercial-six-layer-product-pricing.md)。现状以 `paytech/commercial/docs/database/sql/commercial-schema.sql`（14 张表）为准；模型由 `scripts/pdmaner/generate-commercial-model.mjs` 从 `agent-docs/database/CLIENT_CONTRACT_DATABASE.md` 第 3 节目录 + `client-contract-field-metadata.mjs` 生成，**不手改 `commercial.pdma.json`**。类型约定沿用 `DATABASE_DESIGN_GUIDE.md`：Snowflake `BIGINT` 主键、`DATETIME(6)` UTC、金额 `DECIMAL(24,8)`、费率 `DECIMAL(18,10)`、不建 CHECK / FK。
>
> **与 design.md 第 5 节的一处修正：** 结算四字段（T+N、日历、时区、截止秒）**保留在 `t_commercial_term_price`**，不挪到开通项。原因：T+N 按画像粒度（Visa 信用卡本地 T+2、Amex T+3）比按开通项更细，且 stage3 的 `commercial-prices` 合同已把结算输入放在 Price 响应里；挪动需要再建"开通项 ↔ 画像"映射，收益不够。开通项只持有保证金条款。

## 1. 表清单：14 → 20

| 分组 | 表 | 变化 |
|---|---|---|
| 产品目录（新） | `t_product` | **新增** L0 产品 |
| 产品目录（新） | `t_product_capability` | **新增** L1 可开通项目录 |
| 产品目录（新） | `t_product_fee_event` | **新增** L2 计费事件目录 |
| 合同 | `t_agreement` | **+ `business_mode`** |
| 合同 | `t_agreement_party` / `t_agreement_document_ref` / `t_agreement_operating_entity` | 不变 |
| 合同 | `t_agreement_product` | **新增** 合同订阅的产品 |
| 合同 | `t_agreement_capability` | **新增** 合同在精确 OE 下的开通项 + 保证金条款 |
| 合同 | `t_agreement_capability_limit` | **新增** 开通项约束，一值一行 |
| 定价画像 | `t_payment_method_profile` | **+ `profile_kind`**；`payment_method_code` 改可空；显示名改"定价画像"（物理名不改，避免 API / Java 连锁改名） |
| 定价画像 | `t_payment_method_profile_cond` | `dimension_code` 收口为闭合值 |
| 商业条款 | `t_commercial_term_plan` | `term_type` 改为目录驱动；`status` 收口 |
| 商业条款 | `t_commercial_term_version` | **+ `pricing_model`、`fee_tax_treatment`** |
| 商业条款 | `t_commercial_term_price` | **+ `currency`**（多币种方案 B，2026-10-19 用户已定）；唯一键改为含币种；结算四字段保留 |
| 商业条款 | `t_commercial_term_component` | **+ `refund_behavior`、`band_basis`**；`trigger_code` 改为目录驱动；`calculation_base` 收口；`band_mode` 增 RETROACTIVE |
| 商业条款 | `t_commercial_term_component_band` | 不变 |
| 审计 / 补偿 | `t_business_change_record` / `t_business_change_version` / `t_compensation_event` | `record_type` 白名单加 AGREEMENT_PRODUCT、AGREEMENT_CAPABILITY |

## 2. 新增表

### 2.1 `t_product`（产品）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `product_id` | BIGINT | NN | PK |
| `product_code` | VARCHAR(64) | NN | 产品稳定代码 = 可售模块码；创建后不可改。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。`uk_product_code` |
| `product_name` | VARCHAR(128) | NN | 运营识别用展示名 |
| `business_mode_scope` | VARCHAR(16) | NN | 该产品允许的业务模式。允许值：MOR_ONLY、PSP_ONLY、BOTH |
| `base_product` | TINYINT(1) | NN | 1 = 基座产品，所有实例必装、合同无需订阅；0 = 可选模块 |
| `status` | VARCHAR(64) | NN | 允许值：DRAFT、ACTIVE、RETIRED。RETIRED 阻止新合同订阅，历史订阅可读 |
| `create_time` / `update_time` | DATETIME(6) | NN | |

### 2.2 `t_product_capability`（可开通项目录）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `product_capability_id` | BIGINT | NN | PK |
| `product_code` | VARCHAR(64) | NN | 所属产品 → `t_product.product_code` |
| `capability_code` | VARCHAR(64) | NN | 全局唯一稳定代码，如 CARD_VISA、CARD_AMEX、APPLE_PAY、WALLET_STC、BNPL_TABBY、PAYOUT_LOCAL、PAYOUT_SWIFT、FX_SPOT、DISPUTE_HANDLING、ANALYTICS_SUBSCRIPTION。取值来源：产品发版维护的能力目录；非固定枚举。`uk_product_capability_code` |
| `capability_name` | VARCHAR(128) | NN | 展示名 |
| `capability_kind` | VARCHAR(32) | NN | 允许值：PAYMENT_METHOD、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE |
| `payment_method_code` | VARCHAR(64) | NULL | PAYMENT_METHOD 必填，其余为空；取值来源：Payment 拥有的 Payment Method 目录；非固定枚举 |
| `dimension_code` | VARCHAR(64) | NULL | 把开通项细分到画像维度的单一条件，如 CARD_BRAND；与 `dimension_value` 成组为空或非空。允许值同 `t_payment_method_profile_cond.dimension_code` |
| `dimension_value` | VARCHAR(128) | NULL | 上述维度的规范化值，如 AMEX |
| `status` | VARCHAR(64) | NN | 允许值：DRAFT、ACTIVE、RETIRED |
| `create_time` / `update_time` | DATETIME(6) | NN | |

索引：`idx_product_capability_product (product_code, status)`。

### 2.3 `t_product_fee_event`（计费事件目录）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `product_fee_event_id` | BIGINT | NN | PK |
| `product_code` | VARCHAR(64) | NN | 所属产品 |
| `fee_event_code` | VARCHAR(64) | NN | 触发收费的业务动作，= `t_commercial_term_component.trigger_code` 的值域。如 AUTH、CAPTURE、SALE、REFUND、CHARGEBACK、DISPUTE_WON_REVERSAL、PAYOUT_SUBMITTED、PAYOUT_RETURNED、FX_DEAL、DCC_APPLIED、MONTHLY_CYCLE、SETUP、TOKEN_STORED。取值来源：产品发版维护的计费事件目录；非固定枚举 |
| `term_type` | VARCHAR(64) | NN | 该事件可在哪类条款下收费，一值一行。如 CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、PAYOUT_FEE、FX_SPREAD、DISPUTE_FEE、PLATFORM_FEE。取值来源：本表登记；非固定枚举 |
| `billing_fact_type` | VARCHAR(64) | NN | 对应 billing 事实类型，如 PAYMENT、REFUND、DISPUTE、PAYOUT_FEE、FX_SELL。取值来源：billing 事实类型目录；非固定枚举 |
| `create_time` / `update_time` | DATETIME(6) | NN | |

唯一键：`uk_product_fee_event (fee_event_code, term_type)`；索引：`idx_product_fee_event_product (product_code)`。

### 2.4 `t_agreement_product`（合同订阅产品）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `agreement_product_id` | BIGINT | NN | PK |
| `agreement_id` | BIGINT | NN | → `t_agreement` |
| `product_code` | VARCHAR(64) | NN | → `t_product.product_code`；产品的 `business_mode_scope` 必须包含合同的 `business_mode`；`base_product=1` 的产品不写行 |
| `create_time` / `update_time` | DATETIME(6) | NN | |

唯一键：`uk_agreement_product (agreement_id, product_code)`。

### 2.5 `t_agreement_capability`（合同开通项）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `agreement_capability_id` | BIGINT | NN | PK |
| `agreement_id` | BIGINT | NN | → `t_agreement`；所属产品必须已在 `t_agreement_product` 订阅 |
| `entity_no` | CHAR(5) | NN | 精确 OE；CLIENT 合同须等于 `t_agreement.entity_no`，PARTNER 合同须在 `t_agreement_operating_entity` 内 |
| `capability_code` | VARCHAR(64) | NN | → `t_product_capability.capability_code`，引用时须 ACTIVE |
| `status` | VARCHAR(32) | NN | 允许值：ENABLED、SUSPENDED、DISABLED。SUSPENDED = 风控 / 合规临时停用，可恢复；DISABLED = 运营关闭 |
| `effective_from` | DATETIME(6) | NN | 开通生效起点（含） |
| `effective_to` | DATETIME(6) | NULL | 开通结束（不含）；空 = 未预定结束 |
| `rolling_reserve_rate` | DECIMAL(18,10) | NULL | 滚动保证金比例 0–1；无保证金为空；执行归 account / risk |
| `rolling_reserve_days` | INT | NULL | 滚动保证金持有天数；与比例成组为空或非空 |
| `create_time` / `update_time` | DATETIME(6) | NN | |

唯一键：`uk_agreement_capability (agreement_id, entity_no, capability_code)`；索引：`idx_agreement_capability_lookup (entity_no, capability_code, status)`、`idx_agreement_capability_code (capability_code)`。

### 2.6 `t_agreement_capability_limit`（开通项约束）

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `agreement_capability_limit_id` | BIGINT | NN | PK |
| `agreement_capability_id` | BIGINT | NN | → `t_agreement_capability` |
| `limit_code` | VARCHAR(64) | NN | 允许值：CURRENCY、CONSUMER_COUNTRY、REQUIRE_3DS。同码多行 OR、异码 AND；金额限额归风控决策，不在此表 |
| `limit_value` | VARCHAR(128) | NN | 规范化值：ISO 4217 / ISO 3166-1 alpha-2 / `1` |
| `create_time` / `update_time` | DATETIME(6) | NN | |

唯一键：`uk_agreement_capability_limit (agreement_capability_id, limit_code, limit_value)`。

## 3. 既有表字段改动

| 表 | 列 | 变化 |
|---|---|---|
| `t_agreement` | `business_mode` VARCHAR(16) | **新增**。允许值：MOR、PSP。CLIENT 合同非空，PARTNER 合同为空；创建后不可改 |
| `t_payment_method_profile` | `profile_kind` VARCHAR(32) | **新增** NN。允许值：PAYMENT_METHOD、PAYOUT_RAIL、FX_PAIR、DISPUTE、PERIODIC；ACTIVE 后冻结 |
| `t_payment_method_profile` | `payment_method_code` | 改**可空**：PAYMENT_METHOD 必填，其余为空 |
| `t_payment_method_profile_cond` | `dimension_code` | 收口为闭合值。允许值：CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、ISSUER_REGION、THREE_DS_RESULT、CURRENCY、CONSUMER_COUNTRY、TRANSACTION_TYPE、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR、DISPUTE_STAGE |
| `t_commercial_term_plan` | `term_type` | 取值来源改为 `t_product_fee_event.term_type` 登记；非固定枚举（现三值继续有效） |
| `t_commercial_term_plan` | `status` | 收口。允许值：DRAFT、ACTIVE、RETIRED |
| `t_commercial_term_version` | `pricing_model` VARCHAR(32) | **新增** NN。允许值：BLENDED、INTERCHANGE_PLUS_PLUS；本期应用只接受 BLENDED |
| `t_commercial_term_version` | `fee_tax_treatment` VARCHAR(16) | **新增** NN。允许值：INCLUSIVE、EXCLUSIVE |
| `t_commercial_term_price` | `currency` CHAR(3) | **新增** NN，紧随 `entity_no`。该价格适用的交易币种 = 费用币种；取值来源：ISO 4217；非固定枚举。唯一键改为 `uk_term_price_profile (term_version_id, entity_no, currency, payment_method_profile_code)`、`uk_term_price_priority (term_version_id, entity_no, currency, priority_no)`；访问索引 `idx_term_price_oe` 改为 `(entity_no, currency, term_version_id)` |
| `t_commercial_term_component` | `trigger_code` | 取值来源改为 `t_product_fee_event.fee_event_code`；非固定枚举 |
| `t_commercial_term_component` | `calculation_base` | 收口。允许值：GROSS_AMOUNT、NET_AMOUNT、ACTUAL_CLIENT_FEE、FIXED_PER_EVENT |
| `t_commercial_term_component` | `refund_behavior` VARCHAR(32) | **新增** NN。允许值：RETURN_ALL、RETURN_PRORATA、KEEP。非支付类事件的组件填 KEEP |
| `t_commercial_term_component` | `band_basis` VARCHAR(32) | **新增**，仅 TIERED 非空。允许值：TRANSACTION_AMOUNT、PERIOD_VOLUME |
| `t_commercial_term_component` | `band_mode` | 允许值增 RETROACTIVE：WHOLE_BASE、PROGRESSIVE、RETROACTIVE；RETROACTIVE 只允许与 PERIOD_VOLUME 组合 |
| `t_business_change_record` | `record_type` | 白名单：AGREEMENT、AGREEMENT_PRODUCT、AGREEMENT_CAPABILITY、COMMERCIAL_TERM_PLAN、COMMERCIAL_TERM_VERSION、PAYMENT_METHOD_PROFILE；Root 仍为 AGREEMENT、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE。产品目录三表随发版维护，不接入 BCR；`_limit` 为冻结 Child，不单独审计 |

## 4. 关系图（库内，PDManer Correlation；跨库引用只存值）

```text
t_product ─1:n─ t_product_capability
t_product ─1:n─ t_product_fee_event
t_product ─1:n─ t_agreement_product ─n:1─ t_agreement
t_product_capability ─1:n─ t_agreement_capability ─n:1─ t_agreement
t_agreement_capability ─1:n─ t_agreement_capability_limit

t_agreement ─1:n─ t_agreement_party / t_agreement_document_ref / t_agreement_operating_entity   （已有）
t_agreement ─1:1─ t_commercial_term_plan（CLIENT_PRICING）─1:n─ t_commercial_term_version ─1:n─ t_commercial_term_price
t_commercial_term_price ─n:1─ t_payment_method_profile ─1:n─ t_payment_method_profile_cond   （已有）
t_commercial_term_price ─1:n─ t_commercial_term_component ─1:n─ t_commercial_term_component_band   （已有）
```

值引用、不建关系：`entity_no`、`client_id`、`local_partner_id`、`party_id`、`party_profile_id`、`file_id`（client-master / Platform）；`trigger_code`、`term_type` → `t_product_fee_event`（非唯一列，不做 Correlation）。

图组：Agreement（合同 4 表 + 订阅 / 开通 / 约束 3 表）、Product Catalog（目录 3 表）、Client Pricing and Partner Share（画像 2 表 + 条款 5 表）、Local History and Reliability（3 表）。

## 5. 应用层不变量（不建 CHECK）

1. `t_agreement_product.product_code` 的 `business_mode_scope` 包含合同 `business_mode`；基座产品不写订阅行。
2. `t_agreement_capability`：产品已订阅；`capability_code` ACTIVE；`entity_no` 被合同覆盖；保证金两字段成组。
3. 解析时开通项判定：`status=ENABLED` 且 `effective_from <= contextAsOfTime < effective_to`，且本次支付方式（及品牌等 `dimension_code/value`）命中一条开通项，限制全部满足。
4. 命中顺序：币种精确 = 交易币种 → 画像具体度 → `priority_no`；组件的 `currency` 非空时必须等于 Price 的 `currency`。
5. Price 激活前校验：每个开通项的每个 CURRENCY 约束币种至少命中一条 Price；Version 下每个开通项至少命中一条 Price（覆盖完整）；Band 连续无重叠；`RETROACTIVE` 仅配 `PERIOD_VOLUME`；本期 `pricing_model` 只接受 BLENDED，`band_basis=PERIOD_VOLUME` 本期拒绝激活。
6. 画像：`profile_kind=PAYMENT_METHOD` 时 `payment_method_code` 非空，否则为空；条件维度须与种类相容（出款类只用 PAYOUT_RAIL / DESTINATION_COUNTRY / CURRENCY，FX 只用 CURRENCY_PAIR）。

## 6. 实现清单（本次只做模型与 SQL 草案，不执行到任何环境）

1. `agent-docs/database/CLIENT_CONTRACT_DATABASE.md` 第 3 节目录加 6 张表行、改 4 张表的字段列表与约束列。
2. `scripts/pdmaner/client-contract-field-metadata.mjs`：表显示名、每个新字段的中文 defName / 说明、CLOSED_ENUM_VALUES、OPEN_CODE_SOURCES、CONDITIONAL_VALUE_RULES、BCR 白名单。
3. `scripts/pdmaner/client-contract-model-core.mjs`：类型集合（INT / FLAG / RATE / VARCHAR 长度）、NULLABLE_FIELD_PATHS、TABLE_COMMENT_CLAUSES、ACCESS_INDEXES、LOCAL_RELATIONS。
4. `scripts/pdmaner/generate-commercial-model.mjs`：COMMERCIAL_TABLES、图组。
5. 重新生成 `paytech/commercial/docs/database/commercial.pdma.json` 与 `sql/commercial-schema.sql`（`generate-oe-client-schemas.mjs`）；更新 `COMMERCIAL_PHYSICAL_DECISIONS.md` 摘要与 `client-contract-model.test.mjs` / `oe-client-model.test.mjs` 的期望统计与 Digest；`node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/oe-client-model.test.mjs scripts/pdmaner/pdmaner-canonical-paths.test.mjs` 全绿。
6. client-master 模型不受影响（共享目录但只取各自表）；若生成器断言要求两库表集合并集与目录一致，client-master 的期望统计不变。

## 7. 多币种（2026-10-19 用户已定方案 B）

- 开通层：币种集合 = 开通项的 CURRENCY 约束。
- 定价层：费用币种 = 交易币种；Price 加 `currency`，同一画像按币种各一条 Price，组件金额同币种；纯百分比价格也按币种复制，不做通配。
- 结算层：余额按主体 + 币种分片；结算换汇与出款换汇是 FX 产品的点差事实，不改变交易计价。结算偏好（分币种结算 / 自动换汇到基准币种）是合同级条款，首切片固定为分币种结算。
- 展示层：费率页按币种 Tab 切换，固定费列随币种显示。

## 8. 第二轮模型改动（2026-10-19 用户决定：Profile 改名、维度补两项、商业关系属性）

| 改动 | 内容 |
|---|---|
| 画像表改名 | `t_payment_method_profile` → `t_pricing_profile`（定价画像），`t_payment_method_profile_cond` → `t_pricing_profile_cond`（定价画像条件）；主键 `pricing_profile_id`，代码列 `pricing_profile_code`，条件表父引用 `pricing_profile_id`；`t_commercial_term_price.payment_method_profile_code` → `pricing_profile_code`；唯一键 / 索引名同步（`uk_pricing_profile_code`、`uk_pricing_profile_cond_value`、`idx_pricing_profile_lookup`、`idx_pricing_cond_lookup`、`uk_term_price_profile` 字段名随之改）。Java：commercial 模块内 Mapper / XML / DataObject / Repository / 枚举 `PricingProfileStatus` / 测试固件与 DDL 同步改名；stage3 合同字段 `paymentMethodProfileCode` 在实现时改为 `pricingProfileCode`，本轮不动 api 模块 |
| 维度枚举 | `dimension_code` 允许值增 ISSUER_COUNTRY、CARD_PRODUCT（共 14 值） |
| `t_agreement` | + `fund_mode` VARCHAR(32) NULL：CLIENT 合同非空、PARTNER 空；允许值 SETTLE_THROUGH_PLATFORM、FEES_ONLY；MOR 只允许 SETTLE_THROUGH_PLATFORM，FEES_ONLY 只允许 OE 来源 CLIENT；本期应用只接受 SETTLE_THROUGH_PLATFORM |
| `t_agreement` | + `pricing_authority` VARCHAR(16) NULL：CLIENT 合同非空；允许值 PLATFORM、PARTNER；PARTNER 时价目由推荐伙伴提出、平台运营录入审批，Partner 门户只读；本期只接受 PLATFORM |
| `t_agreement` | + `referral_partner_id` BIGINT NULL：推荐伙伴（client-master Local Partner，只存值，跨库引用）；签署时冻结；`pricing_authority=PARTNER` 时必须非空；访问索引 `idx_agreement_referral (referral_partner_id)` |
| `t_commercial_term_plan.term_type` | 取值来源目录示例增 PARTNER_BUY_RATE（Partner 合同下、结构同 CLIENT_PRICING，按画像 × 币种） |
| `t_commercial_term_component.calculation_base` | 允许值增 BUY_RATE_DIFFERENCE：= Client 实收费用 − 同场景 PARTNER_BUY_RATE 命中金额，负值取 0；仅 PARTNER_SHARE 组件可用 |
| 字段顺序 | `t_agreement`：`business_mode` 之后依次 `fund_mode`、`pricing_authority`、`referral_partner_id` |
