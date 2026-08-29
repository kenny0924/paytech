# Finance Security Deposit Account Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development while changing executable model contracts. This repository forbids commits for this task; preserve the dirty worktree and report file-level changes instead.

**Goal:** Replace the five-table Finance Reserve/Recovery workflow with a single prepaid Client security-deposit Account plus Journal-based recovery accounting and a live settlement gate.

**Architecture:** `t_account` and immutable Journal remain the only balance/accounting truth. A Client security deposit is the `CLIENT_SECURITY_DEPOSIT/NONE` liability Account; a confirmed shortfall is the `CLIENT_RECOVERY_RECEIVABLE/NONE` asset Account. Candidate and Settlement requirement snapshots keep only the source requirement, final required amount, currency, effective window and content hash; actual payout paths lock and check the current Account rather than creating Reserve/Recovery Cases.

**Tech Stack:** Node.js ESM model generator, PDManer 4.9.3 JSON, `node:test`, TypeScript requirements catalog, Markdown authority documents.

**Spec:** `agent-docs/database/FINANCE_DATABASE.md` plus the user-approved 2026-08-27 decision recorded by this plan.

## Global Constraints

- Delete exactly `t_reserve_case`, `t_restricted_fund`, `t_restricted_fund_move`, `t_recovery_case`, and `t_recovery_application`.
- Keep `t_ledger_period_balance`, move it into `ledger_and_close`, and remove the standalone `balance_reserve_recovery` Diagram Group.
- Keep `CLIENT_SECURITY_DEPOSIT` as a Client-owned liability Account with `balance_bucket=NONE`; do not create a per-case Account or another balance table.
- Keep `CLIENT_RECOVERY_RECEIVABLE` as an asset Account; recovery source, future netting and manual receipt are expressed by Transaction Economics, Accounting Instruction and immutable Journal, not a Case/Application workflow.
- Keep Risk as the policy owner. Finance may retain `RESERVE_REQUIREMENT` as the incoming source requirement code, but in Finance it means only the final minimum prepaid security-deposit amount; remove reserve type, percentage basis/rate, release terms and local Case binding from Finance snapshots.
- A Client payout is eligible only when current `CLIENT_RECOVERY_RECEIVABLE/NONE` is zero and current `CLIENT_SECURITY_DEPOSIT/NONE` is at least the authoritative required amount. Candidate evaluation, Settlement promotion and first bank submission recheck under the Account row lock; the check is not a new persisted Gate aggregate.
- `RefundSucceeded` continues to use PENDING then AVAILABLE and sends uncovered value directly to Recovery Receivable. Only an explicitly allowed `DisputeLost` path may consume the security deposit before creating a remaining Recovery Receivable.
- Do not create Java, Migration, a physical database, infrastructure, a branch or a Git commit.
- Preserve unrelated Commerce, Payment, Risk, Platform Support and existing dirty-worktree changes.

---

### Task 1: Freeze the simplified model contract with a failing test

**Files:**
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/finance-model.test.mjs`

**Interfaces:**
- Consumes: current generated 103-table Finance model.
- Produces: independent expected contract for 98 tables, 1,221 fields, 166 unique indexes, 161 access indexes, 172 relations, 28 one-to-one relations and 7 Diagram Groups.

- [x] **Step 1: Change the independent Oracle before generator code**

  Remove the five deleted tables from `APPROVED_TABLES`, remove their Version/Hash/immutable/check/relation contracts, remove `balance_reserve_recovery`, and set literal totals to:

  ```js
  {
    tables: 98,
    fields: 1221,
    uniqueIndexes: 166,
    accessIndexes: 161,
    relations: 172,
    oneToOneRelations: 28,
    diagrams: 7,
  }
  ```

  Freeze `t_ledger_period_balance` under `ledger_and_close`. For both requirement snapshots, forbid `reserve_type`, `basis_amount`, `rate_value`, and `release_terms_hash`; additionally forbid `settlement_req_snapshot.reserve_case_id`. Replace the old Reserve matrix with a literal two-branch contract: Settlement Restriction rows carry no required amount, while Client `RESERVE_REQUIREMENT` rows carry only a positive final `required_amount` and use the prepaid security-deposit meaning.

- [x] **Step 2: Add observable model assertions**

  In `finance-model.test.mjs`, assert the generated artifact has no deleted tables or standalone diagram, `ledger_and_close` contains `t_ledger_period_balance`, the two snapshots expose only the approved minimal fields, and no relation targets a deleted table. Keep assertions against the parsed generated model rather than grepping source text.

- [x] **Step 3: Run RED and verify the failure is caused by the old model**

  Run:

  ```bash
  node --test scripts/pdmaner/finance-model.test.mjs
  ```

  Expected: FAIL because the current generated artifact still has 103 tables, the five removed tables, the old diagram and old snapshot fields.

### Task 2: Remove workflow tables and generate the Account-only model

**Files:**
- Modify: `scripts/pdmaner/finance/manifest.mjs`
- Delete: `scripts/pdmaner/finance/manifests/04-balance-reserve-recovery.mjs`
- Modify: `scripts/pdmaner/finance/manifests/06-settlement.mjs`
- Modify: `scripts/pdmaner/finance/manifests/07-close.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance/generate-finance-model.mjs`
- Regenerate: `finance-service/docs/database/finance.pdma.json`

**Interfaces:**
- Consumes: Task 1 Oracle and tests.
- Produces: deterministic 98-table Finance PDManer artifact with seven diagrams and no Reserve/Recovery workflow relations.

- [x] **Step 1: Move the retained close projection and delete the old manifest**

  Move the unchanged `ledger_period_balance` `defineTable` block into `07-close.mjs`, include it in the `ledger_and_close` primary table list, remove the old manifest import/spread, then delete `04-balance-reserve-recovery.mjs`.

- [x] **Step 2: Narrow Settlement requirement snapshots**

  Keep `requirement_type`, source ID/version, decision, final `required_amount`, currency, effective window and `content_hash`. Remove `reserve_type`, `basis_amount`, `rate_value`, `release_terms_hash` from Candidate and Settlement snapshots and remove `reserve_case_id`, its index and relation from Settlement Snapshot. Update comments and enum descriptions so `RESERVE_REQUIREMENT` means a minimum prepaid security-deposit threshold, never a Finance-local hold Case.

- [x] **Step 3: Update executable policy and deterministic totals**

  Remove deleted-table CHECK bindings, immutable/version/hash whitelist entries and the old diagram. Update Candidate/Settlement CHECK expressions to the minimal restriction-versus-deposit matrix and update both generator table-count invariants and all expected totals to the Task 1 literals.

- [x] **Step 4: Generate and run GREEN**

  Run:

  ```bash
  node scripts/pdmaner/finance/generate-finance-model.mjs
  node --test scripts/pdmaner/finance-model.test.mjs
  node scripts/pdmaner/finance/generate-finance-model.mjs --check
  ```

  Expected: Finance tests pass; `--check` reports exactly `98 / 1221 / 166 / 161 / 172 / 28 / 7`.

### Task 3: Synchronize Finance authority, scenarios and requirements behavior

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify other existing requirements tests only when their current behavioral expectation changes.

**Interfaces:**
- Consumes: generated model and Account-only gate from Task 2.
- Produces: one consistent beginner-reviewable business story from deposit receipt through payout gate, dispute consumption and recovery.

- [x] **Step 1: Replace the active Finance table and event contract**

  Remove the five tables from the exact catalog, relations, diagrams, state descriptions and outbound payloads. Rename the active chart-account meaning from `CLIENT_RESTRICTED_FUNDS` to `CLIENT_SECURITY_DEPOSIT`; keep `CLIENT_RECOVERY_RECEIVABLE`. Remove `ReserveHeld`, `ReserveReleased` and `RecoveryApplied`; retain `ClientRecoveryReceivableCreated` only with Account/Journal/source identifiers and no removed Case ID.

- [x] **Step 2: Rewrite the real-world scenario**

  Show a Client cash deposit posted `Dr BANK_CASH / Cr CLIENT_SECURITY_DEPOSIT`, Settlement checking the required minimum without moving AVAILABLE into a Reserve Case, an allowed dispute consuming the deposit by Journal, and any excess becoming `CLIENT_RECOVERY_RECEIVABLE`. Show future netting/manual receipt as ordinary Journals. Remove every example row and ID belonging to the five deleted tables.

- [x] **Step 3: Update requirements and E2E behavior before their assertions**

  Replace “freeze/release Reserve Case” behavior with the prepaid Account gate. E2E-10 must keep T+N maturity independent and check the deposit only at payout. E2E-11 must keep the refund/dispute ordering but use the security-deposit Account and Journal-only recovery. Update the Client/Operations capabilities and query contracts to return current Account balances and Journal source history, not a recovery plan or application list.

- [x] **Step 4: Run requirements tests**

  Run:

  ```bash
  npm --prefix requirements-site test
  npm --prefix requirements-site run lint
  ```

  Expected: all tests and lint pass with the Account-only vocabulary and no active removed-table expectations.

### Task 4: Synchronize shared entrances and run the full gate

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `requirements-site/tests/site-content.test.mjs` if exact Finance totals are asserted there.

**Interfaces:**
- Consumes: final Finance model statistics and semantics from Tasks 2-3.
- Produces: synchronized repository navigation and reproducible full validation evidence.

- [x] **Step 1: Update only Finance portions of shared files**

  Replace Finance totals with `98 / 1,221 / 166 / 161 / 172 / 28 / 7`, retain Tax/Fiscal at 23, add this implementation record to the Finance authority links, and describe Security Deposit/Recovery as Account + Journal without changing current statistics or decisions for the other five systems.

- [x] **Step 2: Run deterministic generators and model suites**

  Run all available PDManer generators with `--check`, then:

  ```bash
  node --test scripts/pdmaner/client-contract-model.test.mjs \
    scripts/pdmaner/commerce-model.test.mjs \
    scripts/pdmaner/payment-model.test.mjs \
    scripts/pdmaner/finance-model.test.mjs \
    scripts/pdmaner/risk-compliance-model.test.mjs \
    scripts/pdmaner/platform-support-model.test.mjs
  ```

- [x] **Step 3: Run stale-token, link and diff gates**

  Confirm active generator/model/current-authority files contain none of the five physical table names, `reserve_case_id`, `balance_reserve_recovery`, `CLIENT_RESTRICTED_FUNDS`, `ReserveHeld`, `ReserveReleased` or `RecoveryApplied`. Historical superseded plans may retain their old-stage descriptions when explicitly labeled historical. Verify all changed relative Markdown links resolve and run:

  ```bash
  git diff --check
  ```

- [x] **Step 4: Record the remaining manual gate**

  Report that PDManer 4.9.3 in-app visual/compatibility review remains manual. Do not claim Migration or Java implementation.
