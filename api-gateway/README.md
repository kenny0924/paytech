# API Gateway

`api-gateway` is the public reactive ingress with separate Operations and Client API security chains. It loads the complete PLT-I20 public Gateway
Snapshot from Platform Support, compiles channel-specific routes and targets from that complete snapshot, and
security ceilings, enforces the Gateway Guard, creates trusted request context, and forwards to the approved Owner. It does
not authenticate account passwords or own current role/resource/business authorization facts. The [
`2026-09-01 dynamic Gateway configuration design`](../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)
owns the runtime catalog contract; the [
`2026-08-30 Operations Gateway hardening design`](../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md)
remains authoritative for the Operations security implementation. Public TLS termination, private HTTP and trusted
Client IP follow the [
`2026-09-02 MVC Web logging and internal HTTP design`](../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md).

## Client API boundary

The [Client API contract](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md) owns this channel.
Callers send one `Authorization: Bearer <opaque credential>` plus business inputs; `X-Request-Id` is optional.
Trusted ingress supplies `X-Entry-Channel: CLIENT_API`. Callers never choose Client, Tenant, Scope, Actor or Route identity.
Ordinary Client requests do not require HMAC signatures, application-layer body encryption, or a client timestamp.
Public transport protection remains HTTPS at Nginx.

The request chain is ingress policy → shared/IP Guard → PLT-I03 authentication → typed Client identity →
Credential/Tenant Guard → exact Scope → body budget → minimum Owner headers. The Gateway consumes the reactive
`platform-support-api` contract for `POST /platform-support/api-credentials/v1/validate`; it sends the naked credential
only in `X-Platform-Api-Credential`, with no cache, retry, redirect or alternative authentication fallback.
An invalid credential returns `401 UNAUTHENTICATED`; missing Scope returns `403 INSUFFICIENT_SCOPE`;
unavailable or malformed Platform responses return `503 DEPENDENCY_UNAVAILABLE`.

After rebuilding headers, only `X-Request-Id`, `X-Gateway-Client-Id` and `X-Gateway-Credential-Id` provide trusted
downstream context, alongside explicitly allowlisted business headers. Authorization, Tenant/Scope, Cookie, proxy
headers and all caller-supplied Gateway identity headers are removed. Owner Problems use the shared strict RFC 9457
validator. Client responses use the fixed response-header allowlist, constrained single `201 Location`, and terminal
Request ID/Date/no-cache headers without Operations CORS. Logs use purpose-separated identity digests, never credentials.

`gateway.platform-credential` defaults to service discovery via `PLATFORM_SUPPORT_SERVICE_ID`, a 1-second connect
timeout, a 2-second total authentication budget and a 16 KiB maximum response. Timeout ceilings are 10 seconds;
response size cannot exceed 16 KiB. A direct Loopback URI requires an explicit switch and only the `auto` profile.
Tests use real Gateway HTTP/Redis and HTTP protocol simulators without replacing the production authentication client.
Build/install the Owner `platform-support-api` contract locally before compiling if the local SNAPSHOT predates PLT-I03.

This implementation does not register a production Client Target, activate routes, apply Platform SQL or change Nginx.
Verification, unresolved prerequisites and rollout status live only in the [status ledger](../agent-docs/CURRENT_IMPLEMENTATION.md).

## Prerequisites

- Java 25; use the checked-in Maven Wrapper (Maven 3.9.16).
- Node.js for the repository boundary and coverage gates.
- The existing local-environment Redis 8.2 service on `127.0.0.1:16379`.
- Nacos service discovery for Platform Support PLT-I20 in deployed environments; local tests use an explicitly enabled
  direct URI.

Create `../local-environment/.env.local` from its example if it does not exist. Start only the already-defined Compose
Redis service manually:

```bash
cd ../local-environment
docker compose --env-file .env.local --profile base up -d redis
docker compose --env-file .env.local --profile base ps redis
cd ../api-gateway
```

Fixed test configuration belongs to `api-gateway-test/src/test/resources/application-auto.yml`, loaded by Spring's
`auto` profile in both Maven and IDE runs. Middleware tests start the Gateway application; Redis probes and cleanup
reuse its Spring-managed connection factory. Java only registers runtime simulator addresses, isolation namespaces
and explicit scenario variables, not fixed connection settings or direct environment reads.

`application-auto.yml` automatically imports the optional, Git-ignored
`api-gateway-test/src/test/resources/application-auto.local.yml`. For an existing local Redis with a custom password,
configure `gateway.test.redis.local-password` there from an authorized source and restrict the file to owner read/write
(`chmod 600`). Maven and IDE resource loading then use the same configuration without extra process environment setup.
Do not commit this file or copy real credentials into the tracked YAML. An explicitly supplied `PAYTECH_REDIS_PASSWORD`
still has priority; only when neither is configured does the repository's isolated test account example apply.
Tests never inspect Docker credentials, read `.env.local`, print passwords or change the shared Redis password.
The `unavailable` test scenario also gets its fixed failure settings from the same YAML and injects only a newly
allocated closed Loopback port. It keeps the sole `auto` profile required by the credential client's direct-URI gate.

The automated tests connect to that existing Redis instance. They never invoke Docker, Docker Compose, or
Testcontainers. Each run uses and cleans only its unique Redis namespace with `SCAN`; Redis-unavailable tests use an
unused loopback port and never stop or damage the shared Redis service.

## Build and verify

```bash
./mvnw clean verify
node scripts/verify-gateway-boundaries.mjs
node scripts/verify-jacoco.mjs
```

The application listener is `18080`; the separate management listener is `19080`. The web module produces both an
ordinary dependency JAR and an executable `-exec.jar`. The test module produces coverage and Surefire evidence only, not
a deployable JAR.

## Error contract

Every Gateway-owned response after the public HTTP stack has parsed enough of the request uses the Code-MoR Problem
Profile with exact `Content-Type: application/problem+json`. A non-`HEAD` body contains only `type`, `title`, `detail`,
`code`, `requestId`, and `timestamp`; `type` is `about:blank`, `title` is the standard HTTP reason phrase, and the UTC
timestamp always has six fractional digits. The one response `X-Request-Id` value is byte-for-byte equal to the body
value. `HEAD` errors keep the same status, media type, Request ID, and approved response headers but transfer no body.

The Gateway validates Owner 4xx/5xx responses before forwarding them. A valid Problem is forwarded byte-for-byte;
an invalid or truncated Owner response is discarded and replaced with `502 UPSTREAM_PROTOCOL_ERROR`. During the
source-only rollout, legacy four-field JSON is accepted only from the immutable `operations-console-bff` target;
strict Problem validation is the default for every other Owner. Gateway internal outcomes and metric dimensions keep
the existing `UPSTREAM_INVALID_RESPONSE` name and are not public API codes. The finite outcome set is
`UPSTREAM_RESPONSE`, `AUTHENTICATION_FAILED`, `AUTHORIZATION_DENIED`, `ROUTE_NOT_FOUND`, `INVALID_REQUEST`,
`REQUEST_HEADER_FIELDS_TOO_LARGE`, `PAYLOAD_TOO_LARGE`, `REQUEST_TIMEOUT`, `RATE_LIMITED`, `CONCURRENCY_LIMITED`,
`DEPENDENCY_UNAVAILABLE`, `UPSTREAM_INVALID_RESPONSE`, `UPSTREAM_TIMEOUT`, `CLIENT_CANCELLED`,
`IDEMPOTENCY_REPLAYED` and `IDEMPOTENCY_CONFLICT`. Pre-application Nginx/connector failures
remain transport or edge failures rather than synthetic Problems.

`GatewaySnapshotCompilerAutoTest` 校验通用路径语法、变量、冲突与安全档案；未注册的相似路径由实际 HTTP 测试确认返回 404。
完整 `clean verify`、失败基线对照与 JaCoCo 门禁状态统一记录在 [实现台账](../agent-docs/CURRENT_IMPLEMENTATION.md)，
不能以某个专项通过代替完整门禁。

The Snapshot compiler rejects intersecting variable templates with the same HTTP method and segment count even when
their variable counts differ. Fully literal paths keep their approved priority. Paths are compiled once and compared
within method/segment buckets; the bounded catalog remains capped at 4096 Routes. Regression coverage includes both
insertion orders, different methods, disjoint literals and the maximum catalog size. Current verification and deployment
status for this Platform-aligned correction is recorded in [the status ledger](../agent-docs/CURRENT_IMPLEMENTATION.md).

## Runtime configuration

All values below are deployment inputs. Secret material must come from the deployment secret store; do not commit it.

| Environment variable                  | Spring property                                                            | Purpose                                                                                                          |
|---------------------------------------|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `GATEWAY_BIND_ADDRESS`                | `server.address`                                                           | Explicit private address reachable only from the Nginx network                                                   |
| `NACOS_SERVER_ADDR`                   | `spring.cloud.nacos.discovery.server-addr`                                 | Nacos address used only for internal service discovery                                                           |
| `NACOS_NAMESPACE`                     | `spring.cloud.nacos.discovery.namespace`                                   | Deployment-specific Nacos namespace                                                                              |
| `PLATFORM_SUPPORT_SERVICE_ID`         | `gateway.platform-snapshot.service-id`, `gateway.platform-jwks.service-id`, `gateway.platform-credential.service-id` | Registered Platform Support service ID used by Snapshot, JWKS and Credential clients; defaults to `platform-support-service` |
| `OPERATIONS_CONSOLE_ORIGIN`           | `gateway.operations.console-origin`                                        | Exact HTTPS browser Origin                                                                                       |
| `OPERATIONS_TEST_HTTP_ENABLED`        | `gateway.operations.test-http-enabled`                                     | Defaults to `false`; explicit isolated HTTP test mode described below                                            |
| `OPERATIONS_JWT_ISSUER`               | `gateway.operations.security.issuer`                                       | Required JWT issuer                                                                                              |
| `OPERATIONS_JWT_AUDIENCE`             | `gateway.operations.security.audience`                                     | Required Operations audience                                                                                     |
| `OPERATIONS_GUARD_REDIS_NAMESPACE`    | `gateway.operations.guard.redis.namespace`                                 | Environment-specific Guard key namespace                                                                         |
| `OPERATIONS_ACCESS_LOG_HMAC_VERSION`  | `gateway.operations.hmac.access-log.version`                               | Access-log pseudonym version                                                                                     |
| `OPERATIONS_ACCESS_LOG_HMAC_MATERIAL` | `gateway.operations.hmac.access-log.encoded-material`                      | Independent Base64 access-log HMAC secret (at least 32 decoded bytes)                                            |

These example values are strictly for an isolated non-production machine. The generated access-log HMAC value is
ephemeral and must not be reused in production:

The separately deployed HTTP test environment sets `SPRING_PROFILES_ACTIVE=test`,
`OPERATIONS_TEST_HTTP_ENABLED=true`, and `OPERATIONS_CONSOLE_ORIGIN=http://localhost:28080`.
Enabling the switch requires exactly the active profile `test` and a canonical
`http://localhost:<1-65535>` Origin, with no path, user info, query or fragment.
Other profiles, mixed profiles, other hosts, implicit ports and HTTPS with the HTTP switch enabled fail startup.
Gateway and BFF must enable the same HTTP test mode. Operations uses the same JSON Token
contract in HTTP test mode and HTTPS: no request Cookie is forwarded and no response Cookie
is accepted. JWT, exact Origin and trusted identity boundaries remain enforced.

```bash
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=16379
export SPRING_DATA_REDIS_PASSWORD="${PAYTECH_REDIS_PASSWORD}"
export GATEWAY_BIND_ADDRESS=127.0.0.1
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=public
export PLATFORM_SUPPORT_SERVICE_ID=platform-support-service
export OPERATIONS_CONSOLE_ORIGIN=https://operations-console.local.test
export OPERATIONS_JWT_ISSUER=https://platform-support.local.test
export OPERATIONS_JWT_AUDIENCE=operations-console
export OPERATIONS_GUARD_REDIS_NAMESPACE=local:api-gateway
export OPERATIONS_ACCESS_LOG_HMAC_VERSION=local-access-log-v1
export OPERATIONS_ACCESS_LOG_HMAC_MATERIAL="$(openssl rand -base64 32)"

java -jar api-gateway-web/target/api-gateway-web-1.0.0-SNAPSHOT-exec.jar
```

## Client Console authentication boundary

`CLIENT` is the human customer Console channel. Its approved authentication contract is in the
[Client registration requirement](../requirements/20260914/client-console-注册与客户入驻/api-contract.md).
The `gateway.client.enabled` switch defaults to `false`; a CLIENT PUBLIC route in a Snapshot does not enable it.
An approved deployment must supply `gateway.client.console-origin` as an exact HTTPS Origin,
`gateway.client.security.issuer` as the CUSTOMER issuer, and `gateway.client.security.audience=client-console`.
The Console Origin and issuer must differ from their Operations counterparts. These properties can be supplied
through Spring configuration or the corresponding `GATEWAY_CLIENT_*` environment variables.

CUSTOMER JWKS uses `/platform-support/authentication/v1/realms/CUSTOMER/jwks` and an independent bounded cache.
CLIENT JWT routes require both IP and authenticated-principal Guard pairs; ACCOUNT context requires no Tenant.
Only validated CUSTOMER/CLIENT identity is reconstructed for the BFF; browser Authorization and forged identity
headers are not forwarded. Business authorization remains with the BFF and its Owners.

The channel-specific refresh Cookie is `__Secure-client-refresh`, host-only, Secure, HttpOnly, SameSite=Lax,
Path `/api/client/auth/v1`, with a maximum age of seven days. Route modes govern forwarding, issuing and clearing;
Operations cookies cannot satisfy CLIENT refresh requests. Writes and preflights require the exact CLIENT Origin.
Same-origin GET may omit Origin. Error responses rebuild trusted CORS headers without accepting upstream values.
The Operations-only HTTP test mode does not relax CLIENT cookie or Origin requirements.

Implementation verification and rollout status remain in the [current ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914).

## Dynamic PLT-I20 catalog and readiness

On startup the Gateway resolves `platform-support-service` through Nacos and immediately requests
`GET /platform-support/gateway-config/v1/public-snapshot` through Spring Cloud LoadBalancer. Nacos supplies only healthy
service instances; Platform Support remains the Snapshot authority. The control client accepts exactly HTTP `200`, reads
at most 1 MiB with a maximum JSON nesting depth of 32, compiles the complete DTO, and only then atomically replaces the
current immutable runtime Snapshot. It sends no caller identity, conditional-cache, or public request headers. The
default refresh interval is five seconds, with a single refresh in flight per instance.

Operations JWT verification uses the same Nacos service ID and requests the fixed private endpoint
`GET /platform-support/authentication/v1/realms/OPERATIONS/jwks` through a non-blocking WebClient. The Gateway reuses
the `platform-support-api` JWKS DTOs, accepts only the exact public fields `kty`, `kid`, `use`, `key_ops`, `alg`, `n`,
and `e`, and fails closed unless every key is RSA-2048 or stronger with `RS256`, `sig`, and `[verify]`. The document is
limited to 16 keys and 64 KiB. A direct base URI exists only behind the explicit test switch
`gateway.platform-jwks.direct-uri-enabled=true`.

Direct `gateway.platform-snapshot.base-uri` mode exists only for local and automated tests. It requires an empty
`service-id` plus `direct-uri-enabled=true`; discovery and direct mode are mutually exclusive. Deployed configuration
uses the service ID and must not pin a Platform Support IP or instance URI.

Before the first valid Snapshot, liveness remains Up but readiness is Down. After a successful load, connection
failures, timeouts, non-`200` statuses, empty bodies, malformed JSON, unsupported schemas, or invalid catalogs retain
the Last Valid Snapshot and keep readiness Up. A valid `targets=[]` and `routes=[]` response is different from an empty
body: it atomically clears the catalog, remains Ready, and makes all public requests return `404` without calling a
target.

There is no local static-route fallback and no merge with Java route constants, Redis, Nacos Config, files, or a
partially decoded response. Therefore a Platform disable or fully empty catalog cannot be bypassed by a hidden
Operations route. Platform's database owns Target Code, Channel and Discovery Service Name; the code and discovery
name may differ. Each compiled runtime route binds its target from the same immutable snapshot. The transport handler
uses that pinned target's `lb://<discoveryServiceName>` URI and the existing LoadBalancer/discovery scope; it does not
consult a second RouteLocator cache or hardcoded business target/path list. New service names and route bindings take
effect on refresh. Existing authentication, Guard, path validation and safety ceilings remain enforced. For example,
a database target with discovery name `operations-console-bff` resolves to `lb://operations-console-bff`; the name is
configuration, not a Gateway constant.

`spring.cloud.gateway.server.webflux.route-filter-cache-enabled` must remain `false`: transport routes are created per
request, and caching their filter chains would retain request-scoped route objects. Startup rejects enabling this cache.
The approved boundary is [database routing](../agent-docs/decisions/2026-09-11-gateway-database-routing.md). The route body
ceiling remains 65,536 bytes, matching the deployed Operations/Risk catalog; this does not change the separate 16 KiB
JWKS response ceiling. BFF management endpoints use a separate loopback listener and are absent from its business port.


The management listener keeps `management.endpoint.health.show-details=never`. Safe Snapshot diagnostics are instead
exported on its private Prometheus endpoint: valid/Last-Valid state, age since success, consecutive failures, fixed
result and failure-reason states, and Target/Route totals plus the six fixed Channel counts. `gateway.requests`,
`gateway.guard.rejections`, and `gateway.upstream.latency` use only controlled Route Code, Channel, Security Profile and
Guard-dimension tags; raw paths, path variables, user identifiers, query values, and secrets are never metric tags.

## Request shapes

The three Operations authentication request shapes are below. The current PLT-I20 catalog also owns any approved
context, password-change, navigation, account/role and audit routes. Platform may add or disable ordinary routes only
within the precompiled Operations security profiles and valid database-managed targets. Public clients send HTTPS to
Nginx; they must never call the Gateway application listener directly. The placeholders below are request shapes, not
working credentials:

```bash
curl --request POST 'https://operations-console.local.test/api/operations/auth/v1/login' \
  --header 'Origin: https://operations-console.local.test' \
  --header 'Content-Type: application/json' \
  --data '{"loginEmail":"<operator-email>","password":"<password>"}'

curl --request POST 'https://operations-console.local.test/api/operations/auth/v1/refresh' \
  --header 'Origin: https://operations-console.local.test' \
  --header 'Content-Type: application/json' \
  --data '{"refreshToken":"<refresh-token>"}'

curl --request POST 'https://operations-console.local.test/api/operations/auth/v1/logout' \
  --header 'Origin: https://operations-console.local.test' \
  --header 'Authorization: Bearer <access-token>' \
  --header 'Content-Type: application/json' \
  --data '{"refreshToken":"<refresh-token>"}'
```

Login and refresh return JSON with `accessToken`, `tokenType`, `expiresIn`, `refreshToken`,
`refreshExpiresIn`, and the existing authentication flags. Refresh is `PUBLIC` and does not
require Authorization. Logout keeps its `OPERATIONS_JWT` requirement and forwards the JSON
Refresh Token unchanged to BFF. Gateway does not inspect or log Token bodies and does not
implement refresh rotation; Platform owns those rules.

## Trusted BFF context

Every forwarded request receives `X-Request-Id` generated by Gateway and `X-Gateway-Route-Code` from the request's
pinned Snapshot. Browser values for these names are removed. Protected `OPERATIONS_JWT` routes additionally receive:

| Header                       | Validated source                          |
|------------------------------|-------------------------------------------|
| `X-Gateway-Actor-Subject`    | JWT `sub`                                 |
| `X-Gateway-Session-Id`       | JWT `sid`                                 |
| `X-Gateway-Actor-Type`       | `PLATFORM_USER`                           |
| `X-Gateway-Channel`          | `OPERATIONS`                              |
| `X-Gateway-Realm`            | `OPERATIONS`                              |
| `X-Gateway-Context-Type`     | JWT `TENANT` or `BOOTSTRAP`               |
| `X-Gateway-Tenant-Id`        | JWT `tenant_id`, only for `TENANT`        |
| `X-Gateway-Tenant-Type`      | `INTERNAL`, only for `TENANT`             |
| `X-Gateway-Tenant-Member-Id` | JWT `tenant_member_id`, only for `TENANT` |

Operations JWTs require exactly the configured Operations audience. A `TENANT` context requires all three Tenant claims
and `tenant_type=INTERNAL`; a `BOOTSTRAP` context must omit every Tenant claim. `ACCOUNT` is not an Operations context.
Subject, Session, token and Tenant/member identifiers must be nonblank bounded strings without control characters.
Caller-supplied trusted Header names are stripped case-insensitively by whole-name matching and cannot appear in
Snapshot Header allowlists. Public routes receive no identity context, and raw `Authorization` is never forwarded.

BFF passes the trusted Route Code and identity to Platform PLT-I02 for current Account, Session, Tenant, Member,
forced-password-change, Bootstrap and Permission checks. Gateway never invokes PLT-I02 or grants resource permissions
from JWT claims. The owning core system still checks resource access.

Management write routes declare `Idempotency-Key` and `Content-Type` in their CORS and forwarded Header lists;
`Authorization` is CORS-only and `Origin` is forwarded. BFF validates exact Origin and JSON. The Cookie-specific
`X-CSRF-Token` Header is no longer required or forwarded. Operations routes keep Snapshot `idempotencyMode=NONE`: a
command retry keeps its original idempotency key and Gateway forwards it unchanged until the separately scoped
Operations migration. Gateway-owned request idempotency for `REQUIRED` routes is described in
[Request idempotency](#request-idempotency).

Same-origin browser GET requests may omit `Origin`. Gateway treats only GET with no Origin Header as a non-CORS read and
continues through the unchanged JWT, Guard and BFF authorization chain; it does not infer Origin from Host or invent
CORS response permissions. GET with any supplied Origin must match the configured exact Origin. Preflight and every
write method still require that exact Origin. All initialized Operations routes use Snapshot `requestCookieMode=DROP`
and `responseCookieMode=NONE`. Gateway discards every caller Cookie and rejects any upstream `Set-Cookie` as
`502 UPSTREAM_PROTOCOL_ERROR`; shared Cookie modes for other Channels remain available. Password change and
Bootstrap completion revoke Sessions through Platform without issuing or clearing a Cookie.

## Request idempotency

The [Gateway Idempotency-Key requirement](../requirements/20261017/gateway-请求幂等IdempotencyKey/technique.md) owns this
contract under the [2026-09-17 decision](../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md). Snapshot
Route `idempotencyMode` is `NONE` or `REQUIRED`; a missing value from an older Platform compiles as `NONE`, and an
unknown value rejects the whole Snapshot. `REQUIRED` is accepted only for `CLIENT_API` non-`GET`/`HEAD` Routes that do
not forward `idempotency-key`; the Owner never receives the Key.

`IdempotencyGlobalFilter` runs at `HIGHEST_PRECEDENCE + 150`, after authentication, Guard, Scope and the replayable
request body, and before the upstream timeout. Authentication, Scope, Guard and body failures therefore never occupy a
Key. For a `REQUIRED` Route:

- exactly one `Idempotency-Key` of 1–255 characters matching `[A-Za-z0-9][A-Za-z0-9._:-]*` is required; missing returns
  `400 IDEMPOTENCY_KEY_REQUIRED`, duplicate or malformed returns `400 INVALID_IDEMPOTENCY_KEY` (outcome
  `INVALID_REQUEST`), without Redis or Owner calls;
- the scope is Channel + caller: Client API uses the Client ID, so credential rotation replays the same record;
- the request digest is `SHA-256("v1\n" + method + "\n" + routeCode + "\n" + raw path + "\n" + normalized query +
  "\n" ‖ SHA-256(raw body))`, where the query is stably sorted by raw parameter name; JSON is not normalized;
- one Redis Hash `{namespace}:idem:v1:{channel}:{callerKey}:{sha256(key)}` is claimed, completed and released by Lua.
  A new Key is `PENDING` for route response timeout + body receive timeout + `pending-lease-extra`; a completed record
  expires `retention` after it was first accepted;
- same digest while `PENDING` returns `409 IDEMPOTENCY_REQUEST_IN_PROGRESS` with `Retry-After: 3`; a different digest in
  any state returns `409 IDEMPOTENCY_KEY_REUSED` (outcome `IDEMPOTENCY_CONFLICT`); same digest and `COMPLETED` replays
  the record without an Owner call (outcome `IDEMPOTENCY_REPLAYED`);
- a Redis failure on claim returns `503 DEPENDENCY_UNAVAILABLE`; a failure while completing or releasing only logs WARN
  and never changes the response already produced.

Any response the Owner actually returned (2xx/4xx/5xx, after Owner Problem validation) with a body of at most
`max-response-bytes` is recorded with its status, gzip body and only `Content-Type`, `Content-Language`,
`Content-Disposition`, `Content-Encoding`, `ETag`, `Last-Modified`, `Retry-After` and the `201` `Location`. A larger
body is returned unchanged but not recorded. Gateway-generated `502`/`503`/`504`, invalid Owner responses and client
cancellation release the Key, so a retry with the same Key reaches the Owner again and business deduplication decides
the result. A replay rebuilds `X-Request-Id`, `Cache-Control: no-store` and the other terminal headers for the current
request; an `application/problem+json` body has only `requestId` rewritten to the current Request ID, keeping the
original `timestamp`. Key values and request/response bodies are never logged; Redis stores only the digest and the
recorded response.

| Property | Default |
|---|---|
| `gateway.idempotency.retention` | `72h` |
| `gateway.idempotency.pending-lease-extra` | `10s` |
| `gateway.idempotency.max-response-bytes` | `65536` |
| `gateway.idempotency.redis.namespace` | the Guard namespace `gateway.operations.guard.redis.namespace` |

The store is the replaceable `IdempotencyRecordStore`; the planned database migration keeps this interface and the
claim/complete/release semantics. Run Redis with `noeviction` and monitor memory and `idem:v1` key counts.

## Nginx and private-network boundary

Nginx owns public certificates and TLS termination. On every request it must overwrite, rather than append or pass
through, the client-address headers before proxying over private HTTP:

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header Forwarded "";
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Forwarded-Host "";
proxy_set_header X-Forwarded-Proto "";
proxy_pass http://api-gateway-private:18080;
```

Gateway requires exactly one numeric IPv4/IPv6 `X-Real-IP` and uses the parsed address for both Guard stages and the
access-log HMAC digest. Before BFF forwarding it removes the finite, case-insensitive whole-name list `Forwarded`,
`X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Port`, `X-Forwarded-Prefix`, `X-Forwarded-Proto`,
`X-Forwarded-Server`, `X-Forwarded-Ssl` and `X-Real-IP`; near-match names are not treated as trusted proxy headers.
Missing, duplicate, chained or malformed client IP values return `GATEWAY_FORBIDDEN` without a BFF call. Security
Groups, Network Policies or equivalent deployment controls must allow port `18080` only from the private Nginx network
and prevent all direct public or client access. Nginx-to-Gateway, Gateway-to-BFF and
Gateway-to-Platform-Support traffic is private HTTP; the Java applications do not own X.509 certificates.

## Redis Guard state and access-log pseudonymization

Guard state uses `guard:v3` and Redis server time. Client-IP keys contain the canonical address parsed from the trusted
`X-Real-IP`, and authenticated-Subject keys contain the exact validated JWT `sub`; Redis Guard has no HMAC
configuration. Accepted identity components remain byte-for-byte unchanged, are capped at 512 UTF-8 bytes, and reject
blank, control-character or invalid-Unicode input. Rate Hashes contain exactly `t/lr/rr`, and Concurrency metadata
contains exactly `hw/l`. The scripts persist no policy revision and perform no in-place or rolling state migration.
Access logs remain independently HMAC-pseudonymized and never emit the raw Redis identity values.
