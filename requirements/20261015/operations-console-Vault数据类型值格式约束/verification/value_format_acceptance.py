"""值格式约束验收：经 Nginx 运营入口与 Vault 服务接口逐项核对 AC-1～AC-8。

  python3 -I value_format_acceptance.py a   用临时类型（E2E_VF_ 开头）验收维护规则与写入校验，结束后另行清理
  python3 -I value_format_acceptance.py b   按需求第 7 节设置现有类型的值格式（记入策略变更），再回归现有写入方

默认目标本机；E2E_TARGET=test 时运营入口走 mor-test，服务调用经接入 mor-test-backend 的临时 curl 探针容器发出。
只输出断言结果，不打印 Token、原值、样例与凭据。
"""
import json
import os
import subprocess
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
BASE = "/vault-admin/v1"
RUN = uuid.uuid4().hex[:6].upper()
EMAIL_PATTERN = r"[^\s@\p{Lu}]{1,64}@[^\s@.\p{Lu}]+(?:\.[^\s@.\p{Lu}]+)+"
PHONE_PATTERN = r"\+[1-9][0-9]{7,14}"
TARGETS = {
    "EMAIL_ADDRESS_V1": ("REGEX", EMAIL_PATTERN, ["sample@example.test"]),
    "PHONE_E164_V1": ("REGEX", PHONE_PATTERN, ["+971500000001"]),
    "CHANNEL_ACCOUNT_CONFIG_V1": ("JSON", None, []),
    "NOTIFICATION_PROVIDER_CREDENTIAL_V1": ("JSON", None, []),
}
TEST_PROBE = ["docker", "run", "--rm", "-i", "--network", "mor-test-backend",
              "nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c"]
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def status_of(fn):
    """执行运营入口调用，返回 (HTTP 状态, 响应体)。"""
    try:
        return 200, fn()
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {}


def vault(path, caller, body, key=True):
    """以业务服务身份直连 Vault（test 环境经探针容器），返回 (状态, 响应体)。"""
    headers = {"Content-Type": "application/json", "X-Caller-Service": caller}
    if key:
        headers["Idempotency-Key"] = "e2e-vf-" + uuid.uuid4().hex
    if os.environ.get("E2E_TARGET") == "test":
        args = TEST_PROBE + ["curl", "--silent", "--max-time", "15", "--write-out", "\n%{http_code}", "--request", "POST"]
        for name, value in headers.items():
            args += ["--header", f"{name}: {value}"]
        out = subprocess.run(args + ["--data-binary", "@-", "http://vault-service:18107" + path], input=json.dumps(body),
                             capture_output=True, text=True).stdout
        text, _, status = out.rpartition("\n")
        return int(status or 0), json.loads(text or "{}")
    request = urllib.request.Request(VAULT + path, json.dumps(body).encode(), headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read())
    except urllib.error.HTTPError as failure:
        return failure.code, json.loads(failure.read() or b"{}")


def protect(caller, data_type, mode, value, purpose):
    return vault("/vault/v1/records/protect", caller, {"dataType": data_type, "tokenMode": mode, "value": value, "purpose": purpose})


def reveal(caller, token, purpose):
    return vault("/vault/v1/records/reveal", caller, {"token": token, "purpose": purpose}, key=False)


def wait_until(condition, seconds=30):
    """轮询到条件成立，返回耗时秒数；超时返回 None。"""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if condition():
            return round(time.monotonic() - start, 1)
        time.sleep(0.5)
    return None


def manual_protect(ops, code, value, secret=False):
    path = BASE + ("/manual/secret-protect" if secret else "/manual/protect")
    return status_of(lambda: ops.send("POST", path, {"submissionId": str(uuid.uuid4()), "dataTypeCode": code, "value": value,
                                                     "reasonCode": "OTHER", "ticketRef": "OPS-VF-" + RUN}, key=False))


def data_type(ops, code):
    return next((t for t in ops.get(BASE + "/data-types")["items"] if t["dataTypeCode"] == code), None)


def update_body(current, reason, **fields):
    body = {"expectedVersion": current["version"], "dataClass": current["dataClass"], "description": current["description"],
            "manualProtectEnabled": current["manualProtectEnabled"], "manualRevealEnabled": current["manualRevealEnabled"],
            "status": current["status"], "reason": reason}
    body.update({k: v for k, v in fields.items() if v is not None})
    return body


def phase_a(ops):
    """临时类型：维护规则（AC-1～3、5）与写入校验（AC-4、8）。"""
    regex, json_type, text = f"E2E_VF_PHONE_{RUN}", f"E2E_VF_CONFIG_{RUN}", f"E2E_VF_TEXT_{RUN}"
    caller, purpose = "e2e-vf-service", "E2E_VF"
    version0 = ops.get(BASE + "/data-types")["currentPolicyVersion"]
    common = {"dataClass": "STANDARD", "manualProtectEnabled": True, "manualRevealEnabled": False, "reason": "值格式验收"}

    bad = [("正则无法编译", {"valueFormat": "REGEX", "valuePattern": "(", "sampleValues": ["("]}, "VALUE_PATTERN_INVALID"),
           ("REGEX 缺样例", {"valueFormat": "REGEX", "valuePattern": PHONE_PATTERN}, "SAMPLE_VALUE_INVALID"),
           ("样例不符合正则", {"valueFormat": "REGEX", "valuePattern": PHONE_PATTERN, "sampleValues": ["+971500000001", "abc"]}, "SAMPLE_VALUE_INVALID"),
           ("JSON 带正则", {"valueFormat": "JSON", "valuePattern": "a"}, "VALUE_PATTERN_INVALID")]
    for name, fields, code in bad:
        status, body = status_of(lambda f=fields: ops.send("POST", BASE + "/data-types", {
            "dataTypeCode": regex, "tokenMode": "STABLE", "description": "值格式验收", **common, **f}, key=False))
        check("AC-2", f"新建被拒：{name} → 400 {code}", status == 400 and body.get("code") == code)
    status, body = status_of(lambda: ops.send("POST", BASE + "/data-types", {"dataTypeCode": regex, "tokenMode": "STABLE", "description": "值格式验收",
                                                                        **common, "valueFormat": "REGEX", "valuePattern": "a" * 257, "sampleValues": ["a"]}, key=False))
    check("AC-2", "新建被拒：正则超过 256 字符 → BFF 形状校验 422，不调用 Vault", status == 422)
    check("AC-2", "被拒的保存不产生策略版本", ops.get(BASE + "/data-types")["currentPolicyVersion"] == version0)

    created = ops.send("POST", BASE + "/data-types", {"dataTypeCode": regex, "tokenMode": "STABLE", "description": "值格式验收",
                                                      **common, "valueFormat": "REGEX", "valuePattern": PHONE_PATTERN,
                                                      "sampleValues": ["+971500000077"]}, key=False)
    ops.send("POST", BASE + "/data-types", {"dataTypeCode": json_type, "tokenMode": "REFERENCE", "description": "值格式验收",
                                           **common, "valueFormat": "JSON"}, key=False)
    plain = ops.send("POST", BASE + "/data-types", {"dataTypeCode": text, "tokenMode": "REFERENCE", "description": "值格式验收", **common}, key=False)
    listed = data_type(ops, regex)
    check("AC-1", "列表返回值格式 REGEX 与正则", listed and listed["valueFormat"] == "REGEX" and listed["valuePattern"] == PHONE_PATTERN)
    check("AC-1", "未指定值格式时为 TEXT 且无正则", plain["dataType"]["valueFormat"] == "TEXT" and plain["dataType"].get("valuePattern") is None)
    detail = ops.get(BASE + f"/policy-changes/{created['policyVersion']}")
    snapshot = json.dumps(detail)
    check("AC-3", "变更快照含值格式与正则、不含样例", '"valueFormat": "REGEX"' in json.dumps(detail.get("afterSnapshot")) and "+971500000077" not in snapshot)
    ops.send("POST", BASE + "/access-rules", {"callerService": caller, "action": "PROTECT", "dataTypeCode": regex,
                                             "purposeCode": purpose, "reason": "值格式验收"}, key=False)

    took = wait_until(lambda: protect(caller, regex, "STABLE", "+8613800138000", purpose)[0] == 200)
    check("AC-5", f"新类型与规则在刷新周期内生效（{took} 秒）", took is not None)
    status, body = protect(caller, regex, "STABLE", "13800138000", purpose)
    check("AC-4", "服务 Protect 不符合正则 → 400 VALUE_PATTERN_MISMATCH，响应不含原值",
          status == 400 and body.get("code") == "VALUE_PATTERN_MISMATCH" and "13800138000" not in json.dumps(body))
    took = wait_until(lambda: manual_protect(ops, regex, "+8613800138001")[0] == 200)
    status, body = manual_protect(ops, regex, "8613800138001")
    check("AC-4", "人工加密不符合正则 → 400 VALUE_PATTERN_MISMATCH", took is not None and status == 400 and body.get("code") == "VALUE_PATTERN_MISMATCH")

    wait_until(lambda: manual_protect(ops, json_type, '{"k":"v"}')[0] == 200)
    for name, value in [("数组根", "[1]"), ("重复键", '{"k":1,"k":2}'), ("末尾多余内容", '{"k":1} x'), ("非法 JSON", "{k:1}")]:
        status, body = manual_protect(ops, json_type, value)
        check("AC-8", f"JSON 类型拒绝{name} → 400 VALUE_NOT_JSON_OBJECT", status == 400 and body.get("code") == "VALUE_NOT_JSON_OBJECT")
    check("AC-8", "TEXT 类型不额外限制", wait_until(lambda: manual_protect(ops, text, "{not json")[0] == 200) is not None)

    current = data_type(ops, regex)
    status, body = status_of(lambda: ops.send("PUT", BASE + f"/data-types/{regex}", update_body(
        current, "值格式验收", valueFormat="REGEX", valuePattern=r"\+971[0-9]{9}"), key=False))
    check("AC-2", "修改正则不带样例 → 400 SAMPLE_VALUE_INVALID", status == 400 and body.get("code") == "SAMPLE_VALUE_INVALID")
    kept = ops.send("PUT", BASE + f"/data-types/{regex}", update_body(current, "值格式验收：不带值格式"), key=False)["dataType"]
    check("AC-1", "修改不带值格式时保持 REGEX 与原正则", kept["valueFormat"] == "REGEX" and kept["valuePattern"] == PHONE_PATTERN)
    ops.send("PUT", BASE + f"/data-types/{regex}", update_body(kept, "值格式验收：改严", valueFormat="REGEX",
                                                             valuePattern=r"\+971[0-9]{9}", sampleValues=["+971500000077"]), key=False)
    took = wait_until(lambda: protect(caller, regex, "STABLE", "+8613800138000", purpose)[0] == 400)
    check("AC-5", f"改严正则在刷新周期内生效（{took} 秒）", took is not None)
    print(f"A 段完成：临时类型前缀 E2E_VF_*_{RUN}，起始策略版本 {version0}")


def phase_b(ops):
    """设置现有类型的值格式（AC-6、7）：前后 STABLE Token 不变、现有写入方回归。"""
    email, purpose = "vf-regression@example.test", "PLATFORM_NOTIFICATION_EMAIL_V1"
    status, before = protect("platform-support-service", "EMAIL_ADDRESS_V1", "STABLE", email, purpose)
    check("AC-6", "设置前：通知邮箱加密成功", status == 200)
    for code, (fmt, pattern, samples) in TARGETS.items():
        current = data_type(ops, code)
        if current is None:
            check("AC-7", f"{code} 不存在", False)
            continue
        if current.get("valueFormat") == fmt and current.get("valuePattern") == pattern:
            print(f"  {code} 已是 {fmt}，跳过")
            continue
        result = ops.send("PUT", BASE + f"/data-types/{code}", update_body(
            current, "值格式约束：按需求第 7 节设置现有类型", valueFormat=fmt, valuePattern=pattern, sampleValues=samples or None), key=False)
        print(f"  {code} → {fmt}，策略版本 {result['policyVersion']}")
    took = wait_until(lambda: protect("client-contract-service", "EMAIL_ADDRESS_V1", "STABLE", "Upper@Example.test", "CLIENT_ONBOARDING_CONTACT")[0] == 400)
    check("AC-5", f"现有类型的格式在刷新周期内生效（{took} 秒）", took is not None)

    status, after = protect("platform-support-service", "EMAIL_ADDRESS_V1", "STABLE", email, purpose)
    check("AC-6", "设置后：同一规范邮箱仍得到原 STABLE Token", status == 200 and after.get("token") == before.get("token"))
    status, revealed = reveal("platform-support-service", before.get("token", ""), purpose)
    check("AC-6", "设置前生成的 Token 照常解密", status == 200 and revealed.get("value") == email)

    for name, args in [("CCT 联系人邮箱", ("client-contract-service", "EMAIL_ADDRESS_V1", "STABLE", "vf-contact@example.test", "CLIENT_ONBOARDING_CONTACT")),
                       ("CCT 联系人手机", ("client-contract-service", "PHONE_E164_V1", "STABLE", "+971500000123", "CLIENT_ONBOARDING_CONTACT")),
                       ("CCT Unicode 域名邮箱", ("client-contract-service", "EMAIL_ADDRESS_V1", "STABLE", "vf@例子.中国", "CLIENT_ONBOARDING_CONTACT"))]:
        check("AC-7", f"{name}：规范输入加密成功", protect(*args)[0] == 200)
    status, body = protect("client-contract-service", "PHONE_E164_V1", "STABLE", "0500000123", "CLIENT_ONBOARDING_CONTACT")
    check("AC-7", "CCT 手机缺 + 号 → 400 VALUE_PATTERN_MISMATCH", status == 400 and body.get("code") == "VALUE_PATTERN_MISMATCH")

    s = e2e_http.secrets()
    relogin = status_of(lambda: e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]).get(BASE + "/data-types"))
    check("AC-7", "Platform 运营登录（登录邮箱经 Vault 加密）成功", relogin[0] == 200)

    config = json.dumps({"channelCode": "VERIFY", "channelAccountId": "0", "entityNo": "00000", "note": "value-format"})
    status, body = manual_protect(ops, "CHANNEL_ACCOUNT_CONFIG_V1", config, secret=True)
    check("AC-7", "渠道账号配置人工加密（JSON）成功", status == 200)
    status, revealed = reveal("platform-support-service", body.get("token", ""), "CHANNEL_EXECUTION")
    check("AC-7", "Platform 读取渠道账号配置成功", status == 200 and revealed.get("value") == config)
    status, body = manual_protect(ops, "CHANNEL_ACCOUNT_CONFIG_V1", "apiKey=abc", secret=True)
    check("AC-7", "渠道账号配置非 JSON → 400 VALUE_NOT_JSON_OBJECT", status == 400 and body.get("code") == "VALUE_NOT_JSON_OBJECT")
    credential = json.dumps({"accessKeyId": "AKIAVALUEFORMAT", "secretAccessKey": "synthetic"})
    status, body = manual_protect(ops, "NOTIFICATION_PROVIDER_CREDENTIAL_V1", credential, secret=True)
    status2, revealed = reveal("platform-support-service", body.get("token", ""), "NOTIFICATION_DELIVERY")
    check("AC-7", "SES 凭据人工加密并由 Platform 读取成功", status == 200 and status2 == 200 and revealed.get("value") == credential)


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    {"a": phase_a, "b": phase_b}[sys.argv[1]](ops)
    print(f"合计 {len(results)} 项，失败 {results.count(False)} 项")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
