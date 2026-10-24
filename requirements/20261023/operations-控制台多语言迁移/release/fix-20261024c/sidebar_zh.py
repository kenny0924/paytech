"""侧栏底部中文化（用户 2026-10-24 确认）：nav.sidebar.product / hide / show 的 zh-CN 译文由沿用英文改为中文，发布新语言包。

- 默认只读：核对当前发布、草稿与当前发布无差异、读取 nav.sidebar.* 五个 key 的 zh-CN 译文并打印计划。
- --apply：批量保存三条译文（带 expectedVersion）→ 预检 → 发布 → 等 READY 且为当前 → 读公开语言包核对。
- 回滚：POST /applications/operations-console/current-release 切回打印的上一发布号；译文改回 RESTORE 中的原文。
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "acceptance"))
import opsi18n_common as o  # noqa: E402

c = o.c
CHANGES = {
    "nav.sidebar.product": "运营控制台",
    "nav.sidebar.hide": "收起导航",
    "nav.sidebar.show": "展开导航",
}
WATCH = list(CHANGES) + ["nav.sidebar.collapse", "nav.sidebar.expand"]
NOTE = "侧栏底部中文化：nav.sidebar.product / hide / show 的 zh-CN 由英文改为中文"


def read_entries(token):
    status, body, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/translations?locale=zh-CN&namespaceCode=nav&keyword=nav.sidebar.&page=1&size=50")
    items = {i["keyCode"]: i for i in body.get("items", []) if i.get("keyCode") in WATCH}
    c.check("读取", f"nav.sidebar.* zh-CN 共 {len(items)} 条（{status}）", status == 200 and len(items) == len(WATCH), body if status != 200 else None)
    return items


def draft_clean(token):
    status, precheck, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/release-precheck")
    summary = precheck.get("changeSummary") or {}
    clean = (status == 200 and not summary.get("addedKeyCount") and not summary.get("deprecatedKeyCount")
             and not summary.get("sourceChangedKeyCount") and not any(t.get("changedCount") for t in summary.get("translationChanges") or []))
    return clean, summary, precheck


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    token = c.admin_token()
    current = o.current_release(token)
    base = current.get("releaseNo")
    c.check("现状", f"当前发布 #{base} {current.get('status')}", current.get("status") == "READY")
    clean, summary, _ = draft_clean(token)
    c.check("现状", f"草稿与当前发布 #{summary.get('baseReleaseNo')} 无差异（不夹带他人未发布改动）", clean and summary.get("baseReleaseNo") == base, summary)
    entries = read_entries(token)
    for key in WATCH:
        e = entries.get(key, {})
        print(f"  {key}: {e.get('text')!r} v{e.get('version')}" + (f" → {CHANGES[key]!r}" if key in CHANGES else ""))
    record = {"startedAt": c.now_iso(), "baseReleaseNo": base,
              "RESTORE": {k: entries.get(k, {}).get("text") for k in CHANGES}}
    if not args.apply or not (clean and len(entries) == len(WATCH)):
        return c.summary("侧栏中文化（只读）")
    name = f"sidebar-zh-{record['startedAt'].replace(':', '')}.json"
    c.save(name, record)
    status, saved, _, _ = c.ops(token, "POST", f"/applications/{o.APP}/translations/batch", {"locale": "zh-CN", "translations": [
        {"keyCode": k, "text": v, "expectedVersion": entries[k]["version"]} for k, v in CHANGES.items()]})
    c.check("保存", f"三条 zh-CN 译文（{status}）", status == 200, saved)
    if status != 200:
        return c.summary("侧栏中文化")
    _, summary, precheck = draft_clean(token)
    changed = sum(t.get("changedCount") or 0 for t in summary.get("translationChanges") or [])
    c.check("预检", f"可发布、缺口 {precheck.get('missingTotal')}、仅 {changed} 条译文变化", precheck.get("publishable")
             and precheck.get("missingTotal") == 0 and changed == len(CHANGES) and not summary.get("addedKeyCount"), summary)
    status, release, _, _ = c.ops(token, "POST", f"/applications/{o.APP}/releases", {"releaseNote": NOTE})
    release_no = release.get("releaseNo")
    record["releaseNo"] = release_no
    c.save(name, record)
    detail = c.wait_release(token, o.APP, release_no, timeout=600) if release_no else {}
    c.check("发布", f"#{release_no} READY 且为当前", detail.get("release", {}).get("status") == "READY" and detail["release"].get("current"), detail.get("release"))
    status, manifest, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=c.OPS_MANIFEST, base=c.OPS, key=False)
    url = (((manifest.get("bundles") or {}).get("zh-CN") or {}).get("nav") or {}).get("url")
    st, body, _ = c.public_get(url, origin=c.OPS) if url else (None, b"{}", None)
    texts = json.loads(body) if st == 200 else {}
    c.check("公开包", f"manifest #{manifest.get('releaseNo')} 的 zh-CN/nav 三条为中文", manifest.get("releaseNo") == release_no
            and all(texts.get(k) == v for k, v in CHANGES.items()), {k: texts.get(k) for k in CHANGES})
    record["finishedAt"] = c.now_iso()
    print("运行记录：", c.save(name, record))
    return c.summary("侧栏中文化")


if __name__ == "__main__":
    sys.exit(main())
