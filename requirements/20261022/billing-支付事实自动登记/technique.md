# billing 支付事实自动登记 —— 技术方案

> 状态：阶段 2 评审稿（2026-10-22）。第 1—3 节为现状差异、能力缺口与逐系统影响，待用户确认后进入阶段 3（第 4—6 节）。需求见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。核对依据为 2026-10-22 主工作区源码（路径相对 `paytech/`），不凭旧文档推断。

## 1. 现状差异

### 1.1 链路分段现状

| 段 | 现状 | 差异 |
|---|---|---|
| ① Payment → billing | `PaymentSucceeded` 只在 PAY（`CaptureMode.AUTOMATIC`）成功时由 `PayActionActor.doInTransaction` 同事务登记补偿事件、提交后 `PaymentSucceededHandler` 发布，一次 / 尝试；`aggregateVersion` = 发布时 `t_payment_attempt.version`（可跳号，`strictOrder=false`）；`capturedAmount == amount`（`payCompleted` 把两者都设为 PAY 金额），没有 AUTHORIZE 单独成功事件、没有 CAPTURE 实现。事件目录 `consumers: [acquiring, card]`。billing 无任何 MQ 消费入口（无 `ext/mq`、无 `messaging.consumer` 配置） | billing 缺消费 Handler、消费配置、目录登记；载荷缺渠道交易引用内部 ID 与引用类型 |
| ② billing 事实 → `BillingFactRecorded` | `SubmitBillingFactProcessor` 只放行 `MANUAL_ADJUSTMENT` / `COST_ADJUSTMENT`；`BillingFactRecordedHandler` 发布链路完整，mor-test 已验证 | 缺 AUTO 事实的落库路径（`origin_type = AUTO` 登记即 APPROVED，无 Maker-Checker）、费用计算、T+N、定价侧表写入 |
| ③ account 明细账 | `BillingFactRecordedBalanceHandler` + `FactBalanceMappingRule`：Owner 映射 `CLIENT × {CLIENT_PRINCIPAL, CLIENT_FEE}` → `CLIENT_PAYABLE`、`LOCAL_PARTNER × PARTNER_SHARE` → `PARTNER_PAYABLE`，桶固定 PENDING；平台侧角色 × 组件白名单：`OPERATING_ENTITY × {CLIENT_PRINCIPAL, CLIENT_FEE, PARTNER_SHARE, CHANNEL_COST, SELLER_TAX, PLATFORM_SERVICE_CHARGE}`、`FINANCE_ENTITY × PLATFORM_SERVICE_CHARGE`、`TAX_AUTHORITY × SELLER_TAX`、`CHANNEL × CHANNEL_COST`；其余组合整条拒绝。`maturity_status` 已按 `balance_available_time` + PENDING 贷方判定，`BalanceMaturityJobHandler` 已有 | 按 Q1 行结构，`CHANNEL` 作为 `CLIENT_PRINCIPAL` / `CLIENT_FEE` 付方会被拒 → 白名单加两项。其余不变 |
| ④ ledger 总账 | `AccountPostedPostingHandler` 严格有序消费、经 billing-api 回读事实；`TransactionPostingServiceImpl` 对 PAYMENT 事实按 `STANDARD_TRANSACTION` 三个指令类型计划：`PAYMENT_SUCCEEDED`（OE 账簿，6 行）、`PLATFORM_SERVICE_CHARGE_PARTNER`（OE 账簿：Dr `PLATFORM_SERVICE_FEE_EXPENSE` / Cr `PLATFORM_SERVICE_PAYABLE`@FE）、`PLATFORM_SERVICE_CHARGE_PLATFORM`（**FE 账簿**：Dr `PLATFORM_SERVICE_RECEIVABLE`@OE / Cr `PLATFORM_SERVICE_REVENUE`）；金额只按来源取（`GROSS_AMOUNT` = 头毛额，其余 = 同组件行金额之和），**不看行的付方 / 收方**；来源缺失时必需行拒绝、非必需行跳过。需要：`CLEARING_ACCOUNT` 引用唯一、`SELLER_TAX` 行收方唯一、服务费有金额时头 `finance_entity_id` 非空且该 FE 的币种账簿 OPEN、OE 账簿 OPEN 且有财务日历与 OPEN 期间 | **无规则缺口**（需求稿 1 节"没有服务费规则行"为误判，已更正）。缺口只在输入：`finance_entity_id` 的来源 |
| ⑤ reconciliation 预期明细 | `BillingFactRecordedPlatformTxnHandler` 只处理 APPROVED 的 PAYMENT / REFUND；要求引用 `CLEARING_ACCOUNT`（refId 为主键）、`CHANNEL_TRANSACTION_REF`（refId 非空且 `externalReferenceType` / `externalTransactionId` 非空）、`PAYMENT_ATTEMPT`；预估费用取 `CHANNEL_COST` 行 | 无代码改动；对事实引用提出硬要求（见 2.2） |

### 1.2 上游输入现状（按 billing 需要的输入逐项）

| 输入 | 来源 | 现状 | 差异 / 风险 |
|---|---|---|---|
| 订单冻结的合同 / 条款版本、Local Partner、服务费签约 Party、税务快照 ID、消费者国家、商业时点 | acquiring `t_order_context`（`order_id` 唯一键 `uk_order_context`） | 表与列齐全；**但生产装配的 `CheckoutCommercialPort` 是固定 Mock**（`CustomerCheckoutIntegrationConfiguration` 返回 `AE001` / PLATFORM / `clientPricingVersionId=104` / `clientAgreementId=105`，Partner 字段永远为空；`pom.xml` 注明 client-contract-api 已删、无 commercial 客户端；台账 329 行"acquiring 未消费新字段 / 未冻结"）。acquiring 无内部 HTTP 接口，单监听 `127.0.0.1:18102` `/commerce` | **硬前置 P1**：mor-test 上所有订单的版本 ID 都是假值，拿去 commercial 命中必 404。真实接入 commercial `POST /checkout-context/v1/resolutions` 又被 `CheckoutResolutionProcessor` 的 Risk 资格门禁 503 占位挡住（台账 329 行），而 AGENTS.md 禁止伪造默认通过 |
| Client 价格命中 | commercial `POST /commercial-prices/v1/resolutions` | 已实现。必填 `termVersionId`、`entityNo`、`agreementId`、`contextAsOfTime`、`termType`、`currency`、`paymentFactId`、`paymentFacts`；`clientId` / `localPartnerId` 二选一；校验版本存在、合同 / 条款类型 / Owner / OE 父链、时点有效，否则 404 / 409；返回唯一价格 + 组件（`componentType`、`calculationBase`、`rate`、`fixedAmount`、`minAmount`、`maxAmount`、阶梯）+ 结算四字段。commercial 的 `clientTerms.versionId` / `agreementId` 与 acquiring 列**同一命名空间** | 无差异（前提是 acquiring 存的是真值） |
| Partner 分润 / 平台服务费命中 | 同一接口，`termType = PARTNER_SHARE / PLATFORM_SERVICE_CHARGE`，`agreementId` = **Partner 合同 ID**（`partner_agreement_id`），`localPartnerId` 必填、`clientId` 为空；Partner 方案 `entityNo` 为空，须存在 `t_agreement_operating_entity(partnerAgreementId, entityNo)` | 命中路径无条款类型分支，有 `test_partnerShareTerm` 覆盖。`CalculationBase` 取值：`GROSS_AMOUNT`、`NET_AMOUNT`、`ACTUAL_CLIENT_FEE`、`FIXED_PER_EVENT`、`BUY_RATE_DIFFERENCE`（仅 PARTNER_SHARE） | 分润"手续费 × 80%"可配（`PERCENTAGE` + `ACTUAL_CLIENT_FEE`）；**服务费"分润 × 25%"不可配**——没有"分润金额"基数（Q13） |
| 渠道成本命中 | channel-connector `POST /channel-connector/v1/channel-costs/resolutions`（`channelId`、`midId`、`entityNo`、`countryCode`、`currency`、`paymentMethodCode`、`actionType`、`asOfTime`）→ 方案版本、范围、组件（`calculationBase`、`rateValue`、`fixedAmount`、上下限、`roundingMode`）；未命中 400 `CHANNEL_COST_NOT_RESOLVED` | 已实现；billing `ChannelCostCalculator`（组件 + 基数 + 精度 → 逐组件金额）已有 | 无差异；`countryCode` 取订单上下文 `consumer_country_code`（阶段 3 确认语义） |
| 清算账户 | reconciliation `POST /reconciliation/v1/clearing-accounts/resolutions`（`midId` + `currency`）→ `clearingAccountId`、`channelCode`、`entityNo`、`currency`；未归属 400 | 已实现 | 返回的 `entityNo` 可用于与事件 `entityNo` 交叉校验 |
| 工作日日历 | billing `t_business_calendar` / `_day`，初始化 `DEFAULT`（UTC、周一至周五） | 已有 | 命中价格 `businessCalendarCode` 必须在本表存在，否则 Fail-Closed |
| 税务快照 | tax 空壳 | 无 | 决定第 10 条：无快照 → 无税行；有 ID 读不到 → 失败重试（本期不建客户端，有 ID 即视为读不到） |
| 平台 Finance Entity | ledger `t_finance_entity.legal_party_id`（= CCT Party ID）；查询接口 `GET /finance-entities/v1` 只能按状态分页 | ledger 过账要求事实头 `finance_entity_id`；billing 只有订单上下文的 `platform_service_party_id` | **缺口 G1**：Party → Finance Entity 的映射入口（Q14） |
| 卡属性（`cardBrand` / `fundingType` / `cardLevel`） | `PaymentSucceeded` v2 末尾三个可空字段（card BIN 需求，mor-test 2026-10-22 18:00Z 已上线，字段顺序已由该会话确认） | 已有 | billing 写 `t_billing_fact_pricing.card_network / funding_type / card_level`，并作为命中维度传 commercial（为空不传） |
| `channelTransactionRefId` / `referenceType` | Payment `t_channel_transaction_ref`（`channelTransactionRefId`、`paymentActionId`、`referenceType = ActionType`、`externalTransactionId`…），`PaymentSucceededHandler.rebuild` 已持有该 `ref` 对象 | 载荷只带 `channelTransactionRef`（外部交易号字符串） | 追加 `channelTransactionRefId`、`channelReferenceType` 两个可空字段即可，本地改动；`externalTransactionId` 复用现有 `channelTransactionRef`，不重复 |
| `orderContextId` | Payment 没有；intent 只有 `sourceId`（= orderId）与三个定价版本 ID 副本 | — | **不再需要**：acquiring 读接口按 `orderId` 查即可（`uk_order_context`） |

### 1.3 需求稿修正

1. 第 1 节"ledger 没有 `PLATFORM_SERVICE_CHARGE` 规则行"**不成立**：规则行 5209—5212 与两个指令类型已存在且与 Q2 口径一致；ledger 本期**无规则变更、无 DML**。
2. 第 4 节范围 1 的 Payment 字段改为只追加 `channelTransactionRefId`、`channelReferenceType`（可空，接在 `cardLevel` 之后，`schemaVersion` 仍为 2 以与 card 需求做法一致；对账决定第 5 条"Schema 版本随实现递增"的字面要求在阶段 3 一并确认），不加 `orderContextId`。
3. 第 4 节范围 2 的 acquiring 接口按 `orderId` 查（也接受 `orderContextId`）。
4. 新增硬前置 P1 与缺口 G1、Q13—Q15（见第 3 节）。

## 2. 能力缺口与依赖

### 2.1 本需求内可闭合的缺口

| 编号 | 缺口 | 所属 | 大小 |
|---|---|---|---|
| C1 | billing 消费 `PaymentSucceeded`：目录 consumers 加 `billing`（含 `tools/arch-rules` R12 fixture 同步）、`messaging.consumer.service=billing` + `messaging.consumers.payment.partitions=4`（app 与 test 配置）、`@DomainEventHandler(eventType="PaymentSucceeded", strictOrder=false, transaction=HANDLER_MANAGED)`、本地 JSON 解析（payment-api 无载荷类，参照 acquiring `PaymentSucceededMessageConverter` 校验 `sourceService=payment`）；队列 `billing.payment.hash / p0..p3 / dlx / dlq` 由应用启动自动声明，Broker 插件已启用 | billing | 中 |
| C2 | AUTO 事实落库：`PAYMENT` 事实头（`origin_type=AUTO`、`APPROVED`、`business_time=paymentSucceededTime`、`balance_available_time`、`finance_entity_id`）、`t_billing_fact_pricing`、引用 9 类、明细行（证据列 + `source_evidence`）、`BILLING_FACT_RECORDED` 补偿事件同事务；幂等 `source_event_key` = 事件键 + 来源引用锁 | billing | 大 |
| C3 | 费用计算：组件 → 金额（`PERCENTAGE` / `FIXED` / 阶梯 / 上下限 / 舍入）对 `CLIENT_FEE`、`PARTNER_SHARE`（基数 `ACTUAL_CLIENT_FEE`）、`PLATFORM_SERVICE_CHARGE`（基数按 Q13）；`CLIENT_PRINCIPAL = gross − CLIENT_FEE`；`CHANNEL_COST` 复用 `ChannelCostCalculator`；`NET_AMOUNT` 定义为 `gross − CLIENT_FEE`（阶段 3 定稿） | billing | 中 |
| C4 | T+N：`business_time` → 结算时区 → 与 `cutoffSecondOfDay` 比较定起算日 → 按日历加 N 个工作日 → 该日 23:59:59 结算时区 → UTC | billing | 小 |
| C5 | 四个上游客户端（acquiring、commercial、channel-connector、reconciliation）+ Compose / Nacos URL 变量（`BILLING_ACQUIRING_SERVICE_URL=http://acquiring-service:18102/commerce`、`BILLING_COMMERCIAL_SERVICE_URL=…:18108`、`BILLING_CHANNEL_CONNECTOR_SERVICE_URL=…:18110`、`BILLING_RECONCILIATION_SERVICE_URL=…:18115`）+ 测试 DTO Mock | billing、环境 | 中 |
| C6 | Payment 载荷追加两个可空字段；`PaymentSucceededHandler` 已持有 `ref`，改动局限于 `PaymentSucceededPayload` / `PaymentSucceededResult` / Handler；acquiring、card 忽略新增字段 | payment | 小 |
| C7 | acquiring 私网只读接口 `GET /internal/v1/order-contexts?orderId=`（前缀与暴露规则阶段 3 定）；返回 `t_order_context` 全部冻结字段 + 订单 `clientId` / `entityNo` / `localPartnerId` / `amount` / `currency` | acquiring | 小 |
| C8 | account 白名单：`CHANNEL` 允许作为 `CLIENT_PRINCIPAL` / `CLIENT_FEE` 的付方（不产生 Owner 流水）；现有 `CLIENT × CLIENT_FEE` 映射保留给人工调整 | account | 很小 |
| G1 | Party → Finance Entity 映射（Q14） | ledger（+ billing 调用） | 小 |

### 2.2 事实必须满足的下游合同（阶段 3 的设计约束）

| 下游 | 要求 |
|---|---|
| account | 头 `entityNo`、`currency`、`lines` 必填，行按行号升序；每行付方 / 收方必填；Owner 角色只能出现在白名单组合 |
| ledger | `CLEARING_ACCOUNT` 引用唯一；`SELLER_TAX` 行收方唯一；服务费金额 > 0 时 `finance_entity_id` 非空且 FE 币种账簿 OPEN；`business_time` 非空；OE 账簿 OPEN、有财务日历、有可用 OPEN 期间 |
| reconciliation | 引用 `CLEARING_ACCOUNT`（主键）、`CHANNEL_TRANSACTION_REF`（refId + `externalReferenceType` + `externalTransactionId` 三者非空）、`PAYMENT_ATTEMPT`；`CHANNEL_COST` 行作预估费用 |

### 2.3 本需求外的前置与依赖

| 编号 | 项 | 影响 | 建议 |
|---|---|---|---|
| **P1** | acquiring 的商业事实是固定 Mock；真实接 commercial `checkout-context` 又依赖 Risk 资格合同（503 占位） | 在 P1 完成前，mor-test 上经 Hosted Checkout 产生的订单无法命中价格，billing 事实会停在重试；**需求 B 的代码与 auto 测试不受影响**（上游全部是 DTO Mock） | 另立需求 D"acquiring 接入 commercial Checkout 解析并冻结"（含 Risk 资格合同或经批准的过渡方案），排在 B 之后；B 的 mor-test 验收按 Q15 调整 |
| P2 | card BIN 需求的 `PaymentSucceeded` 字段已上线 mor-test，代码未提交 | 本需求 payment 改动必须基于含该改动的 `PaymentSucceededPayload` | 阶段 4 前取得该会话提交号；字段接在 `cardLevel` 之后 |
| P3 | mor-test 数据：OE `AE001` 账簿、`PLATFORM_HQ` FE 账簿（占位 Party `880000000701`）、清算账户 + MID、渠道成本方案、commercial 真实合同 / 条款版本 / 价格 | 缺任一项事实停在重试 | 阶段 6 列发布前数据清单 |
| P4 | tax 空壳 | 有 `finance_tax_snapshot_id` 的订单无法入账 | 本期 Mock 订单上下文不带快照 ID；真实订单接入时若 acquiring 冻结了快照 ID 则必须先有 tax 读接口 |

## 3. 逐系统影响

| 系统 | 改动 | 表 / DML | 配置 / 注册 | 测试 |
|---|---|---|---|---|
| payment | `PaymentSucceededPayload` / `Result` / Handler 追加 `channelTransactionRefId`、`channelReferenceType` | 无 | 事件目录 `consumers` 加 billing | 既有发布测试加字段断言 |
| acquiring | 新内部只读接口（api 模块 `@HttpExchange` + Controller + Processor 查询）；`web.logging` 前缀 | 无 | 内部前缀暴露规则（不经 Gateway） | 存在 / 不存在 |
| billing | Handler、事件读取、4 个客户端、`PaymentFactAssembler`（费用、T+N、引用、定价侧表）、AUTO 落库 Service、`BillingFactInvariants` 的 PAYMENT 分支 | 无结构变更（`t_billing_fact_pricing` 列已齐；阶段 3 核对 `authentication_class` 等暂无来源的列保持 NULL） | `messaging.consumer*`、4 个 URL、测试 DTO 端点 | 消费 → 事实 → 发布的场景矩阵（正常 / Partner / 幂等 / 并发 / 各上游缺失 / T+N 边界） |
| account | `FactBalanceMappingRule` 白名单 +2 | 无 | 无 | PAYMENT 事实消费用例（本金贷、分润贷、成熟 PENDING → AVAILABLE） |
| ledger | 无规则变更；G1 新增按 Party 解析 Finance Entity 的只读接口（Q14 方案 a） | 无 | 无 | 接口用例；PAYMENT 事实（含服务费）过账用例已有则复用 |
| reconciliation | 无 | 无 | 无 | 验证预期明细生成（mor-test） |
| foundation / tools | 事件目录、arch-rules R12 fixture | — | — | 目录测试 |
| test-environment | billing 4 个 URL 变量；五个镜像（payment、acquiring、billing、account、ledger） | ledger 无 DML（修正） | — | 阶段 6 |
| 文档 | 需求稿修正、CURRENT_IMPLEMENTATION、design-discuss/billing 第 9 节缺口状态、requirements-site FIN 口径 | — | — | `npm --prefix requirements-site test` |

### 3.1 阶段 2 新增待决问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q13 | 平台服务费的计算基数。commercial `CalculationBase` 没有"分润金额"，"分润 × 25%"配不出来 | **本期不新增基数**：billing 支持 commercial 返回的 `GROSS_AMOUNT` / `NET_AMOUNT` / `ACTUAL_CLIENT_FEE` / `FIXED_PER_EVENT`，服务费按运营在 PLATFORM_SERVICE_CHARGE 条款里配置的基数算（例如毛额 2% 或手续费 20%）；`BUY_RATE_DIFFERENCE` 本期 Fail-Closed。讨论稿的"分润 × 25%"只是示例，若业务确需按分润计价，另在 commercial 增 `PARTNER_SHARE_AMOUNT` 基数（commercial + billing 顺序计算），不阻塞本期 |
| Q14 | Party → Finance Entity 映射入口 | **方案 a**：ledger 新增私网只读 `GET /finance-entities/v1/resolutions?legalPartyId=`（ACTIVE 唯一，否则 404 / 409），billing 事务外调用并写事实头 `finance_entity_id`；account 载荷已有该字段。方案 b（billing 只存 Party 引用、ledger 过账时映射）要改 ledger 过账与 account 载荷语义，不选 |
| Q15 | AC-11 mor-test 验收方式（P1 未完成时经 Hosted Checkout 产生的订单带假版本 ID） | 把 AC-11 改为：**用人工构造的订单上下文（真实 commercial 合同 / 版本 ID）+ Payment 内部发布一笔 `PaymentSucceeded`** 验收 billing → account → ledger → reconciliation 全链路；"经 Hosted Checkout 真实入口"的端到端验收移到需求 D。合成数据事后清理 |
| Q16 | `PaymentSucceeded` 追加字段是否升 `schemaVersion=3` | 建议**仍为 v2**（可空末尾追加，与 card 需求、6b 会话实践一致；消费方 acquiring / card 按字段名读取不受影响）；对账决定第 5 条措辞在阶段 3 以本结论做一条修订说明 |


## 4. 方案与接口（阶段 3 评审稿）

> 状态：2026-10-22 评审稿。阶段 2 已确认（Q13—Q16 按建议）。本节把阶段 1 范围与阶段 2 差异落成可实现的合同、规则与边界；需要用户拍板的设计选择列在 4.9（S1—S10）。五个库均无结构变更。

### 4.1 调用链（一笔 PAY 成功）

```
payment  PayActionActor（事务内登记）→ PaymentSucceededHandler（提交后发布 payment.events，v2 末尾 +2 可空字段）
   │ billing.payment.hash → billing.payment.p0..p3（HANDLER_MANAGED）
billing  OrderPaymentSucceededBillingHandler（消费 acquiring.events；见 4.3 修订）
   ├─ 事务外：① findBySource(payment, PAYMENT_ATTEMPT:<attemptId>) 已有 → 结束
   │          ② （已并入事件：订单与冻结商业字段来自 OrderPaymentSucceeded 载荷）
   │          ③ commercial POST /commercial-prices/v1/resolutions（CLIENT_PRICING）
   │          ④ 有 Local Partner：commercial 同接口 ×2（PARTNER_SHARE、PLATFORM_SERVICE_CHARGE）+ ledger GET /finance-entities/v1/by-party/{partyId}
   │          ⑤ channel-connector POST /channel-connector/v1/channel-costs/resolutions
   │          ⑥ reconciliation POST /reconciliation/v1/clearing-accounts/resolutions
   │          ⑦ 纯内存：交叉校验 → 组件计算 → T+N → 组装 BillingFactContext（头 / 定价 / 引用 / 行）+ 来源载荷 Hash
   └─ 本地短事务（BillingFactService.recordAutoFact）：insert 头(APPROVED, v1) + 定价 + 引用 + 行 + 登记 BILLING_FACT_RECORDED
   → 提交后 BillingFactRecordedHandler 发布（既有）
account  BillingFactRecordedBalanceHandler（白名单 +2）→ AccountPosted（既有）
ledger   AccountPostedPostingHandler → STANDARD_TRANSACTION 三指令（既有，无改动）
recon    BillingFactRecordedPlatformTxnHandler → 预期清算明细（既有，无改动）
```

事务公理：②—⑦ 全部在事务外；本地事务只写 `mor_billing`；发布经补偿事件在提交后进行。Handler 不加 Redis 锁：同一事件的并发由 `uk_fact_source` 唯一键兜底（`DuplicateKeyException` → `ConcurrentRequestException` → messaging 退避重投 → 下次步骤 ① 命中返回）。

### 4.2 Payment：`PaymentSucceeded` v2 字段追加

| 字段 | 类型 | 取值 | 位置 |
|---|---|---|---|
| `channelTransactionRefId` | 十进制字符串，可空 | `t_channel_transaction_ref.channel_transaction_ref_id`（`PaymentSucceededHandler.result()` 已持有 `ref`） | `cardLevel` 之后 |
| `channelReferenceType` | 字符串，可空 | `ref.getReferenceType().getCode()`（PAY 成功恒为 `PAY`） | 其后 |

- `PaymentSucceededResult` 增加两个字段；`PaymentSucceededPayload.json()` 在 `cardLevel` 之后 `put`；`schemaVersion` 仍为 2（Q16）。`fields()`（v1 前缀）不变。
- 现有 `channelTransactionRef`（外部交易号）保留，billing 把它作为 `CHANNEL_TRANSACTION_REF` 引用的 `external_transaction_id`。
- `events.yaml` `PaymentSucceeded.consumers: [acquiring, card, billing]`；`tools/arch-rules` R12 fixture 同步。
- 文档：对账决定第 5 条"Schema 版本随实现递增"补一句修订：可空末尾追加不升版本，与 card BIN 需求做法一致。
- acquiring / card 消费方按字段名读取，不受影响（card 需求已验证同类追加）。

### 4.3 acquiring：发布 `OrderPaymentSucceeded` 事件（阶段 4 修订，S13）

> 阶段 3 原方案"billing 调 acquiring 只读接口"违反 D08 / arch-rules R3（领域层 `-core` 不得依赖流程层 `-api`，领域层只经事件感知交易），2026-10-22 用户确认改为 acquiring 发事件。T0 已实现的 `OrderContextQueryApi` / Controller / `OrderContextQueryService` / `ORDER_CONTEXT_NOT_FOUND` 删除；`OrderContextRepository.findCommercialContext` 与 `OrderCommercialContextView`（三表联查）保留，供事件 Handler 重建载荷。

- **事件**：`eventType = OrderPaymentSucceeded`，`schemaVersion = 1`，`producer = acquiring`，`aggregateType = ORDER`，`aggregateId = orderId`，`aggregateVersion = 本次事务提交后的 t_commerce_order.version`（一个订单只成功一次，版本可跳号，`strictOrder = false`），`consumers = [billing]`。发布到 `acquiring.events`（acquiring 增加 `messaging.producer.service: acquiring`、`publisher.unroutable-policy: FAIL`）。`PaymentSucceeded` 的 consumers 回到 `[acquiring, card]`。
- **登记点**：`ApplyPaymentSucceededProcessor` 的 APPLIED 路径——`OrderPaymentService.applySucceeded(context)` 同一本地事务内 `compensationEventRegistrar.register(OrderPaymentSucceededHandler.buildEvent(orderId, paymentFacts))`。已关闭订单（`RECORDED_FOR_CLOSED_ORDER`）与重复事件不登记（已关闭订单的资金处置本就是人工项）。
- **补偿 Handler**：`ext/job/OrderPaymentSucceededHandler implements CompensationEventHandler`（acquiring 既有位置），事件类型 `ORDER_PAYMENT_SUCCEEDED`，`eventKey = ORDER:<orderId>:OrderPaymentSucceeded:<version>`（`AggregateKey.eventKey`）。`payloadJson` 只存 acquiring 没有持久化来源的支付事实（paymentMethodCode、channelId、channelAccountId、midId、channelTransactionRef、channelTransactionRefId、channelReferenceType、cardBrand、fundingType、cardLevel、paymentSucceededTime、capturedAmount）；订单与冻结商业字段在执行时经 `OrderContextRepository.findCommercialContext(orderId)` 回读，`paymentIntentId` / `paymentAttemptId` 取 `t_order_payment_projection`（successfulAttemptId 必须等于登记的尝试，否则终态失败）。发布策略同 billing `BillingFactRecordedHandler`。
- **`PaymentSucceededMessageConverter`** 增读上述支付事实字段（全部可空，老报文缺字段为 null），放进 `ApplyPaymentSucceededRequest` 并随 Context 进入 `applySucceeded`。
- **Payload（固定顺序；ID / 金额十进制字符串，时间 RFC 3339 UTC，缺失 JSON null）**：

| 组 | 字段 |
|---|---|
| 订单 | `orderId`、`orderContextId`、`clientId`、`entityNo`、`entitySourceType`、`localPartnerId`、`industryCode`、`mccCode`、`orderAmount`、`currency`、`commercialContextTime`、`clientAgreementId`、`clientPricingVersionId`、`partnerAgreementId`、`partnerAgreementOperatingEntityId`、`partnerShareVersionId`、`platformChargeVersionId`、`platformServicePartyId`、`financeTaxSnapshotId`、`consumerCountryCode` |
| 支付 | `paymentIntentId`、`paymentAttemptId`、`capturedAmount`、`paymentSucceededTime`、`paymentMethodCode`、`channelId`、`channelAccountId`、`midId`、`channelTransactionRef`、`channelTransactionRefId`、`channelReferenceType`、`cardBrand`、`fundingType`、`cardLevel` |

- **实现定稿（T0，2026-10-22）**：`payloadJson` 存 13 项支付事实（上述 12 项 + `paymentAttemptId`，Handler 用它核对投影的 `successfulAttemptId`）；发布时必需 `paymentAttemptId`、`capturedAmount`、`paymentSucceededTime`、`paymentMethodCode`、`channelId`、`channelAccountId`、`midId`、`channelTransactionRef`（缺失为终态失败 `ORDER_PAYMENT_EVENT_INVALID`），`channelTransactionRefId`、`channelReferenceType`、卡属性三项可空（追加前的旧 PaymentSucceeded 报文也能发布，是否因缺渠道引用 ID 拒绝由 billing 决定）；订单 / 上下文缺失、上下文不完整、投影无成功尝试或尝试不符为终态失败 `ORDER_PAYMENT_NOT_PUBLISHABLE`。`ApplyPaymentSucceededRequest` 的三个支付字段并入 `OrderPaymentFacts`（内部请求，无其他调用方）。acquiring 配置新增：`spring.rabbitmq.publisher-confirm-type: correlated` / `publisher-returns` / `template.mandatory`、`messaging.producer.service: acquiring`、`messaging.publisher.unroutable-policy: FAIL`、`paytech.instance-id`、`acquiring.event.source-service / instance-id`（`AcquiringEventProperties`）。
- **发布窗口风险（阶段 6 记录，评审项 5）**：acquiring 以整段 `PaymentSucceeded` 载荷 Hash 做幂等。若 payment 旧镜像发布的事件在 acquiring 消费失败后、由新镜像重发（多两个字段），同键不同 Hash 会被 acquiring 拒绝进 DLQ、订单停在 OPEN；概率低（card BIN 追加字段时同样存在），发布时 payment 与 acquiring 同批替换并在切换前后检查 `acquiring.payment.dlq`。
- **发布顺序约束（阶段 6）**：`unroutable-policy = FAIL` 下，billing 未声明 `billing.acquiring.*` 队列前，acquiring 的 `ORDER_PAYMENT_SUCCEEDED` 任务会重试约 30 分钟后 FAILED；**billing 先于（或同批先启动于）acquiring 部署**，否则需人工重开任务。
- **测试**：acquiring 新增 Handler 入口测试（发布到真实本地 `acquiring.events`，临时观察队列断言信封与载荷顺序；事实不存在 / 成功尝试不符为终态失败）；`ApplyPaymentSucceeded` 消费测试断言 APPLIED 路径登记了补偿事件且 payloadJson 只含支付事实，CLOSED / 重复路径不登记；转换器对缺字段老报文的兼容。
- **billing 侧**：消费 `acquiring.events`（`messaging.consumers.acquiring.partitions: 4`，队列 `billing.acquiring.*`），`ext/mq/OrderPaymentSucceededBillingHandler` + `OrderPaymentSucceededEventReader` → billing 本地输入模型；事实头 `source_system = acquiring`、`source_event_key = PAYMENT_ATTEMPT:<paymentAttemptId>`、`source_version` = 信封 `aggregateVersion`、`source_payload_hash` = 信封 Payload 的 RFC 8785 SHA-256；`PAYMENT_ATTEMPT` 等引用、交叉校验（`capturedAmount == orderAmount`、`entityNo` / `currency` 与清算账户一致）照 4.5 / 4.8，不再有订单上下文 404 的重试情形。

### 4.4 ledger：按 Party 解析 Finance Entity

- `LedgerSetupQueryApi` 新增 `@GetExchange("/finance-entities/v1/by-party/{legalPartyId}") FinanceEntityResponseBody getFinanceEntityByParty(@PathVariable long legalPartyId)`；复用既有响应体。只返回 `ACTIVE`；不存在或 `CLOSED` → 404（`LedgerSetupErrorCode` 新增 `FINANCE_ENTITY_NOT_FOUND`，若已有同义码则复用）。
- `FinanceEntityRepository.findByLegalPartyId`（`uk_fin_entity_party` 唯一）。只读，无锁、无事务。

### 4.5 billing：消费入口与装配

**包与类（billing-core）**

| 位置 | 类 | 职责 |
|---|---|---|
| `ext/mq` | `OrderPaymentSucceededBillingHandler`（S13） | `@DomainEventHandler(eventType = "OrderPaymentSucceeded", strictOrder = false, transaction = HANDLER_MANAGED)`；读信封 → 调 Processor；异常分类见 4.8 |
| `ext/mq` | `OrderPaymentSucceededEventReader` | 校验 `sourceService == acquiring`、`schemaVersion == 1`、`aggregateType == ORDER`、`aggregateId == orderId`、必填字段与格式（金额 / ID / 时间），计算载荷 RFC 8785 Hash，产出不可变 `OrderPaymentSucceededFact`（billing 本地模型，含 `OrderCommercialContext`；不依赖 acquiring-api） |
| `service/processor` | `RecordPaymentFactProcessor extends AbstractProcessor<OrderPaymentSucceededFact, BillingFactView>` | 4.1 的 ①、③—⑦ + 落库；不加锁（见 4.1）；`validate` 校验渠道交易引用三元组 |
| `integration/commercial` `/channelconnector` `/reconciliation` `/ledger` | `CommercialPriceClient`、`ChannelCostClient`、`ClearingAccountClient`、`FinanceEntityClient`（interface + Impl，经 `@HttpExchange` 代理；无 acquiring 客户端，S13） | 事务外读取；上游 4xx 在 Impl 内转为 `ErrorException`（重试），5xx / 超时本就是 `ErrorException` |
| `domainservice/service` | `PaymentFactAssembler`、`CommercialPriceCalculator`（interface + Impl） | 纯内存：组件计算、行构造、引用、定价侧表；交叉校验在 Processor |
| `domainservice/service` | `SettlementTimeCalculator`（interface + Impl） | T+N（读 `t_business_calendar(_day)`，只读） |
| `domainservice/service` | `BillingFactService.recordAutoFact(BillingFactContext)` | 新方法：同事务 insert 头 / 定价 / 引用 / 行 + `compensationEventRegistrar.register(BillingFactRecordedHandler.buildEvent(id, 1))`；`DuplicateKeyException` → `ConcurrentRequestException` |
| `domainservice/validator/fact` | `BillingFactInvariants.checkAutoPayment(payload)` | PAYMENT 的目录约束（见 4.6）；与 `checkManualRegistration` 并列 |
| `domainservice/repository` | `BillingFactPricingRepository.insert` 已有；`BillingFactRepository.findBySource` 已有 | — |

**事实头**

| 列 | 取值 |
|---|---|
| `fact_type` / `origin_type` / `status` / `fact_version` | `PAYMENT` / `AUTO` / `APPROVED` / 1 |
| `source_system` / `source_event_key` / `source_version` | `acquiring`（S13）/ `PAYMENT_ATTEMPT:<paymentAttemptId>` / 信封 `aggregateVersion`（订单版本，S3） |
| `source_payload_hash` | 信封 Payload JSON 的 RFC 8785 SHA-256（Foundation 既有工具）；同键不同 Hash → 业务拒绝（进 DLQ） |
| `client_id` / `entity_no` / `currency` | 事件订单组（与清算账户的 OE / 币种交叉校验一致） |
| `local_partner_id` / `finance_entity_id` | 事件订单组 `localPartnerId` / ledger 按 `platformServicePartyId` 解析；无合作方两者皆空 |
| `gross_amount` | 事件 `capturedAmount` |
| `business_time` | 事件 `paymentSucceededTime` |
| `balance_available_time` | 4.7 |
| `approved_time` | 登记时刻（= 事件 `occurredAt` 的替代：`recordedTime` 语义为"批准 / 登记时间"） |
| `maker_id` / `checker_id` / `parent_fact_id` / `supersedes_fact_id` | 空 |

**定价侧表 `t_billing_fact_pricing`**：`client_agreement_id`、`partner_agreement_oe_id`（= `partnerAgreementOperatingEntityId`）、`commercial_context_time`、`consumer_country_code`、`industry_code`、`mcc_code` ← 订单上下文；`payment_method` ← 事件 `paymentMethodCode`；`card_network` / `funding_type` / `card_level` ← 事件 `cardBrand` / `fundingType` / `cardLevel`（可空）；`authentication_class` 空（无来源）；`commercial_price_id` ← Client 价格 `priceId`；结算四列 ← Client 价格 `settlement`。

**引用 `t_billing_fact_ref`**（`ref_id` 十进制字符串）

| ref_type | ref_id | 备注 |
|---|---|---|
| `ORDER` / `ORDER_CONTEXT` / `PAYMENT_INTENT` / `PAYMENT_ATTEMPT` | 事件 `orderId` / 上下文 `orderContextId` / 事件 `paymentIntentId` / `paymentAttemptId` | reconciliation 要求 `PAYMENT_ATTEMPT` |
| `PAYMENT_CHANNEL_ACCOUNT` / `MID` | 事件 `channelAccountId` / `midId` | |
| `CLEARING_ACCOUNT` | 清算账户解析 `clearingAccountId` | ledger / reconciliation 都要求唯一 |
| `CHANNEL_TRANSACTION_REF` | `channelTransactionRefId`；`external_reference_type` = `channelReferenceType`；`external_transaction_id` = `channelTransactionRef` | 三者任一为空 → 拒绝进 DLQ（S4） |
| `TAX_SNAPSHOT` | 订单上下文 `financeTaxSnapshotId`（该列 NOT NULL，恒有值） | 只记引用，不生成 `SELLER_TAX` 行、不读 tax、不重试（S11，2026-10-22 用户确认） |

### 4.6 明细行结构与计算（Q1、Q2、Q13 已定）

**行目录（每个命中组件一行，S2；金额为零的组件不生成行，S7）**

| component_type | payer_role / payer_id | payee_role / payee_id | 规则族 `source_rule_type` | `source_rule_version_id` / `source_price_id` / `source_rule_component_id` | 计算 |
|---|---|---|---|---|---|
| `CLIENT_FEE` | `CHANNEL` / clearingAccountId | `OPERATING_ENTITY` / entityNo | `COMMERCIAL_PRICE` | clientPricingVersionId / priceId / componentId | 4.6.1 |
| `CLIENT_PRINCIPAL` | `CHANNEL` / clearingAccountId | `CLIENT` / clientId | `COMMERCIAL_PRICE` | 同上，`component_id` 空 | `gross − Σ CLIENT_FEE.posted`；< 0 → 拒绝 |
| `PARTNER_SHARE` | `OPERATING_ENTITY` / entityNo | `LOCAL_PARTNER` / localPartnerId | `PARTNER_SHARE` | partnerShareVersionId / priceId / componentId | 4.6.1，基数见 4.6.2 |
| `PLATFORM_SERVICE_CHARGE` | `OPERATING_ENTITY` / entityNo | `FINANCE_ENTITY` / financeEntityId | `PLATFORM_SERVICE_CHARGE` | platformChargeVersionId / priceId / componentId | 同上 |
| `CHANNEL_COST` | `OPERATING_ENTITY` / entityNo | `CHANNEL` / clearingAccountId | `CHANNEL_COST` | costVerId / 空 / componentId | `ChannelCostCalculator`（基数 = gross） |
| `SELLER_TAX` | — | — | — | — | 本期不产生 |

全部行 `effect_direction = CHARGE`，`target_bucket` 空，`basis_amount` / `rate_value` / `raw_amount` / `posted_amount` 按实际计算写入；`source_evidence` JSON：`{schemaVersion:1, termType, planId, priceId, pricingProfileCode, componentCode, componentType, calculationBase, triggerCode, rate, fixedAmount, minAmount, maxAmount, bandId, roundingMode, contentHash(渠道成本)}`，只含命中证据不含载荷全文。

**行顺序**：CLIENT_FEE（按 componentNo）→ CLIENT_PRINCIPAL → PARTNER_SHARE → PLATFORM_SERVICE_CHARGE → CHANNEL_COST；`line_no` 从 1 连续。

**4.6.1 组件 → 金额**（commercial 组件；渠道成本沿用 `ChannelCostCalculator` 既有规则）

- 只取 `triggerCode == "SALE"` 的组件（PAY = 直接收款 = 商务计费事件 SALE，S1）；其他触发码的组件忽略。
- 基数：`GROSS_AMOUNT` → gross；`ACTUAL_CLIENT_FEE` → Σ CLIENT_FEE.posted（只允许 Partner 条款）；`NET_AMOUNT` → gross − Σ CLIENT_FEE.posted（只允许 Partner 条款）；`FIXED_PER_EVENT` → 不用基数；`BUY_RATE_DIFFERENCE` → 拒绝（S10）。CLIENT_PRICING 条款出现 `ACTUAL_CLIENT_FEE` / `NET_AMOUNT` → 拒绝（自引用）。
- `componentType`：`PERCENTAGE` → `raw = basis × rate`；`FIXED` → `raw = fixedAmount`，其 `currency` 必须等于事实币种否则拒绝（S9）；`TIERED` → `bandBasis` 必须为 `TRANSACTION_AMOUNT`（`PERIOD_VOLUME` 拒绝，S10），按 `bandMode` 选 `TIERED_WHOLE_BASE`（基数落在哪档用该档 rate / fixedAmount 整体计算）或 `TIERED_PROGRESSIVE`（逐档累进）；档位 `lowerBound` 含、`upperBound` 不含，空 `upperBound` 为无上限；基数落不进任何档 → 拒绝。
- 上下限：`raw` 先 `max(minAmount)` 再 `min(maxAmount)`（为空不约束）。
- 舍入：`raw` 8 位小数 HALF_UP；`posted` 按币种 `minorUnit` HALF_UP（与 `ChannelCostCalculator` 一致）。
- 计算顺序：先 CLIENT_FEE 全部组件 → 本金 → PARTNER_SHARE → PLATFORM_SERVICE_CHARGE → CHANNEL_COST；后者的基数引用前者的 `posted` 之和。
- **实现解释（A 线，2026-10-22）**：commercial 的 `bandMode` 实际取值为 `WHOLE_BASE` / `PROGRESSIVE`（证据 `calculationMethod` 记 billing 枚举 `TIERED_WHOLE_BASE` / `TIERED_PROGRESSIVE`），`RETROACTIVE` 与未知值拒绝；累进档的固定费在该档被触及时计一次；本金 ≤ 0（而非仅 < 0）拒绝；`FIXED_PER_EVENT` 行 `basis_amount` 记毛额（列 NOT NULL）、`rate_value` 为空；`source_rule_version_id` 取价格响应 `termVersionId`（等于请求的冻结版本）；`source_evidence` 另含 `calculationMethod`，渠道成本行证据为 `{schemaVersion, planCode, costVer, scopeId, componentCode, componentType, calculationBase, rate, fixedAmount, minAmount, maxAmount, roundingMode, contentHash}`；非 `SALE` 触发码的组件在计算前过滤、不计入 Σ CLIENT_FEE。

**4.6.2 Partner 条款命中参数**：`termVersionId` = partnerShareVersionId / platformChargeVersionId，`agreementId` = `partnerAgreementId`，`localPartnerId` 必填、`clientId` 空，`entityNo`、`currency`、`contextAsOfTime`、`paymentFactId`、`paymentFacts` 与 Client 命中相同。订单上下文有 `localPartnerId` 但三项 Partner 字段任一为空 → 拒绝（数据不一致）。

**4.6.3 Client 命中参数**：`termVersionId` = clientPricingVersionId、`agreementId` = clientAgreementId、`clientId`、`termType = CLIENT_PRICING`、`entityNo`、`currency`、`contextAsOfTime` = `commercialContextTime`、`paymentFactId` = paymentAttemptId、`paymentFacts.paymentMethodCode` = 事件 `paymentMethodCode`、`paymentFacts.dimensions` = `{CURRENCY, CONSUMER_COUNTRY, CARD_BRAND, FUNDING_TYPE, CARD_LEVEL}` 中非空者（`PricingDimension` 目录代码）。

**4.6.4 `BillingFactInvariants.checkAutoPayment`**：`clientId`、`entityNo`、`currency`、`grossAmount > 0`、`businessTime`、`balanceAvailableTime` 非空；引用集合 ⊇ {ORDER, ORDER_CONTEXT, PAYMENT_INTENT, PAYMENT_ATTEMPT, PAYMENT_CHANNEL_ACCOUNT, MID, CLEARING_ACCOUNT, CHANNEL_TRANSACTION_REF}，不含 TICKET；行组件 ∈ {CLIENT_PRINCIPAL, CLIENT_FEE, PARTNER_SHARE, PLATFORM_SERVICE_CHARGE, CHANNEL_COST}，付方 ≠ 收方，角色 × 组件 与 4.6 行目录一致；`Σ CLIENT_PRINCIPAL + Σ CLIENT_FEE == grossAmount`；合作方二元组全有或全无且 PARTNER_* 行只在有合作方时出现；`CLIENT_PRINCIPAL` 恰一行。

### 4.7 到期转可用时间（`balance_available_time`）

输入：`business_time`（UTC）、`settlementTimezone`（IANA）、`cutoffSecondOfDay`、`settlementBusinessDays = N ≥ 0`、`businessCalendarCode`。

1. `t0 = business_time` 转到结算时区；`d0 = t0.date`；若 `t0.secondOfDay > cutoffSecondOfDay`，`d0 = d0 + 1`。
2. 工作日判定：日期在 `t_business_calendar_day` 有记录时按 `dayType`（WORKING 为工作日、HOLIDAY 为非工作日）；无记录时周一至周五为工作日。
3. `N > 0`：从 `d0` 起向后数 N 个工作日（`d0` 本身不计），落在第 N 个工作日 `d`；`N = 0`：`d = d0` 若为工作日，否则下一个工作日（S8）。
4. `balance_available_time = d 的 23:59:59.000000（结算时区）` 转 UTC。
5. 日历代码不存在、结算四字段任一为空 → 失败重试（配置缺口，Q9）。

### 4.8 错误分类（billing Handler）

| 情况 | 处理 | 结果 |
|---|---|---|
| 信封非法（sourceService / schemaVersion / 聚合类型 / 必填字段 / 格式） | `DomainEventRejectedException` | 直接 DLQ |
| 同 `source_event_key` 已登记且 Hash 相同 | 返回，记 INFO | 消费成功 |
| 同键不同 Hash；事件与订单上下文 / 清算账户的 clientId / entityNo / currency 不一致；`capturedAmount ≠ 上下文 amount`；Partner 字段不完整；`CHANNEL_TRANSACTION_REF` 三元组缺失；`fund_mode` 相关校验（本期无来源，不校验） | `BizCheckFailException` → `DomainEventRejectedException` | 直接 DLQ（数据不一致，重试无意义） |
| 组件规则不可表达（`BUY_RATE_DIFFERENCE`、`PERIOD_VOLUME`、FIXED 币种不符、基数自引用、落不进档、本金 ≤ 0、Σ CLIENT_FEE = 0（S14））；订单组缺 `consumerCountryCode`（渠道成本命中与定价侧表都需要） | 同上 | 直接 DLQ（配置错误需人工改价格后由运维重放） |
| 上游 404 / 400 / 422 / 409（订单上下文不存在、价格 404 / 422 / 409、清算账户 400、渠道成本 400、Finance Entity 404）、上游 5xx / 超时、日历或结算字段缺失、`DuplicateKeyException` | 原样抛出（`FailException` / `ErrorException` / `ConcurrentRequestException`） | messaging 退避重投，第 5 次进 DLQ 并告警；上游补齐后由运维重放 DLQ 恢复 |

日志只记事件 ID、attemptId、阶段与错误码，不记载荷与金额明细。

### 4.9 阶段 3 设计选择（请确认）

| 编号 | 选择 | 建议 |
|---|---|---|
| S1 | PAY 成功对应的商务计费事件 | `SALE`；只取 `triggerCode == SALE` 的组件；渠道成本命中 `actionType` 传 Payment 动作码 `PAY`（阶段 4 核对 channel-connector 的 `actionType` 值域，若为计费事件语义则同样传 `SALE`） |
| S2 | 行粒度 | 每个命中组件一行（`source_rule_component_id` 可追溯）；ledger / account 按组件类型汇总不受影响 |
| S3 | 幂等键 | `source_event_key = PAYMENT_ATTEMPT:<attemptId>`，`source_version` = 信封 `aggregateVersion`；不把版本放进键，避免同一尝试因版本号差异重复登记 |
| S4 | 错误分类 | 4.8：数据不一致与规则不可表达直接 DLQ；上游不可用 / 缺配置退避重试 |
| S5 | acquiring 接口地址 | `GET /commerce/order-contexts/v1/by-order/{orderId}`，不加 `internal` 路径标记 |
| S6 | ledger 接口地址 | `GET /finance-entities/v1/by-party/{legalPartyId}`，挂在 `LedgerSetupQueryApi` |
| S7 | 零金额组件 | 不生成行（ledger / account 本就跳过零行） |
| S8 | T+N 语义 | 4.7：Cutoff 之后算次日起算；`d0` 不计入 N；N=0 取 `d0` 或其后首个工作日；时间为 23:59:59 结算时区 |
| S9 | FIXED 组件币种 | 必须等于事实币种，否则拒绝（不做换汇） |
| S10 | 本期不支持的定价能力 | `BUY_RATE_DIFFERENCE`、`PERIOD_VOLUME` 阶梯、CLIENT_PRICING 下的 `ACTUAL_CLIENT_FEE` / `NET_AMOUNT` 一律拒绝进 DLQ |
| S11（阶段 4 新增，已确认） | 税务快照 | T0 核对：`t_order_context.finance_tax_snapshot_id` NOT NULL，每笔订单恒有占位快照 ID，"有快照 ID → 重试"会卡住全部事件。本期**忽略快照、只记 `TAX_SNAPSHOT` 引用**，不生成 `SELLER_TAX` 行、不读 tax、不重试；tax 上线后由税务切片决定存量事实是否发更正版本。这是对 2026-10-19 决定第 10 条"有快照 ID 读不到则失败等待"的本期边界说明，需求 D / 税务切片接入时恢复该条 |
| S13（阶段 4 新增，已确认） | D08 修正 | billing 不调 acquiring；acquiring 在支付成功落实事务内登记补偿事件、提交后发布 `OrderPaymentSucceeded`（订单冻结商业字段 + 支付事实），billing 消费它。T0 的 acquiring HTTP 接口删除，三表联查 Repository 保留给 Handler；`PaymentSucceeded` consumers 回到 `[acquiring, card]` |
| S14（阶段 4 新增，已确认） | 零手续费 | 独立评审发现 ledger `STANDARD_TRANSACTION` 的 `CLIENT_FEE_REVENUE` 为必需行（`required_flag = 1`），而 billing 对零金额组件不出行（S7）：零手续费事实会在 account 贷本金后被 ledger 拒绝。本期 **billing 对 Σ CLIENT_FEE = 0 的支付直接拒绝进 DLQ**（无 DML）；零费率促销要先出 ledger 规则包新版本把该行改为可选，再放开 billing，记为后续项 |
| S15（阶段 6 新增，已确认） | 结算日历 | 发布预检发现 commercial 把结算日历代码固定为 `AE_BUSINESS_DAYS`（`CommercialCodes`，"本期固定"），而 billing 只有 `DEFAULT` 日历，任何真实价格的 T+N 都会失败重试。billing 初始化数据增加 `AE_BUSINESS_DAYS`（Asia/Dubai，周一至周五，ACTIVE）并提供增量 SQL；本机与 mor-test 各执行一条 INSERT（分别授权）。本需求由"无 SQL"变为一条 DML；初始化 SQL 不进 JAR，已构建镜像不受影响 |
| S12（阶段 4 新增） | 两处实现差异 | acquiring 查询不建 Processor（Controller → 单个 Service，规范允许）；ledger `by-party` 路径参数为 `String` + 正则（与 ledger 其他 ID 路径一致），billing 传十进制字符串 |

### 4.10 account 与配置

- `FactBalanceMappingRule.PLATFORM_SIDE_FEES`：`CHANNEL` → `{CHANNEL_COST, CLIENT_PRINCIPAL, CLIENT_FEE}`；类注释补"PAYMENT 事实渠道为本金与手续费付方"。其余不动；`maturityStatus` 已按 `balanceAvailableTime` 工作。
- billing `application.yml`：`messaging.consumer.service: billing`、`messaging.consumers.acquiring.partitions: 4`（队列 `billing.acquiring.*`，S13）；`http.serviceclient` 新增 `commercial-service`（`${BILLING_COMMERCIAL_SERVICE_URL:http://127.0.0.1:18108}`）、`channel-connector-service`（`:18110`）、`reconciliation-service`（`:18115`）、`ledger-service`（`:18105`），均无 Context Path，超时与 platform-support 相同；无 acquiring 客户端。`application-mock.yml` 同步（`consumer.retry-backoff: 200ms`，四个 base-url 指向测试 Mock HTTP 服务）。
- `test-environment/compose.applications.yaml` billing-service：四个 `BILLING_*_SERVICE_URL`（已在工作树加入）；acquiring-service 加 `PAYTECH_INSTANCE_ID`；`depends_on` 不加（启动顺序不依赖，运行期重试）。mor-test 以 `test` Profile 启动不加载 Nacos，`config/nacos` 无 billing 文件，不补。

## 5. 数据库变更

- 五个库均无结构变更。
- **`mor_billing` 一条 DML（S15）**：`t_business_calendar` 新增 `AE_BUSINESS_DAYS`（Asia/Dubai、ACTIVE）；来源 `paytech/billing/docs/database/sql/billing-initial-data.sql`（新库）与 `paytech/billing/docs/database/sql/changes/2026-10-23-business-calendar-ae.sql`（存量库增量，可重跑，含回滚 DELETE）。执行记录见 [release/release.md](./release/release.md) 第 4 节与 delivery.md。
- 发布预检还发现 mor-test 配置数据缺口（不是 SQL）：channel-connector 无渠道成本方案（阶段 7 经运营控制台创建）、commercial CARD 价格使用事件链未传递的 `ISSUER_REGION` 维度（验收用 APPLE_PAY；发卡维度传递记为需求 D 前置）、DEMO-PT-001 只有 CARD 价格（Partner 场景只由 auto 测试覆盖）。

## 6. 实施与验证计划

### 6.1 分线（阶段 4，Opus 5.5 agent）

| 线 | 范围 | 依赖 |
|---|---|---|
| T0 合同 | payment 两字段 + Handler + 发布测试；acquiring `OrderContextQueryApi` + Controller + Processor + Service + Repository + 测试；ledger `by-party` 接口 + Repository + 测试；`events.yaml` + arch-rules fixture | 先于 A |
| A billing | Reader、Handler、5 个客户端、Assembler、SettlementTimeCalculator、Invariants、`recordAutoFact`、配置、测试基础设施（5 个 Mock HTTP 端点 DTO、`payment.events` 发布 Probe、`billing.payment.*` 队列清理）、场景矩阵 | T0 的 api JAR |
| B account | 白名单 + PAYMENT 事实消费用例 | 无 |
| C 文档 / 环境 | Compose 变量、决定修订说明、design-discuss/billing 第 9 节、CURRENT_IMPLEMENTATION、requirements-site FIN 口径核对 | A 完成后 |

### 6.2 billing 场景矩阵（入口：向本地 `payment.events` 发布 `PaymentSucceeded`）

| 用例 | 预期 |
|---|---|
| 正常 MoR、无 Partner、百分比 + 最低收费 | 事实 APPROVED；行 = CLIENT_FEE、CLIENT_PRINCIPAL、CHANNEL_COST（无税行）；引用 9 类（含 TAX_SNAPSHOT）；定价侧表；`balance_available_time`；`BillingFactRecorded` 发布（观察队列收到） |
| 带 Partner（分润 `ACTUAL_CLIENT_FEE` 80%，服务费 `GROSS_AMOUNT` 2%） | 多 PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 两行；头 `local_partner_id` / `finance_entity_id` |
| 阶梯（WHOLE / PROGRESSIVE）、FIXED、上下限、舍入边界 | 金额逐项断言 |
| T+N：Cutoff 前后、跨周末、节假日记录、N=0 | `balance_available_time` 逐项断言 |
| 同事件重投；两次并发 | 一条事实、一次发布 |
| 上游缺失各一例（价格 404 / 422 / 409、commercial 503、清算账户 400、渠道成本 400、FE 404、日历缺失、结算字段缺失） | 无事实、无发布、退避重投后进 DLQ；上游恢复后重投成功另有一例 |
| 数据不一致各一例（收取金额 ≠ 订单金额、Partner 字段不全、渠道引用三元组缺失、消费者国家缺失、清算账户 OE / 币种不符、同键不同内容、BUY_RATE_DIFFERENCE、PERIOD_VOLUME、FIXED 币种不符、CLIENT_PRICING 自引用基数） | 直接进 DLQ |
| 信封非法（sourceService、schemaVersion ≠ 1、缺必填字段） | 直接进 DLQ |

### 6.3 其他验证

- account：PAYMENT 事实 → Client 贷本金、Partner 贷分润、平台侧行无流水、`maturity_status = PENDING`；到期 Job → AVAILABLE。
- ledger：既有 PAYMENT 过账用例覆盖含服务费的三指令；新接口存在 / 不存在 / CLOSED。
- payment：发布测试断言新字段与顺序；acquiring：接口存在 / 不存在。
- 架构：arch-rules、事件目录测试、`TableOwnershipAutoTest`；requirements-site 测试（若改内容）。
- 本机全量：billing、account、ledger、payment、acquiring 五个 `-test` 模块 `auto`；共享本地库按会话占用约定串行。

### 6.4 mor-test（阶段 6—7 预告）

- 镜像：payment（基于含 card BIN 字段的提交）、acquiring、billing、account、ledger；无 SQL。
- 发布前数据清单：OE 账簿与日历 / 期间 OPEN；`PLATFORM_HQ` FE 的 USD 账簿 OPEN；commercial 真实 Client 合同 + CLIENT_PRICING 版本 + 价格（含结算四字段，日历 `DEFAULT`）、Partner 合同 + 两条款版本（验收 Partner 场景时）；清算账户绑定 MID + USD；渠道成本方案覆盖该渠道；`t_order` / `t_order_context` 人工插入一条带真实 ID 的订单上下文（Q15）。
- 验收：Payment 内部发布一条 `PaymentSucceeded`（或经 Payment 既有内部入口触发 PAY 成功后由 Handler 发布）→ Operations 控制台：计费事实详情、Owner 余额、分录、对账预期明细；DLQ 为空；合成数据清理清单。
