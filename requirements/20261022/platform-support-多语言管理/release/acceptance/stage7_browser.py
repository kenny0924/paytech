"""多语言管理阶段 7：mor-test 浏览器验收（无头 Chromium，严格校验证书——不加 ignore_https_errors，借此证明浏览器信任 test CA）。

--phase no-release（第 12 步激活后、首批语言包前）：
  client-web /login：切换器隐藏、英文“Welcome back”、<html lang=en>；ops-web 多语言页可打开（无发布时回退内置英文）。
--phase released（缺省；首批语言包后）：
  客户控制台 https://localhost:28443（匿名页，不登录）：
    1. /login 显示切换器（English / 简体中文）；选简体中文 → 标题“欢迎回来”、<html lang=zh-CN>、localStorage i18n.locale=zh-CN，刷新保持；
       语言包从 minio.mor-test.localhost:29400 公开桶直接拉取且 200（CORS 与证书都通过）；切回 English；
    2. /login、/register 在 1280 与 390 宽、en 与 zh-CN 下无横向溢出、无裸 key（AC-07、AC-13）；
    3. 拦截 manifest 返回 503：英文渲染、切换器隐藏（AC-08）。
  运营控制台 http://localhost:28080（管理员）：
    4. 翻译（01）、校验错误（02，只在输入框里输入错误文案，不保存，随后还原）、导入 key 弹窗（03）、发布确认弹窗（04）、
       发布记录（05）、切回确认弹窗（06）、语言与应用（07）、新建应用弹窗（08）、新建命名空间弹窗（09）——全部只打开再取消；
       批量粘贴：粘贴一条与线上相同的译文 → 检查 → “1 条无变化”，不保存；`?lang=en` 英文界面；
    5. 新页面中文文案来自语言包（请求了公开桶的 i18n-admin 语言包且 200）。
判定：控制台错误 0、4xx/5xx 0（AC-08 的人为 503 除外）。截图写 screens/，与 ../../assets/prototype/ 对照索引写 screens/index.md，
结果写 stage7-browser-result.json。
用法：python3 -I stage7_browser.py [--phase no-release|released] [--ignore-https-errors]
"""
import argparse
import json
import re
import site
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.append(site.getusersitepackages())
import i18n7_common as c  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

OUT = Path(__file__).resolve().parent / "screens"
PROTOTYPE = c.REQ / "assets/prototype"
RAW_KEY = re.compile(r"\b[a-z][a-z0-9-]*(\.[a-z0-9_-]+){2,}\b")
ERRORS = ".ant-alert-error, .ant-result-error, .ant-message-error, .ant-notification-notice-error"
PAIRS = [("p01-translations", "01-翻译-逐条编辑.png"), ("p02-validation", "02-翻译-校验错误与问题.png"),
         ("p03-import-keys", "03-导入key弹窗.png"), ("p04-publish", "04-发布确认弹窗.png"), ("p05-releases", "05-发布记录.png"),
         ("p06-switch-back", "06-切回确认弹窗.png"), ("p07-settings", "07-语言与应用.png"), ("p08-new-application", "08-新建应用弹窗.png"),
         ("p09-new-namespace", "09-新建命名空间弹窗.png")]


class Run:
    def __init__(self):
        self.results, self.console, self.network, self.shots, self.storage = [], [], [], [], []
        self.path = "/"

    def check(self, name, ok, note=""):
        self.results.append({"step": name, "ok": bool(ok), "note": note})
        print(f"[{'通过' if ok else '失败'}] {name}：{note}", flush=True)
        return bool(ok)

    def watch(self, page):
        page.on("console", lambda m: self.console.append((self.path, m.type, m.text[:300])) if m.type == "error" else None)

        def response(r):
            if c.MINIO_HOST in r.url:
                self.storage.append((self.path, r.status, r.url.split("?")[0]))
            if r.status >= 400 and not getattr(self, "allow_manifest_503", False):
                self.network.append((self.path, r.status, r.request.method, r.url.split("?")[0]))
        page.on("response", response)
        page.on("requestfailed", lambda r: self.network.append((self.path, "FAILED", r.method, r.url.split("?")[0] + " " + str(r.failure))))


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
    page.wait_for_timeout(500)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
    run.shots.append(f"{name}.png")


def layout(page):
    return page.evaluate("""() => ({lang: document.documentElement.lang,
        overflow: document.scrollingElement.scrollWidth - window.innerWidth,
        text: document.body.innerText})""")


def raw_keys(text):
    return sorted({m.group(0) for m in RAW_KEY.finditer(text) if not m.group(0).startswith(("www.", "minio."))})[:5]


# ---------- 客户控制台 ----------

def client(run, browser, phase, ignore):
    context = browser.new_context(viewport={"width": 1280, "height": 900}, ignore_https_errors=ignore)
    page = context.new_page()
    run.watch(page)
    run.path = "client:/login"
    page.goto(c.CLIENT + "/login")
    settle(page)
    switcher = page.locator("select.locale-switcher")
    info = layout(page)
    if phase == "no-release":
        run.check("C0 无发布：切换器隐藏、英文、lang=en", not visible(switcher, 3000) and "Welcome back" in info["text"] and info["lang"] == "en",
                  f"lang={info['lang']}")
        shot(run, page, "c00-login-no-release")
        context.close()
        return
    run.check("C1 有发布：/login 显示语言切换器", visible(switcher), "")
    options = switcher.locator("option").all_inner_texts() if visible(switcher, 1000) else []
    run.check("C1 切换器选项 English / 简体中文", sorted(options) == sorted(["English", "简体中文"]), str(options))
    switcher.select_option("zh-CN")
    settle(page)
    info = layout(page)
    stored = page.evaluate("() => { try { return localStorage.getItem('i18n.locale'); } catch (e) { return 'error'; } }")
    run.check("C1 选简体中文：标题“欢迎回来”、lang=zh-CN、localStorage 记录", "欢迎回来" in info["text"] and info["lang"] == "zh-CN"
              and stored == "zh-CN", f"lang={info['lang']} stored={stored}")
    shot(run, page, "c01-login-zh-CN")
    page.reload()
    settle(page)
    run.check("C1 刷新后保持简体中文", "欢迎回来" in layout(page)["text"], "")
    bundles = [s for s in run.storage if s[0] == "client:/login"]
    run.check("C1 语言包从公开桶直接拉取且全部 200（证书与 CORS 通过）", bundles and all(s[1] == 200 for s in bundles),
              f"{len(bundles)} 个请求：{bundles[:2]}")
    for width in (1280, 390):
        page.set_viewport_size({"width": width, "height": 900})
        for locale in ("zh-CN", "en"):
            for path in ("/login", "/register"):
                run.path = f"client:{path}"
                page.goto(f"{c.CLIENT}{path}?lang={locale}")
                settle(page, 800)
                info = layout(page)
                keys = raw_keys(info["text"])
                run.check(f"C2 {path} {locale} {width}px 无横向溢出、无裸 key、lang 正确",
                          info["overflow"] <= 1 and not keys and info["lang"] == locale, f"overflow={info['overflow']} keys={keys}")
                if width == 390:
                    shot(run, page, f"c02-{path.strip('/')}-{locale}-390")
    page.set_viewport_size({"width": 1280, "height": 900})
    run.path = "client:/login"
    page.goto(c.CLIENT + "/login")
    settle(page)
    switcher.select_option("en")
    settle(page)
    run.check("C1 切回 English", "Welcome back" in layout(page)["text"] and layout(page)["lang"] == "en", "")
    context.close()
    # AC-08：manifest 失败回退内置英文
    context = browser.new_context(viewport={"width": 1280, "height": 900}, ignore_https_errors=ignore)
    page = context.new_page()
    run.watch(page)
    run.allow_manifest_503 = True
    page.route("**/api/client/i18n/v1/manifest**", lambda route: route.fulfill(status=503, body="{}", content_type="application/json"))
    page.add_init_script("try { localStorage.setItem('i18n.locale', 'zh-CN'); } catch (e) {}")
    run.path = "client:/login(manifest-503)"
    page.goto(c.CLIENT + "/login")
    settle(page)
    info = layout(page)
    run.check("C3 manifest 503：英文渲染、切换器隐藏、无裸 key", "Welcome back" in info["text"] and not visible(page.locator("select.locale-switcher"), 1500)
              and not raw_keys(info["text"]), f"lang={info['lang']}")
    shot(run, page, "c03-login-manifest-503")
    run.allow_manifest_503 = False
    context.close()


# ---------- 运营控制台 ----------

def ops_login(page):
    text = (c.TEST_ENV / "private/access.md").read_text()
    email = re.search(r"账号[:：]\s*(\S+)", text).group(1)
    password = re.search(r"密码[:：]\s*(\S+)", text).group(1)
    page.goto(c.OPS + "/")
    page.get_by_label("Email / 初始化账号").fill(email)
    page.get_by_label("密码").fill(password)
    page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
    settle(page)
    return not visible(page.get_by_label("密码"), 2000)


def close_modal(page):
    for name in ("取消", "Cancel", "关闭"):
        button = page.locator(".ant-modal-wrap:visible, [role=dialog]:visible").get_by_role("button", name=name)
        if visible(button, 800):
            button.first.click()
            page.wait_for_timeout(400)
            return
    page.keyboard.press("Escape")
    page.wait_for_timeout(400)


def open_and_shoot(run, page, button_name, shot_name, label):
    button = page.get_by_role("button", name=button_name)
    if not visible(button, 5000):
        run.check(label, False, f"找不到按钮 {button_name}")
        return False
    button.first.click()
    opened = visible(page.locator(".ant-modal-wrap:visible, [role=dialog]:visible"), 5000)
    run.check(label, opened, "")
    shot(run, page, shot_name)
    close_modal(page)
    return opened


def operations(run, browser, phase):
    context = browser.new_context(viewport={"width": 1920, "height": 1080})
    page = context.new_page()
    run.watch(page)
    run.path = "ops:/"
    if not run.check("O0 管理员登录", ops_login(page), ""):
        context.close()
        return

    def goto(path):
        run.path = "ops:" + path
        page.goto(c.OPS + path)
        settle(page, 1500)

    goto("/i18n/translations?app=operations-console")
    title = "翻译" if phase == "released" else "Translations"
    run.check(f"O1 翻译页可打开（标题“{title}”）", visible(page.get_by_text(title, exact=True)), "")
    if phase == "no-release":
        shot(run, page, "o00-translations-no-release")
        context.close()
        return
    bundles = [s for s in run.storage if s[0].startswith("ops:/i18n") and "/i18n/operations-console/" in s[2]]
    run.check("O1 新页面文案来自公开桶语言包（i18n-admin、enum，200）", bundles and all(s[1] == 200 for s in bundles), str(bundles[:2]))
    # 翻译工作台本身会列出 key 编码（数据而非未翻译文案），排除本应用已登记的 key 名后再判断
    known = {k for ns in json.loads((c.BUNDLE_DIR / "operations-console.en.json").read_text())["namespaces"].values() for k in ns}
    stray = [k for k in raw_keys(layout(page)["text"]) if k not in known]
    run.check("O1 页面无裸 key（工作台中的 key 编码列除外）", not stray, str(stray))
    shot(run, page, "p01-translations")
    # 02：在输入框输入错误文案（不保存），截图后还原
    editor = page.locator("textarea").first
    if visible(editor, 5000):
        original = editor.input_value()
        editor.fill("{名字} {count, plural, one {# 项}}")
        page.wait_for_timeout(800)
        error_shown = visible(page.get_by_text(re.compile("参数|other")), 3000)
        run.check("O2 输入错误 ICU 后编辑区显示校验错误、保存不可用", error_shown, "")
        shot(run, page, "p02-validation")
        editor.fill(original)
        page.wait_for_timeout(400)
        run.check("O2 还原输入框内容（未保存）", editor.input_value() == original, "")
    else:
        run.check("O2 编辑区可见", False, "找不到 textarea")
    # 批量粘贴：一条与线上相同的译文，只做检查
    more = page.get_by_role("button", name="更多")
    if visible(more, 3000):
        more.last.click()  # 编辑区底栏的“更多”；列表中译文恰为“更多”的条目也会匹配同名按钮
        item = page.locator(".ant-dropdown:visible [role=menuitem]").filter(has_text=re.compile(r"批量粘贴.*译文…"))
        if visible(item, 3000):
            item.first.click()
            zh = json.loads((c.BUNDLE_DIR / "operations-console.zh-CN.json").read_text())["namespaces"]["enum"]
            key, text = next(iter(zh.items()))
            box = page.locator(".ant-modal-wrap:visible textarea, [role=dialog]:visible textarea")
            box.first.fill(f"{key}\t{text}")
            page.locator(".ant-modal-wrap:visible").get_by_role("button", name="检查", exact=True).first.click()  # 只在弹窗内找；key 列表行也是 button
            page.wait_for_timeout(1500)
            run.check("O3 批量粘贴检查：1 条无变化、无可保存条目（未保存）", visible(page.get_by_text("1 条无变化"), 4000), f"{key}")
            shot(run, page, "o03-batch-paste-check")
            close_modal(page)
        else:
            run.check("O3 批量粘贴入口", False, "更多菜单中找不到批量粘贴")
            page.keyboard.press("Escape")
    else:
        run.check("O3 更多菜单", False, "找不到“更多”按钮")
    open_and_shoot(run, page, "导入 key", "p03-import-keys", "O4 导入 key 弹窗（03）")
    open_and_shoot(run, page, "发布", "p04-publish", "O5 发布确认弹窗（04，未确认）")
    goto("/i18n/releases?app=operations-console")
    run.check("O6 发布记录页（05）", visible(page.get_by_text("发布记录", exact=True)), "")
    shot(run, page, "p05-releases")
    rows = page.locator(".l10n-release-table tbody tr")  # 自定义表格，不是 antd Table
    if rows.count() >= 2:
        rows.nth(1).click()
        settle(page, 800)
        open_and_shoot(run, page, re.compile("切回此版本|上线此版本"), "p06-switch-back", "O7 切回确认弹窗（06，未确认）")
    else:
        run.check("O7 切回确认弹窗", False, f"发布记录只有 {rows.count()} 行（验收脚本会产生第 2 版）")
    goto("/i18n/settings")
    run.check("O8 语言与应用（07）", visible(page.get_by_text("语言与应用", exact=True)), "")
    shot(run, page, "p07-settings")
    open_and_shoot(run, page, "新建应用", "p08-new-application", "O9 新建应用弹窗（08，未提交）")
    goto("/i18n/settings?app=client-console")
    open_and_shoot(run, page, "新建", "p09-new-namespace", "O10 新建命名空间弹窗（09，未提交）")
    goto("/i18n/translations?app=operations-console&lang=en")
    run.check("O11 ?lang=en 英文界面", visible(page.get_by_text("Translations", exact=True)), "")
    shot(run, page, "o11-translations-en")
    context.close()


def write_index():
    lines = ["# 多语言管理阶段 7 截图与设计稿对照", "", "| 截图 | 设计稿 |", "|---|---|"]
    for name, proto in PAIRS:
        lines.append(f"| ![{name}](./{name}.png) | ![{proto}](../../../assets/prototype/{proto}) |")
    (OUT / "index.md").write_text("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase", choices=("no-release", "released"), default="released")
    parser.add_argument("--ignore-https-errors", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(exist_ok=True)
    run = Run()
    with sync_playwright() as p:
        browser = p.chromium.launch()
        client(run, browser, args.phase, args.ignore_https_errors)
        operations(run, browser, args.phase)
        browser.close()
    run.check("控制台错误 0", not run.console, str(run.console[:3]))
    run.check("4xx / 5xx / 失败请求 0", not run.network, str(run.network[:3]))
    if args.phase == "released":
        write_index()
    out = {"phase": args.phase, "strictTls": not args.ignore_https_errors, "results": run.results, "console": run.console,
           "network": run.network, "storageRequests": run.storage[:50], "screens": run.shots}
    (Path(__file__).resolve().parent / f"stage7-browser-{args.phase}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
    failed = [r for r in run.results if not r["ok"]]
    print(f"\n== 浏览器验收（{args.phase}）：通过 {len(run.results) - len(failed)}，失败 {len(failed)}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
