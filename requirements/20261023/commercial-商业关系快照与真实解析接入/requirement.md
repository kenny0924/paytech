# commercial：商业关系快照与真实解析接入 · 需求

> 状态：**阶段 1、2 已确认**（2026-10-24：Q1—Q6 按建议执行；阶段 2 待决 S-01—S-16 按建议执行，第 4、6、7、8、9 节已按其修订）。**阶段 3 评审改动（2026-10-24 用户决定）**：OE 统一用 entity_no；取消“OE 来源 PLATFORM”概念，AE001 本轮迁到我们的 PayFac Partner 名下（PARTNER 来源），不保留历史；Partner `affiliation` 本轮不做。确认记录见 [delivery.md](./delivery.md)，差异与技术方案见 [technique.md](./technique.md)。
> 依据：[2026-10-23 Platform 定位与商户对接模式决定](../../../agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md)（Proposed，D1—D13、第 9 节 U1 / U2 / U5 / U6、9.5 第 1 步、Q5 已定）。本需求是 [commercial 二期执行链路](../../../agent-docs/decisions/2026-10-20-commercial-capability-conditions-and-price-templates.md)的前置，二期（接受条件执行）另立需求。

## 1. 背景

- acquiring 建单时的商务事实（OE、合同、定价版本、税、文件）全部来自写死的 Mock（`AE001` / 合同 105 / 版本 104），从未调用 commercial 的 Checkout 解析；二期要执行的开通项接受条件因此没有来源。
- commercial 的解析在选 OE 前要做资格检查（Client 是否可交易、候选 OE 及其提供方是否合格），但 compliance 没有资格查询接口，commercial 接的是“永远不可用”的占位实现：解析走到资格检查时恒返回 503。mor-test 上直连解析返回的 422 `CLIENT_NOT_ELIGIBLE` 更早发生在 client-master 候选阶段（测试 Client 未就绪），见技术方案 2.1 F11。
- 2026-10-23 的决定把商户对接模式统一为属性驱动的“商业关系快照”（D13）：只在 commercial 推导，acquiring 冻结，下游只读角色绑定。本轮只实现 C1 / C2（Q5），但模型按五种模式一次设计。

## 2. 目标

1. acquiring 建单改为真实调用 commercial 解析，冻结解析结果，去掉商务 Mock；旧消费者依赖的字段继续填充，下游行为不变。
2. commercial 解析输出“商业关系快照”（角色绑定行 + `fund_flow` + 业务模式 + 开通项与接受条件 + 适用价目版本集合），只产出 C1 / C2 所需角色，其余组合拒绝。
3. 选 OE 时的资格检查有真实来源：compliance 提供按角色的只读资格查询；OE / Partner 资格可由运营手工登记。
4. 为五种模式打好字段骨架：Party 多角色、OE 收单资质三档、Partner 角色、合同的出价方 / 对手方；取值本轮只允许 C1 / C2。
5. 取消“平台商户 / OE 来源 PLATFORM”概念：全系统 OE 来源只剩 CLIENT / PARTNER；平台自有 OE（AE001）迁到我们的 PayFac Partner 名下。

## 3. 概念

| 术语 | 含义 |
|---|---|
| 商业关系快照 | 一笔订单创建时由 commercial 推导、acquiring 冻结的角色绑定集合（SELLER、ACQUIRING_OE、OE_PROVIDER、FUNDS_CUSTODIAN、TAX_RESPONSIBLE、PLATFORM_FEE_PAYER、ACQUIRER_SUB_MERCHANT、REFERRER、SPLIT_PAYEE、PLATFORM）+ `fund_flow` + 模型版本 |
| 资格 | compliance 为（角色, 主体）维护的当前有效性（`t_role_qualification`），来源为尽调案件或运营手工登记 |
| 观察快照 / 生效模型 | 开关关闭时快照只记录不参与计算（模型版本 0 = 旧模型）；打开后新订单按版本 1 计算；订单创建时冻结实际生效的版本，开关不改既有订单 |

## 4. 本期范围

| 编号 | 内容 | 对应决定 |
|---|---|---|
| R1 | client-master：Party 多角色绑定；OE 收单资质三档；Partner 角色（OE_PROVIDER / REFERRER）；删除 OE 来源 PLATFORM；Client ↔ 推荐方关系表（本轮只建表）；候选响应带回卖方公开文本与 OE 提供方；OE / Partner 运营 Admin API（只读主档 + 维护，不新建 OE / Partner） | U1、D6、D7、D2 |
| R2 | compliance：只读资格查询接口（角色, 主体 → 当前资格）；运营手工登记 OE / Partner 资格（`MANUAL` 来源）；Client 资格接现有入驻审核结果 | U2、Q6 |
| R3 | commercial：解析输出商业关系快照；资格检查改为对快照中所有需资格的（角色, 主体）逐个查询；组合约束与 MCC 兼容校验；解析请求支持不指定支付方式时返回全部 PAYMENT_METHOD 开通项；平台价目的 Partner 法律主体 = OE 法律主体校验 | U5、D5、D6、R19 |
| R4 | acquiring：真实 `CheckoutCommercialPort`（HTTP 客户端、错误映射到 Client API 现有错误码）；请求带订单冻结的 locale；冻结快照（角色绑定行）、卖方公开文本与 commercial 返回的 `effective_relationship_model_version`；旧字段继续填充；真实解析由 acquiring 开关控制，默认走 Mock 以免 mor-test 既有链路中断 | U6、R18、R20 |
| R5 | 运营控制台：资格手工登记页（compliance）；新建 OE 列表 / 详情（收单资质维护、准入范围只读）与 Partner 列表 / 详情（角色维护）（client-master）；Platform 注册块 137 | U2、U1 |
| R6 | 迁移 / 数据：mor-test 新建“我们的 PayFac”Partner（法律主体 = AE001 的法律主体），AE001 来源由 PLATFORM 改为 PARTNER 指向它，并为其建 Partner 合同（平台签约方、Partner 分成与平台服务费方案，价目先按 0 占位，阶段 6 定）；acquiring / risk 存量 PLATFORM 取值直接改写，不保留历史；测试 Client 的合同、OE 资质与资格种子；已通过入驻审核的 Client 资格回填 | 9.5 第 1 步 + D2 迁移提前（2026-10-24 用户决定） |
| R7 | 删除 OE 来源 PLATFORM 的代码、校验、注释与文档：client-master、commercial、acquiring、risk-decision、AGENTS.md / 决定草案 / 数据库文档 | 2026-10-24 用户决定 |

## 5. 不做项

| 项 | 原因 |
|---|---|
| 双边合同下的 Platform → PayFac 批发价目、按角色计费 | 第 2 步（U3 / U4 / U9）；本轮 AE001 迁到 PayFac Partner 后仍沿用现有 Partner 分成 / 平台服务费口径（风险见 7.7） |
| Partner 关联关系 `affiliation`（AFFILIATED / EXTERNAL） | 本轮无读取方；随“代操作 / 外部 PayFac 自操作”需求再加（2026-10-24 用户决定） |
| 双边合同重构、价格组件角色化、billing / ledger 按角色记账（U3 / U4 / U9 / U10） | 第 2 步 |
| Payment 接收快照字段（`acquiring_role_of_client`、接受条件、3DS 要求）与授权前拦截 | 二期执行链路需求 |
| Client × 渠道子商户绑定与报送（U7） | 第 2 步（C2 启用前） |
| C3 / C4 / C5 的取值与能力 | 按 9.5 后置；本轮字段存在但拒绝 |
| 收银台前端 | 另立需求 |
| Tax / Document / ConsumerProtection / Customer 的 Mock | 本需求只替换商务 Mock；其余 Mock 另立 |

## 6. 业务规则（草案）

- B1 快照只在 commercial 推导；acquiring 不推导、不修改；下游不判断模式。
- B2 资格检查对象 = 快照中所有需资格的（角色, 主体）：Client、ACQUIRING_OE、OE_PROVIDER。Client 资格在取候选之前检查，不合格整单拒绝（`CLIENT_NOT_ELIGIBLE`）；候选的 OE 或提供方不合格则该候选落空、取下一候选，全部落空报 `NO_ELIGIBLE_ENTITY`；查询不可用一律失败关闭（OWNER_UNAVAILABLE）。不新增 commercial 错误码。
- B3 组合约束：OE 收单资质 `MERCHANT` 不能承接 Client；`MERCHANT_RESALE_APPROVED` 只能 MoR；`PAYFAC` 可 MoR / PSP；平台价目的 Partner 法律主体必须等于 OE 法律主体——以上在 commercial 解析时校验，不符的候选落空。Client 行业 / MCC 必须在 OE 获批范围内——沿用 client-master 准入范围 `t_operating_entity_scope`（国家 × 币种 × 行业 × MCC 精确批准）的候选过滤，不另建 MCC 子表。“平台价目”本轮只指 `PLATFORM_SERVICE_CHARGE`。
- B4 本轮快照只允许 `fund_flow=THROUGH_CUSTODIAN` 且 OE 来源为 PARTNER；CLIENT 来源（C3）拒绝。OE 来源取值只剩 CLIENT / PARTNER。
- B5 开关语义（R20）：模型开关只有一个，在 commercial（`commercial.relationship-snapshot.enabled`，决定 9.5 第 1 步），决定解析响应里的 `relationship_model_version`（关闭 = 0 观察、打开 = 1 生效）；acquiring 只冻结返回值，不自行判断。订单创建时冻结实际生效的版本；开关只影响新订单；关闭不回切；开关关闭时快照仍冻结（版本 0 + 观察快照），下游按旧字段计算。
- B6 真实解析失败（无合格候选、Owner 不可用）时建单失败并返回明确错误；不回退到 Mock。
- B7 手工登记资格必须记录操作人、依据与有效期；撤销资格不改写历史快照，只影响之后的解析（R17）。过期按有效期在查询时实时判定，不设定时任务。

## 7. 逐系统变动点

> 阶段 1 列出变动点，阶段 2 核对代码后按 S-01—S-16 修订（2026-10-24 确认）；差异证据见 [technique.md](./technique.md) 第 2 节。

### 7.1 client-master

| 变动点 | 内容 |
|---|---|
| 新表 `t_party_role` | Party × 角色（CLIENT / OPERATING_ENTITY / OE_PROVIDER / REFERRER / SPLIT_PAYEE）绑定行，含状态与生效时间；本轮由现有 Client / OE / Partner 主档回填 CLIENT、OPERATING_ENTITY、OE_PROVIDER 三种，REFERRER / SPLIT_PAYEE 只允许取值不写入；登记表归属规则 |
| 表 `t_operating_entity` | 新增 `acquiring_qualification`（MERCHANT / MERCHANT_RESALE_APPROVED / PAYFAC，存量回填）。获批 MCC 沿用 `t_operating_entity_scope`，不新建子表（S-03） |
| 表 `t_local_partner` | 结构不变；角色多值 → 新子表 `t_partner_role`（OE_PROVIDER / REFERRER）；`affiliation` 本轮不做 |
| 新表 `t_client_partner_relation` | Client ↔ 推荐方关系（本轮只建表，不写入） |
| 表 `t_entity_source_relation` / `t_operating_entity_application` | `source_type` 删除 PLATFORM 取值（注释、PDManer、枚举 `EntitySourceRelationSourceType`）；候选查询删除 PLATFORM 分支；存量 AE001 改为 PARTNER（R6） |
| 审计 | 新表 / 新列作为 OE / LP 根的子行，写入推进根 BCR 版本，verify 自然感知（S-12） |
| 候选 API | 响应增加：`acquiringQualification`；独立的 `oeProvider`（Partner ID、Party、status、roles；取 PARTNER 来源 Source 行的 LP）；卖方公开文本（法定名称、注册号、地址、客服邮箱等，S-01）。新字段只在 commercial 带新 api jar 上线后发布（严格反序列化） |
| 运营 Admin API（新） | OE 列表 / 详情（主档、准入范围只读）与收单资质维护；Partner 列表 / 详情与角色维护；Gateway Actor 头，写入带审计；不提供 OE / Partner 新建（S-04） |
| 种子 / 迁移 | 新建“我们的 PayFac”Partner：法律主体 = AE001 的 Party，ACTIVE，角色 OE_PROVIDER；AE001 来源关系由 PLATFORM 直接改为 PARTNER 指向它（不保留历史） |
| PDManer / Schema / 增量 SQL / 测试 DDL | 同步 |
| 测试 | 候选查询返回新字段（PARTNER 来源 OE + LP 夹具、卖方公开文本；PLATFORM 夹具改为 PARTNER）；Admin API HTTP 入口 |

### 7.2 compliance

| 变动点 | 内容 |
|---|---|
| Schema | `t_qualification_decision.case_id` 改可空；`decision_source` 增加 `MANUAL`；新增 `basis_note VARCHAR(1000)`；依据文件本轮不做（S-05）。`t_role_qualification` / `t_qualification_state_change` 补 DataObject / Mapper / Repository |
| 角色映射 | 快照角色 → compliance（role_type, qualification_type）：CLIENT → CLIENT + INDIVIDUAL_KYC / ORGANIZATION_KYB；ACQUIRING_OE → OPERATING_ENTITY + ENTITY_UNDERWRITING；OE_PROVIDER → LOCAL_PARTNER + PARTNER_DUE_DILIGENCE；OE 的主体 ID 统一用 entity_no（如 AE001，2026-10-24 用户决定，S-07 修订），compliance 资格表 `role_id` 改为字符型；RELATED_PERSON 不在本需求 |
| 新 API 资格查询 | 批量 `POST`（commercial-service 调用，一次解析一次往返）+ 单查 / 历史（运营）；判定 APPROVED 且在有效期内，过期在查询时实时判定（S-08）；CLIENT 另需无 ACTIVE 结算阻断（读本库 `t_settlement_control`，现为空）；调用方在 Controller 硬编码校验 |
| 新 API 运营手工登记 / 撤销 | 来源 `MANUAL`，记录操作人、依据与有效期；同一事务写 decision → upsert role_qualification（version CAS + 唯一键）→ state_change；不用 Redisson 锁（compliance 禁 Redis，S-05 / F8）；主体存在性由 ops-bff 先查 client-master |
| Client 资格 | `ClientReviewServiceImpl.decide()` 审核通过时同一事务投影 CLIENT 资格；mor-test 已通过的 Client 用一次性增量 SQL 回填（ID 由 decision_id 派生）；未经入驻审核的演示 Client 走手工登记（S-09） |
| 测试 | 查询（有效 / 过期 / 撤销 / 未登记 / 结算阻断 → 明确结果、调用方限制）、手工登记与撤销入口、Client 审核通过后资格落表 |

### 7.3 commercial

| 变动点 | 内容 |
|---|---|
| `CheckoutQualificationClient` | 替换 `UnavailableCheckoutQualificationClient` 为 compliance HTTP 客户端（`commercial-core` 依赖 `paytech-compliance-api`）；接口改为批量（角色, 主体）查询；需区分的 4xx 登记为合同错误 |
| `CheckoutResolutionProcessor` | 顺序改为：Client 资格（不合格整单拒绝）→ client-master 候选 → 逐候选推导快照 → 批量查询 OE / 提供方资格 → 组合约束（资质三档、Partner Party = OE Party）→ verify；不符的候选落空并记日志（S-10） |
| 新领域对象 `RelationshipSnapshot` | 角色绑定行、`fund_flow`、模型版本；推导规则（决定 9.2）只在此处；资格结果与模型版本不进入候选 equals（S-13） |
| 解析 API 合同 | 响应增加 `relationshipSnapshot` 与卖方公开文本（透传 client-master，S-01）；请求 locale 由 acquiring 传入（S-02）；`paymentMethodCode` 省略时返回全部 PAYMENT_METHOD 开通项与接受条件，仍按 ENABLED、时间窗、CURRENCY / COUNTRY、有生效价格过滤，为空不落空，不影响 OE 选择 |
| 合同 / 价目写入校验 | 保存平台价目 / 激活合同时做近似校验：合同内 OPERATING_ENTITY 角色行 Party = LOCAL_PARTNER Party；精确校验在解析时（S-14）；“平台价目”只指 `PLATFORM_SERVICE_CHARGE`，`PLATFORM_FEE` 留给 U3 |
| 删除 PLATFORM 来源 | 解析响应 `sourceType` 只剩 CLIENT / PARTNER；注释、视图、测试夹具同步；快照推导只支持 PARTNER |
| 配置 | compliance 地址 / 发现、超时、失败关闭；模型开关 `commercial.relationship-snapshot.enabled`（默认关闭 → `relationship_model_version=0`；打开 → 1） |
| 测试 | 新增 compliance DTO Mock 端点；解析 HTTP 入口：成功路径、Client 资格不合格 / 不可用、候选资格落空、资质三档、Party 不符、快照内容、卖方公开文本、不指定支付方式 |

### 7.4 acquiring

| 变动点 | 内容 |
|---|---|
| 去 Mock | 只把商务 Mock 从 `CustomerCheckoutIntegrationConfiguration` 拆到独立配置，按 `acquiring.commercial.resolution-mode`（MOCK 默认 / REAL）互斥；新增 `CheckoutCommercialPortImpl`（`lb://commercial-service`，`X-Caller-Service=acquiring-service`）；Tax / Document / ConsumerProtection / Payment 查询 Mock 不动 |
| 端口模型 | `InitializationRequest` 增加 locale（取订单冻结的 locale，现为 `en`，S-02；支付方式不传）；`CommercialFacts` 增加快照、开通项、业务模式与卖方公开文本 |
| 表 | 新表 `t_order_relationship_binding`（订单 × 角色 → 主体类型 / 主体 ID / 法律主体）；`t_order_context` 增加 `fund_flow`、`effective_relationship_model_version`、`snapshot_model_version`（阶段 3 由 `observed_snapshot_version` 改名） |
| 删除 PLATFORM 来源 | 枚举 `EntitySourceType`、`validateCommercial` 取值集合、`t_commerce_order.entity_source_type` 注释删除 PLATFORM；商务 Mock 改为 AE001 + PARTNER（带 Partner 合同字段）；mor-test 存量订单 `entity_source_type` 由 PLATFORM 改写为 PARTNER |
| `CreateCheckoutProcessor` | 事务外调用真实解析；事务内冻结快照行；旧字段（entity_no、定价版本、`t_order_legal.public_*` 等）由解析结果填充 |
| 开关 | 只有 `acquiring.commercial.resolution-mode = MOCK / REAL`；默认 MOCK，mor-test 种子就绪后切 REAL。模型版本不在 acquiring 判断，冻结 commercial 返回的 `relationship_model_version`（B5） |
| 错误映射 | 复用 Client API 现有错误码（S-11）：NO_ELIGIBLE_ENTITY / AMBIGUOUS → `CLIENT_CONFIGURATION_NOT_READY`；CLIENT_NOT_ELIGIBLE → `CHECKOUT_NOT_ELIGIBLE`；OWNER_UNAVAILABLE / 5xx → `DEPENDENCY_UNAVAILABLE`；超时 → `DEPENDENCY_TIMEOUT`；CHANGED → 重试一次后 `DEPENDENCY_UNAVAILABLE`；404 / INVALID / 字段格式不符 / 未来时间 422 → `UPSTREAM_PROTOCOL_ERROR` |
| 建单校验 | 建单只收 USD 的白名单保持；解析命中由 commercial 判断 |
| PDManer / Schema / 增量 SQL / 测试 DDL | 同步 |
| 测试 | 测试替身 `CheckoutCommercialMocker`（`@Primary` 直接实现端口）改为 HTTP DTO Mock 端点 `MockCheckoutContextApi`，13 个测试类迁移（S-16）；建单：成功冻结、无合格候选、Client 不合格、Owner 不可用 / 超时、CHANGED 重试、两种开关取值、重放幂等 |

### 7.5 operations-console-bff / operations-console-web / platform-support（注册）

| 变动点 | 内容 |
|---|---|
| ops-bff | compliance 客户端：资格单查、历史、登记、撤销；client-master 客户端：OE / Partner 列表、详情、收单资质维护、角色维护；登记前先查 client-master 校验主体存在 |
| ops-web | 新页面（S-04）：资格登记（按角色 / 主体查询、登记、撤销、历史）；OE 列表 / 详情（主档、准入范围只读、收单资质维护、提供方、资格摘要）；Partner 列表 / 详情（主档、角色维护、提供的 OE、资格摘要）；不做 OE / Partner 新建 |
| platform-support | 注册块 137（权限、菜单、路由；默认休眠 + 激活 / 回滚脚本；2026-10-24 已登记，块 136 已被文件上传入口占用） |
| 多语言 | ops-web 不得写中文字面量；新文案登记 `src/i18n/en/<ns>.json` 与中文种子 `i18n/zh-CN/<ns>.json`，新命名空间登记 `src/i18n/namespaces.ts`，菜单需 `nav.resource.<编码>`；发布时经 Platform“多语言管理”导入并发布 |
| 测试 | BFF HTTP、ops-web 组件测试（含中文字面量守卫）、浏览器验收（英文默认 + 简体中文切换） |

### 7.6 risk-decision

| 变动点 | 内容 |
|---|---|
| 删除 PLATFORM 来源 | 结账输入校验 `CheckoutPrepareEvaluator`、规则校验 `RuleAstValidatorImpl` / `DecisionReleaseStaticValidatorImpl` 的 `entity_source_type` 取值集合、API / DO 注释、schema 注释删除 PLATFORM；初始数据目录行 `ENTITY_SOURCE_VALUES / PLATFORM` 删除；mor-test 先核对是否有已发布规则 / 输入快照引用 PLATFORM，有则改写 |
| 测试 | 输入校验与规则校验拒绝 PLATFORM |

### 7.7 不改代码但受影响的系统

| 系统 | 影响 |
|---|---|
| payment | 不改。切 REAL 后 entityNo 为真实值，Payment 按 entityNo 选 MID；无 MID 的 OE 会无可用渠道。快照字段的接收放在二期需求 |
| billing / ledger | **AE001 迁到 PayFac Partner 后（切 REAL 起），AE001 订单按 PARTNER 来源计费：billing 计算 Partner 分成与平台服务费，ledger 在 AE001 账本与平台主体账本之间记服务费；“快照只观察、下游不变”的保护对 AE001 不再成立（用户 2026-10-24 接受）**。GAP-PARTNER 是 AC-11 的硬前置；价目先按 0 占位。其余：开关关闭期间旧字段不变；切 REAL 后冻结的定价版本变为真实值（Mock 104 / 105 在 commercial 404 进 DLQ 的问题随之消失），billing-b 记录的 GAP-ISSUER / GAP-PARTNER 是 AC-11 前置，需与 code-mor-78 对齐 mor-test 种子；真实 Partner OE 还需账簿、清算账户与成本方案（第 2 步） |
| risk-decision（运行影响） | 结账评估输入的 entityNo / source / LP / 行业 / MCC 变真实值（AE001 来源变为 PARTNER）；是否有按 OE / 来源的规则在阶段 6 核对 |
| tax / documents | 仍为 Mock，固定返回与真实 OE 不冲突 |
| client-console / Client API | 不新增错误码；OpenAPI 补充 `CLIENT_CONFIGURATION_NOT_READY` / `CHECKOUT_NOT_ELIGIBLE` / 依赖类错误在真实解析下的含义 |

## 8. 验收标准

| 编号 | 验收 |
|---|---|
| AC-01 | 运营可在 OE 列表 / 详情查看主档与准入范围（含 MCC）并维护收单资质；在 Partner 列表 / 详情维护角色；写入带审计并推进根版本 |
| AC-02 | 运营可为（角色, 主体）手工登记 / 撤销资格，记录操作人、依据、有效期；Client 入驻审核通过后资格自动落表；mor-test 已通过的 Client 已回填 |
| AC-03 | compliance 资格查询对有效 / 过期 / 撤销 / 未登记 / 结算阻断返回明确结果；调用方限制生效 |
| AC-04 | commercial 解析：Client 不合格整单拒绝（先于候选）；候选 OE 或其提供方不合格则落空并取下一候选；全部落空报 NO_ELIGIBLE_ENTITY；查询不可用 503 失败关闭 |
| AC-05 | 组合约束：MERCHANT 资质 OE 不被选中；MERCHANT_RESALE_APPROVED 只在 MoR 合同下被选中；Partner Party ≠ OE Party 的候选落空；Client MCC 不在 OE 准入范围内则不成为候选 |
| AC-06 | 解析响应含快照（C1 / C2 角色绑定、`fund_flow=THROUGH_CUSTODIAN`、模型版本）与卖方公开文本；不指定支付方式时返回全部 PAYMENT_METHOD 开通项与接受条件 |
| AC-07 | acquiring 开关 REAL 时建单真实解析并冻结快照行；旧字段值（含卖方公开文本）与解析一致；MOCK 时行为与现状一致 |
| AC-08 | commercial 模型开关关闭时订单冻结版本 0、快照只记录；打开后新订单版本为 1；既有订单不变；acquiring 无第二个开关 |
| AC-09 | 解析失败时建单按 7.4 映射返回 Client API 现有错误码，不落单 |
| AC-10 | 解析时 Partner 法律主体 ≠ OE 法律主体的候选落空；保存平台价目 / 激活合同时，合同内 OPERATING_ENTITY 角色行 Party ≠ LOCAL_PARTNER Party 被拒绝 |
| AC-11 | mor-test：AE001（PARTNER 来源，我们的 PayFac Partner）下的 USD 测试 Client 在 REAL 模式下建单成功并冻结真实 OE / 合同 / Partner 合同 / 版本与快照（MoR Client 验 C1；有 PSP Client 时验 C2，否则直连解析验证 C2 快照） |
| AC-13 | 全系统不再出现 OE 来源 PLATFORM：client-master / commercial / acquiring / risk-decision 的枚举、校验与注释已删除；mor-test 存量 PLATFORM 取值已改写；文档同步 |
| AC-12 | 运营页面功能与视觉验收（按阶段 3 原型） |

## 9. 已确认问题（2026-10-24 用户按建议确认）

| 编号 | 问题 | 结论 |
|---|---|---|
| Q1 | Client 资格的判定来源：只认入驻审核通过，还是还要结合结算阻断（`SETTLEMENT_BLOCK`） | 本轮：入驻审核通过 + 无 ACTIVE 结算阻断；阻断读 compliance 本库 |
| Q2 | 平台 OE（PLATFORM 来源）在开关关闭期间如何推导快照 | 视同“我们的 PayFac”占位：OE_PROVIDER 绑定到预留的关联 Partner 记录（本轮先创建该 Partner 主档，不迁 OE）。~~阶段 2 细化（S-06）：占位 Partner + `provider_partner_id`~~ 阶段 3 评审改为（2026-10-24 用户决定）：取消 PLATFORM 来源，AE001 直接迁到我们的 PayFac Partner 名下，不需要占位与特殊字段 |
| Q3 | OE 获批 MCC 的数据形态 | ~~子表多值~~ 阶段 2 改为（S-03）：沿用 `t_operating_entity_scope` 的 MCC 维度，不新建子表；MCC 兼容由 client-master 候选过滤保证 |
| Q4 | 资格查询是单个查询还是批量 | 批量 POST（一次解析一次往返），单个 GET 供运营页 |
| Q5 | 手工登记是否需要双人复核 | 本轮单人 + 审计；双人复核随工作流审批另立 |
| Q6 | mor-test 切 REAL 的时机与种子范围 | 阶段 6 决定；需先为测试 Client 建合同、OE 资质、资格，并完成 client-master 的 Client 审核与分类。验收范围见 AC-11（S-15） |

## 10. 建议适用阶段

阶段 1—8 全部适用：涉及 client-master、compliance、commercial、acquiring、ops-bff、ops-web、platform-support 注册；有 DDL 与注册 DML；要部署 mor-test。
