# billing 计费事实模型重构与渠道成本迁出

> 状态：阶段 1、2 已确认（2026-10-19，按建议），阶段 3 进行中。确认记录见 [delivery.md](./delivery.md)，现状差异与逐系统影响见 [technique.md](./technique.md)。依据 [billing 决定草稿](../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md)（Confirmed）与 [design-discuss/billing](../../../design-discuss/billing/design.md)。

## 1. 背景与来源

billing 讨论（2026-10-19）定了职责边界、`billing_fact` 命名、精简模型与渠道成本归属。支付事实首个切片还卡在三项上游合同，但模型重构与渠道成本迁出不依赖上游，先做掉，为后续所有事实来源切片铺好落点。用户 2026-10-19 回复"一起吧"：写决定记录并登记本需求。

当前事实（2026-10-19，依据 billing / channel-connector 源码调查与 [stage 7 部署记录](../finance-account明细账与ledger控制科目重构/release/release.md)）：

| 项 | 现状 |
|---|---|
| billing 表 | `mor_billing` 9 张：`t_economic_fact`（49 列）、`t_economic_fact_line`（35 列）、`t_economic_fact_line_link`、渠道成本 4 表、`t_currency_spec`、`t_compensation_event`；本机与 mor-test 事实表 0 行、渠道成本配置 0 行 |
| billing 接口 | `EconomicFactCommandApi`（`/billing/v1/economic-facts`：提交、批准、按 ID、按流分页、`/pending`）、`ChannelCostPlanApi`（`/channel-cost-plans/v1/**` 五个 Maker-Checker 端点）；预估成本领域服务无生产调用方 |
| billing 事件 | 发布 `EconomicFactRecorded`（消费者 account）；不消费任何事件；无消息消费表 |
| billing-api 枚举 | `EconomicsType`、`AmountSource`、`FactPartyRole`、`FactLineDirection`、`EconomicFactStatus`、`AutoInstructionType`（ledger 规则键） |
| 依赖方 | account-core（载荷 `EconomicFactPayload`、`EconomicsType`、`AmountSource`、`FactPartyRole`、`FactLineDirection`、事实列表 / 按 ID 接口）、ledger-core / app（载荷、`AutoInstructionType`、`AmountSource`、`getEconomicFact`、`getPendingEconomicFacts`） |
| channel-connector | `mor_channel_connector` 13 张表（渠道、技术账号、MID、能力 ×2、路由策略 ×3、交互、回调 ×2、事件异常、补偿事件）；`ChannelCandidateQueryApi`、`ChannelOperationApi`（前缀 `/channel-connector/v1`）；无 Maker-Checker、无 Redis 锁、无 XXL |
| PDManer | `billing.pdma.json` 仍由 Finance 生成器投影；`channel-connector.pdma.json` 由 Payment Orchestration 生成器按分区投影（阶段 3 核查，Q-02） |
| 控制台 | 没有渠道成本配置页（财务第三批原计划），不受影响 |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 后续事实来源切片（支付、退款、争议、资金类、外汇） | 直接往定稿的 `billing_fact` 模型与接口上接，不再改表 |
| account / ledger | 事件与载荷改名后行为不变 |
| 渠道运营 / 财务配置人员 | 渠道成本方案在渠道 Owner 处维护；支付事实生成时能命中到成本组件 |

## 3. 本期范围

1. **billing 模型**：按 [database.md](../../../design-discuss/billing/database.md) 与 [ddl-draft.sql](../../../design-discuss/billing/ddl-draft.sql)：事实四表改名重建为 `t_billing_fact`、`t_billing_fact_ref`、`t_billing_fact_pricing`、`t_billing_fact_line`、`t_billing_fact_line_link`；新增 `t_consumed_event`、`t_consumed_aggregate_version`、`t_business_calendar`、`t_business_calendar_day`；删除渠道成本四表；`billing.pdma.json` 脱离 Finance 生成器直接维护并加 `billing-model.test.mjs`；Schema、测试 DDL、变更 SQL、初始化 DML（币种 + 日历占位）同步。
2. **billing 代码**：DataObject / Mapper / Repository 按新表；`EconomicFactPayload` → `BillingFactPayload`（头 + 引用 + 行）、`EconomicsType` → `BillingFactType`（12 值）、`AmountSource` 迁到 ledger-api 并在 billing-api 新建 `FactComponentType`（15 值）、新增 `FactOriginType`、`FactRefType`、`SourceRuleType`、`LineLinkType`、`CalculationMethod`；`FactPartyRole` 调整（加 `CASH_ACCOUNT`、`FX_ACCOUNT`、保留 `CONSUMER`，删 `PLATFORM`）；`AutoInstructionType` 迁到 ledger-api；人工登记接口改为 `/billing/v1/facts` 并按新模型（`origin_type = MANUAL`，引用 `TICKET` 可选）；查询接口 `/billing/v1/facts/{id}`、`/billing/v1/facts?…`、`/billing/v1/facts/progress`、新增 `/billing/v1/facts/by-ref`；事件改名 `BillingFactRecorded`（聚合 `BILLING_FACT`），补偿事件类型 `BILLING_FACT_RECORDED`；删除 `ChannelCostPlanApi` 与渠道成本 Processor / 领域服务中的"找方案"部分，保留"组件 → 金额"计算器供后续费用引擎；`unroutable-policy` 改回 `FAIL`。
3. **channel-connector**：接收渠道成本四表（`t_channel_cost_scope.mid_id` 引用本库 `t_channel_mid`）、五个 Maker-Checker 端点与测试（路径改为 `/channel-connector/v1/channel-cost-plans/**`）、新建到期切换 XXL Job（billing 现状没有）；新增命中接口 `POST /channel-connector/v1/channel-costs/resolutions`（只返回命中组件）；`channel-connector.pdma.json`、Schema、测试 DDL、变更 SQL 同步。
4. **account / ledger 适配**：消费 `BillingFactRecorded` 与新载荷（引用表进载荷、行付方 / 收方必填、`businessTime`）；ledger 改用 `BillingFactType`、从 ledger-api 取 `AutoInstructionType`、改调新的事实读取与进度接口；两者行为不变、测试按新名字改。
5. **架构规则与环境**：表归属生成器 + design/03 + `TableOwnershipAutoTest`；事件目录 `events.yaml`（`EconomicFactRecorded` → `BillingFactRecorded`）；Compose 无新增变量；test-environment 的 mor-test 发布含 billing、channel-connector、account、ledger 四个镜像与两库 SQL。
6. **文档**：决定记录转 Confirmed；design/03、design/04 取代说明；`FINANCE_DATABASE.md` 取代说明；requirements-site FIN-04 Owner 与事实名；台账；design-discuss 状态改"已确认 / 实施中"。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| 支付、退款、争议、重复扣款、资金类、外汇事实的生成逻辑与消费者 | 各自切片；本期 billing 仍只有人工登记 |
| `FeeQuoteApi`、`FeeBreakdownQueryApi`、资金类事实备用登记命令 | 随出款 / 控制台 / 对应切片 |
| 费用计算引擎（CCT 命中、分润、服务费、到期时间） | 需求 B |
| 工作日日历的 Owner 实现与同步机制 | 本期只建副本表与初始化占位（默认周一至周五），平台侧另立 |
| 渠道成本配置的运营页面 | 财务第三批 |
| 人工调整目标桶（决定第 14 条） | 与 account 映射一起在需求 B 或退款切片实现；本期人工登记行为不变 |
| 执行 SQL、部署、提交 | 按阶段单独授权 |

## 5. 验收标准

| 编号 | 验收标准 |
|---|---|
| AC-01 | `mor_billing` 为 11 张表、`mor_channel_connector` 为 17 张表，结构与 PDManer、Schema、测试 DDL 逐字一致；表归属与 R4 通过 |
| AC-02 | 人工登记事实经新接口完成 DRAFT → APPROVED，写事实头、引用（可选 TICKET）、行（付方 / 收方必填）；同来源键重复提交幂等；替代链与版本规则不变 |
| AC-03 | `BillingFactRecorded` 载荷含头 + 引用 + 全部行，account 消费后余额与改名前一致（account-test 全过）；ledger 读取事实明细与进度接口改名后关账检查通过（ledger-test 全过） |
| AC-04 | channel-connector 的渠道成本 Maker-Checker 五个端点行为与原 billing 测试一致；命中接口对唯一命中返回组件与版本、内容 Hash，命中不到 / 不唯一返回业务错误；到期切换 Job 可登记 |
| AC-05 | billing 不再有 `ChannelCostPlanApi`、`EconomicFact*` 类与 `economic_fact` 表；`AutoInstructionType` 只在 ledger-api |
| AC-06 | 事件目录、补偿事件目录、arch-rules、scripts/pdmaner（新增 `billing-model.test.mjs`，Finance 生成器去掉 billing）、requirements-site 既有测试通过（既有失败单列） |
| AC-07 | mor-test：两库变更 SQL 执行、四个镜像替换后，人工登记 → account → ledger 链路验收通过，合成数据清理 |

## 6. 问题与建议

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 事实表是改名 ALTER 还是重建？ | 重建：两环境事实表 0 行，DROP 后按新 DDL 建，不写 ALTER 迁移 |
| Q-02 | `channel-connector.pdma.json` 是否也在生成器里？ | 阶段 3 核查：是，由 Payment Orchestration 生成器分区投影。方案：四表加入该生成器的 channel-connector 分区，不另建机制（technique 2.3） |
| Q-03 | 日历初始化数据 | 本期只建一条 `DEFAULT`（UTC、周一至周五），不录节假日；Owner 与同步另立 |
| Q-04 | 适用阶段与执行方式 | 阶段 1 + 2 合并确认；3 设计（接口、DDL、载荷）；4 Opus 5.5 多 agent（T0 合同：billing-api / ledger-api / 事件目录；A billing；B channel-connector；C account + ledger 适配；D 规则 / pdmaner / 文档）；5 本机；6–7 mor-test；8 提交 |

## 7. 依据

- [billing 决定草稿](../../../agent-docs/decisions/2026-10-19-billing-fact-engine.md)、[design-discuss/billing](../../../design-discuss/billing/)
- [2026-10-18 account / ledger 决定](../../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)、[前序需求](../finance-account明细账与ledger控制科目重构/requirement.md)（阶段方式与 agent 分线先例）
- `paytech/billing`、`paytech/channel-connector`、`paytech/account`、`paytech/ledger` 的 `*-api` 合同
