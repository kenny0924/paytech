# 第 11b 步报告 · 账户与会计分库：`t_account` 拆表、双消费 `EconomicFactRecorded`、关账一致性

- 分支：`refactor/step-11b-ledger-account-split`（起点 `refactor-main@a55be893`）。实现与本报告同一提交。
- 结论：任务卡第 2 节 13 项全部实现。主会话执行两份 DDL 后，`-pl ledger/paytech-ledger-test,account/paytech-account-test -am`
  不带过滤为 BUILD SUCCESS：ledger-test 204 例、account-test 40 例，0 失败（第二轮补调整规则并接入 12x 队列基线后）。两个 app 以 mock Profile 启动后 health 为 UP；R1–R12 零违规；
  table-ownership 与 PDManer 检查为 current。全 Reactor 由主会话运行。
- 第二轮（用户确认）：ledger 规则包补 MANUAL_ADJUSTMENT / COST_ADJUSTMENT 记账规则（初始化 DML），偏差 5 解除；已合并 `refactor-main@8c5b4d50`（无冲突）并接入 12x 分区队列基线。
- 与 04 文档有 6 处偏差，见第 6 节。其中“余额身份不复用 ledger 账户 ID”和“试算不再做账户投影核对”影响最大，请确认。

## 1. 改动摘要

1. **表**（04 §3）：ledger `t_account` → `t_ledger_account`，删五个余额列；mor_ledger 增加 `t_consumed_event`、`t_consumed_aggregate_version`（31 表）。
   新库 mor_account 8 表：`t_account_balance`、`t_balance_applied_fact`、`t_balance_applied_entry`（新增，见偏差 2）、`t_balance_movement`（仅 Schema）、
   `t_balance_discrepancy`、两张消费表、`t_compensation_event`（Foundation 现行模型）。PDManer 两模型、两份 Schema、两份测试 DDL、两份增量 SQL 同步。
2. **过账内核**：`JournalPostingServiceImpl` 只校验账户存在、属于账簿且 ACTIVE，不锁账户、不写余额；锁顺序为期间共享锁 → 账簿序号排他锁。
3. **自动记账**：`EconomicFactRecordedPostingHandler`（ledger-core `ext/mq`，SHARED，strictOrder=false）消费 `billing.events`，按 eventId 与
   `economic_fact_id` 双重幂等，一个 ledger 本地事务生成指令并过账。规则拒绝的事实进入 `ledger.billing.dlq`。并发创建科目账户的唯一键冲突转为可重试冲突。
4. **人工调整**：提交与批准都拒绝涉及 CLIENT / LOCAL_PARTNER 账户的调整，错误码 `OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN`（ledger-api `LedgerErrorCode`）。
5. **关账 / 重开 / 重建**：结转、重开只写分录与期间；`ensureResultAccount` 只建账户身份；`approveProjectionRebuild` 只写 `t_trial_balance_exception`，
   摘要冻结账户的分录聚合值（摘要 schema 升为 `CODE-MOR:LEDGER:PROJECTION_REBUILD:2`）。
6. **ledger 本库聚合**：账户列表的四个余额值、关账步骤 3 的非零渠道 / 银行账户数、试算都由分录聚合。
7. **关账步骤 2、3**：事务外逐账簿调 billing `pending` 与 account `consistency`。billing 无未发布事实，且 account 已处理的最大 factId 不小于 billing
   已发布的最大 factId，才算追平。步骤 3 要求本库与 account 都没有非零渠道 / 银行账户。任一不可用或未追平即失败关闭。
8. **account 消费**：`EconomicFactRecordedBalanceHandler`（SHARED，strictOrder=false）+ `FactBalanceMappingRule`。按 fact_id + fact_version 幂等。
   替代事实先冲回被替代事实的余额变化，再应用自己；被替代事实先到时预登记，到达后跳过。余额以 balance_version CAS 写入。
9. **account 接口**：`BalanceBookApi` 的 consistency / accounts / rebuild；余额查询改读自有库并经 ledger-api 确认账簿；日终核对 XXL Handler
   `accountBalanceReconciliation` 写 `t_balance_discrepancy` 并记 WARN。
10. **ledger 接口**：`LedgerReadApi`（`/ledger/v1/books/{bookId}`、`/chart-accounts?ids=`、`/books/{bookId}/account-totals`）。
11. **共享类型**：`AccountIdentityRule` 从 account-api 收回 ledger-core；`StandardAccountCodes` 补渠道两科目；`BalanceMovedPayload` 进 account-api。
12. **装配**：两个 app 引入 paytech-messaging、spring-boot-starter-amqp、paytech-http-client-support（直连 base-url，Finance 禁 Nacos，JDK HttpClient 不重试）；
    account 另引入 paytech-compensation-event（含 XXL）。两个聚合 POM 放开对应禁令：ledger 只放开 AMQP / RabbitMQ，account 放开 AMQP / RabbitMQ /
    compensation / XXL。两个 `*ModuleBoundaryTest` 改为要求 RabbitTemplate 在 classpath（09 §2.7）。
13. **工具与环境**：table-ownership 生成器登记更名与 account 五表、删除 account 的共库只读段；`TableOwnershipAutoTest` 同步；local-environment
    初始化脚本改为给 account_app 建 mor_account 与本库 DML。事件目录只补注释；新增 `paytech/account/compensation-event-catalog.yaml`（空目录）。
14. **测试**：ledger 新增 10 例、改写过账 / 拒绝 / 试算 / 关账 / 重建 / 查询用例；account 新增 17 例、重写基类、清理器与 4 例余额查询。

## 2. 第 4 节验证

所有 Maven 命令前：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；
`set -a; source …/Code-MoR/local-environment/.env.local; set +a; export PAYTECH_REDIS_PASSWORD="$MOR_REDIS_PASSWORD"; unset OTEL_RESOURCE_ATTRIBUTES`。
凭据未打印，未启停任何容器。

### 4.1 Maven

```bash
cd paytech && ./mvnw -B verify -pl ledger/paytech-ledger-test,account/paytech-account-test -am \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

BUILD SUCCESS（1:50）。上游 Foundation 与 arch-rules 全部 0 失败。

| 模块 | 第 07 步 | 本步 | 说明 |
|---|---:|---:|---|
| paytech-ledger-test | 190 | **204** | +14：Owner 账户调整禁令 2、关账依赖 4、DLQ 1、LedgerReadApi 3、调整事实过账 4（第二轮） |
| paytech-account-test | 21 | **40** | +19：消费 9（第二轮 +2：成本调整、平台角色与 ledger 同样拒绝）、consistency / 列表 3、重建 3、日终核对 3、ledger 不可用 1 |
| paytech-arch-rules | 55 | 56 | 只改 TableOwnership 断言 |

account 新增 ≥ 12 例的要求对应：消费更新余额、重复投递只应用一次、版本 2 覆盖版本 1（顺序与乱序各 1）、映射规则各角色（交易隐含角色、调整付方 / 收方、
REVERSAL、未映射拒绝）、consistency 已追平 / 未追平、rebuild 后与 ledger 分录和一致（以 account-totals DTO 核对且核对任务 0 差异）、核对任务发现人为差异、
ledger 不可用时余额查询 / consistency / rebuild 失败关闭。

三服务链路（10 §2.5）：ledger 段 `LedgerReadApiTest.test_accountTotalsAggregateOwnerJournals` 由扮演 billing 的事件过账后读 account-totals；
account 段 `EconomicFactBalanceConsumeTest.test_paymentFactUpdatesOwnerBalances` 以同形 Payload 消费，Owner 余额与 ledger 规则 Owner 行一致。
billing 段为第 11a 步的发布测试；本步没有跑 billing-test。
人工调整（第二轮）：ledger 段 `EconomicAdjustmentPostingTest.test_manualAdjustmentPostsByPartyRoles` 与 account 段
`EconomicFactBalanceConsumeTest` 的调整用例使用同形 Payload，Client 应付与 Partner 应付的借贷与 ledger Owner 行一致。

过程中的失败与处理：

- 第一次运行 account 3 例失败（期望 503 / 400，实际 502）。原因是测试 Mock 响应缺 `X-Request-Id`，公共客户端判为响应不合法；Mock Controller 补上头后通过。
  FE 账簿重建先读 billing 再拒绝，改为 Processor 读到账簿类型后先拒绝。
- ledger 1 例超时：测试事实借贷不平，被规则拒绝。修正测试数据。
- **队列残留（12x 同类问题）**：不带过滤的第一次运行中，`AccountApplicationTest` 首例 `assertEmpty` 失败，`t_balance_applied_entry` 有 3 行。
  原因是上一次运行的 ledger-test 扮演 billing 发布的事实同时路由到 `account.billing.p*`（fanout）。account 应用启动时监听器先于 BeforeEach purge 消费了这些残留。
  反方向同理：account-test 的事实落入 `ledger.billing.p*`，被 ledger 启动时消费；它们都被规则拒绝进 DLQ，没有写库。
  第一轮处理：用 `rabbitmqctl purge_queue` 清空本服务自己的 `ledger.billing.*` / `account.billing.*` 后重跑，通过。生产代码未改。
- **第二轮：接入 12x 队列基线**。两个 `-test` pom 加 `paytech-messaging` test-jar（scope=test），两个 `MockTestBase` 的 `@Import` 加
  `PartitionQueueBaselineConfiguration`。不再手工 purge，同一命令连跑 account 与 ledger 两模块，日志各有一条基线 INFO：

  ```text
  测试分区队列基线: consumer=account, producer=billing, partitions=4, 清除残留消息=0
  测试分区队列基线: consumer=ledger, producer=billing, partitions=4, 清除残留消息=22
  ```

  ledger 启动前清除的 22 条正是 account-test 刚扮演 billing 发布、fanout 到 `ledger.billing.p*` 的事实；两模块均 0 失败。

### 4.2 两个 app 以 mock Profile 启动

```bash
java -jar ledger/paytech-ledger-app/target/paytech-ledger-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock \
  --spring.config.additional-location=file:ledger/paytech-ledger-test/src/test/resources/application-mock.yml \
  --paytech.local-environment.dir=… --ledger.test.external.base-url=http://127.0.0.1:29999 --server.port=28105 --management.server.port=29105
# account 同理：28114 / 29114
```

- `Started LedgerApplication in 2.733 seconds`、`Started AccountApplication in 1.934 seconds`。
- `29105` 与 `29114` 的 `/actuator/health` 均为 `{"groups":["liveness","readiness"],"status":"UP"}`，liveness / readiness 均为 UP。进程已停止。
- account 日志 1 条 ERROR：启动时消费了 ledger-test 残留的人工调整事实，映射拒绝进 DLQ（预期行为，同 4.1 的残留问题）。
  同一启动还应用了 4 条残留交易事实，在 mor_account 写入 4 个余额。停止后我用 root 对 **mor_account 测试库** 执行了 `DELETE`（8 张表），
  并 purge 两服务的 billing 队列；复查 mor_account 与 mor_ledger 的写入均为 0。

### 4.3 R4 / R7 / R12

- 两个 `ArchitectureAutoTest`（R1–R12）零违规，无新增 known-deviations。
- R4：ledger XML 只引用 mor_ledger 表；account XML 只引用 mor_account 表；共库只读段已删除。
- R7：持有 TransactionTemplate 的类（`StartPeriodCloseProcessor`、`TrialBalanceServiceImpl`、`BalanceRebuildServiceImpl`、`BalanceReconciliationServiceImpl` 等）
  不注入任何 `-api`；远程调用只在 `integration/*ClientImpl` 中，由 Processor 在事务外调用。关账步骤 2 的远程调用在步骤短事务之外。
- R12：两个 Handler 在 `-core`，事件目录 `EconomicFactRecorded.consumers` 已含 ledger、account；messaging 启动时的 Handler 登记核对通过。

### 4.4 表归属与 PDManer

- `node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check`：`current (265 physical tables + 56 retained + 6 step-added)`。
- `generate-finance-model.mjs --check`：六个服务模型 up to date；ledger 31 表、account 8 表。生成的 Schema 与测试 DDL 逐块一致。
- `node --test scripts/pdmaner/finance-*.test.mjs oe-client-model.test.mjs pdmaner-canonical-paths.test.mjs`：150 项 149 通过。
  唯一失败是起点即有的 `missing payout.operating_entity_id`。
- `node scripts/verify-local-environment.mjs`：只剩与基线相同的 4 行 Gateway 文档引用。
- PDManer 决定：84 表逻辑模型不变（场景文档测试与逻辑断言依赖原 `t_account`），拆表只体现在服务物理模型。
  新文件 `scripts/pdmaner/finance/manifests/09-service-physical.mjs` 提供 ledger 的更名变体与 account 的服务专有表。

需回写 design/03 的差异（本步未改 design/03）：

1. `t_account` 行只写 account 新建 `t_account_balance`；实现另有 `t_balance_applied_fact`、`t_balance_applied_entry`、`t_balance_movement`、`t_balance_discrepancy`。
2. §4.2 `mor_ledger` 写 29 张，物理库现为 31 张（两张消费表按共享基础设施不计入 §4.2）。
3. §4.2 `mor_account` 写 0 + `t_compensation_event` = 1；现为 5 张自有表 + 补偿表 + 两张消费表 = 8；table-ownership 以 step-added 登记五张表。
4. `t_account_balance` 以账簿主体身份与科目代码为键，`id` 为 account 自有（偏差 1）；account 的 `t_compensation_event` 为现行模型。

## 3. 六处事务拆分前后

| 现有事务 | 拆分前写入 | 拆分后 |
|---|---|---|
| `JournalPostingServiceImpl#post` | ledger 分录 + 锁 `t_account` 并 `applyPosting` | ledger：期间共享锁 → 序号 → 账户存在且 ACTIVE（不加锁）→ 平衡 → 分录 → 指令 POSTED；不写余额 |
| `TransactionPostingServiceImpl#post` | 测试直接调用；ledger + account 余额 | `EconomicFactRecordedPostingHandler` 在消费拦截器事务内调用；只写 ledger（指令、分录、科目账户身份）；account 另行消费同一事实 |
| `ApproveManualAdjustmentProcessor` | ledger + account 余额 | 事务前拒绝 Owner 账户；事务内只写调整、审批、指令、分录 |
| `StartPeriodCloseProcessor#postBookProfitClose` | ledger + `ensureResultAccount` 写余额 | 只写指令、分录、`t_period_close_book` 与科目账户身份 |
| `ApprovePeriodCloseProcessor#approveReopen` | ledger + account 余额 | 只写期间、关账、审批与冲回分录 |
| `TrialExceptionServiceImpl#approveProjectionRebuild` | account 投影 CAS + `t_trial_balance_exception` | ledger 只写 `t_trial_balance_exception`；account 新命令 `POST /balances/v1/books/{bookId}/rebuild`：事务外读 ledger 账簿与 billing 事实，一个 mor_account 短事务重写 |

## 4. 新接口与事件

| 服务 | 接口 / 事件 | 说明 |
|---|---|---|
| ledger | `GET /ledger/v1/books/{bookId}` | 账簿主体类型、标识、币种、科目表与状态；不存在 400 |
| ledger | `GET /ledger/v1/chart-accounts?ids=` | 逗号分隔 1–200 个主键，按主键升序，不存在的主键不返回 |
| ledger | `GET /ledger/v1/books/{bookId}/account-totals` | CLIENT / LOCAL_PARTNER / CHANNEL_ACCOUNT / CASH_ACCOUNT 账户按全部分录聚合的余额（不分页） |
| ledger | 人工调整 | 新错误码 `OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN`（400） |
| account | `GET /balances/v1/books/{bookId}/consistency` | 已处理事实数、最大 factId、按 Owner 类型的非零余额数；ledger 不可用 503 |
| account | `GET /balances/v1/books/{bookId}/accounts` | 账簿内全部余额 |
| account | `POST /balances/v1/books/{bookId}/rebuild` | 重放 billing 已批准事实重算；FE 账簿 400；重放期间有更晚事实 409 |
| account | `GET /balances/v1/clients/{id}`、`/partners/{id}` | 已迁入，改读 mor_account，经 ledger-api 确认账簿 |
| account | XXL-Job `accountBalanceReconciliation` | 日终核对，写 `t_balance_discrepancy`（同日同账户更新）并记 WARN |
| 消费 | `EconomicFactRecorded` | ledger：`ledger.billing.p0..p3`；account：`account.billing.p0..p3`；分区数各 4 |
| Schema | `BalanceMoved` | `com.paytech.account.api.event.BalanceMovedPayload`；无生产调用方 |

## 5. SQL

已由主会话在本机测试库执行（第 11b 步第一轮结束后）：

1. mor_ledger：`paytech/ledger/docs/database/sql/changes/2026-10-18-ledger-account-split.sql`（RENAME、删 5 列、改注释、建两张消费表；含回退说明）。
2. mor_account：`paytech/account/paytech-account-test/src/test/resources/init/ddl.sql`。正式环境对应 `paytech/account/docs/database/sql/changes/2026-10-18-account-database.sql`
   （建库与 8 表，不含账号）。
3. mor_ledger（第二轮）：`paytech/ledger/docs/database/sql/changes/2026-10-18-standard-adjustment-rule-bundle.sql`（只插入保留规则行 bundle 5002、ver 5102、行 5301–5332；回退为按主键删除）。

撤销第 07 步临时只读授权（草案，**未执行**）：

```sql
REVOKE SELECT ON mor_ledger.* FROM 'account_app'@'%';
```

本机测试库清理（4.2 节，残留数据）：root 对 mor_account 8 张表执行 `DELETE`，未触碰其他库。

正式库若已有 `t_account` 余额数据，余额迁到 mor_account 不在本步范围，须按 billing 经济事实重放（rebuild）另行设计。

## 6. 与 04 文档的偏差

1. **余额身份（§3.2）**：`t_account_balance.id` 是 account 自有 Snowflake，唯一键为 `(book_entity_type, book_entity_id, currency, account_code, owner_type, owner_id,
   balance_bucket)`，不存 `ledger_book_id` / `chart_account_id`。原因是 account 独立消费事实时不知道 ledger 的账户 ID 与账簿 ID；回查 ledger 会让两个消费者产生顺序依赖。
   另外 04 的唯一键 `(ledger_book_id, owner_type, owner_id, balance_bucket)` 无法区分同一 Client 的保证金与追偿应收（都是 NONE），必须带科目。
   与 ledger 的配对改用身份键；需要 ledger 账簿 ID 时经 ledger-api 解析。
2. **新增 `t_balance_applied_entry`**：替代事实要冲回被替代事实的余额变化，而替代事实的 Payload 不含旧明细行；把每条事实的余额变化落表，才能不回查 billing 就冲回。
3. **试算不再做账户投影核对（§6）**：ledger 已没有投影可比；改调 account-api 会在异步消费的滞后窗口内产生假异常，并阻塞关账。
   余额与分录的一致性改由 account 日终核对（§7）和关账步骤 2 的 consistency 承担。原“投影漂移”用例改为“已过账分录借贷不平”，ACCOUNT 类存量异常的处置流程保留。
4. **`selectPage` / `countPage` 仍联 `t_chart_account`**：拆表后 `t_ledger_account` 与 `t_chart_account` 同属 mor_ledger，属本库联表；任务卡第 9 项把它列为跨服务读，
   是沿用第 07 步共库时的口径。account 侧不存 `chart_account_id`，见偏差 1；ledger 的 `chart-accounts?ids=` 已实现，供 BFF 或后续调用方使用。
5. **调整事实记账规则（第二轮已解除）**：用户确认后，规则包新增 `STANDARD_ADJUSTMENT`（bundle 5002 / ver 5102 / 行 5301–5332），只放在初始化 DML、
   测试 DDL 与增量 SQL `ledger/docs/database/sql/changes/2026-10-18-standard-adjustment-rule-bundle.sql`（主会话已在 mor_ledger 执行），代码不写规则。
   最小扩展：billing-api `AutoInstructionType` 增 `ECONOMIC_ADJUSTMENT`（MANUAL_ADJUSTMENT）与 `CHANNEL_COST_ADJUSTMENT`（COST_ADJUSTMENT）；
   `TransactionPostingServiceImpl` 按明细行“付方记借、收方记贷”（REVERSAL 互换）选规则行，键为（金额来源、Owner 角色、借贷方向）且须唯一命中。
   Owner 取值：CHANNEL 取头部 `channelAccountId`，OPERATING_ENTITY 取 `entityNo`，其他角色取明细行 partyId。account 的 `FactBalanceMappingRule` 同步收紧，
   两侧对同一事实接受与拒绝一致。仍拒绝进 DLQ 的组合（真正不可映射）：
   - 付方或收方为 PLATFORM / CONSUMER 角色；
   - 明细行缺付方或收方，或金额为负；
   - 费用 × 角色没有规则行，例如 SELLER_TAX 的付方为 CLIENT、PARTNER_SHARE 的收方为 CLIENT；
   - COST_ADJUSTMENT 中非 CHANNEL_COST 的行，或 CHANNEL_COST 不在 CHANNEL 与 OPERATING_ENTITY 之间；
   - REFUND 等仍无规则的事实类型（DLQ 用例改用 REFUND）。
   测试库保留行由 52 增至 86（`LedgerDataCleaner.RESERVED_ROWS`）。
6. **关账步骤 2 只按 account 判定追平**：ledger 自身是否已消费完 billing 事实没有可靠的按账簿口径（被规则拒绝的事实没有指令），本步未检查。
   另外 billing `pending` 以账簿主体三元组为参数（第 11a 步偏差 1），不是 bookId。

其他实现细节：

- `TransactionPostingServiceImpl` 保持 domain service；Handler 是独立的 `ext/mq` 类。
- 自动建科目账户的唯一键冲突转为 `ConcurrentRequestException` 并重投。`CreateAccount` API 仍按业务拒绝处理。
- 余额重建只允许经营主体账簿。Owner 余额都在 OE 账簿，FE 账簿重建会误删跨账簿事实的处理记录，所以拒绝。

## 7. 待确认

1. 偏差 1–3 请确认。偏差 5 已在第二轮按用户确认补规则解除（第 6 节）。
2. FactBalanceMappingRule 的映射表：CLIENT（CLIENT_PRINCIPAL、CLIENT_FEE）→ CLIENT_PAYABLE/PENDING；LOCAL_PARTNER（PARTNER_SHARE）→ PARTNER_PAYABLE/PENDING；
   CHANNEL（总额 → CHANNEL_UNSETTLED_RECEIVABLE，CHANNEL_COST → CHANNEL_FEE_ACCRUAL），Owner 为事实头 `channelAccountId`。收方记贷、付方记借，REVERSAL 互换。
   **已确认（第二轮）**：CLIENT_FEE 以 Client 为付方时借记 CLIENT_PAYABLE；CHANNEL 角色的 Owner 取头部 `channelAccountId`。ledger 调整规则按同一映射。
3. billing app 的 `messaging.publisher.unroutable-policy: ACK_AND_WARN` 注释写“接入订阅者后改回 FAIL”。本步接入了订阅者，但未改 billing 配置（不在本步范围）。
4. 已完成：12x 分区队列基线已接入 ledger-test / account-test，两模块连跑无需手工 purge（4.1 节）。
5. `account-totals` 不分页；账户量大时需要游标分页。
6. 撤销 account_app 对 mor_ledger 的 SELECT（第 5 节草案）待主会话执行。
7. `agent-docs/CURRENT_IMPLEMENTATION.md` 与 `FINANCE_DATABASE.md` 等目录文档未同步本步状态（09 §5.1 不在本期范围）。
