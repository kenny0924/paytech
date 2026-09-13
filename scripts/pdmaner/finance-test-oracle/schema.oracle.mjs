export const APPROVED_TABLES = Object.freeze([
  "t_currency_spec",
  "t_chart_of_accounts",
  "t_chart_account",
  "t_finance_entity",
  "t_ledger_book",
  "t_account",
  "t_fiscal_calendar",
  "t_accounting_period",
  "t_book_sequence",
  "t_tax_jurisdiction",
  "t_tax_authority",
  "t_tax_registration",
  "t_tax_category",
  "t_tax_category_mapping",
  "t_tax_treatment",
  "t_tax_rule",
  "t_place_of_supply_rule",
  "t_tax_rate",
  "t_tax_evidence_rule",
  "t_tax_exchange_rate",
  "t_tax_snapshot",
  "t_tax_evidence",
  "t_tax_line",
  "t_fiscal_number_sequence",
  "t_fiscal_record",
  "t_fiscal_record_line",
  "t_tax_period",
  "t_tax_liability_summary",
  "t_tax_return",
  "t_tax_return_line",
  "t_tax_payment",
  "t_tax_payment_event",
  "t_transaction_economics",
  "t_transaction_economic_component",
  "t_transaction_economic_comp_link",
  "t_channel_cost_plan",
  "t_channel_cost_plan_ver",
  "t_channel_cost_scope",
  "t_channel_cost_component",
  "t_channel_cost_confirm",
  "t_channel_cost_alloc",
  "t_accounting_rule_bundle",
  "t_accounting_rule_bundle_ver",
  "t_accounting_rule_line",
  "t_accounting_instruction",
  "t_instruction_line",
  "t_journal_entry",
  "t_journal_line",
  "t_manual_adjustment",
  "t_manual_adjustment_line",
  "t_adjustment_approval",
  "t_ledger_period_balance",
  "t_channel_account",
  "t_channel_open_item",
  "t_channel_debit_item",
  "t_duplicate_capture_item",
  "t_channel_settle_batch",
  "t_channel_settle_item",
  "t_channel_transaction_match",
  "t_cash_account",
  "t_cash_account_binding",
  "t_cash_account_binding_ver",
  "t_bank_statement",
  "t_bank_statement_item",
  "t_bank_recon_match",
  "t_recon_exception",
  "t_recon_resolution",
  "t_beneficiary_account",
  "t_finance_restriction_proj",
  "t_payout",
  "t_payout_approval",
  "t_payout_execution",
  "t_payout_evidence_ref",
  "t_payout_event",
  "t_trial_balance_run",
  "t_trial_balance_line",
  "t_trial_balance_exception",
  "t_period_close",
  "t_period_close_book",
  "t_close_step",
  "t_close_approval",
  "t_close_account_rule",
  "t_compensation_event",
  "t_command_idempotency",
]);

export const APPROVED_DIAGRAMS = Object.freeze({
  ledger_and_close: "账簿、账户与关账",
  tax_and_fiscal: "税务与财政事实",
  economics_and_cost: "交易经济与渠道成本",
  channel_cash_recon: "Channel 清算、现金与对账",
  manual_payout: "手工出款与银行确认",
  reliability: "可靠执行与命令幂等",
});

export const EXPECTED_TOTALS = Object.freeze({
  tables: 84,
  fields: 1109,
  uniqueIndexes: 145,
  accessIndexes: 155,
  relations: 160,
  oneToOneRelations: 22,
  diagramGroups: 6,
});

export const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
export const EXPECTED_MODEL_DIGEST = "0ce10fb68b98492017b2ba60bc773d7d2828f4b62c01b45153c757bf8f9c72df";
export const EXPECTED_STRUCTURAL_DIGEST = "3ea1d613278f7e4be42860048596d607b134b5a85572cbce8e0684ca99504255";

export const APPROVED_VERSION_PATHS = Object.freeze(`
t_ledger_book.version
t_account.balance_version
t_fiscal_calendar.version
t_accounting_period.version
t_tax_registration.version
t_tax_category_mapping.version
t_tax_rule.version
t_fiscal_number_sequence.version
t_tax_period.version
t_tax_return.version
t_tax_payment.version
t_channel_cost_plan.version
t_accounting_rule_bundle.version
t_accounting_instruction.version
t_manual_adjustment.version
t_channel_open_item.version
t_channel_debit_item.version
t_duplicate_capture_item.version
t_cash_account_binding.version
t_bank_recon_match.version
t_recon_exception.version
t_beneficiary_account.version
t_finance_restriction_proj.version
t_payout.version
t_payout_execution.version
t_period_close.version
`.trim().split(/\s+/u));

export const APPROVED_HASH_PATHS = Object.freeze(`
t_tax_registration.vault_metadata_hash
t_tax_rule.content_hash
t_tax_exchange_rate.source_hash
t_tax_snapshot.request_hash
t_tax_snapshot.buyer_tax_vault_metadata_hash
t_tax_snapshot.calculation_hash
t_tax_evidence.evidence_value_hash
t_tax_line.fiscal_scope_hash
t_fiscal_record.source_payload_hash
t_fiscal_record.generation_scope_hash
t_fiscal_record.content_hash
t_tax_liability_summary.source_hash
t_tax_return.content_hash
t_tax_return.file_content_hash
t_tax_payment.request_hash
t_tax_payment_event.evidence_hash
t_transaction_economics.source_payload_hash
t_transaction_economic_component.source_rule_content_hash
t_channel_cost_plan_ver.content_hash
t_channel_cost_confirm.source_hash
t_accounting_instruction.source_payload_hash
t_accounting_instruction.input_hash
t_manual_adjustment.file_content_hash
t_manual_adjustment.content_hash
t_adjustment_approval.approved_content_hash
t_duplicate_capture_item.source_payload_hash
t_channel_settle_batch.file_content_hash
t_cash_account.vault_metadata_hash
t_bank_statement.file_content_hash
t_bank_statement_item.description_hash
t_recon_resolution.content_hash
t_recon_resolution.approved_content_hash
t_beneficiary_account.vault_metadata_hash
t_beneficiary_account.content_hash
t_finance_restriction_proj.source_payload_hash
t_finance_restriction_proj.reason_set_hash
t_payout.beneficiary_content_hash
t_payout.control_set_hash
t_payout.eligibility_hash
t_payout.request_hash
t_payout.approval_hash
t_payout_approval.approved_content_hash
t_payout_execution.request_hash
t_payout_execution.control_set_hash
t_payout_execution.eligibility_hash
t_payout_evidence_ref.file_content_hash
t_trial_balance_run.content_hash
t_trial_balance_exception.resolution_content_hash
t_period_close.close_hash
t_close_step.result_hash
t_close_approval.approved_close_hash
t_compensation_event.payload_hash
t_command_idempotency.request_hash
t_command_idempotency.response_hash
`.trim().split(/\s+/u));

export const APPROVED_ACTIVE_PATHS = Object.freeze(`
t_tax_payment.active_slot
t_payout_execution.active_slot
t_period_close.active_slot
`.trim().split(/\s+/u));

export const EXPECTED_IMMUTABLE_TABLES = Object.freeze([
  "t_place_of_supply_rule",
  "t_tax_rate",
  "t_tax_evidence_rule",
  "t_tax_exchange_rate",
  "t_tax_snapshot",
  "t_tax_evidence",
  "t_tax_line",
  "t_fiscal_record",
  "t_fiscal_record_line",
  "t_tax_liability_summary",
  "t_tax_return_line",
  "t_tax_payment_event",
  "t_transaction_economics",
  "t_transaction_economic_component",
  "t_transaction_economic_comp_link",
  "t_channel_cost_scope",
  "t_channel_cost_component",
  "t_channel_cost_confirm",
  "t_channel_cost_alloc",
  "t_accounting_rule_line",
  "t_instruction_line",
  "t_journal_entry",
  "t_journal_line",
  "t_adjustment_approval",
  "t_channel_settle_batch",
  "t_channel_settle_item",
  "t_channel_transaction_match",
  "t_bank_statement",
  "t_bank_statement_item",
  "t_payout_approval",
  "t_payout_evidence_ref",
  "t_payout_event",
  "t_trial_balance_line",
  "t_close_approval",
  "t_close_account_rule",
]);

export const EXPECTED_FILE_REFERENCE_TUPLES = Object.freeze([
  Object.freeze({ table: "tax_return", id: "file_id", hash: "file_content_hash" }),
  Object.freeze({ table: "manual_adjustment", id: "file_id", hash: "file_content_hash" }),
  Object.freeze({ table: "channel_settle_batch", id: "file_id", hash: "file_content_hash" }),
  Object.freeze({ table: "bank_statement", id: "file_id", hash: "file_content_hash" }),
  Object.freeze({ table: "payout_evidence_ref", id: "file_id", hash: "file_content_hash" }),
]);

export const FORBIDDEN_TABLES = Object.freeze([
  "t_beneficiary_account_ver",
  "t_settlement_profile",
  "t_settlement_profile_ver",
  "t_profile_review",
  "t_payout_request",
  "t_settlement_run",
  "t_settlement_candidate",
  "t_candidate_requirement_snapshot",
  "t_settlement",
  "t_settlement_req_snapshot",
  "t_settlement_approval",
  "t_settlement_cancel",
  "t_settlement_cancel_approval",
  "t_settlement_state_change",
  "t_settlement_payment",
  "t_settlement_payment_attempt",
  "t_settlement_payment_event",
  "t_settlement_evidence_ref",
]);

// Independent review Oracle. Keep these literals detached from the production policy module.
export const EXPECTED_CHECK_CONTRACTS = Object.freeze({
  ck_chart_account_class: "account_class IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')",
  ck_chart_account_side: "normal_side IN ('DEBIT','CREDIT')",
  ck_account_balance_projection: "debit_total>=0 AND credit_total>=0 AND last_journal_sequence>=0 AND balance_version>=0 AND ((normal_side='DEBIT' AND net_balance=debit_total-credit_total) OR (normal_side='CREDIT' AND net_balance=credit_total-debit_total))",
  ck_book_sequence_next: "next_value>0",
  ck_chart_account_parent: "parent_account_id IS NULL OR parent_account_id<>id",
  ck_account_owner_bucket: "(owner_type='CLIENT' AND balance_bucket IN ('PENDING','AVAILABLE','LOCKED','NONE')) OR (owner_type='LOCAL_PARTNER' AND balance_bucket IN ('NONE','PENDING','AVAILABLE','LOCKED')) OR (owner_type IN ('OPERATING_ENTITY','FINANCE_ENTITY','CHANNEL_ACCOUNT','CASH_ACCOUNT','TAX_AUTHORITY') AND balance_bucket='NONE')",
  ck_currency_minor_unit: "minor_unit BETWEEN 0 AND 8",
  ck_fiscal_calendar_month: "year_start_month BETWEEN 1 AND 12",
  ck_accounting_period_range: "period_no>0 AND start_date<=end_date AND reopen_count>=0",
  ck_versioned_root_pointer: "status='DRAFT' OR current_ver_id IS NOT NULL",
  ck_tax_jurisdiction_parent: "parent_id IS NULL OR parent_id<>id",
  ck_tax_category_parent: "parent_id IS NULL OR parent_id<>id",
  ck_tax_rate_value: "rate_value BETWEEN 0 AND 1",
  ck_tax_fx_rate: "rate_value > 0 AND base_currency <> quote_currency",
  ck_tax_snapshot_amounts: "total_collected_tax >= 0 AND total_reverse_charge_tax >= 0 AND reporting_collected_tax >= 0 AND reporting_reverse_charge_tax >= 0",
  ck_tax_snapshot_exchange_rate: "(currency=reporting_currency AND exchange_rate_id IS NULL) OR (currency<>reporting_currency AND exchange_rate_id IS NOT NULL)",
  ck_tax_snapshot_buyer_id: "(buyer_tax_validation='NOT_PROVIDED' AND buyer_tax_country IS NULL AND buyer_tax_id_vault_token IS NULL AND buyer_tax_id_blind_index IS NULL AND buyer_tax_id_blind_key_ver IS NULL AND buyer_tax_id_masked IS NULL AND buyer_tax_vault_record_version IS NULL AND buyer_tax_vault_metadata_hash IS NULL) OR (buyer_tax_validation IN ('UNVERIFIED','VALID','INVALID') AND buyer_tax_country IS NOT NULL AND buyer_tax_id_vault_token IS NOT NULL AND buyer_tax_id_blind_index IS NOT NULL AND buyer_tax_id_blind_key_ver IS NOT NULL AND buyer_tax_id_masked IS NOT NULL AND buyer_tax_vault_record_version IS NOT NULL AND buyer_tax_vault_metadata_hash IS NOT NULL)",
  ck_fiscal_record_business_ref: "(business_ref_type='ORDER' AND order_id IS NOT NULL AND business_ref_id=order_id AND refund_request_id IS NULL) OR (business_ref_type='REFUND' AND order_id IS NOT NULL AND refund_request_id IS NOT NULL AND business_ref_id=refund_request_id)",
  ck_fiscal_record_sequence: "source_version=1 AND generation_key_version>0 AND sequence_value>0 AND total_amount>=0",
  ck_fiscal_record_original: "original_record_id IS NULL OR original_record_id<>id",
  ck_tax_registration_kind: "(registration_kind='REGISTERED' AND registration_no_vault_token IS NOT NULL AND registration_no_blind_index IS NOT NULL AND registration_no_blind_key_ver IS NOT NULL AND registration_no_masked IS NOT NULL AND vault_record_version IS NOT NULL AND vault_metadata_hash IS NOT NULL) OR (registration_kind='NO_REGISTRATION_REQUIRED' AND registration_no_vault_token IS NULL AND registration_no_blind_index IS NULL AND registration_no_blind_key_ver IS NULL AND registration_no_masked IS NULL AND vault_record_version IS NULL AND vault_metadata_hash IS NULL)",
  ck_tax_period_range: "period_start<=period_end",
  ck_tax_rule_lifecycle: "rule_ver>0 AND (effective_to IS NULL OR effective_from<effective_to) AND ((status='DRAFT' AND content_hash IS NULL AND checker_id IS NULL AND checker_decision IS NULL AND decision_time IS NULL) OR (status='PENDING_APPROVAL' AND content_hash IS NOT NULL AND checker_id IS NULL AND checker_decision IS NULL AND decision_time IS NULL) OR (status IN ('ACTIVE','INACTIVE') AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='APPROVED' AND decision_time IS NOT NULL) OR (status='REJECTED' AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='REJECTED' AND decision_time IS NOT NULL))",
  ck_fiscal_line_amount: "net_amount + collected_tax_amount = gross_amount",
  ck_tax_line_liability: "tax_basis >= 0 AND reporting_tax_basis >= 0 AND tax_rate BETWEEN 0 AND 1 AND collected_tax_amount >= 0 AND reverse_charge_tax_amount >= 0 AND reporting_collected_tax >= 0 AND reporting_reverse_charge_tax >= 0 AND ((liability_mode='SELLER' AND liable_party_type='OPERATING_ENTITY' AND liable_entity_no IS NOT NULL AND reverse_charge_tax_amount=0 AND reporting_reverse_charge_tax=0) OR (liability_mode='BUYER_REVERSE_CHARGE' AND liable_party_type='BUYER' AND liable_entity_no IS NULL AND collected_tax_amount=0 AND reporting_collected_tax=0))",
  ck_tax_return_file: "(file_id IS NULL AND file_content_hash IS NULL) OR (file_id IS NOT NULL AND file_content_hash IS NOT NULL)",
  ck_tax_liability_summary: "taxable_amount>=0 AND output_tax>=0 AND recoverable_tax>=0 AND reverse_charge_taxable_amount>=0 AND reverse_charge_tax_amount>=0 AND net_tax_payable=output_tax-recoverable_tax AND ((liability_mode='SELLER' AND reverse_charge_taxable_amount=0 AND reverse_charge_tax_amount=0) OR (liability_mode='BUYER_REVERSE_CHARGE' AND taxable_amount=0 AND output_tax=0 AND recoverable_tax=0 AND net_tax_payable=0))",
  ck_tax_return_lifecycle: "return_ver>0 AND ((status='DRAFT' AND content_hash IS NULL AND checker_id IS NULL AND checker_decision IS NULL AND decision_time IS NULL AND filed_time IS NULL AND authority_ref IS NULL) OR (status='PENDING_APPROVAL' AND content_hash IS NOT NULL AND checker_id IS NULL AND checker_decision IS NULL AND decision_time IS NULL AND filed_time IS NULL AND authority_ref IS NULL) OR (status='APPROVED' AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='APPROVED' AND decision_time IS NOT NULL AND filed_time IS NULL AND authority_ref IS NULL) OR (status='REJECTED' AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='REJECTED' AND decision_time IS NOT NULL AND filed_time IS NULL AND authority_ref IS NULL) OR (status='FILED' AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='APPROVED' AND decision_time IS NOT NULL AND file_id IS NOT NULL AND file_content_hash IS NOT NULL AND filed_time IS NOT NULL) OR (status IN ('ACCEPTED','AMENDMENT_REQUIRED','CLOSED') AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='APPROVED' AND decision_time IS NOT NULL AND file_id IS NOT NULL AND file_content_hash IS NOT NULL AND filed_time IS NOT NULL AND authority_ref IS NOT NULL))",
  ck_tax_return_amend: "(return_ver=1 AND amends_return_id IS NULL) OR (return_ver>1 AND amends_return_id IS NOT NULL AND amends_return_id<>id)",
  ck_channel_cost_approval: "(status IN ('DRAFT','PENDING_APPROVAL') AND checker_id IS NULL) OR (status IN ('APPROVED','ACTIVE','REJECTED','INACTIVE') AND checker_id IS NOT NULL AND checker_id<>maker_id)",
  ck_channel_cost_component: "(rate_value IS NOT NULL OR fixed_amount IS NOT NULL) AND (rate_value IS NULL OR (rate_value>=0 AND rate_value<=1)) AND ((fixed_amount IS NULL AND currency IS NULL) OR (fixed_amount IS NOT NULL AND fixed_amount>=0 AND currency IS NOT NULL)) AND (min_amount IS NULL OR min_amount>=0) AND (max_amount IS NULL OR max_amount>=0) AND (min_amount IS NULL OR max_amount IS NULL OR min_amount<=max_amount)",
  ck_channel_cost_confirm: "actual_cost>=0",
  ck_channel_cost_alloc_amount: "allocated_amount>=0",
  ck_instruction_origin: "(origin_type='AUTO' AND source_system IS NOT NULL AND source_event_key IS NOT NULL AND source_version IS NOT NULL AND source_payload_hash IS NOT NULL AND manual_adjustment_id IS NULL AND accounting_rule_bundle_ver_id IS NOT NULL AND input_hash IS NOT NULL AND maker_id IS NULL AND checker_id IS NULL) OR (origin_type='MANUAL' AND source_system IS NULL AND source_event_key IS NULL AND source_version IS NULL AND source_payload_hash IS NULL AND transaction_economics_id IS NULL AND manual_adjustment_id IS NOT NULL AND accounting_rule_bundle_ver_id IS NULL AND input_hash IS NOT NULL AND maker_id IS NOT NULL AND checker_id IS NOT NULL AND maker_id<>checker_id)",
  ck_accounting_rule_review: "(status IN ('DRAFT','PENDING_APPROVAL') AND checker_id IS NULL AND approved_time IS NULL) OR (status IN ('APPROVED','ACTIVE','RETIRED') AND checker_id IS NOT NULL AND checker_id<>maker_id AND approved_time IS NOT NULL) OR (status='REJECTED' AND checker_id IS NOT NULL AND checker_id<>maker_id AND approved_time IS NULL)",
  ck_accounting_rule_version_shape: "bundle_ver>0 AND (effective_to IS NULL OR effective_from<effective_to)",
  ck_accounting_rule_line_no: "line_no>0",
  ck_instruction_line_source: "(accounting_rule_line_id IS NOT NULL AND manual_adjustment_line_id IS NULL) OR (accounting_rule_line_id IS NULL AND manual_adjustment_line_id IS NOT NULL)",
  ck_instruction_line_amount: "amount>0",
  ck_journal_line_amount: "amount > 0",
  ck_journal_entry_reversal: "(reversal_of_entry_id IS NULL OR reversal_of_entry_id<>id) AND ((entry_type='REVERSAL' AND reversal_of_entry_id IS NOT NULL) OR (entry_type<>'REVERSAL' AND reversal_of_entry_id IS NULL))",
  ck_manual_adjustment_file: "(file_id IS NULL AND file_content_hash IS NULL) OR (file_id IS NOT NULL AND file_content_hash IS NOT NULL)",
  ck_manual_adjustment_balance: "total_debit > 0 AND total_debit = total_credit",
  ck_manual_adjustment_line_amount: "amount>0",
  ck_manual_adjustment_submission: "(status='DRAFT' AND submitted_time IS NULL) OR (status IN ('SUBMITTED','APPROVED','REJECTED','POSTED','CANCELLED') AND submitted_time IS NOT NULL)",
  ck_active_slot: "active_slot IS NULL OR active_slot = 1",
  ck_tax_payment_state: "(status IN ('CREATED','SUBMITTED','PROCESSING','BANK_ACCEPTED','UNKNOWN') AND active_slot=1) OR (status IN ('FAILED','RECONCILED') AND active_slot IS NULL)",
  ck_period_close_active: "(status IN ('CREATED','RUNNING','PENDING_CHECK','APPROVED') AND active_slot IS NOT NULL AND active_slot=1) OR (status IN ('REJECTED','COMPLETED','FAILED') AND active_slot IS NULL)",
  ck_period_close_type: "(reopens_close_id IS NULL OR reopens_close_id<>id) AND ((close_type='CLOSE' AND reopens_close_id IS NULL AND reopen_reason_code IS NULL AND reopen_request_ref IS NULL) OR (close_type='REOPEN' AND reopens_close_id IS NOT NULL AND reopen_reason_code IS NOT NULL AND reopen_request_ref IS NOT NULL))",
  ck_period_close_completion: "(status IN ('CREATED','RUNNING','PENDING_CHECK') AND checker_id IS NULL) OR (status IN ('APPROVED','REJECTED','COMPLETED') AND checker_id IS NOT NULL AND checker_id<>maker_id) OR (status='FAILED' AND (checker_id IS NULL OR checker_id<>maker_id))",
  ck_period_close_book_state: "(status='LOCKED' AND final_trial_run_id IS NULL AND posting_instruction_id IS NULL) OR (status='FINALIZED' AND final_trial_run_id IS NOT NULL AND posting_instruction_id IS NOT NULL)",
  ck_trial_balance_success: "total_debit >= 0 AND total_credit >= 0 AND (status<>'SUCCEEDED' OR total_debit=total_credit)",
  ck_trial_balance_hash: "(status IN ('CREATED','RUNNING') AND content_hash IS NULL) OR (status IN ('SUCCEEDED','FAILED') AND content_hash IS NOT NULL)",
  ck_trial_exception_resolution: "(status IN ('OPEN','UNDER_REVIEW') AND resolution_type IS NULL AND resolution_ref_id IS NULL AND resolution_content_hash IS NULL AND resolution_maker_id IS NULL AND resolution_checker_id IS NULL AND resolution_time IS NULL) OR (status='RESOLVED' AND ((exception_type='GENERAL_LEDGER_MISMATCH' AND resolution_type IN ('ACCOUNTING_INSTRUCTION','PROJECTION_REBUILD')) OR (exception_type<>'GENERAL_LEDGER_MISMATCH' AND resolution_type IN ('RECON_RESOLUTION','ACCOUNTING_INSTRUCTION'))) AND resolution_ref_id IS NOT NULL AND resolution_content_hash IS NOT NULL AND resolution_maker_id IS NOT NULL AND resolution_checker_id IS NOT NULL AND resolution_checker_id<>resolution_maker_id AND resolution_time IS NOT NULL) OR (status='WAIVED' AND exception_type<>'GENERAL_LEDGER_MISMATCH' AND resolution_type='CONTROLLED_WAIVER' AND resolution_ref_id IS NOT NULL AND resolution_content_hash IS NOT NULL AND resolution_maker_id IS NOT NULL AND resolution_checker_id IS NOT NULL AND resolution_checker_id<>resolution_maker_id AND resolution_time IS NOT NULL)",
  ck_ledger_period_balance_sequence: "as_of_sequence>=0",
  ck_close_step_state: "(status IN ('PENDING','RUNNING') AND result_hash IS NULL AND completed_time IS NULL) OR (status IN ('SUCCEEDED','FAILED','SKIPPED') AND result_hash IS NOT NULL AND completed_time IS NOT NULL)",
  ck_channel_open_item_type: "(item_type='PAYMENT' AND payment_intent_id IS NOT NULL AND payment_attempt_id IS NOT NULL AND duplicate_capture_item_id IS NULL) OR (item_type='DUPLICATE_CAPTURE' AND payment_intent_id IS NULL AND payment_attempt_id IS NULL AND duplicate_capture_item_id IS NOT NULL)",
  ck_duplicate_capture_amount: "amount>0",
  ck_channel_open_item_amounts: "gross_receivable > 0 AND settled_amount >= 0 AND outstanding_amount >= 0 AND settled_amount + outstanding_amount = gross_receivable",
  ck_channel_open_item_state: "(status='OPEN' AND settled_amount=0 AND outstanding_amount=gross_receivable) OR (status='PARTIALLY_SETTLED' AND settled_amount>0 AND outstanding_amount>0) OR (status='SETTLED' AND settled_amount=gross_receivable AND outstanding_amount=0) OR status='EXCEPTION'",
  ck_channel_cost_alloc_target: "(payment_attempt_id IS NOT NULL AND channel_refund_id IS NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL) OR (payment_attempt_id IS NULL AND channel_refund_id IS NOT NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL) OR (payment_attempt_id IS NULL AND channel_refund_id IS NULL AND channel_dispute_notice_id IS NOT NULL AND dispute_case_id IS NOT NULL)",
  ck_channel_debit_type: "(debit_type='REFUND' AND channel_refund_id IS NOT NULL AND duplicate_capture_item_id IS NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL) OR (debit_type='DUPLICATE_COMPENSATION' AND channel_refund_id IS NULL AND duplicate_capture_item_id IS NOT NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL) OR (debit_type='DISPUTE' AND channel_refund_id IS NULL AND duplicate_capture_item_id IS NULL AND channel_dispute_notice_id IS NOT NULL AND dispute_case_id IS NOT NULL)",
  ck_channel_debit_amounts: "expected_amount > 0 AND settled_amount >= 0 AND outstanding_amount >= 0 AND settled_amount + outstanding_amount = expected_amount",
  ck_channel_debit_state: "(status='OPEN' AND settled_amount=0 AND outstanding_amount=expected_amount) OR (status='PARTIALLY_SETTLED' AND settled_amount>0 AND outstanding_amount>0) OR (status='SETTLED' AND settled_amount=expected_amount AND outstanding_amount=0) OR status='EXCEPTION'",
  ck_channel_settle_batch_net: "gross_amount >= 0 AND fee_amount >= 0 AND debit_amount >= 0 AND net_amount >= 0 AND ((cash_direction='CREDIT' AND gross_amount>fee_amount+debit_amount AND net_amount=gross_amount-fee_amount-debit_amount) OR (cash_direction='DEBIT' AND gross_amount<fee_amount+debit_amount AND net_amount=fee_amount+debit_amount-gross_amount) OR (cash_direction='NONE' AND gross_amount=fee_amount+debit_amount AND net_amount=0))",
  ck_channel_settle_item_direction: "gross_amount >= 0 AND fee_amount >= 0 AND net_amount >= 0 AND gross_amount + fee_amount + net_amount > 0 AND ((item_type IN ('PAYMENT','DUPLICATE_CAPTURE') AND settlement_direction='CREDIT') OR (item_type IN ('REFUND','DISPUTE','FEE') AND settlement_direction='DEBIT') OR item_type='ADJUSTMENT')",
  ck_channel_transaction_match_amounts: "gross_amount >= 0 AND fee_amount >= 0 AND net_amount >= 0 AND gross_amount + fee_amount + net_amount > 0",
  ck_channel_transaction_match_target: "(target_type='OPEN_ITEM' AND open_item_id IS NOT NULL AND debit_item_id IS NULL AND target_key=CONCAT('OPEN_ITEM:',CAST(open_item_id AS CHAR))) OR (target_type='DEBIT_ITEM' AND open_item_id IS NULL AND debit_item_id IS NOT NULL AND target_key=CONCAT('DEBIT_ITEM:',CAST(debit_item_id AS CHAR)))",
  ck_bank_recon_match_target: "((match_type IN ('CHANNEL_CREDIT','CHANNEL_DEBIT') AND channel_batch_id IS NOT NULL AND payout_execution_id IS NULL AND tax_payment_id IS NULL) OR (match_type='PAYOUT_DEBIT' AND channel_batch_id IS NULL AND payout_execution_id IS NOT NULL AND tax_payment_id IS NULL) OR (match_type='TAX_PAYMENT_DEBIT' AND channel_batch_id IS NULL AND payout_execution_id IS NULL AND tax_payment_id IS NOT NULL)) IS TRUE",
  ck_bank_recon_payout_component: "((match_type='PAYOUT_DEBIT' AND payout_component_type IS NOT NULL AND payout_component_type IN ('BENEFICIARY_TRANSFER','BANK_FEE','COMBINED')) OR (match_type<>'PAYOUT_DEBIT' AND payout_component_type IS NULL)) IS TRUE",
  ck_bank_statement_item_amount: "amount > 0",
  ck_bank_recon_match_amount: "matched_amount > 0",
  ck_bank_recon_match_confirmation: "(status='PROPOSED' AND decision_by IS NULL AND decision_time IS NULL) OR (status IN ('CONFIRMED','REJECTED') AND decision_by IS NOT NULL AND decision_time IS NOT NULL)",
  ck_recon_exception_amount: "difference_amount > 0",
  ck_recon_exception_state: "(status IN ('OPEN','UNDER_REVIEW') AND applied_resolution_id IS NULL) OR (status IN ('RESOLVED','CLOSED') AND applied_resolution_id IS NOT NULL)",
  ck_txn_economics_amount: "gross_amount >= 0",
  ck_txn_econ_component_amounts: "basis_amount>=0 AND raw_amount>=0 AND posted_amount>=0 AND (rate_value IS NULL OR (rate_value>=0 AND rate_value<=1)) AND (source_rate IS NULL OR (source_rate>=0 AND source_rate<=1)) AND ((source_fixed_amount IS NULL AND source_currency IS NULL) OR (source_fixed_amount IS NOT NULL AND source_fixed_amount>=0 AND source_currency IS NOT NULL)) AND (source_min_amount IS NULL OR source_min_amount>=0) AND (source_max_amount IS NULL OR source_max_amount>=0) AND (source_min_amount IS NULL OR source_max_amount IS NULL OR source_min_amount<=source_max_amount)",
  ck_txn_econ_component_party: "(party_type IS NULL AND party_id IS NULL) OR (party_type IS NOT NULL AND party_id IS NOT NULL)",
  ck_txn_econ_component_source: "((source_rule_type IN ('CLIENT_PRICING','PARTNER_SHARE') AND tax_line_id IS NULL AND source_rule_scope_id IS NOT NULL AND source_rule_content_hash IS NULL) OR (source_rule_type='PLATFORM_SERVICE_CHARGE' AND tax_line_id IS NULL AND source_rule_scope_id IS NOT NULL AND source_rule_component_id IS NOT NULL AND source_rule_content_hash IS NULL AND party_type='FINANCE_ENTITY') OR (source_rule_type='TAX_RULE' AND tax_line_id IS NOT NULL AND source_rule_scope_id IS NULL AND source_rule_content_hash IS NOT NULL) OR (source_rule_type='CHANNEL_COST' AND tax_line_id IS NULL AND source_rule_scope_id IS NOT NULL AND source_rule_content_hash IS NOT NULL)) AND source_price_id IS NULL AND source_profile_code IS NULL AND source_band_evidence IS NULL OR (source_rule_type='CCT_PRICE_COMPONENT' AND source_rule_version_id IS NOT NULL AND source_rule_component_id IS NOT NULL AND source_price_id IS NOT NULL AND source_profile_code IS NOT NULL AND source_rule_scope_id IS NULL AND source_rule_content_hash IS NULL AND tax_line_id IS NULL)",
  ck_txn_econ_component_link: "source_component_id<>target_component_id AND linked_amount>0",
  ck_txn_economics_parent: "parent_economics_id IS NULL OR parent_economics_id<>id",
  ck_txn_economics_type: "(economics_type='PAYMENT' AND parent_economics_id IS NULL AND payment_attempt_id IS NOT NULL AND payment_succeeded_time IS NOT NULL AND balance_available_time IS NOT NULL AND refund_request_id IS NULL AND channel_refund_id IS NULL AND duplicate_capture_item_id IS NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL) OR (economics_type='REFUND' AND parent_economics_id IS NOT NULL AND payment_attempt_id IS NOT NULL AND payment_succeeded_time IS NULL AND balance_available_time IS NULL AND refund_request_id IS NOT NULL AND channel_refund_id IS NOT NULL AND duplicate_capture_item_id IS NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL) OR (economics_type IN ('DUPLICATE_CAPTURE','DUPLICATE_COMPENSATION') AND parent_economics_id IS NOT NULL AND payment_attempt_id IS NOT NULL AND payment_succeeded_time IS NULL AND balance_available_time IS NULL AND refund_request_id IS NULL AND channel_refund_id IS NULL AND duplicate_capture_item_id IS NOT NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL) OR (economics_type='DISPUTE' AND parent_economics_id IS NOT NULL AND payment_attempt_id IS NOT NULL AND payment_succeeded_time IS NULL AND balance_available_time IS NULL AND refund_request_id IS NULL AND channel_refund_id IS NULL AND duplicate_capture_item_id IS NULL AND channel_dispute_notice_id IS NOT NULL AND dispute_case_id IS NOT NULL) OR (economics_type='COST_ADJUSTMENT' AND parent_economics_id IS NOT NULL AND payment_succeeded_time IS NULL AND balance_available_time IS NULL AND refund_request_id IS NULL AND channel_refund_id IS NULL AND duplicate_capture_item_id IS NULL AND channel_dispute_notice_id IS NULL AND dispute_case_id IS NULL)",
  ck_txn_economics_maturity_time: "balance_available_time IS NULL OR balance_available_time>=payment_succeeded_time",
  ck_txn_economics_partner: "(local_partner_id IS NULL AND partner_agreement_oe_id IS NULL AND finance_entity_id IS NULL) OR (local_partner_id IS NOT NULL AND partner_agreement_oe_id IS NOT NULL AND finance_entity_id IS NOT NULL)",
  ck_beneficiary_lifecycle: "(status='DRAFT' AND content_hash IS NULL AND checker_id IS NULL AND checker_decision IS NULL AND decision_time IS NULL) OR (status='PENDING_REVIEW' AND content_hash IS NOT NULL AND checker_id IS NULL AND checker_decision IS NULL AND decision_time IS NULL) OR (status='VERIFIED' AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='APPROVED' AND decision_time IS NOT NULL) OR (status='REJECTED' AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='REJECTED' AND decision_time IS NOT NULL) OR (status IN ('SUSPENDED','CLOSED') AND content_hash IS NOT NULL AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision='APPROVED' AND decision_time IS NOT NULL)",
  ck_payout_amounts: "requested_amount>0 AND fee_amount>=0 AND owner_debit_amount>0 AND beneficiary_amount>0 AND cash_required_amount=owner_debit_amount AND beneficiary_amount+fee_amount=owner_debit_amount AND ((fee_mode='ADDED_ON_TOP' AND owner_debit_amount=requested_amount+fee_amount AND beneficiary_amount=requested_amount) OR (fee_mode='DEDUCTED_FROM_PAYOUT' AND requested_amount>fee_amount AND owner_debit_amount=requested_amount AND beneficiary_amount=requested_amount-fee_amount))",
  ck_payout_risk_snapshot: "(assessment_id IS NULL AND evaluation_id IS NULL AND eligibility_decision IS NULL AND eligibility_reason_codes IS NULL AND eligibility_matched_controls IS NULL AND risk_required_amount IS NULL AND risk_required_currency IS NULL AND control_set_hash IS NULL AND eligibility_hash IS NULL) OR (assessment_id IS NOT NULL AND evaluation_id IS NOT NULL AND eligibility_decision='ALLOW' AND eligibility_reason_codes IS NOT NULL AND eligibility_matched_controls IS NOT NULL AND control_set_hash IS NOT NULL AND eligibility_hash IS NOT NULL AND ((owner_type='CLIENT' AND risk_required_amount IS NOT NULL AND risk_required_amount>=0 AND risk_required_currency IS NOT NULL AND risk_required_currency=currency) OR (owner_type='LOCAL_PARTNER' AND risk_required_amount IS NULL AND risk_required_currency IS NULL)))",
  ck_payout_approval_snapshot: "(approval_hash IS NULL AND approved_time IS NULL) OR (approval_hash IS NOT NULL AND approved_time IS NOT NULL AND assessment_id IS NOT NULL AND evaluation_id IS NOT NULL AND eligibility_decision='ALLOW' AND eligibility_reason_codes IS NOT NULL AND eligibility_matched_controls IS NOT NULL AND control_set_hash IS NOT NULL AND eligibility_hash IS NOT NULL AND ((owner_type='CLIENT' AND risk_required_amount IS NOT NULL AND risk_required_amount>=0 AND risk_required_currency IS NOT NULL AND risk_required_currency=currency) OR (owner_type='LOCAL_PARTNER' AND risk_required_amount IS NULL AND risk_required_currency IS NULL)))",
  ck_payout_reconciled: "(status='PAID' AND journal_entry_id IS NOT NULL AND reconciled_time IS NOT NULL) OR (status IN ('PENDING_APPROVAL','APPROVED','ON_HOLD','PAYING','PAYMENT_UNKNOWN','RETRY_REQUIRED','REJECTED','CANCELLED') AND journal_entry_id IS NULL AND reconciled_time IS NULL)",
  ck_payout_approval_action: "((action_type='PAYOUT_APPROVAL' AND action_no=1 AND requested_from_status='PENDING_APPROVAL' AND evidence_ref_id IS NULL) OR (action_type='PAYOUT_CANCEL' AND action_no>0 AND requested_from_status IN ('APPROVED','ON_HOLD','RETRY_REQUIRED'))) AND ((action_type<>'PAYOUT_CANCEL' AND decision<>'REJECTED') OR reason_code IS NOT NULL)",
  ck_payout_execution_active: "(status IN ('CREATED','SUBMITTED','UNKNOWN') AND active_slot=1) OR (status IN ('ABORTED','FAILED','RECONCILED') AND active_slot IS NULL)",
  ck_payout_execution_state: "(status='CREATED' AND submitted_time IS NULL AND resolved_time IS NULL AND failure_code IS NULL) OR (status IN ('SUBMITTED','UNKNOWN') AND submitted_time IS NOT NULL AND resolved_time IS NULL AND failure_code IS NULL) OR (status='ABORTED' AND submitted_time IS NULL AND resolved_time IS NOT NULL AND failure_code IS NULL) OR (status='FAILED' AND submitted_time IS NOT NULL AND resolved_time IS NOT NULL AND failure_code IS NOT NULL) OR (status='RECONCILED' AND submitted_time IS NOT NULL AND resolved_time IS NOT NULL AND failure_code IS NULL)",
  ck_payout_execution_risk_snapshot: "assessment_id IS NOT NULL AND evaluation_id IS NOT NULL AND eligibility_decision='ALLOW' AND eligibility_reason_codes IS NOT NULL AND eligibility_matched_controls IS NOT NULL AND control_set_hash IS NOT NULL AND eligibility_hash IS NOT NULL AND ((risk_required_amount IS NULL AND risk_required_currency IS NULL) OR (risk_required_amount IS NOT NULL AND risk_required_amount>=0 AND risk_required_currency IS NOT NULL))",
  ck_payout_event_scope: "(event_scope='PAYOUT' AND execution_id IS NULL AND execution_version_after IS NULL) OR (event_scope='EXECUTION' AND execution_id IS NOT NULL AND execution_version_after IS NOT NULL)",
  ck_tax_payment_amounts: "amount > 0 AND reconciled_amount >= 0 AND reconciled_amount <= amount",
  ck_tax_payment_reconciled: "(status='RECONCILED' AND reconciled_amount=amount AND reconciled_time IS NOT NULL AND journal_entry_id IS NOT NULL) OR (status='FAILED' AND reconciled_amount=0 AND reconciled_time IS NULL AND journal_entry_id IS NULL) OR (status IN ('CREATED','SUBMITTED','PROCESSING','BANK_ACCEPTED','UNKNOWN') AND reconciled_amount<amount AND reconciled_time IS NULL AND journal_entry_id IS NULL)",
  ck_tax_payment_submission: "(status='CREATED' AND bank_payment_ref IS NULL) OR status IN ('SUBMITTED','PROCESSING','FAILED','UNKNOWN') OR (status IN ('BANK_ACCEPTED','RECONCILED') AND bank_payment_ref IS NOT NULL)",
  ck_tax_payment_event_evidence_pair: "(source_ref IS NULL AND evidence_hash IS NULL) OR (source_ref IS NOT NULL AND evidence_hash IS NOT NULL)",
  ck_tax_payment_failed_event: "event_type<>'FAILED' OR (source_ref IS NOT NULL AND evidence_hash IS NOT NULL)",
  ck_recon_resolution_approval: "(status IN ('DRAFT','PENDING_CHECK') AND checker_id IS NULL AND checker_decision IS NULL AND approved_content_hash IS NULL AND decision_time IS NULL) OR (status IN ('APPROVED','APPLIED') AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision IS NOT NULL AND checker_decision='APPROVED' AND approved_content_hash IS NOT NULL AND approved_content_hash=content_hash AND decision_time IS NOT NULL) OR (status='REJECTED' AND checker_id IS NOT NULL AND checker_id<>maker_id AND checker_decision IS NOT NULL AND checker_decision='REJECTED' AND approved_content_hash IS NOT NULL AND approved_content_hash=content_hash AND decision_time IS NOT NULL)",
  ck_recon_resolution_execution: "(status<>'APPLIED' AND instruction_id IS NULL AND channel_transaction_match_id IS NULL AND bank_recon_match_id IS NULL) OR (status='APPLIED' AND ((resolution_type='RE_MATCH' AND instruction_id IS NULL AND ((channel_transaction_match_id IS NOT NULL AND bank_recon_match_id IS NULL) OR (channel_transaction_match_id IS NULL AND bank_recon_match_id IS NOT NULL))) OR (resolution_type IN ('ACCOUNTING_ADJUSTMENT','WRITE_OFF') AND instruction_id IS NOT NULL AND channel_transaction_match_id IS NULL AND bank_recon_match_id IS NULL) OR (resolution_type='ACCEPT_TIMING_DIFFERENCE' AND instruction_id IS NULL AND channel_transaction_match_id IS NULL AND bank_recon_match_id IS NULL)))",
  ck_trial_exception_amount: "difference_amount > 0",
  ck_compensation_lease: "(execute_status='PROCESSING' AND lock_owner IS NOT NULL AND lock_until IS NOT NULL) OR (execute_status<>'PROCESSING' AND lock_owner IS NULL AND lock_until IS NULL)",
  ck_command_result_ref: "(result_ref_type IS NULL AND result_ref_id IS NULL) OR (result_ref_type IS NOT NULL AND result_ref_id IS NOT NULL)",
});

export const EXPECTED_CHECK_BINDINGS = Object.freeze({
  ck_chart_account_class: Object.freeze(["chart_account"]),
  ck_chart_account_side: Object.freeze(["chart_account"]),
  ck_account_balance_projection: Object.freeze(["account"]),
  ck_book_sequence_next: Object.freeze(["book_sequence"]),
  ck_chart_account_parent: Object.freeze(["chart_account"]),
  ck_account_owner_bucket: Object.freeze(["account"]),
  ck_currency_minor_unit: Object.freeze(["currency_spec"]),
  ck_fiscal_calendar_month: Object.freeze(["fiscal_calendar"]),
  ck_accounting_period_range: Object.freeze(["accounting_period"]),
  ck_versioned_root_pointer: Object.freeze(["channel_cost_plan", "accounting_rule_bundle", "cash_account_binding"]),
  ck_tax_jurisdiction_parent: Object.freeze(["tax_jurisdiction"]),
  ck_tax_category_parent: Object.freeze(["tax_category"]),
  ck_tax_rate_value: Object.freeze(["tax_rate"]),
  ck_tax_fx_rate: Object.freeze(["tax_exchange_rate"]),
  ck_tax_snapshot_amounts: Object.freeze(["tax_snapshot"]),
  ck_tax_snapshot_exchange_rate: Object.freeze(["tax_snapshot"]),
  ck_tax_snapshot_buyer_id: Object.freeze(["tax_snapshot"]),
  ck_fiscal_record_business_ref: Object.freeze(["fiscal_record"]),
  ck_fiscal_record_sequence: Object.freeze(["fiscal_record"]),
  ck_fiscal_record_original: Object.freeze(["fiscal_record"]),
  ck_tax_registration_kind: Object.freeze(["tax_registration"]),
  ck_tax_period_range: Object.freeze(["tax_period"]),
  ck_tax_rule_lifecycle: Object.freeze(["tax_rule"]),
  ck_fiscal_line_amount: Object.freeze(["fiscal_record_line"]),
  ck_tax_line_liability: Object.freeze(["tax_line"]),
  ck_tax_return_file: Object.freeze(["tax_return"]),
  ck_tax_liability_summary: Object.freeze(["tax_liability_summary"]),
  ck_tax_return_lifecycle: Object.freeze(["tax_return"]),
  ck_tax_return_amend: Object.freeze(["tax_return"]),
  ck_channel_cost_approval: Object.freeze(["channel_cost_plan_ver"]),
  ck_channel_cost_component: Object.freeze(["channel_cost_component"]),
  ck_channel_cost_confirm: Object.freeze(["channel_cost_confirm"]),
  ck_channel_cost_alloc_amount: Object.freeze(["channel_cost_alloc"]),
  ck_instruction_origin: Object.freeze(["accounting_instruction"]),
  ck_accounting_rule_review: Object.freeze(["accounting_rule_bundle_ver"]),
  ck_accounting_rule_version_shape: Object.freeze(["accounting_rule_bundle_ver"]),
  ck_accounting_rule_line_no: Object.freeze(["accounting_rule_line"]),
  ck_instruction_line_source: Object.freeze(["instruction_line"]),
  ck_instruction_line_amount: Object.freeze(["instruction_line"]),
  ck_journal_line_amount: Object.freeze(["journal_line"]),
  ck_journal_entry_reversal: Object.freeze(["journal_entry"]),
  ck_manual_adjustment_file: Object.freeze(["manual_adjustment"]),
  ck_manual_adjustment_balance: Object.freeze(["manual_adjustment"]),
  ck_manual_adjustment_line_amount: Object.freeze(["manual_adjustment_line"]),
  ck_manual_adjustment_submission: Object.freeze(["manual_adjustment"]),
  ck_active_slot: Object.freeze(["tax_payment", "period_close"]),
  ck_tax_payment_state: Object.freeze(["tax_payment"]),
  ck_period_close_active: Object.freeze(["period_close"]),
  ck_period_close_type: Object.freeze(["period_close"]),
  ck_period_close_completion: Object.freeze(["period_close"]),
  ck_period_close_book_state: Object.freeze(["period_close_book"]),
  ck_trial_balance_success: Object.freeze(["trial_balance_run"]),
  ck_trial_balance_hash: Object.freeze(["trial_balance_run"]),
  ck_trial_exception_resolution: Object.freeze(["trial_balance_exception"]),
  ck_ledger_period_balance_sequence: Object.freeze(["ledger_period_balance"]),
  ck_close_step_state: Object.freeze(["close_step"]),
  ck_channel_open_item_type: Object.freeze(["channel_open_item"]),
  ck_duplicate_capture_amount: Object.freeze(["duplicate_capture_item"]),
  ck_channel_open_item_amounts: Object.freeze(["channel_open_item"]),
  ck_channel_open_item_state: Object.freeze(["channel_open_item"]),
  ck_channel_cost_alloc_target: Object.freeze(["channel_cost_alloc"]),
  ck_channel_debit_type: Object.freeze(["channel_debit_item"]),
  ck_channel_debit_amounts: Object.freeze(["channel_debit_item"]),
  ck_channel_debit_state: Object.freeze(["channel_debit_item"]),
  ck_channel_settle_batch_net: Object.freeze(["channel_settle_batch"]),
  ck_channel_settle_item_direction: Object.freeze(["channel_settle_item"]),
  ck_channel_transaction_match_amounts: Object.freeze(["channel_transaction_match"]),
  ck_channel_transaction_match_target: Object.freeze(["channel_transaction_match"]),
  ck_bank_recon_match_target: Object.freeze(["bank_recon_match"]),
  ck_bank_recon_payout_component: Object.freeze(["bank_recon_match"]),
  ck_bank_statement_item_amount: Object.freeze(["bank_statement_item"]),
  ck_bank_recon_match_amount: Object.freeze(["bank_recon_match"]),
  ck_bank_recon_match_confirmation: Object.freeze(["bank_recon_match"]),
  ck_recon_exception_amount: Object.freeze(["recon_exception"]),
  ck_recon_exception_state: Object.freeze(["recon_exception"]),
  ck_txn_economics_amount: Object.freeze(["transaction_economics"]),
  ck_txn_econ_component_amounts: Object.freeze(["transaction_economic_component"]),
  ck_txn_econ_component_party: Object.freeze(["transaction_economic_component"]),
  ck_txn_econ_component_source: Object.freeze(["transaction_economic_component"]),
  ck_txn_econ_component_link: Object.freeze(["transaction_economic_comp_link"]),
  ck_txn_economics_parent: Object.freeze(["transaction_economics"]),
  ck_txn_economics_type: Object.freeze(["transaction_economics"]),
  ck_txn_economics_maturity_time: Object.freeze(["transaction_economics"]),
  ck_txn_economics_partner: Object.freeze(["transaction_economics"]),
  ck_beneficiary_lifecycle: Object.freeze(["beneficiary_account"]),
  ck_payout_amounts: Object.freeze(["payout"]),
  ck_payout_risk_snapshot: Object.freeze(["payout"]),
  ck_payout_approval_snapshot: Object.freeze(["payout"]),
  ck_payout_reconciled: Object.freeze(["payout"]),
  ck_payout_approval_action: Object.freeze(["payout_approval"]),
  ck_payout_execution_active: Object.freeze(["payout_execution"]),
  ck_payout_execution_state: Object.freeze(["payout_execution"]),
  ck_payout_execution_risk_snapshot: Object.freeze(["payout_execution"]),
  ck_payout_event_scope: Object.freeze(["payout_event"]),
  ck_tax_payment_amounts: Object.freeze(["tax_payment"]),
  ck_tax_payment_reconciled: Object.freeze(["tax_payment"]),
  ck_tax_payment_submission: Object.freeze(["tax_payment"]),
  ck_tax_payment_event_evidence_pair: Object.freeze(["tax_payment_event"]),
  ck_tax_payment_failed_event: Object.freeze(["tax_payment_event"]),
  ck_recon_resolution_approval: Object.freeze(["recon_resolution"]),
  ck_recon_resolution_execution: Object.freeze(["recon_resolution"]),
  ck_trial_exception_amount: Object.freeze(["trial_balance_exception"]),
  ck_compensation_lease: Object.freeze(["compensation_event"]),
  ck_command_result_ref: Object.freeze(["command_idempotency"]),
});

export const REQUIRED_FIELDS = Object.freeze({
  t_chart_of_accounts: Object.freeze(["chart_code", "chart_name", "status"]),
  t_finance_entity: Object.freeze([
    "entity_code",
    "legal_party_id",
    "display_name",
    "status",
  ]),
  t_ledger_book: Object.freeze(["entity_type", "entity_id"]),
  t_account: Object.freeze([
    "ledger_book_id",
    "chart_account_id",
    "owner_type",
    "owner_id",
    "balance_bucket",
    "normal_side",
    "account_status",
    "debit_total",
    "credit_total",
    "net_balance",
    "last_journal_sequence",
    "balance_version",
  ]),
  t_book_sequence: Object.freeze(["ledger_book_id", "next_value"]),
  t_fiscal_calendar: Object.freeze(["entity_type", "entity_id"]),
  t_tax_rule: Object.freeze([
    "rule_code",
    "rule_ver",
    "content_hash",
    "maker_id",
    "checker_id",
    "checker_decision",
    "decision_time",
  ]),
  t_place_of_supply_rule: Object.freeze(["tax_rule_id"]),
  t_tax_rate: Object.freeze(["tax_rule_id"]),
  t_tax_evidence_rule: Object.freeze(["tax_rule_id"]),
  t_tax_return: Object.freeze([
    "return_no",
    "return_ver",
    "amends_return_id",
    "content_hash",
    "maker_id",
    "checker_id",
    "checker_decision",
    "decision_time",
    "file_id",
    "file_content_hash",
  ]),
  t_tax_return_line: Object.freeze(["tax_return_id"]),
  t_tax_payment: Object.freeze(["tax_return_id", "tax_payable_account_id", "active_slot"]),
  t_transaction_economics: Object.freeze([
    "finance_entity_id",
    "payment_succeeded_time",
    "balance_available_time",
  ]),
  t_accounting_instruction: Object.freeze(["ledger_book_id"]),
  t_ledger_period_balance: Object.freeze([
    "period_id",
    "ledger_book_id",
    "chart_account_id",
    "trial_balance_run_id",
  ]),
  t_instruction_line: Object.freeze(["account_id"]),
  t_journal_line: Object.freeze(["account_id"]),
  t_manual_adjustment_line: Object.freeze(["account_id"]),
  t_trial_balance_line: Object.freeze(["chart_account_id"]),
  t_trial_balance_exception: Object.freeze(["reference_type", "reference_id"]),
  t_accounting_rule_bundle_ver: Object.freeze(["approved_time"]),
  t_tax_line: Object.freeze(["reporting_tax_basis"]),
  t_channel_debit_item: Object.freeze([
    "channel_refund_id",
    "channel_transaction_ref_id",
    "channel_debit_id",
    "external_transaction_id",
    "transaction_economics_id",
  ]),
  t_channel_open_item: Object.freeze(["transaction_economics_id"]),
  t_channel_transaction_match: Object.freeze([
    "settle_item_id",
    "open_item_id",
    "debit_item_id",
    "allocation_no",
  ]),
  t_channel_cost_confirm: Object.freeze(["channel_settle_item_id"]),
  t_channel_cost_scope: Object.freeze(["channel_agreement_ref"]),
  t_bank_recon_match: Object.freeze([
    "channel_batch_id",
    "payout_execution_id",
    "payout_component_type",
    "cash_account_binding_ver_id",
    "decision_by",
    "decision_time",
  ]),
  t_recon_resolution: Object.freeze([
    "exception_id",
    "channel_transaction_match_id",
    "bank_recon_match_id",
    "content_hash",
    "approved_content_hash",
  ]),
  t_tax_registration: Object.freeze(["entity_type", "entity_id", "registration_kind"]),
  t_cash_account: Object.freeze(["entity_type", "entity_id"]),
  t_cash_account_binding: Object.freeze(["entity_type", "entity_id"]),
  t_recon_exception: Object.freeze(["entity_type", "entity_id"]),
  t_period_close_book: Object.freeze([
    "period_close_id",
    "ledger_book_id",
    "as_of_sequence",
    "final_trial_run_id",
    "posting_instruction_id",
  ]),
  t_beneficiary_account: Object.freeze([
    "owner_type", "owner_id", "account_code", "beneficiary_vault_token",
    "beneficiary_name_masked", "bank_country", "currency", "bank_account_blind_index",
    "bank_account_blind_key_ver", "account_last4", "vault_record_version",
    "vault_metadata_hash", "bank_code", "content_hash", "maker_id", "checker_id",
    "checker_decision", "decision_time", "status", "version",
  ]),
  t_finance_restriction_proj: Object.freeze([
    "source_system", "source_restriction_id", "source_version", "source_event_key",
    "source_payload_hash", "restriction_type", "owner_type", "owner_id", "client_id",
    "entity_no", "currency_scope_key", "decision", "reason_set_hash",
    "effective_from", "effective_to", "version",
  ]),
  t_payout: Object.freeze([
    "payout_no", "request_key", "owner_type", "owner_id", "entity_no",
    "currency", "beneficiary_account_id", "beneficiary_content_hash", "available_account_id",
    "locked_account_id", "requested_amount", "fee_mode", "fee_amount", "owner_debit_amount",
    "beneficiary_amount", "cash_required_amount", "assessment_id", "evaluation_id",
    "eligibility_decision", "eligibility_reason_codes", "eligibility_matched_controls",
    "risk_required_amount", "risk_required_currency", "control_set_hash", "eligibility_hash",
    "request_hash", "approval_hash", "approved_time", "journal_entry_id", "reconciled_time",
    "status", "version",
  ]),
  t_payout_approval: Object.freeze([
    "payout_id", "action_type", "action_no", "approval_role", "actor_id", "decision",
    "requested_from_status", "reason_code", "evidence_ref_id", "approved_content_hash",
    "decision_time",
  ]),
  t_payout_execution: Object.freeze([
    "payout_id", "attempt_no", "cash_account_binding_ver_id", "cash_account_id",
    "execution_key", "request_hash", "assessment_id", "evaluation_id", "eligibility_decision",
    "eligibility_reason_codes", "eligibility_matched_controls", "risk_required_amount",
    "risk_required_currency", "control_set_hash", "eligibility_hash", "bank_payment_ref",
    "status", "failure_code", "active_slot", "submitted_time", "resolved_time", "version",
  ]),
  t_payout_evidence_ref: Object.freeze([
    "payout_execution_id", "file_id", "file_content_hash", "evidence_type",
  ]),
  t_payout_event: Object.freeze([
    "payout_id", "execution_id", "event_scope", "event_no", "from_status", "to_status",
    "reason_code", "actor_id", "source_type", "source_id", "source_event_key", "approval_id",
    "evidence_ref_id", "bank_recon_match_id", "journal_entry_id", "payout_version_after",
    "execution_version_after", "occurred_time",
  ]),
  t_manual_adjustment: Object.freeze(["file_id", "file_content_hash"]),
  t_channel_settle_batch: Object.freeze(["file_id", "file_content_hash"]),
  t_bank_statement: Object.freeze(["file_id", "file_content_hash"]),
  t_compensation_event: Object.freeze(["lock_version"]),
});

export const FORBIDDEN_FIELDS = Object.freeze([
  "ledger_account_id",
  "subledger_account_id",
  "subledger_owner_type",
  "channel_refund_attempt_id",
  "refund_transaction_ref_id",
  "provider_refund_id",
  "refund_item_id",
  "transit_item_id",
  "settle_batch_ref",
  "settlement_mode",
  "jdbc_url",
  "password",
  "pan",
  "cvv",
  "cvc",
  "track_data",
  "pin_block",
  "balance_lot_id",
  "locked_bucket_id",
  "active_lock_flag",
  "eligible_lot_count",
  "lot_set_hash",
  "client_available_time",
  "current_attempt_id",
  "settlement_payment_attempt_id",
  "eligibility_result_id",
  "result_version",
]);

export const FORBIDDEN_TABLE_FIELDS = Object.freeze({
  t_chart_of_accounts: Object.freeze([
    "chart_version",
    "name",
    "effective_from",
    "effective_to",
    "version",
  ]),
  t_finance_entity: Object.freeze([
    "version",
    "content_hash",
    "current_ver_id",
    "active_slot",
    "maker_id",
    "checker_id",
  ]),
  t_tax_rule: Object.freeze(["current_ver_id"]),
  t_place_of_supply_rule: Object.freeze(["tax_rule_ver_id"]),
  t_tax_rate: Object.freeze(["tax_rule_ver_id"]),
  t_tax_evidence_rule: Object.freeze(["tax_rule_ver_id"]),
  t_tax_line: Object.freeze(["tax_rule_ver_id"]),
  t_tax_return: Object.freeze(["current_ver_id", "amends_ver_id"]),
  t_tax_return_line: Object.freeze(["tax_return_ver_id"]),
  t_tax_payment: Object.freeze(["tax_return_ver_id"]),
  t_tax_registration: Object.freeze(["entity_no"]),
  t_cash_account: Object.freeze(["entity_no"]),
  t_cash_account_binding: Object.freeze(["entity_no"]),
  t_recon_exception: Object.freeze(["entity_no"]),
  t_book_sequence: Object.freeze(["version"]),
  t_account: Object.freeze(["balance_direction", "current_balance_side", "current_balance_amount"]),
  t_accounting_instruction: Object.freeze(["accounting_rule_artifact_id"]),
  t_instruction_line: Object.freeze(["ledger_book_id", "currency"]),
  t_journal_line: Object.freeze(["currency"]),
  t_manual_adjustment_line: Object.freeze(["currency"]),
  t_channel_cost_plan_ver: Object.freeze(["channel_agreement_ref"]),
  t_channel_cost_scope: Object.freeze(["priority", "scope_hash"]),
  t_recon_resolution: Object.freeze([
    "return_reference",
    "return_file_id",
    "return_file_hash",
  ]),
  t_bank_recon_match: Object.freeze([
    "channel_cash_item_id",
    "settlement_payment_attempt_id",
    "confirmed_by",
    "confirmed_time",
  ]),
  t_beneficiary_account: Object.freeze(["current_ver_id"]),
  t_payout: Object.freeze(["eligibility_result_id", "result_version", "result_hash"]),
  t_payout_execution: Object.freeze(["eligibility_result_id", "result_version", "result_hash"]),
});
