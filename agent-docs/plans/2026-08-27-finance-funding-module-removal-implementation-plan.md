# Finance Funding Module Removal Implementation Plan

> **后续专项取代范围（2026-08-27）：** 本文的 103 表统计是通用 Funding 四表删除后的历史阶段；Settlement/Tax Payment 直接 Account 和 `BANK_CASH` 行锁仍有效。Reserve Case、Restricted Fund、Recovery Case/Application 及其流程已由 [Finance 保证金 Account 精简实施记录](./2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md) 删除，当前基线为 98 表。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 Finance 通用 Funding 四表，以 Settlement、Tax Payment、Account、付款事件、Recon Match 与 Journal 直接承载相同业务控制。

**Architecture:** Journal 继续是唯一会计事实，`t_account` 是可重建余额投影和跨 Settlement/Tax Bank Cash 的共同互斥点。Settlement 直接冻结 LOCKED Account 与 Cash Required Amount，Tax Payment 直接冻结 Tax Payable Account 并使用活动槽阻止 UNKNOWN 期间的替代执行。

**Tech Stack:** Node.js `node:test`、PDManer 4.9.3 JSON、Finance Schema DSL、Markdown requirements-site。

**Spec:** `agent-docs/plans/2026-08-27-finance-funding-module-removal-design.md`

## Global Constraints

- 删除 `t_funding_position`、`t_funding_commitment`、`t_funding_allocation`、`t_funding_move`，不得新增替代 Funding、Availability、Balance Lot 或 Treasury 表。
- 目标基线必须为 103 表 / 1,285 字段 / 172 唯一索引 / 169 访问索引 / 178 关系 / 29 条 1:1 / 8 Diagram Groups；Tax/Fiscal 主图保持 23 表。
- Settlement 必须直接保存 `locked_account_id` 与 `cash_required_amount`；Settlement Payment Attempt 不再保存 Allocation ID。
- Tax Payment 必须直接保存 `tax_payable_account_id` 与 `active_slot`；不得保存三个 Funding ID。
- Bank Cash 并发门禁必须使用 Cash Account 对应 BANK_CASH `t_account` 行锁与活动 Settlement/Tax Payment 汇总；UNKNOWN 不释放。
- Journal 是唯一不可变会计事实；状态事件、Recon Match 与 Journal 共同替代 Funding Move 审计。
- 保留退款 Funding 已删除的负向防回归门禁。
- 不创建 Java、Migration、物理数据库、部署环境或基础设施，不提交 Git。
- 保留 PDManer 4.9.3 应用内人工兼容检查为未完成门禁。

---

### Task 1: Write the failing Finance schema and scenario gates

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: the historical pre-change 107-table Finance model and then-current Funding-positive documentation.
- Produces: red tests that define table absence, replacement fields, indexes, check contracts and scenario language.

- [x] **Step 1: Replace the Funding-positive model assertions with the approved negative and replacement assertions**

```js
for (const table of [
  "t_funding_position",
  "t_funding_commitment",
  "t_funding_allocation",
  "t_funding_move",
]) assert.ok(!entities.has(table));

assert.deepEqual(
  ["locked_account_id", "cash_required_amount"].filter((field) => !fieldKeys("t_settlement").includes(field)),
  [],
);
assert.ok(!fieldKeys("t_settlement").includes("funding_commitment_id"));
assert.ok(!fieldKeys("t_settlement_payment_attempt").includes("bank_cash_allocation_id"));

assert.deepEqual(
  ["tax_payable_account_id", "active_slot"].filter((field) => !fieldKeys("t_tax_payment").includes(field)),
  [],
);
for (const field of ["funding_commitment_id", "tax_payable_allocation_id", "bank_cash_allocation_id"]) {
  assert.ok(!fieldKeys("t_tax_payment").includes(field));
}
assert.equal(MODEL_CHECK_CONTRACTS.ck_tax_payment_state,
  "(status IN ('CREATED','SUBMITTED','PROCESSING','BANK_ACCEPTED','UNKNOWN') AND active_slot=1) OR (status IN ('FAILED','RECONCILED') AND active_slot IS NULL)");
```

- [x] **Step 2: Require the scenarios to explain Account-derived cash capacity and forbid positive Funding rows**

```js
assert.doesNotMatch(text, /\| `t_funding_(?:position|commitment|allocation|move)` \|/u);
assert.match(text, /Bank Cash Account\.net_balance/u);
assert.match(text, /活动 Settlement Payment/u);
assert.match(text, /活动 Tax Payment/u);
assert.match(text, /UNKNOWN.*不释放/u);
```

- [x] **Step 3: Run the focused tests and capture the expected red result**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: FAIL because the four Funding tables and old fields still exist, replacement fields/checks are absent, and the scenario still contains Funding-positive rows.

### Task 2: Remove Funding from the schema source and regenerate PDManer

**Files:**
- Move: `scripts/pdmaner/finance/manifests/04-balance-funding.mjs` -> `scripts/pdmaner/finance/manifests/04-balance-reserve-recovery.mjs`
- Modify: `scripts/pdmaner/finance/manifest.mjs`
- Modify: `scripts/pdmaner/finance/generate-finance-model.mjs`
- Modify: `scripts/pdmaner/finance/manifests/02-tax-economics-cost.mjs`
- Modify: `scripts/pdmaner/finance/manifests/06-settlement.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `finance-service/docs/database/finance.pdma.json`

**Interfaces:**
- Consumes: Task 1 failing gates and the exact schema decisions in the design spec.
- Produces: deterministic 103-table model and independent Oracle with direct Settlement/Tax Account navigation.

- [x] **Step 1: Delete the four table definitions and Funding-only constants/checks/bindings**

Rename the manifest file and import so no active source path retains the removed Funding module name. Remove the complete `defineTable` blocks for `funding_position`, `funding_commitment`, `funding_allocation`, and `funding_move`; remove their enums and every `ck_funding_*` contract/binding. Rename the diagram to `balance_reserve_recovery` / `余额、准备金与追偿`, keeping only `ledger_period_balance`, `reserve_case`, `restricted_fund`, `restricted_fund_move`, `recovery_case`, and `recovery_application` as primary tables. Update both generator manifest-count invariants from 107 to 103; do not weaken or remove those invariants.

- [x] **Step 2: Add direct Settlement fields and access paths**

```js
field("locked_account_id", "BIGINT", "NN", "已锁定应付账户标识", "本次结算直接占用的同收款方、经营主体和币种 LOCKED Account"),
field("cash_required_amount", types.amount, "NN", "银行现金需求金额", "本次结算预计从付款银行账户扣除且必须大于零的总金额"),
```

Add `idx_settlement_locked(locked_account_id,status)`, relation `locked_account_id -> account.id`, and `idx_settle_attempt_cash_status(cash_account_id,status)`. Remove `funding_commitment_id`, `bank_cash_allocation_id` and their indexes/relations. Make `settlement_payment.amount` equal `settlement.cash_required_amount` in comments and checks.

- [x] **Step 3: Add direct Tax Payment fields, activity slot and access paths**

```js
field("tax_payable_account_id", "BIGINT", "NN", "应付税账户ID", "本次付款直接清偿的同主体、税务机关和币种 TAX_PAYABLE/NONE Account"),
field("active_slot", "TINYINT", "N", "活动付款槽", "付款仍可能形成银行扣款时固定为 1，FAILED 或 RECONCILED 后固定为 NULL"),
```

Add `uk_tax_payment_active(tax_return_id,active_slot)`, `idx_tax_pay_payable_status(tax_payable_account_id,status)`, `idx_tax_pay_cash_status(cash_account_id,status)`, relation `tax_payable_account_id -> account.id`, and this check:

```sql
(status IN ('CREATED','SUBMITTED','PROCESSING','BANK_ACCEPTED','UNKNOWN') AND active_slot=1)
OR (status IN ('FAILED','RECONCILED') AND active_slot IS NULL)
```

Remove the three Funding IDs, three unique indexes and three Funding relations. Rewrite `request_hash` to cover Return/Authority/Registration, Tax Payable Account, Binding/Cash Account, amount and currency.

- [x] **Step 4: Update the independent Oracle totals and approved digests**

Set totals to:

```js
{
  tables: 103,
  fields: 1285,
  uniqueIndexes: 172,
  accessIndexes: 169,
  relations: 178,
  oneToOneRelations: 29,
  diagramGroups: 8,
}
```

Remove all Funding paths/checks and add the new Settlement/Tax fields, indexes, relations, `ck_tax_payment_state`, diagram name and primary-table set. Do not calculate approved digests from production imports; generate the model, inspect it, and freeze the independently observed SHA-256 values.

- [x] **Step 5: Generate the model and turn the focused tests green**

Run:

```bash
node scripts/pdmaner/finance/generate-finance-model.mjs
node --test scripts/pdmaner/finance-model.test.mjs
```

Expected: all Finance model tests PASS and generator reports the new deterministic model path and digest.

### Task 3: Rewrite Finance authority and real-world scenarios

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify: current authority plan records that still claim generic Funding exists

**Interfaces:**
- Consumes: Task 2 direct schema and exact target totals.
- Produces: one beginner-readable, internally consistent Finance explanation with no live Funding-positive semantics.

- [x] **Step 1: Replace the Balance/Funding catalog with Balance/Reserve/Recovery**

Document the six remaining tables and state that Account/Journal own balances, while Restricted Fund and Recovery remain separate business evidence.

- [x] **Step 2: Rewrite Settlement lifecycle**

Describe direct `locked_account_id`, `cash_required_amount`, AVAILABLE-to-LOCKED Journal, Account row locking, Payment Attempt UNKNOWN/retry/cancel rules, complete Debit Recon and final payment Journal. Remove every live Funding Commitment/Allocation/Move reference.

- [x] **Step 3: Rewrite Tax Payment lifecycle**

Describe direct `tax_payable_account_id`, `active_slot`, exact Return amount ceiling, Bank Cash Account lock, execution-key idempotency, FAILED evidence, UNKNOWN blocking and complete Debit Recon. Remove all three old Funding IDs from the field catalog and scenario rows.

- [x] **Step 4: Replace the Funding scenario rows with derived cash capacity evidence**

Use the formula exactly:

```text
available_cash = bank_cash_account.net_balance - active_settlement_cash - active_tax_cash
```

Explain that no extra table stores this result and that all writers lock the same BANK_CASH Account row. Update the scenario totals to 103 / 1,285 / 172 / 169 / 178 / 29 / 8 and Balance/Reserve/Recovery to 6 tables.

- [x] **Step 5: Run the Finance scenario gate**

Run:

```bash
node --test scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: PASS with Funding-positive rows absent and direct Account-derived capacity language present.

### Task 4: Synchronize requirements, sequences, tests and shared baselines

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`

**Interfaces:**
- Consumes: Tasks 2-3 target model, direct control language and current merged Risk 85-table baseline.
- Produces: consistent requirements catalog, global sequences, navigation and all-system statistics.

- [x] **Step 1: Rewrite FIN-13, FIN-21, FIN-23 and Finance source-of-truth/domain descriptions**

Replace Funding with direct Settlement/Tax Payment plus Account-derived Bank Cash capacity. Preserve refund semantics: Finance only consumes `RefundSucceeded`; refund never reserves Finance Funding; only DisputeLost may consume restricted reserve under approved Risk rules.

- [x] **Step 2: Rewrite the Settlement and Tax global sequences**

Require the common BANK_CASH Account lock, active-payment aggregation, UNKNOWN blocking, authoritative FAILED evidence, complete Debit Recon and Journal. Remove positive Commitment/Allocation/Position steps.

- [x] **Step 3: Update tests to enforce absence and replacement language**

Keep existing refund negative assertions. Add positive assertions for `locked_account_id`, `tax_payable_account_id`, the Account-derived formula, UNKNOWN non-release and direct Account-control sequence labels; add negative assertions for generic Funding tables and Funding-positive sequence labels.

- [x] **Step 4: Semantically merge shared documents**

Start from the current main-workspace versions so Risk remains at its final `85 / 933 / 111 / 82 / 131 / 11 / 8` baseline with 63 CHECK and DD 19, while Platform Support's current shared authority remains intact. Change only Finance to `103 / 1,285 / 172 / 169 / 178 / 29 / 8`, keep Tax/Fiscal 23, and replace current Funding-positive wording. Do not copy an older whole shared file over the merged baseline.

- [x] **Step 5: Run requirements build, tests and lint**

Run:

```bash
npm --prefix requirements-site run build
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: build succeeds, all requirements tests pass, and ESLint reports no errors.

### Task 5: Full verification, independent review and main-workspace sync

**Files:**
- Modify: `agent-docs/plans/2026-08-27-finance-funding-module-removal-implementation-plan.md`
- Sync: only the exact changed files to `/Users/kenny/Desktop/Workspace/Code-MoR`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: verified Finance 103-table baseline in both the isolated worktree and main workspace.

- [x] **Step 1: Run deterministic generator checks and targeted suites**

```bash
node scripts/pdmaner/finance/generate-finance-model.mjs --check
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: generator current and all focused tests PASS.

- [x] **Step 2: Run the full PDManer and requirements suites**

```bash
node --test scripts/pdmaner/*.test.mjs
npm --prefix requirements-site test
```

Expected: all tests PASS; any unrelated pre-existing failure must be identified separately and must not be hidden.

- [x] **Step 3: Verify whitespace, links, hashes and exact totals**

Run `git diff --check`, validate relative Markdown links in every changed document, calculate the Finance model SHA-256, and independently inspect PDManer totals and diagram primary-table counts.

- [x] **Step 4: Obtain an independent read-only review and fix every confirmed issue**

The reviewer must check table/field/index/relation counts, absence of generic Funding, Settlement/Tax concurrency semantics, retained refund negative gates, Risk shared baseline preservation and generated-model determinism.

- [x] **Step 5: Sync exact files and re-run main-workspace checks**

Use an explicit file list; never replace directories or unrelated files. Re-run Finance generator `--check`, Finance focused tests, full PDManer tests, requirements build/test/lint, link checks and SHA-256 from `/Users/kenny/Desktop/Workspace/Code-MoR`.

- [x] **Step 6: Mark this record complete without claiming the manual PDManer gate**

Record actual counts, test totals and model SHA-256. Keep PDManer 4.9.3 application review unchecked. Do not commit Git.

## Verification Record

- Finance model: `103 / 1,285 / 172 / 169 / 178 / 29 / 8`; Tax/Fiscal 23 primary tables; Balance/Reserve/Recovery 6 primary tables.
- Finance model SHA-256: `17ca4dbcca5b803a18180f5324e4da80034e4b64e1f1b8f34c4d385e17ad901e`.
- Independent structural SHA-256: `0cdd1ec2089ce1e0f01f6ca4240908ec642e1d97370ec146d7c398d0908b6e83`.
- Finance focused tests: 29/29; isolated-worktree PDManer/scenario tests: 154/154.
- requirements-site: build passed, 84/84 tests passed, ESLint passed.
- Two independent read-only reviews found no Critical issue. Confirmed Important issues were fixed through exact FAILED/UNKNOWN evidence checks, late-Debit suspense posting, Settlement first-Attempt/current-pointer closure, cross-Return Tax Payable aggregation, common BANK_CASH locking and predecessor-plan supersession notices.
- Main-workspace generator `--check`: CCT、Commerce、Payment、Finance、Risk 全部通过；Platform Support 由其并行物理设计任务随后集成和验证。
- Main-workspace Finance focused tests: 29/29; full available PDManer/scenario tests: 173/173.
- Main-workspace requirements-site: build passed, 84/84 tests passed, ESLint passed.
- Main-workspace independent recount matches `103 / 1,285 / 172 / 169 / 178 / 29 / 8`; Tax/Fiscal is 23 primary tables and Balance/Reserve/Recovery is 6 primary tables. Model SHA-256 matches the worktree digest.
- The removed `04-balance-funding.mjs` was moved to `/private/tmp/Code-MoR-04-balance-funding.mjs.backup-20260827`; the pre-sync main-file snapshot is under `/private/tmp/Code-MoR-finance-sync-backup-20260827`.
- Finance writes are complete and shared files were explicitly released to the parallel Risk and Platform Support tasks for their own semantic increments.
- No Git commit, branch, Java, Migration, physical database, deployment or runtime environment was created.
- [ ] PDManer 4.9.3 application in-app compatibility review (manual gate).
