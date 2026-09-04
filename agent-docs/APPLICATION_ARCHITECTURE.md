# Code-MoR Entry Application Architecture

> **2026-09-04 Operations 浏览器契约更新：** 按用户最新要求，Operations 使用 JSON 传递 Refresh Token，登录/刷新返回 Access/Refresh 两 Token；前端通过当前标签页的 sessionStorage 保存两 Token 及绝对过期时刻，在模块启动时恢复，整页刷新不再要求重新登录。该决定覆盖此前仅内存限制；不使用 localStorage 或 Cookie，不缓存密码、Account 上下文或权限。有效 Access 继续用于 Authorization，临近到期或 GET 首次 401 时可 JSON Refresh；轮换一起更新两 Token，退出、改密完成、Refresh 401、刷新后的 GET 唯一一次重试仍 401 或会话到期时清理。临时 429/503/网络故障保留会话并提供恢复重试。BFF 统一 HTTP 入站边界仍负责 PLT-I02，Controller 委托本地 Client Port；Client/Partner 契约及业务所有权不变。详见 [第一版实施记录中的后续决定](./plans/2026-09-04-operations-console-first-slice.md)，本次前端 32 项测试与生产构建通过，已部署 mor-test，真实浏览器连续两次整页刷新保持登录、退出后刷新保持登录页；无后端或数据库改动。


> **2026-09-04 Operations 当前增量：** [第一版实施记录](./plans/2026-09-04-operations-console-first-slice.md) 更新本页此前“BFF 仅基础 / Console 前端和认证未实现”的状态：独立 React/TypeScript/Vite/Ant Design 前端与 BFF 浏览器适配已增加；Platform 增加显式启用的 Operations 认证/IAM/人员/审计代码和 22 表手工 SQL 投影；Gateway 已有动态 Snapshot，并扩展可信 Context 和四个 BFF 下游根。Vault 按用户要求暂用隔离开发 Mock + TODO，生产 Vault/KMS、手工 Bootstrap/Key 初始化、真实 MySQL/Redis 联调和部署仍需各自前置。下文旧基础阶段的实现范围描述在被本记录更新处仅保留历史意义，架构所有权与手工基础设施规则不变。


Last updated: 2026-09-04

Status: current architecture baseline for subsequent design sessions. The Platform Support database model for the unified public API control plane is approved and generated; its `api`/`web`/`test` Java reactor now implements the Phase A persistence/initialization slice, PLT-I20 full typed Snapshot provider, Operations-only PLT-I22 JWKS provider and Nacos Discovery registration. Operations BFF business capabilities, Console frontends, Client/Partner BFFs, login/token-signing and other authentication/Tenant IAM commands, CUSTOMER JWKS, control-plane management APIs and the dynamic Gateway Snapshot/JWKS cutover remain unimplemented; the existing Operations Gateway first slice remains the deployed Gateway runtime implementation.

The [`operations-console-bff`](../operations-console-bff/README.md) application foundation is now initialized as an independent Web/test Maven repository. It adds Spring MVC, shared Foundation HTTP behavior, Actuator/Prometheus and Nacos Discovery only; no business endpoint or authentication/authorization implementation is present.

## 1. Purpose

This document maps logical entry applications to frontend projects, Java deployables, Gateway routes, and core systems. It prevents the incorrect assumption that every entry application must become a separate Java service or server.

Five concepts must remain distinct:

- **Entry Application**: a user or machine access channel, such as Hosted Checkout, Client Console, or Client API.
- **Core System**: a business boundary and source of truth. Code-MoR has exactly six.
- **Deployable Service**: an independently running backend process.
- **Supporting Technical Service**: an independently secured infrastructure capability, such as Vault, that protects or transports data without owning its business meaning.
- **Runtime Instance / Server**: infrastructure that runs one or more service instances.

These concepts are not one-to-one. Several entry applications may share one frontend or BFF, and several Java services may run on the same container hosts.

## 2. Backend and frontend project baseline

### Existing core backend project roots

```text
client-contract-service/
commerce-service/
payment-orchestration-service/
risk-compliance-service/
finance-service/
platform-support-service/
api-gateway/
```

`platform-support-service/` contains the tested Maven/application foundation described by the [Platform Support application-foundation design](../docs/superpowers/specs/2026-09-01-platform-support-application-foundation-design.md), plus a manually maintained SQL/MyBatis 21-table Phase A projection, private-HTTP PLT-I20 full-Snapshot provider, Operations PLT-I22 JWKS provider and Nacos Discovery registration. The [manual-infrastructure simplification](../docs/superpowers/specs/2026-09-02-manual-infrastructure-and-platform-runtime-simplification.md) supersedes its earlier Flyway/Testcontainers/runtime-seed mechanism, while the [MVC/internal-HTTP design](../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md) supersedes its application mTLS transport. Platform now has no Spring Security/X.509/mTLS or local exception handler and reuses the Foundation MVC boundary. PLT-I22 publishes only Operations public verification keys; it does not implement login or private-key signing. The other five core backend roots still contain database design artifacts without Java application modules. This implementation does not imply that remaining Platform capabilities are available.

### Entry-layer backend project roots

```text
operations-console-bff/  # Internal Operations personnel; application foundation initialized
client-console-bff/      # Client
partner-console-bff/     # Local Partner
```

### Supporting technical service roots

```text
vault-service/           # Central non-card sensitive-data protection; approved logical target, not yet created
```

Vault is not a seventh core business system. It owns encrypted value records, cryptographic metadata and sensitive-access evidence, while the calling core system remains the source of truth for the Party, Order, Payment, Bank Account or other business fact represented by the value.

### Frontend project roots

```text
checkout-web/            # Consumer Hosted Checkout and Service Portal
operations-console-web/ # Internal Operations personnel
client-console-web/      # Client
partner-console-web/     # Local Partner
```

`checkout-web/` and the historical empty placeholders `business-portal-web/` / `business-portal-bff/` currently exist. The `business-portal-*` names are retired as target architecture; do not add implementation under them. The Operations BFF application foundation now exists. The Operations frontend and Client/Partner frontend/BFF roots remain unimplemented. The Vault root contains database-design artifacts only. None of these naming decisions authorizes additional runtime capabilities.

Do not create `client-api-service` or `provider-callback-service`. Client API is a logical external API surface routed to the owning core systems. Provider callbacks belong to Payment Orchestration.

The current logical backend target is six core services, one API Gateway, three separately secured Console BFFs and one internal Vault: eleven Java deployable types. This is a target boundary, not a mandate to run or scaffold every deployable now. The Operations channel is required by MVP onboarding, refund, dispute, reconciliation and settlement flows; current Java implementation includes its Gateway slice, Platform Support Phase A plus Operations JWKS query and the Operations BFF application foundation. Frontend applications and the embedded `compensation-event` JAR are not deployable services.

## 3. What BFF means

**BFF** means **Backend for Frontend**. Every `*-bff` is a stateless frontend aggregation layer. It owns the query composition and response assembly needed by its frontend, while core systems own their business domains. Page-specific assembly must stay in the BFF rather than being pushed into core services for frontend convenience.

For Code-MoR, each authenticated Console has its own BFF between API Gateway and the authorized core-system interfaces:

```text
<audience>-console-web
  -> API Gateway
    -> <audience>-console-bff
      -> Platform Support identity/authorization where applicable
      -> authorized Client & Contract / Commerce / Payment / Risk / Finance / Platform Support interfaces
```

`<audience>` in the project-name template is exactly `operations`, `client`, or `partner`; it is not the JWT `aud` claim. JWT Audience is respectively `operations-console`、`client-console` or `partner-console`. Platform owns the global Account, Realm exactly `{OPERATIONS, CUSTOMER}`, and Channel exactly `{OPERATIONS, CLIENT, PARTNER}`. Operations uses OPERATIONS Realm/Channel; Client and Partner use CUSTOMER Realm with their respective CLIENT or PARTNER Channel. Tenant Member authorizes an Account within one Platform Tenant. CCT creates Client/Partner Applications before the Tenant projection exists, and Risk owns the typed source namespace for the qualification Case. The BFFs may share libraries later, but they do not share a public deployable or security boundary by default.

The BFF is responsible for:

- receive authenticated Client, Partner, user, and permission context from the edge;
- aggregate read-only data from multiple core-system interfaces for one page;
- transform domain responses into page-oriented response objects;
- forward a command to the one core system that owns that command;
- normalize presentation-level errors and pagination without changing business meaning.

Actual multi-interface query composition and page response assembly belong in the BFF's application/Processor layer, using local Client Ports to access authorized core interfaces. Controllers retain protocol adaptation and integration adapters retain individual remote-contract adaptation. The rule that removes empty forwarding Processors does not remove a layer with real aggregation responsibilities; simple forwarding still needs no empty orchestration layer. Core APIs should expose reusable domain capabilities without taking ownership of a particular Console's page model.

The BFF must not:

- own orders, payments, balances, ledger entries, contracts, MIDs, or other core facts;
- have a business source-of-truth database;
- calculate fees, partner shares, reserves, or settlement amounts;
- decide risk, routing, payment, refund, or settlement outcomes;
- orchestrate a cross-system business transaction;
- mutate a second core system to compensate for the failure of another system.

This prohibition is literal for cancel, void and reversal: neither BFF may call Payment and Finance as steps of one browser/API command. The BFF forwards the single owning Commerce command; Commerce owns the command lifecycle and internally invokes the authorized domain flow.

Example of query aggregation owned by the BFF:

```text
Transaction detail page
  -> BFF queries Commerce, Payment, and Finance
  -> BFF combines their authorized views into one page response
```

Cross-system business transaction orchestration remains with the owning core system:

```text
Refund command
  -> BFF sends one command to Commerce
  -> Commerce owns refund intent and calls Payment internally
  -> Payment executes the provider refund
```

Cancel and Reversal remain separate Commerce commands. `COM-I05 Cancel` calls Payment `PAY-I12` only to prove that no successful Authorization/Capture/Sale or unresolved Provider result exists and to terminate the Intent; it never creates a VOID or Refund and never silently upgrades itself. Operations may separately send `COM-I08 Reversal`; Commerce calls `PAY-I13`, and Payment atomically resolves the authoritative branch under lock. An uncaptured successful Authorization creates a precise-target VOID, while SALE/full Capture returns `REFUND_REQUIRED` so Commerce can create the normal Refund Request and continue through the Commerce Order Refund Position and Payment Channel Refund chain. Finance consumes only `RefundSucceeded` after Provider success. Gateway and BFF never coordinate Payment and Finance business mutations, and an unsolicited post-capture reversal or a reversal of a successful refund is an internal exception workflow, not an entry-layer command.

The BFF is different from API Gateway:

- **Gateway** handles external ingress, minimal channel identity validation, Route Scope enforcement, rate limiting, routing, request IDs, and access logs. 所有外部 HTTP API 都由 Platform Support 的公共 Route Catalog 统一维护，覆盖 `OPERATIONS`, `CLIENT`, `PARTNER`, `CHECKOUT`, `CLIENT_API` and `PROVIDER_CALLBACK`. Gateway validates and compiles the global immutable Platform Snapshot, while retaining ownership of the Target Registry, security profiles, protocol limits and Redis runtime state. It does not evaluate `PLT-I02`, resource ownership or page use cases.
- **BFF** owns frontend response shapes, authorized query composition and page model assembly. It applies entry authorization and preserves upstream resource authorization; domain decisions remain with core systems.
- **Core systems** own business rules, state transitions, data, and cross-system domain workflows.

公网 TLS 由 Nginx 终止；Nginx 覆盖一个 `X-Real-IP` 后仅通过私网访问 Gateway。Gateway 只解析该值一次供 Guard 和 Access Log 共用，并在转发 BFF 前删除代理 Header。Gateway、BFF、Platform Support、Vault 与核心 Java 服务通过私有 HTTP 通信且不持有 X.509 应用证书。内部服务同步 HTTP、MQ、XXL-Job 与 Vault 调用绕过 Gateway，使用服务发现、部署网络限制和各自的内部契约，不进入公共 Route Catalog。Platform 已实现手工 Schema/初始化 DML、PLT-I20 全量 Snapshot Provider、Operations PLT-I22 JWKS Provider 与 Nacos Discovery 注册；Gateway 动态消费与运行态切换尚未实现。已部署 Gateway 在切换前继续使用静态 Operations Catalog 和测试 JWKS Simulator，不能据此把静态目录解释为新的长期控制面。

## 4. Browser-facing access model

### Hosted Checkout and Consumer Service Portal

`checkout-web` primarily calls the Commerce Checkout API through Gateway:

```text
checkout-web
  -> API Gateway
    -> Commerce Checkout API
      -> Risk & Compliance (internal synchronous decision)
      -> Payment Orchestration (internal payment command)
      -> Platform Support (internal document/support capability)
```

Suggested route family:

```text
/checkout/v1/sessions/**
/checkout/v1/orders/**
/checkout/v1/payment-actions/**
/checkout/v1/documents/**
```

Commerce is the external owner of the Checkout flow. The browser must not call Risk, Finance, Client & Contract or Vault directly. Commerce owns the single cancel command; Gateway/Checkout Web do not call Payment to void an authorization or Finance to reverse funds. Card entry and tokenization use the selected Provider SDK or Hosted Fields so PAN, CVV/CVC, full track data and PIN data do not enter a Code-MoR backend or Vault. Payment receives only the Provider token and approved card descriptors.

### Client Console

The Client experience uses its own projects:

```text
client-console-web
  -> API Gateway
    -> client-console-bff
      -> authorized core-system interfaces
```

Its route families use the channel-specific Customer Realm contract from the [unified human-authentication design](../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md). Client and Partner use separate audiences, BFFs and cookies; Operations remains Password-only. Gateway validates only the minimal Claim matrix, while Platform evaluates current membership/IAM and each core system verifies resource ownership.

### Partner Console

The Local Partner experience also uses a separate boundary:

```text
partner-console-web
  -> API Gateway
    -> partner-console-bff
      -> authorized read-only transaction views and partner-owned capabilities
```

Partner transaction access remains read-only. It cannot operate orders, payments, refunds, routing, MIDs or ledger records. Client/Partner source code may later share packages, but their deployables, identities and permissions remain distinct; hiding a frontend control is never authorization.

### Operations Console

The high-privilege internal console uses a separate BFF:

```text
operations-console-web
  -> API Gateway
    -> operations-console-bff
      -> Platform Support internal authentication/authorization interfaces
      -> authorized core-system interfaces
```

The only currently deployed public Operations routes are the three exact authentication paths retained by the [Operations Gateway hardening design](../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md); unaffected parts of the [2026-08-29 first-slice design](./plans/2026-08-29-operations-api-gateway-design.md) remain in force until dynamic Snapshot cutover. Gateway removes `/api/operations` and forwards only to `operations-console-bff`; it never routes Operations browser requests directly to Platform Support or a core system. Platform Support owns internal Operations accounts and Token issuance, while Gateway validates the Access JWT and the BFF is responsible for current Role/Permission/Resource authorization when that capability is implemented. Any later Operations route uses the same Platform-owned public Route Catalog and `OPERATIONS` channel rather than another static control-plane design.

Do not share Client or Partner BFFs with Operations. Operations has broader permissions, maker-checker controls and sensitive audit requirements. The Operations BFF now has only its application foundation; its business APIs, login, IAM and current Role/Permission/Resource checks remain unimplemented, as does the frontend. The new application name is exactly `operations-console-bff`, matching Gateway's current Approved Target and `lb://operations-console-bff` routing identity. It has no Servlet context path, so a later approved `/auth/v1/*` implementation can match the downstream contract directly. Gateway and BFF must use the same Nacos namespace. This initialization does not make the authentication routes operational. Before adding them, the narrower authentication prohibition on logging credentials and token bodies must be satisfied; do not add a duplicate service-local logging Filter.

## 5. Portal command and query ownership

| Portal operation | BFF target / business owner |
|---|---|
| Client onboarding and profile | Client & Contract |
| Client transaction eligibility | Client & Contract view, supported by Risk and Payment facts |
| Client order and transaction history | Commerce, with authorized Payment/Finance query enrichment |
| Client order cancellation; full or partial refund | Commerce command only |
| Operations payment Reversal | Commerce `COM-I08` command only; Payment branch selection is internal |
| Client balance, statement, and Manual Payout request | Finance |
| Partner entity submission | Client & Contract |
| Partner entity and MID query | Client & Contract plus read-only Payment query |
| Partner transaction query | Read-only Commerce/Payment views |
| Partner revenue share and payout request | Finance |
| API key, webhook, team, and notification settings | Platform Support |

Partner access remains read-only for transactions. It cannot operate orders, payments, refunds, routing, MIDs, or ledger records.

## 6. Machine-facing access model

### Client API

Client API is the collective name for stable external contracts, not an independent Java service. Every Client API route is maintained in Platform Support with channel `CLIENT_API`, authentication profile `CLIENT_API_CREDENTIAL`, authorization mode `API_SCOPE` and an explicit `required_scope_code`. After validating the global immutable Snapshot and credential scope, Gateway routes the request to the owning core system, which retains resource-level authorization:

| External route family | Owning backend |
|---|---|
| `/api/v1/checkouts/**` | Commerce |
| `/api/v1/orders/**` | Commerce |
| `/api/v1/refunds/**` | Commerce |
| `/api/v1/balances/**` | Finance |
| `/api/v1/payouts/**` | Finance |
| `/api/v1/webhooks/**` | Platform Support |
| `/api/v1/client-profile/**` | Client & Contract, if externally required |

Gateway performs edge controls only. A Client API credential belongs to a Tenant, but the trusted Tenant ID contributes only to the runtime rate/concurrency key; it does not select a tenant-specific Route or Guard configuration. External-contract versioning and resource-level authorization remain the responsibility of the owning system's external HTTP adapter.

### Provider callbacks

Provider callbacks route directly through Gateway to Payment Orchestration:

```text
Provider / Acquirer
  -> API Gateway
    -> Payment Orchestration callback interface
```

Suggested route family:

```text
/callbacks/v1/providers/{providerCode}/**
```

Payment Orchestration verifies signatures, deduplicates messages, maps external states, and publishes normalized MQ facts. When investigation or reconciliation truly requires the original provider interaction, it may retain only a schema-validated, card-data/Secret scan-passed, encrypted raw envelope in controlled object storage. Unexpected PAN, SAD or Secret content must be rejected from persistence and logs and must trigger a security event; “retain original payload” is never an unconditional rule. It is not a separate callback service.

### Vault internal access

Vault has no browser or external machine route:

```text
Core System
  -> internal Spring HTTP Service Client
    -> deployment-restricted private HTTP
      -> required X-Caller-Service + static Allowlist (current weak V1 trust)
      -> Vault internal API
        -> Vault database + KMS
```

The caller sends a Purpose, Reason, business resource reference and minimum requested field set. Vault returns an opaque Token or a purpose-scoped minimum Reveal response and emits a sensitive-access audit fact to Platform Support. Gateway and BFF cannot proxy a generic Reveal endpoint. A user-facing screen must call its owning core system, which performs resource authorization and requests only the fields required for that use case.

The `compensation-event` component has no network endpoint. It runs inside each state-owning service and accesses only that service's local table; XXL-Job wakes the registered service Handler rather than reading the table centrally.

## 7. Direct Gateway exposure matrix

| Backend | Browser access through Gateway | Machine access through Gateway | Exposure decision |
|---|---|---|---|
| Client Console BFF | Client Console only | No | Planned; unified auth boundary is designed, runtime implementation remains unauthorized |
| Partner Console BFF | Partner Console only | No | Planned; unified auth boundary is designed, runtime implementation remains unauthorized |
| Operations Console BFF | Operations Console only | No | Application foundation initialized; authentication and business endpoints remain unimplemented |
| Commerce | Scoped Checkout endpoints | Client System | Exposed |
| Payment Orchestration | No general browser API | Provider callbacks; narrowly scoped Client API if required | Restricted exposure |
| Finance | Browser uses BFF | Client System | Exposed for machine API |
| Client & Contract | Browser uses BFF | Optional Client profile API | Expose only required machine endpoints |
| Platform Support | Browser uses BFF | API key/Webhook management where required | Restricted exposure |
| Risk & Compliance | No | Internal by default | Not externally exposed |
| Vault | No | Internal service calls only | Never exposed through Gateway or BFF |

The fact that a service is reachable through Gateway does not make every interface public. Each backend separates external interfaces from internal service interfaces.

## 8. Deployment and resource rules

- A Java service does not require a dedicated physical server. Multiple service containers may share the same runtime nodes.
- Frontend projects may be built as static assets or use a lightweight frontend runtime; they are not Java services.
- Console BFFs are stateless and initially need no business database. Scale each independently only when traffic requires it.
- MQ consumers belonging to a core system remain in that system's deployable initially; do not create separate worker services without a scaling or isolation reason.
- Each state-owning service embeds the hardened `compensation-event` library and stores its events in its own database. Transaction `afterCommit` performs the normal wake-up; XXL-Job provides per-service fallback scanning or sharding, while database Claim/Lease protects multi-instance execution. Do not create a central compensation worker or database.
- Vault is a separately secured internal deployable and datastore because its threat boundary, KMS permissions and sensitive-access controls differ from the six business systems. It is called through a deployment-restricted private HTTP Spring `@HttpExchange` contract and is never routed through the external Gateway. Current V1 has no application certificate or strong workload identity: its required but spoofable `X-Caller-Service` plus static Allowlist prevents only accidental misuse.
- A future Maven module named `*-api` is a lightweight internal HTTP-contract JAR containing `@HttpExchange` interfaces and transport DTOs; it is not a separately running process. The Spring Boot `*-web` module is normally the deployable unit.
- Internal callers use Spring HTTP Service Clients with Spring Cloud LoadBalancer and Nacos discovery. They do not route service-to-service calls through Gateway and must not introduce new Dubbo or OpenFeign contracts.
- API Gateway uses Spring Cloud Gateway Server WebFlux. For the Operations first slice it validates the Platform Support Operations Access JWT and uses non-blocking Spring Data Redis Reactive + Lettuce Lua scripts for atomic multi-dimension Token Bucket and lease-based concurrency controls. It does not depend on `paytech-redis-starter`, Redisson or a blocking Redis template.
- Confirmed framework, middleware and local-machine decisions are in `TECHNOLOGY_STACK.md` and `LOCAL_DEVELOPMENT_ENVIRONMENT.md`. The local hybrid topology is a development arrangement, not a production deployment topology.
- Split an entry adapter into another service only for demonstrated security isolation, independent scaling, failure isolation, or release-cadence needs.

## 9. Current build order

1. Keep the six core project roots and API Gateway project root.
2. Keep `checkout-web` untouched. Treat empty `business-portal-web` and `business-portal-bff` as retired historical placeholders; do not implement under them or delete them without a separate cleanup decision.
3. Do not create standalone Client API or Provider Callback projects.
4. Keep the Operations BFF implementation limited to the initialized Web/test application foundation. Defer its business/authentication capabilities, `operations-console-web`, `client-console-*` and `partner-console-*` implementation until explicitly requested.
5. Do not scaffold `vault-service` until implementation is explicitly requested; when it is created, follow `VAULT_SERVICE_ARCHITECTURE.md` and keep the Code-PMX Vault repository as a reference rather than copying its current security assumptions unchanged.
