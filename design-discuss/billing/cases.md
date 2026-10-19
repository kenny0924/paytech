# billing 业务案例（每种事实一个，与 design.pen 的 Case C1–C10 画板一致）

> 状态：讨论中（2026-10-19）。数字串起来讲同一笔生意：消费者付 100 USD，Client 1001，OE AE001，合作伙伴 2001，手续费 10%、分润 80%、服务费 25%、渠道预估成本 0.5%，T+2 工作日。每个案例四段：① 触发与输入 → ② 事实（行：组件、付方 → 收方、金额）→ ③ account 明细账影响 → ④ ledger 总账影响。

| # | 案例 | 触发 | 事实与行 | account | ledger | 要点 |
|---|---|---|---|---|---|---|
| C1 | 支付成功 `PAYMENT` | `PaymentSucceeded`（A1，100 USD）；事务外读订单上下文、CCT 价格 P-17、channel-connector 成本 V-5 | #9001：`CLIENT_PRINCIPAL` 渠道→Client 90；`CLIENT_FEE` Client→OE 10；`PARTNER_SHARE` OE→合作伙伴 8；`PLATFORM_SERVICE_CHARGE` 合作伙伴→平台财务主体 2；`CHANNEL_COST` OE→渠道 0.5；`balance_available_time` = 10-20 23:59:59Z | Client PENDING +90、合作伙伴 PENDING +8；到期后定时任务转 AVAILABLE | Dr 渠道应收 100 / Cr Client 应付 90 / Cr 手续费收入 10；分润 8；服务费双账簿 2；渠道费用 0.5 | 缺价格 / 上下文 / 成本命中不唯一 → 死信，不按 0 |
| C2 | 退款 `REFUND` | `RefundSucceeded`（R-7，30 USD）；读原事实 #9001，不重新命中 | #9105（parent #9001）：本金 REVERSAL Client→渠道 27；手续费 REVERSAL OE→Client 3；分润 REVERSAL 2.4；服务费 REVERSAL 0.6；每行 link 原行、封顶 | Client 净减 24（AVAILABLE → PENDING 顺序，不动 LOCKED 与保证金，不足记追偿应收）；合作伙伴 −2.4 | 各科目反向 | 累计超 100 拒绝；固定费不退 |
| C3 | 争议败诉 `DISPUTE` | `DisputeLost`（DC-3，100 USD，争议费 15，Client 承担，允许用保证金） | #9210：本金 REVERSAL Client→渠道 90；`CHANNEL_COST` OE→渠道 15（DISPUTE_FEE） | Client −90：AVAILABLE → PENDING → 保证金 → 追偿应收 | Dr Client 应付 90 / Cr 渠道应收 90；Dr 渠道费用 15 / Cr 应计 15 | `DisputeOutcomeCorrected` → 新事实 supersedes |
| C4 | 重复扣款 `DUPLICATE_CAPTURE` / `_COMPENSATION` | `DuplicatePaymentDetected`（A2 100）、`DuplicatePaymentRemediated` | #9300：`DUPLICATE_AMOUNT` 渠道→消费者 100；#9301：REVERSAL 消费者→渠道 100 | 两条都是 0 流水批次 | Dr 渠道应收 / Cr 应退消费者款，再反向 | 需加回角色 `CONSUMER` 与组件 `DUPLICATE_AMOUNT` |
| C5 | 成本差异 `COST_ADJUSTMENT` | `ActualChannelCostConfirmed`（SI-77，实际 0.45） | #9420：`CHANNEL_COST` ADJUSTMENT 渠道→OE 0.05（冲减方向由付方 / 收方表达），link #9001 行 5 | 0 流水批次 | Dr 渠道费用应计 0.05 / Cr 渠道费用 0.05 | 二次更正 → supersedes |
| C6 | 渠道到账 `CHANNEL_SETTLEMENT` | `ChannelSettlementConfirmed`（SB-12 / SI-77，毛额 100，扣 0.45，到 BA-9） | #9510：`SETTLEMENT_AMOUNT` 渠道→银行账户 100；`CHANNEL_COST_SETTLED` 银行账户→渠道 0.45；`client_id` 空 | BA-9 BANK_CASH / AVAILABLE 净 +99.55 | Dr 银行存款 99.55 / Dr 应计 0.45 / Cr 渠道应收 100 | 与 C5 顺序不限；需加组件 `CHANNEL_COST_SETTLED` |
| C7 | 出款 `PAYOUT_SETTLED` | 申请时 `FeeQuoteApi` 报价 1（只算不记）→ 出款单冻结 → account 锁 51、银行现金锁 50 → `PayoutSettled`（PO-31） | #9620：`PAYOUT_AMOUNT` 银行账户→Client 50；`PAYOUT_FEE` Client→OE 1（证据 = 冻结报价） | Client LOCKED −51；BA-9 LOCKED −50 | Dr Client 应付 51 / Cr 银行存款 50 / Cr 出款手续费收入 1 | 失败 / 取消无事实，LOCKED 释放；UNKNOWN 不释放 |
| C8 | 缴税完成 `TAX_PAID` | 税务创建缴税付款 → 银行现金锁 7 → `TaxPaymentSettled`（TP-4） | #9710：`TAX_PAYMENT_AMOUNT` 银行账户→税务机关 7；`client_id` 空 | BA-9 LOCKED −7 | Dr 税务应付 7 / Cr 银行存款 7 | 应付在支付事实的 `SELLER_TAX` 行形成 |
| C9 | 人工调整 `MANUAL_ADJUSTMENT` | 运营 Maker 提交（工单 T-88）→ Checker 批准 | #9800（MANUAL）：`CLIENT_PRINCIPAL` ADJUSTMENT OE→Client 5 | Client +5 —— **进哪个桶待定**（现行映射进 PENDING 且无到期时间） | Dr 调整费用 5 / Cr Client 应付 5 | 更正只能 supersedes 新事实并再审批 |
| C10 | 外汇 `FX_SELL` + `FX_BUY` | `FxDealSettled`（FX-5，50 USD → AED，3.67，点差 0.5%） | #9901 USD：`FX_SOLD_AMOUNT` Client→平台外汇账户 50、`FX_FEE` Client→OE 0.25；#9902 AED：`FX_BOUGHT_AMOUNT` 平台外汇账户→Client 182.58 | USD LOCKED −50.25；AED AVAILABLE +182.58 | USD 账簿 Dr Client 应付 50.25 / Cr 外汇清算 50 / Cr 手续费 0.25；AED 账簿反向 | 两条事实各自币种流有序；汇兑损益归 ledger |

## 案例暴露出的待定点

1. **人工调整进哪个桶**（C9）：建议调整行可指定目标桶（默认 AVAILABLE），或事实头 `balance_available_time` = 批准时间。
2. **目录补充**：角色加回 `CONSUMER`（只用于重复扣款两类事实）；组件加 `DUPLICATE_AMOUNT`、`CHANNEL_COST_SETTLED`；争议费用 `CHANNEL_COST` 行的 `componentCode = DISPUTE_FEE` 进证据。
3. **出款与外汇的锁定金额要含费用 / 点差**（C7、C10）：报价接口必须在锁定前调用。
4. **争议费是否转嫁 Client** 属合同条款（C3 按平台承担）。
5. **C1 自动 PAYMENT 行与现行 account 映射不兼容**（阶段 4 C 线发现，[C-report 第 6 节第 3 项](../../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/tasks/C-report.md)）：现行 account `FactBalanceMappingRule` 中 CHANNEL 只允许 `CHANNEL_COST`，所以 `CLIENT_PRINCIPAL` 渠道 → Client 会被拒绝；`CLIENT_FEE` Client → OE 会再扣 Client 余额 10，与 C1 预期的 Client PENDING +90 不符；`PLATFORM_SERVICE_CHARGE` 合作伙伴 → 平台财务主体也会被拒绝。ledger 的 PAYMENT 走交易规则包、不看行付方，不受影响。本期没有自动 PAYMENT 事实，不触发；需求 B（支付事实首个切片）开始前，先定 account 对 PAYMENT 行的映射（或调整 C1 行的付方 / 收方写法）。

## 退款单如何追溯到原订单（以 C2 为例）

追溯是三层，每层都只存 ID，不建外键：

| 层 | 从哪到哪 | 怎么建立 | 怎么用 |
|---|---|---|---|
| 事实头 | 退款事实 #9105 → 原支付事实 #9001 | `RefundSucceeded` 带原 `paymentAttemptId`（A1）；billing 用 `t_billing_fact_ref (PAYMENT_ATTEMPT, A1)` 找到 PAYMENT 事实，写进 `parent_fact_id`；同时把原事实的 `ORDER`、`ORDER_CONTEXT`、`PAYMENT_INTENT`、`PAYMENT_ATTEMPT` 引用复制到退款事实的引用表，再加自己的 `REFUND_REQUEST`、`CHANNEL_REFUND` | 按订单查：`GET /billing/v1/facts/by-ref?refType=ORDER&refId=` 一次返回支付与全部退款、争议、成本差异，不用沿父链走；按退款单查：`refType=REFUND_REQUEST` |
| 明细行 | 退款行 → 原支付行 | `t_billing_fact_line_link`：`source_line_id` = #9001 的本金行 / 手续费行 / 分润行 / 服务费行，`target_line_id` = #9105 对应行，`linked_amount` = 27 / 3 / 2.4 / 0.6，`link_type = REVERSAL` | 封顶：同一原行的 REVERSAL 链接金额累计 ≤ 原行 `posted_amount`（再退 80 时本金行要再冲 72，累计 27 + 72 = 99 > 90 → 拒绝）；费用拆解接口按链接把退款金额归到原费用项 |
| 下游 | 控制台从分录追到订单 | ledger 分录行 `account_entry_id` → account 流水 → 入账批次（`fact_id`、`fact_version`）→ billing 事实 → `parent_fact_id` / 引用表 → 订单、退款单 | 运营从账本查询的分录抽屉点「流水」到明细账，再点批次里的事实 ID 到 billing 事实详情（按 ID 接口返回头 + 引用 + 行 + 链接） |

两条规则：
- **找不到原事实就等，不生成孤儿退款事实**：`RefundSucceeded` 到达时 PAYMENT 事实还没落（支付事实卡在死信或还在重试）→ 退款消费抛并发异常重投，超限进死信；修好支付事实后重放。
- **退款事实的币种、Client、OE 必须与原事实一致**，否则拒绝进死信；同一退款请求重复到达按来源键幂等。
