# W1 报告（后端，2026-10-31，主会话据代理回报整理）

代理回报"无法写报告文件"，以下为其回报整理。实现由 W1b 完成（复用旧 W1 的 ledger / account 部分与其 15:46 的 BFF 客户端改动）。

## 调用链与事务

- ledger 进度：`PostingProgressQueryController` → `GetPostingProgressProcessor` → `PostingProgressService(Impl)`；关账第 3 步改为调用同一服务，摘要格式不变。
- account 重建：沿用 `RebuildPostingStreamsProcessor` → `PostingStreamRebuildServiceImpl`。
- BFF：B1 `FinancePostingOpsController` → `PostingProgressProcessor`；B2—B4 经 `FinanceSubledgerClient` / `FinanceLedgerBookClient` 转发。
- 无新增本地原子写入；billing / account 在事务外读取；已消费序号每流一个短事务；重建沿用每流短事务。

## 验收映射

| AC | 实现 | 测试 |
|---|---|---|
| AC-01 | ledger `factSegment`，与关账同一判定 | `GetPostingProgressTest`；BFF 用例 |
| AC-02 | 流清单来自 account 进度，财务主体账簿含关联 OE 流 | `test_financeEntityBookIncludesOperatingEntityStream` |
| AC-03 | 每流已消费序号、落后数、状态；BFF 串行补 `stuckPosting`（limit 1，单条失败为 null） | ledger 流用例；BFF `test_stuckPostingFailureIsolated` |
| AC-04 | B2：limit 20 / 50 / 100 / 200，默认 50；摘要带 `entryCount`；校验流与序号 | BFF 用例与参数校验 |
| AC-05 | 重建事务内比较得出 `changedAccountCount`，写 INFO 日志；`operatorId` 为会话 Account ID | `RebuildPostingStreamsTest`（篡改后 1，一致时 0） |
| AC-06 | 400 `STREAM_NOT_FOUND`、409 冲突 | account 用例；BFF `test_rebuildRejections` |
| AC-07 | B3 `maxBatches=200`、操作人请求头、长耗时超时 | BFF 用例、`test_commandTimeout` |
| AC-08 / 08a | 400 / 409 时把 ledger 的 `code` + `detail` 透给页面；503 透出状态不带 ledger 文本 | BFF `test_catchUpRejectionShowsOwnerDetail` |
| AC-09 | 查看 `FINANCE_ACCOUNTING_READ`，执行 `FINANCE_SUBLEDGER_MANAGE` | BFF `test_executionNeedsSubledgerManage`、`PostingOpsRegistrationAutoTest` |
| AC-10 | billing / account 不可用时 ledger 仍 200、对应段 `UNAVAILABLE`；ledger 自身失败 BFF 整页失败（503 / 400 `LEDGER_BOOK_NOT_FOUND`，不调 account） | ledger `test_billingUnavailable`、`test_accountUnavailable`、`test_bookNotFoundAndInvalid`；BFF `test_progressSegmentUnavailable`、`test_progressLedgerFailure` |
| AC-11 | 不在本卡（阶段 5 本机联调） | — |

错误码核对：账簿不存在为 `LedgerSetupErrorCode.LEDGER_BOOK_NOT_FOUND`（400）；ledger 追平错误是通用码（`BUSINESS_CHECK_FAILED` 400、`CONCURRENT_REQUEST` 409、`SERVICE_UNAVAILABLE` 503），因此追平客户端需要 `withOwnerProblemDetail()`；account `STREAM_NOT_FOUND` 已注册，默认 400，`OperationsErrorStatusResolver` 未改。

## 命令与结果（代理执行）

公共：JDK 25；只导出 `MOR_REDIS_PASSWORD`、`MOR_RABBITMQ_*`（未打印）；`PAYTECH_REDIS_PASSWORD`；库为 `mor-local-mysql-1`；`./mvnw -o -q -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`；未 install。

| 范围 | 结果 |
|---|---|
| 编译 ledger-app、account-app、ops-bff-test（`-DskipTests package -am`） | 成功 |
| ledger（progress / close / reopen / CatchUp） | 60 / 60 |
| account（posting） | 8 / 8 |
| 架构测试 ledger + account | 2 / 2 |
| BFF（Finance*、*Registration*、OperationsLedgerUnavailableAutoTest、OperationsBrowserBoundaryAutoTest） | 595 / 595（新增 FinancePostingOpsBrowserAutoTest 24、PostingOpsRegistrationAutoTest 6） |
| 合计 | 665 / 665 |

## 主会话复跑（2026-10-31）

| 范围 | 结果 |
|---|---|
| Web `npx tsc --noEmit` | 通过 |
| Web `npm test` | 1683 / 1683 |
| BFF `Finance*AutoTest` + `PostingOpsRegistrationAutoTest` | 555 / 555（ERROR 日志来自故意构造的非法上游响应用例） |
| ledger progress / close / CatchUp + 架构 | 61 / 61 |
| account posting + 架构 | 9 / 9 |

## 偏离方案

1. 块 148 请求头 16、Guard 24（共 51 行），按真实模板，已接受。
2. 删除 `ledgerLastConsumedTime`（technique 7.1 修订），任务卡中"`ConsumedEventRepository` 新增读取"未做。
3. 卡住批次按流串行读取（任务卡允许）。
4. B1 调 ledger 使用长耗时超时（ledger 内部依次读 billing、account）。
5. 只有追平和重建客户端透出 Owner 错误详情，其余财务调用不变（既有测试确认）。
6. 块 148 中查看权限写字面量 `900000000000097101`（便于注册一致性测试校验）。

## 未完成 / 待后续

- 块 148 的 Gateway 快照离线编译与 4 MiB 大小核对（需候选 Platform JAR，阶段 6）。
- 测试模拟器的会话 Account ID 为 `account_10001`（非十进制），真实 Platform 为十进制，满足 account `operatorId` 校验；BFF 本身不校验（与既有 `ChannelCostClientImpl` 一致）。
- 阶段 5 本机联调：AC-11、追平 200 批耗时实测（超过 7 秒降到 100）。

## 文件

见工作树 `git status`：ledger（api 3 新 + 1 改、app 2 新、core 5 新 + 5 改、test 1 新 + 2 改、README）；account（6 改 + README）；BFF（5 新：Controller、Models、Processor、Request、两测试 + 边界 JSON；改 FinanceClientConfiguration、FinanceRequestConfiguration、两个客户端及实现、application.yml、测试基类、PlatformOperationsSimulator、README）；Platform（初始化 SQL 末尾块 148、`console-resource-capabilities.json`）；主工作区需求目录 `db/platform-support/` 六件套。
