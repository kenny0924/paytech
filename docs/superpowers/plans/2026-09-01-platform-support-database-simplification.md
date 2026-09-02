# Platform Support 57-Table Database Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. For this task the user has already selected direct inline TDD to avoid long subagent cycles; use `superpowers:test-driven-development`, keep the same red/green gates, and do not dispatch implementation subagents.

**Goal:** Replace the current 97-table Platform Support PDManer design with the approved 57-table consumer-driven model, including Tenant-owned API/Webhook/document roots, derived Integration Readiness, static Gateway/Redis boundaries, and an exact 36-field Hash allowlist.

**Architecture:** Platform Support retains global human Account/Tenant IAM, API Credential, file/document, notification, Webhook, audit/security, and local reliable-execution facts. Gateway policy and runtime counters remain static-code/Redis facts; generic technical configuration and derived Readiness persistence are removed. Necessary history is represented by exact immutable rows and explicit revision/rotation semantics instead of mechanical Root/Version and State Change patterns.

**Tech Stack:** Node.js 22+, deterministic JavaScript PDManer generators, PDManer 4.9.3 JSON models, MySQL 8.4 physical semantics, Markdown authority documents, TypeScript/React requirements catalog, Node test runner, ESLint.

**Spec:** `docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md`

## Global Constraints

- Work only in `/private/tmp/code-mor-unified-auth-worktree` until the isolated model and requirements gates pass.
- Preserve every existing Unified Auth, CCT, Risk, Finance, Gateway, and unrelated dirty change. Never replace a shared file wholesale with an older worktree copy.
- Read the Spec plus `agent-docs/README.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/APPLICATION_ARCHITECTURE.md`, `agent-docs/DATABASE_DESIGN_GUIDE.md`, `agent-docs/database/README.md`, `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`, its scenario document, and the Platform physical decisions before model edits.
- Do not modify `api-gateway` Java, tests, POMs, Lua, README, or runtime configuration.
- Do not add Java modules, Flyway migrations, DOs, Mappers, Repositories, physical databases, deployment configuration, Nacos data, KMS/Secrets, Redis state, BFF/frontend code, or production resources.
- Exact topology is 57 tables in groups `18 + 12 + 9 + 8 + 8 + 2`; Gateway and generic technical-configuration groups do not exist.
- No logical or physical table name ends in `_ver`. Necessary history is named `document_version`, `webhook_endpoint_revision`, or `webhook_signing_secret`.
- Exact `*_hash` allowlist is the 36 fields in Spec Section 6. Do not retain a Hash because an earlier model or test expected it.
- `user_account` is global within a fixed Realm. Client/Partner authorization always uses `tenant_member`; Tenant-owned Platform roots use `tenant_id`, not repeated `client_id`.
- `api_credential.key_verifier_phc` is an irreversible verifier. `api_credential_scope` rows are the only API authorization source.
- Integration Readiness is derived synchronously and has no table, Evaluation ID, Version, Hash, current pointer, or publication event.
- Gateway Route Catalog/Guard Profiles are compile-time static. Redis owns TTL-bounded counters and leases; Platform MySQL owns neither.
- Preserve source Event Key/Version/Payload Hash only at the accepted ingress fact that detects same-key/different-payload conflict.
- Use `apply_patch` for edits. The user has not authorized commits, merges, pushes, branch rewrites, or worktree removal; replace each plan-template Commit step with tests, status, and `git diff --check`.
- Generated field/index/relation/one-to-one/composite-FK/CHECK totals and structural digests must come from the generated artifact. Do not guess them before generation.
- PDManer 4.9.3 in-app opening and built-in validation remain a manual gate and must not be claimed complete by automation.

## Repository and File Map

### Model source and policy

- `scripts/pdmaner/platform-support/manifest.mjs`: six-group assembly and exact group sizes.
- `scripts/pdmaner/platform-support/manifests/01-identity-access.mjs`: Account, Tenant IAM, API Credential, and business OTP.
- `scripts/pdmaner/platform-support/manifests/02-file-document.mjs`: File and Consumer Document.
- `scripts/pdmaner/platform-support/manifests/03-notification.mjs`: Notification and Email.
- `scripts/pdmaner/platform-support/manifests/04-webhook.mjs`: Client Webhook.
- `scripts/pdmaner/platform-support/manifests/05-audit-security.mjs`: Audit and Security.
- `scripts/pdmaner/platform-support/manifests/08-reliability.mjs`: compensation and command idempotency.
- `scripts/pdmaner/platform-support/manifests/06-gateway.mjs`: delete after the manifest no longer imports it.
- `scripts/pdmaner/platform-support/manifests/07-technical-config.mjs`: delete after the manifest no longer imports it.
- `scripts/pdmaner/platform-support/model-policy.mjs`: composite FKs, CHECKs, file/hash/security contracts.
- `scripts/pdmaner/platform-support/generate-platform-support-model.mjs`: deterministic PDManer generation and diagram layout.

### Test authority

- `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`: independent exact topology, statistics, structural digest, and contract sets.
- `scripts/pdmaner/platform-support-model.test.mjs`: DSL, topology, metadata, security, FK, CHECK, and generated-model assertions.
- `scripts/pdmaner/platform-support-scenario-doc.test.mjs`: exact table-to-scenario mapping and failure-path assertions.

### Generated and written authority

- `platform-support-service/docs/database/platform-support.pdma.json`
- `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
- `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- `agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md`
- `agent-docs/plans/2026-09-01-platform-support-database-simplification-implementation-plan.md`
- Historical Platform plans listed in the Spec supersession section.

### Canonical/shared contracts

- `requirements-site/content/requirements.ts`
- `requirements-site/content/global-use-case-sequences.mjs`
- `requirements-site/components/architecture-diagrams.tsx`
- `requirements-site/tests/domain-decisions.test.mjs`
- `requirements-site/tests/global-use-case-sequences.test.mjs`
- `requirements-site/tests/site-content.test.mjs`
- `AGENTS.md`, `README.md`, `agent-docs/README.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/APPLICATION_ARCHITECTURE.md`, `agent-docs/DATABASE_DESIGN_GUIDE.md`, `agent-docs/database/README.md`

## Stable Cross-Task Interfaces

### Exact group keys

```js
Object.freeze({
  identity_access: 18,
  file_document: 12,
  notification: 9,
  webhook: 8,
  audit_security: 8,
  reliability: 2,
});
```

### Exact necessary historical table names

```js
Object.freeze([
  "document_version",
  "webhook_endpoint_revision",
  "webhook_signing_secret",
]);
```

### Exact derived Readiness response

```text
status      = READY | NOT_READY | UNKNOWN
checked_at  = current UTC time
reason_code = present only when status != READY
```

### Exact rate-limit subject keys

```text
Client API Credential -> tenant:{tenant_id}
Tenant Console        -> member:{member_id}
Operations            -> account:{account_id}
```

---

### Task 1: Freeze the 57-Table and 36-Hash Contract in Failing Tests

**Files:**
- Modify: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**
- Consumes: Spec Sections 4, 6, 7, and 13.
- Produces: `EXPECTED_GROUP_TABLES`, `EXPECTED_HASH_FIELDS`, `REMOVED_PLATFORM_TABLES`, and named red tests used by Tasks 2–7.

- [ ] **Step 1: Replace the Oracle group topology with the exact six groups**

Define exact ordered arrays for all 57 logical table keys. The arrays must match Spec Section 4 and flatten into a
57-element `EXPECTED_TABLES`; remove Gateway and Technical Config arrays entirely.

```js
export const EXPECTED_GROUP_SIZES = Object.freeze({
  identity_access: 18,
  file_document: 12,
  notification: 9,
  webhook: 8,
  audit_security: 8,
  reliability: 2,
});
export const EXPECTED_TABLE_TOTAL = 57;
```

- [ ] **Step 2: Add an exact Hash-field allowlist**

Export `EXPECTED_HASH_FIELDS` as sorted `table.field` strings copied exactly from Spec Section 6. Add an assertion that
discovers every Manifest field ending `_hash`, sorts the result, and compares it to this array.

```js
const actualHashFields = TABLES
  .flatMap((table) => table.fields
    .filter((field) => field.defKey.endsWith("_hash"))
    .map((field) => `${table.defKey}.${field.defKey}`))
  .sort();
assert.deepEqual(actualHashFields, EXPECTED_HASH_FIELDS);
assert.equal(actualHashFields.length, 36);
```

- [ ] **Step 3: Add topology and ownership rejection tests**

Add named tests that reject any table ending `_ver`, any table in the removed Gateway/Config/Readiness/IdP/Credential
sets, any `client_id` field in a Platform table, `api_credential.scope_hash`, and absence of
`api_credential.key_verifier_phc`.

```js
assert.deepEqual(TABLES.filter((table) => table.defKey.endsWith("_ver")), []);
assert.equal(TABLES.some((table) => table.fields.some((field) => field.defKey === "client_id")), false);
assert.deepEqual(fieldKeys("api_credential").includes("scope_hash"), false);
assert.deepEqual(fieldKeys("api_credential").includes("key_verifier_phc"), true);
```

- [ ] **Step 4: Run the new contract tests and confirm Red**

Run:

```bash
node --test --test-name-pattern='57 approved tables|consumer-driven hash allowlist|tenant-owned roots' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: FAIL against the current 97-table model, reporting the obsolete eight-group topology, forbidden tables,
forbidden `client_id`, or 98 Hash fields. A syntax/import failure is not an acceptable Red state.

- [ ] **Step 5: Check the isolated diff**

Run:

```bash
git diff --check -- scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs scripts/pdmaner/platform-support-model.test.mjs
git status --short
```

Expected: diff check exits 0; only intentional worktree changes remain.

### Task 2: Simplify Identity, Tenant IAM, API Credential, and OTP to 18 Tables

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifests/01-identity-access.mjs`
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**
- Consumes: exact Identity table list and Hash fields from Task 1.
- Produces: an 18-table `IDENTITY_ACCESS_TABLES` export with Tenant-owned API Credential and no Readiness persistence.

- [ ] **Step 1: Add focused failing Identity assertions**

Assert the exact 18 table keys, Google binding shape, Access Tenant identity, API Credential shape, and OTP composite
active key. The test must require:

```text
federated_identity: provider_code + provider_subject; no oidc_provider_id
access_tenant: tenant_type + owner_ref_id; no owner_ref_type
api_credential: tenant_id + key_prefix + key_verifier_phc; no client_id/scope_hash/key_hash
email_otp_challenge: no active_scope_hash; active uniqueness uses the explicit Tenant/Subject/Purpose/Context tuple
```

- [ ] **Step 2: Run the focused Identity test and confirm Red**

Run:

```bash
node --test --test-name-pattern='Identity group uses global accounts and tenant-owned credentials' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: FAIL because the current group still has 23 tables and the obsolete fields/tables.

- [ ] **Step 3: Implement the 18-table Identity manifest**

Remove `oidc_provider`, `credential_event`, and all three Integration Readiness tables. Rewire
`federated_identity` to closed `provider_code = GOOGLE`, with unique `(provider_code, provider_subject)` and
`(account_id, provider_code)`. Remove `access_tenant.owner_ref_type` and retain unique `(tenant_type, owner_ref_id)`.

Replace API Key fields with:

```text
credential_id, tenant_id, key_prefix, key_verifier_phc, expires_time,
status, rotated_from_id, version, create_time, update_time
```

Retain `api_credential_scope` rows as the only authorization source. Remove `signing_key_metadata.public_jwk_hash`, all
Readiness Hashes, and `email_otp_challenge.active_scope_hash`. Replace the OTP active uniqueness with the explicit
Tenant/Subject/Purpose/Context tuple plus `active_slot`. Keep Authentication Challenge `active_scope_hash`, keyed HMAC
fields, OTP Subject/Context/IP/Device hashes, and source projection hash exactly as allowed by the Spec.

- [ ] **Step 4: Remove obsolete Identity CHECK/FK policy**

Delete policy entries for OIDC Provider FK, Readiness Current Pointer/Config Version, API Scope Hash equality, and OTP
active-scope hash. Add/update checks for fixed GOOGLE provider code, Access Tenant source tuple keyed by `tenant_type`,
API verifier presence, API Credential CLIENT-Tenant cross-row rule documentation, and explicit OTP active tuple.

- [ ] **Step 5: Run focused tests to Green**

Run:

```bash
node --test --test-name-pattern='Identity group uses global accounts and tenant-owned credentials|secret contract' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: PASS for both named tests; the global 57-table test may remain Red until Tasks 3–6.

- [ ] **Step 6: Check the Identity diff**

Run `git diff --check` and inspect `git diff -- scripts/pdmaner/platform-support/manifests/01-identity-access.mjs scripts/pdmaner/platform-support/model-policy.mjs scripts/pdmaner/platform-support-model.test.mjs`.

### Task 3: Collapse File and Consumer Document to 12 Tables

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifests/02-file-document.mjs`
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**
- Consumes: Tenant ID from Task 2 and single-table revision rules from the Spec.
- Produces: exact `file_scan_policy`, `document_template`, merged `consumer_document`, and render-capable `document_version` shapes.

- [ ] **Step 1: Add a failing exact File/Document topology test**

Require the 12 table names from Spec Section 4.2; reject `file_scan_policy_ver`, `document_template_ver`,
`document_generation`, all three state-change tables, and `document_render_job`.

- [ ] **Step 2: Run the focused test and confirm Red**

Run:

```bash
node --test --test-name-pattern='File and Consumer Document use twelve consumer-driven tables' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: FAIL with the current 19-table group.

- [ ] **Step 3: Merge Scan Policy and Document Template revisions**

Make each row self-contained with stable code, `revision_no`, DRAFT/ACTIVE/RETIRED status, nullable `active_slot`, and
only fields consumed by scanning/rendering. Rewire Requirement, File Object, Translation, and Document Version to the
exact merged row IDs. Remove allowed-MIME, accepted-result, requirement, variable-schema, translation, policy-content,
and template-content hashes.

- [ ] **Step 4: Merge Document Generation into Consumer Document**

Use `tenant_id` instead of `client_id`. Move the generation key/version, explicit Order/business identity, Commerce
slot, Fiscal slot, Legal File IDs, correction target, status, and optimistic lock into `consumer_document`. Keep only
the Commerce/Fiscal source payload hashes and Consumer Reference hash from the allowlist. Remove duplicated Legal File
content hashes and `generation_scope_hash`.

- [ ] **Step 5: Merge Render Job into Document Version**

Add the exact Template/Translation IDs, renderer version, File ID, supersedes relation, lifecycle status,
`active_slot`, attempt count, lease owner/until, fencing token, error code, and optimistic lock required by the rendering
lifecycle. Remove `source_set_hash` and duplicate rendered `content_hash`; File integrity remains
`file_object.content_hash`.

- [ ] **Step 6: Simplify Document Delivery and remove generic histories**

Make `document_delivery` an immutable link from exact Document Version to unique `notification_delivery_id` with a
delivery sequence; remove recipient hash and duplicate notification status. Delete the three state-change definitions.
Keep `document_access_grant.consumer_ref_hash` because the grant performs privacy-preserving Consumer lookup.

- [ ] **Step 7: Update File/Document FK and CHECK policy**

Replace all composite FKs involving old Root/Version tuples. Preserve exact File/Requirement/Policy ownership,
Document/Version ownership, Template/Translation ownership, correction/supersedes self-owner checks, File availability,
and Lease/Fencing invariants. Remove all file-ID/content-hash tuple rules except the actual File and signed Audit
Integrity file contract.

- [ ] **Step 8: Run focused tests to Green**

Run:

```bash
node --test --test-name-pattern='File and Consumer Document use twelve consumer-driven tables|critical fields and minimal Vault shapes|composite foreign keys' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: PASS for the focused tests.

### Task 4: Collapse Notification to 9 Tables

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifests/03-notification.mjs`
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**
- Consumes: exact Tenant ownership and revision conventions from Tasks 2–3.
- Produces: exact Provider/Template revision rows and one recipient/message/delivery row per Request recipient.

- [ ] **Step 1: Add and run a failing 9-table Notification test**

Require Spec Section 4.3 table names. Reject Provider/Template `_ver`, `notification_recipient`,
`notification_message`, and `notify_delivery_state_change`.

Run:

```bash
node --test --test-name-pattern='Notification uses nine delivery-centered tables' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: FAIL against the current 14-table group.

- [ ] **Step 2: Merge Provider and Template revisions**

Move stable code/channel/purpose, `revision_no`, exact provider connection/secret/callback fields or template schema,
effective window where consumed, DRAFT/ACTIVE/RETIRED status, `active_slot`, and optimistic lock onto one revision row.
Rewire Routes, Attempts, Callbacks, Requests, and Translations to the exact merged IDs. Remove capability, content,
schema, and translation hashes.

- [ ] **Step 3: Merge Recipient and Message into Delivery**

For each Request recipient, one Delivery stores recipient sequence, Channel, address Vault/Blind/Masked tuple, locale,
Template Translation ID, Message Vault Token/safe previews/sensitivity/expiry, Route ID, status, retry/Lease/Fencing,
attempt count, and optimistic lock. Unique `(request_id, recipient_no)` replaces the old one-to-one chain.

- [ ] **Step 4: Tighten Notification idempotency and attempts**

Set Request uniqueness to `(source_system, source_event_key, notification_type)` and retain
`source_payload_hash` for conflict detection. Remove `recipient_set_hash`, Delivery `route_hash`, Message content hash,
Route content hash, Attempt response hash, Callback signature-header hash, and state-history source hash. Retain Attempt
request hash and Callback payload hash.

- [ ] **Step 5: Rebuild Notification composite FKs and checks**

Enforce Request/Delivery, Delivery/Route/Translation, Attempt/Provider, Callback/Provider/Attempt/Delivery, and
Suppression address-key ownership. Preserve callback verification, provider-message anchoring, Lease/Fencing, and
address Vault tuple checks without old Root/Version pairs.

- [ ] **Step 6: Run focused tests to Green**

Run:

```bash
node --test --test-name-pattern='Notification uses nine delivery-centered tables|callback|Lease|consumer-driven hash allowlist' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: Notification-focused tests pass; global topology may remain Red.

### Task 5: Normalize Tenant-Owned Webhook to 8 Tables

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifests/04-webhook.mjs`
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**
- Consumes: `access_tenant.tenant_id` and exact hash/revision policy.
- Produces: stable Endpoint, necessary Endpoint Revision and Signing Secret, direct Replay Delivery, and Tenant composite ownership.

- [ ] **Step 1: Add and run a failing 8-table Webhook test**

Require Spec Section 4.4 names. Reject `_ver` names, Subscription State Change, Replay, Delivery State Change, and every
Webhook `client_id`.

Run:

```bash
node --test --test-name-pattern='Webhook uses tenant-owned revisions and direct replay deliveries' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: FAIL against the current 11-table Client-ID model.

- [ ] **Step 2: Rename and simplify Endpoint/Secret history**

Keep `webhook_endpoint` as the stable Tenant-owned root without `current_ver_id`. Rename the URL row to
`webhook_endpoint_revision`, add exact revision number/status/active-slot semantics, and remove content hash. Rename
Secret rows to `webhook_signing_secret`, use an explicit rotation number/signing slot, and keep Secret Ref/fingerprint
and effective window.

- [ ] **Step 3: Normalize Event and Delivery Tenant ownership**

Replace Webhook Endpoint/Event/Delivery `client_id` with local `tenant_id`. Keep Event source and outbound-payload
hashes. Keep Delivery exact Endpoint Revision, Subscription, payload schema, Lease/Fencing, and status.

- [ ] **Step 4: Merge Replay into Delivery and simplify Attempt**

Delete Replay and state-change tables. Add nullable `replay_of_delivery_id`, non-null `generation_no`, nullable
`requested_by`, and nullable `replay_reason_code` to Delivery with checks that generation zero has no replay fields and
generation greater than zero has all replay fields. Rewire Attempt to exact `webhook_signing_secret`; remove response
hash and retain request hash.

- [ ] **Step 5: Rebuild Tenant and endpoint composite contracts**

Enforce Event/Delivery Tenant, Endpoint/Delivery Tenant, Endpoint/Revision ownership, Subscription/Endpoint ownership,
and Attempt/Delivery/Secret Endpoint ownership. Preserve URL Vault tuple, verification evidence, payload XOR,
generation/replay, and Lease/Fencing checks.

- [ ] **Step 6: Run focused tests to Green**

Run:

```bash
node --test --test-name-pattern='Webhook uses tenant-owned revisions and direct replay deliveries|webhook|composite foreign keys|consumer-driven hash allowlist' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: Webhook-focused tests pass.

### Task 6: Remove Gateway, Generic Config, Audit Archive, and Response Hash

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifests/05-audit-security.mjs`
- Delete: `scripts/pdmaner/platform-support/manifests/06-gateway.mjs`
- Delete: `scripts/pdmaner/platform-support/manifests/07-technical-config.mjs`
- Modify: `scripts/pdmaner/platform-support/manifests/08-reliability.mjs`
- Modify: `scripts/pdmaner/platform-support/manifest.mjs`
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**
- Consumes: completed five retained business groups.
- Produces: six-group/57-table Manifest and the final 36-field Hash set.

- [ ] **Step 1: Add and run a failing topology-boundary test**

Assert six exact group keys, absence of Gateway/Config imports and tables, absence of `audit_archive_batch`, and absence
of `command_idempotency.response_hash`.

Run:

```bash
node --test --test-name-pattern='Platform has no database-backed Gateway or generic configuration control plane' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: FAIL against the current Manifest.

- [ ] **Step 2: Remove Gateway and Technical Config groups**

Delete both Manifest modules, remove their imports/group entries, and set exact group sizes to the Stable Cross-Task
Interface. Delete all Gateway/Config composite FKs, checks, enums, field contracts, test branches, and diagram references
from Platform policy/generator logic.

- [ ] **Step 3: Remove Audit Archive Batch**

Delete the table and archive-specific checks/contracts. Preserve `audit_integrity_point` with start/end/root/content
hashes, signature reference, exact File reference, and immutable semantics.

- [ ] **Step 4: Remove command response hash**

Keep `command_idempotency.request_hash`, `response_ref`, status, expiry, and request conflict semantics. Rewrite the
success CHECK to require `response_ref` only when SUCCEEDED; remove `response_hash` from Manifest and policy.

- [ ] **Step 5: Run the global topology and Hash tests**

Run:

```bash
node --test --test-name-pattern='57 approved tables|consumer-driven hash allowlist|no database-backed Gateway' scripts/pdmaner/platform-support-model.test.mjs
```

Expected: table topology and exact Hash allowlist pass. Generated-model digest/statistics tests may remain Red until
Task 7 regenerates the artifact.

### Task 7: Reconcile Physical Contracts and Regenerate the PDManer Artifact

**Files:**
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`
- Modify: `scripts/pdmaner/platform-support/generate-platform-support-model.mjs`
- Modify: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/platform-support-model.test.mjs`
- Modify generated: `platform-support-service/docs/database/platform-support.pdma.json`

**Interfaces:**
- Consumes: final 57-table Manifest.
- Produces: deterministic exact field/index/relation/diagram/composite-FK/CHECK counts and structural/check-policy digests.

- [ ] **Step 1: Run the complete model test and inventory remaining failures**

Run:

```bash
node scripts/pdmaner/platform-support-model.test.mjs
```

Expected: only stale generated totals/digests, obsolete composite/check bindings, diagram layout, or removed-name tests
fail. Fix structural Manifest/policy defects before changing Oracle totals.

- [ ] **Step 2: Rebuild composite FK and CHECK policy from real target tuples**

Remove every child/parent reference to deleted tables/fields. Add only exact Tenant/owner, merged revision,
Document/Template, Notification/Provider, Webhook Endpoint/Secret, Audit File, and Lease/Fencing contracts described in
the Spec. Ensure every composite child has a left-prefix index and every parent tuple has an exact unique key.

- [ ] **Step 3: Update generator group layout and metadata rules**

Lay out exactly six groups, remove Gateway/Config references and legends, update immutable-row classification for merged
tables, and retain deterministic IDs based on logical keys. Do not hand-edit generated PDManer entity JSON.

- [ ] **Step 4: Generate the artifact**

Run:

```bash
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs
```

Expected: generator writes a valid 57-table model to the nested Platform service repository.

- [ ] **Step 5: Derive and freeze exact generated evidence**

Run the generator's statistics output plus the model test diagnostic. Update Oracle field/index/relation/one-to-one,
composite-FK/CHECK totals and structural/check-policy SHA-256 values to the exact generated values. Never alter the
Manifest to preserve an old statistic.

- [ ] **Step 6: Prove deterministic Green**

Run:

```bash
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
node scripts/pdmaner/platform-support-model.test.mjs
```

Expected: generator reports 57 tables and exits 0; every Platform model test passes with zero failures.

### Task 8: Rewrite Platform Authority, Scenarios, and Implementation Record

**Files:**
- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Modify: `agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md`
- Modify: `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
- Create: `agent-docs/plans/2026-09-01-platform-support-database-simplification-implementation-plan.md`
- Modify historical markers in the three superseded Platform plan files named in the Spec.
- Modify: `scripts/pdmaner/platform-support-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: final generated statistics and exact 57 table definitions from Task 7.
- Produces: current written authority and one valid scenario assignment for every retained table.

- [ ] **Step 1: Change scenario tests first and confirm Red**

Require exactly 57 scenario table assignments, reject every removed name and stale Hash field, and assert the derived
Readiness, direct Webhook Replay, merged Notification Delivery, merged Consumer Document, and static Gateway/Redis
failure paths.

Run `node scripts/pdmaner/platform-support-scenario-doc.test.mjs`.

Expected: FAIL because the current document still maps 97 tables.

- [ ] **Step 2: Rewrite logical table/domain sections**

Use the six groups and exact tables from the Spec. Replace Root/Version, Current Pointer, generic State Change,
Gateway LKG, Config Release, and persisted Readiness explanations. Document every retained Hash's consumer and every
merged lifecycle's concurrency boundary.

- [ ] **Step 3: Rewrite the scenario document**

Provide one concrete field assignment for every retained table using only real target fields and closed enum values.
Demonstrate at least these negative paths: same source key/different payload conflict, inactive Tenant/API Scope denial,
Document slot conflict, failed new render preserving old ACTIVE Version, Notification unknown Provider result, Webhook
DNS/Challenge rejection, replay generation mismatch, stale Fencing Token, and Audit chain mismatch.

- [ ] **Step 4: Rewrite physical decisions and create the implementation record**

Record the generated totals/digests, exact Hash allowlist, revision decisions, removed table list, composite/CHECK totals,
and validation evidence. Mark prior Platform topology sections historical without rewriting their implementation history.

- [ ] **Step 5: Run scenario and model gates**

Run:

```bash
node scripts/pdmaner/platform-support-scenario-doc.test.mjs
node scripts/pdmaner/platform-support-model.test.mjs
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
```

Expected: all pass.

### Task 9: Update Canonical Requirements and Shared Architecture Semantically

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/components/architecture-diagrams.tsx`
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify semantically: `AGENTS.md`, `README.md`, `agent-docs/README.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/APPLICATION_ARCHITECTURE.md`, `agent-docs/DATABASE_DESIGN_GUIDE.md`, `agent-docs/database/README.md`

**Interfaces:**
- Consumes: final generated statistics and the Spec's requirement changes.
- Produces: one canonical product/architecture contract with no dynamic Gateway, generic Config, or persisted Readiness semantics.

- [ ] **Step 1: Write failing requirements tests**

Require removal of PLT-09, Technical Configuration domain/source-of-truth text, `ConfigurationActivated`, and
`ClientIntegrationReadinessChanged`. Require PLT-I13 to state synchronous derivation with no persisted
Evaluation/Version/Hash. Require Tenant-owned API Credential and static Gateway/Redis subjects.

- [ ] **Step 2: Run requirements tests and confirm Red**

Run:

```bash
node --test requirements-site/tests/domain-decisions.test.mjs requirements-site/tests/global-use-case-sequences.test.mjs requirements-site/tests/site-content.test.mjs
```

Expected: FAIL on old PLT-09/config/publication/readiness wording.

- [ ] **Step 3: Update the canonical Platform catalog and sequences**

Remove the obsolete use case/domain/publications. Rewrite PLT-I03 and PLT-I13. In E2E-01/CCT-I10/CCT-I13 sequences,
replace frozen Requirement Config/Evaluation wording with a current derived Platform check. Preserve Unified Auth,
Typed Risk Namespace, Finance, and static Operations Gateway contracts.

- [ ] **Step 4: Update diagrams and shared authority by semantic merge**

Replace only Platform statistics, six-group topology, Hash/revision rules, and Gateway/Config/Readiness descriptions.
Preserve every unrelated CCT, Commerce, Payment, Finance, Risk, Gateway Header/Cookie, and local-environment change from
the latest file versions.

- [ ] **Step 5: Run requirements build/tests/lint**

Run:

```bash
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: build and all tests pass; lint exits 0.

### Task 10: Full Regression, Coordination, and Main-Workspace Semantic Integration

**Files:**
- Inspect all changed files in the parent and nested Platform service repositories.
- Semantically synchronize only the approved Platform/shared deltas to `/Users/kenny/Desktop/Workspace/Code-MoR`.

**Interfaces:**
- Consumes: isolated Green model, docs, and requirements.
- Produces: coordinated main-workspace result and final verification evidence without a commit.

- [ ] **Step 1: Run all six model generators in check mode**

Run the current repository's six generator commands discovered from `scripts/pdmaner/generate-*-model.mjs` and the
Platform generator path. Expected: all exit 0 without rewriting files.

- [ ] **Step 2: Run the complete PDManer/scenario and requirements suites**

Run every `scripts/pdmaner/*model.test.mjs` and `*scenario-doc.test.mjs`, followed by requirements build/tests/lint.
Expected: zero failures.

- [ ] **Step 3: Run structural hygiene gates**

Run:

```bash
rg -n 't_gateway_|t_config_|t_.*_ver\b|ClientIntegrationReadinessChanged|ConfigurationActivated|current_ver_id|scope_hash|reason_set_hash|requirement_config_hash|evidence_hash' agent-docs requirements-site scripts/pdmaner/platform-support platform-support-service/docs/database
git diff --check
git status --short
git -C platform-support-service diff --check
git -C platform-support-service status --short
```

Expected: only explicitly historical/supersession statements in the new Spec/implementation record may match removed
terms; no current authority/model/scenario match remains. All diff checks exit 0.

- [ ] **Step 4: Notify concurrent task owners before synchronization**

Send the Gateway, Risk, and Finance task owners the exact Platform changed-file set, final generated statistics, shared
files to be semantically merged, and a statement that no Gateway code/Runtime Config or Risk/Finance specialized files
will be overwritten. Wait for any active writer warning before touching a shared main-workspace file.

- [ ] **Step 5: Semantically integrate into the latest main workspace**

For Platform-only files, apply the isolated diff to the latest main version. For shared files, compare both versions and
apply only the Platform paragraphs/statistics/requirements changes with `apply_patch`. Do not use whole-file `cp`, Git
checkout, reset, or merge over dirty files.

- [ ] **Step 6: Repeat the full gate in main**

Run the same six generators, all model/scenario tests, requirements build/tests/lint, removed-contract scan, Markdown
relative-link check used by the repository, and diff checks from the main workspace. Expected: zero failures and exact
Platform 57-table/36-Hash evidence.

- [ ] **Step 7: Request and receive a read-only total gate**

Send the final main-workspace changed-file list and validation evidence to the coordinating Gateway/Risk/Finance task
owners. Ask them to run read-only validation only. Resolve any concrete semantic conflict with an incremental patch and
repeat the affected gate.

- [ ] **Step 8: Report completion without claiming the manual PDManer gate**

Report final Platform table/field/index/relation/one-to-one/diagram/composite-FK/CHECK/Hash totals, changed files,
automated command results, worktree/main status, and the remaining PDManer 4.9.3 manual review requirement. Do not
commit, merge, push, or delete the worktree.
