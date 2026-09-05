# MoR Compensation Event Design

Date: 2026-08-28

Status: Historical — originally approved for implementation; superseded in the contract areas listed below

> **Historical / superseded on 2026-09-05:** This file preserves the rationale and implementation context of the
> original compensation-event design. The current contract is
> [`COMPENSATION_EVENT_GUIDE.md`](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md), and the remaining six-model
> migration is governed by the
> [2026-09-05 alignment plan](../plans/2026-09-05-compensation-event-six-model-alignment.md). Do not implement or
> restore this file's `execution_type`, source ID/version, Schema version, Canonical Envelope, Payload Hash, 64 KiB
> Payload or Testcontainers requirements. The unchanged transaction, after-commit, Claim/Lease, at-least-once and
> bounded-execution reasoning remains historical input only where it agrees with the current guide.

## 1. Purpose

Create `com.paytech:mor-compensation-event:1.0.0-SNAPSHOT` as an embedded Foundation JAR for reliable,
per-service asynchronous work. The Code-PMX `compensation-event` module is the implementation reference, but the
Code-MoR module must harden its transaction, concurrency, retry, timeout, RabbitMQ, observability and test behavior.

The module is not a central worker, does not own a central database and does not introduce generic Inbox/Outbox
tables. Every consuming service owns its local `t_compensation_event` table and its business facts.

## 2. Goals

- Register a compensation row in the caller's active local business transaction.
- Wake execution only from Spring transaction `afterCommit`.
- Recover a lost immediate wake-up through a scanner that an owning service can call from XXL-Job.
- Claim work atomically with a database Lease and `lock_version` fencing.
- Execute bounded, timed handlers with explicit retryable and terminal outcomes.
- Publish RabbitMQ events as mandatory persistent JSON messages and wait for a Broker Publisher Confirm.
- Preserve at-least-once semantics with the same event key, envelope and payload hash on every retry.
- Retain failed work for alerting and audited manual reopening; delete only confirmed successful work.
- Provide low-cardinality Micrometer metrics and payload-safe logging.
- Prove the production chain with MySQL 8.4 and RabbitMQ 4.2 Testcontainers tests.

## 3. Non-goals

- No central compensation service or cross-service database access.
- No generic consumer Inbox, workflow engine or long-term successful-delivery history.
- No automatic creation of production tables and no Foundation-owned Flyway migration.
- No business event catalog, business DTO, Provider model or service-specific XXL-Job Handler.
- No claim of exactly-once delivery. External side effects remain idempotent and query-recoverable.
- No shared authentication, Gateway or deployment work.

## 4. Source migration policy

The implementation may reuse the following Code-PMX ideas:

- Handler registration with duplicate-key validation.
- A bounded executor and batched retry scanner.
- Stable duplicate registration and successful-row deletion.
- Explicit success, retryable failure and terminal failure results.

The following Code-PMX behavior is replaced rather than preserved:

- `saveEvent` and `asyncExecute` as separately callable operations.
- Auto-increment IDs, `main_type/sub_type`, arbitrary `extension` and the legacy state codes.
- Query-then-execute without Claim/Lease.
- `DiscardPolicy`, unbounded `Future.get()`, `IGNORE` and nullable Handler results.
- Private PayPlus/Beacon/ElasticJob/SkyWalking dependencies.
- Broad component scanning, field injection and a private `SqlSessionFactory`.

No binary compatibility with `com.payplus.basis:compensation-event` is required.

## 5. Module and package boundary

```text
mor-foundation/mor-compensation-event/
├── pom.xml
├── src/main/java/com/paytech/foundation/compensation/
│   ├── api/                 # registration, handler, scan and recovery contracts
│   ├── autoconfigure/       # Spring Boot 4 auto-configuration and properties
│   ├── execution/           # dispatcher, registry, timeout and retry policy execution
│   ├── messaging/           # confirmed RabbitMQ publisher
│   ├── model/               # immutable internal event model and states
│   ├── observability/       # Micrometer and safe logging adapters
│   └── persistence/         # MyBatis mapper/store and fencing transitions
└── src/main/resources/
    ├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── META-INF/mybatis/CompensationEventMapper.xml
```

The module depends on `mor-id-snowflake` for local IDs. Lower-level Foundation modules never depend on the
compensation module. RabbitMQ support is conditionally configured and consumers still declare Spring AMQP when they
use `MQ_PUBLISH`.

## 6. Public contracts

### 6.1 Registration

`CompensationEventRegistrar.register(CompensationEventRequest)` is the only normal creation entry point.

The request contains:

- `eventType`
- `executionType`: `MQ_PUBLISH` or `LOCAL_HANDLER`
- `sourceType`
- string `sourceId`
- positive `sourceVersion`
- positive `schemaVersion`
- JSON `payload`

The registrar:

1. rejects calls without an active Spring transaction;
2. validates a matching Handler or RabbitMQ Route before insertion;
3. generates the Snowflake row ID;
4. derives a deterministic event key from source system/type/id/version/event type;
5. builds and canonicalizes the immutable JSON envelope;
6. writes the row through the host service's MyBatis/transaction infrastructure;
7. treats an identical unique-key collision as an idempotent repeat;
8. rejects a same-key or same-source collision with different immutable content;
9. registers an `afterCommit` wake-up that never makes commit success depend on executor capacity.

Duplicate registration is idempotent only while the local row is retained. Confirmed success deletes the row, so the
host business fact and its versioning must guarantee that the same source tuple is never registered again after
success; this JAR intentionally stores no long-term success history.

The readable event-key form is
`sourceSystem:sourceType:sourceId:sourceVersion:eventType`. If it exceeds the common 128-character physical limit or
the AMQP 255-byte UTF-8 limit, or any component contains the `:` delimiter, the key becomes
`sha256:<lowercase-hex>`. The hash input is the UTF-8 identity tuple encoded as
`v1|<byte-length>:<value>|...`; byte-length prefixes make delimiter-containing and multibyte components unambiguous.
Identity fields reject control characters before a key is created.

### 6.2 Handler contracts

`LocalCompensationEventHandler` declares one unique `eventType`, an execution policy and a synchronous `handle`
method. It receives the stable event key and immutable envelope. It returns exactly one of:

- success;
- retryable failure with a stable error code and safe summary;
- terminal failure with a stable error code and safe summary.

Null and `IGNORE` are invalid. Unexpected exceptions become retryable technical failures whose persisted summary does
not include the exception message or payload.

`RabbitMqEventRoute` declares one unique MQ event type, durable topic exchange, routing key, confirm timeout and
execution policy. An event type is globally unique across `LOCAL_HANDLER` and `MQ_PUBLISH`; same-mechanism duplicates
or a cross-mechanism collision fail application startup. This ensures an accidentally changed persisted
`execution_type` cannot select another configured side-effect mechanism.

### 6.3 Scanner and recovery

`CompensationEventScanner.scan(CompensationScanRequest)` is the service-local seam called by XXL-Job. It accepts
batch size, shard index/count and an optional event-type allowlist. It never crosses a database boundary.

`CompensationEventRecoveryService` is auto-configured only when the host supplies a
`CompensationRecoveryAuditRecorder`. Reopen requires event ID, expected lock version, actor, reason and ticket. The
FAILED-to-ERROR update and audit record share one local transaction; execution count is reset, the previous error is
retained, and wake-up happens after commit. The host owns the authenticated and authorized operations entry point,
FAILED query/operation surface, audit-table migration and audit retention. The library trusts the supplied actor; it
does not authenticate an operator.

## 7. Envelope and immutable hash

The JSON message contains:

```json
{
  "eventKey": "payment:PAYMENT_INTENT:10001:3:PaymentSucceeded",
  "eventType": "PaymentSucceeded",
  "sourceSystem": "payment",
  "sourceType": "PAYMENT_INTENT",
  "sourceId": "10001",
  "sourceVersion": 3,
  "occurredAt": "2026-08-28T10:15:30.123456Z",
  "schemaVersion": 1,
  "payloadHash": "sha256:<64 lowercase hexadecimal characters>",
  "payload": {}
}
```

The hash input is the canonical envelope without `payloadHash`. The database `payload_hash CHAR(64)` stores only the
raw lowercase hexadecimal SHA-256 value; the JSON envelope carries the algorithm prefix. The canonical serialized
envelope must not exceed 65,536 UTF-8 bytes. Object keys are recursively sorted; array order is retained.

Payload JSON numbers are limited to signed 64-bit integers. Money, decimals, scientific values and integers outside
that range use schema-defined canonical strings; otherwise MySQL JSON numeric normalization could change the
serialized hash input. `sourceVersion` and `schemaVersion` are limited to the positive INTEGER range shared by the
approved physical models. New rows start with `lock_version=1`, satisfying the Payment positive-counter constraint.

The approved service models use a MySQL `JSON` column for `payload_json`; Section 8 separately defines the semantic
adoption gate for existing model comments and scenario fixtures. Because MySQL may normalize its binary JSON
representation, the Dispatcher recursively canonicalizes the value read from the database before every Handler or
RabbitMQ attempt. The first attempt and every retry therefore use byte-identical canonical JSON without requiring a
physical-model type change. Before any Handler or MQ side effect, execution also verifies the exact envelope field
set, source system, event/source metadata, well-formed Unicode, positive version bounds, object Payload, embedded Hash
and persisted row Hash. Unpaired UTF-16 surrogates in identity or Payload text are rejected before UTF-8 encoding;
they are never silently replaced. Any mismatch is terminal `ENVELOPE_INTEGRITY_INVALID`; corrupted data is never
published.

This SHA-256 value is an unkeyed consistency check, not an authenticity mechanism. It detects inconsistent row/envelope
metadata and accidental corruption, but it cannot defend against an attacker who can update both JSON and Hash. Host
database least privilege, change audit and intrusion controls remain required.

The module rejects binary nodes and obvious secret/card field names. This is a guardrail, not a replacement for each
service's event-contract review. The module never logs `payload_json`.

## 8. Persistence compatibility

The module assumes the approved physical column shapes from `COMPENSATION_EVENT_GUIDE.md` and does not create them.
Public Java types use the narrowest interoperable contract across the six approved PDManer models:

- `id` and `lockVersion`: Java `long`; new `lockVersion` starts at 1;
- `sourceVersion` and `schemaVersion`: positive INTEGER range exposed through Java `long` for API consistency;
- `sourceId`: Java `String`, compatible with a service-owned numeric or string physical column;
- event key maximum: 128 characters;
- event type and lock owner maximum: 64 characters;
- last error summary maximum: 1,000 characters;
- canonical envelope maximum: 64 KiB.

The table has unique constraints for `event_key` and
`(source_type, source_id, source_version, event_type)`. Ready work uses an access index on
`(execute_status, next_execute_at, id)`; expired Lease recovery uses a separate index on
`(execute_status, lock_until, id)`.

Physical type compatibility does not mean that every pre-existing model comment or scenario fixture already uses this
JAR's canonical semantics. Before each non-Risk host adopts the JAR or receives database-implementation authorization,
that host has an explicit migration/readiness gate:

- Client & Contract must replace legacy event keys/full-or-short Hash fixtures, use the complete canonical envelope,
  change new-row `lock_version=0` examples to 1, and align its model comments;
- Payment, Finance and Platform Support must replace legacy or shortened event keys, bare-Payload JSON and
  prefixed/short/pseudo database Hash fixtures with the five-component canonical key, complete envelope, raw 64-hex
  database Hash and `sha256:` embedded Hash;
- Commerce has no concrete compensation fixture today, but must still validate its PDManer semantics before adoption;
- all five hosts must describe `payload_json` as the complete Canonical Envelope and `payload_hash` as the SHA-256 of
  that envelope with `payloadHash` removed, update unique/access indexes if their approved physical model requires it,
  and keep PDManer, scenario documents, versioned migrations and Java mappings consistent in the authorized change.

Those host-owned artifacts are intentionally not rewritten by this Foundation implementation while their shared
model/document entry points are frozen.

## 9. State machine and fencing

```text
register -> INIT

INIT/ERROR due ---------------------> PROCESSING (claim, count + 1, lease, lock_version + 1)
expired PROCESSING -----------------> PROCESSING (takeover, count + 1, new owner/version)
PROCESSING + confirmed success ----> DELETE
PROCESSING + retryable/timeout ----> ERROR (clear lease, next_execute_at, version + 1)
PROCESSING + terminal/exhausted ---> FAILED (clear lease, version + 1)
FAILED + audited manual reopen ----> ERROR (count reset, version + 1)
```

If the last allowed attempt crashes after its external effect, Lease takeover increments the attempt count once more
and the Dispatcher transitions it to FAILED before invoking a Handler/Publisher. Takeover never creates an unbounded
extra side effect beyond `maximumAttempts`.

Every completion/update/delete includes the current `id + PROCESSING + lock_owner + lock_version + unexpired lease`
predicate. A timed-out or replaced worker cannot overwrite the new owner. External effects may still complete after a
timeout, so every Handler uses the stable event key as its idempotency key.

Claim happens inside an accepted Coordinator and only after a Handler-capacity permit is acquired. `AbortPolicy`
rejection or exhausted Handler capacity therefore leaves the row unclaimed and recoverable by scanning. The Handler
runs in a separate bounded platform-thread pool. The Coordinator waits at most the immutable Policy timeout, then
interrupts the Handler and conditionally moves the current Lease to ERROR/FAILED. A non-cooperative Handler may still
run after timeout, so its permit is released only when that Handler actually exits; DB transition failures never let
the Coordinator release a Handler-owned permit. Any late state result fails fencing, while external late side effects
remain at-least-once and require `eventKey` idempotency plus lower-level I/O timeouts shorter than Policy timeout.

## 10. Retry policy

Every Handler/Route supplies:

- positive timeout shorter than the configured Lease;
- positive maximum attempts;
- initial and maximum backoff;
- multiplier and bounded jitter ratio.

Retry delay uses capped exponential backoff with jitter. Attempts increment when Claim starts. A retryable outcome at
the configured maximum becomes FAILED. A missing Handler/Route is a terminal configuration failure and emits
controlled failure observations instead of being scanned forever.

Executor defaults are:

- core pool: 10;
- maximum pool: 20;
- queue capacity: 500;
- keep alive: 60 seconds;
- scanner batch: 100;
- rejection: `AbortPolicy`.

The configured pool is the bounded Coordinator pool. A second bounded platform-thread Handler pool has a fixed upper
bound equal to `maximum pool`, protected by a same-size Semaphore acquired before Claim. With defaults, local Handler
execution uses at most 20 Coordinator plus 20 Handler threads; virtual threads are not enabled. An MQ host additionally
owns an exclusive Rabbit callback executor with zero task queue and a hard maximum of twice the publication/Handler
maximum (40 threads by default). Saturation applies `CallerRunsPolicy`, deliberately pushing bounded protocol cleanup
back to the submitting Rabbit or caller thread instead of dropping it. Policy and Rabbit Route values are validated
and snapshotted at startup, so SLA/Route changes are restart-only. The
`CompensationExecutionPolicy.defaults()` helper is a development/safety baseline, not an approved production SLA;
its values are timeout 30 seconds, maximum attempts 5, initial/maximum backoff 1 second/5 minutes, multiplier 2.0 and
jitter 0.2. Production event types explicitly choose and review their Policy. Process defaults are enabled, Lease 2
minutes, maximum envelope 65,536 bytes, scanner batch 100, core/maximum pool 10/20, queue 500 and keep-alive 60 seconds;
source system and instance ID are mandatory. Nacos changes require a rolling restart because runtime objects are built
once at startup.

## 11. RabbitMQ behavior

The confirmed publisher sends the re-canonicalized immutable envelope using:

- the configured durable topic exchange and stable routing key;
- `mandatory=true`;
- persistent delivery mode;
- `application/json`, UTF-8;
- `messageId=eventKey`;
- synchronous Publisher Confirm scoped to the publication Channel.

An MQ host constructs one uninitialized, exclusive `CachingConnectionFactory`, fully applies endpoint or
ConnectionDetails, addresses/AddressResolver, credentials, TLS, failover, connection naming, correlated Confirm,
Return and a positive channel RPC timeout, then transfers ownership through exactly one
`CompensationRabbitConnectionFactoryProvider` Bean. The nested factory must never be exposed as a general
`ConnectionFactory` Bean or used by another `RabbitTemplate`/listener. The Provider freezes it to
`CacheMode.CONNECTION`, caps connections at `mor.compensation.pool.maximum-size`, initializes it, and owns both factory
and bounded callback-executor shutdown. Provider concurrency must equal the Handler maximum. Spring Boot
`spring.rabbitmq.*` properties affect the dedicated factory only when the host explicitly applies Boot's factory
Configurers while constructing it; setting properties for the ordinary Boot factory alone is insufficient.

The publisher owns a private `RabbitTemplate`, a fair publication Semaphore capped at the same maximum and a
payload-safe Message representation for Spring DEBUG/TRACE output. Each `invoke` obtains an attempt-dedicated physical
Connection and Channel. Successful attempts return the logical Channel/Connection to the dedicated pool. Confirm
timeout, interruption or another unknown outcome aborts the exact captured physical Connection with the bounded
channel RPC timeout, while `invoke` cleanup still closes logical resources and the publication permit is always
released. The general-purpose host connection factory is never mutated or aborted. Provider close uses a fair
read/write lifecycle gate: it rejects new attempts, drains bounded in-flight attempts, marks the nested factory
stopped, destroys it and closes its callback executor.

The host owns durable Topic Exchange/Queue/Binding declaration. The strict timing contract is
`0 < confirmTimeout + channelRpcTimeout < policy.timeout < leaseDuration`; neither channel RPC timeout nor Confirm
timeout may inherit a ten-minute default. Confirm waiting uses the exact validated `Duration` in nanoseconds. At
startup the library validates only the `topic_{source_system}_{business_subject}` naming grammar, Routing Key grammar,
AMQP 255-byte limits, Provider exclusivity/capacity and timeout relationships. It does not connect to the Broker or
prove Exchange type/durability or Binding existence; the host deployment readiness gate owns those checks, and
missing topology fails on first publication.

Acked Confirm with no returned message is success. Returned/unroutable messages, Nack, publish exception and Confirm
timeout are retryable failures. The library does not declare consumer queues or implement business consumer
idempotency.

## 12. Observability and safe errors

Micrometer counters/timers cover registration, duplicate registration, Claim/Lease takeover, success, retry, FAILED,
timeout, Coordinator rejection, Handler-capacity rejection, stale completion, missing Handler and manual reopen.
Gauges cover backlog rows, oldest pending age and expired Leases. Tags are limited to startup-controlled event type,
execution type and result; historical unknown types map to the fixed `unconfigured` tag. Event key/source ID never
become metric tags.

Library logs may contain event ID, controlled event type, status, stable error code and exception class, but do not log
event key, source ID, JSON envelope or raw exception message. Unexpected exceptions persist a fixed generic summary.
An explicit Handler `safeSummary` is a host contract: it must already exclude PII, credentials and business secrets.
The library additionally bounds it and performs best-effort redaction of common assignments, Bearer/opaque tokens,
long digit sequences and control characters; that cannot replace per-Handler contract tests.

The built-in Micrometer observer is a named, non-replaceable core bean and requires a `MeterRegistry`; declaring a
no-op `CompensationEventObserver` cannot disable mandatory signals. Hosts extend observability through their registry,
filters or separate listeners. Any explicit custom Rabbit publisher remains responsible for the complete mandatory,
persistent, Confirm and Return contract and must not downgrade success semantics.
The Handler/Route registry, canonical envelope factory, production Dispatcher and both bounded execution Executors are
also named, non-replaceable core Beans constructed from approved startup configuration. An extra Wakeup alongside the
default Dispatcher or a same-name core Bean fails startup. Scanner, AfterCommitDispatcher and Registrar retain explicit
`@ConditionalOnMissingBean` host replacement seams; a host that replaces one owns the complete equivalent transaction,
afterCommit wake-up, XXL scan and Claim-recovery contract.
The library emits metrics only; the host owns thresholds, Prometheus/Alertmanager rules, notification routing and
on-call response.

## 13. Auto-configuration

Spring Boot 4 uses `@AutoConfiguration` and `AutoConfiguration.imports`; no `spring.factories` or broad
`@ComponentScan` is added. The module reuses the host `SqlSessionFactory`/transaction manager and never creates a
private data source or MyBatis factory. The selected `SqlSessionFactory` and injected/default
`PlatformTransactionManager` must use the same service-local `DataSource` Bean; the transaction manager must be a
`DataSourceTransactionManager` or its `JdbcTransactionManager` subclass. Auto-configuration compares the exact
DataSource identity at startup, and registration/recovery also require a live transaction-bound resource for that
DataSource before accessing the Mapper. Unknown or mismatched transaction managers fail closed.

`mor.compensation.*` properties provide source system, instance ID, lease, pool, scan and envelope limits. RabbitMQ
Confirm timeout belongs to each `RabbitMqEventRoute`, not a global property. The module fails fast on invalid pool
relationships, missing identity, duplicate/cross-mechanism Handler/Route, timeout not shorter than Lease, missing
required Beans or multiple Wakeups. When the built-in Rabbit publisher is selected, it additionally fails fast on
multiple/missing dedicated Providers, a Provider exposed as a general ConnectionFactory, Provider/pool capacity
mismatch, non-positive channel RPC timeout, unsafe combined timeout budget, or disabled Confirm/Return. It does not
validate the production table or Broker topology at startup; schema and topology readiness remain host deployment
gates.

## 14. Test strategy

Tests live under `.../auto/` and use no Mockito behavior stubs, `@MockBean`, fixed local service or `Thread.sleep`.

Pure tests prove deterministic key generation, canonical JSON/hash, validation, backoff bounds and safe summaries.
MySQL 8.4 Testcontainers tests prove transaction rollback, afterCommit, duplicate conflict, BIGINT/VARCHAR source ID,
concurrent Claim, expired Lease takeover, fencing, retry/FAILED, non-cooperative timeout isolation, capacity ownership
when the timeout DB transition fails, positive initial lock version, last-attempt takeover, execution-type tampering,
MySQL JSON canonical round-trip, envelope integrity, rejection, delete-on-success, failure retention and audited reopen.
RabbitMQ 4.2 Testcontainers tests cover persistent mandatory JSON publication, Confirm success, returned/unroutable
failure at Provider concurrency 1, physical-Connection abort isolation during a paused Broker, interrupt restoration,
protocol-failure cleanup, Provider executor shutdown, closed-provider rejection, log canaries and identical
envelope/key on redelivery.

Awaitility or latches provide bounded asynchronous waiting. Test-only stateful Handlers and audit recorders derive
behavior from prepared scenario data and record observations; they do not replace production persistence, dispatcher
or RabbitMQ code.

## 15. Change coordination

Initial implementation changes only `mor-foundation`, the Foundation BOM entry, Foundation verification script and
this feature's design/plan documents. Risk Settlement assets remain untouched. Shared architecture/status documents
remain frozen until code, tests and Nexus publication have actually succeeded and concurrent tasks have coordinated a
safe update window.
