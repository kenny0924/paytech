# 第 07 步报告 · Finance → billing / account / ledger / reconciliation / tax

- 分支：`refactor/step-07-finance-modules`（起点 `refactor-main@7e0b70d`）。提交 `5c678730`（Java、POM、测试、环境）、`84b0ad65`（PDManer、node 测试、文档），本报告单独提交。
- 结论：Finance 已拆为五个服务各四模块，旧根 `finance-service/` 消失。环境恢复后在最终代码上复跑：五个 `-test` 共 316 例与 arch-rules 55 例全部通过，五个 app 以 mock Profile 启动后 health / liveness / readiness 均为 UP（第 2 节"环境恢复后复跑"）。
- 全 Reactor verify 按 team-lead 决定由主会话合入后执行；本步未跑 platform-support-test。
- 中途宿主磁盘写满曾导致共享 Redis / MySQL / Docker 故障，期间的失败记录保留在第 2 节，本步未重启任何容器。

## 1. 改动摘要

1. 464 个 Java 文件、35 个 Mapper XML、POM、配置与文档用 `git mv` 迁入 `paytech/<svc>/paytech-<svc>-{api,core,app,test}`，模块 parent 为服务聚合 POM。
   - 包根改为 `com.paytech.<svc>`；类名只改 `Finance*`→`Ledger*`、`TransactionEconomics*`→`EconomicFact*`。
   - 删除 treasury / tax 空壳、`finance-service/mvnw`、`.mvn`、`.gitignore`；`paytech/pom.xml` 只取消五行注释。
2. 归属按任务卡第 2 节切分：
   - ledger-core：过账、规则、人工调整、试算、关账、`t_account`、`t_finance_entity`。
   - billing-core：渠道成本与经济事实。
   - account-core：余额查询。
   - reconciliation-core：`t_channel_account` 只读。
   - tax-core：只有 `package-info`。
3. **交易自动记账改为输入驱动**（定义见第 4 节）：
   - `TransactionPostingCommand` 只含 billing-api 的 `EconomicFactPayload`。
   - 实现不再注入 `TransactionEconomics*Repository` 与 `ChannelAccountRepository`；CHANNEL_ACCOUNT Owner 取载荷头的 `channelAccountId`。
   - Partner 三元组与服务费归属校验迁到 billing-core 的静态类 `EconomicFactInvariants`；ledger 只做载荷完整性断言 `checkFactComplete`。
   - 其余规则、账户解析、平衡、过账、幂等逻辑逐行保留。
4. 表更名只涉及纸面与测试 DDL，没有执行任何业务 SQL：
   - `t_economic_fact`：`fact_type` 由 `economics_type` 更名，增加 `MANUAL_ADJUSTMENT` 取值；新增 `supersedes_fact_id` 和 `status`（本期只有 `RECORDED`）。
   - `t_economic_fact_line` 与 `t_economic_fact_line_link` 只改表名。
   - `t_accounting_instruction.economic_fact_id` 由 `transaction_economics_id` 更名。
5. 共享类型（任务卡第 3.3 项）：
   - billing-api：`AmountSource`、`EconomicsType`、`AutoInstructionType`。
   - account-api：`OwnerType`、`OwnerRole`、`BalanceBucket`、`StandardAccountCodes`、`AccountIdentityRule`（采用任务卡的兜底位置）。
   - `CurrencySpec.status` 改用 platform-support-api 的 `CurrencySpecStatus`。
6. account-core 自有只读 `BalanceAccountMapper`，查询与 ledger 原 SQL 逐字一致；ledger 删除已无调用方的 `selectOwnerBalances`。
7. 五个 app 各自具备：启动类、`@MapperScan`（只扫本服务 mapper 包）、TypeHandler、`<svc>.identifier` Snowflake。
   - 端口：ledger 18105、billing 18113、account 18114、reconciliation 18115、tax 18116；管理端口各加 1000。
   - Redis 锁：`AccountingLocks` 留 ledger，渠道成本锁迁为 billing 的 `BillingLocks`。
   - tax-app 不装配 MyBatis。
8. 五个聚合 POM 的 Enforcer：复制 Finance 禁令（含 AMQP / compensation-event），加上 `com.paytech:*-core` / `*-app` 通配禁止，放行自身四模块与 `paytech-common-core`。
9. PDManer：
   - 生成器仍在内存构建 84 表逻辑模型，按新增的 `finance/service-split.mjs` 输出六份服务模型（含 payout），各服务 Schema SQL 由同一模型生成。
   - 发现并同步了 Manifest 的既有漂移，见第 6 节第 1 项。
10. arch-rules：`table-ownership.yaml` 改用新表名，并新增 `transitionalSharedReads`（account 只读 `t_account` / `t_chart_account` / `t_ledger_book`）；`TableOwnership.allowedTables` 读取该段。
11. local-environment：
    - 初始化脚本增加五个账号的可选创建块（变量存在才创建），`.env.example` 增加五个变量，`verify-local-environment.mjs` 同步清单。
    - compose 文件未改。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令前执行 `export JAVA_HOME=…/jdk-25.jdk/Contents/Home`，并导出 `PAYTECH_REDIS_PASSWORD`（取自 `.env.local` 的 `MOR_REDIS_PASSWORD`，未打印）。

### 1. 前后测试数与 Reactor

搬迁前基线（`cd finance-service && ./mvnw -B verify`）：BUILD SUCCESS，执行 **242**，skipped 0；源码方法 **236**（`FinanceApplicationTest` 6 个方法展开为 12）。

搬迁后，环境正常时的实际运行：

```bash
cd paytech && ./mvnw -B -o verify -pl ledger/paytech-ledger-test -am -Dtest='com/paytech/ledger/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…/Code-MoR/local-environment
cd paytech && ./mvnw -B -o verify -pl tools/arch-rules,billing/paytech-billing-test,account/paytech-account-test,reconciliation/paytech-reconciliation-test,tax/paytech-tax-test -am -Dtest='com/paytech/{billing,account,reconciliation,tax,arch}/**/*Test' …
```

两条命令均 BUILD SUCCESS。`-Dtest` 只是避免重复跑上游 Foundation 用例；Foundation 在后续 Reactor 运行中单独通过。

| 模块 | 执行数 | 源码方法 | 失败 / skipped |
|---|---|---|---|
| paytech-ledger-test | 190 | 184 | 0 / 0 |
| paytech-billing-test | 71 | 65 | 0 / 0 |
| paytech-account-test | 21 | 15 | 0 / 0 |
| paytech-reconciliation-test | 17 | 11 | 0 / 0 |
| paytech-tax-test | 17 | 11 | 0 / 0 |
| 合计 | **316**（基线 242） | **286**（基线 236） | 0 / 0 |
| paytech-arch-rules | 55 | — | 0 / 0（含新增白名单断言） |

- 方法下限：billing 49、ledger 156+17、account 4 均满足。10 §1.2 写的 ledger 173 是含 Application / Boundary 的口径，实际 184。
- 类映射（10 §6.1 第 4 条）：
  - 整类搬入的 billing 5 类 49 例、ledger 29 类 156 例：类名、方法名、方法数不变。10 §2.5 写"26 类"，按源码实际为 29 类。
  - `TransactionPostingTest`：4 → 4，改为 DTO 输入。
  - `TransactionPostingRejectionTest`：13 → 13。9 例保持原意。
    - `channelAccountMissing` 改为载荷缺少 `channel_account_id`。
    - `channelAccountOfOtherEntity` 改为 `sellerTaxWithoutAuthority`。
    - 四个 Partner 不变量用例改为 `partnerShareWithoutLocalPartner`、`serviceChargeWithoutFinanceEntity`、`missingSucceededTime`、`incompleteFactPayload` 四个载荷完整性用例。
  - 原四个 Partner 不变量用例迁到 billing 新增的 `EconomicFactInvariantsTest`：5 例，4 个迁入加 1 个合法组合。
  - `QueryOwnerBalanceTest`：4 → 4，改由 `BalanceAccountMocker` 直接写共库 `t_account`。
  - `FinanceApplicationTest`（6 方法）与 `FinanceModuleBoundaryTest`（4）在五个服务各建一份，另各加一个 `ArchitectureAutoTest`。
- 最终代码在环境故障后补跑了无需基础设施的部分：
  - 全 Reactor `./mvnw -B -o verify -DskipTests`：BUILD SUCCESS。
  - 五服务的 `ArchitectureAutoTest`、`*ModuleBoundaryTest`、`EconomicFactInvariantsTest`、`*ContentHashTest` 与 arch-rules：BUILD SUCCESS，0 失败。
  - 补跑的原因：import 排序在上面的完整运行之后，只调整 import 顺序。
- **全 Reactor（未完成）：**
  - 第一次 `./mvnw -B -o clean verify`：停在 `paytech-vault-test`。该模块需要 `VAULT_TEST_*` 变量和 Go 服务二进制，这是既有环境前置，与本步无关。
  - 第二次排除 vault-test：Foundation、arch-rules 等通过，`paytech-platform-support-test` 大面积失败，首因是 `Duplicate entry '900000000000000500' for key 't_access_tenant.PRIMARY'`。当时 step05 worktree 正在对同一 `mor_platform` 跑 platform-support-test（`ps` 已核实），属并发争用，本步未改 Platform 任何文件。
  - 第三、四次：宿主磁盘满（ENOSPC），Redis 进入 `MISCONF`，Foundation redis-starter 的 2 个集成用例失败；随后 MySQL 也报 `Communications link failure`，account 测试在故障期间出错（16 个错误）。
  - team-lead 已决定：全 Reactor verify 只由主会话在合入后执行；本步只需在环境恢复后重跑 Finance 五服务的 `-pl … -am`。原计划命令留作参考：`cd paytech && ./mvnw -B clean verify -pl '!vault/paytech-vault-test' -Dpaytech.local-environment.dir=…`，并在没有其他 Platform 测试 JVM 时单跑 platform-support-test。

### 2. 五个 app 以 mock Profile 启动（部分完成）

命令：`java -jar <svc>-app-exec.jar --spring.profiles.active=mock --spring.config.additional-location=file:<svc>-test/…/application-mock.yml --server.port=281xx --management.server.port=291xx`。执行时共享环境已处于故障状态：

- account、reconciliation、tax 均在 2.1–2.4 秒内输出 `Started …Application`。
  - account 与 reconciliation 的 liveness、readiness 均为 UP，总体 health 为 DOWN（db 组件，原因是 MySQL 连接中断）。
  - tax 的探测因 curl 无超时被挂住，结果未取到。
- ledger、billing 启动失败，原因是 Redisson 初始化失败（Redis `MISCONF`）。
- 在故障前的测试中，五个 `<Svc>ApplicationTest` 均通过真实随机端口启动正式启动类，管理端口 `/actuator/health`、liveness、readiness 均返回 UP。这只是同一路径的测试内证据，不能替代本项。
- 进程均已停止。

### 3. node 测试

`node --test scripts/pdmaner/finance-*.test.mjs scripts/pdmaner/oe-client-model.test.mjs`：148 项，146 通过，2 失败。

- 基线：147 项，142 通过，5 失败。
- 剩余 2 个失败与基线逐字相同，均属既有问题：
  - `missing payout.operating_entity_id`（manual-payout 测试，Payout Manifest 本身的问题）。
  - commerce schema drift（`oe-client-model` 在 commerce 条目先失败）。
- 基线中另外 3 个失败已修复：
  - 关系 1:1 失败：已同步 Manifest，见第 6 节第 1 项。
  - 模型字节摘要与结构摘要：已重新批准，见待确认第 3 项。
- 新增 `Finance service artifacts are exact per-database projections…`：逐份核对六个已提交服务模型等于逻辑模型投影，并检查表数、无跨库关系、每张表的归属。
- 另外：
  - `pdmaner-canonical-paths.test.mjs`：2 / 2 通过。
  - `generate-finance-model.mjs --check`：六份模型均为最新。
  - 六份 schema 等于 `renderSchema(model)`。
  - `node scripts/verify-local-environment.mjs`：只剩与基线相同的 4 行 Gateway 文档引用。

### 4. 历史与旧根

- `git log --follow` 的 `JournalPostingServiceImpl.java`（ledger）可追溯 `5c678730 → e80e2085 → 3cbbfcef → c5424981 → e900c342`。
- `ChannelCostPlanServiceImpl.java`（billing）可追溯 `5c678730 → 0696b4fe`。
- `ls finance-service` 返回 No such file or directory。

### 5. R4 共库过渡

- `table-ownership.yaml` 中 `t_account` 归 ledger；account 的 `transitionalSharedReads.mor_ledger` 只有 `t_account`、`t_chart_account`、`t_ledger_book`，不计入 265 张物理表。
- `generate-table-ownership.mjs --check` 输出 current（265 + 56）。
- 五个 `ArchitectureAutoTest` 的 R1–R12 均为 0 违规，无新增 known-deviations。
- 第 11b 步删除该段，account 改连 `mor_account`。

### 环境恢复后复跑（最终代码，team-lead 通知"环境已恢复"后）

- 测试库：四个库与五个账号在恢复后仍在（`mor_ledger` 29、`mor_billing` 9、`mor_reconciliation` 18、`mor_tax` 24 张表；`t_chart_account` 29 行），未重复执行 DDL。
- 命令：`cd paytech && ./mvnw -B -o clean verify -pl tools/arch-rules,ledger/paytech-ledger-test,billing/paytech-billing-test,account/paytech-account-test,reconciliation/paytech-reconciliation-test,tax/paytech-tax-test -am -Dtest='com/paytech/{ledger,billing,account,reconciliation,tax,arch}/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…/Code-MoR/local-environment`。
- 结果：BUILD SUCCESS，用时 43.9 s。

  | 模块 | 执行数 | 失败 / 错误 / skipped |
  |---|---|---|
  | paytech-ledger-test | 190 | 0 |
  | paytech-billing-test | 71 | 0 |
  | paytech-account-test | 21 | 0 |
  | paytech-reconciliation-test | 17 | 0 |
  | paytech-tax-test | 17 | 0 |
  | paytech-arch-rules | 55 | 0 |

- app 启动：五个 exec jar 以 mock Profile 启动（ledger 28105/29105、billing 28113/29113、account 28114/29114、reconciliation 28115/29115、tax 28116/29116），启动耗时 3.1–4.9 s，日志 0 条 ERROR。管理端口 `/actuator/health`、`/health/liveness`、`/health/readiness` 均为 `{"status":"UP"}`。进程已全部停止。

### 本机一次性操作（不在仓库中，2026-10-17T18:03:44Z）

用 root 对 `mor-local-mysql-1` 执行五份测试 DDL（密码取自 `.env.local`，未打印）：

```bash
docker exec -i -e MYSQL_PWD="$P" mor-local-mysql-1 mysql -uroot --default-character-set=utf8mb4 < paytech/<svc>/paytech-<svc>-test/src/test/resources/init/ddl.sql   # ledger billing reconciliation tax account
```

- 结果：`mor_ledger` 29 张表、`mor_billing` 9 张、`mor_reconciliation` 18 张、`mor_tax` 24 张。
- 授权：`ledger_app` 等四个账号为 `SELECT, INSERT, UPDATE, DELETE`；`account_app` 只有 `SELECT ON mor_ledger.*`。
- 种子：ledger 52 行保留数据，billing 1 行 USD。
- `mor_finance` 与 `finance_app` 未改动。

## 3. 五个 api 的合同清单

| api | 合同（HTTP 前缀与方法） | 共享类型 |
|---|---|---|
| paytech-ledger-api | `FinanceEntityCommandApi` `POST /finance-entities/v1`；`FiscalCalendarCommandApi` `POST /fiscal-calendars/v1`、`/{id}/periods`；`AccountingPeriodCommandApi` `POST /accounting-periods/v1/{id}/open`；`PeriodCloseApi` `/accounting-periods/v1/{periodId}/closes`、`/reopens`、`/closes/{no}/approve`、`/reject` 与查询；`LedgerBookCommandApi` `POST /ledger-books/v1`、`/{id}/accounts`；`LedgerBookQueryApi` `GET /ledger-books/v1`、accounts、journal-lines、journal-entries；`TrialBalanceApi` `/ledger-books/v1/{bookId}/periods/{periodId}/trial-balances`；`TrialExceptionApi` `/trial-balance-exceptions/v1/{id}/projection-rebuild`（含 approve）；`AccountingRuleBundleApi` `/accounting-rule-bundles/v1/{code}/versions` 与 submit / approve / reject / 查询；`ManualAdjustmentApi` `/manual-adjustments/v1` 与 approve / reject / 查询 | 10 个接口，请求 / 响应 DTO 共 36 个 |
| paytech-billing-api | `ChannelCostPlanApi` `/channel-cost-plans/v1`：创建方案、版本创建 / submit / approve / reject、方案与版本查询 | 枚举 `AmountSource`、`EconomicsType`（+MANUAL_ADJUSTMENT）、`AutoInstructionType`；载荷 `fact.EconomicFactPayload`、`fact.EconomicFactLinePayload` |
| paytech-account-api | `BalanceQueryApi` `GET /balances/v1/clients/{clientId}`、`/partners/{partnerId}` | 枚举 `OwnerType`、`OwnerRole`、`BalanceBucket`；`constant.StandardAccountCodes`；`rule.AccountIdentityRule`（过渡） |
| paytech-reconciliation-api | 暂无合同（只有 `package-info`） | — |
| paytech-tax-api | 暂无合同（只有 `package-info`） | — |

## 4. `TransactionPostingService` 新入参 DTO（给第 11a 步）

`List<PostedInstructionView> post(TransactionPostingCommand command)`。`command.getFact()` 为 `com.paytech.billing.api.fact.EconomicFactPayload`，调用方事务内执行的语义不变。

| 字段 | 类型 | ledger 的用法 / 必填 |
|---|---|---|
| factId | Long | 幂等键（`t_accounting_instruction.economic_fact_id`）、Input Hash、拒绝消息；必填 |
| factType | String（`EconomicsType` 代码） | 必须为 PAYMENT；必填 |
| sourceSystem / sourceEventKey / sourceVersion / sourcePayloadHash | String / String / Long / String | 冻结到 AUTO 指令来源 Tuple；必填 |
| paymentSucceededTime | Instant | 过账日期与生效规则版本；PAYMENT 必填 |
| clientId / entityNo / currency / grossAmount | Long / String / String / BigDecimal | CLIENT Owner、OE 账簿、币种、GROSS_AMOUNT；必填 |
| localPartnerId / partnerAgreementOeId / financeEntityId | Long | LOCAL_PARTNER、PLATFORM_FINANCE_ENTITY Owner 与平台账簿；规则需要时必填 |
| channelAccountId | Long | CHANNEL_ACCOUNT Owner（reconciliation `t_channel_account.id`，由 billing 解析）；规则需要时必填 |
| lines[] | `EconomicFactLinePayload` | `lineNo`、`componentType`（按 `AmountSource` 代码匹配规则行）、`postedAmount` 必填；`partyType` / `partyId`（SELLER_TAX 的税务机关） |

第 11a 步需补充的内容：

- 事件版本字段：`factVersion` 或 `supersedesFactId`。
- 由 billing 登记时调用 `EconomicFactInvariants`。
- billing 解析渠道清算账户时，必须确认账户 ACTIVE 且 `entityNo` 等于事实 OE。这是原 ledger 内的检查，本步已不在 ledger 中。

## 5. 第 11b 步需改的跨库读清单

本步共库，均合法。

- **ledger `AccountMapper`**：`insert`、`selectById`、`selectByIds`、`selectByIdsForUpdate`、`selectByIdForUpdate`、`selectByIdentity`、`applyPosting`、`rebuildProjection` 读写余额列；`selectPage` / `countPage` 联 `t_chart_account`；`selectProjectionChecks` 比对余额与分录；`selectJournalTotals`。
- **ledger `JournalLineMapper`**：`selectAccountLines`、`selectEntryLines`、`selectTrialAggregates`、`selectProfitCloseBalances` 均联 `t_account`。
- **ledger `PeriodCloseMapper.countNonZeroTreasuryAccounts`**：读 `t_account.net_balance`。按 04 §6 改调 account-api consistency。
- **ledger `PeriodCloseMapper.countPendingCompensationEvents`**：SQL 未改，本库只有 ledger 自己的补偿事件，已自然只统计本库。04 §6 要求增加 account / billing 追平检查。
- **ledger 写余额的事务**：`JournalPostingServiceImpl`、`TransactionPostingServiceImpl#resolveAccount`、`PeriodCloseServiceImpl#ensureResultAccount`、`TrialExceptionServiceImpl#approveProjectionRebuild`，按 04 §4 拆分。
- **account-core `BalanceAccountMapper`**：`selectOperatingEntityBook`（`t_ledger_book`）、`selectOwnerBalances`（`t_account` ⋈ `t_chart_account`），改为自有 `t_account_balance` 加 ledger-api。
- **account-api `AccountIdentityRule`**：随账户身份收回 account-core / ledger。
- **account-core 的 `NormalSide` 副本**：一并收口。
- **arch-rules `transitionalSharedReads.account`**：删除。
- **account-test**：`LedgerFixtureJdbc`、`BalanceAccountMocker`、`AccountDataCleaner` 经 ledger 测试账号写共库，改为自有库。

## 6. 与设计的偏差

1. **PDManer Manifest 既有漂移已同步。** 已提交的 Finance 模型与 SQL 比生成器新：`close_hash` 可空，`step_type` 多两个重开步骤，`uk_period_reopen_target` 被手改为非唯一 `idx_` 但关系仍是 1:1，这正是基线第 12 个失败的原因。
   - 本步把 Manifest 改为与已提交 DDL 一致，关系改为 n:1，因此重新生成的 DDL 与原 SQL 逐表相同。
   - 逐表比对的结果：只有更名的经济事实表、`t_accounting_instruction`，以及 `t_currency_spec` 的表注释（注明只读副本）不同，其余逐字一致。
2. **测试读取的模型。** 不再提交单一 `finance.pdma.json`。finance-model / scenario-doc 测试改读生成器内存中的 84 表逻辑模型，另以新用例逐份核对六个服务模型。原测试"不导入生产生成器"的独立性有所减弱。
3. **`FINANCE_PHYSICAL_DECISIONS.md` 未拆成五份。** 它整体随 ledger 搬迁，并加了第 07 步切分说明，因为正文描述的是 84 表逻辑决策。
4. **管理端口独立。** 按任务卡把管理端口设为业务端口 + 1000，Finance 原来与业务端口共用。为此 `<Svc>ApplicationTest` 改为经 `@LocalManagementPort` 访问 Actuator，业务端口上的 `/actuator*` 404 断言保留。
5. **Redis 测试前缀** 用固定的 `ledger-mock` / `billing-mock`，没有用 10 §5.2 的 `<svc>-test-${random.uuid}`，因为清理器以 `@Value` 读前缀，随机值每次解析都不同。
6. **生产代码中的副本与改动：**
   - reconciliation-core 复制 `ReferenceDataStatus`，account-core 复制 `NormalSide`。
   - ledger 新增 `LedgerTestMapperConfiguration` 一类测试配置，因为生产 `@MapperScan` 不再覆盖测试 Mapper。
   - `AccountIdentityRule.checkEntityId` 改为接收主体类型代码（字符串），使 api 不依赖 ledger 枚举；行为与消息不变。
7. **ledger 的保留行与清理表。** 保留行由 45 增至 52，加入种子表 `t_close_account_rule` 的 7 行；原清理器漏掉了该表，10 §3.1 也要求补入。ledger 清理器同时覆盖 `t_compensation_event`。

## 7. 待确认

1. `fact_type` 采用"`economics_type` 更名并补 MANUAL_ADJUSTMENT"的解读，没有并存两列。`status` 本期只有 `RECORDED`，表仍为不可变表。
   - `MANUAL_ADJUSTMENT` 事实所需的订单锚点可空性与 CHECK 分支，留第 11a 步定义。
   - `t_economic_fact` 未加 `channel_account_id` 列，只有 DTO 携带该字段。第 11a 步登记时决定是否落库。
2. 明细行表的 `transaction_economics_id` 列、reconciliation 表中同名列与索引名 `idx_instr_economics` 均未改名，任务卡只要求改 instruction 一列。
3. finance oracle 的 `EXPECTED_TOTALS` 与两个摘要按新逻辑模型重新批准（基线时两个摘要已失败），请确认。
4. `AccountIdentityRule` 放在 account-api（任务卡的兜底方案）。规则"接口加 DTO 注入"在 ledger-app 内无法装配实现，除非依赖 account-core 或远程调用。
5. ledger-core 与 billing-core 依赖 `paytech-platform-support-api`，会传递引入其已登记 R2 偏差的 jackson / reactor-core；Finance Enforcer 未禁止这两项。
6. 初始化脚本的五个账号为可选创建，因为 compose 未传入变量，且本步不能改 compose。需要后续把 `MOR_{LEDGER,BILLING,ACCOUNT,RECONCILIATION,TAX}_DB_PASSWORD` 加进 `compose.yaml` 的 mysql environment。
7. data-service 的 CDC 白名单与 Flink 演示管道仍指向 `mor_finance.t_currency_spec`（第 09 步遗留"07 步后改为 platform-support / mor_platform"）。本步未改 data-service 与 local-environment 的 flink 文件。
8. agent-docs 的 `FINANCE_DATABASE.md` 等目录文档仍用旧表名（09 §5.1 不在本期范围）；场景文档 `FINANCE_REAL_WORLD_SCENARIOS.md` 因测试需要已同步改名并补 `status`。
9. 环境：已于环境恢复后复跑并通过（见第 2 节"环境恢复后复跑"）；原计划为重跑 Finance 五服务 `-pl ledger/paytech-ledger-test,billing/paytech-billing-test,account/paytech-account-test,reconciliation/paytech-reconciliation-test,tax/paytech-tax-test -am` 与第 2 节第 2 项的 app health 探测；全 Reactor 由主会话合入后执行。
