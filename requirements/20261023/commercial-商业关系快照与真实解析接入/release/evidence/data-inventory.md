# mor-test 数据盘点（阶段 6，只读）

> 2026-10-25，只读 SELECT 与 `mysqldump --single-transaction`，没有对 mor-test 做任何写入、登录或容器操作。
> 备份：scratchpad `backup/relsnap-stage6-mortest/`（6 个库，取于 2026-10-25T11:12:17Z，`SHA256SUMS` 同目录）：
> mor_compliance `1b5db9f0…`、mor_client_master `50869c4b…`、mor_commercial `cff9f4d8…`、mor_acquiring `fad93013…`、
> mor_risk_decision `8a56544c…`、mor_platform `dcb65c1b…`。MySQL 8.4.11（镜像 `sha256:b3b90af2…`），utf8mb4 / utf8mb4_0900_ai_ci，时区 +00:00。
> 全库扫描原始输出：[platform-value-scan.txt](./platform-value-scan.txt)；多语言比对：[i18n-diff-vs-mortest.txt](./i18n-diff-vs-mortest.txt)。

## 0. 结论

| # | 发现 | 影响 | 处理 |
|---|---|---|---|
| **F-1** | **mor-test client-master 没有任何 OE / Local Partner 主档**：`t_operating_entity`、`t_entity_source_relation`、`t_operating_entity_application`、`t_operating_entity_scope`、`t_local_partner`、`t_client_entity_affiliation`、`t_seller_disclosure`、`t_consumer_legal_document` 全为 0 行；BCR 只有 54 条 CLIENT 根；占位 Party 880000000701 / 880000000702 也不存在。AE001 只存在于 acquiring 固定 Mock 和 commercial 合同里 | 正式数据文件（22）在 mor-test 上执行后 **不会建立 PayFac Partner**（AE001 缺失时 ② 不插入，离线演练变体 A 已证实），切 REAL 必然失败；technique 3.4.1 “AE001 迁到 PayFac Partner” 的前提不成立 | 新增 [21-client-master-ae001-seed.sql](../sql/21-client-master-ae001-seed.sql)（**D-1，需用户批准**） |
| **F-2** | **GAP-PROFILE（既有缺陷）**：client-master 入驻审核通过只写 `t_party_profile.approved_time`，从不清空 `draft_application_id`、从不写 `t_party_profile.content_hash` / `t_client_business_profile.content_hash`（全仓 Mapper 无此写入）。mor-test 37 个已批准 Client 全部 draft 非空、两个 hash 为空 | client-master 候选查询判定分类无效 → 422 `CLIENT_NOT_ELIGIBLE`（technique F11 的真正原因）；越过后 commercial 签署校验也要求 draft 为空、hash 合法 → 409 `CCT_CONTEXT_AMBIGUOUS`。**切 REAL 后任何真实 Client 都无法建单** | AC-11 用 [23-client-master-ac11-profile-fix.sql](../sql/23-client-master-ac11-profile-fix.sql) 只补 DEMO-CL-001 一个 Client（**D-3**）；根治另立 client-master 需求 |
| F-3 | OE 来源 PLATFORM 只出现在 acquiring 46 条订单、risk 46 条输入快照与 1 条目录行；client-master、billing、ledger、reconciliation、account、payment、channel-connector、tax、vault 均无（含 JSON / 文本列） | 两个改写文件与目录行删除即覆盖全部；无规则 / 动作引用 PLATFORM | 41 / 50 按原文执行 |
| F-4 | 块 137 号段、编码、路径、菜单排序全部空闲；所有手工号段在 8 个库的 BIGINT 主键中无冲突 | — | 60 / 61 照原文 |
| F-5 | Platform 多语言当前发布 #7（8594 key、21 个命名空间）；worktree 比线上多 219 个 key、1 个英文基准变更、4 条 zh-CN 不同（3 条 `nav.sidebar.*` 是线上已改中文、仓库种子仍为英文） | 全量导入 zh-CN 会把 3 条侧栏中文冲回英文 | 见第 7 节，**D-5** |

## 1. OE 来源 PLATFORM（全库）

精确值扫描（所有 `mor_*` 库 varchar / char ≥ 8 的列 = `'PLATFORM'`）与文本 / JSON 列扫描（含 `entitySourceType` 等键后跟 PLATFORM 的正则）：

| 库.表.列 | 行数 | 是否 OE 来源 | 处理 |
|---|---|---|---|
| mor_acquiring.t_commerce_order.entity_source_type | 46（全部 AE001、local_partner_id 为空，2026-10-17—10-23） | 是 | 41：改 PARTNER + PayFac Partner 900000000001370001 |
| mor_risk_decision.t_checkout_input_snapshot.entity_source_type | 46（local_partner_id 为空） | 是；`stable_input_hash` 按 PLATFORM 计算，改写后不变（46 条受影响，订单已完成准备，不会重放） | 50：同上 |
| mor_risk_decision.t_risk_variable_member（900000000000092001，ENTITY_SOURCE_VALUES / PLATFORM） | 1 | 是（目录行） | 50：删除 |
| mor_commercial.t_agreement.pricing_authority | 9 | 否（定价权，取值保留） | — |
| mor_commercial.t_product / t_product_fee_event.product_code | 1 / 3 | 否（PLATFORM 基座产品） | — |
| mor_platform t_audit_event.resource_system、t_security_event.source_system、t_role.tenant_scope / scope_key、t_tenant_member_role.role_scope_key | 若干 | 否（Platform 系统 / 租户范围） | — |
| 文本列含 PLATFORM：commercial `t_business_change_version.snapshot_json`（123，PLATFORM_SIGNATORY / 定价权）、channel-connector 脚本与 trace、vault 策略文本 | — | 否（正则未命中来源键） | — |

- client-master 两张来源表 0 行（F-1）；billing / ledger / reconciliation / account 的 `source_type` 列为过账 / 流水来源，不含 PLATFORM。
- risk 规则：唯一引用 `entity_source_type` 的操作数（node 224089451389882381）比较 `= CLIENT`；AE001 由 PLATFORM 变 PARTNER 后结果不变（仍不等于 CLIENT）。`t_rule_operand` / `t_rule_action_parameter` 无 PLATFORM 字面量（50 的预检 1 为 0）。
- acquiring 新 Mock 发 AE001 + PARTNER（local_partner_id 107、分成 / 服务费版本 108 / 109），与改写行可区分（回滚 87 / 89 据此定位）。

## 2. AE001

| 项 | mor-test 现状 |
|---|---|
| client-master OE 行 / 来源关系 / 入驻申请 / 准入范围 | 均不存在（F-1） |
| 根 BCR | 不存在（client-master BCR 只有 CLIENT） |
| Party | client-master 无；commercial 9 份 AE001 Client 合同的 OPERATING_ENTITY 签约方都是占位 Party **880000000702 / 资料 881000000702**（最早签署 2026-10-20 02:50:52.326809） |
| 卖方披露 / 消费者法律文件（AE001 × US × en） | 不存在。存量 46 笔订单全是 US / en / USD，Client 分类全是 RETAIL / 5999 |
| ledger | `t_ledger_book` 有 OPERATING_ENTITY / AE001 / USD（OPEN）与 FINANCE_ENTITY PLATFORM_HQ / USD（OPEN） |
| payment | 按 entityNo 选 MID，AE001 不变（Mock 已是 AE001） |

21 种子沿用占位 Party 号（AE001 = 880000000702，平台 = 880000000701），使现有合同签约方与 ledger PLATFORM_HQ（legal_party_id 880000000701）无需改写；资料批准时间 2026-10-13（不晚于最早签署时间）；AE001 × US × USD × RETAIL / 5999 一条准入范围；三份 US / en 法律文件与一条卖方披露（**公开文本为占位，D-1 确认**）；给 7 个有 ACTIVE AE001 Client 合同的 Client 建 Affiliation；AE001 根 BCR V1（22 迁移后为 V2）。

## 3. 平台签约方 Party 880000000701

- client-master：**Party 与资料都不存在**（F-1）。commercial 解析要求合同每个签约方（含 PLATFORM_SIGNATORY）在 client-master 有已批准、无草稿、hash 合法、批准时间不晚于签署时间的资料，否则 409 `CCT_CONTEXT_AMBIGUOUS`（`CheckoutResolutionContext.validateSignature`）。
- commercial：DEMO-PT-001、DEMO2-PT-101 的 PLATFORM_SIGNATORY = 880000000701 / 881000000701；ledger PLATFORM_HQ 财务主体 legal_party_id = 880000000701。
- 21 种子建立 880000000701 + 已批准资料 881000000701（机构名占位 “Paytech Platform HQ Demo Ltd”，D-1）。

## 4. 测试 Client（AC-11 候选）与 compliance

| Client | commercial 合同 | Platform API 凭据 | 订单 | 备注 |
|---|---|---|---|---|
| **237575207592763393（DEMO-CL-001）** | 238331035912310784 ACTIVE，MOR，ACQUIRING + PAYOUT；CARD（VISA / MC / AMEX，USD / AED）、APPLE_PAY（USD / AED）开通；ACQ V1 ACTIVE（AE001 USD 含 CARD_STANDARD / APPLE_PAY_STANDARD 等 7 个画像） | 有（租户 237575207718567936） | 6 笔 US / en / USD | **推荐**：23 只补它的资料 |
| 237573267559391233（DEMO2-CL-103） | ACTIVE，CARD（USD / AED）、GOOGLE_PAY / STC_PAY（AED）；ACQ V1 ACTIVE（USD 4 个卡画像） | 有 | 0 | 备选 |
| 237610139979456513（CL-F1105E） | ACTIVE，CARD VISA USD / AED | 无 | 0 | 不能走 Client API |
| 其余 4 份 ACTIVE 合同（DEMO-CL-002、DEMO2-CL-101 / 102 / 901） | CARD 不含 USD 或 ACQ 版本未 ACTIVE | 部分有 | — | 不可用于 USD 建单 |

- **没有 PSP Client**：mor-test 全部 Client 合同都是 MOR。AC-11 的 C2 只能另建 PSP 合同（运营 API）或接受只验 C1 + 本地阶段 5 的 C2 证据（**D-6**）。
- compliance：37 个 Client 有 REVIEWER APPROVED 决定（`valid_to` 全为空），3 个 ORGANIZATION 只有 REJECTED；`t_role_qualification` / `t_qualification_state_change` / `t_settlement_control` 全空；无 OE / Partner 决定。11 回填后 37 个 Client 为 APPROVED / NOT_REQUIRED（含 DEMO-CL-001、DEMO2-CL-103）。AE001（ENTITY_UNDERWRITING）与 PayFac Partner（PARTNER_DUE_DILIGENCE）的资格在阶段 7 经新资格页手工登记（technique 3.4.2）。
- mor-test compliance 共 24 张表（technique 3.4.2 写“表数 25”与线上 / schema 文件不符，schema 文件同为 24，基线漂移 0）。

## 5. commercial：PayFac Partner 合同

- 现有 PARTNER 合同：DEMO-PT-001（LP 770000001001，PARTNER_SHARE + PLATFORM_SERVICE_CHARGE V1 ACTIVE，只有 CARD_STANDARD AED / USD 价格）、DEMO2-PT-101（LP 770000001002，只有 PARTNER_SHARE）。两者都没有 OPERATING_ENTITY 签约方，LP 在 client-master 不存在；新代码只为候选的来源 Partner 找合同，二者不影响 AE001 → PayFac 的解析。
- PayFac 合同需要（`CheckoutResolutionContext.partner` / `requireSigned`）：PARTNER 合同、`local_partner_id` = 900000000001370001、覆盖 AE001、`client_id` / `entity_no` 为空、LOCAL_PARTNER 与 OPERATING_ENTITY 签约方同为 AE001 Party（880000000702）、恰好一个 PLATFORM_SIGNATORY（880000000701）、恰好一份 SIGNING_DOCUMENT、所有签约方资料已批准且早于签署；PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 两个方案都有生效版本。
- **运营 API 路径可行**（首选，留 BCR 快照、事件、maker-checker）：`POST /commercial-admin/v1/agreements`（`CreateAgreementProcessor.checkParties` 允许 PARTNER 合同带 OPERATING_ENTITY 签约方，要求 LOCAL_PARTNER 在、CLIENT 不在；签署文件 ID 不回查文件服务，现有演示合同用合成 ID）→ 签署 → 激活（AC-10 Party 校验在合同内部比较，不调 client-master）；方案 / 版本 / 价格 / 批准 / 激活照 2026-10-20 演示种子流程（批准需第二个账号）。前提：21 种子先执行（资料存在，解析时才能通过）。
- 事件：`AgreementStatusChanged`、`CommercialTermVersionActivated` 的登记消费者只有 acquiring，且 acquiring 尚未接入 Handler；billing 走同步 HTTP 按版本取价，不消费这两个事件 → SQL 兜底（30）不会让 billing / ledger 漏数据，代价是无 BCR 快照（运营历史页为空）与无审计事件。
- 价目（**D-4**）：草稿每个方案只有 AE001 / USD / CARD_STANDARD 一条 0 费率价格。billing 在支付成功时按实际画像取 Partner 价格：APPLE_PAY 支付需 APPLE_PAY_STANDARD USD 价格，否则 PRICE_NOT_FOUND 进 DLQ（GAP-PARTNER）；CARD 仍受 GAP-ISSUER 限制。建议两方案各建 CARD_STANDARD 与 APPLE_PAY_STANDARD 的 USD 价格（费率由用户定，0 占位可接受时写明）。

## 6. 服务发现（Nacos）

- 启动日志：commercial-service（2026-10-22T05:37:49Z）、compliance-service（05:37:38Z）、client-master-service（2026-10-23T05:23:38Z）、acquiring-service、operations-console-bff 均 “nacos registry … register finished”（DEFAULT_GROUP）。ops-bff 已在用 `compliance-service`（入驻审核页）。
- 新代码的发现名：acquiring → `lb://commercial-service`；commercial → `client-master-service`、`compliance-service`；ops-bff → `client-master-service`、`compliance-service`。都已注册。
- **未核实**：Nacos 实时实例列表需要登录 Nacos（阶段 6 不登录），只以启动日志与 ops-bff 现有调用为据。新镜像替换后各服务会重新注册。

## 7. Platform

- 块 137：permission / role_permission / console_resource / gateway_route / route_header / query_param / guard_rule 在 900000000000137001—137999 均 0 行；6 个权限码、resource_type、6 个资源码、3 个前端路由、11 个路由码与外部路径均未占用；风控管理（565）下排序 90、商务（95301）下排序 60 / 70 空闲；Target 900000000000000001 ACTIVE；PLATFORM_ADMIN（510）版本 49；Gateway 路由 579 条、ACTIVE 574。现有最大 ID：permission 900000000000136102、route 900000000000136405、resource 900000000000136302。
- 多语言（operations-console）：默认 en、要求 zh-CN、当前发布 **#7**（READY，8594 key，2026-10-24T10:02:50Z）、21 个命名空间（`qualification`、`master-data` 不存在）、全部 key ACTIVE。线上 `nav.sidebar.product / hide / show` 的 zh-CN 为 “运营控制台 / 收起导航 / 展开导航”，仓库种子仍为英文（fc00257f 只改了线上）。
- worktree 对线上（[i18n-diff-vs-mortest.txt](./i18n-diff-vs-mortest.txt)）：新增 219 个 key（qualification 105、master-data 108、nav 6 个 `nav.resource.OPERATIONS_*`）；英文基准变更 1 个（`risk.legacyCatalog.fieldHelp.entity_source_type` 去掉 PLATFORM）；线上有而 worktree 没有 0 个；zh-CN 不同 4 个（3 条侧栏 + 上面那条 risk）。
- 建议导入方式（**D-5**，照文件上传入口 `fu_bundles.py` 只增不改）：先建 `qualification`、`master-data` 两个命名空间（sort 220 / 230）；KEY_IMPORT 上传 worktree 英文全集（预览应为新增 219、基准变更 1、废弃 0）；TRANSLATION_IMPORT 只含 219 个新 key + 那 1 条 risk key 的 zh-CN（不含 3 条侧栏，避免冲回英文）；核对已有翻译快照不变后发布 #8（219 < 3000，一批完成，不触发 Snowflake 上限）。另需把仓库 `i18n/zh-CN/nav.json` 的 3 条侧栏改成线上中文（单独提交，不在本发布包）。

## 8. 手工号段冲突

在 compliance、client-master、commercial、acquiring、risk-decision、platform、ledger、billing 的全部 BIGINT 主键中扫描：900000000001370000—900000000001379999（client-master 数据文件 / commercial 兜底 13710xx）、880000000000—881999999999（占位 Party / 资料）、900000000000137000—900000000000137999（块 137）均 **0 命中**。另核：`partner_no LIKE 'LP-PAYFAC%'`、`agreement_no / plan_code LIKE 'PAYFAC%'`、Party 编号、资料 881000000701 / 702 均未占用；21 种子用的 9000000000013810xx—13811xx 由其前置断言与 00-precheck 再核一次。

## 9. 风险与需用户决定

> 2026-10-25 用户决定：D-1、D-2（运营 API + 第二账号批准）、D-3、D-4（CARD_STANDARD 与 APPLE_PAY_STANDARD USD，费率 0）、D-7（REAL 与快照开关只在 AC-11 窗口打开）已确认；D-5、D-6 按建议执行。以 release.md 第 9 节为准。

| 编号 | 决定 | 建议 |
|---|---|---|
| **D-1** | 是否批准在 mor-test 执行 21 AE001 主档种子（合成法律资料、占位公开文本、7 个 Affiliation） | 批准；卖方披露公开文本（法定名称、注册号、地址、客服邮箱）由用户给定或接受占位 |
| **D-2** | PayFac Partner 合同走运营 API 还是 SQL 兜底（30） | 运营 API（阶段 7 主会话，需第二个账号批准方案版本） |
| **D-3** | 是否对 DEMO-CL-001 执行 23 资料定稿补丁（合成 hash、清空草稿指针） | 批准（否则 AC-11 不可验收）；另立 client-master 需求根治 GAP-PROFILE |
| **D-4** | Partner 分成 / 平台服务费价目 | CARD_STANDARD 与 APPLE_PAY_STANDARD 两个 USD 价格；费率由用户定（0 占位须写明） |
| **D-5** | 多语言导入方式 | 第 7 节的只增不改；仓库侧栏中文另行修正 |
| **D-6** | AC-11 的 C2 | mor-test 无 PSP Client：只验 C1，C2 以阶段 5 本地证据为准；或另建 PSP 合同 |
| **R-3** | acquiring 的 REAL 开关是全局的 | 切 REAL 后没有 Affiliation / 合同 / 资料定稿的 Client（mor-test 54 个中除 DEMO-CL-001 外全部）建单都会失败（CLIENT_CONFIGURATION_NOT_READY / CHECKOUT_NOT_ELIGIBLE），近期建过单的其他 Client（如 239260386824658945 的 7 笔、239130487124238337 的 6 笔，都没有 commercial 合同）及其他会话的 e2e 会受影响。建议 REAL 只在 AC-11 窗口打开、验收后切回 MOCK，或事先通知其他会话 |
| R-4 | GAP-PROFILE 是 client-master 既有缺陷 | 另立需求：审核通过时定稿资料（清草稿指针、计算两个 hash） |
| R-5 | billing PARTNER 口径 | 切 REAL 起 AE001 订单按 PARTNER 计费；ledger 两本账簿已存在；GAP-ISSUER（CARD）未解决前 CARD 支付计费可能 422，验收建议用 APPLE_PAY |
| R-6 | 41 / 50 无跨库校验 | 只能在 22 之后、且 99-postcheck 的 `cm.payfac_partner_party_eq_ae001 = 1` 时执行，否则订单 / 快照指向不存在的 Partner |

## 10. 阶段 7 分工

| 经运营 API / 页面 | 经 SQL（apply_sql.sh，主会话 root） |
|---|---|
| PayFac Partner 合同与两个方案（D-2 首选）；资格手工登记：AE001 ENTITY_UNDERWRITING、PayFac Partner PARTNER_DUE_DILIGENCE（DEMO-CL-001 由 11 回填）；多语言命名空间、导入、发布 #8；（可选）PSP 合同（D-6） | 10 / 11 compliance；20 / 21 / 22 / 23 client-master；40 / 41 acquiring；50 risk-decision；60 块 137 注册、61 激活；（D-2 选兜底时）30 |
