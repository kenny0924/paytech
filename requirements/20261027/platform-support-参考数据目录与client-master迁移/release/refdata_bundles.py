"""参考数据目录与 client-master 迁移 · mor-test 语言包发布（runbook 第 ⑪ 步）：client-console 与 operations-console 两个应用。
复制自切片 1 assets/harness/onboarding_bundles.py（mor-test 已用于 client #2、ops #9），复用多语言管理发布包的 i18n7_common
（经 Nginx → Gateway → ops-bff 调 Platform /localization-admin/v1、管理员登录、导入文件直传、只读 SQL）。与切片 1 的差别：
  a. 仓库源 = Web 源码 i18n（--web-root，缺省发布构建快照 Code-MoR-refdata-rel）+ release/i18n-bundles/<应用>.refdata.{en,zh-CN}.json
     （refdata 是 Platform 数据名称：行业 42 + 行业说明 42 + 行政区 47 + MCC 298 = 429 个 key，两个应用相同；不在 Web 源码里）。
  b. refdata 的 zh-CN：线上已有且与语言包不同的，以语言包为准（数据名称，不由运营改写）；其他命名空间仍是"线上已有且基准未变的不覆盖"。
  c. 新增第 4b 步"确认基准已变更的译文"：基准文本变了而译文不变时，翻译导入记为"不变"，不会把译文的依据修订推进到新基准
     （阶段 5 本机发现，见 verification/local-e2e/i18n_local.py rebase_unchanged）；这里用批量保存原文确认到当前基准（每批 ≤ 200），
     否则发布预检把它们算作缺口（覆盖率要求 100%）。计划中列出预计条数。
其余步骤、可重跑性与废弃 key 的门禁与切片 1 相同：
  0. 计划（只读 SQL，不登录）→ 1. 建命名空间（refdata、reference-data 等）→ 2. KEY_IMPORT（en 全集；废弃集合必须等于计划）
  → 3. TRANSLATION_IMPORT（zh-CN 增量）→ 4. 复核导入集合外的已有 zh-CN 未变 → 4b. 确认基准已变更的译文 → 5. 预检、发布、等 READY
  → 6. 匿名 manifest：命名空间数 = 仓库命名空间数；逐个语言包匿名可读、摘要 = hash。
缺省（不带 --apply）只做第 0 步：只读 SQL，打印计划并写导入文件到 --out；不登录、不调用任何写接口。--apply 执行 0—6（阶段 7，主会话）。
运行记录写 I18N7_STATE（缺省 ~/Code-MoR-backups/refdata-20261027-i18n，不含口令与令牌）。
用法：python3 -I refdata_bundles.py [--web-root <含两个 *-console-web 的工作区>] [--app client-console|operations-console]
                                   [--out <导入文件目录>] [--accept-deprecations] [--apply]
"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parent
MOR_ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/refdata-20261027-i18n"))
os.environ.setdefault("MOR_TEST_ENVIRONMENT_DIR", str(MOR_ROOT / "test-environment"))
sys.path.insert(0, str(MOR_ROOT / "requirements/20261022/platform-support-多语言管理/release/acceptance"))
import i18n7_common as c  # noqa: E402  （只读 SQL 不需要登录；写操作只在 --apply 中调用）

APPS = {"client-console": "client-console-web", "operations-console": "operations-console-web"}
MANIFEST = {"operations-console": (c.OPS_MANIFEST, c.OPS), "client-console": (c.CLIENT_MANIFEST, c.CLIENT)}
NAMESPACE_NAMES = {**c.NAMESPACE_NAMES, "onboarding-schema": "入驻采集表单", "onboarding-config": "入驻配置",
                   "client-admin": "Client 管理", "onboarding-code": "入驻码", "workbench": "审核工作台",
                   "refdata": "参考数据名称", "reference-data": "参考数据"}
REFDATA_NAMESPACE = "refdata"
REFDATA_BUNDLES = REQ / "release/i18n-bundles"
REBASE_BATCH = 200
NEW_NAMESPACE_SORT_START = 300
# 与 Platform LocalizationCodes / CreateNamespaceRequestBody 一致（2026-10-27 核对）
KEY_CODE = re.compile(r"(?=.{3,191}$)[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+")
NAMESPACE_CODE = re.compile(r"[a-z][a-z0-9-]{0,63}")
MAX_TEXT_LENGTH = 8000
MAX_DESCRIPTION_LENGTH = 512
MAX_NAMESPACE_NAME = 64
BATCH_LIMIT = 3000
TRANSIENT = (502, 503, 504)
RELEASE_NOTE = "参考数据目录与 client-master 迁移：参考数据页、行业两级与 MCC、币种字段、Platform 参考数据名称 refdata（2026-10-27）"


def text_of(value):
    return value if isinstance(value, str) else (value or {}).get("text")


# ---------- 仓库源 ----------

def read_sources(web_root, app):
    web = web_root / APPS[app]
    en = {}
    for file in sorted((web / "src/i18n/en").glob("*.json")):
        entries = json.loads(file.read_text(encoding="utf-8"))
        en[file.stem] = {k: entries[k] for k in sorted(entries)}
    zh = {}
    if (web / "i18n/zh-CN").is_dir():
        for file in sorted((web / "i18n/zh-CN").glob("*.json")):
            zh[file.stem] = json.loads(file.read_text(encoding="utf-8"))
    seed = web / f"i18n/{app}.zh-CN.seed.json"
    if seed.exists():
        doc = json.loads(seed.read_text(encoding="utf-8"))
        if doc.get("format") != "code-mor-i18n-translation/1" or doc.get("locale") != "zh-CN":
            raise SystemExit(f"{seed}: 不是 code-mor-i18n-translation/1 zh-CN")
        for ns, entries in (doc.get("namespaces") or {}).items():
            zh.setdefault(ns, {}).update(entries)
    ref_en = json.loads((REFDATA_BUNDLES / f"{app}.refdata.en.json").read_text(encoding="utf-8"))
    ref_zh = json.loads((REFDATA_BUNDLES / f"{app}.refdata.zh-CN.json").read_text(encoding="utf-8"))
    if ref_en.get("format") != "code-mor-i18n-source/1" or ref_zh.get("format") != "code-mor-i18n-translation/1" or ref_zh.get("locale") != "zh-CN":
        raise SystemExit(f"{app}: refdata 语言包格式不对")
    if REFDATA_NAMESPACE in en:
        raise SystemExit(f"{app}: Web 源码不应内置 {REFDATA_NAMESPACE}（数据名称只来自 Platform 语言包）")
    en[REFDATA_NAMESPACE] = dict(sorted(ref_en["namespaces"][REFDATA_NAMESPACE].items()))
    zh.setdefault(REFDATA_NAMESPACE, {}).update(ref_zh["namespaces"][REFDATA_NAMESPACE])
    problems = []
    if set(en[REFDATA_NAMESPACE]) != set(zh[REFDATA_NAMESPACE]):
        problems.append(f"refdata en / zh-CN key 集合不同：{len(en[REFDATA_NAMESPACE])} / {len(zh[REFDATA_NAMESPACE])}")
    for ns in sorted(set(en) | set(zh)):
        if not NAMESPACE_CODE.fullmatch(ns):
            problems.append(f"命名空间编码不符合 Platform 规则 [a-z][a-z0-9-]{{0,63}}: {ns}")
    for ns, entries in en.items():
        for key, value in entries.items():
            text = text_of(value) or ""
            if not key.startswith(ns + ".") or not KEY_CODE.fullmatch(key) or not text.strip():
                problems.append(f"en {ns}: {key}")
            if len(text) > MAX_TEXT_LENGTH:
                problems.append(f"en 文案超过 {MAX_TEXT_LENGTH} 字符: {key}")
            if isinstance(value, dict) and len(value.get("description") or "") > MAX_DESCRIPTION_LENGTH:
                problems.append(f"en 说明超过 {MAX_DESCRIPTION_LENGTH} 字符: {key}")
    for ns, entries in zh.items():
        for key, value in entries.items():
            if len(str(value or "")) > MAX_TEXT_LENGTH:
                problems.append(f"zh-CN 文案超过 {MAX_TEXT_LENGTH} 字符: {key}")
    return en, zh, problems


# ---------- 线上（只读 SQL） ----------

def _unescape_batch(value):
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t", "0": "\0"}.get(m.group(1), m.group(1)), value)


def live_state(app):
    rows = c.sql("mor_platform", f"SELECT application_id FROM t_i18n_application WHERE application_code='{app}' AND status='ENABLED'")
    if not rows:
        raise SystemExit(f"线上没有 ENABLED 的多语言应用 {app}")
    app_id = rows[0][0]
    namespaces = sorted(r[0] for r in c.sql("mor_platform", f"SELECT namespace_code FROM t_i18n_namespace WHERE application_id={app_id}"))
    max_sort = c.sql("mor_platform", f"SELECT IFNULL(MAX(sort_order), 0) FROM t_i18n_namespace WHERE application_id={app_id}")[0][0]
    keys = c.sql("mor_platform", "SELECT JSON_OBJECT('k', k.key_code, 's', k.source_text, 'st', k.status, 'ns', n.namespace_code, "
                                 "'zh', t.text, 'zv', t.version) FROM t_i18n_key k JOIN t_i18n_namespace n ON n.namespace_id=k.namespace_id "
                                 "LEFT JOIN t_i18n_translation t ON t.key_id=k.key_id AND t.locale_code='zh-CN' "
                                 f"WHERE k.application_id={app_id}")
    live = {r["k"]: r for r in (json.loads(_unescape_batch(row[0])) for row in keys)}
    current = c.sql("mor_platform", f"SELECT IFNULL(MAX(release_no), 0) FROM t_i18n_release WHERE application_id={app_id} AND status='READY'")[0][0]
    return namespaces, int(max_sort), live, int(current)


def outdated_translations(app):
    """zh-CN 译文的依据修订落后于 key 当前基准修订（ACTIVE key）：(key_code, text, version)。"""
    rows = c.sql("mor_platform", "SELECT JSON_OBJECT('k', k.key_code, 't', t.text, 'v', t.version) FROM t_i18n_translation t "
                                 "JOIN t_i18n_key k ON k.key_id = t.key_id JOIN t_i18n_application a ON a.application_id = k.application_id "
                                 f"WHERE a.application_code = '{app}' AND t.locale_code = 'zh-CN' AND k.status = 'ACTIVE' "
                                 "AND t.based_on_source_revision < k.source_revision")
    return [json.loads(_unescape_batch(row[0])) for row in rows]


# ---------- 计划 ----------

def plan_for(app, en, zh, live_namespaces, max_sort, live, current, live_outdated):
    source = {k: (ns, text_of(v)) for ns, entries in en.items() for k, v in entries.items()}
    active = {k: r for k, r in live.items() if r["st"] == "ACTIVE"}
    missing_in_repo = sorted(k for k in active if k not in source)
    added = sorted(k for k in source if k not in live)
    restored = sorted(k for k in source if k in live and live[k]["st"] != "ACTIVE")
    source_changed = sorted(k for k in source if k in active and (active[k]["ns"], active[k]["s"]) != source[k])
    no_live_zh = sorted(k for k in source if k in active and active[k]["zh"] is None)
    zh_flat = {k: v for entries in zh.values() for k, v in entries.items()}
    refdata_replaced = sorted(k for k in source if source[k][0] == REFDATA_NAMESPACE and k in active and active[k]["zh"] is not None
                              and str(zh_flat.get(k) or "") != active[k]["zh"])
    import_keys = sorted(set(added) | set(source_changed) | set(no_live_zh) | set(restored) | set(refdata_replaced))
    # 4b 预计：线上已过期的译文 + 本次基准变更但导入译文与线上相同的 key（导入记为"不变"，依据修订不推进）
    # 4b 只处理本次发布触及的 key（基准变更 key 与 refdata）；线上原有、与本需求无关的过期译文不动，单独列出。
    rebase_scope = sorted(set(source_changed) | {k for k in source if source[k][0] == REFDATA_NAMESPACE})
    rebase_expected = sorted((set(live_outdated) & set(rebase_scope)) | {k for k in source_changed if active[k]["zh"] is not None
                                                                          and str(zh_flat.get(k) or "") == active[k]["zh"]})
    outdated_untouched = sorted(set(live_outdated) - set(rebase_scope))
    missing_zh = sorted(k for k in import_keys if not str(zh_flat.get(k) or "").strip())
    new_namespaces = [ns for ns in en if ns not in live_namespaces]
    translation = {}
    for key in import_keys:
        if key in zh_flat and str(zh_flat[key]).strip():
            translation.setdefault(source[key][0], {})[key] = zh_flat[key]
    return {
        "app": app, "currentRelease": current, "liveNamespaces": len(live_namespaces), "repoNamespaces": len(en),
        "newNamespaces": [{"code": ns, "name": NAMESPACE_NAMES.get(ns, ns), "sort": max_sort + 10 * (i + 1) if max_sort else NEW_NAMESPACE_SORT_START + 10 * i}
                          for i, ns in enumerate(new_namespaces)],
        "repoKeys": len(source), "liveActiveKeys": len(active), "added": added, "restored": restored,
        "sourceChanged": source_changed, "missingInRepo": missing_in_repo, "zhImportKeys": len(import_keys),
        "zhMissingForImport": missing_zh, "translation": translation,
        "keepLiveZh": len([k for k in active if k not in import_keys and active[k]["zh"] is not None]),
        "refdataZhReplaced": refdata_replaced, "liveOutdatedZh": sorted(live_outdated), "rebaseExpected": rebase_expected,
        "rebaseScope": rebase_scope, "outdatedUntouched": outdated_untouched,
    }


def check_plan(p, problems, accept_deprecations):
    ok = True
    ok &= check(p, "仓库源校验（命名空间编码、key 规则与前缀、非空基准、文案 / 说明长度，均按 Platform 规则）", not problems, problems[:5])
    long_names = [ns["code"] for ns in p["newNamespaces"] if len(ns["name"]) > MAX_NAMESPACE_NAME or not 0 <= ns["sort"] <= 9999]
    ok &= check(p, "新命名空间名称 ≤ 64 字符、排序 0—9999", not long_names, long_names)
    if accept_deprecations and p["missingInRepo"]:
        check(p, f"线上 {len(p['missingInRepo'])} 个 ACTIVE key 不在仓库中，按 --accept-deprecations 计划废弃（见 plan.json deprecations）", True)
    else:
        ok &= check(p, f"线上 {p['liveActiveKeys']} 个 ACTIVE key 都在仓库中（否则 KEY_IMPORT 会废弃它们；确认废弃用 --accept-deprecations）",
                    not p["missingInRepo"], p["missingInRepo"][:10])
    ok &= check(p, f"新增 {len(p['added'])} < {BATCH_LIMIT}", len(p["added"]) < BATCH_LIMIT)
    ok &= check(p, f"导入集合 {len(p['added']) + len(p['sourceChanged'])}+ 个 key 都有仓库 zh-CN（缺 {len(p['zhMissingForImport'])}）",
                not p["zhMissingForImport"], p["zhMissingForImport"][:10])
    return ok


def check(p, name, ok, evidence=None):
    return c.check(p["app"], name, ok, evidence)


def write_files(out, app, en, p):
    out.mkdir(parents=True, exist_ok=True)
    doc_en = {"format": "code-mor-i18n-source/1", "application": app, "namespaces": en}
    doc_zh = {"format": "code-mor-i18n-translation/1", "application": app, "locale": "zh-CN", "namespaces": p["translation"]}
    data_en = (json.dumps(doc_en, ensure_ascii=False, indent=2) + "\n").encode()
    data_zh = (json.dumps(doc_zh, ensure_ascii=False, indent=2) + "\n").encode()
    names = (f"{app}.release.en.json", f"{app}.release.zh-CN.delta.json")
    (out / names[0]).write_bytes(data_en)
    (out / names[1]).write_bytes(data_zh)
    summary = {k: (len(v) if isinstance(v, list) else v) for k, v in p.items() if k != "translation"}
    summary["newNamespaces"] = p["newNamespaces"]
    summary["deprecations"] = p["missingInRepo"]
    summary["sha256"] = {names[0]: hashlib.sha256(data_en).hexdigest(), names[1]: hashlib.sha256(data_zh).hexdigest()}
    (out / f"{app}.release.plan.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1) + "\n")
    return data_en, data_zh, names, summary


# ---------- 写（--apply） ----------

def ensure_namespaces(token, p, record):
    status, listed, _, _ = c.ops(token, "GET", f"/applications/{p['app']}/namespaces")
    have = {item["namespaceCode"] for item in (listed or {}).get("items", [])}
    for ns in p["newNamespaces"]:
        if ns["code"] in have:
            check(p, f"命名空间 {ns['code']} 已存在，跳过", True)
            continue
        status, body, _, _ = c.ops(token, "POST", f"/applications/{p['app']}/namespaces",
                                   {"namespaceCode": ns["code"], "namespaceName": ns["name"], "description": RELEASE_NOTE, "sortOrder": ns["sort"]})
        if not check(p, f"新建命名空间 {ns['code']}（{ns['name']}，排序 {ns['sort']}）：{status}", status in (200, 201), body):
            raise SystemExit(1)
        record.setdefault("namespaces", []).append({"app": p["app"], "code": ns["code"], "status": status})


def import_keys(token, p, data_en, name, record):
    app = p["app"]
    completed = c.upload_json(token, "KEY_IMPORT", name, data_en)
    for attempt in range(6):
        status, preview, _ = c.with_file_ready(lambda: c.ops(token, "POST", f"/applications/{app}/key-imports/previews", {"fileId": completed["fileId"]}))
        if status in TRANSIENT:
            time.sleep(10)
            continue
        added = sorted(item["keyCode"] for item in preview.get("added") or [])
        changed = sorted(item["keyCode"] for item in preview.get("sourceChanged") or [])
        counts = {k: len(preview.get(k) or []) for k in ("added", "sourceChanged", "deprecated", "restored")}
        ok = status == 200 and preview.get("importable") and not preview.get("missingNamespaces") and not preview.get("problems") \
            and sorted(item["keyCode"] for item in preview.get("deprecated") or []) in (p["missingInRepo"], []) \
            and added in (p["added"], []) and changed in (p["sourceChanged"], [])
        if not check(p, f"key 导入预览（第 {attempt + 1} 次）：{counts}", ok,
                     {k: preview.get(k) for k in ("code", "detail", "missingNamespaces", "problems")}):
            raise SystemExit(1)
        if not added and not changed and not counts["restored"] and not counts["deprecated"]:
            check(p, f"key 与仓库一致（{preview.get('unchangedCount')} 个不变）", True)
            return
        status, result, _, _ = c.ops(token, "POST", f"/applications/{app}/key-imports",
                                     {"fileId": completed["fileId"], "previewDigest": preview["previewDigest"]})
        if status == 200:
            record.setdefault("keyImports", []).append({"app": app, **{k: result.get(k) for k in ("addedCount", "sourceChangedCount")}})
            continue
        if status in (409,) + TRANSIENT:
            time.sleep(15)
            continue
        check(p, f"key 导入确认（{status}）", False, result)
        raise SystemExit(1)
    check(p, "key 导入在 6 次预览内收敛", False)
    raise SystemExit(1)


def import_translations(token, p, data_zh, name, record):
    count = sum(len(v) for v in p["translation"].values())
    if not count:
        check(p, "无需导入 zh-CN", True)
        return
    completed = c.upload_json(token, "TRANSLATION_IMPORT", name, data_zh)
    status, result = None, {}
    for _ in range(6):
        status, result, _ = c.with_file_ready(lambda: c.ops(token, "POST", f"/applications/{p['app']}/translations/imports",
                                                           {"fileId": completed["fileId"], "locale": "zh-CN"}))
        if status == 200 or not (status in TRANSIENT or status == 409):
            break
        time.sleep(10)
    ok = status == 200 and result.get("emptyCount") == 0 and result.get("updatedCount", 0) + result.get("unchangedCount", 0) == count
    record.setdefault("translationImports", []).append({"app": p["app"], **(result or {})})
    if not check(p, f"zh-CN 导入：写入 {result.get('updatedCount')}、不变 {result.get('unchangedCount')}（文件 {count}）", ok, result):
        raise SystemExit(1)


def verify_kept(p, before):
    _, _, live, _ = live_state(p["app"])
    imported = {k for entries in p["translation"].values() for k in entries}
    changed = [k for k, (text, version) in before.items() if k not in imported
               and ((live.get(k) or {}).get("zh"), (live.get(k) or {}).get("zv")) != (text, version)]
    check(p, f"导入集合外的 {len(before) - len(imported & set(before))} 条已有 zh-CN 文案与版本未变", not changed, changed[:5])
    missing = [k for k in p["added"] if (live.get(k) or {}).get("st") != "ACTIVE" or (live.get(k) or {}).get("zh") is None]
    check(p, f"新增 {len(p['added'])} 个 key ACTIVE 且有 zh-CN", not missing, missing[:5])


def rebase_outdated(token, p, record):
    """4b：把依据修订落后的 zh-CN 译文按原文批量保存，确认到当前基准（与运营在翻译页逐条确认等效）。"""
    scope = set(p["rebaseScope"])
    rows = [r for r in outdated_translations(p["app"]) if r["k"] in scope]
    if not rows:
        check(p, "没有基准已变更待确认的 zh-CN", True)
        return
    for start in range(0, len(rows), REBASE_BATCH):
        batch = [{"keyCode": r["k"], "text": r["t"], "expectedVersion": int(r["v"])} for r in rows[start:start + REBASE_BATCH]]
        status, body, _, _ = c.ops(token, "POST", f"/applications/{p['app']}/translations/batch", {"locale": "zh-CN", "translations": batch})
        if not check(p, f"确认基准已变更的 zh-CN {len(batch)} 条：{status}", status == 200, body):
            raise SystemExit(1)
    left = [r for r in outdated_translations(p["app"]) if r["k"] in scope]
    record.setdefault("rebased", []).append({"app": p["app"], "count": len(rows), "left": len(left)})
    check(p, f"本次触及的基准已变更 zh-CN 已全部确认（{len(rows)} 条，剩余 {len(left)}；计划预计 {len(p['rebaseExpected'])}；"
             f"不在本次范围、保持原样 {len(p['outdatedUntouched'])}）", not left, [r["k"] for r in left][:5])


def publish(token, p, record):
    app = p["app"]
    status, precheck, _, _ = c.ops(token, "GET", f"/applications/{app}/release-precheck")
    summary = precheck.get("changeSummary") or {}
    if not check(p, f"预检可发布（缺口 {precheck.get('missingTotal')}，下一发布号 {precheck.get('nextReleaseNo')}，新增 {summary.get('addedKeyCount')}、"
                    f"废弃 {summary.get('deprecatedKeyCount')}、基准变更 {summary.get('sourceChangedKeyCount')}）",
                 status == 200 and precheck.get("publishable") and precheck.get("missingTotal") == 0
                 and (summary.get("deprecatedKeyCount") or 0) in (0, len(p["missingInRepo"])),
                 {k: precheck.get(k) for k in ("publishable", "missingTotal", "problems", "changeSummary")}):
        raise SystemExit(1)
    _, detail, _, _ = c.ops(token, "GET", f"/applications/{app}")
    current = (detail or {}).get("currentRelease") or {}
    if not summary.get("addedKeyCount") and not summary.get("sourceChangedKeyCount") and not summary.get("deprecatedKeyCount") \
            and not any(t.get("changedCount") for t in summary.get("translationChanges") or []) and current.get("status") == "READY":
        check(p, f"与当前发布 #{current.get('releaseNo')} 无差异，跳过发布", True)
        return current.get("releaseNo")
    status, release, _, _ = c.ops(token, "POST", f"/applications/{app}/releases", {"releaseNote": RELEASE_NOTE})
    release_no = release.get("releaseNo") if status in (200, 201) else None
    if not check(p, f"发布 #{release_no}（{status}）", release_no is not None, release):
        raise SystemExit(1)
    waited = c.wait_release(token, app, release_no, timeout=600)
    progress = waited.get("checkProgress") or {}
    check(p, f"#{release_no} READY 且为当前（语言包 {progress.get('available')}/{progress.get('total')}，期望 {p['repoNamespaces'] * 2}）",
          waited.get("release", {}).get("status") == "READY" and waited["release"].get("current")
          and progress.get("available") == progress.get("total") == p["repoNamespaces"] * 2, progress)
    record.setdefault("releases", []).append({"app": app, "releaseNo": release_no})
    return release_no


def verify_manifest(p, release_no):
    prefix, origin = MANIFEST[p["app"]]
    status, manifest, text, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=prefix, base=origin, key=False)
    check(p, f"匿名 manifest 200、#{manifest.get('releaseNo')}", status == 200 and manifest.get("releaseNo") == release_no, text[:300])
    for locale in ("en", "zh-CN"):
        per_ns = (manifest.get("bundles") or {}).get(locale, {})
        check(p, f"{locale} 列出 {len(per_ns)} 个命名空间（期望 {p['repoNamespaces']}）", len(per_ns) == p["repoNamespaces"])
        for namespace, bundle in sorted(per_ns.items()):
            st, body, _ = c.public_get(bundle["url"], origin=origin)
            check(p, f"{locale}/{namespace} 匿名 200 且摘要 = hash", st == 200 and hashlib.sha256(body).hexdigest() == bundle["hash"],
                  {"status": st, "url": bundle["url"]})


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--web-root", default="/Users/kenny/Desktop/Workspace/Code-MoR-refdata-rel")
    parser.add_argument("--app", choices=sorted(APPS))
    parser.add_argument("--out", default=str(REQ / "release/i18n-bundles"))
    parser.add_argument("--apply", action="store_true", help="阶段 7：执行 1—6（写 Platform 多语言数据并发布）")
    parser.add_argument("--accept-deprecations", action="store_true", help="接受计划中列出的废弃 key（仓库已删除的旧界面文案）")
    args = parser.parse_args()
    web_root, out = Path(args.web_root), Path(args.out)
    apps = [args.app] if args.app else sorted(APPS)
    record = {"startedAt": c.now_iso(), "webRoot": str(web_root), "apply": args.apply, "apps": {}}
    prepared, all_ok = [], True
    for app in apps:
        en, zh, problems = read_sources(web_root, app)
        live_namespaces, max_sort, live, current = live_state(app)
        p = plan_for(app, en, zh, live_namespaces, max_sort, live, current, [r["k"] for r in outdated_translations(app)])
        all_ok &= check_plan(p, problems, args.accept_deprecations)
        data_en, data_zh, names, summary = write_files(out, app, en, p)
        print(json.dumps({k: summary[k] for k in ("currentRelease", "liveNamespaces", "repoNamespaces", "newNamespaces", "repoKeys",
                                                  "liveActiveKeys", "added", "restored", "sourceChanged", "missingInRepo",
                                                  "zhMissingForImport", "keepLiveZh", "refdataZhReplaced", "liveOutdatedZh",
                                                  "rebaseExpected", "outdatedUntouched")}, ensure_ascii=False))
        record["apps"][app] = summary
        before = {k: (r["zh"], r["zv"]) for k, r in live.items() if r["st"] == "ACTIVE" and r["zh"] is not None}
        prepared.append((p, data_en, data_zh, names, before))
    print(f"导入文件与计划：{out}")
    if not args.apply:
        print(f"计划完成（只读，未登录、未写入）：{'可执行' if all_ok else '有问题，不能执行'}")
        return c.summary("参考数据语言包计划") if hasattr(c, "summary") else (0 if all_ok else 1)
    if not all_ok:
        raise SystemExit("计划有问题，停止（不建命名空间、不导入、不发布）")
    token = c.admin_token()
    try:
        for p, data_en, data_zh, names, before in prepared:
            ensure_namespaces(token, p, record)
            import_keys(token, p, data_en, names[0], record)
            import_translations(token, p, data_zh, names[1], record)
            verify_kept(p, before)
            rebase_outdated(token, p, record)
            release_no = publish(token, p, record)
            verify_manifest(p, release_no)
    finally:
        record["finishedAt"] = c.now_iso()
        print("运行记录：", c.save(f"refdata-bundles-{record['startedAt'].replace(':', '')}.json", record))
    return c.summary("参考数据语言包")


if __name__ == "__main__":
    sys.exit(main())
