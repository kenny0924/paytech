import { TABLES as IDENTITY_ACCESS_TABLES } from "./manifests/01-identity-access.mjs";
import { TABLES as FILE_DOCUMENT_TABLES } from "./manifests/02-file-document.mjs";
import { TABLES as NOTIFICATION_TABLES } from "./manifests/03-notification.mjs";
import { TABLES as WEBHOOK_TABLES } from "./manifests/04-webhook.mjs";
import { TABLES as AUDIT_SECURITY_TABLES } from "./manifests/05-audit-security.mjs";
import { TABLES as GATEWAY_CONTROL_TABLES } from "./manifests/06-gateway.mjs";
import { TABLES as RELIABILITY_TABLES } from "./manifests/08-reliability.mjs";
import { TABLES as REFERENCE_DATA_TABLES } from "./manifests/10-reference-data.mjs";
import { TABLES as WORKFLOW_TABLES } from "./manifests/11-workflow.mjs";

export const TABLE_GROUPS = Object.freeze({
  identity_access: IDENTITY_ACCESS_TABLES,
  file_document: FILE_DOCUMENT_TABLES,
  notification: NOTIFICATION_TABLES,
  webhook: WEBHOOK_TABLES,
  audit_security: AUDIT_SECURITY_TABLES,
  gateway_control: GATEWAY_CONTROL_TABLES,
  reliability: RELIABILITY_TABLES,
  reference_data: REFERENCE_DATA_TABLES,
  workflow: WORKFLOW_TABLES,
});

export const TABLES = Object.freeze(Object.values(TABLE_GROUPS).flat());
export const RELATIONS = Object.freeze(TABLES.flatMap((table) => table.relations));

const expectedGroupSizes = Object.freeze({
  identity_access: 19,
  file_document: 15,
  notification: 7,
  webhook: 5,
  audit_security: 8,
  gateway_control: 6,
  reliability: 2,
  reference_data: 1,
  workflow: 10,
});

export const EXPECTED_TABLE_TOTAL = Object.values(expectedGroupSizes)
  .reduce((total, size) => total + size, 0);

for (const [groupKey, expectedSize] of Object.entries(expectedGroupSizes)) {
  const actualSize = TABLE_GROUPS[groupKey].length;
  if (actualSize !== expectedSize) {
    throw new Error(`Platform Support 图组 ${groupKey} 必须有 ${expectedSize} 张表，当前为 ${actualSize}`);
  }
}

if (TABLES.length !== EXPECTED_TABLE_TOTAL) {
  throw new Error(`Platform Support 必须有 ${EXPECTED_TABLE_TOTAL} 张表，当前为 ${TABLES.length}`);
}

if (new Set(TABLES.map((table) => table.defKey)).size !== TABLES.length) {
  throw new Error("Platform Support 逻辑表名重复");
}

if (new Set(TABLES.map((table) => table.physicalKey)).size !== TABLES.length) {
  throw new Error("Platform Support 物理表名重复");
}
