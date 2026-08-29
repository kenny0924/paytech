# Risk Dispute Balanced Database Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 保留 Risk Dispute 七表边界，将重复字段精确收敛到 84 字段，并把保证金许可、Request 生命周期、Outcome 幂等、更正链和同案归属冻结为可验证的物理契约。

**Architecture:** Case 是并发与当前指针聚合根；Package/Ref 冻结证据内容，Submission Request 隔离 Payment 调用生命周期，Outcome 与 Shared-PK Liability 分别保存最终事实和内部责任判断。单行条件使用 Null-safe MySQL CHECK，同案归属使用复合外键契约，连续版本和原子写集使用 Risk 跨行契约。

**Tech Stack:** Node.js ESM、`node:test`、Risk Manifest DSL、PDManer 4.9.3 确定性生成器、Markdown requirements catalog。

**Spec:** `agent-docs/plans/2026-08-28-risk-dispute-balanced-optimization-design.md`

## Global Constraints

- Dispute 必须保持恰好 7 张表；Outcome 与 Liability 不合表。
- 删除 `submission_deadline`、`document_version`、`request_hash`、`liability_id`、Liability `release_id`。
- Liability 必须以 `outcome_id` 作为 Shared Primary Key，并新增非空 `security_deposit_use_allowed`。该物理名满足 32 字符上限，语义中的 Client 限定由 CHECK/跨行契约表达。
- `DisputeLost/DisputeOutcomeCorrected` 必须显式携带保证金许可；Finance 不得从承担方或原因推导。
- Client 沉默、Deadline 到期或未提交证据不得自动生成 `LOST`。
- 保留当前 Limit、Settlement Eligibility、Decision Engine、DD 和 compensation-event 契约。
- Finance 冻结期间不修改 requirements、global sequence、对应测试/组件或六份共享入口。
- 最终统计、CHECK 数和摘要只从生成器读取，不手算。
- 不 commit、不打开 PDManer GUI、不连接数据库、不发布或部署。

## Risk 专属实施检查点（2026-08-28）

Tasks 1–6 已完成。生成器实算结果为：

```text
71 tables / 865 fields / 106 unique indexes / 74 access indexes
111 local relations / 9 one-to-one relations / 8 diagram groups / 83 CHECK contracts
Dispute: 7 tables / 84 fields / 15 unique indexes / 6 access indexes / 11 relations / 2 one-to-one relations
Model SHA-256:      c8ffcde849af80c57ffa54e0cbb7b063bec3aa9c8796f95adde1be1f09b46e07
Structural SHA-256: b8d50ca73156277e5b7284df1afb365b51432e8d34d69a123b19bf5357cb30e9
CHECK SHA-256:      8fcf719717efd47b0ceeb8e69d51b2a0c1535e4d0dd1ec0ed3de809762160efa
```

Risk 全量自动门禁为 `165/165 PASS`，生成器 `--check` 通过。Finance 明确解冻后，Task 7 已在主目录最新版本上完成逐段共享合并；Requirements build + `98/98 PASS` 与 ESLint 均通过，并保留 Finance `84/1100/145/155/160/22/6` 当前基线。

---

### Task 1: 冻结 Dispute 专项失败门禁

**Files:**
- Create: `scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs`
- Test: `scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs`

**Interfaces:**
- Consumes: `07-dispute.mjs`、`model-policy.mjs`、生成 PDManer 模型。
- Produces: 七表、精确字段、Shared PK、索引、关系、CHECK 与复合外键的独立失败门禁。

- [x] **Step 1: 写入字段与 Shared-PK 红测**

断言五个字段不存在，Liability 字段必须包含：

```text
outcome_id
decision_evaluation_id
action_effect_id
recommended_principal_bearer
principal_bearer
provider_fee_bearer
platform_error_flag
provider_recovery_required
security_deposit_use_allowed
override_reason_code
reason_code
decided_by
decided_time
create_time
```

并断言 `outcome_id.primaryKey=true`、没有 `uk_dispute_liability`、没有 Release 索引/关系、仍保留 Outcome 1:1 关系。

- [x] **Step 2: 写入状态、来源和完整性红测**

断言：

```text
Case status 不含 EVIDENCE_READY
Evidence UK = package_id,evidence_type,file_id
Outcome source enum = PROVIDER_ACTION,CLIENT_DISPOSITION,OPERATIONS_DISPOSITION,OPERATIONS_CORRECTION
Outcome source tuple 有唯一键
supersedes tuple 不允许分叉
Request CHECK 同时约束 status/slot/time/action tuple
Liability 行内 CHECK 约束许可只能用于 CLIENT；跨行契约再约束对应 Outcome=LOST
```

- [x] **Step 3: 运行 RED**

Run:

```bash
node --test scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs
```

Expected: FAIL，且失败原因是旧字段、旧主键/索引或缺失新契约，不是语法/导入错误。

### Task 2: 实现七表字段、索引与关系优化

**Files:**
- Modify: `scripts/pdmaner/risk/manifests/07-dispute.mjs`
- Test: `scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs`

**Interfaces:**
- Consumes: Task 1 的字段、索引和关系门禁。
- Produces: 精确 84 字段的七表 Manifest、Shared-PK Liability 和来源幂等键。

- [x] **Step 1: 删除重复字段并建立 Shared PK**

使用：

```javascript
pk("outcome_id", "争议结果", "责任决定与争议结果共享的稳定业务标识")
```

删除 Liability 独立 ID、Release 字段/索引/关系以及三个证据/请求重复字段。

- [x] **Step 2: 加入显式保证金许可和来源闭集**

新增非空 Boolean；将 Outcome 来源固定为四种已批准类型，并新增：

```text
uk_dispute_out_source(decision_source_type,decision_source_id,source_version)
```

- [x] **Step 3: 收紧证据和更正索引**

将 Evidence 唯一键改为 `(package_id,evidence_type,file_id)`；保留 Provider Action 导航字段和索引，避免把 Operations Correction 来源与促成更正的 Provider Action 混为同一坐标。为三条同案复合外键建立精确键：

```text
uk_dispute_package_identity(package_id,dispute_case_id)
uk_dispute_outcome_identity(outcome_id,dispute_case_id)
uk_dispute_outcome_ptr(current_outcome_id,dispute_case_id)
idx_dispute_req_package(package_id,dispute_case_id)
uk_dispute_out_prev(supersedes_outcome_id,dispute_case_id)
```

最后一条同时替代旧访问索引并防止更正链分叉。

- [x] **Step 4: 运行字段 GREEN**

Run:

```bash
node --test scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs
```

Expected: Manifest 字段/索引/关系用例 PASS；Policy、生成模型用例仍可因后续任务未完成而 FAIL。

### Task 3: 实现 Null-safe CHECK 与聚合跨行契约

**Files:**
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk/generate-risk-compliance-model.mjs`
- Create: `scripts/pdmaner/risk/dispute-aggregate-validator.mjs`
- Create: `scripts/pdmaner/risk-dispute-aggregate-contract.test.mjs`
- Test: `scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs`

**Interfaces:**
- Consumes: Task 2 的字段和索引。
- Produces: MySQL 8.4 可执行 CHECK、三条同案复合 FK 元数据及不可误读的聚合写入契约。

- [x] **Step 1: 替换 Request CHECK**

显式使用 `IS NULL/IS NOT NULL`，覆盖活动/终态 Slot、时间、Action ID/Version 成对、版本正值以及各 Status 允许的 Action 分支，防止 MySQL `UNKNOWN` 放行。

- [x] **Step 2: 增加 Case/State/Outcome/Liability CHECK**

覆盖：

```text
Case 当前 Pointer 与非终态/结果态矩阵
State Change 首事件及所有批准状态边
Outcome Version/Supersedes 单行矩阵
Outcome 来源类型与 Provider Action 矩阵
Liability 推荐值覆盖原因
Liability 规则来源必须同时保存 Evaluation/Effect，且 Effect 属于同一 Evaluation 并为 REAL/APPLIED/LOCAL_SYNC/SUCCEEDED；人工处置必须保存 decided_by
Liability 行内许可只允许 CLIENT；对应 Outcome 必须为 LOST 由跨行契约断言
```

- [x] **Step 3: 冻结复合外键和跨行写集**

复用 Platform Support 的 `COMPOSITE_FK_CONTRACTS` 持久化方式，将三条同案复合外键写入生成实体 `properties.compositeForeignKeys`。跨行契约另行冻结链尾、连续版本、Outcome/Liability 恰好性、Case/State Version、首次结果与 V1 Outcome、Trigger Type+ID 来源坐标、Operations Correction 顺序及 Rule Effect/Evaluation 授权链；不得把这些伪装成单行 CHECK。设计期纯 JS Validator 用真实行矩阵验证 Provider LOST、Client/Operations 明确认责、Operations Correction、Timer/沉默拒绝、交换 Trigger ID、Effect/Evaluation 错配和保证金许可反例；未来 Java/MySQL 实施仍须用 Testcontainers 重证。

- [x] **Step 4: 运行 Policy GREEN**

Run:

```bash
node --test scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs \
  scripts/pdmaner/risk-dispute-aggregate-contract.test.mjs
```

Expected: Manifest/Policy/复合外键元数据用例 PASS；生成模型摘要可等待 Task 5。

### Task 4: 冻结 Evidence Package Canonical Hash

**Files:**
- Create: `scripts/pdmaner/risk/dispute-evidence-package-canonicalizer.mjs`
- Create: `scripts/pdmaner/risk-dispute-evidence-package-canonicalizer.test.mjs`

**Interfaces:**
- Consumes: `dispute_case_id`、`version_no` 和 Evidence Ref 的 `evidence_type/file_id/content_hash`。
- Produces: `CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1` 的闭合 canonical bytes 与 SHA-256。

- [x] **Step 1: 写 Canonicalizer RED**

用以下手工固定 JSON 与 SHA-256 断言：输入顺序不影响输出；字段缺失/未知、稀疏数组、非法 ID/Version/Hash、重复 `evidence_type+file_id` 均拒绝；调用方数组不得被修改。

```text
canonical = {"disputeCaseId":"720001","evidence":[{"contentHash":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","evidenceType":"RECEIPT","fileId":"710101"}],"schema":"CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1","versionNo":1}
sha256 = eeb0a7cb32c7aabadb3c30f57caa7ba3ec05cf82de2e633aafe4c1e4eeada177
```

- [x] **Step 2: 运行 RED**

Run:

```bash
node --test scripts/pdmaner/risk-dispute-evidence-package-canonicalizer.test.mjs
```

Expected: FAIL，原因是模块尚不存在。

- [x] **Step 3: 最小实现并运行 GREEN**

Canonical JSON 只包含：

```json
{
  "disputeCaseId": "720001",
  "evidence": [{"contentHash":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","evidenceType":"RECEIPT","fileId":"710101"}],
  "schema": "CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1",
  "versionNo": 1
}
```

Run:

```bash
node --test scripts/pdmaner/risk-dispute-evidence-package-canonicalizer.test.mjs
```

Expected: PASS。

### Task 5: 生成模型并冻结最终 Oracle

**Files:**
- Modify: `risk-compliance-service/docs/database/risk-compliance.pdma.json`（仅生成器写入）
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`
- Modify: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`

**Interfaces:**
- Consumes: Tasks 2–4 的完整 Manifest/Policy。
- Produces: 最终生成统计、Dispute 七表 Oracle、CHECK 数和独立摘要。

- [x] **Step 1: 运行生成器**

```bash
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs
```

- [x] **Step 2: 从生成结果读取统计和摘要**

更新 Oracle 的 totals、CHECK count、model/structural/check-policy digest；保留 71 表和 8 图组，字段/索引/关系必须使用生成器真实值。

- [x] **Step 3: 更新交叉 Fixture**

将 Decision Engine 测试中的 Liability fixture 改为 Shared PK，删除 Release，加入显式保证金许可；保留 `CR_SIMULATED_EFFECT_DISPUTE_LIABILITY` 的无真实副作用契约。

- [x] **Step 4: 运行模型 GREEN**

```bash
node --test scripts/pdmaner/risk-dispute-*.test.mjs \
  scripts/pdmaner/risk-compliance-model.test.mjs \
  scripts/pdmaner/risk-decision-engine-contract.test.mjs
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
```

Expected: PASS，且模型仍为 71 表、7 表 Dispute、8 图组。

### Task 6: 同步 Risk 专属权威文档与场景

**Files:**
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/plans/2026-08-28-risk-dispute-balanced-optimization-design.md`
- Modify: `agent-docs/plans/2026-08-28-risk-dispute-balanced-optimization-implementation-plan.md`

**Interfaces:**
- Consumes: Task 5 的真实统计与摘要。
- Produces: 当前七表关系图、字段清单、现实数据、状态/更正/保证金示例和实施记录。

- [x] **Step 1: 更新表清单与关系图**

删除五个旧字段，写明 Shared PK、复合同案归属、来源闭集、Request 矩阵、线性更正和 Explicit Security Deposit Permission。

- [x] **Step 2: 更新现实场景**

场景至少展示：

```text
Provider LOST -> Outcome V1 + Shared-PK Liability + deposit allowed=1
Client/Operations 明确认责 -> 无 Provider Action 的 LOST
Operations 核验 Provider 更正或执行人工更正 -> OPERATIONS_CORRECTION Outcome V2 supersedes V1，Finance 只追加补偿 Journal
UNKNOWN Request 保持 active_slot=1，权威收口后才允许下一 Request
```

- [x] **Step 3: 更新物理决策与当前统计**

只使用 Task 5 实算值；历史计划保持历史，不改写为当前模型。

- [x] **Step 4: 运行 Risk 文档 GREEN**

```bash
node --test scripts/pdmaner/risk-compliance-scenario-doc.test.mjs \
  scripts/pdmaner/risk-dispute-*.test.mjs
```

Expected: PASS。

### Task 7: 解冻后同步 Requirements 与共享入口

**Files:**
- Modify after Finance release: `requirements-site/content/requirements.ts`
- Modify after Finance release: `requirements-site/content/global-use-case-sequences.mjs`
- Modify after Finance release: `requirements-site/tests/domain-decisions.test.mjs`
- Modify after Finance release: `requirements-site/tests/global-use-case-sequences.test.mjs`
- Modify after Finance release: `requirements-site/tests/site-content.test.mjs`
- Modify after Finance release: `requirements-site/components/architecture-diagrams.tsx`
- Modify after Finance release: `AGENTS.md`
- Modify after Finance release: `README.md`
- Modify after Finance release: `agent-docs/README.md`
- Modify after Finance release: `agent-docs/PROJECT_CONTEXT.md`
- Modify after Finance release: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify after Finance release: `agent-docs/database/README.md`

**Interfaces:**
- Consumes: Finance 明确解冻后的主目录最新版本，以及 Task 5 的 Risk 最终统计。
- Produces: 显式保证金许可事件契约、当前 Risk 统计和新计划链接的一致共享入口。

- [x] **Step 1: 等待精确解冻并逐段语义合并**

不得整文件覆盖。保留 Finance 最新表数、手工出款和 Provider 对账语义，只替换 Risk 子串并追加本计划链接。

- [x] **Step 2: 加入 requirements 与架构图回归断言**

锁定 V1 Provider/Client/Operations 来源矩阵、V2+ `OPERATIONS_CORRECTION`、`trigger_id=decision_source_id`，以及 `DisputeLost/DisputeOutcomeCorrected` 明确携带 `security_deposit_use_allowed`。Finance 不得由 Client bearer 推导，也不得把 DisputeLost 画成或实现成消耗 PENDING/AVAILABLE/未结算余额；只有显式许可下的 Client Security Deposit 可使用，剩余缺口进入 Recovery Receivable。

- [x] **Step 3: 运行 Requirements GREEN**

```bash
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: PASS。

### Task 8: 全量门禁与独立审查

**Files:**
- Review: 本计划全部 changed files。

**Interfaces:**
- Consumes: Tasks 1–7 的完整增量。
- Produces: 最终 Risk 基线、验证证据和未完成的人工门禁说明。

- [x] **Step 1: 运行 Risk 全量**

```bash
node --test scripts/pdmaner/risk-*.test.mjs scripts/pdmaner/risk-compliance-*.test.mjs
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
```

- [x] **Step 2: 运行六模型和 Requirements 总门禁**

```bash
node --test scripts/pdmaner/*-model.test.mjs scripts/pdmaner/*-scenario-doc.test.mjs
npm --prefix requirements-site test
npm --prefix requirements-site run lint
git diff --check
```

- [x] **Step 3: 扫描旧字段和冲突标记**

```bash
rg -n "submission_deadline|document_version|request_hash|liability_id|idx_liability_release|decision_source_type=PROVIDER\\b" \
  scripts/pdmaner/risk risk-compliance-service/docs/database agent-docs/database/RISK_COMPLIANCE_DATABASE.md \
  agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md
rg -n "^(<<<<<<<|=======|>>>>>>>)" .
```

- [ ] **Step 4: 独立只读审查**

审查字段最小性、MySQL NULL 语义、复合 FK、跨行更正链、Finance 明确许可边界、并行文件保留和最终统计。PDManer 4.9.3 应用内视觉检查继续作为人工门禁，不由命令行冒充完成。
