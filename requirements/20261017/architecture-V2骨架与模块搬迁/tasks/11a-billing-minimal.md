# 任务卡 11a · billing 最小能力：经济事实登记与 `EconomicFactRecorded`

- 分支：`refactor/step-11a-billing-minimal`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step11a`；起点：`refactor-main`（07、08 合入后，主会话填写）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 11a 步；设计 [04-finance-account-ledger-split.md](../design/04-finance-account-ledger-split.md) §2（阶段 0 的最小计费）；决定 D37、C1。
- 可与 11b 的表拆分部分并行；11b 的消费者依赖本步的事件。

## 1. 输入文档（先完整读取）

- [design/04-finance-account-ledger-split.md](../design/04-finance-account-ledger-split.md) 全文（§2 为本步范围；§5 事件语义；§8 接口清单 billing 部分）。
- [design/08-messaging-module.md](../design/08-messaging-module.md)；`paytech/foundation/paytech-messaging*` README 与 `catalog/events.yaml`。
- 第 07 步报告（billing 模块现状、`t_economic_fact*` 更名后的 DO / Mapper、`TransactionPostingService` 的新入参 DTO 定义、billing 不变量校验类的位置）；第 08 步报告（发布侧串行器装配方式）。
- 现有 `paytech/billing/`：经济事实 DO / Mapper / Repository、`ChannelCostEstimationService`、billing 不变量校验类；现 ledger 的 `ApproveManualAdjustmentProcessor`（Maker-Checker 语义参考）。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`。

## 2. 改动清单（逐条引用 04 §2）

1. **模型**：`t_economic_fact` 的 `fact_type` 取值补 `MANUAL_ADJUSTMENT`、`COST_ADJUSTMENT`（枚举 `EconomicsType` 扩展，常量间空行）；`supersedes_fact_id`、`status`（`DRAFT / APPROVED / SUPERSEDED`）、`fact_version` 列已在第 07 步纸面更名时加入，本步核对 DO / Mapper / 测试 DDL 一致；`channel_account_id` 列在头表存在（04 §6）。
2. **登记与批准（Maker-Checker）**：`paytech-billing-api` 新增 `EconomicFactCommandApi`：`POST /billing/v1/economic-facts`（提交 `DRAFT`：`factType`、来源引用、Client / 合作伙伴 / OE / Finance Entity、币种、明细行 `{feeTypeCode, payerRole/payerId, payeeRole/payeeId, amount, direction}`、`channelAccountId?`、`supersedesFactId?`、`makerId`）、`POST /billing/v1/economic-facts/{factId}/approve`（`checkerId ≠ makerId`，`DRAFT → APPROVED`，同事务：`fact_version` 定稿、若有 `supersedesFactId` 则把被替代事实置 `SUPERSEDED`、登记补偿事件 `ECONOMIC_FACT_RECORDED`）；`GET /billing/v1/economic-facts/{factId}`、`GET /billing/v1/economic-facts?bookId=&fromFactId=&limit=`（分页，按 `fact_id` 升序）、`GET /billing/v1/economic-facts/pending?bookId=`（已 APPROVED 且 `published_at IS NULL` 的计数与最大 `fact_id`，供关账一致性检查）。Processor 继承 Foundation `AbstractLockProcessor`（锁 `economic-fact:<factId>`），事务内不调外部服务。
3. **不变量校验**：第 07 步迁入 billing 的"合作伙伴三元组全有或全无、平台服务费主体等于 Finance Entity"在提交时执行；金额与舍入按 `t_currency_spec` 只读副本（D16：billing 一次决定）。
4. **事件**：补偿 Handler `EconomicFactRecordedHandler` 在提交后读取事实头 + 全部明细行，构造 `DomainEvent{eventType=EconomicFactRecorded, schemaVersion=1, aggregateType=ECONOMIC_FACT, aggregateId=factId, aggregateVersion=factVersion, sourceService=billing}` 经 `DomainEventPublisher` 发到 `billing.events`，成功后写 `published_at`；`event_key` 用 `AggregateKey.eventKey`；Payload 字段文档化到 `paytech-billing-api` 的 Payload 类（供 ledger / account 反序列化）。事件目录 `EconomicFactRecorded` 条目核对（consumers ledger、account；strictOrder=false）。
5. **装配**：billing-app 放开 AMQP 与 compensation-event 禁令（09 §1.4 第 11 步注记），引入 `paytech-messaging`、`paytech-compensation-event`、`paytech-redis-starter`（锁）；`messaging.producer.service=billing`；补偿 `instance-id` 为 billing。
6. **DDL**：billing 测试 DDL 与 Schema SQL 补 `t_compensation_event`（已有）与 `published_at` 列（纸面变更，不执行）；PDManer 模型同步。

## 3. 测试要求

- `auto/`：提交成功 / 校验失败（三元组、主体、币种精度）/ 批准（Checker ≠ Maker、重复批准幂等、替代链）/ 事件发布（本地 Broker + Probe：信封字段、Payload 含全部明细、`published_at` 落库）/ 分页与 pending 查询 / 并发两次批准只发一条事件。≥ 14 例。
- 清理器加 `t_compensation_event`、`t_consumed_event`（若 billing 以后消费）；`ArchitectureAutoTest` 通过（R7：Processor 事务类不注入 Publisher——发布在补偿 Handler）。
- 全 Reactor `verify` 全绿。

## 4. 验证清单

1. `cd paytech && ./mvnw -B verify -pl billing/paytech-billing-test -am` 全绿；全 Reactor 全绿。
2. billing-app 以 `mock` Profile 启动 health UP。
3. 事件目录 R12 零违规；`generate-table-ownership.mjs --check` current。

## 5. 禁止事项

- 不实现支付 / 退款 / 争议 / 出款费用的事实生成（业务切片）；不改 ledger / account（11b）。
- 不执行业务库 SQL（测试库除外）；不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-11a): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/11a-billing-minimal-report.md`：改动摘要 ≤ 30 行、第 4 节逐项、`EconomicFactRecorded` Payload 定义（给 11b）、待确认项。
3. 最终回复两行。
