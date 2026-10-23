# billing（计费）的职责与设计

> 状态：讨论中（2026-10-19 起草；同日用户确认 Q1–Q11 按建议，并指出渠道成本配置不应归 billing，见 Q12）。依据现状调查（`paytech/billing` 源码与模型）、[目标架构 V2 决定](../../agent-docs/decisions/2026-10-17-target-architecture-v2.md) D05 / D16 / D37 / D41、[2026-10-18 account / ledger 决定](../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)、[CCT 定价决定](../../agent-docs/decisions/2026-09-10-cct-payment-method-profile-pricing.md)、[四系统决定](../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)、[收单端到端时序](../../requirements/20261017/architecture-目标架构V2/acquiring-sequence.md) Stage 2 / 3。讨论定稿后登记决定记录。

## 1. 一句话

**billing 是"算钱并把结果定格成事实"的服务。** 任何会改变 Client、合作伙伴、平台或银行现金余额的金额，都必须先在 billing 变成一条不可变、带版本的**经济事实**，再由 account 记明细账、ledger 记总账。billing 不管余额（account）、不管分录（ledger）、不定价格（CCT / 商务）、不算税（税务）、不确认渠道实收（对账）。

## 2. 现状与目标的差距

| 能力 | 现在（2026-10-19） | 目标 |
|---|---|---|
| 经济事实模型与事件 | 三张事实表、`EconomicFactRecorded`、替代链、Maker-Checker 登记 | 不变，作为全部事实的落点 |
| 人工调整 / 成本调整登记 | 已实现（两步审批） | 不变 |
| 渠道成本配置与预估计算 | 配置与预估计算都在 billing（配置已实现；预估无生产调用方） | **迁出 billing**：配置归渠道 Owner，billing 只在支付事实生成时去命中并冻结证据（Q12） |
| 支付事实（PAYMENT） | **没有**：不消费 `PaymentSucceeded`，不算手续费 / 分润 / 服务费 / 预估成本 | 核心能力，首个切片 |
| 退款、争议、重复扣款事实 | 没有 | 按反向规则生成，受原事实封顶 |
| 到期转可用时间 `balance_available_time` | 字段在，**没人写** | 支付事实生成时按命中价格的 T+N 算出 |
| 渠道到账、出款完成、缴税完成的资金事实 | 没有 | 由对账 / 出款 / 税务调 billing 登记（决定第 8 条） |
| 出款费用 | 没有（连事实类型都没有） | 出款申请时计算 |
| 费用明细 / 利润查询 | 只有按 ID 和按主体分页读 | 单笔费用拆解给控制台与 Client 门户 |

## 3. 职责边界（提案）

### 3.1 billing 拥有什么

1. **经济事实目录与生命周期**：事实类型、明细行（费用类型、付方 / 收方、方向、金额）、计算证据（命中的价格 / 组件 / 阶梯 / 费率）、替代链（更正不改历史，发新版本冲回旧版本）、发布。
2. **费用计算引擎**：按"冻结的合同版本 + 交易事实"算 Client 手续费、合作伙伴分润、平台服务费、渠道预估成本、出款费用；规则：事实不齐不按零费率，命中不唯一就失败，不重新命中合同。
3. **反向规则**：退款按比例冲回百分比手续费、固定费不退；争议、重复扣款的冲回受原事实封顶（用 `t_economic_fact_line_link`）。
4. **到期转可用时间**：和费用来自同一条命中价格的 T+N、日历、时区、截止秒；写在支付事实上，account 的定时任务只读它。
5. **成本差异事实**（对账确认实际成本后，预估 → 实际的差额）；渠道成本配置本身不在 billing（Q12）。
6. **资金类事实的登记入口**：渠道结算到账、出款完成、缴税完成、银行现金期初，由各自 Owner 调 billing 登记（系统来源、按来源键幂等、不走人工审批）。
7. **人工调整**（已有）：影响 Owner 余额的人工调整只能在这里登记，两步审批。
8. **查询**：按事实 ID / 来源键 / 订单 / 主体 + 币种分页读事实与明细；单笔交易费用拆解。

### 3.2 billing 不拥有什么

| 不做 | 谁做 | billing 只做 |
|---|---|---|
| 合同、价格、Profile、阶梯主数据与命中 | CCT / 商务 | 拿冻结的版本去问命中结果，把结果和证据冻结在事实行 |
| 税额计算、税务快照、发票 | 税务 | 把税务快照里的卖方税额作为一行 `SELLER_TAX` 引用进来 |
| 余额、余额桶、可用额度 | account | 发事实；到期时间写在事实上 |
| 会计分录、试算、关账 | ledger | 发事实；ledger 经 account 批次再读事实明细 |
| 渠道成本方案、版本、范围、组件（渠道合同价格）与命中 | 渠道连接（channel-connector；渠道与 MID 主数据今在 Payment，随渠道连接切片一起迁） | 拿支付事实去问命中的成本组件，把结果与证据冻结在 `CHANNEL_COST` 行；与 CCT 价格同一模式 |
| 渠道实际成本、清算文件、长短款 | 清算与对账 | 收 `ActualChannelCostConfirmed`，生成成本差异事实 |
| 出款编排、审批、银行提交 | 出款 | 出款申请时算费用、出款完成时登记资金事实 |
| 利润报表、跨主体汇总 | 数据服务（数仓） | 只保证事实可被 CDC 同步 |

### 3.3 设计原则

- **一笔业务动作 = 一条事实**，头上带来源系统 + 来源事件键 + 来源版本，重复到达按来源键幂等（同内容 200，不同内容 400）。
- **事实不可变**：更正用 `supersedes_fact_id` 发新版本；消费者按 `fact_id + fact_version` 处理冲回。
- **证据随事实冻结**：每行记命中的价格 ID、组件、阶梯证据、费率、固定费、舍入方式，日后合同改了也能复算当时的数。
- **计算失败就停**：缺价格、缺订单上下文、缺税务快照、渠道成本命中不唯一，都进死信等人处理，不生成"暂估为 0"的事实。
- **事务公理**：读 CCT / 收单 / 税务 / 对账在事务外，算完一个本地事务写事实 + 补偿事件，提交后发布。

## 4. 一笔支付在 billing 里怎么走

例子：消费者付 100 USD，Client 合同手续费 10%（最低 1 USD），合作伙伴分润 = 手续费的 80%，平台向合作伙伴收服务费 = 分润的 25%，渠道预估成本 0.5%，无税。

1. **收事件**：`PaymentSucceeded`（Intent、Attempt、OE `AE001`、100 USD、成功时间、支付方式、卡组织 / 资金类型 / 卡等级 / 3DS 结果、订单上下文 ID）。
2. **事务外读三样东西**：
   - 收单的订单上下文：Client、合作伙伴、冻结的 Client 价格版本、分润版本、服务费版本、税务快照 ID、商务上下文时间；
   - CCT 价格命中：拿"冻结的版本 + 支付事实"换回命中的 Price（组件、阶梯、最低收费、T+N 结算输入）；
   - 税务快照（有则读，本例无）。
3. **本地事务算并定格**：

| 行 | 费用类型 | 付方 → 收方 | 金额 | 证据 |
|---|---|---|---|---|
| 1 | `CLIENT_PRINCIPAL` | 渠道 → Client | 90.00 | 毛额 − 手续费 |
| 2 | `CLIENT_FEE` | Client → OE | 10.00 | Price P-17 组件 PERCENTAGE 10%，最低 1 |
| 3 | `PARTNER_SHARE` | OE → 合作伙伴 | 8.00 | 分润版本 S-3，手续费 × 80% |
| 4 | `PLATFORM_SERVICE_CHARGE` | 合作伙伴 → 平台财务主体 | 2.00 | 服务费版本 C-2，分润 × 25% |
| 5 | `CHANNEL_COST` | OE → 渠道 | 0.50 | 渠道成本版本 V-5，0.5% |

   头：`PAYMENT`、毛额 100、`balance_available_time` = 成功时间按 T+2 工作日 / UTC / 23:59:59 推算、版本 1。
4. **提交后发布** `EconomicFactRecorded`。account 把 90 记入 Client `CLIENT_PAYABLE / PENDING`、8 记入合作伙伴 `PARTNER_PAYABLE / PENDING`；ledger 经 account 批次记控制科目与收入、费用、渠道应收、服务费双账簿。
5. **后续**：对账确认实际成本 0.45 → billing 发 `COST_ADJUSTMENT`（减 0.05）；Client 退 30 → billing 发 `REFUND` 事实，冲回本金 30、按比例冲回手续费 3（固定部分不退）、分润与服务费随之按比例冲回。

## 5. 事实类型目录（提案）

| 类型 | 触发 | 来源 | 审批 |
|---|---|---|---|
| `PAYMENT` | `PaymentSucceeded` | 自动 | 无 |
| `REFUND` | `RefundSucceeded` | 自动 | 无 |
| `DUPLICATE_CAPTURE` / `DUPLICATE_COMPENSATION` | 重复扣款发现 / 补偿 | 自动 | 无 |
| `DISPUTE` | `DisputeLost` / `DisputeOutcomeCorrected` | 自动 | 无 |
| `COST_ADJUSTMENT` | `ActualChannelCostConfirmed`（自动）或人工 | 两者 | 人工的要审批 |
| `CHANNEL_SETTLEMENT`（新） | 渠道款到账（`ChannelSettlementConfirmed` 事件） | 自动 | 无 |
| `PAYOUT_SETTLED`（新） | 出款银行确认（`PayoutSettled` 事件） | 自动 | 无；一条事实两行：`PAYOUT_AMOUNT` 本金 + `PAYOUT_FEE` 手续费（费用在出款申请时经报价接口算好并冻结在出款单上，事件带回） |
| `TAX_PAID`（新） | 缴税完成（`TaxPaymentSettled` 事件） | 自动 | 无 |
| `MANUAL_ADJUSTMENT` | 人工 | 人工 | Maker-Checker |
| `FX_SELL` + `FX_BUY`（新，外汇模块） | 兑换单成交（`FxDealSettled`），一笔兑换两条事实 | 自动 | 无 |

**外汇（FX）成交会不会在 billing 产生数据？会。** 兑换改变 Client 两个币种的余额（卖出币减少、买入币增加），按"改变余额的金额先成为事实"的原则必须经 billing。但一条事实只有一个币种、account 的入账流也按主体 + 币种分片，所以**一笔兑换产生两条事实**：卖出币种一条（Client → 平台外汇账户，含平台点差 / 手续费行）、买入币种一条（平台外汇账户 → Client），两条都引用同一个 `FX_DEAL`，各自进自己币种的入账流。汇率、点差、报价 ID 作为行证据冻结（规则族 `FX_RATE`）。汇兑损益、敞口与平盘是 ledger 和汇率服务的事，不在 billing。渠道以不同币种结算（结算 FX）同理：到账事实按实收币种记，差额由 ledger 的汇兑科目承接，billing 不再造一条"差额事实"。

这三类是"资金类事实"：只动银行现金与渠道应收 / 应付科目，不涉及价格计算，但仍然经 billing 落事实，保证 account 与 ledger 只有一个上游；优先由各 Owner 的事件触发，没有事件源时才用备用的登记命令（Q13）。

## 6. 合同（提案）

**进来的**

| 合同 | 提供方 | billing 用它做什么 | 现状 |
|---|---|---|---|
| `PaymentSucceeded` | Payment | 触发 PAYMENT 事实 | 有，但缺订单上下文 ID 与定价所需的支付事实（卡组织、资金类型、卡等级、3DS 结果） |
| 订单上下文查询 | 收单 | 读冻结的合同版本、合作伙伴、税务快照 ID | 没有内部查询接口 |
| 价格命中 `POST /commercial-prices/v1/resolutions` | CCT / 商务 | 换回命中 Price 与结算输入 | 合同已写（2026-09-11 stage3），接口未实现 |
| 税务快照读取 | 税务 | `SELLER_TAX` 行 | 税务是空壳 |
| `RefundSucceeded`、`DisputeLost`、`DuplicatePayment*` | Payment / 争议 | 反向事实 | 事件存在，billing 不消费 |
| 渠道成本命中 `POST /channel-costs/v1/resolutions`（新） | 渠道连接 | 换回命中的成本组件（费率、固定费、上下限、版本、内容 Hash） | 配置与命中逻辑现在 billing 里（4 张表 + 领域服务），要迁出 |
| `ActualChannelCostConfirmed` | 对账 | 成本差异事实 | 对账是空壳 |
| 资金事实登记：`ChannelSettlementConfirmed`、`PayoutSettled`、`TaxPaymentSettled`（事件或命令，见 Q13） | 对账 / 出款 / 税务 | 资金类事实；出款事实含本金与手续费两行 | 出款无代码 |
| 渠道清算账户映射（MID → 清算账户） | 对账 | 事实头 `channel_account_id` | 没有（依赖清单 X6） |
| 工作日日历 | 平台参考数据（建议同 D41 只读副本） | 算 `balance_available_time` | 没有 |

**出去的**

| 合同 | 消费方 | 现状 |
|---|---|---|
| `EconomicFactRecorded`（头 + 全部行） | account（ledger 经 account 批次后回读） | 有；文档里的 `FeesCalculated` / `EconomicsCalculated` 应统一成这个名字 |
| 事实查询（按 ID、按主体分页、待发布计数） | account、ledger、控制台 | 有 |
| 单笔费用拆解（FIN-I03） | 控制台、Client 门户 | 没有 |

## 6a. billing 对外提供的接口（提案）

内部 HTTP 合同（`paytech-billing-api`，`@HttpExchange`，未接 Gateway；运营入口经 Operations BFF 转发）。「现状」列为 2026-10-19 需求 A 阶段 4 代码状态（本机库待重建、未部署；最新状态只见 `agent-docs/CURRENT_IMPLEMENTATION.md`）：

| 接口 | Method / Path | 调用方 | 说明 | 现状 |
|---|---|---|---|---|
| `BillingFactCommandApi` 登记人工事实 | `POST /billing/v1/facts` | 运营（经 BFF）、工单 | `MANUAL_ADJUSTMENT`、人工 `COST_ADJUSTMENT`，Maker 提交为 DRAFT；按 `sourceSystem + sourceReference` 幂等 | 已实现（需求 A：原 `/billing/v1/economic-facts` 改路径、字段与 `refs`、`targetBucket`） |
| 同上 批准 | `POST /billing/v1/facts/{factId}/approve` | 运营 | Checker 批准，定稿版本，登记发布 | 已实现（需求 A，登记 `BILLING_FACT_RECORDED` 补偿事件） |
| `BillingFactRegistrationApi` 登记资金类事实（备用） | `POST /billing/v1/facts/channel-settlements`、`.../payout-settlements`、`.../tax-payments` | 对账、出款、税务 | 只在对应 Owner 没有事件源时使用的系统命令，AUTO，按来源键幂等，不审批；优先走事件（Q13） | 无 |
| `FeeQuoteApi` 费用报价（只算不记） | `POST /billing/v1/fee-quotes` | 出款（出款申请时算费用以确定锁定金额）、将来其他需要预先知道费用的流程 | 入参场景（`PAYOUT`）、Client / OE / 币种 / 金额 / 时间，返回费用明细与证据（价格 ID、版本、内容 Hash）；**不落事实、无状态**；调用方把报价冻结在自己的单据上，事实在业务完成时由事件生成 | 无 |
| `BillingFactQueryApi` 按 ID 读 | `GET /billing/v1/facts/{factId}` | ledger、控制台 | 头 + 引用 + 行（含证据） | 已实现（需求 A，含定价、行证据与链接） |
| 同上 按流分页 | `GET /billing/v1/facts?entityType&entityId&currency&fromFactId&limit` | account（重建核对）、ledger | APPROVED / SUPERSEDED，按 ID 升序 | 已实现（需求 A：参数改 `entityType`、`entityId`） |
| 同上 发布进度 | `GET /billing/v1/facts/progress?entityType&entityId&currency` | ledger 关账 | 未发布数、已发布数、最大 ID | 已实现（需求 A：原 `/pending` 改名，含 `publishedFactCount`） |
| 同上 按引用查 | `GET /billing/v1/facts/by-ref?refType&refId&fromFactId&limit` | 控制台、Client 门户 BFF | 一个订单 / 出款单 / 争议下的全部事实，按 factId 游标分页 | 已实现（需求 A） |
| `FeeBreakdownQueryApi` 单笔费用拆解（FIN-I03） | `GET /billing/v1/fee-breakdowns?refType=ORDER&refId=` | 控制台、Client 门户 BFF | 把一个订单的支付、退款、调整事实合成一份"收了多少、扣了多少、净多少"的拆解 | 无 |
| ~~`ChannelCostPlanApi`~~ | ~~`/channel-cost-plans/v1/**`~~ | — | 迁到 channel-connector（第 8 节）：`/channel-connector/v1/channel-cost-plans/**` 与命中接口 `POST /channel-connector/v1/channel-costs/resolutions` | 已迁出（需求 A，billing 侧删除，channel-connector 已实现） |

事件：

| 方向 | 事件 | 说明 |
|---|---|---|
| 发布 | `BillingFactRecorded`（原 `EconomicFactRecorded`） | 聚合 `BILLING_FACT:{factId}`，版本 `factVersion`，载荷 = 头 + 引用 + 全部行；消费者 account |
| 消费 | `PaymentSucceeded`、`RefundSucceeded`、`DisputeLost` / `DisputeOutcomeCorrected`、`DuplicatePaymentDetected` / `DuplicatePaymentRemediated`、`ActualChannelCostConfirmed`、`ChannelSettlementConfirmed`、`PayoutSettled`、`TaxPaymentSettled`、`FxDealSettled` | 各生成对应事实；按来源键幂等，不要求严格有序 |

事务外出站调用：收单订单上下文（COM-I04）、CCT 价格命中、channel-connector 渠道成本命中、税务快照读取、对账清算账户映射（随对账切片）。

## 7. 要讨论的问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | **价格命中在哪做？** A：CCT 提供命中接口，billing 只拿结果（2026-09-11 已定）；B：billing 直接读商务的价格表自己命中 | **A**。命中规则属于合同 Owner，billing 把命中结果与证据冻结即可；避免两处实现同一套阶梯规则 |
| Q2 | **PAYMENT 事实的输入从哪来？** `PaymentSucceeded` 现在缺订单上下文 ID 和卡组织等定价事实 | 事件加 `orderContextId` 与定价支付事实（Payment 已有 `t_payment_pricing_fact` 被删的历史，字段直接放事件）；订单的合同版本与合作伙伴经收单内部查询接口（COM-I04）事务外读取 |
| Q3 | **到期时间谁算？** 时序图写 account 算，模型与 FIN-18 写 billing 算 | **billing 算**，写在事实头：费用和成熟必须来自同一条命中价格（2026-09-11 决定）。工作日日历作为平台参考数据，billing 持只读副本（同币种表） |
| Q4 | **税怎么接？** 税务服务还是空壳 | 首个切片允许"订单无税务快照 → 无 `SELLER_TAX` 行"；有快照 ID 但读不到 → 失败等待，不按 0 入账 |
| Q5 | **渠道成本：预估 + 差异，还是只记实际？** | 保留预估 + 差异（利润能及时看到）；预估的命中改为调渠道 Owner 的接口（Q12） |
| Q6 | **退款 / 争议 / 重复扣款的冲回规则**：按比例冲回百分比手续费、固定费不退、封顶 | 原则先定下来（见第 3.1 第 3 条），细则随退款与争议切片 |
| Q7 | **资金类事实是否都经 billing？** 渠道到账、出款完成、缴税完成不涉及价格，经 billing 多一跳 | **经 billing**（决定第 8 条）：account / ledger 只有一个上游，事实有统一的来源键、版本与查询；新增三个事实类型，不走审批 |
| Q8 | **自动事实要不要审批？** 现在只有人工登记走 Maker-Checker | 自动事实不审批；更正只能发新版本（人工发起、走审批）；不允许改自动事实 |
| Q9 | **事件名** | 统一 `EconomicFactRecorded`，时序图与站点里的 `FeesCalculated` / `EconomicsCalculated` 改名 |
| Q10 | **billing 要不要给门户出"交易利润"？** | 不出。单笔费用拆解由 billing 出；利润、汇总由数据服务从事实表 CDC 算 |
| Q13 | **为什么有 `payout-fees` 命令接口？**（用户 2026-10-19 提出）原设计照搬时序图「出款编排 → 计费：计算出款费用 → 返回经济事实 ID」，变成了一个同步命令在业务还没完成时就落事实，和其他自动事实「业务完成 → 事件 → 事实」的模式不一致；出款被拒或取消还得冲回 | **改为「报价 + 事件」**：出款申请时调无状态的 `FeeQuoteApi` 只算不记，报价与证据冻结在出款单上，用于确定锁定金额；出款银行确认后 `PayoutSettled` 事件带回本金与冻结的费用，billing 生成一条 `PAYOUT_SETTLED` 事实（本金行 + 手续费行）。删除 `PAYOUT_FEE` 事实类型（保留 `PAYOUT_FEE` 组件代码）。渠道到账、缴税完成同样优先走事件（`ChannelSettlementConfirmed`、`TaxPaymentSettled`），没有事件源时才用命令 |
| Q12 | **渠道成本配置为什么在 billing？**（用户 2026-10-19 提出）历史原因：最早在 finance-service 里做（FIN-04），V2 拆分时随 billing 走，因为预估计算要用它 | **迁出**。按「规则归 Owner、billing 只冻结证据」的原则，渠道成本方案是和渠道签的合同价格（买入价），与 Client 价格（卖出价）对称，应归渠道 Owner（V2 的渠道连接；渠道与 MID 主数据今天在 Payment）。渠道 Owner 提供命中接口，billing 算金额并冻结证据；4 张配置表、Maker-Checker 与预估领域服务整体搬走，billing 只剩事实引擎 |
| Q11 | **首个切片范围** | `PaymentSucceeded → PAYMENT 事实 → account → ledger`，前置三项上游合同（Q2 的事件字段与收单查询、Q1 的 CCT 命中接口、工作日日历）；退款、争议、资金类事实、出款费用各自后续 |

## 8. 渠道成本的处理方案（Q12 展开，2026-10-19）

**目标归属：** 渠道成本方案归 **channel-connector**。它已经拥有 `t_payment_channel`、`t_channel_account`、`t_channel_mid`、`t_mid_capability*`（`mor_channel_connector`），成本方案的 `channel_id` 与范围里的 `mid_id` 搬过去后都变成库内关系，不再是跨库引用。

**各方职责：**

| 系统 | 做什么 | 不做什么 |
|---|---|---|
| channel-connector | 渠道成本方案 / 版本 / 范围 / 组件四表与 Maker-Checker 接口（`/channel-cost-plans/v1` 原样搬）；版本到期自动切换 Job；新增命中接口 `POST /channel-connector/v1/channel-costs/resolutions`：入参 MID、OE、国家、币种、支付方式、动作、发生时间，返回命中的版本、范围、各组件（费率、固定费、上下限、舍入、内容 Hash）；命中不唯一或命中不到返回业务错误 | 不算金额（和 CCT 一样"规则 Owner 不算钱"） |
| billing | PAYMENT 事实生成时事务外调命中接口，按组件算 `CHANNEL_COST` 行（基数 × 费率 + 固定费，上下限，币种精度舍入），证据冻结在行上（`source_rule_type = CHANNEL_COST`、版本 ID、组件 ID、`source_evidence`）；命中失败 → 整条事实失败进死信，不按 0 入账。现有 `ChannelCostEstimationService` 的"组件 → 金额"计算逻辑留在 billing 费用引擎里复用，命中部分搬走 | 不维护方案 |
| 清算与对账 | 确认实际成本后发 `ActualChannelCostConfirmed`（含清算账户、实际金额、对应支付尝试） | — |
| billing | 收到后发 `COST_ADJUSTMENT`（`CHANNEL_COST` + `ADJUSTMENT`，增提为 OE → 渠道、冲减为渠道 → OE；阶段 3 细化，见 technique 2.6），`link` 到原 `CHANNEL_COST` 行 | — |
| ledger | 规则包不变：`CHANNEL_COST` → 渠道费用 / 渠道费用应计；差异行同科目 | — |
| Operations 控制台 | 「渠道成本配置」页（财务第三批原计划）改接 channel-connector | — |

**迁移内容（独立小切片，先于 billing 首个切片的研发）：**

1. 四张表从 `mor_billing` 迁到 `mor_channel_connector`（两环境均 0 行，直接建表、删表），`table-ownership.yaml` 与 design/03 同步；`t_channel_cost_scope.mid_id` 改为引用本库 `t_channel_mid`。
2. 5 个 Maker-Checker Processor、Controller、API、测试整体搬到 channel-connector；billing 的 `ChannelCostPlanApi` 删除。
3. 新增命中接口与 Processor（只读、无锁、无事务）；billing 新增命中客户端（事务外）。
4. billing 保留"组件 → 金额"计算器（从 `ChannelCostEstimationServiceImpl` 拆出），删除 billing 内的方案读取。
5. 到期切换 Job 在 channel-connector 登记（XXL）。

**顺序：** 先做这个小切片（channel-connector + billing + arch-rules，约 1–2 天），再开 billing 首个切片；两者都不依赖对账服务。

## 9. 已知的上游缺口（首个切片要先补）

1. `PaymentSucceeded` 缺 `orderContextId` 与定价支付事实（Payment）。
2. 收单没有内部订单上下文查询接口（COM-I04 未实现）。
3. CCT / 商务没有 `POST /commercial-prices/v1/resolutions`（合同已写）。
4. 没有工作日日历表，无法算 T+N。
5. `channel_account_id` 语义：billing 存的是对账的清算账户，`PaymentSucceeded` 带的是 Payment 的技术账户，两者没有映射（精简模型里拆成 `CLEARING_ACCOUNT` 与 `PAYMENT_CHANNEL_ACCOUNT` 两种引用）。
7. 渠道成本配置与命中接口还在 billing，要先迁到 channel-connector（第 8 节）。
6. 税务快照在 `mor_tax`，税务服务是空壳。

**状态（2026-10-23，需求 B [billing 支付事实自动登记](<../../requirements/20261022/billing-支付事实自动登记/requirement.md>) 已上 mor-test）：** 第 1、2 条改由 acquiring 在支付成功落实后发布 `OrderPaymentSucceeded`（订单冻结商业字段 + 支付事实）解决，billing 不回查 acquiring（S13）；第 3 条 commercial 命中接口已存在；第 4 条 billing `t_business_calendar` 已有 `DEFAULT` 与 `AE_BUSINESS_DAYS`；第 5 条按拆分为 `CLEARING_ACCOUNT`（reconciliation 解析接口）与 `PAYMENT_CHANNEL_ACCOUNT` 两种引用落地；第 7 条已迁出；第 6 条只记 `TAX_SNAPSHOT` 引用、不生成税行（S11）。仍开放：acquiring 商业事实为固定 Mock（需求 D 前置）、CARD 价格所需发卡维度未经事件传递、Partner 演示价格缺失、对账四类事件（需求 C）。
