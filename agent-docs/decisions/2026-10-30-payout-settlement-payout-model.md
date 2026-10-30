# 2026-10-30 决定：payout 结算出款模型（出款渠道、简化状态机、出款计划与收款账户）

- Decision status: Confirmed（2026-10-29—30 用户在讨论中逐项确认 Q1–Q14 与状态机 03b；本记录为登记稿，用户审阅后生效）
- Implementation status: Not started（首个需求 [payout：收款账户与余额解释](../../requirements/20261030/payout-收款账户与余额解释/requirement.md) 阶段 1 评审稿）
- Deployment status: Not deployed（`mor_payout` 尚未创建，payout 只有 2026-08-28 七表模型）
- Decision date: 2026-10-30
- 详细设计：[design-discuss/payout/design.md](../../design-discuss/payout/design.md)（第 8 节字段级模型、第 9 节各系统改动、第 10 节切片）与 `payout.pen` 九张画板；行业调研见 [research-industry-payout.md](../../design-discuss/payout/assets/research-industry-payout.md)。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 取代与修订范围

- [2026-08-28 Finance 手工出款与共同余额成熟精简设计](../plans/2026-08-28-finance-manual-payout-simplification-design.md)：**取代**其中的出款单 / 执行状态机（第 7 节）、"删除自动出款周期"（第 1 节）、"执行对手方只有自有银行与人工提交"、锁定与现金占用以 Journal 表达（第 6 节）、`journal_entry_id` / `active_slot` / `cash_account_binding_ver_id` 等字段；**保留**单表收款账户、金额恒等式与费用模式、四个 Risk 检查点（RCO-I09）、PAID 只由权威确认、UNKNOWN 不释放额度、`t_payout_event` 连续审计、第 3 节共同成熟（已由 account 实现）。
- [2026-10-20 channel-connector 统一对外出口决定](./2026-10-20-channel-connector-unified-outbound.md)第 2 条：渠道由"两类"**修订为三类**，新增出款渠道 `PAYOUT`（已在该记录追加修订说明）。
- [2026-10-18 account / ledger 决定](./2026-10-18-account-ledger-subledger-control-account.md)第 6、11 条与 §8.3 两步占用不变；本决定补充 `CASH_ACCOUNT` 的渠道备付余额种类。
- [2026-10-20 reconciliation 决定](./2026-10-20-reconciliation-facts-via-billing.md)不变：出款事实仍由 payout 宣布；本决定补充服务商出款渠道账单对账与"退回第一版仍人工归类"。
- `AGENTS.md` 固定边界中"Manual Payout 固定双人审批、预建 Execution、人工银行提交、完整 CONFIRMED 借记对账后 PAID"的表述描述的是**自有银行人工渠道**（切片 S2），继续成立；服务商 API 渠道（S3）按本决定 P7 以渠道终态确认。

## 1. 背景

payout 只有 2026-08-28 从 Finance 切出的七表模型，没有代码。该设计把执行对手方限定为"自有银行 + 运营在银行门户打款"，删除了自动出款周期，状态机 10 + 7 个状态。2026-10-29 用户要求先做行业调研再设计；三轮讨论后确认：第一版只做结算出款，但模型按五个独立维度建，执行对手方改为出款渠道，状态机简化，恢复出款计划，退回延后。

## 2. 决定

| # | 决定 |
|---|---|
| P1 | **定位：** payout 是"清偿既有应付余额的执行系统"。谁欠谁多少钱由 account 说了算；payout 按合规门禁、审批策略，经 channel-connector 路由到的出款渠道把钱送到已核验的收款账户，并把渠道 / 银行确认的结果作为资金事实交还 billing。它不创造债务、不记分录、不碰原始账号、不直连外部。 |
| P2 | **范围与维度：** 第一版只做 Client / Local Partner 的结算出款（对单一 OE × 币种的 AVAILABLE）。出款单按付款方 / 收款方 / 发起来源 / 审批策略 / 执行渠道五个互不绑定的维度建模：`payee_type=OWNER_SELF`（预留 PAYEE）、`origin ∈ {OWNER_PORTAL, OPS_ON_BEHALF, SCHEDULE}`、`approval_policy=FIXED_DUAL`（预留）、渠道坐标冻结在执行上。代发 / 分账（2026-10-23 决定 U11 / U14）后续只加枚举值、批次与审批策略。 |
| P3 | **执行渠道：** 出款执行的对手方是出款渠道，与收单对称；自有银行只是其中一种。channel-connector 新增第三种 `channel_type=PAYOUT`，既收单又出款的服务商建两个渠道身份；出款账户（类比 MID，绑定 OE，绑定现金来源）、出款账户能力（国家 / 币种 / 轨道 / 收款方类型 / 限额 / 截止与日历 / 收款账户字段要求）、出款路由策略（类比 MID 路由，一开始就用）、成本方案、出款入口 `PayoutOperationApi`（CREATE / QUERY，只接受 PAYOUT 渠道、只供 payout 调用）、回调转事件 `PayoutChannelResult` 都归 channel-connector。payout 不建 rail / routing 表，执行上冻结 `channel_id · channel_payout_account_id · rail_code · channel_ref · channel_result_json`，运营可逐单改选。第一版接自有银行人工渠道 + 一家 API 服务商。 |
| P4 | **资金模式：** 两种都支持。预充值服务商 → account `CASH_ACCOUNT` 新种类 `CHANNEL_FLOAT`（备付余额，占用 / 释放 / 借记与 BANK_CASH 同一套逻辑）；后结算服务商 → 应付服务商 + reconciliation 净额对账。自有银行 → 现有 BANK_CASH。出款使用的现金来源必须与被扣余额同主体（2026-10-18 决定第 5 节）。 |
| P5 | **状态机（画板 03b）：** 出款单 PENDING_APPROVAL → APPROVED → PAID，终态 CANCELLED（`close_reason` 区分拒绝 / 撤回 / 取消 / 锁定失败），加 `active_execution_id`；执行 CREATED → SUBMITTED → (UNKNOWN) → SETTLED / FAILED（`submitted_time` 为空的 FAILED = 未提交放弃；`failure_class` TEMPORARY / PERMANENT）。门禁阻断不是状态（`blockers` 查询）；活动执行、UNKNOWN、需重试由出款单状态 + 活动执行状态查出。RETURNED 状态与退回事件**后续**（P7）。 |
| P6 | **资金动作：** 创建出款单与创建执行都是"本地事务先建单 + 补偿事件 → 调 account `BalanceMovementApi` → 短事务确认"两步（`lock_confirmed` / `cash_locked`）；Owner 锁定 / 释放用 PAYOUT_LOCK / PAYOUT_RELEASE，现金占用用同 API 的 BANK_CASH / CHANNEL_FLOAT 科目，按来源 ID 幂等；锁定失败 → CANCELLED(INSUFFICIENT_BALANCE)，占用失败 → 执行 FAILED。UNKNOWN 不释放任何额度；只有权威未扣款才 FAILED 并释放现金。 |
| P7 | **确认来源与对账依赖：** PAID 只由"确认来源"触发，按渠道而定：自有银行 = reconciliation 借记匹配事件 `BankDebitMatched`（每个执行一条，按匹配 ID 幂等；payout 提交时发 `PayoutExecutionSubmitted` 登记预期流水）；服务商 = 渠道终态事件 `PayoutChannelResult`，reconciliation 只做服务商账单 ↔ 执行匹配并发 `ActualPayoutCostConfirmed`，以及充值 / 结算 ↔ 银行流水。payout 消费结论、不读对账过程与表；依赖单向。**退回：** 第一版不建 `BankDebitReturned` / `PayoutReturned`，退回流水按 reconciliation 现稿人工 `CLASSIFY PAYOUT_RETURN_PENDING` 并线下通知，出款单保持 PAID；真实发生后再定。 |
| P8 | **费用与事实：** 创建出款单时调 billing `FeeQuote(PAYOUT)` 只算不记，报价证据冻结在单上；PAID 时发 `PayoutSettled`，billing 生成 `PAYOUT_SETTLED`（`PAYOUT_AMOUNT` + `PAYOUT_FEE` 两行，服务商渠道另加渠道成本预估行），account 借记 Owner LOCKED 与现金 LOCKED，ledger Dr 应付 / Cr 银行存款（或备付余额）；对账确认实际成本后生成成本差异事实。利润 = 出款费（commercial 卖出价）− 渠道成本（channel-connector 买入价）。 |
| P9 | **出款计划：** 恢复自动按计划出款，做成 Owner × OE × 币种的"出款计划"配置（manual / daily / weekly / monthly、最低留存余额、最小出款额、默认收款账户、轨道偏好、费用模式），XXL-Job 按日历生成**待审批**出款单（`origin=SCHEDULE`，按 `schedule_id + run_date` 防重）；审批与执行链不变。Owner 可否自选随 commercial 合同条款。 |
| P10 | **审批与门禁：** 第一版固定双人 Maker-Checker（创建、取消各一套），`approval_policy` 预留阈值 / 授权即过；四个 Risk 检查点（RCO-I09）全部保留，服务商渠道的外部提交检查点由系统触发；结算控制、开通项、收款账户状态、保证金 / 追偿为每次命令重新检查的门禁。 |
| P11 | **收款账户：** 单表 `t_beneficiary_account`，原值只进 Vault；新增 / 变更重走 Maker-Checker（不同人），核验通过后冷却 `cooling_hours`（默认 24h）才可用于出款，邮件 + Webhook 通知 Owner；同 Owner × 币种可设默认账户；执行 `failure_class=PERMANENT` 自动 SUSPENDED；收款账户字段要求来自所经渠道能力（S1 过渡期由 payout 内置静态清单）。 |
| P12 | **结算单与查询：** payout 记录本次出款清偿到的 account 流水与 billing 事实指针；结算单由 BFF 聚合 billing 事实生成，payout 不复制事实；余额解释经 account 余额查询合成。 |

## 3. 影响

- payout：从零建 api / core / app / test；模型按 design.md 第 8 节（七表改造 + `t_payout_schedule`）。
- channel-connector：PAYOUT 渠道类型、出款账户与能力、出款路由策略、成本方案扩展、`PayoutOperationApi`、回调转事件。
- account：`CHANNEL_FLOAT` 现金账户种类与科目；`BalanceMovementApi` 复用。
- billing：`FeeQuoteApi`（scene=PAYOUT）、`PAYOUT_SETTLED` 渠道成本预估行、消费 `ActualPayoutCostConfirmed` 与充值事实。
- reconciliation：切片 3（出款预期流水、借记匹配）；新增服务商出款账单与充值 / 结算对账。
- compliance：结算控制 Processor 与事件；commercial：已有 PAYOUT 产品与开通项，补开通项状态查询；risk-decision：无结构改动。
- Platform / BFF / Web：注册块、语言包、Webhook `payout.*`、Client 与 Operations 页面；Partner 提款第一期由运营代操作。
- 切片：S1 收款账户与余额解释 → S2 结算出款（自有银行人工渠道）→ S3 服务商 API 渠道 → S4 出款计划 → S5 结算单 / 看板 / 代 Partner 操作。

## 4. 实施修订

- **2026-10-30（S1 阶段 2，S-01）：** `t_beneficiary_account` 删除 `bank_account_blind_index`、`bank_account_blind_key_ver`、`vault_record_version`、`vault_metadata_hash` 四列（现有 Vault 客户端无来源、无消费者）；改为原值整体存一个 REFERENCE Token，另存账号规范值的 STABLE Token `bank_account_token` 用于同 Owner 查重与跨 Owner 共用提示（同 client-master 做法）。
- **2026-10-30（S1 阶段 2，S-03）：** S2 出款单创建的两个前置：commercial 开通项状态查询、compliance `SettlementControlApi` 实现。
