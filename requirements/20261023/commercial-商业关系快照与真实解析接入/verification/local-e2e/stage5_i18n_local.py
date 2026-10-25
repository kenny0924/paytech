"""Local e2e only: publish the operations-console language bundles (23 namespaces incl. qualification / master-data)
into mor_platform_v2e2e through the real Operations entry (nginx 18444 -> Gateway -> ops-bff -> Platform).
Mirrors requirements/20261023/operations-控制台多语言迁移/release/acceptance/ops_bundles.py. Prints no secrets.
Usage: python3 local_i18n.py [probe]"""
import json
import ssl
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR-relsnap/requirements/20261023/"
                   "commercial-商业关系快照与真实解析接入/verification/local-e2e")
import relsnap_http as h  # noqa: E402

OUT = Path("/Users/kenny/Desktop/Workspace/Code-MoR-relsnap/operations-console-web/i18n-out")
APP = "operations-console"
ADMIN = "/localization-admin/v1"
NAMES = {"i18n-admin": "多语言管理", "enum": "枚举展示名", "common": "通用", "auth": "登录与语言", "nav": "菜单与资源名",
         "access": "人员、角色与审计", "gateway": "API 路由与凭证", "channel": "渠道", "commercial": "商务", "finance": "财务",
         "recon": "对账", "risk": "风控", "workflow": "工作流", "data-query": "数据查询", "file": "文件存储", "notification": "通知",
         "webhook": "Webhook", "vault": "数据保护", "card": "卡 BIN 库", "client-review": "客户入驻审核", "currency": "币种",
         "qualification": "主体资格", "master-data": "主数据"}
NOTE = "本机 e2e：运营控制台语言包（商业关系快照 AC-12 浏览器验收）"
TLS = ssl._create_unverified_context()


def call(ops, method, path, body=None, empty_json=False):
    headers = {"Origin": h.OPERATIONS_ORIGIN, "Accept": "application/json", "Authorization": "Bearer " + ops.token,
               "X-Request-Id": "req-relsnap-" + uuid.uuid4().hex[:12]}
    data = None
    if method != "GET":
        headers["Idempotency-Key"] = str(uuid.uuid4())
        if method != "DELETE":
            headers["Content-Type"] = "application/json"
            data = b"" if empty_json else json.dumps(body or {}, ensure_ascii=False).encode()
    for attempt in range(20):
        req = urllib.request.Request(h.OPERATIONS_ORIGIN + "/api/operations" + ADMIN + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=120, context=TLS) as r:
                text = r.read().decode()
                return r.status, (json.loads(text) if text else {})
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 19:
                time.sleep(int(failure.headers.get("Retry-After") or 6))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text)
            except ValueError:
                return failure.code, {"raw": text[:300]}


def upload(ops, purpose, name, data):
    st, started = call(ops, "POST", "/uploads", {"purpose": purpose, "fileName": name, "sizeBytes": str(len(data))})
    assert st == 201, (st, started)
    grant = started["authorization"]
    req = urllib.request.Request(grant["url"], data=data, headers=dict(grant.get("headers") or {}), method=grant["method"])
    with urllib.request.urlopen(req, timeout=120, context=TLS) as r:
        assert r.status in (200, 201), r.status
    st, done = call(ops, "POST", f"/uploads/{started['uploadId']}/completion", empty_json=True)
    assert st == 200, (st, done)
    return done["fileId"]


def ready(fn):
    for i in range(15):
        st, body = fn()
        if st == 409 and body.get("code") == "I18N_IMPORT_FILE_NOT_READY":
            time.sleep(min(i + 1, 5))
            continue
        return st, body
    return st, body


def doc_with(doc, namespaces):
    return (json.dumps({**{k: v for k, v in doc.items() if k != "namespaces"}, "namespaces": namespaces},
                       ensure_ascii=False, indent=2) + "\n").encode()


def main():
    ops = h.Operations()
    if len(sys.argv) > 1 and sys.argv[1] == "probe":
        print(call(ops, "GET", f"/applications/{APP}")[0], call(ops, "GET", f"/applications/{APP}/namespaces"))
        return
    en = json.loads((OUT / f"{APP}.en.json").read_text())
    zh = json.loads((OUT / f"{APP}.zh-CN.json").read_text())
    st, detail = call(ops, "GET", f"/applications/{APP}")
    app = detail["application"]
    print("app", st, {k: app.get(k) for k in ("defaultLocale", "requiredLocales", "coverageThresholdPercent", "version")})
    if app["defaultLocale"] != "en":
        st, saved = call(ops, "PUT", f"/applications/{APP}", {
            "expectedVersion": app["version"], "applicationName": app["applicationName"], "defaultLocale": "en",
            "requiredLocales": app["requiredLocales"], "coverageThresholdPercent": app["coverageThresholdPercent"]})
        print("default locale -> en", st, saved.get("defaultLocale"), saved.get("code"))
    st, listed = call(ops, "GET", f"/applications/{APP}/namespaces")
    have = {item["namespaceCode"] for item in listed.get("items", [])}
    for order, ns in enumerate(sorted(en["namespaces"]), 1):
        if ns not in have:
            st, body = call(ops, "POST", f"/applications/{APP}/namespaces",
                            {"namespaceCode": ns, "namespaceName": NAMES[ns], "description": NOTE, "sortOrder": order * 10})
            print("namespace", ns, st, body.get("code"))
    # cumulative key batches, <= 2500 keys added per batch (Snowflake 4096-per-ms limit)
    batches, included, added = [], {}, 0
    for ns, entries in en["namespaces"].items():
        if included and added + len(entries) > 2500:
            batches.append(dict(included))
            added = 0
        included[ns] = entries
        added += len(entries)
    batches.append(dict(included))
    if len(sys.argv) > 1 and sys.argv[1] == "update":
        batches = [en["namespaces"]]  # keys already online: one full-set import, only source changes
    for i, batch in enumerate(batches, 1):
        file_id = upload(ops, "KEY_IMPORT", f"{APP}.en.{i}.json", doc_with(en, batch))
        for _ in range(6):
            st, plan = ready(lambda: call(ops, "POST", f"/applications/{APP}/key-imports/previews", {"fileId": file_id}))
            counts = {k: len(plan.get(k) or []) for k in ("added", "sourceChanged", "deprecated", "restored")}
            print(f"batch {i}/{len(batches)} preview", st, counts, plan.get("problems"), plan.get("missingNamespaces"), plan.get("code"))
            if st in (502, 503, 504):
                time.sleep(10)
                continue
            assert st == 200 and plan.get("importable") and counts["deprecated"] == 0, plan
            if not (counts["added"] or counts["sourceChanged"] or counts["restored"]):
                break
            st, result = call(ops, "POST", f"/applications/{APP}/key-imports", {"fileId": file_id, "previewDigest": plan["previewDigest"]})
            print("  confirm", st, result.get("addedCount"), result.get("code"))
            if st != 200:
                time.sleep(10)
    for ns in sorted(zh["namespaces"]):
        file_id = upload(ops, "TRANSLATION_IMPORT", f"{APP}.zh-CN.{ns}.json", doc_with(zh, {ns: zh["namespaces"][ns]}))
        for _ in range(6):
            st, result = ready(lambda: call(ops, "POST", f"/applications/{APP}/translations/imports", {"fileId": file_id, "locale": "zh-CN"}))
            if st == 200 or st not in (502, 503, 504, 409):
                break
            time.sleep(10)
        print("zh-CN", ns, st, result.get("updatedCount"), result.get("unchangedCount"), result.get("emptyCount"), result.get("code"))
    st, pre = call(ops, "GET", f"/applications/{APP}/release-precheck")
    print("precheck", st, pre.get("publishable"), pre.get("missingTotal"), pre.get("nextReleaseNo"), pre.get("problems"))
    assert pre.get("publishable"), pre
    st, release = call(ops, "POST", f"/applications/{APP}/releases", {"releaseNote": NOTE})
    print("release", st, release.get("releaseNo"), release.get("status"), release.get("code"))
    number = release.get("releaseNo")
    detail = {}
    for _ in range(300):
        st, detail = call(ops, "GET", f"/applications/{APP}/releases/{number}")
        if detail.get("release", {}).get("status") in ("READY", "FAILED"):
            break
        time.sleep(2)
    print("release state", detail.get("release", {}).get("status"), detail.get("release", {}).get("current"), detail.get("checkProgress"))


if __name__ == "__main__":
    main()
