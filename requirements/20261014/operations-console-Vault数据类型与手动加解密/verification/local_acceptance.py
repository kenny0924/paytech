"""阶段 5 本机全链路验收：经 Nginx 运营入口逐项走 AC-1～AC-9、AC-11（AC-10 另做浏览器验收）。

只连接本机；不打印 Token、原值与凭据，只输出断言结果。测试数据编码以 E2E_S5_ 开头，结束后另行清理。
用法：python3 -I local_acceptance.py
"""
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

VAULT = "http://127.0.0.1:18107"
RUN = uuid.uuid4().hex[:6].upper()
STD, NO_REVEAL, SEC = f"E2E_S5_STD_{RUN}", f"E2E_S5_NOREV_{RUN}", f"E2E_S5_SEC_{RUN}"
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


def vault_business(path, caller, body, key=False):
    headers = {"Content-Type": "application/json", "X-Caller-Service": caller}
    if key:
        headers["Idempotency-Key"] = str(uuid.uuid4())
    request = urllib.request.Request(VAULT + path, json.dumps(body).encode(), headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as failure:
        return failure.code, json.loads(failure.read() or b"{}")


def wait_until(probe, seconds=30):
    """轮询直到 probe 返回真；返回耗时秒数，超时返回 None。"""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if probe():
            return round(time.monotonic() - start, 1)
        time.sleep(1)
    return None


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    base = "/vault-admin/v1"
    get = lambda path: ops.get(base + path)
    send = lambda method, path, body: ops.send(method, base + path, body, key=False)
    manual = lambda body: {"reasonCode": "OTHER", "ticketRef": f"OPS-S5-{RUN}", **body}

    # AC-1 分页查看与当前版本
    listing = get("/data-types")
    start_version = listing["currentPolicyVersion"]
    check("AC-1", "数据类型列表含当前策略版本", start_version >= 1 and len(listing["items"]) >= 2)
    rules = get("/access-rules?page=1&size=10")
    check("AC-1", "访问规则分页（默认页大小 10）", rules["total"] >= 6 and len(rules["items"]) <= 10)
    check("AC-1", "页大小 201 被拒绝", status_of(lambda: get("/access-rules?page=1&size=201"))[0] == 400)

    # AC-4 发布前校验
    bad = {"dataTypeCode": "bad-code", "tokenMode": "REFERENCE", "dataClass": "STANDARD", "description": "x",
           "manualProtectEnabled": True, "manualRevealEnabled": True, "reason": "联调"}
    check("AC-4", "编码格式非法被拒（422）", status_of(lambda: send("POST", "/data-types", bad))[0] == 422)
    rule_missing_type = {"callerService": "platform-support-service", "action": "REVEAL", "dataTypeCode": f"E2E_S5_NONE_{RUN}",
                         "purposeCode": "E2E_S5_PURPOSE", "reason": "联调"}
    code, body = status_of(lambda: send("POST", "/access-rules", rule_missing_type))
    check("AC-4", f"规则引用不存在的数据类型被拒（{code} {body.get('code')}）", code in (400, 404, 422))
    reserved = {**rule_missing_type, "dataTypeCode": "EMAIL_ADDRESS_V1", "purposeCode": "OPERATIONS_MANUAL"}
    code, body = status_of(lambda: send("POST", "/access-rules", reserved))
    check("AC-4", f"保留用途 OPERATIONS_MANUAL 不能用于服务规则（{code} {body.get('code')}）", code in (400, 422))
    check("AC-4", "校验失败不产生新版本", get("/data-types")["currentPolicyVersion"] == start_version)

    # AC-2 新增数据类型，生成新版本并可审计
    def create_type(code_, data_class, reveal):
        return send("POST", "/data-types", {"dataTypeCode": code_, "tokenMode": "REFERENCE", "dataClass": data_class,
                                            "description": "阶段 5 联调", "manualProtectEnabled": True,
                                            "manualRevealEnabled": reveal, "reason": "阶段 5 联调"})
    created = create_type(STD, "STANDARD", True)
    after_create = get("/data-types")["currentPolicyVersion"]
    check("AC-2", "新增数据类型后策略版本递增", after_create == start_version + 1)
    dup = status_of(lambda: create_type(STD, "STANDARD", True))
    check("AC-2", f"重复编码返回 409（{dup[1].get('code')}）", dup[0] == 409)
    change = get(f"/policy-changes/{after_create}")
    check("AC-2", "变更记录含操作人、理由、变更后快照且变更前为空",
          change["actorId"] and change["reason"] == "阶段 5 联调" and change["targetKey"] == STD
          and change.get("afterSnapshot") and not change.get("beforeSnapshot"))
    stale = status_of(lambda: send("PUT", f"/data-types/{STD}", {"expectedVersion": created["dataType"]["version"] + 5, "dataClass": "STANDARD",
                                                                 "description": "x", "manualProtectEnabled": True,
                                                                 "manualRevealEnabled": True, "status": "ACTIVE", "reason": "过期版本"}))
    check("AC-2", f"过期版本修改返回 409（{stale[1].get('code')}）", stale[0] == 409)
    create_type(NO_REVEAL, "STANDARD", False)
    create_type(SEC, "SECRET", True)

    # AC-3 热加载：保存后无需重启，人工加密在 30 秒内可用
    submission = str(uuid.uuid4())
    protect_body = manual({"submissionId": submission, "dataTypeCode": STD, "value": f"s5-standard-{RUN}"})
    holder = {}
    def protect_ok():
        code_, body_ = status_of(lambda: send("POST", "/manual/protect", protect_body))
        holder["token"] = body_.get("token") if code_ == 200 else None
        return code_ == 200
    elapsed = wait_until(protect_ok)
    check("AC-3", f"新类型 {elapsed} 秒内生效（≤30 秒）", elapsed is not None)

    # AC-8 同一提交重试返回同一 Token；AC-5 解密得到原值
    token = holder["token"]
    again = send("POST", "/manual/protect", protect_body)
    check("AC-8", "同一 submissionId 重试返回同一 Token", again["token"] == token and len(token) == 43)
    revealed = send("POST", "/manual/reveal", manual({"token": token}))
    check("AC-5", "人工解密返回原值与数据类型", revealed["value"] == f"s5-standard-{RUN}" and revealed["dataTypeCode"] == STD)

    # AC-6 错误场景
    code, body = status_of(lambda: send("POST", "/manual/reveal", manual({"token": "A" * 43})))
    check("AC-6", f"Token 不存在返回 404（{body.get('code')}）且不含原值", code == 404 and "value" not in body)
    nr = send("POST", "/manual/protect", manual({"submissionId": str(uuid.uuid4()), "dataTypeCode": NO_REVEAL,
                                                 "value": f"s5-norev-{RUN}"}))
    code, body = status_of(lambda: send("POST", "/manual/reveal", manual({"token": nr["token"]})))
    check("AC-6", f"类型未开启人工解密返回 403（{body.get('code')}）", code == 403 and "value" not in body)
    code, body = status_of(lambda: send("POST", "/manual/protect", manual({"submissionId": str(uuid.uuid4()),
                                                                            "dataTypeCode": "EMAIL_ADDRESS_V1", "value": "x"})))
    check("AC-6", f"类型未开启人工加密返回 403（{body.get('code')}）", code == 403)
    code, _ = status_of(lambda: send("POST", "/manual/reveal", {"token": token, "reasonCode": "OTHER", "ticketRef": "bad ticket!"}))
    check("AC-6", "工单号格式非法被拒（422）", code == 422)

    # AC-11 密钥类：普通路由拒绝，密钥路由放行
    secret_value = f"s5-secret-{RUN}"
    sec_body = manual({"submissionId": str(uuid.uuid4()), "dataTypeCode": SEC, "value": secret_value})
    code, body = status_of(lambda: send("POST", "/manual/protect", sec_body))
    check("AC-11", f"普通路由加密密钥类返回 403（{body.get('code')}）", code == 403)
    sec_token = send("POST", "/manual/secret-protect", sec_body)["token"]
    code, body = status_of(lambda: send("POST", "/manual/reveal", manual({"token": sec_token})))
    check("AC-11", f"普通路由解密密钥类返回 403 且不含原值（{body.get('code')}）", code == 403 and "value" not in body)
    check("AC-11", "密钥路由解密得到原值", send("POST", "/manual/secret-reveal", manual({"token": sec_token}))["value"] == secret_value)

    # AC-8 业务系统按自身规则 Reveal 人工加密的 Token（规则保存后热加载）
    rule = send("POST", "/access-rules", {"callerService": "platform-support-service", "action": "REVEAL", "dataTypeCode": SEC,
                                          "purposeCode": "E2E_S5_PURPOSE", "reason": "阶段 5 联调"})
    business = lambda: vault_business("/vault/v1/records/reveal", "platform-support-service",
                                      {"token": sec_token, "purpose": "E2E_S5_PURPOSE"})
    elapsed = wait_until(lambda: business()[0] == 200)
    check("AC-8", f"业务服务按新规则 Reveal 人工加密的 Token（{elapsed} 秒生效）",
          elapsed is not None and business()[1].get("value") == secret_value)
    send("PUT", f"/access-rules/{rule['accessRule']['accessRuleId']}", {"expectedVersion": rule["accessRule"]["version"], "status": "DISABLED", "reason": "联调结束停用"})
    elapsed = wait_until(lambda: business()[0] == 403)
    check("AC-3", f"停用规则后 {elapsed} 秒内业务 Reveal 被拒", elapsed is not None)

    # AC-9 既有业务 Protect/Reveal 合同不变
    code, protected = vault_business("/vault/v1/records/protect", "client-contract-service",
                                     {"dataType": "EMAIL_ADDRESS_V1", "tokenMode": "STABLE", "value": f"s5-{RUN.lower()}@example.test",
                                      "purpose": "CLIENT_ONBOARDING_CONTACT"}, key=True)
    code2, shown = vault_business("/vault/v1/records/reveal", "client-contract-service",
                                  {"token": protected.get("token", ""), "purpose": "CLIENT_ONBOARDING_CONTACT"})
    check("AC-9", "既有 CCT 邮箱 Protect/Reveal 正常", code == 200 and code2 == 200
          and shown.get("value") == f"s5-{RUN.lower()}@example.test")
    code, _ = vault_business("/vault/v1/records/reveal", "commerce-service", {"token": protected.get("token", ""), "purpose": "CLIENT_ONBOARDING_CONTACT"})
    check("AC-9", "无规则的调用方仍被拒（403）", code == 403)

    # AC-7 人工操作记录：操作人、理由码、工单号、结果；不含 Token 与原值
    events = get(f"/manual-events?page=1&size=50&dataTypeCode={SEC}")
    outcomes = sorted(e["outcome"] for e in events["items"])
    raw = json.dumps(events)
    check("AC-7", f"密钥类人工操作记录 {len(events['items'])} 条，结果 {outcomes}",
          len(events["items"]) >= 4 and all(e["actorId"] and e["reasonCode"] == "OTHER" and e["ticketRef"] == f"OPS-S5-{RUN}"
                                            for e in events["items"]))
    check("AC-7", "记录中不含 Token 与原值", sec_token not in raw and secret_value not in raw and "token" not in raw.lower())

    print(f"\nRUN={RUN} 起始版本={start_version} 结束版本={get('/data-types')['currentPolicyVersion']}")
    failed = [r for r in results if not r[2]]
    print(f"合计 {len(results)} 项，失败 {len(failed)} 项")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
