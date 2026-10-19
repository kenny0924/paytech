# account 明细账与 ledger 控制科目重构：技术方案

> 状态：第 1 节（阶段 2 现状差异与影响分析）为 2026-10-19 评审稿；第 2 节起（阶段 3 方案）待阶段 2 确认后编写。需求与验收标准见 [requirement.md](./requirement.md)。

## 1. 现状差异、能力缺口与逐系统影响（阶段 2）

依据：2026-10-19 对 `paytech/account`、`paytech/ledger`、`paytech/billing`、`paytech/foundation/paytech-messaging`、`paytech/tools/arch-rules`、`scripts/pdmaner`、`paytech/operations-console-bff`、`operations-console-web`、`paytech/platform-support`、`test-environment` 与 `requirements-site` 的只读源码调查；实现与部署状态以[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)为准。下文路径相对仓库根，`A` = `paytech/account/paytech-account-core/src/main/java/com/paytech/account`，`L` = `paytech/ledger/paytech-ledger-core/src/main/java/com/paytech/ledger`。

### 1.1 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为（验收） | 缺口 | 处理建议 |
|---|---|---|---|---|
| account 入账批次与序号 | 无批次概念；`t_balance_applied_fact` + `t_balance_applied_entry` 直接挂事实（`A/domainservice/service/impl/BalanceApplicationServiceImpl.java:57-160`） | 每次入账一条 `t_account_posting`，主体 + 币种 + 分片内序号连续（AC-01） | 未实现：表、序号分配（锁同一行）、0 流水批次 | 新增表；序号分配锁同一主体 + 币种 + 分片的序号行，序号行的落点（独立序号表或批次表自身）在阶段 3 定 |
| 版本替代冲回 | account 对 APPLIED 的前版本写反向余额增量但**不写流水行**（`:118-127`）；ledger **完全不处理**替代（`L/domainservice/service/impl/TransactionPostingServiceImpl.java:129-133` 按 `economic_fact_id` 返回已有指令，`supersedesFactId` 未读） | 冲回行带 `reverses_entry_id`，ledger 按流水生成冲回分录（AC-02） | account 需改写冲回；ledger 需新增冲回分录逻辑 | 冲回与新版本在同一批次（一条事实一条批次） |
| `AccountPosted` 可靠发布 | account 不发布任何事件；`t_compensation_event` 已建、`compensation-event-catalog.yaml` 为空；`BalanceMovedPayload` 与目录项 `BalanceMoved` 无生产者（`paytech/foundation/paytech-messaging/catalog/events.yaml:44-51`） | 入账事务内登记补偿事件，提交后发布，严格有序（AC-03） | 未实现：载荷类、Handler、目录项、消费端分区配置 | 参照 billing `EconomicFactRecordedHandler`（`paytech/billing/paytech-billing-core/.../service/handler/EconomicFactRecordedHandler.java`） |
| ledger 消费源 | `L/ext/mq/EconomicFactRecordedPostingHandler.java:30` 消费 billing，`strictOrder=false`，从事件载荷读明细，不调 billing-api | 消费 `AccountPosted`（严格有序），非 Owner 行事务外经 billing-api 读事实（AC-03） | 需新 Handler、新 Reader、事务拆分（事务外读事实 → 事务内过账）、`messaging.consumers.account` 配置 | 事务拆分符合事务公理；billing-api `GET /billing/v1/economic-facts/{factId}` 已存在（响应行缺 `partyType / partyId`，规则推导按 payer / payee 角色即可） |
| 控制科目账户 | 规则包 Owner 行按 `owner_role` CLIENT / LOCAL_PARTNER 自动开 Client / Partner × 桶账户（`TransactionPostingServiceImpl.java:332-401`）；身份矩阵允许 CLIENT / LOCAL_PARTNER（`L/domainservice/validator/account/AccountIdentityRule.java:30-35,89-95`） | 每账簿每 Owner 科目一个控制账户，Owner = 账簿主体，桶 NONE（AC-04、AC-14） | 规则包 8 条 Owner 行（`ledger-initial-data.sql` 5202、5208、5315–5320）要删；身份矩阵、`CreateRuleVersionProcessor:148-179`、人工调整规则 `OwnerAccountAdjustmentRule:19` 要改 | 删除 5202 / 5208 后 PAYMENT 规则本身不再平衡，平衡由 account 流水行补足，测试需按新口径重写 |
| 严格有序与首条基线 | `IdempotentConsumeInterceptor.java:93-100`：首次见到聚合接受任意版本；GAP 只在 strictOrder 下拒绝并重投，5 次后进死信 | 不允许跳过批次（AC-08） | 首条可能不是序号 1 而被静默接受 | ledger 自校验连续性 + `catch-up` 命令（Q-06） |
| 关账一致性 | 步骤 2 `checkCaughtUp`（`L/service/processor/StartPeriodCloseProcessor.java:262-280`）调 billing `pending` 与 account `consistency`；步骤 3 `RECONCILE_ACCOUNT_BALANCES` 只核渠道 / 银行非零数（`:286-294`） | `CHECK_ACCOUNT_POSTING_PROGRESS` 两段检查（AC-07） | account 进度接口不存在；ledger 进度读 `t_consumed_aggregate_version` 需按分片比对；范围只到日历主体的账簿（`:146-160`） | 新接口 `GET /account-postings/v1/progress`；FINANCE_ENTITY 账簿接收所有 OE 流的平台服务费分录，其关账检查范围按阶段 3 定义 |
| `PROJECTION_REBUILD` | `TrialExceptionApi` 两端点、两个 Processor、Validator、`TrialExceptionServiceImpl.PROJECTION_REBUILD`；试算只产生 CHART_ACCOUNT 异常，生产不可达 | 删除（AC-12） | 死代码 | 连同 `ProjectionRebuildTest` 一并删除 |
| 桶移动命令 | 不存在任何 API / Processor / DO（只有 DDL） | `source_type + source_ref` 幂等命令（AC-05） | 全新实现 | 命令在 account 本地事务：锁来源与目标桶账户 → 批次 + 两条流水 + 余额 CAS |
| 到期转可用 | 无；所有映射金额停在 PENDING（`A/domainservice/validator/FactBalanceMappingRule.java:50-66`）；`balance_available_time` 只在 billing 表与 DO（`paytech/billing/.../EconomicFact.java:145`），不在事件载荷，也无写入逻辑 | 定时任务按事实 `balance_available_time` 移动（AC-06） | 载荷缺字段；account 无存储；无 Job | Q-02：billing 载荷加可选字段；account `t_balance_applied_fact` 存该时间与成熟标记；XXL-Job `accountBalanceMaturity` |
| 余额重建 | `RebuildBookBalancesProcessor` 按 ledger 账簿 ID，经 billing-api 分页重放重算（`A/integration/billing/impl/EconomicFactReplayClientImpl.java:34`） | 按主体 + 币种从本库流水重算（AC-11） | 路径参数与算法都变 | 删除 billing 重放客户端；保留 `paytech-billing-api` 依赖仅为事件载荷 |
| 余额查询 | 先经 ledger 找账簿，缺失返回 `LEDGER_BOOK_NOT_FOUND`（`A/service/processor/QueryClientBalanceProcessor.java:32`）；BFF / Web 映射到 entityNo 字段 | 不依赖账簿（AC-16） | 错误合同变 | Q-03 |
| 账户状态 | `BalanceStatus` 只有 ACTIVE | ACTIVE / FROZEN / CLOSED，只约束命令（AC-10） | 枚举、字段、命令校验 | 本期没有冻结 / 销户命令入口，状态由 DML 或后续需求维护；入账路径加 CLOSED 告警 |
| 控制台账户枚举 | BFF 正则 7 种 Owner 类型（`.../ext/rest/FinanceLedgerBookController.java:81`、`service/model/FinanceLedgerBookModels.java:141`）；Web `finance.ts:36-39` 与账户表单 `FinanceModals.tsx:185-219`；夹具与验收脚本都建 CLIENT 账户 | 只有 ledger 的 5 种类型，桶 NONE（AC-14） | 合同收窄 | 改 BFF、Web、夹具、`local_acceptance.py`、`local_browser.py`、Platform 注册 SQL 的 R10 / R11 Query 白名单与阅读副本 |
| 分录来源 | Web `finance.ts:117` 只认 AUTO / MANUAL；`InstructionOrigin` 只有 AUTO / MANUAL；`t_instruction_line` 无 `account_entry_id` | `ACCOUNT_POSTING` 来源与流水 ID（AC-15） | 枚举、字段、响应体、Web 解码 | ledger 响应体加 `accountEntryId`、来源字段加批次 ID / 序号 |
| 明细账查询页 | 无 | AC-17 | 全新：account 3 个只读接口、BFF 路由、Web 页面、注册 DML、画板 | Q-04 |
| requirements-site | 目录与时序不提 `EconomicFactRecorded` / `AccountPosted`；E2E-06 / 09 / 10 / 12、FIN-18 / 19 / 21 / I16 仍写 BANK_CASH 账户行锁定、成熟 Journal 与账户余额核对（`requirements-site/content/requirements.ts:93-98,307-333`、`global-use-case-sequences.mjs:382-632`）；三处测试钉住措辞 | 与决定一致 | 文档漂移 | Q-07 |

### 1.2 逐系统影响表

| 系统 / 仓库 | 业务职责 | 影响点 | 改代码 / 模型 / 配置 | 上下游依赖及兼容风险 |
|---|---|---|---|---|
| **paytech/account** | 明细账：Owner 账户、桶、流水、批次、余额 | api：新增 `AccountPostedPayload`、桶移动命令 API、入账进度 / Owner 账户 / 流水 / 批次查询 API、`OwnerAccountType` 目录、错误码；`OwnerType` 收窄；删 `BalanceBookApi`（consistency / 按账簿列表 / 按账簿重建）、`BalanceMovedPayload`。core：`BalanceApplicationService` 改批次 + 流水 + 序号；新增 `BalanceMovementService`、`AccountPostingPublisher`（补偿 Handler）、成熟 Job、进度与明细查询；删 `LedgerBookClient`、`EconomicFactReplayClient`、`BalanceReconciliationService`、差异表 DO / Mapper。app：删 ledger 客户端配置，加 XXL `accountBalanceMaturity`、补偿事件与发布配置。test：34 例大部分重写，`AccountDataCleaner` 表名、`AccountModuleBoundaryTest` 依赖集、`MockLedger*` 删除，新增 `AccountPostedProbe` 消费探针 | 代码 ✔ 模型 ✔（8 表：改 4、新 1、删 1）配置 ✔（`ACCOUNT_LEDGER_SERVICE_URL` 删除；`messaging.publisher`；XXL 执行器已有） | 下游 ledger、Operations BFF 合同变；mor-test `mor_account` 0 行可直接按变更 SQL 重建；XXL `accountBalanceReconciliation` 在 mor-test 未登记（台账 L97），新 Job 登记随阶段 6 |
| **paytech/ledger** | 总账：科目、账簿、控制科目、分录、试算、关账 | api：`LedgerOwnerType` 与规则角色枚举、`CreateAccountRequestBody` / `LedgerBookQueryApi` 正则、分录响应体加来源与 `accountEntryId`、删 `LedgerReadApi.account-totals` 与 `TrialExceptionApi`、新增 `catch-up` 命令 API。core：新 `AccountPostedPostingHandler` + Reader、`ControlAccountPostingService`（流水行 → 控制账户、冲回）、非 Owner 行事务外读 billing 事实、`AccountIdentityRule` 矩阵、`CreateRuleVersionProcessor`、`OwnerAccountAdjustmentRule`、`StartPeriodCloseProcessor` 步骤 3 改两段检查、`findOpenPeriod` 已关账顺延、删 `BalanceConsistencyClient` 与 `PROJECTION_REBUILD` 链路；`InstructionOrigin` 加 `ACCOUNT_POSTING`。app：`LedgerPersistenceConfiguration` TypeHandler 改本地枚举、`messaging.consumers.account`、`LEDGER_ACCOUNT_SERVICE_URL` 改为 catch-up 用。test：约 215 例中 posting 23 例重写，setup / query / adjustment / trial / close 约 60 例改 Owner 口径，新增 `AccountPostedPublisher` 探针与 billing 事实 DTO Mock，`LedgerDataCleaner.RESERVED_ROWS` 86 → 78 | 代码 ✔ 模型 ✔（7 表改允许值 / 加字段；脱离生成器）配置 ✔ 初始化 DML ✔（删 8 行规则） | 上游 account；billing-api 仍用于事实明细与 `pending`；Operations BFF 合同变；mor-test `mor_ledger` 有 86 行种子且无业务行，按 8.4 清空重建 |
| **paytech/billing** | 经济事实唯一来源 | `EconomicFactPayload` 加可选 `balanceAvailableTime`，Handler 组装时填入（现为 null）；`compensation-event-catalog.yaml` 不变 | 代码 ✔（api + core 各一处）模型 ✘ 配置 ✘ | 载荷向后兼容；`unroutable-policy: ACK_AND_WARN` 改回 FAIL 属台账既有事项，不在本需求 |
| **paytech/foundation/paytech-messaging** | 事件目录与消费基础设施 | `catalog/events.yaml`：`EconomicFactRecorded` 消费者改 [account]，删 `BalanceMoved`，加 `AccountPosted`（producer account，aggregateType ACCOUNT_POSTING，versionField postingSequence，consumers [ledger]，strictOrder true） | 配置 ✔ 代码 ✘ | R12 按目录校验 Handler；`DomainEventHandlerRegistryAutoTest` 若断言目录内容需同步 |
| **paytech/tools/arch-rules** | R4 表归属、R12 事件目录 | `scripts/generate-table-ownership.mjs` 的 `stepAddedTables`（account 五张表名变）与 design/03 §2 / §4.2 同步；`TableOwnershipAutoTest` 总数与 account 表集合；fixture `BalanceEchoHandler` 引用 `BalanceMoved`（仅 fixture，视目录校验范围决定是否改） | 代码 ✔（生成器 + 测试）文档 ✔ | `paytech/scripts/verify-reactor.sh --check` 必须通过 |
| **scripts/pdmaner** | PDManer 生成与门禁 | ledger 摘出生成器（`service-split.mjs`、`generate-finance-model.mjs`、`manifests/09`、`finance-model.test.mjs`、`pdmaner-canonical-paths.test.mjs`）；新增 `ledger-model.test.mjs`；`account-model.test.mjs` 关系列表随新表更新；84 表逻辑模型冻结不改（与 account 摘出时一致） | 代码 ✔ | `finance-model.test.mjs` 已有 2 例既有失败（cf4a429c 漂移），须区分 |
| **paytech/operations-console-bff** | 财务页面聚合与转发 | `FinanceLedgerBookController` / Models 正则与 `@Pattern` 改 ledger 枚举；分录模型加来源与 `accountEntryId`；`FinanceBalanceClientImpl` 错误码；新增 `FinanceSubledgerController`（3 条路由）与 account 客户端方法；夹具 `finance-ledger-boundary.json` / `finance-setup-boundary.json` 改 Owner；精确路由集合 +3 | 代码 ✔ 配置 ✘（base-url 不变） | 依赖 account-api / ledger-api 新版本 |
| **operations-console-web** | 财务页面 | `finance.ts` 枚举、桶规则、错误表、来源类型、新类型与路径；`FinanceModals.tsx` 账户表单；`JournalEntryDrawer.tsx` 来源与跳转；`FinanceBalances.tsx` 错误字段；新增 `pages/FinanceSubledger.tsx` 与 `finance/Subledger*.tsx`；`pageCapabilities.ts` / `ConsolePage.tsx` +1；`finance.test.ts` 与计数断言 33 → 34；`docs/design.pen` 补画板 | 代码 ✔ 设计 ✔ | 现有画板 04 / 05 / 09 / 11 内容变 |
| **paytech/platform-support** | 路由、权限、Console Resource 注册 | 初始化 SQL 新增独立块（明细账 3 条路由 + 1 个 Console Resource + Header / Query / 限流）；块 97 的 R10 / R11 `ownerType` 白名单不在 SQL 中限定值（只登记参数名），**不需要改**；`console-resource-capabilities.json` +1；`OperationsCatalog` 不加权限 | 配置 / DML ✔ 代码 ✘ | 需求目录放阅读副本与本机 / mor-test 激活 SQL |
| **test-environment** | mor-test Compose | account 新 XXL Job 无需 Compose 变更；`LEDGER_ACCOUNT_SERVICE_URL` 保留（catch-up）；`ACCOUNT_LEDGER_SERVICE_URL` 删除 | 配置 ✔（1 行删除） | 阶段 6 发布清单含 5 个镜像（account、ledger、billing、BFF、Web）+ Platform 注册 |
| **local-environment** | 本机库 | 空库由 `init/ddl.sql` 手工建；已有库按变更 SQL | 无脚本改动 | 阶段 5 本机验收前需用户授权执行变更 SQL |
| **agent-docs / design-discuss / requirements-site** | 权威文档与目录 | `CURRENT_IMPLEMENTATION.md` 台账、决定记录实现状态、ledger / account README（README 现仍描述 11b 消费与 consistency）、`DOCUMENT_GOVERNANCE.md` 是否登记（决定第 6 节待用户）、requirements-site 冲突条目（Q-07） | 文档 ✔ | — |
| **paytech/payout、paytech/tax、paytech/reconciliation** | 出款、缴税、对账 | 本期不改（无代码）；模型中引用 ledger 账户的字段留待各自切片 | ✘ | 决定第 6 节 |
| **paytech/billing-api 消费者 card / acquiring / client-master** | — | 不受影响（不消费 Finance 事件） | ✘ | — |

### 1.3 阻塞依赖、难点与工作量依据

1. **ledger 消费端的事务拆分。** 非 Owner 行要在事务外读 billing 事实，再在事务内过账；消费 Handler 需用 `HANDLER_MANAGED` 事务（acquiring、client-master 已有先例），幂等表写入与业务写入的边界要按 messaging 库约定核对。
2. **序号连续性与追平机制（Q-06）。** 消息库首条基线行为决定 ledger 必须自校验；`catch-up` 命令绕过消费拦截器时要同步推进 `t_consumed_aggregate_version`，需确认 `ConsumedEventRepository` 可被业务侧调用。
3. **关账检查范围（8.8）。** OE 账簿只需本主体 + 币种的流；FINANCE_ENTITY 账簿接收所有 OE 流的平台服务费分录，其两段检查范围要在阶段 3 定义（建议：按该期间内向此账簿过账过的全部流 + billing 中 `financeEntityId` 等于该主体的未入账事实）。
4. **测试重写量。** account 34 例几乎全部、ledger 约 80 例、BFF 约 25 例、Web 14 例受影响，另需新增链路测试（设计第 10 节 10 个场景）。这是本需求最大工作量，agent 分线时测试与实现同线。
5. **枚举跨模块持久化（Q-05）。** ledger 的 `CodeEnumTypeHandler` 现注册 account-api 枚举，改为本地枚举后库中已有值（OPERATING_ENTITY 等）兼容，CLIENT / LOCAL_PARTNER 行按 8.4 清空。
6. **mor-test 变更 SQL。** `initialize.py` 不处理已有库的结构变更，需手工变更 SQL（account：删 1 建 1 改 4；ledger：改 3 表允许值 / 加 1 字段 / 删 8 行规则 / 清空 Owner 账户与引用它们的指令、分录、试算行）。
7. **并行会话。** 工作区另有 compliance 的未提交改动；本会话已改的 account 模型 / 文档属本需求前序，一并纳入。

### 1.4 阶段 2 结论

- 决定与设计已覆盖目标形态；代码侧缺口集中在 account（批次、序号、发布、桶移动、成熟、查询）与 ledger（新消费、控制科目、冲回、两段检查、catch-up），控制台属合同跟随改动加一个新页面。
- 两项设计外补充需用户定：事件载荷加 `balanceAvailableTime`（Q-02）、ledger 追平机制（Q-06）。
- 无历史数据迁移；mor-test 与本机都按变更 SQL 重建 Finance 数据。

## 2. 方案、接口与数据设计（阶段 3，2026-10-19 评审稿）

**规模：** 本次方案涉及 9 个系统（account、ledger、billing、Foundation 事件目录、arch-rules、scripts/pdmaner、Operations BFF、Operations Web、Platform 注册）；接口新增 9、修改 7、删除 6（按 Owner Method + Path 去重，BFF 转发另列）；事件新增 1、删除 1、修改 1；account 新增 2 张表、改 4 张、删 1 张（8 → 9），ledger 新增 1 个字段 + 1 个索引、4 处允许值变化，删除 8 行规则 DML；Platform 注册 DML 新增 1 个块约 44 行；页面新增 1 个（3 个画板），修改 3 个画板。

### 2.1 业务与架构

**链路与事务（事务公理：事务内只操作本库）**

| 环节 | 事务边界 | 内容 |
|---|---|---|
| account 消费 `EconomicFactRecorded` | SHARED（消费拦截器事务） | 锁顺序固定：① `t_balance_applied_fact` 按 `fact_id` 行锁（幂等与替代链，同现状）→ ② `t_account_posting_stream` 行锁（分配 `posting_sequence`，行不存在时插入）→ ③ `t_owner_account` 按 ID 升序 CAS。同一事务写：批次、流水、余额、`t_balance_applied_fact.posting_id`、补偿事件 `ACCOUNT_POSTED`。不影响 Owner 的事实也走 ②，生成 `entry_count = 0` 的批次 |
| `AccountPosted` 发布 | 事务外（补偿 Handler） | `AccountPostedHandler` 按事件键读批次与流水重建载荷，发布到 `account.events`，Broker 确认后写 `t_account_posting.published_time`；策略与 billing 的 Handler 相同（20 秒 / 12 次 / 5 秒起指数退避至 30 分钟） |
| 桶移动命令 | Processor 事务外 → 领域服务一个短事务 | ① `t_balance_movement` 按 `(source_type, source_ref)` 查已存在 → 幂等返回；② 流行锁；③ 转出、转入两个账户按 ID 升序锁与 CAS，转出桶不足拒绝；④ 写移动、批次（`source_type = BALANCE_MOVEMENT`）、两条流水、补偿事件 |
| 到期转可用 | XXL-Job `accountBalanceMaturity` → 每条事实一个短事务 | 查 `maturity_status = PENDING` 且 `balance_available_time <= now` 的已应用事实；对该事实流水中记入 `*_PAYABLE / PENDING` 的每个账户，按该账户在此事实上的净贷方金额做一次 `FACT_MATURITY` 移动（`source_ref = {factId}:{factVersion}:{accountId}`），全部完成后事实标记 MATURED。重复执行按 `source_ref` 幂等 |
| ledger 消费 `AccountPosted` | HANDLER_MANAGED | 事务外：① 首条序号校验（见下）；② `source_type = BALANCE_MOVEMENT` → 直接返回（拦截器推进进度，不过账）；③ 经 billing-api `GET /billing/v1/economic-facts/{factId}` 读事实头与明细（版本不等于载荷版本 → 并发异常重投）。事务内：生成指令并过账（见 2.1 控制科目）。拦截器成功后推进 `t_consumed_aggregate_version` |
| ledger 关账 | 现有 `AbstractLockProcessor` | 步骤 3 `CHECK_ACCOUNT_POSTING_PROGRESS` 事务外调 billing、account 两个只读接口，再在本库比对 |

**序号与分片**

- 聚合 ID 带主体类型前缀，避免 OE 编号与 Finance Entity 主键歧义：`{OE|FE}.{entityId}.{currency}.{shardNo}`，例如 `OE.AE001.USD.0`、`FE.8001.USD.0`（≤ 40 字符；分隔符用 `.` 而不是 `:`，因为 Foundation `AggregateKey` 规定聚合 ID 只允许 `[A-Za-z0-9._-]`，`:` 是事件键的分段符；阶段 4 T0 发现并确认）。这是对设计文档第 7 节 `entity_id:currency:shard_no` 的细化，阶段 4 同步回写设计文档。
- 事件键 `ACCOUNT_POSTING:{aggregateId}:AccountPosted:{postingSequence}`（如 `ACCOUNT_POSTING:OE.AE001.USD.0:AccountPosted:18`）；`t_account_posting_stream` 一行一个流，`last_sequence` 单调加一。设计文档第 4.1 节"表数 8 → 8"因此改为 8 → 9（新增流表），阶段 4 回写。

**ledger 首条序号校验与追平（Q-06）**

- Handler 事务外先读 `ConsumedEventRepository.lastVersion(aggregateKey)`：为空且 `postingSequence != 1` → 抛 `ConcurrentRequestException` 等待重投（直到序号 1 到达或运维追平）；不为空时缺口 / 旧版本由拦截器判定。
- 达到投递上限的消息进入 `ledger.account.dlq`；同一分片后续批次因缺口同样最终进入死信。恢复路径不是重放死信，而是 `POST /posting-streams/v1/catch-up`：按流经 account-api `GET /account-postings/v1?fromSequence=lastVersion+1` 分页读取，逐批次按与消费相同的逻辑过账，每批次一个本库事务并调用 `advance` 推进进度；之后重放的死信消息按 STALE 跳过。关账检查的 ledger 段用同一进度读取，不依赖队列状态。

**控制科目过账（ledger）**

| 项 | 规则 |
|---|---|
| Owner 行 | 载荷每条流水 → 一行：科目 = `account_code` 对应科目表科目；账户 = 该账簿下 (科目, `owner_type` = 账簿主体类型, `owner_id` = 账簿主体, 桶 NONE) 的控制账户，不存在时自动开户（ACTIVE）；方向、金额照抄；`account_entry_id = entryId`；`accounting_rule_line_id` 为空。流水带 `reversesEntryId` 的冲回行与普通行同样处理（流水本身已是反向） |
| 非 Owner 行 | 现有规则引擎去掉 Owner 角色后不变：PAYMENT 用 `STANDARD_TRANSACTION`，MANUAL_ADJUSTMENT / COST_ADJUSTMENT 用 `STANDARD_ADJUSTMENT`；`PLATFORM_SERVICE_CHARGE_PLATFORM` 仍进 Finance Entity 账簿 |
| 版本替代 | 替代事实的批次：Owner 行来自流水（含冲回行）；非 Owner 行 = 对新事实应用规则 + 对被替代事实（`supersedesFactId`）已过账指令中 `accounting_rule_line_id` 非空的行取反。同一指令内净额替代，不新增 REVERSAL 类型分录 |
| 替代先到 | 载荷 `factApplyStatus = SKIPPED`（被替代事实的自身事件后到）→ 不生成任何行，只推进进度 |
| 指令头 | `origin_type = ACCOUNT_POSTING`、`source_system = account`、`source_event_key = postingId`、`source_version = postingSequence`、`economic_fact_id = factId`、`instruction_type` 同现状；`uk_instr_source` 保持 |
| 过账日期 | 取 billing 事实业务时间（PAYMENT 为 `paymentSucceededTime`，其余 `recordedTime`）在日历时区的日期；所属期间已 CLOSED → 取其后最早的 OPEN 期间，`posting_date` 保留业务日期（8.9）；没有可用 OPEN 期间 → 抛并发异常等待（期间开放后重投或追平） |
| 人工调整 | `OwnerAccountAdjustmentRule` 改为：任一行账户的科目代码属于 Owner 控制科目集合（`CLIENT_PAYABLE`、`CLIENT_SECURITY_DEPOSIT`、`CLIENT_RECOVERY_RECEIVABLE`、`PARTNER_PAYABLE`、`PARTNER_RECOVERY_RECEIVABLE`）即拒绝，错误码不变 |
| 身份矩阵 | `AccountIdentityRule`：控制科目集合只允许 `owner_type ∈ {OPERATING_ENTITY, FINANCE_ENTITY}`、`owner_id` = 账簿主体、桶 NONE；其余科目只允许平台类 Owner（OE、FE、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY）、桶 NONE。`BANK_CASH` 在 ledger 仍为 `CASH_ACCOUNT / NONE` |

**关账 `CHECK_ACCOUNT_POSTING_PROGRESS`（步骤 3，替代原步骤 2 的 account 调用与步骤 3）**

对关账上下文中每本账簿：

1. 段 ① billing → account：billing `GET /billing/v1/economic-facts/pending?bookEntityType&bookEntityId&currency`（响应新增 `publishedFactCount`）与 account `GET /account-postings/v1/progress`（同参数）。通过条件：`pendingCount = 0` 且 `appliedFactCount = publishedFactCount` 且 `maxAppliedFactId = maxPublishedFactId`。被 account 拒绝进死信或卡住的事实使计数不等。
2. 段 ② account → ledger：account 进度响应列出该账簿涉及的全部流（OE 账簿：本主体 + 币种各分片；FE 账簿：主体为该 FE 的流 + 所有 `finance_entity_id` 等于该 FE 的事实所在的 OE 流），逐流比对 ledger `lastVersion(aggregateKey) = maxPostingSequence`。
3. 任一条件不满足记录阻断项（流、两侧序号或计数）并失败关闭；任一接口不可用按 UNAVAILABLE 失败关闭。

步骤 2 `CHECK_ASYNC_AND_JOURNAL` 删除对 account 的调用，保留本库未过账指令与不平衡分录检查；`PeriodCloseServiceImpl` 中对渠道 / 银行账户非零数的检查随 `RECONCILE_ACCOUNT_BALANCES` 一并删除。

**account 规则与目录**

- `OwnerAccountType`（account-api 枚举，不建表）：

| 代码 | Owner 类型 | 正常方向 | 允许桶 |
|---|---|---|---|
| `CLIENT_PAYABLE` | CLIENT | CREDIT | PENDING、AVAILABLE、LOCKED |
| `CLIENT_SECURITY_DEPOSIT` | CLIENT | CREDIT | NONE |
| `CLIENT_RECOVERY_RECEIVABLE` | CLIENT | DEBIT | NONE |
| `PARTNER_PAYABLE` | LOCAL_PARTNER | CREDIT | PENDING、AVAILABLE、LOCKED |
| `PARTNER_RECOVERY_RECEIVABLE` | LOCAL_PARTNER | DEBIT | NONE |
| `BANK_CASH` | CASH_ACCOUNT | DEBIT | AVAILABLE、LOCKED |

  ledger 测试断言每个代码在科目表中存在且 `posting_allowed`。
- `FactBalanceMappingRule`：删除 CHANNEL 目标（渠道只在 ledger）；PAYMENT 的 CLIENT_PRINCIPAL / CLIENT_FEE → `CLIENT_PAYABLE / CLIENT / PENDING` 贷，PARTNER_SHARE → `PARTNER_PAYABLE / LOCAL_PARTNER / PENDING` 贷；MANUAL_ADJUSTMENT 行按付方 / 收方为 CLIENT / LOCAL_PARTNER 产生借 / 贷（同现状）；COST_ADJUSTMENT 无 Owner 影响 → 0 流水批次。`CLIENT_SECURITY_DEPOSIT`、`*_RECOVERY_RECEIVABLE` 本期无事实来源（Q-01），只在目录与开户校验中存在。
- 替代冲回：冲回行记在原流水的同一账户（含已成熟后仍记 PENDING 的情况，可能出现负 PENDING）；净余额为负时记 WARN 日志。更细的扣减顺序随 8.6 切片。
- 账户状态：`ACTIVE / FROZEN / CLOSED`。桶移动命令对 FROZEN / CLOSED 账户拒绝（`ACCOUNT_NOT_ACTIVE`）；事实入账不检查状态，CLOSED 账户入账时记 WARN。本期不提供改状态接口。
- 余额重建：按流锁 `t_account_posting_stream` 行 → 按 `t_account_entry` 重算该流全部账户 → 以旧 `balance_version` CAS 并加一；不删不改流水。

**枚举归属（Q-05）**

| 枚举 | 现在 | 方案 |
|---|---|---|
| `OwnerType`（account-api） | 7 值 | `CLIENT`、`LOCAL_PARTNER`、`CASH_ACCOUNT` |
| `BalanceBucket`（account-api） | 4 值 | 不变；ledger 继续引用 |
| `OwnerRole`（account-api） | 7 值 | 删除；ledger-api 新增 `RuleOwnerRole`：`BOOK_ENTITY`、`CHANNEL_ACCOUNT`、`PLATFORM_FINANCE_ENTITY`、`TRANSACTION_OE`、`TAX_AUTHORITY` |
| `StandardAccountCodes`（account-api） | 7 常量 | 保留并补 `BANK_CASH`；渠道两个常量留给 ledger 引用 |
| ledger 账户 Owner 类型 | 引用 account-api `OwnerType` | ledger-api 新增 `LedgerOwnerType`：`OPERATING_ENTITY`、`FINANCE_ENTITY`、`CHANNEL_ACCOUNT`、`CASH_ACCOUNT`、`TAX_AUTHORITY`；TypeHandler、DataObject、请求正则、BFF、Web 改引 |

### 2.2 接口

**事件（Foundation `catalog/events.yaml`）**

| 事件 | 变化 | 内容 |
|---|---|---|
| `EconomicFactRecorded` | 修改 | consumers `[account]`；载荷头新增可选 `balanceAvailableTime`（UTC RFC 3339，仅 PAYMENT 且 billing 已计算时非空；Schema 版本保持 1，account 读、ledger 不再消费） |
| `BalanceMoved` | 删除 | 目录项与 `BalanceMovedPayload` 一并删除 |
| `AccountPosted` | 新增 | producer account，aggregateType `ACCOUNT_POSTING`，aggregateVersionField `postingSequence`，consumers `[ledger]`，strictOrder true，payloadSchemaRef `paytech-account-api/event/AccountPosted/v1` |

`AccountPostedPayload`（account-api `event` 包；标量全部为 JSON 字符串）：

```text
postingId, entityType(OPERATING_ENTITY|FINANCE_ENTITY), entityId, currency, shardNo, postingSequence,
sourceType(ECONOMIC_FACT|BALANCE_MOVEMENT), postedTime,
factId, factVersion, factType, supersedesFactId, factApplyStatus(APPLIED|SKIPPED), financeEntityId   -- 事实批次
movementId, movementSourceType, movementSourceRef                                                   -- 桶移动批次
entries[]: entryId, lineNo, accountId, accountCode, ownerType, ownerId, balanceBucket, entrySide, amount, factLineNo, reversesEntryId
```

**account-api**

| 变化 | 接口 | Method / Path | 说明 |
|---|---|---|---|
| 新增 | `BalanceMovementApi` | `POST /balance-movements/v1` | Body：`sourceType`（`PAYOUT_LOCK`、`PAYOUT_RELEASE`、`TAX_PAYMENT_LOCK`、`TAX_PAYMENT_RELEASE`）、`sourceRef`（≤ 128 ASCII）、`entityType`、`entityId`、`currency`、`accountCode`、`ownerType`、`ownerId`、`fromBucket`、`toBucket`、`amount`（Money）、`operatorId` 可空。LOCK 只允许 AVAILABLE → LOCKED，RELEASE 只允许 LOCKED → AVAILABLE；`FACT_MATURITY` 仅内部。返回 `{movementId, postingId, postingSequence, replayed}`：首次 201，同来源重复 200 `replayed = true`。错误：`BALANCE_INSUFFICIENT`、`ACCOUNT_NOT_FOUND`、`ACCOUNT_NOT_ACTIVE`、`MOVEMENT_NOT_ALLOWED`（400）；并发冲突 409 |
| 新增 | `AccountPostingApi` | `GET /account-postings/v1/progress?entityType&entityId&currency` | 返回 `{entityType, entityId, currency, appliedFactCount, maxAppliedFactId, streams[{entityType, entityId, currency, shardNo, maxPostingSequence, lastPostedTime}]}`；FINANCE_ENTITY 主体时 streams 含引用该 FE 的 OE 流。`appliedFactCount` 只计自身事件已到达的事实（排除 PENDING_SUPERSEDED） |
| 新增 | 同上 | `GET /account-postings/v1?entityType&entityId&currency&shardNo&fromSequence&limit` | ledger 追平用：按序号升序返回批次（含流水，结构同载荷），`limit` ≤ 200，返回 `{items, nextFromSequence}` |
| 新增 | 同上 | `GET /account-postings/v1/{postingId}` | 控制台批次详情：批次头 + 全部流水（流水带科目代码、Owner、桶） |
| 新增 | 同上 | `POST /account-postings/v1/streams/rebuild` | Body `{entityType, entityId, currency, operatorId}`；返回 `{accountCount, entryCount, maxPostingSequence}`；无流 400 `STREAM_NOT_FOUND` |
| 新增 | `OwnerAccountQueryApi` | `GET /owner-accounts/v1?entityType&entityId&currency&ownerType&ownerId&accountCode&balanceBucket&page&size` | `entityType`、`entityId`、`currency` 必填；分页同 ledger（10 / 20 / 50 / 100 / 200）；项：`accountId, accountCode, ownerType, ownerId, balanceBucket, normalSide, accountStatus, debitTotal, creditTotal, netBalance, balanceVersion` |
| 新增 | 同上 | `GET /owner-accounts/v1/{accountId}/entries?page&size` | 按批次序号、行号升序：`entryId, postingId, postingSequence, sourceType, factId, factVersion, movementSourceType, lineNo, factLineNo, entrySide, amount, reversesEntryId, postedTime` |
| 修改 | `BalanceQueryApi` | `GET /balances/v1/clients/{id}`、`/partners/{id}` | 不再查 ledger；Owner 在该 OE + 币种下无任何账户 → 400 `OWNER_ACCOUNT_NOT_FOUND`（Q-03）；`LEDGER_BOOK_NOT_FOUND` 删除 |
| 删除 | `BalanceBookApi` | `GET /balances/v1/books/{bookId}/consistency`、`/accounts`、`POST .../rebuild` | 由进度、Owner 账户列表、按流重建替代 |

**ledger-api**

| 变化 | 接口 | Method / Path | 说明 |
|---|---|---|---|
| 新增 | `PostingStreamCommandApi` | `POST /posting-streams/v1/catch-up` | Body `{entityType, entityId, currency, shardNo, maxBatches}`（默认 200）；返回 `{processedCount, lastSequence, caughtUp}`；需 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`（与现有命令一致）；本期不接控制台 |
| 修改 | `LedgerBookQueryApi.listAccounts`、`LedgerBookCommandApi.createAccount` | 同路径 | `ownerType` 正则改为 `LedgerOwnerType` 五值；`ACCOUNT_IDENTITY_INVALID` 覆盖控制科目规则 |
| 修改 | `AccountJournalLineResponseBody`、`JournalEntryDetailResponseBody` | 同路径 | `originType` 增加 `ACCOUNT_POSTING`；新增 `accountPostingId`、`accountPostingSequence`（非 ACCOUNT_POSTING 为空）；详情行新增 `accountEntryId`（规则行、人工行为空） |
| 删除 | `LedgerReadApi.getAccountTotals` | `GET /ledger/v1/books/{bookId}/account-totals` | 连同两个响应体；`getLedgerBook`、`listChartAccounts` 保留 |
| 删除 | `TrialExceptionApi` | `POST /trial-balance-exceptions/v1/{id}/projection-rebuild`、`.../approve` | 连同请求体、Processor、Validator |

**billing-api**

| 变化 | 内容 |
|---|---|
| 修改 | `EconomicFactPayload` 新增 `balanceAvailableTime`（Instant，可空）；Handler 组装时从 `t_economic_fact.balance_available_time` 读取 |
| 修改 | `PendingEconomicFactsResponseBody` 新增 `publishedFactCount`（该主体 + 币种下已发布的 APPROVED / SUPERSEDED 事实数） |

**Operations BFF（`/finance/**`，Gateway 前缀 `/api/operations`）**

| 路由 | Method / Path | 下游 | 权限 | Query 白名单 |
|---|---|---|---|---|
| R10 / R11 修改 | 同现状 | ledger | 同现状 | `ownerType` 正则改五值（SQL 不变） |
| R12 / R13 修改 | 同现状 | ledger | 同现状 | 响应新增字段透传 |
| R17 / R18 修改 | 同现状 | account | 同现状 | 错误码表改 `OWNER_ACCOUNT_NOT_FOUND` |
| R19 新增 | `GET /finance/owner-accounts/v1` | account `OwnerAccountQueryApi` | READ | entityType, entityId, currency, ownerType, ownerId, accountCode, balanceBucket, page, size |
| R20 新增 | `GET /finance/owner-accounts/v1/{accountId}/entries` | account | READ | page, size |
| R21 新增 | `GET /finance/account-postings/v1/{postingId}` | account | READ | — |

### 2.3 数据库与注册数据

**account（`mor_account`，执行文件 [db/account/ddl.sql](./db/account/ddl.sql)）**

按决定第 11 条与设计 8.4，`mor_account` 两环境均无业务数据：变更脚本直接 DROP 旧 5 张业务表并 CREATE 新 6 张；三张基础设施表不动。阶段 4 同步改写 `account-schema.sql`、测试 `init/ddl.sql`、`account.pdma.json`（表、关系与三张图）。

| 表 | 变化 | 要点 |
|---|---|---|
| `t_owner_account` | 由 `t_account_balance` 改名改字段 | `entity_type / entity_id`；`account_status ACTIVE / FROZEN / CLOSED`；删 `last_fact_*`；`owner_type` 允许值 CLIENT、LOCAL_PARTNER、CASH_ACCOUNT |
| `t_account_posting_stream` | 新增 | 每流一行：`(entity_type, entity_id, currency, shard_no)` 唯一，`last_sequence`、`last_posted_time` |
| `t_account_posting` | 新增 | 唯一键 `(entity_type, entity_id, currency, shard_no, posting_sequence)`、`(source_type, fact_id, fact_version)`、`(movement_id)`；`published_time` 可空 |
| `t_account_entry` | 由 `t_balance_applied_entry` 替换 | `posting_id + line_no` 唯一；`account_id`、`fact_line_no`、`reverses_entry_id` |
| `t_balance_applied_fact` | 改 | 加 `posting_id`、`balance_available_time`、`maturity_status`（NOT_APPLICABLE / PENDING / MATURED）、`matured_time`；删 `lines_hash` |
| `t_balance_movement` | 改 | `entity_type / entity_id`；删 `movement_sequence` 与序号唯一键；加 `posting_id`、`operator_id`；`owner_type` 允许值加 CASH_ACCOUNT，桶允许值加 NONE 以外三值 |
| `t_balance_discrepancy` | 删除 | — |

**ledger（`mor_ledger`，执行文件 [db/ledger/ddl.sql](./db/ledger/ddl.sql)、[db/ledger/dml.sql](./db/ledger/dml.sql)）**

| 项 | 变化 |
|---|---|
| `t_instruction_line` | 新增 `account_entry_id BIGINT NULL` + `KEY idx_instr_line_entry (account_entry_id)`（跨库引用只存 ID） |
| `t_ledger_account.owner_type` | 允许值注释删除 CLIENT、LOCAL_PARTNER；`balance_bucket` 注释改为"控制科目一律 NONE" |
| `t_accounting_instruction.origin_type` | 允许值加 `ACCOUNT_POSTING`；`source_*` 注释补充 account 来源 |
| `t_close_step.step_type` | `RECONCILE_ACCOUNT_BALANCES` → `CHECK_ACCOUNT_POSTING_PROGRESS` |
| `t_trial_balance_exception.resolution_type` | 删除 `PROJECTION_REBUILD` |
| 数据清理（8.4） | 删除 `owner_type IN (CLIENT, LOCAL_PARTNER)` 的账户及引用它们的指令行 / 指令 / 分录行 / 分录 / 试算行；两环境均只有测试数据，脚本先 SELECT COUNT 供执行前核对 |
| 规则 DML | 删除 `t_accounting_rule_line` 5202、5208、5315–5320；`ledger-initial-data.sql` 同步，保留行数 86 → 78 |

**Platform 注册 DML（`mor_platform`，阶段 4 由 D 线写入初始化 SQL 新块 99 并放阅读副本 `db/platform-support/registration-dml.sql`）**

| 内容 | 行数 |
|---|---|
| `t_gateway_route` R19–R21（`operations-finance-owner-accounts`、`-owner-account-entries`、`-account-posting`），DISABLED，`HUMAN_PERMISSION` = `FINANCE_ACCOUNTING_READ`，Target 同块 97 | 3 |
| `t_gateway_route_header`（authorization、origin × 3） | 6 |
| `t_gateway_route_query_param`（R19 9 个、R20 2 个） | 11 |
| `t_gateway_guard_rule`（每路由 6 条，登录人速率 30 / 分钟与 R08 一致） | 18 |
| `t_console_resource` `OPERATIONS_FINANCE_SUBLEDGER`（`/finance/subledger`，父 `OPERATIONS_FINANCE`） | 1 |
| 权限、角色授权 | 0（沿用 READ） |
| 合计 | 39 |

`console-resource-capabilities.json` +1；`OperationsCatalog` 不变。激活 SQL 本机 / mor-test 各一份随阶段 5 / 6 提供。

### 2.4 页面（Pencil `operations-console-web/docs/design.pen`，「财务」画板区）

已按下表修改并新增画板（2026-10-19），导出见 [assets/stage3/pencil](./assets/stage3/pencil/)；`design.pen` 未提交。

| 画板 | 变化 | 内容 |
|---|---|---|
| 04 账簿与账户 | 修改 | 账户列表只有控制账户与平台类 Owner：`CLIENT_PAYABLE` / `PARTNER_PAYABLE` / `CLIENT_SECURITY_DEPOSIT` 以 OPERATING_ENTITY AE001 为核算对象、桶 NONE；说明文改写 |
| 05 新建账户错误 | 修改 | 表单示例改为 CHANNEL_ACCOUNT 核算对象，余额桶固定 NONE；提示文案「Client / Partner 余额在明细账，不在总账开户」 |
| 07 账本查询 · 账户列表 | 修改 | 核算对象类型筛选默认「全部」；五行示例改为控制账户与税务机关账户 |
| 08 账本查询 · 账户流水 | 修改 | 面包屑与摘要改为控制账户（OPERATING_ENTITY AE001 · NONE）；来源列改为「ACCOUNT_POSTING · account」+「批次 … · 序号 n」；行后余额按新示例重算 |
| 09 分录抽屉 | 修改 | 来源「ACCOUNT_POSTING · account」、来源事件键为 account 批次 ID、新增「account 批次」字段（序号、主体、分片、流水行数）；控制科目行核算对象为 OE，行内「流水 …3001 ↗」链接；顺带修正抽屉分录行表列宽溢出（原画板 09 已有） |
| 11 余额未找到 | 修改 | 错误挂 Owner ID 字段并高亮该输入：「该 Client 在 SG002 / USD 下没有账户（OWNER_ACCOUNT_NOT_FOUND）」；空状态文案同步 |
| 13 明细账查询 · 账户列表 | 新增 | 左侧导航新增「明细账查询」；主体类型 / 主体标识 / 币种三个必填一行，科目 / Owner 类型 / Owner ID / 余额桶筛选一行；表：科目、Owner（类型 + ID）、余额桶、方向、状态、借方合计、贷方合计、净余额，示例含 CLIENT、LOCAL_PARTNER、CASH_ACCOUNT（BANK_CASH / AVAILABLE）各类账户 |
| 14 明细账查询 · 账户流水 | 新增 | 面包屑「明细账 › AE001 / USD › CLIENT_PAYABLE · CLIENT 1001 · PENDING」；摘要（科目、Owner、桶、方向、当前净余额）；表：批次序号、入账时间、来源类型（事实 / 桶移动）、行号、方向、金额、冲回、事实类型、来源（事实 ID · 版本 + 事件键，或 FACT_MATURITY + 来源引用）；点击批次序号打开批次 |
| 15 明细账查询 · 入账批次抽屉 | 新增 | 批次头（批次主键、主体 / 币种 / 分片、序号、入账时间、来源类型、发布时间）、来源事实（事实 ID、类型、版本、替代事实、应用状态）、全部流水行（科目、Owner、桶、方向、金额）与合计 |

未查询 / 无账户 / 无流水空状态沿用财务页面现有空状态样式（画板 11 的空状态组件），不另绘。

### 2.5 研发与验证计划

**分线（阶段 4，Opus 5.5 agent，各自 worktree）**

| 线 | 范围 | 先后 |
|---|---|---|
| T0 合同 | account-api（`AccountPostedPayload`、三个新 API、枚举收窄、`OwnerAccountType`、错误码）、ledger-api（`LedgerOwnerType`、`RuleOwnerRole`、新 / 改响应体、`PostingStreamCommandApi`、删除项）、billing-api 两个字段 + billing Handler 填值、`events.yaml`、account / ledger `compensation-event-catalog.yaml` | 先行，合入后其余并行 |
| A account | core / app / test 全部改动；`account-schema.sql`、`init/ddl.sql`、`account.pdma.json`、`account-model.test.mjs`、README | 依赖 T0 |
| B ledger | core / app / test 全部改动；`ledger-schema.sql`、`init/ddl.sql`、`ledger-initial-data.sql`、`ledger.pdma.json` 脱离生成器 + `ledger-model.test.mjs`、`scripts/pdmaner` 五个脚本、README | 依赖 T0 |
| C 控制台 | Operations BFF（3 个 Controller 改 + 1 新、客户端、夹具、测试）与 Web（枚举、表单、抽屉、余额错误、新页面、测试、`design.pen` 已有画板按阶段 3 设计实现） | 依赖 T0 |
| D 规则与注册 | `generate-table-ownership.mjs` + design/03 + `TableOwnershipAutoTest`；Platform 初始化 SQL 块 99 + `console-resource-capabilities.json` + 阅读副本 + 激活 SQL；test-environment Compose 删 `ACCOUNT_LEDGER_SERVICE_URL`；requirements-site 冲突条目与测试（Q-07）；设计文档回写（聚合 ID 前缀、9 张表）、`CURRENT_IMPLEMENTATION.md`、决定记录状态 | 依赖 T0（表名） |

主会话：任务卡、跨线一致性核对（路由 = Web 路径 = BFF；载荷字段 = account 写入 = ledger 读取；Owner 类型五值在 ledger / BFF / Web / SQL 注释一致）、独立重跑全部测试。

**验证（阶段 4 开发检查 → 阶段 5 本机）**

| 系统 | 入口与场景 |
|---|---|
| account | 经 Probe 发 `EconomicFactRecorded`：顺序 / 乱序 / 重复 / 替代（先到、后到）/ 0 流水批次 / CLOSED 账户告警；桶移动 HTTP：幂等、不足、非法桶对、CASH_ACCOUNT；成熟 Job：到期 / 未到期 / 重复执行；重建；进度与明细查询；`AccountPosted` 消费探针断言序号连续与载荷；模块边界测试更新 |
| ledger | 经 Probe 发 `AccountPosted`（扮演 account）+ billing 事实 DTO Mock：控制科目行与 `account_entry_id`、非 Owner 规则、替代净额、SKIPPED 批次、BALANCE_MOVEMENT 只推进、首条非 1 等待、缺口重投、catch-up 追平、已关账期间顺延、人工调整拒绝控制科目、关账两段检查（billing 未发布 / account 计数不等 / ledger 落后 / 全部追平）、规则版本创建拒绝 Owner 角色、PROJECTION_REBUILD 删除后试算异常链路 |
| billing | 载荷字段与 `publishedFactCount` 的 auto 测试 |
| BFF / Web | 转发、错误透传、五值正则、新页面加载 / 空状态 / 错误、`finance.test.ts`、页面计数 34 |
| 架构 | `paytech/scripts/verify-reactor.sh`、arch-rules、`scripts/pdmaner` 测试（区分 cf4a429c 既有失败）、requirements-site 测试 |
| 阶段 5 本机全链路 | billing 人工登记 MANUAL_ADJUSTMENT → account → ledger → 控制台账本 / 明细账 / 余额三页；Probe 发 PAYMENT 事实验证成熟与控制科目合计 = 明细合计 |
