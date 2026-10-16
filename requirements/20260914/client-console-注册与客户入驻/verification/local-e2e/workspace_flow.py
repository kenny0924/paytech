"""2026-10-16 租户与工作区 API 级端到端验收（本机全链路）：经 Nginx → Gateway → BFF → Platform/CCT/Risk 真实进程。

覆盖 requirements/20261016/client-console-租户与工作区 的 AC-01—AC-11（AC-03 投影未完成窗口无法稳定构造，不覆盖）：
  批准 → C01 工作区可访问（CCT 名称）→ C02 进入（Cookie 替换、旧 Refresh 失效）→ W02 首页 → 刷新保持租户上下文
  → 运营安全暂停 → 刷新降级为账号上下文（fallbackReason）→ C01 受限 → C02 403 → 恢复 → 再进入 → W01 返回账号
  → 账号上下文 W02 403；另一账号（进行中申请，PENDING 工作区）列表为空、用他人 memberId 进入 403。
输出每步 PASS/FAIL 与证据；不输出密码、令牌、Cookie、预签名 URL。
用法：python3 workspace_flow.py
"""
import base64
import json
import os
import time
import urllib.error
import urllib.request
import uuid

import acceptance_flow as flow
from acceptance_flow import RESULTS, Client, Stop, problem, record
from e2e_http import CLIENT, TLS, Failure, Operations, secrets

COOKIE = "__Secure-client-refresh"


def cookie_value(client):
    found = [c.value for c in client.jar if c.name == COOKIE]
    return found[0] if found else None


def refresh_with(cookie):
    """用指定的 Refresh Cookie 直接调用刷新，返回 HTTP 状态（不影响任何 Client 会话的 Cookie Jar）。"""
    request = urllib.request.Request(CLIENT + "/api/client/auth/v1/refresh", data=b"{}", method="POST", headers={
        "Origin": CLIENT, "Accept": "application/json", "Content-Type": "application/json", "Cookie": f"{COOKIE}={cookie}"})
    try:
        with urllib.request.urlopen(request, context=TLS, timeout=20) as response:
            return response.status
    except urllib.error.HTTPError as failure:
        return failure.code


def claims(token):
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def expect_failure(step, action, status, code):
    try:
        action()
    except Failure as failure:
        body = json.loads(failure.body) if failure.body else {}
        record(step, failure.status == status and body.get("code") == code, problem(failure))
        return
    record(step, False, f"预期 HTTP {status} {code}，实际成功")


def workspaces(client):
    status, start = client.call("/onboarding/v1/start")
    return status, start.get("workspaces") or {}


def approved_client(stamp):
    """走完整入驻：注册 → 资料 → 上传 → 提交 → 运营核验与分类 → 批准；返回 (client, ops, application)。"""
    client = Client()
    flow.register_and_login(client, f"workspace-{stamp}@example.test", "Workspace-" + uuid.uuid4().hex[:16])
    application_id = flow.start_application(client)
    options = flow.form_options(client)
    flow.fill_profile(client, application_id, options, stamp)
    app = flow.read_application(client, application_id)
    flow.upload_required(client, application_id, app)
    submission_id = flow.submit(client, application_id, "")
    ops = Operations(secrets()["E2E_ADMIN_EMAIL"], secrets()["E2E_ADMIN_PASSWORD"])
    case_id, detail, evidence = flow.review_case(ops, application_id, submission_id, "")
    flow.approve_case(ops, case_id, detail, evidence)
    app = flow.wait_status(client, application_id, ("APPROVED",), 60)
    record("C05 已批准", app["status"] == "APPROVED", f"status={app['status']} clientId={app.get('clientId')}")
    return client, ops, app


def wait_accessible(client, client_id, seconds=60):
    deadline, ws = time.time() + seconds, {}
    while time.time() < deadline:
        _, ws = workspaces(client)
        for item in ws.get("items") or []:
            if item.get("clientId") == client_id and item.get("state") == "ACCESSIBLE":
                return ws, item
        time.sleep(3)
    return ws, None


def change_security(ops, tenant_id, status):
    """测试夹具：运营租户访问路由在本机未启用（DISABLED），直接更新 Platform 库该租户的安全状态与版本。

    只改该租户一行；其余链路（刷新、上下文、C01、C02）仍经真实入口。本机 mor_platform_e2e，mor-test 为 mor_platform。
    """
    from e2e_http import platform_sql
    rows = platform_sql(f"UPDATE t_access_tenant SET security_status='{status}', version=version+1, update_time=UTC_TIMESTAMP(6) "
                        f"WHERE tenant_id={int(tenant_id)} AND tenant_type='CLIENT'; SELECT ROW_COUNT();")
    record(f"夹具：安全状态 → {status}", rows and rows[-1][0] == "1", f"tenantId={tenant_id} rows={rows}")


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    data = {}
    try:
        client, ops, app = approved_client(stamp)
        client_id = app["clientId"]
        data["clientId"] = client_id

        ws, item = wait_accessible(client, client_id)
        record("AC-02 C01 工作区可访问", item is not None and ws.get("status") == "AVAILABLE" and item.get("displayName"),
               f"status={ws.get('status')} item={item}")
        member_id = item["memberId"]
        data["memberId"] = member_id

        old_cookie = cookie_value(client)
        status, grant = client.call("/auth/v1/workspace-context", "POST", {"memberId": member_id})
        client.token = grant["accessToken"]
        context = grant["context"]
        token_claims = claims(client.token)
        tenant_id = context.get("tenantId")
        data["tenantId"] = tenant_id
        record("AC-04 C02 进入工作区", status == 200 and context.get("contextType") == "TENANT"
               and context.get("memberId") == member_id and cookie_value(client) not in (None, old_cookie),
               f"HTTP {status} contextType={context.get('contextType')} tenantId={tenant_id} "
               f"allowedActions={context.get('allowedActions')} cookieReplaced={cookie_value(client) != old_cookie}")
        record("AC-11 租户 Token Claims", token_claims.get("tenant_type") == "CLIENT" and token_claims.get("tenant_id") == tenant_id
               and token_claims.get("tenant_member_id") == member_id and not any("email" in k for k in token_claims),
               f"claimKeys={sorted(token_claims)}")
        old_status = refresh_with(old_cookie)
        record("AC-04 旧账号 Refresh 失效", old_status in (401, 403), f"HTTP {old_status}")

        status, home = client.call("/workspace/v1/home")
        record("AC-09 W02 工作区首页", status == 200 and home["workspace"]["clientId"] == client_id
               and home.get("notice") == "PAYMENTS_NOT_YET_ENABLED" and "payment" not in json.dumps(home).lower().replace("payments_not_yet_enabled", ""),
               f"HTTP {status} workspace={home.get('workspace')} profile={home.get('profile')} notice={home.get('notice')}")

        status, refreshed = client.call("/auth/v1/refresh", "POST", {}, auth=False)
        client.token = refreshed["accessToken"]
        record("租户上下文刷新", status == 200 and refreshed["context"]["contextType"] == "TENANT"
               and refreshed["context"].get("fallbackReason") is None, f"HTTP {status} context={refreshed['context'].get('contextType')}")

        change_security(ops, tenant_id, "SUSPENDED")
        status, refreshed = client.call("/auth/v1/refresh", "POST", {}, auth=False)
        client.token = refreshed["accessToken"]
        record("AC-06 暂停后刷新降级", status == 200 and refreshed["context"]["contextType"] == "ACCOUNT"
               and refreshed["context"].get("fallbackReason") == "TENANT_SECURITY_SUSPENDED",
               f"HTTP {status} contextType={refreshed['context']['contextType']} fallbackReason={refreshed['context'].get('fallbackReason')}")
        _, ws = workspaces(client)
        restricted = next((i for i in ws.get("items") or [] if i.get("memberId") == member_id), None)
        record("AC-06 C01 显示受限", restricted is not None and restricted.get("state") == "RESTRICTED" and restricted.get("reasonText"),
               f"item={restricted}")
        expect_failure("AC-06 暂停时 C02 拒绝",
                       lambda: client.call("/auth/v1/workspace-context", "POST", {"memberId": member_id}), 403, "CLIENT_WORKSPACE_UNAVAILABLE")

        change_security(ops, tenant_id, "ACTIVE")
        status, grant = client.call("/auth/v1/workspace-context", "POST", {"memberId": member_id})
        client.token = grant["accessToken"]
        record("AC-07 恢复后再进入", status == 200 and grant["context"]["contextType"] == "TENANT", f"HTTP {status}")
        status, grant = client.call("/auth/v1/account-context", "POST", {})
        client.token = grant["accessToken"]
        record("AC-08 W01 返回账号", status == 200 and grant["context"]["contextType"] == "ACCOUNT"
               and grant["context"].get("tenantId") is None, f"HTTP {status} allowedActions={grant['context'].get('allowedActions')}")
        expect_failure("AC-08 账号上下文访问 W02", lambda: client.call("/workspace/v1/home"), 403, "CLIENT_WORKSPACE_CONTEXT_REQUIRED")

        other = Client()
        flow.register_and_login(other, f"workspace-other-{stamp}@example.test", "Workspace-" + uuid.uuid4().hex[:16])
        flow.start_application(other)
        _, ws = workspaces(other)
        record("AC-01/AC-10 进行中申请不列工作区", ws.get("status") == "AVAILABLE" and not ws.get("items"), f"workspaces={ws}")
        expect_failure("AC-05 他人 memberId 拒绝",
                       lambda: other.call("/auth/v1/workspace-context", "POST", {"memberId": member_id}), 403, "CLIENT_WORKSPACE_UNAVAILABLE")
        expect_failure("AC-05 不存在的 memberId 拒绝",
                       lambda: other.call("/auth/v1/workspace-context", "POST", {"memberId": "999999999999999999"}), 403,
                       "CLIENT_WORKSPACE_UNAVAILABLE")
    except Stop:
        pass
    except Failure as failure:
        RESULTS.append(("未预期 HTTP 失败", "FAIL", problem(failure)))
        print("FAIL unexpected HTTP | " + problem(failure))
    finally:
        print("\n==== 汇总 ====")
        print(f"stamp={stamp} data={data}")
        passed = sum(1 for _, verdict, _ in RESULTS if verdict == "PASS")
        print(f"{passed}/{len(RESULTS)} PASS")
        for step, verdict, _ in RESULTS:
            print(f"{verdict:4} {step}")


if __name__ == "__main__":
    main()
