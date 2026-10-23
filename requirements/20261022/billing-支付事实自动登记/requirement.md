# billing 支付事实自动登记（计费需求 B 首切片）

> 状态：阶段 1 已确认（2026-10-22：Q1 手续费行 渠道 → OE；Q2 服务费由 OE 承担；Q8 对账四事件另立需求 C；其余按建议），阶段 2 评审稿见 [technique.md](./technique.md) 第 1—3 节（含对本稿的四处修正与 Q13—Q16）。确认记录见 [delivery.md](./delivery.md)。依据 [2026-10-19 billing 决定](../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md)（Confirmed，第 1、2、5、7—11 条与 Q11 首切片范围）、[2026-10-18 account / ledger 决定](../../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)、[2026-10-20 reconciliation 决定](../../../agent-docs/decisions/2026-10-20-reconciliation-facts-via-billing.md)第 4、5、9 条，以及 [design-discuss/billing](../../../design-discuss/billing/design.md) 第 4、9 节。

## 1. 背景与来源

2026-10-22 对"支付成功 → 记账"链路的代码核对结论：`billing 批准 → BillingFactRecorded → account 明细账 → AccountPosted → ledger 分录` 三段已经建好，并在 mor-test 上用人工调整事实跑通；但 **billing 不消费 `PaymentSucceeded`，系统里从未产生过一条 `PAYMENT` 事实**，account 的 `FactBalanceMappingRule` 也只认人工调整的付方 / 收方结构。这就是 billing 决定 Q11 定义的首切片"需求 B"，前序需求（[20261019 billing 模型重构](../20261019/billing-计费事实模型重构与渠道成本迁出/requirement.md)）把它登记为遗留项，等待三项上游合同。

上游缺口到 2026-10-22 的实际状态（依据源码与 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)）：

| 编号 | 讨论稿第 9 节缺口 | 现状 | 本需求处理 |
|---|---|---|---|
| X1 | `PaymentSucceeded` 缺 `orderContextId` 与定价支付事实 | v2 载荷（mor-test 2026-10-22 18:00Z 版本）22 个字段：clientId、orderId、sourceType、paymentIntentId、paymentAttemptId、sourceSubmissionId、entityNo、amount / currency / capturedAmount、paymentSucceededTime、paymentMethodCode、channelId / channelAccountId / midId、channelTransactionRef（外部交易号）、instrumentRef、consentToSave、customerId、cardBrand / fundingType / cardLevel（card BIN 需求已加）；只在 PAY 成功时发一次，`capturedAmount == amount`。**缺**渠道交易引用的内部 ID 与引用类型（对账决定第 5 条）；`orderContextId` 经阶段 2 核对**不再需要**（acquiring 按 `orderId` 可唯一定位上下文） | Payment 在 v2 末尾追加可空字段 `channelTransactionRefId`、`channelReferenceType`（接在 `cardLevel` 之后；外部交易号复用现有 `channelTransactionRef`）；卡属性为空时按无该维度命中（阶段 2 修正） |
| X2 | 收单没有内部订单上下文查询接口 | `t_order_context` 已冻结 commercial_context_time、client_pricing_version_id、partner_share_version_id、platform_charge_version_id、platform_service_party_id、client_agreement_id、partner_agreement_id、partner_agreement_operating_entity_id、finance_tax_snapshot_id、consumer_country_code，`order_id` 唯一；acquiring-api 只有 Client / Customer 对外接口，**没有内部读接口**。**阶段 2 发现：这些商业 ID 由固定 Mock 写入（`AE001` / 104 / 105，Partner 字段恒空），生产代码不调 commercial**（硬前置 P1，见 technique.md 2.3） | acquiring 新增私网只读接口，按 `orderId` 查（Q4）；真实接入 commercial 另立需求 D |
| X3 | CCT / 商务没有 `POST /commercial-prices/v1/resolutions` | **已实现**（commercial `ResolveCommercialPriceProcessor`）：按冻结版本 + 支付事实返回唯一价格，含组件 / 阶梯 / 最低最高与结算四字段（`settlementBusinessDays`、`businessCalendarCode`、`settlementTimezone`、`cutoffSecondOfDay`） | 直接消费；Partner 条款命中方式见 Q6 |
| X4 | 工作日日历 | billing `t_business_calendar` / `t_business_calendar_day` 已建，初始化 `DEFAULT`（UTC、周一至周五） | 直接使用 |
| X5 | 渠道清算账户映射 | **已实现** reconciliation `POST /reconciliation/v1/clearing-accounts/resolutions`（MID + 币种 → 清算账户、渠道、OE、币种；未归属 400 `CLEARING_ACCOUNT_NOT_RESOLVED`） | 事务外调用，取 `CLEARING_ACCOUNT` 引用 |
| X6 | 税务快照 | tax 仍是空壳 | 按决定第 10 条：订单无税务快照 → 无 `SELLER_TAX` 行；有快照 ID 但读不到 → 失败等待 |
| X7 | 渠道成本配置与命中在 billing | **已迁出**：channel-connector `ChannelCostResolutionApi`（`POST /channel-connector/v1/channel-costs/resolutions`）；billing 保留 `ChannelCostCalculator`（无调用方） | 事务外命中，billing 算金额并冻结到 `CHANNEL_COST` 行 |
| — | account 支付行映射 | `FactBalanceMappingRule` 只映射 `CLIENT × CLIENT_PRINCIPAL / CLIENT_FEE`、`LOCAL_PARTNER × PARTNER_SHARE`，桶固定 `PENDING`，Owner 出现在未登记组合即整条拒绝（Fail-Closed）；前序需求遗留项已指出与支付事实行结构不兼容 | 本需求定稿支付事实的付方 / 收方结构并扩展映射（Q1、Q2） |
| — | ledger 支付规则包 | `STANDARD_TRANSACTION` 规则包 v1 ACTIVE：`PAYMENT_SUCCEEDED` 6 行（GROSS_RECEIVABLE@清算账户、CLIENT_FEE_REVENUE、SELLER_TAX_PAYABLE、CHANNEL_COST_EXPENSE / ACCRUAL、PARTNER_SHARE_EXPENSE）+ `PLATFORM_SERVICE_CHARGE_PARTNER` 2 行（OE 账簿）+ `PLATFORM_SERVICE_CHARGE_PLATFORM` 2 行（平台 FE 账簿），与 Q2 口径一致；ledger 对 PAYMENT 只按金额来源取数，不看行付方 / 收方（阶段 2 更正：阶段 1 稿误记为"没有服务费规则行"） | **无规则变更**；只需事实头带 `finance_entity_id`（缺口 G1 / Q14） |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 财务（Operations 控制台） | 每笔支付成功后无需人工干预即在明细账与总账看到本金、手续费、分润、服务费、预估渠道成本、税；事实可按订单 / 支付尝试追溯，证据（命中价格、组件、费率、舍入）随事实冻结 |
| account / ledger | 继续只消费 `BillingFactRecorded` / `AccountPosted`；支付事实进入 Owner `PENDING` 桶并按 `balance_available_time` 成熟 |
| reconciliation | 按既有订阅收到 PAYMENT 事实，生成预期清算明细（决定第 4 条），渠道账单匹配从此有数据 |
| 后续切片（退款、争议、重复扣款、资金类事实） | 复用本期的消费入口、上游读取、费用计算与映射骨架 |

## 3. 业务场景

**触发：** Payment 发布 `PaymentSucceeded`（聚合 `PAYMENT_ATTEMPT`，一个支付尝试只成功一次）。

**成功结果（以讨论稿第 4 节例子：消费者付 100 USD，Client 手续费 10%，Partner 分润 = 手续费 × 80%，平台服务费 = 分润 × 25%，预估渠道成本 0.5%，无税）：**

1. billing 事务外依次读取：acquiring 订单上下文（冻结的合同 / 条款版本、Local Partner、服务费签约 Party、税务快照 ID、消费者国家、商业时点）→ commercial 价格命中（Client 价格；Partner 分润与平台服务费条款见 Q6）→ channel-connector 渠道成本命中 → reconciliation 清算账户解析（MID + 币种）→（有快照 ID 时）税务快照。任一必需输入缺失或命中不唯一：不按 0 入账，消费失败退避重投（决定第 2 条）；清算账户未建时同样停在重试，建好后自动恢复（对账决定第 9 条）。
2. billing 本地事务：登记一条 `PAYMENT` 事实（`origin_type = AUTO`，登记即 `APPROVED`，`fact_version = 1`，`business_time = paymentSucceededTime`，`gross_amount` 见 Q10，`balance_available_time` 按命中价格的结算四字段 + 工作日日历推算），引用 `ORDER`、`ORDER_CONTEXT`、`PAYMENT_INTENT`、`PAYMENT_ATTEMPT`、`PAYMENT_CHANNEL_ACCOUNT`、`MID`、`CLEARING_ACCOUNT`、`CHANNEL_TRANSACTION_REF`（可空的外部引用类型 / 外部交易号）、`TAX_SNAPSHOT`（有时），定价侧表 `t_billing_fact_pricing` 写命中维度与价格 ID、结算四字段，明细行每行付方 / 收方 / 方向 / 金额 / 证据，同一事务登记 `BILLING_FACT_RECORDED` 补偿事件。幂等键 `source_system = payment`、`source_event_key` = 事件键（同事件重投返回原事实）。
3. 提交后发布 `BillingFactRecorded`；account 把 Client 本金 90 记 `CLIENT_PAYABLE / PENDING` 贷、Partner 净分润记 `PARTNER_PAYABLE / PENDING` 贷（服务费是否从分润中扣减见 Q2），`maturity_status = PENDING`，到期任务转 `AVAILABLE`；ledger 按 `STANDARD_TRANSACTION` 规则在 OE 账簿记渠道待清算应收 100、手续费收入 10、分润费用 8、渠道成本费用 / 预提 0.5，平台服务费按 Q2 结论记 OE 账簿与平台 Finance Entity 账簿。
4. reconciliation 消费同一事件生成预期清算明细（已实现订阅，本期只验证有数据流入）。

**边界场景：**

- 卡属性缺失（card BIN 需求尚未上线或未识别）：commercial 命中时不传该维度；若命中价格的条件要求该维度，返回 422 `PRICING_FACTS_INCOMPLETE`，按失败重试处理，由运营补价格或 BIN 库。
- Local Partner 为空（PLATFORM / CLIENT 来源 OE）：无 `PARTNER_SHARE`、`PLATFORM_SERVICE_CHARGE` 行。
- 同一事件重复投递、不同实例并发：`source_event_key` 唯一键 + 来源引用资源锁；返回原事实、不重复发布。
- `PaymentSucceeded` 迟到、事实业务时间落在已 `CLOSED` 期间：由 ledger 既有规则顺延到最早 OPEN 期间（2026-10-18 决定第 12 条修订），billing 不处理。

## 4. 本期范围

1. **Payment**：`PaymentSucceeded` v2 载荷末尾追加可空字段 `channelTransactionRefId`、`channelReferenceType`（接在 card BIN 需求的 `cardLevel` 之后；`schemaVersion` 仍为 2，见 Q16；acquiring / card 忽略新增字段）；事件目录 `consumers` 加 `billing`。（阶段 2 修正：不加 `orderContextId`，外部交易号复用现有 `channelTransactionRef`。）
2. **acquiring**：（阶段 4 S13 修订）不再提供只读接口（违反 D08 领域层不得调流程层）；改为在支付成功落实事务内登记补偿事件、提交后发布 `OrderPaymentSucceeded`（订单冻结商业字段 + 支付事实），billing 消费该事件。
3. **billing**：
   - 新增 `PaymentSucceeded` 消费 Handler（`paytech-messaging`，`strictOrder = false`，`HANDLER_MANAGED`：上游读取在事务外，事实写入在短事务内）；
   - 上游客户端：acquiring 订单上下文、commercial `CommercialPriceApi`、channel-connector `ChannelCostResolutionApi`、reconciliation `ClearingAccountResolutionApi`；税务：本期不生成 `SELLER_TAX` 行、不建 tax 客户端，只把订单上下文的 `finance_tax_snapshot_id`（NOT NULL，恒有）记为 `TAX_SNAPSHOT` 引用（阶段 4 S11，取代阶段 1 的"有快照 ID → 失败重试"）；
   - 费用计算：按命中组件（`PERCENTAGE` / `FIXED` / 阶梯 / 最低最高）算 `CLIENT_FEE`，`CLIENT_PRINCIPAL = gross − CLIENT_FEE`（MoR），`PARTNER_SHARE`、`PLATFORM_SERVICE_CHARGE` 按 Partner 条款基数，`CHANNEL_COST` 用既有 `ChannelCostCalculator`；舍入按币种精度、每行证据写 `source_rule_type` / 规则 ID / 版本 / 费率 / `source_evidence`；
   - `balance_available_time` = `business_time` 在结算时区按 `cutoffSecondOfDay` 判定起算日，加 `settlementBusinessDays` 个 `businessCalendarCode` 日历工作日；
   - 事实头 / 定价侧表 / 引用 / 行 / 补偿事件一个本地事务；`BillingFactRecorded` 载荷不变（头 + 引用 + 全部行）。
4. **account**：`FactBalanceMappingRule` 增加 PAYMENT 行结构的映射（按 Q1 / Q2 结论），`maturity_status` 按 `balance_available_time` 判定（现有逻辑已支持，补测试）；不改表。
5. **ledger**：规则包无变更（阶段 2 更正）；新增按 `legalPartyId` 解析 ACTIVE Finance Entity 的私网只读接口，供 billing 事务外取 `finance_entity_id`（Q14 方案 a）。
6. **架构规则与环境**：事件目录、补偿事件目录、arch-rules、Compose / Nacos 新增 billing 到四个上游的 URL 变量、test-environment 发布清单（payment、acquiring、billing、account、ledger 五个镜像 + ledger DML）。
7. **文档**：CURRENT_IMPLEMENTATION 台账、design-discuss/billing 状态、requirements-site FIN 相关用例的事件名与链路口径（前序遗留 FIN-12 / FIN-24 / E2E-08 一并核对）。

## 5. 明确不做

| 项 | 说明 |
|---|---|
| `REFUND`、`DISPUTE`、`DUPLICATE_*`、`CHANNEL_SETTLEMENT`、`PAYOUT_SETTLED`、`TAX_PAID`、`FX_*` 事实 | 各自切片；account 扣款类分桶规则（V3 设计 8.6）随退款切片 |
| 消费 reconciliation 四类事件（`ActualChannelCostConfirmed` → `COST_ADJUSTMENT` 等）与新增 `CHANNEL_BATCH_RECONCILED` / `RECON_ADJUSTMENT` / `BANK_SUSPENSE` 事实类型 | 另立需求 C（Q8） |
| PSP / `fund_mode = FEES_ONLY`（Direct Merchant 只收费不收本金）、`PARTNER_BUY_RATE` 分润基数、月交易量阶梯、IC++ | commercial 设计第 13 节与 Q4 已明确后续；本期只做 MoR `SETTLE_THROUGH_PLATFORM`，遇到其他 `fund_mode` 失败重试并告警 |
| `PayableBalanceMatured` 发布、`FeeQuoteApi`、单笔费用拆解 `FeeBreakdownQueryApi` | 各自切片 |
| 税务快照读取与 `SELLER_TAX` 行 | tax 空壳；本期只记 `TAX_SNAPSHOT` 引用（S11），税行随税务切片 |
| Operations 新页面 | 既有计费事实列表已能展示 AUTO 事实；只验证可见，不新增页面 |
| 卡属性进入 `PaymentSucceeded` | card BIN 需求 AC-10；本期按可空消费 |
| 执行 SQL、部署、推送、提交 | 按阶段单独授权 |

## 6. 验收标准

| 编号 | 验收标准 |
|---|---|
| AC-01 | `PaymentSucceeded` v2 载荷在 `cardLevel` 之后追加 `channelTransactionRefId`、`channelReferenceType`（可空），`schemaVersion` 仍为 2；acquiring、card 既有消费测试不变；事件目录新增 `OrderPaymentSucceeded`（producer acquiring，consumers [billing]），`PaymentSucceeded` consumers 保持 `[acquiring, card]`（阶段 4 S13 修订） |
| AC-02 | （S13 修订）acquiring 在 APPLIED 路径发布 `OrderPaymentSucceeded`：载荷含全部订单冻结商业字段与支付事实、字段顺序固定、可空字段为 null；CLOSED / 重复路径不发布；Handler 入口测试与消费测试覆盖 |
| AC-03 | billing 消费一条 MoR、无 Local Partner、无税的 `PaymentSucceeded` 后，生成 `PAYMENT` / `AUTO` / `APPROVED` 事实：引用集合完整、`CLIENT_PRINCIPAL + CLIENT_FEE = gross_amount`、`CHANNEL_COST` 行命中版本与金额、`balance_available_time` 按 T+N 工作日 / 时区 / Cutoff 正确（含跨周末与 Cutoff 之后的用例）；证据列与 `source_evidence` 可追溯到价格 ID、组件、费率、舍入 |
| AC-04 | Partner 来源订单：事实含 `PARTNER_SHARE` 与 `PLATFORM_SERVICE_CHARGE` 行，基数与比例按命中条款；行结构与 Q1 / Q2 结论一致 |
| AC-05 | 同一事件重投、两个实例并发消费同一事件：只有一条事实、一次发布；不同 `paymentAttemptId` 互不影响 |
| AC-06 | 任一必需上游缺失或未就绪（价格 404 / 422 / 409、commercial 5xx、清算账户 400、渠道成本 400、Finance Entity 404、日历或结算字段缺失）：不落事实、不发事件，消费失败按退避重投并记录原因，上游恢复后重投成功；事件内部或与上游事实不一致（收取金额 ≠ 订单金额、Partner 字段不全、渠道引用缺失、清算账户 OE / 币种不符）与定价不可表达（含 S14 零手续费）直接进 DLQ。`fund_mode` 本期无来源不校验（阶段 4 修订） |
| AC-07 | account 消费该 PAYMENT 事实：Client `CLIENT_PAYABLE / PENDING` 贷本金、Partner `PARTNER_PAYABLE / PENDING` 贷净分润（按 Q2），平台侧行不产生 Owner 流水，`maturity_status = PENDING`；到期任务执行后转 `AVAILABLE`；任何未登记的角色 × 组件组合仍整条拒绝 |
| AC-08 | ledger 消费对应 `AccountPosted`：`STANDARD_TRANSACTION` 生成 `PAYMENT_SUCCEEDED` 指令并过账，分录借贷平衡，渠道待清算应收挂清算账户；Partner 来源时平台服务费按 Q2 在两账簿同一本地事务成对过账；试算平衡通过 |
| AC-09 | reconciliation 消费同一 `BillingFactRecorded` 生成预期清算明细（现有逻辑），`CHANNEL_TRANSACTION_REF` 与 `CLEARING_ACCOUNT` 引用可用 |
| AC-10 | billing / account / ledger / payment / acquiring auto 测试全过；arch-rules、事件目录测试、requirements-site 测试通过（既有失败单列） |
| AC-11 | mor-test：五个镜像替换后（无 DML），用人工构造的订单上下文（真实 commercial 合同 / 条款版本 ID）+ Payment 内部发布一笔 `PaymentSucceeded`，Operations 控制台可见该计费事实、Owner 余额、分录与对账预期明细；合成数据清理。经 Hosted Checkout 真实入口的端到端验收随需求 D（阶段 2 按 Q15 调整，待确认） |

## 7. 待决问题与建议

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | **PAYMENT 事实行的付方 / 收方结构。** 讨论稿 C1 写"CLIENT_PRINCIPAL 渠道 → Client；CLIENT_FEE Client → OE"。按现行 account 规则，Client 作为 `CLIENT_FEE` 付方会被映射为 `CLIENT_PAYABLE` 借方，本金 90 贷、手续费 10 借 → Client 余额 80，**重复扣减**；这正是前序需求指出的不兼容 | **改为按资金实际流向写行：`CLIENT_PRINCIPAL` 渠道 → Client（90），`CLIENT_FEE` 渠道 → OE（10）**。毛额在渠道处一次拆分，Client 只作为本金收方出现，account 只记一笔贷 90；ledger 规则（GROSS_RECEIVABLE 用头部毛额、CLIENT_FEE_REVENUE 贷 BOOK_ENTITY）与此结构相容。account 规则只需把 `CHANNEL` 加入 `CLIENT_PRINCIPAL` / `CLIENT_FEE` 的平台侧白名单；PAYMENT 事实中 Client 不再出现在 `CLIENT_FEE` 行，既有 `CLIENT × CLIENT_FEE` 映射保留给人工调整（阶段 4 按 technique.md 4.10 落地） |
| Q2 | **平台服务费由谁承担。** 讨论稿 C1：`PLATFORM_SERVICE_CHARGE` 合作伙伴 → 平台 Finance Entity（Partner 净得 8 − 2 = 6）。AGENTS.md 固定边界：在 **Partner OE 账簿** `Dr Service Fee Expense / Cr Payable`，平台 FE 账簿 `Dr Receivable / Cr Service Revenue`——即服务费是 OE 对平台法人的费用，不是从 Partner 分润里扣 | 两种口径只能留一个。**建议按 AGENTS.md**：行写 `PLATFORM_SERVICE_CHARGE` OPERATING_ENTITY → FINANCE_ENTITY，不动 Partner 余额（Partner 仍得 8），ledger 补两条规则行并在平台 FE 账簿成对过账，`finance_entity_id` 由 ledger 按冻结的 `platform_service_party_id` 映射。若用户确认应从分润中扣（讨论稿口径），则 account 需增加 `LOCAL_PARTNER × PLATFORM_SERVICE_CHARGE` 借方映射，且 AGENTS.md 该条要改——这是业务决定，请明确 |
| Q3 | `PaymentSucceeded` 字段追加与 card BIN 需求的先后 | 两者都是 v2 末尾追加可空字段，互不阻塞；本需求只加 X1 的四个字段，卡属性由 card 需求加。先落地者先改 `PaymentSucceededPayload`，后者在其后追加；阶段 3 对齐字段顺序 |
| Q4 | 订单上下文读取形式 | **新增 acquiring 私网只读接口** `GET /acquiring/v1/order-contexts/{orderContextId}`（acquiring-api 模块 `@HttpExchange`），不把全部冻结字段塞进事件。响应包含 `t_order_context` 全部冻结字段 + 订单 clientId / entityNo / 金额币种 |
| Q5 | `t_order_context.client_pricing_version_id` 是否就是 commercial `ResolveCommercialPriceRequestBody.termVersionId`；`client_agreement_id` 是否为 commercial `agreementId` | 台账记"acquiring 未消费 commercial 新字段 / 未冻结"（六层模型遗留）。阶段 2 核对 commercial 版本与合同 ID 命名空间；若不一致，本需求增加 acquiring 冻结 commercial 解析输出（`planId` / `versionId` / `fundMode` / `referralPartnerId`）作为前置项 |
| Q6 | Partner 分润与平台服务费怎么命中 | 阶段 2 核对 commercial 是否支持以 `termType = PARTNER_SHARE / PLATFORM_SERVICE_CHARGE` + `partner_share_version_id / platform_charge_version_id` 调同一 `/commercial-prices/v1/resolutions`，以及组件的 `calculationBase` 是否能表达"手续费 × 80%""分润 × 25%"。能则直接用；不能则作为阻塞项先补 commercial |
| Q7 | 渠道成本未命中（渠道未配成本方案）怎么处理 | 按决定第 2 条 **Fail-Closed，等待重试并告警**，不省略 `CHANNEL_COST` 行；mor-test 启用前须为在用渠道配置成本方案（channel-connector 控制台已有） |
| Q8 | 是否顺带消费 reconciliation 四类事件 | **另立需求 C**。它需要 3 种新事实类型、`ChannelBatchReconciled` 四类行、ledger 归类码映射与新科目，体量与本需求相当；本需求先把 PAYMENT 跑通，也是对账事件有意义的前提。mor-test 上四个发布任务继续留 FAILED 直到需求 C |
| Q9 | 命中价格结算四字段为空（MoR 下不应出现） | Fail-Closed：`balance_available_time` 不可为空，缺失视为价格配置错误，失败重试并告警 |
| Q10 | `gross_amount` 取 `amount` 还是 `capturedAmount` | 取 **`capturedAmount`**（实际入账金额）；阶段 2 核对 Payment 对 AUTH + 部分 CAPTURE 场景何时发布 `PaymentSucceeded`、是否只发一次 |
| Q11 | 幂等键 | `source_system = payment`，`source_event_key` = `PAYMENT_ATTEMPT:<paymentAttemptId>:PaymentSucceeded:<aggregateVersion>`；同键重投返回原事实；`paytech-messaging` 的 eventId 去重在前 |
| Q13—Q16 | 阶段 2 新增：服务费计算基数、Party → Finance Entity 映射、mor-test 验收方式、`schemaVersion` 是否升 3 | 见 [technique.md 3.1](./technique.md#31-阶段-2-新增待决问题) |
| Q12 | 阶段安排与执行方式 | 阶段 1 单独确认（本稿）；阶段 2 核对 Q5 / Q6 / Q10 并出差异表；阶段 3 定事件字段、acquiring 接口、行结构、account 映射、ledger 规则 DML、测试矩阵；阶段 4 Opus 5.5 多 agent 分线（T0 合同：payment 事件 + acquiring-api + 事件目录；A billing；B account + ledger；C 规则 / 环境 / 文档）；5 本机；6—7 mor-test；8 提交。mor-test 的本地 `mor_platform` 等共享库使用遵守会话间占用约定 |

## 8. 依据

- [2026-10-19 billing 决定](../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md)、[design-discuss/billing](../../../design-discuss/billing/)（design.md 第 4、6、7、9 节；cases.md C1）
- [2026-10-18 account / ledger 决定](../../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)、[V3 设计](../../../design-discuss/finance-account-ledger-v3/design.md)第 6、8 节
- [2026-10-20 reconciliation 决定](../../../agent-docs/decisions/2026-10-20-reconciliation-facts-via-billing.md)第 4、5、9 条
- [2026-10-19 commercial 六层定价决定](../../../agent-docs/decisions/2026-10-19-commercial-six-layer-product-pricing.md)、[design-discuss/commercial](../../../design-discuss/commercial/design.md)第 11、13 节
- [20261016 交易自动记账跨系统依赖清单](../../20261016/finance-交易自动记账/dependencies.md)（X1—X6 的历史口径）
- [20261019 billing 模型重构](../20261019/billing-计费事实模型重构与渠道成本迁出/requirement.md)（遗留项）、[20261022 card BIN 需求](../card-BIN库与卡属性/requirement.md)（R5 / AC-10）
- 源码：`paytech/payment/.../PaymentSucceededPayload.java`、`paytech/acquiring/docs/database/sql/acquiring-schema.sql`（`t_order_context`）、`paytech/billing/paytech-billing-api`、`paytech/account/.../FactBalanceMappingRule.java`、`paytech/ledger/.../TransactionPostingServiceImpl.java`、`paytech/ledger/docs/database/sql/ledger-initial-data.sql`、`paytech/commercial/paytech-commercial-api/.../price`、`paytech/reconciliation/paytech-reconciliation-api/.../clearing`、`paytech/channel-connector/paytech-channel-connector-api/.../cost`
