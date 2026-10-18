# Client & Contract 57 表物理模型实施计划

> **历史阶段提示：** 本文保留 57 表阶段及其此前 66 表基线、测试结果、Digest 与决策原文；当前基线已进一步收敛为 [36 表实时资格与 Retention 延期设计](./2026-08-23-cct-realtime-eligibility-retention-deferral-design.md)。不得据此恢复旧 Pricing/Share、Agreement/Legal、持久化 Eligibility 或 Retention 结构。

状态：历史 57 表阶段实施记录，已被当前 55 表模型取代；本文仅保留执行证据，其中 Ownership Path 内容不代表当前能力。当前设计见 [物理建模决策](../../paytech/client-master/docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md)；PDManer 4.9.3 应用内检查待人工确认；Migration、Java 与数据库实现未授权

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Client & Contract 权威逻辑目录、PDManer 生成链和跨系统引用从已验证的 66 表基线升级为已确认的 57 表平衡型模型。

**Architecture:** `agent-docs/database/CLIENT_CONTRACT_DATABASE.md` 继续作为生成器可解析的权威表目录；JavaScript 元数据只补充物理类型、中文说明、闭合值域、空值规则、关系和图组。先让契约测试对旧模型失败，再同步文档、生成器和元数据，最后只通过生成器重建 `.pdma.json`。Eligibility Wire 字段名保持不变，Application、Consumer Legal 和 Eligibility 只收敛内部物理结构。

**Tech Stack:** Markdown、Node.js `node:test`、JavaScript ESM、PDManer 4.9.3 JSON、Vite/Vinext requirements site

**Spec:** `agent-docs/plans/2026-08-21-cct-model-slimming-design.md`

## Global Constraints

- 精确生成 57 张且只有 57 张 CCT 表，目录分组为 `9+8+10+6+4+4+4+4+2+2+4`。
- 不删除 Assignment、Policy、Pricing/Share、Business Change History、Compensation、Idempotency 或 Retention 能力。
- Source Type 只允许 `PLATFORM/CLIENT/PARTNER`；`DIRECT` 只能出现在禁止使用该旧值的说明中。
- 删除全部 `current_version_id` 和普通业务表机械 `version`；仅 `compensation_event.lock_version` 与 `retention_resource_control.lock_version` 保留独立技术锁。
- 保留 `version_no/source_version/evaluation_version/vault_record_version/hash_schema_version/schema_version` 的业务或协议语义。
- CCT-I10/I13 继续使用 `eligibility_id/eligibility_history_id` Wire 字段，分别映射 `client_id/evaluation_id`。
- CCT-I13 继续返回 Client Fee 与 Partner Share Component `canonical_hash`。
- 生成的 `client-contract.pdma.json` 只能由生成器写入；共享模板必须保持 SHA-256 `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- 本阶段不创建 Migration、DO、Mapper、Repository、Java 模块、数据库或运行环境。
- 当前 Hash 门禁使用固定 Fixture 与 Node 验证；未来 Java 实现必须复用同一 Fixture，本阶段不为跨语言验证新建 Java 工程。
- 保留用户现有脏工作树；不提交、不建分支、不发布。

---

### Task 1: 先把 57 表目标写成失败的模型契约

**Files:**
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`
- Test: `scripts/pdmaner/client-contract-model.test.mjs`

**Interfaces:**
- Consumes: 当前 66 表 `.pdma.json` 基线和已确认设计文档。
- Produces: 57 表名称、字段、唯一键、关系、值域、空值、Hash 和锁边界的可执行目标契约。

- [x] **Step 1: 将 `expectedTables` 改为精确 57 表集合**

```js
const expectedTables = [
  "agreement", "agreement_acceptance", "agreement_document_ref",
  "agreement_scope_binding", "agreement_version", "agreement_version_party",
  "application_document", "assignment_policy_binding",
  "business_change_record", "business_change_version", "client",
  "client_application", "client_business_activity", "client_business_profile",
  "client_eligibility_evaluation", "client_entity_affiliation",
  "client_entity_assignment", "client_entity_assignment_ver",
  "client_fee_component", "client_pricing_plan", "client_pricing_scope",
  "client_pricing_version", "command_idempotency", "compensation_event",
  "consumer_legal_document", "consumer_legal_document_version",
  "eligibility_dimension_evaluation", "eligibility_source_evaluation",
  "eligibility_source_fact", "entity_assignment_policy",
  "entity_assignment_policy_ver", "entity_assignment_rule",
  "entity_source_ownership_path", "entity_source_path_edge",
  "entity_source_relation", "individual_profile", "local_partner",
  "operating_entity", "operating_entity_application", "organization_profile",
  "partner_share_component", "partner_share_plan", "partner_share_scope",
  "partner_share_version", "party", "party_address", "party_contact",
  "party_identity_key", "party_profile", "party_profile_identifier",
  "party_relation", "retention_action_log", "retention_hold_projection",
  "retention_policy_projection", "retention_resource_control",
  "seller_disclosure", "seller_disclosure_version",
].sort();
```

- [x] **Step 2: 更新显示名、唯一键和精确字段契约**

新增显示名固定为：

```js
client_application: "客户申请",
application_document: "申请材料",
consumer_legal_document: "消费者法律文档主档",
consumer_legal_document_version: "消费者法律文档版本",
client_eligibility_evaluation: "客户交易资格评估",
eligibility_dimension_evaluation: "资格评估维度",
eligibility_source_evaluation: "资格评估来源",
```

关键唯一键固定为：

```js
client_application: ["application_no", "client_id,active_slot"],
operating_entity_application: ["application_no", "operating_entity_id,active_slot"],
application_document: [
  "client_application_id,subject_party_id,requirement_code,document_type,file_id",
  "operating_entity_application_id,subject_party_id,requirement_code,document_type,file_id",
],
agreement_version: ["agreement_id,version_no", "agreement_id,active_slot"],
agreement_document_ref: ["agreement_version_id,document_type,file_id"],
consumer_legal_document: [
  "document_type,document_code",
  "document_type,operating_entity_id,scope_key,country_code,locale",
],
consumer_legal_document_version: [
  "legal_document_id,version_no",
  "legal_document_id,active_slot",
],
seller_disclosure_version: ["disclosure_id,version_no", "disclosure_id,active_slot"],
client_eligibility_evaluation: ["client_id,evaluation_version", "client_id,active_slot"],
eligibility_dimension_evaluation: ["evaluation_id,dimension_code"],
eligibility_source_evaluation: [
  "dimension_evaluation_id,source_fact_row_id,applicability_type,applicability_id",
],
entity_source_ownership_path: ["path_hash"],
```

- [x] **Step 3: 新增针对精简决策的专用断言**

断言至少覆盖：

```js
assert.equal(model.entities.length, 57);
for (const removed of [
  "client_onboarding", "client_profile_change", "client_onboarding_doc",
  "operating_entity_app_doc", "consumer_terms", "consumer_terms_version",
  "refund_policy", "refund_policy_version", "privacy_notice",
  "privacy_notice_version", "client_eligibility",
  "client_eligibility_dimension", "client_eligibility_history",
  "eligibility_dimension_history", "eligibility_dimension_source",
  "eligibility_source_history",
]) {
  assert.equal(entities.has(removed), false);
}
```

再断言：Application 双父 FK XOR 说明、Risk Tuple 字段、OE 稳定 Role ID、七维 Eligibility、七张 Version Active Slot、正确 Path 方向、Component Hash 保留、被删除 `canonical_hash` 不存在、全部 `current_version_id/document_version/primary_flag/fallback_flag/path_version/path_length` 不存在。

为 Client/OE Submission、Client Fee Component、Partner Share Component 各保存一个固定 JCS JSON 与期望 SHA-256，并用 Node 测试实现验证；未来 Java 阶段必须读取同一向量。

- [x] **Step 4: 增加版本与锁字段白名单断言**

```js
const independentLocks = model.entities.flatMap((entity) =>
  entity.fields
    .filter((field) => field.defKey === "lock_version")
    .map(() => `${entity.defKey}.lock_version`),
).sort();
assert.deepEqual(independentLocks, [
  "compensation_event.lock_version",
  "retention_resource_control.lock_version",
]);
```

普通字段名严格等于 `version` 时直接失败；业务 `version_no`、来源/评估/Schema 版本不进入该禁用集合。

- [x] **Step 5: 运行测试并确认旧模型失败**

Run: `node --test scripts/pdmaner/client-contract-model.test.mjs`

Expected: FAIL，首要失败明确显示旧模型仍为 66 表或缺少 `client_application`，不能因测试语法错误失败。

- [x] **Step 6: 检查本任务差异**

Run: `git diff -- scripts/pdmaner/client-contract-model.test.mjs`

Expected: 只有目标契约变化；不修改生成模型或模板。

---

### Task 2: 将权威 CCT 逻辑目录改成完整 57 表模型

**Files:**
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/plans/2026-08-20-cct-row-history-design.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/COMPENSATION_EVENT_GUIDE.md`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`

**Interfaces:**
- Consumes: 57 表设计和 Task 1 的字段/唯一键契约。
- Produces: 生成器可解析的 57 行 Markdown 表目录、状态机、关系图、Hash 和并发权威说明。

- [x] **Step 1: 重写 CCT 表目录的四个收敛区域**

按设计文档逐字段替换：

- `client_onboarding + client_profile_change -> client_application`；
- 两张申请材料表 `-> application_document`；
- 三套 Consumer Legal Root/Version `-> consumer_legal_document + consumer_legal_document_version`；
- 七张 Eligibility Current/History `-> 四张 Evaluation/Fact`。

每个 Markdown 表格行必须列出完整字段、命名 `uk_*`、闭合状态、Active Slot、XOR、Hash 和有效区间规则，不能用“同上”代替。

- [x] **Step 2: 对未合并表执行字段精简**

删除全部普通 `version/current_version_id`；执行以下精确改名或删除：

```text
compensation_event.version -> lock_version
retention_resource_control.version -> lock_version
affiliation_version -> affiliation_revision_no
source_relation_version -> source_relation_revision_no
document_version -> 删除
primary_flag -> 删除
fallback_flag -> 删除
source_client_party_id/operating_entity_party_id/path_version/path_length -> 删除
```

删除无独立消费者的子行 `canonical_hash`，保留 Client Fee/Partner Share Component `canonical_hash`、Scope Hash、Relation Hash、文件 Hash 和 Eligibility Set Hash。

- [x] **Step 3: 更新状态机与事务语义**

固定：

- Profile `DRAFT -> FROZEN -> ACTIVE -> SUPERSEDED`；
- OE `PENDING -> ACTIVE -> SUSPENDED/CLOSED`；
- Application `DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED/REJECTED/WITHDRAWN`；
- Consumer Legal/Seller Disclosure Version `DRAFT/APPROVED/SCHEDULED/ACTIVE/SUPERSEDED/EXPIRED/WITHDRAWN`；
- Eligibility 七维与 Fail-Closed；
- Risk Case 通过 after-commit `LOCAL_HANDLER` 创建和幂等回填；
- 多聚合事务各 Root 独立 CAS/History/Event，以同一 Trigger ID 关联。

- [x] **Step 4: 更新 Mermaid 关系图与 CCT-I10/I13**

关系图必须只引用 57 表；Application Document 分别关联两个真实父表和 Party；Consumer Legal Version 关联统一 Root；Evaluation -> Dimension -> Source -> Fact。Ownership Path 固定 `related_party_id -> organization_party_id`。

CCT-I10/I13 保持 Wire 字段 `eligibility_id/eligibility_history_id`，说明分别映射 `client_id/evaluation_id`；Component 数组继续返回 `canonical_hash`。

三类 Consumer Legal Wire 字段继续分别使用 `consumer_terms_version_id/refund_policy_version_id/privacy_notice_version_id`，都映射统一物理 `legal_document_version_id`；既有 `affiliation_version/source_relation_version/binding_version` Wire 名称可保留兼容别名，但 CCT 物理列与内部 DTO 语义使用 `*_revision_no`。

- [x] **Step 5: 更新行级历史设计**

把旧的“结构化 Root `version`”替换为 Root `business_change_record.current_version` CAS；完整写明 Root/Child CREATE、UPDATE、DELETE、跳号、System Command、多聚合固定锁顺序和失败回滚。更新“继续保留”清单为新 Evaluation 表名，删除 `client_profile_change` 等旧表名。

- [x] **Step 6: 更新物理决策**

删除 Current Pointer 规则，补充七张 Active Slot Version、Application XOR、Generated `source_owner_key/scope_key` 的 PDManer 说明边界、不可变 Evaluation 的 `update_time=create_time` 规则和 57 表人工检查门禁。

- [x] **Step 7: 消除通用规范与 CCT 专项并发语义冲突**

`DATABASE_DESIGN_GUIDE.md` 明确：普通服务仍可使用结构化 Root `version`；CCT 已批准以 Root `business_change_record.current_version` 作为唯一 Expected Version/CAS Token，该专项规则不自动推广到其他五库。

`COMPENSATION_EVENT_GUIDE.md` 把技术 Claim 锁字段的推荐物理名明确为 `lock_version`；已有逻辑文档中的泛称 `version` 表示同一乐观锁语义，各服务进入物理建模时统一规范命名，不借此改动业务 `source_version`。

- [x] **Step 8: 验证目录可解析且测试保持红色**

Run: `node --test scripts/pdmaner/client-contract-model.test.mjs`

Expected: 目录解析不再报告表数或字段缺失；测试仍因旧生成模型/旧生成器缺少新表而失败。

---

### Task 3: 更新跨系统数据库引用与需求目录

**Files:**
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`

**Interfaces:**
- Consumes: Task 2 的 CCT-I10/I13、Revision 和 Risk Case 语义。
- Produces: 六系统一致的字段说明和不破坏 Wire 名称的需求/时序目录。

- [x] **Step 1: 更新全局数据库规范中的 CCT 特例**

把 CCT Row History 并发令牌、多聚合版本、`*_revision_no` 和 57 表现状写入总览；不把该特例推广成其他五库的默认版本策略。

- [x] **Step 2: 更新 Commerce 不可变快照说明**

保留 `cct_eligibility_id/cct_eligibility_history_id` 字段名，明确它们分别保存 CCT 的 Client ID 映射和 Evaluation ID；`cct_evaluation_version` 与三个 Hash 必须来自同一 Evaluation。把 `source_relation_version/affiliation_version` 的语义改为 Revision，并保持字段兼容说明。

- [x] **Step 3: 更新 Risk Checkout 与 Qualification 说明**

保持 Risk 的 Eligibility 快照字段名，修正 Evaluation 映射；明确 Client Application 的 `INDIVIDUAL_KYC/ORGANIZATION_KYB` 和 OE Application 的 `ENTITY_UNDERWRITING` Tuple，以及 `application_id + submission_content_hash` 幂等 Case 创建。

- [x] **Step 4: 同步 requirements 与全局序列**

更新 CCT 表数/申请模型、CCT-I10/I13 结果说明、Risk after-commit 调用顺序和 OE PENDING Role；不得改变六大系统边界、入口应用或 Client 不可选择 OE 的既定约束。

保留全部现有 Use Case/Interface/E2E ID，不自行新增 Client Application 提交接口或新的 E2E 编号；当前目录未覆盖的接口只作为后续需求缺口记录。

- [x] **Step 5: 运行需求站点测试**

Run: `npm --prefix requirements-site test`

Expected: build 成功，28 个或更新后的全部测试通过，0 fail。

---

### Task 4: 更新 PDManer 中文元数据与值域目录

**Files:**
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs`
- Test: `scripts/pdmaner/client-contract-model.test.mjs`

**Interfaces:**
- Consumes: Task 2 的精确表/字段目录。
- Produces: 每张目标表和每个新字段唯一、非空、可生成的中文名称/说明和值域。

- [x] **Step 1: 替换表显示名目录**

删除 16 个旧表键，新增 7 个合并表键，使 `TABLE_DISPLAY_NAMES` 与 57 表一一对应；所有显示名为中文且不重复。

- [x] **Step 2: 增删字段元数据**

新增至少以下字段：

```text
application_type, base_party_profile_id, target_party_profile_id,
base_business_profile_id, target_business_profile_id,
client_application_id, operating_entity_application_id,
subject_party_id, requirement_code, qualification_decision_id,
qualification_decision_version, qualification_type,
submission_hash_schema_version, submission_content_hash,
legal_document_id, legal_document_version_id, document_code,
evaluation_id, dimension_evaluation_id, source_evaluation_id,
reason_summary_hash, valid_until, lock_version,
affiliation_revision_no, source_relation_revision_no
```

删除只服务旧表/旧字段的 Override；同名字段在不同表含义不同时使用表级 Override。

- [x] **Step 3: 更新闭合枚举和值域来源**

至少增加：

```js
"client_application.application_type": ["ONBOARDING", "PROFILE_CHANGE"],
"client_application.qualification_type": ["INDIVIDUAL_KYC", "ORGANIZATION_KYB"],
"operating_entity_application.qualification_type": ["ENTITY_UNDERWRITING"],
"consumer_legal_document.document_type": ["CONSUMER_TERMS", "REFUND_POLICY", "PRIVACY_NOTICE"],
"consumer_legal_document.scope_type": ["ENTITY_DEFAULT", "CLIENT_OVERRIDE"],
"client_eligibility_evaluation.overall_status": ["READY", "NOT_READY", "RESTRICTED"],
"eligibility_dimension_evaluation.dimension_status": ["PASS", "FAIL", "UNKNOWN"],
```

为 `profile_status` 增加 `FROZEN`，把 `operating_entity.status` 改为 `PENDING/ACTIVE/SUSPENDED/CLOSED`，为三个新增 Version Slot 写 `status <-> active_slot` 条件说明。

- [x] **Step 4: 运行元数据完整性测试**

Run: `node --test --test-name-pattern="Chinese|description|enum|controlled|metadata" scripts/pdmaner/client-contract-model.test.mjs`

Expected: 仍可因旧模型结构失败，但不得出现缺少 Metadata、重复中文名或未登记值域的新错误。

---

### Task 5: 重构生成器并生成 57 表 PDManer

**Files:**
- Modify: `scripts/pdmaner/generate-client-contract-model.mjs`
- Modify: `client-contract-service/docs/database/client-contract.pdma.json`（仅由生成器写入）
- Test: `scripts/pdmaner/client-contract-model.test.mjs`

**Interfaces:**
- Consumes: Task 2 的 57 表 Markdown Catalog 和 Task 4 元数据。
- Produces: 57 表、关系/索引/图组完整且确定性生成的 PDManer JSON。

- [x] **Step 1: 消除重复表数硬编码**

删除 `EXPECTED_TABLE_COUNT = 66`。`parseClientContractCatalog` 将 Catalog Key Set 与 `TABLE_DISPLAY_NAMES` Key Set 做双向精确比较，`validateGeneratedModel` 将生成 Entity Key Set 与本次 Catalog 精确比较；关系数量直接等于关系配置集合，图覆盖从实际实体/关系推导。测试独立保留精确 57 表业务契约，不再以第二个数字常量间接代表目录。

- [x] **Step 2: 更新物理字段分类与空值规则**

删除 `path_length/document_version/version/primary_flag/fallback_flag/current_version_id` 的旧推断；增加 `lock_version/*_revision_no/submission_hash_schema_version` 类型。为 Application Base/Decision、双父 FK、Consumer Legal Override Client、有效期、Source Owner、可空金额和 `valid_until` 写表级 Nullability。

- [x] **Step 3: 更新唯一键、访问索引和本库关系**

删除九条 Current Pointer 关系和全部旧表关系；新增 Application/Profile/Document、统一 Consumer Legal、Evaluation 三层关系；Seller/Agreement/Legal Version 加 Active Slot 访问索引。所有本库 FK 字段必须由 PK 或左前缀索引覆盖，不创建 Risk/Platform 跨库 FK。

- [x] **Step 4: 更新 Diagram Groups**

角色与入驻、Consumer Legal、Eligibility 图组只使用新表；每张 57 表至少出现在一个图组，Application Document 的两个父关系和 Subject Party 端口可解析。

- [x] **Step 5: 生成模型**

Run: `node scripts/pdmaner/generate-client-contract-model.mjs`

Expected: 命令成功写入 `client-contract-service/docs/database/client-contract.pdma.json`；不得写入共享模板或真实连接信息。

- [x] **Step 6: 运行模型契约并修正到全绿**

Run: `node --test scripts/pdmaner/client-contract-model.test.mjs`

Expected: 全部测试通过，0 fail；输出明确包含 57 表目标，而不是旧的 66 表描述。

- [x] **Step 7: 验证确定性和模板不变**

Run: `node scripts/pdmaner/generate-client-contract-model.mjs --check`

Run: `shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json`

Expected: 模型 up to date；模板 Digest 仍为 `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。

---

### Task 6: 清理旧术语并完成全量门禁

**Files:**
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/plans/2026-08-20-cct-row-history-design.md`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/plans/2026-08-21-cct-model-slimming-design.md`
- Modify: `agent-docs/plans/2026-08-21-cct-model-slimming-implementation-plan.md`
- Inspect: all files changed in Tasks 1-5

**Interfaces:**
- Consumes: 完成的 57 表模型和全部文档。
- Produces: 可导航、无过期权威声明、具备验证证据的最终交付。

- [x] **Step 1: 更新导航和状态**

在数据库导航同时链接设计与实施计划；设计状态改为已实施并通过自动验证，实施计划勾选全部已执行步骤。PDManer 4.9.3 应用内检查仍标记为人工门禁，不得写成已通过。

- [x] **Step 2: 执行旧结构 Guard Scan**

Run:

```bash
rg -n "client_onboarding|client_profile_change|client_onboarding_doc|operating_entity_app_doc|consumer_terms_version|refund_policy_version|privacy_notice_version|client_eligibility_history|eligibility_dimension_history|eligibility_source_history|eligibility_dimension_source|current_version_id|document_version|primary_flag|fallback_flag|path_version|path_length" agent-docs scripts/pdmaner requirements-site/content client-contract-service/docs/database
```

Expected: 只允许设计/实施说明中的“删除或替换”历史语境；权威表目录、元数据、生成器、测试期望和 PDManer 实体中不得残留。

Run: `rg -n "\bDIRECT\b" agent-docs requirements-site/content scripts/pdmaner client-contract-service/docs/database`

Expected: 只命中“禁止使用 DIRECT”的规范说明。

- [x] **Step 3: 执行完整自动验证**

Run: `node --test scripts/pdmaner/client-contract-model.test.mjs`

Run: `node scripts/pdmaner/generate-client-contract-model.mjs --check`

Run: `node -e 'JSON.parse(require("node:fs").readFileSync("client-contract-service/docs/database/client-contract.pdma.json","utf8")); console.log("valid json")'`

Run: `npm --prefix requirements-site test`

Expected: 模型契约全部通过、生成器 up to date、JSON 可解析、需求站点全部测试通过。

- [x] **Step 4: 审查工作树范围**

Run: `git status --short`

Run: `git diff --check`

Expected: 无空白错误；不覆盖用户原有无关修改，不产生 Migration、Java、连接信息或环境文件。

- [x] **Step 5: 记录人工门禁**

最终交付明确说明：自动验证已完成；仍需用户在 PDManer 4.9.3 中打开 `client-contract.pdma.json` 执行应用内模型检查。该人工动作不阻塞本次文件交付，也不授权数据库实现。

## 最终证据

- 生成模型：57 张表、632 个字段；529 required、103 nullable；86 个唯一键、68 个访问索引、89 条本库关系；6 个 Diagram Groups、75 个 Table Cells、89 个 Relation Cells。
- `node --test scripts/pdmaner/client-contract-model.test.mjs`：退出码 0，63/63 pass、0 fail。
- `node scripts/pdmaner/generate-client-contract-model.mjs --check`：退出码 0，模型 up to date。
- JSON 解析：退出码 0，`valid json`。
- `npm --prefix requirements-site test`：退出码 0，build 成功，28/28 pass、0 fail。
- Structural Projection SHA-256：`50b1246134e4ea6472a38a5b6af96f02a93b074ba838ba7aa722db4e753320c1`。
- Model SHA-256：`dcbd750febc8c91dd55becdcb1406d00e2b0a60be308ab1b26b8f61efe62373d`。
- Template SHA-256：`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- 旧结构宽 Guard 的命中均已分类为历史删除/替换、Wire 兼容、负向测试、禁止恢复或其他系统合法字段；PDManer JSON 精确检查为 `oldTables=[]`、`oldFields=[]`。当前状态短语零命中，`DIRECT` 只存在于禁止性说明。
- 完整命令、退出码和逐文件变化见 `.superpowers/sdd/2026-08-21-cct-model-slimming-implementation-plan/task-6-report.md`。

PDManer 4.9.3 应用内模型检查仍待人工执行。上述自动证据不授权 Migration、Java/Mapper/Repository、Testcontainers、物理数据库、运行环境、其他五库模型或发布。
