# client-master：业务信息补采 · 技术方案

> 状态：**阶段 2 已确认（2026-10-29，S-01—S-12 按建议）；第 7—13 节为阶段 3 评审稿（2026-10-29，设计稿，未改代码 / 未执行 SQL）**。页面原型由主会话绘制（"原型 门户 · 业务信息补采"、"运营 · 申请页签业务信息"），确认前不写前端。
> 依据：[2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)（REQ-12）；需求 [requirement.md](./requirement.md)（Q1 commercial 产品能力目录、Q2 无网站个人可选、Q3 交付方式四值单选，2026-10-29 已确认）。

路径缩写：`cm/` = `paytech/client-master/paytech-client-master-core/src/main/java/com/paytech/client/master/`；`cm-api/` = `paytech/client-master/paytech-client-master-api/src/main/java/com/paytech/client/`；`cm-sql/` = `paytech/client-master/docs/database/sql/`；`ccbff/` = `paytech/client-console-bff/paytech-client-console-bff-app/src/main/java/com/paytech/client/console/bff/`；`obff/` = `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/`；`cweb/` = `client-console-web/src/`；`oweb/` = `operations-console-web/src/`。

## 1. 核对范围与证据

| 范围 | 证据位置 |
|---|---|
| 经营资料表 | `cm-sql/client-master-schema.sql:229-246` `t_client_business_profile`：每 Client 一行（`uk_client_business_client`），列 `business_model_code / website_url / expected_volume / expected_ticket / currency / business_description / content_hash` 与分类确认三列；**同一行既是草稿也是已批准资料**（注释"Client 单份经营资料稳定标识，不是版本标识"） |
| 草稿读写 | `cm/../resources/mapper/ClientDraftMapper.xml:101-111`（读 `b.*` 为 `businessXxx`）、`:137-138`（建行）、`:198-204`（`saveBusiness` 按脏字段逐列更新）；`cm/core/dal/dataobject/ClientDraftFields.java:45` 起（record，一列一个组件）；`cm/domain/onboarding/ClientDraftValues.java:49-55`（扁平为 `business.<field>` 字符串）；`cm/domain/onboarding/ClientDraftPatch.java:86-91`（固定字段表 `Map<path, Field(Kind, length)>`，`Kind` = TEXT / DATE / COUNTRY / CURRENCY / URL / MONEY / EMAIL / PHONE / BOOLEAN）、`:281-285`（URL 接受 http 与 https） |
| 草稿 API 形状 | `cm-api/contract/api/onboarding/ClientDraftModels.java:30`（`DraftPatchRequestBody(List<String> dirtyFields, Map<String, Map<String, String>> values)`）、`:44-45`（`ClientDraftResponseBody.values` 同为字符串二级映射）；client-console-bff `ccbff/ext/rest/ClientOnboardingController.java:152` 原样透传该类型 |
| Schema 表 | `cm-sql/client-master-schema.sql:524-541` `t_onboarding_schema`（键 = 辖区 × 主体类型 × 角色 × 组织类型，`version_no`、`active_slot`）；`:542-565` `t_onboarding_schema_field`（`widget_code` 允许值 16 个，**无多选**；`catalog_code` 指 `t_catalog_item` 或 `REF_` 保留码；`requirement_level` REQUIRED / EVENTUALLY / OPTIONAL / HIDDEN，按字段静态；`visible_when` "简单可见条件 field_key=value"）；`:477-490` `t_catalog_item`（client-master 自有目录，禁止 `REF_`） |
| Schema 校验 | `cm/domain/onboarding/requirement/OnboardingRequirementEvaluator.java:81-100`（逐字段：HIDDEN 或不可见跳过）、`:125-150`（普通字段：缺失 / 格式 / 目录 / 日期）、`:155-170`（正则、长度、MONEY > 0）、`:282-290`（`visible` 只支持 `key=value` 等值，值取 `subject.values()` 的单个字符串）、`:385-400`（缺失且级别 EVENTUALLY → `EVENTUALLY_DUE`，否则 `CURRENTLY_DUE`）；`cm/domain/enums/WidgetCode.java`（16 个常量） |
| 目录取值 | `cm/domain/onboarding/pack/ReferenceCatalogCodes.java`（`REF_` 前缀 = Platform 参考数据，form-schema 不带选项，由 client-console-bff 补全，提交时向 Platform 校验）；`cm/ext/rest/FormSchemaConverter.java:130-145`（自有目录随 form-schema 下发，`REF_` 剔除）；`cm/service/processor/ClientSubmissionPreparer.java:96-117,121-127`（提交 / 清单前在事务外组装参考数据结论，快照未就绪整体 503）；`ccbff/service/processor/GetClientFormSchemaProcessor.java`（补全 `REF_CURRENCY`、`REF_COUNTRY_SUBDIVISION`） |
| 批准与摘要 | `cm/service/processor/ApplyClientReviewResultProcessor.java:130-145`（`profileHash`：`values = ClientDraftValues.values(...)` + 扩展属性的 RFC 8785 SHA-256，**已含 `business.*`**）；`cm/domainservice/service/impl/ClientReviewOutcomeServiceImpl.java:36`（摘要写入 **`t_party_profile.content_hash`**）；`ClientBusinessProfileMapper.xml` 只有 select 与分类绑定 / 确认两条 update，**没有任何代码写 `t_client_business_profile.content_hash`**；`cm/domain/checkout/CheckoutEligibilityContext.java:204` 却要求该列为合法哈希 |
| Risk 登记 | `cm/integration/impl/ClientReviewRegistrationClientImpl.java:88-97`（`ClientReviewBusinessBody` 送经营资料六项）；compliance `ClientReviewRegistrationController.java:81-82` 只映射 subjects / relations / documents，**不读 business** |
| 运营展示 | `obff/service/processor/ClientReviewDetailsProcessor.java:57`（工作台"申请"取 client-master `ClientDraftResponseBody`）；`obff/ext/rest/ClientAdminController.java:50`（Client 详情 → `onboardingAdmin.client`，client-master `OnboardingAdminConverter.java:166` 同样用 `ClientDraftValues`）；`oweb/clientReview/ReviewEvidence.tsx:26-33`、`oweb/masterData/ClientDetail.tsx:19,58`（按 values 的分组 / 字段**通用渲染**，空值显示 `—`）；`oweb/onboardingConfig/schemaLabel.ts:31-64`（`FIELD_CATALOGS` 决定目录字段显示名） |
| 门户表单 | `cweb/onboarding/model.ts:11-13`（`Widget` 联合类型）、`cweb/onboarding/widgets.tsx:52-61`（`Choice` 单选）、`:107-121`（`Select` 取 `schema.catalogs[catalogCode]`）、`:245-260`（控件注册表，无多选）、`cweb/onboarding/schema.ts:45-63`（`isVisible` 支持 `=`、`!=`、`a|b`、逗号与，**比后端语法宽**）、`:150-170`（长度 / URL 校验） |
| commercial 目录 | `paytech/commercial/docs/database/sql/commercial-schema.sql:141-157` `t_product_capability`（`capability_kind`，PAYMENT_METHOD 种类带 `payment_method_code`）；`paytech/commercial/paytech-commercial-api/.../catalog/ProductCatalogApi.java:43-56`（`GET /products/v1/capabilities?productCode&status`，无操作者头要求）；commercial `internal-auth.mode: "OFF"`（`application.yml:129-130`）；client-master 已依赖 `paytech-commercial-api`（`paytech-client-master-core/pom.xml:27`，`ClientAgreementPartyClientImpl` 经服务发现 `commercial-service` 调用）；client-console-bff 只依赖 client-master / compliance / platform-support 三个 api |
| 文案 | `onboardingSchema.*` 键：`cweb/i18n/en/onboarding-schema.json`、`client-console-web/i18n/client-console.zh-CN.seed.json`、`oweb/i18n/en/onboarding-schema.json`、`operations-console-web/i18n/zh-CN/onboarding-schema.json`；运营字段名 `oweb/i18n/en/client-review.json:48`（`client-review.application.field.websiteUrl`）；发布方式见国家包法务修订 `release/i18n-bundles/`（两个 console 各发一个语言包版本） |
| Schema 发布方式 | `cm-sql/changes/2026-10-27-legal-pack-revision.sql:1-40`：**只用 DML**（复制线上 v1 字段 → v2 新 ID 段，差异增删，v1 置 RETIRED、`active_slot` 置空；`@cm_legal_status` 可先 DRAFT 再 ACTIVE；带停止条件与回退文件）；无运营发布 API |
| 数据现状（只读） | **mor-test `mor_client_master`**：ACTIVE Schema 9 个（AE 机构 `*` / FZ_LLC、AE 个人 `*` / SOLE_ESTABLISHMENT、SA 机构 / 个人 / FREELANCER、EG 机构 / 个人），字段合计 327 行，每个 BUSINESS 分区 6 字段（`businessModelCode`、`businessDescription`、`websiteUrl`（OPTIONAL）、`currency`、`expectedVolume`、`expectedTicket`）；在途申请：UNDER_REVIEW 8、DRAFT 3，**全部冻结在 v1（已 RETIRED）**，v2 无申请；`t_client_business_profile` 51 行仅 1 行有 `content_hash`，`t_party_profile` 75 行 3 行有哈希；`t_catalog_item` 7 个目录，无支付方式 / 交付方式。**mor-test `mor_commercial`**：PAYMENT_METHOD 种类 ACTIVE 4 条（ACQUIRING：`APPLE_PAY`、`CARD`、`GOOGLE_PAY`、`STC_PAY`），`capability_code = payment_method_code`；`CARD` 的 `capability_name` 显示为 `???`（存量字符集问题）。**本机**：`mor_client_master` 无 Schema（auto 测试按需写入）；`mor_client_master_v2e2e` 仍为 v1 Schema（8 个 ACTIVE），在途 UNDER_REVIEW 9、DRAFT 3 |
| 台账 | `agent-docs/CURRENT_IMPLEMENTATION.md:31`（国家包法务修订已部署 mor-test、已推送）、`:67`（全球合规入驻切片 1 已部署 mor-test） |

## 2. 现状发现

| 编号 | 发现 | 影响 |
|---|---|---|
| F-01 | 经营资料是**单行可变**：草稿直接写 `t_client_business_profile` 本行（`saveBusiness`），批准不搬运数据；只读状态由申请状态控制 | 新增四列即"草稿 = 资料"，无需搬运逻辑；批准后数据自然在本表（需求第 3 项"批准时流入"无代码量） |
| F-02 | 草稿 API、`ClientDraftValues`、需求评估器、门户与运营页面全部以 **`Map<String, String>`** 传值，一个字段一个字符串 | 多选值若改为数组，会波及 API 契约、两 BFF、两 Web 与评估器全部值类型。按规范化逗号串（如 `APPLE_PAY,CARD`）承载，可沿用现有通道（S-01、S-02） |
| F-03 | Schema 没有多选控件；`SELECT` 单值；后端草稿 `Kind` 无代码列表类型 | 新增 `MULTI_SELECT`：后端枚举与列注释、草稿 `Kind`、评估器逐项目录校验、门户控件、运营显示（R4 成立） |
| F-04 | `requirement_level` 按字段静态，`visible_when` 只控制显示 / 是否校验，不能"按条件改要求级别"；同一 Schema 同一 `field_key` 只能一行（`uk_schema_field`） | Q2"无网站的个人可选"无法用条件表达；可行的 Schema 写法是**按主体类型**：个人 Schema（含 AE 个人独资、SA 自由职业者）OPTIONAL，机构 Schema EVENTUALLY（S-03） |
| F-05 | `visible_when` 后端只支持 `key=value` 等值（`OnboardingRequirementEvaluator.java:282-290`），前端支持 `!=`、`a|b`、逗号与；空值比较前后端结论相反（后端 `null ≠ ""`） | `fulfillmentDays` 用 `business.fulfillmentType=PRE_ORDER` 两端一致可用；不要写其他语法。语法差异是存量问题，本需求不扩（S-09） |
| F-06 | `EVENTUALLY_DUE` 只出现在需求清单（门户与运营可见），**批准不检查**：审批门禁在 compliance（`ClientReviewApprovalPolicy` 只看材料要求与程序检查） | "退款政策链接批准前必填"目前只是提示，审核员仍可批准。要强制须另加门禁（S-04） |
| F-07 | 批准摘要实际写在 `t_party_profile.content_hash`，输入 `values` 已含全部 `business.*`；`t_client_business_profile.content_hash` 没有写入路径（mor-test 51 行仅 1 行有值），而 `CheckoutEligibilityContext:204` 要求它合法 | B5 / AC-06 的口径应改为"主体资料摘要（`t_party_profile.content_hash`）包含三项"——加进 `ClientDraftFields` / `ClientDraftValues` 即自动包含，批准时只算一次，存量摘要不重算。业务资料摘要列无人写入是**存量缺口**（可能使新入驻 Client 的 Checkout 分类为空），超出本需求，单独报主会话（S-08） |
| F-08 | 期望支付方式的来源 commercial `t_product_capability`：PAYMENT_METHOD 种类目前 4 条，无辖区维度（`STC_PAY` 为沙特本地方式也对 AE / EG 显示）；`capability_name` 是运营内部名且 mor-test 有乱码，不宜直接作为门户文案 | 选项由 client-master 或 BFF 向 commercial 读取，门户文案用 `onboardingSchema.paymentMethod.<代码>` 键、缺失时显示代码（S-05、S-06） |
| F-09 | client-master 已依赖 `paytech-commercial-api` 并有调用 commercial 的客户端模式；client-console-bff 未依赖 commercial | 目录读取与提交校验放在 client-master 只多一个 Owner 查询，BFF 不新增依赖；若照 `REF_` 模式放 BFF 则 BFF 新增 commercial 依赖且两处取数（S-05） |
| F-10 | 现有 URL 控件接受 http 与 https；B2 要求退款政策只收 https | 需要 https-only：Schema `validation_pattern` 加前端同口径校验，或草稿 `Kind` 新增 HTTPS_URL（S-07） |
| F-11 | 没有整数控件；`fulfillment_days` 1–365 | 用 `TEXT` + `validation_pattern`（1–365 正则）+ 草稿 `Kind` 整数，存 `SMALLINT`；切换为非 PRE_ORDER 时须清空天数，否则隐藏字段的旧值会进摘要（S-07） |
| F-12 | Schema 只能 DML 发布；当前 9 个 ACTIVE v2 / FREELANCER v1，字段 327 行；在途申请全部冻结在 v1 | 本需求按法务修订同样方式发布 v3：复制 v2 字段 + 每个 Schema 加 4 行（约 363 行字段、9 行 Schema 插入、9 行退役）；在途 11 个申请不受影响（B4 / AC-04 天然成立），承保页对其显示"未采集" |
| F-13 | 运营工作台"申请"页签与 Client 详情都**通用渲染** values；新字段加进 `ClientDraftValues` 即出现，但：多选值会原样显示逗号串、交付方式无目录文案、退款链接不可点、空值为 `—` | 运营端改动是显示层：`FIELD_CATALOGS` / 值格式化、链接新开页、BUSINESS 组空值文案"未采集"（AC-08）；BFF 无改动 |
| F-14 | compliance 收到的 `ClientReviewBusinessBody` 未被使用 | 本需求可不改 compliance 契约；若 REQ-03 承保页要从 Risk 侧读，则另议（S-10） |
| F-15 | 现在**没有**任何内部服务读 Client 经营资料（client-console-bff 首页只读 `businessModelCode`）；运营读取全走 admin / 审核两个通用 values 接口 | R6"对外读接口加三项"对运营页面已由 values 覆盖；类型化内部读接口目前没有消费者（S-10） |
| F-16 | `t_catalog_item.BUSINESS_MODEL` 已有 `SERVICES`、`SUBSCRIPTION`，与交付方式 `SERVICE`、`SUBSCRIPTION` 语义重叠 | 不合并（一个是商业模式、一个是交付节奏），文案上区分；交付方式建 client-master 自有目录 `FULFILLMENT_TYPE`（全局 `*`，4 项） |
| F-17 | client-master PDManer 文档仍写"只能由生成器生成"（`CLIENT_MASTER_PHYSICAL_DECISIONS.md:26`），但 2026-10-27 起已直接编辑模型；用户 2026-10-22 定"直接改，不用生成器" | 阶段 4 直接编辑 `client-master.pdma.json`；生成器漂移测试失败按既有噪声处理 |

## 3. 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 / 原因 | 处理建议 |
|---|---|---|---|---|
| R1 资料表四列 | 6 列经营资料（第 1 节） | 加期望支付方式、退款政策、交付方式、天数 | 未实现 | DDL 4 列（S-01 定存储）；`ClientDraftFields`、两 Mapper、`ClientDraftValues`、`ClientDraftPatch` 同步 |
| R2 Schema 新版本 | 9 个 ACTIVE Schema，BUSINESS 6 字段（F-12） | 每个加 4 字段 | 无 v3 | 复制式 DML 发布 v3；要求级别按 S-03；天数 `visible_when=business.fulfillmentType=PRE_ORDER` |
| R2 退款政策条件级别 | 级别静态（F-04） | 无网站个人可选 | 不支持条件级别 | 按主体类型分级（S-03） |
| R3 草稿 / 提交校验 | 单值字符串校验（F-02） | 多值、https、1–365 | 缺多值与整数、https-only 类型 | 草稿 `Kind` 增 CODE_LIST / INTEGER / HTTPS_URL，评估器逐项目录校验（S-02、S-07） |
| R3 EVENTUALLY 批准前必填 | 只提示不阻断（F-06） | 批准前必填 | compliance 不读字段级清单 | S-04 |
| R4 多选控件 | 无（F-03） | `MULTI_SELECT` | 未实现 | 后端枚举 + 列注释 + 门户控件 + 运营显示 |
| B1 目录 ACTIVE | 自有目录 / `REF_` 两种来源（第 1 节） | commercial 支付方式 ACTIVE | 第三种来源 | 新保留目录码（S-05） |
| R5 运营展示 | 通用渲染（F-13） | 三项可见、链接可点、空值"未采集" | 显示细节 | 只改 ops-web 显示层 |
| R6 读接口 | 无内部读者（F-15） | 承保页、合同页可读 | 无消费者 | S-10 |
| B5 / AC-06 摘要 | 主体资料摘要已含 `business.*`；业务资料摘要列无写入（F-07） | 三项进摘要 | 口径不符 | 改为 `t_party_profile.content_hash`（S-08） |
| B4 / AC-04 在途不受影响 | Schema 冻结（F-12） | 同 | 无缺口 | 维持；承保页"未采集"由消费方处理 |
| R7 文档 | 数据库文档、国家包说明 | 更新 | — | 阶段 6 同步 |

## 4. 逐系统影响

| 系统 / 仓库 | 业务职责 | 影响点 | 是否改代码 / 模型 / 配置 | 上下游依赖及兼容风险 |
|---|---|---|---|---|
| client-master | 经营资料与采集 Schema Owner | DDL 4 列；`WidgetCode.MULTI_SELECT`；`ClientDraftPatch` 新 `Kind` 与 4 个路径；`ClientDraftFields` / `ClientDraftMapper.xml` 读写；`ClientDraftValues`；评估器多值目录校验；新保留目录码与 commercial 支付方式查询（form-schema 选项 + 提交 / 清单校验）；交付方式变更时清空天数；`t_catalog_item` `FULFILLMENT_TYPE` 4 行；Schema v3 DML；auto 测试 | 代码：是；模型：是（1 表 4 列，2 列注释）；DML：是 | 新增对 commercial 的运行期读依赖（S-05：commercial 不可用时 form-schema 与提交 503，草稿保存不受影响）；旧镜像读到新列无影响（DDL 可先行）；v3 DML 须在新镜像之后（旧镜像不认识 `MULTI_SELECT`） |
| commercial | 产品能力目录 Owner | 只被读：`GET /products/v1/capabilities?productCode=ACQUIRING&status=ACTIVE` 过滤 PAYMENT_METHOD | 否 | 目录变化（RETIRED）后，已批准资料中的旧代码保留原值，新提交被拒；`CARD` 名称乱码不影响（门户不用该名称） |
| compliance | 尽调审核 | 不改（F-14）；若采纳 S-04 方案 B 则加程序检查 | 默认否 | — |
| client-console-bff | 门户聚合 | 若 S-05 取 client-master 方案：无改动（form-schema 带选项，草稿透传字符串）；若取 BFF 方案：新增 commercial 依赖与目录补全 | 默认否 | — |
| client-console-web | 门户 | `model.ts` 控件类型；`widgets.tsx` 新多选控件（已选可移除、错误在字段下）；`schema.ts` 多值必填 / https / 整数校验；`draft.ts` 逗号串收发；文案 | 代码：是；**Pencil 原型：多选控件与业务分区 1 处**（阶段 3 先出图） | 1440 / 390 宽度（AC-07） |
| operations-console-bff | 运营聚合 | 无（values 透传） | 否 | — |
| operations-console-web | 审核工作台、Client 详情 | `schemaLabel.ts` 增目录映射与多值显示；退款链接新开页；BUSINESS 组空值"未采集"；`client-review.application.field.*` 新字段名；Schema 配置查看页的控件名文案 | 代码：是（显示层）；原型：可沿用现有布局（阶段 3 确认是否需出图） | — |
| Platform 语言包 | 文案 | client-console、operations-console 各发一个版本：字段标题 / 帮助 4 组、`FULFILLMENT_TYPE` 4 项、支付方式 4 项、控件名、运营字段名与"未采集" | DML（语言包） | 发布顺序在 Web 之后 |
| risk-decision（REQ-03） | 承保 | 本需求不改；读法见 S-10 | 否 | REQ-03 阶段 2 定 |
| 文档 | — | `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`、client-master 物理决策、国家包说明、`CURRENT_IMPLEMENTATION.md`；需求 B5 / AC-06 口径（S-08） | 是 | — |

## 5. 待决事项

| 编号 | 问题 | 建议 |
|---|---|---|
| S-01 | `expected_payment_methods` 存储：逗号串列、JSON 列，还是子表 | **`VARCHAR(512)` 规范化逗号串**（大写、去重、按代码排序，如 `APPLE_PAY,CARD`）。理由：全链路值类型为字符串（F-02），草稿 / 摘要 / 评估器 / 两端页面零形状改动；只整体读写、无按方式检索的现有消费者；有 `t_catalog_item.client_types` 先例。若 REQ-03 / commercial 将来要"按支付方式查 Client"，再迁子表 |
| S-02 | 草稿 API 多值形状 | 沿用 `Map<String, String>`：多选值为逗号串；新增草稿 `Kind.CODE_LIST`（逐项 trim、大写、`[A-Z][A-Z0-9_]{0,63}`、去重排序、≤ 20 项、总长 ≤ 512）。不引入 JSON 数组，避免 BFF 严格解析与页面类型同时改 |
| S-03 | 退款政策链接的要求级别（Q2） | 按主体类型：**个人 Schema（AE `*` / SOLE_ESTABLISHMENT、SA `*` / FREELANCER、EG `*`）OPTIONAL，机构 Schema EVENTUALLY**。不新增条件级别机制（`required_when`）。个人有网站也不强制，由审核员网站审查检查项兜底 |
| S-04 | EVENTUALLY 不阻断批准（F-06） | **本需求维持现状**：机构未填时需求清单显示"批准前需补"，审核员可退回补件；不在 compliance 加门禁。若要硬性阻断，另立项让 compliance 审批前读取 client-master 需求清单（影响所有 EVENTUALLY 字段，不只本项） |
| S-05 | 支付方式目录的取数与校验位置 | **client-master 统一负责**：新增保留目录码 `COMMERCIAL_PAYMENT_METHOD`（与 `REF_` 同为外部目录，不进 `t_catalog_item`，守卫测试同样禁止）；form-schema 组装时在事务外调用 commercial 取 ACTIVE 的 PAYMENT_METHOD 能力（`productCode` 不限，只按 `capabilityKind` 过滤），以 `onboardingSchema.paymentMethod.<代码>` 为文案键放入 `catalogs`；提交 / 清单校验同一来源（事务外查询，不可用时 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`，同参考数据）；可选短时进程内缓存（≤ 60 秒）。client-console-bff 不改 |
| S-06 | 存哪个代码；是否按辖区过滤 | 存 **`capability_code`**（与合同开通项同一编码，商务可直接对照；当前与 `payment_method_code` 相同）。本期不按辖区过滤（commercial 目录无辖区维度），全量展示 ACTIVE 支付方式 |
| S-07 | https 与天数的实现 | 退款政策：Schema `validation_pattern` `^https://.+` + 草稿新 `Kind.HTTPS_URL`（复用 URL 解析，只收 https），门户按 pattern 提示；天数：控件 `TEXT`、`validation_pattern` 精确匹配 1–365、草稿 `Kind.INTEGER`（≤ 3 位）、列 `SMALLINT`；草稿保存时交付方式被改为非 PRE_ORDER 则同一事务清空天数（服务端规则，不依赖前端） |
| S-08 | B5 / AC-06 摘要口径；业务资料摘要列无写入 | 需求改为"批准时冻结的主体资料摘要（`t_party_profile.content_hash`）包含三项"——随字段加入自动成立，不重算存量。`t_client_business_profile.content_hash` 无写入路径而 Checkout 分类要求它（F-07）：**本需求不修**，单独报主会话确认是否为缺陷并另立项 |
| S-09 | 前后端 `visible_when` 语法不一致（F-05） | 本需求只用 `=` 等值；在阶段 3 方案中注明，另记技术债，不在本需求统一 |
| S-10 | R6 读接口 | **本需求不新增类型化内部读接口**：运营页面（审核、Client 详情，以及 REQ-03 / 合同页若在 ops-bff 聚合）读现有 admin / 审核 values 即可；若 REQ-03 的 risk-decision 服务端自动承保需要机读，在 REQ-03 阶段 2 按其消费形状加 `GET` 内部查询（避免无消费者接口）。compliance 登记体是否加三项同样随 REQ-03 决定 |
| S-11 | 交付方式目录 | client-master 自有目录 `FULFILLMENT_TYPE`（`INSTANT / PRE_ORDER / SUBSCRIPTION / SERVICE`，辖区 `*`，个人与机构都适用），控件 `SELECT` 单选，`REQUIRED` |
| S-12 | Schema v3 发布方式 | 照法务修订脚本：新 ID 段（阶段 3 在 mor-test 复核后选，不与 `…700000—…999999` 重叠）、复制当时线上 v2 字段、每个 Schema 加 4 行、v2 退役；支持 `@status = DRAFT` 先写后发；带停止条件与回退文件（有引用 v3 的申请时拒绝回退）；本机 `mor_client_master_v2e2e` 仍在 v1，联调前需先补跑法务修订 DML 或只在 auto 库验证 |

## 6. 工作量与风险

| 项 | 判断 |
|---|---|
| 规模 | **M（偏小）**，高于 requirement-plan 的 S：除 4 列与 Schema DML 外，新增一个控件类型（后端校验 + 门户控件 + 运营显示）和第三种目录来源（client-master → commercial 读取与提交校验）。client-master 约 10—12 个新改类；DDL 1 表 4 列；DML 约 9 + 9 + 363 + 4 行；门户 3—4 个文件；运营 2—3 个文件；语言包两个版本 |
| 最大风险 | 多值在全链路以逗号串传递：规范化规则须在草稿、评估器、页面三处一致，否则同一选择生成不同字符串、进入摘要后不可比（以单一规范化函数 + auto 测试兜底） |
| 第二风险 | 新增运行期依赖 commercial：commercial 不可用时 form-schema 与提交 503。草稿保存不依赖，影响面限于新申请首屏与提交 |
| 第三风险 | EVENTUALLY 不阻断批准（S-04）可能与业务对"批准前必填"的预期不符，需用户明确接受 |
| 依赖 | 无阻塞；REQ-03 读取方式在其阶段 2 定（S-10） |
| 需要页面原型的点 | ① 门户业务信息分区：多选控件（已选标签可移除、错误在字段下、390 宽度换行）、交付方式与条件出现的天数、退款政策链接；② 运营"申请"页签业务信息组：多值标签、可点链接、"未采集"占位（可沿用现有 Descriptions 布局，阶段 3 定是否出图） |

**确认点**：用户确认 F-01—F-17 的判断、S-01—S-12 的取舍与第 4 节影响范围后进入阶段 3。

> **阶段 2 已确认（2026-10-29）**：用户接受 S-01—S-12 全部建议。

---

# 阶段 3：方案、接口与数据设计（评审稿，2026-10-29）

**一句话规模**：本次方案涉及 client-master、client-console-web、operations-console-web 3 个系统（两个 BFF 与 commercial 不改代码）；新增接口 0，调整接口 4 个（form-schema、草稿保存、草稿 / 审核 / admin 详情 values、提交与需求清单校验），复用 commercial 已有接口 1 个；新增表 0；修改旧表 1 张（`t_client_business_profile` 新增 4 列，另改 2 张表的列注释：`t_onboarding_schema_field.widget_code`、`t_catalog_item.catalog_code`）；DML：Schema 新版本 9 行 + 旧版本退役 9 行、字段复制约 327 行 + 新增 36 行、目录项 4 行；注册 DML 0；页面原型 2 处（主会话绘制）。

已读规范：`DEVELOPMENT_CONVENTIONS.md`（第 4—6 节）、`JAVA_PROJECT_STRUCTURE.md`（跨服务调用与 Processor）、`DATABASE_DESIGN_GUIDE.md`、`UNIT_TEST_GUIDE.md`、`examples/WRITE_API_EXAMPLE.md`；国家包法务修订的 DML / 发布记录（`requirements/20261027/client-master-国家包法务修订/release/`）。

本地原子写入与事务外步骤：

| 用例 | 事务外 | 本地事务（`mor_client_master`） |
|---|---|---|
| 取表单 Schema | 读 commercial 支付方式（仅当 Schema 引用 `COMMERCIAL_PAYMENT_METHOD`） | 无写入 |
| 保存草稿 | 无外部调用（只做语法规范化） | 同现有：按脏字段写 `t_client_business_profile`，`draft_revision + 1`；交付方式改为非 PRE_ORDER 时同事务清空天数 |
| 需求清单 / 提交 | 读 Platform 参考数据快照（现有）+ 读 commercial 支付方式（新增） | 提交同现有，无新增写入 |
| 批准 | 无 | 同现有：`t_party_profile.content_hash` 的输入自动含新四项 |

## 7. 方案总览

```
client-console-web ──GET form-schema──▶ client-console-bff ──▶ client-master GetOnboardingFormSchemaProcessor
                                         （透传 catalogs，不改）     ├─ pack 引用 COMMERCIAL_PAYMENT_METHOD？
                                                                    │    └─ 事务外 ClientPaymentMethodCatalogClient
                                                                    │         → commercial GET /products/v1/capabilities?status=ACTIVE
                                                                    │         → 过滤 capabilityKind=PAYMENT_METHOD → [capability_code]
                                                                    └─ FormSchemaConverter：catalogs["COMMERCIAL_PAYMENT_METHOD"] =
                                                                         [{code, labelKey=onboardingSchema.paymentMethod.<code>}]

client-console-web ──PATCH draft（values 仍为字符串；多选为逗号串）──▶ BFF 透传 ──▶ SaveClientDraftProcessor
      ClientDraftPatch.normalize：CODE_LIST 规范化 / HTTPS_URL / INTEGER
      ClientDraftPreparer：天数只随 PRE_ORDER 保留（改类型即清空；非 PRE_ORDER 单独写天数拒绝）
      → saveBusiness（4 个新列）

需求清单 / 提交 ──▶ ClientSubmissionPreparer：参考数据快照（现有）+ 支付方式目录（新，事务外，失败 503）
      → RequirementInput.paymentMethods → OnboardingRequirementEvaluator：MULTI_SELECT 逐项目录校验

批准 ──▶ ApplyClientReviewResultProcessor.profileHash：ClientDraftValues 已含 business.* 新四项 → t_party_profile.content_hash

运营（审核"申请"页签 / Client 详情）──▶ ops-bff 透传 values ──▶ ops-web schemaLabel：多值 / 交付方式 / 天数 / 链接 / "未采集"
```

- **外部目录码**：`COMMERCIAL_PAYMENT_METHOD` 与 `REF_*` 同为"外部目录"——不进 `t_catalog_item`，form-schema 由 client-master 自己补全（`REF_*` 仍由 BFF 补全，不变），提交时向 Owner 校验。
- **多值在全链路是规范化逗号串**：唯一的规范化函数在 `ClientDraftPatch`（CODE_LIST），评估器与页面只拆分不再改写，摘要输入就是存储值。
- **三处天然成立的需求**：在途申请（冻结 v1 / v2）不受影响；批准不搬运数据（单行资料）；摘要自动包含新字段。

## 8. 接口

### 8.1 form-schema（调整，路径不变）

client-master `GET /client-onboarding/v1/form-schema`（及按申请取 Schema 的同一 Processor `GetOnboardingFormSchemaProcessor`）响应 `FormSchemaResponseBody`（`cm-api/master/api/onboarding/response/FormSchemaResponseBody.java`）：

| 变化 | 说明 |
|---|---|
| `Field.widget` | 新增取值 `MULTI_SELECT`（字段注释与 `WidgetCode` 同步） |
| `catalogs["COMMERCIAL_PAYMENT_METHOD"]` | 仅当 Schema 有字段引用该目录码时出现；`CatalogOption{code, labelKey}`，`code` = commercial `capability_code`，`labelKey` = `onboardingSchema.paymentMethod.<code>`；顺序沿用 commercial 返回顺序（可开通项 ID 升序） |
| `catalogs["FULFILLMENT_TYPE"]` | client-master 自有目录，按现有逻辑随包下发 |
| 错误 | commercial 不可用或响应不合法 → client-master 返回 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`（`ClientDraftErrorCode`，与参考数据未就绪同码），不返回缺选项的 Schema。**门户（经 client-console-bff）收到的是 503 `SERVICE_UNAVAILABLE`**：BFF 把上游 503 统一为自身通用码（既有行为，参考数据 / compliance 不可用时相同）；2026-10-29 阶段 5 发现、用户确认保持（不在门户区分） |

client-console-bff `GetClientFormSchemaProcessor.ownerCatalogs` 原样转换全部 client-master 目录，`widget` 为字符串透传 → **BFF 不改**；只补一条回归测试（新目录与新控件原样到达页面）。

示例（片段）：

```json
{"sections":[{"section":"BUSINESS","groups":[{"group":"OPERATIONS","fields":[
  {"fieldKey":"business.expectedPaymentMethods","widget":"MULTI_SELECT","catalogCode":"COMMERCIAL_PAYMENT_METHOD",
   "requirementLevel":"REQUIRED","labelKey":"onboardingSchema.field.business.expectedPaymentMethods",
   "helpKey":"onboardingSchema.help.business.expectedPaymentMethods"}]}]}],
 "catalogs":{"COMMERCIAL_PAYMENT_METHOD":[{"code":"APPLE_PAY","labelKey":"onboardingSchema.paymentMethod.APPLE_PAY"},
   {"code":"CARD","labelKey":"onboardingSchema.paymentMethod.CARD"}]}}
```

### 8.2 commercial 只读调用（复用，不改 commercial）

| 项 | 设计 |
|---|---|
| 接口 | `com.paytech.commercial.api.catalog.ProductCatalogApi#listCapabilities(productCode = null, status = "ACTIVE")` → `GET /products/v1/capabilities?status=ACTIVE`；响应 `ProductCapabilityListResponseBody.capabilities[]`（`ProductCapabilitySummary`：`capabilityCode`、`capabilityKind`、`status` 等） |
| client-master 客户端 | 新接口 `cm/integration/ClientPaymentMethodCatalogClient`：`List<String> activePaymentMethodCodes()`；实现 `cm/integration/impl/ClientPaymentMethodCatalogClientImpl`：过滤 `capabilityKind = PAYMENT_METHOD` 且 `status = ACTIVE`，校验 `capabilityCode` 匹配 `[A-Z][A-Z0-9_]{0,63}`，去重保序；任何调用失败或响应不合法 → `ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE, "commercial 支付方式目录不可用")`；日志只记失败类别 |
| Bean | `paytech-client-master-app/.../configuration/ClientDraftConfiguration.java` 仿 `clientAgreementPartyClient`：`discoveryTransport(discovery, "commercial-service", "")` + `InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536), tokens, "commercial-service")` → `createClient(ProductCatalogApi.class)`；超时同现有私网客户端（连接 2 秒、读取 3 秒） |
| 缓存 | 不缓存（每次 form-schema / 清单 / 提交各读一次；四行数据，量级可忽略） |
| 依赖方向 | client-master → commercial 已存在（`AgreementPartyQueryApi`），不新增 Maven 依赖；commercial `internal-auth` 现为 OFF，开启后沿用同一 Token 机制 |

### 8.3 草稿保存（调整，路径不变）

`PATCH /client-onboarding/v1/applications/{app}/draft` 请求体 `DraftPatchRequestBody(dirtyFields, values)` 形状不变。`ClientDraftPatch.FIELDS` 新增 4 个路径与 3 个 `Kind`：

| 路径 | Kind | 长度 | 规范化 / 校验（不合规 → 422 `INVALID_DRAFT`，`errors[].field` = 路径） |
|---|---|---|---|
| `business.expectedPaymentMethods` | `CODE_LIST`（新） | 512 | 按英文逗号拆分 → 每项 trim、转大写 → 丢弃空项 → 每项须 `[A-Z][A-Z0-9_]{0,63}` → 去重 → 按代码升序 → 以 `,` 连接；≤ 20 项；结果为空视为清空。消息"须为以逗号分隔的代码，最多 20 项" |
| `business.refundPolicyUrl` | `HTTPS_URL`（新） | 2048 | 同 `URL` 的解析与主机 / 无用户信息检查，但 scheme 只能 `https`。消息"须为 https 网址" |
| `business.fulfillmentType` | `TEXT` | 32 | 现有文本规则；目录在提交时校验 |
| `business.fulfillmentDays` | `INTEGER`（新） | 3 | `[0-9]{1,3}`，规范化去前导零（`"007"` → `"7"`）；范围 1—365 由 Schema `validation_pattern` 在提交时校验。消息"须为整数" |

`ClientDraftPreparer.prepare`（新增一条规则，在写入前）：取"本次值或当前值"的有效交付方式 `effectiveType`：

- 本次修改了 `business.fulfillmentType` 且 `effectiveType != PRE_ORDER` → 在 changes 中强制 `business_fulfillmentDays = null`（同事务清空）。
- 本次修改了 `business.fulfillmentDays` 为非空且 `effectiveType != PRE_ORDER` → 422 `INVALID_DRAFT`，字段 `business.fulfillmentDays`，消息"仅在交付方式为预售时填写"。

响应 `ClientDraftResponseBody.values.business` 新增 4 键（字符串；多选为规范化逗号串，天数为十进制字符串，未填为 `null`）。admin 申请 / Client 详情（`OnboardingAdminConverter`）与审核 `ClientDraftResponseBody` 同源（`ClientDraftValues`），自动带出。

### 8.4 需求清单与提交（调整，路径不变）

`GET …/applications/{app}/requirements`、`GET …/submission-check`、`POST …/submissions` 共用 `ClientSubmissionPreparer.prepare`：

| 变化 | 说明 |
|---|---|
| 支付方式事实 | `prepare` 在事务外：`pack` 有字段引用 `COMMERCIAL_PAYMENT_METHOD` 时调用 `ClientPaymentMethodCatalogClient.activePaymentMethodCodes()`，放入 `RequirementInput` 新字段 `Set<String> activePaymentMethods`（不引用时为空集合、不调用） |
| 失败 | client-master 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`、门户 503 `SERVICE_UNAVAILABLE`（见 8.1），不写入（与参考数据未就绪同一处理） |
| 评估 | `OnboardingRequirementEvaluator.valueField`：`WidgetCode.MULTI_SELECT` 时按逗号拆分，任一项不在目录 → `FIELD_VALUE_NOT_IN_CATALOG`；`catalogContains` 对 `COMMERCIAL_PAYMENT_METHOD` 查 `activePaymentMethods`，其余不变；缺失按要求级别（REQUIRED → `CURRENTLY_DUE`，EVENTUALLY → `EVENTUALLY_DUE`，OPTIONAL 不出项） |
| 正则 / 长度 | 退款政策 `validation_pattern` 不符 → `FIELD_FORMAT_INVALID`；天数（仅 PRE_ORDER 可见时）不符 1—365 → `FIELD_FORMAT_INVALID` |

### 8.5 目录码与控件常量

| 位置 | 变化 |
|---|---|
| `cm/domain/enums/WidgetCode.java` | 新增 `MULTI_SELECT`（相邻常量空一行） |
| `cm/domain/onboarding/pack/ReferenceCatalogCodes.java` | 新增常量 `COMMERCIAL_PAYMENT_METHOD = "COMMERCIAL_PAYMENT_METHOD"` 与 `isExternal(code)` = `isReference(code) \|\| COMMERCIAL_PAYMENT_METHOD.equals(code)`；类注释补"commercial 外部目录" |
| `FormSchemaConverter.catalogs:139`、`OnboardingPackServiceImpl:133` | 剔除条件由 `isReference` 改为 `isExternal`（不从 `t_catalog_item` 加载外部目录）；`FormSchemaConverter` 新增入参 `List<String> paymentMethods`，非空时放入 `catalogs` |
| `ReferenceCatalogGuardTest` | 守卫扩展：`t_catalog_item` 与初始化 DML 不得出现 `COMMERCIAL_` 开头的目录码 |
| 新 client-master 自有目录 | `FULFILLMENT_TYPE`：`INSTANT`、`PRE_ORDER`、`SUBSCRIPTION`、`SERVICE`（辖区 `*`，`client_types` NULL） |

### 8.6 运营显示（ops-bff 不改）

ops-web 只改显示层（`oweb/onboardingConfig/schemaLabel.ts` 与使用它的 `oweb/clientReview/ReviewEvidence.tsx`、`oweb/masterData/ClientDetail.tsx`）：

| 字段 | 显示 |
|---|---|
| `business.expectedPaymentMethods` | 拆分为标签，逐项取 `onboarding-schema.paymentMethod.<code>`，无译文显示代码 |
| `business.fulfillmentType` | `FIELD_CATALOGS` 加 `catalog:FULFILLMENT_TYPE` |
| `business.fulfillmentDays` | `client-review.application.fulfillmentDaysValue`（"{count} 天"） |
| `business.refundPolicyUrl`、`business.websiteUrl` | 可点击链接，`target="_blank" rel="noopener noreferrer"`，长链接截断显示 |
| BUSINESS 组上述四键为空 | `client-review.application.notCollected`（"未采集"，AC-08）；其他字段仍为 `—` |
| Schema 配置查看页 | `client-review.config.enum.widget.MULTI_SELECT` |

## 9. 数据库（`mor_client_master`）

### 9.1 `t_client_business_profile` 新增 4 列（置于 `business_description` 之后）

| 列 | 定义 |
|---|---|
| `expected_payment_methods` | `VARCHAR(512) NULL COMMENT 'Client 期望开通的支付方式：commercial 产品能力目录 capability_kind=PAYMENT_METHOD 的 capability_code，大写、去重、按代码升序、英文逗号分隔（如 APPLE_PAY,CARD），最多 20 项；草稿可空，提交按 Schema 要求级别校验且每项须为 ACTIVE；仅作审核与商务参考，不代表已开通。取值来源：commercial t_product_capability；非固定枚举。'` |
| `refund_policy_url` | `VARCHAR(2048) NULL COMMENT 'Client 对外公示的退款政策页面 https 网址；草稿可空，机构申请批准前必填、个人申请可选（按 Schema 要求级别）；不校验页面内容。'` |
| `fulfillment_type` | `VARCHAR(32) NULL COMMENT 'Client 向消费者交付商品或服务的方式；草稿可空，提交必填。取值来源：t_catalog_item 目录 FULFILLMENT_TYPE（INSTANT、PRE_ORDER、SUBSCRIPTION、SERVICE）；非固定枚举。'` |
| `fulfillment_days` | `SMALLINT NULL COMMENT '预售从付款到交付的预计天数，1—365；只在 fulfillment_type=PRE_ORDER 时有值，改为其他交付方式时清空。'` |

无默认值、无索引（只按 `client_id` 整行读写）。另改注释：`t_onboarding_schema_field.widget_code` 允许值加 `MULTI_SELECT`；`catalog_code` 注释补"`COMMERCIAL_PAYMENT_METHOD` 表示 commercial 产品能力目录的支付方式（外部目录）"；`t_catalog_item.catalog_code` 注释补"不得为 COMMERCIAL_ 开头的外部目录码"。

### 9.2 DDL 文件

| 文件 | 内容 |
|---|---|
| `paytech/client-master/docs/database/sql/changes/2026-10-29-business-info-collection-ddl.sql` | 预检（4 列不存在，存在即视为已执行跳过）→ `ALTER TABLE t_client_business_profile ADD COLUMN … AFTER business_description`（4 列一次 ALTER）→ 3 处注释 `MODIFY COLUMN`（原类型原可空性） |
| 同目录 `…-ddl-rollback.sql` | 预检：没有任何 `t_onboarding_schema` 引用 v3（否则 SIGNAL 停止，需先回滚 DML）；`ALTER TABLE … DROP COLUMN` 4 列（**数据丢失，已采集的三项不可恢复**）；3 处注释恢复原文 |
| `client-master-schema.sql` | 同步表定义与注释 |
| `paytech/client-master/paytech-client-master-test/src/test/resources/init/ddl.sql` | 同步（本机 auto 库 `mor_client_master` 需人工执行一次前滚 DDL，测试不自动建表） |
| 需求目录 `db/ddl.sql` | 只写索引说明，指向上述执行文件 |

DDL 可先于新镜像执行：旧代码不读写新列；新列全可空。

### 9.3 PDManer（`paytech/client-master/docs/database/client-master.pdma.json`，直接编辑，不用生成器）

- `t_client_business_profile`：在 `business_description` 字段后新增 4 个字段对象（新 UUID；VARCHAR 用同表 `website_url` 的 `baseType`，`fulfillment_days` 用 SMALLINT 基类——模型中无 SMALLINT 时用 INT 基类并把 `type` 写为 `SMALLINT`，与 schema.sql 一致；注释同 9.1）。
- `t_onboarding_schema_field.widget_code`、`catalog_code`，`t_catalog_item.catalog_code` 注释同步。
- `CLIENT_MASTER_PHYSICAL_DECISIONS.md`：第 26 行"只能由生成器生成"改注为"自 2026-10-22 起直接编辑模型"（F-17），并记本次变更。

### 9.4 Schema v3 DML（照 2026-10-27 法务修订脚本模式）

文件：`paytech/client-master/docs/database/sql/changes/2026-10-29-business-info-collection.sql` 与 `…-rollback.sql`；`client-master-initial-data.sql` 改为最终状态（v2 RETIRED、v3 ACTIVE、新字段与目录项；auto 测试 `JurisdictionPackMocker` 直接读该文件）。

**ID 段**（2026-10-29 mor-test 只读复核：`t_onboarding_schema` / `_field` / `t_catalog_item` 在 `9281200000000000—9281200000999999` 均无行；现有 Schema 与字段都在 `9270000000…` 段）：

| 内容 | ID 规则 |
|---|---|
| v3 Schema（9 行） | v2 `schema_id + 11200000000000`（`9270000000900301` → `9281200000900301` … `…900309`）；`version_no` = v2 + 1（SA FREELANCER 为 2） |
| 复制字段（约 327 行） | v2 `schema_field_id + 11200000000000`（v2 字段全在 `9270000000910001—9270000000950002`，复制后落在 `92812000009xxxxx`） |
| 新增字段（36 行） | `9281200000800000 + 序号 × 10 + n`（序号 = v3 Schema 末三位 301—309 映射为 1—9，n = 1—4） |
| 目录项（4 行） | `9281200000600001—…600004`（`FULFILLMENT_TYPE`） |

**每个 v3 Schema 新增 4 字段**（`section=BUSINESS`，`applies_to_subject=PRIMARY`，`display_order` = 该 Schema BUSINESS 最大序号 + 10 / 20 / 30 / 40）：

| field_key | display_group | widget | catalog_code | requirement_level | validation_pattern | max_length | visible_when | label_key / help_key |
|---|---|---|---|---|---|---|---|---|
| `business.expectedPaymentMethods` | `OPERATIONS` | `MULTI_SELECT` | `COMMERCIAL_PAYMENT_METHOD` | REQUIRED | — | 512 | — | `onboardingSchema.field.business.expectedPaymentMethods` / `onboardingSchema.help.business.expectedPaymentMethods` |
| `business.refundPolicyUrl` | `OPERATIONS` | `URL` | — | 机构 EVENTUALLY；个人（AE `*` / SOLE_ESTABLISHMENT、SA `*` / FREELANCER、EG `*`）OPTIONAL | `^https://\S+$` | 2048 | — | `…field.business.refundPolicyUrl` / `…help.business.refundPolicyUrl` |
| `business.fulfillmentType` | `FULFILLMENT`（新组） | `SELECT` | `FULFILLMENT_TYPE` | REQUIRED | — | — | — | `…field.business.fulfillmentType` / `…help.business.fulfillmentType` |
| `business.fulfillmentDays` | `FULFILLMENT` | `TEXT` | — | REQUIRED | `^(36[0-5]\|3[0-5][0-9]\|[12][0-9]{2}\|[1-9][0-9]?)$` | 3 | `business.fulfillmentType=PRE_ORDER` | `…field.business.fulfillmentDays` / `…help.business.fulfillmentDays` |

**脚本结构**（同法务修订）：会话变量 `@cm_bizinfo_status`（缺省 `ACTIVE`；生产或先发 Web 前可先 `DRAFT`）→ 临时表装静态行与复制行 → 停止条件 → `INSERT … SELECT … ON DUPLICATE KEY UPDATE`（不改已存在行）→ `ACTIVE` 时：v2 置 RETIRED、`active_slot = NULL`，本段 DRAFT 的 v3 置 ACTIVE、`active_slot = 1`、`approved_time` 写执行时间；重跑为空操作。

停止条件（`SIGNAL`，尚未写入）：

1. `@cm_bizinfo_status` 不是 `ACTIVE` / `DRAFT`。
2. 9 个 v2（含 SA FREELANCER v1）Schema 缺失、键不符或不是 ACTIVE（ACTIVE 重跑时允许 v2 已 RETIRED 且 v3 已 ACTIVE）。
3. 待复制字段 ID 不在 `9270000000900000—9270000000999999`。
4. 新增 4 个 `field_key` 已出现在任一 v2 Schema。
5. 本 ID 段内已有非本脚本的行（按业务唯一键比对）。
6. `t_catalog_item` 已有 `FULFILLMENT_TYPE` 且内容不同，或出现 `COMMERCIAL_` 开头的目录码。
7. `t_client_business_profile` 缺少 9.1 的 4 列（DDL 未执行）。

回滚（`…-rollback.sql`）：存在引用 v3 的申请（`t_client_application.schema_id` 在 v3 段）时拒绝；否则删除 v3 字段与 Schema、v2 恢复 ACTIVE 与 `active_slot = 1`、删除 `FULFILLMENT_TYPE` 4 行（任一资料行 `fulfillment_type` 非空时拒绝）。

**环境执行计划**：

| 环境 | 现状 | 计划 |
|---|---|---|
| 本机 auto 库 `mor_client_master` | 空表（测试按 `client-master-initial-data.sql` 自行写入） | 只执行 9.2 前滚 DDL；初始化 DML 由测试读取最终状态文件 |
| 本机 `mor_client_master_v2e2e` | 已有法务修订 DDL（`draft_revision`）与参考数据迁移（`REF_` 已在字段中），但仍为 v1 Schema（法务修订 DML 未执行） | **先补跑** `changes/2026-10-27-legal-pack-revision.sql`（ACTIVE），再执行本需求 DDL + DML；需本机 e2e 的 client-master 为新 JAR（法务修订前置 2 要求新镜像识别规则枚举，本需求 JAR 已包含） |
| mor-test | v2 ACTIVE 9 个；在途 11 个申请冻结在 v1 | 先在本机临时库（由 mor-test 只读导出恢复）试跑：DDL → DML → 重跑 → 回滚拒绝分支 → 回滚 → 再执行；比对最终配置表与发布工作树 `schema.sql` + `initial-data` 全新建库逐行一致 |

## 10. 配置与发布要素

- 配置：无新增配置项（commercial 服务发现名 `commercial-service` 已在 client-master 配置中；测试配置 `application-mock.yml` 已把 `commercial-service` 指到测试外部端点）。
- 注册 DML（Gateway 路由 / 权限 / 菜单）：**0**（无新接口）。
- 补偿 / MQ / XXL：无。
- 语言包：client-console、operations-console 各发布一个新版本（版本号以发布时线上最新为准）。

## 11. 文案键（en / zh-CN）

门户：`client-console-web/src/i18n/en/onboarding-schema.json` 与 `client-console-web/i18n/client-console.zh-CN.seed.json`（DML `label_key` 的 `onboardingSchema.` 前缀对应命名空间 `onboarding-schema`）；运营：`operations-console-web/src/i18n/en/onboarding-schema.json`、`client-review.json` 及 `operations-console-web/i18n/zh-CN/` 同名文件。

| Key | en | zh-CN |
|---|---|---|
| `onboarding-schema.group.FULFILLMENT` | Fulfillment | 交付 |
| `onboarding-schema.field.business.expectedPaymentMethods` | Payment methods you plan to accept | 期望开通的支付方式 |
| `onboarding-schema.help.business.expectedPaymentMethods` | Select all that apply. This helps us review your application; it doesn't turn them on. | 可多选。仅供审核参考，不会直接为您开通。 |
| `onboarding-schema.field.business.refundPolicyUrl` | Refund policy URL | 退款政策链接 |
| `onboarding-schema.help.business.refundPolicyUrl` | A public https page that explains how customers get refunds. | 公开可访问的 https 页面，说明客户如何申请退款。 |
| `onboarding-schema.field.business.fulfillmentType` | How you deliver | 交付方式 |
| `onboarding-schema.help.business.fulfillmentType` | How customers receive what they paid for. Describe anything unusual in the business description. | 客户付款后如何获得商品或服务；特殊情况请在业务描述中说明。 |
| `onboarding-schema.field.business.fulfillmentDays` | Expected delivery time (days) | 预计交付天数 |
| `onboarding-schema.help.business.fulfillmentDays` | Days between payment and delivery, from 1 to 365. | 从付款到交付的天数，1—365。 |
| `onboarding-schema.catalog.FULFILLMENT_TYPE.INSTANT` | Instant delivery | 即时交付 |
| `onboarding-schema.catalog.FULFILLMENT_TYPE.PRE_ORDER` | Pre-order | 预售 |
| `onboarding-schema.catalog.FULFILLMENT_TYPE.SUBSCRIPTION` | Subscription | 订阅 |
| `onboarding-schema.catalog.FULFILLMENT_TYPE.SERVICE` | Service delivered later | 服务交付 |
| `onboarding-schema.paymentMethod.CARD` | Cards | 银行卡 |
| `onboarding-schema.paymentMethod.APPLE_PAY` | Apple Pay | Apple Pay |
| `onboarding-schema.paymentMethod.GOOGLE_PAY` | Google Pay | Google Pay |
| `onboarding-schema.paymentMethod.STC_PAY` | STC Pay | STC Pay |
| `onboarding.form.multi_select_placeholder` | Select one or more | 请选择（可多选） |
| `onboarding.form.remove_option` | Remove {label} | 移除 {label} |
| `onboarding.error.https_url` | Use a link that starts with https:// | 请填写以 https:// 开头的链接 |
| `onboarding.error.days_range` | Enter a whole number from 1 to 365 | 请输入 1—365 的整数 |
| `client-review.application.field.expectedPaymentMethods` | Expected payment methods | 期望支付方式 |
| `client-review.application.field.refundPolicyUrl` | Refund policy | 退款政策 |
| `client-review.application.field.fulfillmentType` | Fulfillment | 交付方式 |
| `client-review.application.field.fulfillmentDays` | Delivery time | 预计交付天数 |
| `client-review.application.fulfillmentDaysValue` | {count} days | {count} 天 |
| `client-review.application.notCollected` | Not collected | 未采集 |
| `client-review.application.openLink` | Opens in a new tab | 在新标签页打开 |
| `client-review.config.enum.widget.MULTI_SELECT` | Multi-select | 多选 |

运营端 `onboarding-schema` 命名空间同步上表的 `group / field / help / catalog / paymentMethod` 键。门户错误与表单键在 `onboarding` 命名空间（`cweb/i18n/en/onboarding.json`，蛇形命名，如现有 `onboarding.error.url`）。支付方式新增到 commercial 目录时须同步补文案键，否则显示代码。最终措辞以原型确认为准。

页面原型（主会话 2026-10-29 绘制）：
- 门户：`client-console-web/client-console.pen` 板 `jA2it`「C6 · 业务信息补采（期望支付方式 / 退款政策 / 交付方式）+ 移动端 · REQ-12」（x 15000，y 9720），导出 [prototypes/01-门户-经营信息-三项补采.png](./prototypes/01-门户-经营信息-三项补采.png)：A 机构已填写 + 多选弹层（勾选、编码、目录说明）；B 预售 + 错误汇总、https 与天数字段错误；C 个人 / 服务、退款政策"可选"；D 390 底部面板多选。分区标题"收款与售后"对应 `OPERATIONS` 组的新字段位置、"交付"对应新 `FULFILLMENT` 组（O-1），实现时以现有组文案为准。
- 运营：`operations-console-web/docs/operations-console.pen` 板 `NhiN6`「客户入驻审核 · 资料 03｜业务信息（REQ-12 补采三项）」（x 6060，y 95600），导出 [prototypes/02-运营-审核资料页签-业务信息.png](./prototypes/02-运营-审核资料页签-业务信息.png)：业务信息组三项（支付方式标签"名称 编码"、退款政策链接新窗口、交付方式标签 + 天数）、旧版申请"未采集"与个人"未填写（可选）"对照、右侧现有网站审查旁的退款政策链接（不新增检查项）。页签名以现有审核页为准（原型沿用"资料"）。

## 12. 测试与验证计划

### 12.1 client-master auto（`paytech-client-master-test`，本机 MySQL + 测试外部 HTTP 端点）

新增测试外部端点 `test/mock/external/commercial/MockCommercialProductCatalogApi`（实现 `ProductCatalogApi`，只配置 `listCapabilities` 的 DTO，其余方法调用即记为意外调用失败）与 Mocker `test/mocker/commercial/CommercialProductCatalogMocker`（设置返回的能力列表 / 模拟 5xx / 非法响应，记录调用次数）；遵守全局 BeforeEach / AfterEach 清理与意外调用检查。国家包数据由 `JurisdictionPackMocker` 读取更新后的 `client-master-initial-data.sql`（v3）。

| 验收 | 入口（`auto/ext/http/onboarding/`） | 场景 |
|---|---|---|
| AC-01 | `OnboardingFormSchemaTest`（扩展） | AE 机构 / AE 个人 / SA 自由职业者 / EG 个人 v3：BUSINESS 新增 4 字段；机构退款政策 EVENTUALLY、个人 OPTIONAL；天数 `visibleWhen`；`catalogs.COMMERCIAL_PAYMENT_METHOD` 只含 ACTIVE 的 PAYMENT_METHOD（FEATURE、RETIRED 被过滤）；commercial 5xx / 非法响应 → 503 |
| AC-01 / AC-02 / AC-03 | 新 `BusinessInfoCollectionTest` | 草稿：`" card , apple_pay,CARD"` → `"APPLE_PAY,CARD"`；非法代码、> 20 项 → 422；`http://` 退款政策 → 422；天数 `"abc"` → 422、`"007"` → `"7"`；交付方式改 INSTANT 清空天数；非 PRE_ORDER 单独写天数 → 422。清单 / 提交：未选支付方式 → `CURRENTLY_DUE`；含 RETIRED 代码 → `FIELD_VALUE_NOT_IN_CATALOG`；PRE_ORDER 缺天数 → `CURRENTLY_DUE`、天数 `366` → `FIELD_FORMAT_INVALID`；机构缺退款政策 → `EVENTUALLY_DUE` 且可提交；个人缺退款政策 → 无清单项；commercial 不可用提交 → 503 且无写入（`draft_revision`、状态不变） |
| AC-04 | `LegacyApplicationReadTest` / `ResubmitAfterReturnTest`（扩展） | 冻结在 v2 的申请（夹具直接写 v2 `schema_id`）照常读取、清单不出新字段、可提交，且不调用 commercial |
| AC-05 | `ClientApplicationAdminTest`、`OnboardingAdminQueryTest`（扩展） | admin 申请 / Client 详情 values.business 带 4 键；Schema 配置查看 `widget=MULTI_SELECT`，外部目录不出现在目录项列表 |
| AC-06 | `auto/ext/mq/onboarding/ClientReviewLifecycleV2Test`（扩展） | 批准后 `t_party_profile.content_hash` 等于按含新四键的 values 重新计算的摘要（Checker 复算） |
| 守卫 | `auto/architecture/ReferenceCatalogGuardTest`（扩展） | `t_catalog_item` 与初始化 DML 无 `COMMERCIAL_` 目录码；`FULFILLMENT_TYPE` 4 项存在 |

client-console-bff：`GetClientFormSchemaProcessor` 回归测试——client-master 返回 `MULTI_SELECT` 字段与 `COMMERCIAL_PAYMENT_METHOD` 目录时原样到达页面响应。ops-bff：无。

Web 单测：client-console-web（`codeList` 拆分 / 连接与规范化一致性、`fieldIssue` 的 https / 天数、`isVisible` 天数条件、多选控件交互）；ops-web（`schemaLabel` 多值、交付方式、天数、"未采集"、链接属性）。两端 `tsc`、build、i18n 校验。

### 12.2 本机 e2e（阶段 5）

`mor_client_master_v2e2e` 按 9.4 先补法务修订 DML 再执行本需求 DDL + DML；client-master 本机 JAR 替换（临时文件 + rename，原参数重启）；两个 Web 本地构建。浏览器 en / zh-CN × 1440 / 390：门户新建 AE 机构申请（多选增删、交付方式切换天数显隐与清空、http 链接报错、提交）、AE 个人（无退款政策可提交）；运营审核"申请"页签与 Client 详情（标签、链接新开页、在途旧申请显示"未采集"）。

### 12.3 mor-test 验收（阶段 6—7，单独授权）

1. 本机临时库试跑（9.4）。
2. DDL → client-master 新镜像 → client-console-web、operations-console-web → 两个语言包 → DML（ACTIVE）。
3. API：form-schema 9 个 v3 Schema 字段与选项；新申请草稿 / 清单 / 提交；在途 v1 申请审核不受影响。
4. 浏览器 en / zh-CN × 1440 / 390（门户与运营同上）；验收数据用后清理。
5. 回滚演练只做离线（回滚 SQL 在临时库执行）。

## 13. 实施切分与发布

| # | 任务 | 执行者 | 依赖 | 产物 |
|---|---|---|---|---|
| T1 | DDL、`client-master-schema.sql`、PDManer、测试 `init/ddl.sql`、物理决策文档；本机 auto 库执行前滚 DDL（本机授权内） | client-master 后端 | — | 9.1—9.3 |
| T2 | v3 DML + 回滚 + `client-master-initial-data.sql` 最终状态；本机临时库试跑 | client-master 后端 | T1 | 9.4 |
| T3 | 后端代码：`WidgetCode`、`ReferenceCatalogCodes`、`ClientDraftPatch`（3 个 Kind）、`ClientDraftFields` / `ClientDraftMapper.xml` / `ClientDraftValues`、`ClientDraftPreparer` 天数规则、`ClientPaymentMethodCatalogClient` + Bean、`GetOnboardingFormSchemaProcessor` / `FormSchemaConverter`、`ClientSubmissionPreparer` / `RequirementInput` / 评估器、`OnboardingPackServiceImpl` 外部目录剔除 | client-master 后端 | T1 | 第 8 节 |
| T4 | auto 测试与 Mock / Mocker（12.1）；client-master 全模块串行跑（共享本机库） | client-master 后端 | T2 T3 | 测试报告 |
| T5 | client-console-bff 回归测试 | client-master 后端（顺带） | T3 | 1 个测试 |
| T6 | client-console-web：`model.ts`、多选控件、`schema.ts` 校验、草稿收发、文案 en / zh-CN（**原型确认前不开始**） | client-console-web | T3 + 原型 | 单测 / build |
| T7 | operations-console-web：`schemaLabel.ts`、`ReviewEvidence.tsx`、`ClientDetail.tsx`、Schema 配置查看文案、文案 en / zh-CN（**原型确认前不开始**） | ops-web | T3 + 原型 | 单测 / build |
| T8 | 语言包两份（client-console、operations-console）与发布计划 | 主会话或 ops-web 执行者 | T6 T7 | `release/i18n-bundles/` |
| T9 | 文档：`CLIENT_CONTRACT_DATABASE.md`、国家包说明、`CURRENT_IMPLEMENTATION.md`（实现后） | client-master 后端 | T4 | — |

发布（mor-test，单独授权）：

| 顺序 | 动作 | 回滚 |
|---|---|---|
| 1 | `mor_client_master` 前滚 DDL | DDL 回滚（DML 已回滚前提下；数据丢失） |
| 2 | client-master 新镜像（固定 sha256） | 旧镜像（DDL 可保留） |
| 3 | client-console-web、operations-console-web 新镜像（旧 Web 不认识 `MULTI_SELECT`，必须先于 DML ACTIVE） | 旧镜像（须先回滚 DML） |
| 4 | 两个语言包 | 语言包回退到上一版本 |
| 5 | v3 DML（ACTIVE；可先 DRAFT 写入核对） | DML 回滚（有 v3 申请时拒绝） |

两个 BFF、commercial、Gateway 注册：不发布。

### 13.1 待主会话确认

| 编号 | 问题 | 本稿取舍 |
|---|---|---|
| O-1 | 新字段的分组：支付方式与退款政策放现有 `OPERATIONS` 组，交付方式与天数新建 `FULFILLMENT` 组 | 按此；若原型合为一组，只改 DML 的 `display_group` 与一个组文案键 |
| O-2 | 交付方式文案键用目录项惯例 `onboardingSchema.catalog.FULFILLMENT_TYPE.<值>`（与 `t_catalog_item.label_key` 及运营 `catalog:` 显示族一致），而不是 `fulfillmentType.<值>` | 按目录项惯例；支付方式不是本库目录，沿用 S-05 的 `paymentMethod.<代码>` |
| O-3 | 发布顺序把 DML ACTIVE 放在 Web 与语言包之后（法务修订是 DML 在 Web 之前） | 必须如此：旧门户遇到 `MULTI_SELECT` 无法渲染 |
| O-4 | `mor_client_master_v2e2e` 先补跑法务修订 DML | 补跑；否则本机联调只能用 v1 Schema，无法覆盖 v3 |
| O-5 | commercial 支付方式不缓存 | 不缓存；若阶段 7 观察到 form-schema 延迟再加进程内短缓存 |
