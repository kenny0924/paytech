# Client & Contract Row History Refactor Implementation Plan

> Historical execution record: the two-table model remains, but the current CCT design now uses an explicit 18-Record-Type BCR whitelist and Root-only CAS. Do not reuse this plan's former all-Child history assumptions; follow [the current row-history design](./2026-08-20-cct-row-history-design.md) and [the CCT database specification](../database/CLIENT_CONTRACT_DATABASE.md).

> **For agentic workers:** Execute with test-first checkpoints. Project policy forbids Git commits unless the user explicitly requests one.

**Goal:** Replace 11 CCT-only state history tables with the approved two-table generic row-history model, then regenerate and verify the CCT PDManer artifact.

**Architecture:** Structured domain tables remain authoritative. `business_change_record` indexes an audited business row and its immutable aggregate topology; `business_change_version` stores an append-only full row audit snapshot at the owning aggregate root version. Controlled JSON metadata preserves schema, trigger, reason and integrity information without expanding the physical detail table.

**Scope:** CCT logical documentation, global database conventions, the deterministic CCT PDManer generator, artifact contract tests, generated CCT model and physical decisions. No Java, Migration, database connection, deployment, or physical model for the other five systems.

## Task 1: Freeze the approved design

- [x] Record the table schemas, aggregate-version semantics, JSON envelope, security boundary and replacement/retention scope in `2026-08-20-cct-row-history-design.md`.
- [x] Independently review versioning, reconstruction, parent/root identity and sensitive-data handling.

## Task 2: Change the artifact contract first

- [x] Replace the 11 old table names with the two new tables in `scripts/pdmaner/client-contract-model.test.mjs`.
- [x] Assert exact fields, types, nullability, unique/index contracts, relationships, immutable detail audit-field exception and absence of old tables.
- [x] Run the test and confirm it fails against the old model for the expected reason.

## Task 3: Update authoritative logical rules

- [x] Replace the 11 table rows and all prose/ER references in `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`.
- [x] Add aggregate-version write/reconstruction rules, JSON metadata, Catalog, Retention and sensitive-data restrictions.
- [x] Update `agent-docs/database/README.md` and `agent-docs/DATABASE_DESIGN_GUIDE.md` so the generic row-history pattern is a permitted service-local implementation and the immutable detail may omit `update_time`.
- [x] Preserve all domain state machines and specialist immutable history/fact tables.

## Task 4: Update deterministic generation

- [x] Change expected table count, JSON typing, `create_by` typing, nullability and per-table audit-field policy.
- [x] Replace old unique/index definitions, local relations and diagram members.
- [x] Regenerate `client-contract.pdma.json` from the unchanged shared template.
- [x] Update `CLIENT_CONTRACT_PHYSICAL_DECISIONS.md` with the new rules, exact metrics and digest.

## Task 5: Verify and independently review

- [x] Run the model contract test, generator drift check, Node syntax check and `jq` parse.
- [x] Generate twice and prove an identical SHA-256 digest; prove the shared template checksum is unchanged.
- [x] Run `npm --prefix requirements-site test`, Markdown/link/hygiene checks and `git diff --check` where applicable.
- [x] Obtain a final independent read-only review and fix every Critical/Important issue.
- [ ] Manual gate: open the artifact in PDManer 4.9.3 and run its in-app model checker before entering Migration.
