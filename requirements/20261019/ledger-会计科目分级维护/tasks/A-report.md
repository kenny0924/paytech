# 任务卡 A 报告：ledger

工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-chart`（未提交、未建分支、未 stash、未 `mvn install`）。测试库 `mor_ledger_chart`、RabbitMQ vhost `/mor-chart`、Redis 前缀 `ledger-chart`。

## 1. 开工说明（WRITE_API_EXAMPLE 第 1 节）

- 已读：`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（worktree 内）；需求 `requirement.md`、`technique.md` 第 2 节、`db/ledger/ddl.sql`、`db/ledger/dml.sql`、决定记录 `2026-10-19-ledger-chart-hierarchy-and-account-numbers.md`。
- 归属与入口：ledger-core `service/processor` 下 `CreateChartAccountProcessor` / `UpdateChartAccountProcessor` / `DisableChartAccountProcessor` / `EnableChartAccountProcessor`（共用 `AbstractChartAccountProcessor`，继承 `AbstractLockProcessor`）；Context 为 `ChartAccountChangeContext`（科目表、目标科目、上级、变更记录 DataObject）。
- 原请求身份：无业务操作身份。新增以编号 / 代码唯一键拒绝重复；修改无变化直接返回；停用 / 启用按当前状态幂等返回。
- 原子保存：`LedgerSetupService.saveChartAccountChange` 一个 `TransactionTemplate` 内写科目（插入或条件更新、校验行数、成功后回写原对象）+ 追加 `t_chart_account_change`。无外部调用、无提交后推进。
- 锁：`AccountingLocks.chart(chartId)`（LOCK），四个维护入口与 `CreateLedgerBookProcessor`（新建账簿 = 采用）共用；唯一索引与条件更新兜底。
- 建户三路径：账户号序号在建户的同一本地事务中由 `AccountNoSequenceRepository.next` 分配，`LedgerAccountNo` 统一组装。

## 2. 改动文件（共 121 个）

模型与 SQL（7）
- `paytech/ledger/docs/database/ledger.pdma.json`：`t_chart_account` 加 `account_no`、`account_status`、`uk_chart_acct_no`；`t_ledger_account` 加 `account_no`、`uk_ledger_account_no`；新表 `t_chart_account_change`、`t_account_no_sequence`（图组 `ledger_and_close`）；31 → 33 表。
- `docs/database/sql/ledger-schema.sql`（由模型经 `renderSchema` 只重新生成 ledger 一个文件，未运行会覆盖其他服务 Schema 的全量生成命令）。
- `docs/database/sql/ledger-initial-data.sql`：STANDARD 改为 19 个汇总科目 + 29 个末级科目（technique 2.1），全部 ACTIVE。
- 新增 `docs/database/sql/changes/2026-10-19-ledger-chart-hierarchy.sql`：按执行顺序合并需求目录 `ddl.sql` 第 1 段 → `dml.sql` → `ddl.sql` 第 2 段（含恢复段），各段逐字一致。
- `paytech-ledger-test/src/test/resources/init/ddl.sql`：头部 + 新 Schema + 新初始化数据（与“头部 + schema + initial-data”逐字组合规则一致）。
- `scripts/pdmaner/ledger-model.test.mjs`：表数 31 → 33（`oe-client-model.test.mjs` 无需修改）。

API（`paytech-ledger-api`，15）
- 新增 `ChartOfAccountsCommandApi`（L1—L4）、`CreateChartAccountRequestBody`、`UpdateChartAccountRequestBody`、`CreateChartAccountResponseBody`、`ChartAccountChangeResponseBody`、`AccountBalanceListResponseBody`、`AccountBalanceLine`。
- 修改 `LedgerSetupQueryApi`（M1 加 `accountStatus` 参数、L5 `listChartAccountChanges`）、`LedgerBookQueryApi`（M2 加 `accountNo` 参数）、`TrialBalanceApi`（L6 `listAccountBalances`）、`ChartAccountResponseBody`（+`accountNo`/`parentChartAccountId`/`level`/`accountStatus`）、`AccountResponseBody`（+`accountNo`/`accountName`）、`JournalEntryDetailResponseBody.Line`（+`accountNo`/`accountName`）、`TrialBalanceDetailResponseBody`（`lines` 改用 `AccountBalanceLine` 并加字段，新增 `summaryLines`）、`LedgerSetupErrorCode`（technique 2.3 的 8 个 Code，相邻项空一行）。

App（`paytech-ledger-app`，9）
- 新增 `ChartOfAccountsController`、`ChartOfAccountsConverter`；修改 `LedgerSetupQueryController`、`LedgerBookQueryController`、`TrialBalanceController`、`LedgerQueryConverter`、`LedgerReadConverter`、`TrialBalanceConverter`、`LedgerPersistenceConfiguration`（显式注册 `ChartAccountStatus`、`ChartAccountChangeType` 的 `CodeEnumTypeHandler`）。

Core（`paytech-ledger-core`，62）
- 枚举 `ChartAccountStatus`、`ChartAccountChangeType`；DataObject `ChartAccount`（+`accountNo`、`accountStatus`）、`LedgerAccount`（+`accountNo`）、新 `ChartAccountChange`。
- Mapper / XML：`ChartAccountMapper`（插入、按编号查、下级、条件更新结构与状态、排序改 `account_no`）、新 `ChartAccountChangeMapper`、新 `AccountNoSequenceMapper`（`INSERT … VALUES (…, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE \`last_value\` = LAST_INSERT_ID(\`last_value\` + 1)` 后 `SELECT LAST_INSERT_ID()`）、`LedgerAccountMapper`（列 + 筛选 + 返回 `account_no`、科目名）、`JournalLineMapper`（分录行账户号、科目名）、`TrialBalanceLineMapper`（行加编号、上级，排序改编号）、`LedgerBookMapper.countByChart`、`AccountingRuleLineMapper.countCurrentByAccountCode`。
- Repository：`ChartAccountRepository(+Impl)`、新 `ChartAccountChangeRepository(+Impl)`、新 `AccountNoSequenceRepository(+Impl)`（要求调用方事务）、`LedgerBookRepository(+Impl).existsByChart`、`AccountingRuleLineRepository(+Impl).existsCurrentByAccountCode`、`TrialBalanceLineRepository(+Impl)`。
- 纯规则 / 计算：`domainservice/validator/chart/ChartAccountNoRule`、`domain/LedgerAccountNo`、`domain/ChartBalanceRollup`；Context `ChartAccountChangeContext`；View `AccountBalanceView`（取代并删除 `TrialBalanceLineView`）、`AccountBalanceListView`、`ChartAccountChangeView`；`AccountQuery`、`AccountView`、`JournalEntryDetailView`、`TrialBalanceDetailView` 加字段。
- Processor：新 `AbstractChartAccountProcessor` + 四个维护 Processor + 三个内部 Request；`CreateLedgerBookProcessor` 改为 `AbstractLockProcessor`（同一科目表锁）；`CreateAccountProcessor`（停用拒绝 `CHART_ACCOUNT_INACTIVE`、账户号）；`CreateRuleVersionProcessor.checkPostingAccount`（停用拒绝）。
- Service：`LedgerSetupService(+Impl)`（`createAccount(account, accountNo)` 事务内取号建户、`saveChartAccountChange`）、`LedgerQueryService(+Impl)`（M1 状态筛选、L5）、`TrialBalanceService(+Impl)`（L6、M4 汇总行）、`TransactionPostingServiceImpl.resolveAccount` 与 `PeriodCloseService(+Impl).ensureResultAccount`（新建分支停用拒绝 + 账户号）、`AccountingLocks.chart`。

测试（`paytech-ledger-test`，25）
- 新增：`auto/ext/http/chart/CreateChartAccountTest`、`UpdateChartAccountTest`、`DisableEnableChartAccountTest`、`ListChartAccountChangesTest`、`ChartMaintenanceLockTest`；`auto/ext/http/query/StandardChartTreeTest`；`auto/ext/http/trial/AccountBalancesQueryTest`；`auto/domain/ChartAccountNoRuleTest`、`ChartBalanceRollupTest`；`test/base/AbstractChartAccountApiTest`；`test/builder/ChartAccountRequestBuilder`。
- 修改：`CreateAccountTest`、`ListAccountsTest`、`GetJournalEntryTest`、`ListChartOfAccountsTest`、`RunTrialBalanceTest`、`CreateRuleVersionValidationTest`、`StartPeriodCloseTest`、`TransactionPostingTest`、`TransactionPostingRejectionTest`、`AbstractManualAdjustmentApiTest`（`CAPITAL_CODE` 常量）、`LedgerDataCleaner`（保留行 78 → 97、两张新表、恢复 STANDARD 科目状态）、`AccountingTestMapper`、`LedgerMocker`。

arch-rules（3，与 B 卡共享文件，逐段增量修改）
- `scripts/generate-table-ownership.mjs` 加 ledger 两表 → 重新生成 `table-ownership.yaml`（只多出两行与表头计数 8 → 10）；`TableOwnershipAutoTest`（总数 +2、ledger 29 → 31、两表存在断言）。

## 3. 对应验收

| AC | 实现与证据 |
|---|---|
| AC-01 编号唯一、前缀与位数 | `ChartAccountNoRule` + `uk_chart_acct_no`；`ChartAccountNoRuleTest`、`CreateChartAccountTest.test_invalidAccountNoRejected/test_duplicateNoOrCodeRejected`、`UpdateChartAccountTest` |
| AC-02 STANDARD 完整树 | 初始化 DML；`StandardChartTreeTest`（19 汇总覆盖五类、29 末级有同类上级且前缀一致、全部 ACTIVE、`OwnerAccountType.getChartAccountNo()` 与科目表一致） |
| AC-03 父科目不可记账 | 建户 `CHART_ACCOUNT_NOT_POSTABLE`（`CreateAccountTest.test_inactiveOrSummaryChartAccountRejected`）、规则（`CreateRuleVersionValidationTest.test_summaryAccountRejected`）；记账沿用既有 `posting_allowed` 校验 |
| AC-04 未采用时调整、成环 / 类别 / 父可记账拒绝 | `UpdateChartAccountTest`（移动重编号、自身为上级、有下级、新上级类别不同或停用） |
| AC-05 采用后冻结 | `UpdateChartAccountTest.test_structureFrozenAfterAdoption`、`ChartMaintenanceLockTest.test_adoptionThenStructureUpdateFrozen`；追加与启停允许（`CreateChartAccountTest.test_appendAllowedAfterAdoption`、`DisableEnableChartAccountTest.test_statusChangeAllowedAfterAdoption`） |
| AC-06 停用效果 | 建户 `CHART_ACCOUNT_INACTIVE`、规则版本拒绝、记账新建分支拒绝且整笔回滚、关账建户拒绝；已有账户照常记账（`TransactionPostingTest.test_inactiveChartAccountKeepsExistingAccountPosting`） |
| AC-07 直接生效 + 留痕 | 变更记录落库与 L5 查询（`ListChartAccountChangesTest`、各维护测试断言前后值与操作人）；权限由 Gateway/Platform 注册（D 卡），ledger 只校验操作人 Header 必填 |
| AC-08 级次汇总 | L6 `AccountBalancesQueryTest`、M4 `RunTrialBalanceTest.test_detailSummaryLinesRollUpToLevelOne`、`ChartBalanceRollupTest`（含备抵科目方向换算） |
| AC-09 记账链路 | 全部既有过账 / 消费 / 关账测试通过 |
| AC-11 账户号（ledger 部分） | 手工建户 `CreateAccountTest.test_accountNoFormatAndSequence/test_financeEntityAccountNoUsesEntityCode`、记账建户 `TransactionPostingTest`（OE 与 FE 账簿）、关账建户 `StartPeriodCloseTest`；M2 精确筛选 `ListAccountsTest`、M3 `GetJournalEntryTest`；事件载荷未改 |

AC-10（mor-test）不在本卡范围。

## 4. 实际执行的命令与结果

- `node --test scripts/pdmaner/ledger-model.test.mjs scripts/pdmaner/oe-client-model.test.mjs`：15 / 15 通过。
- ledger 自动测试（`paytech/` 下）：`./mvnw -o -pl ledger/paytech-ledger-test -am verify -Dtest='com/paytech/ledger/auto/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false`，环境 `LEDGER_TEST_DB_URL=…/mor_ledger_chart`、`MOR_RABBITMQ_VHOST=/mor-chart`、`REDIS_KEYPREFIX=ledger-chart`：**294 个测试、0 失败、0 错误**，BUILD SUCCESS（新增测试类写入前的首轮全量运行为 235 个，其中失败暴露 `last_value` 是 MySQL 8 保留字，已加反引号修复）。
- `./mvnw -o -pl tools/arch-rules -am verify`（按卡原样）：60 / 60 通过，BUILD SUCCESS；`node tools/arch-rules/scripts/generate-table-ownership.mjs --check` 通过。
- 增量 SQL 验证（只在 `mor_ledger_chart`）：用 HEAD 的旧测试 DDL 建库 → 执行 `changes/2026-10-19-ledger-chart-hierarchy.sql`（核对输出：采用 0、48/48 有编号、bad_parent 0、两项无编号 0）→ 导出全部 `SHOW CREATE TABLE` 与科目行，再按新测试 DDL 重建后导出对比：**完全一致**。之后 `mor_ledger_chart` 已按新 DDL 重建为空基线。
- 未运行：按卡原样的 `./mvnw -o -pl paytech/ledger/paytech-ledger-test -am verify`（不带 `-Dtest`）。原因：`-am` 会连带执行 Foundation 等上游模块的测试，它们使用其他会话共享的测试库 / Redis / MQ，违反“只用本需求专用资源”；因此以 `-Dtest` 限定为 ledger 测试（上游模块照常编译打包）。

## 5. 偏离设计之处

1. **L1—L4 的 Controller 位置**：technique 2.8 写“`LedgerSetupController`（已有类）”，但仓库没有该类。按现有“每类资源一个 CommandApi + Controller”（如 `FinanceEntityCommandApi`）新建 `ChartOfAccountsCommandApi` + `ChartOfAccountsController`；L5 放 `LedgerSetupQueryApi`；L6 路径与试算平衡同前缀，放 `TrialBalanceApi` / `TrialBalanceService`。
2. **Java API 签名变化（影响 C 卡 BFF 编译）**：`listChartAccounts(chartCode, postingAllowed, accountStatus)`、`listAccounts(ledgerBookId, accountNo, accountCode, …)`；`TrialBalanceDetailResponseBody` 的嵌套 `Line` 换为共享模型 `AccountBalanceLine`（JSON 字段只增不减）。已发消息告知 team-lead。
3. **PDManer 关系**：只画 `t_chart_account_change.chart_id → t_chart_of_accounts`、`t_account_no_sequence.ledger_book_id → t_ledger_book`。两表的 `chart_account_id` 不是任何索引的左前缀，`ledger-model.test.mjs` 不允许画关系；DDL 以 technique 为准，未加索引。
4. **L5 上级编号**：表只存上级主键，查询时取上级科目的**当前**编号；上级之后被重编号时显示新编号。
5. **停用引用检查**：账户类型目录与规则行按科目代码判断，不区分科目表（测试科目表里同名代码也视为被引用）；引用检查对汇总与末级科目一律执行，汇总科目另查启用中的下级。
6. **科目表状态**：technique 只对“新增”要求科目表 ACTIVE，实现对四个维护入口都要求 ACTIVE（`CHART_NOT_ACTIVE`），保持一致。
7. **错误形式**：规则版本停用沿用既有 `BizCheckFailException`；记账新建分支沿用既有 `reject`（消费拒绝）；关账建户与手工建户用 `CHART_ACCOUNT_INACTIVE`。
8. **排序**：M1 科目列表、试算行（M4 `lines`）、L6 都按 `account_no`；`RunTrialBalanceTest` 期望顺序随之调整。M2 账户列表排序未改（仍按科目代码），technique 未要求。
9. `LedgerAccount` 中原有的 6 行多余空行在加字段时一并删除。

## 6. 未完成项与注意

- BFF（C 卡）在更新调用前无法编译通过（见偏离 2）。
- 关账路径的停用检查在真实接口下不可达（`CURRENT_PERIOD_RESULT` 被结转规则引用，停用接口会拒绝），测试通过测试 Mapper 直接改状态覆盖。测试只改 STANDARD 保留科目的启停状态，清理组件恢复为 ACTIVE，并在基线检查中断言。
- `paytech/ledger/README.md`、`FINANCE_PHYSICAL_DECISIONS.md` 属 D 卡，本卡未改。
- **Git 索引事故（已恢复）**：删除 `TrialBalanceLineView.java` 时误用了 `git rm --cached`，把该文件的删除写进了 worktree 索引；随即用 `git restore --staged -- <该路径>` 恢复索引，现在该文件只是工作区删除（` D`），索引中没有任何本卡暂存。之后未再执行任何改动索引的命令。

## 7. 写接口自查（示例第 11 节）

- API / Body：class + 中文 JavaDoc + Hibernate Validator（编号、代码、类别、方向、必填）；L3/L4 无 Body；无 OpenAPI 文件需同步（内部接口）。
- Controller / Converter 只转换与委托；操作人取 `X-Gateway-Actor-Subject` 传入内部 Request。
- Processor 只查本模块 Repository（科目表、科目、账簿、规则行、结转规则），不调 Mapper、不逐表写；主方法显示“无变化 / 已是目标状态直接返回”与新变更分支。
- Context 持有本模块 DataObject，无 HTTP 对象或同义 Snapshot；变更后的值以变更记录 after 字段为准。
- Service 不查询、不调用其他 Service；一个 `TransactionTemplate` 写科目 + 变更记录；建户在事务内取号 + 插入；事务内无外部调用。
- Repository 一表一类；条件更新校验行数，成功后回写原对象；唯一键冲突按索引名转 `CHART_ACCOUNT_NO_DUPLICATE` / `CHART_ACCOUNT_CODE_DUPLICATE`。
- 锁：科目表锁 LOCK，五个入口共用同一 Key；唯一索引与条件更新保留；`ChartMaintenanceLockTest` 证明持锁时新建账簿与结构修改都等待。
- 无业务操作身份，不涉及 Gateway 幂等；多表查询使用 View（`AccountBalanceView`、`ChartAccountChangeView`）。
- 测试按 UNIT_TEST_GUIDE 从 HTTP / Handler / 公开纯 Java 入口触发，类级 `@see` 指向被测方法，无 Mockito Stub。
