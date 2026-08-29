# CCT Assignment Simplification Implementation Plan

> **历史阶段提示：** 本文保留该阶段 51 表 / 576 字段基线、测试结果、Digest 与决策原文；当前基线已进一步收敛为 [44 表 Commercial Term 设计](./2026-08-23-cct-commercial-term-consolidation-design.md)。不得据此恢复旧 Assignment、Agreement/Legal 或 Pricing/Share Hash 结构。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 CCT 经营主体自动分配从 8 张表精简为 4 张表，并让 PDManer、需求目录、跨系统冻结字段、文档导航和自动验证保持一致。

**Architecture:** Source Relation 保存 OE 来源，Affiliation 保存 Client 使用资格，Rule 保存 Client 精确 Scope 到候选 OE 的优先级，Assignment 保存已验证的最终分配事实。普通 Assignment 配置不再使用 Root/Version 或 Hash；CCT `commercial_context_hash`、Commerce Snapshot Hash 和 BCR Integrity Hash 继续承担边界证明。

**Tech Stack:** Markdown、TypeScript/MJS 需求目录、Node.js `node:test`、PDManer 4.9.3 JSON、确定性模型生成器。

**Spec:** `agent-docs/plans/2026-08-22-cct-assignment-simplification-design.md`

**Status:** 已实施并通过自动验证；PDManer 4.9.3 应用内检查仍为人工门禁。

## Global Constraints

- 最终只保留 `entity_source_relation`、`client_entity_affiliation`、`entity_assignment_rule`、`client_entity_assignment` 四张 Assignment 领域表。
- 不创建 Assignment Policy/Version/Binding、GLOBAL/Wildcard/Fallback/Manual/Workflow 等超出现阶段的能力。
- 不手工编辑 `client-contract-service/docs/database/client-contract.pdma.json`；只能由生成器重建。
- 不修改 PDManer 自动备份或把历史计划中的旧结构当作当前目标。
- 不创建 Java、Migration、数据库、运行环境或其他五个系统的 PDManer 文件。
- 保留工作区既有修改；不提交 Git commit，不创建分支，不发布站点。

---

### Task 1: 锁定精简模型契约

**Files:**
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`
- Modify: `requirements-site/tests/domain-decisions.test.mjs`

- [x] 将当前表集合从 55 改为 51，并删除四张被淘汰表的字段、主键、唯一键、关系、Nullable、枚举和图组预期。
- [x] 为四张保留表写入精确字段、两个唯一键、12 条本库关系和中文表名预期。
- [x] 增加遗留结构拒绝断言：当前模型不得出现四张删除表，以及 `source_owner_key`、Assignment `scope_hash/content_hash/hash_schema_version/version_no/*_revision_no/policy_*`。
- [x] 在需求测试中断言 CCT-I11/I13 输出稳定 Assignment/Rule/Affiliation/Source ID，不再输出 Assignment Version/Scope Hash 或 Source/Affiliation Revision。
- [x] 运行 `node --test scripts/pdmaner/client-contract-model.test.mjs requirements-site/tests/domain-decisions.test.mjs`，确认失败来自旧 Catalog/模型仍未同步。

### Task 2: 更新权威逻辑模型与跨系统契约

**Files:**
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`

- [x] 把“来源与主体分配”分组从 8 张改为 4 张，写入四张表的完整字段、状态、唯一键和自动选择规则。
- [x] 删除当前文档中的 Policy/Version/Binding、Assignment Version、Assignment 中间 Hash 和 Revision Wire 承诺。
- [x] 明确 `client_source_basis` 保留，`source_owner_key` 删除并以 Owner XOR + 两个直接索引字段替代。
- [x] 将 CCT-I11/I13 Proof 改为稳定 ID + 准确 Scope + `commercial_context_hash`，并保持 Fail Closed。
- [x] 将 Commerce `order_snapshot` 的 `assignment_version_id/assignment_scope_hash/source_relation_version` 改为 `assignment_id/assignment_rule_id/affiliation_id/entity_source_relation_id`。
- [x] 将 Payment Intent 的 `assignment_version_id` 改为 `assignment_id`。
- [x] 更新 E2E-02 和需求决策测试，确保 Client 仍不能选择 OE/MID，Payment Attempt 仍不能切换 OE。

### Task 3: 更新生成器和字段元数据

**Files:**
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs`
- Modify: `scripts/pdmaner/generate-client-contract-model.mjs`

- [x] 删除被移除表与字段的显示名、描述、Hash 说明、值域、Nullable、版本表、索引、关系和 Diagram 引用。
- [x] 为新增的 `active_slot`、`priority_no`、四个稳定证明 FK 和精确 Scope 字段配置简洁中文元数据。
- [x] 将四张表状态固定为 `ACTIVE|ENDED`，Slot 规则固定为 ACTIVE=1、ENDED=NULL。
- [x] 为四张表生成完整唯一索引和访问索引；对跨行 Rule/Affiliation/Source/Assignment 一致性写入明确事务约束说明。
- [x] 运行 `node --check scripts/pdmaner/client-contract-field-metadata.mjs` 与 `node --check scripts/pdmaner/generate-client-contract-model.mjs`。

### Task 4: 重建并验证 PDManer

**Files:**
- Generate: `client-contract-service/docs/database/client-contract.pdma.json`
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`

- [x] 运行 `node scripts/pdmaner/generate-client-contract-model.mjs` 重建模型。
- [x] 运行模型测试并修复表、字段、唯一键、关系、Nullable、中文元数据、图组和遗留结构失败。
- [x] 从通过结构检查的模型读取实际表/字段、必填/可空、唯一键/访问索引、关系、Table/Relation Cell 和 Structural Projection SHA-256，更新精确断言。
- [x] 再运行 `node --test scripts/pdmaner/client-contract-model.test.mjs` 和 `node scripts/pdmaner/generate-client-contract-model.mjs --check`。
- [x] 运行 JSON 解析检查，确保工件可读取。

### Task 5: 同步导航、统计和最终证据

**Files:**
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/plans/2026-08-20-cct-row-history-design.md`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/plans/2026-08-22-cct-assignment-simplification-design.md`
- Modify: `agent-docs/plans/2026-08-22-cct-assignment-simplification-implementation-plan.md`

- [x] 把所有当前导航和阶段说明更新为模型实际统计，并链接本次设计；历史 57/55 计划继续作为历史证据，不改写其实施步骤。
- [x] 更新物理决策中的表数、字段数、索引数、关系数、图组 Cell 数、Structural Projection、Model SHA-256 和模板 SHA-256。
- [x] 将设计与计划状态更新为已实施并通过自动验证，所有任务勾选完成。
- [x] 运行 `npm --prefix requirements-site test`、模型全量测试、生成器 `--check`、JSON 解析和 SHA-256 检查。
- [x] 用 `rg` 审计当前源中四张删除表及被删除 Assignment 字段只出现在明确历史/拒绝清单，不出现在当前 Catalog、生成器、元数据、需求或跨系统契约中。
