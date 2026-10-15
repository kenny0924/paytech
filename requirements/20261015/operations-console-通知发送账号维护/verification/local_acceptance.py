"""阶段 5 接口验收：经运营入口（Nginx → Gateway → BFF → Platform → Vault）走发送账号维护的 AC。

前置：本机 mor_platform_e2e 已执行 ddl.sql、注册块与 activation-local.sql；本机 Vault 已有
platform-support-service / PROTECT / NOTIFICATION_PROVIDER_CREDENTIAL_V1 / NOTIFICATION_PROVIDER_SETUP 规则。
只连接本机（或 E2E_TARGET 指定的环境）；不打印 Token 与凭据。新建账号编码以 E2E_PROV_ 开头，结束后执行 acceptance-cleanup-local.sql。
用法：python3 -I local_acceptance.py [日志目录]
"""
import json
import re
import sys
import time
import uuid
from pathlib import Path

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

RUN = uuid.uuid4().hex[:6].upper()
PROVIDER = f"E2E_PROV_{RUN}"
BASE = "/notification-admin/v1"
ACCESS_KEY = "AKIAE2ESYNTHETIC" + RUN[:4]
SECRET = f"E2E-SECRET-{RUN}/abc+123"
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def status_of(fn):
    """执行调用，返回 (HTTP 状态, 响应体)；成功统一记 200。"""
    try:
        return 200, fn()
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {}


def wait_until(probe, seconds=60):
    """轮询直到 probe 返回真；超时返回假。"""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if probe():
            return True
        time.sleep(2)
    return False


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    get = lambda path: ops.get(BASE + path)
    send = lambda method, path, body: ops.send(method, BASE + path, body, key=True)
    path = f"/providers/{PROVIDER}"

    def item():
        page = get("/providers?page=1&size=100")
        return next(p for p in page["items"] if p["providerCode"] == PROVIDER)

    # AC-02 / AC-12 新路由与按钮
    check("AC-12", "Gateway 已加载新建账号路由",
          wait_until(lambda: status_of(lambda: send("POST", "/providers", {
              "providerCode": "E2E_PROV_PROBE", "providerType": "SMTP", "region": "x", "fromAddress": "x",
              "reason": "探测"}))[0] == 422))
    codes = json.dumps(ops.get("/authorization/v1/navigation"), ensure_ascii=False)
    check("AC-12", "导航含「维护账号」「密钥与测试发送」按钮",
          "OPERATIONS_NOTIFICATION_PROVIDER_EDIT" in codes and "OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL" in codes)

    # AC-01 新建
    created = send("POST", "/providers", {"providerCode": PROVIDER, "providerType": "AMAZON_SES", "region": "eu-west-1",
                                          "fromAddress": " Ops@Example.com ", "configurationSet": "", "reason": "阶段 5 联调"})
    row = item()
    check("AC-01", "新建账号版本 1、停用、发件地址规范化", created == {"code": PROVIDER, "version": 1}
          and row["status"] == "DISABLED" and row["fromAddress"] == "ops@example.com" and row["configurationSet"] is None)
    check("AC-05", f"未录密钥时配置不完整（{row['missingConfiguration']}）", row["missingConfiguration"] == ["credential"]
          and row["credentialConfigured"] is False)
    check("AC-01", "编码重复返回 400",
          status_of(lambda: send("POST", "/providers", {"providerCode": PROVIDER, "providerType": "AMAZON_SES",
                                                        "region": "eu-west-1", "fromAddress": "ops@example.com",
                                                        "reason": "重复"}))[0] == 400)
    code, body = status_of(lambda: send("PUT", path, {"region": "Middle East", "fromAddress": "ops@example.com",
                                                      "expectedVersion": 1, "reason": "非法区域"}))
    check("AC-14", f"非法区域 422 并定位字段（{[e.get('field') for e in body.get('errors', [])]}）",
          code == 422 and [e.get("field") for e in body.get("errors", [])] == ["region"])
    check("AC-05", "配置不完整不能启用（400）",
          status_of(lambda: send("PATCH", f"{path}/status", {"status": "ACTIVE", "expectedVersion": 1,
                                                             "reason": "阶段 5 联调"}))[0] == 400)

    # AC-01 编辑与冲突
    updated = send("PUT", path, {"region": "me-central-1", "fromAddress": "noreply@example.com",
                                 "configurationSet": "prod-events", "expectedVersion": 1, "reason": "阶段 5 联调"})
    row = item()
    check("AC-01", "编辑区域、发件地址与 Configuration Set 后版本 2", updated["version"] == 2
          and (row["region"], row["fromAddress"], row["configurationSet"]) == ("me-central-1", "noreply@example.com", "prod-events"))
    check("AC-01", "过期版本返回 409",
          status_of(lambda: send("PUT", path, {"region": "eu-west-1", "fromAddress": "noreply@example.com",
                                               "expectedVersion": 1, "reason": "冲突"}))[0] == 409)

    # AC-02 / AC-03 录入密钥：经真实 Vault Protect
    code, body = status_of(lambda: send("PUT", f"{path}/credential", {"accessKeyId": ACCESS_KEY, "secretAccessKey": SECRET,
                                                                    "expectedVersion": 2, "reason": "阶段 5 联调"}))
    row = item()
    text = json.dumps(body) + json.dumps(row)
    check("AC-03", "录入密钥经 Vault 保存后版本 3、配置完整", code == 200 and body["version"] == 3
          and row["credentialConfigured"] and row["missingConfiguration"] == [] and row["credentialUpdateTime"])
    check("AC-03", "响应与列表不含密钥与 Token", SECRET not in text and ACCESS_KEY not in text and "vlt_" not in text)

    # AC-05 启用
    enabled = send("PATCH", f"{path}/status", {"status": "ACTIVE", "expectedVersion": 3, "reason": "阶段 5 联调"})
    check("AC-05", "配置完整后可以启用", enabled["version"] == 4 and item()["status"] == "ACTIVE")

    # AC-08 测试发送：未启用 SES 的环境按合同返回 503；已启用时合成数据不调用 SES（只核对适配器已注册）
    if item()["adapterConfigured"]:
        check("AC-08", "本环境已注册 SES 适配器（合成数据不调用 SES，真实发送待录入真实账号后验证）", True)
    else:
        code, body = status_of(lambda: send("POST", f"{path}/test-send", {"email": "ops@example.com", "reason": "阶段 5 联调"}))
        check("AC-08", f"未启用 SES 时测试发送返回 503（{code} {body.get('code')}）", code == 503)

    # AC-10 审计
    def audited():
        events = ops.get(f"/audit/v1/events?page=1&size=50&resourceType=NOTIFICATION_PROVIDER&resourceId={PROVIDER}")
        return {"OPERATIONS_NOTIFICATION_PROVIDER_CREATE", "OPERATIONS_NOTIFICATION_PROVIDER_UPDATE",
                "OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL_REPLACE",
                "OPERATIONS_NOTIFICATION_PROVIDER_STATUS_CHANGE"} <= {e.get("actionCode") for e in events.get("items", [])}
    check("AC-10", "新建、编辑、录入密钥、启用产生审计事件", wait_until(audited, 15))

    # AC-03 日志零泄露
    if len(sys.argv) > 1:
        logs = Path(sys.argv[1])
        leaked = [f.name for f in logs.glob("*.log") if f.name in ("platform.log", "ops-bff.log", "gateway.log", "nginx-access.log")
                  and (SECRET in f.read_text(errors="ignore") or ACCESS_KEY in f.read_text(errors="ignore"))]
        check("AC-03", f"Platform / BFF / Gateway / Nginx 日志不含密钥（命中 {leaked}）", not leaked)

    failed = [r for r in results if not r[2]]
    print(f"\n共 {len(results)} 项，通过 {len(results) - len(failed)}，失败 {len(failed)}；本次账号 {PROVIDER}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
