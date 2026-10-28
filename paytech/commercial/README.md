# commercial

商务服务：合同、合同参与方与签署文件、产品目录（产品 / 可开通项 / 计费事件）、合同订阅与开通项、商业条款计划 / 版本 / 价格 / 组件 / 阶梯、定价画像，以及两段式 Checkout 解析的编排（[design/06](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/06-cct-two-stage-resolution.md)）。由原 `client-contract-service` 拆出（第 04 步）。

- spring.application.name：`commercial-service`
- 端口：18108，管理端口 19108
- 数据库：`mor_commercial`（20 张表：17 张商务表、本库 BCR 两表、`t_compensation_event`）
- 包根：`com.paytech.commercial`
- 无 MQ、无补偿 Handler、无 Redis：Enforcer 禁止 AMQP / XXL / compensation-event / Redis 依赖

## 模块

| 模块 | 内容 |
|---|---|
| `paytech-commercial-api` | `CheckoutContextApi`（路径不变）、`CheckoutErrorCode`、`AgreementPartyQueryApi`、运营写入 `AgreementApi`（`/agreements/v1`）与 `PricingProfileApi`（`/pricing-profiles/v1`）；运营只读 `AgreementQueryApi`、`CommercialTermQueryApi`、`ProductCatalogApi`、`BusinessChangeQueryApi`；六层定价闭合枚举（`api/enums`）与开放目录稳定代码 `CommercialCodes`（`api/constant`） |
| `paytech-commercial-core` | 参数化商务查询、两段式解析 `CheckoutResolutionProcessor`、client-master 出站端口、Risk 资格端口（仍为 503 占位，risk-decision 尚无对应合同）；全部 17 张商务表的完整行 DataObject 与写入映射（Mapper XML + Repository）；合同 / 签署 / 订阅 / 开通项 / 画像的写入 Processor、`AgreementService`、`PricingProfileService` 与 BCR 写入协作者 `BusinessChangeHistoryWriter`；`CheckoutRecords` 保留为 Checkout 只读投影 |
| `paytech-commercial-app` | 启动类、Controller、DataSource / 事务 / MyBatis、client-master HTTP 客户端、`application.yml` |
| `paytech-commercial-test` | 全部 `auto` 测试，见[测试说明](paytech-commercial-test/README.md) |

## 两段式解析

`POST /checkout-context/v1/resolutions` 由 commercial 提供，调用方须为 `acquiring-service`（第 03 步由 `commerce-service` 改名）：

1. 调 client-master 候选接口取得有序候选、查询范围与根版本。
2. 本库 REPEATABLE READ 只读快照按 `clientId`、`entityNos`、`localPartnerIds` 显式参数读取商务事实（不再子查询主档表），同一快照读取范围内合同的订阅、开通项、约束与引用的可开通项目录；事务结束后经 `party-profiles` 读取签署资料；逐候选惰性解析，fall-through 语义与原实现一致。
3. 六层模型的逐候选门槛（六层定价需求 technique §2.3、§2.5）：资金模式 `FEES_ONLY` 本期落空；合同必须订阅 `ACQUIRING`；请求带 `paymentMethodCode` 时，同合同同 OE 下至少一个 ENABLED、区间含商业时点、`CURRENCY` / `CONSUMER_COUNTRY` 约束满足的开通项（零个落空；带卡品牌维度的目录项按其 `payment_method_code` 匹配，命中项全部返回）；条款版本在 SCHEDULED / ACTIVE / SUPERSEDED 中按区间选唯一一条，不看 `active_slot`。落空原因写 INFO 日志。
4. 事务外调 Risk 资格端口（risk-decision 尚无 Client / 候选资格合同，固定 503 `OWNER_UNAVAILABLE`）。
5. 调 client-master `verify` 并重读本库合同根比对，任一变化返回 409 `CCT_CONTEXT_CHANGED`。
6. 响应在原 CCT 结构上只做加字段：`clientTerms` 增加 `businessMode`、`fundMode`、`pricingAuthority`、`referralPartnerId`、`agreementProducts`、`capabilities[]`（含维度），顶层增加 `derived`（MOR：卖方 OE Party、税务 `OPERATING_ENTITY`；PSP：卖方 Client Party、税务 `CLIENT`；资金账簿均为 `OPERATING_ENTITY`）。client-master 或 verify 不可用一律 503 `OWNER_UNAVAILABLE`；两次调用沿用同一 Request ID 并写入解析日志。

接口分层与原解析设计见 [docs/requirements/20260921-checkout-context-resolution/design.md](docs/requirements/20260921-checkout-context-resolution/design.md)。

## 运营写入（六层定价 B 线）

`AgreementApi` 与 `PricingProfileApi` 是 technique.md §2.2 的合同 / 订阅 / 开通项 / 画像 13 个接口，调用方 operations-console-bff，操作者经 `X-Gateway-Actor-Subject` 透传。每个写入一个 Processor（Foundation `AbstractLockProcessor`，LOCK、租约 -1，Key `agreement:{agreementNo|id}`、`pricing-profile:{code|id}`）；Service 在一个本地事务内写业务表、BCR（Root `current_version` 为 CAS 令牌，请求携带 `expectedChangeVersion`；受审计 Child 为 `AGREEMENT_PRODUCT`、`AGREEMENT_CAPABILITY`；快照为 `_meta + data` 信封与 RFC 8785 完整性哈希）与 `compensation_event`。只有激活与终止登记 `AGREEMENT_STATUS_CHANGED`，提交后发布 `AgreementStatusChanged`。

## 开通项前置门禁（2026-10-28 REQ-01）

方案见 [technique.md §7–§16](../../requirements/20261028/commercial-开通项前置门禁/technique.md)。`t_agreement_capability.status` 只表示合同成员资格 `ACTIVE` / `DISABLED`（定价相关性与覆盖完整只看它）；新增 `execution_status`：`PENDING_PREREQUISITES` / `ENABLED` / `SUSPENDED_BY_PREREQUISITE` / `SUSPENDED_MANUALLY`（Checkout 解析只认 `ENABLED`）。存量迁移与回滚见 `docs/database/sql/changes/2026-10-28-capability-execution-status*.sql`，SQL 与新镜像必须相邻执行。

- 唯一入口 `CheckCapabilityPrerequisitesProcessor`（锁 `agreement:{id}`，LOCK）：事务外查 compliance 资格（Client + 各 OE），逐开通项判定 ①资格、②合同 ACTIVE 且相关方案 ACTIVE 版本完整覆盖（FEATURE 只看合同）、④渠道身份（本期固定 `SKIPPED`）；有迁移时一个短事务 Root CAS、条件迁移、系统身份 0 快照（`AGREEMENT_CAPABILITY_EXECUTION_CHANGED`）并登记 `AGREEMENT_CAPABILITY_STATUS_CHANGED`。UNKNOWN 不迁移，`SUSPENDED_MANUALLY` 不触碰，PARTNER 合同不进门禁（400）。
- 触发：`POST /agreements/v1/{id}/prerequisite-checks`（运营）；新增 / 恢复 / 重新纳入 / 合同激活 / Client 价格版本激活在原事务登记 `AGREEMENT_CAPABILITY_PREREQUISITE_CHECK`，提交后由 `CapabilityPrerequisiteCheckHandler` 调用门禁；消费 compliance `QualificationChanged`（`QualificationChangedListener`，分区队列 `commercial.compliance.p0..p3`，`messaging.consumer.service=commercial`）；XXL-Job `capabilityPrerequisiteRecheck`（参数空 = 等前置 / 前置失效暂停，建议每 5 分钟；`scope=ALL` 含已开通，建议每日 03:00；按合同 ID 分片）。
- 运营：`POST .../capabilities/{id}/suspension`（→ `SUSPENDED_MANUALLY`，带原因）、`POST .../capabilities/{id}/resumption`（→ 等前置并核对）；`PUT .../capabilities/{id}` 的 `status` 只接受 `ACTIVE` / `DISABLED`。
- 事件 `AgreementCapabilityStatusChanged`（schema 1，聚合 `AGREEMENT`、版本为合同 Root 版本）：一个合同版本一条，`changes[]` 按本版本与上一版本的开通项快照重建，触发来源 `PREREQUISITE` / `MANUAL` / `MEMBERSHIP`。

## 商业条款、价格命中与费率卡（2026-10-19 六层定价模型 C 线）

接口合同见 [technique.md §2.2–§2.8](../../requirements/20261019/commercial-六层产品定价模型/technique.md)；写入 Processor 继承 `AbstractLockProcessor`（锁 `term-plan:{planId}`，创建方案锁 `agreement:{agreementId}`，LOCK），每次写入携带方案 BCR 根修订 `expectedChangeVersion` 做 CAS（冲突 409）。BCR 与 B 线共用 `BusinessChangeHistoryWriter`：方案为 `COMMERCIAL_TERM_PLAN` Root，版本为受审计 Child `COMMERCIAL_TERM_VERSION`，Maker / Checker 记在版本快照 `data`，定时激活的快照 `triggerType=SYSTEM`。操作者与合同写入一致取 `X-Gateway-Actor-Subject` 请求头（缺失或非规范十进制 400），请求体不含操作人；定时激活作业以系统操作者 0 执行。

| 接口 | 作用 |
|---|---|
| `POST /commercial-terms/v1/plans` | 创建方案（DRAFT）；`(termType, planCode)` 同内容重放 200、改内容 409 |
| `POST /commercial-terms/v1/plans/{planId}/versions` | 创建 DRAFT 版本，可 `copyFromVersionId` |
| `PUT /commercial-terms/v1/versions/{versionId}/prices` | DRAFT 价格集合整体替换 |
| `POST .../versions/{versionId}/submit` · `approve` · `reject` | 本地 Maker-Checker：提交记 Maker，批准 / 驳回 Checker ≠ Maker（400） |
| `POST .../versions/{versionId}/schedule` · `activate` | 排期执行激活校验 1–5；激活切换 ACTIVE / SUPERSEDED 并登记 `CommercialTermVersionActivated` |
| `GET /commercial-terms/v1/versions/{versionId}` | 版本详情（含 `changeVersion`） |
| `GET /commercial-term-versions/v1/{versionId}/prices` | 固定版本价格全集（stage3 新增 5） |
| `POST /commercial-prices/v1/resolutions` · `GET /commercial-prices/v1/{priceId}` | 价格命中与回读；错误码 `CommercialPriceErrorCode` |
| `GET /rate-cards/v1` | Client 费率卡结构化数据 |

定时激活作业 `commercialTermVersionActivation`（XXL-Job，测试中执行器关闭）以系统命令调用与 `activate` 接口相同的用例。

## 运营控制台只读接口（2026-10-20 第二切片 H 线）

接口合同见 [technique.md §2](../../requirements/20261020/commercial-运营控制台完善/technique.md)；调用方 operations-console-bff。全部只读，不加锁、不开事务；列表复用公共分页 `page`（默认 1）/ `size`（默认 10，只允许 10、20、50、100、200），响应 `{items, page, size, total}`，ID 为十进制字符串、时间为 UTC ISO 8601，未知代码或非规范 ID 400 `INVALID_REQUEST`。

| 接口 | 查询参数 | 说明 |
|---|---|---|
| `GET /agreements/v1` | `clientId`、`localPartnerId`、`agreementType`、`status`（可重复）、`entityNo`（含 PARTNER 覆盖 OE）、`agreementNo`（字面前缀）、`page`、`size` | `AgreementQueryApi`；按创建时间倒序，每行带合同 BCR Root `changeVersion` |
| `GET /commercial-terms/v1/plans` | `agreementId`、`clientId`、`localPartnerId`、`termType`、`status`、`page`、`size` | `CommercialTermQueryApi`；按创建时间倒序，带方案根版本与当前 ACTIVE / SCHEDULED 版本 |
| `GET /commercial-terms/v1/versions` | `planId`、`agreementId`、`status`（可重复）、`effectiveFrom` / `effectiveTo`（窗口相交）、`page`、`size` | 只筛 `PENDING_APPROVAL` 按提交时刻升序，只筛 `SCHEDULED` 按生效起点升序，其他按方案、版本号倒序；带 Maker / Checker / 提交时刻（由版本快照推导）、价格条数、方案根版本 |
| `GET /products/v1` · `/capabilities` · `/fee-events` | `status`；`productCode`、`status`；`productCode`、`termType` | `ProductCatalogApi`；目录不分页，按 ID 升序 |
| `GET /business-changes/v1` | `aggregateRootType`（AGREEMENT、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE）、`aggregateRootId`（必填）、`page`、`size` | `BusinessChangeQueryApi`；Root 与受审计 Child 快照按版本倒序（同版本 Root 在前），`meta` 只含 triggerType / triggerId / reasonCode，`data` 为白名单审计视图 |

## 数据库与 PDManer

- 模型：[commercial.pdma.json](docs/database/commercial.pdma.json)；Schema：[commercial-schema.sql](docs/database/sql/commercial-schema.sql)；初始化 DML（产品目录）：[commercial-initial-data.sql](docs/database/sql/commercial-initial-data.sql)；物理决定：[COMMERCIAL_PHYSICAL_DECISIONS.md](docs/database/COMMERCIAL_PHYSICAL_DECISIONS.md)。
- 本机测试库增量：[local/2026-10-19-round3-local.sql](docs/database/sql/local/2026-10-19-round3-local.sql)（只写不执行，由主会话审阅后人工执行；执行状态以 `agent-docs/CURRENT_IMPLEMENTATION.md` 为准）。
- 生成器：`scripts/pdmaner/generate-commercial-model.mjs`；测试库 DDL：`paytech-commercial-test/src/test/resources/init/ddl.sql`。

## 构建与验证

```bash
cd paytech
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -B verify -pl commercial/paytech-commercial-test -am
```

并行任务使用隔离库时设置 `COMMERCIAL_TEST_DB_URL`（如 `jdbc:mysql://127.0.0.1:13306/mor_commercial_linec?...`）；清理器只接受 `mor_commercial` 或 `mor_commercial_<后缀>`。不要用 `SPRING_DATASOURCE_URL`，它会改写同一 Reactor 内 Foundation 模块的测试库。

只需要本机 `local-environment` 的 MySQL（`mor_commercial` 已按 `init/ddl.sql` 建好）。
