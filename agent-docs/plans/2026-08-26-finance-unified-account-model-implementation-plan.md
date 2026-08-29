# Finance Unified Account Model Implementation Plan

> **Execution status (2026-08-26):** The original dual-account consolidation was implemented, then the remaining 1:1 balance table was merged into `t_account` by the [account-balance merge plan](./2026-08-26-finance-account-balance-merge-implementation-plan.md). The later [transaction-economics and accounting-rule simplification](./2026-08-26-finance-transaction-economics-accounting-rule-simplification-implementation-plan.md) retained this decision; the current model has 121 tables. This checklist is retained as an execution record, not as pending authorization. PDManer 4.9.3 in-app review remains manual.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for every structural behavior change and `superpowers:verification-before-completion` before reporting completion. Work in the current workspace because the authoritative Finance model is intentionally uncommitted; do not create branches or commits.

**Goal:** Replace Finance's dual Ledger/Subledger account model with one owner-aware posting account while preserving per-Book accounting, chart-level trial balance, funding safety, and deterministic PDManer generation.

**Architecture:** `t_ledger_book` remains the OE-currency boundary. `t_account` becomes the only posting account and directly carries its current balance columns; Journal lines reference one account, while period and trial projections aggregate by Book and Chart Account. Current `t_accounting_rule_line` records stay code-based and resolve account identity at runtime.

**Tech Stack:** Node.js ESM, `node:test`, Markdown, PDManer 4.9.3 JSON, deterministic Finance Manifest/Generator.

**Spec:** `agent-docs/plans/2026-08-26-finance-unified-account-model-design.md`

## Global Constraints

- All table names start with `t_`; unique indexes start with `uk_`; access indexes start with `idx_`.
- `t_ledger_book` remains the `Operating Entity + Currency` accounting boundary and every Journal Entry remains single-Book.
- The only posting-account identity is `(ledger_book_id, chart_account_id, owner_type, owner_id, balance_bucket)`.
- Account currency and OE are derived from the Book and are not duplicated on `t_account`.
- Journal, Movement, Snapshot, Event and Approval history remains append-only.
- Cross-system IDs never receive database foreign keys.
- Do not add Java, migrations, databases, infrastructure, generic history, Inbox/Outbox, cross-OE transfers or summary balance tables.
- Preserve unrelated workspace changes; do not create a branch, commit, publish or deploy.

---

### Task 1: Freeze the unified-account contract with RED tests

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: current generated PDManer model and scenario document.
- Produces: failing assertions for the exact target account, Journal, balance, trial and scenario contracts.

- [ ] Add assertions that `t_account` exists with the approved field signature and `uk_account_identity`.
- [ ] Add assertions that `t_ledger_account`, `t_subledger_account` and `t_subledger_balance` do not exist.
- [ ] Add assertions that instruction, Journal and adjustment lines contain `account_id` and no Ledger/Subledger pair.
- [ ] Add assertions for `t_ledger_period_balance`, Chart-level Trial lines, account Owner/Balance Bucket checks and renamed Close step.
- [ ] Add scenario assertions for `CLIENT_PAYABLE`, `PARTNER_PAYABLE`, Provider Account, Cash Account and Tax Authority account examples.
- [ ] Run `node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs` and verify failure is caused by the old dual-account model.

### Task 2: Implement the Manifest and policy changes

**Files:**
- Modify: `scripts/pdmaner/finance/manifests/01-accounting-foundation.mjs`
- Modify: `scripts/pdmaner/finance/manifests/03-accounting-journal.mjs`
- Modify: `scripts/pdmaner/finance/manifests/04-balance-funding.mjs`
- Modify: `scripts/pdmaner/finance/manifests/07-close.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance/generate-finance-model.mjs`

**Interfaces:**
- Consumes: Task 1's target model assertions.
- Produces: a 123-table production Manifest using one posting account with embedded current-balance columns.

- [ ] Replace the two account entities with `account` and enforce approved Owner/Bucket shape and indexes.
- [ ] Remove `subledger_owner_type`; make `balance_bucket` closed and non-null with `NONE`.
- [ ] Replace line-level Ledger/Subledger references with `account_id` and remove redundant Book/Currency fields.
- [ ] Merge the current balance columns into `account`, delete `account_balance` and `subledger_balance`, and replace `account_period_balance` with `ledger_period_balance`.
- [ ] Change Tax Payable Funding source semantics from `SUBLEDGER_ACCOUNT` to `ACCOUNT`.
- [ ] Change Trial lines to Chart Account, type Trial Exception references, and rename the close reconciliation step.
- [ ] Update Diagram Groups and generator table-count invariant to 123.
- [ ] Run the targeted model tests; expect structural assertions to advance to Oracle/digest failures rather than old-model failures.

### Task 3: Update the independent Oracle and generate PDManer

**Files:**
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `finance-service/docs/database/finance.pdma.json` through the deterministic generator only.
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`

**Interfaces:**
- Consumes: Task 2's production Manifest.
- Produces: independent expected structure, regenerated PDManer artifact and exact physical totals/digests.

- [ ] Replace approved table, field, index, relation, Check and immutable-field signatures in the Oracle without importing production Manifest data.
- [ ] Set the expected target totals after independently calculating and verifying the generated structure; current design totals are 123 tables, 1,498 fields, 211 unique indexes, 188 access indexes, 213 relations and 41 one-to-one relations.
- [ ] Generate with `node scripts/pdmaner/finance/generate-finance-model.mjs`.
- [ ] Capture the new deterministic model, structure and metadata digests in the independent Oracle.
- [ ] Update the physical decisions document with the unified-account rationale and exact totals.
- [ ] Run `node --test scripts/pdmaner/finance-model.test.mjs` and `node scripts/pdmaner/finance/generate-finance-model.mjs --check` until both pass.

### Task 4: Synchronize logical documentation and real-world data

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`

**Interfaces:**
- Consumes: generated account fields, enums and exact table catalog from Task 3.
- Produces: canonical logical rules and a complete 123-table example dataset consistent with the PDManer model.

- [ ] Replace all Ledger/Subledger account descriptions and diagrams with the approved Account/Book/Chart relationships.
- [ ] Rewrite Journal, balance, Funding, Trial and Close invariants around one Account balance truth.
- [ ] Replace Client and Partner bucket-specific Chart codes with `CLIENT_PAYABLE` and `PARTNER_PAYABLE`.
- [ ] Populate realistic Account, Journal, balance, Tax Authority, Provider Account and Cash Account values.
- [ ] Remove `SUBLEDGER_MISMATCH` and rewrite the failed-Trial example as a typed account/business reconciliation mismatch.
- [ ] Update table counts and domain group catalogs to 123.
- [ ] Replace requirements/global-sequence wording that still describes Ledger/Subledger dual posting.
- [ ] Run `node --test scripts/pdmaner/finance-scenario-doc.test.mjs` and `npm --prefix requirements-site test`.

### Task 5: Verify the complete artifact

**Files:**
- Inspect all files changed by Tasks 1-4.

**Interfaces:**
- Consumes: final Manifest, Oracle, PDManer, documents and requirements.
- Produces: fresh verification evidence and a review report; no commits.

- [ ] Run the Finance model and scenario tests together.
- [ ] Run deterministic generator `--check`.
- [ ] Run all CCT, Commerce, Payment and Finance PDManer tests, reporting unrelated pre-existing failures separately.
- [ ] Run `npm --prefix requirements-site test` and lint touched requirements files.
- [ ] Scan for obsolete tables, fields, enums, bucket-specific payable codes and dual-account wording.
- [ ] Parse final JSON and independently verify totals, naming prefixes, local-only relations, credentials/PAN safety and diagram membership.
- [ ] Review the complete diff against the design spec and report the remaining PDManer 4.9.3 in-app manual compatibility gate.
