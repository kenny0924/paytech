# CCT Agreement 与 Consumer Legal 精简实施计划

> **Commerce 后续决策：** 本文中的 Commerce Snapshot Hash 引用已被 [2026-08-24 Commerce 订单上下文精简设计](./2026-08-24-commerce-order-context-simplification-design.md) 取代；CCT Legal 所有权及不可变 File/Content Hash 结论仍有效。

> **后续决策提示：** 本文保留 48 表阶段的执行记录和当时验收值；Pricing/Partner Share 的八表结构、内部 Hash 和 Commercial Context V2 已被 [44 表 Commercial Term 当前设计](./2026-08-23-cct-commercial-term-consolidation-design.md) 取代。

状态：已实施并通过自动验证；PDManer 4.9.3 应用内检查待人工确认

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 CCT Agreement 与 Consumer Legal 从 10 张表精简为 7 张，并同步 Pricing/Share 合同引用、CCT-I04/I12/I13、Commerce/Finance 快照、需求目录、PDManer 生成链路和全部当前导航。

**Architecture:** 一行 `agreement` 表示一份独立法律合同，以 `agreement_signatory`、`agreement_document_ref`、`agreement_acceptance` 和 `agreement_operating_entity` 分别表达签约主体、文件、接受证据和 OE 覆盖；Consumer Legal 与 Seller Disclosure 各以一行表示一次可独立发布的内容。历史 As-Of 通过结构化签署/发布时间与半开有效区间解析，跨系统只冻结稳定 ID、文件校验值、Seller 展示字段及 `commercial_context_hash` V2，不再外发 Agreement 内部 Version/Binding/Hash。

**Tech Stack:** Markdown、TypeScript/MJS 需求目录、Node.js `node:test`、PDManer 4.9.3 JSON、确定性模型生成器、SHA-256。

**Spec:** `agent-docs/plans/2026-08-22-cct-agreement-legal-simplification-design.md`

**Status:** 已实施并通过自动验证；PDManer 4.9.3 应用内检查待人工确认。

## Global Constraints

- 最终 Agreement/Legal 只保留 `agreement`、`agreement_signatory`、`agreement_document_ref`、`agreement_acceptance`、`agreement_operating_entity`、`consumer_legal_document`、`seller_disclosure` 七张表，共 77 个字段。
- CCT 最终自动验收目标固定为 48 表、539 字段、446 个必填字段、93 个可空字段、72 个唯一索引、52 个访问索引、72 条本库关系、6 个 Diagram Groups、64 个 Table Cells 和 72 个 Relation Cells。
- 删除 `agreement_version`、`agreement_version_party`、`agreement_scope_binding`、`consumer_legal_document_version`、`seller_disclosure_version`，不得保留物理兼容表、影子字段或 Wire 兼容别名。
- 删除 `client_pricing_plan.agreement_id` 与 `partner_share_plan.agreement_id`；将两个商业 Version 的 `agreement_version_id` 原位改为 `agreement_id -> agreement.agreement_id`。
- 文件字节校验字段统一命名为 `content_hash`；不得引入 `file_content_hash`。Agreement/Legal 不保存业务 `content_hash/scope_hash/hash_schema_version/version_no/active_slot/binding_revision_no`。
- `commercial_context_hash` 保留，Canonical Domain 升级为 `CODE-MOR:CCT:COMMERCIAL_CONTEXT:2`；不得为七张表重新增加 Hash Schema 字段。
- 除 `business_change_version` 外，所有目标表继续显式包含非空 `create_time + update_time`；不可变行 INSERT 时二者相等，之后不得覆盖。
- 表显示名、字段显示名、字段说明和枚举说明使用自然中文，不加英文表名前缀，不使用“用于某表”一类无信息描述。
- 不手工编辑 `client-contract-service/docs/database/client-contract.pdma.json`；只能运行生成器重建。
- 共享模板必须保持 SHA-256 `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`，不得写入数据库连接或凭证。
- 本轮不创建 Java、Migration、DDL、物理数据库、运行环境或其他五个系统的 PDManer 文件。
- 保留工作区既有修改；不创建分支、不提交 Git commit、不发布站点。

---

### Task 1: 先锁定目标模型与需求契约（RED）

**Files:**
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`

**Interfaces:**
- Consumes: 已批准设计中的七表字段、索引、关系、状态机、CCT-I04/I12/I13 Proof 和下游快照字段。
- Produces: 48 表物理模型的关闭式测试清单，以及 Requirements/Sequence 不允许旧 Agreement/Legal Proof 的回归门禁。

- [x] **Step 1: 将模型测试的表、主键和完整字段 Manifest 改为目标结构**

在 `expectedTables` 中删除五张旧表并加入 `agreement_signatory`、`agreement_operating_entity`。目标七表字段必须逐字锁定为：

```js
const expectedAgreementLegalFields = Object.freeze({
  agreement: [
    "agreement_id", "agreement_no", "agreement_name", "agreement_type",
    "client_id", "local_partner_id", "status", "effective_from",
    "effective_to", "signed_time", "activated_time", "create_by",
    "create_time", "update_time",
  ],
  agreement_signatory: [
    "agreement_signatory_id", "agreement_id", "party_id", "party_profile_id",
    "signatory_role", "create_time", "update_time",
  ],
  agreement_document_ref: [
    "agreement_document_id", "agreement_id", "document_type", "file_id",
    "content_hash", "create_time", "update_time",
  ],
  agreement_acceptance: [
    "acceptance_id", "agreement_signatory_id", "acceptance_method",
    "accepted_by_actor_id", "accepted_time", "evidence_document_id",
    "acceptance_reference", "create_by", "create_time", "update_time",
  ],
  agreement_operating_entity: [
    "agreement_operating_entity_id", "agreement_id", "operating_entity_id",
    "create_time", "update_time",
  ],
  consumer_legal_document: [
    "legal_document_id", "document_code", "document_type",
    "operating_entity_id", "country_code", "locale", "content_file_id",
    "content_hash", "status", "effective_from", "effective_to",
    "approved_time", "published_time", "create_by", "create_time",
    "update_time",
  ],
  seller_disclosure: [
    "disclosure_id", "operating_entity_id", "country_code", "locale",
    "public_legal_name", "public_registration_number",
    "public_tax_registration_number", "public_address_text",
    "public_support_email", "public_support_phone", "status",
    "effective_from", "effective_to", "approved_time", "published_time",
    "create_by", "create_time", "update_time",
  ],
});
```

同时把主键改为 `agreement_document_id`、`agreement_signatory_id`、`agreement_operating_entity_id` 等上述首字段，并把 Pricing/Share 字段 Manifest 改为：

```js
client_pricing_plan: [
  "pricing_plan_id", "plan_code", "client_id", "status", "create_time", "update_time",
],
client_pricing_version: [
  "pricing_version_id", "pricing_plan_id", "agreement_id", "version_no", "status",
  "active_slot", "effective_from", "effective_to", "rounding_mode",
  "hash_schema_version", "content_hash", "create_time", "update_time",
],
partner_share_plan: [
  "share_plan_id", "plan_code", "local_partner_id", "status", "create_time", "update_time",
],
partner_share_version: [
  "share_version_id", "share_plan_id", "agreement_id", "version_no", "status",
  "active_slot", "effective_from", "effective_to", "rounding_mode",
  "hash_schema_version", "content_hash", "create_time", "update_time",
],
```

- [x] **Step 2: 锁定唯一键、访问索引、可空字段、枚举与关系**

目标唯一键为：

```js
agreement: ["agreement_no"],
agreement_signatory: ["agreement_id,party_id,signatory_role"],
agreement_document_ref: ["agreement_id,document_type,file_id"],
agreement_acceptance: ["agreement_signatory_id", "acceptance_reference"],
agreement_operating_entity: ["agreement_id,operating_entity_id"],
consumer_legal_document: [
  "document_code",
  "document_type,operating_entity_id,country_code,locale,effective_from",
],
seller_disclosure: ["operating_entity_id,country_code,locale,effective_from"],
```

目标普通访问索引字段顺序为：

```js
agreement: [
  ["client_id", "effective_from", "effective_to"],
  ["local_partner_id", "effective_from", "effective_to"],
],
agreement_signatory: [["party_id"], ["party_profile_id"]],
agreement_acceptance: [["evidence_document_id"]],
agreement_operating_entity: [["operating_entity_id"]],
consumer_legal_document: [[
  "operating_entity_id", "country_code", "locale", "document_type",
  "effective_from", "effective_to",
]],
seller_disclosure: [[
  "operating_entity_id", "country_code", "locale", "effective_from", "effective_to",
]],
```

精确可空字段必须等于以下 15 项；其余七表字段全部非空：

```js
[
  "agreement.client_id", "agreement.local_partner_id",
  "agreement.effective_from", "agreement.effective_to",
  "agreement.signed_time", "agreement.activated_time",
  "agreement_acceptance.accepted_by_actor_id",
  "consumer_legal_document.effective_to",
  "consumer_legal_document.approved_time",
  "consumer_legal_document.published_time",
  "seller_disclosure.public_tax_registration_number",
  "seller_disclosure.public_support_phone",
  "seller_disclosure.effective_to",
  "seller_disclosure.approved_time",
  "seller_disclosure.published_time",
]
```

关闭枚举固定为：

```js
"agreement.agreement_type": ["CLIENT", "PARTNER"],
"agreement.status": [
  "DRAFT", "PENDING_SIGNATURE", "SIGNED", "ACTIVE", "EXPIRED",
  "TERMINATED", "CANCELLED",
],
"agreement_signatory.signatory_role": [
  "CLIENT", "LOCAL_PARTNER", "PLATFORM_SIGNATORY", "OPERATING_ENTITY",
],
"agreement_document_ref.document_type": [
  "SIGNING_DOCUMENT", "ANNEX", "EXECUTED_DOCUMENT", "TERMINATION_NOTICE",
],
"agreement_acceptance.acceptance_method": [
  "E_SIGNATURE", "CLICK_ACCEPTANCE", "MANUAL_SIGNATURE",
],
"consumer_legal_document.document_type": [
  "CONSUMER_TERMS", "REFUND_POLICY", "PRIVACY_NOTICE",
],
"consumer_legal_document.status": [
  "DRAFT", "APPROVED", "ACTIVE", "EXPIRED", "WITHDRAWN",
],
"seller_disclosure.status": [
  "DRAFT", "APPROVED", "ACTIVE", "EXPIRED", "WITHDRAWN",
],
```

七表关系精确为 12 条：Agreement 到 Client/Local Partner 两条；Signatory 到 Agreement/Party/Party Profile 三条；Document 到 Agreement 一条；Acceptance 到 Signatory/Document 两条；Agreement OE 到 Agreement/OE 两条；Consumer Legal 与 Seller Disclosure 各到 OE 一条。Pricing/Share 删除两条 Plan→Agreement 关系，两个 Version 改为 `agreement_id -> agreement.agreement_id`。

- [x] **Step 3: 增加遗留结构拒绝断言和最终统计断言**

模型测试必须拒绝五张旧表，并按完整路径拒绝当前 Catalog 恢复以下旧字段语义；不得按全库字段名扫描，以免误伤其他领域仍合法的 `owner_id/version_no/content_hash`：

```js
const removedAgreementLegalTables = Object.freeze([
  "agreement_version",
  "agreement_version_party",
  "agreement_scope_binding",
  "consumer_legal_document_version",
  "seller_disclosure_version",
]);

const removedAgreementLegalFieldPaths = Object.freeze([
  "agreement_document_ref.document_id",
  "agreement_document_ref.agreement_version_id",
  "agreement_acceptance.agreement_version_party_id",
  "agreement_acceptance.agreement_version_id",
  "agreement_acceptance.party_id",
  "agreement_acceptance.party_role",
  "agreement_acceptance.actor_id",
  "agreement_acceptance.evidence_file_id",
  "agreement_acceptance.evidence_content_hash",
  "consumer_legal_document.scope_type",
  "consumer_legal_document.client_id",
  "consumer_legal_document.scope_key",
  "seller_disclosure.disclosure_version_id",
  "seller_disclosure.version_no",
  "seller_disclosure.active_slot",
  "seller_disclosure.approved_by",
  "seller_disclosure.approval_trigger_id",
  "seller_disclosure.public_support_contact",
  "seller_disclosure.content_hash",
  "client_pricing_plan.agreement_id",
  "partner_share_plan.agreement_id",
  "client_pricing_version.agreement_version_id",
  "partner_share_version.agreement_version_id",
]);
```

此外，在七张目标表的字段集合内统一拒绝 `version_no/active_slot/scope_hash/hash_schema_version/binding_revision_no`；该断言不得扩展到仍版本化的 Pricing/Share 表。把目标硬断言改为 48 表、539 字段、446 必填、93 可空、72 唯一索引、52 访问索引、72 关系、64 Table Cells、72 Relation Cells、26 个 `*_hash` 字段。把 Pending 标记改为 `PENDING_APPROVED_48_DIGEST`，在目标结构生成前不得写最终 Structural Projection Digest。

- [x] **Step 4: 为需求目录和全局时序增加回归测试**

在原始 Requirements 源测试和渲染测试中断言：

```js
assert.match(source, /client_agreement_id.*client_agreement_operating_entity_id/s);
assert.match(source, /consumer_terms_document_id.*refund_policy_document_id.*privacy_notice_document_id/s);
assert.match(source, /public_support_email.*public_support_phone/s);
assert.match(source, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:2/);
assert.doesNotMatch(source, /agreement_scope_binding_(?:id|version|hash)/);
assert.doesNotMatch(source, /consumer_terms_version_id|seller_disclosure_version_id/);
```

在 E2E-02 Sequence 测试中断言 CCT 返回 Agreement/Association ID、三个 Legal Document/File/Hash 和 Seller 展示字段，并断言后续 Risk/Finance 只消费同一不可变 Order Snapshot；所有 Sequence Label 不得出现 Binding Revision 或 Agreement/Seller Content Hash。

- [x] **Step 5: 运行目标测试并确认 RED 原因**

Run:

```bash
node --test scripts/pdmaner/client-contract-model.test.mjs
node --test requirements-site/tests/site-content.test.mjs requirements-site/tests/domain-decisions.test.mjs requirements-site/tests/global-use-case-sequences.test.mjs
```

Expected: 模型测试因当前仍是 51 表旧工件而失败；Requirements 测试因当前仍返回 Version/Binding/Hash 而失败。不得接受语法错误、路径错误或 Fixture 本身损坏作为有效 RED。

---

### Task 2: 更新 CCT 权威 Catalog 与业务语义

**Files:**
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/plans/2026-08-20-cct-row-history-design.md`

**Interfaces:**
- Consumes: Task 1 的完整字段、唯一键、状态与关系清单。
- Produces: `parseClientContractCatalog()` 可读取的 48 表权威 Catalog，以及生成器注释所需的完整业务约束。

- [x] **Step 1: 将 Section 3 Agreement/Legal 分组改为 `5 + 2` 表**

使用 Task 1 的七张完整字段清单，约束列必须显式写入九个命名唯一键：

```text
uk_agreement_no
uk_agreement_signatory
uk_agreement_doc_file
uk_agreement_accept_signatory
uk_agreement_accept_ref
uk_agreement_entity
uk_legal_doc_code
uk_legal_doc_scope_time
uk_seller_disclosure_time
```

表中文职责分别为“合同”“合同签约方”“合同文件引用”“合同签署记录”“合同覆盖经营主体”“消费者法律文档”“法律卖方披露”。字段说明不恢复 Root/Version、Client Override、Agreement 交易 Scope 或 Seller Content Hash。

- [x] **Step 2: 更新 Pricing/Share Catalog 的合同引用**

从两个 Plan 字段清单删除 `agreement_id`；把 `client_pricing_version.agreement_version_id` 和 `partner_share_version.agreement_version_id` 改为 `agreement_id`。约束文字必须写明 Agreement Type/Owner 匹配、每个 Scope OE 被 Agreement 覆盖、Partner Source Relation 匹配、Pricing/Share 有效区间不超出 Agreement，以及 Agreement 提前终止后新交易 Fail Closed。

- [x] **Step 3: 重写 Agreement/Legal 状态、As-Of、签署证据和替换事务**

当前文档必须明确：

```text
Agreement candidate:
signed_time <= t
AND activated_time <= t
AND effective_from <= t
AND (effective_to IS NULL OR t < effective_to)

Consumer Legal / Seller candidate:
published_time <= t
AND effective_from <= t
AND (effective_to IS NULL OR t < effective_to)
```

进入 `PENDING_SIGNATURE` 或 `APPROVED` 时执行区间冲突检查，进入 `SIGNED/ACTIVE` 时防御性复查；替换命令按稳定顺序锁定全部 Root，在同一本地事务收窄旧 `effective_to`、写 BCR/事件并冻结新记录。不得按当前 `status=ACTIVE` 排除历史 EXPIRED/TERMINATED/WITHDRAWN 行，也不得回溯收窄到 `operation_context_time` 之前。

签署规则必须逐项写明：唯一 Signing Document、Annex 多份、Executed Document 至多一份；CLICK 指向 Signing Document，E-Sign/Manual 指向 Executed Document；`accepted_by_actor_id` 是实际在线 Actor，`create_by` 是登记者；Email OTP 只是验证因子。

- [x] **Step 4: 重写 CCT-I04/I12/I13 与 `commercial_context_hash` V2**

CCT-I04/I13 Agreement Proof 固定为：

```text
client_agreement_id + client_agreement_operating_entity_id
partner_agreement_id + partner_agreement_operating_entity_id  # 仅 PARTNER 来源
```

CCT-I12/I13 三个 Legal 结果分别返回 `legal_document_id + content_file_id + content_hash`；Seller 返回 `disclosure_id`、OE/Country/Locale 和全部公开字段。V2 Hash 必须覆盖 Client/Partner/OE/As-Of、两组 Agreement/Association ID、三个 Legal/File/Hash 和 Seller 全部展示字段，同时继续覆盖未删除的 Profile、Classification、Assignment、Eligibility、Pricing/Share Proof。

- [x] **Step 5: 更新 ER 图、留存、BCR 与明确禁止项**

ER 图改为七表关系；`agreement` 是 Root，Signatory/Document/OE 是 Child，Acceptance 是追加式法律证据，Consumer/Seller 各自为 Root。新增 Acceptance 推进 Agreement Root BCR；所有不可变行保留 `create_time/update_time`。更新 Section 9/10 的当前表数和状态，但暂不写尚未生成的 Digest。

- [x] **Step 6: 同步当前行级历史设计**

删除 `agreement_scope_binding.binding_revision_no` 当前例外；把 Agreement Root/Child、Consumer Root、Seller Root 和 Acceptance 证据边界改成新结构；当前目标从 51 表改为 48 表。历史阶段数字保留为历史，不改写旧时间线。

- [x] **Step 7: 验证 Catalog 已触发预期生成差异**

Run:

```bash
node scripts/pdmaner/generate-client-contract-model.mjs --check
```

Expected: 在 Task 3 更新元数据前，命令因旧 Metadata/Relation Manifest 引用已删除字段而 Fail Closed；不能静默继续生成旧结构。

---

### Task 3: 更新字段元数据与确定性生成器

**Files:**
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs`
- Modify: `scripts/pdmaner/generate-client-contract-model.mjs`

**Interfaces:**
- Consumes: Task 2 的 48 表 Catalog。
- Produces: 能确定性生成七表模型、Pricing/Share 新 FK、中文元数据和 72 条关系的生成器。

- [x] **Step 1: 更新中文表名和字段元数据闭包**

目标表显示名固定为：

```js
agreement: "合同",
agreement_signatory: "合同签约方",
agreement_document_ref: "合同文件引用",
agreement_acceptance: "合同签署记录",
agreement_operating_entity: "合同覆盖经营主体",
consumer_legal_document: "消费者法律文档",
seller_disclosure: "法律卖方披露",
```

新增 `agreement_name`、`agreement_signatory_id`、`signatory_role`、`agreement_document_id`、`accepted_by_actor_id`、`evidence_document_id`、`acceptance_reference`、`agreement_operating_entity_id`、`approved_time`、`published_time`、`public_support_email`、`public_support_phone` 的自然中文名称与业务说明。删除只被旧表使用的字段 Metadata/Override/Value Source；保留 Pricing/Share 仍在使用的 `version_no/hash_schema_version/content_hash` 元数据。更新后 `FIELD_METADATA` 的唯一字段键必须精确为 244 个，并由测试验证 Catalog 中每个字段均恰好命中一份元数据。

- [x] **Step 2: 更新物理类型、长度与 Nullable Manifest**

`VARCHAR_LENGTH_OVERRIDES` 固定加入：

```js
"agreement.agreement_name": 255,
"agreement_acceptance.acceptance_reference": 128,
"consumer_legal_document.locale": 35,
"seller_disclosure.locale": 35,
"seller_disclosure.public_legal_name": 255,
"seller_disclosure.public_registration_number": 128,
"seller_disclosure.public_tax_registration_number": 64,
"seller_disclosure.public_address_text": 512,
"seller_disclosure.public_support_email": 254,
"seller_disclosure.public_support_phone": 32,
```

`content_hash` 继续推导为 `CHAR(64)`，全部 `*_time/effective_*` 推导为 `DATETIME(6)`，全部 `*_id/create_by` 为 `BIGINT`。Nullable 集合严格使用 Task 1 的 15 项，并删除全部旧 Agreement/Legal Nullable 路径。

- [x] **Step 3: 更新关闭枚举、开放值源与 Version/Slot 分类**

把 Task 1 的八组关闭枚举写入 `CLOSED_ENUM_VALUES`；从 `OPEN_CODE_SOURCES` 删除 Agreement Status/Party Role/Method 的旧开放目录，只保留 Consumer/Seller Country 与 Locale 等真正开放值源。从 `VERSION_TABLES`、`activeSlotVersions`、`versionContracts` 和 `CONDITIONAL_VALUE_RULES` 删除 Agreement/Consumer/Seller 旧 Version/Slot，仅保留 Pricing/Share 等仍有真实版本语义的领域。

- [x] **Step 4: 写入表级约束和文件 Hash 语义**

`TABLE_COMMENT_CLAUSES` 为七表写入设计文档的 Owner XOR、状态时间矩阵、半开区间、锁顺序、历史 As-Of、签约方匹配、文件基数、Acceptance Method Matrix、OE 覆盖冻结、Consumer/Seller 审批发布及替换事务。`FIELD_COMMENT_CLAUSES` 对不可变 Child/Acceptance 的 `update_time=create_time`、`content_hash` 文件 SHA-256、Actor/Recorder 区别和 Consumer Country 语义写精确中文说明。

Hash 测试契约只保留：

```js
"agreement_document_ref.content_hash": [
  true,
  null,
  "输入为 Platform Support 返回的合同文件 SHA-256，与 file_id 成组冻结。",
],
"consumer_legal_document.content_hash": [
  true,
  null,
  "输入为 Platform Support 返回的消费者法律文档 SHA-256，与 content_file_id 成组冻结。",
],
```

Seller Disclosure 不进入业务 Hash Manifest；`commercial_context_hash` V2 属于接口边界，不伪造成七表字段。

- [x] **Step 5: 支持显式命名的访问索引并写入八个索引**

保持旧数组格式兼容，同时让 `buildIndexes()` 接受以下对象格式：

```js
const access = Array.isArray(spec)
  ? { fields: spec, defKey: indexName("ix", table.defKey, spec) }
  : spec;
```

七表使用下列显式名称：

```js
agreement: [
  { defKey: "ix_agreement_client_time", fields: ["client_id", "effective_from", "effective_to"] },
  { defKey: "ix_agreement_partner_time", fields: ["local_partner_id", "effective_from", "effective_to"] },
],
agreement_signatory: [
  { defKey: "ix_agreement_party", fields: ["party_id"] },
  { defKey: "ix_agreement_profile", fields: ["party_profile_id"] },
],
agreement_acceptance: [
  { defKey: "ix_accept_evidence_doc", fields: ["evidence_document_id"] },
],
agreement_operating_entity: [
  { defKey: "ix_agreement_oe_entity", fields: ["operating_entity_id"] },
],
consumer_legal_document: [
  { defKey: "ix_legal_doc_resolve", fields: [
    "operating_entity_id", "country_code", "locale", "document_type",
    "effective_from", "effective_to",
  ] },
],
seller_disclosure: [
  { defKey: "ix_seller_disclosure_resolve", fields: [
    "operating_entity_id", "country_code", "locale", "effective_from", "effective_to",
  ] },
],
```

生成器继续拒绝超过 32 字符、引用未知字段或重复字段签名的索引。

- [x] **Step 6: 替换本库关系和 Diagram Groups**

`LOCAL_RELATIONS` 的七表部分精确替换为：

```js
["agreement", "client_id", "client", "client_id", "n"],
["agreement", "local_partner_id", "local_partner", "local_partner_id", "n"],
["agreement_signatory", "agreement_id", "agreement", "agreement_id", "n"],
["agreement_signatory", "party_id", "party", "party_id", "n"],
["agreement_signatory", "party_profile_id", "party_profile", "profile_id", "n"],
["agreement_document_ref", "agreement_id", "agreement", "agreement_id", "n"],
["agreement_acceptance", "agreement_signatory_id", "agreement_signatory", "agreement_signatory_id", "1"],
["agreement_acceptance", "evidence_document_id", "agreement_document_ref", "agreement_document_id", "n"],
["agreement_operating_entity", "agreement_id", "agreement", "agreement_id", "n"],
["agreement_operating_entity", "operating_entity_id", "operating_entity", "operating_entity_id", "n"],
["consumer_legal_document", "operating_entity_id", "operating_entity", "operating_entity_id", "n"],
["seller_disclosure", "operating_entity_id", "operating_entity", "operating_entity_id", "n"],
```

删除两个 Plan→Agreement 关系；两个商业 Version 改为 `agreement_id -> agreement.agreement_id`。`agreement_legal` Diagram 的 Primary Tables 只含七表，并自动带入 Party/Profile/Client/Local Partner/OE 五个 Anchor；`pricing_share` 不再带入 `agreement_version`。

- [x] **Step 7: 运行脚本语法与元数据闭包检查**

Run:

```bash
node --check scripts/pdmaner/client-contract-field-metadata.mjs
node --check scripts/pdmaner/generate-client-contract-model.mjs
node scripts/pdmaner/generate-client-contract-model.mjs --check
```

Expected: 两个语法检查通过；生成器能够完整构建目标内存模型，但 `--check` 报告现有 `.pdma.json` 已过期。

---

### Task 4: 重建 PDManer 并关闭物理模型契约（GREEN）

**Files:**
- Generate: `client-contract-service/docs/database/client-contract.pdma.json`
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`

**Interfaces:**
- Consumes: Task 3 的确定性生成器和 Task 1 的关闭式模型契约。
- Produces: 通过自动验证的 48 表 PDManer JSON、稳定 Structural Projection Digest 和实际 Model SHA-256。

- [x] **Step 1: 只通过生成器重建模型**

Run:

```bash
node scripts/pdmaner/generate-client-contract-model.mjs
```

Expected: 输出 `Generated .../client-contract.pdma.json`；共享模板字节不变。

- [x] **Step 2: 运行模型测试并只允许 Pending Digest 门禁失败**

Run:

```bash
node --test scripts/pdmaner/client-contract-model.test.mjs
```

Expected: 字段、Nullable、枚举、索引、关系、中文元数据、图组和统计断言均通过；唯一保留失败是 `PENDING_APPROVED_48_DIGEST` 尚未替换。

- [x] **Step 3: 计算并冻结 Structural Projection Digest**

先把 `expectedStructuralProjectionDigest` 临时设为以下 64 位无效比较值：

```text
0000000000000000000000000000000000000000000000000000000000000000
```

运行指定测试并从 `approved Structural Projection digest drift` 的 Assertion Diff 读取实际 64 位小写 Hex；立即将该实际值写回 `expectedStructuralProjectionDigest`，不得保留零值或 Pending 标记。

Run:

```bash
node --test --test-name-pattern="Structural Projection" scripts/pdmaner/client-contract-model.test.mjs
```

Expected: 写入实际 Digest 后，全部 Structural Projection 测试通过。

- [x] **Step 4: 验证精确统计**

Run:

```bash
node -e 'const fs=require("node:fs");const m=JSON.parse(fs.readFileSync("client-contract-service/docs/database/client-contract.pdma.json","utf8"));const e=m.entities;const cells=m.diagrams.flatMap(d=>d.canvasData.cells);const fields=e.flatMap(t=>t.fields);console.log(JSON.stringify({tables:e.length,fields:fields.length,required:fields.filter(f=>f.notNull).length,nullable:fields.filter(f=>!f.notNull).length,uniqueIndexes:e.flatMap(t=>t.indexes).filter(i=>i.unique).length,accessIndexes:e.flatMap(t=>t.indexes).filter(i=>!i.unique).length,relations:e.flatMap(t=>t.correlations).length,diagramGroups:m.diagrams.length,tableCells:cells.filter(c=>c.shape==="table").length,relationCells:cells.filter(c=>c.shape==="erdRelation").length},null,2))'
```

Expected:

```json
{
  "tables": 48,
  "fields": 539,
  "required": 446,
  "nullable": 93,
  "uniqueIndexes": 72,
  "accessIndexes": 52,
  "relations": 72,
  "diagramGroups": 6,
  "tableCells": 64,
  "relationCells": 72
}
```

- [x] **Step 5: 运行模型全量 GREEN 门禁并保存 Hash 证据**

Run:

```bash
node --test scripts/pdmaner/client-contract-model.test.mjs
node scripts/pdmaner/generate-client-contract-model.mjs --check
node -e 'JSON.parse(require("node:fs").readFileSync("client-contract-service/docs/database/client-contract.pdma.json","utf8")); console.log("client-contract PDManer JSON OK")'
shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json client-contract-service/docs/database/client-contract.pdma.json
```

Expected: 测试零失败、生成模型为最新、JSON 可解析、模板 Hash 保持固定；记录实际 Model SHA-256 供 Task 6 写入物理决策文档。

---

### Task 5: 同步 Requirements 与跨系统不可变快照

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Inspect: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`

**Interfaces:**
- Consumes: Task 2 的 CCT-I04/I12/I13 V2 Wire。
- Produces: Commerce、Finance、Risk、Platform Support 与全局用例使用同一套稳定 ID 和不可变证据字段。

- [x] **Step 1: 更新 Requirements 接口用例和事件语义**

`CCT-I04` 返回 Agreement/Association ID；`CCT-I12` 返回三个 `Document ID + File ID + Content Hash` 对象和结构化 Seller Disclosure；`CCT-I13` 返回同一 As-Of 的完整 V2 Bundle。`COM-03` 改为冻结稳定 Agreement/Association、三个 Legal/File/Hash、Seller 展示字段、Pricing/Share Proof 和 V2 Context Hash。

发布事件列表删除会暗示已签内容原位覆盖的 `AgreementChanged/PartnerAgreementChanged`，改为：

```text
AgreementActivated
AgreementStatusChanged
AgreementReplaced
```

新内容始终创建新 Agreement ID；`AgreementReplaced` 明确携带旧/新 Agreement ID，状态变化不伪装成内容更新。

- [x] **Step 2: 更新 E2E-02、结算、退款与消费者文档时序**

所有 CCT→Commerce/Finance Label 使用 Agreement/Association ID、三份 Legal/File/Hash、Seller 展示值和 `commercial_context_hash` V2。删除 `agreement_scope_binding_*`、Agreement Business Version/Content Hash、Consumer Legal Version ID、Seller Version/Hash。Consumer Document 请求只发送 Commerce 自己的 Order Snapshot Hash 与三份 Legal File Hash。

- [x] **Step 3: 更新 Commerce 三张不可变快照**

`order_snapshot` 与 `order_contract_snapshot` 的 Agreement 字段固定为：

```text
client_agreement_id
client_agreement_operating_entity_id
partner_agreement_id
partner_agreement_operating_entity_id
```

后两项仅 PARTNER Source 非空。`order_contract_snapshot` 三类文档字段固定为：

```text
consumer_terms_document_id
consumer_terms_file_id
consumer_terms_content_hash
refund_policy_document_id
refund_policy_file_id
refund_policy_content_hash
privacy_notice_document_id
privacy_notice_file_id
privacy_notice_content_hash
```

`order_seller_snapshot` 固定为 `seller_disclosure_id`、OE/Country/Locale、Legal Name、Registration Number、可空 Tax Registration Number、Address、Support Email、可空 Support Phone；删除 `seller_disclosure_content_hash`。全部字段进入 Commerce `snapshot_hash`，不得回查 CCT 当前值改写历史。

- [x] **Step 4: 更新 Finance、Risk 与 Platform Support**

`finance.economics_snapshot` 删除 Client/Partner Binding/Version/Content Hash，加入两组 Agreement/Association ID；保留 Pricing/Share Version/Scope/Content Hash、`cct_commercial_context_hash` 和现有经济计算字段。PAYMENT/REFUND/DISPUTE/COST_ADJUSTMENT 子快照继承同一四个合同证明字段，`calculation_hash` 覆盖它们。

Risk 的合同引用统一写为 `agreement_id`，确需证明 OE 时成组保存 `agreement_operating_entity_id`；不复制合同内容。Platform Support 的文档生成输入使用 Commerce Snapshot Hash 与三份 Legal File Hash。Payment 文档只做旧字段审计；没有命中时不增加 Agreement/Legal 物理列。

- [x] **Step 5: 运行 Requirements RED→GREEN 验证**

Run:

```bash
node --test requirements-site/tests/site-content.test.mjs requirements-site/tests/global-use-case-sequences.test.mjs
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: 原始 Catalog、全局时序、完整构建、渲染测试和 Lint 全部通过；页面不再渲染旧 Version/Binding/Hash 承诺。

---

### Task 6: 同步导航、物理决策、历史标记与最终证据

**Files:**
- Modify: `AGENTS.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/plans/2026-08-22-cct-agreement-legal-simplification-design.md`
- Modify: `agent-docs/plans/2026-08-22-cct-agreement-legal-simplification-implementation-plan.md`
- Modify: `agent-docs/plans/2026-08-22-cct-assignment-simplification-design.md`
- Modify: `agent-docs/plans/2026-08-22-cct-assignment-simplification-implementation-plan.md`
- Modify: `agent-docs/plans/2026-08-22-client-industry-mcc-design.md`
- Modify: `agent-docs/plans/2026-08-22-client-industry-mcc-implementation-plan.md`
- Modify: `agent-docs/plans/2026-08-21-cct-model-slimming-design.md`
- Modify: `agent-docs/plans/2026-08-21-cct-model-slimming-implementation-plan.md`

**Interfaces:**
- Consumes: Task 4 的实际 Structural/Model SHA-256、精确统计与 Task 5 的最终 Wire。
- Produces: Agent 可从导航读取的当前 48 表基线，以及不与历史阶段记录混淆的最终验收证据。

- [x] **Step 1: 更新当前导航和冻结状态**

在 `AGENTS.md`、项目上下文、Database Guide 与两个 README 中统一写入 48 表/539 字段/72 唯一索引/52 访问索引/72 关系；加入本设计和本实施计划链接。保留“仅 CCT PDManer 获授权、其他五套未授权、无 Java/Migration/环境、PDManer 4.9.3 应用内检查仍为人工门禁”。

- [x] **Step 2: 更新物理决策的最终自动证据**

`CLIENT_CONTRACT_PHYSICAL_DECISIONS.md` 必须写入：

```text
表 / 字段: 48 / 539
必填 / 可空: 446 / 93
唯一键 / 访问索引: 72 / 52
本库关系: 72
Diagram Groups: 6
Table Cells / Relation Cells: 64 / 72
Template SHA-256: 51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540
```

Structural Projection SHA-256 与 Model SHA-256 必须逐值使用 Task 4 新鲜命令输出。同步更新七表物理设计、文件 Hash、BCR、As-Of、Pricing/Share FK 和 Wire 说明；删除当前章节中的 Agreement Scope Revision、Consumer Override 和 Seller Version 语义。

- [x] **Step 3: 标记历史计划而不改写历史证据**

Assignment、Industry/MCC 和 57 表 Slimming 文档保留当时字段、测试数与 Digest，只在文首增加“该阶段的 51/576 或更早基线已被本次 48/539 Agreement/Legal 设计取代”的醒目标记和链接。`2026-08-20-cct-row-history-design.md` 属于当前横切规范，按 Task 2 更新正文，不只加历史标记。

- [x] **Step 4: 更新本设计和本计划状态**

设计文档状态改为“已实施并通过自动验证；PDManer 4.9.3 应用内检查待人工确认”。实施计划所有已执行步骤改为 `[x]`，状态使用相同表述；不得宣称人工 PDManer 检查已经完成。

- [x] **Step 5: 审计旧名只存在于历史或拒绝测试**

Run:

```bash
rg -n "agreement_version|agreement_version_party|agreement_scope_binding|consumer_legal_document_version|seller_disclosure_version|binding_revision_no|consumer_terms_version_id|seller_disclosure_content_hash|public_support_contact|file_content_hash" AGENTS.md agent-docs requirements-site/content scripts/pdmaner
```

Expected: 当前导航、当前数据库文档、Requirements、生成器和 Metadata 没有旧结构；命中只允许出现在明确标注的历史计划、已批准设计中的“删除旧项”说明，以及模型测试的拒绝清单。逐条检查，不使用全局机械替换清除历史证据。

- [x] **Step 6: 运行完整最终门禁**

Run:

```bash
node --check scripts/pdmaner/client-contract-field-metadata.mjs
node --check scripts/pdmaner/generate-client-contract-model.mjs
node --test scripts/pdmaner/client-contract-model.test.mjs
node scripts/pdmaner/generate-client-contract-model.mjs --check
node -e 'JSON.parse(require("node:fs").readFileSync("client-contract-service/docs/database/client-contract.pdma.json","utf8")); console.log("client-contract PDManer JSON OK")'
npm --prefix requirements-site test
npm --prefix requirements-site run lint
shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json client-contract-service/docs/database/client-contract.pdma.json
git diff --check
```

Expected: 全部自动命令退出码为 0；模板 Hash 固定，模型 Hash 与物理决策文档一致，PDManer JSON 可解析，生成器无漂移，Requirements 测试和 Lint 零失败。

- [x] **Step 7: 保留人工兼容性门禁**

最终交付明确记录：仍需用户在本机 PDManer 4.9.3 打开 `client-contract.pdma.json` 并运行应用内规范检查。该人工步骤未执行前只声明“自动验证通过”，不能声明 PDManer 应用内兼容性已确认。
