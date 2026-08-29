# Risk Due Diligence 19-Table Simplification Implementation Plan

> **For agentic workers:** Execute task-by-task with strict RED → GREEN verification. Do not create a branch or commit; this workspace is intentionally shared with other Codex worktrees.

**Goal:** 将 Risk & Compliance 的 Due Diligence 图组从 22 表精简为 19 表，同时完整保留角色资格、关系冻结、持续复审、筛查处置和审计能力。

**Architecture:** 以 `t_due_diligence_case` 作为每次 Application、Continuous Review 或 Manual 尽调的业务 Root；关系事实并入不可变 `t_dd_case_subject`；持续复审配置冻结在 Program Version，当前调度状态保存在 `t_role_qualification`，每个到期周期直接创建新 Case。Case/Hit 内部版本事实按版本号确定最新记录，仅 `t_role_qualification.current_decision_id` 保留跨 Case 的当前资格投影指针。

**Tech Stack:** Markdown、Node.js ESM、`node:test`、PDManer 4.9.3 JSON、SHA-256、MySQL 8.0 物理约束语义。

**Scope:** 仅修改 Risk DD 逻辑文档、场景、生产 Manifest、Policy、独立测试 Oracle、生成器计数、物理决策和生成模型；必要时同步共享导航中的 Risk 统计。不得创建 Migration、Java 模块、物理数据库、运行环境、分支或 Git 提交。

## Frozen Decisions

- 删除 `t_dd_party_relation_snapshot`、`t_continuous_review_plan`、`t_continuous_review_run`；DD 图组固定为 19 表，Risk 总表数相应减少 3。
- `t_dd_case_subject` 新增来源关系坐标、持股比例和单 PRIMARY 占位；PRIMARY 与关联主体字段矩阵由 CHECK 契约约束。
- `t_dd_program_version.review_frequency_code` 冻结可选复审频率；`t_role_qualification` 保存频率、下次复审时间和 `NOT_REQUIRED/ACTIVE/PAUSED/COMPLETED` 调度状态。
- `t_due_diligence_case` 新增 `case_origin` 与可空 `scheduled_review_time`；Continuous Review 必须带调度时点，并由 Role + Qualification + Scheduled Time 唯一约束防重。
- 原 `source_system` 正名为有实际去重消费者的 `source_namespace`：Client/OE Application 分别固定为 `CCT_CLIENT_APPLICATION/CCT_OPERATING_ENTITY_APPLICATION`，内部 Case 固定为 `RISK_COMPLIANCE`。Application Case 由 `source_namespace + source_application_id + role_type + role_id + qualification_type` 唯一定位；命中后比较 Submission Hash，实现同 Hash 幂等、不同 Hash 冲突。该 Typed 五元键允许同一 Organization Application 派生主 KYB Case 和多个关联人 KYC Case，也能隔离两类 CCT Application 的相同数值 ID 与相同关联 Party，不增加 `application_kind` 或派生 Subject Key 字段。Program Version 与 Case 的 nullable Active Slot 必须和完整生命周期状态矩阵绑定。
- 删除 Case/Hit 的当前版本指针以及 Decision/Resolution 的自引用替代链；Case 内最新决定和 Hit 内最新处置分别由 `(case_id, decision_version)` 与 `(hit_id, resolution_version)` 的最大版本确定。
- 删除可由直接父记录推导的 `t_dd_evidence_ref.case_id`、`t_dd_check.case_id`、`t_screening_run.case_id` 及其关系和索引。
- 保留 `t_role_qualification.current_decision_id`，因为它表达跨多个 Case 的当前生效资格；保留 Case 与 Qualification 两类 State Change。
- 字段复核后进一步删除 11 个重复或无当前消费者的字段：材料模板 `validity_days`、筛查模板 `frequency_days`、Case 的可由 Qualification 推导的 `case_type`、Requirement 的 Code/Type、Evidence 的 Document Type/Version、Check/Run 的重复 Type，以及 Snapshot 的 Vault Record Version/Metadata Hash；新增 Dependency/Evidence/Run 三个 CAS Version，Dependency/Evidence 改用双审计时间，净减 6 个字段。
- Evidence 用本库 `case_subject_id` 替换外部 `party_id`；来源 Profile/Relation 使用准确的 Version ID/Version No 命名；持股单位与 CCT 统一为百分比 0–100。
- Application 才保存 Submission Version/Hash；Continuous Review/Manual 必须为空。Case/Qualification/History 的并发或聚合版本统一使用 `BIGINT`，业务序号仍使用 `INT`。
- Qualification 词表与 CCT Risk Tuple 对齐：Client 使用 `INDIVIDUAL_KYC/ORGANIZATION_KYB`，Local Partner 使用 `PARTNER_DUE_DILIGENCE`，Operating Entity 使用 `ENTITY_UNDERWRITING`，关联 Party 使用 `RELATED_PERSON_KYC`；Role + Qualification 决定 Case 流程，不再保存重复 `case_type`。

---

### Task 1: 建立独立 RED 模型契约

**Files:**

- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`

- [x] 将测试侧 DD 表清单改为 19 个字面量表名，并把三个删除表加入禁止表清单。
- [x] 以字面量冻结 Program Version、Case、Case Subject、Role Qualification 的新关键字段。
- [x] 新增领域行为测试：Continuous Review Case 的字段/唯一键、Case Subject 单 PRIMARY/关系快照字段、冗余父字段和版本指针缺席。
- [x] 暂不更新生成模型摘要，运行 `node --test scripts/pdmaner/risk-compliance-model.test.mjs`，确认仅因旧 22 表模型不满足新契约而失败。

### Task 2: 实现 19 表生产 Manifest 与 CHECK Policy

**Files:**

- Modify: `scripts/pdmaner/risk/manifests/01-due-diligence.mjs`
- Modify: `scripts/pdmaner/risk/manifest.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk/generate-risk-compliance-model.mjs`

- [x] 删除三个表及所有引用；将 Risk/Diagram 精确表数改为 85。
- [x] 增加复审配置、Case Origin/调度时点、Case Subject 关系字段和到期扫描/防重索引。
- [x] 删除七个冗余父/当前/替代字段及对应索引、Correlation。
- [x] 用 CHECK 契约冻结 Case Origin 来源矩阵、Subject PRIMARY/关系字段矩阵、持股比例范围和 Role Review 调度矩阵。
- [x] 运行 Generator，记录 19 表首轮精简统计：85 / 939 / 111 / 81 / 130 / 11 / 8；字段复核与 CCT Tuple 对齐后最终统计更新为 85 / 933 / 111 / 82 / 131 / 11 / 8。

### Task 3: 让独立 Oracle GREEN

**Files:**

- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `risk-compliance-service/docs/database/risk-compliance.pdma.json` (generated)

- [x] 手工核对 Generator 输出统计后更新测试侧字面量统计，禁止从生产 Manifest 动态推导。
- [x] 更新生成模型摘要与结构摘要 SHA-256。
- [x] 运行 `node --test scripts/pdmaner/risk-compliance-model.test.mjs`，直到所有结构与领域契约通过。

### Task 4: 同步逻辑、场景与物理决策

**Files:**

- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify only when Risk statistics are present: `AGENTS.md`, `agent-docs/README.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/DATABASE_DESIGN_GUIDE.md`, `agent-docs/database/README.md`

- [x] 将 DD 关系图、表目录、生命周期和持续复审流程改为 19 表模型。
- [x] 明确 CAS + 唯一 Review Cycle 约束：调度器以 `role_qualification.version/next_review_time` 条件更新，在同一本地事务创建 Case 并推进下次复审时间。
- [x] 更新物理统计、Diagram Group 表数、CHECK 数量、哈希和人工评审说明。
- [x] 保留 RCO-01/RCO-03/RCO-I01 的既有业务能力与外部接口语义；无模型字段引用时不改需求目录。

### Task 5: 回归、冲突审计与交付检查

- [x] Run: `node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check`
- [x] Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs`
- [x] Run: `npm --prefix requirements-site test`
- [x] 使用 `rg` 检查三个删除表、旧 22 表计数、旧 Risk 总计和已删除字段的陈旧引用；历史实施记录可保留但必须标注已被本决策取代。
- [x] 对比另一 Codex worktree 的共享文件；若出现同文件改动，先做语义 diff，再只合并非冲突的 Risk 段落。
- [x] 进行独立审查并修复发现的问题；PDManer 4.9.3 应用内人工评审继续作为未自动替代的最终门禁。

### Task 6: DD 字段级二次精简与约束补强

**Files:**

- Modify: `scripts/pdmaner/risk/manifests/01-due-diligence.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk/generate-risk-compliance-model.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`

- [x] 先写独立 Oracle，确认旧 939 字段模型因字段、类型、关系、索引和 CHECK 契约不满足而 RED。
- [x] 删除 11 个重复/无消费者字段，增加 5 个必要 CAS/审计字段，将 Evidence 关系落到 Case Subject，删除可推导的 Case Type，并生成 933 字段模型。
- [x] 补齐 Program 批准、Case Tuple/Origin/状态时间、版本正数、Screening 时间、历史首行、Decision Source 与 Review Status 等单行 CHECK；跨行不变量保留 Processor/Testcontainers 责任。
- [x] 更新测试侧精确统计、Model/Structural/CHECK Policy 三类 SHA-256，并验证模型测试 17/17 通过。
