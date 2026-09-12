# Risk & Compliance 数据库设计规范

> 已确认功能增量（2026-09-11）：[历史查看与字典决定](../decisions/2026-09-11-risk-rule-history-and-dictionaries.md)批准统一只读详情、历史列表、发布引用查询以及 Risk 原因/主体标签字典。精确两表结构、接口与旧项兼容已通过阶段 3 评审，合同见[需求材料](../../requirements/20260911/operations-console-风控规则集历史与字典管理/technique.md)。旧代码和消息不按当前字典重写，不新增字典回执表或标签主体关系表；数据库执行和验证状态只见当前台账，其他执行语义不变。

> 2026-09-11 规则集整体管理增量以[已确认需求与方案](../../requirements/20260911/operations-console-风控规则集列表优化/technique.md)第 10–18 节为准：新增 `t_rule_set` 稳定根；列表按根及当前配置分页，删除只终止整体配置管理并永久保留编码、版本、Candidate、审计与幂等回放。有任一版本被当前 ACTIVE Release 使用时禁止删除；冻结或 Candidate 引用本身不算使用中。此项取代旧设计无管理根、版本列表和无整体删除部分，保留其余业务事实。


> 2026-09-11：规则 V1 配置的可空终态、附加动作、主体绑定、手工拒绝文案及格式/执行门禁以[配置增量决定](../decisions/2026-09-11-risk-rule-v1-configuration.md)及其接口合同为准。本文其他执行语义保留；新增配置不表示已实现执行。

最后更新：2026-08-30

历史基线（2026-09-05）：V1 逻辑模型、现实场景、71 表 PDManer、Manual Payout 四检查点与 CCT Local Partner Application Typed Namespace 已对齐并通过自动门禁；PDManer 4.9.3 应用内人工 Review 待完成。本文拥有 Risk 数据库业务事实，不维护手工 SQL、Java 实现、物理数据库验证或部署的活态状态；这些事实只见[当前实现台账](../CURRENT_IMPLEMENTATION.md)。

> **2026-09-05 跨主题对齐：** 本文拥有 Risk 业务事实，不单独拥有 Vault、`compensation_event`、物理 CHECK/FK 或测试基础设施规范。Risk 模型已投影当前 Vault V1、统一 15 列补偿表与零物理 CHECK/FK；六库 COMP-01 和全局 DB-01 的状态仍只由 [当前实现台账](../CURRENT_IMPLEMENTATION.md) 按全局证据维护。

当前配套资产：[现实场景](./scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md) · [物理建模决策](../../risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md) · [PDManer 模型](../../risk-compliance-service/docs/database/risk-compliance.pdma.json) · [Dispute 平衡优化设计](../plans/2026-08-28-risk-dispute-balanced-optimization-design.md) · [Dispute 平衡优化实施计划](../plans/2026-08-28-risk-dispute-balanced-optimization-implementation-plan.md) · [Settlement Eligibility 平衡精简设计](../plans/2026-08-28-risk-settlement-eligibility-balanced-simplification-design.md) · [Settlement Eligibility 实施计划](../plans/2026-08-28-risk-settlement-eligibility-balanced-simplification-implementation-plan.md) · [Manual Payout 四检查点对齐实施记录](../plans/2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan.md) · [Limit 平衡精简设计](../plans/2026-08-28-risk-limit-balanced-simplification-design.md) 和 [实施计划](../plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md) · [Decision Engine 平衡精简实施计划](../plans/2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan.md) · [DD 精简实施计划](../plans/2026-08-27-risk-dd-simplification-implementation-plan.md) · [首版物理建模历史记录](../plans/2026-08-26-risk-compliance-pdmaner-implementation-plan.md)。历史 2026-09-05 物理基线为 71 表；现行模型统计见[当前台账](../CURRENT_IMPLEMENTATION.md#3-当前生成模型快照)，Risk 物理 DDL 目标为零 CHECK、零 FOREIGN KEY。

## 1. 目标与边界

Risk & Compliance 数据库是以下事实的唯一权威来源：

- Individual Client KYC 案件和资格决定；
- Organization Client KYB 以及 UBO、Director、Signatory 个人 KYC 依赖；
- Local Partner 轻量 PDD 案件和角色资格决定；
- Operating Entity 完整 KYB、支付机构准入审查和角色资格决定；
- 制裁、PEP、负面信息筛查、命中处置和持续复审；
- Decision Point、Feature、Rule Set、类型安全规则 AST、Action Catalog 及其不可变版本；
- 每个环境的完整 Decision Release、静态校验、旁路测试、数据检查和审核事实；
- 每次 Checkout、Payment Attempt 与 Settlement Risk 的输入快照、执行轨迹、业务决定或技术失败；
- Rule 命中产生的 Action Intent、冲突合并后的 Action Effect、真实执行结果及其冷却/名单状态；
- Risk 自有的类型化业务 Fact、Velocity Event 和风险主体关联；
- 固定 UTC 窗口硬限额、原子 Reservation Group、Reservation 和 Movement；
- 面向 Client/Local Partner 的 Settlement Eligibility Control，以及仅面向 Client 的 `MIN_SECURITY_DEPOSIT` 最低保证金门槛；
- Dispute Case、按需 RFI、业务终态和责任分类。

本库不拥有：

- Client & Contract 的 Party/Profile、Client、Local Partner、Operating Entity、Agreement 或商业条款主档；
- Commerce 的 Order、Checkout、Refund Request 或消费者合同事实；
- Payment 的 Provider/MID 主档、支付状态、Provider 原始报文、渠道请求响应或 Representment 交互；
- Finance 的 Fee Calculation、Ledger、Balance、Reserve、Settlement、Recovery 或实际损失；
- Platform Support 的文件正文、IAM 用户主档或集中审计日志；
- Central Vault 的密文、密钥或业务语义。

Risk 可以保存案件时点的不可变 Party Snapshot，但该 Snapshot 只用于解释历史决定，绝不能作为当前 Party 主档对外提供。

Checkout Risk 必须从 Commerce 在最终 Order/Context 落库前构造的不可变 OrderPreparationContext 继承同一组 industry_code + mcc_code；Payment Attempt Risk 直接继承 Payment Intent 从最终 order_id + order_context_id 冻结的同一组合。industry_code 是平台业务行业维度，mcc_code 是收单分类维度，二者不得合并、互相推导，也不得与 Finance tax_category_code 混用。Risk 不接受 Client 借分类选择 Operating Entity、Provider 或 MID。

## 2. 物理建模统一规则

### 2.1 命名、ID、时间和并发

- 本文表清单使用逻辑表名；物理表名统一增加 t_ 前缀。
- 物理表名使用 lowercase snake_case；唯一索引使用 uk_，普通访问索引使用 idx_。
- 所有主键和跨系统 ID 使用 Snowflake BIGINT，Java 类型统一为 Long；禁止数据库自增。
- 所有时间戳使用 UTC DATETIME(6)；Java 边界使用 Instant。
- 所有有效期和窗口使用半开区间 [effective_from, effective_to)。
- 金额使用 DECIMAL(24,8) 并携带 ISO 4217 CHAR(3) 币种；比例使用 DECIMAL(18,10)。
- 可变聚合根包含 version BIGINT NOT NULL，状态变化使用期望旧状态和乐观锁。
- 决定、Snapshot、Rule/Predicate Trace、Action Intent、Movement、Transition 等业务事实只追加，不覆盖。
- compensation_event 是可重试本地执行记录，成功后允许删除，最终失败保留供人工处置。
- 普通业务表包含 create_time DATETIME(6) NOT NULL、update_time DATETIME(6) NOT NULL。
- 不使用通用软删除、任意 EAV、自由 SQL/Script 或无版本 JSON 规则。终止和失效通过明确状态表达。

### 2.2 外部引用

所有外部引用必须同时具备业务语义和来源系统。外部 ID 不建立物理外键；接收事实时在目标领域表保存 source_event_key + source_version + source_payload_hash，依赖业务唯一约束和单调版本实现幂等。

### 2.3 规则自由编排边界

运营可以自由组合工程注册的 Feature、Context Field、Operator、Condition Group 和 Action，但不能提交 SQL、JavaScript、Groovy、任意类名或任意 HTTP 地址。

- Feature 与 Action 的执行 Handler 由工程实现、测试、部署和注册。
- Feature 输出在 MVP 只允许 Code/String、Number、Boolean、Datetime、Duration 等标量类型。
- Rule Condition 使用类型安全 AST，可任意嵌套 AND、OR、NOT。
- Literal 使用类型化列；同一 Operand 只能选择一种来源和一种值类型。
- Action 参数来源只允许 LITERAL/FEATURE/CONTEXT；LITERAL 使用类型化列，FEATURE 与 CONTEXT 必须属于当前 Decision Point 白名单。
- 新增 Feature/Action 通常不改规则表结构；新增 Handler 仍需工程发布。
- Decision、技术状态和领域副作用是不同概念，不能用一个通用 status/value 字段混写。

## 3. 业务主线

### 3.1 角色尽调

~~~text
CCT Party/Profile/Role
  → 创建角色级 Due Diligence Case
  → 冻结 DD Program Version、来源提交坐标和 Case Subject 的 Party/Profile/Relation 事实
  → 执行 Screening / Check / Review
  → Decision Point 给出可追溯建议
  → 形成不可变 Qualification Decision
  → 更新 Role Qualification
  → 发布角色资格事件
~~~

固定 Qualification Type：

~~~text
INDIVIDUAL_KYC
ORGANIZATION_KYB
PARTNER_DUE_DILIGENCE
ENTITY_UNDERWRITING
RELATED_PERSON_KYC
~~~

- Individual Client 使用 `role_type=CLIENT + qualification_type=INDIVIDUAL_KYC`。
- Organization Client 使用 `CLIENT + ORGANIZATION_KYB`，并依赖 UBO、Director、Signatory 的个人 KYC。
- Local Partner 使用 `LOCAL_PARTNER + PARTNER_DUE_DILIGENCE`。
- Operating Entity 使用 `OPERATING_ENTITY + ENTITY_UNDERWRITING`。
- 关联自然人使用 `RELATED_PERSON + RELATED_PERSON_KYC`，其中 `role_id` 精确保存 CCT `party_id`；其执行流程为个人 KYC，但不与 Client 资格混用。
- Case 不再重复保存 `case_type`；执行流程由 Qualification Type 确定，Role + Qualification 的同一行矩阵必须 Fail Closed。CCT Client Application 的 `INDIVIDUAL_KYC/ORGANIZATION_KYB` 因而能与 Risk 决定 Tuple 逐值一致。
- 同一 Party 的资料、文件引用和仍有效的基础检查可以复用，但每个业务角色必须形成独立资格决定。
- 自动规则结果只是 recommendation；qualification_decision 才是权威资格事实。人工覆盖必须保存 override_reason_code。

CCT Client/Operating Entity/Local Partner Application 提交事务绝不能同步调用 Risk。CCT 先冻结 Application/Profile/Document、计算 `submission_content_hash`、推进本地状态并登记 LOCAL_HANDLER；三个 Handler 分别固定 `source_namespace=CCT_CLIENT_APPLICATION`、`CCT_OPERATING_ENTITY_APPLICATION`、`CCT_LOCAL_PARTNER_APPLICATION`，并携带 `application_id`、`role_type`、`role_id`、`qualification_type`、`submission_content_hash`。Risk 以 `source_namespace + source_application_id + role_type + role_id + qualification_type` 唯一定位既有 Case，再比较 Submission Hash：完整来源身份相同且 Hash 相同返回同一 `case_id`，Hash 不同必须冲突。同一 Application 可以用相同 Submission 坐标派生一个主 Case 和多个不同 Party 的 RELATED_PERSON/RELATED_PERSON_KYC 子 Case；CCT 只接收 Local Partner 主 Case/Decision，Risk 管理关联人 KYC 子 Case 及其依赖。三个 CCT Application Namespace 即使数值 ID 相同且涉及同一关联 Party，也不会串案。

Program、Case、Decision 与 Current Qualification 必须使用同一 Qualification Type 词表。创建 Case 时还必须验证 `dd_program.qualification_type = due_diligence_case.qualification_type`；不能让 Client Case 引用 Operating Entity Program。

Due Diligence Case 自身必须显式冻结 `case_origin` 和 `source_namespace`，不能把业务来源只藏在 command_idempotency 中。APPLICATION 必须携带 Application ID、Submission Version 和 Submission Hash，并遵循以下闭合 Namespace/Role 矩阵：

| Namespace | 主 Role / Qualification | 允许的关联人 Role / Qualification |
|---|---|---|
| `CCT_CLIENT_APPLICATION` | `CLIENT + INDIVIDUAL_KYC/ORGANIZATION_KYB` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `CCT_OPERATING_ENTITY_APPLICATION` | `OPERATING_ENTITY + ENTITY_UNDERWRITING` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `CCT_LOCAL_PARTNER_APPLICATION` | `LOCAL_PARTNER + PARTNER_DUE_DILIGENCE` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `RISK_COMPLIANCE` | Continuous Review / Manual 自有来源 | 按既有 Role/Qualification 矩阵 |

Local Partner Namespace 不允许 `CLIENT` 或 `OPERATING_ENTITY` Role。命中包含 Typed Namespace 与 Role/Qualification 的完整来源唯一键后，以 Hash 区分幂等重放与冲突提交。CONTINUOUS_REVIEW 与 MANUAL 固定为 `source_namespace=RISK_COMPLIANCE`，Application ID、Submission Version/Hash 均为空，避免伪造不存在的外部提交坐标；只有 CONTINUOUS_REVIEW 携带 scheduled_review_time。

Program Version 可配置 review_frequency_code；Role Qualification 保存当前 review_frequency_code、next_review_time 和 review_status。调度器只扫描到期的 ACTIVE 投影，并在同一本地事务中以 qualification version + next_review_time 做 CAS、创建 case_origin=CONTINUOUS_REVIEW 的新 Case、推进下一次复审时间。Role + Qualification Type + Scheduled Review Time 唯一键与 CAS 共同防止重复周期；不再建立独立 Review Plan/Run。

Case 实例化必须保持 Program 闭包：Requirement、Check、Screening Template 都属于 Case 冻结的同一 Program Version，Screening Subject Role 必须匹配 Case Subject，所有 Required Template 不得遗漏。Evidence 的 Requirement/Case Subject/File/Hash 创建后冻结；替换时同事务把旧 RECEIVED/ACCEPTED Evidence 置为 SUPERSEDED、插入新 RECEIVED Evidence，并把 Requirement 置回 RECEIVED，重新接受后才满足要求。Screening Provider 成功结果必须完整写入全部去重 Hit 后才 CAS Run 为 SUCCEEDED；FAILED/TIMED_OUT Run 不得写 Hit。

### 3.2 Checkout 资格评估

~~~text
Commerce Checkout Request
  → 冻结 OrderPreparationContext
  → 定位当前 PRIMARY Decision Release
  → 按 FIRST_TERMINAL 执行有序 Rule Set
  → 保存 Evaluation、Feature、Predicate、Rule 和 Action 轨迹
  → 返回 ALLOW / REVIEW / DENY，或明确技术失败
~~~

每次调用形成一个 checkout_input_snapshot 和一个 PRIMARY decision_evaluation；snapshot_id 同时是返回 Commerce 的稳定 Assessment ID。REVIEW 与 DENY 都拒绝当前 Checkout 创建；REVIEW 只进入内部分析，不把原结果改为 ALLOW。FAILED/TIMED_OUT 是技术终态，没有业务决定，Commerce 按 fail-closed 停止创建。Commerce 只在 ALLOW 时把准确 Assessment ID 锁入 Order Context。

### 3.3 每 Attempt 交易评估

~~~text
Payment Attempt
  → 创建唯一 Transaction Risk Assessment
  → 定位当前 PRIMARY Decision Release
  → 执行 Feature 与 FIRST_TERMINAL Rule
  → 收集并合并 Action
  → 对候选 ALLOW 执行系统义务 RESERVE_LIMIT_GROUP
  → 原子保存正式决定、Action Effect 和 Limit Reservation
  → 返回业务决定及 Reservation Group ID
~~~

- ALLOW：当前 Attempt 可以继续；必须原子创建 Limit Reservation Group。
- REVIEW：当前 Attempt 直接拒绝；分析结果不得复活该 Attempt。
- DENY：当前 Attempt 直接拒绝。
- FAILED/TIMED_OUT：没有业务决定，不创建真实 Action Effect 或 Limit Reservation，Payment 按 fail-closed 停止。
- 新支付尝试必须使用新的 payment_attempt_id 和 Assessment。
- Rule/Default 先产生 proposed_decision；系统义务完成后写入正式 business_decision。
- 并发占额失败属于 LIMIT_EXCEEDED 业务拒绝；技术执行失败仍是 FAILED/TIMED_OUT。

### 3.4 Consumer Context 与 Network Evidence 最小输入

Commerce 经 Payment 同步调用 Risk 时使用获批最小契约，Risk 不读取 Commerce 数据库。Payment Attempt Assessment 的不可变 Input 可使用以下字段：

~~~text
consumer_reference
consumer_email_vault_token
consumer_phone_vault_token
email_domain
phone_country_code
billing_country_code + billing_region_code + billing_city + billing_postal_prefix
client_ip_address + user_agent + trusted_proxy_chain
ip_country_code + ip_region_code + ip_city + ip_asn + network_type
proxy_flag + vpn_flag + tor_flag + hosting_flag
browser_family + browser_major_version
os_family + os_major_version
device_type
~~~

- consumer_reference 必须是 Client 命名空间内非 PII、不透明引用。
- Email Token 必填、Phone Token 可空；二者是 Commerce 已取得的稳定 Vault Value Token。
- Risk 不接收 consumer_profile_vault_token，也不为 Consumer 保存 Blind Index、Key Version 或 Masked Value。
- Create Order Checkout Assessment 发生在消费者交互之前，不强求尚不存在的 Consumer Context 或 Network Evidence。
- Payment Attempt Assessment 使用最新 Consumer Context 与 PAYMENT_START Evidence，并可引用同 Session 的 LEGAL_ACCEPTANCE Evidence。
- Token 与网络原值只允许通过内部同步最小契约进入对应 Assessment Input；不得进入外部 API、普通日志、MQ、BCR 或 compensation_event.payload_json。
- Shadow 使用同一个 Risk 本地 Input Snapshot 和 evaluation_time；异步任务只携带 Assessment ID，再回读 Risk 自己的数据库。

### 3.5 Action 与系统义务

Rule 命中后可以产生多个非终态 Action 和最多一个终态 Decision Action。

~~~text
按 Rule Set sequence_no
  → 按 Rule priority_no
     → 未命中：继续
     → 命中非终态 Action：收集后继续
     → 命中 ALLOW / REVIEW / DENY：停止全部规则
  → 没有终态 Decision：使用 Decision Point 默认决定
~~~

非终态 Action 示例：

~~~text
COOLDOWN_SUBJECT
ADD_LIST_ENTRY
REMOVE_LIST_ENTRY
CREATE_SETTLEMENT_CONTROL
ADD_RISK_TAG
~~~

相同目标上的多个 Action 先按 Action Version 固定 merge_policy 合并。merge_policy 仅允许闭集 FIRST/LAST/MOST_RESTRICTIVE/UNION/MIN/MAX：冷却截止时间使用 MAX，名单或标签集合使用 UNION，终态决定使用 MOST_RESTRICTIVE；FIRST/LAST/MIN 只供工程注册且已验证其目标和值类型的 Action 使用。运营不能修改 merge_policy，不能引入闭集外策略。

Decision Point Obligation 是工程绑定的系统不变量，运营不能删除。PAYMENT_ATTEMPT_RISK 的 ALLOW 必须执行 `RESERVE_LIMIT_GROUP`；`PAYOUT_CREATION/PAYOUT_FINAL_APPROVAL/PAYOUT_EXECUTION_CREATION/PAYOUT_EXTERNAL_SUBMISSION` 四个 Manual Payout Point Version 的 ALLOW 必须各自恰好执行一条 `CHECK_SETTLEMENT_ELIGIBILITY`。`obligation_type=ACTION` 必须绑定准确 `action_version_id` 并进入统一 Action Intent/Effect 轨迹；`obligation_type=RESERVE_LIMIT_GROUP` 不绑定 Action，而由 0..N 条 `decision_point_limit_binding` 冻结 Limit Version 及取值来源，直接形成 Limit Reservation Group；`obligation_type=CHECK_SETTLEMENT_ELIGIBILITY` 同样不绑定 Action Version 或 Limit Binding，以 `FAIL_CLOSED` 同步读取当前 Control。两类非 Action 义务都绝不伪造成 Action Intent/Effect。零条 Limit Binding 仍创建空 Group，使系统义务成功、幂等和 Payment 回绑都有稳定事实。规则 Action 使用 `source_type=RULE + rule_action_id`，Action 型义务使用 `source_type=OBLIGATION + obligation_id`，两类 Intent 来源严格互斥。

### 3.6 发布与旁路

每个 Decision Point、每个 Environment 同时只允许一个 PRIMARY/ACTIVE Decision Release。Client、国家、币种、Operating Entity 等业务差异通过规则表达，MVP 不建设动态发布 Scope 或真实流量灰度。

~~~text
Rule Set Draft
  → Freeze Rule Set Version
  → Create immutable Candidate Release
  → 8 typed STATIC_CHECK Audit Events
  → Shadow Run + SHADOW Evaluation comparisons
  → Fixed quality gate
  → Two APPROVAL Audit Events from distinct reviewers on the same Run
  → Second approval atomically activates Candidate and supersedes old ACTIVE
  → Monitor / Roll back to a previously approved composition
~~~

Shadow：

- 进入 SHADOWING 前，`t_release_audit_event` 必须已有 `REFERENCE_INTEGRITY`、`AST_SHAPE`、`AST_TYPES`、`FEATURE_DEPENDENCY`、`HANDLER_HASH`、`ACTION_PARAMETERS`、`TERMINAL_ACTION`、`SYSTEM_OBLIGATION` 八项 `STATIC_CHECK=PASSED`；
- 使用与 PRIMARY 相同的输入快照和 evaluation_time；
- 计算 Candidate 决定、Rule、Predicate 和 Action Intent；
- 不返回给调用方；
- Action Effect 只允许 `SIMULATED`，执行字段全部为空；
- 不冷却、不拉黑、不创建 Risk Restriction/List Entry/Settlement Control、不占限额、不写 Velocity Event、不发布业务事实或 compensation_event；
- 对比 ALLOW/REVIEW/DENY 迁移、Action 差异、Feature 缺失、技术失败、超时、P95/P99 和分群偏移；
- Run `COMPLETED + PASSED` 后进入 REVIEW_PENDING；两个不同 Reviewer 必须批准同一 Run，第二次批准命令原子完成 Candidate `APPROVED→ACTIVE` 与旧 Release `ACTIVE→SUPERSEDED`。

MVP 不做真实流量灰度。冷却、拉黑等持久副作用无法通过简单 Release 回滚撤销，且灰度会让同类请求受到不一致处置。

### 3.7 Dispute 与 Settlement Eligibility

- Payment 归一化 Provider Dispute 通知并负责所有渠道交互。
- Risk 创建 Dispute Case 后直接进入 EVIDENCE_REQUIRED，发起按需 RFI、协调证据、接收渠道结果并形成 Outcome 和 Liability。
- Risk 通过真实本地 Action Effect 创建 Settlement Control；`SETTLEMENT_BLOCK` 阻断资格，`MIN_SECURITY_DEPOSIT` 只表达 Client 最终最低保证金门槛，不指令 Finance 迁移余额。
- `DisputeOpened` 只通知开案与举证期限，不触发 Finance 记账或冻结；Client 沉默不得自动形成 `DisputeLost`。
- 只有权威 `DisputeLost` 才触发 Finance Journal。仅明确允许由 Client 承担的损失可以使用 `CLIENT_SECURITY_DEPOSIT`，不足部分进入 `CLIENT_RECOVERY_RECEIVABLE`；Finance 独立执行这些账户与账务动作，不读取 Risk 数据库。

### 3.8 Manual Payout 检查点与 RCO-I09

Manual Payout Eligibility 固定在四个业务检查点重新评估：Payout 创建使用 `PAYOUT_CREATION`，最终 Finance 审批使用 `PAYOUT_FINAL_APPROVAL`，Execution 创建使用 `PAYOUT_EXECUTION_CREATION`，每次实际外部提交使用 `PAYOUT_EXTERNAL_SUBMISSION`。每个不同检查点都创建新的 PRIMARY Evaluation 和 `t_settlement_risk_assessment`；同一幂等命令重放复用原 `assessment_id + evaluation_id`，不同检查点及每个新 Retry Execution 必须使用新坐标。前两个检查点只接受稳定 `PAYOUT` 来源事实，后两个只接受稳定 `PAYOUT_EXECUTION` 来源事实；Finance 必须在调用前预分配来源 ID。创建类检查点的 `source_fact_version` 固定为 1，其余检查点使用命令开始时锁定的变更前 expected/current Version；所有 `source_fact_version` 都必须是 MySQL signed INT 的 `1..2147483647`。

`RCO-I09` 的最小返回固定包含 `assessment_id + evaluation_id` 两个坐标，并携带：

~~~text
assessment_id
evaluation_id
decision
reason_codes[]
matched_controls[]
required_amount
required_currency
control_set_hash
~~~

`assessment_id + evaluation_id` 是不可变且可检索的 Risk 坐标，必须逐值指向同一 Assessment 及其 1:1 Evaluation；查询或同一幂等命令重放返回原冻结结果，不按当前 Control 重新求值。Assessment 以结构化列证明 `checkpoint_type`、`role_type/role_id`、`operating_entity_id`、`gross_amount/currency`、`bank_country`、`source_fact_type/id/version` 和 `input_hash`，且必须满足 `Assessment.checkpoint_type = Decision Point.point_code` 与 `Assessment.input_hash = Evaluation.input_snapshot_hash`。Eligibility Obligation 实际执行时还成组冻结 `eligibility_decision/required_amount/required_currency/control_set_hash`；规则已先给出 REVIEW/DENY 或技术执行未完成时，这四列为空并由 Evaluation 保留正式结果或技术终态。Risk 不建立独立 Eligibility Result 聚合、ID、Version 或完整结果 Hash。

Assessment Eligibility 结果与 Evaluation 只允许以下六个 `(eligibility_decision, technical_status, proposed_decision, business_decision)` 组合：`(ALLOW, SUCCEEDED, ALLOW, ALLOW)`、`(RESTRICT, SUCCEEDED, ALLOW, DENY)`、`(NULL, SUCCEEDED, REVIEW, REVIEW)`、`(NULL, SUCCEEDED, DENY, DENY)`、`(NULL, FAILED, NULL, NULL)` 和 `(NULL, TIMED_OUT, NULL, NULL)`。其他组合一律拒绝，防止 Assessment 与 Evaluation 各自成为互相矛盾的结果权威。静态校验还必须拒绝空 Assertion/Selector/Mapping、重复 Mapping Key、未知 Alias/Field/Operator 和缺失或多余 Mapping Key，禁止元数据空集合造成真空通过。

`gross_amount` 固定为 Finance `owner_debit_amount`，并等于 `cash_required_amount`；不得取 `requested_amount` 或 `beneficiary_amount`。`source_fact_version` 固定为评估命令开始时观察并锁定的变更前 expected version：Payout/Execution 创建类检查使用预分配 ID 与版本 1，Final Approval 和 External Submission 使用各自状态迁移前的当前版本，绝不引用尚未产生的审批后或 SUBMITTED 版本。Risk 成功后 Finance 本地事务失败不撤销该执行事实；同一命令重放复用原 pair 和来源版本。契约中没有 `beneficiary_account_ver`，Beneficiary 完整性由 Finance 自己的稳定 ID 与内容 Hash 证明。

Finance 必须冻结完整 RCO-I09 返回，包括两个坐标、排序后的 Reason、每个命中 Control/Scope、金额币种和 `control_set_hash`，并在 Finance 本库按自己的 Canonical Contract 计算本地 `eligibility_hash`。Risk 的 `input_hash` 只承诺输入，`control_set_hash` 只承诺匹配 Control 集，二者都不能冒充 Finance 的完整结果 Hash。

`bank_country` 逐值来自 Finance 已冻结的单表 `beneficiary_account.bank_country`，不是 OE、订单、Consumer 或付款目的地国家。四个检查点在各自业务副作用前都必须独立执行 Finance 本地账户门禁：Client 必须同时满足 `CLIENT_RECOVERY_RECEIVABLE/NONE = 0` 且 `CLIENT_SECURITY_DEPOSIT/NONE >= 本次 required_amount`，Local Partner 必须满足 `PARTNER_RECOVERY_RECEIVABLE/NONE = 0` 且 Risk 返回的 `required_amount/required_currency` 均为 NULL。任一检查点的决定非 ALLOW、Scope/1:1/Hash 不一致、账户门禁不通过或技术失败都 Fail-Closed，不得复用早先检查点的结果。Payout/Final Approval 不占用 BANK_CASH；Execution Creation 通过后由 Finance 精确锁定 BANK_CASH 并创建 CREATED Execution，External Submission 再执行第四次新鲜检查。Local Partner 不得查询或伪造两类 Client Account。这里的 Payout 只清偿 Finance 已形成的 Client/Local Partner 应付余额；未来 Client-to-Beneficiary Disbursement 是独立能力，必须使用独立 Decision Point、输入和控制。

## 4. 聚合与精确表清单

本章 V1 共 71 张逻辑表。t_velocity_bucket 不属于 V1；只有真实性能数据证明明细查询不足时，才作为可重建加速投影新增。MVP 不预建通用 Retention、Legal Hold、Archive、Generic Inbox/Outbox、Generic Case 或 Generic RFI 表。

### 4.1 Due Diligence Program、Case 与 Qualification（19 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| dd_program | 尽调程序稳定身份 | program_id, program_code, qualification_type, status；program_code 唯一，类型创建后不可改变 |
| dd_program_version | 不可变程序版本 | program_version_id, program_id, version_no, review_frequency_code, status, active_slot, content_hash, approved_by, approved_time；版本号为正且唯一；仅 ACTIVE 写 active_slot=1；DRAFT 不得有批准信息，ACTIVE/RETIRED 必须保留批准人和批准时间；复审频率为空表示不要求周期复审 |
| dd_program_requirement | 程序材料模板 | program_requirement_id, program_version_id, requirement_code, document_type, required_flag；同版本 Requirement Code 唯一；文件类型只在模板保存一次 |
| dd_program_check | 程序结构化检查模板 | program_check_id, program_version_id, check_code, check_type, required_flag, validity_days；同版本 Check Code 唯一；有效天数若有值必须为正 |
| dd_program_screening | 程序筛查模板 | program_screening_id, program_version_id, subject_role, screening_type, required_flag；组合唯一；不预置独立复筛间隔，持续复审由 Role Qualification 调度 |
| due_diligence_case | 角色尽调案件根 | case_id, case_no, role_type, role_id, qualification_type, dd_program_version_id, case_origin, scheduled_review_time, source_namespace, source_application_id, source_submission_version, source_submission_hash, status, active_slot, submitted_time, decision_time, version；Case Type 由 Qualification 推导而不重复保存；case_no 唯一；APPLICATION 按 Typed Source Namespace + Application + Role + Role ID + Qualification 唯一，允许同一申请派生主 Case 与关联人子 Case，并隔离三个 CCT Application Namespace 的数值 ID；状态与提交/决定时间严格成矩阵；进行中 Slot 按 Role+Qualification 唯一；同 Role/Qualification/Scheduled Time 最多一个持续复审 Case |
| dd_case_subject | 案件主体与关系快照 | case_subject_id, case_id, party_id, subject_role, primary_slot, source_profile_id, source_profile_version_no, source_relation_version_id, source_relation_version_no, ownership_percentage, required_flag；case+party+role 唯一；每 Case 最多一个 PRIMARY；关联主体冻结 CCT Party Relation Version ID/Version No；持股百分比只适用于 UBO 且范围为 (0,100] |
| dd_case_dependency | Organization Case 对关联人 KYC Case 的依赖 | dependency_id, parent_case_id, child_case_id, dependency_type, required_flag, accepted_decision_id, version；不得自依赖或成环；接受子 Case 决定时以 Version CAS 从空值单向写入，决定必须属于该子 Case |
| dd_party_snapshot | 案件时点 Party/Profile 不可变快照 | snapshot_id, case_subject_id, schema_version, source_content_hash, snapshot_vault_token, captured_time；case_subject_id 唯一；仅保留有 Reveal/审计消费者的稳定 Vault Token，不机械复制 Vault Record Version/Metadata Hash |
| dd_requirement | 本 Case 实例化的材料要求 | requirement_id, case_id, program_requirement_id, status, due_time, version；case+program_requirement_id 唯一；Code/Type 由精确模板推导 |
| dd_evidence_ref | 尽调证据文件引用与审核投影 | evidence_id, requirement_id, case_subject_id, file_id, content_hash, status, version；requirement+file 唯一；文件替换产生新 file_id，不复制文件类型或文件业务版本；状态以 Version CAS 更新，Requirement 与 Case Subject 必须属于同一 Case |
| dd_check | 身份、注册、地址和业务检查 | check_id, case_subject_id, program_check_id, provider_code, request_ref, result_code, result_vault_token, performed_time, valid_until；Check Type 由精确模板推导；结果只追加；有效截止时间若有值必须晚于执行时间 |
| screening_run | 制裁/PEP/负面信息执行 | screening_run_id, case_subject_id, program_screening_id, provider_code, request_ref, status, started_time, completed_time, version；Screening Type 由精确模板推导；provider+request_ref 唯一；STARTED 无完成时间，终态必须有完成时间并以 Version CAS 更新 |
| screening_hit | Screening 命中当前投影 | hit_id, screening_run_id, external_hit_ref, match_type, match_score, status, name_vault_token, version；run+external_ref 唯一，score 在 [0,1]；状态仅 OPEN/RESOLVED；INSUFFICIENT_EVIDENCE 保持 OPEN，CONFIRMED_MATCH/FALSE_POSITIVE 同事务转 RESOLVED，具体结论只保存在处置版本中 |
| screening_resolution | 命中人工处置版本 | resolution_id, hit_id, resolution_version, resolution, reason_code, reviewer_id, resolved_time, evidence_file_id, evidence_content_hash；hit+version 唯一，最大 resolution_version 是当前处置 |
| dd_case_state_change | Case 状态和审核轨迹 | change_id, case_id, from_status, to_status, action_type, reason_code, trigger_type, trigger_id, actor_id, aggregate_version, occurred_time；case+aggregate_version 唯一且版本为正；首行 from_status 为空，后续行非空；追加写 |
| qualification_decision | 不可变资格决定版本 | decision_id, case_id, decision_version, role_type, role_id, qualification_type, decision_evaluation_id, decision_source, recommended_decision, outcome, override_reason_code, valid_from, valid_to, decided_by, decided_time；case+version 唯一；AUTOMATED/REVIEWER/OVERRIDE 来源字段成矩阵；最大 decision_version 是该 Case 当前决定 |
| role_qualification | 每个角色/资格类型的当前投影与复审调度 | qualification_id, role_type, role_id, qualification_type, current_decision_id, status, valid_from, valid_to, review_frequency_code, next_review_time, review_status, version；role+qualification_type 唯一；current_decision_id 是跨 Case 的当前生效决定；APPROVED 才能 NOT_REQUIRED/ACTIVE/PAUSED，拒绝/撤销/过期使用 COMPLETED 且无下次时间 |
| qualification_state_change | Qualification 历史 | change_id, qualification_id, from_status, to_status, decision_id, reason_code, trigger_type, trigger_id, actor_id, aggregate_version, occurred_time；聚合版本为正，首行 from_status 为空，后续行非空，追加写 |

Role Type 固定为 CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON；Role/Qualification 组合受上文矩阵约束。RELATED_PERSON 资格只在明确 Organization Case 上下文中有效，其 `role_id` 是 CCT Party ID，Dependency Child Case、Parent Case Subject Party 与 Qualification Decision 必须逐值一致。Case 内不保存 Case Type 或当前 Decision 指针，Screening Hit 内也不保存当前 Resolution 指针；局部最新事实由各自最大版本确定，只有 Role Qualification 保留跨 Case 的当前决定指针。

### 4.2 Decision Point、Feature、Rule、Action 与 Release（19 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| decision_point | 决策事件点稳定身份 | decision_point_id, point_code, point_name, business_domain, status；point_code 唯一；business_domain 决定 Evaluation 允许的输入快照族 |
| decision_point_version | 事件点不可变执行协议 | point_version_id, decision_point_id, version_no, input_contract_code, input_contract_version, output_contract_code, default_decision, evaluation_strategy, failure_strategy, timeout_ms, status, content_hash；版本唯一；FIRST_TERMINAL、FAIL_CLOSED |
| decision_point_input_field | 工程注册的 Context Field | input_field_id, point_version_id, field_code, value_type, required_flag, data_classification, rule_reference_flag, action_parameter_flag；point_version+field_code 唯一 |
| decision_point_feature | 事件点 Feature 白名单 | binding_id, point_version_id, feature_version_id, sequence_no；同一绑定唯一；Rule 引用必须属于白名单 |
| decision_point_rule_set | 有序 Rule Set Version 绑定 | binding_id, point_version_id, rule_set_version_id, sequence_no；同 Point Version sequence 唯一 |
| decision_point_obligation | 工程绑定的类型化系统义务 | obligation_id, point_version_id, trigger_decision, obligation_type, action_version_id, sequence_no, failure_policy, enabled_flag；ACTION 必须有 Action Version；RESERVE_LIMIT_GROUP 与 CHECK_SETTLEMENT_ELIGIBILITY 必须无 Action，前者由 0..N Binding 直接形成占用组，后者不得有 Limit Binding；四个固定 Manual Payout Point Version 各恰一条启用、ALLOW 触发且 FAIL_CLOSED 的 Eligibility 义务 |
| feature_version | Feature 目录身份与不可变执行版本 | feature_version_id, feature_code, feature_name, business_category, version_no, result_value_type, handler_code, implementation_version, implementation_hash, timeout_ms, status；feature_code+version_no 唯一；只允许工程注册 Handler，不允许 SQL/Script |
| feature_dependency | Feature Version 的显式依赖 | dependency_id, feature_version_id, dependency_feature_version_id, sequence_no, required_flag；依赖组合与 sequence 分别唯一；不得自依赖或成环 |
| rule_reject_reason | DENY 原因的当前业务字典 | rejection_reason_id, reason_code, reason_name, description, default_message, status, version, created_by, updated_by, create_time, update_time；代码永久唯一，ACTIVE/DISABLED 只决定新选择资格，默认文案不回写旧规则 |
| rule_subject_tag | 可配置的主体标签当前定义 | tag_id, tag_code, tag_name, description, status, version, created_by, updated_by, create_time, update_time；代码永久唯一，不存真实主体标签或适用主体关系，含义由运营维护 |
| rule_set_version | Decision Point 内可冻结的规则集身份与版本 | rule_set_version_id, decision_point_id, rule_set_code, rule_set_name, version_no, status, content_hash, created_by, change_reason, frozen_by, frozen_time；point+code+version 唯一；created_by 与 1..500 字符非敏感 change_reason 在创建时必填，FROZEN 后不可修改且不得跨 Point 复用 |
| risk_rule | Rule Set Version 内规则 | rule_id, rule_set_version_id, rule_code, rule_name, priority_no, enabled_flag, reason_code；version+code、version+priority 分别唯一 |
| rule_node | Condition AST 节点 | node_id, rule_id, parent_node_id, node_type, group_operator, comparison_operator, sequence_no；GROUP/PREDICATE 结构合法，同级 sequence 唯一，无环 |
| rule_operand | Predicate 来源与多行类型化值 | operand_id, node_id, operand_role, value_sequence, source_type, feature_version_id, input_field_id, value_type, string_value, number_value, boolean_value, datetime_value, duration_value；node+role+sequence 唯一；来源 FEATURE/CONTEXT/LITERAL 三选一；Literal 每行仅一个类型值非空，IN/NOT_IN 使用连续 1..n，BETWEEN 固定两行 [1,2] |
| action_version | Action 目录身份与不可变 Handler 协议 | action_version_id, action_code, action_name, action_category, version_no, handler_code, implementation_version, implementation_hash, terminal_flag, decision_code, merge_policy, shadow_supported_flag, status；action_code+version_no 唯一 |
| action_parameter_definition | Action 参数协议 | parameter_definition_id, action_version_id, parameter_code, value_type, required_flag, allowed_source_type, target_parameter_flag；参数编码唯一 |
| rule_action | Rule 绑定的有序 Action | rule_action_id, rule_id, action_version_id, sequence_no；同 Rule sequence 唯一；一条 Rule 最多一个 Terminal |
| rule_action_parameter | Action 参数绑定 | parameter_id, rule_action_id, parameter_definition_id, value_sequence, source_type, feature_version_id, input_field_id, string_value, number_value, boolean_value, datetime_value, duration_value；来源和值类型严格 XOR；rule_action+definition+value_sequence 唯一；普通及 Control 根参数固定 sequence=1，Settlement Scope 三维按连续 sequence 组成 Tuple |
| decision_release | 一个环境的一份完整候选/生效快照 | release_id, release_no, decision_point_id, point_version_id, environment_code, status, active_slot, content_hash, created_by, activated_time, superseded_time, version；release_no 唯一；point+environment+active_slot 唯一 |
| release_audit_event | 类型化发布审计流 | audit_event_id, release_id, event_type, event_time；STATIC_CHECK 填 check_code, check_status, severity, error_code, error_summary；APPROVAL 填 shadow_run_id, approval_request_id, approval_status, reviewer_id, review_comment；STATE_CHANGE 填 from_status, to_status, reason_code, trigger_type, trigger_id, actor_id, aggregate_version；三类字段严格互斥且分别建立业务唯一键 |
| release_shadow_run | 候选 Release 旁路批次与冻结质量汇总 | shadow_run_id, run_no, baseline_release_id, candidate_release_id, status, sample_target, sample_count, started_time, completed_time, allow_to_review_count, allow_to_deny_count, deny_to_allow_count, action_change_count, feature_missing_count, failed_count, timed_out_count, p95_millis, p99_millis, check_status, version；仅 COMPLETED 完整填写质量列；p99>=p95，sample_count<=sample_target |

Operator 由引擎闭集注册，MVP 至少包含 EQ、NE、GT、GTE、LT、LTE、IN、NOT_IN、BETWEEN、IS_NULL、IS_NOT_NULL。Operator 必须声明适用 Value Type 和 Operand 数量，发布静态校验负责类型检查。

### 4.3 Evaluation、Assessment 与 Action 执行（8 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| decision_evaluation | 所有决策点的统一执行事实和 Shadow 逐样本差异 | evaluation_id, evaluation_request_id, decision_point_id, point_version_id, release_id, evaluation_mode, evaluation_time, technical_status, proposed_decision, business_decision, decision_adjustment_reason_code, default_decision_used_flag, started_time, completed_time, elapsed_millis, input_snapshot_hash, failure_code, shadow_run_id, baseline_evaluation_id, decision_changed_flag, action_changed_flag, comparison_severity, compared_time；request 唯一；SUCCEEDED 必须同时有 proposed/business，二者不同时必须有调整原因；FAILED/TIMED_OUT 两个决定与调整原因全空、default flag=0 且 failure_code 必填；default flag=1 时 proposed 必须等于 Point Version 默认决定；SHADOW 比较字段完整，PRIMARY/REPLAY 全空 |
| evaluation_feature_result | Feature 实际结果 | result_id, evaluation_id, feature_version_id, execution_sequence, technical_status, value_type, string_value, number_value, boolean_value, datetime_value, duration_value, elapsed_millis, failure_code；evaluation+feature_version 唯一；标量类型 XOR |
| evaluation_rule_trace | 统一 Rule/Predicate 可解释轨迹 | trace_id, evaluation_id, trace_type, rule_set_version_id, rule_id, node_id, execution_sequence, result_value, terminal_flag, elapsed_millis, failure_code, rule_result_slot, predicate_result_slot；RULE/PREDICATE 字段矩阵互斥；每 Evaluation+Rule 和 Evaluation+Node 各最多一行，各类型 sequence 独立唯一 |
| evaluation_action_intent | 命中后原始 Action | intent_id, evaluation_id, source_type, rule_action_id, obligation_id, action_version_id, resolved_target_type, resolved_target_key, intent_sequence, merged_effect_id, status；RULE 只填 rule_action_id，并且所属 Rule Set 必须绑定 Evaluation 的准确 Point Version；OBLIGATION 只填 obligation_id、只能引用 Action 型义务且义务必须属于同一 Point Version；两类来源/序号分别唯一；MERGED 必须引用 Effect，其他状态不得引用 |
| action_effect | 冲突合并后的最终 Action 与执行状态 | effect_id, evaluation_id, action_version_id, effect_mode, target_type, target_key, merge_policy, effect_hash, effect_status, idempotency_key, execution_type, execution_status, attempt_count, started_time, completed_time, last_error_code, last_error_summary；每个 Effect 至少一个 MERGED Intent，双方 Evaluation/Action/Target 一致且 merge_policy 等于 Action Version；`RISK_ACTION_EFFECT_V1` 只覆盖数据库可重算的冻结 Effect 身份与贡献者身份，REAL 幂等键固定为 `risk-action-effect:v1:<effect_hash>`；SIMULATED 的执行字段全部为空 |
| `checkout_input_snapshot` | Checkout 事件专属不可变输入及稳定 Assessment | snapshot_id, evaluation_id, client_id, operating_entity_id, assignment_id, input_hash；冻结 Commerce 的 OrderPreparationContext：预分配 order_id/order_item_id、Client/OE/Source Tuple、国家/Locale、industry_code/mcc_code、税前金额与币种、V5 商业上下文的 Agreement/Scope、Partner 来源的 Platform Service Charge Scope/Party 及三份 Legal/Seller 证据；Risk 不消费 Finance Entity ID；Create Order 发生在消费者交互前，Consumer/Network Evidence 可空；Finance Tax Snapshot、最终金额和 `order_context_id` 尚未生成，禁止作为输入；input_hash 覆盖完整 Canonical 输入；Risk 不回查 Commerce；evaluation 唯一 |
| transaction_risk_assessment | Payment Attempt 专属输入快照 | assessment_id, evaluation_id, payment_intent_id, payment_attempt_id, order_id, order_context_id, client_id, operating_entity_id, assignment_id, payment_method_code, amount, currency, industry_code, mcc_code, consumer_reference, consumer_email_vault_token, consumer_phone_vault_token, network_evidence_id, network_evidence_version, restricted_input_hash；payment_attempt、evaluation 分别唯一 |
| settlement_risk_assessment | Manual Payout Eligibility 专属不可变输入与结果快照 | assessment_id, evaluation_id, checkpoint_type, role_type, role_id, operating_entity_id, gross_amount, currency, bank_country, source_fact_type, source_fact_id, source_fact_version, input_hash, eligibility_decision, required_amount, required_currency, control_set_hash；evaluation 唯一；checkpoint_type 等于 Decision Point.point_code，input_hash 等于 Evaluation.input_snapshot_hash；Payout 创建/最终审批只接受 PAYOUT，Execution 创建/外部提交只接受 PAYOUT_EXECUTION；结果四列只在 Eligibility 实际执行时成组冻结，Client 金额币种成对且同请求币种，Local Partner 两列均空 |

`decision_point.business_domain` 与三类快照严格成矩阵：CHECKOUT/PAYMENT/SETTLEMENT 分别恰好一张 Checkout/Transaction/Settlement 行且另外两类为零；DUE_DILIGENCE/DISPUTE 三类均为零。Due Diligence 的正式决定通过 qualification_decision.decision_evaluation_id 追溯；Dispute Liability 通过 dispute_liability_decision.decision_evaluation_id 追溯，不为它们重复建立空壳 Assessment 表。

`RISK_ACTION_EFFECT_V1` 的 Canonical Object 只有三部分：`schema`；由 `action_version_id/effect_mode/evaluation_id/merge_policy/target_key/target_type` 组成的 `effect`；以及属于该 Effect 且 `status=MERGED` 的 `contributors`。每个贡献者只含 `intent_id/intent_sequence/obligation_id/resolved_target_key/resolved_target_type/rule_action_id/source_type`，先按数值型 `intent_sequence`、再按数值型 `intent_id` 排序。所有 BIGINT 以无前导零的十进制 JSON String 表示，`intent_sequence` 是正安全整数，nullable 来源 ID 使用 JSON Null；对象按 RFC 8785 JCS 序列化为 UTF-8，再计算 SHA-256 小写十六进制。Effect ID、执行生命周期字段、Handler 返回值及未持久化结果均不进入 Hash；因此任一实例都能仅用本库行确定性复算。

### 4.4 冷却、名单与风险数据面（6 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| risk_restriction | Consumer 等主体的冷却/限制状态 | restriction_id, restriction_no, restriction_type, subject_type, subject_key, source_effect_id, status, effective_from, effective_to, supersedes_restriction_id, version；restriction_no 唯一；区间合法 |
| risk_list | Risk 名单定义 | list_id, list_code, list_name, supported_subject_type, matching_mode, status, version；list_code 唯一；Matching Mode 由工程注册 |
| risk_list_entry | 名单成员事实 | entry_id, list_id, subject_type, subject_key, source_effect_id, status, active_slot, effective_from, effective_to, supersedes_entry_id, version；仅 ACTIVE 写 active_slot=1，历史行写 NULL；同名单和主体一次最多一个 ACTIVE |
| payment_attempt_fact | Payment Attempt 权威状态投影 | fact_id, payment_attempt_id, payment_intent_id, attempt_status, result_reason, limit_reservation_group_id, source_event_key, source_version, source_payload_hash, occurred_time, version；payment_attempt 唯一；来源版本单调 |
| velocity_event | 可计数/计额的不可变风险事件 | velocity_event_id, event_code, source_type, source_id, source_version, occurred_time, amount, currency, event_hash；来源事件和 event_code 唯一 |
| velocity_event_subject | 风险事件关联主体 | event_subject_id, velocity_event_id, subject_type, subject_key；event+subject_type+subject_key 唯一；索引 subject_type+subject_key+event_code+occurred_time |

velocity_event_subject 是明确的事件—主体关联，不是任意属性 EAV。Subject Resolver 负责类型特定的规范化和保护；IP 原值仍只保存在受限 Assessment Input，普通日志和 MQ 不输出 subject_key。

滑动窗口 Feature 以 velocity_event 明细为准确性来源并以 decision_evaluation.evaluation_time 为时间锚点。未来若增加 t_velocity_bucket，它只是可重建加速投影，窗口首尾仍必须由明细校准。

### 4.5 Limit Version、Binding、Position 与 Reservation（7 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| limit_version | 限额身份与不可变阈值版本 | limit_version_id, limit_code, limit_name, version_no, subject_type, metric_type, window_unit, window_size, amount_limit, count_limit, currency_mode, fixed_currency, content_hash, status, frozen_by, frozen_time；code+version 唯一；同 code 的 subject_type+metric_type 永不变化；DRAFT 无冻结信息，FROZEN/RETIRED 必须保留冻结人和时间；金额/次数阈值与币种模式严格成矩阵；发布前按 CODE-MOR:RISK:LIMIT_VERSION:1 重算 Hash |
| decision_point_limit_binding | 纯限额义务的有序限额绑定 | binding_id, obligation_id, limit_version_id, sequence_no, subject_source_type, subject_feature_version_id, subject_input_field_id, amount_source_type, amount_feature_version_id, amount_input_field_id；义务+版本、义务+顺序分别唯一；Feature/Context 来源 XOR；只允许挂在同 Point Version 的 RESERVE_LIMIT_GROUP 义务上 |
| limit_position | 固定 UTC Window 当前计数 | position_id, limit_version_id, subject_key, currency_scope_key, window_start, window_end, reserved_amount, committed_amount, reserved_count, committed_count, version；组合唯一；主体类型和币种规则由 Limit Version 推导，FIXED/PER_CURRENCY 使用实际币种键，NO_CURRENCY 使用字面值 NO_CURRENCY |
| limit_reservation_group | 一次 Assessment 的稳定原子占用集合与当前状态 | reservation_group_id, assessment_id, obligation_id, status, hold_reason, resolved_time, version；assessment 唯一；即使 Binding 为零也必须创建空 Group；PAYMENT_UNKNOWN 必须有 hold_reason，终态必须有 resolved_time |
| limit_reservation | Group 内不可变单项占用 | reservation_id, reservation_group_id, position_id, amount, count_value, create_time；group+position 唯一；至少金额或次数之一大于零；状态、原因和收口时间只由 Group 表达 |
| limit_group_state_change | Group 状态历史 | change_id, reservation_group_id, from_status, to_status, reason_code, trigger_type, trigger_id, actor_id, aggregate_version, occurred_time；首行必须 NULL→RESERVED；后续只允许 RESERVED→PAYMENT_UNKNOWN/COMMITTED/RELEASED 或 PAYMENT_UNKNOWN→COMMITTED/RELEASED，并连续追加 |
| limit_movement | Position 账式变动 | movement_id, reservation_id, movement_type, amount_delta, count_delta, trigger_type, trigger_id, occurred_time, position_version；Position 通过不可变 Reservation 定位；非零变动且触发来源+类型+Reservation 唯一 |

硬限额只支持 UTC 固定窗口。最近十分钟、最近二十四小时等滑动判断属于 Feature/Velocity，不得把固定 Position 冒充滑动窗口。

原子规则：

1. PRIMARY 候选 ALLOW 时解析准确 RESERVE_LIMIT_GROUP Obligation 的 0..N 条 Binding；非空时按 limit_version_id + subject_key + currency_scope_key + window_start 的稳定顺序锁定全部 Position。
2. 检查 committed + reserved + requested 不超过阈值；金额型 Binding 必须有 NUMBER 来源，COUNT 型不得伪造金额来源。
3. Assessment 正式决定与 Group 总是同事务提交；每条 Binding 的不可变 Reservation、RESERVE Movement 与 Position 在该事务提交。零 Binding 只创建空 Group，不创建伪 Reservation；没有批准的外部副作用时不机械登记补偿任务。
4. 任一 Limit 失败则全部回滚，正式 business_decision 为 DENY/LIMIT_EXCEEDED。
5. SHADOW/REPLAY 不创建 Group，不占 Position。
6. Group 是唯一当前状态权威，Reservation 不复制状态；每次状态迁移追加 limit_group_state_change。
7. PaymentSucceeded 将 RESERVED/PAYMENT_UNKNOWN 提交为 COMMITTED。
8. PaymentAttemptFailed 对安全失败、Provider 提交前 Cancel 和权威 Void 释放为 RELEASED。
9. PaymentAttemptUnknown 转为 PAYMENT_UNKNOWN 并继续占用，不得自动过期。
10. Refund、Dispute 和已 Capture Reversal 默认不恢复原交易额度；重复或乱序事件不得回退终态或重复 Movement。

发布静态校验必须以完整版本闭包读取每个被绑定的 `limit_code`：逐行验证 Version/Window/Threshold/Currency/Freeze 形状，按固定 Canonical Schema 重算 `content_hash`，并证明同 Code 的 `subject_type + metric_type` 未变化；只有校验通过且状态为 FROZEN 的准确 Version 才能进入 Binding。未来 Repository/Processor 必须保证闭包装载完整，不能以漏行绕过门禁。

### 4.6 Settlement Eligibility Control（3 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| settlement_control | Client/Local Partner 长期 Manual Payout 资格/限制控制根 | control_id, control_no, role_type, role_id, control_type, reason_code, source_action_effect_id, status, effective_from, effective_to, required_amount, required_currency, release_condition_code, control_instance_key, content_hash, active_slot, version；control_no 与来源 Effect 分别唯一；同 Role+Instance Key 最多一条 ACTIVE |
| settlement_control_scope | Control 适用的 OE、币种、收款银行国家 OR 组合 | scope_id, control_id, operating_entity_id, currency, bank_country, scope_hash；control+scope_hash 唯一；三个业务维度可空表示受控通配，每个 Control 至少一行 |
| settlement_control_state_change | Control 创建、精确释放和过期的状态历史 | change_id, control_id, from_status, to_status, reason_code, trigger_type, trigger_id, actor_id, aggregate_version, occurred_time；物理名缩写为 t_settle_control_state_change；首行 NULL→ACTIVE，随后仅 ACTIVE→RELEASED/EXPIRED，连续追加 |

一个 REAL Action Effect 最多创建一条 Control；每条 Control 必须至少有一条 Scope 和一条初始 State Change。`SETTLEMENT_BLOCK` 允许 CLIENT/LOCAL_PARTNER 且金额字段全空；`MIN_SECURITY_DEPOSIT` 只允许 CLIENT，保存最终正金额、同币种和受控释放条件。多条 Scope 使用 OR，多个最低保证金 Control 取 MAX 而不相加。释放必须指定准确 `control_id + expected_version`、受控原因和幂等键，不得清除同一 Role 的其他 ACTIVE Control。Security Deposit 只是 Risk 门槛；Finance Account 仍是实际资金权威。

### 4.7 Dispute（7 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| dispute_case | Risk 争议案件根 | dispute_case_id, case_no, provider_dispute_notice_id, provider_dispute_source_version, provider_id, mid_id, external_dispute_id, payment_intent_id, payment_attempt_id, order_id, client_id, disputed_amount, currency, current_outcome_id, status, reason_code, client_response_due_time, provider_response_due_time, version；Notice、外部争议组合分别唯一；Client Deadline 早于 Provider Deadline |
| dispute_state_change | Dispute 状态历史 | change_id, dispute_case_id, from_status, to_status, reason_code, trigger_type, trigger_id, actor_id, occurred_time, aggregate_version；追加写且 Aggregate Version 必须与 Case Version 连续一致 |
| dispute_evidence_package | 一次按需 RFI 证据包 | package_id, dispute_case_id, version_no, status, content_hash；case+version 唯一；SUBMITTED 后不可修改；Deadline 只读取 Case 权威字段 |
| dispute_evidence_ref | 证据文件引用 | evidence_id, package_id, evidence_type, file_id, content_hash；package+type+file 唯一，只保存 Platform 稳定文件引用 |
| dispute_submission_request | Risk 给 Payment 的渠道提交指令 | request_id, dispute_case_id, package_id, request_no, status, active_slot, provider_dispute_action_id, action_source_version, requested_time, resolved_time, version；同 Case 最多一个进行中 Request，Request 与 Package 必须同案 |
| dispute_outcome | 不可变 WON/LOST 版本 | outcome_id, dispute_case_id, outcome_version, outcome, reason_code, decision_source_type, decision_source_id, source_version, provider_dispute_action_id, decided_time, supersedes_outcome_id；case+version 与 source type+id+version 分别唯一；Provider/Client/Operations 首次来源只允许 V1，后两者只产生 LOST；V2+ 只允许 Operations Correction 且更正链不得分叉 |
| dispute_liability_decision | 责任分类，不计算金额 | outcome_id（Shared PK/FK）, decision_evaluation_id, action_effect_id, recommended_principal_bearer, principal_bearer, provider_fee_bearer, platform_error_flag, provider_recovery_required, security_deposit_use_allowed, override_reason_code, reason_code, decided_by, decided_time；每个 Outcome 恰一条责任决定；规则路径 Evaluation/Effect 成对且 Effect 必须属于同一 Evaluation；人工路径二者必须同时为空并冻结 decided_by，禁止双来源 |

Dispute 使用既有 Case/State/Evidence Package/Evidence Ref/Submission Request 做按需 RFI，不增加 Generic RFI。Package `content_hash` 按 `CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1` 冻结 Case、Package Version，以及按 `evidence_type + file_id` 排序的文件 ID/Hash 集合；Status、时间和审计列不进入 Hash。Risk 决定责任类别并显式给出 `security_deposit_use_allowed`；Finance 计算本金冲回、费用、损失和追偿金额，不能从 `principal_bearer` 或 Reason Code 推导保证金许可。

Submission Request 的 `CREATED/SUBMITTED/UNKNOWN` 占用 `active_slot=1` 且不得写 `resolved_time`；`SUCCEEDED/FAILED/CANCELLED` 释放 Slot 并写收口时间。`SUBMITTED/UNKNOWN/SUCCEEDED` 必须回绑准确 Provider Action ID/Version；`FAILED/CANCELLED` 可在提交前无 Action，也可在提交后带准确 Action。命令重试由稳定 Request ID 与 `command_idempotency` 负责，不在 Request 重复保存 Hash。

三条同案复合外键固定为：Request `(package_id, dispute_case_id)` → Package `(package_id, dispute_case_id)`；Case `(current_outcome_id, dispute_case_id)` → Outcome `(outcome_id, dispute_case_id)`；Outcome `(supersedes_outcome_id, dispute_case_id)` → 直接前序 Outcome `(outcome_id, dispute_case_id)`。`outcome_version` 必须相邻连续，`current_outcome_id` 必须指向同案最新链尾；禁止跨案、跳版、回指和分叉。

Outcome Version 1 只允许三种权威入口：已验证 Provider Action 可给出 `WON/LOST`，已授权 Client/Operations 首次处置只能明确接受 `LOST`；Version 2+ 只允许 `OPERATIONS_CORRECTION`，并以非空 `decided_by` 证明授权操作。首次 `WON/LOST` State Change 必须等于 V1 Outcome；Trigger Type 分别为 `PROVIDER_OUTCOME/CLIENT_DISPOSITION/OPERATIONS_DISPOSITION`，且 `trigger_id=decision_source_id`，Provider 路径还等于 `provider_dispute_action_id`。Timer、Deadline、未提交证据和 Client 沉默都不具备 LOST 权限。每次聚合写入后，State Change 最大 Aggregate Version 必须等于 Case Version；`CLOSED→CLOSED` 按版本逐条绑定新 Operations Correction，`trigger_id` 必须等于对应更正的 `decision_source_id`，不得缺失、错配或交换。规则责任的 Action Effect 必须属于 Liability 指定的同一 Evaluation，并已达到 `REAL/APPLIED/LOCAL_SYNC/SUCCEEDED`。

### 4.8 本地可靠执行与命令幂等（2 张）

| 逻辑表 | 用途 | 关键字段与约束 |
|---|---|---|
| compensation_event | MQ 发布和本地可恢复动作 | id, event_key, event_type, sub_event_type, payload_json, execute_status, execute_count, next_execute_at, lock_owner, lock_until, last_error_code, last_error_summary, lock_version, create_time, update_time；(event_key,event_type,sub_event_type) 唯一；payload_json 可空且最大 4 KiB；execute_status DEFAULT 'INIT'，execute_count DEFAULT 0，next_execute_at/create_time/update_time DEFAULT CURRENT_TIMESTAMP(6)，lock_version DEFAULT 1；idx_comp_event_ready(execute_status,next_execute_at,id) 与 idx_comp_event_lease(execute_status,lock_until,id) 用 id 稳定同时间批次顺序 |
| command_idempotency | Assessment、Case、Settlement Control 等命令幂等 | idempotency_id, caller_scope, operation_type, idempotency_key, request_hash, response_ref, status, expire_time；caller+operation+key 唯一；同键同 Hash 返回原 Assessment/Evaluation 或释放结果，同键不同请求冲突 |

MVP 不建立独立 Inbox/Outbox。领域事务原子写入业务状态、History 和 compensation_event；afterCommit 唤醒，XXL-Job 只做兜底扫描。MQ 使用 at-least-once，消费者在目标领域表幂等。

Risk 补偿事件重建清单冻结在 `scripts/pdmaner/risk/model-policy.mjs`：Qualification、Compliance Restriction、Checkout/Payment Assessment、Settlement Control、Dispute 和异步 Action Effect 各自使用应用唯一 `event_type` 与业务 `sub_event_type`；`event_key` 分别由对应不可变 State Change/Assessment ID、Restriction ID 或 Action Effect `idempotency_key` 构造。Handler 从清单列出的 Risk 权威表重建 MQ 信封或本地动作，永久幂等落在对应历史/事实唯一身份。只有会被后来状态变化覆盖且确实无法回源的 Restriction 事件时点字段可进入最小非敏感 `payload_json`；其余不把 Source Version、Schema、Envelope、Payload Hash、Vault Token 或可恢复上下文复制到补偿表。

## 5. 核心关系图

### 5.1 Due Diligence 与资格

~~~mermaid
erDiagram
    DD_PROGRAM ||--o{ DD_PROGRAM_VERSION : versioned_as
    DD_PROGRAM_VERSION ||--o{ DD_PROGRAM_REQUIREMENT : defines
    DD_PROGRAM_VERSION ||--o{ DD_PROGRAM_CHECK : defines
    DD_PROGRAM_VERSION ||--o{ DD_PROGRAM_SCREENING : defines
    DD_PROGRAM_VERSION ||--o{ DUE_DILIGENCE_CASE : frozen_by
    DUE_DILIGENCE_CASE ||--o{ DD_CASE_SUBJECT : covers
    DUE_DILIGENCE_CASE ||--o{ DD_CASE_DEPENDENCY : parent_case
    DUE_DILIGENCE_CASE ||--o{ DD_CASE_DEPENDENCY : child_case
    DD_CASE_SUBJECT ||--|| DD_PARTY_SNAPSHOT : freezes
    DUE_DILIGENCE_CASE ||--o{ DD_REQUIREMENT : instantiates
    DD_REQUIREMENT ||--o{ DD_EVIDENCE_REF : evidenced_by
    DD_CASE_SUBJECT ||--o{ DD_EVIDENCE_REF : submitted_for
    DD_CASE_SUBJECT ||--o{ DD_CHECK : checked_by
    DD_CASE_SUBJECT ||--o{ SCREENING_RUN : screened_by
    SCREENING_RUN ||--o{ SCREENING_HIT : produces
    SCREENING_HIT ||--o{ SCREENING_RESOLUTION : resolved_by
    DUE_DILIGENCE_CASE ||--o{ DD_CASE_STATE_CHANGE : transitions
    DUE_DILIGENCE_CASE ||--o{ QUALIFICATION_DECISION : concludes
    DECISION_EVALUATION o|--o{ QUALIFICATION_DECISION : recommends
    QUALIFICATION_DECISION o|--o{ DD_CASE_DEPENDENCY : accepted_by
    QUALIFICATION_DECISION }o--|| ROLE_QUALIFICATION : updates
    ROLE_QUALIFICATION ||--o{ QUALIFICATION_STATE_CHANGE : transitions
    QUALIFICATION_DECISION ||--o{ QUALIFICATION_STATE_CHANGE : explains
~~~

### 5.2 Decision Point、Rule、Action 与 Release

~~~mermaid
erDiagram
    DECISION_POINT ||--o{ DECISION_POINT_VERSION : versioned_as
    DECISION_POINT_VERSION ||--o{ DECISION_POINT_INPUT_FIELD : exposes
    DECISION_POINT_VERSION ||--o{ DECISION_POINT_FEATURE : allows
    FEATURE_VERSION ||--o{ FEATURE_DEPENDENCY : depends_on
    FEATURE_VERSION ||--o{ DECISION_POINT_FEATURE : bound_as
    DECISION_POINT ||--o{ RULE_SET_VERSION : owns_versions
    DECISION_POINT_VERSION ||--o{ DECISION_POINT_RULE_SET : orders
    RULE_SET_VERSION ||--o{ DECISION_POINT_RULE_SET : bound_as
    RULE_SET_VERSION ||--o{ RISK_RULE : contains
    RISK_RULE ||--o{ RULE_NODE : owns
    RULE_NODE ||--o{ RULE_OPERAND : compares
    ACTION_VERSION ||--o{ ACTION_PARAMETER_DEFINITION : defines
    RISK_RULE ||--o{ RULE_ACTION : produces
    ACTION_VERSION ||--o{ RULE_ACTION : binds
    RULE_ACTION ||--o{ RULE_ACTION_PARAMETER : configures
    DECISION_POINT_VERSION ||--o{ DECISION_POINT_OBLIGATION : requires
    FEATURE_VERSION ||--o{ FEATURE_DEPENDENCY : required_by
    DECISION_POINT_VERSION ||--o{ DECISION_RELEASE : released_as
    DECISION_RELEASE ||--o{ RELEASE_AUDIT_EVENT : audited_by
    DECISION_RELEASE ||--o{ RELEASE_SHADOW_RUN : baseline
    DECISION_RELEASE ||--o{ RELEASE_SHADOW_RUN : candidate
    RELEASE_SHADOW_RUN ||--o{ RELEASE_AUDIT_EVENT : reviewed_against
~~~

### 5.3 Evaluation、Action、Assessment 与 Limit

~~~mermaid
erDiagram
    DECISION_RELEASE ||--o{ DECISION_EVALUATION : governs
    DECISION_EVALUATION ||--o{ EVALUATION_FEATURE_RESULT : computes
    FEATURE_VERSION ||--o{ EVALUATION_FEATURE_RESULT : produced_by
    DECISION_EVALUATION ||--o{ EVALUATION_RULE_TRACE : explains
    RISK_RULE ||--o{ EVALUATION_RULE_TRACE : traced_as
    RULE_NODE o|--o{ EVALUATION_RULE_TRACE : predicate_node
    RELEASE_SHADOW_RUN o|--o{ DECISION_EVALUATION : compares_in
    DECISION_EVALUATION o|--o{ DECISION_EVALUATION : baseline_for
    DECISION_EVALUATION ||--o{ EVALUATION_ACTION_INTENT : intends
    EVALUATION_ACTION_INTENT }o--o| ACTION_EFFECT : merged_into
    DECISION_EVALUATION ||--o{ ACTION_EFFECT : produces
    DECISION_EVALUATION ||--o| CHECKOUT_INPUT_SNAPSHOT : evaluates_checkout
    DECISION_EVALUATION ||--o| TRANSACTION_RISK_ASSESSMENT : evaluates_attempt
    DECISION_EVALUATION ||--o| SETTLEMENT_RISK_ASSESSMENT : evaluates_settlement
    TRANSACTION_RISK_ASSESSMENT ||--o| LIMIT_RESERVATION_GROUP : reserves
    DECISION_POINT_OBLIGATION ||--o{ DECISION_POINT_LIMIT_BINDING : configures
    LIMIT_VERSION ||--o{ DECISION_POINT_LIMIT_BINDING : selected_as
    LIMIT_VERSION ||--o{ LIMIT_POSITION : partitions
    LIMIT_RESERVATION_GROUP ||--o{ LIMIT_RESERVATION : contains
    LIMIT_POSITION ||--o{ LIMIT_RESERVATION : holds
    LIMIT_RESERVATION_GROUP ||--o{ LIMIT_GROUP_STATE_CHANGE : transitions
    LIMIT_RESERVATION ||--o{ LIMIT_MOVEMENT : changes
~~~

### 5.4 Settlement 与 Dispute

~~~mermaid
erDiagram
    DECISION_RELEASE ||--o{ DECISION_EVALUATION : executes_under
    DECISION_EVALUATION ||--o{ ACTION_EFFECT : produces
    ACTION_EFFECT ||--o| SETTLEMENT_CONTROL : creates
    DECISION_EVALUATION ||--o| SETTLEMENT_RISK_ASSESSMENT : freezes
    SETTLEMENT_CONTROL ||--|{ SETTLEMENT_CONTROL_SCOPE : applies_to
    SETTLEMENT_CONTROL ||--|{ SETTLEMENT_CONTROL_STATE_CHANGE : transitions
    DISPUTE_CASE ||--o{ DISPUTE_STATE_CHANGE : transitions
    DISPUTE_CASE ||--o{ DISPUTE_EVIDENCE_PACKAGE : packages
    DISPUTE_EVIDENCE_PACKAGE ||--o{ DISPUTE_EVIDENCE_REF : contains
    DISPUTE_CASE ||--o{ DISPUTE_SUBMISSION_REQUEST : requests
    DISPUTE_EVIDENCE_PACKAGE ||--o{ DISPUTE_SUBMISSION_REQUEST : submitted_by
    DISPUTE_CASE ||--o{ DISPUTE_OUTCOME : concludes
    DISPUTE_CASE o|--o| DISPUTE_OUTCOME : points_to_current
    DISPUTE_OUTCOME o|--o| DISPUTE_OUTCOME : supersedes
    DISPUTE_OUTCOME ||--|| DISPUTE_LIABILITY_DECISION : classifies
    DECISION_EVALUATION o|--o{ DISPUTE_LIABILITY_DECISION : recommends
~~~

## 6. 状态机

### 6.1 Rule Set Version 与 Decision Release

~~~mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> FROZEN: AST/type/handler checks passed
    FROZEN --> RETIRED: no longer eligible
    RETIRED --> [*]
~~~

~~~mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> VALIDATING: validation starts
    VALIDATING --> SHADOWING: 8 static checks passed
    SHADOWING --> REVIEW_PENDING: shadow completed and quality passed
    REVIEW_PENDING --> APPROVED: second distinct reviewer approves
    APPROVED --> ACTIVE: same-command atomic activation
    ACTIVE --> SUPERSEDED: replaced by another release
    DRAFT --> REJECTED: cancelled
    VALIDATING --> REJECTED: validation failed
    SHADOWING --> REJECTED: quality gate failed
    REVIEW_PENDING --> REJECTED: reviewer rejects
    REJECTED --> [*]
    SUPERSEDED --> [*]
~~~

Candidate Release 从创建起不可修改。进入 SHADOWING 前必须存在八项 `PASSED` STATIC_CHECK Audit Event；进入 REVIEW_PENDING 前 Run 必须 `COMPLETED + PASSED`；两个不同 Reviewer 必须批准同一 Run，第二次批准命令在同一本地事务中追加 Approval/State Audit、将 Candidate 从 APPROVED 激活并把旧 ACTIVE 置为 SUPERSEDED。任何 Rule、Feature、Action、Point、Limit Version 或 Limit Binding 变化都创建新 Version 和新 Release。回滚不是修改旧 Release，而是创建新的已审核 Release，重新引用历史批准组合。

### 6.2 Decision Evaluation

~~~mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> EVALUATING
    EVALUATING --> SUCCEEDED_ALLOW
    EVALUATING --> SUCCEEDED_REVIEW
    EVALUATING --> SUCCEEDED_DENY
    EVALUATING --> FAILED
    EVALUATING --> TIMED_OUT
    SUCCEEDED_ALLOW --> [*]
    SUCCEEDED_REVIEW --> [*]
    SUCCEEDED_DENY --> [*]
    FAILED --> [*]
    TIMED_OUT --> [*]
~~~

technical_status 与业务决定分列。SUCCEEDED 必须同时形成 proposed_decision 和 business_decision；相等时调整原因为空，不同时必须填写调整原因。FAILED/TIMED_OUT 的 proposed/business/adjustment 全空、default_decision_used_flag=0 且 failure_code 必填。对 Payment Attempt，proposed_decision=ALLOW 只有在 `RESERVE_LIMIT_GROUP` 系统义务成功并创建 Group 后才能成为 business_decision=ALLOW；额度不足可调整为 DENY/LIMIT_EXCEEDED。

### 6.3 Due Diligence Case

~~~mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> SUBMITTED
    SUBMITTED --> IN_REVIEW
    IN_REVIEW --> WAITING_EVIDENCE
    WAITING_EVIDENCE --> IN_REVIEW
    IN_REVIEW --> APPROVED
    IN_REVIEW --> REJECTED
    DRAFT --> WITHDRAWN
    SUBMITTED --> WITHDRAWN
    IN_REVIEW --> WITHDRAWN
    WAITING_EVIDENCE --> WITHDRAWN
    DRAFT --> EXPIRED
    SUBMITTED --> EXPIRED
    IN_REVIEW --> EXPIRED
    WAITING_EVIDENCE --> EXPIRED
    APPROVED --> [*]
    REJECTED --> [*]
    WITHDRAWN --> [*]
    EXPIRED --> [*]
~~~

资格过期不修改原 Case 或 Decision；系统更新 Role Qualification 为 EXPIRED。周期复审到期时，调度器按冻结频率和 qualification CAS 直接创建 `case_origin=CONTINUOUS_REVIEW` 的新 Case，`scheduled_review_time` 是复审周期幂等坐标，不存在独立 Plan/Run 生命周期。

### 6.4 Limit Reservation Group

~~~mermaid
stateDiagram-v2
    [*] --> RESERVED
    RESERVED --> COMMITTED: payment succeeded
    RESERVED --> RELEASED: authoritative failure/cancel/void
    RESERVED --> PAYMENT_UNKNOWN: payment or void unknown
    PAYMENT_UNKNOWN --> COMMITTED: late success
    PAYMENT_UNKNOWN --> RELEASED: explicit failure or authoritative void
    COMMITTED --> [*]
    RELEASED --> [*]
~~~

PAYMENT_UNKNOWN 不自动过期。Group 是当前状态唯一权威；Reservation 创建后不可变，状态迁移以 Group CAS、追加 State Change 和逐 Reservation Movement 在同一事务收口。零 Binding 产生的空 Group 也走相同状态机。

### 6.5 Settlement Control

~~~mermaid
stateDiagram-v2
    [*] --> ACTIVE
    ACTIVE --> RELEASED
    ACTIVE --> EXPIRED
    RELEASED --> [*]
    EXPIRED --> [*]
~~~

状态边固定为 `NULL → ACTIVE → RELEASED|EXPIRED`。未来生效由 `effective_from` 表达，不增加中间状态；RELEASED/EXPIRED 是终态且永不复活，同一业务实例以后再次命中必须创建新的 Control Root。

PRIMARY Eligibility 使用 MySQL InnoDB `REPEATABLE READ`，沿 `(role_type,role_id,active_slot,control_id)` 索引锁定 Role 活动区间；即使当前零行也必须取得 Next-Key/Gap Lock。创建、精确释放与 Timer 统一遵守 `Role Range -> control_id 升序 Root -> Scope/Event`，不得从 Root 反向获取 Range。Control `create_time`、State Change `occurred_time`、Root `update_time` 和 Evaluation `completed_time` 只能在取得所需锁或冻结最终结果后采样数据库 UTC。

Operations 释放必须携带准确 `control_id + expected_version + 幂等键`；同键同 Hash 返回原结果，同键异 Hash 冲突，其他 Control 不受影响。Timer 的稳定 Trigger ID 为 `settlement-control-expiry:v1:<control_id>:<expected_version>:<effective_to>`；重复 Trigger 幂等，Version 不符只在 Root 已是同一 EXPIRED 结果时视为成功，否则进入冲突处置。业务匹配在 `effective_to` 已停止，Timer 延迟不得延长有效期。

### 6.6 Dispute Case

~~~mermaid
stateDiagram-v2
    [*] --> EVIDENCE_REQUIRED
    EVIDENCE_REQUIRED --> UNDER_REVIEW
    EVIDENCE_REQUIRED --> LOST
    UNDER_REVIEW --> SUBMITTED
    UNDER_REVIEW --> LOST
    SUBMITTED --> WON
    SUBMITTED --> LOST
    WON --> CLOSED
    LOST --> CLOSED
    CLOSED --> CLOSED: append corrected outcome
    CLOSED --> [*]
~~~

直接 LOST 必须创建 Outcome Version 1 和 Shared-PK Liability Decision，只能由匹配 `PROVIDER_OUTCOME` Trigger 的已验证 Provider LOST、匹配 `CLIENT_DISPOSITION` 的已授权 Client 明确认责，或匹配 `OPERATIONS_DISPOSITION` 的已授权 Operations 明确认责触发；Trigger ID 必须等于对应 Outcome 来源 ID。Deadline、Timer、Client 沉默或未提交证据不得自动认损。CLOSED 自环只允许以 `OPERATIONS_CORRECTION` Trigger 追加一条可信更正版本，且 Trigger ID 必须逐版匹配更正命令，不能覆盖原事实。Case 当前指针必须指向同案链尾；Version N 只能替代同案 Version N-1，首次结果 State 必须等于 V1 Outcome，State Change Aggregate Version 必须与 Case Version 一致，禁止跨案、跳版、回指或分叉。

## 7. 领域事件契约

| 方向 | 事件 | Risk 处理或发布语义 |
|---|---|---|
| Publish | RoleQualificationApproved | 携带 role_type、role_id、qualification_type、decision_id、valid_from/to、aggregate_version |
| Publish | RoleQualificationRejected | 携带准确 Decision、原因和角色上下文，不推导同 Party 其他角色 |
| Publish | RoleQualificationExpired | Timer 触发，引用原 Decision，不修改历史 Case |
| Publish | RoleQualificationRevoked | 合规撤销时引用新 Decision/原因 |
| Subscribe | PaymentSucceeded | 必须含 payment_attempt_id 与 limit_reservation_group_id；将 RESERVED/PAYMENT_UNKNOWN 原子提交 |
| Subscribe | PaymentAttemptFailed | 必须含 Attempt、Group ID 字段、终态、原因、allow_new_attempt 和来源版本；曾 ALLOW 时 Group ID 必须非空并精确匹配 |
| Subscribe | PaymentAttemptUnknown | 必须含 Attempt 与 Group ID；仅转 PAYMENT_UNKNOWN 并继续占用 |
| Subscribe | DisputeNoticeReceived | 以 provider_dispute_notice_id 幂等建案，冻结 Provider/MID/External Dispute、Attempt、金额币种和截止时间 |
| Subscribe | DisputeSubmissionStatusChanged | 匹配 Risk request_id、Case、Package 与准确 provider_dispute_action_id |
| Subscribe | ProviderDisputeOutcomeReceived | 绑定 Notice、Risk Request、Action ID 和 Source Version，形成 Risk Outcome |
| Publish | DisputeOpened | 携带 Notice ID/Version、Case、金额币种和两个截止时间；仅启动举证，不触发 Finance 记账或冻结 |
| Publish | DisputeEvidenceRequested | 与开案同事务登记，携带 Case、Client/Provider Deadline 和材料类型 |
| Publish | DisputeWon / DisputeLost | 携带 Notice、Case、Outcome ID/Version、Liability、`security_deposit_use_allowed` 和原争议金额币种；只有权威 DisputeLost 才触发 Finance Journal，Client 沉默不得自动生成 Lost |
| Publish | DisputeOutcomeCorrected | 携带旧/新 Outcome ID/Version、责任分类和显式保证金许可；下游只追加补偿，不改写既有 Journal |
| Publish | SettlementControlChanged | 每个 Control Version 只发布一个聚合事件，携带 control_id/version、Role、Type、Reason、状态、有效期、金额门槛，以及按 scope_id 排序的完整 Scope Set、scope_count 和 scope_set_hash；创建、释放、过期都保留完整集合，不携带 Finance 实际余额 |

所有事件必须携带 event_key、schema_version、occurred_time、source_type/source_id/source_version 和 payload_hash。消费事务原子提交领域变化以及需要产生的 compensation_event；不能先 ACK 再落业务事实。

## 8. 强制业务不变量

1. 每个 Decision Point 的 Rule Set 只属于该 Point；不同 Point 复用时必须复制为新 Rule Set。
2. 一个 Point Version 可以绑定多个有序 Rule Set Version；执行顺序为 Rule Set sequence_no 后 Rule priority_no。
3. Rule Condition 使用无环 AST；Group 只允许 AND/OR/NOT，Predicate 的 Operator、Operand 数量和 Value Type 必须兼容。
4. Feature、Context 和 Literal 是 Rule Operand 唯一来源；不允许任意字段名、JSON、SQL、Script 或运行时类名。
5. Feature 和 Action Handler 必须已注册、已部署、哈希匹配并被 Point Version Allowlist 接受。
6. 一条 Rule 最多一个 Terminal Decision；非终态 Action 可继续执行；第一次 Terminal Decision 停止后续 Rule。
7. 没有 Terminal Decision 时使用 Point Version 的显式 default_decision，并令 default_decision_used_flag=1、proposed_decision 等于该默认值；技术失败不得回退到默认业务决定。
8. 相同目标的 Action 必须按 Action Version 固定 merge_policy 合并；每个 MERGED Intent 与 Effect 的 Evaluation、Action、Target、Merge Policy 必须逐值一致，Effect 必须至少有一个贡献者，运营不能覆盖合并语义。
9. 系统义务由 Point Version 工程绑定，运营不可删除。Action 型义务才进入 Intent/Effect；Payment Attempt 的 RESERVE_LIMIT_GROUP 是纯限额义务，以 0..N 条 Binding 冻结准确 Limit Version 与取值来源，候选 ALLOW 只有成功创建 Limit Reservation Group 才能正式 ALLOW；零 Binding 也创建空 Group。
10. 每个 Decision Point、每个 Environment 同时最多一个 PRIMARY/ACTIVE Release；MVP 无动态发布 Scope、无真实流量灰度。
11. Candidate Release 创建后不可修改；所有组成通过精确 Version 和 content_hash 冻结，不使用 root.current_version_id 形成第二真相。
12. Release 必须在 Shadow 前完成八项静态校验，在 `COMPLETED + PASSED` Run 后进入 REVIEW_PENDING；两个不同 Reviewer 必须批准同一 Run，第二次批准命令原子完成 Candidate `APPROVED→ACTIVE` 与旧 Release `ACTIVE→SUPERSEDED`。
13. Shadow 与 Replay 只形成 SIMULATED Effect，不返回业务结果、不创建真实 Risk Restriction/List Entry/Settlement Control/Limit Reservation/Velocity Event、不发布业务事实或 compensation_event，也不修改原 Assessment。
14. Shadow 必须复用 PRIMARY 的输入快照和 evaluation_time；受限 Token/网络原值不进入 MQ，任务只传 Assessment ID。
15. technical_status 只允许 SUCCEEDED/FAILED/TIMED_OUT；SUCCEEDED 的 proposed/business 必填并遵守调整原因矩阵，FAILED/TIMED_OUT 不得携带业务决定或默认决定标记，也不计业务 DENY。
16. 每次 Checkout 调用恰好一个 PRIMARY Evaluation；其 Point Domain 只允许一张 Checkout Snapshot。Commerce 只能锁定一个 ALLOW Assessment，REVIEW/DENY 均拒绝当前创建。
17. 每个 Payment Attempt 恰好一个 Transaction Risk Assessment 和一个 PRIMARY Evaluation；Payout 创建、最终 Finance 审批、Execution 创建与每次外部提交各有一个新的 Settlement Assessment 和 PRIMARY Evaluation，同一幂等命令重放复用原坐标，不同检查点或新 Retry Execution 不得复用；DD/Dispute 不建空壳快照。不得在新 Run 中替换 Order Context、Operating Entity、行业或 MCC。
18. Action Intent 全部收集并校验后才形成 Effect；RULE/OBLIGATION 来源严格 XOR 并且必须属于 Evaluation 的准确 Point Version，RESERVE_LIMIT_GROUP 义务无 Intent；`effect_hash` 与 REAL 幂等键使用数据库可重算的固定 Canonical 契约；评估失败时不得形成 REAL 或 SIMULATED Effect，本次 Action 全部不生效。
19. PRIMARY 本地状态 Action、Evaluation 正式结果和领域状态必须同事务提交；异步 Action 通过同事务 compensation_event 可靠执行。
20. Risk Restriction 和 List Entry 必须保留 source_effect_id；人工解除、延期和覆盖也必须形成可追溯 Action Effect。
21. Velocity 滑动窗口以明细 Event 和 evaluation_time 为准；任何聚合桶只可加速，不能改变精确结果。
22. 硬 Limit 只支持 FIXED_UTC；不得把单一 Position/Bucket 冒充真实滑动窗口。
23. PRIMARY ALLOW 与 Group、Reservation、Movement、Position 更新原子提交；即使零条 Binding 也创建没有 Reservation 明细的空 Group。
24. Payment 只持有 Group ID；UNKNOWN 保留占用；只有权威 Success/Failure/Cancel/Void 才提交或释放。Refund/Dispute 默认不恢复额度。
25. 同一 Party 资料可复用，但 Client、Local Partner、Operating Entity 和 Related Person 资格相互独立。
26. Organization KYB 必须引用全部 Required UBO、Director、Signatory KYC 决定；缺失或失效时不得 APPROVED。
27. Application Due Diligence Case 冻结 Program Version、与 CCT 完全一致的 Role/Qualification Tuple 和来源提交坐标；Continuous Review/Manual Case 不伪造 Submission；Case Subject 冻结 Party/Profile 及准确 Relation Version ID/No 与可选百分比持股，后续配置和主档变化不改写旧 Case。
28. Program Version 定义可选复审频率；Role Qualification 保存当前调度。到期任务必须以 version + next_review_time 做 CAS，并在同一本地事务创建唯一 Scheduled Review Case、推进下次时间和登记可靠任务。
29. 自动规则结果只是 Qualification recommendation；正式 Decision 可由获准自动流程或 Reviewer 形成，覆盖必须记录原因。
30. Risk 只能发布 Settlement Control 及 Client 最低 Security Deposit 门槛，不修改 Finance Account、余额、Ledger 或银行执行；Local Partner `MIN_SECURITY_DEPOSIT` 配置在发布和运行时都必须失败。
31. Dispute Case 建案时冻结 Notice、金额币种和 Deadline；Request 与 Package 必须同 Case，`UNKNOWN` 继续占用活动 Slot，直到权威收口才能创建下一 Request。
32. Risk 拥有 Dispute Outcome/Liability；Payment 拥有 Provider 交互；Finance 拥有金额计算、余额和会计。Outcome 与 Shared-PK Liability、Case 当前指针/Version、State Change 和补偿事件必须在同一 Case Root Lock 本地事务内原子提交。
33. Outcome 来源只允许 `PROVIDER_ACTION/CLIENT_DISPOSITION/OPERATIONS_DISPOSITION/OPERATIONS_CORRECTION`，以来源 Type+ID+Version 幂等；更正只追加同案线性版本。只有 `LOST + principal_bearer=CLIENT + security_deposit_use_allowed=1` 才允许 Finance 使用 Client 保证金，且该许可不得推导。
34. 所有业务状态、History 和需要发布的 compensation_event 同事务提交；重复、冲突和乱序由业务唯一键、Hash 和来源版本处理。

## 9. 跨系统引用规则

| 来源系统 | 本库允许保存 | 本库禁止保存 |
|---|---|---|
| Client & Contract | party_id、Profile/Relation Version、client_id、local_partner_id、operating_entity_id、Application ID/Version/Hash、必要 Agreement/OE Association 引用 | Party/Profile、Agreement、商业条款主档副本；不得回查当前 CCT 改写历史 |
| Commerce | order_id、order_context_id、OrderPreparationContext Version/Hash、OE/Assignment、金额币种、industry_code、mcc_code；同步最小契约中的 Consumer Token 和 Network Evidence | Order/Checkout/Refund 主档、consumer_profile Token、device identifier、跨库 View；Token/网络原值不得通过 MQ |
| Payment Orchestration | payment_intent_id、payment_attempt_id、payment_method_code、Attempt 权威事实、同步透传的批准最小输入、Provider Dispute Notice/Action 引用 | Provider/MID 主档、渠道请求响应、Provider 原始报文、跨库 View |
| Finance | RCO-I09 Assessment/Evaluation 坐标、Control ID/Version/Scope Hash、必要追偿上下文 | Account、Balance、Reserve、Ledger、Settlement、实际损失或费用计算结果；Risk 不接收 Finance 本地 eligibility_hash |
| Platform Support | DD/Dispute 使用 file_id、content_hash；IAM Actor ID、audit correlation ID | 文件正文、文件业务版本副本、IAM 主档、集中审计日志 |
| Central Vault | 已批准且有恢复消费者的 *_vault_token，以及确有消费者的 Record Version/Metadata Hash | Vault DB、密文、密钥；不得为方便默认增加 Blind Index、Masked Value 或通用 Metadata |

Risk 业务通过率以 PRIMARY decision_evaluation 及对应 Checkout/Transaction Assessment 的冻结行业/MCC 为准。FAILED/TIMED_OUT 单独统计；SHADOW/REPLAY 不混入业务通过率。未来数仓按稳定业务 ID 汇聚，Risk 不为统计跨库查询。

## 10. 敏感数据与留存

### 10.1 分类与保护

- KYC/KYB/PDD/Underwriting Snapshot、证件、地址、DOB、Screening Hit 和关联人关系属于 RESTRICTED_COMPLIANCE。
- Assessment 的稳定 Consumer Token、Network Evidence 原值与派生维度至少属于 CONFIDENTIAL_RISK。
- 四个 Commerce Stable Correlation Token 是 Checkout/Transaction 两类输入快照中的 Email/Phone Token；Risk 只按批准 Purpose 保存和关联，不拥有原值、不调用 Protect，也不因此取得 Reveal 权限。
- 三个 Risk-owned Recoverable Token 是 `dd_party_snapshot.snapshot_vault_token`、`dd_check.result_vault_token` 和 `screening_hit.name_vault_token`。当前 Vault V1 只使用 Protect/Reveal：Risk 以批准的 Data Type 与调用方上下文 Protect，授权合规人员在 `COMPLIANCE_REVIEW` Purpose 下 Reveal。`dd_party_snapshot.snapshot_vault_token` 对应批准的案件时点可恢复受保护快照，存在明确案件复核/审计 Reveal 消费者，因此保持 `NOT NULL`；另外两个按是否保存可恢复结果维持现有可空性。
- 七个 Token 物理列保持 `VARCHAR(128)` 作为协议演进缓冲，不按当前 43 字符 V1 Wire 收紧。Rule Configuration 切片只编排已注册的 Context Field/Feature/Action，规则配置不调用 Vault，也不保存 Token、敏感原值或任意 Vault 业务 Resource 参数。
- Consumer 等值/频次匹配直接使用稳定 Email/Phone Token；不另存 Consumer Blind Index/Key Version。
- Network Risk 使用 Assessment 冻结的受限原值和批准派生维度，不建立 device identifier。
- Feature 应尽量输出 Boolean、Number 或 Code，不复制输入敏感原值。任何需要恢复的新增 Feature Value 都必须先证明 Reveal 消费者并完成 Vault 专项设计。
- Platform 保存文件正文；DD/Dispute 只保存 File ID、Content Hash 与本库案件或证据包关联，文件替换产生新 File ID，不在 Risk 复制 Document Version。
- PAN、CVV/CVC、完整磁道、PIN/PIN Block 和卡有效期禁止进入 Risk、文件、日志、事件或 Vault。
- Vault Reveal 必须由 Risk 服务端在资源授权后通过部署网络限制的私有 HTTP 发起，携带当前 V1 必需但可伪造的 `X-Caller-Service`、Purpose、Reason 和 Case/Assessment 引用，并接受静态 Allowlist 不构成强认证的限制；明文不得持久缓存。

### 10.2 MVP 留存口径

- MVP 对业务事实默认保留，不自动归档、匿名化或销毁，也不以临时 Job、SQL 或人工脚本代替受控流程。
- 尽调/争议七年、敏感评估输入二十四个月、规则与发布版本十年等期限只是后续专项候选输入，当前不触发清理或 Vault Destroy。
- compensation_event 成功后可按可靠执行规范删除；FAILED 在人工处置完成且结果已落业务历史后清理。
- Platform 文件不因 Risk 本地状态变化而删除；expire_time/effective_to 只驱动业务状态，不等于数据销毁。
- 承载真实受监管数据前必须完成司法辖区 Retention、Legal Hold、Backup、Archive、Anonymization、Vault Lifecycle 和 Controlled Destruction 专项设计及法律/合规评审。

## 11. 明确禁止的模型

- 不恢复已被当前 Version、Operand、Typed Audit、Shadow Run/Evaluation、Rule Trace 和 Action Effect 吸收的独立目录根或 1:1 明细表；只有新的独立生命周期消费者才能重新拆表。
- 不创建 Party、Client、Operating Entity 或 Payment 主档副本。
- 不以一个 Party KYC 状态替代角色化 Qualification。
- 不把 REVIEW 设计成稍后恢复同一 Checkout 或 Payment Attempt 的异步批准。
- 不允许任意 SQL、Script、JSON、EAV 或跨库 View 作为规则执行语言。
- 不允许运营配置任意 Handler、Class、URL 或外部调用。
- 不允许规则集跨 Decision Point 直接引用。
- 不允许绕过 Release 静态校验、Shadow、数据检查和审核直接激活。
- 不做带真实冷却、拉黑或限额副作用的生产灰度。
- 不让 Shadow/Replay 创建 Action Execution、Limit Reservation、Risk Restriction、Settlement Control 或业务事件。
- 不把技术失败或超时写成 DENY。
- 不用 Redis 作为 Limit 权威来源；Redis 只能做非权威加速。
- 不把固定 UTC Limit Position 宣称为滑动窗口。
- 不在 Risk 保存 Finance Balance、Reserve、Ledger 或实际冻结金额。
- 不在 Risk 保存 Provider 原始报文或渠道请求响应。
- 不让 Payment 与 Risk 同时拥有 Dispute 业务终态。
- 不建立跨库外键、跨库 Repository、通用 Inbox/Outbox、通用 Case/RFI、通用 Retention 或无当前用例的基础设施。
