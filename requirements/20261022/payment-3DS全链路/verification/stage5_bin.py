"""卡 BIN 库阶段 5（tasks/E-stage5.md BIN 部分）：经运营入口（Nginx 18444 → Gateway → ops-bff → Card / Platform）导入、校验、激活、回切与查询，
并经 Payment 建交易工具核对快照（AC-02 / AC-06）。合成 CSV 只含 6～8 位前缀与合成发卡行名，不含卡号。

子命令：
  lookup               AC-03 / AC-07：查询 40128888（8 位命中）、401288（6 位）、未识别 BIN、非法 BIN（400）、超过 8 位（400）
  import <name>        上传 → 完成 → 创建版本 → 轮询到终态（必要时向 Card 执行器 19991 触发 compensationEventScan）；name: good / bad / header
  activate <versionNo> [expectedActiveVersionNo]   激活或回切；可指定过期的期望生效版本（409）
  race <a> <b>         两个运营请求同时激活不同版本（只成功一个）
  instrument <label> <card>   经 Payment 申请卡工具并读 Token 快照
结果合并写入 stage5-bin.json。
"""
import json
import sys
import threading
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parents[2] / "20261020/channel-connector-统一对外渠道出口/verification"))
import stage5_3ds as t  # noqa: E402
import stage5_e2e as s5  # noqa: E402
import stage5_routing as rt  # noqa: E402

OUT = HERE / "stage5-bin.json"
BASE = "/card-admin/v1/bin-library"
HEADER = "bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name"
FILES = {
    "good": [HEADER,
             "40128888,VISA,CREDIT,PLATINUM,CONSUMER,AE,SYNTHETIC V2 ISSUER AE",
             "401288,VISA,CREDIT,GOLD,CONSUMER,US,SYNTHETIC V2 ISSUER US",
             "4111111,VISA,DEBIT,CLASSIC,CONSUMER,GB,SYNTHETIC V2 ISSUER GB",
             "411111,VISA,CREDIT,CLASSIC,CONSUMER,US,SYNTHETIC V2 ISSUER US",
             "555555,MASTERCARD,CREDIT,WORLD,CONSUMER,US,SYNTHETIC V2 ISSUER US",
             "51051051,MASTERCARD,DEBIT,,CONSUMER,NG,SYNTHETIC V2 ISSUER NG",
             '378282,AMEX,CHARGE,,COMMERCIAL,US,"SYNTHETIC, V2 ISSUER US"',
             "588845,MADA,DEBIT,,CONSUMER,SA,SYNTHETIC V2 ISSUER SA", ""],
    "bad": [HEADER,
            "400000,VISA,DEBIT,CLASSIC,CONSUMER,AE,OK ROW",
            "400000,VISA,DEBIT,CLASSIC,CONSUMER,AE,DUPLICATE PREFIX",
            "422222,VISAX,DEBIT,,CONSUMER,US,ILLEGAL BRAND",
            "433333,VISA,DEBIT,,CONSUMER,,MISSING COUNTRY",
            "444444,VISA,DEBIT,,CONSUMER,XX,UNMAPPED COUNTRY",
            "4111111111111111,VISA,DEBIT,,CONSUMER,US,PAN-LIKE PREFIX",
            "45AB12,VISA,DEBIT,,CONSUMER,US,LETTERS",
            "466666,VISA,DEBIT,CLASSIC,CONSUMER,US",
            "477777,VISA,OVERDRAFT,,CONSUMER,US,ILLEGAL FUNDING", ""],
    "header": ["bin,brand,country", "411111,VISA,US", ""],
}
results = {}


def save(name, data):
    results[name] = data
    print(name, json.dumps(data, ensure_ascii=False)[:2500])


def ops(token, method, path, body=None):
    return s5.ops(token, method, BASE + path, body)


def lookup():
    token = s5.login()
    out = {}
    for bin_ in ("40128888", "401288", "60110009", "12ab56", "123456789"):
        status, body = ops(token, "GET", f"/bins/{bin_}")
        out[bin_] = {"http": status, "body": body if status == 200 else {"code": body.get("code")}}
    status, body = ops(token, "GET", "/versions?page=1&size=20")
    out["versions"] = {"http": status, "items": [{k: v.get(k) for k in ("versionNo", "status", "active", "totalRows", "errorRows",
                                                                        "fileName")} for v in body.get("items", [])]}
    status, body = ops(token, "GET", "/activations?page=1&size=20")
    out["activations"] = {"http": status, "items": body.get("items")}
    save("lookup_" + t.now(), out)


def put(url, headers, data):
    request = urllib.request.Request(url, data=data, method="PUT", headers=headers)
    with urllib.request.urlopen(request, context=t.h.TLS, timeout=30) as response:
        return response.status


def card_scan():
    body = {"jobId": 1, "executorHandler": "compensationEventScan", "executorParams": "CARD_BIN_LIBRARY_IMPORT",
            "executorBlockStrategy": "SERIAL_EXECUTION", "executorTimeout": 0, "logId": int(time.time() * 1000),
            "logDateTime": int(time.time() * 1000), "glueType": "BEAN", "glueSource": "", "glueUpdatetime": 0,
            "broadcastIndex": 0, "broadcastTotal": 1}
    request = urllib.request.Request("http://127.0.0.1:19991/run", method="POST", data=json.dumps(body).encode(),
                                     headers={"XXL-JOB-ACCESS-TOKEN": t.XXL_TOKEN, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read())


def import_file(name):
    token = s5.login()
    data = "\n".join(FILES[name]).encode()
    out = {"file": f"s5-bin-{name}.csv", "bytes": len(data), "rows": len(FILES[name]) - 2}
    status, started = ops(token, "POST", "/uploads", {"fileName": out["file"], "sizeBytes": str(len(data))})
    out["start"] = {"http": status, "code": started.get("code"), "detail": str(started.get("detail"))[:200]}
    if status not in (200, 201):
        save("import_" + name, out)
        return
    auth = started["authorization"]
    out["put"] = put(auth["url"], dict(auth.get("headers") or {}), data)
    status, completed = ops(token, "POST", f"/uploads/{started['uploadId']}/completion")
    out["complete"] = {"http": status, "scanStatus": completed.get("scanStatus"), "fileAddress": completed.get("fileAddress"),
                       "code": completed.get("code"), "detail": str(completed.get("detail"))[:200]}
    if status != 200:
        save("import_" + name, out)
        return
    status, version = ops(token, "POST", "/versions", {"fileAddress": completed["fileAddress"]})
    out["create"] = {"http": status, "versionNo": version.get("versionNo"), "status": version.get("status"),
                     "code": version.get("code"), "detail": str(version.get("detail"))[:200]}
    replay_status, replay = ops(token, "POST", "/versions", {"fileAddress": completed["fileAddress"]})
    out["createReplay"] = {"http": replay_status, "versionNo": replay.get("versionNo")}
    number = version.get("versionNo")
    polls, scans = [], []
    for attempt in range(40):
        status, detail = ops(token, "GET", f"/versions/{number}")
        polls.append(detail.get("status"))
        if detail.get("status") != "VALIDATING":
            break
        if attempt in (6, 14, 24):
            scans.append(card_scan())
        time.sleep(2)
    out["final"] = {k: detail.get(k) for k in ("versionNo", "status", "active", "totalRows", "processedRows", "errorRows",
                                               "failureReason", "fileContentHash", "createdBy")}
    out["pollStatuses"] = sorted(set(polls))
    out["xxlScans"] = scans
    status, errors = ops(token, "GET", f"/versions/{number}/errors?page=1&size=50")
    out["errors"] = {"http": status, "total": errors.get("total"), "items": errors.get("items")}
    status, list_ = ops(token, "GET", "/versions?page=1&size=20")
    out["listAfter"] = [{k: v.get(k) for k in ("versionNo", "status", "active", "totalRows", "errorRows")} for v in list_.get("items", [])]
    save("import_" + name, out)


def activate(version, expected=None):
    token = s5.login()
    status, body = ops(token, "POST", f"/versions/{version}/activation",
                       {"expectedActiveVersionNo": None if expected in (None, "null") else int(expected)})
    save(f"activate_{version}_{t.now()}", {"http": status, "body": body})


def current_active(token):
    status, body = ops(token, "GET", "/versions?page=1&size=50")
    return next((v["versionNo"] for v in body.get("items", []) if v.get("active")), None)


def race(a, b):
    token = s5.login()
    expected = current_active(token)
    outcomes = {}

    def go(version):
        outcomes[version] = ops(token, "POST", f"/versions/{version}/activation", {"expectedActiveVersionNo": expected})
    threads = [threading.Thread(target=go, args=(v,)) for v in (a, b)]
    [x.start() for x in threads]
    [x.join() for x in threads]
    save(f"race_{t.now()}", {"expectedActive": expected, "outcomes": {k: {"http": v[0], "code": v[1].get("code"),
         "detail": str(v[1].get("detail"))[:160], "versionNo": v[1].get("versionNo")} for k, v in outcomes.items()},
         "activeAfter": current_active(token)})


def instrument(label, card):
    sub = t.Submission("10.00")
    sub.apply(card)
    save(f"instrument_{label}", {"intent": sub.intent, "submission": sub.submission, "token": rt.token_snapshot(sub)})


def main():
    command, args = sys.argv[1], sys.argv[2:]
    {"lookup": lookup, "import": import_file, "activate": activate, "race": race, "instrument": instrument}[command](*args)
    previous = json.loads(OUT.read_text()) if OUT.exists() else {}
    previous.update(results)
    text = json.dumps(previous, ensure_ascii=False, indent=2)
    for pan in t.CARDS.values():
        assert pan not in text, "输出中出现卡号"
    OUT.write_text(text + "\n")


if __name__ == "__main__":
    main()
