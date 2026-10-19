import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import {
  buildIndexes,
  LOCAL_RELATIONS,
  logicalTableKey,
  modelRelations,
  parseClientContractCatalog,
  physicalTableKey,
  serializeModel,
} from "./client-contract-model-core.mjs";
import {
  CLIENT_MASTER_CATALOG_TABLES,
  generateClientMasterModel,
} from "./generate-client-master-model.mjs";
import {
  COMMERCIAL_TABLES,
  generateCommercialModel,
} from "./generate-commercial-model.mjs";
import {
  CLOSED_ENUM_VALUES,
  CONDITIONAL_VALUE_RULES,
  FIELD_DESCRIPTION_OVERRIDES,
  FIELD_METADATA,
  OPEN_CODE_SOURCES,
} from "./client-contract-field-metadata.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
// The Client & Contract catalog is split into two service databases. Catalog
// contracts below run against the combined catalog view of both models; every
// per-database fact (paths, totals, relations, diagrams, digests) is asserted per model.
const serviceModels = Object.freeze([
  {
    key: "client-master",
    modelPath: path.join(workspaceRoot, "paytech/client-master/docs/database/client-master.pdma.json"),
    generatorPath: path.join(workspaceRoot, "scripts/pdmaner/generate-client-master-model.mjs"),
    physicalDecisionsPath: path.join(workspaceRoot, "paytech/client-master/docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md"),
    catalogTables: CLIENT_MASTER_CATALOG_TABLES,
    generate: generateClientMasterModel,
  },
  {
    key: "commercial",
    modelPath: path.join(workspaceRoot, "paytech/commercial/docs/database/commercial.pdma.json"),
    generatorPath: path.join(workspaceRoot, "scripts/pdmaner/generate-commercial-model.mjs"),
    physicalDecisionsPath: path.join(workspaceRoot, "paytech/commercial/docs/database/COMMERCIAL_PHYSICAL_DECISIONS.md"),
    catalogTables: COMMERCIAL_TABLES,
    generate: generateCommercialModel,
  },
]);
// Tables each service database keeps its own identical copy of.
const sharedServiceTables = Object.freeze(["business_change_record", "business_change_version", "compensation_event"]);
// Tables that do not come from the Client & Contract catalog.
const nonCatalogServiceTables = Object.freeze({
  "client-master": ["client_stored_card_setting", "consumed_event", "consumed_aggregate_version"],
  commercial: [],
});
// paytech-messaging consumer tables (step 08, design/08 §4): standard messaging shape, not CCT catalog conventions.
const messagingConsumerTables = Object.freeze(["consumed_event", "consumed_aggregate_version"]);
// Catalog tables dropped from both service databases (decision S10).
const droppedCatalogTables = Object.freeze(["command_idempotency"]);
const templatePath = path.join(
  workspaceRoot,
  "agent-docs/templates/pdmaner/template.pdma.json",
);
const generatorSource = [
  path.join(workspaceRoot, "scripts/pdmaner/client-contract-model-core.mjs"),
  ...serviceModels.map((service) => service.generatorPath),
].map((file) => readFileSync(file, "utf8")).join("\n");
const expectedTemplateDigest =
  "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const pendingStructuralProjectionDigest = "PENDING_APPROVED_40_DIGEST";
// Approved per-database Structural Projection digests after the CCT split.
const expectedStructuralProjectionDigests = Object.freeze({
  "client-master": "80dba108a6322e957ca7c652a1911979705388b47b71caa24ee51bb14f989802",
  commercial: "34c577620f6290641323d9a7b1996032e0ccda5af6cf6911ff8a1591a1cff72e",
});

const leanAssignmentTables = Object.freeze(["entity_source_relation","client_entity_affiliation","operating_entity_scope"]);
const removedAssignmentTables = Object.freeze(["entity_assignment_rule","client_entity_assignment","entity_assignment_policy","entity_assignment_policy_ver","assignment_policy_binding","client_entity_assignment_ver"]);
const removedAgreementLegalTables = Object.freeze(["agreement_acceptance","agreement_version","agreement_version_party","agreement_scope_binding","consumer_legal_document_version","seller_disclosure_version"]);
const removedRealtimeDeferredTables = Object.freeze(["client_eligibility_evaluation","eligibility_dimension_evaluation","eligibility_source_evaluation","eligibility_source_fact","retention_policy_projection","retention_resource_control","retention_hold_projection","retention_action_log"]);
const removedAgreementLegalFieldPaths = Object.freeze(["agreement_document_ref.document_id","agreement_document_ref.agreement_version_id","agreement_acceptance.agreement_version_party_id","agreement_acceptance.agreement_version_id","agreement_acceptance.party_id","agreement_acceptance.party_role","agreement_acceptance.actor_id","agreement_acceptance.evidence_file_id","agreement_acceptance.evidence_content_hash","consumer_legal_document.scope_type","consumer_legal_document.client_id","consumer_legal_document.scope_key","seller_disclosure.disclosure_version_id","seller_disclosure.version_no","seller_disclosure.active_slot","seller_disclosure.approved_by","seller_disclosure.approval_trigger_id","seller_disclosure.public_support_contact","seller_disclosure.content_hash","client_pricing_plan.agreement_id","partner_share_plan.agreement_id","client_pricing_version.agreement_version_id","partner_share_version.agreement_version_id"]);
const leanAssignmentFields = Object.freeze({
  "entity_source_relation": ["source_relation_id","entity_no","source_type","source_client_id","source_partner_id","client_source_basis","status","active_source_slot","effective_from","effective_to","create_time","update_time"],
  "client_entity_affiliation": ["affiliation_id","client_id","entity_no","priority_no","status","active_slot","effective_from","effective_to","create_time","update_time"],
  "operating_entity_scope": ["scope_id","entity_no","application_id","consumer_country_code","currency","industry_code","mcc_code","status","active_slot","effective_from","effective_to","create_time","update_time"]
});
const idPattern =
  /^[A-F0-9]{8}-[A-F0-9]{4}-[45][A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/;

const expectedTables = Object.freeze(["agreement","agreement_capability","agreement_capability_limit","agreement_document_ref","agreement_operating_entity","agreement_party","agreement_product","application_document","business_change_record","business_change_version","client","client_application","client_business_classification","client_business_profile","client_entity_affiliation","commercial_term_component","commercial_term_component_band","commercial_term_plan","commercial_term_price","commercial_term_version","compensation_event","consumer_legal_document","entity_source_relation","individual_profile","local_partner","operating_entity","operating_entity_application","operating_entity_scope","organization_profile","partner_application","party","party_address","party_contact","party_identity_key","party_profile","party_profile_identifier","party_relation","pricing_profile","pricing_profile_cond","product","product_capability","product_fee_event","seller_disclosure"]);
// Combined catalog view: 44 catalog tables minus the dropped command_idempotency.
const expectedFinalTableManifestCount = 43;
const expectedFinalFieldManifestCount = 456;
// All catalog references (each keeps a left-prefix access index); 12 of them
// cross the client-master/commercial database boundary and are not correlations.
const expectedRelationSignatureCount = 71;
const expectedCrossDatabaseRelationSignatureCount = 12;
const expectedLocalRelationSignatureCount = 59;
const expectedServiceTotals = Object.freeze({
  // Step 08 adds t_consumed_event (6 fields, 1 IDX) and t_consumed_aggregate_version (4 fields).
  "client-master": { tables: 29, fields: 300, uniqueIndexes: 39, accessIndexes: 25, relations: 40, diagramGroups: 6 },
  commercial: { tables: 20, fields: 203, uniqueIndexes: 28, accessIndexes: 25, relations: 21, diagramGroups: 4 },
});

const expectedTableDisplayNames = Object.freeze({
  "party": "法律主体主档",
  "party_profile": "法律主体资料",
  "individual_profile": "个人资料",
  "organization_profile": "机构资料",
  "party_identity_key": "法律标识注册主档",
  "party_profile_identifier": "主体资料法律标识关联",
  "party_address": "主体地址",
  "party_contact": "主体联系方式",
  "party_relation": "主体关系",
  "client": "客户角色",
  "local_partner": "本地合作伙伴角色",
  "operating_entity": "经营主体角色",
  "client_business_profile": "客户业务资料",
  "client_business_classification": "客户经营分类",
  "client_application": "客户申请",
  "partner_application": "本地合作伙伴入驻申请",
  "operating_entity_application": "经营主体入驻申请",
  "operating_entity_scope": "经营主体准入范围",
  "application_document": "申请材料",
  "client_entity_affiliation": "客户经营主体适用关系",
  "entity_source_relation": "经营主体来源关系",
  "agreement": "合同",
  "agreement_party": "合同参与方与签署",
  "agreement_document_ref": "合同文件引用",
  "agreement_operating_entity": "合同覆盖经营主体",
  "agreement_product": "合同订阅产品",
  "agreement_capability": "合同开通项",
  "agreement_capability_limit": "开通项约束",
  "product": "产品",
  "product_capability": "可开通项目录",
  "product_fee_event": "计费事件目录",
  "consumer_legal_document": "消费者法律文档",
  "seller_disclosure": "法律卖方披露",
  "pricing_profile": "定价画像",
  "pricing_profile_cond": "定价画像条件",
  "commercial_term_plan": "商业条款方案",
  "commercial_term_version": "商业条款版本",
  "commercial_term_price": "商业条款价格",
  "commercial_term_component": "商业条款计算组件",
  "commercial_term_component_band": "商业条款阶梯档位",
  "business_change_record": "业务行变更历史主档",
  "business_change_version": "业务行变更版本明细",
  "compensation_event": "本地补偿事件"
});

const expectedServiceDiagramGroups = Object.freeze({
  "client-master": Object.freeze({
    "party_identity": {
      "defName": "Party and Identity",
      "primaryTables": ["party","party_profile","individual_profile","organization_profile","party_identity_key","party_profile_identifier","party_address","party_contact","party_relation"],
      "tables": ["party","party_profile","individual_profile","organization_profile","party_identity_key","party_profile_identifier","party_address","party_contact","party_relation","client_application"]
    },
    "roles_onboarding": {
      "defName": "Roles and Onboarding",
      "primaryTables": ["client","local_partner","operating_entity","client_business_profile","client_business_classification","client_application","partner_application","operating_entity_application","application_document"],
      "tables": ["client","local_partner","operating_entity","client_business_profile","client_business_classification","client_application","partner_application","operating_entity_application","application_document","party","entity_source_relation","party_profile"]
    },
    "entity_assignment": {
      "defName": "OE Scope, Affiliation and Source",
      "primaryTables": ["operating_entity_scope","client_entity_affiliation","entity_source_relation"],
      "tables": ["client_entity_affiliation","operating_entity_scope","entity_source_relation","operating_entity","operating_entity_application","client","local_partner"]
    },
    "consumer_legal": {
      "defName": "Consumer Legal and Seller Disclosure",
      "primaryTables": ["consumer_legal_document","seller_disclosure"],
      "tables": ["consumer_legal_document","seller_disclosure","operating_entity"]
    },
    "client_settings": {
      "defName": "Client Settings",
      "primaryTables": ["client_stored_card_setting"],
      "tables": ["client_stored_card_setting"]
    },
    "history_reliability": {
      "defName": "Local History and Reliability",
      "primaryTables": ["business_change_record","business_change_version","compensation_event","consumed_event","consumed_aggregate_version"],
      "tables": ["business_change_record","business_change_version","compensation_event","consumed_event","consumed_aggregate_version"]
    }
  }),
  commercial: Object.freeze({
    "agreement": {
      "defName": "Agreement",
      "primaryTables": ["agreement","agreement_party","agreement_document_ref","agreement_operating_entity","agreement_product","agreement_capability","agreement_capability_limit"],
      "tables": ["agreement","agreement_party","agreement_document_ref","agreement_operating_entity","agreement_product","agreement_capability","agreement_capability_limit","product","product_capability"]
    },
    "product_catalog": {
      "defName": "Product Catalog",
      "primaryTables": ["product","product_capability","product_fee_event"],
      "tables": ["product","product_capability","product_fee_event"]
    },
    "pricing_share": {
      "defName": "Client Pricing and Partner Share",
      "primaryTables": ["pricing_profile","pricing_profile_cond","commercial_term_plan","commercial_term_version","commercial_term_price","commercial_term_component","commercial_term_component_band"],
      "tables": ["pricing_profile","pricing_profile_cond","commercial_term_plan","commercial_term_version","commercial_term_price","commercial_term_component","commercial_term_component_band","agreement"]
    },
    "history_reliability": {
      "defName": "Local History and Reliability",
      "primaryTables": ["business_change_record","business_change_version","compensation_event"],
      "tables": ["business_change_record","business_change_version","compensation_event"]
    }
  }),
});

const expectedPrimaryKeys = Object.freeze({
  "party": "party_id",
  "party_profile": "profile_id",
  "individual_profile": "profile_id",
  "organization_profile": "profile_id",
  "party_identity_key": "identity_key_id",
  "party_profile_identifier": "profile_identifier_id",
  "party_address": "address_id",
  "party_contact": "contact_id",
  "party_relation": "relation_id",
  "client": "client_id",
  "local_partner": "local_partner_id",
  "operating_entity": "entity_no",
  "client_business_profile": "business_profile_id",
  "client_business_classification": "classification_id",
  "client_application": "application_id",
  "partner_application": "application_id",
  "operating_entity_application": "application_id",
  "operating_entity_scope": "scope_id",
  "application_document": "document_id",
  "client_entity_affiliation": "affiliation_id",
  "entity_source_relation": "source_relation_id",
  "agreement": "agreement_id",
  "agreement_party": "agreement_party_id",
  "agreement_document_ref": "agreement_document_id",
  "agreement_operating_entity": "agreement_operating_entity_id",
  "agreement_product": "agreement_product_id",
  "agreement_capability": "agreement_capability_id",
  "agreement_capability_limit": "agreement_capability_limit_id",
  "product": "product_id",
  "product_capability": "product_capability_id",
  "product_fee_event": "product_fee_event_id",
  "consumer_legal_document": "legal_document_id",
  "seller_disclosure": "disclosure_id",
  "pricing_profile": "pricing_profile_id",
  "pricing_profile_cond": "profile_condition_id",
  "commercial_term_plan": "term_plan_id",
  "commercial_term_version": "term_version_id",
  "commercial_term_price": "term_price_id",
  "commercial_term_component": "term_component_id",
  "commercial_term_component_band": "term_band_id",
  "business_change_record": "history_id",
  "business_change_version": "change_version_id",
  "compensation_event": "id"
});

const changedTableUniqueKeys = Object.freeze({
  "party": ["party_no"],
  "party_profile": ["party_id"],
  "individual_profile": [],
  "organization_profile": [],
  "party_identity_key": ["identifier_type,issuer_scope_key,normalized_identifier_value"],
  "party_profile_identifier": ["profile_id,identity_key_id"],
  "party_address": ["profile_id,address_type"],
  "party_contact": ["profile_id,contact_type"],
  "party_relation": ["organization_party_id,related_party_id,relation_type"],
  "client": ["client_no","party_id"],
  "local_partner": ["partner_no","party_id"],
  "operating_entity": ["party_id"],
  "client_business_profile": ["client_id"],
  "client_business_classification": ["business_profile_id,industry_code,mcc_code","business_profile_id,primary_slot"],
  "client_application": ["application_no","client_id,active_slot","current_submission_id","requested_by_account_id,active_slot"],
  "partner_application": ["application_no","local_partner_id,active_slot"],
  "operating_entity_application": ["application_no","entity_no,active_slot"],
  "operating_entity_scope": ["application_id,consumer_country_code,currency,industry_code,mcc_code","entity_no,consumer_country_code,currency,industry_code,mcc_code,active_slot"],
  "application_document": ["client_application_id,subject_party_id,requirement_code,document_type,file_id","partner_application_id,subject_party_id,requirement_code,document_type,file_id","operating_entity_application_id,subject_party_id,requirement_code,document_type,file_id"],
  "client_entity_affiliation": ["client_id,entity_no,active_slot","client_id,priority_no,active_slot","client_id,entity_no,effective_from"],
  "entity_source_relation": ["entity_no,effective_from","entity_no,active_source_slot"],
  "agreement": ["agreement_no"],
  "agreement_party": ["agreement_id,party_id,party_role","agreement_id,party_id,acceptance_ref_slot","acceptance_reference,acceptance_ref_slot"],
  "agreement_document_ref": ["agreement_id,document_type,file_id"],
  "agreement_operating_entity": ["agreement_id,entity_no"],
  "agreement_product": ["agreement_id,product_code"],
  "agreement_capability": ["agreement_id,entity_no,capability_code"],
  "agreement_capability_limit": ["agreement_capability_id,limit_code,limit_value"],
  "product": ["product_code"],
  "product_capability": ["capability_code"],
  "product_fee_event": ["fee_event_code,term_type"],
  "consumer_legal_document": ["document_code","document_type,entity_no,country_code,locale,effective_from"],
  "seller_disclosure": ["entity_no,country_code,locale,effective_from"],
  "pricing_profile": ["pricing_profile_code"],
  "pricing_profile_cond": ["pricing_profile_id,dimension_code,condition_value"],
  "commercial_term_plan": ["term_type,plan_code","agreement_id,term_type","local_partner_id,term_type"],
  "commercial_term_version": ["term_plan_id,version_no","term_plan_id,active_slot"],
  "commercial_term_price": ["term_version_id,entity_no,currency,pricing_profile_code","term_version_id,entity_no,currency,priority_no"],
  "commercial_term_component": ["term_price_id,component_code","term_price_id,component_no"],
  "commercial_term_component_band": ["term_component_id,band_no","term_component_id,lower_bound"],
  "business_change_record": ["record_type,record_id"],
  "business_change_version": ["history_id,version_no"],
  "compensation_event": ["event_key,event_type,sub_event_type"]
});

const expectedUniqueKeys = Object.freeze({
  "party": ["party_no"],
  "party_profile": ["party_id"],
  "individual_profile": [],
  "organization_profile": [],
  "party_identity_key": ["identifier_type,issuer_scope_key,normalized_identifier_value"],
  "party_profile_identifier": ["profile_id,identity_key_id"],
  "party_address": ["profile_id,address_type"],
  "party_contact": ["profile_id,contact_type"],
  "party_relation": ["organization_party_id,related_party_id,relation_type"],
  "client": ["client_no","party_id"],
  "local_partner": ["partner_no","party_id"],
  "operating_entity": ["party_id"],
  "client_business_profile": ["client_id"],
  "client_business_classification": ["business_profile_id,industry_code,mcc_code","business_profile_id,primary_slot"],
  "client_application": ["application_no","client_id,active_slot","current_submission_id","requested_by_account_id,active_slot"],
  "partner_application": ["application_no","local_partner_id,active_slot"],
  "operating_entity_application": ["application_no","entity_no,active_slot"],
  "operating_entity_scope": ["application_id,consumer_country_code,currency,industry_code,mcc_code","entity_no,consumer_country_code,currency,industry_code,mcc_code,active_slot"],
  "application_document": ["client_application_id,subject_party_id,requirement_code,document_type,file_id","partner_application_id,subject_party_id,requirement_code,document_type,file_id","operating_entity_application_id,subject_party_id,requirement_code,document_type,file_id"],
  "client_entity_affiliation": ["client_id,entity_no,active_slot","client_id,priority_no,active_slot","client_id,entity_no,effective_from"],
  "entity_source_relation": ["entity_no,effective_from","entity_no,active_source_slot"],
  "agreement": ["agreement_no"],
  "agreement_party": ["agreement_id,party_id,party_role","agreement_id,party_id,acceptance_ref_slot","acceptance_reference,acceptance_ref_slot"],
  "agreement_document_ref": ["agreement_id,document_type,file_id"],
  "agreement_operating_entity": ["agreement_id,entity_no"],
  "agreement_product": ["agreement_id,product_code"],
  "agreement_capability": ["agreement_id,entity_no,capability_code"],
  "agreement_capability_limit": ["agreement_capability_id,limit_code,limit_value"],
  "product": ["product_code"],
  "product_capability": ["capability_code"],
  "product_fee_event": ["fee_event_code,term_type"],
  "consumer_legal_document": ["document_code","document_type,entity_no,country_code,locale,effective_from"],
  "seller_disclosure": ["entity_no,country_code,locale,effective_from"],
  "pricing_profile": ["pricing_profile_code"],
  "pricing_profile_cond": ["pricing_profile_id,dimension_code,condition_value"],
  "commercial_term_plan": ["term_type,plan_code","agreement_id,term_type","local_partner_id,term_type"],
  "commercial_term_version": ["term_plan_id,version_no","term_plan_id,active_slot"],
  "commercial_term_price": ["term_version_id,entity_no,currency,pricing_profile_code","term_version_id,entity_no,currency,priority_no"],
  "commercial_term_component": ["term_price_id,component_code","term_price_id,component_no"],
  "commercial_term_component_band": ["term_component_id,band_no","term_component_id,lower_bound"],
  "business_change_record": ["record_type,record_id"],
  "business_change_version": ["history_id,version_no"],
  "compensation_event": ["event_key,event_type,sub_event_type"]
});

const expectedAgreementLegalAccessIndexes = Object.freeze({
  "agreement": [["client_id","effective_from","effective_to"],["local_partner_id","effective_from","effective_to"],["referral_partner_id"],["entity_no"]],
  "agreement_party": [["party_id"],["party_profile_id"],["evidence_document_id"]],
  "agreement_document_ref": [],
  "agreement_operating_entity": [["entity_no"]],
  "consumer_legal_document": [["entity_no","country_code","locale","document_type","effective_from","effective_to"]],
  "seller_disclosure": [["entity_no","country_code","locale","effective_from","effective_to"]]
});

const expectedRelationSignatures = Object.freeze(["agreement.client_id->client.client_id|n:1","agreement.entity_no->operating_entity.entity_no|n:1","agreement.local_partner_id->local_partner.local_partner_id|n:1","agreement.referral_partner_id->local_partner.local_partner_id|n:1","agreement_capability.agreement_id->agreement.agreement_id|n:1","agreement_capability.capability_code->product_capability.capability_code|n:1","agreement_capability.entity_no->operating_entity.entity_no|n:1","agreement_capability_limit.agreement_capability_id->agreement_capability.agreement_capability_id|n:1","agreement_document_ref.agreement_id->agreement.agreement_id|n:1","agreement_operating_entity.agreement_id->agreement.agreement_id|n:1","agreement_operating_entity.entity_no->operating_entity.entity_no|n:1","agreement_party.agreement_id->agreement.agreement_id|n:1","agreement_party.evidence_document_id->agreement_document_ref.agreement_document_id|n:1","agreement_party.party_id->party.party_id|n:1","agreement_party.party_profile_id->party_profile.profile_id|n:1","agreement_product.agreement_id->agreement.agreement_id|n:1","agreement_product.product_code->product.product_code|n:1","application_document.client_application_id->client_application.application_id|n:1","application_document.operating_entity_application_id->operating_entity_application.application_id|n:1","application_document.partner_application_id->partner_application.application_id|n:1","application_document.subject_party_id->party.party_id|n:1","business_change_record.parent_history_id->business_change_record.history_id|n:1","business_change_version.history_id->business_change_record.history_id|n:1","client.party_id->party.party_id|1:1","client_application.client_id->client.client_id|n:1","client_business_classification.business_profile_id->client_business_profile.business_profile_id|n:1","client_business_profile.client_id->client.client_id|n:1","client_entity_affiliation.client_id->client.client_id|n:1","client_entity_affiliation.entity_no->operating_entity.entity_no|n:1","commercial_term_component.term_price_id->commercial_term_price.term_price_id|n:1","commercial_term_component_band.term_component_id->commercial_term_component.term_component_id|n:1","commercial_term_plan.agreement_id->agreement.agreement_id|1:1","commercial_term_plan.client_id->client.client_id|n:1","commercial_term_plan.entity_no->operating_entity.entity_no|n:1","commercial_term_plan.local_partner_id->local_partner.local_partner_id|n:1","commercial_term_price.entity_no->operating_entity.entity_no|n:1","commercial_term_price.pricing_profile_code->pricing_profile.pricing_profile_code|n:1","commercial_term_price.term_version_id->commercial_term_version.term_version_id|n:1","commercial_term_version.agreement_id->agreement.agreement_id|n:1","commercial_term_version.term_plan_id->commercial_term_plan.term_plan_id|n:1","consumer_legal_document.entity_no->operating_entity.entity_no|n:1","entity_source_relation.entity_no->operating_entity.entity_no|n:1","entity_source_relation.source_client_id->client.client_id|n:1","entity_source_relation.source_partner_id->local_partner.local_partner_id|n:1","individual_profile.profile_id->party_profile.profile_id|1:1","local_partner.party_id->party.party_id|1:1","operating_entity.party_id->party.party_id|1:1","operating_entity_application.approved_source_relation_id->entity_source_relation.source_relation_id|n:1","operating_entity_application.entity_no->operating_entity.entity_no|n:1","operating_entity_application.party_profile_id->party_profile.profile_id|n:1","operating_entity_application.source_client_id->client.client_id|n:1","operating_entity_application.source_partner_id->local_partner.local_partner_id|n:1","operating_entity_scope.application_id->operating_entity_application.application_id|n:1","operating_entity_scope.entity_no->operating_entity.entity_no|n:1","organization_profile.profile_id->party_profile.profile_id|1:1","partner_application.local_partner_id->local_partner.local_partner_id|n:1","partner_application.target_party_profile_id->party_profile.profile_id|n:1","party_address.profile_id->party_profile.profile_id|n:1","party_contact.profile_id->party_profile.profile_id|n:1","party_identity_key.party_id->party.party_id|n:1","party_profile.draft_application_id->client_application.application_id|n:1","party_profile.party_id->party.party_id|n:1","party_profile_identifier.identity_key_id->party_identity_key.identity_key_id|n:1","party_profile_identifier.profile_id->party_profile.profile_id|n:1","party_relation.client_application_id->client_application.application_id|n:1","party_relation.organization_party_id->party.party_id|n:1","party_relation.related_party_id->party.party_id|n:1","pricing_profile_cond.pricing_profile_id->pricing_profile.pricing_profile_id|n:1","product_capability.product_code->product.product_code|n:1","product_fee_event.product_code->product.product_code|n:1","seller_disclosure.entity_no->operating_entity.entity_no|n:1"]);
// Catalog references whose parent lives in client-master while the child lives in
// commercial: the field and access index stay, the PDManer correlation does not.
const expectedCrossDatabaseRelationSignatures = Object.freeze(["agreement.client_id->client.client_id|n:1","agreement.entity_no->operating_entity.entity_no|n:1","agreement.local_partner_id->local_partner.local_partner_id|n:1","agreement.referral_partner_id->local_partner.local_partner_id|n:1","agreement_capability.entity_no->operating_entity.entity_no|n:1","agreement_operating_entity.entity_no->operating_entity.entity_no|n:1","agreement_party.party_id->party.party_id|n:1","agreement_party.party_profile_id->party_profile.profile_id|n:1","commercial_term_plan.client_id->client.client_id|n:1","commercial_term_plan.entity_no->operating_entity.entity_no|n:1","commercial_term_plan.local_partner_id->local_partner.local_partner_id|n:1","commercial_term_price.entity_no->operating_entity.entity_no|n:1"]);
const expectedLocalRelationSignatures = Object.freeze(expectedRelationSignatures.filter(
  (signature) => !expectedCrossDatabaseRelationSignatures.includes(signature)));

const forbiddenExternalRelationFields = new Set([
  "agreement_acceptance.actor_id",
  "agreement_acceptance.evidence_file_id",
  "agreement_document_ref.file_id",
  "agreement_scope_binding.owner_id",
  "application_document.file_id",
  "business_change_record.aggregate_root_id",
  "business_change_record.record_id",
  "business_change_version.create_by",
  "client_application.due_diligence_case_id",
  "client_application.qualification_decision_id",
  "client_application.requested_by_account_id",
  "compensation_event.event_key",
  "consumer_legal_document_version.content_file_id",
  "operating_entity_application.due_diligence_case_id",
  "operating_entity_application.qualification_decision_id",
  "partner_application.due_diligence_case_id",
  "partner_application.qualification_decision_id",
  "partner_application.requested_by_account_id",
  "party_contact.contact_vault_token",
  "seller_disclosure_version.approval_trigger_id",
  "seller_disclosure_version.approved_by",
]);

const expectedFields = Object.freeze({
  "client_business_classification": ["classification_id","business_profile_id","industry_code","mcc_code","primary_slot","create_time","update_time"],
  "client_application": ["application_id","application_no","client_id","requested_by_account_id","status","active_slot","due_diligence_case_id","qualification_decision_id","qualification_type","submitted_time","create_time","update_time","current_submission_id"],
  "partner_application": ["application_id","application_no","local_partner_id","target_party_profile_id","requested_by_account_id","status","active_slot","due_diligence_case_id","qualification_decision_id","qualification_decision_version","qualification_type","submitted_time","submission_hash_schema_version","submission_content_hash","create_by","create_time","update_time"],
  "operating_entity_application": ["application_id","application_no","application_type","base_entity_change_version","entity_no","party_profile_id","source_type","source_client_id","source_partner_id","client_source_basis","status","active_slot","due_diligence_case_id","qualification_decision_id","qualification_decision_version","qualification_type","submitted_time","submission_hash_schema_version","submission_content_hash","approved_source_relation_id","create_time","update_time"],
  "operating_entity_scope": ["scope_id","entity_no","application_id","consumer_country_code","currency","industry_code","mcc_code","status","active_slot","effective_from","effective_to","create_time","update_time"],
  "application_document": ["document_id","client_application_id","partner_application_id","operating_entity_application_id","subject_party_id","requirement_code","document_type","file_id","content_hash","create_time","update_time"],
  "client_entity_affiliation": ["affiliation_id","client_id","entity_no","priority_no","status","active_slot","effective_from","effective_to","create_time","update_time"],
  "entity_source_relation": ["source_relation_id","entity_no","source_type","source_client_id","source_partner_id","client_source_basis","status","active_source_slot","effective_from","effective_to","create_time","update_time"],
  "agreement": ["agreement_id","agreement_no","agreement_name","agreement_type","business_mode","fund_mode","pricing_authority","referral_partner_id","client_id","local_partner_id","entity_no","agreement_format","status","effective_from","effective_to","signed_time","activated_time","create_by","create_time","update_time"],
  "agreement_party": ["agreement_party_id","agreement_id","party_id","party_profile_id","party_role","acceptance_method","accepted_by_actor_id","accepted_time","evidence_document_id","acceptance_reference","acceptance_ref_slot","acceptance_recorded_by","acceptance_recorded_at","create_by","create_time","update_time"],
  "agreement_document_ref": ["agreement_document_id","agreement_id","document_type","file_id","content_hash","create_time","update_time"],
  "agreement_operating_entity": ["agreement_operating_entity_id","agreement_id","entity_no","create_time","update_time"],
  "agreement_product": ["agreement_product_id","agreement_id","product_code","create_time","update_time"],
  "agreement_capability": ["agreement_capability_id","agreement_id","entity_no","capability_code","status","effective_from","effective_to","rolling_reserve_rate","rolling_reserve_days","create_time","update_time"],
  "agreement_capability_limit": ["agreement_capability_limit_id","agreement_capability_id","limit_code","limit_value","create_time","update_time"],
  "product": ["product_id","product_code","product_name","business_mode_scope","base_product","status","create_time","update_time"],
  "product_capability": ["product_capability_id","product_code","capability_code","capability_name","capability_kind","payment_method_code","dimension_code","dimension_value","status","create_time","update_time"],
  "product_fee_event": ["product_fee_event_id","product_code","fee_event_code","term_type","billing_fact_type","create_time","update_time"],
  "consumer_legal_document": ["legal_document_id","document_code","document_type","entity_no","country_code","locale","content_file_id","content_hash","status","effective_from","effective_to","approved_time","published_time","create_by","create_time","update_time"],
  "seller_disclosure": ["disclosure_id","entity_no","country_code","locale","public_legal_name","public_registration_number","public_tax_registration_number","public_address_text","public_support_email","public_support_phone","status","effective_from","effective_to","approved_time","published_time","create_by","create_time","update_time"],
  "pricing_profile": ["pricing_profile_id","pricing_profile_code","profile_name","profile_kind","payment_method_code","status","create_time","update_time"],
  "pricing_profile_cond": ["profile_condition_id","pricing_profile_id","dimension_code","condition_value","create_time","update_time"],
  "commercial_term_plan": ["term_plan_id","plan_code","term_type","agreement_id","entity_no","client_id","local_partner_id","status","create_time","update_time"],
  "commercial_term_version": ["term_version_id","term_plan_id","agreement_id","version_no","status","active_slot","effective_from","effective_to","rounding_mode","pricing_model","fee_tax_treatment","create_time","update_time"],
  "commercial_term_price": ["term_price_id","term_version_id","entity_no","currency","pricing_profile_code","priority_no","settlement_business_days","business_calendar_code","settlement_timezone","cutoff_second_of_day","create_time","update_time"],
  "commercial_term_component": ["term_component_id","term_price_id","component_no","component_code","component_type","trigger_code","calculation_base","refund_behavior","rate","fixed_amount","currency","min_amount","max_amount","band_basis","band_mode","create_time","update_time"],
  "commercial_term_component_band": ["term_band_id","term_component_id","band_no","lower_bound","upper_bound","rate","fixed_amount","create_time","update_time"]
});

const expectedFinalFields = Object.freeze({
  "party": ["party_id","party_no","party_type","create_time","update_time"],
  "party_profile": ["profile_id","party_id","content_hash","create_time","update_time","draft_application_id","approved_time"],
  "individual_profile": ["profile_id","given_name","middle_name","family_name","birth_date","nationality_country","residence_country","create_time","update_time"],
  "organization_profile": ["profile_id","legal_name","trade_name","incorporation_country","organization_type","incorporation_date","create_time","update_time"],
  "party_identity_key": ["identity_key_id","party_id","identifier_type","issuing_country","issuer_scope_key","normalized_identifier_value","create_time","update_time"],
  "party_profile_identifier": ["profile_identifier_id","profile_id","identity_key_id","valid_from","valid_to","create_time","update_time","identifier_type","issuing_country","issuer_scope_key","identifier_value"],
  "party_address": ["address_id","profile_id","address_type","address_line1","address_line2","city","state_or_province","postal_code","country_code","create_time","update_time"],
  "party_contact": ["contact_id","profile_id","contact_type","contact_vault_token","contact_masked_value","create_time","update_time"],
  "party_relation": ["relation_id","organization_party_id","related_party_id","relation_type","ownership_percentage","effective_from","effective_to","status","create_time","update_time","client_application_id"],
  "client": ["client_id","client_no","party_id","status","activation_time","suspension_time","termination_time","create_time","update_time"],
  "local_partner": ["local_partner_id","partner_no","party_id","status","activation_time","termination_time","create_time","update_time"],
  "operating_entity": ["entity_no","party_id","operating_country","status","create_time","update_time"],
  "client_business_profile": ["business_profile_id","client_id","business_model_code","website_url","expected_volume","expected_ticket","currency","content_hash","create_time","update_time","business_description","classification_case_id","classification_confirmed_by","classification_confirmed_time"],
  "client_business_classification": ["classification_id","business_profile_id","industry_code","mcc_code","primary_slot","create_time","update_time"],
  "client_application": ["application_id","application_no","client_id","requested_by_account_id","status","active_slot","due_diligence_case_id","qualification_decision_id","qualification_type","submitted_time","create_time","update_time","current_submission_id"],
  "partner_application": ["application_id","application_no","local_partner_id","target_party_profile_id","requested_by_account_id","status","active_slot","due_diligence_case_id","qualification_decision_id","qualification_decision_version","qualification_type","submitted_time","submission_hash_schema_version","submission_content_hash","create_by","create_time","update_time"],
  "operating_entity_application": ["application_id","application_no","application_type","base_entity_change_version","entity_no","party_profile_id","source_type","source_client_id","source_partner_id","client_source_basis","status","active_slot","due_diligence_case_id","qualification_decision_id","qualification_decision_version","qualification_type","submitted_time","submission_hash_schema_version","submission_content_hash","approved_source_relation_id","create_time","update_time"],
  "operating_entity_scope": ["scope_id","entity_no","application_id","consumer_country_code","currency","industry_code","mcc_code","status","active_slot","effective_from","effective_to","create_time","update_time"],
  "application_document": ["document_id","client_application_id","partner_application_id","operating_entity_application_id","subject_party_id","requirement_code","document_type","file_id","content_hash","create_time","update_time"],
  "client_entity_affiliation": ["affiliation_id","client_id","entity_no","priority_no","status","active_slot","effective_from","effective_to","create_time","update_time"],
  "entity_source_relation": ["source_relation_id","entity_no","source_type","source_client_id","source_partner_id","client_source_basis","status","active_source_slot","effective_from","effective_to","create_time","update_time"],
  "agreement": ["agreement_id","agreement_no","agreement_name","agreement_type","business_mode","fund_mode","pricing_authority","referral_partner_id","client_id","local_partner_id","entity_no","agreement_format","status","effective_from","effective_to","signed_time","activated_time","create_by","create_time","update_time"],
  "agreement_party": ["agreement_party_id","agreement_id","party_id","party_profile_id","party_role","acceptance_method","accepted_by_actor_id","accepted_time","evidence_document_id","acceptance_reference","acceptance_ref_slot","acceptance_recorded_by","acceptance_recorded_at","create_by","create_time","update_time"],
  "agreement_document_ref": ["agreement_document_id","agreement_id","document_type","file_id","content_hash","create_time","update_time"],
  "agreement_operating_entity": ["agreement_operating_entity_id","agreement_id","entity_no","create_time","update_time"],
  "agreement_product": ["agreement_product_id","agreement_id","product_code","create_time","update_time"],
  "agreement_capability": ["agreement_capability_id","agreement_id","entity_no","capability_code","status","effective_from","effective_to","rolling_reserve_rate","rolling_reserve_days","create_time","update_time"],
  "agreement_capability_limit": ["agreement_capability_limit_id","agreement_capability_id","limit_code","limit_value","create_time","update_time"],
  "product": ["product_id","product_code","product_name","business_mode_scope","base_product","status","create_time","update_time"],
  "product_capability": ["product_capability_id","product_code","capability_code","capability_name","capability_kind","payment_method_code","dimension_code","dimension_value","status","create_time","update_time"],
  "product_fee_event": ["product_fee_event_id","product_code","fee_event_code","term_type","billing_fact_type","create_time","update_time"],
  "consumer_legal_document": ["legal_document_id","document_code","document_type","entity_no","country_code","locale","content_file_id","content_hash","status","effective_from","effective_to","approved_time","published_time","create_by","create_time","update_time"],
  "seller_disclosure": ["disclosure_id","entity_no","country_code","locale","public_legal_name","public_registration_number","public_tax_registration_number","public_address_text","public_support_email","public_support_phone","status","effective_from","effective_to","approved_time","published_time","create_by","create_time","update_time"],
  "pricing_profile": ["pricing_profile_id","pricing_profile_code","profile_name","profile_kind","payment_method_code","status","create_time","update_time"],
  "pricing_profile_cond": ["profile_condition_id","pricing_profile_id","dimension_code","condition_value","create_time","update_time"],
  "commercial_term_plan": ["term_plan_id","plan_code","term_type","agreement_id","entity_no","client_id","local_partner_id","status","create_time","update_time"],
  "commercial_term_version": ["term_version_id","term_plan_id","agreement_id","version_no","status","active_slot","effective_from","effective_to","rounding_mode","pricing_model","fee_tax_treatment","create_time","update_time"],
  "commercial_term_price": ["term_price_id","term_version_id","entity_no","currency","pricing_profile_code","priority_no","settlement_business_days","business_calendar_code","settlement_timezone","cutoff_second_of_day","create_time","update_time"],
  "commercial_term_component": ["term_component_id","term_price_id","component_no","component_code","component_type","trigger_code","calculation_base","refund_behavior","rate","fixed_amount","currency","min_amount","max_amount","band_basis","band_mode","create_time","update_time"],
  "commercial_term_component_band": ["term_band_id","term_component_id","band_no","lower_bound","upper_bound","rate","fixed_amount","create_time","update_time"],
  "business_change_record": ["history_id","record_type","record_id","aggregate_root_type","aggregate_root_id","parent_history_id","current_version","create_time","update_time"],
  "business_change_version": ["change_version_id","history_id","version_no","operation_type","snapshot_json","snapshot_format","create_by","create_time"],
  "compensation_event": ["id","event_key","event_type","sub_event_type","payload_json","execute_status","execute_count","next_execute_at","lock_owner","lock_until","last_error_code","last_error_summary","lock_version","create_time","update_time"]
});

const forbiddenTargetFieldKeys = Object.freeze(["current_version_id","document_version","primary_flag","fallback_flag","path_version","path_length","version"]);

const activeSlotVersions = Object.freeze([
  ["commercial_term_version", "term_plan_id"],
]);

const legacyExpectedNullableFieldPaths = Object.freeze([
  "agreement_acceptance.actor_id",
  "agreement_acceptance.evidence_content_hash",
  "agreement_acceptance.evidence_file_id",
  "agreement_scope_binding.active_slot",
  "agreement_scope_binding.effective_to",
  "agreement_scope_binding.entity_no",
  "agreement_version.active_slot",
  "agreement_version.effective_to",
  "agreement_version.supersedes_version_id",
  "application_document.client_application_id",
  "application_document.partner_application_id",
  "application_document.operating_entity_application_id",
  "business_change_record.parent_history_id",
  "client.activation_time",
  "client.suspension_time",
  "client.termination_time",
  "client_application.active_slot",
  "client_application.base_business_profile_id",
  "client_application.base_party_profile_id",
  "client_application.due_diligence_case_id",
  "client_application.qualification_decision_id",
  "client_application.qualification_decision_version",
  "client_application.submitted_time",
  "client_application.requested_by_account_id",
  "client_business_classification.primary_slot",
  "client_business_profile.active_slot",
  "client_business_profile.effective_to",
  "client_business_profile.website_url",
  "client_entity_affiliation.active_slot",
  "client_entity_affiliation.effective_to",
  "client_entity_assignment.active_slot",
  "client_entity_assignment.effective_to",
  "commercial_term_component.currency",
  "commercial_term_component.band_mode",
  "commercial_term_component.fixed_amount",
  "commercial_term_component.max_amount",
  "commercial_term_component.min_amount",
  "commercial_term_component.rate",
  "commercial_term_plan.client_id",
  "commercial_term_plan.local_partner_id",
  "commercial_term_component_band.upper_bound",
  "commercial_term_component_band.rate",
  "commercial_term_component_band.fixed_amount",
  "commercial_term_version.active_slot",
  "commercial_term_version.effective_to",
  "compensation_event.last_error_code",
  "compensation_event.last_error_summary",
  "compensation_event.lock_owner",
  "compensation_event.lock_until","compensation_event.payload_json",
  "consumer_legal_document.client_id",
  "consumer_legal_document_version.active_slot",
  "consumer_legal_document_version.effective_to",
  "entity_assignment_rule.active_slot",
  "entity_assignment_rule.effective_to",
  "entity_source_relation.active_source_slot",
  "entity_source_relation.client_source_basis",
  "entity_source_relation.effective_to",
  "entity_source_relation.source_client_id",
  "entity_source_relation.source_partner_id",
  "individual_profile.middle_name",
  "local_partner.activation_time",
  "local_partner.termination_time",
  "operating_entity_application.active_slot",
  "operating_entity_application.approved_source_relation_id",
  "operating_entity_application.client_source_basis",
  "operating_entity_application.due_diligence_case_id",
  "operating_entity_application.qualification_decision_id",
  "operating_entity_application.qualification_decision_version",
  "operating_entity_application.source_client_id",
  "operating_entity_application.source_partner_id",
  "operating_entity_application.submitted_time",
  "partner_application.active_slot",
  "partner_application.due_diligence_case_id",
  "partner_application.qualification_decision_id",
  "partner_application.qualification_decision_version",
  "partner_application.submitted_time",
  "organization_profile.trade_name",
  "party_address.address_line2",
  "party_address.postal_code",
  "party_address.state_or_province",
  "party_identity_key.issuing_country",
  "party_profile.active_slot",
  "party_profile.effective_to",
  "party_profile_identifier.valid_to",
  "party_relation.active_slot",
  "party_relation.effective_to",
  "party_relation.ownership_percentage",
  "seller_disclosure_version.active_slot",
  "seller_disclosure_version.approval_trigger_id",
  "seller_disclosure_version.approved_by",
  "seller_disclosure_version.effective_to",
  "seller_disclosure_version.public_tax_registration_number",
].sort());
const agreementLegalTableKeys = new Set([
  "agreement",
  "agreement_party",
  "agreement_document_ref",
  "agreement_acceptance",
  "agreement_operating_entity",
  "consumer_legal_document",
  "seller_disclosure",
  ...removedAgreementLegalTables,
]);
const expectedAgreementLegalNullableFieldPaths = Object.freeze(["agreement.activated_time","agreement.client_id","agreement.effective_from","agreement.effective_to","agreement.entity_no","agreement.local_partner_id","agreement.signed_time","agreement_party.acceptance_method","agreement_party.acceptance_recorded_at","agreement_party.acceptance_recorded_by","agreement_party.acceptance_ref_slot","agreement_party.acceptance_reference","agreement_party.accepted_by_actor_id","agreement_party.accepted_time","agreement_party.evidence_document_id","consumer_legal_document.approved_time","consumer_legal_document.effective_to","consumer_legal_document.published_time","seller_disclosure.approved_time","seller_disclosure.effective_to","seller_disclosure.public_support_phone","seller_disclosure.public_tax_registration_number","seller_disclosure.published_time"]);
const expectedNullableFieldPaths = Object.freeze(["agreement.activated_time","agreement.business_mode","agreement.client_id","agreement.effective_from","agreement.effective_to","agreement.entity_no","agreement.fund_mode","agreement.local_partner_id","agreement.pricing_authority","agreement.referral_partner_id","agreement.signed_time","agreement_capability.effective_to","agreement_capability.rolling_reserve_days","agreement_capability.rolling_reserve_rate","agreement_party.acceptance_method","agreement_party.acceptance_recorded_at","agreement_party.acceptance_recorded_by","agreement_party.acceptance_ref_slot","agreement_party.acceptance_reference","agreement_party.accepted_by_actor_id","agreement_party.accepted_time","agreement_party.evidence_document_id","application_document.client_application_id","application_document.operating_entity_application_id","application_document.partner_application_id","business_change_record.parent_history_id","client.activation_time","client.suspension_time","client.termination_time","client_application.active_slot","client_application.current_submission_id","client_application.due_diligence_case_id","client_application.qualification_decision_id","client_application.submitted_time","client_business_classification.primary_slot","client_business_profile.business_description","client_business_profile.business_model_code","client_business_profile.classification_case_id","client_business_profile.classification_confirmed_by","client_business_profile.classification_confirmed_time","client_business_profile.content_hash","client_business_profile.currency","client_business_profile.expected_ticket","client_business_profile.expected_volume","client_business_profile.website_url","client_entity_affiliation.active_slot","client_entity_affiliation.effective_to","commercial_term_component.band_basis","commercial_term_component.band_mode","commercial_term_component.currency","commercial_term_component.fixed_amount","commercial_term_component.max_amount","commercial_term_component.min_amount","commercial_term_component.rate","commercial_term_component_band.fixed_amount","commercial_term_component_band.rate","commercial_term_component_band.upper_bound","commercial_term_plan.agreement_id","commercial_term_plan.client_id","commercial_term_plan.entity_no","commercial_term_plan.local_partner_id","commercial_term_price.business_calendar_code","commercial_term_price.cutoff_second_of_day","commercial_term_price.settlement_business_days","commercial_term_price.settlement_timezone","commercial_term_version.active_slot","commercial_term_version.effective_to","compensation_event.last_error_code","compensation_event.last_error_summary","compensation_event.lock_owner","compensation_event.lock_until","compensation_event.payload_json","consumer_legal_document.approved_time","consumer_legal_document.effective_to","consumer_legal_document.published_time","entity_source_relation.active_source_slot","entity_source_relation.client_source_basis","entity_source_relation.effective_to","entity_source_relation.source_client_id","entity_source_relation.source_partner_id","individual_profile.birth_date","individual_profile.family_name","individual_profile.given_name","individual_profile.middle_name","individual_profile.nationality_country","individual_profile.residence_country","local_partner.activation_time","local_partner.termination_time","operating_entity_application.active_slot","operating_entity_application.approved_source_relation_id","operating_entity_application.client_source_basis","operating_entity_application.due_diligence_case_id","operating_entity_application.qualification_decision_id","operating_entity_application.qualification_decision_version","operating_entity_application.source_client_id","operating_entity_application.source_partner_id","operating_entity_application.submitted_time","operating_entity_scope.active_slot","operating_entity_scope.effective_from","operating_entity_scope.effective_to","organization_profile.incorporation_country","organization_profile.incorporation_date","organization_profile.legal_name","organization_profile.organization_type","organization_profile.trade_name","partner_application.active_slot","partner_application.due_diligence_case_id","partner_application.qualification_decision_id","partner_application.qualification_decision_version","partner_application.submitted_time","party_address.address_line1","party_address.address_line2","party_address.city","party_address.country_code","party_address.postal_code","party_address.state_or_province","party_identity_key.issuing_country","party_profile.approved_time","party_profile.content_hash","party_profile.draft_application_id","party_profile_identifier.identifier_type","party_profile_identifier.identifier_value","party_profile_identifier.identity_key_id","party_profile_identifier.issuer_scope_key","party_profile_identifier.issuing_country","party_profile_identifier.valid_from","party_profile_identifier.valid_to","party_relation.client_application_id","party_relation.effective_to","party_relation.ownership_percentage","pricing_profile.payment_method_code","product_capability.dimension_code","product_capability.dimension_value","product_capability.payment_method_code","seller_disclosure.approved_time","seller_disclosure.effective_to","seller_disclosure.public_support_phone","seller_disclosure.public_tax_registration_number","seller_disclosure.published_time"]);

const applicationStatuses = Object.freeze(["DRAFT","SUBMITTED","UNDER_REVIEW","APPROVED","REJECTED","WITHDRAWN"]);

const expectedBcrRecordTypes = Object.freeze(["PARTY","CLIENT","LOCAL_PARTNER","OPERATING_ENTITY","PARTY_PROFILE","CLIENT_BUSINESS_PROFILE","COMMERCIAL_TERM_VERSION","CLIENT_APPLICATION","PARTNER_APPLICATION","OPERATING_ENTITY_APPLICATION","PARTY_RELATION","CLIENT_ENTITY_AFFILIATION","ENTITY_SOURCE_RELATION","AGREEMENT","CONSUMER_LEGAL_DOCUMENT","SELLER_DISCLOSURE","COMMERCIAL_TERM_PLAN","PAYMENT_METHOD_PROFILE"]);

const expectedBcrRootTypes = Object.freeze(["PARTY","CLIENT","LOCAL_PARTNER","OPERATING_ENTITY","CLIENT_APPLICATION","PARTNER_APPLICATION","OPERATING_ENTITY_APPLICATION","PARTY_RELATION","CLIENT_ENTITY_AFFILIATION","ENTITY_SOURCE_RELATION","AGREEMENT","CONSUMER_LEGAL_DOCUMENT","SELLER_DISCLOSURE","COMMERCIAL_TERM_PLAN","PAYMENT_METHOD_PROFILE"]);

const expectedBcrExcludedBusinessTables = Object.freeze(["individual_profile","organization_profile","party_identity_key","party_profile_identifier","party_address","party_contact","client_business_classification","application_document","operating_entity_scope","agreement_party","agreement_document_ref","agreement_operating_entity","pricing_profile_cond","commercial_term_price","commercial_term_component","commercial_term_component_band","agreement_capability_limit","product","product_capability","product_fee_event"]);

const expectedClosedEnumValues = Object.freeze({
  "party.party_type": ["INDIVIDUAL","ORGANIZATION"],
  "party_contact.contact_type": ["EMAIL","PHONE"],
  "party_relation.relation_type": ["OWNERSHIP","CONTROL","DIRECTOR","SIGNATORY","UBO"],
  "party_relation.status": ["DRAFT","ACTIVE","ENDED"],
  "client.status": ["PENDING","ACTIVE","SUSPENDED","TERMINATED"],
  "local_partner.status": ["PENDING","ACTIVE","SUSPENDED","TERMINATED"],
  "operating_entity.status": ["PENDING","ACTIVE","SUSPENDED","CLOSED"],
  "client_application.status": ["DRAFT","SUBMITTED","UNDER_REVIEW","RETURNED","APPROVED","REJECTED","WITHDRAWN"],
  "client_application.qualification_type": ["INDIVIDUAL_KYC","ORGANIZATION_KYB"],
  "partner_application.status": ["DRAFT","SUBMITTED","UNDER_REVIEW","APPROVED","REJECTED","WITHDRAWN"],
  "partner_application.qualification_type": ["PARTNER_DUE_DILIGENCE"],
  "operating_entity_application.source_type": ["PLATFORM","CLIENT","PARTNER"],
  "operating_entity_application.client_source_basis": ["SAME_PARTY","VERIFIED_ASSOCIATION"],
  "operating_entity_application.status": ["DRAFT","SUBMITTED","UNDER_REVIEW","APPROVED","REJECTED","WITHDRAWN"],
  "operating_entity_application.qualification_type": ["ENTITY_UNDERWRITING","ENTITY_SCOPE_REVIEW"],
  "client_entity_affiliation.status": ["ACTIVE","ENDED"],
  "entity_source_relation.source_type": ["PLATFORM","CLIENT","PARTNER"],
  "entity_source_relation.client_source_basis": ["SAME_PARTY","VERIFIED_ASSOCIATION"],
  "entity_source_relation.status": ["ACTIVE","ENDED"],
  "agreement.agreement_type": ["CLIENT","PARTNER"],
  "consumer_legal_document.document_type": ["CONSUMER_TERMS","REFUND_POLICY","PRIVACY_NOTICE"],
  "commercial_term_plan.status": ["DRAFT","ACTIVE","RETIRED"],
  "commercial_term_version.status": ["DRAFT","PENDING_APPROVAL","APPROVED","SCHEDULED","ACTIVE","SUPERSEDED","EXPIRED"],
  "commercial_term_version.rounding_mode": ["HALF_UP"],
  "commercial_term_version.pricing_model": ["BLENDED","INTERCHANGE_PLUS_PLUS"],
  "commercial_term_version.fee_tax_treatment": ["INCLUSIVE","EXCLUSIVE"],
  "pricing_profile.status": ["DRAFT","ACTIVE","RETIRED"],
  "pricing_profile.profile_kind": ["PAYMENT_METHOD","PAYOUT_RAIL","FX_PAIR","DISPUTE","PERIODIC"],
  "pricing_profile_cond.dimension_code": ["CARD_BRAND","FUNDING_TYPE","CARD_LEVEL","CARD_PRODUCT","ISSUER_REGION","ISSUER_COUNTRY","THREE_DS_RESULT","CURRENCY","CONSUMER_COUNTRY","TRANSACTION_TYPE","PAYOUT_RAIL","DESTINATION_COUNTRY","CURRENCY_PAIR","DISPUTE_STAGE"],
  "commercial_term_component.component_type": ["PERCENTAGE","FIXED","TIERED"],
  "commercial_term_component.calculation_base": ["GROSS_AMOUNT","NET_AMOUNT","ACTUAL_CLIENT_FEE","FIXED_PER_EVENT","BUY_RATE_DIFFERENCE"],
  "commercial_term_component.refund_behavior": ["RETURN_ALL","RETURN_PRORATA","KEEP"],
  "commercial_term_component.band_basis": ["TRANSACTION_AMOUNT","PERIOD_VOLUME"],
  "commercial_term_component.band_mode": ["WHOLE_BASE","PROGRESSIVE","RETROACTIVE"],
  "business_change_record.record_type": ["PARTY","CLIENT","LOCAL_PARTNER","OPERATING_ENTITY","PARTY_PROFILE","CLIENT_BUSINESS_PROFILE","COMMERCIAL_TERM_VERSION","CLIENT_APPLICATION","PARTNER_APPLICATION","OPERATING_ENTITY_APPLICATION","PARTY_RELATION","CLIENT_ENTITY_AFFILIATION","ENTITY_SOURCE_RELATION","AGREEMENT","CONSUMER_LEGAL_DOCUMENT","SELLER_DISCLOSURE","COMMERCIAL_TERM_PLAN","PAYMENT_METHOD_PROFILE"],
  "business_change_record.aggregate_root_type": ["PARTY","CLIENT","LOCAL_PARTNER","OPERATING_ENTITY","CLIENT_APPLICATION","PARTNER_APPLICATION","OPERATING_ENTITY_APPLICATION","PARTY_RELATION","CLIENT_ENTITY_AFFILIATION","ENTITY_SOURCE_RELATION","AGREEMENT","CONSUMER_LEGAL_DOCUMENT","SELLER_DISCLOSURE","COMMERCIAL_TERM_PLAN","PAYMENT_METHOD_PROFILE"],
  "business_change_version.operation_type": ["CREATE","UPDATE","DELETE"],
  "compensation_event.execute_status": ["INIT","PROCESSING","ERROR","FAILED"],
  "agreement.status": ["DRAFT","PENDING_SIGNATURE","SIGNED","ACTIVE","EXPIRED","TERMINATED","CANCELLED"],
  "agreement_party.party_role": ["CLIENT","LOCAL_PARTNER","PLATFORM_SIGNATORY","OPERATING_ENTITY"],
  "agreement_document_ref.document_type": ["SIGNING_DOCUMENT","ANNEX","EXECUTED_DOCUMENT","TERMINATION_NOTICE"],
  "consumer_legal_document.status": ["DRAFT","APPROVED","ACTIVE","EXPIRED","WITHDRAWN"],
  "seller_disclosure.status": ["DRAFT","APPROVED","ACTIVE","EXPIRED","WITHDRAWN"],
  "operating_entity_application.application_type": ["ONBOARDING","SCOPE_CHANGE"],
  "operating_entity_scope.status": ["DRAFT","ACTIVE","ENDED"],
  "agreement.agreement_format": ["MERGED_PARTY","LEGACY_ROLE_SIGNATURE"],
  "business_change_version.snapshot_format": ["ENTITY_NO","LEGACY_NUMERIC"],
  "agreement_party.acceptance_method": ["E_SIGNATURE","CLICK_ACCEPTANCE","MANUAL_SIGNATURE"],
  "agreement.business_mode": ["MOR","PSP"],
  "agreement.fund_mode": ["SETTLE_THROUGH_PLATFORM","FEES_ONLY"],
  "agreement.pricing_authority": ["PLATFORM","PARTNER"],
  "product.product_code": ["ACQUIRING","PAYOUT","FX","DISPUTE","TAX_INVOICE","DATA_ANALYTICS","PLATFORM"],
  "product.business_mode_scope": ["MOR_ONLY","PSP_ONLY","BOTH"],
  "product.status": ["DRAFT","ACTIVE","RETIRED"],
  "product_capability.product_code": ["ACQUIRING","PAYOUT","FX","DISPUTE","TAX_INVOICE","DATA_ANALYTICS","PLATFORM"],
  "product_capability.capability_kind": ["PAYMENT_METHOD","PAYOUT_RAIL","FX","DISPUTE","PERIODIC_SERVICE"],
  "product_capability.dimension_code": ["CARD_BRAND","FUNDING_TYPE","CARD_LEVEL","CARD_PRODUCT","ISSUER_REGION","ISSUER_COUNTRY","THREE_DS_RESULT","CURRENCY","CONSUMER_COUNTRY","TRANSACTION_TYPE","PAYOUT_RAIL","DESTINATION_COUNTRY","CURRENCY_PAIR","DISPUTE_STAGE"],
  "product_capability.status": ["DRAFT","ACTIVE","RETIRED"],
  "product_fee_event.product_code": ["ACQUIRING","PAYOUT","FX","DISPUTE","TAX_INVOICE","DATA_ANALYTICS","PLATFORM"],
  "agreement_product.product_code": ["ACQUIRING","PAYOUT","FX","DISPUTE","TAX_INVOICE","DATA_ANALYTICS","PLATFORM"],
  "agreement_capability.status": ["ENABLED","SUSPENDED","DISABLED"],
  "agreement_capability_limit.limit_code": ["CURRENCY","CONSUMER_COUNTRY","REQUIRE_3DS"]
});

const expectedOpenCodeFields = Object.freeze(["agreement_capability.capability_code","application_document.document_type","application_document.requirement_code","client_business_classification.industry_code","client_business_classification.mcc_code","client_business_profile.business_model_code","client_business_profile.currency","commercial_term_component.currency","commercial_term_component.trigger_code","commercial_term_plan.term_type","commercial_term_price.business_calendar_code","commercial_term_price.currency","commercial_term_price.pricing_profile_code","commercial_term_price.settlement_timezone","compensation_event.event_type","compensation_event.last_error_code","compensation_event.sub_event_type","consumer_legal_document.country_code","consumer_legal_document.locale","individual_profile.nationality_country","individual_profile.residence_country","operating_entity.operating_country","operating_entity_scope.consumer_country_code","operating_entity_scope.currency","operating_entity_scope.industry_code","operating_entity_scope.mcc_code","organization_profile.incorporation_country","organization_profile.organization_type","party_address.address_type","party_address.country_code","party_identity_key.identifier_type","party_identity_key.issuing_country","party_profile_identifier.identifier_type","party_profile_identifier.issuing_country","pricing_profile.payment_method_code","pricing_profile_cond.condition_value","product_capability.capability_code","product_capability.payment_method_code","product_fee_event.billing_fact_type","product_fee_event.fee_event_code","product_fee_event.term_type","seller_disclosure.country_code","seller_disclosure.locale"]);

const expectedBusinessIdentifierCodeFields = Object.freeze(["commercial_term_component.component_code","commercial_term_plan.plan_code","pricing_profile.pricing_profile_code","consumer_legal_document.document_code"]);

const expectedConditionalValueRules = Object.freeze({
  "client_business_classification.primary_slot": "值为 1 时表示一组主要经营分类，其他情况为 NULL",
  "client_application.active_slot": "DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED 为 1；APPROVED、REJECTED、WITHDRAWN 为 NULL，禁止 0",
  "partner_application.active_slot": "状态为 DRAFT、SUBMITTED 或 UNDER_REVIEW 时为 1，终态为 NULL",
  "operating_entity_application.active_slot": "状态为 DRAFT、SUBMITTED 或 UNDER_REVIEW 时为 1，终态为 NULL",
  "client_entity_affiliation.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "entity_source_relation.active_source_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "commercial_term_version.active_slot": "状态为 ACTIVE 时为 1，其他状态为 NULL",
  "operating_entity_scope.active_slot": "仅ACTIVE为1，DRAFT和ENDED为NULL",
  "agreement_party.acceptance_ref_slot": "同合同Party最小角色主键行签后为1，其他角色及未签时为NULL"
});

const versionContracts = Object.freeze({
  commercial_term_version: {
    rootKey: "term_plan_id",
    statuses: ["DRAFT", "PENDING_APPROVAL", "APPROVED", "SCHEDULED", "ACTIVE", "SUPERSEDED", "EXPIRED"],
  },
});

const applicationDocumentXor =
  "(client_application_id IS NOT NULL) + (partner_application_id IS NOT NULL) + (operating_entity_application_id IS NOT NULL) = 1";
const clientApplicationProfileMatrix =
  "CHECK ((application_type='ONBOARDING' AND base_party_profile_id IS NULL AND base_business_profile_id IS NULL AND target_party_profile_id IS NOT NULL AND target_business_profile_id IS NOT NULL) OR (application_type='PROFILE_CHANGE' AND base_party_profile_id IS NOT NULL AND base_business_profile_id IS NOT NULL AND target_party_profile_id IS NOT NULL AND target_business_profile_id IS NOT NULL AND (target_party_profile_id<>base_party_profile_id OR target_business_profile_id<>base_business_profile_id)))";
const operatingEntityApprovalSourceMatrix =
  "CHECK ((status='APPROVED' AND approved_source_relation_id IS NOT NULL) OR (status<>'APPROVED' AND approved_source_relation_id IS NULL))";
const partyRelationBoundaryClauses = Object.freeze(["稳定关系标识；Client 未批准资料只允许所属可编辑申请维护，批准后关系集合锁定。","真实关系生效/结束日期保留；非 Client 历史引用不得自动迁移或归并。"]);
const verifiedAssociationClause =
  "CLIENT 来源依据：SAME_PARTY 要求 Client Party=Operating Entity Party；VERIFIED_ASSOCIATION 要求两个 Party 不同，并由已批准申请、冻结材料和匹配的 Risk Qualification Decision 支撑；非 CLIENT 要求 client_source_basis IS NULL。";
const expectedRevisionFields = Object.freeze({

});

const expectedBusinessChangeFields = Object.freeze({
  "business_change_record": ["history_id","record_type","record_id","aggregate_root_type","aggregate_root_id","parent_history_id","current_version","create_time","update_time"],
  "business_change_version": ["change_version_id","history_id","version_no","operation_type","snapshot_json","snapshot_format","create_by","create_time"]
});

const expectedBusinessChangePhysicalFields = Object.freeze({
  "business_change_record.history_id": ["BIGINT","",true],
  "business_change_record.record_type": ["VARCHAR",64,true],
  "business_change_record.record_id": ["VARCHAR",64,true],
  "business_change_record.aggregate_root_type": ["VARCHAR",64,true],
  "business_change_record.aggregate_root_id": ["VARCHAR",64,true],
  "business_change_record.parent_history_id": ["BIGINT","",false],
  "business_change_record.current_version": ["BIGINT","",true],
  "business_change_record.create_time": ["DATETIME",6,true],
  "business_change_record.update_time": ["DATETIME",6,true],
  "business_change_version.change_version_id": ["BIGINT","",true],
  "business_change_version.history_id": ["BIGINT","",true],
  "business_change_version.version_no": ["BIGINT","",true],
  "business_change_version.operation_type": ["VARCHAR",64,true],
  "business_change_version.snapshot_json": ["JSON","",true],
  "business_change_version.snapshot_format": ["VARCHAR",32,true],
  "business_change_version.create_by": ["BIGINT","",true],
  "business_change_version.create_time": ["DATETIME",6,true]
});

const expectedBusinessChangeTransactionClauses = Object.freeze(["Record Type 白名单由 record_type 字段闭合枚举定义；白名单外不创建 BCR。","非审计 Child 使用冻结的结构化事实或所属版本保留历史，完整目录见 BCR_EXCLUDED_BUSINESS_TABLES。","CREATE 原子协议：创建受审计聚合时，必须在同一事务插入结构化业务数据、Root business_change_record(current_version=1)、Root Version 1 CREATE Snapshot、白名单内且本次确需审计的 Child Record/Snapshot 与本地 compensation_event。","Root UPDATE CAS：外部命令必须携带 expected_change_version，固定 next_version=expected_change_version+1；Root business_change_record 必须以 current_version=expected_change_version 条件 CAS 到 next_version，影响行数必须为 1。","受审计 Child 版本指针：只有 Record Type 白名单内且本次发生需留档变化的 Child 才追加最终 Snapshot，并把 current_version 推进到 Root next_version；Root CAS 已串行化同聚合写入，Child current_version 不是独立 CAS Token。","非审计 Child：白名单外 Child 不创建 BCR、不接收 expected_change_version，也不写重复 Snapshot；其 Draft 改动由所属 Root CAS 保护，冻结后历史由结构化 Version 或追加式领域证据保留。","DELETE 顺序：删除白名单内受审计业务行前必须先追加包含稳定身份与删除前安全字段的 DELETE Snapshot，再删除结构化行；Record 永久保留。","System Transition 协议：定时激活、过期、撤销和系统回调必须作为锁内读取 expected_change_version 的 System Command，执行同一 Root CAS/History/Event 协议，不得绕过审计版本。","Multi-Root 原子协议：受影响 Root 必须按 (aggregate_root_type,aggregate_root_id) 固定顺序锁定和 CAS；每个 Root 独立计算 next_version 并写 Root/白名单内 Changed Child Snapshot，所有 Snapshot 使用同一 _meta.triggerId 关联。","整体回滚：任一 Root CAS、业务校验、结构化业务更新、应写 History Snapshot 或 compensation_event 登记失败，整个本地事务必须回滚。","Event 原子与版本：权威业务变化、business_change_record/business_change_version History 与每个 compensation_event 必须在同一本地事务提交；每个 Event.source_version 必须等于它所代表 Root 的 next_version，不得借用其他 Root 版本。"]);

const expectedCompensationFields = Object.freeze(["id","event_key","event_type","sub_event_type","payload_json","execute_status","execute_count","next_execute_at","lock_owner","lock_until","last_error_code","last_error_summary","lock_version","create_time","update_time"]);

const expectedCompensationPhysicalFields = Object.freeze({
  "id": ["BIGINT","",true,""],
  "event_key": ["VARCHAR",128,true,""],
  "event_type": ["VARCHAR",64,true,""],
  "sub_event_type": ["VARCHAR",64,true,""],
  "payload_json": ["VARCHAR",4096,false,""],
  "execute_status": ["VARCHAR",16,true,"'INIT'"],
  "execute_count": ["INT","",true,"0"],
  "next_execute_at": ["DATETIME",6,true,"CURRENT_TIMESTAMP(6)"],
  "lock_owner": ["VARCHAR",64,false,""],
  "lock_until": ["DATETIME",6,false,""],
  "last_error_code": ["VARCHAR",64,false,""],
  "last_error_summary": ["VARCHAR",1000,false,""],
  "lock_version": ["BIGINT","",true,"1"],
  "create_time": ["DATETIME",6,true,"CURRENT_TIMESTAMP(6)"],
  "update_time": ["DATETIME",6,true,"CURRENT_TIMESTAMP(6)"]
});

const expectedCompensationExecutionClauses = Object.freeze(["Payload 最小化：payload_json 可空或为最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。","Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。","Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。","登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。","成功删除：Handler 只有取得确定成功结果后才允许删除 compensation_event；MQ Handler 必须等待 Broker Confirm。","失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。","Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。"]);

const mysqlMappingId = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const javaMappingId = "797A1496-D649-4261-89B4-544132EC3F36";
const myBatisMappingId = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";

const expectedTypeMappings = Object.freeze({
  "string": ["VARCHAR","String","String"],
  "double": ["DECIMAL","BigDecimal","BigDecimal"],
  "int": ["INT","Integer","Integer"],
  "date": ["DATETIME","Instant","Instant"],
  "bytes": ["BLOB","byte[]","byte[]"],
  "largeText": ["TEXT","String","String"],
  "BIGINT": ["BIGINT","Long","Long"],
  "char": ["CHAR","String","String"],
  "localDate": ["DATE","LocalDate","LocalDate"]
});

const expectedPhysicalTypeSemantics = Object.freeze({
  "BIGINT": ["Long","Long"],
  "BLOB": ["byte[]","byte[]"],
  "CHAR": ["String","String"],
  "DATE": ["LocalDate","LocalDate"],
  "DATETIME": ["Instant","Instant"],
  "DECIMAL": ["BigDecimal","BigDecimal"],
  "INT": ["Integer","Integer"],
  "JSON": ["String","String"],
  "TEXT": ["String","String"],
  "TINYINT": ["Integer","Integer"],
  "VARCHAR": ["String","String"]
});

const expectedLocalDateMappingFields = Object.freeze(["individual_profile.birth_date","organization_profile.incorporation_date"]);

const tinyIntBaseTypeOverride = Object.freeze(["TINYINT","int","INT","Integer","Integer"]);
const jsonBaseTypeOverride = Object.freeze(["JSON","largeText","TEXT","String","String"]);
const expectedBaseTypeOverrides = Object.freeze({
  "agreement_party.acceptance_ref_slot": ["TINYINT","int","INT","Integer","Integer"],
  "operating_entity_scope.active_slot": ["TINYINT","int","INT","Integer","Integer"],
  "business_change_version.snapshot_json": ["JSON","largeText","TEXT","String","String"],
  "client_application.active_slot": ["TINYINT","int","INT","Integer","Integer"],
  "client_business_classification.primary_slot": ["TINYINT","int","INT","Integer","Integer"],
  "client_entity_affiliation.active_slot": ["TINYINT","int","INT","Integer","Integer"],
  "commercial_term_version.active_slot": ["TINYINT","int","INT","Integer","Integer"],
  "entity_source_relation.active_source_slot": ["TINYINT","int","INT","Integer","Integer"],
  "operating_entity_application.active_slot": ["TINYINT","int","INT","Integer","Integer"],
  "partner_application.active_slot": ["TINYINT","int","INT","Integer","Integer"],
  "product.base_product": ["TINYINT","int","INT","Integer","Integer"]
});

const expectedMoneyDomainFields = Object.freeze(["client_business_profile.expected_ticket","client_business_profile.expected_volume","commercial_term_component.fixed_amount","commercial_term_component.max_amount","commercial_term_component.min_amount","commercial_term_component_band.fixed_amount","commercial_term_component_band.lower_bound","commercial_term_component_band.upper_bound"]);

const expectedDecimalDomainFields = Object.freeze(["agreement_capability.rolling_reserve_rate","commercial_term_component.rate","commercial_term_component_band.rate","party_relation.ownership_percentage"]);

const expectedVarcharLengthOverrides = Object.freeze({
  "agreement_party.acceptance_reference": 128,
  "agreement.agreement_name": 255,
  "agreement.agreement_format": 32,
  "business_change_version.snapshot_format": 32,
  "client_business_classification.mcc_code": 4,
  "pricing_profile.profile_name": 128,
  "pricing_profile_cond.condition_value": 128,
  "compensation_event.event_key": 128,
  "compensation_event.payload_json": 4096,
  "compensation_event.execute_status": 16,
  "compensation_event.last_error_summary": 1000,
  "consumer_legal_document.locale": 35,
  "seller_disclosure.locale": 35,
  "seller_disclosure.public_legal_name": 255,
  "seller_disclosure.public_registration_number": 128,
  "seller_disclosure.public_address_text": 512,
  "seller_disclosure.public_support_email": 254,
  "seller_disclosure.public_support_phone": 32,
  "operating_entity_scope.mcc_code": 4,
  "party_address.address_line1": 255,
  "party_address.address_line2": 255,
  "party_address.city": 128,
  "party_address.state_or_province": 128,
  "party_address.postal_code": 32,
  "party_contact.contact_vault_token": 128,
  "party_contact.contact_masked_value": 255,
  "party_profile_identifier.identifier_value": 128,
  "party_identity_key.normalized_identifier_value": 128,
  "client_business_profile.website_url": 2048,
  "client_business_profile.business_description": 2000,
  "agreement.business_mode": 16,
  "agreement.fund_mode": 32,
  "agreement.pricing_authority": 16,
  "product.product_name": 128,
  "product.business_mode_scope": 16,
  "product_capability.capability_name": 128,
  "product_capability.capability_kind": 32,
  "product_capability.dimension_value": 128,
  "agreement_capability.status": 32,
  "agreement_capability_limit.limit_value": 128,
  "pricing_profile.profile_kind": 32,
  "commercial_term_version.pricing_model": 32,
  "commercial_term_version.fee_tax_treatment": 16,
  "commercial_term_component.refund_behavior": 32,
  "commercial_term_component.band_basis": 32
});

const expectedBigIntFieldKeys = Object.freeze(new Set([
  "create_by", "current_version",
  "lock_version", "qualification_decision_version",
  "source_version", "vault_record_version",
  "version_no",
]));

const expectedIntegerFieldKeys = Object.freeze(new Set([
  "band_no", "component_no", "execute_count", "hash_schema_version",
  "priority_no", "schema_version", "settlement_business_days", "submission_hash_schema_version",
  "cutoff_second_of_day", "rolling_reserve_days",
]));

const expectedTinyIntFieldKeys = Object.freeze(new Set([
  "active_slot", "active_source_slot", "primary_slot",
  "required_acceptance_flag", "base_product",
]));

const expectedDomains = Object.freeze({
  "DefaultString": ["string",255,""],
  "PrimaryKey": ["BIGINT","",""],
  "Money": ["double",24,8],
  "Int": ["int","",""],
  "Decimal": ["double",18,10],
  "DateTime": ["date",6,""],
  "currency": ["char",3,""]
});

const expectedHashFields = Object.freeze({
  "party_profile.content_hash": [false,null,"批准资料内容摘要，保留现有 Checkout 法律资料消费者；未批准为空，批准后不可变。"],
  "client_business_profile.content_hash": [false,null,"已批准单份经营资料内容摘要；未批准为空，不用于草稿版本冲突控制。"],
  "partner_application.submission_content_hash": [true,"submission_hash_schema_version","输入遵循 CODE-MOR:CCT:PARTNER_APPLICATION_SUBMISSION:1，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 位 Hex。"],
  "application_document.content_hash": [true,null,"输入为 Platform File 返回的文件正文 SHA-256，与 file_id 成组冻结。"],
  "operating_entity_application.submission_content_hash": [true,"submission_hash_schema_version","输入遵循 CODE-MOR:CCT:OPERATING_ENTITY_APPLICATION_SUBMISSION:3，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 位 Hex。"],
  "agreement_document_ref.content_hash": [true,null,"输入为 Platform Support 返回的合同文件 SHA-256，与 file_id 成组冻结。"],
  "consumer_legal_document.content_hash": [true,null,"输入为 Platform Support 返回的消费者法律文档 SHA-256，与 content_file_id 成组冻结。"]
});

const expectedPartyIdentityFields = Object.freeze({
  "party": ["party_id","party_no","party_type","create_time","update_time"],
  "party_profile": ["profile_id","party_id","content_hash","create_time","update_time","draft_application_id","approved_time"],
  "individual_profile": ["profile_id","given_name","middle_name","family_name","birth_date","nationality_country","residence_country","create_time","update_time"],
  "organization_profile": ["profile_id","legal_name","trade_name","incorporation_country","organization_type","incorporation_date","create_time","update_time"],
  "party_identity_key": ["identity_key_id","party_id","identifier_type","issuing_country","issuer_scope_key","normalized_identifier_value","create_time","update_time"],
  "party_profile_identifier": ["profile_identifier_id","profile_id","identity_key_id","valid_from","valid_to","create_time","update_time","identifier_type","issuing_country","issuer_scope_key","identifier_value"],
  "party_address": ["address_id","profile_id","address_type","address_line1","address_line2","city","state_or_province","postal_code","country_code","create_time","update_time"],
  "party_contact": ["contact_id","profile_id","contact_type","contact_vault_token","contact_masked_value","create_time","update_time"],
  "party_relation": ["relation_id","organization_party_id","related_party_id","relation_type","ownership_percentage","effective_from","effective_to","status","create_time","update_time","client_application_id"]
});

const hashVectors = Object.freeze([
  {
    name: "Client Application Submission V1",
    jcs: '{"application":{"applicationId":"1001","applicationType":"ONBOARDING","baseBusinessProfile":null,"basePartyProfile":null,"clientId":"2001","clientPartyId":"3001","clientPartyType":"ORGANIZATION","qualificationType":"ORGANIZATION_KYB","targetBusinessProfile":{"businessProfileId":"4001","contentHash":"2222222222222222222222222222222222222222222222222222222222222222"},"targetPartyProfile":{"contentHash":"1111111111111111111111111111111111111111111111111111111111111111","profileId":"3002"}},"documents":[{"contentHash":"3333333333333333333333333333333333333333333333333333333333333333","documentType":"INCORPORATION_CERTIFICATE","fileId":"5001","requirementCode":"LEGAL_ENTITY","subjectPartyId":"3001"}],"domain":"CODE-MOR:CCT:CLIENT_APPLICATION_SUBMISSION:1","hashSchemaVersion":1}',
    digest: "1f9289fbc5c5a4250505e1794b6f4279f8293ba1f4fbfd0068bb5925ccba2c1b",
  },
  {
    name: "Operating Entity Application Submission V2",
    jcs: '{"application":{"operatingCountry":"AE","operatingEntityId":"6001","operatingEntityPartyId":"6002","partyProfile":{"contentHash":"4444444444444444444444444444444444444444444444444444444444444444","profileId":"6003"},"qualificationType":"ENTITY_UNDERWRITING","source":{"clientSourceBasis":"VERIFIED_ASSOCIATION","sourceClientId":"2001","sourcePartnerId":null,"sourceType":"CLIENT"}},"documents":[{"contentHash":"6666666666666666666666666666666666666666666666666666666666666666","documentType":"UBO_EVIDENCE","fileId":"6005","requirementCode":"OWNERSHIP","subjectPartyId":"6002"}],"domain":"CODE-MOR:CCT:OPERATING_ENTITY_APPLICATION_SUBMISSION:2","hashSchemaVersion":2}',
    digest: "8aee688d8f87c2e235425d9401c2a26e9ceb4a61869fc7436f817288eed5cf00",
  },
]);

function readServiceModel(service) {
  assert.ok(existsSync(service.modelPath), `PDManer model is missing: ${service.modelPath}`);
  return JSON.parse(readFileSync(service.modelPath, "utf8"));
}

// Combined catalog view: Client & Contract catalog tables of both service
// models in catalog order, shared tables once (they must be identical), the
// non-catalog Client stored-card setting excluded. Diagrams are concatenated.
function combineServiceModels(models) {
  const [first] = models;
  const entities = new Map();
  for (const [index, model] of models.entries()) {
    const skipped = new Set(nonCatalogServiceTables[serviceModels[index].key].map(physicalTableKey));
    for (const entity of model.entities) {
      if (skipped.has(entity.defKey)) continue;
      const existing = entities.get(entity.defKey);
      if (existing) {
        assert.ok(sharedServiceTables.includes(logicalTableKey(entity.defKey)), `table owned by two services: ${entity.defKey}`);
        assert.deepEqual(entity, existing, `shared table differs between service models: ${entity.defKey}`);
        continue;
      }
      entities.set(entity.defKey, entity);
    }
  }
  const ordered = [...entities.values()].sort((left, right) =>
    catalogOrderIndex(left.defKey) - catalogOrderIndex(right.defKey));
  return { ...first, name: "combined", entities: ordered, diagrams: models.flatMap((model) => model.diagrams) };
}

const catalogTableOrder = parseClientContractCatalog(
  readFileSync(path.join(workspaceRoot, "agent-docs/database/CLIENT_CONTRACT_DATABASE.md"), "utf8"),
).map((table) => physicalTableKey(table.defKey));

function catalogOrderIndex(tableKey) {
  return catalogTableOrder.indexOf(tableKey);
}

function serviceTableKeys(service) {
  return [...service.catalogTables, ...nonCatalogServiceTables[service.key]];
}

function serviceRelationSignatures(service) {
  const tables = new Set(serviceTableKeys(service));
  return expectedLocalRelationSignatures.filter((signature) => {
    const relation = parseRelationSignature(signature);
    return tables.has(relation.childKey) && tables.has(relation.parentKey);
  });
}

function serviceDiagramSpec(service) {
  return {
    groups: expectedServiceDiagramGroups[service.key],
    tables: serviceTableKeys(service).slice().sort(),
    relations: serviceRelationSignatures(service).slice().sort(),
  };
}

function readServiceModels() {
  return serviceModels.map(readServiceModel);
}

function readModel() {
  return combineServiceModels(readServiceModels());
}

function generateClientContractModel(options) {
  return combineServiceModels(serviceModels.map((service) => service.generate(options)));
}

function entityMap(model) {
  return new Map(model.entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
}

function requireEntity(entities, tableKey) {
  const entity = entities.get(tableKey);
  assert.ok(entity, `missing required table: ${tableKey}`);
  return entity;
}

function fieldMap(entity) {
  return new Map(entity.fields.map((field) => [field.defKey, field]));
}

function assertAgreementLegalSimplificationContract(model) {
  const entities = entityMap(model);
  for (const tableKey of removedAgreementLegalTables) {
    assert.equal(entities.has(tableKey), false, `removed Agreement/Legal table remains: ${tableKey}`);
  }
  for (const fieldPath of removedAgreementLegalFieldPaths) {
    const [tableKey, fieldKey] = fieldPath.split(".");
    assert.equal(
      fieldMap(entities.get(tableKey) ?? { fields: [] }).has(fieldKey),
      false,
      `removed Agreement/Legal field remains: ${fieldPath}`,
    );
  }
  for (const tableKey of agreementLegalTableKeys) {
    if (!entities.has(tableKey) || removedAgreementLegalTables.includes(tableKey)) continue;
    const fields = fieldMap(requireEntity(entities, tableKey));
    for (const fieldKey of [
      "version_no", "active_slot", "scope_hash", "hash_schema_version", "binding_revision_no",
    ]) {
      assert.equal(fields.has(fieldKey), false, `obsolete Agreement/Legal structure remains: ${tableKey}.${fieldKey}`);
    }
  }
}

function indexSignatures(entity, unique) {
  const fields = new Map(entity.fields.map((field) => [field.id, field.defKey]));
  return entity.indexes
    .filter((index) => index.unique === unique)
    .map((index) => index.fields.map((field) => fields.get(field.fieldDefKey)).join(","))
    .sort();
}

function allPhysicalFields(model) {
  return model.entities.flatMap((entity) => entity.fields.map((field) => ({
    entity,
    field,
    fieldPath: `${logicalTableKey(entity.defKey)}.${field.defKey}`,
  })));
}

function qualifiedField(model, fieldPath) {
  const [tableKey, fieldKey] = fieldPath.split(".");
  return fieldMap(requireEntity(entityMap(model), tableKey)).get(fieldKey);
}

function isControlledValueCandidate(fieldPath, fieldKey) {
  if (
    expectedBusinessIdentifierCodeFields.includes(fieldPath) ||
    fieldPath === "party_address.postal_code"
  ) return false;
  return (
    /(?:^|_)(?:status|type|role|method|source|flag|slot|mode|basis|level|system)$/u.test(fieldKey) ||
    ["dimension_code", "calculation_base", "data_class", "result"].includes(fieldKey) ||
    fieldKey.endsWith("_code") ||
    fieldKey.endsWith("_country") ||
    fieldKey.endsWith("_currency") ||
    ["country_code", "currency", "locale", "caller_scope"].includes(fieldKey)
  );
}

function assertTableMetadata(entity) {
  const tableKey = logicalTableKey(entity.defKey);
  assert.equal(
    entity.defName,
    expectedTableDisplayNames[tableKey],
    `table display-name drift: ${tableKey}`,
  );
  assert.match(entity.defName, /[\u3400-\u9fff]/u, `Chinese table name required: ${entity.defKey}`);
  assert.equal(entity.comment.trim().length > 0, true, `table comment required: ${entity.defKey}`);
}

function assertFieldMetadata(entity) {
  const tablePurpose = entity.comment.split(/[;；]/u)[0].trim();
  assert.equal(tablePurpose.length > 0, true, `table purpose required: ${entity.defKey}`);
  for (const field of entity.fields) {
    const context = `${entity.defKey}.${field.defKey}`;
    assert.equal(field.defName.trim().length > 1, true, `meaningful field display name required: ${context}`);
    assert.match(field.defName, /[\u3400-\u9fff]/u, `Chinese field name required: ${context}`);
    assert.equal(field.comment.trim().length >= 6, true, `meaningful field comment required: ${context}`);
    assert.match(field.comment, /[\u3400-\u9fff]/u, `Chinese field comment required: ${context}`);
    assert.doesNotMatch(field.comment, /\bfor\b/iu, `mechanical English field comment: ${context}`);
    assert.equal(
      field.comment.startsWith(`${tablePurpose}：`) || field.comment.startsWith(`${tablePurpose}:`),
      false,
      `field comment repeats table purpose: ${context}`,
    );
  }
}

function assertMetadataContract(model) {
  assert.deepEqual(Object.keys(expectedTableDisplayNames).sort(), expectedTables);
  assert.equal(
    new Set(Object.values(expectedTableDisplayNames)).size,
    expectedTables.length,
    "table display names must be unique",
  );
  assert.deepEqual(
    Object.fromEntries(model.entities.map((entity) => [
      logicalTableKey(entity.defKey),
      entity.defName,
    ])),
    expectedTableDisplayNames,
    "complete table display-name mapping drift",
  );
  const entities = entityMap(model);
  for (const tableKey of expectedTables) {
    const entity = requireEntity(entities, tableKey);
    assertTableMetadata(entity);
    assertFieldMetadata(entity);
  }
}

function assertControlledValueContract(model) {
  const physicalFields = allPhysicalFields(model);
  const byPath = new Map(physicalFields.map((item) => [item.fieldPath, item.field]));
  const assertTargetPath = (fieldPath) => {
    const tableKey = fieldPath.split(".")[0];
    assert.equal(expectedTables.includes(tableKey), true, `obsolete table path in metadata manifest: ${fieldPath}`);
    assert.ok(byPath.has(fieldPath), `missing controlled field: ${fieldPath}`);
  };

  for (const [fieldPath, values] of Object.entries(expectedClosedEnumValues)) {
    assertTargetPath(fieldPath);
    const [tableKey, fieldKey] = fieldPath.split(".");
    assertClosedValues(requireEntity(entityMap(model), tableKey), fieldKey, values);
  }

  const actualOpenCodeFields = physicalFields
    .filter(({ field }) => field.comment.includes("取值来源："))
    .map(({ fieldPath }) => fieldPath)
    .sort();
  assert.deepEqual(actualOpenCodeFields, expectedOpenCodeFields, "open-code whitelist drift");
  for (const fieldPath of expectedOpenCodeFields) {
    assertTargetPath(fieldPath);
    const comment = byPath.get(fieldPath).comment;
    assert.equal([...comment.matchAll(/取值来源：/gu)].length, 1, `one open-code source required: ${fieldPath}`);
    assert.match(comment, /取值来源：[^；。]+；非固定枚举。/u, `exact open-code marker required: ${fieldPath}`);
    assert.doesNotMatch(comment, /允许值：/u, `open code must not masquerade as enum: ${fieldPath}`);
  }

  const actualConditionalFields = physicalFields
    .filter(({ field }) => field.comment.includes("取值规则："))
    .map(({ fieldPath }) => fieldPath)
    .sort();
  assert.deepEqual(
    actualConditionalFields,
    Object.keys(expectedConditionalValueRules).sort(),
    "conditional-value whitelist drift",
  );
  for (const [fieldPath, rule] of Object.entries(expectedConditionalValueRules)) {
    assertTargetPath(fieldPath);
    const field = byPath.get(fieldPath);
    assert.equal(field.comment.includes(`取值规则：${rule}。`), true, `conditional rule drift: ${fieldPath}`);
    assert.deepEqual([field.type, field.len], ["TINYINT", 1], `conditional field type drift: ${fieldPath}`);
    if (field.defKey.endsWith("_flag")) {
      assert.equal(field.notNull, true, `Boolean flag must be required: ${fieldPath}`);
      assert.equal(field.comment.includes("允许值：0、1。"), true, `Boolean values required: ${fieldPath}`);
    } else {
      assert.equal(field.notNull, false, `unique Slot must be nullable: ${fieldPath}`);
      assert.equal(field.comment.includes("允许值：1 或 NULL。"), true, `Slot values required: ${fieldPath}`);
    }
  }

  for (const fieldPath of expectedBusinessIdentifierCodeFields) {
    assertTargetPath(fieldPath);
    const comment = byPath.get(fieldPath).comment;
    assert.doesNotMatch(comment, /允许值：|取值来源：|非固定枚举|取值规则：/u);
    assert.match(comment, /唯一|幂等键命名空间/u, `business identifier meaning required: ${fieldPath}`);
  }

  const closedPaths = new Set(Object.keys(expectedClosedEnumValues));
  const openPaths = new Set(expectedOpenCodeFields);
  const conditionalPaths = new Set(Object.keys(expectedConditionalValueRules));
  for (const { field, fieldPath } of physicalFields) {
    if (!isControlledValueCandidate(fieldPath, field.defKey)) continue;
    const classificationCount = [closedPaths, openPaths, conditionalPaths]
      .filter((paths) => paths.has(fieldPath)).length;
    assert.equal(classificationCount, 1, `controlled field classification drift: ${fieldPath}`);
  }
}

const classificationMccFieldPath = "client_business_classification.mcc_code";
const exactAssignmentIndustryMccTables = Object.freeze(["operating_entity_scope"]);
const scopeIndustryMccTables = Object.freeze([
  ...exactAssignmentIndustryMccTables,
]);
const forbiddenLegacyTableKeys = Object.freeze(new Set([
  "client_business_activity",
]));
const forbiddenLegacyFieldKeys = Object.freeze(new Set([
  "activity_id",
  "classification_scheme",
  "classification_code",
  "activity_code",
  "scope_activity_code",
]));

function assertIndustryMccContract(model) {
  const classificationMcc = qualifiedField(model, classificationMccFieldPath);
  assert.ok(classificationMcc, `missing classification MCC field: ${classificationMccFieldPath}`);
  assert.deepEqual(
    [classificationMcc.type, classificationMcc.len, classificationMcc.notNull],
    ["VARCHAR", 4, true],
    `classification MCC physical contract drift: ${classificationMccFieldPath}`,
  );
  assert.match(
    classificationMcc.comment,
    /MCC[^。；]*(?:必须是|必须为|仅允许为|固定为)[^。；]*四位数字/u,
    "classification MCC must positively declare a four-digit MCC",
  );
  assert.doesNotMatch(
    classificationMcc.comment,
    /通配|\*/u,
    "classification MCC must not allow a wildcard",
  );

  for (const tableKey of exactAssignmentIndustryMccTables) {
    const industry = qualifiedField(model, `${tableKey}.industry_code`);
    const mcc = qualifiedField(model, `${tableKey}.mcc_code`);
    assert.ok(industry, `missing exact Assignment industry field: ${tableKey}.industry_code`);
    assert.ok(mcc, `missing exact Assignment MCC field: ${tableKey}.mcc_code`);
    assert.deepEqual(
      [mcc.type, mcc.len, mcc.notNull],
      ["VARCHAR", 4, true],
      `exact Assignment MCC physical contract drift: ${tableKey}.mcc_code`,
    );
    assert.match(mcc.comment, /MCC[^。；]*(?:必须是|必须为|仅允许为)[^。；]*四位数字/u);
    assert.doesNotMatch(
      mcc.comment,
      /允许[^。；]*(?:通配|\*)|批准的\s*[`]?\*[`]?/u,
      `Assignment Scope must be exact: ${tableKey}`,
    );
  }

}

function assertNoLegacyIndustryMccStructure(model) {
  for (const entity of model.entities) {
    assert.equal(
      forbiddenLegacyTableKeys.has(logicalTableKey(entity.defKey)),
      false,
      `legacy table key remains: ${entity.defKey}`,
    );
    for (const field of entity.fields) {
      assert.equal(
        forbiddenLegacyFieldKeys.has(field.defKey),
        false,
        `legacy field key remains: ${entity.defKey}.${field.defKey}`,
      );
    }
  }
}

function exactAllowedValues(field, context) {
  const matches = [...field.comment.matchAll(/允许值：([^。]+)。/gu)];
  assert.equal(matches.length, 1, `${context} must declare exactly one closed allowed-value set`);
  return matches[0][1].split("、");
}

function assertClosedValues(entity, fieldKey, expectedValues) {
  const field = fieldMap(entity).get(fieldKey);
  assert.ok(field, `missing controlled field: ${entity.defKey}.${fieldKey}`);
  assert.deepEqual(
    exactAllowedValues(field, `${entity.defKey}.${fieldKey}`),
    expectedValues,
    `closed value-set drift: ${entity.defKey}.${fieldKey}`,
  );
  assert.doesNotMatch(field.comment, /非固定枚举/u);
}

function assertOpenCodeSource(entity, fieldKey, expectedSource) {
  const field = fieldMap(entity).get(fieldKey);
  assert.ok(field, `missing open-code field: ${entity.defKey}.${fieldKey}`);
  assert.equal(
    field.comment.includes(`取值来源：${expectedSource}；非固定枚举。`),
    true,
    `open-code source drift: ${entity.defKey}.${fieldKey}`,
  );
  assert.doesNotMatch(field.comment, /允许值：/u);
}

function normalizeSql(value) {
  return value.replace(/\s+/gu, " ").trim();
}

function assertExactExpression(comment, expression, context) {
  assert.equal(
    normalizeSql(comment).includes(normalizeSql(expression)),
    true,
    `missing exact expression for ${context}: ${normalizeSql(expression)}`,
  );
}

function assertCommentClauses(comment, clauses, context) {
  for (const clause of clauses) {
    assert.equal(comment.includes(clause), true, `missing exact ${context} clause: ${clause}`);
  }
}

function expectedFinalFieldPaths() {
  return Object.entries(expectedFinalFields).flatMap(([tableKey, fieldKeys]) =>
    fieldKeys.map((fieldKey) => `${tableKey}.${fieldKey}`));
}

function assertFinalFieldManifestConsistency() {
  assert.deepEqual(Object.keys(expectedFinalFields).sort(), expectedTables);
  assert.equal(
    expectedTables.length,
    expectedFinalTableManifestCount,
    "exact final table manifest count drift",
  );
  assert.equal(
    Object.keys(expectedFinalFields).length,
    expectedFinalTableManifestCount,
    "exact final field-map table count drift",
  );
  const fieldPaths = expectedFinalFieldPaths();
  assert.equal(
    fieldPaths.length,
    expectedFinalFieldManifestCount,
    "exact final field manifest count drift",
  );
  assert.equal(new Set(fieldPaths).size, fieldPaths.length, "duplicate final field path");
  for (const [tableKey, fieldKeys] of Object.entries(expectedFinalFields)) {
    assert.equal(new Set(fieldKeys).size, fieldKeys.length, `duplicate final field: ${tableKey}`);
    for (const fieldKey of fieldKeys) {
      assert.equal(
        forbiddenTargetFieldKeys.includes(fieldKey),
        false,
        `forbidden field in final manifest: ${tableKey}.${fieldKey}`,
      );
    }
  }
  for (const [tableKey, changedFieldKeys] of Object.entries(expectedFields)) {
    assert.deepEqual(
      expectedFinalFields[tableKey],
      changedFieldKeys,
      `changed field manifest diverges from final manifest: ${tableKey}`,
    );
  }
  const fieldPathSet = new Set(fieldPaths);
  for (const fieldPath of Object.keys(expectedBaseTypeOverrides)) {
    assert.equal(
      fieldPathSet.has(fieldPath),
      true,
      `Base Type override is not a final field: ${fieldPath}`,
    );
  }
  for (const fieldPath of Object.keys(expectedVarcharLengthOverrides)) {
    assert.equal(
      fieldPathSet.has(fieldPath),
      true,
      `VARCHAR length override is not a final field: ${fieldPath}`,
    );
  }
}

function assertTargetModelFieldCatalog(model, context) {
  assertFinalFieldManifestConsistency();
  assert.deepEqual(
    model.entities.map((entity) => logicalTableKey(entity.defKey)).sort(),
    expectedTables,
    `${context} requires the approved 40-table catalog`,
  );
  const entities = entityMap(model);
  for (const tableKey of expectedTables) {
    const entity = requireEntity(entities, tableKey);
    assert.deepEqual(
      entity.fields.map((field) => field.defKey),
      expectedFinalFields[tableKey],
      `${context} final field catalog drift: ${tableKey}`,
    );
    for (const field of entity.fields) {
      assert.equal(
        forbiddenTargetFieldKeys.includes(field.defKey),
        false,
        `${context} forbidden field: ${tableKey}.${field.defKey}`,
      );
    }
  }
}

function assertNullabilityManifestConsistency() {
  assertFinalFieldManifestConsistency();
  const fieldPaths = expectedFinalFieldPaths();
  assert.equal(
    new Set(expectedNullableFieldPaths).size,
    expectedNullableFieldPaths.length,
    "duplicate nullable field path",
  );
  const fieldPathSet = new Set(fieldPaths);
  for (const fieldPath of expectedNullableFieldPaths) {
    assert.equal(fieldPathSet.has(fieldPath), true, `nullable path is not a final field: ${fieldPath}`);
  }
  const nullableSet = new Set(expectedNullableFieldPaths);
  const requiredPaths = fieldPaths.filter((fieldPath) => !nullableSet.has(fieldPath));
  assert.equal(
    requiredPaths.length + expectedNullableFieldPaths.length,
    fieldPaths.length,
    "required/nullable field manifest must be closed",
  );
}

function assertNullabilityContract(model) {
  assertNullabilityManifestConsistency();
  assertTargetModelFieldCatalog(model, "Nullability evaluator");
  const entities = entityMap(model);
  for (const tableKey of expectedTables) {
    const entity = requireEntity(entities, tableKey);
    const fields = fieldMap(entity);
    const auditFields = tableKey === "business_change_version"
      ? ["create_time"] : ["create_time", "update_time"];
    for (const fieldKey of auditFields) {
      const fieldPath = `${tableKey}.${fieldKey}`;
      const field = fields.get(fieldKey);
      assert.ok(field, `missing audit field: ${fieldPath}`);
      assert.deepEqual(
        [field.type, field.len, field.notNull],
        ["DATETIME", 6, true],
        `required nullability drift: ${fieldPath}`,
      );
    }
    if (tableKey === "business_change_version") {
      assert.equal(fields.has("update_time"), false, "business_change_version must not have update_time");
    }
  }

  const nullableSet = new Set(expectedNullableFieldPaths);
  for (const fieldPath of expectedFinalFieldPaths()) {
    const expectedNotNull = !nullableSet.has(fieldPath);
    const field = qualifiedField(model, fieldPath);
    const classification = expectedNotNull ? "required" : "nullable";
    assert.ok(field, `missing ${classification} nullability field: ${fieldPath}`);
    assert.equal(
      field.notNull,
      expectedNotNull,
      `${classification} nullability drift: ${fieldPath}`,
    );
  }
}

function assertApplicationDocumentContract(model) {
  const document = requireEntity(entityMap(model), "application_document");
  const fields = fieldMap(document);
  for (const parentKey of ["client_application_id", "partner_application_id", "operating_entity_application_id"]) {
    assert.equal(fields.get(parentKey)?.notNull, false, `nullable XOR parent required: ${parentKey}`);
  }
  for (const fieldKey of [
    "document_id", "subject_party_id", "requirement_code", "document_type",
    "file_id", "content_hash", "create_time", "update_time",
  ]) {
    assert.equal(fields.get(fieldKey)?.notNull, true, `required Application Document field: ${fieldKey}`);
  }
  assertExactExpression(document.comment, applicationDocumentXor, "Application Document parent XOR");
  assert.equal(
    document.comment.includes("client_application_id、partner_application_id 与 operating_entity_application_id 三个 Parent FK 均为本库 RESTRICT。"),
    true,
    "Application Document parent FK delete rule",
  );
  assert.deepEqual(relationSignatures(model, document), [
    "client_application_id->client_application.application_id",
    "operating_entity_application_id->operating_entity_application.application_id",
    "partner_application_id->partner_application.application_id",
    "subject_party_id->party.party_id",
  ]);
}

function assertApplicationBaseContract(application) {
  const fields = fieldMap(application);
  assertClosedValues(application, "status", applicationStatuses);
  assert.deepEqual(
    [fields.get("active_slot")?.type, fields.get("active_slot")?.len, fields.get("active_slot")?.notNull],
    ["TINYINT", 1, false],
    `${application.defKey}.active_slot physical contract`,
  );
  assertExactExpression(
    application.comment,
    "CHECK ((status IN ('DRAFT','SUBMITTED','UNDER_REVIEW') AND active_slot=1) OR (status IN ('APPROVED','REJECTED','WITHDRAWN') AND active_slot IS NULL))",
    `${application.defKey} status/slot CHECK`,
  );
  assertExactExpression(
    application.comment,
    "CHECK ((qualification_decision_id IS NULL) = (qualification_decision_version IS NULL))",
    `${application.defKey} Risk Decision ID/Version pair CHECK`,
  );
  for (const fieldKey of [
    "due_diligence_case_id", "qualification_decision_id", "qualification_decision_version",
  ]) {
    assert.deepEqual(
      [fields.get(fieldKey)?.type, fields.get(fieldKey)?.notNull],
      ["BIGINT", false],
      `${application.defKey}.${fieldKey} Risk Tuple physical contract`,
    );
  }
  assert.deepEqual(
    [fields.get("qualification_type")?.type, fields.get("qualification_type")?.len, fields.get("qualification_type")?.notNull],
    ["VARCHAR", 64, true],
    `${application.defKey}.qualification_type physical contract`,
  );
  assert.deepEqual(
    [fields.get("submission_hash_schema_version")?.type, fields.get("submission_hash_schema_version")?.notNull],
    ["INT", true],
    `${application.defKey}.submission_hash_schema_version physical contract`,
  );
  assert.deepEqual(
    [fields.get("submission_content_hash")?.type, fields.get("submission_content_hash")?.len, fields.get("submission_content_hash")?.notNull],
    ["CHAR", 64, true],
    `${application.defKey}.submission_content_hash physical contract`,
  );
}

function assertClientApplicationContract(model) {
  const application = requireEntity(entityMap(model), "client_application");
  const fields = fieldMap(application);
  assertClosedValues(application, "status", ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "RETURNED", "APPROVED", "REJECTED", "WITHDRAWN"]);
  assertClosedValues(application, "qualification_type", ["INDIVIDUAL_KYC", "ORGANIZATION_KYB"]);
  assert.deepEqual([fields.get("requested_by_account_id")?.type, fields.get("requested_by_account_id")?.notNull], ["BIGINT", true], "Client application account must be required");
  assert.deepEqual([fields.get("current_submission_id")?.type, fields.get("current_submission_id")?.len, fields.get("current_submission_id")?.notNull], ["VARCHAR", 64, false]);
  assert.ok(indexSignatures(application, true).includes("requested_by_account_id,active_slot"), "Client account active application uniqueness");
  assert.ok(indexSignatures(application, true).includes("current_submission_id"));
  for (const removed of ["application_type", "base_party_profile_id", "target_party_profile_id", "base_business_profile_id", "target_business_profile_id", "qualification_decision_version", "submission_hash_schema_version", "submission_content_hash"]) {
    assert.equal(fields.has(removed), false, "obsolete Client application field: " + removed);
  }
  assertCommentClauses(application.comment, ["DRAFT/RETURNED 可编辑", "批准后永久只读", "最终拒绝不复活", "旧审核任务结果不得覆盖当前任务"], "Client application state boundary");
}

function assertPartnerApplicationContract(model) {
  const application = requireEntity(entityMap(model), "partner_application");
  const fields = fieldMap(application);
  assertApplicationBaseContract(application);
  assertClosedValues(application, "qualification_type", ["PARTNER_DUE_DILIGENCE"]);
  for (const fieldKey of ["local_partner_id", "target_party_profile_id", "requested_by_account_id"]) {
    assert.deepEqual(
      [fields.get(fieldKey)?.type, fields.get(fieldKey)?.notNull],
      ["BIGINT", true],
      `partner_application.${fieldKey} required reference contract`,
    );
  }
  assertCommentClauses(application.comment, [
    "Risk Tuple 固定映射：role_type=LOCAL_PARTNER、role_id=local_partner_id、qualification_type=PARTNER_DUE_DILIGENCE。",
    "requested_by_account_id 是 Platform CUSTOMER Account 的外部稳定引用，不建立跨库物理外键。",
    "due_diligence_case_id、qualification_decision_id 与 qualification_decision_version 是 Risk & Compliance 外部稳定引用，不建立跨库物理外键。",
    "qualification_type 固定由系统写入 PARTNER_DUE_DILIGENCE，不接受调用方输入。",
  ], "Partner Application contract");
  assert.deepEqual(relationSignatures(model, application), [
    "local_partner_id->local_partner.local_partner_id",
    "target_party_profile_id->party_profile.profile_id",
  ]);
}

function assertOperatingEntityApplicationContract(model) {
  const application = requireEntity(entityMap(model), "operating_entity_application");
  const fields = fieldMap(application);
  assertApplicationBaseContract(application);
  assertClosedValues(application, "source_type", ["PLATFORM", "CLIENT", "PARTNER"]);
  assertClosedValues(application, "qualification_type", ["ENTITY_UNDERWRITING", "ENTITY_SCOPE_REVIEW"]);
  assertExactExpression(
    application.comment,
    operatingEntityApprovalSourceMatrix,
    "Operating Entity Application approval/source CHECK",
  );
  assertCommentClauses(application.comment, [
    "Risk Tuple 固定映射：role_type=OPERATING_ENTITY、role_id=entity_no、ONBOARDING 的 qualification_type=ENTITY_UNDERWRITING；SCOPE_CHANGE 的 qualification_type=ENTITY_SCOPE_REVIEW。",
    "qualification_decision_id 与 qualification_decision_version 必须成组为空或成组非空。",
    "APPROVED/REJECTED 前 due_diligence_case_id、qualification_decision_id、qualification_decision_version、qualification_type、role_type、role_id 必须匹配同一次 Risk 决策；WITHDRAWN 不要求 Decision。",
    "qualification_type 由系统按申请类型固定为 ENTITY_UNDERWRITING 或 ENTITY_SCOPE_REVIEW，不接受调用方输入。",
    "提交后 source_type、source_client_id、source_partner_id、client_source_basis 与 qualification_type 组成的 Source/Qualification Tuple 冻结且不可修改。",
    "approved_source_relation_id 仅在 APPROVED 时非空，其他状态必须为空。",
  ], "Operating Entity Application contract");
  assert.deepEqual(
    [fields.get("approved_source_relation_id")?.type, fields.get("approved_source_relation_id")?.notNull],
    ["BIGINT", false],
    "operating_entity_application.approved_source_relation_id physical contract",
  );
  assert.equal(fields.get("entity_no")?.notNull, true);
  assert.equal(fields.has("organization_party_id"), false);
  assert.equal(fields.has("approved_operating_entity_id"), false);
}

function assertVersionContract(entity, contract) {
  const fields = fieldMap(entity);
  assert.deepEqual(
    [fields.get("version_no")?.type, fields.get("version_no")?.notNull],
    ["BIGINT", true],
    `${entity.defKey}.version_no physical contract`,
  );
  assert.deepEqual(
    [
      fields.get("active_slot")?.type,
      fields.get("active_slot")?.len,
      fields.get("active_slot")?.notNull,
      fields.get("active_slot")?.defaultValue,
    ],
    ["TINYINT", 1, false, ""],
    `${entity.defKey}.active_slot physical contract`,
  );
  assert.deepEqual(
    [fields.get("effective_from")?.type, fields.get("effective_from")?.len, fields.get("effective_from")?.notNull],
    ["DATETIME", 6, true],
    `${entity.defKey}.effective_from physical contract`,
  );
  assert.deepEqual(
    [fields.get("effective_to")?.type, fields.get("effective_to")?.len, fields.get("effective_to")?.notNull],
    ["DATETIME", 6, false],
    `${entity.defKey}.effective_to physical contract`,
  );
  assert.ok(indexSignatures(entity, true).includes(`${contract.rootKey},active_slot`));
  assert.ok(
    indexSignatures(entity, false).includes(
      `${contract.rootKey},status,effective_from,effective_to`,
    ),
    `missing As-Of resolution index: ${entity.defKey}`,
  );
  if (contract.statuses) assertClosedValues(entity, "status", contract.statuses);
  if (contract.openStatusSource) {
    assertOpenCodeSource(entity, "status", contract.openStatusSource);
  }
  assertExactExpression(
    entity.comment,
    "CHECK ((status='ACTIVE' AND active_slot=1) OR (status<>'ACTIVE' AND active_slot IS NULL))",
    `${entity.defKey} status/slot CHECK`,
  );
  assertExactExpression(
    entity.comment,
    "CHECK (effective_to IS NULL OR effective_from < effective_to)",
    `${entity.defKey} effective interval CHECK`,
  );
  assert.equal(
    entity.comment.includes("同一 Root 已批准或生效的有效区间不得重叠。"),
    true,
    `${entity.defKey} overlap rule`,
  );
}

function assertSourceOwnerContract(model) {
  const entities = entityMap(model);
  const relation = requireEntity(entities, "entity_source_relation");
  const relationFields = fieldMap(relation);
  assertClosedValues(relation, "source_type", ["PLATFORM", "CLIENT", "PARTNER"]);
  assertClosedValues(relation, "client_source_basis", ["SAME_PARTY", "VERIFIED_ASSOCIATION"]);
  assertClosedValues(relation, "status", ["ACTIVE", "ENDED"]);
  assert.equal(relationFields.has("source_owner_key"), false, "derived source_owner_key must stay removed");
  assertCommentClauses(relation.comment, [
    "CHECK ((status='ACTIVE' AND active_source_slot=1) OR (status='ENDED' AND active_source_slot IS NULL))",
    "CHECK (effective_to IS NULL OR effective_from < effective_to)",
    "Owner XOR：PLATFORM 要求 source_client_id IS NULL AND source_partner_id IS NULL；CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。",
    verifiedAssociationClause,
    "source_type、source_client_id、source_partner_id、client_source_basis 创建后不可修改；来源失效时只允许推进为 ENDED 并填写 effective_to。",
  ], "Entity Source Relation identity");

  const application = requireEntity(entities, "operating_entity_application");
  assertClosedValues(application, "client_source_basis", ["SAME_PARTY", "VERIFIED_ASSOCIATION"]);
  assertCommentClauses(application.comment, [
    "Source Owner XOR：PLATFORM 要求 source_client_id IS NULL AND source_partner_id IS NULL；CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。",
    verifiedAssociationClause,
  ], "Operating Entity Application source");

  assertCommentClauses(
    requireEntity(entities, "party_relation").comment,
    partyRelationBoundaryClauses,
    "Party Relation immutability/end boundary",
  );
}

function relationSignatures(model, entity) {
  const entitiesById = new Map(model.entities.map((candidate) => [candidate.id, candidate]));
  return entity.correlations.map((relation) => {
    const source = entity.fields.find((field) => field.id === relation.myField);
    const targetEntity = entitiesById.get(relation.refEntity);
    const target = targetEntity?.fields.find((field) => field.id === relation.refField);
    assert.ok(source && targetEntity && target, `dangling relation in ${entity.defKey}`);
    return `${source.defKey}->${logicalTableKey(targetEntity.defKey)}.${target.defKey}`;
  }).sort();
}

function assertApprovedPrimaryKey(entity) {
  const tableKey = logicalTableKey(entity.defKey);
  const approvedFieldKey = expectedPrimaryKeys[tableKey];
  assert.ok(approvedFieldKey, `missing approved primary-key manifest: ${tableKey}`);
  const primaryKeys = entity.fields.filter((field) => field.primaryKey);
  assert.deepEqual(
    primaryKeys.map((field) => field.defKey),
    [approvedFieldKey],
    `approved primary-key drift: ${tableKey}`,
  );
  assert.deepEqual(
    [primaryKeys[0].type, primaryKeys[0].notNull, primaryKeys[0].autoIncrement],
    [tableKey === "operating_entity" ? "CHAR" : "BIGINT", true, false],
    `Snowflake primary-key contract drift: ${tableKey}.${approvedFieldKey}`,
  );
}

function assertBusinessTimeField(field, fieldPath) {
  assert.deepEqual(
    [field.type, field.len],
    ["DATETIME", 6],
    `business time must preserve UTC microseconds: ${fieldPath}`,
  );
}

function assertPhysicalPrecisionContract(model) {
  const expectedDecimals = Object.freeze({
  "party_relation.ownership_percentage": [18,10],
  "client_business_profile.expected_volume": [24,8],
  "client_business_profile.expected_ticket": [24,8],
  "commercial_term_component.rate": [18,10],
  "commercial_term_component.fixed_amount": [24,8],
  "commercial_term_component.min_amount": [24,8],
  "commercial_term_component.max_amount": [24,8],
  "commercial_term_component_band.lower_bound": [24,8],
  "commercial_term_component_band.upper_bound": [24,8],
  "commercial_term_component_band.rate": [18,10],
  "commercial_term_component_band.fixed_amount": [24,8],
  "agreement_capability.rolling_reserve_rate": [18,10]
});
  const fields = allPhysicalFields(model);
  assert.deepEqual(
    fields.filter(({ field }) => field.type === "DECIMAL").map(({ fieldPath }) => fieldPath).sort(),
    Object.keys(expectedDecimals).sort(),
    "DECIMAL field allowlist drift",
  );
  assert.deepEqual(
    fields.filter(({ field }) => field.type === "DATE").map(({ fieldPath }) => fieldPath).sort(),
    expectedLocalDateMappingFields,
    "DATE fact allowlist drift",
  );
  for (const { field, fieldPath } of fields) {
    if (
      field.defKey.endsWith("_time") || field.defKey.endsWith("_at") ||
      field.defKey.endsWith("_from") || field.defKey.endsWith("_to") ||
      field.defKey.endsWith("_until")
    ) {
      assertBusinessTimeField(field, fieldPath);
    }
    if (expectedDecimals[fieldPath]) {
      assert.deepEqual(
        [field.type, field.len, field.scale],
        ["DECIMAL", ...expectedDecimals[fieldPath]],
        `decimal precision drift: ${fieldPath}`,
      );
    }
    if (field.defKey.endsWith("country") || field.defKey.endsWith("country_code")) {
      assert.deepEqual([field.type, field.len], ["CHAR", 2], `country precision drift: ${fieldPath}`);
    }
    if (field.defKey === "currency" || field.defKey === "scope_currency") {
      assert.deepEqual([field.type, field.len], ["CHAR", 3], `currency precision drift: ${fieldPath}`);
    }
  }
}

function assertTypeMappingContract(model) {
  const mappings = Object.fromEntries(model.dataTypeMapping.mappings.map((mapping) => [
    mapping.defKey,
    [mapping[mysqlMappingId], mapping[javaMappingId], mapping[myBatisMappingId]],
  ]));
  assert.deepEqual(mappings, expectedTypeMappings, "PDManer/MySQL/Java/MyBatis type mapping drift");

  const mappingKeysById = new Map(
    model.dataTypeMapping.mappings.map((mapping) => [mapping.id, mapping.defKey]),
  );
  const domains = Object.fromEntries(model.domains.map((domain) => [
    domain.defKey,
    [mappingKeysById.get(domain.applyFor), domain.len, domain.scale],
  ]));
  assert.deepEqual(domains, expectedDomains, "PDManer domain mapping drift");
}

function targetFieldKeys(tableKey) {
  const fieldKeys = expectedFinalFields[tableKey];
  assert.ok(fieldKeys, `missing final target field specification: ${tableKey}`);
  return [...fieldKeys];
}

function expectedPhysicalFieldSpec(fieldPath) {
  const [tableKey, fieldKey] = fieldPath.split(".");
  assert.equal(
    expectedFinalFields[tableKey]?.includes(fieldKey) ?? false,
    true,
    `physical specification references unknown final field: ${fieldPath}`,
  );
  if (tableKey === "compensation_event" && ["payload_json", "last_error_summary", "execute_status"].includes(fieldKey)) return {type:"VARCHAR",len:expectedVarcharLengthOverrides[fieldPath],scale:"",baseType:"string"};
  if (fieldKey === "current_submission_id") return {type:"VARCHAR",len:64,scale:"",baseType:"string"};
  if (fieldKey === "classification_confirmed_by") return {type:"BIGINT",len:"",scale:"",baseType:"BIGINT"};
  if (fieldKey === "entity_no") return {type:"CHAR",len:5,scale:"",baseType:"char"};
  if (tableKey === "business_change_record" && ["record_id","aggregate_root_id"].includes(fieldKey)) return {type:"VARCHAR",len:64,scale:"",baseType:"string"};
  if (["agreement_format","snapshot_format"].includes(fieldKey)) return {type:"VARCHAR",len:32,scale:"",baseType:"string"};
  if (["base_entity_change_version", "acceptance_recorded_by"].includes(fieldKey)) return {type:"BIGINT",len:"",scale:"",baseType:"BIGINT"};
  if (fieldKey === "acceptance_ref_slot") return {type:"TINYINT",len:1,scale:"",baseType:"int"};
  if (fieldKey === expectedPrimaryKeys[tableKey] || fieldKey === "id" || fieldKey.endsWith("_id")) {
    return { type: "BIGINT", len: "", scale: "", baseType: "BIGINT" };
  }
  if (expectedBigIntFieldKeys.has(fieldKey)) {
    return { type: "BIGINT", len: "", scale: "", baseType: "BIGINT" };
  }
  if (expectedMoneyDomainFields.includes(fieldPath)) {
    return { type: "DECIMAL", len: 24, scale: 8, baseType: "double" };
  }
  if (expectedDecimalDomainFields.includes(fieldPath)) {
    return { type: "DECIMAL", len: 18, scale: 10, baseType: "double" };
  }
  if (fieldKey.endsWith("_hash") || fieldKey.endsWith("_blind_index")) {
    return { type: "CHAR", len: 64, scale: "", baseType: "char" };
  }
  if (fieldKey === "currency" || fieldKey === "scope_currency") {
    return { type: "CHAR", len: 3, scale: "", baseType: "char" };
  }
  if (fieldKey.endsWith("country") || fieldKey.endsWith("country_code")) {
    return { type: "CHAR", len: 2, scale: "", baseType: "char" };
  }
  if (fieldKey === "snapshot_json" || fieldKey === "payload_json") {
    return { type: "JSON", len: "", scale: "", baseType: "largeText" };
  }
  if (fieldKey === "last_error_summary" || fieldKey === "reason_snapshot") {
    return { type: "TEXT", len: "", scale: "", baseType: "largeText" };
  }
  if (expectedLocalDateMappingFields.includes(fieldPath)) {
    return { type: "DATE", len: "", scale: "", baseType: "localDate" };
  }
  if (
    fieldKey.endsWith("_time") || fieldKey.endsWith("_at") ||
    fieldKey.endsWith("_from") || fieldKey.endsWith("_to") ||
    fieldKey.endsWith("_until")
  ) {
    return { type: "DATETIME", len: 6, scale: "", baseType: "date" };
  }
  if (expectedIntegerFieldKeys.has(fieldKey)) {
    return { type: "INT", len: "", scale: "", baseType: "int" };
  }
  if (expectedTinyIntFieldKeys.has(fieldKey)) {
    return { type: "TINYINT", len: 1, scale: "", baseType: "int" };
  }
  return {
    type: "VARCHAR",
    len: expectedVarcharLengthOverrides[fieldPath] ?? 64,
    scale: "",
    baseType: "string",
  };
}

function assertPhysicalTypeShape(field, fieldPath) {
  const exactShapes = {
    BIGINT: ["", ""],
    BLOB: ["", ""],
    DATE: ["", ""],
    DATETIME: [6, ""],
    INT: ["", ""],
    JSON: ["", ""],
    TEXT: ["", ""],
    TINYINT: [1, ""],
  };
  if (exactShapes[field.type]) {
    assert.deepEqual(
      [field.len, field.scale],
      exactShapes[field.type],
      `physical type length/scale drift: ${fieldPath}`,
    );
    return;
  }
  if (field.type === "CHAR" || field.type === "VARCHAR") {
    assert.equal(Number.isInteger(field.len) && field.len > 0, true, `positive character length required: ${fieldPath}`);
    assert.equal(field.scale, "", `character scale must be empty: ${fieldPath}`);
    return;
  }
  if (field.type === "DECIMAL") {
    assert.equal(Number.isInteger(field.len) && field.len > 0, true, `positive decimal precision required: ${fieldPath}`);
    assert.equal(Number.isInteger(field.scale) && field.scale >= 0 && field.scale <= field.len, true, `valid decimal scale required: ${fieldPath}`);
    return;
  }
  assert.fail(`unsupported physical type: ${fieldPath}=${field.type}`);
}

function assertBaseTypeCompatibility(field, fieldPath, mappingsById) {
  const mapping = mappingsById.get(field.baseType);
  assert.ok(mapping, `unknown base type: ${fieldPath}`);
  const expected = expectedPhysicalFieldSpec(fieldPath);
  assert.deepEqual(
    [field.type, field.len, field.scale, mapping.defKey],
    [expected.type, expected.len, expected.scale, expected.baseType],
    `independent physical/Base Type contract drift: ${fieldPath}`,
  );
  const override = expectedBaseTypeOverrides[fieldPath];
  if (override) {
    assert.deepEqual(
      [field.type, mapping.defKey, mapping[mysqlMappingId], mapping[javaMappingId], mapping[myBatisMappingId]],
      override,
      `base type compatibility drift: ${fieldPath}`,
    );
  } else {
    assert.equal(field.type, mapping[mysqlMappingId], `base type compatibility drift: ${fieldPath}`);
  }
  assert.deepEqual(
    [mapping[javaMappingId], mapping[myBatisMappingId]],
    expectedPhysicalTypeSemantics[field.type],
    `Java/MyBatis type semantics drift: ${fieldPath}`,
  );
}

function expectedDomainFieldManifest() {
  const allPaths = expectedTables.flatMap((tableKey) =>
    targetFieldKeys(tableKey).map((fieldKey) => `${tableKey}.${fieldKey}`));
  return {
    DefaultString: [],
    PrimaryKey: Object.entries(expectedPrimaryKeys)
      .filter(([tableKey]) => tableKey !== "operating_entity")
      .map(([tableKey, fieldKey]) => `${tableKey}.${fieldKey}`).sort(),
    Money: [...expectedMoneyDomainFields].sort(),
    Int: [],
    Decimal: [...expectedDecimalDomainFields].sort(),
    DateTime: allPaths.filter((fieldPath) =>
      expectedPhysicalFieldSpec(fieldPath).type === "DATETIME").sort(),
    currency: allPaths.filter((fieldPath) =>
      /\.(?:currency|scope_currency)$/u.test(fieldPath)).sort(),
  };
}

function assertFieldTypeMappingContract(model) {
  assertTargetModelFieldCatalog(model, "Field type mapping evaluator");
  assertTypeMappingContract(model);
  const expectedPhysicalSpecs = new Map(expectedFinalFieldPaths().map((fieldPath) => [
    fieldPath,
    expectedPhysicalFieldSpec(fieldPath),
  ]));
  assert.equal(
    expectedPhysicalSpecs.size,
    expectedFinalFieldManifestCount,
    "exact physical field-spec manifest count drift",
  );
  assert.deepEqual(
    [...expectedPhysicalSpecs]
      .filter(([, spec]) => spec.type === "VARCHAR" && spec.len !== 64)
      .map(([fieldPath]) => fieldPath)
      .sort(),
    Object.keys(expectedVarcharLengthOverrides).sort(),
    "non-default VARCHAR length whitelist drift",
  );
  const mappingsById = new Map(model.dataTypeMapping.mappings.map((mapping) => [mapping.id, mapping]));
  const domainsById = new Map(model.domains.map((domain) => [domain.id, domain]));
  const expectedDomainFields = expectedDomainFieldManifest();
  const expectedDomainByPath = new Map(Object.entries(expectedDomainFields).flatMap(([
    domainKey, fieldPaths,
  ]) => fieldPaths.map((fieldPath) => [fieldPath, domainKey])));
  assert.equal(
    expectedDomainByPath.size,
    Object.values(expectedDomainFields).flat().length,
    "field cannot be assigned to multiple Domains",
  );
  const actualOverrides = [];
  const actualLocalDateFields = [];
  const actualDomainFields = Object.fromEntries(model.domains.map((domain) => [domain.defKey, []]));
  for (const entity of model.entities) {
    const tableKey = logicalTableKey(entity.defKey);
    const fieldKeys = targetFieldKeys(tableKey);
    assert.deepEqual(
      entity.fields.map((field) => field.defKey),
      fieldKeys,
      `type-mapping fixture field catalog drift: ${entity.defKey}`,
    );
    for (const field of entity.fields) {
      const fieldPath = `${tableKey}.${field.defKey}`;
      assertPhysicalTypeShape(field, fieldPath);
      assertBaseTypeCompatibility(field, fieldPath, mappingsById);
      const mapping = mappingsById.get(field.baseType);
      if (field.type !== mapping[mysqlMappingId]) actualOverrides.push(fieldPath);
      if (mapping.defKey === "localDate") actualLocalDateFields.push(fieldPath);

      const actualDomain = field.domain ? domainsById.get(field.domain)?.defKey : undefined;
      const expectedDomain = expectedDomainByPath.get(fieldPath);
      assert.equal(actualDomain, expectedDomain, `domain assignment drift: ${fieldPath}`);
      if (!expectedDomain) continue;
      actualDomainFields[expectedDomain].push(fieldPath);
      const domain = domainsById.get(field.domain);
      assert.equal(domain.applyFor, field.baseType, `Domain applyFor drift: ${fieldPath}`);
      assert.deepEqual(
        [field.type, field.len, field.scale],
        [mapping[mysqlMappingId], domain.len, domain.scale],
        `Domain physical definition drift: ${fieldPath}`,
      );
    }
  }
  assert.deepEqual(actualOverrides.sort(), Object.keys(expectedBaseTypeOverrides).sort(), "Base Type override whitelist drift");
  assert.deepEqual(actualLocalDateFields.sort(), expectedLocalDateMappingFields, "localDate mapping field allowlist drift");
  for (const [domainKey, fieldPaths] of Object.entries(expectedDomainFields)) {
    assert.deepEqual(actualDomainFields[domainKey].sort(), fieldPaths, `exact Domain use drift: ${domainKey}`);
  }
}

function indexContracts(entity) {
  const fieldsById = new Map(entity.fields.map((field) => [field.id, field.defKey]));
  return entity.indexes.map((index) =>
    `${index.unique ? "UK" : "IX"}:${index.fields.map((field) => fieldsById.get(field.fieldDefKey)).join(",")}`,
  ).sort();
}

function qualifiedRelationSignatures(model, tableKeys) {
  const entities = entityMap(model);
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return tableKeys.flatMap((tableKey) => {
    const entity = requireEntity(entities, tableKey);
    return entity.correlations.map((relation) => {
      const source = entity.fields.find((field) => field.id === relation.myField);
      const targetEntity = entitiesById.get(relation.refEntity);
      const target = targetEntity?.fields.find((field) => field.id === relation.refField);
      assert.ok(source && targetEntity && target, `dangling relation in ${tableKey}`);
      return `${tableKey}.${source.defKey}->${logicalTableKey(targetEntity.defKey)}.${target.defKey}|${relation.myRows}:${relation.refRows}`;
    });
  }).sort();
}

function assertBusinessChangeContract(model) {
  const entities = entityMap(model);
  for (const [tableKey, expected] of Object.entries(expectedBusinessChangeFields)) {
    assert.deepEqual(
      requireEntity(entities, tableKey).fields.map((field) => field.defKey),
      expected,
      `business-change field drift: ${tableKey}`,
    );
  }
  for (const [fieldPath, expected] of Object.entries(expectedBusinessChangePhysicalFields)) {
    const field = qualifiedField(model, fieldPath);
    assert.deepEqual([field.type, field.len, field.notNull], expected, `business-change physical drift: ${fieldPath}`);
  }
  const record = requireEntity(entities, "business_change_record");
  const version = requireEntity(entities, "business_change_version");
  assert.deepEqual(indexContracts(record), [
    "IX:aggregate_root_type,aggregate_root_id,history_id",
    "IX:parent_history_id",
    "UK:record_type,record_id",
  ]);
  assert.deepEqual(indexContracts(version), ["UK:history_id,version_no"]);
  assert.deepEqual(qualifiedRelationSignatures(model, [
    "business_change_record", "business_change_version",
  ]), [
    "business_change_record.parent_history_id->business_change_record.history_id|n:1",
    "business_change_version.history_id->business_change_record.history_id|n:1",
  ]);
  assert.equal(fieldMap(version).has("update_time"), false);
  assertClosedValues(record, "record_type", expectedBcrRecordTypes);
  assertClosedValues(record, "aggregate_root_type", expectedBcrRootTypes);
  assertClosedValues(version, "operation_type", ["CREATE", "UPDATE", "DELETE"]);
  assertCommentClauses(record.comment, ["current_version > 0"], "business_change_record");
  assertCommentClauses(
    record.comment,
    expectedBusinessChangeTransactionClauses,
    "business-change transaction protocol",
  );
  assertCommentClauses(version.comment, [
    "version_no > 0",
    "operation_type IN (CREATE,UPDATE,DELETE)",
    "64 KiB",
  ], "business_change_version");
  assert.equal(
    fieldMap(version).get("snapshot_json").comment.includes(
      "按 Record Type 白名单生成的不可变 JSON 审计视图",
    ),
    true,
    "snapshot_json whitelist envelope contract",
  );
}

function assertCompensationContract(model) {
  const event = requireEntity(entityMap(model), "compensation_event");
  assert.deepEqual(event.fields.map((field) => field.defKey), expectedCompensationFields);
  const fields = fieldMap(event);
  for (const [fieldKey, expected] of Object.entries(expectedCompensationPhysicalFields)) {
    const field = fields.get(fieldKey);
    assert.deepEqual(
      [field.type, field.len, field.notNull, field.defaultValue],
      expected,
      `compensation field drift: ${fieldKey}`,
    );
  }
  assert.deepEqual(indexContracts(event), [
    "IX:execute_status,lock_until,id",
    "IX:execute_status,next_execute_at,id",
    "UK:event_key,event_type,sub_event_type",
  ]);
  assert.deepEqual(relationSignatures(model, event), [], "compensation task identity must not have an FK");
  assertClosedValues(event, "execute_status", ["INIT", "PROCESSING", "ERROR", "FAILED"]);
  assert.equal(fields.get("payload_json").comment.includes("4 KiB"), true, "payload_json 4 KiB bound");
  assertCommentClauses(
    event.comment,
    expectedCompensationExecutionClauses,
    "compensation execution protocol",
  );
}

function assertAllModelIdentifiers(model) {
  const identifiers = [];
  const add = (id, context) => {
    assert.equal(typeof id, "string", `missing generated ID: ${context}`);
    assert.match(id, idPattern, `invalid generated ID: ${context}`);
    identifiers.push([id, context]);
  };
  for (const entity of model.entities) {
    add(entity.id, `Entity ${entity.defKey}`);
    assert.match(entity.defKey, /^t_[a-z][a-z0-9_]*$/u);
    assert.ok(entity.defKey.length <= 32, `table name too long: ${entity.defKey}`);
    for (const field of entity.fields) {
      add(field.id, `Field ${entity.defKey}.${field.defKey}`);
      assert.match(field.defKey, /^[a-z][a-z0-9_]*$/u);
      assert.ok(field.defKey.length <= 32, `field name too long: ${entity.defKey}.${field.defKey}`);
    }
    for (const index of entity.indexes) {
      add(index.id, `Index ${entity.defKey}.${index.defKey}`);
      assert.match(index.defKey, index.unique ? /^uk_[a-z0-9_]+$/u : /^idx_[a-z0-9_]+$/u);
      assert.ok(index.defKey.length <= 32, `index name too long: ${entity.defKey}.${index.defKey}`);
      assert.equal(index.comment.trim().length > 0, true, `index comment required: ${entity.defKey}.${index.defKey}`);
      for (const indexField of index.fields) {
        add(indexField.id, `IndexField ${entity.defKey}.${index.defKey}`);
        assert.equal(
          entity.fields.some((field) => field.id === indexField.fieldDefKey),
          true,
          `dangling index field: ${entity.defKey}.${index.defKey}`,
        );
      }
    }
  }
  for (const diagram of model.diagrams) {
    add(diagram.id, `Diagram ${diagram.defKey}`);
    for (const cell of diagram.canvasData.cells) add(cell.id, `Cell ${diagram.defKey}`);
  }
  const duplicateIds = identifiers
    .filter(([id], index) => identifiers.findIndex(([candidate]) => candidate === id) !== index);
  assert.deepEqual(duplicateIds, [], "Entity/Field/Index/IndexField/Diagram/Cell IDs must be globally unique");
}

function assertTableSpecificFieldSemantics(model) {
  const entities = entityMap(model);
  const comment = (tableKey, fieldKey) => {
    const field = fieldMap(requireEntity(entities, tableKey)).get(fieldKey);
    assert.ok(field, `missing table-specific metadata: ${tableKey}.${fieldKey}`);
    return field.comment;
  };

  assert.match(comment("agreement_party", "agreement_party_id"), /Party角色/u);
  assert.doesNotMatch(comment("agreement_party", "agreement_party_id"), /消费者/u);
  assert.equal(
    comment("operating_entity_application", "entity_no"),
    "申请创建时冻结的稳定 Operating Entity 标识；ONBOARDING 拒绝或撤回后重提复用同一 PENDING 主体；SCOPE_CHANGE 沿用既有主体且不回退其状态。",
  );
  assert.doesNotMatch(
    comment("operating_entity_application", "entity_no"),
    /批准后创建|approved_operating_entity_id/u,
  );
  for (const tableKey of ["business_change_record", "business_change_version"]) {
    assert.match(fieldMap(requireEntity(entities, tableKey)).get("history_id").defName, /业务变更记录标识/u);
    assert.doesNotMatch(comment(tableKey, "history_id"), /资格评估/u);
  }
  assert.match(comment("application_document", "content_hash"), /Platform.*文件.*内容哈希/u);
  assert.match(comment("agreement_document_ref", "content_hash"), /Platform Support.*合同文件.*SHA-256/u);
  assert.match(comment("consumer_legal_document", "content_hash"), /Platform Support.*消费者法律文档.*SHA-256/u);

  for (const [tableKey, fieldKey] of [
    ["party_address", "country_code"],
    ["client_business_profile", "currency"],
    ["consumer_legal_document", "country_code"],
    ["seller_disclosure", "country_code"],
    ["commercial_term_component", "currency"],
  ]) {
    assert.match(comment(tableKey, fieldKey), /(?:精确|准确)/u);
    assert.doesNotMatch(comment(tableKey, fieldKey), /通配/u);
  }
  for (const [tableKey, fieldKey] of [
    ["operating_entity_scope", "consumer_country_code"],
    ["operating_entity_scope", "currency"],
  ]) {
    assert.match(comment(tableKey, fieldKey), /(?:精确|准确)/u);
    assert.doesNotMatch(comment(tableKey, fieldKey), /允许[^。；]*(?:通配|\*)|批准的\s*[`]?\*[`]?/u);
  }
}

function assertRelationContract(model, expectedSignatures = expectedLocalRelationSignatures) {
  assert.equal(
    expectedRelationSignatures.length,
    expectedRelationSignatureCount,
    "exact catalog relation signature manifest count drift",
  );
  assert.equal(
    new Set(expectedRelationSignatures).size,
    expectedRelationSignatures.length,
    "duplicate relation signature in manifest",
  );
  assert.equal(
    expectedCrossDatabaseRelationSignatures.length,
    expectedCrossDatabaseRelationSignatureCount,
    "cross-database relation manifest count drift",
  );
  assert.ok(
    expectedCrossDatabaseRelationSignatures.every((signature) => expectedRelationSignatures.includes(signature)),
    "cross-database relation must be a catalog reference",
  );
  assert.equal(
    expectedLocalRelationSignatures.length,
    expectedLocalRelationSignatureCount,
    "exact local relation signature manifest count drift",
  );
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  const actualSignatures = [];
  for (const entity of model.entities) {
    for (const relation of entity.correlations) {
      const sourceField = entity.fields.find((field) => field.id === relation.myField);
      const targetEntity = entitiesById.get(relation.refEntity);
      const targetField = targetEntity?.fields.find((field) => field.id === relation.refField);
      assert.ok(sourceField, `dangling local relation field: ${entity.defKey}`);
      assert.ok(targetEntity && targetField, `cross-system or dangling relation: ${entity.defKey}.${sourceField.defKey}`);

      const sourceKey = `${logicalTableKey(entity.defKey)}.${sourceField.defKey}`;
      assert.equal(
        forbiddenExternalRelationFields.has(sourceKey),
        false,
        `external Risk/File/Vault/MQ reference must not have an FK: ${sourceKey}`,
      );
      assert.equal(relation.innerType, "", `unexpected relation inner type: ${sourceKey}`);
      assert.ok(
        sourceField.primaryKey || entity.indexes.some(
          (index) => index.fields[0]?.fieldDefKey === sourceField.id,
        ),
        `local FK needs a left-prefix index: ${sourceKey}`,
      );
      actualSignatures.push(
        `${sourceKey}->${logicalTableKey(targetEntity.defKey)}.${targetField.defKey}|${relation.myRows}:${relation.refRows}`,
      );
    }
  }
  assert.deepEqual(actualSignatures.sort(), [...expectedSignatures].sort(), "local relation signature drift");
}

function relationContractFixture() {
  const entities = new Map();
  const requireFixtureEntity = (tableKey) => {
    if (!entities.has(tableKey)) {
      entities.set(tableKey, {
        id: `entity:${tableKey}`,
        defKey: tableKey,
        fields: [],
        indexes: [],
        correlations: [],
      });
    }
    return entities.get(tableKey);
  };
  const requireFixtureField = (tableKey, fieldKey) => {
    const entity = requireFixtureEntity(tableKey);
    let field = entity.fields.find((candidate) => candidate.defKey === fieldKey);
    if (!field) {
      field = { id: `field:${tableKey}.${fieldKey}`, defKey: fieldKey, primaryKey: false };
      entity.fields.push(field);
    }
    return field;
  };

  for (const signature of expectedLocalRelationSignatures) {
    const match = signature.match(/^([^.]+)\.([^-]+)->([^.]+)\.([^|]+)\|([^:]+):(.+)$/u);
    assert.ok(match, `invalid relation manifest entry: ${signature}`);
    const [, childKey, childFieldKey, parentKey, parentFieldKey, myRows, refRows] = match;
    const child = requireFixtureEntity(childKey);
    const childField = requireFixtureField(childKey, childFieldKey);
    const parent = requireFixtureEntity(parentKey);
    const parentField = requireFixtureField(parentKey, parentFieldKey);
    if (!child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id)) {
      child.indexes.push({ fields: [{ fieldDefKey: childField.id }] });
    }
    child.correlations.push({
      myField: childField.id,
      refEntity: parent.id,
      refField: parentField.id,
      myRows,
      refRows,
      innerType: "",
    });
  }

  const document = requireFixtureEntity("application_document");
  const fileField = requireFixtureField("application_document", "file_id");
  document.indexes.push({ fields: [{ fieldDefKey: fileField.id }] });
  return { entities: [...entities.values()] };
}

function diagramContractFixture(service = serviceModels[0]) {
  const spec = serviceDiagramSpec(service);
  const fixture = relationContractFixture();
  const serviceTables = new Set(spec.tables);
  const model = {
    entities: fixture.entities.filter((entity) => serviceTables.has(entity.defKey)).map((entity) => ({
      ...entity,
      correlations: entity.correlations.filter((relation) => serviceTables.has(relation.refEntity.slice("entity:".length))),
    })),
  };
  const relatedTableKeys = new Set(model.entities.map((entity) => entity.defKey));
  for (const tableKey of spec.tables) {
    if (relatedTableKeys.has(tableKey)) continue;
    model.entities.push({
      id: `entity:${tableKey}`,
      defKey: tableKey,
      fields: [],
      indexes: [],
      correlations: [],
    });
  }
  const entities = entityMap(model);
  assert.deepEqual([...entities.keys()].sort(), spec.tables);
  model.diagrams = Object.entries(spec.groups).map(([groupKey, group]) => {
    const tableCells = group.tables.map((tableKey, index) => ({
      id: `cell:${groupKey}:${tableKey}`,
      shape: "table",
      position: { x: index * 20, y: 0 },
      size: { width: 10, height: 10 },
      originKey: entities.get(tableKey).id,
    }));
    const cellsByTable = new Map(
      group.tables.map((tableKey, index) => [tableKey, tableCells[index]]),
    );
    const relationCells = spec.relations.flatMap((signature) => {
      const relation = parseRelationSignature(signature);
      if (!group.primaryTables.includes(relation.childKey)) return [];
      const child = entities.get(relation.childKey);
      const parent = entities.get(relation.parentKey);
      return [{
        id: `cell:${groupKey}:relation:${signature}`,
        shape: "erdRelation",
        source: {
          cell: cellsByTable.get(relation.childKey).id,
          port: `${fieldMap(child).get(relation.childFieldKey).id}%out`,
        },
        target: {
          cell: cellsByTable.get(relation.parentKey).id,
          port: `${fieldMap(parent).get(relation.parentFieldKey).id}%in`,
        },
        relation: `${relation.myRows}:${relation.refRows}`,
      }];
    });
    return {
      id: `diagram:${groupKey}`,
      defKey: groupKey,
      defName: group.defName,
      relationType: "field",
      canvasData: { cells: [...tableCells, ...relationCells] },
    };
  });
  return model;
}

function applicationDocumentSemanticFixture() {
  const model = relationContractFixture();
  const document = entityMap(model).get("application_document");
  const ensureField = (fieldKey, notNull) => {
    let field = document.fields.find((candidate) => candidate.defKey === fieldKey);
    if (!field) {
      field = {
        id: `field:application_document.${fieldKey}`,
        defKey: fieldKey,
        primaryKey: fieldKey === "document_id",
      };
      document.fields.push(field);
    }
    field.notNull = notNull;
  };
  for (const fieldKey of ["client_application_id", "partner_application_id", "operating_entity_application_id"]) {
    ensureField(fieldKey, false);
  }
  for (const fieldKey of [
    "document_id", "subject_party_id", "requirement_code", "document_type",
    "file_id", "content_hash", "create_time", "update_time",
  ]) ensureField(fieldKey, true);
  document.comment = `父申请 CHECK ${applicationDocumentXor}；client_application_id、partner_application_id 与 operating_entity_application_id 三个 Parent FK 均为本库 RESTRICT。`;
  return model;
}

function versionSemanticFixture() {
  const field = (defKey, properties) => ({
    id: `field:commercial_term_version.${defKey}`,
    defKey,
    ...properties,
  });
  const fields = [
    field("term_plan_id", { type: "BIGINT", notNull: true }),
    field("version_no", { type: "BIGINT", notNull: true }),
    field("status", {
      type: "VARCHAR",
      notNull: true,
      comment: "版本状态。允许值：DRAFT、PENDING_APPROVAL、APPROVED、SCHEDULED、ACTIVE、SUPERSEDED、EXPIRED。",
    }),
    field("active_slot", { type: "TINYINT", len: 1, notNull: false, defaultValue: "" }),
    field("effective_from", { type: "DATETIME", len: 6, notNull: true }),
    field("effective_to", { type: "DATETIME", len: 6, notNull: false }),
  ];
  const fieldId = (fieldKey) => fields.find((candidate) => candidate.defKey === fieldKey).id;
  const index = (unique, fieldKeys) => ({
    unique,
    fields: fieldKeys.map((fieldKey) => ({ fieldDefKey: fieldId(fieldKey) })),
  });
  return {
    defKey: "commercial_term_version",
    fields,
    indexes: [
      index(true, ["term_plan_id", "active_slot"]),
      index(false, ["term_plan_id", "status", "effective_from", "effective_to"]),
    ],
    comment: [
      "CHECK ((status='ACTIVE' AND active_slot=1) OR (status<>'ACTIVE' AND active_slot IS NULL))",
      "CHECK (effective_to IS NULL OR effective_from < effective_to)",
      "同一 Root 已批准或生效的有效区间不得重叠。",
    ].join("；"),
  };
}

function nullabilityContractFixture() {
  assertNullabilityManifestConsistency();
  const nullablePaths = new Set(expectedNullableFieldPaths);
  return {
    entities: expectedTables.map((tableKey) => ({
      id: `entity:${tableKey}`,
      defKey: tableKey,
      fields: expectedFinalFields[tableKey].map((fieldKey) => {
        const fieldPath = `${tableKey}.${fieldKey}`;
        const physical = expectedPhysicalFieldSpec(fieldPath);
        return {
          id: `field:${fieldPath}`,
          defKey: fieldKey,
          type: physical.type,
          len: physical.len,
          scale: physical.scale,
          notNull: !nullablePaths.has(fieldPath),
        };
      }),
      indexes: [],
      correlations: [],
    })),
  };
}

function applicationBusinessContractFixture() {
  const field = (tableKey, defKey, properties = {}) => ({
    id: `field:${tableKey}.${defKey}`,
    defKey,
    ...properties,
  });
  const commonFields = (tableKey, qualificationValues) => [
    field(tableKey, "status", {
      comment: `申请状态。允许值：${applicationStatuses.join("、")}。`,
    }),
    field(tableKey, "active_slot", { type: "TINYINT", len: 1, notNull: false }),
    field(tableKey, "due_diligence_case_id", { type: "BIGINT", notNull: false }),
    field(tableKey, "qualification_decision_id", { type: "BIGINT", notNull: false }),
    field(tableKey, "qualification_decision_version", { type: "BIGINT", notNull: false }),
    field(tableKey, "qualification_type", {
      type: "VARCHAR",
      len: 64,
      notNull: true,
      comment: `资格类型。允许值：${qualificationValues.join("、")}。`,
    }),
    field(tableKey, "submission_hash_schema_version", { type: "INT", notNull: true }),
    field(tableKey, "submission_content_hash", { type: "CHAR", len: 64, notNull: true }),
  ];
  const commonComment = [
    "CHECK ((status IN ('DRAFT','SUBMITTED','UNDER_REVIEW') AND active_slot=1) OR (status IN ('APPROVED','REJECTED','WITHDRAWN') AND active_slot IS NULL))",
    "CHECK ((qualification_decision_id IS NULL) = (qualification_decision_version IS NULL))",
    "qualification_decision_id 与 qualification_decision_version 必须成组为空或成组非空。",
    "APPROVED/REJECTED 前 due_diligence_case_id、qualification_decision_id、qualification_decision_version、qualification_type、role_type、role_id 必须匹配同一次 Risk 决策；WITHDRAWN 不要求 Decision。",
  ];
  const client = {
    id: "entity:client_application",
    defKey: "client_application",
    fields: [
      field("client_application", "application_type", {
        comment: "申请类型。允许值：ONBOARDING、PROFILE_CHANGE。",
      }),
      field("client_application", "requested_by_account_id", { type: "BIGINT", notNull: false }),
      ...commonFields("client_application", ["INDIVIDUAL_KYC", "ORGANIZATION_KYB"]),
    ],
    indexes: [],
    correlations: [],
    comment: [
      ...commonComment,
      clientApplicationProfileMatrix,
      "Client 状态归属：ONBOARDING 仅允许绑定 PENDING Client；PROFILE_CHANGE 仅允许绑定 ACTIVE Client。",
      "ONBOARDING 必须携带 requested_by_account_id；该字段是 Platform CUSTOMER Account 的外部稳定引用，不建立跨库物理外键。",
      "Profile 归属：Base/Target Party Profile 必须属于 Client 对应 Party，Base/Target Business Profile 必须属于同一 client_id。",
      "PROFILE_CHANGE 提交时 Base Party/Business Profile 必须分别等于当时 ACTIVE Profile；与 Base 不同的 Target 必须在同一提交事务封存为 FROZEN。",
      "qualification_type 在提交时由 Client Party Type 推导：INDIVIDUAL 为 INDIVIDUAL_KYC、ORGANIZATION 为 ORGANIZATION_KYB；不得由调用方输入，提交后冻结且不可修改。",
      "Risk Tuple 固定映射：role_type=CLIENT、role_id=client_id。",
      "INDIVIDUAL Client 使用 qualification_type=INDIVIDUAL_KYC；ORGANIZATION Client 使用 qualification_type=ORGANIZATION_KYB。",
    ].join("；"),
  };
  const operatingEntity = {
    id: "entity:operating_entity_application",
    defKey: "operating_entity_application",
    fields: [
      field("operating_entity_application", "entity_no", { notNull: true }),
      field("operating_entity_application", "source_type", {
        comment: "经营主体来源。允许值：PLATFORM、CLIENT、PARTNER。",
      }),
      field("operating_entity_application", "client_source_basis", {
        comment: "Client 来源依据。允许值：SAME_PARTY、VERIFIED_ASSOCIATION。",
      }),
      field("operating_entity_application", "approved_source_relation_id", {
        type: "BIGINT",
        notNull: false,
      }),
      ...commonFields("operating_entity_application", ["ENTITY_UNDERWRITING", "ENTITY_SCOPE_REVIEW"]),
    ],
    indexes: [],
    correlations: [],
    comment: [
      ...commonComment,
      operatingEntityApprovalSourceMatrix,
      "Risk Tuple 固定映射：role_type=OPERATING_ENTITY、role_id=entity_no、ONBOARDING 的 qualification_type=ENTITY_UNDERWRITING；SCOPE_CHANGE 的 qualification_type=ENTITY_SCOPE_REVIEW。",
      "qualification_type 由系统按申请类型固定为 ENTITY_UNDERWRITING 或 ENTITY_SCOPE_REVIEW，不接受调用方输入。",
      "提交后 source_type、source_client_id、source_partner_id、client_source_basis 与 qualification_type 组成的 Source/Qualification Tuple 冻结且不可修改。",
      "approved_source_relation_id 仅在 APPROVED 时非空，其他状态必须为空。",
      "Source Owner XOR：PLATFORM 要求 source_client_id IS NULL AND source_partner_id IS NULL；CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。",
      verifiedAssociationClause,
    ].join("；"),
  };
  return { entities: [client, operatingEntity] };
}

function sourceOwnerContractFixture() {
  const model = applicationBusinessContractFixture();
  model.entities.push({
    id: "entity:entity_source_relation",
    defKey: "entity_source_relation",
    fields: [
      {
        id: "field:entity_source_relation.source_type",
        defKey: "source_type",
        comment: "来源类型。允许值：PLATFORM、CLIENT、PARTNER。",
      },
      {
        id: "field:entity_source_relation.client_source_basis",
        defKey: "client_source_basis",
        comment: "Client 来源依据。允许值：SAME_PARTY、VERIFIED_ASSOCIATION。",
      },
      {
        id: "field:entity_source_relation.status",
        defKey: "status",
        comment: "来源关系状态。允许值：ACTIVE、ENDED。",
      },
    ],
    indexes: [],
    correlations: [],
    comment: [
      "CHECK ((status='ACTIVE' AND active_source_slot=1) OR (status='ENDED' AND active_source_slot IS NULL))",
      "CHECK (effective_to IS NULL OR effective_from < effective_to)",
      "Owner XOR：PLATFORM 要求 source_client_id IS NULL AND source_partner_id IS NULL；CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。",
      verifiedAssociationClause,
      "source_type、source_client_id、source_partner_id、client_source_basis 创建后不可修改；来源失效时只允许推进为 ENDED 并填写 effective_to。",
    ].join("；"),
  });
  model.entities.push({
    id: "entity:party_relation",
    defKey: "party_relation",
    fields: [],
    indexes: [],
    correlations: [],
    comment: partyRelationBoundaryClauses.join("；"),
  });
  return model;
}

function businessChangeContractFixture() {
  const fixtureField = (tableKey, fieldKey) => {
    const [type, len, notNull] =
      expectedBusinessChangePhysicalFields[`${tableKey}.${fieldKey}`];
    return {
      id: `field:${tableKey}.${fieldKey}`,
      defKey: fieldKey,
      type,
      len,
      notNull,
      comment: "",
    };
  };
  const record = {
    id: "entity:business_change_record",
    defKey: "business_change_record",
    fields: expectedBusinessChangeFields.business_change_record.map((fieldKey) =>
      fixtureField("business_change_record", fieldKey)),
    indexes: [],
    correlations: [],
    comment: ["current_version > 0", ...expectedBusinessChangeTransactionClauses].join("；"),
  };
  const version = {
    id: "entity:business_change_version",
    defKey: "business_change_version",
    fields: expectedBusinessChangeFields.business_change_version.map((fieldKey) =>
      fixtureField("business_change_version", fieldKey)),
    indexes: [],
    correlations: [],
    comment: "version_no > 0；operation_type IN (CREATE,UPDATE,DELETE)；Snapshot 最大 64 KiB。",
  };
  const recordFields = fieldMap(record);
  const versionFields = fieldMap(version);
  const fixtureIndex = (entityFields, unique, fieldKeys) => ({
    unique,
    fields: fieldKeys.map((fieldKey) => ({ fieldDefKey: entityFields.get(fieldKey).id })),
  });
  record.indexes.push(
    fixtureIndex(recordFields, true, ["record_type", "record_id"]),
    fixtureIndex(recordFields, false, [
      "aggregate_root_type", "aggregate_root_id", "history_id",
    ]),
    fixtureIndex(recordFields, false, ["parent_history_id"]),
  );
  version.indexes.push(fixtureIndex(versionFields, true, ["history_id", "version_no"]));
  record.correlations.push({
    myField: recordFields.get("parent_history_id").id,
    refEntity: record.id,
    refField: recordFields.get("history_id").id,
    myRows: "n",
    refRows: "1",
    innerType: "",
  });
  version.correlations.push({
    myField: versionFields.get("history_id").id,
    refEntity: record.id,
    refField: recordFields.get("history_id").id,
    myRows: "n",
    refRows: "1",
    innerType: "",
  });
  versionFields.get("operation_type").comment =
    "操作类型。允许值：CREATE、UPDATE、DELETE。";
  recordFields.get("record_type").comment =
    `业务记录类型。允许值：${expectedBcrRecordTypes.join("、")}。`;
  recordFields.get("aggregate_root_type").comment =
    `聚合根类型。允许值：${expectedBcrRootTypes.join("、")}。`;
  versionFields.get("snapshot_json").comment =
    "按 Record Type 白名单生成的不可变 JSON 审计视图。";
  return { entities: [record, version] };
}

function compensationContractFixture() {
  const event = {
    id: "entity:compensation_event",
    defKey: "compensation_event",
    fields: expectedCompensationFields.map((fieldKey) => {
      const [type, len, notNull, defaultValue] =
        expectedCompensationPhysicalFields[fieldKey];
      return {
        id: `field:compensation_event.${fieldKey}`,
        defKey: fieldKey,
        type,
        len,
        notNull,
        defaultValue,
        comment: "",
      };
    }),
    indexes: [],
    correlations: [],
    comment: expectedCompensationExecutionClauses.join("；"),
  };
  const fields = fieldMap(event);
  const fixtureIndex = (unique, fieldKeys) => ({
    unique,
    fields: fieldKeys.map((fieldKey) => ({ fieldDefKey: fields.get(fieldKey).id })),
  });
  event.indexes.push(
    fixtureIndex(true, ["event_key", "event_type", "sub_event_type"]),
    fixtureIndex(false, ["execute_status", "next_execute_at", "id"]),
    fixtureIndex(false, ["execute_status", "lock_until", "id"]),
  );
  fields.get("execute_status").comment =
    "执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。";
  fields.get("payload_json").comment = "最小 JSON Object 最大 4 KiB UTF-8。";
  return { entities: [event] };
}

function fieldTypeMappingContractFixture() {
  const mappingEntries = Object.entries(expectedTypeMappings).map(([
    defKey, [mysqlType, javaType, myBatisType],
  ]) => ({
    id: `fixture:type:${defKey}`,
    defKey,
    [mysqlMappingId]: mysqlType,
    [javaMappingId]: javaType,
    [myBatisMappingId]: myBatisType,
  }));
  const mappingIds = new Map(mappingEntries.map((mapping) => [mapping.defKey, mapping.id]));
  const domainEntries = Object.entries(expectedDomains).map(([
    defKey, [mappingKey, len, scale],
  ]) => ({
    id: `fixture:domain:${defKey}`,
    defKey,
    applyFor: mappingIds.get(mappingKey),
    len,
    scale,
  }));
  const domainIds = new Map(domainEntries.map((domain) => [domain.defKey, domain.id]));
  const moneyFields = new Set(expectedMoneyDomainFields);
  const decimalFields = new Set(expectedDecimalDomainFields);
  const nullableFields = new Set(expectedNullableFieldPaths);
  assertFinalFieldManifestConsistency();
  const entities = expectedTables.map((tableKey) => {
    const fieldKeys = targetFieldKeys(tableKey);
    return {
      id: `fixture:entity:${tableKey}`,
      defKey: tableKey,
      fields: fieldKeys.map((fieldKey) => {
        const fieldPath = `${tableKey}.${fieldKey}`;
        const { type, len, scale, baseType } = expectedPhysicalFieldSpec(fieldPath);
        let domainKey = "";
        if (fieldKey === expectedPrimaryKeys[tableKey] && tableKey !== "operating_entity") domainKey = "PrimaryKey";
        if (moneyFields.has(fieldPath)) domainKey = "Money";
        if (decimalFields.has(fieldPath)) domainKey = "Decimal";
        if (type === "DATETIME") domainKey = "DateTime";
        if (fieldKey === "currency" || fieldKey === "scope_currency") domainKey = "currency";
        return {
          id: `fixture:field:${fieldPath}`,
          defKey: fieldKey,
          type,
          len,
          scale,
          notNull: !nullableFields.has(fieldPath),
          baseType: mappingIds.get(baseType),
          domain: domainKey ? domainIds.get(domainKey) : "",
        };
      }),
      indexes: [],
      correlations: [],
    };
  });
  return {
    dataTypeMapping: { mappings: mappingEntries },
    domains: domainEntries,
    entities,
  };
}

function canonicalizeJcs(value) {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalizeJcs).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) =>
      `${JSON.stringify(key)}:${canonicalizeJcs(value[key])}`,
    ).join(",")}}`;
  }
  return JSON.stringify(value);
}

function structuralProjection(model) {
  return {
    entities: model.entities.map((entity) => ({
      id: entity.id,
      defKey: entity.defKey,
      type: entity.type,
      fields: entity.fields.map((field) => ({
        id: field.id,
        defKey: field.defKey,
        type: field.type,
        len: field.len,
        scale: field.scale,
        primaryKey: field.primaryKey,
        notNull: field.notNull,
        autoIncrement: field.autoIncrement,
        defaultValue: field.defaultValue,
        refDict: field.refDict,
        baseType: field.baseType,
        domain: field.domain,
      })),
      indexes: entity.indexes.map((index) => ({
        id: index.id,
        defKey: index.defKey,
        unique: index.unique,
        fields: index.fields.map((field) => ({
          id: field.id,
          fieldDefKey: field.fieldDefKey,
          ascOrDesc: field.ascOrDesc,
        })),
      })),
      correlations: entity.correlations.map((relation) => ({
        myField: relation.myField,
        refEntity: relation.refEntity,
        refField: relation.refField,
        myRows: relation.myRows,
        refRows: relation.refRows,
        innerType: relation.innerType,
      })),
    })),
    diagrams: model.diagrams.map((diagram) => ({
      id: diagram.id,
      defKey: diagram.defKey,
      relationType: diagram.relationType,
      cells: diagram.canvasData.cells.map((cell) => cell.shape === "table" ? {
        id: cell.id,
        shape: cell.shape,
        originKey: cell.originKey,
      } : {
        id: cell.id,
        shape: cell.shape,
        source: cell.source,
        target: cell.target,
        relation: cell.relation,
      }),
    })),
  };
}

function structuralProjectionDigest(model) {
  return createHash("sha256")
    .update(JSON.stringify(structuralProjection(model)))
    .digest("hex");
}

function hasApprovedTableCatalog(model) {
  return model.entities.length === expectedTables.length &&
    model.entities.map((entity) => logicalTableKey(entity.defKey)).sort().join("\n") ===
      expectedTables.join("\n");
}

function assertStructuralProjectionDigest(model, expectedStructuralProjectionDigest) {
  if (expectedStructuralProjectionDigest === pendingStructuralProjectionDigest) {
    assert.equal(
      hasApprovedTableCatalog(model),
      false,
      "replace PENDING_APPROVED_40_DIGEST with the approved 64-hex digest before GREEN",
    );
    return;
  }
  assert.match(
    expectedStructuralProjectionDigest,
    /^[a-f0-9]{64}$/u,
    "approved Structural Projection digest must be 64 lowercase hex",
  );
  assert.equal(
    structuralProjectionDigest(model),
    expectedStructuralProjectionDigest,
    "approved Structural Projection digest drift",
  );
}

function parseRelationSignature(signature) {
  const match = signature.match(
    /^([^.]+)\.([^-]+)->([^.]+)\.([^|]+)\|([^:]+):(.+)$/u,
  );
  assert.ok(match, `invalid relation manifest entry: ${signature}`);
  const [, childKey, childFieldKey, parentKey, parentFieldKey, myRows, refRows] = match;
  return { childKey, childFieldKey, parentKey, parentFieldKey, myRows, refRows };
}

function assertDiagramManifestConsistency(spec) {
  const primaryTables = Object.values(spec.groups)
    .flatMap((group) => group.primaryTables);
  assert.deepEqual(primaryTables.slice().sort(), spec.tables, "Diagram primary membership drift");
  assert.equal(
    new Set(primaryTables).size,
    primaryTables.length,
    "each target table must have exactly one primary Diagram Group",
  );

  for (const [groupKey, group] of Object.entries(spec.groups)) {
    assert.equal(
      new Set(group.tables).size,
      group.tables.length,
      `duplicate table in Diagram manifest: ${groupKey}`,
    );
    const requiredTables = new Set(group.primaryTables);
    for (const signature of spec.relations) {
      const relation = parseRelationSignature(signature);
      if (group.primaryTables.includes(relation.childKey)) {
        requiredTables.add(relation.parentKey);
      }
    }
    assert.deepEqual(
      [...group.tables].sort(),
      [...requiredTables].sort(),
      `Diagram auto-parent membership drift: ${groupKey}`,
    );
  }
}

function assertNonOverlappingTableCells(diagram, tableCells) {
  for (let left = 0; left < tableCells.length; left += 1) {
    for (let right = left + 1; right < tableCells.length; right += 1) {
      const first = tableCells[left];
      const second = tableCells[right];
      const separated =
        first.position.x + first.size.width <= second.position.x ||
        second.position.x + second.size.width <= first.position.x ||
        first.position.y + first.size.height <= second.position.y ||
        second.position.y + second.size.height <= first.position.y;
      assert.equal(
        separated,
        true,
        `overlapping tables in ${diagram.defKey}: ${first.originKey} and ${second.originKey}`,
      );
    }
  }
}

function resolveDiagramPort(endpoint, direction, tableCell, entitiesById, context) {
  assert.equal(typeof endpoint?.port, "string", `missing ${direction} port: ${context}`);
  const parts = endpoint.port.split("%");
  assert.deepEqual(parts.slice(1), [direction], `invalid ${direction} port: ${context}`);
  const entity = entitiesById.get(tableCell.originKey);
  const field = entity.fields.find((candidate) => candidate.id === parts[0]);
  assert.ok(field, `dangling ${direction} port: ${context}`);
  return { entity, field };
}

function assertDiagramContract(model, spec = serviceDiagramSpec(serviceModels[0])) {
  assertDiagramManifestConsistency(spec);
  assert.equal(
    model.diagrams.length,
    Object.keys(spec.groups).length,
    "Diagram Group count drift",
  );
  assert.equal(
    new Set(model.diagrams.map((diagram) => diagram.defKey)).size,
    model.diagrams.length,
    "duplicate Diagram Group key",
  );
  assert.deepEqual(
    Object.fromEntries(model.diagrams.map((diagram) => [diagram.defKey, diagram.defName])),
    Object.fromEntries(Object.entries(spec.groups).map(
      ([groupKey, group]) => [groupKey, group.defName],
    )),
    "Diagram Group key/name drift",
  );

  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  const coveredTableKeys = new Set();
  const visualizedRelations = [];
  for (const diagram of model.diagrams) {
    const expectedGroup = spec.groups[diagram.defKey];
    assert.ok(expectedGroup, `unexpected Diagram Group: ${diagram.defKey}`);
    const cells = new Map(diagram.canvasData.cells.map((cell) => [cell.id, cell]));
    assert.equal(cells.size, diagram.canvasData.cells.length, `duplicate cell ID in ${diagram.defKey}`);
    const tableCells = diagram.canvasData.cells.filter((cell) => cell.shape === "table");
    const tableKeys = tableCells.map((cell) => {
      const entity = entitiesById.get(cell.originKey);
      assert.ok(entity, `dangling table cell: ${diagram.defKey}`);
      const tableKey = logicalTableKey(entity.defKey);
      coveredTableKeys.add(tableKey);
      return tableKey;
    });
    assert.equal(
      new Set(tableKeys).size,
      tableKeys.length,
      `duplicate table cell in Diagram Group: ${diagram.defKey}`,
    );
    assert.deepEqual(
      tableKeys.slice().sort(),
      [...expectedGroup.tables].sort(),
      `Diagram Group membership drift: ${diagram.defKey}`,
    );
    assertNonOverlappingTableCells(diagram, tableCells);

    for (const cell of diagram.canvasData.cells) {
      if (cell.shape === "table") continue;
      assert.equal(cell.shape, "erdRelation", `unexpected Diagram cell shape: ${diagram.defKey}`);
      const sourceCell = cells.get(cell.source?.cell);
      const targetCell = cells.get(cell.target?.cell);
      assert.equal(sourceCell?.shape, "table", `dangling relation source cell: ${diagram.defKey}`);
      assert.equal(targetCell?.shape, "table", `dangling relation target cell: ${diagram.defKey}`);
      const context = `${diagram.defKey}.${cell.id}`;
      const source = resolveDiagramPort(cell.source, "out", sourceCell, entitiesById, context);
      const target = resolveDiagramPort(cell.target, "in", targetCell, entitiesById, context);
      assert.equal(
        expectedGroup.primaryTables.includes(logicalTableKey(source.entity.defKey)),
        true,
        `relation child is not primary in Diagram Group: ${context}`,
      );
      visualizedRelations.push(
        `${logicalTableKey(source.entity.defKey)}.${source.field.defKey}->${logicalTableKey(target.entity.defKey)}.${target.field.defKey}|${cell.relation}`,
      );
    }
  }

  assert.deepEqual([...coveredTableKeys].sort(), spec.tables, "target table Diagram coverage drift");
  assert.equal(
    new Set(visualizedRelations).size,
    visualizedRelations.length,
    "duplicate visualized local relation",
  );
  assert.deepEqual(
    visualizedRelations.slice().sort(),
    spec.relations,
    "Diagram local relation coverage drift",
  );
}

test("removes the deferred multi-level ownership-path model", () => {
  const entities = entityMap(readModel());
  assert.equal(entities.has("entity_source_ownership_path"), false);
  assert.equal(entities.has("entity_source_path_edge"), false);
  assert.equal(
    fieldMap(requireEntity(entities, "operating_entity_application")).has("ownership_path_id"),
    false,
  );
  assert.equal(
    fieldMap(requireEntity(entities, "entity_source_relation")).has("ownership_path_id"),
    false,
  );
});

test("uses reviewed association instead of an ownership path for Client-sourced entities", () => {
  const entities = entityMap(readModel());
  assertClosedValues(
    requireEntity(entities, "operating_entity_application"),
    "client_source_basis",
    ["SAME_PARTY", "VERIFIED_ASSOCIATION"],
  );
  assertClosedValues(
    requireEntity(entities, "entity_source_relation"),
    "client_source_basis",
    ["SAME_PARTY", "VERIFIED_ASSOCIATION"],
  );
});

test("uses approved OE scopes and ordered affiliations without persisted assignments", () => {
  const entities = entityMap(readModel());

  for (const tableKey of removedAssignmentTables) {
    assert.equal(entities.has(tableKey), false, `removed Assignment table returned: ${tableKey}`);
  }
  for (const tableKey of leanAssignmentTables) {
    const entity = requireEntity(entities, tableKey);
    assert.deepEqual(
      entity.fields.map((field) => field.defKey),
      leanAssignmentFields[tableKey],
      `lean Assignment field drift: ${tableKey}`,
    );
  }
  assert.equal(
    fieldMap(requireEntity(entities, "entity_source_relation")).has("source_owner_key"),
    false,
  );
  assertCommentClauses(requireEntity(entities, "operating_entity_scope").comment,
    ["完整范围批准后原子替换", "无通配"], "OE Scope approval boundary");
});

test("uses reusable Payment Method Profiles and composable commercial prices", () => {
  const entities = entityMap(generateClientContractModel());
  const expectedCommercialTermFields = {
  "pricing_profile": [
    "pricing_profile_id",
    "pricing_profile_code",
    "profile_name",
    "profile_kind",
    "payment_method_code",
    "status",
    "create_time",
    "update_time"
  ],
  "pricing_profile_cond": [
    "profile_condition_id",
    "pricing_profile_id",
    "dimension_code",
    "condition_value",
    "create_time",
    "update_time"
  ],
  "commercial_term_plan": [
    "term_plan_id",
    "plan_code",
    "term_type",
    "agreement_id",
    "entity_no",
    "client_id",
    "local_partner_id",
    "status",
    "create_time",
    "update_time"
  ],
  "commercial_term_version": [
    "term_version_id",
    "term_plan_id",
    "agreement_id",
    "version_no",
    "status",
    "active_slot",
    "effective_from",
    "effective_to",
    "rounding_mode",
    "pricing_model",
    "fee_tax_treatment",
    "create_time",
    "update_time"
  ],
  "commercial_term_price": [
    "term_price_id",
    "term_version_id",
    "entity_no",
    "currency",
    "pricing_profile_code",
    "priority_no",
    "settlement_business_days",
    "business_calendar_code",
    "settlement_timezone",
    "cutoff_second_of_day",
    "create_time",
    "update_time"
  ],
  "commercial_term_component": [
    "term_component_id",
    "term_price_id",
    "component_no",
    "component_code",
    "component_type",
    "trigger_code",
    "calculation_base",
    "refund_behavior",
    "rate",
    "fixed_amount",
    "currency",
    "min_amount",
    "max_amount",
    "band_basis",
    "band_mode",
    "create_time",
    "update_time"
  ],
  "commercial_term_component_band": [
    "term_band_id",
    "term_component_id",
    "band_no",
    "lower_bound",
    "upper_bound",
    "rate",
    "fixed_amount",
    "create_time",
    "update_time"
  ]
};

  for (const [tableKey, expectedFields] of Object.entries(expectedCommercialTermFields)) {
    assert.deepEqual(
      requireEntity(entities, tableKey).fields.map((field) => field.defKey),
      expectedFields,
      `Commercial Term field drift: ${tableKey}`,
    );
  }

  for (const removedTable of [
    "client_pricing_plan", "client_pricing_version", "client_pricing_scope",
    "client_fee_component", "partner_share_plan", "partner_share_version",
    "partner_share_scope", "partner_share_component", "commercial_term_scope",
    "payment_method", "payment_method_profile_version",
    "pricing_profile_condition_value", "dimension_definition",
  ]) {
    assert.equal(entities.has(removedTable), false, `legacy table remains: ${removedTable}`);
  }

  for (const tableKey of Object.keys(expectedCommercialTermFields)) {
    const fields = fieldMap(requireEntity(entities, tableKey));
    for (const removedField of [
      "hash_schema_version", "content_hash", "scope_hash", "canonical_hash",
    ]) {
      assert.equal(fields.has(removedField), false, `${tableKey}.${removedField} remains`);
    }
  }
});

test("removes persisted Eligibility and deferred Retention tables from every structural contract", () => {
  assert.equal(removedRealtimeDeferredTables.length, 8);
  assert.equal(new Set(removedRealtimeDeferredTables).size, 8);

  const tableManifests = [
    expectedTableDisplayNames,
    expectedPrimaryKeys,
    expectedUniqueKeys,
    expectedFinalFields,
  ];
  const qualifiedFieldManifests = [
    expectedClosedEnumValues,
    expectedConditionalValueRules,
    expectedHashFields,
    FIELD_DESCRIPTION_OVERRIDES,
    CLOSED_ENUM_VALUES,
    CONDITIONAL_VALUE_RULES,
    OPEN_CODE_SOURCES,
  ];
  for (const removedTable of removedRealtimeDeferredTables) {
    assert.equal(expectedTables.includes(removedTable), false, `removed expected table remains: ${removedTable}`);
    for (const manifest of tableManifests) {
      assert.equal(Object.hasOwn(manifest, removedTable), false, `removed table manifest remains: ${removedTable}`);
    }
    for (const manifest of qualifiedFieldManifests) {
      assert.deepEqual(
        Object.keys(manifest).filter((fieldPath) => fieldPath.startsWith(`${removedTable}.`)),
        [],
        `removed qualified field manifest remains: ${removedTable}`,
      );
    }
    assert.deepEqual(
      expectedNullableFieldPaths.filter((fieldPath) => fieldPath.startsWith(`${removedTable}.`)),
      [],
      `removed nullable field manifest remains: ${removedTable}`,
    );
    assert.deepEqual(
      expectedRelationSignatures.filter((signature) =>
        signature.startsWith(`${removedTable}.`) || signature.includes(`->${removedTable}.`)),
      [],
      `removed relation manifest remains: ${removedTable}`,
    );
    for (const [groupKey, group] of Object.values(expectedServiceDiagramGroups).flatMap(Object.entries)) {
      assert.equal(group.primaryTables.includes(removedTable), false, `removed primary table remains: ${groupKey}.${removedTable}`);
      assert.equal(group.tables.includes(removedTable), false, `removed diagram table remains: ${groupKey}.${removedTable}`);
    }
  }

  for (const [source, model] of [
    ["generated", generateClientContractModel()],
    ["checked-in", readModel()],
  ]) {
    const entities = entityMap(model);
    for (const removedTable of removedRealtimeDeferredTables) {
      assert.equal(entities.has(removedTable), false, `${source} removed table remains: ${removedTable}`);
    }
    assert.equal(
      model.diagrams.some((diagram) => diagram.defKey === "eligibility_reliability"),
      false,
      `${source} obsolete Eligibility diagram remains`,
    );
  }
});

test("requires the approved Partner Application Client & Contract catalog", () => {
  const model = readModel();
  const entities = entityMap(model);
  assert.equal(model.entities.length, expectedTables.length);
  assert.deepEqual([...entities.keys()].sort(), expectedTables);
  for (const removed of [
    "client_onboarding", "client_profile_change", "client_onboarding_doc",
    "operating_entity_app_doc", "consumer_terms", "consumer_terms_version",
    "refund_policy", "refund_policy_version", "privacy_notice",
    "privacy_notice_version", "client_eligibility",
    "client_eligibility_dimension", "client_eligibility_history",
    "eligibility_dimension_history", "eligibility_dimension_source",
    "eligibility_source_history", "entity_source_ownership_path",
    "entity_source_path_edge", ...removedAgreementLegalTables,
    ...removedRealtimeDeferredTables,
  ]) {
    assert.equal(entities.has(removed), false, `removed table remains: ${removed}`);
  }
});

test("uses the approved physical table and index prefixes", () => {
  const model = readModel();
  for (const entity of model.entities) {
    assert.match(entity.defKey, /^t_[a-z0-9_]+$/u, `physical table prefix drift: ${entity.defKey}`);
    assert.equal(
      entity.defKey,
      physicalTableKey(logicalTableKey(entity.defKey)),
      `physical table mapping drift: ${entity.defKey}`,
    );
    for (const index of entity.indexes) {
      assert.match(
        index.defKey,
        index.unique ? /^uk_[a-z0-9_]+$/u : /^idx_[a-z0-9_]+$/u,
        `physical index prefix drift: ${entity.defKey}.${index.defKey}`,
      );
    }
  }
});

test("keeps Partner Application create_by as the mutable CCT command actor", () => {
  const partnerApplication = requireEntity(entityMap(readModel()), "partner_application");
  const createBy = fieldMap(partnerApplication).get("create_by");
  const requestedBy = fieldMap(partnerApplication).get("requested_by_account_id");

  assert.equal(
    createBy?.comment,
    "记录该可变生命周期申请行的 CCT 录入或命令 Actor 标识；不同于 requested_by_account_id，后者仅是外部 Platform CUSTOMER Account 申请人且不证明法律主体或签约资格。",
  );
  assert.equal(
    requestedBy?.comment,
    "发起申请的 Platform CUSTOMER Account 外部稳定标识；Partner Application 必填，不建立跨库物理外键。",
  );
});

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

test("locks the generated Partner Application physical totals", () => {
  // Before the split one model held 38 / 408 / 59 / 40 / 62 / 6. The combined
  // catalog view drops command_idempotency (1 table, 10 fields, 1 UK, 1 IDX) and
  // the 10 cross-database correlations; shared BCR/compensation tables count once.
  // The 2026-10-19 six-layer product tables add 6 / 54 / 6 / 5 / 7 / 1 (one more cross-database reference); Price.currency adds 1 field; round 2 adds 3 agreement fields and idx_agreement_referral.
  assert.deepEqual(modelTotals(readModel()), {
    tables: 43,
    fields: 456,
    uniqueIndexes: 64,
    accessIndexes: 45,
    relations: 59,
    diagramGroups: 10,
  });
  for (const service of serviceModels) {
    assert.deepEqual(modelTotals(readServiceModel(service)), expectedServiceTotals[service.key], service.key);
  }
});

test("splits the catalog into client-master and commercial by approved table ownership", () => {
  const [clientMaster, commercial] = readServiceModels();
  const tables = (model) => model.entities.map((entity) => logicalTableKey(entity.defKey));
  assert.deepEqual(tables(clientMaster), [...CLIENT_MASTER_CATALOG_TABLES, ...nonCatalogServiceTables["client-master"]]);
  assert.deepEqual(tables(commercial), [...COMMERCIAL_TABLES]);
  assert.equal(tables(clientMaster).length, 29);
  assert.equal(tables(commercial).length, 20);
  const both = tables(clientMaster).filter((tableKey) => tables(commercial).includes(tableKey));
  assert.deepEqual(both, [...sharedServiceTables]);
  for (const tableKey of droppedCatalogTables) {
    assert.equal(tables(clientMaster).includes(tableKey), false, `dropped table in client-master: ${tableKey}`);
    assert.equal(tables(commercial).includes(tableKey), false, `dropped table in commercial: ${tableKey}`);
  }
  assert.deepEqual(
    [...new Set([...tables(clientMaster), ...tables(commercial)])]
      .filter((tableKey) => !nonCatalogServiceTables["client-master"].includes(tableKey)).sort(),
    expectedTables,
    "every retained catalog table belongs to exactly one service (shared tables to both)",
  );
});

// 第 03 步删除了 Commerce 模型；以下是拆分前 commerce-service/docs/database/commerce.pdma.json 中该实体的冻结事实（ID、字段与注释）。
const FORMER_COMMERCE_STORED_CARD_SETTING = Object.freeze({
    "id": "1A821886-19AB-5FA1-AAAF-C942297031CA",
    "defKey": "t_client_stored_card_setting",
    "defName": "Client 存卡开通",
    "comment": "按 Client 开通存卡能力（CCT 暂无按 Client 配置落点时的 Commerce 本地配置）；首期由初始化 DML 或内部调用维护，运营页面后续提供。",
    "fields": [
      [
        "C3079BA3-1ABA-59E5-9681-B7F8A865F8FE",
        "client_id",
        "BIGINT",
        "",
        true,
        "平台 Client 的内部唯一标识；每个 Client 至多一行，没有记录视为未开通。"
      ],
      [
        "C27EDAA3-96AA-5B8F-9BB2-F2710142116F",
        "stored_card_enabled",
        "TINYINT",
        1,
        true,
        "是否开通存卡与已存卡支付；1 开通，0 关闭。关闭后已存卡保留但不展示、不可使用。"
      ],
      [
        "F37F0C54-74D5-5E93-8351-DD22C1C1F67D",
        "version",
        "BIGINT",
        "",
        true,
        "可变业务行并发更新使用的单调乐观锁版本。"
      ],
      [
        "76A1DE4E-E12F-5600-941E-CFD1E36C52E6",
        "create_time",
        "DATETIME",
        6,
        true,
        "数据库记录创建的 UTC 微秒精度时间。"
      ],
      [
        "8DD1F15F-01D7-50B3-BC07-1975FCED3411",
        "update_time",
        "DATETIME",
        6,
        true,
        "数据库记录最近一次持久化更新的 UTC 微秒精度时间。"
      ]
    ],
    "indexes": []
  });

test("copies the Client stored-card setting entity verbatim from the former Commerce model", () => {
  const actual = requireEntity(entityMap(readServiceModel(serviceModels[0])), "client_stored_card_setting");
  assert.deepEqual({
    id: actual.id,
    defKey: actual.defKey,
    defName: actual.defName,
    comment: actual.comment,
    fields: actual.fields.map((field) => [field.id, field.defKey, field.type, field.len, field.notNull, field.comment]),
    indexes: actual.indexes.map((index) => [index.id, index.defKey, index.unique]),
  }, FORMER_COMMERCE_STORED_CARD_SETTING);
});

test("rejects removed Agreement/Legal tables and fields without overreaching into Pricing or Share", () => {
  assertAgreementLegalSimplificationContract(readModel());
});

test("uses the exact unique Chinese display-name manifest for all 40 tables", () => {
  assertMetadataContract(readModel());
});

test("defines exact fields for industry/MCC scopes and the other changed aggregates", () => {
  const entities = entityMap(readModel());
  for (const [tableKey, fields] of Object.entries(expectedFields)) {
    assert.deepEqual(
      requireEntity(entities, tableKey).fields.map((field) => field.defKey),
      fields,
      `field contract drift in ${tableKey}`,
    );
  }
});

test("locks required and nullable field boundaries for every changed aggregate", () => {
  assertNullabilityContract(readModel());
});

test("keeps the approved unique-key contracts", () => {
  const model = readModel();
  const entities = entityMap(model);
  assert.deepEqual(Object.keys(expectedUniqueKeys).sort(), expectedTables);
  for (const [tableKey, expected] of Object.entries(expectedUniqueKeys)) {
    assert.deepEqual(indexSignatures(requireEntity(entities, tableKey), true), [...expected].sort());
  }
  // Combined catalog view; 59/40 before command_idempotency was dropped, +6 for the six-layer product tables.
  assert.equal(
    model.entities.flatMap((entity) => entity.indexes).filter((index) => index.unique).length,
    64,
    "unique-index count drift",
  );
  assert.equal(
    model.entities.flatMap((entity) => entity.indexes).filter((index) => !index.unique).length,
    45,
    "access-index count drift",
  );
  for (const service of serviceModels) {
    const totals = modelTotals(readServiceModel(service));
    assert.equal(totals.uniqueIndexes, expectedServiceTotals[service.key].uniqueIndexes, `${service.key} unique-index count drift`);
    assert.equal(totals.accessIndexes, expectedServiceTotals[service.key].accessIndexes, `${service.key} access-index count drift`);
  }
});

test("keeps the approved Agreement and Legal access-index contracts", () => {
  const entities = entityMap(readModel());
  for (const [tableKey, expected] of Object.entries(expectedAgreementLegalAccessIndexes)) {
    assert.deepEqual(
      indexSignatures(requireEntity(entities, tableKey), false),
      expected.map((fields) => fields.join(",")).sort(),
      `Agreement/Legal access-index drift: ${tableKey}`,
    );
  }
});

test("keeps Assignment MCC exact after moving pricing dimensions into Profile conditions", () => {
  assertIndustryMccContract(readModel());
});

test("industry/MCC evaluator rejects unbound classification wording and wildcard Assignment scope", () => {
  const model = {
    entities: [
      {
        defKey: "client_business_classification",
        fields: [{
          defKey: "mcc_code", type: "VARCHAR", len: 4, notNull: true,
          comment: "MCC 必须为保留前导零的四位数字字符串。",
        }],
      },
      ...scopeIndustryMccTables.map((tableKey) => ({
        defKey: tableKey,
        fields: [
          { defKey: "industry_code" },
          {
            defKey: "mcc_code", type: "VARCHAR", len: 4, notNull: true,
            comment: exactAssignmentIndustryMccTables.includes(tableKey)
              ? "MCC 必须为四位数字字符串。"
              : "允许四位 MCC 或批准的 `*` 通配值。",
          },
        ],
      })),
    ],
  };
  assertIndustryMccContract(model);

  const unboundClassification = structuredClone(model);
  qualifiedField(unboundClassification, classificationMccFieldPath).comment =
    "分类代码必须为四位数字字符串；MCC 取值来自批准目录。";
  assert.throws(
    () => assertIndustryMccContract(unboundClassification),
    /classification MCC must positively declare a four-digit MCC/u,
  );

  const wildcardAssignmentScope = structuredClone(model);
  qualifiedField(wildcardAssignmentScope, "operating_entity_scope.mcc_code").comment =
    "MCC 必须为四位数字；同时允许批准的 `*` 通配值。";
  assert.throws(
    () => assertIndustryMccContract(wildcardAssignmentScope),
    /Assignment Scope must be exact/u,
  );

});

test("rejects exact legacy industry/MCC structure without scanning historical prose", () => {
  const proseOnlyModel = {
    entities: [{
      defKey: "client_business_classification",
      comment: "历史说明：client_business_activity 的 activity_id 曾用于旧分类身份；classification_scheme 和 classification_code 已移除。",
      fields: [{
        defKey: "classification_id",
        comment: "迁移历史：activity_code 与 scope_activity_code 已替换为两个结构维度。",
      }],
    }],
  };
  assertNoLegacyIndustryMccStructure(proseOnlyModel);

  const legacyTable = structuredClone(proseOnlyModel);
  legacyTable.entities[0].defKey = "client_business_activity";
  assert.throws(
    () => assertNoLegacyIndustryMccStructure(legacyTable),
    /legacy table key remains: client_business_activity/u,
  );

  for (const legacyFieldKey of forbiddenLegacyFieldKeys) {
    const legacyField = structuredClone(proseOnlyModel);
    legacyField.entities[0].fields[0].defKey = legacyFieldKey;
    assert.throws(
      () => assertNoLegacyIndustryMccStructure(legacyField),
      new RegExp(`legacy field key remains: client_business_classification\\.${legacyFieldKey}`, "u"),
    );
  }
});

test("generated model contains no exact legacy industry/MCC structure", () => {
  assertNoLegacyIndustryMccStructure(readModel());
});

test("models exact Application state, Risk tuple, submission hash and parent XOR contracts", () => {
  const model = readModel();
  assertClientApplicationContract(model);
  assertPartnerApplicationContract(model);
  assertOperatingEntityApplicationContract(model);
  assertApplicationDocumentContract(model);
});

test("locks the retained Version status, Slot, effective-window and resolution-index contracts", () => {
  const entities = entityMap(readModel());
  assert.deepEqual(Object.keys(versionContracts).sort(), activeSlotVersions.map(([key]) => key).sort());
  for (const [tableKey, contract] of Object.entries(versionContracts)) {
    assertVersionContract(requireEntity(entities, tableKey), contract);
  }
});

test("locks direct Consumer Legal and Seller Disclosure publication records", () => {
  const entities = entityMap(readModel());
  const legal = requireEntity(entities, "consumer_legal_document");
  const disclosure = requireEntity(entities, "seller_disclosure");
  const legalFields = fieldMap(legal);
  assertClosedValues(legal, "document_type", [
    "CONSUMER_TERMS", "REFUND_POLICY", "PRIVACY_NOTICE",
  ]);
  assertClosedValues(legal, "status", ["DRAFT", "APPROVED", "ACTIVE", "EXPIRED", "WITHDRAWN"]);
  assertClosedValues(disclosure, "status", ["DRAFT", "APPROVED", "ACTIVE", "EXPIRED", "WITHDRAWN"]);
  for (const fieldKey of ["content_file_id", "content_hash", "approved_time", "published_time"]) {
    assert.equal(legalFields.has(fieldKey), true, `Consumer Legal publication field missing: ${fieldKey}`);
  }
  for (const fieldKey of ["scope_type", "client_id", "scope_key", "active_slot", "version_no"]) {
    assert.equal(legalFields.has(fieldKey), false, `obsolete Consumer Legal field remains: ${fieldKey}`);
  }
  for (const fieldKey of ["public_support_email", "public_support_phone", "approved_time", "published_time"]) {
    assert.equal(fieldMap(disclosure).has(fieldKey), true, `Seller Disclosure publication field missing: ${fieldKey}`);
  }
});

test("removes ownership paths and Commercial Term derived hashes", () => {
  const model = readModel();
  const entities = entityMap(model);
  assert.equal(entities.has("entity_source_ownership_path"), false);
  assert.equal(entities.has("entity_source_path_edge"), false);
  assert.equal(fieldMap(entities.get("operating_entity_application")).has("ownership_path_id"), false);
  assert.equal(fieldMap(entities.get("entity_source_relation")).has("ownership_path_id"), false);
  const canonicalHashFields = model.entities.flatMap((entity) =>
    entity.fields.filter((field) => field.defKey === "canonical_hash").map(() => entity.defKey),
  ).sort();
  assert.deepEqual(canonicalHashFields, []);
});

test("locks direct source ownership, Owner XOR and reviewed Client association matrices", () => {
  assertSourceOwnerContract(readModel());
});

test("does not retain Agreement/Legal revision coordinates", () => {
  assert.deepEqual(expectedRevisionFields, {});
  assertAgreementLegalSimplificationContract(readModel());
});

test("locks the complete approved Hash allowlist, CHAR(64), nullability and input schema", () => {
  const model = readModel();
  const entities = entityMap(model);
  const actualHashFields = model.entities.flatMap((entity) =>
    entity.fields
      .filter((field) => field.defKey.endsWith("_hash"))
      .map((field) => `${logicalTableKey(entity.defKey)}.${field.defKey}`),
  ).sort();
  // 8 before the split; command_idempotency.request_hash left with the dropped table.
  assert.equal(actualHashFields.length, 7, "hash-field count drift");
  assert.deepEqual(actualHashFields, Object.keys(expectedHashFields).sort(), "Hash field allowlist drift");
  for (const [qualifiedField, [notNull, schemaField, inputClause]] of Object.entries(expectedHashFields)) {
    const [tableKey, fieldKey] = qualifiedField.split(".");
    const entity = requireEntity(entities, tableKey);
    const fields = fieldMap(entity);
    const field = fields.get(fieldKey);
    assert.deepEqual([field.type, field.len, field.notNull], ["CHAR", 64, notNull], qualifiedField);
    assert.equal(field.comment.includes(inputClause), true, `Hash input drift: ${qualifiedField}`);
    if (schemaField) {
      assert.deepEqual(
        [fields.get(schemaField)?.type, fields.get(schemaField)?.notNull],
        ["INT", true],
        `Hash schema contract: ${tableKey}.${schemaField}`,
      );
    }
  }
  assert.deepEqual(
    actualHashFields.filter((field) => field.endsWith(".canonical_hash")),
    [],
  );
});

test("removes obsolete version and derived fields while retaining only explicit technical locks", () => {
  const model = readModel();
  for (const entity of model.entities) {
    for (const field of entity.fields) {
      assert.equal(
        forbiddenTargetFieldKeys.includes(field.defKey),
        false,
        `forbidden field: ${entity.defKey}.${field.defKey}`,
      );
    }
  }
  const independentLocks = model.entities.flatMap((entity) =>
    entity.fields
      .filter((field) => field.defKey === "lock_version")
      .map(() => `${logicalTableKey(entity.defKey)}.lock_version`),
  ).sort();
  assert.deepEqual(independentLocks, ["compensation_event.lock_version"]);
});

test("validates the fixed RFC 8785/JCS SHA-256 interoperability vectors", () => {
  for (const vector of hashVectors) {
    const parsed = JSON.parse(vector.jcs);
    assert.equal(canonicalizeJcs(parsed), vector.jcs, `${vector.name} is not fixed JCS JSON`);
    assert.match(vector.jcs, /"domain":"CODE-MOR:CCT:/u);
    assert.equal(createHash("sha256").update(vector.jcs, "utf8").digest("hex"), vector.digest, vector.name);
  }
});

test("keeps the shared template byte-for-byte unchanged", () => {
  assert.equal(
    createHash("sha256").update(readFileSync(templatePath)).digest("hex"),
    expectedTemplateDigest,
  );
});

test("locks the approved Structural Projection or requires its digest before GREEN", () => {
  for (const service of serviceModels) {
    assertStructuralProjectionDigest(readServiceModel(service), expectedStructuralProjectionDigests[service.key]);
  }
});

test("pending Structural Projection digest cannot accept the approved 40-table catalog", () => {
  for (const service of serviceModels) {
    assert.match(expectedStructuralProjectionDigests[service.key], /^[a-f0-9]{64}$/u);
  }
  const approvedCatalogFixture = {
    entities: expectedTables.map((defKey) => ({ defKey })),
  };
  assert.throws(
    () => assertStructuralProjectionDigest(approvedCatalogFixture, pendingStructuralProjectionDigest),
    /replace PENDING_APPROVED_40_DIGEST with the approved 64-hex digest before GREEN/u,
  );
});

test("Structural Projection ignores display metadata and changes for every structural layer", () => {
  for (const model of readServiceModels()) assertStructuralProjectionLayers(model);
});

function assertStructuralProjectionLayers(model) {
  const approvedDigest = structuralProjectionDigest(model);
  assert.match(approvedDigest, /^[a-f0-9]{64}$/u);

  const displayOnlyMutation = structuredClone(model);
  const displayEntity = displayOnlyMutation.entities[0];
  displayOnlyMutation.name = "仅模型显示名变更";
  displayOnlyMutation.describe = "仅模型说明变更";
  displayEntity.defName = "仅表显示名变更";
  displayEntity.comment = "仅表说明变更";
  displayEntity.headers[0].value = "仅画布表头变更";
  displayEntity.fields[0].defName = "仅字段显示名变更";
  displayEntity.fields[0].comment = "仅字段说明变更";
  displayEntity.fields[0].hideInGraph = !displayEntity.fields[0].hideInGraph;
  displayEntity.fields[0].extProps = { uiHint: "仅显示" };
  displayEntity.indexes[0].defName = "仅索引显示名变更";
  displayEntity.indexes[0].comment = "仅索引说明变更";
  const displayDiagram = displayOnlyMutation.diagrams[0];
  displayDiagram.defName = "仅图显示名变更";
  displayDiagram.comment = "仅图说明变更";
  const displayTableCell = displayDiagram.canvasData.cells.find((cell) => cell.shape === "table");
  displayTableCell.position = { x: -1000, y: -1000 };
  displayTableCell.size = { width: 1, height: 1 };
  displayTableCell.count = 999;
  const displayRelationCell = displayDiagram.canvasData.cells.find(
    (cell) => cell.shape === "erdRelation",
  );
  displayRelationCell.vertices = [{ x: 1, y: 1 }];
  displayRelationCell.fillColor = "#FFFFFF";
  displayRelationCell.attrs = { line: { strokeDasharray: "1 1" } };
  displayRelationCell.isLock = !displayRelationCell.isLock;
  assert.equal(structuralProjectionDigest(displayOnlyMutation), approvedDigest);

  const structuralMutations = [
    (candidate) => { candidate.entities[0].id = "entity-id-structure-drift"; },
    (candidate) => { candidate.entities[0].fields[0].notNull = false; },
    (candidate) => {
      candidate.entities.find((entity) => entity.indexes.length > 0).indexes[0].unique = false;
    },
    (candidate) => {
      candidate.entities.find((entity) => entity.correlations.length > 0)
        .correlations[0].refField = "relation-port-structure-drift";
    },
    (candidate) => { candidate.diagrams[0].canvasData.cells.pop(); },
  ];
  for (const mutate of structuralMutations) {
    const candidate = structuredClone(model);
    mutate(candidate);
    assert.notEqual(structuralProjectionDigest(candidate), approvedDigest);
  }
}

test("generator --check verifies determinism without rewriting the model", () => {
  for (const { modelPath, generatorPath } of serviceModels) {
    const before = readFileSync(modelPath, "utf8");
    const result = spawnSync(process.execPath, [generatorPath, "--check"], {
      cwd: workspaceRoot,
      encoding: "utf8",
    });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /model is up to date/iu);
    assert.equal(readFileSync(modelPath, "utf8"), before);
  }
});

test("rejects unknown generator arguments without rewriting the model", () => {
  for (const { modelPath, generatorPath } of serviceModels) {
    const before = readFileSync(modelPath, "utf8");
    const result = spawnSync(process.execPath, [generatorPath, "--unknown-option"], {
      cwd: workspaceRoot,
      encoding: "utf8",
    });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Unknown generator argument: --unknown-option/);
    assert.equal(readFileSync(modelPath, "utf8"), before);
  }
});

test("preserves the nine focused Party Identity tables and their immutable field contracts", () => {
  const entities = entityMap(readModel());
  assert.equal(entities.has("party_identity_alias"), false);
  assert.equal(entities.has("party_relation_version"), false);
  for (const [tableKey, fields] of Object.entries(expectedPartyIdentityFields)) {
    assert.deepEqual(requireEntity(entities, tableKey).fields.map((field) => field.defKey), fields);
  }
});

test("maps every physical field to the independent literal 40-table manifest", () => {
  const model = readModel();
  assertTargetModelFieldCatalog(model, "complete physical field evaluator");
});

test("uses valid physical identifiers, Snowflake primary keys and UTC audit timestamps", () => {
  const model = readModel();
  const identifiers = new Set();
  assert.deepEqual(Object.keys(expectedPrimaryKeys).sort(), expectedTables);
  for (const entity of model.entities) {
    assert.match(entity.id, idPattern);
    assert.equal(identifiers.has(entity.id), false, `duplicate entity ID: ${entity.id}`);
    identifiers.add(entity.id);
    assert.match(entity.defKey, /^t_[a-z][a-z0-9_]*$/);
    assert.ok(entity.defKey.length <= 32);
    assertApprovedPrimaryKey(entity);
    for (const field of entity.fields) {
      assert.match(field.id, idPattern);
      assert.equal(identifiers.has(field.id), false, `duplicate field ID: ${field.id}`);
      identifiers.add(field.id);
      assert.match(field.defKey, /^[a-z][a-z0-9_]*$/);
      assert.ok(field.defKey.length <= 32);
      assert.equal(field.autoIncrement, false);
      if (field.defKey.endsWith("_time") || field.defKey.endsWith("_at") || field.defKey.endsWith("_from") || field.defKey.endsWith("_to") || field.defKey.endsWith("_until")) assert.deepEqual([field.type, field.len], ["DATETIME", 6]);
    }
    for (const index of entity.indexes) {
      assert.match(index.id, idPattern);
      assert.match(index.defKey, /^(?:uk|idx)_[a-z0-9_]+$/);
      assert.ok(index.defKey.length <= 32 && index.comment.length > 0);
      for (const indexField of index.fields) assert.ok(entity.fields.some((field) => field.id === indexField.fieldDefKey));
    }
    for (const auditField of logicalTableKey(entity.defKey) === "business_change_version" ? ["create_time"] : ["create_time", "update_time"]) {
      assert.deepEqual([fieldMap(entity).get(auditField).type, fieldMap(entity).get(auditField).len], ["DATETIME", 6]);
    }
  }
});

test("keeps Chinese table and field metadata with controlled value semantics", () => {
  const model = readModel();
  assertMetadataContract(model);
  assertControlledValueContract(model);
  assertTableSpecificFieldSemantics(model);
});

test("keeps every generated ID, type mapping and domain reference resolvable", () => {
  const model = readModel();
  const typeIds = new Set(model.dataTypeMapping.mappings.map((mapping) => mapping.id));
  const domainIds = new Set(model.domains.map((domain) => domain.id));
  const identifiers = [];
  for (const entity of model.entities) {
    identifiers.push(entity.id, ...entity.fields.map((field) => field.id), ...entity.indexes.map((index) => index.id));
    for (const field of entity.fields) {
      assert.ok(typeIds.has(field.baseType), `unknown base type: ${entity.defKey}.${field.defKey}`);
      assert.ok(!field.domain || domainIds.has(field.domain), `unknown domain: ${entity.defKey}.${field.defKey}`);
    }
  }
  assert.equal(new Set(identifiers).size, identifiers.length);
  assertFieldTypeMappingContract(model);
  // Identifier uniqueness is a per-PDManer-file property: the shared history
  // diagram legitimately repeats its IDs across the two service models.
  for (const serviceModel of readServiceModels()) assertAllModelIdentifiers(serviceModel);
});

test("preserves DATE, all business times, money, rate, country and currency precision", () => {
  assertPhysicalPrecisionContract(readModel());
});

test("models immutable business-change records and versions", () => {
  assertBusinessChangeContract(readModel());
});

test("locks the complete compensation-event Claim and physical contract", () => {
  assertCompensationContract(readModel());
});

test("keeps model identity and all database connection profiles empty", () => {
  for (const [service, name, describe] of [
    [serviceModels[0], "Code-MoR Client Master Database V1", "Client Master"],
    [serviceModels[1], "commercial", "合同及参与方、产品目录、合同订阅与开通项、定价画像、商业条款方案 / 版本 / 价格 / 组件 / 阶梯档位"],
  ]) {
    const model = readServiceModel(service);
    assert.equal(model.name, name);
    assert.equal(model.describe, describe);
    assert.equal(model.version, "4.9.3");
    assert.deepEqual(model.dbConn, []);
    assert.deepEqual(model.dbConns, []);
    assert.equal(model.profile.default.dbConn, "");
  }
});

test("passes every enabled PDManer rule", () => {
  for (const model of readServiceModels()) assertEnabledPdmanerRules(model);
});

function assertEnabledPdmanerRules(model) {
  for (const rule of model.namingRules.filter((candidate) => candidate.enable)) {
    const execute = new Function("data", rule.programCode);
    for (const entity of model.entities) {
      // The paytech-messaging standard tables keep their VARCHAR / composite primary keys and own audit shape.
      if (messagingConsumerTables.includes(logicalTableKey(entity.defKey))) continue;
      const checkedEntity = { ...entity, fields: entity.fields.map((field) => ({ ...field, dbType: field.type })) };
      const data = { entity: checkedEntity, logicEntity: checkedEntity };
      if (rule.applyFieldType === "entity") assert.ok(execute(data), `${rule.defName}: ${entity.defKey}`);
      if (rule.applyFieldType === "field") for (const field of checkedEntity.fields) assert.ok(execute({ ...data, field }), `${rule.defName}: ${entity.defKey}.${field.defKey}`);
      if (rule.applyFieldType === "index") for (const index of entity.indexes) assert.ok(execute({ ...data, index }), `${rule.defName}: ${entity.defKey}.${index.defKey}`);
    }
  }
}

test("matches the complete approved local relation signature manifest", () => {
  assertRelationContract(readModel());
  for (const service of serviceModels) {
    assertRelationContract(readServiceModel(service), serviceRelationSignatures(service));
  }
  // The generator's catalog reference list is exactly the approved manifest.
  assert.deepEqual(
    LOCAL_RELATIONS.map(([child, childField, parent, parentField, rows]) =>
      `${child}.${childField}->${parent}.${parentField}|${rows}:1`).sort(),
    expectedRelationSignatures,
  );
  assert.deepEqual(
    serviceModels.map((service) => modelRelations(serviceTableKeys(service)).length),
    serviceModels.map((service) => serviceRelationSignatures(service).length),
  );
});

test("cross-database catalog references keep their field and access index without a correlation", () => {
  const commercial = entityMap(readServiceModel(serviceModels[1]));
  const clientMaster = entityMap(readServiceModel(serviceModels[0]));
  for (const signature of expectedCrossDatabaseRelationSignatures) {
    const relation = parseRelationSignature(signature);
    const child = requireEntity(commercial, relation.childKey);
    assert.equal(clientMaster.has(relation.childKey), false, `cross-database child must live in commercial: ${signature}`);
    assert.equal(commercial.has(relation.parentKey), false, `cross-database parent must live in client-master: ${signature}`);
    requireEntity(clientMaster, relation.parentKey);
    const field = fieldMap(child).get(relation.childFieldKey);
    assert.ok(field, `cross-database reference field missing: ${signature}`);
    assert.ok(
      child.indexes.some((index) => index.fields[0]?.fieldDefKey === field.id),
      `cross-database reference lost its left-prefix index: ${signature}`,
    );
    assert.equal(
      child.correlations.some((correlation) => correlation.myField === field.id),
      false,
      `cross-database reference must not be a PDManer correlation: ${signature}`,
    );
  }
});

test("relation contract evaluator rejects a missing Application Document parent FK", () => {
  const model = relationContractFixture();
  assertRelationContract(model);
  const document = entityMap(model).get("application_document");
  const parentField = fieldMap(document).get("client_application_id");
  document.correlations = document.correlations.filter(
    (relation) => relation.myField !== parentField.id,
  );
  assert.throws(() => assertRelationContract(model), /local relation signature drift/u);
});

test("relation contract evaluator rejects an external File FK", () => {
  const model = relationContractFixture();
  assertRelationContract(model);
  const entities = entityMap(model);
  const document = entities.get("application_document");
  const fileField = fieldMap(document).get("file_id");
  const party = entities.get("party");
  const partyId = fieldMap(party).get("party_id");
  document.correlations.push({
    myField: fileField.id,
    refEntity: party.id,
    refField: partyId.id,
    myRows: "n",
    refRows: "1",
    innerType: "",
  });
  assert.throws(
    () => assertRelationContract(model),
    /external Risk\/File\/Vault\/MQ reference must not have an FK/u,
  );
});

test("semantic evaluator rejects a weakened Application Document XOR", () => {
  const model = applicationDocumentSemanticFixture();
  assertApplicationDocumentContract(model);
  const document = entityMap(model).get("application_document");
  document.comment = document.comment.replace("= 1", "= 2");
  assert.throws(
    () => assertApplicationDocumentContract(model),
    /missing exact expression for Application Document parent XOR/u,
  );
});

test("semantic evaluator rejects restoring the derived source owner key", () => {
  const validModel = sourceOwnerContractFixture();
  assertSourceOwnerContract(validModel);
  const mutated = structuredClone(validModel);
  entityMap(mutated).get("entity_source_relation").fields.push({
    id: "field:entity_source_relation.source_owner_key",
    defKey: "source_owner_key",
  });
  assert.throws(
    () => assertSourceOwnerContract(mutated),
    /derived source_owner_key must stay removed/u,
  );
});

test("semantic evaluator rejects a wrong Version Active Slot condition", () => {
  const entity = versionSemanticFixture();
  assertVersionContract(entity, versionContracts.commercial_term_version);
  entity.comment = entity.comment.replace("status<>'ACTIVE'", "status='APPROVED'");
  assert.throws(
    () => assertVersionContract(entity, versionContracts.commercial_term_version),
    /missing exact expression for commercial_term_version status\/slot CHECK/u,
  );
});

test("metadata evaluator rejects a wrong table display name and mechanical field prefix", () => {
  const party = structuredClone(entityMap(readModel()).get("party"));
  assertTableMetadata(party);
  assertFieldMetadata(party);

  party.defName = "客户角色";
  assert.throws(() => assertTableMetadata(party), /table display-name drift: party/u);

  party.defName = expectedTableDisplayNames.party;
  party.fields[0].comment = `${party.comment.split(/[;；]/u)[0]}：party_id for party`;
  assert.throws(
    () => assertFieldMetadata(party),
    /mechanical English field comment|field comment repeats table purpose/u,
  );
});

test("primary-key evaluator rejects a different BIGINT column marked as the table key", () => {
  const party = structuredClone(entityMap(readModel()).get("party"));
  assertApprovedPrimaryKey(party);
  const fields = fieldMap(party);
  fields.get("party_id").primaryKey = false;
  Object.assign(fields.get("party_no"), {
    type: "BIGINT",
    notNull: true,
    autoIncrement: false,
    primaryKey: true,
  });
  assert.throws(() => assertApprovedPrimaryKey(party), /approved primary-key drift: party/u);
});

test("business-time evaluator rejects loss of microsecond precision", () => {
  const createTime = structuredClone(
    fieldMap(entityMap(readModel()).get("party")).get("create_time"),
  );
  assertBusinessTimeField(createTime, "party.create_time");
  createTime.len = 0;
  assert.throws(
    () => assertBusinessTimeField(createTime, "party.create_time"),
    /business time must preserve UTC microseconds: party\.create_time/u,
  );
});

test("business-change evaluator rejects a nullable snapshot JSON", () => {
  const model = businessChangeContractFixture();
  assertBusinessChangeContract(model);
  fieldMap(entityMap(model).get("business_change_version")).get("snapshot_json").notNull = false;
  assert.throws(
    () => assertBusinessChangeContract(model),
    /business-change physical drift: business_change_version\.snapshot_json/u,
  );
});

test("business-change evaluator rejects missing System Transition, CAS rollback or Event atomicity clauses", () => {
  const validModel = businessChangeContractFixture();
  assertBusinessChangeContract(validModel);
  for (const clause of [
    expectedBusinessChangeTransactionClauses[7],
    expectedBusinessChangeTransactionClauses[9],
    expectedBusinessChangeTransactionClauses[10],
  ]) {
    const mutated = structuredClone(validModel);
    const record = entityMap(mutated).get("business_change_record");
    record.comment = record.comment.replace(clause, "");
    assert.throws(
      () => assertBusinessChangeContract(mutated),
      /business-change transaction protocol/u,
    );
  }
});

test("compensation evaluator rejects weakened Payload immutability or Lease matrix clauses", () => {
  const validModel = compensationContractFixture();
  assertCompensationContract(validModel);

  const mutablePayload = structuredClone(validModel);
  const mutablePayloadEvent = entityMap(mutablePayload).get("compensation_event");
  mutablePayloadEvent.comment = mutablePayloadEvent.comment.replace(
    expectedCompensationExecutionClauses[0],
    "Payload 可以在重试时修改。",
  );
  assert.throws(
    () => assertCompensationContract(mutablePayload),
    /compensation execution protocol/u,
  );

  const weakLease = structuredClone(validModel);
  const weakLeaseEvent = entityMap(weakLease).get("compensation_event");
  weakLeaseEvent.comment = weakLeaseEvent.comment.replace(
    expectedCompensationExecutionClauses[1],
    "PROCESSING 可以没有活动 Lease。",
  );
  assert.throws(
    () => assertCompensationContract(weakLease),
    /compensation execution protocol/u,
  );
});

test("keeps Party email and phone as the only Vault-backed Party values", () => {
  const entities = entityMap(readModel());
  for (const tableKey of ["individual_profile", "organization_profile", "party_identity_key", "party_profile_identifier", "party_address"]) {
    for (const field of requireEntity(entities, tableKey).fields) {
      assert.doesNotMatch(field.defKey, /(_vault_|_masked_value|_blind_index|vault_match_ref)/);
    }
  }
  const contact = fieldMap(requireEntity(entities, "party_contact"));
  for (const fieldKey of ["contact_vault_token", "contact_masked_value"]) assert.ok(contact.has(fieldKey));
  for (const fieldKey of ["vault_record_version", "vault_metadata_hash"]) assert.equal(contact.has(fieldKey), false);
  assert.equal(
    contact.get("contact_vault_token").comment,
    "Vault 联系方式标量原值的逻辑引用，不建立跨库物理外键；EMAIL 对应 EMAIL_ADDRESS_V1，PHONE 对应 PHONE_E164_V1。",
  );
  assertCommentClauses(requireEntity(entities, "party_contact").comment, [
    "Email 使用 EMAIL_ADDRESS_V1 标量 Schema",
    "Phone 使用 PHONE_E164_V1 标量 Schema",
  ], "Party Contact Vault scalar schemas");
  const prohibited = /(pan|cvv|cvc|track_data|pin_block|ciphertext|dek|private_key|password|secret_value)/i;
  for (const entity of entities.values()) for (const field of entity.fields) assert.doesNotMatch(field.defKey, prohibited);
});

test("ships the six + three approved Diagram Groups with exact membership and every relation once", () => {
  for (const service of serviceModels) {
    assertDiagramContract(readServiceModel(service), serviceDiagramSpec(service));
  }
});

test("Diagram evaluator rejects deleting every Diagram Group", () => {
  const model = diagramContractFixture();
  assertDiagramContract(model);
  model.diagrams = [];
  assert.throws(() => assertDiagramContract(model), /Diagram Group count drift/u);
});

test("Diagram evaluator rejects deleting one local relation cell", () => {
  const model = diagramContractFixture();
  assertDiagramContract(model);
  const diagram = model.diagrams.find((candidate) =>
    candidate.canvasData.cells.some((cell) => cell.shape === "erdRelation"));
  const relationIndex = diagram.canvasData.cells.findIndex(
    (cell) => cell.shape === "erdRelation",
  );
  diagram.canvasData.cells.splice(relationIndex, 1);
  assert.throws(
    () => assertDiagramContract(model),
    /Diagram local relation coverage drift/u,
  );
});

test("Nullability evaluator rejects a nullable retained audit timestamp", () => {
  const model = nullabilityContractFixture();
  assertNullabilityContract(model);
  fieldMap(entityMap(model).get("party")).get("create_time").notNull = false;
  assert.throws(
    () => assertNullabilityContract(model),
    /required nullability drift: party\.create_time/u,
  );
});

test("Nullability evaluator rejects a nullable required source-relation field", () => {
  const model = nullabilityContractFixture();
  assertNullabilityContract(model);
  fieldMap(entityMap(model).get("entity_source_relation")).get("entity_no").notNull = false;
  assert.throws(
    () => assertNullabilityContract(model),
    /required nullability drift: entity_source_relation\.entity_no/u,
  );
});

test("Nullability evaluator rejects a nullable retained business number", () => {
  const model = nullabilityContractFixture();
  assertNullabilityContract(model);
  qualifiedField(model, "party.party_no").notNull = false;
  assert.throws(
    () => assertNullabilityContract(model),
    /required nullability drift: party\.party_no/u,
  );
});

test("Nullability evaluator rejects a nullable retained local FK", () => {
  const model = nullabilityContractFixture();
  assertNullabilityContract(model);
  qualifiedField(model, "client.party_id").notNull = false;
  assert.throws(
    () => assertNullabilityContract(model),
    /required nullability drift: client\.party_id/u,
  );
});

test("Client Application evaluator rejects weakened account uniqueness", () => {
  const model = structuredClone(readModel());
  assertClientApplicationContract(model);
  const application = entityMap(model).get("client_application");
  application.indexes = application.indexes.filter(index => !index.fields.some(entry => application.fields.find(field => field.id === entry.fieldDefKey)?.defKey === "requested_by_account_id"));
  assert.throws(() => assertClientApplicationContract(model), /Client account active application uniqueness/u);
});

test("Operating Entity Application evaluator rejects a weakened approval/source matrix", () => {
  const model = applicationBusinessContractFixture();
  assertOperatingEntityApplicationContract(model);
  const application = entityMap(model).get("operating_entity_application");
  application.comment = application.comment.replace(
    "status='APPROVED' AND approved_source_relation_id IS NOT NULL",
    "status='APPROVED' AND approved_source_relation_id IS NULL",
  );
  assert.throws(
    () => assertOperatingEntityApplicationContract(model),
    /Operating Entity Application approval\/source CHECK/u,
  );
});

test("Field type mapping evaluator accepts the approved 40-table fixture", () => {
  assertFieldTypeMappingContract(fieldTypeMappingContractFixture());
});

test("Field type mapping evaluator rejects a wrong Base Type", () => {
  const model = fieldTypeMappingContractFixture();
  assertFieldTypeMappingContract(model);
  const mappings = new Map(model.dataTypeMapping.mappings.map((mapping) => [
    mapping.defKey,
    mapping.id,
  ]));
  qualifiedField(model, "party.party_no").baseType = mappings.get("BIGINT");
  assert.throws(
    () => assertFieldTypeMappingContract(model),
    /independent physical\/Base Type contract drift: party\.party_no/u,
  );
});

test("Field type mapping evaluator rejects matching wrong physical and Base Types", () => {
  const model = fieldTypeMappingContractFixture();
  assertFieldTypeMappingContract(model);
  const mappings = new Map(model.dataTypeMapping.mappings.map((mapping) => [
    mapping.defKey,
    mapping.id,
  ]));
  Object.assign(qualifiedField(model, "party.party_no"), {
    type: "BIGINT",
    len: "",
    scale: "",
    baseType: mappings.get("BIGINT"),
  });
  assert.throws(
    () => assertFieldTypeMappingContract(model),
    /independent physical\/Base Type contract drift: party\.party_no/u,
  );
});

test("Field type mapping evaluator rejects a wrong Domain", () => {
  const model = fieldTypeMappingContractFixture();
  assertFieldTypeMappingContract(model);
  const domains = new Map(model.domains.map((domain) => [domain.defKey, domain.id]));
  qualifiedField(model, "party.party_id").domain = domains.get("Money");
  assert.throws(
    () => assertFieldTypeMappingContract(model),
    /domain assignment drift: party\.party_id/u,
  );
});

test("Field type mapping evaluator rejects DATE reusing the DateTime mapping", () => {
  const model = fieldTypeMappingContractFixture();
  assertFieldTypeMappingContract(model);
  const mappings = new Map(model.dataTypeMapping.mappings.map((mapping) => [
    mapping.defKey,
    mapping.id,
  ]));
  qualifiedField(model, "individual_profile.birth_date").baseType = mappings.get("date");
  assert.throws(
    () => assertFieldTypeMappingContract(model),
    /independent physical\/Base Type contract drift: individual_profile\.birth_date/u,
  );
});

test("Field type mapping evaluator rejects Assignment MCC shortened from four to three digits", () => {
  const model = fieldTypeMappingContractFixture();
  assertFieldTypeMappingContract(model);
  qualifiedField(model, "operating_entity_scope.mcc_code").len = 3;
  assert.throws(
    () => assertFieldTypeMappingContract(model),
    /independent physical\/Base Type contract drift: operating_entity_scope\.mcc_code/u,
  );
});

test("direct Agreement and Legal facts use immutable-record metadata and complete constraint matrices", () => {
  const model = generateClientContractModel();
  const entities = entityMap(model);
  const comment = (tableKey, fieldKey) => fieldMap(entities.get(tableKey)).get(fieldKey).comment;

  assert.match(comment("agreement_party", "agreement_party_id"), /Party角色/u);
  assert.doesNotMatch(comment("agreement_party", "agreement_party_id"), /合同版本/u);
  assert.match(comment("agreement_party", "acceptance_reference"), /平台生成.*全局唯一.*稳定/u);
  assert.match(comment("agreement_party", "evidence_document_id"), /证据/u);
  assert.match(comment("consumer_legal_document", "legal_document_id"), /独立.*可发布.*法律文档/u);
  assert.doesNotMatch(comment("consumer_legal_document", "legal_document_id"), /Scope/u);

  const agreementComment = entities.get("agreement").comment;
  assert.match(agreementComment, /DRAFT\/PENDING_SIGNATURE\/CANCELLED 两时间为空/u);
  assert.match(agreementComment, /按 Owner、类型、OE 稳定顺序锁定/u);
  assert.match(agreementComment, /CLIENT 合同 client_id 非空且 local_partner_id 为空，PARTNER 反之/u);
  assert.match(entities.get("agreement_party").comment, /PLATFORM_SIGNATORY.*OPERATING_ENTITY/u);
  assert.match(
    entities.get("commercial_term_plan").comment,
    /PLATFORM_SERVICE_CHARGE.*Local Partner.*复合唯一/u,
  );
  assert.match(
    entities.get("commercial_term_version").comment,
    /PARTNER_SHARE.*PLATFORM_SERVICE_CHARGE.*同一 Local Partner.*PARTNER Agreement/u,
  );
  assert.match(
    entities.get("commercial_term_price").comment,
    /PLATFORM_SERVICE_CHARGE 使用受控 Calculation Base.*残差/u,
  );
  assert.match(
    entities.get("agreement_party").comment,
    /PLATFORM_SERVICE_CHARGE.*恰好一个 PLATFORM_SIGNATORY.*Fail Closed/u,
  );
  const acceptanceComment = entities.get("agreement_party").comment;
  assert.match(acceptanceComment, /CLICK.*SIGNING_DOCUMENT.*E_SIGNATURE.*MANUAL_SIGNATURE.*EXECUTED_DOCUMENT/u);
  assert.match(acceptanceComment, /全部 CLICK_ACCEPTANCE.*无需 EXECUTED_DOCUMENT.*SIGNED/u);
  assert.match(acceptanceComment, /任一 E_SIGNATURE 或 MANUAL_SIGNATURE.*SIGNED.*恰有一份 EXECUTED_DOCUMENT/u);
  const legalComment = entities.get("consumer_legal_document").comment;
  assert.match(legalComment, /DRAFT.*approved_time\/published_time.*均为空.*APPROVED.*approved_time.*非空/u);
  assert.match(legalComment, /EXPIRED\/WITHDRAWN.*effective_to/u);
  assert.match(legalComment, /缺失或并列.*Fail Closed/u);
});

test("CLIENT Price freezes the complete business-day settlement term without a new policy table", () => {
  const model = generateClientContractModel();
  const entities = entityMap(model);
  const agreementEntity = requireEntity(entities, "commercial_term_price");
  const fields = fieldMap(agreementEntity);

  assert.deepEqual(agreementEntity.fields.map((field) => field.defKey), expectedFields.commercial_term_price);
  assert.deepEqual(
    [fields.get("settlement_business_days")?.type, fields.get("settlement_business_days")?.notNull],
    ["INT", false],
  );
  assert.deepEqual(
    [fields.get("cutoff_second_of_day")?.type, fields.get("cutoff_second_of_day")?.notNull],
    ["INT", false],
  );
  for (const fieldKey of ["business_calendar_code", "settlement_timezone"]) {
    assert.deepEqual([fields.get(fieldKey)?.type, fields.get(fieldKey)?.len], ["VARCHAR", 64]);
    assert.equal(fields.get(fieldKey)?.notNull, false);
    assert.match(fields.get(fieldKey)?.comment ?? "", /取值来源：/u);
  }
  assert.match(agreementEntity.comment, /CLIENT 的四个结算字段全部非空/u);
  assert.match(agreementEntity.comment, /PARTNER 全部为空/u);
  assert.match(agreementEntity.comment, /settlement_business_days>=0/u);
  assert.match(agreementEntity.comment, /cutoff_second_of_day 在 0–86399/u);
  assert.equal(entities.has("settlement_maturity_policy"), false);
  assert.equal(entities.has("business_calendar"), false);
});

test("generator rejects duplicate explicit index names with different field signatures", () => {
  const catalog = readFileSync(
    path.join(workspaceRoot, "agent-docs/database/CLIENT_CONTRACT_DATABASE.md"),
    "utf8",
  ).replace(
    "`uk_agreement_no(agreement_no)`；",
    "`uk_duplicate_name(agreement_no)`、`uk_duplicate_name(agreement_name)`；",
  );
  assert.throws(
    () => generateClientContractModel({ catalogMarkdown: catalog }),
    /Duplicate index name in agreement: uk_duplicate_name/u,
  );
});

test("CCT authority defines Profile-priced Commercial Terms without intermediate hashes", () => {
  const authority = readFileSync(
    path.join(workspaceRoot, "agent-docs/database/CLIENT_CONTRACT_DATABASE.md"),
    "utf8",
  );

  assert.doesNotMatch(authority, /父 Content Hash 按.*Component Canonical Hash/u);
  assert.match(authority, /`pricing_profile`/u);
  assert.match(authority, /`pricing_profile_code`/u);
  assert.match(authority, /`commercial_term_price`/u);
  assert.match(authority, /同维度多行按 OR、不同维度按 AND/u);
  assert.match(
    authority,
    /Payment Method Profile\/Condition 与 Commercial Term Version\/Price\/Component\/Band 都以结构化行和冻结边界保留历史，不维护中间 Hash/u,
  );
});

test("buildIndexes rejects empty and invalid explicit access-index names while retaining array specs", () => {
  const table = { defKey: "index_fixture", fieldKeys: ["id", "alpha", "beta"], constraints: "" };
  const fields = new Map(["id", "alpha", "beta"].map((fieldKey) => [fieldKey, { id: `field:${fieldKey}` }]));
  assert.deepEqual(
    buildIndexes(table, fields, [["alpha"]]).map((index) => index.defKey),
    ["idx_index_fixture_alpha"],
  );
  assert.throws(
    () => buildIndexes(table, fields, [{ defKey: "", fields: ["alpha"] }]),
    /Index name is required: index_fixture/u,
  );
  assert.throws(
    () => buildIndexes(table, fields, [{ defKey: "wrong-name", fields: ["alpha"] }]),
    /Invalid index name: index_fixture\.wrong-name/u,
  );
});

test("direct metadata manifests expose no removed Agreement or Legal compatibility paths", () => {
  const legacy = /^(?:agreement_version|agreement_version_party|agreement_scope_binding|consumer_legal_document_version|seller_disclosure_version)\./u;
  for (const manifest of [
    FIELD_DESCRIPTION_OVERRIDES,
    CLOSED_ENUM_VALUES,
    CONDITIONAL_VALUE_RULES,
    OPEN_CODE_SOURCES,
  ]) {
    assert.deepEqual(Object.keys(manifest).filter((fieldPath) => legacy.test(fieldPath)), []);
  }
  for (const fieldKey of [
    "agreement_version_id", "agreement_version_party_id", "binding_id", "binding_revision_no",
    "evidence_content_hash", "evidence_file_id", "legal_document_version_id", "scope_key", "scope_type",
  ]) assert.equal(Object.hasOwn(FIELD_METADATA, fieldKey), false, `removed metadata remains: ${fieldKey}`);
});

test("active physical type manifests exclude removed Agreement and Legal revision fields", () => {
  assert.equal(expectedBigIntFieldKeys.has("binding_revision_no"), false);
  assert.doesNotMatch(generatorSource, /^\s*"binding_revision_no",$/mu);
});

test("physical decisions records exactly one generated Model SHA-256", () => {
  for (const service of serviceModels) {
    const physicalDecisions = readFileSync(service.physicalDecisionsPath, "utf8");
    const documentedDigests = [
      ...physicalDecisions.matchAll(/^\| Model SHA-256 \| `([0-9a-f]+)` \|$/gmu),
    ].map((match) => match[1]);

    assert.equal(documentedDigests.length, 1, `${service.key} physical decisions must contain exactly one Model SHA-256`);
    assert.match(documentedDigests[0], /^[0-9a-f]{64}$/u);

    const generatedDigest = createHash("sha256")
      .update(serializeModel(service.generate()))
      .digest("hex");
    assert.equal(documentedDigests[0], generatedDigest, service.key);
  }
});
