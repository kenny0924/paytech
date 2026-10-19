# 任务卡 B · ledger 控制科目实现与模型脱离生成器

先读 `00-common.md`、`T0-report.md`。设计：`technique.md` 2.1（ledger 消费、首条序号校验与追平、控制科目过账、关账两段检查、枚举归属）、2.2 ledger-api、2.3 ledger；`system-design/finance-account-ledger-v3.md` 第 5、8.2、8.8、8.9 节。

## 允许改动的路径

`paytech/ledger/**`（api 模块只允许为实现需要做小修并在报告中列出）、`scripts/pdmaner/finance/**`、`scripts/pdmaner/finance-model.test.mjs`、`scripts/pdmaner/pdmaner-canonical-paths.test.mjs`、`scripts/pdmaner/oe-client-model.test.mjs`、新增 `scripts/pdmaner/ledger-model.test.mjs`。

## 1. 模型与 SQL

- `docs/database/sql/ledger-schema.sql`：按 `REQ/db/ledger/ddl.sql` 的字段 / 索引 / 注释变化改写建库文件（31 张表不变）；新增 `docs/database/sql/changes/2026-10-19-ledger-control-accounts.sql`（内容 = `REQ/db/ledger/ddl.sql` + `REQ/db/ledger/dml.sql` 合并，保持两段）；`ledger-initial-data.sql` 删除规则行 5202、5208、5315–5320 并改注释；`paytech-ledger-test/src/test/resources/init/ddl.sql` 同步（保留行 86 → 78，`LedgerDataCleaner.RESERVED_ROWS` 同步）。
- `docs/database/ledger.pdma.json` 脱离 Finance 生成器，照 account 的先例（见 `git diff scripts/pdmaner/finance/service-split.mjs scripts/pdmaner/finance/generate-finance-model.mjs scripts/pdmaner/finance/manifests/09-service-physical.mjs scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/pdmaner-canonical-paths.test.mjs` 与 `scripts/pdmaner/account-model.test.mjs`）：生成器删除 ledger（`FINANCE_SERVICES`、`SERVICE_TITLES`、`SERVICE_TABLE_VARIANTS`、`SERVICE_ADDED_TABLES`、`EXPECTED_SERVICE_TABLE_COUNTS`、manifest 09 的 `LEDGER_ACCOUNT` 等；manifests 01 / 03 / 07 的默认归属改为不投影；`READ_ONLY_COPIES.currency_spec` 只剩 billing；`ownerOf` 不变量相应调整），`finance-model.test.mjs` 的投影断言与 owners 计数更新，84 表逻辑模型与其摘要**冻结不改**；`pdmaner-canonical-paths.test.mjs` ledger 生成器置 null；新增 `ledger-model.test.mjs`（表集合与 `ledger-schema.sql` 一致、库内关系指向主键且有索引、每张表与关系在图中出现一次、标识唯一）。随后直接改 JSON：`t_instruction_line` 加 `account_entry_id` 字段与索引（跨库引用不画关系）、四处允许值注释、`t_ledger_account` 表注释；图不新增。`node --test scripts/pdmaner/*.test.mjs` 中本卡相关测试通过，既有 2 例 cf4a429c 漂移失败如仍存在须在报告中单列。
- README 按新职责改写。

## 2. core / app

1. 枚举：`LedgerAccount.ownerType` 改 `LedgerOwnerType`，`AccountingRuleLine.ownerRole` 改 `RuleOwnerRole`，`LedgerPersistenceConfiguration` TypeHandler 同步；`InstructionOrigin` 加 `ACCOUNT_POSTING`；`CloseStepType` 的 `RECONCILE_ACCOUNT_BALANCES` → `CHECK_ACCOUNT_POSTING_PROGRESS`；删除 `TrialExceptionServiceImpl.PROJECTION_REBUILD` 及整条链路（`Start/ApproveProjectionRebuildProcessor`、`ProjectionRebuildValidator`、`ProjectionRebuildContentHash`、`ProjectionSnapshot`、`TrialExceptionService.start/approve`、`TrialExceptionController`、`LedgerErrorCode` 中 T0 标注的 TODO Code）。
2. 消费：删除 `EconomicFactRecordedPostingHandler` 与其 Reader；新增 `AccountPostedPostingHandler`（`@DomainEventHandler(eventType = "AccountPosted", strictOrder = true, transaction = HANDLER_MANAGED)`，照 acquiring `PaymentSucceededListener` 的异常分类）+ `AccountPostedEventReader`（校验 schemaVersion、aggregateType、aggregateId 与载荷一致、aggregateVersion = postingSequence）。Handler 事务外：① 注入 `ConsumedEventRepository`，`lastVersion(aggregateKey)` 为空且 `postingSequence != 1` → 抛 `ConcurrentRequestException`；② `BALANCE_MOVEMENT` → 直接返回；③ `ECONOMIC_FACT` 且 `factApplyStatus = SKIPPED` → 返回；④ 经 billing-api `getEconomicFact(factId)` 读事实（版本不等 → `ConcurrentRequestException`），然后调用 `ControlAccountPostingProcessor`（或领域服务，事务内）过账。
3. 过账（`TransactionPostingServiceImpl` 改造）：指令头 `origin_type = ACCOUNT_POSTING`、`source_system = account`、`source_event_key = postingId`、`source_version = postingSequence`、`economic_fact_id = factId`；Owner 行：每条载荷流水 → 该科目代码的控制账户（账簿 = 事实 OE 账簿；科目按 `account_code` 查科目表、须 `posting_allowed`；账户 (book, chart, `owner_type` = 账簿主体类型, `owner_id` = 账簿主体, NONE) 不存在则开户），`account_entry_id = entryId`，`accounting_rule_line_id` 空；非 Owner 行：现有规则引擎（规则行角色改 `RuleOwnerRole`，删除 Owner 角色分支）；版本替代：`supersedesFactId` 非空时追加被替代事实已过账指令中 `accounting_rule_line_id` 非空行的反向行；每个 `AutoInstructionType` 一条指令，账簿路由不变（`PLATFORM_SERVICE_CHARGE_PLATFORM` 进 FE 账簿）；幂等：`uk_instr_source` 已存在则返回。过账日期：事实业务时间（PAYMENT `paymentSucceededTime`，其余 `recordedTime`）在日历时区的日期；所属期间 CLOSED → 其后最早 OPEN 期间，`posting_date` 保留；无 OPEN 期间 → `ConcurrentRequestException`。
4. 追平：`PostingStreamCatchUpProcessor`（`POST /posting-streams/v1/catch-up`）：事务外读 `lastVersion`，经 account-api `AccountPostingApi.listPostings(fromSequence = last + 1, limit)` 分页，逐批次按与消费相同的逻辑处理，每批次一个本库事务并在同一事务内 `ConsumedEventRepository.advance(aggregateKey, sequence)`；最多 `maxBatches`；account 不可用 503。新增 `integration/account` 客户端（替换 `BalanceConsistencyClient`），`AccountClientConfiguration` 类型改为 `AccountPostingApi`。
5. 身份矩阵 `AccountIdentityRule`、`CreateAccountProcessor`、`CreateRuleVersionProcessor`（拒绝 Owner 角色，现在枚举已无）、`OwnerAccountAdjustmentRule`（按科目代码集合拒绝控制科目，错误码不变）按 technique 2.1。
6. 关账：`StartPeriodCloseProcessor` 步骤 2 删除 account 调用；步骤 3 `CHECK_ACCOUNT_POSTING_PROGRESS`：对每本账簿事务外调 billing `getPendingEconomicFacts`（新字段 `publishedFactCount`）与 account `getProgress`，按 technique 2.1 三条通过条件逐流比对 `ConsumedEventRepository.lastVersion`，失败记录阻断项（流、两侧序号或计数）并失败关闭；`PeriodCloseServiceImpl` 删除渠道 / 银行非零数检查与相关 Mapper SQL。
7. 查询：`LedgerQueryServiceImpl` 删除 `listOwnerAccountTotals`；分录行与详情填 `accountPostingId` / `accountPostingSequence` / `accountEntryId`；`LedgerReadController` 删 account-totals。
8. app 配置：`messaging.consumers.account.partitions: 4`（删除 `billing` 消费者配置）、`LEDGER_ACCOUNT_SERVICE_URL` 保留（追平）、`LEDGER_BILLING_SERVICE_URL` 保留；注释同步。

## 3. 测试（`paytech-ledger-test`）

- 新增 `AccountPostedPublisher` Probe（扮演 account 发布到 `account.events`，序号可控，照 `BillingEventPublisher`）；`MockEconomicFactApi` 增加 `getEconomicFact` DTO；`MockAccountPostingApi`（进度、列表）替换 `MockBalanceBookApi`；Mocker 相应改名；`LedgerModuleBoundaryTest` 依赖集更新；`LedgerDataCleaner` 保留行 78。
- 场景：控制科目行与 `account_entry_id`、非 Owner 规则行、借贷平衡；替代净额；SKIPPED 批次；BALANCE_MOVEMENT 只推进；首条非 1 等待后补齐；缺口重投后补齐；重复投递；追平命令（含 account 不可用 503）；已关账期间顺延；无开放期间等待；人工调整拒绝控制科目；关账四种情况（billing 未发布 / 计数不等 / ledger 落后 / 追平通过）；规则版本创建、账户创建身份矩阵；query 接口新字段；PROJECTION_REBUILD 删除后试算异常相关测试调整；既有 setup / adjustment / trial / reopen 用例按五值 Owner 改写。
- 本机 `mor_ledger` 需按新 `init/ddl.sql` 重建：**只有主会话在本卡末尾写明「已授权重建」时才可执行**；否则停在报告中说明。

## 4. 验证

`./mvnw -B -o verify -pl ledger/paytech-ledger-test -am` 全部通过；`node --test scripts/pdmaner/ledger-model.test.mjs scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/pdmaner-canonical-paths.test.mjs`；`paytech/scripts/verify-reactor.sh`。报告 `REQ/tasks/B-report.md`。

## 本机数据库重建授权

（主会话填写）

**已授权重建（主会话，2026-10-19，用户确认「授权」）。** 只限本机 local-environment 的 `mor_ledger`，不碰 mor-test。在仓库根执行（root 密码只从 `.env.local` 加载，不打印）：

```sh
set -a; source local-environment/.env.local; set +a
docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot -e "DROP DATABASE IF EXISTS mor_ledger"
docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot < paytech/ledger/paytech-ledger-test/src/test/resources/init/ddl.sql
docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_ledger'; SELECT COUNT(*) FROM mor_ledger.t_accounting_rule_line"
```

期望 31 张表、规则行 36。改完 `init/ddl.sql` 后再执行；之后再改表结构就重复以上步骤。**RabbitMQ：本线所有测试运行前 `export MOR_RABBITMQ_VHOST=/mor-e2e`**（A 线同时在 `/mor-local` 跑 account 测试并发布 `account.events`，两线必须分开 vhost；`/mor-e2e` 已存在且 `mor_local` 有全部权限）。
