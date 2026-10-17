# CCT boundary inventory (client-contract-service)

Read-only inventory of `/Users/kenny/Desktop/Workspace/Code-MoR-refactor/client-contract-service`, 2026-10-17. Nothing was edited. Paths are relative to that root unless stated otherwise.

## Main findings

- **No commercial writes exist.** Every Agreement / Pricing / Payment Method Profile / Consumer Legal Document / Seller Disclosure mapper has exactly one statement, `findForCheckout`. Affiliation, OE, Scope, Source Relation and Local Partner also have no writers. In code that data only enters via the test fixture JSON (`client-contract-test/src/test/resources/fixtures/checkout-query.json`).
- **No cross-core write transaction exists today.** Onboarding approval only activates the Client and stamps `t_party_profile.approved_time`. Nothing creates an Affiliation, Scope or Agreement.
- **The only cross-core runtime path is checkout resolution** (`POST /checkout-context/v1/resolutions`). It reads 23 tables from both cores in one REPEATABLE READ read-only snapshot, calls Risk outside the transaction, then re-reads and compares business change record (BCR) root versions (CLIENT / OPERATING_ENTITY / AGREEMENT / LOCAL_PARTNER).
- **The Risk checkout qualification port is a placeholder** (`UnavailableCheckoutQualificationClient`) that always throws OWNER_UNAVAILABLE (503). A successful resolution has never run in tests.
- **No MQ producers.** One consumer (`cg_cct_client_review`) and three compensation handlers, all client-master.

## 1. Maven module layout

- Parent `pom.xml` (parent `paytech-parent`), modules: `client-contract-api`, `client-contract-web`, `client-contract-test`. Base package `com.paytech.client.contract`. One Spring Boot application `ClientContractApplication` (port 18101).
- **`client-contract-api`**: `@HttpExchange` interfaces in `api/checkout` (`CheckoutContextApi` + request/response bodies), `api/onboarding` (Draft, People, Document, FileTarget, FormOptions, OnboardingStart, Submission, ClientApplicationAdmin, ClientClassificationAdmin + models/bodies), `api/workspace` (`ClientWorkspaceApi`), `api/enums` (`CheckoutErrorCode`, `ClientDraftErrorCode`, `ClientFormCatalogStatus`). Depends only on spring-web, jakarta.validation, paytech-common-core.
- **`client-contract-web`** (280 main files): packages `configuration`, `core/dal/dataobject`, `core/dal/mapper` (+ `resources/mapper/*.xml`, 30 XMLs), `domain` (+ `domain/enums`, `domain/onboarding`), `domainservice/repository(+impl)`, `domainservice/service(+impl)`, `domainservice/validator`, `ext/rest`, `ext/mq`, `integration(+impl)`, `service/processor(+request/response)`, `service/handler`. Dependencies: client-contract-api, paytech-mvc-web-starter, paytech-mybatis-support, MyBatis, HikariCP, mysql-connector-j, paytech-compensation-event, paytech-http-client-support, paytech-json-canonicalization, paytech-snowflake-id, platform-support-api, risk-api, paytech-vault-client, paytech-redis-starter, spring-boot-starter-amqp, Nacos discovery, loadbalancer, actuator/prometheus.
- **`client-contract-test`**: paytech-test-support, client-contract-web, spring-boot-starter-webmvc-test; Mockito excluded.
- Persistence: a single DataSource / SqlSessionFactory / `DataSourceTransactionManager` in `configuration/ClientContractPersistenceConfiguration.java`. `configuration/ClientContractRepositoryConfiguration.java` wires all per-table repositories for both cores.
- Feature switches in `application.yml`: `onboarding.draft-enabled`, `onboarding.recovery-enabled` (default false), `compensation.enabled` follows recovery; checkout beans activate only when `spring.datasource.url` is set.

## 2. Table inventory (38 tables)

Source: `docs/database/sql/client-contract-schema.sql` (664 lines); model `docs/database/client-contract.pdma.json`; decisions `docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`.

### Client-master (21)

| Table | Key columns / reason |
|---|---|
| `t_party` | Legal identity root (`party_no`, `party_type`). |
| `t_party_profile` | One profile per Party; `draft_application_id` -> application; `approved_time` makes it read-only. Read by commercial signing validation. |
| `t_individual_profile` | Person profile. |
| `t_organization_profile` | Organization profile. |
| `t_party_identity_key` | Permanent legal identifier registry (`uk_party_identity_norm`). |
| `t_party_profile_identifier` | Profile-to-identity-key binding. |
| `t_party_address` | Profile addresses. |
| `t_party_contact` | Email/Phone as `contact_vault_token` + `contact_masked_value`. |
| `t_party_relation` | Organization-to-person relations; `client_application_id`. |
| `t_client` | Client role root (`client_no`, `party_id`, status). |
| `t_local_partner` | Local Partner role root. |
| `t_operating_entity` | OE role root, PK `entity_no`, `party_id`, `operating_country`. |
| `t_client_business_profile` | Business profile + `classification_case_id/confirmed_by/confirmed_time`. |
| `t_client_business_classification` | Industry + MCC, `primary_slot`. |
| `t_client_application` | Client application (`due_diligence_case_id`, `current_submission_id`, `qualification_*`). |
| `t_partner_application` | Partner application; only referenced in `ClientPeopleMapper` personColumns. |
| `t_operating_entity_application` | OE onboarding / scope-change application; `approved_source_relation_id`, `source_*`. |
| `t_operating_entity_scope` | Approved consumer country x currency x industry x MCC per OE, tied to `application_id`. |
| `t_application_document` | File refs shared by all three application types (`client_application_id`, `partner_application_id`, `operating_entity_application_id`, `subject_party_id`, `file_id`). |
| `t_client_entity_affiliation` | Client may use OE; `priority_no`, `active_slot`, effective window. |
| `t_entity_source_relation` | Unique active source of an OE: PLATFORM / CLIENT / PARTNER (`source_client_id`, `source_partner_id`, `client_source_basis`). |

### Commercial (11)

| Table | Key columns / reason |
|---|---|
| `t_agreement` | Contract; carries master IDs `client_id`, `local_partner_id`, `entity_no`. |
| `t_agreement_party` | Signing roles; carries master `party_id`, `party_profile_id`; acceptance evidence. |
| `t_agreement_document_ref` | SIGNING_DOCUMENT / EXECUTED_DOCUMENT file refs. |
| `t_agreement_operating_entity` | Partner agreement multi-OE coverage (`entity_no`). |
| `t_payment_method_profile` | Reusable payment method profile. |
| `t_payment_method_profile_cond` | Profile conditions; **never referenced in code**. |
| `t_commercial_term_plan` | CLIENT_PRICING / PARTNER_SHARE / PLATFORM_SERVICE_CHARGE plan; carries `agreement_id`, `entity_no`, `client_id`, `local_partner_id`. |
| `t_commercial_term_version` | Immutable version; `agreement_id`. |
| `t_commercial_term_price` | Version x exact `entity_no` x `payment_method_profile_code`; settlement fields. |
| `t_commercial_term_component` | Fee/share/charge components. |
| `t_commercial_term_component_band` | Tier bands. |

### Shared / unclear (6)

- **`t_consumer_legal_document`**: CONSUMER_TERMS / REFUND_POLICY / PRIVACY_NOTICE per `entity_no` x country x locale. Used as checkout legal evidence. Could be either core; needs a decision.
- **`t_seller_disclosure`**: OE public legal facts per country x locale. Fits client-master "legal entity data", but is consumed only by checkout resolution.
- **`t_business_change_record` / `t_business_change_version`**: audit roots; record types span both cores (PARTY, CLIENT, LOCAL_PARTNER, OPERATING_ENTITY, PARTY_PROFILE, CLIENT_BUSINESS_PROFILE, CLIENT_APPLICATION, PARTNER_APPLICATION, OPERATING_ENTITY_APPLICATION, PARTY_RELATION, CLIENT_ENTITY_AFFILIATION, ENTITY_SOURCE_RELATION vs AGREEMENT, COMMERCIAL_TERM_PLAN, COMMERCIAL_TERM_VERSION, PAYMENT_METHOD_PROFILE, CONSUMER_LEGAL_DOCUMENT, SELLER_DISCLOSURE). Each core needs its own copy. Also used by client-master as the Client lifecycle projection source (`ClientProjectionMapper`).
- **`t_compensation_event`**: per-core infrastructure; only client-master handlers use it now.
- **`t_command_idempotency`**: never referenced in code; banned for new adoption by the Gateway request idempotency decision. Candidate to drop.

## 3. Cross-core SQL

All in `client-contract-web/src/main/resources/mapper/`. Statement is `findForCheckout` unless noted. Common scoping subqueries (client-master tables): `entity_no IN (SELECT entity_no FROM t_client_entity_affiliation WHERE client_id=#{clientId})` and `local_partner_id IN (SELECT source_partner_id FROM t_entity_source_relation WHERE entity_no IN (...affiliation...) AND source_type='PARTNER')`.

| Mapper | Tables touched |
|---|---|
| `AgreementMapper.xml` | t_agreement, t_agreement_operating_entity, t_client_entity_affiliation, t_entity_source_relation |
| `AgreementPartyMapper.xml` | + t_agreement_party |
| `AgreementDocumentRefMapper.xml` | + t_agreement_document_ref |
| `AgreementOperatingEntityMapper.xml` | t_agreement, t_agreement_operating_entity, affiliation, source relation |
| `CommercialTermPlanMapper.xml` | t_commercial_term_plan, affiliation, source relation |
| `CommercialTermVersionMapper.xml` | + t_commercial_term_version |
| `CommercialTermPriceMapper.xml` | + t_commercial_term_price (also `entity_no IN affiliation`) |
| `CommercialTermComponentMapper.xml` | + t_commercial_term_component |
| `CommercialTermComponentBandMapper.xml` | + t_commercial_term_component_band |
| `PaymentMethodProfileMapper.xml` | t_payment_method_profile via price/version/plan + affiliation/source |
| `ConsumerLegalDocumentMapper.xml` | t_consumer_legal_document, affiliation |
| `SellerDisclosureMapper.xml` | t_seller_disclosure, affiliation |
| `PartyProfileMapper.xml` | master `t_party_profile` selected through commercial `t_agreement_party` / `t_agreement` / `t_agreement_operating_entity`, or `t_client.party_id` |
| `BusinessChangeRecordMapper.xml` `findCheckoutRoots` | t_business_change_record roots of CLIENT, OPERATING_ENTITY (via affiliation), AGREEMENT (via agreement + source + agreement OE), LOCAL_PARTNER (via source relation) |
| `ClientPeopleMapper.xml` fragment `personColumns` (used by `people`, `person`) | **Reverse edge**: client-master computes person `shared` flag using `EXISTS t_agreement_party` (party_id or party_profile_id), plus t_partner_application, t_operating_entity_application, t_client/t_client_application, t_party_relation |

Pure client-master checkout statements (still need only master tables): `ClientMapper.findById`, `ClientBusinessProfileMapper.findByClientId`, `ClientBusinessClassificationMapper.findForCheckout`, `ClientEntityAffiliationMapper.findByClientId`, `OperatingEntityMapper`, `EntitySourceRelationMapper`, `OperatingEntityScopeMapper`, `OperatingEntityApplicationMapper`, `LocalPartnerMapper` (`findForCheckout`).

Client-master-only multi-table SQL: `ClientDraftMapper` fragment `ApplicationIdentity` (t_client_application, t_client, t_party, t_party_profile, t_client_business_profile), `ClientDraftMapper.fields`, `ClientProjectionMapper` fragment `Source` (BCR + t_client + t_client_application), `ClientApplicationMapper.findApprovedSummaries`, `ClientDocumentMapper` (t_application_document, t_party).

After a database split every commercial statement above must take explicit `entityNos` / `localPartnerIds` / `partyProfileIds` parameters instead of master subqueries.

## 4. Cross-core transactions

None. All write transactions (TransactionTemplate, no `@Transactional`) stay in client-master:

| Entry | Writes |
|---|---|
| `domainservice/service/impl/ClientSubmissionServiceImpl.submit` (via `SubmitClientApplicationProcessor`, `AbstractLockProcessor`) | t_client_application.submit, t_party_identity_key.insert, t_party_profile_identifier.bind, compensation CLIENT_REVIEW_REGISTRATION |
| `domainservice/service/impl/ClientReviewOutcomeServiceImpl.conclude` (via `ApplyClientReviewResultProcessor` from MQ) | t_client_application.concludeReview; on APPROVED: t_client.activate, t_party_profile.markApproved, BCR version append + root advance, compensation CLIENT_TENANT_PROJECTION. No Affiliation / Agreement / Scope. |
| `domainservice/service/impl/ClientClassificationServiceImpl.replace/confirm` (via `Replace/ConfirmClientClassificationProcessor`) | t_client_business_profile bind/confirm, t_client_business_classification delete+insert, compensation CLIENT_REVIEW_CLASSIFICATION |
| `ClientReviewRegistrationServiceImpl` | t_client_application.startReview |
| `ClientDraftServiceImpl` via `ClientDraftProcessor` (`@ResourceLock` account-slot / application) | t_party, t_client, t_client_application, t_party_profile, t_individual_profile, t_organization_profile, t_client_business_profile, t_party_address, t_party_profile_identifier, t_party_contact, BCR; reapplication moves profiles/relations |
| `ClientPeopleServiceImpl` via `ClientPeopleProcessor` (`@ResourceLock` application + party) | t_party, t_party_profile, t_individual_profile, t_party_address, t_party_profile_identifier, t_party_relation |
| `ClientDocumentServiceImpl` via `ClientDocumentProcessor` (`@ResourceLock`) | t_application_document |

The only multi-core transaction is the read-only checkout snapshot (REQUIRED, REPEATABLE READ, read-only, 10 s) built in `configuration/CheckoutResolutionConfiguration.java`, executed twice per request by `CheckoutQueryServiceImpl.load` and `.verify`.

## 5. Cross-core Java calls (class edges)

- `domainservice/service/impl/CheckoutQueryServiceImpl` -> 23 repositories: master (Client, ClientBusinessProfile, ClientBusinessClassification, ClientEntityAffiliation, OperatingEntity, EntitySourceRelation, OperatingEntityScope, OperatingEntityApplication, LocalPartner, PartyProfile) + commercial (Agreement, AgreementOperatingEntity, AgreementParty, AgreementDocumentRef, CommercialTermPlan/Version/Price/Component/ComponentBand, PaymentMethodProfile) + unclear (ConsumerLegalDocument, SellerDisclosure, BusinessChangeRecord).
- `service/processor/CheckoutResolutionProcessor` -> `CheckoutQueryService` + `integration/CheckoutQualificationClient`.
- `domain/CheckoutResolutionContext`, `domain/CheckoutCandidateContext`, `domain/CheckoutPricingContext`, `domain/CheckoutContextView`, `ext/rest/CheckoutContextConverter` mix master and commercial records.
- `configuration/CheckoutResolutionConfiguration` imports `ClientContractRepositoryConfiguration` (all repos).
- Onboarding classes (draft/people/document/submission/review/classification/workspace/projection) never import commercial types.

## 6. Shared DataObjects / enums

- `core/dal/dataobject/CheckoutRecords.java` (451 lines): one record per checkout table for both cores (Client, ClientBusinessProfile, ClientBusinessClassification, ClientEntityAffiliation, OperatingEntity, EntitySourceRelation, OperatingEntityScope, OperatingEntityApplication, LocalPartner, Agreement, AgreementOperatingEntity, AgreementParty, AgreementDocumentRef, PartyProfile, CommercialTermPlan/Version/Price/Component/ComponentBand, PaymentMethodProfile, ConsumerLegalDocument, SellerDisclosure, BusinessChangeRecord). Must be split.
- `domain/enums/BusinessChangeRecordRecordType` and `BusinessChangeRecordAggregateRootType`: types from both cores.
- `domain/enums/BusinessChangeOperation`, `BusinessChangeSnapshotFormat`: needed by both cores' audit.
- Per-table status/type enums (`Agreement*`, `CommercialTerm*`, `PaymentMethodProfileStatus`, `ConsumerLegalDocument*`, `SellerDisclosureStatus` vs `Client*`, `OperatingEntity*`, `EntitySourceRelation*`, `LocalPartnerStatus`, `PartyRelation*`) split cleanly.
- `api/enums/CheckoutErrorCode` belongs to the resolution owner.
- `PartyProfile` (approved_time, draft_application_id, content_hash) is needed by commercial signing validation; master-owned.

## 7. External contracts

### Inbound HTTP

| Path | Caller guard | Owner core |
|---|---|---|
| `POST /checkout-context/v1/resolutions` (`CheckoutContextApi`, `ext/rest/CheckoutQueryController`) | `X-Caller-Service` must be `commerce-service` | Commercial (resolution orchestrator) |
| `GET /client-onboarding/v1/start` | Gateway actor/session | Client-master |
| `GET /client-onboarding/v1/form-options` | Gateway | Client-master |
| `/client-onboarding/v1/applications`: POST create, GET `{id}`, PATCH `{id}/draft`, POST/PATCH `{id}/people[/{partyId}]`, POST/PATCH/DELETE `{id}/relations[/{relationId}]`, POST/DELETE `{id}/documents[/{documentId}]`, POST `{id}/submissions`, GET `{id}/submission-check` | Gateway | Client-master |
| `GET /internal/client-onboarding/file-targets` | `X-Caller-Service` | Client-master |
| `/client-onboarding-admin/v1`: GET classification-options, GET cases/{caseId}/application, GET/PUT cases/{caseId}/classifications, POST cases/{caseId}/classification-confirmation | Operations gateway | Client-master |
| `GET /client-workspace/v1/client-summaries` | Gateway | Client-master |

### Outbound HTTP (`HttpServiceClientFactory`, load-balanced `platform-support-service` / `risk-service`)

| Client | API | Owner core |
|---|---|---|
| `integration/impl/ClientDraftAccountClientImpl` | Platform `ClientAuthenticationApi` (account/session) | Client-master |
| `integration/ClientMaterialAuthorityClient` | Platform `OnboardingFileVerificationApi`; Risk `MaterialRequirementsApi` | Client-master |
| `integration/impl/ClientTenantProjectionClientImpl` | Platform `TenantProjectionApi` (PLT-I17) | Client-master |
| `integration/impl/ClientReviewRegistrationClientImpl` | Risk `ClientReviewRegistrationApi` (I01 register, I02 classifications) | Client-master |
| `integration/impl/ClientDraftContactClientImpl` | `VaultClient` (stable contact token) | Client-master |
| `integration/impl/UnavailableCheckoutQualificationClient` | Risk checkout qualification, **not implemented**, always 503 | Commercial |

### MQ

- Consumer `ext/mq/ClientReviewResultListener`: queue `cg_cct_client_review` (quorum, delivery limit 5), bound to topic `topic_risk_client_review` with key `client_review.decided.v1`; DLX `dlx_cct_client_review`, DLQ `cg_cct_client_review_dlq`. Declared in `configuration/ClientReviewConfiguration.java`. Owner: client-master.
- Producers: **none**. OperatingEntityChanged, ClientOnboarded, etc. are not published.

### Compensation / jobs

- `service/handler/ClientReviewRegistrationHandler` (CLIENT_REVIEW_REGISTRATION), `ClientClassificationHandler` (CLIENT_REVIEW_CLASSIFICATION), `ClientTenantProjectionHandler` (CLIENT_TENANT_PROJECTION). All client-master.
- No `@XxlJob` in CCT code; XXL-Job only configured in test `application-mock.yml` with executor disabled.

## 8. Tests

12 classes, 53 `@Test` methods.

| Class | Tests | Base | Core |
|---|---|---|---|
| `auto/ext/http/checkout/ResolveCheckoutHttpTest` | 13 | AbstractCheckoutApiTest | Cross-core |
| `auto/ext/http/checkout/ResolveCheckoutValidationTest` | 3 | AbstractCheckoutApiTest | Cross-core (no data) |
| `auto/ext/http/workspace/ClientSummariesTest` | 7 | OnboardingMockTestBase | Master |
| `auto/ext/http/onboarding/SubmitClientApplicationTest` | 8 | AbstractSubmissionApiTest | Master |
| `auto/ext/http/onboarding/OnboardingDuplicateWriteTest` | 4 | AbstractSubmissionApiTest | Master |
| `auto/ext/eventhandler/onboarding/ClientReviewRegistrationHandlerTest` | 3 | AbstractSubmissionApiTest | Master |
| `auto/ext/http/onboarding/ClientClassificationAdminTest` | 3 | AbstractClientReviewTest | Master |
| `auto/ext/http/onboarding/ClientApplicationAdminTest` | 2 | AbstractClientReviewTest | Master |
| `auto/ext/http/onboarding/ReapplyAfterRejectionTest` | 1 | AbstractClientReviewTest | Master |
| `auto/ext/http/onboarding/ResubmitAfterReturnTest` | 1 | AbstractClientReviewTest | Master |
| `auto/ext/mq/onboarding/ClientReviewResultListenerTest` | 4 | AbstractClientReviewTest | Master |
| `auto/ext/eventhandler/onboarding/ClientClassificationHandlerTest` | 4 | AbstractClientReviewTest | Master |

- Checkout base `test/base/checkout/AbstractCheckoutTest` extends Foundation `TestBase`; `@SpringBootTest(ClientContractApplication, RANDOM_PORT)`, profiles `auto` + `checkout-auto`, local MySQL `mor_cct` (`CCT_TEST_DB_*`). `test/mocker/local/checkout/CheckoutResolutionFixture` + `CheckoutFixture` install `fixtures/checkout-query.json` (23 tables across both cores), assert no compensation events changed, clean up per test via TestBase. `test/dal/checkout/CheckoutTestMapper` touches t_client, t_client_business_profile/classification, t_operating_entity_scope/application, t_party_profile. Risk port is the real 503 placeholder, so no successful resolution is tested.
- Onboarding base `test/base/onboarding/OnboardingMockTestBase` extends `TestBase`; `@SpringBootTest(OnboardingTestApplication, RANDOM_PORT)`, profile `mock`. Global BeforeEach asserts empty applications and mocks session; AfterEach waits for owner mock server idle and compensation dispatcher idle, then fails on unexpected requests; clearing blocked if async work did not stop. External HTTP mocks in `test/mock/external/onboarding/` (MockPlatformAuthenticationApi, MockPlatformFileVerificationApi, MockPlatformTenantProjectionApi, MockRiskReviewRegistrationApi, MockRiskOnboardingApi). `test/dal/onboarding/OnboardingTestMapper` touches only master tables + BCR + t_compensation_event.
- `client-contract-test/README.md` is stale: it says only the 16 checkout tests exist.

## 9. OE candidate resolution ownership and logic hotspots

### Recommended ownership

Split in two:

- **Client-master** owns an ordered eligible-OE candidate list: Client ACTIVE, confirmed primary classification and approved party profile, active Affiliations in priority order, OE ACTIVE, unique active Source Relation (shape-validated), exact active Scope matching consumer country / currency / industry / MCC, and Scope application APPROVED with matching `approved_source_relation_id`. Plus root versions (CLIENT, OPERATING_ENTITY, LOCAL_PARTNER) for later re-verification.
- **Commercial** owns final resolution: iterate candidates, require applicable signed Client Agreement, CLIENT_PRICING plan/version/prices, Partner Agreement + PARTNER_SHARE + PLATFORM_SERVICE_CHARGE + PLATFORM_SIGNATORY for PARTNER sources, three legal documents and seller disclosure; call Risk outside transactions; re-verify; return OE + agreement + plan/version IDs.

Reason: the fall-through to the next OE depends on commercial facts (agreement, pricing, legal documents), so client-master alone cannot pick the final OE. Consequence: the single 23-table snapshot and `verifyUnchanged` root comparison no longer work across two databases; client-master must return root versions with candidates and re-verify them on a second call (or commercial must hold versioned replicas).

### Top 10 hotspots

1. **Candidate fall-through** (`domain/CheckoutResolutionContext.candidates` / `buildCandidate`): Affiliation-priority iteration skipping OEs missing Client agreement, pricing, Partner terms or legal docs.
2. **Snapshot consistency** (`verifyUnchanged` / `stillAvailable`, `CheckoutQueryServiceImpl.verify`): re-read compares CLIENT / OPERATING_ENTITY / AGREEMENT / LOCAL_PARTNER roots together and re-checks exact Affiliation / Scope / Source IDs.
3. **Signing validation** (`requireSigned` / `validateSignature`): agreement party vs master party profile (approved before acceptance, no draft, valid hash); owner party must match Client/Partner party; OE's `party_id` must be an OPERATING_ENTITY party; same-party roles share one signature; CLICK_ACCEPTANCE vs EXECUTED_DOCUMENT rules.
4. **Partner path** (`partner`): source relation (master) -> Local Partner ACTIVE (master) -> Partner agreement via `t_agreement_operating_entity` (commercial) -> share and charge plans -> exactly one PLATFORM_SIGNATORY.
5. **Pricing** (`domain/CheckoutPricingContext.resolve`): plan by agreement/client/OE or partner; version effective at asOf; all prices for the OE validated with profile, components, bands, settlement fields. Returns only plan/version IDs; no per-payment-method price selection.
6. **Classification to Scope match**: primary industry/MCC must equal an active Scope exactly; Client and party profile approvals must predate asOf.
7. **Scope approval link**: Scope's OE application APPROVED and `approved_source_relation_id` == active source relation.
8. **Legal evidence** (`legal`): CONSUMER_TERMS / REFUND_POLICY / PRIVACY_NOTICE + seller disclosure by OE x country x locale; party profiles of the client agreement returned as evidence.
9. **"Shared" person check** (`ClientPeopleMapper` personColumns): client-master reads commercial `t_agreement_party` to lock persons used in agreements.
10. **Review approval flow** (`ApplyClientReviewResultProcessor` / `ClientReviewOutcomeServiceImpl`): activates Client and stamps profile approval, which later anchors agreement signing; no step provisions Affiliation, Agreement or pricing, so how that data enters is undefined.

### Uncertainties

- Owner of `t_consumer_legal_document` and `t_seller_disclosure` is open.
- `docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md` treats CCT-I13 / CCT-I14 as legacy V5 Scope wires; `CCT-QUERY-01` does not appear anywhere. The implemented resolution follows `docs/requirements/20260921-checkout-context-resolution/design.md`; its new pricing wire is marked as not yet designed.
- Writers for OE / Affiliation / Scope / Source / Agreement / Pricing are absent from code; whether they live elsewhere (manual SQL, another service) was not established.
