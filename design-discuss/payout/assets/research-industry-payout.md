# payout 行业调研：出款系统通常如何设计

> 日期：2026-10-29。调研笔记，不是结论。来源以 Stripe / Adyen / Checkout.com / Razorpay X / Rapyd / Worldpay / Infinicept 等公开文档为主，GCC 本地轨道部分为二手资料，字段与限额未逐一核实。对应讨论稿见 [../design.md](../design.md)。

## 1. 行业里的"payout"指什么

同一个词在不同产品里是三件事，设计前要先分清：

| 形态 | 含义 | 代表 | 我们对应 |
|---|---|---|---|
| **结算出款（Settlement payout）** | 平台把替商户收到的钱，扣费后付给商户 / 卖家 / 分销方 | Stripe Connect payouts、Adyen for Platforms sweeps、PayFac sub-merchant funding | 当前 payout 服务的全部范围：付给 Client / Local Partner 的应付余额 |
| **代发 / 分账付款（Disbursement）** | 商户用自己的余额给第三方付钱：供应商、承包商、分账收款方、工资 | Stripe Treasury / Global Payouts、Rapyd Disburse、Razorpay X、PayPal Payouts、Thunes | 2026-10-23 决定 U11 / U14：推荐分成出款、子商户 / 分账收款方出款（后续） |
| **退款到卡（Refund / payout to card）** | 原路或 OCT 退回 | 支付渠道能力 | 不属于 payout，归 payment |

Stripe、Adyen 把第一类建在**账户余额模型**之上（balance → payout），第二类建在**资金账户**之上（treasury / wallet → transfer）；两者共用同一套出款执行与收款方（beneficiary / transfer instrument）管理。

## 2. 结算出款的通用模型

### 2.1 余额与可用性

- **两桶起步**：`pending`（待成熟）→ `available`（可出）。Stripe 每个账户只有这两桶加平台的 `connect_reserved`；Adyen balance account 也是 pending / available。我们已细分到 PENDING / AVAILABLE / LOCKED + 保证金 / 追偿应收，粒度更高。
- **成熟时点与出款计划分离**：Stripe 明确"payout schedule 控制什么时候打款，不改变 pending 多久变 available"；成熟由 `delay_days`（T+N，按国家区分工作日 / 自然日）决定，可被平台覆盖到最多 31 天。我们的 `balance_available_time` 由 billing 按命中价格写在支付事实上，与此一致。
- **负余额**：退款 / 拒付先冲未来收入；仍为负时 Stripe 不允许出款，可配置 `debit_negative_balances` 从商户银行账户反向扣款（仅部分国家支持），平台为负余额兜底时在平台账户上占 `connect_reserved`，180 天未转正则用平台储备清零并建议关停账户。我们的对应物是 `CLIENT_RECOVERY_RECEIVABLE` + 保证金；没有反向扣款。
- **最低余额 / 最低出款额**：Stripe `minimum_balance_by_currency` 把一部分余额留在账上不出，降低转负概率；Adyen sweep 有 `triggerAmount` / `targetAmount`。我们没有。
- **储备（Reserve）**：行业两种：**滚动储备**（rolling reserve，按成交额 5–15% 扣留 N 天后释放，是合同条款）与**出款冻结**（payout hold，风控审查期间的临时暂停）。Stripe 为合规原因还可按国家冻结 90 天 / 2 年。我们用 Risk 的 `MIN_SECURITY_DEPOSIT` + `SETTLEMENT_BLOCK` 表达，没有滚动储备。

### 2.2 出款触发方式（timing model）

| 模式 | 做法 | 适用 | 代表 |
|---|---|---|---|
| **自动按计划** | 每日 / 每周（指定星期几）/ 每月（指定日，29–31 顺延到月末）扫 available 全额出 | 商品电商、SaaS | Stripe `interval=daily/weekly/monthly`；Adyen sweep `schedule`（daily / weekly / monthly / balance 触发 / cron） |
| **自动按阈值** | 余额超过 `triggerAmount` 即出，或出到只剩 `targetAmount` | 控制留存余额 | Adyen sweep `balance` 类型 |
| **手动 / 按需** | 商户或运营对 available 发起指定金额的提款 | 平台要控节奏、Instant Payouts、我们现状 | Stripe `interval=manual` + Payouts API；Adyen on-demand transfer |
| **即时** | 对已成功交易即刻到卡 / 账，收 1–1.5% 费，上限（美国 0.50–9,999 USD），负余额不可用，可动用 pending | Gig / 服务市场 | Stripe Instant Payouts；Adyen 不提供 |
| **按履约事件** | 订单 fulfilled 后才生成 payout | 带退货窗口的商品市场 | Mercur `requiredFulfillmentStatus`、Stripe "hold funds until precondition" 建议 |

行业建议：**出款节奏要匹配收款方类型**——Gig 即时、商品卖家按计划或余额提现；平台不应无目的扣押资金，但有明确前置条件（履约、争议窗口）时可持有。

### 2.3 出款单生命周期

各家状态机形状基本一致，差异在"审批 / 排队 / 定时"几个前置态是否存在，以及"失败"与"退回"是否区分：

| 阶段 | Stripe payout | Adyen transfer | Razorpay X payout | Rapyd payout | 含义 |
|---|---|---|---|---|---|
| 待审批 | — | — | `pending`（审批流开启时，3 个月自动拒绝） | — | Maker-Checker 中 |
| 等资金 | — | — | `queued`（余额不足，3 个月自动失败） | — | 余额或现金不足排队 |
| 定时等待 | — | — | `scheduled` | — | 到点再执行 |
| 等确认 | — | `pending`+`internalReview` | — | `Confirmation`（手动确认 FX 报价） | 风控审查 / 汇率确认 |
| 已受理 / 执行中 | `pending` → `in_transit` | `received` → `authorised` → `booked` | `processing` | `Created` | 已提交轨道 |
| 成功 | `paid` | `booked`（扣账即视为出款）→ 银行入账 | `processed` | `Completed` | 轨道受理或银行确认 |
| 失败（未扣款） | `failed` + `failure_code` | `failed` / `refused`（余额不扣） | `failed` | `Declined` | 轨道拒绝，余额回到 available |
| 退回（已扣后退回） | `canceled`（极少）；失败款回 available | `returned`（可晚几天，单独记账） | `reversed`（含手续费与税一起退回） | `Returned` | 收款行退回，需要重新记账 |
| 取消 | `canceled`（仅 pending 态可取） | — | `cancelled`（queued / scheduled 可取）/ `rejected` | `Canceled` | 未提交前撤回 |

共同点：
1. **成功有两个层次**：轨道受理（Stripe `paid`、Adyen `booked`）与银行借记 / 入账确认；Verto 等建议"以 webhook 为最终状态来源"。我们现设计只认第二层（银行借记完整对账后才 `PAID`），比行业更严格。
2. **失败 vs 退回必须区分**：失败是未扣款、余额原地回滚；退回是已扣款后几天由收款行退回，要作为新的资金事实入账（Adyen 在 Accounting Report 里是单独一行，Razorpay 退回时连手续费一起回退）。我们有 `FAILED`（权威未扣款）与迟到借方 / `LATE_DEBIT`，**没有"已付后退回"**的路径。
3. **失败原因分临时 / 永久**：Adyen 永久原因（账户关闭、所有权变更）会阻止后续重试，退回 3 次即封锁该收款账户；Stripe 失败码如 `account_closed`、`account_frozen`、`bank_account_restricted`、`bank_ownership_changed`、`insufficient_funds`（平台自己余额不足）。
4. **UNKNOWN 不是公开状态**：各家对外只给 processing，内部靠 webhook / 轮询收敛；我们对外暴露 `PAYMENT_UNKNOWN` 并禁止重试与取消，是为人工银行提交场景设计的，自动化后要重新审视。

### 2.4 收款账户（Beneficiary / Transfer instrument / External account）

- **独立实体、先验证后使用**：Adyen transfer instrument、Stripe external account、Checkout.com instrument（`src_`）。删除不可逆，轮换账户要"先加新、再改引用、后删旧"。
- **验证手段**：微存款（micro-deposit）、开放银行即时验证（Plaid 等）、户名匹配（beneficiary name matching，与制裁筛查分开做，注意名序 / 缩写 / 变音符造成的误判）、法定名 = 户名核对。
- **按国家 / 币种 / 轨道的动态字段**：IBAN + SWIFT BIC、本地账号 + 银行代码、Routing number、账户类型（current / savings / cash，部分银行拒收 savings）；Wise / Airwallex / Rapyd 以"收款人要求"接口动态下发表单。
- **变更即高风险事件**：行业把"入驻后修改收款账户"列为账户盗用主要信号，要求重新核验、冷却期、通知与审计。
- **敏感数据**：账号 / 户名入 Vault，表里只留 Token、末四位、盲索引（有等值查重消费者时）。我们的 `t_beneficiary_account` 已按此设计。

### 2.5 费用与金额

- **费用承担模式**：`ADDED_ON_TOP`（收款方到账全额，Owner 多扣）/ `DEDUCTED_FROM_PAYOUT`（从出款额扣）；SWIFT 场景另有 OUR / SHA / BEN 费用分担。恒等式：`beneficiary_amount + fee_amount = owner_debit_amount`。我们已有。
- **费用定价**：按轨道（本地 / SWIFT / 即时）、按金额（固定 + 百分比）、按币种、按收款方类型；Stripe 即时 1.5% min 0.50 USD。我们的 billing `FeeQuoteApi(PAYOUT)` 设计就是这个"先报价、冻结在单上、完成后生成事实"的模式。
- **FX**：跨币出款三步——取报价（有效期）→ 在有效期内创建出款并绑定 `quote_id` → 跟踪；Rapyd 支持手动确认汇率的 `Confirmation` 态；Worldpay 要求 quote 币对与出款一致。我们 fx 模块只有 README，MVP 单币 USD。
- **最小 / 最大金额、速度限制**：按轨道的单笔上限（SARIE 等本地即时轨道有上限，各资料口径不一）、按收款方的每日 / 每笔速率限制（Instant payouts 常见）。

### 2.6 审批与控制（运营侧）

- **Maker-Checker / 4-eyes**：发起人不能审批自己的单；按阈值触发审批（单笔或批次总额超过阈值）；审批期间资金锁定防重复使用；新建或变更的收款账户、高额、异常项由审批人重点看。我们的固定双人审批 + 创建即锁定与此一致，缺的是**阈值分级**（小额免审或单人审）。
- **批次 / 付款运行（payment run）**：把当日待付汇成批次文件（NACHA / ISO 20022 pain.001 / 银行 bulk 模板），控制合计数、逐项幂等键、部分成功时逐项对账；行业建议批次 ≈ 50 项、每项独立 Idempotency-Key、webhook 为终态来源。
- **人工银行门户提交**：无 API 时的通用做法——系统生成付款指令与批次文件，运营在银行门户上传 / 录入，回填银行受理号与凭证，最终以银行对账单借记对账为准。我们现状。
- **暂停 / 冻结**：运营可对单个 Owner 或全局暂停出款（Stripe `payouts.status`、payout hold）；暂停只阻止新建 / 执行，不动已锁定。
- **取消 / 撤回**：未提交轨道前可取消；已提交只能等失败或申请 recall（SWIFT 撤回、ACH reversal 有窄窗口与条件）。

### 2.7 合规与风险门禁

- **出款前筛查**：对收款方（及法人 / 受益所有人）做制裁名单筛查，名单更新后重筛，不只在入驻时做；50% 规则下的间接受控主体也要拦。
- **KYC / KYB 状态**：`payouts_enabled` 与 `charges_enabled` 分开（Stripe requirements、Adyen capability `receivePayouts`、Checkout.com `payouts_enabled`）；资料过期 / 待补时先停出款。我们已在 client-activation 讨论中采用"出款通道开通项"表达。
- **风控检查点**：按创建 / 终审 / 执行 / 提交多次评估（我们 RCO-I09 四检查点）；行业多数只在创建时评估一次加执行前的余额 / 冻结检查，四次是我们偏严的设计。
- **资金托管与货币转移牌照**：PayFac 若经手资金易被认定为 money transmitter；常见做法是 **FBO 账户**（收单行结算一笔总额到 PayFac 名下为子商户持有的 FBO 账户，PayFac 的账本记录归属，银行按指令付款、PayFac 不碰钱）或 **收单行直接付子商户**（PayFac 只发指令、单独收费）。与 2026-10-23 决定"Platform 不经手资金、FUNDS_CUSTODIAN 为 PayFac Partner 的 OE"一致：出款的银行账户必须是托管方（OE）的账户。
- **税务 / 发票附带**：部分地区出款需同时产出结算单（settlement statement）、代扣税或 1099 / 发票；我们 tax 讨论稿有 `TAX_PAID`，出款对账单（statement）尚未定义。

### 2.8 记账与对账

- **出款是"清偿应付"**：会计上 Dr 应付客户款 / Cr 银行存款，锁定与排队不产生分录（我们 account 桶移动 + ledger 控制科目已如此）。
- **三方对账**：出款单 ↔ 轨道 / 银行回执 ↔ 银行对账单借记；退回要生成独立事实。行业提醒"把收付记在同一账本，避免每天跑匹配任务"，我们正是 billing → account → ledger 单链路。
- **报表**：按日出款报告（Adyen Balance Platform Payout Report）、每笔出款的组成明细（含哪些交易 / 退款 / 费用，Stripe balance transactions by payout）、商户可下载结算单。

### 2.9 收款方体验（Client / Partner 门户）

- 余额页：pending / available / 即将成熟时间线、负余额与原因、储备 / 保证金占用。
- 出款页：计划设置（若允许自选）、手动提款、每笔出款的状态、失败原因与修复指引（更新账户后重试）、出款明细下载（哪些交易构成本次出款）、银行凭证下载。
- 收款账户页：添加 / 验证 / 设默认 / 停用，变更冷却与通知。
- 通知：出款已发起 / 已到账 / 失败 / 账户需更新（邮件 + webhook `payout.paid` / `payout.failed`）。

## 3. 代发 / 分账付款（第二类）与结算出款的差异

| 维度 | 结算出款 | 代发 / 分账 |
|---|---|---|
| 资金来源 | 平台替 Owner 收的应付余额 | Owner 自有余额 / 预充值 / 刚收到的某笔交易 |
| 收款方 | Owner 自己（Client / Partner 法人） | 第三方：供应商、承包商、分账收款方、工资 |
| 收款方入驻 | Owner 已 KYB | 需要单独的 payee 主档 + KYC（我们 counterparty 的"未来 Payee"） |
| 触发 | 计划 / 提现 | API 批量指令、按交易分账 split instruction |
| 合规 | Owner 自身资格 | 每个收款方筛查、代付用途、可能触发汇款牌照 |
| 典型 API | balance → payout | transfers / payouts batch + beneficiary API + FX quote |

Adyen 的 split 在收款时就把卖家份额、平台佣金、费用分别记入不同 balance account，再按各自 sweep 出款，这是"分账 + 结算出款"合一的模型；Stripe 以 separate charges and transfers 实现同样效果。我们 2026-10-23 决定 U14 分账模块与 U11"出款对象 = 某账本里对某主体的应付余额、一个流程"即是把两类统一到"应付即可出"。

## 4. GCC 本地轨道（AE / SA / EG，二手资料，待核）

| 国家 | 大额 / 批量 | 即时 | 说明 |
|---|---|---|---|
| AE | UAEFTS（央行，工作日 08:00–14:00 GST，AED 结算强制走此系统） | IPP / Aani（24×7，Aani 单笔约 AED 50,000） | 大于 AED 25,000 常走 UAEFTS，是否当日取决于是否在银行时段内；各行有自己的 cut-off |
| SA | SARIE（央行 RTGS） | SARIE 即时层（2021-02 上线），上限资料口径不一（20,000 vs 300,000 SAR） | 另有 Sadad、Buna（阿拉伯区域跨境） |
| EG | ACH / CBE | InstaPay（IPN，2026 Q2 约 2,200 万用户） | 出款服务商多以 IPN 做本地到账 |

结论：MVP 人工银行提交下，轨道差异体现在**截止时间、工作日日历、单笔上限、是否当日到账**，这些应作为"出款通道配置"的属性，而非写死；自动化接入时每条轨道是 channel-connector 的一个 `SERVICE` 渠道标准 API。

## 5. 对我们的启发（供 design.md 讨论）

1. **现有 Manual Payout 设计在"控制"维度已超过行业平均**（四检查点、双人审批、银行借记对账后才 PAID、UNKNOWN 不释放），缺的是**便利性与自动化维度**：计划出款、阈值出款、最低留存余额、批次 / 付款运行、失败后的自动重试与收款账户修复闭环、出款明细 / 结算单、通知。
2. **缺"已付后退回（returned）"路径**，这是行业对账里最常见的异常之一。
3. **缺出款通道（rail）这一层抽象**：费用、截止时间、上限、日历、是否自动化都挂在它上面；client-activation 讨论里的 `PAYOUT_LOCAL / PAYOUT_SWIFT` 开通项正需要它。
4. **收款账户的变更风控**（冷却、重新核验、通知）在行业是标配。
5. **自动化路线**：人工门户提交 → 批次文件导出 / 导入 → 银行 API（channel-connector `SERVICE` 渠道）。状态机应一开始就为三种执行方式留位。
6. **分账 / 代发**与结算出款共用执行层与收款方管理，差在资金来源与收款方入驻；按 U11 / U14 分期。

## 来源

- Stripe：[Understanding Connect account balances](https://docs.stripe.com/connect/account-balances)、[Manage payout schedule](https://docs.stripe.com/connect/manage-payout-schedule)、[Manual payouts](https://docs.stripe.com/connect/manual-payouts)、[Instant payouts](https://docs.stripe.com/connect/instant-payouts)、[Instant payouts with advance funding](https://docs.stripe.com/payouts/instant-payouts-with-advance-funding)、[Minimum balances for automatic payouts](https://docs.stripe.com/payouts/minimum-balances-for-automatic-payouts)、[Managing Connect payout failures](https://support.stripe.com/questions/managing-connect-payout-failures)、[Fix the negative balance](https://support.stripe.com/questions/fix-the-negative-balance-on-your-account)、[Connect Reserves FAQ](https://support.stripe.com/questions/connect-reserves-faq)、[Marketplace payouts](https://stripe.com/resources/more/marketplace-payouts)
- Adyen：[Scheduled payouts (sweeps)](https://docs.adyen.com/platforms/payout-to-users/scheduled-payouts/)、[Payouts quickstart](https://docs.adyen.com/platforms/quickstart-guide/payouts)、[Manage transfer instruments](https://docs.adyen.com/platforms/manage-transfer-instruments)、[Create a sweep](https://docs.adyen.com/api-explorer/balanceplatform/2/post/balanceAccounts/_balanceAccountId_/sweeps)、[Payout webhooks](https://docs.adyen.com/platforms/payout-webhooks)、[Payout report](https://docs.adyen.com/marketplaces/reports-and-fees/payout-report)、[Why was my payout returned](https://help.adyen.com/knowledge/finance/payouts/why-was-my-payout-returned-to-adyen)
- 其他服务商：[Checkout.com bank payouts](https://www.checkout.com/docs/payouts/bank-payouts)、[Checkout.com idempotency](https://www.checkout.com/docs/developer-resources/api/idempotency.md)、[Razorpay X payout states](https://razorpay.com/docs/x/payouts/states-life-cycle/)、[Razorpay X approval workflow](https://razorpay.com/docs/x/bulk-payouts/approval-workflow.md)、[Rapyd payout with FX](https://docs.rapyd.net/en/payout-with-fx-message-sequences.html)、[Worldpay account payouts](https://developer.worldpay.com/products/access/account-payouts/openapi/single-payout)、[StraitsX FX payout](https://docs.straitsx.com/docs/fx-payout-api)、[Verto bulk disbursement](https://docs.verto.co/docs/bulk-supplier-disbursement)、[Tazapay payout state machine](https://developer.tazapay.com/Global-Money-Movement/payouts/state-machine)、[Mercur payout module](https://docs.mercurjs.com/references/modules/payout)
- PayFac 资金流：[Venable: Money transmission in the PayFac model](https://www.venable.com/insights/publications/2018/06/money-transmission-in-the-payment-facilitator-mode)、[Infinicept: PayFac funding options](https://www.infinicept.com/payment-facilitator-funding-options/)、[Formance: Becoming a PayFac](https://www.formance.com/blog/product/becoming-a-payment-facilitator-ledger-reconciliation)、[Worldpay Dynamic Payout FAQ](http://support.worldpay.com/support/pdf/Worldpay_Dynamic_Payout_FAQs.pdf)
- 运营与合规：[Maker-checker](https://en.wikipedia.org/wiki/Maker-checker)、[Payment run review](https://vorplabs.com/workflows/accounting/payment-run-positive-pay-review)、[Checkout.com: rolling reserve](https://www.checkout.com/blog/what-is-a-rolling-reserve)、[Plaid: merchant onboarding](https://plaid.com/resources/fintech/merchant-onboarding/)、[Beneficiary name matching](https://www.signzy.com/general-glossary/beneficiary-name-matching)、[Screening payouts](https://www.sanctions.io/blog/screening-contractor-and-payroll-payouts)、[Ongoing sanctions rescreening](https://blindpay.com/resources/more/ongoing-sanctions-screening-how-often-to-rescreen)、[LLD: Payout service](https://www.techinterview.org/post/3233471082/lld-payout-service/)
- GCC 轨道：[CBUAE rulebook: AED settlement via UAEFTS](https://rulebook.centralbank.ae/node/3580)、[Lean: settlement in the UAE](https://help.leantech.me/article/how-does-payment-settlement-work-in-the-uae)、[Barclays UAE payment services](https://www.ib.barclays/content/dam/barclaysmicrosites/ibpublic/documents/investment-bank/uae/Payment%20Services%20UAE.pdf)、[Saudi SARIE](https://paymentbrief.com/articles/saudi-arabia-mada-sarie-payment-stack/)、[SAR payouts](https://keybs.io/services/global-payouts/currencies/sar)、[Egypt IPN](https://edgeconsultancykw.com/egypt-instant-payment-network-22-million-customers-1-47-billion-transactions/)、[Gulf payments infrastructure](https://valu.vc/payments-infrastructure-gulf/)
