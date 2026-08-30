# Code-MoR Entry Application Architecture

Last updated: 2026-08-29

Status: current architecture baseline for subsequent design sessions. Console project naming and the Operations Gateway first slice are approved logical components; only API Gateway implementation is currently authorized.

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

### Entry-layer backend project roots

```text
operations-console-bff/  # Internal Operations personnel
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

`checkout-web/` and the historical empty placeholders `business-portal-web/` / `business-portal-bff/` currently exist. The `business-portal-*` names are retired as target architecture; do not add implementation under them. The three `*-console-*` frontend/BFF roots and Vault are not yet created. This naming decision does not authorize their scaffolding.

Do not create `client-api-service` or `provider-callback-service`. Client API is a logical external API surface routed to the owning core systems. Provider callbacks belong to Payment Orchestration.

The current logical backend target is six core services, one API Gateway, three separately secured Console BFFs and one internal Vault: eleven Java deployable types. This is a target boundary, not a mandate to run or scaffold every deployable now. The Operations channel is required by MVP onboarding, refund, dispute, reconciliation and settlement flows; only its Gateway first slice is currently authorized. Frontend applications and the embedded `compensation-event` JAR are not deployable services.

## 3. What BFF means

**BFF** means **Backend for Frontend**. It is a small, stateless backend designed around the needs of a particular frontend rather than around ownership of a business domain.

For Code-MoR, each authenticated Console has its own BFF between API Gateway and the authorized core-system interfaces:

```text
<audience>-console-web
  -> API Gateway
    -> <audience>-console-bff
      -> Platform Support identity/authorization where applicable
      -> authorized Client & Contract / Commerce / Payment / Risk / Finance / Platform Support interfaces
```

`<audience>` is exactly `operations`, `client`, or `partner`. The BFFs may share libraries later, but they do not share a public deployable or security boundary by default.

The BFF may:

- receive authenticated Client, Partner, user, and permission context from the edge;
- aggregate read-only data from multiple core-system interfaces for one page;
- transform domain responses into page-oriented response objects;
- forward a command to the one core system that owns that command;
- normalize presentation-level errors and pagination without changing business meaning.

The BFF must not:

- own orders, payments, balances, ledger entries, contracts, MIDs, or other core facts;
- have a business source-of-truth database;
- calculate fees, partner shares, reserves, or settlement amounts;
- decide risk, routing, payment, refund, or settlement outcomes;
- orchestrate a cross-system business transaction;
- mutate a second core system to compensate for the failure of another system.

This prohibition is literal for cancel, void and reversal: neither BFF may call Payment and Finance as steps of one browser/API command. The BFF forwards the single owning Commerce command; Commerce owns the command lifecycle and internally invokes the authorized domain flow.

Example query aggregation is allowed:

```text
Transaction detail page
  -> BFF queries Commerce, Payment, and Finance
  -> BFF combines their authorized views into one page response
```

Business orchestration is not allowed in the BFF:

```text
Refund command
  -> BFF sends one command to Commerce
  -> Commerce owns refund intent and calls Payment internally
  -> Payment executes the provider refund
```

Cancel and Reversal remain separate Commerce commands. `COM-I05 Cancel` calls Payment `PAY-I12` only to prove that no successful Authorization/Capture/Sale or unresolved Provider result exists and to terminate the Intent; it never creates a VOID or Refund and never silently upgrades itself. Operations may separately send `COM-I08 Reversal`; Commerce calls `PAY-I13`, and Payment atomically resolves the authoritative branch under lock. An uncaptured successful Authorization creates a precise-target VOID, while SALE/full Capture returns `REFUND_REQUIRED` so Commerce can create the normal Refund Request and continue through the Commerce Order Refund Position and Payment Channel Refund chain. Finance consumes only `RefundSucceeded` after Provider success. Gateway and BFF never coordinate Payment and Finance, and an unsolicited post-capture reversal or a reversal of a successful refund is an internal exception workflow, not an entry-layer command.

The BFF is different from API Gateway:

- **Gateway** handles external ingress, basic authentication, rate limiting, routing, request IDs, and access logs. It does not understand page use cases.
- **BFF** understands frontend response shapes and authorized query composition. It does not own business decisions.
- **Core systems** own business rules, state transitions, data, and cross-system domain workflows.

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

Its route families and authentication scheme remain a separate design. This document does not reuse Operations JWT decisions for Client users.

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

The only current public Operations routes are the three exact authentication paths defined by the [Operations API Gateway first-slice design](./plans/2026-08-29-operations-api-gateway-design.md). Gateway removes `/api/operations` and forwards only to `operations-console-bff`; it never routes Operations browser requests directly to Platform Support or a core system. Platform Support owns internal Operations accounts and Token issuance, while Gateway validates the Access JWT and the BFF performs current Role/Permission/Resource authorization.

Do not share Client or Partner BFFs with Operations. Operations has broader permissions, maker-checker controls and sensitive audit requirements. The Operations frontend/BFF remain unimplemented until explicitly authorized; Gateway tests use protocol-level Simulators.

## 5. Portal command and query ownership

| Portal operation | BFF target / business owner |
|---|---|
| Client onboarding and profile | Client & Contract |
| Client transaction eligibility | Client & Contract view, supported by Risk and Payment facts |
| Client order and transaction history | Commerce, with authorized Payment/Finance query enrichment |
| Client order cancellation; full or partial refund | Commerce command only |
| Operations payment Reversal | Commerce `COM-I08` command only; Payment branch selection is internal |
| Client balance, statement, and settlement request | Finance |
| Partner entity submission | Client & Contract |
| Partner entity and MID query | Client & Contract plus read-only Payment query |
| Partner transaction query | Read-only Commerce/Payment views |
| Partner revenue share and payout request | Finance |
| API key, webhook, team, and notification settings | Platform Support |

Partner access remains read-only for transactions. It cannot operate orders, payments, refunds, routing, MIDs, or ledger records.

## 6. Machine-facing access model

### Client API

Client API is the collective name for stable external contracts, not an independent Java service. Gateway routes each request to the owning core system:

| External route family | Owning backend |
|---|---|
| `/api/v1/checkouts/**` | Commerce |
| `/api/v1/orders/**` | Commerce |
| `/api/v1/refunds/**` | Commerce |
| `/api/v1/balances/**` | Finance |
| `/api/v1/settlements/**` | Finance |
| `/api/v1/webhooks/**` | Platform Support |
| `/api/v1/client-profile/**` | Client & Contract, if externally required |

Gateway performs edge controls only. External-contract versioning and resource-level authorization remain the responsibility of the owning system's external HTTP adapter.

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
    -> mTLS / Workload Identity
      -> Vault internal API
        -> Vault database + KMS
```

The caller sends a Purpose, Reason, business resource reference and minimum requested field set. Vault returns an opaque Token or a purpose-scoped minimum Reveal response and emits a sensitive-access audit fact to Platform Support. Gateway and BFF cannot proxy a generic Reveal endpoint. A user-facing screen must call its owning core system, which performs resource authorization and requests only the fields required for that use case.

The `compensation-event` component has no network endpoint. It runs inside each state-owning service and accesses only that service's local table; XXL-Job wakes the registered service Handler rather than reading the table centrally.

## 7. Direct Gateway exposure matrix

| Backend | Browser access through Gateway | Machine access through Gateway | Exposure decision |
|---|---|---|---|
| Client Console BFF | Client Console only | No | Planned; separately exposed after its auth design |
| Partner Console BFF | Partner Console only | No | Planned; separately exposed after its auth design |
| Operations Console BFF | Operations Console only | No | First-slice auth routes exposed with high-privilege controls |
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
- Vault is a separately secured internal deployable and datastore because its threat boundary, KMS permissions and sensitive-access controls differ from the six business systems. It is called through an internal Spring `@HttpExchange` contract with mTLS/workload identity and is never routed through the external Gateway.
- A future Maven module named `*-api` is a lightweight internal HTTP-contract JAR containing `@HttpExchange` interfaces and transport DTOs; it is not a separately running process. The Spring Boot `*-web` module is normally the deployable unit.
- Internal callers use Spring HTTP Service Clients with Spring Cloud LoadBalancer and Nacos discovery. They do not route service-to-service calls through Gateway and must not introduce new Dubbo or OpenFeign contracts.
- API Gateway uses Spring Cloud Gateway Server WebFlux. For the Operations first slice it validates the Platform Support Operations Access JWT and uses non-blocking Spring Data Redis Reactive + Lettuce Lua scripts for atomic multi-dimension Token Bucket and lease-based concurrency controls. It does not depend on `paytech-redis-starter`, Redisson or a blocking Redis template.
- Confirmed framework, middleware and local-machine decisions are in `TECHNOLOGY_STACK.md` and `LOCAL_DEVELOPMENT_ENVIRONMENT.md`. The local hybrid topology is a development arrangement, not a production deployment topology.
- Split an entry adapter into another service only for demonstrated security isolation, independent scaling, failure isolation, or release-cadence needs.

## 9. Current build order

1. Keep the six core project roots and API Gateway project root.
2. Keep `checkout-web` untouched. Treat empty `business-portal-web` and `business-portal-bff` as retired historical placeholders; do not implement under them or delete them without a separate cleanup decision.
3. Do not create standalone Client API or Provider Callback projects.
4. Implement only the approved `api-gateway` Operations first slice. Defer `operations-console-web`, `operations-console-bff`, `client-console-*` and `partner-console-*` scaffolding until explicitly requested.
5. Do not scaffold `vault-service` until implementation is explicitly requested; when it is created, follow `VAULT_SERVICE_ARCHITECTURE.md` and keep the Code-PMX Vault repository as a reference rather than copying its current security assumptions unchanged.
