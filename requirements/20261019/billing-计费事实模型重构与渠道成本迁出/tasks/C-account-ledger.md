# 任务卡 C · account + ledger：适配 BillingFactRecorded、新载荷、新查询接口与 ledger-api 枚举

先读 `00-common.md` 与 `T0-report.md`。目标：两服务**行为不变**，测试计数不变（account 58、ledger 234），只改名与结构。

## 允许改动的路径

- `paytech/account/paytech-account-core/**`、`paytech/account/paytech-account-app/**`、`paytech/account/paytech-account-test/**`、`paytech/account/paytech-account-api/**`（只改 JavaDoc 里的事实名）、`paytech/account/README.md`、`paytech/account/docs/**`
- `paytech/ledger/paytech-ledger-core/**`、`paytech/ledger/paytech-ledger-app/**`、`paytech/ledger/paytech-ledger-test/**`、`paytech/ledger/README.md`、`paytech/ledger/docs/database/FINANCE_PHYSICAL_DECISIONS.md`（只改措辞）

## 1. account

1. `ext/mq/EconomicFactRecordedBalanceHandler` → `BillingFactRecordedBalanceHandler`（`eventType = BillingFactPayload.EVENT_TYPE`），`EconomicFactEventReader` → `BillingFactEventReader`（校验 `BILLING_FACT` 聚合类型；`FactRecord` 行增加 `targetBucket` 字段但本期不使用）。
2. `domainservice/validator/FactBalanceMappingRule`：删除"没有付方 / 收方的隐含收方"分支与 `IMPLICIT_PAYEE`、`PARTY_REQUIRED_FEES`；每行必须有付方 / 收方，缺失即拒绝；`PLATFORM_SIDE_FEES` 删除 `GROSS_AMOUNT`；类注释按新模型改写。常量引用改 `BillingFactType` / `FactComponentType` / `FactPartyRole` 的 `getCode()`。其余（Owner 目标、PENDING、REVERSAL 互换、COST_ADJUSTMENT 限制、Fail-Closed）不变。
3. `service/handler/AccountPostedHandler`、`domain/FactRecord`、`BalanceApplicationService`、`PostingSourceType` 等 JavaDoc / 常量改名；`compensation-event-catalog.yaml` 注释措辞。
4. 测试：`EconomicFactBuilder` → `BillingFactBuilder`（生成 `BillingFactPayload`，每行带付方 / 收方；原来"PAYMENT 行无付方 / 收方"的样例改为 `CHANNEL → CLIENT` 等显式写法）、`BillingEventPublisher`、`EconomicFactBalanceConsumeTest` → `BillingFactBalanceConsumeTest`，其余测试只改引用；断言行为不变。

## 2. ledger

1. `integration/billing/EconomicFactClient` → `BillingFactClient`（`getProgress` 调 `BillingFactQueryApi.getBillingFactProgress`，参数名 `entityType / entityId`；`getFact` 调 `getBillingFact`，返回 `BillingFactPayload`：直接映射响应体字段，不再构造 `partyType`）；`app/configuration/BillingClientConfiguration` 注册 `BillingFactQueryApi`（命令接口 ledger 不用，不注册）。
2. `domainservice/service/impl/TransactionPostingServiceImpl` 与 `domain/TransactionPostingContext` / `AccountPostingCommand`：`EconomicsType` → `BillingFactType`；`AutoInstructionType`、`AmountSource` 改引 ledger-api；业务时间统一 `fact.getBusinessTime()`（删 `paymentSucceededTime / recordedTime` 分支，`businessTime` 缺失即拒绝）；`CHANNEL_ACCOUNT` Owner 从 `refs` 取 `FactRefType.CLEARING_ACCOUNT` 的 `refId`（缺失时错误同现状 `channel_account_id` 缺失）；明细行"主体"改取 `payeeRole / payeeId`（原 `partyType / partyId`）。`core/dal/dataobject/AccountingRuleLine`、`domain/view/*`、`LedgerPersistenceConfiguration`（TypeHandler 改注册 ledger-api 枚举）、`AccountingRuleBundleConverter`、`CreateRuleVersionProcessor` / `SubmitRuleVersionProcessor`、`PostingStreamCatchUpProcessor`、`StartPeriodCloseProcessor`（进度接口）同步。
3. 测试：`test/mock/external/MockEconomicFactApi` → `MockBillingFactApi`（实现 `BillingFactQueryApi`，路径 `/billing/v1/facts/**`）、`EconomicFactMocker` → `BillingFactMocker`、`TransactionEconomicsBuilder` / `EconomicComponentBuilder` 改产出 `BillingFactResponseBody` 新字段（`businessTime`、`refs` 含 `CLEARING_ACCOUNT`、行付方 / 收方必填）；其余测试只改引用；断言行为不变。
4. `README.md`、`FINANCE_PHYSICAL_DECISIONS.md` 中事实名措辞；`t_accounting_instruction.economic_fact_id` 列名**不改**。

## 3. 验证

`./mvnw -B -o verify -pl account/paytech-account-test,ledger/paytech-ledger-test -am`（两库无结构变化，可直接跑全量）；报告列出计数并与 58 / 234 对比，差异逐条解释。

## 4. 报告

`REQ/tasks/C-report.md`（按 00-common）。
