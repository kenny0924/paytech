"""阶段 7 mor-test 卡 BIN 库验收（acceptance-plan 第 3 节），由 verification/stage5_bin.py 改写。

经运营入口（28080 → Nginx → Gateway → ops-bff → Card / Platform）导入、校验、激活、回切与查询；Card 导入任务由 mor-test XXL
`compensationEventScan` 自动推进（本脚本只轮询，不触发 XXL）。合成 CSV 只含 6～8 位前缀与合成发卡行名，不含卡号。

子命令：lookup | import <good|good2|bad|header> | activate <versionNo> [expectedActiveVersionNo] | race <a> <b>
结果合并写入 accept-bin.json。
"""
import json
import sys
import threading
import time
import urllib.request

import mt

h = mt.h
OUT = "accept-bin.json"
BASE = "/card-admin/v1/bin-library"
HEADER = "bin_prefix,card_brand,funding_type,card_level,card_product,issuer_country,issuer_name"
FILES = {
    "good": [HEADER,
             "40128888,VISA,CREDIT,PLATINUM,CONSUMER,AE,C3DS V2 ISSUER AE",
             "401288,VISA,CREDIT,GOLD,CONSUMER,US,C3DS V2 ISSUER US",
             "4111111,VISA,DEBIT,CLASSIC,CONSUMER,GB,C3DS V2 ISSUER GB",
             "411111,VISA,CREDIT,CLASSIC,CONSUMER,US,C3DS V2 ISSUER US",
             "555555,MASTERCARD,CREDIT,WORLD,CONSUMER,US,C3DS V2 ISSUER US",
             "51051051,MASTERCARD,DEBIT,,CONSUMER,NG,C3DS V2 ISSUER NG",
             '378282,AMEX,CHARGE,,COMMERCIAL,US,"C3DS, V2 ISSUER US"',
             "588845,MADA,DEBIT,,CONSUMER,SA,C3DS V2 ISSUER SA", ""],
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
# 并发激活用的第二个 VALID 版本（与 good 只差发卡行名）
FILES["good2"] = [line.replace("C3DS V2", "C3DS V5") for line in FILES["good"]]


def ops(token, method, path, body=None):
    return mt.ops(token, method, BASE + path, body)


def lookup():
    token = mt.login()
    out = {}
    for bin_ in ("40128888", "401288", "60110009", "12ab56", "123456789"):
        status, body = ops(token, "GET", f"/bins/{bin_}")
        out[bin_] = {"http": status, "body": body if status == 200 else {"code": body.get("code")}}
    status, body = ops(token, "GET", "/versions?page=1&size=20")
    out["versions"] = {"http": status, "items": [{k: v.get(k) for k in ("versionNo", "status", "active", "totalRows", "errorRows",
                                                                        "fileName")} for v in body.get("items", [])]}
    status, body = ops(token, "GET", "/activations?page=1&size=20")
    out["activations"] = {"http": status, "items": body.get("items")}
    mt.save(OUT, "lookup_" + mt.now(), out)


def put(url, headers, data):
    request = urllib.request.Request(url, data=data, method="PUT", headers=headers)
    with urllib.request.urlopen(request, context=h.TLS, timeout=30) as response:
        return response.status


def import_file(name):
    token = mt.login()
    data = "\n".join(FILES[name]).encode()
    out = {"at": mt.now(), "file": f"c3ds-bin-{name}.csv", "bytes": len(data), "rows": len(FILES[name]) - 2}
    status, started = ops(token, "POST", "/uploads", {"fileName": out["file"], "sizeBytes": str(len(data))})
    out["start"] = {"http": status, "code": started.get("code"), "detail": str(started.get("detail"))[:200]}
    if status not in (200, 201):
        return mt.save(OUT, "import_" + name, out)
    auth = started["authorization"]
    out["putHost"] = auth["url"].split("/")[2]
    out["put"] = put(auth["url"], dict(auth.get("headers") or {}), data)
    status, completed = ops(token, "POST", f"/uploads/{started['uploadId']}/completion")
    out["complete"] = {"http": status, "scanStatus": completed.get("scanStatus"), "fileAddress": completed.get("fileAddress"),
                       "code": completed.get("code"), "detail": str(completed.get("detail"))[:200]}
    if status != 200:
        return mt.save(OUT, "import_" + name, out)
    status, version = ops(token, "POST", "/versions", {"fileAddress": completed["fileAddress"]})
    out["create"] = {"http": status, "versionNo": version.get("versionNo"), "status": version.get("status"),
                     "code": version.get("code"), "detail": str(version.get("detail"))[:200]}
    replay_status, replay = ops(token, "POST", "/versions", {"fileAddress": completed["fileAddress"]})
    out["createReplay"] = {"http": replay_status, "versionNo": replay.get("versionNo")}
    number, polls, started_at = version.get("versionNo"), [], time.time()
    detail = {}
    while number and time.time() - started_at < 240:
        status, detail = ops(token, "GET", f"/versions/{number}")
        polls.append(detail.get("status"))
        if detail.get("status") != "VALIDATING":
            break
        time.sleep(5)
    out["secondsToFinal"] = round(time.time() - started_at, 1)
    out["final"] = {k: detail.get(k) for k in ("versionNo", "status", "active", "totalRows", "processedRows", "errorRows",
                                               "failureReason", "fileContentHash", "createdBy")}
    out["pollStatuses"] = sorted(set(p for p in polls if p))
    status, errors = ops(token, "GET", f"/versions/{number}/errors?page=1&size=50")
    out["errors"] = {"http": status, "total": errors.get("total"), "items": errors.get("items")}
    status, list_ = ops(token, "GET", "/versions?page=1&size=20")
    out["listAfter"] = [{k: v.get(k) for k in ("versionNo", "status", "active", "totalRows", "errorRows")} for v in list_.get("items", [])]
    mt.save(OUT, "import_" + name, out)


def activate(version, expected=None):
    token = mt.login()
    status, body = ops(token, "POST", f"/versions/{version}/activation",
                       {"expectedActiveVersionNo": None if expected in (None, "null") else int(expected)})
    mt.save(OUT, f"activate_{version}_{mt.now()}", {"http": status, "body": body})


def current_active(token):
    status, body = ops(token, "GET", "/versions?page=1&size=50")
    return next((v["versionNo"] for v in body.get("items", []) if v.get("active")), None)


def race(a, b):
    token = mt.login()
    expected = current_active(token)
    outcomes = {}

    def go(version):
        outcomes[version] = ops(token, "POST", f"/versions/{version}/activation", {"expectedActiveVersionNo": expected})
    threads = [threading.Thread(target=go, args=(v,)) for v in (a, b)]
    [x.start() for x in threads]
    [x.join() for x in threads]
    mt.save(OUT, f"race_{mt.now()}", {"expectedActive": expected, "outcomes": {k: {"http": v[0], "code": v[1].get("code"),
            "detail": str(v[1].get("detail"))[:160], "versionNo": v[1].get("versionNo")} for k, v in outcomes.items()},
            "activeAfter": current_active(token)})


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    {"lookup": lookup, "import": import_file, "activate": activate, "race": race}[command](*args)
