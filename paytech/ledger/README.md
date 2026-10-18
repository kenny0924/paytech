# ledger

会计服务（V2，第 07 步由 `finance-service` 拆出）。拥有账簿、科目与科目账户身份、财务日历与期间、自动记账规则包、指令与分录、
人工调整 Maker-Checker、试算、期间关账与重开、Finance Entity。需求与设计见
[账务内核需求](../../requirements/20261015/finance-账务内核/requirement.md)、[技术方案](../../requirements/20261015/finance-账务内核/technique.md)、
[V2 账户与会计分库方案](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/04-finance-account-ledger-split.md)。

- spring.application.name：`ledger-service`；端口 18105，管理端口 19105。
- 数据库：`mor_ledger`（原 `mor_finance`，31 张表，含 paytech-messaging 消费端两张标准表）；第 11b 步起 `t_account` 更名为只保存身份的
  `t_ledger_account`，余额迁到 account-service 的 `mor_account`，account 不再连接本库。
- 物理模型：[ledger.pdma.json](docs/database/ledger.pdma.json)，Schema 与初始化 DML 在 [docs/database/sql](docs/database/sql)；
  Finance 逻辑模型（84 表）的物理决策仍集中在 [FINANCE_PHYSICAL_DECISIONS.md](docs/database/FINANCE_PHYSICAL_DECISIONS.md)。

## 模块

| 模块 | 职责 |
|---|---|
| paytech-ledger-api | 内部 HTTP 契约：Finance Entity、财务日历与期间、账簿与科目账户、规则集、人工调整、试算与关账；供 account 读取的 `LedgerReadApi`（`/ledger/v1`：账簿、批量科目、Owner 账户分录聚合余额）；`LedgerErrorCode` |
| paytech-ledger-core | 过账内核、`EconomicFactRecorded` 消费 Handler 与交易自动记账、规则、人工调整、试算、关账、account / billing 出站客户端；包根 `com.paytech.ledger` |
| paytech-ledger-app | 唯一 Spring Boot 启动模块：Controller、DataSource / MyBatis / TypeHandler、Snowflake、Redis 锁、paytech-messaging 消费端、account / billing HTTP 客户端 |
| paytech-ledger-test | auto 测试、Mocker / Checker / 清理器、扮演 billing 的事件发布 Probe、account / billing DTO Mock、`init/ddl.sql` |

- ledger-core 依赖 `paytech-billing-api`（金额来源、经济事实类型、经济事实载荷与 pending 合同）、`paytech-account-api`（Owner 类型 / 角色、
  余额桶、标准科目代码、consistency 合同）与 `paytech-platform-support-api`（币种规则状态）；账户身份矩阵 `AccountIdentityRule` 第 11b 步收回本服务；
  不依赖其他服务的 core。
- 交易自动记账（design/04 §5）：`EconomicFactRecordedPostingHandler` 消费 `billing.events`（分区队列 `ledger.billing.p0..p3`），按
  `fact_id + fact_version` 幂等，在一个 ledger 本地事务内生成 AUTO 指令并过账；规则明确拒绝的事实进入 `ledger.billing.dlq`。
- 过账内核不再锁账户、不再写余额（design/04 §4）：锁顺序为期间共享锁 → 账簿序号行排他锁；账户列表、试算与关账检查的余额一律由本库分录聚合。
  人工调整复核、期间创建、试算与关账另用 Foundation `AbstractLockProcessor` 的 Redis 资源锁（`AccountingLocks`）。
- 人工调整不得涉及 Client / Local Partner 账户（`OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN`），影响 Owner 余额的调整经 billing 登记
  `MANUAL_ADJUSTMENT` 经济事实；纯科目重分类不受影响。
- 关账步骤 2、3 在事务外调用 billing `GET /billing/v1/economic-facts/pending` 与 account `GET /balances/v1/books/{bookId}/consistency`：
  billing 无未发布事实、account 已处理的最大 factId 不小于 billing 已发布的最大 factId、两侧都没有非零渠道 / 银行账户，否则失败关闭。

## 构建与运行

在 `paytech/` 下用 JDK 25 执行：

```sh
./mvnw -B verify -pl ledger/paytech-ledger-test -am
java -jar ledger/paytech-ledger-app/target/paytech-ledger-app-1.0.0-SNAPSHOT-exec.jar
```

运行前在 `mor_ledger` 人工依次执行 `docs/database/sql/ledger-schema.sql` 与 `ledger-initial-data.sql`（已按第 07 步建库的环境改为执行
`docs/database/sql/changes/2026-10-18-ledger-account-split.sql`）。连接参数
`LEDGER_DB_URL` / `LEDGER_DB_USERNAME` / `LEDGER_DB_PASSWORD`，Redis `LEDGER_REDIS_HOST` / `LEDGER_REDIS_PORT` / `LEDGER_REDIS_PASSWORD`，
RabbitMQ `LEDGER_RABBITMQ_*`，出站 `LEDGER_ACCOUNT_SERVICE_URL` / `LEDGER_BILLING_SERVICE_URL`（Finance 后继服务不接服务发现），
Snowflake `LEDGER_SNOWFLAKE_REGION_ID` / `LEDGER_SNOWFLAKE_WORKER_ID`（缺失时启动失败）。Actuator 只在管理端口暴露
health / info / prometheus，不披露组件详情。

默认 auto 测试连接 local-environment 的 `mor_ledger`（账号 `ledger_app`，只有 SELECT / INSERT / UPDATE / DELETE）、Redis（前缀 `ledger-mock`）
与 RabbitMQ；account / billing 由 Loopback Mock HTTP 服务返回配置的 DTO。每例 BeforeEach 清空本服务的 billing 分区队列并检查空基线与 52 行
初始化保留数据，AfterEach 清空 31 张表的测试写入。测试环境准备见
`paytech-ledger-test/src/test/resources/init/ddl.sql`。

## 当前不包含

无 Nacos 与服务发现、无补偿 Handler 与 XXL-Job（Enforcer 禁止）；不发布任何领域事件（`BalanceMoved` 的消费在后续切片）。规则包只有 PAYMENT
交易规则：`MANUAL_ADJUSTMENT` / `COST_ADJUSTMENT` 经济事实在补齐规则前被拒绝进入 DLQ。全部接口只能作为内部开发接口，不能直接发布给 Client、
Partner 或 Operations。
