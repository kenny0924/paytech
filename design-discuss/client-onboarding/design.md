# 客户入驻与资格（client-master + compliance）的职责与设计

> 状态：**讨论中（2026-10-26）**。本稿是 client-master（客户主档）与 compliance（合规）两个服务在"全球合规入驻 + KYC/KYB + 持续监控 + PayFac 合规服务"上的目标设计。讨论定稿后登记到 `agent-docs/decisions/`，实现与部署状态只记在 `agent-docs/CURRENT_IMPLEMENTATION.md`。配图：[client-onboarding.pen](client-onboarding.pen)（十一张画板：职责边界与责任链 / 两系统全景与合同 / 入驻与审核流程 / 状态机总表 / 走数例子 / client-master 实体关系 / compliance 实体关系 / 采集 Schema 驱动表单 / 三国国家包对照 / 角色 × 阶段 / 持续监控·PayFac·切片·已定事项）。
>
> 用户已在讨论中确认的方向（2026-10-26）：① compliance **不并入** client-master，两服务按一个"客户入驻与资格域"写设计；② C1 / C2 下 Client 的 KYC/KYB 由**平台提供系统、PayFac 自己审核决定**并按用量计费，不走"信赖第三方 KYC"路径；③ 首批辖区 **AE、SA、EG**；④ 首期**人工审核 + 名单筛查供应商**，IDV 作为后续标准 API 预留位置；⑤ 敏感字段进 Vault，清单逐项讨论（第 12 节）；⑥ C5 子商户 KYC 延后；⑦ 第二轮（同日）：Q1 入驻审核由 PayFac 决定（平台只提供系统）；Q4 Client 填 PayFac 发的**入驻码**而不是选择 Partner；Q8 AE/SA 不一定本地化部署；Q2/Q3/Q5–Q7/Q9–Q14 按第 16 节建议；第 10 节已按"过度设计"复核砍掉 4 张表与若干列。
>
> 调研材料：行业模型（Stripe Connect、Adyen LEM、Checkout.com Platforms、Rapyd、PayPal、Mangopay、Sumsub、Onfido、Jumio、Veriff、ComplyAdvantage、Ballerine、Visa/Mastercard PayFac 规则、FATF/EU AMLR）与三国监管（CBUAE / SAMA / CBE、UBO 法规、PDPL、主流 PSP 材料清单）的原始笔记见第 19 节来源；现有代码链路核对结果见第 4 节。

> **2026-10-27 变更（用户决定）：不做阿拉伯语。** Client 门户只有 en / zh-CN、不做 RTL；不采集阿拉伯文名称，下文 `local_*` 名称列、`name_script`、双文字姓名控件与阿语排版规则作废；将来如需本地文字名称按扩展属性加。

## 1. 一句话

**client-master 回答"你是谁、你填了什么"，compliance 回答"你够不够格、还够不够格"。** client-master 按辖区采集并保存法律身份、资料、关系人、角色与申请；compliance 按辖区 × 主体类型 × 角色 × 档次运行尽调程序，执行材料核验、外部检查、名单筛查、风险评级，形成不可变资格决定并持续复审；两者之间只经 HTTP 合同与 MQ 事件，不共享库。PayFac Partner 买到的是"按其程序运行的案件流水线 + 可给收单行看的决定与证据包"。

## 2. 为什么要做入驻（责任链）

```
监管（央行 AML/CFT 法，依据 FATF R.10 / R.11 / R.12）
   └─ 收单行（持牌，对监管负责）
        └─ PayFac（PF 协议：对收单行承诺子商户已尽调；Visa 5.3.1.1 / Mastercard SRP 7.1.2）
             └─ 平台（我们：把"尽调"做成系统并代为运行；D1 技术服务商）
                  └─ 商户（Client）与其关系人
```

三股压力同时作用：**AML/CFT**（身份、受益所有人、制裁/PEP）产生 KYC/KYB；**卡组织规则**（MCC、禁止行业、子商户 ID、USD 1M 直签门槛、MATCH/VMSS 查询）产生承保；**自身资金风险**（跑路、拒付、假货）产生保证金与持续监控。三国差异见第 9 节：SA 的 SAMA 明确要求 **PSP 自行签约并 KYC 商户、技术对接公司不得做 KYC**；AE 的 CBUAE 要求持牌机构核验 Emirates ID **必须经 ICP 在线网关**并留存数字核验；EG 的 CBE 要求支付促进者在埃及有实体办公室、银行监督其商户选择。

## 3. 行业借鉴（取什么、不取什么）

| 来源 | 可借鉴的模型 | 本设计采纳方式 |
|---|---|---|
| Stripe Connect `requirements{currently_due, eventually_due, past_due, pending_verification, current_deadline, disabled_reason, errors[], alternatives[]}` + `future_requirements` + 能力级 requirements、`awaiting_action_from`、`impact.restricts_capabilities` | "缺什么、何时到期、影响哪个能力、等谁动作"的需求清单型状态 | 第 8 节：`t_application_requirement` 投影 + `GET …/requirements`；首期只做申请级，不做能力级 |
| Stripe `relationship{owner, director, executive, representative, authorizer, percent_ownership}`；Adyen `entityAssociations.type: uboThroughOwnership / uboThroughControl / signatory / director`；Sumsub `subtypes: ownershipUbo / controlUbo / substituteUbo` | 显式区分"持股 UBO / 控制 UBO / 替代 UBO（高管）"，与 FATF INR.10 三层瀑布一致 | `t_party_relation` 增 `ubo_basis`（OWNERSHIP / CONTROL / SENIOR_MANAGING_OFFICIAL）与 `job_title`、`nominee_flag` |
| Stripe `ownership_declaration / directorship_declaration / representative_declaration {date, ip, user_agent}`、`tos_acceptance` | 证据式声明：谁在何时以何网络身份声明了什么 | `t_application_declaration`（所有权完整、董事完整、代表授权、平台使用条款、PEP 自述、信息真实） |
| Adyen 验证错误 `type / code / message / subErrors / remediatingActions`；Stripe `errors[{requirement, code, reason}]`；Checkout.com `requirements_due[{field, reason}]` | 机读错误码 + 补救动作，而不是自由文本退回 | 第 8.3 节错误码族 + `remediation_code`；退回不再只定位到分区 |
| 文件槽按用途命名（Stripe `documents.company_license / proof_of_ultimate_beneficial_ownership`；Adyen `proofOfOwnership / proofOfSignatory`）+ 签发时效（12 / 6 / 3 个月）+ 文件级拒绝原因（Mangopay `RefusedReasonType`、Stripe `details_code`） | 材料要求 = 用途 + 时效 + 可机读拒绝原因 | `t_dd_program_requirement` 增 `max_age_days`、`subject_role`；`t_dd_evidence_ref` 增 `reject_reason_code`、`issue_date`、`expiry_date` |
| Adyen 周期 data review（30/60 天期限，逾期停能力）；Checkout.com 周期复核 `requirements_due` 90 天；EU AMLR Art.26 高风险 ≤1 年、其余 ≤5 年 | 复审 = deadline 驱动，逾期有后果 | 第 7.6 节：复审案件 + `t_review_trigger`，逾期进入结算控制 |
| ComplyAdvantage `match_status: no_match / potential_match / true_positive / false_positive / unknown` + `risk_level` + 白名单 + `is_monitored` + `monitored_search_updated(new/updated/removed)`；Onfido watchlist monitor | 命中处置枚举与持续监控差异事件 | 复用 `t_screening_run/hit/resolution`，新增 `t_screening_monitor`；处置枚举对齐 |
| Onfido report `breakdown{data_comparison, data_validation, image_integrity, visual_authenticity, …}`；Jumio `decision{PASSED/WARNING/REJECTED/NOT_EXECUTED}`；Veriff `approved/declined/resubmission_requested` | 外部检查结果 = 总结论 + 分项 finding | `t_dd_check` 增状态/结论/原因；新增 `t_dd_check_finding` |
| Ballerine：`EndUser.variant: director/ubo`、`BusinessPosition[]`、工作流子流程（每个 UBO 一条子 KYC）、`Alert{severity, state}` | 机构案件依赖关系人 KYC 子案件；告警独立于案件 | 已有 `t_dd_case_dependency`；告警落 `t_review_trigger` |
| Visa 5.3.1 / PF Risk Guide；Mastercard SRP 7.1.2 | 子商户 ID、MCC 指派、MATCH/VMSS、principals 负面背景、USD 1M 直签、禁止签约其他 PF、保证金由收单行持有、尽调记录保留 | 第 14 节 PayFac 产品：案件记责任 PayFac、导出证据包；MATCH/VMSS 作为检查类型预留；USD 1M 门槛为 billing/commercial 侧告警（待定 Q9） |
| FATF R.10/11/12、EU AMLR Art.52 | UBO 25%（可降至 15%）、CDD 触发、PEP 四项措施、记录 ≥5 年 | 辖区包字段 `ubo_threshold_pct`、`record_retention_years`；PEP 流程第 7.4 节 |

**不采纳**：Stripe 的"验证通过前允许收款、按金额/时间阈值逐步要求资料"（payout_limit / trigger_limit）——我们是 PayFac 模式，收单行要求上线前完成尽调；Adyen/Stripe 的能力级（capability）需求清单——首期只按申请/角色，能力开通归 commercial 的开通项；Ballerine 的 JSON 自由上下文——我们保持结构化表。

## 4. 现状与差距（2026-10-26 只读核对）

| 能力 | 现在 | 目标 |
|---|---|---|
| 入驻资料 | 单页四分区（BASIC / BUSINESS / PEOPLE / MATERIALS）；必填项**写死**在 `ClientSubmissionValidator`；organization_type / identifier_type / business_model / industry 来自 client-master `application.yml` 的 `onboarding.form-catalog`，**无辖区维度**；address_type 写死 RESIDENTIAL / REGISTERED | 按 辖区 × 主体类型 × 角色（× 组织类型）解析的**采集 Schema**，目录入库、运营可维护、版本化 |
| 资料字段 | `given/middle/family_name`、`birth_date`、`nationality/residence_country`；机构 `legal/trade_name`、`incorporation_country/type/date`；地址西式六列 | 增本地文字姓名、出生地、性别、税务居民地、职业/资金来源、PEP 自述；机构增登记机关、执照到期、上市标记、本地名；地址增建筑号/附加号/区/PO Box/短地址与格式码；辖区特有事实走扩展属性 |
| 证件号 | `t_party_profile_identifier.identifier_value` **明文**；规范化值 `t_party_identity_key.normalized_identifier_value` 明文作唯一键；I01 明文发 compliance | 原值进 Vault（STABLE Token 兼作唯一键），库内只留 Token + 脱敏 |
| 关系人 | `OWNERSHIP / CONTROL / DIRECTOR / SIGNATORY / UBO` + 持股比例 | 增 UBO 依据（持股 / 控制 / 高管替代）、职务、代持标记、辖区门槛校验 |
| 批准后变更 | 资料批准后永久只读，**没有修订流程** | 修订申请（PROFILE_AMENDMENT）：新 Profile 行草稿 → 审核 → 替换当前，旧行保留；重大变更触发重核 |
| 材料要求 | 运行时读 compliance `MaterialRequirementsApi`（`t_dd_program_requirement`，按资格类型一个全局程序） | 程序增辖区 × 主体类型 × 角色 × 档次 × Owner；要求增主体角色、时效、拒绝原因 |
| 案件 | 表声明 9 态，代码只用 SUBMITTED / RETURNED / APPROVED / REJECTED（子任务 WITHDRAWN）；无状态机类；无撤回 | 完整状态机（含 IN_REVIEW / WAITING_EVIDENCE / EXPIRED）、案件类型（入驻 / 修订 / 定期复审 / 触发复审）、责任方与操作方 |
| 外部检查 / 筛查 | `t_dd_check`、`t_screening_*`、`t_dd_program_check/screening`、`t_dd_party_snapshot` **只有表，无代码**；channel-connector 标准 API 目录无 KYC 类 | 第 13 节：名单筛查、IDV、登记库、税号核验作为 SERVICE 渠道标准 API；结果落表；人工检查项同表 |
| 风险评级 / 档次 | 无 | 案件风险因子 → 评级 → 档次（SDD / CDD / EDD）→ 程序版本与复审频率 |
| 决定 / 资格 | `t_qualification_decision`、`t_role_qualification`（2026-10-24 后有读写、手工登记、`QualificationEvaluationApi` 供 commercial） | 增风险评级、档次、辖区、责任 PayFac、推荐人/决定人（四眼）；资格事件发布 |
| 持续监控 | `next_review_time` 等字段有，无任务 | 定期复审、名单更新重筛、资料变更触发、文件到期；XXL-Job 扫描 + compensation-event |
| 结算控制 | 三表 + API 合同，无 Processor（D38） | 实现创建/释放/过期与 `SettlementRestrictionChanged`；复审逾期可创建控制 |
| PayFac 合规服务 | 无 | 程序 Owner、案件责任方、Partner 可见与导出、按案件/检查/筛查计费 |
| 前端 | Client 门户入驻单页；Ops 有 `/client-onboarding/reviews`、`/compliance/qualifications`，**无 Client 列表/详情** | 动态表单（按 Schema 渲染）、运营案件工作台（检查/筛查/评级/决定）、Client 主档详情、复审队列、Partner 合规视图 |

## 5. 职责边界

### 5.1 client-master 拥有

1. **法律身份与资料**：Party、Profile（含辖区扩展属性、本地文字姓名、辖区地址格式）、法律标识（Vault Token + 唯一键）、联系方式、关系人与关系（含 UBO 依据）。
2. **角色根**：Client、Local Partner、Operating Entity 及其状态；角色索引。
3. **申请**：入驻申请、修订申请、Partner / OE 申请；材料引用（地址 + Hash + 签发/到期日）；声明记录；申请级**需求清单投影**。
4. **采集规则**：辖区包（`t_jurisdiction`）、组织类型目录、标识类型目录、采集 Schema 与字段规则、扩展属性定义。它回答"这个国家的这类主体要填什么、怎么校验格式"。
5. **资料变更事件**：`PartyProfileChanged`、`PartyRelationChanged`、`ClientApplicationSubmitted`——供 compliance 触发重核。

### 5.2 compliance 拥有

1. **尽调程序**：按辖区 × 主体类型 × 角色 × 档次 × Owner（平台 / PayFac）的程序版本：材料要求、检查项、筛查项、复审频率、决定规则。它回答"怎么核、核到什么程度、谁来核"。
2. **案件**：入驻 / 修订 / 定期复审 / 触发复审四类；主体冻结；依赖子案件；状态机与状态变更日志。
3. **核验事实**：材料证据审核、外部/人工检查结果与分项、筛查执行/命中/处置/持续监控、风险因子与评级。
4. **决定与资格**：不可变决定（推荐人 + 决定人）、角色资格投影、有效期与复审调度、资格状态变更；结算资格控制。
5. **监控触发**：定期、名单更新、资料变更、文件到期、风控信号、人工；去重与分级。
6. **PayFac 服务**：程序 Owner、案件责任方与操作方、Partner 可见范围、证据包导出、计费事件。

### 5.3 不属于这两者

| 不拥有 | 归属 | 说明 |
|---|---|---|
| 文件正文与下载 | Platform file | 业务只存 `/files/v1/{fileId}` + Hash |
| 账号、登录、使用条款文本 | Platform IAM / legal | 条款**接受记录**在 client-master 申请声明表 |
| 外部供应商调用（筛查、IDV、登记库、税号） | channel-connector SERVICE 渠道 | compliance 只持标准 API 的调用与结果引用，不持凭据 |
| 四眼审批的人工流（可选） | Platform workflow | D13：审批只记"人已同意"，compliance 执行时复核；首期用 compliance 自带的推荐/决定双人，不接 workflow（第 16 节 Q2） |
| 合同、开通项、定价 | commercial | 合同只引用 APPROVED 的角色资格 |
| 收款账户核验（户名匹配、penny test） | payout | 入驻只采集银行证明材料，不核账户 |
| 交易监控、名单、频次 | risk-decision | 它的 Action Effect 产生结算控制，经 compliance-api 创建 |
| 买家 / 子商户（C5）/ 分账收款方 | counterparty | 待定（Q6） |
| 自动留存、销毁 | 数据合规专项 | MVP 不做；只记录 `record_retention_years` 供将来使用 |

### 5.4 设计原则

- **辖区是数据，不是分支**：代码里不出现 `if (country == "SA")`；三国差异全部落在辖区包、目录与程序版本里。
- **采集与核验分离**：client-master 只判断"格式对不对、必填齐不齐"，compliance 判断"真不真、够不够"。两边共用辖区键与资格类型，但各持自己的目录。
- **需求清单是投影**：缺什么、谁来动、何时到期由规则计算并持久化为投影，供门户与运营看；它不是审计事实。
- **外部结果只存引用与规范化结论**：供应商原始报文进 Vault（现有 `RISK_DD_CHECK_RESULT_V1`），库内只存结论、原因码与分项。
- **决定不可变、资格可投影**：每个案件一个最终决定；资格状态变更追加日志；撤销是新决定。
- **事务公理**：外部调用（Vault、channel-connector、Platform file）都在本地事务之外；案件创建是可逆跨服务命令（D18），client-master 登记 compensation-event 后事务外调用 I01。
- **PayFac 是责任方，不是租户隔离边界**：实例是数据隔离边界（D21/D22）；同一实例内 PayFac 只是案件上的责任方与程序 Owner，数据按责任方做行级可见范围。

## 6. 概念模型

```
辖区包 Jurisdiction ──1:N── 组织类型目录 / 标识类型目录 / 属性定义 / 采集 Schema(版本)
                                                   │
Party ──1:1(当前)── Profile ──1:N── Identifier(Vault Token) / Address / Contact / Attribute / Name(本地文字)
  │                  └── 修订：新 Profile 行(DRAFT) 经修订申请批准后成为当前
  ├─1:N── Relation(organization ↔ related party, type, ubo_basis, %)
  └─1:N── Role(CLIENT / OPERATING_ENTITY / OE_PROVIDER / REFERRER / …)

Application(ONBOARDING | PROFILE_AMENDMENT | PARTNER | OE) ──1:N── Document / Declaration / Requirement(投影)
        │ submit (I01)                               ▲ ClientReviewDecided / QualificationChanged
        ▼                                            │
尽调程序 Program(jurisdiction, party_type, role_type, tier, owner) ──版本── Requirement / Check / Screening 模板
        │ 实例化
Case(type, responsible_partner, operated_by, tier, risk_rating) ──1:N── Subject(冻结) / Requirement / Evidence / Check(+Finding) / ScreeningRun(+Hit+Resolution) / RiskFactor / Dependency / StateChange
        │ decide
Decision(不可变; recommended_by + decided_by) ──投影── RoleQualification(valid_to, next_review) ──追加── QualificationStateChange
        │
ReviewTrigger(PERIODIC | PROFILE_CHANGE | DOCUMENT_EXPIRY | SCREENING_ALERT | RISK_SIGNAL | MANUAL) ──创建── Case(PERIODIC_REVIEW | TRIGGERED_REVIEW)
SettlementControl(SETTLEMENT_BLOCK | MIN_SECURITY_DEPOSIT) ◄── risk-decision Action Effect / 复审逾期
ScreeningMonitor(subject, provider, external_ref) ◄── 供应商差异事件
```

术语：

| 词 | 含义 |
|---|---|
| **辖区（Jurisdiction）** | 适用一套采集与核验规则的法域，默认 = ISO 国家码；AE 的 DIFC / ADGM 作为子辖区（`AE-DIFC`、`AE-ADGM`）因其独立公司登记与数据保护法 |
| **档次（Tier）** | `SIMPLIFIED`（SDD）/ `STANDARD`（CDD）/ `ENHANCED`（EDD）；由风险评级决定，决定程序版本与复审频率 |
| **资格类型** | 现有 `INDIVIDUAL_KYC / ORGANIZATION_KYB / RELATED_PERSON_KYC / PARTNER_DUE_DILIGENCE / ENTITY_UNDERWRITING`，不新增 |
| **案件类型** | `ONBOARDING / PROFILE_AMENDMENT / PERIODIC_REVIEW / TRIGGERED_REVIEW` |
| **责任方（responsible partner）** | 对收单行承担该主体尽调责任的 PayFac Partner；平台自营（C3）为空 |
| **操作方（operated_by）** | `PLATFORM`（平台运营代操作，含 AFFILIATED PayFac）/ `PARTNER`（外部 PayFac 自己的合规人员；受 R3 限制，首期不开放） |

### 6.1 状态机总表

> 每行：`FROM → TO`：触发（谁 / 什么事件）；[守卫]。"系统"= 补偿 Handler、事件消费者或 XXL-Job。终态用 ■ 标记。总表见第 4 板；**每个状态机的单图（状态节点 + 带触发方颜色的箭头 + 守卫 + 备注）见 `client-onboarding.pen` 右侧 SM-A … SM-Q 十七张画板**（箭头颜色：蓝 Client、橙 审核员 / PayFac 合规、红 决定人、黄 PayFac 管理员、紫 平台运营、灰 系统）。

**A. Client 申请 `t_client_application.status`**（ONBOARDING 与 PROFILE_AMENDMENT 同；Partner / OE 申请同形）

| 状态 | active_slot | 说明 |
|---|---|---|
| DRAFT | 1 | 可编辑 |
| SUBMITTED | 1 | 已冻结 submissionId，等待 I01 立案 |
| UNDER_REVIEW | 1 | 案件已建，只读 |
| RETURNED | 1 | 退回 Client 补件，可编辑（需求级错误） |
| APPROVED ■ / REJECTED ■ / WITHDRAWN ■ | NULL | 终态释放名额 |

- 创建 → DRAFT：Client 创建（入驻码校验；同账号只允许一份进行中）
- DRAFT → SUBMITTED：Client 提交；[Schema 校验、关系门槛、材料槽、声明全部通过；文件核验通过]
- SUBMITTED → UNDER_REVIEW：系统，I01 成功回填 `due_diligence_case_id`（失败保留 SUBMITTED 由补偿重试，确定性失败 FAILED 告警）
- UNDER_REVIEW → RETURNED：系统，消费 `ClientReviewDecided(RETURNED)` [submissionId 与 caseId 为当前，否则忽略 STALE]
- RETURNED → SUBMITTED：Client 重提（新 submissionId；compliance 同案件 round+1）
- UNDER_REVIEW → APPROVED：系统，`ClientReviewDecided(APPROVED)`；同事务：Profile 冻结 / 替换当前行、Client ACTIVE、BCR、Platform 投影
- UNDER_REVIEW → REJECTED：系统，`ClientReviewDecided(REJECTED)`；不复活，同类型重新申请复用 Party 与 Client（现行规则）
- DRAFT / RETURNED → WITHDRAWN：Client 撤回（**本期新增**；修订申请撤回时删除草稿 Profile 行；已立案的通知 compliance 案件 WITHDRAWN）
- RETURNED → WITHDRAWN：系统，消费 `ClientReviewDecided(EXPIRED)`（退回超期，Q16）；释放名额，Client 可重新申请

**B. Party Profile 行**（`approved_time` / `current_slot` / `superseded_time` 组合，不是单列状态）

- 创建 → DRAFT（`approved_time=NULL`，`draft_application_id` 指向申请）
- DRAFT → CURRENT（`approved_time` 置值，`current_slot=1`，`draft_application_id=NULL`）：申请 APPROVED；[修订时同事务把旧 CURRENT → SUPERSEDED]
- CURRENT → SUPERSEDED（`current_slot=NULL`，`superseded_time`）：修订申请 APPROVED；永久保留供历史合同引用
- DRAFT → 删除：修订申请 REJECTED / WITHDRAWN（DELETE Snapshot）

**C. Client `t_client.status`**：PENDING → ACTIVE（申请 APPROVED）；ACTIVE → SUSPENDED（运营；或系统消费 `QualificationChanged(REVOKED)` 自动挂起，Q15 已定）；SUSPENDED → ACTIVE（新资格 APPROVED + 运营确认）；ACTIVE / SUSPENDED → TERMINATED ■（运营）；REJECTED 不改 Client（保持 PENDING 供重新申请）。

**D. 尽调案件 `t_due_diligence_case.status`**（四种 case_type 同一状态机）

| 状态 | active_slot | 含义 |
|---|---|---|
| DRAFT | 1 | 运营 / PayFac 手工立案尚未开始（case_origin=MANUAL） |
| SUBMITTED | 1 | I01 建案，要求 / 检查 / 筛查已实例化，自动项已登记 |
| IN_REVIEW | 1 | 人工审核中 |
| WAITING_EVIDENCE | 1 | 等外部结果（检查 UNKNOWN 重跑、IDV 会话、监控拉取） |
| PENDING_DEPENDENCY | 1 | 等关系人子案件决定 |
| RETURNED | 1 | 已退回 Client，等重提 |
| APPROVED ■ / REJECTED ■ / WITHDRAWN ■ / EXPIRED ■ | NULL | 终态 |

- 建案 → SUBMITTED（I01 / 触发器建复审案件）；DRAFT → SUBMITTED（手工立案启动）
- SUBMITTED → IN_REVIEW：系统，自动项全部有结果（或无自动项）；或审核员首次操作
- IN_REVIEW ⇄ WAITING_EVIDENCE：系统，发起外部调用 / 收到结果
- IN_REVIEW ⇄ PENDING_DEPENDENCY：系统，子案件未全部终态 / 全部终态
- IN_REVIEW → RETURNED：审核员退回（errors[] + 期限）；发布 `ClientReviewDecided(RETURNED)`
- RETURNED → IN_REVIEW：系统，I01 同案件 round+1（只重做变更部分，已接受证据与有效期内检查复用）
- IN_REVIEW → APPROVED：决定人；[门禁：必需要求 ACCEPTED 或 WAIVED；阻断检查 PASS；无 OPEN 命中；子案件全部 APPROVED；分类已确认；CRR 已评；四眼时 decided_by ≠ recommended_by]
- IN_REVIEW → REJECTED：决定人；[子案件同事务 WITHDRAWN]
- 任一进行中 → WITHDRAWN：Client 撤回申请 / 主案件拒绝时子案件
- RETURNED → EXPIRED：系统，退回期限超时未重提（可配置）；发布 `ClientReviewDecided(EXPIRED)`，申请同步 WITHDRAWN（Q16 已定）

**E. 材料要求 `t_dd_requirement.status`**：PENDING → RECEIVED（Client 提交证据）→ ACCEPTED / REJECTED（审核员，拒绝带 reason_code）；REJECTED → RECEIVED（新证据，旧证据 SUPERSEDED）；PENDING / REJECTED → WAIVED（审核员，[waivable_flag]，记原因）；ACCEPTED → EXPIRED（系统，证据 expiry_date 到期）→ RECEIVED。

**F. 证据 `t_dd_evidence_ref.status`**：RECEIVED → ACCEPTED / REJECTED（审核员）；RECEIVED / ACCEPTED / REJECTED → SUPERSEDED（同要求新证据）。

**G. 检查 `t_dd_check.status` + `outcome`**：REQUESTED → RUNNING（系统调渠道）→ COMPLETED（outcome PASS / FAIL / REVIEW）/ FAILED（供应商业务失败）/ UNKNOWN（ERROR / 超时）；REVIEW → 审核员定 PASS / FAIL（reviewed_by）；FAILED / UNKNOWN → 重跑 = 新 check 行（旧行 outcome NOT_EXECUTED）；MANUAL：REQUESTED → COMPLETED（审核员录入结论）。

**H. 筛查执行 `t_screening_run.status`**：STARTED → SUCCEEDED / FAILED / TIMED_OUT；FAILED / TIMED_OUT → 新 run。

**I. 命中 `t_screening_hit.status`**：OPEN → RESOLVED：审核员追加处置 NO_MATCH / FALSE_POSITIVE / TRUE_POSITIVE_ACCEPT / TRUE_POSITIVE_REJECT；处置 POTENTIAL_MATCH / INSUFFICIENT_EVIDENCE 不关闭（仍 OPEN，等更多证据）；OPEN / RESOLVED → SUPERSEDED：同主体新 run 取代；TRUE_POSITIVE_REJECT + SANCTIONS → 案件只能 REJECTED，已有资格 → REVOKED。

**J. 资格决定 `t_qualification_decision`**：无状态，不可变；`outcome` ∈ APPROVED / REJECTED / REVOKED / EXPIRED；每个案件最多一个最终决定；撤销 = 新 MANUAL 决定。

**K. 角色资格 `t_role_qualification.status`**（投影）：决定 APPROVED → APPROVED；决定 REJECTED → REJECTED；APPROVED → REVIEW_OVERDUE（系统，`next_review_time` + 宽限超过且复审案件未 APPROVED）→ APPROVED（复审案件 APPROVED，新决定）；APPROVED / REVIEW_OVERDUE → REVOKED（复审 REJECTED、手工撤销、制裁 TRUE_POSITIVE_REJECT）；APPROVED → EXPIRED（系统，`valid_to` 到期无续期）；REJECTED / REVOKED / EXPIRED → APPROVED（新案件 APPROVED）。每次迁移追加 `t_qualification_state_change`，发布 `QualificationChanged`。

**L. 复审触发 `t_review_trigger.status`**：OPEN → CASE_CREATED（审核员或自动规则建案）/ DISMISSED（审核员，原因）/ EXPIRED（去重合并进更高严重度触发，或超期）。

**M. 结算控制 `t_settlement_control.status`**：ACTIVE → RELEASED（释放条件满足 / 复审 APPROVED / 手工）/ EXPIRED（`effective_to`）；每次迁移发布 `SettlementRestrictionChanged`。

**N. 筛查监控 `t_screening_monitor.status`**：ACTIVE → STOPPED（资格 REVOKED / Client TERMINATED / 程序不再要求）/ FAILED（供应商错误）→ ACTIVE（重启）。

**O. 入驻码 `t_partner_onboarding_code.status`**：ACTIVE → REVOKED（PayFac / 运营）/ EXPIRED（`effective_to` 或 `use_count ≥ max_uses`）。

**P. 程序版本 / 采集 Schema 版本**：DRAFT → ACTIVE（发布，四眼；同键旧 ACTIVE → RETIRED）；ACTIVE → RETIRED（下线；在途案件 / 申请继续引用旧版本）。

**Q. 需求清单项**（计算值，非持久化）：`CURRENTLY_DUE`（提交前必须）/ `EVENTUALLY_DUE`（批准前必须）/ `PAST_DUE`（超过案件给的期限）/ `PENDING_VERIFICATION`（已提供等核验）/ `SATISFIED` / `WAIVED`；来源 = Schema ∪ 关系规则 ∪ 程序材料 ∪ 声明 ∪ 案件 errors[]。

## 7. 核心流程

### 7.1 F1 机构 Client 入驻（以沙特 LLC 经 PayFac 接入 C2 为例）

```
0  Client 门户：注册账号（Platform）→ 选择国家 SA、主体类型 ORGANIZATION、组织类型 LLC
1  GET /client-onboarding/v1/form-schema?jurisdiction=SA&partyType=ORGANIZATION&roleType=CLIENT&organizationType=LLC
   ← client-master 解析采集 Schema：分区、字段、必填、格式、标识类型(CR_NUMBER 10 位 7 开头, VAT_NUMBER 15 位首尾 3, …)、
     关系人规则(≥1 签字人、UBO ≥25%)、地址格式 SA_NATIONAL_ADDRESS、材料槽(经 compliance 程序解析得到)
2  PATCH …/draft：按分区保存；证件号 → [事务外] Vault Protect(STABLE) → [短事务] 存 Token+脱敏；
   每次保存后重算需求清单投影（t_application_requirement）
3  POST …/people / relations：关系人各自成为 Party；UBO 关系带 ubo_basis 与持股；系统按辖区门槛校验
   “持股合计 / 是否有 UBO / 是否有签字人”
4  POST …/documents：材料引用（fileAddress + hash + issue/expiry），槽位来自程序解析
5  POST …/declarations：所有权完整、董事完整、PEP 自述、信息真实、平台使用条款（记 ip/user_agent/时间）
6  POST …/submissions：
   [事务外] 文件核验(Platform)、程序解析(compliance GET /programs/resolve) 
   [本地事务] 校验 Schema 必填/格式/关系门槛/声明齐全 → 冻结 submissionId → 申请 SUBMITTED →
             登记 compensation-event CLIENT_REVIEW_REGISTRATION
   [提交后] Handler 调 compliance I01（带辖区、组织类型、属性、Token、责任 PayFac）→ 申请 UNDER_REVIEW
7  compliance I01：[本地事务] 按 (SA, ORGANIZATION, CLIENT, STANDARD, owner=PayFac 或平台默认) 取 ACTIVE 程序版本 →
   建主案件 + 每个关系人子案件 + 依赖 → 冻结主体 → 实例化材料要求/检查/筛查 → 案件 SUBMITTED →
   登记 compensation-event 发起自动检查与筛查
8  [事务外] 自动项经 channel-connector SERVICE 渠道执行：名单筛查（公司 + 每个人）、[后续] 登记库/税号/IDV；
   结果 [短事务] 落 t_dd_check / t_screening_run+hit；有命中或需人工 → 案件 IN_REVIEW
9  运营工作台：审材料（接受/拒绝+原因码）、处置命中、做人工检查项（网站审查、MATCH/VMSS 查询…）、
   确认行业/MCC（O04）、记录风险因子 → 系统算评级；评级 HIGH → 升 ENHANCED：补要求（资金来源证明）、第二审批人
10 决定：推荐人 → 决定人（四眼按档次/评级）→ APPROVED：写决定 + 资格投影(valid_to, next_review) →
   发布 ClientReviewDecided + QualificationChanged；RETURNED：带 requirement 级错误码与补救动作；REJECTED：终态
11 client-master 消费 ClientReviewDecided：APPROVED → Profile 冻结、Client ACTIVE、Platform 投影；
   RETURNED → 申请回 RETURNED，需求清单投影标 past_due
12 commercial 签合同只引用 role_qualification=APPROVED；acquiring 侧 C2 子商户登记（R15，另需求）
```

事务边界：步骤 6 与 7 各自本地事务，跨服务为可逆命令（client-master 以 submissionId 幂等，compliance 以 `source_submission_id` 幂等）；步骤 8 的每次外部调用都是 compensation-event 驱动、事务外执行、结果另开短事务。

### 7.2 F2 个人 Client 入驻

同 F1，少关系人与机构文件；资格类型 `INDIVIDUAL_KYC`；AE 个人实为 Sole Establishment（需执照）——由 AE 辖区包的组织类型 `SOLE_ESTABLISHMENT` 表达，个人 Client 在 AE 必须附执照号作为标识（第 9 节）。

### 7.3 F3 退回与重提

- 退回载体从"分区"升级为**需求级**：`errors[{requirementKey, code, reason, remediation}]`，门户按字段高亮；兼容 `returnSection`。
- 重提：新 `submissionId`、同案件新一轮（`t_due_diligence_case.round_no+1`，不新建案件，保留已接受的证据与已完成的检查，只重做变更部分）。现行"每次重提新任务、旧任务结果不生效"改为"同案件多轮"，原因：外部检查有成本且结果有效期内可复用（Adyen / Sumsub 都按 applicant 累积）。

### 7.4 F4 外部检查与筛查

| 检查/筛查类型 | 对象 | 首期 | 执行方式 | 结果 |
|---|---|---|---|---|
| `SANCTIONS` / `PEP` / `ADVERSE_MEDIA` 筛查 | 机构 + 每个关系人 | **是**（供应商待选） | 标准 API `SCREENING_SEARCH` | run → hits → 人工处置 `NO_MATCH / FALSE_POSITIVE / POTENTIAL_MATCH / TRUE_POSITIVE`；TRUE_POSITIVE + SANCTIONS → 阻断；PEP → 升 ENHANCED |
| `GOVERNMENT_ID_LOOKUP` | 自然人证件 | 预留（AE ICP 网关、SA Yakeen） | `GOV_ID_VERIFY` | PASS / FAIL / NOT_AVAILABLE + 分项（姓名、生日、国籍一致） |
| `IDENTITY_DOCUMENT` + `LIVENESS` | 自然人 | 预留（IDV 供应商） | `IDV_SESSION_CREATE` / `IDV_RESULT_GET`（会话式，回调另议 Q7） | 分项 breakdown（Onfido 形状） |
| `REGISTRY_LOOKUP` | 机构 | 预留（SA Wathq、AE NER、EG 待定） | `KYB_REGISTRY_LOOKUP` | 存续、名称一致、经理/股东清单比对 |
| `TAX_ID_VERIFY` | 机构 | 预留（AE FTA TRN、SA ZATCA） | `TAX_ID_VERIFY` | 登记名称一致 |
| `WEBSITE_REVIEW` | 机构/个人 | **是（人工）** | 人工检查项 | PASS / FAIL + 原因（无退款政策、商品与申报不符…） |
| `TERMINATED_MERCHANT_LOOKUP`（MATCH / VMSS） | 机构 + principals | 人工（经收单行/PayFac 查询，记录结果） | 人工检查项 | HIT / NO_HIT |
| `ADDRESS_VERIFICATION` | 地址证明 | 人工 | 材料审核 | — |
| `BANK_ACCOUNT_NAME_MATCH` | 银行证明 | 人工（户名与法定名一致） | 材料审核 | — |

人工检查项与自动检查项同落 `t_dd_check`（`check_mode = MANUAL / AUTOMATED`），这样程序模板一处定义、工作台一处展示、证据包一处导出。

### 7.5 F5 客户风险评级（CRR）与档次

**CRR = Customer Risk Rating，客户风险评级**，是 AML 风险为本方法（FATF R.1 / R.10；CBUAE AML 指引 §6.3.6；SAMA AML/CTF Guide 3.7–3.8）要求每个客户都要有的一个分级结论：入驻时评一次，之后随复审与触发事件重评。它决定三件事：**尽调档次**（SDD / CDD / EDD）、**复审频率**、**是否需要高管批准**。收单行对 PayFac 的子商户尽调记录通常要求能看到每个子商户的 CRR 及其依据。本设计里 CRR 就是案件与资格上的 `risk_rating`（LOW / MEDIUM / HIGH）+ `risk_score` + `t_dd_risk_factor`（依据），决定表冻结当时的评级。它与"承保"（业务 / 信用 / 品牌风险，MCC、流水、拒付）是两条线：CRR 是反洗钱口径，承保是收单风险口径；本设计把承保因子（行业 / 流水）也作为 CRR 的输入因子之一，但不单独建承保评级。

**本期要不要做**：要，但分两步——**S1 做手工 CRR**（审核员在案件上从 LOW / MEDIUM / HIGH 选一个并填依据，必填，决定时冻结；复审频率按评级取默认值），**S4 做自动评分**（因子规则 → 分 → 评级，审核员可覆盖并记原因）。没有 CRR 的案件不能批准（门禁）。

因子（S4 起，运营可维护权重）：主体国家风险、关系人国籍/居住国风险、PEP（自述或命中）、行业/MCC 风险、预计流水、组织类型（现金密集/离岸/复杂结构）、负面媒体命中、既往拒绝/撤销记录。评级 `LOW / MEDIUM / HIGH` → 档次 `SIMPLIFIED / STANDARD / ENHANCED` → 程序版本（同一程序可按档次挂不同模板集）→ 复审频率（默认 HIGH 12 个月、MEDIUM 36、LOW 60，受辖区包 `max_review_interval_months` 上限约束）。评级与因子作为案件事实保存并随决定冻结；运营可覆盖并记原因。

### 7.6 F6 持续监控

| 触发 | 来源 | 处理 |
|---|---|---|
| `PERIODIC` | XXL-Job 扫 `t_role_qualification.next_review_time` | 建 `PERIODIC_REVIEW` 案件，提前 N 天通知 Client 确认资料（声明"无变化"或发起修订）；逾期 → 资格 `REVIEW_OVERDUE` 投影 + 结算控制 `SETTLEMENT_BLOCK`（可配置） |
| `SCREENING_ALERT` | 供应商监控差异（`t_screening_monitor`）或平台按名单版本重筛 | 新 run + hits → 处置；TRUE_POSITIVE → `TRIGGERED_REVIEW` 案件，必要时立即撤销资格 |
| `PROFILE_CHANGE` | client-master `PartyProfileChanged` / `PartyRelationChanged`（修订申请） | 重大性规则：UBO/董事/签字人/法定名/注册国变更 → 建案件；联系方式/地址小变更 → 只记录 |
| `DOCUMENT_EXPIRY` | XXL-Job 扫 `t_dd_evidence_ref.expiry_date` | 提前 30 天建触发，通知补件；到期未补 → 复审案件 |
| `RISK_SIGNAL` | risk-decision 事件（拒付率、欺诈） | 建触发，由运营决定是否立案 |
| `MANUAL` | 运营 | 立案 |

去重：同主体同类型 OPEN 触发合并；分级：SANCTIONS 命中 `CRITICAL` 立即、UBO 变更 `HIGH` 30 天内、负面媒体 `MEDIUM` 72 小时复核队列、地址变更 `LOW` 仅记录。

### 7.7 F7 批准后修订（PROFILE_AMENDMENT）

```
Client：POST /client-onboarding/v1/applications {applicationType: PROFILE_AMENDMENT} → 新 Profile 行(DRAFT，复制当前值) + 申请(DRAFT, draft_profile_id)
      编辑 → 提交（同 F1 步骤 6，I01 case_type=PROFILE_AMENDMENT，携带 changedFields[]）
compliance：按重大性规则决定是否全量复核（Q10）；APPROVED → ClientReviewDecided
client-master：[本地事务] 新 Profile 行 approved_time 置值、current_slot=1；旧行 current_slot=NULL、superseded_time；
              关系集合按修订申请替换；发布 PartyProfileChanged（已批准）
```

修订申请复用 `t_client_application`（`application_type=PROFILE_AMENDMENT`），不单独建表；同一 Client 同时最多一份进行中的申请（入驻或修订）沿用 `active_slot`。

commercial 对 `GET /party-profiles?ids=` 的读取改为"当前行"，历史合同引用的旧 profile_id 仍可读。

### 7.8 F8 Partner / OE 资格

Partner（`PARTNER_DUE_DILIGENCE`）与 OE（`ENTITY_UNDERWRITING`）复用同一程序/案件/决定链，程序模板不同：Partner 要牌照、PayFac 协议、收单行关系证明；OE 要收单资质档位证据（MERCHANT / MERCHANT_RESALE_APPROVED / PAYFAC）、MCC 范围。现有 Partner / OE 申请表的 Risk Tuple 字段保持，I01 增加这两类来源命名空间（现有 `source_namespace` 已预留）。

## 8. 需求清单（Requirements）投影

### 8.1 来源

`需求清单 = 采集 Schema 字段要求 ∪ 关系人规则 ∪ 程序材料要求 ∪ 声明要求 ∪ 案件回传的待办（补件、补检查）`。client-master 在 `GET …/requirements` 时**实时计算**（Schema + 当前草稿 + 程序解析 + 案件返回的待办），**不持久化**；带期限的项（退回补件期限、复审期限）由 compliance 在案件上持有并随 `ClientReviewDecided` / `result` 接口返回。原设计的 `t_application_requirement` 投影表因与规则重复而删除。

### 8.2 项状态

| 状态 | 含义 |
|---|---|
| `CURRENTLY_DUE` | 提交前必须满足 |
| `EVENTUALLY_DUE` | 批准前必须满足，但允许先提交（如运营确认分类后才需要的材料） |
| `PAST_DUE` | 已过 `due_time`（退回后补件期限、复审期限） |
| `PENDING_VERIFICATION` | 已提供，等 compliance 核验 |
| `SATISFIED` | 已满足 |
| `WAIVED` | 程序允许豁免（运营记原因） |

每项带 `requirement_kind`（FIELD / IDENTIFIER / PERSON / RELATION / DOCUMENT / DECLARATION / CHECK）、`requirement_key`（如 `profile.organization.local_legal_name`、`relation.ubo.min_one`、`document.PROOF_OF_UBO`）、`subject_party_id`、`awaiting_action_from`（CLIENT / OPERATIONS / SYSTEM）、`error_code`、`remediation_code`、`due_time`。

### 8.3 错误码族（首版）

- 字段：`FIELD_MISSING`、`FIELD_FORMAT_INVALID`、`FIELD_VALUE_NOT_IN_CATALOG`、`DOB_AGE_UNDER_MINIMUM`（辖区包 `min_age`）
- 标识：`IDENTIFIER_FORMAT_INVALID`、`IDENTIFIER_CHECKSUM_FAILED`、`IDENTIFIER_EXPIRED`、`IDENTIFIER_CONFLICT`（已绑定其他 Party）
- 关系：`UBO_MISSING`、`UBO_OWNERSHIP_EXCEEDS_100`、`SIGNATORY_MISSING`、`DIRECTOR_MISSING`、`RELATED_PERSON_INCOMPLETE`
- 材料：`DOCUMENT_MISSING`、`DOCUMENT_EXPIRED`、`DOCUMENT_TOO_OLD`、`DOCUMENT_UNREADABLE`、`DOCUMENT_MISMATCH_NAME`、`DOCUMENT_MISMATCH_ADDRESS`、`DOCUMENT_TYPE_INVALID`、`DOCUMENT_FRAUDULENT`
- 声明：`DECLARATION_MISSING`
- 核验：`VERIFICATION_FAILED_IDENTITY`、`VERIFICATION_FAILED_REGISTRY`、`VERIFICATION_FAILED_TAX_ID`、`SCREENING_REVIEW_REQUIRED`
- 补救：`PROVIDE_FIELD`、`UPLOAD_DOCUMENT`、`ADD_PERSON`、`ADD_RELATION`、`SIGN_DECLARATION`、`CONTACT_SUPPORT`、`NONE`

## 9. 三国国家包（AE / SA / EG）

> 标 **[待核实]** 的项上线前须法务 / 当地合规确认；本节只定义模型要装什么，不替代法律意见。

### 9.1 辖区包字段（`t_jurisdiction`）

| 字段 | AE | SA | EG |
|---|---|---|---|
| `name_script` | `ARABIC` + `LATIN`（证件双语；芯片 Full Name 单字段 126 字符） | `ARABIC`（国民身份证阿文，护照拉丁） | `ARABIC`（身份证阿文，护照 MRZ 拉丁） |
| `address_format_code` | `AE_POBOX`（无邮编；PO Box；迪拜 Makani 10 位；阿布扎比 Onwani） | `SA_NATIONAL_ADDRESS`（建筑号 4 + 街道 + 区 + 城市 + 邮编 5 + 附加号 4；短地址 4 字母 + 4 数字） | `EG_STANDARD`（楼号 + 街道、区、市、**省必填**、邮编 5） |
| `postal_code_required` | 否 | 是 | 是 |
| `ubo_threshold_pct` | 25（Cabinet Decision 109/2023；FI 侧 Res.134/2025 Art.10） | 25 或以上（商务部第 99 号决定取代第 235 号，MD 267 不再权威；SAMA AML 指引 3.18；比较方式 `UBO_THRESHOLD_COMPARISON`=GTE） | 25 候选（官方评估记录的实践，非确认的统一法定门槛，待银行 / PSP 书面确认；见 9.5a #18） |
| `ubo_fallback` | 控制 → 高管 | 控制 → 经理/董事/董事长 | 控制 → 高管 |
| `record_retention_years` | 5（Res.134/2025 Art.25） | **10**（AML Law Art.12） | 5 **[待核实]** |
| `max_review_interval_months` | 风险为本（CBUAE 指引 §6.3.6）；默认 12/36/60 | 风险为本（SAMA 3.7–3.8） | 风险为本 |
| `min_age` | 18 | 18 | 18 |
| `id_verification_mandate` | 持牌机构须经 ICP 在线网关核验 Emirates ID（CBUAE AML 指引 §6.3.1） | NIC 批准电子认证可远程（SAMA 3.9）：Yakeen / Nafath | 无公开 API；CBE e-KYC 规则 2026-08 已发布、平台未上线 **[待核实]** |
| `data_localization` | **强**：消费者与交易数据须存于 UAE（CBUAE CPS §6.1.6.3、RPSCS Reg.）；DIFC/ADGM 另有数据保护法 | **强（审批制）**：SAMA CSF 3.4.3 云默认境内，境外需事前批准 | 不明 **[待核实]**；PDPL 跨境需 PDPC 许可 + 主体同意 |
| `cdd_occasional_threshold` | AED 55,000；电汇 AED 3,500 | 未取得 | 未取得 |
| `psp_rule_note` | RPSCS Regulation 2021：Merchant Acquiring / Payment Aggregation 持牌类别 | **PSP 必须自行签约并 KYC 商户；技术对接公司不得做 KYC**（SAMA E-Commerce PSP Rules 2021/2024）→ 见 Q1 | CBE 2019：PF 须在埃及有实体办公室并登记；银行监督商户选择；3 日结算；担保 = 3 日收款额 |

### 9.2 组织类型目录（`t_organization_type`，节选）

| 辖区 | code | 名称 | 典型人员角色（最低要求） | 必交材料槽 |
|---|---|---|---|---|
| AE | `LLC` | 大陆有限责任公司 | 股东（MOA 持股）、经理、签字人（不在执照上需 POA） | `TRADE_LICENSE`、`MOA`、`BANK_PROOF`、UBO/经理证件 |
| AE | `SOLE_ESTABLISHMENT` | 独资机构 | 业主（执照仅列经理则经理为代表） | `TRADE_LICENSE`、个人证件、个人银行证明 |
| AE | `CIVIL_COMPANY` | 民事合伙 | 合伙人、经理 | `TRADE_LICENSE`、`MOA` |
| AE | `FZE` / `FZCO` / `FZ_LLC` | 自贸区公司 | 股东、董事/经理 | `TRADE_LICENSE`（自贸区）、`MOA`/`AOA`、`CERT_OF_INCORPORATION` |
| AE | `FOREIGN_BRANCH` | 外国公司分支 | 母公司、分支经理（POA） | 母公司注册文件、分支执照、POA |
| AE | `PJSC` | 公众股份公司 | 董事会 | 上市豁免 UBO（`listed_company=1`） |
| SA | `LLC` | 有限责任公司 | 股东、经理 | `COMMERCIAL_REGISTRATION`、`MOA`（多股东）、`BANK_PROOF` |
| SA | `SJSC` / `JSC` | 简易/股份公司 | 董事、董事长 | `COMMERCIAL_REGISTRATION`、章程 |
| SA | `ESTABLISHMENT` | 个人独资（مؤسسة فردية） | 业主 | `COMMERCIAL_REGISTRATION`、业主证件 |
| SA | `FREELANCER` | 自由职业（HRSD Freelance Work Document） | 本人 | `FREELANCE_DOCUMENT`（1 年有效） |
| SA | `FOREIGN_BRANCH` | 外国公司分支 | 母公司、分支经理 | MISA 许可 + CR |
| EG | `LLC` | 有限责任公司 | 合伙人（≥2）、经理 | `COMMERCIAL_REGISTRATION`、`TAX_CARD`、`MOA` |
| EG | `JSC` | 股份公司 (SAE) | 董事会 | 同上 + 章程 |
| EG | `ONE_PERSON_COMPANY` | 一人公司 | 业主、经理 | `COMMERCIAL_REGISTRATION`、`TAX_CARD` |
| EG | `SOLE_PROPRIETORSHIP` | 个人经营 | 业主 | `COMMERCIAL_REGISTRATION`、`TAX_CARD`、国民 ID |
| EG | `PARTNERSHIP` | 普通/有限合伙 | 普通合伙人 | 同 LLC |
| EG | `FOREIGN_BRANCH` | 外国公司分支（GAFI） | 母公司、分支经理 | GAFI 文件 |

### 9.3 标识类型目录（`t_identifier_type`）

| 辖区 | code | 主体 | 格式 / 校验 | 有效期 | Vault | 核验来源 |
|---|---|---|---|---|---|---|
| AE | `AE_EMIRATES_ID` | 个人 | 15 位数字 `784-YYYY-NNNNNNN-C`；规范化去连字符；末位校验 **[待核实，仅提示]** | 公民 5/10 年，居民随签证 | 是 | ICP 网关 / UAE PASS |
| AE | `PASSPORT` | 个人 | 签发国 + 号码；9 位字母数字 **[待核实]** | 必填到期日 | 是 | IDV |
| AE | `AE_RESIDENCE_VISA` | 个人（外籍） | 自由格式 + 到期日 | 必填 | 是 | — |
| AE | `AE_TRADE_LICENSE` | 机构 | 签发机关必填（DED/DET、各自贸区）；号码格式不统一 **[待核实]**；`issuer_scope_key` = 机关码 | 必填到期日 | 否（公开登记号） | NER / DED / 自贸区登记处 |
| AE | `AE_TRN` | 机构（VAT） | 15 位数字 | — | 否 | FTA TRN 查询 |
| AE | `AE_UNIFIED_ECONOMIC_ID` | 机构 | NER CBL 号 **[待核实]** | — | 否 | NER |
| SA | `SA_NATIONAL_ID` | 个人（公民） | 10 位，首位 1，Luhn | 10 年 **[待核实]** | 是 | Yakeen |
| SA | `SA_IQAMA` | 个人（居民） | 10 位，首位 2，Luhn | 卡 5 年、居留年续 | 是 | Yakeen |
| SA | `PASSPORT` | 个人 | 签发国 + 号码 **[待核实]** | 必填 | 是 | IDV |
| SA | `SA_CR_NUMBER` | 机构 | 10 位；新法下 = 统一国家号，首位 7（2025-04-03 后）；旧号按城市前缀 **[待核实]** | 年度确认 | 否 | MC 查询 / Wathq / 证书 QR |
| SA | `SA_VAT_NUMBER` | 机构 | 15 位，首位 3、末位 3 | — | 否 | ZATCA 查询 |
| SA | `SA_FREELANCE_DOCUMENT` | 个人 | 格式未公开 **[待核实]** | 1 年 | 否 | freelance.sa |
| EG | `EG_NATIONAL_ID` | 个人 | 14 位：世纪 1 + YYMMDD + 省 2 + 序列 4（第 13 位奇男偶女）+ 校验 1（算法未公开，只校验结构与生日一致） | 7 年 | 是 | 无公开 API（供应商） |
| EG | `PASSPORT` | 个人 | **[待核实]** | 必填 | 是 | IDV |
| EG | `EG_COMMERCIAL_REGISTER`（v1）→ `EG_UNIFIED_CR` / `EG_LOCAL_CR`（v2，二选一） | 机构 | 15 位统一商业登记号已实施；本地 CR 号由登记处签发 | — | 否 | ITDA 电子摘录 |
| EG | `EG_TAX_CARD` | 机构 | 9 位 `xxx-xxx-xxx`，规范化去连字符 | — | 否 | ETA |
| 通用 | `LEI` | 机构 | 20 位 ISO 17442 | — | 否 | GLEIF |

字段（只有定义，不含规则开关）：`identifier_type_code, jurisdiction_code, party_type, label_key(i18n), format_regex, normalization_rule(NFKC+去分隔符+大写), checksum_rule(LUHN/NONE/EG_NID/KR_BRN…), issuer_scope_mode(NONE/COUNTRY/AUTHORITY), verification_check_code, status`。`acquire_mode`、`expiry_required`、`vault_required` 在 `t_rule_setting(scope_type=IDENTIFIER_TYPE)`。

### 9.4 采集 Schema 要点（`t_onboarding_schema` + `t_onboarding_schema_field`）

| 分区 | 字段 | AE | SA | EG |
|---|---|---|---|---|
| 个人基础 | `given_name/family_name`（拉丁）、`local_full_name`（阿文）、`id_full_name`（证件拉丁全名） | 必填：拉丁 + 阿文 | 必填：阿文（证件）+ 拉丁（护照/转写） | 必填：阿文 + 拉丁转写 |
| 个人基础 | `birth_date`、`birth_place`、`birth_country`、`gender`、`nationality`、`residence_country` | 必填（Res.134 Art.9 要求出生地） | 必填（SAMA 3.3 要求出生地） | 必填 |
| 个人补充 | `occupation`、`source_of_funds_code`、`tax_residence_country`、`pep_self_declared` | 必填 | 必填（SAMA：收入来源） | 必填 |
| 机构基础 | `legal_name`、`local_legal_name`、`trade_name`、`organization_type`、`incorporation_country`、`incorporation_date`、`registration_authority`、`license_expiry_date`、`listed_company` | 必填（自贸区须选机关） | 必填 | 必填（GAFI/ITDA） |
| 机构补充 | `economic_sector`、`tax_registration_status`、`website` | — | — | — |
| 地址 | 按 `address_format_code` 渲染 | PO Box / Makani | 建筑号、附加号、区、邮编、短地址 | 省必填 |
| 标识 | 按 9.3 | 个人：EID 或护照（外籍） + 签证；机构：执照 + TRN（如有） | 个人：NID 或 Iqama；机构：CR + VAT | 个人：NID；机构：CR + 税卡 |
| 关系人 | 规则：UBO ≥ 阈值 ≥1 人或 UBO 替代；签字人 ≥1；经理/董事按组织类型 | LLC 需经理；法人股东 ≥25% 需穿透 | LLC 需经理 | LLC 需经理 |
| 材料 | 来自程序解析 | 见 9.5 | 见 9.5 | 见 9.5 |
| 声明 | `OWNERSHIP_COMPLETE`、`DIRECTORSHIP_COMPLETE`、`REPRESENTATIVE_AUTHORITY`、`PEP_DECLARATION`、`INFORMATION_TRUE`、`PLATFORM_TERMS` | 全部 | 全部 | 全部 |

### 9.5 材料槽（程序 `t_dd_program_requirement`，STANDARD 档，机构）

| 槽 `requirement_code` | document_type | AE | SA | EG | 时效 / 规则 |
|---|---|---|---|---|---|
| `REGISTRATION` | `TRADE_LICENSE` / `COMMERCIAL_REGISTRATION` | 必 | 必 | 必 | 未过期 |
| `CONSTITUTION` | `MOA` / `AOA` / 章程 | 必（多股东） | 必（多股东） | 必 | 最新认证版 |
| `OWNERSHIP` | `OWNERSHIP_STRUCTURE` / 股东名册 | 法人股东时必 | 法人股东时必 | 必 | 6 个月内 |
| `TAX` | `VAT_CERTIFICATE` / `TAX_CARD` | 如已登记 | 如已登记 | **必**（税卡） | — |
| `AUTHORIZATION` | `POA` / 授权书 | 签字人不在执照上时必 | 同 | 同 | 6 个月内 |
| `BANK` | `BANK_PROOF`（IBAN 证明/对账单） | 必 | 必 | 必 | 6 个月内；户名与法定名一致 |
| `ADDRESS` | `PROOF_OF_ADDRESS` | 可选（执照含地址） | 可选 | 必 | 3 个月内 |
| `IDENTITY`（每个关系人） | `PASSPORT` + `EMIRATES_ID` / `NATIONAL_ID` / `IQAMA` + 签证 | 必 | 必 | 必 | 未过期 |
| `ONLINE_PRESENCE` | 网站/店铺截图或链接 | 必（CNP） | 必 | 必 | 含价格、条款、退款、隐私 |
| `SOURCE_OF_FUNDS`（ENHANCED） | 资金/财富来源证明 | EDD | EDD | EDD | — |

### 9.5a 法务待确认清单（2026-10-27 汇总，26 项；同日按法源研究逐项标注）

> 依据 [法源研究 2026-10-27](./legal-research-26-items-2026-10-27.md)（设计评审参考，不是当地律师签署的意见或上线批准）。状态：**已符合**＝现状不需改；**已修订**＝由需求 [client-master 国家包法务修订](../../requirements/20261027/client-master-国家包法务修订/requirement.md) 落地；**待决定**＝需业务 / 架构决定；**后续**＝归入后续切片；**待确认**＝证据不足，仍需当地律师或责任机构书面确认。国家包生产发布仍以"待决定 / 待确认"项处理完毕为前提。

| # | 辖区 | 问题 | 结论摘要 | 状态 |
|---|---|---|---|---|
| 1 | AE | Emirates ID 校验位 | 未见官方公布的 Luhn 规范；只做 15 位格式检查，不以校验位拒绝 | 已符合 |
| 2 | AE | 外籍居民 EID 有效期 | 随居留类别；读证面 / 官方核验，不自行推算 | 已符合 |
| 3 | AE | Trade License 格式与签发机关；NER | 无统一号码格式；区分签发机关、号码类型；ERN 可作关联标识 | 已修订（ERN、"其他"机关；签发机关全目录待官方接口） |
| 4 | AE | TRN 是否固定 100 开头 | 只能确认 15 位；不设前缀 | 已符合 |
| 5 | AE | 109/2023 生效与罚则；UBO 登记核对 | 2023-11-16 生效，罚则见 132/2023 递进；登记回执不能替代穿透核验 | 已符合（作为证据之一） |
| 6 | AE | ICP 网关核验义务 | 对象是持牌机构；照片上传不足；网关由谁签约取决于 ICP 准入 | 后续（S6） |
| 7 | AE | 134/2025 Art.9 是否最小集；外国 PEP 批准 | Art.9 是基础要求非上限；外国 PEP 由承担义务机构高管批准 | 待决定（合规程序增加高管批准角色） |
| 8 | AE | CPS / RPSCS 本地化对技术平台 | 可能经外包合同传导；需逐项审查主库、备份、日志、运维访问 | 待决定（部署架构） |
| 9 | AE | PDPL 执行条例；DIFC / ADGM | 执行条例未见官方文本；DIFC / ADGM 可用主 DPA + 附录 | 待确认 |
| 10 | SA | 技术公司限制下"平台系统、PayFac 决定" | 平台人员实质审查、PayFac 形式签字有明显问题；`on_behalf_of_partner_id` 不转移责任 | **待决定（最高优先）**：SA 在 Partner 门户工作台上线前不宜 ACTIVE |
| 11 | SA | 新 CR 号与到期口径 | 7 开头 10 位统一号；CR 无到期日，改年度确认 | 已修订（年度确认日；不设前缀白名单） |
| 12 | SA | UBO 规则编号与核对 | 现行为商务部第 99 号决定（取代第 235 号；MD 267 不再权威）；SAMA 通函 472047799 自 2026-03-09 要求经 Wathq 核验 UBO 并上报差异 | 后续（S6，SA 上线前提） |
| 13 | SA | BO 门槛原文 | 25% 或以上；不得主要依赖客户声明 | 已符合（比较方式另已可配置） |
| 14 | SA | NID / Iqama 到期 | 以官方到期日与居留状态触发，不按签发日推算 | 已符合 |
| 15 | SA | Freelance 文件 | `FL-` 前缀统一编号，有效期一年；在覆盖范围内可作个人经营资格 | 已修订（自由职业者专用表单） |
| 16 | SA | CSF 3.4.3 境外云 | 直接适用持牌机构，经外包传导；与 PDPL 跨境分别满足 | 待决定（部署架构） |
| 17 | SA | AML 十年留存 | 按交易完成、账户关闭、关系终止等分别起算 | 后续（数据合规项目；MVP 默认全量保留） |
| 18 | EG | UBO 25% | 有官方评估记录的实践，但非确认的统一法定门槛 | 待确认（规则保留 25% 为候选，待银行 / PSP 书面确认） |
| 19 | EG | 商业登记号与税号 | 15 位统一号已实施；税号 9 位，不等于 VAT 登记 | 已修订（两种登记号分型、增值税号单列） |
| 20 | EG | 2019 PF 规则与 2025 PSP / PSO | 境外服务空间不能沿用旧分析；数据本地化无普遍结论 | 待确认 |
| 21 | EG | 816/2025 跨境与敏感数据 | 自然人财务数据属敏感；跨境需许可 / 批准与本人同意，Client 代点击不足 | 待决定（部署架构与同意机制） |
| 22 | EG | AML 留存与 PEP 定义 | 至少五年，按事件起算；PEP 含国内外及家属、密切关系人 | 后续（数据合规项目） |
| 23 | 通用 | 点击接受 + IP / UA / 时间 | 是电子证据但不等于合格签名；应绑定条款版本与资料，区分条款、隐私告知、数据同意 | 已修订（声明拆分、条款版本、绑定资料版本） |
| 24 | 通用 | 关系人 PII 代填 | Client 可提交但不能代表关系人同意；需告知机制 | 部分已修订（Client 确认有权提交并承诺告知）；告知机制本身后续 |
| 25 | 通用 | 电子声明证据效力 | 证明"谁声明了什么"，不证明内容真实；需绑定资料版本 | 已修订（绑定资料版本；声明不替代核验） |
| 26 | 卡组织 | 留存与直签门槛 | Visa 档案终止后至少 2 年、直签 >USD 1M Visa 交易额；Mastercard >USD 10M（Mastercard+Maestro），均有例外 | 后续（S7 直签监控；留存随数据合规项目） |

### 9.6 三国核验来源与接入优先级

| 国家 | 个人 | 机构 | 名单筛查 | 接入优先级 |
|---|---|---|---|---|
| AE | ICP Validation Gateway（需 ICP 审批合作合同，四阶段）；UAE PASS（OIDC，需用例审批） | NER / DED / 自贸区登记处（公开程度不一）；FTA TRN | 国际供应商 | 筛查 → ICP（由 PayFac 持牌主体申请） → NER |
| SA | Yakeen（Elm，NIC 数据）；Nafath（生物确认） | Wathq API（CR 全量/经理/股东付费）；ZATCA VAT | 国际供应商 | 筛查 → Wathq → Yakeen |
| EG | 无公开 API（VLens 等供应商接 CSO） | ITDA 电子摘录；ETA | 国际供应商 | 筛查 → 人工为主 |

## 10. 数据模型改动

### 10.0 统一模型 + 扩展模型：怎么支撑任意国家

```
第一层 统一核心列（所有国家都有、且有代码逻辑消费）
   个人：姓名(拉丁 / 本地文字 / 证件全名)、出生日期与地点、国籍、居住国、性别、PEP 自述、资金来源
   机构：法定名(拉丁 / 本地)、商号、组织类型、注册国、注册日期、登记机关、执照到期、上市标记
   地址：line1/2、城市、州省、邮编、国家 + 建筑号、附加号、区、PO Box、格式码
   关系：类型、持股比例、UBO 依据、职务、代持标记
第二层 目录化取值（列相同，每个辖区一套目录；全部 DML）
   t_organization_type、t_identifier_type（格式 / 规范化 / 校验 / 有效期 / Vault / 核验来源）、地址格式码、登记机关码
第三层 扩展属性（辖区特有事实；全部 DML）
   t_attribute_definition（编码、辖区、主体类型、数据类型、敏感级别）+ t_party_attribute（类型化值或 Vault Token）
   例：AE 自贸区代码、SA 短地址、EG 税卡登记处、将来 BR 州税登记号 IE、IN GSTIN 州码、个人职业、税务居民地
黏合剂 采集 Schema（辖区 × 主体类型 × 角色 × 组织类型，版本化；同时是前端渲染模板）
   决定：哪些核心列必填 / 可选 / 隐藏、要哪些标识、要哪些属性、关系人门槛、材料槽（来自 compliance 程序）、声明
```

新增一个国家（例：巴西）需要做的事：一行 `t_jurisdiction`、规则开关、组织类型（LTDA / SA / MEI）、标识类型（CPF / CNPJ）、地址格式组件、属性定义、一版采集 Schema、一套 compliance 程序——**全部是 DML**。只有两种情况要改代码：新的校验算法（CPF 的 MOD11 → 新 `checksum_rule` 实现）、新的核验供应商（channel-connector 新渠道脚本）。（地址格式原列为第三个插件位，10.0.1a 已数据化。）升降级规则：扩展属性被逻辑使用且 ≥2 个辖区必填 → 迁为核心列；核心列只剩一个国家用 → 降为属性。

### 10.0.1 扩展演练：韩国（KR）

> 目的：验证"新国家 = DML + 三个插件位"是否成立。韩国事实标 **[待核实]** 的上线前须法务确认。

| 事实 | 韩国 | 落到哪 |
|---|---|---|
| 个人身份 | PIPA 禁止一般企业采集 주민등록번호（RRN）；实务经持牌本人确认机构（NICE / KCB / KMC）手机认证取回 **CI / DI**；外国人 외국인등록번호 13 位 | `t_identifier_type`：`KR_CI`（Vault，`acquire_mode=VERIFIED_SESSION`）、`KR_ARN`；**不建** `KR_RRN` |
| 机构身份 | 사업자등록번호 10 位（校验位）、법인등록번호 13 位、통신판매업 신고번호（电商必需） | `KR_BRN`（checksum `KR_BRN`）、`KR_CRN`、`KR_MAIL_ORDER_REPORT_NO`（明文） |
| 组织类型 | 개인사업자、주식회사、유한회사、유한책임회사、합자/합명회사、외국법인 지점 | `t_organization_type` 6 行 |
| 核验来源 | 国税厅 홈택스 사업자등록상태 API；大法院 인터넷등기소；本人确认机构 | channel-connector `S_NICE`（IDV_SESSION_*）、`S_NTS`（TAX_ID_VERIFY） |
| UBO | 특정금융정보법 25%（KoFIU） | `ubo_threshold_pct=25` |
| 姓名 / 地址 | 한글 + 罗马字；도로명주소 + 5 位邮编 | `name_script=HANGUL_LATIN`；`address_format_code=KR_ROAD_ADDRESS` |
| 材料 | 사업자등록증、법인등기부등본、통신판매업 신고증、통장사본、代表人证件 | 程序要求 6 行 |

**只需 DML**：辖区 1 行、组织类型 6 行、标识类型 5 行、属性定义若干（如 `KR_INDUSTRY_CODE`）、采集 Schema 两版（个人 / 机构）、compliance 程序 4 个（个人 / 机构 × STANDARD / ENHANCED）、Platform `ko` 语言包。

**要改代码（三个插件位）**：`checksum_rule=KR_BRN` 实现类；前端地址控件 `KR_ROAD_ADDRESS` 子模板；channel-connector `S_NICE` / `S_NTS` 渠道脚本（标准 API 形状不变，compliance 不动）。

**演练发现、现在补进模型的两点**：
1. `t_identifier_type.acquire_mode`（`MANUAL_ENTRY` / `VERIFIED_SESSION`）：`VERIFIED_SESSION` 时 IDENTIFIER 控件显示"去认证"而不是输入框，调 `IDV_SESSION_CREATE`，结果以 Token 回填；韩国是第一个消费者，控件逻辑通用。
2. `relation_type` 从闭合枚举改为目录 **`t_relation_type`**（`relation_type_code, jurisdiction_code?, ubo_flag, ownership_pct_required, label_key, status`），现有五个值作为全局种子；UBO 门槛校验只认 `ubo_flag=1` 的类型。韩国不需要，但信托（settlor / trustee / protector）、美国 control person 会需要。

**不覆盖的边界**：主体模型本身不同的法域（信托、合伙作为独立 `party_type`）需要新 `party_type`；国家要求平台本身持牌或驻地（韩国 PG 登记、埃及 PF 实体办公室）是商业与法务问题。

**第二次演练：俄罗斯（RU）**——考验的不是采集而是准入。模型侧仍是 DML + 插件位：标识 `RU_PASSPORT`（4+6 位，`expiry_required=0`）、`RU_INN_12`、`RU_SNILS`、`RU_OGRN`（13）、`RU_OGRNIP`（15）、`RU_INN_10`、`RU_KPP`；组织类型 ООО / АО·ПАО / ИП（INDIVIDUAL + 属性 + ОГРНИП，同 Q14）/ самозанятый（INDIVIDUAL + ИНН + NPD 状态）/ НКО / 外国分支；`name_script=CYRILLIC_LATIN`（父称进 `middle_name`）；`address_format_code=RU_FIAS`；UBO 25%（115-ФЗ）；核验 `S_FNS`（ЕГРЮЛ / ЕГРИП / NPD）、`S_ESIA`（IDV_SESSION）；插件位：校验位 5 个、地址子模板 1 个、渠道 2 个；242-ФЗ 本地化归部署层。**现实约束**：Visa / Mastercard 暂停在俄业务、大量主体受制裁，没有 PayFac 的收单行会接——模型里缺的是显式准入开关：

3. `t_jurisdiction.acceptance_policy`（`OPEN` / `ENHANCED_ONLY` / `CLOSED`）+ `acceptance_reason_key`：`CLOSED` 时创建申请即返回 `JURISDICTION_NOT_ACCEPTED`；`ENHANCED_ONLY` 强制档次。PayFac 维度沿用程序解析：该 PayFac 既无定制程序、平台也无标准程序的辖区 = 该 PayFac 不能接（这是设计意图，不是巧合）。`COUNTRY_RISK` 因子与 `TRUE_POSITIVE_REJECT + SANCTIONS` 硬阻断（SM-I）在此类辖区是主要工作路径。

**第三次演练：美国（US）**——考验受益人规则变体、无国家级登记与"二选一"字段。模型侧：标识 `US_SSN`（Vault，无校验位）、`US_SSN_LAST4`、`US_ITIN`、`US_DRIVER_LICENSE`（`issuer_scope_mode=AUTHORITY`，作用域 = 州）、`US_EIN`、`US_STATE_ENTITY_NO`（作用域 = 州）；登记机关目录 `US_SOS_<州>`；组织类型 Sole proprietorship / 单·多成员 LLC / Private·Public corporation / GP·LP·LLP / Non-profit / Government entity；`name_script=LATIN`，后缀作属性；地址 `GENERIC`；W-9 字段作属性、认证作声明 `W9_CERTIFICATION`（`content_hash` + `terms_version`）；核验 `S_MIDDESK`（`KYB_REGISTRY_LOOKUP`）、IRS TIN Matching 代理（`TAX_ID_VERIFY`）、USPS（**新标准 API `ADDRESS_VERIFY`**，识别 CMRA / 私人信箱）、OFAC 经筛查供应商；USD 1M 直签与 MATCH 已由 Q9 与人工检查项覆盖。**没有新校验位、没有新地址子模板**。补进模型的三处：

4. `t_jurisdiction.control_person_required`（US = 1）：FinCEN CDD Rule 要求 ≥25% 所有权人 **加** 至少一名控制人，不是 FATF 的瀑布替代；关系人规则校验读它。
5. `t_organization_type.ubo_collection_mode`（`OWNERS_AND_CONTROL` / `CONTROL_ONLY` / `EXEMPT`）取代 `listed_exempt_ubo`：非营利、政府、上市公司只采控制人；AE PJSC、SA 上市公司同样适用。
6. `t_onboarding_schema_field.requirement_group`：同组字段至少满足一个（US：SSN | 护照；SA：NID | Iqama | 护照）；需求清单合并为一项展示。

四轮演练小结：AE/SA/EG（三层模型）→ KR（`ACQUIRE_MODE`）→ RU（`ACCEPTANCE_POLICY`）→ US（`CONTROL_PERSON_REQUIRED`、`UBO_COLLECTION_MODE`、`requirement_group`）。演练暴露的是**新的规则类型**，不是新的商户字段；商户字段四轮都没有加表列。规则开关统一落在 `t_rule_setting`（见上），往后新规则类型 = 注册 `rule_code` + 一个代码分支，不改表；`requirement_group` 是 Schema 结构属性，留在 `t_onboarding_schema_field`。

### 10.0.1a 硬编码边界（2026-10-26 第三轮）

代码里**不出现**按辖区 / 组织类型 / 标识类型取值的分支（`if (country == "SA")` 之类）。允许存在的只有三种以**编码**为键的解释器：
1. 规则开关解释器：每个 `rule_code` 一段逻辑（"门槛多少"来自数据，"怎么用门槛"是代码）。
2. 算法注册表：`checksum_rule` → 实现类（LUHN / EG_NID / KR_BRN …）。
3. 控件注册表（前端）：`widget_code` → 控件；地址控件按 `t_address_format_component` 渲染，不再按格式码写子模板。

再数据化两处：
- **`t_address_format_component`**（`address_format_code, component(line1/line2/city/state_or_province/postal_code/building_number/additional_number/district/po_box), required, display_order, label_key`）：通用地址控件按它渲染、后端按它校验；新国家不改地址代码。插件位由三个降为两个（校验位算法、供应商脚本）。
- **`t_materiality_rule`**（`jurisdiction_code?, field_key, materiality(MINOR/MAJOR)`）：修订申请的重大性判断读表（Q10），不写 if 链。

守门：① ArchUnit：core 包禁止辖区码 / 组织类型码 / 标识类型码字符串字面量（测试与 DML 除外），前端 lint 禁止 `country === '…'`；② **ZZ 国家包测试**：CI 用虚构国家 `ZZ` 的一套 DML 跑通入驻全链，证明新国家零代码；③ 评审清单：新增 `rule_code` / `checksum_rule` / `widget_code` / 标准 API 必须同步本文注册表。

### 10.0.2 新国家接入步骤

法务输入（证件、登记号、UBO、材料、数据法）→ 辖区包 DML → 判断是否触发三个插件位 → 样例商户在测试环境跑通 SM-A…SM-K → 发布 Schema 与程序版本（四眼）→ PayFac 按本地收单行要求复制程序设 Owner 定制。

> 约定沿用 `agent-docs/DATABASE_DESIGN_GUIDE.md`：Snowflake BIGINT 主键、UTC `DATETIME(6)`、无 CHECK/FK、`t_` 前缀、`uk_/idx_`。字段级清单只列业务字段，`create_time/update_time` 省略。

### 10.1 client-master（`mor_client_master`）

#### 新表

> **过度设计复核（2026-10-26 第二轮）**：只保留"有代码消费者"的列与表。规则：被逻辑使用且 ≥2 个辖区必填的事实才升为核心列，否则是扩展属性；法规事实（记录保存年限、本地化要求、偶发交易阈值）只写在第 9 节文档，不进表。
>
> **规则开关不做表列（2026-10-26 第三轮，四国演练后）**：商户资料字段靠目录 + 扩展属性扩展，**规则开关**（系统按它改变行为的参数）同样走键值表 `t_rule_setting`，辖区 / 组织类型 / 标识类型三张表只保留身份与展示列。新国家 = DML；已有规则类型的新取值 = DML；**新的规则类型 = 注册一个 `rule_code` + 一段读它的代码，不改表**。只有校验位算法、地址控件、供应商渠道三类是代码插件位。

**`t_jurisdiction`** 辖区包（只有身份与展示）
| 字段 | 类型 | 说明 |
|---|---|---|
| `jurisdiction_code` | VARCHAR(16) PK | 首期只有 `AE`、`SA`、`EG`（Q12：DIFC/ADGM 作登记机关） |
| `country_code` | CHAR(2) | ISO 3166-1 |
| `label_key` | VARCHAR(64) | 多语言键 |
| `name_script` | VARCHAR(32) | `LATIN` / `ARABIC` / `LATIN_ARABIC` / `HANGUL_LATIN` / `CYRILLIC_LATIN`；姓名控件 |
| `address_format_code` | VARCHAR(32) | 地址控件子模板 |
| `status` | VARCHAR(16) | `DRAFT` / `ACTIVE` / `RETIRED` |
| `change_version` | BIGINT | 目录 CAS |

**`t_rule_setting`** 规则开关（键值，一值一行）
| 字段 | 类型 | 说明 |
|---|---|---|
| `rule_setting_id` | BIGINT PK | |
| `scope_type` | VARCHAR(32) | `JURISDICTION` / `ORGANIZATION_TYPE` / `IDENTIFIER_TYPE` |
| `scope_code` | VARCHAR(64) | `SA`、`AE:LLC`、`KR_CI` |
| `rule_code` | VARCHAR(64) | 见下方注册表；代码只读已注册的 code，未知 code 忽略并告警 |
| `rule_value` | VARCHAR(64) | 文本值；数值 / 布尔由 code 定义解析 |
| `status` | VARCHAR(16) | `ACTIVE` / `RETIRED` |
`uk_rule_setting(scope_type,scope_code,rule_code)`。解析顺序：精确 scope → 全局默认（`scope_code='*'`）。

**规则注册表（首版，随代码维护在 `RuleCode` 枚举并同步到本节）**
| rule_code | scope | 取值 | 读它的逻辑 | 来源演练 |
|---|---|---|---|---|
| `POSTAL_CODE_REQUIRED` | JURISDICTION | 0/1 | 地址校验 | AE |
| `UBO_THRESHOLD_PCT` | JURISDICTION | 数值，默认 25 | 关系人规则校验 | 三国 |
| `UBO_FALLBACK_RULE` | JURISDICTION | `CONTROL_THEN_SMO` | 关系人规则校验 | 三国 |
| `CONTROL_PERSON_REQUIRED` | JURISDICTION | 0/1 | 关系人规则校验（无论持股都要控制人） | US |
| `MIN_AGE` | JURISDICTION | 数值 | 出生日期校验 | 三国 |
| `MAX_REVIEW_INTERVAL_MONTHS` | JURISDICTION | 数值 | compliance programs/resolve | 三国 |
| `ACCEPTANCE_POLICY` | JURISDICTION | `OPEN` / `ENHANCED_ONLY` / `CLOSED` | 创建申请、程序解析 | RU |
| `ACCEPTANCE_REASON_KEY` | JURISDICTION | 多语言键 | 门户展示 | RU |
| `UBO_COLLECTION_MODE` | ORGANIZATION_TYPE | `OWNERS_AND_CONTROL` / `CONTROL_ONLY` / `EXEMPT` | 关系人规则校验 | US（上市 / 非营利 / 政府） |
| `MIN_SIGNATORIES` | ORGANIZATION_TYPE | 数值 | 关系人规则校验 | AE |
| `REQUIRES_MANAGER` / `REQUIRES_DIRECTORS` | ORGANIZATION_TYPE | 0/1 | 关系人规则校验 | AE / SA |
| `REQUIRES_CONSTITUTION_DOC` | ORGANIZATION_TYPE | 0/1 | 材料槽补充 | AE |
| `LEGAL_ENTITY_FLAG` | ORGANIZATION_TYPE | 0/1 | 分支 / 独资的主体处理 | AE |
| `ACQUIRE_MODE` | IDENTIFIER_TYPE | `MANUAL_ENTRY` / `VERIFIED_SESSION` | IDENTIFIER 控件与提交校验 | KR |
| `EXPIRY_REQUIRED` | IDENTIFIER_TYPE | 0/1 | 标识校验 | AE / RU |
| `VAULT_REQUIRED` | IDENTIFIER_TYPE | 0/1 | 写入路径 | 三国 |

**`t_organization_type`**（只有身份与展示）：`organization_type_id` PK、`jurisdiction_code`、`type_code`、`label_key`、`status`；`uk_org_type(jurisdiction_code,type_code)`。规则一律在 `t_rule_setting(scope_type=ORGANIZATION_TYPE, scope_code='<jurisdiction>:<type_code>')`。

**`t_identifier_type`**：字段见 9.3；`uk_identifier_type(identifier_type_code)`、`idx_identifier_type_scope(jurisdiction_code,party_type)`。

**`t_attribute_definition`** 扩展属性定义：`attribute_code` PK、`jurisdiction_code` NULL（全局）、`party_type`、`data_type`（TEXT/DATE/COUNTRY/CODE/DECIMAL）、`catalog_code` NULL（CODE 类型的取值目录）、`sensitivity`（PUBLIC/INTERNAL/VAULT）、`vault_data_type` NULL、`label_key`、`status`。

**`t_party_attribute`** 扩展属性值：`attribute_id` PK、`profile_id`、`attribute_code`、`value_text` NULL、`value_date` NULL、`value_decimal` NULL、`value_vault_token` NULL、`masked_value` NULL；`uk_party_attribute(profile_id,attribute_code)`。VAULT 级只填 Token + 脱敏。

**`t_onboarding_schema`**：`schema_id` PK、`jurisdiction_code`、`party_type`、`role_type`、`organization_type_code` NULL（为空=该辖区该主体类型默认）、`version_no`、`status`（DRAFT/ACTIVE/RETIRED）、`active_slot`、`approved_by`、`approved_time`；`uk_schema_active(jurisdiction_code,party_type,role_type,organization_type_code,active_slot)`。

**`t_onboarding_schema_field`**（同时是前端渲染模板）：`schema_field_id` PK、`schema_id`、`section`（BASIC/BUSINESS/PEOPLE/MATERIALS/DECLARATIONS）、`display_group`（分区内分组，如 `IDENTITY`、`REGISTRATION`、`ADDRESS`）、`field_key`（如 `profile.individual.birth_place`、`attribute.AE_FREE_ZONE_CODE`、`identifier.SA_CR_NUMBER`、`relation.UBO`、`declaration.PEP_DECLARATION`）、`widget_code`（TEXT / TEXTAREA / DATE / COUNTRY / SELECT / IDENTIFIER / ADDRESS / NAME / PERSON_LIST / DOCUMENT_SLOT / DECLARATION / URL / MONEY）、`catalog_code` NULL（SELECT 的取值目录）、`requirement_level`（REQUIRED/OPTIONAL/HIDDEN/EVENTUALLY）、`requirement_group` NULL（同组至少满足一个，如 `US_PERSON_ID` = SSN | 护照）、`applies_to_subject`（PRIMARY/RELATED_PERSON/BOTH）、`validation_rule_code` NULL（regex / 长度 / 范围的规则编码）、`visible_when` NULL（简单条件 `field_key=value`，如 `profile.organization.organization_type=FOREIGN_BRANCH`）、`label_key`、`help_key` NULL、`placeholder_key` NULL（Platform 多语言键）、`display_order`；`uk_schema_field(schema_id,field_key,applies_to_subject)`。

**`t_application_declaration`** 声明记录：`declaration_id` PK、三个申请父 ID（XOR，与 `t_application_document` 同模式）、`declaration_type`（OWNERSHIP_COMPLETE / DIRECTORSHIP_COMPLETE / REPRESENTATIVE_AUTHORITY / PEP_DECLARATION / INFORMATION_TRUE / PLATFORM_TERMS / PRIVACY_NOTICE）、`declared_value`（TRUE/FALSE，PEP 自述用）、`declared_by_account_id`、`declared_by_party_id` NULL、`declared_time`、`client_ip`、`user_agent`、`terms_version` NULL、`content_hash` NULL；`uk_app_declaration(client_application_id,declaration_type,declared_by_party_id)` 等三组。不可变，追加。

**`t_partner_onboarding_code`** PayFac 入驻码（Q4）：`code_id` PK、`code`（VARCHAR(32)，不可猜测的随机码，PayFac 线下发给商户）、`local_partner_id`、`status`（ACTIVE/REVOKED/EXPIRED）、`effective_from`、`effective_to` NULL、`max_uses` NULL、`use_count`、`note`；`uk_partner_onboarding_code(code)`。Client 入驻时填码 → client-master 解析为 `responsible_partner_id`；码无效只返回 `ONBOARDING_CODE_INVALID`，不泄露任何 Partner 信息；不提供 Partner 列表查询。

已删除的原设计：`t_application_requirement`（改为实时计算，8.1）、`t_profile_amendment`（并入 `t_client_application`）、`t_party_name`（改为 profile 加列）。

#### 改表

| 表 | 改动 |
|---|---|
| `t_party_profile` | 增 `jurisdiction_code`、`current_slot`（当前行 1，其他 NULL）、`superseded_time`、`source_amendment_id` NULL；`uk_party_profile_party(party_id)` → `uk_party_profile_current(party_id,current_slot)`；`draft_application_id` 语义扩展为"所属可编辑申请或修订" |
| `t_individual_profile` | 增 `local_given_name`、`local_family_name`、`local_full_name`（VARCHAR(128)）、`id_full_name`（证件拉丁全名，VARCHAR(128)）、`name_script`；`middle_name` 放宽到 VARCHAR(128)（父名+祖父名）；增 `birth_place`（VARCHAR(128)，AE/SA 必采）、`birth_country` CHAR(2)、`gender`（筛查维度）、`source_of_funds_code`（评级因子）、`pep_self_declared` TINYINT(1)（评级因子）。职业、税务居民地改为扩展属性 |
| `t_organization_profile` | 增 `local_legal_name`、`local_trade_name`（VARCHAR(128)）、`registration_authority_code`（如 `AE_DED_DUBAI`、`AE_DMCC`、`AE_DIFC`、`SA_MC`、`EG_GAFI`）、`license_expiry_date` DATE（到期触发）、`listed_company` TINYINT(1)（UBO 豁免规则）。经济部门改为扩展属性；`website_url` 留在 business profile |
| `t_party_address` | 增 `address_format_code`、`building_number`、`additional_number`、`district`、`po_box`；`postal_code` 必填性由辖区包决定。沙特短地址、阿文地址行改为扩展属性 |
| `t_party_profile_identifier` | **删** `identifier_value` 明文；增 `identifier_vault_token`（STABLE Token，Vault 数据类型 `LEGAL_IDENTIFIER_V1`）、`identifier_masked_value`（末 4 位）、`expiry_date` DATE NULL、`issue_date` DATE NULL；`identifier_type` 改引用 `t_identifier_type` |
| `t_party_identity_key` | **删** `normalized_identifier_value` 明文；改为 `identifier_vault_token`；`uk_party_identity_norm` → `uk_party_identity_token(identifier_type,issuer_scope_key,identifier_vault_token)`（STABLE Token 等值即原值等值） |
| `t_party_relation` | 增 `ubo_basis`（OWNERSHIP / CONTROL / SENIOR_MANAGING_OFFICIAL，仅 UBO）、`job_title`、`nominee_flag`、`control_nature`（VARCHAR(255)，CONTROL 时说明）；`relation_type` 改引用新目录 **`t_relation_type`**（10.0.1，现有五值为种子）；`client_application_id` 语义扩展到修订申请 |
| `t_application_document` | 增 `issue_date`、`expiry_date`、`document_number_masked` |
| `t_client_application` | 增 `application_type`（ONBOARDING / PROFILE_AMENDMENT）、`draft_profile_id` NULL（修订时的草稿 Profile 行）、`jurisdiction_code`、`organization_type_code` NULL、`schema_id`（提交时冻结的采集 Schema 版本）、`onboarding_code_id` NULL、`responsible_partner_id` NULL（由入驻码解析；C3 为空）、`materiality` NULL（修订：MINOR/MAJOR，由 compliance 回填） |
| `t_client` | 增 `jurisdiction_code`、`responsible_partner_id` NULL |
| `t_operating_entity` / `t_local_partner` | 增 `jurisdiction_code` |
| `t_business_change_record` 白名单 | 不新增 Root（修订申请复用 `CLIENT_APPLICATION` Root）；目录与国家包不进 BCR（Q11），走 `change_version` + 版本化发布 + 运营操作日志 |

### 10.2 compliance（`mor_compliance`）

#### 改表

| 表 | 改动 |
|---|---|
| `t_dd_program` | 增 `jurisdiction_code` NULL（NULL=全局默认）、`party_type` NULL、`role_type` NULL、`tier`（SIMPLIFIED/STANDARD/ENHANCED）、`owner_partner_id` NULL（NULL=平台标准程序）、`priority_no`（解析时辖区精确 > 国家 > 全局；Owner 定制 > 平台）；`uk_dd_program_code` 保留 |
| `t_dd_program_version` | 增 `review_interval_months`（替代/并存 `review_frequency_code`）、`four_eyes_required` TINYINT(1)、`auto_approve_allowed` TINYINT(1) |
| `t_dd_program_requirement` | 增 `subject_role`（PRIMARY/UBO/DIRECTOR/SIGNATORY/ANY_RELATED）、`max_age_days` NULL、`accepted_document_types`（VARCHAR(255) CSV 允许的 document_type 列表——或拆子表 `t_dd_program_requirement_doc_type`，**选拆子表**）、`waivable_flag` |
| `t_dd_program_check` | 增 `check_mode`（AUTOMATED/MANUAL）、`subject_role`、`standard_api_code` NULL（AUTOMATED 时 channel-connector 标准 API）、`blocking_flag`（FAIL 是否阻断批准） |
| `t_dd_program_screening` | 增 `monitoring_required`（批准后开启持续监控）、`standard_api_code` |
| `t_due_diligence_case` | 增 `case_type`（ONBOARDING/PROFILE_AMENDMENT/PERIODIC_REVIEW/TRIGGERED_REVIEW）、`jurisdiction_code`、`tier`、`risk_rating` NULL、`risk_score` DECIMAL(10,4) NULL、`responsible_partner_id` NULL、`operated_by`（PLATFORM/PARTNER）、`round_no` INT（重提轮次）、`assignee_id` NULL、`trigger_id` NULL（来源触发）、`previous_case_id` NULL（复审指向上一案件）、`review_due_time` NULL；状态启用全部 9 态 + 新增 `PENDING_DEPENDENCY`（等关系人子案件） |
| `t_dd_case_subject` | 增 `ubo_basis`、`job_title`、`screening_monitor_id` NULL |
| `t_dd_requirement` | 增 `subject_role`、`waived_by`、`waived_reason_code`、`round_no` |
| `t_dd_evidence_ref` | 增 `file_address`（与 `file_id` 并存，过渡）、`document_type`、`issue_date`、`expiry_date`、`reject_reason_code`（DOCUMENT_EXPIRED/DOCUMENT_TOO_OLD/DOCUMENT_UNREADABLE/MISMATCH_NAME/MISMATCH_ADDRESS/TYPE_INVALID/FRAUDULENT/OTHER）、`reviewer_id`、`reviewed_time` |
| `t_dd_check` | 增 `case_id`（直接索引）、`check_mode`、`status`（REQUESTED/RUNNING/COMPLETED/FAILED/UNKNOWN）、`outcome`（PASS/FAIL/REVIEW/NOT_EXECUTED）、`reason_code`、`execution_ref`（channel-connector 执行引用）、`reviewer_id` NULL、`reviewed_time` NULL、`note` VARCHAR(1000)、`round_no`；`provider_code` 对 MANUAL 为 `MANUAL` |
| `t_screening_run` | 增 `case_id`、`search_term_vault_token` NULL、`trigger_source`（CASE/MONITOR/REBATCH）、`list_version_ref` NULL、`execution_ref`；**一次 run 只对应一个筛查类型**（随 `program_screening_id` 的 `screening_type`），不存 CSV |
| `t_screening_hit` | 增 `entity_type`（PERSON/ORGANIZATION/VESSEL/OTHER）、`risk_level`（LOW/MEDIUM/HIGH/UNKNOWN）、`whitelisted_flag`；`status` 增 `SUPERSEDED`；命中所在名单类型拆子表 **`t_screening_hit_list`**（`hit_id`、`list_type`，一值一行）；匹配方式细节留在 Vault 原始报文，不建列 |
| `t_screening_resolution` | `resolution` 枚举改为 `NO_MATCH / FALSE_POSITIVE / POTENTIAL_MATCH / TRUE_POSITIVE_ACCEPT / TRUE_POSITIVE_REJECT / INSUFFICIENT_EVIDENCE`（保留旧 `CONFIRMED_MATCH` 迁移为 `TRUE_POSITIVE_REJECT`） |
| `t_qualification_decision` | 增 `tier`、`risk_rating`、`jurisdiction_code`、`responsible_partner_id` NULL、`recommended_by` NULL、`recommended_time` NULL（四眼：recommended_by ≠ decided_by）、`conditions_note` VARCHAR(1000) |
| `t_role_qualification` | 增 `tier`、`risk_rating`、`jurisdiction_code`、`responsible_partner_id` NULL、`review_interval_months`、`last_review_time`、`review_overdue_flag`；`status` 增 `REVIEW_OVERDUE`（投影态，不是新决定） |
| `t_settlement_control` | `control_type` 增 `REVIEW_OVERDUE_BLOCK`；`source_action_effect_id` 允许 NULL 并增 `source_type`（RISK_ACTION_EFFECT/COMPLIANCE_REVIEW/MANUAL）+ `source_ref` |

#### 新表

**`t_dd_program_requirement_doc_type`**：`id` PK、`program_requirement_id`、`document_type`；`uk(program_requirement_id,document_type)`。

**`t_dd_check_finding`** 检查分项（**S6 才建**，人工检查不需要分项）：`finding_id` PK、`check_id`、`finding_code`（如 `NAME_MATCH`、`DOB_MATCH`、`DOCUMENT_EXPIRY`、`MRZ_VALID`、`FACE_MATCH`、`LIVENESS`、`REGISTRY_STATUS_ACTIVE`、`MANAGER_LIST_MATCH`）、`outcome`（PASS/FAIL/WARN/NOT_EXECUTED）、`detail_code` NULL、`note` VARCHAR(500)；`uk(check_id,finding_code)`。

**`t_dd_risk_factor`**：`factor_id` PK、`case_id`、`factor_code`（COUNTRY_RISK/NATIONALITY_RISK/PEP/INDUSTRY_RISK/VOLUME/ENTITY_STRUCTURE/ADVERSE_MEDIA/PRIOR_REJECTION/MANUAL）、`factor_value`（VARCHAR(128)）、`score` DECIMAL(10,4)、`source`（AUTO/REVIEWER）、`recorded_by` NULL、`recorded_time`；`uk(case_id,factor_code,factor_value)`。

**`t_dd_risk_rating_rule`**（运营可维护，首版可只用 DML）：`rule_id` PK、`jurisdiction_code` NULL、`factor_code`、`factor_value_pattern`、`score`、`status`。评级阈值放程序版本：`t_dd_program_version` 增 `medium_threshold`、`high_threshold`。

**`t_screening_monitor`**：`monitor_id` PK、`subject_party_id`、`role_type` NULL、`role_id` NULL、`provider_code`、`external_monitor_ref`、`screening_types`、`status`（ACTIVE/STOPPED/FAILED）、`last_diff_time` NULL、`last_run_id` NULL、`responsible_partner_id` NULL；`uk(subject_party_id,provider_code,screening_types)`。

**`t_review_trigger`**：`trigger_id` PK、`trigger_no`、`role_type`、`role_id`、`subject_party_id` NULL、`trigger_type`（PERIODIC/SCREENING_ALERT/PROFILE_CHANGE/DOCUMENT_EXPIRY/RISK_SIGNAL/MANUAL）、`severity`（LOW/MEDIUM/HIGH/CRITICAL）、`source_type`、`source_ref`（事件 ID / run ID / evidence ID）、`dedupe_key`、`status`（OPEN/CASE_CREATED/DISMISSED/EXPIRED）、`created_case_id` NULL、`due_time` NULL、`dismissed_by` NULL、`dismissed_reason_code` NULL、`version`；`uk_review_trigger_dedupe(dedupe_key,status_active_slot)`。

**`t_dd_case_assignment_log`**：**不建**，用 `t_dd_case_state_change.action_type=ASSIGN` 记录。

**`t_partner_compliance_scope`**：**不建**（过度设计）；PayFac 的可见与操作范围 = `responsible_partner_id` 等于本 Partner，程序 Owner = `t_dd_program.owner_partner_id`。

### 10.3 计费相关（commercial / billing，只定义，不在本域实现）

- commercial 产品 `COMPLIANCE_SERVICE`（L0），开通项 `KYB_CASE`、`KYC_CASE`、`SCREENING_RUN`、`SCREENING_MONITORING`、`IDV_CHECK`、`REGISTRY_LOOKUP`；计费事件 `COMPLIANCE_CASE_DECIDED`、`COMPLIANCE_SCREENING_RUN`、`COMPLIANCE_CHECK_EXECUTED`、`COMPLIANCE_MONITOR_MONTH`，付款方 = 案件 `responsible_partner_id`。
- billing 消费 compliance 事件（第 11.3 节）形成经济事实；首版只定义事件与计费事件码，价目与 billing 适配另立需求。

## 11. 接口与事件

### 11.1 client-master

| 接口 | 变化 |
|---|---|
| `GET /client-onboarding/v1/form-schema?jurisdiction&partyType&roleType[&organizationType]` | **新**，取代 `form-options`：返回分区、字段规则、标识类型、组织类型、地址格式、关系人规则、材料槽（含 compliance 程序解析结果）、声明清单 |
| `GET /client-onboarding/v1/jurisdictions` | 新：可入驻辖区清单（ACTIVE） |
| `PATCH …/draft` | `values` 增 `attributes{}`、本地文字姓名、新增地址字段；证件号写入走 Vault |
| `POST …/declarations` | 新 |
| `GET …/requirements` | 新：需求清单投影 |
| `POST …/submissions` | 校验改为 Schema 驱动；422 体改为 `errors[{requirementKey, code, reason, remediation}]`（保留 `SUBMISSION_INCOMPLETE`） |
| `POST /client-onboarding/v1/applications {applicationType: PROFILE_AMENDMENT}` 及既有 draft/people/relations/documents/submissions 子资源 | 修订申请复用入驻申请资源与 Processor 骨架；创建时复制当前 Profile 为草稿行 |
| `POST /client-onboarding/v1/applications {clientType, onboardingCode?}` | 入驻码在创建申请时校验并解析为 `responsible_partner_id`；无码 = 平台自营 C3；错误只回 `ONBOARDING_CODE_INVALID` |
| 运营 / Partner：`POST /client-master-admin/v1/local-partners/{id}/onboarding-codes`、`…/{codeId}/revocation` | 入驻码签发与作废（Partner 门户可自助签发，Q4） |
| `GET /client-master/v1/party-profiles?ids=` | 读当前行；增 `jurisdictionCode`、本地名、属性（非 VAULT 级） |
| 运营：`/client-master-admin/v1/jurisdictions|organization-types|identifier-types|attribute-definitions|onboarding-schemas` CRUD + 发布 | 新：目录与 Schema 维护（四眼发布） |
| 运营：`GET /client-master-admin/v1/clients`、`/clients/{id}` | 新：Client 列表/详情（主档、关系人、资格投影、申请历史） |

### 11.2 compliance

| 接口 | 变化 |
|---|---|
| `GET /compliance/v1/programs/resolve?jurisdiction&partyType&roleType&tier&responsiblePartnerId` | **新**，取代 `MaterialRequirementsApi`：返回程序版本、材料槽（含 subject_role、时效、允许类型）、检查项、筛查项、声明要求、复审间隔 |
| `POST /client-onboarding-review/v1/submissions`（I01） | 请求增 `caseType`、`jurisdictionCode`、`organizationTypeCode`、`tierHint`、`responsiblePartnerId`、`roundNo`、`attributes[]`、`declarations[]`、`identifierVaultTokens`（**不再明文**）、`documents[].issueDate/expiryDate/fileAddress`；`sourceNamespace` 扩展到修订与 Partner/OE |
| `POST …/cases/{caseId}/classifications`（I02） | 不变 |
| `GET …/applications/{applicationId}/result` | 增 `errors[]`、`requirementsDue[]` |
| 运营 `ClientReviewAdminApi` | 增：`POST /cases/{id}/assignment`、`POST /cases/{id}/checks/{checkId}/result`（人工检查）、`POST /cases/{id}/checks/{checkId}/rerun`、`POST /cases/{id}/screenings/{runId}/hits/{hitId}/resolution`、`POST /cases/{id}/risk-factors`、`PUT /cases/{id}/risk-rating`（覆盖）、`POST /cases/{id}/tier`、`POST /cases/{id}/recommendation`（推荐人）、`POST /cases/{id}/approval|rejection`（决定人，四眼校验）、`POST /cases/{id}/requirements/{reqId}/waiver`、`POST /cases/{id}/evidence-package`（导出清单，文件经 Platform file） |
| `GET /compliance-admin/v1/review-triggers`、`POST …/{id}/case`、`POST …/{id}/dismissal` | 新：监控触发队列 |
| `GET /compliance-admin/v1/programs…` CRUD + 版本发布 | 新：程序维护 |
| `SettlementControlApi` | 实现创建/查询 + 新增 `POST …/{id}/release`、`POST …/{id}/expiry`（系统） |
| `POST /compliance/v1/internal/screening-callbacks`（channel-connector 转发的监控差异） | 新（Q7 决定是否经 channel-connector 入站） |
| Partner 侧（第 14 节） | `GET /compliance-partner/v1/cases`、`/cases/{id}`、`/cases/{id}/evidence-package`（只读，按 `responsible_partner_id` 过滤） |

### 11.3 事件（RabbitMQ，`compensation-event` 登记后发布，信封按 paytech-messaging）

| 事件 | 发布方 | 消费方 | 载荷要点 |
|---|---|---|---|
| `ClientReviewDecided` v2 | compliance | client-master | 现有 + `caseType`、`errors[]`、`tier`、`riskRating`、`roundNo`；`outcome` 增 `EXPIRED`（Q16） |
| `QualificationChanged` | compliance | commercial、payout、account、billing、client-master | `roleType, roleId, qualificationType, status, decisionId, validFrom/To, tier, riskRating, responsiblePartnerId, aggregateVersion`；client-master 收到 `REVOKED` 自动 Client SUSPENDED（Q15） |
| `SettlementRestrictionChanged`（D38） | compliance | payout、account | 现有合同 |
| `ReviewTriggerRaised` | compliance | Operations 通知 | `triggerType, severity, roleType, roleId` |
| `DueDiligenceCaseDecided` / `ScreeningRunCompleted` / `ComplianceCheckExecuted` | compliance | billing | 计费键：`responsiblePartnerId, caseType, qualificationType, jurisdictionCode, providerCode` |
| `PartyProfileChanged` / `PartyRelationChanged` | client-master | compliance | `partyId, profileId, changeKind(APPROVED_AMENDMENT), changedFields[], materiality` |
| `ClientApplicationSubmitted` | client-master | （审计/通知） | 可选 |
| `WorkflowCallbackRaised`（Platform） | Platform | compliance（Q2 若接 workflow） | 现有 |

## 12. 敏感字段与 Vault 清单（逐项讨论稿）

Vault V1 只有 Protect/Reveal、REFERENCE/STABLE 两种 Token，无 Blind Index；`VAULT_SERVICE_ARCHITECTURE.md` 第 112 行目前**禁止**把姓名、生日、法律标识、地址送进 Vault——本节的"建议 Vault"项都需要一个新决定登记数据类型与访问规则（`t_vault_data_type` / `t_vault_access_rule` DML）。

| 字段 | 现状 | 建议 | 理由 / 取舍 |
|---|---|---|---|
| 证件号 / 注册号（个人：EID、NID、Iqama、护照、签证；机构：执照号、CR、税号） | 明文两处 | **个人证件进 Vault**（`LEGAL_IDENTIFIER_V1`，STABLE，Token 兼作唯一键，库存末 4 位脱敏）；**机构登记号/税号明文**（公开登记信息，需要查询与展示） | 个人证件是三国 PDPL 下的高敏数据；STABLE Token 保留"同号同 Party"去重能力 |
| 出生日期 | 明文 | **明文 + 访问控制**（待讨论） | 年龄校验、筛查（year_of_birth 匹配）、排序都要用；Vault 化后筛查需先 Reveal |
| 姓名（拉丁/阿文） | 明文 | **明文** | 筛查、展示、文件比对的主键；无法 Token 化使用 |
| 出生地、国籍、性别 | 新增 | 明文 | 筛查维度 |
| 住宅地址（自然人） | 明文 | 明文（待讨论：外部 PayFac 可见时脱敏） | 地址证明比对 |
| 机构地址 | 明文 | 明文 | 公开信息 |
| 邮箱 / 电话 | Vault STABLE | 不变 | — |
| 职业、资金来源、税务居民地、PEP 自述 | 新增 | 明文 | 评级输入 |
| 材料文件 | Platform file | 不变（Platform 侧访问控制） | — |
| 案件时点主体快照 | `RISK_DD_PARTY_SNAPSHOT_V1` Token（已设计未实现） | 实现 | 审核 Reveal 用 |
| 供应商原始结果（IDV 报文、筛查全文） | `RISK_DD_CHECK_RESULT_V1` / `RISK_SCREENING_HIT_NAME_V1`（已设计） | 实现 | 库内只存结论与分项 |
| 筛查检索词 | 新增 | Vault（`search_term_vault_token`） | 含全名 + 生日 |
| 声明中的 IP / UA | 新增 | 明文（证据） | Stripe 同做法 |
| 日志 / MQ / 补偿载荷 | — | 任何上述原值不出现；证件只出现 Token 与脱敏 | 现有规则 |
| 数据本地化 | — | AE / SA 要求支付与个人数据境内：按 D21/D22 实例隔离部署解决，不在本域建模；Vault 实例随业务实例 | Q8 |

## 13. 外部核验接入（channel-connector SERVICE 渠道）

- 渠道：`SERVICE` 类型，每供应商一个渠道（如 `S_COMPLYADV`、`S_SUMSUB`、`S_WATHQ`、`S_ICP`），技术账号凭据在 channel-connector（Vault Token），compliance 不持凭据。
- 标准 API（`t_standard_api`，开放代码）：

| standard_api_code | 输入（规范化） | 输出（规范化） |
|---|---|---|
| `SCREENING_SEARCH` | `subjectType(PERSON/ORG), fullName, aliases[], birthYear, countries[], screeningTypes[], fuzziness, clientRef` | `runRef, hits[{externalRef, entityType, listTypes[], score, matchTypes[], name, dob, countries[]}]` |
| `SCREENING_MONITOR_START` / `STOP` | `runRef` | `monitorRef` |
| `SCREENING_MONITOR_DIFF` | `monitorRef, since` | `added[], updated[], removed[]` |
| `GOV_ID_VERIFY` | `jurisdiction, idType, idNumber(明文，仅此调用链路), birthDate, name` | `outcome, findings[{code, outcome}]` |
| `KYB_REGISTRY_LOOKUP` | `jurisdiction, registrationNumber, registrationAuthority` | `status, legalName, incorporationDate, managers[], owners[{name, pct}], expiryDate` |
| `TAX_ID_VERIFY` | `jurisdiction, taxId` | `registered, legalName` |
| `ADDRESS_VERIFY` | `jurisdiction, addressLines[], city, region, postalCode` | `deliverable, standardizedAddress, addressType(RESIDENTIAL/COMMERCIAL/PO_BOX/CMRA)` |
| `IDV_SESSION_CREATE` | `applicantRef, documentTypes[], redirectUrl` | `sessionRef, clientUrl/sdkToken` |
| `IDV_RESULT_GET` | `sessionRef` | `decision, breakdown{document{...}, face{...}}` |

- 授权：`client-master`、`compliance` × 渠道 × 标准 API 白名单；compliance 是唯一调用方（client-master 不调外部核验）。
- 执行：compensation-event Handler 事务外调用 `POST /channel-connector/v1/service-executions`；`ERROR`/超时 = 结果未知 → `t_dd_check.status=UNKNOWN`，按策略重试或转人工；不自动重试同一供应商超过 N 次。
- 入站回调（监控差异、IDV 完成）：channel-connector 现只处理支付回调；SERVICE 入站需要一个决定（Q7）：方案 A 由 channel-connector 接收并发 `ServiceChannelCallbackReceived` 事件；方案 B compliance 定时拉 `SCREENING_MONITOR_DIFF`。**首期用 B（拉取）**，IDV 用轮询 `IDV_RESULT_GET`。
- 证件原值进入标准 API 输入：compliance 在事务外 Reveal（purpose `COMPLIANCE_CHECK`），只在内存中传给 channel-connector，不落日志；channel-connector 对 SERVICE 渠道请求体不记全文日志（需在其日志策略登记）。

## 14. PayFac 合规服务（多方）

### 14.1 模型

- **程序 Owner**：`t_dd_program.owner_partner_id`。平台维护标准程序；为 PayFac 定制时复制一份并设 Owner；解析优先 Owner 程序，缺失回退平台程序。
- **案件责任方**：`responsible_partner_id` 由 Client 创建申请时填写的 **PayFac 入驻码**解析（Q4 已定）；商户与 PayFac 线下已沟通，码由 PayFac 在 Partner 门户签发；系统不暴露 Partner 列表。C3（平台自营）不填码。
- **操作方（Q1 已定：审核由 PayFac 决定，平台只提供系统；2026-10-27 补：Partner 门户工作台列入后续计划，首期由平台运营代操作并记 `on_behalf_of_partner_id`，见 17a）**：`operated_by=PARTNER` 是目标形态——PayFac 的合规人员在 Partner 门户的案件工作台审材料、处置命中、做人工检查、记风险因子、推荐与决定；决定人身份是 Platform CUSTOMER Realm / PARTNER Channel 的账号。平台运营只对 **AFFILIATED**（我们自己的 PayFac）代操作（`operated_by=PLATFORM`），并保留对所有案件的只读与程序维护。这与 AGENTS "Partner 只读"规则冲突，是 2026-10-23 决定 R3 的落地，须在本设计定稿时一并修订该规则。
- **可见范围**：Partner 门户只见 `responsible_partner_id = 自己` 的案件、决定、证据清单；个人证件只给脱敏；文件下载走 Platform file 的授权；平台运营可见全部。
- **证据包导出**：案件 → 清单（主体、要求、证据 Hash、检查结论、筛查处置、风险评级、决定、决定人、时间线）→ 生成 PDF/JSON 存 Platform file，给 PayFac 向收单行提交。
- **计费**：第 10.3 节事件；付款方 = 责任 PayFac；平台自营 C3 不计费（或计入技术服务费）。
- **卡组织要求的落点**：子商户 ID 在 acquiring/channel（R15、R21）；MCC 指派在 O04 分类；MATCH/VMSS 查询作为人工检查项记录（谁查、何时、结果）；USD 1M 年量直签门槛在 billing 侧告警（Q9）；保证金由收单行/PayFac 持有，平台只记 `MIN_SECURITY_DEPOSIT` 控制。

### 14.2 与 2026-10-23 决定的关系

本节细化 D2/D7/第 9 节 U2（"compliance 按（角色, 主体）只读查询资格，支持手工登记，子商户 KYC 复用同一流程"），并提出 R3（外部 PayFac 自行操作）的实现落点（`operated_by`），不改该决定。

## 15. 页面影响（只列，不设计）

- Client 门户：入驻单页改为 **Schema 驱动的模板渲染**——`form-schema` 返回分区 → 分组 → 字段的渲染规格（`widget_code`、`label_key/help_key`、校验、`visible_when`、顺序），前端是一个通用渲染器 + 控件注册表；地址格式（AE_POBOX / SA_NATIONAL_ADDRESS / EG_STANDARD / GENERIC）、双文字姓名、UBO 穿透列表、材料槽、声明是固定控件，由 Schema 决定是否出现与是否必填。需求清单面板（缺什么、谁动、到期）、修订申请入口、复审确认入口。Operations 侧 Schema 首期用 DML / JSON 导入维护，编辑器后置。
- Operations 控制台：案件工作台（材料/检查/筛查命中/风险因子/评级/档次/推荐/决定/时间线/导出）、触发队列、程序与国家包维护、Client 列表/详情、资格列表（已有）、结算控制。
- Partner 门户：合规案件只读视图 + 证据包下载。

## 16. 待定问题

| # | 问题 | 建议 |
|---|---|---|
> 2026-10-26 第二轮：Q1、Q4、Q8 由用户直接回答；其余按建议采纳。本表保留问题与结论，供日后查阅。

| # | 问题 | 结论（已定） |
|---|---|---|
| Q1 | SA 规定 PSP 必须自行签约并 KYC 商户，技术对接公司不得做 KYC；我们的角色？ | **用户决定：我们只提供系统，入驻审核由 PayFac 决定。** 因此 `operated_by=PARTNER` 首期必需（第 14 节），SAMA 顾虑随之消除；AGENTS "Partner 只读"规则须修订（R3） |
| Q2 | 四眼审批 | compliance 自带"推荐人 + 决定人"，程序版本 `four_eyes_required`；不接 workflow |
| Q3 | PEP 自述 | 要求 Client 勾选 `PEP_DECLARATION` + 系统筛查双轨 |
| Q4 | 案件责任方如何确定 | **用户决定：Client 填 PayFac 发的入驻码**（`t_partner_onboarding_code`），不暴露 Partner 列表；无码 = 平台自营 |
| Q5 | 退回后重提 | 同案件多轮（`round_no`），外部检查在 `valid_until` 内复用 |
| Q6 | C5 子商户 KYC | 延后，不在本设计 |
| Q7 | SERVICE 渠道入站回调 | 首期定时拉取；入站方案与 channel-connector 一起定 |
| Q8 | AE/SA 数据本地化 | **用户决定：不一定本地化部署。** 模型不记录本地化级别；三国要求只保留在第 9 节文档，部署归部署专项 |
| Q9 | USD 1M 年量直签门槛 | billing 按 Client × 卡组织年量告警，compliance 收 `RISK_SIGNAL` 立案；本域不计算 |
| Q10 | 修订重大性规则 | compliance 维护；client-master 只传 `changedFields[]` |
| Q11 | 目录与国家包审计 | 不进 BCR；`change_version` + 版本化发布 + 运营操作日志 |
| Q12 | DIFC / ADGM | 首期只建 `AE`，自贸区作为 `registration_authority_code` 记录 |
| Q13 | 筛查供应商 | 倾向 Sumsub（筛查 + KYB + IDV 一家，S6 可复用）；只要筛查则 ComplyAdvantage；标准 API 形状与供应商无关 |
| Q14 | AE 个人 Client 须有执照 | Party=INDIVIDUAL + 组织类型属性 `SOLE_ESTABLISHMENT` + 执照标识；资格类型 INDIVIDUAL_KYC |
| Q15 | 资格 REVOKED 时 Client 是否自动 SUSPENDED？ | **已定（2026-10-26）**：自动挂起——client-master 消费 `QualificationChanged(REVOKED)` 后系统置 SUSPENDED 并写 BCR；恢复需新资格 APPROVED + 运营确认 |
| Q16 | 退回超期案件 EXPIRED 后申请怎么处理？ | **已定（2026-10-26）**：compliance 案件 EXPIRED 时发布 `ClientReviewDecided(EXPIRED)`，client-master 把申请置 WITHDRAWN（释放名额），Client 可重新申请并复用 Party / Client |
| Q17（新） | CRR 本期范围 | 已定方向：S1 手工评级必填，S4 自动评分（7.5） |

## 17. 切片

| 切片 | 内容 | 依赖 |
|---|---|---|
| **S1 国家包与采集规则** | client-master：`t_jurisdiction`、组织/标识类型目录、属性定义与值、采集 Schema、profile/address/relation 加列、需求清单投影、声明表、`form-schema` 与 `requirements` 接口、Schema 驱动校验；compliance：程序加辖区/档次/Owner、`programs/resolve`、要求加时效与主体角色、案件完整状态机与轮次、人工检查项、证据拒绝原因；AE/SA/EG 三套目录与程序 DML；门户动态表单；运营案件工作台（人工部分） | 无 |
| **S2 证件 Vault 化与修订申请** | `LEGAL_IDENTIFIER_V1` 数据类型决定 + DML；identifier 两表改 Token；I01 改 Token；`t_profile_amendment` 与流程；`PartyProfileChanged` 事件 | S1 |
| **S1 补充：手工 CRR** | 案件 `risk_rating` 必填（审核员选择 + 依据 `t_dd_risk_factor(source=REVIEWER)`），决定冻结，复审间隔按评级默认；自动评分留 S4 | S1 |
| **S1b PayFac 入驻码与责任方** | `t_partner_onboarding_code` 与签发/作废（首期由运营代签发）；申请创建时解析责任方；案件按 `responsible_partner_id` 隔离；首期平台运营在 Operations 控制台代所有 PayFac 操作（`on_behalf_of_partner_id`）；AGENTS "Partner 只读"规则已修订（2026-10-27） | S1 |
| **S-P Partner 门户案件工作台（后续计划）** | PayFac 合规人员自助：材料审核、人工检查、命中处置、分类确认、CRR、推荐 / 决定、入驻码签发；行级可见范围测试；`operated_by=PARTNER` | S1b、S4 |
| **S1 补充：筛查先 Mock（用户 2026-10-27 决定）** | 供应商未定前：① channel-connector 建 `SCREENING_*` 标准 API 定义与一个**显式标注的 Mock 渠道**（`S_SCREENING_MOCK`，只在 local / mor-test 启用，按请求配置 DTO，遵守 UNIT_TEST_GUIDE 的测试端点规则，不进生产）；② 程序模板加人工检查项 `MANUAL_SCREENING`（审核员用公开名单 OFAC / UN / EU 自查并录入结论 + 名单版本），作为生产可用的人工兜底；③ `t_screening_run.provider_code=MOCK` 的结果不得出现在证据包导出中。生产上线前必须替换为真实供应商（S3），"不虚构外部筛查成功"的既有规则不变 | S1 |
| **S3 名单筛查** | channel-connector：`SCREENING_*` 标准 API 接一家真实供应商渠道替换 Mock；compliance：筛查执行/命中/处置、监控拉取、`t_screening_monitor`、`t_screening_hit_list`、`ReviewTriggerRaised`；Partner / 运营工作台命中处置页 | S1b |
| **S4 风险评级、档次与四眼** | 风险因子/规则/评级、档次切换程序模板、推荐/决定双人、`QualificationChanged` 事件、决定表加列 | S1 |
| **S5 持续复审与结算控制** | 定期/到期/变更/信号触发、复审案件、逾期投影、结算控制实现与事件（D38）、XXL-Job | S2、S4 |
| **S6 IDV 与登记库核验** | `GOV_ID_VERIFY`、`KYB_REGISTRY_LOOKUP`、`TAX_ID_VERIFY`、`IDV_*` 标准 API 与首批渠道（Wathq、ICP 视合作进度）、检查分项 | S3 |
| **S7 PayFac 合规服务（计费与导出）** | 程序 Owner 定制、证据包导出、计费事件与 commercial 产品 `COMPLIANCE_SERVICE` | S1b、S4 |
| **S8 Partner / OE 资格复用** | Partner / OE 申请走同一程序链，收单资质档位证据 | S1 |

## 17a. 落地前提与范围决定（2026-10-27）

- **存量数据：按重写处理（用户决定）。** local / mor-test 现有 Client、申请、案件、程序数据不迁移；新 Schema 直接建表、旧表按取代范围删除；真实环境尚无数据。第 10.1 中"← 原列"的写法只表示列的替代关系，不表示回填。
- **Partner 门户案件工作台：列入后续计划（用户决定）。** S1b 保留入驻码与责任方解析；案件操作首期由平台运营在 Operations 控制台**代所有 PayFac 操作**（`operated_by=PLATFORM`，决定人记录为运营账号并带 `on_behalf_of_partner_id`），Partner 门户工作台作为独立需求排期；AGENTS 规则已按 Q1 预先修订，实现顺序不影响规则。
- **筛查供应商：先 Mock + 人工兜底**（切片表 S1 补充行）；生产上线以真实供应商接入为门禁。
- **法务待确认 26 项**（9.5a）：未确认的国家包行以 DRAFT 入库，不发布；S1 开发不等法务，但 AE/SA/EG 的 ACTIVE 发布等。
- **Partner 行级可见范围**与 **`ClientReviewDecided` v2 兼容**作为 S1 的明确验收项：PayFac A 查不到 PayFac B 的案件、证件只见脱敏；现有 `ClientReviewResultListener` 升级到 schemaVersion 2 并保留对 1 的拒绝。
- **四眼可关**：程序版本 `four_eyes_required=0` 时决定人可同时为推荐人，决定记录 `four_eyes_waived=1`，由责任 PayFac 承担。

## 18. 对现行文档的取代范围（定稿时登记）

- `requirements/20260914/client-console-注册与客户入驻/generic-material-form-proposal.md`："不按国家维度、每类资格一个全局程序"由本稿第 9/10 节取代。
- 同目录 `simplification-design.md`："每次重提新任务、旧任务结果不生效"由 7.3 同案件多轮取代；退回"分区"升级为需求级错误。
- `agent-docs/database/CLIENT_CONTRACT_DATABASE.md` 与 `paytech/client-master/docs/database/*`、`paytech/compliance/docs/database/*`：按第 10 节修订 PDManer 与 Schema。
- `agent-docs/VAULT_SERVICE_ARCHITECTURE.md` 第 112 行对 CCT 的字段限制：个人证件号例外需新决定。
- `agent-docs/decisions/2026-10-23-platform-tech-provider-and-partner-payfac.md` 第 5 节现状表中 compliance 行按本稿更新；U2 的实现落点指向本稿第 14 节。
- `AGENTS.md`：定稿后加一条路由指向决定记录。

## 19. 来源

- 行业：Stripe Connect Accounts / Persons / Capabilities / Requirements / Identity（docs.stripe.com，含 AE 的 `get-requirements-for-setups` 实测 52 项）；Adyen LEM v4（legalEntities、entityAssociations、verification errors 1_xx/2_8xxx、remediating actions、data review、document requirements）；Checkout.com Platforms（sub-entity status、requirements_due、periodic review 90 天）；Rapyd Verify；PayPal Partner Referrals v2 schema；Mangopay Legal User / KYC Document / UBO Declaration；Sumsub KYB 2.0 beneficiaries 与 rejection labels；Onfido/Entrust OpenAPI（reports、breakdown、watchlist monitor）；Jumio KYX capabilities；Veriff decisions；ComplyAdvantage API（search/hit/match_status/monitor）；Ballerine `schema.prisma` 与 KYB+KYC workflow；Visa Core Rules 2026-04 §5.3 与 PF Risk Guide 2021；Mastercard Rules（2010 镜像）与 SPME 2019；FATF R.10/11/12、EU AMLD4 Art.3、AMLR 2024/1624 Art.19/20/26/52；pKYC 实践（Moody's、PwC）。
- 三国：UAE Cabinet Resolution 134/2025（AML 执行条例）、Cabinet Decision 109/2023（UBO）、CBUAE AML/CFT 指引 2021、RPSCS Regulation 2021、ICP Validation Gateway、FTA TRN、PDPL 45/2021 与 DIFC/ADGM；SA Companies Law M/132、新商业登记法（2025-04-03）、UBO Rules（2025-04-03 / MD 267 2026-01-04）、SAMA AML/CTF Guide、SAMA E-Commerce PSP Rules、Wathq / Yakeen / Nafath、UPU 国家地址、PDPL 与 Transfer Regulation、SAMA CSF 3.4.3；EG MoSIT Decree 41/2020（UBO）、AML Law 80/2002 与 3331/2023、CBE 2019 PF 规则与 2025 PSP/PSO 许可规则、PDPL 151/2020 与 816/2025、国民 ID 14 位结构、税卡 9 位；主流 PSP 材料清单（Stripe UAE、PayTabs、Telr、Tap、Network International、Paymob、Fawry、Moyasar）。
- 原始笔记（含全部 URL）：会话 scratchpad `industry-research.md`、`jurisdiction-research.md`、`internal-explore.md`（未入库；定稿时择要迁入 `assets/`）。
