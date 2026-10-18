# Commerce boundary inventory

Read-only inventory; nothing was edited.

- P = /Users/kenny/Desktop/Workspace/Code-MoR-refactor/commerce-service
- W = P/paytech-commerce-web/src/main/java/com/paytech/commerce

## TL;DR

- No production SQL joins `t_customer` / `t_client_stored_card_setting` with acquiring tables. The only DB link is the soft reference `t_commerce_order.customer_id`.
- No transaction writes consumer-core and acquiring tables together. CreateCheckout creates or links the Customer in its OWN short transaction BEFORE, and separate from, the order-save transaction. An orphan Customer on a failed order is accepted by design.
- The real coupling is runtime and in Payment's data:
  - Payment stored cards are keyed by Commerce `customer_id`.
  - `customer_no` is sent to Payment as `consumerReference`.
  - Card-saving eligibility reads Customer plus the stored-card setting live on every GET checkout and every SubmitPayment.
- **Premise correction:** consumer profile/Email/Phone Vault tokens are NOT Customer-scoped today. They live per ORDER in `t_consumer_context`. The Phone token is never written, `consumer_reference` there is always NULL, and Vault Protect is a fixed Mock. A V2 "consumer core owns Vault tokens" is a new model, not a move.
- The schema DDL and table comments still describe the deleted preparation stage. The code freezes everything at CreateCheckout.

## 1. Maven modules (P/pom.xml)

- **`paytech-commerce-client-api`**: 24 java files.
  - Client API `@HttpExchange` contracts plus DTOs (order, checkout, refund, refundable-amount, customer).
  - Enums `CommerceCheckoutErrorCode`, `OrderCreationMode`, `Cancellation*`.
  - No Maven consumer outside commerce-service in this repo.
- **`paytech-commerce-customer-api`**: 10 files.
  - Customer (consumer-facing) Checkout contracts `CustomerCheckoutApi` and `CustomerPaymentApi`.
  - Enums `CustomerCheckoutErrorCode` and `CustomerPaymentStatus`.
  - No external consumer.
- **`paytech-commerce-web`**: 231 files. The single deployable app; context path `/commerce`, default port 18102.
  - Depends on paytech-payment-api, risk-api, platform-support-api and client-contract-api.
  - Foundation dependencies: redis-starter, compensation-event, http-client-support, mvc-web-starter, mybatis-support, snowflake-id, json-canonicalization.
  - Also AMQP, MyBatis, MySQL and Nacos discovery.
  - **client-contract-api (CCT) is declared but imported by no main code.** The old precheck was deleted and the CCT port is a fixed Mock.
- **`paytech-commerce-test`**: 99 files. Auto/manual tests only, no deployable JAR.

Layering: `ext/rest|mq|job` → `service/processor` → `domainservice/service|repository|port` → `core/dal/mapper|dataobject`, plus `integration/*`.

### Naming trap

Every `*/customer/` package (dataobject, mapper, repository, domainservice/service, service/processor, domain) mostly holds CUSTOMER-CHECKOUT (consumer-facing checkout) code, not consumer core.

The consumer-core files are:

- DataObject `Customer`.
- `CustomerMapper(.xml)` and `ClientStoredCardSettingMapper(.xml)`.
- `CustomerRepository(Impl)` and `ClientStoredCardSettingRepository(Impl)`.
- `CustomerService(Impl)`, enum `CustomerStatus` and `ConsumerReferencePolicy`.
- Processors `CreateCustomer`, `GetCustomer`, `DeleteCustomer`, `ListStoredCards`, `DeleteStoredCard`.
- `ext/job/StoredCardInvalidationHandler`.
- `integration/payment/PaymentStoredCardClient(+Impl)`.
- The Client API `ClientCustomerController` and `ClientCustomerConverter`.
- Shared with acquiring (see section 5): `CardSavingEligibility`.

## 2. Tables

Source: P/docs/database/sql/commerce-schema.sql, 17 tables.

The test DDL P/paytech-commerce-test/src/test/resources/init/ddl.sql is identical column-for-column except it omits `t_command_idempotency`. The README says "14 tables", which is stale; the test DDL has 16.

| table | proposed module | reason |
|---|---|---|
| t_commerce_order | transaction | Order root. Soft ref `customer_id` to consumer. Frozen OE (entity_no/source/partner/industry/mcc) and tax/total. Still carries preparation-era columns preparation_command_id, context_format (always 'TERM_PRICE'), termination_requested_at |
| t_order_item | transaction | Immutable lines from create |
| t_order_context | transaction | Freeze: affiliation/scope/source relation, pricing versions, risk_checkout_assessment_id, finance_tax_snapshot_id/hash, country, locale. Java writer is `mapper/customer/CustomerCheckoutMapper.insertContext` |
| t_order_legal | transaction (freeze), heavily read by checkout | Agreements, 3 doc refs, seller disclosure. Frozen at create |
| t_order_payment_projection | transaction | Inserted by checkout SubmitPayment (`bindPayment`), advanced by the MQ consumer |
| t_order_funds_availability_projection | transaction | NOT_APPLICABLE at create, PENDING on payment success. No Finance consumer yet |
| t_order_refund_position | **shared transaction/command** | Created at create and base set on payment success (transaction); reserve/settle by refund (command) |
| t_checkout_session | checkout | Inserted in the CreateCheckout tx, completed by the payment-success tx, cancelled by the cancel tx |
| t_consumer_context | checkout (NOT consumer) | Per-order email/profile Vault tokens and derived billing dims. phone_vault_token and consumer_reference are never written |
| t_consumer_contract | checkout | Rendered contract, written in the CreateCheckout tx |
| t_checkout_network_evidence | checkout | LEGAL_ACCEPTANCE evidence from SubmitPayment |
| t_consumer_acceptance | checkout | The 3 acceptances, written at first SubmitPayment |
| t_refund_request | command | Refund aggregate |
| t_customer | consumer | `uk(client_id, active_reference)`, `cus_` number, irreversible delete |
| t_client_stored_card_setting | consumer (or Client config, unclear) | Read-only in code. No write path except DML or test mapper. README calls it a placeholder until CCT has a slot |
| t_command_idempotency | legacy, drop | No code reads or writes it. Absent from the test DDL |
| t_compensation_event | shared technical | Holds acquiring events (CLIENT_WEBHOOK_REQUESTED, refund) and consumer events (STORED_CARD_INVALIDATION). The consumer DB needs its own copy |

## 3. Cross-boundary SQL

### Consumer ↔ acquiring, production

None. `t_commerce_order.customer_id` is written by `CommerceOrderMapper.insert` and read back as a column. The Customer is then loaded by `CustomerMapper.findById` in Java.

### Consumer ↔ acquiring, test only

In P/paytech-commerce-test/src/test/java/com/paytech/commerce/test/dal/CustomerTestMapper.java:

- `checkCustomerTables` selects from t_customer, t_commerce_order.customer_id and t_client_stored_card_setting.
- `orderCustomerId` reads t_commerce_order.
- `deleteTasks` joins `t_compensation_event` to `t_customer` on `CAST(customer_id AS CHAR)=event_key`.

`CheckoutDataMocker.clear()` deletes acquiring rows and Customer rows in ONE transaction.

### Statements spanning proposed acquiring modules

These are fine in a shared DB, but each needs an owner:

- `CommerceOrderMapper.recordPaymentSubmission`: order CAS plus EXISTS on session (version, PREPARING/OPEN), consumer_contract, payment_projection (version/uncaptured) or NOT EXISTS, and consumer_context.
- `CommerceOrderMapper.recordConsumerChange`: order CAS plus NOT EXISTS acceptance⋈contract, EXISTS session, and `preparation_command_id IS NULL`.
- `CommerceOrderMapper.cancel`: order CAS plus payment_projection EXISTS. With localOnly it also checks `preparation_command_id IS NULL` and NOT EXISTS acceptance⋈contract.
- `OrderRefundPositionMapper.reserve`: refund_position plus EXISTS order⋈payment_projection.
- `CheckoutSessionMapper.findQueryVersions`: session⋈order LEFT JOIN consumer_context.
- `CheckoutSessionMapper.expire`: session⋈order UPDATE. **No caller.**
- `OrderQueryMapper.selectOrder`: order LEFT JOIN session and context.
- `BasicCheckoutMapper.select/selectItems`: order⋈session and items⋈order.
- `CustomerCheckoutMapper.frozen`, `ConsumerContractMapper.findByOrderId`, `OrderContextMapper.findCheckoutLegal`: context⋈legal⋈contract.
- `ConsumerAcceptanceMapper.existsByOrderId`: acceptance⋈contract.
- `RefundMapper.select`: refund⋈order.

## 4. Transactions

All go through `service/transaction/CommerceTransactionExecutor.write` (REQUIRED).

### CreateCheckout

Code: W/service/processor/checkout/CreateCheckoutProcessor.java.

1. `CustomerService.resolveActiveCustomer` runs a separate short transaction that inserts t_customer. On DuplicateKey it re-reads the winner.
2. `CheckoutCreationServiceImpl.saveCreateCheckoutContext` then writes 8 tables in one tx: order, items, refund_position, funds, session, context, legal, contract.

The two steps are not atomic. A failed order leaves the Customer in place, by design per the code comment.

### DeleteCustomer

`CustomerServiceImpl.deleteCustomer` conditionally deletes t_customer and registers a STORED_CARD_INVALIDATION compensation event in one tx. Orders are untouched and keep `customer_id`. After commit the handler calls Payment `POST /payment-card/v1/customers/{customerId}/stored-cards/invalidation`.

No tx mixes consumer and acquiring business tables.

### Acquiring-only transactions

| transaction | writes |
|---|---|
| `saveConsumerContext` | order version + consumer_context |
| `saveSubmissionContext` | order version + evidence + 3 acceptances, before Intent creation |
| `bindPayment` | payment_projection insert + order CAS |
| `applySucceeded` | projection, order PAID, session COMPLETED, refund base, funds PENDING, order.paid webhook event |
| `recordClosedOrderPayment` | payment projection only, for closed orders |
| `requestTermination`, then `cancel` | order, session, order.cancelled event |
| `saveCreateRefundContext` | refund, reserve, refund-submission event, refund.created event |
| `RefundSubmissionHandler` prepare/complete | two short transactions around the Payment call, position row locked `FOR UPDATE` |

## 5. Java calls and CreateCheckout initialization

### Acquiring → consumer

- `CreateCheckoutProcessor.attachCustomer` calls `CustomerService.resolveActiveCustomer`.
- `ConsumerReferencePolicy.requireOpaque` is shared with `CreateCustomerProcessor`. It enforces the charset and rejects 7-15 pure digits.
- `CardSavingEligibility` (in service/processor/customer):
  - Reads `CustomerRepository.findById` and `ClientStoredCardSettingRepository.isEnabled`.
  - Requires ACTIVE status and the same client_id.
  - Used by `GetCustomerCheckoutProcessor` and `SubmitCustomerPaymentProcessor`.
- `SubmitCustomerPaymentProcessor.buildPayer` calls `CustomerRepository.findById` and sends customer_no, or order_no for guests, as Payment `consumerReference`.
- `buildCardChoice` passes `customerId` to Payment `POST /payment-instrument/v1/card-tokens`.
- `GetCustomerCheckoutProcessor` calls `PaymentStoredCardClient.list(clientId, customerId)` and degrades to an empty list when Payment is unavailable.

### Consumer → acquiring

No table access. Consumer code reuses:

- `CommerceCheckoutErrorCode` (client-api).
- `CommerceIdentifierGenerator`.
- `CommerceTransactionExecutor`.
- `CompensationEventRegistrar`.
- `PaymentStoredCardClient`.

### CreateCheckout initialization

`initializeCheckout` runs outside any transaction, in this order:

1. `CheckoutCommercialPort.resolve` (CCT): **fixed Mock bean** in W/configuration/CustomerCheckoutIntegrationConfiguration.java, marked TODO(CCT). Returns AE001/PLATFORM plus fixed IDs and doc refs.
2. `CheckoutInitializationContext.validateCommercial`.
3. `CheckoutRiskPort.assess` (Risk):
   - **Real HTTP**: `RiskDecisionApi` `POST /risk-decisions/v1/evaluations`, checkpoint CHECKOUT_PREPARE v5.
   - Retries once on an unknown result. REVIEW or DENY returns 422; FAILED or TIMED_OUT returns 503.
   - Code: W/integration/risk/impl/CheckoutRiskPortImpl.java.
4. `CheckoutTaxPort.calculate` (Finance): fixed Mock, zero tax, total equals net.
5. `CheckoutDocumentPort.render` (Platform): fixed Mock.
6. `buildFrozen` allocates 3 IDs.

Where the results are frozen:

- Onto the order: entity_no, source, partner, industry, mcc, tax, rounding and total; status set to OPEN.
- Into t_order_context: risk_checkout_assessment_id and the tax snapshot id/hash.
- Into t_order_legal and t_consumer_contract.
- The response returns final amounts.

Vault is not called at create, and neither is Payment. The Intent is created at the FIRST SubmitPayment from `OrderContextRepository.findFrozen`, via Payment `POST /payment-execution/v1/intents`.

### Other fixed Mocks

- `ConsumerProtectionPort` (Vault).
- `CheckoutPaymentPort` (Payment summary), always returns PROCESSING.
- `FixedPaymentTerminationClient` always returns TERMINATED, marked TODO(PAY-I12). Payment's real API terminates by intentId (`/intents/{id}/terminate`), but Commerce wants termination by order.
- `UnavailablePaymentRefundClient` always throws.

## 6. Shared DataObjects and enums

- `CommerceOrder` is read for customerId and clientId by `CardSavingEligibility`, which sits next to the consumer code.
- `CommerceCheckoutErrorCode` in client-api mixes order, refund and customer codes: CUSTOMER_NOT_FOUND, INVALID_CONSUMER_REFERENCE, STORED_CARD_NOT_FOUND.
- `CustomerCheckoutErrorCode` contains STORED_CARD_NOT_AVAILABLE.
- `CustomerStatus` is used only by consumer code and `CardSavingEligibility`.
- `PaymentStoredCardClient.StoredCard` leaks into checkout's `GetCheckoutResponse`.
- `domain/customer/CustomerCheckoutModels` (InitializationRequest, CommercialFacts, TaxFacts) is used by the CreateCheckout/transaction freeze despite its package name.
- SQL still accepts `CheckoutSessionStatus.PREPARING`.

## 7. External contracts

### Inbound Client API

Prefix `/commerce/client-gateway-api/v1`. Controllers are under W/ext/rest/external/clientapi/v1.

| route | owner module |
|---|---|
| `POST /checkouts` | checkout / transaction |
| `GET /orders/{orderNo}`, `GET /orders?clientOrderId=` | transaction |
| `POST /orders/{orderNo}/cancel` | command |
| `GET /orders/{orderNo}/refundable-amount` | transaction / command |
| `POST/GET /orders/{orderNo}/refunds`, `GET /refunds/{no}`, `GET /refunds?clientRefundId=` | command |
| `POST /customers`, `GET /customers/{no}`, `GET /customers?consumerReference=`, `DELETE /customers/{no}` | consumer |
| `GET /customers/{no}/stored-cards`, `DELETE /customers/{no}/stored-cards/{token}` | consumer |

### Inbound Customer API (consumer-facing checkout)

Prefix `/customer-gateway-api/v1/checkouts`. All routes belong to checkout:

- `GET /{token}`
- `PUT /{token}/consumer`
- `POST /{token}/payments`
- `GET /{token}/payment-submissions/{id}`

There is **no separate acceptance endpoint**. Acceptance happens inside `POST /payments`.

OpenAPI files: P/api/openapi/client-v1.yaml and P/docs/api/customer-checkout.openapi.json.

### Outbound `@ImportHttpServices`

- **`payment-orchestration-service`** (W/configuration/PaymentClientConfiguration.java):
  - PaymentIntentApi: create intent, pay, getSubmission (EX-I06). Owned by checkout.
  - PaymentInstrumentApi: card-tokens. Owned by checkout.
  - StoredCardApi: list, delete, invalidation. Owned by consumer.
- **`risk-service`**: RiskDecisionApi, owned by the checkout/transaction initialization.
- **`platform-support-service`**: WebhookEventApi `POST /webhooks/v1/events`, shared across acquiring.
- CCT, Finance/Tax, Vault and Platform documents have **no HTTP client**. They are ports with Mock beans.

### MQ

`W/ext/mq/PaymentSucceededListener`, owned by transaction:

- Exchange `topic_payment_result`, routing key `payment.succeeded.v1`.
- Queue `cg_commerce_payment_result`: quorum, delivery limit 5.
- Dead letters: `dlx_commerce_payment_result` → `cg_commerce_payment_result_dlq`.
- Manual ack. Conflicts and runtime errors are requeued; FailException and IllegalArgumentException are rejected to the DLQ.
- There is **no MQ producer**. Client webhooks go through compensation to the Platform HTTP API.

### Compensation handlers

Foundation, after-commit, in W/ext/job:

| event type | content | owner |
|---|---|---|
| `CLIENT_WEBHOOK_REQUESTED` | order.paid, order.cancelled, refund.created; RFC 8785 hashes | shared acquiring |
| `SUBMISSION_EVENT_TYPE` | refund submission (the constant name is odd) | command |
| `STORED_CARD_INVALIDATION` | invalidate stored cards after Customer delete | consumer |

### XXL-Job

There is no `@XxlJob` in code, only test yml config. The scan/recovery job is not wired.

## 8. Tests

- **Size:** 33 test classes; 163 `@Test` plus 122 `@ParameterizedTest` = 285 methods. The ledger's 764/764 (2026-10-15) is consistent with parameterized expansion. Not re-run by me.
- **Base classes:**
  - Foundation `TestBase` → `test/base/MockTestBase` (RANDOM_PORT, profile mock).
  - Below that: `AbstractCreateCheckoutApiTest`, `AbstractCustomerApiTest`, `AbstractGetOrderApiTest`, `AbstractPaymentResultTest`, `AbstractCreateRefundApiTest`.
  - There is also `ManualTestBase`.
- **Test composition root:** `TestApplication` imports `CommerceApplication` and uses `@EnableMockHttpServer`.
- **HTTP DTO mocks:** MockPaymentIntentApi, MockPaymentInstrumentApi, MockStoredCardApi, MockRiskDecisionApi, MockWebhookEventApi.
- **`@Primary` port replacements:** CheckoutCommercialMocker, ConsumerProtectionMocker, CheckoutIntentMocker (CheckoutPaymentPort), PaymentTerminationMocker. The production defaults are themselves fixed Mocks.
- **Infrastructure:**
  - Local MySQL 127.0.0.1:13306/mor_commerce with manual DDL.
  - Redis 16379, used for TRY_LOCK `order:{id}`.
  - Real RabbitMQ for the listener tests.
- **Cleanup:**
  - AfterEach waits for MockHttpServer and the compensation dispatcher/handlers to go idle, then asserts no unexpected calls.
  - `CheckoutDataMocker.clear` runs one transaction over all owned orders plus the two test Clients' customers, settings and tasks.
- **Cross-boundary tests:** CreateCheckoutCustomerTest (3), CustomerApiTest (4), StoredCardApiTest (3), StoredCardCheckoutTest (6). All rely on the shared DB and shared cleanup.

## 9. Hotspots, ranked by risk

1. **Payment data keyed by Commerce `customer_id`.** Card-token customerId, StoredCardApi by customerId, and customer_no as Payment consumerReference. Moving t_customer to its own DB must keep the IDs globally stable, and Payment becomes a third party to the consumer split.
2. **Live eligibility read on every GET checkout and SubmitPayment.** The chain is order.customer_id → Customer ACTIVE and same client → setting enabled. Today it is a local DB read that fails hard. After the split it becomes a synchronous cross-service call and needs a degrade policy. The Payment stored-card list already degrades to empty.
3. **Vault/consumer-profile premise mismatch.** Tokens are per order in t_consumer_context. Phone and consumer_reference are never written, and Protect/Reveal is a Mock. Putting the profile, Email and Phone in consumer core needs new design.
4. **CreateCheckout Customer resolution.** It runs as a separate pre-save transaction and becomes a remote create-or-get after the split, with orphan Customers and races. If a Customer is deleted between resolve and order insert, the order references a DELETED customer; eligibility then returns null, so this is safe. Idempotency relies on `uk(client_id, active_reference)`.
5. **Freeze at create vs preparation drift.**
   - The schema, PDManer comments, t_order_context and order table comments still say the freeze happens at payment preparation.
   - SQL still guards `preparation_command_id IS NULL` and accepts session `PREPARING`.
   - `context_format` is hard-coded 'TERM_PRICE', and `t_command_idempotency` is dead.
   - V2 should decide whether to drop or keep these columns.
6. **Legal acceptance inside SubmitPayment.**
   - Acceptances and evidence are committed under the Redis TRY_LOCK before Intent creation.
   - An existing acceptance locks SaveConsumer and gates local-only Cancel through cross-table SQL.
   - That couples checkout, transaction and command in one statement.
7. **Order CAS statements span tables of 3 proposed modules.** These are `recordPaymentSubmission`, `recordConsumerChange`, `cancel` and `reserve`. Ownership of `t_commerce_order.version` bumps must be assigned.
8. **Direct Pay vs Hosted Checkout.**
   - DIRECT_PAY is a reserved enum value with no entry point.
   - Cancel, apply-success and GetOrder tolerate a null session.
   - Intent creation exists only in checkout's SubmitPayment, so Direct Pay needs an Intent path in transaction or command.
9. **Refund position straddles transaction and command, and the cancel/refund Payment adapters are Mocks.** Cancel always gets TERMINATED, refund is always unavailable, and refund terminal webhooks are TODO. Real PAY-I12 is keyed by intentId.
10. **Shared `t_compensation_event` and registrar.**
    - The consumer needs its own compensation table and scheduler. XXL-Job is not wired anywhere.
    - Test cleanup joins compensation with t_customer.
    - Dead code to drop during the move:
      - `CheckoutRepository(+Impl)`, which has no consumers.
      - `CheckoutSessionMapper.expire`.
      - `CheckoutDocumentLinkClient.links(FrozenCheckoutRecord)`.
      - The unused client-contract-api dependency.
      - The stale application-auto.yml.

## Uncertainty

- Tests were not run and the local DB was not checked.
- Whether t_client_stored_card_setting belongs to consumer or to Client config is a design decision.
- The module labels for t_order_legal and t_order_refund_position are my judgment.
