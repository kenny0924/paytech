# 客户上线（Client Go-live）需求规划

> 状态：**规划已定（2026-10-28，用户确认 Q-1–Q-10 按建议；同日复核后调整四处：门禁收窄到 ①②④、完成条件查询由 Owner 维护、C2 报送改为签署后、M1 的 workflow 范围收缩），需求拆分见第 14 节**，讨论阶段，未登记需求、未改代码与模型。
> 上游：[design.md](design.md)（全流程、开通项门禁、缺口清单、编排思路，Q1–Q9 已定）。本稿在其基础上做**需求人视角的完整规划**：每一步的业务规则、系统事实、异常分支、页面、通知、权限，以及系统之间怎样结合、拆成哪些需求、按什么顺序交付。
> 状态值均按 2026-10-28 代码核对；标 **（新）** 的是本规划新增，尚不存在。

---

## 1. 背景与问题

一个 Client 从提交入驻申请到能真实交易，要经过合规、风控、商务、渠道、财务、技术六个职能，跨 client-master、compliance、risk-decision、commercial、channel-connector、payment、payout、finance、Platform 九个系统。今天的问题：

| # | 问题 | 后果 |
|---|---|---|
| P1 | 案件批准即把 Client 置 ACTIVE，同时合同开通项一加上就是 ENABLED | 资格、承保、渠道报备、签署任何一项没完成，系统都可能放行交易 |
| P2 | 每个职能只在自己系统里做事，没有一处能看到"这个 Client 卡在哪、该谁做" | 新运营要靠问人；交接靠聊天；超时无人发现 |
| P3 | 承保、子商户登记、收款账户核验、生产验证等步骤没有系统记录 | 事后无法证明"上线前做过什么"，审计与收单行检查无据可查 |
| P4 | 运营与 Client 看到的进度不是同一份事实 | Client 不知道自己还差什么，只能等运营邮件 |

## 2. 目标、非目标与成功标准

**目标**

1. **执行安全**：可收款 / 可出款只由开通项决定；开通项只有在全部前置事实满足后才生效，前置失效时自动暂停。
2. **流程可执行**：任何一个新运营打开"我的待办"，就能知道下一步**谁做、在哪做、做什么、做完的标准是什么**，并一键进入对应页面。
3. **过程可证明**：每一步都在 Owner 系统里留下事实（谁、何时、依据什么），上线复核可以逐项回看。
4. **双方同一视图**：运营的"上线进度"和 Client 门户的"上线清单"读同一批 Owner 事实。

**非目标（本规划不做）**

- 不建通用 BPM / 低代码平台；workflow 只做本流程需要的增强。
- 不让 workflow 成为任何执行开关（红线二）；不让外部提交触发内部流程（Q9）。
- 不改变各 Owner 已有的业务规则（尽调规则、定价规则、路由规则）。
- Client 沙箱、税务全量、持续监控的完整设计各自单独立项，本规划只定接口位置。

**成功标准（上线后度量）**

| 指标 | 定义 | 目标 |
|---|---|---|
| 上线周期 | 申请提交 → 收款上线（N10 通过）的自然日中位数 | 建立基线，季度下降 |
| 节点超 SLA 率 | 超时节点数 / 已完成节点数 | < 10% |
| 未发起滞留 | 申请提交后超过 2 个工作日仍未发起流程的 Client 数 | 0 |
| 上线后 30 天回退 | 上线后 30 天内因上线前遗漏被暂停的开通项数 | 0 |
| 门禁绕过 | 前置未满足但开通项 ENABLED 的记录数（巡检） | 0 |

## 3. 设计原则

| # | 原则 | 落到设计上 |
|---|---|---|
| 1 | **事实在 Owner，流程只看事实** | 每个节点的"完成"都是查 Owner 数据得出，不是人点"完成"；流程走完 ≠ 能交易，能交易只看开通项 |
| 2 | **执行开关只有一处** | 收款看支付方式开通项，出款看出款通道开通项；限额看 Risk 限额档位；workflow 没有开关 |
| 3 | **内部流程只由内部人发起** | 运营点按钮发起；Client / PayFac 的提交只产生 Owner 事实 |
| 4 | **人做判断，系统做核对** | 需要判断的（尽调决定、承保、上线复核）由人在 Owner 页面四眼完成；可核对的（签署、MID、账户、交易成功）由系统查 |
| 5 | **先人工兜底、后自动** | 每个节点先能以"人工确认 + 证据"运行，Owner 能力上线后再换成自动核对，模板版本化切换 |
| 6 | **收款与出款分开上线** | 收款上线不等出款就绪；出款就绪是同一实例里的独立出口 |
| 7 | **按风险分档** | 低风险走快速通道（自动承保、单人复核）；高风险加步骤（加强尽调、卡组织登记、保证金、双人复核） |

## 4. 参与方与职责（RACI）

R = 执行，A = 最终负责（决定 / 审批），C = 协作，I = 知会。

| 步骤 | Client 管理员 | Client 开发者 | PayFac 合规 | 平台运营 | 合规组长 | 风控 | 商务 | 渠道运营 | 财务 | 技术支持 |
|---|---|---|---|---|---|---|---|---|---|---|
| S0 发起 | I | | | R | A | | | | | |
| N1 尽调与决定 | C（补件） | | I（首期；后续 R/A） | R（首期全部代操作） | A | C | | | | |
| N2 承保 | | | | | | R/A | C | | I | |
| N3 合同与价格 | C | | | | | C | R/A | | | |
| N4 签署 | R | | | | | | C（跟进） | | | |
| N5 渠道报备 | C（材料） | | | | | | | R/A | | |
| N6 出款准备 | R（账户 / 保证金） | | | | | | | | R/A | |
| N7 技术接入 | | R | | | | | | | | C（跟进） |
| N8 开通项生效 | | | | I | | | I | | | |
| N9 生产验证 | C | R | | | | | | C | | R/A |
| N10 上线复核 | I | | | R（提交） | | A（批准） | | | | |
| N11 观察期 | | | | I | | R/A | | | | |

## 5. 业务对象与状态全景

### 5.1 参与本流程的事实对象

| 对象 | Owner 系统 | 状态（现有 / 新） | 本流程用到的含义 |
|---|---|---|---|
| 入驻申请 | client-master | DRAFT → SUBMITTED → UNDER_REVIEW ⇄ RETURNED → APPROVED / REJECTED / WITHDRAWN | 可发起流程的入口条件：SUBMITTED 之后 |
| Client | client-master | PENDING → ACTIVE → SUSPENDED / TERMINATED | 合作关系是否有效（Q1），不表示能交易 |
| 尽调案件 | compliance | DRAFT · SUBMITTED · IN_REVIEW · WAITING_EVIDENCE · PENDING_DEPENDENCY · RETURNED · APPROVED · REJECTED · WITHDRAWN · EXPIRED | N1 的完成与分支依据 |
| 角色资格 | compliance | APPROVED（含 valid_to、next_review）/ 复审逾期 / 撤销 | 开通项前置 ① |
| 承保记录（新） | risk-decision | DRAFT → SUBMITTED → APPROVED / DECLINED；APPROVED 可 REVISED | N2；Risk 限额档位的依据（运行期检查 ③，不进门禁） |
| 限额档位（新） | risk-decision | VERIFICATION → OBSERVATION → STANDARD；任意 → RESTRICTED。**实现：三张档位名单**（`t_risk_list` 新增 `list_purpose = TIER`），Client 在哪张名单就走决策流哪条分支，各分支绑不同限额版本；不在任何档位名单 = 拒绝分支 | 交易限额的执行开关；N10、N11 的完成依据 |
| 最低保证金要求 | risk-decision | 已设计，入口无 | 是否触发 N6a；Manual Payout 运行期检查 ⑦ |
| 合同 Agreement | commercial | DRAFT → PENDING_SIGNATURE → SIGNED → ACTIVE；EXPIRED / TERMINATED / CANCELLED | N3、N4 |
| 价格版本 Term Version | commercial | DRAFT → PENDING_APPROVAL → APPROVED → CLIENT_SIGNED → SCHEDULED → ACTIVE → SUPERSEDED / EXPIRED | N3 内审批、N4 签署 |
| 开通项 Agreement Capability | commercial | 现：ENABLED / SUSPENDED / DISABLED；**新增 PENDING_PREREQUISITES** | 唯一执行开关；N8 |
| 子商户绑定（新） | channel-connector | DRAFT → SUBMITTED → ACTIVE / REJECTED；ACTIVE → SUSPENDED / CLOSED | N5a / N5b；前置 ④（C2） |
| 渠道登记事实（新） | channel-connector | REQUIRED → SUBMITTED → APPROVED / REJECTED（类型：卡组织高风险、本地方式、3DS、钱包域名） | N5e / N5f；前置 ④ |
| MID | payment | `t_channel_mid` 现有状态 | N5c；前置 ④（C3） |
| 收款账户（新，payout 无代码） | payout | PENDING_VERIFICATION → VERIFIED / REJECTED；VERIFIED → DISABLED | N6b / N6c；Manual Payout 运行期检查 ⑤ |
| 结算控制 | compliance | 合同已有，Processor 无 | Manual Payout 运行期检查 ⑥ |
| 保证金入账 | finance（account / ledger） | `CLIENT_SECURITY_DEPOSIT/NONE` Journal | N6a；运行期检查 ⑦ |
| API 凭据 | Platform | ACTIVE / REVOKED / EXPIRED | N7 |
| Webhook 端点 / 投递 | Platform | 端点 ACTIVE / DISABLED；投递 CREATED…SUCCEEDED / DEAD | N7 |
| 上线验证记录（新） | workflow 表单 + 系统核对 acquiring / payment 事实 | — | N9 |
| 流程实例 | Platform workflow | RUNNING → COMPLETED / TERMINATED；动作 PENDING / COMPLETED / CANCELLED | 编排本身 |

### 5.2 两条执行开关如何联动

```
           门禁前置（只有三项）                         执行开关
 ① 资格 APPROVED ─┐                        ┌─ 支付方式开通项 ENABLED ──▶ 能收款（Checkout 解析只出 ENABLED 且已定价）
 ② 合同 / 价格 ACTIVE ┼─ commercial 门禁核对 ──┤
 ④ 渠道身份生效 ──┘   （事务外查，短事务置位）└─ 出款通道开通项 ENABLED ──▶ 能出款（Manual Payout 四个检查点逐笔再查）

           运行期检查（不进门禁，各 Owner 自己管）
 ③ 承保 / 额度 ──▶ Risk 限额档位（无承保记录 = 额度为零）
 ⑤ 账户 VERIFIED · ⑥ 无结算控制 · ⑦ 保证金 ──▶ Manual Payout 四个 Risk 检查点

 限额档位（Risk）：VERIFICATION（验证额度）→ OBSERVATION（观察额度）→ STANDARD
   ▲ 开通项刚生效时自动为 VERIFICATION，只够做生产验证；N10 复核批准后由风控切 OBSERVATION；N11 期满切 STANDARD
```

这样"上线复核"不需要 workflow 当开关：**开通项生效后 Client 已经能交易，但额度只够验证**；放量由 Risk 的限额档位控制，复核动作发生在 Risk 页面里，流程只观察档位变化。

## 6. 端到端主流程

```
Client 提交申请 ──(client-master 立案 compliance，已有)──▶ 审核列表出现"待发起"
                                                         │
S0 运营点「发起上线流程」──────────────────────────────────┘
 └▶ N1 合规尽调与决定 ──REJECTED──▶ 结束（未通过）
 └▶ 分叉
     ├ 风控  N2 承保（自动 / 人工）
     ├ 商务  N3 合同与价格 → N4 等 Client 签署
     │        └▶ 渠道（按 businessMode）N5a 子商户登记 → N5b 等收单行 ｜ N5c MID 录入 ｜ N5d MCC 覆盖核对
     │                                 + N5e 高风险登记（若 highRiskMcc） + N5f 本地方式开通（若 mada 等）
     └ 技术  N7 等 Client 技术接入（可在 N1 期间开始）
 └▶ 汇合（收款路径）
 └▶ N8 收款开通项生效（系统核对）
 └▶ N9 生产交易验证
 └▶ N10 上线复核（风控切限额档位 → OBSERVATION）  ══▶ 出口 A：收款上线
 └▶ N11 观察期（期满切 STANDARD）

 并行（出款路径，不阻塞收款上线）：N6a 保证金（若要求）· N6b 等 Client 添加收款账户 → N6c 核验账户
 └▶ N8' 出款开通项生效                                     ══▶ 出口 B：出款就绪
实例在 出口 A + 观察期结束 + 出口 B 都完成后 COMPLETED
```

**关键时序约束**

| 约束 | 原因 |
|---|---|
| N3 必须在 N1 APPROVED 之后 | 合同要用尽调确认的 MCC、风险评级；资格不通过不应发合同 |
| N5 必须在 N3 合同确定之后 | OE、业务模式、开通的支付方式、MCC 都来自合同 |
| N5 的**内部准备**不必等 N4 签署，但**对外报送**（C2 子商户、卡组织登记）必须在合同 ACTIVE 之后 | 收单行与卡组织要求已签署的子商户协议；签署前只做预填与核对，签署后立即报送。C3 的 MID 录入、C1 的 MCC 核对不涉及报送，可与签署并行（Q-4 修订） |
| N7 可在 N1 期间开始 | 沙箱联调不依赖资格；生产凭据要等 Client ACTIVE |
| N9 必须在 N8 之后 | 开通项未生效时交易不会成功 |
| 保证金若被 Risk 标为"阻塞收款" | N6a 从出款路径移到收款汇合前（Q4：默认不阻塞收款） |

## 7. 逐步详细设计

每一步按同一张卡片描述。"完成条件"一栏就是 workflow 节点的 `DATA_QUERY`（或过渡期的人工确认）。

### S0 发起上线流程

| 项 | 内容 |
|---|---|
| 目的 | 为一个 Client 建立唯一的上线流程实例，让所有后续工作有统一的待办、进度和审计线 |
| 入口 | 审核列表行操作、审核工作台顶部、Client 详情"上线进度"页签：「发起上线流程」 |
| 可发起条件 | 申请状态 ∈ {SUBMITTED, UNDER_REVIEW, RETURNED, APPROVED}；该 Client 无 RUNNING 的同模板实例；操作人有"发起上线流程"权限 |
| 系统动作 | ops-bff 按模板编码 `CLIENT_GO_LIVE` 取当前发布版本，预填变量 `clientId / applicationId / caseId / payfacPartnerId / payfacType(AFFILIATED/EXTERNAL) / jurisdiction`，以业务关联键 `CLIENT:{clientId}` 发起 |
| 发起后 | 立即对所有入口节点执行一次完成条件核对：已满足的节点直接完成（例如案件已 APPROVED，则 N1 立即完成并进入分叉） |
| 防重 | 重复点击返回已有实例；并发点击由 workflow 按 `definitionCode + businessKey + RUNNING` 唯一约束保证 |
| 终止 | 有"终止流程"权限者可终止（必填原因）；终止后按钮恢复为「发起上线流程」 |
| 防漏 | 列表"待发起"筛选显示已提交天数；超过 2 个工作日未发起，通知合规组长；看板第一列"已提交未发起" |
| 审计 | workflow 实例记录发起人、时间、模板版本；Client 详情时间线展示 |

### N1 合规尽调与资格决定

| 项 | 内容 |
|---|---|
| 目的 | 完成 KYC / KYB、筛查、行业 / MCC 确认、风险评级，作出资格决定 |
| 节点类型 | 运营做：**首期全部由平台运营代操作**（Q-10）；案件照常记录责任 PayFac，决定记录标明"平台代 PayFac 操作"。PayFac 在 Partner 门户自审（节点改为"等外部"）后续另立项 |
| 队列 | `COMPLIANCE_REVIEW`；决定需 `COMPLIANCE_DECIDER`（四眼：决定人 ≠ 推荐人，已有） |
| 去哪做 | 审核工作台 `/client-onboarding/reviews/{caseId}`（已有，materials / checks / screening / classification / risk / timeline + 决定面板） |
| 做什么 | 材料逐件审核；名单筛查命中处理；网站审查、MATCH / VMSS 等人工检查；关系人子案件；确认行业与 MCC；给 CRR；推荐 → 决定 |
| 卡片上的"现在" | 案件状态 + 未完成检查数 + 未处理筛查命中数 + 关系人子案件进度 |
| 完成条件 | 案件 = APPROVED **且** 角色资格 = APPROVED |
| 输出变量 | `crr`（LOW/MEDIUM/HIGH）、`mcc`、`industryCode`、`ddTier`（STANDARD/ENHANCED） |
| 分支 | RETURNED / WAITING_EVIDENCE → 卡片显示"等 Client 补件 · 已等 N 天"，跟进人可催办；REJECTED / WITHDRAWN / EXPIRED → 流程走"未通过"结束，实例 COMPLETED（结果 = REJECTED 等） |
| Client 侧 | 门户入驻页显示审核中 / 需补件（已有）；结果通知由 client-master 按 `ClientReviewResult` 发送（已有） |
| SLA | STANDARD 3 个工作日、ENHANCED 7 个工作日（不含等 Client 时间） |
| 现状 | 已有；缺真实筛查供应商（生产前必须，P0） |

### N2 风控承保

| 项 | 内容 |
|---|---|
| 目的 | 判断平台愿意承担该 Client 多大的交易与拒付风险，定下额度、保证金和结算周期建议 |
| 节点类型 | 系统（自动承保）或 运营做（人工承保），按规则分流 |
| 自动承保规则（新） | `crr = LOW` **且** 预计月流水 ≤ 阈值 **且** MCC 不在高风险清单 → 系统按标准模板生成承保记录 APPROVED（审批人 = 系统规则，记规则版本）；其余进入人工 |
| 队列 | `RISK_UNDERWRITING`；HIGH 风险需 `RISK_LEAD` 复核（四眼） |
| 去哪做 | 承保页（新，risk-decision 前端）`/risk/underwriting/new?clientId=` |
| 输入（自动带出） | 尽调输出（CRR、MCC、行业、经营年限）、申请业务信息（网站、预计流水、客单价、目标国家、期望支付方式、退款政策）、同 UBO 的其他 Client 历史（如有） |
| 做什么 | 评估交付周期（预售 / 订阅 / 实物）、退款与拒付预期；设定：三档限额（验证 / 观察 / 标准）的单笔、日、月额度；观察期长度或笔数（Q8）；是否要求保证金及金额；是否阻塞收款；结算周期建议 T+N；是否高风险 MCC 需卡组织登记 |
| 产出事实 | 承保记录 APPROVED（不可变，修订产生新版本）；限额档位配置；最低保证金要求（若有）；发布 `UnderwritingDecided`（新） |
| 完成条件 | 该 Client 存在 APPROVED 承保记录 |
| 输出变量 | `requireDeposit`、`depositBlocksCollection`、`highRiskMcc`、`suggestedSettlementDays` |
| 分支 | DECLINED → 合规组长与商务知会；流程停在 N2 等待人工决定"终止"或"重新承保"（承保拒绝不自动撤销资格，二者是不同判断） |
| 与商务的结合 | N3 合同页读取承保建议：结算周期低于建议值、免保证金时提示"破标"，进入合同审批 |
| SLA | 人工 2 个工作日 |
| 现状 | 无（P0 新建） |

### N3 合同、开通项与价格

| 项 | 内容 |
|---|---|
| 目的 | 确定商业关系模式、收单 OE、订阅产品、开通项与价格，并完成内部审批 |
| 前置 | N1 完成（需要 MCC 与资格）；N2 可并行，但合同审批提交前须读到承保结论 |
| 队列 | `COMMERCIAL`；价格审批走 commercial 已有审批（提交人不得审批） |
| 去哪做 | `/commercial/agreements/new?clientId=`（预填 Client、建议 OE 候选） |
| 做什么 | 选模式（C1 / C2 / C3）与 OE；选产品与开通项（支付方式、出款通道、币种）；从价目模板生成价格副本；填写条款（T+N、保证金引用 Risk 要求）；提交审批 |
| 业务校验（新增部分） | 开通的支付方式 ⊆ OE 可提供的方式；C1 时 Client MCC 须在 OE MCC 范围内（不满足给出"需 OE 先扩 MCC"提示，而非直接拒绝）；结算周期 < 承保建议 → 标破标 |
| 产出事实 | Agreement DRAFT → 提交签署后 PENDING_SIGNATURE；价格版本 APPROVED；开通项创建为 **PENDING_PREREQUISITES**（新，现为直接 ENABLED） |
| 完成条件 | 存在该 Client 的 Agreement ∈ {PENDING_SIGNATURE, SIGNED, ACTIVE} 且其价格版本 ≥ APPROVED |
| 输出变量 | `agreementId`、`businessMode`、`operatingEntityNo`、`capabilities[]`（支付方式与出款通道清单）、`localMethods[]`（如 MADA） |
| 分支 | 价格审批驳回 → 节点保持，卡片显示"审批被驳回：原因"；合同取消 → 节点回到未完成 |
| SLA | 3 个工作日 |
| 现状 | 已有；缺开通项新状态、C1 MCC 校验、承保建议联动 |

### N4 等 Client 签署

| 项 | 内容 |
|---|---|
| 节点类型 | 等 Client；跟进人 = N3 处理人（商务） |
| Client 侧 | 门户"上线清单"出现"签署合同与价格"，进入签署页查看合同正文与价格附件，确认后记录签署人、时间、IP、User-Agent（自助签署，S-F；过渡期由商务上传已签 PDF 并"登记客户签署"，已有） |
| 产出事实 | Agreement 当事方接受 → SIGNED；价格版本 CLIENT_SIGNED；随后排期 → ACTIVE（已有） |
| 完成条件 | Agreement = ACTIVE 且价格版本 = ACTIVE |
| 催办 | 跟进人点"催办"→ Platform 通知（邮件 + 门户站内信），记录催办次数；第 5 / 10 个工作日自动提醒跟进人 |
| 分支 | Client 要求改价 → 商务在 commercial 新建价格版本，N4 继续等待；Client 拒签 → 商务取消合同，流程停在 N3 |
| 现状 | 运营代登记已有；门户自助签署缺（P1） |

### N5 渠道报备（按 businessMode 分支）

| 子节点 | 条件 | 类型 / 队列 | 去哪做 | 做什么 | 完成条件 |
|---|---|---|---|---|---|
| N5a 子商户登记 | businessMode = C2 | 运营做 · `CHANNEL_OPS` | 子商户登记页（新）`/channels/sub-merchants/new?clientId=&agreementId=` | 系统预填法定名、注册地址、网址、MCC、账单描述符（≤ 卡组织长度上限）；渠道运营核对后保存为 DRAFT；**合同 ACTIVE 后**报送收单行（首期人工报送 + 录入报送编号）；页面在合同未生效时禁用“报送” | 子商户绑定 ∈ {SUBMITTED, ACTIVE} |
| N5b 等收单行生效 | 同上 | 等外部 · 跟进：渠道运营 · SLA 5 工作日 | 同上 | 录入收单行回执（生效 / 拒绝 + 原因） | 子商户绑定 = ACTIVE |
| N5c MID 录入 | businessMode = C3 | 运营做 · `CHANNEL_OPS` | `/channels/mids/new?operatingEntityNo=`（已有） | 录入 Client 自有 MID 与账号 | 每个开通的支付方式在该 OE 下有 ACTIVE MID |
| N5d MCC 覆盖核对 | businessMode = C1 | 系统 | — | 核对该 OE 下 ACTIVE MID 的 `t_mid_capability.mcc_code` 是否包含 Client MCC（不另维护 OE MCC 清单） | 核对通过；不通过时转人工"OE 扩 MCC"任务（完成标准：有 MID 能力行包含该 MCC） |
| N5e 卡组织高风险登记 | highRiskMcc = true | 等外部 · `CHANNEL_OPS` | 渠道登记页（新） | 经收单行向卡组织登记（Visa VIRP 等），录入登记号与生效日 | 登记事实 = APPROVED |
| N5f 本地方式开通 | localMethods 非空（如 MADA、Apple Pay 域名） | 等外部 · `CHANNEL_OPS` | 渠道登记页（新） | 本地收单 MID / 钱包商户域名验证 | 对应登记事实 = APPROVED |

**结合点**：N5 全部完成 ⇔ payment 的"渠道身份就绪查询"对每个开通的支付方式返回 READY，这正是开通项前置 ④，二者用同一个查询，不会对不上。
**分支**：收单行拒绝子商户 → 卡片显示拒绝原因，渠道运营可修改后重报（新建 SUBMITTED 版本）或上报商务改模式（回到 N3）。
**现状**：MID 录入已有；子商户、登记事实、渠道身份就绪查询均无（P0 / P1）。

### N6 出款准备（出款路径，不阻塞收款上线）

| 子节点 | 条件 | 类型 / 队列 | 去哪做 | 做什么 | 完成条件 |
|---|---|---|---|---|---|
| N6a 保证金到账 | requireDeposit = true | 等 Client · 跟进：财务 | Client 门户显示金额与付款指引；财务在 finance 确认到账 | Client 汇款；财务确认入账 `CLIENT_SECURITY_DEPOSIT/NONE` | 保证金余额 ≥ Risk 最低要求 |
| N6b 等 Client 添加收款账户 | 合同开通了出款通道 | 等 Client · 跟进：财务 | Client 门户"收款账户"（新） | 填写户名、银行、IBAN / 账号、币种，上传银行证明；敏感字段进 Vault | 存在该币种账户 ∈ {PENDING_VERIFICATION, VERIFIED} |
| N6c 核验收款账户 | 同上 | 运营做 · `FINANCE_OPS`（Maker / Checker） | 账户核验页（新）`/payout/beneficiary-accounts/{id}` | 核对户名 = 法定名、银行证明、国家与制裁限制；Maker 提交、Checker 批准 | 账户 = VERIFIED |

保证金若 `depositBlocksCollection = true`，N6a 移入收款汇合前（模板网关按变量决定）。
**现状**：payout 无代码（P0，S-D）；保证金入驻流程无（P1）。

### N7 技术接入

| 项 | 内容 |
|---|---|
| 节点类型 | 等 Client；跟进人：技术支持 |
| 开始时间 | 发起即激活（不等 N1）；生产凭据创建要求 Client ACTIVE（Platform 已有限制则沿用） |
| Client 侧 | 门户开发者页：创建生产 API 凭据、配置 Webhook 端点、发送测试事件（已有部分） |
| 完成条件 | 存在 ACTIVE 生产 API 凭据 **且** 存在 ACTIVE Webhook 端点 **且** 该端点有至少一次 SUCCEEDED 投递 |
| 卡片上的"现在" | 凭据 ✔/✘、端点 ✔/✘、最近投递结果 |
| 现状 | 凭据、端点、投递已有；Integration Readiness 派生查询只有设计无代码（P1，可先用上面三条直接查） |

### N8 收款开通项生效（系统）

| 项 | 内容 |
|---|---|
| 目的 | 让运营在流程里看到"门禁结果"，并把未满足的前置指回对应节点 |
| 谁让开通项生效 | **commercial**（不是 workflow）：在合同 ACTIVE、以及资格 / 渠道身份 Owner 发布变更事件时，事务外查询前置 ①②④，满足后短事务置 ENABLED，发布 `AgreementCapabilityStatusChanged`（新）。承保、账户、结算控制、保证金不进门禁（运行期检查） |
| 完成条件 | 合同内全部支付方式开通项 = ENABLED |
| 卡片上的"现在" | 逐开通项列出：哪条前置未满足 → 对应节点 → 当前处理人 |
| 兜底 | commercial XXL-Job 定时重查 PENDING_PREREQUISITES；运营在合同页可点"重新检查前置" |
| 限额档位联动 | 开通项首次 ENABLED 时，若 Client 无限额档位，Risk 设为 VERIFICATION（由 Risk 消费 `AgreementCapabilityStatusChanged`） |

### N9 生产交易验证

| 项 | 内容 |
|---|---|
| 目的 | 用真实生产链路证明：下单 → 支付 → Webhook → 退款 → 对账，全部通 |
| 类型 | 运营做 · `TECH_SUPPORT`，Client 开发者配合 |
| 做什么 | 指导 Client 用真实卡做一笔小额（验证档额度内）支付并全额退款；在节点表单录入 Order ID / Payment ID / Refund ID |
| 系统核对（完成条件） | Order 属于该 Client 且已支付；Payment SUCCEEDED；Refund SUCCEEDED；该 Order 的 Webhook 投递 SUCCEEDED；T+1 后渠道对账已匹配（可选项，见 Q-7） |
| C1 变体 | Partner OE × MoR 时可用平台测试卡完成（Q6） |
| 失败处理 | 任一核对失败，卡片显示失败项与对应排查入口（订单详情、支付详情、投递日志） |
| 现状 | 各事实已有；表单节点已有；核对查询需登记（P1） |

### N10 上线复核

| 项 | 内容 |
|---|---|
| 目的 | 放量前最后一次人工确认：所有上线事实齐全、风险可接受 |
| 类型 | 人判断；平台运营提交、风控批准（四眼） |
| 去哪做 | 风控"限额档位变更"页（新）：从 VERIFICATION 申请切到 OBSERVATION |
| 复核清单（页面自动带出） | 资格与 CRR、承保结论、合同与价格、开通项状态、渠道报备、技术接入、生产验证记录、未处理事项（筛查待复核、证件到期等） |
| 分档 | CRR = LOW：风控单人批准；MEDIUM / HIGH：风控负责人批准 |
| 产出事实 | 档位变更记录（申请人、批准人、依据）；限额档位 = OBSERVATION |
| 完成条件 | 限额档位 ∈ {OBSERVATION, STANDARD} |
| 出口 | **出口 A：收款上线**；通知 Client"已上线"，通知商务、渠道运营 |

### N11 观察期

| 项 | 内容 |
|---|---|
| 类型 | 等待；跟进人：风控 |
| 规则 | 默认 30 天或前 N 笔（Q8），按 CRR 调整；期间首批交易进入人工复核队列（risk-decision 规则，已有引擎） |
| 结束方式 | 到期时风控在档位页一键切 STANDARD（系统提示到期）；发现异常可延长或切 RESTRICTED |
| 完成条件 | 限额档位 = STANDARD |
| 之后 | 实例在出口 B 也完成时 COMPLETED；Client 转入持续监控（定期复审、证件到期、持续筛查，单独需求） |

## 8. 跨步骤场景与异常

| # | 场景 | 处理 |
|---|---|---|
| 8.1 | Client 撤回申请 | 案件 WITHDRAWN → N1 走"未通过"结束；已发起的其他节点 CANCELLED |
| 8.2 | 流程中资格被暂停 / 撤销 | compliance 发 `QualificationChanged` → commercial 把相关开通项置 SUSPENDED；流程不自动终止，看板标红并通知合规组长决定终止或等待 |
| 8.3 | 合同被取消重签（换 OE / 换模式） | 已完成的 N5 不自动作废；N8 会因前置 ④ 不满足而卡住并指出原因；运营使用"回退到节点"（W11）回到 N3，模板按新变量重新分支；旧子商户登记由渠道运营关闭 |
| 8.4 | Client 最终不签（N4 停滞） | 跟进人超时升级给商务主管；决定放弃时商务取消合同，合规组长终止实例；已报送的子商户由渠道运营撤回 |
| 8.5 | 上线后前置失效（MID 停用、账户失效、子商户被关） | Owner 发变更事件 → commercial 将开通项 SUSPENDED；不新建上线流程，进入"开通项异常"待办（运营做：修复后系统自动恢复 ENABLED） |
| 8.6 | 上线后追加支付方式 / 出款通道 / 新合同 | 不重走客户上线；使用第二个模板"开通变更"（业务关联键 `AGREEMENT:{agreementId}:CAP:{capabilityId}`），只含 N5（按新方式）→ N8 → 需要时 N9 |
| 8.7 | 资格到期复审 | 持续监控需求负责；复审逾期按 8.2 处理 |
| 8.8 | 节点负责人离职 / 休假 | 认领任务可转交；队列任务自动回到待认领；跟进人可由组长改派 |
| 8.9 | 模板改版 | 新版本只对新实例生效；运行中实例保持原版本；"人工确认 → 自动核对"的节点替换通过发新版本完成 |
| 8.10 | 外部 PayFac 不作为 | 首期不适用（平台代操作）；Partner 门户自审上线后，N1 改为"等外部"节点，有 SLA，超时通知平台合规组长并按 PayFac 协议升级 |
| 8.11 | 数据查询失败 / 延迟 | 只算"尚未满足"，不报错不阻塞；连续失败超过阈值告警 Platform 运维 |

## 9. 系统结合设计

### 9.1 结合方式总则

| 交互 | 方式 | 理由 |
|---|---|---|
| 开通项门禁查前置 | commercial **同步查询** compliance 资格、payment 渠道身份两个就绪接口（事务外），本地短事务置位；只查 ①②④ | 执行开关必须以 Owner 的实时事实为准；其余条件在执行点已有检查，不做双份 |
| 前置变化通知门禁 | Owner 发 **MQ 事件**，commercial 消费后重查（不信任事件里的结论，只当"该重查了"） | 事件可能乱序，重查保证正确 |
| workflow 判断节点完成 | **data-query 只读查询** 各 Owner 已登记数据源 | 2026-10-22 决定：data-query 只服务运营查询与流程验证；不进业务执行 |
| workflow 重查时机 | XXL-Job 定时（默认 5 分钟）+ 卡片"立即检查" | 不让 Owner 依赖 workflow；以后可加事件提醒加速 |
| 页面跳转 | 节点配置路由模板 + 变量映射 → Owner 前端页面（带参数） | 节点只引导，不代办 |
| Client 门户清单 | client-console-bff 同步聚合各 Owner 查询 | Client 看不到内部队列与流程 |
| 运营上线进度 | operations-console-bff 聚合：workflow 实例 + 开通项 + Owner 摘要 | BFF 负责页面组装（AGENTS.md） |

### 9.2 事件清单

| 事件 | 发布方 | 消费方 | 用途 | 现状 |
|---|---|---|---|---|
| `ClientReviewResult` / `QualificationChanged` | compliance | client-master、commercial（新消费） | Client ACTIVE；开通项重查 / 暂停 | 已有发布；commercial 消费新增 |
| `UnderwritingDecided`（新） | risk-decision | （暂无消费方；档位初始化读本库承保记录） | 预留给通知 / 看板 | 本期不发 |
| `ClientLimitTierChanged`（新） | risk-decision | client-console（通知）、ops | 上线 / 放量通知 | 新 |
| `AgreementStatusChanged` | commercial | 已有消费方 | 合同生效 | 已有 |
| `AgreementCapabilityStatusChanged`（新） | commercial | risk-decision（初始化 VERIFICATION 档）、checkout 缓存、通知 | 开通项生效 / 暂停 | 新 |
| `SubMerchantBindingChanged`（新） | channel-connector | commercial | 前置 ④ 重查 | 新 |
| `ChannelRegistrationChanged`（新） | channel-connector | commercial | 前置 ④ 重查 | 新 |
| `ChannelMidChanged` | payment | commercial（新消费） | 前置 ④ 重查 | 待核现有事件 |
| `BeneficiaryAccountVerified / Disabled`（新） | payout | client-console（通知） | 出款就绪通知；Manual Payout 检查点同步读账户状态，不靠事件 | 新 |
| `SettlementControlChanged` | compliance | payout | 结算控制变化通知；检查点同步读 | API 合同有，Processor 无 |

### 9.3 同步就绪查询（门禁用）

| 前置 | 查询 | Owner | 现状 |
|---|---|---|---|
| ① 资格 | `POST /compliance/v1/qualifications/evaluations`：返回 `eligible / status / validTo`，实时判定有效期并检查结算冻结；只允许 commercial-service 调用 | compliance | **已有**，commercial 已在 Checkout 解析调用；门禁零改动 |
| ② 合同 / 价格 | 本库 | commercial | 有 |
| ④ 渠道身份 | `GET /channel-identities/v1/readiness?entityNo&paymentMethod&currency&mcc`（新）：该 OE 下存在 ACTIVE `t_channel_mid`，其 ENABLED `t_mid_capability` 覆盖支付方式 × 币种 且 `mcc_code` 包含 Client MCC，且 MID 在生效路由策略内；M3 再并入子商户绑定 / 登记事实 | **channel-connector**（MID 模型 2026-10-20 已迁入该库） | 新接口，落现有表 |

**运行期检查（不进门禁）**：③ 承保 / 额度由 risk-decision 在结账与支付的既有 Risk 调用点按限额档位判定（无承保记录 = 额度为零）；⑤ 收款账户、⑥ 结算控制、⑦ 保证金由 Manual Payout 四个 Risk 检查点同步读取 payout / compliance / finance。这些 Owner 不需要为门禁提供额外就绪接口。

### 9.4 流程完成条件查询（data-query 登记）

每个节点一条只读查询，登记在 data-query，参数为流程变量，返回 `satisfied` + 输出变量 + `missing[]` 文案键（前端多语言）。

**口径归 Owner**：每条查询的 SQL 模板由对应 Owner 在自己的需求里交付和维护（视为 Owner 对外契约之一，与其状态机同步变更），data-query 只负责登记与执行；Owner 改表结构或状态机时必须同时改模板，验收时与 Owner 页面展示的状态对照测试。Platform 不自行编写或修改这些模板。

| 查询编码 | 节点 | 数据源 | 返回 |
|---|---|---|---|
| `GOLIVE_CASE_APPROVED` | N1 | compliance | satisfied、crr、mcc、ddTier、案件状态 |
| `GOLIVE_UNDERWRITING_APPROVED` | N2 | risk-decision | satisfied、requireDeposit、depositBlocksCollection、highRiskMcc |
| `GOLIVE_AGREEMENT_DRAFTED` | N3 | commercial | satisfied、agreementId、businessMode、operatingEntityNo、capabilities、localMethods |
| `GOLIVE_AGREEMENT_ACTIVE` | N4 | commercial | satisfied、签署状态 |
| `GOLIVE_SUB_MERCHANT_SUBMITTED / GOLIVE_SUB_MERCHANT_ACTIVE` | N5a / N5b | channel-connector | satisfied、绑定状态、拒绝原因 |
| `GOLIVE_MID_READY` | N5c | payment | satisfied、缺 MID 的支付方式 |
| `GOLIVE_CHANNEL_REGISTRATION_APPROVED` | N5e / N5f | channel-connector | satisfied、类型 |
| `GOLIVE_DEPOSIT_RECEIVED` | N6a | finance + risk-decision | satisfied、差额 |
| `GOLIVE_BENEFICIARY_SUBMITTED / GOLIVE_BENEFICIARY_VERIFIED` | N6b / N6c | payout | satisfied |
| `GOLIVE_INTEGRATION_READY` | N7 | Platform | satisfied、凭据 / 端点 / 投递 |
| `GOLIVE_COLLECTION_CAPABILITIES_ENABLED` | N8 | commercial | satisfied、未满足前置清单 |
| `GOLIVE_PRODUCTION_VERIFIED` | N9 | acquiring、payment、Platform | satisfied、失败项 |
| `GOLIVE_LIMIT_TIER` | N10 / N11 | risk-decision | 当前档位 |

### 9.5 逐系统变动点

| 系统 | 变动点 | 类型 | 优先级 |
|---|---|---|---|
| **client-master** | 业务信息补采：期望支付方式、退款政策链接、交付方式 | 字段 + 门户表单 | P1 |
| | 登记 data-query 数据源（申请、Client 只读视图） | 配置 | P0 |
| | 真实筛查供应商接入 | 渠道 | P0（生产前） |
| | 结算控制 Processor + `SettlementControlChanged` | 代码 | P1 |
| **risk-decision** | 承保记录（表、接口、承保页、自动承保规则）+ `UnderwritingDecided` | 表 / 接口 / 页面 / 事件 | P0 |
| | 限额档位（VERIFICATION / OBSERVATION / STANDARD / RESTRICTED）、按档位的单笔 / 日 / 月额度，接入现有规则引擎；档位变更四眼页面 + `ClientLimitTierChanged` | 表 / 规则 / 页面 / 事件 | P0 |
| | 最低保证金要求配置入口 | 页面 | P1 |
| | 消费 `AgreementCapabilityStatusChanged` 初始化 VERIFICATION 档 | 消费者 | P0 |
| **commercial** | 开通项 `PENDING_PREREQUISITES` + 门禁（事务外查 ①②④、短事务置位）+ 重查 Job + "重新检查前置"操作 | 状态 / Processor / Job | P0 |
| | 消费前置事件 → 重查 / SUSPENDED；发布 `AgreementCapabilityStatusChanged` | 消费者 / 事件 | P0 |
| | 合同页：C1 MCC 覆盖提示、承保建议破标提示 | 校验 / 页面 | P1 |
| | 门户自助签署（合同 + 价格附件） | 接口 / 门户页 | P1 |
| | 存量数据：已 ENABLED 的开通项保持 ENABLED，不回溯（迁移说明） | 迁移 | P0 |
| **channel-connector** | 子商户绑定（表、登记页、报送编号、回执录入）+ 事件 | 表 / 页面 / 事件 | P0（C2） |
| | 渠道登记事实（高风险、本地方式、3DS、钱包域名）+ 事件 | 表 / 页面 / 事件 | P1 |
| | 交易报送子商户标识（R15） | 渠道报文 | P0（C2） |
| **channel-connector** | 渠道身份就绪查询（MID + 能力 + 路由策略；M3 并入子商户 / 登记事实） | 接口 | P0 |
| **payout** | 收款账户（Vault 保护）+ Maker / Checker 核验页 + 就绪查询 + 事件；Manual Payout | 新服务 | P0（S-D） |
| **finance** | 入驻保证金到账确认 → `CLIENT_SECURITY_DEPOSIT` 入账；余额查询 | 流程 / 页面 | P1 |
| **Platform workflow** | W1 按钮发起防重；W2 `DATA_QUERY` 完成条件 + 输出变量 + 定时重查 + 立即检查；W3 等待节点（跟进人、已等时长、催办）；W4 队列指派与认领、转交；W5 任务卡"做什么 / 完成标准 / 现在"；W6 SLA 与超时提醒 / 升级；W7 按业务关联键反查；W11 回退到指定节点（新） | 引擎 / 模型 / 接口 | W1–W5 P0，W6–W7、W11 P1 |
| **Platform data-query** | 登记上表各数据源与 13 条查询 | 配置 | P0 |
| **Platform Access Control** | 新增角色 / 队列：`COMPLIANCE_REVIEW`、`COMPLIANCE_DECIDER`、`RISK_UNDERWRITING`、`RISK_LEAD`、`COMMERCIAL`、`CHANNEL_OPS`、`FINANCE_OPS`、`TECH_SUPPORT`；权限"发起 / 终止上线流程" | 配置 | P0 |
| **Platform notification / i18n** | 催办、超时、上线通知模板；新页面文案键 | 配置 | P0 / P1 |
| **operations-console-bff / web** | 发起按钮（三处）；待办增强（三页签、任务卡）；Client 详情"上线进度"；上线看板；节点深链参数接收 | 页面 / 聚合 | P0 / P1 |
| **client-console-bff / web** | "上线清单"（缺什么、谁处理、影响哪项能力）；签署页；收款账户页；保证金指引 | 页面 / 聚合 | P0 / P1 |
| **partner portal** | 首期无改造（Q-10）。后续另立项：PayFac 合规人员在 Partner 门户处理案件、查看其 Client 上线进度、接收待办 | — | 后续 |

## 10. 页面规划

| 端 | 页面 | 新 / 增强 | 关键内容 |
|---|---|---|---|
| 运营 | 入驻审核列表 | 增强 | "待发起"筛选、已提交天数、发起按钮、流程状态列 |
| 运营 | 我的待办 | 增强 | 页签：待我处理 / 待认领（按队列）/ 我跟进的等待；任务卡四要素；立即检查；转交 |
| 运营 | Client 详情 · 上线进度 | 新 | 横向阶段条、出口 A / B 状态、开通项清单（还差什么 → 节点 → 谁 → 已等）、时间线 |
| 运营 | 上线看板 | 新 | 首列"已提交未发起"，其余按节点分列，超 SLA 标红，按队列 / 国家 / 模式筛选 |
| 运营 | 承保页、限额档位页 | 新（risk） | 带出尽调与业务信息；四眼 |
| 运营 | 子商户登记页、渠道登记页 | 新（channel-connector） | 预填、报送编号、回执 |
| 运营 | 收款账户核验页 | 新（payout） | Maker / Checker |
| 运营 | 合同页 | 增强 | 开通项新状态与"还差什么"、重新检查前置、破标提示 |
| Client | 上线清单 | 新 | 按"收款上线 / 出款就绪"两组列事项：待你处理（签署、账户、接入、验证）/ 平台处理中（不暴露内部人员） |
| Client | 签署页、收款账户页 | 新 | — |

## 11. 通知

| 触发 | 接收 | 渠道 | 发送方 |
|---|---|---|---|
| 申请提交超 2 工作日未发起 | 合规组长 | 站内 + 邮件 | ops 定时检查（workflow 外） |
| 队列任务产生 | 队列成员 | 站内 | workflow |
| 节点超 SLA / 等 Client 超时 | 跟进人；再超时升级组长 | 站内 + 邮件 | workflow W6 |
| 催办 | Client 管理员 | 邮件 + 门户站内 | workflow → Platform notification |
| 需补件 / 审核结果 | Client | 邮件 + 门户 | client-master（已有） |
| 待签署 / 待添加账户 / 待缴保证金 | Client | 邮件 + 门户 | 对应 Owner 事件 |
| 收款上线 / 观察期结束 | Client、商务 | 邮件 + 门户 | risk-decision 档位事件 |
| 开通项被暂停 | 商务、渠道运营、Client | 站内 + 邮件 | commercial 事件 |

## 12. 权限与职责分离

| 动作 | 权限 | 四眼 |
|---|---|---|
| 发起 / 终止上线流程 | 合规组长、运营主管 | 终止需原因 |
| 尽调决定 | `COMPLIANCE_DECIDER` | 是（已有） |
| 人工承保 | `RISK_UNDERWRITING`；HIGH 需 `RISK_LEAD` | HIGH 是 |
| 价格审批 | commercial 审批人 | 是（已有） |
| 登记客户签署（过渡期） | 商务 | 需上传已签文件 |
| 子商户 / 登记事实录入回执 | `CHANNEL_OPS` | 否（有收单行回执为证） |
| 收款账户核验 | `FINANCE_OPS` | 是（Maker / Checker） |
| 限额档位变更 | 申请：运营；批准：风控 | 是 |
| 回退到节点 | 运营主管 | 需原因 |

## 13. 非功能要求

| 项 | 要求 |
|---|---|
| 一致性 | 执行以开通项与限额档位为准；流程显示允许最多一个重查周期（默认 5 分钟）的延迟，"立即检查"即时 |
| 幂等 | 发起按业务关联键；门禁置位按开通项版本 CAS；事件消费按事件 ID |
| 审计 | 每个人工动作在 Owner 系统留不可变记录；流程实例保存节点完成时刻与当时查询结果快照 |
| 隐私 | 流程变量只放 ID 与枚举，不放证件号、账户号等 PII；卡片展示的敏感信息由 Owner 页面按权限脱敏 |
| 多语言 | 节点名称、指令、missing 文案均为文案键，走 Platform 多语言 |
| 性能 | 在途实例 ≤ 1,000、每实例待查节点 ≤ 6 时，每 5 分钟 ≤ 6,000 次只读查询；data-query 按数据源限流 |
| 可观测 | 节点耗时、超时、查询失败率指标；看板数据可导出 |

## 14. 需求拆分与路线图（2026-10-28 细化）

配图：[client-activation.pen](client-activation.pen) 画板 06（车道图 + 按里程碑的需求清单）。

### 14.1 拆分原则

1. **一个需求一个主系统**：每个需求由一个会话、一个主 Owner 交付；跨系统只到"对方提供一个接口 / 消费一个事件"为止，对方改动作为该需求的变动点列明。
2. **契约先行**：跨需求共用的事件、就绪查询、完成条件查询的形状，在 REQ-00 一次定下，后面各需求并行实现时不再互相等待。
3. **每个需求独立可验收**：交付后在 mor-test 上能演示一个业务结果，而不是"做了一半的框架"。
4. **大小控制**：每个需求按八阶段走完不超过约 1–2 周；超过的继续拆（R1 → REQ-01/02，R2 → REQ-03/04，R3 → REQ-05/06/09，R4 → REQ-07/08）。
5. **门禁只管三项**：commercial 门禁固定核对 ①②④，按"前置检查器"组织（资格、合同价格、渠道身份）；④ 在 M1 只覆盖 MID / MCC，M3 再加子商户与登记事实。其余条件在各 Owner 运行期检查，不进门禁，不做双份逻辑。
6. **完成条件查询归 Owner**：流程节点的 `DATA_QUERY` SQL 模板由 Owner 交付与维护，和其状态机同步变更；data-query 只登记与执行。
7. **M1 的 workflow 只补最小集**：按钮发起、数据完成、队列认领与转交、四眼排除、跟进人、交接备注；粘性分配、认领上限、不在岗、沟通记录、SLA 暂停等放 M2 之后。

### 14.2 需求清单

规模：S ≈ 3–5 天，M ≈ 1–2 周，L ≈ 2 周以上（L 级在阶段 1 再拆）。

| 编号 | 目录（登记时） | 内容 | 主系统 · 其他变动点 | 规模 | 依赖 | 交付后可演示 |
|---|---|---|---|---|---|---|
| **REQ-00** | `agent-docs/decisions/` 决定 + 契约附件 | 登记"客户上线"决定（Q1–Q9、Q-1–Q-10、复核调整）；定事件载荷、两个就绪查询、完成条件查询的统一形状与 Owner 维护责任 | 文档 | S | — | 各需求可并行开工 |
| **REQ-01** | `commercial-开通项前置门禁` | 开通项 `PENDING_PREREQUISITES`；前置检查器框架；接入 ① 资格（复用已有 `QualificationEvaluationApi` 客户端）、② 合同价格；重查 Job + "重新检查前置"；消费 `QualificationChanged`（暂停 / 重查）；发布 `AgreementCapabilityStatusChanged`；合同页显示"还差什么"；存量不回溯；交付 `GOLIVE_AGREEMENT_DRAFTED / agreement_active / collection_capabilities_enabled` 三条查询模板 | commercial · ops-web 合同页（compliance 零改动） | M | REQ-00 | 新开通项在资格 / 签署未完成时不生效；资格撤销自动暂停 |
| **REQ-02** | `channel-connector-渠道身份就绪查询` | OE × 支付方式 × 币种 × MCC 就绪查询，落现有 `t_channel_mid / t_mid_capability / t_routing_policy_mid`（预留子商户 / 登记事实位）；commercial 接入前置 ④；合同页 C1 MCC 提示；阶段 1 核实：OE 下 MID 是否默认进入路由策略 | channel-connector · commercial | S | REQ-01 | 无 MID 或 MCC 不覆盖的支付方式不生效 |
| **REQ-03** | `risk-decision-承保记录` | 承保记录（表、页面、四眼、修订版本）；自动承保规则（阈值配置）；交付 `GOLIVE_UNDERWRITING_APPROVED` 查询模板 | risk-decision · ops-web | M | REQ-00 | 低风险自动承保；承保结论可追溯 |
| **REQ-04** | `risk-decision-限额档位` | **名单 + 分支方案**：`list_purpose = TIER` 的三张档位名单（VERIFICATION / OBSERVATION / STANDARD）+ RESTRICTED；决策流按名单 Feature 分支，各分支绑各自限额版本，不在任何档位名单 → 拒绝；消费开通项事件把 Client 加入 VERIFICATION；档位变更页（即上线复核页，带复核清单；四眼由 workflow T26 / T27 保证，Risk 记 `created_by`）；`ClientLimitTierChanged`。不新建档位表、不改限额绑定解析 | risk-decision · ops-web | M | REQ-01、REQ-03 | 新户只有验证额度；复核批准后放量 |
| **REQ-05** | `platform-support-workflow-按钮发起与数据验证` | W1 按模板编码 + 业务关联键发起防重；W2 `DATA_QUERY` 完成条件、输出变量、网关按变量分支、定时重查、"立即检查"、查询失败按未满足 | Platform workflow · data-query | M | REQ-00 | 节点由数据自动完成 |
| **REQ-06** | `platform-support-workflow-队列与等待节点` | W3 等待节点（跟进人、已等时长、催办通知）；W4 队列指派、认领、转交；W12 复核任务排除前序执行人（四眼）；实例级"上线负责人"变量；W5 任务卡"做什么 / 完成标准 / 现在" + 交接备注；Access Control 新角色。**不含**粘性分配、认领上限、不在岗、沟通记录、SLA 暂停 | Platform workflow · Access Control · notification | M | REQ-05 | 待认领、等 Client 卡片、催办、四眼不同人 |
| **REQ-07** | `operations-console-客户上线发起与待办` | 模板 v1 配置与 13 条查询登记（已就绪的接自动，未就绪的为人工确认）；三处发起按钮；"待发起"筛选与滞留提醒；我的待办三页签与任务卡 | ops-bff / ops-web · data-query 配置 · workflow 模板 | M | REQ-05、REQ-06 | **M1 验收主场景**：新运营从发起到 N10 全程按待办推进 |
| **REQ-08** | `operations-console-上线进度与看板` | Client 详情"上线进度"（阶段条、出口 A / B、开通项还差什么 → 节点 → 谁）；上线看板（首列未发起、超 SLA 标红、筛选） | ops-bff / ops-web | M | REQ-01、REQ-05 | 主管看哪里堵了 |
| **REQ-09** | `platform-support-workflow-时限与回退` | W6 SLA（运营工作日历、"等待中"暂停）、超时提醒与升级；W7 按业务关联键反查；W11 回退到指定节点（原因、审计）；"开通变更"模板（8.6）；粘性分配、认领上限、不在岗、沟通记录 | Platform workflow | M | REQ-06 | 超时升级；换合同可回退；分配更顺手 |
| **REQ-10** | `client-console-上线清单` | 门户"上线清单"（收款上线 / 出款就绪两组；待你处理 / 平台处理中）；client-bff 聚合各 Owner | client-bff / client-web | M | REQ-01–04 | Client 自己看到还差什么 |
| **REQ-11** | `commercial-门户自助签署` | 门户查看合同与价格附件并签署（签署人、时间、IP、UA）；运营代登记保留 | commercial · client-bff / web | M | REQ-10 | 免运营代登记 |
| **REQ-12** | `client-master-业务信息补采` | 期望支付方式、退款政策链接、交付方式；承保页带出 | client-master · client-web | S | — | 承保输入完整 |
| **REQ-13** | `channel-connector-子商户登记` | 子商户绑定（登记页、预填、报送编号、回执）；`SubMerchantBindingChanged`；接入就绪查询 | channel-connector · payment | M | REQ-02 | C2 新开通项可生效 |
| **REQ-14** | `channel-connector-交易报送子商户标识` | 渠道报文携带子商户标识（R15），按渠道适配 | channel-connector | M | REQ-13 | C2 交易合规报送 |
| **REQ-15** | `channel-connector-渠道登记事实` | 卡组织高风险、本地方式（mada）、3DS、钱包域名登记事实；`ChannelRegistrationChanged`；接入就绪查询 | channel-connector · payment | M | REQ-02 | 高风险行业 / mada 可上线 |
| **REQ-16** | `payout-收款账户与核验` | payout 服务骨架；收款账户（Vault）；Maker / Checker 核验页；账户状态查询（供 Manual Payout 检查点与 `GOLIVE_BENEFICIARY_*`）；门户账户页 | payout（新服务）· client-web · ops-web | L | REQ-00 | 出款运行期检查 ⑤ 有数据可读 |
| **REQ-17** | `compliance-结算控制` | 结算控制 Processor、事件；Manual Payout 检查点读取 | compliance · payout | S | — | 出款运行期检查 ⑥ |
| **REQ-18** | `finance-入驻保证金` | Risk 最低保证金要求入口；到账确认、入账、余额查询；Manual Payout 检查点读取 | finance · risk-decision | M | REQ-03、REQ-16 | 出款运行期检查 ⑦ |
| **REQ-19** | `compliance-真实筛查供应商` | 真实名单筛查替换 Mock | compliance · channel-connector | M | — | 生产前提 |
| — | 另立项 | Manual Payout 全链路；持续监控；Client 沙箱；税务资料；**Partner 门户 PayFac 自审与上线进度**（Q-10） | — | L | — | — |

### 14.3 里程碑与并行车道

```
           周 1        周 2        周 3        周 4        周 5        周 6
契约   ── REQ-00 ──┐
commercial · cc    ├─ REQ-01 ──────┬─ REQ-02 ─┐                  ┌─ REQ-11 ───
risk               │               └─ REQ-03 ──────┬─ REQ-04 ─────┤
workflow           ├─ REQ-05 ──────┬─ REQ-06 ──────┴──────────────┼─ REQ-09 ───
ops-console        │               │              ├─ REQ-07 ──────┼─ REQ-08 ───
client-master      └─ REQ-12 ─┘    │              │               │
client-console                     │              │               └─ REQ-10 ───
                                   ▼              ▼               ▼
                                              M1 可控上线        M2 双方同屏
channel-connector（M3）：REQ-13 → REQ-14，REQ-15 可并行，REQ-02 之后任意时间开始
payout / compliance / finance（M4）：REQ-16 → REQ-18；REQ-17 独立；REQ-19 生产前完成
```

| 里程碑 | 需求 | 业务结果 | 验收场景 |
|---|---|---|---|
| **M1 可控上线** | REQ-00–07、REQ-12 | C1 / C3 新客户按流程上线；交易被开通项门禁与限额档位保护 | 新运营只看待办，把一个 C3 Client 从"待发起"推进到 OBSERVATION；中途撤销资格，开通项自动暂停 |
| **M2 双方同屏** | REQ-08–11 | 主管看板、超时升级、回退；Client 门户清单与自助签署 | Client 只看门户完成签署与接入；某节点超时升级到组长 |
| **M3 C2 与本地方式** | REQ-13–15 | PayFac 子商户模式、mada、高风险行业可上线 | 一个 C2 Client 从子商户登记到收单行生效全程自动推进 |
| **M4 出款就绪** | REQ-16–18（+ Manual Payout 另立） | 出口 B 走通 | 账户核验通过、保证金到账后出款开通项生效 |
| **生产前** | REQ-19 | 可接真实客户 | — |

### 14.4 M1 的取舍

- **M1 只覆盖 C1 / C3。** C2 的前置 ④ 要等 REQ-13 的子商户事实；在此之前，C2 新开通项会停在 PENDING_PREREQUISITES（存量不受影响，Q-8）。如业务需要 C2 新客户更早上线，把 REQ-13 提前到 M1，与 REQ-03 并行。
- **M1 中未就绪的节点用"人工确认 + 证据"**：N5a / N5b / N5e / N5f、N6 全部、N9 的系统核对。人工确认只推进流程，不影响门禁（门禁里对应前置要么已接入，要么该开通项不会生效）。
- **REQ-07 是 M1 的验收主场景**，所以它排在最后，前面各需求交付时都要提供能被它接入的查询。

### 14.5 下一步

1. 登记 REQ-00 决定与契约附件。
2. REQ-01（commercial）、REQ-05（workflow）、REQ-12（client-master）三条车道同时进入阶段 1 需求评审；各自的 `requirement.md` 按"逐系统变动点"写全。

## 15. 已定问题（2026-10-28 用户：按建议）

| 编号 | 问题 | 结论 |
|---|---|---|
| Q-1 | 流程实例粒度：每个 Client 一个"客户上线"实例 + 后续用"开通变更"实例（8.6），还是每个合同一个实例 | **已定**：前者：首次上线含尽调，按 Client；之后的增量按合同 / 开通项 |
| Q-2 | 引入 Risk"限额档位"作为放量开关，让 N10 复核在 Risk 页面完成 | **已定**：同意；否则复核只能是形式（workflow 不能做开关） |
| Q-3 | 自动承保的阈值（月流水上限、排除的 MCC） | **已定**：风控给出初值，配置化 |
| Q-4 | 渠道报备是否与签署并行（N5 不等 N4） | **已定（2026-10-28 修订）**：内部准备并行，**对外报送在合同 ACTIVE 之后**（收单行 / 卡组织要求已签署的子商户协议）；C3 MID 录入与 C1 MCC 核对可并行 |
| Q-5 | 出款就绪是否作为收款上线的前提 | **已定**：不作为（原则 6）；出款前置在出款开通项上控制 |
| Q-6 | 上线复核的批准人分档（LOW 单人 / MEDIUM-HIGH 负责人） | **已定**：同意 |
| Q-7 | 生产验证是否要求 T+1 对账匹配后才算完成 | **已定**：首期不要求（会拖慢一天）；对账异常由对账流程处理 |
| Q-8 | 存量 Client（已 ACTIVE、开通项已 ENABLED）是否补建流程 | **已定**：不补建；开通项保持现状，仅对新增开通项启用门禁；可选一次性巡检报告 |
| Q-9 | M1 首个需求从哪个开始登记 | **已定**：先登记 REQ-00 契约，再由 REQ-01（commercial）、REQ-05（workflow）、REQ-12（client-master）三条车道并行进入阶段 1；REQ-03 / REQ-04 紧随 REQ-01（见 14.3） |
| Q-10 | Partner 门户是否在本期改造（PayFac 自审） | **已定**：本期不改；入驻审核首期全部由平台运营代操作，Partner 门户自审另立项。**前提（2026-10-28 已定，选方案 1）**：外部（非 AFFILIATED）PayFac 的 Client 由平台代操作需该 PayFac 书面委托（2026-10-27 入驻决定只允许平台代 AFFILIATED PayFac 操作），首期只为 AFFILIATED PayFac 与 C3 直连发放入驻码；REQ-13 不提前，仍在 M3。内部运营作业线路见 [ops-line.md](ops-line.md) |

## 16. 阶段 1 前已核实的三件事（2026-10-28）

| 事项 | 核到的事实 | 对规划的影响 |
|---|---|---|
| compliance 资格查询 | `QualificationEvaluationApi` 已有，返回 `eligible / status / validTo`，commercial 已在 Checkout 解析调用 | REQ-01 不依赖 compliance；门禁 ① 零改动 |
| Risk 能否按 Client 分档 | 限额阈值在 `t_limit_version` 上，不在主体上；`t_risk_list / t_risk_list_entry` 支持 CLIENT 主体、人工加入、生效区间，可做 Feature 供决策流分支 | REQ-04 走“档位名单 + 分支”，保持 M；档位正规化（按主体覆盖阈值）留待真有逐 Client 阈值需求时再立项 |
| MID 能否按 OE × 支付方式查覆盖 | `t_channel_mid`（含 `entity_no`、状态）、`t_mid_capability`（支付方式、币种、国家、**mcc_code**）、`t_routing_policy_mid` 都在 **channel-connector** 库 | REQ-02 主系统改为 channel-connector，查询落现有表；C1 MCC 核对并入 ④，不另维护 OE MCC 清单；需核实 MID 是否默认进路由策略 |

## 17. 主要风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 范围大、跨 9 个系统 | 周期长、并行会话冲突 | 按里程碑交付；每个需求单系统为主；共享合同（事件、查询）先定 |
| payout 服务从零开始 | M4 晚 | 出款不阻塞收款上线（原则 6） |
| 门禁上线影响存量 | 误停已有客户 | Q-8：存量不回溯；上线前巡检报告 |
| data-query 查询与 Owner 状态口径不一致 | 流程显示与执行不一致 | 查询模板由 Owner 维护（9.4），Owner 改状态机必须同时改模板；验收时与 Owner 页面对照测试 |
| commercial 门禁与执行点双份逻辑 | 门禁放行、执行拒绝 | 门禁只查 ①②④；③⑤⑥⑦ 只在运行期检查（已收窄） |
| 外部 PayFac 不配合 | N1 停滞 | SLA + 升级；PayFac 协议约束 |
