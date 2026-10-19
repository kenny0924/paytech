# 任务卡 A · account 明细账实现

先读 `00-common.md`、`T0-report.md`（合同已就绪）。设计：`technique.md` 2.1（链路与事务、序号与分片、account 规则与目录）、2.2 account-api、2.3 account 表；`design-discuss/finance-account-ledger-v3/design.md` 第 4、8.1、8.5、8.7 节。

## 允许改动的路径

`paytech/account/**`（api 模块只允许为实现需要做小修并在报告中列出）、`scripts/pdmaner/account-model.test.mjs`。

## 1. 模型与 SQL

- `paytech/account/docs/database/sql/account-schema.sql`：按 `REQ/db/account/ddl.sql` 的 6 张新表 + 原 3 张基础设施表重写（9 张，完整建库文件，注释逐字一致）；新增 `docs/database/sql/changes/2026-10-19-account-subledger.sql`（内容 = `REQ/db/account/ddl.sql`）；`paytech-account-test/src/test/resources/init/ddl.sql` 同步（保留建库、建用户、授权头）。
- `docs/database/account.pdma.json`：直接改 JSON（模型已脱离生成器）：实体按新表（字段、类型、默认值、注释、唯一键 / 索引与 SQL 逐字一致）；库内关系：`t_account_entry.posting_id → t_account_posting.id`、`t_account_entry.account_id → t_owner_account.id`、`t_account_entry.reverses_entry_id → t_account_entry.id`、`t_balance_applied_fact.posting_id → t_account_posting.id`、`t_balance_movement.posting_id → t_account_posting.id`、`t_account_posting_stream` 与 `t_account_posting` 按流身份（非主键，不画关系，只在图注说明）；图组：「余额权威与事实应用」改名「明细账：账户、批次与流水」含 6 张表与上述关系，「可靠执行与消费幂等」不变；关系 ID 用既有 `uuid5(NAMESPACE_URL, 'code-mor/account/database')` 规则（见该测试文件）。更新 `scripts/pdmaner/account-model.test.mjs` 的期望表集合与关系列表，`node --test scripts/pdmaner/account-model.test.mjs` 通过。

## 2. core

1. 删除：`integration/ledger/**`、`integration/billing/**`（重放客户端）、`BalanceReconciliationService` 及 Impl、`BalanceDiscrepancy` DO / Mapper / Repository、`BalanceBookService` 中按账簿的方法、`ReconcileBalancesProcessor`、`GetBalanceConsistencyProcessor`、`ListBookBalancesProcessor`、`ext/job/BalanceReconciliationJobHandler`、`LedgerBookRef` 等只服务旧链路的 domain 类；`paytech-account-core/pom.xml` 删除 `paytech-ledger-api` 与 `paytech-billing-api` 以外不再需要的依赖（`paytech-billing-api` 保留，仅为事件载荷）。
2. DataObject / Mapper / Repository：`OwnerAccount`、`AccountPostingStream`、`AccountPosting`、`AccountEntry`、`BalanceAppliedFact`（改）、`BalanceMovement`；枚举 `AccountStatus { ACTIVE, FROZEN, CLOSED }`、`PostingSourceType`、`MaturityStatus`，TypeHandler 在 `AccountPersistenceConfiguration` 登记。
3. `BalanceApplicationService.apply`（消费事务内）：锁顺序 ① `t_balance_applied_fact` 按 `fact_id` 行锁（沿用）→ ② `t_account_posting_stream` 行锁（不存在则插入；主体 = 事实 `entityNo`，币种 = 事实币种，`shard_no = 0`）分配 `posting_sequence` → ③ 映射效果，`t_owner_account` 不存在则按 `OwnerAccountType` 校验后开户（ACTIVE），按 ID 升序 CAS；写批次（`entry_count` 可为 0）、流水（`fact_line_no`）、`applied_fact.posting_id`，补偿事件 `ACCOUNT_POSTED`（`eventKey = AggregateKey.of("ACCOUNT_POSTING", aggregateId).eventKey("AccountPosted", postingSequence)`，参照 billing `EconomicFactRecordedHandler.buildEvent`）。版本替代：被替代事实 APPLIED 时在同一批次先写冲回行（`reverses_entry_id` 指向原流水，方向相反，同账户），再写新版本行；SKIPPED / PENDING_SUPERSEDED 规则沿用，SKIPPED 也生成 0 流水批次。`balance_available_time` 落库，有 PENDING 贷方流水且时间非空 → `maturity_status = PENDING`，否则 NOT_APPLICABLE。CLOSED 账户入账记 WARN。
4. `FactBalanceMappingRule`：删除 CHANNEL 目标；PAYMENT / MANUAL_ADJUSTMENT / COST_ADJUSTMENT 规则按 technique 2.1；Owner 账户类型校验用 `OwnerAccountType`。
5. `AccountPostedHandler`（`CompensationEventHandler`，`eventType = ACCOUNT_POSTED`）：事务外按事件键读批次 + 流水重建 `AccountPostedPayload`，`DomainEventPublisher` 发布（信封见载荷类注释），确认后写 `published_time`；策略同 billing；需要发布端配置（`messaging.publisher`，照 billing `application.yml`）。
6. `BalanceMovementService` + `MoveBalanceProcessor`（`POST /balance-movements/v1`）：Processor 事务外校验（`MovementSourceType` 允许经 API、桶对合法、金额正且币种精度），领域服务一个 `TransactionTemplate` 短事务：按 `(source_type, source_ref)` 查已存在 → 幂等返回 `replayed = true`；流行锁；转出 / 转入账户按 ID 升序锁与 CAS（转出不足 → `BALANCE_INSUFFICIENT`；账户不存在 → `ACCOUNT_NOT_FOUND`；非 ACTIVE → `ACCOUNT_NOT_ACTIVE`）；写移动、批次（`BALANCE_MOVEMENT`）、两条流水、补偿事件。并发唯一键冲突 → `ConcurrentRequestException`（409）。
7. 到期转可用：`BalanceMaturityService` + XXL-Job Handler `accountBalanceMaturity`（照原 `accountBalanceReconciliation` 的 Handler 形态）：查 `maturity_status = PENDING` 且 `balance_available_time <= now` 的事实（分页），每条事实：对其流水中记入 `*_PAYABLE / PENDING` 账户的净贷方金额（扣除同事实冲回）逐账户调用 `FACT_MATURITY` 移动（`source_ref = {factId}:{factVersion}:{accountId}`），全部成功后标 MATURED；按 `source_ref` 幂等。
8. 查询：`QueryClientBalanceProcessor` / `QueryPartnerBalanceProcessor` 不再调 ledger，Owner 在该 OE + 币种下无任何账户 → `FailException(OWNER_ACCOUNT_NOT_FOUND)`，有账户时缺的桶为 0；新增 `ListOwnerAccountsProcessor`、`ListAccountEntriesProcessor`、`GetAccountPostingProcessor`、`ListAccountPostingsProcessor`（追平用，含流水）、`GetPostingProgressProcessor`（FINANCE_ENTITY 主体时 streams 含 `finance_entity_id` 等于该主体的事实所在 OE 流；`appliedFactCount` 排除 PENDING_SUPERSEDED）、`RebuildStreamBalancesProcessor`（按流锁 → 按流水重算 → CAS 加一；无流 → `STREAM_NOT_FOUND`）。全部经 `AbstractProcessor`，只读的不开事务。
9. app：Controller 按 API 一一对应（`BalanceMovementController`、`AccountPostingController`、`OwnerAccountQueryController`；删 `BalanceBookController`）；配置删 `LedgerClientConfiguration` / `BillingClientConfiguration`、加发布端与 XXL Handler；`application.yml` 删 `ledger-service` / `billing-service` 客户端与 `ACCOUNT_LEDGER_SERVICE_URL`、`ACCOUNT_BILLING_SERVICE_URL`，加 `messaging.publisher`（照 billing）；注释同步。
10. README：按新职责改写（9 张表、事件发布、桶移动、成熟 Job、接口列表、环境变量）。

## 3. 测试（`paytech-account-test`）

- 删除 ledger / billing Mock 与 Mocker、`AccountModuleBoundaryTest` 依赖集更新（core 不再含 ledger-api；app 新增 `paytech-compensation-event` 已有）；`AccountDataCleaner` 改 9 张表名；`AbstractBalanceTest` 去掉账簿 Mock。
- 新增 `AccountPostedProbe`（测试内消费 `account.events`：声明测试专用队列绑定 fanout，读取并解析信封与载荷；每例 BeforeEach 清空），断言序号连续与载荷字段。
- 场景（经真实入口：MQ Probe、HTTP、Job Handler）：事实顺序 / 乱序 / 重复投递 / 替代先到与后到（冲回行与 `reverses_entry_id`）/ 0 流水批次（COST_ADJUSTMENT）/ CLOSED 账户入账告警 / 映射非法进死信；桶移动：首次 201、重复 200 replayed、不足 400、非法桶对 400、CASH_ACCOUNT 锁定与释放、FROZEN 拒绝；成熟 Job：到期移动、未到期不动、重复执行不重复、冲回后净额；重建：余额一致且版本加一；余额查询：无账户 400、缺桶为 0；进度查询（OE 与 FE 两种）、Owner 账户与流水分页、批次详情、追平列表；全部既有保留场景按新口径重写。
- 本机 `mor_account` 需先按新 `init/ddl.sql` 重建：**只有主会话在本卡末尾写明「已授权重建」时才可用 root 执行**（命令见下）；否则停在报告中说明。

## 4. 验证

`./mvnw -B -o verify -pl account/paytech-account-test -am` 全部通过；`node --test scripts/pdmaner/account-model.test.mjs` 通过；`paytech/scripts/verify-reactor.sh` 的表归属检查通过（T0 已登记新表）。报告 `REQ/tasks/A-report.md`。

## 本机数据库重建授权

（主会话填写）

**已授权重建（主会话，2026-10-19，用户确认「授权」）。** 只限本机 local-environment 的 `mor_account`，不碰 mor-test。在仓库根执行（root 密码只从 `.env.local` 加载，不打印）：

```sh
set -a; source local-environment/.env.local; set +a
docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot -e "DROP DATABASE IF EXISTS mor_account"
docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot < paytech/account/paytech-account-test/src/test/resources/init/ddl.sql
docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_account'"
```

期望 9 张表。改完 `init/ddl.sql` 后再执行；若之后又改了表结构，重复以上三步。RabbitMQ 测试沿用默认 vhost `/mor-local`（B 线用 `/mor-e2e`，互不干扰）。
