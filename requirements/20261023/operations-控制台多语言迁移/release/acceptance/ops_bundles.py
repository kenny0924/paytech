"""运营控制台多语言迁移阶段 7 第 ② 步：operations-console 语言包数据（经 Gateway 走真实运营接口；实现沿用前例 first_bundles.py）。

  1. 应用存在且 ENABLED；默认语言改为 en（PUT 应用设置，必须语言、覆盖率阈值与名称原样带回；原值写运行记录，供回滚）；
  2. 建 19 个新命名空间（已存在则跳过；i18n-admin、enum 已有）；
  3. 上传 i18n-bundles/operations-console.en.json（KEY_IMPORT，8523 key 全集）→ 预览（409 I18N_IMPORT_FILE_NOT_READY 退避）
     → 无缺失命名空间、无问题、废弃 0 → 确认；
  4. zh-CN 翻译按命名空间切成 21 个导入文件逐个导入（Platform 翻译导入逐行写入，全量一次可能超过 BFF 9 秒读取超时）；
  5. 预检 publishable、missingTotal 0 → 发布 → 轮询到 READY 且为当前（语言包 42/42）；每个语言包的文件记录在 MINIO/mor-platform-public；
  6. 匿名 manifest（en、zh-CN）各 21 个命名空间；逐个不带凭据读取语言包：摘要 = hash、内容 = 输入文件、CORS 允许运营 Origin。
可重跑：默认语言已是 en 跳过；命名空间已存在跳过；key 无变化跳过确认；翻译未变化计为 unchanged；与当前发布无差异且 READY 时跳过发布。
网关 / BFF 超时（502 / 503 / 504）后 Platform 可能已完成写入：脚本等待后重新预览或核对发布记录，不盲目重复写。
产物：运行记录 I18N7_STATE/ops-bundles-<时间>.json（0600）；manifest 快照 release/i18n-bundles/published/。

用法：
  python3 -I ops_bundles.py --dry-run     只核对本机输入与计划（不登录、不调用任何 mor-test 接口）
  python3 -I ops_bundles.py               阶段 7 第 ② 步执行（写数据）
"""
import argparse
import hashlib
import json
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True  # 不在发布包与前例目录留下 __pycache__
sys.path.insert(0, str(Path(__file__).resolve().parent))
import opsi18n_common as o  # noqa: E402

c = o.c
RELEASE_NOTE = "运营控制台全量多语言（2026-10-23 阶段 7）"


def check_inputs():
    files = o.load_bundles()
    for locale, name in (("en", o.EN_FILE), ("zh-CN", o.ZH_FILE)):
        data, doc, sum_ok = files[locale]
        c.check("输入", f"{name} SHA-256 与 SHA256SUMS 一致", sum_ok)
        fmt = "code-mor-i18n-source/1" if locale == "en" else "code-mor-i18n-translation/1"
        c.check("输入", f"{name} 格式 {fmt}、应用 {o.APP}" + ("" if locale == "en" else "、语言 zh-CN"),
                doc.get("format") == fmt and doc.get("application") == o.APP and (locale == "en" or doc.get("locale") == "zh-CN"))
        c.check("输入", f"{name} {len(data) / 1024:.1f} KiB ≤ 2 MiB（导入上限）", len(data) <= o.MAX_FILE)
        c.check("输入", f"{name} 命名空间 = 21 个（{len(doc['namespaces'])}）", sorted(doc["namespaces"]) == o.ALL_NAMESPACES,
                sorted(set(doc["namespaces"]) ^ set(o.ALL_NAMESPACES)))
    en, zh = files["en"][1], files["zh-CN"][1]
    source, translation = o.flat(en, "en"), o.flat(zh, "zh-CN")
    bad_keys = [k for k, (ns, _) in source.items() if not o.KEY_CODE.fullmatch(k) or not k.startswith(ns + ".")]
    c.check("输入", f"{len(source)} 个 key 均符合 Platform KEY_CODE 且以所在命名空间开头", not bad_keys, bad_keys[:5])
    c.check("输入", "命名空间编码符合 Platform NAMESPACE_CODE", all(o.NAMESPACE_CODE.fullmatch(ns) for ns in en["namespaces"]))
    too_long = [k for k, (_, t) in {**source, **translation}.items() if not t or len(t) > o.MAX_TEXT]
    c.check("输入", "全部 en 基准与 zh-CN 翻译非空且 ≤ 8000 字符", not too_long, too_long[:5])
    c.check("输入", f"zh-CN 覆盖 {len(set(source) & set(translation))}/{len(source)}（应用阈值 100%）、无多余 key",
            set(source) == set(translation), sorted(set(source) ^ set(translation))[:5])
    c.check("输入", "ICU 校验已离线通过（evidence/icu-validate.txt：sourceProblems=0 translationProblems=0）",
            "sourceProblems=0 translationProblems=0" in (o.RELEASE / "evidence/icu-validate.txt").read_text())
    names = dict(o.NEW_NAMESPACES)
    c.check("输入", "19 个新命名空间名称 ≤ 64 字符、与文件命名空间互补", len(names) == 19 and all(len(v) <= 64 for v in names.values())
            and sorted(list(names) + o.EXISTING_NAMESPACES) == o.ALL_NAMESPACES)
    return files


def set_default_locale(token, record):
    status, detail = o.application(token)
    app = detail.get("application") or {}
    if not c.check("准备", f"{o.APP} 存在且 ENABLED（{status}）", status == 200 and app.get("status") == "ENABLED", detail):
        raise SystemExit(1)
    record["applicationBefore"] = {k: app.get(k) for k in ("defaultLocale", "requiredLocales", "coverageThresholdPercent", "version")}
    if app.get("defaultLocale") == "en":
        c.check("②a", "默认语言已是 en，跳过", True)
        return
    status, saved, _, _ = c.ops(token, "PUT", f"/applications/{o.APP}", {
        "expectedVersion": app["version"], "applicationName": app["applicationName"], "defaultLocale": "en",
        "requiredLocales": app["requiredLocales"], "coverageThresholdPercent": app["coverageThresholdPercent"]})
    c.check("②a", f"默认语言 {app.get('defaultLocale')} → en（{status}；必须语言 {app.get('requiredLocales')}、阈值 "
                   f"{app.get('coverageThresholdPercent')}% 不变）", status == 200 and saved.get("defaultLocale") == "en"
            and saved.get("requiredLocales") == app["requiredLocales"], saved)
    if status != 200:
        raise SystemExit(1)


def ensure_namespaces(token, record):
    status, listed, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/namespaces")
    existing = {item["namespaceCode"] for item in listed.get("items", [])} if status == 200 else set()
    created = []
    for order, (code, name) in enumerate(o.NEW_NAMESPACES, start=3):
        if code in existing:
            continue
        status, body, _, _ = c.ops(token, "POST", f"/applications/{o.APP}/namespaces", {
            "namespaceCode": code, "namespaceName": name, "description": RELEASE_NOTE, "sortOrder": order * 10})
        c.check("②b", f"新建命名空间 {code}（{status}）", status in (200, 201), body)
        created.append(code)
    record["createdNamespaces"] = created
    status, listed, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/namespaces")
    codes = sorted(item["namespaceCode"] for item in listed.get("items", []))
    c.check("②b", f"命名空间 21 个齐全（新建 {len(created)}）", codes == o.ALL_NAMESPACES, sorted(set(codes) ^ set(o.ALL_NAMESPACES)))
    if codes != o.ALL_NAMESPACES:
        raise SystemExit(1)


def preview(token, file_id):
    status, body, not_ready = c.with_file_ready(
        lambda: c.ops(token, "POST", f"/applications/{o.APP}/key-imports/previews", {"fileId": file_id}))
    return status, body, not_ready


def import_keys_file(token, data, record):
    completed = c.upload_json(token, "KEY_IMPORT", o.EN_FILE, data)
    record.setdefault("uploads", []).append({"purpose": "KEY_IMPORT", "file": o.EN_FILE, **completed})
    file_id = completed["fileId"]
    for attempt in range(6):
        status, plan, not_ready = preview(token, file_id)
        if status in o.TRANSIENT:
            time.sleep(10)
            continue
        counts = {k: len(plan.get(k) or []) for k in ("added", "sourceChanged", "deprecated", "restored")}
        ok = status == 200 and plan.get("importable") and not plan.get("missingNamespaces") and not plan.get("problems") \
            and counts["deprecated"] == 0
        c.check("②c", f"key 导入预览（第 {attempt + 1} 次）：{counts}、不变 {plan.get('unchangedCount')}", ok,
                {k: plan.get(k) for k in ("code", "detail", "missingNamespaces", "problems")} | {"deprecated": (plan.get("deprecated") or [])[:5]})
        if not ok:
            raise SystemExit(1)
        record["keyPreview"] = {**counts, "unchanged": plan.get("unchangedCount"), "sawFileNotReady": not_ready}
        if not (counts["added"] or counts["sourceChanged"] or counts["restored"]):
            c.check("②c", "key 与线上一致（8523 个 ACTIVE），无需确认", plan.get("unchangedCount") == len(o.flat(json.loads(data), "en")))
            return
        status, result = o.retry_transient(lambda: c.ops(token, "POST", f"/applications/{o.APP}/key-imports",
                                                         {"fileId": file_id, "previewDigest": plan["previewDigest"]}), "确认 key 导入", attempts=1)
        if status == 200:
            c.check("②c", f"key 导入确认：新增 {result.get('addedCount')}、变更 {result.get('sourceChangedCount')}", True)
            record["keyImport"] = result
            continue  # 再预览一次，确认已无差异
        if status == 409:  # 预览过期 / 锁占用：上一次确认可能仍在提交，等待后重新预览
            print(f"      确认 409 {result.get('code')}，10 秒后重新预览", flush=True)
            time.sleep(10)
            continue
        if status in o.TRANSIENT or status == 599:
            time.sleep(15)
            continue
        c.check("②c", f"key 导入确认（{status}）", False, result)
        raise SystemExit(1)
    c.check("②c", "key 导入在 6 次预览内收敛", False)
    raise SystemExit(1)


BATCH_LIMIT = 3000  # 每次确认新增 key 上限：Platform Snowflake 每毫秒序列 4096 用尽即抛 ID_GENERATION_ERROR（不等下一毫秒）


def cumulative_batches(full_doc, existing_codes):
    """按命名空间累积切分：每批包含此前全部 key + 若干新命名空间，新增不超过 BATCH_LIMIT；最后一批即全集，任何一批都不会废弃 key。"""
    namespaces = list(full_doc["namespaces"].items())
    # 含已在线 key 的命名空间放在最前，保证每一批都包含它们（否则这些 key 会被标为废弃）
    namespaces.sort(key=lambda item: 0 if any(k in existing_codes for k in item[1]) else 1)
    batches, included, added = [], {}, 0
    for ns, entries in namespaces:
        new = sum(1 for k in entries if k not in existing_codes)
        if included and added + new > BATCH_LIMIT:
            batches.append(dict(included)); added = 0
        included[ns] = entries; added += new
    batches.append(dict(included))
    return [json.dumps({**{k: v for k, v in full_doc.items() if k != "namespaces"}, "namespaces": b}, ensure_ascii=False, indent=2).encode("utf-8") + b"\n"
            for b in batches]


def import_keys(token, data, record):
    full = json.loads(data)
    completed = c.upload_json(token, "KEY_IMPORT", o.EN_FILE, data)
    status, plan, _ = preview(token, completed["fileId"])
    if status != 200:
        c.check("②c", f"全集预览（{status}）", False, plan)
        raise SystemExit(1)
    added = {item["keyCode"] for item in plan.get("added") or []}
    existing = {k for entries in full["namespaces"].values() for k in entries} - added
    c.check("②c", f"全集预览：新增 {len(added)}、已在线 {len(existing)}、废弃 {len(plan.get('deprecated') or [])}", not plan.get("deprecated"))
    batches = cumulative_batches(full, existing)
    c.check("②c", f"key 导入累积分批 {len(batches)} 批（每批新增 ≤ {BATCH_LIMIT}，最后一批为全集）", batches[-1] and json.loads(batches[-1])["namespaces"] == full["namespaces"])
    record["keyImportBatches"] = [sorted(json.loads(b)["namespaces"]) for b in batches]
    for i, batch in enumerate(batches, 1):
        print(f"      第 {i}/{len(batches)} 批：{sum(len(v) for v in json.loads(batch)['namespaces'].values())} key", flush=True)
        import_keys_file(token, batch, record)


def import_translations(token, doc_zh, record):
    totals = {"updated": 0, "unchanged": 0, "empty": 0}
    for namespace in o.ALL_NAMESPACES:
        part = o.translation_file(doc_zh, namespace)
        completed = c.upload_json(token, "TRANSLATION_IMPORT", f"{o.APP}.zh-CN.{namespace}.json", part)
        record.setdefault("uploads", []).append({"purpose": "TRANSLATION_IMPORT", "namespace": namespace, **completed})
        expected = len(doc_zh["namespaces"][namespace])
        result = {}
        for attempt in range(6):
            status, result, _ = c.with_file_ready(lambda: c.ops(token, "POST", f"/applications/{o.APP}/translations/imports",
                                                                {"fileId": completed["fileId"], "locale": "zh-CN"}))
            if status == 200:
                break
            if status in o.TRANSIENT or (status == 409 and result.get("code") != "I18N_IMPORT_FILE_NOT_READY"):
                print(f"      {namespace} 导入 {status} {result.get('code')}，10 秒后重试（Platform 可能已完成，重试计为未变化）", flush=True)
                time.sleep(10)
                continue
            break
        ok = status == 200 and result.get("emptyCount") == 0 and result.get("updatedCount", 0) + result.get("unchangedCount", 0) == expected
        c.check("②d", f"zh-CN {namespace}：写入 {result.get('updatedCount')}、不变 {result.get('unchangedCount')}（文件 {expected}）", ok, result)
        if not ok:
            raise SystemExit(1)
        for key, name in (("updated", "updatedCount"), ("unchanged", "unchangedCount"), ("empty", "emptyCount")):
            totals[key] += result.get(name, 0)
    record["translationImports"] = totals
    c.check("②d", f"zh-CN 合计：写入 {totals['updated']}、不变 {totals['unchanged']}", totals["empty"] == 0)


def publish(token, record):
    status, precheck, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/release-precheck")
    summary = precheck.get("changeSummary") or {}
    c.check("②e", f"预检可发布（缺口 {precheck.get('missingTotal')}，下一发布号 {precheck.get('nextReleaseNo')}，"
                  f"相对 #{summary.get('baseReleaseNo')} 新增 key {summary.get('addedKeyCount')}）",
            status == 200 and precheck.get("publishable") and precheck.get("missingTotal") == 0,
            {k: precheck.get(k) for k in ("publishable", "missingTotal", "problems", "coverage")})
    if not (status == 200 and precheck.get("publishable")):
        raise SystemExit(1)
    record["precheck"] = {k: precheck.get(k) for k in ("nextReleaseNo", "missingTotal", "coverage", "changeSummary")}
    current = o.current_release(token)
    unchanged = (summary.get("baseReleaseNo") is not None and not summary.get("addedKeyCount") and not summary.get("deprecatedKeyCount")
                 and not summary.get("sourceChangedKeyCount") and not any(t.get("changedCount") for t in summary.get("translationChanges") or []))
    if unchanged and current.get("status") == "READY":
        c.check("②e", f"与当前发布 #{current.get('releaseNo')} 无差异，跳过发布", True)
        record["releaseNo"] = current.get("releaseNo")
        return current.get("releaseNo")
    before = max([r.get("releaseNo", 0) for r in o.releases(token)] or [0])
    status, release = o.retry_transient(lambda: c.ops(token, "POST", f"/applications/{o.APP}/releases", {"releaseNote": RELEASE_NOTE}),
                                        "发布", attempts=1)
    release_no = release.get("releaseNo") if status in (200, 201) else None
    if release_no is None and status in o.TRANSIENT + (599,):
        for _ in range(12):  # 超时后核对发布记录：发布在 Platform 锁内生成语言包，可能已完成
            time.sleep(10)
            newer = [r for r in o.releases(token) if r.get("releaseNo", 0) > before]
            if newer:
                release_no = max(r["releaseNo"] for r in newer)
                break
    c.check("②e", f"发布 #{release_no}（{status} {release.get('status')}）", release_no is not None, release)
    if release_no is None:
        raise SystemExit(1)
    started = time.time()
    detail = c.wait_release(token, o.APP, release_no, timeout=600)
    progress = detail.get("checkProgress") or {}
    c.check("②e", f"#{release_no} 检查后上线 READY 且为当前（{time.time() - started:.0f} 秒，语言包 {progress.get('available')}/{progress.get('total')}）",
            detail.get("release", {}).get("status") == "READY" and detail["release"].get("current")
            and progress.get("available") == progress.get("total") == len(o.ALL_NAMESPACES) * 2,
            {"release": detail.get("release"), "checkProgress": progress})
    record["releaseNo"] = release_no
    for bundle in detail.get("bundles") or []:
        file_id = bundle["fileAddress"].rsplit("/", 1)[-1]
        status, meta, _, _ = c.raw(token, "GET", f"/storage-admin/v1/files/{file_id}")
        c.check("存储", f"#{release_no} {bundle['locale']}/{bundle['namespaceCode']} 在 MINIO/mor-platform-public、PUBLIC、platform-support",
                status == 200 and meta.get("instanceCode") == "MINIO" and meta.get("bucketName") == "mor-platform-public"
                and meta.get("accessLevel") == "PUBLIC" and meta.get("sourceSystem") == "platform-support",
                {k: meta.get(k) for k in ("instanceCode", "bucketName", "accessLevel", "sourceSystem")})
    return release_no


def verify_manifest(release_no, files, record):
    expected = {"en": files["en"][1]["namespaces"], "zh-CN": files["zh-CN"][1]["namespaces"]}
    out = o.BUNDLE_DIR / "published"
    out.mkdir(exist_ok=True)
    for locale in ("en", "zh-CN"):
        status, manifest, text, headers = c.raw(None, "GET", f"?locale={locale}", prefix=c.OPS_MANIFEST, base=c.OPS, key=False)
        bundles = manifest.get("bundles") or {}
        c.check("②f", f"匿名 manifest ?locale={locale}：200、#{manifest.get('releaseNo')}、默认 {manifest.get('defaultLocale')}、"
                       f"语言 {[l.get('code') for l in manifest.get('locales', [])]}",
                status == 200 and manifest.get("releaseNo") == release_no and manifest.get("defaultLocale") == "en"
                and {l["code"] for l in manifest.get("locales", [])} == {"en", "zh-CN"}, {"status": status, "body": text[:300]})
        (out / f"{o.APP}-r{release_no}-manifest-{locale}.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n")
        for loc, per_ns in sorted(bundles.items()):
            c.check("②f", f"manifest({locale}) {loc} 列出 21 个命名空间", sorted(per_ns) == o.ALL_NAMESPACES, sorted(set(per_ns) ^ set(o.ALL_NAMESPACES)))
        record.setdefault("manifestHeaders", {})[locale] = {k: v for k, v in headers.items() if k.lower() in ("cache-control", "etag")}
    _, manifest, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=c.OPS_MANIFEST, base=c.OPS, key=False)
    for loc in ("en", "zh-CN"):
        for namespace, bundle in sorted((manifest.get("bundles") or {}).get(loc, {}).items()):
            st, body, hdrs = c.public_get(bundle["url"], origin=c.OPS)
            want = {k: o.text_of(v) for k, v in expected[loc][namespace].items()} if loc == "en" else expected[loc][namespace]
            try:
                content_ok = json.loads(body) == want
            except ValueError:
                content_ok = False
            c.check("②f", f"{loc}/{namespace} 公开地址匿名 200、摘要 = hash、内容 = 输入文件（{len(want)} key）、CORS 允许运营 Origin",
                    st == 200 and hashlib.sha256(body).hexdigest() == bundle["hash"] and content_ok
                    and bundle["url"].startswith(c.PUBLIC_BASE + f"/i18n/{o.APP}/") and hdrs.get("access-control-allow-origin") == c.OPS,
                    {"status": st, "url": bundle["url"], "acao": hdrs.get("access-control-allow-origin")})
            record.setdefault("bundles", []).append({"locale": loc, "namespace": namespace, "url": bundle["url"], "hash": bundle["hash"],
                                                     "bytes": len(body), "cacheControl": hdrs.get("cache-control")})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    files = check_inputs()
    if args.dry_run:
        en_keys = sum(len(v) for v in files["en"][1]["namespaces"].values())
        print(f"计划：{o.APP} 默认语言 → en；新建 {len(o.NEW_NAMESPACES)} 个命名空间 {[n for n, _ in o.NEW_NAMESPACES]}；"
              f"KEY_IMPORT {o.EN_FILE}（{en_keys} key）；TRANSLATION_IMPORT zh-CN 按命名空间 {len(o.ALL_NAMESPACES)} 个文件；"
              f"预检 → 发布 → READY（语言包 {len(o.ALL_NAMESPACES) * 2} 个）；manifest {c.OPS}{c.OPS_MANIFEST}")
        for namespace in o.ALL_NAMESPACES:
            part = o.translation_file(files["zh-CN"][1], namespace)
            c.check("干跑", f"zh-CN 切分文件 {namespace}：{len(files['zh-CN'][1]['namespaces'][namespace])} key、{len(part) / 1024:.1f} KiB",
                    len(part) <= o.MAX_FILE and json.loads(part)["namespaces"] == {namespace: files["zh-CN"][1]["namespaces"][namespace]})
        return c.summary("运营控制台语言包（干跑，未调用任何接口）")
    record = {"startedAt": c.now_iso(), "app": o.APP}
    name = f"ops-bundles-{record['startedAt'].replace(':', '')}.json"
    token = c.admin_token()
    try:
        set_default_locale(token, record)
        ensure_namespaces(token, record)
        import_keys(token, files["en"][0], record)
        import_translations(token, files["zh-CN"][1], record)
        release_no = publish(token, record)
        verify_manifest(release_no, files, record)
    finally:
        record["finishedAt"] = c.now_iso()
        print("运行记录：", c.save(name, record))
    return c.summary("运营控制台语言包")


if __name__ == "__main__":
    sys.exit(main())
