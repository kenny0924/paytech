# tax：结账算税与事件登记 · 技术方案

> 状态：**阶段 2 已确认；阶段 3 评审稿（2026-10-29）**。第 1—6 节为阶段 2 只读核对（基于 `e3e0c12f`），第 7—17 节为阶段 3 方案，均未改代码、未执行 SQL。
> 依据：[2026-10-29 tax 决定](../../../agent-docs/decisions/2026-10-29-tax-domain-fact-service.md)、[design-discuss/tax](../../../design-discuss/tax/design.md)。需求：[requirement.md](./requirement.md)。

路径简写：`acq-core/` = `paytech/acquiring/paytech-acquiring-core/src/main/java/com/paytech/acquiring/`，`acq-app/` 同理；`bil-core/` = `paytech/billing/paytech-billing-core/src/main/java/com/paytech/billing/`。

## 1. 核对范围与证据

| 范围 | 证据位置 |
|---|---|
| acquiring 建单算税 | `acq-core/service/processor/checkout/CreateCheckoutProcessor.java:141-161`；`acq-core/domain/checkout/CheckoutInitializationContext.java:41-79,171-199`；`acq-core/domainservice/service/checkout/impl/CheckoutCreationServiceImpl.java:31-43` |
| 零税 Mock | `acq-app/configuration/CustomerCheckoutIntegrationConfiguration.java:36-49`；端口 `acq-core/domainservice/port/customer/CheckoutTaxPort.java:8` |
| 税务模型与订单列 | `acq-core/domain/customer/CustomerCheckoutModels.java:51-84,177,183`；`paytech/acquiring/docs/database/sql/acquiring-schema.sql:24-26,56-57,95-113` |
| 消费者资料提交 | `acq-core/service/processor/customer/SaveConsumerProcessor.java:56-69`；`CustomerCheckoutServiceImpl.java:21-29`；`CommerceOrderMapper.xml:26-36` |
| 支付提交 | `acq-core/service/processor/.../SubmitCustomerPaymentProcessor.java:166-195,238-246` |
| 金额状态 | `CreateCheckoutConverter.java:42`（写死 FINAL）对照 `paytech/acquiring/api/openapi/client-v1.yaml:874-889`（ESTIMATED）；`GetOrderProcessor.java:125`；`GetCheckoutConverter.java:30` |
| 支付成功事件 | `acq-core/domain/order/OrderPaymentSucceededPayload.java:22,38-76`；`acq-core/ext/job/OrderPaymentSucceededHandler.java:62-128`；`paytech/foundation/paytech-messaging/catalog/events.yaml:7-24,192-204` |
| 商业关系快照 | `paytech/commercial/paytech-commercial-core/.../domain/relationship/RelationshipSnapshot.java:50-74`；MOCK 模式 `acq-app/configuration/CommercialMockConfiguration.java:20-40`、`application.yml:130-131` |
| billing 组装与不变量 | `bil-core/ext/mq/OrderPaymentSucceededEventReader.java:33,82,121`；`bil-core/domainservice/service/impl/PaymentFactAssemblerImpl.java:101,119-135,291,308-310`；`bil-core/domainservice/validator/fact/BillingFactInvariants.java:60-70,206-213,271-301`；`paytech/billing/docs/database/sql/billing-schema.sql:53,79-104` |
| account 映射 | `paytech/account/paytech-account-core/.../validator/FactBalanceMappingRule.java:38-60,97-104` |
| ledger 过账 | `paytech/ledger/paytech-ledger-core/.../TransactionPostingServiceImpl.java:236-239,271-279,572,603-609,672-678`；`ledger-initial-data.sql:81,124,170-171` |
| tax 空壳 | `paytech/tax/pom.xml:20-24`、`paytech-tax-app/src/main/resources/application.yml`、`docs/database/sql/tax-schema.sql`（24 表）；`tools/arch-rules/src/main/resources/table-ownership.yaml:420-445` |
| file 服务 | `InternalFileApi.java:35-148`（`/internal/files/v1`）；[文件地址决定](../../../agent-docs/decisions/2026-10-22-file-upload-address-principles.md) |
| Operations / checkout-web | `operations-console-web/src/pages/` 无订单页；`operations-console-bff` 无 acquiring 订单客户端；`checkout-web/` 只有 `docs/checkout.pen` 与 `design-index.md` |
| mor-test | `test-environment/compose.applications.yaml:820-850`（tax-service 已部署，空壳）；`scripts/initialize.py:52`；`requirements/20261018/test-environment-V2部署与数据迁移/scripts/register_xxl_executors.py:57-66` |

## 2. 关键发现

| 编号 | 发现 | 影响 |
|---|---|---|
| F-01 | acquiring 只在建单时算一次税，存三个汇总数到订单；**逐行税额从未落库**（`t_order_item` 无税列，Mapper 一律 `NULL AS line_tax_amount`）；`TaxFacts` 的 hash、行、国家只校验不保存 | 需要新的税务计算落点 |
| F-02 | 建单后没有任何路径修改订单金额；Schema 注释把 Order Context、商品行定为不可变 | 两阶段重算与"不可变"注释冲突，需明确哪些可变（S-04） |
| F-03 | 法律合同在建单时用建单税额渲染（`documents.render(..., amounts)`），消费者接受发生在资料提交之后 | 重算后合同金额会过期（S-05） |
| F-04 | 计税国家来自 Client 的 `consumerCountryCode`；消费者账单国家另存 `t_consumer_context.billing_country_code`，注释明确"与计税国家独立" | 两阶段报价的证据来源已具备 |
| F-05 | 全链路没有价格模式（含税 / 未税），金额隐含未税：`total = net + tax + rounding` | 含税价需要新增 Client API 字段（S-06） |
| F-06 | CreateCheckout 代码写死 `READY/FINAL/OPEN`，OpenAPI 文档是 `PENDING/ESTIMATED/PREPARING` | 本需求按文档纠正（S-07） |
| F-07 | 支付提交只检查订单 OPEN、有消费者上下文、合同 ID 一致，不检查税是否最终 | 需加"最新 READY 计算"门禁 |
| F-08 | `OrderPaymentSucceeded` v1 没有任何税额；目录规定新增可空字段不升版本，破坏性变更升 v2 且消费方先兼容；billing 只接受 v1 | 采用"可空新增、不升版本"可免双版本过渡（S-08） |
| F-09 | MOCK 商业解析（mor-test 当前模式）不产出任何角色绑定，没有 `TAX_RESPONSIBLE`；REAL 模式由 commercial 推导：MoR 时 = 收单 OE，PSP 时 = Client | mor-test 演示走不通（S-03） |
| F-10 | billing 自动 PAYMENT：`CLIENT_PRINCIPAL = gross − Σfee`；不变量要求 Σ(本金+费用) = gross；`SELLER_TAX` 不在行目录、`TAX_AUTHORITY` 无身份校验 | 三处不变量与组装都要改，本金须减税额 |
| F-11 | account 只允许 OE 或 TAX_AUTHORITY 侧出现 `SELLER_TAX`，**CHANNEL 不能作付方** | 渠道付税额需加一条映射（S-09） |
| F-12 | ledger 要求一笔事实所有 `SELLER_TAX` 行的收方 ID 唯一且非空，多行合并为一笔 `TAX_PAYABLE` 贷记 | 收方 ID 取纳税主体则天然唯一，与决定第 10 条"每账本一个控制科目"一致，ledger 免改（S-09） |
| F-13 | billing 明细行**没有可索引的证据列**，只有 `source_evidence` JSON；`uk_fact_ref(fact_id, ref_type)` 每类引用一条 | 决定第 8 条"三个可索引证据列"需要 DDL；可改为收方 ID + JSON（S-09） |
| F-14 | billing REFUND、acquiring 退款事件、Payment 退款结果回传均未实现（`UnavailablePaymentRefundClient` 恒抛错） | 印证 Q1 不做退款 |
| F-15 | **Operations 控制台没有订单详情页**，BFF 没有 acquiring 订单客户端，acquiring 只有 Client 范围的订单查询 | R9 原写法不成立（S-01） |
| F-16 | **checkout-web 没有代码**，只有设计稿，且设计明确"不承诺可下载收据" | R8 的消费者下载入口无处落地（S-02） |
| F-17 | tax 是空壳但**已部署在 mor-test**（24 张旧表）；`tax_app` 只有 DML 权限，`--apply-service-sql` 只对空库生效 | 切换需要 root 删表重建，阶段 6 单独授权（S-10） |
| F-18 | tax 的 PDManer 仍由 Finance 生成器产出且有已知漂移 | 按 billing 先例移出生成器，直接维护 JSON（S-11） |
| F-19 | 目前没有任何后端服务自己生成并上传文件；file 服务按（来源系统，访问级别）选写入路由，缺路由返回 503 | tax 是首个服务端上传方，需要 PRIVATE 写路由（S-12） |
| F-20 | 订单行 `tax_category_code` 由 Client 自由填写、无目录校验，Mock 也不使用 | 接入后未知税类会 BLOCKED，影响 mor-test 演示与 e2e（S-13） |

## 3. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 tax 服务落地 | 空壳，24 张旧表（F-17） | 13 张新表 + 补偿 / 消费标准表 | 全部代码、模型、Schema | 新建；旧 Schema 作废 |
| R2 CalculateTax | 不存在 | READY / BLOCKED / NOT_MANAGED，逐行组件 | 引擎、规则包、API | 新建 |
| R3 地区包与主数据 | 无 | 三国 B2C 包、税类、演示 market / registration | DML | 初始化 DML（Q2 已定） |
| R4 acquiring 真实算税 | 零税 Mock，建单一次（F-01、F-02） | 建单暂估 + 资料提交后重算，支付用最新 READY | Client、落点、重算、门禁、合同重渲染 | 新增计算表；见 S-04、S-05 |
| R5 事件载荷 | 只有 `financeTaxSnapshotId`（F-08） | 计算 ID、税务状态、逐组件税额 | 字段 | 可空新增、保持 v1（S-08） |
| R6 tax 消费事件 | 无 | 建正式交易与底稿，幂等 | 消费者、期间生成 | 复制 billing 消费基础设施 |
| R7 billing SELLER_TAX | 只记引用（F-10、F-13） | 逐组件 `SELLER_TAX`、本金减税 | 组装、不变量、account 映射 | 见 S-09 |
| R8 B2C 简化税票 | 无；checkout-web 无代码（F-16、F-19） | 系统分号生成文件 | 生成与存储可做，消费者下载入口无处落地 | 见 S-02 |
| R9 运营只读查询 | 无订单详情页（F-15） | 运营可查某订单的税务 | 页面、BFF、查询来源 | 见 S-01 |
| R10 文档 | — | 模型、台账、事件目录 | — | 随实现 |

## 4. 逐系统影响

| 系统 | 业务职责 | 影响点 | 代码 / 模型 / 配置 | 上下游与兼容风险 |
|---|---|---|---|---|
| tax | 税务事实 Owner | 13 表、`CalculateTax` 与查询 API、计算引擎、`OrderPaymentSucceeded` 消费者、正式事实与底稿、期间、简化税票、补偿事件、app 依赖（messaging、compensation-event、mybatis、http-client、platform-support-api） | 全部新增；旧 Schema 与测试 DDL 作废；arch-rules 表归属改为 13 + 标准表；PDManer 移出 Finance 生成器 | mor-test 删表重建；首个服务端文件上传方 |
| acquiring | 订单与结账流程 | tax HTTP Client 替换 Mock；建单暂估；资料提交后重算并重渲染合同；支付门禁；新增订单税务计算表；`t_order_context.finance_tax_snapshot_*` 处理；CreateCheckout 金额状态纠正；事件可空新增字段；测试改用 tax DTO Mock 端点 | 代码、表、事件、对外响应语义 | 订单金额在接受前可变；存量订单无新计算（读路径兼容）；e2e 断言需更新 |
| billing | 计费事实 | Reader 读新字段；Assembler 逐组件 `SELLER_TAX`、本金减税额；Invariants 三处；新引用类型 `TAX_CALCULATION` 替代 `TAX_SNAPSHOT` | 代码；无 DDL（S-09 采纳时） | 新旧事件并存期：无税务字段按无税处理 |
| account | 明细账 | `FactBalanceMappingRule` 允许 CHANNEL → TAX_AUTHORITY 的 `SELLER_TAX` | 一处映射 + 测试 | 无存量影响 |
| ledger | 总账控制科目 | 现有 `TAX_PAYABLE` 规则直接承接；无代码改动（S-09 采纳时） | 仅测试补多行用例 | — |
| commercial | 商业关系快照 | 无代码改动；REAL 已输出 `TAX_RESPONSIBLE` | — | MOCK 模式问题见 S-03（改在 acquiring） |
| foundation messaging | 事件目录 | `OrderPaymentSucceeded` 消费方加 tax（`TaxTransactionRecorded` 本期不发布，见 11.4） | 配置 | — |
| Platform | file、注册、语言包 | tax-service PRIVATE 文件写路由；运营税务页的 Route / 权限 / 菜单注册；语言包 | 注册数据 DML | 注册块编号阶段 3 查现网再定 |
| operations-console-bff / web | 运营页面 | 新"税务"页（S-01）；BFF 新 tax 客户端 | 页面、BFF | 须先出 Pencil 画板 |
| checkout-web | — | 本需求不涉及（S-02） | 无 | — |
| mor-test | 环境 | `mor_tax` 删表重建；compose 加 RabbitMQ / XXL / Platform 环境变量；XXL 执行器注册；acquiring 演示数据税类 | 环境 | 阶段 6 单独授权 |

## 5. 待决事项

| 编号 | 问题 | 建议 |
|---|---|---|
| S-01 | Operations 没有订单详情页（F-15）。做一个完整订单详情页要新增 acquiring 运营查询接口、BFF 聚合与页面，超出税务范围 | **单独的"税务"菜单页**：按订单号查询，BFF 只调 tax 的查询接口，展示计算、正式事实、底稿与税票；不新增 acquiring 运营接口。订单详情页以后有了再嵌入 |
| S-02 | checkout-web 没有代码，消费者下载入口无处落地（F-16） | 本需求只**生成并保存**简化税票，运营在 S-01 页面可下载；消费者与 Client 的下载入口随 checkout-web 实现。AC-14 相应收窄 |
| S-03 | mor-test 用 MOCK 商业解析，没有 `TAX_RESPONSIBLE`（F-09） | MOCK 配置补出 MoR 角色绑定（`TAX_RESPONSIBLE` = AE001 的 Party），与 REAL 推导一致；REAL 不改。这样 mor-test 演示走真实算税 |
| S-04 | 重算与"订单不可变"冲突（F-02） | 新增 acquiring 表"订单税务计算"按 revision 追加（calculation ID、状态、是否暂估、汇总与逐行税额、证据国家）；订单表的 `tax_amount / total_amount` 改为"当前有效报价"，只允许在消费者接受前随重算更新；Order Context 与商品行仍不可变；`t_order_context.finance_tax_snapshot_*` 改为建单计算 ID、可空，阶段 3 定迁移 |
| S-05 | 合同按建单税额渲染，重算后会过期（F-03） | 重算导致金额变化时，在同一流程内重渲染合同（接受前允许），支付门禁同时校验合同绑定的计算 ID 等于最新 READY 计算 |
| S-06 | 无价格模式（F-05） | 本需求只支持未税价；tax 引擎实现含税反算并在 API 层测试，acquiring 不新增 Client API 字段；AC-02 的含税用例改为 tax 接口级验收 |
| S-07 | CreateCheckout 金额状态与 OpenAPI 不一致（F-06） | 按 OpenAPI 纠正：建单返回 `ESTIMATED`，消费者资料提交且计算 READY 后为 `FINAL`；BLOCKED 时不可支付并给出原因 |
| S-08 | 事件改版方式（F-08） | **可空新增、保持 v1**：增加 `taxCalculationId`、`taxStatus`、`taxComponents[]`（纳税主体、辖区、注册、税额）；`financeTaxSnapshotId` 保留并置空，后续再删。免去 billing 双版本过渡 |
| S-09 | `SELLER_TAX` 行的付收方与证据（F-10—F-13） | 付方 CHANNEL、收方 `TAX_AUTHORITY` 且收方 ID = 纳税主体 Party（一笔事实唯一，ledger 免改）；account 补 CHANNEL 映射；辖区与注册写入 `source_evidence` JSON，不加列。**这修订决定第 8 条"三个可索引证据列"**：收方 ID 已建索引，辖区与注册目前没有查询消费者，子分类账核对按账本 + 币种进行 |
| S-10 | mor-test 的 tax 已有 24 张旧表（F-17） | 阶段 6 提供 root 删表重建脚本与回滚（旧表无业务数据），单独授权执行 |
| S-11 | tax PDManer 由 Finance 生成器产出（F-18） | 移出生成器，直接维护 JSON，与 billing 先例相同 |
| S-12 | tax 首个服务端上传文件（F-19） | Platform 增加 tax-service 的 PRIVATE 写路由；tax 走 start → PUT → complete 三步；运营下载经 BFF 授权 |
| S-13 | 未知 `tax_category_code` 会 BLOCKED（F-20），可能打断 mor-test 演示与 e2e | 未知税类一律 BLOCKED，不默认；阶段 3 列出 mor-test 演示数据与 e2e 脚本中的税类并改为 `SAAS_AUTOMATED`，或在地区包里为它们登记明确的税类别名 |

## 6. 主要风险与工作量依据

- **acquiring 改动面最大**：建单、资料提交、支付提交三条主流程都要改，外加合同重渲染与新表；测试中依赖零税 Mock 的检查器与构造器约 10 个文件需要改为 DTO Mock。
- **跨系统发布顺序**：tax 先上（可被调用、可消费事件）→ billing / account（兼容无税字段的事件）→ acquiring（开始发送税务字段并调用 tax）。回滚按相反顺序，acquiring 回滚后事件不含税务字段，billing 按无税处理。
- **mor-test 演示数据**：真实算税后演示订单总额会变化，依赖固定金额的验收脚本要同步。
- **规则包内容**：演示包按 regional-policies.md 起草，标注未经税务顾问复核（Q6）。

---

> **阶段 3 评审稿（2026-10-29）。** 以下第 7—16 节是本次方案 / 计划，不代表已实现或已执行。阶段 2 待决 S-01—S-13 已按建议确认。

## 7. 规模与方案总览

**一句话规模（本次计划）：** 涉及 8 个系统（tax、acquiring、billing、account、foundation messaging、Platform 注册、operations-console-bff、operations-console-web）；新增 5 个接口（tax 内部 3 个、ops-bff 2 个），调整 3 个已有接口的响应语义（acquiring CreateCheckout、Customer GetCheckout、Customer SaveConsumer）；tax 新建 16 张表（13 张业务表 + 3 张标准表）、作废旧 24 张；acquiring 新增 1 张表、修改 2 张表共 4 个字段；billing、account、ledger 无 DDL；tax 初始化 DML 约 16 行，Platform 注册 DML 约 6 行；新增 1 个运营页面；原型 13 张（运营页 6 张、收银台 3 张、税票版式 4 张，见第 13 节）。

```text
Client 建单 ──► acquiring CreateCheckout ──(事务外)──► tax POST /calculations（PROVISIONAL，Client 声明国家）
                         │                                     └─► 计算落库，返回 READY / BLOCKED / NOT_MANAGED
                         └─ 本地事务：订单 + 计算记录 r1（ESTIMATED，不可支付）
消费者提交资料 ─► acquiring SaveConsumer ──(事务外)──► tax POST /calculations（FINAL，账单国家）+ 合同重渲染
                         └─ 本地事务：计算记录 r2、订单当前报价、合同 r2（接受前）
消费者付款 ─────► acquiring SubmitPayment：门禁 = 最新计算 READY / NOT_MANAGED 且合同绑定同一计算
支付成功 ───────► acquiring 发布 OrderPaymentSucceeded（v1 + 可空税务字段）
                         ├─► tax：建正式交易、底稿、期间；分号并生成 B2C 简化税票（补偿任务）
                         └─► billing：逐组件 SELLER_TAX（不读 tax）─► account ─► ledger TAX_PAYABLE
运营 ───────────► ops-web 财务 / 税务查询 ─► ops-bff ─► tax GET /orders/{orderNo}；下载经 file 服务授权
```

## 8. 数据模型

字段语义以 [design-discuss/tax/database.md](../../../design-discuss/tax/database.md) 为准，本节只给本期物理落点与取舍。全部遵守 [DATABASE_DESIGN_GUIDE](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)：主键 Snowflake `BIGINT`；代码列 `ascii_bin`；金额 `DECIMAL(24,8)`、税率 `DECIMAL(18,10)`；时刻 `DATETIME(6)` UTC；法定日期 `DATE`；审计列 `create_time / update_time`（追加写表只有 `create_time`）；无 FK / CHECK；表与列注释完整。

### 8.1 tax 新库结构（`mor_tax`，16 张表）

| 表 | 本期列（省略 `create_time / update_time`） | 唯一键 / 索引 | 本期取舍 |
|---|---|---|---|
| `t_tax_category` | `id`、`category_code`、`name`、`description`、`status` | `uk_tax_category(category_code)` | 首批 1 行 `SAAS_AUTOMATED` |
| `t_tax_market` | `id`、`subject_party_id`、`jurisdiction_code`、`tax_type`、`scheme_code`、`assessment_no`、`revision`、`valid_from`、`valid_to`、`obligation`、`collection_status`、`managed_by`、`registration_id`、`policy_id`、`approved_by`、`approved_at`、`review_due_at` | `uk_tax_market(assessment_no, revision)`；`idx_tax_market_subject(subject_party_id, jurisdiction_code, tax_type)` | 阈值、评估文件列推迟（无录入入口）；`managed_by` 首期 `PLATFORM_OPERATIONS` |
| `t_tax_registration` | `id`、`subject_party_id`、`jurisdiction_code`、`authority_code`、`tax_type`、`scheme_code`、`registration_no_ref`、`public_registration_no`、`effective_from`、`effective_to`、`status`、`reporting_currency`、`legal_timezone`、`filing_frequency` | `uk_tax_registration(subject_party_id, authority_code, registration_no_ref, tax_type, scheme_code)` | `public_registration_no` 为票据上展示的税号（主体的公开注册号，非个人资料）；缴款指引、日历代码推迟 |
| `t_tax_policy` | `id`、`package_code`、`revision`、`jurisdiction_code`、`tax_type`、`scheme_code`、`effective_from`、`effective_to`、`schema_version`、`status`、`policy_json`、`source_refs_json`、`approved_by`、`approved_at` | `uk_tax_policy(package_code, revision)`；`idx_tax_policy_scope(jurisdiction_code, tax_type, scheme_code, status)` | `policy_json` 本期只用 `applicability / placeOfSupply / taxPoint / treatments / calculation / documents` 六块 |
| `t_tax_calculation` | `id`、`source_system`、`source_object_type`、`source_object_id`、`source_object_no`、`source_revision`、`tax_scope_key`、`calculation_stage`、`supply_kind`、`business_mode`、`seller_party_id`、`buyer_client_id`、`tax_subject_party_id`、`acquiring_entity_no`、`currency`、`price_mode`、`tax_point_date`、`tax_point_basis`、`status`、`tax_amount_source`、`block_reasons_json`、`net_amount`、`charged_tax_amount`、`gross_amount`、`rounding_amount`、`expires_at` | `uk_tax_calculation_revision(source_system, source_object_type, source_object_id, source_revision, tax_scope_key)`；`idx_tax_calculation_no(source_object_no)`；`idx_tax_calculation_subject(tax_subject_party_id, create_time)` | 新增 `source_object_no`（订单号，运营查询用）、`calculation_stage`（`PROVISIONAL / FINAL`）、`rounding_amount`；买方快照、协议、机构证据推迟（B2B / counterparty 未接入）；不可变，只有 `create_time` |
| `t_tax_calculation_line` | `id`、`calculation_id`、`source_line_ref`、`line_no`、`category_code`、`quantity`、`unit_price`、`discount_amount`、`net_amount`、`charged_tax_amount`、`gross_amount`、`rounding_delta` | `uk_tax_calculation_line(calculation_id, line_no)` | 服务期间、退款基数、原行引用随退款需求加 |
| `t_tax_calculation_component` | `id`、`calculation_id`、`line_id`、`component_code`、`jurisdiction_code`、`tax_type`、`treatment`、`reason_code`、`liable_role`、`market_id`、`registration_id`、`policy_id`、`rule_code`、`tax_base`、`rate`、`charged_tax_amount`、`calculation_method`、`rounding_method`、`rounding_scale` | `uk_tax_calculation_component(line_id, jurisdiction_code, component_code)`；`idx_tax_component_calc(calculation_id)` | 增加冗余 `calculation_id` 便于整单读取；自计税额推迟 |
| `t_tax_evidence` | `id`、`calculation_id`、`evidence_type`、`source_code`、`country_code`、`subdivision_code`、`validation_status`、`result_code`、`checked_at` | `idx_tax_evidence_calc(calculation_id)` | 本期只有 `DECLARED_COUNTRY`（Client 声明）、`BILLING_COUNTRY`（消费者账单国家）两类 |
| `t_tax_transaction` | `id`、`calculation_id`、`source_system`、`source_fact_type`、`source_fact_id`、`tax_scope_key`、`origin`、`transaction_kind`、`tax_subject_party_id`、`currency`、`occurred_at`、`tax_point_date`、`tax_point_basis`、`registered_at`、`reporting_status`、`reporting_block_reason`、`source_payload_hash` | `uk_tax_transaction_source(source_system, source_fact_type, source_fact_id, tax_scope_key)`；`uk_tax_transaction_calc(calculation_id)` | `source_payload_hash` 用于"同身份不同内容"判定；本期只有 `ORIGINAL` / `AUTO` |
| `t_tax_reporting_entry` | `id`、`transaction_id`、`component_id`、`registration_id`、`period_id`、`entry_role`、`reporting_rule_code`、`reporting_policy_id`、`source_currency`、`source_tax_base`、`source_tax_amount`、`reporting_currency`、`fx_rate`、`fx_rate_date`、`fx_source_ref`、`reporting_tax_base`、`reporting_tax_amount`、`tax_point_date` | `uk_tax_reporting_entry(transaction_id, component_id, registration_id, entry_role, reporting_rule_code)`；`idx_tax_entry_period(period_id)` | 本期只有 `OUTPUT`；跨币种缺法定汇率时不建本行、交易标 `PENDING`（汇率来源阶段 4 用 fx 服务或人工，见 9.4） |
| `t_tax_period` | `id`、`registration_id`、`period_start`、`period_end`、`filing_due_date`、`payment_due_date`、`reporting_currency`、`status` | `uk_tax_period(registration_id, period_start, period_end)` | 首次需要时按注册的 `filing_frequency` 自动生成；截止日按注册规则计算，本期只读 |
| `t_tax_document` | `id`、`transaction_id`、`document_kind`、`document_scope_key`、`issuance_mode`、`issuer_party_id`、`registration_id`、`series_code`、`document_number`、`issued_at`、`currency`、`policy_id`、`template_code`、`template_revision`、`content_schema_version`、`document_content_json`、`issue_status`、`submission_status`、`submission_source`、`document_file_address`、`document_digest`、`void_reason` | `uk_tax_document(transaction_id, document_kind, document_scope_key)`；`uk_tax_document_number(issuer_party_id, registration_id, series_code, document_number)` | 本期只有 `RECEIPT`（B2C 简化税票）、`SYSTEM`、`NOT_REQUIRED` |
| `t_tax_document_sequence` | `id`、`issuer_party_id`、`registration_id`、`series_code`、`document_kind`、`next_number`、`number_format` | `uk_tax_document_sequence(issuer_party_id, registration_id, series_code)` | 年度编进 `series_code`（如 `SA-RCPT-2026`） |
| `t_compensation_event` | 与 billing 相同的标准结构 | 同 billing | 补偿事件：生成税票 |
| `t_consumed_event`、`t_consumed_aggregate_version` | paytech-messaging 标准结构 | 同 billing | 消费 `OrderPaymentSucceeded` 的去重 |

不建（后续需求）：`t_tax_return`、`t_tax_return_item`、`t_tax_payment`、`t_tax_payment_allocation`、`t_tax_payment_event`、`t_tax_credit_application`。

### 8.2 acquiring 变更（`mor_acquiring`）

| 表 | 变更 | 说明 |
|---|---|---|
| `t_order_tax_calculation`（新） | `order_tax_calculation_id`、`order_id`、`revision`、`tax_calculation_id`、`calculation_stage`（`PROVISIONAL / FINAL`）、`tax_status`（`READY / BLOCKED / NOT_MANAGED`）、`basis_country_code`、`tax_amount`、`rounding_amount`、`total_amount`、`block_reason_code`、`lines_json`（逐行 `clientItemId / netAmount / taxAmount / totalAmount`）、`components_json`（事件所需的纳税主体、辖区、注册、税额，按组件）、`expires_at`（阶段 4 补：FINAL 取 tax 响应，PROVISIONAL / NOT_MANAGED 为空，支付门禁据此判断过期而不回调 tax）、`create_time`；`uk_order_tax_calc(order_id, revision)`、`uk_order_tax_calc_ref(tax_calculation_id)` | 追加写、不可变；最大 revision 即当前报价；`lines_json` 补上 F-01 的逐行税额落点；`components_json` 让支付成功事件不必回查 tax |
| `t_commerce_order` | `tax_amount / rounding_amount / total_amount` 保留，语义改为"当前有效报价"，只在消费者接受前随新 revision 更新；新增 `amount_status`（`ESTIMATED / FINAL / BLOCKED`） | 列注释同步改写 |
| `t_order_context` | `finance_tax_snapshot_id`、`finance_tax_snapshot_hash` 改为可空，新单写 NULL；新增 `initial_tax_calculation_id`（可空，建单计算） | 存量订单读路径兼容；两列后续需求删除 |
| `t_consumer_contract` | 新增 `tax_calculation_id`（可空，存量为空）；唯一键 `uk_consumer_contract(order_id)` 改为 `(order_id, tax_calculation_id)` | 每次金额变化重渲染一份合同；接受记录引用的合同必须绑定当前计算 |

迁移：一个前滚脚本、一个回滚脚本（回滚前置条件：无新单写入新列；回滚恢复 NOT NULL 前需检查新单空值并阻断）。

### 8.3 billing / account / ledger

无 DDL。billing `t_billing_fact_ref.ref_type` 列注释允许值增加 `TAX_CALCULATION`（注释变更，非结构变化）；`TAX_SNAPSHOT` 保留只读兼容存量事实。

### 8.4 SQL 文件

| 文件 | 目标库 | 性质 |
|---|---|---|
| `paytech/tax/docs/database/sql/tax-schema.sql` | `mor_tax` | 全量重写为 16 张表（空库执行） |
| `paytech/tax/docs/database/sql/changes/2026-10-29-tax-rebuild.sql` / `-rollback.sql` | `mor_tax` | root 执行：删除旧 24 表 + 建新 16 表；回滚：删新表 + 建旧 24 表空表（旧表无业务数据） |
| `paytech/tax/docs/database/sql/tax-initial-data.sql` | `mor_tax` | 税类、三国地区包、演示 market / registration / sequence（9.5） |
| `paytech/acquiring/docs/database/sql/changes/2026-10-29-order-tax-calculation.sql` / `-rollback.sql` | `mor_acquiring` | 8.2 |
| `requirements/20261029/tax-结账算税与事件登记/db/platform-registration.sql` | `mor_platform` | 文件写路由、菜单资源、Route、权限（第 12 节） |
| `requirements/20261029/tax-结账算税与事件登记/db/ddl.sql`、`dml.sql` | — | 只作索引，指向上列执行文件 |

## 9. tax 业务规则

### 9.1 计算流程（`CalculateTaxProcessor`）

1. 校验请求（金额、币种精度、行合计）。
2. 按 `taxSubjectPartyId` 查 `t_tax_market`：为空（阶段 4 裁决：CLIENT 来源 OE 无商业关系快照，税务责任方在 Client 一侧）或一条都没有 → `NOT_MANAGED`（为空时原因码 `TAX_RESPONSIBLE_UNRESOLVED`），金额取请求中的自报税额（缺省 0），不建组件与证据，仍落计算头与行（供追溯）。
3. 按国家证据确定供应地：`FINAL` 用 `BILLING_COUNTRY`，`PROVISIONAL` 用 `DECLARED_COUNTRY`；地区包 `placeOfSupply.algorithm` 本期只实现 `BUYER_COUNTRY`。
4. 取该地区、`tax_point_date` 有效的 market：`collection_status=BLOCKED` 或无记录 → `BLOCKED(MARKET_NOT_ENABLED)`；`obligation=NO_COLLECTION_REQUIRED` → 组件 `NOT_COLLECTED`、税 0、原因码来自地区包。
5. 取 market 指向的 APPROVED policy；逐行按 `treatments` 匹配（`EQ / IN` 合取，必须恰好命中一条，否则 `BLOCKED(NO_RULE / AMBIGUOUS_RULE)`）；未知税类 → `BLOCKED(UNKNOWN_TAX_CATEGORY)`（S-13）。
6. 逐行计算：未税 `tax = round(base × rate)`；含税 `tax = round(gross × rate / (1 + rate))`、`net = gross − tax`；取整按 `calculation.roundingMethod / roundingScale`（默认 `HALF_UP` + 币种 minor unit）；头 `rounding_amount` = 0（逐行取整，尾差在行上），保留列以兼容 acquiring。
7. 一个本地事务写头、行、组件、证据；返回结果。同一 `(source…, source_revision, tax_scope_key)` 重复请求：内容相同返回原计算，不同则 409。

`expires_at`：`PROVISIONAL` 为 null（不可支付，无需过期）；`FINAL` = 计算时间 + 地区包 `taxPoint.quoteValidityHours`（默认 24h）。acquiring 支付门禁校验未过期（跨税率生效日）。

### 9.2 登记流程（消费 `OrderPaymentSucceeded`）

1. `@DomainEventHandler(strictOrder=false, transaction=HANDLER_MANAGED)`；载荷缺 `taxCalculationId` 或 `taxStatus=NOT_MANAGED` → 记消费去重后直接返回（存量订单与 PSP）。
2. 读计算，校验状态 `READY`、`stage=FINAL`、币种与事件一致、`Σ charged_tax_amount` 等于事件 `taxComponents` 合计；不一致 → 业务异常，进死信并告警（B5）。
3. 事务外：确定期间（必要时生成）与汇率（同币种 = 1；跨币种取 fx 服务法定来源，取不到则 `reporting_status=PENDING`）。
4. 一个本地事务：`t_tax_transaction`（唯一身份 = `acquiring / ORDER_PAYMENT_SUCCEEDED / <订单 ID> / <tax_scope_key>`）、每个组件一条 `OUTPUT` 底稿、补偿事件 `TAX_DOCUMENT_ISSUE`。唯一键冲突且载荷哈希相同 → 幂等成功；哈希不同 → 死信。

### 9.3 简化税票（补偿任务 `TAX_DOCUMENT_ISSUE`）

1. 短事务：锁序列行、取号、插入 `RESERVED` 单据（内容 JSON：卖方名称与公开税号、单号、日期、行描述按税类、税率、净额、税额、总额、币种、法定文字键；不含消费者任何资料）。
2. 事务外：渲染 PDF → file 服务 `start → PUT → complete`（`PRIVATE`，reasonCode `TAX_RECEIPT`）。
3. 短事务：写 `document_file_address`、`document_digest`、`issue_status=ISSUED`。失败重试同一单据，不重新取号。

### 9.4 期间与汇率

- 期间：注册 `filing_frequency` 为 `MONTHLY / QUARTERLY`，按 `legal_timezone` 的日历月 / 季度生成；`filing_due_date` / `payment_due_date` 按地区包 `reporting.dueDateRule`（AE：期末 + 28 天；SA：次月最后一日；EG：次月最后一日）。
- 汇率：演示数据 AE 申报 AED、SA 申报 SAR、EG 申报 EGP，交易币种 USD 时需要法定汇率。**本期不接法定汇率源**：底稿 `PENDING`，`reporting_block_reason=FX_RATE_MISSING`，运营页可见；接入随申报需求。这不影响计算与入账（交易币种口径）。

### 9.5 初始化数据（`tax-initial-data.sql`，演示，未经税务顾问复核）

| 表 | 行 |
|---|---|
| category | `SAAS_AUTOMATED` |
| policy | `DEMO_AE_SAAS` r1（AE VAT 5%，`STANDARD` 制度）、`DEMO_SA_SAAS` r1（SA VAT 15%，`NON_RESIDENT`）、`DEMO_EG_SAAS` r1（EG VAT 14%，`SVRR`）；均 B2C、`unmatchedBehavior=BLOCK`、`source_refs_json` 指向 regional-policies.md 来源 |
| registration | AE001 Party 在 AE / SA / EG 各一份（`registration_no_ref` 与公开税号为 `DEMO-*` 占位） |
| market | 三地各一份，`collection_status=ENABLED`、`managed_by=PLATFORM_OPERATIONS` |
| document_sequence | 三份注册各一条 `RECEIPT` 序列 |

AE001 的 `subject_party_id` 在执行时由操作者从 `mor_client_master.t_operating_entity` 查出后代入（脚本顶部变量），不在应用里跨库读取。

## 10. 接口

### 10.1 tax 新增（内部，`paytech-tax-api`，`@HttpExchange`）

| Method + Path | 调用方 | 请求要点 | 响应要点 | 错误 |
|---|---|---|---|---|
| `POST /tax/v1/calculations` | acquiring | `sourceSystem`、`sourceObjectType=ORDER`、`sourceObjectId`、`sourceObjectNo`、`sourceRevision`（int）、`taxScopeKey=SALE`、`stage`、`supplyKind=SALE`、`businessMode`、`sellerPartyId`、`taxSubjectPartyId`、`buyerClientId`、`entityNo`、`currency`、`priceMode`（本期 acquiring 恒 `EXCLUSIVE`）、`taxPointDate`、`evidence[]{type,countryCode,subdivisionCode}`、`lines[]{lineRef,lineNo,categoryCode,quantity,unitPrice,discountAmount,netAmount}`、`declaredTaxAmount`（NOT_MANAGED 用，可空） | `calculationId`、`status`、`stage`、`taxAmountSource`、`netAmount`、`taxAmount`、`roundingAmount`、`grossAmount`、`expiresAt`、`blockReasons[]{code,path}`、`lines[]{lineRef,netAmount,taxAmount,grossAmount,components[]}`、`components[]{componentId,jurisdictionCode,registrationId,taxSubjectPartyId,treatment,rate,taxAmount}` | 400 参数；409 同身份不同内容；`BLOCKED` 是 200 正常结果，不用错误码 |
| `GET /tax/v1/calculations/{calculationId}` | 内部诊断、阶段 5 联调 | — | 同上 | 404 |
| `GET /tax/v1/orders/{orderNo}` | ops-bff | — | 该订单全部计算（按 revision）、正式交易与底稿（含 `reporting_status`）、单据（单号、状态、`documentFileAddress`）| 404 无记录 |

金额、ID 均为十进制字符串；时间带 offset ISO 8601；错误为 RFC 9457 Problem。

### 10.2 acquiring 调整（无新增路径）

| 接口 | 变化 |
|---|---|
| Client `POST` CreateCheckout | 按 OpenAPI 纠正：`amountStatus=ESTIMATED`；`taxAmount / totalAmount` 为暂估值；`BLOCKED` 时仍建单成功（订单可在消费者资料提交后重算），`amountStatus=BLOCKED` 并返回原因码；`NOT_MANAGED` 时税额为自报值、`amountStatus=FINAL`。OpenAPI 文案同步 |
| Customer SaveConsumer | 响应增加重算后的 `amounts` 与 `amountStatus`、`taxBlockReason`（可空） |
| Customer GetCheckout / Client GetOrder | `amountStatus` 改由最新 revision 推导（不再写死 / 按非空推导） |

### 10.3 ops-bff 新增

| Method + Path | 权限 | 说明 |
|---|---|---|
| `GET /tax/v1/orders/{orderNo}`（Gateway 对外 `/api/operations/tax/v1/...`） | 新权限 `TAX_VIEW` | 转发 tax 并转换为页面模型 |
| `POST /tax/v1/orders/{orderNo}/documents/{documentId}/download` | `TAX_VIEW` | 读该订单的税务记录并找到 ISSUED 单据（不属于该订单或未开具则 404），再调 file 服务 `authorizeDownload`，返回 60 秒链接；阶段 4 改为挂在订单下，tax 不新增按单据查询接口 |

### 10.4 枚举（`paytech-tax-api`）

`TaxCalculationStatus{READY, BLOCKED, NOT_MANAGED}`、`TaxCalculationStage{PROVISIONAL, FINAL}`、`TaxAmountSource{COMPUTED, DECLARED}`、`TaxTreatment{TAXABLE, ZERO_RATED, EXEMPT, OUT_OF_SCOPE, REVERSE_CHARGE, NOT_COLLECTED}`、`TaxBlockReason{MARKET_NOT_ENABLED, NO_RULE, AMBIGUOUS_RULE, UNKNOWN_TAX_CATEGORY, MISSING_EVIDENCE, POLICY_INVALID}`、`ManagedBy{SUBJECT_HOLDER, PLATFORM_OPERATIONS}`、`TaxReportingStatus{PENDING, READY}`、`TaxDocumentKind{RECEIPT}`（本期）、`IssuanceMode{SYSTEM, EXTERNAL}`、`TaxDocumentIssueStatus{RESERVED, ISSUED, VOID}`。

## 11. 事件

### 11.1 `OrderPaymentSucceeded`（schema 1，可空新增，S-08）

载荷定义在 acquiring core 的 `domain/order/OrderPaymentSucceededPayload`（`paytech-acquiring-api` 不发布载荷类，阶段 4 核实）。新增字段（均可空，旧消费者忽略）：`taxCalculationId`、`taxStatus`、`taxScopeKey`、`taxAmount`、`taxComponents[]{jurisdictionCode, registrationId, taxSubjectPartyId, taxAmount}`（按 `jurisdiction + registration` 汇总后的组件，金额十进制字符串）。`financeTaxSnapshotId` 新单置空。目录 `consumers: [billing, tax]`，Payload Schema 文件同步。

### 11.2 billing 组装

- 有 `taxComponents` 且 `taxStatus=READY`：每个组件一行 `SELLER_TAX`，付方 `CHANNEL(clearingAccountId)`、收方 `TAX_AUTHORITY(taxSubjectPartyId)`，`source_rule_type=TAX_RULE`、`source_rule_version_id=taxCalculationId`、`source_evidence` 含 `jurisdictionCode / registrationId`；本金 = gross − Σfee − Σtax，须 > 0。
- 新引用 `TAX_CALCULATION`（替代 `TAX_SNAPSHOT`）。
- 不变量：`PAYMENT_LINE_PARTIES` 加 `SELLER_TAX → (CHANNEL, TAX_AUTHORITY)`；`TAX_AUTHORITY` 身份校验为十进制 Party ID；合计校验含 `SELLER_TAX`；同一事实所有 `SELLER_TAX` 收方 ID 必须相同（与 ledger 一致）。
- 无税务字段或 `NOT_MANAGED`：与现状相同。

### 11.3 account / ledger

- account：`FactBalanceMappingRule` 的 CHANNEL 付方集合加 `SELLER_TAX`（不产生 Owner 分录，与现有 OE / TAX_AUTHORITY 侧一致）。
- ledger：无代码改动；补"一笔事实两行 `SELLER_TAX`、同一收方"过账测试，验证合并为一笔 `TAX_PAYABLE` 贷记。

### 11.4 tax 发布

`TaxTransactionRecorded` **本期不发布**。目录虽允许 `consumers: []`，但发布端 `unroutable-policy: FAIL` 下无订阅方的事件会发布失败并留存（台账中 reconciliation 四类事件的先例），只产生运维噪声。等运营待办或申报需求有了消费方再登记发布。

### 11.5 补偿任务（`paytech/tax/compensation-event-catalog.yaml`）

`TAX_DOCUMENT_ISSUE`（生成税票，重试至成功，超限告警）。

## 12. Platform 注册

| 项 | 内容 |
|---|---|
| 文件写路由 | （`tax-service`, `PRIVATE`）→ 现有私有桶；reasonCode `TAX_RECEIPT` |
| 菜单与资源 | 财务 → 税务查询（`/finance/tax`），Console Resource 1 条 |
| 权限 | `TAX_VIEW` 1 条，授予现有财务运营角色 |
| Gateway Route | ops-bff 2 条（10.3） |
| 语言包 | operations-console 新增 `tax.*` 命名空间键（en / zh-CN） |
| XXL | tax-service 执行器 + 补偿扫描任务 |

注册块编号与路由 ID 在阶段 6 查现网 `mor_platform` 后分配（按既有做法）。

## 13. 运营页面（财务 → 税务查询）

原型在 `operations-console-web/docs/operations-console.pen`（y=101400 一行，财务 · 16—21），PNG 预览在 [prototypes/](./prototypes/)。2026-10-29 按用户意见重做布局：首版信息平铺、没有主次，改为与"商务 · 14 合同详情"一致的骨架。

**页面骨架（所有结果页相同）：** 订单号大标题 + 状态药丸 + 一行"谁的订单、谁计税"；右上角常驻搜索框 → 税务进度条（建单报价 → 资料重算 → 支付成功 → 登记与开票 → 申报底稿），一眼看出卡在哪一步 → 一张提示卡，只说当前需要注意的一件事 → 正文两栏：左栏回答"收了多少税、为什么"，右栏放税票、登记、底稿与依据。不使用页内标签，状态徽标只保留标题处一个。

| 画板 | 内容 |
|---|---|
| [财务 · 16 已登记订单](./prototypes/16-registered.png) | 进度五步，前四步完成、"申报底稿"待补汇率；琥珀提示卡说明只影响申报不影响入账；左栏"税额构成"三个大号数字（净额 / 税额 / 应付）+ 一句白话解释为何按沙特 15% + 精简明细（规则码作为副文字）+"报价记录"（r2 当前、r1 暂估，写明变化原因与合同已重新生成）；右栏税票卡（缩略图、票号、下载 PDF）与"正式登记 / 申报底稿 / 计算依据"合并的一张分段卡 |
| [财务 · 17 重算被阻断](./prototypes/17-blocked.png) | 进度停在"资料提交重算"（红色）；红色提示卡"这笔订单暂时不能支付"；左栏"为什么被阻断"逐条原因（白话标题、原因码、由谁处理）+ 报价记录；右栏空状态"尚未支付，没有正式登记和税票"与本次匹配结果 |
| [财务 · 18 PSP 订单](./prototypes/18-not-managed.png) | 状态"不由平台计税"；三步进度（建单自报 → 支付成功 → 平台登记不适用）；提示卡说明税务由 Client 负责、不是免税；左栏金额（税额标注 Client 自报）；右栏"为什么不由平台计税" |
| [财务 · 19 进入页面](./prototypes/19-initial.png) | 居中大搜索框 + 说明能看到什么 + 最近查询（订单、Client 与国家、税额、状态、进入箭头），仅保存在本浏览器 |
| [财务 · 20 订单未找到](./prototypes/20-not-found.png) | 搜索框保留输入；空状态说明可能原因（订单号错误、税务服务上线前的订单、尚未报价） |
| [财务 · 21 税票下载失败](./prototypes/21-download-failed.png) | 在第 16 张上叠加右上角错误提示条：说明税票本身不受影响、重试下载、复制请求编号、可关闭 |

权限：`TAX_VIEW` 可查询与下载；页面全部只读。所有文案走 `tax.*` 语言包键（en / zh-CN）；"最近查询"只存在浏览器本地。

### 13.1 收银台与税票版式（暂存 operations-console.pen，待迁入）

按用户指示，以下 7 张暂时画在 operations-console.pen 的 y=102640 一行（收银台 3 张已于 2026-10-29 按标准结账页两栏结构重做：左栏商品与大号总额、税费行就近说明变化，右栏资料填写），标注"待迁入"；不切换 Pencil 当前文件，避免干扰同时编辑该文件的会话。收银台 3 张以后迁入 `checkout-web/docs/checkout.pen`，税票 4 张迁入 `design-discuss/tax/tax.pen`。

| 画板 | 内容 | 本需求的实现落点 |
|---|---|---|
| [收银台 · T1 暂估](./prototypes/T1-checkout-estimated.png) | 填写账单地址前：大号总额 105.00 下注"含估算税费，填写账单地址后确认最终金额"；税费行标"估算"并就近说明按商户提供的国家估算；右栏提示"税费会按账单地址所在国家计算" | Customer API 返回 `amountStatus=ESTIMATED`；页面随 checkout-web |
| [收银台 · T2 最终](./prototypes/T2-checkout-final.png) | 提交沙特地址后：总额 115.00"已按你的账单地址计算"；税费行"增值税 15%（沙特阿拉伯）"带"已更新"标签，下方说明原估算 5.00 与购买条款已更新；按钮"继续：确认购买条款" | SaveConsumer 响应带重算金额；合同重渲染（S-05） |
| [收银台 · T3 被阻断](./prototypes/T3-checkout-blocked.png) | 科威特地址：总额"—"并说明暂时无法计算；税费行"无法计算"与"我们不会在无法正确计税时向你收款"；错误就近显示在国家字段下（可更换地址或联系商户）；按钮置灰"暂时无法付款" | SaveConsumer / GetCheckout 返回 `BLOCKED` 与原因码；支付门禁拒绝 |
| [税票 · R1 AE](./prototypes/R1-receipt-ae.png) | 英文 Tax Invoice（简化），供应商与 TRN、明细、VAT 5%、合计 | 地区包 `documents` 模板 |
| [税票 · R2 SA](./prototypes/R2-receipt-sa.png) | 阿英双语 فاتورة ضريبية مبسطة，VAT No.、日期时间、VAT 15%、合计；QR 占位标"非居民是否适用待复核" | 同上 |
| [税票 · R3 EG](./prototypes/R3-receipt-eg.png) | 英阿双语 Receipt / إيصال，ETA SVRR 注册号、VAT 14% | 同上 |
| [税票 · R4 字段来源与规则](./prototypes/R4-receipt-fields.png) | 票面字段 → 数据来源；三国法定项（均"待复核"）；生成规则 ①—⑤ | 9.3 |

## 14. 代码结构

| 系统 | 新增 / 修改 |
|---|---|
| tax `-api` | `TaxCalculationApi`、`TaxOrderQueryApi`、请求 / 响应 Body、10.4 枚举、事件载荷类 |
| tax `-core` | `CalculateTaxProcessor`；`TaxPolicyService`（加载、Schema 校验、匹配）、`TaxComputationService`（纯计算，无 IO）；`TaxMarketRepository` 等 13 个 Repository / Mapper；`ext/mq/OrderPaymentSucceededTaxHandler` + `RegisterTaxTransactionProcessor`；`TaxPeriodService`；`ext/job` 补偿 Handler `TaxDocumentIssueHandler`、`TaxTransactionRecordedPublisher`；`integration/file` 上传客户端、`integration/fx`（本期只留端口与"不可用"实现） |
| tax `-app` | 依赖补齐（messaging、compensation-event、mybatis-support、http-client-support、redis-starter 视锁需要、platform-support-api）；`application.yml` 加 RabbitMQ、MyBatis、补偿、消费者分区、Platform URL |
| acquiring | `integration/tax/TaxCalculationClient` + 实现 `CheckoutTaxPort`；删除 Mock bean；`CreateCheckoutProcessor`、`SaveConsumerProcessor`（事务外重算与合同重渲染、事务内 revision + 当前报价 + 合同）、`SubmitCustomerPaymentProcessor`（门禁）；`OrderTaxCalculation` DO / Mapper / Repository；`OrderPaymentSucceededPayload` 新字段；MOCK 商业配置补 MoR 角色绑定（S-03） |
| billing | Reader 新字段；`PaymentFactAssemblerImpl` 税行；`BillingFactInvariants`；`FactRefType.TAX_CALCULATION` |
| account | `FactBalanceMappingRule` 一处 |
| ops-bff | `TaxClient`（integration）、`GetOrderTaxProcessor`、`AuthorizeTaxDocumentDownloadProcessor`、Controller |
| ops-web | `src/finance/TaxLookup.tsx`（或 `pages/FinanceTax.tsx`，按现有财务页约定）、菜单、i18n |

PDManer：`paytech/tax/docs/database/tax.pdma.json` 移出 Finance 生成器后直接维护（S-11）；`tools/arch-rules` 表归属改为新 13 张 + 标准表；acquiring 模型同步 8.2。

## 15. 测试与验证计划

### 15.1 auto

| 系统 | 入口 | 场景（对应 AC） |
|---|---|---|
| tax | HTTP `POST /calculations` | 三国 × 未税 / 含税（AC-01、AC-02）；NOT_MANAGED（AC-04）；BLOCKED 四种原因（AC-05）；三行含折扣尾差（AC-06）；同身份同内容幂等、不同内容 409；PROVISIONAL / FINAL 证据选择 |
| tax | MQ 监听 `OrderPaymentSucceeded`（真实本地 RabbitMQ） | 登记与底稿、期间生成（AC-07）；重复投递；金额不一致死信（AC-08）；无税务字段与 NOT_MANAGED 跳过（AC-12）；跨币种 PENDING |
| tax | 补偿 Handler | 税票分号与文件上传（file 服务测试 HTTP 端点 + DTO Mocker）；并发两笔不撞号；上传失败重试不换号（AC-11） |
| tax | 纯 Java | 规则包 Schema 与互斥校验 |
| acquiring | HTTP（tax 测试端点 + DTO Mocker 替代零税 Mock） | 建单暂估（AC-03）；资料提交重算与合同重渲染；暂估或过期计算支付被拒；NOT_MANAGED 正常支付（AC-04）；BLOCKED 不可支付（AC-05）；事件新字段；存量订单读与支付回调（AC-12） |
| billing | MQ | 单组件 / 多组件 `SELLER_TAX`、本金减税、NOT_MANAGED 无税行、旧载荷兼容（AC-09） |
| account / ledger | 现有入口 | CHANNEL → TAX_AUTHORITY 映射；两行合并一笔 TAX_PAYABLE（AC-09） |
| ops-bff | HTTP | 转发与转换、下载授权、权限 |
| ops-web | 组件测试 + i18n 守卫 | AC-13、AC-14 |

### 15.2 本机联调（阶段 5，另行授权）

本机 e2e 环境替换 tax / acquiring / billing / account / ops-bff JAR，`mor_tax` 重建与初始化，跑一笔 SA 演示订单：建单暂估 → 资料提交 → 支付 → tax 事实与税票 → billing / account / ledger → ops-web 页面（zh-CN / en × 1440 / 390）。tax 宕机时付款，验证 AC-10。

### 15.3 mor-test（阶段 6—7，另行授权）

发布顺序：Platform 注册 → tax（删表重建 + 初始化 + 新镜像 + compose 环境变量 + XXL）→ billing、account → acquiring（迁移 + 新镜像）→ ops-bff → ops-web。回滚按相反顺序；acquiring 回滚后事件不含税务字段，billing、tax 自然退回无税路径。

## 16. 实施顺序与工作量

1. tax 模型、Schema、PDManer、arch-rules（基础）。
2. tax 计算引擎与 `POST /calculations`（可独立验收 AC-01、02、04、05、06）。
3. tax 消费与登记、期间、补偿、税票。
4. billing / account 税行（与 3 并行）。
5. acquiring 接入、重算、门禁、事件字段（依赖 2）。
6. ops-bff 查询与下载；ops-web 待画板确认。
7. 文档：tax 数据库文档、CURRENT_IMPLEMENTATION、events.yaml、design-discuss 回写。

acquiring 是关键路径（三条主流程 + 合同重渲染 + 约 10 个测试文件改造）；tax 为新服务首次落地，基础设施工作量与 billing 首切片相当。

## 17. 阶段 3 待确认

| 编号 | 问题 | 建议 |
|---|---|---|
| D-01 | 演示数据中交易币种 USD、申报币种为本币，法定汇率源未接入，底稿将全部 `PENDING(FX_RATE_MISSING)` | 接受；本需求不做申报，汇率随申报需求接入。或把演示注册的申报币种临时设为 USD 让底稿 READY（不符合真实制度，不建议） |
| D-02 | 建单时 BLOCKED 是否允许建单成功？ | 允许：建单阶段的国家只是 Client 声明，消费者资料提交后可能变成 READY；支付门禁会阻断仍为 BLOCKED 的订单 |
| D-03 | `TaxTransactionRecorded` 本期无消费者，是否发布？ | 不发布（11.4）：无订阅方事件在 `unroutable-policy: FAIL` 下会发布失败并留存；有消费方时再加 |
| D-04 | 原型 13 张（第 13 节运营页 6 张、13.1 收银台 3 张与税票 4 张）是否接受 | 已画，随本阶段一并确认；ops-web 按运营页原型实现；收银台与税票版式暂存 operations-console.pen，迁入时机见 13.1 |
