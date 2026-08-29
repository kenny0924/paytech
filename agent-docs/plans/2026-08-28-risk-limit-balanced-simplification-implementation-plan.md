# Risk Limit Balanced Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Risk Limit 图组从 10 表收敛为 7 表，删除重复配置版本层和运行明细状态，同时保留原子占额、UNKNOWN 持有、幂等与完整审计。

**Architecture:** `decision_point_obligation` 直接拥有 0..N 个 `decision_point_limit_binding`，`limit_version` 吸收 Definition 身份；运行态继续使用 Position、Group、不可变 Reservation、Group State Change 和 Movement。Group 是唯一当前状态权威，State Change 与 Movement 分别保存状态历史和计数器账迹。

**Tech Stack:** Node.js ESM、`node:test`、Risk Manifest DSL、PDManer 4.9.3 确定性生成器、Markdown requirements catalog。

**Spec:** `agent-docs/plans/2026-08-28-risk-limit-balanced-simplification-design.md`

**Historical scope:** 本计划记录的 `72 tables / 866 fields` 是 Settlement Eligibility 实施前的历史阶段基线；当前 Risk 总量与 Settlement 拓扑以后续 Settlement Eligibility 设计、实现记录和生成 Oracle 为准。

## Global Constraints

- 最终 Limit 图组必须恰好 7 表，Risk 总表数必须恰好 72，Diagram Groups 保持 8。
- `PAYMENT_ATTEMPT_RISK + ALLOW` 必须恰有一个启用且 `FAIL_CLOSED` 的 `RESERVE_LIMIT_GROUP`；该义务允许 0..N 个 Binding，零项仍创建空 Group。
- Position、Group、Reservation、Group State Change、Movement 的业务边界不得合并。
- Reservation 创建后不可变；Group 是唯一当前状态权威。
- PAYMENT_UNKNOWN 不自动释放、不产生 Movement；Success COMMIT，安全失败/提交前 Cancel/权威 Void RELEASE。
- SHADOW/REPLAY 不创建任何真实 Limit 运行事实。
- 只增量修改当前 main；保留 Finance 98/1,221/166/161/172/28/7 及当前 Commerce、Payment、Platform 语义。
- 不 commit、不打开 PDManer GUI、不连接数据库、不发布或部署。

---

### Task 1: 冻结独立 Limit Oracle 与失败用例

**Files:**
- Create: `scripts/pdmaner/risk-limit-balanced-simplification.test.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Test: `scripts/pdmaner/risk-limit-balanced-simplification.test.mjs`

**Interfaces:**
- Consumes: 当前 `02-decision-engine.mjs`、`05-limits.mjs`、`model-policy.mjs` 和生成 PDManer 模型。
- Produces: 7 表清单、准确字段/索引/关系、删除表/字段、CHECK 语义的独立失败门禁。

- [x] **Step 1: 写入失败测试**

测试必须直接断言：

```text
limits = [
  limit_version,
  decision_point_limit_binding,
  limit_position,
  limit_reservation_group,
  limit_reservation,
  limit_group_state_change,
  limit_movement
]
```

并断言三个旧 Root/Version 表不存在，Limit Version 吸收目录字段，Binding 归属 Obligation，Group 引用 Obligation，Reservation 不可变且没有同步状态字段。

- [x] **Step 2: 运行 RED**

Run:

```bash
node --test scripts/pdmaner/risk-limit-balanced-simplification.test.mjs
```

Expected: FAIL，原因必须是旧 10 表结构或新表/字段尚不存在，而不是语法或导入错误。

### Task 2: 实现 7 表 Manifest 与物理 CHECK

**Files:**
- Modify: `scripts/pdmaner/risk/manifests/02-decision-engine.mjs`
- Modify: `scripts/pdmaner/risk/manifests/05-limits.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk/manifest.mjs`
- Test: `scripts/pdmaner/risk-limit-balanced-simplification.test.mjs`

**Interfaces:**
- Consumes: Task 1 的精确结构 Oracle。
- Produces: `obligation_type=ACTION/RESERVE_LIMIT_GROUP`、`decision_point_limit_binding`、合并后的 `limit_version` 和精简运行表。

- [x] **Step 1: 修改 Decision Point Obligation**

删除 `limit_plan_version_id`、对应索引和关系；增加 `obligation_type`。行内 CHECK 固定：

```sql
(obligation_type='ACTION' AND action_version_id IS NOT NULL)
OR (obligation_type='RESERVE_LIMIT_GROUP' AND action_version_id IS NULL)
```

- [x] **Step 2: 修改 Limit Manifest**

删除三个旧表，建立 7 表精确结构；Reservation 标记为 immutable；删除所有设计文档已列出的可推导字段并重建索引/关系。

- [x] **Step 3: 收紧 CHECK**

至少覆盖：Limit Metric/Threshold、Currency Mode、Freeze Time、Binding Subject/Amount XOR、Position 非负、Group 状态/时间/Hold、Reservation 正占用、State Change 首事件、Movement 正 Delta。

- [x] **Step 4: 运行 GREEN 前半程**

Run:

```bash
node --test scripts/pdmaner/risk-limit-balanced-simplification.test.mjs
```

Expected: Manifest/Policy 部分 PASS；生成模型相关断言仍可因模型尚未重新生成而 FAIL。

### Task 3: 更新静态 Release Validator

**Files:**
- Modify: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`
- Modify: `scripts/pdmaner/risk/decision-engine-static-validator.mjs`
- Create: `scripts/pdmaner/risk/limit-version-canonicalizer.mjs`

**Interfaces:**
- Consumes: `obligationType`、`limitVersions`、`limitBindings` 扁平 Fixture 集合。
- Produces: Action 与 Limit Obligation 的排他校验、Point 归属、FROZEN Limit、Binding 来源和 Payment 必备义务校验。

- [x] **Step 1: 先改 Fixture 与失败用例**

新增/更新用例证明：

```text
ACTION + binding                    -> reject
RESERVE_LIMIT_GROUP + action target -> reject
RESERVE_LIMIT_GROUP + 0 binding     -> accept
Binding 跨 Point                    -> reject
Binding 引用非 FROZEN Limit         -> reject
Payment ALLOW 缺失/重复强制义务      -> reject
```

- [x] **Step 2: 运行 Validator RED**

Run:

```bash
node --test scripts/pdmaner/risk-decision-engine-contract.test.mjs
```

Expected: FAIL，原因是 Validator 仍依赖 `limitPlans/limitPlanVersions`。

- [x] **Step 3: 最小实现并运行 GREEN**

删除旧集合和 Plan lookup；通过 Obligation Type 与 Binding 直接校验。保留全部既有 AST、Action、Shadow、Approval 和跨行契约行为。

Run:

```bash
node --test scripts/pdmaner/risk-decision-engine-contract.test.mjs
```

Expected: PASS。

### Task 4: 生成模型并冻结独立统计

**Files:**
- Modify: `risk-compliance-service/docs/database/risk-compliance.pdma.json`（仅生成器写入）
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: 完整生产 Manifest/Policy。
- Produces: 72 表生成模型、真实字段/索引/关系/CHECK 统计以及四个独立 Digest。

- [x] **Step 1: 运行生成器**

```bash
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs
```

- [x] **Step 2: 从生成结果读取真实统计与摘要**

禁止手算；使用生成器输出和独立测试投影更新 Oracle 的 totals、group size、CHECK count、model/structural/check-policy digest。

- [x] **Step 3: 运行模型 GREEN**

```bash
node --test scripts/pdmaner/risk-limit-balanced-simplification.test.mjs \
  scripts/pdmaner/risk-compliance-model.test.mjs \
  scripts/pdmaner/risk-decision-engine-contract.test.mjs
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
```

Expected: PASS，且生成器报告 72 tables、7-table Limits group、8 diagram groups。

### Task 5: 同步权威文档、场景和 Requirements

**Files:**
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`

**Interfaces:**
- Consumes: Task 4 的生成器真实统计。
- Produces: 统一的 7 表术语、现实示例、关系图、入口统计和当前计划链接。

- [x] **Step 1: 更新 Risk 专属权威内容**

将所有 `Limit Plan Version/Item` 改为 `RESERVE_LIMIT_GROUP Obligation/Decision Point Limit Binding`；更新表清单、关系图、原子流程、UNKNOWN、零项 Group、字段和 CHECK。

- [x] **Step 2: 更新 Requirements 与测试**

保留“正式 ALLOW 前原子创建 Group”“不生成 Action Intent/Effect”“SHADOW/REPLAY 无副作用”，只替换已删除的 Plan 术语。

- [x] **Step 3: 更新共享入口统计**

只替换 Risk 子串与追加新设计/计划链接；Finance、Commerce、Payment、CCT、Platform 数字和语义逐字保留。

- [x] **Step 4: 运行文档/requirements GREEN**

```bash
node --test scripts/pdmaner/risk-compliance-scenario-doc.test.mjs
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: PASS。

### Task 6: 全量门禁与独立审查

**Files:**
- Review: 本计划全部 changed files。

**Interfaces:**
- Consumes: Tasks 1-5 的完整增量。
- Produces: 可交付的最终 Risk 基线和验证证据。

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

- [x] **Step 3: 扫描残留旧结构与冲突标记**

```bash
rg -n "t_limit_definition|t_limit_plan_version|t_limit_plan_item|limit_plan_version_id|plan_version_id" \
  AGENTS.md README.md agent-docs requirements-site scripts/pdmaner/risk risk-compliance-service/docs/database
rg -n "^(<<<<<<<|=======|>>>>>>>)" .
```

只允许历史计划中明确标注的旧阶段，不允许当前权威文件或生成资产残留。

- [x] **Step 4: 独立审查**

审查 0..N Binding、跨 Point 校验、Position 锁键、Group 状态机、Reservation 不可变、Movement 重建链以及所有共享统计，修复后重新运行覆盖命令。

## Implementation Result (2026-08-28)

- Limit 图组已从 10 表收敛为 7 表；Risk 最终生成基线为 `72 tables / 866 fields / 102 unique indexes / 79 access indexes / 115 local relations / 9 one-to-one relations / 8 Diagram Groups / 71 CHECK constraints`。
- Model SHA-256 为 `47a4aa0be2e9ed4dcb1703e5696a2773875a1759457811824a174d208730e657`；Structural Projection SHA-256 为 `39c3c34b4d40d9586c7ba3f5977f889e510a60e9c745619864d79f2d5ae91826`；CHECK Policy SHA-256 为 `afd6ff5e46db9ef3a516e6719b24d7f6f5de124f7cbb0e0865ac8c27c6236d75`。
- 独立审查发现的接口重复、Risk/Payment 原子边界、Limit Version Canonical Hash/同 Code 稳定性、Group 非法终态迁移和旧计划权威边界均已修复；复审结论 PASS，无 Critical/Important。
- 最终门禁：全量 PDManer `282/282`、Requirements `96/96`、六生成器 `--check`、Requirements ESLint、`git diff --check`、冲突与当前旧结构扫描全部通过。
- 本轮未创建 Java、Migration、数据库、Redis/MQ 环境、分支或 Commit；PDManer 4.9.3 应用内人工视觉检查仍待执行。
