# Operations Gateway Hardening and Guard Simplification Design

> **2026-09-01 dynamic-control-plane successor:** The [unified public API control-plane design](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) now owns public Target/Route/Guard current configuration. This design remains authoritative for the deployed static Operations Gateway before dynamic Snapshot cutover and for its unaffected TLS/JWT/Header/Cookie/Redis/transport hardening; the static Catalog is a pre-cutover runtime fact only.

Date: 2026-08-30

Status: Implemented and verified on 2026-08-31

Supersedes the affected routing, trusted-header, request-body, JWKS and Guard sections of
`agent-docs/plans/2026-08-29-operations-api-gateway-design.md` and the corresponding implementation tasks in
`docs/superpowers/plans/2026-08-29-operations-api-gateway.md`. Unaffected Operations Gateway decisions remain in
force. The implementation plan created from this spec is the only execution authority for the superseded areas.

## 1. Purpose

Harden the existing Operations API Gateway for direct public access while keeping its first-slice boundary small and
reviewable. The Gateway must protect itself without assuming that a VPN, WAF or reverse proxy is correctly configured;
those controls remain additional production defenses.

The change also removes abstractions that were created for a future dynamic Guard policy source which does not yet
exist. It retains the distributed mechanisms that directly protect Gateway and BFF capacity.

## 2. Goals

- Keep exactly three public Operations routes: Login, Refresh and Logout.
- Make HTTP method an explicit route fact so future reviewed routes may use GET without changing the routing model.
- Guarantee that an unregistered Method + Path never reaches the Operations BFF.
- Derive routing, security, CORS, cookie handling, request-body policy and Guard policy from one compile-time catalog.
- Replace legacy protocol identifiers with neutral Paytech/Gateway names.
- Match protected Header names by case-insensitive whole-name equality; never use prefix or wildcard matching.
- Protect JWT/JWKS work and slow request bodies before they can consume unbounded Gateway resources.
- Preserve non-blocking, fleet-wide Redis Rate Limit and Concurrency protection.
- Correct Redis clock rollback, exact state-schema and Concurrency `Retry-After` behavior.
- Prove every security boundary through the existing real Gateway, Redis and protocol Simulator test style.

## 3. Non-goals

- No new GET route, Operations business API, fourth test-only runtime route or BFF endpoint.
- No Platform Support, Operations BFF or frontend production implementation.
- No dynamic Guard policy publication, Last-Known-Good policy cache or `PLT-I12` integration.
- No VPN, WAF, load-balancer or Redis high-availability provisioning.
- No generic routing repository, YAML-created public route or wildcard Operations route.
- No business authorization in Gateway. Resource authorization remains a BFF and owning-system responsibility.
- No request retry and no request-body cache outside one request lifetime.

## 4. Route catalog as the single runtime authority

`OperationsRouteDefinition` becomes an immutable route-policy aggregate. It owns:

- stable route code;
- exact `HttpMethod`;
- exact external path;
- exact BFF path;
- query policy (`REQUIRE_EMPTY` for all current Auth routes);
- access requirement (`ANONYMOUS` or `OPERATIONS_ACCESS_TOKEN`);
- Refresh Cookie input requirement (`DROP` or `REQUIRE_EXACTLY_ONE`);
- exact allowed CORS request Header set;
- exact external Header forwarding set;
- request-body maximum and total receive deadline;
- static ingress and authenticated-subject Guard profiles;
- BFF response timeout.

The three production definitions remain:

| Route | External contract | BFF contract | Access | Refresh Cookie input |
|---|---|---|---|---|
| Login | `POST /api/operations/auth/v1/login` | `POST /auth/v1/login` | Anonymous | Drop all |
| Refresh | `POST /api/operations/auth/v1/refresh` | `POST /auth/v1/refresh` | Anonymous | Require exactly one |
| Logout | `POST /api/operations/auth/v1/logout` | `POST /auth/v1/logout` | Operations Access JWT | Require exactly one |

Catalog lookup uses exact Method + raw Path equality. Header names use HTTP case-insensitive equality; paths remain
case-sensitive. Trailing slashes, encoded slash variants, other methods and unknown paths are not aliases.
The three current routes reject any raw query as an unregistered contract with the same local 404 and no BFF call.

Route registration uses each definition's method and path. Forwarding sets the downstream path explicitly to
`bffPath`; it does not infer a path with `stripPrefix`. The configured BFF URI is an HTTPS origin without userinfo,
query, fragment or embedded path. Plain HTTP is accepted only for a numeric loopback host used by the local
same-machine BFF Simulator or a same-workload loopback BFF boundary.

Security matchers and CORS preflight behavior are generated from the same definitions. The fallback Security chain is
deny-by-default. The Operations boundary returns the existing stable local 404 for unknown Operations Method + Path
before fallback Security, while fallback denial prevents a mistakenly registered non-Catalog route from becoming
anonymous.

No GET definition is created in this change. Future GET support consists of adding a reviewed Catalog entry whose
method is `GET` and whose body, access, CORS, forwarding and Guard policies are explicit.

## 5. Request processing order

The Operations request path is:

```text
trusted request ID and exact reserved-Header removal
  -> exact Operations Route and Origin boundary
  -> ingress Route/IP Rate decision and Concurrency Lease
  -> Spring Security JWT/JWKS validation when required
  -> authenticated Subject Rate decision and Concurrency Lease when configured
  -> bounded request-body aggregation with total receive deadline
  -> exact upstream Header/Cookie reconstruction and trusted identity injection
  -> Operations BFF response-time budget
  -> terminal response protection and all acquired Lease releases
```

Preflight OPTIONS requests terminate at the boundary and do not consume Redis Guard capacity. Unknown routes terminate
locally. The ingress Lease wraps the remaining chain, so completion, error, cancellation and timeout all attempt an
idempotent release; its TTL recovers capacity after process failure.

This ordering intentionally allows an ingress Rate Token to remain consumed when JWT validation, body validation or a
later Subject rule rejects the request. Refunding it would let expensive invalid requests evade the entry budget.

### 5.1 HTTP transport envelope

Redis Guard begins only after Netty has parsed an HTTP request, so the public listener also enforces pre-application
limits:

- production client traffic terminates TLS at this Gateway or arrives through an explicitly trusted same-workload L4
  TLS pass-through; cleartext public HTTP is forbidden;
- TLS 1.3 permits exactly `TLS_AES_128_GCM_SHA256`, `TLS_AES_256_GCM_SHA384` and
  `TLS_CHACHA20_POLY1305_SHA256`; TLS 1.2 permits exactly `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`,
  `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`, `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256` and
  `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384`, following
  [RFC 8446](https://www.rfc-editor.org/rfc/rfc8446.html#section-9.1) and
  [RFC 9325](https://www.rfc-editor.org/rfc/rfc9325.html#section-4.2); startup validation rejects an enabled suite
  outside this set, while certificate/key material remains deployment Secret input;
- request line maximum: 8 KiB;
- aggregate request Header maximum: 32 KiB;
- `Authorization` maximum: 8 KiB;
- aggregate `Cookie` maximum: 8 KiB;
- `Origin` and each CORS control Header maximum: 2 KiB;
- total time to receive and parse request Headers: five seconds;
- connection read-idle timeout: five seconds while a request is incomplete;
- accepted connections: at most 1,000 per Gateway instance with a listen backlog of 512;
- at most 1,000 requests on one keep-alive connection.

Limits that apply before `ServerWebExchange` creation are installed through the Reactor Netty server configuration,
not a WebFilter. Tests may use loopback HTTP but run the same decoder, deadline and connection-admission code.

After ingress Concurrency Acquire succeeds, every remaining stage has a finite budget: JWKS response three seconds,
each Redis operation two seconds, body receive five seconds, BFF pool acquisition two seconds, and the complete BFF
chain ten seconds including connect and pool wait. Authorization and Cookie size limits bound local parsing work.

## 6. Request body aggregation and replay

A WebFlux request body is a one-shot `Flux<DataBuffer>`. Enforcing an actual byte limit for chunked, missing or false
`Content-Length` requires consuming that stream before the BFF sees it. Once consumed, the downstream chain cannot read
it again.

The existing `forwardCachedBody` behavior therefore remains conceptually necessary but is renamed to
`forwardWithReplayableBody`:

1. aggregate at most the route maximum plus one byte;
2. reject an oversized stream before any byte reaches the BFF;
3. copy the allowed bytes out of the pooled Netty buffer;
4. release the original pooled buffer;
5. decorate the request with a replayable request-lifetime byte array;
6. set the exact `Content-Length` and remove `Transfer-Encoding`;
7. best-effort overwrite the temporary byte array after downstream termination.

This is not Redis, Caffeine or persistent caching. It is a maximum 16 KiB request-scoped replay buffer. Production code
and logs never record the body. Full aggregation is preferred to streaming counting because streaming can discover an
oversized request only after part of a password-bearing body has already reached the BFF.

All three current Auth routes retain a 16 KiB maximum and gain a five-second total body receive deadline. The deadline
includes all chunks rather than resetting after every chunk. Expiry cancels aggregation, releases every acquired
ingress and Subject Lease, does not call the BFF and returns `408 GATEWAY_REQUEST_TIMEOUT` when the response can still
be written.

## 7. Protocol names and exact Header boundary

The public and trusted protocol names are:

- request/response correlation: `X-Request-Id`;
- authenticated subject: `X-Gateway-Actor-Subject`;
- authenticated session: `X-Gateway-Session-Id`;
- authenticated actor type: `X-Gateway-Actor-Type`;
- authenticated channel: `X-Gateway-Channel`;
- Refresh Cookie: `__Secure-operations-refresh`.

Production source, production configuration and positive test fixtures contain no legacy project acronym in Header,
Cookie, environment-variable, class, field or constant names. A narrow forgery/migration test may construct an old
external value locally, but must not define it as a reusable protocol constant.

At ingress, the Gateway removes only the following complete names using case-insensitive equality:

- `X-Request-Id`;
- the four `X-Gateway-*` identity names listed above;
- `Forwarded`;
- `X-Forwarded-For`;
- `X-Forwarded-Host`;
- `X-Forwarded-Port`;
- `X-Forwarded-Proto`;
- `X-Forwarded-Prefix`;
- `X-Forwarded-Ssl`;
- `X-Forwarded-Server`;
- `X-Real-IP`.

Names such as `X-Gateway-Actor-Subject-Extra` and `X-Forwarded-Evil` do not match this removal set. They still cannot
reach the BFF because upstream Header construction starts from an empty set and copies only the current route's exact
allowlist.

The route contracts are:

| Route | Browser may request | External values BFF may receive |
|---|---|---|
| Login | `Content-Type` | `Origin`, `Content-Type` |
| Refresh | `Content-Type`, `X-CSRF-Token` | `Origin`, `Content-Type`, `X-CSRF-Token`, one Refresh Cookie |
| Logout | `Authorization`, `Content-Type`, `X-CSRF-Token` | `Origin`, `Content-Type`, `X-CSRF-Token`, one Refresh Cookie |

Every BFF request receives the Gateway-generated `X-Request-Id`. A successfully authenticated protected route also
receives the four trusted identity Headers. The raw `Authorization` value and all non-target Cookies are never
forwarded.

CORS allows only the configured exact HTTPS Operations Console Origin. Preflight accepts only the requested method and
Header subset declared by that route. Gateway-owned response Headers are removed and rebuilt using an exact set:
`X-Request-Id`, `Access-Control-Allow-Origin`, `Access-Control-Allow-Credentials`,
`Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`, `Access-Control-Expose-Headers` and
`Access-Control-Max-Age`. Prefix removal of `Access-Control-*` is forbidden. CORS exposes only `X-Request-Id` and
`Retry-After`.

The Refresh Cookie remains `Secure; HttpOnly; SameSite=Strict; Path=/api/operations/auth/v1` without `Domain`.
`__Secure-` is retained because the narrower Path is intentional; `__Host-` would require `Path=/`.

Production Operations Console and Gateway URLs must be schemeful same-site: both use HTTPS and share the same
registrable domain, even though they are different Origins. Every Login, Refresh and Logout browser request uses Fetch
credentials mode `include`; the Gateway continues to return the exact Origin plus
`Access-Control-Allow-Credentials: true`. A cross-site Console/Gateway deployment is incompatible with this Strict
Cookie contract and is rejected during deployment review rather than weakening `SameSite`.

The response boundary accepts at most one BFF `Set-Cookie`, and only for `__Secure-operations-refresh`. It validates
the opaque value and rewrites the attributes to the exact contract above. Login requires a positive `Max-Age` no
greater than 28,800 seconds when a Cookie is present; Refresh permits either that positive range or `Max-Age=0` to
clear an invalid Session; Logout permits only `Max-Age=0`. An empty value is valid only when clearing. Any second,
foreign or malformed `Set-Cookie`, any `Domain`, or a missing required security attribute is an invalid upstream
contract and becomes 502 before response commitment.

Every Auth response, including BFF and Gateway errors, receives `Cache-Control: no-store`, `Pragma: no-cache` and a
deduplicated `Vary: Origin`. These are Gateway-owned exact response Header names.

## 8. Why Rate Limit and Concurrency both remain

Rate Limit controls how many requests may start per interval. Concurrency controls how many admitted requests may
remain unfinished at the same moment. They protect different resources.

For example, `300/min` is an average of five starts per second. At 100 ms latency it represents roughly one in-flight
request; at the ten-second BFF timeout it can represent roughly 50. A Token Bucket can also admit its full Burst at
once. Rate Limit deducts a Token at start and cannot observe completion. A Concurrency Lease is released on completion
and directly bounds body aggregation, BFF connections, response buffers and slow upstream work.

Concurrency is therefore a distributed Bulkhead, not a duplicate Rate Limit. It remains necessary while the Gateway
must protect itself without relying on WAF correctness.

## 9. Simplified Guard architecture

The implementation retains:

- static per-route profiles;
- Redis-backed atomic Token Bucket decisions;
- Route, Client IP and authenticated Subject dimensions;
- Redis ZSET Concurrency Leases;
- raw canonical socket IP and exact validated JWT `sub` values as Redis identity-key components;
- HMAC-SHA-256 pseudonymization for access-log IP and Subject values only;
- fail-closed Redis behavior;
- one Redis `TIME` value per Lua decision.

The implementation removes:

- `GuardTier` and the `GLOBAL/ROUTE/CLIENT/CREDENTIAL/EMERGENCY` precedence model;
- the generic policy resolver and unused dynamic policy-release scaffolding;
- all Redis identity HMAC configuration, digest expansion and rotation branches;
- stored policy revisions, active Rate-policy metadata and state-migration branches.

Redis Guard has no HMAC key or Redis-key rotation workflow. Every instance derives the same identity key from the raw
canonical socket IP or exact validated JWT `sub`; these Redis values are sensitive operational state and must not be
copied to logs or metrics. Access logs continue to use their own HMAC key and Purpose, and rotating that key changes
only log pseudonyms, never Guard capacity. An identity component is limited to 512 UTF-8 bytes and rejects blank,
control-character or invalid-Unicode input, bounding raw Key growth without transforming the accepted value. Guard
state has no policy revision or rolling migration contract.

The Guard has two phases:

1. ingress phase before body and JWT work: Shared Route plus Client IP Rate and Concurrency;
2. authenticated phase after JWT work: authenticated Subject Rate and Concurrency when the route declares them.

Within a phase, all Rate Bucket checks and deductions are atomic in one Lua invocation, and all Concurrency Lease-key
checks and writes are atomic in a separate Lua invocation. Rate and Concurrency decisions are deliberately ordered but
are not one transaction. A Concurrency, JWT, body or authenticated-phase rejection does not restore an already
consumed Rate Token, but every acquired Concurrency Lease is released.

## 10. Static first-slice Guard profiles

Existing limits remain unless an ingress IP rule is required to close the Logout/JWKS gap:

| Route | Ingress Rate | Ingress Concurrency | Authenticated Subject |
|---|---|---|---|
| Login | Route `300/min`; IP `5/min`, Burst `5` | Route `20`; IP `2` | None |
| Refresh | Route `600/min`; IP `30/min`, Burst `10` | Route `50`; IP `3` | None |
| Logout | Route `300/min`; IP `30/min`, Burst `10` | Route `50`; IP `3` | Rate `10/min`, Burst `10`; Concurrency `2` |

Logout's new IP ceiling is deliberately wider than Login's credential-attack limit so an office NAT is not treated as
one user. Subject rules continue to constrain a successfully authenticated account.

All ingress and Subject Leases use a 30-second duration and a 60-second Redis Key/metadata TTL. This exceeds the
bounded post-Acquire path of JWKS, Subject Redis calls, body receive, BFF pool/connect/response and local processing.
Release normally occurs immediately; TTL is crash recovery, not the normal completion mechanism. A startup invariant
recomputes the declared worst-case path and rejects configuration whose Lease duration is not strictly greater.

No default or hard-coded profile is invented for a future GET route. Adding any route requires an explicit reviewed
profile in its Catalog definition.

## 11. JWKS hardening

JWT work remains behind the ingress Guard and the JWKS provider also has its own bounded refresh behavior:

- only `RS256` proceeds to key selection;
- `kid` must match the exact expression `^[A-Za-z0-9._:-]{1,128}$`;
- invalid algorithm or `kid` returns 401 without an HTTPS request;
- cached known keys are selected without network access;
- all unknown kids share one in-flight refresh and one five-second global refresh cooldown;
- concurrent requests join the same refresh, then independently check the refreshed immutable key map;
- no per-kid cooldown map and no in-flight collection of requested kids exists;
- a still-unknown kid after a successful refresh returns 401;
- a required refresh that fails because JWKS is unavailable returns 503;
- during the five-second cooldown after a failed refresh, cache-miss requests receive the same bounded 503 without a
  new network call; after a successful refresh, still-unknown kids receive 401 during the cooldown;
- cached known keys remain usable until their five-minute cache TTL expires.

The JWKS URI must be HTTPS with a host and path, and must not contain userinfo, query or fragment. The existing private
trust anchor, two-second connect timeout, three-second response timeout, 64 KiB response maximum, 16-key maximum,
RSA-2048 minimum and signing/verification key checks remain.
JWKS HTTP redirects are never followed; any 3xx response is a dependency failure.

Metrics record bounded refresh success, failure and suppressed-refresh counts without using `kid` as a tag.

## 12. Redis correctness

### 12.1 Token Bucket

Rate state uses the `guard:v3` Redis namespace and keeps exactly three short Hash fields: `t` for current integer
Microtokens, `lr` for last-refill milliseconds and `rr` for the integer refill remainder. It stores no policy revision,
capacity, refill amount or refill period. Scripts never read or migrate earlier-version state; earlier namespaces expire
under their existing TTLs.

For each request the script:

1. reads Redis `TIME` once;
2. validates every key and argument before mutation;
3. uses `effectiveNow = max(redisNow, lastRefillMillis)` so persisted time never moves backward;
4. evaluates every applicable Bucket before deducting any Token;
5. deducts from all Buckets only when all allow;
6. otherwise persists only the updated `t/lr/rr` refill state without a deduction and returns the maximum recovery
   time;
7. computes and applies a finite TTL from the current compile-time static profile.

This prevents clock rollback from granting a refill interval twice without adding policy-version state. A policy or
schema change requires a separately reviewed namespace change; the v3 script performs no in-place or rolling state
migration.

### 12.2 Concurrency

Every Concurrency ZSET has a companion metadata Hash in the same Lua decision. It stores exactly `hw`, the logical-time
high-water mark, and `l`, the active limit. Both keys share the 60-second TTL. A non-empty ZSET without valid metadata,
or metadata whose `l` differs from the current static profile, fails closed; the script never guesses or migrates
incomplete or mismatched state.

Acquire reads Redis `TIME` once and uses `effectiveNow = max(redisNow, storedHighWaterMillis)`. It persists the new
high-water mark before using it for expiry cleanup and new Lease scores, so clock rollback cannot create a Lease that
expires early when Redis time recovers. No policy revision is accepted or persisted, and limit changes are not migrated
inside the script.

Acquire then uses one Lease ID for every dimension in the phase, removes expired members, checks all dimensions, and
writes only when all allow. When a dimension is full, `Retry-After` uses the `count - limit + 1`-th expiry rather than
assuming the earliest expiry always frees capacity. The reported wait is capped to the configured Lease window. A
rejected Acquire does not extend ZSET or metadata TTL beyond the last successful Acquire, so continued
traffic cannot pin a rollback high-water state forever; a prolonged rollback remains fail-closed until release or
finite TTL recovery rather than releasing work early.

Release is idempotent and removes the Lease ID from all phase keys. Release failure records a bounded metric and relies
on TTL; it never rewrites a response that is already committed.

## 13. Error and upstream behavior

Gateway-owned errors retain one JSON shape and the trusted `X-Request-Id`:

| Status | Gateway condition |
|---:|---|
| 401 | Missing/invalid JWT, invalid or unknown `kid`, missing/duplicate/malformed Refresh Cookie |
| 403 | Origin or validated Operations channel/actor constraint fails |
| 404 | Unknown Operations Method + Path |
| 408 | Total request-body receive deadline expires before forwarding |
| 413 | Actual request body exceeds the route maximum |
| 429 | Rate or Concurrency decision rejects; includes integer `Retry-After` |
| 431 | A parsed Authorization, Cookie, Origin or CORS control Header exceeds its declared per-Header budget |
| 502 | BFF TLS, protocol or invalid upstream-response failure |
| 503 | Redis unavailable, required JWKS unavailable, BFF connect/pool acquisition unavailable |
| 504 | BFF response-time budget expires before response commitment |

BFF application responses are not wrapped. Network exception classification walks the cause chain but matches only
an explicit finite type set. Error bodies and logs never contain body, password, Cookie, JWT, Redis identity, internal
URI or exception message.

## 14. Local Redis configuration

Gateway tests continue to use the already-running repository Redis at `127.0.0.1:16379` and never start Testcontainers
or Docker. The password environment variable becomes `PAYTECH_REDIS_PASSWORD` in Gateway test code, Gateway README,
the Redis service entry in repository Compose, its health check and the active local-environment/testing documents.
Other established project environment variables and the Compose project name are outside this rename.

The tracked `.env.example` is renamed in the same change. The untracked `.env.local` is never edited automatically;
the developer copies the existing Redis secret value to `PAYTECH_REDIS_PASSWORD` before the next Compose recreation.
An already-running Redis container keeps its current password and need not be recreated: tests export the same secret
under the new process variable name. The README provides this one-time migration without printing the value.

Tests retain unique per-run namespaces and clean only their own keys with paginated `SCAN` plus exact `UNLINK/DEL`.
They never use `KEYS`, `FLUSHDB`, port 6379 or another service's Redis namespace.

## 15. Automated verification

Implementation follows test-first changes and the existing no-Mock production-chain rule. Required coverage includes:

- Catalog method lookup, exact BFF path rewrite and startup validation;
- the three production routes remain POST and no fourth runtime route exists;
- unknown method/path, trailing slash, case and encoded-slash variants return local 404 with zero BFF calls;
- Auth requests with a query return local 404 with zero BFF calls;
- fallback Security denies unknown non-Operations traffic, while an architecture test rejects RouteLocator sources
  outside the compile-time Catalog configuration;
- route-derived Security and CORS method/Header behavior;
- exact reserved-Header equality, including near-match names that must not match;
- exact BFF Header reconstruction and absence of Bearer/non-target Cookies;
- exact response Cookie validation plus `no-store`, `Pragma` and `Vary: Origin` ownership;
- new Header, Cookie and Redis environment-variable names and absence of legacy protocol values or symbols in
  production source and positive fixtures;
- chunked/misreported body size, five-second total receive timeout, cancellation and temporary-array cleanup behavior;
- ingress Guard rejection before body/JWT/BFF work and Lease release on every terminal signal;
- different sequential and concurrent unknown kids cause at most one JWKS HTTPS refresh in a cooldown window;
- invalid algorithm and invalid/long `kid` cause zero JWKS calls;
- cached valid keys continue working while unknown-kid refresh is suppressed;
- Redis time rollback cannot duplicate refill;
- Redis identity keys contain the raw canonical IP or exact JWT `sub`, while access logs retain HMAC pseudonyms;
- `guard:v3` Rate Hashes contain exactly `t/lr/rr`, with no policy revision or migration metadata;
- `guard:v3` Concurrency metadata contains exactly `hw/l`, and a limit mismatch fails closed without migration;
- saturated Concurrency dimensions report the actual time to an available slot;
- Redis failure remains fail-closed;
- upstream connect, pool, TLS, protocol and timeout failures map to the declared stable outcomes;
- access logs and metrics remain low-cardinality and contain none of the protected values.

Focused tests run first. The final gate runs the complete Gateway automatic suite against the existing Compose Redis,
the repository boundary verification, coverage verification and a semantic scan for forbidden legacy protocol names.

## 16. Documentation and acceptance

The implementation synchronizes:

- `api-gateway/README.md`;
- `agent-docs/plans/2026-08-29-operations-api-gateway-design.md`;
- `agent-docs/plans/2026-08-29-platform-support-operations-authentication-requirements.md`;
- the active Gateway portions of `agent-docs/TECHNOLOGY_STACK.md`, `agent-docs/JAVA_PROJECT_STRUCTURE.md`,
  `agent-docs/UNIT_TEST_GUIDE.md` and `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`;
- repository local-environment verification for the renamed Redis variable.

The old implementation plan receives a supersession notice and must not be executed for routing, Header/Cookie,
request-body, JWKS or Guard work after this spec.

Acceptance requires all of the following:

1. only the three exact current POST routes are public;
2. future GET does not require a framework redesign, but no GET API is exposed now;
3. unknown API traffic and spoofed trusted context never reach the BFF;
4. body and JWT/JWKS work are covered by the ingress distributed Bulkhead and Rate ceiling;
5. Rate and Concurrency algorithms satisfy rollback and exact `guard:v3` state-schema tests without policy revision or
   rolling migration;
6. Gateway production code and positive protocol fixtures have no legacy protocol or environment identifiers;
7. all Gateway automatic tests and repository gates pass without Testcontainers;
8. no Platform Support, BFF, frontend or business-service production code is added.
