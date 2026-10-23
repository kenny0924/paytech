"""运营控制台多语言迁移阶段 7 浏览器验收（无头 Chromium，严格校验证书——不加 ignore_https_errors；真实 manifest 与公开桶）。

--phase released（缺省；第 ⑤ 步，只读：除登录外不发写请求，非 GET 请求一律拦截并计数，试算 POST 查询除外）：
  B1 新浏览器（无本地选择）打开登录页：英文（<html lang=en>、标题 = auth.login.title）、语言按钮出现；
  B2 语言菜单选“简体中文”→ 整页刷新为中文、本地记录 i18n.locale=zh-CN；语言包从公开桶直接拉取且全部 200；再刷新保持中文；
  B3 中文登录；页头语言按钮显示“简体中文”；菜单用 nav.resource 中文；
  B4 角色管理“创建角色”抽屉：权限树一级节点标题 = 资源中文名 / “其他权限” / 权限编码（与 role-permission-options 逐个对应），只打开再关闭；
  B5 审计日志第一条“查看”：页面收到的详情无 label；字段名 = access.audit.field.<key> 本语言（未知 key 显示原 key），只打开再关闭；
  B6 价格试算：URL 预填 + 示例金额（候选取自 mor_commercial 只读 SELECT，先经接口确认会产生说明）→ 说明为中文；
  B7 页头切到 English → B4—B6 英文复核；再切回简体中文；
  B8 断网：拦截 manifest 与公开桶后刷新，仍为中文（本地缓存），无白屏；
  判定：控制台错误 0、4xx / 5xx 0（B8 人为拦截除外）、页面无裸 key。截图写 screens/。
--phase ac07（第 ⑤ 步 AC-07，写数据，主会话执行并负责恢复）：
  把 auth.login.title 的 zh-CN 译文改为“…（AC-07 验收）”→ 发布 → 刷新登录页可见 → 切回上一发布 → 刷新恢复原文 → 译文改回原文。
  运行记录写 I18N7_STATE/ac07-<时间>.json（含原文、版本、两个发布号），中途失败时按记录手工恢复（release.md 第 7 节）。
用法：python3 -I stage7_browser.py [--phase released|ac07] [--dry-run]
  --dry-run：只核对脚本用到的 key 在输入文件中都有 en / zh-CN 文案并列出步骤，不启动浏览器、不登录。
"""
import argparse
import json
import re
import site
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True  # 不在发布包与前例目录留下 __pycache__
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.append(site.getusersitepackages())
import opsi18n_common as o  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

c = o.c
OUT = Path(__file__).resolve().parent / "screens"
FILES = o.load_bundles()
EN, ZH = FILES["en"][1], FILES["zh-CN"][1]
KNOWN_KEYS = set(o.flat(EN, "en"))
TOKEN = re.compile(r"[A-Za-z][A-Za-z0-9_-]*(?:\.[A-Za-z0-9_-]+){2,}")
MANIFEST_RE = re.compile(r".*/api/operations/i18n/v1/manifest.*")
MINIO_RE = re.compile(rf"^https://{re.escape(c.MINIO_HOST)}:{c.MINIO_PORT}/.*")
AC07_KEY = "auth.login.title"
AC07_SUFFIX = "（AC-07 验收）"


def L(key, locale, **params):
    text = o.label(EN, ZH, key, locale)
    for name, value in params.items():
        text = text.replace("{" + name + "}", str(value))
    return text


class Run:
    def __init__(self):
        self.results, self.console, self.network, self.storage, self.blocked, self.shots = [], [], [], [], [], []
        self.path, self.offline = "/", False

    def check(self, name, ok, note=""):
        self.results.append({"step": name, "ok": bool(ok), "note": str(note)[:400]})
        print(f"[{'通过' if ok else '失败'}] {name}{'' if ok else '：' + str(note)[:400]}", flush=True)
        return bool(ok)

    def watch(self, page):
        page.on("console", lambda m: self.console.append((self.path, m.text[:300])) if m.type == "error" and not self.offline else None)

        def response(r):
            if c.MINIO_HOST in r.url:
                self.storage.append((self.path, r.status, r.url.split("?")[0]))
            if r.status >= 400:
                self.network.append((self.path, r.status, r.request.method, r.url.split("?")[0]))
        page.on("response", response)
        page.on("requestfailed", lambda r: None if self.offline else self.network.append((self.path, "FAILED", r.method, r.url.split("?")[0])))

    def guard(self, context, allow_writes=False):
        """只读阶段：登录 / 刷新 / 退出与试算查询以外的非 GET 一律拦截（返回 503 并记录）。"""
        allowed = ("/api/operations/auth/v1/login", "/api/operations/auth/v1/refresh", "/api/operations/auth/v1/logout",
                   "/api/operations" + "/commercial-admin/v1/commercial-prices/simulate")

        def handler(route):
            request = route.request
            path = request.url.split("localhost:28080", 1)[-1].split("?")[0]
            if allow_writes or request.method in ("GET", "HEAD", "OPTIONS") or path in allowed:
                return route.continue_()
            self.blocked.append((request.method, path))
            return route.fulfill(status=503, body="{}", content_type="application/json")
        context.route(re.compile(r"^http://localhost:28080/api/.*"), handler)


def settle(page, ms=1200):
    try:
        page.wait_for_load_state("networkidle", timeout=20000)
    except Exception:  # noqa: BLE001
        pass
    page.wait_for_timeout(ms)


def visible(locator, timeout=8000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def shot(run, page, name):
    OUT.mkdir(exist_ok=True)
    page.wait_for_timeout(400)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
    run.shots.append(f"{name}.png")


def info(page):
    return page.evaluate("""() => ({lang: document.documentElement.lang, text: document.body.innerText,
        h1: (document.querySelector('h1') || {}).innerText || '', stored: (() => { try { return localStorage.getItem('i18n.locale'); }
        catch (e) { return 'error'; } })()})""")


def bare_keys(text):
    return sorted({t for t in TOKEN.findall(text) if t in KNOWN_KEYS})[:5]


def credentials():
    text = (c.TEST_ENV / "private/access.md").read_text()
    return re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1)


def login(page, locale):
    email, password = credentials()
    page.get_by_label(L("auth.login.identifier", locale)).fill(email)
    page.get_by_label(L("auth.login.password", locale), exact=True).fill(password)
    submit = L("auth.login.submit", locale)  # antd 在两个汉字的按钮文字间插空格（“登 录”）
    page.get_by_role("button", name=re.compile(r"^\s*" + r"\s?".join(re.escape(ch) for ch in submit) + r"\s*$")).click()
    settle(page, 2000)
    return visible(page.locator(".console-layout"), 20000)


def switch_language(run, page, native, locale):
    """页头 / 登录页语言按钮 → 菜单项（按本地名）→ 整页刷新。"""
    button = page.locator("button.language-switcher")
    if not visible(button, 8000):
        return run.check(f"语言按钮可见（切到 {native}）", False)
    button.first.click()
    item = page.locator(".language-menu [role=menuitem]").filter(has_text=native)
    if not visible(item, 5000):
        return run.check(f"语言菜单含 {native}", False)
    with page.expect_navigation(timeout=30000):
        item.first.click()
    settle(page, 2000)
    return info(page)["lang"] == locale


def goto(run, page, path):
    run.path = path
    page.evaluate("r => { window.history.pushState({}, '', r); window.dispatchEvent(new PopStateEvent('popstate')); }", path)
    settle(page, 2500)


def close_overlay(page):
    page.keyboard.press("Escape")
    page.wait_for_timeout(500)


# ---------- 只读数据（经接口，与页面同源） ----------

def api_facts(token):
    status, options, _, _ = c.raw(token, "GET", "/iam/v1/role-permission-options")
    top = options.get("nodes") or [] if status == 200 else []
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import stage7_api  # noqa: PLC0415
    simulate, notes = None, []
    for body in stage7_api.simulation_candidates():
        st, result, _, _ = c.raw(token, "POST", stage7_api.SIMULATE, body)
        found = [n for e in ((result.get("illustration") or {}).get("feeEvents") or []) for comp in e.get("components") or []
                 for n in comp.get("notes") or [] if n.get("code") != "PROGRESSIVE_BANDS"]
        if st == 200 and found:
            simulate, notes = body, found
            break
    audit_action = None  # 带字段的审计事件的动作编码（登录类事件没有字段），供 B5 筛选
    items = []
    for n in (1, 2, 3):
        st, listed, _, _ = c.raw(token, "GET", f"/audit/v1/events?page={n}&size=50")
        items += (listed.get("items") or []) if st == 200 else []
    for item in items[:80]:
        st, detail, _, _ = c.raw(token, "GET", f"/audit/v1/events/{item['auditEventId']}")
        if st == 200 and (detail.get("changes") or []) + (detail.get("attributes") or []):
            audit_action = item.get("actionCode")
            break
    return {"top": top, "simulate": simulate, "notes": notes, "auditAction": audit_action}


def tree_title(node, locale):
    code = node.get("code") or ""
    if code == "OTHER_PERMISSIONS":
        return L("access.permissionTree.other", locale)
    key = "nav.resource." + code
    if key in KNOWN_KEYS:
        return L(key, locale)
    return node.get("displayName") if str(node.get("nodeId", "")).startswith("resource:") else code


def check_roles(run, page, facts, locale):
    goto(run, page, "/roles")
    create = page.get_by_role("button", name=L("access.roles.create", locale))
    if not visible(create, 8000):
        return run.check(f"B4 {locale} 角色页“{L('access.roles.create', locale)}”按钮", False)
    create.first.click()
    drawer = page.locator(".ant-drawer-open")
    visible(drawer.locator(".ant-tree-title"), 10000)
    titles = [t.strip() for t in drawer.locator(".ant-tree-title").all_inner_texts()]
    expected = [tree_title(node, locale) for node in facts["top"]]
    missing = [t for t in expected if t not in titles]
    run.check(f"B4 {locale} 权限树一级节点标题 = 资源名 / 其他权限 / 编码（{len(expected)} 个）", expected and not missing, missing[:5])
    run.check(f"B4 {locale} 权限树无裸 key", not bare_keys("\n".join(titles)), bare_keys("\n".join(titles)))
    shot(run, page, f"b4-roles-tree-{locale}")
    close_overlay(page)


def check_audit(run, page, facts, locale):
    goto(run, page, "/audit")
    if facts.get("auditAction"):
        page.get_by_label(L("access.audit.detail.action", locale)).first.fill(facts["auditAction"])
        page.keyboard.press("Escape")
        search = L("access.tenants.filter.search", locale)
        page.get_by_role("button", name=re.compile("^" + r"\s?".join(map(re.escape, search)) + "$")).click()
        settle(page, 2500)
    view = page.get_by_role("button", name=L("access.audit.action.view", locale), exact=True)
    if not visible(view, 10000):
        return run.check(f"B5 {locale} 审计列表“查看”按钮", False)
    detail, text = {}, ""
    for index in range(min(view.count(), 30)):  # 找一条带字段的审计详情（登录类事件没有字段）
        with page.expect_response(lambda r: re.search(r"/api/operations/audit/v1/events/[0-9]+$", r.url.split("?")[0]) is not None,
                                  timeout=15000) as response:
            view.nth(index).click()
        detail = response.value.json()  # 页面实际收到的详情（新 BFF），据此计算期望字段名
        drawer = page.locator(".ant-drawer-open")
        visible(drawer, 8000)
        page.wait_for_timeout(1500)
        text = drawer.first.inner_text()
        if (detail.get("changes") or []) + (detail.get("attributes") or []):
            break
        close_overlay(page)
        page.wait_for_timeout(500)
    run.check(f"B5 {locale} 页面收到的审计详情无 label 字段", not any("label" in p for p in (detail.get("changes") or []) + (detail.get("attributes") or [])))
    parts = (detail.get("changes") or []) + (detail.get("attributes") or [])
    expected = [L(f"access.audit.field.{p['key']}", locale) if f"access.audit.field.{p['key']}" in KNOWN_KEYS else p["key"] for p in parts]
    missing = [e for e in expected if e not in text]
    headers = [L("access.audit.detail.changes", locale)] if detail.get("changes") else []
    run.check(f"B5 {locale} 审计详情字段名按 key 翻译（{len(expected)} 个）、分区标题本语言", expected and not missing and all(h in text for h in headers),
              missing[:5] or headers)
    run.check(f"B5 {locale} 审计详情无裸 key", not bare_keys(text), bare_keys(text))
    shot(run, page, f"b5-audit-detail-{locale}")
    close_overlay(page)


def note_text(note, locale):
    text = L(f"commercial.simulator.note.{note['code']}", locale)
    for name, value in (note.get("params") or {}).items():
        text = text.replace("{" + name + "}", value)
    return text


def check_simulator(run, page, facts, locale):
    body = facts["simulate"]
    if not body:
        return c.unverified("B6", f"{locale} 价格试算说明", "没有会产生说明的 ACTIVE 条款价格")
    query = "&".join(f"{k}={body[k]}" for k in ("termVersionId", "agreementId", "entityNo", "currency", "termType"))
    owner = body.get("clientId") or body.get("localPartnerId")
    goto(run, page, f"/commercial/price-simulator?{query}&clientId={owner}")
    form = page.locator("form[id=commercial-price-simulation], form").first
    method = form.get_by_label(L("commercial.catalog.column.paymentMethod", locale))
    method.fill(body["paymentFacts"]["paymentMethodCode"] or "")
    form.get_by_label(L("commercial.simulator.field.amount", locale)).fill(body["amount"])
    label = L("commercial.simulator.run", locale)  # antd 给两个汉字的按钮自动加空格（"试 算"）
    page.get_by_role("button", name=re.compile("^" + r"\s?".join(map(re.escape, label)) + "$")).click()
    settle(page, 2500)
    text = page.locator(".console-content").inner_text()
    expected = sorted({note_text(n, locale) for n in facts["notes"]})
    missing = [e for e in expected if e not in text]
    run.check(f"B6 {locale} 试算说明为本语言（{expected[:3]}）", not missing, missing[:3])
    run.check(f"B6 {locale} 试算页无裸 key", not bare_keys(text), bare_keys(text))
    shot(run, page, f"b6-simulator-{locale}")


def released(run, browser, facts):
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    run.guard(context)
    page = context.new_page()
    run.watch(page)
    run.path = "/login"
    page.goto(c.OPS + "/")
    settle(page, 2500)
    first = info(page)
    run.check("B1 默认英文：lang=en、标题 = auth.login.title、无本地选择", first["lang"] == "en" and first["h1"].strip() == L("auth.login.title", "en")
              and first["stored"] is None, first["h1"])
    run.check("B1 登录页语言按钮可见（English）", visible(page.locator("button.language-switcher").filter(has_text="English"), 8000))
    shot(run, page, "b1-login-en")
    before = len(run.storage)
    run.check("B2 选简体中文后整页刷新为中文", switch_language(run, page, "简体中文", "zh-CN"))
    now = info(page)
    run.check("B2 中文标题、本地记录 i18n.locale=zh-CN", now["h1"].strip() == L("auth.login.title", "zh-CN") and now["stored"] == "zh-CN", now["h1"])
    fetched = [s for s in run.storage[before:] if f"/i18n/{o.APP}/" in s[2]]
    run.check(f"B2 语言包从公开桶直接拉取且全部 200（{len(fetched)} 个请求）", fetched and all(s[1] == 200 for s in fetched), fetched[:2])
    page.reload()
    settle(page, 2000)
    run.check("B2 刷新后保持中文", info(page)["h1"].strip() == L("auth.login.title", "zh-CN"))
    shot(run, page, "b2-login-zh-CN")
    if not run.check("B3 中文登录进入控制台", login(page, "zh-CN")):
        context.close()
        return
    run.check("B3 页头语言按钮显示简体中文", visible(page.locator("button.language-switcher").filter(has_text="简体中文"), 8000))
    nav = page.locator(".console-layout .ant-menu").first.inner_text() if visible(page.locator(".console-layout .ant-menu"), 5000) else ""
    run.check("B3 菜单为 nav.resource 中文", L("nav.resource.OPERATIONS_ROLES", "zh-CN") in nav or L("nav.resource.OPERATIONS_ACCOUNTS", "zh-CN") in nav,
              nav[:120])
    shot(run, page, "b3-console-zh-CN")
    for locale in ("zh-CN", "en"):
        if locale == "en":
            run.check("B7 页头切到 English 后整页刷新为英文", switch_language(run, page, "English", "en"))
        check_roles(run, page, facts, locale)
        check_audit(run, page, facts, locale)
        check_simulator(run, page, facts, locale)
    run.check("B7 再切回简体中文", switch_language(run, page, "简体中文", "zh-CN"))
    goto(run, page, "/roles")
    # B8 断网：manifest 与公开桶全部拦截，刷新后仍用本地缓存渲染中文
    run.offline = True
    page.route(MANIFEST_RE, lambda route: route.abort())
    page.route(MINIO_RE, lambda route: route.abort())
    page.reload()
    settle(page, 3000)
    offline = info(page)
    run.check("B8 断网刷新仍为中文（本地缓存），无白屏", offline["lang"] == "zh-CN" and L("nav.resource.OPERATIONS_ROLES", "zh-CN") in offline["text"],
              offline["h1"])
    shot(run, page, "b8-offline-zh-CN")
    page.unroute(MANIFEST_RE)
    page.unroute(MINIO_RE)
    run.offline = False
    context.close()


# ---------- AC-07（写数据） ----------

def ac07(run, browser):
    token = c.admin_token()
    record = {"startedAt": c.now_iso(), "key": AC07_KEY}
    name = f"ac07-{record['startedAt'].replace(':', '')}.json"
    status, page_body, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/translations?locale=zh-CN&namespaceCode=auth&keyword={AC07_KEY}&page=1&size=20")
    entry = next((i for i in page_body.get("items", []) if i.get("keyCode") == AC07_KEY), None)
    if not run.check(f"AC-07 读取 {AC07_KEY} zh-CN 译文（{status}）", entry and entry.get("text") == L(AC07_KEY, "zh-CN"), entry):
        return
    original, base = entry["text"], o.current_release(token).get("releaseNo")
    record.update({"original": original, "versionBefore": entry.get("version"), "baseReleaseNo": base})
    c.save(name, record)
    edited = original + AC07_SUFFIX
    status, saved, _, _ = c.ops(token, "POST", f"/applications/{o.APP}/translations/batch",
                                {"locale": "zh-CN", "translations": [{"keyCode": AC07_KEY, "text": edited, "expectedVersion": entry["version"]}]})
    run.check(f"AC-07 保存测试译文（{status}）", status == 200, saved)
    status, release, _, _ = c.ops(token, "POST", f"/applications/{o.APP}/releases", {"releaseNote": "AC-07 验收：临时修改登录标题（随后切回）"})
    release_no = release.get("releaseNo")
    record["testReleaseNo"] = release_no
    c.save(name, record)
    detail = c.wait_release(token, o.APP, release_no, timeout=600) if release_no else {}
    run.check(f"AC-07 发布 #{release_no} READY 且为当前", detail.get("release", {}).get("status") == "READY" and detail["release"].get("current"),
              detail.get("release"))
    context = browser.new_context(viewport={"width": 1280, "height": 800})
    context.add_init_script("try { if (!localStorage.getItem('i18n.locale')) localStorage.setItem('i18n.locale', 'zh-CN'); } catch (e) {}")
    page = context.new_page()
    run.watch(page)
    run.path = "/login(ac07)"

    def title_after_reload(want, attempts=4):
        for _ in range(attempts):  # 首次刷新可能先用缓存渲染，后台核对到新发布后即时替换文字
            page.goto(c.OPS + "/")
            settle(page, 3000)
            if info(page)["h1"].strip() == want:
                return True
        return False
    run.check("AC-07 刷新登录页后显示新译文", title_after_reload(edited), info(page)["h1"])
    shot(run, page, "ac07-edited")
    status, switched, _, _ = c.ops(token, "POST", f"/applications/{o.APP}/current-release", {"releaseNo": base, "reason": "AC-07 验收：切回上一发布"})
    run.check(f"AC-07 切回 #{base}（{status}）", status == 200, switched)
    run.check("AC-07 刷新后恢复原文", title_after_reload(original), info(page)["h1"])
    shot(run, page, "ac07-restored")
    context.close()
    _, after, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/translations?locale=zh-CN&namespaceCode=auth&keyword={AC07_KEY}&page=1&size=20")
    current = next((i for i in after.get("items", []) if i.get("keyCode") == AC07_KEY), {})
    status, saved, _, _ = c.ops(token, "POST", f"/applications/{o.APP}/translations/batch",
                                {"locale": "zh-CN", "translations": [{"keyCode": AC07_KEY, "text": original, "expectedVersion": current.get("version")}]})
    run.check(f"AC-07 译文改回原文（{status}）", status == 200, saved)
    _, precheck, _, _ = c.ops(token, "GET", f"/applications/{o.APP}/release-precheck")
    summary = precheck.get("changeSummary") or {}
    run.check(f"AC-07 草稿与当前发布 #{base} 一致（预检无差异）", summary.get("baseReleaseNo") == base and not summary.get("addedKeyCount")
              and not any(t.get("changedCount") for t in summary.get("translationChanges") or []), summary)
    record["finishedAt"] = c.now_iso()
    print("运行记录：", c.save(name, record))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase", choices=("released", "ac07"), default="released")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    run = Run()
    if args.dry_run:
        used = sorted(set(re.findall(r'L\(\s*"([A-Za-z0-9_.-]+)"', Path(__file__).read_text())) | {AC07_KEY, "nav.resource.OPERATIONS_ROLES"})
        for key in used:
            run.check(f"干跑 key {key} 有 en / zh-CN 文案", key in KNOWN_KEYS and key in o.flat(ZH, "zh-CN"))
        run.check("干跑 输入文件 SHA256SUMS 一致", FILES["en"][2] and FILES["zh-CN"][2])
        print("步骤：" + ("B1 默认英文 → B2 切中文 → B3 登录 → B4 权限树 → B5 审计详情 → B6 价格试算 → B7 英文复核 → B8 断网"
                         if args.phase == "released" else "AC-07 改译文 → 发布 → 刷新可见 → 切回 → 恢复原文"))
        failed = [r for r in run.results if not r["ok"]]
        print(f"\n== 浏览器验收干跑（{args.phase}）：通过 {len(run.results) - len(failed)}，失败 {len(failed)}")
        return 0 if not failed else 1
    with sync_playwright() as p:
        browser = p.chromium.launch()
        if args.phase == "released":
            facts = api_facts(c.admin_token())
            released(run, browser, facts)
        else:
            ac07(run, browser)
        browser.close()
    run.check("控制台错误 0", not run.console, run.console[:3])
    run.check("4xx / 5xx / 失败请求 0", not run.network, run.network[:3])
    run.check("只读阶段未拦截到写请求", not run.blocked, run.blocked[:3])
    out = {"phase": args.phase, "results": run.results, "console": run.console, "network": run.network, "blocked": run.blocked,
           "storageRequests": run.storage[:80], "screens": run.shots}
    (Path(__file__).resolve().parent / f"stage7-browser-{args.phase}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
    failed = [r for r in run.results if not r["ok"]]
    print(f"\n== 浏览器验收（{args.phase}）：通过 {len(run.results) - len(failed)}，失败 {len(failed)}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
