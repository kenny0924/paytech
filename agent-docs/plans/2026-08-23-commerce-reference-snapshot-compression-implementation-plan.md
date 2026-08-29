# Commerce Reference Snapshot Compression Implementation Plan

> Historical implementation record: the 2026-08-23 Order Snapshot contract in this plan was superseded by [the 2026-08-24 Commerce Order Context design](./2026-08-24-commerce-order-context-simplification-design.md). Do not restore its retired Snapshot schema/hash or identifier fields.

Status: implemented and automatically validated on 2026-08-23; PDManer 4.9.3 in-app compatibility remains a manual gate.

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven development for generator behavior changes. The repository forbids creating branches, commits, migrations, Java modules or runtime infrastructure unless the user explicitly asks; execute in the current workspace and leave changes uncommitted.

**Goal:** Replace Commerce's six-table copied Order Snapshot with the approved three-table reference snapshot and keep all cross-system documents, requirements, scenarios and generated PDManer artifacts consistent.

**Architecture:** Commerce keeps Root, Legal and Item facts locally. It references CCT immutable Commercial Term Scope rows and Finance immutable Tax Snapshot rows by exact stable IDs plus the approved source hashes; Finance freezes fetched CCT inputs into its own Economics Snapshot, and common Checkout reads remain Commerce-local.

**Tech Stack:** Markdown architecture documents, TypeScript requirements catalog, Node.js `node:test`, deterministic ESM PDManer generator, PDManer 4.9.3 JSON artifact.

**Spec:** `agent-docs/plans/2026-08-23-commerce-reference-snapshot-compression-design.md`

## Global Constraints

- Target Commerce totals are exactly 23 tables, 329 fields, 37 unique indexes, 18 access indexes, 26 local relations and 6 diagram groups.
- Target Order Snapshot is exactly `t_order_snapshot` (24 fields), `t_order_legal` (21 fields) and `t_order_item` (17 fields).
- Delete `t_order_tax_line`, `t_order_commercial_term` and `t_order_term_component`; do not replace them with generic reference/JSON tables.
- Cross-system IDs are ordinary fields only. Never add a cross-database foreign key, view, query or transaction.
- Exact CCT/Finance historical queries must never resolve current `ACTIVE` data or fall back to current/default/zero fee.
- Common Checkout reads remain local; complete tax details are queried from Finance only when needed.
- Preserve all unrelated user changes. Do not create a branch, commit, migration, Java module, database or environment.

---

### Task 1: Make the model contract fail for the approved target

**Files:**
- Modify: `scripts/pdmaner/commerce-model.test.mjs`
- Modify: `scripts/pdmaner/commerce-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: the exact table/field/index/relation totals and field manifests from the design spec.
- Produces: a failing executable oracle that the generator and current docs cannot satisfy before implementation.

- [x] **Step 1: Change the independent table oracle**

Replace the six-table Snapshot manifest with these exact roots:

```text
t_order_snapshot: 24 fields
t_order_legal: 21 fields
t_order_item: 17 fields
```

Assert that the three removed physical table names are absent and that the root contains `client_pricing_scope_id`, nullable `partner_share_scope_id`, and `finance_tax_snapshot_hash`, while all nine deleted Root fields are absent.

- [x] **Step 2: Change totals and relation/index oracles**

Use these exact totals:

```js
{
  tables: 23,
  fields: 329,
  uniqueIndexes: 37,
  accessIndexes: 18,
  relations: 26,
  diagramGroups: 6,
}
```

Delete all approved index/relation manifest rows owned by the three removed tables.

- [x] **Step 3: Add scenario-document assertions**

Assert that the scenario names the 23-table/three-Snapshot-table target, includes Finance immutable Tax Snapshot lookup and CCT immutable Term Scope lookup, and does not list the three removed tables as current mappings.

- [x] **Step 4: Verify RED**

Run:

```bash
node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
```

Expected: FAIL because the current generator/model still contain 26 tables and the current scenario still maps Tax Line/Term/Component locally.

### Task 2: Update canonical requirements and cross-system contracts

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/README.md`

**Interfaces:**
- Consumes: exact reference semantics and fail-closed rules from the design spec.
- Produces: canonical CCT immutable Scope query, Finance immutable Tax query, corrected Checkout Risk preparation input, and updated COM-I04/FIN-I17 contracts.

- [x] **Step 1: Update CCT contracts**

Document that CCT-I13 still returns and hashes the full V4 Bundle for real-time validation, but Commerce persists only Assignment ID, two Agreement Association IDs, two Term Scope IDs, Legal/Seller values and V4 Hash. Add `GetImmutableCommercialTermScope(term_scope_id, agreement_operating_entity_id, context_as_of_time)` semantics: exact historical Scope-closure read, no current resolution, full owner/Plan/Version/Agreement/Scope/Component response, long-term retention and fail-closed validation. Explicitly prohibit treating the full V4 Hash as a single-Scope validation input.

- [x] **Step 2: Update Risk contracts**

Replace the circular requirement for a final Order Snapshot Hash during Checkout Assessment with an immutable `OrderPreparationContext`/input hash. Keep only the adopted Assessment ID in Commerce; Risk continues to own Outcome, Reason, Rule Hit and input history. Payment Attempt Risk continues to consume the final Snapshot Hash.

- [x] **Step 3: Update Finance contracts**

Define FIN-I17 as returning Tax Snapshot ID, Calculation Hash, aggregate collected/reverse-charge tax and per-Item collected tax. Add exact immutable Tax Snapshot detail query. Change PAYMENT Economics calculation from consuming Commerce-copied terms to querying CCT by the frozen Scope IDs and immediately freezing the complete result in Finance; failure schedules local compensation and never uses current/default/zero fees.

- [x] **Step 4: Update requirements and sequences**

Rewrite COM-03/COM-I04, CCT-I13, FIN-I17 and the Hosted Checkout global flow so they no longer claim Commerce persists or returns Profile/Classification IDs, Assignment internals, Tax Lines or complete Commercial Term components. Preserve `industry_code + mcc_code`, legal self-containment, amount-based refunds and the V4 CCT hash.

- [x] **Step 5: Run requirement tests**

Run:

```bash
npm --prefix requirements-site test
```

Expected: PASS after all canonical copy and sequence assertions are synchronized.

### Task 3: Update Commerce authority, scenario and physical decisions

**Files:**
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md`
- Modify: `agent-docs/plans/2026-08-23-commerce-balanced-physical-model-design.md`
- Modify: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`

**Interfaces:**
- Consumes: three-table/62-field manifest and the updated CCT/Risk/Finance contracts.
- Produces: one consistent 23-table Commerce authority and a realistic NOVA example using exact external references.

- [x] **Step 1: Replace the Commerce Snapshot catalog**

Set the document to 23 tables and replace the six-table aggregate with the exact 24/21/17 field lists. Remove the three deleted table definitions, their relations, indexes, transaction writes and BCR exclusion wording. Document Snapshot Schema V2 and its canonical coverage.

- [x] **Step 2: Rewrite the NOVA scenario**

Map `client_pricing_scope_id=720000000000000403`, `partner_share_scope_id=720000000000000406`, `finance_tax_snapshot_id=740000000000000101` and a 64-hex Finance Calculation Hash into the three local tables. Explain that Finance owns Tax Line `740000000000000102` and CCT owns all term components; remove Commerce IDs for the deleted rows.

- [x] **Step 3: Update the physical-design rationale**

Record 23 tables, 329 fields, 37 unique indexes, 18 access indexes and 26 relations. Set the immutable-table whitelist to nine tables by removing the three deleted tables. Replace copied Tax/Term constraints with exact-reference invariants.

- [x] **Step 4: Run scenario tests**

Run:

```bash
node --test scripts/pdmaner/commerce-scenario-doc.test.mjs
```

Expected: PASS.

### Task 4: Implement and regenerate the deterministic PDManer model

**Files:**
- Modify: `scripts/pdmaner/generate-commerce-model.mjs`
- Modify: `scripts/pdmaner/commerce-field-metadata.mjs`
- Generate: `commerce-service/docs/database/commerce.pdma.json`
- Modify: `scripts/pdmaner/commerce-model.test.mjs` only when an oracle defect, not a production mismatch, is proven.

**Interfaces:**
- Consumes: the failing oracle from Task 1 and exact field lists from the spec.
- Produces: deterministic PDManer JSON with the approved target and no deleted IDs/cells.

- [x] **Step 1: Replace generator table manifests**

Remove the three deleted table blocks. Rewrite `t_order_snapshot` and `t_order_legal` to the exact ordered lists in the spec. Preserve all `t_order_item` fields unchanged.

- [x] **Step 2: Remove obsolete metadata and add new metadata**

Delete table names, field comments, enum/open-catalog registrations and table comments that only serve the deleted tables or deleted fields. Add direct Chinese descriptions for `client_pricing_scope_id`, `partner_share_scope_id` and `finance_tax_snapshot_hash`; the Hash comment must identify Finance `tax_snapshot.calculation_hash` and SHA-256 lower-case 64-hex encoding.

- [x] **Step 3: Remove obsolete indexes, relations and diagram cells**

Delete six unique indexes, one access index and four relationships owned by the deleted tables. Keep the Order Snapshot diagram with `commerce_order`, `order_snapshot`, `order_legal` and `order_item` only. Update immutable table rules and expected totals.

- [x] **Step 4: Generate the model**

Run:

```bash
node scripts/pdmaner/generate-commerce-model.mjs
```

Expected generator summary:

```text
23 tables
329 fields
37 unique indexes
18 access indexes
26 relations
6 diagram groups
```

- [x] **Step 5: Verify GREEN and determinism**

Run:

```bash
node --test scripts/pdmaner/commerce-model.test.mjs
node scripts/pdmaner/generate-commerce-model.mjs --check
```

Expected: PASS and byte-for-byte current model confirmation.

### Task 5: Update navigation counts and run the full consistency gate

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: any requirements-site test that intentionally freezes the old Commerce count/copy semantics.

**Interfaces:**
- Consumes: generated model manifest and all updated canonical documents.
- Produces: repository-wide navigation with no active 26/390/43/19/30 claim and complete automated evidence.

- [x] **Step 1: Replace current manifest references**

Change active Commerce navigation to `23 tables / 329 fields / 37 unique indexes / 18 access indexes / 26 relations`. Historical plan passages may retain old values only when explicitly labeled historical and must not be linked as the current target.

- [x] **Step 2: Scan for stale current semantics**

Run:

```bash
rg -n "Commerce.*26|26 表|390 字段|43 个唯一索引|19 个访问索引|30 条本库关系|order_tax_line|order_commercial_term|order_term_component|完整结构化组件" AGENTS.md README.md agent-docs requirements-site/content commerce-service/docs/database scripts/pdmaner
```

Review every hit. Keep only explicit history or Finance/CCT source-table ownership references; remove any claim that the three deleted tables remain current Commerce tables.

- [x] **Step 3: Run all automated gates**

Run:

```bash
node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
node scripts/pdmaner/generate-commerce-model.mjs --check
npm --prefix requirements-site test
```

Expected: all tests pass with no model drift.

- [x] **Step 4: Record the remaining manual gate**

Report that PDManer 4.9.3 in-app open/check remains manual. Do not claim Migration, Java, database or runtime completion.
