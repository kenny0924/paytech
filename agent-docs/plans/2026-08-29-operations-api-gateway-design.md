# Operations API Gateway design

> **2026-09-01 dynamic-control-plane successor:** The [unified public API control-plane design](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) now owns public Target/Route/Guard current configuration. This document remains the implementation authority for the deployed static Operations Gateway before dynamic Snapshot cutover and for its unaffected security hardening; statements that the Catalog is permanently code-owned are pre-cutover history only.

Last updated: 2026-08-31

Status: the initial Gateway boundary remains approved. The routing, trusted-header, request-body, JWKS, Guard and transport portions are superseded by the approved [`2026-08-30-operations-gateway-hardening-design.md`](../../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md). This document records the compatible final contract; the hardening design is authoritative if wording differs. It does not authorize BFF, Platform Support, frontend, deployment or infrastructure work.

## 1. Boundary and routing

The Gateway is public reactive ingress, not a business service. It validates Operations JWTs and enforces the edge protocol/trusted request context only; it does not perform Login, issue tokens, or own current role, resource or business authorization. Browser traffic reaches Platform Support through Operations BFF. BFF and Platform Support retain authorization responsibilities, and core services retain business authority.

The Catalog is static and method-aware. It currently contains exactly these three `POST` routes:

| Route | Method | External contract | Explicit BFF path | Access and refresh-cookie input |
| --- | --- | --- | --- | --- |
| Login | `POST` | `/api/operations/auth/v1/login` | `/auth/v1/login` | anonymous; no refresh cookie |
| Refresh | `POST` | `/api/operations/auth/v1/refresh` | `/auth/v1/refresh` | anonymous; exactly one refresh cookie |
| Logout | `POST` | `/api/operations/auth/v1/logout` | `/auth/v1/logout` | JWT; exactly one refresh cookie |

### Verification contract

- Gateway validates the JWT and enforces only edge protocol and trusted request context.
- Operations BFF and Platform Support retain current authorization; core services retain business authorization.
- The Catalog contains exactly three routes, each with Method `POST`.
- Matching is exact `HttpMethod + rawPath`, requires an empty query, and each Catalog item owns its downstream path.
- There is no wildcard route or prefix-stripping rule.
- No current `GET` route or default future Guard profile exists.

Matching is exact `HttpMethod + rawPath`, requires an empty query, and each Catalog item owns its downstream path. There is no wildcard route or prefix-stripping rule. A reviewed future `GET` Catalog item is possible, but no `GET` route or default future Guard profile exists now. Unknown method/path/query combinations return the local 404 and never reach BFF.

## 2. Browser and trusted-header contract

The public/trusted names are `X-Request-Id`, `X-Gateway-Actor-Subject`, `X-Gateway-Session-Id`, `X-Gateway-Actor-Type`, `X-Gateway-Channel`, and `__Secure-operations-refresh`.

Ingress removes names by case-insensitive whole-name equality only. Its complete finite removal list is:

| Removed trusted name |
| --- |
| `X-Request-Id` |
| `X-Gateway-Actor-Subject` |
| `X-Gateway-Session-Id` |
| `X-Gateway-Actor-Type` |
| `X-Gateway-Channel` |
| `Forwarded` |
| `X-Forwarded-For` |
| `X-Forwarded-Host` |
| `X-Forwarded-Port` |
| `X-Forwarded-Proto` |
| `X-Forwarded-Prefix` |
| `X-Forwarded-Ssl` |
| `X-Forwarded-Server` |
| `X-Real-IP` |

It does not use a forwarding-header wildcard. Upstream construction begins empty and then applies this exact route allowlist:

| Route | Browser request headers | BFF external values | Trusted BFF values |
| --- | --- | --- | --- |
| Login | `Content-Type` | `Origin`, `Content-Type` | `X-Request-Id` |
| Refresh | `Content-Type`, `X-CSRF-Token` | `Origin`, `Content-Type`, `X-CSRF-Token`, one refresh cookie | `X-Request-Id` |
| Logout | `Authorization`, `Content-Type`, `X-CSRF-Token` | `Origin`, `Content-Type`, `X-CSRF-Token`, one refresh cookie | `X-Request-Id` and four `X-Gateway-*` identity headers |

### Verification contract

- Public/trusted names are `X-Request-Id`, `X-Gateway-Actor-Subject`, `X-Gateway-Session-Id`, `X-Gateway-Actor-Type`, `X-Gateway-Channel`, and `__Secure-operations-refresh`.
- Ingress removes names by case-insensitive whole-name equality only.
- Upstream reconstruction starts empty and copies only the exact route allowlist.
- The raw `Authorization` value is not a BFF external value.
- Every BFF request receives Gateway-generated `X-Request-Id`; authenticated Logout also receives four `X-Gateway-*` identity headers.
- The refresh cookie is `Secure`, `HttpOnly`, `SameSite=Strict`, path `/api/operations/auth/v1`, and has no `Domain`.
- Browser Fetch uses credentials `include`; CORS allows an exact Origin and `Access-Control-Allow-Credentials: true`.

The raw `Authorization` value is not a BFF external value. Every BFF request receives Gateway-generated `X-Request-Id`; authenticated Logout also receives the four `X-Gateway-*` identity headers.

The refresh cookie is `Secure`, `HttpOnly`, `SameSite=Strict`, path `/api/operations/auth/v1`, and has no `Domain`. Console and Gateway are schemefully same-site. Browser Fetch uses credentials `include`; CORS allows an exact Origin and `Access-Control-Allow-Credentials: true`.

## 3. Guard and body limits

Every route has a static Guard profile. Rate limiting occurs before immediate Concurrency acquisition; there is no wait or queue. A later Concurrency rejection, JWT rejection, body rejection or downstream failure does not refund an already-consumed Rate token. Every acquired lease releases on success, error or cancellation.

Guard uses the `guard:v3` namespace and Redis server time. Client-IP keys contain the raw canonical socket IP, and authenticated-Subject keys contain the exact validated JWT `sub`; Redis Guard has no HMAC key. Raw identity components are capped at 512 UTF-8 bytes and reject blank, control-character or invalid-Unicode input without transforming accepted values. Rate Hashes contain exactly `t/lr/rr`, while Concurrency metadata contains exactly `hw/l`. No policy revision or rolling state migration is configured or executed. Access logs continue to HMAC-pseudonymize IP and Subject with independent access-log material.

All current routes have a `16 KiB` actual body cap and a `5 s` total aggregation deadline. Validated bytes replay to BFF with exact `Content-Length` and no `Transfer-Encoding`; BFF sees no body before validation. Pooled buffers are released and temporary arrays are best-effort cleared on complete, error and cancellation. Timeout is `408`; oversize is `413`.

### Verification contract

- Every route has a static Guard profile.
- Rate limiting occurs before immediate Concurrency acquisition; there is no wait or queue.
- A later Concurrency rejection, JWT rejection, body rejection or downstream failure does not refund an already-consumed Rate token.
- Every acquired lease releases on success, error or cancellation.
- Guard uses the `guard:v3` namespace and Redis server time.
- Client-IP keys use the raw canonical socket IP; authenticated-Subject keys use the exact validated JWT `sub`.
- Redis Guard has no HMAC configuration; Access Log IP and Subject values remain independently HMAC-pseudonymized.
- Rate Hashes contain exactly `t/lr/rr`; Concurrency metadata contains exactly `hw/l`.
- No policy revision or rolling state migration is configured or executed.
- All current routes have a `16 KiB` actual body cap and a `5 s` total aggregation deadline.
- Validated bytes replay to BFF with exact `Content-Length` and no `Transfer-Encoding`; BFF sees no body before validation.
- Pooled buffers are released and temporary arrays are best-effort cleared on complete, error and cancellation.
- Timeout is `408`; oversize is `413`.

## 4. JWT/JWKS and transport envelope

Gateway accepts only `RS256`, with `kid` matching `^[A-Za-z0-9._:-]{1,128}$`; invalid input causes no JWKS call. Refresh uses one global in-flight operation and one global five-second cooldown, never per-`kid` state. Known cached keys stay usable. A definitive unknown after success is `401`; a required failed/cooldown refresh is `503`.

JWKS uses constrained HTTPS with no userinfo, query, fragment or redirect; connect is `2 s`, response `3 s`, body `64 KiB`, at most 16 valid RSA-2048 keys, and cache age five minutes.

Production/default ingress is `PUBLIC_TLS`, limited to TLS 1.3/1.2 and the approved seven suites, with HTTP/2 disabled. Test-only cleartext binds numeric `127.0.0.1`. Request line is `8 KiB`, aggregate headers `32 KiB`, parsed Authorization and Cookie are each `8 KiB`, Origin and each CORS control header are `2 KiB`, header/read-idle deadline is `5 s`, connection cap is 1000, backlog is 512, and keepalive requests are capped at 1000. Parsed oversize headers return `431 GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE`; a decoder-level rejection may close before an exchange exists.

BFF transport uses a fixed pool of 1000, acquire/connect timeouts of `2 s`, a complete-chain timeout of `10 s`, no redirects and no Reactor retry. Finite type-based classification maps BFF TLS/protocol/invalid response to `502`, connect/pool/DNS to `503`, and complete timeout to `504`, without exception details.

### Verification contract

- Gateway accepts only `RS256`, with `kid` matching `^[A-Za-z0-9._:-]{1,128}$`; invalid input causes no JWKS call.
- Refresh uses one global in-flight operation and one global five-second cooldown, never per-`kid` state.
- Known cached keys remain usable.
- A definitive unknown after success is `401`; a required failed/cooldown refresh is `503`.
- JWKS uses constrained HTTPS with no userinfo, query, fragment or redirect; connect is `2 s`, response `3 s`, body `64 KiB`, at most 16 valid RSA-2048 keys, and cache age five minutes.
- Production/default ingress is `PUBLIC_TLS`, limited to TLS 1.3/1.2 and the approved seven suites, with HTTP/2 disabled.
- Test-only cleartext binds numeric `127.0.0.1`.
- Request line is `8 KiB`, aggregate headers `32 KiB`, parsed Authorization and Cookie are each `8 KiB`, Origin and each CORS control header are `2 KiB`, header/read-idle deadline is `5 s`, connection cap is 1000, backlog is 512, and keepalive requests are capped at 1000.
- Parsed oversize headers return `431 GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE`; a decoder-level rejection may close before an exchange exists.
- BFF transport uses a fixed pool of 1000, acquire/connect timeouts of `2 s`, a complete-chain timeout of `10 s`, no redirects and no Reactor retry.
- Finite type-based classification maps BFF TLS/protocol/invalid response to `502`, connect/pool/DNS to `503`, and complete timeout to `504`, without exception details.

## 5. Runtime and test boundary

Gateway uses Reactive Redis/Lettuce Lua and the repository Compose Redis at `127.0.0.1:16379`; automated tests use a unique Namespace and clean only that Namespace with `SCAN` plus `UNLINK`/`DEL`. Tests use `PAYTECH_REDIS_PASSWORD`, do not start Docker/Testcontainers, do not use port `6379`, and never mutate the existing service. `.env.local` is untracked: before a future Compose recreation, a developer manually copies the existing secret value to its new key.

The static Catalog, real Gateway chain, stateful BFF/JWKS simulators and Redis precondition remain the required auto test boundary. No test-only fourth route, dynamic route repository, token issuance, BFF authorization replacement or production infrastructure provisioning is introduced by this Gateway work.
