# channel-connector：渠道身份就绪查询 · 技术方案

> 状态：**第 1—5 节为阶段 2 评审稿（2026-10-30，只读核对，未改代码、未执行 SQL）**。需求见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。阶段 3 方案在第 6 节起追加。

## 1. 核对范围与证据

| 范围 | 证据（main `331d8e0e`） |
|---|---|
| 运行期选 MID | `channel-connector` `QueryMidCandidatesProcessor`、`MidCapabilityContext`、`ChannelCandidateQueryApi`（`POST /channel-connector/v1/mid-candidates`）；`payment` `PaymentChannelClientImpl`、`RouteServiceImpl` |
| 能力目录 | `channel-connector-app` `application.yml` `capability-definitions` |
| 门禁 ④ | `commercial` `CapabilityPrerequisiteContext.channelIdentity`、`CheckCapabilityPrerequisitesProcessor`、`RecheckDueCapabilityPrerequisitesProcessor` |
| Client MCC | `client-master` `t_client_business_profile` / `t_client_business_classification`、`paytech-client-master-api` 各接口 |
| 页面 | `operations-console-web` `commercial/capabilityGate.ts`、`commercial/ProductCapabilitySections.tsx`、`pages/ChannelMids.tsx`、`i18n/*/commercial.json`；`operations-console-bff` `CommercialAgreementModels` |
| data-query | REQ-01 `db/data-query-templates.sql` 与 runbook 第 8 节；mor-test `mor_platform.t_data_source` |
| mor-test 数据 | 只读 SELECT（`mor_commercial`、`mor_channel_connector`、`mor_client_master`） |

## 2. 发现

| # | 发现 | 影响 |
|---|---|---|
| F-01 | 运行期候选：OE 下 MID ACTIVE、渠道账号 ACTIVE、渠道 ACTIVE；能力行 ENABLED，支付方式与 MCC 精确，体系 / 国家 / 币种精确或 `*`；**请求维度为空时只匹配 `*`**。只要 MID 有任一能力代码命中就成为候选 | 门禁时点不知道体系和国家，**不能直接调现有候选接口**（传空只会匹配 `*`，会把配了具体国家的 MID 误判为不就绪）；需新 Processor 复用同一规则，但"未知维度 = 任意值" |
| F-02 | payment 选路：自动请款要求候选支持 `PAY`，手动请款要求 `AUTHORIZE`（能力 `ACTION.PAY` / `ACTION.AUTHORIZE` 为 true），再按金额上下限过滤 | 需求 B2 修正：收款动作是 `ACTION.PAY` 或 `ACTION.AUTHORIZE` 任一为 true（原写 `SALE` 不存在）；金额上下限不进门禁（运行期因素） |
| F-03 | payment 以支付尝试冻结的支付方式代码精确查询（`criteria.paymentMethodCode`），钱包类不借用 CARD 能力 | 假设 A1 成立：APPLE_PAY 等必须有自己的能力行 |
| F-04 | `t_routing_policy*` 无任何生产代码读取 | Q-1 已定：门禁不加路由策略条件，修订决定 |
| F-05 | ④ 在 `CapabilityPrerequisiteContext.channelIdentity` 固定 `SKIPPED`；① 资格由 `CheckCapabilityPrerequisitesProcessor.qualify` 在**事务外**批量调用 compliance，失败时 ① `UNKNOWN`、整次不改状态（`anyUnknown`） | ④ 按同一模式接入：事务外批量查 channel-connector，结果传入上下文；失败 → `UNKNOWN`，无需新增状态语义 |
| F-06 | ② 的 `TermCoverage` 已能按开通项算出价格**未覆盖**的币种（`PRICE_NOT_COVERED` 参数 `currencies`） | ④ 的币种可从同一覆盖结果取"已覆盖币种"，不另查价格 |
| F-07 | **client-master 没有服务间接口能按 Client 取 MCC**：`ClientMasterCheckoutApi` 只有结账候选（需消费者国家 / 币种等）与 Party 资料；`ClientOnboardingAdminApi` 是运营后台接口。分类在 `t_client_business_classification`，经 `t_client_business_profile` 关联 Client，`primary_slot` 标主分类 | 需求第 7 节写的"client-master 无改动"不成立：需新增一个只读服务间查询（S-03）；或另找来源 |
| F-08 | 运营端"还差什么"按原因码 + 参数渲染，跳转由 `lineLink` 按原因码决定（Client / OE / 定价 / 重新检查）；UNKNOWN 已显示"重新检查" | 新原因只需加文案键与一个"去 MID 页"跳转种类；语言包需要发布 |
| F-09 | MID 页 `/channel/mids` 有 OE 筛选表单，但**不读 URL 参数** | 小改：支持 `?entityNo=` 预填筛选；决定 3.5 的 `/channels/mids/new?entityNo=` 路径需修订 |
| F-10 | operations-console-bff 对前置结论按字符串透传（`reasonCode`、`params`） | BFF 零改动 |
| F-11 | 重查作业 `capabilityPrerequisiteRecheck`：每 5 分钟扫等待 / 门禁暂停；参数 `includeEnabled` 时另扫 ENABLED（每日） | 发布后"全量重查一次" = 发布后约 5 分钟自动覆盖等待 / 暂停；ENABLED 由运维手动触发一次带 `includeEnabled` 的作业，**无需新代码** |
| F-12 | data-query：mor-test 已有数据源 `MOR_CHANNEL_CONNECTOR_REPLICA`；REQ-01 先例为模板正文文本交付、发布时运营后台登记（单独授权），参数语法 `#{name}`，列表用逗号分隔字符串 | `GOLIVE_MID_READY` 照此交付；列表参数用 `FIND_IN_SET` |
| F-13 | 配置损坏（MID 与账号的渠道 / OE 不一致）时候选查询抛错，不当作"无候选" | 就绪查询保持同样行为 → commercial 得到 5xx → ④ `UNKNOWN`；需在运营端可见（日志 + 原因"暂时无法核对"） |
| F-14 | mor-test 读数：AE001 两个 ACTIVE CARD MID 的 USD × 5999 能力都有 `ACTION.PAY`；`*` × 5816 那条只有 `ACTION.AUTHENTICATE`；无任何钱包能力行 | 3 条 CARD ENABLED 仍就绪；DEMO-CL-001 APPLE_PAY 将被暂停（Q-2 已接受） |

## 3. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 就绪查询接口 | 无；只有单条件候选查询 `POST /mid-candidates`（F-01） | 按 OE 批量判定支付方式 × 币种 × MCC 是否就绪并给原因 | 未实现；现有接口语义不同 | 新接口（S-01） |
| R2 与运行期同口径 | 规则在 `MidCapabilityContext.matches` 与候选 Processor 的账号 / 渠道过滤 | 同规则 + 未知维度任意 + 收款动作 | 未知维度语义不同、动作判定在 payment | 抽出共用的筛选规则供两处使用（S-02），动作判定按 F-02 |
| R3 commercial ④ | 固定 SKIPPED（F-05） | 真实查询；UNKNOWN 不改状态 | 客户端、币种、MCC 来源缺 | 事务外批量查询；币种用 ② 覆盖结果（F-06）；MCC 见 S-03 |
| R4 全量重查 | 作业已支持（F-11） | 发布后一次 | 无 | 发布步骤：手动触发 `includeEnabled` 一次 |
| R5 `GOLIVE_MID_READY` | 无 | 与 R2 同口径的 SQL | 未实现 | 文本交付 + 发布登记（S-06） |
| R6 运营页面 | 通用渲染 + 跳转（F-08）；MID 页不读 URL（F-09） | 新原因文案、去 MID 页 | 文案键、跳转种类、URL 预填 | 小改 ops-web；BFF 零改动（F-10） |
| R7 决定修订 | 决定 3.2 含路由策略条件；3.5 路径不对 | 删条件、改路径 | 文档 | 阶段 3 一并修订 |
| B5 Client MCC | 无服务间来源（F-07） | 主 MCC | client-master 需新接口 | S-03 |

## 4. 逐系统影响

| 系统 | 职责 | 影响点 | 改代码 / 模型 / 配置 | 依赖与兼容风险 |
|---|---|---|---|---|
| **channel-connector** | MID 与能力的 Owner | 新就绪查询接口 + Processor；抽共用筛选规则（候选查询改为调用它，行为不变） | 代码、`-api`；不改表 | 候选查询是 payment 运行期路径：重构须有回归测试兜底（现有候选查询测试 + 新增对照测试） |
| **commercial** | 门禁 Owner | ④ 真实查询：channel-connector 客户端、client-master 客户端（MCC）、币种取覆盖结果；新原因码；配置（地址、超时） | 代码、配置 | ④ 变严格：存量 ENABLED 中不就绪的会在复核时暂停（Q-2 已接受）；channel-connector 不可用只会 UNKNOWN |
| **client-master** | Client 分类 Owner | 新增只读服务间查询：按 Client 返回业务分类（主 MCC，行业） | 代码、`-api`；不改表 | **相对需求第 7 节新增的系统**（F-07）；纯只读 |
| **operations-console-web** | 页面 | 新原因文案（zh-CN / en）、去 MID 页跳转、MID 页读 `?entityNo=` | 代码、i18n | 语言包需发布一次（运营命名空间 `commercial`、`channel`） |
| **operations-console-bff** | 聚合 | 无（透传，F-10） | 否 | — |
| **Platform data-query** | 模板登记 | 登记 `GOLIVE_MID_READY`（数据源已存在） | 配置（发布时） | 单独授权 |
| **Platform Gateway / 注册** | — | 0：新接口都是服务间内部调用 | 否 | — |
| **payment** | 运行期选路 | 无（候选查询行为不变） | 否 | 依赖 channel-connector 重构不改变候选结果 |
| **test-environment** | — | commercial 新增出站地址键（channel-connector、client-master；可能已有） | 配置 | 阶段 6 核对现网 env |
| **文档** | — | 决定 3.2 / 3.5 修订；channel-connector / client-master / commercial 接口文档；CURRENT_IMPLEMENTATION | 文档 | — |

## 5. 待决

| # | 问题 | 建议 |
|---|---|---|
| S-01 | 接口形状：决定写的是 `GET …/readiness?entityNo&paymentMethodCode&currency&mccCode`（单条）。commercial 一次核对一个合同的多个开通项 × 多币种 | 改为**批量**：一个 OE + 多组（支付方式、币种、MCC），一次返回逐组结论；路径与命名在阶段 3 按 DEVELOPMENT_CONVENTIONS 定稿 |
| S-02 | 复用方式：直接调现有候选接口（语义不对，F-01），还是抽出共用规则 | **抽共用规则**：账号 / 渠道过滤与能力匹配抽成一个领域组件，候选查询与就绪查询都用它；就绪查询对体系 / 国家用"任意"，并要求 `ACTION.PAY` 或 `ACTION.AUTHORIZE`。重构不改变候选查询结果 |
| S-03 | Client MCC 来源（F-07） | **client-master 新增只读服务间接口**按 Client 返回业务分类；④ 用主分类 MCC。备选：只用结账候选接口（需消费者条件，不适用）、commercial 自存 MCC（重复事实，不建议） |
| S-04 | Client 有多个分类（主 + 次）时 | 本期**只用主分类**（与需求 B5 一致）；次分类如何参与结账另议 |
| S-05 | 开通项还没有任何生效价格（② 不满足）时 ④ 怎么判 | ④ 记 `SKIPPED`，原因 `AWAITING_PRICE`（等价格确定后再核对币种），避免"还差什么"重复报错；② 已说明缺价格 |
| S-06 | `GOLIVE_MID_READY` 的币种：流程变量里没有币种（N3 输出只有 OE、开通项） | 参数：OE、支付方式列表、MCC 必填，**币种列表可选**（空 = 任一币种即可）；最终是否可收款以 N8 开通项生效为准。REQ-07 若能从合同取币种再传入 |
| S-07 | 跳 MID 页的权限 | 运营没有 MID 页面权限时不显示链接（按现有页面能力判断） |
| S-08 | 查询超时 | commercial 调用超时 3 秒（与 compliance 客户端一致），超时 → UNKNOWN |

**规模：** 由 S 调整为 **M**（多出 client-master 接口与 channel-connector 候选规则抽取）。约 5 个系统有改动（channel-connector、commercial、client-master、ops-web、data-query 登记），新增 2 个服务间接口，0 张表、0 个 Gateway 路由。

**主要风险：** S-02 抽取会触碰 payment 运行期的候选查询，须以现有测试 + 新增对照测试保证结果不变；存量 ENABLED 被暂停（Q-2 已接受，发布前只读巡检）。

---

> 第 6 节起为**阶段 3 评审稿（2026-10-30）**：本次方案 / 计划口径，未实现、未执行任何 SQL。

## 6. 一句话规模

本次方案涉及 **4 个系统改代码**（channel-connector、commercial、client-master、operations-console-web）+ data-query 登记 1 条模板；**新增 2 个服务间接口**（channel-connector 就绪查询、client-master 经营分类查询），**修改 0 个已有接口合同**（MID 候选查询行为不变）；**新增 0 张表 / 0 字段，修改 0 张表，索引 0，DDL / 业务 DML / 注册 DML 均 0 行**；Gateway 路由 0；页面设计 **1 张画板**（商务 · 18，在商务 · 14 上做增量，无新页面、无新路由）；运营语言包新增约 18 个文案键。

## 7. 方案总览

```
commercial 门禁（检查前置 / 激活 / 价格版本生效 / 重查作业，同一路径）
  ① 资格            事务外 → compliance（已有）
  ② 合同与价格        本库（已有）→ 同时得出每个支付方式开通项的"已覆盖币种"
  ④ 渠道身份（新）     事务外：
       a. client-master  GET  /client-master/v1/clients/{clientId}/business-classification  → 已确认的主 MCC
       b. channel-connector POST /channel-connector/v1/channel-identities/readiness   → 逐组 ready / 原因
     → 结论传入 CapabilityPrerequisiteContext，与 ①② 一起判定目标执行状态（短事务置位 + 事件，已有）
  任一外部调用失败 → 对应项 UNKNOWN → 本次不改状态（已有 anyUnknown 语义）

上线流程 N5c / N5d → data-query GOLIVE_MID_READY（只读 channel-connector 库，同口径 SQL）
运营页面 → 还差什么：渠道身份一项显示原因 + 去配置 MID / 去 Client / 重新检查
```

## 8. channel-connector

### 8.1 共用筛选（S-02）

- 新增领域服务 `OperatingEntityMidService`（interface + `OperatingEntityMidServiceImpl`），方法 `loadUsableMids(entityNo)`：一次读出该 OE 的渠道账号、MID、渠道、能力行与能力明细，按现有规则过滤"MID / 账号 / 渠道均 ACTIVE"，悬空关联抛 `ErrorException`（原 `QueryMidCandidatesProcessor.eligible` 行为原样搬入）。返回领域对象 `OperatingEntityMids`（可用 MID 列表及其能力行、明细、渠道代码）。
- `QueryMidCandidatesProcessor` 改为：在原有可重复读只读事务内调用 `loadUsableMids`，其余（`MidCapabilityContext.resolve`、投影支持操作 / 金额 / 3DS）不变。**候选结果逐字段不变**，以现有候选测试 + 新增"重构前后对照"用例保证。

### 8.2 就绪判定（新 Processor `QueryChannelIdentityReadinessProcessor`）

在同样的只读可重复读事务内 `loadUsableMids(entityNo)` 一次，然后对每组（支付方式 P、币种 C、MCC M）：

| 步 | 判定 | 不满足时原因 |
|---|---|---|
| 1 | 有可用 MID | `MID_NOT_FOUND` |
| 2 | 某 MID 有 ENABLED 能力行且支付方式 = P | `PAYMENT_METHOD_NOT_SUPPORTED` |
| 3 | 上述能力行中 MCC = M | `MCC_NOT_COVERED` |
| 4 | 上述能力行中币种 = C 或 `*` | `CURRENCY_NOT_SUPPORTED` |
| 5 | 存在某 MID 与某（体系、国家）取值，使 `MidCapabilityContext.resolve(P, M, C, 体系, 国家)` 解析出 `ACTION.PAY` 或 `ACTION.AUTHORIZE` 为 true | `PAYMENT_ACTION_NOT_SUPPORTED` |

第 5 步的（体系、国家）取值集合 = 该 MID 命中第 2—4 步的能力行里出现过的体系值与国家值（`*` 以"未提供"即 null 代入，null 只匹配 `*`，与运行期一致）。这样与运行期"按最精确能力行逐代码取值"完全同口径，不另写一套优先级（AC-05）。能力配置冲突时 `resolve` 抛错 → 整次请求 500 → commercial 记 UNKNOWN（与运行期把配置损坏当错误一致，F-13）。

### 8.3 接口（`paytech-channel-connector-api`，包 `api.readiness`）

```java
@HttpExchange("/channel-connector/v1")
public interface ChannelIdentityReadinessApi {
    /** 判定一个 OE 下多组支付方式 × 币种 × MCC 的渠道身份是否就绪（只读）。 */
    @PostExchange("/channel-identities/readiness")
    ChannelIdentityReadinessResponseBody checkReadiness(@Valid @RequestBody ChannelIdentityReadinessRequestBody requestBody);
}
```

请求（`ChannelIdentityReadinessRequestBody`）：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `entityNo` | string | 是 | `[0-9A-Za-z]{5}`（与候选查询相同） |
| `items` | array | 是 | 1—200 组；同组重复返回同一结论 |
| `items[].paymentMethodCode` | string | 是 | `[A-Z0-9][A-Z0-9_.-]{0,63}` |
| `items[].currency` | string | 是 | `[A-Z]{3}`（精确币种，不接受 `*`） |
| `items[].mccCode` | string | 是 | `[0-9]{4}` |

响应（`ChannelIdentityReadinessResponseBody`）：`entityNo`、`items[]`，每组回显三个入参并加 `ready`（boolean）、`reasonCode`（string，ready=true 时为 null；取值见 8.2）。示例：

```json
POST /channel-connector/v1/channel-identities/readiness
{"entityNo":"AE001","items":[{"paymentMethodCode":"CARD","currency":"USD","mccCode":"5999"},
                             {"paymentMethodCode":"APPLE_PAY","currency":"USD","mccCode":"5999"}]}
200
{"entityNo":"AE001","items":[
  {"paymentMethodCode":"CARD","currency":"USD","mccCode":"5999","ready":true,"reasonCode":null},
  {"paymentMethodCode":"APPLE_PAY","currency":"USD","mccCode":"5999","ready":false,"reasonCode":"PAYMENT_METHOD_NOT_SUPPORTED"}]}
```

错误：入参不合法 422 `VALIDATION_ERROR`（RFC 9457，共享异常处理对 Bean Validation 的固定映射，与 `/mid-candidates` 一致；阶段 4 更正）；配置损坏 500。POST 只因批量入参，接口无副作用。服务间调用，不经 Gateway；内部认证沿用宿主现有方式（与 `/mid-candidates` 相同）。

## 9. client-master：经营分类查询（S-03 / S-04）

```java
@HttpExchange("/client-master/v1")
public interface ClientBusinessClassificationApi {
    /** 查询 Client 已确认的主经营分类（行业与 MCC），供门禁等服务间只读使用。 */
    @GetExchange("/clients/{clientId}/business-classification")
    ClientBusinessClassificationResponseBody getBusinessClassification(@PathVariable("clientId") String clientId);
}
```

- 响应 `ClientBusinessClassificationResponseBody`：`clientId`、`confirmed`（boolean，业务资料的 `classification_confirmed_time` 非空）、`industryCode`、`mccCode`（主分类 `primary_slot = 1`；未确认或无主分类时两者为 null）。
- Client 不存在 404 `CLIENT_NOT_FOUND`；无业务资料按 `confirmed=false`、两码为 null 返回 200（由调用方判 `CLIENT_MCC_MISSING`）。
- 阶段 4 补充：`clientId` 格式非法 400（与同族 `stored-card-setting` 一致）；三次读在一个只读可重复读事务内，避免确认状态与分类行取自不同时刻。commercial 侧 client-master 404 / 失败均记 ④ UNKNOWN。
- 实现：简单查询，Controller 委托一个查询 Service（`ClientBusinessClassificationQueryService` + Impl），读 `t_client_business_profile` + `t_client_business_classification`（同模块表）；不新增 Processor。

## 10. commercial

### 10.1 出站客户端

| 端口（`integration`） | 实现 | 远端 | 失败处理 |
|---|---|---|---|
| `ChannelIdentityReadinessClient` | `ChannelIdentityReadinessClientImpl` | `channel-connector-service` `ChannelIdentityReadinessApi` | 抛 `IntegrationUnavailable` 类异常 → 调用方记 ④ UNKNOWN |
| `ClientClassificationClient` | `ClientClassificationClientImpl` | `client-master-service` `ClientBusinessClassificationApi` | 同上 |

构造方式与现有 compliance / client-master 客户端相同（服务发现、内部令牌、连接 2 秒 / 读取 3 秒，S-08），Bean 在 `CheckoutResolutionConfiguration` 同处定义。无新增配置键。

### 10.2 门禁接入

- `CheckCapabilityPrerequisitesProcessor`（及共用同一判定路径的激活 / 价格生效 / 重查入口）在现有"事务外查资格"之后增加"事务外查渠道身份"：
  1. 只对成员资格 ACTIVE、种类 PAYMENT_METHOD 的开通项；
  2. 取 ② 的覆盖结果得到每个开通项的**已覆盖币种**（`TermCoverage` 增加 `coveredCurrencies(agreementCapabilityId)`，与已有 `uncoveredCurrencies` 同源）；无覆盖币种的不查（S-05）；
  3. 调 client-master 取主 MCC；未确认 → 不再调 channel-connector，④ 直接不满足 `CLIENT_MCC_MISSING`；
  4. 按合同 OE 组装（支付方式、币种、MCC）去重后一次调用就绪查询；
  5. 结果以（开通项 ID → 逐币种结论）传入 `CapabilityPrerequisiteContext`。
- `CapabilityPrerequisiteContext.channelIdentity` 判定：

| 情况 | 结果 | 原因码 | 参数 |
|---|---|---|---|
| 非 PAYMENT_METHOD 种类 | SKIPPED | `NOT_APPLICABLE`（已有） | — |
| 无已覆盖币种 | SKIPPED | `AWAITING_PRICE`（新） | `entityNo` |
| client-master 或 channel-connector 调用失败 | UNKNOWN | `CHECK_UNAVAILABLE`（已有） | — |
| Client 主 MCC 未确认 | UNSATISFIED | `CLIENT_MCC_MISSING`（新） | `clientId` |
| 全部覆盖币种都不就绪 | UNSATISFIED | 不就绪币种中字典序第一个的原因（`MID_NOT_FOUND` / `PAYMENT_METHOD_NOT_SUPPORTED` / `MCC_NOT_COVERED` / `CURRENCY_NOT_SUPPORTED` / `PAYMENT_ACTION_NOT_SUPPORTED`，新） | `entityNo`、`paymentMethodCode`、`mccCode`、`currencies`（全部不就绪币种，字典序逗号分隔） |
| 部分覆盖币种就绪（2026-10-30 Q-4 修订） | SATISFIED | `CHANNEL_IDENTITY_PARTIAL`（新，提醒） | 同上，`currencies` 为缺 MID 的币种 |
| 全部覆盖币种就绪 | SATISFIED | — | — |

- 删除原因常量 `CHANNEL_IDENTITY_NOT_CONNECTED`（不再产生）；`CapabilityPrerequisiteCheckResponseBody` 字段与注释枚举同步，响应结构不变。
- 状态机、短事务、事件、作业不变。

### 10.3 发布后重查（R4）

等待 / 门禁暂停的开通项由每 5 分钟作业自动覆盖；ENABLED 的在发布后由运维手动触发一次 `capabilityPrerequisiteRecheck`（参数 `includeEnabled`）。触发前先跑只读巡检 SQL（阶段 6 交付）列出预计会被暂停的 ENABLED 开通项告知用户（Q-2）。

## 11. data-query 模板 `GOLIVE_MID_READY`

正文见 [db/data-query-templates.sql](./db/data-query-templates.sql)。阶段 7 修订：data-query 以预编译参数绑定执行，MySQL `JSON_TABLE` 不接受绑定参数（真实执行 500），列表拆分改为 `WITH RECURSIVE`，逻辑不变。参数 `entityNo`、`paymentMethods`、`mccCode` 必填，`currencies` 可选（空串 = 任一币种，S-06）；输出 `out_missing_methods`；`missing` 为 `PAYMENT_METHODS_EMPTY`（未传支付方式视为未满足，阶段 5 补）/ `MID_NOT_FOUND` / `PAYMENT_METHOD_NOT_READY`。约束：maxRows 1、超时 5 秒、不分页（同 REQ-01）。

阶段 3 已在 mor-test 以 root 只读会话、参数替换为字面量试跑 5 组：AE001 `CARD,APPLE_PAY` × 5999 × USD → 0 / `APPLE_PAY`；`CARD` × 5999 × 任一 → 1；`CARD` × 5816 → 0（该能力行无收款动作）；`CARD` × 5999 × `USD,EUR` → 0；SA001 → 0 / `MID_NOT_FOUND`。与第 8.2 节口径一致。已知简化（模板注释已写）：不复现"更精确能力行把动作设为 0"的覆盖，流程最终以 N8 为准。

## 12. 运营控制台（页面设计：[商务 · 18](./prototypes/ops/商务-18-还差什么-渠道身份.png)，`operations-console.pen` 新画板）

| 文件 | 改动 |
|---|---|
| `commercial/capabilityGate.ts` | `PrerequisiteLink` 增加 `{ kind: 'mids'; entityNo }`；`lineLink`：五个渠道原因 → `mids`；`CLIENT_MCC_MISSING` → `client`；`AWAITING_PRICE` → 无链接；UNKNOWN 仍为"重新检查" |
| `commercial/ProductCapabilitySections.tsx` | `mids` 跳 `/channel/mids?entityNo=`；无 MID 页面能力时不显示链接（复用现有页面能力判断，S-07） |
| `pages/ChannelMids.tsx` | 读取 URL `entityNo` 作为初始筛选（不改变无参数时的行为） |
| `i18n`（zh-CN / en，`commercial`） | 新增 7 个原因的完整说明与短原因各 1 键（含参数占位），"去配置 MID →"1 键；删除 `CHANNEL_IDENTITY_NOT_CONNECTED` 两键（发布时标记废弃） |

视觉：沿用商务 · 14 的行结构（图标 / 名称 / 说明 / 链接），红叉 = 未满足、灰减号 = 等待价格、琥珀感叹号 = 暂时无法核对。

## 13. 数据库、注册与配置清单

| 类别 | 数量 | 说明 |
|---|---|---|
| 业务 Schema DDL | 0 | 不加表、不改表 |
| 业务初始化 / 增量 DML | 0 | — |
| Platform 注册 DML（路由 / 权限 / 菜单） | 0 | 新接口为服务间调用；页面无新路由 |
| data-query 模板 | 1 | `GOLIVE_MID_READY`，发布时后台登记（单独授权） |
| 语言包 | 1 次发布 | `commercial` 命名空间增量 |
| 应用配置 | 0 新键 | 客户端走服务发现；超时沿用常量 |

## 14. 决定修订（随阶段 4 文档提交）

- 2026-10-28 客户上线决定附件 3.2 ④：判定改为"与运行期选 MID 同口径：该 OE 下 MID、渠道账号、渠道均 ACTIVE，ENABLED 能力行覆盖支付方式 × 币种（精确或 `*`）× Client 主 MCC，并声明 `ACTION.PAY` 或 `ACTION.AUTHORIZE`；不含路由策略（运行期不读）"；接口改为 `POST /channel-connector/v1/channel-identities/readiness`（批量）。
- 附件 3.5：MID 深链改为 `/channel/mids?entityNo=`。
- 第 5 节登记 REQ-02 目录与阶段。

## 15. 研发计划与验证

| # | 系统 | 任务 | 测试入口（auto，真实生产链路） | 覆盖 AC |
|---|---|---|---|---|
| T1 | channel-connector | 抽 `OperatingEntityMidService`；候选查询改用它 | 现有候选查询 HTTP 测试全部通过 + 新增对照用例 | AC-05 |
| T2 | channel-connector | 就绪查询 Processor / Controller / API | HTTP 测试：五种原因、`*` 币种、非 ACTIVE 账号 / 渠道 / MID、能力 DISABLED、无收款动作、更精确行把动作设 0、批量去重、配置冲突 500 | AC-01—AC-05 |
| T3 | client-master | 经营分类查询 | HTTP 测试：已确认主分类、未确认、无资料、Client 不存在 | AC-09 |
| T4 | commercial | 两个客户端 + 门禁接入 + 原因码 | 检查前置 HTTP 测试（外部 HTTP DTO Mock：channel-connector、client-master）：满足 / 不满足 / UNKNOWN、多币种、无覆盖币种、MCC 未确认、ENABLED → 暂停 → 恢复与事件 | AC-06—AC-10 |
| T5 | ops-web | 链接、MID 页预填、文案 | 单测（`capabilityGate.test.ts`、MID 页）+ tsc / build / i18n 导出；阶段 5 浏览器 zh-CN / en × 1440 / 390 | AC-V1—AC-V4 |
| T6 | 文档 | 决定修订、接口文档、CURRENT_IMPLEMENTATION | — | R7 |
| T7 | data-query | 模板本机 / mor-test 只读试跑 | 阶段 5 本机 + 阶段 7 登记后经 workflow / data-query 预览执行 | AC-11 |

依赖顺序：T1 → T2；T3 与 T1 / T2 并行；T4 依赖 T2、T3 的 `-api`；T5 可与 T4 并行。预计 3—5 个工作日。

## 16. 发布顺序与回滚（阶段 6 细化）

1. 只读巡检：列出按新口径会被暂停的 ENABLED 开通项（告知）。
2. 提供方先上：client-master、channel-connector（新接口无人调用前不影响现有行为；候选查询行为不变）。
3. commercial 上线（④ 改为真实判定；5 分钟内等待 / 暂停项重查）。
4. ops-web 与语言包。
5. 手动触发一次 `includeEnabled` 重查；核对暂停清单与巡检一致。
6. data-query 登记 `GOLIVE_MID_READY`（单独授权）。

回滚：倒序回退镜像即可（无 DDL / DML）。commercial 回退后 ④ 恢复为 SKIPPED，已被暂停的开通项在下一次重查自动恢复 ENABLED。
