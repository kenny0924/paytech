# 任务卡 D 报告：Operations BFF

日期：2026-10-20。工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-fin3`，只改 `paytech/operations-console-bff/**`。没有提交、建分支、stash、回滚，也没有执行 `install`。

本卡由两个 agent 先后完成：前一个 agent 写好了全部转发、模型、看板和大部分测试后被强制停止；接手的 agent 补上了 billing 错误码登记、D4 错误码测试和 README 修正，并跑通全量测试。

说明：接手时 worktree 的改动已经全部在 index 中暂存（`git status` 第一列为 `M` / `A`），不是本卡做的。本卡没有操作 index。

## 0. 开工说明（WRITE_API_EXAMPLE 第 1 节）

- 已读：`00-common.md`、`D-bff.md`、`../technique.md` 第 2—5 节（D4 用 2026-10-20 修订版）、`A-report.md`（第 1、8 节）、`B-report.md`（API 签名、BillingFactErrorCode）、`C-report.md`（第 4 节路由清单、第 8 节注意事项）。
- 所属层：BFF `ext/rest` 下的 Controller 直接调用 `integration` Client，属于简单转发，不经过 Processor。唯一的聚合是 F1 看板，由 `service/processor/PeriodCloseBoardProcessor` 负责，只读。
- 本地原子写：没有。BFF 无状态、无数据库、不开事务。
- 事务外步骤：所有写操作都是对 ledger / billing 发一次同步 HTTP 调用，不重试；业务幂等由 Owner 负责。
- 第 11 节自查：Processor、Context、锁、事务、补偿各项不适用，原因是 BFF 只做转发，不持有业务事实。适用的几项：操作人只取自 `OperatorContext`；请求体严格解析；Query 按白名单校验；无体命令不发请求体；错误码只登记本 Owner 的合同 Code；超时映射为 504。测试情况见第 5 节。

## 1. 改动文件

**app**（`paytech-operations-console-bff-app`）
- `pom.xml`、上级 `operations-console-bff/pom.xml`：依赖 `paytech-billing-api`。
- `configuration/FinanceClientConfiguration`：
  - 新增 billing 工厂（`operations.billing-base-url`），登记 `BillingFactErrorCode`。
  - ledger 工厂登记 `LedgerSetupErrorCode` 和 `LedgerErrorCode`（包括 D4 新增的 14 个）。
  - 新增长耗时 ledger 工厂，供关账类调用使用。
  - `PeriodCloseApi` 有两个代理：`@Primary` 用普通超时，`longRunningPeriodCloseApi` 用长超时。
- `configuration/FinanceRequestConfiguration`：严格解析四个新增的 Models；三个多行请求体（人工调整提交、计费事实登记、规则版本新建）上限 65536 字节，其余写请求体上限 8192；未声明 RequestBody 的命令如果带了 Body，返回 400。
- `resources/application.yml`：`operations.billing-base-url`（`OPERATIONS_BILLING_BASE_URL`，默认 `http://127.0.0.1:18113`）；`operations.ledger-long-running-read-timeout`（`OPERATIONS_LEDGER_LONG_RUNNING_READ_TIMEOUT`，默认 `9s`）。
- Controller：
  - 新增 `ext/rest/FinanceManualAdjustmentController`、`FinanceBillingFactController`、`FinancePeriodCloseController`、`FinanceRuleBundleController`。
  - `FinanceLedgerBookController` 新增发起试算。
- Client：新增 `integration/Finance{ManualAdjustment,BillingFact,PeriodClose,RuleBundle}Client` 及其 `impl`；`FinanceLedgerBookClient(Impl)` 新增 `runTrialBalance`。
- 模型：新增 `service/model/Finance{ManualAdjustment,BillingFact,PeriodClose,RuleBundle}Models`。
- 看板：`service/processor/PeriodCloseBoardProcessor`、`request/PeriodCloseBoardRequest`。
- `README.md`：财务控制台三项说明。

**test**（`paytech-operations-console-bff-test`）
- 新增 `auto/Finance{ManualAdjustment,BillingFact,PeriodClose,RuleBundle}BrowserAutoTest`。
- 修改 `FinanceLedgerBookBrowserAutoTest`（发起试算）、`OperationsConsoleBffApplicationAutoTest`（路由清单）、`OperationsHttpBodyNamingAutoTest`。
- 基类与配置：`FinanceBrowserAutoTestBase`、`OperationsBffHttpTestBase`、`OperationsBffTestConfiguration`（billing Simulator 地址；长耗时超时在测试中设为 5 秒）。
- Simulator：新增 `BillingOwnerSimulator`；修改 `LedgerOwnerSimulator`、`PlatformOperationsSimulator`（`FINANCE_APPROVE_PERMISSION_ID` 与新路由权限）。
- 边界 JSON：新增 `finance-adjustment-boundary.json`（M1—M5、B1—B5）、`finance-period-close-boundary.json`（P1—P6）、`finance-rule-bundle-boundary.json`（A1—A8）；`finance-ledger-boundary.json` 新增发起试算一行。

## 2. BFF 对 Web 暴露的最终路径（共 26 条，与 C-report 第 4 节逐条一致）

外部路径为 `/api/operations` 加下表路径。下表中的「操作人」指：BFF 从会话的 `OperatorContext` 取出操作人，以 `X-Gateway-Actor-Subject` 和 `X-Gateway-Session-Id` 两个 Header 传给上游。页面不传 makerId 或 checkerId；请求体里出现这两个字段属于未知属性，返回 400。

### 人工调整（ledger）

| Method | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/finance/manual-adjustments/v1` | Query：`ledgerBookId`（必填），可选 `accountingPeriodId`、`status`（SUBMITTED / POSTED / REJECTED）、`makerId`、`page`、`size` | `{items, page, size, total}`。items 元素：`manualAdjustmentId, adjustmentNo, ledgerBookId, accountingPeriodId, currency, reasonCode, status, totalDebit, totalCredit, makerId, submittedTime, journalEntryId?` |
| POST | `/finance/manual-adjustments/v1` | 操作人；Body：`{ledgerBookId, accountingPeriodId, currency, reasonCode, fileId?, fileContentHash?, lines:[{accountId, entrySide, amount, lineDescription?}]}`，上限 64 KiB | 201 `{manualAdjustmentId, adjustmentNo, contentHash, status}` |
| GET | `/finance/manual-adjustments/v1/{manualAdjustmentId}` | | `{manualAdjustmentId, adjustmentNo, ledgerBookId, accountingPeriodId, currency, reasonCode, fileId, fileContentHash, totalDebit, totalCredit, contentHash, status, makerId, submittedTime, lines:[{lineNo, accountId, entrySide, amount, lineDescription}], approvals:[{approvalRole, actorId, decision, approvedContentHash, decisionTime}], journalEntryId, journalSequence, postingDate}` |
| POST | `.../{manualAdjustmentId}/approve`、`/reject` | 操作人；Body：`{approvedContentHash}` | `{manualAdjustmentId, status, journalEntryId, journalSequence, postingDate}` |

### 计费事实（billing `/billing/v1/facts`）

| Method | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/finance/billing-facts/v1` | 转发到 `/billing/v1/facts/search`。Query：`entityNo`、`currency`（必填），可选 `clientId`、`status`（DRAFT / APPROVED / SUPERSEDED / VOIDED）、`factType`、`createdFrom`（含）、`createdTo`（不含，ISO Instant）、`page`、`size` | 分页，元素：`factId, factType, sourceReference, clientId, status, grossAmount, makerId, checkerId, createTime, approvedTime` |
| POST | `/finance/billing-facts/v1` | 操作人；Body：`{factType, parentFactId?, supersedesFactId?, clientId, entityNo, localPartnerId?, financeEntityId?, currency, refs:[{refType, refId}], lines:[{componentType, payerRole, payerId, payeeRole, payeeId, amount, direction, targetBucket?}]}`，上限 64 KiB。BFF 固定补 `sourceSystem=OPERATIONS`，并为每次请求生成 `sourceReference="OPS-"+UUID`；页面如果自带这两个字段，返回 400 | 201，完整事实（见下一行） |
| GET | `/finance/billing-facts/v1/{factId}` | | `{factId, factVersion, supersedesFactId, parentFactId, factType, originType, status, sourceSystem, sourceEventKey, sourceVersion, sourcePayloadHash, businessTime, recordedTime, balanceAvailableTime, clientId, entityNo, localPartnerId, financeEntityId, currency, grossAmount, makerId, checkerId, approvedTime, publishedAt, pricing{…16 个字段}, refs[{refType, refId}], lines[{lineNo, componentType, payerRole, payerId, payeeRole, payeeId, direction, postedAmount, targetBucket, basisAmount, rateValue, rawAmount, sourceRuleType, sourceRuleVersionId, sourcePriceId, sourceRuleComponentId, sourceEvidence}], links[{sourceLineId, targetLineId, linkType, linkedAmount}]}` |
| POST | `.../{factId}/approve`、`/void` | 操作人；**无 Body** | 完整事实；作废后 `status=VOIDED` |

注意：BFF 每次登记都生成新的来源引用，所以页面重复点击会产生两条草稿。页面需要自己防止重复提交。

### 期间关账（ledger）

| Method | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/finance/period-close/v1/board` | Query：`fiscalCalendarId`（必填，只允许这一个参数） | `{fiscalCalendarId, periods:[{period:<AccountingPeriod 与 97405 相同>, latestClose:<PeriodClose 或 null>}]}`。期间顺序与 ledger 相同（按财年、期号升序）；`latestClose` 是关账列表第 1 页的第一条；任一上游调用失败，整页失败 |
| GET | `/finance/accounting-periods/v1/{accountingPeriodId}/closes` | Query：`page`、`size` | 分页 PeriodClose：`periodCloseId, periodId, closeNo, closeType, status, makerId, checkerId, closeHash, failedStepType, reopensCloseNo, reopenReasonCode, reopenRequestRef, createTime` |
| GET | `.../closes/{closeNo}` | | PeriodClose 加 `steps[{stepNo, stepType, status, resultHash, completedTime}]`、`books[{ledgerBookId, asOfSequence, finalTrialRunId, postingInstructionId, status}]`、`approvals[{approvalRole, actorId, decision, approvedCloseHash, decisionTime}]` |
| POST | `.../closes` | 操作人；**无 Body** | 201 PeriodClose（失败时 `status=FAILED`、`failedStepType` 有值、`closeHash` 为 null） |
| POST | `.../reopens` | 操作人；Body：`{reasonCode, requestRef}` | 201 PeriodClose |
| POST | `.../closes/{closeNo}/approve`、`/reject` | 操作人；Body：`{approvedCloseHash}` | PeriodClose |
| POST | `/finance/ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances` | 操作人；**无 Body** | 201 `{trialBalanceRunId, ledgerBookId, periodId, runNo, asOfSequence, status, totalDebit, totalCredit, contentHash, lineCount, …}`（与 91407 列表元素相同） |

### 记账规则包（ledger）

| Method | 路径 | 请求 | 响应 |
|---|---|---|---|
| GET | `/finance/accounting-rule-bundles/v1` | 不接受 Query | `{items:[{bundleCode, status, currentBundleVer?, currentEffectiveFrom?}]}`，没有 `bundleName`，见 A-report 第 5 节 |
| GET | `.../{bundleCode}` | | `{bundleId, bundleCode, status, currentBundleVer, versions:[{versionId, bundleVer, status, effectiveFrom, effectiveTo}]}` |
| GET | `.../{bundleCode}/versions/{bundleVer}` | | `{versionId, bundleCode, bundleVer, effectiveFrom, effectiveTo, status, makerId, checkerId, approvedTime, lines:[{ruleLineId, instructionType, lineNo, lineCode, amountSourceCode, ownerRole, accountCode, balanceBucket, entrySide, requiredFlag}]}`；status 包括 VOIDED |
| POST | `.../{bundleCode}/versions` | 操作人；Body：`{effectiveFrom, lines:[{instructionType, lineNo, lineCode, amountSourceCode, ownerRole, accountCode, balanceBucket, entrySide, requiredFlag}]}`，上限 64 KiB | 201 RuleVersion |
| POST | `.../versions/{bundleVer}/submit`、`/approve`、`/reject`、`/void` | 操作人；**无 Body** | RuleVersion |

**无 Body 的命令**（billing 批准 / 作废、发起关账、发起试算、规则版本提交 / 批准 / 驳回 / 作废）：Web 不要发送任何请求体，`{}` 也不行。BFF 收到请求体时返回 400；即使 BFF 放行，Gateway 也会按 Body 上限 0 拒绝。

## 3. Web 需要映射的错误码

| 来源 | Code | 默认消息（Problem `detail`） | 出现的端点 |
|---|---|---|---|
| ledger | `SELF_REVIEW_FORBIDDEN` | 制单人不能复核自己提交的单据 | 人工调整、关账 / 重开、规则版本的复核 |
| | `REVIEW_CONTENT_CHANGED` | 单据内容已变化，请刷新后重新复核 | 人工调整、关账的复核 |
| | `REVIEW_STATUS_INVALID` | 单据不在待复核状态 | 人工调整、关账的复核 |
| | `ADJUSTMENT_UNBALANCED` | 分录借贷不平衡 | 人工调整提交 |
| | `ADJUSTMENT_PERIOD_NOT_OPEN` | 会计期间未开放 | 人工调整提交 |
| | `ADJUSTMENT_ACCOUNT_INVALID` | 账户不属于该账簿、已停用或不可记账 | 人工调整提交 |
| | `OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN`（既有） | 以 `LedgerErrorCode` 为准 | 人工调整提交 |
| | `PERIOD_CLOSE_ORDER_INVALID` | 之前的期间尚未关账 | 发起关账 |
| | `PERIOD_NOT_OPEN` | 期间不是开放状态，不能关账 | 发起关账 |
| | `PERIOD_REOPEN_NOT_ALLOWED` | 只能重开最近一期已关账期间 | 发起重开 |
| | `RULE_VERSION_IN_PROGRESS` | 规则包已有草稿或待审批版本 | 新建版本 |
| | `RULE_VERSION_STATUS_INVALID` | 版本当前状态不允许该操作 | 提交 / 批准 / 驳回 / 作废 |
| | `RULE_SUBMITTER_NOT_MAKER` | 只有制单人可以提交或作废草稿 | 提交 / 作废 |
| | `RULE_LINE_INVALID` | 规则行不合法：科目、代码或借贷配置不符合要求 | 新建 / 提交 |
| | `RULE_EFFECTIVE_FROM_INVALID` | 生效时间必须晚于当前生效版本 | 批准 |
| ledger（既有 `LedgerSetupErrorCode`） | `LEDGER_BOOK_NOT_FOUND`、`FISCAL_CALENDAR_NOT_FOUND` 等 | 枚举默认消息 | 人工调整列表、看板 |
| billing | `SELF_REVIEW_FORBIDDEN` | 制单人不能复核自己提交的计费事实 | 批准 |
| | `FACT_NOT_DRAFT` | 只能批准或作废草稿计费事实 | 批准 / 作废 |
| | `FACT_LINE_INVALID` | 明细行不合法：金额组件、收付方、目标余额桶或金额精度不符合规则 | 登记 |
| | `FACT_SUPERSEDE_INVALID` | 被替代的计费事实不存在或不可替代 | 登记 / 批准 |
| 公共 | `BUSINESS_CHECK_FAILED`（400，detail 固定为「业务校验未通过」） | 显示通用提示，并保留 Request ID | 其余业务拒绝 |
| 公共 | `INVALID_REQUEST` 400 / `VALIDATION_ERROR` 422（带字段路径） / `CONCURRENT_REQUEST` 409 / 413 | | 参数、Body 与并发 |
| BFF | `DEPENDENCY_TIMEOUT` 504 / `DEPENDENCY_UNAVAILABLE` 503 / 上游响应不合法 502 | | 所有端点 |

`SELF_REVIEW_FORBIDDEN` 在 ledger 和 billing 中都有，Code 相同，默认消息不同。Web 直接显示 `detail` 即可，也可以按页面区分。

## 4. 超时（第 5 项）

- 发起关账、发起重开、关账 / 重开审批走 `longRunningPeriodCloseApi`，读取超时 `operations.ledger-long-running-read-timeout`，默认 9 秒。启动时校验取值必须不小于 4 秒、且小于 10 秒。其他调用（包括关账驳回、列表、详情和看板）仍为 4 秒，连接超时 2 秒。
- 取 9 秒而不是建议的 60 秒，原因是 C-report 的 Gateway 路由响应超时统一为 10000 ms。BFF 的超时必须短于 Gateway，才能由 BFF 先返回 504 `DEPENDENCY_TIMEOUT`，而不是被 Gateway 断开连接。
- 耗时依据：ledger 测试报告中 Start / Review 关账与重开的用例（含夹具准备）单例最长约 0.43 秒，远低于 9 秒。目前没有 mor-test 实际数据量下的耗时数据。
- **Gateway 路由不需要修改**：101404、101405、101406 维持 10000 ms 即可。如果以后 mor-test 实测关账超过约 8 秒，需要同时提高这三条路由的超时和本配置项，并放宽 `validatedLongRunning` 的上限。

## 5. 实际执行的命令与结果

在 `paytech/` 下执行，设置 `JAVA_HOME=…/jdk-25.jdk`：

1. `./mvnw -o -q -pl operations-console-bff/paytech-operations-console-bff-test -am test-compile -DskipTests`：通过。
2. `./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am verify -Dtest='com/paytech/operations/console/bff/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`
   - 接手时首次运行（尚未补错误码）：BUILD SUCCESS，1693 例，0 失败。
   - 补完后运行：**BUILD SUCCESS，1725 例，失败 0、错误 0、跳过 0**。
   - 本需求相关的测试类：FinanceManualAdjustment 28、FinanceBillingFact 27、FinancePeriodClose 35、FinanceRuleBundle 35、FinanceLedgerBook 42、OperationsConsoleBffApplication 8、OperationsHttpBodyNaming 36。

本次追加的测试：
- 四个 BrowserAutoTest 各增加 `test_businessRuleViolation` 参数化用例，按「端点 × 错误码」组合 35 例，覆盖全部 14 个 D4 ledger Code、既有的 `OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN` 和 4 个 billing Code。每例断言 400、Code、默认消息、Request ID，并确认响应中不含 Owner 原文和 Owner Request ID。
- 保留公共 `BUSINESS_CHECK_FAILED` 和 409 的用例。

BFF auto 测试只使用 loopback Simulator，不连接 MySQL、Redis 或 RabbitMQ，因此没有使用本需求的 fin3 专用库。

**没有运行**：Web 的类型检查与测试（属于卡 E）；`npm --prefix requirements-site test`（本卡没有改动）；mor-test 部署；与真实 ledger / billing 进程的联调。

## 6. 偏离与注意事项

1. 长耗时超时为 9 秒，不是卡片建议的 60 秒。原因见第 4 节，Gateway 不需要修改。
2. 计费事实登记由 BFF 生成 `sourceReference`，technique 第 3 节写的是「由页面生成」，这里按任务卡 D 的写法实现。代价是同一页面重复提交会产生新的草稿，不会返回原事实，因此 Web 必须自己防重复提交。如果希望由页面生成，以便重试时得到原事实，需要把它改为页面必填字段，并同步修改 BFF 与 Gateway 的 Body 白名单。
3. 看板读取期间列表时用 `size=200` 分页读完，再对每个期间取关账列表第 1 页（size 10）。请求次数为 1 加期间数，日历最多 24 期。
