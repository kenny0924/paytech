"""批量粘贴核对的 mor-test 验收（只读：只点“检查”，不保存）：只读取粘贴内容涉及的命名空间，结果为“无变化”。"""
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
    page.on("response", lambda r: calls.append((r.status, r.url.split("/api/operations/")[-1])) if "/api/operations/" in r.url else None)
    page.goto(sb.c.OPS + "/?lang=zh-CN"); sb.settle(page, 2500); sb.login(page, "zh-CN")
    page.evaluate("r => { window.history.pushState({}, '', r); window.dispatchEvent(new PopStateEvent('popstate')); }", "/i18n/translations?app=operations-console&locale=zh-CN")
    page.wait_for_timeout(4000)
    page.get_by_role("button", name=re.compile("^更\\s?多")).first.click(); page.wait_for_timeout(800)
    page.screenshot(path=str(Path(__file__).parent / "debug-more-menu.png"))
    page.locator(".ant-dropdown:visible [role=menuitem]").filter(has_text="批量粘贴").first.click(); page.wait_for_timeout(1000)
    calls.clear()
    page.locator(".ant-modal textarea").fill("auth.login.title\t登录 Operations\ncurrency.specs.title\t币种")
    page.get_by_role("button", name=re.compile("^检\\s?查$")).click(); page.wait_for_timeout(4000)
    print("requests during check:", calls)
    lists = [c for c in calls if "/translations?" in c[1]]
    check("核对只读取 2 个涉及的命名空间（各 1 页）", len(lists) == 2 and all("namespaceCode=" in c[1] for c in lists) and all(c[0] == 200 for c in lists), str(lists))
    text = page.locator(".ant-modal").inner_text()
    check("核对结果显示且无加载失败", "检查结果" in text and "加载失败" not in text, text[:160].replace("\n", " "))
    page.screenshot(path=str(Path(__file__).parent / "batch-paste-live-zh-CN.png"))
    browser.close()
print(f"== {sum(results)} / {len(results)}")
