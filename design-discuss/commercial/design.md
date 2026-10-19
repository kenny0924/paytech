# commercial（商务 · 售卖）的职责与设计

> 状态：已确认（2026-10-19；决定记录 [2026-10-19 commercial 六层产品定价模型](../../agent-docs/decisions/2026-10-19-commercial-six-layer-product-pricing.md)）。用户同日确认六层模型、IC++ 本期只留字段、月量阶梯规则归商务 / 用量归 billing，其余待定项全部按建议；实现未开始。依据：[目标架构 V2 决定](../../agent-docs/decisions/2026-10-17-target-architecture-v2.md) D02 / D09 / D13 / D20 / D31 / D39、[2026-09-10 Profile 定价决定](../../agent-docs/decisions/2026-09-10-cct-payment-method-profile-pricing.md)、[2026-09-11 四系统决定 stage3 合同](../../requirements/20260911/client-contract-OE与Client模型梳理/stage3-contracts.md)、[阶段 0 设计 06 两段式解析](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/06-cct-two-stage-resolution.md)、[billing 讨论稿](../billing/design.md) 第 8 节上游缺口、`paytech/commercial` 现有 Schema 与枚举。图：[design.pen](./design.pen)（两张画板：职责与设计、数据库表与枚举）。讨论定稿后登记决定记录。

## 1. 一句话

**commercial 是"卖什么、按什么条件卖、跟谁签了什么"的唯一权威。** 它持有产品目录、合同（含业务模式 MoR / PSP）、商业条款版本，回答两类问题：交易前"这个 Client 在这个 OE 下适用哪份合同、哪个业务模式、哪个条款版本"；交易后"冻结的那个版本在这组支付事实下命中哪条价格"。它不算钱（billing）、不管主体（client-master）、不算税（tax）、不持交易状态（acquiring）、不做审批裁决（workflow-approval 只记"人已同意"）。

V2 里没有 CCT 服务：原 client-contract-service 在阶段 4 第 04 步拆成 client-master（Party / Client / Partner / OE / Scope / Affiliation / 法律文档 / 披露）与 commercial（Agreement 四表 + Profile 两表 + Term 五表）。合同整体归商务；客户主档反查"共签人员"走 `GET /commercial/v1/agreement-parties/exists`。

## 2. 现状与目标的差距（2026-10-19）

| 能力 | 现在 | 目标 |
|---|---|---|
| Agreement 四表、Profile + Term 七表、BCR 两表 | 表、Mapper、Repository 都在，**零写入代码**；入驻审批不创建合同 | 运营控制台可建合同、签署登记、配价、排期激活 |
| `POST /checkout-context/v1/resolutions` | 已搬到 commercial；client-master 候选 → 本库解析 → Risk（503 占位）→ verify | 不变；输出补 `businessMode`、`agreementProducts`、开通项；Risk 门禁接通 |
| `POST /commercial-prices/v1/resolutions`、`GET /commercial-prices/v1/{priceId}` | stage3 合同已写，接口没有 | 实现；billing 首切片硬依赖（上游缺口 X3） |
| 业务模式 MoR / PSP | D02 定了挂合同按 OE；表里没有字段 | `t_agreement.business_mode`；收单准备支付时冻结四项派生 |
| 产品目录、能力开通 | 没有；`term_type` 写死三类 | 六层产品模型（第 4 节） |
| 条款版本排期 | `t_commercial_term_version` 已有 `effective_from` 与 `SCHEDULED`，但没有写入方 | 运营写入实际使用；解析按 `contextAsOfTime` 选版本 |
| 报价（Quote） | 没有 | 后置 |

## 3. 职责边界

### 3.1 commercial 拥有

1. **产品目录**：产品（= 可售模块）、可开通项、计费事件三张目录表；实例装了模块才可售（D20 投影）。
2. **合同 Agreement**：CLIENT 合同精确一个 OE、PARTNER 合同多 OE（沿用）；`business_mode` 一份合同一个模式；合同订阅的产品与开通项及其 T+N / 保证金条款。
3. **商业条款** Plan → Version → Price → Component → Band；Client Pricing / Partner Share / Platform Service Charge 之外，出款费、FX 点差、争议费、平台周期费同样在这里，`term_type` 由计费事件目录登记。
4. **定价画像**（现 Payment Method Profile）：全局复用、ACTIVE 后冻结；加 `profile_kind` 后出款 / FX / 争议复用同一维度机制。
5. **交易前解析**（A 类查询后冻结）与**交易后命中**（只读历史版本）两个只读接口。
6. **版本生命周期与审计**：ACTIVE 版本永不修改，换版只新建并排期；BCR 行级历史；激活前重新校验。

### 3.2 commercial 不拥有

| 不做 | 谁做 | commercial 只做 |
|---|---|---|
| Party、Client、合作伙伴、OE、Scope、Affiliation | client-master | 经候选接口拿主档事实，只存 ID / entity_no 跨库引用 |
| 费用金额、分润、服务费、到期时间、周期用量 | billing | 给出命中的 Price 与组件 / 阶梯证据，不算数、不记用量 |
| 税额、税务快照、发票 | tax | 业务模式决定税务责任方，`fee_tax_treatment` 决定含税口径 |
| 订单、Session、冻结后的 Order Context | acquiring | 返回解析结果，由收单本地事务冻结 |
| 审批流转、Maker-Checker | workflow-approval | 激活时重新校验并记录最终决定（D13） |
| 法律文档与卖方披露内容 | client-master | 候选自带引用，直接取值（D39） |
| 品牌、实例装配、模块授权 | 运维平面 / Platform | 只读"本实例可售产品集合" |
| 保证金、结算限制的执行 | account / risk / compliance | 只存合同约定的比例与天数 |

### 3.3 原则

- **一份合同 = 一个业务模式**：MoR 与 PSP 并存就签两份。
- **历史按冻结版本读**：换版、到期、Profile 退役都不改变已冻结交易；阻断只由支付前新鲜门禁承担。
- **计算失败就停**：无命中、事实缺失、父链冲突一律失败关闭，不默认通过、不默认零费率。
- **配置在商务、事实在 billing**：阶梯规则、周期定义在商务；本月累计用量、月末返利在 billing；商务永不读 billing。
- **事务公理**：解析与命中只读；写入 Processor 锁一个资源（合同或 Plan），事务内不调外部服务。

## 4. 六层产品定价模型（已定方向）

| 层 | 回答什么 | 例子 | 落在哪 |
|---|---|---|---|
| L0 产品 | 实例卖哪些模块 | ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE（基座）、DATA_ANALYTICS、PLATFORM（基座） | `t_product` |
| L1 能力开通 | 这份合同给这个 Client 开了什么 | CARD_VISA / CARD_MC / CARD_AMEX、APPLE_PAY、GOOGLE_PAY、WALLET_STC、BNPL_TABBY；PAYOUT_LOCAL / PAYOUT_SWIFT / PAYOUT_WALLET；FX_SPOT | `t_product_capability` 目录；`t_agreement_capability` + `_limit` 开通 |
| L2 计费事件 | 什么动作触发收费 | AUTH、CAPTURE / SALE、REFUND、CHARGEBACK、PAYOUT_SUBMITTED、PAYOUT_RETURNED、FX_DEAL、DCC_APPLIED、MONTHLY_CYCLE、SETUP、TOKEN_STORED | `t_product_fee_event`；= `component.trigger_code` 值域 |
| L3 定价画像 | 同一事件里按什么细分价格 | 卡：品牌、资金类型、卡等级、发卡区域、3DS 结果、币种；出款：通道、目的国；FX：币对 | 现 Profile + Condition，加 `profile_kind` |
| L4 价格与组件 | 收多少 | 百分比 + 固定 + 阶梯相加，最低 / 最高，退款时费用退不退 | 现 Price / Component / Band，加 `refund_behavior`、`band_basis` |
| L5 条款附属 | 钱什么时候、怎么给 | 结算 T+N 按支付方式、滚动保证金比例与天数、月最低消费、费用含税口径、BLENDED / IC++ | 结算与保证金挪到 `t_agreement_capability`；`pricing_model`、`fee_tax_treatment` 在 Version |

### 4.1 逐产品的收费构成

- **收单**：MDR（百分比 + 每笔固定，按 L3 细分）；授权费（失败也收）；退款费与原 MDR 退不退（RETURN_ALL / RETURN_PRORATA / KEEP）；争议费按 case 与阶段；跨境附加费、DCC 分成、3DS 费、网络代币费、存卡费按 token 每月。定价模式 MoR 基本 BLENDED，PSP 大商户要 IC++。阶梯分单笔金额与月交易量两种基数。
- **出款**：按通道定价（本地转账固定费、SWIFT 固定费 + 中间行费用承担方 OUR / SHA / BEN、钱包百分比）；跨币种叠加 FX 加价；退回费、加急费；最低出款金额与出款周期是条款不是价格。
- **外汇**：参考汇率之上的点差，按币对与金额档以 bps 计，可叠加固定费；结算换汇、出款换汇、收银台 DCC 三处可配不同点差。
- **争议**：按 case 与阶段收费；赢了是否返还（DISPUTE_WON_REVERSAL）；卡组织拒付率计划费用先不做。
- **平台类**：开户费、月费 / 月最低消费、模块订阅、KYB 复审、风控筛查按笔；周期或一次性事件，无画像维度。

### 4.2 两项已定的取舍

| 事项 | 决定 | 理由 |
|---|---|---|
| IC++ | 本期不做；Version 留 `pricing_model`，只允许 BLENDED | 商务侧改动很小（一个字段 + 一种组件），但 billing 要多一条"估算 → 实际 → 差异"链并对外透传成本行，对账要逐笔映射交换费；MoR 下 Client 接触不到卡组织，只对 PSP 大商户有意义 |
| 月交易量阶梯 | 规则归商务（`band_basis=PERIOD_VOLUME`、周期、档位），用量归 billing；首切片不做 | 配置与事实各在各家；命中接口整表返回 Band，billing 按自己的周期用量选档，商务永不读 billing。递进 / 回溯（RETROACTIVE）用 `band_mode` 区分，回溯需 billing 月末返利事实 |

## 5. 模型改动清单（提案，未进 PDManer）

**新增表**

| 表 | 关键字段 |
|---|---|
| `t_product` | `product_code` UK、`allowed_business_modes` MOR_ONLY / PSP_ONLY / BOTH、`base_product`、`status` |
| `t_product_capability` | `capability_code` UK、`product_code`、`capability_kind` PAYMENT_METHOD / PAYOUT_RAIL / FX / DISPUTE / PERIODIC_SERVICE、`payment_method_code` ?、`status` |
| `t_product_fee_event` | `fee_event_code` UK、`product_code`、允许的 `term_type` 集合、对应 billing 事实类型 |
| `t_agreement_product` | uk(`agreement_id`, `product_code`) |
| `t_agreement_capability` | uk(`agreement_id`, `entity_no`, `capability_code`)、`status` ENABLED / SUSPENDED / DISABLED、结算四字段（从 Price 挪来）、`rolling_reserve_rate` / `_days` |
| `t_agreement_capability_limit` | `limit_code` CURRENCY / CONSUMER_COUNTRY / MAX_TXN_AMOUNT / REQUIRE_3DS，一值一行 |

**既有表字段**

- `t_agreement` + `business_mode` MOR / PSP。
- `t_payment_method_profile` + `profile_kind`；`payment_method_code` 改可空（仅 PAYMENT_METHOD 必填）。`dimension_code` 收口为 Java 枚举 PricingDimension。
- `t_commercial_term_version` + `pricing_model`（本期只 BLENDED）、`fee_tax_treatment` INCLUSIVE / EXCLUSIVE。
- `t_commercial_term_component`：`trigger_code` 值域 = `t_product_fee_event`；+ `refund_behavior`；+ `band_basis` TRANSACTION_AMOUNT / PERIOD_VOLUME；`band_mode` 增 RETROACTIVE；`calculation_base` 收口 GROSS_AMOUNT / NET_AMOUNT / ACTUAL_CLIENT_FEE / FIXED_PER_EVENT。
- `t_commercial_term_price`：移除四个结算字段。
- `t_commercial_term_plan`：`term_type` 值域改由计费事件目录登记；`status` 收口 DRAFT / ACTIVE / RETIRED。
- `t_business_change_record.record_type`：DB 注释收口为本库值集（现 Java 4 值 + PRODUCT、AGREEMENT_CAPABILITY）。

**不改**：Version 的 `effective_from` / `SCHEDULED` 已存在；Profile 冻结规则、精确 OE Price、Band 半开区间不变。

## 6. 三条流程

- **A 交易前解析**（收单支付准备）：acquiring → `POST /checkout-context/v1/resolutions` → client-master 候选（含法律文档、披露、rootVersions）→ commercial 本库只读快照逐候选解析（合同已签且覆盖 OE、业务模式、产品订阅含本次产品、开通项含本次支付方式、按 `contextAsOfTime` 生效的条款版本）→ 事务外 risk-decision 门禁 + client-master verify + 本库根版本重读 → acquiring 本地事务冻结 OE、agreementId、businessMode、Term Version IDs、法律文档引用、`contextAsOfTime`，以及 D02 四项派生（卖方 Party、收单 OE、税务责任方、资金归属账簿主体，由商务按模式给出，收单只冻结）。
- **B 交易后命中**（billing 生成事实）：`POST /commercial-prices/v1/resolutions` 按冻结版本的完整历史内容解析，校验 Version / Plan / Agreement / OE 父链与原适用关系；命中规则"支付方式精确 → 条件等值 → 维度数多者先 → priority_no 小者先"；事实缺失 422 `PRICING_FACTS_INCOMPLETE`、无命中 422 `PRICE_NOT_FOUND`、歧义 409；返回 priceId、profileCode、components / bands；`GET /commercial-prices/v1/{priceId}` 历史回读不重新选价。
- **C 运营写入**：operations-console-bff → commercial Processor（`AbstractLockProcessor` 锁 agreement 或 plan，事务内只写本库 + BCR）→ workflow-approval 记"人已同意" → 排期 / 激活前重新校验 Profile 仍 ACTIVE、OE 仍有效、覆盖完整、Band 连续 → 提交后发布 `AgreementStatusChanged` / `CommercialTermVersionActivated`（只带 ID 与版本）→ acquiring 新鲜门禁、data-service CDC。

## 7. 合同

**进来的**：client-master 候选 + verify（有）、Party Profile 读取（有）、risk-decision 资格（无，现 503 占位）、运营写入命令（无）、审批结果（无）、实例已启用模块集合（无）。

**出去的**：`POST /checkout-context/v1/resolutions`（有，补字段）、`POST /commercial-prices/v1/resolutions`（无）、`GET /commercial-prices/v1/{priceId}`（无）、`GET /commercial/v1/agreement-parties/exists`（有）、`GET /commercial-term-versions/v1/{id}/prices`（无）、两个事件（无）。

## 8. 已定与待定

| 编号 | 问题 | 结论 |
|---|---|---|
| Q1 | 产品目录阶段 0 要不要建 | **已定**：建六层模型六张表；报价后置 |
| Q2 | business_mode 粒度 | **已定**：合同级 |
| Q3 | 标准价目与 Client 价格 | **已定**：模板复制成自己的 Version，不做引用覆盖 |
| Q4 | 出款 / FX / 争议条款归谁 | **已定**：归商务，复用画像机制；IC++ 留字段不做；月量阶梯规则在商务、用量在 billing |
| Q5 | 换版生效 | **已定**：不加字段；用已有 SCHEDULED + `effective_from` |
| Q6 | 签署方式 | **已定**：沿用 `acceptance_method` 两条路径 |
| Q7 | D02 冻结字段清单在哪收口 | **已定**：商务按模式给出四项，收单只冻结 |
| Q9 | 出款 / FX 价格挂 OE 还是挂 Client | **已定**：挂精确 OE，MoR 下钱从 OE 出 |
| Q8 | 首切片 | **已定**：六层目录表 + 合同 / 开通 / 价格写入 + commercial-prices 两接口 + business_mode 与收单冻结；BLENDED + 单笔阶梯；不做 IC++、月量阶梯、报价、标准价目管理、门户价目展示 |

## 9. 核对时发现的现状偏差

1. `t_commercial_term_version` 已有 `effective_from` / `SCHEDULED`，之前"只能立即切版"的判断是错的；缺的是写入方。
2. `t_commercial_term_plan.status` DB 注释写"非固定枚举"且无 Java 枚举。
3. `t_business_change_record.record_type` DB 注释仍列拆分前 CCT 全表白名单，Java 已收口为 4 值。
4. `CheckoutErrorCode` 仍用 `CCT_` 前缀。
5. `trigger_code`、`calculation_base`、`business_calendar_code` 的目录都未落地；工作日日历表不存在（billing 缺口 4）。

## 10. 扩展性评估（2026-10-19，用户确认采用本模型）

**加目录即支持**：新产品（L0）、新支付方式 / 出款通道 / 币对（L1）、新收费动作（L2）、新定价维度（L3）、新组件形态或阶梯基数（L4）、IC++（L5 已留 `pricing_model`，加一种"成本之上加成"组件）。

**会碰模型假设、现在留口子**：

| 场景 | 假设 | 口子 |
|---|---|---|
| 分账、市场平台子商户 | 一条价格只有一个付方一个收方 | 组件保持 `payer_role` / `payee_role` 为角色，不写死 Client → OE |
| 订阅、分期 | 收费事件绑单笔交易 | L2 允许周期型事件；周期调度在 billing |
| 促销价、限时折扣 | 版本整体换版 | 用 SCHEDULED 版本 + `effective_to` 表达限时，不做覆盖层 |
| 承诺量、最低消费、免费额度 | 价格无状态 | 同月量阶梯：规则在商务、累计在 billing |
| 出款 / FX 等 Owner 级产品 | Price 要求精确 OE | 已定：仍挂 OE（MoR 下钱从 OE 出） |
| 报价、自助套餐、A/B 定价 | 无 Quote / 模板版本 | 后置；模板复制成 Version 天然兼容 |
| 多实例价目下发 | 目录每实例一份 | L0–L2 目录随版本由运维平面下发，L3–L5 留实例内 |

真正改骨架的只有多收款方分账和把"周期"引入收费事件；其余都是加行。三条不变量：上层约束下层、历史按冻结版本回读、配置在商务 / 用量在 billing。

**谁维护哪一层**：L0 / L1 目录 / L2 随产品发版；L1 开通项与条款、L4、L5 由运营按 Client × OE 在合同上配置（DRAFT → 审批 → SCHEDULED → ACTIVE，激活后不可改）；L3 画像由运营全局维护，ACTIVE 后冻结。

## 11. 定稿前还要补的事

1. ~~Q2 / Q3 / Q5 / Q6 / Q7 确认；出款 / FX 价格挂 OE 还是挂 Client。~~ 2026-10-19 全部按建议确认。
2. D02 四项冻结字段（卖方 Party、收单 OE、税务责任方、资金归属账簿主体）按 MoR / PSP 各自的取值规则，需与 acquiring、tax、ledger 对齐后写进 D02。
3. 登记决定记录 `agent-docs/decisions/`，并按 `requirements/README.md` 建 `requirements/YYYYMMDD/commercial-<change>/` 需求目录；PDManer 模型（`paytech/commercial/docs/database/commercial.pdma.json` 由生成器产出）与评审 SQL 在实现切片内做。
4. 写入 API 清单与运营控制台页面、Permission / Console Resource、Route 登记；两个事件在业务事件目录登记。
5. 跨系统依赖：acquiring 解析响应与 Order Context 冻结字段；billing X3 接口消费；risk-decision 资格门禁；Platform 提供实例已启用模块集合。
6. 现状偏差（第 9 节）在同一切片内收口：Plan status、BCR record_type 注释、`CCT_` 错误码前缀、trigger / calculation_base / 日历目录。

## 12. 定价 · 成本 · 路由的关系与毛利控制（2026-10-19 讨论，画板七）

- **画像不关联渠道。** 定价画像只向 Client 描述收费场景；渠道成本规则与路由策略各自建条件行，三方共用的只有维度词汇（PAYMENT_METHOD、CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_COUNTRY、ISSUER_REGION、THREE_DS_RESULT、CURRENCY、CONSUMER_COUNTRY、TRANSACTION_TYPE），作为 Foundation 级共享目录。
- **毛利在配置时锁定，路由时只做约束。** ① 价格版本激活或渠道成本变更时，按（OE、币种、画像、事件、金额区间）对 Client 费率公式与每条候选渠道 / MID 的成本公式算预估毛利，生成"场景 → 渠道 → 预估毛利"对照表；② 以不可变快照下发给渠道连接，路由不跨服务调用；③ 路由候选 = 能力过滤 ∩ 快照 allowed（预估毛利 ≥ 按 OE / Client 配置的底线），候选内按健康度、成功率、成本动态排序；无候选失败关闭，或 OE 配置允许兜底渠道时选最小亏损并告警；④ 事后由 billing 的预估 vs 实际成本差异事实与阈值告警、数据服务的 Client × 渠道 × 场景毛利报表兜底。
- **商务侧新增激活校验**：每条 Price 至少一条预估毛利为正的可用渠道，否则不许激活。
- **影响**：商务模型不变，维度枚举改引用共享目录；渠道成本配置按 billing 讨论稿 Q12 迁到渠道连接，并新增路由策略与毛利快照；billing 成本预估改调渠道连接的命中接口；快照下发复用 Platform Snapshot 机制。待讨论归属：对照表的计算 Owner（建议渠道连接拉取商务价格与自有成本计算，商务只提供价格读接口）。

## 13. 商业关系模式：Direct Merchant / ISV / PayFac（2026-10-19 用户要求支持，方案稿）

业务模式（MoR / PSP）回答"谁是卖方、税与资金归谁"；商业关系模式回答"谁和谁签约、谁持 MID、钱从谁手里过、谁定价"。两者正交，用三个属性表达，不新增"模式"枚举：

| 属性 | 落点 | 取值 | 说明 |
|---|---|---|---|
| `fund_mode` | `t_agreement`（CLIENT 合同） | SETTLE_THROUGH_PLATFORM、FEES_ONLY | 资金经平台结算（现状）或收单机构直结商户、平台只收费用 |
| `partner_role` | client-master `t_local_partner` | OE_SUPPLIER、REFERRAL | 提供 OE 的伙伴（现状）或只推荐、拿分成的 ISV |
| 推荐关系 | client-master `t_client_partner_relation` | Client ↔ Partner，REFERRAL，生效区间 | 商务合同签署时冻结 `referral_partner_id`（只存值） |
| `pricing_authority` | `t_agreement` | PLATFORM、PARTNER | 谁决定该 Client 的价目；PARTNER 时价目由 ISV 提出、平台运营录入与审批（Partner 门户仍只读，不开放写） |

模式 = 属性组合：

| 模式 | business_mode | fund_mode | OE 来源 | partner_role | 其他 |
|---|---|---|---|---|---|
| 我们是 PayFac（现状） | MOR / PSP | SETTLE_THROUGH_PLATFORM | PLATFORM / PARTNER | OE_SUPPLIER 或无 | — |
| Direct Merchant | PSP | FEES_ONLY | CLIENT | 无 | MID 与收单合同归 Client 自有 OE，渠道账号凭据由 Client 提供 |
| ISV / Referral | MOR / PSP | 任一 | 任一 | REFERRAL | 分润按 ACTUAL_CLIENT_FEE 比例，或 BUY_RATE_DIFFERENCE |
| Client 是 PayFac / 平台型商户 | MOR / PSP | SETTLE_THROUGH_PLATFORM | 任一 | 任一 | 需"分账 / 子商户"可选模块（见 13.3） |
| 白标代运营 | — | — | — | — | 多实例，不是合同属性 |

约束：MOR 只允许 SETTLE_THROUGH_PLATFORM；FEES_ONLY 只允许 OE 来源 CLIENT。

### 13.1 Direct Merchant 需要什么

- 商务：`fund_mode=FEES_ONLY` 时价格组件只允许 payee = PLATFORM（平台法人收费），不允许 CLIENT_PRINCIPAL 语义；结算四字段为空；保证金为空。
- billing：PAYMENT 事实不生成本金行，只生成费用行，付方 Client、收方平台财务主体，方向为应收；新增事实类型 `FEE_INVOICE_SETTLED`（Client 付清账单）。
- account：Client 不建应付桶，建 `CLIENT_FEE_RECEIVABLE/NONE` 资产账户；账单周期（MONTHLY_CYCLE）汇总应收并开票（tax 开发票）。
- 渠道连接：Channel Account 的凭据来源增加 CLIENT_PROVIDED；路由仍按能力与健康，毛利快照对此类场景只校验网关成本。
- 对账：不收款，只对费用账单回款。

### 13.2 ISV / Referral 需要什么

- client-master：`partner_role`、`t_client_partner_relation`；Partner 入驻流程按角色分支（REFERRAL 不需要 OE 材料）。
- 商务：Partner 合同新增条款类型 `PARTNER_BUY_RATE`（结构同 CLIENT_PRICING，按画像 × 币种）；`PARTNER_SHARE` 的 `calculation_base` 增 BUY_RATE_DIFFERENCE = Client 实收费用 − buy rate 命中金额，负值取 0；`pricing_authority=PARTNER` 的 Client 价目激活前校验不低于 buy rate。
- billing：分润行按新基数计算；推荐伙伴从订单上下文冻结的 `referral_partner_id` 取，不再依赖 OE 来源。
- 收银台品牌：ISV 嵌入走 D21 品牌机制，不在商务。

### 13.3 Client 是 PayFac：独立可选模块"分账 / 子商户"

- 边界：子商户实体与 KYC（client-master + compliance）、订单分账指令（acquiring）、多收款方事实行（billing，付收方角色已泛化）、子商户余额与出款（account / payout）。
- 商务只增一个产品 `SPLIT_PAYMENT`：能力 SPLIT_INSTRUCTION、计费事件 SPLIT_EXECUTED；平台向 Client 收分账费。子商户的价格是 Client 自己的事，不进商务。
- 时间：首切片之后单独立项。

### 13.4 对首切片的影响

- 模型：`t_agreement` + `fund_mode`、`pricing_authority`、`referral_partner_id`；`t_commercial_term_plan.term_type` 允许 PARTNER_BUY_RATE；`calculation_base` 增 BUY_RATE_DIFFERENCE。字段本切片进模型，取值先只允许现状组合（SETTLE_THROUGH_PLATFORM / PLATFORM / 空）。
- 解析响应补 `fundMode`、`referralPartnerId`，收单冻结到 Order Context，billing 据此分支。
- D02 冻结字段定义建议扩展为：卖方 Party、收单 OE、税务责任方、资金归属账簿、资金模式、推荐伙伴。
