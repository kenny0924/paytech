# Finance Provider Reconciliation Simplification Implementation Plan

> **后续语义收口：** 本文完成的两层 Match 结构与删表决定继续有效；Provider 待清算/结算账户名称、Fee Accrual、最终 Settlement/Remittance Batch 门禁及 Journal 时点由 [Provider 三阶段账户实施计划](./2026-08-28-finance-provider-three-stage-accounting-implementation-plan.md) 补充并取代本文较早的“交易匹配完整即可作为银行目标”简写。

> **For agentic workers:** REQUIRED SUB-SKILLS: Use `superpowers:test-driven-development` for executable model contracts and `superpowers:verification-before-completion` before reporting success. This repository forbids commits for this task; preserve the dirty worktree and report file-level changes instead.

**Goal:** Remove the derived Provider Cash Item layer and make the two real reconciliation questions explicit: Provider transaction lines versus Finance receivable/debit items, then Provider batch net cash versus bank statement lines.

**Architecture:** Finance keeps local Provider Open/Debit Items from authoritative business facts, imports immutable Provider Batch/Item facts, and records immutable `t_provider_transaction_match` rows without requiring Payment to be online. A transaction-reconciled `VALIDATED` Batch is the Provider-side bank target. `t_bank_recon_match` then matches that Batch, Client/Partner Settlement attempts, or Tax Payments to immutable bank statement items using a frozen Cash Account Binding Version. Exceptions and Maker-Checker resolutions remain shared, but `RE_MATCH` identifies exactly one reconciliation layer.

**Tech Stack:** Node.js ESM model generator, PDManer 4.9.3 JSON, `node:test`, TypeScript requirements catalog, Markdown authority documents.

**Spec:** `agent-docs/database/FINANCE_DATABASE.md` plus the user-approved 2026-08-28 Provider reconciliation decision recorded by this plan.

## Global Constraints

- Delete `t_provider_cash_item`; a Provider Batch already freezes `cash_direction` and `net_amount` and is the unique bank-reconciliation target.
- Rename `t_provider_settle_alloc` to `t_provider_transaction_match` and `t_recon_match` to `t_bank_recon_match`.
- A Provider bank match references `provider_batch_id` directly. Do not add another cash projection or status table.
- `t_recon_resolution` replaces the ambiguous `recon_match_id` with nullable `provider_transaction_match_id` and `bank_recon_match_id`; an applied `RE_MATCH` supplies exactly one.
- Normal Provider transaction matching uses Finance-local frozen Provider references and never requires a synchronous Payment query. Unknown external lines open an exception and may be investigated manually.
- Remove `REFUND` from Cash Account Binding purpose. Refunds become Provider Debit Items and affect the Provider Batch net; they are not a standalone bank-cash purpose.
- Preserve `t_cash_account_binding` plus immutable `t_cash_account_binding_ver` so historical cash-account selection remains auditable.
- Do not create Java, Migration, a physical database, infrastructure, a branch or a Git commit.
- Preserve unrelated Commerce, Payment, Risk, Platform Support and existing dirty-worktree changes.

---

### Task 1: Freeze the simplified contract with failing tests

**Files:**
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-scenario-doc.test.mjs`

- [x] Update the independent Oracle first to exactly 97 tables, 1,211 fields, 166 unique indexes, 160 access indexes, 172 relations, 28 one-to-one relations and 7 Diagram Groups.
- [x] Assert the old three table names are absent, the two new Match tables expose the approved fields/relations/indexes, the Provider bank target is the Batch, and Binding purpose excludes `REFUND`.
- [x] Assert an applied `RE_MATCH` uses exactly one explicit Match reference and that the Provider diagram contains exactly 15 tables.
- [x] Run the Finance model/scenario tests and confirm they fail against the old generated model for the intended contract differences.

### Task 2: Change the executable model and regenerate PDManer

**Files:**
- Modify: `scripts/pdmaner/finance/manifests/05-provider-cash-recon.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Regenerate: `finance-service/docs/database/finance.pdma.json`

- [x] Rename the transaction and bank Match tables, fields, indexes, relations and CHECK contracts.
- [x] Delete Provider Cash Item and its two CHECK contracts.
- [x] Replace the Resolution target with the two explicit one-to-one targets and enforce the exact APPLIED matrix.
- [x] Remove Binding purpose `REFUND`; retain Provider Settlement, Settlement and Tax.
- [x] Generate and verify the exact target totals and deterministic digests.

### Task 3: Synchronize Finance authority and end-to-end behavior

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify matching requirements tests.

- [x] Explain the two layers with one realistic payment/refund/net-bank example.
- [x] Define `VALIDATED` as import/schema/arithmetic validation only, then derive transaction-reconciliation completeness before a Provider Bank Match may be confirmed.
- [x] Define the local-reference-only normal match path and the controlled unknown-line exception path.
- [x] Keep actual Provider fee differences in Provider Cost Confirm/Cost Adjustment, not in a fake cash row.
- [x] Update active scenarios to use the new table and field names and remove every active Provider Cash Item reference.

### Task 4: Synchronize shared Finance statistics and run gates

**Files:**
- Modify only Finance portions of `AGENTS.md`, `README.md`, `agent-docs/README.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/DATABASE_DESIGN_GUIDE.md`, and `agent-docs/database/README.md`.

- [x] Replace active Finance totals with `97 / 1,211 / 166 / 160 / 172 / 28 / 7`, keeping Tax/Fiscal at 23 and all other systems untouched.
- [x] Add this implementation record to active Finance authority links where implementation plans are cataloged.
- [x] Run Finance targeted tests, all PDManer generators/tests, requirements build/tests/lint, stale-token scans, Markdown-link checks where available, and `git diff --check`.
- [x] Record PDManer 4.9.3 in-app visual/compatibility review as the remaining manual gate.
