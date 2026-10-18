# Client & Contract 物理模型精简设计

> **历史阶段提示：** 本文保留 57 表阶段及其此前 66 表基线、测试结果、Digest 与决策原文；当前基线已进一步收敛为 [36 表实时资格与 Retention 延期设计](./2026-08-23-cct-realtime-eligibility-retention-deferral-design.md)，BCR 当前边界见 [按需白名单行级变更历史设计](./2026-08-20-cct-row-history-design.md)。不得据此恢复旧 Pricing/Share、Agreement/Legal、持久化 Eligibility、Retention 或“所有 Child 独立 BCR CAS”结构。

最后更新：2026-08-22  
状态：历史 57 表阶段设计，已被当前 55 表模型取代；本文仅保留演进证据，其中 Ownership Path 内容不代表当前能力。当前设计见 [物理建模决策](../../paytech/client-master/docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md)，PDManer 4.9.3 应用内检查待人工确认；数据库实现未授权

## 1. 背景

实施前的 Client & Contract PDManer 基线包含 66 张表。模型覆盖的业务能力完整，但存在四类可收敛问题：

1. Client Onboarding 与 Profile Change 使用两套相同申请状态机；
2. Consumer Terms、Refund Policy、Privacy Notice 使用三套完全同构的 Root/Version；
3. Eligibility 同时维护可变 Current Projection 和内容相同的不可变 History；
4. 多张业务表机械保存含义模糊的 `version/current_version_id/document_version` 和无独立消费者的 `canonical_hash`，形成重复并发令牌、重复指针或重复 Hash。

本设计采用已经确认的“平衡型”方案：保留全部既定业务能力、系统边界、审计、可靠执行和留存能力，只合并生命周期与字段结构真正相同的模型，并删除能够从唯一权威事实确定性重建的字段。

## 2. 目标与非目标

### 2.1 目标

- 将物理表从 66 张收敛为 57 张；
- 保留 Party、角色、来源、Assignment、Agreement、Pricing、Partner Share、Eligibility、审计和 Retention 的既定业务能力；
- 让业务版本、外部来源版本、审计版本和技术乐观锁各自只有一个明确含义；
- 修复 Operating Entity 在审核期间没有稳定 Role ID 的链路缺口；
- 消除重复 Current Pointer、Current Projection、无独立消费者的子行 Hash 和 Ownership Path 快照字段；
- 保持 CCT-I01 至 CCT-I13 的业务能力、原子性、Wire 字段名和跨系统事实所有权不变；Eligibility Root/History 合并只调整字段映射与说明，不制造接口破坏；
- 保留 CCT-I13 已逐项暴露给下游冻结的 Pricing/Share Component `canonical_hash`，不以父 Hash 代替既有组件级证明。

### 2.2 非目标

- 不删除或延期自动主体分配、通用行级变更历史、可靠补偿、幂等或 Retention/Legal Hold；
- 不合并 Client Pricing 与 Partner Share；
- 不把 Agreement Acceptance、Party Relation、Eligibility Evaluation 或 Source Fact 压入通用历史；
- 不引入 EAV、自由 JSON、万能 `owner_type + owner_id` 或跨库外键；
- 本阶段不创建 Migration、Java 模块、DO/Mapper/Repository、数据库实例或运行环境。

## 3. 方案比较

| 方案 | 目标表数 | 内容 | 结论 |
|---|---:|---|---|
| 保守型 | 62 | 只合并三类 Consumer Legal Root/Version | 收益不足 |
| 平衡型 | 57 | Consumer Legal、Client Application、Application Document、Eligibility 一并收敛 | 采用 |
| 深度型 | 约 53 | 继续删除 Assignment、Policy、Pricing/Share 稳定 Root | 会增加 Lineage 唯一槽、重复 Scope 和并发复杂度，不采用 |

## 4. 目标 57 表目录

### 4.1 Party 与身份：9 张

1. `party`
2. `party_profile`
3. `individual_profile`
4. `organization_profile`
5. `party_identity_key`
6. `party_profile_identifier`
7. `party_address`
8. `party_contact`
9. `party_relation`

个人与机构明细继续使用明确的 1:1 Subtype 表；地址、联系方式和法律标识具有独立基数、唯一约束或 Vault 绑定生命周期，不为减少表数并入 Profile。

### 4.2 角色与入驻：8 张

1. `client`
2. `local_partner`
3. `operating_entity`
4. `client_business_profile`
5. `client_business_activity`
6. `client_application`
7. `operating_entity_application`
8. `application_document`

### 4.3 来源与主体分配：10 张

1. `client_entity_affiliation`
2. `client_entity_assignment`
3. `client_entity_assignment_ver`
4. `entity_source_relation`
5. `entity_source_ownership_path`
6. `entity_source_path_edge`
7. `entity_assignment_policy`
8. `entity_assignment_policy_ver`
9. `assignment_policy_binding`
10. `entity_assignment_rule`

Affiliation、Assignment、Source Relation 和 Policy 表达不同业务事实。Binding 负责选择适用 Policy，Rule 负责在已选 Policy 中确定 Operating Entity；两阶段解析继续保留。

### 4.4 Agreement：6 张

1. `agreement`
2. `agreement_version`
3. `agreement_version_party`
4. `agreement_document_ref`
5. `agreement_acceptance`
6. `agreement_scope_binding`

Acceptance 是合同版本封存后产生的独立法律证据，不能并入已封存的 Version Party。

### 4.5 Consumer Legal：4 张

1. `consumer_legal_document`
2. `consumer_legal_document_version`
3. `seller_disclosure`
4. `seller_disclosure_version`

`consumer_legal_document.document_type` 为闭合值：

```text
CONSUMER_TERMS
REFUND_POLICY
PRIVACY_NOTICE
```

三类文档仍独立解析并由 CCT-I12/I13 返回三个显式结果，不因共表而混淆语义。Seller Disclosure 保存结构化获批公开字段并具有独立审批边界，继续单独建模。

Wire 兼容规则：CCT-I12/I13 继续分别输出 `consumer_terms_version_id`、`refund_policy_version_id`、`privacy_notice_version_id`，三者按 `document_type` 映射统一物理 `legal_document_version_id`；共表不改变接口字段名。

### 4.6 Client Pricing：4 张

1. `client_pricing_plan`
2. `client_pricing_version`
3. `client_pricing_scope`
4. `client_fee_component`

### 4.7 Partner Share：4 张

1. `partner_share_plan`
2. `partner_share_version`
3. `partner_share_scope`
4. `partner_share_component`

Pricing 与 Share 的 Owner、合同类型、计算顺序和 Finance 消费语义不同，禁止泛化成一套商业规则表。

### 4.8 Eligibility：4 张

1. `client_eligibility_evaluation`
2. `eligibility_dimension_evaluation`
3. `eligibility_source_evaluation`
4. `eligibility_source_fact`

### 4.9 通用历史：2 张

1. `business_change_record`
2. `business_change_version`

### 4.10 可靠执行与幂等：2 张

1. `compensation_event`
2. `command_idempotency`

### 4.11 Retention：4 张

1. `retention_policy_projection`
2. `retention_resource_control`
3. `retention_hold_projection`
4. `retention_action_log`

## 5. 表级合并设计

### 5.1 Client Application

`client_onboarding` 与 `client_profile_change` 合并为 `client_application`。

核心字段：

```text
application_id
application_no
application_type
client_id
base_party_profile_id
target_party_profile_id
base_business_profile_id
target_business_profile_id
status
active_slot
due_diligence_case_id
qualification_decision_id
qualification_decision_version
qualification_type
submitted_time
submission_hash_schema_version
submission_content_hash
create_time
update_time
```

条件规则：

- `application_no` 唯一；`application_type IN (ONBOARDING,PROFILE_CHANGE)`；`status IN (DRAFT,SUBMITTED,UNDER_REVIEW,APPROVED,REJECTED,WITHDRAWN)`；
- `application_type=ONBOARDING`：Base 两字段为空，Target Party/Business Profile 均非空；
- `application_type=PROFILE_CHANGE`：Base 与 Target 两组均非空；未变化的一组允许 `target=base`，但两组中至少一组必须变化；
- Onboarding 必须绑定 `PENDING` Client；Profile Change 必须绑定 `ACTIVE` Client；四个 Profile FK 均必须属于该 Client 对应 Party/Business Profile；
- Profile Change 提交时 Base 必须等于 Client 当时的 ACTIVE Profile；提交事务完成后，发生变化的 Target 必须为 `FROZEN`；
- `(client_id, active_slot)` 唯一，`DRAFT/SUBMITTED/UNDER_REVIEW` 占用 `1`，终态为 `NULL`。

Risk Tuple 规则：

- Client Application 固定 `role_type=CLIENT`、`role_id=client_id`；Individual Client 的 `qualification_type=INDIVIDUAL_KYC`，Organization Client 为 `ORGANIZATION_KYB`；
- `qualification_type` 在提交时由 Client Party Type 确定并冻结，不接受调用方任意指定；
- `due_diligence_case_id`、`qualification_decision_id`、`qualification_decision_version` 由 Risk 幂等返回后回填，Decision ID/Version 必须成组出现；
- 申请进入 `APPROVED/REJECTED` 前，Case、Decision、Version、Role 和 Qualification Type 必须组成同一次 Risk 决策的完整匹配 Tuple；`WITHDRAWN` 不要求 Decision。

`submission_content_hash` 的版本化 Canonical 输入固定覆盖：Application Type、Client ID、Qualification Type、Base/Target Profile ID 及各自 Content Hash，以及按 `subject_party_id/requirement_code/document_type/file_id` 排序后的全部材料 File ID/Content Hash。Case ID 和 Decision ID 是提交后的外部执行结果，不进入 Submission Hash。

### 5.2 Application Document

`client_onboarding_doc` 与 `operating_entity_app_doc` 合并为 `application_document`，并直接覆盖 Profile Change 材料，不再新增第三张材料表。

核心字段：

```text
document_id
client_application_id
operating_entity_application_id
subject_party_id
requirement_code
document_type
file_id
content_hash
create_time
update_time
```

约束：

- `client_application_id` 与 `operating_entity_application_id` 恰好一个非空；
- 精确 CHECK 为 `(client_application_id IS NOT NULL) <> (operating_entity_application_id IS NOT NULL)`；
- 两个父字段都建立 `RESTRICT` 本库 FK，不使用多态 `application_type + application_id`；`subject_party_id` 同样建立到 `party` 的本库 FK；
- `subject_party_id/requirement_code/document_type/file_id/content_hash` 全部非空；
- `uk_app_doc_client(client_application_id,subject_party_id,requirement_code,document_type,file_id)`；
- `uk_app_doc_entity(operating_entity_application_id,subject_party_id,requirement_code,document_type,file_id)`；
- 两个唯一键依赖父 FK XOR：目标父列非空的一把索引负责去重，另一把索引的 NULL 不参与该父类型的唯一性；
- `subject_party_id` 必须是申请主 Party 或其审核涉及的 UBO/Director/Control 图节点；
- `file_id + content_hash` 精确冻结 Platform File，删除重复 `document_version`；
- 同一 Requirement/Document Type 允许按 Risk Requirement Cardinality 提交多个不同文件，完全相同的 File 不得重复；单文件 Requirement 的“最多一份”由提交入口按冻结 Policy 校验；
- DRAFT 替换材料使用删除旧行、增加新行并写入同一聚合变更历史，不原地覆盖 File Tuple；父申请提交后材料只读。

### 5.3 Consumer Legal

三套同构 Root 合并为 `consumer_legal_document`。Root 只保存稳定 Scope 身份并作为 Scope 级锁定对象，不保存重复 Current Pointer 或乐观锁：

```text
legal_document_id
document_type
document_code
operating_entity_id
scope_type
client_id
scope_key
country_code
locale
create_time
update_time
```

Scope 与唯一约束：

- `(document_type, document_code)`；
- `(document_type, operating_entity_id, scope_key, country_code, locale)`。

- `scope_type IN (ENTITY_DEFAULT, CLIENT_OVERRIDE)`；
- `ENTITY_DEFAULT`：`client_id IS NULL AND scope_key='*'`；
- `CLIENT_OVERRIDE`：`client_id IS NOT NULL AND scope_key=CONCAT('CLIENT:',client_id)`；
- `scope_key` 是根据 Scope Type/Client 生成并校验的物理规范键，不接受接口直接输入；
- CCT-I12/I13 对每种 `document_type` 分别先查同 OE/Country/Locale/As-Of 下唯一有效的 Client Override；没有有效 Override 才查唯一 Entity Default；任一优先级出现并列即 Fail Closed；
- Seller Disclosure 不参与 Override，按 `(operating_entity_id,country_code,locale,as_of)` 唯一解析，缺失或并列同样 Fail Closed。

`consumer_legal_document_version` 完整业务字段为：

```text
legal_document_version_id
legal_document_id
version_no
content_file_id
content_hash
status
active_slot
effective_from
effective_to
create_time
update_time
```

状态闭合为 `DRAFT/APPROVED/SCHEDULED/ACTIVE/SUPERSEDED/EXPIRED/WITHDRAWN`。只有 `ACTIVE` 保存 `active_slot=1`，其余状态必须为 `NULL`；`uk_legal_doc_ver_no(legal_document_id,version_no)` 与 `uk_legal_doc_active(legal_document_id,active_slot)` 分别唯一。ACTIVE 后 File/Hash/有效期不可修改，同一 Root 已批准或生效的有效区间不得重叠。

`seller_disclosure_version` 增加同义 `active_slot`，使用相同状态集合与 `status ↔ active_slot` CHECK，并建立 `(disclosure_id,version_no)`、`(disclosure_id,active_slot)` 唯一键。其公开字段、审批人和 Approval Trigger 继续保留，不能并入 Consumer Legal 文件版本。

### 5.4 Eligibility Evaluation

删除以下 Current Projection：

- `client_eligibility`
- `client_eligibility_dimension`
- `eligibility_dimension_source`

原 History 三表改名并收敛为不可变 Evaluation 三表，完整语义如下：

| 表 | 关键字段 | 关键约束 |
|---|---|---|
| `client_eligibility_evaluation` | `evaluation_id`, `client_id`, `evaluation_version`, `overall_status`, `active_slot`, `reason_snapshot`, `reason_summary_hash`, `dimension_set_hash`, `source_set_hash`, `evaluated_time`, `valid_until`, `create_time`, `update_time` | `(client_id,evaluation_version)`、`(client_id,active_slot)` 唯一；最新完成结果 slot=1；业务结果只追加，只有 Slot/Update Time 可切换 |
| `eligibility_dimension_evaluation` | `dimension_evaluation_id`, `evaluation_id`, `dimension_code`, `dimension_status`, `reason_code`, `source_set_hash`, `evaluated_time`, `create_time`, `update_time` | `(evaluation_id,dimension_code)` 唯一；每次 Evaluation 固定七行 |
| `eligibility_source_evaluation` | `source_evaluation_id`, `dimension_evaluation_id`, `source_fact_row_id`, `applicability_type`, `applicability_id`, `contribution_status`, `reason_code`, `source_as_of_time`, `create_time`, `update_time` | `(dimension_evaluation_id,source_fact_row_id,applicability_type,applicability_id)` 唯一；必须冻结真实使用的来源和本地适用依据 |
| `eligibility_source_fact` | `source_fact_row_id`, `source_system`, `source_fact_id`, `source_version`, `source_event_key`, `source_payload_hash`, `fact_type`, `subject_type`, `subject_id`, `as_of_time`, `received_time`, `create_time`, `update_time` | `(source_system,source_fact_id,source_version)`、`(source_system,source_event_key)` 唯一；事实只追加、来源版本不得回退 |

固定维度仍为：

```text
CLIENT_STATUS
IDENTITY_COMPLIANCE
BUSINESS_ELIGIBILITY
AGREEMENT
OPERATING_ENTITY
ROUTING_COVERAGE
INTEGRATION_READINESS
```

状态与汇总规则：

- `overall_status IN (READY,NOT_READY,RESTRICTED)`；`dimension_status IN (PASS,FAIL,UNKNOWN)`；
- 每次完成 Evaluation 必须恰有上述七个 Dimension，并保存每个 Dimension 实际使用的 Source Fact 及本库 Role/Affiliation/Assignment/Source Relation 适用依据；
- 任一必需 Dimension 为 `FAIL` 或 `UNKNOWN` 时整体不得为 `READY`；限制型事实按确定性策略汇总为 `RESTRICTED`，其余失败关闭为 `NOT_READY`；
- Dimension/Source 集合都完整写入并成功重算 Hash 后，Evaluation 才能成为当前完成结果；
- `valid_until` 取本次必需来源与适用资格中最早的明确失效时间；均无明确失效时间时可为 `NULL`，但来源状态变化仍必须触发新 Evaluation；
- 最新完成的 `READY/NOT_READY/RESTRICTED` Evaluation 均占用 `active_slot=1`。这里的 Slot 是当前完成投影指针，不是名为 ACTIVE 的业务状态；
- `eligibility_source_fact` 继续负责跨 Client Fan-out、Source Event 幂等、Payload Hash 冲突检测和来源版本单调；`INTEGRATION_READINESS` 只接受 Platform 的版本化技术事实。
- 三张不可变明细/事实表的 `update_time` 在 INSERT 时等于 `create_time`，之后不得更新；保留该字段只为遵守全库审计字段规则，不构成覆盖许可。

接口和 As-Of 规则：

- CCT-I10 继续返回 `eligibility_id + eligibility_history_id + evaluation_version + eligibility_status + reason_summary_hash + dimension_set_hash + source_set_hash + evaluated_time + valid_until`；其中 `eligibility_id` 映射稳定 `client_id`，`eligibility_history_id` 映射不可变 `evaluation_id`，`eligibility_status` 映射表内 `overall_status`；
- CCT-I13 在同一只读一致性事务中，从 `evaluated_time <= context_as_of_time` 且 `valid_until IS NULL OR context_as_of_time < valid_until` 的完成结果里选择最大 `evaluation_version`；版本唯一键保证结果唯一，不得拿晚于 As-Of 的当前 Slot 结果代替；只有 `READY` 可进入交易 Bundle；
- CCT-I13 的 Eligibility 继续输出 `eligibility_id/eligibility_history_id/evaluation_version/eligibility_status/reason_summary_hash/dimension_set_hash/source_set_hash`，同一次结果全部进入 `commercial_context_hash`；
- Commerce/Risk 继续保存既有 `cct_eligibility_id/cct_eligibility_history_id` Wire/快照字段；字段说明必须明确前者是 CCT 返回的稳定 Client ID 映射，后者是不可变 Evaluation ID，并继续与 Evaluation Version、Status 和三个 Hash 成组保存；
- 不得把 Evaluation ID 冒充一个不存在的稳定 Eligibility Root ID。

## 6. Operating Entity 申请闭环

首次创建 Operating Entity Application 时，在同一事务创建 `PENDING` Operating Entity，申请直接保存非空 `operating_entity_id`。同一 Party 的拒绝/撤回重提必须复用原 `PENDING` Role 并新建 Application，不能重复创建 Role。删除申请中的：

- `organization_party_id`
- `operating_country`
- `approved_operating_entity_id`

这些字段分别从 `operating_entity.party_id/operating_country/operating_entity_id` 获得。申请继续保存准确目标 `party_profile_id`、来源 XOR、Risk Tuple、Submission Hash 和 `approved_source_relation_id`。

目标字段完整为：

```text
application_id
application_no
operating_entity_id
party_profile_id
source_type
source_client_id
source_partner_id
client_source_basis
ownership_path_id
status
active_slot
due_diligence_case_id
qualification_decision_id
qualification_decision_version
qualification_type
submitted_time
submission_hash_schema_version
submission_content_hash
approved_source_relation_id
create_time
update_time
```

`application_no` 唯一，`(operating_entity_id,active_slot)` 唯一；Status/Slot 矩阵与 Client Application 相同。Source Type 仅为 `PLATFORM/CLIENT/PARTNER`，Owner XOR 与 CLIENT Basis/Path 规则和 Source Relation 完全一致。

OE Application 的 Risk 字段完整保存：

```text
due_diligence_case_id
qualification_decision_id
qualification_decision_version
qualification_type
```

其 Risk 映射固定为 `role_type=OPERATING_ENTITY`、`role_id=operating_entity_id`、`qualification_type=ENTITY_UNDERWRITING`，提交后不可修改；Case/Decision 回填规则与 Client Application 相同。

状态职责分离：

- Application：`DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED/WITHDRAWN`；
- Operating Entity：`PENDING → ACTIVE → SUSPENDED/CLOSED`；
- 审核拒绝或撤回只终止本次 Application，OE 保持 `PENDING`，允许通过新申请重提；明确放弃后由 Operations 关闭；
- `operating_entity.party_id` 唯一；`CLOSED` Role 不得重开，也不得为同一 Party 新建第二个 OE Role；如法律主体发生变化，应新建 Party；
- 批准时创建唯一 ACTIVE Source Relation 并把 OE 激活；
- `(operating_entity_id, active_slot)` 保证同一 OE 最多一个进行中申请。
- `approved_source_relation_id` 仅 `APPROVED` 时非空，其他状态必须为空。

该设计使 Risk Qualification 和 CLIENT Ownership Path 在审核期即可引用稳定 `operating_entity_id`。

OE Submission Hash 覆盖 OE/Party/Profile ID 与 Content Hash、Qualification Type、Source Type/Owner/Basis、Ownership Path ID/Path Hash，以及按稳定键排序的 Application Document；Risk 执行后产生的 Case/Decision ID 不进入 Hash。

## 7. Profile 封存语义

Party Profile 和 Client Business Profile 使用：

```text
DRAFT → FROZEN → ACTIVE → SUPERSEDED
```

- Client Onboarding 提交时冻结 Target Party Profile 和 Target Business Profile；
- Client Profile Change 提交时冻结所有与 Base 不同的 Target Profile；
- OE Application 提交时冻结该 OE Party 的 Target Party Profile；
- 三类申请只能引用申请 Role 对应 Party 的 Profile；审批只能激活已冻结且 Content Hash 与 Submission Package 一致的目标资料；
- `FROZEN/ACTIVE/SUPERSEDED` 业务内容不可修改；
- 未变化的 Profile Change Target 可以继续引用同一 ACTIVE Base Profile；
- 被拒绝申请引用的 FROZEN Profile 仍是提交包证据，可被后续新申请复用，但不得原地修改；
- 批准时原子释放旧 ACTIVE Slot 并激活新 Profile。

## 8. 版本与并发模型

### 8.1 必须保留的非锁版本

- `version_no`：不可变业务版本坐标；
- `source_version`：外部来源事实版本；
- `evaluation_version`：Eligibility 评估版本；
- `vault_record_version`：Vault 记录版本；
- `hash_schema_version/schema_version`：Hash 或 Payload Schema 版本。

这些字段不得被当作通用乐观锁删除。

### 8.2 删除 Current Pointer

删除原模型全部 9 个 `current_version_id`：Assignment、Assignment Policy、Agreement、三类 Consumer Legal、Seller Disclosure、Pricing Plan 和 Share Plan。合并后当前生效版本统一通过 `(root_id,active_slot=1)` 唯一查询；最新草稿或最新业务版本通过 `(root_id,status,version_no DESC)` 索引查询，不得把审计 `current_version` 当作业务 `version_no`。

目标 Active Slot 矩阵：

| Version 表 | 唯一键 | Slot 规则 |
|---|---|---|
| `client_entity_assignment_ver` | `(assignment_id,active_slot)` | 仅 `ACTIVE` 为 1 |
| `entity_assignment_policy_ver` | `(policy_id,active_slot)` | 仅 `ACTIVE` 为 1 |
| `agreement_version` | `(agreement_id,active_slot)` | 仅 `ACTIVE` 为 1 |
| `consumer_legal_document_version` | `(legal_document_id,active_slot)` | 仅 `ACTIVE` 为 1 |
| `seller_disclosure_version` | `(disclosure_id,active_slot)` | 仅 `ACTIVE` 为 1 |
| `client_pricing_version` | `(pricing_plan_id,active_slot)` | 仅 `ACTIVE` 为 1 |
| `partner_share_version` | `(share_plan_id,active_slot)` | 仅 `ACTIVE` 为 1 |

其中 Agreement、Consumer Legal、Seller Disclosure 是本次新增 Slot 的三类 Version。每张表都必须存在 `CHECK ((status='ACTIVE' AND active_slot=1) OR (status<>'ACTIVE' AND active_slot IS NULL))`、有效期合法约束和 `(root_id,status,effective_from,effective_to)` 解析索引；Repository 还要在 Root 锁内拒绝已批准/生效区间重叠。

### 8.3 统一业务并发令牌

普通受审计业务聚合不再各自保存机械 `version`。`business_change_record` 中聚合根记录的 `current_version` 同时承担：

1. 聚合审计版本坐标；
2. 外部编辑命令的 `expected_change_version` 并发令牌。

该机制必须遵守以下原子写协议，不能只在文字上把字段重命名：

1. 为每种受审计行维护稳定 `record_type` Catalog，并明确所属 `aggregate_root_type`；业务 Repository 是唯一写入口；
2. 创建聚合时，在同一事务插入结构化 Root/Child、Root `business_change_record(current_version=1)`、各 Child Record、Root/Child 的 Version 1 `CREATE` Snapshot 和本地补偿事件；
3. 修改聚合时，外部命令携带 `expected_change_version`，固定 `next_version=expected_change_version+1`，先执行 Root CAS：

```sql
UPDATE business_change_record
SET current_version = :next_version, update_time = :now
WHERE record_type = :root_type
  AND record_id = :root_id
  AND current_version = :expected_version;
```

4. Root CAS 影响行数必须为 1；随后以期望旧业务状态更新结构化行、切换 Slot，并为 Root 和每个变化 Child 追加 `version_no=:next_version` 的不可变 Snapshot；
5. 变化 Child 的 Record 使用各自真实 `child_expected_version` CAS 到本次 Root `next_version`，允许中间没有变化的聚合版本自然跳号，禁止假设 Child 旧版本一定等于 `next_version-1`；
6. 新增 Child 在本次 Root Version 创建 Record 和 `CREATE` Snapshot；删除 Child 先追加包含稳定身份和删除前安全字段的 `DELETE` Snapshot，再删除结构化行，Record 永久保留；未变化 Child 不写空洞 Snapshot；
7. 同一事务最后写入以 Root `next_version` 为 `source_version` 的 `compensation_event`。任一 CAS、结构化更新、Snapshot 或事件登记失败全部回滚；
8. 定时激活、过期、撤销和系统回调只是在锁内读取期望版本的 System Command，仍必须执行同一 Root CAS/History/Event 协议，不得绕过审计版本。

一个本地事务可以原子修改多个聚合，但不能共享伪造的聚合版本。受影响 Root 按 `(aggregate_root_type,aggregate_root_id)` 固定顺序逐个锁定和 CAS；每个 Root 独立计算自己的 `next_version`、写 Root/Changed Child Snapshot，所有 Snapshot 通过相同 `_meta.triggerId` 关联。需要发布的每个 Event 以它所代表的 Root Version 作为 `source_version`，不能借用另一个 Root 的版本。任一 Root CAS 冲突时整个本地事务回滚。Application Document 只属于 Application 聚合；Party Profile 属于 Party 聚合，Client Business Profile 属于 Client 聚合，禁止为了提交申请而修改它们既有的 Aggregate/Parent Tuple。

`business_change_record` 的多态 Record Tuple 不建立伪造 FK，因此 Catalog、聚合归属和原子写协议必须由封闭 Repository、数据库写权限及未来 Testcontainers 并发测试共同保证。该决策替代原行级历史设计中“`current_version` 不代替结构化根 `version`”的旧规则，实施时必须同步更新权威历史文档。

只有真正高并发且不进入通用业务历史的技术记录保留独立锁，并明确改名：

- `compensation_event.lock_version`；
- `retention_resource_control.lock_version`。

### 8.4 冻结 Revision

下游需要冻结的物理 `affiliation_version/source_relation_version/binding_version` 改名为 `*_revision_no`，取值为对应业务记录在 `business_change_record.current_version` 中的准确修订坐标。它们是业务快照引用，不是被引用表上的乐观锁。既有 CCT-I01 至 CCT-I13 Wire 字段可继续使用 `*_version` 兼容名称，但字段说明必须明确映射 Revision，内部物理字段不得继续混称乐观锁 Version。

## 9. 可推导字段与 Hash 精简

### 9.1 来源与路径

本次变化后的完整字段集合：

```text
entity_source_relation:
source_relation_id, operating_entity_id, source_type, source_client_id,
source_partner_id, source_owner_key, client_source_basis, ownership_path_id,
status, active_source_slot, effective_from, effective_to, create_time, update_time

entity_source_ownership_path:
ownership_path_id, source_client_id, operating_entity_id, hash_schema_version,
path_hash, effective_from, effective_to, status, create_time, update_time

entity_source_path_edge:
path_edge_id, ownership_path_id, edge_no, relation_version_id,
relation_content_hash, create_time, update_time
```

- `entity_source_relation.source_owner_key` 是不可写的 STORED Generated Column，逻辑表达式固定为：

```sql
CASE source_type
  WHEN 'PLATFORM' THEN
    CASE WHEN source_client_id IS NULL AND source_partner_id IS NULL
         THEN 'PLATFORM' END
  WHEN 'CLIENT' THEN
    CASE WHEN source_client_id IS NOT NULL AND source_partner_id IS NULL
         THEN CONCAT('CLIENT:', CAST(source_client_id AS CHAR)) END
  WHEN 'PARTNER' THEN
    CASE WHEN source_client_id IS NULL AND source_partner_id IS NOT NULL
         THEN CONCAT('PARTNER:', CAST(source_partner_id AS CHAR)) END
END
```

ID 使用无前导零十进制 BIGINT 文本；表达式得到 `NULL` 时由 CHECK 拒绝。PDManer 阶段必须把生成语义写入字段说明和契约，未来 Migration 再验证实际 MySQL Generated Column；
- `source_type=PLATFORM` 时两个 Owner ID 均空；`CLIENT` 仅 `source_client_id` 非空；`PARTNER` 仅 `source_partner_id` 非空。CLIENT 还必须满足 `SAME_PARTY/OWNERSHIP_PATH` 与 Path 的既定矩阵；
- `source_type/source_client_id/source_partner_id/client_source_basis/ownership_path_id` 创建后不可修改；法律来源变化必须 END 旧 Source Relation 并 CREATE 新 Relation，不能让同一 `source_relation_id` 通过更新 Generated Key 改变身份；
- `entity_source_ownership_path` 删除 `source_client_party_id`、`operating_entity_party_id`、`path_length`、`path_version`；
- `entity_source_path_edge` 仅保留业务主键、Path ID、Edge No、Relation Version ID 和 Relation Content Hash；
- Path 父行继续保存创建后不可修改的 `source_client_id`、`operating_entity_id`、Hash Schema、Path Hash、有效期和状态；端点分别从 Client/OE Role 的不可变 `party_id` 派生；
- `party_relation` 表达“`related_party_id` 拥有/控制 `organization_party_id`”；Ownership Path 的自然遍历方向固定为 `related_party_id → organization_party_id`，从 Source Client Party 逐边走到 Operating Entity Party；From/To Party、Relation Type、Ownership Percentage、有效期从引用的不可变 Relation Version 读取；未来若需要反向遍历，必须显式新增 Direction 设计，不能暗中反转；
- 每条 Edge 的 `relation_content_hash` 必须等于引用 Relation Version 的 Content Hash，`edge_no` 从 1 连续递增，相邻边首尾连续，第一条起点和最后一条终点必须等于派生的 Path 端点；
- Relation Version 一旦 ACTIVE 或被 Path 引用，`organization_party_id/related_party_id/relation_type/ownership_percentage/effective_from/effective_to` 均不可修改。`ACTIVE → ENDED` 只推进不进入 Content Hash 的生命周期并由 BCR 记录结束时点；若业务有效区间必须变化，则追加新的 Relation Version，绝不改写历史边引用的那一行；
- 该调整同时消除当前 `relation_effective_to` 非空而 ACTIVE Relation 可开放结束时间的物理矛盾。

### 9.2 标记字段

- 删除 `client_business_activity.primary_flag`，`primary_slot=1` 即表示主要活动；
- 删除 `entity_assignment_rule.fallback_flag`，`fallback_slot=1` 即表示兜底规则。

### 9.3 子行 Canonical Hash

删除以下只被父 Hash 二次聚合、没有独立消费者的 `canonical_hash`：

- Client Business Activity；
- Source Path Edge；
- Assignment Policy Binding/Rule；
- Agreement Version Party/Document；
- Client Pricing Scope；
- Partner Share Scope。

`client_fee_component.canonical_hash` 与 `partner_share_component.canonical_hash` 必须保留，因为 CCT-I13 会逐项返回，Commerce/Finance 会按组件冻结和校验。父级封存时，对无独立 Hash 的子行直接按稳定业务键排序并规范化业务字段；对 Component 则聚合其 Canonical Hash。继续保留下游会独立冻结、匹配或验证的 `content_hash`、`scope_hash`、文件 Hash、Relation Hash 和 Eligibility Set Hash。

### 9.4 Submission 与 Component Hash Schema

四类 Hash 统一使用 UTF-8 RFC 8785/JCS 后的 SHA-256，输出小写 64 位 Hex。`BIGINT` 一律编码为无前导零十进制字符串；`DECIMAL` 使用去无意义尾零、禁止指数的十进制字符串，零固定为 `"0"`；时间若未来进入新 Schema，固定为六位微秒 UTC 字符串；固定属性不得因空值而省略，使用 JSON `null`。算法或属性变化必须提升 `hash_schema_version` 和 Domain Tag，旧版本永不重算覆盖。

Client Application Submission Schema Version 1 的精确输入对象为：

```json
{
  "domain": "CODE-MOR:CCT:CLIENT_APPLICATION_SUBMISSION:1",
  "hashSchemaVersion": 1,
  "application": {
    "applicationId": "<BIGINT>",
    "applicationType": "<ONBOARDING|PROFILE_CHANGE>",
    "clientId": "<BIGINT>",
    "clientPartyId": "<BIGINT>",
    "clientPartyType": "<INDIVIDUAL|ORGANIZATION>",
    "qualificationType": "<INDIVIDUAL_KYC|ORGANIZATION_KYB>",
    "basePartyProfile": null,
    "targetPartyProfile": {"profileId": "<BIGINT>", "contentHash": "<64-hex>"},
    "baseBusinessProfile": null,
    "targetBusinessProfile": {"businessProfileId": "<BIGINT>", "contentHash": "<64-hex>"}
  },
  "documents": [
    {
      "subjectPartyId": "<BIGINT>",
      "requirementCode": "<STRING>",
      "documentType": "<STRING>",
      "fileId": "<BIGINT>",
      "contentHash": "<64-hex>"
    }
  ]
}
```

Profile Change 的四个 Profile 对象均非空；Onboarding 的两个 Base 固定为 `null`。Document 数组按 `subjectPartyId/requirementCode/documentType/fileId` 的 UTF-8 字节序稳定排序。

OE Application Submission Schema Version 1 的精确输入对象为：

```json
{
  "domain": "CODE-MOR:CCT:OPERATING_ENTITY_APPLICATION_SUBMISSION:1",
  "hashSchemaVersion": 1,
  "application": {
    "applicationId": "<BIGINT>",
    "operatingEntityId": "<BIGINT>",
    "operatingEntityPartyId": "<BIGINT>",
    "operatingCountry": "<ISO-3166-1-alpha-2>",
    "partyProfile": {"profileId": "<BIGINT>", "contentHash": "<64-hex>"},
    "qualificationType": "ENTITY_UNDERWRITING",
    "source": {
      "sourceType": "<PLATFORM|CLIENT|PARTNER>",
      "sourceClientId": null,
      "sourcePartnerId": null,
      "clientSourceBasis": null,
      "ownershipPath": null
    }
  },
  "documents": [
    {
      "subjectPartyId": "<BIGINT>",
      "requirementCode": "<STRING>",
      "documentType": "<STRING>",
      "fileId": "<BIGINT>",
      "contentHash": "<64-hex>"
    }
  ]
}
```

Source 对象始终保留全部固定属性并按 XOR 填字符串或 `null`；`OWNERSHIP_PATH` 时 `ownershipPath={"ownershipPathId":"<BIGINT>","pathHash":"<64-hex>"}`。Document 排序与 Client Application 相同。

Client Fee 与 Partner Share Component 分别使用 `CODE-MOR:CCT:CLIENT_FEE_COMPONENT:1` 和 `CODE-MOR:CCT:PARTNER_SHARE_COMPONENT:1`。二者的 `component` 对象属性固定为 `componentCode/componentType/triggerCode/calculationBase/rate/fixedAmount/currency/minAmount/maxAmount`；所有可空属性显式为 `null`，Decimal 使用上述字符串编码。Component ID、Scope ID、状态、时间和 Active Slot 不进入内容 Hash。父 Content Hash 按 `componentCode` 排序聚合这些 Canonical Hash，并继续使用各自独立的父级 Domain Tag。

## 10. 状态转换与事务边界

### 10.1 Application Submit

本地提交事务必须：

1. 校验申请状态、Active Slot、Profile 归属和材料完整性；
2. 按固定 Root 顺序锁定 Application、目标 Party、涉及的 Client/OE 以及全部材料；
3. 计算待提交包的 Submission Hash；
4. 按第 8.3 节分别 CAS 推进 Application Root、Party Root，以及发生 Business Profile 冻结时的 Client Root；
5. 按第 7 节冻结对应 Target Profile，写入 Submission Hash 并把申请转为 `SUBMITTED`；
6. Application 与 Application Document 使用 Application Version；Party Profile 使用 Party Version；Client Business Profile 使用 Client Version；分别追加对应 Root/Changed Child Snapshot，并用同一 Trigger ID 关联；
7. 登记唯一 `LOCAL_HANDLER` Compensation Event，请求 Risk 以 `application_id + submission_content_hash` 幂等创建 Case。

任一步失败整体回滚。

提交事务内禁止调用 Risk HTTP。事务提交后 Handler 使用冻结的 Role/Qualification/Profile/Document Tuple 调用 Risk；成功后在第二个 CCT 本地事务校验同一 Submission Hash，回填 `due_diligence_case_id`，把 `SUBMITTED` 推进为 `UNDER_REVIEW`，并写 History/Event 完成状态。重复回调必须得到同一 Case ID，不同 Case ID 冲突必须拒绝并告警。这样没有跨库事务，也不会出现本地回滚但 Risk Case 已创建的悬挂状态。

### 10.2 Application Decision

批准或拒绝前必须校验 `due_diligence_case_id + qualification_decision_id + qualification_decision_version + qualification_type` 与准确 Role、Case 和不可变 Submission Hash 一致。Client Application 的 Role/Qualification 映射按 Party Type，OE Application 固定为 `OPERATING_ENTITY/ENTITY_UNDERWRITING`。

批准 Client Onboarding 时原子激活 Client 与目标 Profile；批准 Client Profile Change 时保持 Client ACTIVE 并原子切换目标 Profile；批准 OE Application 时原子激活 OE/目标 Party Profile 并创建 Source Relation。一次批准可以涉及 Application、Party、Client/OE 与 Source Relation 等多个 Root，但每个 Root 必须独立 CAS、使用自己的下一版本和 History Tree；只有 Application Document 归属 Application Child。所有 Root 更新、副作用历史和对应 Compensation 仍在同一本地事务提交，任一冲突全部回滚。

### 10.3 Eligibility Recalculation

1. 锁定 Client 和旧 `active_slot=1` 当前完成 Evaluation；
2. 校验 Source Event Key/Hash 和严格递增 Source Version；
3. 追加包含七个固定 Dimension 及其 Source/Applicability 的完整 Evaluation；
4. 校验 Dimension/Source Set Hash；
5. 释放旧 Slot并占用新 Slot；
6. 登记 `ClientEligibilityChanged` 补偿事件后提交。

处理结果分三类：

- Event/Hash/Version 在接纳前不合法：整个事务回滚、不 Ack，旧 Evaluation 仍只代表上一次已接纳来源集合；
- 外部事实缺失、过期、拒绝或限制：写入完整 `NOT_READY/RESTRICTED` Evaluation 并 Fail Closed；
- 新 Source Fact 已通过校验，但复杂计算出现可归类的技术错误：不得保留旧 `READY` 冒充新事实结果；必须写入完整七维 Fail-Closed Evaluation，将受影响维度置为 `UNKNOWN`、整体置为 `NOT_READY`、保存稳定失败原因和完整 Hash，随后提交 Source Fact。修复或重试时再追加下一 Evaluation，禁止修改失败版本。

无法安全归类的程序错误、依赖故障或数据库提交失败必须整体回滚，Source Fact、Evaluation 和 Slot 均不视为已接纳，消息不得 Ack。任何路径都不得留下只有 Header、缺 Dimension/Source 或 Hash 不可重算的半成品 Evaluation。

### 10.4 Version Activation

Agreement、Consumer Legal、Seller Disclosure、Assignment Policy、Pricing 和 Share 激活时锁定稳定 Root 与候选 Version，校验内容 Hash、有效区间、Scope 唯一和期望旧状态，按第 8.3 节推进 Root BCR 并原子切换 Active Slot。重复或并列解析必须拒绝，不能按第一条记录兜底。

`agreement_version` 的目标字段为：

```text
agreement_version_id
agreement_id
version_no
status
active_slot
effective_from
effective_to
hash_schema_version
content_hash
supersedes_version_id
create_time
update_time
```

它增加 `(agreement_id,active_slot)` 唯一键并保留 `(agreement_id,version_no)`。`agreement_document_ref` 删除 `document_version` 后使用 `uk_agreement_doc_file(agreement_version_id,document_type,file_id)`，允许同类型多份 Exhibit，但禁止同一文件重复。Agreement Version 进入 `PENDING_SIGNATURE` 后 Document Tuple 及 Party 集合不可修改。

## 11. 错误处理

- `expected_change_version` 不匹配：返回并发冲突，不覆盖较新结果；
- Submission/Content/Set Hash 无法重算：拒绝状态转换；
- Risk Decision 与申请 Role/Case/Type 不一致：拒绝审批；
- Risk Case 创建超时或响应丢失：保留 `SUBMITTED`，由本地 Compensation 按相同 Application ID/Submission Hash 重试；同键返回不同 Case ID 视为冲突；
- Active Slot、Scope 唯一或有效期冲突：事务回滚并返回明确业务冲突；
- 同 Source Event Key 不同 Payload Hash、来源版本回退：拒绝并告警；
- 不允许出现申请已批准但 Profile、Role、Source Relation、历史或补偿登记只完成部分的状态。

## 12. 验证策略

本次实施先更新模型契约测试，使旧 66 表模型按新规则明确失败，再更新逻辑文档、生成器和元数据，并只通过生成器重建 PDManer，完成了 RED → GREEN 闭环。

实施前的生成器同时硬编码表数、唯一键、关系和图组。实施后，变化表的完整字段/`uk_*`/关系/状态矩阵均落入权威逻辑目录，Catalog、中文元数据与生成 Entity Key Set 双向闭合；关系、Diagram Group 和数量断言从最终配置与 Catalog 校验，不再维护第二个表数权威。

自动门禁至少验证：

- 精确 57 张表及上述目录；
- 不存在错误来源枚举 `DIRECT`；唯一保留的文字出现是“禁止使用 DIRECT”的规范说明；
- 不存在遗留 `current_version_id`、无语义业务 `version`、Application/Agreement `document_version` 和已删除 Canonical Hash；
- 只有 `compensation_event.lock_version` 与 `retention_resource_control.lock_version` 是独立技术锁；
- Application 两把 Parent FK、XOR CHECK、两把精确唯一键、Subject Party FK、Risk Decision Tuple、三类 Profile 冻结规则和 Submission Hash 输入完整；
- 全部七张业务 Version 的 Root/Version No、Active Slot 唯一键、状态矩阵、解析索引与有效区间规则完整；
- Consumer Legal Type/Scope XOR、生成 Scope Key、Override → Default 优先级、Version 唯一和 Seller Disclosure 独立解析完整；
- Eligibility Evaluation 固定七维、`PASS/FAIL/UNKNOWN` 汇总、不可变 Source/Applicability、As-Of、Source Fact 幂等和版本单调保持；
- CCT-I10/I13、Commerce 和 Risk 保持既有 Eligibility 字段名，并验证 `eligibility_id → client_id`、`eligibility_history_id → evaluation_id`、Evaluation Version/Hash 的一致映射；Pricing/Share Component Canonical Hash 仍在 Bundle；
- Ownership Path/Edge 的正向关系、连续 Edge、端点派生、Relation Hash 对应、索引和开放结束时间正确；
- `source_owner_key` 的生成表达式、Owner XOR 和 CLIENT Basis/Path 矩阵完整；
- BCR 文档契约覆盖 Root/Child CREATE、UPDATE、DELETE、System Transition、CAS 失败回滚和 Event 同事务；
- Client/OE Submission 与 Client Fee/Partner Share Component 各保存至少一个固定 JCS 输入/期望 SHA-256 向量；当前 PDManer 阶段用 Node 验证。未来获得 Java 实现授权后，Java 必须复用同一批向量并与 Node 结果一致，不能另建一套测试输入；
- 全部中文表名、字段名、字段说明和值域说明符合规范；
- 本库关系都有左前缀索引且不出现跨库关系；
- 共享 PDManer 模板保持字节级不变；
- `node --test scripts/pdmaner/client-contract-model.test.mjs`、生成器 `--check`、JSON 检查和 `npm --prefix requirements-site test` 全部通过。

自动验证后仍需使用 PDManer 4.9.3 打开模型并执行应用内模型检查。该人工门禁不授权 Migration 或 Java 实现。

最终自动证据为 57 张表、632 个字段、529 个必填字段、103 个可空字段、86 个唯一键、68 个访问索引、89 条本库关系，以及 6 个 Diagram Groups（75 个 Table Cells、89 个 Relation Cells）。模型契约 63/63、需求站 28/28；Structural Projection、Model、Template SHA-256 分别为 `50b1246134e4ea6472a38a5b6af96f02a93b074ba838ba7aa722db4e753320c1`、`dcbd750febc8c91dd55becdcb1406d00e2b0a60be308ab1b26b8f61efe62373d`、`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。

PDManer 阶段能自动验证表、字段、说明、索引和关系形状，但不能证明 MySQL CHECK、Generated Column、跨行有效区间、封闭 Repository 或 BCR CAS 的运行行为。获得数据库实现授权后，还必须增加 Migration 契约和 Testcontainers 自动测试，至少覆盖：两并发命令只有一个 CAS 成功、两 Root 中后一个冲突时前一个及全部业务更新回滚、Child 版本跳号、System Transition 不绕过 History、事务中任一点失败全回滚、Application XOR/唯一键、Active Slot 并发切换、Source Owner 生成式、Path 连续性与 Hash 重算。

## 13. 受影响工件

本次实施实际覆盖：

- `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`；
- `agent-docs/plans/2026-08-20-cct-row-history-design.md`；
- `agent-docs/database/README.md`；
- `agent-docs/database/COMMERCE_DATABASE.md` 与 `agent-docs/database/RISK_COMPLIANCE_DATABASE.md` 中的 Eligibility 字段；
- `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`；
- `scripts/pdmaner/generate-client-contract-model.mjs`；
- `scripts/pdmaner/client-contract-field-metadata.mjs`；
- `scripts/pdmaner/client-contract-model.test.mjs`；
- `client-contract-service/docs/database/client-contract.pdma.json`；
- `requirements-site/content/requirements.ts` 及相关需求回归测试。

生成的 `.pdma.json` 只能通过生成器更新，不得手工修改后绕过契约测试。
