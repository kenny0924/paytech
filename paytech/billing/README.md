# billing

计费服务（V2，第 07 步由 `finance-service` 拆出）：唯一计费事实来源（[决定 2026-10-19 计费事实引擎](../../agent-docs/decisions/2026-10-19-billing-fact-engine.md)）。
任何会改变 Client、合作伙伴、平台或银行现金余额的金额在此落为不可变、带版本的计费事实；account 记明细账、ledger 记总账都只消费它。
渠道成本方案 / 版本 / 范围 / 组件的配置与审批已迁至 channel-connector（需求 `requirements/20261019/billing-计费事实模型重构与渠道成本迁出`）。

- spring.application.name：`billing-service`；端口 18113，管理端口 19113。
- 数据库：`mor_billing`（10 张表）：计费事实五表 `t_billing_fact`、`t_billing_fact_ref`、`t_billing_fact_pricing`、`t_billing_fact_line`、
  `t_billing_fact_line_link`；参考数据只读副本 `t_business_calendar`、`t_business_calendar_day`；可靠执行与消费幂等 `t_compensation_event`、`t_consumed_event`、`t_consumed_aggregate_version`（本期无消费者）。
- 物理模型：[billing.pdma.json](docs/database/billing.pdma.json)，直接维护 JSON（不再由 Finance 生成器产出），门禁
  `node --test scripts/pdmaner/billing-model.test.mjs`；Schema 与初始化 DML 在 [docs/database/sql](docs/database/sql)，结构变更见
  [changes/](docs/database/sql/changes)（最近一次：[2026-10-20-drop-currency-spec-copy.sql](docs/database/sql/changes/2026-10-20-drop-currency-spec-copy.sql)
  删除币种副本表）。
- 币种规则：Owner platform-support（`mor_platform.t_ref_currency`），本服务不持副本。2026-10-27 起由 core 依赖的
  [参考数据客户端](../../requirements/20261027/platform-support-参考数据客户端组件/technique.md)（`kinds: [CURRENCY]`）整体读取到进程内快照，
  业务只经 `CurrencyRuleService.requireUsable` 读内存（可在事务内）；启动后立即加载，首次成功前每 10 秒重试，之后每 1 小时全量刷新，
  失败保留上一份快照；首次加载成功前 readiness（`referenceData`）为 OUT_OF_SERVICE；查不到币种不提前回源。计费事实登记接受启用过（ACTIVE 或
  INACTIVE）的币种，DRAFT 与未知币种拒绝（Q2/Q7）；已登记事实的发布与自动 PAYMENT 事实登记把查不到转为可重试的系统异常。

| 模块 | 职责 |
|---|---|
| paytech-billing-api | `/billing/v1/facts` 合同：`BillingFactCommandApi`（提交、批准、作废草稿）、`BillingFactQueryApi`（按 ID、按账簿主体、发布进度、按引用、Operations 管理列表）；枚举 `BillingFactType`、`BillingFactStatus`、`FactOriginType`、`FactRefType`、`FactComponentType`、`FactPartyRole`、`FactLineDirection`、`SourceRuleType`、`LineLinkType`、`CalculationMethod`；载荷 `BillingFactPayload` / `BillingFactRefPayload` / `BillingFactLinePayload`（同时是 `BillingFactRecorded` v1 Payload） |
| paytech-billing-core | 计费事实 Maker-Checker 登记（`SubmitBillingFactProcessor`、`ApproveBillingFactProcessor`、`VoidBillingFactProcessor`）、目录约束与登记不变量 `BillingFactInvariants`、读取 `BillingFactQueryService`、发布 Handler `BillingFactRecordedHandler`；渠道成本"组件 → 金额"纯计算器 `ChannelCostCalculator`（本期无调用方，保留给自动 PAYMENT 事实）；`BillingLocks`；包根 `com.paytech.billing` |
| paytech-billing-app | 启动类、`BillingFactController` / `BillingFactConverter`、DataSource / MyBatis / TypeHandler、Snowflake、Redis 锁、RabbitMQ 与 `paytech-messaging` 发布端、`paytech-compensation-event`、platform-support HTTP 客户端与币种快照刷新调度 / 就绪探针 |
| paytech-billing-test | auto 测试、清理器、`init/ddl.sql` |

## 接口

| Method / Path | 说明 |
|---|---|
| `POST /billing/v1/facts` | Maker（`X-Gateway-Actor-Subject`）提交 `MANUAL_ADJUSTMENT` / `COST_ADJUSTMENT`（`originType = MANUAL`）为 `DRAFT`，`business_time` 暂为提交时间；同来源引用同内容 200 返回原事实，内容不同 400 |
| `POST /billing/v1/facts/{factId}/approve` | Checker（`X-Gateway-Actor-Subject`，≠ Maker）批准（已作废拒绝）：`APPROVED`、`fact_version` 定稿、`approved_time = business_time = 批准时间`，被替代事实同事务 `SUPERSEDED`，同事务登记补偿事件 `BILLING_FACT_RECORDED` |
| `POST /billing/v1/facts/{factId}/void` | 作废草稿：`DRAFT → VOIDED`（条件更新），不登记补偿、不发布事件；已作废返回原事实，非草稿 400 |
| `GET /billing/v1/facts/search?entityNo&currency&clientId&status&factType&createdFrom&createdTo&page&size` | Operations 管理列表：任意状态事实头摘要，按 factId 降序，`PagingResponseBody` 分页 |
| `GET /billing/v1/facts/{factId}` | 头、引用、明细行与计算证据、定价输入（仅 PAYMENT）、以其明细行为新行的行链接 |
| `GET /billing/v1/facts?entityType&entityId&currency&fromFactId&limit` | 账簿主体下 APPROVED / SUPERSEDED 事实，按 factId 游标，limit 1–500 缺省 100（account 重放） |
| `GET /billing/v1/facts/progress?entityType&entityId&currency` | 未发布 / 已发布两段计数与最大 factId（ledger 关账） |
| `GET /billing/v1/facts/by-ref?refType&refId&fromFactId&limit` | 引用给定外部对象的任意状态事实，按 factId 游标，limit 1–200 缺省 100 |

三个命令的操作人只取 `X-Gateway-Actor-Subject`（Operations Account ID），`X-Gateway-Session-Id` 只记日志；请求体不再接受 `makerId` / `checkerId`。

调用方需要区分的业务拒绝以 `BillingFactErrorCode` 返回 400（Problem `code` 为错误码、`detail` 为默认消息，Operations BFF 显式注册）：
`SELF_REVIEW_FORBIDDEN`（批准人等于编制人）、`FACT_NOT_DRAFT`（批准或作废非草稿，含已作废）、`FACT_LINE_INVALID`（明细行违反组件、收付方、
合作方组件、目标余额桶、金额为正或币种精度规则）、`FACT_SUPERSEDE_INVALID`（被替代事实不合格）。其余业务拒绝仍为 `BUSINESS_CHECK_FAILED`。Operations「财务 / 调整」页经 BFF `/finance/billing-facts/v1/**` 转发（登记固定 `sourceSystem=OPERATIONS`），
Gateway 路由与权限见 Platform 初始化 DML 块 100（查询 `FINANCE_ACCOUNTING_READ`、登记与作废 `FINANCE_ACCOUNTING_MANAGE`、批准 `FINANCE_ACCOUNTING_APPROVE`）。

人工登记目录约束：`MANUAL_ADJUSTMENT` 必须有 `clientId`、不得有 `parentFactId`、只允许可选 `TICKET` 引用、组件限
`CLIENT_PRINCIPAL / CLIENT_FEE / PARTNER_SHARE / PLATFORM_SERVICE_CHARGE / SELLER_TAX / CHANNEL_COST`，`targetBucket` 只用于收方为
CLIENT / LOCAL_PARTNER 的行（本期只校验、落库并进载荷，account 继续按现行映射记账）；`COST_ADJUSTMENT` 必须有已批准同 OE 同币种的
PAYMENT 来源事实（Client 取自它）与 `CLEARING_ACCOUNT` 引用，只允许 `CHANNEL_COST + ADJUSTMENT`，付方 / 收方为 CHANNEL ↔ OPERATING_ENTITY。

## 事件

批准提交后由 `BillingFactRecordedHandler` 经 `DomainEventPublisher` 发布 `BillingFactRecorded`（Schema 1，聚合 `BILLING_FACT`，
aggregateId = factId，aggregateVersion = factVersion，occurredAt = approvedTime）到 `billing.events`，Broker 确认后写 `published_at`；
`messaging.publisher.unroutable-policy: FAIL`（唯一消费方 account 已绑定）。

## 其他

- `t_business_calendar*` 是只读副本，种子行（DEFAULT 与 AE_BUSINESS_DAYS 日历）在 `billing-initial-data.sql`；币种规则与状态值类型取自 `paytech-platform-support-api`。
- 运行：`BILLING_DB_*`、`BILLING_REDIS_*`、`BILLING_RABBITMQ_*`、`BILLING_SNOWFLAKE_*`、`BILLING_PLATFORM_SERVICE_URL`（默认
  `http://127.0.0.1:18106/platform-support`）、`PAYTECH_INSTANCE_ID`；测试库账号 `billing_app`，Redis 前缀 `billing-mock`，RabbitMQ 凭据取自 `local-environment/.env.local`。
