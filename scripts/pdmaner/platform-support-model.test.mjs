import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { isForbiddenSecretField as isForbiddenGeneratorSecretField } from "./platform-support/generate-platform-support-model.mjs";
import { redisIdentityHmacContradictions } from "./platform-support-test-oracle/text-contracts.mjs";

import {
  APPROVED_DIAGRAMS,
  APPROVED_TABLE_GROUPS,
  APPROVED_TABLES,
  EXPECTED_CHECK_COUNT,
  EXPECTED_CHECK_POLICY_DIGEST,
  EXPECTED_GROUP_SIZES,
  EXPECTED_HASH_FIELDS,
  EXPECTED_MODEL_DIGEST,
  EXPECTED_STRUCTURAL_DIGEST,
  EXPECTED_TABLE_TOTAL,
  EXPECTED_TEMPLATE_DIGEST,
  EXPECTED_TOTALS,
  FILE_REFERENCE_TUPLES,
  FORBIDDEN_FIELDS_BY_TABLE,
  FORBIDDEN_TABLES,
  GATEWAY_CONTROL_CONTRACTS,
  NO_AUDIT_TABLES,
  REMOVED_PLATFORM_TABLES,
  REQUIRED_FIELDS,
  REQUIRED_COMPOSITE_FKS,
  REQUIRED_CHECK_CONTRACTS,
  REQUIRED_GATEWAY_ROUTE_COMPATIBILITY,
  isForbiddenSecretField,
} from "./platform-support-test-oracle/schema.oracle.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const paths = Object.freeze({
  template: path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json"),
  manifest: path.join(workspaceRoot, "scripts/pdmaner/platform-support/manifest.mjs"),
  policy: path.join(workspaceRoot, "scripts/pdmaner/platform-support/model-policy.mjs"),
  generator: path.join(workspaceRoot, "scripts/pdmaner/platform-support/generate-platform-support-model.mjs"),
  model: path.join(workspaceRoot, "platform-support-service/docs/database/platform-support.pdma.json"),
  decisions: path.join(workspaceRoot, "platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md"),
  design: path.join(workspaceRoot, "docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md"),
  dynamicGatewayDesign: path.join(workspaceRoot, "docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md"),
  logicalDatabase: path.join(workspaceRoot, "agent-docs/database/PLATFORM_SUPPORT_DATABASE.md"),
});
const historicalPlatformAuthorityDocuments = Object.freeze([
  path.join(workspaceRoot, "agent-docs/plans/2026-08-27-platform-support-balanced-physical-model-design.md"),
  path.join(workspaceRoot, "agent-docs/plans/2026-08-27-platform-support-pdmaner-implementation-plan.md"),
  path.join(workspaceRoot, "agent-docs/plans/2026-08-29-platform-support-operations-authentication-requirements.md"),
  path.join(workspaceRoot, "agent-docs/plans/2026-08-30-platform-unified-human-authentication-pdmaner-implementation-plan.md"),
  path.join(workspaceRoot, "docs/superpowers/plans/2026-08-30-platform-unified-human-authentication.md"),
  path.join(workspaceRoot, "docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md"),
]);
const preCutoverGatewayAuthorityDocuments = Object.freeze([
  path.join(workspaceRoot, "agent-docs/plans/2026-08-29-operations-api-gateway-design.md"),
  path.join(workspaceRoot, "docs/superpowers/plans/2026-08-29-operations-api-gateway.md"),
  path.join(workspaceRoot, "docs/superpowers/plans/2026-08-30-operations-gateway-hardening.md"),
  path.join(workspaceRoot, "docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md"),
]);

const idPattern = /^[A-F0-9]{8}-[A-F0-9]{4}-5[A-F0-9]{3}-[89AB][A-F0-9]{3}-[A-F0-9]{12}$/u;

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function assertArtifactsExist() {
  for (const [name, artifactPath] of Object.entries(paths)) {
    assert.ok(existsSync(artifactPath), `Platform Support ${name} 尚未生成：${artifactPath}`);
  }
}

function readModel() {
  assertArtifactsExist();
  return JSON.parse(readFileSync(paths.model, "utf8"));
}

function objectKeysDeep(value) {
  if (!value || typeof value !== "object") return [];
  if (Array.isArray(value)) return value.flatMap(objectKeysDeep);
  return Object.entries(value).flatMap(([key, nested]) => [key, ...objectKeysDeep(nested)]);
}

function fieldNames(model, tableKey) {
  const entity = model.entities.find((item) => item.defKey === tableKey);
  assert.ok(entity, `缺少表：${tableKey}`);
  return entity.fields.map((field) => field.defKey);
}

function assertRsaPublicJwk(serializedJwk) {
  const jwk = JSON.parse(serializedJwk);
  assert.equal(typeof jwk, "object", "JWK 必须是对象");
  assert.ok(jwk && !Array.isArray(jwk), "JWK 必须是非数组对象");
  assert.equal(jwk.kty, "RSA", "持久化 JWK 必须是 RSA 公钥");
  for (const member of Object.keys(jwk)) {
    assert.ok(["kty", "n", "e"].includes(member), `JWK 不得保存非 RSA 公钥成员：${member}`);
  }
  assert.equal(typeof jwk.n, "string", "RSA 公钥必须包含模数 n");
  assert.equal(typeof jwk.e, "string", "RSA 公钥必须包含指数 e");
}

function assertSecretMaterialContract(fields) {
  for (const field of fields) {
    assert.equal(isForbiddenSecretField(field.defKey), false, `禁止保存敏感字段：${field.defKey}`);
    if (field.defKey === "public_jwk_canonical_json" && field.serializedValue) {
      assertRsaPublicJwk(field.serializedValue);
    }
  }
}

function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  const relations = model.entities.flatMap((entity) => entity.correlations);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: relations.length,
    oneToOneRelations: relations.filter((relation) => relation.myRows === "1").length,
    diagramGroups: model.diagrams.length,
  };
}

// Test-owned projection: deliberately does not import the production generator or manifests.
function structuralProjection(model) {
  const mysqlMappingId = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
  const javaMappingId = "797A1496-D649-4261-89B4-544132EC3F36";
  const mybatisMappingId = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
    mappings: model.dataTypeMapping.mappings.map((mapping) => ({
      id: mapping.id,
      defKey: mapping.defKey,
      mysql: mapping[mysqlMappingId] ?? null,
      java: mapping[javaMappingId] ?? null,
      mybatis: mapping[mybatisMappingId] ?? null,
    })),
    domains: model.domains.map(({ id, defKey, applyFor, len, scale }) => ({ id, defKey, applyFor, len, scale })),
    namingRules: model.namingRules.map(({ id, defName, programCode }) => ({ id, defName, programCode })),
    entities: model.entities.map((entity) => {
      const fieldsById = new Map(entity.fields.map((field) => [field.id, field]));
      return {
        id: entity.id,
        defKey: entity.defKey,
        defName: entity.defName,
        comment: entity.comment,
        properties: {
          logicalTableKey: entity.properties?.logicalTableKey,
          compositeForeignKeys: entity.properties?.compositeForeignKeys ?? [],
        },
        fields: entity.fields.map(({
          id, defKey, defName, comment, type, len, scale, primaryKey,
          notNull, autoIncrement, defaultValue, baseType, domain, extProps,
        }) => ({
          id, defKey, defName, comment, type, len, scale, primaryKey,
          notNull, autoIncrement, defaultValue, baseType, domain, extProps,
        })),
        indexes: entity.indexes.map((index) => ({
          id: index.id,
          defKey: index.defKey,
          unique: index.unique,
          fields: index.fields.map((entry) => fieldsById.get(entry.fieldDefKey)?.defKey),
        })),
        correlations: entity.correlations.map((relation) => {
          const parent = entitiesById.get(relation.refEntity);
          return {
            id: relation.id,
            childField: fieldsById.get(relation.myField)?.defKey,
            parentTable: parent?.defKey,
            parentField: parent?.fields.find((field) => field.id === relation.refField)?.defKey,
            myRows: relation.myRows,
            refRows: relation.refRows,
          };
        }),
      };
    }),
    diagrams: model.diagrams.map((diagram) => ({
      id: diagram.id,
      defKey: diagram.defKey,
      defName: diagram.defName,
      primaryTables: diagram.properties?.primaryTables ?? [],
      referenceTables: diagram.properties?.referenceTables ?? [],
      relations: diagram.canvasData.cells
        .filter((cell) => cell.shape === "erdRelation")
        .map(({ id, originKey, relation }) => ({ id, originKey, relation })),
    })),
  };
}

test("Platform Support Manifest contains exactly 72 approved tables in eight groups", async () => {
  const { TABLES, TABLE_GROUPS } = await import("./platform-support/manifest.mjs");
  assert.equal(Object.keys(APPROVED_TABLE_GROUPS).length, 8);
  assert.deepEqual(
    Object.fromEntries(Object.entries(APPROVED_TABLE_GROUPS).map(([key, tables]) => [key, tables.length])),
    EXPECTED_GROUP_SIZES,
  );
  assert.deepEqual(
    Object.fromEntries(Object.entries(TABLE_GROUPS).map(([key, tables]) => [key, tables.length])),
    EXPECTED_GROUP_SIZES,
  );
  assert.equal(APPROVED_TABLES.length, EXPECTED_TABLE_TOTAL);
  assert.equal(TABLES.length, EXPECTED_TABLE_TOTAL);
  assert.equal(new Set(APPROVED_TABLES).size, EXPECTED_TABLE_TOTAL);
  assert.ok(APPROVED_TABLES.every((table) => /^t_[a-z][a-z0-9_]*$/u.test(table)));
  assert.ok(APPROVED_TABLES.every((table) => table.length <= 32));
  assert.deepEqual(TABLES.map((table) => `t_${table.physicalKey}`).sort(), [...APPROVED_TABLES].sort());
  assert.ok(FORBIDDEN_TABLES.every((table) => !APPROVED_TABLES.includes(table)));
});

test("Platform Support Manifest has the exact consumer-driven hash allowlist", async () => {
  const { TABLES } = await import("./platform-support/manifest.mjs");
  const actualHashFields = TABLES
    .flatMap((table) => table.fields
      .filter((field) => field.defKey.endsWith("_hash"))
      .map((field) => `${table.defKey}.${field.defKey}`))
    .sort();
  assert.deepEqual(actualHashFields, EXPECTED_HASH_FIELDS);
  assert.equal(actualHashFields.length, 28);
});

test("Platform Support tenant-owned roots reject obsolete ownership and version tables", async () => {
  const { TABLES } = await import("./platform-support/manifest.mjs");
  const tableByKey = new Map(TABLES.map((table) => [table.defKey, table]));
  const physicalTables = new Set(TABLES.map((table) => table.physicalKey));
  const tablesEndingVer = TABLES
    .filter((table) => table.defKey.endsWith("_ver") || table.physicalKey.endsWith("_ver"))
    .map((table) => table.physicalKey)
    .sort();
  const clientIdFields = TABLES
    .flatMap((table) => table.fields
      .filter((field) => field.defKey === "client_id")
      .map((field) => `${table.defKey}.${field.defKey}`))
    .sort();

  assert.deepEqual(tablesEndingVer, []);
  assert.deepEqual(REMOVED_PLATFORM_TABLES.filter((table) => physicalTables.has(table)), []);
  assert.deepEqual(clientIdFields, []);
  assert.equal(tableByKey.get("access_tenant")?.fields.some((field) => field.defKey === "owner_ref_type"), false);
  assert.equal(tableByKey.get("api_credential")?.fields.some((field) => field.defKey === "scope_hash"), false);
  assert.equal(tableByKey.get("api_credential")?.fields.some((field) => field.defKey === "key_verifier_phc"), true);
});

test("Platform Support Manifest DSL rejects incomplete and invalid tables", async () => {
  assert.ok(existsSync(paths.manifest), `Platform Support Manifest 尚未生成：${paths.manifest}`);
  const { defineTable, field, primaryKey, uniqueIndex } = await import("./platform-support/schema-dsl.mjs");
  assert.throws(() => defineTable({
    key: "missing_metadata",
    name: "Missing",
    comment: "Missing",
    fields: [primaryKey("id", "主键", "测试主键")],
  }), /中文/u);
  assert.throws(() => defineTable({
    key: "table_name_that_is_longer_than_the_physical_database_limit",
    name: "超长表",
    comment: "验证物理表名长度限制",
    fields: [primaryKey("id", "主键", "测试主键")],
  }), /32/u);
  assert.throws(() => field(
    "field_name_that_is_longer_than_the_physical_database_limit",
    "VARCHAR(64)",
    "NN",
    "超长字段",
    "验证物理字段名长度限制",
  ), /32/u);
  assert.throws(() => uniqueIndex("idx_wrong_prefix", "id"), /索引/u);
});

test("Platform Support PDManer production artifacts are ready for validation", () => {
  assertArtifactsExist();
});

test("Platform Support model is current and preserves the shared template", () => {
  assertArtifactsExist();
  assert.equal(sha256(readFileSync(paths.template)), EXPECTED_TEMPLATE_DIGEST);
  const result = spawnSync(process.execPath, [paths.generator, "--check"], {
    cwd: workspaceRoot,
    encoding: "utf8",
  });
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /Platform Support model is up to date/u);
  assert.ok(EXPECTED_MODEL_DIGEST, "模型摘要尚未冻结");
  assert.equal(sha256(readFileSync(paths.model)), EXPECTED_MODEL_DIGEST);
});

test("Platform Support model matches independent totals and structural digest", () => {
  const model = readModel();
  assert.ok(EXPECTED_TOTALS, "模型统计尚未冻结");
  assert.ok(EXPECTED_STRUCTURAL_DIGEST, "结构摘要尚未冻结");
  assert.deepEqual(modelTotals(model), EXPECTED_TOTALS);
  assert.equal(sha256(JSON.stringify(structuralProjection(model))), EXPECTED_STRUCTURAL_DIGEST);
});

test("Platform Support model has the exact approved table and diagram sets", () => {
  const model = readModel();
  assert.deepEqual(model.entities.map((entity) => entity.defKey).sort(), [...APPROVED_TABLES].sort());
  assert.deepEqual(
    Object.fromEntries(model.diagrams.map((diagram) => [diagram.defKey, diagram.defName])),
    APPROVED_DIAGRAMS,
  );
  assert.ok(FORBIDDEN_TABLES.every((table) => !model.entities.some((entity) => entity.defKey === table)));
});

test("Platform Support unified human authentication keeps deployment IdP configuration out of MySQL", () => {
  const model = readModel();
  const names = new Set(model.entities.map(({ defKey }) => defKey));
  for (const table of [
    "t_user_account", "t_password_credential", "t_federated_identity",
    "t_authentication_challenge", "t_auth_session", "t_refresh_token_generation",
    "t_jwt_signing_key", "t_tenant_invitation",
  ]) {
    assert.ok(names.has(table), `missing ${table}`);
  }
  for (const table of ["t_oidc_provider", "t_idp_connection", "t_user_identity", "t_gateway_route_ver"]) {
    assert.equal(names.has(table), false, `removed table remains: ${table}`);
  }
  assert.equal(fieldNames(model, "t_user_account").includes("email_blind_index"), false);
  assert.equal(fieldNames(model, "t_password_credential").includes("password_verifier_phc"), true);
  assertSecretMaterialContract(model.entities.flatMap((entity) => entity.fields.map((field) => ({
    defKey: field.defKey,
    serializedValue: field.defKey === "public_jwk_canonical_json" ? field.defaultValue : null,
  }))));
});

test("Platform Support IAM uses exact Tenant Member Role identifiers and derived Role scope key", async () => {
  const { TABLES } = await import("./platform-support/manifests/01-identity-access.mjs");
  const tenantMemberRole = TABLES.find((table) => table.defKey === "tenant_member_role");
  assert.ok(tenantMemberRole, "Identity Manifest 必须定义 tenant_member_role");
  assert.equal(TABLES.some((table) => table.defKey === "member_role"), false);
  assert.equal(`t_${tenantMemberRole.physicalKey}`, "t_tenant_member_role");
  assert.equal(
    tenantMemberRole.fields.find((field) => field.primaryKey)?.defKey,
    "tenant_member_role_id",
  );
  assert.deepEqual(tenantMemberRole.indexes.map((index) => index.defKey), [
    "uk_tenant_member_role",
    "idx_tenant_grant_member_scope",
    "idx_tenant_grant_role_scope",
  ]);

  const role = TABLES.find((table) => table.defKey === "role");
  const scopeKey = role?.fields.find((field) => field.defKey === "scope_key");
  assert.ok(scopeKey?.notNull, "t_role.scope_key 必须保留为非空派生隔离键");
});

test("Platform Support Identity group uses global accounts and tenant-owned credentials", async () => {
  const { TABLES } = await import("./platform-support/manifests/01-identity-access.mjs");
  assert.deepEqual(TABLES.map((table) => table.defKey), [
    "user_account", "password_credential", "federated_identity", "access_tenant",
    "tenant_member", "tenant_invitation", "authentication_challenge", "auth_session",
    "refresh_token_generation", "jwt_signing_key", "role", "permission",
    "tenant_member_role", "role_permission", "api_credential", "api_scope", "api_credential_scope",
    "email_otp_challenge", "email_otp_attempt",
  ]);

  const tableByKey = new Map(TABLES.map((table) => [table.defKey, table]));
  const fieldsOf = (tableKey) => tableByKey.get(tableKey).fields.map((field) => field.defKey);
  assert.ok(fieldsOf("federated_identity").includes("provider_code"));
  assert.ok(fieldsOf("federated_identity").includes("provider_subject"));
  assert.equal(fieldsOf("federated_identity").includes("oidc_provider_id"), false);
  assert.ok(fieldsOf("access_tenant").includes("tenant_type"));
  assert.ok(fieldsOf("access_tenant").includes("owner_ref_id"));
  assert.equal(fieldsOf("access_tenant").includes("owner_ref_type"), false);
  assert.ok(fieldsOf("api_credential").includes("tenant_id"));
  assert.ok(fieldsOf("api_credential").includes("key_prefix"));
  assert.ok(fieldsOf("api_credential").includes("key_verifier_phc"));
  for (const removedField of ["client_id", "scope_hash", "key_hash"]) {
    assert.equal(fieldsOf("api_credential").includes(removedField), false);
  }
  assert.equal(fieldsOf("jwt_signing_key").includes("public_jwk_hash"), false);
  assert.equal(fieldsOf("email_otp_challenge").includes("active_scope_hash"), false);
  assert.deepEqual(
    tableByKey.get("email_otp_challenge").indexes
      .find((index) => index.defKey === "uk_otp_active")?.fields,
    [
      "tenant_scope_key", "subject_type", "subject_ref_hash", "purpose",
      "context_type", "context_ref_hash", "active_slot",
    ],
  );
});

test("Platform Support File and Consumer Document use twelve consumer-driven tables", async () => {
  const { TABLES } = await import("./platform-support/manifests/02-file-document.mjs");
  assert.deepEqual(TABLES.map((table) => table.defKey), [
    "file_upload", "file_scan_policy", "file_scan_requirement", "file_object",
    "file_owner_ref", "file_scan_result", "document_template",
    "document_template_translation", "consumer_document", "document_version",
    "document_delivery", "document_access_grant",
  ]);

  const tableByKey = new Map(TABLES.map((table) => [table.defKey, table]));
  const fieldsOf = (tableKey) => tableByKey.get(tableKey).fields.map((field) => field.defKey);
  for (const revisionTable of ["file_scan_policy", "document_template"]) {
    assert.ok(fieldsOf(revisionTable).includes("revision_no"));
    assert.ok(fieldsOf(revisionTable).includes("active_slot"));
  }
  assert.ok(fieldsOf("consumer_document").includes("tenant_id"));
  assert.equal(fieldsOf("consumer_document").includes("client_id"), false);
  assert.ok(fieldsOf("consumer_document").includes("document_generation_key"));
  assert.ok(fieldsOf("consumer_document").includes("commerce_source_payload_hash"));
  assert.ok(fieldsOf("consumer_document").includes("fiscal_source_payload_hash"));
  for (const removedField of ["source_set_hash", "content_hash"]) {
    assert.equal(fieldsOf("document_version").includes(removedField), false);
  }
  for (const requiredField of [
    "document_template_id", "document_template_translation_id", "renderer_version",
    "file_id", "active_slot", "attempt_count", "lease_owner", "lease_until",
    "fencing_token", "error_code", "version",
  ]) {
    assert.ok(fieldsOf("document_version").includes(requiredField), `missing document_version.${requiredField}`);
  }
  assert.equal(fieldsOf("document_delivery").includes("recipient_hash"), false);
  assert.ok(fieldsOf("document_delivery").includes("notification_delivery_id"));
});

test("Platform Support Notification keeps seven ordinary-email tables and recipient privacy", async () => {
  const { TABLES } = await import("./platform-support/manifests/03-notification.mjs");
  assert.deepEqual(TABLES.map(table => table.defKey), [
    "notification_request", "notification_delivery", "notification_attempt", "notification_template",
    "notification_template_content", "notification_provider", "notification_route",
  ]);
  const tableByKey = new Map(TABLES.map(table => [table.defKey, table]));
  const fieldsOf = key => tableByKey.get(key).fields.map(field => field.defKey);
  const uniqueFields = (table, name) => tableByKey.get(table).indexes.find(index => index.defKey === name)?.fields;
  assert.deepEqual(uniqueFields("notification_request", "uk_notify_source"), ["source_system", "source_event_key"]);
  assert.deepEqual(uniqueFields("notification_delivery", "uk_notify_recipient"), ["request_id", "address_vault_token"]);
  assert.deepEqual(uniqueFields("notification_attempt", "uk_notify_provider_request"), ["provider_request_no", "provider_id"]);
  assert.deepEqual(uniqueFields("notification_template_content", "uk_notify_template_locale"), ["template_id", "locale"]);
  for (const name of ["notification_template", "notification_template_content", "notification_provider", "notification_route"]) {
    assert.ok(fieldsOf(name).includes("version"));
    for (const removed of ["revision_no", "active_slot", "secret_ref", "endpoint"]) assert.ok(!fieldsOf(name).includes(removed));
  }
  for (const table of TABLES) assert.ok(!table.fields.some(field => field.defKey.endsWith("_hash")));
  assert.equal(fieldsOf("notification_delivery").includes("tenant_id"), false);
  assert.ok(fieldsOf("notification_request").includes("tenant_id"));
});

test("Platform Support Webhook keeps subscriptions on the endpoint and reuses compensation scheduling", async () => {
  const { TABLES } = await import("./platform-support/manifests/04-webhook.mjs");
  assert.deepEqual(TABLES.map((table) => table.defKey), [
    "webhook_endpoint", "webhook_signing_secret", "webhook_event", "webhook_delivery", "webhook_attempt",
  ]);

  const tableByKey = new Map(TABLES.map((table) => [table.defKey, table]));
  const fieldsOf = (tableKey) => tableByKey.get(tableKey).fields.map((field) => field.defKey);
  assert.ok(fieldsOf("webhook_endpoint").includes("tenant_id"));
  assert.ok(fieldsOf("webhook_endpoint").includes("event_types"));
  assert.equal(fieldsOf("webhook_endpoint").includes("url_vault_token"), false);
  assert.ok(fieldsOf("webhook_signing_secret").includes("rotation_no"));
  assert.ok(fieldsOf("webhook_signing_secret").includes("valid_until"));
  assert.ok(fieldsOf("webhook_event").includes("tenant_id"));
  assert.ok(fieldsOf("webhook_delivery").includes("tenant_id"));
  assert.deepEqual(
    ["next_retry_time", "lease_owner", "lease_until", "fencing_token", "generation_no"]
      .filter((field) => fieldsOf("webhook_delivery").includes(field)),
    [],
  );
  assert.ok(fieldsOf("webhook_delivery").includes("replay_of_delivery_id"));
  assert.ok(fieldsOf("webhook_attempt").includes("signing_secret_id"));
  assert.ok(fieldsOf("webhook_attempt").includes("grace_secret_id"));
  const webhookClientFields = TABLES.flatMap((table) => table.fields
    .filter((field) => field.defKey === "client_id")
    .map((field) => `${table.defKey}.${field.defKey}`));
  assert.deepEqual(webhookClientFields, []);
});

test("Platform Support public API control plane has the exact six-table manifest contract", async () => {
  const { TABLES, TABLE_GROUPS } = await import("./platform-support/manifest.mjs");
  assert.ok(TABLE_GROUPS.gateway_control, "缺少 gateway_control 图组");
  assert.deepEqual(TABLE_GROUPS.gateway_control.map((table) => table.defKey), Object.keys(GATEWAY_CONTROL_CONTRACTS));

  const tableByKey = new Map(TABLES.map((table) => [table.defKey, table]));
  for (const [tableKey, contract] of Object.entries(GATEWAY_CONTROL_CONTRACTS)) {
    const table = tableByKey.get(tableKey);
    assert.ok(table, `缺少公共 API 控制面表：t_${tableKey}`);
    assert.equal(table.physicalKey, tableKey);
    assert.deepEqual(table.fields.map((field) => field.defKey), contract.fields, `字段漂移：t_${tableKey}`);
    assert.deepEqual(table.fields.filter((field) => !field.notNull).map((field) => field.defKey),
      contract.nullableFields, `可空字段漂移：t_${tableKey}`);
    const fieldTypes = Object.fromEntries(table.fields.map((field) => {
      const length = field.len === "" ? "" : `(${field.len}${field.scale === "" ? "" : `,${field.scale}`})`;
      return [field.defKey, `${field.type}${length}`];
    }));
    assert.deepEqual(fieldTypes, contract.fieldTypes, `字段类型漂移：t_${tableKey}`);

    const uniqueIndexes = Object.fromEntries(table.indexes
      .filter((index) => index.unique)
      .map((index) => [index.defKey, index.fields]));
    const accessIndexes = Object.fromEntries(table.indexes
      .filter((index) => !index.unique)
      .map((index) => [index.defKey, index.fields]));
    assert.deepEqual(uniqueIndexes, contract.uniqueIndexes, `唯一索引漂移：t_${tableKey}`);
    assert.deepEqual(accessIndexes, contract.accessIndexes, `访问索引漂移：t_${tableKey}`);

    const relations = table.relations.map(({ childField, parentTable, parentField, cardinality }) => ({
      childField, parentTable, parentField, cardinality,
    }));
    assert.deepEqual(relations, contract.relations, `关系漂移：t_${tableKey}`);
    for (const [fieldKey, values] of Object.entries(contract.enums)) {
      assert.deepEqual(table.fields.find((field) => field.defKey === fieldKey)?.closedValues, values,
        `闭合枚举漂移：t_${tableKey}.${fieldKey}`);
    }
    for (const [fieldKey, storage] of Object.entries(contract.fieldStorage ?? {})) {
      const field = table.fields.find((item) => item.defKey === fieldKey);
      assert.deepEqual(
        { characterSet: field?.characterSet, collation: field?.collation },
        storage,
        `字段存储语义漂移：t_${tableKey}.${fieldKey}`,
      );
    }
  }

  const controlTables = TABLE_GROUPS.gateway_control;
  assert.deepEqual(controlTables.flatMap((table) => table.fields
    .filter((field) => field.defKey === "tenant_id" || field.defKey.endsWith("_hash"))
    .map((field) => `${table.defKey}.${field.defKey}`)), []);
  const childKeys = new Set([
    "gateway_route_header", "gateway_route_query_param", "gateway_guard_rule",
  ]);
  assert.deepEqual(controlTables.filter((table) => childKeys.has(table.defKey)).flatMap((table) => table.fields
    .filter((field) => ["status", "version", "update_time"].includes(field.defKey))
    .map((field) => `${table.defKey}.${field.defKey}`)), []);
  assert.deepEqual(controlTables.filter((table) => table.fields.some((field) => field.defKey === "version"))
    .map((table) => table.defKey), ["gateway_target", "gateway_route", "console_resource"]);

  const networkOrScript = /(?:^|_)(?:uri|scheme|host|ip|port|secret|script)(?:_|$)/u;
  for (const tableKey of ["gateway_target", "gateway_route"]) {
    const forbiddenFields = tableByKey.get(tableKey).fields
      .map((field) => field.defKey)
      .filter((field) => networkOrScript.test(field)
        || ["runtime_route_ref", "path_hash", "path_shape", "config_hash", "policy_ver_id", "release_id", "snapshot_id", "history_id"].includes(field));
    assert.deepEqual(forbiddenFields, [], `公共目标或路由不得保存任意网络、脚本或版本发布字段：${tableKey}`);
  }

  const route = tableByKey.get("gateway_route");
  assert.equal(route.fields.find((field) => field.defKey === "permission_id")?.notNull, false);
  assert.equal(route.fields.find((field) => field.defKey === "required_scope_code")?.notNull, false);
  assert.equal(route.fields.find((field) => field.defKey === "http_method")?.closedValues, null);
  assert.ok(route.fields.find((field) => field.defKey === "http_method")?.openSource,
    "HTTP Method 必须由 Gateway 预编译能力集合校验，数据库模型不擅自冻结白名单");
  assert.ok(route.relations.some((relation) => relation.childField === "permission_id"
    && relation.parentTable === "permission" && relation.parentField === "permission_id"));

  const tableKeys = TABLES.map((table) => table.defKey);
  assert.equal(tableKeys.some((table) => table.startsWith("config_")), false);
  assert.equal(tableKeys.includes("audit_archive_batch"), false);
  for (const forbiddenTable of [
    "gateway_route_ver", "gateway_policy", "gateway_policy_ver", "gateway_policy_binding",
    "gateway_policy_binding_ver", "gateway_release", "gateway_release_item", "gateway_instance",
    "gateway_instance_ack",
  ]) assert.equal(tableKeys.includes(forbiddenTable), false, `旧 Gateway 结构仍存在：${forbiddenTable}`);
  const commandIdempotency = TABLES.find((table) => table.defKey === "command_idempotency");
  assert.ok(commandIdempotency);
  assert.equal(commandIdempotency.fields.some((field) => field.defKey === "response_hash"), false);
  assert.ok(commandIdempotency.fields.some((field) => field.defKey === "request_hash"));
  assert.ok(commandIdempotency.fields.some((field) => field.defKey === "response_ref"));

  const consoleResource = tableByKey.get("console_resource");
  assert.equal(consoleResource.fields.find((field) => field.defKey === "permission_id")?.notNull, true);
});

test("Platform Support freezes the exact Channel, Security Profile and Authorization Mode matrix", async () => {
  const { GATEWAY_ROUTE_COMPATIBILITY } = await import("./platform-support/model-policy.mjs");
  assert.deepEqual(GATEWAY_ROUTE_COMPATIBILITY, REQUIRED_GATEWAY_ROUTE_COMPATIBILITY);
  assert.deepEqual(Object.values(GATEWAY_ROUTE_COMPATIBILITY).flat()
    .filter((pair) => pair.startsWith("PUBLIC:")), ["PUBLIC:PUBLIC", "PUBLIC:PUBLIC", "PUBLIC:PUBLIC"]);
  assert.equal(Object.values(GATEWAY_ROUTE_COMPATIBILITY).flat()
    .some((pair) => pair.endsWith(":HUMAN_PERMISSION") && pair.startsWith("PUBLIC:")), false);
});

test("Platform Support secret contract rejects adversarial fields and non-public RSA JWK members", () => {
  const publicJwk = JSON.stringify({ kty: "RSA", n: "public-modulus", e: "AQAB" });
  assert.doesNotThrow(() => assertSecretMaterialContract([
    { defKey: "password_verifier_phc" },
    { defKey: "password_changed_time" },
    { defKey: "last_password_failure_time" },
    { defKey: "consecutive_password_failures" },
    { defKey: "password_status" },
    { defKey: "password_algorithm_code" },
    { defKey: "code_hmac" },
    { defKey: "secret_hmac" },
    { defKey: "token_lookup_id" },
    { defKey: "kms_key_ref" },
    { defKey: "secret_ref" },
    { defKey: "email_vault_token" },
    { defKey: "email_masked" },
    { defKey: "public_jwk_canonical_json", serializedValue: publicJwk },
  ]));
  const forbiddenFields = [
    { defKey: "password_plaintext" },
    { defKey: "current_password" },
    { defKey: "password_hash" },
    { defKey: "password_reset_token" },
    { defKey: "reset_token" },
    { defKey: "verification_token" },
    { defKey: "session_token" },
    { defKey: "oidc_token" },
    { defKey: "oidc_secret" },
    { defKey: "private_jwk_canonical_json" },
    { defKey: "symmetric_jwk" },
    { defKey: "card_number" },
  ];
  for (const mutation of [
    ...forbiddenFields,
    { defKey: "public_jwk_canonical_json", serializedValue: JSON.stringify({ kty: "oct", k: "symmetric-secret" }) },
    ...["k", "d", "p", "q", "dp", "dq", "qi", "oth"].map((member) => ({
      defKey: "public_jwk_canonical_json",
      serializedValue: JSON.stringify({ kty: "RSA", n: "public-modulus", e: "AQAB", [member]: "forbidden" }),
    })),
  ]) {
    assert.throws(() => assertSecretMaterialContract([mutation]));
  }
  for (const { defKey } of forbiddenFields) {
    assert.equal(isForbiddenSecretField(defKey), true, `Oracle 必须拒绝：${defKey}`);
    assert.equal(isForbiddenGeneratorSecretField(defKey), true, `Generator 必须拒绝：${defKey}`);
  }
});

test("Platform Support generated model contains only the approved public API control plane", () => {
  const model = readModel();
  const tableNames = new Set(model.entities.map((entity) => entity.defKey));
  for (const table of APPROVED_TABLE_GROUPS.gateway_control) {
    assert.equal(tableNames.has(table), true, `生成模型缺少公共 API 控制面表：${table}`);
  }
  assert.equal([...tableNames].some((name) => name.startsWith("t_config_")), false);
  for (const table of FORBIDDEN_TABLES) assert.equal(tableNames.has(table), false, `生成模型仍有禁用表：${table}`);

  const controlTables = model.entities.filter((entity) => APPROVED_TABLE_GROUPS.gateway_control.includes(entity.defKey));
  assert.deepEqual(controlTables.flatMap((entity) => entity.fields
    .filter((field) => field.defKey === "tenant_id" || field.defKey.endsWith("_hash"))
    .map((field) => `${entity.defKey}.${field.defKey}`)), []);

  const route = model.entities.find((entity) => entity.defKey === "t_gateway_route");
  for (const fieldKey of ["http_method", "external_path_template", "downstream_path_template"]) {
    assert.deepEqual(route.fields.find((field) => field.defKey === fieldKey)?.extProps, {
      characterSet: "ascii",
      collation: "ascii_bin",
    }, `t_gateway_route.${fieldKey} 必须使用单字节大小写敏感存储`);
  }
  const routePathIndex = route.indexes.find((index) => index.defKey === "uk_gateway_route_path");
  const routeFields = new Map(route.fields.map((field) => [field.id, field]));
  const maximumIndexBytes = routePathIndex.fields.reduce((total, entry) => {
    const field = routeFields.get(entry.fieldDefKey);
    return total + Number(field.len) * (field.extProps.characterSet === "ascii" ? 1 : 4);
  }, 0);
  assert.ok(maximumIndexBytes <= 3072, `uk_gateway_route_path 最大 ${maximumIndexBytes} bytes，超过 InnoDB 3072-byte 上限`);
  const mysqlDdlTemplate = model.profile.codeTemplates.find((template) => template.type === "dbDDL");
  assert.match(mysqlDdlTemplate.createTable, /CHARACTER SET \{\{=field\.extProps\.characterSet\}\} COLLATE \{\{=field\.extProps\.collation\}\}/u,
    "生成模型的 MySQL DDL 必须输出字段级字符集与排序规则");
  const routeHeader = model.entities.find((entity) => entity.defKey === "t_gateway_route_header");
  assert.deepEqual(routeHeader.fields.find((field) => field.defKey === "header_name")?.extProps, {
    characterSet: "ascii",
    collation: "ascii_general_ci",
  }, "t_gateway_route_header.header_name 必须用 ASCII 大小写不敏感排序规则冻结唯一性");
});

test("Platform Support identifiers, metadata, primary keys and indexes follow conventions", () => {
  const model = readModel();
  const generatedIds = [];
  for (const entity of model.entities) {
    assert.match(entity.defKey, /^t_[a-z][a-z0-9_]*$/u);
    assert.ok(entity.defKey.length <= 32, `${entity.defKey} 超过 32 字符`);
    assert.match(entity.defName, /[\u3400-\u9fff]/u);
    assert.match(entity.comment, /[\u3400-\u9fff]/u);
    generatedIds.push(entity.id);
    const primaryKeys = entity.fields.filter((field) => field.primaryKey);
    assert.equal(primaryKeys.length, 1, `${entity.defKey} 必须恰有一个主键`);
    assert.equal(primaryKeys[0].type, "BIGINT");
    assert.equal(primaryKeys[0].autoIncrement, false);
    if (NO_AUDIT_TABLES.includes(entity.defKey)) {
      assert.equal(entity.fields.some((field) => ["create_time", "update_time"].includes(field.defKey)), false,
        `${entity.defKey} 聚合子表不得机械增加审计字段`);
    } else {
      assert.ok(entity.fields.some((field) => field.defKey === "create_time"), `${entity.defKey} 缺少 create_time`);
    }
    assert.equal(new Set(entity.fields.map((field) => field.defKey)).size, entity.fields.length);
    for (const field of entity.fields) {
      generatedIds.push(field.id);
      assert.match(field.defKey, /^[a-z][a-z0-9_]*$/u);
      assert.ok(field.defKey.length <= 32, `${entity.defKey}.${field.defKey} 超过 32 字符`);
      assert.match(field.defName, /[\u3400-\u9fff]/u);
      assert.match(field.comment, /[\u3400-\u9fff]/u);
      assert.equal(isForbiddenSecretField(field.defKey), false, `禁止保存敏感字段：${field.defKey}`);
    }
    for (const index of entity.indexes) {
      generatedIds.push(index.id, ...index.fields.map((entry) => entry.id));
      assert.match(index.defKey, index.unique ? /^uk_[a-z0-9_]+$/u : /^idx_[a-z0-9_]+$/u);
      assert.ok(index.defKey.length <= 32, `${entity.defKey}.${index.defKey} 超过 32 字符`);
      assert.match(index.defName, /[\u3400-\u9fff]/u);
    }
  }
  assert.ok(generatedIds.every((id) => idPattern.test(id)));
  assert.equal(new Set(generatedIds).size, generatedIds.length);
});

test("Platform Support separates mutable lifecycle metadata from immutable facts", () => {
  const model = readModel();
  const byKey = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const tableKey of [
    "t_file_scan_policy",
    "t_document_template",
    "t_document_template_translation",
    "t_document_version",
    "t_notification_provider",
    "t_notification_route",
    "t_notification_template",
    "t_notification_attempt",
    "t_webhook_endpoint",
    "t_webhook_signing_secret",
    "t_webhook_delivery",
    "t_webhook_attempt",
  ]) {
    const fields = new Set(byKey.get(tableKey)?.fields.map((field) => field.defKey));
    assert.ok(fields.has("status"), `${tableKey} 必须保存生命周期状态`);
    assert.ok(fields.has("update_time"), `${tableKey} 的生命周期更新必须可审计`);
  }
  for (const tableKey of [
    "t_file_owner_ref",
    "t_file_scan_result",
    "t_document_delivery",
    "t_webhook_event",
    "t_audit_event",
  ]) {
    const fields = new Set(byKey.get(tableKey)?.fields.map((field) => field.defKey));
    assert.ok(fields.has("create_time"), `${tableKey} 必须保存创建时间`);
    assert.ok(!fields.has("update_time"), `${tableKey} 是追加式事实，不得保存更新时间`);
  }
});

test("Platform Support derives Integration Readiness without persisted evaluation state", () => {
  const model = readModel();
  const names = new Set(model.entities.map((entity) => entity.defKey));
  for (const tableName of [
    "t_client_integration_readiness", "t_integration_readiness_eval",
    "t_integration_readiness_check", "t_config_version",
  ]) {
    assert.equal(names.has(tableName), false, `derived readiness must not persist ${tableName}`);
  }
  for (const tableName of [
    "t_access_tenant", "t_api_credential", "t_api_credential_scope",
    "t_webhook_endpoint",
  ]) {
    assert.equal(names.has(tableName), true, `derived readiness requires ${tableName}`);
  }
});

test("Platform Support documentation freezes current API, readiness, and Gateway boundaries", () => {
  const design = readFileSync(paths.design, "utf8");
  const logicalDatabase = readFileSync(paths.logicalDatabase, "utf8");

  assert.match(design, /`t_notify_template_translation`/u);
  assert.doesNotMatch(design, /`t_notification_template_translation`/u);
  assert.match(design, /READY \| NOT_READY \| RESTRICTED/u);
  assert.doesNotMatch(design, /status\s+=\s+READY \| NOT_READY \| UNKNOWN/u);
  assert.match(design, /technical dependency failure[\s\S]*does not return a business\s+readiness status/iu);
  assert.match(design, /raw canonical socket IP[\s\S]*exact validated JWT `sub`[\s\S]*with no Redis HMAC; only Access Log[\s\S]*independent HMAC pseudonymization/iu);
  assert.doesNotMatch(design, /deployment-key HMAC|tenant:\{tenant_id\}|member:\{member_id\}|account:\{account_id\}/u);
  assert.deepEqual(redisIdentityHmacContradictions(design), []);

  assert.match(logicalDatabase, /PLT-I03[\s\S]*tenant_type[\s\S]*owner_ref_id[\s\S]*credential_id[\s\S]*Scope/u);
  assert.match(logicalDatabase, /PLT-I13[\s\S]*READY \| NOT_READY \| RESTRICTED/u);
  assert.match(logicalDatabase, /响应仅包含[\s\S]*status[\s\S]*checked_at[\s\S]*reason_code/u);
  assert.doesNotMatch(logicalDatabase, /必要资源稳定 ID/u);
});

test("Platform Support design gate rejects contradictory Redis identity HMAC requirements", () => {
  const design = readFileSync(paths.design, "utf8");
  for (const contradiction of [
    "Redis identity inputs MUST be HMAC-transformed before limiter lookup.",
    "The limiter must derive its Redis identity key by applying HMAC to the JWT subject.",
  ]) {
    assert.equal(redisIdentityHmacContradictions(`${design}\n${contradiction}`).length, 1);
  }
});

test("Platform Support freezes critical fields and minimal Vault shapes", () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const [table, fields] of Object.entries(REQUIRED_FIELDS)) {
    const entity = entities.get(table);
    assert.ok(entity, `缺少关键表：${table}`);
    const actual = new Set(entity.fields.map((field) => field.defKey));
    for (const field of fields) assert.ok(actual.has(field), `缺少关键字段：${table}.${field}`);
  }
  for (const [table, fields] of Object.entries(FORBIDDEN_FIELDS_BY_TABLE)) {
    const actual = new Set(entities.get(table).fields.map((field) => field.defKey));
    for (const field of fields) assert.ok(!actual.has(field), `不需要的字段仍存在：${table}.${field}`);
  }
  for (const tuple of FILE_REFERENCE_TUPLES) {
    const actual = new Set(entities.get(tuple.table).fields.map((field) => field.defKey));
    assert.ok(actual.has(tuple.id) && actual.has(tuple.hash), `文件引用必须成对：${tuple.table}`);
  }
});

test("Platform Support persists enforceable cross-owner composite foreign keys", async () => {
  const model = readModel();
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  const { COMPOSITE_FK_CONTRACTS } = await import("./platform-support/model-policy.mjs");
  assert.deepEqual(COMPOSITE_FK_CONTRACTS, REQUIRED_COMPOSITE_FKS);

  for (const contract of REQUIRED_COMPOSITE_FKS) {
    const child = entities.get(contract.childTable);
    const parent = entities.get(contract.parentTable);
    assert.ok(child && parent, `${contract.name} 引用了未知表`);
    const childFields = child.fields.map((field) => field.defKey);
    const parentFields = parent.fields.map((field) => field.defKey);
    assert.ok(contract.childFields.every((field) => childFields.includes(field)), `${contract.name} 子字段不完整`);
    assert.ok(contract.parentFields.every((field) => parentFields.includes(field)), `${contract.name} 父字段不完整`);

    const childIndexable = child.indexes.some((index) => {
      const fields = index.fields.map((entry) => child.fields.find((field) => field.id === entry.fieldDefKey)?.defKey);
      return contract.childFields.every((field, offset) => fields[offset] === field);
    });
    assert.ok(childIndexable, `${contract.name} 缺少子表左前缀索引`);

    const parentUnique = parent.indexes.some((index) => {
      const fields = index.fields.map((entry) => parent.fields.find((field) => field.id === entry.fieldDefKey)?.defKey);
      return index.unique && fields.length === contract.parentFields.length
        && contract.parentFields.every((field, offset) => fields[offset] === field);
    });
    assert.ok(parentUnique, `${contract.name} 缺少父表精确唯一键`);

    const persisted = child.properties?.compositeForeignKeys?.find((item) => item.name === contract.name);
    assert.deepEqual(persisted, contract, `${contract.name} 未完整写入 PDManer 实体属性`);
  }
});

test("Platform Support composite keys bind every security-sensitive identity and delivery tuple", async () => {
  const { COMPOSITE_FK_CONTRACTS } = await import("./platform-support/model-policy.mjs");
  const contracts = new Map(COMPOSITE_FK_CONTRACTS.map((contract) => [contract.name, contract]));
  const tuple = (name) => {
    const contract = contracts.get(name);
    assert.ok(contract, `缺少安全复合约束：${name}`);
    return [contract.childFields, contract.parentFields];
  };

  assert.deepEqual(tuple("fk_auth_session_account_realm"), [
    ["account_id", "realm"], ["account_id", "realm"],
  ]);
  assert.deepEqual(tuple("fk_auth_session_member_identity"), [
    ["tenant_member_id", "tenant_id", "account_id"], ["member_id", "tenant_id", "account_id"],
  ]);
  assert.deepEqual(tuple("fk_invite_accepted_identity"), [
    ["accepted_member_id", "tenant_id", "accepted_account_id"], ["member_id", "tenant_id", "account_id"],
  ]);
  assert.deepEqual(tuple("fk_tenant_member_role_member"), [
    ["tenant_member_id", "tenant_id"], ["member_id", "tenant_id"],
  ]);
  assert.deepEqual(tuple("fk_tenant_member_role_role_scope"), [
    ["role_id", "role_scope_key"], ["role_id", "scope_key"],
  ]);
  assert.deepEqual(tuple("fk_document_grant_subject"), [
    ["document_id", "tenant_id", "order_id", "consumer_ref_hash"],
    ["document_id", "tenant_id", "order_id", "consumer_ref_hash"],
  ]);

});

test("Platform Support CHECK policy freezes critical single-row invariants", async () => {
  const { CHECK_BINDINGS, CHECK_CONTRACTS } = await import("./platform-support/model-policy.mjs");
  assert.equal(Object.keys(CHECK_CONTRACTS).length, EXPECTED_CHECK_COUNT);
  assert.deepEqual(Object.keys(CHECK_BINDINGS).sort(), Object.keys(CHECK_CONTRACTS).sort());
  assert.equal(CHECK_BINDINGS.ck_positive_version.includes("tenant_member_role"), true,
    "ck_positive_version 必须绑定 tenant_member_role");
  assert.equal(CHECK_BINDINGS.ck_positive_version.includes("member_role"), false,
    "ck_positive_version 不得绑定旧 member_role");
  assert.equal(CHECK_BINDINGS.ck_effective_range.includes("tenant_member_role"), true,
    "ck_effective_range 必须绑定 tenant_member_role");
  assert.equal(CHECK_BINDINGS.ck_effective_range.includes("member_role"), false,
    "ck_effective_range 不得绑定旧 member_role");
  const fullPolicy = Object.keys(CHECK_CONTRACTS).sort().map((name) => ({
    name,
    expression: CHECK_CONTRACTS[name],
    tables: CHECK_BINDINGS[name],
  }));
  assert.equal(sha256(JSON.stringify(fullPolicy)), EXPECTED_CHECK_POLICY_DIGEST, "完整 CHECK Policy 摘要漂移");
  for (const [checkName, expected] of Object.entries(REQUIRED_CHECK_CONTRACTS)) {
    assert.equal(CHECK_CONTRACTS[checkName], expected.expression, `CHECK 表达式漂移：${checkName}`);
    assert.deepEqual(CHECK_BINDINGS[checkName], expected.tables, `CHECK 绑定漂移：${checkName}`);
  }

  const model = readModel();
  const decisions = readFileSync(paths.decisions, "utf8");
  const checkLikeKeys = objectKeysDeep(model).filter((key) => /check/iu.test(key));
  assert.deepEqual(checkLikeKeys, [], `PDManer 模型不得伪造 CHECK 节点：${checkLikeKeys.join(", ")}`);
  assert.match(decisions, /PDManer 4\.9\.3.*(?:没有|不提供).*可移植 CHECK 节点[\s\S]*model-policy\.mjs/u);
  assert.match(decisions, /人工[\s\S]*sidecar[\s\S]*DDL/u);
  assert.doesNotMatch(decisions, /PDManer 4\.9\.3[^。\n]*CHECK 可读性/u);
});

test("historical Platform and static Gateway authorities point to the current successor", () => {
  const dynamicDesign = readFileSync(paths.dynamicGatewayDesign, "utf8");
  assert.match(dynamicDesign, /当前(?:已)?生成[^\n]*63 表[^\n]*822 字段/u);
  assert.doesNotMatch(dynamicDesign, /约 63 表候选|精确统计以后续生成模型为准/u);

  for (const documentPath of historicalPlatformAuthorityDocuments) {
    const content = readFileSync(documentPath, "utf8");
    assert.match(content, /2026-09-01/iu, `${documentPath} 缺少 2026-09-01 后继标记`);
    assert.match(content, /2026-09-01-gateway-platform-support-dynamic-operations-configuration-design\.md/u, `${documentPath} 未指向公共控制面后继设计`);
    assert.match(content, /63 表[^\n]*822 字段/u, `${documentPath} 未标记当前 63 表后继`);
    assert.match(content, /(?:57 表|57-table)[^\n]*(?:前序|历史|predecessor)|(?:前序|历史|predecessor)[^\n]*(?:57 表|57-table)/iu, `${documentPath} 未限制 57 表阶段的读取方式`);
  }

  for (const documentPath of preCutoverGatewayAuthorityDocuments) {
    const content = readFileSync(documentPath, "utf8");
    assert.match(content, /2026-09-01-gateway-platform-support-dynamic-operations-configuration-design\.md/u, `${documentPath} 未指向公共控制面后继设计`);
    assert.match(content, /(?:静态|static)[^\n]*(?:切换前|pre-cutover)|(?:切换前|pre-cutover)[^\n]*(?:静态|static)/iu, `${documentPath} 未将静态 Catalog 限定为切换前运行态`);
  }
});

test("Platform Support relations are local, indexed and one-to-one cardinality is enforceable", () => {
  const model = readModel();
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  for (const child of model.entities) {
    const fieldsById = new Map(child.fields.map((field) => [field.id, field]));
    const primary = child.fields.find((field) => field.primaryKey);
    for (const relation of child.correlations) {
      assert.ok(entitiesById.has(relation.refEntity), `${child.defKey} 引用了库外实体`);
      const childField = fieldsById.get(relation.myField);
      const leftPrefix = childField.id === primary.id
        || child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id);
      assert.ok(leftPrefix, `${child.defKey}.${childField.defKey} 缺少左前缀索引`);
      if (relation.myRows === "1") {
        const exactUnique = child.indexes.filter((index) => index.unique
          && index.fields.length === 1
          && index.fields[0].fieldDefKey === childField.id);
        assert.ok(childField.id === primary.id || exactUnique.length === 1,
          `${child.defKey}.${childField.defKey} 的 1:1 关系缺少单列唯一键`);
      }
    }
  }
});

test("Platform Support diagrams draw each relation once and each table owns one group", () => {
  const model = readModel();
  const primaryTables = model.diagrams.flatMap((diagram) => diagram.properties.primaryTables);
  assert.equal(primaryTables.length, EXPECTED_TABLE_TOTAL);
  assert.equal(new Set(primaryTables).size, EXPECTED_TABLE_TOTAL);
  assert.deepEqual([...primaryTables].sort(), [...APPROVED_TABLES].sort());
  const relationCount = model.entities.flatMap((entity) => entity.correlations).length;
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation");
  assert.equal(relationCells.length, relationCount);
  assert.equal(new Set(relationCells.map((cell) => cell.originKey)).size, relationCount);
});

test("Platform Support model stores no connection or forbidden credential and card fields", () => {
  const model = readModel();
  assert.deepEqual(model.dbConn, []);
  assert.deepEqual(model.dbConns, []);
  assert.equal(model.profile.default.dbConn, "");
  const allFields = model.entities.flatMap((entity) => entity.fields.map((field) => ({
    tableKey: entity.defKey,
    fieldKey: field.defKey,
  })));
  for (const { tableKey, fieldKey } of allFields) {
    assert.equal(isForbiddenSecretField(fieldKey), false, `禁止保存敏感字段：${tableKey}.${fieldKey}`);
  }
  assert.ok(!JSON.stringify(model).match(/jdbc:\/\/|BEGIN PRIVATE KEY|password\s*[=:]/iu));
});
