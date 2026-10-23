# reconciliation 对账：只认定事实、记账经 billing 的职责与模型决定

- Decision status: Confirmed（2026-10-20 两轮：第一轮确认第 1–15 条；第二轮确认第 16–24 条，并修订第 6、15 条与 Q4 行结构）
- Implementation status: Not started（`paytech/reconciliation` 仍为空壳，只有 `t_channel_account` 只读代码）
- Deployment status: Not applicable
- Decision date: 2026-10-20
- 用户决定：2026-10-20 在 [design-discuss/reconciliation](../../design-discuss/reconciliation/design.md) 的讨论中同意边界与链路；Q2 改为「不回查 Payment」，Q3 选方案 A，Q5 选方案 C，Q10 定为「暂不自动创建、先手动」，其余（Q1、Q4、Q6–Q9、Q11）按建议。第二轮用户确认：银行到账与在途累计不一致的处理、内部推送 / 外部账单对称命名、表收到 13 张，以及 Q12–Q16、缺口 G1–G15「能闭环的做，要等真实对接的先人工处置或暂缓」。
- 详细设计：[职责与设计](../../design-discuss/reconciliation/design.md)（第 9.2 节为第二轮逐项结论）、画板 [reconciliation.pen](../../design-discuss/reconciliation/reconciliation.pen)（四张：职责边界与三段账户；业务流程与走数例子；领域模型；用例、枚举与问题）。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

`mor_reconciliation` 的 18 张表来自旧 Finance 单体：对账直接生成 `CHANNEL_BATCH_SETTLEMENT`、`CHANNEL_BANK_MATCH`、`LATE_DEBIT` 三种 Journal，处置挂会计 Instruction。[2026-10-18 account / ledger 决定](./2026-10-18-account-ledger-subledger-control-account.md)与 [2026-10-19 billing 决定](./2026-10-19-billing-fact-engine.md)确定：改变余额的金额必须先成为 billing 事实，再由 account、ledger 入账，ledger 只有一个上游。旧对账模型与此冲突。

## 2. 决定

1. **职责。** reconciliation 回答两个问题：交易层（渠道清算文件每一行对应平台哪一笔预期应收或应付）、银行层（应到或应付的钱是否真的进出平台银行账户），并把结论以事件交给 billing。拥有：渠道清算账户、预期清算明细、清算批次与清算行、交易匹配、实际渠道成本确认与分配、平台银行账户与用途绑定、银行对账单与流水、预期银行流水、银行匹配、对账异常与处置。不拥有：渠道 / 技术账号 / MID 主数据（channel-connector）、金额计算（billing）、分录与余额（ledger / account）、银行账号明文（Vault）、出款与缴税是否完成（payout / tax）。
2. **不写分录。** 旧模型的三种 Journal 全部改为事件 → billing 事实。对账只为渠道资金登记事实；出款、缴税的银行扣款只做匹配并发 `BankDebitMatched`，由 payout / tax 发 `PayoutSettled` / `TaxPaymentSettled`。
3. **三段账户、两次登记。** 渠道待清算应收 / 应付 →（批次逐笔对平，`ChannelBatchReconciled` → billing 新增 `CHANNEL_BATCH_RECONCILED`）→ 渠道结算应收 / 应付 →（银行流水确认，`ChannelSettlementConfirmed` → billing `CHANNEL_SETTLEMENT`）→ 银行现金。实际渠道成本与预估不同时另发 `ActualChannelCostConfirmed` → `COST_ADJUSTMENT`。`CHANNEL_BATCH_RECONCILED` 一条事实四类行：结算应收或应付（净额）、待清算应收冲销、待清算应付冲销、实际渠道费（`CHANNEL_COST_SETTLED`）（Q4）。
4. **预期明细来自 billing 事实。** 对账消费 `BillingFactRecorded` 生成预期清算明细，不直接消费 Payment 事件；按 `billing_fact_id` 幂等。
5. **渠道交易引用不回查（Q2）。** Payment 侧事件统一带 `channelTransactionRefId`、`referenceType`、`externalTransactionId` 三项；billing 新增引用类型 `CHANNEL_TRANSACTION_REF`（`ref_id` 存内部引用 ID，引用行加可空列 `external_reference_type`、`external_transaction_id`），随 `BillingFactRecorded` 带出；对账正常流程不调用 Payment。`PaymentSucceeded` 现只有外部交易号（字段 `channelTransactionRef`），需补另两项；`RefundSucceeded`、`DisputeLost`、`DuplicatePayment*` 尚未实现，实现时按此携带。
6. **处置只在对账审批一次（Q3）。** Maker-Checker 批准的同一本地事务内处置即 `APPLIED`、异常 `RESOLVED`；金额类处置（`BILLING_ADJUSTMENT`、`WRITE_OFF`）登记 `ReconAdjustmentApproved`，billing 生成 AUTO 事实 `RECON_ADJUSTMENT`（组件 `RECON_DIFFERENCE`，引用 `RECON_RESOLUTION`），不走 billing 的人工审批，不复用 `MANUAL_ADJUSTMENT`。处置表删除 `instruction_id`，由 billing 事实反向引用处置 ID。（第二轮修订：处置类型收为 `RE_MATCH`、`ACCEPT_TIMING_DIFFERENCE`、`CLASSIFY`，`BILLING_ADJUSTMENT` / `WRITE_OFF` 并入 `CLASSIFY`，见第 18 条。）
7. **只暂记借方银行流水（Q5）。** 迟到扣款 `LATE_DEBIT` 导入即暂记并发 `LateBankDebitDetected` 给 payout / tax；其他未识别借方过 1 个工作日窗口后暂记；未识别贷方只挂异常、期末列示；金额不符的已确认部分正常入账、差额挂异常。暂记经 `BankLineSuspended` → billing 新增 `BANK_SUSPENSE`。已暂记流水（`SUSPENDED`）只能经处置重分类，不再改变银行现金。**payout 前置条件**（切片 3 之前定稿）：收到 `LateBankDebitDetected` 后冻结该 Client 后续出款或重新锁定对应金额；认定实际已付时不发会扣现金的 `PayoutSettled`，改为只冲 Client 应付、贷记暂记科目；tax 同理。
8. **拒付按渠道实际扣款时记账（Q9）。** Payment 在渠道拒付扣款被确认时发布带渠道交易引用的扣款事件，billing 生成 `CHARGEBACK_DEBIT`（待清算应付增加、借记争议暂扣，不动 Client 余额）；渠道退回时生成 `CHARGEBACK_REVERSAL`；`DisputeLost` 的 DISPUTE 事实只做 Client 侧结转，不再产生渠道应付。暂扣由渠道扣款触发、不由开案触发，与「`DisputeOpened` 不记账」不冲突。事件名与 DISPUTE 行结构在 billing、争议实现时定稿。
9. **清算账户手工创建（Q10）。** 暂不消费 `MidActivated` 自动创建；缺清算账户时 billing 事实停在补偿 ERROR 重试、建好自动恢复；控制台提示已激活但未建清算账户的 MID + 币种。清算账户提供解析接口 `POST /reconciliation/v1/clearing-accounts/resolutions`（MID + 币种 → 清算账户 + 经营主体）供 billing 事务外调用。
10. **渠道文件（Q1）。** 解析为统一批次归 channel-connector；对外拉取是否经 Platform Integration 执行，实现自动拉取时对齐 [2026-09-21 决定](./2026-09-21-platform-integration-http-execution.md)。MVP 只支持控制台上传标准文件。
11. **银行匹配人工确认（Q6）。** 系统只生成 `PROPOSED` 建议；稳定后对「金额相等 + 参考号一致」再开放自动确认。
12. **配置放在清算账户上（Q7）。** `unmatched_wait_days`（默认 3）、`cost_tolerance_amount`、`cash_grace_days`，不建独立配置表。
13. **本期不支持多币种结算（Q8）。** 批次币种必须等于清算账户币种。
14. **更正文件（Q11）。** 批次加 `revision`，唯一键 `(clearing_account_id, channel_batch_ref, revision)`；未对平的旧版可作废（`VOIDED`）后导入新版；已 `RECONCILED` 一律拒绝，差额走后续批次 ADJUSTMENT 行。
15. **模型 18 → 16 张（第二轮再收到 13 张，见第 17 条）。** `t_channel_account` → `t_clearing_account`；`t_channel_open_item` + `t_channel_debit_item` 合并为 `t_clearing_item`（`direction` 区分应收应付，来源 `PAYMENT`、`DUPLICATE_CAPTURE`、`REFUND`、`DUPLICATE_COMPENSATION`、`CHARGEBACK`、`CHARGEBACK_REVERSAL`）；`t_channel_cost_confirm` + `t_channel_cost_alloc` 合并为 `t_cost_allocation`；新增 `t_expected_bank_movement`（渠道批次净额 / 出款 / 缴税统一为银行层目标）；删除 `t_duplicate_capture_item`（补偿流程归 Payment）；其余改名见详细设计第 6 节。消费幂等靠业务唯一键，不另建业务消费表（实施修订 2026-10-20：开启 MQ 消费时公共消息库要求本库另有 `t_consumed_event`、`t_consumed_aggregate_version` 两张去重机制表，与 billing、account 相同，库内共 15 张）。关账不被对账异常阻塞，对账为关账报告提供在途与开放异常汇总。
16. **Q12–Q15（第二轮确认）：** Q12 不发 `CashAccountActivated`，财务手工在 account 开 `CASH_ACCOUNT` 户；Q13 业务日期取处理当天，落入已关账期间时记到下一开放期间开始日；Q14 中间行扣费挂 `BANK_AMOUNT_MISMATCH`，人工归类 `BANK_FEE`，不做容差内自动草稿；Q15 切片 1 含 Operations BFF 与最小页面（导入、列表、异常处置）。

### 第二轮（2026-10-20）

17. **命名与 13 张表。** 内部推送叫「平台流水」`t_platform_txn`（原 `t_clearing_item`），外部账单叫「渠道账单 / 账单行」`t_channel_statement` / `t_channel_statement_line`（原 `t_settlement_batch` / `t_settlement_line`），与银行侧 `t_bank_statement` / `t_bank_statement_line` 对称；不用「入账流水」，避免与 account 的入账流水撞名。交易匹配与实际成本记在账单行上（一行最多对一笔平台流水），删除 `t_transaction_match`、`t_cost_allocation`；用途并入 `t_cash_account.account_purpose`，删除 `t_cash_account_binding` 与 `_ver`；新增 `t_clearing_account_mid`（一个清算账户含多个 MID，唯一 `(mid_id, currency)`，G2）。共 13 张：清算账户、清算账户 MID、平台流水、渠道账单、账单行、银行账户、银行对账单、银行流水、预期银行流水、银行匹配、异常、处置、补偿事件。银行匹配保留多对多。
18. **手工优先与通用出口。** 要等真实对接才清楚的场景不写专门逻辑，统一走「异常 → `CLASSIFY` 处置（带归类码 `ReconClassification`）→ 经 billing 记账」。入账路径与对方科目由异常来源对象决定：渠道账单行的归类随该账单 `ChannelBatchReconciled` 作为已归类调整行带出（Q4 增加组件 `CHANNEL_BATCH_ADJUSTMENT`），不单独发事件；平台流水、预期银行流水、银行流水的归类经 `ReconAdjustmentApproved` → `RECON_ADJUSTMENT`，对方科目分别为待清算应收应付、结算应收应付、银行现金（已暂记则暂记科目）。初始归类码：`CHANNEL_FEE_OTHER`、`CHANNEL_RESERVE_HOLD`、`CHANNEL_RESERVE_RELEASE`、`BANK_FEE`、`BANK_INTEREST`、`INTERNAL_TRANSFER`、`UNALLOCATED_RECEIPT`、`PAYOUT_RETURN_PENDING`、`RECON_DIFFERENCE`；科目映射在 ledger 规则包。
19. **银行到账与在途累计不一致。** 银行层对每一次打款而非在途余额。合并到账、分笔到账、负净额滚动抵扣由人工多选银行匹配解决：同一银行流水的确认匹配按方向带符号求和不超过流水金额、且同一银行账户；自动建议只给一对一。新预期银行流水生成时对未匹配流水重新建议（钱比账单先到），确认后原异常自动关闭。中间行扣费（Q14）、渠道保证金扣留与释放（Q16）、与交易无关的渠道费走人工归类。
20. **本期做的结构性规则。** 跨账单查重（同清算账户未作废账单内「引用类型 + 外部交易号 + 行类型 + 方向 + 金额」相同 → `DUPLICATE_SETTLEMENT`，G4）；标准渠道账单最小字段集，渠道特有字段放 `raw_attributes`（G3）；银行对账单导入校验期初 + 流水 = 期末（不通过拒绝）与上下张相接（不通过开不阻塞的 `BANK_STATEMENT_GAP`，G7）。
21. **先人工处置的场景。** 渠道有、平台没有（G1，人工到 Payment 核实后 `RE_MATCH`）；上游冲正（G5，平台流水超期异常后人工处置）；与渠道 / 出款 / 缴税无关的银行流水（G8）、自有账户调拨（G9）、出款退回（G10）、非交易渠道费（G11）、渠道保证金（Q16）均经 `CLASSIFY`；`UNALLOCATED_RECEIPT`、`PAYOUT_RETURN_PENDING` 进待处理负债，后续由财务经 billing `MANUAL_ADJUSTMENT` 转最终科目。
22. **暂缓。** 期末报告只做控制台查询（G13）；异常只显示账龄，不做分派与到期日（G14）；`OrphanChannelTransactionDetected`、保证金专门行类型、中间行扣费自动草稿随真实数据频率再定。
23. **权限（G15）。** 两个权限点：对账操作、对账审批；Maker 与 Checker 不得同人。
24. **切片 1 范围。** 渠道资金主链路全部人工兜底：清算账户与 MID、平台流水（PAYMENT、REFUND）、渠道账单导入匹配对平、银行账户、银行对账单与人工匹配、三种处置、向 billing 发四类事件、Operations BFF 与最小页面。依赖 billing 新增 `CHANNEL_BATCH_RECONCILED`、`RECON_ADJUSTMENT`、引用类型，`PaymentSucceeded` 补字段，`RefundSucceeded`（未就绪先只做 PAYMENT），ledger 规则包映射归类码。超期 Job、借方暂记、拒付与重复扣款在切片 2；出款缴税在切片 3。

## 3. 取代范围

| 被取代的内容 | 取代方式 |
|---|---|
| [`FINANCE_DATABASE.md`](../database/FINANCE_DATABASE.md) §4.5 中对账直接生成 Journal（`CHANNEL_BATCH_SETTLEMENT`、`CHANNEL_BANK_MATCH`、`LATE_DEBIT`）、处置挂 Instruction、`duplicate_capture_item`、表名与 open / debit item 分表；§6.2 状态机 | 由本决定第 2–8、14、15 条取代；两层对账分离、三段账户含义、批次对平门禁、同一流水用途一致与累计不超额、净额不跨清算账户等规则继续成立 |
| `paytech/reconciliation/docs/database/reconciliation.pdma.json` 与 Schema SQL 的现有 18 张表 | 随实现切片按第 15、17 条修改为 13 张；未执行前现有 Schema 仍是代码依据 |
| [billing 决定](./2026-10-19-billing-fact-engine.md)第 4 条事实类型清单、DISPUTE 事实含渠道应付的口径 | 增加 `CHANNEL_BATCH_RECONCILED`（含组件 `CHANNEL_BATCH_ADJUSTMENT`）、`RECON_ADJUSTMENT`、`BANK_SUSPENSE`、`CHARGEBACK_DEBIT`、`CHARGEBACK_REVERSAL`；DISPUTE 只做 Client 侧结转（第 3、6–8 条）；引用类型增加 `CHANNEL_TRANSACTION_REF`、`RECON_RESOLUTION` |
| `PaymentSucceeded` Payload v2 的渠道引用字段 | 补 `channelTransactionRefId`、`referenceType`（第 5 条），Schema 版本随实现递增。**修订（2026-10-23，需求 B Q16）：** 已实现为 v2 末尾追加两个可空字段 `channelTransactionRefId`、`channelReferenceType`，`schemaVersion` 保持 2（与 card BIN 追加字段做法一致，消费方按字段名读取）；"随实现递增"只适用于非兼容变更 |

## 4. 已知代价

- billing 新增 5 种事实类型、2 种引用类型、引用行 2 列，ledger 规则包相应增加映射（含归类码与若干新科目）。
- 手工优先意味着上线初期运营处置量较大；高频场景要按真实数据逐个改成自动。
- payout 必须实现迟到扣款的冻结与「已付不重复扣现金」分支，否则切片 3 不能上线。
- 清算账户手工创建，MID 上线流程多一步运营操作；漏建时支付事实延迟入账。
- 拒付暂扣改变了 DISPUTE 事实的行结构，争议与 billing 实现需同步。

## 5. 不变的边界

- account 只消费 billing 事实，ledger 只消费 account 批次（2026-10-18 决定）。
- 事务公理：事务内只写本库，调 billing、读 Platform File 在事务外，对外事件经补偿事件表发出。
- 银行账号只存 Vault 令牌、盲索引与末四位；无跨库外键；PDManer 为设计源、人工 SQL 规则不变。

## 6. 后续

- 进入实现前按[需求交付流程](../REQUIREMENT_DELIVERY_WORKFLOW.md)登记切片 1 需求（本次未开始）。
