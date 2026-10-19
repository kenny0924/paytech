# 任务卡 T0 · 合同先行：account-api / ledger-api / billing 载荷 / 事件目录 / 表归属

先读 `00-common.md`。本卡完成并由主会话核对后，A / B / C / D 线才并行开始；下游 core / app / BFF 编译因本卡删除旧合同而暂时失败是**预期的**，由 A（account）、B（ledger）、C（BFF / Web）修复，本卡不改它们。

## 允许改动的路径

- `paytech/account/paytech-account-api/**`、`paytech/account/compensation-event-catalog.yaml`
- `paytech/ledger/paytech-ledger-api/**`
- `paytech/billing/paytech-billing-api/**`、`paytech/billing/paytech-billing-core/**`、`paytech/billing/paytech-billing-app/**`、`paytech/billing/paytech-billing-test/**`
- `paytech/foundation/paytech-messaging/catalog/events.yaml`
- `paytech/tools/arch-rules/scripts/generate-table-ownership.mjs`、`paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`（只能由生成器重生成）、`paytech/tools/arch-rules/src/test/java/com/paytech/arch/auto/TableOwnershipAutoTest.java`、`requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md`（只改 account 表清单所在的 §2 / §4.2 行）

## 1. account-api（`com.paytech.account.api`）

按 `technique.md` 2.1「枚举归属」与 2.2「account-api」：

1. `enums/OwnerType` 收窄为 `CLIENT`、`LOCAL_PARTNER`、`CASH_ACCOUNT`；删除 `enums/OwnerRole`；`BalanceBucket` 不变；新增 `enums/AccountEntityType { OPERATING_ENTITY, FINANCE_ENTITY }`、`enums/MovementSourceType { FACT_MATURITY, PAYOUT_LOCK, PAYOUT_RELEASE, TAX_PAYMENT_LOCK, TAX_PAYMENT_RELEASE }`（每个带 `fromBucket` / `toBucket` 与是否允许经 API 发起）、`enums/OwnerAccountType`（代码、`ownerType`、`normalSide`、允许桶集合，六条见 technique 2.1 表）；`constant/StandardAccountCodes` 增加 `BANK_CASH`。枚举实现方式（`CodeEnum` 等）照现有 `OwnerType`。
2. 新增 `event/AccountPostedPayload`（`EVENT_TYPE = "AccountPosted"`、`SCHEMA_VERSION = 1`、`AGGREGATE_TYPE = "ACCOUNT_POSTING"`）与 `event/AccountPostedEntryPayload`，字段按 technique 2.2 载荷清单；提供静态 `aggregateId(entityType, entityId, currency, shardNo)` 生成 `OE.AE001.USD.0` / `FE.8001.USD.0`（主体类型前缀 OE / FE，分隔符 `.`），并有解析方法。类注释写清信封映射（aggregateId、aggregateVersion = postingSequence、occurredAt = postedTime、sourceService = account）与「标量全为 JSON 字符串」约定，参照 billing `EconomicFactPayload`。删除 `event/BalanceMovedPayload`。
3. 新增 `BalanceMovementApi`（`@HttpExchange("/balance-movements/v1")`，`POST`）、`AccountPostingApi`（`/account-postings/v1`：`GET /progress`、`GET`（列表，`fromSequence`、`limit` ≤ 200）、`GET /{postingId}`、`POST /streams/rebuild`）、`OwnerAccountQueryApi`（`/owner-accounts/v1`：`GET`、`GET /{accountId}/entries`），请求 / 响应体放 `request` / `response` 包，字段、校验正则、分页（`PagingResponseBody`，`page` / `size` 规则同 ledger `LedgerBookQueryApi`）按 technique 2.2 表；金额用 `Money`（同 `ClientBalanceResponseBody`），ID / 版本 / 序号用十进制字符串。新增 `enums/BalanceMovementErrorCode`（`BALANCE_INSUFFICIENT`、`ACCOUNT_NOT_FOUND`、`ACCOUNT_NOT_ACTIVE`、`MOVEMENT_NOT_ALLOWED`）、`enums/AccountPostingErrorCode`（`STREAM_NOT_FOUND`、`POSTING_NOT_FOUND`）；`BalanceQueryErrorCode` 的 `LEDGER_BOOK_NOT_FOUND` 改为 `OWNER_ACCOUNT_NOT_FOUND`（中文消息「该核算对象在此主体与币种下没有账户」）。错误码枚举形态照现有 `BalanceQueryErrorCode`。
4. 删除 `BalanceBookApi` 及只被它使用的请求 / 响应体（`RebuildBookBalancesRequestBody`、`BalanceConsistencyResponseBody`、`BalanceRebuildResponseBody`、`BookBalanceResponseBody`、`BookBalancesResponseBody`、`OwnerBalanceCountResponseBody`）。
5. `package-info.java` 与各接口类注释更新为明细账语义（内部接口、未接 Gateway）。
6. `paytech/account/compensation-event-catalog.yaml`：`eventTypes: [ACCOUNT_POSTED]`，注释说明用途。

## 2. ledger-api（`com.paytech.ledger.api`）

1. 新增 `enums/LedgerOwnerType { OPERATING_ENTITY, FINANCE_ENTITY, CHANNEL_ACCOUNT, CASH_ACCOUNT, TAX_AUTHORITY }`、`enums/RuleOwnerRole { BOOK_ENTITY, CHANNEL_ACCOUNT, PLATFORM_FINANCE_ENTITY, TRANSACTION_OE, TAX_AUTHORITY }`（形态照 account-api 原 `OwnerType` / `OwnerRole`）。
2. `LedgerBookQueryApi.listAccounts` 的 `ownerType` 正则、`request/CreateAccountRequestBody.ownerType` 正则改为上述五值；相关 JavaDoc 说明控制科目规则。
3. `response/AccountJournalLineResponseBody`、`response/JournalEntryDetailResponseBody`：`originType` 注释增加 `ACCOUNT_POSTING`；新增 `accountPostingId`、`accountPostingSequence`（非 ACCOUNT_POSTING 为空）；`JournalEntryDetailResponseBody.Line` 新增 `accountEntryId`（规则行、人工行为空）。
4. 新增 `PostingStreamCommandApi`（`@HttpExchange("/posting-streams/v1")`，`POST /catch-up`，Body `{entityType, entityId, currency, shardNo, maxBatches}`，响应 `{processedCount, lastSequence, caughtUp}`，需要 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` Header，照 `LedgerBookCommandApi` 现有命令写法）。
5. 删除 `LedgerReadApi.getAccountTotals` 与 `response/LedgerAccountTotalsResponseBody`、`LedgerAccountTotalResponseBody`；删除 `TrialExceptionApi`、`request/StartProjectionRebuildRequestBody`、`request/ApproveProjectionRebuildRequestBody`、`response/TrialExceptionResponseBody`（确认只被该 API 引用）。`LedgerErrorCode` 中只服务于投影重建的 Code 标注 `// TODO by CLAUDE: B 线删除实现后移除` 而不要在本卡删除。
6. 其余接口不动。

## 3. billing

1. `EconomicFactPayload` 新增 `Instant balanceAvailableTime`（可空，注释：仅 PAYMENT、billing 已计算时非空；Schema 版本保持 1）；`EconomicFactContext.toPayload()`（或等价组装处）从 `EconomicFact.balanceAvailableTime` 填入；`EconomicFactPayloads.json` 序列化为 RFC 3339 字符串或 null，与现有时间字段一致。
2. `PendingEconomicFactsResponseBody` 新增 `publishedFactCount`（该主体 + 币种下 `published_at` 非空的 APPROVED / SUPERSEDED 事实数，十进制字符串，与现有字段风格一致）；补 Mapper / Repository / Service / Processor 链路与 Controller 转换。
3. 测试：billing-test 补充载荷含 `balanceAvailableTime`（空与非空）与 `publishedFactCount` 的 auto 用例；既有测试全部通过。
4. `paytech/foundation/paytech-messaging/catalog/events.yaml`：`EconomicFactRecorded` 的 `consumers: [account]` 并更新注释；删除 `BalanceMoved` 条目；新增 `AccountPosted`（producer account，aggregateType `ACCOUNT_POSTING`，aggregateVersionField `postingSequence`，consumers `[ledger]`，strictOrder true，payloadSchemaRef `paytech-account-api/event/AccountPosted/v1`），注释写聚合 ID 规则与 HANDLER_MANAGED 消费。

## 4. 表归属（R4）

按 technique 2.3 的 account 表清单：`t_owner_account`、`t_account_posting_stream`、`t_account_posting`、`t_account_entry`、`t_balance_applied_fact`、`t_balance_movement`、`t_compensation_event`（删除 `t_account_balance`、`t_balance_applied_entry`、`t_balance_discrepancy`）。改生成器的 `stepAddedTables` 与 design/03 对应行，重生成 `table-ownership.yaml`，`paytech/scripts/verify-reactor.sh` 的 `--check` 通过；`TableOwnershipAutoTest` 的总数与 account 集合断言同步。ledger 表集合不变。

## 5. 验证

- `./mvnw -B -o verify -pl account/paytech-account-api,ledger/paytech-ledger-api,billing/paytech-billing-test -am`（billing-test 全部通过）。
- `./mvnw -B -o verify -pl tools/arch-rules`（表归属测试通过）。
- `node --test` 或等价方式跑 foundation messaging 的目录校验（找到现有 `YamlEventCatalog` 相关测试并运行；若需 `-pl foundation/paytech-messaging` 就跑它）。
- 不要尝试编译 account-core / ledger-core / BFF（预期失败）。

## 6. 报告

`REQ/tasks/T0-report.md`：每个新增 / 修改 / 删除的类与字段清单（给 A / B / C 用）、`AccountPostedPayload` 示例 JSON 一份、events.yaml 前后对照、表归属生成器输出摘要、测试计数。
