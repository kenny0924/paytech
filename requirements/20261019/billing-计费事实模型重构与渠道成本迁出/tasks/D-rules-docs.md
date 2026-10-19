# 任务卡 D · 架构规则、PDManer 生成器、文档与站点

先读 `00-common.md` 与 `T0 / A / B / C` 的报告（本卡在它们之后开始）。

## 允许改动的路径

- `paytech/tools/arch-rules/scripts/generate-table-ownership.mjs`、`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`（只由生成器重生成）、`paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java`、`paytech/tools/arch-rules/src/test/resources/fixtures/r12domain/foundation/paytech-messaging/catalog/events.yaml`（fixture 事件名改 `BillingFactRecorded`）
- `scripts/pdmaner/payment-service-partition.mjs`、`scripts/pdmaner/generate-payment-model.mjs`、`scripts/pdmaner/payment-field-metadata.mjs`、`scripts/pdmaner/payment-channel-field-overrides.mjs`、`scripts/pdmaner/payment-model.test.mjs`、`scripts/pdmaner/payment-channel-query.test.mjs`、`scripts/pdmaner/generate-channel-connector-model.mjs`、`paytech/channel-connector/docs/database/channel-connector.pdma.json`（只由生成器重生成）、`paytech/payment/docs/database/payment.pdma.json`（若生成器把四表也写进 Payment 总模型则随之重生成，但四表只归 channel-connector 分区）
- `scripts/pdmaner/finance/**`、`scripts/pdmaner/finance-model.test.mjs`、`scripts/pdmaner/pdmaner-canonical-paths.test.mjs`、`scripts/pdmaner/oe-client-model.test.mjs`、`scripts/pdmaner/generate-oe-client-schemas.mjs`（若引用 billing 表）
- `requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md`、`design/04-finance-account-ledger-split.md`（只加"取代说明"段落）
- `agent-docs/database/FINANCE_DATABASE.md`（只加取代说明）、`agent-docs/CURRENT_IMPLEMENTATION.md`（billing / channel-connector / 事件段落）、`agent-docs/decisions/2026-10-19-billing-fact-engine.md`（Implementation status 行）
- `design-discuss/billing/{design.md,database.md,cases.md,ddl-draft.sql}`（按 technique 2.6 回写）
- `requirements-site/content/**`（FIN-04 Owner 改 channel-connector；`EconomicFactRecorded` / `FeesCalculated` / `EconomicsCalculated` 改 `BillingFactRecorded`；`t_economic_fact*` 改 `t_billing_fact*`）

## 1. 表归属（R4）

`generate-table-ownership.mjs`：billing 集合 = 11 张（`t_billing_fact`、`t_billing_fact_ref`、`t_billing_fact_pricing`、`t_billing_fact_line`、`t_billing_fact_line_link`、`t_business_calendar`、`t_business_calendar_day`、`t_consumed_event`、`t_consumed_aggregate_version`、`t_currency_spec`、`t_compensation_event`）；channel-connector 加 `t_channel_cost_plan`、`t_channel_cost_plan_ver`、`t_channel_cost_scope`、`t_channel_cost_component`。重生成 YAML，`TableOwnershipAutoTest` 总数与两集合断言同步；`./mvnw -B -o verify -pl tools/arch-rules -am` 全过。

## 2. PDManer 生成器

1. Payment 生成器：四表 DSL 与字段元数据按 `REQ/db/channel-connector/ddl.sql` 逐列加入（含注释、索引、关系：`plan_ver n:1 plan`、`scope n:1 plan_ver`、`component n:1 scope`、`scope n:1 t_channel_mid`、`plan n:1 t_payment_channel`），`CHANNEL_CONNECTOR_TABLES` 加四表；重生成 `channel-connector.pdma.json`，`generate-channel-connector-model.mjs --check` 通过；`payment-channel-query.test.mjs` / `payment-model.test.mjs` 期望同步。生成的 Schema 必须与 B 线写的 `channel-connector-schema.sql` 四表逐字一致（不一致以 REQ DDL 为准，回报给主会话）。
2. Finance 生成器：`service-split.mjs` 删除 billing（`FINANCE_SERVICES`、`BILLING_TABLES`、`READ_ONLY_COPIES`、计数 9）、`manifests/02-tax-economics-cost.mjs` 删除经济事实三表与渠道成本四表的 DSL；`finance-model.test.mjs`、`pdmaner-canonical-paths.test.mjs` 期望同步（billing 模型改由 A 线的 `billing-model.test.mjs` 校验）。`node --test scripts/pdmaner/*.test.mjs`：本需求涉及的测试全过，既有失败单列在报告里。

## 3. 文档

1. design/03：§2.1 表格中 billing 行与四表行加"2026-10-19 起：`t_billing_fact*` 五表归 billing、四表归 channel-connector，见 billing 决定"；§3 "Finance 共享枚举"行注明 `AmountSource`、`AutoInstructionType` 已迁 ledger-api。design/04 §2 开头加取代说明段（指向决定第 3 节）。`FINANCE_DATABASE.md` §4.2 加取代说明。
2. `design-discuss/billing`：`database.md` 6a 枚举表与第 5 节行表按 technique 2.6（15 组件、`COMMERCIAL_PRICE`、`commercial_price_id`、`target_bucket`、`AmountSource` 迁 ledger）；`ddl-draft.sql` 头注释改为"已被 REQ/db 取代，保留讨论稿"；`cases.md` C5 的 `CHANNEL_COST_DECREASE` 改 `CHANNEL_COST` + ADJUSTMENT（渠道 → OE）；`cases.md` 待定点加一条：C1 自动 PAYMENT 行的付方 / 收方与现行 account `FactBalanceMappingRule` 不兼容（CHANNEL 只允许 CHANNEL_COST；Client → OE 手续费会扣 Client 余额），需求 B 前先定 account 映射（C-report.md 第 6 节第 3 项）；`design.md` 6a 接口表更新路径与现状列。
3. `CURRENT_IMPLEMENTATION.md`：billing 段（模型 11 表、接口、事件改名、`unroutable-policy` 事项关闭）、channel-connector 段（17 表、Maker-Checker、命中接口、Job）、消息段（`BillingFactRecorded`）、库表数表；状态写"已实现、本机库待重建 / 未部署"（阶段 5–7 由主会话再改）。决定记录 Implementation status 改 "In progress（阶段 4）"。
4. requirements-site：改内容后 `npm --prefix requirements-site test` 全过。

## 4. 报告

`REQ/tasks/D-report.md`（按 00-common），附各测试命令与计数、既有失败清单。
