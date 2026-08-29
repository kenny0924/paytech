# Client & Contract PDManer V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Freeze the approved six-system logical database baseline as V1 and create a deterministic, validated PDManer 4.9.3 physical model for Client & Contract.

**Architecture:** The project PDManer template remains the metadata source. A deterministic Node.js generator reads the authoritative Client & Contract Markdown table catalog, applies an explicit physical-type/nullability/index/relation policy, and emits one model at `client-contract-service/docs/database/client-contract.pdma.json`. A real artifact test parses the generated JSON and validates its externally observable schema, references and security boundaries.

**Tech Stack:** PDManer 4.9.3 JSON, MySQL 8 physical types, Node.js built-ins, `node:test`, Maven/Java intentionally out of scope.

**Spec:** `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`; global rules in `agent-docs/DATABASE_DESIGN_GUIDE.md`, `agent-docs/database/README.md`, `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`, and `agent-docs/COMPENSATION_EVENT_GUIDE.md`.

**Execution status:** Automated implementation and verification completed on 2026-08-20. The PDManer 4.9.3 in-app checker remains a manual compatibility gate before any Migration phase.

> 2026-08-20 后续变更：本计划记录首次 77 表基线。用户批准的通用行级历史重构由 [CCT Row History Refactor Plan](./2026-08-20-cct-row-history-plan.md) 接续，完成后以最新逻辑目录和生成模型指标为准。

## Global Constraints

- Preserve the six core-system ownership boundaries and create no cross-database physical foreign key.
- Keep exactly the 77 approved Client & Contract tables; do not create Java modules, Migration SQL or databases in this phase.
- Use Snowflake `BIGINT` primary keys with `autoIncrement=false`, `DATETIME(6)` UTC, `DECIMAL(24,8)` money, `DECIMAL(18,10)` rates, `CHAR(2)` country and `CHAR(3)` currency.
- Keep every physical table/field/index name as lowercase `snake_case` and at most 32 characters.
- Store recoverable non-card sensitive values only as Vault Token Tuple, necessary Blind Index plus same-prefix key version, and Masked Value; never store PAN/SAD, Vault ciphertext or secrets.
- Keep one local `compensation_event`, one `command_idempotency`, no generic Inbox/Outbox and no central compensation table.
- Do not commit, create a branch, deploy, connect to a database or mutate any external environment.

---

### Task 1: Freeze the logical baseline and define derived-model overrides

**Files:**
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: the user-approved logical database documents dated 2026-08-20.
- Produces: a V1 approval marker and an explicit CCT derived-model normalization policy while preserving the shared template byte-for-byte.

- [x] **Step 1: Record the physical inconsistencies as explicit acceptance requirements**

  Require non-auto-increment Snowflake keys, Java/MyBatis `Long` for `BIGINT`, `BigDecimal` for `DECIMAL`, `DATETIME(6)`, `CHAR(3)` currency and physical-name length checks on `defKey`.

- [x] **Step 2: Normalize only the derived CCT model metadata**

  Preserve all stable template IDs. The generator must clone the template, then change mapping/domain/rule values in the derived model rather than replacing the shared template or inventing PDManer internal fields. This keeps the documented shared-template checksum stable while preventing its generic auto-increment rule and imprecise domains from leaking into a six-core model.

- [x] **Step 3: Resolve the organization-profile Vault mismatch**

  Replace recoverable organization name plaintext in the protected profile with one structured Vault Token Tuple plus approved Masked Values. Keep `seller_disclosure_version.public_*` as the only approved public legal disclosure snapshot.

- [x] **Step 4: Mark all six logical documents as V1 approved**

  Mark Client & Contract as the active PDManer pilot and the remaining five as approved/pending their documented physicalization order.

- [x] **Step 5: Prove the shared template did not drift**

  Run: `shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json`

  Expected: `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`.

### Task 2: Write the artifact acceptance test first

**Files:**
- Create: `scripts/pdmaner/client-contract-model.test.mjs`
- Test: `scripts/pdmaner/client-contract-model.test.mjs`

**Interfaces:**
- Consumes: `client-contract-service/docs/database/client-contract.pdma.json`.
- Produces: an executable contract that rejects missing/incomplete/unsafe PDManer artifacts.

- [x] **Step 1: Write a test that parses the real emitted model**

  The test must assert the literal 77-table set, exact compensation fields, Snowflake PK behavior, field/index naming, internal reference integrity, diagram coverage, Vault tuple/index pairing, no prohibited card/secret/ciphertext fields, and empty database connections.

- [x] **Step 2: Run the test and verify RED**

  Run: `node --test scripts/pdmaner/client-contract-model.test.mjs`

  Expected: FAIL with the model-file-missing assertion because no physical model has been generated.

### Task 3: Implement deterministic PDManer generation

**Files:**
- Create: `scripts/pdmaner/generate-client-contract-model.mjs`
- Create: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Create: `client-contract-service/docs/database/client-contract.pdma.json` (generated)

**Interfaces:**
- Consumes: project template, CCT Markdown table catalog, explicit physical overrides and local relation definitions.
- Produces: `generateClientContractModel()` and a stable PDManer JSON artifact whose IDs do not churn across runs.

- [x] **Step 1: Parse the authoritative table catalog**

  Parse only four-column Markdown entity rows from section 3 and assert exactly 77 unique tables before generation.

- [x] **Step 2: Apply explicit physical decisions**

  Resolve types, lengths, scale, nullability, defaults, unique keys, access indexes and local-only relations. Add `create_time` and `update_time` to every entity unless already present. Document choices that the logical catalog intentionally leaves physical.

- [x] **Step 3: Build PDManer entities and indexes with stable IDs**

  Derive uppercase UUID-shaped IDs from SHA-256 of model/table/field/index paths. Preserve template domain and mapping IDs; every index field and correlation must reference an existing UUID.

- [x] **Step 4: Build grouped relationship diagrams**

  Cover Party/Identity, Roles/Onboarding, Assignment/Source, Agreement/Legal, Pricing/Share and Eligibility/Reliability. A table must occur in at least one diagram; diagram relations may connect only local entities.

- [x] **Step 5: Generate twice and compare digests**

  Run the generator twice and compare SHA-256 digests. Expected: identical output, proving stable model IDs and ordering.

- [x] **Step 6: Run the test and verify GREEN**

  Run: `node --test scripts/pdmaner/client-contract-model.test.mjs`

  Expected: all artifact-contract tests pass.

### Task 4: Link, validate and hand off the physical model

**Files:**
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/README.md`

**Interfaces:**
- Consumes: validated PDManer model and physical-decisions companion.
- Produces: navigation future Agents can follow from logical source to physical source.

- [x] **Step 1: Add direct navigation links**

  Link the CCT logical document, physical decisions and `.pdma.json` artifact without marking the five remaining service models as created.

- [x] **Step 2: Run focused artifact verification**

  Run:

  ```bash
  node --test scripts/pdmaner/client-contract-model.test.mjs
  node scripts/pdmaner/generate-client-contract-model.mjs --check
  jq empty client-contract-service/docs/database/client-contract.pdma.json
  shasum -a 256 client-contract-service/docs/database/client-contract.pdma.json
  ```

  Expected: tests pass, check mode reports no drift, JSON parses, and a model digest is printed.

- [x] **Step 3: Run repository regression and hygiene checks**

  Run:

  ```bash
  npm --prefix requirements-site test
  git diff --check
  rg -n '[[:blank:]]+$' README.md AGENTS.md agent-docs scripts client-contract-service --glob '!**/node_modules/**'
  ```

  Expected: 27/27 requirements-site tests pass, no diff errors and no trailing whitespace.

- [x] **Step 4: Perform an independent read-only review**

  Verify table/field/index counts, internal ID references, security boundaries, V1 status wording and absence of cross-database relations before reporting completion.
