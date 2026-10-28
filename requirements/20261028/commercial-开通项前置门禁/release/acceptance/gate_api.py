"""REQ-01 开通项前置门禁 · mor-test 阶段 7 接口验收（E2E_TARGET=test；运营入口 http://localhost:28080 → Gateway → ops-bff → commercial；
Checkout 解析经接入 mor-test-backend 的一次性探针容器私网直连 commercial，与商业关系快照发布的 stage7_relsnap.py 相同）。

子命令（按 runbook 顺序执行；结果写本目录 result-<子命令>-<UTC>.json，不含口令与令牌）：
  before    发布前（runbook ①，停 commercial 之前）：只读 SQL 记录 15 个开通项；对每个 ACTIVE CLIENT 合同的 Client 调一次 Checkout 解析
            （POST /checkout-context/v1/resolutions，X-Caller-Service: acquiring-service；只读，不保存 Assignment），记录输出的开通项集合。
  after     发布后（runbook ⑥ 之后、任何新增 / 检查前置之前）：before 中的开通项全部 ACTIVE + ENABLED；同一组请求的 Checkout 解析开通项集合与 before 相同。
  scenario  门禁场景（写操作，需 --yes）：在 --agreement-no（缺省 DEMO2-CL-103，CLIENT_PRICING v1 ACTIVE）上
            1 新增 TOKEN_STORAGE（功能项，无条件）与 APPLE_PAY（CURRENCY=USD），回执 ACTIVE + PENDING_PREREQUISITES；
            2 等补偿任务核对（≤ 90 秒）后调 POST …/prerequisite-checks：资格 SATISFIED、渠道身份 SKIPPED；TOKEN_STORAGE 合同与价格 SATISFIED → ENABLED；
              APPLE_PAY 合同与价格 SATISFIED → ENABLED，或 UNSATISFIED PRICE_NOT_COVERED（params.currencies 含 USD）→ 保持 PENDING；执行状态与逐项结果一致；
            3 暂停 TOKEN_STORAGE（原因必填）→ SUSPENDED_MANUALLY；再次暂停 → 400；恢复 → PENDING_PREREQUISITES，随后核对回到 ENABLED；
            4 合同上原有开通项的执行状态与第 1 步前相同（若被门禁改为 SUSPENDED_BY_PREREQUISITE，记为发现并停止，见 runbook 停止条件）。
            时间：新增开通项 effectiveFrom = 调用时刻（UTC），不早于合同与价格版本生效时间（经验：数据时间 ≥ 配置生效时间）。
  cleanup   验收数据收尾（写操作，需 --yes）：把 scenario 新增的两项改为 DISABLED（执行状态清空）。开通项行与 BCR 变更历史保留，无删除接口。
验收留下的数据见 runbook 第 6 节。
用法：E2E_TARGET=test python3 -I gate_api.py <before|after|scenario|cleanup> [--agreement-no DEMO2-CL-103] [--yes]
"""
import argparse
import datetime
import json
import os
import sys
import time
import uuid
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
HERE = Path(__file__).resolve().parent
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

PROBE_IMAGE = "sha256:806043a320cfce7eaaf0eed19d0ef202487ec0a6559aaa5b74f8fe3d1085f728"  # 候选 commercial 镜像（含 JDK 与 health-probe）
e2e_http.PROBE_IMAGE = PROBE_IMAGE
NEW_CODES = ("TOKEN_STORAGE", "APPLE_PAY")
checks = []


def now():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def check(name, ok, evidence=None):
    checks.append({"name": name, "pass": bool(ok), "evidence": evidence})
    print(f"{'PASS' if ok else 'FAIL'} {name}" + ("" if evidence is None else " | " + json.dumps(evidence, ensure_ascii=False)[:400]))
    return ok


def save(label, data):
    path = HERE / f"result-{label}-{now().replace(':', '')}.json"
    path.write_text(json.dumps({**data, "checks": checks, "verdict": all(c["pass"] for c in checks)}, ensure_ascii=False, indent=2) + "\n")
    print("结果：", path)
    return path


def capability_rows():
    rows = e2e_http.sql("commercial", "SELECT a.agreement_no, a.client_id, c.agreement_capability_id, c.capability_code, c.status, "
                                      "IF(COUNT(col.COLUMN_NAME) > 0, 'Y', 'N') FROM t_agreement_capability c JOIN t_agreement a USING (agreement_id) "
                                      "LEFT JOIN information_schema.COLUMNS col ON col.TABLE_SCHEMA = DATABASE() AND col.TABLE_NAME = 't_agreement_capability' "
                                      "AND col.COLUMN_NAME = 'execution_status' GROUP BY 1, 2, 3, 4, 5 ORDER BY 1, 4")
    result = [{"agreementNo": r[0], "clientId": r[1], "id": r[2], "code": r[3], "status": r[4]} for r in rows]
    if rows and rows[0][5] == "Y":
        execution = dict(e2e_http.sql("commercial", "SELECT agreement_capability_id, IFNULL(execution_status, '') FROM t_agreement_capability"))
        for row in result:
            row["executionStatus"] = execution.get(row["id"]) or None
    return result


def active_clients():
    return [r[0] for r in e2e_http.sql("commercial", "SELECT DISTINCT client_id FROM t_agreement WHERE agreement_type = 'CLIENT' AND status = 'ACTIVE' ORDER BY 1")]


def resolve(client_id):
    request = {"clientId": client_id, "orderNo": "GATES7" + uuid.uuid4().hex[:12].upper(), "consumerCountryCode": "US", "currency": "USD",
               "locale": "en", "contextAsOfTime": now()}
    status, text = e2e_http.private_call("commercial", "POST", "/checkout-context/v1/resolutions",
                                         {"X-Caller-Service": "acquiring-service", "X-Request-Id": "req-gate-s7-" + uuid.uuid4().hex[:12]}, request)
    try:
        body = json.loads(text) if text else {}
    except ValueError:
        body = {"raw": text[:300]}
    caps = sorted(c.get("capabilityCode") for c in ((body.get("clientTerms") or {}).get("capabilities") or []))
    return {"clientId": client_id, "http": status, "code": body.get("code"), "entityNo": body.get("entityNo"), "capabilities": caps}


def step_before():
    rows = capability_rows()
    check(f"发布前 {len(rows)} 个开通项均为旧值 ENABLED / SUSPENDED / DISABLED", rows and all(r["status"] in ("ENABLED", "SUSPENDED", "DISABLED") for r in rows),
          sorted({r["status"] for r in rows}))
    resolutions = [resolve(c) for c in active_clients()]
    check(f"Checkout 解析已记录 {len(resolutions)} 个 Client", resolutions, [(r["clientId"], r["http"], r["capabilities"]) for r in resolutions])
    save("before", {"at": now(), "capabilities": rows, "resolutions": resolutions})


def latest(label):
    files = sorted(HERE.glob(f"result-{label}-*.json"))
    if not files:
        raise SystemExit(f"没有 {label} 记录，先执行 {label}")
    return json.loads(files[-1].read_text())


def step_after():
    before = latest("before")
    rows = {r["id"]: r for r in capability_rows()}
    for old in before["capabilities"]:
        new = rows.get(old["id"]) or {}
        expected = {"ENABLED": ("ACTIVE", "ENABLED"), "SUSPENDED": ("ACTIVE", "SUSPENDED_MANUALLY"), "DISABLED": ("DISABLED", None)}[old["status"]]
        check(f"{old['agreementNo']} {old['code']}：{old['status']} → {expected}", (new.get("status"), new.get("executionStatus")) == expected,
              {"status": new.get("status"), "executionStatus": new.get("executionStatus")})
    for old in before["resolutions"]:
        new = resolve(old["clientId"])
        check(f"Client {old['clientId']} Checkout 解析与发布前一致（{old['http']} {old['capabilities']}）",
              (new["http"], new["code"], new["entityNo"], new["capabilities"]) == (old["http"], old["code"], old["entityNo"], old["capabilities"]), new)
    save("after", {"at": now(), "capabilities": list(rows.values())})


class Gate:
    def __init__(self, agreement_no):
        rows = e2e_http.sql("commercial", f"SELECT agreement_id FROM t_agreement WHERE agreement_no = '{agreement_no}' AND agreement_type = 'CLIENT'")
        if not rows:
            raise SystemExit(f"找不到合同 {agreement_no}")
        self.base = f"/commercial-admin/v1/agreements/{rows[0][0]}"
        secrets = e2e_http.secrets()
        self.ops = e2e_http.Operations(secrets["E2E_ADMIN_EMAIL"], secrets["E2E_ADMIN_PASSWORD"])

    def detail(self):
        return self.ops.get(self.base)["agreement"]

    def caps(self):
        return {c["capabilityCode"]: c for c in self.detail()["capabilities"]}

    def versioned(self, method, path, body):
        """写命令带最新合同版本；门禁补偿任务提交后会推进版本，409 时重读版本重试（最多 5 次）。"""
        for attempt in range(5):
            try:
                return self.ops.send(method, path, {**body, "expectedChangeVersion": self.detail()["changeVersion"]})
            except e2e_http.Failure as failure:
                if failure.status != 409 or attempt == 4:
                    raise
                time.sleep(3)

    def wait(self, code, until, timeout=90):
        for _ in range(timeout // 3):
            cap = self.caps().get(code) or {}
            if until(cap.get("executionStatus")):
                return cap
            time.sleep(3)
        return self.caps().get(code) or {}


def step_scenario(agreement_no):
    gate = Gate(agreement_no)
    original = {code: (c["status"], c.get("executionStatus")) for code, c in gate.caps().items() if code not in NEW_CODES}
    check(f"{agreement_no} 原有开通项（{sorted(original)}）全部 ACTIVE + ENABLED", original and all(v == ("ACTIVE", "ENABLED") for v in original.values()), original)
    if any(code in gate.caps() for code in NEW_CODES):
        raise SystemExit(f"{agreement_no} 已有 {NEW_CODES} 之一（上次验收未清理？），换合同或先 cleanup")
    replies = {}
    for code in NEW_CODES:
        body = {"entityNo": "AE001", "capabilityCode": code, "effectiveFrom": now(), "require3ds": False,
                "conditions": [] if code == "TOKEN_STORAGE" else [{"dimensionCode": "CURRENCY", "values": ["USD"]}]}
        replies[code] = gate.versioned("POST", gate.base + "/capabilities", body)
        cap = replies[code].get("capability") or replies[code]
        check(f"新增 {code} 回执 ACTIVE + PENDING_PREREQUISITES", (cap.get("status"), cap.get("executionStatus")) == ("ACTIVE", "PENDING_PREREQUISITES"),
              {k: cap.get(k) for k in ("agreementCapabilityId", "status", "executionStatus")})
    token = gate.wait("TOKEN_STORAGE", lambda s: s != "PENDING_PREREQUISITES")
    check("补偿任务自动核对：TOKEN_STORAGE → ENABLED（≤ 90 秒）", token.get("executionStatus") == "ENABLED", token.get("executionStatus"))
    result = gate.ops.send("POST", gate.base + "/prerequisite-checks", None)
    by_code = {c["capabilityCode"]: c for c in result["capabilities"]}
    for code in NEW_CODES:
        items = {i["code"]: i for i in by_code[code]["checks"]}
        term = items.get("AGREEMENT_TERM") or {}
        satisfied = all(i["result"] in ("SATISFIED", "SKIPPED") for i in items.values())
        check(f"{code} 资格 SATISFIED、渠道身份 SKIPPED", (items.get("QUALIFICATION") or {}).get("result") == "SATISFIED"
              and (code == "TOKEN_STORAGE" or (items.get("CHANNEL_IDENTITY") or {}).get("result") == "SKIPPED"), items)
        if code == "APPLE_PAY" and term.get("result") == "UNSATISFIED":
            check("APPLE_PAY 价格未覆盖时原因 PRICE_NOT_COVERED 且 currencies 含 USD",
                  term.get("reasonCode") == "PRICE_NOT_COVERED" and "USD" in str((term.get("params") or {}).get("currencies")), term)
        check(f"{code} 执行状态与逐项结果一致（{by_code[code]['executionStatus']}）",
              by_code[code]["executionStatus"] == ("ENABLED" if satisfied else "PENDING_PREREQUISITES"), items)
    token_id = gate.caps()["TOKEN_STORAGE"]["agreementCapabilityId"]
    suspended = gate.versioned("POST", f"{gate.base}/capabilities/{token_id}/suspension", {"reason": "mor-test 验收：暂停 / 恢复演练（REQ-01）"})
    check("暂停 TOKEN_STORAGE → SUSPENDED_MANUALLY", (suspended.get("capability") or suspended).get("executionStatus") == "SUSPENDED_MANUALLY", suspended)
    try:
        gate.versioned("POST", f"{gate.base}/capabilities/{token_id}/suspension", {"reason": "重复暂停"})
        check("重复暂停 → 400", False, "200")
    except e2e_http.Failure as failure:
        check("重复暂停 → 400", failure.status == 400, failure.status)
    resumed = gate.versioned("POST", f"{gate.base}/capabilities/{token_id}/resumption", {})
    check("恢复 → PENDING_PREREQUISITES", (resumed.get("capability") or resumed).get("executionStatus") == "PENDING_PREREQUISITES", resumed)
    token = gate.wait("TOKEN_STORAGE", lambda s: s == "ENABLED")
    check("恢复后补偿任务核对回到 ENABLED", token.get("executionStatus") == "ENABLED", token.get("executionStatus"))
    after = {code: (c["status"], c.get("executionStatus")) for code, c in gate.caps().items() if code not in NEW_CODES}
    check("合同原有开通项执行状态未被门禁改变", after == original, {"before": original, "after": after})
    save("scenario", {"at": now(), "agreementNo": agreement_no, "replies": replies, "prerequisiteCheck": result,
                      "final": {code: [c["agreementCapabilityId"], c["status"], c.get("executionStatus")] for code, c in gate.caps().items()}})


def step_cleanup(agreement_no):
    gate = Gate(agreement_no)
    for code in NEW_CODES:
        cap = gate.caps().get(code)
        if not cap or cap["status"] == "DISABLED":
            check(f"{code} 无需收尾（{(cap or {}).get('status')}）", True)
            continue
        body = {"status": "DISABLED", "effectiveFrom": cap["effectiveFrom"], "effectiveTo": cap.get("effectiveTo"),
                "conditions": cap.get("conditions") or [], "require3ds": bool(cap.get("require3ds"))}
        reply = gate.versioned("PUT", f"{gate.base}/capabilities/{cap['agreementCapabilityId']}", body)
        reply = reply.get("capability") or reply
        check(f"{code} → DISABLED、执行状态为空", (reply.get("status"), reply.get("executionStatus")) == ("DISABLED", None), reply)
    save("cleanup", {"at": now(), "agreementNo": agreement_no})


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("step", choices=("before", "after", "scenario", "cleanup"))
    parser.add_argument("--agreement-no", default="DEMO2-CL-103")
    parser.add_argument("--yes", action="store_true", help="scenario / cleanup 会写 mor-test 业务数据，须显式确认")
    args = parser.parse_args()
    if e2e_http.TARGET != "test":
        raise SystemExit("只用于 mor-test（E2E_TARGET=test）")
    if args.step in ("scenario", "cleanup") and not args.yes:
        raise SystemExit(f"{args.step} 会写 mor-test，须加 --yes（阶段 7 用户授权后由主会话执行）")
    {"before": step_before, "after": step_after}.get(args.step, lambda: None)()
    if args.step == "scenario":
        step_scenario(args.agreement_no)
    if args.step == "cleanup":
        step_cleanup(args.agreement_no)
    return 0 if all(c["pass"] for c in checks) else 1


if __name__ == "__main__":
    sys.exit(main())
