# 任务卡 A（ledger）交付报告

日期：2026-10-20。工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-fin3`（未提交、未建分支、未 stash / 回滚，未 `mvn install`）。

## 0. 开工说明（WRITE_API_EXAMPLE 第 1 节）

已读：`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`（XXL / ext/job 相关节）、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`（第 1—2 节）、`DATABASE_DESIGN_GUIDE.md`（相关节）；需求 `technique.md` 第 2—6 节与三份 `requirement.md`；任务卡 `00-common.md`、`A-ledger.md`。

- **D1**：只改协议适配层。API 新增两个 Header 参数（Actor 带 `@Pattern([1-9][0-9]{0,18})`，由 Spring 方法校验在 Controller 入口拒绝），Converter 把 Header 转成内部 Request 的 `makerId` / `checkerId`。各 Processor、Context、事务与业务规则不变（复核人 ≠ 编制人、提交人 = 编制人照旧）。试算只在 Controller 记一条 INFO 操作日志。
- **L1**：只读查询，不建 Processor。Controller → `ManualAdjustmentService.pageAdjustments`（先查账簿、按币种精度补齐金额）→ `ManualAdjustmentRepository` → Mapper XML（`t_manual_adjustment` 左关联 `t_accounting_instruction` / `t_journal_entry` 取分录主键，按 `idx_manual_adj_book` 过滤，`LIMIT/OFFSET` + `COUNT`）。不开事务。
- **L2**：只读查询，Controller → `AccountingRuleService.listBundles`（规则包 + 版本摘要），由 Converter 取当前版本开始时间。
- **L3**：`VoidRuleVersionProcessor`（规则包资源锁 LOCK，与其他规则写入口相同）。本地原子写：`AccountingRuleService.voidVersion` 一个事务内锁规则包行 → 锁版本行 → 以原状态 DRAFT 为条件更新为 VOIDED。没有事务外步骤。
- **L4**：`AccountingRuleVersionActivationJobHandler`（app `ext/job`）→ `ActivateDueRuleVersionsProcessor`（无锁，事务外逐个处理）→ 每个版本 `ActivateRuleVersionProcessor`（规则包锁，取锁后重读，非 APPROVED 或未到期直接返回）。本地原子写：`AccountingRuleService.activateVersion` 一个事务内锁规则包 → 锁版本 → APPROVED→ACTIVE（条件更新）→ 原当前版本 RETIRED → 规则包指针 CAS 切换。替代版本的读取与“生效时间必须晚于当前版本”检查从 `ApproveRuleVersionProcessor` 移到 `AccountingRuleService.findReplacedVersion`，批准立即生效与到期生效共用。

## 1. 最终 API 签名（供卡 D）

所有命令前两个参数为 `@RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String actorAccountId`、`@RequestHeader("X-Gateway-Session-Id") String sessionId`；缺失或格式错误为 400 `INVALID_REQUEST`。

| 接口 | 方法 / 路径 | 签名（Header 之外） | 请求体 / 响应 |
|---|---|---|---|
| `ManualAdjustmentApi` | POST `/manual-adjustments/v1` | `submitManualAdjustment(actor, session, body)` | `SubmitManualAdjustmentRequestBody`（去掉 makerId）→ 201 `SubmitManualAdjustmentResponseBody` |
| | POST `/{manualAdjustmentId}/approve`、`/reject` | `approveManualAdjustment` / `rejectManualAdjustment(actor, session, id, body)` | `ReviewManualAdjustmentRequestBody{approvedContentHash}` → `ReviewManualAdjustmentResponseBody` |
| | **新增** GET `/manual-adjustments/v1` | `listManualAdjustments(ledgerBookId 必填, accountingPeriodId?, status? SUBMITTED\|POSTED\|REJECTED, makerId?, page?, size?)` | `PagingResponseBody<ManualAdjustmentResponseBody>`：manualAdjustmentId、adjustmentNo、ledgerBookId、accountingPeriodId、currency、reasonCode、status、totalDebit、totalCredit、makerId、submittedTime、journalEntryId（可空）；按 submittedTime、id 倒序；账簿不存在 400 `LEDGER_BOOK_NOT_FOUND` |
| | GET `/{manualAdjustmentId}` | 不变 | |
| `PeriodCloseApi` | POST `/accounting-periods/v1/{periodId}/closes` | `startPeriodClose(actor, session, periodId)` | **无请求体**（`StartPeriodCloseRequestBody` 已删除） |
| | POST `/{periodId}/reopens` | `startPeriodReopen(actor, session, periodId, body)` | `StartPeriodReopenRequestBody{reasonCode, requestRef}` |
| | POST `/{periodId}/closes/{closeNo}/approve`、`/reject` | `approvePeriodClose` / `rejectPeriodClose(actor, session, periodId, closeNo, body)` | `ReviewPeriodCloseRequestBody{approvedCloseHash}` |
| | 列表 / 详情 | 不变 | |
| `TrialBalanceApi` | POST `/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances` | `runTrialBalance(actor, session, ledgerBookId, periodId)` | 无请求体；查询方法不变 |
| `AccountingRuleBundleApi` | POST `/accounting-rule-bundles/v1/{bundleCode}/versions` | `createRuleVersion(actor, session, bundleCode, body)` | `CreateRuleVersionRequestBody{effectiveFrom, lines}` → 201 |
| | POST `/{bundleCode}/versions/{bundleVer}/submit`、`/approve`、`/reject` | `submitRuleVersion` / `approveRuleVersion` / `rejectRuleVersion(actor, session, bundleCode, bundleVer)` | **无请求体**（`SubmitRuleVersionRequestBody`、`ReviewRuleVersionRequestBody` 已删除） |
| | **新增** POST `/{bundleCode}/versions/{bundleVer}/void` | `voidRuleVersion(actor, session, bundleCode, bundleVer)` | 无请求体 → `RuleVersionResponseBody`（VOIDED） |
| | **新增** GET `/accounting-rule-bundles/v1` | `listRuleBundles()` | `RuleBundleListResponseBody{items:[{bundleCode, status, currentBundleVer?, currentEffectiveFrom?}]}`，按代码升序 |
| | GET `/{bundleCode}`、`/{bundleCode}/versions/{bundleVer}` | 不变（版本状态新增 VOIDED） | |

签名已于开发中途发给主会话（main）。

## 2. 改动文件

**API（paytech-ledger-api）**：`ManualAdjustmentApi`、`AccountingRuleBundleApi`、`PeriodCloseApi`、`TrialBalanceApi`（Header 参数、JavaDoc 删除“未接统一身份”说明）；`request/SubmitManualAdjustmentRequestBody`、`ReviewManualAdjustmentRequestBody`、`ReviewPeriodCloseRequestBody`、`StartPeriodReopenRequestBody`、`CreateRuleVersionRequestBody`（删 makerId / checkerId）；删除 `SubmitRuleVersionRequestBody`、`ReviewRuleVersionRequestBody`、`StartPeriodCloseRequestBody`；新增 `response/ManualAdjustmentResponseBody`、`response/RuleBundleListResponseBody`；`RuleVersionResponseBody` 状态注释加 VOIDED。

**Core（paytech-ledger-core）**：`RuleVersionStatus` 加 `VOIDED`；新增 `domain/ManualAdjustmentQuery`、`domain/view/ManualAdjustmentView`；`ManualAdjustmentRepository(+Impl)`、`ManualAdjustmentMapper(.xml)`（列表与计数）；`AccountingRuleBundleRepository(+Impl)`、`AccountingRuleBundleMapper(.xml)`（`findAll`）；`AccountingRuleBundleVerRepository(+Impl)`、`AccountingRuleBundleVerMapper(.xml)`（`findDueApproved`、`activate`）；`ManualAdjustmentService(+Impl)`（`pageAdjustments`）；`AccountingRuleService(+Impl)`（`findReplacedVersion`、`activateVersion`、`voidVersion`、`listBundles`，批准与生效共用 `switchCurrentVersion`）；`ApproveRuleVersionProcessor`（改用 `findReplacedVersion`）；新增 `ActivateRuleVersionProcessor`、`ActivateDueRuleVersionsProcessor`、`VoidRuleVersionProcessor` 及 `request/ActivateRuleVersionRequest`、`ActivateDueRuleVersionsRequest`、`VoidRuleVersionRequest`、`response/ActivateDueRuleVersionsResponse`。

**App（paytech-ledger-app）**：`pom.xml` 加 `paytech-xxl-job-starter`；新增 `ext/job/AccountingRuleVersionActivationJobHandler`；`ManualAdjustmentController`、`AccountingRuleBundleController`、`PeriodCloseController`、`TrialBalanceController` 及对应 Converter；`application.yml` 加 `xxl.job.executor.enabled: false` 与说明。

**构建**：`paytech/ledger/pom.xml` Enforcer 放开 `com.xuxueli:*` 与 `paytech-xxl-job-starter`（`paytech-compensation-event` 仍禁止）。`tools/arch-rules` 未改。

**数据库**：`docs/database/ledger.pdma.json`、`docs/database/sql/ledger-schema.sql`、测试 `init/ddl.sql` 中 `t_accounting_rule_bundle_ver.status` 注释加 `VOIDED`；新增 `docs/database/sql/changes/2026-10-20-finance-console-statuses.sql`（只有一条 `MODIFY COLUMN` 注释，可重复执行，含回滚说明）。

**测试（paytech-ledger-test）**：`MockTestBase.postAs`；基类 `AbstractManualAdjustmentApiTest`、`AbstractPeriodCloseApiTest`、`AbstractPeriodReopenApiTest`、`AbstractTrialBalanceApiTest`、`AbstractRuleBundleApiTest`（`assertPointer` 上移、`voidVersion`、`listBundles`）；Builder `SubmitManualAdjustmentRequestBuilder`、`RuleVersionRequestBuilder` 去掉编制人；`AccountingTestMapper`（`countAdjustments`、`updateRuleVersionEffectiveFrom`、`selectRuleVersion`）；`MockBillingFactApi` 补上卡 B 新增的 `searchBillingFacts`（记为意外调用，否则测试模块不能编译）；受影响用例改传 Header 并更新 `@see`；`LedgerModuleBoundaryTest` 允许 xxl-job-starter；`application-mock.yml` 加 `xxl.job.executor.enabled: false`。新增 `ListManualAdjustmentsTest`（4 例）、`ListRuleBundlesTest`（2）、`VoidRuleVersionTest`（5）、`auto/ext/job/AccountingRuleVersionActivationJobTest`（5）；`SubmitManualAdjustmentTest` 新增 Header 校验用例，`CreateRuleVersionValidationTest` 把“缺编制人字段”改为“Header 缺失 / 格式错误”。

## 3. 对应验收

| 项 | 实现 / 验证 |
|---|---|
| 需求 1 AC-01（列表筛选、分页稳定） | L1；`ListManualAdjustmentsTest`：只按账簿、期间 / 状态 / 编制人单独与组合筛选、分页、参数错误、账簿不存在 |
| 需求 1 AC-03（本人不能复核；可跳到分录） | 规则不变，原用例改传 Header 后通过；列表带 `journalEntryId` |
| 需求 1 / 2 / 3 的 AC-07 / R6 / R7（身份取自登录账号） | D1；接口不再接受请求体操作人；Header 缺失或格式错误 400（`SubmitManualAdjustmentTest`、`CreateRuleVersionValidationTest`、`VoidRuleVersionTest`、`StartPeriodReopenTest`） |
| 需求 2 R2（发起试算） | `runTrialBalance` 读 Header 并记操作日志；`RunTrialBalanceTest` 通过 |
| 需求 3 AC-01（规则包列表） | L2；`ListRuleBundlesTest` |
| 需求 3 AC-03（有草稿 / 待审不能新建；草稿可作废） | L3；`VoidRuleVersionTest`：编制人作废、作废后可新建、作废版本不能提交、重复作废、非编制人、非 DRAFT、参数错误；原有“已有草稿 / 待复核不能新建”用例不变（VOIDED 本就不在检查范围内） |
| 需求 3 AC-06（未来生效到时切换） | L4；`AccountingRuleVersionActivationJobTest`：到期生效（原版本 RETIRED、失效时间正确、指针与乐观锁版本）、未到期不动、重复执行幂等、`@XxlJob` 入口、生效时间早于当前版本时失败并保持 APPROVED |

## 4. 实际执行的检查

- `./mvnw -o -pl ledger/paytech-ledger-app -am -DskipTests compile`：通过。
- `./mvnw -o -pl ledger/paytech-ledger-test -am verify -Dtest='com/paytech/ledger/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dredis.key-prefix=ledger-fin3 -Dpaytech.local-environment.dir=…/local-environment`，环境 `LEDGER_TEST_DB_URL=…/mor_ledger_fin3`、`MOR_RABBITMQ_VHOST=/mor-fin3`、`REDIS_KEY_PREFIX=ledger-fin3`：**BUILD SUCCESS，56 个测试类，311 个用例，失败 0、错误 0、跳过 0**（含 `ArchitectureAutoTest`、`LedgerModuleBoundaryTest`、`LedgerApplicationTest`）。已确认写入的是 `mor_ledger_fin3`，`mor_ledger` 的最后更新时间仍是 2026-10-19。
- 测试环境：用 root 从 worktree 测试 DDL 新建 `mor_ledger_fin3`，并授予 `ledger_app` 该库的 SELECT/INSERT/UPDATE/DELETE；通过管理 API 新建 vhost `/mor-fin3`，并给 `MOR_RABBITMQ_USER` 全部权限。口令只从 `.env.local` 按键读入环境变量，没有打印。
- Schema 逐字节校验：在 scratchpad 写了一个只渲染 ledger 的脚本，调用 `scripts/pdmaner/generate-oe-client-schemas.mjs` 的 `renderSchema`，结果为 byte-identical。没有运行会重写全部服务 Schema 的生成器，以免覆盖卡 B 的 billing 文件。`node --test scripts/pdmaner/ledger-model.test.mjs` 5/5 通过；`node --test scripts/pdmaner/oe-client-model.test.mjs` 10/10 通过（按当时工作区的 billing 内容）。
- 没有运行：`npm --prefix requirements-site test`（不在本卡范围）；mor-test 部署与 SQL 执行（未授权）；仓库级 lint。

## 5. 偏离设计之处

1. **L2 不返回 `bundleName`**：`t_accounting_rule_bundle` 没有名称列，本卡又规定“不新增字段”。列表只返回 `bundleCode`、`status`、`currentBundleVer`、`currentEffectiveFrom`，由 Web 按代码显示中文名。已告知主会话。
2. **L1 账簿不存在**返回 `LEDGER_BOOK_NOT_FOUND`，与现有账户 / 流水列表一致。技术方案没有规定这一点。
3. **L3 编制人重复作废**直接返回当前结果，与 B2 的作废语义保持一致。技术方案只写了“只能制单人”。
4. **L4 失败处理**：某个到期版本的生效时间不晚于当前版本时（例如任务停机期间又有更新的版本立即生效），它会一直保持 APPROVED，每轮都失败并让 XXL 任务标红。本期没有自动作废或退役这类版本，需要人工处理。见第 6 节。
5. **D1 Header 格式校验**写在 API 参数注解上（`@Pattern`，由 Spring 方法校验执行，统一返回 400 `INVALID_REQUEST`）。基础资料命令的操作人是字符串，没有格式校验；这里内部类型是 `Long`，所以必须校验。
6. 发起关账，以及规则版本的提交 / 批准 / 驳回，去掉 `makerId` / `checkerId` 后请求体为空，因此整个删除了请求体参数和对应的 RequestBody 类。BFF 调用时不应再传请求体。

## 6. 未完成项 / 留给其他卡

- ledger 的 XXL 执行器环境变量、固定 IP，以及 mor-test 上的任务登记，由卡 C 负责。需要配置：`XXL_JOB_EXECUTOR_ENABLED=true`、`XXL_JOB_ADMIN_ADDRESSES`、`XXL_JOB_ADMIN_ACCESSTOKEN`、`XXL_JOB_EXECUTOR_APPNAME`（建议 `ledger-service`）、`XXL_JOB_EXECUTOR_IP`、`XXL_JOB_EXECUTOR_PORT`、`XXL_JOB_EXECUTOR_LOGPATH`、`XXL_JOB_EXECUTOR_EXCLUDEDPACKAGES`，任务 Handler 为 `accountingRuleVersionActivation`，每分钟执行一次。
- `paytech/ledger/README.md` 与 `docs/database/FINANCE_PHYSICAL_DECISIONS.md` 已被其他卡修改，本卡没有再动。README 模块表中 app 一行没有提到 XXL 执行器，交由文档卡统一处理。
- L4 的“过期且不能生效的 APPROVED 版本”目前只能人工处理（见第 5 节第 4 条），是否需要自动退役请业务确认。
- 规则版本作废后状态为 VOIDED，旧镜像无法解析该值。发布顺序必须是先执行注释 SQL、再上线新镜像；回滚前须确认表内没有 VOIDED 行。

## 7. 追加变更：已被取代的到期版本置为 RETIRED（team-lead 决定，2026-10-20）

本节取代第 5 节第 4 条，以及第 6 节“过期且不能生效的 APPROVED 版本只能人工处理”一项。

- **行为**：`accountingRuleVersionActivation` 遇到已到期、但生效时间不晚于当前 ACTIVE 版本生效时间的 APPROVED 版本时，判定它已被取代：不生效，置为 RETIRED，并记一条 WARN（规则集、版本号、原因：自身生效时间与当前版本号及其生效时间）。它不再计为失败，任务不再每轮标红。
- **数据**：在同一规则包资源锁下，一个事务内锁规则包行与版本行，再以 `status = APPROVED` 为条件更新为 RETIRED。`effective_to` 写为自身 `effective_from`，即零长度生效区间，表示从未生效。规则包当前指针与当前 ACTIVE 版本都不变。批准接口的行为不变：批准时如果生效时间不晚于当前版本，仍按原规则业务拒绝。
- **代码**：`AccountingRuleService` 新增 `findCurrentVersion`（`findReplacedVersion` 改为复用它）和 `retireSupersededVersion`；`AccountingRuleBundleVerRepository(+Impl)` 新增 `retireSuperseded`（条件 APPROVED，复用 `transit`）；`ActivateRuleVersionProcessor` 先判断是否已被取代，再决定生效；`ActivateDueRuleVersionsProcessor` 按返回状态分别计数；`ActivateDueRuleVersionsResponse` 新增 `retiredCount`；Job Handler 的日志摘要增加 retired 数。
- **测试**：`AccountingRuleVersionActivationJobTest` 用 `test_supersededVersionRetired` 替换原来的 `test_effectiveBeforeCurrentKeptApproved`。场景是：第 2 版批准为明天生效；第 3 版以一分钟前立即生效；再把第 2 版的生效时间改到第 3 版之前。断言 retired 1、activated 0、failed 0；第 2 版 RETIRED 且失效时间等于自身生效时间；第 3 版仍为 ACTIVE；指针指向第 3 版且乐观锁版本为 1；下一轮没有任何处理。到期生效、未到期不动、重复执行幂等与 `@XxlJob` 入口这 4 个用例保留。
- **重跑**：在相同命令与环境下（`mor_ledger_fin3` / `/mor-fin3` / `ledger-fin3`）重跑 ledger 全量测试：BUILD SUCCESS，56 个测试类，311 个用例，失败 0、错误 0、跳过 0；`AccountingRuleVersionActivationJobTest` 5/5 通过。
- **未覆盖**：生效阶段出现系统异常或并发冲突（failedCount > 0）的分支，目前没有专门用例。这类失败需要在测试侧注入故障才能触发，本次没有加。

## 8. 追加变更：控制台错误码（technique.md D4 修订，team-lead 要求，2026-10-20）

本节取代第 2 节 D4 中“不新增业务错误码”的旧说法。

- **枚举**：`paytech-ledger-api` `api/enums/LedgerErrorCode`（`CodeMessageEnum`，HTTP 400，常量之间空一行），在原有的 `OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN` 之后新增 14 个 Code，默认消息与 D4 表逐字一致：`SELF_REVIEW_FORBIDDEN`、`REVIEW_CONTENT_CHANGED`、`REVIEW_STATUS_INVALID`、`ADJUSTMENT_UNBALANCED`、`ADJUSTMENT_PERIOD_NOT_OPEN`、`ADJUSTMENT_ACCOUNT_INVALID`、`PERIOD_CLOSE_ORDER_INVALID`、`PERIOD_NOT_OPEN`、`PERIOD_REOPEN_NOT_ALLOWED`、`RULE_VERSION_IN_PROGRESS`、`RULE_VERSION_STATUS_INVALID`、`RULE_SUBMITTER_NOT_MAKER`、`RULE_LINE_INVALID`、`RULE_EFFECTIVE_FROM_INVALID`。类 JavaDoc 写明消费者：Operations BFF 在 `HttpServiceClientFactory` 登记并透传，Operations Web 按 Code 显示默认消息。`ManualAdjustmentApi`、`PeriodCloseApi`、`AccountingRuleBundleApi` 的类注释列出各自会返回的 Code。
- **抛出点**（均为 `new FailException(LedgerErrorCode.X)`，Problem detail 为默认消息）：
  - 人工调整提交（`SubmitManualAdjustmentProcessor`）：期间不存在或未 OPEN → `ADJUSTMENT_PERIOD_NOT_OPEN`；账户不存在、不属于账簿或不是 ACTIVE → `ADJUSTMENT_ACCOUNT_INVALID`；借贷不平衡 → `ADJUSTMENT_UNBALANCED`。
  - 人工调整复核（`ManualAdjustmentReviewValidator`，以及 `ApproveManualAdjustmentProcessor` 的非重放已处理分支）：状态 → `REVIEW_STATUS_INVALID`；同人 → `SELF_REVIEW_FORBIDDEN`；摘要不符 → `REVIEW_CONTENT_CHANGED`。
  - 发起关账（`StartPeriodCloseProcessor`）：非 OPEN → `PERIOD_NOT_OPEN`；前序未关 → `PERIOD_CLOSE_ORDER_INVALID`。
  - 发起重开（`StartPeriodReopenProcessor`）：非 CLOSED，或存在更晚的已关闭期间 → `PERIOD_REOPEN_NOT_ALLOWED`。
  - 关账 / 重开复核（`ApprovePeriodCloseProcessor`、`RejectPeriodCloseProcessor`）：状态 → `REVIEW_STATUS_INVALID`；同人 → `SELF_REVIEW_FORBIDDEN`；摘要不符 → `REVIEW_CONTENT_CHANGED`。
  - 规则版本：新建时有在途版本 → `RULE_VERSION_IN_PROGRESS`；新建时的行集校验（空、行号非正 / 重复、行代码重复、代码不在目录、科目不存在 / 不可过账 / 已停用 / 为控制科目），以及提交时某指令类型缺少借方或贷方 → `RULE_LINE_INVALID`；提交 / 作废非 DRAFT、批准 / 驳回非 PENDING_APPROVAL（`RuleVersionReviewValidator`）→ `RULE_VERSION_STATUS_INVALID`；提交 / 作废人不是编制人 → `RULE_SUBMITTER_NOT_MAKER`；规则版本同人复核 → `SELF_REVIEW_FORBIDDEN`；批准时生效时间不晚于当前版本（`AccountingRuleService.findReplacedVersion`）→ `RULE_EFFECTIVE_FROM_INVALID`。
- **保持原样**：409 并发冲突；各类“不存在”（调整、期间、关账流程、规则集、版本）；账簿未开放、调整币种不符、期间不属于账簿主体、金额精度；重开缺少已完成的原关账、重开冲回计划摘要不符；审批过账门禁（期间已关闭等，来自过账内核）；账户身份矩阵 `AccountIdentityRule`（与建账户共用，例如“余额桶只能为 NONE”、科目不允许该 Owner）；`PeriodCloseServiceImpl` 事务内的状态复验（并发竞态）。这些仍为 `BUSINESS_CHECK_FAILED`。
- **测试**：41 处原先断言 detail 片段的拒绝改为 `assertRejected(LedgerErrorCode.X)`，同时检查 code 与默认消息，涉及 Submit / Approve / Reject 人工调整、Start / Review 关账、Start / Review 重开、规则版本的新建校验、生命周期和作废。新增 `RuleVersionLifecycleTest.test_effectiveFromNotAfterCurrentRejected`（`RULE_EFFECTIVE_FROM_INVALID`，版本保持 PENDING_APPROVAL，指针不变）。删除 `StartPeriodReopenTest` 中已不再使用的 `notClosed` 辅助方法。
- **重跑**：在相同命令与环境下（`mor_ledger_fin3` / `/mor-fin3` / `ledger-fin3`）重跑 ledger 全量测试：BUILD SUCCESS，**312** 个用例（比上次多 1 个），失败 0、错误 0、跳过 0。之后只删掉了 `StartPeriodReopenTest` 文件末尾的一个空行，没有再次运行。
- **说明**：按要求用 `FailException(code)` 抛出，Problem detail 不再带单据主键等上下文。排查问题依赖 Request ID 与 MVC 请求日志。

## 暂停点（2026-10-20）

- **14 个错误码：已全部完成**，详见第 8 节：枚举、抛出点、测试断言、新增用例都已改完。
- **改到一半的文件：没有。** 暂停时没有正在进行的编辑，也没有正在运行的 Maven 测试。
- **编译与测试：** ledger 模块可以编译。最近一次全量测试（`mor_ledger_fin3` / `/mor-fin3` / `ledger-fin3`）为 BUILD SUCCESS，312 个用例，失败 0。之后只删掉了 `StartPeriodReopenTest` 文件末尾的一个空行，没有再次运行。
- **后续步骤：** 卡 A 本身没有遗留开发项。恢复后可选择先重跑一次 ledger 全量测试，确认上述空行改动；之后等待卡 D（BFF）按第 1 节签名与第 8 节错误码接入。提交前须询问用户。
