# Vault Minimal Database Design

Date: 2026-09-02

Status: approved for the first PDManer physical model

This design is the current authority for the Code-MoR Vault V1 API boundary, caller-identification assumption,
token semantics, database topology, cryptographic persistence, idempotency and local access audit. It supersedes the
corresponding API, mTLS/Workload Identity, Binding, Record lifecycle, table topology, command-idempotency and audit
sections of `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`. That older document remains authoritative only for the
unchanged system boundary, non-card-data classification, prohibited card data, KMS/Secrets separation and the rule
that Vault never owns business meaning.

Application transport follows the later `2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`:
Vault uses deployment-restricted private HTTP, owns no X.509 application certificate and remains unreachable through
Gateway/BFF/browser/external Client paths. This transport supersession does not strengthen the weak V1 caller model.

## 1. Purpose

Build the smallest Vault database that supports the current demonstrated consumers:

- protect an approved recoverable non-card sensitive value and return an opaque Token;
- reveal the value by Token for an approved internal purpose;
- return one shared Token for equal canonical values when the Data Type uses `STABLE` mode;
- return the original Token when the same Protect command is retried;
- record every Protect and Reveal attempt without copying protected material into the audit trail.

Vault is supporting security infrastructure, not a seventh core business system. It stores a protected value and a
generic `data_type`; it does not create Email, Phone, Consumer, Customer, Order, Tenant, Network Evidence or other
business-specific tables.

## 2. Scope

### 2.1 In scope

- one independent `vault-service` database-design project;
- synchronous internal Protect and Reveal operations;
- `REFERENCE` and `STABLE` token modes;
- per-record envelope encryption;
- Protect request idempotency;
- immutable local access audit;
- one MySQL 8.4 database and one local transaction boundary;
- deterministic PDManer 4.9.3 generation and automated structural validation.

### 2.2 Explicitly out of scope

- Java/Maven modules, HTTP implementation, Flyway migrations or a physical database;
- Gateway, BFF, browser or external Client access;
- Binding, Bind, Confirm, Copy, Retire, Destroy, Query, Batch, Manage or Rewrap APIs;
- Tenant, Owner Resource, Field, Consumer or business aggregate ownership inside Vault;
- mTLS, Workload Identity or a database-backed service credential in this V1;
- application-level request/response encryption envelopes or application transport certificates;
- Blind Index APIs, masked values, metadata hash, record version, key-version tables or algorithm tables;
- Inbox/Outbox, `compensation_event`, Platform Audit relay, audit hash chains or WORM/archive workflows;
- automatic retention, Legal Hold, anonymization, archive or destruction;
- database/table sharding or sharding-reservation fields;
- PAN, CVV/CVC, full track data, card expiry, PIN or PIN Block.

## 3. Trust and access boundary

Vault is reachable only through deployment-restricted private service networking over HTTP and owns no X.509
application certificate. No Gateway, BFF, browser, external Client or business service may access the Vault database
directly.

V1 temporarily obtains `caller_service` from the required `X-Caller-Service` header and evaluates a static
`caller_service + action + data_type + purpose` allowlist. This is best-effort prevention of accidental misuse, not
strong service authentication, because an internal caller can spoof the header. The accepted V1 trust assumption is:
any internal service allowed for a Data Type and Purpose may Reveal any Token it obtains. A future strong workload
identity can be designed later without changing the three-table model; the current private network and Caller Header
must not be described as application authentication.

The allowlist is deployment configuration, not a database table. Missing policy denies the request. Policy failure,
KMS failure, integrity failure or local audit persistence failure must never return plaintext.

## 4. API boundary

2026-09-04 approved URL simplification: use `/vault` as the system prefix and ordinary slash-separated capability
paths. Former paths and colon action notation have no alias or redirect and return `404`. This changes only the HTTP
path contract, not private-network access, DTOs, idempotency, encryption or tables.
Client and Server must be updated together.

### 4.1 Protect

`POST /vault/v1/records/protect`

Required request context:

- `X-Caller-Service`: internal caller name;
- `Idempotency-Key`: caller-generated identifier for one logical Protect command;
- `X-Request-Id`: identifier for this HTTP attempt; Vault generates one if absent;
- optional trace context.

Request body:

```text
dataType   String
tokenMode  REFERENCE | STABLE
value      canonical byte/string value
purpose    String
```

Response body:

```text
token      opaque Vault Token
```

The caller generates a fresh Idempotency Key for a new logical command and reuses it only when retrying that command.
The key does not determine Stable equality and does not need to match keys used by other services.

### 4.2 Reveal

`POST /vault/v1/records/reveal`

Request body:

```text
token      opaque Vault Token
purpose    String
```

Response body:

```text
value      original canonical value
```

Reveal has no persisted idempotency record. Every actual attempt rechecks policy, decrypts when allowed and appends a
new Access Event. Token must be carried in the request body and must not appear in a URL, query parameter, ordinary
log, trace attribute, metric label or exception message.

## 5. Token semantics

Token is a case-sensitive, non-sequential opaque identifier generated with a cryptographically secure random source.
It is a database locator, not an authorization credential. `VARCHAR(64)` uses `ascii` with binary collation.

### 5.1 REFERENCE

- A new logical Protect command creates a new Record and a new random Token even when the canonical value already
  exists elsewhere.
- An exact retry using the same caller and Idempotency Key returns the original Token.

### 5.2 STABLE

- Equal `(data_type, canonical value bytes)` within one deployed Vault environment return the same existing Token to
  every caller.
- Caller, purpose and Idempotency Key do not participate in Stable identity.
- Different Data Types may return different Tokens for identical bytes.
- Token remains unchanged when only the record-encryption KMS wrapping key changes in a future design.

The caller owns semantic normalization. Vault compares exact canonical bytes and does not implement Email-, Phone- or
business-specific normalization. A Data Type that requires global stability must be statically fixed to `STABLE` so
callers cannot select `REFERENCE` for it.

## 6. Stable fingerprint

Vault computes `value_fingerprint` internally with HMAC-SHA-256 under an environment-isolated
`stable-identity-key` held by KMS or Secrets Manager. The caller never supplies the fingerprint.

The canonical HMAC input is length-delimited and domain-separated:

```text
UTF8("vault-stable-v1")
+ UINT16_BE(length(UTF8(data_type))) + UTF8(data_type)
+ UINT32_BE(length(canonical_value_bytes)) + canonical_value_bytes
```

Environment is excluded because deployment and key material already isolate environments. Caller, purpose,
Idempotency Key and transport metadata are excluded so Stable identity is global inside that Vault deployment.

Raw SHA-256 is forbidden for low-entropy values. The stable identity key is not routinely rotated in V1; rotating it
would require a separate migration design because it defines Record identity. This is independent from rotating or
rewrapping the KMS key used for record encryption.

## 7. Cryptographic persistence

Each Record uses envelope encryption:

1. Generate an independent random 256-bit DEK.
2. Generate an independent random 12-byte AES-GCM nonce.
3. Encrypt the canonical value with AES-256-GCM; store ciphertext and authentication tag together in `ciphertext`.
4. Wrap the DEK with the configured KMS key and store only `wrapped_dek` plus `kms_key_ref`.
5. Reconstruct AAD exactly from `record_id + token + data_type + token_mode` using one fixed length-delimited encoding.

Algorithm and encoding are fixed V1 implementation contracts, so no algorithm/version columns or key tables are
persisted. Encryption keys, HMAC keys, plaintext DEKs and credentials never enter PDManer, configuration files, logs,
events or tests. The Protect input is limited to 32 KiB of canonical bytes, keeping ciphertext within MySQL `BLOB`.

## 8. Exact three-table topology

### 8.1 `t_vault_record`

Purpose: authoritative Token, Stable identity and encrypted-value storage.

| Field | MySQL type | Null | Meaning |
|---|---|---:|---|
| `record_id` | `BIGINT` | no | Snowflake primary key |
| `token` | `VARCHAR(64)` | no | case-sensitive opaque Token |
| `data_type` | `VARCHAR(64)` | no | generic registered Data Type code |
| `token_mode` | `VARCHAR(16)` | no | `REFERENCE` or `STABLE` |
| `value_fingerprint` | `BINARY(32)` | yes | server HMAC fingerprint; only for `STABLE` |
| `ciphertext` | `BLOB` | no | AES-GCM ciphertext with authentication tag |
| `encryption_nonce` | `BINARY(12)` | no | per-record AES-GCM nonce |
| `wrapped_dek` | `BLOB` | no | KMS-wrapped per-record DEK |
| `kms_key_ref` | `VARCHAR(512)` | no | non-secret KMS key reference |
| `create_time` | `DATETIME(6)` | no | UTC creation time |
| `update_time` | `DATETIME(6)` | no | UTC last persistence update time |

Indexes:

```text
PRIMARY KEY (record_id)
UNIQUE uk_vault_record_token (token)
UNIQUE uk_vault_record_stable (data_type, value_fingerprint)
```

CHECK contracts:

```sql
token_mode IN ('REFERENCE', 'STABLE')

(token_mode = 'STABLE' AND value_fingerprint IS NOT NULL)
OR
(token_mode = 'REFERENCE' AND value_fingerprint IS NULL)
```

MySQL permits multiple `NULL` values in a unique index, so REFERENCE rows do not conflict with one another.

### 8.2 `t_vault_protect_request`

Purpose: persist the successful result of one caller Protect command so a lost response can be replayed safely.

| Field | MySQL type | Null | Meaning |
|---|---|---:|---|
| `protect_request_id` | `BIGINT` | no | Snowflake primary key |
| `caller_service` | `VARCHAR(128)` | no | caller identity used for the idempotency namespace |
| `idempotency_key` | `VARCHAR(128)` | no | caller-generated logical command key |
| `request_hash` | `BINARY(32)` | no | server-keyed HMAC over canonical request content |
| `record_id` | `BIGINT` | no | local relation to the returned Record |
| `create_time` | `DATETIME(6)` | no | UTC creation time |
| `update_time` | `DATETIME(6)` | no | UTC last persistence update time |

Indexes and relation:

```text
PRIMARY KEY (protect_request_id)
UNIQUE uk_vault_protect_request (caller_service, idempotency_key)
INDEX idx_vault_protect_record (record_id)
FOREIGN KEY record_id -> t_vault_record.record_id
```

Only successful Protect results are retained. The table has no operation type, status, response copy, failure,
expiry, retry, lease or cleanup fields.

`request_hash` is HMAC-SHA-256 under a separate request-hash key over a length-delimited encoding of:

```text
caller_service + data_type + token_mode + canonical_value_bytes + purpose
```

Request ID, trace context, HTTP header ordering and other per-attempt transport data are excluded.

### 8.3 `t_vault_access_event`

Purpose: immutable local evidence for every actual Protect and Reveal attempt.

| Field | MySQL type | Null | Meaning |
|---|---|---:|---|
| `access_event_id` | `BIGINT` | no | Snowflake primary key |
| `request_id` | `VARCHAR(64)` | no | this HTTP attempt identifier |
| `trace_id` | `VARCHAR(64)` | yes | distributed trace identifier |
| `caller_service` | `VARCHAR(128)` | no | asserted internal caller; `UNKNOWN` when unavailable |
| `action` | `VARCHAR(16)` | no | `PROTECT` or `REVEAL` |
| `data_type` | `VARCHAR(64)` | yes | resolved/requested Data Type when available |
| `purpose` | `VARCHAR(64)` | yes | requested purpose when available |
| `record_id` | `BIGINT` | yes | resolved local Record; null on early failure or unknown Token |
| `outcome` | `VARCHAR(16)` | no | `SUCCESS`, `DENIED` or `FAILED` |
| `result_code` | `VARCHAR(64)` | no | stable non-sensitive result code |
| `policy_version` | `VARCHAR(64)` | yes | matched static policy version when available |
| `create_time` | `DATETIME(6)` | no | immutable UTC occurrence time |

Indexes and relation:

```text
PRIMARY KEY (access_event_id)
INDEX idx_vault_access_record (record_id, create_time)
INDEX idx_vault_access_caller (caller_service, create_time)
INDEX idx_vault_access_request (request_id)
FOREIGN KEY record_id -> t_vault_record.record_id
```

CHECK contracts:

```sql
action IN ('PROTECT', 'REVEAL')
outcome IN ('SUCCESS', 'DENIED', 'FAILED')
```

The event is append-only and therefore has no `update_time`. It never stores Token, raw value, fingerprint,
request hash, ciphertext, nonce, wrapped DEK, KMS material, response body, free-text exception or stack trace.

## 9. Protect transaction and concurrency

### 9.1 Idempotency decision

Query by `(caller_service, idempotency_key)`:

- no row: execute Protect;
- same `request_hash`: append a new success Access Event and return the existing Record Token;
- different `request_hash`: append a denied Access Event and return `IDEMPOTENCY_KEY_CONFLICT`.

### 9.2 New REFERENCE command

Within one local transaction:

1. generate Record ID, random Token, DEK and nonce;
2. encrypt and insert `t_vault_record` with null `value_fingerprint`;
3. insert `t_vault_protect_request`;
4. insert the successful `t_vault_access_event`;
5. commit before returning Token.

Concurrent calls using the same idempotency key may both reach Record creation. The unique request index selects one
winner; the losing transaction must roll back its entire Record/Event work, then read and return the committed winner
in a new transaction after verifying the request hash.

### 9.3 New STABLE command

Within one local transaction:

1. compute `value_fingerprint`;
2. insert the Record or read the existing Record selected by
   `uk_vault_record_stable(data_type, value_fingerprint)`;
3. insert the caller-specific `t_vault_protect_request` pointing to that Record;
4. insert the successful Access Event;
5. commit before returning Token.

Concurrent callers with different Idempotency Keys but the same stable value converge on one Record through the
Stable unique index. A duplicate-key outcome is an expected concurrency path: reload the winner and continue; do not
return a business failure. Different services retain independent Protect Request rows pointing to the same Record.

## 10. Reveal flow and failure semantics

Reveal executes in this order:

1. validate request and static caller/action/Data Type/Purpose policy;
2. find Record by Token;
3. unwrap the DEK with KMS;
4. reconstruct AAD and authenticate/decrypt AES-GCM;
5. insert and commit a successful Access Event;
6. return plaintext only after the audit commit succeeds.

Unknown Token, policy denial, KMS failure, malformed cryptographic material and authentication-tag failure return no
plaintext. Denied and failed attempts are recorded in an independent short transaction when the main operation has no
successful transaction to commit. If local audit persistence is unavailable, Reveal fails closed.

## 11. Retention and lifecycle

V1 has no Retire or Destroy capability. Records, successful idempotency rows and Access Events are preserved by
default and are not automatically deleted. The design does not claim Right-to-Erasure, automated retention or legal
hold compliance. Before real regulated production data is processed, a separate legally reviewed design must cover
classification, jurisdictional retention, Legal Hold, backups, stable shared-token reference tracking and controlled
destruction. That future design may require additional tables and APIs and is not implied by this model.

## 12. PDManer physical baseline

- Model name: `Code-MoR Vault Database V1`.
- Exactly 3 tables and 30 fields.
- Exactly 3 non-primary unique indexes and 4 access indexes.
- Exactly 2 local `n:1` relations.
- Exactly 1 Diagram Group containing all three tables.
- Every primary key is a non-auto-increment Snowflake `BIGINT`.
- Tables use `t_`; unique indexes use `uk_`; access indexes use `idx_`; physical identifiers do not exceed 32
  characters.
- Time fields use UTC `DATETIME(6)`.
- Token, Data Type and other machine identifiers use binary comparison where specified.
- PDManer contains no database connection, hostname, port, username, password, environment name or real KMS key.
- CHECK expressions are sidecar contracts because the PDManer 4.9.3 JSON schema has no portable CHECK node.
- The generated model is automatically validated, but opening it in PDManer 4.9.3 and checking the Diagram, field
  order, indexes, relations, comments and DDL remains a manual gate.

## 13. Acceptance examples

### 13.1 Global Stable Email example

Commerce protects canonical bytes for `alice@example.com` using Data Type `EMAIL_ADDRESS_V1`, Purpose
`CHECKOUT_CONTACT` and Idempotency Key `A`. CCT later protects the same canonical bytes with key `B`. Vault computes
the same fingerprint, both Protect Request rows reference one Record, and both responses contain the same Token.

### 13.2 Reference retry example

Finance protects a beneficiary name in REFERENCE mode. The transaction commits but the HTTP response is lost. Finance
retries with the same caller and Idempotency Key. Vault compares the same request hash and returns the original Token;
it does not create another Record.

### 13.3 Idempotency conflict example

The same caller reuses one Idempotency Key with different canonical bytes, Data Type, mode or purpose. Vault finds a
different request hash, records `DENIED / IDEMPOTENCY_KEY_CONFLICT`, and does not modify the original Record or request
mapping.

### 13.4 Reveal audit failure example

Vault decrypts a Record in memory but cannot commit the Access Event. It discards the plaintext and returns a failure;
no plaintext is exposed without durable local audit evidence.
