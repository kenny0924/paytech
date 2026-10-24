# commercial：商业关系快照与真实解析接入 · 需求

> 状态：**阶段 1 评审稿**（2026-10-23）。确认记录见 [delivery.md](./delivery.md)，技术方案见 [technique.md](./technique.md)（阶段 2 起填写）。
> 依据：[2026-10-23 Platform 定位与商户对接模式决定](../../../agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md)（Proposed，D1—D13、第 9 节 U1 / U2 / U5 / U6、9.5 第 1 步、Q5 已定）。本需求是 [commercial 二期执行链路](../../../agent-docs/decisions/2026-10-20-commercial-capability-conditions-and-price-templates.md)的前置，二期（接受条件执行）另立需求。

## 1. 背景

- acquiring 建单时的商务事实（OE、合同、定价版本、税、文件）全部来自写死的 Mock（`AE001` / 合同 105 / 版本 104），从未调用 commercial 的 Checkout 解析；二期要执行的开通项接受条件因此没有来源。
- commercial 的解析在选 OE 前要做资格检查（Client 是否可交易、候选 OE 及其提供方是否合格），但 compliance 没有资格查询接口，commercial 接的是“永远不可用”的占位实现，mor-test 上直连解析恒返回 422。
- 2026-10-23 的决定把商户对接模式统一为属性驱动的“商业关系快照”（D13）：只在 commercial 推导，acquiring 冻结，下游只读角色绑定。本轮只实现 C1 / C2（Q5），但模型按五种模式一次设计。

## 2. 目标

1. acquiring 建单改为真实调用 commercial 解析，冻结解析结果，去掉商务 Mock；旧消费者依赖的字段继续填充，下游行为不变。
2. commercial 解析输出“商业关系快照”（角色绑定行 + `fund_flow` + 业务模式 + 开通项与接受条件 + 适用价目版本集合），只产出 C1 / C2 所需角色，其余组合拒绝。
3. 选 OE 时的资格检查有真实来源：compliance 提供按角色的只读资格查询；OE / Partner 资格可由运营手工登记。
4. 为五种模式打好字段骨架：Party 多角色、OE 收单资质三档、Partner 关联关系与角色、合同的出价方 / 对手方；取值本轮只允许 C1 / C2。

## 3. 概念

| 术语 | 含义 |
|---|---|
| 商业关系快照 | 一笔订单创建时由 commercial 推导、acquiring 冻结的角色绑定集合（SELLER、ACQUIRING_OE、OE_PROVIDER、FUNDS_CUSTODIAN、TAX_RESPONSIBLE、PLATFORM_FEE_PAYER、ACQUIRER_SUB_MERCHANT、REFERRER、SPLIT_PAYEE、PLATFORM）+ `fund_flow` + 模型版本 |
| 资格 | compliance 为（角色, 主体）维护的当前有效性（`t_role_qualification`），来源为尽调案件或运营手工登记 |
| 观察快照 / 生效模型 | 开关关闭时快照只记录不参与计算（模型版本 0 = 旧模型）；打开后新订单按版本 1 计算；订单创建时冻结实际生效的版本，开关不改既有订单 |

## 4. 本期范围

| 编号 | 内容 | 对应决定 |
|---|---|---|
| R1 | client-master：Party 多角色绑定；OE 收单资质三档；Partner 关联关系（AFFILIATED / EXTERNAL）与角色（OE_PROVIDER / REFERRER）；Client ↔ 推荐方关系表（本轮只建表） | U1、D6、D7、D2 |
| R2 | compliance：只读资格查询接口（角色, 主体 → 当前资格）；运营手工登记 OE / Partner 资格（`MANUAL` 来源）；Client 资格接现有入驻审核结果 | U2、Q6 |
| R3 | commercial：解析输出商业关系快照；资格检查改为对快照中所有需资格的（角色, 主体）逐个查询；组合约束与 MCC 兼容校验；解析请求支持不指定支付方式时返回全部 PAYMENT_METHOD 开通项；平台价目的 Partner 法律主体 = OE 法律主体校验 | U5、D5、D6、R19 |
| R4 | acquiring：真实 `CheckoutCommercialPort`（HTTP 客户端、错误映射）；冻结快照（角色绑定行）与 commercial 返回的 `effective_relationship_model_version`；旧字段继续填充；真实解析由 acquiring 开关控制，默认走 Mock 以免 mor-test 既有链路中断 | U6、R18、R20 |
| R5 | 运营控制台：资格手工登记页（compliance）与 OE 收单资质 / Partner 关联关系维护（client-master）；Platform 注册块 | U2、U1 |
| R6 | 迁移 / 数据：mor-test 测试 Client 的真实合同、OE 资质与资格种子；不迁移平台 OE（AE001 仍为 PLATFORM 来源，作为历史来源保留读取） | 9.5 第 1 步 |

## 5. 不做项

| 项 | 原因 |
|---|---|
| 平台 OE 迁到我们的 PayFac Partner | 9.5 第 2 步末尾，需先完成合同 / 计费 / 账务链路（R18） |
| 双边合同重构、价格组件角色化、billing / ledger 按角色记账（U3 / U4 / U9 / U10） | 第 2 步 |
| Payment 接收快照字段（`acquiring_role_of_client`、接受条件、3DS 要求）与授权前拦截 | 二期执行链路需求 |
| Client × 渠道子商户绑定与报送（U7） | 第 2 步（C2 启用前） |
| C3 / C4 / C5 的取值与能力 | 按 9.5 后置；本轮字段存在但拒绝 |
| 收银台前端 | 另立需求 |
| Tax / Document / ConsumerProtection / Customer 的 Mock | 本需求只替换商务 Mock；其余 Mock 另立 |

## 6. 业务规则（草案）

- B1 快照只在 commercial 推导；acquiring 不推导、不修改；下游不判断模式。
- B2 资格检查对象 = 快照中所有需资格的（角色, 主体）：Client、ACQUIRING_OE、OE_PROVIDER；任一不合格则该候选落空，Client 不合格则整单拒绝；查询不可用一律失败关闭（OWNER_UNAVAILABLE）。
- B3 组合约束在 commercial 校验：OE 收单资质 `MERCHANT` 不能承接 Client；`MERCHANT_RESALE_APPROVED` 只能 MoR；`PAYFAC` 可 MoR / PSP；Client 行业 / MCC 必须在 OE 获批 MCC 范围内；平台价目的 Partner 法律主体必须等于 OE 法律主体。
- B4 本轮快照只允许 `fund_flow=THROUGH_CUSTODIAN` 且 OE 来源为 PARTNER 或 PLATFORM（PLATFORM 作为历史来源，推导时视同“我们的 PayFac”的占位，开关关闭期间不改变任何计算）；其他组合拒绝。
- B5 开关语义（R20）：模型开关只有一个，在 commercial（`commercial.relationship-snapshot.enabled`，决定 9.5 第 1 步），决定解析响应里的 `relationship_model_version`（关闭 = 0 观察、打开 = 1 生效）；acquiring 只冻结返回值，不自行判断。订单创建时冻结实际生效的版本；开关只影响新订单；关闭不回切；开关关闭时快照仍冻结（版本 0 + 观察快照），下游按旧字段计算。
- B6 真实解析失败（无合格候选、Owner 不可用）时建单失败并返回明确错误；不回退到 Mock。
- B7 手工登记资格必须记录操作人、依据与有效期；撤销资格不改写历史快照，只影响之后的解析（R17）。

## 7. 逐系统变动点

> 阶段 1 列出变动点，阶段 2 核对代码后细化为差异 / 影响表。路径为当前代码位置。

### 7.1 client-master

| 变动点 | 内容 |
|---|---|
| 新表 `t_party_role` | Party × 角色（CLIENT / OPERATING_ENTITY / OE_PROVIDER / REFERRER / SPLIT_PAYEE）绑定行，含状态与生效时间；本轮由现有 Client / OE / Partner 主档回填 CLIENT、OPERATING_ENTITY、OE_PROVIDER 三种，REFERRER / SPLIT_PAYEE 只允许取值不写入 |
| 表 `t_operating_entity` | 新增 `acquiring_qualification`（MERCHANT / MERCHANT_RESALE_APPROVED / PAYFAC）、`approved_mcc_codes`（或子表）；默认值与迁移规则 |
| 表 `t_local_partner` | 新增 `affiliation`（AFFILIATED / EXTERNAL）、`partner_roles`（OE_PROVIDER / REFERRER，可多值 → 子表 `t_partner_role`） |
| 新表 `t_client_partner_relation` | Client ↔ 推荐方关系（本轮只建表，不写入） |
| 表 `t_entity_source_relation` | 来源 `PLATFORM` 保留为历史值；注释与校验改为“新建 OE 不允许 PLATFORM” |
| 内部 API | 候选查询（commercial 调用的 `ClientMasterCheckoutClient` 合同）增加 OE 收单资质、OE 法律主体 Party、OE 获批 MCC、Partner 关联关系与角色 |
| 运营 API | OE 收单资质与 MCC 维护、Partner 关联关系与角色维护（含审计） |
| PDManer / Schema / 增量 SQL / 测试 DDL | 同步 |
| 测试 | 候选查询返回新字段；资质维护 HTTP 入口 |

### 7.2 compliance

| 变动点 | 内容 |
|---|---|
| 新 API `QualificationQueryApi` | `GET /compliance/v1/qualifications?roleType=&roleId=`（或批量 POST）：返回当前资格状态、有效期、决定来源；只允许 `commercial-service` 调用 |
| 新 API 运营手工登记 | 为（角色, 主体）登记 / 撤销资格，来源 `MANUAL`，记录操作人与依据，写 `t_qualification_decision` + `t_role_qualification` + `t_qualification_state_change` |
| Client 资格 | 入驻审核通过（现有 `ClientReviewDecided`）时写入 `t_role_qualification(CLIENT, INDIVIDUAL_KYC / ORGANIZATION_KYB)`；核对现有审核流程是否已落该表，缺则补 |
| 角色词表 | `role_type` 确认含 CLIENT、OPERATING_ENTITY、LOCAL_PARTNER；RELATED_PERSON 不在本需求 |
| 测试 | 查询（有效 / 过期 / 撤销 / 未登记 → 明确结果）、手工登记入口、Client 审核通过后资格落表 |

### 7.3 commercial

| 变动点 | 内容 |
|---|---|
| `CheckoutQualificationClient` | 替换 `UnavailableCheckoutQualificationClient` 为 compliance HTTP 客户端；接口改为按（角色, 主体）查询 |
| `CheckoutResolutionProcessor` | 候选循环改为：推导快照 → 对快照中需资格的（角色, 主体）逐个查询 → 组合约束与 MCC 校验 → verify |
| 新领域对象 `RelationshipSnapshot` | 角色绑定行、`fund_flow`、模型版本；推导规则（9.2）只在此处 |
| 解析 API 合同 | 响应增加 `relationshipSnapshot`；请求 `paymentMethodCode` 可省略时返回全部 PAYMENT_METHOD 开通项（明确不影响 OE 选择） |
| 合同 / 价目校验 | 保存平台价目 / 激活平台合同时校验 Partner 法律主体 = OE 法律主体；`fund_mode` 改为推导值并校验一致 |
| 配置 | compliance 地址 / 发现、超时、失败关闭；模型开关 `commercial.relationship-snapshot.enabled`（默认关闭 → 快照 `relationship_model_version=0`；打开 → 1） |
| 测试 | 解析 HTTP 入口：资格有效 / 无效 / 不可用、资质三档、MCC 不兼容、快照内容、不指定支付方式 |

### 7.4 acquiring

| 变动点 | 内容 |
|---|---|
| 去 Mock | 删除 `CustomerCheckoutIntegrationConfiguration` 中的商务 Mock；新增 `CheckoutCommercialPortImpl`（commercial HTTP 客户端、Problem 映射、`X-Caller-Service=acquiring-service`） |
| 端口模型 | `InitializationRequest` 增加 locale（支付方式不传）；`CommercialFacts` 增加快照与开通项 |
| 表 | 新表 `t_order_relationship_binding`（订单 × 角色 → 主体类型 / 主体 ID / 法律主体）；`t_order_context` 增加 `fund_flow`、`effective_relationship_model_version`、`observed_snapshot_version` |
| `CreateCheckoutProcessor` | 事务外调用真实解析；事务内冻结快照行；旧字段（entity_no、定价版本等）仍由解析结果填充 |
| 开关 | 只有 `acquiring.commercial.resolution-mode = MOCK / REAL`；默认 MOCK，mor-test 种子就绪后切 REAL。模型版本不在 acquiring 判断，冻结 commercial 返回的 `relationship_model_version`（B5） |
| 建单校验 | 建单只收 USD 的白名单与合同币种的关系：保持白名单，解析命中由 commercial 判断 |
| PDManer / Schema / 增量 SQL / 测试 DDL | 同步 |
| 测试 | 建单经 commercial DTO Mock：成功冻结、无合格候选、Owner 不可用、开关两种取值、重放幂等 |

### 7.5 operations-console-bff / operations-console-web / platform-support（注册）

| 变动点 | 内容 |
|---|---|
| ops-bff | 转发：compliance 资格查询与手工登记；client-master OE 资质 / MCC、Partner 关联关系与角色维护 |
| ops-web | 页面：资格登记（按角色 / 主体查询、登记、撤销、历史）；OE 详情增加收单资质与 MCC；Partner 详情增加关联关系与角色 |
| platform-support | 注册块（权限、菜单、路由）；本轮申请新块号（开工前向其他会话登记） |
| 多语言 | ops-web 不得写中文字面量；新文案登记 `src/i18n/en/<ns>.json` 与 `zh-CN/<ns>.json`，发布时经 Platform“多语言管理”导入并发布（2026-10-23 运营控制台多语言迁移） |
| 测试 | BFF HTTP、ops-web 组件测试（含中文字面量守卫）、浏览器验收（英文默认 + 简体中文切换） |

### 7.6 不改代码但受影响的系统

| 系统 | 影响 |
|---|---|
| payment | 不改。快照字段的接收放在二期需求；本轮 acquiring 传给 Payment 的 Intent 字段不变 |
| billing / ledger | 开关关闭期间旧字段不变、无影响；切 REAL 后冻结的定价版本变为真实值（不再是 Mock 的 104），billing 价格命中改用真实版本——需与计费需求 B（code-mor-78）对齐 mor-test 种子 |
| risk-decision | 结账点评估不变 |
| tax / documents | 仍为 Mock；真实 OE 值进入后核对 Mock 的固定返回是否与之冲突 |
| client-console / Client API | 建单错误码新增“无合格主体 / 资格不可用”，文档同步 |

## 8. 验收标准

| 编号 | 验收 |
|---|---|
| AC-01 | 运营可为 OE 维护收单资质与获批 MCC、为 Partner 维护关联关系与角色；写入带审计 |
| AC-02 | 运营可为（角色, 主体）手工登记 / 撤销资格；Client 入驻审核通过后资格自动落表 |
| AC-03 | compliance 资格查询对有效 / 过期 / 撤销 / 未登记返回明确结果；调用方限制生效 |
| AC-04 | commercial 解析：Client 不合格整单拒绝；候选 OE 或其提供方不合格则落空并取下一候选；查询不可用 503 失败关闭 |
| AC-05 | 组合约束：MERCHANT 资质 OE 不被选中；MERCHANT_RESALE_APPROVED 只在 MoR 合同下被选中；Client MCC 不在 OE 范围内则落空 |
| AC-06 | 解析响应含快照（C1 / C2 角色绑定、`fund_flow=THROUGH_CUSTODIAN`、模型版本）；不指定支付方式时返回全部 PAYMENT_METHOD 开通项与接受条件 |
| AC-07 | acquiring 开关 REAL 时建单真实解析并冻结快照行；旧字段值与解析一致；MOCK 时行为与现状一致 |
| AC-08 | commercial 模型开关关闭时订单冻结版本 0、快照只记录；打开后新订单版本为 1；既有订单不变；acquiring 无第二个开关 |
| AC-09 | 解析失败（无合格候选 / 不可用）时建单返回明确错误，不落单 |
| AC-10 | 平台价目保存 / 合同激活时 Partner 法律主体 ≠ OE 法律主体被拒绝 |
| AC-11 | mor-test：测试 Client（如 DEMO-CL-001、CL-F1105E）在 REAL 模式下建单成功并冻结真实 OE / 合同 / 版本 |
| AC-12 | 运营页面功能与视觉验收（按阶段 3 原型） |

## 9. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | Client 资格的判定来源：只认入驻审核通过，还是还要结合结算阻断（`SETTLEMENT_BLOCK`） | 本轮：入驻审核通过 + 无 ACTIVE 结算阻断；阻断读 compliance 本库 |
| Q2 | 平台 OE（PLATFORM 来源）在开关关闭期间如何推导快照 | 视同“我们的 PayFac”占位：OE_PROVIDER 绑定到预留的关联 Partner 记录（本轮先创建该 Partner 主档，不迁 OE） |
| Q3 | OE 获批 MCC 的数据形态 | 子表多值，运营维护；空表示未登记 → 校验失败关闭 |
| Q4 | 资格查询是单个查询还是批量 | 批量 POST（一次解析一次往返），单个 GET 供运营页 |
| Q5 | 手工登记是否需要双人复核 | 本轮单人 + 审计；双人复核随工作流审批另立 |
| Q6 | mor-test 切 REAL 的时机与种子范围 | 阶段 6 决定；需先为测试 Client 建合同、OE 资质、资格 |

## 10. 建议适用阶段

阶段 1—8 全部适用：涉及 client-master、compliance、commercial、acquiring、ops-bff、ops-web、platform-support 注册；有 DDL 与注册 DML；要部署 mor-test。
