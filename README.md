# Code-MoR

Code-MoR is a local, interactive requirements and architecture blueprint for a lightweight Merchant of Record platform. It documents the business model, system boundaries, entry applications, functional/interface use cases, and end-to-end sequence diagrams, and now also contains the approved Operations API Gateway implementation, the Platform Support Phase A runtime slice and the independent Vault database-design project.

This repository is not yet a production-ready Java microservice platform.

## Read first

For a new AI or developer session, read these files in order:

1. `AGENTS.md` — persistent project rules and fixed design boundaries.
2. `agent-docs/README.md` — task-based documentation navigation for agents and developers.
3. `agent-docs/PROJECT_CONTEXT.md` — current decisions, scope, terminology, and open questions.
4. `agent-docs/TECHNOLOGY_STACK.md` — confirmed Java, Spring, Maven, MySQL, RabbitMQ, Redis, Nacos, Gateway and observability baseline.
5. `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md` — approved hybrid setup for the current Apple Silicon Mac; the `base` Compose Profile has passed a local smoke test and is currently stopped.
6. `agent-docs/plans/2026-08-24-commerce-document-audit-cancel-reversal-design.md` — current Commerce document/audit boundary and Cancel/Void/Reversal semantics.
7. `agent-docs/APPLICATION_ARCHITECTURE.md` — frontend/backend projects, BFF responsibilities, Gateway exposure, and deployment mapping.
8. `agent-docs/database/README.md` — six-system logical database overview and links to the system-specific models.
9. `agent-docs/DATABASE_DESIGN_GUIDE.md` — PDManer template and database design/change discipline when persistence work begins.
10. `agent-docs/COMPENSATION_EVENT_GUIDE.md` — per-service reliable asynchronous execution, XXL-Job recovery and message idempotency.
11. `docs/superpowers/specs/2026-09-02-vault-database-design.md` — current Vault V1 API, trust, token, topology, cryptographic persistence, idempotency and local-audit authority; read it with `vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md` and use `agent-docs/VAULT_SERVICE_ARCHITECTURE.md` only for the unchanged non-card/PCI and business-ownership safety boundaries.
12. `requirements-site/content/requirements.ts` — canonical detailed requirements catalog.
13. `requirements-site/content/global-use-case-sequences.mjs` — canonical global sequence catalog.

## Current phase

> **2026-09-01 current generated authority:** CCT is 37 tables / 423 fields / 57 unique indexes / 44 access indexes / 64 local relations / 7 one-to-one relations / 6 Diagram Groups. Platform Support is 63 / 822 / 123 / 99 / 107 / 5 / 7, plus 31 composite-FK, 85 named CHECK sidecar contracts and 36 exact `*_hash` contracts. Commerce, Payment, Finance and Risk retain their current figures below.

The [unified human-authentication design](./docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md) and its [PDManer implementation record](./agent-docs/plans/2026-08-30-platform-unified-human-authentication-pdmaner-implementation-plan.md) are the authority for global Account, Realm exactly `{OPERATIONS, CUSTOMER}`, human Channel exactly `{OPERATIONS, CLIENT, PARTNER}`, Platform Tenant membership, CCT Client/Partner pre-Tenant Applications, and Risk typed `source_namespace`. The [Platform Support 57-table simplification design](./docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md) and [implementation record](./agent-docs/plans/2026-09-01-platform-support-database-simplification-implementation-plan.md) remain the direct predecessor for CLIENT Tenant-owned API Credentials, derived Integration Readiness and all unaffected Platform areas. The [unified public API dynamic control-plane design](./docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) and [PDManer implementation plan](./docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) supersede only its static-Gateway conclusion: all external HTTP APIs use one Platform-owned Catalog across six public Route Channels, while internal service calls bypass Gateway. Gateway retains the static Approved Target Registry, Security Profile implementations, trusted protocol, absolute limits and Redis runtime state; BFFs/core systems retain resource authorization. The [Platform Support application foundation](./docs/superpowers/specs/2026-09-01-platform-support-application-foundation-design.md) initialized the `api`/`web`/`test` Maven boundary; the current Phase A slice additionally implements the manually maintained MyBatis 21-table projection, one initialization DML, mTLS workload boundary and PLT-I20 full typed Snapshot provider. Human authentication/Tenant IAM commands, control-plane management APIs, Nacos/MQ/Redis integration and Gateway dynamic Snapshot consumption remain unimplemented, so the deployed static Operations Catalog is still a pre-cutover runtime fact.

The high-level architecture, six core-system boundaries, and all six written logical database/state-model blueprints are confirmed and frozen as V1. The reliability baseline is per-service `compensation_event` with XXL-Job fallback and business-level consumer idempotency; recoverable non-card sensitive values use an internal centralized Vault. All six physical designs are now authorized, generated and automatically validated: [Client & Contract](./client-contract-service/docs/database/client-contract.pdma.json) has 37 tables, 423 fields, 57 unique indexes, 44 access indexes, 64 local relations, 7 one-to-one relations and 6 Diagram Groups; [Commerce](./commerce-service/docs/database/commerce.pdma.json) has 14 tables and 223 fields; [Payment Orchestration](./payment-orchestration-service/docs/database/payment-orchestration.pdma.json) has 29 tables and 373 fields; [Finance](./finance-service/docs/database/finance.pdma.json) is the fourth and has 84 tables, 1,100 fields, 145 unique indexes, 155 access indexes, 160 local relations, 22 one-to-one relations and 6 Diagram Groups; [Risk & Compliance](./risk-compliance-service/docs/database/risk-compliance.pdma.json) is the fifth with 71 tables, 865 fields, 106 unique indexes, 74 access indexes, 111 local relations, 9 one-to-one relations, 8 Diagram Groups and 83 CHECK constraints; and [Platform Support](./platform-support-service/docs/database/platform-support.pdma.json) is the sixth with 63 tables, 822 fields, 123 unique indexes, 99 access indexes, 107 local relations, 5 one-to-one relations and 7 Diagram Groups, plus 31 composite foreign-key contracts, 85 named CHECK sidecar contracts and exactly 36 `*_hash` fields. Finance's review authorities are its [logical model](./agent-docs/database/FINANCE_DATABASE.md), [real-world scenarios](./agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md), [transaction-economics and accounting-rule simplification](./agent-docs/plans/2026-08-26-finance-transaction-economics-accounting-rule-simplification-implementation-plan.md), [account-balance contract-maturity simplification](./agent-docs/plans/2026-08-27-finance-account-balance-contract-maturity-simplification-implementation-plan.md), [refund Funding removal implementation record](./agent-docs/plans/2026-08-27-finance-refund-funding-removal-implementation-plan.md), [generic Funding module removal design](./agent-docs/plans/2026-08-27-finance-funding-module-removal-design.md), [generic Funding module removal implementation record](./agent-docs/plans/2026-08-27-finance-funding-module-removal-implementation-plan.md), [security-deposit Account simplification implementation record](./agent-docs/plans/2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md), [Provider reconciliation simplification implementation plan](./agent-docs/plans/2026-08-28-finance-provider-reconciliation-simplification-implementation-plan.md), [Provider three-stage accounting implementation plan](./agent-docs/plans/2026-08-28-finance-provider-three-stage-accounting-implementation-plan.md), [manual-payout simplification design](./agent-docs/plans/2026-08-28-finance-manual-payout-simplification-design.md), [manual-payout simplification implementation plan](./agent-docs/plans/2026-08-28-finance-manual-payout-simplification-implementation-plan.md), [unified account design (generic Funding sections superseded)](./agent-docs/plans/2026-08-26-finance-unified-account-model-design.md), [account-balance merge plan (generic Funding sections superseded)](./agent-docs/plans/2026-08-26-finance-account-balance-merge-implementation-plan.md), [unified account implementation record](./agent-docs/plans/2026-08-26-finance-unified-account-model-implementation-plan.md), [physical decisions](./finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md), and generated [PDManer model](./finance-service/docs/database/finance.pdma.json). Risk review is governed by its [logical model](./agent-docs/database/RISK_COMPLIANCE_DATABASE.md), [real-world scenarios](./agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md), [physical decisions](./risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md), [Limit balanced simplification design](./agent-docs/plans/2026-08-28-risk-limit-balanced-simplification-design.md), [Limit balanced simplification implementation plan](./agent-docs/plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md), [Decision Engine balanced simplification plan](./agent-docs/plans/2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan.md), [Dispute balanced optimization design](./agent-docs/plans/2026-08-28-risk-dispute-balanced-optimization-design.md), [Dispute balanced optimization implementation plan](./agent-docs/plans/2026-08-28-risk-dispute-balanced-optimization-implementation-plan.md), [DD 19-table simplification plan](./agent-docs/plans/2026-08-27-risk-dd-simplification-implementation-plan.md), [initial PDManer implementation history](./agent-docs/plans/2026-08-26-risk-compliance-pdmaner-implementation-plan.md), and generated [PDManer model](./risk-compliance-service/docs/database/risk-compliance.pdma.json). Platform Support review is governed by the [57-table simplification design](./docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md), [implementation record](./agent-docs/plans/2026-09-01-platform-support-database-simplification-implementation-plan.md), [logical model](./agent-docs/database/PLATFORM_SUPPORT_DATABASE.md), [real-world scenarios](./agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md), [physical decisions](./platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md), and generated [PDManer model](./platform-support-service/docs/database/platform-support.pdma.json). All six models still require the PDManer 4.9.3 in-app manual review.

Vault is a separate supporting-infrastructure database design, not a seventh core-system model. Its [approved V1 design](./docs/superpowers/specs/2026-09-02-vault-database-design.md), [physical decisions](./vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md) and generated [PDManer model](./vault-service/docs/database/vault.pdma.json) are synchronized at exactly 3 tables / 30 fields / 3 non-primary unique indexes / 4 access indexes / 2 local `n:1` relations / 0 one-to-one relations / 1 `vault_core` Diagram Group. The older mandatory Binding, mTLS/Workload Identity, broad API/lifecycle, 18-table topology, complex idempotency and Platform-audit design is historical wherever the approved V1 supersedes it. Current V1 accepts ordinary internal HTTPS plus the required `X-Caller-Service` header and a static allowlist as an MVP trust assumption; the header is spoofable and is not strong service authentication. `vault-service` contains no Java/Maven, hand-maintained DDL/DML, physical database, KMS integration/runtime authentication or deployment implementation, and its PDManer 4.9.3 in-app review remains pending.

Refunds use independent Commerce Order and Payment Channel positions. Commerce calls `PAY-I04` directly; Payment keeps `UNKNOWN` local, publishes `RefundSucceeded` to Commerce and Finance, and publishes `RefundFailed`/`RefundCancelled` only to Commerce. Finance creates accounting and fiscal facts only after success. Finance has no generic Funding module: a Manual Payout locks Owner `AVAILABLE → LOCKED` by `owner_debit_amount`, and every active Execution serializes `active_payout_cash` on its `BANK_CASH` Account; `UNKNOWN` keeps that capacity occupied.

For Platform Support review, the 57-table documents remain historical/current authority for unaffected domains; the public Gateway slice additionally requires the dynamic control-plane design and its PDManer implementation plan above. The current six-table control plane has no `*_ver`, Release, Snapshot, Ack, Tenant Override or new `*_hash` fields.

Risk's [Manual Payout eligibility checkpoint alignment](./agent-docs/plans/2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan.md) preserves the 71-table model and requires fresh RCO-I09 evaluation at `PAYOUT_CREATION`, `PAYOUT_FINAL_APPROVAL`, `PAYOUT_EXECUTION_CREATION`, and `PAYOUT_EXTERNAL_SUBMISSION`. The same command replay reuses its original Assessment/Evaluation pair; each distinct checkpoint obtains a fresh pair over the exact Owner/OE/currency/Beneficiary Account-country/`owner_debit_amount` scope.

Cancel is the Commerce-owned close command for an unpaid Order and completes only after Payment definitively terminates the unsucceeded Intent. Void is Payment's internal release of a successful uncaptured Authorization and creates no refund or Finance/Fiscal facts. Reversal is a Commerce-owned orchestration command: Payment decides atomically under lock, routes an uncaptured Authorization to Void, and routes captured or `SALE` value through the existing full Refund chain; direct public Void and a `REVERSAL` Payment Action are intentionally absent.

Platform Support now has a tested Phase A Java runtime with a manually maintained MyBatis 21-table projection, one initialization DML and the mTLS-protected PLT-I20 full Snapshot query. Authentication/Tenant IAM commands, control-plane management APIs, Nacos/MQ/Redis integration and Gateway-side dynamic consumption remain outside this slice. Repository-owned local middleware is prepared independently from Maven tests; Java tests neither create nor stop it.

The technology baseline is now confirmed as Java 25 LTS, Spring Boot 4.0.8, Spring Cloud 2025.1.3, Spring Cloud Alibaba 2025.1.0.0, Nacos 3.1.x, MySQL 8.4 LTS, RabbitMQ 4.2, Redis 8.2, Spring MVC for core services/BFF/Vault, Spring Cloud Gateway WebFlux and an OpenTelemetry-based observability stack. The current Mac uses an approved hybrid local topology; its `base` Compose Profile passed a local smoke test and is currently stopped with persistent volumes retained.

## Run locally

Prerequisite: Node.js `>=22.13.0`.

```bash
cd requirements-site
npm install
npm run dev
```

Open [http://localhost:3000/](http://localhost:3000/).

The requirements site is local-only. Do not deploy or publish it externally unless explicitly requested.

## Validation

```bash
npm --prefix requirements-site test
```

Focused commands are also available:

```bash
npm --prefix requirements-site run build
npm --prefix requirements-site run test:content
```

## Repository layout

`paytech-build`、`paytech-foundation`、六个核心服务、Gateway、BFF 和两个前端目录分别是独立 Git 仓库；它们保留在同一工作区中，Code-MoR 根仓库只管理跨系统需求、架构、联合验证和本地开发环境。

- `agent-docs/` — project context, architecture, six-system logical database specifications, Vault/event-compensation boundaries and engineering documents used by developers and AI agents, including the shared PDManer template.
- `requirements-site/` — local requirements website and canonical detailed content.
- `paytech-build/` — independently versioned Maven BOM and Parent repository.
- `paytech-foundation/` — independently versioned focused Foundation JAR repository.
- `client-contract-service/` — Client & Contract System project root; contains the first authorized PDManer physical-design artifact but no Java implementation.
- `commerce-service/` — Commerce System project root; contains the second authorized PDManer physical-design artifact but no Java implementation.
- `payment-orchestration-service/` — Payment Orchestration System project root; contains the third authorized PDManer physical-design artifact but no Java implementation.
- `risk-compliance-service/` — Risk & Compliance System project root; contains the fifth authorized PDManer physical-design artifact but no Java implementation.
- `finance-service/` — Finance System project root; contains the fourth authorized PDManer physical-design artifact but no Java implementation.
- `platform-support-service/` — Platform Support System project root; contains the sixth authorized PDManer model and the tested Phase A persistence/initialization plus PLT-I20 full-Snapshot provider.
- `vault-service/` — independently versioned supporting-infrastructure database-design project; contains the generated three-table Vault PDManer model and physical decisions, with no Java, hand-maintained DDL/DML, physical database, KMS/runtime authentication or deployment implementation.
- `api-gateway/` — API Gateway project root, outside the six core systems; its Operations first slice is the only currently authorized Java implementation scope.
- `business-portal-bff/` — retired empty historical placeholder; do not implement Client/Partner Console capabilities under this name.
- `checkout-web/` — frontend project root for Hosted Checkout and Consumer Service Portal.
- `business-portal-web/` — retired empty historical placeholder; do not implement Client/Partner Console capabilities under this name.

The target console names are `operations-console-*` for internal personnel, `client-console-*` for Client, and `partner-console-*` for Local Partner; `checkout-*` remains the Consumer channel. These console roots remain absent until explicitly requested. The Vault database-design root now exists, but no Vault Java/runtime implementation exists. Apart from the six core-system models and the supporting Vault model, the implemented Java scopes are the API Gateway Operations slice and Platform Support Phase A; no Console code is implemented.

## Planned platform direction

The confirmed implementation direction is Java 25 microservices using the shared `paytech-dependencies` BOM and `paytech-parent`, Spring Boot 4.0.8, Spring Cloud 2025.1.3, Nacos, RabbitMQ, MySQL, Redis and DDD-style layering. Internal synchronous contracts use lightweight `*-api` modules with `@HttpExchange`. Each state-owning service embeds the `paytech-compensation-event` library; Vault is a separate internal-only security service. See `agent-docs/TECHNOLOGY_STACK.md` and `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`. Unified Client/Partner/Operations human authentication is designed by the 2026-08-30 authority above; Gateway hardening continues to own TLS/JWT/JWKS/Header/Cookie/Redis/timeout implementation, while Platform's generated control-plane model owns current Route/Guard configuration. Platform now provides PLT-I20 as a complete typed internal Snapshot query; authentication, management behavior, Gateway consumption, BFF/Console implementation and production deployment still require their own implementation slices.
