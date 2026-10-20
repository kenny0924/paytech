import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { serviceOfTable } from "./payment-service-partition.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const paths = Object.freeze({
  metadata: path.join(workspaceRoot, "scripts/pdmaner/payment-field-metadata.mjs"),
  generator: path.join(workspaceRoot, "scripts/pdmaner/generate-payment-model.mjs"),
  // 第 05 步按表归属拆为两份规范模型（design/03 §2.3）；断言作用于两份模型的并集，t_compensation_event 每库一份只计一次。
  model: path.join(workspaceRoot, "paytech/payment/docs/database/payment.pdma.json"),
  channelConnectorModel: path.join(workspaceRoot, "paytech/channel-connector/docs/database/channel-connector.pdma.json"),
  decisions: path.join(workspaceRoot, "paytech/payment/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md"),
  template: path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json"),
});

const coreArtifactPaths = Object.freeze([paths.metadata, paths.generator, paths.model, paths.channelConnectorModel]);
const expectedTemplateDigest = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
// Physical Decisions freezes the pre-split 27-table unified model (historical manifest, see the document text).
const historicalModelTotals = Object.freeze({ tables: 27, fields: 349, uniqueIndexes: 38, accessIndexes: 44, relations: 63, oneToOneRelations: 7, diagramGroups: 7 });
const historicalStructuralDigest = "2793402a691608c374c988e9ef2e01a24ad965d3fdec1b0f9acaf8ade026b690";
// Current models: payment.pdma.json bytes, and the union with channel-connector.pdma.json (31 tables since the 2026-10-19
// channel cost tables moved into the channel-connector partition, 32 since the 2026-10-20 t_channel_config_change).
const currentModelTotals = Object.freeze({ tables: 32, fields: 400, uniqueIndexes: 45, accessIndexes: 46, relations: 68, oneToOneRelations: 7, diagramGroups: 9 });
const expectedModelDigest = "4fb5a4c88f00bd5552dc4c66de1811aee4bc73aee40c40674c264966261e4979";
const expectedStructuralDigest = "3588e21f75a01a0563a1547a123b19168db0129039169820c7db3af19ad7abf7";

// Test-owned oracle. It must never import a generator manifest or parse Markdown.
const APPROVED_FIELD_MANIFEST = String.raw`
[t_payment_channel]
channel_id BIGINT NN
channel_code VARCHAR(64) NN OPEN(Channel Catalog)
channel_name VARCHAR(255) NN
channel_status VARCHAR(64) NN {DRAFT,ACTIVE,SUSPENDED,CLOSED}
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_account]
channel_account_id BIGINT NN
channel_id BIGINT NN
entity_no CHAR(5) NN
account_name VARCHAR(128) NN
account_vault_token VARCHAR(64) N
account_status VARCHAR(64) NN {DRAFT,ACTIVE,SUSPENDED,CLOSED}
remark VARCHAR(512) N
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
capability_status VARCHAR(64) NN {ENABLED,DISABLED}
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_mid_capability_detail]
mid_capability_detail_id BIGINT NN
mid_capability_id BIGINT NN
capability_code VARCHAR(128) NN OPEN(MID Capability Catalog)
value_type VARCHAR(64) NN {BOOLEAN,DECIMAL,CODE}
boolean_value TINYINT(1) N
decimal_value DECIMAL(24,8) N
code_value VARCHAR(128) N
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

[t_payment_intent]
payment_intent_id BIGINT NN
source_type VARCHAR(64) NN OPEN(Payment Source Type Catalog)
source_id BIGINT NN
client_id BIGINT NN
entity_no CHAR(5) NN
client_pricing_version_id BIGINT NN
partner_share_version_id BIGINT N
platform_charge_version_id BIGINT N
industry_code VARCHAR(64) NN OPEN(CCT/Commerce frozen classification)
mcc_code VARCHAR(4) NN OPEN(CCT/Commerce frozen classification)
consumer_country_code CHAR(2) NN OPEN(ISO 3166-1 alpha-2)
amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
intent_status VARCHAR(64) NN {OPEN,SUCCEEDED,CANCELLED,EXPIRED}
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
source_submission_id BIGINT N
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
capture_mode VARCHAR(32) N {AUTOMATIC,MANUAL}
payment_instrument_token_id BIGINT N
channel_id BIGINT N
channel_account_id BIGINT N
mid_id BIGINT N
attempt_status VARCHAR(64) NN {PROCESSING,SUCCEEDED,FAILED,CANCELLED}
active_action_id BIGINT N
authorized_amount DECIMAL(24,8) NN
captured_amount DECIMAL(24,8) NN
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_payment_instrument_token]
payment_instrument_token_id BIGINT NN
payment_instrument_token VARCHAR(64) NN
client_id BIGINT NN
payment_intent_id BIGINT NN
source_submission_id BIGINT NN
payment_method_code VARCHAR(64) NN OPEN(Payment Method Catalog)
instrument_type VARCHAR(32) NN {CARD}
instrument_ref VARCHAR(128) NN
customer_id BIGINT N
consent_to_save TINYINT(1) NN
card_bin VARCHAR(8) N
card_fingerprint CHAR(64) N
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
action_type VARCHAR(64) NN {PAY_RISK,AUTHENTICATE,AUTHORIZE,CAPTURE,PAY,VOID,REFUND}
action_status VARCHAR(64) NN {PROCESSING,UNKNOWN,SUCCEEDED,FAILED}
target_channel_txn_ref_id BIGINT N
amount DECIMAL(24,8) NN
currency CHAR(3) NN OPEN(ISO 4217)
channel_idempotency_key VARCHAR(128) N
transaction_assessment_id BIGINT N
risk_result VARCHAR(64) N {ALLOW,REVIEW,DENY,FAILED,TIMED_OUT}
authentication_requirement VARCHAR(32) N {NOT_REQUIRED,THREE_DS_REQUIRED,CHALLENGE_REQUIRED}
interaction_type VARCHAR(32) N {REDIRECT,THREE_DS_CHALLENGE,SDK,APP_SWITCH,QR_CODE}
interaction_expires_at DATETIME(6) N
channel_response_code VARCHAR(128) N OPEN(Channel Adapter Catalog)
channel_response_msg VARCHAR(512) N
channel_result_time DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_transaction_ref]
channel_transaction_ref_id BIGINT NN
payment_action_id BIGINT NN
channel_id BIGINT NN
mid_id BIGINT NN
reference_type VARCHAR(64) NN {AUTHORIZE,CAPTURE,PAY,VOID,REFUND}
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
idempotency_key VARCHAR(255) NN
request_hash CHAR(64) NN
request_hash_version INT NN
request_snapshot_schema_version INT N
request_snapshot_json JSON N
operation_context_time DATETIME(6) NN
recovery_stage VARCHAR(64) N OPEN(Payment Command Recovery Stage Catalog)
process_status VARCHAR(64) NN {PROCESSING,SUCCEEDED,FAILED}
resource_type VARCHAR(64) N OPEN(Payment Command Resource Type Catalog)
resource_id BIGINT N
resource_no VARCHAR(64) N
response_code VARCHAR(64) N OPEN(Payment Command Response Code Catalog)
response_http_status INT N
response_snapshot_schema_version INT N
response_location VARCHAR(255) N
response_body_json JSON N
lock_owner VARCHAR(64) N
lock_until DATETIME(6) N
version BIGINT NN
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

[t_channel_cost_plan]
id BIGINT NN
channel_id BIGINT NN
plan_code VARCHAR(64) NN OPEN(channel-connector 渠道成本方案代码)
current_ver_id BIGINT N
status VARCHAR(64) NN {DRAFT,ACTIVE,INACTIVE}
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_cost_plan_ver]
id BIGINT NN
plan_id BIGINT NN
cost_ver INT NN
effective_from DATETIME(6) NN
effective_to DATETIME(6) N
content_hash CHAR(64) NN
status VARCHAR(64) NN {DRAFT,PENDING_APPROVAL,APPROVED,ACTIVE,REJECTED,INACTIVE}
maker_id BIGINT NN
checker_id BIGINT N
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_channel_cost_scope]
id BIGINT NN
cost_ver_id BIGINT NN
scope_no INT NN
mid_id BIGINT NN
entity_no CHAR(5) NN
channel_agreement_ref VARCHAR(128) NN
country_code CHAR(2) NN OPEN(ISO 3166-1 alpha-2 国家代码)
currency CHAR(3) NN OPEN(ISO 4217 币种代码)
payment_method_code VARCHAR(64) NN OPEN(Payment 支付方式目录)
action_type VARCHAR(64) NN OPEN(Payment 动作类型目录)
create_time DATETIME(6) NN

[t_channel_cost_component]
id BIGINT NN
scope_id BIGINT NN
component_code VARCHAR(64) NN OPEN(channel-connector 渠道成本组件代码)
component_type VARCHAR(64) NN {PROCESSING_FEE,CROSS_BORDER_FEE,REFUND_FEE,DISPUTE_FEE}
calculation_base VARCHAR(64) NN {TRANSACTION_AMOUNT}
rate_value DECIMAL(18,10) N
fixed_amount DECIMAL(24,8) N
currency CHAR(3) N OPEN(ISO 4217 币种代码)
min_amount DECIMAL(24,8) N
max_amount DECIMAL(24,8) N
rounding_mode VARCHAR(64) NN {HALF_UP}
create_time DATETIME(6) NN

[t_channel_config_change]
id BIGINT NN
target_type VARCHAR(64) NN {CHANNEL,CHANNEL_ACCOUNT,CHANNEL_MID,MID_CAPABILITY,CHANNEL_COST_VERSION}
target_id BIGINT NN
change_type VARCHAR(64) NN {CREATE,UPDATE,STATUS,CONFIGURATION,REBIND,CANCEL}
from_status VARCHAR(64) N OPEN(渠道、账号、MID、能力组或成本版本的状态代码)
to_status VARCHAR(64) N OPEN(渠道、账号、MID、能力组或成本版本的状态代码)
operator_id BIGINT NN
reason VARCHAR(512) N
create_time DATETIME(6) NN
`;

const APPROVED_INDEX_MANIFEST = String.raw`
t_payment_channel
UK uk_payment_channel_code(channel_code)
t_channel_account
UK uk_channel_account_owner(channel_account_id,channel_id)
IDX idx_channel_account_status(channel_id,account_status)
IDX idx_channel_account_entity(entity_no,account_status)
t_channel_mid
UK uk_channel_mid(channel_id,external_mid_code)
IDX idx_channel_mid_account(channel_account_id,mid_status)
IDX idx_channel_mid_entity(entity_no,mid_status)
t_mid_capability
UK uk_mid_capability_scope(mid_id,payment_method_code,scheme_code,country_code,currency,mcc_code)
t_mid_capability_detail
UK uk_mid_cap_detail_code(mid_capability_id,capability_code)
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
t_payment_intent
UK uk_payment_intent_source(source_type,source_id)
IDX idx_payment_intent_expiry(intent_status,expires_at,payment_intent_id)
UK uk_payment_intent_active(active_attempt_id)
UK uk_payment_intent_success(successful_attempt_id)
t_payment_attempt
UK uk_payment_attempt_no(payment_intent_id,attempt_no)
UK uk_pay_attempt_submission(source_submission_id)
UK uk_pay_attempt_token(payment_instrument_token_id)
IDX idx_pay_attempt_channel(channel_id)
IDX idx_pay_attempt_account(channel_account_id)
IDX idx_pay_attempt_mid(mid_id)
t_payment_instrument_token
UK uk_pay_token_value(payment_instrument_token)
UK uk_pay_token_scope(client_id,payment_intent_id,source_submission_id)
t_payment_action
UK uk_payment_action_attempt_no(payment_attempt_id,action_no)
UK uk_payment_action_refund_no(channel_refund_id,action_no)
UK uk_pay_action_dup_case_no(duplicate_payment_case_id,action_no)
UK uk_payment_action_idempotency(channel_idempotency_key)
IDX idx_pay_action_target_ref(target_channel_txn_ref_id)
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
t_channel_cost_plan
UK uk_channel_cost_plan(channel_id)
UK uk_channel_cost_code(plan_code)
UK uk_channel_cost_current(current_ver_id)
t_channel_cost_plan_ver
UK uk_channel_cost_ver(plan_id,cost_ver)
t_channel_cost_scope
UK uk_channel_cost_scope_no(cost_ver_id,scope_no)
UK uk_channel_cost_scope(cost_ver_id,mid_id,entity_no,country_code,currency,payment_method_code,action_type)
IDX idx_channel_cost_match(mid_id,entity_no,country_code,currency)
t_channel_cost_component
UK uk_channel_cost_component(scope_id,component_code)
t_channel_config_change
IDX idx_channel_config_change_target(target_type,target_id,id)
`;

const APPROVED_RELATION_MANIFEST = String.raw`
n:1 channel_account.channel_id -> payment_channel.channel_id
n:1 channel_mid.channel_id -> payment_channel.channel_id
n:1 channel_mid.channel_account_id -> channel_account.channel_account_id
n:1 mid_capability.mid_id -> channel_mid.mid_id
n:1 mid_capability_detail.mid_capability_id -> mid_capability.mid_capability_id
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
1:1 payment_attempt.active_action_id -> payment_action.payment_action_id
n:1 payment_attempt.payment_intent_id -> payment_intent.payment_intent_id
1:1 payment_intent.active_attempt_id -> payment_attempt.payment_attempt_id
1:1 payment_intent.successful_attempt_id -> payment_attempt.payment_attempt_id
1:1 payment_attempt.payment_instrument_token_id -> payment_instrument_token.payment_instrument_token_id
n:1 payment_action.payment_attempt_id -> payment_attempt.payment_attempt_id
n:1 payment_action.channel_refund_id -> channel_refund.channel_refund_id
n:1 payment_action.duplicate_payment_case_id -> duplicate_payment_case.duplicate_payment_case_id
n:1 payment_attempt.channel_id -> payment_channel.channel_id
n:1 payment_attempt.mid_id -> channel_mid.mid_id
n:1 payment_attempt.channel_account_id -> channel_account.channel_account_id
n:1 payment_action.target_channel_txn_ref_id -> channel_transaction_ref.channel_transaction_ref_id
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
n:1 channel_cost_plan.channel_id -> payment_channel.channel_id
n:1 channel_cost_plan_ver.plan_id -> channel_cost_plan.id
n:1 channel_cost_scope.cost_ver_id -> channel_cost_plan_ver.id
n:1 channel_cost_scope.mid_id -> channel_mid.mid_id
n:1 channel_cost_component.scope_id -> channel_cost_scope.id
`;

// Row-level invariants that future MySQL migrations must implement. PDManer 4.9.3
// does not expose portable CHECK nodes, so the generated model freezes the
// participating fields while Physical Decisions freezes these exact expressions.
const APPROVED_CHECK_MANIFEST = String.raw`
[t_channel_account]
CK ck_channel_account_status(account_status) :: account_status IN ('DRAFT','ACTIVE','SUSPENDED','CLOSED')

[t_channel_mid]
CK ck_channel_mid_times(mid_status,activated_at,closed_at) :: (mid_status = 'DRAFT' AND activated_at IS NULL AND closed_at IS NULL) OR (mid_status IN ('ACTIVE','SUSPENDED') AND activated_at IS NOT NULL AND closed_at IS NULL) OR (mid_status = 'CLOSED' AND closed_at IS NOT NULL AND (activated_at IS NULL OR closed_at >= activated_at))

[t_mid_capability_detail]
CK ck_mid_capability_values(value_type,boolean_value,decimal_value,code_value) :: (value_type = 'BOOLEAN' AND boolean_value IS NOT NULL AND boolean_value IN (0,1) AND decimal_value IS NULL AND code_value IS NULL) OR (value_type = 'DECIMAL' AND boolean_value IS NULL AND decimal_value IS NOT NULL AND code_value IS NULL) OR (value_type = 'CODE' AND boolean_value IS NULL AND decimal_value IS NULL AND code_value IS NOT NULL)

[t_routing_policy]
CK ck_route_policy_scope(scope_type,scope_id) :: (scope_type IN ('CLIENT','OPERATING_ENTITY') AND scope_id > 0) OR (scope_type = 'GLOBAL' AND scope_id = 0)
CK ck_route_policy_priority(match_priority) :: match_priority > 0
CK ck_route_policy_active(policy_status,current_version_id) :: policy_status <> 'ACTIVE' OR current_version_id IS NOT NULL
CK ck_route_policy_root_ver(version) :: version > 0

[t_routing_policy_version]
CK ck_route_policy_ver_no(version_no) :: version_no > 0

[t_routing_policy_mid]
CK ck_route_policy_mid_rank(candidate_priority,weight_value) :: candidate_priority > 0 AND weight_value > 0

[t_routing_decision]
CK ck_route_decision_policy_pair(routing_policy_id,routing_policy_version_id) :: (routing_policy_id IS NULL AND routing_policy_version_id IS NULL) OR (routing_policy_id IS NOT NULL AND routing_policy_version_id IS NOT NULL)
CK ck_route_decision_state(decision_status,routing_policy_id,routing_policy_version_id,selected_channel_id,selected_mid_id,selected_channel_account_id,decision_reason_code) :: (decision_status = 'SELECTED' AND routing_policy_id IS NOT NULL AND routing_policy_version_id IS NOT NULL AND selected_channel_id IS NOT NULL AND selected_mid_id IS NOT NULL AND selected_channel_account_id IS NOT NULL) OR (decision_status = 'CONFIGURATION_CONFLICT' AND routing_policy_id IS NULL AND routing_policy_version_id IS NULL AND selected_channel_id IS NULL AND selected_mid_id IS NULL AND selected_channel_account_id IS NULL AND decision_reason_code IS NOT NULL) OR (decision_status = 'REJECTED' AND selected_channel_id IS NULL AND selected_mid_id IS NULL AND selected_channel_account_id IS NULL AND decision_reason_code IS NOT NULL)

[t_route_candidate_eval]
CK ck_route_candidate_flag(eligible_flag,channel_account_id,health_status,circuit_status,health_score,technical_success_rate,timeout_rate,p95_latency_ms,sample_count,health_observed_at,exclusion_reason_code) :: (eligible_flag = 1 AND channel_account_id IS NOT NULL AND health_status IS NOT NULL AND circuit_status IS NOT NULL AND health_score IS NOT NULL AND technical_success_rate IS NOT NULL AND timeout_rate IS NOT NULL AND p95_latency_ms IS NOT NULL AND sample_count IS NOT NULL AND health_observed_at IS NOT NULL AND exclusion_reason_code IS NULL) OR (eligible_flag = 0 AND exclusion_reason_code IS NOT NULL)
CK ck_route_candidate_health(channel_account_id,health_status,circuit_status,health_score,technical_success_rate,timeout_rate,p95_latency_ms,sample_count,health_observed_at) :: (health_status IS NULL AND circuit_status IS NULL AND health_score IS NULL AND technical_success_rate IS NULL AND timeout_rate IS NULL AND p95_latency_ms IS NULL AND sample_count IS NULL AND health_observed_at IS NULL) OR (channel_account_id IS NOT NULL AND health_status IS NOT NULL AND circuit_status IS NOT NULL AND health_score IS NOT NULL AND technical_success_rate IS NOT NULL AND timeout_rate IS NOT NULL AND p95_latency_ms IS NOT NULL AND sample_count IS NOT NULL AND health_observed_at IS NOT NULL)
CK ck_route_candidate_rank(candidate_priority,weight_value) :: candidate_priority > 0 AND weight_value > 0

[t_payment_intent]
CK ck_payment_intent_amount(amount) :: amount > 0
CK ck_payment_intent_pointer(intent_status,active_attempt_id,successful_attempt_id) :: (intent_status = 'OPEN' AND successful_attempt_id IS NULL) OR (intent_status = 'SUCCEEDED' AND active_attempt_id IS NULL AND successful_attempt_id IS NOT NULL) OR (intent_status IN ('EXPIRED','CANCELLED') AND active_attempt_id IS NULL AND successful_attempt_id IS NULL)
CK ck_payment_intent_version(version) :: version > 0

[t_payment_attempt]
CK ck_payment_attempt_no(attempt_no) :: attempt_no > 0
CK ck_payment_attempt_amounts(authorized_amount,captured_amount) :: authorized_amount >= 0 AND captured_amount >= 0
CK ck_payment_attempt_version(version) :: version > 0

[t_payment_action]
CK ck_payment_action_parent(payment_attempt_id,channel_refund_id,duplicate_payment_case_id) :: (payment_attempt_id IS NOT NULL) + (channel_refund_id IS NOT NULL) + (duplicate_payment_case_id IS NOT NULL) = 1
CK ck_payment_action_parent_type(payment_attempt_id,channel_refund_id,duplicate_payment_case_id,action_type) :: (payment_attempt_id IS NOT NULL AND action_type IN ('PAY_RISK','AUTHENTICATE','AUTHORIZE','CAPTURE','PAY','VOID')) OR (channel_refund_id IS NOT NULL AND action_type = 'REFUND') OR (duplicate_payment_case_id IS NOT NULL AND action_type = 'REFUND')
CK ck_payment_action_values(action_no,amount,version) :: action_no > 0 AND amount > 0 AND version > 0


[t_channel_transaction_ref]
CK ck_channel_txn_amount(amount) :: amount > 0

[t_channel_interaction]
CK ck_channel_interact_parent(payment_action_id,channel_dispute_action_id) :: (payment_action_id IS NOT NULL) + (channel_dispute_action_id IS NOT NULL) = 1
CK ck_channel_interact_no(interaction_no) :: interaction_no > 0
CK ck_channel_interact_req_file(request_file_id,request_content_hash) :: (request_file_id IS NULL AND request_content_hash IS NULL) OR (request_file_id IS NOT NULL AND request_content_hash IS NOT NULL)
CK ck_channel_interact_rsp_file(response_file_id,response_content_hash) :: (response_file_id IS NULL AND response_content_hash IS NULL) OR (response_file_id IS NOT NULL AND response_content_hash IS NOT NULL)
CK ck_channel_interact_state(interaction_status,started_at,finished_at,latency_ms) :: (interaction_status = 'STARTED' AND finished_at IS NULL AND latency_ms IS NULL) OR (interaction_status IN ('RESPONSE_RECEIVED','NETWORK_ERROR','TIMED_OUT') AND finished_at IS NOT NULL AND finished_at >= started_at AND latency_ms IS NOT NULL AND latency_ms >= 0)

[t_duplicate_payment_case]
CK ck_duplicate_case_values(duplicate_amount,version) :: duplicate_amount > 0 AND version > 0


[t_payment_refund_position]
CK ck_refund_position_amounts(original_amount,reserved_amount,refunded_amount,version) :: original_amount > 0 AND reserved_amount >= 0 AND refunded_amount >= 0 AND reserved_amount + refunded_amount <= original_amount AND version > 0

[t_channel_refund]
CK ck_channel_refund_values(refund_amount,version) :: refund_amount > 0 AND version > 0

[t_channel_callback]
CK ck_channel_callback_dedup(external_event_id,payload_fingerprint) :: external_event_id IS NOT NULL OR payload_fingerprint IS NOT NULL
CK ck_channel_callback_file(file_id,content_hash) :: (file_id IS NULL AND content_hash IS NULL) OR (file_id IS NOT NULL AND content_hash IS NOT NULL)
CK ck_channel_callback_version(version) :: version > 0

[t_channel_callback_item]
CK ck_channel_callback_item_no(item_no) :: item_no > 0

[t_channel_event_exception]
CK ck_event_exception_resolution(exception_status,resolved_by,resolved_at) :: (exception_status IN ('OPEN','INVESTIGATING') AND resolved_by IS NULL AND resolved_at IS NULL) OR (exception_status IN ('RESOLVED','IGNORED') AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
CK ck_event_exception_version(version) :: version > 0

[t_channel_dispute_notice]
CK ck_dispute_notice_values(dispute_amount,received_at,channel_deadline_at,latest_event_id,latest_event_no,version) :: dispute_amount > 0 AND (channel_deadline_at IS NULL OR channel_deadline_at > received_at) AND ((latest_event_id IS NULL AND latest_event_no = 0) OR (latest_event_id IS NOT NULL AND latest_event_no > 0)) AND version > 0

[t_channel_dispute_event]
CK ck_channel_dispute_event_no(event_no) :: event_no > 0

[t_channel_dispute_action]
CK ck_dispute_action_payload(action_type,dispute_submission_request_id,dispute_submission_request_version,dispute_evidence_package_id,dispute_evidence_package_version) :: (action_type = 'SUBMIT_EVIDENCE' AND dispute_submission_request_id IS NOT NULL AND dispute_submission_request_version IS NOT NULL AND dispute_submission_request_version > 0 AND dispute_evidence_package_id IS NOT NULL AND dispute_evidence_package_version IS NOT NULL AND dispute_evidence_package_version > 0) OR (action_type = 'QUERY' AND dispute_submission_request_id IS NULL AND dispute_submission_request_version IS NULL AND dispute_evidence_package_id IS NULL AND dispute_evidence_package_version IS NULL)
CK ck_dispute_action_version(version) :: version > 0

[t_command_idempotency]
CK ck_command_resource_pair(resource_type,resource_id) :: (resource_type IS NULL AND resource_id IS NULL) OR (resource_type IS NOT NULL AND resource_id IS NOT NULL)

[t_compensation_event]
CK ck_compensation_counters(execute_count,lock_version) :: execute_count >= 0 AND lock_version > 0
CK ck_compensation_lease(execute_status,lock_owner,lock_until) :: (execute_status = 'PROCESSING' AND lock_owner IS NOT NULL AND lock_until IS NOT NULL) OR (execute_status IN ('INIT','ERROR','FAILED') AND lock_owner IS NULL AND lock_until IS NULL)
`;

const approvedTableNames = Object.freeze([
  "t_payment_channel", "t_channel_account", "t_channel_mid", "t_mid_capability", "t_mid_capability_detail",
  "t_routing_policy", "t_routing_policy_version", "t_routing_policy_mid", "t_routing_decision", "t_route_candidate_eval",
  "t_payment_intent", "t_payment_attempt", "t_payment_instrument_token", "t_payment_action", "t_channel_transaction_ref", "t_channel_interaction", "t_duplicate_payment_case",
  "t_payment_refund_position", "t_channel_refund", "t_channel_callback", "t_channel_callback_item", "t_channel_event_exception", "t_channel_dispute_notice", "t_channel_dispute_event", "t_channel_dispute_action", "t_command_idempotency", "t_compensation_event",
  // 2026-10-19 计费事实重构：渠道成本四表自 billing 迁入 channel-connector 分区。
  "t_channel_cost_plan", "t_channel_cost_plan_ver", "t_channel_cost_scope", "t_channel_cost_component",
  // 2026-10-20 channel-connector 运营控制台：渠道配置变更留痕表。
  "t_channel_config_change",
]);

const forbiddenTables = Object.freeze([
  "t_payment_pricing_fact", "t_payment_state_transition",
  "t_payment_customer_action", "t_channel_connection", "t_channel_connection_version", "t_mid_limit", "t_mid_credential_ref", "t_mid_health", "t_routing_policy_binding", "t_routing_rule", "t_routing_rule_condition", "t_payment_execution_gate", "t_payment_execution_gate_result", "t_three_ds_context", "t_payment_recovery_task", "t_channel_refund_attempt", "t_refund_transaction_ref", "t_payment_state_conflict", "t_channel_dispute_projection", "t_restriction_projection", "t_business_change_record", "t_business_change_version",
]);
const forbiddenFieldKeys = Object.freeze([
  "connection_id", "connection_code", "connection_status", "connection_version_id", "selected_connection_version_id", "endpoint_url", "credential_secret_ref", "webhook_secret_ref", "connect_timeout_ms", "read_timeout_ms", "mid_credential_ref_id", "credential_type", "credential_status", "secret_ref", "active_slot", "success_slot", "scope_hash", "projection_hash", "snapshot_hash", "snapshot_schema_version", "routing_policy_binding_id", "routing_rule_id", "routing_rule_condition_id", "three_ds_context_id", "three_ds_status", "channel_three_ds_reference", "channel_three_ds_transaction_id", "protocol_version", "eci", "liability_shift_flag", "challenge_deadline_at", "channel_token_ref", "redirect_url", "client_secret", "sdk_token", "qr_payload", "payment_recovery_task_id", "channel_refund_attempt_id", "refund_transaction_ref_id", "funding_binding_hash", "pan", "cvv", "cvc", "card_expiry", "card_exp", "track_data", "pin_block", "private_key", "password", "jdbc_url",
]);
const hashFieldWhitelist = new Set([
  "request_hash", "payload_hash", "payload_fingerprint", "content_hash", "request_content_hash", "response_content_hash",
]);
const versionFieldWhitelist = new Set([
  "t_command_idempotency", "t_routing_policy", "t_payment_intent", "t_payment_attempt", "t_payment_action", "t_duplicate_payment_case", "t_payment_refund_position", "t_channel_refund", "t_channel_callback", "t_channel_event_exception", "t_channel_dispute_notice", "t_channel_dispute_action", "t_channel_cost_plan",
]);
const expectedTableDisplayNames = Object.freeze({
  payment_channel: "支付服务商", channel_account: "支付服务商账户", channel_mid: "服务商商户号", mid_capability: "商户号静态能力", mid_capability_detail: "商户号能力明细",
  routing_policy: "路由策略", routing_policy_version: "路由策略版本", routing_policy_mid: "路由策略候选商户号", routing_decision: "路由决策", route_candidate_eval: "路由候选评估",
  payment_intent: "支付意图", payment_attempt: "支付尝试", payment_instrument_token: "交易级支付工具 Token", payment_action: "支付动作", channel_transaction_ref: "服务商交易引用", channel_interaction: "服务商交互记录", duplicate_payment_case: "重复支付处置",
  payment_refund_position: "渠道可退款位置", channel_refund: "渠道退款", channel_callback: "服务商回调", channel_callback_item: "服务商回调项", channel_event_exception: "服务商事件异常", channel_dispute_notice: "服务商争议通知", channel_dispute_event: "服务商争议事件", channel_dispute_action: "服务商争议动作", command_idempotency: "命令幂等记录", compensation_event: "本地补偿事件",
  channel_cost_plan: "渠道成本方案", channel_cost_plan_ver: "渠道成本方案版本", channel_cost_scope: "渠道成本适用范围", channel_cost_component: "渠道成本组件",
  channel_config_change: "渠道配置变更记录",
});
const expectedTableComments = Object.freeze({
  payment_channel: "支付渠道的稳定身份和当前运行状态。",
  channel_account: "归属单个 Channel 与 OE 的技术账号；账号参数及凭据只保存 Vault 引用。",
  channel_mid: "服务商商户号与唯一 Operating Entity 的稳定归属及当前 Channel Account 绑定；归属创建后不得改写。",
  mid_capability: "商户号按支付方式、支付体系、消费者国家、币种和精确 MCC 定义的能力条件组。",
  mid_capability_detail: "能力条件组下的类型化能力值；按能力代码唯一，值类型与受控目录一致。",
  routing_policy: "按固定交易作用域匹配的路由策略根，当前发布版本只由 current_version_id 指针确定。",
  routing_policy_version: "不可变路由候选集发布版本。",
  routing_policy_mid: "路由策略版本中的候选商户号、优先级和权重。",
  routing_decision: "一次支付尝试的不可变最终路由结论，冻结服务商、Channel Account 和商户号。",
  route_candidate_eval: "一次路由决策中全部候选的不可变资格评估证据。",
  payment_intent: "从已提交Commerce Context取得固定entityNo、金额及Term Version，所有Attempt只能在同一OE下执行；新写去Assignment。Risk未接入新身份时不得伪造资格或跨OE降级。",
  payment_attempt: "同一支付意图内的一次支付尝试，冻结方式、捕获模式、工具及选定渠道，持有唯一活动动作。",
  payment_instrument_token: "Execution 拥有的交易级支付工具 Token 与不可变使用范围，不存卡数据或会话状态。",
  payment_action: "一次风控或渠道业务步骤；复用原动作恢复，通过所属 Attempt 取得冻结渠道。",
  channel_transaction_ref: "授权、捕获、扣款、撤销或退款的不可变服务商外部交易引用。",
  channel_interaction: "一次真实服务商 HTTP 交互的受控通信记录，可选文件引用必须通过安全扫描。",
  duplicate_payment_case: "第二笔真实渠道成功的处置根，不能替换支付意图的首笔成功指针。",
  payment_refund_position: "Payment 侧渠道可退款金额位置，预占、消费和释放由乐观锁原子保护。",
  channel_refund: "一个 Commerce Refund Request 的唯一渠道退款根，UNKNOWN 继续占用退款位置。",
  channel_callback: "服务商回调信封的 Channel Account 上下文、验签、去重和批次处理状态。",
  channel_callback_item: "批量服务商回调中逐项归一化和目标映射的不可变证据。",
  channel_event_exception: "签名、匹配、乱序或非资金状态冲突的独立处置记录。",
  channel_dispute_notice: "服务商争议身份、金额事实和当前渠道状态根。",
  channel_dispute_event: "服务商争议的不可变事件流。",
  channel_dispute_action: "按 Risk 精确请求提交举证或查询并冻结 Channel Account 的服务商动作。",
  command_idempotency: "Foundation 公共组件持久化的 Payment 本地命令 Claim、Lease、CAS 与结果定位；业务幂等语义由 Payment 拥有。",
  compensation_event: "业务变化同事务登记的本地可靠执行记录；成功后删除，FAILED 保留并可受控重开。",
  channel_cost_plan: "保存每个 Payment Channel 的预计渠道成本规则稳定身份和当前版本指针；billing 经命中接口取组件并冻结证据，本库不算金额。",
  channel_cost_plan_ver: "保存成本规则发布批次的生效期、内容摘要及双人审批状态，当前版本仅由方案根指针确定；APPROVED 版本到达生效时间后由到期切换 Job 原子切换为当前版本。",
  channel_cost_scope: "冻结成本版本对 MID、经营主体、国家、币种、支付方式和动作的确定性匹配范围。",
  channel_cost_component: "冻结渠道成本适用范围下的百分比、固定费、跨境费、退款费或争议费参数；金额由 billing 按证据计算。",
  channel_config_change: "渠道配置与成本版本的人工变更留痕；与业务变更同事务写入，只增不改。",
});
const expectedDiagramGroups = Object.freeze({
  channel_mid: Object.freeze({ defName: "Channel 与 MID", primary: Object.freeze(["payment_channel", "channel_account", "channel_mid", "mid_capability", "mid_capability_detail"]), references: Object.freeze([]) }),
  payment_routing: Object.freeze({ defName: "支付路由", primary: Object.freeze(["routing_policy", "routing_policy_version", "routing_policy_mid", "routing_decision", "route_candidate_eval"]), references: Object.freeze(["payment_channel", "channel_account", "channel_mid", "payment_attempt"]) }),
  payment_execution: Object.freeze({ defName: "支付执行", primary: Object.freeze(["payment_intent", "payment_attempt", "payment_instrument_token", "payment_action", "channel_transaction_ref", "channel_interaction", "duplicate_payment_case", ]), references: Object.freeze(["payment_channel", "channel_account", "channel_mid", "routing_decision", "channel_refund"]) }),
  channel_refund: Object.freeze({ defName: "Channel Refund", primary: Object.freeze(["payment_refund_position", "channel_refund"]), references: Object.freeze(["payment_intent", "payment_attempt", "payment_action", "channel_transaction_ref"]) }),
  channel_callback: Object.freeze({ defName: "Channel Callback", primary: Object.freeze(["channel_callback", "channel_callback_item", "channel_event_exception"]), references: Object.freeze(["payment_channel", "channel_account", "payment_action", "channel_refund", "channel_dispute_notice", "channel_dispute_action"]) }),
  channel_dispute: Object.freeze({ defName: "Channel Dispute", primary: Object.freeze(["channel_dispute_notice", "channel_dispute_event", "channel_dispute_action"]), references: Object.freeze(["payment_channel", "channel_account", "channel_mid", "channel_transaction_ref", "channel_interaction"]) }),
  reliability: Object.freeze({ defName: "可靠执行", primary: Object.freeze(["command_idempotency", "compensation_event"]), references: Object.freeze([]) }),
  channel_cost: Object.freeze({ defName: "渠道成本配置", primary: Object.freeze(["channel_cost_plan", "channel_cost_plan_ver", "channel_cost_scope", "channel_cost_component"]), references: Object.freeze(["payment_channel", "channel_mid"]) }),
  channel_config_change: Object.freeze({ defName: "渠道配置变更留痕", primary: Object.freeze(["channel_config_change"]), references: Object.freeze([]) }),
});

function sha256(value) { return createHash("sha256").update(value).digest("hex"); }
function logicalTableKey(key) { return key.replace(/^t_/u, ""); }
function parseType(type) {
  const match = type.match(/^([A-Z]+)(?:\((\d+)(?:,(\d+))?\))?$/u);
  assert.ok(match, `无法解析类型 ${type}`);
  return { type: match[1], len: match[2] ? Number(match[2]) : "", scale: match[3] ? Number(match[3]) : "" };
}
function parseApprovedFields() {
  const headings = [...APPROVED_FIELD_MANIFEST.matchAll(/^\[(t_[a-z0-9_]+)\]$/gmu)];
  return headings.map((heading, index) => {
    const body = APPROVED_FIELD_MANIFEST.slice(heading.index + heading[0].length, headings[index + 1]?.index);
    return { defKey: heading[1], fields: body.split(/\r?\n/u).map((line) => line.trim()).filter(Boolean).map((line, position) => {
      const match = line.match(/^([a-z][a-z0-9_]*)\s+([A-Z]+(?:\(\d+(?:,\d+)?\))?)\s+(NN|N)(.*)$/u);
      assert.ok(match, `无法解析字段 oracle：${heading[1]}.${line}`);
      const enumMatch = match[4].match(/\{([^}]+)\}/u);
      const openMatch = match[4].match(/(?:^|\s)OPEN(?:\(([^)]+)\))?/u);
      return { defKey: match[1], ...parseType(match[2]), notNull: match[3] === "NN", primaryKey: position === 0, closedValues: enumMatch?.[1].split(",") ?? null, openSource: openMatch?.[1] ?? (openMatch ? "APPROVED_CATALOG" : null) };
    }) };
  });
}
function parseIndexes() {
  const indexes = new Map(); let current;
  for (const line of APPROVED_INDEX_MANIFEST.split(/\r?\n/u).map((value) => value.trim()).filter(Boolean)) {
    if (/^t_/u.test(line)) { current = line; indexes.set(current, []); continue; }
    const match = line.match(/^(UK|IDX)\s+(\w+)\(([^)]+)\)$/u);
    assert.ok(match && current, `无法解析索引 oracle：${line}`);
    indexes.get(current).push({ defKey: match[2], unique: match[1] === "UK", fields: match[3].split(",") });
  }
  return indexes;
}
function parseRelations() {
  return APPROVED_RELATION_MANIFEST.split(/\r?\n/u).map((line) => line.trim()).filter(Boolean).map((line) => {
    const match = line.match(/^(1:1|n:1)\s+([a-z0-9_]+)\.([a-z0-9_]+) -> ([a-z0-9_]+)\.([a-z0-9_]+)$/u);
    assert.ok(match, `无法解析关系 oracle：${line}`);
    return { cardinality: match[1], child: match[2], childField: match[3], parent: match[4], parentField: match[5] };
  });
}
function parseChecks() {
  const checks = []; let current;
  for (const line of APPROVED_CHECK_MANIFEST.split(/\r?\n/u).map((value) => value.trim()).filter(Boolean)) {
    const heading = line.match(/^\[(t_[a-z0-9_]+)\]$/u);
    if (heading) { current = heading[1]; continue; }
    const match = line.match(/^CK\s+(ck_[a-z0-9_]+)\(([^)]*)\)\s+::\s+(.+)$/u);
    assert.ok(match && current, `无法解析 CHECK oracle：${line}`);
    checks.push({ table: current, name: match[1], fields: match[2].split(",").filter(Boolean), expression: match[3].replace(/\s+/gu, " ").trim() });
  }
  return checks;
}
function physicalDecisionChecks() {
  const source = readFileSync(paths.decisions, "utf8");
  const start = source.indexOf("<!-- PAYMENT_CHECK_CONSTRAINTS_START -->");
  const end = source.indexOf("<!-- PAYMENT_CHECK_CONSTRAINTS_END -->");
  assert.ok(start >= 0 && end > start, "Payment Physical Decisions 缺少 CHECK 约束标记区");
  return source.slice(start, end).split(/\r?\n/u).flatMap((line) => {
    const match = line.match(/^\|\s*`(t_[a-z0-9_]+)`\s*\|\s*`(ck_[a-z0-9_]+)`\s*\|\s*`(.+)`\s*\|$/u);
    return match ? [{ table: match[1], name: match[2], expression: match[3].replace(/\s+/gu, " ").trim() }] : [];
  });
}
function physicalDecisionManifest() {
  const source = readFileSync(paths.decisions, "utf8");
  const match = source.match(/<!-- PAYMENT_PHYSICAL_MANIFEST_START -->\s*```json\s*([\s\S]*?)\s*```\s*<!-- PAYMENT_PHYSICAL_MANIFEST_END -->/u);
  assert.ok(match, "Payment Physical Decisions 缺少物理 Manifest 标记区");
  return JSON.parse(match[1]);
}
function readModel() {
  const payment = JSON.parse(readFileSync(paths.model, "utf8"));
  const channelConnector = JSON.parse(readFileSync(paths.channelConnectorModel, "utf8"));
  const names = new Set(payment.entities.map((entity) => entity.defKey));
  return { ...payment, entities: [...payment.entities, ...channelConnector.entities.filter((entity) => !names.has(entity.defKey))],
    diagrams: [...payment.diagrams, ...channelConnector.diagrams] };
}
// 跨服务（跨库）关系在拆分后不再出现在任一模型中；本地关系 = 两端同属一个服务库。
function isLocalRelation(relation) {
  return serviceOfTable(`t_${relation.child}`) === serviceOfTable(`t_${relation.parent}`);
}
function entityMap(model) { return new Map(model.entities.map((entity) => [entity.defKey, entity])); }
function fieldMap(entity) { return new Map(entity.fields.map((field) => [field.id, field])); }
function relationSignatures(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return model.entities.flatMap((entity) => {
    const childFields = fieldMap(entity);
    return entity.correlations.map((relation) => {
      const parent = entitiesById.get(relation.refEntity);
      assert.ok(parent, `关系指向不存在实体：${entity.defKey}.${relation.id}`);
      const childField = childFields.get(relation.myField);
      const parentField = fieldMap(parent).get(relation.refField);
      assert.ok(childField && parentField, `关系字段不存在：${entity.defKey}.${relation.id}`);
      return `${relation.myRows}:${relation.refRows} ${logicalTableKey(entity.defKey)}.${childField.defKey} -> ${logicalTableKey(parent.defKey)}.${parentField.defKey}`;
    });
  });
}
function modelRelations(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return model.entities.flatMap((child) => child.correlations.map((relation) => ({
    relation,
    child,
    parent: entitiesById.get(relation.refEntity),
    childField: fieldMap(child).get(relation.myField),
  })));
}
function expectedMetadataContracts() {
  const tables = parseApprovedFields();
  return {
    fields: [...new Set(tables.flatMap((table) => table.fields.map((field) => field.defKey)))].sort(),
    closed: Object.fromEntries(tables.flatMap((table) => table.fields.filter((field) => field.closedValues)
      .map((field) => [`${logicalTableKey(table.defKey)}.${field.defKey}`, field.closedValues]))),
    open: Object.fromEntries(tables.flatMap((table) => table.fields.filter((field) => field.openSource)
      .map((field) => [`${logicalTableKey(table.defKey)}.${field.defKey}`, field.openSource]))),
  };
}
function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  const relations = model.entities.flatMap((entity) => entity.correlations);
  return { tables: model.entities.length, fields: model.entities.flatMap((entity) => entity.fields).length, uniqueIndexes: indexes.filter((index) => index.unique).length, accessIndexes: indexes.filter((index) => !index.unique).length, relations: relations.length, oneToOneRelations: relations.filter((relation) => `${relation.myRows}:${relation.refRows}` === "1:1").length, diagramGroups: model.diagrams.length };
}
function structuralDigest(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  const projection = {
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
  return sha256(JSON.stringify(projection));
}
function assertCoreReady() {
  const missing = coreArtifactPaths.filter((artifact) => !existsSync(artifact)).map((artifact) => path.relative(workspaceRoot, artifact));
  assert.deepEqual(missing, [], `Payment PDManer core readiness failure: missing ${missing.join(", ")}`);
}
function assertPhysicalDecisionsReady() {
  assert.ok(existsSync(paths.decisions), `Payment Physical Decisions readiness failure: missing ${path.relative(workspaceRoot, paths.decisions)}`);
}

const coreArtifactsReadyAtRegistration = coreArtifactPaths.every((artifact) => existsSync(artifact));
const physicalDecisionsReadyAtRegistration = existsSync(paths.decisions);

test("Payment test-owned Oracle is internally complete for all 32 tables", () => {
  const approvedFields = parseApprovedFields();
  assert.deepEqual(approvedFields.map((table) => table.defKey), approvedTableNames);
  assert.ok(approvedFields.every((table) => table.fields.length > 1));
  assert.deepEqual([...parseIndexes().keys()], approvedTableNames);
  assert.equal(approvedFields.reduce((count, table) => count + table.fields.length, 0), 400);
  const oracleIndexes = [...parseIndexes().values()].flat();
  assert.equal(oracleIndexes.filter((index) => index.unique).length, 45);
  assert.equal(oracleIndexes.filter((index) => !index.unique).length, 46);
  assert.equal(parseRelations().length, 68);
  assert.equal(parseRelations().filter((relation) => relation.cardinality === "1:1").length, 7);
  const logicalTables = new Set(approvedTableNames.map(logicalTableKey));
  const fieldsByTable = new Map(approvedFields.map((table) => [logicalTableKey(table.defKey), table.fields]));
  const fieldsByPhysicalTable = new Map(approvedFields.map((table) => [table.defKey, new Set(table.fields.map((field) => field.defKey))]));
  const indexes = parseIndexes();
  for (const table of approvedFields) {
    const fieldKeys = table.fields.map((field) => field.defKey);
    assert.equal(new Set(fieldKeys).size, fieldKeys.length, `Oracle 含重复字段 defKey：${table.defKey}`);
    for (const field of table.fields) {
      if (!["http_status", "response_http_status"].includes(field.defKey) && /(?:_code|_type|_status|source_type|currency|country_code|industry_code|mcc_code|scheme_code)$/u.test(field.defKey)) {
        assert.ok(field.closedValues || field.openSource, `代码/类型/状态/来源/币种/国家字段未标注闭合或开放：${table.defKey}.${field.defKey}`);
      }
    }
  }
  for (const tableIndexes of indexes.values()) for (const index of tableIndexes) {
    assert.ok(index.defKey.length <= 32, `索引名超过 32 字符：${index.defKey}`);
    assert.match(index.defKey, index.unique ? /^uk_/u : /^idx_/u);
  }
  for (const relation of parseRelations()) {
    assert.ok(logicalTables.has(relation.child), `关系 child 不属于 Payment：${relation.child}`);
    assert.ok(logicalTables.has(relation.parent), `关系 parent 不属于 Payment：${relation.parent}`);
    const childFields = fieldsByTable.get(relation.child);
    assert.ok(childFields.some((field) => field.defKey === relation.childField), `关系 child 字段不存在：${relation.child}.${relation.childField}`);
    const childIndexes = indexes.get(`t_${relation.child}`);
    const primary = childFields[0].defKey === relation.childField;
    if (relation.child === "payment_attempt" && relation.childField === "active_action_id") continue; // Application-owned pointer, guarded by parent lock and CAS.
    if (relation.cardinality === "1:1") {
      const exactUniqueIndexes = childIndexes.filter((index) => index.unique && index.fields.length === 1 && index.fields[0] === relation.childField);
      assert.ok(primary || exactUniqueIndexes.length === 1, `Oracle 的 1:1 child 必须是主键或恰好一个单字段 UK：${relation.child}.${relation.childField}`);
      continue;
    }
    const indexed = childIndexes.some((index) => index.fields[0] === relation.childField);
    assert.ok(primary || indexed, `Oracle 缺少关系左前缀索引：${relation.child}.${relation.childField}`);
  }
  const checkNames = new Set();
  for (const check of parseChecks()) {
    assert.ok(fieldsByPhysicalTable.has(check.table), `CHECK 指向不存在表：${check.table}.${check.name}`);
    assert.match(check.name, /^ck_/u);
    assert.ok(check.name.length <= 32, `CHECK 名超过 32 字符：${check.name}`);
    assert.ok(!checkNames.has(check.name), `CHECK 名重复：${check.name}`);
    checkNames.add(check.name);
    assert.ok(check.fields.length > 0 && check.expression.length > 0, `CHECK 缺少字段或表达式：${check.name}`);
    for (const field of check.fields) assert.ok(fieldsByPhysicalTable.get(check.table).has(field), `CHECK 引用不存在字段：${check.table}.${field}`);
  }
});

test("Payment PDManer core artifacts are ready for model validation", assertCoreReady);

test("Payment channel refund has no Finance reservation binding", () => {
  assertCoreReady();
  const channelRefund = readModel().entities.find((entity) => entity.defKey === "t_channel_refund");
  const fieldKeys = channelRefund.fields.map((field) => field.defKey);
  const indexKeys = channelRefund.indexes.map((index) => index.defKey);

  assert.ok(!fieldKeys.includes("refund_funding_reservation_id"));
  assert.ok(!fieldKeys.includes("funding_reserve_version"));
  assert.ok(!fieldKeys.includes("funding_bound_version"));
  assert.ok(!indexKeys.includes("uk_channel_refund_funding"));
});
test("Payment Physical Decisions artifact is ready for Task 6 validation", assertPhysicalDecisionsReady);

if (physicalDecisionsReadyAtRegistration) {
test("Payment Physical Decisions freezes current model totals and digests", () => {
  assertPhysicalDecisionsReady();
  assert.deepEqual(physicalDecisionManifest(), {
    modelTotals: historicalModelTotals,
    templateSha256: expectedTemplateDigest,
    modelSha256: expectedModelDigest,
    structuralProjectionSha256: historicalStructuralDigest,
  });
});

test("Payment Physical Decisions freezes every approved row-level CHECK", () => {
  assertPhysicalDecisionsReady();
  const expected = parseChecks().map(({ table, name, expression }) => ({ table, name, expression }));
  assert.deepEqual(physicalDecisionChecks(), expected);
});
}

if (coreArtifactsReadyAtRegistration) {
test("Payment model has the exact approved 32-table set and no retired roots", () => {
    assertCoreReady();
    const model = readModel();
    // The union lists payment tables before channel-connector tables (step 05 split), so compare as a set.
    const actualTableNames = model.entities.map((entity) => entity.defKey);
    assert.deepEqual([...actualTableNames].sort(), [...approvedTableNames].sort());
    assert.equal(new Set(actualTableNames).size, actualTableNames.length);
    const names = new Set(actualTableNames);
    for (const table of forbiddenTables) assert.ok(!names.has(table), `恢复已删除表 ${table}`);
});

test("Payment model exactly implements its independent field and index manifests", () => {
    assertCoreReady();
    const model = readModel(); const entities = entityMap(model); const approved = parseApprovedFields();
    assert.deepEqual(approved.map((table) => table.defKey), approvedTableNames);
    for (const table of approved) {
      const actual = entities.get(table.defKey);
      assert.ok(actual, `缺少表 ${table.defKey}`);
      assert.deepEqual(actual.fields.map(({ defKey, type, len, scale, notNull, primaryKey }) => ({ defKey, type, len, scale, notNull, primaryKey })), table.fields.map(({ defKey, type, len, scale, notNull, primaryKey }) => ({ defKey, type, len, scale, notNull, primaryKey })));
    }
    const approvedIndexes = parseIndexes();
    for (const [tableKey, expected] of approvedIndexes) {
      const fields = fieldMap(entities.get(tableKey));
      const actual = entities.get(tableKey).indexes.map((index) => ({ defKey: index.defKey, unique: index.unique, fields: index.fields.map((field) => fields.get(field.fieldDefKey)?.defKey) }));
      assert.deepEqual(actual, expected);
    }
});

test("Payment model freezes routing, risk and state enum contracts", () => {
    assertCoreReady();
    const entities = entityMap(readModel());
    const enums = new Map(parseApprovedFields().flatMap((table) => table.fields.filter((field) => field.closedValues).map((field) => [`${table.defKey}.${field.defKey}`, field.closedValues])));
    for (const [fieldPath, expected] of enums) {
      const [tableKey, fieldKey] = fieldPath.split(".");
      const actual = entities.get(tableKey).fields.find((field) => field.defKey === fieldKey);
      assert.ok(actual.comment.includes(`允许值：${expected.join("、")}。`), `${fieldPath} 未列全闭合枚举`);
    }
    assert.deepEqual(enums.get("t_payment_attempt.attempt_status"), ["PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED"]);
    assert.deepEqual(enums.get("t_payment_action.action_status"), ["PROCESSING", "UNKNOWN", "SUCCEEDED", "FAILED"]);
    assert.deepEqual(enums.get("t_payment_action.interaction_type"), ["REDIRECT", "THREE_DS_CHALLENGE", "SDK", "APP_SWITCH", "QR_CODE"]);
    assert.deepEqual(enums.get("t_routing_policy.scope_type"), ["CLIENT", "OPERATING_ENTITY", "GLOBAL"]);
    for (const fieldPath of ["t_routing_decision.selected_channel_account_id", "t_route_candidate_eval.channel_account_id", "t_route_candidate_eval.health_observed_at", "t_payment_attempt.channel_account_id", "t_channel_callback.channel_account_id", "t_channel_dispute_action.channel_account_id"]) {
      const [tableKey, fieldKey] = fieldPath.split(".");
      assert.ok(entities.get(tableKey).fields.some((field) => field.defKey === fieldKey), `缺少关键字段 ${fieldPath}`);
    }
});

test("Payment model has only the approved local relations", () => {
    assertCoreReady();
    const model = readModel();
    const expected = parseRelations().filter(isLocalRelation).map((relation) => `${relation.cardinality} ${relation.child}.${relation.childField} -> ${relation.parent}.${relation.parentField}`).sort();
    assert.deepEqual(relationSignatures(model).sort(), expected);
    for (const { relation, child, parent, childField } of modelRelations(model)) {
      assert.ok(parent && childField, `Payment 禁止跨库或悬空关系：${child.defKey}`);
      const primary = child.fields.find((field) => field.primaryKey);
      if (child.defKey === "t_payment_attempt" && childField.defKey === "active_action_id") continue; // Same-parent unfinished-action invariant is application-owned.
      if (`${relation.myRows}:${relation.refRows}` === "1:1") {
        const exactUniqueIndexes = child.indexes.filter((index) => index.unique && index.fields.length === 1 && index.fields[0]?.fieldDefKey === childField.id);
        assert.ok(childField.id === primary?.id || exactUniqueIndexes.length === 1, `${child.defKey}.${childField.defKey} 的 1:1 关系必须由主键或恰好一个单字段 UK 保证`);
        continue;
      }
      const indexed = child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id);
      assert.ok(childField.id === primary?.id || indexed, `${child.defKey}.${childField.defKey} 缺少关系子字段左前缀索引`);
    }
});

test("Payment model is Chinese-metadata, credential-free and security constrained", () => {
    assertCoreReady();
    const model = readModel();
    assert.deepEqual(model.dbConn, []); assert.deepEqual(model.dbConns, []); assert.equal(model.profile.default.dbConn, "");
    for (const entity of model.entities) {
      assert.match(entity.defKey, /^t_[a-z0-9_]+$/u); assert.match(entity.defName, /[\u3400-\u9fff]/u); assert.match(entity.comment, /[\u3400-\u9fff]/u);
      for (const index of entity.indexes) assert.match(index.defKey, index.unique ? /^uk_/u : /^idx_/u);
      for (const field of entity.fields) {
        assert.equal(field.autoIncrement, false, `${entity.defKey}.${field.defKey} 不得自增`);
        assert.match(field.defName, /[\u3400-\u9fff]/u); assert.match(field.comment, /[\u3400-\u9fff]/u);
        assert.ok(!forbiddenFieldKeys.includes(field.defKey), `恢复已删除/敏感字段 ${entity.defKey}.${field.defKey}`);
        assert.doesNotMatch(field.defKey, /(?:^|_)(?:pan|cvv|cvc|track(?:_data)?|pin(?:_block)?|card_exp(?:iry|iration)?|password|private_key|jdbc(?:_url)?)(?:_|$)/iu);
        if (field.defKey.endsWith("_hash")) assert.ok(hashFieldWhitelist.has(field.defKey), `未批准 Hash 字段 ${entity.defKey}.${field.defKey}`);
        if (field.defKey === "version") assert.ok(versionFieldWhitelist.has(entity.defKey), `未批准机械 version ${entity.defKey}`);
      }
    }
    const entities = entityMap(model);
    const fieldComment = (tableKey, fieldKey) => entities.get(tableKey).fields.find((field) => field.defKey === fieldKey).comment;
    assert.match(fieldComment("t_mid_capability", "payment_method_code"), /精确/u, "Capability payment_method_code 必须说明使用精确目录值");
    assert.match(fieldComment("t_payment_attempt", "payment_method_code"), /冻结.*支付方式/u, "Attempt payment_method_code 必须说明冻结支付方式");
    assert.match(fieldComment("t_payment_instrument_token", "instrument_ref"), /稳定.*内部记录引用/u, "Token 必须保存稳定 Owner 引用");
    assert.match(fieldComment("t_payment_action", "channel_response_msg"), /不得.*原始报文.*卡数据.*临时交互参数/u);
    for (const fieldKey of ["scheme_code", "country_code", "currency"]) {
      assert.match(fieldComment("t_mid_capability", fieldKey), /(通配符.*\*|精确.*\*)/u, `Capability Scope 字段必须说明允许控制通配符：${fieldKey}`);
    }
});

test("Payment metadata exports exactly cover approved Chinese names, comments and closed/open contracts", async () => {
    assertCoreReady();
    const metadata = await import(`./payment-field-metadata.mjs?contract=${Date.now()}`);
    const expected = expectedMetadataContracts();
    assert.deepEqual(metadata.TABLE_DISPLAY_NAMES, expectedTableDisplayNames);
    assert.deepEqual(metadata.TABLE_COMMENTS, expectedTableComments);
    assert.deepEqual(Object.keys(metadata.FIELD_METADATA).sort(), expected.fields);
    assert.deepEqual(metadata.CLOSED_ENUM_VALUES, expected.closed);
    assert.deepEqual(metadata.OPEN_CODE_SOURCES, expected.open);
    for (const [fieldKey, detail] of Object.entries(metadata.FIELD_METADATA)) {
      assert.match(detail.defName, /[\u3400-\u9fff]/u, `字段元数据缺少中文名：${fieldKey}`);
      assert.match(detail.description, /[\u3400-\u9fff]/u, `字段元数据缺少中文说明：${fieldKey}`);
    }
    const entities = entityMap(readModel());
    for (const [fieldPath, source] of Object.entries(expected.open)) {
      const [tableKey, fieldKey] = fieldPath.split(".");
      const field = entities.get(`t_${tableKey}`).fields.find((candidate) => candidate.defKey === fieldKey);
      assert.ok(field.comment.includes(`取值来源：${source}；非固定枚举。`), `${fieldPath} 未标明精确开放代码来源`);
    }
    for (const table of parseApprovedFields()) for (const field of table.fields) {
      if (!["http_status", "response_http_status"].includes(field.defKey) && /(?:_code|_type|_status|source_type|currency|country_code|industry_code|mcc_code|scheme_code)$/u.test(field.defKey)) {
        assert.ok(field.closedValues || field.openSource, `代码/类型/状态/来源/币种/国家字段未标注闭合或开放：${table.defKey}.${field.defKey}`);
      }
      if (field.type === "TINYINT" && field.len === 1) {
        const detail = metadata.FIELD_METADATA[field.defKey];
        assert.match(detail.description, /0.*1|1.*0/u, `Boolean 字段需说明 0/1：${field.defKey}`);
        if (!field.notNull) assert.match(detail.description, /NULL/u, `可空 Boolean 字段需说明 NULL：${field.defKey}`);
      }
    }
});

test("Payment model is deterministic and preserves nine diagram themes", async () => {
    assertCoreReady();
    const { structuralProjectionDigest: generatorStructuralProjectionDigest } = await import(`./generate-payment-model.mjs?digest=${Date.now()}`);
    const modelBytes = readFileSync(paths.model); const model = JSON.parse(modelBytes);
    assert.equal(sha256(readFileSync(paths.template)), expectedTemplateDigest);
    assert.equal(sha256(modelBytes), expectedModelDigest);
    assert.equal(structuralDigest(model), expectedStructuralDigest);
    assert.equal(generatorStructuralProjectionDigest(model), expectedStructuralDigest);
    assert.deepEqual(model.diagrams.map((diagram) => diagram.defKey), Object.keys(expectedDiagramGroups));
    const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
    const relationIds = model.entities.flatMap((entity) => entity.correlations.map((relation) => relation.id));
    const primaryTables = [];
    const diagramRelationIds = [];
    for (const diagram of model.diagrams) {
      const expected = expectedDiagramGroups[diagram.defKey];
      assert.equal(diagram.defName, expected.defName);
      const tableKeys = diagram.canvasData.cells.filter((cell) => cell.shape === "table").map((cell) => {
        const entity = entitiesById.get(cell.originKey);
        assert.ok(entity, `图 ${diagram.defKey} 含悬空表格单元`);
        return logicalTableKey(entity.defKey);
      });
      assert.deepEqual(tableKeys, [...expected.primary, ...expected.references]);
      primaryTables.push(...expected.primary);
      for (const cell of diagram.canvasData.cells.filter((cell) => cell.shape === "erdRelation")) {
        assert.ok(relationIds.includes(cell.originKey), `图 ${diagram.defKey} 含悬空关系单元 ${cell.originKey}`);
        diagramRelationIds.push(cell.originKey);
      }
    }
    assert.deepEqual(primaryTables.sort(), approvedTableNames.map(logicalTableKey).sort());
    assert.equal(new Set(primaryTables).size, 32);
    assert.deepEqual(diagramRelationIds.sort(), relationIds.sort());
    assert.equal(new Set(diagramRelationIds).size, relationIds.length);
    const result = spawnSync(process.execPath, [paths.generator, "--check"], { cwd: workspaceRoot, encoding: "utf8" });
    assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
    assert.deepEqual(modelTotals(model), currentModelTotals);
});

test("Payment shared domains preserve the approved money, weight, time and currency precision", async () => {
    assertCoreReady();
    const model = readModel();
    const domains = new Map(model.domains.map((domain) => [domain.defKey, domain]));
    assert.deepEqual(
      Object.fromEntries(["Money", "Decimal", "DateTime", "currency"].map((key) => {
        const domain = domains.get(key);
        return [key, { len: domain?.len, scale: domain?.scale }];
      })),
      {
        Money: { len: 24, scale: 8 },
        Decimal: { len: 18, scale: 10 },
        DateTime: { len: 6, scale: "" },
        currency: { len: 3, scale: "" },
      },
    );

    const decimalDomainId = domains.get("Decimal").id;
    for (const tableKey of ["t_routing_policy_mid", "t_route_candidate_eval"]) {
      const field = entityMap(model).get(tableKey).fields.find((candidate) => candidate.defKey === "weight_value");
      assert.equal(field.type, "DECIMAL");
      assert.equal(field.len, 18);
      assert.equal(field.scale, 10);
      assert.equal(field.domain, decimalDomainId, `${tableKey}.weight_value 必须引用 DECIMAL(18,10) 共享 Domain`);
    }

    const changedDomainModel = structuredClone(model);
    changedDomainModel.domains.find((domain) => domain.defKey === "Decimal").scale = 6;
    assert.notEqual(structuralDigest(changedDomainModel), expectedStructuralDigest, "Domain 精度变化必须改变测试独立结构摘要");
    const { structuralProjectionDigest: generatorStructuralProjectionDigest } = await import(`./generate-payment-model.mjs?domain-digest=${Date.now()}`);
    assert.notEqual(generatorStructuralProjectionDigest(changedDomainModel), expectedStructuralDigest, "Domain 精度变化必须改变 Generator 结构摘要");
});
}
