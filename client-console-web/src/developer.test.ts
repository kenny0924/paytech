import assert from "node:assert/strict";
import { test } from "node:test";
import {
  canCloseSecret,
  canReplay,
  credentialFormIssues,
  credentialNameIssue,
  deliveryQuery,
  developerMenu,
  emptyDeliveryFilter,
  endpointFormIssues,
  endpointUrlIssue,
  readCapabilities,
  readCredentials,
  readEndpoints,
  readSecret,
} from "./developer.ts";
import { readHome } from "./workspace.ts";

test("capabilities drive the developer menu and unknown codes are ignored", () => {
  assert.deepEqual(developerMenu([]), []);
  assert.deepEqual(developerMenu(readCapabilities(["API_CREDENTIAL_READ", "BOGUS"])).map((i) => i.key), ["api-keys"]);
  assert.deepEqual(developerMenu(readCapabilities(["WEBHOOK_MANAGE"])).map((i) => i.path), ["/workspace/developer/webhooks"]);
  assert.deepEqual(readCapabilities("API_CREDENTIAL_READ"), []);
  assert.deepEqual(readCapabilities(["TEAM_MANAGE", "TEAM_READ", "TEAM_OWNER"]), ["TEAM_READ", "TEAM_MANAGE"]);
  assert.deepEqual(developerMenu(readCapabilities(["TEAM_READ", "TEAM_MANAGE"])), []);
  const home = readHome({ workspace: { clientId: "1", clientNo: "CL-1", displayName: null, clientType: "INDIVIDUAL", clientStatus: "ACTIVE" },
    profile: {}, notice: null, capabilities: ["WEBHOOK_READ"] });
  assert.deepEqual(home?.capabilities, ["WEBHOOK_READ"]);
  const legacy = readHome({ workspace: { clientId: "1", clientNo: "CL-1", displayName: null, clientType: "INDIVIDUAL", clientStatus: "ACTIVE" }, profile: {} });
  assert.deepEqual(legacy?.capabilities, []);
});

test("secret modal closes only after acknowledgement and secrets are read strictly", () => {
  assert.equal(canCloseSecret(false), false);
  assert.equal(canCloseSecret(true), true);
  assert.equal(readSecret({ secret: "ck_abcdefghijklmnopqrst.secret" }, "secret"), "ck_abcdefghijklmnopqrst.secret");
  assert.equal(readSecret({ secret: "short" }, "secret"), null);
  assert.equal(readSecret({ secret: "has space in it here" }, "secret"), null);
  assert.equal(readSecret({ signingSecret: "whsec_" + "a".repeat(43) }, "signingSecret"), "whsec_" + "a".repeat(43));
});

test("credential form validation", () => {
  assert.equal(credentialNameIssue("  "), "Enter a credential name");
  assert.equal(credentialNameIssue("a".repeat(65)), "The name can't be longer than 64 characters");
  assert.equal(credentialNameIssue("bad\u0007"), "The name can't contain control characters");
  assert.deepEqual(credentialFormIssues({ name: "Prod", scopeCodes: ["orders.read"], validityDays: 365 }, ["orders.read"]), {});
  const issues = credentialFormIssues({ name: "", scopeCodes: [], validityDays: 400 }, ["orders.read"]);
  assert.deepEqual(Object.keys(issues).sort(), ["name", "scopeCodes", "validityDays"]);
  assert.ok(credentialFormIssues({ name: "x", scopeCodes: ["x", "x"], validityDays: 30 }, ["x"]).scopeCodes);
  assert.ok(credentialFormIssues({ name: "x", scopeCodes: ["other"], validityDays: 30 }, ["x"]).scopeCodes);
});

test("endpoint form validation", () => {
  assert.equal(endpointUrlIssue("http://example.com/hook"), "The URL must use HTTPS");
  assert.equal(endpointUrlIssue("https://user:pw@example.com"), "The URL can't contain a username or password");
  assert.equal(endpointUrlIssue("not a url"), "The URL format is invalid");
  assert.equal(endpointUrlIssue("https://example.com/hook"), null);
  assert.deepEqual(endpointFormIssues({ url: "https://example.com/h", eventTypes: ["order.paid"] }), {});
  assert.ok(endpointFormIssues({ url: "https://example.com/h", eventTypes: [] }).eventTypes);
  assert.ok(endpointFormIssues({ url: "https://example.com/h", eventTypes: ["webhook.ping"] }).eventTypes);
});

test("list readers reject malformed items and delivery query is bounded", () => {
  assert.equal(readCredentials({ items: [{ credentialId: "1" }] }), null);
  const credentials = readCredentials({ items: [{ credentialId: "1", name: "A", keyPrefix: "ck_x", status: "ACTIVE", scopeCodes: ["s"],
    createdAt: "2026-10-16T00:00:00Z", expiresAt: "2027-10-16T00:00:00Z", rotatedFromId: null, version: 1, secret: "must-not-leak" }] });
  assert.equal(credentials?.length, 1);
  assert.equal("secret" in (credentials![0] as object), false);
  assert.equal(readEndpoints({ items: [{ endpointId: "1", url: "https://x", eventTypes: [], status: "ACTIVE" }] }), null);
  assert.equal(readEndpoints({ items: [{ endpointId: "1", url: "https://x", eventTypes: ["order.paid"], status: "ACTIVE", version: 2,
    signingSecret: { rotationNo: 3, previousValidUntil: null } }] })?.[0].rotationNo, 3);
  const query = new URLSearchParams(deliveryQuery({ ...emptyDeliveryFilter, status: "DEAD", size: 500, page: 0 }));
  assert.equal(query.get("status"), "DEAD");
  assert.equal(query.get("size"), "50");
  assert.equal(query.get("page"), "1");
  assert.equal(query.has("endpointId"), false);
});

test("replay is offered only for finished deliveries", () => {
  assert.equal(canReplay("SUCCEEDED"), true);
  assert.equal(canReplay("DEAD"), true);
  assert.equal(canReplay("CANCELLED"), true);
  assert.equal(canReplay("RETRY_WAIT"), false);
  assert.equal(canReplay("PROCESSING"), false);
  assert.equal(canReplay(null), false);
});
