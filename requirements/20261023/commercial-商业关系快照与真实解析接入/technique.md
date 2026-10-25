# commercial：商业关系快照与真实解析接入 · 技术方案

> 状态：**阶段 2 已确认**（2026-10-24，S-01—S-16 按建议；只读核对 main `8a86a31c`，已含 billing-b 98210e0b 与多语言 28939303）。第 1 节为阶段 1 证据，第 2 节为阶段 2 差异与影响，第 3 节为阶段 3 技术方案（评审稿，原型见 operations-console.pen 画板 `RS-*`）。

## 1. 现状证据（阶段 1）

- acquiring 商务 Mock：`paytech/acquiring/paytech-acquiring-app/src/main/java/com/paytech/acquiring/configuration/CustomerCheckoutIntegrationConfiguration.java:35-47`（固定 AE001 / 105 / 104）；`acquiring/pom.xml:37-38` 无 commercial-api 依赖；端口模型 `paytech-acquiring-core/.../domain/customer/CustomerCheckoutModels.java:48-112`（`InitializationRequest`、`CommercialFacts`）；冻结在建单事务内（`CreateCheckoutProcessor.java:88-156`），落 `t_commerce_order`、`t_order_context`、`t_order_legal`（`acquiring/docs/database/sql/acquiring-schema.sql:7-90`）；Intent 在首次提交时创建（`SubmitCustomerPaymentProcessor.java:107-113`）。
- commercial 解析：`POST /checkout-context/v1/resolutions`（`CheckoutContextApi.java:25`），只允许 `acquiring-service`；流程 `CheckoutResolutionProcessor`（client-master 候选 → 本库快照 → 资格 → verify）；资格客户端 `CheckoutQualificationClient`（`requireClientEligible` / `isCandidateEligible`）当前装配 `UnavailableCheckoutQualificationClient`（恒 OWNER_UNAVAILABLE）；解析只检查 CURRENCY / CONSUMER_COUNTRY（`CheckoutSubscriptionContext.java:34,103-104`），`paymentMethodCode` 省略时 `capabilities` 为空。
- compliance：`t_qualification_decision`（`compliance-schema.sql:272`）、`t_role_qualification`（`:294`）已建；API 只有入驻审核、材料要求、结算控制合同，无资格查询。
- client-master：`t_party`、`t_local_partner`、`t_operating_entity`、`t_entity_source_relation`（`client-master-schema.sql:7,147,161,340`）；OE 来源 PLATFORM / CLIENT / PARTNER；无收单资质、无 Partner 角色 / 关联关系、无推荐关系表。
- Finance 账簿按 OE（`entity_no`）与 Finance Entity（`design-discuss/finance-account-ledger-v3/design.md` 3.1）。
- mor-test 带条件的 CARD 开通项合同：CL-F1105E、DEMO-CL-001 / 002、DEMO2-CL-101 / 103 / 901（见 `requirements/20261020/commercial-开通与定价模型调整/verification/stage7-acceptance.md`）；acquiring 建单只收 USD。


## 2. 差异与影响（阶段 2）

> 只读核对，没有改代码、没跑构建 / 测试 / SQL。路径以 `paytech/<系统>` 为根，行号对应 main `8a86a31c`。

### 2.1 改变方案的发现

| # | 发现 | 证据 | 影响 |
|---|---|---|---|
| F1 | **REAL 模式会被卖方公开文本阻断**：acquiring 建单强制 seller 的法定名称、注册号、地址、客服邮箱非空，并冻结进 `t_order_legal.public_*`、展示给消费者；commercial 响应只有 `sellerDisclosureId`，client-master 候选里的 SellerDisclosure 也只有 ID 与状态 | acquiring `CheckoutInitializationContext.java:75-76`；commercial `ResolveCheckoutContextResponseBody.java:272-293`；client-master `CheckoutCandidatesResponseBody.java:200-218`、`client-master-schema.sql:384-406` | 不补则 REAL 每单 502。需求 7.1 / 7.3 / 7.4 要加“卖方公开文本”合同扩展（决策 S-01） |
| F2 | **REAL 模式缺 locale**：commercial 请求 locale 必填且按 locale 精确匹配法律文件；acquiring 端口请求没有 locale，冻结时写死 `"en"` | commercial `CheckoutQuery.java:68`；acquiring `CheckoutInitializationContext.java:50` | 端口请求加 locale（决策 S-02） |
| F3 | **OE 获批 MCC 已存在**：OE 准入范围 `t_operating_entity_scope` 按（国家, 币种, 行业, MCC）精确批准，候选查询已强制与 Client 主分类匹配 | client-master `client-master-schema.sql:282-300`、`CheckoutEligibilityContext.java:252-256` | Q3 的“MCC 子表”与之重复；B3 的 MCC 校验在 client-master 已生效（决策 S-03） |
| F4 | **client-master 没有任何 OE / Local Partner 写入或运营读取接口**；ops-web 也没有 OE / Partner 页面 | client-master `ext/rest/` 只有 Checkout / 入驻 / 分类 / 存卡等 Controller，OE / LP mapper 只有 `findForCheckout`；ops-web `src/pages/` 无对应页 | 7.1 “运营 API”与 7.5 “OE 详情 / Partner 详情增加…”是从零新建，范围变大（决策 S-04） |
| F5 | **compliance 资格表有表无人写**：`t_role_qualification`、`t_qualification_state_change` 没有 DataObject / Mapper / 写入方；入驻审核通过只写 `t_qualification_decision` | compliance `ClientReviewServiceImpl.java:90-101`；全仓 grep 只命中 DDL 与测试清理器 | AC-02 “通过后资格落表”要从零补齐，并回填 mor-test 已通过 Client |
| F6 | **手工登记有三处 Schema 阻断**：`t_qualification_decision.case_id` NOT NULL；`decision_source` 只允许 AUTOMATED / REVIEWER / OVERRIDE；没有依据列 | `compliance-schema.sql:272-292` | 需改 PDManer / Schema / 增量 SQL / 测试 DDL（决策 S-05） |
| F7 | **结算阻断只有合同**：`t_settlement_control` 已建表，`SettlementControlApi` 无 Controller / Processor / 写入方 | compliance `SettlementControlApi.java:14-55`（注释写明未实现） | Q1 的“无 ACTIVE 阻断”只需在资格查询里加一个只读判断；mor-test 表为空即恒无阻断 |
| F8 | **compliance 不能用 `AbstractLockProcessor`**：架构测试禁止 Redisson 上 classpath，README 写明不用 Redis；也没有出站 HTTP 客户端 | `ComplianceArchitectureTest.java:118-119` | 手工登记用 `AbstractProcessor` + `t_role_qualification.version` CAS + 唯一键；主体存在性由 ops-bff 先查 client-master |
| F9 | **发布顺序约束**：commercial 解析 client-master 响应时开启 `FAIL_ON_UNKNOWN_PROPERTIES`，client-master 先上新字段会让解析 503；client-master 也拒绝未知请求字段 | foundation `HttpServiceClientFactory.java:119-130`；client-master `ClientMasterWebConfiguration.java:14` | 必须 commercial（带新 api jar）先于 client-master 发布（2.4） |
| F10 | **候选摘要随 record 变化**：client-master 候选 digest 是对 record `toString()` 做 SHA-256，verify 比对摘要 | client-master `CheckoutEligibilityContext.java:301-302,332-340` | 滚动发布期间 candidates / verify 落在不同版本实例会误报变化；client-master 单实例替换即可 |
| F11 | **需求第 1 节归因不准**：mor-test 直连解析的 422 `CLIENT_NOT_ELIGIBLE` 来自 client-master 候选阶段（Client 未就绪），还没走到资格客户端；走到资格客户端时是 503 | `requirements/20261020/commercial-开通与定价模型调整/verification/stage7-acceptance.md:33` | 已修正需求第 1 节；mor-test 种子（Q6）还要包括 client-master 的 Client 审核与分类 |
| F12 | **mor-test 没有可用的 PARTNER 来源种子**：所有 Client 合同都挂 AE001（PLATFORM）；Partner 合同 DEMO2-PT-101 只有 PARTNER_SHARE，没有 PLATFORM_SERVICE_CHARGE，PARTNER 来源候选必然落空 | commercial `CheckoutResolutionContext.java:190-195`；stage7-acceptance `:82-92` | AC-11 范围需重定（决策 S-15） |
| F13 | **注册块 136 已被占用**（operations-文件上传入口，已上 mor-test）；**块 137 → 本需求（商业关系快照）**：2026-10-24 code-mor-db 只读核对主仓库、各 worktree 与 mor-test mor_platform 均无 137 占用（ID 最大到 900000000000136xxx）；阶段 4 写 SQL 前再核对一次 | `requirements/20261023/operations-文件上传入口/technique.md:148` | 7.5 块号改为 137 |
| F14 | **acquiring 的测试替身会盖住真实客户端**：`CheckoutCommercialMocker` 是 `@TestComponent @Primary` 直接实现端口，13 个测试类在用 | acquiring `test/mocker/external/CheckoutCommercialMocker.java:22-24` | 按测试规范改为 HTTP DTO Mock 端点（`MockCheckoutContextApi`），否则 REAL 解码路径无覆盖 |
| F15 | **Client API 已有可复用错误码**：设计稿规定 OE / 合同 / 法律未就绪返回 `CLIENT_CONFIGURATION_NOT_READY`，依赖故障 503 / 504；现有测试已断言 | acquiring `CommerceCheckoutErrorCode.java:13-29`；`docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md:367-374` | 7.6 “新增错误码”改为“映射 + 文档补充”（决策 S-11） |

### 2.2 逐系统差异

#### client-master

| 变动点 | 现状 | 差异 | 影响 / 风险 |
|---|---|---|---|
| `t_party_role`（新） | 角色是三张根表（`t_client` / `t_local_partner` / `t_operating_entity`）各自 `uk_*_party`，无统一角色表；`t_party_relation` 只记 KYB 关系（`schema.sql:114-170`） | 新表；回填按三张根表 `party_id` 推出 CLIENT / OPERATING_ENTITY，OE_PROVIDER 按 PARTNER 来源 Source 行的 LP | 需登记 `tools/arch-rules/.../table-ownership.yaml:97-118`；BCR 白名单不含新表（见审计行） |
| `t_operating_entity.acquiring_qualification` | 只有 entity_no / party_id / operating_country / status（`:161-170`） | 新列；存量回填 | DO（`CheckoutRecords.java:72-79`）、resultMap、候选链路逐层加（mapper → record → Context → `EntityCandidate` → Converter → DTO） |
| `t_local_partner.affiliation` + `t_partner_role`（新） | LP 只有 partner_no / party_id / status 等（`:147-159`） | 新列 + 新子表 | 同上；commercial 对 Partner 的判断按 `sourceType=="PARTNER"`（commercial `CheckoutResolutionContext.java:131,171-189`） |
| OE 获批 MCC | 已有 `t_operating_entity_scope`（F3） | 不新建（建议，S-03） | — |
| `t_client_partner_relation`（新，只建表） | 不存在；commercial 合同已有 `referral_partner_id`（`commercial-schema.sql:15`） | 新表 | 与合同级推荐字段的关系留给 C4 需求；本轮只建表 |
| `t_entity_source_relation` PLATFORM | 枚举 PLATFORM / CLIENT / PARTNER（`EntitySourceRelationSourceType.java:6-11`）；无写入路径；`t_operating_entity_application.source_type` 也允许 PLATFORM | 只改注释、PDManer 规则文本（`scripts/pdmaner/client-contract-model-core.mjs:304`）、物理决策 | 没有写入路径可挂“新建 OE 不允许 PLATFORM”校验，留给 OE 写入接口落地时 |
| PLATFORM 来源 OE 的占位 OE_PROVIDER（Q2） | Source 行 client / partner 均空；候选只在 PARTNER 来源时读 LP（`CheckoutEligibilityContext.java:287-304`） | 无字段把 PLATFORM OE 指向任何 Partner | 决策 S-06 |
| 候选 API 响应 | 已有 `oePartyId`、`localPartner.partyId`、Client 主分类 industry / mcc（`CheckoutCandidatesResponseBody.java:40-159`） | 新增 OE `acquiringQualification`、`oeProvider`（含 affiliation / roles）、卖方公开文本（S-01） | 不需要新增 “OE legal Party”；严格解析约束见 F9 |
| 根版本 / 审计 | BCR `record_type` / `aggregate_root_type` 闭合白名单 + CAS（`schema.sql:408-435`、`CheckoutRecords.java:221-240`）；verify 只认 5 类根（`BusinessChangeRecordMapper.xml:15-20`） | 新表不在白名单 | 建议作为 OE / LP 根的子行，写入推进根版本，verify 自然感知（S-12） |
| 运营 API | 无（F4）；可参照 `ClientClassificationAdminApi`（`/client-onboarding-admin/v1`，Gateway Actor 头，Controller 只校验会话，BCR 写法见 `ClientDraftMapper.xml:106-109`） | 新建 OE / LP 列表、详情、资质维护、关联关系与角色维护 | ops-bff 照 `ClientReviewClientConfiguration.java:54-68` 加客户端 |
| 测试 | 夹具 `checkout-candidates.json` 两个 OE 都是 PLATFORM、LP 为空；`ResolveCandidatesHttpTest.java:22` 断言 LP 为空；没有 OE / LP Builder | 补 PARTNER 来源 OE + LP、资质、角色夹具与用例 | 新列若 NOT NULL 无默认，现有 JSON 夹具会插入失败 |

#### compliance

| 变动点 | 现状 | 差异 | 影响 / 风险 |
|---|---|---|---|
| 资格表 | 三表已建（`compliance-schema.sql:272-331`）；`uk_role_qualification(role_type, role_id, qualification_type)` | 补 DO / Mapper / Repository；决定表改 case_id 可空、加 MANUAL 与依据列（S-05） | 设计约束 `ck_role_qual_review` 只能由应用保证：撤销时 review_status 改 COMPLETED |
| 角色映射 | role_type CLIENT / LOCAL_PARTNER / OPERATING_ENTITY / RELATED_PERSON；qualification_type INDIVIDUAL_KYC / ORGANIZATION_KYB / PARTNER_DUE_DILIGENCE / ENTITY_UNDERWRITING | 快照角色 → compliance（角色, 资格类型）映射：CLIENT → CLIENT + KYC/KYB；ACQUIRING_OE → OPERATING_ENTITY + ENTITY_UNDERWRITING；OE_PROVIDER → LOCAL_PARTNER + PARTNER_DUE_DILIGENCE | 映射表写入阶段 3 合同；OE 的 role_id 是 BIGINT，commercial 现用 entityNo 字符串（S-07） |
| 查询 API（新） | 无；读 Processor 样板 `GetClientReviewResultProcessor.java:23-45` | `POST /compliance/v1/qualifications/query`（批量，commercial-service）+ 运营单查 / 历史（Admin 风格，Gateway Actor 头） | 判定：APPROVED 且 valid_from ≤ now < valid_to（或空）；CLIENT 另需无 ACTIVE SETTLEMENT_BLOCK；过期查询时实时判定（S-08） |
| 调用方限制 | 现有 Controller 写“X-Caller-Service 暂不校验”，无白名单配置；其他服务在 Controller 硬编码比较（client-master `CheckoutCandidateController.java:72-75`） | 新增硬编码守卫 | AC-03 |
| 手工登记 / 撤销（新） | 写 Processor 样板 `DecideClientReviewProcessor.java:37-147`（TransactionTemplate + 版本条件更新，冲突 409） | 同一事务写 decision → upsert role_qualification（version CAS）→ state_change；操作人进 decided_by / actor_id | F8 |
| 入驻通过落表 | 不落（F5） | 在 `ClientReviewServiceImpl.decide()` 同一事务投影 CLIENT 资格；RELATED_PERSON 不投影 | mor-test 回填（S-09） |
| 开关 | 入驻 Controller 受 `onboarding.review.enabled` 控制 | 新 Controller 不挂该开关（建议） | — |
| 测试 | `ComplianceMockTestBase`；`EXPECTED_TABLE_COUNT = 25` | 只加列不加表时表数不变 | 本地 mor_compliance 需 root 手工执行 DDL |

#### commercial

| 变动点 | 现状 | 差异 | 影响 / 风险 |
|---|---|---|---|
| 资格客户端 | `CheckoutQualificationClient`：`requireClientEligible(long)`、`isCandidateEligible(entityNo, localPartnerId)`；无条件装配 Unavailable 实现（`CheckoutResolutionConfiguration.java:91-94`） | 改为批量（角色, 主体）；新 compliance 客户端照 `:80-88` 写法；`commercial-core` 加 `paytech-compliance-api` 依赖 | compliance 需区分的 4xx 要登记为 contractErrors，否则当 503 |
| 解析顺序 | 先构造第一个候选，没有候选报 NO_ELIGIBLE_ENTITY，有候选才查 Client 资格（`CheckoutResolutionProcessor.java:46-56`） | B2：Client 资格提前到候选之前（S-10） | `ResolveCheckoutHttpTest` 大量断言 503 的用例要改 |
| 快照推导 | 无；`derivedView()` 硬编码 `fundsBookOwner=OPERATING_ENTITY`（`CheckoutCandidateContext.java:146-152`） | 新领域对象 `RelationshipSnapshot` 放 `domain/`，由 `buildCandidate`（`CheckoutResolutionContext.java:110-144`）构造；`Derived` 保留为旧字段 | verify 用 `candidate.equals(selected)`：资格结果与时间戳不得进入 equals（S-13） |
| 组合约束 | 无资质 / MCC / Party 校验；`fund_mode` 只在建合同时校验（`CreateAgreementProcessor.java:142-169`），本期只接受 SETTLE_THROUGH_PLATFORM | 资质三档、Partner Party = OE Party 在解析时校验，不合格候选落空 | 违反时落空而非整单拒绝（S-10） |
| 省略支付方式 | 直接返回空开通项（`CheckoutSubscriptionContext.java:65-67`） | 返回全部 PAYMENT_METHOD 开通项与接受条件，仍按 ENABLED、时间窗、CURRENCY / COUNTRY、有生效价格过滤；为空不落空 | API 注释与 `CheckoutResolutionMatrixTest.java:79` 断言要改 |
| 写入时 Party 校验（AC-10） | `ReplaceCommercialTermPricesProcessor`、`ActivateAgreementProcessor` 都不读 Party；commercial 本库没有 entityNo → partyId | 解析时精确校验（零依赖）；写入时只能做“合同内 OPERATING_ENTITY 角色行 Party = LOCAL_PARTNER Party”近似 | S-14 |
| “平台价目” | `PLATFORM_SERVICE_CHARGE` 归 Local Partner；另有 Client 条款类型 `PLATFORM_FEE`（向 Client 收，与 D10 冲突） | 本轮只指 PLATFORM_SERVICE_CHARGE；PLATFORM_FEE 留给 U3 | — |
| 开关 / 配置 | 无 `commercial.relationship-snapshot.*`；属性写法 `CommercialIdentifierProperties.java:9-24` | 新建 Properties（默认 false），yml 与测试 yml 同步 | — |
| 测试 | 基类自述“成功解析路径仍无覆盖”（`CommercialMockTestBase.java:39`）；无 compliance Mock | 新增 `MockComplianceQualificationApi` + Mocker；补成功路径 HTTP 用例 | — |

#### acquiring

| 变动点 | 现状 | 差异 | 影响 / 风险 |
|---|---|---|---|
| Mock 拆分 | `CustomerCheckoutIntegrationConfiguration.java:17-90` 5 个 `@ConditionalOnMissingBean` Mock（ConsumerProtection / Commercial / Tax / Document / Payment 查询） | 只把 Commercial（`:35-47`）移到独立配置，按 `acquiring.commercial.resolution-mode`（MOCK 默认，`matchIfMissing`）与 REAL 互斥 | 其余 4 个不动 |
| 端口模型 | `InitializationRequest` 无 locale；`CommercialFacts` 无快照 / fund_flow / 版本 / 开通项（`CustomerCheckoutModels.java:49-112`） | 加 locale（F2）、快照、开通项、businessMode | 全部 ID 是字符串，impl 内解析，格式不符抛 `CheckoutUpstreamProtocolException`（502） |
| 冻结 | 事务外 resolve → validate → risk → tax → documents（`CreateCheckoutProcessor.java:135-156`）；事务内一次写 8 张表（`CheckoutCreationServiceImpl.java:30-42`） | 新表 `t_order_relationship_binding` 同事务插入；`t_order_context` 加 `fund_flow`、`effective_relationship_model_version`、`observed_snapshot_version` | 读侧 resultMap 同步（`CustomerCheckoutMapper.xml:5-60`、`OrderContextMapper.xml:28-64`） |
| 出站客户端 | 无 commercial-api 依赖（`acquiring/pom.xml:37-38`）；范本 `RiskClientConfiguration.java:13-25` | 新增 `CommercialClientConfiguration`（`lb://commercial-service`），Header 恰好一个 `X-Caller-Service: acquiring-service` | mor-test 上 commercial 是否在 Nacos 注册未核实（billing 用直连 URL） |
| 时钟 | contextAsOfTime 取 acquiring 时钟；commercial 对未来时间返回 422 | 映射为 502 并记录日志 | 时钟偏差可能偶发失败（未核实是否同步） |
| 错误映射 | 现有码见 F15 | NO_ELIGIBLE_ENTITY / AMBIGUOUS → CLIENT_CONFIGURATION_NOT_READY；CLIENT_NOT_ELIGIBLE → CHECKOUT_NOT_ELIGIBLE；OWNER_UNAVAILABLE / 5xx → DEPENDENCY_UNAVAILABLE；超时 → DEPENDENCY_TIMEOUT；CHANGED → 重试一次后 DEPENDENCY_UNAVAILABLE；404 / INVALID → UPSTREAM_PROTOCOL_ERROR | OpenAPI `client-v1.yaml:13` description 补说明 |
| 测试 | 建单 6 个测试类；`CommercialFactsBuilder` 模板 AE001 / 104 / 105；替身问题见 F14 | 新增 `MockCheckoutContextApi`；两种开关取值各覆盖 | — |

#### 运营端与 Platform 注册

| 变动点 | 现状 | 差异 | 影响 / 风险 |
|---|---|---|---|
| ops-bff → compliance | 只有入驻审核（`ClientReviewClientConfiguration.java:45-51`） | 资格查询 / 登记 / 撤销 / 历史客户端与转发 | 依赖 compliance 先上 |
| ops-bff → client-master | 只有入驻申请与分类 Admin API | OE / LP 列表、详情、资质、关联关系与角色维护 | 依赖 client-master 新 API（F4） |
| ops-web | 无 OE / LP 页面；合规只有入驻审核页 | 新建：资格登记页、OE 列表 / 详情（收单资质）、Partner 列表 / 详情（关联关系、角色） | 多语言：禁中文字面量（`src/i18nSourceGuard.test.ts`），英文 `src/i18n/en/<ns>.json`，中文种子在仓库根 `i18n/zh-CN/<ns>.json`，新命名空间登记 `src/i18n/namespaces.ts`，菜单需 `nav.resource.<编码>` |
| Platform 注册 | 块到 136（文件上传入口） | 块 137：权限、角色授权、菜单、路由、Header / Query / Guard；默认休眠 + 激活 / 回滚脚本；Guard 照块 134 同类路由 | 用 mor-test 备份离线编译休眠态与激活态 |

#### 不改代码但受影响

| 系统 | 影响 | 证据 |
|---|---|---|
| payment | 切 REAL 后 entityNo 为真实值，Payment 按 entityNo 选 MID；没有 MID 的 OE 会“没有可用的渠道 MID” | payment `RouteServiceImpl.java:39` |
| billing | 当前 Mock 104 / 105 在 commercial 404，消息进 DLQ；切 REAL 后才可能命中价格。billing-b 记录的 GAP-ISSUER（CARD 缺发卡维度 422）、GAP-PARTNER 是 AC-11 的前置；真实 OE 还需账簿、清算账户和成本方案（mor-test 只有 AE001） | billing `CommercialPriceClientImpl.java:48-93`；`requirements/20261022/billing-支付事实自动登记/release/release.md:287-290` |
| risk-decision | 结账评估输入的 entityNo / source / LP / 行业 / MCC 变真实值；是否有按 OE 的规则未核实 | acquiring `CheckoutRiskPortImpl.java:137-143` |
| tax / document Mock | Tax Mock 回显 entityNo，Document Mock 只拼假 URL，不冲突 | acquiring `CustomerCheckoutIntegrationConfiguration.java:58-76` |
| 事件 OrderPaymentSucceeded | 字段不变，快照不进事件 | acquiring `OrderPaymentSucceededPayload.java:42-61` |

### 2.3 待决事项（阶段 2 已确认，2026-10-24 全部按建议）

| 编号 | 事项 | 建议 |
|---|---|---|
| S-01 | 卖方公开文本来源（F1） | client-master 候选响应带回卖方披露公开文本，commercial 透传到解析响应；acquiring 一次调用拿全，不另调 client-master |
| S-02 | locale 取值（F2） | 端口请求加 locale，本轮取订单冻结的 locale（现为 `en`），两处同值；多语言法律文件另立 |
| S-03 | OE 获批 MCC（F3，改 Q3） | 复用 `t_operating_entity_scope` 的 MCC 维度，不新建子表；B3 的 MCC 兼容由 client-master 候选过滤保证 |
| S-04 | 运营页面范围（F4） | 新建 OE 列表 / 详情与 Partner 列表 / 详情（只读主档 + 资质 / 关联关系 / 角色维护），不做 OE / LP 创建；client-master 新增对应 Admin API |
| S-05 | 手工资格的 Schema（F6） | `case_id` 改可空；`decision_source` 加 MANUAL；决定表加 `basis_note VARCHAR(1000)`；依据文件不做 |
| S-06 | PLATFORM 来源 OE 的占位 OE_PROVIDER（Q2 细化） | **阶段 3 评审作废（2026-10-24 用户决定）：取消 OE 来源 PLATFORM，AE001 迁到我们的 PayFac Partner 名下，不设占位与 `provider_partner_id`。** 原建议：种子 SQL 建一条预留 LP，绑定 AE001 自己的 Party（满足 Partner Party = OE Party），状态 ACTIVE、affiliation=AFFILIATED、角色 OE_PROVIDER；`t_operating_entity` 加可空 `provider_partner_id`，只用于 PLATFORM 来源；候选响应新增独立 `oeProvider` 字段，不复用 `localPartner` |
| S-07 | OE 资格主体标识 | ~~compliance 的 role_id 用 OE 的数值标识~~ 阶段 3 评审修订（2026-10-24 用户决定）：OE 全系统统一用 entity_no（AE001 格式），不引入数值 ID；compliance 资格两表的 `role_id` 改为字符型，OE 存 entity_no，Client / Partner 存十进制 ID |
| S-08 | 过期判定 | 查询时按 valid_to 实时判定 EXPIRED，不加定时任务 |
| S-09 | Client 资格回填 | 入驻通过时同事务投影 CLIENT；mor-test 已通过 Client 用一次性增量 SQL 回填（ID 由 decision_id 派生）；未经入驻审核的演示 Client 走手工登记 |
| S-10 | 失败语义 | Client 资格检查提前到候选之前，不合格整单 CLIENT_NOT_ELIGIBLE；资质 / Party / 组合不符的候选落空并记日志，全部落空报 NO_ELIGIBLE_ENTITY；不新增 commercial 错误码 |
| S-11 | Client API 错误码（F15） | 不新增，按 2.2 acquiring 错误映射复用现有码，OpenAPI 补说明 |
| S-12 | client-master 审计 | 新表作为 OE / LP 根的子行，写入推进根 BCR 版本 |
| S-13 | verify 相等性 | 资格结果、模型版本不进入候选 equals |
| S-14 | AC-10 写入时校验 | 解析时精确校验；写入时做“合同 OPERATING_ENTITY 角色行 Party = LOCAL_PARTNER Party”近似校验；AC-10 文字相应调整 |
| S-15 | mor-test 验收范围（F12，细化 Q6） | AC-11 只验 AE001（PLATFORM 占位）路径下 USD Client 的 REAL 建单；PARTNER 来源（C1 / C2）只在 mor-test 直连 commercial 解析验证快照，不建单（真实 Partner OE 需 MID、账簿、成本，属第 2 步） |
| S-16 | acquiring 测试替身（F14） | 改为 HTTP DTO Mock 端点，13 个测试类迁移 |

### 2.4 发布顺序（阶段 6 细化；阶段 4 跨系统核查后修订 2026-10-25）

1. compliance（新列、API；无消费者时无影响）。
2. commercial 与 client-master **紧接着连续发布**（commercial 先）：新 commercial 要求候选带 `acquiringQualification` / `oeProvider`，旧 client-master 下解析恒 503；新 client-master 的新字段旧 commercial 又不认（F9）。两者之间的窗口内 acquiring 仍是 MOCK，不影响建单。client-master 的数据 SQL（AE001 迁到 PayFac Partner）与其镜像同窗口执行。
3. acquiring（默认 MOCK；Mock 已改为 AE001 + PARTNER）。**必须先于 risk-decision**：旧 acquiring 的 Mock 发 PLATFORM，新 risk 会拒绝。
4. risk-decision（删除 PLATFORM 取值）及其数据 SQL。
5. ops-bff → ops-web → Platform 多语言命名空间与导入 → 块 137 激活。
6. 切 REAL 前的门禁（全部满足才切）：commercial 为 PayFac Partner 建 Partner 合同（优先运营 API）；运营页为 AE001（ENTITY_UNDERWRITING）、PayFac Partner（PARTNER_DUE_DILIGENCE）和测试 Client 登记资格；AE001 × 消费者国家 × `en` 的已发布卖方披露；GAP-PARTNER 处理口径。然后 acquiring 切 REAL（AC-11）。模型开关保持关闭。

## 3. 技术方案（阶段 3 评审稿）

> **规模（本次方案）：** 8 个系统（client-master、compliance、commercial、acquiring、risk-decision、ops-bff、ops-web、platform-support 注册）；新增接口 12 个 Owner 接口 + 11 个 BFF 接口（其中 5 个为 BFF 聚合，6 个为转发），修改接口 2 个（checkout-candidates 响应、checkout 解析响应），另有 2 个既有写接口增加校验；新增表 4 张（client-master 3、acquiring 1）共 34 个字段；修改旧表 4 张，新增 5 个字段、1 个字段改可空、2 个字段改类型（`t_operating_entity` +1、`t_qualification_decision` +1 且 `case_id` 改可空、`role_id` 改字符型、`t_role_qualification` 的 `role_id` 改字符型、`t_order_context` +3），`t_entity_source_relation` 与 `decision_source` 只改注释；新增索引 7 个（均在新表）；业务 DML 为 client-master 回填与 AE001 迁移、commercial PayFac Partner 合同、compliance 回填、acquiring / risk-decision 取值改写；另删除 OE 来源 PLATFORM 的代码与文档（含 AGENTS.md、决定草案）；Platform 注册块 137 约 134 行（权限 6、授权 6、菜单与按钮 6、路由 11、Header 约 28、Query 约 11、Guard 66）；页面 3 个菜单、5 个视图。下文所有内容都是方案，没有实现或执行。

### 3.1 主流程（REAL 模式建单）

```
acquiring CreateCheckoutProcessor（事务外）
  └─ CheckoutCommercialPortImpl.resolve ──POST /checkout-context/v1/resolutions──▶ commercial
        commercial CheckoutResolutionProcessor
          1. client-master POST /client-master/v1/checkout-candidates   （候选 + 卖方公开文本 + 收单资质 + oeProvider）
          2. 本库只读快照（合同 / 价目 / 开通项）
          3. compliance POST /compliance/v1/qualifications/evaluations （一次批量：Client + 全部候选的 OE 与提供方）
          4. Client 不合格 → 422 CLIENT_NOT_ELIGIBLE（先于候选判断，即使没有候选）
          5. 按优先级逐候选：构造候选 → 推导 RelationshipSnapshot → 组合约束 → OE / 提供方资格 → 选中
          6. client-master verify + 本库复核（CCT_CONTEXT_CHANGED → acquiring 重试一次）
          7. 响应附 relationshipSnapshot（modelVersion 由开关决定）与 legal.seller
  └─ validateCommercial → risk → tax → documents（不变）
  └─ CheckoutCreationServiceImpl 一个事务：原 8 张表 + t_order_relationship_binding；t_order_context 写 fund_flow / 模型版本
```

S-10 的落地方式：调用顺序仍是先取候选（OE 名单来自候选），但**判定顺序**是 Client 资格先于候选判定，资格查询合并为一次往返（Q4）。效果与“先查 Client”一致：Client 不合格时一律报 `CLIENT_NOT_ELIGIBLE`，不会被 `NO_ELIGIBLE_ENTITY` 掩盖。

### 3.2 商业关系快照（commercial 唯一推导点）

**领域对象** `domain/relationship/RelationshipSnapshot`（record）：`int snapshotModelVersion`（推导规则版本，本轮恒为 1）、`FundFlow fundFlow`、`BusinessMode businessMode`、`List<RoleBinding> bindings`。`RoleBinding(RelationshipRole role, SubjectType subjectType, String subjectId, Long partyId)`。

枚举：`RelationshipRole` = CLIENT、SELLER、ACQUIRING_OE、OE_PROVIDER、FUNDS_CUSTODIAN、TAX_RESPONSIBLE、PLATFORM_FEE_PAYER、ACQUIRER_SUB_MERCHANT、REFERRER、SPLIT_PAYEE、PLATFORM（全部 11 个可取值，本轮只产出前 9 个中的 C1 / C2 所需及 PLATFORM）；`SubjectType` = CLIENT、OPERATING_ENTITY、LOCAL_PARTNER、PLATFORM；`FundFlow` = THROUGH_CUSTODIAN、DIRECT_TO_CLIENT（本轮只产出前者）。

**推导规则（决定 9.2，本轮只支持 OE 来源 PARTNER；OE 来源 PLATFORM 已取消）：**

| 角色 | MOR（C1） | PSP（C2） | 主体 |
|---|---|---|---|
| CLIENT | ✓ | ✓ | CLIENT / clientId / Client Party |
| SELLER | 收单 OE | Client | OE：OPERATING_ENTITY / entityNo / OE Party |
| ACQUIRING_OE | ✓ | ✓ | OPERATING_ENTITY / entityNo / OE Party |
| OE_PROVIDER | ✓ | ✓ | LOCAL_PARTNER / localPartnerId / Partner Party（PARTNER 来源 Source 行的 LP） |
| FUNDS_CUSTODIAN | 收单 OE | 收单 OE | 同 ACQUIRING_OE |
| TAX_RESPONSIBLE | 收单 OE | Client | 同 SELLER |
| PLATFORM_FEE_PAYER | 提供方 | 提供方 | LOCAL_PARTNER / localPartnerId / Partner Party |
| ACQUIRER_SUB_MERCHANT | 不产出 | Client | CLIENT |
| PLATFORM（技术服务商） | ✓ | ✓ | PLATFORM / 空 / Partner 合同 `PLATFORM_SIGNATORY` Party |

`fund_flow` 恒为 THROUGH_CUSTODIAN。OE 来源 CLIENT（C3）或缺少提供方 → 该候选落空（日志 `relationship-unsupported`）。PLATFORM 角色是技术服务商（D1），与已取消的“OE 来源 PLATFORM”无关，保留。

**组合约束（不符即候选落空，记日志 reason）：**

| 约束 | 规则 |
|---|---|
| 收单资质 | `MERCHANT` 不能承接 → 落空；`MERCHANT_RESALE_APPROVED` 只允许 MOR；`PAYFAC` 允许 MOR / PSP |
| 提供方状态与角色 | 提供方 status=ACTIVE 且 roles 含 OE_PROVIDER |
| 法律主体一致（D3 ②） | 提供方 Party = OE Party；不符落空 |
| MCC | 由 client-master 准入范围过滤，commercial 不重复判断（S-03） |
| 资格 | Client：整单；ACQUIRING_OE（OPERATING_ENTITY, entityNo）与 OE_PROVIDER（LOCAL_PARTNER, localPartnerId）任一不 eligible → 落空 |

注意 D3 ② 与现有唯一键的组合含义：client-master `uk_entity_party(party_id)` 使一个 Party 只能对应一个 OE，所以一个 Partner 本期最多给一个 OE 提供服务费合同——这就是决定 D3 的“本期显式限制”，不另处理。

**相等性（S-13）：** 快照只由候选事实推导，作为 `CheckoutCandidateContext` 的一个字段参与 equals（同样事实同样快照）；资格结果放在 Processor 局部的 `Map<SubjectKey, Eligibility>`，模型版本在 `toView()` 时由开关填入，二者都不进入 equals。

**开关：** `CommercialRelationshipSnapshotProperties`（`@ConfigurationProperties("commercial.relationship-snapshot")`，`boolean enabled` 默认 false，构造绑定风格同 `CommercialIdentifierProperties`）。响应 `effectiveModelVersion = enabled ? 1 : 0`，`snapshotModelVersion = 1`。

### 3.3 接口

#### 3.3.1 client-master

**修改 `POST /client-master/v1/checkout-candidates`（只加字段，调用方 commercial-service）**

`Candidate` 增加：

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `acquiringQualification` | String | 否 | MERCHANT / MERCHANT_RESALE_APPROVED / PAYFAC |
| `oeProvider` | Object | 是 | `{localPartnerId, partyId, status, roles: [String]}`；= PARTNER 来源 Source 行的 LP（与 `localPartner` 同一个，新字段带角色）；CLIENT 来源为 null |

`SellerDisclosure` 增加 `publicLegalName`、`publicRegistrationNumber`、`publicTaxRegistrationNumber`（可空）、`publicAddressText`、`publicSupportEmail`、`publicSupportPhone`（可空），取自 `t_seller_disclosure` 同名列（`SellerDisclosureMapper.xml:18` 补列，`CheckoutRecords.SellerDisclosure` 补字段）。

影响：`CheckoutRecords` 加字段后候选摘要全部变化（摘要对 record `toString()` 做 SHA-256），发布瞬间在途的 verify 会得到 CHANGED，acquiring 重试一次即恢复；client-master 单实例替换。候选查询删除 PLATFORM 分支（`CheckoutEligibilityContext.java:272`），来源只认 CLIENT / PARTNER，其他取值 → `CANDIDATE_CONTEXT_AMBIGUOUS`（失败关闭）。

**新增 Admin API `@HttpExchange("/client-master-admin/v1")`（`ClientMasterEntityAdminApi`，Gateway Actor 头 `X-Gateway-Actor-Subject` + `X-Gateway-Session-Id`，校验同 `ClientClassificationAdminController.actor()`；不挂 onboarding 开关；成功无信封，错误为 Problem）**

| # | Method + Path | 说明 |
|---|---|---|
| 1 | `GET /operating-entities?status=&sourceType=&country=&page=&size=` | 分页（Foundation `PagingResponseBody`，size ≤ 100）：entityNo、法定名称（`t_organization_profile.legal_name`）、country、status、sourceType、acquiringQualification、提供方（id / 名称）、changeVersion |
| 2 | `GET /operating-entities/{entityNo}` | 详情：主档 + 当前 Source 行 + 准入范围列表（国家 / 币种 / 行业 / MCC / 状态 / 生效期，只读）+ 卖方披露摘要（国家、locale、状态、生效期）+ 提供方 + `changeVersion`（根 BCR `current_version`） |
| 3 | `PUT /operating-entities/{entityNo}/acquiring-qualification` | 请求 `{acquiringQualification, expectedChangeVersion, reason(≤500)}`；响应同详情 |
| 4 | `GET /local-partners?status=&role=&page=&size=` | 分页：localPartnerId、partnerNo、法定名称、status、roles、提供的 OE 数、changeVersion |
| 5 | `GET /local-partners/{localPartnerId}` | 详情：主档 + roles + 提供的 OE（PARTNER 来源 Source 行）+ changeVersion |
| 6 | `PUT /local-partners/{localPartnerId}/roles` | 请求 `{roles: [OE_PROVIDER, REFERRER]（非空、去重）, expectedChangeVersion, reason}`；去掉 OE_PROVIDER 时若仍有 ACTIVE 的提供关系 → 422 |
| 7 | `GET /clients/{clientId}/summary` | 资格登记前的主体核对：clientId、clientNo、partyId、clientType、status、法定名称 |

写接口 3、6：`AbstractLockProcessor`（TRY_LOCK `client-master:operating-entity:{entityNo}` / `client-master:local-partner:{id}`，沿用 `resource-lock` 配置）；锁内一个事务：CAS 推进根 BCR（`current_version = expected` → `expected + 1`，影响行数 ≠ 1 → 409）、写 `t_business_change_version`（UPDATE 快照，`create_by` = actor）、改主表 / 子表、维护 `t_party_role`（roles 增减 OE_PROVIDER / REFERRER 时同步行状态）。根 BCR 不存在（历史手工数据）时同事务补建 V1 根后再推进。本轮不发事件（无消费者）。

错误码 `EntityAdminErrorCode`：`OPERATING_ENTITY_NOT_FOUND` 404、`LOCAL_PARTNER_NOT_FOUND` 404、`CLIENT_NOT_FOUND`（复用，404）、`ENTITY_CHANGE_VERSION_CONFLICT` 409、`ENTITY_ATTRIBUTE_INVALID` 422（值不在枚举、roles 为空、仍在提供却去掉 OE_PROVIDER）；锁竞争沿用 `CONCURRENT_REQUEST` 409 + Retry-After。

示例（3）：

```json
PUT /client-master-admin/v1/operating-entities/AE001/acquiring-qualification
{"acquiringQualification":"PAYFAC","expectedChangeVersion":3,"reason":"Acquirer approved PayFac programme"}
→ 200 {"entityNo":"AE001","acquiringQualification":"PAYFAC","changeVersion":4, ...}
```

#### 3.3.2 compliance

**新增内部 `POST /compliance/v1/qualifications/evaluations`（`QualificationEvaluationApi`，Controller 硬编码只允许 `X-Caller-Service: commercial-service`，否则 404 `QUALIFICATION_NOT_FOUND` 同风格拒绝）**

```json
请求 {"subjects":[{"roleType":"CLIENT","roleId":"239..."},{"roleType":"OPERATING_ENTITY","roleId":"AE001"},{"roleType":"LOCAL_PARTNER","roleId":"9000..."}]}
响应 {"evaluatedAt":"2026-10-24T08:00:00.000000Z","results":[
  {"roleType":"CLIENT","roleId":"239...","eligible":true,"status":"ELIGIBLE","qualificationType":"ORGANIZATION_KYB","qualificationId":"...","validFrom":"...","validTo":null}, ...]}
```

- subjects 1—50 个、去重；roleType ∈ CLIENT / OPERATING_ENTITY / LOCAL_PARTNER；roleId 为字符串：OPERATING_ENTITY 必须是 entity_no（`[A-Z]{2}[0-9]{3}`），CLIENT / LOCAL_PARTNER 为正十进制 ID。Admin API 的 roleId 规则相同。
- 每种角色要求的资格类型由 compliance 定（调用方不传）：CLIENT → INDIVIDUAL_KYC 或 ORGANIZATION_KYB 任一；OPERATING_ENTITY → ENTITY_UNDERWRITING；LOCAL_PARTNER → PARTNER_DUE_DILIGENCE。
- `status`：ELIGIBLE、NOT_REGISTERED（无行）、REJECTED、REVOKED、EXPIRED（status=APPROVED 但 `valid_to ≤ now`，或 status=EXPIRED，查询时实时判定，S-08）、NOT_YET_VALID（`valid_from > now`）、SETTLEMENT_BLOCKED（仅 CLIENT：`t_settlement_control` 存在 role CLIENT、SETTLEMENT_BLOCK、ACTIVE 且在生效期的行，Q1）。`eligible = status == ELIGIBLE`。
- 只读事务（READ_COMMITTED，只读），按 `uk_role_qualification` 与 `idx_settle_control_role` 查询。

**新增运营 Admin API `@HttpExchange("/compliance-admin/v1")`（`QualificationAdminApi`，Gateway Actor 头，`OperationRequestContextParser`）**

| # | Method + Path | 说明 |
|---|---|---|
| 9 | `GET /qualifications?roleType=&roleId=&qualificationType=&status=&page=&size=` | 分页列表；返回当前状态、实时判定的 `effectiveStatus`（同上）、有效期、当前决定来源、version |
| 10 | `GET /qualifications/{qualificationId}/history` | 状态变更（`t_qualification_state_change`）与对应决定（来源、操作人、依据、原因码、时间）倒序 |
| 11 | `POST /qualifications/manual-decisions` | 请求 `{roleType, roleId, qualificationType, validFrom, validTo(可空), reasonCode, basisNote(1—1000), expectedVersion(已有行时必填)}`；结果 APPROVED |
| 12 | `POST /qualifications/{qualificationId}/revocation` | 请求 `{expectedVersion, reasonCode, basisNote}`；只允许当前 APPROVED |

写接口：`AbstractProcessor` + `TransactionTemplate`（READ_COMMITTED、5s，同 `ClientReviewServiceImpl`）；一个事务内：插入 `t_qualification_decision`（`case_id` NULL、`decision_source=MANUAL`、`basis_note`、`decided_by`=actor、`override_reason_code`=reasonCode）→ upsert `t_role_qualification`（无行则插入 version=1；有行则 `WHERE qualification_id=? AND version=?` 条件更新，影响行数 ≠ 1 → 409 `QUALIFICATION_CHANGED`；`uk_role_qualification` 冲突 → 409）→ 插入 `t_qualification_state_change`（trigger_type `OPERATIONS_MANUAL`，trigger_id = decision_id，`aggregate_version` = 新 version）。登记：status APPROVED、`review_status=NOT_REQUIRED`、复审字段为空；撤销：status REVOKED、`review_status=COMPLETED`、`valid_to`=now（满足 `ck_role_qual_review`，由应用保证）。不用 Redisson（F8），并发由 version CAS + 唯一键保证。

允许的（roleType, qualificationType）组合：CLIENT + INDIVIDUAL_KYC / ORGANIZATION_KYB（演示 Client，S-09）、OPERATING_ENTITY + ENTITY_UNDERWRITING、LOCAL_PARTNER + PARTNER_DUE_DILIGENCE；其余 422 `QUALIFICATION_INVALID`。`validTo` 必须晚于 `validFrom`。compliance 不出站校验主体是否存在（F8），由 ops-bff 先查 client-master（3.3.5）。

错误码 `QualificationErrorCode`：`QUALIFICATION_NOT_FOUND` 404、`QUALIFICATION_CHANGED` 409、`QUALIFICATION_NOT_ACTIVE` 409、`QUALIFICATION_INVALID` 422；在 `ComplianceApiErrorConfiguration` 登记。

**入驻审核通过投影（S-09）：** `DecideClientReviewProcessor` 在 APPROVE 时为主 Case 额外生成一个投影命令，`ClientReviewServiceImpl.decide()` 在同一事务内 upsert `t_role_qualification`（CLIENT, clientId, Case 的 qualification_type, current_decision_id = 本次 decision, APPROVED, NOT_REQUIRED）并写 state_change（trigger_type `OPERATIONS_REVIEW`）。REJECT 时若已有 APPROVED 行则改为 REJECTED / COMPLETED。RELATED_PERSON 不投影。

#### 3.3.3 commercial

**修改 `POST /checkout-context/v1/resolutions`（请求不变，响应只加字段）**

| 位置 | 字段 | 说明 |
|---|---|---|
| 顶层 | `relationshipSnapshot` | `{effectiveModelVersion:int(0/1), snapshotModelVersion:int(1), fundFlow, businessMode, bindings:[{role, subjectType, subjectId(可空), partyId(可空)}]}`，bindings 按 role 固定顺序 |
| `legal` | `seller` | `{legalName, registrationNumber, taxRegistrationNumber, addressText, supportEmail, supportPhone}`，透传选中披露的公开文本（S-01） |
| `clientTerms.capabilities` | 行为变化 | `paymentMethodCode` 省略时返回全部 `capabilityKind=PAYMENT_METHOD`、ENABLED、在时间窗、CURRENCY / CONSUMER_COUNTRY 条件允许、且有生效价格的开通项；结果为空不落空 |

错误码与状态不变（S-10 / S-11）。新的落空原因只记日志，不新增码。

**compliance 客户端：** `ComplianceQualificationClientImpl implements CheckoutQualificationClient`，接口改为 `Map<SubjectKey, Eligibility> evaluate(Collection<SubjectKey>)`；装配照 `CheckoutResolutionConfiguration.java:80-88`（`compliance-service`，`HttpServiceClientOptions.strict().withStrictJsonResponses(262144)`，`QualificationErrorCode.values()` 作为合同错误，连接 2s / 读 3s，无重试）；任何 4xx / 5xx / 超时 / 解析失败 → `OWNER_UNAVAILABLE`（503，失败关闭）。删除 `UnavailableCheckoutQualificationClient`。`commercial-core` 依赖 `paytech-compliance-api`。测试 `application-mock.yml` 增加 `compliance-service` simple-discovery 实例。

**写入时近似校验（S-14 / AC-10）：** `ReplaceCommercialTermPricesProcessor` 在 term_type=PLATFORM_SERVICE_CHARGE 时、`ActivateAgreementProcessor` 在 PARTNER 合同且该合同有 PLATFORM_SERVICE_CHARGE 方案时，校验合同 `t_agreement_party` 中每个 OPERATING_ENTITY 角色行的 party_id 都等于 LOCAL_PARTNER 角色行的 party_id；不符抛 `BizCheckFailException`（中文，含 agreementId 与两个 partyId），沿用既有 422 映射。只影响之后的写入，不回查存量。

#### 3.3.4 acquiring

**`CheckoutCommercialPortImpl`（REAL）：** `CommercialClientConfiguration` 照 `RiskClientConfiguration` 写法（`@ImportHttpServices(group="commercial-service", types=CheckoutContextApi.class)`，`withStrictJsonResponses(262144)`），`application.yml`：`spring.http.serviceclient.commercial-service.base-url: lb://commercial-service`、connect 2s、read 3s。请求：clientId、orderNo、consumerCountryCode、currency、`locale`（端口新字段，取订单冻结的 locale，现为 `en`）、contextAsOfTime（订单 create_time，6 位小数 UTC），不传 paymentMethodCode；Header 恰好一个 `X-Caller-Service: acquiring-service`。

**响应 → `CommercialFacts` 映射：** entityNo、sourceType、localPartnerId（`partnerTerms.localPartnerId`）、industry / mcc（classification）、affiliationId / scopeId / sourceRelationId（eligibility）、clientPricingVersionId（`clientTerms.versionId`）、partnerShareVersionId / platformChargeVersionId / platformServicePartyId / partnerAgreementId / partnerAgreementOperatingEntityId（partnerTerms）、clientAgreementId、sellerDisclosureId、seller（`legal.seller`）、consumerTerms / refundPolicy / privacyNotice（按 `legal.*DocumentId` 在 `legal.documents` 中取 fileId / contentHash）；新增 `RelationshipFacts relationship`（snapshot）与 `List<CapabilityFacts> capabilities`、`businessMode`（不落库，二期使用）。ID 文本解析失败 → `CheckoutUpstreamProtocolException`（502）。

**错误映射（S-11）：**

| commercial | acquiring |
|---|---|
| 422 NO_ELIGIBLE_ENTITY、409 CCT_CONTEXT_AMBIGUOUS | 422 `CLIENT_CONFIGURATION_NOT_READY` |
| 422 CLIENT_NOT_ELIGIBLE | 422 `CHECKOUT_NOT_ELIGIBLE` |
| 409 CCT_CONTEXT_CHANGED | 重试一次；仍 CHANGED → 503 `DEPENDENCY_UNAVAILABLE` |
| 503 OWNER_UNAVAILABLE、其他 5xx、无实例 | 503 `DEPENDENCY_UNAVAILABLE` |
| 读超时 | 504 `DEPENDENCY_TIMEOUT` |
| 404 CLIENT_NOT_FOUND、422 INVALID_CHECKOUT_QUERY、其他 4xx、响应格式不符 | 502 `UPSTREAM_PROTOCOL_ERROR`（日志带 commercial code） |

**开关：** `acquiring.commercial.resolution-mode`：MOCK（默认，`matchIfMissing`）注册现有固定 Mock（从 `CustomerCheckoutIntegrationConfiguration` 移到 `CommercialMockConfiguration`）；REAL 注册 `CheckoutCommercialPortImpl`。MOCK 下 `relationship` 为空：不写绑定行，`fund_flow` / `snapshot_model_version` 为 NULL，`effective_relationship_model_version=0`。

**冻结：** `CheckoutInitializationContext.buildFrozen` 增加绑定行与三列；`validateCommercial` 增加：relationship 非空时 fundFlow / 版本取值合法、bindings 角色不重复、SELLER / ACQUIRING_OE / CLIENT 必在、ACQUIRING_OE.subjectId = entityNo。`CheckoutCreationServiceImpl` 同事务批量插入 `t_order_relationship_binding`。重放：`client_order_id` 唯一约束不变，重复请求不重复解析（既有 `checkClientOrderId` 在解析前）。

**Client API：** 合同不变；`client-v1.yaml` 中 `CLIENT_CONFIGURATION_NOT_READY` / `CHECKOUT_NOT_ELIGIBLE` / 依赖类错误的 description 补充真实解析下的含义。

#### 3.3.5 operations-console-bff

| # | BFF Method + Path（外部 `/api/operations` 前缀） | 下游 | 类型 |
|---|---|---|---|
| B1 | `GET /entity-admin/v1/operating-entities` | client-master 1 | 转发 |
| B2 | `GET /entity-admin/v1/operating-entities/{entityNo}` | client-master 2 + compliance 9（OE 与提供方资格） | 聚合（资格部分失败标 UNAVAILABLE，同 `ClientReviewDetailsProcessor`） |
| B3 | `PUT /entity-admin/v1/operating-entities/{entityNo}/acquiring-qualification` | client-master 3 | 转发 |
| B4 | `GET /entity-admin/v1/local-partners` | client-master 4 | 转发 |
| B5 | `GET /entity-admin/v1/local-partners/{localPartnerId}` | client-master 5 + compliance 9 | 聚合 |
| B6 | `PUT /entity-admin/v1/local-partners/{localPartnerId}/roles` | client-master 6 | 转发 |
| B7 | `GET /qualification-admin/v1/subjects?roleType=&subjectRef=` | client-master 2 / 5 / 7 | 聚合：把运营输入的 entityNo / localPartnerId / clientId 解析为 `{roleType, roleId, displayName, status}` |
| B8 | `GET /qualification-admin/v1/qualifications` | compliance 9（列表行补主体名称：按页内主体批量查 client-master，失败则只显示 ID） | 聚合 |
| B9 | `GET /qualification-admin/v1/qualifications/{qualificationId}/history` | compliance 10 | 转发 |
| B10 | `POST /qualification-admin/v1/qualifications/manual-decisions` | 先 B7 逻辑核对主体存在且非终态，再 compliance 11 | 聚合（顺序：查询在前，单次写） |
| B11 | `POST /qualification-admin/v1/qualifications/{qualificationId}/revocation` | compliance 12 | 转发 |

客户端照 `ClientReviewClientConfiguration.factory`（`compliance-service` / `client-master-service`，测试基址属性 `operations.client-review-risk-test-base-url` / `operations.cct-test-base-url` 复用）；`OperatorContext` 透传 Actor 与 Session；请求配置照 `ClientReviewRequestConfiguration`（严格 JSON、64 KiB）。

### 3.4 数据库

#### 3.4.1 client-master（`mor_client_master`）

| 表 | 变更 | 字段 |
|---|---|---|
| `t_operating_entity` | 加 1 列 | `acquiring_qualification VARCHAR(64) NOT NULL`（存量回填 `PAYFAC`，保持现有候选不被过滤，运营再按实情调整） |
| `t_local_partner` | 不改结构 | `affiliation` 本轮不做（用户 2026-10-24 决定） |
| `t_partner_role`（新） | 7 字段 | `partner_role_id BIGINT PK`、`local_partner_id BIGINT`、`role_code VARCHAR(64)`（OE_PROVIDER / REFERRER）、`status VARCHAR(64)`（ACTIVE / ENDED）、`active_slot TINYINT(1) NULL`、`create_time`、`update_time`；`uk_partner_role_active(local_partner_id, role_code, active_slot)`、`idx_partner_role_code(role_code, status)` |
| `t_party_role`（新） | 10 字段 | `party_role_id BIGINT PK`、`party_id BIGINT`、`role_code VARCHAR(64)`（CLIENT / OPERATING_ENTITY / OE_PROVIDER / REFERRER / SPLIT_PAYEE）、`subject_id VARCHAR(64) ascii_bin`（clientId / entityNo / localPartnerId）、`status`（ACTIVE / ENDED）、`active_slot`、`effective_from`、`effective_to NULL`、`create_time`、`update_time`；`uk_party_role_active(role_code, subject_id, active_slot)`、`idx_party_role_party(party_id, status)` |
| `t_client_partner_relation`（新，只建表） | 9 字段 | `relation_id BIGINT PK`、`client_id`、`local_partner_id`、`relation_type VARCHAR(64)`（REFERRAL）、`status`、`effective_from`、`effective_to NULL`、`create_time`、`update_time`；`uk_client_partner_rel(client_id, local_partner_id, relation_type, effective_from)`、`idx_client_partner_rel_partner(local_partner_id)` |
| `t_entity_source_relation` / `t_operating_entity_application` | 只改注释 | `source_type` 取值删除 PLATFORM（PDManer 规则文本 `scripts/pdmaner/client-contract-model-core.mjs:304` 只改文档，不运行生成器） |

新表都是 OE / LP / Client 根的子行（S-12），BCR `record_type` 白名单不变（写入推进的是根）。`t_party_role` 的写入方：本轮回填 + Admin API 6 维护 OE_PROVIDER / REFERRER + Client 投影（`ClientProjection` 新建 Client 时同事务写 CLIENT 行）；本轮没有读取方，是给后续步骤的骨架。

同步：PDManer `client-master.pdma.json`（直接编辑，不跑生成器）、`client-master-schema.sql`、测试 `init/ddl.sql`、`ClientMasterDataCleaner.TABLES`（+3）、`table-ownership.yaml` 生成器 `stepAddedTables` 与 `TableOwnershipAutoTest` 计数（+3）、增量 `docs/database/sql/changes/2026-10-24-relationship-snapshot.sql`。

**DML（`changes/2026-10-24-relationship-snapshot-data.sql`，mor-test 用，不保留历史）：** ① 回填 `acquiring_qualification`，然后 MODIFY 为 NOT NULL；② 新建“我们的 PayFac”Partner：一条 `t_local_partner`（party_id = AE001 的 party_id，ACTIVE）+ BCR 根 V1 + `t_partner_role`(OE_PROVIDER)；③ AE001 的 `t_entity_source_relation` 直接 UPDATE：`source_type` PLATFORM → PARTNER、`source_partner_id` = 新 Partner，并推进 AE001 根 BCR 版本；其他 PLATFORM 来源行同样处理（阶段 6 先列清单）；`t_operating_entity_application.source_type` 的 PLATFORM 行改为 PARTNER；④ `t_partner_role` 回填 PARTNER 来源 Source 行引用的 LP 为 OE_PROVIDER；⑤ `t_party_role` 回填 Client / OE / OE_PROVIDER。手工 ID 用 `900000000001370000 + ROW_NUMBER()`（每表独立号段，行数 < 1 万）。回滚：按号段删除插入的行、来源行改回 PLATFORM、DROP 列（须与 commercial / acquiring / risk 代码回滚同时进行，因为新代码已不认 PLATFORM）。

**commercial DML（`commercial/docs/database/sql/changes/2026-10-24-payfac-partner-agreement.sql`，mor-test）：** 为“我们的 PayFac”Partner 建 PARTNER 合同（`t_agreement` + `t_agreement_party`：LOCAL_PARTNER、OPERATING_ENTITY = AE001 Party、PLATFORM_SIGNATORY = 平台主体 Party，签署文件，`t_agreement_operating_entity`(AE001)），以及 PARTNER_SHARE / PLATFORM_SERVICE_CHARGE 两个方案和生效版本；价目先按 0 占位，阶段 6 确定。优先走 commercial 运营 API 创建（留审计），SQL 只作兜底。

**acquiring / risk-decision DML：** `t_commerce_order.entity_source_type`、risk 结账输入快照中的 PLATFORM 直接改为 PARTNER；risk 初始数据目录行 `ENTITY_SOURCE_VALUES / PLATFORM` 删除（初始化 DML 文件同步）。

#### 3.4.2 compliance（`mor_compliance`）

| 表 | 变更 |
|---|---|
| `t_qualification_decision` | `case_id` 改 NULL；`role_id` 由 BIGINT 改为 `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`（OE 存 entity_no，其余存十进制 ID；存量数值无损转换）；加 `basis_note VARCHAR(1000) NULL`；`decision_source` 注释加 MANUAL（只改注释）；`uk_qual_decision_ver(case_id, decision_version)` 对 NULL 不约束，无影响 |
| `t_role_qualification` | `role_id` 同上改为字符型（`uk_role_qualification` 随列重建，无存量数据）；补 DataObject / Mapper / Repository |
| `t_qualification_state_change` | 不改结构；补 DataObject / Mapper / Repository |
| `t_due_diligence_case` / `t_settlement_control` | 不改：本轮没有 OE 尽调案件，结算控制只涉及 CLIENT / LOCAL_PARTNER；以后做 OE 尽调时再把案件表 `role_id` 统一为字符型。现有 `QualificationDecision.roleId` 由 long 改 String，入驻审核写决定时转为十进制文本 |

表数不变（25），`EXPECTED_TABLE_COUNT` 不变。同步 PDManer、schema、测试 DDL、`COMPLIANCE_PHYSICAL_DECISIONS.md`、`scripts/pdmaner/risk/model-policy.mjs` 中 `ck_qual_decision_source` 的说明文本（只改文档，不运行生成器）。

**DML（`changes/2026-10-24-qualification-backfill.sql`）：** 已 APPROVED 的 CLIENT 主 Case 决定（每个 Client 取最新一条）→ `t_role_qualification`（`qualification_id = decision_id`）+ `t_qualification_state_change`（`change_id = decision_id`，trigger_type `BACKFILL`，trigger_id = decision_id）。AE001、我们的 PayFac Partner 与演示 Client 的资格不写 SQL，在阶段 7 经运营页手工登记（留审计）。回滚：按 `trigger_type='BACKFILL'` 删除。

#### 3.4.3 acquiring（`mor_acquiring`）

| 表 | 变更 |
|---|---|
| `t_order_relationship_binding`（新，8 字段） | `binding_id BIGINT PK`、`order_id BIGINT`、`role_code VARCHAR(64)`、`subject_type VARCHAR(64)`、`subject_id VARCHAR(64) NULL`、`party_id BIGINT NULL`、`snapshot_model_version INT`、`create_time`；`uk_order_relationship_role(order_id, role_code)` |
| `t_order_context` | 加 `fund_flow VARCHAR(32) NULL`、`effective_relationship_model_version INT NOT NULL`（存量回填 0）、`snapshot_model_version INT NULL`（阶段 1 稿的 `observed_snapshot_version` 改名，含义为“记录下来的快照按哪版规则推导”） |

同步 PDManer、schema、测试 DDL、清理器、表归属（+1），DataObject / Mapper（`OrderContextMapper.xml` resultMap 同步）。DML：仅 `UPDATE t_order_context SET effective_relationship_model_version = 0` 后 MODIFY NOT NULL（写在 DDL 增量文件内）。

#### 3.4.4 Platform 注册块 137（`db/platform-support/registration-block137.sql`，默认休眠）

| 类别 | ID（900000000000137xxx） | 内容 |
|---|---|---|
| 权限 | 137101—137106 | `QUALIFICATION_READ` / `QUALIFICATION_MANAGE`(HIGH)、`OPERATING_ENTITY_READ` / `OPERATING_ENTITY_MANAGE`(HIGH)、`LOCAL_PARTNER_READ` / `LOCAL_PARTNER_MANAGE`(HIGH) |
| 角色授权 | 137201—137206 | 角色 900000000000000510，REVOKED |
| 菜单 / 按钮 | 137301—137306 | MENU `OPERATIONS_QUALIFICATIONS`（`/compliance/qualifications`）、`OPERATIONS_OPERATING_ENTITIES`（`/master-data/operating-entities`）、`OPERATIONS_LOCAL_PARTNERS`（`/master-data/local-partners`）；三个 MANAGE 按钮；父菜单与排序阶段 4 按 mor-test 现行菜单定 |
| 路由 | 137401—137411 | B1—B11 |
| Header / Query / Guard | 1375xx / 1376xx / 1377xx | 照块 136：每路由 authorization + origin，写路由加 content-type / idempotency-key；Query 参数 status、sourceType、country、role、roleType、roleId、subjectRef、qualificationType、page、size；Guard 每路由 6 行，数值照块 134 |

另有 `test-activate.sql` / `test-deactivate.sql` 与 `release/sql/` 下的发布副本；写 SQL 前再核对一次 137 未被占用，并按“休眠 / 激活”两种状态离线编译 Gateway 快照。

### 3.5 页面（原型：`operations-console-web/docs/operations-console.pen`，画板前缀 `RS-`）

画板（y=93000 一行）：RS-01 资格登记 · 列表与主体查找 `z0k1u`、RS-02 手工登记对话框 `b98kbs`、RS-03 历史与撤销 `i9bB4`、RS-04 运营主体 · 列表 `j6iqx`、RS-05 运营主体 · 详情 `EQdR9`、RS-06 修改收单资质对话框 `DzAdB`、RS-07 Partner · 列表 `NLryG`、RS-08 Partner · 详情与关系属性 `w5Vpid`。

| 菜单 | 视图 | 关键内容与交互 |
|---|---|---|
| 资格登记 | 列表 + 主体抽屉 | 顶部“查找主体”：角色（Client / OE / Partner）+ 编号 → 主体卡（名称、状态、各资格类型当前状态）；列表筛选角色 / 资格类型 / 实时状态；行操作：查看历史（时间线：来源 MANUAL / REVIEWER / BACKFILL、操作人、依据）、登记（对话框：资格类型、有效期起止、原因码、依据说明，确认页复述影响“登记后新订单解析会把该主体视为合格”）、撤销（二次确认 + 依据）；版本冲突提示刷新 |
| 运营主体（OE） | 列表 | 筛选状态 / 来源 / 国家；列：编号、法定名称、来源、收单资质徽标、提供方、资格摘要 |
| | 详情 | 头部（编号、名称、状态、来源）；“收单资质”卡（三档说明 + 修改对话框：选择档位、原因，提示 MERCHANT 将使其不再承接 Client）；“提供方”卡（Partner 名称、资格状态，链接到 Partner 详情）；“准入范围”表（国家 / 币种 / 行业 / MCC，只读）；“卖方披露”表（国家、locale、状态、生效期，只读，附精确匹配说明）；“资格”卡（ENTITY_UNDERWRITING 当前状态，跳转资格登记并预填主体） |
| Partner | 列表 | 筛选状态 / 角色；列：编号、名称、角色标签、提供的 OE 数、资格摘要 |
| | 详情 | 头部；“角色”卡（角色多选对话框；仍提供 OE 时 OE_PROVIDER 不可取消并给出原因）；“提供的运营主体”表；“资格”卡（PARTNER_DUE_DILIGENCE） |

通用：只读用户看不到写按钮；资格聚合不可用时卡片显示“暂不可用，可重试”，不阻塞主档；所有文案走 i18n，新命名空间 `qualification`、`master-data`（登记 `namespaces.ts`、`builtin.ts`、`src/i18n/en/*.json`、`i18n/zh-CN/*.json`，菜单 `nav.resource.OPERATIONS_QUALIFICATIONS` 等），发布前在 Platform 建命名空间再导入。

### 3.6 研发与验证计划（阶段 4 / 5）

| 顺序 | 系统 | 主要工作 | 测试入口（auto，本地 local-environment） | 覆盖 AC |
|---|---|---|---|---|
| 1 | compliance | Schema；资格 DO / Mapper；评估 API；Admin API；入驻通过投影 | HTTP：评估（7 种状态、调用方、上限 50）；手工登记 / 撤销（CAS 冲突、组合非法）；审核通过 / 驳回后资格行 | AC-02、03 |
| 2 | client-master | Schema + 回填 / 迁移 DML；候选响应扩展；删除 PLATFORM 来源；Admin API 1—7 | HTTP：候选含新字段（PARTNER 来源 + LP、卖方文本）、PLATFORM 来源不再出现；verify 感知资质修改；Admin 写入 CAS / 锁 / BCR / 子表 | AC-01、13 |
| 3 | commercial | 删除 PLATFORM 来源；快照推导；compliance 客户端 + DTO Mock 端点 `MockQualificationEvaluationApi`；Processor 顺序；省略支付方式；写入校验；开关 | HTTP 解析：首次成功路径、Client 不合格（含无候选）、候选资格落空取下一个、资质三档、Party 不符、C1 / C2 快照、开关 0 / 1、卖方文本、compliance 不可用 503；价目替换 / 合同激活的 Party 校验 | AC-04、05、06、08、10 |
| 4 | acquiring | 删除 PLATFORM 来源（枚举、校验、Mock 改为 AE001 + PARTNER）；端口模型；REAL 客户端 + `MockCheckoutContextApi`；13 个测试类迁移；冻结与表 | HTTP 建单：REAL 成功冻结（绑定行、三列、卖方文本）、错误映射全表、CHANGED 重试一次、MOCK 与现状一致 | AC-07、08、09 |
| 4b | risk-decision | 删除 PLATFORM 来源：输入校验、规则校验、注释、初始数据目录行 | HTTP 结账评估与规则发布校验拒绝 PLATFORM | AC-13 |
| 5 | ops-bff | 两组客户端；B1—B11；聚合降级 | HTTP：转发、聚合部分不可用、登记前主体核对 | AC-01、02 |
| 6 | ops-web | 3 个页面、i18n | 组件测试（含中文字面量守卫）；浏览器：英文默认 + 中文切换 | AC-12 |
| 7 | platform-support | 块 137 SQL、离线编译 | 休眠 / 激活两态 Gateway 快照编译通过 | — |

各系统只跑本服务测试，不跑全 reactor（共享本地库）。阶段 5 本地联调：compliance → commercial → client-master → acquiring（REAL）用本地 e2e 环境走一笔 AE001 USD 建单，并直连解析验证 PARTNER 来源快照（夹具数据）。本地 SQL（各库 DDL / DML）需要在阶段 4 开始前单独授权（目标库 `mor_client_master`、`mor_compliance`、`mor_acquiring`，root 手工执行，先 mysqldump 相关表）。

### 3.7 风险与阶段 6 待核

| 项 | 说明 |
|---|---|
| 卖方披露 locale / 国家 | 披露按（OE, 消费者国家, locale）精确匹配；acquiring 传 `en`，mor-test 需有 AE001 × US × en 的已发布披露，否则 REAL 每单 `CLIENT_CONFIGURATION_NOT_READY` |
| AE001 根 BCR | 若 mor-test 的 OE 是手工插入、没有根 BCR，Admin 写入会补建；verify 根集合也依赖它，阶段 6 先查 |
| commercial 在 Nacos 注册 | acquiring 用 `lb://commercial-service`；mor-test 未核实（billing 用直连 URL），阶段 6 核对，必要时 acquiring 用直连 base-url |
| 时钟 | contextAsOfTime 取 acquiring 时钟，commercial 拒绝未来时间（映射 502）；mor-test 同宿主机，风险低 |
| 存量 OE 资质 = PAYFAC | 为保持行为不变的回填值，不代表真实资质；阶段 7 后由运营按实情修改 |
| billing / ledger 口径变化 | AE001 迁为 PARTNER 来源后，切 REAL 起 AE001 订单按 Partner 分成 + 平台服务费计费，ledger 在 AE001 账本与平台主体账本之间记服务费（用户 2026-10-24 接受）；GAP-PARTNER（code-mor-78）是 AC-11 硬前置，GAP-ISSUER 未解决前 CARD 计费可能 422；价目先按 0 占位 |
| 删除 PLATFORM 的回滚 | 新代码不认 PLATFORM，数据已改写；回滚须代码与数据一起回（数据回滚脚本把来源改回 PLATFORM） |
| 其他 PLATFORM 来源数据 | mor-test 其他服务（risk 规则、reconciliation / ledger 中按来源的配置）是否存有 PLATFORM 取值，阶段 6 全库检索后列清单 |

## 4. 研发结果（阶段 4，2026-10-25）

> 代码在独立 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-relsnap`（基于 main 8a86a31c，未提交）。约 185 个已有文件修改 + 167 个新文件。

### 4.1 各系统结果

| 系统 | 主要内容 | 本地 SQL（已备份后执行） | 测试 |
|---|---|---|---|
| compliance | Schema（case_id 可空、role_id 字符型、basis_note）；资格 DO / Mapper；评估 API；Admin API 4 个；入驻审核投影；`/compliance-admin` 日志只记元数据 | mor_compliance：DDL + 回填（本地无数据） | 全量 auto 437 通过；日志 + Admin 复跑 38 通过 |
| client-master | `acquiring_qualification`、3 张新表；删除 PLATFORM 来源；候选响应新字段；Admin API 7 个；Client 创建写 CLIENT 角色；数据 SQL（AE001 迁 PayFac Partner，前后置检查失败即中止） | mor_client_master：DDL；数据文件用合成数据验证正常 / 中止 / 回滚三种情况后清空 | 全量 auto 100 通过；arch-rules 68 通过 |
| commercial | 快照推导与组合约束；compliance 客户端；解析顺序；省略支付方式；写入时 Party 校验；开关；删除 PLATFORM 来源；修复 `CapabilityConditions` 缺 equals 导致带开通项时 verify 必报 CHANGED 的既有缺陷；PayFac Partner 合同种子草稿（未执行） | 无 Schema 变更 | 全量 211 通过 |
| acquiring | REAL 客户端与错误映射；MOCK / REAL 开关（测试默认走 REAL + HTTP DTO Mock）；快照冻结表与三列；删除 PLATFORM 来源（Mock 改 AE001 + PARTNER） | mor_acquiring：DDL + 注释（本地无订单） | 全量 auto 897 通过 |
| risk-decision | 删除 PLATFORM 来源（输入 / 规则校验、目录行、注释） | mor_risk_decision：删目录行、改注释 | 相关 188 通过 |
| ops-bff | B1—B11，聚合降级，主体核对 | — | 95 通过 |
| ops-web | 资格登记、经营主体、Local Partner 三页；两个新命名空间；风控规则编辑器删除 PLATFORM | — | 1519 通过、tsc、构建、i18n 检查通过 |
| platform-support | 块 137（150 行，休眠）独立文件 + 追加到 initial-data | 未执行 | 初始数据脚本测试与 HEAD 同样的既有失败 |
| 文档 | AGENTS.md、requirements-site、数据库文档、决定草案（PLATFORM 来源删除、AE001 迁移注记） | — | requirements-site 137 个中 10 个失败，与 HEAD 相同的既有失败 |

### 4.2 与第 3 节的偏差（已接受）

- compliance：评估不开只读事务（READ_COMMITTED 下无意义）；结算阻断只在资格本身合格时报告；列表分页沿用 Foundation 的 10/20/50/100/200。
- client-master：Admin API 与分类 Admin API 一样受 `onboarding.draft-enabled` / `recovery-enabled` 开关控制（ID 生成与锁来自那里）；数据 SQL 不写审计快照（完整性哈希需应用计算）；库里残留 PLATFORM 时候选查询 503（失败关闭）。
- commercial：枚举命名 `RelationshipSubjectType`；写入时 Party 不符返回 400 `BUSINESS_CHECK_FAILED`（沿用 `BizCheckFailException` 的既有映射，非 422）；SQL 兜底建合同不写审计、不发事件，优先用运营 API。
- acquiring：commercial 响应缺快照时按版本 0 容忍；非 Problem 的 5xx 映射 503；`effective_relationship_model_version` 名称超出 32 字符的默认上限，保留已批准名称。
- ops-bff：B8 主体名称逐个并发查询（client-master 无批量接口）；B10 同样拦截已终止的 Client。
- ops-web：列表页不显示资格摘要（列表接口不返回资格）；资格列表按存储状态筛选，实时状态作为列。

### 4.3 跨系统核查（独立代理，只读）

接口字段、错误码、分页上限、网关路由 / 参数白名单、事务边界、CAS 均一致。提出并已处理：发布顺序（2.4 修订：commercial 与 client-master 连续发布、risk-decision 在 acquiring 之后、切 REAL 门禁）；ops-web 风控规则编辑器删除 PLATFORM；compliance 回填改为与线上投影一致（取最近一次审核决定）；client-master 数据 SQL 改为前后置检查失败即中止。时钟偏差导致 502 的风险接受（同宿主机）。

### 4.4 验收映射

| AC | 覆盖 |
|---|---|
| AC-01 | client-master Admin HTTP 测试 + BFF 测试 + ops-web 页面 |
| AC-02 / 03 | compliance 评估 / Admin / 投影测试 |
| AC-04 / 05 / 06 / 08 / 10 | commercial `ResolveCheckoutRelationshipHttpTest`、开关测试、价目 / 激活 Party 校验 |
| AC-07 / 09 | acquiring `CreateCheckoutCommercialResolutionTest`、`CreateCheckoutMockResolutionTest` |
| AC-11 | 阶段 5 本地联调 + 阶段 7 mor-test |
| AC-12 | 阶段 5 浏览器验收（英文默认 + 中文切换） |
| AC-13 | 各系统删除 PLATFORM 的测试 + 文档 |

### 4.5 遗留到阶段 5 / 6

- 本地联调（compliance → commercial → client-master → acquiring REAL）与浏览器验收尚未做。
- compose 增加 `ACQUIRING_COMMERCIAL_RESOLUTION_MODE`、`COMMERCIAL_RELATIONSHIP_SNAPSHOT_ENABLED`；核对 mor-test 上 commercial 是否在 Nacos 注册。
- MOCK 模式下 Mock 的 Partner 占位版本号会随支付成功事件进入 billing（与原 Mock 104 / 105 一样会 404 进 DLQ），切 REAL 后消失。
- 被改写为 PARTNER 的历史订单没有 Partner 合同字段，不满足“PARTNER 字段齐全”的约束（数据文件已注明）。
- Platform 多语言需先建 `qualification`、`master-data` 命名空间再导入。
- risk-decision 改写历史输入快照后，其 `stable_input_hash` 不变，同一订单重新评估会被视为内容不同（文件已注明；mor-test 先查是否有此类快照）。

## 5. 本地联调与浏览器验收（阶段 5，2026-10-25）

证据目录：worktree `Code-MoR-relsnap` 下本需求 `verification/local-e2e/`（`stage5-report.md` / `stage5-result.json`、`stage5-browser-report.md` / `stage5-browser-result.json`、`screenshots/` 48 张、脚本与本地种子 SQL）。

### 5.1 环境

- 本地共享 e2e 栈实际使用 `mor_*_v2e2e` 库（`mor_*` 为自动测试库），授权中的“本地 mor_platform”等按此落在对应 v2e2e 库；执行前均已备份（scratchpad `backup/relsnap-stage5/`、`backup/relsnap-stage5-browser/`）。
- 新起 compliance 18112、client-master 18101、commercial 18108（client-master / commercial / compliance 的 v2e2e 库原不存在，按 worktree Schema 新建）；替换 acquiring 18102、risk-decision 18104、ops-bff 18201（旧 JAR 保留为 `*.pre-relsnap` 可回切）。acquiring 验收结束后回到 MOCK。
- 块 137 在 `mor_platform_v2e2e` 执行并激活（Gateway 路由 464 → 475）；9 条旧 acquiring e2e 订单与 9 条 risk 输入快照改写为 PARTNER。
- 浏览器验收用 worktree ops-web 独立 Vite 18311（经 nginx 18444 → Gateway，与 18301 同入口），18301 未动，18311 已停。
- 本地 Platform 多语言：`mor_platform_v2e2e` 原无任何命名空间 / 发布；补执行多语言管理 `db/dml.sql` 第 3 部分（文件检查策略 + 存储路由，按本地路由 id 改写，文件 `stage5-i18n-file-part3-local.sql`），经运营 API 建 23 个命名空间、导入 8813 个 key 与 zh-CN，发布 #2（默认语言 en，46/46 READY）。

### 5.2 后端联调：44/44 通过，无代码修改

- commercial 直接解析：MOR 8 个、PSP 9 个角色绑定，卖方公开文本正确。
- 失败路径：Client 不合格 422、OE 资格撤销 → NO_ELIGIBLE_ENTITY、compliance 停止 → 503、MERCHANT_RESALE_APPROVED 档位挡 PSP。
- acquiring REAL：建单 201，关系绑定 / 资金路径 / 模型版本 / 卖方文本冻结；Client 资格撤销 → CHECKOUT_NOT_ELIGIBLE；MOCK 行为不变。
- B1—B11 经 Gateway 全部正常，含 409 / 422。

### 5.3 浏览器验收（AC-12）：30/30 通过（英文 15、中文 15）

- 菜单（风控管理 / 商务下）、资格页（筛选入 URL、主体查找、历史时间线、按角色限定资格类型、确认步骤说明对新订单的影响、真实撤销与重新登记、旧版本 409 + 刷新恢复）、经营主体页（来源 / 国家筛选、AE001 详情各卡、MERCHANT 提示与档位往返）、Partner 页（角色筛选、PayFac 详情、OE_PROVIDER 锁定及原因、REFERRER 增删）。
- 中文：页头切换语言，新页面无原始 key、无未翻译英文。网络 / 控制台只有预期的 409、脚本快速刷新触发的一次 429（重试恢复）和既有代码的两条 antd 废弃告警。
- 种子数据已恢复业务取值（PSP Client APPROVED / ELIGIBLE、AE001 PAYFAC、PayFac Partner 只有 OE_PROVIDER）；版本号因多次真实写入后还原而增大。验收后 commercial 直接解析 MOR / PSP 仍为 200。

### 5.4 阶段 5 内的修复（worktree ops-web）

1. 四个新对话框 `maskClosable` 改为 `mask={{ closable }}`（antd 废弃告警；`QualificationDialogs.tsx`、`MasterDataModals.tsx`）。
2. 历史时间线触发类型文案去重：OPERATIONS_MANUAL “Operations Action / 运营操作”，OPERATIONS_REVIEW “Operations Review / 运营审核”（`qualification.json` en / zh-CN）。

修复后 `npm test` 1519 通过、`tsc --noEmit` 通过、i18n 导出检查通过（23 个命名空间、8813 个 key）。

### 5.5 遗留与阶段 6 输入

- **平台签约方主体资料：** commercial 核对合同各签约方的已审核主体资料；mor-test 建 PayFac Partner 合同前须确认平台占位主体 880000000701 在 client-master 有已审核资料，否则切 REAL 后每单 409 CCT_CONTEXT_AMBIGUOUS。
- **侧栏中文会被覆盖：** fc00257f 在 Platform 直接改了 `nav.sidebar.product/hide/show` 的中文，仓库 `i18n/zh-CN/nav.json` 仍为英文；mor-test 只导入 6 个新菜单 key，或先更新仓库种子。`qualification`、`master-data` 命名空间须先建。
- **小的界面问题（未改）：** Partner 角色对话框“原因”无必填星号（已校验）；查找按钮比输入行略低（与既有搜索栏一致）；列表不显示资格摘要（4.2 已接受）。
- 其余见 4.5：compose 两个开关、commercial 的 Nacos 注册、mor-test PLATFORM 来源行清单与 AE001 根 BCR、卖方披露 AE001×US×en、Partner 合同价目（0 占位）、GAP-PARTNER、块 137 离线编译 Gateway 快照、mor-test 镜像基线。
- 本地状态保留：e2e 栈运行新服务（acquiring MOCK）、本地 Platform 已发布 zh-CN（18301 页面将出现语言菜单）、新增的文件策略与路由保留。
