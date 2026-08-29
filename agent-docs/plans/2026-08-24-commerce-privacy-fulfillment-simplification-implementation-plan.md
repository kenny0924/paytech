# Commerce Privacy and Fulfillment Simplification Implementation Plan

> **后续覆盖决定（2026-08-24 Commerce Refund 精简）：** 本计划中关于退款 Reservation、Reserve Change、Payment Projection、UNKNOWN/Release Pending、Finance Ack 以及旧退款表数量的前序讨论，仅在退款范围内由本次决定继续替代。历史任务记录保留，不重写；最终执行与验收以本计划顶部覆盖条款、当前生成器和物理决策文档为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved single-track consumer token, plaintext network evidence, one-to-many acceptance, and no-fulfillment V1 decisions across the generated Commerce model and all canonical documents.

**Architecture:** Commerce retains consumer and checkout evidence needed for payment risk while eliminating duplicate privacy representations and the proactive fulfillment subsystem. Risk collects evidence only after a dispute through its existing case/package model, and Finance matures balances without a fulfillment condition.

**Tech Stack:** Node.js model generator/tests, PDManer 4.9.3 JSON, TypeScript requirements catalog, Markdown architecture documents.

**Spec:** `agent-docs/plans/2026-08-24-commerce-privacy-fulfillment-simplification-design.md`

## Global Constraints

- Preserve the dirty worktree and all unrelated user changes.
- Physical names use `t_`; unique indexes use `uk_`; ordinary indexes use `idx_`.
- Keep exactly three Acceptance types under the one-to-many Contract relationship.
- Do not add Java, Migration, infrastructure, branches, commits, deployments, or external resources.
- Final Commerce baseline after the refund simplification: 17 tables, 266 fields, 29 unique indexes, 16 ordinary indexes, 19 relations, 4 diagram groups, 8 version tables, 7 immutable tables, and 4 BCR record types. Commerce retains only `t_order_refund_position` and `t_refund_request`; Payment `UNKNOWN` maps the Commerce request to `PROCESSING`, Payment terminal events consume/release the local position immediately, and Finance closes independently without a Commerce Ack subscription.

---

### Task 1: Lock the new model behavior with failing tests

**Files:**
- Modify: `scripts/pdmaner/commerce-model.test.mjs`
- Modify: `scripts/pdmaner/commerce-scenario-doc.test.mjs`

- [ ] Replace the old 23-table/338-field and Fulfillment assertions with literal target expectations.
- [ ] Assert the exact Consumer Context and Network Evidence field sets and approved indexes.
- [ ] Assert the three Acceptance types and `1:N` relationship remain.
- [ ] Run the two tests and verify they fail because the generator and documents still express the old design.

### Task 2: Generate the approved Commerce model

**Files:**
- Modify: `scripts/pdmaner/generate-commerce-model.mjs`
- Modify: `scripts/pdmaner/commerce-field-metadata.mjs`
- Modify: `commerce-service/docs/database/commerce.pdma.json`

- [ ] Remove Fulfillment tables, indexes, relations, diagram group and allowlists.
- [ ] Apply the exact Consumer Context and Network Evidence fields from the spec.
- [ ] Regenerate the PDManer model.
- [ ] Run the focused tests and generator `--check` until green.

### Task 3: Synchronize Commerce documentation and scenario

**Files:**
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md`
- Modify: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Modify: affected Commerce design/plan navigation entries

- [ ] Replace old counts and privacy fields with the exact V1 baseline.
- [ ] Remove all proactive Fulfillment flows and sample rows.
- [ ] Keep three explicit Acceptance records in the realistic scenario.

### Task 4: Synchronize canonical and cross-system contracts

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`
- Modify: `AGENTS.md`, `README.md`, and affected navigation/plan documents

- [ ] Remove Fulfillment capabilities, events, interfaces, maturity conditions and dependencies.
- [ ] Add the Dispute RFI sequence using existing Risk case/package tables.
- [ ] Replace consumer Blind Index contracts with stable Vault Tokens and network pseudonymization contracts with approved raw structured fields.
- [ ] Keep external responses free of internal Vault Tokens and restricted risk inputs.

### Task 5: Verify the repository

- [ ] Run `node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs`.
- [ ] Run `node scripts/pdmaner/generate-commerce-model.mjs --check`.
- [ ] Confirm the generated artifact totals are 17 tables / 266 fields / 29 unique indexes / 16 ordinary indexes / 19 relations / 4 diagram groups / 8 version tables / 7 immutable tables / 4 BCR types.
- [ ] Confirm the generated model SHA-256 is `df8c093fb533992003ca6930e4ce97d782d231d65f94cc79618d7cede1e28243` and the structural projection SHA-256 is `3f5b4aa48b25e45bc198f6466ee9d114ba1f4115524aebe27d08e14e37da3551`.
- [ ] Run `npm --prefix requirements-site test`.
- [ ] Run `npm --prefix requirements-site run lint`.
- [ ] Run `git diff --check` and targeted stale-term scans.
- [ ] Report PDManer 4.9.3 in-app validation as the remaining manual gate.
