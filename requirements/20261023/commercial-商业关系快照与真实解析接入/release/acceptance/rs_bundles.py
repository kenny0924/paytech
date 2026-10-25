"""商业关系快照与真实解析接入 · 阶段 7 第 ⑧ 步：operations-console 语言包只增不改（release.md 第 4 节，D-5）。

做法同 operations-文件上传入口/release/acceptance/fu_bundles.py，复用多语言管理发布包的 i18n7_common（运营入口、登录、上传、只读 SQL）：
  0. 只读核对线上（SQL）：命名空间 21 个、qualification / master-data 尚不存在（重跑时允许已存在）；线上全部 ACTIVE key 都在输入中；
     英文基准只有 risk.legacyCatalog.fieldHelp.entity_source_type 一处不同；zh-CN 只有已知 4 处不同（3 条 nav.sidebar.* 线上为中文、
     上面那条 risk key）；输入相对线上新增 = 219（qualification 105、master-data 108、nav 6）。任何一处不同即停止。
     记下全部已有 zh-CN 翻译的（文案, 版本）快照。
  1. 新建命名空间 qualification（220）、master-data（230）。
  2. KEY_IMPORT：上传英文全集（23 个命名空间；Platform 把文件视为应用的完整 key 集合）→ 预览：新增 219、基准变更 1、废弃 0、恢复 0 → 确认
     → 再预览全部不变。219 < 3000（Snowflake 每毫秒序列上限），一批完成。
  3. TRANSLATION_IMPORT：只含 220 个 key（219 个新 key + 基准变更的那条 risk key）的 zh-CN 文件；**不含 nav.sidebar.***（线上中文保持不变）。
  4. 复核：除那条 risk key 外，已有翻译逐条不变（文案与版本都相同），3 条侧栏仍为线上中文；新 key 都 ACTIVE 且有 zh-CN。
  5. 预检 publishable、缺口 0、相对当前 #7 新增 219、基准变更 1、废弃 0 → 发布 #8 → READY 且为当前（语言包 46 个）；
     匿名 manifest 列出 23 个命名空间；每个语言包内容 = 预期（en = 输入；zh-CN = 线上已有翻译 + 本次 220 条）。
可重跑：key 无变化跳过确认；翻译未变化计为 unchanged；与当前发布无差异且 READY 时跳过发布。

缺省为干跑（--dry-run）：只从 ops-web 源码构建导入文件到 ../i18n-bundles/（英文全集、220 条 zh-CN、SHA256SUMS、plan.json）并打印计数，
不登录、不调用任何 mor-test 接口、不执行 SQL。--apply 才执行上面 0—5 步（阶段 7，主会话）。
输入源：RELSNAP_WEB（缺省为构建目录 /Users/kenny/Desktop/Workspace/Code-MoR-relsnap-rel/operations-console-web，与候选镜像同源）。
运行记录：I18N7_STATE（缺省 ~/Code-MoR-backups/relsnap-stage7，目录 0700、文件 0600），不含口令与令牌。
用法：python3 -I rs_bundles.py [--dry-run | --apply]
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
RELEASE = HERE.parent
MOR_ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))
WEB = Path(os.environ.get("RELSNAP_WEB", "/Users/kenny/Desktop/Workspace/Code-MoR-relsnap-rel/operations-console-web"))
BUNDLES = RELEASE / "i18n-bundles"
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/relsnap-stage7"))
os.environ.setdefault("MOR_TEST_ENVIRONMENT_DIR", str(MOR_ROOT / "test-environment"))

APP = "operations-console"
EN_FILE, ZH_FILE = f"{APP}.en.json", f"{APP}.zh-CN.relsnap-delta.json"
NEW_NAMESPACES = {"qualification": ("主体资格", 220), "master-data": ("主数据", 230)}
NEW_NAV = tuple(f"nav.resource.{code}" for code in ("OPERATIONS_QUALIFICATIONS", "OPERATIONS_QUALIFICATIONS_MANAGE",
                                                    "OPERATIONS_OPERATING_ENTITIES", "OPERATIONS_OPERATING_ENTITIES_MANAGE",
                                                    "OPERATIONS_LOCAL_PARTNERS", "OPERATIONS_LOCAL_PARTNERS_MANAGE"))
SOURCE_CHANGED = ("risk.legacyCatalog.fieldHelp.entity_source_type",)
SIDEBAR = ("nav.sidebar.product", "nav.sidebar.hide", "nav.sidebar.show")  # 线上 zh-CN 为中文、仓库种子为英文：不导入
KNOWN_ZH_DIFF = set(SIDEBAR) | set(SOURCE_CHANGED)
LIVE_NAMESPACES = 21
EXPECTED_NEW = {"qualification": 105, "master-data": 108, "nav": 6}
KEY_CODE = re.compile(r"(?=.{3,191}$)[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+")
MAX_FILE = 2 * 1024 * 1024
BATCH_LIMIT = 3000
TRANSIENT = (502, 503, 504)
RELEASE_NOTE = "商业关系快照与真实解析接入：主体资格、经营主体、Local Partner 三页（2026-10 阶段 7）"


def text_of(value):
    return value if isinstance(value, str) else value.get("text")


# ---------- 输入（离线） ----------

def read_dir(directory):
    namespaces = {}
    for file in sorted(directory.glob("*.json")):
        entries = json.loads(file.read_text(encoding="utf-8"))
        namespaces[file.stem] = {k: entries[k] for k in sorted(entries)}
    return namespaces


def build():
    """与 scripts/i18n-export-source.mjs 相同的格式与校验；返回 (英文文档, zh-CN 全集, 220 条 zh-CN 文档, 预期新增, 问题)。"""
    en, zh = read_dir(WEB / "src/i18n/en"), read_dir(WEB / "i18n/zh-CN")
    problems = []
    if set(en) != set(zh):
        problems.append(f"namespaces differ: {sorted(set(en) ^ set(zh))}")
    for ns, entries in en.items():
        for key, value in entries.items():
            if not key.startswith(ns + ".") or not KEY_CODE.fullmatch(key) or not (text_of(value) or "").strip():
                problems.append(f"en {ns}: {key}")
            if key not in zh.get(ns, {}) or not str(zh[ns][key]).strip():
                problems.append(f"zh-CN missing {key}")
    expected_new = sorted([k for ns in NEW_NAMESPACES for k in en.get(ns, {})] + list(NEW_NAV))
    delta_keys = sorted(expected_new + list(SOURCE_CHANGED))
    delta = {}
    for key in delta_keys:
        ns = key.split(".", 1)[0]
        delta.setdefault(ns, {})[key] = zh[ns][key]
    doc_en = {"format": "code-mor-i18n-source/1", "application": APP, "namespaces": en}
    doc_delta = {"format": "code-mor-i18n-translation/1", "application": APP, "locale": "zh-CN", "namespaces": delta}
    return doc_en, zh, doc_delta, expected_new, problems


def encode(doc):
    return (json.dumps(doc, ensure_ascii=False, indent=2) + "\n").encode()


def flat(namespaces, en=True):
    return {k: (ns, text_of(v) if en else v) for ns, entries in namespaces.items() for k, v in entries.items()}


def write_payloads(doc_en, doc_delta, expected_new):
    BUNDLES.mkdir(exist_ok=True)
    data_en, data_zh = encode(doc_en), encode(doc_delta)
    (BUNDLES / EN_FILE).write_bytes(data_en)
    (BUNDLES / ZH_FILE).write_bytes(data_zh)
    sums = "".join(f"{hashlib.sha256(d).hexdigest()}  {n}\n" for n, d in ((EN_FILE, data_en), (ZH_FILE, data_zh)))
    (BUNDLES / "SHA256SUMS").write_text(sums)
    delta_keys = sorted(k for entries in doc_delta["namespaces"].values() for k in entries)
    plan = {"source": str(WEB), "namespaces": len(doc_en["namespaces"]), "keys": sum(len(v) for v in doc_en["namespaces"].values()),
            "newNamespaces": sorted(NEW_NAMESPACES), "expectedNewKeys": len(expected_new),
            "expectedNewByNamespace": {ns: sum(1 for k in expected_new if k.startswith(ns + ".")) for ns in EXPECTED_NEW},
            "expectedSourceChanged": list(SOURCE_CHANGED), "translationKeys": len(delta_keys),
            "translationNamespaces": sorted(doc_delta["namespaces"]), "excludedSidebarKeys": list(SIDEBAR),
            "enBytes": len(data_en), "zhDeltaBytes": len(data_zh), "sha256": {EN_FILE: hashlib.sha256(data_en).hexdigest(),
                                                                                   ZH_FILE: hashlib.sha256(data_zh).hexdigest()}}
    (BUNDLES / "plan.json").write_text(json.dumps(plan, ensure_ascii=False, indent=1) + "\n")
    return data_en, data_zh, plan


def offline_checks(c, doc_en, doc_delta, expected_new, problems, plan):
    c.check("输入", f"导出校验（与 i18n-export-source.mjs 相同规则）无问题", not problems, problems[:5])
    c.check("输入", f"英文全集 {plan['namespaces']} 个命名空间、{plan['keys']} 个 key，{plan['enBytes'] / 1024:.1f} KiB ≤ 2 MiB",
            plan["namespaces"] == LIVE_NAMESPACES + 2 and plan["enBytes"] <= MAX_FILE)
    c.check("输入", f"预期新增 {plan['expectedNewKeys']}：{plan['expectedNewByNamespace']}",
            plan["expectedNewByNamespace"] == EXPECTED_NEW and len(expected_new) == sum(EXPECTED_NEW.values()) < BATCH_LIMIT)
    keys = {k for entries in doc_delta["namespaces"].values() for k in entries}
    c.check("输入", f"zh-CN 增量 {len(keys)} 条 = 新增 {len(expected_new)} + 基准变更 {len(SOURCE_CHANGED)}，不含 nav.sidebar.*",
            keys == set(expected_new) | set(SOURCE_CHANGED) and not any(k.startswith("nav.sidebar.") for k in keys))
    c.check("输入", "nav 命名空间只带 6 个新菜单 key", sorted(doc_delta["namespaces"].get("nav", {})) == sorted(NEW_NAV))
    previous = MOR_ROOT.parent / "Code-MoR-relsnap/operations-console-web/i18n-out" / EN_FILE
    if previous.exists():
        c.check("输入", "与开发目录 i18n-out 的英文导出内容相同", json.loads(previous.read_text()) == doc_en)


# ---------- 线上（--apply） ----------

def live_state(c):
    app_id = c.sql("mor_platform", f"SELECT application_id FROM t_i18n_application WHERE application_code='{APP}'")[0][0]
    rows = c.sql("mor_platform", "SELECT JSON_OBJECT('k', k.key_code, 's', k.source_text, 'st', k.status, 'ns', n.namespace_code, "
                                 "'zh', t.text, 'zv', t.version) FROM t_i18n_key k JOIN t_i18n_namespace n ON n.namespace_id=k.namespace_id "
                                 "LEFT JOIN t_i18n_translation t ON t.key_id=k.key_id AND t.locale_code='zh-CN' "
                                 f"WHERE k.application_id={app_id}")
    namespaces = sorted(r[0] for r in c.sql("mor_platform", f"SELECT namespace_code FROM t_i18n_namespace WHERE application_id={app_id}"))
    return namespaces, {r["k"]: r for r in (json.loads(_unescape_batch(row[0])) for row in rows)}


def _unescape_batch(value):
    """mysql -B 会把 \\、\n、\t、\0 再转义一次；JSON_OBJECT 的输出须还原后再解析。"""
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t", "0": "\0"}.get(m.group(1), m.group(1)), value)


def verify_add_only(c, doc_en, zh_full, expected_new, record):
    source, translation = flat(doc_en["namespaces"]), flat(zh_full, en=False)
    namespaces, live = live_state(c)
    active = {k: r for k, r in live.items() if r["st"] == "ACTIVE"}
    old = [n for n in namespaces if n not in NEW_NAMESPACES]
    ok = c.check("⓪", f"已有命名空间 {len(old)} 个（期望 {LIVE_NAMESPACES}）；新命名空间已存在：{sorted(set(namespaces) & set(NEW_NAMESPACES))}",
                 len(old) == LIVE_NAMESPACES and set(old) <= set(source[k][0] for k in source))
    ok &= c.check("⓪", f"线上 {len(active)} 个 ACTIVE key 全部在输入中（废弃 0）", all(k in source for k in active), [k for k in active if k not in source][:5])
    changed = sorted(k for k, r in active.items() if k in source and (source[k][0], source[k][1]) != (r["ns"], r["s"]))
    ok &= c.check("⓪", f"英文基准只有预期的 {len(SOURCE_CHANGED)} 处不同", changed == sorted(SOURCE_CHANGED), changed[:5])
    zh_changed = sorted(k for k, r in active.items() if r["zh"] is not None and translation.get(k, (None, None))[1] != r["zh"])
    ok &= c.check("⓪", "zh-CN 只有已知的 4 处不同（3 条侧栏 + 1 条 risk）", set(zh_changed) <= KNOWN_ZH_DIFF, zh_changed[:5])
    new = sorted(set(source) - set(live))
    ok &= c.check("⓪", f"输入相对线上新增 {len(new)} 个 = 预期 {len(expected_new)}（重跑时为 0）", new in (expected_new, []), sorted(set(new) ^ set(expected_new))[:5])
    record["liveBefore"] = {"activeKeys": len(active), "translations": sum(1 for r in active.values() if r["zh"] is not None),
                            "sidebar": {k: (live.get(k) or {}).get("zh") for k in SIDEBAR}}
    if not ok:
        raise SystemExit("线上与输入不一致，停止（不建命名空间、不导入、不发布）")
    return {k: (r["zh"], r["zv"]) for k, r in active.items() if r["zh"] is not None}


def ensure_namespaces(c, token, record):
    status, listed, _, _ = c.ops(token, "GET", f"/applications/{APP}/namespaces")
    have = {item["namespaceCode"] for item in (listed or {}).get("items", [])}
    for code, (name, order) in NEW_NAMESPACES.items():
        if code in have:
            c.check("①", f"命名空间 {code} 已存在，跳过", True)
            continue
        status, body, _, _ = c.ops(token, "POST", f"/applications/{APP}/namespaces",
                                   {"namespaceCode": code, "namespaceName": name, "description": RELEASE_NOTE, "sortOrder": order})
        c.check("①", f"新建命名空间 {code}（{name}，排序 {order}）：{status}", status in (200, 201), body)
        record.setdefault("namespaces", []).append({"code": code, "status": status})
        if status not in (200, 201):
            raise SystemExit(1)


def retry_transient(call, what, attempts=8, pause=10):
    status, body = None, {}
    for attempt in range(attempts):
        try:
            status, body, _, _ = call()
        except OSError as failure:
            status, body = 599, {"detail": str(failure)[:200]}
        if status not in TRANSIENT + (599,):
            return status, body
        print(f"      {what}：瞬时失败 {status}，{pause} 秒后重新核对（第 {attempt + 1} 次）", flush=True)
        time.sleep(pause)
    return status, body


def import_keys(c, token, data_en, expected_new, record):
    completed = c.upload_json(token, "KEY_IMPORT", EN_FILE, data_en)
    record.setdefault("uploads", []).append({"purpose": "KEY_IMPORT", "file": EN_FILE, **completed})
    for attempt in range(6):
        status, plan, not_ready = c.with_file_ready(
            lambda: c.ops(token, "POST", f"/applications/{APP}/key-imports/previews", {"fileId": completed["fileId"]}))
        if status in TRANSIENT:
            time.sleep(10)
            continue
        added = sorted(item["keyCode"] for item in plan.get("added") or [])
        source_changed = sorted(item["keyCode"] for item in plan.get("sourceChanged") or [])
        counts = {k: len(plan.get(k) or []) for k in ("added", "sourceChanged", "deprecated", "restored")}
        ok = status == 200 and plan.get("importable") and not plan.get("missingNamespaces") and not plan.get("problems") \
            and counts["deprecated"] == counts["restored"] == 0 and added in (expected_new, []) and source_changed in (sorted(SOURCE_CHANGED), [])
        c.check("②", f"key 导入预览（第 {attempt + 1} 次）：{counts}、不变 {plan.get('unchangedCount')}", ok,
                {k: plan.get(k) for k in ("code", "detail", "missingNamespaces", "problems")} | {"unexpected": sorted(set(added) - set(expected_new))[:5]})
        if not ok:
            raise SystemExit(1)
        record.setdefault("keyPreviews", []).append({**counts, "unchanged": plan.get("unchangedCount"), "sawFileNotReady": not_ready})
        if not added and not source_changed:
            c.check("②", f"key 与输入一致（{plan.get('unchangedCount')} 个不变），无需确认", True)
            return
        status, result = retry_transient(lambda: c.ops(token, "POST", f"/applications/{APP}/key-imports",
                                                       {"fileId": completed["fileId"], "previewDigest": plan["previewDigest"]}), "确认 key 导入", attempts=1)
        if status == 200:
            c.check("②", f"key 导入确认：新增 {result.get('addedCount')}、基准变更 {result.get('sourceChangedCount')}",
                    result.get("addedCount") in (len(expected_new), 0) and result.get("sourceChangedCount") in (len(SOURCE_CHANGED), 0), result)
            record["keyImport"] = result
            continue
        if status in (409,) + TRANSIENT + (599,):
            print(f"      确认 {status} {result.get('code')}，15 秒后重新预览（上一次确认可能已提交）", flush=True)
            time.sleep(15)
            continue
        c.check("②", f"key 导入确认（{status}）", False, result)
        raise SystemExit(1)
    c.check("②", "key 导入在 6 次预览内收敛", False)
    raise SystemExit(1)


def import_translations(c, token, data_zh, count, record):
    completed = c.upload_json(token, "TRANSLATION_IMPORT", ZH_FILE, data_zh)
    record.setdefault("uploads", []).append({"purpose": "TRANSLATION_IMPORT", "file": ZH_FILE, **completed})
    status, result = None, {}
    for _ in range(6):
        status, result, _ = c.with_file_ready(lambda: c.ops(token, "POST", f"/applications/{APP}/translations/imports",
                                                           {"fileId": completed["fileId"], "locale": "zh-CN"}))
        if status == 200:
            break
        if status in TRANSIENT or (status == 409 and result.get("code") != "I18N_IMPORT_FILE_NOT_READY"):
            time.sleep(10)
            continue
        break
    ok = status == 200 and result.get("emptyCount") == 0 and result.get("updatedCount", 0) + result.get("unchangedCount", 0) == count
    c.check("③", f"zh-CN 增量：写入 {result.get('updatedCount')}、不变 {result.get('unchangedCount')}（文件 {count}）", ok, result)
    record["translationImport"] = result
    if not ok:
        raise SystemExit(1)


def verify_unchanged(c, before, delta_keys, record):
    _, live = live_state(c)
    kept = {k: v for k, v in before.items() if k not in delta_keys}
    changed = [k for k, (text, version) in kept.items() if (live.get(k) or {}).get("zh") != text or (live.get(k) or {}).get("zv") != version]
    c.check("④", f"已有 {len(kept)} 条 zh-CN 翻译（含 3 条侧栏中文）文案与版本均未变化", not changed, changed[:5])
    missing = [k for k in delta_keys if (live.get(k) or {}).get("st") != "ACTIVE" or (live.get(k) or {}).get("zh") is None]
    c.check("④", f"增量 {len(delta_keys)} 个 key ACTIVE 且有 zh-CN 翻译", not missing, missing[:5])
    record["liveAfter"] = {"activeKeys": sum(1 for r in live.values() if r["st"] == "ACTIVE"), "sidebar": {k: (live.get(k) or {}).get("zh") for k in SIDEBAR}}
    return {k: r["zh"] for k, r in live.items() if r["st"] == "ACTIVE" and r["zh"] is not None}


def publish(c, token, expected_new, record):
    status, precheck, _, _ = c.ops(token, "GET", f"/applications/{APP}/release-precheck")
    summary = precheck.get("changeSummary") or {}
    c.check("⑤", f"预检可发布（缺口 {precheck.get('missingTotal')}，下一发布号 {precheck.get('nextReleaseNo')}，相对 #{summary.get('baseReleaseNo')} "
                 f"新增 {summary.get('addedKeyCount')}、废弃 {summary.get('deprecatedKeyCount')}、基准变更 {summary.get('sourceChangedKeyCount')}）",
            status == 200 and precheck.get("publishable") and precheck.get("missingTotal") == 0 and not summary.get("deprecatedKeyCount"),
            {k: precheck.get(k) for k in ("publishable", "missingTotal", "problems", "changeSummary")})
    if not (status == 200 and precheck.get("publishable")):
        raise SystemExit(1)
    record["precheck"] = {k: precheck.get(k) for k in ("nextReleaseNo", "missingTotal", "coverage", "changeSummary")}
    _, app, _, _ = c.ops(token, "GET", f"/applications/{APP}")
    current = (app or {}).get("currentRelease") or {}
    if not summary.get("addedKeyCount") and not summary.get("sourceChangedKeyCount") \
            and not any(t.get("changedCount") for t in summary.get("translationChanges") or []) and current.get("status") == "READY":
        c.check("⑤", f"与当前发布 #{current.get('releaseNo')} 无差异，跳过发布", True)
        return current.get("releaseNo")
    c.check("⑤", f"相对当前发布新增 key = {len(expected_new)}、基准变更 = {len(SOURCE_CHANGED)}",
            summary.get("addedKeyCount") == len(expected_new) and summary.get("sourceChangedKeyCount") == len(SOURCE_CHANGED), summary)
    status, release = retry_transient(lambda: c.ops(token, "POST", f"/applications/{APP}/releases", {"releaseNote": RELEASE_NOTE}), "发布", attempts=1)
    release_no = release.get("releaseNo") if status in (200, 201) else None
    c.check("⑤", f"发布 #{release_no}（{status} {release.get('status')}）", release_no is not None, release)
    if release_no is None:
        raise SystemExit(1)
    detail = c.wait_release(token, APP, release_no, timeout=600)
    progress = detail.get("checkProgress") or {}
    total = (LIVE_NAMESPACES + 2) * 2
    c.check("⑤", f"#{release_no} READY 且为当前（语言包 {progress.get('available')}/{progress.get('total')}，期望 {total}）",
            detail.get("release", {}).get("status") == "READY" and detail["release"].get("current")
            and progress.get("available") == progress.get("total") == total, {"release": detail.get("release"), "checkProgress": progress})
    record["releaseNo"] = release_no
    return release_no


def verify_manifest(c, release_no, doc_en, zh_live, record):
    expected = {"en": {ns: {k: text_of(v) for k, v in entries.items()} for ns, entries in doc_en["namespaces"].items()},
                "zh-CN": {}}
    for key, text in zh_live.items():
        expected["zh-CN"].setdefault(key.split(".", 1)[0], {})[key] = text
    status, manifest, text, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=c.OPS_MANIFEST, base=c.OPS, key=False)
    c.check("⑥", f"匿名 manifest：200、#{manifest.get('releaseNo')}、默认 {manifest.get('defaultLocale')}",
            status == 200 and manifest.get("releaseNo") == release_no and manifest.get("defaultLocale") == "en", {"status": status, "body": text[:300]})
    for locale in ("en", "zh-CN"):
        per_ns = (manifest.get("bundles") or {}).get(locale, {})
        c.check("⑥", f"{locale} 列出 {len(per_ns)} 个命名空间（期望 {LIVE_NAMESPACES + 2}，含 qualification / master-data）",
                len(per_ns) == LIVE_NAMESPACES + 2 and set(NEW_NAMESPACES) <= set(per_ns))
        for namespace, bundle in sorted(per_ns.items()):
            st, body, hdrs = c.public_get(bundle["url"], origin=c.OPS)
            try:
                content_ok = json.loads(body) == expected[locale].get(namespace)
            except ValueError:
                content_ok = False
            c.check("⑥", f"{locale}/{namespace} 匿名 200、摘要 = hash、内容 = 预期、CORS 允许运营 Origin",
                    st == 200 and hashlib.sha256(body).hexdigest() == bundle["hash"] and content_ok
                    and hdrs.get("access-control-allow-origin") == c.OPS, {"status": st, "url": bundle["url"]})
            record.setdefault("bundles", []).append({"locale": locale, "namespace": namespace, "url": bundle["url"], "hash": bundle["hash"]})


class Offline:
    """干跑用的最小 check / summary（不导入 i18n7_common，不读取任何凭据）。"""
    results = []

    def check(self, ac, name, ok, evidence=None):
        self.results.append(bool(ok))
        print(f"[{'通过' if ok else '失败'}] {ac} {name}" + ("" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False)[:400]}"))
        return bool(ok)

    def summary(self, title):
        failed = self.results.count(False)
        print(f"\n== {title}：通过 {self.results.count(True)}，失败 {failed}")
        return 0 if not failed else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="缺省：只构建导入文件并打印计数，不连接 mor-test")
    mode.add_argument("--apply", action="store_true", help="阶段 7：执行 0—5 步（写 Platform 多语言数据并发布）")
    args = parser.parse_args()
    doc_en, zh_full, doc_delta, expected_new, problems = build()
    data_en, data_zh, plan = write_payloads(doc_en, doc_delta, expected_new)
    if not args.apply:
        o = Offline()
        offline_checks(o, doc_en, doc_delta, expected_new, problems, plan)
        print(json.dumps({k: plan[k] for k in ("namespaces", "keys", "expectedNewKeys", "expectedNewByNamespace", "translationKeys",
                                               "translationNamespaces", "excludedSidebarKeys", "enBytes", "zhDeltaBytes")}, ensure_ascii=False))
        print(f"导入文件：{BUNDLES / EN_FILE}、{BUNDLES / ZH_FILE}（SHA256SUMS、plan.json 同目录）")
        return o.summary("商业关系快照语言包（干跑，未调用任何接口）")
    sys.path.insert(0, str(MOR_ROOT / "requirements/20261022/platform-support-多语言管理/release/acceptance"))
    import i18n7_common as c  # noqa: E402  （登录、上传、导入、发布、只读 SQL；凭据只在内存）
    offline_checks(c, doc_en, doc_delta, expected_new, problems, plan)
    record = {"startedAt": c.now_iso(), "app": APP, "plan": plan}
    name = f"rs-bundles-{record['startedAt'].replace(':', '')}.json"
    delta_keys = {k for entries in doc_delta["namespaces"].values() for k in entries}
    try:
        before = verify_add_only(c, doc_en, zh_full, expected_new, record)
        token = c.admin_token()
        ensure_namespaces(c, token, record)
        import_keys(c, token, data_en, expected_new, record)
        import_translations(c, token, data_zh, len(delta_keys), record)
        zh_live = verify_unchanged(c, before, delta_keys, record)
        release_no = publish(c, token, expected_new, record)
        verify_manifest(c, release_no, doc_en, zh_live, record)
    finally:
        record["finishedAt"] = c.now_iso()
        print("运行记录：", c.save(name, record))
    return c.summary("商业关系快照语言包")


if __name__ == "__main__":
    sys.exit(main())
