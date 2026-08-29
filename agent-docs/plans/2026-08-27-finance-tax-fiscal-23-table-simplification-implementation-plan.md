# Finance Tax And Fiscal 23-Table Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将 Finance Tax/Fiscal 主表从 27 张精简到 23 张，用版本行自身表达税务规则和税务申报的身份、版本与固定 Maker-Checker 结果，同时保留完整算税、财政凭证、申报、更正和缴税证据链。

**Architecture:** 删除 `t_tax_rule_version`、`t_tax_rule_approval`、`t_tax_return_ver`、`t_tax_return_approval`。`t_tax_rule` 变为一行一个规则版本，子规则和 Tax Line 直接引用该精确行；`t_tax_return` 变为一行一个申报版本，使用 `return_no + return_ver + amends_return_id` 表达申报系列和更正链，Return Line 与 Tax Payment 直接引用精确申报行。

**Tech Stack:** Node.js ESM、`node:test`、PDManer 4.9.3 JSON、Finance manifest/schema DSL、Markdown 权威文档。

**Spec:** 本会话已批准的 23 表方案；`agent-docs/database/FINANCE_DATABASE.md`、`agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`、`finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`。

**Status:** 2026-08-27 已完成；PDManer 4.9.3 应用内人工兼容检查仍是既有门禁。

## Global Constraints

- Tax/Fiscal 主表精确为 23 张；Finance 总表精确为 118 张。
- 最终模型预期为 1,465 字段、205 个唯一索引、188 个访问索引、205 条本库关系、38 条 1:1 关系、8 个 Diagram Groups。
- 保留 `t_tax_treatment`、`t_tax_rate`、`t_tax_evidence_rule`、Tax Snapshot/Line、Fiscal Record/Line、Tax Period、Liability Summary、Return Line、Tax Payment/Event。
- `t_tax_rule` 的 `rule_code + rule_ver` 唯一；离开 DRAFT 后 Header 和全部子规则冻结，税法变化创建新版本行。
- `t_tax_return` 的 `return_no + return_ver` 和 `tax_period_id + return_ver` 唯一；更正使用 `amends_return_id` 指向同申报系列的上一已提交版本，禁止覆盖历史版本。
- Rule/Return 固定双人审批直接保存在版本行；不恢复 Approval 表，不增加通用 Workflow 或 History。
- 保留业务 `rule_ver/return_ver` 与真实并发更新使用的技术 `version`，不增加 Current Pointer、Active Slot 或第二套发布指针。
- 不修改跨系统接口所有权，不创建 Java、Migration、物理数据库、部署环境或 Git commit。
- 采用 TDD：先让测试因旧四表和旧引用存在而失败，再修改 manifest、policy、场景、文档、生成模型和独立 Oracle。

---

### Task 1: 冻结 23 表目录和合并后字段契约

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: 当前确定性生成模型和现实场景解析器。
- Produces: 删除旧四表、Rule/Return 合并字段、引用改名和 118 表统计的失败保护。

- [x] **Step 1: 写 Finance Model 失败测试**

增加一个面向行为的结构测试，要求：

```js
assert.ok(entities.has("t_tax_rule"));
assert.ok(!entities.has("t_tax_rule_version"));
assert.ok(!entities.has("t_tax_rule_approval"));
assert.ok(entities.has("t_tax_return"));
assert.ok(!entities.has("t_tax_return_ver"));
assert.ok(!entities.has("t_tax_return_approval"));
```

并冻结：

- `t_tax_rule` 包含 `rule_code/rule_ver/seller_jurisdiction_id/buyer_jurisdiction_id/buyer_type/category_id/treatment_id/effective_from/effective_to/priority/content_hash/status/maker_id/checker_id/checker_decision/decision_time/version`，不含 `current_ver_id/tax_rule_id`；
- `t_place_of_supply_rule/t_tax_rate/t_tax_evidence_rule/t_tax_line` 使用 `tax_rule_id`，不含 `tax_rule_ver_id`；
- `t_tax_return` 包含 `return_no/return_ver/amends_return_id/tax_period_id/tax_registration_id/authority_id/currency/四个申报金额/content_hash/file_id/file_content_hash/maker_id/checker_id/checker_decision/decision_time/authority_ref/status/filed_time/version`，不含 `current_ver_id/tax_return_id/amends_ver_id`；
- `t_tax_return_line/t_tax_payment` 使用 `tax_return_id`，不含 `tax_return_ver_id`；
- 规则和申报生命周期 CHECK 绑定合并后的表。

- [x] **Step 2: 写现实场景失败测试**

要求场景覆盖 118 张表，且不出现被删除的四个表；Rule 示例直接包含版本和审批字段，Return 示例直接包含申报版本、更正链和审批字段，Return Line/Payment 使用 `tax_return_id`。

- [x] **Step 3: 运行测试确认 RED**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: FAIL，原因是旧四表、Current Pointer 和 `*_ver_id` 引用仍存在；不是语法或测试夹具错误。

### Task 2: 合并 Tax Rule 三表并收紧生命周期

**Files:**
- Modify: `scripts/pdmaner/finance/manifests/02-tax-economics-cost.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`

**Interfaces:**
- Consumes: 现有 Rule Root、Rule Version、Approval 和三类子规则。
- Produces: 一行一个版本的 `t_tax_rule` 及 `tax_rule_id` 精确引用。

- [x] **Step 1: 合并 Rule Manifest**

- 把 Rule Version 的适用范围、版本、Hash 和 Maker/Checker 字段移到 `t_tax_rule`；
- 增加 `checker_decision`、`decision_time` 和真实 CAS `version`；
- 删除 `current_ver_id`、`t_tax_rule_version`、`t_tax_rule_approval`；
- 唯一键改为 `uk_tax_rule(rule_code,rule_ver)`；四个访问索引保留；
- 三类子规则和 `t_tax_line` 的 FK 改为 `tax_rule_id -> t_tax_rule.id`。

- [x] **Step 2: 增加 Rule 生命周期 CHECK**

固定状态矩阵：

- DRAFT：`content_hash/checker_id/checker_decision/decision_time` 全空；
- PENDING_APPROVAL：`content_hash` 非空，Checker Tuple 全空；
- ACTIVE/INACTIVE：Checker 与 Maker 不同，`checker_decision=APPROVED`，决定时间非空；
- REJECTED：Checker 与 Maker 不同，`checker_decision=REJECTED`，决定时间非空。

离开 DRAFT 后 Header 与子规则禁止增改删；重新提交创建更高 `rule_ver`。

- [x] **Step 3: 同步独立 Oracle 契约但暂不刷新 Digest**

从 approved table/hash/immutable/version/check/file manifests 删除旧 Rule 表引用，增加新字段、索引、关系和 CHECK literal；保留 `t_tax_rule.version` 与 `t_tax_rule.content_hash`。

- [x] **Step 4: 运行专项测试**

Expected: Rule 新断言通过；Return、模型陈旧和 Digest 失败保留到后续任务。

### Task 3: 合并 Tax Return 三表并保留更正与缴税链

**Files:**
- Modify: `scripts/pdmaner/finance/manifests/02-tax-economics-cost.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`

**Interfaces:**
- Consumes: Return Root、Return Version、Approval、Return Line 与 Tax Payment。
- Produces: 一行一个申报版本的 `t_tax_return`、自引用更正链和精确缴税引用。

- [x] **Step 1: 合并 Return Manifest**

- 合并 Root/Version/Approval 字段；
- `amends_return_id` 自引用上一已提交版本；
- 增加 `maker_id/checker_id/checker_decision/decision_time/version`；
- 删除 `current_ver_id`、`t_tax_return_ver`、`t_tax_return_approval`；
- 唯一键使用 `uk_tax_return_no(return_no,return_ver)` 与 `uk_tax_return_period(tax_period_id,return_ver)`；
- `t_tax_return_line/t_tax_payment` 改为 `tax_return_id -> t_tax_return.id`。

- [x] **Step 2: 增加 Return 生命周期 CHECK**

固定：

- DRAFT：Content Hash 和 Checker Tuple 为空，未提交且无税局回执；
- PENDING_APPROVAL：Content Hash 非空，Checker Tuple 为空，未提交；
- APPROVED：Checker 批准，未提交且无税局回执；
- REJECTED：Checker 驳回，未提交且无税局回执；
- FILED：Checker 已批准，文件二元组和 `filed_time` 非空；
- ACCEPTED/AMENDMENT_REQUIRED/CLOSED：Checker 已批准，文件、提交时间和 `authority_ref` 全部存在；
- `return_ver=1` 时 `amends_return_id` 为空；更高版本必须引用同 `return_no` 的上一已提交版本，数据库 CHECK 阻止直接自环，跨行约束由应用事务保证。

- [x] **Step 3: 同步 File Pair、Hash、Version 和 CHECK Oracle**

File Pair 绑定到 `t_tax_return`；保留 `t_tax_return.content_hash/file_content_hash/version`，删除 Approval Hash 和旧 Version 表路径。

- [x] **Step 4: 运行专项测试**

Expected: 合并结构和 policy 断言通过；生成模型与 Digest 仍陈旧。

### Task 4: 同步权威文档、现实场景和项目统计

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: 合并后的 23 表业务含义。
- Produces: 无旧 Root/Version/Approval 语义的当前权威入口和逐表现实数据。

- [x] **Step 1: 更新 Finance 逻辑与物理决策**

明确 Rule/Return 均是一行一个业务版本；审批结果固定在本行，离开 DRAFT 后内容冻结；Return Amendment 追加新行。删除 Version Root Current Pointer 和独立 Approval 不变量。

- [x] **Step 2: 更新现实场景**

- `t_tax_rule` 示例直接使用原版本 ID 30008，保存 `rule_code/rule_ver/.../maker/checker/decision_time`；
- `t_tax_return` 示例直接使用原精确版本 ID 30401，保存 `return_no/return_ver/.../maker/checker/decision_time`；
- Return Line 和 Tax Payment 引用 30401；
- 删除四张旧表的示例和覆盖目录项。

- [x] **Step 3: 更新当前统计和权威链接**

同步 118/1465/205/188/205/38/8 和 Tax/Fiscal 23 表；只保留旧实施记录中的历史统计，不改写历史快照。

### Task 5: 生成 PDManer 并刷新独立摘要

**Files:**
- Modify: `scripts/pdmaner/finance/generate-finance-model.mjs`
- Modify: `finance-service/docs/database/finance.pdma.json`（生成器写入）
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`

**Interfaces:**
- Consumes: 新 manifest/policy/diagram group。
- Produces: 确定性 118 表 Finance PDManer 模型和独立双摘要。

- [x] **Step 1: 更新生成器精确表数门禁**

把 122 表主目录与图组门禁改为 118；Tax/Fiscal 主图组由 27 改为 23，其他图组不变。

- [x] **Step 2: 生成模型**

Run: `node scripts/pdmaner/finance/generate-finance-model.mjs`

Expected: 118 tables、1465 fields、205 unique indexes、188 access indexes、205 relations、38 one-to-one relations、8 diagram groups。

- [x] **Step 3: 从失败输出刷新双摘要**

更新独立 Oracle 的 `EXPECTED_MODEL_DIGEST` 和 `EXPECTED_STRUCTURAL_DIGEST`，物理决策记录同值；共享模板摘要保持不变。

- [x] **Step 4: 运行两组 Finance 专项测试**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: 全部 PASS。

### Task 6: 全量验证和实施记录收尾

**Files:**
- Modify: `agent-docs/plans/2026-08-27-finance-tax-fiscal-23-table-simplification-implementation-plan.md`

**Interfaces:**
- Consumes: 完整变更集。
- Produces: 可复现验证证据和完成状态。

- [x] **Step 1: 检查生成器无漂移**

Run: `node scripts/pdmaner/finance/generate-finance-model.mjs --check`

Expected: Finance model is up to date。

- [x] **Step 2: 运行全量 PDManer**

Run: `node --test scripts/pdmaner/*.test.mjs`

Expected: 当前全部套件 PASS。

- [x] **Step 3: 运行 requirements-site**

Run: `npm --prefix requirements-site test`

Expected: 构建成功且当前 83 项全部 PASS。

- [x] **Step 4: 审计范围和格式**

Run:

```bash
git diff --check
rg -n "t_tax_rule_version|t_tax_rule_approval|t_tax_return_ver|t_tax_return_approval|tax_rule_ver_id|tax_return_ver_id|current_ver_id" <当前 Finance 权威与生成源>
```

Expected: 当前 Finance 权威和生成源不含被删除结构；历史计划可保留历史词汇。没有 Java、Migration、部署、Treatment/Rate 合并或无关改动。

- [x] **Step 5: 记录最终实算结果**

把模型统计、三个 SHA-256、Finance 专项、全量 PDManer、requirements-site 和生成器 Check 的实际结果写入本计划；不创建 Git commit。

## Final Verification Evidence

- TDD RED：旧模型运行两组 Finance 测试为 19 PASS / 4 FAIL；失败精确来自 122 表旧统计、四张待合并表和旧场景引用。
- 最终模型：118 表、1,465 字段、205 个唯一索引、188 个访问索引、205 条本库关系、38 条 1:1 关系、8 个 Diagram Groups；Tax/Fiscal 主图 23 表。
- 共享模板 SHA-256：`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- 模型文件 SHA-256：`80acb6d2433a86a012b97e2d48330f927969da0f95fe20e2b30acb988a3fead3`。
- 独立结构 SHA-256：`e4434e8ed307dc1f4890b85e759adb59c13841b5c2b7c437927a8b0498497621`。
- `node scripts/pdmaner/finance/generate-finance-model.mjs --check`：PASS，模型无漂移。
- Finance 专项：23/23 PASS。
- 全量 PDManer：145/145 PASS。
- requirements-site：构建成功，83/83 PASS。
- `git diff --check`：PASS。
- 当前 Finance 权威、场景、生成 Manifest/Policy 与模型不再正向使用被删除的四表和旧 `*_ver_id`；测试与 Oracle 仅保留禁止恢复这些结构的负向断言。
- 未创建 Java、Migration、物理数据库、部署环境、Git branch 或 commit。
