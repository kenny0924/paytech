import { registerServiceModelContract } from "./commerce-service-model-contract.mjs";

// acquiring (mor_acquiring) model contract: transaction / checkout / command tables split from commerce-service in step 03; S10 / C7 removals.
// The oracle below is frozen independently of the generator manifest; a manifest change must update it on purpose.
const ORACLE = String.raw`
[t_commerce_order]
order_id BIGINT NN
order_no VARCHAR(64) NN
client_id BIGINT NN
client_order_id VARCHAR(128) NN
customer_id BIGINT N
creation_mode VARCHAR(32) NN
consumer_country_code CHAR(2) N
termination_requested_at DATETIME(6) N
entity_no CHAR(5) N
entity_source_type VARCHAR(64) N
local_partner_id BIGINT N
industry_code VARCHAR(64) N
mcc_code VARCHAR(4) N
merchandise_amount DECIMAL(24,8) NN
discount_amount DECIMAL(24,8) NN
net_amount DECIMAL(24,8) NN
tax_amount DECIMAL(24,8) N
rounding_amount DECIMAL(24,8) N
total_amount DECIMAL(24,8) N
currency CHAR(3) NN
order_status VARCHAR(64) NN
status_reason_code VARCHAR(64) N
expires_at DATETIME(6) NN
paid_at DATETIME(6) N
cancelled_at DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_order_no(order_no)
UK uk_order_client_order(client_id,client_order_id)
IDX idx_order_client_time(client_id,create_time,order_id)
IDX idx_order_partner_time(local_partner_id,create_time,order_id)
IDX idx_order_expiry(order_status,expires_at,order_id)

[t_order_context]
order_context_id BIGINT NN
order_id BIGINT NN
commercial_context_time DATETIME(6) NN
affiliation_id BIGINT NN
entity_scope_id BIGINT NN
entity_source_relation_id BIGINT NN
client_pricing_version_id BIGINT NN
partner_share_version_id BIGINT N
platform_charge_version_id BIGINT N
platform_service_party_id BIGINT N
risk_checkout_assessment_id BIGINT NN
finance_tax_snapshot_id BIGINT NN
finance_tax_snapshot_hash CHAR(64) NN
consumer_country_code CHAR(2) NN
locale VARCHAR(35) NN
create_time DATETIME(6) NN
UK uk_order_context(order_id)

[t_order_legal]
order_legal_id BIGINT NN
order_context_id BIGINT NN
client_agreement_id BIGINT NN
partner_agreement_id BIGINT N
partner_agreement_operating_entity_id BIGINT N
consumer_terms_document_id BIGINT NN
consumer_terms_file_id BIGINT NN
consumer_terms_content_hash CHAR(64) NN
refund_policy_document_id BIGINT NN
refund_policy_file_id BIGINT NN
refund_policy_content_hash CHAR(64) NN
privacy_notice_document_id BIGINT NN
privacy_notice_file_id BIGINT NN
privacy_notice_content_hash CHAR(64) NN
seller_disclosure_id BIGINT NN
public_legal_name VARCHAR(255) NN
public_registration_number VARCHAR(128) NN
public_tax_registration_number VARCHAR(64) N
public_address_text VARCHAR(512) NN
public_support_email VARCHAR(254) NN
public_support_phone VARCHAR(32) N
create_time DATETIME(6) NN
UK uk_order_legal(order_context_id)

[t_order_item]
order_item_id BIGINT NN
order_id BIGINT NN
client_item_id VARCHAR(128) NN
item_name VARCHAR(255) NN
item_description VARCHAR(1024) N
product_type_code VARCHAR(64) NN
tax_category_code VARCHAR(64) NN
quantity DECIMAL(24,8) NN
unit_amount DECIMAL(24,8) NN
line_gross_amount DECIMAL(24,8) NN
line_discount_amount DECIMAL(24,8) NN
line_net_amount DECIMAL(24,8) NN
currency CHAR(3) NN
sort_no INT NN
create_time DATETIME(6) NN
UK uk_order_item(order_id,client_item_id)

[t_order_payment_projection]
order_payment_projection_id BIGINT NN
order_id BIGINT NN
payment_intent_id BIGINT NN
intent_status VARCHAR(64) NN
intent_source_version BIGINT NN
last_intent_event_key VARCHAR(128) N
last_intent_payload_hash CHAR(64) N
active_attempt_id BIGINT N
last_attempt_id BIGINT N
last_attempt_no INT N
last_attempt_status VARCHAR(64) N
attempt_source_version BIGINT N
last_attempt_event_key VARCHAR(128) N
last_attempt_payload_hash CHAR(64) N
successful_attempt_id BIGINT N
captured_amount DECIMAL(24,8) NN
currency CHAR(3) NN
last_event_time DATETIME(6) NN
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_order_payment(order_id)
UK uk_payment_intent(payment_intent_id)

[t_order_funds_availability_projection]
order_funds_availability_projection_id BIGINT NN
order_id BIGINT NN
finance_economics_id BIGINT N
maturity_journal_id BIGINT N
funds_availability_status VARCHAR(32) NN
available_amount DECIMAL(24,8) N
currency CHAR(3) NN
scheduled_at DATETIME(6) N
available_at DATETIME(6) N
economics_event_key VARCHAR(128) N
economics_source_version BIGINT N
economics_source_payload_hash CHAR(64) N
maturity_event_key VARCHAR(128) N
maturity_source_version BIGINT N
maturity_source_payload_hash CHAR(64) N
as_of DATETIME(6) NN
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_order_funds_availability(order_id)
UK uk_order_funds_economics(finance_economics_id)
UK uk_order_funds_econ_event(economics_event_key)
UK uk_order_funds_maturity_event(maturity_event_key)
UK uk_order_funds_maturity_journal(maturity_journal_id)

[t_order_refund_position]
order_refund_position_id BIGINT NN
order_id BIGINT NN
refundable_base_amount DECIMAL(24,8) NN
reserved_amount DECIMAL(24,8) NN
refunded_amount DECIMAL(24,8) NN
currency CHAR(3) NN
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_order_refund_position(order_id)

[t_checkout_session]
checkout_session_id BIGINT NN
order_id BIGINT NN
public_token CHAR(34) NN
public_origin VARCHAR(255) NN
session_status VARCHAR(64) NN
opened_at DATETIME(6) N
completed_at DATETIME(6) N
cancelled_at DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_checkout_order(order_id)
UK uk_checkout_public_token(public_token)

[t_consumer_context]
consumer_context_id BIGINT NN
order_id BIGINT NN
consumer_reference VARCHAR(128) N
email_vault_token VARCHAR(128) NN
phone_vault_token VARCHAR(128) N
consumer_profile_vault_token VARCHAR(128) NN
email_domain VARCHAR(255) NN
phone_country_code VARCHAR(8) N
billing_country_code CHAR(2) NN
billing_region_code VARCHAR(64) N
billing_city VARCHAR(128) N
billing_postal_prefix VARCHAR(16) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_consumer_context_order(order_id)

[t_consumer_contract]
consumer_contract_id BIGINT NN
order_id BIGINT NN
order_legal_id BIGINT NN
rendered_file_id BIGINT NN
rendered_content_hash CHAR(64) NN
locale VARCHAR(35) NN
presented_at DATETIME(6) NN
create_time DATETIME(6) NN
UK uk_consumer_contract(order_id)
IDX idx_contract_legal(order_legal_id)

[t_checkout_network_evidence]
network_evidence_id BIGINT NN
order_id BIGINT NN
checkout_session_id BIGINT NN
evidence_scene VARCHAR(64) NN
request_id VARCHAR(128) NN
client_ip_address VARCHAR(45) NN
user_agent VARCHAR(1024) NN
trusted_proxy_chain VARCHAR(2048) N
ip_country_code CHAR(2) N
ip_region_code VARCHAR(64) N
ip_city VARCHAR(128) N
ip_asn BIGINT N
network_type VARCHAR(64) N
proxy_flag TINYINT(1) N
vpn_flag TINYINT(1) N
tor_flag TINYINT(1) N
hosting_flag TINYINT(1) N
browser_family VARCHAR(64) N
browser_major_version VARCHAR(32) N
os_family VARCHAR(64) N
os_major_version VARCHAR(32) N
device_type VARCHAR(64) NN
occurred_at DATETIME(6) NN
create_time DATETIME(6) NN
UK uk_network_evidence_request(checkout_session_id,request_id,evidence_scene)
IDX idx_network_order_time(order_id,occurred_at)
IDX idx_network_client_ip(client_ip_address,occurred_at)

[t_consumer_acceptance]
consumer_acceptance_id BIGINT NN
consumer_contract_id BIGINT NN
network_evidence_id BIGINT NN
acceptance_type VARCHAR(64) NN
accepted_at DATETIME(6) NN
command_idempotency_id BIGINT N
create_time DATETIME(6) NN
UK uk_consumer_acceptance(consumer_contract_id,acceptance_type)
IDX idx_acceptance_network(network_evidence_id)

[t_refund_request]
refund_request_id BIGINT NN
refund_request_no VARCHAR(64) NN
order_id BIGINT NN
client_id BIGINT NN
request_origin VARCHAR(64) NN
client_refund_id VARCHAR(128) N
refund_amount DECIMAL(24,8) NN
currency CHAR(3) NN
reason_code VARCHAR(64) NN
reason_summary VARCHAR(512) N
refund_status VARCHAR(64) NN
status_reason_code VARCHAR(64) N
channel_refund_id BIGINT N
completed_at DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_refund_request_no(refund_request_no)
UK uk_refund_client_ref(client_id,client_refund_id)
UK uk_refund_channel(channel_refund_id)
IDX idx_refund_order(order_id,create_time,refund_request_id)
IDX idx_refund_client_time(client_id,create_time,refund_request_id)

[t_compensation_event]
id BIGINT NN
event_key VARCHAR(128) NN
event_type VARCHAR(64) NN
sub_event_type VARCHAR(64) NN
payload_json JSON N
execute_status VARCHAR(64) NN
execute_count INT NN
next_execute_at DATETIME(6) NN
lock_owner VARCHAR(64) N
lock_until DATETIME(6) N
last_error_code VARCHAR(64) N
last_error_summary TEXT N
lock_version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_compensation_event(event_key,event_type,sub_event_type)
IDX idx_compensation_ready(execute_status,next_execute_at,id)
IDX idx_compensation_lease(execute_status,lock_until,id)
`;

registerServiceModelContract({
  service: "acquiring",
  label: "Acquiring",
  database: "mor_acquiring",
  account: "acquiring_app",
  oracle: ORACLE,
  totals: {
    "tables": 14,
    "fields": 227,
    "uniqueIndexes": 23,
    "accessIndexes": 11,
    "relations": 15,
    "diagramGroups": 4
  },
  modelSha256: "eaf2905a124462d42ac676a9144a7ed708b77f91b7696e6630c33537389781dc",
  structuralSha256: "708f517f6d7325e06fda0e66dda6bfbe1d4677a96608bf4752982e7c8012e58c",
  diagrams: [
    "订单与不可变上下文",
    "Checkout 与消费者法律",
    "支付与退款",
    "可靠执行"
  ],
  versionTables: [
    "t_commerce_order",
    "t_order_payment_projection",
    "t_order_funds_availability_projection",
    "t_order_refund_position",
    "t_checkout_session",
    "t_consumer_context",
    "t_refund_request"
  ],
  forbiddenTables: [
    "t_command_idempotency",
    "t_customer",
    "t_client_stored_card_setting"
  ],
  forbiddenFields: [
    "t_commerce_order.context_format",
    "t_commerce_order.preparation_command_id"
  ],
  relations: [
    "t_order_context.order_id -> t_commerce_order.order_id",
    "t_order_legal.order_context_id -> t_order_context.order_context_id",
    "t_order_item.order_id -> t_commerce_order.order_id",
    "t_order_payment_projection.order_id -> t_commerce_order.order_id",
    "t_order_funds_availability_projection.order_id -> t_commerce_order.order_id",
    "t_order_refund_position.order_id -> t_commerce_order.order_id",
    "t_checkout_session.order_id -> t_commerce_order.order_id",
    "t_consumer_context.order_id -> t_commerce_order.order_id",
    "t_consumer_contract.order_id -> t_commerce_order.order_id",
    "t_consumer_contract.order_legal_id -> t_order_legal.order_legal_id",
    "t_checkout_network_evidence.order_id -> t_commerce_order.order_id",
    "t_checkout_network_evidence.checkout_session_id -> t_checkout_session.checkout_session_id",
    "t_consumer_acceptance.consumer_contract_id -> t_consumer_contract.consumer_contract_id",
    "t_consumer_acceptance.network_evidence_id -> t_checkout_network_evidence.network_evidence_id",
    "t_refund_request.order_id -> t_commerce_order.order_id"
  ],
});
