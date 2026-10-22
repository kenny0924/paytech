"""任务卡 P2-stage5 A / D：第二段本机端到端回归（第一段版本基线 / 第二段版本各跑一次，结果逐项对照）。

自 stage5_e2e.py 与 stage5_card_pay.py 复制改写（原文件不改）。本机环境沿用 local-e2e（*_v2e2e 库、Redis db 3、vhost /mor-e2e、
合成 PayerMax 127.0.0.1:18190），HTTP / 数据库辅助复用 20261017 local-e2e/e2e_http.py。渠道数据沿用第一段 A1 登记的 PAYERMAX_MOCK
（local-environment/e2e/runtime/ccu-stage5-channel.json，只读）。

用法：python3 -I stage5p2_e2e.py run <label> <out.json>      label 为 baseline / after
  链路：候选查询 → /channel-connector/v1/operations：REFUND、PAY（合成票据）、PAY（真实读卡：card 建工具 + 签发票据）、两个 NOT_SENT；
  card 调用方探针：合成票据三种调用方；after 另用真实票据以 platform-support-service 直接读卡（应被拒绝且票据仍未使用）；
  沙箱：新建验证账号与用例、运行一次；
  管理读取快照：外部 API 管理全部 GET（经 Nginx 18444 → Gateway → ops-bff）与渠道管理 GET。
    第一段版本：接入方编码取自 GET /integrations；第二段版本：接入方列表与详情已删除（记录状态码），编码取自支付渠道列表，
    其余快照名称保持一致以便逐项对照。
输出不含 Token、密钥、口令或卡号：写盘前用合成密钥、管理员口令与账号 Vault Token 逐值扫描，命中即中止；appId / merchantNo 替换为占位符。
"""
import json
import random
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import stage5_e2e as s5  # noqa: E402
from stage5_e2e import h, ops, login, actor_of, normalize, paged, journal, log_offset, log_since, card_log_facts  # noqa: E402

CODE = s5.CODE
ENTITY_NO = s5.ENTITY_NO
CHANNEL_ADMIN = s5.CHANNEL_ADMIN
EXTERNAL_ADMIN = s5.EXTERNAL_ADMIN
REASON = "统一对外渠道出口第二段阶段 5 本机端到端"
CARD = dict(s5.TEST_CARD)


def phase(token):
    """第一段版本仍有接入方列表；第二段版本已删除（Gateway 停用路由 / BFF 下线）。"""
    status, _ = ops(token, "GET", EXTERNAL_ADMIN + "/integrations?page=1&size=10")
    return 1 if status == 200 else 2


# ---------- 链路 ----------

def card_call(path, caller, body):
    status, text = h.private_call("card", "POST", "/card/v1" + path, headers={"X-Caller-Service": caller}, body=body)
    return status, (json.loads(text) if text.startswith("{") else {"raw": text[:200]})


def digits(n):
    return str(random.randrange(10 ** (n - 1), 10 ** n))


def issue_ticket():
    """card 建手输卡工具并签发 CHANNEL_EXECUTION 读卡票据（调用方 payment-service）。返回 (结果, 票据, attemptId)。"""
    started = datetime.now(timezone.utc)
    status, instrument = card_call("/instruments", "payment-service", {
        "clientId": digits(15), "sourceSubmissionId": digits(16), "card": CARD, "consentToSave": False,
        "expiresAt": (started + timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%SZ")})
    out = {"instrument": {"httpStatus": status, "maskedPan": instrument.get("maskedPan"), "code": instrument.get("code")}}
    if status != 200:
        return out, None, None, None
    attempt_id, action_id = digits(15) + "1", digits(16)  # 末位 1：合成 PayerMax 同步 SUCCESS
    status, issued = card_call("/material-tickets", "payment-service", {
        "instrumentRef": instrument["instrumentRef"], "paymentAttemptId": attempt_id, "paymentActionId": action_id,
        "purpose": "CHANNEL_EXECUTION", "ttlSeconds": 90})
    out["ticket"] = {"httpStatus": status, "issued": bool(issued.get("ticket")), "code": issued.get("code")}
    return out, issued.get("ticket"), attempt_id, instrument["instrumentRef"]


def ticket_used(instrument_ref):
    rows = h.sql("card", "SELECT used_at IS NOT NULL FROM t_card_material_ticket WHERE instrument_ref = '"
                 + str(int(instrument_ref)) + "'")
    return [r[0] for r in rows]


def real_card_pay(state, platform_probe):
    """真实读卡 PAY；platform_probe 为真时先以 platform-support-service 用同一票据直接读卡（第二段应 404 且票据未使用）。"""
    out, ticket, attempt_id, instrument_ref = issue_ticket()
    if not ticket:
        return out
    if platform_probe:
        status, body = card_call("/channel-card-materials/reveal", "platform-support-service", {"ticket": ticket})
        out["platformCallerReveal"] = {"httpStatus": status, "code": body.get("code"),
                                       "returnedCardFields": bool({"pan", "cvv", "cardNumber"} & set(body)),
                                       "ticketUsedAfter": ticket_used(instrument_ref)}
    marks = {n: log_offset(n) for n in ("card", "channel-connector")}
    pay = s5.execute(state, "PAY", "NEW_PAYMENT", "10.00", {
        "card": {"token": ticket},
        "payment": {"attemptId": attempt_id, "amount": {"value": "10.00", "currency": "USD"}, "country": "US",
                    "returnUrl": "https://127.0.0.1:18446/return", "payerReference": "stage5p2-card-pay",
                    "clientIp": "203.0.113.10", "userAgent": "Mozilla/5.0 (stage5p2 card pay)"}})
    out["pay"] = pay
    out["ticketUsedAfterPay"] = ticket_used(instrument_ref)
    status, again = card_call("/channel-card-materials/reveal", "channel-connector-service", {"ticket": ticket})
    out["reuseTicket"] = {"httpStatus": status, "code": again.get("code"),
                          "returnedCardFields": bool({"pan", "cvv", "cardNumber"} & set(again))}
    # 读卡调用方：card 访问日志读卡请求的 requestId 与 channel-connector 本次 PAY 的 requestId 相同
    card_text, cc_text = log_since("card", marks["card"]), log_since("channel-connector", marks["channel-connector"])
    import re
    card_ids = set(re.findall(r'requestId="?([0-9a-f-]{36})', card_text)) | set(re.findall(r'\[([0-9a-f-]{36})\]', card_text))
    cc_ids = set(re.findall(r'requestId="?([0-9a-f-]{36})', cc_text)) | set(re.findall(r'\[([0-9a-f-]{36})\]', cc_text))
    out["readerCorrelation"] = {"cardRequestIds": len(card_ids), "sharedWithChannelConnector": len(card_ids & cc_ids)}
    pan_hit = {n: CARD["pan"] in t for n, t in (("card", card_text), ("channel-connector", cc_text))}
    out["panInLogs"] = pan_hit
    return out


def chain(state, version):
    out = s5.chain(state)
    out["payWithRealCard"] = real_card_pay(state, platform_probe=(version == 2))
    return out


# ---------- 沙箱 ----------

def sandbox(token, state, label):
    result = s5.sandbox(token, state, "P2" + label)
    return result


# ---------- 管理读取快照 ----------

def snapshot(token, version, state):
    snap, calls, removed = {}, [], {}

    def get(name, path):
        status, body = ops(token, "GET", path)
        calls.append([name, status])
        snap[name] = {"status": status, "body": normalize(body)}
        return status, body

    def get_paged(name, path):
        status, body, items = paged(token, path)
        calls.append([name, status])
        snap[name] = {"status": status, "total": body.get("total") if status == 200 else None,
                      "items": normalize(items) if status == 200 else normalize(body)}
        return items

    get("editor-capabilities", EXTERNAL_ADMIN + "/editor-capabilities")
    for item in get_paged("standard-apis", EXTERNAL_ADMIN + "/standard-apis"):
        get(f"standard-apis/{item['standardApiCode']}", f"{EXTERNAL_ADMIN}/standard-apis/{item['standardApiCode']}")
    standard_codes = [i["standardApiCode"] for i in snap["standard-apis"].get("items") or []]
    if version == 1:
        codes = [i["integrationCode"] for i in get_paged("integrations", EXTERNAL_ADMIN + "/integrations")]
        for code in codes:
            get(f"integrations/{code}", f"{EXTERNAL_ADMIN}/integrations/{code}")
    else:
        for name, path in (("integrations", EXTERNAL_ADMIN + "/integrations?page=1&size=50"),
                           (f"integrations/{CODE}", f"{EXTERNAL_ADMIN}/integrations/{CODE}")):
            status, body = ops(token, "GET", path)
            removed[name] = {"status": status, "code": body.get("code") if isinstance(body, dict) else None}
        _, _, channels = paged(token, CHANNEL_ADMIN + "/channels?channelType=PAYMENT")
        codes = sorted(c["channelCode"] for c in channels if c["channelCode"] == CODE or c.get("endpointCount"))
    for code in codes:
        base = f"{EXTERNAL_ADMIN}/integrations/{code}"
        for item in get_paged(f"integrations/{code}/apis", base + "/apis"):
            get(f"integrations/{code}/apis/{item['apiCode']}", f"{base}/apis/{item['apiCode']}")
        for item in get_paged(f"integrations/{code}/scripts", base + "/scripts"):
            get(f"integrations/{code}/scripts/{item['scriptCode']}", f"{base}/scripts/{item['scriptCode']}")
        status, impls = get(f"integrations/{code}/implementations", base + "/implementations")
        impl_items = impls if isinstance(impls, list) else (impls.get("items") or impls.get("implementations") or [])
        for item in impl_items:
            get(f"integrations/{code}/implementations/{item['standardApiCode']}", f"{base}/implementations/{item['standardApiCode']}")
        for std in standard_codes:
            get(f"integrations/{code}/flow-editor-context?{std}", f"{base}/flow-editor-context?standardApiCode={std}")
        status, accounts = get(f"integrations/{code}/verify-accounts", base + "/verify-accounts")
        account_items = accounts if isinstance(accounts, list) else (accounts.get("items") or accounts.get("accounts") or [])
        for item in account_items:
            get(f"integrations/{code}/verify-accounts/{item['accountCode']}", f"{base}/verify-accounts/{item['accountCode']}")
        for item in get_paged(f"integrations/{code}/verify-cases", base + "/verify-cases"):
            get(f"integrations/{code}/verify-cases/{item['caseCode']}", f"{base}/verify-cases/{item['caseCode']}")
        for item in get_paged(f"integrations/{code}/verify-runs", base + "/verify-runs"):
            get(f"integrations/{code}/verify-runs/{item['runCode']}", f"{base}/verify-runs/{item['runCode']}")

    # 渠道管理 GET（第二段新增字段属于允许差异，对照脚本逐项归类）
    cid, aid, mid, cap = state["channelId"], state["channelAccountId"], state["midId"], state["midCapabilityId"]
    channel_calls = {}

    def cget(name, path):
        status, body = ops(token, "GET", path)
        channel_calls[name] = {"status": status, "body": normalize(body)}

    cget("channels", CHANNEL_ADMIN + "/channels?page=1&size=50")
    cget("channels/{id}", f"{CHANNEL_ADMIN}/channels/{cid}")
    cget("channel-accounts", f"{CHANNEL_ADMIN}/channel-accounts?channelId={cid}&page=1&size=50")
    cget("channel-accounts/{id}", f"{CHANNEL_ADMIN}/channel-accounts/{aid}")
    cget("mids", f"{CHANNEL_ADMIN}/mids?channelId={cid}&page=1&size=50")
    cget("mids/{id}", f"{CHANNEL_ADMIN}/mids/{mid}")
    cget("mid-capabilities/definitions", CHANNEL_ADMIN + "/mid-capabilities/definitions")
    for target, tid in (("CHANNEL", cid), ("CHANNEL_ACCOUNT", aid), ("CHANNEL_MID", mid), ("MID_CAPABILITY", cap)):
        cget(f"config-changes/{target}", f"{CHANNEL_ADMIN}/config-changes?targetType={target}&targetId={tid}&page=1&size=50")
    return {"version": version, "calls": calls, "responses": snap, "removedIntegrationCrud": removed, "channelAdmin": channel_calls}


# ---------- 输出 ----------

def card_hits(value, path=""):
    """卡号原值或名为 cvv 的键（只返回位置，不返回值）。"""
    hits = []
    if isinstance(value, dict):
        for k, v in value.items():
            if str(k).lower() == "cvv" and str(v).isdigit() and 3 <= len(str(v)) <= 4:
                hits.append(f"{path}/{k}")
            hits += card_hits(v, f"{path}/{k}")
    elif isinstance(value, list):
        for i, v in enumerate(value):
            hits += card_hits(v, f"{path}[{i}]")
    elif isinstance(value, str) and CARD["pan"] in value:
        hits.append(path)
    return hits


def main():
    if len(sys.argv) not in (3, 4) or sys.argv[1] not in ("run", "cardpay"):
        raise SystemExit("用法：stage5p2_e2e.py run <label> <out.json> | cardpay <已有运行记录>")
    state = json.loads(s5.STATE.read_text())
    token = login()
    version = phase(token)
    if sys.argv[1] == "cardpay":
        # 只重跑真实读卡 PAY，并入已有运行记录（基线第一次签发票据时 ttlSeconds 超出上限 422，见报告）
        path = Path(sys.argv[2])
        data = json.loads(path.read_text())
        data["chain"]["payWithRealCard"] = {"rerunAt": datetime.now(timezone.utc).isoformat(),
                                            **real_card_pay(state, platform_probe=(version == 2))}
        hits = card_hits(data)
        if hits:
            raise SystemExit(f"输出含卡号或 CVV 键，未写入：{hits[:10]}")
        s5.write(path, data, state)
        print(json.dumps(data["chain"]["payWithRealCard"], ensure_ascii=False))
        return
    label, out = sys.argv[2], sys.argv[3]
    data = {"label": label, "version": version, "startedAt": datetime.now(timezone.utc).isoformat(),
            "adminActor": actor_of(token), "channel": state}
    data["chain"] = chain(state, version)
    data["cardGate"] = s5.card_gate()
    data["sandbox"] = sandbox(token, state, label)
    data["snapshot"] = snapshot(token, version, state)
    data["finishedAt"] = datetime.now(timezone.utc).isoformat()
    hits = card_hits(data)
    if hits:
        raise SystemExit(f"输出含卡号或 CVV 键，未写入：{hits[:10]}")
    s5.write(out, data, state)
    summary = {k: (v.get("outcome"), v.get("failureCode")) for k, v in data["chain"].items() if isinstance(v, dict) and "outcome" in v}
    summary["payWithRealCard"] = (data["chain"]["payWithRealCard"].get("pay") or {}).get("outcome")
    summary["sandbox"] = data["sandbox"].get("runStatus")
    summary["snapshotCalls"] = [len(data["snapshot"]["calls"]), [c for c in data["snapshot"]["calls"] if c[1] != 200]]
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
