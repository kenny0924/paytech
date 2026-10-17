# ledger

会计服务（V2，第 07 步由 `finance-service` 拆出）。拥有账簿、科目与科目账户身份、财务日历与期间、自动记账规则包、指令与分录、
人工调整 Maker-Checker、试算、期间关账与重开、Finance Entity。需求与设计见
[账务内核需求](../../requirements/20261015/finance-账务内核/requirement.md)、[技术方案](../../requirements/20261015/finance-账务内核/technique.md)、
[V2 账户与会计分库方案](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/04-finance-account-ledger-split.md)。

- spring.application.name：`ledger-service`；端口 18105，管理端口 19105。
- 数据库：`mor_ledger`（原 `mor_finance`，29 张表）；本步 account-service 以只读账号共连本库（第 11b 步拆出 `mor_account`）。
- 物理模型：[ledger.pdma.json](docs/database/ledger.pdma.json)，Schema 与初始化 DML 在 [docs/database/sql](docs/database/sql)；
  Finance 逻辑模型（84 表）的物理决策仍集中在 [FINANCE_PHYSICAL_DECISIONS.md](docs/database/FINANCE_PHYSICAL_DECISIONS.md)。

## 模块

| 模块 | 职责 |
|---|---|
| paytech-ledger-api | 内部 HTTP 契约：Finance Entity、财务日历与期间、账簿与科目账户、规则集、人工调整、试算与关账 |
| paytech-ledger-core | 过账内核、交易自动记账、规则、人工调整、试算、关账；包根 `com.paytech.ledger` |
| paytech-ledger-app | 唯一 Spring Boot 启动模块：Controller、DataSource / MyBatis / TypeHandler、Snowflake、Redis 锁 |
| paytech-ledger-test | auto 测试、Mocker / Checker / 清理器、`init/ddl.sql` |

- ledger-core 依赖 `paytech-billing-api`（金额来源、经济事实类型与经济事实载荷 DTO）、`paytech-account-api`（Owner 类型 / 角色、
  余额桶、标准科目代码、账户身份矩阵）与 `paytech-platform-support-api`（币种规则状态）；不依赖其他服务的 core。
- 交易自动记账 `TransactionPostingService` 为输入驱动：入参是 billing 的 `EconomicFactPayload`（头 + 明细行 + 渠道清算账户主键），
  事务内只读写本库；Partner 登记不变量由 billing 校验。第 11a / 11b 步接到 `EconomicFactRecorded` 消费。
- 过账锁顺序固定为期间共享锁 → 账簿序号行排他锁 → 账户主键升序排他锁；人工调整复核、期间创建、试算与关账另用 Foundation
  `AbstractLockProcessor` 的 Redis 资源锁（`AccountingLocks`），数据库 CAS 与行锁仍是最终防线。

## 构建与运行

在 `paytech/` 下用 JDK 25 执行：

```sh
./mvnw -B verify -pl ledger/paytech-ledger-test -am
java -jar ledger/paytech-ledger-app/target/paytech-ledger-app-1.0.0-SNAPSHOT-exec.jar
```

运行前在 `mor_ledger` 人工依次执行 `docs/database/sql/ledger-schema.sql` 与 `ledger-initial-data.sql`。连接参数
`LEDGER_DB_URL` / `LEDGER_DB_USERNAME` / `LEDGER_DB_PASSWORD`，Redis `LEDGER_REDIS_HOST` / `LEDGER_REDIS_PORT` / `LEDGER_REDIS_PASSWORD`，
Snowflake `LEDGER_SNOWFLAKE_REGION_ID` / `LEDGER_SNOWFLAKE_WORKER_ID`（缺失时启动失败）。Actuator 只在管理端口暴露
health / info / prometheus，不披露组件详情。

默认 auto 测试连接 local-environment 的 `mor_ledger`（账号 `ledger_app`，只有 SELECT / INSERT / UPDATE / DELETE）与 Redis（前缀 `ledger-mock`）；
每例 BeforeEach 只检查空基线与 52 行初始化保留数据，AfterEach 清空 29 张表的测试写入。测试环境准备见
`paytech-ledger-test/src/test/resources/init/ddl.sql`。

## 当前不包含

无 Nacos / 远端 HTTP 客户端、无 MQ / XXL-Job / Compensation（Enforcer 禁止，第 11 步放开）。`t_account` 仍同时承载科目账户身份与余额投影，
余额列与按余额的试算、关账检查在第 11b 步改写。全部接口只能作为内部开发接口，不能直接发布给 Client、Partner 或 Operations。
