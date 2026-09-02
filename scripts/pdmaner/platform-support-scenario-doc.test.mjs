import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { APPROVED_TABLES, FORBIDDEN_TABLES } from "./platform-support-test-oracle/schema.oracle.mjs";
import { redisIdentityHmacContradictions } from "./platform-support-test-oracle/text-contracts.mjs";

const workspaceRoot = path.resolve(import.meta.dirname, "../..");
const scenarioPath = path.join(workspaceRoot, "agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md");
const modelPath = path.join(workspaceRoot, "platform-support-service/docs/database/platform-support.pdma.json");
const APPROVED_API_CREDENTIAL_VALIDATIONS = Object.freeze([
  Object.freeze({
    credentialId: "240001",
    returnedTenantId: "210001",
    returnedTenantType: "CLIENT",
    returnedOwnerRefId: "200001",
    returnedScopeCodes: Object.freeze(["WEBHOOK_READ", "orders.write"]),
    callerSuppliedOwnerRefId: null,
    keyPrefixDerivedOwnerRefId: null,
  }),
]);
const APPROVED_GATEWAY_TARGETS = Object.freeze([
  Object.freeze(["operations-console-bff", "operations-console-bff", "OPERATIONS"]),
  Object.freeze(["client-console-bff", "client-console-bff", "CLIENT"]),
  Object.freeze(["partner-console-bff", "partner-console-bff", "PARTNER"]),
  Object.freeze(["commerce-checkout-api", "commerce-service", "CHECKOUT"]),
  Object.freeze(["commerce-client-api", "commerce-service", "CLIENT_API"]),
  Object.freeze(["payment-provider-callback", "payment-orchestration-service", "PROVIDER_CALLBACK"]),
]);

function scenarioRows(content) {
  return content.split(/\n/u).flatMap((line) => {
    const match = line.match(/^\| `(t_[a-z0-9_]+)` \| (.*) \|$/u);
    return match ? [{ table: match[1], data: match[2] }] : [];
  });
}

function fieldAssignments(data) {
  return [...data.matchAll(/(?:^|[，； /`])([a-z][a-z0-9_]*)=([^，； |`】]+)/gu)]
    .map((match) => ({ field: match[1], value: match[2] }));
}

function assignmentValues(rows, table, field) {
  return rows
    .filter((row) => row.table === table)
    .flatMap((row) => fieldAssignments(row.data))
    .filter((assignment) => assignment.field === field)
    .map((assignment) => assignment.value);
}

function assertAssignmentValues(rows, table, field, expectedValues) {
  const actual = assignmentValues(rows, table, field);
  for (const value of expectedValues) {
    assert.ok(actual.includes(value), `场景缺少 ${table}.${field}=${value}`);
  }
}

function assertScenarioRecord(rows, table, expectedAssignments) {
  const matched = tableScenarioRecords(rows, table).some((assignments) => {
    return Object.entries(expectedAssignments).every(([field, expected]) => {
      const value = assignments.get(field);
      return typeof expected === "function" ? expected(value) : value === expected;
    });
  });
  assert.ok(matched, `场景缺少 ${table} 记录：${Object.keys(expectedAssignments).join(", ")}`);
}

function scenarioAssignments(row) {
  return new Map(fieldAssignments(row.data).map(({ field, value }) => [field, value]));
}

function scenarioRecords(row) {
  const fragments = [...row.data.matchAll(/【([^】]+)】/gu)].map((match) => match[1]);
  return (fragments.length ? fragments : [row.data])
    .map((fragment) => new Map(fieldAssignments(fragment).map(({ field, value }) => [field, value])))
    .filter((record) => record.size > 0);
}

function scenarioRecordEntries(row) {
  const labelled = [...row.data.matchAll(/【([^：:]+)[：:]([^】]+)】/gu)];
  return (labelled.length ? labelled.map((match) => ({ label: match[1], data: match[2] })) : [{ label: "", data: row.data }])
    .map(({ label, data }) => ({
      label,
      assignments: new Map(fieldAssignments(data).map(({ field, value }) => [field, value])),
    }))
    .filter(({ assignments }) => assignments.size > 0);
}

function tableScenarioRecords(rows, table) {
  return rows.filter((row) => row.table === table).flatMap(scenarioRecords);
}

function tableScenarioRecordEntries(rows, table) {
  return rows.filter((row) => row.table === table).flatMap(scenarioRecordEntries);
}

function findScenarioRecord(rows, table, predicate, failureMessage) {
  const record = tableScenarioRecords(rows, table).find(predicate);
  assert.ok(record, failureMessage);
  return record;
}

function isPresent(value) {
  return value && value !== "NULL";
}

function assertTenantMemberRoleNames(rows) {
  const tenantMemberRoleRecords = tableScenarioRecords(rows, "t_tenant_member_role");
  const roleRecords = tableScenarioRecords(rows, "t_role");
  const assignmentFields = rows.flatMap((row) => fieldAssignments(row.data).map(({ field }) => field));
  assert.deepEqual({
    tenantMemberRoleTablePresent: tenantMemberRoleRecords.length > 0,
    tenantMemberRoleIdPresent: tenantMemberRoleRecords.length > 0
      && tenantMemberRoleRecords.every((record) => isPresent(record.get("tenant_member_role_id"))),
    oldMemberRoleTablePresent: rows.some((row) => row.table === "t_member_role"),
    oldMemberRoleIdPresent: assignmentFields.includes("member_role_id"),
    platformRoleScopePresent: roleRecords.some((record) => (
      record.get("tenant_scope") === "PLATFORM"
        && record.get("tenant_id") === "NULL"
        && record.get("scope_key") === "PLATFORM"
    )),
    tenantRoleScopePresent: roleRecords.some((record) => (
      record.get("tenant_scope") === "TENANT"
        && isPresent(record.get("tenant_id"))
        && record.get("scope_key") === record.get("tenant_id")
    )),
  }, {
    tenantMemberRoleTablePresent: true,
    tenantMemberRoleIdPresent: true,
    oldMemberRoleTablePresent: false,
    oldMemberRoleIdPresent: false,
    platformRoleScopePresent: true,
    tenantRoleScopePresent: true,
  });
}

function assertRefreshReuseCompromise(rows) {
  const consumed = findScenarioRecord(rows, "t_refresh_token_generation", (record) => (
    record.get("generation_no") === "7"
      && record.get("status") === "CONSUMED"
      && record.get("active_slot") === "NULL"
      && isPresent(record.get("session_id"))
      && isPresent(record.get("replaced_by_generation_id"))
  ), "场景缺少已消费的 Refresh Generation 7");
  const sessionId = consumed.get("session_id");
  const successorId = consumed.get("replaced_by_generation_id");
  findScenarioRecord(rows, "t_refresh_token_generation", (record) => (
    record.get("refresh_generation_id") === successorId
      && record.get("session_id") === sessionId
      && record.get("generation_no") === "8"
      && record.get("status") === "ISSUED"
      && record.get("active_slot") === "1"
  ), "场景缺少同一 Session 的 Refresh Generation 8");
  const replayRevocation = tableScenarioRecordEntries(rows, "t_refresh_token_generation").find(({ label, assignments }) => (
    /重放/u.test(label)
      && assignments.get("refresh_generation_id") === successorId
      && assignments.get("session_id") === sessionId
      && assignments.get("generation_no") === "8"
      && assignments.get("status") === "REVOKED"
      && assignments.get("active_slot") === "NULL"
  ));
  assert.ok(replayRevocation, "Generation 7 重放后必须撤销同一 Session 的 Generation 8");
  findScenarioRecord(rows, "t_auth_session", (record) => (
    record.get("session_id") === sessionId && record.get("status") === "COMPROMISED"
  ), "场景缺少同一 Session 的 Refresh 重用 COMPROMISED 状态");
}

function assertLinkedAuthenticationBranches(rows) {
  const newGoogleIdentity = findScenarioRecord(rows, "t_federated_identity", (record) => (
    record.get("binding_method") === "NEW_ACCOUNT" && record.get("status") === "ACTIVE"
  ), "场景缺少 Google NEW_ACCOUNT Federated Identity");
  const newGoogleAccountId = newGoogleIdentity.get("account_id");
  findScenarioRecord(rows, "t_user_account", (record) => (
    record.get("account_id") === newGoogleAccountId
      && record.get("realm") === "CUSTOMER"
      && record.get("status") === "ACTIVE"
  ), "Google NEW_ACCOUNT 必须引用实际 CUSTOMER Account");
  findScenarioRecord(rows, "t_auth_session", (record) => (
    record.get("account_id") === newGoogleAccountId
      && record.get("authentication_method") === "GOOGLE"
      && record.get("context_type") === "ACCOUNT"
  ), "Google NEW_ACCOUNT 必须具有匹配的 Google Account Session");

  const googlePasswordCredential = findScenarioRecord(rows, "t_password_credential", (record) => (
    record.get("status") === "ACTIVE"
      && record.get("algorithm_code") === "ARGON2ID"
      && record.get("account_id") === "220402"
  ), "场景缺少 Google-only Account 的 Password 创建结果");
  const googlePasswordAccountId = googlePasswordCredential.get("account_id");
  findScenarioRecord(rows, "t_user_account", (record) => (
    record.get("account_id") === googlePasswordAccountId && record.get("realm") === "CUSTOMER"
  ), "Google-only Password 创建必须引用实际 Account");
  findScenarioRecord(rows, "t_federated_identity", (record) => (
    record.get("account_id") === googlePasswordAccountId
      && record.get("binding_method") === "GOOGLE_REAUTH"
      && record.get("status") === "ACTIVE"
  ), "Google-only Password 创建必须绑定 Google 重新认证 Identity");
  findScenarioRecord(rows, "t_auth_session", (record) => (
    record.get("account_id") === googlePasswordAccountId
      && record.get("authentication_method") === "GOOGLE"
      && record.get("context_type") === "ACCOUNT"
  ), "Google-only Password 创建必须绑定近期 Google 重新认证 Session");

  const operationsAdmin = findScenarioRecord(rows, "t_user_account", (record) => (
    record.get("account_id") === "220001"
      && record.get("realm") === "OPERATIONS"
      && record.get("account_type") === "HUMAN"
      && record.get("status") === "ACTIVE"
  ), "场景缺少首个 Operations Email Admin");
  const operationsAccountId = operationsAdmin.get("account_id");
  findScenarioRecord(rows, "t_password_credential", (record) => (
    record.get("account_id") === operationsAccountId
      && record.get("force_change_flag") === "1"
      && record.get("status") === "ACTIVE"
  ), "首个 Operations Email Admin 必须有 force-change Password Credential");
  const operationsMember = findScenarioRecord(rows, "t_tenant_member", (record) => (
    record.get("account_id") === operationsAccountId && record.get("status") === "ACTIVE"
  ), "首个 Operations Email Admin 必须是 INTERNAL Tenant Member");
  const operationsTenantId = operationsMember.get("tenant_id");
  findScenarioRecord(rows, "t_access_tenant", (record) => (
    record.get("tenant_id") === operationsTenantId && record.get("tenant_type") === "INTERNAL"
  ), "首个 Operations Email Admin Member 必须属于 INTERNAL Tenant");
  const platformAdminRole = findScenarioRecord(rows, "t_role", (record) => (
    record.get("role_code") === "PLATFORM_ADMIN"
      && record.get("tenant_scope") === "PLATFORM"
      && record.get("status") === "ACTIVE"
  ), "场景缺少 PLATFORM_ADMIN Role");
  findScenarioRecord(rows, "t_tenant_member_role", (record) => (
    isPresent(record.get("tenant_member_role_id"))
      && record.get("tenant_member_id") === operationsMember.get("member_id")
      && record.get("tenant_id") === operationsTenantId
      && record.get("role_id") === platformAdminRole.get("role_id")
      && record.get("status") === "ACTIVE"
  ), "首个 Operations Email Admin 必须具有 PLATFORM_ADMIN Member Role grant");
}

function assertDualTenantMembership(rows) {
  const members = rows
    .filter((row) => row.table === "t_tenant_member")
    .flatMap(scenarioRecords)
    .filter((record) => isPresent(record.get("account_id")) && isPresent(record.get("tenant_id")));
  const tenantTypes = new Map(rows
    .filter((row) => row.table === "t_access_tenant")
    .flatMap(scenarioRecords)
    .map((record) => [record.get("tenant_id"), record.get("tenant_type")]));
  const accountTenantTypes = new Map();
  for (const member of members) {
    const accountId = member.get("account_id");
    const tenantType = tenantTypes.get(member.get("tenant_id"));
    if (!tenantType) continue;
    const types = accountTenantTypes.get(accountId) ?? new Set();
    types.add(tenantType);
    accountTenantTypes.set(accountId, types);
  }
  assert.ok([...accountTenantTypes.values()].some((types) => (
    types.has("CLIENT") && types.has("PARTNER")
  )), "场景必须让同一 Account 同时成为 CLIENT 与 PARTNER Tenant Member");
}

function assertInvitationAcceptance(rows) {
  const invitation = findScenarioRecord(rows, "t_tenant_invitation", (record) => (
    record.get("status") === "ACCEPTED"
      && isPresent(record.get("accepted_account_id"))
      && isPresent(record.get("accepted_member_id"))
      && isPresent(record.get("initial_role_id"))
  ), "场景缺少已接受且带单一初始角色的 Tenant Invitation");
  const memberId = invitation.get("accepted_member_id");
  const roleId = invitation.get("initial_role_id");
  findScenarioRecord(rows, "t_tenant_member", (record) => (
    record.get("member_id") === memberId
      && record.get("account_id") === invitation.get("accepted_account_id")
  ), "场景缺少 Invitation 接受创建的唯一 Member");
  findScenarioRecord(rows, "t_tenant_member_role", (record) => (
    isPresent(record.get("tenant_member_role_id"))
      && record.get("tenant_member_id") === memberId
      && record.get("role_id") === roleId
  ), "场景缺少 Invitation 接受授予的单一初始 Role");
}

function apiCredentialTenantViolations(rows, validations = APPROVED_API_CREDENTIAL_VALIDATIONS) {
  const tenantRecords = tableScenarioRecords(rows, "t_access_tenant");
  const scopeRecords = tableScenarioRecords(rows, "t_api_credential_scope");
  const violations = [];

  for (const credential of tableScenarioRecords(rows, "t_api_credential")) {
    const credentialId = credential.get("credential_id");
    const tenantId = credential.get("tenant_id");
    const matchingTenants = tenantRecords.filter((tenant) => tenant.get("tenant_id") === tenantId);
    const tenant = matchingTenants[0];
    if (matchingTenants.length !== 1
      || tenant.get("tenant_type") !== "CLIENT"
      || tenant.get("owner_status") !== "ACTIVE"
      || tenant.get("security_status") !== "ACTIVE") {
      violations.push(`credential ${credentialId} must join one ACTIVE CLIENT Tenant`);
    }

    const activeScopes = scopeRecords.filter((scope) => (
      scope.get("credential_id") === credentialId
        && scope.get("status") === "ACTIVE"
        && isPresent(scope.get("scope_code"))
    ));
    if (activeScopes.length === 0) {
      violations.push(`credential ${credentialId} must expose only its own ACTIVE scopes`);
    }

    const matchingValidations = validations.filter((validation) => validation.credentialId === credentialId);
    if (matchingValidations.length !== 1) {
      violations.push(`credential ${credentialId} must have one PLT-I03 validation result`);
      continue;
    }
    if (!tenant) continue;

    const validation = matchingValidations[0];
    if (validation.returnedTenantId !== tenantId) {
      violations.push(`credential ${credentialId} must return tenant_id from verified Access Tenant`);
    }
    if (validation.returnedTenantType !== tenant.get("tenant_type")) {
      violations.push(`credential ${credentialId} must return tenant_type from verified Access Tenant`);
    }
    if (validation.returnedOwnerRefId !== tenant.get("owner_ref_id")) {
      violations.push(`credential ${credentialId} must return owner_ref_id from verified Access Tenant`);
    }
    if (isPresent(validation.callerSuppliedOwnerRefId)) {
      violations.push(`credential ${credentialId} must reject caller owner_ref_id`);
    }
    if (isPresent(validation.keyPrefixDerivedOwnerRefId)) {
      violations.push(`credential ${credentialId} must not infer owner_ref_id from Key Prefix`);
    }

    const actualScopes = activeScopes.map((scope) => scope.get("scope_code")).sort();
    const returnedScopes = [...validation.returnedScopeCodes].sort();
    if (!actualScopes.every((scope, index) => scope === returnedScopes[index])
      || actualScopes.length !== returnedScopes.length) {
      violations.push(`credential ${credentialId} must return exactly its own ACTIVE scopes`);
    }
  }
  return violations;
}

function assertDocumentVersionRecovery(rows) {
  const generated = findScenarioRecord(rows, "t_document_version", (record) => (
    record.get("document_version_no") === "1"
      && record.get("status") === "GENERATED"
      && record.get("active_slot") === "1"
      && isPresent(record.get("file_id"))
  ), "场景缺少仍保持 ACTIVE 的 Document Version 1");
  findScenarioRecord(rows, "t_document_version", (record) => (
    record.get("document_id") === generated.get("document_id")
      && record.get("document_version_no") === "2"
      && record.get("supersedes_version_id") === generated.get("document_ver_id")
      && record.get("status") === "FAILED"
      && record.get("active_slot") === "NULL"
      && record.get("file_id") === "NULL"
  ), "新 Render 失败时必须保留旧 ACTIVE Document Version");
}

function assertDirectWebhookReplay(rows) {
  const original = findScenarioRecord(rows, "t_webhook_delivery", (record) => (
    record.get("generation_no") === "0"
      && record.get("replay_of_delivery_id") === "NULL"
      && record.get("requested_by") === "NULL"
      && record.get("replay_reason_code") === "NULL"
  ), "场景缺少原始 Webhook Delivery generation 0");
  findScenarioRecord(rows, "t_webhook_delivery", (record) => (
    record.get("generation_no") === "1"
      && record.get("replay_of_delivery_id") === original.get("delivery_id")
      && isPresent(record.get("requested_by"))
      && isPresent(record.get("replay_reason_code"))
      && record.get("tenant_id") === original.get("tenant_id")
      && record.get("endpoint_id") === original.get("endpoint_id")
  ), "Replay 必须直接创建同 Tenant/Endpoint 的新 Webhook Delivery");
}

function assertContextClaimMatrices(rows) {
  assertScenarioRecord(rows, "t_auth_session", {
    context_type: "ACCOUNT", tenant_id: "NULL", tenant_member_id: "NULL",
  });
  const tenantSession = findScenarioRecord(rows, "t_auth_session", (record) => (
    record.get("context_type") === "TENANT"
      && isPresent(record.get("tenant_id"))
      && isPresent(record.get("tenant_member_id"))
      && isPresent(record.get("channel"))
  ), "场景缺少 Tenant Context Session");
  const tenantId = tenantSession.get("tenant_id");
  const memberId = tenantSession.get("tenant_member_id");
  const tenantType = new Map([
    ["OPERATIONS", "INTERNAL"],
    ["CLIENT", "CLIENT"],
    ["PARTNER", "PARTNER"],
  ]).get(tenantSession.get("channel"));
  assert.ok(tenantType, `Tenant Context Channel 非法：${tenantSession.get("channel")}`);
  findScenarioRecord(rows, "t_tenant_member", (record) => (
    record.get("member_id") === memberId && record.get("tenant_id") === tenantId
  ), "场景缺少与 Tenant Context 匹配的 Tenant Member");
  findScenarioRecord(rows, "t_access_tenant", (record) => (
    record.get("tenant_id") === tenantId && record.get("tenant_type") === tenantType
  ), "场景缺少与 Tenant Context Channel 匹配的 Access Tenant");
}

function assertGatewayControlScenarios(rows, content) {
  const operationsRoute = findScenarioRecord(rows, "t_gateway_route", (record) => (
    record.get("channel") === "OPERATIONS"
      && record.get("security_profile_code") === "OPERATIONS_JWT"
      && record.get("authorization_mode") === "HUMAN_PERMISSION"
      && isPresent(record.get("permission_id"))
      && record.get("required_scope_code") === "NULL"
      && record.get("status") === "ACTIVE"
  ), "场景缺少 Operations HUMAN_PERMISSION Route");

  const clientApiRoute = findScenarioRecord(rows, "t_gateway_route", (record) => (
    record.get("channel") === "CLIENT_API"
      && record.get("security_profile_code") === "CLIENT_API_CREDENTIAL"
      && record.get("authorization_mode") === "API_SCOPE"
      && record.get("permission_id") === "NULL"
      && record.get("required_scope_code") === "orders.write"
      && record.get("status") === "ACTIVE"
  ), "场景缺少 Client API orders.write Route");

  const partnerRoute = findScenarioRecord(rows, "t_gateway_route", (record) => (
    record.get("route_code") === "PARTNER_TRANSACTIONS_READ"
      && record.get("channel") === "PARTNER"
      && record.get("security_profile_code") === "PARTNER_JWT"
      && record.get("authorization_mode") === "HUMAN_PERMISSION"
      && isPresent(record.get("permission_id"))
      && record.get("required_scope_code") === "NULL"
      && record.get("status") === "ACTIVE"
  ), "Partner Transactions Route 必须通过 HUMAN_PERMISSION + PLT-I02 授权");

  for (const [channel, profile, mode] of [
    ["CHECKOUT", "CHECKOUT_SESSION", "CHECKOUT_RESOURCE"],
    ["PROVIDER_CALLBACK", "PROVIDER_CALLBACK", "PROVIDER_CALLBACK"],
  ]) {
    findScenarioRecord(rows, "t_gateway_route", (record) => (
      record.get("channel") === channel
        && record.get("security_profile_code") === profile
        && record.get("authorization_mode") === mode
        && record.get("permission_id") === "NULL"
        && record.get("required_scope_code") === "NULL"
    ), `场景缺少 ${channel} 无 Permission/Scope Route`);
  }

  assert.deepEqual(
    [...new Set(tableScenarioRecords(rows, "t_gateway_route").map((record) => record.get("channel")))].sort(),
    ["CHECKOUT", "CLIENT", "CLIENT_API", "OPERATIONS", "PARTNER", "PROVIDER_CALLBACK"],
    "场景必须覆盖六个公共 Route Channel",
  );

  for (const [targetServiceCode, discoveryServiceName, channel] of APPROVED_GATEWAY_TARGETS) {
    findScenarioRecord(rows, "t_gateway_target", (record) => (
      record.get("target_service_code") === targetServiceCode
        && record.get("discovery_service_name") === discoveryServiceName
        && record.get("channel") === channel
    ), `场景缺少 Approved Target Registry 三元组 ${targetServiceCode}/${discoveryServiceName}/${channel}`);
  }

  const activeRoutes = tableScenarioRecords(rows, "t_gateway_route")
    .filter((record) => record.get("status") === "ACTIVE");
  const guardRules = tableScenarioRecords(rows, "t_gateway_guard_rule");
  for (const route of activeRoutes) {
    const routeId = route.get("route_id");
    assert.ok(guardRules.some((record) => (
      record.get("route_id") === routeId
        && record.get("rule_type") === "RATE_LIMIT"
        && record.get("dimension") === "SHARED_ROUTE"
        && Number(record.get("capacity")) > 0
        && Number(record.get("refill_tokens")) > 0
        && Number(record.get("refill_period_millis")) > 0
        && record.get("concurrency_limit") === "NULL"
    )), `ACTIVE Route ${routeId} 缺少 SHARED_ROUTE Rate Limit`);
    assert.ok(guardRules.some((record) => (
      record.get("route_id") === routeId
        && record.get("rule_type") === "CONCURRENCY"
        && record.get("dimension") === "SHARED_ROUTE"
        && record.get("capacity") === "NULL"
        && record.get("refill_tokens") === "NULL"
        && record.get("refill_period_millis") === "NULL"
        && Number(record.get("concurrency_limit")) > 0
    )), `ACTIVE Route ${routeId} 缺少 SHARED_ROUTE Concurrency`);
    assert.ok(!route.get("downstream_path_template").startsWith("/internal/"),
      `公共 Route ${routeId} 不得指向 /internal/**`);
  }

  for (const routeCode of [
    "OPS_TENANT_READ", "CLIENT_PROFILE_READ", "PARTNER_TRANSACTIONS_READ", "OPS_CURRENT_IDENTITY",
  ]) {
    findScenarioRecord(rows, "t_gateway_route", (record) => (
      record.get("route_code") === routeCode && record.get("request_cookie_mode") === "DROP"
    ), `普通 Console Route ${routeCode} 不得要求 Refresh Cookie`);
  }

  const partnerPermissionId = partnerRoute.get("permission_id");
  const partnerPermission = findScenarioRecord(rows, "t_permission", (record) => (
    record.get("permission_id") === partnerPermissionId
      && record.get("permission_code") === "PARTNER_TRANSACTIONS_READ"
      && record.get("status") === "ACTIVE"
  ), "Partner Transactions Route 必须引用 ACTIVE PARTNER_TRANSACTIONS_READ Permission");
  const partnerRolePermission = findScenarioRecord(rows, "t_role_permission", (record) => (
    record.get("permission_id") === partnerPermission.get("permission_id")
      && record.get("status") === "ACTIVE"
  ), "Partner Transactions Permission 必须授予 Partner Role");
  const partnerRole = findScenarioRecord(rows, "t_role", (record) => (
    record.get("role_id") === partnerRolePermission.get("role_id")
      && record.get("tenant_scope") === "TENANT"
      && record.get("tenant_id") === "210002"
      && record.get("scope_key") === "210002"
      && record.get("status") === "ACTIVE"
  ), "Partner Transactions Permission 必须属于 Gulf Partner Tenant Role");
  findScenarioRecord(rows, "t_tenant_member_role", (record) => (
    record.get("tenant_member_id") === "220912"
      && record.get("tenant_id") === "210002"
      && record.get("role_id") === partnerRole.get("role_id")
      && record.get("role_scope_key") === "210002"
      && record.get("status") === "ACTIVE"
  ), "Gulf Partner Member 必须获得 Partner Transactions Role");

  findScenarioRecord(rows, "t_api_credential_scope", (record) => (
    record.get("scope_code") === clientApiRoute.get("required_scope_code")
      && record.get("status") === "ACTIVE"
  ), "Client API Route 的 required_scope_code 必须能命中 PLT-I03 Effective Credential Scope");

  findScenarioRecord(rows, "t_gateway_guard_rule", (record) => (
    record.get("route_id") === clientApiRoute.get("route_id")
      && record.get("rule_type") === "RATE_LIMIT"
      && record.get("dimension") === "TENANT"
      && Number(record.get("capacity")) > 0
      && Number(record.get("refill_tokens")) > 0
      && Number(record.get("refill_period_millis")) > 0
      && record.get("concurrency_limit") === "NULL"
  ), "场景缺少 Client API TENANT Rate Limit Guard");

  const consoleRecords = tableScenarioRecords(rows, "t_console_resource");
  const menu = consoleRecords.find((record) => record.get("resource_type") === "MENU");
  const button = consoleRecords.find((record) => record.get("resource_type") === "BUTTON");
  assert.ok(menu && button, "场景必须同时给出 Console MENU 与 BUTTON");
  assert.equal(menu.get("permission_id"), operationsRoute.get("permission_id"));
  assert.equal(button.get("permission_id"), operationsRoute.get("permission_id"));
  assert.equal(button.get("parent_resource_id"), menu.get("resource_id"));
  assert.equal(button.get("channel"), menu.get("channel"));

  const disabledTarget = findScenarioRecord(rows, "t_gateway_target", (record) => (
    record.get("status") === "DISABLED"
  ), "场景缺少 Gateway Disabled Target Kill Switch");
  findScenarioRecord(rows, "t_gateway_route", (record) => (
    record.get("target_id") === disabledTarget.get("target_id") && record.get("status") === "ACTIVE"
  ), "Disabled Target 必须演示仍为 ACTIVE 的子 Route 被 Snapshot 整体抑制");

  const targetChannels = new Map(tableScenarioRecords(rows, "t_gateway_target")
    .map((record) => [record.get("target_id"), record.get("channel")]));
  for (const route of tableScenarioRecords(rows, "t_gateway_route")) {
    assert.equal(targetChannels.get(route.get("target_id")), route.get("channel"),
      `Route ${route.get("route_id")} 与 Target Channel 不一致`);
  }

  const controlTables = new Set([
    "t_gateway_target", "t_gateway_route", "t_gateway_route_header",
    "t_gateway_route_query_param", "t_gateway_guard_rule", "t_console_resource",
  ]);
  assert.deepEqual(rows.filter((row) => controlTables.has(row.table))
    .flatMap((row) => fieldAssignments(row.data))
    .filter(({ field }) => field === "tenant_id" || field.endsWith("_hash")), []);
  assert.match(content, /Guard[^。\n]*全局[^。\n]*trusted `tenant_id`[^。\n]*Redis Key[^。\n]*不是[^。\n]*Foreign Key/u);
  assert.match(content, /Target[^。\n]*静态 Approved Target Registry[^。\n]*双重校验/u);
  assert.match(content, /DISABLED Target[^。\n]*ACTIVE Route[^。\n]*Snapshot[^。\n]*排除/u);
  assert.match(content, /Partner[^。\n]*HUMAN_PERMISSION[^。\n]*PLT-I02/u);
}

function assertSingleFlowContains(content, flowName, terms) {
  const sections = content.split(/(?=^## )/mu);
  assert.ok(sections.some((section) => terms.every((term) => section.includes(term))),
    `场景缺少同一 ${flowName} 流程中的约束：${terms.join(" / ")}`);
}

function allowedValues(field) {
  return field.comment.match(/允许值：([^。]+)。/u)?.[1].split("、") ?? null;
}

const IMPLICIT_SCENARIO_DEFAULTS = Object.freeze({
  create_time: "2026-09-01T00:00:00Z",
  update_time: "2026-09-01T00:00:00Z",
});

function isNull(value) {
  return value === undefined || value === "NULL";
}

function criticalTupleViolations(table, assignments) {
  const value = (field) => assignments.get(field);
  const violations = [];
  if (table === "t_user_account") {
    const bootstrap = value("account_type") === "BOOTSTRAP_ADMIN"
      && value("realm") === "OPERATIONS"
      && value("login_name") === "admin"
      && isNull(value("email_vault_token"))
      && isNull(value("email_masked"))
      && isNull(value("email_verified_time"));
    const human = value("account_type") === "HUMAN"
      && isNull(value("login_name"))
      && !isNull(value("email_vault_token"))
      && !isNull(value("email_masked"));
    if (!bootstrap && !human) violations.push("ck_user_account_shape");
  }
  if (table === "t_access_tenant") {
    const internal = value("tenant_type") === "INTERNAL"
      && isNull(value("source_version"))
      && isNull(value("source_payload_hash"));
    const projected = ["CLIENT", "PARTNER"].includes(value("tenant_type"))
      && Number(value("source_version")) > 0
      && !isNull(value("source_payload_hash"));
    if (!internal && !projected) violations.push("ck_tenant_source_tuple");
  }
  if (table === "t_role") {
    const platform = value("tenant_scope") === "PLATFORM"
      && isNull(value("tenant_id"))
      && value("scope_key") === "PLATFORM";
    const tenant = value("tenant_scope") === "TENANT"
      && !isNull(value("tenant_id"))
      && value("scope_key") === value("tenant_id");
    if (!platform && !tenant) violations.push("ck_role_scope");
  }
  if (table === "t_tenant_member_role") {
    if (value("role_scope_key") !== "PLATFORM" && value("role_scope_key") !== value("tenant_id")) {
      violations.push("ck_tenant_member_role_scope");
    }
  }
  if (table === "t_auth_session") {
    const account = value("context_type") === "ACCOUNT"
      && value("realm") === "CUSTOMER"
      && isNull(value("tenant_id"))
      && isNull(value("tenant_member_id"));
    const tenant = value("context_type") === "TENANT"
      && !isNull(value("tenant_id"))
      && !isNull(value("tenant_member_id"));
    const bootstrap = value("context_type") === "BOOTSTRAP"
      && value("realm") === "OPERATIONS"
      && value("channel") === "OPERATIONS"
      && isNull(value("tenant_id"))
      && isNull(value("tenant_member_id"));
    if (!account && !tenant && !bootstrap) violations.push("ck_auth_session_context");
    const channel = (value("realm") === "OPERATIONS" && value("channel") === "OPERATIONS")
      || (value("realm") === "CUSTOMER" && ["CLIENT", "PARTNER"].includes(value("channel")));
    if (!channel) violations.push("ck_auth_session_channel");
    const method = value("authentication_method") === "PASSWORD"
      || (value("authentication_method") === "GOOGLE" && value("realm") === "CUSTOMER");
    if (!method) violations.push("ck_auth_session_method");
    const issued = Date.parse(value("issued_time"));
    const activity = Date.parse(value("last_activity_time"));
    const idle = Date.parse(value("idle_expire_time"));
    const absolute = Date.parse(value("absolute_expire_time"));
    if (![issued, activity, idle, absolute].every(Number.isFinite)
      || issued > activity || activity >= absolute || idle > absolute) {
      violations.push("ck_auth_session_times");
    }
  }
  return violations;
}

function assertValueMatchesType(tableKey, field, value) {
  if (value === "NULL") return;
  const context = `${tableKey}.${field.defKey}=${value}`;
  if (["BIGINT", "INT", "INTEGER", "TINYINT", "SMALLINT", "BOOLEAN"].includes(field.type)) {
    assert.match(value, /^-?\d+$/u, `${context} 不是整数`);
  } else if (field.type === "DECIMAL") {
    assert.match(value, /^-?\d+(?:\.\d+)?$/u, `${context} 不是十进制数`);
  } else if (field.type === "DATE") {
    assert.match(value, /^\d{4}-\d{2}-\d{2}$/u, `${context} 不是 DATE`);
  } else if (field.type === "DATETIME") {
    assert.ok(!Number.isNaN(Date.parse(value)), `${context} 不是带时区的 ISO 8601 时刻`);
  }
}

test("Platform Support reality scenarios cover every approved table and no removed table", () => {
  assert.ok(existsSync(scenarioPath), `Platform Support 现实场景尚未生成：${scenarioPath}`);
  const content = readFileSync(scenarioPath, "utf8");
  const rows = scenarioRows(content);
  assert.equal(rows.length, APPROVED_TABLES.length, "每张批准物理表必须恰有一条场景行");
  assert.deepEqual([...new Set(rows.map((row) => row.table))].sort(), [...APPROVED_TABLES].sort());
  const mentionedTables = new Set(content.match(/\bt_[a-z][a-z0-9_]*\b/gu) ?? []);
  for (const table of mentionedTables) assert.ok(APPROVED_TABLES.includes(table), `现实场景引用未知物理表：${table}`);
  for (const table of FORBIDDEN_TABLES) assert.ok(!mentionedTables.has(table), `现实场景仍使用已删除表：${table}`);
});

test("Platform Support scenarios use exact Tenant Member Role names and preserve derived Role scopes", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  assertTenantMemberRoleNames(rows);
});

test("Platform Support scenario records satisfy NN fields, primitive types, enums and critical CHECK tuples", () => {
  const model = JSON.parse(readFileSync(modelPath, "utf8"));
  const entities = new Map(model.entities.map((entity) => [
    entity.defKey,
    new Map(entity.fields.map((field) => [field.defKey, field])),
  ]));
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  let assignmentCount = 0;
  for (const row of rows) {
    const fields = entities.get(row.table);
    assert.ok(fields, `场景使用未知表 ${row.table}`);
    for (const assignment of fieldAssignments(row.data)) {
      assignmentCount += 1;
      const field = fields.get(assignment.field);
      assert.ok(field, `${row.table} 使用不存在字段 ${assignment.field}`);
      assertValueMatchesType(row.table, field, assignment.value);
      const allowed = allowedValues(field);
      if (allowed && assignment.value !== "NULL") {
        assert.ok(allowed.includes(assignment.value),
          `${row.table}.${assignment.field}=${assignment.value} 不在允许值 ${allowed.join("、")} 中`);
      }
    }
    for (const rawRecord of scenarioRecords(row)) {
      const record = new Map([...Object.entries(IMPLICIT_SCENARIO_DEFAULTS), ...rawRecord]);
      for (const field of fields.values()) {
        if (!field.notNull) continue;
        assert.ok(record.has(field.defKey), `${row.table} 场景记录缺少 NN 字段 ${field.defKey}`);
        assert.notEqual(record.get(field.defKey), "NULL", `${row.table}.${field.defKey} 是 NN 却使用 NULL`);
      }
      assert.deepEqual(
        criticalTupleViolations(row.table, record),
        [],
        `${row.table} 场景记录违反关键 CHECK Tuple`,
      );
    }
  }
  assert.ok(assignmentCount >= 330, `场景字段赋值不足，当前仅 ${assignmentCount} 个`);
});

test("Platform Support scenario validator rejects identity and scope tuple mutations", () => {
  const humanWithLoginName = new Map(Object.entries({
    account_type: "HUMAN", realm: "CUSTOMER", login_name: "owner@example.test",
    email_vault_token: "vt-owner", email_masked: "o***@example.test", email_verified_time: "NULL",
  }));
  assert.deepEqual(criticalTupleViolations("t_user_account", humanWithLoginName), ["ck_user_account_shape"]);

  const mismatchedScope = new Map(Object.entries({
    tenant_scope: "TENANT", tenant_id: "210001", scope_key: "TENANT-210001",
  }));
  assert.deepEqual(criticalTupleViolations("t_role", mismatchedScope), ["ck_role_scope"]);

  const crossTenantRole = new Map(Object.entries({
    tenant_id: "210001", role_scope_key: "210002",
  }));
  assert.deepEqual(
    criticalTupleViolations("t_tenant_member_role", crossTenantRole),
    ["ck_tenant_member_role_scope"],
  );

  const crossRealmGoogle = new Map(Object.entries({
    context_type: "BOOTSTRAP", realm: "OPERATIONS", channel: "OPERATIONS",
    tenant_id: "NULL", tenant_member_id: "NULL", authentication_method: "GOOGLE",
    issued_time: "2026-09-01T08:00:00Z", last_activity_time: "2026-09-01T08:01:00Z",
    idle_expire_time: "2026-09-01T08:30:00Z", absolute_expire_time: "2026-09-01T16:00:00Z",
  }));
  assert.deepEqual(criticalTupleViolations("t_auth_session", crossRealmGoogle), ["ck_auth_session_method"]);
});

test("Platform Support scenarios demonstrate approved failure and recovery paths", () => {
  const content = readFileSync(scenarioPath, "utf8");
  for (const marker of [
    "SCN-PLT-01", "SCN-PLT-02", "SCN-PLT-03", "SCN-PLT-04",
    "SCN-PLT-05", "SCN-PLT-06", "SCN-PLT-07", "63 表", "派生 Readiness",
    "AVAILABLE → QUARANTINED", "Credit Note", "supersedes_version_id",
    "旧 ACTIVE Version", "晚到 COMPLAINT", "UNKNOWN", "Replay",
    "replay_of_delivery_id", "DNS", "Challenge", "fencing_token",
    "审计链不匹配", "INLINE", "VAULT_TOKEN", "lock_version",
    "Operations 连续 5 次 Password 失败锁定 30 分钟",
    "CUSTOMER Email 验证与 Password Reset",
    "Code/PKCE/state/nonce", "密封 Pending Binding Proof", "不自动绑定",
    "exact validated JWT `sub`", "raw canonical socket IP",
  ]) {
    assert.match(content, new RegExp(marker, "u"), `现实场景缺少关键链路：${marker}`);
  }
  assert.doesNotMatch(content, /登录 MFA[^。\n]*OTP|跨库外键|通用 Job 平台|Last-Known-Good|ROLLBACK|SECRET_REF/u);
  assert.match(content, /raw canonical socket IP.*exact validated JWT `sub`.*没有 Redis HMAC.*Access Log.*独立 HMAC/su);
  assert.doesNotMatch(content, /deployment-key HMAC|Tenant 维度累计执行/u);
  assert.doesNotMatch(content, /\bscope_hash=|reason_set_hash=|requirement_config_hash=|evidence_hash=|response_hash=/u);
  for (const [cookie, cookiePath] of [
    ["__Secure-operations-refresh", "/api/operations/auth/v1"],
    ["__Secure-client-refresh", "/api/client/auth/v1"],
    ["__Secure-partner-refresh", "/api/partner/auth/v1"],
  ]) {
    assert.match(content, new RegExp(`${cookie}[\\s\\S]{0,160}${cookiePath}`, "u"));
  }
  assertSingleFlowContains(content, "Refresh 重用处置", [
    "t_refresh_token_generation", "token_lookup_id", "CONSUMED", "正确 HMAC", "COMPROMISED", "撤销整个 Session Family",
  ]);
  assertSingleFlowContains(content, "Access Token Claim 矩阵", [
    "Account Context", "Tenant Context", "tenant_id", "tenant_type", "tenant_member_id",
    "一个 Access Token 只绑定一个当前 Tenant",
  ]);
});

test("Platform Support scenarios demonstrate all public Route channels, authorization joins and tenant-isolated limiting", () => {
  const content = readFileSync(scenarioPath, "utf8");
  assertGatewayControlScenarios(scenarioRows(content), content);
});

test("Platform Support authentication scenarios persist the approved state semantics", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  assertAssignmentValues(rows, "t_user_account", "realm", ["OPERATIONS", "CUSTOMER"]);
  assertAssignmentValues(rows, "t_user_account", "account_type", ["BOOTSTRAP_ADMIN", "HUMAN"]);
  assertAssignmentValues(rows, "t_user_account", "status", ["PENDING_VERIFICATION", "ACTIVE"]);
  assertAssignmentValues(rows, "t_password_credential", "algorithm_code", ["ARGON2ID"]);
  assertAssignmentValues(rows, "t_federated_identity", "provider_code", ["GOOGLE"]);
  assertAssignmentValues(rows, "t_authentication_challenge", "purpose", [
    "SIGN_UP_EMAIL", "PASSWORD_RESET", "TENANT_INVITATION_ACCEPT",
  ]);
  assertContextClaimMatrices(rows);
  assertRefreshReuseCompromise(rows);
  assertLinkedAuthenticationBranches(rows);
  assertDualTenantMembership(rows);
  assertInvitationAcceptance(rows);
  assertAssignmentValues(rows, "t_tenant_invitation", "status", ["PENDING", "ACCEPTED"]);
  assertAssignmentValues(rows, "t_access_tenant", "owner_status", ["PENDING", "ACTIVE"]);
  assertAssignmentValues(rows, "t_access_tenant", "security_status", ["ACTIVE", "SUSPENDED"]);
});

test("Platform Support scenarios freeze Tenant ownership and simplified lifecycles", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  assert.deepEqual(apiCredentialTenantViolations(rows), []);
  for (const table of [
    "t_api_credential", "t_consumer_document", "t_notification_request",
    "t_notification_delivery", "t_webhook_endpoint", "t_webhook_event",
    "t_webhook_delivery", "t_webhook_attempt",
  ]) {
    assert.ok(assignmentValues(rows, table, "tenant_id").some(isPresent), `${table} 缺少 Tenant 归属`);
  }
  assertScenarioRecord(rows, "t_api_credential", {
    key_prefix: (value) => isPresent(value),
    key_verifier_phc: (value) => isPresent(value),
    status: "ACTIVE",
  });
  assertScenarioRecord(rows, "t_notification_delivery", {
    address_vault_token: (value) => isPresent(value),
    message_vault_token: (value) => isPresent(value),
    notification_route_id: (value) => isPresent(value),
  });
  assertAssignmentValues(rows, "t_notification_attempt", "result", ["ACCEPTED", "UNKNOWN"]);
  assertDocumentVersionRecovery(rows);
  assertDirectWebhookReplay(rows);
});

test("Platform Support scenario validator rejects a Credential rebound to a Partner Tenant", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  const mutatedRows = rows.map((row) => (
    row.table === "t_api_credential"
      ? { ...row, data: row.data.replace("tenant_id=210001", "tenant_id=210002") }
      : row
  ));
  assert.deepEqual(apiCredentialTenantViolations(mutatedRows), [
    "credential 240001 must join one ACTIVE CLIENT Tenant",
    "credential 240001 must return tenant_id from verified Access Tenant",
    "credential 240001 must return tenant_type from verified Access Tenant",
    "credential 240001 must return owner_ref_id from verified Access Tenant",
  ]);
});

test("Platform Support scenario validator freezes PLT-I03 owner authority and rejects caller or Key Prefix overrides", () => {
  const rows = scenarioRows(readFileSync(scenarioPath, "utf8"));
  assert.deepEqual(apiCredentialTenantViolations(rows, APPROVED_API_CREDENTIAL_VALIDATIONS), []);

  for (const [field, value, expectedViolation] of [
    ["returnedOwnerRefId", "200002", "must return owner_ref_id from verified Access Tenant"],
    ["callerSuppliedOwnerRefId", "200002", "must reject caller owner_ref_id"],
    ["keyPrefixDerivedOwnerRefId", "200002", "must not infer owner_ref_id from Key Prefix"],
  ]) {
    const validations = APPROVED_API_CREDENTIAL_VALIDATIONS.map((validation) => ({
      ...validation,
      [field]: value,
    }));
    assert.ok(
      apiCredentialTenantViolations(rows, validations).some((violation) => violation.includes(expectedViolation)),
      `PLT-I03 validator accepted forbidden ${field}`,
    );
  }
});

test("Platform Support scenario Redis identity contract rejects contradictory HMAC requirements", () => {
  const content = readFileSync(scenarioPath, "utf8");
  assert.deepEqual(redisIdentityHmacContradictions(content), []);
  for (const contradiction of [
    "Redis identity inputs MUST be HMAC-transformed before limiter lookup.",
    "Redis 身份输入必须先做 HMAC，再用于限流键。",
  ]) {
    assert.equal(redisIdentityHmacContradictions(`${content}\n${contradiction}`).length, 1);
  }
});

test("Platform Support scenarios retain the minimum-data security boundary", () => {
  const content = readFileSync(scenarioPath, "utf8");
  const assignments = scenarioRows(content).flatMap((row) => fieldAssignments(row.data));
  assert.match(content, /Vault Token/u);
  assert.match(content, /File ID.*Content Hash/su);
  assert.match(content, /Argon2id PHC/u);
  assert.match(content, /不保存[^。\n]*(?:PAN|CVV)/u);
  for (const assignment of assignments) {
    assert.ok(![
      "password", "plaintext_password", "recoverable_password", "access_token", "refresh_token",
      "id_token", "authorization_code", "client_secret", "private_key", "pan", "cvv",
    ].includes(assignment.field), `场景不得持久化认证或卡秘密字段：${assignment.field}`);
  }
  assert.doesNotMatch(content, /vault_record_version=|vault_metadata_hash=|password=|private_key=|client_secret=|access_token=|refresh_token=|id_token=|authorization_code=|\bpan=|\bcvv=/iu);
});
