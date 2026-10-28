# 阶段 4 第 2 步：ledger、billing 迁移到参考数据客户端 · 研发与验证记录

> 工作区：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refclient`，私有 Maven 仓库 `~/.m3-refclient`。只改 `paytech/ledger/**`、`paytech/billing/**`。未提交、未暂存、无 SQL、无 docker。
> 对应 technique.md 第 12 节 ledger / billing 两行、第 14 节步骤 2、AC-07。

## 1. 已读

requirement.md、technique.md（1—14 节）、verification/rdc-core.md；AGENTS.md、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`UNIT_TEST_GUIDE.md` + `examples/UNIT_TEST_EXAMPLE.md`；两服务现有币种客户端 / 快照 / 调度 / 健康 / 配置 / Mock 及全部调用方；客户端模块与测试支持模块源码（`ReferenceData`、`ReferenceCatalog`、`ReferenceDataRefresher`、`ReferenceDataMocker`、`ReferenceDataBuilders`、`MockReferenceDataApi`）。

本步没有新增或修改写接口，WRITE_API_EXAMPLE 第 11 节核对项不适用。

## 2. 文件清单

### ledger（相对 `paytech/ledger/`）

| 动作 | 文件 |
|---|---|
| 删除 | core `integration/platform/PlatformCurrencyClient`、`integration/platform/impl/PlatformCurrencyClientImpl`、`integration/platform/CurrencySpecSnapshot`（空目录一并删除） |
| 删除 | app `configuration/CurrencySnapshotConfiguration`、`CurrencySnapshotHealthIndicator`、`CurrencySnapshotProperties`、`PlatformSupportClientConfiguration`；`ext/job/CurrencySnapshotRefreshScheduler` |
| 删除 | test `mock/external/MockCurrencySpecApi`、`mocker/external/CurrencySpecMocker`、`auto/ext/job/CurrencySnapshotRefreshTest` |
| 修改 | core `domain/CurrencyRule`（状态类型 `CurrencySpecStatus` → `ReferenceDataStatus`）、`domainservice/service/CurrencyRuleService`（Javadoc）、`impl/CurrencyRuleServiceImpl`（改读 `ReferenceData`）、`service/processor/CreateLedgerBookProcessor`（注释去掉"触发刷新"） |
| 修改 | `paytech-ledger-core/pom.xml` 加 `paytech-platform-support-reference-data-client`（BOM 版本）；`paytech-ledger-app/pom.xml` 删 `paytech-platform-support-api`（只服务已删的查询客户端）；`paytech-ledger-test/pom.xml` 加 `…-client-test-support`（test） |
| 修改 | app `application.yml`；test `application-mock.yml` |
| 修改 | test `MockTestBase`、`LedgerTestMockConfiguration`、`LedgerModuleBoundaryTest`、`CurrencyRulePostingTest`、`CreateLedgerBookTest`、`StartPeriodCloseTest`、`ReviewPeriodReopenTest` |
| 新增 | test `auto/domainservice/posting/ReferenceDataRefreshPostingTest` |
| 修改 | `README.md`（模块职责与币种规则说明） |

### billing（相对 `paytech/billing/`）

| 动作 | 文件 |
|---|---|
| 删除 | core `core/common/CurrencySpecSnapshot`、`integration/platform/PlatformCurrencyClient`、`integration/platform/impl/PlatformCurrencyClientImpl`（空目录一并删除） |
| 删除 | app `configuration/CurrencySnapshotConfiguration`、`CurrencySnapshotHealthIndicator`、`CurrencySnapshotProperties`、`PlatformClientConfiguration`（只注册 `CurrencySpecQueryApi`）；`ext/job/CurrencySnapshotRefreshScheduler` |
| 删除 | test `mock/external/MockCurrencySpecApi`、`mocker/external/CurrencySpecMocker`、`builder/CurrencySpecResponseBuilder`、`auto/ext/job/CurrencySnapshotRefreshSchedulerTest` |
| 修改 | core `domain/CurrencyRule`（状态类型同 ledger）、`CurrencyRuleService`（Javadoc）、`impl/CurrencyRuleServiceImpl`（改读 `ReferenceData`） |
| 修改 | `paytech-billing-core/pom.xml` 加客户端；`paytech-billing-test/pom.xml` 加测试支持（test）；`paytech-billing-app/pom.xml` 只改 `http-client-support` 的注释（仍服务 commercial / channel-connector / reconciliation / ledger 四组） |
| 修改 | app `application.yml`；test `application-mock.yml` |
| 修改 | test `MockTestBase`、`BillingTestMockConfiguration`、`BillingModuleBoundaryTest`、`SubmitBillingFactTest`、`BillingFactRecordedHandlerTest`、`PaymentFactConstants`、`DtoResponseMocker`（`REQUEST_ID` 不再借用币种 Mocker，改为 `"upstream-test"`）、4 个上游 Mock API（`markRequestId` 改调新 `MockRequestId.mark()`） |
| 新增 | test `mock/external/MockRequestId`（原寄居在 `MockCurrencySpecApi` 的响应头工具）、`builder/TestCurrencies`（USD / EUR / JPY / GBP 常量，原在 `CurrencySpecResponseBuilder`）、`auto/ext/http/fact/ReferenceDataRefreshTest` |
| 修改 | `README.md`（币种规则说明） |

## 3. 行为说明

### 3.1 `CurrencyRule` 保留，状态类型改为 `ReferenceDataStatus`

两服务都选"改类型"，不做映射：`CurrencyRule` 的状态只在本类 `isUsable()` / `usable()` 与 `CurrencyRuleServiceImpl` 中使用（ledger 9 处、billing 5 处调用方只读 `getMinorUnit()`），改类型只动这两个类；映射需要新增一个 `ReferenceDataStatus → CurrencySpecStatus` 的转换并继续依赖 Platform 将保留但与参考数据无关的 `CurrencySpecStatus`。`roundingMode` 直接沿用 `CurrencyRoundingMode`（客户端值对象同类型）。

### 3.2 ledger：查不到 = 可重试的系统异常（语义不变）

`requireKnown` 改为 `referenceData.currencies().require(code)`：查不到抛 `ReferenceDataNotFoundException`（`ErrorException`，错误码 `INTERNAL_ERROR`，消息 `参考数据不存在: CURRENCY USD`），再转换为 `CurrencyRule`；`requireUsable`（拒 DRAFT）/ `requireActive`（只收 ACTIVE）判定与消息保留在 ledger。"查不到时请求刷新"已删除（Q1 / R4）。

证据（`CurrencyRulePostingTest`，均通过）：

- `test_missingCurrencyStallsStreamUntilRefresh`：快照只有 AED，经真实 Broker 发布 USD 账簿的 AccountPosted，等待 billing 事实被读取 ≥ 2 次（即消息已被重投），此时无指令 / Journal / 账户残留、消费进度为 0、**死信数 0**、参考数据请求次数不变（过账不回源）；随后经 `refreshNow()` 恢复 USD，重投的批次自动过账，死信仍为 0，CURRENCY 只被请求一次（只有显式刷新）。
- `test_catchUpWithMissingCurrencyRetriedAfterRefresh`：追平命令返回 **500 `INTERNAL_ERROR`**、不推进进度、不残留、不回源；刷新后再次追平成功。
- `test_missingCurrencyIsRetryableFailure`：直接调用 Handler 抛 `ReferenceDataNotFoundException`，`getKind()=CURRENCY`、`getReferenceCode()=USD`、`getCode()=INTERNAL_ERROR`。
- `test_draftCurrencyRejected`：DRAFT 抛 `DomainEventRejectedException(cause=BizCheckFailException "币种尚未启用: USD, 状态 DRAFT")`（业务拒绝，进入死信语义，与迁移前一致）。
- `test_inactiveCurrencyStillPosts`：INACTIVE 照常过账，过账期间零请求。

`CreateLedgerBookProcessor.checkActiveCurrency` 原本就把 `BizCheckFailException | ErrorException` 统一转为 `CURRENCY_NOT_ACTIVE`，因此未知币种（NotFound）与未就绪（Unavailable）仍得到 `CURRENCY_NOT_ACTIVE`（`CreateLedgerBookTest.test_referenceDataMustBeActive`、`test_nonActiveCurrencyRejected[INACTIVE/DRAFT]` 通过）。

### 3.3 billing：沿用原有"查不到 = 业务拒绝"（偏差，见第 6 节 D1）

billing 迁移前查不到币种抛 `BizCheckFailException`（登记命令 422 "币种不存在或尚未同步"），而事实发布（`BillingFactPublicationServiceImpl.minorUnit`）与自动 PAYMENT 事实登记（`RecordPaymentFactProcessor.minorUnitOf`）在调用处把 `BizCheckFailException` 转成 `ErrorException` 以便重试。为满足 AC-07"行为与迁移前一致"，billing 的 `CurrencyRuleServiceImpl` 用 `find(code)`，空时仍抛 `BizCheckFailException("币种不存在或尚未同步: X, 等待 Platform 币种快照下一次刷新后重试")`；DRAFT 仍为 "币种未启用: X, 状态 DRAFT"。快照未就绪时 `find` 抛 `ReferenceDataUnavailableException`（系统异常），不再被当作"未知币种"。

证据：`SubmitBillingFactTest.test_currencyPrecision` / `test_draftAndUnknownCurrencyRejected` / `test_newlyActiveCurrencyRecognizedAfterRefresh`（422，登记不回源，刷新后按 0 位精度登记 JPY）、`test_inactiveCurrencyAccepted`；`BillingFactRecordedHandlerTest.test_currencyMissingFromSnapshotIsRetryable`（补偿 Handler 抛 `ErrorException` 可重试、不发布、不回源）；`OrderPaymentSucceededRetryTest` 9 例通过。

### 3.4 就绪

两服务 readiness 组改为 `include: readinessState,referenceData`。`LedgerApplicationTest` / `BillingApplicationTest` 的 readiness UP 用例通过；新增的两个刷新测试验证 Platform 503 时刷新失败、保留快照、`referenceData` 健康仍 UP、readiness 200、业务照常。

## 4. 配置差异

### ledger `application.yml`

```diff
-      platform-support-service:
-        base-url: "${LEDGER_PLATFORM_SERVICE_URL:http://127.0.0.1:18106/platform-support}"
-        connect-timeout: 2s
-        read-timeout: 5s
-        redirects: dont-follow
+platform-support:
+  reference-data:
+    client:
+      base-url: "${LEDGER_PLATFORM_SERVICE_URL:http://127.0.0.1:18106/platform-support}"
+      kinds: [CURRENCY]
 ...
-          include: readinessState,currencySnapshot
+          include: readinessState,referenceData
 ...
-  currency-snapshot:
-    scheduling-enabled: true
-    check-interval: PT5S
-    refresh-interval: PT60S
```

### billing `application.yml`

同上，环境变量 `BILLING_PLATFORM_SERVICE_URL`（默认 `http://127.0.0.1:18106/platform-support`）；删除 `billing.currency-snapshot.*` 与 `spring.http.serviceclient.platform-support-service`（billing 只为币种注册了该组）；commercial / channel-connector / reconciliation / ledger 四组不变。

刷新间隔、初始重试、超时均不覆盖（默认 PT1H / PT10S / 2s / 5s）。环境变量名与默认值不变，mor-test compose 无需改值（注释由第 13 节另行处理）。

### 测试 `application-mock.yml`（两服务）

删除 `platform-support-service` 组与 `*.currency-snapshot.*`；新增 `platform-support.reference-data.client`，`base-url: ${<svc>.test.external.base-url}`、`kinds: [CURRENCY]`、`scheduling-enabled: false`，并列出全部 7 个键（与生产默认同值）；readiness 组同生产。

### 测试基建

- `@EnableMockHttpServer` 扫描包加入 `com.paytech.platform.support.referencedata.client.test`（`MockReferenceDataApi` + `ReferenceDataMocker` 注册到本服务 Mock 服务）。
- `MockTestBase`：注入 `ReferenceDataMocker`、`ReferenceData`、`ReferenceDataRefresher`；全局 BeforeEach 调 `refreshCurrencies(ReferenceDataBuilders.currency(USD))`（`reset(CURRENCY)` → `currency(...)` → `refreshNow()` 并断言全部成功）；全局 AfterEach 调 `referenceDataMocker.assertNoUnexpectedCalls()`，`clear()` 由 Foundation TestBase 统一执行。billing BeforeEach 的空基线检查改为 `referenceDataMocker.totalCalls() == 0`。
- 没有 Mockito 桩、`@MockBean` 或生产测试分支。

## 5. 执行命令与结果（JDK 25，`paytech/` 下）

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -o -q -pl ledger/paytech-ledger-api,ledger/paytech-ledger-core,ledger/paytech-ledger-app,ledger/paytech-ledger-test -am -DskipTests install
#   成功
./mvnw -o -q -pl billing/paytech-billing-api,billing/paytech-billing-core,billing/paytech-billing-app,billing/paytech-billing-test,ledger/paytech-ledger-test -am -DskipTests install
#   成功
./mvnw -o -pl ledger/paytech-ledger-api,ledger/paytech-ledger-core,ledger/paytech-ledger-app,ledger/paytech-ledger-test test \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
#   Tests run: 331, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
#   （含 ArchitectureAutoTest 1、LedgerModuleBoundaryTest 4、CurrencyRulePostingTest 5、ReferenceDataRefreshPostingTest 2、CreateLedgerBookTest 9）
./mvnw -o -pl billing/paytech-billing-api,billing/paytech-billing-core,billing/paytech-billing-app,billing/paytech-billing-test test \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
#   Tests run: 131, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
#   （含 ArchitectureAutoTest 1、BillingModuleBoundaryTest 4、SubmitBillingFactTest 17、BillingFactRecordedHandlerTest 7、ReferenceDataRefreshTest 1）
./mvnw -o -DskipTests test-compile
#   整个 Reactor BUILD SUCCESS（当时其他 agent 的进行中模块也通过编译）
./mvnw -o -pl tools/arch-rules-reactor-test test
#   Tests run: 5, Failures: 0 — BUILD SUCCESS
```

两服务串行运行，只跑本服务模块，未跑整个 Reactor 测试。worktree 内没有 `local-environment/.env.local`，用 `-Dpaytech.local-environment.dir` 指向主仓库的 local-environment（Surefire 注入、`spring.config.import` 读取，未在命令行或输出中出现凭据）。

残留检查：`grep -rn "CurrencySpecQueryApi|/currency-specs|currency-snapshot|CurrencySnapshot|CurrencySpec|platform-support-service" paytech/ledger paytech/billing`（排除 target 与 docs）无结果。`ledger/docs/database/sql/changes/2026-10-20-drop-currency-spec-copy.sql` 是已执行的历史变更脚本，按原样保留。

## 5.1 基于更新后 starter 的复跑（X-Caller-Service、base-url 可选）

- starter 新版（`~/.m3-refclient` 中 jar 时间 2026-10-27 22:40）：每个请求带 `X-Caller-Service`（`caller-service`，默认 `spring.application.name`），`base-url` 可选（不配走服务发现）。
- ledger / billing 生产配置继续用 `base-url` 直连原环境变量（注释注明不走服务发现）；`spring.application.name` 分别为 `ledger-service`、`billing-service`，符合小写服务名格式，与服务身份一致，因此生产不显式配置 `caller-service`。
- 测试 `application-mock.yml` 补列 `service-id: platform-support-service`、`context-path: /platform-support`（base-url 存在时不生效）与 `caller-service: <svc>-service`，保持"列全字段"。
- 接入测试新增断言：`referenceDataMocker.lastCallerService()` 为 `ledger-service`（`ReferenceDataRefreshPostingTest.test_newCurrencyRecognizedAfterRefresh`）/ `billing-service`（`ReferenceDataRefreshTest.test_platformUnavailableKeepsSnapshot`）。

复跑（命令同上，先 `-DskipTests install` 本服务 api/core/app，未 `-am` 重建 starter）：ledger `Tests run: 331, Failures: 0, Errors: 0`；billing `Tests run: 131, Failures: 0, Errors: 0`；整个 Reactor `-DskipTests test-compile` BUILD SUCCESS；`tools/arch-rules-reactor-test` 5 / 0 BUILD SUCCESS。

## 6. 偏差

| # | 偏差 | 原因 |
|---|---|---|
| D1 | billing 查不到币种仍是 `BizCheckFailException`（登记 422），没有改成 `ReferenceDataNotFoundException`（500） | technique 第 12 节写"billing 同 ledger"，但 billing 迁移前的语义本来就和 ledger 不同（F-02 只描述了 ledger），AC-07 要求行为一致；billing 的两条异步路径已在调用处转为可重试的 `ErrorException`。已裁决保留（team-lead，2026-10-27）：AC-07 要求行为不变，不统一成 500 |
| D2 | ledger 查不到币种的消息由 "币种规则不存在或尚未同步: USD" 变为客户端的 "参考数据不存在: CURRENCY USD" | 直接使用 starter 的 `require`（technique 第 12 节 ②）；错误码仍为 `INTERNAL_ERROR`，对外 Problem 不显示内部消息 |
| D3 | 服务级快照专项用例缩减：ledger 原 `CurrencySnapshotRefreshTest`（14 例：503、9 种非法响应、按需 / 到期回源、首次加载前 DOWN）、billing 原 `CurrencySnapshotRefreshSchedulerTest` 改为各服务一个接入测试（ledger 2 例、billing 1 例） | 加载、校验、首次重试、未就绪、周期等通用行为已由客户端模块 45 例覆盖（rdc-core 第 6 节）；按需回源 / 待刷新标记按 R4 已取消；服务内只保留"503 保留快照 + 就绪 UP + 业务照常"与"刷新后识别新币种"的接入证据 |
| D4 | billing 测试新增 `MockRequestId`、`TestCurrencies` 两个小类 | 原先的响应头工具与币种常量寄居在被删除的 `MockCurrencySpecApi` / `CurrencySpecResponseBuilder` 中，4 个上游 Mock 与多个测试仍需要它们 |

## 7. 遗留

1. D1 已裁决（team-lead，2026-10-27）：billing 保留原行为（`find()` + 422 与原消息），不统一成 500。理由是 AC-07 要求行为不变，ledger 与 billing 迁移前的语义本来就不同。
2. mor-test compose 中 ledger / billing 的注释（第 13 节）不在本步范围；环境变量名不变。
3. 部署后行为变化（Q1 已确认）：新启用币种最长约 1 小时后才能被 ledger / billing 使用；Platform 停机时启动的实例 readiness 为 OUT_OF_SERVICE，每 10 秒重试。
