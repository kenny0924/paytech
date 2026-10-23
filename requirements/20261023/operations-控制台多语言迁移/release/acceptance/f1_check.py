"""code-mor-78 F1 复核（只读）：现网新 ops-web 打开 acquiring 来源计费事实 239295097391407104 的详情抽屉应正常显示（不报 INVALID_RESPONSE）。"""
import re, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import stage7_browser as sb  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402
FACT = "239295097391407104"
with sync_playwright() as p:
    browser = p.chromium.launch(); page = browser.new_context(viewport={"width": 1440, "height": 900}).new_page()
    page.goto(sb.c.OPS + "/"); sb.settle(page, 2000)
    assert sb.login(page, "en"), "login failed"
    with page.expect_response(lambda r: f"/billing-facts/{FACT}" in r.url or r.url.rstrip("/").endswith(FACT), timeout=30000) as resp:
        page.evaluate("r => { window.history.pushState({}, '', r); window.dispatchEvent(new PopStateEvent('popstate')); }", "/finance/adjustments")
        sb.settle(page, 2500)
        page.get_by_role("tab", name=re.compile("Billing Facts")).click(); sb.settle(page, 2500)
        panel = page.locator(".console-content")
        panel.get_by_label(sb.L("finance.billing.field.entityNo", "en")).fill("AE001")
        currency = panel.get_by_label(sb.L("finance.field.currency", "en"))
        currency.click(); sb.settle(page, 500); page.locator('.ant-select-dropdown:visible .ant-select-item-option[title="USD"]').first.click(); sb.settle(page, 500)
        panel.get_by_role("button", name=re.compile("^Search$")).click(); sb.settle(page, 3000)
        for _ in range(5):
            if page.get_by_text(FACT, exact=True).count():
                break
            nxt = page.locator(".ant-pagination-next:not(.ant-pagination-disabled)")
            if not nxt.count():
                break
            nxt.first.click(); sb.settle(page, 2000)
        page.get_by_text(FACT, exact=True).first.click()
    sb.settle(page, 2500)
    drawer = page.locator(".ant-drawer-open").first
    text = drawer.inner_text() if drawer.count() else ""
    body = page.inner_text("body")
    ok = resp.value.status == 200 and FACT in text and "INVALID_RESPONSE" not in body and "acquiring" in text
    page.screenshot(path=str(Path(__file__).parent / "screens" / "f1-billing-fact-drawer.png"))
    print("PASS" if ok else "FAIL", "status", resp.value.status, "| drawer has fact", FACT in text, "| acquiring", "acquiring" in text, "| INVALID_RESPONSE", "INVALID_RESPONSE" in body)
    browser.close()
