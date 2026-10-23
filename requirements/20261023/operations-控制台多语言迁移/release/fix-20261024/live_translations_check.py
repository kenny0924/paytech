"""翻译页分页加载修复的 mor-test 验收（只读）：中文界面打开翻译页只发 1 次列表请求、加载更多再发 1 次、无限流；新 key 已发布为中文。"""
import re, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "acceptance"))
import stage7_browser as sb  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402
calls, results = [], []
def check(name, ok, extra=""):
    results.append(ok); print("PASS" if ok else "FAIL", name, extra, flush=True)
with sync_playwright() as p:
    browser = p.chromium.launch(); page = browser.new_context(viewport={"width": 1440, "height": 900}).new_page()
    page.on("response", lambda r: calls.append(r.status) if re.search(r"/translations\?", r.url) else None)
    page.goto(sb.c.OPS + "/?lang=zh-CN"); sb.settle(page, 2500)
    check("中文登录进入控制台", sb.login(page, "zh-CN"))
    page.evaluate("r => { window.history.pushState({}, '', r); window.dispatchEvent(new PopStateEvent('popstate')); }", "/i18n/translations?app=operations-console&locale=zh-CN")
    page.wait_for_timeout(5000)
    rows = page.locator(".l10n-key-row").count(); more = page.locator(".l10n-load-more button")
    label = more.inner_text() if more.count() else ""
    check("打开翻译页只发 1 次列表请求且 200", calls == [200], str(calls))
    check("首屏 100 条", rows == 100, str(rows))
    check("“加载更多”为已发布的中文（新 key）", label.startswith("加载更多（100 / 8,525）"), label)
    more.first.click(); page.wait_for_timeout(3000)
    check("加载更多再发 1 次请求、共 200 条", calls == [200, 200] and page.locator(".l10n-key-row").count() == 200, str(calls))
    alerts = " ".join(page.locator(".ant-alert, .ant-notification-notice").all_inner_texts())  # 列表内容本身含"加载失败"等译文，只看提示框
    check("页面无限流 / 加载失败提示", "请求过于频繁" not in alerts and "加载失败" not in alerts, alerts[:120])
    page.screenshot(path=str(Path(__file__).parent / "translations-live-zh-CN.png"))
    browser.close()
print(f"== {sum(results)} / {len(results)}")
sys.exit(0 if all(results) else 1)
