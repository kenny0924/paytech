# Finance Account Balance Merge Implementation Plan

> **Current authority notice (2026-08-27):** The Account Balance merge remains implemented, but 121 tables is a historical snapshot; current Finance is 103 tables. Every generic Funding Position/Commitment/Allocation/Move statement in this plan is superseded by the [Funding module removal design](./2026-08-27-finance-funding-module-removal-design.md) and [implementation record](./2026-08-27-finance-funding-module-removal-implementation-plan.md). Only the unaffected unified Account and Journal-projection decisions remain current. PDManer 4.9.3 in-app review remains the manual compatibility gate.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for structural changes and `superpowers:verification-before-completion` before reporting completion. Work in the current workspace because the Finance design artifacts are intentionally uncommitted; do not create branches or commits.

**Goal:** Merge the one-to-one `t_account_balance` projection into `t_account` without changing Journal authority, account identity, ledger-book isolation or period/trial aggregation.

**Architecture:** `t_account` remains the only posting-account identity and directly carries its rebuildable current balance columns. Identity fields stay immutable; `balance_version + last_journal_sequence` control only balance projection updates. `t_ledger_period_balance` and Trial Balance remain Book/Chart summaries.

**Tech Stack:** Node.js ESM, `node:test`, Markdown, PDManer 4.9.3 JSON, deterministic Finance Manifest/Generator.

**Spec:** `agent-docs/plans/2026-08-26-finance-unified-account-model-design.md`

## Global Constraints

- Keep `uk_account_identity(ledger_book_id,chart_account_id,owner_type,owner_id,balance_bucket)` unchanged.
- Add `debit_total`, `credit_total`, `net_balance`, `last_journal_sequence` and `balance_version` to `t_account`, all non-null with zero defaults.
- Remove `t_account_balance`, its primary key, `uk_account_balance`, local 1:1 relation and Diagram membership.
- `balance_version` is not an account identity/configuration version; account status transitions continue to use an expected old status.
- Journal remains the only accounting fact source; the balance columns remain rebuildable projections.
- Historical implementation step: this change originally kept `t_ledger_book`, `t_ledger_period_balance`, Trial Balance and then-existing Funding Position semantics unchanged; the later 2026-08-27 authority deletes all generic Funding semantics.
- Preserve unrelated workspace changes; do not create migrations, Java code, databases, branches, commits, deployments or external resources.

---

### Task 1: Freeze the merged-account contract with RED tests

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`

- [x] Require the five balance columns on `t_account` and require `t_account_balance` to be absent.
- [x] Move the approved balance concurrency path to `t_account.balance_version`.
- [x] Set independent expected totals to 123 tables, 1,498 fields, 211 unique indexes, 188 access indexes, 213 relations, 41 one-to-one relations and 8 Diagram Groups.
- [x] Run the Finance model test and confirm it fails because the old generated model still contains `t_account_balance`.

### Task 2: Implement and regenerate the physical model

**Files:**
- Modify: `scripts/pdmaner/finance/manifests/01-accounting-foundation.mjs`
- Modify: `scripts/pdmaner/finance/manifests/04-balance-funding.mjs`
- Modify: `scripts/pdmaner/finance/manifest.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Regenerate: `finance-service/docs/database/finance.pdma.json`

- [x] Add the balance columns to `account` with Chinese names, descriptions and zero defaults.
- [x] Delete the independent `account_balance` entity and Diagram membership.
- [x] Generate the PDManer model and update independent deterministic digests only after verifying structure.
- [x] Run the targeted Finance model test and generator `--check` until green.

### Task 3: Synchronize canonical documentation and scenarios

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `agent-docs/plans/2026-08-26-finance-unified-account-model-design.md`
- Modify: `agent-docs/plans/2026-08-26-finance-unified-account-model-implementation-plan.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify current-count references in root and `agent-docs` navigation/context documents.

- [x] Replace independent balance-table wording with `t_account` balance-column wording.
- [x] Merge the example balance values into their corresponding account rows and remove synthetic balance IDs.
- [x] Update current model counts to 123/1,498/211/188/213/41/8 while preserving the historical 126-table baseline.
- [x] Run the Finance scenario-document test.

### Task 4: Verify the complete artifact

**Files:**
- Inspect every file changed by Tasks 1-3.

- [x] Run Finance model and scenario tests together.
- [x] Run the deterministic Finance generator with `--check`.
- [x] Run every PDManer test and `npm --prefix requirements-site test`.
- [x] Scan for current-model references to `t_account_balance`, obsolete totals and dangling relation/Diagram IDs.
- [x] Parse the final PDManer JSON and independently verify exact totals, naming prefixes, local-only relations, sensitive-data boundaries and Diagram membership.
