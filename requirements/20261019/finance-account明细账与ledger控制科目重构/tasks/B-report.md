# B ledger 控制科目实现与模型脱离生成器 · 交付报告（2026-10-19）

结论：ledger core / app / test、建库 SQL、变更脚本、初始化 DML、PDManer 模型脱离生成器与 README 已按任务卡完成；本机 `mor_ledger` 已按授权重建（31 张表、规则行 36）。`verify -pl ledger/paytech-ledger-test -am` 全部通过（ledger-test 234 例）。主会话批准的两项卡外 / 偏离事项已落地：billing 事实详情增加 `paymentSucceededTime`（第 2.6 节）；迟到事实过账日期取顺延期间开始日（第 7 节第 8 项）。account 线澄清的 SKIPPED 批次规则已实现并覆盖测试（第 3 节）。

## 1. 已读规范

- `AGENTS.md`「接口开发必读与自查」
- `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- `agent-docs/examples/WRITE_API_EXAMPLE.md`（含第 11 节）
- `agent-docs/UNIT_TEST_GUIDE.md`（`examples/UNIT_TEST_EXAMPLE.md` 未逐行重读，测试结构沿用 ledger-test 现有基类与规范第 12、13 节）
- `agent-docs/DATABASE_DESIGN_GUIDE.md`：只按任务卡与 REQ `db/ledger/*.sql` 落地，未改动模型规则
- 需求：`tasks/00-common.md`、`tasks/T0-report.md`、`tasks/B-ledger.md`、`technique.md` 第 2 节、`design-discuss/finance-account-ledger-v3/design.md` 第 5、6.3、8 节
- Foundation：`paytech-messaging` 的 `IdempotentConsumeInterceptor`、`DomainEventMessageListener`、`ConsumedEventRepository`、`AggregateKey`；参照 acquiring `PaymentSucceededListener`

所属 Processor / Handler 与事务位置：消费入口 `AccountPostedPostingHandler`（HANDLER_MANAGED，事务外读进度与 billing 事实，`TransactionTemplate` 内调 `TransactionPostingService.post`）；追平 `PostingStreamCatchUpProcessor`（AbstractLockProcessor，事务外分页读 account、读 billing，每批次一个本库事务内过账 + `advance`）；关账步骤 3 在 `StartPeriodCloseProcessor` 内事务外调两个接口，消费进度在本库短事务内读取。

## 2. 改动清单

### 2.1 SQL 与模型（`paytech/ledger/docs/database/**`、测试 `init/ddl.sql`）

| 类型 | 文件 | 内容 |
|---|---|---|
| 修改 | `sql/ledger-schema.sql` | 按 REQ `db/ledger/ddl.sql`：`t_instruction_line.account_entry_id`（在 `manual_adjustment_line_id` 之后）+ `idx_instr_line_entry`；`t_ledger_account` owner_type / balance_bucket / 表注释；`t_accounting_instruction` origin_type / source_*；`t_close_step.step_type`；`t_trial_balance_exception.resolution_type`。仍 31 张表；与 `ledger.pdma.json` 渲染结果逐字一致 |
| 新增 | `sql/changes/2026-10-19-ledger-control-accounts.sql` | REQ `ddl.sql` + `dml.sql` 原文两段合并 |
| 修改 | `sql/ledger-initial-data.sql` | 删除规则行 5202、5208、5315–5320，头注释与 STANDARD_ADJUSTMENT 匹配说明同步 |
| 修改 | `ledger.pdma.json` | 直接改 JSON：新字段与索引（跨库引用不画关系）、上述允许值 / 表注释；`reliability` 图组改名「可靠执行与消费幂等」并补入第 11b 步已有但从未入图的 `t_consumed_event`、`t_consumed_aggregate_version` 两个表单元（未新增图组） |
| 修改 | `paytech-ledger-test/.../init/ddl.sql` | 表段与 `ledger-schema.sql`、数据段与 `ledger-initial-data.sql` 逐字一致（已用 diff 核对）；保留行 86 → 78 |

### 2.2 core（`com.paytech.ledger`）

| 类型 | 类 | 内容 |
|---|---|---|
| 修改 | `core/enums/InstructionOrigin` | 加 `ACCOUNT_POSTING` |
| 修改 | `core/enums/CloseStepType` | `RECONCILE_ACCOUNT_BALANCES` → `CHECK_ACCOUNT_POSTING_PROGRESS` |
| 修改 | DataObject `LedgerAccount` / `AccountingRuleLine` / `InstructionLine` | Owner 类型改 `LedgerOwnerType`、规则角色改 `RuleOwnerRole`；`InstructionLine.accountEntryId` |
| 修改 | `InstructionLineMapper.xml` | 读写 `account_entry_id` |
| 修改 | `AccountingInstructionMapper` / Repository | `selectByEconomicFact`（AUTO + ACCOUNT_POSTING）、新增 `selectBySourceEvent`（批次幂等） |
| 修改 | `AccountingPeriodMapper` / Repository | 新增 `findFirstUnclosedAfter`（迟到事实顺延） |
| 修改 | `LedgerAccountMapper` / Repository | 删除 `selectOwnerTotals`、`countNonZeroByOwnerTypes`、`selectJournalTotals`（消费者已删除） |
| 修改 | `TrialBalanceExceptionMapper` / Repository | 删除 `selectById`、`updateResolution`（只服务投影重建） |
| 修改 | `JournalLineMapper.xml` / `JournalEntryMapper.xml`、`AccountJournalLineView`、`JournalEntryDetailView` | 返回 `accountPostingId`、`accountPostingSequence`（ACCOUNT_POSTING 指令的来源事件键与版本），详情行 `accountEntryId` |
| 重写 | `TransactionPostingService(Impl)` | 入参 `AccountPostingCommand`；见第 3 节 |
| 新增 | `domain/AccountPostingBatch` | 批次校验与 Code 解析、过账流 `AggregateKey`、`requiresPosting()`（APPLIED 事实批次）；消费与追平共用 |
| 新增 | `domain/AccountPostingCommand`、`AccountPostingProgress`、`AccountPostingPage` | 命令、account 进度、批次页 |
| 修改 | `domain/EconomicFactProgress` | 加 `publishedFactCount` |
| 修改 | `domain/TransactionPostingContext`、`PeriodCloseContext` | 命令类型；删除 consistency 映射 |
| 新增 | `ext/mq/AccountPostedEventReader`、`AccountPostedPostingHandler` | 见第 3 节 |
| 新增 | `integration/account/AccountPostingClient(Impl)` | `getProgress`、`listPostings`（批次 DTO → 载荷，与消费同一校验） |
| 改名重写 | `integration/billing/EconomicFactClient(Impl)` | 原 `EconomicFactProgressClient`；`getProgress`（含已发布计数）+ `getFact(factId, factVersion)`（版本不一致 → `ConcurrentRequestException`） |
| 新增 | `service/processor/PostingStreamCatchUpProcessor` + request / response | 追平命令 |
| 修改 | `StartPeriodCloseProcessor` | 步骤 2 只查本库；步骤 3 两段进度检查 |
| 修改 | `PeriodCloseService(Impl)` | 删除 `countNonZeroTreasuryAccounts`；结转账户 Owner 用 `AccountIdentityRule.bookOwnerType` |
| 修改 | `LedgerQueryService(Impl)` | 删除 `listOwnerAccountTotals` |
| 重写 | `validator/account/AccountIdentityRule` | 五值矩阵；`CONTROL_ACCOUNT_CODES`（由 account-api `OwnerAccountType` 中 Owner 为 Client / Local Partner 的科目导出）；控制科目须以账簿主体为 Owner；桶一律 NONE |
| 重写 | `validator/adjustment/OwnerAccountAdjustmentRule` | `checkNoControlAccounts(ref, accounts, charts)` 按科目代码拒绝，错误码不变 |
| 修改 | `CreateAccountProcessor` | 身份判定移到读取账簿之后（需要账簿主体） |
| 修改 | `CreateRuleVersionProcessor` | `RuleOwnerRole`；规则行不得引用控制科目 |
| 修改 | `Submit/ApproveManualAdjustmentProcessor` | 读取科目后按控制科目拒绝 |
| 修改 | `core/constant/AccountingLocks` | 删除 `trialException`，新增 `postingStream(aggregateId)` |
| 删除 | 投影重建整条链路 | `Start/ApproveProjectionRebuildProcessor` 与两个 request、`ProjectionRebuildValidator`、`ProjectionRebuildContentHash`、`ProjectionSnapshot`、`TrialExceptionService(Impl)`、`TrialExceptionView`、`AccountJournalTotalsView` |
| 删除 | 旧消费与对账 | `EconomicFactRecordedPostingHandler`、`EconomicFactEventReader`、`TransactionPostingCommand`、`BalanceConsistencyClient(Impl)`、`BookBalanceConsistency` |

### 2.3 app

| 类型 | 文件 | 内容 |
|---|---|---|
| 新增 | `ext/rest/PostingStreamController`、`converter/PostingStreamConverter` | `POST /posting-streams/v1/catch-up`；maxBatches 缺省 200；按操作人 Header 记 INFO 日志 |
| 删除 | `TrialExceptionController`、`TrialExceptionConverter` | — |
| 修改 | `LedgerReadController` / `LedgerReadConverter` | 删除 account-totals |
| 修改 | `LedgerQueryConverter`、`LedgerSetupConverter` | 新字段输出；`LedgerOwnerType` 解析 |
| 修改 | `AccountClientConfiguration` | 类型改 `AccountPostingApi` |
| 修改 | `BillingClientConfiguration` | 注释（事实详情 + pending） |
| 修改 | `LedgerPersistenceConfiguration` | TypeHandler 注册 `LedgerOwnerType`、`RuleOwnerRole` |
| 修改 | `application.yml` | `messaging.consumers.account.partitions: 4`（删 billing）；两个服务 URL 保留，注释同步 |

### 2.4 test（`paytech-ledger-test`）

- 新增：`probe/AccountPostedPublisher`（扮演 account 发布到 `account.events`，序号可控，清理 `ledger.account.p*` 与 DLQ）、`builder/AccountPostingBuilder`、`mocker/external/AccountPostingMocker` + `mock/external/MockAccountPostingApi`（进度、按序号分页的批次列表；`getPosting`、`rebuild` 记为意外）、`mocker/external/EconomicFactMocker`（替换 `BillingProgressMocker`：pending 含 `publishedFactCount` + `getEconomicFact` DTO）。
- 删除：`BillingEventPublisher`、`MockBalanceBookApi`、`AccountConsistencyMocker`、`BillingProgressMocker`、`ProjectionRebuildTest`。
- 新增测试类：`auto/ext/mq/AccountPostedPostingHandlerTest`（6）、`auto/ext/http/stream/CatchUpPostingStreamTest`（6）、`auto/ext/http/query/AccountPostingJournalQueryTest`（2）。
- 改写：`TransactionPostingTest`、`TransactionPostingRejectionTest`、`EconomicAdjustmentPostingTest`（含替代净额两次、SKIPPED）、`StartPeriodCloseTest`（步骤 3 四种情况 + 不可用）、`PeriodCloseQueryTest`、`ReviewPeriodCloseTest`、`CreateAccountTest`、`ListAccountsTest`、`CreateRuleVersionValidationTest`、`RuleVersionLifecycleTest`（10 条规则行）、`LedgerReadApiTest`、`GetJournalEntryTest`、人工调整与试算用例的控制账户；`MockTestBase`、`AbstractTransactionPostingTest`、`AbstractPeriodCloseApiTest`、`AccountingTestMapper`（消费进度读写、按来源事件读指令）、`LedgerDataCleaner`（78 行）、`LedgerModuleBoundaryTest`（依赖集不变，只更新说明）。
- `application-mock.yml`：消费者改 `account`；新增 `spring.sql.init.mode: never`（见第 7 节）。

### 2.5 `scripts/pdmaner`

| 类型 | 文件 | 内容 |
|---|---|---|
| 修改 | `finance/service-split.mjs` | `FINANCE_SERVICES` 去掉 ledger；`READ_ONLY_COPIES.currency_spec = [billing]`；删除 ledger 变体与追加表；manifests 01 / 03 / 07 与 08 的默认归属改为 `NOT_PROJECTED`（`ownerOf` 返回空即跳过，其余仍须命中服务）；`EXPECTED_SERVICE_TABLE_COUNTS` 去掉 ledger |
| 修改 | `finance/generate-finance-model.mjs` | `SERVICE_TITLES` 去掉 ledger，注释 |
| 删除 | `finance/manifests/09-service-physical.mjs` | 其中 `LEDGER_ACCOUNT`、两张消费表定义只服务 ledger，已无消费者 |
| 修改 | `finance-model.test.mjs` | 投影断言：currency_spec 只在 billing；ledger 表不投影；owners 84 − 1 − 27 = 56。84 表逻辑模型与摘要未改 |
| 修改 | `pdmaner-canonical-paths.test.mjs` | ledger 生成器置 null |
| 新增 | `ledger-model.test.mjs` | 表集合 = `ledger-schema.sql`（31）；控制科目字段 / 索引 / 允许值；库内关系指向主键且有左前缀索引；每张表与关系在图中各一次；标识唯一 |

### 2.6 主会话批准的卡外改动（billing，2026-10-19）

| 类型 | 文件 | 内容 |
|---|---|---|
| 修改 | `paytech/billing/paytech-billing-api/.../response/EconomicFactResponseBody.java` | 新增 `Instant paymentSucceededTime`（仅 PAYMENT，其余为空） |
| 修改 | `paytech/billing/paytech-billing-app/.../converter/EconomicFactConverter.java` | `toResponseBody` 写入 `payload.getPaymentSucceededTime()` |

billing-test 无需调整；`verify -pl billing/paytech-billing-test -am`（billing 包 90 例）通过。

## 3. 接口 / 事件 / 过账行为前后对照

| 项 | 之前 | 之后 |
|---|---|---|
| 消费事件 | `EconomicFactRecorded`（billing，SHARED，非严格有序） | `AccountPosted`（account，HANDLER_MANAGED，strictOrder，按 `{OE|FE}.{entity}.{ccy}.{shard}` 聚合） |
| 首条序号 | — | 该流无进度且序号 ≠ 1 → `ConcurrentRequestException` 重投 |
| 桶移动 | — | 不读事实、不过账，只推进进度 |
| SKIPPED 批次 | — | 不对事实自身应用规则包；有流水（所替代事实的冲回行）或有被替代事实时读事实，记流水控制科目行 + 被替代事实正向规则行的反向行；无流水且无被替代事实时只推进进度（account 线澄清，v1 已应用 → v3 先到 → v2 后到 SKIPPED 冲回 v1） |
| 指令头 | `AUTO`、来源取事实 tuple | `ACCOUNT_POSTING`、`source_system = account`、`source_event_key = postingId`、`source_version = postingSequence`、`economic_fact_id`；`source_payload_hash` 为空 |
| Owner 行 | 规则 5202 / 5208 / 5315–5320 按 Client / Partner × 桶开户 | 每条流水一行：控制账户（账簿主体、NONE），`account_entry_id`；BANK_CASH 流水记 `CASH_ACCOUNT / 流水 Owner / NONE`；其他科目拒绝 |
| 非 Owner 行 | 规则引擎 | 不变；调整事实中 Client / Local Partner 一侧跳过规则 |
| 版本替代 | 无 | 同类型指令内追加被替代事实**正向**规则行（行方向 = 规则方向）的反向行，已是反向的行不再取反；被替代事实账簿须一致 |
| 幂等 | `economic_fact_id` | `findBySourceEvent(account, postingId)`，`uk_instr_source` 兜底 |
| 过账期间 | 业务日所在期间须 OPEN，否则拒绝进 DLQ | OPEN 直接用；CLOSED 顺延到其后最早未关闭期间（须 OPEN，`posting_date` = 该期间开始日，记 WARN：事实 ID、业务日期、原期间、目标期间）；否则 `ConcurrentRequestException` |
| 关账步骤 2 | 本库检查 + billing / account 追平 | 只查本库 |
| 关账步骤 3 | `RECONCILE_ACCOUNT_BALANCES`：渠道 / 银行非零 | `CHECK_ACCOUNT_POSTING_PROGRESS`：billing pending = 0、applied = published、maxApplied = maxPublished；每条流 ledger 已消费 = account 最大序号；摘要记 `billingPending/Published/MaxPublished/accountApplied/MaxApplied` 与 `stream=<id>:account=n/ledger=m` |
| 新接口 | — | `POST /posting-streams/v1/catch-up`：200 `{processedCount, lastSequence, caughtUp}`；account / billing 不可用 503；并发 409；规则拒绝 400；字段错误 422 |
| 删除接口 | `GET /ledger/v1/books/{bookId}/account-totals`、投影重建两条 | — |

## 4. 写接口示例第 11 节自查

- **API 与 Body**：使用 T0 的 `PostingStreamCommandApi` / Body（class、中文注释、Validator），ledger-api 未再改动。内部 `CatchUpPostingStreamRequest` 带注解，由 `validateBasic` 触发。
- **Controller / Converter**：`PostingStreamController` 只做转换、委托与日志；`PostingStreamConverter` 负责 Code 解析与缺省值。
- **Processor**：`PostingStreamCatchUpProcessor` 位于 core `service/processor`，主方法显示「读进度 → 分页 → 逐批次校验连续 → 处理」；不访问 Mapper。
- **Context / 对象**：沿用 `TransactionPostingContext`；新增 `AccountPostingCommand` 是 Service 的完整入参（批次 + 事实），无同义 Input / Snapshot。
- **Service**：`TransactionPostingServiceImpl` 要求调用方事务，只读写本库，不调外部、不调其他 Service（记账内核 `JournalPostingService` 是同一保存阶段的既有协作，沿用原结构）。
- **事务位置**：Handler 与追平在事务外调 billing / account；Handler 用 `TransactionTemplate` 包过账；追平每批次一个事务内 `post + advance`；关账步骤 3 事务外调用，消费进度在短事务中读（`ConsumedEventRepository` 要求事务）。
- **Repository**：新增方法均单表、SQL 在 XML。
- **锁**：追平按流 TRY_LOCK；消费 Handler 不取锁，并发由 `uk_instr_source`（DuplicateKey → 409）与只增 `advance` 保护。
- **原请求 / 业务防重**：批次主键即业务身份；同批次已有指令直接返回。不涉及 HTTP `Idempotency-Key`。
- **View / DataObject 边界**：查询新增字段走既有 View；HTTP 不暴露 DataObject。
- **无多余抽象**：新增类均有当前消费者；删除了无消费者的 Repository 方法、manifest 09。
- **测试**：真实入口（HTTP、真实 Broker + Handler、Handler 直调）+ 本地 MySQL / Redis / RabbitMQ + 外部 DTO Mock；无 Mockito / MockBean / Testcontainers。

## 5. 测试

命令（JDK 25，离线，`MOR_RABBITMQ_VHOST=/mor-e2e`）：

```bash
./mvnw -B -o test -pl ledger/paytech-ledger-test -am '-Dtest=com.paytech.ledger.**' -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -B -o verify -pl ledger/paytech-ledger-test -am
./mvnw -B -o verify -pl tools/arch-rules,tools/arch-rules-reactor-test
node tools/arch-rules/scripts/generate-table-ownership.mjs --check
node --test scripts/pdmaner/ledger-model.test.mjs scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/pdmaner-canonical-paths.test.mjs scripts/pdmaner/oe-client-model.test.mjs
```

| 范围 | 结果 |
|---|---|
| paytech-ledger-test | 234 例全部通过 |
| 完整 `verify -pl ledger/paytech-ledger-test -am` | BUILD SUCCESS；排除 Foundation 既有不稳定用例 `PartitionQueueBaselineAutoTest`（同 T0：第一次完整运行在其 `test_normalFlow_residualMessagesPurged` 失败，与本卡无关） |
| billing（批准的卡外改动） | `verify -pl billing/paytech-billing-test -am`，billing 包 90 例通过 |
| arch-rules / reactor | 60 + 3 通过；table-ownership 最新 |
| `scripts/pdmaner`（四个文件 45 例） | 42 通过、3 失败，均与本卡无关：`finance-model` 第 2、3 例为既有 cf4a429c 漂移（现在落在 billing 模型 name / describe）；`oe-client-model` 的 manual schemas 例失败在 A 线正在修改的 account 模型。ledger 的 schema 与模型渲染逐字一致，`ledger-model.test.mjs` 5 例全过 |
| 本机库 | `mor_ledger` 已按授权命令重建：31 张表、规则行 36 |

## 6. 场景覆盖

控制科目行与 `account_entry_id`、非 Owner 规则行、借贷平衡（PAYMENT 与调整事实）；替代净额（两次替代）；SKIPPED 只推进；SKIPPED 带冲回流水（v1 → v3 → v2）；BALANCE_MOVEMENT 只推进；首条非 1 等待后进 DLQ、追平补齐并使死信重放按 STALE 跳过；缺口（3 先于 2）重投补齐；重复投递与重复调用幂等；追平分段续跑、已追平、account 503、billing 503（已处理批次保留）、字段错误；已关账期间顺延；无开放期间等待；事实版本不一致等待；人工调整拒绝控制科目（提交、批准）；关账 billing 未发布 / 计数不等 / ledger 落后后追平通过 / 依赖不可用；规则版本拒绝已删除角色、控制科目与非 NONE 桶；创建账户身份矩阵；查询新字段（详情与账户流水）；投影重建删除后试算与关账链路回归。

## 7. 偏离设计或卡片的地方

1. **PDManer 图组**：`reliability` 图组改名并补入两张消费表的表单元（第 11b 步遗留，未入图会使「每张表在图中出现一次」不成立）；未新增图组。
2. **manifest 09 整个删除**：卡片写「LEDGER_ACCOUNT 等」，文件内三项都只服务 ledger。
3. **`application-mock.yml` 新增 `spring.sql.init.mode: never`**：`-am` reactor 构建时，`paytech-messaging` test-jar 解析为整个 `target/test-classes`，其 `application-mock.yaml` 会让 ledger 测试执行 DROP / CREATE 脚本而启动失败（`ledger_app` 无 DROP 权限）。先例是 risk-decision 的同名配置。建议 Foundation 让 test-jar 的测试配置不进入消费方。
4. **规则行不得引用控制科目**（`CreateRuleVersionProcessor`）：卡片只写「拒绝 Owner 角色（枚举已无）」。若允许，BOOK_ENTITY 规则行会混入控制账户，控制科目 = 明细合计将不再成立。
5. **BANK_CASH 流水**：设计只写「每条流水 → 控制账户」；BANK_CASH 不是 Owner 控制科目，按「ledger 仍为 CASH_ACCOUNT / NONE」记到流水 Owner 的银行账户。当前没有事实会产生 BANK_CASH 流水（billing 无 CASH 付方 / 收方角色）。
6. **`source_payload_hash`** 对 ACCOUNT_POSTING 指令为空（追平路径没有信封摘要；输入摘要 `input_hash` 覆盖规则行、流水 ID、账户、方向与金额）。
7. **明细行的 `partyType / partyId`**（主会话已认可）：billing 详情接口不返回 `partyType / partyId`，客户端按 billing 登记约定取收方。PAYMENT 的 SELLER_TAX 税务机关依赖这一点；billing 将来产生 PAYMENT 事实时须保持「归属方 = 收方」或补充字段。

8. **迟到事实的 `posting_date`（主会话已批准，待用户确认并回写文档）**：偏离 technique 2.1、设计 8.9 与决定第 12 条「保留原业务日期」。现行记账内核要求过账日期落在期间内，试算与损益结转按 `posting_date` 区间取数；保留旧日期会让迟到分录落入下一期期初且永不结转。实现为：所属期间 CLOSED 时顺延到其后最早的 OPEN 期间，`posting_date` 取该期间开始日并记 WARN；业务日期经 `economic_fact_id` 与 billing 事实追溯。试算 / 结转未改为按 `accounting_period_id` 取数。
9. **SKIPPED 批次规则**按 account 线澄清实现（`AccountPostingBatch.requiresPosting / appliesRules`），见第 3 节。

## 8. 阻断项与待主会话确认

无阻断项。需同步给 D 线 / 主会话：

1. technique 2.1 / 设计 8.9 / 决定第 12 条按第 7 节第 8 项回写。
2. 「保留行 86 → 78」已落地；`CloseStepType` 与 `t_close_step` 注释已改；控制台若展示关账步骤名需同步 `CHECK_ACCOUNT_POSTING_PROGRESS`。
3. `scripts/pdmaner` 的 3 例失败不属本卡：billing 模型 cf4a429c 漂移（finance-model 第 2、3 例），以及 A 线进行中的 account 模型（oe-client-model 的 manual schemas 例）。
