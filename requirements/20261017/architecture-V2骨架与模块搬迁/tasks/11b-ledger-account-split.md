# 任务卡 11b · 账户与会计分库：`t_account` 拆表、双消费 `EconomicFactRecorded`、关账一致性

- 分支：`refactor/step-11b-ledger-account-split`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step11b`；起点：`refactor-main`（07、08、11a 合入后，主会话填写）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 11b 步；设计 [04-finance-account-ledger-split.md](../design/04-finance-account-ledger-split.md) §3–§9；决定 D05、D16、D36（修订）、D37。
- **这是阶段 0 两处业务逻辑重做之一**：余额维护从"过账事务内同步更新"改为"账户独立消费经济事实"。逐条按 04 文档实现，任何偏差先在报告写明再做。

## 1. 输入文档（先完整读取）

- [design/04-finance-account-ledger-split.md](../design/04-finance-account-ledger-split.md) 全文。
- [design/08-messaging-module.md](../design/08-messaging-module.md)、[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) §2.5（第 11 步的测试改写与新增 ≥ 12 例一致性测试）、§3.1 account / ledger 行。
- [analysis/finance.md](../analysis/finance.md) §3、§4、§9（六处事务、跨库 SQL、热点）。
- 第 07 步报告（共库过渡清单、第 11b 步需改的跨库读清单、`TransactionPostingService` DTO）、第 11a 步报告（`EconomicFactRecorded` Payload）、第 08 步报告。
- 现有 `paytech/ledger/`、`paytech/account/`：`JournalPostingServiceImpl`、`TransactionPostingServiceImpl`、`ApproveManualAdjustmentProcessor`、`StartPeriodCloseProcessor`、`ApprovePeriodCloseProcessor`、`TrialExceptionServiceImpl`、`PeriodCloseServiceImpl`、`AccountMapper.xml`、`JournalLineMapper.xml`、`PeriodCloseMapper.xml`、`BalanceQueryServiceImpl`、`AccountIdentityRule`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md`。

## 2. 改动清单（逐条引用 04 文档）

1. **表变更（04 §3，纸面 + 测试 DDL，不执行）**：ledger `t_account` → `t_ledger_account`，删除 `debit_total`、`credit_total`、`net_balance`、`last_journal_sequence`、`balance_version`；account 库 `mor_account` 新建 `t_account_balance`、`t_balance_applied_fact`、`t_balance_movement`、`t_consumed_event`、`t_consumed_aggregate_version`、`t_compensation_event`、`t_balance_discrepancy`；PDManer 两个模型同步；两份 Schema SQL 与测试 DDL 同步；`table-ownership.yaml` 重新生成。
2. **过账内核（04 §4 第 1 行）**：`JournalPostingServiceImpl#post` 不再锁账户、不再更新余额；校验账户存在且 ACTIVE；其余（期间、序号、平衡、分录、指令 POSTED）不变。锁顺序中去掉账户锁。
3. **自动记账（04 §4 第 2 行）**：`TransactionPostingServiceImpl` 成为 ledger 的 `@DomainEventHandler(eventType="EconomicFactRecorded", strictOrder=false)` 消费者：按 `fact_id + fact_version` 幂等（`t_consumed_event`），按规则包生成指令并过账，一个 ledger 本地事务；`resolveAccount` 开科目账户仍在 ledger。
4. **人工调整（04 §4 第 3 行）**：`SubmitManualAdjustmentProcessor` / `ApproveManualAdjustmentProcessor` 校验调整行涉及的账户 `owner_type ∈ {CLIENT, LOCAL_PARTNER}` 即拒绝（新错误码 `OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN`，提示经计费登记 `MANUAL_ADJUSTMENT`）；纯科目重分类保留，只写分录。
5. **关账 / 重开（04 §4 第 4 行）**：`postBookProfitClose`、`approveReopen` 只写分录与期间；`ensureResultAccount` 只建科目账户身份。
6. **投影重建（04 §4 第 5 行）**：ledger 侧 `approveProjectionRebuild` 只写 `t_trial_balance_exception`；account 新增 `POST /balances/v1/books/{bookId}/rebuild`（经 `billing-api` 分页重放 `economic-facts?bookId=&fromFactId=` 重算，事务外取数、短事务写）。
7. **account 消费（04 §5）**：`EconomicFactRecordedBalanceHandler`：幂等（`fact_id + fact_version`）；映射规则 = 第 07 步迁入 account 的 `AccountIdentityRule`：`fee_type_code × 付方 / 收方角色 → owner_type / owner_id / balance_bucket`；版本 2 事实覆盖版本 1（按 `supersedes_fact_id` 冲回旧版本的余额变化再应用新版本，两步同事务）；写 `t_account_balance`（`balance_version` CAS）与 `t_balance_applied_fact`；`strictOrder=false`。
8. **`BalanceMoved`（04 §5）**：只定义 Payload 类（`paytech-account-api`）与事件目录条目；无生产调用方。
9. **跨服务读（04 §6）**：`AccountMapper.selectOwnerBalances / selectPage / countPage` 不再联 `t_chart_account`——account 自存 `chart_account_id`，科目名经 `ledger-api` `GET /ledger/v1/chart-accounts?ids=` 批量读；`selectProjectionChecks`、`selectTrialAggregates`、`selectProfitCloseBalances` 改为按分录聚合（ledger 本库），需与余额比对的路径调 `account-api`；关账步骤 2 改为本库统计 + `GET /balances/v1/books/{bookId}/consistency` + `GET /billing/v1/economic-facts/pending?bookId=`（任一不可用或未追平则关账失败关闭）；关账步骤 3 的非零 CHANNEL_ACCOUNT / CASH_ACCOUNT 统计由 consistency 接口返回；`BalanceQueryServiceImpl` 经 `ledger-api` 读账簿与科目（失败关闭）；`TransactionPostingServiceImpl` 不再读 reconciliation（`channel_account_id` 来自事实头）。
10. **核对（04 §7）**：account 新增日终核对任务（XXL-Job Handler）比对 `t_account_balance` 与 `ledger-api` `GET /ledger/v1/books/{bookId}/account-totals`，差异写 `t_balance_discrepancy` 并记 WARN；阶段 0 不接工单。
11. **接口（04 §8）**：ledger-api 新增 `GET /ledger/v1/books/{bookId}`、`GET /ledger/v1/chart-accounts?ids=`、`GET /ledger/v1/books/{bookId}/account-totals`；account-api 新增 `GET /balances/v1/books/{bookId}/consistency`、`GET /balances/v1/books/{bookId}/accounts`、`POST /balances/v1/books/{bookId}/rebuild`，迁入 `/balances/v1/clients/{clientId}`、`/partners/{partnerId}`。
12. **装配**：account-app 自己的 DataSource（`mor_account`，账号 `account_app`）、`paytech-messaging`、`paytech-compensation-event`、XXL-Job；ledger-app 引入 `paytech-messaging`；两个聚合 POM 放开 AMQP / compensation-event / XXL 禁令（09 §1.4 第 11 步注记）；`messaging.consumers.billing.partitions=4` 两边各自声明 `ledger.billing.*`、`account.billing.*`；ledger 的 `FinanceModuleBoundaryTest` 后继中"RabbitTemplate 不在 classpath"断言对 account / ledger 取消（09 §2.7）。
13. **本地库**：新建 `mor_account` / `account_app`（init 脚本、`.env.example`、运行中 MySQL 手工建库建账号并应用测试 DDL，命令记报告）；`account_app` 对 `mor_ledger` 的只读授权（第 07 步临时）撤销（SQL 草案写进报告，不执行）。

## 3. 测试要求（04 §9、10 §2.5）

- ledger 236 例后继全部通过；`TransactionPostingTest` 改为"消费 `EconomicFactRecorded` 后过账"（本地 Broker 发布信封触发）；人工调整 / 关账 / 重开 / 试算 / 重建测试中对 `t_account` 余额列的断言改为分录与 `t_ledger_period_balance` 断言。
- account 新增（≥ 12 例）：消费事实更新余额、同一事实重复投递只应用一次、版本 2 覆盖版本 1、映射规则各角色、`consistency` 接口（已追平 / 未追平）、`rebuild` 重算后与 ledger 分录和一致、核对任务发现人为差异写 `t_balance_discrepancy`、ledger-api 不可用时余额查询失败关闭。
- 三服务链路测试：billing 登记事实 → ledger 过账 → account 余额（各自 `-test` 用本地 Broker 与对方 API 的 DTO Mock；10 §2.5）。
- 清理器：`LedgerDataCleaner` 收缩（去余额表）、`AccountDataCleaner` 新建；DDL 两份同步。
- 全 Reactor `verify` 全绿。禁止 `when(...).thenReturn`、`@MockBean`。

## 4. 验证清单

1. `cd paytech && ./mvnw -B verify -pl ledger/paytech-ledger-test,account/paytech-account-test -am` 全绿；全 Reactor 全绿。
2. 两个 app 以 `mock` Profile 启动 health UP。
3. R4 / R7 / R12 零违规（R7：过账事务类不注入任何 HTTP 接口；关账步骤 2 的远程调用在事务外）。
4. `generate-table-ownership.mjs --check` current（白名单随 03 文档的表更名重新生成；03 文档中 `t_account` 行与 account 新表按实现核对，报告列出需回写 03 的差异）。

## 5. 禁止事项

- 不改规则包语义、分录生成规则、期间 / 关账状态机；不实现 `BalanceMoved` 的生产调用方；不接工单。
- 不执行业务库 SQL（测试库除外）；不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-11b): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/11b-ledger-account-split-report.md`：改动摘要 ≤ 30 行、第 4 节逐项、六处事务拆分前后对照表、新接口与事件清单、SQL 草案（撤销只读授权、改表）、与 04 文档偏差、待确认项。
3. 最终回复两行。
