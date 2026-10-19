# billing 计费事实模型重构与渠道成本迁出：技术方案

> 状态：第 1 节（阶段 2）已确认（2026-10-19）；第 2 节（阶段 3）为 2026-10-19 评审稿，待确认。SQL 待执行文件在 [db/](./db/)。设计正文见 [design-discuss/billing](../../../design-discuss/billing/database.md)。

## 1. 现状差异、能力缺口与逐系统影响（阶段 2）

依据：2026-10-19 对 `paytech/billing`、`paytech/channel-connector`、account / ledger 对 billing-api 的引用、`scripts/pdmaner`、arch-rules 的只读调查（细节见 [design.md 第 2 节](../../../design-discuss/billing/design.md)）。

### 1.1 需求项差异表

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理 |
|---|---|---|---|---|
| 事实头 / 明细行模型 | `t_economic_fact` 49 列、`t_economic_fact_line` 35 列（`billing-schema.sql`）；按类型分组的 17 个可空 ID 列、15 个定价输入列、17 个 `source_*` 列 | 5 张事实表（头 25、引用 5、定价 18、行 19、链接 6） | 结构重构 | 两环境事实表 0 行 → DROP 重建（Q-01） |
| 事实类型与枚举 | `EconomicsType` 7 值、`AmountSource` 7 值、`FactPartyRole` 8 值 | `BillingFactType` 12、`FactComponentType` 17、`FactPartyRole` 9、新增 `FactOriginType` / `FactRefType` / `SourceRuleType` / `CalculationMethod` | 改名与扩展 | billing-api 改，account / ledger 跟随 |
| 人工登记接口 | `/billing/v1/economic-facts` 提交 / 批准，请求体含 `clientId`、`entityNo`、`localPartnerId` 等头字段与行（`feeTypeCode`、付方 / 收方） | `/billing/v1/facts`：请求体按新模型（`refs[]` 可选、行付方 / 收方必填），`origin_type = MANUAL` | 路径与字段名 | 阶段 3 定请求体 |
| 查询接口 | 按 ID、按流分页、`/pending`（已含 `publishedFactCount`） | 路径改 `/billing/v1/facts…`，`/pending` → `/progress`，新增 `/by-ref` | 改名 + 1 新 | ledger / account 客户端同步 |
| 事件 | `EconomicFactRecorded`（`EconomicFactPayload`，头 + 行；`balanceAvailableTime` 已加） | `BillingFactRecorded`（`BillingFactPayload`：头 + 引用 + 行，行含付方 / 收方 / 组件 / `businessTime`） | 改名 + 载荷结构 | 事件目录、account 消费者、ledger 回读 |
| 消息消费表 | 无 | `t_consumed_event`、`t_consumed_aggregate_version` | 新增 | 本期只建表，不接消费者 |
| 工作日日历 | 无 | 两张副本表 + `DEFAULT` 日历占位 | 新增 | 同步机制另立 |
| 渠道成本配置 | billing 4 表 + 5 个 Maker-Checker 端点 + `ChannelCostEstimationService`（`domainservice/service/impl/ChannelCostEstimationServiceImpl.java`，无调用方） | channel-connector 拥有四表与端点 + 命中接口；billing 保留"组件 → 金额"计算器 | 整体搬迁 + 1 新接口 | mor-test 两库都是 0 行配置 |
| `AutoInstructionType` | billing-api | ledger-api | 迁移 | ledger 唯一使用方 |
| `unroutable-policy` | `ACK_AND_WARN` | `FAIL` | 配置 | 台账既有事项，顺带 |
| PDManer | `billing.pdma.json` 由 Finance 生成器投影（`service-split.mjs` 仍含 billing） | 直接维护 + `billing-model.test.mjs` | 摘出 | 同 account / ledger 先例 |

### 1.2 逐系统影响表

| 系统 | 影响点 | 改代码 / 模型 / 配置 | 风险 |
|---|---|---|---|
| **paytech/billing** | api：类与枚举改名 / 新增，接口路径改，删 `ChannelCostPlanApi`；core：DO / Mapper / Repository 按 5 张事实表，登记 / 批准 / 查询 Processor 改新模型，补偿 Handler 改载荷与事件名，删渠道成本 Processor / Service（保留计算器）；app：Controller、`application.yml`（`FAIL`）；test：按新模型重写（人工登记、幂等、替代、发布、查询），删渠道成本测试；docs：`billing.pdma.json`、Schema、测试 DDL、变更 SQL、初始化 DML、README | 代码 ✔ 模型 ✔（9 → 11）配置 ✔ | 事实表无数据；api 改名波及 account / ledger |
| **paytech/channel-connector** | api：`ChannelCostPlanApi`（原样搬）+ `ChannelCostResolutionApi`；core：四表 DO / Mapper / Repository、五个 Maker-Checker Processor（Redis 锁沿用）、命中 Processor（只读）、XXL 切换 Job；app：Controller、XXL 配置（本服务现无 XXL，需加执行器）；test：搬测试 + 命中接口测试；docs：pdma（Q-02）、Schema、测试 DDL、变更 SQL | 代码 ✔ 模型 ✔（5 → 9 业务表）配置 ✔ | XXL 执行器新接；`scope.mid_id` 改为引用本库 MID 需校验存在 |
| **paytech/account** | 消费者 Handler 与 Reader 改事件名与载荷类；映射规则按行付方 / 收方 + 组件类型（`FactComponentType`）；测试 Probe 与断言改名 | 代码 ✔ | 行为不变，测试计数不变 |
| **paytech/ledger** | 事实读取（`getBillingFact`）、进度接口改路径；`AutoInstructionType` 改从 ledger-api；`BillingFactType` / `FactComponentType` 引用；测试 DTO Mock 改名 | 代码 ✔ | 行为不变 |
| **Foundation 事件目录** | `EconomicFactRecorded` → `BillingFactRecorded`，payloadSchemaRef 改 | 配置 ✔ | R12 校验 |
| **arch-rules / scripts/pdmaner** | 表归属生成器（billing 表集合、channel-connector 加四表）、design/03、`TableOwnershipAutoTest`；Finance 生成器去 billing，`finance-model.test.mjs` / `pdmaner-canonical-paths.test.mjs`，新增 `billing-model.test.mjs`（必要时 `channel-connector-model.test.mjs`） | 代码 ✔ | 既有 50 例 pdmaner 失败要区分 |
| **test-environment** | 无新变量；发布 4 个镜像 + `mor_billing`、`mor_channel_connector` 变更 SQL | 配置 ✘ | — |
| **Operations BFF / Web / Platform** | 不涉及 | ✘ | — |
| **文档 / 站点** | 决定记录 Confirmed、design/03 与 design/04 取代说明、`FINANCE_DATABASE.md` 取代说明、requirements-site FIN-04 / 事实名、台账、design-discuss 状态 | 文档 ✔ | — |

### 1.3 难点与工作量依据

1. billing-api 改名是 T0 合同，account / ledger 的适配只能在其后并行；本次没有新业务逻辑，主要是模型与命名的机械改动 + 测试重写（billing 90 例、channel-connector 新增约 30 例、account / ledger 各改名）。
2. channel-connector 首次引入 Maker-Checker 与 XXL：Redis 锁与补偿事件配置照 billing 现有实现搬，XXL 执行器照 account 加。
3. PDManer 摘出两份模型（billing 必做，channel-connector 视 Q-02）。
4. mor-test 两库都无业务行，变更 SQL 为重建型；发布顺序 channel-connector → billing → ledger → account（事件名切换时 account 旧消费者会对新事件名无队列，需先停 account 再切）。

## 2. 方案、接口与数据设计（阶段 3，2026-10-19 评审稿）

**规模：** 本次方案涉及 7 个系统（billing、channel-connector、account、ledger、Foundation 事件目录、arch-rules / scripts/pdmaner、test-environment）；接口新增 9（channel-connector 8：7 个 Maker-Checker 端点搬入 + 1 个命中接口；billing 1：按引用查）、修改 5（billing 提交、批准、按 ID、按流分页、进度：路径与请求 / 响应体）、删除 7（billing 的 `ChannelCostPlanApi` 7 个端点）；事件修改 1（`EconomicFactRecorded` → `BillingFactRecorded`，载荷结构变）；`mor_billing` 删除 7 张表、新增 9 张（9 → 11），`mor_channel_connector` 新增 4 张（13 → 17），ledger、account 无表结构变化；索引随新表 25 个；业务初始化 DML 新增 1 行（billing 日历）、注册 DML 0；页面设计 0。

### 2.1 业务与架构

**Owner 与职责（决定 2026-10-19 第 1、7 条）**

| 能力 | Owner | 本期动作 |
|---|---|---|
| 计费事实目录、登记、版本、发布 | billing | 模型与命名重构；人工登记沿用 Maker-Checker；自动事实生成仍不在本期 |
| 渠道成本方案 / 版本 / 范围 / 组件配置与审批 | channel-connector | 四表、五个 Maker-Checker 端点、到期切换 Job 搬入；新增命中接口 |
| 渠道成本"组件 → 金额"计算 | billing | 保留现有 `ChannelCostEstimationServiceImpl` 中的逐组件计算部分为本地纯计算器（输入：命中接口返回的组件 + 基数 + 币种精度），删除"找方案 / 版本 / 范围"部分；本期无调用方，只保留并加纯 Java 测试 |
| 明细账 / 总账 | account / ledger | 只做事件名、载荷、枚举、接口路径适配，规则与余额行为不变 |

**链路与事务（事务公理：事务内只操作本库）**

| 环节 | 事务边界 | 内容 |
|---|---|---|
| billing 人工登记 `POST /billing/v1/facts` | `AbstractLockProcessor`（锁 `sourceSystem + sourceReference`）→ 一个本地事务 | 事务外：解析代码、币种精度（`t_currency_spec`）、COST_ADJUSTMENT 读父事实；事务内：同来源键存在 → Hash 相同返回原事实、不同 400；写 `t_billing_fact`（`origin_type = MANUAL`、`status = DRAFT`、`fact_version = 0`、`business_time` 暂为空值占位不允许 → 以提交时间写入，批准时覆盖为批准时间）、`t_billing_fact_ref`、`t_billing_fact_line`（含 `target_bucket`）。与现状差异只有表与字段 |
| billing 批准 `POST /billing/v1/facts/{factId}/approve` | 锁 `factId` → 一个本地事务 | 同现状：Checker ≠ Maker、幂等重复批准；`APPROVED`、`fact_version` 定稿、`approved_time = business_time = 批准时间`、被替代事实 `SUPERSEDED`、登记补偿事件 `BILLING_FACT_RECORDED`（`event_key = factId:factVersion`） |
| billing 发布 | 补偿 Handler（事务外） | 按事件键读头 + 引用 + 行组装 `BillingFactPayload`，发布 `BillingFactRecorded`（聚合 `BILLING_FACT`、aggregateId = factId、aggregateVersion = factVersion、occurredAt = approvedTime）；Broker 确认后短事务写 `published_at`。退避策略不变；`unroutable-policy` 改 `FAIL` |
| channel-connector Maker-Checker 五个端点 | `AbstractLockProcessor`（锁 `planCode`）→ 一个本地事务 | 逻辑逐字搬自 billing；新增校验：`scopes[].midId` 在本库 `t_channel_mid` 存在、`channel_id` 等于方案 Channel、`entity_no` 等于 MID 归属 OE；币种只校验格式与小数位 ≤ 8（本库无币种表，精度归整由 billing 在计算时按 `t_currency_spec` 做） |
| channel-connector 到期切换 Job `channelCostVersionActivation` | XXL 每 5 分钟 → 每个方案一个锁 + 短事务 | 查 `status = APPROVED` 且 `effective_from <= now` 的版本：锁 `planCode`，复核仍 APPROVED 后原子切换（原当前版本 `INACTIVE`、`effective_to = 新版本 effective_from`，方案 `current_ver_id`、`version` CAS）。补上 billing 现状"批准时未到生效时间保持 APPROVED 后无人切换"的缺口 |
| channel-connector 命中 `POST /channel-connector/v1/channel-costs/resolutions` | 只读 Processor，无事务 | 方案 ACTIVE → 覆盖 `asOfTime` 的唯一 ACTIVE / INACTIVE 版本 → 六维精确命中唯一范围 → 返回范围与组件；任一步不唯一或缺失返回 400 业务错误，不返回空组件 |
| account 消费 `BillingFactRecorded` | SHARED（同现状） | Reader 改读新载荷；映射规则改为"每行必有付方 / 收方"的单一路径（删除隐含收方分支与 `GROSS_AMOUNT`），其余规则、锁顺序、`AccountPosted` 发布不变 |
| ledger 消费 `AccountPosted` | HANDLER_MANAGED（同现状） | 事务外经 `BillingFactQueryApi.getFact` 读事实；`businessTime` 直接取载荷 `businessTime`；`CHANNEL_ACCOUNT` Owner 取引用 `CLEARING_ACCOUNT`；明细行主体取收方（现状已如此）。关账步骤 3 改调 `/billing/v1/facts/progress` |

**枚举归属（决定第 11 条 + 阶段 3 细化）**

| 枚举 | 现在 | 方案 |
|---|---|---|
| `EconomicsType`（billing-api） | 7 值 | 改名 `BillingFactType`，12 值：`PAYMENT`、`REFUND`、`DUPLICATE_CAPTURE`、`DUPLICATE_COMPENSATION`、`DISPUTE`、`COST_ADJUSTMENT`、`CHANNEL_SETTLEMENT`、`PAYOUT_SETTLED`、`TAX_PAID`、`MANUAL_ADJUSTMENT`、`FX_SELL`、`FX_BUY` |
| `EconomicFactStatus` | 3 值 | 改名 `BillingFactStatus`，值不变 |
| `AmountSource`（billing-api） | 7 值（含 `GROSS_AMOUNT`） | **迁到 ledger-api** `com.paytech.ledger.api.enums.AmountSource`，值与代码不变（ledger 规则行 DML 36 行不动）；billing-api 新增 `FactComponentType` 15 值：`CLIENT_PRINCIPAL`、`CLIENT_FEE`、`SELLER_TAX`、`PARTNER_SHARE`、`PLATFORM_SERVICE_CHARGE`、`CHANNEL_COST`、`CHANNEL_COST_SETTLED`、`DUPLICATE_AMOUNT`、`PAYOUT_FEE`、`PAYOUT_AMOUNT`、`SETTLEMENT_AMOUNT`、`TAX_PAYMENT_AMOUNT`、`FX_SOLD_AMOUNT`、`FX_BOUGHT_AMOUNT`、`FX_FEE`（讨论稿的 `CHANNEL_COST_INCREASE / DECREASE` 删除：付方 / 收方已表达增提或冲减方向，见 2.6） |
| `AutoInstructionType`（billing-api） | 5 值 | 迁到 ledger-api，值不变 |
| `FactPartyRole` | 8 值 | 9 值：删 `PLATFORM`，加 `CASH_ACCOUNT`、`FX_ACCOUNT`；`CONSUMER` 保留 |
| `FactLineDirection` | 3 值 | 不变 |
| 新增（billing-api） | — | `FactOriginType`（`AUTO`、`MANUAL`）、`FactRefType`（21 值，见 DDL 注释）、`SourceRuleType`（`COMMERCIAL_PRICE`、`PARTNER_SHARE`、`PLATFORM_SERVICE_CHARGE`、`TAX_RULE`、`CHANNEL_COST`、`FX_RATE`）、`LineLinkType`（`REVERSAL`、`ADJUSTMENT`）、`CalculationMethod`（`PERCENTAGE`、`FIXED`、`TIERED_WHOLE_BASE`、`TIERED_PROGRESSIVE`、`PASS_THROUGH`） |
| 新增（billing-core） | — | `CalendarDayType`（`WORKING`、`NON_WORKING`）、`CalendarStatus`（`ACTIVE`、`INACTIVE`） |
| 渠道成本枚举 `ChannelCostPlanStatus` 等 | billing-core | 随四表迁到 channel-connector-core；`ChannelCostContentHash.SCHEMA` 改 `CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1`（无存量数据） |

**人工登记的目录约束（本期实现范围）**

| 事实类型 | `refs` | 行 | 其他 |
|---|---|---|---|
| `MANUAL_ADJUSTMENT` | 可选 `TICKET` | `componentType` ∈ {`CLIENT_PRINCIPAL`、`CLIENT_FEE`、`PARTNER_SHARE`、`PLATFORM_SERVICE_CHARGE`、`SELLER_TAX`、`CHANNEL_COST`}；`direction` ∈ {`CHARGE`、`REVERSAL`、`ADJUSTMENT`}；`targetBucket` 可选（`PENDING` / `AVAILABLE`，仅收方为 CLIENT / LOCAL_PARTNER 时允许） | `clientId` 必填；`parentFactId` 必须为空 |
| `COST_ADJUSTMENT` | 必填 `CLEARING_ACCOUNT`（替代原 `channelAccountId`） | 只允许 `CHANNEL_COST` + `ADJUSTMENT`，付方 / 收方为 CHANNEL ↔ OPERATING_ENTITY | `parentFactId` 必填且为 APPROVED 的 PAYMENT 事实；`clientId` 取父事实 |

其余类型本期 400 `INVALID_REQUEST`（请求正则限定两个值，同现状）。行级不变量同现状：付方 ≠ 收方；CLIENT / LOCAL_PARTNER / FINANCE_ENTITY / OPERATING_ENTITY 的标识必须等于头部对应字段；`PARTNER_SHARE`、`PLATFORM_SERVICE_CHARGE` 要求合作方二元组（`localPartnerId` + `financeEntityId`）存在，服务费收方 = `financeEntityId`；金额正且精度 ≤ 币种 minor unit。`target_bucket` 本期只校验与落库、进载荷，account 继续按现行映射记 PENDING（决定第 14 条的 account 侧随后续切片启用）。

### 2.2 接口

**事件（Foundation `catalog/events.yaml`）**

| 事件 | 变化 | 内容 |
|---|---|---|
| `EconomicFactRecorded` | 删除 | 目录项、`EconomicFactPayload` / `EconomicFactLinePayload` 删除 |
| `BillingFactRecorded` | 新增（替代） | producer billing、aggregateType `BILLING_FACT`、aggregateVersionField `factVersion`、consumers `[account]`、strictOrder false、payloadSchemaRef `paytech-billing-api/event/BillingFactRecorded/v1`，Schema 版本 1 |

`BillingFactPayload`（billing-api `fact` 包；事件 JSON 全部标量为字符串，时间 UTC RFC 3339；也是按 ID 读取接口的字段语义）：

```text
factId, factVersion, supersedesFactId, parentFactId, factType, originType, status,
sourceSystem, sourceEventKey, sourceVersion, sourcePayloadHash,
businessTime, recordedTime(= approvedTime, 事件 occurredAt), balanceAvailableTime(可空),
clientId(可空), entityNo, localPartnerId(可空), financeEntityId(可空), currency, grossAmount,
refs[]:  refType, refId
lines[]: lineNo, componentType, payerRole, payerId, payeeRole, payeeId, direction, postedAmount, targetBucket(可空)
```

删除的载荷字段：`paymentSucceededTime`（并入 `businessTime`）、`partnerAgreementOeId`（只在定价侧表）、`channelAccountId`（改为 `refs` 的 `CLEARING_ACCOUNT`）、行的 `partyType / partyId`（主体 = 收方）。定价侧表与行证据不进事件（消费者不需要；按 ID 读取接口返回）。

**billing-api**

| 变化 | 接口 | Method / Path | 说明 |
|---|---|---|---|
| 修改 | `BillingFactCommandApi`（原 `EconomicFactCommandApi`） | `POST /billing/v1/facts` | Body `SubmitBillingFactRequestBody`：`factType`（`MANUAL_ADJUSTMENT`\|`COST_ADJUSTMENT`）、`sourceSystem`、`sourceReference`、`parentFactId`?、`supersedesFactId`?、`clientId`?、`entityNo`、`localPartnerId`?、`financeEntityId`?、`currency`、`makerId`、`refs[]`?（≤ 10：`refType` 正则 `[A-Z][A-Z_]{0,31}`，`refId` `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`）、`lines[]`（1–100：`componentType`、`payerRole`、`payerId`、`payeeRole`、`payeeId`、`amount`、`direction`、`targetBucket`?）。删除 `partnerAgreementOeId`、`channelAccountId`。返回同现状（201 新建 / 200 幂等重复）：`BillingFactResponseBody` |
| 修改 | 同上 | `POST /billing/v1/facts/{factId}/approve` | Body 不变（`checkerId`）；返回 `BillingFactResponseBody` |
| 修改 | `BillingFactQueryApi`（新接口类，承接原查询方法） | `GET /billing/v1/facts/{factId}` | 返回 `BillingFactResponseBody`：载荷全部字段 + `makerId`、`checkerId`、`approvedTime`、`publishedAt` + `pricing`（仅 PAYMENT，`BillingFactPricingResponseBody`，本期恒为空）+ `lines[]` 增加 `basisAmount`、`rateValue`、`rawAmount`、`sourceRuleType`、`sourceRuleVersionId`、`sourcePriceId`、`sourceRuleComponentId`、`sourceEvidence`（JSON 对象，可空）+ `links[]`（`sourceLineId`、`targetLineId`、`linkType`、`linkedAmount`；本期人工登记不产生链接，恒为空列表） |
| 修改 | 同上 | `GET /billing/v1/facts?entityType&entityId&currency&fromFactId&limit` | 参数改名 `bookEntityType → entityType`、`bookEntityId → entityId`（与 account 进度接口一致）；语义不变（APPROVED / SUPERSEDED，按 factId 升序，limit 1–500） |
| 修改 | 同上 | `GET /billing/v1/facts/progress?entityType&entityId&currency` | 原 `/pending`；响应 `BillingFactProgressResponseBody` 四个字段不变 |
| 新增 | 同上 | `GET /billing/v1/facts/by-ref?refType&refId&fromFactId&limit` | 返回 `BillingFactListResponseBody`（任意状态，按 factId 升序，`fromFactId` 可选游标同列表接口，limit 1–200 缺省 100，`nextFromFactId` 同列表接口；T0 发现缺游标入参后补）；`refType` 不在目录 400 `INVALID_REQUEST`；无匹配返回空列表 |
| 删除 | `ChannelCostPlanApi` 及 11 个请求 / 响应体 | `/channel-cost-plans/v1/**` | 搬到 channel-connector-api |

错误码沿用现状：`INVALID_REQUEST`（400，格式 / 目录）、`BUSINESS_CHECK_FAILED`（400，不变量）、并发 409；鉴权同现状（内部接口，操作人由调用方传入）。

**channel-connector-api**

| 变化 | 接口 | Method / Path | 说明 |
|---|---|---|---|
| 新增（搬入） | `ChannelCostPlanApi`（包 `com.paytech.channel.connector.api.cost`） | `POST /channel-connector/v1/channel-cost-plans`、`POST .../{planCode}/versions`、`POST .../{planCode}/versions/{costVer}/submit`、`.../approve`、`.../reject`、`GET .../{planCode}`、`GET .../{planCode}/versions/{costVer}` | 请求 / 响应体 11 个类原样搬入（字段、正则、JavaDoc 不变）；路径前缀从 `/channel-cost-plans/v1` 改为本服务统一的 `/channel-connector/v1/channel-cost-plans`；`midId` 语义改为本库 MID 主键，新增三项存在性 / 归属校验（2.1） |
| 新增 | `ChannelCostResolutionApi` | `POST /channel-connector/v1/channel-costs/resolutions` | Body `ResolveChannelCostRequestBody`：`channelId`、`midId`、`entityNo`、`countryCode`、`currency`、`paymentMethodCode`、`actionType`（正则同范围请求体）、`asOfTime`（Instant，必填）。返回 200 `ChannelCostResolutionResponseBody`：`planCode`、`costVerId`、`costVer`、`contentHash`、`effectiveFrom`、`effectiveTo`?、`scopeId`、`scopeNo`、`channelAgreementRef`、`currency`、`components[]`（`componentId`、`componentCode`、`componentType`、`calculationBase`、`rateValue`?、`fixedAmount`?、`currency`?、`minAmount`?、`maxAmount`?、`roundingMode`，按 `componentCode` 升序）。错误：无 ACTIVE 方案 / 无覆盖版本 / 无命中范围 400 `CHANNEL_COST_NOT_RESOLVED`；命中多于一个 400 `CHANNEL_COST_AMBIGUOUS`（均含维度上下文）。只读、幂等，不算金额 |

**ledger-api**

| 变化 | 内容 |
|---|---|
| 新增 | `com.paytech.ledger.api.enums.AutoInstructionType`（5 值）、`AmountSource`（7 值），JavaDoc 改为 ledger 口径；`RuleLineRequestBody` 正则 / 说明不变 |

**account-api**：无变化（`AccountPostedPayload` 等 JavaDoc 中的事实名随改）。

**Operations BFF / Web**：无变化。

### 2.3 数据库与注册数据

**billing（`mor_billing`，执行文件 [db/billing/ddl.sql](./db/billing/ddl.sql) → [db/billing/dml.sql](./db/billing/dml.sql)）**

两环境事实表与成本配置均 0 行（Q-01 已确认重建）：DROP 7 张、CREATE 9 张；`t_currency_spec`、`t_compensation_event` 不动。阶段 4 同步改写 `billing-schema.sql`（11 张）、`billing-initial-data.sql`（币种 1 行 + 日历 1 行）、测试 `init/ddl.sql`、`billing.pdma.json`（脱离 Finance 生成器，直接维护 JSON，图组：计费事实 5 表、参考数据副本 3 表、可靠执行与消费幂等 3 表）与 `billing-model.test.mjs`。

| 表 | 变化 | 要点 |
|---|---|---|
| `t_billing_fact` | 新（替代 `t_economic_fact`） | 25 列，7 个索引；详见 DDL |
| `t_billing_fact_ref` | 新 | `uk (fact_id, ref_type)`、`idx (ref_type, ref_id)` |
| `t_billing_fact_pricing` | 新 | 1:1，本期无写入方；`commercial_price_id`（讨论稿 `client_price_id` 改名，见 2.6） |
| `t_billing_fact_line` | 新（替代 `t_economic_fact_line`） | 20 列（讨论稿 19 + `target_bucket`）；`source_evidence` JSON |
| `t_billing_fact_line_link` | 新（替代 `t_economic_fact_line_link`） | 结构不变、列名改 `source_line_id / target_line_id` |
| `t_business_calendar` / `t_business_calendar_day` | 新 | 只读副本；DML 1 行 DEFAULT |
| `t_consumed_event` / `t_consumed_aggregate_version` | 新 | 逐字取 Foundation 标准表；本期无消费者 |
| `t_channel_cost_plan` / `_ver` / `_scope` / `_component` | 删除 | 归 channel-connector |

**channel-connector（`mor_channel_connector`，执行文件 [db/channel-connector/ddl.sql](./db/channel-connector/ddl.sql)）**

CREATE 4 张（结构取自 billing 现 Schema，`mid_id` 语义改本库 MID）；无 DML。阶段 4 同步：`channel-connector-schema.sql`（17 张）、测试 `init/ddl.sql`、`channel-connector.pdma.json`。Q-02 核查结果：该模型由 `scripts/pdmaner/generate-channel-connector-model.mjs` 从 Payment Orchestration 生成模型按 `payment-service-partition.mjs` 分区投影；方案为把四表加入 Payment 生成模型的 channel-connector 分区（`CHANNEL_CONNECTOR_TABLES` + 表 DSL 与字段元数据），Finance 生成器 `manifests/02-tax-economics-cost.mjs` 与 `service-split.mjs` 删除 billing 与这四表；不另建第二套机制。

**ledger / account**：无 DDL、无 DML（ledger `t_accounting_instruction.economic_fact_id` 列名保留，只改注释措辞不算结构变化，本期不改）。

**注册 DML**：0（无新增对外 API、菜单或权限）。

**执行顺序（本机与 mor-test 相同）**：① `db/channel-connector/ddl.sql` → ② `db/billing/ddl.sql` → ③ `db/billing/dml.sql`。恢复：执行前对 `mor_billing`、`mor_channel_connector` 整库 dump；两库均无业务行，回退即按 dump 恢复或按文件头注释 DROP / 重建。

**arch-rules / 事件目录 / 补偿目录**：`table-ownership.yaml` billing 集合改为 11 张、channel-connector 加 4 张；`events.yaml` 替换目录项；billing `compensation-event-catalog.yaml` 改 `BILLING_FACT_RECORDED`；channel-connector 新建 `compensation-event-catalog.yaml`（若引入 `paytech-compensation-event` 需要空目录）。

### 2.4 页面

无（渠道成本配置页面属财务第三批；本期不改 Operations BFF / Web）。

### 2.5 研发与验证

**分线（阶段 4，Opus 5.5 多 agent；公共约定沿用前序需求 `tasks/00-common.md` 形式另写）**

| 线 | 范围 | 依赖 | 预期制品与验证入口 |
|---|---|---|---|
| T0 合同 | billing-api 全部改名 / 新增 / 删除；ledger-api 两个枚举；channel-connector-api 搬入 + 命中接口；`events.yaml` 与 Foundation `YamlEventCatalogAutoTest` 的事件名；两个补偿目录 | — | 三个 `*-api` 模块 `verify` 通过；Foundation `YamlEventCatalogAutoTest` 通过 |
| A billing | DO / Mapper / XML / Repository 按 5 张事实表 + 日历 2 表；登记 / 批准 / 查询 Processor 与 Converter；`BillingFactRecordedHandler`；删除渠道成本 Processor / Service / Repository / Controller，保留纯计算器；`application.yml`（`FAIL`）；Schema / 初始化 DML / 测试 DDL / `billing.pdma.json` / `billing-model.test.mjs` / README；测试按新模型重写 | T0 | billing-test：人工登记（幂等、不变量、`refs`、`targetBucket`）、批准（替代链、版本）、发布 Handler（载荷含 `refs` 与行）、四个查询接口（含 `by-ref`）、计算器纯 Java 测试；`BillingModuleBoundaryTest`；目标 ≥ 现 90 例覆盖同等场景 |
| B channel-connector | 四表 DO / Mapper / Repository；五个 Maker-Checker Processor（搬入 + 三项校验）；命中 Processor；切换 Job；Controller；`paytech-redis-starter`、`paytech-compensation-event`（XXL 执行器）、`paytech-snowflake-id` 依赖与配置（测试 yml 接 local-environment Redis，照 billing）；Schema / 测试 DDL / pdma（经 Payment 生成器）/ README | T0 | channel-connector-test：搬入 billing 现有 3 个成本测试类并改路径与 MID 校验用例；命中接口（唯一命中、无方案、无版本、无范围、多命中）；Job（到期切换、未到期不动、幂等） |
| C account + ledger | account：Handler / Reader / `FactRecord` / 映射规则（删隐含收方与 `GROSS_AMOUNT`）/ 测试 Builder、Publisher、断言；ledger：`BillingFactClient`、`TransactionPostingServiceImpl`（`businessTime`、`CLEARING_ACCOUNT` 引用、`BillingFactType`、ledger-api 枚举）、`LedgerPersistenceConfiguration`、Converter、关账进度调用、测试 DTO Mock 与 Builder | T0 | account-test 58/58、ledger-test 234/234 计数不变（只改名与结构） |
| D 规则与文档 | `table-ownership.yaml` + `TableOwnershipAutoTest`；`payment-service-partition.mjs` / Payment 模型 DSL 加四表、Finance 生成器去 billing 与四表、`finance-model.test.mjs` / `pdmaner-canonical-paths.test.mjs` / `payment-channel-query.test.mjs` 期望；design/03、design/04 取代说明；`FINANCE_DATABASE.md` 取代说明；requirements-site FIN-04 Owner 与事实名；`design-discuss/billing/*` 按 2.6 回写；台账 | A、B 完成 pdma 后 | arch-rules 全过；`node --test scripts/pdmaner/*.test.mjs` 新增项通过、既有失败单列；`npm --prefix requirements-site test` |

实施顺序：T0 → A、B、C 并行 → D。各线报告 `tasks/<线>-report.md`，含第 11 节自查。

**验收映射**

| AC | 验证方式 |
|---|---|
| AC-01 | 本机两库执行三份 SQL 后 `SHOW TABLES` 计数 11 / 17；`billing-model.test.mjs`、Payment 生成器 `--check`、`TableOwnershipAutoTest` 通过；Schema 与测试 DDL 用 diff 核对逐字一致 |
| AC-02、AC-03 | A / C 线自动测试 + 阶段 5 本机链路：人工登记 → 批准 → `BillingFactRecorded` → account 批次 → ledger 分录，复用前序需求 `verification/local_acceptance.py` 改接口路径 |
| AC-04 | B 线自动测试（搬入用例逐一对应原 billing 用例）+ 阶段 5 本机用 curl 走一遍创建 → 提交 → 批准 → 命中 |
| AC-05 | `grep` 零命中 `EconomicFact`、`economic_fact`、`ChannelCostPlanApi`（billing 范围）；`AutoInstructionType` 只在 ledger-api |
| AC-06 | D 线各测试命令 |
| AC-07 | 阶段 6–7：备份 → 停 account → 执行 SQL → 发布 channel-connector → billing → ledger → account（account 旧消费者对新事件名无队列，先停再切）→ 删除 `account.billing.*` 旧绑定前确认 0 消息 → 链路验收 → 清理 |

**真实环境依赖**：local-environment MySQL / Redis / RabbitMQ；channel-connector 测试首次接 Redis。mor-test 发布随阶段 6 另写 `release/release.md`。

### 2.6 阶段 3 对决定与讨论稿的细化（阶段 4 D 线回写 design-discuss，决定记录只加一条"阶段 3 细化"注）

| 项 | 讨论稿 / 决定 | 阶段 3 | 原因 |
|---|---|---|---|
| 组件类型 | 17 值，含 `CHANNEL_COST_INCREASE / DECREASE` | 15 值，成本调整用 `CHANNEL_COST` + `ADJUSTMENT` | 行已强制付方 / 收方，方向由付方 / 收方表达；account 与 ledger 现行规则（`CHANNEL_COST` 在 CHANNEL ↔ OE 之间）无需改，ledger 规则 DML 不动 |
| 价格规则族 | `CCT_PRICE`、`client_price_id` | `COMMERCIAL_PRICE`、`commercial_price_id` | 2026-10-19 commercial 决定：价格命中接口为 `POST /commercial-prices/v1/resolutions`，CCT 已拆为 client-master 与 commercial |
| `AmountSource` | 讨论稿只说去掉 `GROSS_AMOUNT` | 整个枚举迁到 ledger-api，billing-api 另建 `FactComponentType` | `GROSS_AMOUNT` 是 ledger 规则行的金额来源语义，不是事实组件 |
| 明细行列数 | 19 | 20（加 `target_bucket`） | 决定第 14 条已确认；现在建列避免后续切片再改表，本期 account 不消费 |
| 到期切换 Job | 需求写"搬入" | 新建 | 核查发现 billing 现状没有该 Job（批准后未到期的 APPROVED 版本无人切换） |
| channel-connector 币种校验 | — | 只校验格式与小数位 | 本库无 `t_currency_spec`；精度归整在 billing 计算时做 |
| 命中接口路径 | `/channel-connector/v1/channel-costs/resolutions` | 同 | 搬入的 Maker-Checker 端点同步改为 `/channel-connector/v1/channel-cost-plans/**` 前缀，与本服务现有接口一致 |
