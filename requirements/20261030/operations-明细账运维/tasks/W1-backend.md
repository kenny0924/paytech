# W1 任务卡：ledger / account / 运营 BFF / Platform 注册（后端）

## 工作位置与边界

- 只在工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-plops`（基于 main `20ffcd9f`）中修改。**不要**修改主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`（其他会话在用）。
- 只改：`paytech/ledger`、`paytech/account`、`paytech/operations-console-bff`、`paytech/platform-support`（只限初始化 SQL 与 `console-resource-capabilities.json`、相关一致性测试）、`paytech/tools/arch-rules`（如需要）、两个服务的 README。`operations-console-web` 归 W2，不要改。
- **不执行任何 SQL**、不连接任何数据库写入；注册块 148 只写 SQL 文件，由主会话审阅后执行。
- 不碰 mor-test（所有 `mor-test-*` 容器），不提交、不推送、不建分支。
- 测试只跑受影响模块，不跑整个 reactor（共享本机库会被其他会话的测试清空）。

## 必读（开工前实际读取）

1. 需求：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/operations-明细账运维/requirement.md`
2. 技术方案（**以第 6—11 节为准**）：同目录 `technique.md`
3. `agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（第 11 节逐项自查）、`agent-docs/UNIT_TEST_GUIDE.md` 与 `agent-docs/examples/UNIT_TEST_EXAMPLE.md`
4. 现有实现：`paytech/ledger/.../service/processor/StartPeriodCloseProcessor.java`（`checkBookPostingProgress` / `checkFactsApplied` / `checkStreamConsumed`）、`PostingStreamCatchUpProcessor.java`、`ConsumedEventRepository`；`paytech/account/.../RebuildPostingStreamsProcessor.java`、`PostingStreamRebuildServiceImpl.java`；BFF `configuration/FinanceClientConfiguration.java`、`FinanceRequestConfiguration.java`、`OperationsErrorStatusResolver.java`、`integration/FinanceSubledgerClient*`、`FinanceBillingFactClient*`、`ext/rest/OperatorContextArgumentResolver.java`；注册块参照 `requirements/20261029/tax-结账算税与事件登记/release/sql/mor_platform-block-146*.sql` 与 `platform-support-initial-data.sql` 的块 143 / 144。

开工前在报告开头简述：已读路径、各接口所属 Processor / Service、哪些是事务外步骤（本需求无本地原子写入，只有 account 重建沿用既有短事务）。

## 任务

### T1 ledger

- 从 `StartPeriodCloseProcessor` 抽出 `PostingProgressService`（interface + `*Impl`，Domain Service），负责按账簿计算两段进度，返回领域结果（不是 DTO）；关账第 3 步改为调用它，**关账行为和摘要格式不变**，回归既有关账测试。
- `ConsumedEventRepository` 新增读取"版本 + `update_time`"的方法（SQL 写在 Mapper XML）。
- 新增 `paytech-ledger-api` 合同 `PostingProgressQueryApi`：`GET /posting-progress/v1/ledger-books/{ledgerBookId}`，响应 `PostingProgressResponseBody`，字段、`status` 取值（`CONSISTENT` / `BEHIND` / `UNAVAILABLE`）、不可用语义按 technique 7.1。账簿不存在用 ledger 已有错误码（核对实际名称，写进报告）。
- `PostingStreamCommandApi` 类注释删除"本期不接控制台"，改为经运营控制台"明细账运维"由有执行权限的运营账号触发。
- auto 测试（真实 HTTP 入口，billing / account 用测试 HTTP 端点返回 DTO）：一致、事实段落后、流段落后、billing 不可用、account 不可用、账簿不存在、Finance Entity 账簿（含 OE 流）。

### T2 account

- 重建结果新增 `changedAccountCount`（technique 7.2）：在每条流重算的事务内比较各桶新旧余额并计数；`StreamRebuildResult`、`PostingStreamsRebuildResponseBody` 加字段；INFO 日志输出该数。
- auto 测试：数据一致时为 0；用 TestMapper 直接改某账户余额后重建为 1 且余额恢复；无过账流仍为 `STREAM_NOT_FOUND`。

### T3 运营 BFF

- 新增 B1—B4（technique 7.3）：路径、权限标注、请求校验（`streamId` 正则 + `AccountPostedPayload.parseAggregateId`）、响应字段、错误透出。
  - B1 用 Foundation `AbstractProcessor`：先调 ledger，再对 `BEHIND` 的流**串行或受限并发**调用 account 批次列表（`fromSequence = 已消费 + 1`，`limit = 1`）取 `stuckPosting`；单条失败置 `null`，不影响整体。ledger 失败 → 503。
  - B2 转发；`limit` 只允许 20 / 50 / 100 / 200，默认 50；批次摘要不含流水，带 `entryCount`。
  - B3 追平：`maxBatches = 200`，按 ledger 合同带 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`（来自 `OperatorContext`）；使用已有的 ledger 长耗时读超时客户端。
  - B4 重建：`operatorId = OperatorContext.accountId`；account 调用也需要长耗时超时（account 目前只有普通超时，按 `FinanceClientConfiguration` 现有方式增加一个，**不改变其他 account 调用的超时**）。
- 在三个现有客户端上扩展方法，不新建配置项（除 account 长耗时超时如确需新属性，沿用 `operations.*` 命名并给默认值）。
- 错误码：account `AccountPostingErrorCode` 注册与状态映射；ledger 追平错误沿用已注册的 `LedgerErrorCode`，逐个核对 400 / 409 / 503 能正确透出 `code` + `detail`。
- 测试：沿用 `OperationsBffHttpTestBase` 与 Finance 模拟服务，覆盖成功、ledger 400 / 409 / 503、BFF 超时 504、stuckPosting 单条失败、权限无关的参数校验；补边界清单 JSON（参照 `finance-*-boundary.json`）。

### T4 Platform 注册块 148

- 写入前用 `grep` 再次确认 `900000000000148` 未被占用（主工作区、本工作树、`requirements/**/db`、`requirements/**/release/sql`）；被占用则停下报告。
- 按 technique 第 8 节写六件套（precheck / 主块休眠写入 / activate / deactivate / verify / rollback），放在 `requirements/20261030/operations-明细账运维/db/platform-support/`（在**主工作区**的需求目录下写这六个 SQL 文件即可，这是唯一允许写主工作区的位置），同时把主块追加到工作树的 `platform-support-initial-data.sql` 末尾（与块 144 同风格）。
- 限流取值照抄同形态现有路由；POST 路由的 PRINCIPAL 限流收紧为 10 / 分钟 + 并发 1。
- `console-resource-capabilities.json` 新增 `OPERATIONS_FINANCE_POSTING_OPS`（动作 `OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP`、`OPERATIONS_FINANCE_POSTING_OPS_REBUILD`）。
- 跑 BFF 中"注册 SQL 与 Controller 一致性"类测试（参照 `FileRecordRegistrationAutoTest`），新路由要能对上 BFF Controller。

## 构建与测试

- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Maven 离线：`./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ ...`
- 测试用 `package`（不要只用 `test`），`-pl <模块> -am`，环境变量从 `local-environment/.env.local` 只加载所需键（MySQL / RabbitMQ / Redis，含 `MOR_RABBITMQ_USER/PASSWORD/VHOST`），**不要打印密码**。
- 本机数据库只能用 `mor-local-mysql-1`（端口 13306），绝不连接 `mor-test-*`。
- 不要往共享 `~/.m3` 执行 `install`（会污染其他会话）；如必须安装 api 模块供 BFF 编译，在报告中写明并尽量用 `-am` 一起构建代替。

## 交付

完成后在工作树根目录写 `REPORT-W1.md`：已读路径、改动文件清单、每个验收项（AC-01—AC-10）对应的实现与测试、执行的命令与结果（通过 / 失败数）、偏离方案之处、未完成项。不要只说"已完成"，要有命令输出摘要。
