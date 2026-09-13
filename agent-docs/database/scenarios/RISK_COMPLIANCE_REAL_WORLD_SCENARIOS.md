# Risk & Compliance 现实场景与逐表字段映射

最后更新：2026-09-11

本文描述设计场景与逐表映射；现行生成模型统计、验证结果和部署状态只见[当前实现台账](../../CURRENT_IMPLEMENTATION.md)。2026-09-11 的[规则集管理方案](../../../requirements/20260911/operations-console-风控规则集列表优化/technique.md)补充管理根和整体删除；下文运行期、Shadow、审批和激活场景仍是独立设计，不表示本期已实现。

> **2026-09-05 横切迁移提示：** 本文的 Risk 逐表记录已投影当前三表 Protect/Reveal、补偿三字段身份与 4 KiB 可选 Payload，并把关系 Sidecar 保持为应用不变量。这里只能证明 Risk 模型局部对齐；六库 COMP-01 与全局 DB-01 仍按 [`CURRENT_IMPLEMENTATION.md`](../../CURRENT_IMPLEMENTATION.md) 的全局证据维护，其中 DB-01 继续受 Vault 物理 Schema 阻塞。

当前 Manual Payout 契约以 [四检查点对齐实施记录](../../plans/2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan.md) 为准。

## 1. 固定参与方与编号

本文使用同一组稳定示例贯穿尽调、Checkout、Payment Attempt、限额、Settlement Eligibility Control 和争议。金额均为 USD：

| 对象 | 示例值 |
|---|---|
| Organization Party / UBO Party | `100001` / `100002` |
| Client / Operating Entity | `200001` / `210001` |
| CCT Application | `810001`，Submission Version `3` |
| Local Partner / Related Person Party | `730300000000000001` / `730100000000000009` |
| CCT Local Partner Application | `730320000000000001` |
| Order / Order Item / Order Context | `410001` / `410011` / `410021` |
| Payment Intent / Attempt | `510001` / `510011` |
| Channel / MID | `610001` / `610011` |
| Checkout PRIMARY / SHADOW Evaluation | `310001` / `310002` |
| Payment PRIMARY Evaluation | `310010` |
| Payout Creation / Final Approval Evaluation | `310020` / `310021` |
| Payout Execution Creation / External Submission Evaluation | `310022` / `310023` |
| Dispute Liability Evaluation | `310030` |

所有跨系统编号都只是来源系统业务 ID，不建立本库关系。文件正文仍由 Platform Support 保存；DD 只保存 File ID 与 Content Hash，文件替换产生新的 File ID。受限可恢复快照只保存具备明确 Reveal/审计消费者的批准 Vault Token，不机械复制 Vault 元数据 Tuple。

## 2. SCN-RISK-01：组织 KYB、UBO KYC 与资格决定

Client `200001` 提交 Application `810001`。Risk 选择已审核的 Organization KYB Program Version `110011`，建立 Organization Case `120001`；Required UBO `100002` 具有独立 KYC Case `120002`，Organization Case 通过 Dependency 明确引用 UBO 的有效决定。自动规则只给 recommendation，Reviewer 最终形成 Qualification Decision `130001`，CCT 接收 `RoleQualificationApproved`。

Local Partner Application `730320000000000001` 的 Handler 携带 `application_id`、`role_type`、`role_id`、`qualification_type`、`submission_content_hash`。Risk 创建主 Tuple `source_namespace=CCT_LOCAL_PARTNER_APPLICATION, source_application_id=730320000000000001, role_type=LOCAL_PARTNER, role_id=730300000000000001, qualification_type=PARTNER_DUE_DILIGENCE` 和关联人子 Tuple `source_namespace=CCT_LOCAL_PARTNER_APPLICATION, source_application_id=730320000000000001, role_type=RELATED_PERSON, role_id=730100000000000009, qualification_type=RELATED_PERSON_KYC`；CCT 只接收 Local Partner 主 Case/Decision，Risk 管理关联人 KYC 子 Case 及其依赖。

### 2.1 尽调与资格 19 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_dd_program` | `program_id=110001, program_code=ORG_KYB_V1, qualification_type=ORGANIZATION_KYB, status=ACTIVE`；稳定身份可更新状态，类型不可改变。 |
| `t_dd_program_version` | `program_version_id=110011, program_id=110001, version_no=1, review_frequency_code=ANNUAL, status=ACTIVE, active_slot=1, approved_by=900001, approved_time=2026-08-25T12:00Z`；Content Hash 冻结 Requirements/Checks/Screening 集合与复审配置；DRAFT 不得有批准信息，RETIRED 保留原批准信息但不占 Slot。 |
| `t_dd_program_requirement` | `program_requirement_id=110021, requirement_code=TRADE_LICENSE, document_type=BUSINESS_LICENSE, required_flag=1`；Version 冻结后只读。 |
| `t_dd_program_check` | `program_check_id=110031, check_code=REGISTRY_ACTIVE, check_type=COMPANY_REGISTRY, required_flag=1`。 |
| `t_dd_program_screening` | `program_screening_id=110041, subject_role=UBO, screening_type=SANCTIONS, required_flag=1`。 |
| `t_due_diligence_case` | Organization `case_id=120001, case_no=DD-2026-0001, role_type=CLIENT, role_id=200001, qualification_type=ORGANIZATION_KYB, case_origin=APPLICATION, source_namespace=CCT_CLIENT_APPLICATION, source_application_id=810001, source_submission_version=3, source_submission_hash=sha256:submission-v3, status=APPROVED, active_slot=NULL, version=6`；Case Type 由 Qualification 推导，不重复存列。由同一申请派生的 UBO KYC Case `120002` 使用 `role_type=RELATED_PERSON, role_id=100002, qualification_type=RELATED_PERSON_KYC, case_origin=APPLICATION, source_namespace=CCT_CLIENT_APPLICATION, source_application_id=810001, source_submission_version=3, source_submission_hash=sha256:submission-v3`，其中 Role ID 就是 CCT Party ID；同一 Party 同时是 UBO/Director/Signatory 时复用该 KYC Case，由多条不同 Dependency Type 指向它。Partner 主 Case `120003` 使用 `source_namespace=CCT_LOCAL_PARTNER_APPLICATION, source_application_id=730320000000000001, role_type=LOCAL_PARTNER, role_id=730300000000000001, qualification_type=PARTNER_DUE_DILIGENCE`；关联人子 Case `120004` 使用相同 Namespace/Application 和 `role_type=RELATED_PERSON, role_id=730100000000000009, qualification_type=RELATED_PERSON_KYC`。2027 到期周期创建 `case_id=120101, case_origin=CONTINUOUS_REVIEW, source_namespace=RISK_COMPLIANCE, scheduled_review_time=2027-08-26T10:00Z, source_application_id=NULL, source_submission_version=NULL, source_submission_hash=NULL`；进行中状态按 Role+Qualification 占用 active_slot=1，终态必须置空。 |
| `t_dd_case_subject` | PRIMARY `case_subject_id=120011, case_id=120001, party_id=100001, subject_role=PRIMARY, primary_slot=1, source_profile_id=810101, source_profile_version_no=4`；UBO 行 `120012` 冻结 Party `100002` 的 Profile 坐标，以及 `source_relation_version_id=820001, source_relation_version_no=2, ownership_percentage=60.0000000000`。 |
| `t_dd_case_dependency` | `dependency_id=120021, parent_case_id=120001, child_case_id=120002, dependency_type=UBO_KYC, accepted_decision_id=130002, version=2`；创建时决定为空，子 Case 形成有效决定后以 CAS 单向接受；禁止自依赖和环。 |
| `t_dd_party_snapshot` | `snapshot_id=120031, case_subject_id=120011, schema_version=1, source_content_hash=sha256:profile-v4, snapshot_vault_token=vt_dd_120031, captured_time=2026-08-26T09:55Z`；Token 支持授权 Reveal，来源 Hash 支持案件输入核验，不保存无消费者的 Vault Record Version/Metadata Hash。 |
| `t_dd_requirement` | `requirement_id=120051, case_id=120001, program_requirement_id=110021, status=ACCEPTED, version=3`。 |
| `t_dd_evidence_ref` | `evidence_id=120061, requirement_id=120051, case_subject_id=120011, file_id=710001, content_hash=sha256:license, status=ACCEPTED, version=2`；Document Type 从 Program Requirement 推导，替换文件时写新 File ID，不保存正文或文件业务版本。 |
| `t_dd_check` | `check_id=120071, case_subject_id=120011, program_check_id=110031, provider_code=UAE_REGISTRY, request_ref=REG-9001, result_code=ACTIVE, performed_time=2026-08-26T09:57Z`；Check Type 从模板推导；若提供方结果需恢复，只保存 `result_vault_token`。 |
| `t_screening_run` | `screening_run_id=120081, case_subject_id=120012, program_screening_id=110041, provider_code=SCREEN_A, request_ref=SCR-9001, status=SUCCEEDED, started_time=2026-08-26T09:58Z, completed_time=2026-08-26T09:59Z, version=2`；Screening Type 从模板推导。 |
| `t_screening_hit` | `hit_id=120091, screening_run_id=120081, external_hit_ref=HIT-7, match_score=0.7200000000, status=RESOLVED, version=2`；Status 只表达是否仍待处置，具体结论读取最大 Resolution Version。 |
| `t_screening_resolution` | `resolution_id=120101, hit_id=120091, resolution_version=1, resolution=FALSE_POSITIVE, reviewer_id=900001`；可选 Evidence File ID/Hash 同空值，最大 `resolution_version` 为当前处置。 |
| `t_dd_case_state_change` | Aggregate Version `1..6` 追加 `NULL→DRAFT→SUBMITTED→IN_REVIEW→WAITING_EVIDENCE→IN_REVIEW→APPROVED`，首行 from_status 为空，后续行非空，每行携带 Trigger 与 Actor。 |
| `t_qualification_decision` | `decision_id=130001, case_id=120001, decision_version=1, decision_evaluation_id=310090, decision_source=REVIEWER, recommended_decision=APPROVE, outcome=APPROVED, decided_by=900001, valid_from=2026-08-26T10:00Z`；创建后不可改。 |
| `t_role_qualification` | `qualification_id=130011, role_type=CLIENT, role_id=200001, current_decision_id=130001, status=APPROVED, review_frequency_code=ANNUAL, next_review_time=2027-08-26T10:00Z, review_status=ACTIVE, version=1`；是跨 Case 当前投影和复审调度，不代替决定历史。 |
| `t_qualification_state_change` | `change_id=130021, qualification_id=130011, to_status=APPROVED, decision_id=130001, aggregate_version=1`，与投影和可靠事件同事务。 |

若 UBO 决定缺失、失效或不属于 Dependency 指向的 Party，Organization Qualification 必须失败关闭；不能用 Organization 自身检查替代 UBO KYC。`source_namespace + source_application_id + role_type + role_id + qualification_type` 完整来源身份相同的重放先命中 Case：Submission Hash 相同返回既有 Case，Hash 不同直接冲突；同一 Application 下不同 Role/Party/Qualification 的主子 Case 互不冲突。相同数值 Application ID `730320000000000001` 在 `CCT_CLIENT_APPLICATION`、`CCT_OPERATING_ENTITY_APPLICATION`、`CCT_LOCAL_PARTNER_APPLICATION` 三个 Namespace 中彼此隔离；Local Partner Namespace 只接受 LOCAL_PARTNER 或 RELATED_PERSON Role，拒绝 CLIENT 和 OPERATING_ENTITY。周期调度器以 `qualification_id=130011, version=1, next_review_time=2027-08-26T10:00Z` 做条件更新，在同一本地事务创建 Case `120101` 并推进下一复审时间；重复执行由 Review Cycle 唯一键返回同一 Case，不建立 Plan/Run 记录。

## 3. SCN-RISK-02：自由规则、候选 Release 与 SHADOW 旁路

Checkout Decision Point 使用受控 Context Field、工程注册 Feature/Action Handler 和类型安全 Rule AST。生产基线 Release `260001` 已 ACTIVE；Candidate `260002` 完成 8 项强制 STATIC_CHECK 后，以相同输入快照和 `evaluation_time` 对 10,000 个样本执行 SHADOW。每个 STATIC_CHECK 的 (event_time,audit_event_id) 必须严格早于 Candidate 进入 SHADOWING 的 State Change；Run 的 completed_time 必须位于 SHADOWING（含）与 REVIEW_PENDING（含）之间；每个 APPROVAL 的 (event_time,audit_event_id) 必须严格晚于 REVIEW_PENDING。Candidate 只产生 SIMULATED Effect；Shadow 完成且固定质量门禁通过后才进入 REVIEW_PENDING，两个不同 reviewer 必须绑定同一 Shadow Run。第二次批准以同一 approval_request_id、同一 event_time 绑定 Candidate `REVIEW_PENDING→APPROVED`、Candidate `APPROVED→ACTIVE` 与旧 Release `ACTIVE→SUPERSEDED` 三条 State Change，并在同一命令和本地事务中原子完成切换。

### 3.1 决策点、规则和发布逐表映射

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_decision_point` | `decision_point_id=210001, point_code=CHECKOUT_PREPARE, business_domain=CHECKOUT, status=ACTIVE`。 |
| `t_decision_point_version` | `point_version_id=210011, version_no=2, input_contract_code=ORDER_PREPARATION, input_contract_version=4, default_decision=REVIEW, evaluation_strategy=FIRST_TERMINAL, failure_strategy=FAIL_CLOSED`。 |
| `t_decision_point_input_field` | `input_field_id=210021, field_code=client_ip_address, value_type=STRING, data_classification=CONFIDENTIAL_RISK, rule_reference_flag=1`；未注册字段不能被规则读取。 |
| `t_decision_point_feature` | `binding_id=210031, point_version_id=210011, feature_version_id=220011, sequence_no=1`；构成 Feature 白名单。 |
| `t_decision_point_rule_set` | `binding_id=210041, point_version_id=210011, rule_set_version_id=230011, sequence_no=1`。 |
| `t_decision_point_obligation` | Checkout 的 Action 型义务为 `obligation_id=210051, trigger_decision=ALLOW, obligation_type=ACTION, action_version_id=240031, sequence_no=1, failure_policy=FAIL_CLOSED`，会以 `obligation_id` 进入 Intent；Payment 的义务为 `obligation_id=210052, obligation_type=RESERVE_LIMIT_GROUP, action_version_id=NULL`，由 0..N 条 Limit Binding 配置且不生成 Action Intent；四个 Manual Payout Point Version 各恰一条 `trigger_decision=ALLOW, obligation_type=CHECK_SETTLEMENT_ELIGIBILITY, action_version_id=NULL, failure_policy=FAIL_CLOSED`，且不得有 Limit Binding。运营不能删除这些系统义务。 |
| `t_feature_version` | Feature Version 吸收目录身份：`feature_version_id=220011, feature_code=IP_RISK_LEVEL, feature_name=IP Risk Level, business_category=NETWORK, version_no=2, result_value_type=STRING, handler_code=NETWORK_RISK_V2, implementation_version=2.4.1, implementation_hash=sha256:handler, status=ACTIVE`；只允许工程注册 Handler，禁止任意 SQL/Script。 |
| `t_feature_dependency` | `dependency_id=220021, feature_version_id=220011, dependency_feature_version_id=220012, sequence_no=1, required_flag=1`；发布时验证无环。 |
| `t_rule_reject_reason` | `rejection_reason_id=231001, reason_code=COUNTRY_RESTRICTION, reason_name=地区限制, default_message=当前地区需人工复核, status=ACTIVE, version=1, created_by=900001, updated_by=900001`。运营维护当前名称和默认内部文案；规则保存自己的代码与消息快照，停用不改旧版本。编辑和启停携带 expectedVersion，结果不确定时人工查询；不存变更回执。 |
| `t_rule_subject_tag` | `tag_id=232001, tag_code=REQUIRES_REVIEW, tag_name=需要复核, description=需要人工确认交易背景, status=ACTIVE, version=1, created_by=900001, updated_by=900001`。标签只定义可配置代码，不保存真实主体标签实例或适用主体关系；目标绑定沿用工程目录，新增规则选项必须启用。 |
| `t_rule_set` | `rule_set_id=230001, decision_point_id=210001, rule_set_code=CHECKOUT_NETWORK, current_rule_set_version_id=230011, status=CONFIGURABLE, version=5`；列表只展示一行当前配置。保存或冻结按管理版本 CAS 推进；任一历史版本被当前 ACTIVE Release 使用时禁止整体删除。可删除时根转为 DELETED 并记录删除人、时间、原因和触发身份，编码永久占用，旧版本、Candidate 与成功命令回放保留，不提供恢复。 |
| `t_rule_set_version` | Rule Set Version 保留不可变配置身份：`rule_set_version_id=230011, decision_point_id=210001, rule_set_code=CHECKOUT_NETWORK, rule_set_name=Checkout Network Rules, version_no=4, status=FROZEN, content_hash=sha256:rules, created_by=900001, change_reason=Block anonymous high-risk checkout, frozen_by=900002, frozen_time=2026-08-26T09:58:00.000000Z`；创建人和非敏感变更原因必填，冻结后不可修改，也不跨 Decision Point 复用。 |
| `t_risk_rule` | `rule_id=230021, rule_code=DENY_HIGH_RISK_IP, priority_no=10, enabled_flag=1, reason_code=NETWORK_HIGH_RISK`。 |
| `t_rule_node` | Root `node_id=230031, node_type=GROUP, group_operator=AND`；Child `230032` 为 `PREDICATE/EQ`，同级 Sequence 唯一。 |
| `t_rule_operand` | Predicate Left `operand_id=230041, operand_role=LEFT, value_sequence=1, source_type=FEATURE, feature_version_id=220011, value_type=STRING`；Right LITERAL `operand_id=230042, operand_role=RIGHT, value_sequence=1, source_type=LITERAL, value_type=STRING, string_value=HIGH`。LITERAL 直接用多行 `value_sequence` 和五个 typed columns 表达；IN/NOT_IN 使用连续 `1..n`，BETWEEN 固定 `[1,2]`，每行仅一个类型值非空。 |
| `t_action_version` | Action Version 吸收目录身份：`action_version_id=240011, action_code=DENY_CHECKOUT, action_name=Deny Checkout, action_category=DECISION, version_no=3, handler_code=DECISION_DENY_V1, implementation_version=1.3.0, implementation_hash=sha256:deny-handler, terminal_flag=1, decision_code=DENY, merge_policy=MOST_RESTRICTIVE, shadow_supported_flag=1, status=ACTIVE`；Cooldown 与 IP List 使用各自工程注册版本，Settlement 使用 `CREATE_SETTLEMENT_CONTROL + action_category=SETTLEMENT + target_type=SETTLEMENT_CONTROL + terminal_flag=0 + LOCAL_SYNC` 的已登记 Handler。 |
| `t_action_parameter_definition` | `parameter_definition_id=240021, action_version_id=240011, parameter_code=reason_code, value_type=STRING, allowed_source_type=LITERAL`。 |
| `t_rule_action` | `rule_action_id=240031, rule_id=230021, action_version_id=240011, sequence_no=1`；该 Rule 只有一个 Terminal Action。 |
| `t_rule_action_parameter` | `parameter_id=240041, rule_action_id=240031, parameter_definition_id=240021, value_sequence=1, source_type=LITERAL, string_value=NETWORK_HIGH_RISK`；普通及 Control 根参数固定 sequence=1，Settlement Scope 的 OE/Currency/Bank Country 三维按连续相同 sequence 对齐。 |
| `t_decision_release` | Baseline `release_id=260001, environment_code=PRODUCTION, status=ACTIVE, active_slot=1, version=8`；Candidate `260002` 在审核前无 Active Slot。完整状态链为 `DRAFT→VALIDATING→SHADOWING→REVIEW_PENDING→APPROVED→ACTIVE→SUPERSEDED`，`REJECTED` 是 DRAFT/VALIDATING/SHADOWING/REVIEW_PENDING 的终态分支。 |
| `t_release_audit_event` | 同一不可变 typed audit stream 保存三类行。Candidate 在进入 SHADOWING 前分别追加 `event_type=STATIC_CHECK` 的 `REFERENCE_INTEGRITY/AST_SHAPE/AST_TYPES/FEATURE_DEPENDENCY/HANDLER_HASH/ACTION_PARAMETERS/TERMINAL_ACTION/SYSTEM_OBLIGATION` 八行且全部 `PASSED`；最后一条 STATIC_CHECK 示例为 `event_time=2026-08-26T09:59:58.000000Z`，其 (event_time,audit_event_id) 严格早于 SHADOWING State Change。状态迁移用 `event_type=STATE_CHANGE, from_status, to_status, trigger_type, trigger_id, aggregate_version`；Reviewer `900002/900003` 用不同 `approval_request_id` 追加两个 `event_type=APPROVAL, shadow_run_id=260011, approval_status=APPROVED` 事件，且各自 (event_time,audit_event_id) 严格晚于 REVIEW_PENDING。第二次批准的 `approval_request_id=APR-260002-02, event_time=2026-08-26T11:02:00.000000Z` 同时绑定 Candidate `REVIEW_PENDING→APPROVED`、Candidate `APPROVED→ACTIVE` 和旧 Release `ACTIVE→SUPERSEDED` 三条 State Change；三条迁移在同一命令和本地事务中原子执行。 |
| `t_release_shadow_run` | `shadow_run_id=260011, baseline_release_id=260001, candidate_release_id=260002, sample_target=10000, sample_count=10000, status=COMPLETED, started_time=2026-08-26T10:00:00.000000Z, completed_time=2026-08-26T10:30:00.000000Z, allow_to_review_count=80, allow_to_deny_count=20, deny_to_allow_count=0, action_change_count=24, feature_missing_count=0, failed_count=0, timed_out_count=2, p95_millis=18, p99_millis=31, check_status=PASSED, version=4`；SHADOWING State Change 为 `2026-08-26T09:59:59.000000Z`，REVIEW_PENDING State Change 为 `2026-08-26T10:30:00.000000Z`，因此 completed_time 位于 SHADOWING（含）与 REVIEW_PENDING（含）之间；Run 完成时冻结汇总质量指标。 |

8 项 Static 全部通过才允许 Shadow；Shadow 完成且质量合格后才能 Review。Shadow/Replay 只能形成 SIMULATED Effect，不返回业务结果，不创建或执行真实 Risk Restriction、Risk List Entry、Limit Reservation Group、Limit Reservation、Limit Movement、Settlement Control、Dispute Liability Decision、Velocity Event 或 compensation_event。逐样本差异保存在候选 SHADOW Evaluation，数据门禁读取 Run 的固定汇总指标，不允许通过临时 SQL/Script 改写标准。

## 4. SCN-RISK-03：Checkout PRIMARY、Action 合并与冷却/IP 名单

Order `410001` 创建前，PRIMARY Evaluation `310001` 使用 Release `260001` 和输入快照 `320001`。另一个高风险请求 Evaluation `310003` 命中 DENY、30 分钟 Cooldown 和 `BLOCKED_IP` 名单 Action；所有 Intent 先按各自 Action Version 与目标合并，随后在本地事务写入三个 REAL Effect、Risk Restriction/List Entry 和可靠事件。对应 SHADOW Evaluation 只形成 SIMULATED Effect。

### 4.1 Evaluation、Assessment 与 Action 8 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_decision_evaluation` | PRIMARY `evaluation_id=310001, evaluation_request_id=REQ-CHECKOUT-410001, evaluation_mode=PRIMARY, evaluation_time=2026-08-26T10:00:00.000000Z, technical_status=SUCCEEDED, proposed_decision=ALLOW, business_decision=ALLOW, decision_adjustment_reason_code=NULL, default_decision_used_flag=0, input_snapshot_hash=sha256:checkout`；若使用默认决定，proposed 必须等于 Point Version 默认值；若 proposed/business 不同必须填写调整原因。FAILED/TIMED_OUT 的两个决定和调整原因全空、default flag=0 且 failure_code 必填。SHADOW `310002` 完整填写 `shadow_run_id/baseline_evaluation_id/decision_changed_flag/action_changed_flag/comparison_severity/compared_time`，并复用基线的事件点、输入 Hash 和 `evaluation_time`。 |
| `t_evaluation_feature_result` | `result_id=311001, evaluation_id=310001, feature_version_id=220011, value_type=STRING, string_value=LOW, technical_status=SUCCEEDED`；其他类型列为空。 |
| `t_evaluation_rule_trace` | RULE 行 `trace_id=312001, trace_type=RULE, rule_set_version_id=230011, rule_id=230021, execution_sequence=1, result_value=1, terminal_flag=1, elapsed_millis=2, rule_result_slot=1`；PREDICATE 行 `trace_id=313001, trace_type=PREDICATE, rule_id=230021, node_id=230032, execution_sequence=1, result_value=1, predicate_result_slot=1`，失败时 Result 为空并写 Failure Code。统一表仍保留逐 Rule 和逐 Predicate 解释能力。 |
| `t_evaluation_action_intent` | 高风险规则产生 DENY `intent_id=314003, source_type=RULE, rule_action_id=240031, obligation_id=NULL, action_version_id=240011, resolved_target_type=CHECKOUT, resolved_target_key=410003, merged_effect_id=315003, status=MERGED`；Cooldown 与 IP 名单 Intent 分别合并到 `315004/315005`。Action 型系统义务使用 `source_type=OBLIGATION, rule_action_id=NULL, obligation_id=210051`；`RESERVE_LIMIT_GROUP` 义务不产 Intent。RULE 必须经 Rule Set Binding、OBLIGATION 必须是同 Point Version 的 ACTION 义务；两类来源严格 XOR，只有 MERGED 可引用 Effect。 |
| `t_action_effect` | PRIMARY 高风险请求形成 DENY `effect_id=315003, merge_policy=MOST_RESTRICTIVE`、Cooldown `315004, merge_policy=MAX` 和 IP List `315005, merge_policy=UNION` 三行；每个 Effect 至少有一个 MERGED Intent，Evaluation/Action/Target/Merge Policy 逐值一致。`RISK_ACTION_EFFECT_V1` 只使用持久化 Effect 身份和按数值 `(intent_sequence,intent_id)` 排序的 MERGED 贡献者，按 RFC 8785 JCS/UTF-8 计算 SHA-256 小写 Hash，不含 Handler 返回值；REAL 的 `idempotency_key=risk-action-effect:v1:<effect_hash>`。`effect_status` 与 `execution_status` 严格按 CREATED/INIT、PENDING_ASYNC/PROCESSING、PENDING_ASYNC/ERROR、APPLIED/SUCCEEDED、FAILED/FAILED 映射。候选旁路 Effect `315002` 必须为 SIMULATED，`effect_status=SIMULATED` 且 `idempotency_key/execution_type/execution_status/attempt_count/started_time/completed_time/last_error_code/last_error_summary` 全为空；任何 FAILED/TIMED_OUT Evaluation 都不得产生 Effect。 |
| `t_checkout_input_snapshot` | `snapshot_id=320001, evaluation_id=310001, order_id=410001, order_item_id=410011, client_id=200001, operating_entity_id=210001, assignment_id=820001, industry_code=SOFTWARE, mcc_code=5734, amount=100.00000000, currency=USD`；V5 Agreement/Legal/Seller、Partner 来源的 Platform Service Charge Scope/Party 和商业上下文一并进入 Canonical 输入，Risk 不消费 Finance Entity ID；Finance Tax Snapshot 与 Order Context 尚不存在。 |
| `t_transaction_risk_assessment` | Payment 场景使用 `assessment_id=330010, evaluation_id=310010, payment_attempt_id=510011, order_context_id=410021, amount=108.00000000, currency=USD, restricted_input_hash=sha256:payment-risk`。 |
| `t_settlement_risk_assessment` | 示例不可变行 `assessment_id=340020, evaluation_id=310020, checkpoint_type=PAYOUT_CREATION, role_type=CLIENT, role_id=200001, operating_entity_id=210001, gross_amount=82000.00000000, currency=USD, bank_country=AE, source_fact_type=PAYOUT, source_fact_id=560001, source_fact_version=1, input_hash=sha256:payout-create, eligibility_decision=RESTRICT, required_amount=20000.00000000, required_currency=USD, control_set_hash=sha256:control-set-v1`；其他检查点各写新行，同一命令重放复用原行。 |

Settlement Assessment 必须逐值满足 `checkpoint_type = Decision Point.point_code` 与 `input_hash = Evaluation.input_snapshot_hash`。`(eligibility_decision, technical_status, proposed_decision, business_decision)` 只允许 `(ALLOW, SUCCEEDED, ALLOW, ALLOW)`、`(RESTRICT, SUCCEEDED, ALLOW, DENY)`、`(NULL, SUCCEEDED, REVIEW, REVIEW)`、`(NULL, SUCCEEDED, DENY, DENY)`、`(NULL, FAILED, NULL, NULL)` 和 `(NULL, TIMED_OUT, NULL, NULL)`；任一字段错配都拒绝该不可变行。

Point Domain 冻结快照族：CHECKOUT/PAYMENT/SETTLEMENT 分别只能有一张 Checkout/Transaction/Settlement 行且另外两类为零；DUE_DILIGENCE/DISPUTE 三类均为零。PRIMARY 继续使用 Fail-Closed、FIRST_TERMINAL 和 Intent Merge：任何 Feature/AST/Handler/系统义务失败都不降级为 ALLOW；第一次 Terminal Decision 停止后续 Rule；同一目标的 DENY、Cooldown 与 IP List Intent 先按固定策略合并，再形成 REAL Effect。Shadow/Replay 仅留下 SIMULATED 轨迹，绝不执行这些副作用。

### 4.2 冷却、名单与风险数据 6 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_risk_restriction` | Cooldown Effect 落地 `restriction_id=410101, restriction_type=COOLDOWN, subject_type=CONSUMER, subject_key=consumer:C-9001, source_effect_id=315004, status=ACTIVE, effective_to=2026-08-26T11:00Z, version=1`；到期只改业务状态，不销毁历史。 |
| `t_risk_list` | `list_id=410111, list_code=BLOCKED_IP, supported_subject_type=IP_ADDRESS, matching_mode=EXACT, status=ACTIVE, version=3`。 |
| `t_risk_list_entry` | IP List Effect 落地 `entry_id=410121, list_id=410111, subject_type=IP_ADDRESS, subject_key=203.0.113.9, source_effect_id=315005, status=ACTIVE, version=1`；人工解除仍由新的 Effect 形成替代行。 |
| `t_payment_attempt_fact` | Payment 场景中 `fact_id=510101, payment_attempt_id=510011, attempt_status=UNKNOWN, limit_reservation_group_id=540010, source_event_key=pay-attempt-510011-v4, source_version=4, version=4`。 |
| `t_velocity_event` | `velocity_event_id=420001, event_code=CHECKOUT_DENIED, source_type=CHECKOUT_EVALUATION, source_id=310003, source_version=1, amount=100.00000000, currency=USD`；不可变明细是窗口准确性来源。 |
| `t_velocity_event_subject` | `event_subject_id=420011, velocity_event_id=420001, subject_type=IP_ADDRESS, subject_key=203.0.113.9, event_code=CHECKOUT_DENIED, occurred_time=2026-08-26T10:30Z`；两个冻结查询维度必须等于父 Event。 |

## 5. SCN-RISK-04：Payment Attempt 限额、LIMIT_EXCEEDED 与 UNKNOWN

Payment Attempt `510011` 的 PRIMARY Evaluation `310010` 候选 ALLOW。`RESERVE_LIMIT_GROUP` Obligation `210052` 通过 Binding `520021` 冻结 Limit Version `510211`，不生成 Action Intent/Effect；Risk 按稳定顺序锁 Position `530001`，原子创建 Group `540010`、不可变 Reservation `540011` 和 RESERVE Movement `540021`。Channel 结果 UNKNOWN 后只推进 Group 并继续占用；权威 Success 到达才 COMMIT。另一笔请求若超过阈值，整个事务回滚并返回 DENY/LIMIT_EXCEEDED，不留下半个 Group。

### 5.1 Limit 7 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_limit_version` | `limit_version_id=510211, limit_code=CLIENT_DAILY_AMOUNT, limit_name=Client Daily Amount, version_no=2, subject_type=CLIENT, metric_type=AMOUNT, window_unit=DAY, window_size=1, amount_limit=10000.00000000, count_limit=NULL, currency_mode=PER_CURRENCY, fixed_currency=NULL, status=FROZEN, frozen_by=900001`；身份与阈值同表冻结，只表达固定 UTC Window。 |
| `t_decision_point_limit_binding` | `binding_id=520021, obligation_id=210052, limit_version_id=510211, sequence_no=1, subject_source_type=CONTEXT, subject_input_field_id=210121, amount_source_type=CONTEXT, amount_input_field_id=210122`；只能属于准确 Point Version 的 RESERVE_LIMIT_GROUP 义务。 |
| `t_limit_position` | `position_id=530001, limit_version_id=510211, subject_key=client:200001, currency_scope_key=USD, window_start=2026-08-26T00:00Z, window_end=2026-08-27T00:00Z, reserved_amount=108.00000000, committed_amount=0, version=8`。 |
| `t_limit_reservation_group` | `reservation_group_id=540010, assessment_id=330010, obligation_id=210052, status=PAYMENT_UNKNOWN, hold_reason=CHANNEL_RESULT_UNKNOWN, resolved_time=NULL, version=2`；Group 是唯一当前状态权威；零 Binding 也创建没有 Reservation 明细的空 Group。 |
| `t_limit_reservation` | 不可变行 `reservation_id=540011, reservation_group_id=540010, position_id=530001, amount=108.00000000, count_value=0`；不复制 Group 状态、原因、时间或 Version。 |
| `t_limit_group_state_change` | `change_id=540012, aggregate_version=2` 追加 `RESERVED→PAYMENT_UNKNOWN`；Success 后再追加 `PAYMENT_UNKNOWN→COMMITTED`，Aggregate Version 单调，首行固定 `NULL→RESERVED`。 |
| `t_limit_movement` | `movement_id=540021, reservation_id=540011, movement_type=RESERVE, amount_delta=108.00000000, count_delta=0, position_version=8`；Position 经 Reservation 定位；Success 追加 COMMIT 的非负绝对转移量，重复事件受 Trigger 唯一键阻挡。 |

UNKNOWN 不自动过期、不释放 Position。只有 Payment 权威 Success/Failure/Cancel/Void 能按准确 Group ID 收口；Refund、Dispute 和已 Capture Reversal 默认不恢复额度。

## 6. SCN-RISK-05：Manual Payout Eligibility Control、四个检查点与精确释放

Client `200001`、OE `210001`、USD/AE 同时命中 `SETTLEMENT_BLOCK` 和最终门槛为 `20,000 USD` 的 `MIN_SECURITY_DEPOSIT`；Risk 只冻结控制与门槛，不读取、锁定或迁移 Finance 余额。Payout Create 即使因 Block 返回 `RESTRICT`，仍冻结完整 `20,000 USD` 门槛，保证结果可审计和重放。

四个业务检查点各自创建新的 PRIMARY Evaluation 与 Settlement Assessment，不能用前一阶段结果替代当前检查：

Execution Create 与 External Submit 是两个独立检查点；`BANK_CASH` 在前者通过并成功创建 CREATED Execution 时立即占用，不能延迟到后者或银行提交后。

所有检查点的 `source_fact_version` 都限定为 MySQL signed INT 的 `1..2147483647`；创建类检查点进一步固定为 1。

- Payout Create：使用预分配 `payout_id=560001` 与 `source_fact_version=1`，写 `evaluation_id=310020, assessment_id=340020, checkpoint_type=PAYOUT_CREATION, source_fact_type=PAYOUT, source_fact_id=560001, decision=RESTRICT, required_amount=20000.00000000`；Risk 成功但 Finance 本地锁账或建根失败时，同一创建命令重放复用该坐标。
- Final Approval：`evaluation_id=310021, assessment_id=340021, checkpoint_type=PAYOUT_FINAL_APPROVAL, source_fact_type=PAYOUT, source_fact_id=560001, source_fact_version=2`；它绑定审批命令开始时锁定的变更前 expected current version，不得引用尚未产生的审批后版本，也不能复用 Payout Create 结论。
- Execution Create：使用预分配 `execution_id=580001` 与 `source_fact_version=1`，写 `evaluation_id=310022, assessment_id=340022, checkpoint_type=PAYOUT_EXECUTION_CREATION, source_fact_type=PAYOUT_EXECUTION, source_fact_id=580001`；通过后 Finance 才创建 CREATED Execution，并立即以 `cash_required_amount` 锁定准确 `BANK_CASH`。
- External Submit：`evaluation_id=310023, assessment_id=340023, checkpoint_type=PAYOUT_EXTERNAL_SUBMISSION, source_fact_type=PAYOUT_EXECUTION, source_fact_id=580001, source_fact_version=1`；它绑定提交命令开始时锁定的 CREATED 变更前 expected current version，不得引用未来 SUBMITTED 版本。每次真正调用银行前都重新检查；前一 Execution 权威 FAILED 后创建的新 Retry Execution 必须使用新 `execution_id`、连续 `attempt_no`，再走 Execution Create 与 External Submit 两个检查点并取得两对新坐标；UNKNOWN 必须阻断 Retry。

同一幂等命令重放复用并返回原 Evaluation 与 Assessment，不创建额外坐标，也不按当前 Control 重算原结果。每个 Assessment 结构化证明 Checkpoint、Role/OE、Gross Amount/Currency、Bank Country、来源事实 Type/ID/Version 和 Input Hash；其中 `gross_amount=owner_debit_amount=cash_required_amount`，`requested_amount` 或 `beneficiary_amount` 均不得用作该字段，`source_fact_version` 始终是评估命令锁定的变更前 expected version；创建类固定为 1，Final Approval/External Submit 使用命令开始时的当前锁定版本。`bank_country` 逐值来自 Finance 冻结的单表 `beneficiary_account.bank_country`；契约不包含 `beneficiary_account_ver`。Finance 冻结完整 RCO-I09 返回并在本库计算自己的 `eligibility_hash`，但该 Hash 不能跳过下一业务检查点；Payout 本地只冻结 Final Approval 完整结果，Execution 本地当前快照最终由 External Submit 结果覆盖，Execution Create 坐标仍在 Risk 长期可检索。

四个检查点在各自业务副作用前都独立执行 Finance 本地账户门禁：Client 要求 `CLIENT_RECOVERY_RECEIVABLE/NONE = 0` 且 `CLIENT_SECURITY_DEPOSIT/NONE >= 本次 required_amount`；Local Partner 要求 `PARTNER_RECOVERY_RECEIVABLE/NONE = 0`，且本次 Risk `required_amount/required_currency` 必须同时为 NULL。非 ALLOW、Scope/1:1/Hash 不一致、账户不合格或技术失败都 Fail-Closed，不能复用上一检查点的门禁结果。Local Partner 不查询或伪造 `CLIENT_SECURITY_DEPOSIT/NONE` 与 `CLIENT_RECOVERY_RECEIVABLE/NONE`。这里的 Payout 是 Finance 向 Client/Local Partner 清偿已有应付余额；未来 Client-to-Beneficiary Disbursement 属于独立能力，不得复用这些 Decision Point、输入或 Control。

### 6.1 Settlement 3 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_settlement_control` | Block 根 `control_id=610101, control_no=SC-2026-001, role_type=CLIENT, role_id=200001, control_type=SETTLEMENT_BLOCK, reason_code=KYB_EXPIRED, source_action_effect_id=315020, status=ACTIVE, required_amount=NULL, required_currency=NULL, control_instance_key=RULE_ACTION:240051:KYB_CASE:120001, active_slot=1, version=1`；独立门槛根 `control_id=610102, control_type=MIN_SECURITY_DEPOSIT, reason_code=DISPUTE_EXPOSURE, required_amount=20000.00000000, required_currency=USD, release_condition_code=MANUAL_REVIEW, status=ACTIVE, active_slot=1, version=1`。 |
| `t_settlement_control_scope` | `scope_id=610111, control_id=610101, operating_entity_id=210001, currency=USD, bank_country=AE, scope_hash=sha256:block-scope`；`scope_id=610112` 用同一 Scope 维度归属门槛 Control `610102`。一条 Control 至少一条 Scope，多条 Scope 为 OR。 |
| `t_settle_control_state_change` | 两个 Control 分别追加 `NULL→ACTIVE, aggregate_version=1`。Operations 用准确 `control_id=610101 + expected_version=1 + 幂等键` 做精确释放，只追加该 Block 的 `ACTIVE→RELEASED`；`MIN_SECURITY_DEPOSIT` `610102` 仍为 `ACTIVE`。 |

精确释放 `SETTLEMENT_BLOCK` 只终止 Control `610101`，同 Role 的 `MIN_SECURITY_DEPOSIT` Control `610102` 继续保持 `ACTIVE`。

所有 Eligibility、创建、精确释放和 Timer 都先锁该 Role Range，并用 `(role_type,role_id,active_slot,control_id)` 取得 Next-Key/Gap Lock；统一锁序为 `Role Range -> control_id 升序 Root -> Scope/Event`。取得 Role Range 与 Root 锁后才采样数据库 UTC，等待锁前的命令接收或 Worker 唤醒时间不能冒充状态发生时间。Timer 的幂等 Trigger 固定为 `settlement-control-expiry:v1:<control_id>:<expected_version>:<effective_to>`；它只做 `ACTIVE→EXPIRED` CAS，重复触发返回原结果。`RELEASED/EXPIRED` 永不复活，新政策或终态后的再次命中必须创建新 Control。

## 7. SCN-RISK-06：Dispute RFI、Outcome 与 Liability

Payment 发布 Channel Dispute Notice `710001`。Risk 幂等创建 Case `720001`，Client Deadline 为 `2026-09-01T00:00:00.000000Z`，早于 Channel Deadline `2026-09-03T00:00:00.000000Z`。开案直接进入 EVIDENCE_REQUIRED，并与 `DisputeEvidenceRequested` 同事务登记；`DisputeOpened` 不触发 Finance 记账或冻结，Client 沉默也不得自动生成 Lost。Client 提交证据包后，Risk 请求 Payment 创建 Channel Action；只有 Channel 权威 LOST 或明确授权处置才由 Risk 冻结 Outcome/Shared-PK Liability 并发布权威结果。

### 7.1 Dispute 7 表

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_dispute_case` | `dispute_case_id=720001, case_no=DSP-2026-001, channel_dispute_notice_id=710001, channel_dispute_source_version=1, channel_id=610001, mid_id=610011, payment_attempt_id=510011, order_id=410001, client_id=200001, disputed_amount=108.00000000, currency=USD, client_response_due_time=2026-09-01T00:00:00.000000Z, channel_response_due_time=2026-09-03T00:00:00.000000Z`；建案为 `status=EVIDENCE_REQUIRED,current_outcome_id=NULL,version=1`，形成更正后为 `status=CLOSED,current_outcome_id=720052,version=6`。 |
| `t_dispute_state_change` | `change_id=720011` 追加 `NULL→EVIDENCE_REQUIRED, aggregate_version=1`；随后追加 `UNDER_REVIEW→SUBMITTED, trigger_type=SUBMISSION_REQUEST, trigger_id=720041, aggregate_version=3`、`SUBMITTED→LOST, trigger_type=CHANNEL_OUTCOME, trigger_id=730001, aggregate_version=4`、`LOST→CLOSED, trigger_type=CASE_CLOSE_COMMAND, trigger_id=risk-command:720001-close, aggregate_version=5` 和 `CLOSED→CLOSED, trigger_type=OPERATIONS_CORRECTION, trigger_id=740002, aggregate_version=6`；首次结果与 V1 Outcome 相同，每个结果/更正 Trigger ID 逐值绑定 Outcome 来源，最大 Aggregate Version 始终等于 Case Version。 |
| `t_dispute_evidence_package` | `package_id=720021, dispute_case_id=720001, version_no=1, status=SUBMITTED, content_hash=eeb0a7cb32c7aabadb3c30f57caa7ba3ec05cf82de2e633aafe4c1e4eeada177`；Hash 使用 `CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1`，不复制 Case Deadline，提交后不可改。 |
| `t_dispute_evidence_ref` | `evidence_id=720031, package_id=720021, evidence_type=RECEIPT, file_id=710101, content_hash=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef`；Platform 替换文件会产生新 File ID，Risk 不复制文件版本。 |
| `t_dispute_submission_request` | `request_id=720041, dispute_case_id=720001, package_id=720021, request_no=1`；`status=UNKNOWN` 时保持 `active_slot=1, channel_dispute_action_id=730001, action_source_version=3, resolved_time=NULL`，因此不能发起第二个 Request；权威回查后推进 `status=SUCCEEDED, active_slot=NULL, resolved_time=2026-09-02T12:00:00.000000Z, version=4`。 |
| `t_dispute_outcome` | V1 `outcome_id=720051, dispute_case_id=720001, outcome_version=1, outcome=LOST, decision_source_type=CHANNEL_ACTION, decision_source_id=730001, source_version=4, channel_dispute_action_id=730001, supersedes_outcome_id=NULL`；Operations 核验 Channel 更正后追加 V2 `outcome_id=720052, outcome_version=2, outcome=WON, decision_source_type=OPERATIONS_CORRECTION, decision_source_id=740002, source_version=1, channel_dispute_action_id=730002, supersedes_outcome_id=720051`，不得跨案、跳版、回指或分叉。 |
| `t_dispute_liability_decision` | V1 与 Outcome 共享主键 `outcome_id=720051, decision_evaluation_id=310030, action_effect_id=315030, decided_by=NULL, recommended_principal_bearer=CLIENT, principal_bearer=CLIENT, channel_fee_bearer=CLIENT, platform_error_flag=0, channel_recovery_required=0, security_deposit_use_allowed=1, reason_code=CLIENT_LIABILITY`；Effect `315030` 必须保存 `evaluation_id=310030, effect_mode=REAL, effect_status=APPLIED, execution_type=LOCAL_SYNC, execution_status=SUCCEEDED`。V2 同步创建 `outcome_id=720052, decision_evaluation_id=NULL, action_effect_id=NULL, decided_by=900002, principal_bearer=PLATFORM, security_deposit_use_allowed=0`。人工更正必须冻结操作人；责任行不保存独立 Liability ID、Release ID 或金额。 |

Package Hash 的 Canonical JSON 只含 `disputeCaseId/versionNo` 和按 `evidenceType + fileId` 排序的 `contentHash/evidenceType/fileId` 数组；Status、Deadline 和审计列均不参与。相同集合无论输入顺序如何都得到同一 Hash，重复 Evidence Identity 或非规范 ID/Hash 直接拒绝。

另一个 Case `720002` 可由明确授权 Client 处置形成 `decision_source_type=CLIENT_DISPOSITION, decision_source_id=740001, channel_dispute_action_id=NULL` 的 V1 LOST，并以 Liability `decided_by` 冻结授权操作人；对应 State Change 必须为 `trigger_type=CLIENT_DISPOSITION, trigger_id=740001`，这不是把 Client 沉默解释为认损。Operations 首次处置同样只允许 V1 LOST，使用 `OPERATIONS_DISPOSITION`；只有 V2+ 更正命令使用 `OPERATIONS_CORRECTION`，每个 CLOSED 自环的 Trigger ID 必须按版本等于对应更正 `decision_source_id`，交换两个更正命令 ID 也必须拒绝。四类来源均以 `decision_source_type + decision_source_id + source_version` 幂等。

Risk 拥有 Outcome/Liability，Payment 拥有 Channel 交互。只有权威 `DisputeLost` 触发 Finance Journal；Finance 只能读取显式 `security_deposit_use_allowed=1`，且该值还必须对应 `LOST + principal_bearer=CLIENT`，不能从承担方或 Reason Code 推导。更正为 WON 后只追加补偿 Journal，不改写原账；没有建立 Generic RFI。

## 8. 本地可靠执行与幂等

| 物理表 | 示例行与生命周期 |
|---|---|
| `t_compensation_event` | Qualification、Settlement Control、Dispute 等业务事务分别写最小任务。例：`id=900001, event_key=risk:dispute-change:720021, event_type=PUBLISH_DISPUTE_EVENT, sub_event_type=DisputeOpened, payload_json=NULL, execute_status=INIT, execute_count=0, next_execute_at=2026-08-26T10:00:00.000000Z, lock_owner=NULL, lock_until=NULL, last_error_code=NULL, last_error_summary=NULL, lock_version=1`。Claim 后进入 PROCESSING 并成对写 Lock Owner/Until；成功后删除。 |
| `t_command_idempotency` | `idempotency_id=900011, caller_scope=PAYMENT, operation_type=ASSESS_TRANSACTION, idempotency_key=PAY-510011-RISK, request_hash=sha256:req, response_ref=assessment:330010/evaluation:310010, status=SUCCEEDED`；同键同 Hash 返回原坐标和冻结结果，同键不同 Hash 冲突。 |

最小登记可以省略运行字段，由 Schema 安全默认值补齐：`execute_status DEFAULT 'INIT'`、`execute_count DEFAULT 0`、`next_execute_at DEFAULT CURRENT_TIMESTAMP(6)`、`create_time DEFAULT CURRENT_TIMESTAMP(6)`、`update_time DEFAULT CURRENT_TIMESTAMP(6)`，而 `lock_version DEFAULT 1`。扫描分别使用 `idx_comp_event_ready(execute_status,next_execute_at,id)` 与 `idx_comp_event_lease(execute_status,lock_until,id)`；末尾 `id` 使相同时间值的 Claim 顺序稳定且可继续分页。

Risk 补偿事件重建清单按事实族冻结如下；每一行的权威事实同时承担永久幂等，MQ 信封只从列出的本库事实重建：

| event_type | sub_event_type | event_key 构造 | 权威事实 / MQ 信封来源 / 永久幂等 |
|---|---|---|---|
| `PUBLISH_ROLE_QUALIFICATION_EVENT` | `RoleQualificationApproved/Rejected/Expired/Revoked` | `risk:qualification-change:{changeId}` | `t_qualification_state_change + t_role_qualification + t_qualification_decision`；`change_id` 永久唯一 |
| `PUBLISH_COMPLIANCE_RESTRICTION_EVENT` | `ComplianceRestricted` | `risk:restriction:{restrictionId}` | `t_risk_restriction`；`restriction_id` 永久唯一；若后来状态会覆盖事件时点字段，只把无法回源的最小非敏感字段放入 4 KiB Payload |
| `PUBLISH_CHECKOUT_RISK_ASSESSMENT_EVENT` | `CheckoutRiskAssessed` | `risk:checkout-assessment:{snapshotId}` | `t_checkout_input_snapshot + t_decision_evaluation`；`snapshot_id` |
| `PUBLISH_PAYMENT_RISK_ASSESSMENT_EVENT` | `PaymentRiskAssessed` | `risk:payment-assessment:{assessmentId}` | `t_transaction_risk_assessment + t_decision_evaluation`；`assessment_id` |
| `PUBLISH_SETTLEMENT_CONTROL_EVENT` | `SettlementControlChanged` | `risk:settlement-control-change:{changeId}` | `t_settle_control_state_change + t_settlement_control + t_settlement_control_scope`；`change_id` |
| `PUBLISH_DISPUTE_EVENT` | `DisputeOpened/DisputeEvidenceRequested/DisputeWon/DisputeLost/DisputeOutcomeCorrected` | `risk:dispute-change:{changeId}` | `t_dispute_state_change` 联合 Case/Request/Outcome/Liability；`change_id + sub_event_type` |
| `EXECUTE_RISK_ACTION_EFFECT` | `APPLY_ASYNC_ACTION_EFFECT` | `t_action_effect.idempotency_key` | `t_action_effect + t_decision_evaluation + t_action_version`；`uk_action_effect_key` |

`payload_json` 默认为空，只允许无法从上述权威事实恢复的最小非敏感 JSON Object，最大 4 KiB；不保存 Vault Token、网络/消费者原值、Source Version、Schema、Canonical Envelope 或 Payload Hash。`CR_SETTLEMENT_CONTROL_EVENT` 用 State Change `change_id` 重算事件键，`CR_SIMULATED_EFFECT_COMPENSATION_EVENT` 用 Action Effect `idempotency_key` 连接本地任务，不再依赖已删除的 Source 字段。

三个 Risk-owned Recoverable Token 是 `t_dd_party_snapshot.snapshot_vault_token`、`t_dd_check.result_vault_token`、`t_screening_hit.name_vault_token`；四个 Commerce Stable Correlation Token 是 Checkout/Transaction 快照各自的 Email/Phone Token，后四者不授予 Risk Reveal。`t_dd_party_snapshot.snapshot_vault_token` 是已批准的案件时点受保护快照，存在合规复核 Reveal 消费者，保持 NOT NULL。七列均保留 `VARCHAR(128)` 协议缓冲；规则配置不调用 Vault。Risk 物理 DDL 目标为零 CHECK、零 FOREIGN KEY，关系和复合关系 Sidecar 继续由 Owner 事务与测试执行；全局 DB-01 未完成且仍受 Vault 物理 Schema 阻塞。

补偿载荷不包含消费者/网络原值、Vault Reveal 值、文件正文、Channel 原文、Secret 或卡数据。XXL-Job 只做丢失唤醒后的兜底扫描；业务唯一键、来源版本和状态机负责消费者幂等。

## 9. 失败与不创建清单

- Feature Handler 缺失、Implementation Hash 不匹配、Rule AST 有环或 Action 参数类型错误：Release 静态检查失败，不进入旁路。
- SHADOW 或 REPLAY：只保留 Evaluation/Trace/SIMULATED Effect，不创建 Action Execution、Restriction、List Entry、Limit Group、Movement 或业务事件。
- Evaluation FAILED/TIMED_OUT：Business Decision 为空，按 Point Failure Strategy Fail Closed，不写成 DENY。
- Limit 任一 Position 超阈值：全部 Position、Reservation、Movement 和 Group 回滚，返回 DENY/LIMIT_EXCEEDED。
- Payment UNKNOWN：继续保留 Reservation，不由 TTL、调度器或人工脚本释放。
- Local Partner `MIN_SECURITY_DEPOSIT`：发布和运行都失败，不落 Control；Block 仍可正常命中。
- Dispute Deadline 逆序、Request/Package 跨 Case、Channel Action 坐标不匹配、首次结果与 V1 Outcome 相反、Trigger ID 与来源错配、Outcome 来源重放冲突、更正链跨案/分叉、State/Case Version 不一致、Timer/沉默形成 LOST、人工责任缺少 `decided_by`、Action Effect 不属于指定 Evaluation 或保证金许可缺少规则/操作人来源：拒绝状态推进。
- MVP 不建立 Velocity 聚合桶、通用 Case、通用 RFI、通用 Retention、中央补偿库或无业务消费者的敏感字段。
