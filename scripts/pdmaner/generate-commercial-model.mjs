import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  generateServiceModel,
  runGeneratorCli,
  serializeModel,
} from "./client-contract-model-core.mjs";

export {
  buildIndexes,
  logicalTableKey,
  physicalTableKey,
} from "./client-contract-model-core.mjs";

// Commercial owns Agreements and Client/Partner pricing from the shared
// Client & Contract catalog; each service database keeps its own BCR and
// compensation_event copy. command_idempotency is dropped (decision S10).
export const COMMERCIAL_TABLES = Object.freeze([
  "agreement",
  "agreement_party",
  "agreement_document_ref",
  "agreement_operating_entity",
  "agreement_product",
  "agreement_capability",
  "agreement_capability_limit",
  "product",
  "product_capability",
  "product_fee_event",
  "product_capability_dimension",
  "pricing_profile",
  "pricing_profile_cond",
  "commercial_term_plan",
  "commercial_term_version",
  "commercial_term_price",
  "commercial_term_component",
  "commercial_term_component_band",
  "term_version_acceptance",
  "business_change_record",
  "business_change_version",
  "compensation_event",
]);

export const COMMERCIAL_DIAGRAM_GROUPS = Object.freeze([
  {
    defKey: "agreement",
    defName: "Agreement",
    tables: [
      "agreement",
      "agreement_party",
      "agreement_document_ref",
      "agreement_operating_entity",
      "agreement_product",
      "agreement_capability",
      "agreement_capability_limit",
    ],
  },
  {
    defKey: "product_catalog",
    defName: "Product Catalog",
    tables: [
      "product",
      "product_capability",
      "product_fee_event",
      "product_capability_dimension",
    ],
  },
  {
    defKey: "pricing_share",
    defName: "Client Pricing and Partner Share",
    tables: [
      "pricing_profile",
      "pricing_profile_cond",
      "commercial_term_plan",
      "commercial_term_version",
      "commercial_term_price",
      "commercial_term_component",
      "commercial_term_component_band",
      "term_version_acceptance",
    ],
  },
  {
    defKey: "history_reliability",
    defName: "Local History and Reliability",
    tables: [
      "business_change_record",
      "business_change_version",
      "compensation_event",
    ],
  },
]);

export const COMMERCIAL_SERVICE = Object.freeze({
  label: "Commercial",
  modelName: "commercial",
  describe: "合同及参与方、产品目录、合同订阅与开通项、定价画像、商业条款方案 / 版本 / 价格 / 组件 / 阶梯档位",
  outputPath: "paytech/commercial/docs/database/commercial.pdma.json",
  tables: COMMERCIAL_TABLES,
  diagramGroups: COMMERCIAL_DIAGRAM_GROUPS,
});

export function generateCommercialModel(options = {}) {
  return generateServiceModel(COMMERCIAL_SERVICE, options);
}

export function serializeCommercialModel(model = generateCommercialModel()) {
  return serializeModel(model);
}

if (path.resolve(process.argv[1] ?? "") === fileURLToPath(import.meta.url)) {
  runGeneratorCli(COMMERCIAL_SERVICE, generateCommercialModel);
}
