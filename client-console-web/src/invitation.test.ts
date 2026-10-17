import { test } from "node:test";
import assert from "node:assert/strict";
import {
  afterLoginPath, captureInvitationLink, clearStoredInvitation, invitationOpen, invitationProblem, invitationWorkspaceName, readAcceptance,
  readDetails, readPreview, readStoredInvitation,
} from "./invitation.ts";

function memoryStore() {
  const values = new Map<string, string>();
  return {
    values,
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => { values.set(key, value); },
    removeItem: (key: string) => { values.delete(key); },
  };
}
const challengeNo = "12345678-1234-4234-8234-123456789abc", secret = "B".repeat(43);
const fragment = "#challengeNo=" + challengeNo + "&secret=" + secret;

test("invitation link is captured only on the accept path and survives the login round trip", () => {
  const store = memoryStore();
  assert.equal(captureInvitationLink("/verify-email", fragment, store), null);
  assert.equal(store.values.size, 0);
  assert.equal(captureInvitationLink("/invitations/accept", "#challengeNo=" + challengeNo + "&secret=short", store), null);
  assert.equal(afterLoginPath(store), "/onboarding");
  assert.deepEqual(captureInvitationLink("/invitations/accept", fragment, store), { challengeNo, secret });
  assert.deepEqual(readStoredInvitation(store), { challengeNo, secret });
  assert.equal(afterLoginPath(store), "/invitations/accept");
  clearStoredInvitation(store);
  assert.equal(afterLoginPath(store), "/onboarding");
});

test("tampered or unavailable storage never redirects to the accept page", () => {
  const store = memoryStore();
  for (const raw of ["not json", "null", JSON.stringify({ challengeNo, secret: "x" }), JSON.stringify({ challengeNo: "../x", secret })]) {
    store.values.set("paytech-client-pending-invitation", raw);
    assert.equal(readStoredInvitation(store), null);
    assert.equal(afterLoginPath(store), "/onboarding");
  }
  const broken = { getItem: () => { throw new Error("blocked"); }, setItem: () => { throw new Error("blocked"); }, removeItem: () => { throw new Error("blocked"); } };
  assert.deepEqual(captureInvitationLink("/invitations/accept", fragment, broken), { challengeNo, secret });
  assert.equal(afterLoginPath(broken), "/onboarding");
  assert.doesNotThrow(() => clearStoredInvitation(broken));
  assert.equal(afterLoginPath(null), "/onboarding");
});

test("preview, details and acceptance responses are validated", () => {
  const preview = { emailMasked: "d***@example.test", roleCode: "TENANT_DEVELOPER", status: "PENDING", expiresAt: "2026-10-24T00:00:00Z" };
  assert.deepEqual(readPreview(preview), preview);
  assert.equal(readPreview({ ...preview, status: undefined }), null);
  const details = readDetails({ ...preview, emailMatches: true, clientId: "21", workspaceName: null, clientNo: "CL-21", clientType: null });
  assert.equal(details?.workspaceName, null);
  assert.equal(details && invitationWorkspaceName(details), "CL-21");
  assert.equal(invitationWorkspaceName({ workspaceName: null, clientNo: null }), "名称暂不可用");
  assert.equal(invitationWorkspaceName({ workspaceName: "Example Trading LLC", clientNo: "CL-21" }), "Example Trading LLC");
  assert.equal(readDetails(preview), null);
  assert.equal(readDetails({ ...preview, emailMatches: false })?.clientId, null);
  assert.equal(readAcceptance({ outcome: "ALREADY_MEMBER", tenantId: "7", memberId: "8", clientId: "21" })?.outcome, "ALREADY_MEMBER");
  assert.equal(readAcceptance({ outcome: "MAYBE" }), null);
});

test("status and error codes map to page states", () => {
  assert.equal(invitationOpen("PENDING"), true);
  assert.equal(invitationOpen("ACCEPTED"), true);
  assert.equal(invitationOpen("EXPIRED"), false);
  assert.equal(invitationOpen("REVOKED"), false);
  assert.equal(invitationProblem("CLIENT_INVITATION_INVALID"), "invalid");
  assert.equal(invitationProblem("CLIENT_INVITATION_CLOSED"), "closed");
  assert.equal(invitationProblem("CLIENT_INVITATION_EMAIL_MISMATCH"), "mismatch");
  assert.equal(invitationProblem("CLIENT_WORKSPACE_UNAVAILABLE"), "unavailable");
  assert.equal(invitationProblem("CLIENT_ACCOUNT_CONTEXT_REQUIRED"), "account-context");
  assert.equal(invitationProblem("SOMETHING_ELSE"), null);
});
