# payout（出款）设计讨论

> 状态：**已确认（2026-10-30）**，决定记录 [2026-10-30 payout 结算出款决定](../../agent-docs/decisions/2026-10-30-payout-settlement-payout-model.md)（P1–P12）；首个需求 [payout：收款账户与余额解释](../../requirements/20261030/payout-收款账户与余额解释/requirement.md)（阶段 1 评审稿）。调研（第 2–3 节）→ 三轮问答（第 4、6、7 节）→ 用户已定全部问题：Q1 范围、Q2 出款计划、Q3 / Q5 执行渠道、Q4 退回延后、Q6 四检查点、Q7 固定双人、Q8 账户变更风控、Q9 结算单、Q10 UNKNOWN、Q11–Q14 渠道、状态机取 03b。第 8–10 节是据此写的字段级模型、各系统改动清单与切片，供登记决定记录与首个需求使用。未改代码、模型与权威文档。调研原始笔记见 [assets/research-industry-payout.md](assets/research-industry-payout.md)。配图 [payout.pen](payout.pen) 九张画板（01 定位与职责边界；02 概念模型与五个独立维度；03 状态机与资金动作（现行）；03b 状态机简化提案；04 一笔出款的走数例子（自有银行人工）；05 跨系统合同、用例与待定；06 完整时序：自有银行人工渠道；06b 完整时序：服务商 API 渠道（预充值）；07 出款渠道：修正后的执行模型），导出 `assets/01-responsibilities.png` … `07-payout-channel.png`。**2026-10-29 第三轮修正：** 执行对手方从“自有银行”改为“出款渠道”（第 7 节），用户已定 Q11–Q14，01 / 02 / 05 / 06 板已按此回改，03 / 03b / 04 不受影响（04 只是自有银行渠道的例子）。用户 2026-10-29 已定：先只考虑结算出款；代发（Client 维护自己的收款人并向其出款）只预留维度，见第 4 节 Q1 与画板 02。

## 1. 我们今天有什么

| 项 | 现状 | 出处 |
|---|---|---|
| 服务 | `paytech/payout`：只有 7 张表的 PDManer 模型与 Schema SQL（由 Finance 切分），**无代码** | `paytech/payout/README.md` |
| 模型 | `t_beneficiary_account`、`t_finance_restriction_proj`、`t_payout`、`t_payout_approval`、`t_payout_execution`、`t_payout_evidence_ref`、`t_payout_event`（+ `t_compensation_event`） | `paytech/payout/docs/database/payout.pdma.json` |
| 流程设计 | Manual Payout：Owner（Client / Local Partner）对单一 OE × 币种的 AVAILABLE 主动申请 → 创建即锁定 → 固定双人审批 → 预建 Execution 占用银行现金 → 运营人工银行提交 + 回执 → 银行借记完整对账后 PAID；Risk 四检查点（创建 / 终审 / 执行创建 / 外部提交）；UNKNOWN 不释放、禁重试与取消 | [2026-08-28 设计](../../agent-docs/plans/2026-08-28-finance-manual-payout-simplification-design.md)，AGENTS 固定边界 |
| 余额 | account 明细账：Owner × OE × 币种的 PENDING / AVAILABLE / LOCKED 桶、保证金、追偿应收；银行现金额度 `CASH_ACCOUNT` 的 BANK_CASH AVAILABLE / LOCKED；桶移动不产生分录 | [2026-10-18 决定](../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)、[account/ledger V3](../finance-account-ledger-v3/design.md) §8.3 |
| 费用与事实 | billing `FeeQuoteApi(PAYOUT)` 只算不记，报价冻结在出款单；银行确认后 `PayoutSettled` → `PAYOUT_SETTLED` 事实（本金行 + 手续费行） | [billing 设计](../billing/design.md) Q13 |
| 银行侧 | 银行账户与"主体 + 币种 + 用途（PAYOUT）"绑定归 reconciliation；出款银行账户必须与被扣余额同主体；出款借记对账属对账切片 3，未开始 | [reconciliation 设计](../reconciliation/design.md) |
| 门禁 | 出款能力 = 合同上出款通道开通项（`PAYOUT_LOCAL / PAYOUT_SWIFT…`）ENABLED；收款账户 VERIFIED、结算控制、保证金为运行期检查 | [client-activation](../client-activation/design.md) §4 |
| 商业模式 | 出款对象 = 某账本里对某主体的应付余额，一个流程覆盖 Client 结算款、推荐分成、子商户 / 分账（U11 / U14，后两类后续） | [2026-10-23 决定](../../agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md) §9.3 |

## 2. 行业通常怎么做（摘要）

详见调研笔记。要点：

1. **"payout"有三类**：结算出款（平台付给商户的应付余额，Stripe Connect payouts / Adyen sweeps / PayFac sub-merchant funding）、代发 / 分账付款（商户用自有余额付第三方）、退款到卡。前两类共用执行层与收款方管理，差在资金来源与收款方入驻。
2. **余额模型**：pending → available 两桶起步；成熟时点（T+N）与出款计划分离；负余额不出款、可反向扣款或用平台储备兜底；可设最低留存余额；储备分滚动储备（合同条款）与出款冻结（风控临时）。
3. **触发方式**：自动按计划（日 / 周 / 月）、自动按阈值（trigger / target）、手动按需、即时（高费率、限额、负余额不可用）、按履约事件。节奏要匹配收款方类型。
4. **生命周期**：待审批 → 等资金 / 定时 → 执行中 → 成功（轨道受理 vs 银行确认两层）/ 失败（未扣款，余额回滚）/ **退回**（已扣后几天由收款行退回，独立记账）/ 取消。失败原因分临时 / 永久，永久则禁止重试并封锁收款账户。
5. **收款账户**：独立实体、先验证后用（微存款 / 开放银行 / 户名匹配）、按国家 / 币种 / 轨道动态字段、删除不可逆、变更视为高风险事件（冷却、重核、通知）。
6. **费用与金额**：外扣 / 内扣、按轨道与金额定价、FX 三步（报价 → 绑定报价创建 → 跟踪）、按轨道的单笔上限与速率限制。
7. **运营控制**：Maker-Checker 按阈值分级、批次 / 付款运行（控制合计、逐项幂等、部分成功逐项对账）、人工银行门户提交为无 API 时的通用做法、Owner 级 / 全局暂停、未提交前可取消。
8. **合规**：出款前对收款方筛查并随名单更新重筛；`payouts_enabled` 与收款资格分开；PayFac 经手资金要么 FBO 账户要么收单行直付，出款银行账户必须是资金托管方的账户。
9. **记账与对账**：出款 = 清偿应付，锁定不产生分录；出款单 ↔ 回执 ↔ 对账单三方对账；日报、每笔出款组成明细、结算单。
10. **GCC 轨道**：AE UAEFTS（工作日时段）/ Aani 即时；SA SARIE / 即时层；EG ACH / InstaPay。差异体现在截止时间、日历、单笔上限、是否当日到账，应是"出款通道配置"的属性。

## 3. 用例清单（行业通用 → 我们的覆盖）

| 编号 | 用例 | 行业 | 我们 |
|---|---|---|---|
| P-01 | 维护与核验收款账户（添加、验证、设默认、停用、变更冷却） | 标配 | 模型有（FIN-16），无代码；无冷却 / 通知 |
| P-02 | 查看余额与成熟时间线（pending / available / locked / 负余额 / 保证金） | 标配 | account 有余额；门户解释接口 FIN-I16 未实现 |
| P-03 | 手动申请出款（Owner 门户 / 运营代操作） | 标配 | FIN-13 / 15 设计有 |
| P-04 | 自动按计划出款（日 / 周 / 月，Owner 或平台设定） | 标配 | **无**（2026-08-28 明确删除 Settlement Run） |
| P-05 | 自动按阈值出款 / 最低留存余额 | 常见 | 无 |
| P-06 | 即时出款 | 可选，Gig 场景 | 无，MVP 不需要 |
| P-07 | 出款审批（Maker-Checker，阈值分级，批准 / 拒绝 / 暂挂） | 标配 | 固定双人，有；无阈值分级 |
| P-08 | 出款资格与风控门禁（KYB 状态、结算控制、保证金、筛查） | 标配 | RCO-I09 四检查点设计有；筛查由 compliance |
| P-09 | 费用报价与承担模式、FX 报价 | 标配 | 费用模式有、FeeQuote 已定；FX 无 |
| P-10 | 执行渠道：自有银行人工 / 服务商 API / 银行 API / 批次文件 | 多渠道 | 第一版两种（Q11，第三轮改），其余只加渠道配置 |
| P-11 | 回执与银行受理号回填、状态跟踪 | 标配 | FIN-I09 设计有 |
| P-12 | 失败处理：未扣款释放、原因分类（临时 / 永久）、修复后重试 | 标配 | FAILED → RETRY_REQUIRED 有；无原因分类与账户封锁 |
| P-13 | **已付后退回（returned）**：收款行退回入账、通知 Owner、重出 | 标配 | 第一版人工处理（Q4，2026-10-30 定） |
| P-14 | 取消 / 撤回 | 标配 | FIN-23 有 |
| P-15 | 银行借记对账与完成（PAID）、少扣 / 多扣处置 | 标配 | 设计有（对账切片 3） |
| P-16 | 出款完成登记资金事实 → 记账（清偿应付） | 标配 | billing `PAYOUT_SETTLED` 已定 |
| P-17 | 暂停 / 恢复出款（Owner 级、全局） | 标配 | 结算控制 SETTLEMENT_BLOCK（compliance，只有 API 合同） |
| P-18 | 出款明细 / 结算单（本次出款由哪些交易、退款、费用组成）、下载凭证 | 标配 | 无（billing 有按引用查事实，可作基础） |
| P-19 | 通知（发起 / 到账 / 失败 / 账户需更新；邮件 + webhook） | 标配 | 无 |
| P-20 | 出款日报与运营看板（待审、待提交、UNKNOWN、失败、退回） | 标配 | 无 |
| P-21 | 负余额处理（反向扣款 / 储备兜底 / 180 天清零） | Stripe 式 | 用保证金 + 追偿应收代替，不反向扣款 |
| P-22 | 代发 / 分账付款（第三方收款方、批量指令） | 第二类产品 | U11 / U14 后续 |

## 4. 差距与待讨论（给用户）

调研得出的六点启发（笔记 §5）：控制维度已超行业平均，缺便利性与自动化；缺 returned 路径；缺出款渠道抽象（第三轮改为：渠道归 channel-connector，payout 只冻结坐标）；缺收款账户变更风控；状态机要与执行渠道解耦；分账 / 代发共用执行层。

| 编号 | 问题 | 决定 / 建议 |
|---|---|---|
| Q1 | **已定** 第一版范围 | 只做 Client / Local Partner 结算出款；出款单按五维度建模，收款方枚举预留 PAYEE，费用承担方独立 |
| Q2 | **已定** 恢复自动按计划出款 | 做成“出款计划”配置（manual / daily / weekly / monthly + 最低留存余额 + 最小出款额），系统按计划生成**待审批**出款单，审批与执行链不变；Owner 可否自选随 commercial 合同条款 |
| Q3 | **已定（第三轮改）** 执行方式 = 出款渠道 | 执行上冻结 channel-connector 路由解析出的渠道坐标；第一版自有银行人工 + 一家服务商 API（Q11） |
| Q4 | **已定（2026-10-30）** 已付后退回 | 用户：等实际发生再说。第一版不建 RETURNED 状态与 `BankDebitReturned` / `PayoutReturned`；退回流水由 reconciliation 人工 `CLASSIFY PAYOUT_RETURN_PENDING`（现稿 G10）并线下通知 payout，出款单保持 PAID；服务商回调 RETURNED 同样作为渠道异常人工处理 |
| Q5 | **已定（第三轮改）** 出款通道（rail）归谁 | 归 channel-connector 出款账户能力（轨道、限额、日历、收款账户字段要求）；payout 不建 rail 表；费率仍在 commercial；client-activation 的 `PAYOUT_*` 开通项不变 |
| Q6 | **已定** 四个 Risk 检查点全部保留 | 同命令重放复用 pair；服务商 API 渠道下外部提交检查点由系统自动触发 |
| Q7 | **已定** 第一版固定双人 | `approval_policy` 字段预留 THRESHOLD / OWNER_AUTHORIZED |
| Q8 | **已定** 收款账户变更风控 | 新增 / 变更账户重走 Maker-Checker，核验通过后冷却 `cooling_hours`（默认 24h）才可用于出款，邮件 + Webhook 通知 Owner；首笔不强制额外复核 |
| Q9 | **已定** 结算单 | payout 记录本次出款清偿到的 account 流水指针；结算单由 BFF 聚合 billing 事实生成，payout 不复制事实 |
| Q10 | UNKNOWN 禁止重试与取消是否放宽？ | 自有银行人工渠道保持；服务商渠道用 `PayoutOperationApi QUERY` 收敛 |

## 5. 下一步

（2026-10-30 已全部定案，见第 8–10 节。）

## 6. 2026-10-29 第二轮：状态机简化与对账依赖（用户提问）

### 6.1 出款单状态机是否过于复杂（画板 03b）

是。现行 10 + 7 个状态里有 7 个是"别处的事实"在出款单上的投影。原则：出款单只记自己的事实；门禁结果、活动执行、失败原因用查询和引用表达，不复制成状态。

| 原状态 | 改为 |
|---|---|
| ON_HOLD | 不是状态；每个命令重新检查门禁，阻断原因由 `GET …/payouts/{id}/blockers` 返回 |
| PAYING | `active_execution_id` 非空（同 Payment 的 `active_action_id` 做法） |
| PAYMENT_UNKNOWN | 活动执行状态 = UNKNOWN |
| RETRY_REQUIRED | 回到 APPROVED，`active_execution_id` 为空，执行历史有 FAILED |
| REJECTED | CANCELLED + `close_reason=CHECKER_REJECTED`（撤回、取消同理） |
| ABORTED（执行） | FAILED + `submitted_time` 为空 |
| RETURNED（执行） | 只在出款单上；执行保持 SETTLED，记 `returned_time` 与退回匹配 ID |

简化后：出款单 PENDING_APPROVAL → APPROVED → PAID → RETURNED，终态 CANCELLED（5 个）；执行 CREATED → SUBMITTED → (UNKNOWN) → SETTLED / FAILED（5 个）。不变：PAID 只由银行借记匹配确认、UNKNOWN 不释放额度、每次变化写 `t_payout_event`、四个 Risk 检查点。运营队列（待审批 / 待提交 / 待对账 / UNKNOWN / 需重试 / 被卡住）全部由"出款单状态 + 活动执行状态 + blockers"查出。**待用户选定 03 或 03b。**

### 6.2 payout 是否要关心 reconciliation 的状态

关心结论，不关心过程。PAID 的定义是"银行确实扣了"，只有 reconciliation 能给，因此 payout 消费两个事件：`BankDebitMatched`、`BankDebitReturned`，都按 `execution_id` 定位；不读 reconciliation 的表，不知道匹配进度，不处理差异与暂记（对账异常在 reconciliation 内 CLASSIFY，归到某个执行后才发事件）。依赖单向：reconciliation → payout（事件）；payout → reconciliation 只有一次查询（出款用途银行账户绑定）。将来接银行 API，"确认来源"按 `execution_mode` 配置为渠道终态，reconciliation 退到只报异常，状态机不改。

**事件粒度（用户 2026-10-29 追问）：** 是每个执行一条事件，不是每条银行流水一条。这是 reconciliation 已确认设计（[2026-10-20 决定](../../agent-docs/decisions/2026-10-20-reconciliation-facts-via-billing.md)切片 3）的既有约定：payout 提交执行时发 `PayoutExecutionSubmitted`，reconciliation 登记预期银行流水（UC-R10）；该预期流水匹配 `CONFIRMED` 时发一次 `BankDebitMatched`（UC-R13），按匹配 ID 幂等、经补偿事件表；渠道资金侧 `ChannelSettlementConfirmed` 粒度相同。替代方案（payout 轮询对账表、按对账单批次汇总事件、reconciliation 直写 billing）分别违反依赖方向、粒度和"出款事实只有一个来源"。**退回（用户 2026-10-30 定）：** 不改 reconciliation 现稿，第一版退回仍是人工 `CLASSIFY PAYOUT_RETURN_PENDING` 后线下通知 payout（G10），不建 `BankDebitReturned` 事件；payout 与 reconciliation 之间第一版只有一个入向事件 `BankDebitMatched`。真实发生退回后再定 RETURNED 状态与事件。

### 6.3 完整时序（画板 06）

A 申请（1–8）→ B 审批（9–12）→ C 创建执行（13–18）→ D 人工提交银行（19–23，23b 向 reconciliation 登记预期流水）→ E 对账确认（24–30）→ F 退回分支（31–36）。两处与 2026-08-28 设计不同：① account 是独立服务，锁定（第 6 步）与现金占用（第 17 步）都是"本地事务先建单 + 补偿事件 → 调 account → 短事务确认"的两步，失败由补偿事件兜底（对应 account/ledger V3 §8.3）；② 退回分支为新增。

## 7. 2026-10-29 第三轮：出款渠道（修正执行模型）

**修正：** 01–06 板沿用 2026-08-28 Manual Payout 设计，把执行对手方画成"自有银行 + 运营在银行门户打款"，channel-connector 只当成将来的银行 API。这是理解偏差。正确模型与收单对称：**出款执行的对手方是出款渠道**，自有银行只是其中一种；出款服务商（Rapyd、Thunes、PayerMax 出款、AE / SA / EG 本地服务商）、钱包都是渠道，由 channel-connector 统一对外（[2026-10-20 决定](../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)），payout 只持有执行记录与渠道返回的结果。

### 7.1 渠道决定的五件事

| 渠道种类 | 执行方式 | 资金来源 | 确认来源（SETTLED） | 对账层次 | 成本 |
|---|---|---|---|---|---|
| 自有银行 · 人工 | 运营在银行门户打款，回填受理号 | 我方银行账户（account CASH_ACCOUNT · BANK_CASH） | 银行对账单借记匹配（BankDebitMatched） | 一层：银行流水 ↔ 执行 | 银行手续费 |
| 自有银行 · API | channel-connector 出款入口 / 批次文件 | 同上 | 渠道终态，对账单复核 | 一层 | 同上 |
| 出款服务商 · 预充值 | channel-connector：创建 → 查询 / 回调 | 服务商处备付余额（account 新增 CASH_ACCOUNT 种类 CHANNEL_FLOAT） | 服务商终态 | 两层：服务商出款账单 ↔ 执行；充值 / 结算 ↔ 银行流水 | 服务商成本（channel-connector 成本方案）+ 向 Owner 收的出款费 |
| 出款服务商 · 后结算 | 同上 | 服务商垫付，周期性净额结算 | 服务商终态 | 两层 | 同上 |
| 钱包 / 本地即时轨道 | 经服务商或直连 | 随所经渠道 | 服务商 / 轨道终态 | 随渠道 | 同上 |

### 7.2 模型变化

- payout：删 `t_payout_rail`；执行表 `execution_mode` → `channel_id` + `channel_payout_account_id` + `rail_code` + `channel_ref` + 渠道结果快照；新增出款路由（第一版：收款国家 × 币种 × 轨道 → 渠道 + 出款账户 的配置映射，运营可逐单改）。
- channel-connector：渠道获得 PAYOUT 能力（Q13）；出款账户 = 渠道侧我方账户（类比 MID，绑定 OE）；渠道能力：国家 / 币种 / 轨道 / 收款方类型 / 限额 / 截止与日历 / 收款账户字段要求；成本方案；出款入口 `PayoutOperationApi`（CREATE / QUERY / 回调）只供 payout 调用。
- account：`CASH_ACCOUNT` 增加渠道备付余额种类（预充值渠道），占用 / 释放 / 借记逻辑不变。
- reconciliation：新增"出款渠道账单"对账（账单行 ↔ 执行，交易层；可确认实际成本），充值 / 结算净额 ↔ 银行流水（银行层）；自有银行仍按现稿切片 3。
- billing：`PAYOUT_SETTLED` 事实增加渠道成本预估行，对账确认后 `ActualPayoutCostConfirmed` → 成本差异；利润 = 出款费 − 渠道成本。
- 状态机（03b）不变：SETTLED 的触发源由渠道的"确认来源"决定；UNKNOWN 在 API 渠道下用 QUERY 收敛，禁止重试 / 取消只保留给人工渠道。

### 7.3 已定 Q11–Q14（用户 2026-10-29）

| # | 问题 | 决定 |
|---|---|---|
| Q11 | 第一版接哪些出款渠道 | 自有银行人工 + 一家 API 服务商（经 channel-connector），两种形态验证渠道抽象 |
| Q12 | 服务商资金模式 | 两种都支持：预充值 → account `CASH_ACCOUNT` 新种类 `CHANNEL_FLOAT`；后结算 → 应付服务商 + reconciliation 净额对账；按签约选 |
| Q13 | channel-connector 里出款渠道怎么表达 | **新增第三种 `channel_type=PAYOUT`**（不用能力集合）：既收单又出款的服务商建两个渠道身份（PAYMENT + PAYOUT），各自技术账号、出款账户、能力、成本方案；出款入口 `PayoutOperationApi` 只接受 PAYOUT 渠道、只供 payout 调用；与 [2026-10-20 决定](../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)第 2 条"类型创建后不可改"兼容，需修订其"两类"的表述 |
| Q14 | 出款路由归谁 | **一开始就用 channel-connector 路由策略**：类比 MID 路由——`t_payout_routing_policy`（scope OE / GLOBAL × 收款国家 × 币种 × 轨道 × 收款方类型）→ 候选出款账户（优先级 / 权重），静态能力过滤 + 健康状态排序；payout 调解析接口得到渠道 + 出款账户，冻结在执行上，运营可逐单改选 |

### 7.4 回改记录（画板）

- 01：一句话与系统上下文改为"经 channel-connector 路由到的出款渠道"；channel-connector 从后续依赖改为第一版依赖。
- 02：维度"执行方式"改"执行渠道"；删 `t_payout_rail`，改为 channel-connector 外部引用（渠道 / 出款账户 / 能力 / 路由策略 / 成本）；执行表冻结 `channel_id · channel_payout_account_id · rail_code · channel_ref · channel_result_json`；出款单不再带 rail / execution_mode；出款计划的默认通道改为可空的轨道偏好。
- 05：接口表加 channel-connector（路由解析、`PayoutOperationApi` CREATE / QUERY、回调转事件 `PayoutChannelResult`）；reconciliation 行加服务商出款渠道账单对账 → `ActualPayoutCostConfirmed`；Q3 / Q5 / Q10 改为已定。
- 06：泳道"银行"改"外部渠道（本例：自有银行门户）"，加第 14b 步路由解析；06b 新增服务商 API 渠道（预充值）的 C–G 段：路由到出款账户 PA-9、占用 `CHANNEL_FLOAT`、`PayoutOperationApi CREATE`、回调 / QUERY 终态即 SETTLED、服务商账单只确认实际成本、充值对账、退回经回调。
- 对其他系统的新要求：channel-connector 加 PAYOUT 渠道类型、出款账户、出款路由策略、`PayoutOperationApi`、回调转事件；account 加 `CHANNEL_FLOAT` 现金账户种类；reconciliation 加出款渠道账单对账与充值 / 结算对账；billing 的 `PAYOUT_SETTLED` 加渠道成本预估行并消费 `ActualPayoutCostConfirmed`。


| # | 问题 | 建议 |
|---|---|---|
| Q11 | 第一版接哪些出款渠道？ | 自有银行人工 + 一家 API 服务商（AE 本地或 PayerMax 出款），用两种形态验证渠道抽象 |
| Q12 | 服务商资金模式：预充值备付余额还是净额结算 / 后付？ | 按签约定；模型两种都支持（CHANNEL_FLOAT 账户 vs 应付服务商） |
| Q13 | channel-connector 里出款渠道是第三种 `channel_type=PAYOUT`，还是 PAYMENT 渠道的一项能力？ | 能力：渠道表加 capability 集合 {ACQUIRING, PAYOUT}，类型不变；出款入口只接受带 PAYOUT 能力的渠道；一家服务商一个身份、两套账户与成本方案 |
| Q14 | 出款路由归谁？ | 第一版 payout 按配置映射选，运营可改；路由策略引擎后续复用 channel-connector |

### 7.5 2026-10-30 补充：退回延后

用户决定 `BankDebitReturned` 等实际发生再说。画板 03 / 03b / 05 / 06 / 06b 上的 RETURNED 状态、`PayoutReturned`、`PAYOUT_RETURNED` 事实与退回分支均标为"后续"；03b 简化版第一版出款单为 4 个状态（PENDING_APPROVAL / APPROVED / PAID / CANCELLED）+ `active_execution_id`。

## 8. 字段级模型（`mor_payout`，按已定结论）

沿用 2026-08-28 七表骨架（`paytech/payout/docs/database/payout.pdma.json`），改动以 **新增 / 修改 / 删除** 标出；未标出的字段不变。通用列（`id` Snowflake、`version` 乐观锁、`create_time` / `update_time` UTC 微秒）不重复列。外部 ID 只存稳定标识，不建跨库关系。

### 8.1 `t_beneficiary_account` 收款账户（修改）

| 列 | 类型 | 说明 |
|---|---|---|
| `owner_type` | VARCHAR(16) | 允许值 CLIENT、LOCAL_PARTNER；**预留 PAYEE**（枚举值先不加，注释注明） |
| `owner_id` | BIGINT | Client / Local Partner 外部稳定标识 |
| `account_code` | VARCHAR(32) | Owner 范围内稳定可读代码 |
| `beneficiary_vault_token` / `beneficiary_name_masked` / `account_last4` | 不变 | 原值只进 Vault |
| `bank_country` / `currency` / `bank_code` | 不变 | 字段要求由所经渠道能力决定（提交时按 channel-connector 返回的字段要求校验，不在本表建 Schema） |
| `bank_account_blind_index` / `bank_account_blind_key_ver` | 不变 | 等值查重：同 Owner 重复账户、跨 Owner 共用账户告警 |
| `vault_record_version` / `vault_metadata_hash` | 不变 | |
| `content_hash` / `maker_id` / `checker_id` / `checker_decision` / `decision_time` | 不变 | |
| `status` | VARCHAR(16) | DRAFT、PENDING_REVIEW、VERIFIED、REJECTED、SUSPENDED、CLOSED；`SUSPENDED` 新增 `suspended_reason` |
| **`cooling_until`** | DATETIME NULL | **新增**：VERIFIED 后 = decision_time + cooling_hours；`now < cooling_until` 不可用于新出款 |
| **`is_default`** | TINYINT | **新增**：同 Owner × 币种至多一个默认账户，供出款计划与门户预选 |
| **`suspended_reason`** | VARCHAR(32) NULL | **新增**：PERMANENT_FAILURE / OWNER_REQUEST / OPS |

规则：冻结字段变化必须新建账户（新 ID）并让旧账户 CLOSED；新账户重走 Maker-Checker 与冷却；执行 `failure_class=PERMANENT` 自动置 SUSPENDED。

### 8.2 `t_payout_schedule` 出款计划（新增）

| 列 | 类型 | 说明 |
|---|---|---|
| `owner_type` / `owner_id` / `entity_no` / `currency` | — | 唯一键 `uk_schedule_owner_entity_currency` |
| `interval` | VARCHAR(16) | MANUAL、DAILY、WEEKLY、MONTHLY |
| `payout_days` | JSON NULL | WEEKLY 为星期数组，MONTHLY 为日期数组（29–31 顺延月末） |
| `cutoff_second_of_day` / `timezone` / `business_calendar_code` | — | 与 CCT 成熟条款同一套日历语义 |
| `min_retained_amount` | DECIMAL(19,4) | 最低留存余额，出款额 = AVAILABLE − 留存 |
| `min_payout_amount` | DECIMAL(19,4) | 低于此不生成 |
| `beneficiary_account_id` | BIGINT NULL | 默认收款账户；为空取 Owner 默认账户；无可用账户 → 跳过并记事件 |
| `rail_preference` | VARCHAR(32) NULL | 轨道偏好，交路由；可空 |
| `fee_mode` | VARCHAR(32) | ADDED_ON_TOP / DEDUCTED_FROM_PAYOUT |
| `status` | VARCHAR(16) | ACTIVE / SUSPENDED |
| `last_run_date` / `next_run_time` | — | 由 XXL-Job `payoutScheduleRun` 推进；生成的出款单 `origin=SCHEDULE`、`schedule_id`、`schedule_run_date` 组成唯一键防重 |
| `maker_id` / `checker_id` / `change_version` | — | 计划本身 Maker-Checker |

### 8.3 `t_payout` 出款单（修改）

| 列 | 类型 | 说明 |
|---|---|---|
| `payout_no` / `request_key` | 不变 | `(owner_type, owner_id, request_key)` 唯一，同 Hash 重放返回原单 |
| 付款方：`owner_type` / `owner_id` / `entity_no` / `currency` / `available_account_id` / `locked_account_id` | 不变 | |
| 收款方：**`payee_type`** / `beneficiary_account_id` / `beneficiary_content_hash` | **新增 `payee_type`** VARCHAR(16)=OWNER_SELF（预留 PAYEE） | |
| **`origin`** / **`schedule_id`** / **`schedule_run_date`** | **新增** | OWNER_PORTAL / OPS_ON_BEHALF / SCHEDULE；后两列仅 SCHEDULE |
| **`approval_policy`** | **新增** VARCHAR(32)=FIXED_DUAL | 预留 |
| `requested_amount` / `fee_mode` / `fee_amount` / `owner_debit_amount` / `beneficiary_amount` / `cash_required_amount` | 不变 | 恒等式不变 |
| **`fee_quote_ref`** / **`fee_quote_hash`** | **新增** | billing FeeQuote 证据 |
| `assessment_id` / `evaluation_id` / `eligibility_*` / `risk_required_*` / `control_set_hash` / `eligibility_hash` | 不变 | 终审快照 |
| `request_hash` / `approval_hash` / `approved_time` | 不变 | |
| **`lock_confirmed`** | **新增** TINYINT | account 锁定已确认；为 0 不允许审批 |
| **`active_execution_id`** | **新增** BIGINT NULL | 活动执行；非空时禁止取消与新建执行 |
| **`settled_fact_id`** / **`settled_entry_id`** | **替换** `journal_entry_id` | billing 事实 ID、account 清偿流水 ID |
| **`close_reason`** | **新增** VARCHAR(32) NULL | CHECKER_REJECTED / WITHDRAWN / CANCELLED / INSUFFICIENT_BALANCE |
| `status` | VARCHAR(32) | **改为** PENDING_APPROVAL、APPROVED、PAID、CANCELLED（RETURNED 后续） |
| ~~`reconciled_time`~~ | **删除** | 由执行与事实表达 |

### 8.4 `t_payout_approval` 审批事实（不变）

`action_type` PAYOUT_APPROVAL / PAYOUT_CANCEL；`requested_from_status` 允许值改为 PENDING_APPROVAL、APPROVED。

### 8.5 `t_payout_execution` 出款执行（修改）

| 列 | 类型 | 说明 |
|---|---|---|
| `payout_id` / `attempt_no` | 不变 | |
| **`channel_id`** / **`channel_payout_account_id`** / **`rail_code`** | **新增** | channel-connector 路由解析结果，创建时冻结；运营改选时写新执行 |
| **`cash_account_kind`** / `cash_account_id` | **新增 kind** | BANK / CHANNEL_FLOAT；`cash_account_id` 为 reconciliation 银行账户 ID 或 account 备付余额账户 ID |
| ~~`cash_account_binding_ver_id`~~ | **删除** | 绑定改由出款账户配置表达 |
| **`cash_locked`** | **新增** TINYINT | account 现金占用已确认 |
| `execution_key` / `request_hash` | 不变 | `execution_key` 也是渠道幂等键 |
| Risk 执行 / 提交检查点快照 | 不变 | |
| **`channel_ref`** | **替换** `bank_payment_ref` | 渠道受理号或银行付款参考号 |
| **`channel_result_json`** | **新增** JSON | 渠道返回快照（状态、时间、渠道费、原因码），不含敏感值 |
| **`channel_cost_estimate`** | **新增** DECIMAL | 提交时冻结的渠道成本预估 |
| `failure_code` + **`failure_class`** | **新增 class** | TEMPORARY / PERMANENT |
| `status` | VARCHAR(16) | **改为** CREATED、SUBMITTED、UNKNOWN、SETTLED、FAILED |
| `submitted_time` / `resolved_time` | 不变 | FAILED 且 `submitted_time` 为空 = 未提交放弃 |
| ~~`active_slot`~~ | **删除** | 由 `t_payout.active_execution_id` 表达 |

### 8.6 `t_payout_evidence_ref` 证据引用（修改）

`file_id` + `file_content_hash` **改为** `file_address`（`/files/v1/{fileId}`，按 [2026-10-22 文件地址决定](../../agent-docs/decisions/2026-10-22-file-upload-address-principles.md)）+ `file_content_hash`。`evidence_type` 加 CHANNEL_RECEIPT（渠道回执快照导出）。

### 8.7 `t_payout_event` 出款事件（不变）

`from_status` / `to_status` 目录随 8.3、8.5；新增 `source_type` 值 CHANNEL_RESULT（渠道回调 / 查询）、SCHEDULE_RUN。

### 8.8 `t_finance_restriction_proj` 限制投影（不变）

`source_system` 允许值加 COMPLIANCE（结算控制事件来源）。

### 8.9 不建的表

`t_payout_rail`（归 channel-connector 出款账户能力）、`t_payout_batch`（代发时再建）、`t_payout_routing`（归 channel-connector 路由策略）。

## 9. 各系统改动清单

| 系统 | 改动 | 依赖关系 |
|---|---|---|
| **payout** | 从零建代码：api / core / app / test 四模块；第 8 节模型与 Schema SQL；Processor：收款账户提交 / 核验 / 停用、出款单创建 / 撤回 / 审批 / 取消、执行创建 / 改选渠道 / 人工提交回填 / 渠道结果处理、出款计划维护 / 运行；补偿事件：确认锁定、确认现金占用、渠道结果重试；XXL：`payoutScheduleRun`、`payoutUnknownQuery`（服务商渠道 UNKNOWN 收敛） | 全部 |
| **channel-connector** | `channel_type` 加 PAYOUT；新表 `t_channel_payout_account`（渠道 × OE × 币种，绑定 `cash_account_kind` + `cash_account_id`）、`t_payout_account_capability`（国家 / 币种 / 轨道 / 收款方类型 / 单笔与日限额 / 截止与日历 / 收款账户字段要求 JSON）、`t_payout_routing_policy` + `_version` + `_account`（类比 MID 路由）；成本方案 `t_channel_cost_scope` 支持 PAYOUT 渠道（按出款账户 × 轨道）；接口：`POST /channel-connector/v1/payout-routings`（解析）、`PayoutOperationApi` CREATE / QUERY（只接受 PAYOUT 渠道、调用方白名单 payout）、`GET …/payout-accounts/{id}/beneficiary-requirements`；回调落 `t_channel_callback` 后转事件 `PayoutChannelResult`；自有银行人工渠道 = 一个 PAYOUT 渠道（无出站地址，执行方式 MANUAL） | 修订 2026-10-20 决定第 2 条"两类" |
| **account** | `BalanceMovementApi` 复用：Owner 锁定 / 释放 PAYOUT_LOCK / PAYOUT_RELEASE；银行现金占用用同一 API、科目 BANK_CASH（已支持）；**新增** `CASH_ACCOUNT` 种类 CHANNEL_FLOAT（服务商备付余额，`owner_id` = 出款账户 ID）与科目 `CHANNEL_FLOAT`；`PAYOUT_SETTLED` 事实入账借记 Owner LOCKED 与现金 LOCKED（现有规则扩到 CHANNEL_FLOAT）；充值事实贷记 CHANNEL_FLOAT AVAILABLE | 先于 payout 切片 2 / 3 |
| **billing** | `FeeQuoteApi`（scene=PAYOUT，只算不记，返回费用与证据，已在 billing 设计 Q13 定）；消费 `PayoutSettled` 生成 `PAYOUT_SETTLED`（`PAYOUT_AMOUNT` + `PAYOUT_FEE` 两行，已有组件）+ **渠道成本预估行**（组件 `CHANNEL_COST`，服务商渠道）；消费 `ActualPayoutCostConfirmed` 生成成本差异事实（复用 `COST_ADJUSTMENT`）；消费充值 / 结算资金事实 | 切片 2 前 |
| **reconciliation** | 现稿切片 3（出款预期银行流水、借记匹配 → `BankDebitMatched`）；**新增** 出款渠道账单（服务商）导入与"账单行 ↔ 执行"匹配 → `ActualPayoutCostConfirmed`；服务商充值 / 净额结算 ↔ 银行流水匹配 → 充值确认事件；退回仍人工 CLASSIFY | 切片 2（自有银行）、切片 3（服务商） |
| **risk-decision** | RCO-I09 四检查点合同不变；`bank_country` 取收款账户；服务商渠道外部提交检查点由系统触发 | 无结构改动 |
| **compliance** | 结算控制 `SETTLEMENT_BLOCK` 创建 / 释放 Processor 与事件（client-activation 缺口 P1）；收款方筛查结果供 Risk | 切片 2 前可先人工登记 |
| **commercial** | 已有 PAYOUT 产品、`PAYOUT_LOCAL` / `PAYOUT_SWIFT` 开通项（kind PAYOUT_RAIL）、`PAYOUT_SUBMITTED` 计费事件；补：开通项状态查询给 payout；出款费价目模板；`PAYOUT_RETURNED` 事件后续 | 无 |
| **Platform** | 路由 / 菜单 / 权限块（新 ID 块）、i18n 包、Webhook `payout.*` 事件目录、文件地址 | 随各切片 |
| **operations-console** | 出款工作台：待审批 / 待提交 / 待对账 / UNKNOWN / 需重试队列；出款单详情（审批、执行、证据、事件、blockers）；收款账户核验；出款计划；渠道改选；服务商账单导入（reconciliation 页） | 先 Pencil 画板 |
| **client-console** | 收款账户页（添加 / 核验状态 / 冷却提示 / 默认）、余额与成熟时间线、提款申请、出款列表与详情、出款计划（若合同允许）、凭证下载 | 先 Pencil 画板 |
| **Partner 门户** | 尚无；Partner 提款第一期由运营代操作（2026-10-23 决定 D2） | — |

## 10. 切片

| 切片 | 范围 | 可验证结果 | 前置 |
|---|---|---|---|
| **S1 收款账户与余额解释** | payout 服务骨架 + `t_beneficiary_account`（含冷却 / 默认 / 停用）+ Maker-Checker 核验 + 通知；`GET …/balances-explanation`（读 account）；Client 门户收款账户页、余额页；运营核验页 | Client 能添加账户，运营核验，冷却后状态为可用；余额页能解释 PENDING / AVAILABLE / LOCKED / 保证金 | account 余额查询（已有）、Platform 通知 |
| **S2 结算出款 · 自有银行人工渠道** | 出款单（创建 / 撤回 / 审批 / 取消）+ account 锁定两步 + Risk 四检查点 + billing FeeQuote + 执行（路由解析到自有银行 PAYOUT 渠道、现金占用、人工提交回填、证据）+ reconciliation 切片 3（预期流水、借记匹配、`BankDebitMatched`）+ `PayoutSettled` → billing → account / ledger；运营出款工作台；Client 提款申请与出款详情 | 画板 06 全流程（A–E）在 mor-test 跑通，账本与余额与画板 04 一致 | channel-connector PAYOUT 类型与自有银行渠道配置（可先最小实现：渠道 + 出款账户 + 路由解析）、billing FeeQuote、compliance 结算控制（可人工登记） |
| **S3 服务商 API 渠道** | channel-connector：服务商 PAYOUT 渠道、出款账户、能力、路由策略、`PayoutOperationApi`、回调转事件；account CHANNEL_FLOAT；payout：渠道结果处理、UNKNOWN 查询收敛、渠道改选；billing 渠道成本预估行；reconciliation 服务商账单与充值对账 → `ActualPayoutCostConfirmed` | 画板 06b 全流程（C–F）在 mor-test 对沙箱跑通 | S2；服务商沙箱 |
| **S4 出款计划** | `t_payout_schedule` + XXL `payoutScheduleRun` + 运营 / Client 计划页 | 按计划生成待审批单，留存余额与最小额生效 | S2 |
| **S5 结算单、看板、Partner 代操作** | BFF 聚合 billing 事实的结算单与凭证下载；运营出款日报；运营代 Partner 提款 | — | S2 |
| 后续 | RETURNED 状态与退回事件（真实发生后）；阈值审批；代发（PAYEE、批次、OWNER_AUTHORIZED）；更多渠道 | — | — |

**登记：** 结论定稿后登记 `agent-docs/decisions/2026-10-30-payout-settlement-payout-model.md`（Proposed），并修订 [2026-10-20 channel-connector 决定](../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)第 2 条（渠道三类）与 [2026-08-28 Manual Payout 设计](../../agent-docs/plans/2026-08-28-finance-manual-payout-simplification-design.md)的取代范围；首个需求按 S1 登记到 `requirements/2026MMDD/payout-收款账户与余额解释/`。
