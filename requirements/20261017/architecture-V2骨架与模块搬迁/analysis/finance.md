# Finance service boundary inventory (read-only)

Root: `/Users/kenny/Desktop/Workspace/Code-MoR-refactor/finance-service`. Nothing was edited.

**Headline:** only 34 of the 84 schema tables have code, all of it in the accounting module. Every implemented write that crosses a future core boundary is ledger plus account. That is because `t_account` is both the ledger posting account and the balance projection. Reconciliation, tax and payout have no code yet. Their cross-core coupling exists only in the schema and in the design notes in `docs/database/FINANCE_PHYSICAL_DECISIONS.md`.

## 1. Maven module layout

| Module | Content |
|---|---|
| `paytech-finance-api` | The API module. 62 files of `@HttpExchange` contracts and request/response models under `com.paytech.finance.api.{accounting,balance}`. |
| `paytech-finance-accounting` | **All business code.** 344 files in `com.paytech.finance.accounting` under `core/{enums,constant,dal/{dataobject,mapper}}`, `domain/{model,view}`, `domainservice/{repository,service,validator}` and `service/processor`. Mapper XML is in `src/main/resources/mapper/accounting/`. |
| `paytech-finance-treasury` | Empty placeholder. Its `package-info.java` says treasury owns channel clearing, bank reconciliation and manual payout, while book balances stay with accounting. |
| `paytech-finance-tax` | Empty placeholder with only a `package-info.java`. |
| `paytech-finance-web` | **The deployable.** `com.paytech.finance.FinanceApplication` is the Spring Boot main class. It holds 12 REST controllers under `ext/rest/accounting` and `ext/rest/balance`, plus their converters and configuration (`FinanceIdentifierProperties`, `FinancePersistenceConfiguration`, `FinanceRuntimeConfiguration`). |
| `paytech-finance-test` | 66 test files. It is not published (`maven.install.skip` and `maven.deploy.skip` are true). |

- `paytech-finance-test/.../auto/FinanceModuleBoundaryTest.java` freezes the module set and the dependency lists of each POM. It asserts that treasury and tax have no dependencies, that only web is executable, and that Nacos, RabbitTemplate and Mockito are absent from the runtime classpath.
- The accounting module depends on `paytech-mybatis-support`, `paytech-redis-starter`, `paytech-snowflake-id`, `paytech-json-canonicalization`, jackson-core, jakarta.validation, spring-context, spring-tx, mybatis and lombok.
- The README says business modules may not depend on each other or touch each other's tables, and that orchestration happens in web Processors (see `agent-docs/decisions/2026-09-05-business-module-collaboration-policy.md`).
- In practice, every future core shares the single package `com.paytech.finance.accounting`. **No package boundary separates them today.**

## 2. Table inventory

Schema: `docs/database/sql/finance-schema.sql` has 84 tables. Seed data is in `docs/database/sql/finance-initial-data.sql`. "Impl" means a Mapper XML references the table.

### Ledger (all implemented)

| Table | Reason |
|---|---|
| `t_chart_of_accounts`, `t_chart_account` | Chart of accounts and its leaf accounts. |
| `t_ledger_book`, `t_book_sequence` | Books and the per-book journal sequence. |
| `t_fiscal_calendar`, `t_accounting_period` | Calendar and periods. |
| `t_accounting_rule_bundle`, `t_accounting_rule_bundle_ver`, `t_accounting_rule_line` | Auto-posting rules. |
| `t_accounting_instruction`, `t_instruction_line`, `t_journal_entry`, `t_journal_line` | Instructions and journals. |
| `t_manual_adjustment`, `t_manual_adjustment_line`, `t_adjustment_approval` | Manual Maker-Checker adjustments. |
| `t_ledger_period_balance`, `t_trial_balance_run`, `t_trial_balance_line`, `t_trial_balance_exception` | Trial balance. |
| `t_period_close`, `t_period_close_book`, `t_close_step`, `t_close_approval`, `t_close_account_rule` | Period close and reopen. |

### Account

| Table | Impl | Reason and uncertainty |
|---|---|---|
| `t_account` | yes | **Contested.** It is the posting account that every journal line references (`ledger_book_id`, `chart_account_id`, `owner_type`, `owner_id`, `balance_bucket`). It is also the balance projection: `debit_total`, `credit_total`, `net_balance`, `last_journal_sequence`, `balance_version`. It carries the CLIENT/PARTNER_PAYABLE PENDING, AVAILABLE and LOCKED buckets, plus CLIENT_SECURITY_DEPOSIT, CLIENT_RECOVERY_RECEIVABLE, PARTNER_RECOVERY_RECEIVABLE, the CHANNEL_* accounts and BANK_CASH. The treasury `package-info` keeps balances in accounting. |

### Billing

| Table | Impl | Reason |
|---|---|---|
| `t_transaction_economics`, `t_transaction_economic_component` | yes (read only) | Frozen economics and fee components (Client Fee, tax, Partner Share, Platform Service Charge, Channel Cost, rounding). |
| `t_transaction_economic_comp_link` | no | Links a reversal or adjustment to the original component. |
| `t_channel_cost_plan`, `t_channel_cost_plan_ver`, `t_channel_cost_scope`, `t_channel_cost_component` | yes | Estimated channel cost configuration. |

### Reconciliation

| Table | Impl | Reason and uncertainty |
|---|---|---|
| `t_channel_account` | yes (read only) | Channel clearing account per OE, MID and currency. Distinct from Payment's technical Channel Account. |
| `t_channel_open_item`, `t_channel_debit_item`, `t_duplicate_capture_item` | no | Per-transaction channel receivables and payables. |
| `t_channel_settle_batch`, `t_channel_settle_item`, `t_channel_transaction_match` | no | Settlement files and transaction matching. |
| `t_channel_cost_confirm`, `t_channel_cost_alloc` | no | Actual cost confirmation. The alloc table references `transaction_economics_id`, which is billing. |
| `t_cash_account`, `t_cash_account_binding`, `t_cash_account_binding_ver` | no | Bank cash accounts. Uncertain: they could be account-owned, because "payout cash capacity" sits under account. |
| `t_bank_statement`, `t_bank_statement_item`, `t_bank_recon_match` | no | Bank reconciliation. |
| `t_recon_exception`, `t_recon_resolution` | no | Reconciliation exceptions and resolutions. |

### Tax (none implemented)

| Tables | Reason |
|---|---|
| `t_tax_jurisdiction`, `t_tax_authority`, `t_tax_registration`, `t_tax_category`, `t_tax_category_mapping`, `t_tax_treatment` | Tax reference data. |
| `t_tax_rule`, `t_place_of_supply_rule`, `t_tax_rate`, `t_tax_evidence_rule` | Tax rules. |
| `t_tax_exchange_rate`, `t_tax_snapshot`, `t_tax_evidence`, `t_tax_line` | Exchange rates and tax calculation. |
| `t_fiscal_number_sequence`, `t_fiscal_record`, `t_fiscal_record_line` | Fiscal facts and numbering. |
| `t_tax_period`, `t_tax_liability_summary`, `t_tax_return`, `t_tax_return_line` | Filing. |
| `t_tax_payment`, `t_tax_payment_event` | Tax payment. Uncertain: these carry `tax_payable_account_id`, `cash_account_binding_ver_id`, `cash_account_id` and `journal_entry_id`, so they span tax, reconciliation, account and ledger. |

### Payout (none implemented)

| Tables | Reason |
|---|---|
| `t_beneficiary_account`, `t_payout`, `t_payout_approval`, `t_payout_execution`, `t_payout_evidence_ref`, `t_payout_event` | Payout root, approvals, executions and evidence. |
| `t_finance_restriction_proj` | Projection of Client & Contract (CCT) and Risk restrictions, used as the fail-closed payout gate. |

### Shared or unclear

| Table | Impl | Reason |
|---|---|---|
| `t_currency_spec` | yes | Currency precision and rounding, used by both billing and ledger. |
| `t_finance_entity` | yes | Book entity, referenced by billing as `finance_entity_id` and by tax registration. Probably ledger. |
| `t_compensation_event` | yes (counted by close) | Each core would need its own copy. |
| `t_command_idempotency` | no | Retire it. The 2026-09-17 Gateway idempotency decision forbids new business use. |

## 3. Cross-core SQL

All implemented multi-table statements cross only ledger and account, except one that crosses into reconciliation by owner type. Mapper XML directory: `paytech-finance-accounting/src/main/resources/mapper/accounting/`.

| Mapper and id | Tables |
|---|---|
| `AccountMapper.selectOwnerBalances`, `selectPage`, `countPage` | `t_account` (account) joined to `t_chart_account` (ledger) |
| `AccountMapper.selectProjectionChecks` | `t_account` with `t_journal_entry` and `t_journal_line` (ledger). It compares the projection with the journal sums. |
| `AccountMapper.selectJournalTotals` | `t_journal_entry` and `t_journal_line`. The statement is ledger-only SQL, but it lives in the account mapper and feeds the projection rebuild. |
| `JournalLineMapper.selectAccountLines` | `t_account`, `t_journal_line`, `t_journal_entry`, `t_accounting_instruction`, `t_manual_adjustment` |
| `JournalLineMapper.selectEntryLines` | `t_journal_line`, `t_account`, `t_chart_account` |
| `JournalLineMapper.selectTrialAggregates` | `t_journal_line`, `t_journal_entry`, `t_account`, `t_chart_account` |
| `JournalLineMapper.selectProfitCloseBalances` | `t_journal_line`, `t_journal_entry`, `t_account`, `t_chart_account` |
| `PeriodCloseMapper.countNonZeroTreasuryAccounts` | `t_account` filtered by `owner_type IN ('CHANNEL_ACCOUNT','CASH_ACCOUNT') AND net_balance <> 0`. The rows are account, the meaning is reconciliation, and the caller is ledger close. |
| `PeriodCloseMapper.countPendingCompensationEvents` | `t_compensation_event` across the whole database (`execute_status IN ('INIT','PROCESSING','ERROR')`) |

Ledger-internal multi-table statements are not cross-core: `JournalEntryMapper.selectDetail` and `countUnbalancedInPeriod`, `JournalLineMapper.countAccountLines` and `selectCreditNetUpTo`, `LedgerBookMapper.selectPage`, `PeriodCloseBookMapper.countInvalidFinalBooks`, `PeriodCloseMapper.selectView` and `selectViewPage`, `TrialBalanceLineMapper.selectViews`, `TrialBalanceRunMapper.selectView` and `selectPage`.

### Schema-level cross-core references with no SQL yet

These are logical reference columns, not physical FKs:

- **Billing to tax:** `t_transaction_economics.tax_snapshot_id` and `t_transaction_economic_component.tax_line_id`.
- **Ledger to billing:** `t_accounting_instruction.transaction_economics_id`.
- **Reconciliation to billing:** `t_channel_open_item`, `t_channel_debit_item` and `t_channel_cost_alloc` carry `transaction_economics_id`.
- **Back to the economics:** `t_transaction_economics.duplicate_capture_item_id` points to reconciliation.
- **Into the ledger:** `t_duplicate_capture_item`, `t_payout`, `t_payout_event` and `t_tax_payment` carry `journal_entry_id`, and `t_recon_resolution` carries `instruction_id`.
- **Payout and tax to account:** `t_payout.available_account_id` and `locked_account_id`, and `t_tax_payment.tax_payable_account_id`.
- **Reconciliation to payout and tax:** `t_bank_recon_match` carries `channel_batch_id`, `payout_execution_id` and `tax_payment_id`, and `t_payout_execution` carries `cash_account_binding_ver_id` and `cash_account_id`.
- **Payout to reconciliation:** `t_payout_event.bank_recon_match_id`.
- **Fiscal to billing/tax:** `t_fiscal_record` carries `tax_snapshot_id` and `source_event_key`.

## 4. Cross-core transactions

There is no `@Transactional`. Transactions use TransactionTemplate, and some services assert that the caller already has a transaction open (`TransactionSynchronizationManager.isActualTransactionActive()`). `AbstractLockProcessor` comes from `com.paytech.foundation.redis.core.processor`; transactions inside `processWithinLock` are explicit TransactionTemplate calls.

| Class#method | Cores written |
|---|---|
| `JournalPostingServiceImpl#post` (caller's tx) | Ledger tables (`t_book_sequence`, `t_journal_entry`, `t_journal_line`, instruction status) and account (`t_account` lock and `applyPosting`). This is the posting kernel behind every row below. |
| `TransactionPostingServiceImpl#post` (caller's tx; only callers are tests) | Ledger tables (instruction, lines, journals) and account (`t_account` insert and balance). It also reads billing (economics and components) and reconciliation (`t_channel_account`) inside the tx. |
| `ApproveManualAdjustmentProcessor#processWithinLock` | Ledger (adjustment, approval, instruction, journal) and account (balances). |
| `StartPeriodCloseProcessor#postBookProfitClose` | Ledger (instruction, journal, `t_period_close_book`) and account (`ensureResultAccount` insert and balances). |
| `ApprovePeriodCloseProcessor#approveReopen` | Ledger (period, close, approval, reversal journal) and account (balances). |
| `TrialExceptionServiceImpl#approveProjectionRebuild` | Account (`rebuildProjection` via CAS) and ledger (`t_trial_balance_exception`). |

Single-core transactions:

- **Ledger:** `LedgerSetupServiceImpl#createLedgerBook`, `TrialBalanceServiceImpl` run (it reads `t_account` but writes only trial tables and `t_ledger_period_balance`), `PeriodCloseServiceImpl` start, fail, freeze, approve, reject, startReopen and rejectReopen, `AccountingRuleServiceImpl`, `ManualAdjustmentServiceImpl`, `AccountingPeriodServiceImpl`.
- **Billing:** `ChannelCostPlanServiceImpl`.
- **Account:** `LedgerSetupServiceImpl#createAccount`, a single insert with no transaction.

## 5. Cross-core Java calls

All classes share one package, so these edges come from injected repositories mapped to tables.

| Edge | Representative classes |
|---|---|
| Ledger to billing | `TransactionPostingServiceImpl` injects `TransactionEconomicsRepository` and `TransactionEconomicComponentRepository`. |
| Ledger to reconciliation | `TransactionPostingServiceImpl` injects `ChannelAccountRepository` to resolve the CHANNEL_ACCOUNT owner. `PeriodCloseServiceImpl` counts treasury accounts. |
| Ledger to account | `JournalPostingServiceImpl`, `TransactionPostingServiceImpl`, `PeriodCloseServiceImpl`, `TrialBalanceServiceImpl`, `TrialExceptionServiceImpl`, `LedgerQueryServiceImpl`, `LedgerSetupServiceImpl`, `SubmitManualAdjustmentProcessor`, `StartPeriodCloseProcessor`, `StartPeriodReopenProcessor` and `ApprovePeriodCloseProcessor` all use `AccountRepository`. |
| Account to ledger | `BalanceQueryServiceImpl` uses `LedgerBookRepository` and chart data from the `selectOwnerBalances` join. |
| Billing to shared | `ChannelCostEstimationServiceImpl`, `ChannelCostPlanServiceImpl` and `CreateChannelCostVersionProcessor` use `CurrencySpecRepository`. |

`ChannelCostEstimationService` is read-only and has no production caller. Only its tests call it. `TransactionPostingService` also has no production caller; only `AbstractTransactionPostingTest` and the posting tests call it.

## 6. Shared types used by more than one core

- **`AmountSource`:** GROSS_AMOUNT, CLIENT_PRINCIPAL, CLIENT_FEE, SELLER_TAX, CHANNEL_COST, PARTNER_SHARE, PLATFORM_SERVICE_CHARGE. Billing's `component_type` codes are the keys that ledger rule lines match on, through `TransactionPostingContext.amountOf`.
- **`OwnerType`:** OPERATING_ENTITY, FINANCE_ENTITY, CLIENT, LOCAL_PARTNER, CHANNEL_ACCOUNT, CASH_ACCOUNT, TAX_AUTHORITY. **`OwnerRole`** (BOOK_ENTITY, CLIENT, LOCAL_PARTNER, CHANNEL_ACCOUNT, PLATFORM_FINANCE_ENTITY, TRANSACTION_OE, TAX_AUTHORITY), **`BalanceBucket`** (PENDING, AVAILABLE, LOCKED, NONE) and **`AccountIdentityRule`** span account, ledger, reconciliation and tax.
- **Others:** `EconomicsType` (PAYMENT, REFUND, DUPLICATE_CAPTURE, DUPLICATE_COMPENSATION, DISPUTE, COST_ADJUSTMENT) and `LedgerEntityType` span billing and ledger. `AutoInstructionType` (PAYMENT_SUCCEEDED, PLATFORM_SERVICE_CHARGE_PARTNER, PLATFORM_SERVICE_CHARGE_PLATFORM) couples billing facts to ledger instructions. `StandardAccountCodes` spans account and ledger. `CurrencySpec` spans billing and ledger. `AccountingLocks` provides the Redis lock keys. `PeriodCloseContentHash.sha256` is reused by transaction posting.
- **DataObjects:** `Account`, `LedgerBook` and `ChartAccount` are used by both the account code and the ledger code.

## 7. External contracts

### Inbound HTTP

Defined in `paytech-finance-api`, with controllers in `paytech-finance-web/src/main/java/com/paytech/finance/ext/rest`.

| Path | Owning core |
|---|---|
| `/finance-entities/v1` | Ledger |
| `/fiscal-calendars/v1` (and `/{id}/periods`) | Ledger |
| `/accounting-periods/v1/{id}/open` | Ledger |
| `/accounting-periods/v1/{id}/closes`, `/reopens`, `.../approve`, `.../reject` | Ledger |
| `/ledger-books/v1` and `/{id}/accounts` (POST) | Ledger. Creating accounts could arguably be account-owned. |
| `/ledger-books/v1` GET, accounts, journal-lines, journal-entries | Ledger, with account reads |
| `/ledger-books/v1/{id}/periods/{pid}/trial-balances` | Ledger |
| `/trial-balance-exceptions/v1/{id}/projection-rebuild` (and approve) | Ledger, writing account |
| `/accounting-rule-bundles/v1` (versions, submit, approve, reject) | Ledger |
| `/manual-adjustments/v1` | Ledger |
| `/channel-cost-plans/v1` | Billing |
| `/balances/v1/clients/{clientId}`, `/balances/v1/partners/{partnerId}` | Account |

### Not present

- **Outbound clients:** none. There are no outbound `@HttpExchange` clients to other services.
- **MQ:** none. `FinanceModuleBoundaryTest` asserts RabbitTemplate is not on the classpath.
- **Jobs and handlers:** there are no XXL-Job handlers and no compensation-event handlers or library dependency. `t_compensation_event` is only counted by period-close step 2.
- **Event wiring:** no event triggers TransactionPosting yet. The README says economics, AUTO posting, maturation, reconciliation, payout and tax are not implemented.

## 8. Tests

### Counts and infrastructure

| Item | Value |
|---|---|
| Test files | 66 |
| `*Test` classes | 39, all `auto` (includes `FinanceApplicationTest`, `FinanceModuleBoundaryTest` and two pure-Java content-hash tests) |
| Base classes | `TestBase` from paytech-test-support, `MockTestBase`, and 8 `Abstract*Test` bases (ChannelCostApi, ChannelCostEstimation, ManualAdjustmentApi, PeriodCloseApi, PeriodReopenApi, RuleBundleApi, TransactionPosting, TrialBalanceApi) |
| Test support | Builders (`AccountingSetupRequestBuilder`, `ChannelCostEstimateCommandBuilder`, `ChannelCostVersionRequestBuilder`, `EconomicComponentBuilder`, `RuleVersionRequestBuilder`, `SubmitManualAdjustmentRequestBuilder`, `TransactionEconomicsBuilder`), Checkers (`FinanceResponseChecker`, `LedgerChecker`), local Mockers (`LedgerMocker`, `LedgerFixture`, `TransactionEconomicsMocker`) |
| MySQL | `127.0.0.1:13306/mor_finance` |
| Redis | `16379`, key prefix `finance-mock`, needed for resource locks |
| RabbitMQ | Not used |

`MockTestBase` uses `@SpringBootTest(classes = FinanceApplication.class, webEnvironment = RANDOM_PORT)` and imports the cleaners, `LedgerMocker`, `LedgerChecker` and `TransactionEconomicsMocker`.

### Cleanup

- **`FinanceDataCleaner`** deletes 28 fully owned tables, which mix every implemented core: `t_channel_cost_component`, `t_channel_cost_scope`, `t_channel_cost_plan_ver`, `t_channel_cost_plan`, `t_close_approval`, `t_close_step`, `t_period_close_book`, `t_period_close`, `t_ledger_period_balance`, `t_trial_balance_exception`, `t_trial_balance_line`, `t_trial_balance_run`, `t_journal_line`, `t_journal_entry`, `t_instruction_line`, `t_accounting_instruction`, `t_adjustment_approval`, `t_manual_adjustment_line`, `t_manual_adjustment`, `t_transaction_economic_component`, `t_transaction_economics`, `t_channel_account`, `t_account`, `t_book_sequence`, `t_ledger_book`, `t_accounting_period`, `t_fiscal_calendar`, `t_finance_entity`.
- It also runs `deleteNonReserved` on 6 reserved tables (`t_accounting_rule_line`, `t_accounting_rule_bundle_ver`, `t_accounting_rule_bundle`, `t_chart_account`, `t_chart_of_accounts`, `t_currency_spec`) and restores the seeded rule bundle. Before each test, `assertEmpty` checks 45 reserved rows.
- **`FinanceRedisCleaner`** scans and deletes the test key prefix.
- **`AccountingTestMapper`** inserts economics, components and channel accounts directly.
- **One cleaner covers all cores.**

**DDL gap:** `paytech-finance-test/src/test/resources/init/ddl.sql` creates only 26 tables. It omits the trial-balance, period-close, `t_ledger_period_balance` and `t_compensation_event` tables, yet the cleaner deletes from them and close reads them. The tests therefore rely on the full `finance-schema.sql` being applied.

### Tests that span cores

- **`TransactionPostingTest` and `TransactionPostingRejectionTest`:** billing fixtures, reconciliation `t_channel_account`, ledger and account.
- **`QueryOwnerBalanceTest`:** account and ledger.
- **Period close, reopen, trial balance and projection rebuild tests:** ledger and account.
- **`ChannelCostEstimation*` tests:** billing and `t_currency_spec`.

No close test drives the non-zero treasury-account step.

## 9. Logic hotspots, ranked by risk

1. **`t_account` is both posting account and projection.** `JournalPostingServiceImpl#post` writes the journal and updates the balance in one transaction under the lock order period, book sequence, then accounts ascending. Separating account from ledger breaks the rule that balance equals the sum of journals. It also breaks the projection checks in `selectProjectionChecks` and the trial balance.
2. **Account operations are journals in the design.** Payout lock, release and paid are journal postings in `FINANCE_PHYSICAL_DECISIONS.md`: `PAYOUT_LOCK` is Dr AVAILABLE / Cr LOCKED, `PAYOUT_RELEASE` reverses it, and `PAYOUT_PAID` is Dr LOCKED / Cr BANK_CASH. Deposit receipt, use and recovery are journals too. The payout gate must lock AVAILABLE, LOCKED, deposit and recovery accounts and recheck Risk in the same transaction. An account core cannot change a bucket without ledger.
3. **Auto-posting reads billing tables inside the posting transaction.** `TransactionPostingServiceImpl` also enforces billing invariants: the partner triple must be all-or-none, and the PLATFORM_SERVICE_CHARGE party must equal the finance entity. Component codes drive the rule lines through `AmountSource`. Splitting these needs a frozen, versioned economics contract.
4. **Accounts are created during posting.** `TransactionPostingServiceImpl#resolveAccount` and `PeriodCloseServiceImpl#ensureResultAccount` insert `t_account` rows in the posting transaction using `AccountIdentityRule`. A separate account database would make posting depend on a remote write.
5. **Channel-account owner identity crosses into reconciliation.** `t_account.owner_id` equals `t_channel_account.id` and is resolved in the posting transaction. BANK_CASH uses `owner_id = t_cash_account.id`. Close step 3 fails closed on non-zero CHANNEL_ACCOUNT or CASH_ACCOUNT balances. Reconciliation and ledger are coupled on identity and on close gating.
6. **Channel batch settlement is designed as one transaction across three cores.** The design idempotently creates the `CHANNEL_BATCH_SETTLEMENT:<batch_id>` aggregate journal and advances the open and debit item settled amount, outstanding amount, status and the account projection, all in one transaction. That spans reconciliation, ledger and account.
7. **The bank recon match gate spans four cores.** Confirmation locks `t_bank_statement_item` across channel, payout and tax targets and posts BANK_CASH journals. Payout PAID, tax payment and late-debit (`LATE_DEBIT:<bank_item_id>`) journals hang off it. That spans reconciliation, payout, tax, ledger and account.
8. **Period-close step 2 counts across the whole database.** It counts pending instructions, unbalanced entries and every `t_compensation_event` in the database. After a split, close needs a quiescence check across five databases. Otherwise the count silently covers only the ledger's own events.
9. **The projection rebuild writes two cores in one transaction.** It recomputes `t_account` from journals and writes `t_trial_balance_exception` together. Trial exceptions also check the dual-book platform service charge between payable and receivable.
10. **Channel cost is split across cores.** The estimate is billing and has no caller. Confirmation and allocation are reconciliation and point to `transaction_economics_id` and the settle item. The fee accrual and expense journals are ledger. The tax link from `tax_snapshot_id`, `tax_line_id` and the SELLER_TAX party resolves to a TAX_AUTHORITY account owner. Cost and tax data become references that cross three databases.

Runners-up:

- `BalanceQueryServiceImpl` fails closed using ledger chart and book data.
- Manual adjustment lines reference account IDs, and journal views join `t_manual_adjustment`.
- `t_currency_spec` and `t_finance_entity` are shared reference data that would need replication.
