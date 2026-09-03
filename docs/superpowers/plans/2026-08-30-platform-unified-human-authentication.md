# Platform Unified Human Authentication Database Revision Implementation Plan

> **2026-09-01 superseded topology notice:** This plan preserves the historical 97-table implementation sequence. The [57-table simplification design](../specs/2026-09-01-platform-support-database-simplification-design.md) is the direct predecessor for unaffected domains; the [unified public API control-plane design](../specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) and its [PDManer implementation plan](./2026-09-01-platform-public-api-control-plane-pdmaner.md) define the current six-table successor. The current model is 63 表 / 822 字段 / 123 unique indexes / 99 access indexes / 107 relations / 5 one-to-one relations / 7 diagram groups, with 31 composite FKs, 85 CHECKs and 36 `*_hash` fields. Do not restore the old camelCase Provision payload, pre-Tenant upload order, legacy Gateway topology, or removed tables from the historical steps below.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Revise the Platform Support, Client & Contract, and Risk & Compliance design assets so Operations uses Email/Password, Client/Partner uses global Email/Password plus Google OIDC accounts with tenant-scoped authorization, Local Partner onboarding has an authoritative CCT Application, and Gateway protection references only deployment-owned Route Codes.

**Architecture:** Platform Support remains the sole human authentication, Session, signing and Tenant-IAM authority for fixed OPERATIONS and CUSTOMER Realms. CCT remains the Client/Local Partner/Application authority and provisions Platform tenants after commit; Risk extends its typed Application namespace without adding another Case root. The three generated models and all shared requirements must converge atomically, but each task below has its own red/green test gate.

**Tech Stack:** Node.js 22+, deterministic JavaScript PDManer generators, PDManer 4.9.3 JSON models, MySQL 8.4 physical semantics, Markdown authority documents, TypeScript/React requirements catalog, Node test runner, ESLint.

**Spec:** `docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md`

## Global Constraints

- Read the Spec plus `agent-docs/README.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/APPLICATION_ARCHITECTURE.md`, `agent-docs/DATABASE_DESIGN_GUIDE.md`, `agent-docs/database/README.md`, and all three involved database documents before editing.
- Parent work stays in `/private/tmp/code-mor-unified-auth-worktree` on `codex/platform-unified-human-auth`; never edit the dirty main workspace as an implementation shortcut.
- `platform-support-service`, `client-contract-service`, and `risk-compliance-service` are independent repositories ignored by the parent repository. Create service-local worktrees at the exact nested paths expected by the generators.
- Main workspace changes in `agent-docs/COMPENSATION_EVENT_GUIDE.md` and `scripts/verify-java-foundation.mjs` are unrelated user work and must remain byte-for-byte untouched.
- The main `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md` currently has an uncommitted owner change. Treat its diff as an integration input and perform a semantic merge; never replace it with the service worktree file wholesale.
- Wait for the active task titled `准备网关设计` before editing or integrating shared Gateway/requirements files. Preserve its final static Operations Route Catalog, Cookie/Header contract, tests and main HEAD.
- Do not modify `api-gateway` Java, tests, POMs or runtime configuration. This plan changes only database/requirements design assets.
- Do not add Java modules, migrations, Flyway, physical databases, KMS/Secrets, BFF/frontend code, deployment configuration, MFA, Apple/Microsoft, Consumer authentication, machine credentials or a seventh core system.
- Operations supports Password only. All human Operations identifiers are Email except the one-time fixed `admin` Bootstrap Account.
- CUSTOMER supports Email/Password and Google OIDC only. A verified Google Email never silently links to an existing Account; existing Password authentication is mandatory first.
- Fixed Realms are OPERATIONS and CUSTOMER. Realm, issuer, key ring, Credential, Session and lock state never cross boundaries.
- Access JWT lifetime is at most 15 minutes. Operations Session is absolute 8 hours/idle 30 minutes; Client/Partner is absolute 7 days/idle 24 hours; no remember-me.
- Password length is 15–128 Unicode characters after NFC; Argon2id minimum is 19 MiB, two iterations and parallelism one. Operations locks after five failures for 30 minutes; CUSTOMER after ten failures for 15 minutes.
- One Access Token binds one Account or one Tenant Member. It never embeds Email, all Tenants, Roles or Permissions.
- Email equality uses stable Vault Value Token purpose `PLATFORM_LOGIN_EMAIL_V1`; do not restore ordinary SHA/Blind Index fields for login Email.
- Existing `t_email_otp_challenge` remains a business OTP table. Authentication uses a separate high-entropy HMAC-backed Challenge table.
- CCT and Platform never use cross-database foreign keys. `requested_by_account_id`, Owner references and Risk Case/Decision references are stable external IDs.
- Gateway Path, Method, Upstream and Route lifecycle remain deployment/code facts. Platform may publish protection only for a pre-existing exact `route_code` after a separate runtime authorization.
- Rate and Concurrency execute cumulatively across SHARED_ROUTE, CLIENT_IP and AUTHENTICATED_SUBJECT; Redis owns runtime counters/leases, MySQL only approved policy facts.
- Final model totals and digests come from generated artifacts. Do not choose field/index/relation/CHECK totals in advance or preserve old numbers after the structure changes.
- Use `apply_patch` for edits. Do not commit, merge, push or remove worktrees unless the user separately authorizes it; every task ends with tests, status and `git diff --check` instead of a commit.
- Preserve the PDManer 4.9.3 in-app manual review gate; automated success does not claim that manual gate is complete.

## Repository and File Map

### Parent repository: contract sources and generated-model tests

- Platform model source: `scripts/pdmaner/platform-support/manifests/01-identity-access.mjs`, `02-file-document.mjs`, `04-webhook.mjs`, `05-audit-security.mjs`, `06-gateway.mjs`, `07-technical-config.mjs`, `manifest.mjs`, `model-policy.mjs`, `generate-platform-support-model.mjs`.
- Platform test authority: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`, `platform-support-model.test.mjs`, `platform-support-scenario-doc.test.mjs`.
- CCT model source: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`, `scripts/pdmaner/client-contract-field-metadata.mjs`, `generate-client-contract-model.mjs`.
- CCT test authority: `scripts/pdmaner/client-contract-model.test.mjs`, `client-contract-scenario-doc.test.mjs`.
- Risk model source: `scripts/pdmaner/risk/manifests/01-due-diligence.mjs`, `risk/model-policy.mjs`, `risk/manifest.mjs`, `risk/generate-risk-compliance-model.mjs`.
- Risk test authority: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`, `risk-compliance-model.test.mjs`, `risk-compliance-scenario-doc.test.mjs`.
- Logical/scenario docs: the three files under `agent-docs/database/` and their three files under `agent-docs/database/scenarios/`.
- Product contracts: `requirements-site/content/requirements.ts`, `global-use-case-sequences.mjs`, `components/architecture-diagrams.tsx`, `components/requirements-site.tsx`, and their tests.
- Shared authority/navigation: `AGENTS.md`, `README.md`, `agent-docs/README.md`, `PROJECT_CONTEXT.md`, `APPLICATION_ARCHITECTURE.md`, `DATABASE_DESIGN_GUIDE.md`, `agent-docs/database/README.md`.

### Independent service repositories: generated and physical artifacts

- `platform-support-service/docs/database/platform-support.pdma.json`
- `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
- `client-contract-service/docs/database/client-contract.pdma.json`
- `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- `risk-compliance-service/docs/database/risk-compliance.pdma.json`
- `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`

## Stable Cross-Task Contracts

### Platform Account and Tenant enums

```text
realm                 = OPERATIONS | CUSTOMER
account_type          = HUMAN | BOOTSTRAP_ADMIN
account_status        = PENDING_VERIFICATION | ACTIVE | DISABLED | CLOSED
authentication_method = PASSWORD | GOOGLE
session_context       = ACCOUNT | TENANT | BOOTSTRAP
session_status        = ACTIVE | REVOKED | EXPIRED | COMPROMISED
tenant_type           = CLIENT | PARTNER | INTERNAL
owner_ref_type        = CLIENT | LOCAL_PARTNER | PLATFORM
owner_status          = PENDING | ACTIVE | SUSPENDED | TERMINATED
security_status       = ACTIVE | SUSPENDED
member_type           = OWNER | ADMIN | MEMBER
member_status         = ACTIVE | SUSPENDED | REVOKED
```

Security constants are part of the test contract, not deployment suggestions:

```text
Password             = 15..128 Unicode characters after NFC; request <= 512 UTF-8 bytes
Password verifier    = Argon2id PHC; memory >= 19456 KiB, iterations >= 2, parallelism = 1
Challenge/Refresh    = at least 256-bit CSPRNG secret; database stores only keyed HMAC
Access JWT           = RS256; lifetime <= 15 minutes; Realm-specific issuer/KMS key ring/JWKS
Operations Cookie    = __Secure-operations-refresh; Path=/api/operations/auth/v1
Client Cookie        = __Secure-client-refresh; Path=/api/client/auth/v1
Partner Cookie       = __Secure-partner-refresh; Path=/api/partner/auth/v1
System Roles         = PLATFORM_ADMIN | TENANT_ONBOARDING_OWNER | TENANT_OWNER
```

All three Cookies are Secure, HttpOnly, SameSite=Strict, have no Domain attribute and never outlive the Session's remaining absolute lifetime.

### CCT to Platform command

```json
{
  "operationType": "PROVISION_ACCESS_TENANT",
  "tenantType": "CLIENT | PARTNER",
  "ownerRefType": "CLIENT | LOCAL_PARTNER",
  "ownerRefId": "Snowflake ID",
  "requestedByAccountId": "Platform CUSTOMER account ID",
  "sourceVersion": 1,
  "payloadHash": "64 lowercase hex"
}
```

The idempotency identity is `CCT:<tenantType>:<ownerRefId>:<sourceVersion>:PROVISION_ACCESS_TENANT`. Same identity and Hash returns the original Tenant/Member; same identity and a different Hash is `CONFLICT`. Status projection uses the same Owner tuple and a strictly monotonic Source Version.

### CCT to Risk Application tuple

```text
source_namespace + source_application_id + role_type + role_id + qualification_type
```

For Local Partner onboarding the main tuple is `CCT_LOCAL_PARTNER_APPLICATION + application_id + LOCAL_PARTNER + local_partner_id + PARTNER_DUE_DILIGENCE`. Related-person children use the same Namespace/Application with `RELATED_PERSON + party_id + RELATED_PERSON_KYC`.

### Gateway protection identity

```text
binding: route_code + priority_tier + scope_key + effective window
rate/concurrency rule: rule_type + counter_dimension
counter_dimension: SHARED_ROUTE | CLIENT_IP | AUTHENTICATED_SUBJECT
```

`route_code` must exist in the deployment-owned static catalog. No Platform row stores Path, Method, Upstream or runtime route reference.

---

### Task 1: Establish Nested Service Worktrees and Record the Baseline

**Files:**
- Inspect: `/Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
- Create worktree: `/private/tmp/code-mor-unified-auth-worktree/platform-support-service`
- Create worktree: `/private/tmp/code-mor-unified-auth-worktree/client-contract-service`
- Create worktree: `/private/tmp/code-mor-unified-auth-worktree/risk-compliance-service`
- Inspect: all parent and service repository statuses

**Interfaces:**
- Produces: generator-compatible nested paths, clean service worktrees and a recorded list of external dirty/shared inputs.
- Consumes: current parent worktree plus the three service repositories' current `main` commits.

- [ ] **Step 1: Re-read status and active-task state before creating anything**

Run:

```bash
git -C /Users/kenny/Desktop/Workspace/Code-MoR status --short --branch
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service status --short --branch
git -C /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service status --short --branch
git -C /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service status --short --branch
```

Expected: preserve the two known parent modifications and the Platform physical-decisions modification; stop if any CCT/Risk model artifact has an unexpected writer.

- [ ] **Step 2: Capture the Platform owner diff as a semantic merge input**

Run the read-only command and retain the output in the task transcript, not a scratch file:

```bash
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service diff -- docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md
```

Expected: a reviewable owner diff. Do not stage, reset or copy the file.

- [ ] **Step 3: Create service-local worktrees at generator target paths**

```bash
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service worktree add \
  -b codex/platform-unified-auth-model \
  /private/tmp/code-mor-unified-auth-worktree/platform-support-service main
git -C /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service worktree add \
  -b codex/client-partner-onboarding-model \
  /private/tmp/code-mor-unified-auth-worktree/client-contract-service main
git -C /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service worktree add \
  -b codex/partner-application-namespace \
  /private/tmp/code-mor-unified-auth-worktree/risk-compliance-service main
```

If a branch/path already exists from a resumed run, inspect it and reuse it only when its status and HEAD match this plan; never delete it automatically.

- [ ] **Step 4: Run all three current generator checks**

```bash
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
node scripts/pdmaner/generate-client-contract-model.mjs --check
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
```

Expected: all three report current models up to date before any model edit.

- [ ] **Step 5: Run the six focused baseline test files**

```bash
node --test \
  scripts/pdmaner/platform-support-model.test.mjs \
  scripts/pdmaner/platform-support-scenario-doc.test.mjs \
  scripts/pdmaner/client-contract-model.test.mjs \
  scripts/pdmaner/client-contract-scenario-doc.test.mjs \
  scripts/pdmaner/risk-compliance-model.test.mjs \
  scripts/pdmaner/risk-compliance-scenario-doc.test.mjs
```

Expected: green baseline. Also run `npm --prefix requirements-site test`. Run lint separately; if the known Gateway-owned `site-content.test.mjs:780 no-regex-spaces` remains, record it and wait for that task rather than changing it here.

- [ ] **Step 6: Check all four repository boundaries**

Run `git status --short --branch` and `git diff --check` in the parent and each service worktree. Expected: only the already-approved design/plan files are new in the parent; service worktrees are clean.

### Task 2: Freeze the New Platform Test-Owned Contract First

**Files:**
- Modify: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/platform-support-model.test.mjs`
- Modify: `scripts/pdmaner/platform-support-scenario-doc.test.mjs`

**Interfaces:**
- Produces: red tests that define the exact approved table inventory, required/forbidden fields, relations, CHECK contracts and scenarios.
- Consumes: the Spec's Platform table list and Gateway cleanup.

- [ ] **Step 1: Replace the Oracle's identity and Gateway inventories**

In `APPROVED_TABLE_GROUPS.identity_access`, remove `t_idp_connection` and `t_user_identity`, retain the six Tenant/RBAC tables and eight API/Readiness/Business-OTP tables, and add exactly:

```js
[
  "t_user_account",
  "t_password_credential",
  "t_oidc_provider",
  "t_federated_identity",
  "t_authentication_challenge",
  "t_auth_session",
  "t_refresh_token_generation",
  "t_jwt_signing_key",
  "t_tenant_invitation",
]
```

In `APPROVED_TABLE_GROUPS.gateway`, remove `t_gateway_route` and `t_gateway_route_ver`; retain the other eleven policy/release tables. Add all four removed tables to `FORBIDDEN_TABLES`.

- [ ] **Step 2: Define required fields without changing generated totals/digests yet**

Add `REQUIRED_FIELDS` entries matching these exact field lists:

```js
const AUTH_REQUIRED_FIELDS = {
  t_user_account: ["account_id", "realm", "account_type", "login_name", "email_vault_token", "email_masked", "email_verified_time", "status", "consecutive_password_failures", "locked_until", "last_password_failure_time", "credential_version", "auth_epoch", "version"],
  t_password_credential: ["password_credential_id", "account_id", "password_verifier_phc", "algorithm_code", "algorithm_version", "memory_kib", "iterations", "parallelism", "credential_version", "force_change_flag", "status", "password_changed_time", "version"],
  t_oidc_provider: ["oidc_provider_id", "provider_code", "realm", "issuer", "client_id", "discovery_uri", "jwks_uri", "status", "config_version", "version"],
  t_federated_identity: ["federated_identity_id", "account_id", "oidc_provider_id", "provider_subject", "binding_method", "status", "bound_time", "revoked_time", "version"],
  t_authentication_challenge: ["challenge_id", "challenge_no", "realm", "purpose", "account_id", "tenant_invitation_id", "email_vault_token", "email_masked", "active_scope_hash", "secret_hmac", "hmac_key_version", "expires_time", "max_attempts", "used_attempts", "status", "active_slot", "consumed_time", "supersedes_challenge_id", "version"],
  t_auth_session: ["session_id", "account_id", "realm", "channel", "context_type", "tenant_id", "tenant_member_id", "authentication_method", "account_auth_epoch", "credential_version_snapshot", "issued_time", "last_reauthenticated_time", "last_activity_time", "idle_expire_time", "absolute_expire_time", "current_generation_no", "status", "revocation_reason", "revoked_time", "version"],
  t_refresh_token_generation: ["refresh_generation_id", "session_id", "generation_no", "token_lookup_id", "token_secret_hmac", "hmac_key_version", "issued_time", "expires_time", "status", "active_slot", "consumed_time", "replaced_by_generation_id", "version"],
  t_jwt_signing_key: ["signing_key_id", "realm", "kid", "algorithm", "kms_key_ref", "public_jwk_schema_version", "public_jwk_canonical_json", "status", "active_slot", "not_before", "signing_end_time", "verification_end_time", "version"],
  t_tenant_invitation: ["invitation_id", "invitation_no", "tenant_id", "invited_email_vault_token", "invited_email_masked", "member_type", "initial_role_id", "invited_by_member_id", "expires_time", "status", "active_slot", "accepted_account_id", "accepted_member_id", "accepted_time", "version"],
};
```

Keep the existing numeric totals and digests unchanged during this red phase; Task 5 replaces them only from generated evidence.

- [ ] **Step 3: Add model tests for positive and forbidden structure**

Add focused assertions equivalent to:

```js
test("unified human auth replaces IdP-only identity mapping", () => {
  const names = new Set(model.entities.map(({ defKey }) => defKey));
  for (const table of ["t_user_account", "t_password_credential", "t_oidc_provider", "t_federated_identity", "t_authentication_challenge", "t_auth_session", "t_refresh_token_generation", "t_jwt_signing_key", "t_tenant_invitation"]) {
    assert.ok(names.has(table), `missing ${table}`);
  }
  for (const table of ["t_idp_connection", "t_user_identity", "t_gateway_route", "t_gateway_route_ver"]) {
    assert.equal(names.has(table), false, `removed table remains: ${table}`);
  }
});

test("Gateway bindings use static route codes and cumulative dimensions", () => {
  assert.deepEqual(fieldNames("t_gateway_policy_binding_ver").includes("route_code"), true);
  assert.deepEqual(fieldNames("t_gateway_policy_binding_ver").includes("route_id"), false);
  assert.deepEqual(enumValues("t_gateway_guard_rule", "counter_dimension"), ["AUTHENTICATED_SUBJECT", "CLIENT_IP", "SHARED_ROUTE"]);
  assert.deepEqual(enumValues("t_gateway_release_item", "item_type"), ["BINDING", "POLICY"]);
});
```

Also assert login Email has no `email_blind_index`, Password uses `password_verifier_phc`, no table has a field matching `password$|private_key|client_secret|access_token|refresh_token$|id_token|authorization_code`, and JWT private material is absent.

- [ ] **Step 4: Add scenario markers for all new flows**

Require markers for Bootstrap completion, Operations lock, CUSTOMER Email verification/reset, Google Code+PKCE+state+nonce, sealed Pending Binding Proof plus existing-Email Password reauthentication, Google-only recent reauthentication before Password creation, Refresh reuse compromise, all three exact Channel Cookie names/paths, Account/Tenant Claim matrices, multi-Tenant invitation, at-least-one-active-login-method, CCT status projection and static Route Code cumulative Guard. Replace the old assertion requiring “Platform 不保存密码” with assertions that it stores only Argon2id PHC and never plaintext/Token/private key.

- [ ] **Step 5: Run the tests and verify RED for the intended reasons**

```bash
node --test scripts/pdmaner/platform-support-model.test.mjs scripts/pdmaner/platform-support-scenario-doc.test.mjs
```

Expected: failures name missing new tables/fields, still-present removed tables, old scenarios and old totals. No syntax or import failure is acceptable.

- [ ] **Step 6: Review the red contract**

Run `git diff --check` and inspect only the Oracle/tests. Confirm no production Manifest or model was changed in this task.

### Task 3: Implement Platform Account, Credential, Session and Tenant IAM Manifests

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifests/01-identity-access.mjs`
- Modify: `scripts/pdmaner/platform-support/manifests/02-file-document.mjs`
- Modify: `scripts/pdmaner/platform-support/manifests/04-webhook.mjs`
- Modify: `scripts/pdmaner/platform-support/manifests/05-audit-security.mjs`
- Modify: `scripts/pdmaner/platform-support/manifests/06-gateway.mjs`
- Modify: `scripts/pdmaner/platform-support/manifests/07-technical-config.mjs`
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`

**Interfaces:**
- Produces: nine new authentication tables, revised Tenant/Member ownership, migrated Account FKs, explicit composite FKs and CHECK policy.
- Consumes: Task 2's test-owned Platform contract.

- [ ] **Step 1: Replace the first two identity tables with the nine-table authentication inventory**

Use the existing `defineTable/field/pk/id/code/str/time/int/bool/hash/json/uk/ix/rel` DSL. Use BIGINT Snowflake IDs, DATETIME(6), CHAR(64) HMAC/Hash, and bounded VARCHAR values. The minimum physical shape is:

```text
user_account
  account_id BIGINT PK
  realm VARCHAR(64) NN
  account_type VARCHAR(64) NN
  login_name VARCHAR(64) N
  email_vault_token VARCHAR(255) N
  email_masked VARCHAR(255) N
  email_verified_time DATETIME(6) N
  status VARCHAR(64) NN
  consecutive_password_failures INT NN default 0
  locked_until DATETIME(6) N
  last_password_failure_time DATETIME(6) N
  credential_version INT NN
  auth_epoch INT NN
  version INT NN

password_credential
  password_credential_id BIGINT PK
  account_id BIGINT NN 1:1 FK
  password_verifier_phc VARCHAR(512) NN
  algorithm_code VARCHAR(64) NN = ARGON2ID
  algorithm_version INT NN
  memory_kib INT NN
  iterations INT NN
  parallelism INT NN
  credential_version INT NN
  force_change_flag BOOLEAN NN
  status VARCHAR(64) NN
  password_changed_time DATETIME(6) NN
  version INT NN

oidc_provider
  oidc_provider_id BIGINT PK
  provider_code VARCHAR(64) NN = GOOGLE
  realm VARCHAR(64) NN = CUSTOMER
  issuer VARCHAR(512) NN
  client_id VARCHAR(255) NN
  discovery_uri VARCHAR(1000) NN
  jwks_uri VARCHAR(1000) NN
  status VARCHAR(64) NN
  config_version INT NN
  version INT NN

federated_identity
  federated_identity_id BIGINT PK
  account_id BIGINT NN FK
  oidc_provider_id BIGINT NN FK
  provider_subject VARCHAR(255) NN
  binding_method VARCHAR(64) NN = NEW_ACCOUNT | PASSWORD_REAUTH | GOOGLE_REAUTH
  status VARCHAR(64) NN = ACTIVE | REVOKED
  bound_time DATETIME(6) NN
  revoked_time DATETIME(6) N
  version INT NN
```

Define the remaining five tables with exactly the Task 2 field lists. Use `public_jwk_canonical_json` as schema-versioned bounded JSON; it contains a public RSA JWK only. Add these unique identities:

```text
uk_user_account_email(realm,email_vault_token)
uk_user_account_login(realm,login_name)
uk_password_account(account_id)
uk_oidc_provider_code(provider_code)
uk_oidc_issuer_client(issuer,client_id)
uk_federated_provider_subject(oidc_provider_id,provider_subject)
uk_federated_account_provider(account_id,oidc_provider_id)
uk_auth_challenge_no(challenge_no)
uk_auth_challenge_active(active_scope_hash,active_slot)
uk_refresh_lookup(token_lookup_id)
uk_refresh_session_gen(session_id,generation_no)
uk_refresh_active(session_id,active_slot)
uk_jwt_signing_realm_kid(realm,kid)
uk_jwt_signing_active(realm,active_slot)
uk_tenant_invitation_no(invitation_no)
uk_tenant_invite_active(tenant_id,invited_email_vault_token,active_slot)
```

- [ ] **Step 2: Revise Access Tenant and Tenant Member**

Replace `access_tenant.status` with `owner_ref_type`, `owner_status`, `source_version`, `source_payload_hash`, and `security_status`; keep `tenant_type`, `owner_ref_id`, `tenant_name`, `version`, and the existing Owner unique key. Source Version/Hash is nullable only for INTERNAL; CLIENT/PARTNER requires both.

Replace `tenant_member.user_id` with `account_id`; remove SERVICE and INVITED enum values; add `uk_tenant_member_owner(member_id,tenant_id)` for composite ownership. Invitation state belongs only to `tenant_invitation`.

- [ ] **Step 3: Migrate every local Actor/User relation to User Account**

Change only the FK target, preserving child field names and business semantics:

```text
file_upload.uploader_id                       -> user_account.account_id
webhook_replay.requested_by                  -> user_account.account_id
security_alert.owner_id                      -> user_account.account_id
security_alert_action.actor_id               -> user_account.account_id
gateway_release.maker_id/checker_id          -> user_account.account_id
gateway_release_approval.actor_id            -> user_account.account_id
config_change_set.maker_id/checker_id        -> user_account.account_id
config_approval.actor_id                     -> user_account.account_id
```

Do not rename these historical Actor columns to Account in unrelated aggregates.

- [ ] **Step 4: Add composite FK contracts and login-sensitive reference sets**

In `model-policy.mjs`, remove both `user_identity` Vault tuples. Add these authentication-purpose Vault reference sets; none may contain a Blind Index or key-version field:

```text
user_account(email_vault_token,email_masked)
authentication_challenge(email_vault_token,email_masked)
tenant_invitation(invited_email_vault_token,invited_email_masked)
```

Add the supporting child indexes and these exact composite ownership contracts:

```js
compositeFk("fk_tenant_member_inviter", "t_tenant_member", ["invited_by", "tenant_id"], "t_tenant_member", ["member_id", "tenant_id"]),
compositeFk("fk_auth_session_member", "t_auth_session", ["tenant_member_id", "tenant_id"], "t_tenant_member", ["member_id", "tenant_id"]),
compositeFk("fk_invite_inviter", "t_tenant_invitation", ["invited_by_member_id", "tenant_id"], "t_tenant_member", ["member_id", "tenant_id"]),
compositeFk("fk_invite_accepted_member", "t_tenant_invitation", ["accepted_member_id", "tenant_id"], "t_tenant_member", ["member_id", "tenant_id"]),
```

Retain the existing Member Role and Role Scope composite contracts. Ordinary one-column relations cover Account, Provider, Challenge, Session, Invitation and Signing ownership. `initial_role_id` must resolve to an ACTIVE Role whose current Scope matches the Tenant; because the approved Invitation shape does not duplicate `role_scope_key`, freeze this as a locked transactional rule and scenario assertion instead of inventing an unenforceable FK.

- [ ] **Step 5: Add exact CHECK contracts**

Register and bind contracts with these semantics and SQL expressions:

```text
ck_user_account_shape:
  (account_type='BOOTSTRAP_ADMIN' AND realm='OPERATIONS' AND login_name='admin'
   AND email_vault_token IS NULL AND email_masked IS NULL AND email_verified_time IS NULL)
  OR
  (account_type='HUMAN' AND login_name IS NULL
   AND email_vault_token IS NOT NULL AND email_masked IS NOT NULL)

ck_password_argon2:
  algorithm_code='ARGON2ID' AND memory_kib>=19456 AND iterations>=2
  AND parallelism>=1 AND credential_version>0

ck_auth_challenge_slot:
  (status='PENDING' AND active_slot=1 AND consumed_time IS NULL)
  OR (status IN ('CONSUMED','EXPIRED','SUPERSEDED','REVOKED') AND active_slot IS NULL)

ck_auth_challenge_purpose:
  (purpose IN ('SIGN_UP_EMAIL','PASSWORD_RESET','EMAIL_CHANGE')
   AND account_id IS NOT NULL AND tenant_invitation_id IS NULL
   AND email_vault_token IS NOT NULL AND email_masked IS NOT NULL)
  OR
  (purpose='TENANT_INVITATION_ACCEPT' AND tenant_invitation_id IS NOT NULL
   AND email_vault_token IS NOT NULL AND email_masked IS NOT NULL)

ck_auth_session_context:
  (context_type='ACCOUNT' AND realm='CUSTOMER' AND tenant_id IS NULL AND tenant_member_id IS NULL)
  OR (context_type='TENANT' AND tenant_id IS NOT NULL AND tenant_member_id IS NOT NULL)
  OR (context_type='BOOTSTRAP' AND realm='OPERATIONS' AND channel='OPERATIONS'
      AND tenant_id IS NULL AND tenant_member_id IS NULL)

ck_refresh_generation_slot:
  (status='ISSUED' AND active_slot=1 AND consumed_time IS NULL)
  OR (status IN ('CONSUMED','REVOKED','EXPIRED') AND active_slot IS NULL)

ck_jwt_signing_key_slot:
  (status='ACTIVE' AND active_slot=1)
  OR (status IN ('PREPARED','RETIRING','RETIRED') AND active_slot IS NULL)

ck_tenant_owner_shape:
  (tenant_type='CLIENT' AND owner_ref_type='CLIENT')
  OR (tenant_type='PARTNER' AND owner_ref_type='LOCAL_PARTNER')
  OR (tenant_type='INTERNAL' AND owner_ref_type='PLATFORM')

ck_tenant_invitation_slot:
  (status='PENDING' AND active_slot=1 AND accepted_account_id IS NULL
   AND accepted_member_id IS NULL AND accepted_time IS NULL)
  OR (status='ACCEPTED' AND active_slot IS NULL AND accepted_account_id IS NOT NULL
      AND accepted_member_id IS NOT NULL AND accepted_time IS NOT NULL)
  OR (status IN ('EXPIRED','REVOKED','SUPERSEDED') AND active_slot IS NULL
      AND accepted_account_id IS NULL AND accepted_member_id IS NULL AND accepted_time IS NULL)
```

Also enforce:

- `user_account.credential_version>=0`; zero is valid for a never-password Google-only Account. Password Credential Version is positive, and Session `credential_version_snapshot>=0` freezes the Account value for every authentication method.
- positive Epoch/row-Version/Generation values; Challenge `max_attempts=5` and `used_attempts BETWEEN 0 AND max_attempts`.
- Session `issued_time <= last_activity_time < absolute_expire_time`, `idle_expire_time <= absolute_expire_time`, and revoked-time/status matrices.
- `authentication_method='GOOGLE'` only for CUSTOMER; ACCOUNT Context only for CUSTOMER; BOOTSTRAP only for OPERATIONS/OPERATIONS Channel; ordinary OPERATIONS Sessions use TENANT Context.
- OIDC Provider is exactly `GOOGLE + CUSTOMER`; Password Status, Federated Status, Invitation Email tuple and Tenant Source Version/Hash nullability matrices match the Spec.

Cross-row “at least one active login method”, same-Tenant Initial Role, current Member/Tenant status and Realm/Tenant membership rules remain explicit locked transactional rules in comments/docs/tests, not fake single-row CHECKs.

- [ ] **Step 6: Run the Platform model test and verify progress remains RED**

```bash
node --test scripts/pdmaner/platform-support-model.test.mjs
```

Expected: identity table/field/CHECK tests advance; failures remain for old generated PDManer, Gateway shape, totals/digests and scenarios.

- [ ] **Step 7: Review manifest-only changes**

Run `git diff --check` and scan all Platform manifests for `user_identity|idp_connection|email_blind_index`. Expected: no active auth relation or login Email Blind Index remains; historical docs are handled later.

### Task 4: Remove Dynamic Gateway Routes and Add Cumulative Guard Dimensions

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifests/06-gateway.mjs`
- Modify: `scripts/pdmaner/platform-support/model-policy.mjs`
- Modify: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**
- Produces: policy/release-only Gateway persistence keyed by static Route Code and cumulative dimension.
- Consumes: deployment-owned Route Catalog contract from the Gateway hardening design.

- [ ] **Step 1: Delete both Route table definitions and every relation to them**

Remove `gateway_route`, `gateway_route_ver`, the current-pointer composite FK, `ck_gateway_route`, Route diagram membership and all Route relation/index metadata. Do not retain an empty catalog/root table.

- [ ] **Step 2: Replace Binding Route ownership with an exact code**

In `gateway_policy_binding_ver`, remove `route_id` and `route_scope_key`, add non-null `route_code VARCHAR(64)`, and use:

```text
uk_gateway_binding_scope(priority_tier,scope_key,route_code,effective_from)
idx_gateway_binding_lookup(status,route_code,priority_tier,effective_from,effective_to)
```

Binding Content Hash comments must cover Policy Version ID/Hash, Tier, Scope, exact static Route Code, Effective Window and Selector Set Hash.

- [ ] **Step 3: Model nullable Dimension safely under MySQL**

Add `counter_dimension` nullable and `rule_scope_key` non-null. `counter_dimension` enum is SHARED_ROUTE/CLIENT_IP/AUTHENTICATED_SUBJECT. `rule_scope_key` equals that enum for RATE_LIMIT/CONCURRENCY and equals `NONE` for every other Rule Type. Replace the old per-type unique key with:

```text
uk_gateway_guard_scope(policy_ver_id,rule_type,rule_scope_key)
```

Add:

```text
ck_gateway_guard_dimension:
  (rule_type IN ('RATE_LIMIT','CONCURRENCY')
   AND counter_dimension IS NOT NULL
   AND counter_dimension IN ('SHARED_ROUTE','CLIENT_IP','AUTHENTICATED_SUBJECT')
   AND rule_scope_key=counter_dimension)
  OR
  (rule_type NOT IN ('RATE_LIMIT','CONCURRENCY')
   AND counter_dimension IS NULL AND rule_scope_key='NONE')
```

This Canonical Scope Key is required because MySQL nullable unique indexes do not enforce one null-scoped rule.

- [ ] **Step 4: Reduce Release Items to Policy or Binding**

Remove `route_ver_id` and its indexes/relation. Restrict `item_type` to POLICY/BINDING and update `ck_gateway_item_shape` to require exactly the matching one of `policy_ver_id` or `binding_ver_id`.

- [ ] **Step 5: Update policy digests and tests without freezing generated values**

The policy projection must include `counter_dimension`, `rule_scope_key`, static `route_code`, and the two-item Release shape. Add tests that no field named `path_pattern`, `http_method`, `runtime_route_ref`, `route_id` or `route_ver_id` exists in the Gateway group.

- [ ] **Step 6: Run focused tests and inspect expected RED**

```bash
node --test scripts/pdmaner/platform-support-model.test.mjs
```

Expected: Manifest/Policy contract passes; the committed model, totals/digests, physical decisions and scenarios remain red until Tasks 5–6.

- [ ] **Step 7: Check the task diff**

Run `git diff --check` and confirm no Gateway runtime repository path was touched.

### Task 5: Generate and Freeze the Platform Physical Model

**Files:**
- Modify: `scripts/pdmaner/platform-support/manifest.mjs`
- Modify: `scripts/pdmaner/platform-support/generate-platform-support-model.mjs`
- Modify: `platform-support-service/docs/database/platform-support.pdma.json`
- Modify: `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
- Modify: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/platform-support-model.test.mjs` only if a test-owned independent projection needs the observed digest

**Interfaces:**
- Produces: deterministic Platform PDManer, exact generated totals and frozen Model/Structural/CHECK-policy digests.
- Consumes: Tasks 2–4 Manifest/Policy/Oracle structure.

- [ ] **Step 1: Generate the model once**

First export the exact group-size-derived expected table total from `manifest.mjs` and make the Generator consume that value for both table/Diagram coverage invariants; do not retain a second hard-coded `92` or derive test expectations from production code.

```bash
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs
```

Expected: the command writes the nested service-worktree model and prints tables, fields, unique indexes, access indexes, relations, one-to-one relations and diagram groups.

- [ ] **Step 2: Derive model totals, Model SHA and CHECK-policy evidence from artifacts**

```bash
shasum -a 256 platform-support-service/docs/database/platform-support.pdma.json
node --input-type=module -e 'import {readFileSync} from "node:fs"; const m=JSON.parse(readFileSync("platform-support-service/docs/database/platform-support.pdma.json")); const i=m.entities.flatMap(e=>e.indexes); const r=m.entities.flatMap(e=>e.correlations); console.log({tables:m.entities.length,fields:m.entities.flatMap(e=>e.fields).length,uniqueIndexes:i.filter(x=>x.unique).length,accessIndexes:i.filter(x=>!x.unique).length,relations:r.length,oneToOneRelations:r.filter(x=>x.myRows==="1").length,diagramGroups:m.diagrams.length});'
node --input-type=module -e 'import {createHash} from "node:crypto"; import {CHECK_BINDINGS,CHECK_CONTRACTS} from "./scripts/pdmaner/platform-support/model-policy.mjs"; const p=Object.keys(CHECK_CONTRACTS).sort().map(name=>({name,expression:CHECK_CONTRACTS[name],tables:CHECK_BINDINGS[name]})); console.log({checkCount:p.length,checkPolicyDigest:createHash("sha256").update(JSON.stringify(p)).digest("hex")});'
```

Record the exact outputs. The CHECK command intentionally mirrors the sorted test-owned projection; do not hash the raw objects directly.

- [ ] **Step 3: Freeze the independent Structural Projection digest**

First update only `EXPECTED_GROUP_SIZES`, `EXPECTED_TOTALS`, `EXPECTED_MODEL_DIGEST`, `EXPECTED_CHECK_COUNT` and `EXPECTED_CHECK_POLICY_DIGEST` from Step 2, leaving the old Structural Digest in place. Then run:

```bash
node --test --test-name-pattern="Platform Support model matches independent totals and structural digest" \
  scripts/pdmaner/platform-support-model.test.mjs
```

Expected: totals pass and the final equality reports the actual 64-hex digest produced by the test-owned projection. Freeze that exact `actual` value as `EXPECTED_STRUCTURAL_DIGEST`, add the observed required composite FKs/CHECK bindings, and rerun the same focused test to GREEN. Do not substitute the production generator's different projection or leave the old 92/1,076-era values.

- [ ] **Step 4: Semantically update Physical Decisions**

Start from the service worktree file, compare the previously captured owner diff, and use `apply_patch` to preserve every unrelated current decision. Add exact Account/Password/OIDC/Challenge/Session/Refresh/Signing/Tenant/Invitation tables, static Route Code Gateway shape, cumulative dimensions, final totals and exactly one current Model SHA-256.

- [ ] **Step 5: Run Platform generation and model tests to GREEN**

```bash
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
node --test scripts/pdmaner/platform-support-model.test.mjs
```

Expected: both pass with exact totals/digests. Scenario tests may remain red until Task 6.

- [ ] **Step 6: Check both repositories**

Run parent and Platform service-worktree `git status --short` plus `git diff --check`. Confirm the generated JSON changed only through the generator.

### Task 6: Replace Platform Historical Auth/Gateway Documentation and Scenarios

**Files:**
- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Modify: `agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md`
- Modify: `agent-docs/plans/2026-08-29-platform-support-operations-authentication-requirements.md`
- Modify: `agent-docs/plans/2026-08-27-platform-support-balanced-physical-model-design.md`
- Modify: `agent-docs/plans/2026-08-27-platform-support-pdmaner-implementation-plan.md`
- Test: `scripts/pdmaner/platform-support-scenario-doc.test.mjs`

**Interfaces:**
- Produces: current Platform logical authority and one complete realistic scenario row per approved table.
- Consumes: Task 5 exact stats and model field comments.

- [ ] **Step 1: Replace section 4.1 with the generated current identity inventory**

Document every generated field/index/relation and these flows: one-time Bootstrap, Operations Email provisioning, CUSTOMER Email verification/reset, Google subject/new-account/existing-Email binding, Account/Tenant sessions, Refresh rotation/reuse, signing-key overlap, invitations, current authorization and dual Tenant status.

Remove the old claim that an external IdP completes all login/MFA/Token work. Keep Business Email OTP in its existing separate section and explicitly forbid authentication reuse.

- [ ] **Step 2: Replace the Gateway section with the generated policy-only shape**

Document static `route_code`, `rule_scope_key`, three cumulative dimensions, Policy/Binding-only Release Items, Last-Known-Good and Redis runtime state. Remove every active Route/Path/Method/Upstream publication description.

- [ ] **Step 3: Rewrite SCN-PLT-01 and Gateway scenarios with concrete rows**

Use stable example IDs and include at least these scenario branches:

```text
BOOTSTRAP admin -> force change -> first Email PLATFORM_ADMIN -> permanent disable
OPERATIONS Email Password failures 5 -> locked_until +30m
CUSTOMER Email signup -> SIGN_UP_EMAIL Challenge -> ACTIVE
Google new Subject -> Account/Binding
Google Authorization Code + PKCE/state/nonce -> same-Channel callback
Google same Email but new Subject -> sealed Pending Binding Proof -> Password reauth -> Binding
Google-only Account -> recent Google reauth -> Password creation
one Account -> CLIENT and PARTNER Members
Invitation acceptance -> one Member + one initial Role
Refresh generation 7 -> 8; generation 7 reuse -> COMPROMISED Session
CCT PENDING/ACTIVE owner projection combined with Platform security_status
static route_code policy with three cumulative Rate/Concurrency dimensions
```

Freeze all three exact Refresh Cookie names/paths, Account-vs-Tenant Claim matrices, Realm-specific issuer/key-ring isolation, “at least one ACTIVE login method”, common anti-enumeration errors, unified lock order and audit events in the prose and machine-readable scenario assertions.

The scenario manifest at the bottom must list every current table exactly once in its mapped scenario, expose all new closed enums, and remove all four deleted tables.

- [ ] **Step 4: Mark historical plans as superseded without rewriting history**

Add a top warning/link to the approved Spec and current model. Keep old-stage numbers only when explicitly labeled historical; remove “authentication physical revision not authorized” from current navigation semantics because this implementation plan is the authorization being executed.

- [ ] **Step 5: Run Platform scenario and model tests**

```bash
node --test scripts/pdmaner/platform-support-model.test.mjs scripts/pdmaner/platform-support-scenario-doc.test.mjs
```

Expected: both green; minimum-data tests reject plaintext Password, Token, OIDC Secret/private key and dynamic Route fields.

- [ ] **Step 6: Review documentation links and stats**

Run `git diff --check`, verify every local link in the five touched docs resolves, and compare all displayed Platform totals to Task 5 generator output.

### Task 7: Write the CCT Partner Application Contract as Failing Tests

**Files:**
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`
- Modify: `scripts/pdmaner/client-contract-scenario-doc.test.mjs`
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs` only for test-owned enum/BCR catalogs after the initial red assertion exists

**Interfaces:**
- Produces: red CCT tests for `partner_application`, request Account reference and three-parent Application Document.
- Consumes: stable CCT-to-Platform and CCT-to-Risk contracts.

- [ ] **Step 1: Add the expected table, fields and display name**

Add `partner_application` to the expected table list, Onboarding diagram and primary-key/display-name maps. Define exact fields:

```js
[
  "application_id", "application_no", "local_partner_id",
  "target_party_profile_id", "requested_by_account_id", "status",
  "active_slot", "due_diligence_case_id", "qualification_decision_id",
  "qualification_decision_version", "qualification_type", "submitted_time",
  "submission_hash_schema_version", "submission_content_hash", "create_by",
  "create_time", "update_time",
]
```

Add `requested_by_account_id` after `client_id` in `client_application`, and `partner_application_id` between the existing two parent fields in `application_document`.

- [ ] **Step 2: Add exact unique and relation expectations**

```text
partner_application: application_no; local_partner_id,active_slot
application_document partner key:
  partner_application_id,subject_party_id,requirement_code,document_type,file_id

application_document.partner_application_id -> partner_application.application_id | n:1
partner_application.local_partner_id -> local_partner.local_partner_id | n:1
partner_application.target_party_profile_id -> party_profile.profile_id | n:1
```

`requested_by_account_id`, Risk Case and Decision coordinates remain external references without CCT FK.

- [ ] **Step 3: Extend closed enums, BCR and conditional expectations**

Add:

```js
"partner_application.status": ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN"],
"partner_application.qualification_type": ["PARTNER_DUE_DILIGENCE"],
```

Add `PARTNER_APPLICATION` to BCR record/root types and the active-slot rule. Keep `application_document` BCR-excluded.

- [ ] **Step 4: Test the three-parent XOR and pre-Tenant Account rule**

Add assertions that generated comments/conditional rules contain exactly:

```text
(client_application_id IS NOT NULL)
+ (partner_application_id IS NOT NULL)
+ (operating_entity_application_id IS NOT NULL) = 1
```

Also require Client ONBOARDING and every Partner Application to carry `requested_by_account_id`, while no FK points to Platform.

- [ ] **Step 5: Run CCT tests and verify intended RED**

```bash
node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs
```

Expected: missing table/fields/relation/scenario and old total/digest failures; no generic test crash.

- [ ] **Step 6: Review the red CCT contract**

Run `git diff --check`; no CCT logical catalog, generator or generated model changes yet.

### Task 8: Implement and Generate the CCT Partner Application Model

**Files:**
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs`
- Modify: `scripts/pdmaner/generate-client-contract-model.mjs`
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`
- Modify: `client-contract-service/docs/database/client-contract.pdma.json`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`

**Interfaces:**
- Produces: generated Partner Application, three-parent Document, BCR catalog and exact CCT stats/digest.
- Consumes: Task 7 red contract.

- [ ] **Step 1: Add the exact logical catalog row before generating**

Place `partner_application` beside Client/OE Applications. Its physical fields and unique keys must match Task 7. Document that creation atomically creates/reuses one PENDING Local Partner, Organization Party and Target Party Profile; only APPROVED activates the Role; rejected/withdrawn applications leave the stable PENDING Role available for resubmission.

Update Client Application and Application Document rows with the two new columns and the three-parent XOR. Add `PARTNER_APPLICATION` to BCR record/root lists and audit responsibility tables.

- [ ] **Step 2: Extend metadata and generator policy**

Add table/field Chinese names and descriptions, nullable paths for the three Document parent columns and nullable Risk decision/time fields, closed enums, conditional value rules, BCR types, the new relation signatures and Onboarding diagram membership.

Update generator clauses to enforce:

```text
Partner qualification_type = PARTNER_DUE_DILIGENCE
active_slot = 1 only for DRAFT/SUBMITTED/UNDER_REVIEW
Decision ID/Version are both null or both non-null
APPROVED requires due_diligence_case_id + decision tuple
WITHDRAWN may omit decision
Client ONBOARDING requires requested_by_account_id
exactly one of three Application Document parents
```

- [ ] **Step 3: Generate CCT PDManer**

```bash
node scripts/pdmaner/generate-client-contract-model.mjs
```

Expected: deterministic write to the nested CCT service worktree.

- [ ] **Step 4: Derive exact CCT totals and test-owned structural digest**

```bash
node --input-type=module -e 'import {readFileSync} from "node:fs"; const m=JSON.parse(readFileSync("client-contract-service/docs/database/client-contract.pdma.json")); const i=m.entities.flatMap(e=>e.indexes); const r=m.entities.flatMap(e=>e.correlations); console.log({tables:m.entities.length,fields:m.entities.flatMap(e=>e.fields).length,uniqueIndexes:i.filter(x=>x.unique).length,accessIndexes:i.filter(x=>!x.unique).length,relations:r.length,oneToOneRelations:r.filter(x=>x.myRows==="1").length,diagramGroups:m.diagrams.length});'
shasum -a 256 client-contract-service/docs/database/client-contract.pdma.json
```

Update the expected inventory/totals first, deliberately retaining the old Structural Digest, then run:

```bash
node --test --test-name-pattern="locks the approved Structural Projection" \
  scripts/pdmaner/client-contract-model.test.mjs
```

Expected: the equality reports the test-owned projection's actual 64-hex digest. Freeze that exact value and rerun to GREEN. Do not use a hand-written approximation of `structuralProjection(model)`.

- [ ] **Step 5: Update Physical Decisions and Model SHA**

Document the Partner Application, Account external reference, Document XOR, BCR ownership, after-commit Platform/Risk commands and exact totals. Calculate `shasum -a 256 client-contract-service/docs/database/client-contract.pdma.json` and keep exactly one current model digest in Physical Decisions.

- [ ] **Step 6: Run CCT generator and model tests**

```bash
node scripts/pdmaner/generate-client-contract-model.mjs --check
node --test scripts/pdmaner/client-contract-model.test.mjs
```

Expected: model test green; scenario test remains red until Task 9.

- [ ] **Step 7: Check parent/CCT service diffs**

Run status and `git diff --check` in both repositories. Confirm generated JSON is only in the service worktree and no Platform Account FK was created in CCT.

### Task 9: Complete CCT Onboarding Documentation and Real-World Scenarios

**Files:**
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Test: `scripts/pdmaner/client-contract-scenario-doc.test.mjs`

**Interfaces:**
- Produces: end-to-end Client/Partner pre-Tenant onboarding and full current table coverage.
- Consumes: Task 8 generated fields and exact stats.

- [ ] **Step 1: Document account-first onboarding and event order**

Add a section stating Platform Account verification precedes CCT Application, `requested_by_account_id` is not a Party/Signatory, and the CCT transaction writes both the Application/BCR and a `ProvisionAccessTenant` LOCAL_HANDLER event. Define same-key/same-Hash idempotency, different-Hash conflict and Status-before-Provision retry.

- [ ] **Step 2: Extend the Nova/Gulf scenario with concrete Partner Application rows**

Add one `t_partner_application` row for Gulf using `qualification_type=PARTNER_DUE_DILIGENCE`, a third `t_application_document` example with only `partner_application_id`, and the Platform PENDING Tenant/OWNER/Onboarding Role command payload. Demonstrate APPROVED activation and a REJECTED resubmission that reuses the same Local Partner ID.

- [ ] **Step 3: Update the machine-readable scenario manifest**

Add `t_partner_application`, the new fields, closed enums and scenario mapping; update totals only from Task 8. Ensure every generated CCT table appears in exactly one mapped scenario and all field assignments exist in PDManer.

- [ ] **Step 4: Run both CCT tests to GREEN**

```bash
node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs
```

Expected: both green with complete current-table coverage.

- [ ] **Step 5: Review CCT-only output**

Run generator `--check`, link checks and `git diff --check`; scan for the old phrase “两类申请共享” and the two-parent XOR.

### Task 10: Extend Risk Typed Source Namespace Without Changing Its Boundary

**Files:**
- Modify: `scripts/pdmaner/risk/manifests/01-due-diligence.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify: `risk-compliance-service/docs/database/risk-compliance.pdma.json`

**Interfaces:**
- Produces: `CCT_LOCAL_PARTNER_APPLICATION` in the existing five-part Case identity.
- Consumes: Task 8 Partner Application identity and Submission Hash.

- [ ] **Step 1: Add failing main/related-person tuple tests**

Before changing the Manifest, add:

```js
assertApplicationTupleAccepted({
  sourceNamespace: "CCT_LOCAL_PARTNER_APPLICATION",
  sourceApplicationId: 730320000000000001n,
  roleType: "LOCAL_PARTNER",
  roleId: 730300000000000001n,
  qualificationType: "PARTNER_DUE_DILIGENCE",
});
assertApplicationTupleAccepted({
  sourceNamespace: "CCT_LOCAL_PARTNER_APPLICATION",
  sourceApplicationId: 730320000000000001n,
  roleType: "RELATED_PERSON",
  roleId: 730100000000000009n,
  qualificationType: "RELATED_PERSON_KYC",
});
```

Also assert Client/OE Role types are rejected under the new Namespace and the same numeric Application ID remains isolated across all three CCT Namespaces.

- [ ] **Step 2: Run the focused Risk test and verify RED**

```bash
node --test scripts/pdmaner/risk-compliance-model.test.mjs
```

Expected: the new namespace is outside the current enum/CHECK.

- [ ] **Step 3: Update Manifest, Oracle and CHECK expression**

Add `CCT_LOCAL_PARTNER_APPLICATION` to `source_namespace`. Extend `ck_dd_case_origin` APPLICATION branch with exactly:

```text
(source_namespace='CCT_LOCAL_PARTNER_APPLICATION'
 AND role_type IN ('LOCAL_PARTNER','RELATED_PERSON'))
```

Keep the five-part unique index unchanged. `ck_dd_case_tuple` already permits LOCAL_PARTNER/PARTNER_DUE_DILIGENCE and RELATED_PERSON/RELATED_PERSON_KYC; do not add a Case table, index or relation.

- [ ] **Step 4: Regenerate and freeze Risk digests from evidence**

```bash
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs
shasum -a 256 risk-compliance-service/docs/database/risk-compliance.pdma.json
```

Run the same structural/CHECK-policy digest mechanisms used by the current Risk tests and update only observed digests. Recompute totals from the model even though the intended structure does not add tables/fields/indexes/relations/CHECK rows.

- [ ] **Step 5: Update Risk logical, physical and scenario docs**

Add the Namespace to the matrix and a Partner main Case plus related-person child. Explicitly state CCT only receives the Local Partner main Case/Decision, while Risk owns child KYC dependencies. Update the single current model SHA in Physical Decisions.

- [ ] **Step 6: Run all Risk gates**

```bash
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
node --test scripts/pdmaner/risk-compliance-model.test.mjs scripts/pdmaner/risk-compliance-scenario-doc.test.mjs
```

Expected: green, with unchanged structural boundary totals if the generated evidence confirms it.

- [ ] **Step 7: Review Risk isolation**

Run parent/Risk service `git diff --check` and scan the new Namespace for an accidental `CLIENT` or `OPERATING_ENTITY` Role allowance.

### Task 11: Synchronize Product Requirements, Interfaces and Global Sequences

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/components/architecture-diagrams.tsx`
- Modify: `requirements-site/components/requirements-site.tsx` only if the new labels need rendering support
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`

**Interfaces:**
- Produces: canonical product/use-case contracts for unified authentication, Tenant IAM and Partner onboarding.
- Consumes: all three generated model contracts and the final Gateway task baseline.

- [ ] **Step 1: Wait for and semantically import the Gateway task's final shared baseline**

Obtain its final main HEAD and changed-file list. If it fixed the known regex to `\n {2}\},`, apply that exact upstream one-line correction to this worktree before adding auth assertions. Do not overwrite any Gateway hardening tests or restore the old `__Secure-mor-*` name.

- [ ] **Step 2: Write failing catalog assertions**

Require:

```text
Operations = Email/Password only; admin is the sole non-Email bootstrap
CUSTOMER = Email/Password + Google OIDC
one CUSTOMER Account can join CLIENT and PARTNER Tenants
one Access Token = zero or one Tenant Member
business Email OTP != Authentication Challenge
CCT owns Partner Application; Risk uses CCT_LOCAL_PARTNER_APPLICATION
Gateway DB has no Route ownership and Guard dimensions accumulate
Google uses Code+PKCE+state+nonce and never auto-links by matching Email
the three Refresh Cookies keep exact Channel-specific names and paths
```

Require the catalog to contain new interface IDs PLT-I15 through PLT-I18, CCT-I15/CCT-I16 and RCO-I11 as defined below.

- [ ] **Step 3: Update the Platform system contract**

Keep PLT-01 for Operations and add:

```text
PLT-14  管理 CUSTOMER 全局账号与 Google 绑定
PLT-15  管理 Tenant Member、Role 与 Invitation

PLT-I15 执行 CUSTOMER 注册、Email 验证、Password/Google 登录、Refresh、Logout
PLT-I16 选择 Account 或单一 Tenant Member 上下文并签发对应 Audience Token
PLT-I17 接收 CCT Access Tenant Provision/Status 版本化投影
PLT-I18 创建、撤销和接受 Tenant Invitation
```

Revise Platform responsibility/source-of-truth/domains/exclusions so Client/Partner authentication is current, Consumer authentication remains excluded, and business OTP remains separate. Update COARSE_ARCHITECTURE from `IAM Binding` to Account/Credential/Session/Tenant IAM.

- [ ] **Step 4: Update CCT and Risk contracts**

Revise CCT-01/CCT-05 to say Account exists first and CCT creates PENDING Client/Local Partner Applications. Add:

```text
CCT-I15 提交 Client 或 Local Partner 入驻申请；冻结 requested_by_account_id 并登记 Platform/Risk LOCAL_HANDLER
CCT-I16 按 requested_by_account_id 查询本人 Client/Partner Application 进度
RCO-I11 按 Typed Namespace/Application/Role/Qualification/Submission Hash 幂等创建或定位尽调 Case
```

RCO-I11 is internal to CCT and returns the main Role Case identity only.

- [ ] **Step 5: Extend E2E-01 and add E2E-14**

E2E-01 begins with CUSTOMER Email/Google authentication, Account Context, Client Application and after-commit Tenant Provision before its existing readiness flow. Preserve the current Typed Client Namespace, Beneficiary and readiness semantics.

Add E2E-14 “Local Partner 注册、尽调并获得租户访问” with participants Partner -> Partner Console -> Gateway -> Partner BFF -> Platform -> CCT -> Risk -> Platform. Its messages must show global Account registration, Partner Application, `CCT_LOCAL_PARTNER_APPLICATION`, related-person child Cases, CCT decision, versioned Tenant activation and one Partner Tenant token.

- [ ] **Step 6: Update architecture diagrams and entry applications**

Client/Partner Console capabilities include Email registration, Google Login, invitation and Tenant selection. Operations remains Password-only. Diagrams show all browser calls through channel-specific BFFs, Platform as authentication authority, and CCT/Risk as business/qualification owners.

- [ ] **Step 7: Run requirements red then green**

Run focused tests after assertions to observe RED, then apply content changes and run:

```bash
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: build and all tests pass; ESLint has zero errors. Do not suppress `no-regex-spaces` or other rules.

- [ ] **Step 8: Review product boundaries**

Scan for `Client、Partner、Consumer.*待.*设计|不为 Client、Partner.*认证|外部 IdP.*唯一` in active requirements. Expected: no stale active statement; Consumer remains explicitly out of scope.

### Task 12: Update Shared Authority, Navigation and Final Statistics

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/APPLICATION_ARCHITECTURE.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Create: `agent-docs/plans/2026-08-30-platform-unified-human-authentication-pdmaner-implementation-plan.md`

**Interfaces:**
- Produces: one current authority chain and exact generated snapshots across shared entry documents.
- Consumes: Task 5 Platform stats/digests, Task 8 CCT stats/digest, Task 10 Risk digest/stats and Task 11 product wording.

- [ ] **Step 1: Re-read latest main versions before touching shared files**

Compare each shared file against the current main workspace and the Gateway task's final changed-file list. Apply semantic increments to the worktree copy; never restore old Finance/Risk/Gateway numbers or language.

- [ ] **Step 2: Promote the approved Spec and current generated models**

Add `docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md` and the new implementation record to routing/navigation. Mark the 2026-08-29 Operations-only requirement and 2026-08-27 Platform balanced model as historical where superseded.

- [ ] **Step 3: Update architecture boundaries**

Document Account/Realm/Channel/BFF/Tenant ownership, CCT Partner Application, Risk Namespace and the rule that Gateway only validates minimal JWT/static Route facts. Keep BFF stateless and core resource authorization intact.

- [ ] **Step 4: Replace all affected numeric snapshots from generated evidence**

Update CCT and Platform totals in every shared file from Tasks 5/8; update Risk only if Task 10 generated evidence changed any total. Preserve Commerce/Payment/Finance exact current snapshots from latest main. Include Platform composite-FK and CHECK counts from its Oracle.

- [ ] **Step 5: Write the implementation record with evidence**

The new agent-doc plan records:

```text
approved design and superseded semantics
exact changed table inventory
exact generated totals and three Platform digests
exact CCT totals/model/structural digest
exact Risk totals/model/structural/CHECK-policy digest
test commands and counts
manual PDManer 4.9.3 gate still open
no Java/Migration/runtime authorization
```

Do not mark unrun tests or manual review complete.

- [ ] **Step 6: Run stale-stat and stale-semantic scans**

Search active authority files for the previous CCT/Platform exact snapshots, `IdP-only`, “Client/Partner authentication pending”, dynamic Gateway Route publication, `t_user_identity`, `t_idp_connection`, `t_gateway_route_ver`, and two-parent Application Document wording. Historical records may retain old facts only with an explicit historical/superseded marker.

- [ ] **Step 7: Check shared diffs and links**

Run `git diff --check` and validate every changed relative Markdown link. Confirm unrelated sections in all seven shared files are unchanged.

### Task 13: Run Full Automated Gates and Independent Structural Review

**Files:**
- Verify only: all changed parent and service-worktree files
- Update only if evidence exposes a defect: the owning Task's files

**Interfaces:**
- Produces: fresh full-suite evidence and a changed-file/stat/digest handoff.
- Consumes: Tasks 1–12.

- [ ] **Step 1: Run all six generator checks**

```bash
node scripts/pdmaner/generate-client-contract-model.mjs --check
node scripts/pdmaner/generate-commerce-model.mjs --check
node scripts/pdmaner/generate-payment-model.mjs --check
node scripts/pdmaner/finance/generate-finance-model.mjs --check
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
```

Expected: six exits 0 and generated artifacts byte-identical.

- [ ] **Step 2: Run all PDManer and scenario tests**

```bash
node --test scripts/pdmaner/*.test.mjs
```

Expected: zero failures. Record the exact test count from output rather than reusing a historical count.

- [ ] **Step 3: Run requirements build/tests/lint**

```bash
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: zero failures/errors and a fresh exact test count.

- [ ] **Step 4: Validate relative Markdown links**

Run this read-only checker from the parent worktree:

```bash
node --input-type=module -e 'import fs from "node:fs"; import path from "node:path"; import {execFileSync} from "node:child_process"; const files=execFileSync("rg",["--files","-g","*.md"],{encoding:"utf8"}).trim().split("\n").filter(Boolean); const bad=[]; for(const f of files){const s=fs.readFileSync(f,"utf8"); for(const m of s.matchAll(/\[[^\]]*\]\(([^)]+)\)/gu)){let t=m[1].replace(/^<|>$/gu,"").split("#",1)[0]; if(!t||/^(?:https?:|mailto:)/u.test(t)) continue; t=decodeURIComponent(t); if(!fs.existsSync(path.resolve(path.dirname(f),t))) bad.push(`${f} -> ${t}`);}} if(bad.length){console.error(bad.join("\n"));process.exit(1)} console.log(`Markdown links OK: ${files.length} files`);'
```

Expected: no missing relative target.

- [ ] **Step 5: Run hygiene and secret scans**

```bash
rg -n '<<<<<<<|=======|>>>>>>>' . \
  -g '!requirements-site/node_modules/**' -g '!*.pdma.json'
rg -n 'password=|password_plain|private_key|client_secret|access_token=|refresh_token=|authorization_code=' \
  agent-docs scripts requirements-site/content platform-support-service/docs client-contract-service/docs risk-compliance-service/docs
```

Expected: no conflict marker. Classify every secret-pattern match: approved field names such as `password_verifier_phc` and explicit prohibition prose are allowed, while assignments, example values, persisted columns/payloads or credentials are failures. Record the reviewed match count instead of claiming this deliberately broad scan is empty.

- [ ] **Step 6: Independently recompute model totals and digests**

Read each of the three JSON artifacts without importing production manifests, recompute table/field/index/relation/1:1/diagram totals and SHA-256, and compare to test-owned Oracle/docs. Recompute Platform/Risk CHECK-policy digests from test-owned projections.

- [ ] **Step 7: Run diff checks in all repositories**

```bash
git diff --check
git -C platform-support-service diff --check
git -C client-contract-service diff --check
git -C risk-compliance-service diff --check
```

Expected: four exits 0. Record `git status --short` and exact changed files for all repositories.

- [ ] **Step 8: Keep the manual gate honest**

Record PDManer 4.9.3 application review as still required unless a human actually opens and approves all three changed models. Do not convert automated test success into manual approval.

### Task 14: Coordinate and Semantically Integrate Without Overwriting Other Tasks

**Files:**
- Integrate: all parent changed files from Tasks 2–13
- Integrate: two Platform service artifacts
- Integrate: two CCT service artifacts
- Integrate: two Risk service artifacts
- Preserve: all unrelated main/shared/service changes

**Interfaces:**
- Produces: final main workspace content ready for user review, with no lost concurrent work.
- Consumes: Task 13 green evidence and final status messages from all active tasks.

- [ ] **Step 1: Obtain explicit final coordination state**

Ask the task `准备网关设计` for final main HEAD, changed files and tests. Recheck all four main repository statuses. Do not integrate while another task reports an active write to a shared file.

- [ ] **Step 2: Diff every overlapping shared file against latest main**

Use `diff -u` read-only comparisons. For `AGENTS.md`, root/agent README, PROJECT_CONTEXT, APPLICATION_ARCHITECTURE, DATABASE_DESIGN_GUIDE, database README, requirements content/tests and Platform Physical Decisions, classify each hunk by owner and apply only the authentication semantic increment with `apply_patch`.

- [ ] **Step 3: Integrate service artifacts semantically**

Integrate parent Manifest/Generator sources first. After confirming each destination service repository has no divergent model writer, run the corresponding generator from the integrated main workspace so it writes the exact PDManer JSON at the destination path; do not copy a stale JSON wholesale. For Physical Decisions, merge paragraphs/digests semantically—especially the pre-existing Platform owner diff—rather than replacing files.

- [ ] **Step 4: Preserve known unrelated dirty files**

Verify `agent-docs/COMPENSATION_EVENT_GUIDE.md` and `scripts/verify-java-foundation.mjs` still match their pre-task hashes/status. Do not stage, format or mention them as this task's changes.

- [ ] **Step 5: Re-run Task 13 on the integrated main workspace**

All six generator checks, all PDManer/scenario tests, requirements test/lint, links, scans and diff checks must be fresh on the final integrated files. If a failure comes from a concurrent change, coordinate with its owner and rerun; do not weaken this plan's assertions.

- [ ] **Step 6: Report final evidence and leave worktrees intact**

Report final Platform/CCT/Risk totals and digests, changed files per repository, test counts, manual-gate status and any unrelated residual dirt. Do not commit, delete branches or remove worktrees until the user explicitly requests the next integration action.
