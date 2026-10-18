"""2026-10-17 团队成员与邀请 API 级端到端验收（阶段 5 V2 本机全链路，任务卡 14；由 requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/ 同名脚本复制改写，只跑本机）：
经 Nginx → Gateway → BFF → Platform / client-master / compliance 真实进程。

覆盖 requirements/20261017/client-console-团队成员与邀请 的 AC-01—AC-14、AC-16（AC-15 并发接受由 Platform auto 测试覆盖）：
  所有者入驻批准并进入工作区 → 团队能力 → 发起邀请（重复、已是成员）→ 重发后旧链接失效 → 公开预览
  → 新用户注册 → 详情（工作区名称、邮箱一致）→ 接受 → 我的工作区出现 → 进入后只有开发者权限 → 重复接受幂等
  → 邮箱不一致拒绝 → 降级为只读立即生效 → 所有者 / 自己受保护 → 撤销邀请后预览与接受 → 移除成员后会话降级、列表消失
  → 重新邀请恢复原成员 → 跨工作区 404。
本机收不到认证邮件，按邀请 ID 在 Platform 库（mor_platform_v2e2e）铸造已知秘密（HMAC 输入与 ClientEmailChallengePolicy.proofInput 一致）。
输出每步 PASS/FAIL 与证据；不输出密码、令牌、Cookie、链接 Secret 或邮箱原值。
用法：python3 team_flow.py
"""
import base64
import hashlib
import hmac
import json
import secrets as pysecrets
import time
import uuid

import acceptance_flow as flow
import workspace_flow as wf
from acceptance_flow import RESULTS, Client, Stop, problem, record
from e2e_http import Failure, platform_sql, secrets

PASSWORD = "Team-" + uuid.uuid4().hex[:16]


def mint_invitation_link(invitation_id):
    """为该邀请当前 PENDING 的挑战写入已知秘密的 HMAC，返回 (challengeNo, secret)；找不到返回 (None, None)。"""
    keys = secrets()
    rows = platform_sql("SELECT challenge_id,challenge_no,active_scope_hash FROM t_authentication_challenge "
                        f"WHERE purpose='TENANT_INVITATION_ACCEPT' AND status='PENDING' AND tenant_invitation_id={int(invitation_id)} "
                        "ORDER BY create_time DESC LIMIT 1")
    if not rows:
        return None, None
    challenge_id, challenge_no, scope_hash = rows[0]
    secret = base64.urlsafe_b64encode(pysecrets.token_bytes(32)).decode().rstrip("=")
    proof = "".join(f"{len(v.encode())}:{v}" for v in
                    ("CUSTOMER-EMAIL-CHALLENGE", challenge_no, "TENANT_INVITATION_ACCEPT", scope_hash, secret))
    digest = hmac.new(base64.b64decode(keys["CLIENT_LOCAL_HMAC_KEY_BASE64"]),
                      ("client-email-challenge-v1" + "\\0" + proof).encode(), hashlib.sha256).hexdigest()
    platform_sql(f"UPDATE t_authentication_challenge SET secret_hmac='{digest}' WHERE challenge_id={int(challenge_id)} "
                 "AND status='PENDING'")
    return challenge_no, secret


def link(invitation_id, step):
    challenge, secret = mint_invitation_link(invitation_id)
    record(step, bool(challenge), f"invitationId={invitation_id} challengeNo={challenge}")
    return {"challengeNo": challenge, "secret": secret}


def failure_code(action):
    """执行预期失败的调用，返回 (HTTP 状态, Problem code)；意外成功返回 (状态, None)。"""
    try:
        status, _ = action()
        return status, None
    except Failure as failure:
        body = json.loads(failure.body) if failure.body else {}
        return failure.status, body.get("code")


def expect(step, action, status, code):
    got = failure_code(action)
    record(step, got == (status, code), f"预期 HTTP {status} {code}，实际 {got}")


def enter(client, member_id):
    """进入工作区（C02），返回首页能力列表。"""
    status, grant = client.call("/auth/v1/workspace-context", "POST", {"memberId": member_id})
    client.token = grant["accessToken"]
    _, home = client.call("/workspace/v1/home")
    return status, grant["context"], sorted(home.get("capabilities") or [])


def back_to_account(client):
    _, grant = client.call("/auth/v1/account-context", "POST", {})
    client.token = grant["accessToken"]


def invite(owner, email, role):
    return owner.call("/team/v1/invitations", "POST", {"email": email, "roleCode": role})


def owner_workspace(stamp):
    client, ops, app = wf.approved_client(stamp)
    ws, item = wf.wait_accessible(client, app["clientId"])
    record("所有者工作区可访问", item is not None, f"clientId={app['clientId']}")
    status, context, caps = enter(client, item["memberId"])
    record("AC-13 所有者团队能力", status == 200 and {"TEAM_READ", "TEAM_MANAGE"} <= set(caps), f"capabilities={caps}")
    return client, ops, app, item["memberId"], context["tenantId"]


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    invitee_email = f"team-invitee-{stamp}@example.test"
    data = {}
    try:
        owner, ops, app, owner_member, tenant_id = owner_workspace(stamp)
        data.update(clientId=app["clientId"], tenantId=tenant_id)

        status, members = owner.call("/team/v1/members")
        record("UI-01 成员列表只有所有者", status == 200 and members["total"] == 1 and members["items"][0]["self"]
               and members["items"][0]["roleCode"] == "TENANT_OWNER", f"items={members['items']}")
        status, pending = owner.call("/team/v1/invitations")
        record("AC-01 邀请列表为空", status == 200 and pending["total"] == 0, f"total={pending['total']}")

        status, created = invite(owner, invitee_email, "TENANT_DEVELOPER")
        invitation_id = created["invitationId"]
        data["invitationId"] = invitation_id
        record("AC-01 发起邀请", status == 201 and created["status"] == "PENDING" and "@" in created["emailMasked"]
               and invitee_email not in json.dumps(created) and created["emailDelivery"] in ("SENT", "FAILED"),
               f"HTTP {status} emailMasked={created['emailMasked']} emailDelivery={created['emailDelivery']} expiresAt={created['expiresAt']}")
        expect("AC-02 同邮箱重复邀请", lambda: invite(owner, invitee_email, "TENANT_VIEWER"), 409, "CLIENT_INVITATION_DUPLICATE")
        _, owner_context = owner.call("/auth/v1/context")
        expect("AC-02 所有者邮箱已是成员", lambda: invite(owner, f"workspace-{stamp}@example.test", "TENANT_VIEWER"),
               409, "CLIENT_MEMBER_EXISTS")

        old = link(invitation_id, "重发前铸造旧链接")
        status, resent = owner.call(f"/team/v1/invitations/{invitation_id}/resend", "POST")
        record("AC-03 重发", status == 200 and resent["status"] == "PENDING", f"HTTP {status} expiresAt={resent['expiresAt']}")
        expect("AC-03 旧链接失效", lambda: Client().call("/invitations/v1/preview", "POST", old, auth=False),
               404, "CLIENT_INVITATION_INVALID")
        proof = link(invitation_id, "重发后铸造新链接")
        status, preview = Client().call("/invitations/v1/preview", "POST", proof, auth=False)
        record("UI-04 公开预览", status == 200 and preview["status"] == "PENDING" and preview["roleCode"] == "TENANT_DEVELOPER"
               and set(preview) == {"emailMasked", "roleCode", "status", "expiresAt"}, f"preview={preview}")

        invitee = Client()
        flow.register_and_login(invitee, invitee_email, PASSWORD)
        status, details = invitee.call("/invitations/v1/details", "POST", proof)
        record("UI-04 登录后详情含工作区名称", status == 200 and details["emailMatches"] and details["clientId"] == app["clientId"]
               and details.get("workspaceName"), f"workspaceName={details.get('workspaceName')} clientNo={details.get('clientNo')}")
        stranger = Client()
        flow.register_and_login(stranger, f"team-stranger-{stamp}@example.test", PASSWORD)
        status, mismatch = stranger.call("/invitations/v1/details", "POST", proof)
        record("AC-07 邮箱不一致详情不给工作区", status == 200 and not mismatch["emailMatches"] and mismatch.get("clientId") is None
               and mismatch.get("workspaceName") is None, f"emailMatches={mismatch['emailMatches']}")
        expect("AC-07 邮箱不一致拒绝接受", lambda: stranger.call("/invitations/v1/acceptance", "POST", proof),
               403, "CLIENT_INVITATION_EMAIL_MISMATCH")

        status, accepted = invitee.call("/invitations/v1/acceptance", "POST", proof)
        record("AC-06 新用户注册后接受", status == 200 and accepted["outcome"] == "JOINED", f"outcome={accepted['outcome']}")
        invitee_member = accepted["memberId"]
        ws, item = wf.wait_accessible(invitee, app["clientId"], 10)
        record("AC-05 我的工作区出现（非申请人可见名称）", item is not None and item.get("displayName") and item.get("clientNo"),
               f"item={item}")
        status, again = invitee.call("/invitations/v1/acceptance", "POST", proof)
        record("AC-08 已接受链接再次接受幂等", status == 200 and again["outcome"] == "ALREADY_MEMBER", f"outcome={again['outcome']}")

        status, context, caps = enter(invitee, invitee_member)
        record("AC-05 开发者权限", status == 200 and set(caps) == {"TEAM_READ", "API_CREDENTIAL_READ", "API_CREDENTIAL_MANAGE",
               "WEBHOOK_READ", "WEBHOOK_MANAGE"}, f"capabilities={caps}")
        status, seen = invitee.call("/team/v1/members")
        record("Q3 无管理权限可看成员", status == 200 and seen["total"] == 2, f"total={seen['total']}")
        expect("AC-13 开发者看邀请列表", lambda: invitee.call("/team/v1/invitations"), 403, "CLIENT_PERMISSION_DENIED")
        expect("AC-13 开发者发起邀请", lambda: invite(invitee, f"team-x-{stamp}@example.test", "TENANT_VIEWER"),
               403, "CLIENT_PERMISSION_DENIED")

        status, changed = owner.call(f"/team/v1/members/{invitee_member}/role", "POST", {"roleCode": "TENANT_VIEWER"})
        record("AC-10 降级为只读", status == 200 and changed["roleCode"] == "TENANT_VIEWER" and changed["memberType"] == "MEMBER",
               f"roleCode={changed['roleCode']}")
        _, home = invitee.call("/workspace/v1/home")
        caps = sorted(home.get("capabilities") or [])
        record("AC-10 降级立即生效（同一 Token）", set(caps) == {"TEAM_READ", "API_CREDENTIAL_READ", "WEBHOOK_READ"}, f"capabilities={caps}")
        expect("AC-10 只读成员创建凭证", lambda: invitee.call("/developer/v1/credentials", "POST",
               {"name": "team", "scopeCodes": ["orders.read"], "validityDays": 30}), 403, "CLIENT_PERMISSION_DENIED")
        expect("AC-10 不能修改所有者", lambda: owner.call(f"/team/v1/members/{owner_member}/role", "POST",
               {"roleCode": "TENANT_VIEWER"}), 409, "CLIENT_MEMBER_PROTECTED")
        expect("AC-11 不能移除自己", lambda: owner.call(f"/team/v1/members/{owner_member}/removal", "POST"),
               409, "CLIENT_MEMBER_PROTECTED")

        revoked_email = f"team-revoked-{stamp}@example.test"
        _, second = invite(owner, revoked_email, "TENANT_VIEWER")
        second_proof = link(second["invitationId"], "第二个邀请铸造链接")
        status, revoked = owner.call(f"/team/v1/invitations/{second['invitationId']}/revocation", "POST")
        record("AC-04 撤销邀请", status == 200 and revoked["status"] == "REVOKED", f"status={revoked['status']}")
        status, again = owner.call(f"/team/v1/invitations/{second['invitationId']}/revocation", "POST")
        record("AC-04 重复撤销返回原结果", status == 200 and again["status"] == "REVOKED", f"status={again['status']}")
        status, revoked_preview = Client().call("/invitations/v1/preview", "POST", second_proof, auth=False)
        record("AC-04 撤销后预览显示已撤销", status == 200 and revoked_preview["status"] == "REVOKED"
               and set(revoked_preview) == {"emailMasked", "roleCode", "status", "expiresAt"}, f"status={revoked_preview['status']}")
        revoked_user = Client()
        flow.register_and_login(revoked_user, revoked_email, PASSWORD)
        expect("AC-04 撤销后不能接受", lambda: revoked_user.call("/invitations/v1/acceptance", "POST", second_proof),
               409, "CLIENT_INVITATION_CLOSED")
        _, listed = owner.call("/team/v1/invitations?status=REVOKED")
        record("AC-04 邀请列表按状态筛选", any(i["invitationId"] == second["invitationId"] for i in listed["items"]),
               f"total={listed['total']}")

        status, removed = owner.call(f"/team/v1/members/{invitee_member}/removal", "POST")
        record("AC-11 移除成员", status == 200 and removed["status"] == "REVOKED", f"status={removed['status']}")
        expect("AC-11 被移除成员下次请求被拒", lambda: invitee.call("/workspace/v1/home"), 401, "CLIENT_SESSION_INVALID")
        status, refreshed = invitee.call("/auth/v1/refresh", "POST", {}, auth=False)
        invitee.token = refreshed["accessToken"]
        record("AC-11 刷新降级为账号上下文", status == 200 and refreshed["context"]["contextType"] == "ACCOUNT"
               and refreshed["context"].get("fallbackReason") == "MEMBER_INACTIVE",
               f"fallbackReason={refreshed['context'].get('fallbackReason')}")
        _, ws = wf.workspaces(invitee)
        record("AC-11 我的工作区不再显示", not any(i.get("clientId") == app["clientId"] for i in ws.get("items") or []),
               f"items={len(ws.get('items') or [])}")
        _, members = owner.call("/team/v1/members")
        record("AC-11 成员列表不再显示", members["total"] == 1, f"total={members['total']}")

        status, reinvited = invite(owner, invitee_email, "TENANT_ADMIN")
        record("AC-12 重新邀请已移除成员", status == 201, f"HTTP {status}")
        reproof = link(reinvited["invitationId"], "重新邀请铸造链接")
        status, accepted = invitee.call("/invitations/v1/acceptance", "POST", reproof)
        record("AC-12 接受后恢复原成员", status == 200 and accepted["outcome"] == "JOINED" and accepted["memberId"] == invitee_member,
               f"memberId 相同={accepted['memberId'] == invitee_member}")
        status, context, caps = enter(invitee, invitee_member)
        record("AC-12 恢复为管理员", status == 200 and {"TEAM_READ", "TEAM_MANAGE"} <= set(caps), f"capabilities={caps}")
        _, members = owner.call("/team/v1/members")
        record("AC-12 无重复成员", members["total"] == 2, f"total={members['total']}")
        expect("Q2 管理员不能移除所有者", lambda: invitee.call(f"/team/v1/members/{owner_member}/removal", "POST"),
               409, "CLIENT_MEMBER_PROTECTED")

        other, _, other_app, other_member, _ = owner_workspace(stamp + "b")
        expect("AC-14 跨工作区撤销邀请", lambda: other.call(f"/team/v1/invitations/{invitation_id}/revocation", "POST"),
               404, "NOT_FOUND")
        expect("AC-14 跨工作区移除成员", lambda: other.call(f"/team/v1/members/{invitee_member}/removal", "POST"),
               404, "NOT_FOUND")
        back_to_account(owner)
        expect("AC-13 账号上下文访问团队", lambda: owner.call("/team/v1/members"), 403, "CLIENT_WORKSPACE_CONTEXT_REQUIRED")
    except Stop:
        pass
    except Failure as failure:
        RESULTS.append(("未预期 HTTP 失败", "FAIL", problem(failure)))
        print("FAIL unexpected HTTP | " + problem(failure))
    finally:
        print("\n==== 汇总 ====")
        print(f"stamp={stamp} data={data}")
        passed = sum(1 for _, verdict, _ in RESULTS if verdict == "PASS")
        print(f"\n合计 {passed}/{len(RESULTS)} 通过")
        for step, verdict, _ in RESULTS:
            print(f"{verdict:4} {step}")
    raise SystemExit(0 if RESULTS and passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
