# account

账户服务（V2，第 07 步由 `finance-service` 拆出，第 11b 步分库，2026-10-19 改为明细账）：Client / Local Partner / 银行现金的 Owner
账户明细账，是 Owner 余额与可用额度的实时权威；ledger 只记控制科目（[2026-10-18 决定](../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)、
[设计](../../design-discuss/finance-account-ledger-v3/design.md) 第 4、8 节、[技术方案](../../requirements/20261019/finance-account明细账与ledger控制科目重构/technique.md) 第 2 节）。

- spring.application.name：`account-service`；端口 18114，管理端口 19114。
- 数据库：自有 `mor_account`（账号 `account_app`，只有本库 DML），9 张表：明细账 `t_owner_account`、`t_account_posting_stream`、
  `t_account_posting`、`t_account_entry`、`t_balance_applied_fact`、`t_balance_movement`，基础设施 `t_consumed_event`、
  `t_consumed_aggregate_version`、`t_compensation_event`。不连接其他服务的库，也不调用 ledger 或 billing 的 HTTP 接口。
- 物理模型：[account.pdma.json](docs/database/account.pdma.json)（直接维护 JSON，不由脚本生成；结构门禁 `scripts/pdmaner/account-model.test.mjs`）；
  Schema 在 [docs/database/sql](docs/database/sql)（当前完整 `account-schema.sql`，变更 `changes/2026-10-19-account-subledger.sql`）；
  余额查询语义见 [余额查询接口](docs/balance-query-api.md)。

| 模块 | 职责 |
|---|---|
| paytech-account-api | `BalanceQueryApi`、`BalanceMovementApi`、`AccountPostingApi`、`OwnerAccountQueryApi`；`AccountPostedPayload`；`OwnerAccountType`、`OwnerType`、`BalanceBucket`、`MovementSourceType` 等稳定 Code 与错误码 |
| paytech-account-core | 入账 `BalanceApplicationService`（映射规则 `FactBalanceMappingRule`）、桶移动、到期转可用、按流重建、查询；共用写入步骤 `AccountPostingWriter`；`BillingFactRecordedBalanceHandler`（MQ 消费）与 `AccountPostedHandler`（补偿发布）；包根 `com.paytech.account` |
| paytech-account-app | 启动类、Controller 与 Converter、XXL-Job Handler `accountBalanceMaturity`、DataSource / MyBatis / TypeHandler、paytech-messaging 消费与发布端、paytech-compensation-event、Snowflake |
| paytech-account-test | auto 测试；扮演 billing 的事件发布 Probe、`AccountPostedProbe`（观察 account.events）、`init/ddl.sql` |

## 链路

- **入账**：`billing.events` → `account.billing.hash` → `account.billing.p0..p3`（`strictOrder=false`）。每条到达的事实在消费事务内：
  锁 `t_balance_applied_fact`（fact_id）→ 锁事实主体 + 币种的入账流 `t_account_posting_stream`（分片 0）分配连续 `posting_sequence` →
  按账户主键升序 CAS 余额；同一事务写批次、流水、处理记录与补偿事件 `ACCOUNT_POSTED`。不影响 Owner 的事实也生成 `entry_count = 0` 的批次。
- **版本替代**：被替代事实已入账时，替代事实的批次先写冲回行（`reverses_entry_id` 指向原流水，方向相反，同账户）再写新行；
  被替代事实后到时只生成 0 流水的 SKIPPED 批次。映射不出合法流水的事实进入 `account.billing.dlq`。
- **发布**：`AccountPostedHandler` 事务外按事件键 `ACCOUNT_POSTING:{OE|FE}.{entityId}.{currency}.{shardNo}:AccountPosted:{sequence}`
  读批次、流水与来源重建载荷，发布到 `account.events`（发布侧按聚合串行，未路由 FAIL），Broker 确认后写 `published_time`。
- **桶移动**：`POST /balance-movements/v1`，按 `(source_type, source_ref)` 幂等（首次 201、重复 200 replayed）；一个短事务内锁流、锁账户、
  检查状态与余额，写移动、`BALANCE_MOVEMENT` 批次与转出 / 转入两条流水；ledger 收到后只推进进度。
- **到期转可用**：XXL-Job `accountBalanceMaturity` 扫描 `maturity_status = PENDING` 且到期的事实，按每个 PENDING 应付账户在该事实上的
  净贷方金额（扣除冲回）做 `FACT_MATURITY` 移动（`source_ref = {factId}:{factVersion}:{accountId}`），完成后标记 MATURED。
- **账户状态**：`ACTIVE / FROZEN / CLOSED` 只约束桶移动命令；事实照常入账，CLOSED 账户入账记 WARN。本期没有改状态接口。
- **账户号**：`OwnerAccountType` 目录带科目分级编号（静态属性，ledger 测试核对与 STANDARD 一致）；开户的同一事务按「主体 + 币种 + 科目代码」
  序列生成 `t_owner_account.account_no`（如 `AE001-USD-22010101-S000001`，Finance Entity 主体段为 `FE{entityId}`），创建后不变，只用于显示与搜索，
  不进入 `AccountPosted` 载荷（[2026-10-19 决定](../../agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md)第 2 节第 7、8 条）。
- **重建**：`POST /account-postings/v1/streams/rebuild` 每条流锁流行后按 `t_account_entry` 重算账户余额，以旧版本 CAS 加一；不删不改流水。

## 接口

| Method / Path | 说明 |
|---|---|
| `GET /balances/v1/clients/{clientId}`、`/balances/v1/partners/{partnerId}` | 分类余额；Owner 在该 OE + 币种下没有账户为 400 `OWNER_ACCOUNT_NOT_FOUND`，缺少的桶为 0 |
| `POST /balance-movements/v1` | 桶移动命令；`BALANCE_INSUFFICIENT`、`ACCOUNT_NOT_FOUND`、`ACCOUNT_NOT_ACTIVE`、`MOVEMENT_NOT_ALLOWED`（400），并发 409 |
| `GET /account-postings/v1/progress` | 事实应用进度与各流最大序号（ledger 关账） |
| `GET /account-postings/v1` | 按流与起始序号读取批次（ledger 追平，limit ≤ 200） |
| `GET /account-postings/v1/{postingId}` | 批次详情；不存在 400 `POSTING_NOT_FOUND` |
| `POST /account-postings/v1/streams/rebuild` | 按流重建；无流 400 `STREAM_NOT_FOUND` |
| `GET /owner-accounts/v1`、`GET /owner-accounts/v1/{accountId}/entries` | Owner 账户与流水分页；账户不存在 400 `ACCOUNT_NOT_FOUND` |

全部为内部接口，未接 Gateway 与 Owner / OE 资源授权前不得对外发布。

## 运行

环境变量：`ACCOUNT_DB_*`、`ACCOUNT_RABBITMQ_*`、`ACCOUNT_SNOWFLAKE_REGION_ID` / `ACCOUNT_SNOWFLAKE_WORKER_ID`、
`ACCOUNT_COMPENSATION_INSTANCE_ID`、`PAYTECH_INSTANCE_ID`（AccountPosted 信封 instanceId）；本服务不使用 Redis，不再有
`ACCOUNT_LEDGER_SERVICE_URL`、`ACCOUNT_BILLING_SERVICE_URL`。XXL-Job 调度中心与执行器参数沿用组件默认，由部署注入。

数据库结构来自 `docs/database/sql/account-schema.sql`（或测试 `init/ddl.sql`），由开发者人工执行；应用和自动测试都不会自动建表。
已有 `mor_account`（2026-10-18 的 8 张表，无业务数据）按 `changes/2026-10-19-account-subledger.sql` 变更。

## 发布前仍需完成

- 接入统一入站安全上下文并执行 Owner/OE 资源授权；路径 ID 不是身份凭证。Client/Partner 只能读取获授权的自身数据，Operations 需要明确数据范围。
- 验证敏感财务数据的访问审计和 metadata-only 日志策略，并由 Gateway/BFF 只暴露符合产品身份模型的入口。
- 桶移动的调用方（出款、缴税）与 LOCKED 长期未释放告警随各自切片实现（设计 8.3）。
