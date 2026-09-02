# Vault PDManer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the independent Vault database-design project and deterministically generate its approved three-table PDManer 4.9.3 model.

**Architecture:** Keep the shared PDManer template read-only and declare the exact Vault schema in a focused manifest plus CHECK-policy sidecar. A deterministic Node.js generator produces one diagram containing Record, Protect Request and Access Event; an independent `node:test` oracle validates the final JSON, security exclusions and byte-for-byte regeneration.

**Tech Stack:** Markdown, Node.js ES modules, `node:test`, PDManer 4.9.3 JSON, SHA-256 deterministic UUIDs, MySQL 8.4 physical semantics.

**Spec:** `docs/superpowers/specs/2026-09-02-vault-database-design.md`

## Global Constraints

- Generate exactly `3 tables / 30 fields / 3 unique indexes / 4 access indexes / 2 local relations / 0 one-to-one relations / 1 Diagram Group`.
- Keep only `t_vault_record`, `t_vault_protect_request` and `t_vault_access_event`.
- Do not add Binding, environment, Tenant, resource, status, version, expiry, sharding, Retention, Legal Hold, Destroy, compensation or Platform Audit structures.
- Preserve `REFERENCE` and `STABLE`; enforce server-keyed Stable fingerprint and Protect request-hash semantics as sidecar contracts.
- Preserve per-record AES-256-GCM, 12-byte nonce, wrapped DEK and KMS reference fields without persisting plaintext keys or real credentials.
- Keep the shared template byte-for-byte unchanged and strip all database connection data from the generated model.
- Do not create Java/Maven modules, Flyway migrations, a physical database, KMS resources, runtime configuration or deployment assets.
- Treat `agent-docs/VAULT_SERVICE_ARCHITECTURE.md` as historical where the approved spec supersedes its API, mTLS, Binding, lifecycle, topology, idempotency and audit design.
- Do not claim the PDManer 4.9.3 in-app manual review has passed.

---

### Task 1: Freeze the approved schema with a failing model contract

**Files:**
- Create: `scripts/pdmaner/vault-model.test.mjs`

**Interfaces:**
- Consumes: the approved spec and shared PDManer template.
- Produces: an independent executable oracle for exact table, field, index, relation, CHECK and forbidden-field contracts.

- [x] **Step 1: Declare the exact test-owned field and index manifests**

Require the three tables and all 30 fields with exact MySQL type, length, nullability and primary-key metadata. Require:

```text
t_vault_record: uk_vault_record_token(token), uk_vault_record_stable(data_type,value_fingerprint)
t_vault_protect_request: uk_vault_protect_request(caller_service,idempotency_key), idx_vault_protect_record(record_id)
t_vault_access_event: idx_vault_access_record(record_id,create_time), idx_vault_access_caller(caller_service,create_time), idx_vault_access_request(request_id)
```

- [x] **Step 2: Assert the exact relationships and safety exclusions**

Require both `record_id -> t_vault_record.record_id` `n:1` relationships. Reject fields matching:

```text
environment|tenant|binding|resource|plaintext|raw_value|masked|blind_index|key_version|algorithm|status|expiry|shard|bucket|compensation|payload_json
```

Allow the specifically approved `value_fingerprint`, `request_hash`, `ciphertext`, `encryption_nonce`, `wrapped_dek` and `kms_key_ref` fields only in their approved tables.

- [x] **Step 3: Assert deterministic generation and documentation alignment**

Require empty connection metadata, one Diagram Group, deterministic IDs, template digest
`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`, byte-for-byte `--check`, and physical-decision coverage of the four named CHECK contracts.

- [x] **Step 4: Run the focused test and capture RED**

Run:

```bash
node --test scripts/pdmaner/vault-model.test.mjs
```

Expected: FAIL because the Vault generator, manifest, policy, project model and physical decisions do not exist; syntax or test-fixture parsing failures are not acceptable RED reasons.

### Task 2: Implement the deterministic Vault manifest and generator

**Files:**
- Modify: `.gitignore`
- Create: `vault-service/.gitignore`
- Create: `scripts/pdmaner/vault/schema-dsl.mjs`
- Create: `scripts/pdmaner/vault/manifest.mjs`
- Create: `scripts/pdmaner/vault/model-policy.mjs`
- Create: `scripts/pdmaner/vault/generate-vault-model.mjs`
- Generate: `vault-service/docs/database/vault.pdma.json`

**Interfaces:**
- Consumes: Task 1 exact contract and `agent-docs/templates/pdmaner/template.pdma.json`.
- Produces: deterministic `TABLES`, `RELATIONS`, `CHECK_CONTRACTS`, `DIAGRAM_GROUPS`, model generation, structural projection and `--check` APIs.

- [x] **Step 1: Establish the independent repository boundary**

Add `/vault-service/` beside the other independently versioned repositories in the root `.gitignore`. Initialize
`vault-service` on branch `main` and add the same OS/IDE, Java output, runtime, local-secret and PDManer-backup ignores
used by the existing database-design service repositories. This must happen before generation so the generated model
is never accidentally tracked by the workspace repository.

- [x] **Step 2: Define a narrow schema DSL**

Provide validated constructors for field, primary key, unique/access index, relationship and table. Enforce lower
`snake_case`, 32-character names, exactly one Snowflake `BIGINT` primary key, exact nullability, explicit Chinese
names/descriptions and immutable-versus-mutable audit timestamps.

- [x] **Step 3: Declare the three-table production manifest**

Encode every field and index from spec section 8. Use `ascii/ascii_bin` for `token`, `data_type`, `token_mode`,
`caller_service`, `idempotency_key`, `action`, `purpose`, `outcome`, `result_code` and `policy_version`. Declare the two
local Record relationships and ensure relation fields have left-prefix indexes.

- [x] **Step 4: Declare sidecar policy contracts**

Export these exact named CHECK contracts:

```text
ck_vault_record_mode
ck_vault_record_fingerprint
ck_vault_access_action
ck_vault_access_outcome
```

Also declare the one `vault_core` Diagram Group and the forbidden-table/field allowlists used by production validation.

- [x] **Step 5: Implement deterministic template derivation**

Clone the approved template, set model name `Code-MoR Vault Database V1`, clear `dbConn`, `dbConns` and
`profile.default.dbConn`, normalize BIGINT/Date/Binary Java/MyBatis mappings, enforce naming rules, build entities,
indexes, correlations and one diagram, then serialize with two-space indentation and one trailing newline.

- [x] **Step 6: Generate the model**

Run:

```bash
node scripts/pdmaner/vault/generate-vault-model.mjs
```

Expected: write `vault-service/docs/database/vault.pdma.json` and report the exact approved totals.

### Task 3: Create the independent Vault project and physical decisions

**Files:**
- Create: `vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md`
- Reuse generated: `vault-service/docs/database/vault.pdma.json`

**Interfaces:**
- Consumes: generated model totals/digests and the approved spec.
- Produces: an independently versioned Vault database-design project with a reviewable physical baseline.

- [x] **Step 1: Write the physical decisions**

Document exact totals and SHA-256 digests, template derivation, field types, binary collations, indexes, relationships,
CHECK sidecars, Protect/Stable concurrency, encryption/AAD, audit failure behavior, explicit exclusions and the pending
PDManer 4.9.3 in-app review.

- [x] **Step 2: Run focused validation**

Run:

```bash
node --test scripts/pdmaner/vault-model.test.mjs
node scripts/pdmaner/vault/generate-vault-model.mjs --check
node -e "JSON.parse(require('node:fs').readFileSync('vault-service/docs/database/vault.pdma.json','utf8'))"
```

Expected: all commands pass and the check command reports no model drift.

### Task 4: Align active architecture navigation and run regression checks

**Files:**
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`
- Modify: `docs/superpowers/plans/2026-09-02-vault-pdmaner.md`

**Interfaces:**
- Consumes: Tasks 2–3 generated totals and current authority boundary.
- Produces: consistent navigation stating that Vault has a three-table generated design while Java/runtime work remains unimplemented.

- [x] **Step 1: Add a precise supersession notice**

Point all Vault/database routing to the approved spec and generated physical decisions. State that the previous Binding,
mTLS, complex lifecycle and broad audit design is historical for current V1; preserve unchanged PCI/non-card and
business-ownership boundaries.

- [x] **Step 2: Update repository layout and current phase**

List `vault-service` as an independent database-design project. Record actual model totals and explicitly state that it
contains no Java, Migration, database, KMS, authentication or deployment implementation.

- [x] **Step 3: Run final verification**

Run:

```bash
node --test scripts/pdmaner/vault-model.test.mjs
node --test scripts/pdmaner/*.test.mjs
node scripts/pdmaner/vault/generate-vault-model.mjs --check
npm --prefix requirements-site test
git diff --check
```

Expected: focused Vault validation, the full PDManer suite, requirements tests, deterministic generation and whitespace
validation all pass. Report PDManer 4.9.3 in-app manual review as pending.

## Completion Evidence

- Completed: 2026-09-02.
- Parent generator/oracle history through `c67eadf` and independent `vault-service` commit `4e7bc4a` freeze the generated 3-table / 30-field / 3-non-PK-unique-index / 4-access-index / 2-local-`n:1` / 0-one-to-one / 1-`vault_core` model.
- `node --test scripts/pdmaner/vault-model.test.mjs`: PASS, 11 tests / 0 failures.
- `node --test scripts/pdmaner/*.test.mjs`: PASS, 488 tests / 0 failures. Node emitted the pre-existing experimental SQLite warning.
- `node scripts/pdmaner/vault/generate-vault-model.mjs --check`: PASS, model current with exact approved totals.
- `npm --prefix requirements-site test`: PASS, build succeeded and 123 tests / 0 failures. Vinext emitted its existing dynamic-route classification notice.
- `git diff --check`: PASS.
- PDManer 4.9.3 in-app review remains pending; no Vault Java/Maven, Flyway Migration, physical database, KMS integration, runtime authentication or deployment implementation exists.
