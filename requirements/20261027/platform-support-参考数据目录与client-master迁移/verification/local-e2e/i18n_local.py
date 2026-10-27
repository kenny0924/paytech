#!/usr/bin/env python3
"""阶段 5 本机语言包：在本机 e2e Platform（mor_platform_v2e2e）为 client-console / operations-console 导入并发布本需求的文案。

链路：Nginx(127.0.0.1:18444) → Gateway → operations-console-bff → Platform /localization-admin/v1（管理员登录，凭据取 e2e/secrets.env，不打印）。
KEY_IMPORT 把文件视为应用的完整 key 集合，所以 en 文件 = worktree Web 的 `npm run i18n:export-source` 输出 + release/i18n-bundles 的 refdata。
zh-CN 导入集合 = 新增 + 基准变更 + 线上缺 zh 的 key；线上已有且基准未变的 zh 不覆盖。
refdata 是 Platform 数据名称：线上 zh 与语言包不同时以语言包为准。refdata zh-CN 现为 387 / 387（MCC 全部翻译，用户决定 2026-10-27）；
--mcc-english-fill 只是首次发布时的临时做法（把缺译 MCC 填为英文名），现已不需要。

用法：python3 -I i18n_local.py --export-dir <export 输出目录> [--app client-console|operations-console] [--apply] [--mcc-english-fill]
不带 --apply 只读（SQL + 计划）。
"""
import argparse
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
LOCAL_ENV = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment")
OPS = "https://127.0.0.1:18444"
MANIFEST = {"operations-console": (OPS, "/api/operations/i18n/v1/manifest"),
            "client-console": ("https://127.0.0.1:18443", "/api/client/i18n/v1/manifest")}
ADMIN = "/api/operations/localization-admin/v1"
TLS = ssl._create_unverified_context()  # 本机自签名证书，只连接 127.0.0.1
DB = "mor_platform_v2e2e"
NAMES = {"refdata": "参考数据名称", "reference-data": "参考数据", "onboarding-schema": "入驻采集表单", "common": "通用",
         "auth": "登录与注册", "onboarding": "入驻", "workspace": "工作台", "team": "团队", "invitation": "邀请",
         "developer": "开发者", "enum": "枚举展示名"}
NOTE = "参考数据目录与 client-master 迁移：refdata 全部 MCC 中文译名（阶段 5，本机 2026-10-27）"
results = []


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


def check(name, ok, evidence=None):
    results.append({"step": name, "ok": bool(ok), "evidence": evidence})
    print(("PASS " if ok else "FAIL ") + name + ("" if ok or evidence is None else f"  {json.dumps(evidence, ensure_ascii=False)[:400]}"))
    return ok


def sql(statement):
    password = read_env(LOCAL_ENV / ".env.local")["MOR_MYSQL_ROOT_PASSWORD"]
    out = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={password}", "mor-local-mysql-1", "mysql", "-uroot", "-N", "-B",
                          "--default-character-set=utf8mb4", DB, "-e", statement], capture_output=True, text=True)
    if out.returncode:
        raise SystemExit("SQL 失败：" + out.stderr[:300])
    return [row.split("\t") for row in out.stdout.splitlines() if row]


def http(method, url, body=None, token=None, data=None, headers=None, origin=OPS):
    hdrs = {"Origin": origin, "Accept": "application/json", **(headers or {})}
    if token:
        hdrs["Authorization"] = "Bearer " + token
    if method not in ("GET", "DELETE") and data is None:
        hdrs["Content-Type"] = "application/json"
        hdrs["Idempotency-Key"] = str(uuid.uuid4())
        data = b"" if body is None else json.dumps(body, ensure_ascii=False).encode()
    for attempt in range(20):
        request = urllib.request.Request(url, data=data, headers=hdrs, method=method)
        try:
            with urllib.request.urlopen(request, context=TLS, timeout=60) as response:
                text = response.read().decode()
                return response.status, (json.loads(text) if text and text[0] in "{[" else {"raw": text[:200]}), text
        except urllib.error.HTTPError as failure:
            text = failure.read().decode()
            if failure.code == 429 and attempt < 19:
                time.sleep(int(failure.headers.get("Retry-After") or 5))
                continue
            try:
                return failure.code, json.loads(text), text
            except ValueError:
                return failure.code, {"raw": text[:300]}, text


def admin(token, method, path, body=None):
    status, payload, _ = http(method, OPS + ADMIN + path, body, token)
    return status, payload


def login():
    secrets = read_env(LOCAL_ENV / "e2e/secrets.env")
    status, tokens, _ = http("POST", OPS + "/api/operations/auth/v1/login",
                             {"loginIdentifier": secrets["E2E_ADMIN_EMAIL"], "password": secrets["E2E_ADMIN_PASSWORD"]})
    if status != 200:
        raise SystemExit(f"登录失败 {status}（若为 503 vault-service，docker restart mor-local-vault-service-1）")
    return tokens["accessToken"]


def upload(token, purpose, name, data):
    status, started = admin(token, "POST", "/uploads", {"purpose": purpose, "fileName": name, "sizeBytes": str(len(data))})
    if status != 201:
        raise SystemExit(f"开始上传失败 {status} {started}")
    grant = started["authorization"]
    request = urllib.request.Request(grant["url"], data=data, headers=dict(grant.get("headers") or {}), method=grant["method"])
    with urllib.request.urlopen(request, context=TLS, timeout=60) as response:
        if response.status not in (200, 201):
            raise SystemExit(f"直传失败 {response.status}")
    status, completed = admin(token, "POST", f"/uploads/{started['uploadId']}/completion")
    if status != 200:
        raise SystemExit(f"完成上传失败 {status} {completed}")
    return completed["fileId"]


def file_ready(call):
    for attempt in range(15):
        status, body = call()
        if status == 409 and body.get("code") == "I18N_IMPORT_FILE_NOT_READY":
            time.sleep(min(attempt + 1, 5))
            continue
        return status, body
    return status, body


# ---------- 计划（只读） ----------

def plan(app, export_dir, english_fill):
    en_doc = json.loads((export_dir / f"{app}.en.json").read_text())
    zh_doc = json.loads((export_dir / f"{app}.zh-CN.json").read_text())
    ref_en = json.loads((REQ / f"release/i18n-bundles/{app}.refdata.en.json").read_text())["namespaces"]
    ref_zh = json.loads((REQ / f"release/i18n-bundles/{app}.refdata.zh-CN.json").read_text())["namespaces"]
    en = {**en_doc["namespaces"], **ref_en}
    zh = {**zh_doc["namespaces"], **{ns: dict(v) for ns, v in ref_zh.items()}}
    filled = []
    if english_fill:
        for key, text in en["refdata"].items():
            if key not in zh["refdata"]:
                if not key.startswith("refdata.mcc."):
                    raise SystemExit(f"非 MCC 的 refdata key 缺 zh-CN：{key}")
                zh["refdata"][key] = text
                filled.append(key)
    rows = sql(f"""SELECT n.namespace_code, k.key_code, k.status, k.source_text, t.text
        FROM t_i18n_namespace n JOIN t_i18n_application a ON a.application_id = n.application_id
        LEFT JOIN t_i18n_key k ON k.namespace_id = n.namespace_id
        LEFT JOIN t_i18n_translation t ON t.key_id = k.key_id AND t.locale_code = 'zh-CN'
        WHERE a.application_code = '{app}'""")
    live_ns = {r[0] for r in rows}
    live = {r[1]: {"ns": r[0], "st": r[2], "src": r[3], "zh": None if r[4] == "NULL" else r[4]} for r in rows if r[1] != "NULL"}
    max_sort = int((sql(f"SELECT COALESCE(MAX(n.sort_order),0) FROM t_i18n_namespace n JOIN t_i18n_application a "
                        f"ON a.application_id=n.application_id WHERE a.application_code='{app}'") or [["0"]])[0][0])
    repo = {k: (ns, text if isinstance(text, str) else text.get("text")) for ns, keys in en.items() for k, text in keys.items()}
    active = {k for k, v in live.items() if v["st"] == "ACTIVE"}
    added = sorted(k for k in repo if k not in live or live[k]["st"] != "ACTIVE")
    changed = sorted(k for k in repo if k in active and live[k]["src"] != repo[k][1])
    deprecated = sorted(active - set(repo))
    zh_flat = {k: v for ns in zh.values() for k, v in ns.items()}
    # refdata 是 Platform 数据的名称（不由运营在页面改写）：线上 zh 与语言包不同即以语言包为准（替换本机此前的英文填充）。
    refdata_diff = {k for k in en.get("refdata", {}) if k in active and k in zh_flat and live[k]["zh"] != zh_flat[k]}
    zh_set = sorted(set(added) | set(changed) | {k for k in repo if k in active and live[k]["zh"] is None} | refdata_diff)
    zh_missing = [k for k in zh_set if not (zh_flat.get(k) or "").strip()]
    new_ns = [ns for ns in en if ns not in live_ns]
    return {"app": app, "en": {"format": "code-mor-i18n-source/1", "application": app, "namespaces": en},
            "zh": {"format": "code-mor-i18n-translation/1", "application": app, "locale": "zh-CN",
                   "namespaces": {ns: {k: zh_flat[k] for k in en[ns] if k in zh_set and k in zh_flat} for ns in en}},
            "newNamespaces": new_ns, "maxSort": max_sort, "added": added, "changed": changed, "deprecated": deprecated,
            "zhImport": zh_set, "refdataZhReplaced": sorted(refdata_diff), "zhMissing": zh_missing, "englishFilled": filled, "repoNamespaces": len(en)}


def summary(p):
    return {k: (len(v) if isinstance(v, list) else v) for k, v in p.items() if k not in ("en", "zh")} | {
        "newNamespaces": p["newNamespaces"], "deprecatedKeys": p["deprecated"][:40], "refdataZhReplaced": len(p["refdataZhReplaced"])}


# ---------- 执行 ----------

def apply(token, p):
    app = p["app"]
    for i, ns in enumerate(p["newNamespaces"]):
        status, body = admin(token, "POST", f"/applications/{app}/namespaces",
                             {"namespaceCode": ns, "namespaceName": NAMES.get(ns, ns), "description": NOTE,
                              "sortOrder": p["maxSort"] + 10 * (i + 1)})
        check(f"{app} 新建命名空间 {ns}：{status}", status in (200, 201), body)
    file_id = upload(token, "KEY_IMPORT", f"{app}.en.json", (json.dumps(p["en"], ensure_ascii=False, indent=1) + "\n").encode())
    for attempt in range(6):
        status, preview = file_ready(lambda: admin(token, "POST", f"/applications/{app}/key-imports/previews", {"fileId": file_id}))
        counts = {k: len(preview.get(k) or []) for k in ("added", "sourceChanged", "deprecated", "restored")}
        ok = status == 200 and preview.get("importable") and not preview.get("missingNamespaces") and not preview.get("problems")
        if not check(f"{app} key 导入预览 #{attempt + 1}：{counts}", ok,
                     {k: preview.get(k) for k in ("code", "detail", "missingNamespaces", "problems")}):
            raise SystemExit(1)
        if not any(counts.values()):
            check(f"{app} key 集合与文件一致（不变 {preview.get('unchangedCount')}）", True)
            break
        status, result = admin(token, "POST", f"/applications/{app}/key-imports", {"fileId": file_id, "previewDigest": preview["previewDigest"]})
        check(f"{app} key 导入确认：{status} {({k: result.get(k) for k in ('addedCount', 'sourceChangedCount', 'deprecatedCount')})}",
              status == 200, result)
    count = sum(len(v) for v in p["zh"]["namespaces"].values())
    if count:
        file_id = upload(token, "TRANSLATION_IMPORT", f"{app}.zh-CN.json", (json.dumps(p["zh"], ensure_ascii=False, indent=1) + "\n").encode())
        status, result = file_ready(lambda: admin(token, "POST", f"/applications/{app}/translations/imports", {"fileId": file_id, "locale": "zh-CN"}))
        check(f"{app} zh-CN 导入：写入 {result.get('updatedCount')}、不变 {result.get('unchangedCount')}、空 {result.get('emptyCount')}（文件 {count}）",
              status == 200 and result.get("emptyCount") == 0, result)
    rebase_unchanged(token, app)
    status, pre = admin(token, "GET", f"/applications/{app}/release-precheck")
    if not check(f"{app} 发布预检：publishable={pre.get('publishable')} 缺口 {pre.get('missingTotal')} 变更 {pre.get('changeSummary')}",
                 status == 200 and pre.get("publishable"), {k: pre.get(k) for k in ("missingTotal", "problems", "coverage")}):
        raise SystemExit(1)
    status, release = admin(token, "POST", f"/applications/{app}/releases", {"releaseNote": NOTE})
    release_no = release.get("releaseNo")
    check(f"{app} 发布 #{release_no}：{status}", status in (200, 201) and release_no, release)
    deadline, detail = time.time() + 600, {}
    while time.time() < deadline:
        status, detail = admin(token, "GET", f"/applications/{app}/releases/{release_no}")
        if (detail.get("release") or {}).get("status") in ("READY", "FAILED"):
            break
        time.sleep(3)
    progress = detail.get("checkProgress") or {}
    check(f"{app} #{release_no} READY 且为当前（语言包 {progress.get('available')}/{progress.get('total')}）",
          (detail.get("release") or {}).get("status") == "READY" and detail["release"].get("current"), progress)
    return release_no


def rebase_unchanged(token, app):
    """基准变更而译文不变的 zh-CN（如行政区英文名改为通用名）：翻译导入"不变"不会改依据的基准修订，
    用批量保存原文确认到当前基准（与运营在翻译页逐条确认等效），每批 ≤ 200。"""
    rows = sql(f"""SELECT k.key_code, t.text, t.version FROM t_i18n_translation t JOIN t_i18n_key k ON k.key_id = t.key_id
        JOIN t_i18n_namespace n ON n.namespace_id = k.namespace_id JOIN t_i18n_application a ON a.application_id = n.application_id
        WHERE a.application_code = '{app}' AND t.locale_code = 'zh-CN' AND k.status = 'ACTIVE' AND t.based_on_source_revision < k.source_revision""")
    for start in range(0, len(rows), 200):
        batch = [{"keyCode": k, "text": t, "expectedVersion": int(v)} for k, t, v in rows[start:start + 200]]
        status, body = admin(token, "POST", f"/applications/{app}/translations/batch", {"locale": "zh-CN", "translations": batch})
        check(f"{app} 确认基准已变更的 zh-CN {len(batch)} 条：{status}", status == 200, body)


def verify_manifest(app, release_no, expect_namespaces):
    origin, path = MANIFEST[app]
    status, manifest, text = http("GET", origin + path + "?locale=zh-CN", origin=origin)
    check(f"{app} 匿名 manifest {status}、#{manifest.get('releaseNo')}", status == 200 and manifest.get("releaseNo") == release_no, text[:300])
    for locale in ("en", "zh-CN"):
        bundles = (manifest.get("bundles") or {}).get(locale, {})
        check(f"{app} {locale} 命名空间 {len(bundles)}（期望 {expect_namespaces}）", len(bundles) == expect_namespaces)
        for ns in ("refdata", "reference-data"):
            if ns in bundles:
                url = bundles[ns]["url"]
                url = url if url.startswith("http") else origin + url
                request = urllib.request.Request(url, headers={"Origin": origin})
                with urllib.request.urlopen(request, context=TLS, timeout=30) as response:
                    body = json.loads(response.read().decode())
                sample = {k: body.get(k) for k in ("refdata.subdivision.AE-DU", "refdata.industry.RETAIL_APPAREL", "refdata.mcc.5651",
                                                    "refdata.mcc.0742", "reference-data.column.status") if k in body}
                check(f"{app} {locale}/{ns} 匿名可读：{len(body)} 条，样例 {sample}", len(body) > 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--export-dir", required=True)
    parser.add_argument("--app", choices=sorted(MANIFEST))
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--mcc-english-fill", action="store_true")
    parser.add_argument("--out")
    args = parser.parse_args()
    apps = [args.app] if args.app else sorted(MANIFEST)
    plans = [plan(app, Path(args.export_dir), args.mcc_english_fill) for app in apps]
    for p in plans:
        print(json.dumps(summary(p), ensure_ascii=False))
        check(f"{p['app']} 新增 / 变更 key 都有 zh-CN（缺 {len(p['zhMissing'])}）", not p["zhMissing"], p["zhMissing"][:10])
    record = {"plans": [summary(p) for p in plans]}
    if args.apply:
        token = login()
        for p in plans:
            release_no = apply(token, p)
            verify_manifest(p["app"], release_no, p["repoNamespaces"])
    record["results"] = results
    if args.out:
        Path(args.out).write_text(json.dumps(record, ensure_ascii=False, indent=1) + "\n")
    sys.exit(0 if all(r["ok"] for r in results) else 1)


if __name__ == "__main__":
    main()
