import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const paths = Object.freeze({
  metadata: path.join(workspaceRoot, "scripts/pdmaner/commerce-field-metadata.mjs"),
  generator: path.join(workspaceRoot, "scripts/pdmaner/generate-commerce-model.mjs"),
  model: path.join(workspaceRoot, "commerce-service/docs/database/commerce.pdma.json"),
  decisions: path.join(
    workspaceRoot,
    "commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md",
  ),
  commerceDatabase: path.join(
    workspaceRoot,
    "agent-docs/database/COMMERCE_DATABASE.md",
  ),
  schema: path.join(
    workspaceRoot,
    "commerce-service/docs/database/sql/commerce-schema.sql",
  ),
  initialData: path.join(
    workspaceRoot,
    "commerce-service/docs/database/sql/commerce-initial-data.sql",
  ),
  template: path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json"),
});

const requiredArtifacts = [
  paths.metadata,
  paths.generator,
  paths.decisions,
  paths.commerceDatabase,
  paths.model,
  paths.schema,
  paths.initialData,
];
const artifactsReady = requiredArtifacts.every(existsSync);
const expectedTemplateDigest =
  "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const expectedModelDigest =
  "e5f0320b2806b95e9b584c14fa16443d492f96373a996e86ee7f74aa48f95999";
const expectedStructuralDigest =
  "625f365cdda712936d3c4a2c76b7a5b4c1e26d5a348b6f7643f502030ad0c1b4";
const idPattern =
  /^[A-F0-9]{8}-[A-F0-9]{4}-5[A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/u;

// Test-owned oracle. The generator has its own independently parsed manifest.
const APPROVED_FIELD_MANIFEST = String.raw`
[t_commerce_order]
order_id BIGINT NN
order_no VARCHAR(64) NN
client_id BIGINT NN
client_order_id VARCHAR(128) NN
operating_entity_id BIGINT NN
entity_source_type VARCHAR(64) NN {PLATFORM,CLIENT,PARTNER}
local_partner_id BIGINT N
industry_code VARCHAR(64) NN
mcc_code VARCHAR(4) NN
merchandise_amount DECIMAL(24,8) NN
discount_amount DECIMAL(24,8) NN
net_amount DECIMAL(24,8) NN
tax_amount DECIMAL(24,8) NN
rounding_amount DECIMAL(24,8) NN
total_amount DECIMAL(24,8) NN
currency CHAR(3) NN
order_status VARCHAR(64) NN {CREATED,OPEN,PAID,CANCELLED,EXPIRED}
status_reason_code VARCHAR(64) N OPEN(Order Status Reason Catalog)
expires_at DATETIME(6) NN
paid_at DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_order_context]
order_context_id BIGINT NN
order_id BIGINT NN
commercial_context_time DATETIME(6) NN
assignment_id BIGINT NN
cct_commercial_context_hash CHAR(64) NN
client_pricing_scope_id BIGINT NN
partner_share_scope_id BIGINT N
platform_service_charge_scope_id BIGINT N
platform_service_party_id BIGINT N
risk_checkout_assessment_id BIGINT NN
finance_tax_snapshot_id BIGINT NN
finance_tax_snapshot_hash CHAR(64) NN
consumer_country_code CHAR(2) NN
locale VARCHAR(35) NN
create_time DATETIME(6) NN

[t_order_legal]
order_legal_id BIGINT NN
order_context_id BIGINT NN
client_agreement_operating_entity_id BIGINT NN
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

[t_order_item]
order_item_id BIGINT NN
order_id BIGINT NN
client_item_id VARCHAR(128) NN
item_name VARCHAR(255) NN
item_description VARCHAR(1024) N
product_type_code VARCHAR(64) NN OPEN(Product Type Catalog)
tax_category_code VARCHAR(64) NN OPEN(Tax Category Catalog)
quantity DECIMAL(24,8) NN
unit_amount DECIMAL(24,8) NN
line_gross_amount DECIMAL(24,8) NN
line_discount_amount DECIMAL(24,8) NN
line_net_amount DECIMAL(24,8) NN
line_tax_amount DECIMAL(24,8) NN
line_total_amount DECIMAL(24,8) NN
currency CHAR(3) NN
sort_no INT NN
create_time DATETIME(6) NN

[t_order_payment_projection]
order_payment_projection_id BIGINT NN
order_id BIGINT NN
payment_intent_id BIGINT NN
intent_status VARCHAR(64) NN {REQUIRES_PAYMENT,PROCESSING,REQUIRES_ACTION,SUCCEEDED,FAILED,EXPIRED,CANCELLED}
intent_source_version BIGINT NN
last_intent_event_key VARCHAR(128) N
last_intent_payload_hash CHAR(64) N
active_attempt_id BIGINT N
last_attempt_id BIGINT N
last_attempt_no INT N
last_attempt_status VARCHAR(64) N {CREATED,RISK_CHECKING,RISK_REJECTED,RISK_FAILED,LIMIT_REJECTED,ROUTING,PROCESSING,REQUIRES_ACTION,UNKNOWN,SUCCEEDED,FAILED,SUCCESS_CONFLICT}
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

[t_order_funds_availability_projection]
order_funds_availability_projection_id BIGINT NN
order_id BIGINT NN
finance_economics_id BIGINT N
maturity_journal_id BIGINT N
funds_availability_status VARCHAR(32) NN {NOT_APPLICABLE,PENDING,AVAILABLE,FULLY_OFFSET}
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

[t_checkout_session]
checkout_session_id BIGINT NN
order_id BIGINT NN
public_token CHAR(34) NN
public_origin VARCHAR(255) NN
session_status VARCHAR(64) NN {PREPARING,OPEN,PAYMENT_PROCESSING,COMPLETED,CANCELLED,EXPIRED}
opened_at DATETIME(6) N
completed_at DATETIME(6) N
cancelled_at DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_consumer_context]
consumer_context_id BIGINT NN
order_id BIGINT NN
consumer_reference VARCHAR(128) NN
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

[t_consumer_contract]
consumer_contract_id BIGINT NN
order_id BIGINT NN
order_legal_id BIGINT NN
rendered_file_id BIGINT NN
rendered_content_hash CHAR(64) NN
locale VARCHAR(35) NN
presented_at DATETIME(6) NN
create_time DATETIME(6) NN

[t_checkout_network_evidence]
network_evidence_id BIGINT NN
order_id BIGINT NN
checkout_session_id BIGINT NN
evidence_scene VARCHAR(64) NN {CHECKOUT_OPEN,CONSUMER_CONTEXT_SUBMIT,LEGAL_ACCEPTANCE,PAYMENT_START}
request_id VARCHAR(128) NN
client_ip_address VARCHAR(45) NN
user_agent VARCHAR(1024) NN
trusted_proxy_chain VARCHAR(2048) N
ip_country_code CHAR(2) N
ip_region_code VARCHAR(64) N
ip_city VARCHAR(128) N
ip_asn BIGINT N
network_type VARCHAR(64) N OPEN(Network Type Catalog)
proxy_flag TINYINT(1) N
vpn_flag TINYINT(1) N
tor_flag TINYINT(1) N
hosting_flag TINYINT(1) N
browser_family VARCHAR(64) N
browser_major_version VARCHAR(32) N
os_family VARCHAR(64) N
os_major_version VARCHAR(32) N
device_type VARCHAR(64) NN {DESKTOP,MOBILE,TABLET,BOT,OTHER,UNKNOWN}
occurred_at DATETIME(6) NN
create_time DATETIME(6) NN

[t_consumer_acceptance]
consumer_acceptance_id BIGINT NN
consumer_contract_id BIGINT NN
network_evidence_id BIGINT NN
acceptance_type VARCHAR(64) NN {CONSUMER_TERMS,REFUND_POLICY,PRIVACY_NOTICE}
accepted_at DATETIME(6) NN
command_idempotency_id BIGINT NN
create_time DATETIME(6) NN

[t_refund_request]
refund_request_id BIGINT NN
refund_request_no VARCHAR(64) NN
order_id BIGINT NN
client_id BIGINT NN
request_origin VARCHAR(64) NN {CLIENT,OPERATIONS}
client_refund_id VARCHAR(128) N
refund_amount DECIMAL(24,8) NN
currency CHAR(3) NN
reason_code VARCHAR(64) NN OPEN
reason_summary VARCHAR(512) N
refund_status VARCHAR(64) NN {PENDING,PROCESSING,SUCCEEDED,FAILED,CANCELLED}
status_reason_code VARCHAR(64) N OPEN(Refund Status Reason Catalog)
channel_refund_id BIGINT N
completed_at DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN

[t_command_idempotency]
command_idempotency_id BIGINT NN
caller_scope VARCHAR(128) NN
operation_type VARCHAR(64) NN OPEN(Commerce Command Operation Catalog)
idempotency_key VARCHAR(255) NN
request_hash CHAR(64) NN
request_hash_version INT NN
request_snapshot_schema_version INT N
request_snapshot_json JSON N
operation_context_time DATETIME(6) NN
recovery_stage VARCHAR(64) N {CLAIMED,ORDER_COMMITTED,PAYMENT_PENDING,REFUND_COMMITTED,PAYMENT_REFUND_BOUND,COMPLETED}
process_status VARCHAR(64) NN {PROCESSING,SUCCEEDED,FAILED}
resource_type VARCHAR(64) N OPEN
resource_id BIGINT N
resource_no VARCHAR(64) N
response_code VARCHAR(64) N OPEN
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
event_type VARCHAR(64) NN OPEN(Commerce Event/Handler Catalog)
sub_event_type VARCHAR(64) NN
payload_json JSON N
execute_status VARCHAR(64) NN {INIT,PROCESSING,ERROR,FAILED}
execute_count INT NN
next_execute_at DATETIME(6) NN
lock_owner VARCHAR(64) N
lock_until DATETIME(6) N
last_error_code VARCHAR(64) N
last_error_summary TEXT N
lock_version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
`;

const APPROVED_INDEX_MANIFEST = String.raw`
t_commerce_order
UK uk_order_no(order_no)
UK uk_order_client_order(client_id,client_order_id)
IDX idx_order_client_time(client_id,create_time,order_id)
IDX idx_order_partner_time(local_partner_id,create_time,order_id)
IDX idx_order_expiry(order_status,expires_at,order_id)
t_order_context
UK uk_order_context(order_id)
t_order_legal
UK uk_order_legal(order_context_id)
t_order_item
UK uk_order_item(order_id,client_item_id)
t_order_payment_projection
UK uk_order_payment(order_id)
UK uk_payment_intent(payment_intent_id)
t_order_funds_availability_projection
UK uk_order_funds_availability(order_id)
UK uk_order_funds_economics(finance_economics_id)
UK uk_order_funds_econ_event(economics_event_key)
UK uk_order_funds_maturity_event(maturity_event_key)
UK uk_order_funds_maturity_journal(maturity_journal_id)
t_order_refund_position
UK uk_order_refund_position(order_id)
t_checkout_session
UK uk_checkout_order(order_id)
UK uk_checkout_public_token(public_token)
t_consumer_context
UK uk_consumer_context_order(order_id)
t_consumer_contract
UK uk_consumer_contract(order_id)
IDX idx_contract_legal(order_legal_id)
t_checkout_network_evidence
UK uk_network_evidence_request(checkout_session_id,request_id,evidence_scene)
IDX idx_network_order_time(order_id,occurred_at)
IDX idx_network_client_ip(client_ip_address,occurred_at)
t_consumer_acceptance
UK uk_consumer_acceptance(consumer_contract_id,acceptance_type)
IDX idx_acceptance_network(network_evidence_id)
t_refund_request
UK uk_refund_request_no(refund_request_no)
UK uk_refund_client_ref(client_id,client_refund_id)
UK uk_refund_channel(channel_refund_id)
IDX idx_refund_order(order_id,create_time,refund_request_id)
IDX idx_refund_client_time(client_id,create_time,refund_request_id)
t_command_idempotency
UK uk_command_idempotency(caller_scope,operation_type,idempotency_key)
IDX idx_command_recovery(process_status,lock_until,command_idempotency_id)
t_compensation_event
UK uk_compensation_event(event_key,event_type,sub_event_type)
IDX idx_compensation_ready(execute_status,next_execute_at,id)
IDX idx_compensation_lease(execute_status,lock_until,id)
`;

const APPROVED_RELATION_MANIFEST = String.raw`
1:1 order_context.order_id -> commerce_order.order_id
1:1 order_legal.order_context_id -> order_context.order_context_id
n:1 order_item.order_id -> commerce_order.order_id
1:1 order_payment_projection.order_id -> commerce_order.order_id
1:1 order_funds_availability_projection.order_id -> commerce_order.order_id
1:1 order_refund_position.order_id -> commerce_order.order_id
1:1 checkout_session.order_id -> commerce_order.order_id
1:1 consumer_context.order_id -> commerce_order.order_id
1:1 consumer_contract.order_id -> commerce_order.order_id
1:1 consumer_contract.order_legal_id -> order_legal.order_legal_id
n:1 checkout_network_evidence.order_id -> commerce_order.order_id
n:1 checkout_network_evidence.checkout_session_id -> checkout_session.checkout_session_id
n:1 consumer_acceptance.consumer_contract_id -> consumer_contract.consumer_contract_id
n:1 consumer_acceptance.network_evidence_id -> checkout_network_evidence.network_evidence_id
n:1 refund_request.order_id -> commerce_order.order_id
`;

const expectedTableOrder = Object.freeze([
  "t_commerce_order",
  "t_order_context",
  "t_order_legal",
  "t_order_item",
  "t_order_payment_projection",
  "t_order_funds_availability_projection",
  "t_order_refund_position",
  "t_checkout_session",
  "t_consumer_context",
  "t_consumer_contract",
  "t_checkout_network_evidence",
  "t_consumer_acceptance",
  "t_refund_request",
  "t_command_idempotency",
  "t_compensation_event",
]);

const expectedTableDisplayNames = Object.freeze({
  commerce_order: "订单",
  order_context: "订单不可变上下文",
  order_legal: "订单法律上下文",
  order_item: "订单商品服务明细",
  order_payment_projection: "订单支付投影",
  order_funds_availability_projection: "订单资金可用性投影",
  order_refund_position: "订单可退款位置",
  checkout_session: "托管收银台会话",
  consumer_context: "消费者当前上下文",
  consumer_contract: "消费者合同呈现",
  checkout_network_evidence: "收银台网络证据",
  consumer_acceptance: "消费者接受证据",
  refund_request: "退款请求",
  command_idempotency: "命令幂等记录",
  compensation_event: "本地补偿事件",
});

const versionTables = Object.freeze([
  "t_commerce_order",
  "t_order_payment_projection",
  "t_order_funds_availability_projection",
  "t_order_refund_position",
  "t_checkout_session",
  "t_consumer_context",
  "t_refund_request",
  "t_command_idempotency",
]);

const immutableTables = Object.freeze([
  "t_order_context",
  "t_order_legal",
  "t_order_item",
  "t_consumer_contract",
  "t_checkout_network_evidence",
  "t_consumer_acceptance",
]);

const forbiddenTables = Object.freeze([
  "t_order_status_history",
  "t_checkout_status_history",
  "t_fulfillment",
  "t_fulfillment_confirmation",
  "t_fulfillment_evidence",
  "t_fulfillment_status_history",
  "t_refund_status_history",
  "t_acceptance_state_change",
  "t_consumer_acceptance_summary",
  "t_fulfill_callback_snapshot",
  "t_order_amount_snapshot",
  "t_order_seller_snapshot",
  "t_order_contract_snapshot",
  "t_order_tax_line",
  "t_order_commercial_term",
  "t_order_term_component",
  "t_order_snapshot",
  "t_refund_reservation",
  "t_refund_reserve_change",
  "t_refund_payment_projection",
  "t_consumer_document",
  "t_business_change_record",
  "t_business_change_version",
]);

const forbiddenFieldKeys = Object.freeze([
  "snapshot_version",
  "user_agent_blind_index",
  "user_agent_blind_index_key_version",
  "vault_record_version",
  "vault_metadata_hash",
  "ref_masked_value",
  "email_masked_value",
  "phone_masked_value",
  "billing_name_masked_value",
  "billing_addr_masked_value",
  "network_evidence_vault_token",
  "commerce_held_at",
  "finance_held_at",
  "commerce_released_at",
  "finance_released_at",
  "commerce_consumed_at",
  "finance_consumed_at",
  "endpoint_id",
  "endpoint_version_id",
  "subscription_id",
  "webhook_secret",
  "eligibility_evaluation_id",
  "assignment_version",
  "assignment_scope_hash",
  "assignment_revision",
  "business_profile_id",
  "business_profile_version_no",
  "business_profile_content_hash",
  "business_classification_id",
  "assignment_rule_id",
  "assignment_affiliation_id",
  "entity_source_relation_id",
  "risk_checkout_outcome",
  "risk_checkout_reason_hash",
  "client_agreement_id",
  "partner_agreement_id",
  "seller_country_code",
  "seller_locale",
  "external_order_id",
  "creation_request_hash",
  "order_snapshot_id",
  "snapshot_schema_version",
  "snapshot_hash",
  "cct_context_as_of_time",
  "finance_entity_id",
  "fulfilled_at",
  "consumer_pii_vault_token",
  "ref_blind_index",
  "ref_blind_index_key_version",
  "email_blind_index",
  "email_blind_index_key_version",
  "phone_blind_index",
  "phone_blind_index_key_version",
  "raw_evidence_vault_token",
  "ip_blind_index",
  "ip_blind_index_key_version",
  "ip_network_blind_index",
  "ip_network_blind_key_ver",
  "device_blind_index",
  "device_blind_index_key_version",
  "origin_scope_key",
  "origin_request_ref",
  "requested_amount",
  "pending_terminal_status",
  "commerce_reserve_status",
  "finance_reserve_status",
  "finance_source_version",
  "finance_event_key",
  "finance_payload_hash",
  "channel_refund_attempt_id",
  "channel_status",
  "projection_version",
]);

const expectedDiagramGroups = Object.freeze({
  order_context: Object.freeze({
    defName: "订单与不可变上下文",
    primary: Object.freeze([
      "commerce_order",
      "order_context",
      "order_legal",
      "order_item",
    ]),
    references: Object.freeze([]),
  }),
  checkout_consumer: Object.freeze({
    defName: "Checkout 与消费者法律",
    primary: Object.freeze([
      "checkout_session",
      "consumer_context",
      "consumer_contract",
      "checkout_network_evidence",
      "consumer_acceptance",
    ]),
    references: Object.freeze(["commerce_order", "order_legal"]),
  }),
  payment_refund_position: Object.freeze({
    defName: "支付与退款",
    primary: Object.freeze([
      "order_payment_projection",
      "order_funds_availability_projection",
      "order_refund_position",
      "refund_request",
    ]),
    references: Object.freeze(["commerce_order"]),
  }),
  reliability: Object.freeze({
    defName: "可靠执行",
    primary: Object.freeze([
      "command_idempotency",
      "compensation_event",
    ]),
    references: Object.freeze([]),
  }),
});

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function logicalTableKey(physicalTableKey) {
  return physicalTableKey.replace(/^t_/u, "");
}

function parseType(typeToken) {
  const match = typeToken.match(/^([A-Z]+)(?:\((\d+)(?:,(\d+))?\))?$/u);
  assert.ok(match, `无法解析物理类型：${typeToken}`);
  return {
    type: match[1],
    len: match[2] === undefined ? "" : Number(match[2]),
    scale: match[3] === undefined ? "" : Number(match[3]),
  };
}

function parseApprovedFieldManifest() {
  const section = APPROVED_FIELD_MANIFEST;
  const headings = [...section.matchAll(/^\[(t_[a-z0-9_]+)\]$/gmu)];
  const tables = [];
  for (const [index, heading] of headings.entries()) {
    const end = headings[index + 1]?.index ?? section.length;
    const tableSection = section.slice(heading.index, end);
    const fields = tableSection.slice(heading[0].length)
      .split(/\r?\n/u)
      .map((line) => line.trim())
      .filter((line) => /^[a-z][a-z0-9_]*\s+/u.test(line))
      .map((line, position) => {
        const fieldMatch = line.match(
          /^([a-z][a-z0-9_]*)\s+([A-Z]+(?:\(\d+(?:,\d+)?\))?)\s*(.*)$/u,
        );
        assert.ok(fieldMatch, `无法解析字段 Manifest：${heading[1]}.${line}`);
        const [, defKey, typeToken, annotations] = fieldMatch;
        const explicitlyNullable = /(?:^|\s)N(?:\s|$)/u.test(annotations);
        const explicitlyNotNull = /(?:^|\s)NN(?:\s|$)/u.test(annotations);
        assert.ok(
          explicitlyNullable || explicitlyNotNull,
          `${heading[1]}.${defKey} 未声明 Nullability`,
        );
        const enumMatch = annotations.match(/\{([^}]+)\}/u);
        const openMatch = annotations.match(/(?:^|\s)OPEN(?:\(([^)]+)\))?/u);
        return {
          defKey,
          ...parseType(typeToken),
          primaryKey: position === 0,
          notNull: explicitlyNotNull,
          closedValues: enumMatch?.[1].split(",") ?? null,
          openSourceHint: openMatch?.[1] ?? (openMatch ? "APPROVED_CATALOG" : null),
        };
      });
    tables.push({ defKey: heading[1], fields });
  }
  return tables;
}

function parseApprovedIndexes() {
  const result = new Map();
  let tableKey;
  for (const rawLine of APPROVED_INDEX_MANIFEST.split(/\r?\n/u)) {
    const line = rawLine.trim();
    if (/^t_[a-z0-9_]+$/u.test(line)) {
      tableKey = line;
      result.set(tableKey, []);
      continue;
    }
    const match = line.match(/^(UK|IDX)\s+(\w+)\(([^)]+)\)$/u);
    if (!match) continue;
    assert.ok(tableKey, `索引 ${match[2]} 没有所属表`);
    result.get(tableKey).push({
      defKey: match[2],
      unique: match[1] === "UK",
      fields: match[3].split(",").map((field) => field.trim()),
    });
  }
  return result;
}

function parseApprovedRelations() {
  return APPROVED_RELATION_MANIFEST
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => /^(?:1:1|n:1)\s/u.test(line))
    .map((line) => {
      const match = line.match(
        /^(1:1|n:1)\s+([a-z0-9_]+)\.([a-z0-9_]+) -> ([a-z0-9_]+)\.([a-z0-9_]+)/u,
      );
      assert.ok(match, `无法解析本库关系：${line}`);
      return {
        cardinality: match[1],
        childTable: match[2],
        childField: match[3],
        parentTable: match[4],
        parentField: match[5],
      };
    });
}

function readModel() {
  return JSON.parse(readFileSync(paths.model, "utf8"));
}

function entityMap(model) {
  return new Map(model.entities.map((entity) => [entity.defKey, entity]));
}

function fieldById(entity) {
  return new Map(entity.fields.map((field) => [field.id, field]));
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

function relationSignatures(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return model.entities.flatMap((child) => {
    const childFields = fieldById(child);
    return child.correlations.map((relation) => {
      const parent = entitiesById.get(relation.refEntity);
      const parentFields = fieldById(parent);
      return [
        `${relation.myRows}:1`,
        `${logicalTableKey(child.defKey)}.${childFields.get(relation.myField).defKey}`,
        `${logicalTableKey(parent.defKey)}.${parentFields.get(relation.refField).defKey}`,
      ].join("|");
    });
  });
}

function expectedRelationSignatures() {
  return parseApprovedRelations().map((relation) => [
    relation.cardinality,
    `${relation.childTable}.${relation.childField}`,
    `${relation.parentTable}.${relation.parentField}`,
  ].join("|"));
}

function structuralProjection(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
    entities: model.entities.map((entity) => {
      const fields = fieldById(entity);
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
          return {
            childField: fields.get(relation.myField).defKey,
            parentTable: parent.defKey,
            parentField: fieldById(parent).get(relation.refField).defKey,
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

function structuralDigest(model) {
  return sha256(JSON.stringify(structuralProjection(model)));
}

function readPhysicalManifest() {
  const source = readFileSync(paths.decisions, "utf8");
  const match = source.match(
    /<!-- COMMERCE_PHYSICAL_MANIFEST_START -->\s*```json\s*([\s\S]*?)\s*```\s*<!-- COMMERCE_PHYSICAL_MANIFEST_END -->/u,
  );
  assert.ok(match, "物理决策缺少可机器校验的冻结 Manifest");
  return JSON.parse(match[1]);
}

function sqlUnquote(value) {
  return value.replace(/''/gu, "'");
}

function parseIdentifierList(source) {
  const matches = [...source.matchAll(/`([^`]+)`/gu)].map((match) => match[1]);
  assert.ok(matches.length > 0, `无法解析 SQL 标识列表：${source}`);
  return matches;
}

function parseManualSchema(source) {
  const tables = [];
  const tablePattern = /CREATE TABLE `([^`]+)` \(\n([\s\S]*?)\n\) ENGINE=InnoDB DEFAULT CHARACTER SET ([a-z0-9_]+) COLLATE ([a-z0-9_]+) COMMENT='((?:''|[^'])*)';/gu;
  for (const tableMatch of source.matchAll(tablePattern)) {
    const [, defKey, body, characterSet, collation, rawComment] = tableMatch;
    const columns = [];
    const indexes = [];
    let primaryKey = [];
    for (const rawLine of body.split("\n")) {
      const line = rawLine.trim().replace(/,$/u, "");
      if (line.startsWith("`")) {
        const columnMatch = line.match(
          /^`([^`]+)` ([A-Z]+)(?:\((\d+)(?:,(\d+))?\))?(?: CHARACTER SET ([a-z0-9_]+) COLLATE ([a-z0-9_]+))? (NOT NULL|NULL)(?: DEFAULT ('(?:''|[^'])*'|[A-Z_]+(?:\(\d+\))?|-?\d+))? COMMENT '((?:''|[^'])*)'$/u,
        );
        assert.ok(columnMatch, `无法解析 ${defKey} 列：${line}`);
        columns.push({
          defKey: columnMatch[1],
          type: columnMatch[2],
          len: columnMatch[3] === undefined ? "" : Number(columnMatch[3]),
          scale: columnMatch[4] === undefined ? "" : Number(columnMatch[4]),
          characterSet: columnMatch[5] ?? "",
          collation: columnMatch[6] ?? "",
          notNull: columnMatch[7] === "NOT NULL",
          defaultValue: columnMatch[8] ?? "",
          comment: sqlUnquote(columnMatch[9]),
        });
        continue;
      }
      const primaryMatch = line.match(/^PRIMARY KEY \((.+)\)$/u);
      if (primaryMatch) {
        primaryKey = parseIdentifierList(primaryMatch[1]);
        continue;
      }
      const indexMatch = line.match(
        /^(UNIQUE KEY|KEY) `([^`]+)` \(([^)]+)\) COMMENT '((?:''|[^'])*)'$/u,
      );
      assert.ok(indexMatch, `无法解析 ${defKey} 约束或索引：${line}`);
      indexes.push({
        defKey: indexMatch[2],
        unique: indexMatch[1] === "UNIQUE KEY",
        fields: parseIdentifierList(indexMatch[3]),
        comment: sqlUnquote(indexMatch[4]),
      });
    }
    tables.push({
      defKey,
      characterSet,
      collation,
      comment: sqlUnquote(rawComment),
      columns,
      primaryKey,
      indexes,
    });
  }
  return tables;
}

function artifactTest(name, fn) {
  test(name, { skip: !artifactsReady }, fn);
}

test("Commerce PDManer contract has every authorized design artifact", () => {
  const missingArtifacts = requiredArtifacts
    .filter((artifactPath) => !existsSync(artifactPath))
    .map((artifactPath) => path.relative(workspaceRoot, artifactPath));
  assert.deepEqual(
    missingArtifacts,
    [],
    `Commerce PDManer artifacts are missing: ${missingArtifacts.join(", ")}`,
  );
});

artifactTest("refund aggregate keeps only Commerce-owned state and stable downstream references", () => {
  const model = readModel();
  const entities = entityMap(model);

  for (const removedTable of [
    "t_refund_reservation",
    "t_refund_reserve_change",
    "t_refund_payment_projection",
  ]) {
    assert.ok(!entities.has(removedTable), `${removedTable} 不应继续复制下游状态`);
  }

  const refundRequest = entities.get("t_refund_request");
  assert.deepEqual(refundRequest.fields.map((field) => field.defKey), [
    "refund_request_id",
    "refund_request_no",
    "order_id",
    "client_id",
    "request_origin",
    "client_refund_id",
    "refund_amount",
    "currency",
    "reason_code",
    "reason_summary",
    "refund_status",
    "status_reason_code",
    "channel_refund_id",
    "completed_at",
    "version",
    "create_time",
    "update_time",
  ]);
  assert.deepEqual(
    refundRequest.fields.find((field) => field.defKey === "refund_status").comment,
    "退款请求当前的闭合业务生命周期状态。允许值：PENDING、PROCESSING、SUCCEEDED、FAILED、CANCELLED。",
  );
  assert.equal(
    refundRequest.fields.find((field) => field.defKey === "client_refund_id").notNull,
    false,
  );
});

artifactTest("refund request delegates directly to Payment without a Finance reservation", () => {
  const refundRequest = entityMap(readModel()).get("t_refund_request");
  const fieldKeys = refundRequest.fields.map((field) => field.defKey);
  const indexKeys = refundRequest.indexes.map((index) => index.defKey);

  assert.ok(!fieldKeys.includes("refund_funding_reservation_id"));
  assert.ok(!indexKeys.includes("uk_refund_funding"));
  assert.ok(fieldKeys.includes("channel_refund_id"));
});

test("approved field manifest independently sums to the real 246-field candidate", () => {
  const approvedTables = parseApprovedFieldManifest();
  assert.deepEqual(approvedTables.map((table) => table.defKey), expectedTableOrder);
  assert.equal(
    approvedTables.flatMap((table) => table.fields).length,
    246,
    "批准候选字段清单实算必须为 246；不得按旧人工摘要凑数",
  );
});

artifactTest("model has the exact ordered table and field contract", () => {
  const model = readModel();
  const approvedTables = parseApprovedFieldManifest();
  assert.deepEqual(model.entities.map((entity) => entity.defKey), expectedTableOrder);
  for (const expectedTable of approvedTables) {
    const actual = entityMap(model).get(expectedTable.defKey);
    assert.ok(actual, `缺少 ${expectedTable.defKey}`);
    assert.deepEqual(
      actual.fields.map((field) => ({
        defKey: field.defKey,
        type: field.type,
        len: field.len,
        scale: field.scale,
        primaryKey: field.primaryKey,
        notNull: field.notNull,
      })),
      expectedTable.fields.map((field) => ({
        defKey: field.defKey,
        type: field.type,
        len: field.len,
        scale: field.scale,
        primaryKey: field.primaryKey,
        notNull: field.notNull,
      })),
      `${expectedTable.defKey} 字段顺序、类型或 Nullability 偏离批准 Manifest`,
    );
  }
});

artifactTest("model freezes the exact 24 UK and 12 IDX field orders", () => {
  const model = readModel();
  const expectedIndexes = parseApprovedIndexes();
  for (const entity of model.entities) {
    const fields = fieldById(entity);
    const actual = entity.indexes.map((index) => ({
      defKey: index.defKey,
      unique: index.unique,
      fields: index.fields.map((entry) => fields.get(entry.fieldDefKey).defKey),
    }));
    assert.deepEqual(actual, expectedIndexes.get(entity.defKey) ?? [], entity.defKey);
  }
  const totals = modelTotals(model);
  assert.equal(totals.uniqueIndexes, 24);
  assert.equal(totals.accessIndexes, 12);
});

artifactTest("compensation event matches the Foundation host defaults and access paths", () => {
  const compensation = entityMap(readModel()).get("t_compensation_event");
  const fields = new Map(compensation.fields.map((field) => [field.defKey, field]));
  const fieldsById = fieldById(compensation);
  assert.deepEqual(
    Object.fromEntries([
      "execute_status",
      "execute_count",
      "next_execute_at",
      "lock_version",
      "create_time",
      "update_time",
    ].map((fieldKey) => [fieldKey, fields.get(fieldKey).defaultValue])),
    {
      execute_status: "'INIT'",
      execute_count: "0",
      next_execute_at: "CURRENT_TIMESTAMP(6)",
      lock_version: "1",
      create_time: "CURRENT_TIMESTAMP(6)",
      update_time: "CURRENT_TIMESTAMP(6)",
    },
  );
  assert.equal(
    fields.get("payload_json").comment,
    "补偿事件执行所需的最小非敏感结构化载荷，不超过 4 KiB UTF-8 JSON Object，且不得包含 Vault Token。",
  );
  assert.deepEqual(
    compensation.indexes.map((index) => ({
      defKey: index.defKey,
      unique: index.unique,
      fields: index.fields.map((entry) => fieldsById.get(entry.fieldDefKey).defKey),
    })),
    [
      { defKey: "uk_compensation_event", unique: true, fields: ["event_key", "event_type", "sub_event_type"] },
      { defKey: "idx_compensation_ready", unique: false, fields: ["execute_status", "next_execute_at", "id"] },
      { defKey: "idx_compensation_lease", unique: false, fields: ["execute_status", "lock_until", "id"] },
    ],
  );
});

artifactTest("command recovery enums and bounded snapshots are frozen in field comments", () => {
  const command = entityMap(readModel()).get("t_command_idempotency");
  const fields = new Map(command.fields.map((field) => [field.defKey, field]));
  assert.match(fields.get("process_status").comment, /允许值：PROCESSING、SUCCEEDED、FAILED。/u);
  assert.match(
    fields.get("recovery_stage").comment,
    /允许值：CLAIMED、ORDER_COMMITTED、PAYMENT_PENDING、REFUND_COMMITTED、PAYMENT_REFUND_BOUND、COMPLETED。/u,
  );
  assert.match(fields.get("request_snapshot_json").comment, /最多 16 KiB UTF-8/u);
  assert.match(fields.get("response_body_json").comment, /最多 4 KiB UTF-8/u);
  assert.match(fields.get("response_body_json").comment, /Checkout URL.*访问能力.*原命令恢复/u);
  assert.match(fields.get("response_body_json").comment, /普通日志.*MQ.*Webhook.*通用审计/u);
});

artifactTest("model has exactly 15 local indexed relations and no cross-system FK", () => {
  const model = readModel();
  assert.deepEqual(
    relationSignatures(model).sort(),
    expectedRelationSignatures().sort(),
  );
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  for (const child of model.entities) {
    const childFields = fieldById(child);
    const primary = child.fields.find((field) => field.primaryKey);
    for (const relation of child.correlations) {
      assert.ok(entitiesById.has(relation.refEntity), `${child.defKey} 出现跨模型关系`);
      assert.equal(relation.refRows, "1");
      assert.equal(relation.innerType, "");
      const childField = childFields.get(relation.myField);
      const leftPrefixCovered = childField.id === primary.id || child.indexes.some(
        (index) => index.fields[0]?.fieldDefKey === childField.id,
      );
      assert.ok(leftPrefixCovered, `${child.defKey}.${childField.defKey} 缺少左前缀索引`);
    }
  }
});

artifactTest("four diagrams cover each primary table and each relation exactly once", () => {
  const model = readModel();
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  assert.deepEqual(model.diagrams.map((diagram) => diagram.defKey), Object.keys(expectedDiagramGroups));
  const primaryTables = [];
  let relationCells = 0;
  for (const diagram of model.diagrams) {
    const expected = expectedDiagramGroups[diagram.defKey];
    assert.equal(diagram.defName, expected.defName);
    const tableKeys = diagram.canvasData.cells
      .filter((cell) => cell.shape === "table")
      .map((cell) => logicalTableKey(entitiesById.get(cell.originKey).defKey));
    assert.deepEqual(tableKeys, [...expected.primary, ...expected.references]);
    relationCells += diagram.canvasData.cells.filter(
      (cell) => cell.shape === "erdRelation",
    ).length;
    primaryTables.push(...expected.primary);
  }
  assert.deepEqual(primaryTables.sort(), expectedTableOrder.map(logicalTableKey).sort());
  assert.equal(new Set(primaryTables).size, 15);
  assert.equal(relationCells, 15);
});

artifactTest("metadata exactly covers the catalog and annotates closed/open values", async () => {
  const metadata = await import(`./commerce-field-metadata.mjs?contract=${Date.now()}`);
  const approvedTables = parseApprovedFieldManifest();
  const expectedFieldKeys = [...new Set(
    approvedTables.flatMap((table) => table.fields.map((field) => field.defKey)),
  )].sort();
  assert.deepEqual(Object.keys(metadata.TABLE_DISPLAY_NAMES).sort(), expectedTableOrder.map(logicalTableKey).sort());
  assert.deepEqual(metadata.TABLE_DISPLAY_NAMES, expectedTableDisplayNames);
  assert.deepEqual(Object.keys(metadata.FIELD_METADATA).sort(), expectedFieldKeys);

  const expectedClosed = Object.fromEntries(
    approvedTables.flatMap((table) => table.fields
      .filter((field) => field.closedValues)
      .map((field) => [
        `${logicalTableKey(table.defKey)}.${field.defKey}`,
        field.closedValues,
      ])),
  );
  const expectedOpenPaths = approvedTables.flatMap((table) => table.fields
    .filter((field) => field.openSourceHint)
    .map((field) => `${logicalTableKey(table.defKey)}.${field.defKey}`));
  assert.deepEqual(metadata.CLOSED_ENUM_VALUES, expectedClosed);
  assert.deepEqual(Object.keys(metadata.OPEN_CODE_SOURCES).sort(), expectedOpenPaths.sort());
  assert.ok(Object.values(metadata.OPEN_CODE_SOURCES).every((source) => source.length > 8));

  const model = readModel();
  for (const table of approvedTables) {
    const entity = entityMap(model).get(table.defKey);
    assert.match(entity.defName, /[\u3400-\u9fff]/u);
    assert.doesNotMatch(
      entity.defName,
      /[A-Za-z]/u,
      `${table.defKey} 的表显示名必须使用纯中文业务名称`,
    );
    assert.match(entity.comment, /[\u3400-\u9fff]/u);
    assert.doesNotMatch(entity.comment, /\bfor\s+t?_[a-z][a-z0-9_]*\b/iu);
    assert.doesNotMatch(entity.comment, /^[A-Za-z][A-Za-z0-9_]*\s*对象/u);
    for (const field of table.fields) {
      const actual = entity.fields.find((candidate) => candidate.defKey === field.defKey);
      assert.match(actual.defName, /[\u3400-\u9fff]/u, `${table.defKey}.${field.defKey}`);
      assert.match(actual.comment, /[\u3400-\u9fff]/u, `${table.defKey}.${field.defKey}`);
      assert.doesNotMatch(actual.comment, /\bfor\s+t?_[a-z][a-z0-9_]*\b/iu);
      assert.doesNotMatch(actual.comment, /^[A-Za-z][A-Za-z0-9_]*\s*对象/u);
      if (field.closedValues) {
        assert.ok(
          actual.comment.includes(`允许值：${field.closedValues.join("、")}。`),
          `${table.defKey}.${field.defKey} 未列全闭合枚举`,
        );
      }
      if (field.openSourceHint) {
        assert.match(
          actual.comment,
          /取值来源：.+；非固定枚举。/u,
          `${table.defKey}.${field.defKey} 未注明开放目录来源`,
        );
      }
    }
  }
});

artifactTest("version, immutability, audit deferral, Vault and forbidden boundaries are exact", () => {
  const model = readModel();
  const entities = entityMap(model);
  const actualVersionTables = model.entities
    .filter((entity) => entity.fields.some((field) => field.defKey === "version"))
    .map((entity) => entity.defKey);
  assert.deepEqual(actualVersionTables, versionTables);
  assert.ok(entities.get("t_compensation_event").fields.some((field) => field.defKey === "lock_version"));
  assert.ok(!entities.get("t_compensation_event").fields.some((field) => field.defKey === "version"));
  for (const tableKey of immutableTables) {
    const auditFields = entities.get(tableKey).fields
      .filter((field) => ["create_time", "update_time"].includes(field.defKey))
      .map((field) => field.defKey);
    assert.deepEqual(auditFields, ["create_time"], `${tableKey} 必须创建后不可变`);
  }

  assert.ok(!entities.has("t_consumer_document"));
  assert.ok(!entities.has("t_business_change_record"));
  assert.ok(!entities.has("t_business_change_version"));
  assert.match(entities.get("t_order_context").comment, /外部事实.*稳定引用/u);
  assert.match(entities.get("t_order_refund_position").comment, /reserved_amount \+ refunded_amount <= refundable_base_amount/u);
  assert.match(entities.get("t_refund_request").comment, /不复制下游状态.*事件版本.*渠道执行明细/u);
  assert.match(entities.get("t_checkout_network_evidence").comment, /完整客户端 IP.*完整 User-Agent.*可信代理链/u);
  assert.match(entities.get("t_consumer_acceptance").comment, /三种.*实时判定/u);
  assert.match(entities.get("t_compensation_event").comment, /PROCESSING.*lock_owner.*lock_until/u);
  const orderReason = entities.get("t_commerce_order").fields.find(
    (field) => field.defKey === "status_reason_code",
  );
  assert.deepEqual([orderReason.type, orderReason.len, orderReason.notNull], ["VARCHAR", 64, false]);
  assert.match(orderReason.comment, /当前终态原因.*取值来源：Commerce Order Status Reason Catalog/u);

  for (const entity of model.entities) {
    assert.ok(!forbiddenTables.includes(entity.defKey), `恢复了已删除表 ${entity.defKey}`);
    for (const field of entity.fields) {
      assert.equal(field.autoIncrement, false, `${entity.defKey}.${field.defKey} 不得自增`);
      const compensationDefaults = {
        execute_status: "'INIT'",
        execute_count: "0",
        next_execute_at: "CURRENT_TIMESTAMP(6)",
        lock_version: "1",
        create_time: "CURRENT_TIMESTAMP(6)",
        update_time: "CURRENT_TIMESTAMP(6)",
      };
      const expectedDefault = entity.defKey === "t_compensation_event"
        ? (compensationDefaults[field.defKey] ?? "")
        : "";
      assert.equal(field.defaultValue, expectedDefault, `${entity.defKey}.${field.defKey} 默认值漂移`);
      assert.ok(!forbiddenFieldKeys.includes(field.defKey), `恢复了已删除字段 ${entity.defKey}.${field.defKey}`);
      assert.doesNotMatch(field.defKey, /(?:pan|cvv|cvc|track_data|pin_block|ciphertext)/u);
      if (field.defKey.endsWith("_hash") || field.defKey.endsWith("_blind_index")) {
        assert.deepEqual([field.type, field.len], ["CHAR", 64], `${entity.defKey}.${field.defKey}`);
      }
    }
  }

  const context = entities.get("t_consumer_context");
  assert.deepEqual(context.fields.map((field) => field.defKey), [
    "consumer_context_id",
    "order_id",
    "consumer_reference",
    "email_vault_token",
    "phone_vault_token",
    "consumer_profile_vault_token",
    "email_domain",
    "phone_country_code",
    "billing_country_code",
    "billing_region_code",
    "billing_city",
    "billing_postal_prefix",
    "version",
    "create_time",
    "update_time",
  ]);
  const consumerReference = context.fields.find((field) => field.defKey === "consumer_reference");
  assert.deepEqual([consumerReference.type, consumerReference.len, consumerReference.notNull], ["VARCHAR", 128, true]);
  assert.match(consumerReference.comment, /Client 命名空间.*非 PII.*不得提交.*Email.*Phone.*证件号/u);
  assert.equal(context.fields.find((field) => field.defKey === "phone_vault_token").notNull, false);
  assert.equal(context.indexes.length, 1, "Consumer Context 不得保留普通分析索引");

  const networkEvidence = entities.get("t_checkout_network_evidence");
  assert.deepEqual(networkEvidence.fields.map((field) => field.defKey), [
    "network_evidence_id",
    "order_id",
    "checkout_session_id",
    "evidence_scene",
    "request_id",
    "client_ip_address",
    "user_agent",
    "trusted_proxy_chain",
    "ip_country_code",
    "ip_region_code",
    "ip_city",
    "ip_asn",
    "network_type",
    "proxy_flag",
    "vpn_flag",
    "tor_flag",
    "hosting_flag",
    "browser_family",
    "browser_major_version",
    "os_family",
    "os_major_version",
    "device_type",
    "occurred_at",
    "create_time",
  ]);
  for (const [key, length, notNull] of [
    ["client_ip_address", 45, true],
    ["user_agent", 1024, true],
    ["trusted_proxy_chain", 2048, false],
  ]) {
    const field = networkEvidence.fields.find((candidate) => candidate.defKey === key);
    assert.deepEqual([field.type, field.len, field.notNull], ["VARCHAR", length, notNull]);
  }
  for (const key of ["proxy_flag", "vpn_flag", "tor_flag", "hosting_flag"]) {
    const field = networkEvidence.fields.find((candidate) => candidate.defKey === key);
    assert.deepEqual([field.type, field.len, field.notNull], ["TINYINT", 1, false]);
    assert.match(field.comment, /1.*0.*NULL/u);
  }

  const acceptance = entities.get("t_consumer_acceptance");
  assert.deepEqual(
    acceptance.fields.map((field) => field.defKey),
    [
      "consumer_acceptance_id",
      "consumer_contract_id",
      "network_evidence_id",
      "acceptance_type",
      "accepted_at",
      "command_idempotency_id",
      "create_time",
    ],
  );

  const orderContext = entities.get("t_order_context");
  assert.equal(orderContext.fields.find((field) => field.defKey === "client_pricing_scope_id").notNull, true);
  assert.equal(orderContext.fields.find((field) => field.defKey === "partner_share_scope_id").notNull, false);
  const platformServiceScope = orderContext.fields.find(
    (field) => field.defKey === "platform_service_charge_scope_id",
  );
  const platformServiceParty = orderContext.fields.find(
    (field) => field.defKey === "platform_service_party_id",
  );
  assert.ok(platformServiceScope, "Order Context 必须冻结 Platform Service Charge Scope");
  assert.ok(platformServiceParty, "Order Context 必须冻结 Platform Service Party");
  assert.equal(platformServiceScope.notNull, false);
  assert.equal(platformServiceParty.notNull, false);
  assert.match(platformServiceScope.comment, /CCT-I13 V5.*PLATFORM_SERVICE_CHARGE.*Scope/u);
  assert.match(platformServiceParty.comment, /CCT-I13 V5.*PLATFORM_SIGNATORY.*Party/u);
  assert.match(orderContext.comment, /PARTNER.*四项成组非空.*非 PARTNER.*四项成组为空/u);
  const orderContextFieldsById = new Map(
    orderContext.fields.map((field) => [field.id, field.defKey]),
  );
  assert.ok(
    orderContext.correlations.every((relation) => ![
      "platform_service_charge_scope_id",
      "platform_service_party_id",
    ].includes(orderContextFieldsById.get(relation.myField))),
    "CCT 外部稳定 ID 不建立本地关系",
  );
  assert.ok(orderContext.fields.some((field) => field.defKey === "commercial_context_time"));

  const commerceAuthority = readFileSync(paths.commerceDatabase, "utf8");
  assert.match(
    commerceAuthority,
    /COM-I04.*platform_service_charge_scope_id.*platform_service_party_id.*V5 commercial_context_hash/su,
  );
  assert.match(commerceAuthority, /Risk.*V5 Hash.*不得消费 Finance Entity ID/su);
  const financeTaxSnapshotHash = orderContext.fields.find(
    (field) => field.defKey === "finance_tax_snapshot_hash",
  );
  assert.equal(financeTaxSnapshotHash.notNull, true);
  assert.match(
    financeTaxSnapshotHash.comment,
    /Finance 税务快照 calculation_hash.*SHA-256 小写 64 位十六进制/u,
  );
  const legal = entities.get("t_order_legal");
  const clientAgreementEntity = legal.fields.find(
    (field) => field.defKey === "client_agreement_operating_entity_id",
  );
  const partnerAgreementEntity = legal.fields.find(
    (field) => field.defKey === "partner_agreement_operating_entity_id",
  );
  assert.equal(clientAgreementEntity.notNull, true);
  assert.equal(partnerAgreementEntity.notNull, false);
  assert.match(clientAgreementEntity.comment, /唯一定位 Client 合同与订单 Operating Entity 关联记录/u);
  assert.match(partnerAgreementEntity.comment, /唯一定位 Partner 合同与订单 Operating Entity 关联记录/u);
  const item = entities.get("t_order_item");
  assert.match(
    item.fields.find((field) => field.defKey === "client_item_id").comment,
    /订单内唯一/u,
  );
  assert.match(
    item.fields.find((field) => field.defKey === "sort_no").comment,
    /呈现顺序/u,
  );
});

artifactTest("model is a credential-free deterministic clone of the shared template", async () => {
  const templateBytes = readFileSync(paths.template);
  assert.equal(sha256(templateBytes), expectedTemplateDigest);
  const forbiddenRuntimeReference = new RegExp([
    ["\\.", "super", "powers"].join(""),
    "commerce-manifest-design-report",
    "commerce-model\\.test",
    "client-contract-field-metadata",
  ].join("|"), "u");
  for (const productionPath of [paths.metadata, paths.generator]) {
    const source = readFileSync(productionPath, "utf8");
    assert.doesNotMatch(source, forbiddenRuntimeReference);
  }
  const model = readModel();
  assert.deepEqual(model.dbConn, []);
  assert.deepEqual(model.dbConns, []);
  assert.equal(model.profile.default.dbConn, "");

  const generator = await import(`./generate-commerce-model.mjs?contract=${Date.now()}`);
  const first = generator.serializeCommerceModel(generator.generateCommerceModel());
  const second = generator.serializeCommerceModel(generator.generateCommerceModel());
  assert.equal(first, second, "相同输入必须产生逐字节相同模型");
  assert.equal(first, readFileSync(paths.model, "utf8"), "已提交模型必须与生成器输出逐字节一致");

  const ids = [
    ...model.entities.flatMap((entity) => [
      entity.id,
      ...entity.fields.map((field) => field.id),
      ...entity.indexes.flatMap((index) => [index.id, ...index.fields.map((field) => field.id)]),
    ]),
    ...model.diagrams.flatMap((diagram) => [
      diagram.id,
      ...diagram.canvasData.cells.map((cell) => cell.id),
    ]),
  ];
  assert.ok(ids.every((id) => idPattern.test(id)), "所有生成 ID 必须是稳定 SHA-256 UUID v5");
  assert.equal(new Set(ids).size, ids.length, "所有生成 ID 必须唯一");

  const mappings = new Map(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping]));
  assert.equal(mappings.get("BIGINT")["797A1496-D649-4261-89B4-544132EC3F36"], "Long");
  assert.equal(mappings.get("date")["797A1496-D649-4261-89B4-544132EC3F36"], "Instant");
  assert.equal(mappings.get("double")["797A1496-D649-4261-89B4-544132EC3F36"], "BigDecimal");
});

artifactTest("generated model freezes actual totals and both SHA-256 digests", () => {
  const modelBytes = readFileSync(paths.model, "utf8");
  const model = JSON.parse(modelBytes);
  const expectedTotals = {
    tables: 15,
    fields: 246,
    uniqueIndexes: 24,
    accessIndexes: 12,
    relations: 15,
    diagramGroups: 4,
  };
  assert.deepEqual(modelTotals(model), expectedTotals);
  assert.equal(sha256(modelBytes), expectedModelDigest);
  assert.equal(structuralDigest(model), expectedStructuralDigest);

  const physicalManifest = readPhysicalManifest();
  assert.deepEqual(physicalManifest.modelTotals, expectedTotals);
  assert.equal(physicalManifest.templateSha256, expectedTemplateDigest);
  assert.equal(physicalManifest.modelSha256, expectedModelDigest);
  assert.equal(
    physicalManifest.structuralProjectionSha256,
    expectedStructuralDigest,
  );
});

artifactTest("current Commerce database and physical docs report the generated model facts", () => {
  const logicalSource = readFileSync(paths.commerceDatabase, "utf8");
  const physicalSource = readFileSync(paths.decisions, "utf8");
  const currentFacts = /15 表 \/ 246 字段 \/ 24 个非主键唯一索引 \/ 12 个访问索引 \/ 15 条本库关系 \/ 4 个 Diagram Group/u;
  assert.match(logicalSource, currentFacts);
  assert.match(physicalSource, /"tables": 15,[\s\S]*"fields": 246,[\s\S]*"uniqueIndexes": 24,[\s\S]*"accessIndexes": 12,[\s\S]*"relations": 15,[\s\S]*"diagramGroups": 4/u);
  assert.doesNotMatch(logicalSource, /当前已生成(?:资产|模型|的 PDManer).*14 张?表\s*\/\s*223 字段/u);
  assert.match(logicalSource, /当前 Vault V1.*Protect\/Reveal/u);
  assert.doesNotMatch(logicalSource, /Token\/Binding|Retire 未被业务行引用的 Binding/u);
  assert.match(physicalSource, /request_snapshot_json.*最多 16 KiB UTF-8/u);
  assert.match(physicalSource, /response_body_json.*最多 4 KiB UTF-8.*Checkout URL.*访问能力/u);
  assert.match(physicalSource, /当前\s+Vault V1[^\r\n]*Protect\/Reveal/u);
  assert.doesNotMatch(physicalSource, /终止 Binding.*Retire|未被业务行引用.*Binding/u);
});

artifactTest("manual schema exactly projects all 15 model tables without CHECK or FK", () => {
  const schema = readFileSync(paths.schema, "utf8");
  const model = readModel();
  assert.doesNotMatch(schema, /\b(?:CHECK|FOREIGN\s+KEY)\b/iu);
  assert.doesNotMatch(schema, /public_token_hash/u);
  const actualTables = parseManualSchema(schema);
  assert.equal(
    (schema.match(/CREATE TABLE `/gu) ?? []).length,
    actualTables.length,
    "人工 Schema 存在无法完整解析或未纳入精确比较的 CREATE TABLE",
  );
  assert.deepEqual(actualTables.map((table) => table.defKey), model.entities.map((entity) => entity.defKey));
  assert.equal(actualTables.length, 15);

  for (const entity of model.entities) {
    const actual = actualTables.find((table) => table.defKey === entity.defKey);
    const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
    assert.deepEqual(actual.columns, entity.fields.map((field) => ({
      defKey: field.defKey,
      type: field.type,
      len: field.len,
      scale: field.scale,
      characterSet: field.extProps.characterSet ?? "",
      collation: field.extProps.collation ?? "",
      notNull: field.notNull,
      defaultValue: field.defaultValue,
      comment: field.comment,
    })), `${entity.defKey} 列清单、类型、可空性、默认值、排序规则或注释漂移`);
    assert.deepEqual(
      actual.primaryKey,
      entity.fields.filter((field) => field.primaryKey).map((field) => field.defKey),
      `${entity.defKey} 主键漂移`,
    );
    assert.deepEqual(actual.indexes, entity.indexes.map((index) => ({
      defKey: index.defKey,
      unique: index.unique,
      fields: index.fields.map((entry) => fieldsById.get(entry.fieldDefKey)),
      comment: index.comment,
    })), `${entity.defKey} UK/IDX 清单或字段顺序漂移`);
    assert.equal(actual.characterSet, "utf8mb4", `${entity.defKey} 表字符集漂移`);
    assert.equal(actual.collation, "utf8mb4_0900_ai_ci", `${entity.defKey} 表排序规则漂移`);
    assert.equal(actual.comment, entity.comment, `${entity.defKey} 表注释漂移`);
  }

  const firstSliceTables = [
    "t_command_idempotency",
    "t_commerce_order",
    "t_order_context",
    "t_order_legal",
    "t_order_item",
    "t_order_refund_position",
    "t_order_funds_availability_projection",
    "t_checkout_session",
    "t_order_payment_projection",
  ];
  for (const table of firstSliceTables) assert.ok(schema.includes(`CREATE TABLE \`${table}\` (`));
  assert.match(readFileSync(paths.initialData, "utf8"), /no business.*seed rows/iu);
});

artifactTest("generator --check proves the checked-in model is current", () => {
  const result = spawnSync(process.execPath, [paths.generator, "--check"], {
    cwd: workspaceRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /Commerce model is up to date/u);
});
