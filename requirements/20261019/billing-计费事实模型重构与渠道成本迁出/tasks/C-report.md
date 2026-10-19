# C 线报告（account + ledger 适配 BillingFactRecorded / 新载荷 / 新查询接口 / ledger-api 枚举）

日期：2026-10-19。执行者：C 线。未执行任何 git 写命令、数据库 DDL / DML 或 Superpowers skill；只改本卡「允许改动的路径」。

## 1. 已读规范与设计

- `tasks/00-common.md`、`tasks/T0-report.md`、`tasks/C-account-ledger.md`
- `technique.md` 第 2.1、2.2 节
- `AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（目录与第 4、5、7 节）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（目录）、
  `agent-docs/examples/WRITE_API_EXAMPLE.md`（第 9–12 节，含第 11 节自查）、`agent-docs/UNIT_TEST_GUIDE.md`（第 9–20 节）、
  `agent-docs/examples/UNIT_TEST_EXAMPLE.md`（目录）
- 参照：`design-discuss/billing/cases.md` C1、C9（明细行付方 / 收方写法）

本线不新增接口、Processor 或事务，只改消费 / 读取适配：account 的 Handler 仍是 SHARED 本地事务（拦截器事务内入账）；ledger 读事实仍在事务外经 `BillingFactQueryApi`，过账事务内只写 mor_ledger。

## 2. 改动文件清单

### 2.1 account（`paytech/account`）

| 动作 | 文件 |
|---|---|
| 新增（改名） | core `ext/mq/BillingFactRecordedBalanceHandler.java`（原 `EconomicFactRecordedBalanceHandler`，`eventType = BillingFactPayload.EVENT_TYPE`） |
| 新增（改名） | core `ext/mq/BillingFactEventReader.java`（原 `EconomicFactEventReader`，校验 `BILLING_FACT` 聚合类型，行带入 `targetBucket`） |
| 修改 | core `domain/FactRecord.java`：`Line` 增加 `targetBucket`（本期不参与映射） |
| 修改 | core `domainservice/validator/FactBalanceMappingRule.java`：删隐含收方分支、`IMPLICIT_PAYEE`、`PARTY_REQUIRED_FEES`、`PAYMENT` 等字符串常量；`PLATFORM_SIDE_FEES` 删 `GROSS_AMOUNT`；常量改引 `BillingFactType` / `FactComponentType` / `FactPartyRole` / `FactLineDirection` 的 `getCode()`；类注释按新模型改写 |
| 修改（仅注释 / 日志措辞） | core：`package-info`、`AccountStatus`、`AppliedFactStatus`、`MaturityStatus`、`AccountEntry`、`AccountPosting`、`BalanceAppliedFact`、`BalanceAppliedFactMapper`(.java/.xml)、`BalanceEffect`、`AccountPostingWriter`、`BalanceAppliedFactRepository(Impl)`、`BalanceApplicationService(Impl)`、`BalanceMaturityService`、`AccountPostedHandler`、`MatureBalancesProcessor`、core `pom.xml` |
| 修改（仅注释） | app `pom.xml`、`application.yml`；api 的 `FactApplyStatus`、`MovementSourceType`、`PostingSourceType`、`AccountPostedPayload`、`AccountPostedEntryPayload`、`AccountPostingProgressResponseBody`、`AccountPostingResponseBody`、`OwnerAccountEntryResponseBody`（只改 JavaDoc 中的事实名） |
| 修改 | `compensation-event-catalog.yaml`（注释）、`README.md`、`docs/balance-query-api.md` |
| 新增（改名） | test `builder/BillingFactBuilder.java`（原 `EconomicFactBuilder`）、`auto/mq/BillingFactBalanceConsumeTest.java`（原 `EconomicFactBalanceConsumeTest`） |
| 修改 | test `probe/BillingEventPublisher.java`（新载荷 JSON：`originType`、`status`、`businessTime`、`refs`、行 `targetBucket`；删 `paymentSucceededTime`、`partnerAgreementOeId`、`channelAccountId`、行 `partyType/partyId`） |
| 修改（只改引用） | test `AbstractBalanceTest`（删 `PARTNER_AGREEMENT_OE_ID`）、`MockTestBase`、`AccountTestMapper`、`AccountModuleBoundaryTest`、`AccountPostedHandlerTest`、`OwnerAccountQueryTest`、`QueryOwnerBalanceTest`、`AccountPostingQueryTest`、`BalanceMaturityJobTest` |
| 删除 | 上述四个旧名文件 |
| 修改（主会话追加授权，仅描述） | `paytech/account/pom.xml` 第 18、48 行：`EconomicFactRecorded` 改 `BillingFactRecorded` |

### 2.2 ledger（`paytech/ledger`）

| 动作 | 文件 |
|---|---|
| 新增（改名） | core `integration/billing/BillingFactClient.java`、`impl/BillingFactClientImpl.java`（原 `EconomicFactClient(Impl)`）：`getProgress` 调 `getBillingFactProgress(entityType, entityId, currency)`；`getFact` 调 `getBillingFact`，响应体字段直接映射到 `BillingFactPayload`（含 `refs`、行 `targetBucket`），不再构造 `partyType/partyId` |
| 新增（改名） | core `domain/BillingFactProgress.java`（原 `EconomicFactProgress`，字段不变） |
| 修改 | core `domainservice/service/impl/TransactionPostingServiceImpl.java`：`EconomicsType` → `BillingFactType`；`AmountSource`、`AutoInstructionType` 改引 ledger-api；业务时间统一 `fact.getBusinessTime()`（缺失拒绝）；`CHANNEL_ACCOUNT` Owner 取 `refs` 中 `CLEARING_ACCOUNT` 的 `refId`（新方法 `clearingAccount`）；税务机关取 `SELLER_TAX` 行 `payeeId` |
| 修改 | app `configuration/BillingClientConfiguration.java`：只注册 `BillingFactQueryApi` |
| 修改（import / 改名） | core `AccountingRuleLine`、`RuleVersionView`、`PostedInstructionView`、`TransactionPostingContext`、`AccountPostingCommand`、`TransactionPostingService`、`JournalPostingServiceImpl`、`OwnerAccountAdjustmentRule`、`AccountPostedPostingHandler`、`CreateRuleVersionProcessor`、`SubmitRuleVersionProcessor`、`SubmitManualAdjustmentProcessor`、`PostingStreamCatchUpProcessor`、`StartPeriodCloseProcessor`、`package-info`、`AccountingInstruction`（注释）、`AccountingInstructionMapper.java`（注释）、core `pom.xml`；app `LedgerPersistenceConfiguration`（TypeHandler 注册 ledger-api 枚举）、`AccountingRuleBundleConverter`（未改代码，经 import 统一）、app `pom.xml`、`application.yml` |
| 修改 | `README.md`（接口路径、依赖、业务时间与渠道账户来源）、`docs/database/FINANCE_PHYSICAL_DECISIONS.md`（新增 2026-10-19 说明块；历史块原文保留） |
| 新增（改名） | test `mock/external/MockBillingFactApi.java`（实现 `BillingFactQueryApi`，`/billing/v1/facts/**`；列表与按引用查记为意外调用）、`mocker/external/BillingFactMocker.java`（原 `EconomicFactMocker`；响应体按新字段组装，调用记录键 `pending:` → `progress:`） |
| 修改（改名） | test `BillingFactBuilder`（原 `TransactionEconomicsBuilder`；`businessTime`、`refs` 含 `CLEARING_ACCOUNT`、`originType`、`status`；`partner(lp, fe)` 二元组；`economicsType` → `factType`，非 PAYMENT 时业务时间取批准时间）、`BillingFactLineBuilder`（原 `EconomicComponentBuilder`；每行付方 / 收方，见第 6 节）、`BillingFactAdjustmentPostingTest`（原 `EconomicAdjustmentPostingTest`，只改名与引用） |
| 修改（只改引用） | test `MockTestBase`（字段 `economicFacts` → `billingFacts`）、`AbstractTransactionPostingTest`（`paymentDate/recordedDate` 合并为 `businessDate`；删 `PARTNER_AGREEMENT_OE_ID`）、`AbstractPeriodCloseApiTest`、`AccountPostingBuilder`、`RuleVersionRequestBuilder`、`AccountingTestMapper`、`LedgerTestMockConfiguration`（注释）、`LedgerModuleBoundaryTest` 及 10 个 auto 测试类 |
| 删除 | `EconomicFactClient(Impl)`、`EconomicFactProgress`、`MockEconomicFactApi`、`EconomicFactMocker`、`TransactionEconomicsBuilder`、`EconomicComponentBuilder`、`EconomicAdjustmentPostingTest` |
| 修改（主会话追加授权，仅文字） | ledger-api `enums/LedgerErrorCode.java`：注释与默认消息「MANUAL_ADJUSTMENT 经济事实」改「计费事实」，Code 不变 |
| 修改（主会话追加授权，仅描述） | `paytech/ledger/pom.xml` 第 48 行，见第 6 节第 7 项 |

两服务都没有改表结构、测试 DDL 或 SQL；无需重建测试库。

## 3. 前后对照

| 项 | 之前 | 之后 |
|---|---|---|
| account 消费事件 | `EconomicFactRecorded` / `ECONOMIC_FACT` | `BillingFactRecorded` / `BILLING_FACT`（Schema 1） |
| account 映射 | PAYMENT 行可无付方 / 收方（隐含收方 CLIENT_PRINCIPAL→Client、PARTNER_SHARE→Partner）；CHANNEL / OE 平台侧允许 `GROSS_AMOUNT` | 每行必须有付方 / 收方，缺任一即拒绝（死信）；平台侧删 `GROSS_AMOUNT`；其余（Owner 目标、PENDING、REVERSAL 互换、COST_ADJUSTMENT 限制、Fail-Closed）不变 |
| ledger 读事实 | `GET /billing/v1/economic-facts/{factId}` | `GET /billing/v1/facts/{factId}` |
| ledger 关账进度 | `GET /billing/v1/economic-facts/pending?bookEntityType&bookEntityId&currency` | `GET /billing/v1/facts/progress?entityType&entityId&currency` |
| ledger 业务时间 | PAYMENT 取 `paymentSucceededTime`，其余取 `recordedTime` | 一律 `businessTime`，缺失拒绝「计费事实缺少业务时间（businessTime）」 |
| ledger 渠道账户 Owner | 头字段 `channelAccountId` | `refs` 中 `CLEARING_ACCOUNT` 的 `refId`；缺失拒绝原因不变（「缺少 channel_account_id」）；多个不同引用拒绝 |
| ledger 税务机关 | 行 `partyId` | 行 `payeeId` |
| 枚举 | billing-api `AmountSource`、`AutoInstructionType` | ledger-api 同名枚举，值不变；TypeHandler 改注册 ledger-api 类 |
| 表 / 事件发布 | — | 无变化；`t_accounting_instruction.economic_fact_id` 列名不改 |

## 4. 写接口示例第 11 节自查

| 项 | 结果 |
|---|---|
| API 与 Body 最小合同 | 不适用：本线不定义合同，只消费 T0 合同。 |
| Controller / Converter 仅适配 | 符合：`BillingFactClientImpl` 只做 DTO → 载荷字段映射；`AccountingRuleBundleConverter` 未改逻辑。 |
| Processor 查询本模块 Repository | 不适用：未新增 Processor；`StartPeriodCloseProcessor`、`PostingStreamCatchUpProcessor` 只换出站 Client 类型。 |
| Context | `TransactionPostingContext` 只把载荷类型换为 `BillingFactPayload`，未增字段。 |
| Service 边界 | 符合：`TransactionPostingServiceImpl` 的新方法 `clearingAccount` 只读内存载荷，不调外部依赖。 |
| 本地原子写入与事务位置 | 不变：account 入账在消费拦截器 SHARED 事务内；ledger 读 billing 仍在事务外（Handler 与追平），事务内只写 mor_ledger。 |
| Repository 与行数检查 | 不变。 |
| 锁 | 不变。 |
| 原请求比较 / 重放 | 不变：account 仍以 `fact_id + fact_version` 业务幂等；ledger 版本不一致仍按并发冲突等待重投。 |
| Gateway 幂等区分 | 不适用：无 HTTP 写入口改动。 |
| View / DataObject 不泄露 | 符合：未改 View；DataObject 未出模块。 |
| 公共包复用、无多余抽象 | 符合：无新依赖；ledger 只注册查询接口。 |
| 测试验证 | 见第 5 节，两服务全量 auto 通过且计数不变。 |

## 5. 测试命令与计数

```text
cd paytech
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
set -a; source ../local-environment/.env.local; set +a
export PAYTECH_REDIS_PASSWORD=${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}
./mvnw -B -o verify -pl account/paytech-account-test,ledger/paytech-ledger-test -am
  -> BUILD SUCCESS
  -> paytech-account-test：Tests run: 58, Failures: 0, Errors: 0, Skipped: 0（基线 58）
  -> paytech-ledger-test：Tests run: 234, Failures: 0, Errors: 0, Skipped: 0（基线 234）
  -> -am 上游（common-core、Foundation 各模块、messaging 73 等）全部通过
```

收尾批改名后重跑同一命令：account 58、ledger 234，构建成功。

首轮因 ledger 测试仍引用已删除的 `FactPartyRole.PLATFORM` 编译失败，改为 `CONSUMER` 后第二轮全部通过。日志中 RabbitMQ `PRECONDITION_FAILED` 来自 Foundation messaging 测试自身的负例队列，与本线无关，构建结果为成功。

计数相同，用例有三处改名或改输入，断言的业务行为不变：

| 测试 | 变化 | 原因 |
|---|---|---|
| account `test_platformRoleRejectedToDeadLetter` → `test_consumerRoleRejectedToDeadLetter` | 拒绝角色由 `PLATFORM` 改 `CONSUMER` | `PLATFORM` 已从 `FactPartyRole` 删除；`CONSUMER` 在两边同样无规则，仍进死信 |
| ledger `BillingFactAdjustmentPostingTest.test_unmappableCombinationRejected`（原 `EconomicAdjustmentPostingTest`） | 第一组输入同上改 `CONSUMER`，期望消息「角色没有记账规则 CONSUMER」 | 同上 |
| ledger `test_missingSucceededTimeRejected` → `test_missingBusinessTimeRejected` | 输入 `businessTime(null)`，期望片段「businessTime」替代「支付成功时间」 | 拒绝消息随统一业务时间改写，行为仍是拒绝且不落库 |

## 6. 偏离卡片与需要主会话确认的事项

1. **account PAYMENT 样例的付方 / 收方未用"渠道 → Client"。** 卡片举例 `CHANNEL → CLIENT`，但按卡片保留的规则，CHANNEL 平台侧只允许 `CHANNEL_COST`。因此 `CLIENT_PRINCIPAL` 渠道 → Client 会被拒绝。account 测试改用经营主体 → Client（本金）、经营主体 → Partner（分润）、经营主体 → 渠道（渠道成本）、经营主体 → Finance Entity（服务费）。这样断言不变。
2. **account `test_paymentFactPostsOwnerLines` 去掉了 `CLIENT_FEE 3` 行。** 现行规则下，带付方 / 收方的 CLIENT_FEE 只有两种结果。一种是 Client → OE，它会给 Client 记借方 3，改变余额断言。另一种是两边都是平台侧，这只能是 OE → OE。样例改为本金、渠道成本、服务费、分润四行。分润仍是第 4 行，流水行号 1、4 和余额断言都没变。
3. **待后续切片决定：自动 PAYMENT 事实与 account 映射不兼容。** 讨论稿 C1 写的是本金渠道 → Client、手续费 Client → OE、服务费 Partner → Finance Entity。现行 account 规则会拒绝第一行和第三行，第二行会扣 Client 余额。ledger 的 PAYMENT 走交易规则包，不看行付方，所以不受影响。本期没有自动 PAYMENT 事实，现在不触发。启用自动事实生成前，需要先确定 account 对 PAYMENT 行的映射。
4. **ledger 测试的 PAYMENT 行按 C1 写付方 / 收方。** 付方标识来自事实头时，由 `BillingFactBuilder.build` 按头字段填入。ledger 过账不读 PAYMENT 行付方，断言不受影响。
5. **CLEARING_ACCOUNT 多值。** 现在有多个不同 `CLEARING_ACCOUNT` 引用时，ledger 按 Fail-Closed 拒绝「CLEARING_ACCOUNT 引用不唯一」。卡片没有说明这种情况。
6. **范围内仍有旧名的位置（有意保留）：**
   - ledger-api `AmountSource` 及其引用是卡片允许的例外。
   - `t_accounting_instruction.economic_fact_id` 列按卡片不改名。与它绑定的 `AccountingInstruction.economicFactId`、`selectByEconomicFact`、`findByEconomicFact` 和测试 Mapper SQL 也保留。只改 Java 名会和列名脱节。
   - account-test 与 ledger-test 的 `init/ddl.sql`，以及两服务 `docs/database/**` 的 SQL / pdma 列注释仍写"经济事实"和 `EconomicsType`。它们必须与已执行的 Schema 一致，本线不改 DDL。需要的话由主会话在阶段 5 统一处理。
   - ledger 枚举值 `AutoInstructionType.ECONOMIC_ADJUSTMENT` 和 account-api 的 `PostingSourceType.ECONOMIC_FACT` 是已持久化或跨服务的代码值，不改。（三个旧名测试类已按主会话追加授权改名，见第 2.2 节。）
7. **已收尾（主会话追加授权）：**
   - `paytech/account/pom.xml` 第 18、48 行：`EconomicFactRecorded` 改 `BillingFactRecorded`。
   - `paytech/ledger/pom.xml` 第 48 行：授权写的是"改 `BillingFactRecorded`"，但 ledger 早已不消费该事件，现在消费的是 `AccountPosted`。照写会写错事实，所以改为 `AccountPosted`，请主会话确认。
   - ledger-api `LedgerErrorCode` 的注释与默认消息改为"计费事实"，Code 不变，ledger 测试没有断言这段文字。
   - 三个 ledger 测试类按授权改名，只改名与引用，断言不动。
   - 重跑同一条命令仍为 account 58、ledger 234，构建成功。
8. **测试库：** 本线未改任何 `*-test/src/test/resources/init/ddl.sql`，无需重建。
