# commercial 六层产品定价模型 技术方案

> 状态：阶段 1、2 已确认（2026-10-19）；阶段 3 数据设计已落模、接口设计评审稿已交付（第 2 节），待确认。正文以 [design.md](../../../design-discuss/commercial/design.md)、[database.md](../../../design-discuss/commercial/database.md) 为准，本文只做索引与本需求特有的实施安排。

## 1. 现状差异与影响（阶段 2）

- 现状、差距与偏差：design.md 第 2、9 节。
- 受影响系统：commercial（主）；acquiring（解析响应与 Order Context 冻结字段）；billing（X3 接口消费、`balance_available_time` 来源）；risk-decision（资格门禁）；client-master（`partner_role`、推荐关系，另行登记）；client-console-bff（费率页，另行登记）；Platform（实例已启用模块集合，另行登记）。

## 2. 方案、接口与数据设计（阶段 3，2026-10-19 评审稿）

数据设计见 database.md 第 2–8 节（已落模）。本节定义接口合同；路径沿用商务现有风格 `/<资源>/v1/...`，所有 DTO 为 `*RequestBody` / `*ResponseBody` 普通 class，ID 为十进制字符串，金额为主单位十进制字符串并携带 `currency`，时间为带 offset 的 ISO 8601。错误按公共 Problem：业务检查 400 `BUSINESS_CHECK_FAILED` + 中文 detail，并发 / 版本冲突 409，依赖不可用 503；只有 `commercial-prices` 与既有 Checkout 解析有专有错误码。

### 2.1 审批机制的现实约束

`paytech/workflow-approval` 目前只有 README，没有可调用的引擎。本期沿用 Finance 已验证的本地 Maker-Checker：提交人与审批人必须不同（`maker_id ≠ checker_id`），审批只把状态推进到 APPROVED 并记录"人已同意"，激活时重新做全部校验（D13）。审批人、时间记入 BCR 快照，不新增审批表。引擎落地后只替换"谁来按下同意"，不改状态机。

### 2.2 写入 API（commercial-api，仅私网，调用方 operations-console-bff）

| 资源 | 方法与路径 | 作用 | 业务唯一身份 / 重复语义 | 锁 |
|---|---|---|---|---|
| 合同 | `POST /agreements/v1` | 创建 CLIENT 或 PARTNER 合同（DRAFT），含 `businessMode`、`fundMode`、`pricingAuthority`、`referralPartnerId`、参与方角色与资料版本 | `agreementNo` 唯一；重复提交同号 → 返回原合同（内容一致）或 409（内容不同） | `agreement:{agreementNo}` |
| 合同 | `GET /agreements/v1/{agreementId}` | 合同详情：参与方、文件、覆盖 OE、订阅产品、开通项 | — | — |
| 合同 | `POST /agreements/v1/{agreementId}/submit-for-signature` | DRAFT → PENDING_SIGNATURE；校验 SIGNING_DOCUMENT 恰一份、PARTNER 至少一个 OE | 状态条件更新 | `agreement:{id}` |
| 合同 | `POST /agreements/v1/{agreementId}/parties/{partyId}/acceptance` | 登记同 Party 全部角色的签署结果（E_SIGNATURE / CLICK_ACCEPTANCE / MANUAL_SIGNATURE + 证据文件）；全部 Party 签完 → SIGNED | `acceptanceReference` 全局唯一；同引用重放返回原结果 | `agreement:{id}` |
| 合同 | `POST /agreements/v1/{agreementId}/activate` | SIGNED → ACTIVE，写 `activated_time`；提交后发布 `AgreementStatusChanged` | 状态条件更新 | `agreement:{id}` |
| 合同 | `POST /agreements/v1/{agreementId}/terminate` | ACTIVE → TERMINATED（`effectiveTo`、TERMINATION_NOTICE 文件）；提交后发布事件 | 状态条件更新 | `agreement:{id}` |
| 订阅 | `PUT /agreements/v1/{agreementId}/products` | 整体替换订阅产品集合；校验产品 ACTIVE、`businessModeScope` 含合同模式、基座产品不写 | 幂等：集合等价 → 无变化 | `agreement:{id}` |
| 开通项 | `POST /agreements/v1/{agreementId}/capabilities` | 新增开通项：`entityNo`、`capabilityCode`、生效区间、保证金两字段、约束行（CURRENCY / CONSUMER_COUNTRY / REQUIRE_3DS） | uk(agreement, entityNo, capabilityCode)；重复 → 409 | `agreement:{id}` |
| 开通项 | `PUT /agreements/v1/{agreementId}/capabilities/{agreementCapabilityId}` | 改状态（ENABLED / SUSPENDED / DISABLED）、区间、保证金、整体替换约束行 | 版本 CAS（BCR Root 版本） | `agreement:{id}` |
| 画像 | `POST /pricing-profiles/v1` | 创建 DRAFT 画像：`profileKind`、`paymentMethodCode`（仅 PAYMENT_METHOD）、条件行 | `pricingProfileCode` 唯一且不可改 | `pricing-profile:{code}` |
| 画像 | `PUT /pricing-profiles/v1/{pricingProfileId}` | DRAFT 内改名称、支付方式、整体替换条件行 | BCR Root 版本 CAS | `pricing-profile:{id}` |
| 画像 | `POST /pricing-profiles/v1/{pricingProfileId}/activate` / `/retire` | DRAFT → ACTIVE（冻结）；ACTIVE → RETIRED（阻止新引用） | 状态条件更新 | `pricing-profile:{id}` |
| 画像 | `GET /pricing-profiles/v1?kind=&status=` | 列表（运营选择用） | — | — |
| 条款 | `POST /commercial-terms/v1/plans` | 创建 Plan：`termType`（目录登记值）、`agreementId`、Owner（client / localPartner XOR）、`planCode` | uk(termType, planCode)、uk(agreement, termType) | `agreement:{id}` |
| 条款 | `POST /commercial-terms/v1/plans/{planId}/versions` | 创建 DRAFT 版本，可 `copyFromVersionId`（复制全部 Price / Component / Band） | uk(plan, versionNo) 服务端递增 | `term-plan:{id}` |
| 条款 | `PUT /commercial-terms/v1/versions/{versionId}/prices` | DRAFT 内整体替换价格集合：每条 Price = entityNo + currency + pricingProfileCode + priorityNo + 结算四字段 + 组件（含 Band） | BCR Root 版本 CAS | `term-plan:{id}` |
| 条款 | `POST /commercial-terms/v1/versions/{versionId}/submit` | DRAFT → PENDING_APPROVAL（maker） | 状态条件更新 | `term-plan:{id}` |
| 条款 | `POST /commercial-terms/v1/versions/{versionId}/approve` / `/reject` | checker ≠ maker；→ APPROVED / 回 DRAFT | 状态条件更新 | `term-plan:{id}` |
| 条款 | `POST /commercial-terms/v1/versions/{versionId}/schedule` | APPROVED → SCHEDULED，入参 `effectiveFrom`；执行 2.6 激活校验 | 状态条件更新 | `term-plan:{id}` |
| 条款 | `POST /commercial-terms/v1/versions/{versionId}/activate` | SCHEDULED → ACTIVE（`effectiveFrom` 已到），前一 ACTIVE → SUPERSEDED 并写其 `effectiveTo`；提交后发布 `CommercialTermVersionActivated`；也由作业 `commercialTermVersionActivation` 定时调用 | 状态条件更新 + `active_slot` 唯一键 | `term-plan:{id}` |
| 条款 | `GET /commercial-terms/v1/versions/{versionId}`、`GET /commercial-term-versions/v1/{versionId}/prices?entityNo=&currency=` | 版本详情、固定版本下价格全集（stage3 新增 5，≤1000 条） | — | — |

规则：全部写入 Processor 继承 `AbstractLockProcessor`（LOCK，租约 -1），锁 Key 如上；Controller / Converter 只做协议转换；一个具名 Context 贯穿请求（`AgreementContext`、`PricingProfileContext`、`CommercialTermVersionContext`）；本地事务内只写本库 + BCR + `compensation_event`，事务内不调外部服务；状态推进用条件更新并校验影响行数；BCR Root 版本作为 CAS，请求携带 `expectedChangeVersion`（9.1.1 CCT 例外）。写入不处理 `Idempotency-Key`。

### 2.3 版本解析规则（替代"只看 ACTIVE 槽"）

Checkout 解析与价格命中按 `contextAsOfTime` 在状态 ∈ {SCHEDULED, ACTIVE, SUPERSEDED} 且 `effective_from ≤ t < effective_to`（空 = 无穷）的版本中选唯一一条；DRAFT / PENDING_APPROVAL / APPROVED / EXPIRED 不参与。`active_slot` 与 ACTIVE / SUPERSEDED 标签只由激活作业维护，不是解析依据，因此作业迟到不影响正确性。同一 Plan 的已排期版本区间不得重叠（schedule 时校验）。

### 2.4 价格命中（stage3 合同的修订）

`POST /commercial-prices/v1/resolutions`：入参增加 `currency`（交易币种）；`paymentMethodProfileCode` 改名 `pricingProfileCode`；`paymentFacts` 维度码按 14 值目录。命中：币种精确 → 支付方式精确 → 条件等值 → 维度数多者先 → `priorityNo` 小者先。出参增加 `currency`、`pricingModel`、`feeTaxTreatment`，组件增加 `refundBehavior`、`bandBasis`；`settlement` 来自 Price。错误码不变：422 `PRICING_FACTS_INCOMPLETE` / `PRICE_NOT_FOUND` / `INVALID_PRICING_FACT`，409 `PRICE_AMBIGUOUS`。`GET /commercial-prices/v1/{priceId}` 不变。调用方 billing。

### 2.5 Checkout 解析响应扩展（路径不变）

`clientTerms` 增加 `businessMode`、`fundMode`、`pricingAuthority`、`referralPartnerId`、`agreementProducts[]`、`capability{agreementCapabilityId, capabilityCode, status, rollingReserveRate, rollingReserveDays}`；入参增加 `paymentMethodCode`、`currency`（可选；给出时解析必须命中 ENABLED 且约束满足的开通项，否则该候选落空）。新增 `derived{sellerPartyId, acquiringEntityNo, taxResponsibleParty, fundsBookOwner}` 四项派生：MOR → 卖方 = OE Party、税务责任 = OE、账簿 = OE；PSP → 卖方 = Client Party、税务责任 = CLIENT、账簿 = OE（资金经平台）；`fundMode=FEES_ONLY` 本期不接受。收单冻结以上字段到 Order Context（acquiring 配合改动）。

### 2.6 激活校验（schedule 与 activate 都执行）

1. 版本 APPROVED / SCHEDULED；合同 ACTIVE 且区间覆盖 `effectiveFrom`。
2. 每条 Price 引用的画像 ACTIVE、`entityNo` 被合同覆盖、`currency` 在该 OE 任一开通项的 CURRENCY 约束内。
3. 覆盖完整：合同下每个 ENABLED 开通项 × 其每个约束币种，至少命中一条 Price（按开通项的 `paymentMethodCode` 与 `dimension` 条件模拟命中）。
4. 组件：`currency` 等于 Price 币种；TIERED 必有 `bandBasis` 且 Band 连续无重叠、首档 0、末档上界空；本期 `bandBasis` 只接受 TRANSACTION_AMOUNT；`pricingModel` 只接受 BLENDED；RETROACTIVE 拒绝。
5. 同 Plan 已排期 / 生效区间不重叠。
6. 毛利校验（设计稿第 12 节）本期不做，留接口位。

### 2.7 费率卡读接口（供 client-console-bff）

`GET /rate-cards/v1?clientId=&asOf=&currency=`：按 Client 的 ACTIVE 合同返回结构化数据：订阅产品、开通项（含约束与保证金）、每开通项下按币种分块、按具体度排序的画像条件与各事件组件、结算四字段、`pricingModel` / `feeTaxTreatment`、当前与已排期版本（含 `effectiveFrom`）。不返回渲染文案、不返回分润与服务费。Partner 门户的分润视图本期不做。

### 2.8 事件

业务事件目录 `foundation/paytech-messaging/catalog/events.yaml` 登记两条，producer commercial，`strictOrder=false`，消费者 `[acquiring]`（新鲜门禁）：

- `AgreementStatusChanged`：aggregateType AGREEMENT，版本字段 `changeVersion`（BCR Root 版本）；载荷 `agreementId, agreementNo, agreementType, clientId, localPartnerId, entityNo, status, effectiveFrom, effectiveTo, changeVersion, occurredAt`。
- `CommercialTermVersionActivated`：aggregateType COMMERCIAL_TERM_PLAN，版本字段 `changeVersion`；载荷 `planId, termType, agreementId, versionId, versionNo, effectiveFrom, supersededVersionId, changeVersion, occurredAt`。

`paytech/commercial/compensation-event-catalog.yaml` 登记 `AGREEMENT_STATUS_CHANGED`、`COMMERCIAL_TERM_VERSION_ACTIVATED` 两个发布型 Handler；事件只带 ID 与版本，内容按 ID 回读。

### 2.9 Operations 控制台（BFF + 注册）

- operations-console-bff 新增 `/commercial-admin/v1/**`：合同（列表 / 详情 / 创建 / 签署登记 / 激活 / 终止 / 订阅 / 开通项）、画像、条款（Plan / Version / 价格集合 / 提交 / 审批 / 排期）。BFF 做页面聚合（合同 + 订阅 + 开通项 + 当前版本一次返回），不编排跨系统事务。
- Platform 注册（阶段 6 DML）：权限 `COMMERCIAL_CONTRACT_READ / MANAGE`、`COMMERCIAL_PRICING_READ / MANAGE / APPROVE`（APPROVE 与 MANAGE 分离以保证 maker ≠ checker）；Console Resource 三个菜单：合同、定价画像、价目；Gateway OPERATIONS Route 按 BFF 路径登记。
- Web 页面：合同列表 / 详情（订阅、开通项、签署）、画像维护、条款版本编辑器（价格矩阵：OE × 币种 × 画像 × 事件）。页面是否纳入首切片见第 4 节 E 线。

### 2.10 现状偏差收口（S5）

Plan `status` 建 `CommercialTermPlanStatus`；`CheckoutErrorCode` 的 `CCT_CONTEXT_*` 改为 `COMMERCIAL_CONTEXT_*`（调用方 acquiring 同步）；`calculation_base` 建枚举；`trigger_code` / `term_type` 改读 `t_product_fee_event`；`business_calendar_code` 本期固定 AE 日历码常量并登记为 Platform 参考数据待办。

### 2.11 验证矩阵（阶段 4 每线必须覆盖）

写入：首次处理、同身份同内容 / 改内容重放、归属错误（合同不覆盖 OE、Owner XOR 违反）、状态不符、BCR 版本冲突、保存回滚、maker = checker 拒绝、激活校验六条各一个失败例、提交后事件发布与补偿恢复。命中：币种精确、具体度、优先级、事实缺失、无命中、歧义、历史版本回读不受换版影响。解析：未订阅、未开通、币种不在约束、SUSPENDED、区间外版本、SCHEDULED 已到时未切 ACTIVE。


## 3. 数据库变更

- 完整 Schema：`paytech/commercial/docs/database/sql/commercial-schema.sql`（空库执行文件）。
- 本机测试库增量：[design-discuss/commercial/local-mor_commercial-round2.sql](../../../design-discuss/commercial/local-mor_commercial-round2.sql)（2026-10-19 用户授权，仅 local-environment `mor_commercial` 已执行）。
- mor-test 增量：待阶段 6 按 mor-test 当前结构另行评审生成；本目录 `db/ddl.sql` 为索引，不含可执行语句。

## 4. 实施计划（阶段 4 建议）

| 线 | 内容 | 依赖 |
|---|---|---|
| A 模型与测试基座 | 测试 DDL 补齐六张新表与 Price `currency`；Builder / Mocker / Checker 基座；现状偏差收口（S5） | 无 |
| B 运营写入 | 合同 / 签署 / 订阅 / 开通项 / 画像 / 条款的 Processor、Domain Service、Repository 与 HTTP API；审批接入；激活校验 | A |
| C 价格命中与读接口 | `commercial-prices` 两接口；`rate-cards` 读接口 | A |
| D 解析扩展 | 解析响应新增字段与四项派生；订阅 / 开通项 / 币种判定；risk-decision 门禁；S5 错误码改名的调用方同步 | A |
| E 运营控制台 | operations-console-bff `/commercial-admin/v1/**` 聚合、Web 三组页面、Platform 注册 DML | B、C |

验证：每线 auto 测试 + 模型门禁；阶段 5 本机全链路冒烟（建合同到价格命中）。
