# Client 激活全流程：从入驻申请到生产交易验证

> 状态：**讨论中（2026-10-28）**。用户已定：Q1 Client 状态只表示合作关系是否有效，可收款 / 可出款由 commercial 合同开通项数据控制；Q2–Q8 按建议。配图：[client-activation.pen](client-activation.pen)（五张画板：01 全流程泳道；02 开通项门禁与走数例子；03 系统缺口清单与切片；04 客户上线流程模板；05 运营视角：待办任务卡与上线进度）。本稿把一个 Client 从"提交入驻申请"走到"可以生产对接并完成交易验证"的全部工作串成一条流程，标出每一步的执行人、业务 Owner、产出事实、出口门禁与现状。它不替代各 Owner 的设计：入驻与尽调见 [client-onboarding](../client-onboarding/design.md)，合同与开通见 [commercial](../commercial/design.md) 与 [commercial-capability-pricing](../commercial-capability-pricing/design.md)，编排工具见 [workflow](../workflow/design.md)，商业关系模式 C1–C5 见 [2026-10-23 决定](../../agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md)。已登记决定（Proposed）：[2026-10-28 客户上线决定](../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md)；REQ-01 / 05 / 12 已登记阶段 1 评审稿。未改代码与模型。需求人视角的完整规划（逐步卡片、异常、系统结合、需求拆分与里程碑）见 [requirement-plan.md](requirement-plan.md)。

## 1. 一句话

**"激活"不是一个开关：`t_client.status` 只表示合作关系是否有效；能不能收款、能不能出款由 commercial 合同上的开通项控制**——支付方式类开通项（CARD、APPLE_PAY…）生效即可收款，出款通道类开通项（PAYOUT_LOCAL、PAYOUT_SWIFT…）生效即可出款。开通项生效前要满足各自的前置条件。资格通过只是其中第一道门；之后还要风控承保、签约开通、渠道报备、技术接入、收款账户核验，最后由运营上线复核并完成一笔生产交易验证。行业做法一致：Stripe 分 `charges_enabled` / `payouts_enabled`，Adyen 每项能力 `requested → allowed → enabled`，Checkout.com 尽调通过后分别下发 `payments_enabled` / `payouts_enabled`。

现状偏差：案件批准即在同一事务把 `t_client.status` 置 ACTIVE（client-onboarding 6.1 节 A/C），此时 Client 既没有合同、渠道报备，也没有 API 凭据，"ACTIVE"语义失真。

## 2. 总览

```
P0 注册与入驻申请 ──▶ P1 合规尽调与资格决定 ──┬──▶ P2 风控承保 ─────────┐
   (Client)            (PayFac 合规 / 运营)    │                        │
                                              ├──▶ P3 签约与产品开通 ──┬─┼──▶ P4 渠道报备 ──┐
                                              │    (商务 + Client 签署) │ │   (渠道运营)     │
                                              │                        │ │                 ▼
P6 技术接入（API 凭据 / Webhook / 联调）──────────── 可与 P1 并行开始 ─────┼──────────▶ P7 上线复核
   (Client 开发者)                                                     │ │              + 生产交易验证
                                              └──▶ P5 财务与税务 ───────┴─┘              + 观察期
                                                   (收款账户核验 / 税务 / 保证金)              │
                                                                                            ▼
                                                                  ACCEPT_PAYMENTS ✔  RECEIVE_PAYOUTS ✔
```

- **串行硬依赖**：P1 → P3 → P4（合同确定 OE、业务模式、开通的支付方式与 MCC 后才能报备渠道）；P7 在最后。
- **可并行**：P2、P3、P5 在 P1 之后并行；P6 的沙箱联调可在 P1 期间就开始，生产凭据在 P7 前完成。
- **收款与出款分开**：P5 的收款账户核验只阻塞出款；保证金是否阻塞收款由 Risk 的最低保证金要求决定。

## 3. 阶段明细

### P0 注册与入驻申请（执行人：Client）

| 步骤 | 内容 | Owner | 产出 | 现状 |
|---|---|---|---|---|
| 0.1 | 注册账号、邮箱 OTP、创建 CLIENT Tenant | Platform IAM | Account、Tenant | 已有 |
| 0.2 | 填 PayFac 入驻码，确定责任 PayFac | client-master | `t_partner_onboarding_code` 使用记录 | 切片 1 已上 |
| 0.3 | 选辖区 / 主体类型 / 组织类型，按采集 Schema 填资料、关系人（UBO / 董事 / 签字人）、材料、声明 | client-master | 申请 DRAFT、Profile 草稿、证件 Vault Token | 切片 1 已上 |
| 0.4 | 业务信息：网站 / App、商品与服务描述、预计月流水与客单价、目标国家、期望支付方式、退款政策链接 | client-master（采集） | 申请业务分区 | 部分有：网站、预计流水已采；**期望支付方式、退款政策链接未采** |
| 0.5 | 提交 → I01 立案 | client-master → compliance | 申请 UNDER_REVIEW、案件 SUBMITTED | 已有 |

出口：申请 UNDER_REVIEW。

### P1 合规尽调与资格决定（执行人：PayFac 合规 / 平台运营代操作；决定人四眼）

| 步骤 | 内容 | Owner | 产出 | 现状 |
|---|---|---|---|---|
| 1.1 | 自动名单筛查（机构 + 每个关系人：制裁 / PEP / 负面媒体） | compliance → channel-connector SERVICE | 筛查 run / hit | Mock 渠道 + 人工兜底 |
| 1.2 | 材料审核：逐件接受 / 拒绝（原因码）、豁免 | compliance | 证据状态 | 已有 |
| 1.3 | 人工检查项：网站审查（商品与申报一致、退款政策、联系方式、价格币种）、MATCH / VMSS 查询、地址证明、银行证明户名 | compliance | `t_dd_check`（MANUAL） | 已有框架 |
| 1.4 | 关系人子案件（RELATED_PERSON_KYC）全部决定 | compliance | 子案件 APPROVED | 已有 |
| 1.5 | 行业 / MCC 确认（O04）、禁止 / 限制行业判定 | compliance（读 Platform 参考数据） | 分类确认 | 已有 |
| 1.6 | 客户风险评级 CRR（LOW / MEDIUM / HIGH）+ 依据；HIGH 升 ENHANCED 补材料 | compliance | 评级、档次 | 手工评级已有 |
| 1.7 | 退回补件（需求级错误码）→ Client 重提 → 同案件下一轮 | compliance ⇄ client-master | 多轮记录 | 已有 |
| 1.8 | 推荐 → 决定（四眼，decided_by ≠ recommended_by） | compliance | 不可变决定、角色资格 APPROVED（valid_to、next_review） | 已有 |

出口：`t_role_qualification` = APPROVED，发布 `ClientReviewDecided` / `QualificationChanged`。
**本稿建议**：此时 Client 进入"资格通过、待开通"，不再直接等于可交易（见第 5 节 Q1）。

### P2 风控承保（执行人：风控）

| 步骤 | 内容 | Owner | 产出 | 现状 |
|---|---|---|---|---|
| 2.1 | 承保评估：行业风险、预计流水、拒付预期、交付周期（预售 / 订阅）、经营年限 | risk-decision | 承保结论 | **无** |
| 2.2 | 交易限额：单笔 / 日 / 月限额、新户观察期限额 | risk-decision（规则） | 限额配置 | 部分：已有频次变量与规则引擎，**无按 Client 配置的限额与观察期** |
| 2.3 | 最低保证金要求、滚动储备 | risk-decision | 最低保证金要求 | 设计已有（Risk 拥有），**无配置入口** |
| 2.4 | 结算周期建议（T+N）、结算控制 | risk-decision → commercial 条款 | 建议值；T+N 落在合同条款 | 条款有，风控建议无 |
| 2.5 | 高风险 MCC 识别 → 触发 P4 的卡组织高风险登记 | risk-decision / compliance | 登记需求 | **无** |

出口：限额与保证金要求已生效；若要求保证金，则 P5.3 到账前不放开收款（Q4）。

### P3 签约与产品开通（执行人：商务；Client 签署）

| 步骤 | 内容 | Owner | 产出 | 现状 |
|---|---|---|---|---|
| 3.1 | 选商业关系模式（C1 Partner OE × MoR / C2 Partner OE × PSP / C3 Direct）与收单 OE | commercial（读 client-master 候选） | 合同 DRAFT（精确一个 OE、`business_mode`） | 已有 |
| 3.2 | 订阅产品、开通项（支付方式 + 接受条件）、条款（T+N、保证金） | commercial | 合同内容 | 已有 |
| 3.3 | 价格：从标准价目模板生成合同价格副本，破标标记 | commercial | Term Version | 一期已上 |
| 3.4 | 合同审批（内部） | commercial（+ workflow 记"人已同意"） | APPROVED | 已有 |
| 3.5 | Client 签署合同与价格（门户接受，记 ip / user_agent / 时间） | commercial / Platform legal | CLIENT_SIGNED | 部分：运营代登记签署已有；**门户自助签署缺** |
| 3.6 | 排期 → 生效 | commercial | Agreement ACTIVE、`AgreementStatusChanged` | 已有 |

出口：Agreement ACTIVE，且开通项覆盖 Client 要用的支付方式。

### P4 渠道报备（执行人：渠道运营；对外是收单行 / 卡组织 / 钱包）

按商业关系模式不同：

| 模式 | 谁是收单行眼中的商户 | 要做的报备 |
|---|---|---|
| C1 Partner OE × MoR | OE（Partner 的主体） | 不做子商户报备；确认 OE 的 MCC 范围覆盖 Client 行业（"Client MCC ⊆ OE MCC"），超出则 OE 先向收单行申请新增 MCC / 业务线 |
| C2 Partner OE × PSP | OE 为 PayFac 主商户，Client 为子商户 | **子商户登记**：向收单行报送子商户 ID、法定名、MCC、地址、网址、账单描述符；等待生效；交易报送子商户标识（R15） |
| C3 Direct Merchant | Client 自有 OE / MID | Client 向收单行申请 MID（可由 Partner 代办）；MID 录入 `t_payment_channel → t_channel_account → t_channel_mid` |

所有模式还可能需要：

| 步骤 | 内容 | Owner | 现状 |
|---|---|---|---|
| 4.1 | 子商户登记 / MID 申请（上表） | channel-connector（子商户绑定 `ACQUIRER_SUB_MERCHANT`）/ payment（MID） | 子商户登记 **无**；MID 配置已有 |
| 4.2 | 高风险 MCC 卡组织登记（如 Visa VIRP：5967 / 7995 / 7273 等须收单行先登记获批再收款） | channel-connector（记录登记事实） | **无** |
| 4.3 | 本地卡网 / 支付方式单独开通（如沙特 mada 需本地收单 MID；Apple Pay 商户域名验证） | payment / channel-connector | **无** |
| 4.4 | 3DS 商户登记（经收单行在各卡组织目录服务器登记商户） | payment | 3DS 全链路已上，**登记事实无记录处** |
| 4.5 | 路由可用性核对：该 OE 下有 ACTIVE MID 覆盖开通的支付方式 / 币种 | payment | 可查询，无"报备完成"汇总 |

出口：每个开通的支付方式都有生效的渠道身份（子商户绑定 ACTIVE 或 MID ACTIVE）。

### P5 财务与税务（执行人：Client 填写，财务核验）

| 步骤 | 内容 | Owner | 产出 | 现状 |
|---|---|---|---|---|
| 5.1 | 添加收款账户（户名、银行、IBAN），Maker / Checker 核验户名 = 法定名 | payout（`beneficiary_account`） | VERIFIED 账户 | **缺：payout 服务尚无代码**（只有设计与模型） |
| 5.2 | 税务资料：VAT / TRN 号、税务居民地、发票抬头 | tax / client-master | 税务登记 | tax 讨论中 |
| 5.3 | 保证金到账（如 Risk 要求）：`CLIENT_SECURITY_DEPOSIT` 入账 | finance（account / ledger） | 保证金 Journal | 记账模型有，**入驻场景无流程** |
| 5.4 | 平台费付款方式（C3 直接向平台付费：发票或预存） | billing | 计费主体 | 视 2026-10-23 决定 Q7 |

出口：出款能力需要 5.1 VERIFIED 且无结算控制；5.3 视 P2 要求可能阻塞收款。

### P6 技术接入（执行人：Client 开发者；技术支持协助）

| 步骤 | 内容 | Owner | 产出 | 现状 |
|---|---|---|---|---|
| 6.1 | 读文档、沙箱联调（下单 / 支付 / 回调 / 退款） | Platform / commerce | 联调记录 | **Client 侧沙箱环境不存在**（现有沙箱验证只针对渠道） |
| 6.2 | 创建生产 API Credential、授予 Scope | Platform（Tenant-owned API Credential） | ACTIVE Credential | 门户开发者页已有 |
| 6.3 | 配置 Webhook 端点并验证、订阅事件 | Platform（Client Webhook） | VERIFIED Endpoint | 门户已有 |
| 6.4 | 结账页配置：回跳 URL、品牌、语言 | commerce | 配置 | 待核 |
| 6.5 | Integration Readiness 派生查询 = READY | Platform PLT-I13 | READY / NOT_READY / RESTRICTED | 设计已有（同步派生，无持久化） |

出口：Integration Readiness = READY。

### P7 上线复核、生产交易验证与观察期（执行人：运营 + Client）

| 步骤 | 内容 | 产出 |
|---|---|---|
| 7.1 | 上线复核：逐项核对第 4 节就绪条件，运营负责人放行 | 复核记录 |
| 7.2 | 生产交易验证：Client 用真实卡小额下单 → 支付成功 → Webhook 收到 → 退款成功 → 次日对账出现在渠道账单 → billing 事实生成 | 一组可追溯的 Order / Payment / Refund ID |
| 7.3 | 观察期：降低限额、首批交易人工复核、拒付 / 欺诈率监控；期满恢复正常限额 | 观察期结束记录 |
| 7.4 | 启动持续监控：筛查持续监控、定期复审排期、证件到期提醒 | 监控生效 |

出口：两项能力均"已开通"，Client 正式运营。

## 4. 就绪条件：由开通项控制（Q1 已定）

**Client 状态**：PENDING（入驻中）→ ACTIVE（资格通过，合作关系有效）→ SUSPENDED / TERMINATED。它不表示"能交易"。

**可收款 / 可出款 = 合同开通项状态**（`t_agreement_capability`，开通项 = OE × 支付方式 / 出款通道）。现有执行链路已经按开通项判定：Checkout 解析只输出合同已开通且已定价的支付方式；出款按出款通道开通项。缺的是**开通项生效前的前置门禁**和开通项的"待前置"状态。

```
开通项：DRAFT（合同里已选）──▶ PENDING_PREREQUISITES（已签署，等前置）──▶ ENABLED（可交易）──▶ SUSPENDED / DISABLED
                                         ▲ 前置全部满足，commercial 事务外逐项向 Owner 查询，满足后本地事务置 ENABLED
```

**门禁收窄（2026-10-28 复核）**：commercial 门禁只核对“没有它交易必然失败、且 commercial 能说清”的三项前置 ①②④；其余（承保 / 限额、收款账户、结算控制、保证金）由各自 Owner 在运行期检查，不进门禁，避免与执行点双份逻辑。

| 开通项种类 | 生效前置（commercial 激活时事务外查询 Owner） | 判定来源 | 执行点仍自校验 |
|---|---|---|---|
| 支付方式（收款） | ① Client 角色资格 APPROVED（非 REVIEW_OVERDUE / REVOKED） | compliance | acquiring 支付准备 |
| | ② 合同已签署、价格已生效（现有完整性 / 相关性校验） | commercial 本库 | Checkout 解析 |
| | ④ 该支付方式在该 OE 下有生效渠道身份（C2 子商户绑定 / C3 MID / C1 OE MCC 覆盖；mada 等本地方式另需单独开通） | channel-connector / payment | 路由选 MID |
| 出款通道（出款） | ①② 同上；④ 对应出款通道配置存在 | 同上 | Manual Payout 四个 Risk 检查点 |

**不进门禁、由运行期控制的条件**：

| 条件 | 运行期在哪里控制 | 效果 |
|---|---|---|
| ③ 风控承保 / 限额 | risk-decision 限额档位：开通项生效时若无档位则为 VERIFICATION，无承保记录则额度为零 | 未承保 = 不能真正交易，但不需要 commercial 知道 |
| ⑤ 收款账户 VERIFIED、⑥ 无结算控制、⑦ 保证金 | Manual Payout 四个 Risk 检查点（已有设计） | 出款前逐笔检查 |

**不进开通项门禁的两项**：

- **技术接入**（Integration Readiness = READY）：仍由 Platform PLT-I13 在每次调用点判定，它是"能不能调用接口"，不是商务开通。
- **上线复核与生产交易验证**：是运营过程，记在 workflow，workflow 本身不作执行开关（红线二）。放量由 **Risk 限额档位**控制：开通项生效后 Client 只有验证档额度，上线复核在 Risk 的档位变更页四眼批准后切到观察档，观察期满切标准档。生产交易验证需要开通项已 ENABLED 才能做。

**"还差什么"清单**：operations-console-bff 实时聚合（Q2），按开通项逐项列出未满足的前置、Owner、负责人；Client 门户展示同一份（Stripe `requirements` 形状）。

## 5. 角色 × 阶段

| 角色 | P0 | P1 | P2 | P3 | P4 | P5 | P6 | P7 |
|---|---|---|---|---|---|---|---|---|
| Client 管理员 | 填写提交 | 补件 | — | 签署 | 提供材料 | 收款账户、税务、保证金 | — | 生产验证 |
| Client 开发者 | — | — | — | — | — | — | 联调、凭据、Webhook | 生产验证 |
| PayFac 合规 | — | 审核、推荐、决定 | — | — | — | — | — | — |
| 平台运营（代 PayFac） | — | 代操作 | — | — | — | — | — | 上线复核 |
| 风控 | — | CRR 协同 | 承保、限额、保证金 | — | 高风险登记触发 | — | — | 观察期监控 |
| 商务 | — | — | — | 合同、开通、价格 | — | — | — | — |
| 渠道运营 | — | — | — | — | 子商户 / MID / 方式开通 | — | — | — |
| 财务 | — | — | — | — | — | 账户核验、保证金确认 | — | 对账核对 |
| 技术支持 | — | — | — | — | — | — | 协助联调 | 交易验证 |

## 6. 系统缺口清单（2026-10-28 按代码核对）

P0 = 不补就无法正确控制可收款 / 可出款或无法上线；P1 = 重要但可先人工兜底；P2 = 后续。

| 系统 | 优先级 | 缺口 | 阶段 / 依赖 |
|---|---|---|---|
| commercial | P0 | 开通项 `PENDING_PREREQUISITES` 状态 + 生效前置门禁（只查 ①②④，事务外查 Owner，短事务置 ENABLED） | P3；依赖资格查询、渠道身份查询 |
| | P1 | 前置失效 → 开通项 SUSPENDED（消费资格 / 渠道变化事件） | P3 |
| | P1 | 门户自助签署合同与价格（现为运营代登记 CLIENT_SIGNED） | P3 |
| risk-decision | P0 | 承保评估记录（结论、依据、复核人），供门禁查询 | P2 |
| | P0 | 按 Client 的交易限额与观察期限额（复用现有频次变量与规则引擎） | P2、P7 |
| | P1 | 最低保证金要求配置入口 | P2 |
| | P1 | 识别高风险 MCC → 生成卡组织登记需求 | P2 → P4 |
| channel-connector | P0 | 子商户登记（C2）：申请、报送收单行、生效 / 停用、交易报送标识 | P4；依赖合同 OE 与 MCC |
| | P1 | 卡组织高风险登记记录（Visa VIRP 等） | P4 |
| | P1 | 本地方式开通（mada）、Apple Pay 域名、3DS 商户登记的登记事实 | P4 |
| payment | P0 | 渠道身份就绪查询：OE × 支付方式是否有生效 MID / 子商户绑定 | P4 → 门禁④ |
| | P1 | C1 校验 Client MCC ⊆ OE MCC | P4 |
| payout（无代码） | P0 | 收款账户：门户添加、Vault 保护、运营 Maker / Checker 核验 | P5 → 出款运行期检查⑤ |
| | P0 | Manual Payout 全链路（已设计未实现） | 出款能力本身 |
| compliance | P0 | 真实筛查供应商替换 Mock（生产门禁） | P1 |
| | P1 | 结算控制 Processor（SETTLEMENT_BLOCK 创建 / 释放 / 事件；现只有 API 合同） | 出款运行期检查⑥ |
| | P1 | 持续监控：定期复审、证件到期、筛查持续监控 | P7 之后 |
| finance · tax | P1 | 入驻保证金：通知、到账确认、`CLIENT_SECURITY_DEPOSIT` 入账 | P5 → 出款运行期检查⑦ |
| | P1 | 税务资料采集与核验（VAT / TRN），tax 讨论中 | P5 |
| client-master | P1 | 补采期望支付方式、退款政策链接 | P0 |
| Platform workflow | P0 | 运营按钮发起实例：按业务关联键 `CLIENT:{id}` 防重，模板固定、变量预填（Q9） | 客户上线流程 |
| | P1 | `DATA_QUERY` 验证落地 + 自动重验；按角色 / 队列认领、超时提醒 | 节点验证、人员 |
| | P2 | 从业务对象反查实例 | Client 详情 |
| Platform 其他 | P2 | Client 沙箱 / 测试模式（Q7 单独需求） | P6 |
| operations-console | P0 | Client 详情"激活进度"：按开通项列出还差什么、Owner、负责人（BFF 实时聚合） | 全阶段 |
| | P1 | 生产交易验证记录（workflow 表单：Order / Payment / Refund ID） | P7 |
| client-console | P0 | "上线清单"页：缺什么、谁来做、截止时间、影响哪项能力 | 全阶段 |
| | P0 | 收款账户、税务资料入口（随 payout / tax 交付） | P5 |

**建议切片顺序**（每片可独立验证）：

| 切片 | 内容 | 交付后能力 |
|---|---|---|
| S-A 开通项门禁骨架 | commercial 状态 + 门禁；payment 渠道身份查询；risk 承保最小记录；运营激活进度页 + 门户上线清单 | 收款被开通项正确控制 |
| S-B 渠道报备 | 子商户登记（C2）、高风险 / 本地方式 / 3DS 登记事实、C1 MCC 覆盖校验 | C2 与本地方式可上线 |
| S-C 风控承保与限额 | 承保记录、按 Client 限额与观察期、保证金要求入口 | 观察期可执行 |
| S-D 出款 | payout 收款账户 + 核验 + Manual Payout；结算控制 Processor | 可出款 |
| S-E 客户上线编排 | workflow 按钮发起防重、`DATA_QUERY` 完成条件、等待节点、队列认领（第 7.6 节 W1–W4、W9）；生产交易验证记录 | 新运营按待办就能推进全流程 |
| S-F 补齐 | 门户自助签署、保证金流程、税务资料、持续监控、真实筛查、Client 沙箱 | — |

## 7. 流程化编排：让新运营知道"下一步谁做、在哪做、做什么"（2026-10-28 讨论稿）

### 7.1 两份清单各管一件事

| | 开通项门禁（第 4 节） | workflow"客户上线"实例 |
|---|---|---|
| 回答 | 事实是否满足：能不能收款 / 出款 | 谁做、在哪做、做什么、卡在谁 |
| Owner | commercial + 各前置 Owner | Platform workflow |
| 是否执行开关 | 是（开通项） | 否（红线二） |
| 如何保持一致 | — | 节点的完成条件直接查同一份 Owner 事实（`DATA_QUERY` 只读），不靠人点"完成" |

所以运营永远不会遇到"流程说完成了，但系统不让交易"的分叉：流程节点完成 ⇔ Owner 事实已满足。

### 7.2 每个节点固定四要素

| 要素 | 含义 | 来源 |
|---|---|---|
| **谁** | 队列（角色）+ 认领人；等 Client / 等外部时还有一名"跟进人" | 节点配置：队列；认领后为具体人 |
| **做什么** | 一句话指令 + 完成标准（系统检查什么） | 动作模板的说明与完成条件 |
| **在哪做** | 按钮直达 Owner 页面，带参数（clientId、caseId、agreementId…） | `PAGE_TASK` 路由 + 变量映射 |
| **怎么算完** | 系统查 Owner 事实自动完成；查不到时显示"还差什么" | `DATA_QUERY` 完成条件 |

### 7.3 四类节点

| 类型 | 用在 | 完成方式 | 页面上看到 |
|---|---|---|---|
| 运营做 | 合规审核、承保、起草合同、子商户登记、账户核验 | 运营在 Owner 页面做完，系统查到事实即自动完成；也可点"立即检查" | 待认领 / 我的待办，带"去处理"按钮 |
| 等 Client | Client 签署、补件、添加收款账户、缴保证金、技术接入、生产验证 | 系统查到事实自动完成 | 跟进人的待办里显示"等 Client · 已等 N 天"，可"催办"（Platform 通知）；Client 门户上线清单显示同一事项 |
| 等外部 | 收单行子商户生效、卡组织高风险登记 | 跟进人录入结果或系统查到状态 | "等收单行 · SLA 剩余 N 天"，超时提醒 |
| 人判断 | 上线复核 | 上线负责人在 Risk 档位页提交，风控四眼批准；流程查档位 = OBSERVATION 自动完成 | 待办里“处理 + 复核”两张任务 |

### 7.4 "客户上线 v1"流程模板

```
运营点「发起上线流程」（审核列表 / Client 详情；业务关联键 CLIENT:{clientId}，同一 Client 只有一个进行中实例）
 └▶ N1 合规尽调与决定 [队列：合规 / PayFac 合规] → 审核工作台 /client-onboarding/reviews/{caseId}
       完成：案件 APPROVED（输出 riskRating、MCC）｜ 案件 RETURNED 时卡片显示"等 Client 补件"｜ REJECTED → 结束（未通过）
 └▶ 并行分叉
     ├ 风控：N2 承保与限额 [风控] → 承保页（新）；完成：承保记录 APPROVED（输出 requireDeposit、highRiskMcc）
     ├ 商务：N3 起草合同与价格 [商务] → /commercial/agreements/new?clientId；完成：合同 APPROVED（输出 agreementId、businessMode、开通的支付方式）
     │       N4 等 Client 签署 [等 Client，跟进：商务]；完成：条款 CLIENT_SIGNED
     │       ◇ businessMode：C2 → N5a 子商户登记 [渠道；签署前只做内部准备，合同 ACTIVE 后报送] → N5b 等收单行生效 [等外部，SLA 5 工作日]
     │                       C3 → N5c MID 录入 [渠道]；C1 → N5d OE MCC 覆盖（系统自动核对）
     │       ◇ highRiskMcc → N5e 卡组织高风险登记 [渠道，等外部]
     │       ◇ 开通了 MADA 等本地方式 → N5f 本地方式开通 [渠道，等外部]
     ├ 财务：◇ requireDeposit → N6a 等保证金到账 [等 Client，跟进：财务]
     │       N6b 等 Client 添加收款账户 [等 Client] → N6c 核验收款账户 [财务] → payout 账户页
     └ 技术：N7 等 Client 技术接入 [等 Client，跟进：技术支持]；完成：凭据 ACTIVE + Webhook VERIFIED
 └▶ 并行汇合
 └▶ N8 开通项生效检查 [系统]：全部开通项 ENABLED；未生效则列出缺哪条前置并链接到对应节点
 └▶ N9 生产交易验证 [技术支持 + Client]：表单填 Order / Payment / Refund ID，系统核对三者成功
 └▶ N10 上线复核 [上线负责人提交 → 风控批准，在 Risk 限额档位页]；完成：档位 = OBSERVATION
 └▶ N11 观察期 [等待，跟进：风控]；完成：档位 = STANDARD → 结束
```

**发起方式（Q9 已定）**：只由运营在页面上点按钮发起，外部（Client、PayFac）的提交动作不触发任何内部流程。

- **按钮位置**：入驻审核列表每行、审核工作台顶部、Client 详情"上线进度"页签。未发起时显示「发起上线流程」，发起后变成「查看上线进度」。
- **不会漏**：Client 提交申请后只在审核列表出现一条"待发起"记录（显示已提交 N 天）；列表默认筛"待发起"，看板第一列也是"已提交未发起"。
- **什么时候点都行**：节点完成靠查事实，所以晚点发起也不会错——已经满足的节点（如案件已 APPROVED）一发起就自动完成，流程直接停在真正还没做的那一步。建议的习惯是"接手审核时就点"，这样合规审核本身也在流程和看板里。
- **防重**：同一 Client 同一模板只能有一个进行中实例；重复点击直接打开已有实例。上一个实例被终止后才能重新发起。
- **权限**：发起权限单独授予（如合规组长、运营），普通成员只能处理分到自己队列的任务。

`businessMode`、`highRiskMcc`、`requireDeposit` 都是上游 `DATA_QUERY` 的输出变量，网关按变量分支，不靠人选择。

### 7.5 新运营的三个入口

1. **我的待办 / 待认领**（已有页面，增强）：每张任务卡写明客户、阶段、做什么、完成标准、"去处理"按钮、当前检查结果（还差什么）、截止时间；队列任务先认领。
2. **Client 详情 → 上线进度页签**：横向流程进度 + 开通项清单 + 每个未完成项的负责人 / 队列 / 已等时长。
3. **上线看板**：所有在途 Client 按节点分列，超 SLA 标红，按队列筛选（给主管看哪里堵了）。

### 7.6 workflow 与 Owner 要补的能力

| 编号 | 能力 | 归属 | 优先级 |
|---|---|---|---|
| W1 | 按钮发起：沿用现有"登录运营发起"接口；补 `definitionCode + businessKey` 进行中实例唯一（重复点击返回已有实例）、按模板编码发起（运营不选模板）、BFF 预填 clientId / caseId 变量、发起权限 | Platform workflow + operations-console | P0 |
| W2 | `DATA_QUERY` 落地：作完成条件、输出变量、定时重检（XXL-Job）+"立即检查"；查询失败或延迟只算"尚未满足" | Platform workflow + data-query | P0 |
| W3 | 等待节点：无人提交、只由条件完成；跟进人、已等时长、催办（Platform 通知） | Platform workflow | P0 |
| W4 | 队列指派与认领：动作 / 审批可指派给角色，成员认领后进入个人待办 | Platform workflow + Access Control | P0 |
| W5 | 任务卡展示"做什么 / 完成标准 / 当前检查结果" | workflow + operations-console | P1 |
| W6 | 节点 SLA 与超时提醒 | Platform workflow | P1 |
| W7 | 按业务关联键反查实例；Client 详情上线进度页签；上线看板 | operations-console | P1 |
| W8 | Owner 页面支持带参数直达（预填 clientId 等），以及新页面：承保页、子商户登记页、收款账户核验页 | 各 Owner 前端 | P0（随各切片） |
| W9 | 已登记的数据查询：每个完成条件一条只读查询，登记在 data-query | 各 Owner + data-query | P0 |
| W10 | PayFac 合规人员直接处理 N1（现为运营代操作） | workflow + Partner 门户 | 后续 |

建议把 W1–W4 + W9 作为切片 **S-E**，紧跟 S-A；之后每个 Owner 切片交付时，同时登记它的完成条件查询并把节点从"人工确认"换成"自动完成"。

## 8. 待定问题

| 编号 | 问题 | 倾向 |
|---|---|---|
| Q1 | `t_client.status` 语义 | **已定**：Client 状态只表示合作关系是否有效；可收款 / 可出款由合同开通项控制 |
| Q2 | "还差什么"清单由谁算 | **已定**：BFF 实时聚合（运营 / 门户两个 BFF 各自聚合）；开通项门禁本身在 commercial |
| Q3 | 限额、观察期归 risk-decision 还是 commercial 条款 | **已定**：risk-decision（风险参数），合同只引用 |
| Q4 | 保证金未到账是否阻塞收款 | **已定**：由 Risk 的最低保证金要求逐 Client 决定，默认不阻塞收款、只阻塞出款 |
| Q5 | 子商户登记（R15）与高风险登记的事实放在 channel-connector 还是 payment | **已定**：channel-connector（2026-10-23 决定已把子商户绑定放在那里）；高风险登记同处 |
| Q6 | 生产交易验证是必选还是按模式 / 风险可选 | **已定**：必选，小额 + 退款闭环；C1 由平台测试卡 / 内部账户完成也可 |
| Q7 | Client 沙箱环境是否本期做 | **已定**：单独需求；首期用 mor-test 联调 + 生产小额验证替代 |
| Q8 | 观察期长度与退出条件 | **已定**：默认 30 天或前 N 笔，按 CRR 调整；具体值风控定 |
| Q9 | 上线流程由谁、何时发起 | **已定**：运营在页面按钮发起，外部提交不触发内部流程；时间不限，已满足的节点发起后自动完成 |
