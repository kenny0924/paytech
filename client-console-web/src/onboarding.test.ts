import { test } from "node:test";
import assert from "node:assert/strict";
import {
  patch,
  fieldIssues,
  createCommand,
  runUpload,
  newUpload,
  downloadUrl,
} from "./onboarding.ts";
import { RequestFailure } from "./auth.ts";
test("patch sends only changed leaves including explicit clearing", () => {
  assert.deepEqual(
    patch(
      { "individual.givenName": "Ada", "individual.familyName": "L" },
      { "individual.givenName": "Ada", "individual.familyName": "" },
    ),
    {
      dirtyFields: ["individual.familyName"],
      values: { individual: { familyName: null } },
    },
  );
});
test("uncertain command retries identical key and immutable body", async () => {
  const sent: unknown[] = [];
  let attempt = 0;
  const body = { clientType: "ORGANIZATION" };
  const command = createCommand(
    async (_path, options) => {
      sent.push(options);
      if (!attempt++) throw new RequestFailure(0, "offline");
      return { applicationId: "1" } as never;
    },
    "/onboarding/v1/applications",
    "POST",
    body,
  );
  body.clientType = "INDIVIDUAL";
  await assert.rejects(command.run());
  await command.run();
  assert.deepEqual(sent[0], sent[1]);
  assert.equal(
    (sent[0] as { body: typeof body }).body.clientType,
    "ORGANIZATION",
  );
});
test("uncertain completion retries original upload without reupload; scanning cannot associate", async () => {
  const state = newUpload("1", "2", "IDENTITY", "PASSPORT");
  let puts = 0,
    completion = 0,
    associate = 0;
  const api = async <T>(path: string): Promise<T> => {
    if (path.endsWith("/uploads"))
      return {
        uploadId: "3",
        authorization: {
          method: "PUT",
          url: "https://storage.example.test/object",
          headers: { "Content-Type": "application/pdf" },
          expiresAt: "2099-01-01",
        },
        expiresAt: "2099-01-01",
      } as T;
    if (path.endsWith("/completion")) {
      if (!completion++) throw new RequestFailure(0, "offline");
      return { uploadId: "3", fileId: "4" } as T;
    }
    if (path.endsWith("/files/4"))
      return {
        fileId: "4",
        scanStatus: "PENDING_SCAN",
        objectStatus: "ACTIVE",
        allowedActions: [],
      } as T;
    associate++;
    throw Error("must not associate");
  };
  const file = new File(["test"], "test.pdf", { type: "application/pdf" });
  const storage: typeof fetch = async (_url, options) => {
    puts++;
    assert.equal(options?.credentials, "omit");
    assert.equal(new Headers(options?.headers).get("Authorization"), null);
    return new Response(null, { status: 200 });
  };
  await assert.rejects(runUpload(state, api, file, storage));
  assert.equal(state.phase, "COMPLETE");
  await runUpload(state, api, undefined, storage);
  assert.equal(puts, 1);
  assert.equal(completion, 2);
  assert.equal(associate, 0);
  assert.equal(state.phase, "SCAN");
});
test("unknown or infected file cannot associate; available file can", async () => {
  const state = newUpload("1", "2", "IDENTITY", "PASSPORT");
  state.phase = "SCAN";
  state.fileId = "4";
  let scan = "MYSTERY",
    calls = 0;
  const api = async <T>(path: string): Promise<T> => {
    if (path.endsWith("/files/4"))
      return {
        fileId: "4",
        scanStatus: scan,
        objectStatus: "ACTIVE",
        allowedActions: ["DOWNLOAD"],
      } as T;
    calls++;
    return {} as T;
  };
  await assert.rejects(runUpload(state, api));
  scan = "INFECTED";
  await assert.rejects(runUpload(state, api));
  assert.equal(calls, 0);
  scan = "AVAILABLE";
  await runUpload(state, api);
  assert.equal(calls, 1);
  assert.equal(state.phase, "DONE");
});
test("download rejects insecure, credential-bearing and expired grants", () => {
  for (const url of [
    "http://storage.test/a",
    "https://user:pass@storage.test/a",
    "javascript:alert(1)",
  ])
    assert.throws(() => downloadUrl({ url, expiresAt: "2099-01-01" }));
  assert.throws(() =>
    downloadUrl({ url: "https://storage.test/a", expiresAt: "2000-01-01" }),
  );
});

test("checkpoint strips unexpected content and rejects a different application", async () => {
  const { readCheckpoint } = await import("./onboarding.ts");
  const state = newUpload("1", "2", "IDENTITY", "PASSPORT");
  state.phase = "COMPLETE";
  state.uploadId = "3";
  const raw = JSON.stringify({
    ...state,
    filename: "should-not-persist.pdf",
    authorization: { url: "https://secret.test" },
  });
  assert.deepEqual(readCheckpoint(raw, "1"), state);
  assert.equal(readCheckpoint(raw, "9"), null);
});
test("association response loss retries association only under the original key", async () => {
  const state = newUpload("1", "2", "IDENTITY", "PASSPORT");
  state.phase = "ASSOCIATE";
  state.uploadId = "3";
  state.fileId = "4";
  const calls: unknown[] = [];
  const api: import("./auth.ts").ApiRequest = async (path, options) => {
    calls.push({ path, options });
    if (calls.length === 1) throw new RequestFailure(0, "lost");
    return {} as never;
  };
  await assert.rejects(runUpload(state, api));
  await runUpload(state, api);
  assert.deepEqual(calls[0], calls[1]);
  assert.equal(state.phase, "DONE");
});

test("relation patch preserves concurrent untouched attributes and explicit clearing", async () => {
  const { relationPatch } = await import("./onboarding.ts");
  assert.deepEqual(
    relationPatch(
      {
        relationType: "UBO",
        ownershipPercentage: "25",
        effectiveFrom: "2026-01-01",
        effectiveTo: null,
      },
      {
        relationType: "DIRECTOR",
        ownershipPercentage: null,
        effectiveFrom: "2026-01-01",
        effectiveTo: null,
      },
    ),
    {
      dirtyFields: ["relationType", "ownershipPercentage"],
      values: { relationType: "DIRECTOR", ownershipPercentage: null },
    },
  );
});
test("save failure maps Problem errors onto known form fields only", () => {
  const failure = new RequestFailure(422, "入驻字段不符合约定", "req-1", {
    "contact.phone": "手机号须为 E.164 格式，如 +8613800138000",
    dirtyFields: "包含不支持的字段",
  }, "INVALID_DRAFT");
  assert.deepEqual(fieldIssues(failure, ["contact.phone", "contact.email"]), {
    "contact.phone": "手机号须为 E.164 格式，如 +8613800138000",
  });
  assert.deepEqual(fieldIssues(new RequestFailure(503, "unavailable"), ["contact.phone"]), {});
  assert.deepEqual(fieldIssues(new Error("offline"), ["contact.phone"]), {});
});
