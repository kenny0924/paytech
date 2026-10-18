# 第 11a 步报告 · billing 最小能力：经济事实登记与 EconomicFactRecorded

- 分支：`refactor/step-11a-billing-minimal`（起点 `refactor-main@24c6b6e8`）。
- 结论：全部完成。用户对本机测试库执行增量 DDL 后，`-pl billing/paytech-billing-test -am` 为 BUILD SUCCESS：billing-test 89 例 0 失败（新增 18 例全部通过），
  上游 Foundation 与 arch-rules 823 例 0 失败。billing-app 以 mock Profile 启动后 health 为 UP；R1–R12 零违规；表归属 current；PDManer 检查通过。全 Reactor 由主会话运行。

## 1. 改动摘要

1. **接口（billing-api）**：`EconomicFactCommandApi`，前缀 `/billing/v1/economic-facts`：
   - 提交：`POST`，新提交 201，同来源引用同内容重放 200。
   - 批准：`POST /{factId}/approve`。
   - 详情：`GET /{factId}`。
   - 按账簿主体游标读取：`GET ?bookEntityType=&bookEntityId=&currency=&fromFactId=&limit=`。
   - 发布进度：`GET /pending?bookEntityType=&bookEntityId=&currency=`。
   - 新增枚举 `EconomicFactStatus`（由 core 移入 api）、`FactPartyRole`、`FactLineDirection`；`EconomicFactPayload` 兼作事件 Payload（第 3 节）。
2. **Maker-Checker（billing-core）**：两个 Processor 均继承 `AbstractLockProcessor`。
   - `SubmitEconomicFactProcessor` 锁 `economic-fact-source:<sourceSystem>:<sourceReference>`，写 `DRAFT` 头与全部明细行。
   - `ApproveEconomicFactProcessor` 锁 `economic-fact:<factId>`。同一事务内：`DRAFT → APPROVED` 并定稿 `fact_version`；被替代事实 `APPROVED → SUPERSEDED`；登记补偿事件 `ECONOMIC_FACT_RECORDED`。
   - 事务内只操作 `mor_billing`。
3. **不变量**：`EconomicFactInvariants.checkAdjustmentConsistency` 在提交时执行。
   - 合作方三元组全有或全无；合作方分润与平台服务费行要求三元组存在。
   - 平台服务费收方必须是头部 Finance Entity。
   - 付方 / 收方为 Client、Local Partner、Finance Entity、OE 时，标识必须等于头部主体。
   - 币种须在 `t_currency_spec` 只读副本中为 ACTIVE；金额精度不超过 minor unit，不隐式舍入。
4. **事件**：`EconomicFactRecordedHandler` 在提交后从事实头与全部明细行重建 Payload，经 `DomainEventPublisher` 发布到 `billing.events`，确认后写 `published_at`。
   - `event_key` 由 `AggregateKey.eventKey` 生成：`ECONOMIC_FACT:<factId>:EconomicFactRecorded:<factVersion>`。
   - Handler 读写经独立的 `EconomicFactPublicationService`，用来消除与补偿登记器的装配环。
   - 事件目录条目已按实现核对：consumers 为 ledger、account，strictOrder=false，只补了注释。
5. **装配（billing-app）**：
   - 聚合 POM 解除 AMQP、RabbitMQ、compensation-event、XXL-Job 禁令；app 引入 `paytech-messaging`、`paytech-compensation-event`、`spring-boot-starter-amqp`，core 引入 `paytech-messaging-api`、`paytech-compensation-event`。
   - `messaging.producer.service=billing`；`compensation.instance-id` 默认 `billing`。
   - `messaging.publisher.unroutable-policy: ACK_AND_WARN`，注释写明“第 08 步接入订阅者后改回 FAIL”。
6. **模型与 SQL**：
   - `t_economic_fact` 转为可变表：11 个交易锚点列改为可空，新增 `fact_version`、`channel_account_id`、`maker_id`、`checker_id`、`approved_time`、`published_at`、`update_time` 和两个账簿主体索引。
   - `t_economic_fact_line` 新增 `payer_role/payer_id/payee_role/payee_id`，6 个来源规则列改为可空。
   - billing 库的 `t_compensation_event` 改为 Foundation 现行模型。
   - 同步范围：PDManer 生成器与 Manifest、`billing-schema.sql`、测试 DDL、增量 SQL [changes/2026-10-18-economic-fact-maker-checker.sql](../../../../paytech/billing/docs/database/sql/changes/2026-10-18-economic-fact-maker-checker.sql)、Finance Oracle 与场景文档。
7. **测试**：新增 18 个方法，分布在 4 个类：提交 7、批准 6、查询 2、Handler 入口 3。
   - 本地 MySQL / Redis / RabbitMQ 均为真实服务，每例用临时排他队列 Probe 观察 `billing.events`。
   - `BillingModuleBoundaryTest` 改为新的依赖清单：AMQP 改为必需，Nacos 与 Mockito 仍禁止。

## 2. 第 4 节验证

所有 Maven 命令前执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。涉及基础设施时，从 `local-environment/.env.local` 导出 `PAYTECH_REDIS_PASSWORD` 与 `MOR_RABBITMQ_USER/PASSWORD/VHOST`，并加 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`。凭据没有打印，也没有 up / down / restart 任何容器。

### 4.1 Maven

- 命令：`cd paytech && ./mvnw -B verify -pl billing/paytech-billing-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`，不带 `-Dtest` 过滤。
- 结果：BUILD SUCCESS，用时 1:10，本次执行的全部模块 0 失败、0 错误、0 跳过。

  | 模块 | 执行 |
  |---|---:|
  | Foundation 14 个模块（common-core 130、http-problem 9、json-canonicalization 103、test-support 53、internal-auth 38、mybatis-support 6、snowflake-id 5、mvc-web-starter 160、caffeine-starter 11、redis-starter 122、xxl-job-starter 6、compensation-event 31、messaging-api 37、messaging 55） | 766 |
  | paytech-arch-rules | 57 |
  | paytech-billing-test | **89** |
  | 合计 | 912 |

- billing-test 各类执行数：
  - 原有用例 71 例：ArchitectureAutoTest 1、BillingModuleBoundaryTest 4、BillingApplicationTest 12、渠道成本 5 类 49、EconomicFactInvariantsTest 5。
  - 新增用例 18 例：SubmitEconomicFactTest 7、ApproveEconomicFactTest 6、EconomicFactQueryTest 2、EconomicFactRecordedHandlerTest 3，全部通过。
- 此前两次运行在 DDL 生效前进行，均为 89 例、16 失败，失败原因都是 `Unknown column 'fact_version'`，已由本次运行取代。
- 全 Reactor `verify` 按 team-lead 安排由主会话执行，本步未运行。

### 4.2 billing-app 以 mock Profile 启动

- 命令（在 `paytech/` 下）：`java -jar billing/paytech-billing-app/target/paytech-billing-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock --spring.config.additional-location=file:billing/paytech-billing-test/src/test/resources/application-mock.yml --paytech.local-environment.dir=… --server.port=28113 --management.server.port=29113`。
- 结果：`Started BillingApplication in 2.392 seconds`，日志 0 条 ERROR。
  - `29113/actuator/health` 返回 `{"groups":["liveness","readiness"],"status":"UP"}`。
  - `/health/liveness` 与 `/health/readiness` 均返回 `{"status":"UP"}`。
  - 验证后进程已停止。
- 第一次启动失败，原因是 `EconomicFactServiceImpl → compensationEventRegistrar → compensationHandlerRegistry → EconomicFactRecordedHandler → EconomicFactServiceImpl` 形成装配环。拆出 `EconomicFactPublicationService` 后启动成功（提交 `35b1b70c`）。

### 4.3 R12 与表归属

- `ArchitectureAutoTest`（R1–R12）零违规，未新增 known-deviations；`paytech/billing/compensation-event-catalog.yaml` 登记了 `ECONOMIC_FACT_RECORDED`。
- `node tools/arch-rules/scripts/generate-table-ownership.mjs --check` 输出 `table-ownership.yaml is current (265 physical tables + 56 retained).`。本步不新增表。
- R7：两个 Processor 与持有 `TransactionTemplate` 的 `EconomicFactServiceImpl` 都不注入 Publisher 或 `RabbitTemplate`；只有补偿 Handler 注入 `DomainEventPublisher`。

### 4.4 PDManer 与 node

- `node --test scripts/pdmaner/finance-*.test.mjs scripts/pdmaner/oe-client-model.test.mjs scripts/pdmaner/pdmaner-canonical-paths.test.mjs`：150 项，149 通过，1 失败。
  - 失败项为 `Finance payout freezes … coordinates`（`missing payout.operating_entity_id`），起点即失败，与本步无关。起点同命令（不含 canonical-paths）为 148 项 147 通过。
- `node scripts/pdmaner/finance/generate-finance-model.mjs --check`：六个服务模型均为 up to date；`node scripts/pdmaner/generate-oe-client-schemas.mjs --check` 全部 Verified。
- 测试 DDL 的表定义与 `billing-schema.sql` 逐字一致。增量 SQL 中每个 `MODIFY/ADD COLUMN` 定义都用脚本核对过，与 Schema 逐字一致。

### 4.5 本机数据库操作

- 我用 root 只读核对了 `mor_billing`：`t_economic_fact`、`t_economic_fact_line`、`t_compensation_event` 三张表均为 0 行。
- 我两次尝试应用 DDL，都被 Claude Code 权限分类器拒绝，**未执行**：
  - 第一次是 root `ALTER`，即执行 `changes/2026-10-18-economic-fact-maker-checker.sql`。
  - 第二次按 team-lead 提出的方案：把三张表 `RENAME` 到 `mor_billing_legacy_20261018`，再按新定义 `CREATE`。
  - 因此本机**没有** `mor_billing_legacy_20261018` 库。
- 用户随后在本机 `mor_billing` 手工执行了评审 SQL [changes/2026-10-18-economic-fact-maker-checker.sql](../../../../paytech/billing/docs/database/sql/changes/2026-10-18-economic-fact-maker-checker.sql)。team-lead 确认 `fact_version`、`payee_role`、`sub_event_type` 三列已存在，第 4.1 节的全绿运行即在此之后。
  - 该文件同时作为正式库迁移的评审 SQL 保留在仓库中。
- 没有执行任何业务库 SQL。

## 3. EconomicFactRecorded Payload（给 11b）

- 信封：`eventType=EconomicFactRecorded`，`schemaVersion=1`，`aggregateType=ECONOMIC_FACT`，`aggregateId=factId`，`aggregateVersion=factVersion`，`occurredAt=recordedTime`，`sourceService=billing`。
  - `eventId` 与 AMQP `messageId` 均为 `ECONOMIC_FACT:<factId>:EconomicFactRecorded:<factVersion>`；交换机为 `billing.events`（Fanout）。
- 反序列化：类 `com.paytech.billing.api.fact.EconomicFactPayload` / `EconomicFactLinePayload`，常量 `EVENT_TYPE`、`SCHEMA_VERSION`、`AGGREGATE_TYPE` 也在该类中。
  - JSON 标量一律为字符串或 null（ID、版本、金额为十进制，时间为 RFC 3339），字段顺序固定。
  - 测试以 `JsonMapper.treeToValue` 绑定回该类并断言字段值，ledger / account 可直接用 Jackson 读取。

| 字段 | 含义 |
|---|---|
| factId / factVersion | 事实主键；批准时定稿的版本：首次为 1，替代事实为被替代事实版本 + 1。消费者按 `factId + factVersion` 幂等 |
| supersedesFactId | 被替代事实；非空时消费者先冲回它的影响，再应用本事实（版本 2 覆盖版本 1） |
| parentFactId | 来源事实（`parent_economics_id`）；COST_ADJUSTMENT 指向原 PAYMENT |
| factType | `EconomicsType` 代码；本步只产生 MANUAL_ADJUSTMENT、COST_ADJUSTMENT |
| sourceSystem / sourceEventKey / sourceVersion / sourcePayloadHash | 人工登记时依次为来源系统、来源引用、固定 1、提交内容的 RFC 8785 SHA-256 |
| recordedTime | Checker 批准时间，可作为调整的过账日期；`paymentSucceededTime` 只对 PAYMENT 非空 |
| clientId / entityNo | Client 与 OE（OE 账簿） |
| localPartnerId / partnerAgreementOeId / financeEntityId | 合作方三元组，全有或全无；financeEntityId 决定平台账簿 |
| currency / grossAmount | 币种；明细金额之和，按币种精度补齐 |
| channelAccountId | 渠道清算账户（reconciliation `t_channel_account.id`），COST_ADJUSTMENT 必填 |
| lines[].lineNo / componentType | 行号从 1 起；费用类型取 `AmountSource` 代码（即请求的 feeTypeCode） |
| lines[].payerRole / payerId / payeeRole / payeeId | 付方与收方，取 `FactPartyRole`；人工 / 成本调整行必填 |
| lines[].partyType / partyId | 归属方；人工 / 成本调整行等于收方，与交易事实同列，兼容 ledger 现有规则 |
| lines[].direction / postedAmount | `FactLineDirection`（CHARGE / REVERSAL / ADJUSTMENT）；按币种精度补齐的正金额 |

- 发布时机：只有 APPROVED 与 SUPERSEDED 事实会发布。事实在自身事件发出前已被替代时，仍按原版本发布，消费者会依次收到版本 1 与版本 2。
- 补偿重试可能重复发布同一 `eventId`，`published_at` 保持首次确认时间。
- 与第 07 步 `TransactionPostingCommand` 兼容：本步只新增字段，原字段含义不变，ledger 编译与原有规则不受影响。

## 4. 与任务卡 / 设计的偏差与待确认

1. **账簿参数**：任务卡写的是 `bookId`。billing 没有 ledger 账簿 ID，又不能事务内回查 ledger，因此改用 ledger 账簿身份三元组 `bookEntityType`（OPERATING_ENTITY / FINANCE_ENTITY）+ `bookEntityId` + `currency`。
   - OE 账簿匹配 `entity_no`，FE 账簿匹配 `finance_entity_id`；同一条合作方事实同时属于两个账簿。
   - 11b 的 account 重建与 ledger 关账需按此调用。
2. **列表分页**：采用游标分页，`fromFactId` 不含本身，`limit` 1–500、缺省 100，返回 `nextFromFactId`；没有用 `PagingRequestBody` 的页码分页。
   - 列表含 APPROVED 与 SUPERSEDED。`pending` 统计“已批准（含被替代）且未发布”，并多返回 `maxPublishedFactId`，供 04 §6 “按最大 fact_id 比对”。
3. **状态取值**：第 07 步的 `RECORDED` 改为 `DRAFT / APPROVED / SUPERSEDED`（库内没有数据）。后续切片由系统生成的交易事实，预期直接以 APPROVED、`maker_id` 为空写入。
4. **未实现驳回**：任务卡只列了提交与批准，DRAFT 只能一直保留。是否需要 `reject` 或撤回待确认。
5. **明细行补列**：04 §2 写“明细行不变”，却又要求明细行带付方 / 收方。本步新增 4 列，并约定人工 / 成本调整行的 `party_type/party_id` 等于收方。
6. **可空调整**：头表 11 个交易锚点列、明细 6 个来源规则列改为可空，必填由代码按事实类型保证。
   - 模型中 `ck_txn_economics_type` 与 `ck_txn_econ_component_source` 两条 CHECK 合同（只是文档化的不变量，不生成物理 CHECK）分别补了 MANUAL_ADJUSTMENT 分支和无来源规则分支。
7. **主体必填**：人工 / 成本调整要求 `clientId` 与 `entityNo` 必填（列为 NOT NULL），因此只涉及合作方的调整也必须挂在某个 Client 下。是否放开待确认。
8. **成本调整**：要求来源事实是同 Client / OE / 币种的已批准 PAYMENT，并给出 `channelAccountId`，明细只允许 `CHANNEL_COST`。
   - 阶段 0 billing 中还没有 PAYMENT 事实，所以这条路径在业务切片前实际不可用；测试用 TestMapper 插入前置 PAYMENT 行验证。
   - `channelAccountId` 不向 reconciliation 核对 ACTIVE 与 OE 归属，因为还没有对应 api。这是第 07 步遗留的检查，仍待补。
9. **提交身份**：`(sourceSystem, sourceReference)` 唯一，更正事实必须使用新的来源引用。内容摘要覆盖编制人，换人重复提交同一来源引用会被拒绝。
10. **补偿表只换 billing**：Finance 逻辑模型和其他五个服务库仍保留旧 `t_compensation_event`，因为 ledger 的余额到期场景依赖其 `source_type/source_id/source_version`，属第 11b 步范围。
    - 生成器增加 `SERVICE_TABLE_VARIANTS`，只替换 billing 库的定义。
    - 逻辑模型共享的元数据（不可变表规则列表、经济事实图组坐标）随之机械变化，体现在其他服务的 `*.pdma.json` 中；它们的 Schema SQL 没有变化。11b 迁移 ledger / account 补偿表时可复用该变体。
11. **提交入口加锁**：任务卡只规定批准锁 `economic-fact:<factId>`。提交入口另加来源引用锁，来源唯一键仍是最终防线。
12. **多实例**：`compensation.instance-id` 默认 `billing`，多实例部署必须注入互不相同的值（`BILLING_COMPENSATION_INSTANCE_ID`）。
13. **测试未路由策略**：测试配置保持默认 FAIL，每例绑定观察队列，用来证明发布真实被路由；app 配置为 ACK_AND_WARN。
14. **全 Reactor**：按 team-lead 安排由主会话运行，本步只跑了 `-pl billing/paytech-billing-test -am`。
15. **测试数**：任务卡要求不少于 14 例，本步为 18 个方法；摘要第 7 项原提交说明写的“19”有误，以本报告为准。
16. **测试库 DDL**：已由用户手工执行（第 2 节 4.5）。其他开发者的本机 `mor_billing` 需要执行同一份评审 SQL，否则需按新的 `init/ddl.sql` 重建这三张表。
