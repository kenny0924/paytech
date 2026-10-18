# 10 · 测试基线与清理拆分

- 状态：阶段 3 方案评审稿。
- 依据：六份盘点（[analysis/](../analysis/)）第 8 节、[technique.md 第 3 节](../technique.md#3-测试基线搬迁验收的不少于基准)、[UNIT_TEST_GUIDE.md](../../../../agent-docs/UNIT_TEST_GUIDE.md) 第 4、7、12、13、17 节，并于 2026-10-17 对 `Code-MoR-refactor` 工作树的测试源码、`application-mock.yml`、`init/ddl.sql`、`local-environment` 逐项核实。未运行构建或测试。
- 读者：执行第 3 到第 7 步搬迁的会话；每步合入 `refactor-main` 前按第 6 节门禁核对。
- 术语：“现系统”指 `client-contract-service`、`commerce-service`、`payment-orchestration-service`、`risk-compliance-service`、`finance-service` 五个现有工程；“目标服务”指 V2 的 15 个逻辑服务（client-master、commercial、acquiring、consumer、payment、channel-connector、card、risk-decision、compliance、billing、account、ledger、reconciliation、tax，payout 延后）。

## 0. 结论先行

1. **基线数字须更正。** 本次按源码核实（`@Test` + `@ParameterizedTest` 方法数，不含参数化展开）：CCT 53、Commerce 285、Payment 96、Risk 738、Finance 236。technique.md 第 3 节记录的 Finance 80 与 Risk 596 均与当前源码不符：Finance 台账最近一次运行是 242/242（2026-10-17，含参数化展开），Risk 的 596 无法从当前源码复现。门禁以第 6 节定义的 Surefire 实际执行数为准，源码方法数作为第二道核对。
2. **今天没有任何 ArchUnit。** 三个“边界测试”（`RiskModuleBoundaryAutoTest`、`FinanceModuleBoundaryTest`、`ChannelModuleBoundaryTest`）全部是手写的 POM XML 解析与正则扫描；仓库任何 `pom.xml` 都不含 `archunit`。第 6 节的规则集是新增能力，依赖 BOM 新增一个坐标。
3. **五个现系统里有四个清理器跨未来 core。** `FinanceDataCleaner` 28 表跨 ledger / account / billing / reconciliation；Payment `ChannelTestMapper` 清 payment / channel-connector / card 全部 15 表；Commerce `CheckoutDataMocker.clear()` 在一个事务里同时删订单与 Customer；CCT `CheckoutResolutionFixture` 种 23 张表跨 client-master 与 commercial；Risk `MockTestBase` `@Import` 全部核心的 Mocker / TestMapper / Checker。全部必须在对应搬迁步骤内拆成按库隔离的清理器。
4. **测试 DDL 只有三份且都不完整。** Finance 26/84、Payment 15/31、Commerce 16/17（缺的是待删除的 `t_command_idempotency`，不算缺口）；CCT 与 Risk 根本没有 `init/ddl.sql`，违反测试规范第 7.1 节。15 个目标服务每个都要有一份覆盖自身全部表的 DDL。
5. **数据库前置检查与路径耦合要换锚点。** `RiskDatabasePrecondition` 固定 85 表与 `mor_risk`/`risk_app`；Payment `application-mock.yml` 用 `../../local-environment/.env.local` 相对路径导入凭据；Platform `LocalMockEnvironmentInitializer` 以 `platform-support-service/pom.xml` 找仓库根。统一改为由父 POM 注入 `paytech.local-environment.dir`。

## 1. 测试基线表

### 1.1 现系统（搬迁前基线，2026-10-17 源码核实）

统计口径：只统计 `src/test/java/**/auto/**` 下含用例的类；`@Test` 与 `@ParameterizedTest` 分别计数，“方法合计”为两者之和；“最近记录”摘自 `agent-docs/CURRENT_IMPLEMENTATION.md`，含参数化展开。

| 现系统 | 含用例的测试类 | `@Test` | `@ParameterizedTest` | 方法合计 | 最近记录运行数 | 核实状态 |
|---|---:|---:|---:|---:|---|---|
| client-contract-service | 12 | 53 | 0 | 53 | 53/53 | 源码已核实；与盘点一致 |
| commerce-service | 33 | 163 | 122 | 285 | 764/764（2026-10-15） | 源码已核实；与盘点一致 |
| payment-orchestration-service | 12 | 84 | 12 | 96 | 96/96 | 源码已核实；与盘点一致 |
| risk-compliance-service | 63（160 个测试源文件） | 562 | 176 | 738 | 盘点记 596，台账无全量记录 | 源码已核实；**盘点数字不可复现，以 738 为方法基线** |
| finance-service | 39 | 234 | 2 | 236 | 242/242（2026-10-17） | 源码已核实；**technique.md 的 80 为旧数** |

基础设施与基类（均来自各 `application-mock.yml` 与测试源码）：

| 现系统 | MySQL（127.0.0.1:13306） | Redis 16379 key-prefix | RabbitMQ /mor-local | MockHttpServer 承载的外部系统 | 基类 | Mocker / Checker / 清理 |
|---|---|---|---|---|---|---|
| CCT | `mor_cct` / `cct_app`（onboarding 与 checkout 共用） | `cct-test-${random.uuid}` | 消费 `topic_risk_client_review` → `cg_cct_client_review`（+ `dlx_cct_client_review` / `cg_cct_client_review_dlq`） | onboarding：`platform-support-service`、`risk-service`、`vault-service`（`MockPlatformAuthenticationApi`、`MockPlatformFileVerificationApi`、`MockPlatformTenantProjectionApi`、`MockRiskOnboardingApi`、`MockRiskReviewRegistrationApi`）；checkout：无 Mock，Risk 端口为生产 503 占位 | `OnboardingMockTestBase`（`OnboardingTestApplication`，profile `mock`）→ `AbstractSubmissionApiTest` / `AbstractClientReviewTest`；`AbstractCheckoutTest`（`ClientContractApplication`，profile `auto`+`checkout-auto`）→ `AbstractCheckoutApiTest` | `OnboardingApplicationMocker`、`OnboardingOwnerMocker`、`CheckoutResolutionFixture`（Clear）、`CheckoutFixture`、`OnboardingTestMapper`、`CheckoutTestMapper`、`CheckoutResolutionResponseChecker` |
| Commerce | `mor_commerce` / `commerce_app` | `commerce-test-${random.uuid}` | 消费 `topic_payment_result` → `cg_commerce_payment_result`（+ `dlx_commerce_payment_result` / `cg_commerce_payment_result_dlq`）；测试扮演 Payment 发布 | `payment-orchestration-service`、`client-contract-service`、`risk-service`、`platform-support-service`（`MockPaymentIntentApi`、`MockPaymentInstrumentApi`、`MockStoredCardApi`、`MockRiskDecisionApi`、`MockWebhookEventApi`）；另有 `@Primary` 端口替身 `CheckoutCommercialMocker`、`ConsumerProtectionMocker`、`CheckoutIntentMocker`、`PaymentTerminationMocker` | `MockTestBase`（`TestApplication` = `@Import(CommerceApplication)` + `@EnableMockHttpServer`）→ `AbstractCreateCheckoutApiTest` / `AbstractCustomerApiTest` / `AbstractGetOrderApiTest` / `AbstractPaymentResultTest` / `AbstractCreateRefundApiTest`；另有 `ManualTestBase` | 本地 Mocker 6 个（`CheckoutDataMocker` 含 Clear、`CheckoutPaymentDataMocker`、`OrderCancellationDataMocker`、`OrderQueryDataMocker`、`RefundCreationDataMocker`、`SaveConsumerDataMocker`）；外部 Mocker 10 个；TestMapper 9 个；Checker 16 个；探针 `CctTransactionProbe`、`PaymentTransactionProbe`、`AdjustableUtcClock` |
| Payment | `mor_payment_channel_test` / `payment_channel_test`（**不在 local-environment 初始化脚本中**，由测试 DDL 自建） | `payment-channel-test`（固定） | 发布 `topic_payment_result` / `payment.succeeded.v1`；`PaymentResultProbe` 声明临时队列 `payment_test_payment_result_<uuid>` | 一个 MockHttpServer 同时承载 `vault-service`、`platform-support-service`、`risk-service`（`MockVaultController`、`MockPlatformController`、`MockRiskController`）；`PlatformMocker` 收到含 card.token 的请求后经真实 HTTP 回调 Payment `/payment/payment-card/v1/channel-card-materials/reveal` | `MockTestBase`（`PaymentApplication` + `ChannelTestConfiguration`）→ `AbstractPayCardTest` | `ChannelDataMocker`（Clear）、`VaultMocker`、`PlatformMocker`、`RiskMocker`、`VaultTransactionObserver`、`ChannelTestMapper`、`ChannelRequestBuilder`、`PaymentSucceededMessageChecker`、`PaymentResultProbe` |
| Risk | `mor_risk` / `risk_app`；`RiskDatabasePrecondition` 固定 85 表 | `risk-mock`（固定） | 发布 `topic_risk_client_review` / `client_review.decided.v1`；`ClientReviewResultProbe` 临时队列 `risk_test_client_review_<uuid>` | 无（出站 HTTP 为零） | `MockTestBase`（`RiskApplication`，`@ContextConfiguration(initializers = RiskDatabasePrecondition)`，`@Import` 全部核心组件；**无全局 BeforeEach / AfterEach 检查**）→ `AbstractCheckoutRiskApiTest` / `AbstractPaymentRiskApiTest` / `AbstractRuntimeRiskApiTest` / `AbstractVariableApiTest` / `AbstractDecisionDictionaryApiTest` | Mocker 10 个（compliance 2、decision 6、evaluation 2，均实现 Clear）；TestMapper 11 个；Checker 46 个；`RiskRedisCleaner`；探针 `ClientReviewResultProbe`、`HttpLogProbe` |
| Finance | `mor_finance` / `finance_app` | `finance-mock`（固定） | 不用；`FinanceModuleBoundaryTest` 断言 `RabbitTemplate` 不在 classpath | 无 | `MockTestBase`（`FinanceApplication`）→ 8 个 `Abstract*Test`（ChannelCostApi、ChannelCostEstimation、ManualAdjustmentApi、PeriodCloseApi、PeriodReopenApi、RuleBundleApi、TransactionPosting、TrialBalanceApi） | `FinanceDataCleaner`、`FinanceRedisCleaner`、`LedgerMocker`、`LedgerFixture`、`TransactionEconomicsMocker`、`AccountingTestMapper`、`LedgerChecker`、`FinanceResponseChecker`、Builder 7 个 |

### 1.2 目标服务（搬迁后基线下限）

“方法下限”= 从现系统整类搬入的方法数 + 跨边界用例改写后的方法数，不得低于此值；“新增最少”为本方案要求的新测试，不计入基线但作为完成条件。表归属以第 3 节为准。

| 目标服务 | 来源 | 搬入类数 | 方法下限 | 本地基础设施 | MockHttpServer 承载 | 基类（新建） | Mocker / Checker（搬入或新建） |
|---|---|---:|---:|---|---|---|---|
| client-master | CCT | 10 | 37 | MySQL `mor_client_master`、Redis、RabbitMQ（消费 `ClientReviewDecided`） | platform-support-service、risk-service、vault-service | `ClientMasterMockTestBase` ← 现 `OnboardingMockTestBase` | 现 onboarding 全部 Mocker / Mock API / `OnboardingTestMapper` |
| commercial | CCT | 2 | 16 | MySQL `mor_commercial`、Redis | **新增** client-master-api DTO Mock（候选与根版本）；risk-service（资格端口，仍可返回 503） | `CommercialMockTestBase`（profile `mock`，替代 `auto`+`checkout-auto`） | `CheckoutResolutionFixture` 只种 commercial 12 表；新建 `ClientMasterCandidateMocker` |
| acquiring | Commerce | 31 | 278 | MySQL `mor_acquiring`、Redis（`order:{id}` TRY_LOCK）、RabbitMQ | payment、client-contract（→ commercial）、risk、platform，**新增 consumer-api** | `AcquiringMockTestBase` ← 现 `MockTestBase` | 现 Commerce 全部减去 Customer 相关；新建 `ConsumerMocker`（Customer 状态与存卡开关 DTO）、`MockConsumerApi` |
| consumer | Commerce | 2 | 7 | MySQL `mor_consumer`、RabbitMQ（补偿 `STORED_CARD_INVALIDATION`） | payment-orchestration-service（`MockStoredCardApi`） | `ConsumerMockTestBase` | `CustomerTestMapper`（仅 `t_customer`、`t_client_stored_card_setting`、本库 `t_compensation_event`）、`StoredCardMocker`、新建 `CustomerDataMocker`（Clear） |
| payment | Payment | 6 | 48 | MySQL `mor_payment`、Redis、RabbitMQ（发布 `PaymentSucceeded`） | vault、platform、risk，**新增 card-api 与 channel-connector-api** | `PaymentMockTestBase` → `AbstractPayCardTest` | `PlatformMocker`、`RiskMocker`、`VaultMocker`、`VaultTransactionObserver`、`PaymentResultProbe`、`PaymentSucceededMessageChecker`；新建 `CardApiMocker`、`ChannelConnectorMocker`、`PaymentTestMapper` |
| channel-connector | Payment | 4 | 41 | MySQL `mor_channel_connector`、Redis（若渠道配置缓存） | 无（Vault 账号 Reveal 仅在执行链） | `ChannelConnectorMockTestBase` | `ChannelDataMocker`、`ChannelRequestBuilder`、`ChannelTestMapper`（仅渠道 5 表） |
| card | Payment | 2 | 7 | MySQL `mor_card`、Redis（CVV 一次性领取键） | vault-service（DEK） | `CardMockTestBase` | `VaultMocker` 卡域子集、新建 `CardTestMapper`、`CardDataMocker`（Clear） |
| risk-decision | Risk | 55 | 641 | MySQL `mor_risk_decision`、Redis、RabbitMQ（补偿 `DECISION_SHADOW_RUN`） | 无 | `RiskDecisionMockTestBase` → 现 5 个 `Abstract*` | decision 6 + evaluation 2 Mocker、decision 6 + evaluation 3 TestMapper、`RiskRedisCleaner`、`HttpLogProbe` |
| compliance | Risk | 8 | 97 | MySQL `mor_compliance`、Redis、RabbitMQ（发布 `ClientReviewDecided`） | 无 | `ComplianceMockTestBase` | `ClientReviewMocker`、`MaterialProgramMocker`、`ClientReviewTestMapper`、`MaterialProgramTestMapper`、`ClientReviewResultProbe`、`RiskRedisCleaner` 副本 |
| billing | Finance | 5 | 49 | MySQL `mor_billing`、Redis | 无 | `BillingMockTestBase` → `AbstractChannelCostApiTest` / `AbstractChannelCostEstimationTest` | `TransactionEconomicsMocker`、`AccountingTestMapper` 的 economics / channel cost 部分、`EconomicComponentBuilder`、`ChannelCostEstimateCommandBuilder`、`ChannelCostVersionRequestBuilder`、`TransactionEconomicsBuilder` |
| account | Finance | 1 | 4 | MySQL `mor_account`、Redis、RabbitMQ（消费过账结果，S1） | **新增** ledger-api DTO Mock（若余额查询仍需账簿事实） | `AccountMockTestBase` | 新建 `BalanceAccountMocker`、`AccountTestMapper`、`BalanceChecker` |
| ledger | Finance | 31 | 173 | MySQL `mor_ledger`、Redis（资源锁） | **新增** reconciliation-api DTO Mock（渠道清算账户）、billing-api DTO Mock（经济事实，若过账改为同步拉取） | `LedgerMockTestBase` → `AbstractManualAdjustmentApiTest` / `AbstractPeriodCloseApiTest` / `AbstractPeriodReopenApiTest` / `AbstractRuleBundleApiTest` / `AbstractTrialBalanceApiTest` / `AbstractTransactionPostingTest` | `LedgerMocker`、`LedgerFixture`、`LedgerChecker`、`FinanceResponseChecker`、`AccountingSetupRequestBuilder`、`RuleVersionRequestBuilder`、`SubmitManualAdjustmentRequestBuilder` |
| reconciliation | Finance | 0 | 0 | MySQL `mor_reconciliation` | 无 | `ReconciliationMockTestBase` | 仅应用启动与架构测试 |
| tax | Finance | 0 | 0 | MySQL `mor_tax` | 无 | `TaxMockTestBase` | 仅应用启动与架构测试 |

服务级测试（`FinanceApplicationTest` 6、`FinanceModuleBoundaryTest` 4、`RiskModuleBoundaryAutoTest` 11、`WebLoggingBoundaryAutoTest` 3、`ChannelModuleBoundaryTest` 3）不按业务归属搬迁：每个目标服务各自新建等价类（见第 6.2 节），因此总数只增不减。上表 risk-decision 的 641 与 channel-connector 的 41 已含原服务级类的方法数，作为它们首个落点。

校验：client-master 37 + commercial 16 = 53；acquiring 278 + consumer 7 = 285；payment 48 + channel-connector 41 + card 7 = 96；risk-decision 641 + compliance 97 = 738；billing 49 + account 4 + ledger 173 + FinanceApplicationTest 6 + FinanceModuleBoundaryTest 4 = 236。

## 2. 拆分方案

### 2.1 CCT → client-master / commercial（第 4 步）

**整类搬入 client-master（10 类 37 例）：** `ClientSummariesTest`、`SubmitClientApplicationTest`、`OnboardingDuplicateWriteTest`、`ClientReviewRegistrationHandlerTest`、`ClientClassificationAdminTest`、`ClientApplicationAdminTest`、`ReapplyAfterRejectionTest`、`ResubmitAfterReturnTest`、`ClientReviewResultListenerTest`、`ClientClassificationHandlerTest`，连同 `OnboardingMockTestBase`、`AbstractSubmissionApiTest`、`AbstractClientReviewTest`、`OnboardingTestApplication`、`test/mock/external/onboarding/*`、`test/mocker/onboarding/*`、`OnboardingTestMapper`。`OnboardingTestMapper` 今天只触碰 master 表、BCR 两表和 `t_compensation_event`，无需改 SQL。

**跨边界改写（2 类 16 例）：** `ResolveCheckoutHttpTest`（13）、`ResolveCheckoutValidationTest`（3）今天经 `AbstractCheckoutTest` 启动整个 `ClientContractApplication`，`CheckoutResolutionFixture` 把 `fixtures/checkout-query.json` 的 23 张表一次性种入 `mor_cct`。S4 把解析改为两段式后：

- 两类成为 **commercial** 的 HTTP 入口测试，入口仍是 `POST /checkout-context/v1/resolutions`，Caller `commerce-service`（将来 `acquiring`）。
- `checkout-query.json` 拆成两份：`fixtures/commercial/checkout-query.json` 保留 12 张 commercial 表（`agreement`、`agreement_operating_entity`、`agreement_party`、`agreement_document_ref`、`commercial_term_plan`、`commercial_term_version`、`commercial_term_price`、`commercial_term_component`、`commercial_term_component_band`、`payment_method_profile`、`consumer_legal_document`、`seller_disclosure`）加 commercial 自己的 `business_change_record`；另外 10 张 client-master 表（`client`、`client_business_profile`、`client_business_classification`、`client_entity_affiliation`、`operating_entity`、`entity_source_relation`、`operating_entity_scope`、`operating_entity_application`、`local_partner`、`party_profile`）转成 client-master-api 的候选响应 DTO 样本 `fixtures/commercial/client-master-candidates.json`，由新建 `ClientMasterCandidateMocker` 经 MockHttpServer 返回（有序候选 + 四类根版本）。
- `CheckoutTestMapper` 今天 UPDATE `t_client`、`t_client_business_profile`、`t_client_business_classification`、`t_operating_entity_application`、`t_operating_entity_scope`、`t_party_profile` 制造负例，这些全是 client-master 表；改为对 `ClientMasterCandidateMocker` 配置对应 DTO 变体（候选为空、Scope 不匹配、根版本变化）。“回查版本不一致”成为新增用例。
- `ResolveCheckoutHttpTest` 中 `test_unconnectedRiskFailsClosedAndLeavesDatabaseUnchanged` 等 503 场景保留：Risk 端口仍是生产 503 占位，commercial 测试继续断言 `OWNER_UNAVAILABLE` 且本库只读；成功路径仍无覆盖，这是既有缺口而非搬迁引入。
- 两类的 profile 从 `auto`+`checkout-auto` 收敛为 `mock`，`application-auto.yml` 与 `application-checkout-auto.yml` 随 CCT 删除；commercial 的 `application-mock.yml` 列出 client-master-api 与 risk-service 的 simple discovery 实例。
- 10 张 client-master 表的解析查询（候选排序、Affiliation、Source、Scope）在 client-master 侧新增入口测试（`ResolveCandidatesHttpTest`，建议不少于 6 例：有序候选、无候选、Scope 不匹配、Source 失效、根版本返回、只读保证），计入“新增最少”。

### 2.2 Commerce → acquiring / consumer（第 3 步）

**整类搬入 consumer（2 类 7 例）：** `CustomerApiTest`（3 + 1）、`StoredCardApiTest`（3），基类 `AbstractCustomerApiTest`（经 `/client-gateway-api/v1/customers` 真实 HTTP 创建、读取、删除 Customer 与已存卡），外部 Mock `MockStoredCardApi` 与 `StoredCardMocker`（Payment 已存卡列表/删除）。`AbstractCustomerApiTest` 今天从 `checkoutDataMocker.clientId()` 取隔离 Client，改为 consumer 自己的 `CustomerDataMocker.clientId()`。

**留在 acquiring 但改写（2 类 9 例）：** `CreateCheckoutCustomerTest`（3）与 `StoredCardCheckoutTest`（6）今天直接用 `CustomerTestMapper` 插入或断言 `t_customer`。改为：Customer 的存在、状态、`customer_no`、存卡开关由新建 `ConsumerMocker` 以 consumer-api DTO 返回；`CustomerTestMapper` 不再出现在 acquiring；对“CreateCheckout 建或取 Customer”的断言改为 `ConsumerMocker.checker().calledOnce()` 式的调用记录断言。S6 的降级规则“资格读失败降级为不可存卡”作为新增用例进入 `StoredCardCheckoutTest`。

**整类搬入 acquiring（29 类，加上述 2 类共 31 类 278 例）：** 其余全部，包括 `SaveConsumerTest`、`SaveConsumerTransactionTest` 与 `SaveConsumerDataMocker`。注意命名陷阱：Commerce 的 “Consumer” 入口写的是 `t_consumer_context` / `t_consumer_contract`（按订单的结账消费者上下文），属于 checkout 模块，**不是** consumer 服务；不要按类名把它们搬到 consumer。

**清理与固件拆分：** `CheckoutDataMocker.clear()` 删除 `customerMapper.deleteTasks/deleteCustomers/deleteSettings` 三行，并去掉 `customerMapper.checkCustomerTables()` 与“上例 Customer 未清理”的基线断言；consumer 侧由 `CustomerDataMocker` 承担。`CustomerTestMapper.deleteTasks` 今天以 `CAST(customer_id AS CHAR)=event_key` 连接 `t_compensation_event` 与 `t_customer`，搬到 consumer 后连接的是 consumer 自己的 `t_compensation_event`，语义不变。`CustomerTestMapper` 中读 `t_commerce_order.customer_id` 的两条查询（`orderCustomerId`、`checkCustomerTables` 的一部分）删除。

### 2.3 Payment → payment / channel-connector / card（第 5 步，卡域逻辑重做在第 12 步）

**整类搬入 channel-connector（4 类 41 例）：** `QueryMidCandidatesTest`（11 + 1）、`GetChannelExecutionConfigTest`（12 + 5）只依赖 `MockTestBase`、`ChannelDataMocker`、`ChannelRequestBuilder`、`ChannelTestMapper`；`MidCapabilityResolutionTest`（9）为纯 Java；`ChannelModuleBoundaryTest`（3）改名 `ChannelConnectorArchitectureTest` 并按第 6.2 节扩展。

**整类搬入 card（2 类 7 例）：** `CardCipherTest`（5）、`PaymentCardPropertiesTest`（1 + 1），纯 Java，不需要数据库。

**跨边界改写为 payment 测试（6 类 48 例）：** `PayCardChannelTest`（7）、`PayRiskEvaluationTest`（5 + 4）、`PaymentSucceededPublishTest`（4）、`GetSubmissionTest`（7）、`CardStorageTest`（12 + 1）、`StoredCardTest`（8）全部继承 `AbstractPayCardTest`，经真实 HTTP 依次 `newIntent → applyCard → pay`，`PlatformMocker` 在 pay 期间回调 Payment 读卡接口。目标：

- 入口不变（`/payment-execution/v1/intents/{id}/pay` 等），基类 `AbstractPayCardTest` 随 payment 搬迁。
- `applyCard` 原本直接写 `t_payment_card*`，改为 card-api DTO Mock：新建 `CardApiMocker` 返回卡工具 Token、已存卡与一次性授权票据（S3）；`PlatformMocker.revealCard` 的回调目标从 Payment 的 `/payment/payment-card/v1/channel-card-materials/reveal` 改为 card 服务的读卡接口，由 MockHttpServer 以 `card` 服务名承载，并断言票据只能使用一次。
- 渠道候选与执行配置改为 channel-connector-api DTO Mock（`ChannelConnectorMocker`），`ChannelDataMocker.activeFixture()` 不再出现在 payment 测试；`PayActionActor` / `PayOutputs` 的渠道结果映射随 D06 迁入 channel-connector 后，payment 测试只断言映射结果 DTO。
- `CardStorageTest` 与 `StoredCardTest` 中直接断言卡表（`t_payment_card`、`t_payment_card_credential`、`t_payment_card_instrument`、`t_payment_stored_card`）的用例，一分为二：payment 侧改为断言对 card-api 的调用记录与 `t_payment_instrument_token.instrument_ref`；card 侧新增 `CardInstrumentHttpTest`、`StoredCardHttpTest`、`ChannelCardMaterialRevealTest`（建议合计不少于 20 例，覆盖申请卡工具、CVV 一次性领取、已存卡、票据一次性与失败不可重取），计入“新增最少”且满足第 12 步验收“新增授权一次性与失败不可重取测试”。
- 支付成功写卡表改为事件驱动后，`PaymentSucceededPublishTest` 增加“卡域事件已发布”断言，card 侧新增事件消费测试。
- `VaultTransactionObserver.assertOutsideTransactions` 保留在 payment 与 card 两侧各一份。

### 2.4 Risk → risk-decision / compliance（第 6 步）

Risk 今天没有任何同时涉及 compliance 与 decision 的测试，拆分是纯搬迁：

**整类搬入 compliance（8 类 97 例）：** `ClientReviewCaseDetailApiAutoTest`、`ClientReviewCaseListApiAutoTest`、`ClientReviewClassificationApiAutoTest`、`ClientReviewDecisionApiAutoTest`、`ClientReviewEvidenceReviewApiAutoTest`、`ClientReviewResultApiAutoTest`、`ClientReviewSubmissionApiAutoTest`、`MaterialRequirementsApiAutoTest`，连同 `builder/compliance`、`checker/compliance`、`mocker/compliance`、`mapper/compliance`、`ClientReviewResultProbe`。

**整类搬入 risk-decision（55 类 641 例）：** `ext/http/decision` 41 类、`ext/http/evaluation` 10 类、`ext/http/control/RiskListManagementApiAutoTest`（risk-control 并入 decision）、`ext/http/logging/WebLoggingBoundaryAutoTest`、`domain/DecisionFlowValidatorNullInputAutoTest`、`RiskModuleBoundaryAutoTest`（改名 `RiskDecisionArchitectureTest`，模块集合与依赖断言按新结构重写；compliance 另建 `ComplianceArchitectureTest`）。`ManualPayoutEligibilityApiAutoTest` 读 `t_decision_evaluation` + `t_settlement_risk_assessment`，两表都归 decision，随 decision 走；若 S2 最终把 `t_settlement_control` 三表判给 compliance，本类不受影响（它不读这三表）。

**基类拆分：** 现 `MockTestBase` 的 `@Import` 列表按包切开：decision 基类只导入 `mapper/decision`、`mapper/evaluation`、`mocker/decision`、`mocker/evaluation`、`checker/decision`、`checker/evaluation`、`RiskRedisCleaner`；compliance 基类只导入 compliance 三件套与 `ClientReviewResultProbe`。两个基类都补上现基类缺失的全局 `@BeforeEach` 空基线断言与 `@AfterEach` 异步停止 / 意外调用检查（规范第 12 节），`checkClearAllowed()` 在有补偿任务的两侧都要覆盖。

### 2.5 Finance → billing / account / ledger / reconciliation / tax（第 7 步，账户与会计逻辑重做在第 11 步）

**整类搬入 billing（5 类 49 例）：** `ChannelCostEstimationTest`（8）、`ChannelCostEstimationRejectionTest`（6）、`ChannelCostPlanApiTest`（5）、`ChannelCostVersionLifecycleTest`（15）、`CreateChannelCostVersionValidationTest`（15），基类 `AbstractChannelCostApiTest` / `AbstractChannelCostEstimationTest`。它们只用 `AccountingTestMapper` 的渠道成本与经济事实部分和 `t_currency_spec`；`t_currency_spec` 在 billing 库保留一份参考数据副本。

**整类搬入 ledger（26 类 156 例）：** `ManualAdjustmentContentHashTest`、`TrialBalanceContentHashTest`、`ApproveManualAdjustmentConcurrencyTest`、`ApproveManualAdjustmentTest`、`GetManualAdjustmentTest`、`RejectManualAdjustmentTest`、`SubmitManualAdjustmentTest`、`PeriodCloseQueryTest`、`ProjectionRebuildTest`、`ReviewPeriodCloseTest`、`StartPeriodCloseTest`、`YearEndProfitCloseTest`、`GetJournalEntryTest`、`ListAccountJournalLinesTest`、`ListAccountsTest`、`ListLedgerBooksTest`、`ReviewPeriodReopenTest`、`StartPeriodReopenTest`、`CreateRuleVersionValidationTest`、`RuleVersionLifecycleTest`、`CreateAccountingPeriodTest`、`CreateAccountTest`、`CreateFinanceEntityTest`、`CreateFiscalCalendarTest`、`CreateLedgerBookTest`、`OpenAccountingPeriodTest`、`RunTrialBalanceTest`、`TrialBalanceConcurrencyTest`、`TrialBalanceQueryTest`。`CreateAccountTest` 与 `ListAccountsTest` 操作的是过账科目账户（FIN-I05 账本查询），S1 拆表后对应 ledger 侧的“科目账户”表，留在 ledger。

**跨边界改写：**

- `TransactionPostingTest`（4）、`TransactionPostingRejectionTest`（13）：`AbstractTransactionPostingTest` 今天用 `TransactionEconomicsMocker` 写 billing 的 `t_transaction_economics*`、用 `AccountingTestMapper.insertChannelAccount` 写 reconciliation 的 `t_channel_account`，再调 `TransactionPostingServiceImpl#post` 同事务写 ledger 与 account。改为 **ledger** 测试：经济事实由 S1 定义的入口（事件消费或 billing 经 ledger-api 推送）注入，`TransactionEconomicsBuilder` 继续构造 DTO；渠道清算账户经 reconciliation-api DTO Mock 返回；断言只落在 ledger 表。余额侧的断言移到 account 的事件消费测试。方法下限 17 由 ledger 承担。
- `QueryOwnerBalanceTest`（4）：entityNo 余额查询归 **account**，成为 account 唯一搬入类；它今天依赖 `AbstractManualAdjustmentApiTest` 先过账再查余额，改为用 `BalanceAccountMocker` 直接写余额账户表，或消费一条过账结果事件后查询。
- 六处过账事务（过账内核、自动记账、人工调整审批、损益结转、重开冲销、投影重建）涉及的 `ApproveManualAdjustmentTest`、`YearEndProfitCloseTest`、`ReviewPeriodReopenTest`、`ProjectionRebuildTest`、`StartPeriodCloseTest`、`RunTrialBalanceTest` 中对 `t_account` 余额列（`debit_total`、`credit_total`、`net_balance`、`balance_version`）的断言，在第 11 步改为 ledger 侧断言分录与 `t_ledger_period_balance`，并在 account 新增“消费过账结果更新余额”“重复消费幂等”“差异核对”测试（建议不少于 12 例，满足第 11 步“新增一致性测试”）。
- `FinanceApplicationTest`（4 + 2）、`FinanceModuleBoundaryTest`（4）：五个 Finance 目标服务各建一份 `<Service>ApplicationTest` 与 `<Service>ArchitectureTest`。

**reconciliation 与 tax：** 今天没有任何测试（`t_channel_account` 只被读）。新模块只含应用启动测试与架构测试，基线为 0，但 DDL 与清理器必须按第 3、4 节到位，为后续切片准备。payout 7 表不建测试模块。

## 3. 清理拆分

原则（规范第 7.1、13 节）：每个目标服务的清理器触碰的表集合 **等于** 其测试 DDL 的表集合，不多不少；`t_compensation_event` 每库一张，按本服务 `event_type` 定向删除；跨库 DELETE、跨库 JOIN、跨库 EXISTS 一律禁止，由第 6.2 节的表白名单测试强制。所有 `t_command_idempotency` 按 S10 删除，不进入任何清理器或 DDL。

### 3.1 各目标服务清理器允许触碰的表

| 目标服务 | 清理器 | 允许触碰的表（= 本库表） | 备注 |
|---|---|---|---|
| client-master | `ClientMasterDataCleaner`（由 `OnboardingTestMapper` 的 DELETE 升级为 Clear 组件） | `t_party`、`t_party_profile`、`t_individual_profile`、`t_organization_profile`、`t_party_identity_key`、`t_party_profile_identifier`、`t_party_address`、`t_party_contact`、`t_party_relation`、`t_client`、`t_local_partner`、`t_operating_entity`、`t_client_business_profile`、`t_client_business_classification`、`t_client_application`、`t_partner_application`、`t_operating_entity_application`、`t_operating_entity_scope`、`t_application_document`、`t_client_entity_affiliation`、`t_entity_source_relation`、`t_business_change_record`、`t_business_change_version`、`t_compensation_event`（24） | 现 `OnboardingTestMapper` 只 DELETE 10 张表，其余靠“空基线断言”兜底；新清理器要覆盖全部 24 张 |
| commercial | `CommercialDataCleaner` + `CheckoutResolutionFixture`（逆序删本例插入行） | `t_agreement`、`t_agreement_party`、`t_agreement_document_ref`、`t_agreement_operating_entity`、`t_payment_method_profile`、`t_payment_method_profile_cond`、`t_commercial_term_plan`、`t_commercial_term_version`、`t_commercial_term_price`、`t_commercial_term_component`、`t_commercial_term_component_band`、`t_consumer_legal_document`、`t_seller_disclosure`、`t_business_change_record`、`t_business_change_version`、`t_compensation_event`（16） | S5 两表按“倾向 commercial”列入，若改判则移到 client-master；`CheckoutResolutionFixture` 对 `t_operating_entity_scope` 的 UPDATE 删除 |
| acquiring | `CheckoutDataMocker.clear()`（去掉 Customer 三行） + `RefundCreationTestMapper`、`ClientWebhookTestMapper` | `t_commerce_order`、`t_order_item`、`t_order_context`、`t_order_legal`、`t_order_payment_projection`、`t_order_funds_availability_projection`、`t_order_refund_position`、`t_checkout_session`、`t_consumer_context`、`t_consumer_contract`、`t_checkout_network_evidence`、`t_consumer_acceptance`、`t_refund_request`、`t_compensation_event`（14） | 四模块共库（D31），一个清理器；`SaveConsumerTestMapper` 的 `DELETE FROM t_commerce_order` 保留 |
| consumer | `CustomerDataMocker`（新建，Clear） + `CustomerTestMapper` | `t_customer`、`t_client_stored_card_setting`、`t_compensation_event`（3） | `deleteTasks` 只删 `event_type='STORED_CARD_INVALIDATION'` 且 `event_key` 属于本例 Customer |
| payment | `PaymentDataMocker`（新建，Clear） + `PaymentTestMapper` | `t_payment_intent`、`t_payment_attempt`、`t_payment_instrument_token`、`t_payment_action`、`t_channel_transaction_ref`、`t_duplicate_payment_case`、`t_payment_refund_position`、`t_channel_refund`、`t_routing_decision`、`t_route_candidate_eval`、`t_compensation_event`（11） | `t_channel_transaction_ref` 按盘点“payment 写入但有争议”暂列 payment，S7 改判则移动；`clearSecurityCodes()` 的 Redis 键随卡域走 |
| channel-connector | `ChannelDataMocker.clear()`（只保留 `clearDetails/clearCapabilities/clearMids/clearAccounts/clearChannels`） + `ChannelTestMapper` | `t_payment_channel`、`t_channel_account`、`t_channel_mid`、`t_mid_capability`、`t_mid_capability_detail`、`t_compensation_event`（6）；S7 若判入：`t_routing_policy`、`t_routing_policy_version`、`t_routing_policy_mid`、`t_channel_callback`、`t_channel_callback_item`、`t_channel_event_exception`、`t_channel_interaction` | `ChannelTestMapper` 现含 payment 与 card 全部 15 表的 DELETE，删去 10 张非本库表的方法 |
| card | `CardDataMocker`（新建，Clear） + `CardTestMapper` | `t_payment_card`、`t_payment_card_credential`、`t_payment_card_instrument`、`t_payment_stored_card`、`t_compensation_event`（5）+ S3 新增的授权票据表与卡域事件表 | Redis CVV 键 `CardSecurityCodeClientImpl.key(...)` 的清理移到这里 |
| risk-decision | decision 6 + evaluation 2 个 Mocker 的 `clear()` + `RiskRedisCleaner` | 规则配置 31 表（`t_rule_reject_reason` … `t_risk_variable_member`）、评估与运行期 22 表（`t_decision_evaluation` … `t_limit_movement`）、dispute 7 表（仅 Schema，随 decision 库）、`t_compensation_event`（61；S2 判入时加 `t_settlement_control`、`t_settlement_control_scope`、`t_settle_control_state_change` 为 64） | `t_compensation_event` 只删 `DECISION_SHADOW_RUN`；`t_action_version`、`t_action_parameter_definition` 的目录行是保留数据，清理器不得触碰，前置检查校验行数 |
| compliance | `ClientReviewMocker`、`MaterialProgramMocker` 的 `clear()` + `RiskRedisCleaner` 副本 | `t_dd_program`、`t_dd_program_version`、`t_dd_program_requirement`、`t_dd_program_check`、`t_dd_program_screening`、`t_due_diligence_case`、`t_dd_case_subject`、`t_dd_case_classification`、`t_dd_case_dependency`、`t_dd_requirement`、`t_dd_evidence_ref`、`t_dd_case_state_change`、`t_dd_party_snapshot`、`t_dd_check`、`t_screening_run`、`t_screening_hit`、`t_screening_resolution`、`t_qualification_decision`、`t_role_qualification`、`t_qualification_state_change`、`t_compensation_event`（21） | `t_compensation_event` 只删 `CLIENT_REVIEW_RESULT` + case id |
| billing | `BillingDataCleaner` | `t_transaction_economics`、`t_transaction_economic_component`、`t_transaction_economic_comp_link`、`t_channel_cost_plan`、`t_channel_cost_plan_ver`、`t_channel_cost_scope`、`t_channel_cost_component`、`t_compensation_event`（8）；`t_currency_spec` 为保留表，只 `deleteNonReserved` | 从 `FinanceDataCleaner.FULLY_OWNED_TABLES` 摘出前 4 项与 economics 2 项 |
| account | `AccountDataCleaner` | S1 新建的余额账户表、`t_compensation_event`；若 S1 判定 `t_cash_account`、`t_cash_account_binding`、`t_cash_account_binding_ver` 归 account 则加入 | 第 11 步落地前该库为空壳 |
| ledger | `LedgerDataCleaner`（由 `FinanceDataCleaner` 收缩） | `t_chart_of_accounts`、`t_chart_account`、`t_ledger_book`、`t_book_sequence`、`t_fiscal_calendar`、`t_accounting_period`、`t_accounting_rule_bundle`、`t_accounting_rule_bundle_ver`、`t_accounting_rule_line`、`t_accounting_instruction`、`t_instruction_line`、`t_journal_entry`、`t_journal_line`、`t_manual_adjustment`、`t_manual_adjustment_line`、`t_adjustment_approval`、`t_ledger_period_balance`、`t_trial_balance_run`、`t_trial_balance_line`、`t_trial_balance_exception`、`t_period_close`、`t_period_close_book`、`t_close_step`、`t_close_approval`、`t_close_account_rule`、`t_finance_entity`、`t_currency_spec`（保留表）、S1 的科目账户表、`t_compensation_event`（30） | 保留表 `t_accounting_rule_*`、`t_chart_*`、`t_currency_spec` 继续 `deleteNonReserved` + `restoreInitialRuleBundle/Version`；`assertEmpty` 的 45 行保留基数按拆后库重算 |
| reconciliation | `ReconciliationDataCleaner` | `t_channel_account`、`t_channel_open_item`、`t_channel_debit_item`、`t_duplicate_capture_item`、`t_channel_settle_batch`、`t_channel_settle_item`、`t_channel_transaction_match`、`t_channel_cost_confirm`、`t_channel_cost_alloc`、`t_bank_statement`、`t_bank_statement_item`、`t_bank_recon_match`、`t_recon_exception`、`t_recon_resolution`、`t_compensation_event`（15）+ S1 未判给 account 时的 3 张 cash account 表 | 现无测试写入，清理器只需覆盖空基线断言 |
| tax | `TaxDataCleaner` | 23 张 `t_tax_*` / `t_place_of_supply_rule` / `t_fiscal_number_sequence` / `t_fiscal_record` / `t_fiscal_record_line` + `t_compensation_event`（24） | 同上 |

### 3.2 必须移除的跨 core 清理

| 现清理器 | 现状 | 处置 |
|---|---|---|
| `finance/test/clear/FinanceDataCleaner` | `FULLY_OWNED_TABLES` 28 表 + `RESERVED_TABLES` 6 表，一次清 ledger / account / billing / reconciliation | 拆为 `LedgerDataCleaner`（含保留表逻辑）、`BillingDataCleaner`（`t_currency_spec` 保留）、`AccountDataCleaner`、`ReconciliationDataCleaner`；`t_channel_account` 只留在 reconciliation；`t_account` 随 S1 拆表分别进入 ledger 与 account |
| `payment/test/dal/ChannelTestMapper` + `ChannelDataMocker.clear()` | 15 个 `clear*` 方法清全部表；`assertEmpty` 以 `countRows()+countExecutionRows()` 断言整库为空 | `ChannelTestMapper` 只留渠道 5 表；新建 `PaymentTestMapper`、`CardTestMapper`；三个 `assertEmpty` 各断言自己的库名（`mor_payment`、`mor_channel_connector`、`mor_card`）与本库行数为 0 |
| `commerce/test/mocker/local/CheckoutDataMocker.clear()` | 一个 `TransactionTemplate` 内删订单 16 步后再删 Customer 三步 | 删除 `customerMapper.*` 三行与 `checkCustomerTables()`；consumer 新建 `CustomerDataMocker` 实现 Clear |
| `client/contract/test/mocker/local/checkout/CheckoutResolutionFixture` + `CheckoutFixture` | 种 23 表（10 master + 12 commercial + BCR），`assertReadOnly` 比对 `t_compensation_event` ID 集 | 固件只种 commercial 13 表（含本库 BCR）；master 10 表转 DTO 样本；`compensationEventIds` 只读 commercial 库 |
| `risk/test/base/MockTestBase` 的 `@Import` | 导入 compliance、decision、evaluation、settlement 全部 Mocker / TestMapper / Checker，共享一个应用上下文 | 两个基类各导入本服务组件；`SettlementRiskAssessmentTestMapper` 归 decision |
| `commerce/test/dal/CustomerTestMapper` | `deleteTasks` 跨 `t_compensation_event ⋈ t_customer`；`checkCustomerTables` 读 `t_commerce_order.customer_id` | 前者在 consumer 库内合法保留；后者删除 |

## 4. 测试 DDL 与前置检查

### 4.1 现状缺口

| 现系统 | `init/ddl.sql` | 覆盖 | 缺口 |
|---|---|---|---|
| Finance | `paytech-finance-test/src/test/resources/init/ddl.sql` | 26/84 | 缺 `t_ledger_period_balance`、`t_trial_balance_run/line/exception`、`t_period_close`、`t_period_close_book`、`t_close_step`、`t_close_approval`、`t_close_account_rule`、`t_compensation_event`，而 `FinanceDataCleaner` 与关账逻辑都读写它们；`GRANT ALL PRIVILEGES` 不符合最小权限 |
| Payment | `paytech-payment-test/src/test/resources/init/ddl.sql` | 15/31 | 只含有代码的表；自建 `mor_payment_channel_test` / `payment_channel_test`，与 `local-environment` 初始化脚本创建的 `mor_payment` / `payment_app` 并存 |
| Commerce | `paytech-commerce-test/src/test/resources/init/ddl.sql` | 16/17 | 只缺 `t_command_idempotency`，该表按 S10 删除，不补 |
| CCT | 无 | 0/38 | 测试依赖人工应用完整 `client-contract-schema.sql`；`CheckoutFixture.install` 失败时才提示“请先人工应用 CCT Schema” |
| Risk | 无 | 0/85 | 依赖 `RiskDatabasePrecondition` 的 85 表计数与一长串增量 SQL 路径提示 |

### 4.2 每个新测试模块的 DDL 要求

每个 `<service>-test/src/test/resources/init/ddl.sql` 必须包含且仅包含：

1. `CREATE DATABASE IF NOT EXISTS mor_<service>` 与 `CREATE USER IF NOT EXISTS '<service>_app'@'%'`，`GRANT SELECT, INSERT, UPDATE, DELETE ON mor_<service>.*`（对齐 Payment、Commerce 的最小权限写法，不再 `GRANT ALL`）。密码只用仓库隔离本地默认值，与 `application-mock.yml` 的 `${MOR_<SERVICE>_DB_PASSWORD:...}` 一致。
2. 第 3.1 节该服务允许触碰的全部表的完整 `CREATE TABLE`（字段、约束、索引、注释与正式 Schema 一致），包括 `t_compensation_event` 与仅 Schema 的表（dispute、tax、reconciliation 等）。表集合与清理器集合相等，由第 6.2 节测试强制。
3. 保留表的初始化行（ledger 的规则束与科目表、billing 与 ledger 的 `t_currency_spec`、risk-decision 的 `t_action_version` / `t_action_parameter_definition` 目录），与各服务 `*-initial-data.sql` 的对应片段一致。

来源映射：ledger / account / billing / reconciliation / tax 从 `finance-schema.sql` 按归属切片；payment / channel-connector / card 从 `payment-orchestration-schema.sql` 切片；acquiring / consumer 从 `commerce-schema.sql` 切片；client-master / commercial 从 `client-contract-schema.sql` 切片；risk-decision / compliance 从 `risk-compliance-schema.sql` 切片。切片是“表归属”变化而非结构变化（requirement.md 2.3），每张表的 DDL 文本逐字复制，仅 S1、S3 新增表另行归档授权。

### 4.3 数据库前置检查的变化

| 现检查 | 现状 | 变化 |
|---|---|---|
| `risk/test/infrastructure/RiskDatabasePrecondition` | `EXPECTED_TABLE_COUNT = 85`；`validateConnectionTarget` 只允许 `127.0.0.1:13306/mor_risk` + `risk_app`；校验 `t_rule_set` 列、`t_rule_reject_reason` / `t_rule_subject_tag` 21 列、两个索引、`t_command_idempotency.idempotency_key`、`REPEATABLE-READ`、`t_action_version` 行数、Redis AUTH/PING | 拆为 `RiskDecisionDatabasePrecondition`（`mor_risk_decision` / `risk_decision_app`，表数 = decision DDL 表数 61 或 64，保留 `t_rule_set` 列、索引、`t_action_version` 行数校验）与 `ComplianceDatabasePrecondition`（`mor_compliance` / `compliance_app`，表数 21）；删除 `t_command_idempotency` 列校验；`RiskModuleBoundaryAutoTest.test_mockDatabaseTargetIsFixedToOwnedLoopbackSchema` 的 URL 与用户名断言按两服务重写。长串增量 SQL 路径提示改为指向各服务 `init/ddl.sql` |
| Payment `ChannelDataMocker.assertEmpty` | 断言库名 `mor_payment_channel_test` 且 `countRows()+countExecutionRows()==0` | 三份：`PaymentDataMocker`、`ChannelDataMocker`、`CardDataMocker` 各断言自己的库名与行数 |
| Finance `FinanceDataCleaner.assertEmpty` | 45 行保留数据基数 | ledger 与 billing 各自重算保留行基数；account / reconciliation / tax 断言全空 |
| Commerce `CheckoutDataMocker` 基线 | 检查两个测试 Client 的 Customer 为空 | 移到 consumer 的 `CustomerDataMocker`；acquiring 只检查订单 |
| CCT `OnboardingMockTestBase` 的 `applications.assertEmpty()` 与 `AbstractCheckoutTest` 的 `fixture.assertEmpty()` | 同库两套 | client-master 保留前者；commercial 的 `fixture.assertEmpty()` 改查 commercial 库 |
| Platform `platform-support-test/.../LocalMockEnvironmentInitializer` | 以 `local-environment/.env.example` + `platform-support-service/pom.xml` 识别仓库根（第 89–91 行），加载 `mor.local-environment` 凭据 | 改为读取系统属性 `paytech.local-environment.dir`（见 4.4），找不到时报错并提示 `local-environment` 位置；不再按目录名向上搜索 |
| Payment `application-mock.yml` 的 `spring.config.import` | `optional:file:../../local-environment/.env.local` 与 `../local-environment/.env.local` 两条相对路径 | 改为 `optional:file:${paytech.local-environment.dir}/.env.local[.properties]`；其余服务若需读 `.env.local` 一律用同一键 |

### 4.4 统一的本地环境锚点

`paytech/pom.xml`（父 POM）定义属性 `paytech.local-environment.dir=${maven.multiModuleProjectDirectory}/../local-environment`，并在 Surefire `systemPropertyVariables` 中透传；IDE 运行时允许以 `-Dpaytech.local-environment.dir=` 覆盖，缺省回退为相对 `paytech/` 的 `../local-environment`。`local-environment/` 保持在仓库顶层（第 9 步在其中增加 Doris / Flink），不进入 `paytech/`。所有测试对 `local-environment` 的引用（凭据导入、报错提示、`RedisLocalEnvironmentPrecondition` 的 `../local-environment` 文案）统一改为该属性，消除 retained-systems.md 第 8 节风险 3、4。

## 5. 本地环境

### 5.1 新增 MySQL 数据库与账号

命名延续 `mor_<service>` / `<service>_app`（`local-environment/mysql/init/01-create-databases-and-users.sh` 现行写法，服务名中的连字符改下划线）。每库一个账号、只授本库权限；初始化脚本的 `REQUIRED_PASSWORD_VARIABLES`、`compose.yaml` 的 `mysql.environment`、`.env.example` 三处同步增加 `MOR_<SERVICE>_DB_PASSWORD`。

| 目标服务 | 数据库 | 账号 | 环境变量 | 替代的旧库 |
|---|---|---|---|---|
| client-master | `mor_client_master` | `client_master_app` | `MOR_CLIENT_MASTER_DB_PASSWORD` | `mor_cct`（第 4 步完成后删除） |
| commercial | `mor_commercial` | `commercial_app` | `MOR_COMMERCIAL_DB_PASSWORD` | 同上 |
| acquiring | `mor_acquiring` | `acquiring_app` | `MOR_ACQUIRING_DB_PASSWORD` | `mor_commerce`（第 3 步后删除） |
| consumer | `mor_consumer` | `consumer_app` | `MOR_CONSUMER_DB_PASSWORD` | 同上 |
| payment | `mor_payment`（已存在） | `payment_app`（已存在） | `MOR_PAYMENT_DB_PASSWORD`（已存在） | 测试专用 `mor_payment_channel_test` / `payment_channel_test` 退役；payment 测试改连 `mor_payment` |
| channel-connector | `mor_channel_connector` | `channel_connector_app` | `MOR_CHANNEL_CONNECTOR_DB_PASSWORD` | — |
| card | `mor_card` | `card_app` | `MOR_CARD_DB_PASSWORD` | — |
| risk-decision | `mor_risk_decision` | `risk_decision_app` | `MOR_RISK_DECISION_DB_PASSWORD` | `mor_risk`（第 6 步后删除） |
| compliance | `mor_compliance` | `compliance_app` | `MOR_COMPLIANCE_DB_PASSWORD` | 同上 |
| billing | `mor_billing` | `billing_app` | `MOR_BILLING_DB_PASSWORD` | `mor_finance`（第 7 步后删除） |
| account | `mor_account` | `account_app` | `MOR_ACCOUNT_DB_PASSWORD` | 同上 |
| ledger | `mor_ledger` | `ledger_app` | `MOR_LEDGER_DB_PASSWORD` | 同上 |
| reconciliation | `mor_reconciliation` | `reconciliation_app` | `MOR_RECONCILIATION_DB_PASSWORD` | 同上 |
| tax | `mor_tax` | `tax_app` | `MOR_TAX_DB_PASSWORD` | 同上 |

保留不变：`mor_platform`、`mor_vault`、`nacos_config`、`xxl_job`、`paytech_foundation_test`。旧库在对应步骤合入 `refactor-main` 并通过门禁后，从初始化脚本删除；`refactor-main` 与 `main` 并行期间开发者本机两套库共存，`01-create-databases-and-users.sh` 只追加不删除，以免破坏 `main` 会话。

`compose.yaml` 的 `--max-connections=200` 提升到 `400`（technique.md 6a：25 个 JVM 加测试并行），`mysql.mem_limit` 相应从 1536m 评估上调。

### 5.2 Redis

共用 `127.0.0.1:16379` 单实例，不分 database 编号，靠 key-prefix 隔离并由各服务的 `RedisCleaner` 按 SCAN 删除。统一格式 `<service>-test-${random.uuid}`（现 CCT、Commerce 写法，随机后缀允许多个 JVM 并行而互不清理）：`client-master-test-`、`commercial-test-`、`acquiring-test-`、`consumer-test-`、`payment-test-`、`channel-connector-test-`、`card-test-`、`risk-decision-test-`、`compliance-test-`、`billing-test-`、`account-test-`、`ledger-test-`、`reconciliation-test-`、`tax-test-`。现固定前缀 `payment-channel-test`、`risk-mock`、`finance-mock` 弃用。`compensation.instance-id` 同步为 `<service>-test`。

### 5.3 RabbitMQ

继续单 vhost `/mor-local`（跨服务事件需同一 vhost），第 8 步 D34 的一致性哈希交换机另行定义拓扑；本节只约束测试侧命名，保证各服务清理只碰自己的队列：

- 生产交换机 `topic_<producer>_<fact>`（现 `topic_payment_result`、`topic_risk_client_review`）；消费者队列 `cg_<consumer>_<fact>`，死信 `dlx_<consumer>_<fact>` / `cg_<consumer>_<fact>_dlq`（现 `cg_commerce_payment_result*`、`cg_cct_client_review*`）。搬迁后消费者名随服务名变：`cg_acquiring_payment_result`、`cg_client_master_client_review`；发布方 compliance 的交换机名 `topic_risk_client_review` 是跨服务合同，保留原名直到第 8 步统一。
- 测试探针临时队列 `<service>_test_<fact>_<uuid>`（现 `payment_test_payment_result_<uuid>`、`risk_test_client_review_<uuid>`），非持久、独占、过期，由探针 `clear()` 删除。
- 消费者测试只 `purgeQueue` 自己的 `cg_*` 与 `*_dlq`（现 CCT、Commerce 做法），不得清其他服务队列。
- `.env.example` 的 `MOR_RABBITMQ_USER` / `MOR_RABBITMQ_PASSWORD` / `MOR_RABBITMQ_VHOST` 不变。

### 5.4 `compose.yaml` / `.env` 需要的条目

- `.env.example`：新增 14 个 `MOR_<SERVICE>_DB_PASSWORD`（payment 复用现有）。
- `compose.yaml` `mysql.environment`：透传同一组变量；`command` 中 `--max-connections=400`。
- `mysql/init/01-create-databases-and-users.sh`：`REQUIRED_PASSWORD_VARIABLES` 与 `CREATE DATABASE / CREATE USER / GRANT` 各追加 14 组。
- `local-environment/README.md` 第 163–169 行的库与账号清单同步。
- 第 9 步新增 Doris / Flink 容器属 D35，不在本文件范围。

### 5.5 从不执行的三个测试类

`paytech-parent` 的 `surefire.includes=**/auto/**/*Test.java` 使下列三类从未进入默认执行，属既有问题，在第 1、2 步搬迁时移入 `auto/` 并计入目标基线（合计 8 个方法：`@Test` 7、`@ParameterizedTest` 1）：

- `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/configuration/PlatformSnapshotControlClientAutoTest.java`（3 + 1）
- `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCodeParserAutoTest.java`（3）
- `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/infrastructure/RedisLocalEnvironmentPreconditionAutoTest.java`（1）

移入后若失败，按“既有缺陷”单独报告，不计入搬迁门禁的回归。

## 6. 门禁定义

### 6.1 “每步测试数不少于基线”

**计数口径**：以 Surefire 报告为准，不以源码注解为准。对某个测试模块，取 `target/surefire-reports/TEST-*.xml` 中全部 `<testcase>` 元素数，减去含 `<skipped>` 的元素；参数化用例按展开后的每个 `<testcase>` 计 1。源码方法数（`@Test` + `@ParameterizedTest`）作为第二道核对，用于解释差异，不能替代执行数。

```bash
# 执行数：在 paytech/ 下对某个 test 模块运行后统计
find <module>/target/surefire-reports -name 'TEST-*.xml' -print0 \
  | xargs -0 grep -h -o '<testcase ' | wc -l
find <module>/target/surefire-reports -name 'TEST-*.xml' -print0 \
  | xargs -0 grep -h -o '<skipped' | wc -l
# 源码方法数
grep -rE '^\s*@(Test|ParameterizedTest)\b' <module>/src/test/java --include='*.java' | wc -l
```

**基线取值**：每步开始前，在 `refactor-main` 对该步来源工程运行一次全量 `auto`，记录执行数 B（实际运行，不引用台账旧数）；同时记录源码方法数 M。第 1.1 节的 M 值为本方案核实值：CCT 53、Commerce 285、Payment 96、Risk 738、Finance 236。

**判定**：

1. 该步全部目标测试模块的执行数之和 ≥ B，且源码方法数之和 ≥ M。
2. 每个目标模块的执行数 ≥ 第 1.2 节“方法下限”对应的展开数（参数化展开按原类的展开倍数换算）。
3. 整类搬入的测试，类名、方法名、方法数不变；允许的改动只有包名、基类名、Mocker / Mapper 注入名。
4. 跨边界改写的测试，须在该步交付记录中给出“旧类 → 新类（服务）/ 旧方法数 → 新方法数”映射表（第 2 节各小节的类清单即模板），新方法数不少于旧方法数；缺失的场景（如 CCT 成功路径）按既有缺口单列。
5. 不允许 `@Disabled`、`@Tag` 排除、Surefire 排除或把类移出 `auto/` 来凑数；`<skipped>` 计数必须为 0。
6. 服务级测试（Application、Architecture）每个目标服务各一份，只增不减。
7. 第 5.5 节三类归位后的新增数不计入 B 的分母，但作为第 1、2 步的交付项。

### 6.2 每个测试模块必须包含的 ArchUnit 测试

今天仓库没有 ArchUnit；现有三份边界测试是手写 XML / 正则扫描。阶段 3 需在 `paytech` BOM 新增 `com.tngtech.archunit:archunit-junit5`（test scope，版本在 TECHNOLOGY_STACK 修订中登记），每个 `<service>-test` 模块的 `auto/architecture/<Service>ArchitectureTest` 至少包含下列规则；手写的 POM / XML 扫描作为补充保留，不被 ArchUnit 替代。

| 编号 | 规则 | 实现要点 |
|---|---|---|
| A1 | core 不得 import 其他 core 的内部包 | `noClasses().that().resideInAPackage("com.paytech.<svc>..").should().dependOnClassesThat().resideInAnyPackage(<其他 14 个服务的根包>)`，白名单只放 `..api..` 子包；验收标准 2 要求人为引入一次跨 core import 必须失败，每个模块保留一个 `@Disabled` 以外的负例演示方式（在交付记录中以临时改动证明，不提交） |
| A2 | core 不依赖 app | `noClasses().that().resideInAPackage("com.paytech.<svc>.core..").should().dependOnClassesThat().resideInAPackage("com.paytech.<svc>.app..")` |
| A3 | 领域层 core 不依赖流程层 api（D08） | 对 client-master、commercial、consumer、card、channel-connector、risk-decision、compliance、billing、account、ledger、reconciliation、tax：禁止依赖 `com.paytech.acquiring.api..`、`com.paytech.payment.api..` 及将来 payout / dispute 的 api 包 |
| A4 | DataObject 不跨服务 | `..dal.dataobject..` 中的类只能被本服务包访问；`*-api` 模块不得依赖 `..dal..` |
| A5 | Service 形态 | `..service..` 下接口必须有唯一 `*Impl`；Processor、Controller 为具体类（DEVELOPMENT_CONVENTIONS 现行规则） |
| A6 | Mapper XML 表名归属 | 手写：扫描本服务 `src/main/resources/**/*Mapper.xml` 与 `@Select/@Insert/@Update/@Delete` 注解中的 `t_[a-z_]+`，集合 ⊆ 本服务 `init/ddl.sql` 的 `CREATE TABLE` 集合（扩展 `ChannelModuleBoundaryTest.keepsChannelTablesInsideOwnerModule` 的正则） |
| A7 | 清理器与 TestMapper 表白名单 | 手写：扫描本测试模块 `test/dal/**`、`test/clear/**`、`test/mocker/local/**` 中的 `t_[a-z_]+` 与 `FULLY_OWNED_TABLES` 等常量，集合 **等于** `init/ddl.sql` 的表集合（第 3 节“不多不少”） |
| A8 | 测试基类与禁止替身 | ArchUnit：`auto/ext/**` 下的类必须 `assignableTo(<Service>MockTestBase)`；classpath 不含 `org.mockito.Mockito`、`org.springframework.boot.test.mock.mockito.MockBean`、`org.springframework.test.context.bean.override.mockito.MockitoBean`（延续 `FinanceModuleBoundaryTest.test_runtimeContainsOnlyApprovedAccountingInfrastructure`）；无 `@Disabled`、`@TestMethodOrder`、`@TestInstance(PER_CLASS)` |
| A9 | 数据源固定为本库 | 手写：`application-mock.yml` 的 `spring.datasource.url` 等于 `jdbc:mysql://127.0.0.1:13306/mor_<svc>?...`、`username` 等于 `<svc>_app`、`spring.sql.init.mode=never`（延续 `RiskModuleBoundaryAutoTest.test_mockDatabaseTargetIsFixedToOwnedLoopbackSchema`） |
| A10 | 运行时基础设施白名单 | 手写：不用 MQ 的服务（billing、ledger、reconciliation、tax、channel-connector、commercial）断言 `RabbitTemplate` 不在 classpath；所有服务断言 Nacos 客户端不在 test classpath（延续 Finance、Risk 的做法） |

模块映射（谁必须拥有哪一份）：15 个目标服务各一份 `<Service>ArchitectureTest`；acquiring 的四个模块共一份并额外加 A1 的模块内变体（transaction / checkout / command / client-api 之间只允许经公开包依赖，按 D31 共库但分包）；payment-app 若按 Q3 暂含 card 模块，A1 仍以 `com.paytech.card..` 与 `com.paytech.payment..` 为两个独立根包执行。

### 6.3 每步交付时的核对清单

- [ ] 第 6.1 节 B / M 与各目标模块执行数、方法数表，附 Surefire 报告路径。
- [ ] 第 2 节对应小节的类映射表，逐类核对方法数。
- [ ] 第 3.1 节该步涉及服务的清理器表清单 = `init/ddl.sql` 表清单（A7 通过）。
- [ ] 第 4.3 节对应前置检查已改写，旧库名、旧账号、旧表数不再出现在测试源码。
- [ ] 第 5.1 节新库已加入 `local-environment` 三处文件并在本机实际创建；旧库保留到步骤合入后。
- [ ] 第 6.2 节 A1–A10 全部通过，并在交付记录中附一次 A1 负例失败的输出。
- [ ] 未运行的测试、既有缺口（CCT 成功路径、Risk 596 不可复现、Finance 80 旧数）单列，不与搬迁回归混报。

## 7. 待决项对本方案的影响

| 待决 | 影响位置 |
|---|---|
| S1 `t_account` 拆表与过账最终一致（已定 C1：账户只消费 `EconomicFactRecorded`，第 11a/11b 步） | 第 1.2 节 account / ledger 基线、第 2.5 节 17 + 4 例改写与新增一致性测试、第 3.1 节 ledger / account 表清单、cash account 三表归属 |
| S2 `t_settlement_control` 三表归属（已定 C3：compliance） | compliance 的 DDL 与清理器加 3 表；`ManualPayoutEligibilityApiAutoTest` 不受影响 |
| S3 卡域一次性授权票据 | 第 2.3 节 `PlatformMocker.revealCard` 回调目标、card 新增表与新增测试 |
| S4 Checkout 两段式 | 第 2.1 节 DTO 样本结构（候选 + 四类根版本）与 client-master 新增入口测试 |
| S5 `t_consumer_legal_document` / `t_seller_disclosure`（已定 C4：client-master） | client-master 的 DDL、固件、清理器加 2 表，commercial 减 2 表；`t_client_stored_card_setting` 同样迁 client-master，consumer 只剩 `t_customer` |
| S6 `customer_id` 稳定与降级 | 第 2.2 节 `StoredCardCheckoutTest` 新增降级用例 |
| S7 路由策略、`t_channel_transaction_ref`、回调 / 争议表（已定：按建议归属） | payment 与 channel-connector 的 DDL 与清理器表清单 |
| S10 死表删除 | 所有 DDL 与前置检查不再含 `t_command_idempotency`；`RiskDatabasePrecondition` 相关列校验删除 |
