# Code-MoR Entry Application Architecture

Last updated: 2026-09-05

Status: confirmed application and deployment-boundary architecture. Current implementation, verification and environment rollout are maintained only in [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md); this document does not preserve component status snapshots.

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

### Core backend project roots

```text
client-contract-service/
commerce-service/
payment-orchestration-service/
risk-compliance-service/
finance-service/
platform-support-service/
api-gateway/
```

Each core backend root owns one business-system boundary and database. Its current code, SQL and verification status is intentionally not repeated here; use the current status ledger and the repository README. The [manual-infrastructure simplification](../docs/superpowers/specs/2026-09-02-manual-infrastructure-and-platform-runtime-simplification.md) supersedes earlier Flyway/Testcontainers/runtime-seed mechanisms, while the [MVC/internal-HTTP design](../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md) supersedes application mTLS transport.

### Entry-layer backend project roots

```text
operations-console-bff/  # Internal Operations personnel
client-console-bff/      # Client
partner-console-bff/     # Local Partner
```

### Supporting technical service roots

```text
vault-service/           # Central non-card sensitive-data protection; Go Server + Java Client
```

Vault is not a seventh core business system. It owns encrypted value records, cryptographic metadata and sensitive-access evidence, while the calling core system remains the source of truth for the Party, Order, Payment, Bank Account or other business fact represented by the value.

### Frontend project roots

```text
checkout-web/            # Consumer Hosted Checkout and Service Portal
operations-console-web/ # Internal Operations personnel
client-console-web/      # Client
partner-console-web/     # Local Partner
```

`business-portal-web/` / `business-portal-bff/` are retired names; do not add implementation under them. Project-root existence and implementation state are tracked separately in the status ledger. None of these naming decisions authorizes additional runtime capabilities.

Do not create `client-api-service` or `provider-callback-service`. Client API is a logical external API surface routed to the owning core systems. Provider callbacks belong to Payment Orchestration.

The logical backend target is six core Java services, one Java API Gateway, three separately secured Java Console BFFs and one internal Go Vault: eleven deployable types. This is a target boundary, not a mandate to run or scaffold every deployable. Frontend applications and the embedded `compensation-event` JAR are not backend deployable services.

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

公网 TLS 由 Nginx 终止；Nginx 覆盖一个 `X-Real-IP` 后仅通过私网访问 Gateway。Gateway 只解析该值一次供 Guard 和 Access Log 共用，并在转发 BFF 前删除代理 Header。Gateway、BFF、Platform Support、Vault 与核心 Java 服务通过私有 HTTP 通信且不持有 X.509 应用证书。内部服务同步 HTTP、MQ、XXL-Job 与 Vault 调用绕过 Gateway，使用服务发现、部署网络限制和各自的内部契约，不进入公共 Route Catalog。Gateway 必须消费经全局校验的不可变动态 Snapshot；各环境是否已经切换、是否暂时保留静态 Catalog，只由当前状态台账和环境运行手册记录。

## 4. Browser-facing access model

### Hosted Checkout and Consumer Service Portal

`checkout-web` uses one public Checkout path family with operations routed to their business Owners:

```text
checkout-web -> API Gateway
  -> Commerce: GET checkout, PUT consumer, POST preparation
  -> Payment: POST payments (card + legalConfirmation), GET original submission
       -> Commerce: internal access / legal confirmation / confirmation query
       -> NI: original payment execution, outside database transactions
```

The [Customer Checkout requirement](../requirements/20260912/commerce-CustomerCheckout接口设计/technique.md) retains the four basic paths under `/customer-gateway-api/v1/checkouts/{checkoutToken}`. The confirmed [Payment Card amendment](../requirements/20260912/payment-自建CardVault/requirement.md) moves only the public `POST .../payments` Owner to Payment: one request carries both card input and the current legal confirmation. Payment calls Commerce with only the confirmation and trusted request evidence. Commerce owns the three immutable acceptance records and their network evidence, commits them locally and returns; it never calls Payment back to register or execute this submission. Payment then continues the original payment outside database transactions. The [API/data draft](../requirements/20260912/payment-自建CardVault/api-contract.md) also defines one Payment submission query, for five public operations in total. The earlier separate public `/card` and internal Payment registration proposals are superseded.

Commerce owns order, Consumer, preparation and legal facts, and returns payment summaries; Payment owns submission, card handling, execution and constrained NI 3DS instructions. Legal documents remain frozen-file links; Customer inputs do not carry an Order version. The trusted Channel is CHECKOUT, unrelated to Platform's human CUSTOMER Realm. No separate Checkout BFF or Gateway business orchestration is introduced. One Pay request does not create a distributed transaction: accepted terms can remain valid after payment failure, while retries preserve the original command and prevent duplicate funds actions.

The Payment Card business module owns collection handling, encrypted card storage, platform tokens and controlled Provider card use. NI remains the first acquiring Provider. A Maven module is not proof of runtime isolation or PCI compliance; actual card-data controls and deployment follow the linked design. Public Gateway mapping and activation remain separate delivery work.

Earlier suggested route families (historical examples; do not register them alongside the confirmed Customer family):

```text
/checkout/v1/sessions/**
/checkout/v1/orders/**
/checkout/v1/payment-actions/**
/checkout/v1/documents/**
```

Commerce remains the Owner of Checkout order/Consumer/legal facts; Payment is the public payment-submission Owner for this confirmed Card use case. The browser must not call Risk, Finance, Client & Contract or Vault directly. Commerce owns cancellation; Gateway/Checkout Web do not call Payment to void an authorization or Finance to reverse funds. Raw card input is confined to the approved Payment entry and Card module; Commerce and the existing non-card Vault do not receive it. CVV remains transient, and full track/PIN data remain excluded.

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

All public Operations routes belong to the Platform-owned Route Catalog and `OPERATIONS` channel. Gateway removes the public prefix and forwards only to `operations-console-bff`; it never routes an Operations browser request directly to Platform Support or a core system. Platform owns internal Operations accounts and Token issuance, Gateway validates the Access JWT, and the BFF performs PLT-I02/current Role-Permission-Resource authorization before adapting protected requests. Exact deployed Routes remain an environment status fact.

Do not share Client or Partner BFFs with Operations. Operations has broader permissions, maker-checker controls and sensitive audit requirements. The application name remains `operations-console-bff`; Platform Target data supplies the Discovery Service Name used to derive the load-balanced URI, without a second static business registry in Gateway (see the [database routing decision](./decisions/2026-09-11-gateway-database-routing.md)). Operations BFF business and management listeners are isolated; it has no Servlet context path, and Gateway/BFF use the same Nacos Namespace. Authentication and personnel-sensitive paths use the shared metadata-only logging boundary; never add a duplicate service-local logging Filter. Current endpoint coverage is recorded outside this architecture document.

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

The first confirmed product slice is the Commerce-owned Hosted Checkout V1. Its exact ten-route Manifest, five Scopes, Wire schema, Commerce idempotency and activation gates are owned by the [Commerce Client API V1 design](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md); the family table below remains only future routing context and must not be treated as permission to expose Finance, Platform or DirectPay APIs.

| Illustrative external route family (not a frozen Route Manifest) | Owning backend |
|---|---|
| `/api/v1/checkouts/**` | Commerce |
| `/api/v1/orders/**` | Commerce |
| `/api/v1/refunds/**` | Commerce |
| `/api/v1/balances/**` | Finance |
| `/api/v1/payouts/**` | Finance |
| `/api/v1/webhooks/**` | Platform Support |
| `/api/v1/client-profile/**` | Client & Contract, if externally required |

Gateway performs edge controls only. A Client API credential belongs to a Tenant, but the trusted Tenant ID contributes only to the runtime rate/concurrency key; it does not select a tenant-specific Route or Guard configuration. External-contract versioning and resource-level authorization remain the responsibility of the owning system's external HTTP adapter.

The dedicated Client API domain determines the trusted `CLIENT_API` channel at Nginx; callers do not submit Channel, Client ID, Tenant ID, Credential ID, Scope or Gateway context. Gateway validates the opaque Credential through PLT-I03, uses the returned Tenant ID only for its Tenant Guard, and forwards only `X-Request-Id`, `X-Gateway-Client-Id` and `X-Gateway-Credential-Id` as trusted Client API identity headers. Route lookup is keyed by trusted Channel + Method + Path, so identical paths may exist in another Channel while overlaps remain forbidden within one Channel. The complete edge contract is the [Client API Gateway design](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md); the current Commerce business contract is the [Hosted Checkout V1 design](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md).

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

Vault V1 accepts only the approved Protect/Reveal contract: the private caller supplies required request headers and a Purpose-bound request body, Protect returns an opaque Token and Reveal returns the protected Value. V1 records sensitive access in its own database; it does not accept Reason/Resource/minimum-field selection and does not publish audit facts to Platform. Gateway and BFF cannot proxy a generic Reveal endpoint. A user-facing screen calls its owning core system, which performs resource authorization before invoking Vault for the approved Purpose.

The `compensation-event` component has no network endpoint. It runs inside each state-owning service and accesses only that service's local table; XXL-Job wakes the registered service Handler rather than reading the table centrally.

## 7. Direct Gateway exposure matrix

| Backend | Browser access through Gateway | Machine access through Gateway | Exposure decision |
|---|---|---|---|
| Client Console BFF | Client Console only | No | Stateless Client-channel adapter; availability belongs in the status ledger |
| Partner Console BFF | Partner Console only | No | Stateless Partner-channel adapter; availability belongs in the status ledger |
| Operations Console BFF | Operations Console only | No | Stateless Operations-channel adapter; availability belongs in the status ledger |
| Commerce | Scoped Checkout endpoints | Client System | Exposed |
| Payment Orchestration | Unified Customer payment submission and original-submission query under the approved Card scope | Provider callbacks; narrowly scoped Client API if required | Restricted exposure; implementation and activation follow the Card design |
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
- Vault is a separately secured internal Go deployable and datastore because its threat boundary, KMS permissions and sensitive-access controls differ from the six business systems. Java consumers call it through the published `@HttpExchange` client over deployment-restricted private HTTP; it is never routed through the external Gateway. Current V1 has no application certificate or strong workload identity: its required but spoofable `X-Caller-Service` plus static Allowlist prevents only accidental misuse.
- A future Maven module named `*-api` is a lightweight internal HTTP-contract JAR containing `@HttpExchange` interfaces and transport DTOs; it is not a separately running process. The Spring Boot `*-web` module is normally the deployable unit.
- Internal callers use Spring HTTP Service Clients with Spring Cloud LoadBalancer and Nacos discovery. They do not route service-to-service calls through Gateway and must not introduce new Dubbo or OpenFeign contracts.
- API Gateway uses Spring Cloud Gateway Server WebFlux. Its reactive filter chain uses non-blocking Spring Data Redis Reactive + Lettuce Lua for approved rate/concurrency controls and does not depend on `paytech-redis-starter`, Redisson or a blocking Redis template; exact implemented profiles belong in the status ledger.
- Confirmed framework, middleware and local-machine decisions are in `TECHNOLOGY_STACK.md` and `LOCAL_DEVELOPMENT_ENVIRONMENT.md`. The local hybrid topology is a development arrangement, not a production deployment topology.
- Split an entry adapter into another service only for demonstrated security isolation, independent scaling, failure isolation, or release-cadence needs.

## 9. Change sequencing

1. Preserve the six core project roots, API Gateway, three channel-specific BFF boundaries and approved frontend names; do not revive retired `business-portal-*` names.
2. Do not create standalone Client API or Provider Callback services: route those logical entry surfaces to the owning core systems.
3. Add or split a deployable only after a demonstrated security, scaling, failure-isolation or release-cadence need and an approved architecture change.
4. Before changing `vault-service`, read the Vault V1 Spec, OpenAPI, physical decisions and current status; do not restore historical Binding/lifecycle APIs without a new approved consumer design.
5. Determine what already exists, is verified or is deployed only from [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) and the affected repository README.
