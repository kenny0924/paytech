import { EXECUTION_FIELD_OVERRIDES } from "./payment-execution-field-overrides.mjs";
import { paymentChannelIdentityKey } from "./payment-channel-identity.mjs";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  CLOSED_ENUM_VALUES,
  FIELD_METADATA,
  OPEN_CODE_SOURCES,
  TABLE_COMMENTS,
  TABLE_DISPLAY_NAMES,
} from "./payment-field-metadata.mjs";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const workspaceRoot = path.resolve(scriptDirectory, "../..");
const templatePath = path.join(
  workspaceRoot,
  "agent-docs/templates/pdmaner/template.pdma.json",
);
const outputPath = path.join(
  workspaceRoot,
  "payment-orchestration-service/docs/database/payment-orchestration.pdma.json",
);

const MODEL_KEY = "code-mor/payment-orchestration/database/v1";
const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const MYSQL_MAPPING_ID = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const JAVA_MAPPING_ID = "797A1496-D649-4261-89B4-544132EC3F36";
const MYBATIS_MAPPING_ID = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";

// Explicit production manifest. It is intentionally independent from tests and Markdown.
const FIELD_MANIFEST = String.raw`
[t_payment_channel]
channel_id BIGINT NN
channel_code VARCHAR(64) NN OPEN(Channel Catalog)
channel_name VARCHAR(255) NN
adapter_code VARCHAR(64) NN OPEN(Adapter Catalog)
channel_status VARCHAR(64) NN {DRAFT,ACTIVE,SUSPENDED,CLOSED}
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_account]
channel_account_id BIGINT NN
channel_id BIGINT NN
account_code VARCHAR(64) NN OPEN(Channel Account Catalog)
external_account_id VARCHAR(128) N
integration_config_key VARCHAR(128) NN
account_status VARCHAR(64) NN {DRAFT,ACTIVE,SUSPENDED,CLOSED}
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_mid]
mid_id BIGINT NN
channel_id BIGINT NN
channel_account_id BIGINT NN
external_mid_code VARCHAR(128) NN OPEN(Channel Adapter Catalog)
entity_no CHAR(5) NN
mid_status VARCHAR(64) NN {DRAFT,ACTIVE,SUSPENDED,CLOSED}
activated_at DATETIME(6) N
closed_at DATETIME(6) N
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_mid_capability]
mid_capability_id BIGINT NN
mid_id BIGINT NN
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
scheme_code VARCHAR(64) NN OPEN(Scheme Catalog)
country_code CHAR(2) NN OPEN(ISO 3166-1 alpha-2)
currency CHAR(3) NN OPEN(ISO 4217)
mcc_code VARCHAR(4) NN OPEN(CCT/Commerce frozen classification)
capability_code VARCHAR(128) NN OPEN(MID Capability Catalog)
value_type VARCHAR(64) NN {BOOLEAN,DECIMAL,INTEGER,CODE}
boolean_value TINYINT(1) N
decimal_value DECIMAL(24,8) N
integer_value BIGINT N
code_value VARCHAR(128) N
capability_status VARCHAR(64) NN {ENABLED,DISABLED}
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_mid_health_state]
mid_health_state_id BIGINT NN
mid_id BIGINT NN
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
health_status VARCHAR(64) NN {HEALTHY,DEGRADED,UNHEALTHY,UNKNOWN}
circuit_status VARCHAR(64) NN {CLOSED,OPEN,HALF_OPEN}
health_score DECIMAL(5,2) NN
sample_count INT NN
technical_success_count INT NN
channel_error_count INT NN
timeout_count INT NN
technical_success_rate DECIMAL(9,8) NN
channel_error_rate DECIMAL(9,8) NN
timeout_rate DECIMAL(9,8) NN
p95_latency_ms INT NN
consecutive_failure_count INT NN
window_started_at DATETIME(6) NN
window_ended_at DATETIME(6) NN
observed_at DATETIME(6) NN
valid_until DATETIME(6) NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_routing_policy]
routing_policy_id BIGINT NN
scope_type VARCHAR(64) NN {CLIENT,OPERATING_ENTITY,GLOBAL}
scope_id VARCHAR(64) NN
country_code CHAR(2) NN OPEN(ISO 3166-1 alpha-2)
currency CHAR(3) NN OPEN(ISO 4217)
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
industry_code VARCHAR(64) NN OPEN(CCT/Commerce frozen classification)
mcc_code VARCHAR(4) NN OPEN(CCT/Commerce frozen classification)
match_priority INT NN
policy_status VARCHAR(64) NN {ACTIVE,SUSPENDED,CLOSED}
current_version_id BIGINT N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_routing_policy_version]
routing_policy_version_id BIGINT NN
routing_policy_id BIGINT NN
version_no INT NN
version_status VARCHAR(64) NN {DRAFT,ACTIVE,RETIRED}
create_time DATETIME(6) NN

[t_routing_policy_mid]
routing_policy_mid_id BIGINT NN
routing_policy_version_id BIGINT NN
mid_id BIGINT NN
candidate_priority INT NN
weight_value DECIMAL(18,10) NN
create_time DATETIME(6) NN

[t_routing_decision]
routing_decision_id BIGINT NN
payment_attempt_id BIGINT NN
routing_policy_id BIGINT N
routing_policy_version_id BIGINT N
selected_channel_id BIGINT N
selected_mid_id BIGINT N
selected_channel_account_id BIGINT N
decision_status VARCHAR(64) NN {SELECTED,REJECTED,CONFIGURATION_CONFLICT}
decision_reason_code VARCHAR(64) N OPEN(Reason Catalog)
create_time DATETIME(6) NN

[t_route_candidate_eval]
route_candidate_eval_id BIGINT NN
routing_decision_id BIGINT NN
channel_id BIGINT NN
mid_id BIGINT NN
channel_account_id BIGINT N
eligible_flag TINYINT(1) NN
candidate_priority INT NN
weight_value DECIMAL(18,10) NN
exclusion_reason_code VARCHAR(64) N OPEN(Reason Catalog)
health_status VARCHAR(64) N {HEALTHY,DEGRADED,UNHEALTHY,UNKNOWN}
circuit_status VARCHAR(64) N {CLOSED,OPEN,HALF_OPEN}
health_score DECIMAL(5,2) N
technical_success_rate DECIMAL(9,8) N
timeout_rate DECIMAL(9,8) N
p95_latency_ms INT N
sample_count INT N
health_observed_at DATETIME(6) N
create_time DATETIME(6) NN

[t_payment_intent_binding]
payment_intent_binding_id BIGINT NN
source_type VARCHAR(64) NN OPEN(Payment Source Type Catalog)
source_id BIGINT NN
client_id BIGINT NN
payment_intent_id BIGINT N
closed_at DATETIME(6) N
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_payment_intent]
payment_intent_id BIGINT NN
source_type VARCHAR(64) NN OPEN(Payment Source Type Catalog)
source_id BIGINT NN
source_order_context_id BIGINT NN
client_id BIGINT NN
entity_no CHAR(5) NN
context_format VARCHAR(32) NN {TERM_PRICE,LEGACY_SCOPE}
client_pricing_version_id BIGINT NN
partner_share_version_id BIGINT N
platform_charge_version_id BIGINT N
industry_code VARCHAR(64) NN OPEN(CCT/Commerce frozen classification)
mcc_code VARCHAR(4) NN OPEN(CCT/Commerce frozen classification)
amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
intent_status VARCHAR(64) NN {REQUIRES_PAYMENT,PROCESSING,REQUIRES_ACTION,SUCCEEDED,FAILED,EXPIRED,CANCELLED}
active_attempt_id BIGINT N
successful_attempt_id BIGINT N
expires_at DATETIME(6) NN
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_payment_attempt]
payment_attempt_id BIGINT NN
payment_intent_id BIGINT NN
attempt_no INT NN
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
attempt_status VARCHAR(64) NN {CREATED,PROCESSING,REQUIRES_ACTION,AUTHORIZED,UNKNOWN,SUCCEEDED,FAILED,CANCELLED,VOIDED,CONFLICTED}
failure_stage VARCHAR(64) N {READINESS,EXECUTION_GATE,RISK,LIMIT,ROUTING,CHANNEL,RECOVERY,SYSTEM}
status_reason_code VARCHAR(64) N OPEN(Reason Catalog)
consumer_contract_id BIGINT N
consumer_terms_acceptance_id BIGINT N
refund_policy_acceptance_id BIGINT N
privacy_notice_acceptance_id BIGINT N
acceptance_completed_at DATETIME(6) N
order_version BIGINT N
transaction_assessment_id BIGINT N
risk_result VARCHAR(64) N {ALLOW,REVIEW,DENY,FAILED,TIMED_OUT}
limit_reservation_group_id BIGINT N
authorized_amount DECIMAL(24,8) NN
captured_amount DECIMAL(24,8) NN
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
source_submission_id BIGINT N
payment_goal VARCHAR(32) N {PAY_NOW,AUTHORIZE_ONLY}
authentication_requirement VARCHAR(32) N {NOT_REQUIRED,THREE_DS_REQUIRED,CHALLENGE_REQUIRED}
payment_instrument_token_id BIGINT N

[t_payment_pricing_fact]
payment_fact_id BIGINT NN
payment_attempt_id BIGINT NN
fact_schema_code VARCHAR(32) NN {PAYMENT_PRICING_FACTS_1}
source_action_id BIGINT NN
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
consumer_country_code CHAR(2) NN OPEN(ISO 3166-1 alpha-2)
currency CHAR(3) NN OPEN(ISO 4217)
card_network VARCHAR(64) N OPEN(Payment normalized card network catalog)
funding_type VARCHAR(64) N {CREDIT,DEBIT,PREPAID}
card_level VARCHAR(64) N OPEN(Payment normalized card level catalog)
authentication_class VARCHAR(64) N {THREE_DS_AUTHENTICATED,THREE_DS_ATTEMPTED,NON_THREE_DS}
payment_succeeded_time DATETIME(6) NN
amount DECIMAL(24,8) NN
client_pricing_version_id BIGINT NN
partner_share_version_id BIGINT N
platform_charge_version_id BIGINT N
create_time DATETIME(6) NN

[t_payment_instrument_token]
payment_instrument_token_id BIGINT NN
payment_instrument_token VARCHAR(64) NN
client_id BIGINT NN
payment_intent_id BIGINT NN
source_submission_id BIGINT NN
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
instrument_type VARCHAR(32) NN {CARD}
instrument_ref VARCHAR(128) NN
expires_at DATETIME(6) NN
revoked_at DATETIME(6) N
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_payment_action]
payment_action_id BIGINT NN
payment_attempt_id BIGINT N
channel_refund_id BIGINT N
duplicate_payment_case_id BIGINT N
action_no INT NN
action_type VARCHAR(64) NN {AUTHENTICATE,AUTHORIZE,CAPTURE,SALE,VOID,REFUND,QUERY}
action_status VARCHAR(64) NN {CREATED,SUBMITTED,REQUIRES_ACTION,UNKNOWN,SUCCEEDED,FAILED}
channel_id BIGINT NN
mid_id BIGINT NN
channel_account_id BIGINT NN
target_channel_txn_ref_id BIGINT N
amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
channel_idempotency_key VARCHAR(128) NN
channel_result_time DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
target_payment_action_id BIGINT N
authentication_action_id BIGINT N
channel_operation_ref VARCHAR(255) N
interaction_type VARCHAR(32) N {REDIRECT,THREE_DS_CHALLENGE,SDK,APP_SWITCH,QR_CODE}
interaction_expires_at DATETIME(6) N
authentication_outcome VARCHAR(32) N {AUTHENTICATED,ATTEMPTED,FAILED,UNAVAILABLE}
challenge_performed TINYINT N
authentication_valid_until DATETIME(6) N
status_reason_code VARCHAR(64) N OPEN(Reason Catalog)

[t_channel_transaction_ref]
channel_transaction_ref_id BIGINT NN
payment_action_id BIGINT NN
channel_id BIGINT NN
mid_id BIGINT NN
reference_type VARCHAR(64) NN {AUTHORIZE,CAPTURE,SALE,VOID,REFUND}
external_transaction_id VARCHAR(255) NN
original_channel_txn_ref_id BIGINT N
channel_event_time DATETIME(6) N
amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
create_time DATETIME(6) NN

[t_channel_interaction]
channel_interaction_id BIGINT NN
payment_action_id BIGINT N
channel_dispute_action_id BIGINT N
interaction_no INT NN
interaction_status VARCHAR(64) NN {STARTED,RESPONSE_RECEIVED,NETWORK_ERROR,TIMED_OUT}
started_at DATETIME(6) NN
finished_at DATETIME(6) N
request_file_id BIGINT N
request_content_hash CHAR(64) N
response_file_id BIGINT N
response_content_hash CHAR(64) N
http_status INT N
channel_result_code VARCHAR(128) N OPEN(Channel Adapter Catalog)
latency_ms INT N
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_duplicate_payment_case]
duplicate_payment_case_id BIGINT NN
payment_intent_id BIGINT NN
canonical_attempt_id BIGINT NN
canonical_action_id BIGINT NN
canonical_transaction_ref_id BIGINT NN
duplicate_attempt_id BIGINT NN
duplicate_action_id BIGINT NN
duplicate_transaction_ref_id BIGINT NN
duplicate_amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
case_status VARCHAR(64) NN {OPEN,COMPENSATING,COMPENSATION_UNKNOWN,COMPENSATED,MANUAL_REVIEW}
compensation_action_id BIGINT N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_payment_state_transition]
payment_state_transition_id BIGINT NN
aggregate_type VARCHAR(64) NN {PAYMENT_INTENT,PAYMENT_ATTEMPT,PAYMENT_ACTION,CHANNEL_REFUND,DUPLICATE_PAYMENT_CASE}
aggregate_id BIGINT NN
root_type VARCHAR(64) NN {PAYMENT_INTENT,PAYMENT_ATTEMPT,PAYMENT_ACTION,CHANNEL_REFUND,DUPLICATE_PAYMENT_CASE}
root_id BIGINT NN
from_status VARCHAR(64) N OPEN(Payment Aggregate Status Catalog)
to_status VARCHAR(64) NN OPEN(Payment Aggregate Status Catalog)
transition_no INT NN
reason_code VARCHAR(64) N OPEN(Reason Catalog)
trigger_type VARCHAR(64) NN OPEN(Trigger enum)
trigger_id VARCHAR(128) NN
create_by VARCHAR(128) NN
create_time DATETIME(6) NN

[t_payment_refund_position]
payment_refund_position_id BIGINT NN
payment_intent_id BIGINT NN
successful_attempt_id BIGINT NN
original_channel_txn_ref_id BIGINT NN
original_amount DECIMAL(24,8) NN
reserved_amount DECIMAL(24,8) NN
refunded_amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_refund]
channel_refund_id BIGINT NN
commerce_refund_request_id BIGINT NN
payment_refund_position_id BIGINT NN
refund_amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
refund_status VARCHAR(64) NN {CREATED,PROCESSING,UNKNOWN,SUCCEEDED,FAILED,CANCELLED}
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_callback]
channel_callback_id BIGINT NN
channel_id BIGINT NN
channel_account_id BIGINT NN
external_event_id VARCHAR(255) N
payload_fingerprint CHAR(64) N
signature_status VARCHAR(64) NN {VERIFIED,REJECTED,UNKNOWN}
callback_status VARCHAR(64) NN {RECEIVED,PROCESSING,PROCESSED,REJECTED,EXCEPTION}
received_at DATETIME(6) NN
file_id BIGINT N
content_hash CHAR(64) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_callback_item]
channel_callback_item_id BIGINT NN
channel_callback_id BIGINT NN
item_no INT NN
event_type VARCHAR(64) NN OPEN(Channel Adapter Catalog)
external_transaction_id VARCHAR(255) N
target_type VARCHAR(64) NN {PAYMENT_ACTION,CHANNEL_REFUND,CHANNEL_DISPUTE_NOTICE,CHANNEL_DISPUTE_ACTION,UNKNOWN}
target_id BIGINT N
normalized_status VARCHAR(64) N OPEN(Channel Adapter Catalog)
channel_event_time DATETIME(6) N
mapping_status VARCHAR(64) NN {MAPPED,UNMATCHED,AMBIGUOUS,IGNORED}
create_time DATETIME(6) NN

[t_channel_event_exception]
channel_event_exception_id BIGINT NN
channel_callback_id BIGINT NN
channel_callback_item_id BIGINT N
exception_type VARCHAR(64) NN {SIGNATURE,MATCHING,OUT_OF_ORDER,STATE_CONFLICT,PAYLOAD_SECURITY}
exception_status VARCHAR(64) NN {OPEN,INVESTIGATING,RESOLVED,IGNORED}
reason_code VARCHAR(64) N OPEN(Reason Catalog)
resolved_by VARCHAR(128) N
resolved_at DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_dispute_notice]
channel_dispute_notice_id BIGINT NN
channel_id BIGINT NN
mid_id BIGINT NN
original_channel_txn_ref_id BIGINT NN
external_dispute_id VARCHAR(255) NN
dispute_amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
received_at DATETIME(6) NN
channel_deadline_at DATETIME(6) N
current_channel_status VARCHAR(64) NN {RECEIVED,EVIDENCE_REQUIRED,SUBMITTED,UNDER_REVIEW,WON,LOST,CLOSED}
latest_event_id BIGINT N
latest_event_no INT NN
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_dispute_event]
channel_dispute_event_id BIGINT NN
channel_dispute_notice_id BIGINT NN
event_no INT NN
event_type VARCHAR(64) NN {NOTICE_RECEIVED,STATUS_CHANGED,OUTCOME_RECEIVED,DEADLINE_UPDATED}
from_status VARCHAR(64) N OPEN(Channel Dispute Status Catalog)
to_status VARCHAR(64) N OPEN(Channel Dispute Status Catalog)
channel_event_time DATETIME(6) N
reason_code VARCHAR(64) N OPEN(Reason Catalog)
create_time DATETIME(6) NN

[t_channel_dispute_action]
channel_dispute_action_id BIGINT NN
channel_dispute_notice_id BIGINT NN
dispute_case_id BIGINT NN
dispute_submission_request_id BIGINT N
dispute_submission_request_version BIGINT N
dispute_evidence_package_id BIGINT N
dispute_evidence_package_version BIGINT N
action_type VARCHAR(64) NN {SUBMIT_EVIDENCE,QUERY}
action_status VARCHAR(64) NN {CREATED,SUBMITTED,UNKNOWN,SUCCEEDED,FAILED}
channel_id BIGINT NN
mid_id BIGINT NN
channel_account_id BIGINT NN
channel_idempotency_key VARCHAR(128) NN
channel_result_time DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_command_idempotency]
command_idempotency_id BIGINT NN
caller_scope VARCHAR(128) NN OPEN(Payment Caller Scope Catalog)
operation_type VARCHAR(64) NN OPEN(Payment Command Operation Catalog)
idempotency_key VARCHAR(128) NN
request_hash CHAR(64) NN
command_status VARCHAR(64) NN {PROCESSING,SUCCEEDED,FAILED}
resource_type VARCHAR(64) N OPEN(Payment Command Resource Type Catalog)
resource_id BIGINT N
response_code VARCHAR(64) N OPEN(Payment Command Response Code Catalog)
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_compensation_event]
id BIGINT NN
event_key VARCHAR(128) NN
event_type VARCHAR(64) NN OPEN(Payment Event/Handler Catalog)
payload_json VARCHAR(4096) N
execute_status VARCHAR(16) NN {INIT,PROCESSING,ERROR,FAILED}
execute_count INT NN
next_execute_at DATETIME(6) NN
lock_owner VARCHAR(64) N
lock_until DATETIME(6) N
last_error_code VARCHAR(64) N OPEN(Payment Error Catalog)
last_error_summary VARCHAR(1000) N
lock_version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
sub_event_type VARCHAR(64) NN OPEN(Payment Compensation Sub Event Catalog)
`;
const INDEX_MANIFEST = String.raw`
t_payment_channel
UK uk_payment_channel_code(channel_code)
t_channel_account
UK uk_channel_account_code(channel_id,account_code)
UK uk_channel_account_external(channel_id,external_account_id)
UK uk_channel_account_config(channel_id,integration_config_key)
UK uk_channel_account_owner(channel_account_id,channel_id)
IDX idx_channel_account_status(channel_id,account_status)
t_channel_mid
UK uk_channel_mid(channel_id,external_mid_code)
IDX idx_channel_mid_account(channel_account_id,mid_status)
IDX idx_channel_mid_entity(entity_no,mid_status)
t_mid_capability
UK uk_mid_capability_scope(mid_id,payment_method_code,scheme_code,country_code,currency,mcc_code,capability_code)
t_mid_health_state
UK uk_mid_health_state(mid_id,payment_method_code)
t_routing_policy
UK uk_routing_policy_scope(scope_type,scope_id,country_code,currency,payment_method_code,industry_code,mcc_code,match_priority)
UK uk_route_policy_cur_ver(current_version_id)
t_routing_policy_version
UK uk_routing_policy_version(routing_policy_id,version_no)
t_routing_policy_mid
UK uk_routing_policy_mid(routing_policy_version_id,mid_id)
IDX idx_route_policy_mid_mid(mid_id)
t_routing_decision
UK uk_routing_decision_attempt(payment_attempt_id)
IDX idx_route_dec_policy(routing_policy_id)
IDX idx_route_dec_pol_ver(routing_policy_version_id)
IDX idx_route_dec_channel(selected_channel_id)
IDX idx_route_dec_mid(selected_mid_id)
IDX idx_route_dec_account(selected_channel_account_id)
t_route_candidate_eval
UK uk_route_candidate_eval(routing_decision_id,mid_id)
IDX idx_route_eval_channel(channel_id)
IDX idx_route_eval_mid(mid_id)
IDX idx_route_eval_account(channel_account_id)
t_payment_intent_binding
UK uk_pay_binding_source(source_type,source_id)
UK uk_pay_binding_intent(payment_intent_id)
t_payment_intent
UK uk_payment_intent_source(source_type,source_id)
IDX idx_payment_intent_expiry(intent_status,expires_at,payment_intent_id)
UK uk_payment_intent_active(active_attempt_id)
UK uk_payment_intent_success(successful_attempt_id)
t_payment_attempt
UK uk_payment_attempt_no(payment_intent_id,attempt_no)
UK uk_pay_attempt_submission(source_submission_id)
UK uk_pay_attempt_token(payment_instrument_token_id)
t_payment_pricing_fact
UK uk_pricing_fact_attempt(payment_attempt_id)
IDX idx_pricing_fact_action(source_action_id)
t_payment_instrument_token
UK uk_pay_token_value(payment_instrument_token)
UK uk_pay_token_scope(client_id,payment_intent_id,source_submission_id)
t_payment_action
UK uk_payment_action_attempt_no(payment_attempt_id,action_no)
UK uk_payment_action_refund_no(channel_refund_id,action_no)
UK uk_pay_action_dup_case_no(duplicate_payment_case_id,action_no)
UK uk_payment_action_idempotency(channel_id,channel_idempotency_key)
IDX idx_pay_action_mid(mid_id)
IDX idx_pay_action_account(channel_account_id)
IDX idx_pay_action_target_ref(target_channel_txn_ref_id)
IDX idx_pay_action_target_action(target_payment_action_id)
IDX idx_pay_action_authentication(authentication_action_id)
IDX idx_pay_action_operation(channel_account_id,channel_operation_ref)
t_channel_transaction_ref
UK uk_channel_transaction_ref(channel_id,mid_id,reference_type,external_transaction_id)
IDX idx_txn_ref_action(payment_action_id)
IDX idx_txn_ref_mid(mid_id)
IDX idx_txn_ref_original(original_channel_txn_ref_id)
t_channel_interaction
UK uk_channel_interaction_action(payment_action_id,interaction_no)
UK uk_channel_interact_dispute_act(channel_dispute_action_id,interaction_no)
t_duplicate_payment_case
UK uk_dup_case_duplicate_action(duplicate_action_id)
IDX idx_dup_case_intent(payment_intent_id)
IDX idx_dup_case_canonical_attempt(canonical_attempt_id)
IDX idx_dup_case_duplicate_attempt(duplicate_attempt_id)
IDX idx_dup_case_canonical_action(canonical_action_id)
IDX idx_dup_case_canonical_ref(canonical_transaction_ref_id)
IDX idx_dup_case_duplicate_ref(duplicate_transaction_ref_id)
IDX idx_dup_case_comp_action(compensation_action_id)
t_payment_state_transition
UK uk_payment_transition_no(aggregate_type,aggregate_id,transition_no)
IDX idx_payment_transition_root(root_type,root_id,create_time)
IDX idx_payment_transition_trigger(trigger_type,trigger_id,create_time)
t_payment_refund_position
UK uk_payment_refund_position(payment_intent_id,original_channel_txn_ref_id)
IDX idx_refund_pos_attempt(successful_attempt_id)
IDX idx_refund_pos_orig_ref(original_channel_txn_ref_id)
t_channel_refund
UK uk_channel_refund_commerce_req(commerce_refund_request_id)
IDX idx_channel_refund_pos(payment_refund_position_id)
t_channel_callback
UK uk_channel_callback_dedup(channel_id,channel_account_id,external_event_id)
UK uk_channel_callback_fingerprint(channel_id,channel_account_id,payload_fingerprint)
IDX idx_callback_account(channel_account_id)
IDX idx_callback_pending(callback_status,received_at,channel_callback_id)
t_channel_callback_item
UK uk_channel_callback_item(channel_callback_id,item_no)
t_channel_event_exception
IDX idx_event_exc_callback(channel_callback_id)
IDX idx_event_exc_item(channel_callback_item_id)
IDX idx_event_exc_open(exception_status,create_time,channel_event_exception_id)
t_channel_dispute_notice
UK uk_channel_dispute_external(channel_id,external_dispute_id)
IDX idx_dispute_notice_mid(mid_id)
IDX idx_dispute_notice_orig_ref(original_channel_txn_ref_id)
UK uk_dispute_notice_latest(latest_event_id)
IDX idx_dispute_status_due(current_channel_status,channel_deadline_at)
t_channel_dispute_event
UK uk_channel_dispute_event(channel_dispute_notice_id,event_no)
t_channel_dispute_action
UK uk_dispute_action_idempotency(channel_id,channel_idempotency_key)
IDX idx_dispute_action_notice(channel_dispute_notice_id)
IDX idx_dispute_action_mid(mid_id)
IDX idx_dispute_action_account(channel_account_id)
t_command_idempotency
UK uk_command_idempotency(caller_scope,operation_type,idempotency_key)
t_compensation_event
UK uk_comp_event_identity(event_key,event_type,sub_event_type)
IDX idx_comp_event_ready(execute_status,next_execute_at,id)
IDX idx_comp_event_lease(execute_status,lock_until,id)
`;
const RELATION_MANIFEST = String.raw`
1:1 payment_pricing_fact.payment_attempt_id -> payment_attempt.payment_attempt_id
n:1 payment_pricing_fact.source_action_id -> payment_action.payment_action_id
n:1 channel_account.channel_id -> payment_channel.channel_id
n:1 channel_mid.channel_id -> payment_channel.channel_id
n:1 channel_mid.channel_account_id -> channel_account.channel_account_id
n:1 mid_capability.mid_id -> channel_mid.mid_id
n:1 mid_health_state.mid_id -> channel_mid.mid_id
n:1 routing_policy_version.routing_policy_id -> routing_policy.routing_policy_id
1:1 routing_policy.current_version_id -> routing_policy_version.routing_policy_version_id
n:1 routing_policy_mid.routing_policy_version_id -> routing_policy_version.routing_policy_version_id
n:1 routing_policy_mid.mid_id -> channel_mid.mid_id
1:1 routing_decision.payment_attempt_id -> payment_attempt.payment_attempt_id
n:1 routing_decision.routing_policy_id -> routing_policy.routing_policy_id
n:1 routing_decision.routing_policy_version_id -> routing_policy_version.routing_policy_version_id
n:1 routing_decision.selected_channel_id -> payment_channel.channel_id
n:1 routing_decision.selected_mid_id -> channel_mid.mid_id
n:1 routing_decision.selected_channel_account_id -> channel_account.channel_account_id
n:1 route_candidate_eval.routing_decision_id -> routing_decision.routing_decision_id
n:1 route_candidate_eval.channel_id -> payment_channel.channel_id
n:1 route_candidate_eval.mid_id -> channel_mid.mid_id
n:1 route_candidate_eval.channel_account_id -> channel_account.channel_account_id
n:1 payment_attempt.payment_intent_id -> payment_intent.payment_intent_id
1:1 payment_intent.active_attempt_id -> payment_attempt.payment_attempt_id
1:1 payment_intent.successful_attempt_id -> payment_attempt.payment_attempt_id
1:1 payment_attempt.payment_instrument_token_id -> payment_instrument_token.payment_instrument_token_id
n:1 payment_action.payment_attempt_id -> payment_attempt.payment_attempt_id
n:1 payment_action.channel_refund_id -> channel_refund.channel_refund_id
n:1 payment_action.duplicate_payment_case_id -> duplicate_payment_case.duplicate_payment_case_id
n:1 payment_action.channel_id -> payment_channel.channel_id
n:1 payment_action.mid_id -> channel_mid.mid_id
n:1 payment_action.channel_account_id -> channel_account.channel_account_id
n:1 payment_action.target_channel_txn_ref_id -> channel_transaction_ref.channel_transaction_ref_id
1:1 payment_intent_binding.payment_intent_id -> payment_intent.payment_intent_id
n:1 payment_action.target_payment_action_id -> payment_action.payment_action_id
n:1 payment_action.authentication_action_id -> payment_action.payment_action_id
n:1 channel_transaction_ref.payment_action_id -> payment_action.payment_action_id
n:1 channel_transaction_ref.channel_id -> payment_channel.channel_id
n:1 channel_transaction_ref.mid_id -> channel_mid.mid_id
n:1 channel_transaction_ref.original_channel_txn_ref_id -> channel_transaction_ref.channel_transaction_ref_id
n:1 channel_interaction.payment_action_id -> payment_action.payment_action_id
n:1 channel_interaction.channel_dispute_action_id -> channel_dispute_action.channel_dispute_action_id
n:1 duplicate_payment_case.payment_intent_id -> payment_intent.payment_intent_id
n:1 duplicate_payment_case.canonical_attempt_id -> payment_attempt.payment_attempt_id
n:1 duplicate_payment_case.duplicate_attempt_id -> payment_attempt.payment_attempt_id
n:1 duplicate_payment_case.canonical_action_id -> payment_action.payment_action_id
n:1 duplicate_payment_case.duplicate_action_id -> payment_action.payment_action_id
n:1 duplicate_payment_case.canonical_transaction_ref_id -> channel_transaction_ref.channel_transaction_ref_id
n:1 duplicate_payment_case.duplicate_transaction_ref_id -> channel_transaction_ref.channel_transaction_ref_id
n:1 duplicate_payment_case.compensation_action_id -> payment_action.payment_action_id
n:1 payment_refund_position.payment_intent_id -> payment_intent.payment_intent_id
n:1 payment_refund_position.successful_attempt_id -> payment_attempt.payment_attempt_id
n:1 payment_refund_position.original_channel_txn_ref_id -> channel_transaction_ref.channel_transaction_ref_id
n:1 channel_refund.payment_refund_position_id -> payment_refund_position.payment_refund_position_id
n:1 channel_callback.channel_id -> payment_channel.channel_id
n:1 channel_callback.channel_account_id -> channel_account.channel_account_id
n:1 channel_callback_item.channel_callback_id -> channel_callback.channel_callback_id
n:1 channel_event_exception.channel_callback_id -> channel_callback.channel_callback_id
n:1 channel_event_exception.channel_callback_item_id -> channel_callback_item.channel_callback_item_id
n:1 channel_dispute_notice.channel_id -> payment_channel.channel_id
n:1 channel_dispute_notice.mid_id -> channel_mid.mid_id
n:1 channel_dispute_notice.original_channel_txn_ref_id -> channel_transaction_ref.channel_transaction_ref_id
1:1 channel_dispute_notice.latest_event_id -> channel_dispute_event.channel_dispute_event_id
n:1 channel_dispute_event.channel_dispute_notice_id -> channel_dispute_notice.channel_dispute_notice_id
n:1 channel_dispute_action.channel_dispute_notice_id -> channel_dispute_notice.channel_dispute_notice_id
n:1 channel_dispute_action.channel_id -> payment_channel.channel_id
n:1 channel_dispute_action.mid_id -> channel_mid.mid_id
n:1 channel_dispute_action.channel_account_id -> channel_account.channel_account_id
`;

const DIAGRAM_GROUPS = Object.freeze([
  Object.freeze({
    defKey: "channel_mid",
    defName: "Channel 与 MID",
    primary: Object.freeze(["payment_channel", "channel_account", "channel_mid", "mid_capability", "mid_health_state"]),
    references: Object.freeze([]),
  }),
  Object.freeze({
    defKey: "payment_routing",
    defName: "支付路由",
    primary: Object.freeze(["routing_policy", "routing_policy_version", "routing_policy_mid", "routing_decision", "route_candidate_eval"]),
    references: Object.freeze(["payment_channel", "channel_account", "channel_mid", "payment_attempt"]),
  }),
  Object.freeze({
    defKey: "payment_execution",
    defName: "支付执行",
    primary: Object.freeze(["payment_intent", "payment_attempt", "payment_pricing_fact", "payment_instrument_token", "payment_action", "payment_intent_binding", "channel_transaction_ref", "channel_interaction", "duplicate_payment_case", "payment_state_transition"]),
    references: Object.freeze(["payment_channel", "channel_account", "channel_mid", "routing_decision", "channel_refund"]),
  }),
  Object.freeze({
    defKey: "channel_refund",
    defName: "Channel Refund",
    primary: Object.freeze(["payment_refund_position", "channel_refund"]),
    references: Object.freeze(["payment_intent", "payment_attempt", "payment_action", "channel_transaction_ref"]),
  }),
  Object.freeze({
    defKey: "channel_callback",
    defName: "Channel Callback",
    primary: Object.freeze(["channel_callback", "channel_callback_item", "channel_event_exception"]),
    references: Object.freeze(["payment_channel", "channel_account", "payment_action", "channel_refund", "channel_dispute_notice", "channel_dispute_action"]),
  }),
  Object.freeze({
    defKey: "channel_dispute",
    defName: "Channel Dispute",
    primary: Object.freeze(["channel_dispute_notice", "channel_dispute_event", "channel_dispute_action"]),
    references: Object.freeze(["payment_channel", "channel_account", "channel_mid", "channel_transaction_ref", "channel_interaction"]),
  }),
  Object.freeze({
    defKey: "reliability",
    defName: "可靠执行",
    primary: Object.freeze(["command_idempotency", "compensation_event"]),
    references: Object.freeze([]),
  }),
]);

const VERSION_TABLES = Object.freeze([
  "routing_policy",
  "payment_intent",
  "payment_attempt",
  "payment_action",
  "duplicate_payment_case",
  "payment_refund_position",
  "channel_refund",
  "channel_callback",
  "channel_event_exception",
  "channel_dispute_notice",
  "channel_dispute_action",
]);

const IMMUTABLE_TABLES = Object.freeze([
  "payment_pricing_fact",
  "routing_policy_version",
  "routing_policy_mid",
  "routing_decision",
  "route_candidate_eval",
  "channel_transaction_ref",
  "payment_state_transition",
  "channel_callback_item",
  "channel_dispute_event",
]);

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function stableUuid(key) {
  key = paymentChannelIdentityKey(key);
  if (key.startsWith("field/") && key.endsWith("/entity_no")) key = key.replace(/entity_no$/, "operating_entity_id");
  const bytes = Buffer.from(sha256(`${MODEL_KEY}:${key}`).slice(0, 32), "hex");
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex").toUpperCase();
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
}

function logicalTableKey(physicalTableKey) {
  return physicalTableKey.replace(/^t_/u, "");
}

function physicalTableKey(logicalKey) {
  return `t_${logicalKey}`;
}

function humanize(key) {
  return key.split("_").map((part) => part.toUpperCase()).join(" ");
}

function parseType(typeToken) {
  const match = typeToken.match(/^([A-Z]+)(?:\((\d+)(?:,(\d+))?\))?$/u);
  invariant(match, `Cannot parse physical type: ${typeToken}`);
  return {
    type: match[1],
    len: match[2] === undefined ? "" : Number(match[2]),
    scale: match[3] === undefined ? "" : Number(match[3]),
  };
}

function parseFieldManifest() {
  const headings = [...FIELD_MANIFEST.matchAll(/^\[(t_[a-z0-9_]+)\]$/gmu)];
  return headings.map((heading, tableIndex) => {
    const end = headings[tableIndex + 1]?.index ?? FIELD_MANIFEST.length;
    const section = FIELD_MANIFEST.slice(heading.index + heading[0].length, end);
    const fields = section
      .split(/\r?\n/u)
      .map((line) => line.trim())
      .filter((line) => /^[a-z][a-z0-9_]*\s+/u.test(line))
      .map((line, fieldIndex) => {
        const match = line.match(
          /^([a-z][a-z0-9_]*)\s+([A-Z]+(?:\(\d+(?:,\d+)?\))?)\s+(NN|N)(?:\s+(.*))?$/u,
        );
        invariant(match, `Cannot parse field manifest: ${heading[1]}.${line}`);
        const [, defKey, typeToken, nullability, annotations = ""] = match;
        const closed = annotations.match(/\{([^}]+)\}/u);
        const open = annotations.match(/(?:^|\s)OPEN(?:\(([^)]+)\))?/u);
        return Object.freeze({
          defKey,
          ...parseType(typeToken),
          primaryKey: fieldIndex === 0,
          notNull: nullability === "NN",
          closedValues: closed ? Object.freeze(closed[1].split(",")) : null,
          openSourceHint: open?.[1] ?? (open ? "APPROVED_CATALOG" : null),
        });
      });
    return Object.freeze({
      defKey: logicalTableKey(heading[1]),
      fields: Object.freeze(fields),
    });
  });
}

function parseIndexManifest() {
  const result = new Map();
  let tableKey;
  for (const rawLine of INDEX_MANIFEST.split(/\r?\n/u)) {
    const line = rawLine.trim();
    if (/^t_[a-z0-9_]+$/u.test(line)) {
      tableKey = logicalTableKey(line);
      result.set(tableKey, []);
      continue;
    }
    const match = line.match(/^(UK|IDX)\s+([a-z0-9_]+)\(([^)]+)\)$/u);
    if (!match) continue;
    invariant(tableKey, `Index has no table: ${line}`);
    result.get(tableKey).push(Object.freeze({
      defKey: match[2],
      unique: match[1] === "UK",
      fields: Object.freeze(match[3].split(",").map((field) => field.trim())),
    }));
  }
  return result;
}

function parseRelationManifest() {
  return RELATION_MANIFEST
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => /^(?:1:1|n:1)\s/u.test(line))
    .map((line) => {
      const match = line.match(
        /^(1:1|n:1)\s+([a-z0-9_]+)\.([a-z0-9_]+) -> ([a-z0-9_]+)\.([a-z0-9_]+)$/u,
      );
      invariant(match, `Cannot parse relation manifest: ${line}`);
      return Object.freeze({
        cardinality: match[1],
        childTable: match[2],
        childField: match[3],
        parentTable: match[4],
        parentField: match[5],
      });
    });
}

function relationStableKey(relation) {
  return `${relation.childTable}/${relation.childField}/${relation.parentTable}/${relation.parentField}`;
}

function relationId(relation) {
  return stableUuid(`relation/${relationStableKey(relation)}`);
}

function diagramGroupForRelation(relation) {
  if (
    relation.childTable === "channel_interaction"
    && relation.childField === "channel_dispute_action_id"
    && relation.parentTable === "channel_dispute_action"
  ) {
    return "channel_dispute";
  }
  const groups = DIAGRAM_GROUPS.filter((group) => group.primary.includes(relation.childTable));
  invariant(groups.length === 1, `Relation child must have exactly one primary diagram: ${relationStableKey(relation)}`);
  return groups[0].defKey;
}

function readApprovedTemplate() {
  const templateBytes = readFileSync(templatePath);
  invariant(
    sha256(templateBytes) === EXPECTED_TEMPLATE_DIGEST,
    `Unexpected shared template digest: ${sha256(templateBytes)}`,
  );
  return JSON.parse(templateBytes);
}

function normalizeTemplate(template) {
  const model = clone(template);
  model.name = "Code-MoR Payment Orchestration Database V1";
  model.describe = "Deterministic PDManer physical model derived from the approved Payment Orchestration V1 manifest";
  model.dbConn = [];
  model.dbConns = [];
  model.profile.default.dbConn = "";

  const mappings = new Map(
    model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping]),
  );
  mappings.get("BIGINT")[JAVA_MAPPING_ID] = "Long";
  mappings.get("BIGINT")[MYBATIS_MAPPING_ID] = "Long";
  mappings.get("double")[JAVA_MAPPING_ID] = "BigDecimal";
  mappings.get("double")[MYBATIS_MAPPING_ID] = "BigDecimal";
  mappings.get("date")[JAVA_MAPPING_ID] = "Instant";
  mappings.get("date")[MYBATIS_MAPPING_ID] = "Instant";
  mappings.get("char")[MYBATIS_MAPPING_ID] = "String";

  const domains = new Map(model.domains.map((domain) => [domain.defKey, domain]));
  domains.get("Money").len = 24;
  domains.get("Money").scale = 8;
  domains.get("Decimal").len = 18;
  domains.get("Decimal").scale = 10;
  domains.get("DateTime").len = 6;
  domains.get("currency").applyFor = mappings.get("char").id;
  domains.get("currency").len = 3;

  for (const auditField of model.profile.default.entityInitFields) {
    const metadata = FIELD_METADATA[auditField.defKey];
    invariant(metadata, `Missing audit metadata: ${auditField.defKey}`);
    auditField.type = "DATETIME";
    auditField.len = 6;
    auditField.scale = "";
    auditField.notNull = true;
    auditField.autoIncrement = false;
    auditField.defaultValue = "";
    auditField.defName = metadata.defName;
    auditField.comment = `${metadata.description}。`;
  }

  const namingRules = new Map(model.namingRules.map((rule) => [rule.id, rule]));
  const primaryKeyRule = namingRules.get("2BAB122B-8811-40BB-89F3-CDC24B5862D3");
  if (primaryKeyRule) {
    primaryKeyRule.defName = "主键必须为非自增 Snowflake BIGINT";
    primaryKeyRule.programCode = "let field=data.entity.fields.find(item=>item.primaryKey); return !!field && !field.autoIncrement && (field.type||field.dbType||'').toUpperCase()==='BIGINT';";
  }
  const tablePrefixRule = namingRules.get("1168C7C2-8E8E-4FB7-B639-B3DE839C395A");
  if (tablePrefixRule) {
    tablePrefixRule.defName = "物理表名使用 t_ 前缀和小写 snake_case";
    tablePrefixRule.programCode = "return /^t_[a-z][a-z0-9_]*$/.test(data.entity.defKey);";
  }
  const indexPrefixRule = namingRules.get("023450B3-AAE2-4DC1-AE63-2196DD82823D");
  if (indexPrefixRule) {
    indexPrefixRule.defName = "唯一索引以 uk_ 开头，普通索引以 idx_ 开头";
    indexPrefixRule.programCode = "return data.index.unique ? data.index.defKey.indexOf('uk_')===0 : data.index.defKey.indexOf('idx_')===0;";
  }
  const auditRule = namingRules.get("0B2F0BD2-3B84-4AB1-BA29-9DE9620AF608");
  if (auditRule) {
    auditRule.defName = "可变表使用双审计时间，不可变事实只保留 create_time";
    auditRule.programCode = `let actual=data.entity.fields.map(field=>field.defKey); let immutable=${JSON.stringify(IMMUTABLE_TABLES.map(physicalTableKey))}.includes(data.entity.defKey); return actual.includes('create_time') && (immutable ? !actual.includes('update_time') : actual.includes('update_time'));`;
  }
  const reservedRule = namingRules.get("BEC54F19-52D5-4882-BCE1-4439785F8001");
  if (reservedRule) {
    reservedRule.programCode = reservedRule.programCode.replace(",status,", ",");
  }
  const mysqlDdlTemplate = model.profile.codeTemplates.find((templateSpec) => templateSpec.type === "dbDDL");
  const typeLengthTemplate = "{{=field.type}}{{?field.len>0}}{{='('}}{{=field.len}}{{?field.scale>0}}{{=','}}{{=field.scale}}{{?}}{{=')'}}{{?}}";
  invariant(mysqlDdlTemplate?.createTable.includes(typeLengthTemplate), "MySQL DDL template lacks controlled type fragment");
  mysqlDdlTemplate.createTable = mysqlDdlTemplate.createTable.replace(
    typeLengthTemplate,
    `${typeLengthTemplate}{{? field.extProps && field.extProps.characterSet }} CHARACTER SET {{=field.extProps.characterSet}} COLLATE {{=field.extProps.collation}}{{?}}`,
  );
  return model;
}

function baseTypeFor(field, typeIds, domainIds) {
  const result = { baseType: "", domain: "" };
  if (field.primaryKey) result.domain = domainIds.PrimaryKey;
  if (field.type === "BIGINT") result.baseType = typeIds.BIGINT;
  else if (field.type === "VARCHAR") result.baseType = typeIds.string;
  else if (field.type === "CHAR") result.baseType = typeIds.char;
  else if (field.type === "DECIMAL") result.baseType = typeIds.double;
  else if (field.type === "DATETIME") result.baseType = typeIds.date;
  else if (field.type === "INT") result.baseType = typeIds.int;
  else if (field.type === "TINYINT") result.baseType = typeIds.int;
  else if (["JSON", "TEXT"].includes(field.type)) result.baseType = typeIds.largeText;
  else invariant(false, `Unsupported physical type: ${field.type}`);
  if (field.type === "DATETIME") result.domain = domainIds.DateTime;
  if (field.type === "DECIMAL" && field.len === 24 && field.scale === 8) result.domain = domainIds.Money;
  if (field.type === "DECIMAL" && field.len === 18 && field.scale === 10) result.domain = domainIds.Decimal;
  if (field.type === "CHAR" && field.len === 3) result.domain = domainIds.currency;
  return result;
}

function fieldComment(tableKey, field) {
  const metadata = FIELD_METADATA[field.defKey];
  invariant(metadata, `Missing field metadata: ${tableKey}.${field.defKey}`);
  const pathKey = `${tableKey}.${field.defKey}`;
  const closedValues = CLOSED_ENUM_VALUES[pathKey];
  const openSource = OPEN_CODE_SOURCES[pathKey];
  invariant(!(closedValues && openSource), `Field cannot be both closed and open: ${pathKey}`);
  invariant(
    JSON.stringify(closedValues ?? null) === JSON.stringify(field.closedValues),
    `Closed values disagree with production manifest: ${pathKey}`,
  );
  invariant(Boolean(openSource) === Boolean(field.openSourceHint), `Open code source disagrees: ${pathKey}`);
  const clauses = [`${metadata.description}。`];
  if (closedValues) clauses.push(`允许值：${closedValues.join("、")}。`);
  if (openSource) clauses.push(`取值来源：${openSource}；非固定枚举。`);
  return clauses.join("");
}

function buildIndexes(table, fieldsByKey, specs) {
  const names = new Set();
  const signatures = new Set();
  return specs.map((spec) => {
    invariant(/^(?:uk|idx)_[a-z0-9_]+$/u.test(spec.defKey), `Invalid index name: ${spec.defKey}`);
    invariant(spec.defKey.length <= 32, `Index name exceeds 32 characters: ${spec.defKey}`);
    invariant(!names.has(spec.defKey), `Duplicate index name: ${table.defKey}.${spec.defKey}`);
    names.add(spec.defKey);
    const signature = `${spec.unique}:${spec.fields.join(",")}`;
    invariant(!signatures.has(signature), `Duplicate index signature: ${table.defKey}.${signature}`);
    signatures.add(signature);
    return {
      defKey: spec.defKey,
      defName: humanize(spec.defKey),
      unique: spec.unique,
      comment: `${spec.unique ? "唯一" : "访问"}索引：${spec.fields.join("、")}`,
      fields: spec.fields.map((fieldKey, position) => {
        const field = fieldsByKey.get(fieldKey);
        invariant(field, `Unknown index field: ${table.defKey}.${spec.defKey}.${fieldKey}`);
        return {
          fieldDefKey: field.id,
          ascOrDesc: "A",
          id: stableUuid(`index-field/${table.defKey}/${spec.defKey}/${position}/${fieldKey}`),
        };
      }),
      id: stableUuid(`index/${table.defKey}/${spec.defKey}`),
    };
  });
}

function buildEntities(model, tables, indexesByTable) {
  const typeIds = Object.fromEntries(
    model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id]),
  );
  const domainIds = Object.fromEntries(model.domains.map((domain) => [domain.defKey, domain.id]));
  return tables.map((table) => {
    const fields = table.fields.map((manifestField) => {
      const mapping = baseTypeFor(manifestField, typeIds, domainIds);
      const metadata = FIELD_METADATA[manifestField.defKey];
      invariant(metadata, `Missing metadata: ${table.defKey}.${manifestField.defKey}`);
      return {
        defKey: manifestField.defKey,
        defName: metadata.defName,
        comment: fieldComment(table.defKey, manifestField),
        type: manifestField.type,
        len: manifestField.len,
        scale: manifestField.scale,
        primaryKey: manifestField.primaryKey,
        notNull: manifestField.notNull,
        autoIncrement: false,
        defaultValue: "",
        hideInGraph: false,
        refDict: "",
        baseType: mapping.baseType,
        extProps: ["entity_no", "scope_id"].includes(manifestField.defKey) ? { characterSet: "ascii", collation: "ascii_bin" } : {},
        domain: mapping.domain,
        id: stableUuid(`field/${table.defKey}/${manifestField.defKey}`),
        ...EXECUTION_FIELD_OVERRIDES[`${table.defKey}.${manifestField.defKey}`],
      };
    });
    const fieldsByKey = new Map(fields.map((field) => [field.defKey, field]));
    invariant(TABLE_DISPLAY_NAMES[table.defKey], `Missing table display name: ${table.defKey}`);
    invariant(TABLE_COMMENTS[table.defKey], `Missing table comment: ${table.defKey}`);
    return {
      id: stableUuid(`entity/${table.defKey}`),
      env: { base: { nameSpace: "", codeRoot: "" } },
      defKey: physicalTableKey(table.defKey),
      defName: TABLE_DISPLAY_NAMES[table.defKey],
      comment: TABLE_COMMENTS[table.defKey],
      properties: {},
      sysProps: { nameTemplate: "{defKey}[{defName}]" },
      notes: {},
      headers: clone(model.profile.headers),
      fields,
      correlations: [],
      indexes: buildIndexes(table, fieldsByKey, indexesByTable.get(table.defKey) ?? []),
      type: "P",
    };
  });
}

function applyRelations(entities, relations) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  for (const relation of relations) {
    const child = entitiesByKey.get(relation.childTable);
    const parent = entitiesByKey.get(relation.parentTable);
    invariant(child && parent, `Unknown relation table: ${JSON.stringify(relation)}`);
    const childField = child.fields.find((field) => field.defKey === relation.childField);
    const parentField = parent.fields.find((field) => field.defKey === relation.parentField);
    invariant(childField && parentField, `Unknown relation field: ${JSON.stringify(relation)}`);
    child.correlations.push({
      id: relationId(relation),
      myField: childField.id,
      refEntity: parent.id,
      refField: parentField.id,
      myRows: relation.cardinality.startsWith("n") ? "n" : "1",
      refRows: "1",
      innerType: "",
    });
  }
}

function buildDiagrams(entities, relations) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  return DIAGRAM_GROUPS.map((group) => {
    const tableKeys = [...group.primary, ...group.references];
    const columnCount = 4;
    const heights = tableKeys.map((tableKey) => {
      const entity = entitiesByKey.get(tableKey);
      invariant(entity, `Unknown diagram table: ${group.defKey}.${tableKey}`);
      return Math.max(220, 86 + entity.fields.length * 22);
    });
    const rowOffsets = [];
    for (let row = 0, y = 0; row * columnCount < tableKeys.length; row += 1) {
      rowOffsets.push(y);
      y += Math.max(...heights.slice(row * columnCount, (row + 1) * columnCount)) + 60;
    }
    const cellByTable = new Map();
    const cells = tableKeys.map((tableKey, index) => {
      const entity = entitiesByKey.get(tableKey);
      const cell = {
        id: stableUuid(`diagram/${group.defKey}/table/${tableKey}`),
        shape: "table",
        position: { x: (index % columnCount) * 430, y: rowOffsets[Math.floor(index / columnCount)] },
        count: 0,
        originKey: entity.id,
        type: "P",
        size: { width: 390, height: heights[index] },
      };
      cellByTable.set(tableKey, cell);
      return cell;
    });
    for (const relation of relations.filter((candidate) => diagramGroupForRelation(candidate) === group.defKey)) {
      const child = entitiesByKey.get(relation.childTable);
      const parent = entitiesByKey.get(relation.parentTable);
      const childField = child.fields.find((field) => field.defKey === relation.childField);
      const parentField = parent.fields.find((field) => field.defKey === relation.parentField);
      invariant(cellByTable.has(relation.childTable), `Diagram lacks relation child: ${group.defKey}.${relation.childTable}`);
      invariant(cellByTable.has(relation.parentTable), `Diagram lacks relation parent: ${group.defKey}.${relation.parentTable}`);
      cells.push({
        id: stableUuid(`diagram/${group.defKey}/relation/${relation.childTable}/${relation.childField}/${relation.parentTable}/${relation.parentField}`),
        shape: "erdRelation",
        originKey: relationId(relation),
        source: { cell: cellByTable.get(relation.childTable).id, port: `${childField.id}%out` },
        target: { cell: cellByTable.get(relation.parentTable).id, port: `${parentField.id}%in` },
        relation: relation.cardinality,
        vertices: [],
        fillColor: "#ACDAFC",
        attrs: { line: { strokeDasharray: "" } },
        isLock: true,
      });
    }
    return {
      defKey: group.defKey,
      defName: group.defName,
      relationType: "field",
      canvasData: { cells },
      id: stableUuid(`diagram/${group.defKey}`),
      comment: `${group.defName} bounded-context view`,
    };
  });
}

function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: model.entities.flatMap((entity) => entity.correlations).length,
    diagramGroups: model.diagrams.length,
  };
}

function validateMetadata(tables) {
  const tableKeys = tables.map((table) => table.defKey).sort();
  const fieldKeys = [...new Set(tables.flatMap((table) => table.fields.map((field) => field.defKey)))].sort();
  invariant(JSON.stringify(Object.keys(TABLE_DISPLAY_NAMES).sort()) === JSON.stringify(tableKeys), "Table display metadata must exactly cover the manifest");
  invariant(JSON.stringify(Object.keys(TABLE_COMMENTS).sort()) === JSON.stringify(tableKeys), "Table comments must exactly cover the manifest");
  invariant(JSON.stringify(Object.keys(FIELD_METADATA).sort()) === JSON.stringify(fieldKeys), "Field metadata must exactly cover the manifest");
  const closedMetadataKeys = Object.keys(CLOSED_ENUM_VALUES).sort();
  const closedManifestKeys = tables.flatMap((table) => table.fields
    .filter((field) => field.closedValues)
    .map((field) => `${table.defKey}.${field.defKey}`)).sort();
  invariant(JSON.stringify(closedMetadataKeys) === JSON.stringify(closedManifestKeys), "Closed-enum metadata must exactly cover the manifest");
  const openMetadataKeys = Object.keys(OPEN_CODE_SOURCES).sort();
  const openManifestKeys = tables.flatMap((table) => table.fields
    .filter((field) => field.openSourceHint)
    .map((field) => `${table.defKey}.${field.defKey}`)).sort();
  invariant(JSON.stringify(openMetadataKeys) === JSON.stringify(openManifestKeys), "Open-code metadata must exactly cover the manifest");
  invariant(openManifestKeys.length === 56, `Expected 56 true open-code rows, found ${openManifestKeys.length}`);
  for (const [key, value] of Object.entries(FIELD_METADATA)) {
    invariant(/[\u3400-\u9fff]/u.test(value.defName), `Field display name is not Chinese: ${key}`);
    invariant(/[\u3400-\u9fff]/u.test(value.description), `Field description is not Chinese: ${key}`);
  }
}

function validateGeneratedModel(model, tables, relations) {
  invariant(JSON.stringify(modelTotals(model)) === JSON.stringify({
    tables: 30,
    fields: 402,
    uniqueIndexes: 45,
    accessIndexes: 48,
    relations: 67,
    diagramGroups: 7,
  }), `Unexpected generated totals: ${JSON.stringify(modelTotals(model))}`);

  const entitiesByKey = new Map(model.entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  invariant(tables.length === 30, `Field manifest must have 30 tables, found ${tables.length}`);
  invariant(new Set(VERSION_TABLES).size === 11, "Version allowlist must have 11 tables");
  invariant(new Set(IMMUTABLE_TABLES).size === 9, "Immutable allowlist must have 9 tables");
  const actualVersionTables = model.entities
    .filter((entity) => entity.fields.some((field) => field.defKey === "version"))
    .map((entity) => logicalTableKey(entity.defKey));
  invariant(JSON.stringify(actualVersionTables) === JSON.stringify(VERSION_TABLES), "Business version tables disagree with allowlist");
  for (const tableKey of IMMUTABLE_TABLES) {
    const auditFields = entitiesByKey.get(tableKey).fields
      .filter((field) => ["create_time", "update_time"].includes(field.defKey))
      .map((field) => field.defKey);
    invariant(JSON.stringify(auditFields) === JSON.stringify(["create_time"]), `${tableKey} is not immutable`);
  }
  const compensation = entitiesByKey.get("compensation_event");
  invariant(compensation.fields.some((field) => field.defKey === "lock_version"), "Compensation lock_version is required");
  invariant(!compensation.fields.some((field) => field.defKey === "version"), "Compensation cannot use business version");

  for (const relation of relations) {
    const child = entitiesByKey.get(relation.childTable);
    const childField = child.fields.find((field) => field.defKey === relation.childField);
    const primary = child.fields.find((field) => field.primaryKey);
    const leftPrefix = childField.id === primary.id || child.indexes.some(
      (index) => index.fields[0]?.fieldDefKey === childField.id,
    );
    invariant(leftPrefix, `Relation field lacks left-prefix index: ${relation.childTable}.${relation.childField}`);
    if (relation.cardinality === "1:1") {
      const exactUniqueIndexes = child.indexes.filter(
        (index) => index.unique && index.fields.length === 1 && index.fields[0]?.fieldDefKey === childField.id,
      );
      invariant(
        childField.id === primary.id || exactUniqueIndexes.length === 1,
        `1:1 relation must use the primary key or exactly one single-field UK: ${relation.childTable}.${relation.childField}`,
      );
    }
  }
  invariant(relations.filter((relation) => relation.cardinality === "1:1").length === 8, "Relation manifest must have 8 enforced 1:1 relations");
  const primaryTables = DIAGRAM_GROUPS.flatMap((group) => group.primary);
  invariant(primaryTables.length === 30 && new Set(primaryTables).size === 30, "Every table must have exactly one primary diagram group");
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation");
  invariant(relationCells.length === 67, `Expected 67 relation cells, found ${relationCells.length}`);
  const relationIds = relations.map(relationId);
  invariant(new Set(relationIds).size === relations.length, "Relation IDs must be unique");
  invariant(
    JSON.stringify(relationCells.map((cell) => cell.originKey).sort()) === JSON.stringify([...relationIds].sort()),
    "Every relation must appear in exactly one diagram",
  );
}

export function structuralProjection(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
    domains: model.domains.map((domain) => ({
      id: domain.id,
      defKey: domain.defKey,
      applyFor: domain.applyFor,
      len: domain.len,
      scale: domain.scale,
    })),
    entities: model.entities.map((entity) => {
      const fields = new Map(entity.fields.map((field) => [field.id, field]));
      return {
        id: entity.id,
        defKey: entity.defKey,
        fields: entity.fields.map((field) => ({
          id: field.id,
          defKey: field.defKey,
          type: field.type,
          len: field.len,
          scale: field.scale,
          primaryKey: field.primaryKey,
          notNull: field.notNull,
          autoIncrement: field.autoIncrement,
          baseType: field.baseType,
          domain: field.domain,
        })),
        indexes: entity.indexes.map((index) => ({
          id: index.id,
          defKey: index.defKey,
          unique: index.unique,
          fields: index.fields.map((entry) => ({
            id: entry.id,
            field: fields.get(entry.fieldDefKey).defKey,
            ascOrDesc: entry.ascOrDesc,
          })),
        })),
        correlations: entity.correlations.map((relation) => {
          const parent = entitiesById.get(relation.refEntity);
          const parentFields = new Map(parent.fields.map((field) => [field.id, field]));
          return {
            childField: fields.get(relation.myField).defKey,
            parentTable: parent.defKey,
            parentField: parentFields.get(relation.refField).defKey,
            myRows: relation.myRows,
            refRows: relation.refRows,
            innerType: relation.innerType,
          };
        }),
      };
    }),
    diagrams: model.diagrams.map((diagram) => ({
      id: diagram.id,
      defKey: diagram.defKey,
      cells: diagram.canvasData.cells.map((cell) => ({
        id: cell.id,
        shape: cell.shape,
        originKey: cell.originKey,
        source: cell.source,
        target: cell.target,
        relation: cell.relation,
      })),
    })),
  };
}

export function structuralProjectionDigest(model) {
  return sha256(JSON.stringify(structuralProjection(model)));
}

export function generatePaymentModel({
  template = readApprovedTemplate(),
} = {}) {
  const tables = parseFieldManifest();
  const indexesByTable = parseIndexManifest();
  const relations = parseRelationManifest();
  validateMetadata(tables);
  const model = normalizeTemplate(template);
  model.entities = buildEntities(model, tables, indexesByTable);
  applyRelations(model.entities, relations);
  model.diagrams = buildDiagrams(model.entities, relations);
  validateGeneratedModel(model, tables, relations);
  return model;
}

export function serializePaymentModel(model = generatePaymentModel()) {
  return `${JSON.stringify(model, null, 2)}\n`;
}

function runCli() {
  const unknown = process.argv.slice(2).filter((argument) => argument !== "--check");
  invariant(unknown.length === 0, `Unknown argument(s): ${unknown.join(", ")}`);
  const expected = serializePaymentModel();
  const totals = modelTotals(JSON.parse(expected));
  const summary = `${totals.tables} tables, ${totals.fields} fields, ${totals.uniqueIndexes} unique indexes, ${totals.accessIndexes} access indexes, ${totals.relations} relations, ${totals.diagramGroups} diagram groups`;
  if (process.argv.includes("--check")) {
    invariant(existsSync(outputPath), `Payment model does not exist: ${outputPath}`);
    invariant(readFileSync(outputPath, "utf8") === expected, "Payment model is stale; regenerate it");
    process.stdout.write(`Payment model is up to date (${summary})\n`);
    return;
  }
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, expected, "utf8");
  process.stdout.write(`Wrote Payment model: ${outputPath}\n${summary}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runCli();
}
