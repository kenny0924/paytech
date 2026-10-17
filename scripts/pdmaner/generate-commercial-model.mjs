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
  "payment_method_profile",
  "payment_method_profile_cond",
  "commercial_term_plan",
  "commercial_term_version",
  "commercial_term_price",
  "commercial_term_component",
  "commercial_term_component_band",
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
    ],
  },
  {
    defKey: "pricing_share",
    defName: "Client Pricing and Partner Share",
    tables: [
      "payment_method_profile",
      "payment_method_profile_cond",
      "commercial_term_plan",
      "commercial_term_version",
      "commercial_term_price",
      "commercial_term_component",
      "commercial_term_component_band",
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
  modelName: "Code-MoR Commercial Database V1",
  describe: "Commercial",
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
