# T0 合同线报告（billing-api / ledger-api / channel-connector-api / 事件目录 / 补偿目录）

日期：2026-10-19。执行者：T0 合同线。未执行任何 git 写命令、数据库 SQL 或 Superpowers skill；只改本卡「允许改动的路径」。

## 1. 已读规范与设计

- `tasks/00-common.md`、`tasks/T0-contracts.md`
- `technique.md` 第 2.1、2.2、2.6 节；`db/billing/ddl.sql`（`t_billing_fact*` 五表列注释，`ref_type` 21 值顺序）
- `design-discuss/billing/database.md`（引用类型 Owner 表，仅用于 `FactRefType` 注释）
- `AGENTS.md`「接口开发必读与自查」、`agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文）、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（第 1、4、5.6、6、7、9 节）、`agent-docs/examples/WRITE_API_EXAMPLE.md`（全文，含第 11 节）、`agent-docs/UNIT_TEST_GUIDE.md`（第 1–2 节与目录；本线无测试代码改动）
- 形态参照：`paytech-account-api` `BalanceQueryErrorCode`、`paytech-channel-connector-api` `ChannelCandidateQueryApi`、`foundation` `FailException`

本线只改合同，不涉及 Processor / Context / 事务；实现由 A、B、C 线完成。

## 2. 改动文件清单

### 2.1 billing-api（`paytech/billing/paytech-billing-api/src/main/java/com/paytech/billing/api`）

| 动作 | 文件 |
|---|---|
| 新增 | `BillingFactCommandApi.java`、`BillingFactQueryApi.java` |
| 新增 | `enums/BillingFactType.java`、`enums/BillingFactStatus.java`、`enums/FactComponentType.java`、`enums/FactOriginType.java`、`enums/FactRefType.java`、`enums/SourceRuleType.java`、`enums/LineLinkType.java`、`enums/CalculationMethod.java` |
| 修改 | `enums/FactPartyRole.java`（删 `PLATFORM`，加 `CASH_ACCOUNT`、`FX_ACCOUNT`）、`enums/FactLineDirection.java`（仅注释：表名、方向由付方 / 收方决定）、`package-info.java` |
| 新增 | `fact/BillingFactPayload.java`、`fact/BillingFactRefPayload.java`、`fact/BillingFactLinePayload.java` |
| 新增 | `request/SubmitBillingFactRequestBody.java`、`request/BillingFactLineRequestBody.java`、`request/BillingFactRefRequestBody.java`、`request/ApproveBillingFactRequestBody.java` |
| 新增 | `response/BillingFactResponseBody.java`、`response/BillingFactLineResponseBody.java`、`response/BillingFactRefResponseBody.java`、`response/BillingFactPricingResponseBody.java`、`response/BillingFactLineLinkResponseBody.java`、`response/BillingFactListResponseBody.java`、`response/BillingFactProgressResponseBody.java` |
| 删除 | `EconomicFactCommandApi.java`、`ChannelCostPlanApi.java` |
| 删除 | `enums/EconomicsType.java`、`enums/EconomicFactStatus.java`、`enums/AmountSource.java`、`enums/AutoInstructionType.java` |
| 删除 | `fact/EconomicFactPayload.java`、`fact/EconomicFactLinePayload.java` |
| 删除 | `request/SubmitEconomicFactRequestBody.java`、`request/EconomicFactLineRequestBody.java`、`request/ApproveEconomicFactRequestBody.java` 与 6 个 `ChannelCost*` / `*ChannelCost*` 请求体 |
| 删除 | `response/EconomicFactResponseBody.java`、`response/EconomicFactLineResponseBody.java`、`response/EconomicFactListResponseBody.java`、`response/PendingEconomicFactsResponseBody.java` 与 5 个 `ChannelCost*` 响应体 |
| 修改 | `paytech/billing/compensation-event-catalog.yaml`：`ECONOMIC_FACT_RECORDED` → `BILLING_FACT_RECORDED` |

### 2.2 ledger-api（`paytech/ledger/paytech-ledger-api/src/main/java/com/paytech/ledger/api`）

| 动作 | 文件 |
|---|---|
| 新增 | `enums/AmountSource.java`（7 值原样，含 `GROSS_AMOUNT`）、`enums/AutoInstructionType.java`（5 值原样） |
| 修改 | `package-info.java`（渠道成本合同改指 channel-connector-api，登记两个新枚举） |

`RuleLineRequestBody` 等原本就不 import billing 枚举（只用 String），未改。

### 2.3 channel-connector-api（`paytech/channel-connector/paytech-channel-connector-api`）

| 动作 | 文件 |
|---|---|
| 新增（搬入） | `cost/ChannelCostPlanApi.java`；`cost/request/` 6 个：`ChannelCostComponentRequestBody`、`ChannelCostScopeRequestBody`、`CreateChannelCostPlanRequestBody`、`CreateChannelCostVersionRequestBody`、`ReviewChannelCostVersionRequestBody`、`SubmitChannelCostVersionRequestBody`；`cost/response/` 5 个：`ChannelCostComponentResponseBody`、`ChannelCostPlanResponseBody`、`ChannelCostScopeResponseBody`、`ChannelCostVersionResponseBody`、`ChannelCostVersionSummaryResponseBody` |
| 新增 | `cost/ChannelCostResolutionApi.java`、`cost/request/ResolveChannelCostRequestBody.java`、`cost/response/ChannelCostResolutionResponseBody.java`、`enums/ChannelCostErrorCode.java` |
| 修改 | `pom.xml`（仅 `<description>` 文案） |
| 新增 | `paytech/channel-connector/compensation-event-catalog.yaml`（`eventTypes: []`） |

### 2.4 事件目录

| 动作 | 文件 |
|---|---|
| 修改 | `paytech/foundation/paytech-messaging/catalog/events.yaml` |
| 修改（主会话授权） | `paytech/foundation/paytech-messaging/src/test/java/com/paytech/foundation/messaging/auto/catalog/YamlEventCatalogAutoTest.java`：第 44、52 行事件名 `EconomicFactRecorded` → `BillingFactRecorded`，其余断言与内联样例不动 |

## 3. 前后对照（给 A / B / C 线）

### 3.1 类型改名与迁移

| 旧（billing-api） | 新 | 说明 |
|---|---|---|
| `EconomicFactCommandApi` | `BillingFactCommandApi` + `BillingFactQueryApi` | 写 / 读分两个接口，同 base URL `/billing/v1/facts` |
| `enums.EconomicsType` | `enums.BillingFactType` | 7 → 12 值 |
| `enums.EconomicFactStatus` | `enums.BillingFactStatus` | 值不变 |
| `enums.AmountSource` | `com.paytech.ledger.api.enums.AmountSource` | 值与代码不变；billing 行组件改用 `FactComponentType` |
| `enums.AutoInstructionType` | `com.paytech.ledger.api.enums.AutoInstructionType` | 值不变 |
| `fact.EconomicFactPayload` | `fact.BillingFactPayload` | 见 3.3 |
| `fact.EconomicFactLinePayload` | `fact.BillingFactLinePayload` | 删 `partyType / partyId`，加 `targetBucket` |
| — | `fact.BillingFactRefPayload` | `refType`、`refId` |
| `request.SubmitEconomicFactRequestBody` | `request.SubmitBillingFactRequestBody` | 见 3.4 |
| `request.EconomicFactLineRequestBody` | `request.BillingFactLineRequestBody` | `feeTypeCode` → `componentType`；加 `targetBucket` |
| `request.ApproveEconomicFactRequestBody` | `request.ApproveBillingFactRequestBody` | 字段不变 |
| `response.EconomicFactResponseBody` | `response.BillingFactResponseBody` | 见 3.5 |
| `response.EconomicFactLineResponseBody` | `response.BillingFactLineResponseBody` | 见 3.5 |
| `response.EconomicFactListResponseBody` | `response.BillingFactListResponseBody` | 字段不变 |
| `response.PendingEconomicFactsResponseBody` | `response.BillingFactProgressResponseBody` | 四字段不变 |
| `ChannelCostPlanApi` 与 11 个 Body（`com.paytech.billing.api[.request/.response]`） | `com.paytech.channel.connector.api.cost[.request/.response]` | 同名类，见 3.6 |
| 补偿类型 `ECONOMIC_FACT_RECORDED` | `BILLING_FACT_RECORDED` | `event_key = factId:factVersion` |
| 事件 `EconomicFactRecorded` / 聚合 `ECONOMIC_FACT` | `BillingFactRecorded` / `BILLING_FACT` | `BillingFactPayload.EVENT_TYPE / AGGREGATE_TYPE` |

### 3.2 新增与调整的枚举

| 枚举 | 值（声明顺序） |
|---|---|
| `BillingFactType` | PAYMENT、REFUND、DUPLICATE_CAPTURE、DUPLICATE_COMPENSATION、DISPUTE、COST_ADJUSTMENT、CHANNEL_SETTLEMENT、PAYOUT_SETTLED、TAX_PAID、MANUAL_ADJUSTMENT、FX_SELL、FX_BUY |
| `BillingFactStatus` | DRAFT、APPROVED、SUPERSEDED |
| `FactComponentType` | CLIENT_PRINCIPAL、CLIENT_FEE、SELLER_TAX、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、CHANNEL_COST、CHANNEL_COST_SETTLED、DUPLICATE_AMOUNT、PAYOUT_FEE、PAYOUT_AMOUNT、SETTLEMENT_AMOUNT、TAX_PAYMENT_AMOUNT、FX_SOLD_AMOUNT、FX_BOUGHT_AMOUNT、FX_FEE |
| `FactOriginType` | AUTO、MANUAL |
| `FactRefType` | ORDER、ORDER_CONTEXT、PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_CHANNEL_ACCOUNT、MID、REFUND_REQUEST、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE、DISPUTE_CASE、TAX_SNAPSHOT、CLEARING_ACCOUNT、SETTLEMENT_BATCH、SETTLEMENT_ITEM、CASH_ACCOUNT、PAYOUT、TAX_PAYMENT、TICKET、FX_DEAL、FX_QUOTE（与 DDL 注释同序） |
| `SourceRuleType` | COMMERCIAL_PRICE、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、TAX_RULE、CHANNEL_COST、FX_RATE |
| `LineLinkType` | REVERSAL、ADJUSTMENT |
| `CalculationMethod` | PERCENTAGE、FIXED、TIERED_WHOLE_BASE、TIERED_PROGRESSIVE、PASS_THROUGH |
| `FactPartyRole` | CLIENT、LOCAL_PARTNER、CHANNEL、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY、CASH_ACCOUNT、FX_ACCOUNT、CONSUMER（删 PLATFORM） |
| `FactLineDirection` | CHARGE、REVERSAL、ADJUSTMENT（不变） |
| `ChannelCostErrorCode`（channel-connector-api，`CodeMessageEnum`） | CHANNEL_COST_NOT_RESOLVED、CHANNEL_COST_AMBIGUOUS |

`FactRefType` Owner 注释：ORDER / ORDER_CONTEXT / REFUND_REQUEST → acquiring；PAYMENT_INTENT / PAYMENT_ATTEMPT / CHANNEL_REFUND / DUPLICATE_ITEM / DISPUTE_NOTICE → payment；PAYMENT_CHANNEL_ACCOUNT / MID → channel-connector；DISPUTE_CASE → dispute；TAX_SNAPSHOT / TAX_PAYMENT → tax；CLEARING_ACCOUNT / SETTLEMENT_BATCH / SETTLEMENT_ITEM / CASH_ACCOUNT → reconciliation；PAYOUT → payout；TICKET → 调用方工单；FX_DEAL / FX_QUOTE → fx。讨论稿把 MID、PAYMENT_CHANNEL_ACCOUNT 记为 Payment，本仓库实际表 Owner 是 channel-connector，按实际写。

### 3.3 `BillingFactPayload` 字段

| 字段 | Java 类型 | 变化 |
|---|---|---|
| factId、factVersion、supersedesFactId、parentFactId | Long | 不变 |
| factType、status、sourceSystem、sourceEventKey、sourcePayloadHash、entityNo、currency | String | 不变 |
| originType | String | 新增 |
| sourceVersion | Long | 不变 |
| businessTime | Instant | 新增（替代 `paymentSucceededTime`） |
| recordedTime、balanceAvailableTime | Instant | 不变 |
| clientId、localPartnerId、financeEntityId | Long | 不变（clientId 资金类可空） |
| grossAmount | BigDecimal | 不变 |
| refs | `List<BillingFactRefPayload>` | 新增 |
| lines | `List<BillingFactLinePayload>` | 行：lineNo（Integer）、componentType、payerRole、payerId、payeeRole、payeeId、direction、postedAmount（BigDecimal）、targetBucket（新增） |
| paymentSucceededTime、partnerAgreementOeId、channelAccountId、行 partyType / partyId | — | 删除 |

### 3.4 接口与请求体

| 接口方法 | Method / Path | 变化 |
|---|---|---|
| `BillingFactCommandApi.submitBillingFact` | `POST /billing/v1/facts` | 原 `POST /billing/v1/economic-facts`；返回 `ResponseEntity<BillingFactResponseBody>` |
| `BillingFactCommandApi.approveBillingFact` | `POST /billing/v1/facts/{factId}/approve` | 路径改 |
| `BillingFactQueryApi.getBillingFact` | `GET /billing/v1/facts/{factId}` | 路径改 |
| `BillingFactQueryApi.listBillingFacts` | `GET /billing/v1/facts?entityType&entityId&currency&fromFactId&limit` | 参数 `bookEntityType / bookEntityId` 改名，正则不变 |
| `BillingFactQueryApi.getBillingFactProgress` | `GET /billing/v1/facts/progress?entityType&entityId&currency` | 原 `/pending` |
| `BillingFactQueryApi.listBillingFactsByRef` | `GET /billing/v1/facts/by-ref?refType&refId&fromFactId&limit` | 新增；refType `[A-Z][A-Z_]{0,31}`，refId `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`，fromFactId 可选 `0|[1-9][0-9]{0,18}` 缺省 0（返回 factId 大于游标的事实，nextFromFactId 为本页最后一条），limit `[1-9][0-9]{0,2}`（1–200 由实现校验） |

`SubmitBillingFactRequestBody`：删 `partnerAgreementOeId`、`channelAccountId`；`clientId` 去掉 `@NotBlank`（注释写明 MANUAL_ADJUSTMENT 必填、COST_ADJUSTMENT 取父事实）；新增 `refs`（`@Size(max = 10)`，`List<@Valid @NotNull BillingFactRefRequestBody>`，可为空）。其余字段与正则不变。

`BillingFactLineRequestBody`：`feeTypeCode` → `componentType`（正则不变）；新增 `targetBucket`（可空，`PENDING|AVAILABLE`）；角色注释改为 9 值。

### 3.5 响应体

`BillingFactResponseBody`：载荷全部字段（ID / 版本 / 金额为 String，时间为 Instant）+ `makerId`、`checkerId`、`approvedTime`、`publishedAt` + `pricing`（`BillingFactPricingResponseBody`，可空）+ `refs`（`List<BillingFactRefResponseBody>`）+ `lines`（`List<BillingFactLineResponseBody>`）+ `links`（`List<BillingFactLineLinkResponseBody>`）。相对旧响应：`sourceReference` → `sourceEventKey`，`contentHash` → `sourcePayloadHash`；删 `partnerAgreementOeId`、`channelAccountId`、`paymentSucceededTime`；加 `originType`、`sourceVersion`、`businessTime`、`recordedTime`、`balanceAvailableTime`、`pricing`、`refs`、`links`。

`BillingFactLineResponseBody`：lineNo、componentType（原 feeTypeCode）、payerRole、payerId、payeeRole、payeeId、direction、postedAmount（原 amount）、targetBucket、basisAmount、rateValue、rawAmount、sourceRuleType、sourceRuleVersionId、sourcePriceId、sourceRuleComponentId（均 String）、sourceEvidence（`Map<String, Object>`）。

`BillingFactPricingResponseBody`：clientAgreementId、partnerAgreementOeId、commercialContextTime（Instant）、consumerCountryCode、paymentMethod、industryCode、mccCode、cardNetwork、fundingType、cardLevel、authenticationClass、commercialPriceId、settlementBusinessDays、businessCalendarCode、settlementTimezone、cutoffSecondOfDay。

`BillingFactLineLinkResponseBody`：sourceLineId、targetLineId、linkType、linkedAmount。

### 3.6 channel-connector-api

| 接口方法 | Method / Path |
|---|---|
| `ChannelCostPlanApi`（7 个方法，签名与 billing 原版相同） | 前缀 `/channel-cost-plans/v1` → `/channel-connector/v1/channel-cost-plans` |
| `ChannelCostResolutionApi.resolveChannelCost` | `POST /channel-connector/v1/channel-costs/resolutions`，Body `ResolveChannelCostRequestBody`，返回 `ChannelCostResolutionResponseBody` |

搬入时的语义改动（字段与正则全部保留）：
- `ChannelCostPlanApi` 类注释："Finance 内部开发接口"改"channel-connector 内部开发接口"。原"MID、Channel… 不向 Payment 核对"改为 MID 须在本库 `t_channel_mid` 存在、归属方案 Channel 且 entity_no 一致。新增一句说明到期切换 Job 负责未到期 APPROVED 版本。`createChannelCostVersion` 错误说明"币种未启用或金额超精度"改为"MID 不存在或不属于方案 Channel / OE、金额小数位超过 8 位"。
- `ChannelCostScopeRequestBody.midId` 注释按卡改写；`currency` 注释由"必须在 Finance 币种规则中为 ACTIVE"改为"只校验格式，精度归整由 billing 计算时完成"（technique 2.1：本库无币种表）。
- `ChannelCostComponentRequestBody` 三处"不超过币种精度"改为"小数位不超过 8 位"。
- `ChannelCostVersionResponseBody.contentHash` 注释 Schema 改 `CODE-MOR:CHANNEL_CONNECTOR:CHANNEL_COST:1`。
- `ChannelCostScopeResponseBody.midId` 注释改为本库 MID 主键。
- `ChannelCostComponentResponseBody` 新增首字段 `componentId`（String）。版本详情响应也会带它，B 线需在详情组装时一并填。

`ResolveChannelCostRequestBody`：channelId、midId、entityNo、countryCode、currency、paymentMethodCode、actionType（全部 `@NotBlank`，正则同范围请求体），asOfTime（`@NotNull Instant`）。`ChannelCostResolutionResponseBody`：planCode、costVerId、costVer、contentHash、effectiveFrom、effectiveTo、scopeId、scopeNo（Integer，同范围响应体）、channelAgreementRef、currency、components。

### 3.7 下游受影响模块（按 import 统计，供 A / B / C 认领）

account-core、account-test、billing-core、billing-app、billing-test、ledger-core、ledger-app、ledger-test 引用了被删或改名的 billing-api 类型。operations-console-bff 没有引用。

## 4. `BillingFactPayload` 示例 JSON（MANUAL_ADJUSTMENT）

```json
{
  "factId": "7310455688204288001",
  "factVersion": "1",
  "supersedesFactId": null,
  "parentFactId": null,
  "factType": "MANUAL_ADJUSTMENT",
  "originType": "MANUAL",
  "status": "APPROVED",
  "sourceSystem": "OPERATIONS",
  "sourceEventKey": "ADJ-20261019-0001",
  "sourceVersion": "1",
  "sourcePayloadHash": "3b7f0c0f8c1d4a5e9e2b6d7a1c3f5e8b9a0d2c4e6f8a1b3c5d7e9f0a2b4c6d8e",
  "businessTime": "2026-10-19T08:15:30.123456Z",
  "recordedTime": "2026-10-19T08:15:30.123456Z",
  "balanceAvailableTime": null,
  "clientId": "100200300",
  "entityNo": "AE001",
  "localPartnerId": null,
  "financeEntityId": null,
  "currency": "USD",
  "grossAmount": "25.00",
  "refs": [
    { "refType": "TICKET", "refId": "OPS-4821" }
  ],
  "lines": [
    {
      "lineNo": "1",
      "componentType": "CLIENT_PRINCIPAL",
      "payerRole": "OPERATING_ENTITY",
      "payerId": "AE001",
      "payeeRole": "CLIENT",
      "payeeId": "100200300",
      "direction": "ADJUSTMENT",
      "postedAmount": "25.00",
      "targetBucket": "AVAILABLE"
    }
  ]
}
```

信封：eventType `BillingFactRecorded`，schemaVersion 1，aggregateType `BILLING_FACT`，aggregateId = factId，aggregateVersion = factVersion，occurredAt = recordedTime，sourceService billing。

## 5. events.yaml 前后对照

```diff
-  # 唯一经济事实（C1，design/04 §5）：account 维护 Owner 明细账，Payload 含头与全部明细行。
-  # 第 11a 步按实现核对：aggregateId 为 factId，aggregateVersion 取 t_economic_fact.fact_version（替代链递增）；Payload 字段定义见
-  # paytech-billing-api 的 com.paytech.billing.api.fact.EconomicFactPayload。不同事实之间无顺序依赖，故 strictOrder=false。
-  # 2026-10-19 明细账与控制科目重构（决定 2026-10-18）：唯一消费方为 account 的 EconomicFactRecordedBalanceHandler
-  # （account.billing.p0..p3，SHARED 事务）；ledger 不再消费本事件，改消费 AccountPosted，并经 billing-api 读取事实。
-  # Payload 头新增可选 balanceAvailableTime（UTC RFC 3339，仅 PAYMENT 且 billing 已计算时非空），Schema 版本保持 1。
-  - eventType: EconomicFactRecorded
+  # 唯一计费事实（决定 2026-10-19 计费事实引擎；需求 billing-计费事实模型重构与渠道成本迁出，替代原经济事实事件）：Payload 为
+  # paytech-billing-api 的 com.paytech.billing.api.fact.BillingFactPayload，含头 + 外部引用（refs）+ 全部明细行（每行付方 / 收方）。
+  # aggregateId 为 factId，aggregateVersion 取 t_billing_fact.fact_version（替代链递增），occurredAt 为批准（AUTO 为登记）时间。
+  # businessTime 是 ledger 过账日期来源；refs 的 CLEARING_ACCOUNT 是 ledger 渠道账户 Owner 来源。不同事实之间无顺序依赖，故 strictOrder=false。
+  # 唯一消费方为 account（SHARED 事务）维护 Owner 明细账；ledger 不消费本事件，改消费 AccountPosted 并经 billing-api 读取事实。
+  - eventType: BillingFactRecorded
     schemaVersion: 1
     producer: billing
-    aggregateType: ECONOMIC_FACT
+    aggregateType: BILLING_FACT
     aggregateVersionField: factVersion
     consumers: [account]
     strictOrder: false
-    payloadSchemaRef: paytech-billing-api/event/EconomicFactRecorded/v1
+    payloadSchemaRef: paytech-billing-api/event/BillingFactRecorded/v1
```

## 6. 写接口示例第 11 节自查

| 项 | 结果 |
|---|---|
| API 与 Body 最小合同、class、字段注释、Hibernate Validator | 符合。全部 Body 为 Lombok `@Getter/@Setter` class，字段中文 JavaDoc，嵌套 `List<@Valid @NotNull …>`；接口方法 `@param/@return` 写 HTTP 语义。枚举 Code 字段一律 String。 |
| Controller / Converter 仅适配；Processor 位置 | 不适用：本线只改合同，由 A、B 线实现。 |
| Processor 查询本模块 Repository | 不适用，同上。 |
| Context | 不适用。 |
| Service 边界 | 不适用。 |
| 本地原子写入与事务位置 | 不适用；合同注释与 technique 2.1 一致（批准同事务登记补偿任务，命中接口只读无事务）。 |
| Repository 与行数检查 | 不适用。 |
| 锁 | 不适用。 |
| 原请求比较后直接响应 | 合同保留"同来源引用同内容 200 返回原事实、内容不同 400"与"同一复核人重复批准返回原事实"。 |
| Gateway 幂等与业务防重区分 | 符合：没有 `Idempotency-Key` 参数或字段。 |
| View / DataObject 不泄露 | 符合：api 模块只有 HTTP DTO、载荷与枚举，没有持久化类型。 |
| 公共包复用、无多余抽象 | 符合：错误码照 `BalanceQueryErrorCode` 形态；没有新增依赖，api pom 只改描述。 |
| 测试验证 | 见第 7 节；api 模块没有自身测试，本线不改测试。 |

其他规范核对：相邻枚举常量之间空一行、每个常量一句 JavaDoc；类型名无版本后缀；顶层 DTO 用 `*RequestBody` / `*ResponseBody`。

## 7. 验证

```text
cd paytech
./mvnw -B -o verify -pl billing/paytech-billing-api,ledger/paytech-ledger-api,channel-connector/paytech-channel-connector-api -am
  -> BUILD SUCCESS；-am 上游 paytech-common-core 等 130 个测试全部通过，0 失败；三个 api 模块本身没有测试
  -> 构建出的 jar 中没有旧类残留：billing-api 27 个 class，没有 Economic* / ChannelCost* / AmountSource / AutoInstructionType

./mvnw -B -o verify -pl foundation/paytech-messaging -am
  -> 首轮 BUILD FAILURE：YamlEventCatalogAutoTest.test_normalFlow_packagedCatalog:44 写死旧事件名
  -> 经主会话授权改该测试两行后重跑：BUILD SUCCESS；paytech-messaging 73/73 通过，-am 上游模块全部通过

补游标后重跑三个 api 模块：BUILD SUCCESS，上游 130 个测试通过
```

范围内残留检查：

```text
grep -rn "EconomicFact\|economic_fact\|EconomicsType\|AmountSource" <T0 全部路径>
  -> 只剩 ledger-api 自己的 AmountSource（允许）及 package-info 对它的引用
```

`ledger-api` 的 `LedgerErrorCode` 注释与默认消息里仍有"MANUAL_ADJUSTMENT 经济事实"的中文措辞。卡片要求 ledger-api 其余不动，而且 ledger 测试可能断言这条消息，所以留给 C 线决定。

## 8. 偏离设计与待确认事项

1. **已解决：Foundation 事件目录测试。** 测试写死了打包目录的事件名单。经主会话授权，已把第 44、52 行事件名改为 `BillingFactRecorded`，现 73/73 通过。同文件第 30–37、90–94 行，以及 `DomainEventCodecAutoTest`、`DomainEventHandlerRegistryAutoTest` 中的 `EconomicFactRecorded` 是内联样例，按指示未改。
2. **已解决：按引用查询的游标。** 经主会话批准，`listBillingFactsByRef` 新增可选参数 `fromFactId`，正则同列表接口，缺省 0。JavaDoc 写明返回 factId 大于游标的事实，`nextFromFactId` 为本页最后一条。technique.md 2.2 已由主会话同步。A 线需在 Controller 与查询中实现该参数。
3. **`BillingFactPricingResponseBody` 字段取舍。** 没有放 `fact_id`（与头部 factId 重复）和 `create_time`（技术列）。`commercialContextTime` 用 `Instant`，与其他响应体的时间字段一致，JSON 上同样是 RFC 3339 字符串。其余列都是 String。
4. **`BillingFactResponseBody.links` 的语义** 写为"以本事实明细行为目标的行链接"，technique 只说"本期恒为空列表"。A 线实现时按此口径。
5. **新增 `BillingFactRefResponseBody`。** 卡片没有给响应中 `refs` 的元素类型，本线建了这个类（refType、refId），与请求体和载荷对称。
6. **搬入的渠道成本 JavaDoc 有语义更新**，见 3.6。卡片要求"JavaDoc 保留"，但 technique 2.1 已改变 MID 与币种校验。本线只改与之冲突的句子，字段与正则全部保留。
7. **测试库重建：** 本线没有改任何 `*-test/src/test/resources/init/ddl.sql`，无需重建。
