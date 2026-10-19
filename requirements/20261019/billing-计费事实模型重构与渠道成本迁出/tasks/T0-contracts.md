# 任务卡 T0 · 合同先行：billing-api / ledger-api / channel-connector-api / 事件目录 / 补偿目录

先读 `00-common.md`。本卡完成并由主会话核对后，A / B / C / D 线才并行开始；下游 core / app / test 编译因本卡改名而暂时失败是**预期的**，由 A（billing）、B（channel-connector）、C（account + ledger）修复，本卡不改它们。

## 允许改动的路径

- `paytech/billing/paytech-billing-api/**`、`paytech/billing/compensation-event-catalog.yaml`
- `paytech/ledger/paytech-ledger-api/**`
- `paytech/channel-connector/paytech-channel-connector-api/**`、新建 `paytech/channel-connector/compensation-event-catalog.yaml`
- `paytech/foundation/paytech-messaging/catalog/events.yaml`

## 1. billing-api（`com.paytech.billing.api`）

按 `technique.md` 2.1「枚举归属」「人工登记的目录约束」与 2.2「billing-api」。

1. 枚举（`enums` 包，形态照现有 `EconomicsType`：`CodeEnum`、`getCode()` 返回 `name()`，常量间空一行，每个常量 JavaDoc 一句业务含义）：
   - `EconomicsType` 改名 `BillingFactType`，12 值（顺序：`PAYMENT`、`REFUND`、`DUPLICATE_CAPTURE`、`DUPLICATE_COMPENSATION`、`DISPUTE`、`COST_ADJUSTMENT`、`CHANNEL_SETTLEMENT`、`PAYOUT_SETTLED`、`TAX_PAID`、`MANUAL_ADJUSTMENT`、`FX_SELL`、`FX_BUY`）。
   - `EconomicFactStatus` 改名 `BillingFactStatus`，值不变。
   - 新增 `FactComponentType` 15 值（顺序见 technique 2.1）；**删除 `AmountSource`、`AutoInstructionType`**（迁到 ledger-api，见第 2 节）。
   - `FactPartyRole`：删 `PLATFORM`，加 `CASH_ACCOUNT`（平台银行账户）、`FX_ACCOUNT`（平台外汇账户）；`CONSUMER` 注释改"仅重复扣款两类事实的应退消费者款"。
   - `FactLineDirection` 不变。
   - 新增 `FactOriginType { AUTO, MANUAL }`、`FactRefType`（21 值，顺序与 `REQ/db/billing/ddl.sql` 中 `t_billing_fact_ref.ref_type` 注释一致，每个注释写 Owner）、`SourceRuleType { COMMERCIAL_PRICE, PARTNER_SHARE, PLATFORM_SERVICE_CHARGE, TAX_RULE, CHANNEL_COST, FX_RATE }`、`LineLinkType { REVERSAL, ADJUSTMENT }`、`CalculationMethod { PERCENTAGE, FIXED, TIERED_WHOLE_BASE, TIERED_PROGRESSIVE, PASS_THROUGH }`。
2. 载荷（`fact` 包）：`EconomicFactPayload` 改名 `BillingFactPayload`（`EVENT_TYPE = "BillingFactRecorded"`、`SCHEMA_VERSION = 1`、`AGGREGATE_TYPE = "BILLING_FACT"`），字段按 technique 2.2 清单：`factId`、`factVersion`、`supersedesFactId`、`parentFactId`、`factType`、`originType`、`status`、`sourceSystem`、`sourceEventKey`、`sourceVersion`、`sourcePayloadHash`、`businessTime`、`recordedTime`、`balanceAvailableTime`、`clientId`、`entityNo`、`localPartnerId`、`financeEntityId`、`currency`、`grossAmount`、`refs`（`List<BillingFactRefPayload>`：`refType`、`refId`）、`lines`（`List<BillingFactLinePayload>`：`lineNo`、`componentType`、`payerRole`、`payerId`、`payeeRole`、`payeeId`、`direction`、`postedAmount`、`targetBucket`）。删除 `paymentSucceededTime`、`partnerAgreementOeId`、`channelAccountId`、行的 `partyType / partyId`。类注释照现有写信封映射与"标量全为 JSON 字符串"约定，并说明 `businessTime` 是 ledger 过账日期来源、`refs` 的 `CLEARING_ACCOUNT` 是 ledger 渠道账户 Owner 来源。
3. 接口：
   - `EconomicFactCommandApi` 改为 `BillingFactCommandApi`（`@HttpExchange("/billing/v1/facts")`）：`submitBillingFact`（`POST`，Body `SubmitBillingFactRequestBody`）、`approveBillingFact`（`POST /{factId}/approve`，Body `ApproveBillingFactRequestBody`）。
   - 新增 `BillingFactQueryApi`（同 base URL）：`getBillingFact`（`GET /{factId}`）、`listBillingFacts`（`GET`，参数 `entityType`、`entityId`、`currency`、`fromFactId`、`limit`，正则同现状）、`getBillingFactProgress`（`GET /progress`，三个参数）、`listBillingFactsByRef`（`GET /by-ref`，参数 `refType` 正则 `[A-Z][A-Z_]{0,31}`、`refId` 正则 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`、`limit` 可选 1–200）。JavaDoc 写返回与错误行为（technique 2.2）。
   - 请求体（`request` 包）：`SubmitBillingFactRequestBody` 字段与正则按 technique 2.2（删 `partnerAgreementOeId`、`channelAccountId`；加 `refs`（`@Size(max = 10)`，`BillingFactRefRequestBody { refType, refId }`）；`clientId` 改可空并在注释写"MANUAL_ADJUSTMENT 必填，COST_ADJUSTMENT 取父事实"）；`BillingFactLineRequestBody`（`feeTypeCode` 改名 `componentType`；新增 `targetBucket` 可空、正则 `PENDING|AVAILABLE`）；`ApproveBillingFactRequestBody` 不变。
   - 响应体（`response` 包）：`BillingFactResponseBody`（载荷全部字段 + `makerId`、`checkerId`、`approvedTime`、`publishedAt`、`pricing`（`BillingFactPricingResponseBody`，字段对应 `t_billing_fact_pricing` 全部列的字符串形式，可空）、`refs`、`lines`（`BillingFactLineResponseBody`：载荷行字段 + `basisAmount`、`rateValue`、`rawAmount`、`sourceRuleType`、`sourceRuleVersionId`、`sourcePriceId`、`sourceRuleComponentId`、`sourceEvidence`（`Map<String, Object>`，可空））、`links`（`BillingFactLineLinkResponseBody`：`sourceLineId`、`targetLineId`、`linkType`、`linkedAmount`））；`BillingFactListResponseBody`（`items`、`nextFromFactId`）；`BillingFactProgressResponseBody`（原 `PendingEconomicFactsResponseBody` 四字段不变）。
   - **删除** `ChannelCostPlanApi` 与它的 11 个请求 / 响应体（`ChannelCostComponentRequestBody`、`ChannelCostScopeRequestBody`、`CreateChannelCostPlanRequestBody`、`CreateChannelCostVersionRequestBody`、`ReviewChannelCostVersionRequestBody`、`SubmitChannelCostVersionRequestBody`、`ChannelCostComponentResponseBody`、`ChannelCostPlanResponseBody`、`ChannelCostScopeResponseBody`、`ChannelCostVersionResponseBody`、`ChannelCostVersionSummaryResponseBody`）——先按第 3 节把它们搬到 channel-connector-api 再删。
4. `package-info.java` 更新；`paytech/billing/compensation-event-catalog.yaml` 改为 `BILLING_FACT_RECORDED`。

## 2. ledger-api（`com.paytech.ledger.api.enums`）

新增 `AutoInstructionType`（5 值，原样）、`AmountSource`（7 值含 `GROSS_AMOUNT`，原样），JavaDoc 改成 ledger 口径（"自动记账指令类型 / 规则行金额来源；`GROSS_AMOUNT` 取 billing 事实头总额，其余按同名 `FactComponentType` 明细行汇总"）。`RuleLineRequestBody` 等现有引用若有 import billing 枚举的，改引 ledger-api 自己的。其余不动。

## 3. channel-connector-api（`com.paytech.channel.connector.api.cost`）

1. 把 billing-api 的 `ChannelCostPlanApi` 与 11 个请求 / 响应体原样搬入 `cost`、`cost/request`、`cost/response` 包（字段、正则、JavaDoc 保留），`@HttpExchange(url = "/channel-connector/v1/channel-cost-plans", accept = "application/json")`；类注释里"Finance 内部开发接口"改"channel-connector 内部开发接口"；`ChannelCostScopeRequestBody.midId` 注释改"本库 t_channel_mid 主键；须存在、归属方案的 Channel 且 entity_no 一致"。
2. 新增 `ChannelCostResolutionApi`（`@HttpExchange("/channel-connector/v1/channel-costs")`，`@PostExchange("/resolutions")`）：Body `ResolveChannelCostRequestBody`（`channelId`、`midId`、`entityNo`、`countryCode`、`currency`、`paymentMethodCode`、`actionType` 正则同范围请求体；`asOfTime` `@NotNull Instant`）；返回 `ChannelCostResolutionResponseBody`（`planCode`、`costVerId`、`costVer`、`contentHash`、`effectiveFrom`、`effectiveTo`、`scopeId`、`scopeNo`、`channelAgreementRef`、`currency`、`components: List<ChannelCostComponentResponseBody>` 复用搬入的响应体并加 `componentId`）。JavaDoc：只读、不算金额；400 `CHANNEL_COST_NOT_RESOLVED` / `CHANNEL_COST_AMBIGUOUS`。错误码放 `enums/ChannelCostErrorCode`（形态照本仓库其他 `*ErrorCode`，如 account-api `BalanceQueryErrorCode`）。
3. 新建 `paytech/channel-connector/compensation-event-catalog.yaml`（`eventTypes: []`，注释说明 B 线引入 `paytech-compensation-event` 只为 XXL 执行器与到期切换 Job，本期无补偿 Handler）。

## 4. 事件目录

`paytech/foundation/paytech-messaging/catalog/events.yaml`：删除 `EconomicFactRecorded` 条目，新增 `BillingFactRecorded`（producer billing、aggregateType `BILLING_FACT`、aggregateVersionField `factVersion`、consumers `[account]`、strictOrder false、payloadSchemaRef `paytech-billing-api/event/BillingFactRecorded/v1`），注释改写：Payload 类 `BillingFactPayload`，含头 + 引用 + 全部行，`businessTime` / `refs` 语义，account 唯一消费方。

## 5. 验证

- `./mvnw -B -o verify -pl billing/paytech-billing-api,ledger/paytech-ledger-api,channel-connector/paytech-channel-connector-api -am`。
- Foundation 事件目录校验：`./mvnw -B -o verify -pl foundation/paytech-messaging -am`（`YamlEventCatalogAutoTest`）。
- 不要编译 billing-core / channel-connector-core / account / ledger 的 core、app、test（预期失败）。

## 6. 报告

`REQ/tasks/T0-report.md`：每个新增 / 改名 / 删除的类与字段清单（给 A / B / C 用），`BillingFactPayload` 示例 JSON 一份（MANUAL_ADJUSTMENT，含 `refs` 的 `TICKET` 与 `targetBucket`），events.yaml 前后对照，测试计数。
