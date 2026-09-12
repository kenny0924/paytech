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
} from "./commerce-field-metadata.mjs";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const workspaceRoot = path.resolve(scriptDirectory, "../..");
const templatePath = path.join(
  workspaceRoot,
  "agent-docs/templates/pdmaner/template.pdma.json",
);
const outputPath = path.join(
  workspaceRoot,
  "commerce-service/docs/database/commerce.pdma.json",
);

const MODEL_KEY = "code-mor/commerce/database/v1";
const MYSQL_MAPPING_ID = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const JAVA_MAPPING_ID = "797A1496-D649-4261-89B4-544132EC3F36";
const MYBATIS_MAPPING_ID = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";

const ASCII_BINARY_FIELD_PATHS = new Set([
  "commerce_order.entity_no",
  "commerce_order.order_no",
  "commerce_order.client_order_id",
  "order_item.client_item_id",
  "order_funds_availability_projection.economics_event_key",
  "order_funds_availability_projection.economics_source_payload_hash",
  "order_funds_availability_projection.maturity_event_key",
  "order_funds_availability_projection.maturity_source_payload_hash",
  "checkout_session.public_token",
  "checkout_session.public_origin",
  "refund_request.refund_request_no",
  "refund_request.client_refund_id",
  "command_idempotency.caller_scope",
  "command_idempotency.idempotency_key",
  "command_idempotency.resource_no",
  "command_idempotency.response_location",
  "compensation_event.event_key",
  "compensation_event.event_type",
  "compensation_event.sub_event_type",
]);

// Explicit production manifest. It is intentionally independent from tests and Markdown.
const FIELD_MANIFEST = String.raw`
[t_commerce_order]
order_id BIGINT NN
order_no VARCHAR(64) NN
client_id BIGINT NN
client_order_id VARCHAR(128) NN
context_format VARCHAR(32) NN {TERM_PRICE,LEGACY_SCOPE}
preparation_command_id BIGINT N
termination_requested_at DATETIME(6) N
entity_no CHAR(5) N
entity_source_type VARCHAR(64) N {PLATFORM,CLIENT,PARTNER}
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
const INDEX_MANIFEST = String.raw`
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

const FIELD_DEFAULT_VALUES = Object.freeze({
  "compensation_event.execute_status": "'INIT'",
  "compensation_event.execute_count": "0",
  "compensation_event.next_execute_at": "CURRENT_TIMESTAMP(6)",
  "compensation_event.lock_version": "1",
  "compensation_event.create_time": "CURRENT_TIMESTAMP(6)",
  "compensation_event.update_time": "CURRENT_TIMESTAMP(6)",
});
const RELATION_MANIFEST = String.raw`
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

const DIAGRAM_GROUPS = Object.freeze([
  Object.freeze({
    defKey: "order_context",
    defName: "订单与不可变上下文",
    primary: Object.freeze([
      "commerce_order",
      "order_context",
      "order_legal",
      "order_item",
    ]),
    references: Object.freeze([]),
  }),
  Object.freeze({
    defKey: "checkout_consumer",
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
  Object.freeze({
    defKey: "payment_refund_position",
    defName: "支付与退款",
    primary: Object.freeze([
      "order_payment_projection",
      "order_funds_availability_projection",
      "order_refund_position",
      "refund_request",
    ]),
    references: Object.freeze(["commerce_order"]),
  }),
  Object.freeze({
    defKey: "reliability",
    defName: "可靠执行",
    primary: Object.freeze([
      "command_idempotency",
      "compensation_event",
    ]),
    references: Object.freeze([]),
  }),
]);

const VERSION_TABLES = Object.freeze([
  "commerce_order",
  "order_payment_projection",
  "order_funds_availability_projection",
  "order_refund_position",
  "checkout_session",
  "consumer_context",
  "refund_request",
  "command_idempotency",
]);

const IMMUTABLE_TABLES = Object.freeze([
  "order_context",
  "order_legal",
  "order_item",
  "consumer_contract",
  "checkout_network_evidence",
  "consumer_acceptance",
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

function normalizeTemplate(template) {
  const model = clone(template);
  model.name = "Code-MoR Commerce Database V1";
  model.describe = "Deterministic PDManer physical model derived from the approved Commerce V1 manifest";
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
        defaultValue: FIELD_DEFAULT_VALUES[`${table.defKey}.${manifestField.defKey}`] ?? "",
        hideInGraph: false,
        refDict: "",
        baseType: mapping.baseType,
        extProps: ASCII_BINARY_FIELD_PATHS.has(`${table.defKey}.${manifestField.defKey}`)
          ? { characterSet: "ascii", collation: "ascii_bin" }
          : {},
        domain: mapping.domain,
        id: stableUuid(`field/${table.defKey}/${manifestField.defKey}`),
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
    for (const relation of relations.filter((candidate) => group.primary.includes(candidate.childTable))) {
      const child = entitiesByKey.get(relation.childTable);
      const parent = entitiesByKey.get(relation.parentTable);
      const childField = child.fields.find((field) => field.defKey === relation.childField);
      const parentField = parent.fields.find((field) => field.defKey === relation.parentField);
      invariant(cellByTable.has(relation.parentTable), `Diagram lacks relation parent: ${group.defKey}.${relation.parentTable}`);
      cells.push({
        id: stableUuid(`diagram/${group.defKey}/relation/${relation.childTable}/${relation.childField}/${relation.parentTable}/${relation.parentField}`),
        shape: "erdRelation",
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
  for (const [key, value] of Object.entries(FIELD_METADATA)) {
    invariant(/[\u3400-\u9fff]/u.test(value.defName), `Field display name is not Chinese: ${key}`);
    invariant(/[\u3400-\u9fff]/u.test(value.description), `Field description is not Chinese: ${key}`);
  }
}

function validateGeneratedModel(model, tables, relations) {
  invariant(JSON.stringify(modelTotals(model)) === JSON.stringify({
    tables: 15,
    fields: 249,
    uniqueIndexes: 24,
    accessIndexes: 12,
    relations: 15,
    diagramGroups: 4,
  }), `Unexpected generated totals: ${JSON.stringify(modelTotals(model))}`);

  const entitiesByKey = new Map(model.entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  invariant(new Set(VERSION_TABLES).size === 8, "Version allowlist must have 8 tables");
  invariant(new Set(IMMUTABLE_TABLES).size === 6, "Immutable allowlist must have 6 tables");
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
  }
  const primaryTables = DIAGRAM_GROUPS.flatMap((group) => group.primary);
  invariant(primaryTables.length === 15 && new Set(primaryTables).size === 15, "Every table must have exactly one primary diagram group");
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation").length;
  invariant(relationCells === 15, `Expected 15 relation cells, found ${relationCells}`);
}

export function structuralProjection(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
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

export function generateCommerceModel({
  template = JSON.parse(readFileSync(templatePath, "utf8")),
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

export function serializeCommerceModel(model = generateCommerceModel()) {
  return `${JSON.stringify(model, null, 2)}\n`;
}

function runCli() {
  const unknown = process.argv.slice(2).filter((argument) => argument !== "--check");
  invariant(unknown.length === 0, `Unknown argument(s): ${unknown.join(", ")}`);
  const expected = serializeCommerceModel();
  if (process.argv.includes("--check")) {
    invariant(existsSync(outputPath), `Commerce model does not exist: ${outputPath}`);
    invariant(readFileSync(outputPath, "utf8") === expected, "Commerce model is stale; regenerate it");
    process.stdout.write("Commerce model is up to date\n");
    return;
  }
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, expected, "utf8");
  process.stdout.write(`Wrote Commerce model: ${outputPath}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runCli();
}
