# Platform Support 57-Table Consumer-Driven Database Simplification Design

Date: 2026-09-01

Status: approved and implemented in the generated 57-table design assets; PDManer 4.9.3 in-app manual review remains pending

> **Historical-stage notice:** this document freezes the 57-table simplification stage. The later
> [unified public API dynamic control-plane design](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)
> supersedes only its static-Gateway conclusion and adds six current configuration tables. The current generated
> successor is exactly 63 tables / 822 fields / 123 unique indexes / 99 access indexes / 107 local relations / 5
> one-to-one relations / 7 Diagram Groups, with 31 composite-FK contracts, 85 named CHECK contracts and the same exact
> 36 `*_hash` fields. The 57-table numbers below remain implementation history, not the current physical snapshot.

This design supersedes the Platform Support table topology, Gateway-control-plane, generic technical-configuration,
Integration Readiness persistence, generic Root/Version pattern, and broad Hash-field policy in:

- `agent-docs/plans/2026-08-27-platform-support-balanced-physical-model-design.md`;
- `agent-docs/plans/2026-08-27-platform-support-pdmaner-implementation-plan.md`;
- `agent-docs/plans/2026-08-30-platform-unified-human-authentication-pdmaner-implementation-plan.md`;
- `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`;
- `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`.

The unified human-authentication decisions in
`docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md` remain authoritative except where
this design deliberately removes the persisted OIDC Provider catalog, persisted Integration Readiness evaluations,
Platform-owned Gateway policy publication, and generic Platform technical-configuration lifecycle. At that 57-table
checkpoint, Operations Route and Guard configuration stayed in Gateway code. The successor design replaces that
static-only configuration conclusion while preserving Gateway's trusted protocol, precompiled security implementation,
absolute safety ceilings and Redis runtime ownership.

## 1. Historical 57-table purpose

Reduce Platform Support from 97 tables to exactly 57 tables by requiring every table, version, history record, and
Hash field to have a named runtime consumer. Preserve only the state needed for human authentication, tenant IAM,
Client API credentials, files and consumer documents, notifications, Client Webhooks, audit/security, and local
reliable execution.

The simplification has four concrete outcomes:

1. At the 57-table checkpoint, Platform MySQL owned no Gateway Route or Guard configuration; the successor restores only
   six current-configuration tables and still excludes releases, snapshots, per-Tenant quotas and runtime limiter state.
2. Platform MySQL no longer acts as a generic technical configuration service.
3. Necessary historical revisions remain reproducible without the default `root + current_ver_id + *_ver` pattern.
4. `*_hash` fields fall from 98 to exactly 36 and are accepted only for a named security, idempotency, integrity, or
   privacy-preserving lookup consumer.

## 2. Scope and non-goals

### 2.1 In scope

- Platform Support PDManer Manifest, policy, Oracle, generated model, logical database document, scenario document,
  physical decisions, tests, statistics, shared navigation, and canonical requirements.
- Tenant ownership normalization for API Credential, Webhook, Notification, File, and Consumer Document roots.
- Static deployment configuration for Google OIDC and Gateway Guard facts.
- Removal of persisted Integration Readiness evaluations in favor of a synchronous derived query.
- Deterministic regeneration and full six-system regression validation.

### 2.2 Out of scope

- Java modules, Flyway migrations, DOs, Mappers, Repositories, physical databases, data migration, deployment, Nacos,
  KMS, Secrets Manager, Vault, Redis, Gateway code, BFFs, frontends, or external infrastructure.
- Apple, Microsoft, SAML, LDAP, Passkey, Consumer authentication, MFA, dynamic per-Tenant quotas, or a generic IdP
  registry.
- Generic retention, Legal Hold, archive, anonymization, destruction, or a WORM archive workflow.
- Replacing domain-owned state histories in the other five core systems.

## 3. First-principles retention rules

A table is retained only when at least one of these conditions is true:

- it is a current aggregate/root with independent lifecycle and concurrency;
- it is an immutable fact needed for retry, replay, callback verification, legal evidence, or audit;
- it is a many-valued child that cannot be represented without row duplication or JSON/EAV;
- it is a security rotation generation whose historical identity is referenced by an execution fact;
- it is a local reliable-execution record required by the approved compensation pattern.

A separate Version/Revision row is retained only when a historical consumer freezes that exact row after a newer
revision can become current. A Hash is retained only when a concrete consumer compares it, verifies it, uses it as a
privacy-preserving key, or commits it into the audit integrity chain. Hashes are not retained as decoration, as a
substitute for structured columns, or as an unverifiable digest of data that is not stored.

## 4. Exact 57-table topology

### 4.1 Identity, Tenant IAM, API Credential, and business OTP — 18 tables

1. `t_user_account`
2. `t_password_credential`
3. `t_federated_identity`
4. `t_access_tenant`
5. `t_tenant_member`
6. `t_tenant_invitation`
7. `t_authentication_challenge`
8. `t_auth_session`
9. `t_refresh_token_generation`
10. `t_signing_key_metadata`
11. `t_role`
12. `t_permission`
13. `t_tenant_member_role`
14. `t_role_permission`
15. `t_api_credential`
16. `t_api_credential_scope`
17. `t_email_otp_challenge`
18. `t_email_otp_attempt`

`t_tenant_member_role` 明确表达 Tenant Member 获得 Role。`t_role.scope_key` 由 `tenant_scope + tenant_id` 派生，
并与授权表的 `(role_id, role_scope_key)` 复合 FK、Member/Tenant 复合 FK 及
`role_scope_key='PLATFORM' OR role_scope_key=CAST(tenant_id AS CHAR)` CHECK 一起阻止跨 Tenant 错授角色。

Removed from this group:

- `t_oidc_provider`: Google issuer, Client ID, discovery/JWKS endpoints, and secrets are deployment configuration.
- `t_credential_event`: Credential current state, `rotated_from_id`, append-only Audit, and Gateway access logs cover its
  only demonstrated consumers; per-request `USED` rows would be unbounded duplication.
- `t_client_integration_readiness`, `t_integration_readiness_eval`, and `t_integration_readiness_check`: readiness is
  derived synchronously from current Tenant, Credential/Scope, and required Webhook facts.

### 4.2 File and Consumer Document — 12 tables

1. `t_file_upload`
2. `t_file_scan_policy`
3. `t_file_scan_requirement`
4. `t_file_object`
5. `t_file_owner_ref`
6. `t_file_scan_result`
7. `t_document_template`
8. `t_document_template_translation`
9. `t_consumer_document`
10. `t_document_version`
11. `t_document_delivery`
12. `t_document_access_grant`

Structural mergers:

- `file_scan_policy + file_scan_policy_ver -> file_scan_policy`;
- `document_template + document_template_ver -> document_template`;
- `document_generation -> consumer_document`;
- `document_render_job -> document_version`.

Removed generic histories:

- `t_file_state_change`;
- `t_doc_generation_state_change`;
- `t_document_state_change`.

Scan Result, Document Version/Correction, Delivery, and Audit remain the concrete explanation of lifecycle changes.

### 4.3 Notification and Email — 9 tables

1. `t_notification_provider`
2. `t_notification_request`
3. `t_notification_delivery`
4. `t_notification_attempt`
5. `t_notification_route`
6. `t_notification_provider_callback`
7. `t_notification_suppression`
8. `t_notification_template`
9. `t_notify_template_translation`

Structural mergers:

- `notification_provider + notification_provider_ver -> notification_provider`;
- `notification_template + notification_template_ver -> notification_template`;
- `notification_recipient + notification_message + notification_delivery -> notification_delivery`.

`t_notify_delivery_state_change` is removed. Attempt, Callback, Suppression, current Delivery state, and Audit are the
retained consumers.

### 4.4 Client Webhook — 8 tables

1. `t_webhook_endpoint`
2. `t_webhook_endpoint_revision`
3. `t_webhook_endpoint_verification`
4. `t_webhook_signing_secret`
5. `t_webhook_subscription`
6. `t_webhook_event`
7. `t_webhook_delivery`
8. `t_webhook_attempt`

Renames with explicit business meaning:

- `webhook_endpoint_ver -> webhook_endpoint_revision`;
- `webhook_secret_ver -> webhook_signing_secret`.

Removed workflow/history tables:

- `t_webhook_sub_state_change`;
- `t_webhook_replay`;
- `t_webhook_delivery_state_change`.

A Replay directly creates a new Delivery with `replay_of_delivery_id`, `generation_no`, `requested_by`, and
`replay_reason_code` in one transaction.

### 4.5 Audit and Security — 8 tables

1. `t_audit_chain`
2. `t_audit_event`
3. `t_audit_event_attr`
4. `t_audit_integrity_point`
5. `t_security_event`
6. `t_security_alert`
7. `t_security_alert_event`
8. `t_security_alert_action`

`t_audit_archive_batch` is removed. Signed Integrity Points preserve tamper-evident checkpoints. Infrastructure backup
and object-retention policy do not require a speculative business table; a jurisdiction-specific WORM design requires
future legal/compliance approval.

### 4.6 Reliable technical execution — 2 tables

1. `t_compensation_event`
2. `t_command_idempotency`

### 4.7 Removed from the 57-table predecessor — 19 tables

The 57-table predecessor removed these 11 legacy Gateway Policy/Release structures:

- `t_gateway_policy`
- `t_gateway_policy_ver`
- `t_gateway_guard_rule` (the legacy Policy-bound shape; the successor reuses the name only for a minimal Route child)
- `t_gateway_policy_binding`
- `t_gateway_policy_binding_ver`
- `t_gateway_selector`
- `t_gateway_release`
- `t_gateway_release_item`
- `t_gateway_release_approval`
- `t_gateway_release_state_change`
- `t_gateway_instance_ack`

All 8 generic technical-configuration tables are removed:

- `t_config_namespace`
- `t_config_item`
- `t_config_version`
- `t_config_change_set`
- `t_config_change_item`
- `t_config_approval`
- `t_config_release`
- `t_config_release_state_change`

That historical 57-table model had six Diagram Groups. The current 63-table successor has seven Diagram Groups and
still has no table whose logical or physical name ends in `_ver`.

## 5. Revision and optimistic-lock policy

### 5.1 Single-table revisions

`file_scan_policy`, `document_template`, `notification_provider`, and `notification_template` store one row per
revision. Each contains its stable business code, `revision_no`, lifecycle status, and nullable `active_slot`.

- `(business identity, revision_no)` is unique.
- `(business identity, active_slot)` is unique; ACTIVE uses `active_slot=1`, all other states use NULL.
- Revision lifecycle is `DRAFT -> ACTIVE -> RETIRED`.
- DRAFT content may change with optimistic locking. ACTIVE and RETIRED content is immutable.
- Activation locks the rows for one business identity and atomically retires the old ACTIVE row and activates the new
  row.
- Consumers freeze the exact revision row ID; they never resolve the current row during retry, replay, rendering, or
  callback verification.

`notification_route` and `webhook_subscription` already represent one row per revision and follow the same rule with
`revision_no + active_slot`; their derived Content Hash fields are removed.

### 5.2 Necessary historical structures

- `document_version` is a legal/business artifact version. It stores the exact Template/Translation, renderer version,
  rendered File, supersedes relation, lifecycle, Lease/Fencing, attempt count, and error code. A nullable `active_slot`
  replaces `consumer_document.current_ver_id`.
- `webhook_endpoint_revision` is necessary because a URL change requires a new Vault binding and fresh DNS, SSRF, and
  Challenge verification while historical Deliveries retain the exact destination revision.
- `webhook_signing_secret` is necessary because rotation is independent from URL verification and each Attempt must
  freeze the exact signing secret generation used.

### 5.3 Ordinary version fields

The generic `version` field exists only on mutable current-state, Lease, or DRAFT-editable rows with a real concurrent
writer. Immutable Event, Attempt, Callback, Check, Result, relation, and history rows do not receive a mechanical
`version`. `source_version`, `revision_no`, Refresh `generation_no`, Document `document_version_no`, and schema versions
are business protocol values, not optimistic-lock fields.

## 6. Exact Hash allowlist

The final model contains exactly these 36 fields whose names end in `_hash`:

### 6.1 Identity and OTP — 6

- `access_tenant.source_payload_hash`
- `authentication_challenge.active_scope_hash`
- `email_otp_challenge.subject_ref_hash`
- `email_otp_challenge.context_ref_hash`
- `email_otp_attempt.ip_hash`
- `email_otp_attempt.device_hash`

### 6.2 File and Consumer Document — 6

- `file_upload.upload_token_hash`
- `file_object.content_hash`
- `consumer_document.commerce_source_payload_hash`
- `consumer_document.fiscal_source_payload_hash`
- `consumer_document.consumer_ref_hash`
- `document_access_grant.consumer_ref_hash`

### 6.3 Notification — 3

- `notification_request.source_payload_hash`
- `notification_attempt.request_hash`
- `notification_provider_callback.payload_hash`

### 6.4 Webhook — 5

- `webhook_endpoint_verification.challenge_hash`
- `webhook_endpoint_verification.resolved_ip_hash`
- `webhook_event.source_payload_hash`
- `webhook_event.payload_hash`
- `webhook_attempt.request_hash`

### 6.5 Audit and Security — 14

- `audit_chain.previous_partition_hash`
- `audit_chain.last_event_hash`
- `audit_event.previous_event_hash`
- `audit_event.attribute_root_hash`
- `audit_event.event_hash`
- `audit_event.source_payload_hash`
- `audit_event.payload_hash`
- `audit_event_attr.value_hash`
- `audit_integrity_point.start_event_hash`
- `audit_integrity_point.end_event_hash`
- `audit_integrity_point.root_hash`
- `audit_integrity_point.content_hash`
- `security_event.source_payload_hash`
- `security_event.event_hash`

### 6.6 Reliable execution — 2

- `compensation_event.payload_hash`
- `command_idempotency.request_hash`

The following security values remain but are intentionally not generic `*_hash` fields:

- `password_credential.password_verifier_phc`;
- `api_credential.key_verifier_phc`;
- Authentication Challenge, Refresh Token, and Email OTP keyed HMAC values and their KMS key versions;
- Notification Address Blind Index and key version;
- `consumer_document.document_generation_key`;
- Webhook signing-secret fingerprint.

Every other current `*_hash` field is removed. In particular, no Scope Set, Reason Set, Requirement Config, Evaluation
Input, Check Evidence, Policy/Template/Provider/Route/Endpoint/Subscription Content, MIME Set, Accepted Result Set,
Variable Schema, Translation, Generation Scope, Source Set, Recipient Set, Route Snapshot, Response, Signature Header,
Approval, Release, Selector, Item, Archive Batch, or Command Response Hash remains.

## 7. Tenant, API Credential, Webhook, and document ownership

### 7.1 Global Account and Tenant authorization

- `user_account` remains the global human login identity inside one fixed Realm.
- OPERATIONS and CUSTOMER Realms remain isolated. Client and Partner share CUSTOMER Accounts.
- Authorization enters a Client or Partner context only through `tenant_member`.
- Roles and Permissions do not replace owning-system resource authorization.
- `login_name` is reserved for the one OPERATIONS Bootstrap Account and is exactly `admin`; every HUMAN row keeps it
  NULL and uses the Realm-unique stable Email Vault Token instead.
- Session `(account_id, realm)` must reference the same Account. A Tenant Session
  `(tenant_member_id, tenant_id, account_id)` must reference one exact Member identity, and an accepted Invitation uses
  the same Member/Tenant/Account tuple. These are composite-FK contracts, not application-only checks.

### 7.2 Access Tenant identity

`access_tenant` retains `tenant_type = CLIENT | PARTNER | INTERNAL` and `owner_ref_id`. The duplicate
`owner_ref_type` field is removed because the closed `tenant_type` already provides the external ID namespace:

```text
CLIENT   -> CCT Client ID
PARTNER  -> CCT Local Partner ID
INTERNAL -> Platform-owned internal ID
```

`(tenant_type, owner_ref_id)` is unique. CCT-owned CLIENT/PARTNER projections retain monotonic `source_version` and
`source_payload_hash`; INTERNAL rows have both NULL. `owner_status` and Platform `security_status` remain independent.

### 7.3 API Credential

- `api_credential` stores the owning CLIENT `tenant_id` and removes duplicate `client_id`.
- Only an ACTIVE CLIENT Tenant may create or use a Credential in the current product scope.
- `key_prefix` locates the bounded candidate row; `key_verifier_phc` performs the irreversible secret verification.
- `api_credential_scope` rows are the sole authorization source. No Scope Hash is stored.
- PLT-I03 validation returns `tenant_id + tenant_type=CLIENT + owner_ref_id + credential_id + effective Scope rows`.
  The Credential must join that same ACTIVE CLIENT Tenant and only its own effective ACTIVE Scope rows.
  `owner_ref_id` is read from the verified `access_tenant` row; it is never inferred from Key Prefix or accepted from,
  or overridden by, caller input.

### 7.4 Tenant-owned Platform roots

API Credential, Webhook Endpoint/Event/Delivery, Consumer Document, tenant Notification routes/requests/deliveries,
and tenant File uploads use local `tenant_id`. They do not repeat external `client_id`. Child rows repeat `tenant_id`
only when a composite FK is needed to prove that two independently referenced parents belong to the same Tenant or
when the row is a direct Tenant query root.

### 7.5 Onboarding file ownership

CCT-I15 `START_OR_RESUME` creates or reuses the PENDING business owner and DRAFT Application, freezes
`requested_by_account_id`, and only then publishes PLT-I17 with exact
`tenant_type + owner_ref_id + requested_by_account_id + source_version + payload_hash`. Same version/same Hash is
idempotent, same version/different Hash conflicts, and an older version is rejected.

The resulting PENDING Tenant does not receive an ordinary Tenant Context Token. For onboarding uploads, PLT-I04 accepts
the authenticated Account Context through the Client/Partner BFF and resolves exactly one PENDING Tenant whose ACTIVE
Member has that `requested_by_account_id` and `TENANT_ONBOARDING_OWNER`; Platform writes the resolved local `tenant_id`,
never a caller-provided Tenant ID. Zero or multiple candidates fail closed. CCT-I15 `SUBMIT` validates and freezes the
controlled File IDs before it registers the Risk handler. Approval revokes `TENANT_ONBOARDING_OWNER` and grants
`TENANT_OWNER`.

## 8. Derived Integration Readiness

PLT-I13 becomes a synchronous derived query. It persists no Readiness root, Evaluation, Check, Version, Hash, or event.
For the requested CLIENT Tenant, Platform evaluates the current policy encoded in application code against current
local facts:

- Tenant owner and security status are ACTIVE;
- at least one unexpired ACTIVE API Credential exists;
- the required effective Credential Scope rows exist;
- when the fixed integration mode requires Webhook delivery, one VERIFIED/ACTIVE Endpoint Revision and matching ACTIVE
  Subscription exist.

The successful business response contains only:

```text
status      = READY | NOT_READY | RESTRICTED
checked_at  = current UTC time
reason_code = present only when status != READY
```

Only CLIENT Tenant readiness exists in the current product. A missing required Credential/Scope/Webhook fact yields
`NOT_READY`; an Owner or Platform security restriction yields `RESTRICTED`. Ambiguous or inconsistent rows, timeout,
database error, or another technical dependency failure returns an interface error and does not return a business
readiness status. CCT fails closed for every non-READY response or technical failure and continues to call this
interface at each CCT-I10/I13 use point. `ClientIntegrationReadinessChanged` is removed because no consumer subscribes
to it.

## 9. Gateway rate and concurrency boundary — superseded addendum

The 57-table predecessor intentionally kept every Route and Guard fact in Gateway code. The approved successor now
stores only the six-table current public control plane: Target, Route, Header allowlist, Query allowlist, Route Guard
Rule and Console Resource. It covers `OPERATIONS`、`CLIENT`、`PARTNER`、`CHECKOUT`、`CLIENT_API` and
`PROVIDER_CALLBACK`; internal service calls still bypass the public Gateway.

Platform stores global Route Guard parameters for `SHARED_ROUTE`、`CLIENT_IP`、`AUTHENTICATED_PRINCIPAL` and
`TENANT` dimensions. It does not store Bucket, Counter, Lease, TTL, Redis Key, Runtime Metric, Snapshot, Release,
Last-Known-Good or Instance Ack. Gateway continues to own the static implementation and absolute safety ceilings for
Security Profiles, trusted Headers, cookies, identity extraction and Redis algorithms. A `TENANT` Guard uses only the
trusted `tenant_id` returned by validated Human Context or PLT-I03 Credential validation; request Header, Query or Path
values cannot choose the Tenant partition. For the existing Operations implementation, `CLIENT_IP` uses the raw canonical socket IP
and `AUTHENTICATED_PRINCIPAL` uses the exact validated JWT `sub`, with no Redis HMAC; only Access Log IP and Subject values
use their independent HMAC pseudonymization. No per-Tenant override or quota table exists.

## 10. Critical merged lifecycles

### 10.1 Consumer Document

The first `ConsumerDocumentRequested` or `FiscalRecordCreated` message creates `consumer_document` under unique
`generation_key_version + document_generation_key`. The row contains the explicit Tenant/Order/business reference,
document type, Commerce slot, Fiscal slot, correction target, and lifecycle status.

- Same Event Key/Version/Payload Hash is idempotent.
- Same identity with a different Version or Payload Hash is a conflict and never overwrites the accepted slot.
- Commerce Legal File IDs are verified against local `file_object.content_hash` at ingress; duplicate content-hash
  columns are not stored on the Document.
- A Credit Note correction uses the complete
  `(corrects_document_id, tenant_id, order_id, consumer_ref_hash)` ownership tuple.
- When both slots agree on the explicit business identity, Platform creates `document_version`.
- A new render creates a new Version and leaves the old File immutable.
- A failed new render does not displace the last ACTIVE generated Version.
- Document Delivery binds `(document_ver_id, document_id)`, `(document_id, tenant_id)`, and
  `(notification_delivery_id, tenant_id)`. Document Access Grant binds the Version/Document tuple plus the complete
  Document/Tenant/Order/Consumer tuple; neither record may join parents that merely have individually valid IDs.

### 10.2 Notification

`notification_request` is unique by `source_system + source_event_key + notification_type`. Same identity/different
Source Payload Hash is a conflict; a Recipient Set Hash cannot create a second Request.

Each `notification_delivery` row is one recipient and contains the recipient Vault/Blind/Masked tuple, locale,
translation, message Vault token and safe previews, selected exact Route, Lease/Fencing, and current status. Attempts and
verified callbacks remain append-only. Delivery references Request through
`(request_id, tenant_id, notification_template_id)` so it cannot silently substitute another Template. Provider response
bodies are not persisted, so Response Hash is removed.

### 10.3 Webhook

URL changes create and verify a new Endpoint Revision. Signing Secret rotation is independent. Event stores exact source
and outbound-payload hashes. Delivery explicitly freezes `event_type`; its Event and Subscription composite FKs both
include Event Type and payload schema, in addition to Tenant/Endpoint ownership. A Delivery therefore cannot bind an
Event to a Subscription for another type. Attempt freezes the exact signing secret and request hash.

A replay atomically creates a new Delivery with `replay_of_delivery_id` and the next `generation_no`; the original
Delivery and Attempts never change. There is no intermediate Replay workflow row.

## 11. Concurrency and failure semantics

- Same idempotency key and same request hash returns the prior `response_ref`; different request hash is a conflict.
- `command_idempotency.response_hash` is removed because a stable response reference, not an unrecoverable digest,
  supports replay.
- Non-ACTIVE Tenant, Member, Credential, Scope, Revision, Endpoint, Subscription, or Secret fails closed.
- Old Fencing Tokens cannot create an Attempt or advance a Delivery/Document Version.
- Notification/Webhook unknown external outcomes remain explicit `UNKNOWN` or retryable Attempt results.
- Necessary CCT, Vault, KMS, Provider, object-store, or local authority failure does not silently use a stale success.
- Document single-slot arrival remains waiting; local `compensation_event` only wakes owned work and does not create a
  generic workflow/history table.
- Cross-Tenant parent combinations are rejected by composite FKs where structurally expressible and by a fixed Root
  lock/order plus conditional updates for cross-row lifecycle invariants.
- Tenant Switch creates a new fixed-context Session, revokes the old Refresh Family, signs a token that does not reuse
  the old Session, and makes the BFF replace the channel-specific `__Secure-*-refresh` Cookie.

## 12. Canonical requirements changes

The first four bullets below record the 57-table simplification. Its final two Gateway bullets are replaced by the
successor rules that follow.

- Remove PLT-09 `管理通用配置生命周期`.
- Remove Technical Configuration from Platform source-of-truth and internal-domain lists.
- Remove `ConfigurationActivated` and `ClientIntegrationReadinessChanged` from Platform publications.
- Rewrite PLT-I13 as the derived synchronous query in Section 8.
- Preserve PLT-I03 API Credential validation and make CLIENT Tenant the local ownership boundary.
- Superseding rule: Platform owns the current six-table public Route/Guard/Console configuration; Gateway consumes a
  validated global Snapshot while retaining static Approved Target Registry, security algorithms and absolute limits.
  Redis retains runtime limiter state. No legacy Policy Version, Release, Snapshot table, Last-Known-Good table,
  Instance Ack, Tenant Guard Override or dynamic per-Tenant quota is restored.
- Preserve unified Operations/CUSTOMER authentication, Tenant membership, Google login, CCT onboarding, and Risk typed
  Application namespace contracts.

## 13. Automated and manual acceptance

Automated acceptance must prove:

The following list is the historical 57-table acceptance record. Current successor acceptance additionally proves
`63/822/123/99/107/5/7`, exactly 31 composite FKs, 85 named CHECK contracts, exactly six current control-plane tables,
all six public Channels, the Profile/Authorization compatibility matrix, Permission-versus-Scope binding, Target
Registry double validation and trusted Tenant Redis partitioning, while retaining the same 36-field Hash allowlist.

- exactly 57 tables in group counts `18 + 12 + 9 + 8 + 8 + 2`;
- exactly six Diagram Groups and complete one-group-per-table coverage;
- no logical or physical table ending `_ver`;
- none of the 19 Gateway/Config tables or five removed Readiness/IdP/Credential tables;
- exactly the 36-field Hash allowlist in Section 6;
- no repeated `client_id` in Platform tenant-owned rows;
- API Credential Scope rows are authoritative and the PHC verifier is present;
- revision active-slot uniqueness, immutable historical references, Tenant composite-FK ownership, and Lease/Fencing
  negative cases;
- deterministic model generation and scenario coverage of every retained table;
- exactly 74 named CHECK contracts whose expressions and table bindings are frozen in
  `scripts/pdmaner/platform-support/model-policy.mjs` and independently digested by the Oracle; PDManer 4.9.3 has no
  portable CHECK node, so a future Migration must map each contract to executable DDL;
- no stale 97-table statistics, then-unapproved dynamic Gateway wording, generic Technical Configuration authority,
  persisted Readiness evidence, removed names, or obsolete Hash contracts;
- all six generators, full PDManer/scenario tests, requirements build/tests/lint, Markdown links, and `git diff --check`.

PDManer 4.9.3 in-app opening, diagram inspection, and built-in model validation remain a manual gate. The same review
must inspect the sidecar CHECK names/expressions/table bindings and their future Migration mapping; it must not claim
that the PDManer JSON contains CHECK nodes. Exact final field, index, relation, one-to-one, composite-FK, and CHECK
contract totals are generated evidence and must not be guessed in advance.

## 14. Implementation boundary and integration discipline

The 57-table implementation occurred in its recorded isolation workspace. The current successor is implemented with
tests-first in the isolated `codex/platform-api-tenant-model` worktree. No commit, branch rewrite, push, Java/migration
creation or deployment occurs without separate user authorization.

Shared requirements and navigation are semantically merged from the latest main workspace. Existing Gateway, Risk,
Finance, CCT, and unrelated dirty files are never replaced wholesale. Before main-workspace synchronization, notify the
relevant task owners of the exact shared-file boundary; after synchronization, request a read-only total gate.
