# billing 计费事实引擎：职责边界、事实模型与渠道成本归属决定

- Decision status: Confirmed（2026-10-19 用户「按照你的建议来吧」确认第 1–14 条）
- Implementation status: In progress（阶段 4 代码已完成、本机库待重建、未部署；阶段 3 对第 4、8、11 条的细化：15 个组件、`COMMERCIAL_PRICE` / `commercial_price_id`、明细行加 `target_bucket`、`AmountSource` 迁 ledger-api，见 [需求 A technique 2.6](../../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/technique.md)）
- Deployment status: Not applicable
- Decision date: 2026-10-19
- 用户决定：2026-10-19 在 [design-discuss/billing](../../design-discuss/billing/design.md) 的讨论中逐项确认 Q1–Q13 按建议、命名 `billing_fact`、模型精简（DB-1–DB-8）；并提出渠道成本配置不应归 billing（Q12）、出款费用不应是同步落事实的命令（Q13）。
- 详细设计：[职责与设计](../../design-discuss/billing/design.md)、[数据库模型](../../design-discuss/billing/database.md)、[DDL 草案](../../design-discuss/billing/ddl-draft.sql)、[业务案例 C1–C10](../../design-discuss/billing/cases.md)、画板 [design.pen](../../design-discuss/billing/design.pen)。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

V2 把计费定位为"唯一经济事实来源"（[目标架构 V2 决定](./2026-10-17-target-architecture-v2.md) D05 / D16 / D37），阶段 0 只落了最小能力：人工登记 `MANUAL_ADJUSTMENT` / `COST_ADJUSTMENT` 并发布 `EconomicFactRecorded`，加上随 finance-service 拆出来的渠道成本配置。2026-10-19 调查现状：billing 不消费任何事件，不生成支付 / 退款 / 争议事实，不算手续费 / 分润 / 服务费 / 预估成本，`balance_available_time` 没人写；事实头 49 列、明细行 35 列，大量按事实类型分组的可空列与证据列；支付事实的输入在 Payment 事件、收单接口、CCT 接口三处都不存在。

## 2. 决定

1. **职责。** billing 是"算钱并把结果定格成事实"的服务：任何会改变 Client、合作伙伴、平台或银行现金余额的金额，先在 billing 成为不可变、带版本的事实，account 记明细账、ledger 记总账只消费它。billing 拥有事实目录与生命周期、费用计算引擎、反向规则、到期转可用时间、成本差异事实、资金类事实的落点、人工调整、单笔费用拆解查询；不拥有价格与合同（CCT / 商务）、税额（税务）、余额（account）、分录（ledger）、渠道实际成本确认（对账）、出款编排（出款）、利润报表（数据服务）、**渠道成本配置（channel-connector，见第 7 条）**。
2. **原则。** 一笔业务动作一条事实，按 `source_system + source_event_key` 幂等（同内容 200、不同内容 400）；事实不可变，更正发新版本并冲回；证据随事实冻结（命中的价格 / 组件 / 阶梯 / 费率 / 舍入）；缺输入或命中不唯一则失败进死信，不按 0 入账；读上游在事务外，写事实与补偿事件一个本地事务，提交后发布。
3. **命名。** `economic_fact` 改为 `billing_fact`（计费事实）：表 `t_billing_fact*`，事件 `BillingFactRecorded`，枚举 `BillingFactType`，补偿事件类型 `BILLING_FACT_RECORDED`，聚合类型 `BILLING_FACT`。不用 `transaction`（在仓库里已有数据库事务、支付交易、收单交易三重含义，且事实已含非交易项）。
4. **事实类型（12 种）。** `PAYMENT`、`REFUND`、`DUPLICATE_CAPTURE`、`DUPLICATE_COMPENSATION`、`DISPUTE`、`COST_ADJUSTMENT`、`CHANNEL_SETTLEMENT`、`PAYOUT_SETTLED`（本金 + 手续费两行）、`TAX_PAID`、`MANUAL_ADJUSTMENT`；外汇模块启用时 `FX_SELL`、`FX_BUY`（一笔兑换两条事实、各一个币种、同引用一个兑换单）。渠道到账、出款完成、缴税完成是资金类事实：不涉及价格，仍经 billing 落事实，优先由各 Owner 的事件触发（`ChannelSettlementConfirmed`、`PayoutSettled`、`TaxPaymentSettled`），没有事件源时才用备用登记命令。
5. **自动事实不审批。** `origin_type = AUTO` 的事实登记即 `APPROVED`；`MANUAL` 走 `DRAFT → APPROVED` 的 Maker-Checker；更正自动事实只能人工发新版本并审批，不允许修改。
6. **出款费用用"报价 + 事件"。** billing 提供无状态的 `FeeQuoteApi`（只算不记）供出款在锁定前取费用并冻结在出款单；银行确认后由 `PayoutSettled` 事件生成一条 `PAYOUT_SETTLED` 事实（`PAYOUT_AMOUNT` + `PAYOUT_FEE` 两行）。不再有 `PAYOUT_FEE` 事实类型与同步登记命令。
7. **渠道成本配置归 channel-connector。** 渠道成本方案 / 版本 / 范围 / 组件四表、Maker-Checker 接口、到期切换 Job 从 billing 迁到 channel-connector（它已拥有渠道、技术账号、MID 表）；channel-connector 新增命中接口 `POST /channel-connector/v1/channel-costs/resolutions`（返回命中组件，不算金额）；billing 事务外命中、算金额、冻结证据在 `CHANNEL_COST` 行；对账确认实际成本后 billing 发 `COST_ADJUSTMENT`。与 CCT 价格同一模式："规则归 Owner，billing 只冻结证据"。
8. **价格命中在 CCT / 商务。** billing 以冻结的合同版本 + 支付事实换回命中 Price（含 T+N 结算输入），不自行读价格表。
9. **到期转可用时间由 billing 算**，写在支付事实头：费用与成熟来自同一条命中价格；工作日日历作为平台参考数据，billing 持只读副本（同币种，D41）。
10. **税。** 税额来自税务服务的快照，作为 `SELLER_TAX` 行引用；首个支付切片允许"订单无税务快照 → 无税行"，有快照 ID 读不到则失败等待。
11. **模型精简。** `mor_billing` 9 → 11 张表：事实头 25 列（原 49）、业务引用表 `t_billing_fact_ref`（替代 17 个可空 ID 列，按 `ref_type` 扩展）、PAYMENT 定价侧表 `t_billing_fact_pricing`（1:1）、明细行 19 列（原 35；付方 / 收方对所有行必填；证据保留 4 个可索引列 + `source_evidence` JSON）、行链接表不变；`business_time` 取代 `payment_succeeded_time`，ledger 过账日期统一取它；删 `context_format`、`payment_fact_id`、`party_type / party_id`；新增消息消费两张标准表与工作日日历两张副本表；渠道成本四表迁出。`AutoInstructionType` 迁到 ledger-api。
12. **事件名统一 `BillingFactRecorded`**（时序图与 requirements-site 中的 `FeesCalculated` / `EconomicsCalculated` 改名）；billing 不出交易利润，只出单笔费用拆解，利润与汇总由数据服务按事实表 CDC 计算。
13. **退款 / 争议 / 重复扣款原则。** 退款按比例冲回百分比手续费、固定费不退，分润与服务费随手续费按比例冲回；争议与重复扣款受原事实封顶；每行链接到原行（`t_billing_fact_line_link`），累计不超过原行金额。扣减顺序与保证金使用细则随退款与争议切片。
14. **人工调整的目标桶。** 人工调整事实的每一行可指定目标余额桶（`target_bucket`，取 account 的桶枚举），未指定时为 `AVAILABLE`；不再按通用映射进 PENDING（案例 C9）。account 侧映射随需求 B 或退款切片实现。

**阶段 3 细化（2026-10-19，需求 A 技术方案 2.6，待用户确认）：** 组件类型收口为 15 个（删 `CHANNEL_COST_INCREASE / DECREASE`，成本调整用 `CHANNEL_COST` + 付方 / 收方方向）；价格规则族与定价侧表按 commercial 决定命名 `COMMERCIAL_PRICE` / `commercial_price_id`；`AmountSource` 与 `AutoInstructionType` 一起迁到 ledger-api，billing-api 另建 `FactComponentType`；明细行加 `target_bucket` 列承载第 14 条。

## 3. 取代范围

| 被取代的内容 | 取代方式 |
|---|---|
| [design/04](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/04-finance-account-ledger-split.md) §2「阶段 0 的最小计费」中的表名、事实类型清单、`PAYOUT_FEE`、事件名 | 由本决定第 3、4、6、11、12 条取代；§2 的 Maker-Checker 与载荷含全部明细行的原则不变 |
| [design/03](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md) 中 `t_channel_cost_*` 四表归 billing | 改归 channel-connector（第 7 条） |
| [收单端到端时序](../../requirements/20261017/architecture-目标架构V2/acquiring-sequence.md) Stage 2 / 3 的 `FeesCalculated`、"账户算成熟日"、"计费返回出款费用事实 ID" | 事件名统一、到期时间由 billing 算、出款费用改报价 + 事件 |
| [`FINANCE_DATABASE.md`](../database/FINANCE_DATABASE.md) §4.2 的 V5 Scope 口径、`t_transaction_economics*` 命名 | 以本决定与详细设计为准；§4.2 的费用公式（百分比 + 固定、按比例冲回、固定费不退、服务费非残差、预估成本不默认为 0）继续成立 |
| requirements-site FIN-04 渠道成本配置的 Owner、FIN-02 / 03 / 05 / 18 / I03 中的事实名 | 随实现切片同步措辞 |

## 4. 已知代价

- 两张窄表让按订单 / 出款单找事实多一次 join；证据从列变 JSON，复算与审计需按 `schemaVersion` 解析。
- billing 成为 6 个上游事件的消费者，且支付事实依赖三项尚不存在的上游合同（Payment 事件字段、收单订单上下文接口、CCT 价格命中接口）与工作日日历。
- 渠道成本迁出后，channel-connector 多一组 Maker-Checker 与一个 Job。

## 5. 不变的边界

- account 只消费 billing 事实，ledger 只消费 account 批次并事务外读 billing 事实明细（2026-10-18 决定）。
- 事务公理、无跨库外键、PDManer 为设计源、人工 SQL 与单一初始化 DML 规则不变。
- 定价主数据与命中规则归 CCT / 商务；税归税务；实际成本归对账。

## 6. 后续

- 需求 A：billing 模型重构 + 渠道成本迁出（不依赖上游）：[requirements/20261019/billing-计费事实模型重构与渠道成本迁出](../../requirements/20261019/billing-计费事实模型重构与渠道成本迁出/requirement.md)。
- 需求 B：支付事实首个切片（`PaymentSucceeded → PAYMENT 事实 → account → ledger`），待 commercial 定价模型定稿与三项上游合同后登记。
- 退款与争议入账、外汇、出款、缴税、渠道到账各随其切片实现对应事实。
