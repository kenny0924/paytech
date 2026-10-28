# commercial：开通项前置门禁 · 技术方案

> 状态：**阶段 2 评审稿（2026-10-28，基于工作区当前代码只读核对，未改代码）**。阶段 3 起在第 6 节之后补方案、接口、DDL / DML 与验证计划。
> 契约依据：[2026-10-28 客户上线决定 第 3 节](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md#3-契约附件req-00)。需求：[requirement.md](./requirement.md)。

## 1. 核对范围与证据

| 范围 | 证据位置 |
|---|---|
| 开通项写入与状态 | `paytech/commercial/paytech-commercial-core/.../service/processor/AddAgreementCapabilityProcessor.java:165`、`UpdateAgreementCapabilityProcessor.java:30,123-138`、`AgreementCapabilityValidator.java` |
| 执行链路对状态的依赖 | `domain/CheckoutSubscriptionContext.java:90-105`、`domain/CapabilityAcceptance.java:86` |
| 价格激活对状态的依赖 | `domainservice/validator/term/TermVersionActivationValidator.java:38,153-180`、`TermPriceRelevance.java:42,132-144`、`CommercialTermInsightServiceImpl.java:117-128`、`service/processor/CommercialTermLoader.java:340`、`TemplatePriceSelection.java:55-78` |
| 资格查询 | `integration/CheckoutQualificationClient.java:17`、`integration/impl/ComplianceQualificationClientImpl.java:37-50`、`service/processor/CheckoutResolutionProcessor.java:47-86`；compliance `QualificationEvaluationApi`（只允许 commercial-service 调用，资格合格后再检查结算冻结） |
| 事件 | commercial `service/handler/AgreementStatusChangedHandler.java`、`CommercialTermVersionActivatedHandler.java`、`CommercialEventKeys.java`、`configuration/CommercialEventConfiguration.java`（发布侧，compensation-event）；commercial 内**没有** `DomainEventConsumer`；client-master `ext/mq/QualificationChangedListener.java:23-56`（消费模式样板）；compliance `QualificationChangedPayload.java`（`roleType / roleId / status / decisionSource`，`sourceService=compliance`，schema 1） |
| 定时任务 | commercial `ext/job/CommercialTermVersionActivationJobHandler.java` + `ActivateDueCommercialTermVersionsProcessor.java`（XXL-Job 样板） |
| 表结构 | `paytech/commercial/docs/database/sql/commercial-schema.sql` `t_agreement_capability`（`status VARCHAR(32)`，索引 `idx_agreement_capability_lookup(entity_no, capability_code, status)`）；增量脚本目录 `docs/database/sql/changes/`（样板 `2026-10-24-payfac-partner-agreement.sql`） |
| 运营页面 | `operations-console-web/src/commercial/AgreementDetail.tsx`、`ProductCapabilitySections.tsx`、`AgreementModals.tsx`、`commercial.ts`；ops-bff `CommercialAgreementController.java:147-157`（开通项新增 / 更新转发）、`service/model/CommercialAgreementModels.java` |
| data-query | `DataQueryExecutionApi`（`POST /templates/{templateCode}/executions`，`X-Caller-Service` 须在 `data-query.http.callers`）；`QueryTemplateManagementApi` / `DataSourceManagementApi`（模板与数据源为**运行时登记数据**，非 DML）；mor-test 现状见台账 1311 行（callers 只有 operations-console-bff，被查库只有 fixture） |
| 部署状态 | `CURRENT_IMPLEMENTATION.md` 424-431：commercial 六层定价已部署 mor-test，后续 capability / pricing 一期、PayFac 合同已上 |

## 2. 现状发现

| 编号 | 发现 | 影响 |
|---|---|---|
| F-01 | 开通项 ENABLED 只在 `AddAgreementCapabilityProcessor:165` 写入（新行）；合同激活 `ActivateAgreementProcessor` **不创建开通项**，不存在"合同激活时批量创建"路径 | 需求 Q3 不适用；门禁只需改一个创建入口 |
| F-02 | `UpdateAgreementCapabilityProcessor` 允许运营在 ENABLED / SUSPENDED / DISABLED 间**自由切换**（含手动置 ENABLED） | 门禁上线后必须限制：手动不能置 ENABLED；手动 SUSPENDED 与门禁 SUSPENDED 要能区分，否则门禁重查会把风控手动暂停的开通项自动恢复（S-01） |
| F-03 | Checkout 解析 `CheckoutSubscriptionContext.matches` 只认 `status == ENABLED` 且在生效区间 | 新状态 PENDING_PREREQUISITES **天然被排除**，执行链路不用改（AC-01 已满足一半） |
| F-04 | **价格激活依赖开通项 ENABLED**：`TermPriceRelevance` 构造时 `filter(CapabilityAcceptance::enabled)`，Client 合同的价格激活校验 2 要求每条支付方式价格有同 OE 的**已开通**支付方式项，否则抛"价格与开通项不相关（PAYMENT_METHOD_NOT_ENABLED）"；校验 3 覆盖完整只遍历 enabled 项 | **循环依赖**：门禁 ② 要价格 ACTIVE，价格激活要开通项 ENABLED。不处理则新客户永远卡住。必须把价格相关性的口径从"ENABLED"改为"ENABLED 或 PENDING_PREREQUISITES"（S-02） |
| F-05 | 同一口径还用于：`CommercialTermInsightServiceImpl.relevance`（版本洞察 / "待定价"提示）、`CommercialTermLoader:340` + `TemplatePriceSelection`（从价目模板生成合同价格副本时按相关开通项选价） | S-02 的改动点共 4 处，口径必须一致，否则模板生成的价格会漏掉 PENDING 项 |
| F-06 | `CheckoutQualificationClient.evaluate(subjects) → Map<subject, Boolean>`，`CheckoutResolutionProcessor` 把 Client 与候选 OE / Partner 都作为主体；compliance 侧"资格合格的 Client 再检查生效中的结算冻结"，冻结时 `eligible=false` | 门禁 ① 可直接复用，主体 = Client + 开通项所在 OE。**副作用**：结算冻结会让资格不合格 → 开通项 SUSPENDED → 收款也停。这与现有 Checkout 行为一致（Checkout 同样拒绝），不是新口径，但与决定②"结算控制只管出款"字面不一致（S-03） |
| F-07 | commercial 已有 compensation-event 发布链（`compensation.enabled=true`、AMQP starter、Handler 样板），**没有任何 MQ 消费者**；client-master 的 `@DomainEventHandler(eventType, strictOrder=false)` + `event.sourceService()` / `schemaVersion` 校验是可照抄的消费样板 | 新增 commercial 第一个消费者：代码量小，但消费侧队列声明 / 绑定与本地 auto 测试基础设施是本需求的新增面（阶段 3 核实 compensation-event 消费端配置项） |
| F-08 | `QualificationChangedPayload` 含 `roleType`（CLIENT / OPERATING_ENTITY / LOCAL_PARTNER）、`roleId`、`status`（APPROVED / REJECTED / REVOKED / EXPIRED） | 消费者按 roleType 找到受影响开通项：CLIENT → 该 Client 全部合同的开通项；OPERATING_ENTITY → 该 OE 下全部开通项；LOCAL_PARTNER 本期忽略 |
| F-09 | XXL-Job 接入样板已有（`CommercialTermVersionActivationJobHandler`） | 重查 Job 照抄，需在 mor-test XXL 后台登记任务（发布动作） |
| F-10 | 表 `t_agreement_capability` 无检查结果列；`status` 注释枚举三值；增量脚本目录与命名约定已有 | DDL：`status` 值域改为 ACTIVE / DISABLED + 新增 `execution_status`（S-02 方案 A），存量迁移；不加检查结果列（S-04）；PDManer 同步 |
| F-11 | 运营开通项 UI 在 `AgreementDetail.tsx` / `ProductCapabilitySections.tsx`，状态通过 BFF `CommercialAgreementModels` 透传；BFF 已有新增 / 更新开通项转发 | 页面改动是"状态徽标 + 展开清单 + 一个按钮"，BFF 加一个转发 |
| F-12 | data-query 模板 / 数据源是运行时登记；执行入口 `X-Caller-Service` 白名单在 mor-test 只放行 ops-bff；被查库只有 fixture | 本需求交付的三条模板是**文本 + 登记步骤**，登记在发布阶段完成；需要为 `mor_commercial` 建只读账号并登记数据源（DBA 动作，发布授权）。工作流进程内执行（REQ-05）不走白名单 |
| F-13 | ops-bff 已有 `ClientDetailsProcessor`（Client 详情聚合） | 本需求不动；REQ-08 时把开通项"还差什么"并进去 |

## 3. 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 新状态 | 枚举三值；新行直接 ENABLED（F-01） | 新行 PENDING_PREREQUISITES | 未实现 | 加枚举值 + 改创建处 + DDL 注释 |
| R2 检查器 ①资格 | Checkout 已有资格客户端（F-06） | 门禁复用，主体 Client + OE | 调用点未实现，客户端已有 | 复用接口，新增门禁调用 |
| R2 检查器 ②合同价格 | 价格激活反过来依赖 ENABLED（F-04/05） | 合同 ACTIVE 且有 ACTIVE 价格版本覆盖该开通项 | **循环依赖** | S-02：相关性口径改为"ENABLED 或 PENDING"；门禁 ② 复用覆盖校验逻辑判断"该开通项已被 ACTIVE 版本覆盖" |
| R2 检查器 ④渠道身份 | 无 | 调 channel-connector 就绪查询 | REQ-02 未交付 | 定义客户端接口 + 开关（Q1 按建议：关闭时视为满足并标注） |
| R3 门禁执行 | 无 | 事务外查 → 短事务 CAS 置位 | 未实现 | 新 Processor；锁开通项行（Foundation `AbstractLockProcessor`） |
| R4 触发 · 合同 ACTIVE | `ActivateAgreementProcessor` 发 `AgreementStatusChanged`（本地 Handler） | 激活后触发门禁 | 未实现 | 在现有 Handler 链后挂门禁（事务外） |
| R4 触发 · 价格 ACTIVE | `CommercialTermVersionActivatedHandler` | 同上 | 未实现 | 同上（② 的变化源） |
| R4 触发 · 资格事件 | commercial 无消费者（F-07） | 消费 `QualificationChanged` | 消费基础设施新增 | 第一个消费者，照 client-master 样板 |
| R4 触发 · 定时 | XXL 样板（F-09） | 5 分钟重查 PENDING / 门禁 SUSPENDED | 未实现 | 新 JobHandler |
| R4 触发 · 手动 | 无 | "重新检查前置"写接口 | 未实现 | 新接口 + BFF 转发 + 按钮 |
| R5 事件发布 | 发布链已有（F-07） | `AgreementCapabilityStatusChanged` | 未实现 | 新 Payload + Handler，照 `AgreementStatusChangedHandler` |
| R6 页面 | 状态透传，无清单（F-11） | 徽标 + "还差什么" + 按钮 | 未实现 | 小改 |
| R7 查询模板 | data-query 运行时登记（F-12） | 三条模板 + 数据源 | 未登记 | 模板文本随本需求交付，登记在发布阶段；只读账号需 DBA |
| R8 存量 | mor-test 已有 ENABLED 开通项（演示数据 DEMO-CL-001…） | 不回溯 | — | 迁移脚本只改注释，不改数据；巡检为只读 SQL |
| 手动状态切换 | 自由切换（F-02） | 手动不能置 ENABLED；区分手动 / 门禁暂停 | 口径缺失 | S-01 → 已定 | 手动状态与门禁状态共存 | **并入 S-02 方案 A**：执行状态含 `SUSPENDED_MANUALLY` / `SUSPENDED_BY_PREREQUISITE` 两个值；手动只能置 SUSPENDED_MANUALLY 或把 `status` 置 DISABLED；手动"恢复"= 置回 PENDING_PREREQUISITES 并触发门禁；门禁只自动恢复 SUSPENDED_BY_PREREQUISITE；删除手动置 ENABLED 入口 |
| S-02 → 已定（2026-10-28 用户同意方案 A） | 循环依赖根因：`status` 一列同时承担"合同成员资格"（定价用）与"执行开关"（Checkout 用） | **拆成两列**：`status` 只表示合同成员资格 ACTIVE / DISABLED；新增 `execution_status`：PENDING_PREREQUISITES / ENABLED / SUSPENDED_BY_PREREQUISITE / SUSPENDED_MANUALLY（`status=DISABLED` 时为空）。定价 4 处改看 `status=ACTIVE`（已暂停的开通项价格也须覆盖，行为变化已知）；Checkout 改看 `execution_status=ENABLED`。存量迁移：ENABLED → ACTIVE + ENABLED；SUSPENDED → ACTIVE + SUSPENDED_MANUALLY；DISABLED → DISABLED + NULL |
| S-03 | 门禁 ① 复用资格查询会**继承结算冻结 → 不合格**的现有语义，结算冻结将同时停收款 | 本期接受（与 Checkout 现状一致，不新增口径）；在决定②下加注"结算冻结经资格查询影响收款，维持现状"；若未来要区分，由 compliance 在响应里拆出 `frozen` 字段，另立项 |
| S-04 → 已定（2026-10-28 用户：存 JSON 是过度设计） | 检查结果存哪 | **不存**。不加 JSON、检查时间、生效时间列；变更时间与原因由现有 BCR 快照与事件承担。"还差什么"由一个写接口现场核对并返回：`POST /agreements/v1/{agreementId}/capabilities/{id}/prerequisite-checks`（核对三项前置、状态该变即变、返回逐项结果与文案键 + 参数、标注因开关跳过项）；合同页展开清单与"重新检查前置"按钮都调它；定时 Job 调同一段逻辑。查询失败只记日志 / 指标 |
| S-05 | 门禁 ① 主体范围：只查 Client，还是 Client + 开通项所在 OE | Client + OE（与 Checkout 一致，OE 资格撤销也应停） |
| S-06 | 重查批次范围：PENDING + 门禁 SUSPENDED；是否也周期性复核 ENABLED（防事件丢失） | 本期 ENABLED 每日复核一次（低频 Job 参数），PENDING / 门禁 SUSPENDED 5 分钟 |
| S-07 | `QualificationChanged` 的 `LOCAL_PARTNER` 角色 | 忽略（Partner 合同的开通项门禁沿用 OE 主体） |
| S-08 | data-query 只读账号与数据源登记属于发布动作（DBA 建 `mor_commercial` 只读用户、Vault 存密码、运营登记数据源与模板） | 列入阶段 6 发布清单，单独授权；本机 auto 测试不依赖 data-query |

## 6. 工作量与风险

| 项 | 判断 |
|---|---|
| 规模 | commercial core 约 10 个新 / 改类 + 1 消费者 + 1 Job + 4 处口径替换；api 2 处；ops-bff 1 转发；ops-web 1 组件改造；DDL 1 表 1 列 + 值域迁移；注册 1 小块；模板 3 条。维持阶段 1 估计 **M** |
| 最大风险 | S-02 拆列：定价口径改看成员资格（含已暂停项），Checkout 改看执行状态，波及所有合同；存量迁移脚本须幂等并在 mor-test 先核对；靠现有 auto 测试 + 新增"PENDING / 已暂停项也需定价覆盖"用例兜底 |
| 第二风险 | commercial 首个 MQ 消费者的本地测试基础设施（UNIT_TEST_GUIDE 要求真实本地 Broker + 测试消费者） |
| 依赖 | REQ-02（④ 真实判定）、REQ-05（流程读模板）；本需求可独立验收（④ 开关关闭） |
| 阻塞 | 无 |

**确认点**：用户确认 S-01—S-08 与影响范围后进入阶段 3（方案、接口、DDL / DML、页面与验证计划）。

> **阶段 2 已确认（2026-10-28）**：S-01 / S-02 / S-04 按方案 A 与“不存检查结果”定稿；S-03、S-05—S-08 按建议。

---

# 阶段 3：方案、接口与数据设计（评审稿，2026-10-28）

已读规范：`DEVELOPMENT_CONVENTIONS.md`（第 4、5、6 节）、`examples/WRITE_API_EXAMPLE.md`、commercial 现有写接口链（`AbstractAgreementWriteProcessor` → `UpdateAgreementCapabilityProcessor` / `AddAgreementCapabilityProcessor` → `AgreementServiceImpl` → Repository / Mapper）、`AgreementStatusChangedHandler` / `CommercialEventKeys`（补偿发布）、client-master `QualificationChangedListener`（消费样板）、`foundation/paytech-messaging/catalog/events.yaml`。

## 7. 方案总览

```
           ┌──────────── 触发（全部在事务外调用同一个 Processor）────────────┐
 新增开通项 / 恢复 / 合同 ACTIVE / 价格 ACTIVE ──(同事务登记补偿任务)──▶ CapabilityPrerequisiteCheckHandler ─┐
 QualificationChanged（compliance.events）──▶ QualificationChangedListener ───────────────────────────────┤
 XXL-Job 定时重查 ──▶ CapabilityPrerequisiteRecheckJobHandler ──────────────────────────────────────────┤
 运营"检查前置" ──▶ POST /agreements/v1/{id}/prerequisite-checks ──────────────────────────────────────────┤
                                                                                                          ▼
                     CheckCapabilityPrerequisitesProcessor（锁 agreement:{id}，LOCK）
                       1. 读合同、开通项、ACTIVE 价格版本（本库）
                       2. 事务外：compliance 资格批量查询（Client + 各 OE）
                       3. 逐开通项判定 ①②④ → 目标执行状态
                       4. 短事务：CAS 更新 execution_status + BCR 快照 + Root 版本 + 登记状态变更发布任务
                       5. 返回逐项结果
                                                                                                          │
                                         AgreementCapabilityStatusChangedHandler（提交后发布 commercial.events）
```

- **一个判定入口**：所有触发方都调用 `CheckCapabilityPrerequisitesProcessor.process(agreementId)`，判定逻辑只写一次。
- **为什么新增 / 恢复 / 合同激活用补偿任务触发，而不是直接调用**：这些入口自己持有合同锁并在事务中保存；门禁需要外部查询，必须在其提交后、锁外执行。补偿任务在同一事务登记、提交后执行、失败重试，是项目既定的"提交后推进"方式；Job 只是兜底。
- **只对 CLIENT 合同生效**：PARTNER 合同的开通项是 Partner 侧能力，不参与 Client 上线，创建即 `ENABLED`，不进门禁。

## 8. 数据模型

### 8.1 表结构变更（`mor_commercial.t_agreement_capability`）

| 列 | 变更 | 定义 |
|---|---|---|
| `status` | 值域与注释改写 | 合同成员资格：`ACTIVE`（在合同内，定价须覆盖）/ `DISABLED`（运营关闭）。原 ENABLED / SUSPENDED 迁移为 ACTIVE |
| `execution_status` | **新增** `VARCHAR(32) NULL` | 执行开关：`PENDING_PREREQUISITES` / `ENABLED` / `SUSPENDED_BY_PREREQUISITE` / `SUSPENDED_MANUALLY`；`status=DISABLED` 时为 NULL，`status=ACTIVE` 时必须非空（应用校验，不建 CHECK） |
| 索引 `idx_agreement_capability_lookup` | 改为 `(entity_no, capability_code, execution_status)` | Checkout / 按 OE 查找用执行状态 |
| 索引 `idx_agreement_capability_execution` | **新增** `(execution_status, agreement_id)` | 重查 Job 扫 PENDING / SUSPENDED_BY_PREREQUISITE |

不加检查结果、检查时间、生效时间列（S-04）。

### 8.2 存量迁移（同一脚本，幂等）

| 迁移前 `status` | 迁移后 `status` | 迁移后 `execution_status` |
|---|---|---|
| ENABLED | ACTIVE | ENABLED |
| SUSPENDED | ACTIVE | SUSPENDED_MANUALLY |
| DISABLED | DISABLED | NULL |

PARTNER 合同的开通项按同一规则迁移（全为 ENABLED → ACTIVE + ENABLED）。迁移不改变任何开通项的实际可用性（Q-8 不回溯）。

### 8.3 SQL 文件

| 文件 | 性质 |
|---|---|
| `paytech/commercial/docs/database/sql/changes/2026-10-28-capability-execution-status.sql` | 执行文件：ALTER ADD 列 → UPDATE 迁移 → 改 `status` 注释 → 重建两条索引；可重跑（ADD 列前检查 information_schema；UPDATE 只改旧值） |
| 同目录 `2026-10-28-capability-execution-status-rollback.sql` | 回滚：反向 UPDATE（ACTIVE+ENABLED→ENABLED，ACTIVE+SUSPENDED_*→SUSPENDED，ACTIVE+PENDING→**SUSPENDED**（回滚后不能凭空开通），DISABLED→DISABLED）→ 恢复索引 → DROP 列 |
| `commercial-schema.sql`、`commercial.pdma.json` | 同步完整定义（PDManer 直接编辑） |
| 需求目录 `db/ddl.sql` | 注释索引，指向上面执行文件，不含可执行语句 |

**部署顺序约束**：新代码读到旧值（ENABLED / SUSPENDED）会 Fail-Closed；旧代码读到新值（ACTIVE）也会 Fail-Closed。因此 SQL 与新镜像必须相邻执行（停 commercial → SQL → 起新镜像），回滚同理（停 → 回滚 SQL → 旧镜像）。commercial 单实例，停机窗口为秒级。

## 9. 状态规则

### 9.1 成员资格 `status`

| 操作 | 入口 | 规则 |
|---|---|---|
| 新增 | `POST /{agreementId}/capabilities`（已有） | CLIENT 合同：`ACTIVE` + `PENDING_PREREQUISITES`，同事务登记前置检查任务；PARTNER 合同：`ACTIVE` + `ENABLED` |
| 关闭 | `PUT /{agreementId}/capabilities/{id}`，`status=DISABLED` | `execution_status` 置 NULL |
| 重新纳入 | 同上，`status=ACTIVE`（原 DISABLED） | CLIENT：`execution_status=PENDING_PREREQUISITES` 并登记检查任务；PARTNER：`ENABLED` |

PUT 的 `status` 值域改为 `ACTIVE | DISABLED`，不再接受执行状态；原"手动置 ENABLED"入口取消（S-01）。

### 9.2 执行状态 `execution_status`（仅 CLIENT 合同）

| 从 | 到 | 谁 / 何时 |
|---|---|---|
| PENDING_PREREQUISITES | ENABLED | 门禁：①②④ 全部 SATISFIED |
| ENABLED | SUSPENDED_BY_PREREQUISITE | 门禁：任一项明确 UNSATISFIED |
| SUSPENDED_BY_PREREQUISITE | ENABLED | 门禁：全部恢复 SATISFIED |
| PENDING / ENABLED / SUSPENDED_BY_PREREQUISITE | SUSPENDED_MANUALLY | 运营"暂停"（`POST …/suspension`） |
| SUSPENDED_MANUALLY | PENDING_PREREQUISITES | 运营"恢复"（`POST …/resumption`），同事务登记检查任务 |

- 任一项为 **UNKNOWN**（查询失败 / 超时）时，该开通项本次**不迁移**（B3）。
- 门禁**从不**触碰 `SUSPENDED_MANUALLY`。
- `PENDING_PREREQUISITES` 在不满足时保持原状，不迁到 SUSPENDED。

### 9.3 三项前置的判定（每个 ACTIVE 成员开通项）

| 项 | 适用 | SATISFIED 条件 | UNSATISFIED 原因码 |
|---|---|---|---|
| ① 资格 | 全部 | compliance 对 `client(clientId)` 与 `operatingEntity(entityNo)` 均 `eligible=true` | `CLIENT_NOT_QUALIFIED`、`OPERATING_ENTITY_NOT_QUALIFIED` |
| ② 合同与价格 | 全部 | 合同 `ACTIVE` 且 now 在合同区间内；开通项区间覆盖 now；FEATURE 类到此即满足；PAYMENT_METHOD / PAYOUT 类还要求该合同 Client 方案有 `ACTIVE` 价格版本，且该版本对本开通项通过"覆盖完整"校验（复用 `TermVersionActivationValidator.checkCapabilityCoverage`，抽为包外可用的纯方法） | `AGREEMENT_NOT_ACTIVE`、`CAPABILITY_NOT_EFFECTIVE`、`TERM_VERSION_NOT_ACTIVE`、`PRICE_NOT_COVERED` |
| ④ 渠道身份 | PAYMENT_METHOD 类 | 本需求不接入，结果 `SKIPPED`（原因 `CHANNEL_IDENTITY_NOT_CONNECTED`），按满足处理；REQ-02 交付时把本项改为真实查询并全量重查 | — |

PAYOUT 类的 ④ 在 payout 交付前同样 `SKIPPED`（`NOT_APPLICABLE`）。资格查询失败时 ① 为 `UNKNOWN`（原因 `CHECK_UNAVAILABLE`）。

**说明**：REQ-01 不实现 ④ 的客户端与开关——没有被调用的接口时写客户端属于无消费者代码（DEVELOPMENT_CONVENTIONS 第 5 节必要性门禁）。阶段 1 的 Q1 "开关关闭视为满足"在实现上就是"本项固定 SKIPPED"，REQ-02 再引入真实查询。

## 10. 接口

全部在 `AgreementApi`（`/agreements/v1`），沿用 `ACTOR_HEADER` 与合同 BCR Root 的 `expectedChangeVersion` CAS。

### 10.1 变更的已有接口

| 接口 | 变更 |
|---|---|
| `POST /{agreementId}/capabilities` | 响应 `status` 为 `ACTIVE`，新增 `executionStatus` |
| `PUT /{agreementId}/capabilities/{id}` | Body `status` 值域 `ACTIVE\|DISABLED`；响应同上 |
| `GET /{agreementId}`（合同详情） | `AgreementCapabilityDetail` 的 `status` 为成员资格，新增 `executionStatus` |

### 10.2 新增接口

| 方法与路径 | 用途 | Body | 响应 | 错误 |
|---|---|---|---|---|
| `POST /{agreementId}/capabilities/{id}/suspension` | 运营暂停 | `expectedChangeVersion`（必填）、`reason`（必填，≤ 200 字） | `AgreementCapabilityResponseBody` | 合同已结束 / 开通项 DISABLED / 已是 SUSPENDED_MANUALLY → 400；PARTNER 合同 → 400；版本不符 → 409 |
| `POST /{agreementId}/capabilities/{id}/resumption` | 运营恢复（回到 PENDING 并检查） | `expectedChangeVersion` | `AgreementCapabilityResponseBody`（`executionStatus=PENDING_PREREQUISITES`） | 非 SUSPENDED_MANUALLY → 400；版本不符 → 409 |
| `POST /{agreementId}/prerequisite-checks` | 立即核对整个合同的开通项前置，必要时迁移状态 | 无 | `CapabilityPrerequisiteCheckResponseBody` | 合同不存在 → 400；PARTNER 合同 → 400；资格服务不可用 → 200（对应项 UNKNOWN，不报错） |

`CapabilityPrerequisiteCheckResponseBody`：

```
changeVersion        合同 BCR Root 当前版本（核对后）
capabilities[]
  agreementCapabilityId, entityNo, capabilityCode, capabilityKind
  status             ACTIVE / DISABLED
  executionStatus    核对后的执行状态
  checks[]           每项一条（DISABLED 开通项为空数组）
    code             QUALIFICATION / AGREEMENT_TERM / CHANNEL_IDENTITY
    result           SATISFIED / UNSATISFIED / UNKNOWN / SKIPPED
    reasonCode       见 9.3；SATISFIED 时为空
    params           Map<String,String>：如 subjectType、entityNo、termVersionStatus
```

`prerequisite-checks` 是 POST：它可能迁移状态，不是纯查询。合同页展开"还差什么"与"检查前置"按钮调用同一接口（S-04）。

### 10.3 API 枚举

| 枚举 | 值 |
|---|---|
| `AgreementCapabilityStatus`（改） | `ACTIVE`、`DISABLED`（**Code 变化**：唯一外部消费方是 ops-bff / ops-web，同批发布） |
| `AgreementCapabilityExecutionStatus`（新） | `PENDING_PREREQUISITES`、`ENABLED`、`SUSPENDED_BY_PREREQUISITE`、`SUSPENDED_MANUALLY` |
| `CapabilityPrerequisiteCode`（新） | `QUALIFICATION`、`AGREEMENT_TERM`、`CHANNEL_IDENTITY` |
| `CapabilityPrerequisiteResult`（新） | `SATISFIED`、`UNSATISFIED`、`UNKNOWN`、`SKIPPED` |

原因码为开放字符串，集中在 `CapabilityPrerequisiteReasons` 常量类（第 4 节规则：前端按 `commercial.enum.CapabilityPrerequisiteReason.<CODE>` 取文案，未知码显示原值）。

## 11. 事件

### 11.1 发布：`AgreementCapabilityStatusChanged`（schema 1）

| 项 | 定义 |
|---|---|
| 交换机 | `commercial.events`（已有） |
| 聚合 | `AGREEMENT`，aggregateId = 合同 ID，aggregateVersion = 合同 BCR Root `changeVersion`（与 `AgreementStatusChanged` 同口径，跳号，`strictOrder=false`） |
| 触发 | 任一开通项**执行状态**变化的本地事务（门禁、暂停、恢复、新增、关闭 / 重新纳入）登记补偿任务 `AGREEMENT_CAPABILITY_STATUS_CHANGED`；Handler 提交后回读本版本的开通项 BCR 快照与上一快照重建 Payload |
| Payload | `agreementId, clientId, changeVersion, occurredAt, changes[{agreementCapabilityId, entityNo, capabilityCode, capabilityKind, fromExecutionStatus, toExecutionStatus, trigger(PREREQUISITE / MANUAL / MEMBERSHIP)}]` |
| 消费方 | 本期无（risk-decision 在 REQ-04 接入）；发布端 `unroutable-policy` 维持现有 `ACK_AND_WARN` |
| 目录 | `foundation/paytech-messaging/catalog/events.yaml` 新增条目；`compensation-event-catalog.yaml` 新增 2 个类型（见 11.3） |

**修订 REQ-00 契约 3.1**：载荷由"每个开通项一条事件"改为"每个合同版本一条事件、`changes[]` 列出本次全部变化"，原因是一次门禁核对可能同时迁移多个开通项，而事件身份按合同 Root 版本定义。

### 11.2 消费：`QualificationChanged`（compliance，schema 1）

| 项 | 定义 |
|---|---|
| 入口 | 新 `ext/mq/QualificationChangedListener`（commercial 第一个消费者），`@DomainEventHandler(eventType="QualificationChanged", strictOrder=false, transaction=HANDLER_MANAGED)`，信封校验同 client-master 样板 |
| 处理 | `roleType=CLIENT` → 该 Client 的 CLIENT 合同（`status ∈ {SIGNED, ACTIVE}`）；`OPERATING_ENTITY` → 有开通项在该 OE 上的 CLIENT 合同；`LOCAL_PARTNER` → 确认后忽略（S-07）。对每个合同调用 `CheckCapabilityPrerequisitesProcessor.process`（事务外，逐个） |
| 失败 | 合同锁冲突、资格服务不可用 → 原样抛出重投（不是业务拒绝）；信封非法 → 死信 |
| 拓扑 | `messaging.consumer.service: commercial`；订阅 `compliance.events`，分区队列 `commercial.compliance.p0..p3`（默认 4，Quorum、单活、delivery-limit 5、DLX）；`events.yaml` 中 `QualificationChanged.consumers` 加 `commercial` |
| 幂等 | 事件只触发重查；重复投递再查一次，结论以同步查询为准 |

### 11.3 补偿任务类型（`compensation-event-catalog.yaml`）

| 类型 | Handler | 作用 | 事件键 |
|---|---|---|---|
| `AGREEMENT_CAPABILITY_PREREQUISITE_CHECK` | `CapabilityPrerequisiteCheckHandler` | 提交后调用门禁 Processor；锁冲突 / 依赖不可用 → 可重试；合同不存在 → 终态 | `AGREEMENT:{agreementId}:CapabilityPrerequisiteCheck:{changeVersion}` |
| `AGREEMENT_CAPABILITY_STATUS_CHANGED` | `AgreementCapabilityStatusChangedHandler` | 提交后发布 11.1 | `AGREEMENT:{agreementId}:AgreementCapabilityStatusChanged:{changeVersion}` |

合同激活（`ActivateAgreementProcessor`）与价格版本激活（`ActivateCommercialTermVersionProcessor`）在各自现有事务中**追加**登记 `AGREEMENT_CAPABILITY_PREREQUISITE_CHECK`（仅 CLIENT 合同、且存在 PENDING / SUSPENDED_BY_PREREQUISITE 开通项时）。

## 12. 代码结构（commercial）

| 层 | 类 | 新 / 改 | 职责 |
|---|---|---|---|
| api | `AgreementApi` | 改 | 新增 3 个方法（10.2） |
| api | `request/SuspendAgreementCapabilityRequestBody`、`ResumeAgreementCapabilityRequestBody`；`response/CapabilityPrerequisiteCheckResponseBody`（含嵌套 class） | 新 | — |
| api | `enums/AgreementCapabilityStatus`（改）、`AgreementCapabilityExecutionStatus`、`CapabilityPrerequisiteCode`、`CapabilityPrerequisiteResult`（新） | — | — |
| api | `event/AgreementCapabilityStatusChangedPayload` | 新 | — |
| app | `AgreementController` + Converter | 改 | 3 个端点 |
| app | messaging 消费配置、XXL Job 注册 | 改 | 配置 |
| core · processor | `CheckCapabilityPrerequisitesProcessor` | 新 | 门禁唯一入口，锁 `agreement:{id}`（LOCK），见第 7 节五步 |
| core · processor | `SuspendAgreementCapabilityProcessor`、`ResumeAgreementCapabilityProcessor` | 新 | 继承 `AbstractAgreementWriteProcessor` |
| core · processor | `AddAgreementCapabilityProcessor`、`UpdateAgreementCapabilityProcessor`、`ActivateAgreementProcessor`、`ActivateCommercialTermVersionProcessor` | 改 | 初始执行状态；成员资格规则；追加登记检查任务 |
| core · domain | `CapabilityPrerequisiteContext` | 新 | 合同、开通项、ACTIVE 价格版本与候选、资格结果；纯方法 `decide(capability)` 返回逐项结果与目标执行状态 |
| core · domain | `CapabilityAcceptance`：`enabled()` → `member()`（status=ACTIVE）；`CheckoutSubscriptionContext.matches` 改看 `execution_status=ENABLED` | 改 | 解开循环依赖（F-04 / F-05 四处） |
| core · domain | `AgreementDetailView`、`BusinessChangeSnapshot`（开通项快照加 `executionStatus`）、`CheckoutCandidateContext` 日志 | 改 | — |
| core · validator | `TermVersionActivationValidator.checkCapabilityCoverage` 抽为可复用纯方法 | 改 | ② 复用 |
| core · service | `AgreementService.applyCapabilityExecutionChanges(CapabilityPrerequisiteContext)`；`suspendCapability` / `resumeCapability` | 新方法 | 短事务：CAS、BCR、Root、登记发布任务 |
| core · repository | `AgreementCapabilityRepository.updateExecutionStatus(capability, from, to, now)`、`findAgreementIdsByExecutionStatus(...)`、`findClientAgreementIds(clientId)`、`findClientAgreementIdsByEntityNo(entityNo)` | 新方法 | 条件 SQL + 行数检查 |
| core · handler | `CapabilityPrerequisiteCheckHandler`、`AgreementCapabilityStatusChangedHandler` | 新 | 11.3 |
| core · mq | `QualificationChangedListener` | 新 | 11.2 |
| core · job | `CapabilityPrerequisiteRecheckJobHandler` | 新 | 参数 `scope=PENDING`（每 5 分钟，扫 PENDING + SUSPENDED_BY_PREREQUISITE）/ `scope=ALL`（每日 03:00，含 ENABLED）；按 agreementId 分片、每批 200 |
| core · enums | `BusinessChangeReason` 加 `AGREEMENT_CAPABILITY_EXECUTION_CHANGED` | 改 | BCR 原因 |
| core · constant | `CapabilityPrerequisiteReasons` | 新 | 原因码常量 |

门禁写入的 BCR 快照 actor 为系统账号 `0`（沿用 `CommercialTermLoader` 系统命令写法），reason `AGREEMENT_CAPABILITY_EXECUTION_CHANGED`。

**已知副作用**：门禁迁移状态会推进合同 BCR Root 版本，运营若正打开该合同编辑，提交时会得到 409"请刷新"。这与同一合同上其他并发编辑的行为一致，可接受。

## 13. 运营控制台

### 13.1 ops-bff

| 项 | 变更 |
|---|---|
| `CommercialAgreementController` | 新增 3 个转发：`POST /commercial/agreements/{id}/capabilities/{cid}/suspension`、`…/resumption`、`POST /commercial/agreements/{id}/prerequisite-checks` |
| `CommercialAgreementModels` | 开通项加 `executionStatus`；检查结果模型 |

### 13.2 ops-web（`src/commercial/AgreementDetail.tsx`、`ProductCapabilitySections.tsx`、`capabilityConditions.ts`、`AgreementModals.tsx`）

| 位置 | 变更 |
|---|---|
| 开通项卡片 | 两枚徽标：成员资格（已纳入 / 已关闭）+ 执行状态（等前置 / 已开通 / 前置失效暂停 / 手动暂停） |
| 卡片展开"还差什么" | 展开时调 `prerequisite-checks`，列出逐项结果：✔ 满足 / ✘ 原因（ICU 文案 + 参数）/ ? 检查失败 / – 未接入；结果与执行状态在行内刷新 |
| 操作 | 「检查前置」（等前置 / 前置失效暂停时）、「暂停」（填原因）、「恢复」（手动暂停时） |
| 编辑弹窗 | 状态选项只剩"纳入 / 关闭"；移除"开通" |
| `benchmark.ts` 等按 `status === 'ENABLED'` 排序 / 标记处 | 改看 `executionStatus` |
| i18n | `commercial` 命名空间新增：执行状态枚举 4、前置项 3、结果 4、原因码 8、按钮 / 提示约 8；en + zh-CN 种子；Platform 语言包新版本 |

视觉要求：沿用现有开通项卡片样式；展开区为卡片内列表，不新开抽屉；1440 / 390 宽度可用；en / zh-CN 浏览器走查（记忆：API 验收不能代替浏览器验收）。

### 13.3 页面原型（2026-10-28 补画并经用户确认）

> 阶段 3 初稿只有文字描述、未出原型即进入研发，属于流程遗漏；ops-web 已写代码按下列原型返工。

原型在 `operations-console-web/docs/operations-console.pen`（y=99800 一行）：

| 画板 | 内容 |
|---|---|
| 商务 · 14 合同详情 · 开通项执行状态（等前置 / 还差什么） | 表格"执行状态"列四种徽标（前置失效暂停下方小字原因）；行首折叠，展开"还差什么"逐项（资格 / 合同与价格 / 渠道身份）+ 跳转链接；产品标题栏数量摘要、"上次检查"、「检查前置」；"下一步"卡片改为"开通项生效"并列出卡住原因；行操作 编辑 + 暂停 / 恢复 |
| 商务 · 15 暂停开通项（弹窗） | 摘要、"暂停后立即生效"三条影响、必填原因（≤ 200 字，写入变更历史）、红色"暂停" |
| 商务 · 16 恢复开通项（弹窗） | 摘要（含暂停人与时间）、暂停原因、"恢复后不会直接开通"状态流、"恢复并检查" |
| 商务 · 17 编辑开通项（弹窗） | 执行状态标"系统维护"；"合同内状态"只有 纳入 / 关闭；说明手动开通路径已取消 |

新增开通项弹窗（11c）按钮由"开通"改为"添加"，提示"添加后进入等前置，系统自动检查"（改动小，未单独出图）。

**权限（用户 2026-10-28 确认）**：有合同查看权限即可展开"还差什么"并点「检查前置」（`prerequisite-checks` 只做系统本会自动做的迁移，不含人工判断）；编辑 / 暂停 / 恢复 / 新增需要维护权限，只读用户不显示这些按钮。ops-bff 转发与 Gateway 路由按此注册。

## 14. Platform 注册与 data-query

| 项 | 内容 |
|---|---|
| Gateway 路由 | ops-bff 3 条新 Route；权限沿用现有"合同开通项维护"权限（阶段 6 核对具体编码与注册块编号） |
| data-query | `mor_commercial` 只读账号（DBA）、数据源登记、3 条模板（下表）——均为发布动作（S-08） |

**模板约定（修订 REQ-00 契约 3.3，供 REQ-05 统一解释）**：查询返回**单行**；列 `satisfied`（0/1）；`out_<name>` 列写入同名流程变量；`missing` 列为逗号分隔的原因码（可空）。无行视为未满足。

| 编码 | 参数 | SQL 要点 |
|---|---|---|
| `GOLIVE_AGREEMENT_DRAFTED` | `clientId` | 该 Client 最新一份 `agreement_type='CLIENT'` 且 `status IN ('PENDING_SIGNATURE','SIGNED','ACTIVE')` 的合同，且其方案存在 `status IN ('APPROVED','CLIENT_SIGNED','SCHEDULED','ACTIVE')` 的版本；输出 `out_agreement_id`、`out_business_mode`、`out_entity_no`、`out_capability_codes`（GROUP_CONCAT 成员开通项）；`missing`：`AGREEMENT_NOT_DRAFTED` / `TERM_VERSION_NOT_APPROVED` |
| `GOLIVE_AGREEMENT_ACTIVE` | `agreementId` | 合同 `ACTIVE` 且方案有 `ACTIVE` 版本；`missing`：`AGREEMENT_NOT_ACTIVE` / `TERM_VERSION_NOT_ACTIVE` |
| `GOLIVE_COLLECTION_CAPABILITIES_ENABLED` | `agreementId` | 该合同 `status='ACTIVE'` 的 PAYMENT_METHOD 类开通项全部 `execution_status='ENABLED'`（至少一条）；输出 `out_pending_capabilities`（未 ENABLED 的 capability_code 列表）；`missing`：`CAPABILITY_NOT_ENABLED` |

模板正文写入 `requirements/20261028/commercial-开通项前置门禁/db/data-query-templates.sql`（文本，非迁移脚本），阶段 4 在本机 data-query 上逐条执行验证。

## 15. 测试与验证计划

### 15.1 auto（commercial，本地 MySQL / Redis / RabbitMQ；compliance 用已有测试 HTTP 端点 + DTO Mocker）

| 入口 | 场景 |
|---|---|
| HTTP 新增开通项 | CLIENT → ACTIVE + PENDING，检查任务已登记；PARTNER → ACTIVE + ENABLED，无检查任务 |
| HTTP `prerequisite-checks` | 全满足 → ENABLED + 发布任务 + BCR；资格不合格（Client / OE）→ 保持 PENDING，原因码正确；合同未 ACTIVE；价格版本未 ACTIVE；价格未覆盖；ENABLED 后资格撤销 → SUSPENDED_BY_PREREQUISITE；恢复 → ENABLED；资格服务 503 → 状态不变、结果 UNKNOWN；SUSPENDED_MANUALLY 不被触碰；PARTNER 合同 → 400 |
| HTTP 暂停 / 恢复 | 正常；重复暂停 400；版本不符 409；恢复后 PENDING 且登记检查任务 |
| HTTP PUT 成员资格 | DISABLED → execution NULL；ACTIVE 重新纳入 → PENDING；body 传 ENABLED → 400 |
| **价格激活回归（循环依赖）** | PENDING 开通项所在合同的 Client 价格版本可以激活；激活后检查任务登记，核对后开通项 ENABLED；已暂停开通项也被要求价格覆盖（新行为） |
| Checkout 解析 | 只输出 `execution_status=ENABLED` 的开通项；PENDING / 两种暂停均不输出 |
| 补偿 Handler | 检查 Handler：成功、锁冲突可重试、合同不存在终态；发布 Handler：Payload `changes[]` 正确、Broker 不可用可重试 |
| MQ 消费者 | 本地 Broker 发 `QualificationChanged`：CLIENT REVOKED → 相关合同 ENABLED 项暂停；OE 维度；LOCAL_PARTNER 忽略；信封非法进死信 |
| Job | `scope=PENDING` 只处理 PENDING / SUSPENDED_BY_PREREQUISITE；分片不重叠；`scope=ALL` 含 ENABLED |
| 存量迁移 | 在本地库对迁移前快照执行迁移脚本 → 校验映射；再执行回滚脚本 → 恢复（含 PENDING → SUSPENDED） |

现有 commercial auto（149 例）全量回归；ops-bff、ops-web 相关测试与 i18n 源码守卫。

### 15.2 本机联调（阶段 5）

commercial + ops-bff + ops-web + compliance 本机运行：浏览器 en / zh-CN、1440 / 390 走完"新增开通项 → 看到等前置与原因 → 激活价格 → 检查前置 → 已开通 → 暂停 → 恢复"；data-query 三条模板在本机执行。

### 15.3 mor-test（阶段 6—7，另行授权）

停 commercial → 迁移 SQL → 新 commercial / ops-bff / ops-web 镜像 → 注册块 → 语言包 → XXL 任务 → MQ 拓扑核对 → 验收（存量演示合同开通项全部仍 ENABLED；新开通项走门禁）→ data-query 只读账号与模板登记（单独授权）。

## 16. 规模与顺序

| 项 | 数量 |
|---|---|
| commercial 新类 | 约 14（3 Processor、1 Context、2 Handler、1 Listener、1 Job、4 枚举 / 常量、2 Body + 1 Payload） |
| commercial 改类 | 约 14（3 处定价口径、Checkout、4 个 Processor、Service、Repository、Mapper XML、快照、详情 View、Controller） |
| SQL | 1 表：加 1 列、改 1 列值域、2 条索引；迁移 + 回滚 |
| ops-bff / ops-web | 3 转发 + 模型；1 组件改造 + i18n |
| 注册 / 配置 | 3 Route、XXL 2 任务、MQ 消费拓扑、事件目录 2 处 |

规模仍为 **M**（偏大）。研发顺序：数据模型与枚举 → 定价 / Checkout 口径（先跑回归）→ 门禁 Processor 与 HTTP → 暂停 / 恢复 → 补偿 Handler → 消费者 → Job → BFF / Web。

**确认点**：用户确认第 7—16 节（尤其 8.2 迁移映射与部署顺序、9.2 状态迁移表、10 接口、11.1 事件载荷修订、14 模板约定）后进入阶段 4 研发。
