import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  generateServiceModel,
  messagingConsumerEntities,
  runGeneratorCli,
  serializeModel,
} from "./client-contract-model-core.mjs";

export {
  buildIndexes,
  logicalTableKey,
  physicalTableKey,
} from "./client-contract-model-core.mjs";

// Client Master owns Party, roles, onboarding, OE Scope/Affiliation/Source and
// Consumer Legal facts from the shared Client & Contract catalog, plus the
// Client stored-card setting moved from Commerce. Each service database keeps
// its own BCR and compensation_event copy. command_idempotency is dropped (S10).
// Step 08 adds the paytech-messaging consumer tables t_consumed_event / t_consumed_aggregate_version.
export const CLIENT_MASTER_CATALOG_TABLES = Object.freeze([
  "party",
  "party_profile",
  "individual_profile",
  "organization_profile",
  "party_identity_key",
  "party_profile_identifier",
  "party_address",
  "party_contact",
  "party_relation",
  "client",
  "local_partner",
  "operating_entity",
  "client_business_profile",
  "client_business_classification",
  "client_application",
  "partner_application",
  "operating_entity_application",
  "operating_entity_scope",
  "application_document",
  "client_entity_affiliation",
  "entity_source_relation",
  "consumer_legal_document",
  "seller_disclosure",
  "business_change_record",
  "business_change_version",
  "compensation_event",
]);

// Entity definition copied verbatim (IDs, metadata and physical shape) from the
// former Commerce model (commerce-service/docs/database/commerce.pdma.json, removed in step 03);
// only the shared template headers are re-derived.
const CLIENT_STORED_CARD_SETTING_ENTITY = Object.freeze({
  "id": "1A821886-19AB-5FA1-AAAF-C942297031CA",
  "env": {
    "base": {
      "nameSpace": "",
      "codeRoot": ""
    }
  },
  "defKey": "t_client_stored_card_setting",
  "defName": "Client 存卡开通",
  "comment": "按 Client 开通存卡能力（CCT 暂无按 Client 配置落点时的 Commerce 本地配置）；首期由初始化 DML 或内部调用维护，运营页面后续提供。",
  "properties": {},
  "sysProps": {
    "nameTemplate": "{defKey}[{defName}]"
  },
  "notes": {},
  "fields": [
    {
      "defKey": "client_id",
      "defName": "Client 标识",
      "comment": "平台 Client 的内部唯一标识；每个 Client 至多一行，没有记录视为未开通。",
      "type": "BIGINT",
      "len": "",
      "scale": "",
      "primaryKey": true,
      "notNull": true,
      "autoIncrement": false,
      "defaultValue": "",
      "hideInGraph": false,
      "refDict": "",
      "baseType": "B5503A77-01B9-4086-8D7B-EB9106F91540",
      "extProps": {},
      "domain": "B55BA67A-705D-4D0D-AACA-87373A9D6DA7",
      "id": "C3079BA3-1ABA-59E5-9681-B7F8A865F8FE"
    },
    {
      "defKey": "stored_card_enabled",
      "defName": "存卡开通",
      "comment": "是否开通存卡与已存卡支付；1 开通，0 关闭。关闭后已存卡保留但不展示、不可使用。",
      "type": "TINYINT",
      "len": 1,
      "scale": "",
      "primaryKey": false,
      "notNull": true,
      "autoIncrement": false,
      "defaultValue": "",
      "hideInGraph": false,
      "refDict": "",
      "baseType": "1D764C4A-6F9F-421E-B11A-6F3E23B51811",
      "extProps": {},
      "domain": "",
      "id": "C27EDAA3-96AA-5B8F-9BB2-F2710142116F"
    },
    {
      "defKey": "version",
      "defName": "版本",
      "comment": "可变业务行并发更新使用的单调乐观锁版本。",
      "type": "BIGINT",
      "len": "",
      "scale": "",
      "primaryKey": false,
      "notNull": true,
      "autoIncrement": false,
      "defaultValue": "",
      "hideInGraph": false,
      "refDict": "",
      "baseType": "B5503A77-01B9-4086-8D7B-EB9106F91540",
      "extProps": {},
      "domain": "",
      "id": "F37F0C54-74D5-5E93-8351-DD22C1C1F67D"
    },
    {
      "defKey": "create_time",
      "defName": "创建时间",
      "comment": "数据库记录创建的 UTC 微秒精度时间。",
      "type": "DATETIME",
      "len": 6,
      "scale": "",
      "primaryKey": false,
      "notNull": true,
      "autoIncrement": false,
      "defaultValue": "",
      "hideInGraph": false,
      "refDict": "",
      "baseType": "89D69E81-EA34-42EE-9FA2-93B8BD27E098",
      "extProps": {},
      "domain": "7CFFA0D3-6A93-4DDC-BC10-DF21211064DC",
      "id": "76A1DE4E-E12F-5600-941E-CFD1E36C52E6"
    },
    {
      "defKey": "update_time",
      "defName": "更新时间",
      "comment": "数据库记录最近一次持久化更新的 UTC 微秒精度时间。",
      "type": "DATETIME",
      "len": 6,
      "scale": "",
      "primaryKey": false,
      "notNull": true,
      "autoIncrement": false,
      "defaultValue": "",
      "hideInGraph": false,
      "refDict": "",
      "baseType": "89D69E81-EA34-42EE-9FA2-93B8BD27E098",
      "extProps": {},
      "domain": "7CFFA0D3-6A93-4DDC-BC10-DF21211064DC",
      "id": "8DD1F15F-01D7-50B3-BC07-1975FCED3411"
    }
  ],
  "correlations": [],
  "indexes": [],
  "type": "P"
});

export const CLIENT_MASTER_DIAGRAM_GROUPS = Object.freeze([
  {
    defKey: "party_identity",
    defName: "Party and Identity",
    tables: [
      "party",
      "party_profile",
      "individual_profile",
      "organization_profile",
      "party_identity_key",
      "party_profile_identifier",
      "party_address",
      "party_contact",
      "party_relation",
    ],
  },
  {
    defKey: "roles_onboarding",
    defName: "Roles and Onboarding",
    tables: [
      "client",
      "local_partner",
      "operating_entity",
      "client_business_profile",
      "client_business_classification",
      "client_application",
      "partner_application",
      "operating_entity_application",
      "application_document",
    ],
  },
  {
    defKey: "entity_assignment",
    defName: "OE Scope, Affiliation and Source",
    tables: [
      "client_entity_affiliation",
      "operating_entity_scope",
      "entity_source_relation",
    ],
  },
  {
    defKey: "consumer_legal",
    defName: "Consumer Legal and Seller Disclosure",
    tables: [
      "consumer_legal_document",
      "seller_disclosure",
    ],
  },
  {
    defKey: "client_settings",
    defName: "Client Settings",
    tables: [
      "client_stored_card_setting",
    ],
  },
  {
    defKey: "history_reliability",
    defName: "Local History and Reliability",
    tables: [
      "business_change_record",
      "business_change_version",
      "compensation_event",
      "consumed_event",
      "consumed_aggregate_version",
    ],
  },
]);

export const CLIENT_MASTER_SERVICE = Object.freeze({
  label: "Client Master",
  modelName: "Code-MoR Client Master Database V1",
  describe: "Client Master",
  outputPath: "paytech/client-master/docs/database/client-master.pdma.json",
  tables: CLIENT_MASTER_CATALOG_TABLES,
  diagramGroups: CLIENT_MASTER_DIAGRAM_GROUPS,
  additionalEntities: (model) => {
    const { fields, correlations, indexes, type, ...head } = structuredClone(CLIENT_STORED_CARD_SETTING_ENTITY);
    // Step 08: paytech-messaging consumer tables for the ClientReviewDecided subscription (design/08 §4).
    return [{ ...head, headers: structuredClone(model.profile.headers), fields, correlations, indexes, type },
      ...messagingConsumerEntities(model)];
  },
});

export function generateClientMasterModel(options = {}) {
  return generateServiceModel(CLIENT_MASTER_SERVICE, options);
}

export function serializeClientMasterModel(model = generateClientMasterModel()) {
  return serializeModel(model);
}

if (path.resolve(process.argv[1] ?? "") === fileURLToPath(import.meta.url)) {
  runGeneratorCli(CLIENT_MASTER_SERVICE, generateClientMasterModel);
}
