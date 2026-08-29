# Commerce Document Audit Cancel Reversal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the obsolete Commerce document/BCR tables, add structured order cancellation reason, and synchronize the approved Cancel/Void/Reversal semantics through the generated PDManer model and canonical documentation.

**Architecture:** Commerce remains the owner of Order cancellation and Refund Request, while Payment owns authoritative channel state and the actual Void action. Reversal is a Commerce business command whose atomic Payment decision resolves to either Void or the existing full-refund chain; no new aggregate or table is introduced.

**Tech Stack:** Node.js contract tests and deterministic model generator, PDManer 4.9.3 JSON, TypeScript requirements catalog, Markdown architecture documents.

**Spec:** `agent-docs/plans/2026-08-24-commerce-document-audit-cancel-reversal-design.md`

**Status:** Completed on 2026-08-25. All automated gates are green; opening the model and running the checker in the PDManer 4.9.3 desktop application remains the documented manual compatibility gate.

## Global Constraints

- Preserve the dirty worktree and all unrelated user changes.
- Work in the existing workspace because the approved untracked design assets are not available from Git history; do not create branches, commits or worktrees.
- Physical names use `t_`; unique indexes use `uk_`; ordinary indexes use `idx_`.
- Do not add a Cancel, Void, Reversal, Consumer Document or BCR table.
- `PaymentSucceeded` means SALE success or full Capture; authorization-only success must be called `PaymentAuthorized` or “授权成功”.
- Cancel never silently upgrades to Reversal; Provider-submitted or UNKNOWN operations must first converge through Query.
- Reversal resolves only to Void or the existing Refund flow. Partial Capture Reversal is not supported in V1.
- The target Commerce artifact has exactly 14 tables, 222 fields, 21 unique indexes, 11 ordinary indexes, 14 relations, 4 diagrams, 7 version tables and 6 immutable tables.
- This change does not authorize Java, migrations, runtime databases, infrastructure or deployment.

---

### Task 1: Lock the 14-table Commerce contract with failing tests

**Files:**
- Modify: `scripts/pdmaner/commerce-model.test.mjs`
- Modify: `scripts/pdmaner/commerce-scenario-doc.test.mjs`
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`

**Interfaces:**
- Consumes: the approved design in the Spec.
- Produces: literal contract expectations for the generated model, documentation and three operation branches.

- [x] Remove the three obsolete tables from the independent test manifest and add `commerce_order.status_reason_code VARCHAR(64) NULL OPEN(Order Status Reason Catalog)`.
- [x] Freeze literal totals at 14/222/21/11/14/4, version tables at seven and immutable tables at six.
- [x] Assert that Consumer Document and BCR tables stay absent and that the reliability diagram contains only command idempotency and compensation event.
- [x] Assert realistic Cancel, authorization Void and captured Reversal-to-Refund scenarios and the unsupported partial-Capture boundary.
- [x] Assert interface ownership and precise `PaymentSucceeded` terminology.
- [x] Run focused tests and verify they fail against the old 17-table model and old contracts.

### Task 2: Generate the 14-table Commerce PDManer model

**Files:**
- Modify: `scripts/pdmaner/generate-commerce-model.mjs`
- Modify: `scripts/pdmaner/commerce-field-metadata.mjs`
- Modify: `commerce-service/docs/database/commerce.pdma.json`

**Interfaces:**
- Consumes: Task 1 model contract.
- Produces: a deterministic PDManer artifact with the exact target totals and Chinese metadata.

- [x] Delete Consumer Document and BCR fields, indexes, relations, diagram members, version/immutable allowlist entries and metadata.
- [x] Add the open-catalog order `status_reason_code` field and its Chinese name/description.
- [x] Keep four diagram groups and rename the final group to `reliability` / “可靠执行”.
- [x] Regenerate `commerce.pdma.json` and update fresh model/structural SHA-256 values in the contract test and physical manifest.
- [x] Run focused generator/model tests until green.

### Task 3: Synchronize Commerce authorities and realistic scenarios

**Files:**
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md`
- Modify: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Modify: superseded Commerce design/implementation plan status notes where needed.

**Interfaces:**
- Consumes: Task 2 field/table contract.
- Produces: the current 14-table logical, physical and scenario documentation.

- [x] Replace 17-table, Consumer Document and Commerce BCR ownership with the 14-table boundary.
- [x] Explain that Platform owns document status/history and Finance owns Fiscal Facts; Commerce publishes reliable source facts without a local document projection.
- [x] Add exact Cancel/Void/Reversal states, concurrency handling and concrete scenario values.
- [x] Update ER, table catalog, version/immutable lists and exact totals.

### Task 4: Synchronize cross-system requirements and navigation

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/APPLICATION_ARCHITECTURE.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/README.md`
- Modify: `AGENTS.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: Commerce `COM-I05`, `COM-I08`; Payment `PAY-I12`, `PAY-I13`; `OrderCancelled` and existing Refund events.
- Produces: one consistent requirements/navigation contract for future agents.

- [x] Split Void from the existing Refund-only business/funding validation rule.
- [x] Define Payment Action targeting, atomic branch resolution, Query/UNKNOWN handling, risk reservation release and Finance no-op boundaries.
- [x] Add Cancel and Reversal use cases/interfaces without exposing Auth/Capture details to Clients.
- [x] Add or amend global sequences for unpaid Cancel, authorization Void and captured Reversal-to-Refund.
- [x] Remove current claims that Commerce owns Consumer Document or BCR persistence while retaining historical plan files as explicitly superseded records.

### Task 5: Verify all generated and canonical artifacts

**Files:**
- Test: `scripts/pdmaner/*.test.mjs`
- Test: `requirements-site/tests/*.test.mjs`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: fresh automated evidence and the remaining manual PDManer compatibility gate.

- [x] Run `node --test scripts/pdmaner/commerce-field-metadata.test.mjs scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs`.
- [x] Run `node scripts/pdmaner/generate-commerce-model.mjs --check`.
- [x] Run `npm --prefix requirements-site test`.
- [x] Run `npm --prefix requirements-site run lint`.
- [x] Run targeted stale-term scans and `git diff --check`.
- [x] Report PDManer 4.9.3 in-app opening and checker as the remaining manual compatibility gate.
