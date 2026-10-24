"""运营控制台文件上传入口阶段 7 第 ⑦ 步：operations-console 新增 69 个 key 并发布语言包（经 Gateway 走真实运营接口；做法沿用
operations-控制台多语言迁移的 ops_bundles.py，只增不改）。

  0. 只读核对线上（SQL）：命名空间 21 个已存在；线上全部 ACTIVE key 都在输入中、英文基准 / 说明 / 命名空间与输入相同，zh-CN 翻译与输入相同；
     输入只多 file.upload.* 与两个 nav.resource.OPERATIONS_FILE_UPLOAD*。任何一处不同即停止（不能用导入覆盖线上修改）。
     记下全部已有 zh-CN 翻译的 (key, 文案, 版本) 快照。
  1. KEY_IMPORT：上传 i18n-bundles/operations-console.en.json（全集；Platform 把文件视为应用完整 key 集合）→ 预览：新增 = 上述 69 个、
     基准变更 0、废弃 0、恢复 0 → 确认 → 再预览为全部不变。新增 69 ≤ 每批 3000（Snowflake 每毫秒序列上限），一批完成。
  2. TRANSLATION_IMPORT：只含 69 个新 key 的 zh-CN 文件（file、nav 两个命名空间；Platform 翻译导入只写文件中出现的 key）→ 写入 69、不变 0。
  3. 复核：已有翻译快照逐条不变（文案与版本都相同）。
  4. 预检 publishable、缺口 0、相对当前发布只新增 69 个 key → 发布 → READY 且为当前（语言包 42/42），文件记录在 MINIO/mor-platform-public。
  5. 匿名 manifest（en、zh-CN）各 21 个命名空间；逐个不带凭据读取语言包：摘要 = hash、内容 = 输入文件、CORS 允许运营 Origin。
可重跑：key 无变化跳过确认；翻译未变化计为 unchanged；与当前发布无差异且 READY 时跳过发布。502 / 503 / 504 后重新预览或核对发布记录，不盲目重复写。
产物：运行记录 I18N7_STATE/fu-bundles-<时间>.json（0600，默认 ~/Code-MoR-backups/fileupload-stage7）；manifest 快照 release/i18n-bundles/published/。

用法：
  python3 -I fu_bundles.py --dry-run     只核对本机输入与计划（不登录、不调用任何 mor-test 接口、不执行 SQL）
  python3 -I fu_bundles.py               阶段 7 执行（写数据）
"""
import argparse
import hashlib
import json
import os
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/fileupload-stage7"))
sys.path.insert(0, str(HERE))
import fu7_common as f  # noqa: E402  （固定 MOR_ROOT、PB7_STATE；SQL 用其 sql_json）

sys.path.insert(0, str(f.ROOT / "requirements/20261023/operations-控制台多语言迁移/release/acceptance"))
import opsi18n_common as o  # noqa: E402  （i18n7_common 的登录、上传、导入与发布帮助函数）

c = o.c
o.BUNDLE_DIR = c.BUNDLE_DIR = f.RELEASE / "i18n-bundles"  # 本发布包的输入，不用前例目录
RELEASE_NOTE = "运营控制台文件上传入口（2026-10-24 阶段 7）"
NEW_NAV = ("nav.resource.OPERATIONS_FILE_UPLOAD", "nav.resource.OPERATIONS_FILE_UPLOAD_CREATE")
BATCH_LIMIT = 3000


def is_expected_new(key):
    return key.startswith("file.upload.") or key in NEW_NAV


def check_inputs():
    files = o.load_bundles()
    for locale, name in (("en", o.EN_FILE), ("zh-CN", o.ZH_FILE)):
        data, doc, sum_ok = files[locale]
        c.check("输入", f"{name} SHA-256 与 SHA256SUMS 一致", sum_ok)
        fmt = "code-mor-i18n-source/1" if locale == "en" else "code-mor-i18n-translation/1"
        c.check("输入", f"{name} 格式 {fmt}、应用 {o.APP}" + ("" if locale == "en" else "、语言 zh-CN"),
                doc.get("format") == fmt and doc.get("application") == o.APP and (locale == "en" or doc.get("locale") == "zh-CN"))
        c.check("输入", f"{name} {len(data) / 1024:.1f} KiB ≤ 2 MiB（导入上限）", len(data) <= o.MAX_FILE)
        c.check("输入", f"{name} 命名空间 = 21 个", sorted(doc["namespaces"]) == o.ALL_NAMESPACES, sorted(set(doc["namespaces"]) ^ set(o.ALL_NAMESPACES)))
    source, translation = o.flat(files["en"][1], "en"), o.flat(files["zh-CN"][1], "zh-CN")
    bad = [k for k, (ns, _) in source.items() if not o.KEY_CODE.fullmatch(k) or not k.startswith(ns + ".")]
    c.check("输入", f"{len(source)} 个 key 均符合 Platform KEY_CODE 且以所在命名空间开头", not bad, bad[:5])
    long = [k for k, (_, t) in {**source, **translation}.items() if not t or len(t) > o.MAX_TEXT]
    c.check("输入", "全部 en 基准与 zh-CN 翻译非空且 ≤ 8000 字符", not long, long[:5])
    c.check("输入", f"zh-CN 覆盖 {len(set(source) & set(translation))}/{len(source)}、无多余 key", set(source) == set(translation))
    c.check("输入", "ICU 校验已离线通过（evidence/icu-validate.txt：sourceProblems=0 translationProblems=0）",
            "sourceProblems=0 translationProblems=0" in (f.RELEASE / "evidence/icu-validate.txt").read_text())
    expected = sorted(k for k in source if is_expected_new(k))
    c.check("输入", f"本需求新增 key：file.upload.* {len(expected) - 2} 个 + nav.resource 2 个 = {len(expected)}", len(expected) == 69, len(expected))
    return files, expected


def live_state():
    """只读 SQL：应用、命名空间、全部 key 与 zh-CN 翻译（含版本）。"""
    app = f.sql_rows("SELECT application_id FROM t_i18n_application WHERE application_code = 'operations-console'")
    app_id = app[0][0]
    rows = f.sql_json("SELECT JSON_OBJECT('k', k.key_code, 's', k.source_text, 'd', k.description, 'st', k.status, 'ns', n.namespace_code, "
                      "'zh', t.text, 'zv', t.version) FROM t_i18n_key k JOIN t_i18n_namespace n ON n.namespace_id = k.namespace_id "
                      "LEFT JOIN t_i18n_translation t ON t.key_id = k.key_id AND t.locale_code = 'zh-CN' "
                      f"WHERE k.application_id = {app_id}")
    namespaces = sorted(r[0] for r in f.sql_rows(f"SELECT namespace_code FROM t_i18n_namespace WHERE application_id = {app_id}"))
    return namespaces, {r["k"]: r for r in rows}


def verify_add_only(files, expected, record):
    source, translation = o.flat(files["en"][1], "en"), o.flat(files["zh-CN"][1], "zh-CN")
    descriptions = {k: (None if isinstance(v, str) else v.get("description")) for d in files["en"][1]["namespaces"].values() for k, v in d.items()}
    namespaces, live = live_state()
    active = {k: r for k, r in live.items() if r["st"] == "ACTIVE"}
    ok = c.check("⓪", "命名空间 21 个已存在（不新建）", namespaces == o.ALL_NAMESPACES, sorted(set(namespaces) ^ set(o.ALL_NAMESPACES)))
    ok &= c.check("⓪", f"线上 {len(active)} 个 ACTIVE key 全部在输入中（废弃 0）", all(k in source for k in active), [k for k in active if k not in source][:5])
    changed = [k for k, r in active.items() if k in source and (source[k][0], source[k][1], descriptions[k]) != (r["ns"], r["s"], r["d"])]
    ok &= c.check("⓪", "已有 key 的英文基准、说明、命名空间与输入相同", not changed, changed[:5])
    zh_changed = [k for k, r in active.items() if r["zh"] is not None and translation.get(k, (None, None))[1] != r["zh"]]
    ok &= c.check("⓪", "已有 zh-CN 翻译与输入相同（导入不会覆盖线上修改）", not zh_changed, zh_changed[:5])
    new = sorted(set(source) - set(live))
    ok &= c.check("⓪", f"输入相对线上新增 {len(new)} 个 = 本需求的 69 个", new == expected, sorted(set(new) ^ set(expected))[:5])
    record["liveBefore"] = {"activeKeys": len(active), "translations": sum(1 for r in active.values() if r["zh"] is not None)}
    if not ok:
        raise SystemExit("线上与输入不一致，停止（不导入、不发布）")
    return {k: (r["zh"], r["zv"]) for k, r in active.items() if r["zh"] is not None}


def import_keys(token, data, expected, record):
    completed = c.upload_json(token, "KEY_IMPORT", o.EN_FILE, data)
    record.setdefault("uploads", []).append({"purpose": "KEY_IMPORT", "file": o.EN_FILE, **completed})
    file_id = completed["fileId"]
    for attempt in range(6):
        status, plan, not_ready = c.with_file_ready(
            lambda: c.ops(token, "POST", f"/applications/{o.APP}/key-imports/previews", {"fileId": file_id}))
        if status in o.TRANSIENT:
            time.sleep(10)
            continue
        added = sorted(item["keyCode"] for item in plan.get("added") or [])
        counts = {k: len(plan.get(k) or []) for k in ("added", "sourceChanged", "deprecated", "restored")}
        first = attempt == 0 or "keyImport" not in record
        ok = status == 200 and plan.get("importable") and not plan.get("missingNamespaces") and not plan.get("problems") \
            and counts["sourceChanged"] == counts["deprecated"] == counts["restored"] == 0 and (added == expected or not added)
        c.check("①", f"key 导入预览（第 {attempt + 1} 次）：{counts}、不变 {plan.get('unchangedCount')}", ok,
                {k: plan.get(k) for k in ("code", "detail", "missingNamespaces", "problems")} | {"unexpected": sorted(set(added) - set(expected))[:5]})
        if not ok:
            raise SystemExit(1)
        record.setdefault("keyPreviews", []).append({**counts, "unchanged": plan.get("unchangedCount"), "sawFileNotReady": not_ready})
        if not added:
            c.check("①", f"key 与输入一致（{plan.get('unchangedCount')} 个不变），无需确认", plan.get("unchangedCount") == len(o.flat(json.loads(data), "en")))
            return
        if first and len(added) > BATCH_LIMIT:
            c.check("①", f"新增 {len(added)} 超过每批 {BATCH_LIMIT}", False)
            raise SystemExit(1)
        status, result = o.retry_transient(lambda: c.ops(token, "POST", f"/applications/{o.APP}/key-imports",
                                                         {"fileId": file_id, "previewDigest": plan["previewDigest"]}), "确认 key 导入", attempts=1)
        if status == 200:
            c.check("①", f"key 导入确认：新增 {result.get('addedCount')}、变更 {result.get('sourceChangedCount')}",
                    result.get("addedCount") == len(expected) and not result.get("sourceChangedCount"), result)
            record["keyImport"] = result
            continue
        if status in (409,) + o.TRANSIENT + (599,):
            print(f"      确认 {status} {result.get('code')}，15 秒后重新预览（上一次确认可能已提交）", flush=True)
            time.sleep(15)
            continue
        c.check("①", f"key 导入确认（{status}）", False, result)
        raise SystemExit(1)
    c.check("①", "key 导入在 6 次预览内收敛", False)
    raise SystemExit(1)


def new_translation_file(doc_zh, expected):
    namespaces = {}
    for key in expected:
        ns = key.split(".", 1)[0]
        namespaces.setdefault(ns, {})[key] = doc_zh["namespaces"][ns][key]
    part = {k: doc_zh[k] for k in ("format", "application", "locale")} | {"namespaces": namespaces}
    return (json.dumps(part, ensure_ascii=False, indent=2) + "\n").encode()


def import_translations(token, doc_zh, expected, record):
    part = new_translation_file(doc_zh, expected)
    completed = c.upload_json(token, "TRANSLATION_IMPORT", f"{o.APP}.zh-CN.file-upload.json", part)
    record.setdefault("uploads", []).append({"purpose": "TRANSLATION_IMPORT", **completed})
    result = {}
    for attempt in range(6):
        status, result, _ = c.with_file_ready(lambda: c.ops(token, "POST", f"/applications/{o.APP}/translations/imports",
                                                            {"fileId": completed["fileId"], "locale": "zh-CN"}))
        if status == 200:
            break
        if status in o.TRANSIENT or (status == 409 and result.get("code") != "I18N_IMPORT_FILE_NOT_READY"):
            print(f"      翻译导入 {status} {result.get('code')}，10 秒后重试（Platform 可能已完成，重试计为未变化）", flush=True)
            time.sleep(10)
            continue
        break
    ok = status == 200 and result.get("emptyCount") == 0 and result.get("updatedCount", 0) + result.get("unchangedCount", 0) == len(expected)
    c.check("②", f"zh-CN 新 key：写入 {result.get('updatedCount')}、不变 {result.get('unchangedCount')}（文件 {len(expected)}）", ok, result)
    record["translationImport"] = result
    if not ok:
        raise SystemExit(1)


def verify_unchanged(before, expected, record):
    _, live = live_state()
    changed = [k for k, (text, version) in before.items() if (live.get(k) or {}).get("zh") != text or (live.get(k) or {}).get("zv") != version]
    c.check("③", f"已有 {len(before)} 条 zh-CN 翻译文案与版本均未变化", not changed, changed[:5])
    missing = [k for k in expected if (live.get(k) or {}).get("st") != "ACTIVE" or (live.get(k) or {}).get("zh") is None]
    c.check("③", f"新增 {len(expected)} 个 key ACTIVE 且有 zh-CN 翻译", not missing, missing[:5])
    record["liveAfter"] = {"activeKeys": sum(1 for r in live.values() if r["st"] == "ACTIVE"), "unchangedTranslations": len(before) - len(changed)}


def publish(token, expected, record):
    status, precheck, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/release-precheck")
    summary = precheck.get("changeSummary") or {}
    c.check("④", f"预检可发布（缺口 {precheck.get('missingTotal')}，下一发布号 {precheck.get('nextReleaseNo')}，相对 #{summary.get('baseReleaseNo')} "
                 f"新增 key {summary.get('addedKeyCount')}、废弃 {summary.get('deprecatedKeyCount')}、基准变更 {summary.get('sourceChangedKeyCount')}）",
            status == 200 and precheck.get("publishable") and precheck.get("missingTotal") == 0 and not summary.get("deprecatedKeyCount")
            and not summary.get("sourceChangedKeyCount"), {k: precheck.get(k) for k in ("publishable", "missingTotal", "problems", "changeSummary")})
    if not (status == 200 and precheck.get("publishable")):
        raise SystemExit(1)
    record["precheck"] = {k: precheck.get(k) for k in ("nextReleaseNo", "missingTotal", "coverage", "changeSummary")}
    current = o.current_release(token)
    if not summary.get("addedKeyCount") and not any(t.get("changedCount") for t in summary.get("translationChanges") or []) \
            and current.get("status") == "READY" and summary.get("baseReleaseNo") == current.get("releaseNo"):
        c.check("④", f"与当前发布 #{current.get('releaseNo')} 无差异，跳过发布", True)
        record["releaseNo"] = current.get("releaseNo")
        return current.get("releaseNo")
    c.check("④", f"相对当前发布新增 key = {len(expected)}", summary.get("addedKeyCount") == len(expected), summary)
    before = max([r.get("releaseNo", 0) for r in o.releases(token)] or [0])
    status, release = o.retry_transient(lambda: c.ops(token, "POST", f"/applications/{o.APP}/releases", {"releaseNote": RELEASE_NOTE}),
                                        "发布", attempts=1)
    release_no = release.get("releaseNo") if status in (200, 201) else None
    if release_no is None and status in o.TRANSIENT + (599,):
        for _ in range(12):
            time.sleep(10)
            newer = [r for r in o.releases(token) if r.get("releaseNo", 0) > before]
            if newer:
                release_no = max(r["releaseNo"] for r in newer)
                break
    c.check("④", f"发布 #{release_no}（{status} {release.get('status')}）", release_no is not None, release)
    if release_no is None:
        raise SystemExit(1)
    detail = c.wait_release(token, o.APP, release_no, timeout=600)
    progress = detail.get("checkProgress") or {}
    c.check("④", f"#{release_no} READY 且为当前（语言包 {progress.get('available')}/{progress.get('total')}）",
            detail.get("release", {}).get("status") == "READY" and detail["release"].get("current")
            and progress.get("available") == progress.get("total") == len(o.ALL_NAMESPACES) * 2, {"release": detail.get("release"), "checkProgress": progress})
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
        c.check("⑤", f"匿名 manifest ?locale={locale}：200、#{manifest.get('releaseNo')}、默认 {manifest.get('defaultLocale')}",
                status == 200 and manifest.get("releaseNo") == release_no and manifest.get("defaultLocale") == "en", {"status": status, "body": text[:300]})
        (out / f"{o.APP}-r{release_no}-manifest-{locale}.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1) + "\n")
        for loc, per_ns in sorted((manifest.get("bundles") or {}).items()):
            c.check("⑤", f"manifest({locale}) {loc} 列出 21 个命名空间", sorted(per_ns) == o.ALL_NAMESPACES)
    _, manifest, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=c.OPS_MANIFEST, base=c.OPS, key=False)
    for loc in ("en", "zh-CN"):
        for namespace, bundle in sorted((manifest.get("bundles") or {}).get(loc, {}).items()):
            st, body, hdrs = c.public_get(bundle["url"], origin=c.OPS)
            want = {k: o.text_of(v) for k, v in expected[loc][namespace].items()} if loc == "en" else expected[loc][namespace]
            try:
                content_ok = json.loads(body) == want
            except ValueError:
                content_ok = False
            c.check("⑤", f"{loc}/{namespace} 公开地址匿名 200、摘要 = hash、内容 = 输入（{len(want)} key）、CORS 允许运营 Origin",
                    st == 200 and hashlib.sha256(body).hexdigest() == bundle["hash"] and content_ok
                    and hdrs.get("access-control-allow-origin") == c.OPS, {"status": st, "url": bundle["url"]})
            record.setdefault("bundles", []).append({"locale": loc, "namespace": namespace, "url": bundle["url"], "hash": bundle["hash"]})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    files, expected = check_inputs()
    if args.dry_run:
        source = o.flat(files["en"][1], "en")
        part = new_translation_file(files["zh-CN"][1], expected)
        print(f"计划：KEY_IMPORT {o.EN_FILE}（全集 {len(source)} key，新增 {len(expected)} ≤ {BATCH_LIMIT}，一批）；TRANSLATION_IMPORT 只含新 key 的 "
              f"zh-CN 文件 {len(part) / 1024:.1f} KiB（命名空间 {sorted(json.loads(part)['namespaces'])}）；预检 → 发布 → READY（语言包 "
              f"{len(o.ALL_NAMESPACES) * 2} 个）；manifest {c.OPS}{c.OPS_MANIFEST}")
        doc = json.loads(part)
        c.check("干跑", "翻译文件只含 69 个新 key，文案 = zh-CN 输入",
                sorted(k for d in doc["namespaces"].values() for k in d) == expected
                and all(doc["namespaces"][k.split(".", 1)[0]][k] == files["zh-CN"][1]["namespaces"][k.split(".", 1)[0]][k] for k in expected))
        c.check("干跑", "新增 key 均不在本需求以外的命名空间", {k.split(".", 1)[0] for k in expected} == {"file", "nav"})
        return c.summary("文件上传入口语言包（干跑，未调用任何接口）")
    record = {"startedAt": c.now_iso(), "app": o.APP}
    name = f"fu-bundles-{record['startedAt'].replace(':', '')}.json"
    try:
        before = verify_add_only(files, expected, record)
        token = c.admin_token()
        import_keys(token, files["en"][0], expected, record)
        import_translations(token, files["zh-CN"][1], expected, record)
        verify_unchanged(before, expected, record)
        release_no = publish(token, expected, record)
        verify_manifest(release_no, files, record)
    finally:
        record["finishedAt"] = c.now_iso()
        print("运行记录：", c.save(name, record))
    return c.summary("文件上传入口语言包")


if __name__ == "__main__":
    sys.exit(main())
