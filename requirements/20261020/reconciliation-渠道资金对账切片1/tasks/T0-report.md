# T0 合同线交付报告（阶段 4）

> 范围：technique.md §2.9 T0。未提交、未推送、未执行任何 SQL；未调用 Superpowers skill。共享文件只做了局部修改。

## 1. 已读依据

AGENTS.md、DEVELOPMENT_CONVENTIONS.md（§2–4）、WRITE_API_EXAMPLE.md、COMPENSATION_EVENT_GUIDE.md（§1–2 与目录格式）、本需求 requirement.md / technique.md（§1、§2 全部）/ db 下两份 DDL、design-discuss/reconciliation/design.md（F1–F3、§6.3、§7、§8）；参照的源码：channel-connector-api（成本方案）、ledger-api（ManualAdjustmentApi）、billing-api（BillingFactPayload 等）、account-api（AccountPostedPayload）、billing / account / commercial 的补偿目录。

## 2. 文件清单

**新建（reconciliation-api，83 个 Java 文件）**，根包 `com.paytech.reconciliation.api`：

| 包 | 内容 |
|---|---|
| 根包 | `ReconciliationHeaders`（操作人头常量） |
| `clearing` | `ClearingAccountApi`、`ClearingAccountResolutionApi`；request 5 个、response 4 个 |
| `cash` | `CashAccountApi`；request 2 个、response 2 个 |
| `statement` | `ChannelStatementApi`、`PlatformTxnApi`；request 2 个、response 5 个 |
| `bank` | `BankStatementApi`、`ExpectedBankMovementApi`、`BankMatchApi`；request 3 个、response 6 个 |
| `exception` | `ReconExceptionApi`、`ReconResolutionApi`；request 3 个、response 3 个 |
| `summary` | `ReconSummaryApi`；response 1 个 |
| `enums` | 29 个 Code 枚举 + `ReconciliationErrorCode` |
| `event` | 四个事件载荷类 |

**其他新建：** `paytech/reconciliation/compensation-event-catalog.yaml`。

**修改：**
- `paytech/reconciliation/paytech-reconciliation-api/pom.xml`：加 `paytech-common-core`、`spring-web`、`jakarta.validation-api`（照 channel-connector-api / billing-api）；`package-info.java` 改写。
- `paytech/reconciliation/paytech-reconciliation-test/.../ReconciliationModuleBoundaryTest.java`：api 模块依赖白名单同步为上面 4 项（只改这一处断言；A 线改 core / app 依赖时还要改同文件其他断言）。
- billing（Q-02）：`FactRefType` 末尾加 `CHANNEL_TRANSACTION_REF`、`RECON_RESOLUTION`；`BillingFactRefPayload` 加可空 `externalReferenceType`、`externalTransactionId`；`BillingFactRefRequestBody` 注释写明人工登记不允许这两种引用；`BillingFactPayloads.json` 在每个引用里输出这两个键（无值时为 JSON null）；测试 `BillingFactEnvelopeChecker` 的引用字段顺序断言、`BillingFactInvariantsTest` 加两种新引用被拒的断言。
- `paytech/foundation/paytech-messaging/catalog/events.yaml` 与 `YamlEventCatalogAutoTest`。

没有改 billing 的 SQL、PDManer、测试 DDL、DataObject、Mapper（E 线；工作区里 billing 的 schema / pdma / 测试 DDL 改动不是本线做的）。

## 3. 接口与方法对照（technique §2.3）

前缀 `/reconciliation/v1`。分页列表统一返回 `PagingResponseBody<T>`，参数 `page`、`size`。

| # | 方法与路径 | 接口方法 |
|---|---|---|
| 1 | `POST /clearing-accounts` | `ClearingAccountApi.createClearingAccount`（201） |
| 2 | `PUT /clearing-accounts/{id}/settings` | `ClearingAccountApi.updateClearingAccountSettings` |
| 3 | `POST /clearing-accounts/{id}/mids` · `DELETE /clearing-accounts/{id}/mids/{midId}` | `addClearingAccountMid` · `removeClearingAccountMid` |
| 4 | `POST /clearing-accounts/{id}/status` | `changeClearingAccountStatus` |
| 5 | `GET /clearing-accounts` · `/{id}` · `/unassigned-mids` | `listClearingAccounts` · `getClearingAccount` · `listUnassignedMids` |
| 6 | `POST /clearing-accounts/resolutions` | `ClearingAccountResolutionApi.resolveClearingAccount`（内部，无操作人头） |
| 7 | `POST /cash-accounts` | `CashAccountApi.createCashAccount`（201） |
| 8 | `POST /cash-accounts/{id}/closure` | `requestCashAccountClosure`（无 Body） |
| 9 | `POST /cash-accounts/{id}/approval` | `reviewCashAccount`（`decision` + `approvedContentHash`） |
| 10 | `GET /cash-accounts` · `/{id}` | `listCashAccounts` · `getCashAccount` |
| 11 | `GET /cash-accounts/current` | `getCurrentCashAccount`（内部，四个必填参数） |
| 12 | `POST /channel-statements` | `ChannelStatementApi.startChannelStatementImport`（201） |
| 13 | `POST /channel-statements/{id}/lines` | `appendChannelStatementLines`（返回账单与接收进度） |
| 14 | `POST /channel-statements/{id}/completion` | `completeChannelStatementImport`（无 Body） |
| 15 | `POST /channel-statements/{id}/void` | `voidChannelStatement` |
| 16 | `GET /channel-statements` · `/{id}` · `/{id}/lines` | `listChannelStatements` · `getChannelStatement`（含门禁） · `listChannelStatementLines` |
| 17 | `GET /platform-txns` | `PlatformTxnApi.listPlatformTxns` |
| 18 | `POST /bank-statements` · `/{id}/lines` · `/{id}/completion` | `BankStatementApi.startBankStatementImport` · `appendBankStatementLines` · `completeBankStatementImport` |
| 19 | `GET /bank-statements` · `/{id}/lines` · `GET /bank-statement-lines` | `listBankStatements` · `listBankStatementLinesOfStatement` · `listBankStatementLines`（`cashAccountId` 必填） |
| 20 | `GET /expected-bank-movements` | `ExpectedBankMovementApi.listExpectedBankMovements` |
| 21 | `GET /bank-matches` | `BankMatchApi.listBankMatches`（流水或预期流水至少给一个） |
| 22 | `POST /bank-matches/{id}/confirmation` · `/{id}/rejection` | `confirmBankMatch` · `rejectBankMatch` |
| 23 | `POST /bank-matches` | `createBankMatches`（201，返回 `BankMatchListResponseBody`） |
| 24 | `POST /recon-exceptions` | `ReconExceptionApi.openReconException`（只给 `sourceType` + `sourceId`） |
| 25 | `GET /recon-exceptions` · `/{id}` | `listReconExceptions`（按账龄，默认只查未结） · `getReconException` |
| 26 | `POST /recon-exceptions/{id}/resolutions` | `submitReconResolution`（201） |
| 27 | `POST /recon-resolutions/{id}/approval` | `ReconResolutionApi.reviewReconResolution`（`decision` + `approvedContentHash`） |
| 28 | `GET /summary?entityNo&currency` | `ReconSummaryApi.getReconSummary`（`currency` 必填） |

分段导入（D-01）：开始导入带账单头、`fileName`、`fileContentHash`、`declaredLineCount`（渠道账单 1–20,000，银行对账单 0–20,000）；追加每段 1–100 行，每行带 `lineNo`，渠道账单行其他列放 `rawAttributes`（`Map<String,String>`，≤ 64 项）；完成导入返回 `status` 与 `rejections[{lineNo, reason}]`（`lineNo` 为空表示整单原因）。

写法约定：ID、金额、版本在 HTTP Body 中为字符串，金额正则照 ledger（非负 `(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?`，带符号的加 `-?`）；日期字段用 `LocalDate`，日期查询参数照 ledger 用 `YYYY-MM-DD` 字符串；时间用 `Instant`。

## 4. 枚举（`api.enums`）

全部实现 `CodeEnum`，Code 等于常量名，常量之间空一行：

`ClearingAccountStatus`、`ClearingDirection`、`PlatformTxnStatus`、`PlatformTxnSource`、`PlatformTxnSourceRefType`、`ChannelStatementStatus`（含 `RECEIVING`）、`ChannelLineType`、`SettlementDirection`、`CashDirection`、`LineMatchStatus`、`ReconExceptionType`（12 个）、`ExpectedMovementStatus`、`ExpectedMovementType`、`BankMatchStatus`、`MatchMethod`、`PayoutComponentType`、`ReconLayer`、`ReconExceptionStatus`、`ReconExceptionSourceType`（6 个）、`ResolutionType`、`ReconClassification`（9 个，Javadoc 写明各来源对象可用的子集）、`ResolutionStatus`、`CheckerDecision`、`CashAccountStatus`、`CashAccountPendingAction`、`ReconEntityType`、`AccountPurpose`、`BankStatementStatus`、`RematchTargetType`。

另有 `ReconciliationErrorCode`（`CodeMessageEnum`）：只有 `CLEARING_ACCOUNT_NOT_RESOLVED`，给 billing 调解析接口时区分用（照 channel-connector `ChannelCostErrorCode`）。

## 5. 事件载荷（`api.event`）

每个类都有 `EVENT_TYPE`、`SCHEMA_VERSION = 1`、`AGGREGATE_TYPE`、`AGGREGATE_VERSION = 1L`。字段类型照 `BillingFactPayload` / `AccountPostedPayload`：`Long` / `BigDecimal` / `Instant` / `LocalDate`，JSON 中都是字符串。

| 类 | 聚合 | 字段 |
|---|---|---|
| `ActualChannelCostConfirmedPayload` | `PLATFORM_TXN` | platformTxnId、billingFactId、clearingAccountId、clearingAccountCode、entityNo、currency、txnSource、expectedFee、actualFee、difference（实际 − 预估）、channelTransactionRefId、referenceType、externalTransactionId、statementId、confirmedTime |
| `ChannelBatchReconciledPayload` | `CHANNEL_STATEMENT` | statementId、clearingAccountId、clearingAccountCode、entityNo、currency、channelBatchRef、revision、settlementDate、cashDirection、netAmount、receivableTotal、payableTotal、actualFeeTotal、classifiedLines[lineNo、classificationCode、带符号 amount、resolutionId]、platformTxnIds[]、expectedMovementId（净额 NONE 时为空）、reconciledTime |
| `ChannelSettlementConfirmedPayload` | `BANK_MATCH` | bankMatchId、expectedMovementId、statementId、clearingAccountId、clearingAccountCode、cashAccountId、entityNo、currency、direction、matchedAmount、bankStatementLineId、valueDate、confirmedTime |
| `ReconAdjustmentApprovedPayload` | `RECON_RESOLUTION` | resolutionId、exceptionId、sourceType、sourceId、classificationCode、entityType、entityId、currency、带符号 amount、clearingAccountId、cashAccountId、makerId、checkerId、approvedContentHash、approvedTime |

`events.yaml`：`BillingFactRecorded` 的消费方改为 `[account, reconciliation]`，`schemaVersion` 仍为 1（D-09）；新增四个事件，生产方都是 `reconciliation`，`consumers: []`，`strictOrder: false`，`payloadSchemaRef: paytech-reconciliation-api/event/<Name>/v1`。

补偿目录：`ACTUAL_CHANNEL_COST_CONFIRMED`、`CHANNEL_BATCH_RECONCILED`、`CHANNEL_SETTLEMENT_CONFIRMED`、`RECON_ADJUSTMENT_APPROVED`（发布类），以及 `CHANNEL_STATEMENT_MATCH`、`BANK_MATCH_SUGGEST`（本地任务）。命名照 billing / commercial（大写下划线，和 Handler 的 `eventType()` 常量一致）。

## 6. 操作人身份

照 ledger `paytech/ledger/paytech-ledger-api/src/main/java/com/paytech/ledger/api/ManualAdjustmentApi.java`（technique 写的是"照人工调整"）：运营写命令都带两个请求头，`@RequestHeader("X-Gateway-Actor-Subject") @Pattern("[1-9][0-9]{0,18}") String actorAccountId` 和 `@RequestHeader("X-Gateway-Session-Id") String sessionId`，头名放在常量类 `ReconciliationHeaders`（照 `ChannelConnectorHeaders`）。复核请求的 Body 只带 `decision` 和 `approvedContentHash`，不带 checkerId。查询接口和两个内部接口（#6、#11）不带这两个头。

**没照 channel-connector `ChannelCostPlanApi`**：它的 maker / checker 放在 Body 里，自己的注释也写了"未接 Gateway 与统一身份前不得对外发布"。

## 7. 验证

全部在 `paytech/` 下运行，`JAVA_HOME` 为 JDK 25，`./mvnw -B -o`：

| 命令 | 结果 |
|---|---|
| `-q -pl reconciliation/paytech-reconciliation-api,billing/paytech-billing-api -am install -DskipTests` | 成功 |
| `-pl billing/paytech-billing-core,billing/paytech-billing-app,billing/paytech-billing-test,account/paytech-account-core,account/paytech-account-test,ledger/paytech-ledger-core,ledger/paytech-ledger-test,reconciliation/paytech-reconciliation-core,reconciliation/paytech-reconciliation-app,reconciliation/paytech-reconciliation-test -am test-compile -DskipTests` | BUILD SUCCESS（主代码与测试代码都编译通过） |
| `-pl foundation/paytech-messaging test -Dtest=YamlEventCatalogAutoTest` | 12 个测试全部通过；随后 `install -DskipTests`，让 `~/.m3` 里打包的目录是新的 |
| `-pl billing/paytech-billing-test -am test -Dtest=BillingFactInvariantsTest` | 7 个测试全部通过（纯 Java，不连库） |
| `-pl tools/arch-rules -am test -Dtest=CompensationHandlerRuleAutoTest,DomainEventHandlerCatalogRuleAutoTest` | 6 个测试全部通过 |
| `-pl reconciliation/paytech-reconciliation-test -am test -Dtest=ArchitectureAutoTest,ReconciliationModuleBoundaryTest` | 5 个测试全部通过（第一次跑时 api 依赖白名单失败，改断言后通过） |

**没跑的：** 要连本机库或 MQ 的测试都没跑（billing 发布测试里会用到 `BillingFactEnvelopeChecker.payloadShape`，account 消费测试），也没跑全仓 reactor。billing 发布测试的引用字段断言已经改成 4 个键，要在 E 线或阶段 5 实际跑一次确认。

## 8. 与 technique.md 的出入、待确认的问题

1. **四个事件的 `aggregateVersionField` 写成 `none`。** technique §2.4 写的是"聚合 / 1"，我理解为每个聚合只发一次、版本固定为 1，载荷里没有版本字段。目录只检查这个字段的格式。我没有用表上的 `version` 列，因为账单对平后 `cash_confirmed_amount` 还会改它，补偿 Handler 回读时版本会变，不能用来做幂等键。如果要用别的写法，请指出。
2. **`ReconAdjustmentApproved` 用 `entityType + entityId`，不用 `entityNo`。** technique 写的是"经营主体"，但银行流水挂的银行账户可能属于财务主体（`FINANCE_ENTITY`），异常表本身也存这两列。
3. **`ChannelBatchReconciled.platformTxnIds` 可能超过 64 KiB 信封上限。** Foundation `DomainEventCodec` 信封上限是 64 KiB，一张账单最多 20,000 行；平台流水 ID 超过约 2,500 个时，事件就发不出去。建议在 B / C 线定：要么限制每张账单的交易行数，要么载荷不带 ID 列表、改由 billing 回查。合同我暂时按 technique 保留了这个字段。
4. **`ResolutionStatus` 按 DDL 只有 3 个值**（`PENDING_CHECK`、`REJECTED`、`APPLIED`）。设计稿 7.19 还有 `DRAFT`、`APPROVED`；DDL 更新，以它为准。
5. **合同里写进了几条 technique 没明说的行为，实现线需确认：**
   - 银行账户新建被复核拒绝时，状态转为 `CLOSED`。D-03 只写了关闭申请被拒绝时清空申请。
   - 切片 1 不处理净额为借方（DEBIT）的渠道账单（需求 §4 放在切片 2），完成导入时整单拒绝。
   - 对平门禁在四项之外加了 `settlementCashAccountConfigured`（对应 DDL 注释"为空时对平被拦下"）。
   - 在途汇总的账龄段固定为 `D0_3`、`D4_7`、`D8_30`、`D31_PLUS`。
   - 异常列表不传 `status` 时只查未结异常。
6. **#26、#27 的提交和复核请求体里没有 `version`。** 并发靠锁加条件 SQL 保护，冲突返回 409。
7. **银行账户新建请求带明文账号。** C 线要把 `/reconciliation/v1/cash-accounts` 配进 `web.logging.metadata-only-path-prefixes`；BFF 的对应转发前缀也一样（D 线）。
8. **billing 的引用读取还没打通（交给 E 线）：**
   - `BillingFactPayloads.ref()` 还没从 `BillingFactRef` DataObject 取这两列，DataObject 里也还没有这两列，所以现在事件里这两个键始终为 null。
   - 读接口的 `BillingFactRefResponseBody`、billing-app 的 `BillingFactConverter.toRefBody`，以及 ledger `BillingFactClientImpl.toRef` 都没加这两个字段。
   - account 测试的发布探针 `BillingEventPublisher` 仍只输出 `refType` / `refId`；字段可空，account 读取不受影响。
9. **人工登记拒绝新引用类型不用改代码。** `BillingFactInvariants` 本来就按事实类型白名单校验（MANUAL_ADJUSTMENT 只认 TICKET，COST_ADJUSTMENT 只认 CLEARING_ACCOUNT、TICKET），两种新类型会返回 400 INVALID_REQUEST。本线只补了测试和注释。
