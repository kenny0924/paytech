# 任务卡 07 · Finance → billing / account / ledger / reconciliation / tax（五 core 五 app；account 与 ledger 暂共库）

- 分支：`refactor/step-07-finance-modules`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step07`；起点：`refactor-main@c3f4a92`（00–02d 步与 09、10 已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 07 步；决定 D16、D37（经济事实唯一来源）、D41（`t_currency_spec` 归 Platform，ledger / billing 只读副本）、D42（现金账户三表归对账）。账户与会计的事务逻辑重做在第 11a / 11b 步，本步**只搬不改业务行为**，但要把跨库读改成输入驱动，见第 3 节。
- 并行：与 04、05 同时进行。

## 1. 输入文档（先完整读取）

- [design/04-finance-account-ledger-split.md](../design/04-finance-account-ledger-split.md) 全文（了解第 11 步目标，本步只做其中第 2 节表更名与第 6 节"事务内读其他库"的消除）。
- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md)、[design/02-archunit-rules.md](../design/02-archunit-rules.md)、[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节五行、第 2.1 节 Finance 84 表（归属已全部定案）、第 3 节共享数据（`t_currency_spec`、`t_finance_entity`、共享枚举归 api）。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.3、1.4（Finance 行）、2.7、4.3（finance 生成器与三个测试、`generate-oe-client-schemas.mjs`）；[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 1 节 Finance 行、2.5、3.1（billing / account / ledger / reconciliation / tax 行）、3.2、4.1–4.3、5.1、6。
- [analysis/finance.md](../analysis/finance.md) 全文（第 3 节跨核心 SQL、第 4 节六处事务、第 6 节共享类型、第 9 节热点）。
- 第 00、01、02a 步报告（arch-rules 口径、Platform 的 `CurrencySpecQueryApi`）。
- 现有 `finance-service/`：`FINANCE_PHYSICAL_DECISIONS.md`、`finance/manifest.mjs`、`JournalPostingServiceImpl`、`TransactionPostingServiceImpl`、`AccountMapper.xml`、`JournalLineMapper.xml`、`PeriodCloseMapper.xml`、`FinanceDataCleaner`、`AccountingTestMapper`、`FinanceModuleBoundaryTest`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`。

## 2. 模块映射

五个服务目录 `paytech/{billing,account,ledger,reconciliation,tax}/`，各 `paytech-<svc>-{api,core,app,test}`，模块 parent = 服务聚合 POM。

| 现 | 目标 |
|---|---|
| `paytech-finance-api` 的 `accounting` 合同 | `paytech-ledger-api`；其中 `/channel-cost-plans/v1` 合同 → `paytech-billing-api`；`balance` 合同 → `paytech-account-api` |
| `paytech-finance-accounting`（344 文件，单包 `com.paytech.finance.accounting`） | 按 03 §2.1 表归属与 10 §2.5 测试归属切分到 `paytech-ledger-core`（过账内核、规则、人工调整、试算、关账、期间、账簿、科目、`t_finance_entity`、`t_account` 科目账户身份）、`paytech-billing-core`（经济事实、渠道成本、`ChannelCostEstimationService`）、`paytech-account-core`（`BalanceQueryServiceImpl`、`AccountIdentityRule`、余额查询）、`paytech-reconciliation-core`（`t_channel_account` 只读代码）、`paytech-tax-core`（空壳 `package-info`） |
| `paytech-finance-web` | Controller 与装配按归属拆到五个 `-app`（ledger 11 组、billing 1 组、account 1 组；reconciliation / tax 只有启动类与健康检查） |
| `paytech-finance-treasury`、`paytech-finance-tax` 空壳 | 删除（职责由 reconciliation / tax 服务目录承接） |
| `paytech-finance-test` | 按 10 §2.5 拆：billing 5 类 49 例、ledger 26 类 156 例 + `TransactionPosting*` 17 例改写、account `QueryOwnerBalanceTest` 4 例改写、reconciliation / tax 各只有应用启动与架构测试 |
| `docs/`、`README.md` | 按归属拆；PDManer 按 Manifest 分组输出五份模型，Schema SQL 按表归属切片（DDL 逐字复制；经济事实三表与 `t_accounting_instruction` 列按下文更名）|

删除 `finance-service/mvnw`；旧根消失。`paytech/pom.xml` 取消五行注释。应用名与端口：`ledger-service` 18105 / 19105（现 Finance 端口）、`billing-service` 18113、`account-service` 18114、`reconciliation-service` 18115、`tax-service` 18116（管理端口 +1000）。库：`mor_ledger`（现 `mor_finance` 改名；**本步 account-app 也连它**，第 11b 步分离）、`mor_billing`、`mor_reconciliation`、`mor_tax`（`mor_account` 第 11b 步新建）。

## 3. 代码与逻辑改动清单

1. **表更名（04 §2、§3.1，纸面与测试 DDL）**：billing 库 `t_transaction_economics` → `t_economic_fact`（新增列 `fact_type` 补 `MANUAL_ADJUSTMENT` 取值、`supersedes_fact_id`、`status`）、`t_transaction_economic_component` → `t_economic_fact_line`、`t_transaction_economic_comp_link` → `t_economic_fact_line_link`；ledger `t_accounting_instruction.transaction_economics_id` → `economic_fact_id`。本步只改 Schema SQL、PDManer、DataObject、Mapper、测试 DDL 与 Builder；不执行任何 SQL；`t_account` 拆表留第 11b 步。
2. **消除事务内跨库读（04 §6，analysis §3、§4）**：
   - `TransactionPostingServiceImpl#post`（无生产调用方）改为**输入驱动**：入参为经济事实 DTO（头 + 明细行，含 `channelAccountId`）与规则上下文，不再注入 `TransactionEconomicsRepository`、`TransactionEconomicComponentRepository`、`ChannelAccountRepository`；"billing 不变量"校验（合作伙伴三元组全有或全无、平台服务费主体等于 Finance Entity）作为静态校验类迁到 `paytech-billing-core`，ledger 侧只保留对 DTO 完整性的断言。第 11a / 11b 步把它接到 `EconomicFactRecorded` 消费。
   - `PeriodCloseMapper.countPendingCompensationEvents` 只统计本库；`countNonZeroTreasuryAccounts` 保留（本步 account 仍共库）。
   - `AccountMapper` 联 `t_chart_account` 的查询在本步仍合法（共库），不改；在报告中列出第 11b 步要改的清单。
3. **共享类型归 api**（03 §3）：`AmountSource`、`EconomicsType`、`AutoInstructionType` → `paytech-billing-api`；`OwnerType`、`OwnerRole`、`BalanceBucket`、`StandardAccountCodes` → `paytech-account-api`；`AccountIdentityRule` → `paytech-account-core`（ledger 的 `resolveAccount` 在本步仍需要它：ledger-core 临时依赖 `paytech-account-api` 中的规则**接口**，实现以 DTO 结果注入；若做不到无环，则本步把 `AccountIdentityRule` 放 `paytech-account-api` 并在报告标注第 11b 步收回）；`CurrencySpec` 值类型随 D41 取自 `paytech-platform-support-api`，ledger 与 billing 各持 `t_currency_spec` 只读副本表（DDL 与种子 DML 逐字复制，表注释标明"只读副本，Owner platform-support"）。
4. **`t_finance_entity`** 归 ledger；billing / tax 只存 ID（现无写入方，不加校验调用）。
5. **五个 app 装配**：各自 DataSource / 事务管理器 / `@MapperScan` / TypeHandler / Snowflake worker / Redis 锁前缀（`AccountingLocks` 按归属拆：过账与关账锁归 ledger）；account-app 与 ledger-app 共连 `mor_ledger` 但 **account-app 只读**（账号 `account_app` 本步在 `mor_ledger` 上只授 SELECT，第 11b 步改）。
6. **Enforcer**（09 §1.4）：Finance 禁令复制到五个聚合 POM（含 AMQP / compensation-event 禁止；第 11 步再放开 account / ledger）。`FinanceModuleBoundaryTest` 的模块集合断言按五服务各写一份或下沉 arch-rules（09 §2.7）；"RabbitTemplate 不在 classpath"断言保留。
7. **路径修复**（09 §4.3）：`finance/generate-finance-model.mjs` 按 Manifest 分组输出五份；`finance-model.test.mjs`、`finance-scenario-doc.test.mjs` 参数化或拆五份；`finance-manual-payout-simplification.test.mjs` 随 Manifest 06 归 payout（payout 目录本步只放 Schema 归属：`paytech/payout/docs/database/` 的模型与切片 SQL，不建代码模块）；`generate-oe-client-schemas.mjs` / `oe-client-model.test.mjs` 的 Finance 条目改为五个目录。dispute 相关 Risk 表不在本步。

## 4. 测试要求（10 §2.5）

- 基线：搬迁前 `cd finance-service && ./mvnw -B verify` 实际数（源码核实 236）。
- billing 5 类 49 例、ledger 26 类 156 例整类搬入；`TransactionPostingTest` / `TransactionPostingRejectionTest` 改为 ledger 测试：经济事实由 `TransactionEconomicsBuilder` 构造 DTO 直接传入服务（不再写 billing 表），渠道清算账户 ID 由 DTO 给出，断言只落 ledger（本步共库下余额断言仍可留在同一测试，第 11b 步再移到 account）；`QueryOwnerBalanceTest` 归 account，前置数据经 ledger 过账接口写入（共库）或直接 Mocker 写 `t_account`；五个 `<Service>ApplicationTest` + `ArchitectureAutoTest`。
- 清理器：五个 `*DataCleaner` 按 10 §3.1（ledger 的保留表 `deleteNonReserved` 与 45 行保留基数重算；billing 的 `t_currency_spec` 只读副本为保留表）；`init/ddl.sql` 五份按 10 §4.2 补齐 Finance 今天缺的 58 张表 DDL（关账 / 试算 / `t_ledger_period_balance` / `t_compensation_event` / `t_consumed_event`），账号 `GRANT SELECT, INSERT, UPDATE, DELETE`，不再 `GRANT ALL`。
- 本地库：`mor_finance` → `mor_ledger` 改名（运行中 MySQL：新建 `mor_ledger` 并应用测试 DDL，不删 `mor_finance`，命令写进报告），新增 `mor_billing`、`mor_reconciliation`、`mor_tax` 与五个 `<svc>_app` 账号（init 脚本、`.env.example`、手工执行）。
- 禁止 `when(...).thenReturn`、`@MockBean`。

## 5. 验证清单

1. 前后测试数；`cd paytech && ./mvnw -B verify -pl ledger/paytech-ledger-test,billing/paytech-billing-test,account/paytech-account-test,reconciliation/paytech-reconciliation-test,tax/paytech-tax-test -am` 全绿；全 Reactor 全绿。
2. 五个 app 以 `mock` Profile 启动 health UP（Redis 需在跑）。
3. `node --test scripts/pdmaner/finance-*.test.mjs scripts/pdmaner/oe-client-model.test.mjs` 通过。
4. `git log --follow` 抽查 `JournalPostingServiceImpl.java`（ledger）与 `ChannelCostPlanServiceImpl.java`（billing）；旧根不存在。
5. R4：ledger 与 account 共库期间白名单按 03 文档（`t_account` 归 ledger），account-core 的 Mapper 只读 `t_account`、`t_chart_account`、`t_ledger_book`——在 `table-ownership.yaml` 以"共库过渡"注记放行，报告说明第 11b 步收口。

## 6. 禁止事项

- 不改过账、规则、人工调整、试算、关账任何业务行为；`t_account` 不拆表、不删余额列；不引入 messaging / 补偿到 Finance 五服务（第 11 步）。
- 不执行业务库 SQL（测试库除外，须记录）；不改 Platform / Payment 目录。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 7. 交付物

1. 短分支提交，前缀 `refactor(step-07): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/07-finance-modules-report.md`：改动摘要 ≤ 30 行、第 5 节逐项、五个 api 的合同清单、`TransactionPostingService` 新入参 DTO 定义（给第 11a 步）、第 11b 步需改的跨库读清单、与设计偏差、待确认项。
3. 最终回复两行。
