# Compliance 物理数据库决策

> 2026-10-18 第 06 步自原 [Risk & Compliance 物理决策](../../../risk-decision/docs/database/RISK_DECISION_PHYSICAL_DECISIONS.md) 拆出：本库 `mor_compliance` 的规范模型为 [compliance.pdma.json](./compliance.pdma.json)，Schema 为 [compliance-schema.sql](./sql/compliance-schema.sql)（逐字切片）。通用物理规则、CHECK Sidecar、可靠执行与 Review 门禁仍见原文第 3、4、11–13 节。

## 0. 按服务归属

- 尽调、筛查与资格（20）：`t_dd_case_classification`、`t_dd_case_dependency`、`t_dd_case_state_change`、`t_dd_case_subject`、`t_dd_check`、`t_dd_evidence_ref`、`t_dd_party_snapshot`、`t_dd_program`、`t_dd_program_check`、`t_dd_program_requirement`、`t_dd_program_screening`、`t_dd_program_version`、`t_dd_requirement`、`t_due_diligence_case`、`t_qualification_decision`、`t_qualification_state_change`、`t_role_qualification`、`t_screening_hit`、`t_screening_resolution`、`t_screening_run`。
- 结算控制（3，C3 已定：合规持有，D38）：`t_settlement_control`、`t_settlement_control_scope`、`t_settle_control_state_change`。代码尚未实现；创建经 `POST /compliance/v1/settlement-controls`（幂等键 `sourceActionEffectId`），读取经 `GET /compliance/v1/settlement-controls?ownerType=&ownerId=&status=ACTIVE`，状态变化发布 `SettlementRestrictionChanged`（design/07 §2）。
- 每库一份：`t_compensation_event`。
- 跨库只存 ID、不建模关系：`t_qualification_decision.decision_evaluation_id`、`t_settlement_control.source_action_effect_id`、`t_settle_control_state_change.trigger_type=ACTION_EFFECT` 的 `trigger_id`（均指向 risk-decision，design/07 §3）。
- `CODE-MOR:RISK:DD_PROGRAM_VERSION:1` 保持不变。

### 10.1 Due Diligence

Case 冻结 Program Version 和 Case Origin。只有 Application Case 保存来源 Application ID、Submission
Version/Hash；Continuous Review/Manual 固定 `RISK_COMPLIANCE`。Application Case 以
`source_namespace + source_application_id + role_type + role_id + qualification_type` 唯一定位；命中完整来源身份后比较
Hash，相同即幂等返回、不同即冲突。现有五列唯一索引保持不变，同一数值 Application/Party 由 Typed Namespace
隔离；本次没有新增表、字段、索引、关系或 CHECK 行。

| Namespace | 主 Role / Qualification | 允许的关联人 Role / Qualification |
|---|---|---|
| `CCT_CLIENT_APPLICATION` | `CLIENT + INDIVIDUAL_KYC/ORGANIZATION_KYB` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `CCT_OPERATING_ENTITY_APPLICATION` | `OPERATING_ENTITY + ENTITY_UNDERWRITING` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `CCT_LOCAL_PARTNER_APPLICATION` | `LOCAL_PARTNER + PARTNER_DUE_DILIGENCE` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `RISK_COMPLIANCE` | Continuous Review / Manual 自有来源 | 按既有 Role/Qualification 矩阵 |

`ck_dd_case_origin` 只新增
`(source_namespace='CCT_LOCAL_PARTNER_APPLICATION' AND role_type IN ('LOCAL_PARTNER','RELATED_PERSON'))`，因此该
Namespace 拒绝 `CLIENT` 和 `OPERATING_ENTITY`；`ck_dd_case_tuple` 保持不变。CCT 只接收 Local Partner 主 Case/Decision，Risk
管理关联人 KYC 子 Case 及其依赖。Continuous Review/Manual 的三个 Submission 字段必须为空，禁止制造虚假来源坐标。Program
Version 仅 ACTIVE 占用 `active_slot=1`，DRAFT 不得有批准信息，ACTIVE/RETIRED 必须保留批准人和时间。Case 仅
DRAFT/SUBMITTED/IN_REVIEW/WAITING_EVIDENCE 占 Slot，终态置空；唯一键按 Role + Qualification 防止同一资格并发建案。Case Type
可由 Qualification 完全推导，因此不重复存列；同一行 CHECK 固定 Namespace/Role 矩阵以及
CLIENT+INDIVIDUAL_KYC/ORGANIZATION_KYB、LOCAL_PARTNER+PARTNER_DUE_DILIGENCE、OPERATING_ENTITY+ENTITY_UNDERWRITING、RELATED_PERSON+RELATED_PERSON_KYC，并与
CCT Application Tuple 精确对齐。

Case Subject 冻结 Party Profile ID/Version No，以及 UBO、Director、Signatory 的 CCT Party Relation Version ID/Version No；持股单位与 CCT 一致为百分比 `(0,100]`。每个 Case 通过 nullable `primary_slot` 唯一键最多保留一个 PRIMARY，关联主体通过准确 Relation Version ID 防重；不再建立独立 Relation Snapshot 表。Organization KYB 必须显式依赖全部 Required UBO、Director、Signatory KYC Case/Decision。Dependency 是可变 CAS 行：`accepted_decision_id` 只能从 NULL 单向写为属于 Child Case 的有效 Decision，父 Case 终结后冻结；完整性和无环由 Processor 验证。

创建 Case 时必须验证 Program、Case 的 Qualification Type 完全一致。Requirement、Check、Screening Run 引用的 Template 必须属于该 Case 冻结的同一 Program Version，Screening Template 的 Subject Role 还必须等于 Case Subject Role；所有 Required Template 必须完整实例化。Parent Organization KYB 只收组织级材料，UBO/Director/Signatory 的个人材料分别落在各自 Child KYC Case 的 PRIMARY Subject Requirement 中。

Requirement 通过 `program_requirement_id` 精确回链模板，不复制 Requirement Code/Type；Evidence 通过本库 `case_subject_id` 指向案件主体，不保存外部 Party ID、Document Type 或 Document Version。Evidence 创建后只有 `status/version/update_time` 可变，Requirement、Case Subject、File ID 和 Content Hash 全部冻结。替换文件时锁定 Requirement 与现有 Evidence，在同一事务将旧 RECEIVED/ACCEPTED 行 CAS 为 SUPERSEDED、插入新 RECEIVED Evidence，并把 Requirement 推进或回退为 RECEIVED；只有新 Evidence 与 Requirement 同事务转为 ACCEPTED 后才能再次满足材料要求。

Check Type 和 Screening Type 分别从精确 Program Template 推导。Channel 成功响应时，先按 `(screening_run_id, external_hit_ref)` 去重写入完整 Hit 集合，再在同一事务以 Version CAS 把 Run 从 STARTED 推进为 SUCCEEDED；FAILED/TIMED_OUT 不得写 Hit，避免部分批次。Screening Hit 只保留 OPEN/RESOLVED 队列状态；最新 Resolution 为 INSUFFICIENT_EVIDENCE 时 Hit 保持或经明确 Reviewer Correction 重新变为 OPEN，为 CONFIRMED_MATCH/FALSE_POSITIVE 时同事务 CAS 为 RESOLVED，具体结论始终读取最大 Resolution Version。`resolved_time` 表示这一版人工 Review 完成时点，不表示 Hit 必然关闭。

Program `content_hash` 使用 `CODE-MOR:RISK:DD_PROGRAM_VERSION:1` Canonical Object：`programId/versionNo/reviewFrequencyCode`，以及按 `requirementCode` 排序的 Requirements、按 `checkCode` 排序的 Checks、按 `subjectRole + screeningType` 排序的 Screenings。数组元素分别只含 `requirementCode/documentType/required`、`checkCode/checkType/required/validityDays`、`subjectRole/screeningType/required`。字符串按 Unicode 原值编码，Boolean 使用 JSON true/false，所有 ID/Version/Day 使用无前导零十进制字符串，可空值显式写 JSON null；对象采用固定字段顺序后按 RFC 8785 JCS 生成 UTF-8 字节并计算 SHA-256。Status、Slot、批准信息和审计时间不进入 Hash。激活前必须重算校验，Case 只引用校验通过的准确 Program Version。Check Template 的 `validity_days` 是 Channel 结果最长有效期；非空时 `valid_until` 取 Channel 有效期与 `performed_time + validity_days` 的较早值，空值只保留 Channel 明确返回的有效期。

Snapshot 在 Protect 前按 `RISK_DD_PARTY_SNAPSHOT_V1` 与 `schema_version` 校验字段白名单、排序和大小，`source_content_hash` 承诺该规范化来源 Payload，`captured_time` 表示来源采集时点而非本库插入时点。三个 Risk-owned Recoverable Token 为 `snapshot_vault_token`、`result_vault_token`、`name_vault_token`，分别只允许已批准的 `RISK_DD_PARTY_SNAPSHOT_V1`、`RISK_DD_CHECK_RESULT_V1`、`RISK_SCREENING_HIT_NAME_V1` Data Type，并由当前 Vault V1 Protect/Reveal 处理；`snapshot_vault_token` 有案件复核/审计 Reveal 消费者，保持 NOT NULL，另外两个按是否保存可恢复结果维持可空。四个 Commerce Stable Correlation Token 是 Checkout/Transaction 输入快照各自的 Email/Phone Token，Risk 不 Protect、不拥有原值，也不因保存它们取得 Reveal 权限。七个 Token 列都保留 `VARCHAR(128)` 协议缓冲，不按当前 43 字符 Wire 收紧；规则配置不调用 Vault，也不保存 Vault 业务 Resource 参数。

自动 Evaluation 只形成 recommendation。`t_qualification_decision` 是正式不可变决定版本，覆盖必须记录原因；Case 内最大 `decision_version` 是局部当前决定。Screening Hit 内最大 `resolution_version` 是当前处置，不保留 Current/Supersedes 双指针。`t_role_qualification.current_decision_id` 必须保留，因为它是跨 Case 的当前生效资格投影。

Program Version 保存可选 `review_frequency_code`；Role Qualification 保存频率、`next_review_time` 和 `review_status`。到期任务以 Qualification Version + Next Review Time 做 CAS，在同一本地事务直接创建 `case_origin=CONTINUOUS_REVIEW` 且带 `scheduled_review_time` 的新 Case 并推进下次时间；Role + Qualification + Scheduled Time 唯一键做最终防重。不建立 Continuous Review Plan/Run 表。

### 10.2 Settlement Eligibility Control 与 RCO-I09

Risk 侧当前实现恰好三张控制表：`t_settlement_control` 是 Client/Local Partner 长期控制根，`t_settlement_control_scope` 保存 OE/币种/`bank_country` 的 1:N OR 作用域，`t_settle_control_state_change` 追加完整状态历史。`MIN_SECURITY_DEPOSIT` 的最终最低金额、币种和释放条件直接属于根，没有独立 Requirement 表；它只允许 Client 且不表示 Finance Balance、资金预占或余额迁移。`SETTLEMENT_BLOCK` 可用于 Client/Local Partner，金额三列必须为空。Local Partner 配置或运行出现金额门槛都 Fail-Closed。

Control 只允许 `NULL→ACTIVE→RELEASED|EXPIRED`；RELEASED/EXPIRED 是不可复活终态。根只保存唯一 `source_action_effect_id`，沿 `Action Effect→Evaluation→Release` 追溯来源；REAL、PRIMARY、SUCCEEDED、LOCAL_SYNC 的已登记 `CREATE_SETTLEMENT_CONTROL` Handler 才能创建，一个 Effect 最多一个 Control，SHADOW/REPLAY 禁止创建。创建必须同时写 1..N Scope、首条状态历史和 `SettlementControlChanged`；释放必须携带准确 `control_id + expected_version + 幂等键`，不能清除同 Role 其他活动 Control。Timer Trigger 固定为 `settlement-control-expiry:v1:<control_id>:<expected_version>:<effective_to>`，重复执行返回原终态而不复活。

PRIMARY Eligibility 在 MySQL InnoDB `REPEATABLE READ` 下通过 `(role_type,role_id,active_slot,control_id)` 锁定 Role Range；零行也取得 Next-Key/Gap Lock。创建、检查、精确释放和 Timer 使用唯一锁序 `Role Range -> control_id 升序 Root -> Scope/Event`，且只有取得所需锁后才采样数据库 UTC。每个命中 Control 都冻结完整 Scope 集；任一 Block 返回 RESTRICT，同币种多个最低保证金取 MAX 而不相加。
> 原 10.2 后续段落（出款检查点 Assessment 与 RCO-I09 结果）属于 risk-decision，见 [RISK_DECISION_PHYSICAL_DECISIONS.md](../../../risk-decision/docs/database/RISK_DECISION_PHYSICAL_DECISIONS.md) 第 10.2 节。


### 10.3 角色资格投影、手工登记与字符型角色标识（2026-10-24）

> 依据：[商业关系快照与真实解析接入 technique.md](../../../../requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md) 第 3.3.2、3.4.2 节（S-05、S-07、S-09）；增量 SQL 见 [sql/changes/2026-10-24-qualification-manual-and-role-id.sql](./sql/changes/2026-10-24-qualification-manual-and-role-id.sql) 与 [回填](./sql/changes/2026-10-24-qualification-backfill.sql)。

- `t_qualification_decision.role_id` 与 `t_role_qualification.role_id` 为 `VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin`：`OPERATING_ENTITY` 存 `entity_no`（`[A-Z]{2}[0-9]{3}`），`CLIENT`、`LOCAL_PARTNER`、`RELATED_PERSON` 存十进制 ID 文本。`t_due_diligence_case` 与 `t_settlement_control` 的 `role_id` 仍为 BIGINT（本轮没有 OE 尽调案件，结算控制只涉及 CLIENT / LOCAL_PARTNER）；以后做 OE 尽调时再统一。
- `decision_source` 新增 `MANUAL`：运营手工登记（结论 APPROVED）或撤销（结论 REVOKED），`case_id`、`decision_version`、`decision_evaluation_id`、`recommended_decision` 为空，`decided_by` 与原因代码（写入 `override_reason_code`）、依据说明（`basis_note`，1—1000 字符）必填。`ck_qual_decision_source` 相应增加 MANUAL 分支，由应用保证（不建物理 CHECK）。`uk_qual_decision_ver(case_id, decision_version)` 对 NULL 不约束。
- 允许登记的组合：CLIENT + INDIVIDUAL_KYC / ORGANIZATION_KYB、OPERATING_ENTITY + ENTITY_UNDERWRITING、LOCAL_PARTNER + PARTNER_DUE_DILIGENCE。
- `t_role_qualification` 写入：一个本地事务内插入决定 → 无行时插入 version 1、有行时 `WHERE qualification_id=? AND version=?` 条件切换（影响行数 ≠ 1 或 `uk_role_qualification` 冲突即 409 `QUALIFICATION_CHANGED`）→ 追加 `t_qualification_state_change`（`aggregate_version` = 新版本，`trigger_id` = 决定 ID）。本期不安排持续复审：APPROVED 为 `NOT_REQUIRED`，REJECTED / REVOKED 为 `COMPLETED`，`review_frequency_code` 与 `next_review_time` 置空（满足 `ck_role_qual_review`）；撤销把 `valid_to` 置为撤销时间。
- 入驻审核：O08 通过在同一事务为主案件 upsert CLIENT 资格（`trigger_type=OPERATIONS_REVIEW`）；O09 拒绝仅把已有 APPROVED 资格切换为 REJECTED；RELATED_PERSON 子案件不投影。存量已通过的 Client 由回填脚本建立（`trigger_type=BACKFILL`，`qualification_id = change_id = decision_id`）。
- 判定（`POST /compliance/v1/qualifications/evaluations`、Admin 列表的 `effectiveStatus`）按查询时间实时计算 `EXPIRED`（APPROVED 且 `valid_to ≤ now`，或 status=EXPIRED）与 `NOT_YET_VALID`（`valid_from > now`），不依赖定时任务；资格合格的 Client 再按 `idx_settle_control_role` 只读检查 ACTIVE 且在生效期的 `SETTLEMENT_BLOCK`。
- 表数不变（25）。
