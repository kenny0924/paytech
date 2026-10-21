# B 交易层线交付报告（阶段 4）

> 范围：technique.md §2.9 B 线。未提交、未推送、未部署；未对任何数据库执行写 SQL（本机 `mor_reconciliation` 只由测试自身插入并清理，结束时 0 行）；未调用 Superpowers skill。只改了 `paytech/reconciliation/**` 与本报告。

## 1. 已读依据

AGENTS.md；agent-docs/DEVELOPMENT_CONVENTIONS.md（全文）；JAVA_PROJECT_STRUCTURE.md（全文）；examples/WRITE_API_EXAMPLE.md（全文，§11 自查见第 4 节）；UNIT_TEST_GUIDE.md 与 examples/UNIT_TEST_EXAMPLE.md；COMPENSATION_EVENT_GUIDE.md；TECHNOLOGY_STACK.md §6.7–6.9。本需求 requirement.md、technique.md（§1、§2 全部，含 §2.10 I-01–I-08）、db/reconciliation/ddl.sql、tasks/T0-report.md、tasks/A-report.md（第 4 节照做）。design-discuss/reconciliation/design.md §3、§4 F1–F4、§6.3–6.4、§7、§9。记忆：maven-jdk25、tests-use-enums、processor-template-no-javadoc、test-restclient-503。参照源码：account（BillingFactRecordedBalanceHandler、AccountPostedHandler、BalanceMaturityJobHandler）、channel-connector（AbstractLockProcessor 用法、ChannelConnectorLocks、Controller/Converter）、platform-support FileScanHandler（ObjectProvider）、Foundation compensation / messaging / http-client-support / redis-starter 源码。

## 2. 改动（按领域）

**core（新增）**
- 常量：`core/constant/ReconciliationLocks`（D-11 四类锁 Key + 统一 WAIT 10s / LEASE 5min；全部入口用 `tryLocks`，C 线必须沿用，否则单资源 SpinLock 与 RLock 落在不同 Key）。
- 领域纯规则：`domain/ChannelStatementLines`（交易行判定、方向、净额贡献、实际费用）、`ChannelStatementTotals`（恒等式）、`ReconcileGate`（门禁五项）、`ReconExceptionTypes`（层与阻塞派生）、`PlatformTxns`、`ClearingAccountRules`、`Amounts`；上下文与命令 `StatementMatchContext`、`VoidChannelStatementContext`、`OpenReconExceptionCommand`、`PlatformTxnFact`、`ClearingAccountSettings`、`MidProfile`、`UnassignedMid`、`LineRejection`、`ReconciliationEventPayloads`；View `domain/view/{ClearingAccountView, ChannelStatementView, ChannelStatementDetailView}`。
- 出站：`integration/ChannelMidClient` + `impl/ChannelMidClientImpl`（详情 → 按经营主体 + 状态 + 外部 MID 编码查列表取 channelCode；对方 4xx 视为“不存在”）。
- 服务（interface + Impl，`domainservice/service`）：`ClearingAccountService`、`ClearingAccountQueryService`、`PlatformTxnService`、`ChannelStatementService`、`ChannelStatementMatchService`、`ChannelStatementQueryService`、`ExpectedBankMovementService`、`ReconciliationEventQueryService`，以及给 C 线的 `ReconExceptionService`、`ChannelReconciliationResolutionService`。
- Processor（`service/processor`）：清算账户 Create / UpdateSettings / AddMid / RemoveMid / ChangeStatus / ListUnassignedMids；账单 StartImport / AppendLines / CompleteImport / Void / Match（匹配与对平）；ExpireWaitingChannelLines（Job）。
- 补偿 Handler（`service/handler`）：`ChannelStatementMatchHandler`（CHANNEL_STATEMENT_MATCH）、`AbstractEventPublishHandler` + `ChannelBatchReconciledHandler`、`ActualChannelCostConfirmedHandler`（C 线的两个发布 Handler 可直接继承基类）。
- MQ：`ext/mq/BillingFactRecordedPlatformTxnHandler` + `PlatformTxnFactReader`。
- 持久化扩展：`PlatformTxnRepository.findPage/countPage` 增加 `statementId`（I-02）；`ReconExceptionRepository.findLatestBySource`；`ChannelStatementLineRepository.listWaitExpired`（只取 MATCHING 账单的交易行）；对应 Mapper / XML。

**app（新增）**：`app/ext/rest/{ClearingAccountController, ClearingAccountResolutionController, ChannelStatementController, PlatformTxnController}`、`converter/{ClearingAccountConverter, ChannelStatementConverter, ReconciliationWire}`；`app/ext/job/ChannelLineWaitExpiryJobHandler`（`@XxlJob("channelLineWaitExpiry")`）。

**配置**：`messaging.consumers.billing.partitions: 4` 在 application.yml、config/nacos/reconciliation-service.yaml、application-mock.yml 三处启用。

**test**：基类 `test/base/AbstractReconciliationFlowTest`（分段导入、等待补偿、扮演 C 线批准的 `classifyLine` 等）；`test/builder/StatementImportBuilder`；`ChannelConnectorMocker.rejectMid` + `MockChannelMidController` 写出与 Problem 一致的 `X-Request-Id`（否则生产客户端把 400 解码为 502）；`PersistenceMappingTest` 补三条新仓储方法断言。新测试类见第 3 节。

## 3. AC → 测试

| AC | 测试（`paytech-reconciliation-test/.../auto/ext/`） |
|---|---|
| AC-02 | `http/ClearingAccountApiTest`：新建 / 编码重复 / 到账银行账户不合格 / 容差精度；改配置；挂 MID 成功与幂等、MID+币种重复归属（零调用）、MID 不存在 / 未激活 / 渠道 / 主体 / 币种不符；摘除；启停；未归属 MID；列表详情；解析命中与 `CLEARING_ACCOUNT_NOT_RESOLVED` |
| AC-04 | `eventhandler/BillingFactRecordedPlatformTxnHandlerTest`：PAYMENT→应收（预估费 = CHANNEL_COST 合计）、REFUND→应付、重投与同事实新版本只登记一次、其他类型 / 未批准忽略、缺 CLEARING_ACCOUNT / CHANNEL_TRANSACTION_REF、清算账户停用 → 重试后 DLQ、更正事实 → DLQ |
| AC-05 | `http/ChannelStatementImportTest`（三步、同号幂等、同号异内容 / 行号越界 / 精度 / 非上传人拒绝、整单 REJECTED 与逐行原因、DEBIT 净额拒绝、币种、更正版本规则）；`http/ChannelStatementVoidTest`（已对平不能作废） |
| AC-06 | `compensation/ChannelStatementMatchRulesTest`（金额超出、多条命中、跨账单重复 + ACCEPT 后交还匹配、无交易号 / 无对应交易行的 FEE 行、等待行在平台流水到达后重试）；`ChannelStatementReconcileTest`（完全匹配、部分匹配跨两张账单、带交易号 FEE 行并入、调整行） |
| AC-07 | `ChannelStatementReconcileTest.test_s1Walkthrough`（0.45≠0.50 发成本事件）、`test_equalFeeNoCostEventAndFoldedFee`（相同不发）、`test_costDeviationBlocks`；`ChannelStatementVoidTest.test_voidReleasesMatches`（作废后行、费用、平台流水回退，异常 AUTO_CLOSED，重导入重新开偏差） |
| AC-08 | `ChannelStatementReconcileTest`：S1 门禁两项未满足 → 归类后对平，预期银行流水 CREDIT 67.55，`ChannelBatchReconciled` 合计 / 归类行（3、CHANNEL_FEE_OTHER、−2.00、处置 ID）/ 平台流水笔数 / 预期流水 ID / 净额恒等式；NONE 净额直接 CASH_CONFIRMED；未配置到账银行账户被门禁拦下 |
| AC-13 | `job/ChannelLineWaitExpiryJobTest`：只处理 MATCHING 账单中过期的交易行，重复执行不重复开，后到平台流水使 CHANNEL_LINE_UNMATCHED AUTO_CLOSED |
| AC-15 | 全模块 64 例通过，含 ArchitectureAutoTest（共享架构规则零未登记违规）与 ModuleBoundaryTest |
| 其他入口 | `compensation/ReconciliationCompensationHandlerTest`（三个补偿 Handler 的身份校验与终态失败）；#16 详情门禁、#17 `statementId` 过滤在 S1 / 门禁用例中断言 |

## 4. WRITE_API_EXAMPLE §11 自查

- 合同：沿用 T0 的 api（未改 Body）；内部 Request 用 class + Bean Validation，由 `AbstractProcessor.validateBasic` 触发；Job / MQ / 补偿入口各有解析校验。
- Controller / Converter 只做协议转换；每个写操作一个 Processor（`service/processor`），主方法显示阶段与提前返回（已作废 / 非 RECEIVING / 同 MID 已在本账户直接返回）。
- Processor 只查 Repository，写入经 Service；多 Service 原子写由 Processor 开 `TransactionTemplate` 组合（匹配：匹配保存 + 异常开立 / 结案 + 对平 + 预期银行流水；作废：作废 + 异常结案；Job：开异常 + 行转 EXCEPTION）。
- Context：`StatementMatchContext`、`VoidChannelStatementContext` 持本模块 DataObject，计划值与持久化分离，Repository 条件更新成功后同步原对象。
- Service 不调其他 Service / Processor / 外部依赖；channel-connector 调用在 Processor 事务前（挂 MID、未归属 MID）。
- 锁：清算账户级（改配置、挂摘 MID、启停）、账单级（追加、完成、匹配、作废），统一 `tryLocks`；开始导入与 Job 不加锁，分别由 uk(revision) 与行版本 CAS 兜底。
- 业务防重：billing_fact_id、MID + 币种（先查后 uk 兜底 409）、同批次未作废版本、行号同内容幂等、异常 active_slot；无 HTTP Idempotency-Key 处理。
- View 只用于多表组合查询（清算账户 + MID、账单 + 清算账户 + 门禁）；DataObject 不出 core。
- 未新增配置类；未引入生产 Mock 或测试分支。
- 验证见第 6 节；未覆盖项见第 7 节。

## 5. 给 C 线的共享能力（精确签名）

```java
// com.paytech.reconciliation.domainservice.service.ReconExceptionService（REQUIRED，在调用方事务内）
ReconException open(OpenReconExceptionCommand command);          // 同对象同类型有未结异常时返回已有；层与阻塞由类型派生
boolean autoClose(ReconExceptionSourceType sourceType, Long sourceId, ReconExceptionType exceptionType); // 只结 OPEN
void markUnderReview(ReconException exception);                  // OPEN → UNDER_REVIEW
void reopen(ReconException exception);                           // UNDER_REVIEW → OPEN
void resolve(ReconException exception);                          // UNDER_REVIEW → RESOLVED，写结案时间

// com.paytech.reconciliation.domain.OpenReconExceptionCommand（Lombok @Builder）
exceptionType, sourceType, sourceId, statementId, clearingAccountId, cashAccountId, entityType, entityId, currency,
differenceAmount, detail, openedBy

// com.paytech.reconciliation.domainservice.service.ChannelReconciliationResolutionService（必须在 C 线批准事务内调用）
ChannelStatementLine applyLineRematch(Long lineId, Long platformTxnId, Long resolutionId);
ChannelStatementLine applyLineClassification(Long lineId, Long resolutionId);
PlatformTxn applyPlatformTxnClassification(Long platformTxnId, BigDecimal signedAmount); // |金额| 须等于未结清金额
ChannelStatementLine releaseLineForRematching(Long lineId);      // DUPLICATE_SETTLEMENT 的 ACCEPT：先 resolve 异常再调
void scheduleStatementEvaluation(Long statementId, Long resolutionId);

// 扩展点：com.paytech.reconciliation.domainservice.service.ExpectedBankMovementService
ExpectedBankMovement createChannelSettlement(ChannelStatement statement, ClearingAccount clearingAccount);
// C 线在该方法内同一事务登记 BANK_MATCH_SUGGEST；B 线调用方不用改。

// 锁：com.paytech.reconciliation.core.constant.ReconciliationLocks
reconException(id) / bankStatementLine(id) / expectedBankMovement(id)，WAIT / LEASE；用 LockCommand.tryLocks
// 发布 Handler 基类：service.handler.AbstractEventPublishHandler<P>（静态 request(...) 组事件键）
```

C 线批准顺序建议（测试基类 `classifyLine` 即按此）：同一事务内 处置 APPLIED → `markUnderReview`（若尚未）→ `resolve` → `applyXxx` → `scheduleStatementEvaluation`。

## 6. 验证（JDK 25，`paytech/` 下）

| 命令 | 结果 |
|---|---|
| `./mvnw -B -o -pl reconciliation/paytech-reconciliation-api,reconciliation/paytech-reconciliation-core,reconciliation/paytech-reconciliation-app install -DskipTests` | BUILD SUCCESS |
| `./mvnw -B -o -pl reconciliation/paytech-reconciliation-test verify` | 64/64 通过（原 25 + 新 39），BUILD SUCCESS |
| 结束后只读核对 `mor_reconciliation` 业务表与补偿 / 消费表 | 0 行；Redis 前缀与分区队列 / DLQ / 临时观察队列由基类清理 |

没跑：全仓 reactor、其他服务测试、`npm --prefix requirements-site test`（本线未改站点）。

## 7. 偏差与待确认

1. **AC-04 措辞**：实现按 technique §2.4——缺引用 / 清算账户缺失或停用抛可重试异常，messaging 重投 5 次后进 `reconciliation.billing.dlq`，不是“停在补偿 ERROR”。请主会话修订 AC-04 措辞或另定。
2. **`scheduleStatementEvaluation` 多一个 `resolutionId` 参数**：补偿登记在同身份任务执行中会被视为重复而丢失唤醒，所以 CHANNEL_STATEMENT_MATCH 的 `sub_event_type` 带触发来源（`IMPORT` / `PLATFORM_TXN-<id>` / `RESOLUTION-<id>`）。
3. **AMBIGUOUS_REFERENCE 的口径**：uk 含方向，同方向不可能命中多条；实现按“同交易号、OPEN / PARTIALLY_MATCHED、不分方向命中多于 1 条”开异常（不猜测）；只命中 1 条但方向不符视为未命中，继续等待。
4. **行级异常差异金额 = 该行带符号净额贡献**（非 FEE：带方向金额 − 行上费用；FEE：−(金额 + 费用)），保证“应收 − 应付 − 实际费用 + Σ 归类 = 净额”。AMOUNT_MISMATCH 也取整行贡献（说明里写剩余金额），不是只取超出部分。
5. **已归类的交易行**（如对 CHANNEL_LINE_UNMATCHED 做 CLASSIFY）门禁视为已处理，计入 `classifiedLines`，不计入应收 / 应付合计。
6. **`platformTxnCount`** = 本账单行匹配到的去重平台流水数（与 `GET /platform-txns?statementId=` 总数一致）；部分覆盖时与“本账单使其 SETTLED 的笔数”不同。
7. **新平台流水触发范围收窄**：只为同清算账户、同交易号、行未 MATCHED 的 MATCHING 账单登记匹配任务（比“所有有等待行的账单”精确，含已挂未命中异常、可自动结案的行）。
8. **跨账单查重**只比更早导入（主键更小）、状态 MATCHING / RECONCILED / CASH_CONFIRMED 的账单；作废一张账单不会自动撤销其他账单上已开的 DUPLICATE_SETTLEMENT。
9. **等待期满任务**只处理交易行；带交易号的 FEE 行随交易行，ADJUSTMENT / 无交易号 FEE 行由匹配任务直接开异常。Job 不加账单锁，依赖行版本 CAS。
10. **COST_DEVIATION**：挂在平台流水上（source PLATFORM_TXN，statement_id 为使其完全匹配的账单），差异 = 实际 − 预估；每笔只开一次，作废自动结案的除外。
11. **匹配任务超时 100 秒**（须短于补偿默认 Lease 2 分钟）；匹配每行约 2 次查询，2 万行账单的耗时未实测，可能需要批量查询或调大 `compensation.lease-duration`。
12. `ChannelStatementMatchHandler` 经 `ObjectProvider` 取处理器，避免与补偿登记器的启动循环。
13. channel-connector 任何 4xx（含 409）都按“MID 不存在”拒绝；能力组不按启用状态过滤。清算账户写命令不落操作人（表无列），只校验头。
14. 作废时被覆盖的平台流水若已 SETTLED（只会出现在 C 线的平台流水归类之后）拒绝作废。
15. 改动了 A 线文件：`PlatformTxnRepository` / Mapper 的分页签名（加 `statementId`）、三个新仓储方法、`PersistenceMappingTest`、`ChannelConnectorMocker` / `MockChannelMidController`。
