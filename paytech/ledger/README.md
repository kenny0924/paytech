# ledger

会计服务（V2，第 07 步由 `finance-service` 拆出）。拥有账簿、科目与科目账户身份、财务日历与期间、自动记账规则包、指令与分录、
人工调整 Maker-Checker、试算、期间关账与重开、Finance Entity。2026-10-19 起总账只保存 **控制科目**：Client / Local Partner 的明细在
account 明细账，总账每账簿每 Owner 科目一个以账簿主体为 Owner 的控制账户，分录由 account 入账批次（`AccountPosted`）驱动。
需求与设计见 [账务内核需求](../../requirements/20261015/finance-账务内核/requirement.md)、
[account 明细账与 ledger 控制科目重构技术方案](../../requirements/20261019/finance-account明细账与ledger控制科目重构/technique.md)、
[account / ledger 模型划分 V3](../../design-discuss/finance-account-ledger-v3/design.md) 与
[2026-10-18 决定](../../agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md)。

- spring.application.name：`ledger-service`；端口 18105，管理端口 19105。
- 数据库：`mor_ledger`（31 张表，含 paytech-messaging 消费端两张标准表）；`t_ledger_account` 只保存账户身份，余额由本库分录聚合。
- 物理模型：[ledger.pdma.json](docs/database/ledger.pdma.json)，2026-10-19 起**脱离 Finance 生成器直接维护**（门禁见
  `scripts/pdmaner/ledger-model.test.mjs`，建库 SQL 与模型逐字一致由 `scripts/pdmaner/oe-client-model.test.mjs` 核对）；Schema、初始化 DML
  与变更脚本在 [docs/database/sql](docs/database/sql)。Finance 逻辑模型（84 表）的物理决策仍集中在
  [FINANCE_PHYSICAL_DECISIONS.md](docs/database/FINANCE_PHYSICAL_DECISIONS.md)。

## 模块

| 模块 | 职责 |
|---|---|
| paytech-ledger-api | 内部 HTTP 契约：Finance Entity、财务日历与期间、账簿与科目账户、规则集、人工调整、试算与关账、过账流追平 `PostingStreamCommandApi`；`LedgerReadApi`（`/ledger/v1`：账簿、批量科目）；`LedgerOwnerType`、`RuleOwnerRole`、`LedgerErrorCode`、`AmountSource`、`AutoInstructionType` |
| paytech-ledger-core | 过账内核、`AccountPosted` 消费 Handler 与控制科目过账、追平、规则、人工调整、试算、关账、account / billing 出站客户端；包根 `com.paytech.ledger` |
| paytech-ledger-app | 唯一 Spring Boot 启动模块：Controller、DataSource / MyBatis / TypeHandler、Snowflake、Redis 锁、paytech-messaging 消费端、account / billing HTTP 客户端 |
| paytech-ledger-test | auto 测试、Mocker / Checker / 清理器、扮演 account 的 `AccountPosted` 发布 Probe、account / billing DTO Mock、`init/ddl.sql` |

- ledger-core 依赖 `paytech-account-api`（`AccountPosted` 载荷与过账流身份、过账进度 / 批次合同、`OwnerAccountType` 目录、余额桶与标准科目代码）、
  `paytech-billing-api`（计费事实类型、外部引用类型与事实详情 / 进度查询合同 `BillingFactQueryApi`）、`paytech-ledger-api`
  （金额来源 `AmountSource`、自动指令类型 `AutoInstructionType`）与 `paytech-platform-support-api`（币种规则状态）；不依赖其他服务的 core。
- 控制科目过账：`AccountPostedPostingHandler`（`HANDLER_MANAGED`、按过账流 `strictOrder`）消费 `account.events`（分区队列 `ledger.account.p0..p3`）。
  事务外：过账流尚无消费进度且序号不是 1 时等待重投；桶移动批次与 SKIPPED 批次只推进进度；其余经 billing `GET /billing/v1/facts/{factId}`
  读事实（版本不一致等待重投）。事务内生成 `origin_type = ACCOUNT_POSTING` 的指令并过账：Owner 行逐条来自 account 流水（`account_entry_id`），
  非 Owner 行按 STANDARD_TRANSACTION / STANDARD_ADJUSTMENT 规则；替代事实在同一指令内对被替代事实的正向规则行取反。规则明确拒绝的批次进入
  `ledger.account.dlq`，恢复走 `POST /posting-streams/v1/catch-up`（经 account `GET /account-postings/v1` 按序号补齐，每批次一个本库事务）。
- 过账期间按事实载荷的 businessTime（PAYMENT 为支付成功时间，人工 / 成本调整为批准时间，由 billing 决定）确定；该期间已关闭时顺延到其后最早的
  OPEN 期间，没有可用期间时等待。渠道清算账户 Owner 取事实外部引用 `CLEARING_ACCOUNT`，税务机关取 `SELLER_TAX` 行的收方。
- 过账内核不锁账户、不写余额：锁顺序为期间共享锁 → 账簿序号行排他锁；账户列表、试算与关账检查的余额一律由本库分录聚合。人工调整复核、期间创建、
  试算、关账与过账流追平另用 Foundation `AbstractLockProcessor` 的 Redis 资源锁（`AccountingLocks`）。
- 账户身份矩阵（`AccountIdentityRule`）：桶一律 NONE；Owner 控制科目（`CLIENT_PAYABLE`、`CLIENT_SECURITY_DEPOSIT`、`CLIENT_RECOVERY_RECEIVABLE`、
  `PARTNER_PAYABLE`、`PARTNER_RECOVERY_RECEIVABLE`）只能以账簿主体为 Owner，且规则行不得引用；其余科目只允许平台类 Owner。
- 人工调整不得涉及 Owner 控制科目（`OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN`），影响 Owner 余额的调整经 billing 登记 `MANUAL_ADJUSTMENT` 计费事实。
- 关账步骤 3 `CHECK_ACCOUNT_POSTING_PROGRESS` 在事务外调用 billing `GET /billing/v1/facts/progress` 与 account
  `GET /account-postings/v1/progress`：billing 无未发布事实、account 已应用事实数与最大 factId 等于 billing 已发布的，且 account 列出的每条过账流
  ledger 都已消费到最大序号，否则失败关闭。

## 构建与运行

在 `paytech/` 下用 JDK 25 执行：

```sh
./mvnw -B verify -pl ledger/paytech-ledger-test -am
java -jar ledger/paytech-ledger-app/target/paytech-ledger-app-1.0.0-SNAPSHOT-exec.jar
```

新库在 `mor_ledger` 人工依次执行 `docs/database/sql/ledger-schema.sql` 与 `ledger-initial-data.sql`；已建库环境按顺序执行
`docs/database/sql/changes/` 下尚未执行的变更脚本（最新为 `2026-10-19-ledger-control-accounts.sql`）。连接参数
`LEDGER_DB_URL` / `LEDGER_DB_USERNAME` / `LEDGER_DB_PASSWORD`，Redis `LEDGER_REDIS_HOST` / `LEDGER_REDIS_PORT` / `LEDGER_REDIS_PASSWORD`，
RabbitMQ `LEDGER_RABBITMQ_*`，出站 `LEDGER_ACCOUNT_SERVICE_URL` / `LEDGER_BILLING_SERVICE_URL`（Finance 后继服务不接服务发现），
Snowflake `LEDGER_SNOWFLAKE_REGION_ID` / `LEDGER_SNOWFLAKE_WORKER_ID`（缺失时启动失败）。Actuator 只在管理端口暴露
health / info / prometheus，不披露组件详情。

默认 auto 测试连接 local-environment 的 `mor_ledger`（账号 `ledger_app`，只有 SELECT / INSERT / UPDATE / DELETE）、Redis（前缀 `ledger-mock`）
与 RabbitMQ；account / billing 由 Loopback Mock HTTP 服务返回配置的 DTO。每例 BeforeEach 清空本服务的 account 分区队列并检查空基线与 78 行
初始化保留数据，AfterEach 清空 31 张表的测试写入。测试环境准备见 `paytech-ledger-test/src/test/resources/init/ddl.sql`；与其他服务并行跑测试时
用 `MOR_RABBITMQ_VHOST` 分开 vhost。

## 当前不包含

无 Nacos 与服务发现、无补偿 Handler 与 XXL-Job（Enforcer 禁止）；不发布任何领域事件。规则包只覆盖 PAYMENT、MANUAL_ADJUSTMENT 与
COST_ADJUSTMENT 事实，其他事实类型的批次被拒绝进入 DLQ。追平命令本期不接控制台。全部接口只能作为内部开发接口，不能直接发布给 Client、
Partner 或 Operations。
