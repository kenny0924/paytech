"""多语言管理阶段 7：首批语言包（operations-console、client-console），经 Gateway 走真实运营接口。

每个应用：
  1. 核对应用存在且 ENABLED；按 APP_NAMESPACES 建缺少的命名空间（已存在则跳过）；
  2. 上传 release/i18n-bundles/<应用>.en.json（KEY_IMPORT）→ 预览（遇 409 I18N_IMPORT_FILE_NOT_READY 重试）→ 无缺失命名空间、无问题 → 确认；
  3. 上传 <应用>.zh-CN.json（TRANSLATION_IMPORT）→ 导入 zh-CN；
  4. 预检 publishable、missingTotal 0 → 发布 → 轮询到 READY 且成为当前发布（超时 300 秒）；每个语言包的文件记录
     必须在 MINIO/mor-platform-public（platform-support × PUBLIC 精确规则），不是默认 `*` × PUBLIC 的 S3_TEST 桶；
  5. 读该应用 BFF 的匿名 manifest（ops：/api/operations/i18n/v1/manifest；client：/api/client/i18n/v1/manifest），
     逐个不带凭据读取语言包公开地址：摘要等于 manifest hash，key 集合与上传文件一致，带两个控制台 Origin 时返回 CORS 允许头。
可重跑：已存在的命名空间跳过；预览无新增 / 变更时跳过确认；预检显示与当前发布无差异且当前发布 READY 时跳过发布。
输入文件以 release/i18n-bundles/SHA256SUMS 核对（来自候选构建 worktree 的 npm run i18n:export-source）。
产物：运行记录 I18N7_STATE/first-bundles-<时间>.json；manifest 快照写 release/i18n-bundles/published/。

用法：
  python3 -I first_bundles.py --dry-run        只做本机核对与计划（不登录、不调用任何 mor-test 接口）
  python3 -I first_bundles.py [--app operations-console|client-console]   阶段 7 第 13 步执行（写数据）
"""
import argparse
import hashlib
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import i18n7_common as c  # noqa: E402

MANIFEST = {"operations-console": (c.OPS_MANIFEST, c.OPS, "zh-CN"), "client-console": (c.CLIENT_MANIFEST, c.CLIENT, "zh-CN")}


def load_inputs():
    sums = {}
    for line in (c.BUNDLE_DIR / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        sums[name.strip()] = digest
    files = {}
    for app, namespaces in c.APP_NAMESPACES.items():
        for kind in ("en", "zh-CN"):
            name = f"{app}.{kind}.json"
            data = (c.BUNDLE_DIR / name).read_bytes()
            c.check("输入", f"{name} SHA-256 与 SHA256SUMS 一致", hashlib.sha256(data).hexdigest() == sums.get(name))
            doc = json.loads(data)
            expected_format = "code-mor-i18n-source/1" if kind == "en" else "code-mor-i18n-translation/1"
            c.check("输入", f"{name} 格式 {expected_format}、应用 {app}", doc.get("format") == expected_format and doc.get("application") == app,
                    {"format": doc.get("format"), "application": doc.get("application")})
            c.check("输入", f"{name} 命名空间 = {namespaces}", sorted(doc["namespaces"]) == sorted(namespaces), sorted(doc["namespaces"]))
            files[(app, kind)] = (data, doc)
        source, translation = files[(app, "en")][1], files[(app, "zh-CN")][1]
        missing = [k for ns, keys in source["namespaces"].items() for k in keys if not translation["namespaces"].get(ns, {}).get(k)]
        total = sum(len(keys) for keys in source["namespaces"].values())
        c.check("输入", f"{app} zh-CN 覆盖 {total - len(missing)}/{total}（应用阈值 100%）", not missing, missing[:5])
    return files


def ensure_namespaces(token, app, record):
    status, listed, _, _ = c.ops(token, "GET", f"/applications/{app}/namespaces")
    existing = {item["namespaceCode"] for item in listed.get("items", [])} if status == 200 else set()
    created = []
    for order, namespace in enumerate(c.APP_NAMESPACES[app], start=1):
        if namespace in existing:
            continue
        status, body, _, _ = c.ops(token, "POST", f"/applications/{app}/namespaces", {
            "namespaceCode": namespace, "namespaceName": c.NAMESPACE_NAMES[namespace],
            "description": "首批语言包（2026-10-23 阶段 7）", "sortOrder": order * 10})
        c.check("AC-02", f"{app} 新建命名空间 {namespace}（{status}）", status in (200, 201), body)
        created.append(namespace)
    record.setdefault("createdNamespaces", {})[app] = created
    status, listed, _, _ = c.ops(token, "GET", f"/applications/{app}/namespaces")
    codes = sorted(item["namespaceCode"] for item in listed.get("items", []))
    c.check("AC-02", f"{app} 命名空间齐全 {codes}", set(c.APP_NAMESPACES[app]) <= set(codes), codes)


def import_keys(token, app, data, doc, record):
    completed = c.upload_json(token, "KEY_IMPORT", f"{app}.en.json", data)
    record.setdefault("uploads", []).append({"app": app, "purpose": "KEY_IMPORT", **completed})
    status, preview, not_ready = c.with_file_ready(
        lambda: c.ops(token, "POST", f"/applications/{app}/key-imports/previews", {"fileId": completed["fileId"]}))
    total = sum(len(keys) for keys in doc["namespaces"].values())
    ok = status == 200 and preview.get("importable") and not preview.get("missingNamespaces") and not preview.get("problems")
    c.check("AC-02", f"{app} key 导入预览：新增 {len(preview.get('added', []))}、变更 {len(preview.get('sourceChanged', []))}、"
                     f"废弃 {len(preview.get('deprecated', []))}、不变 {preview.get('unchangedCount')}（文件 {total}）", ok,
            {k: preview.get(k) for k in ("code", "detail", "missingNamespaces", "problems")})
    if not ok:
        raise SystemExit(1)
    if not (preview["added"] or preview["sourceChanged"] or preview["deprecated"] or preview["restored"]):
        c.check("AC-02", f"{app} key 与线上一致，跳过确认", True)
        return
    status, result, _, _ = c.ops(token, "POST", f"/applications/{app}/key-imports",
                                 {"fileId": completed["fileId"], "previewDigest": preview["previewDigest"]})
    c.check("AC-02", f"{app} key 导入确认（新增 {result.get('addedCount')}，变更 {result.get('sourceChangedCount')}）",
            status == 200 and result.get("addedCount", 0) + result.get("sourceChangedCount", 0) + result.get("restoredCount", 0)
            == len(preview["added"]) + len(preview["sourceChanged"]) + len(preview["restored"]), result)
    record.setdefault("keyImports", {})[app] = {"preview": {k: len(preview[k]) for k in ("added", "sourceChanged", "deprecated", "restored")},
                                                "result": result, "sawFileNotReady": not_ready}


def import_translations(token, app, data, record):
    completed = c.upload_json(token, "TRANSLATION_IMPORT", f"{app}.zh-CN.json", data)
    record.setdefault("uploads", []).append({"app": app, "purpose": "TRANSLATION_IMPORT", **completed})
    status, result, _ = c.with_file_ready(
        lambda: c.ops(token, "POST", f"/applications/{app}/translations/imports", {"fileId": completed["fileId"], "locale": "zh-CN"}))
    c.check("AC-09", f"{app} 导入 zh-CN 翻译（更新 {result.get('updatedCount')}、不变 {result.get('unchangedCount')}、空 {result.get('emptyCount')}）",
            status == 200 and result.get("emptyCount") == 0, result)
    record.setdefault("translationImports", {})[app] = result


def publish(token, app, record):
    status, precheck, _, _ = c.ops(token, "GET", f"/applications/{app}/release-precheck")
    c.check("AC-04", f"{app} 预检可发布（缺口 {precheck.get('missingTotal')}，下一发布号 {precheck.get('nextReleaseNo')}）",
            status == 200 and precheck.get("publishable") and precheck.get("missingTotal") == 0,
            {k: precheck.get(k) for k in ("publishable", "missingTotal", "problems", "coverage")})
    summary = precheck.get("changeSummary") or {}
    _, detail, _, _ = c.ops(token, "GET", f"/applications/{app}")
    current = (detail.get("currentRelease") or {})
    unchanged = (summary.get("baseReleaseNo") is not None and not summary.get("addedKeyCount") and not summary.get("deprecatedKeyCount")
                 and not summary.get("sourceChangedKeyCount") and not any(t.get("changedCount") for t in summary.get("translationChanges") or []))
    if unchanged and current.get("status") == "READY":
        c.check("AC-04", f"{app} 与当前发布 #{current.get('releaseNo')} 无差异，跳过发布", True)
        record.setdefault("releases", {})[app] = current.get("releaseNo")
        return current.get("releaseNo")
    status, release, _, _ = c.ops(token, "POST", f"/applications/{app}/releases", {"releaseNote": "首批语言包（2026-10-23 阶段 7）"})
    c.check("AC-04", f"{app} 发布 #{release.get('releaseNo')} → {release.get('status')}", status in (200, 201)
            and release.get("status") == "PENDING_CHECK", release)
    if status not in (200, 201):
        raise SystemExit(1)
    started = time.time()
    detail = c.wait_release(token, app, release["releaseNo"])
    progress = detail.get("checkProgress") or {}
    c.check("AC-04", f"{app} 发布 #{release['releaseNo']} 检查后上线 READY 且为当前（{time.time() - started:.0f} 秒，"
                     f"语言包 {progress.get('available')}/{progress.get('total')}）",
            detail.get("release", {}).get("status") == "READY" and detail["release"].get("current")
            and progress.get("available") == progress.get("total") == len(c.APP_NAMESPACES[app]) * 2,
            {"release": detail.get("release"), "checkProgress": progress})
    record.setdefault("releases", {})[app] = release["releaseNo"]
    verify_bundle_storage(token, app, release["releaseNo"], detail)
    return release["releaseNo"]


def verify_bundle_storage(token, app, release_no, detail):
    """语言包必须经 platform-support × PUBLIC 精确规则写 MINIO/mor-platform-public，不能回落到 `*` × PUBLIC 的 S3_TEST 桶。"""
    for bundle in detail.get("bundles") or []:
        file_id = bundle["fileAddress"].rsplit("/", 1)[-1]
        status, record, _, _ = c.raw(token, "GET", f"/storage-admin/v1/files/{file_id}")
        c.check("存储", f"{app} #{release_no} {bundle['locale']}/{bundle['namespaceCode']} 文件记录在 MINIO/mor-platform-public（不是 S3_TEST）",
                status == 200 and record.get("instanceCode") == "MINIO" and record.get("bucketName") == "mor-platform-public"
                and record.get("accessLevel") == "PUBLIC" and record.get("sourceSystem") == "platform-support",
                {k: record.get(k) for k in ("instanceCode", "bucketName", "accessLevel", "sourceSystem", "reasonCode")})


def verify_manifest(app, release_no, files, record):
    path, base, locale = MANIFEST[app]
    status, manifest, text, headers = c.raw(None, "GET", f"?locale={locale}", prefix=path, base=base, key=False)
    c.check("AC-06", f"{app} 匿名 manifest 200、发布 #{manifest.get('releaseNo')}、语言 {[l.get('code') for l in manifest.get('locales', [])]}",
            status == 200 and manifest.get("releaseNo") == release_no and manifest.get("application") == app
            and {l["code"] for l in manifest.get("locales", [])} >= {"en", "zh-CN"}, {"status": status, "body": text[:300]})
    out = c.BUNDLE_DIR / "published"
    out.mkdir(exist_ok=True)
    (out / f"{app}-r{release_no}-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n")
    record.setdefault("manifestHeaders", {})[app] = {k: v for k, v in headers.items() if k.lower() in ("cache-control", "etag", "pragma")}
    expected = {"en": files[(app, "en")][1]["namespaces"], "zh-CN": files[(app, "zh-CN")][1]["namespaces"]}
    bundles = manifest.get("bundles") or {}
    for loc in ("en", "zh-CN"):
        c.check("AC-06", f"{app} manifest {loc} 命名空间齐全", sorted(bundles.get(loc, {})) == sorted(c.APP_NAMESPACES[app]),
                sorted(bundles.get(loc, {})))
        for namespace, bundle in sorted(bundles.get(loc, {}).items()):
            url = bundle["url"]
            st, body, hdrs = c.public_get(url, origin=base)
            content_ok = False
            try:
                content = json.loads(body)
                if loc == "en":
                    content_ok = content == {k: (v if isinstance(v, str) else v.get("text")) for k, v in expected[loc][namespace].items()}
                else:
                    content_ok = content == expected[loc][namespace]
            except ValueError:
                pass
            c.check("AC-06", f"{app} {loc}/{namespace} 公开地址匿名 200、摘要 = hash、内容 = 上传文件、CORS 允许 {base}",
                    st == 200 and hashlib.sha256(body).hexdigest() == bundle["hash"] and url.startswith(c.PUBLIC_BASE + "/i18n/" + app + "/")
                    and content_ok and hdrs.get("access-control-allow-origin") == base,
                    {"status": st, "url": url, "acao": hdrs.get("access-control-allow-origin"), "cache": hdrs.get("cache-control")})
            record.setdefault("bundles", []).append({"app": app, "locale": loc, "namespace": namespace, "url": url,
                                                     "hash": bundle["hash"], "cacheControl": hdrs.get("cache-control")})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--app", choices=sorted(c.APP_NAMESPACES))
    args = parser.parse_args()
    files = load_inputs()
    apps = [args.app] if args.app else ["operations-console", "client-console"]
    if args.dry_run:
        for app in apps:
            print(f"计划 {app}：命名空间 {c.APP_NAMESPACES[app]}；KEY_IMPORT {app}.en.json；TRANSLATION_IMPORT {app}.zh-CN.json；"
                  f"发布；manifest {MANIFEST[app][1]}{MANIFEST[app][0]}")
        return c.summary("首批语言包（干跑，未调用任何接口）")
    record = {"startedAt": c.now_iso(), "apps": apps}
    token = c.admin_token()
    for app in apps:
        status, detail, _, _ = c.ops(token, "GET", f"/applications/{app}")
        if not c.check("准备", f"{app} 应用存在且 ENABLED（{status}）", status == 200 and detail["application"]["status"] == "ENABLED", detail):
            raise SystemExit(1)
        ensure_namespaces(token, app, record)
        import_keys(token, app, *files[(app, "en")], record)
        import_translations(token, app, files[(app, "zh-CN")][0], record)
        release_no = publish(token, app, record)
        verify_manifest(app, release_no, files, record)
        c.save(f"first-bundles-{record['startedAt'].replace(':', '')}.json", record)
    record["finishedAt"] = c.now_iso()
    path = c.save(f"first-bundles-{record['startedAt'].replace(':', '')}.json", record)
    print("运行记录：", path)
    return c.summary("首批语言包")


if __name__ == "__main__":
    sys.exit(main())
