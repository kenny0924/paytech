"""财务控制台三项：mor-test 浏览器检查（无头 Chromium，经运营入口真实登录）。

只读走查：财务菜单新项、调整页两个页签、期间关账看板、记账规则列表与版本详情、试算平衡页签「发起试算」按钮、商务页面回归；
每页截图并收集控制台错误。不打印口令。用法：E2E_TARGET=test python3 -I test_browser.py <截图目录>
"""
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

ROOT = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
results = []


def check(name, ok):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}")


def visible(locator, timeout=10000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def shot(page, name):
    page.wait_for_timeout(1200)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)


def pick_first(page, label):
    """打开带 label 的 antd Select 并选第一个选项。"""
    page.get_by_role("combobox").first.click(force=True)
    option = page.locator(".ant-select-dropdown:visible .ant-select-item-option").first
    if visible(option, 5000):
        option.click()
        return True
    return False


def main():
    s = e2e_http.secrets()
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_context(viewport={"width": 1512, "height": 900}).new_page()
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.goto(e2e_http.OPERATIONS + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.wait_for_load_state("networkidle")
        errors.clear()

        page.get_by_role("menuitem", name="财务", exact=True).first.click()
        items = ("调整", "期间关账", "记账规则")
        check("财务菜单含 调整 / 期间关账 / 记账规则", all(visible(page.get_by_role("menuitem", name=n, exact=True)) for n in items))

        page.goto(e2e_http.OPERATIONS + "/finance/adjustments")
        page.wait_for_load_state("networkidle")
        check("调整页：人工调整 / 计费事实两个页签", visible(page.get_by_role("tab", name="人工调整")) and visible(page.get_by_role("tab", name="计费事实")))
        pick_first(page, "账簿")
        page.wait_for_load_state("networkidle")
        check("人工调整列表显示验收调整（已过账）", visible(page.get_by_text(re.compile("已过账|POSTED")), 8000))
        shot(page, "01-manual-adjustments")
        page.get_by_role("tab", name="计费事实").click()
        page.wait_for_timeout(800)
        shot(page, "02-billing-facts-tab")

        page.goto(e2e_http.OPERATIONS + "/finance/period-close")
        page.wait_for_load_state("networkidle")
        pick_first(page, "财务日历")
        page.wait_for_load_state("networkidle")
        check("期间关账看板显示 2026 年 10 月与 11 月", visible(page.get_by_text(re.compile("2026.*10")), 8000))
        shot(page, "03-period-close-board")

        page.goto(e2e_http.OPERATIONS + "/finance/accounting-rules")
        page.wait_for_load_state("networkidle")
        check("记账规则列表显示两个规则包", visible(page.get_by_text("调整记账规则")) and visible(page.get_by_text("交易记账规则")))
        shot(page, "04-accounting-rules")

        page.goto(e2e_http.OPERATIONS + "/finance/ledger?tab=trial")
        page.wait_for_load_state("networkidle")
        pick_first(page, "账簿")
        page.wait_for_load_state("networkidle")
        page.get_by_role("combobox").nth(1).click(force=True)
        option = page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="OPEN").first
        if visible(option, 5000):
            option.click()
        page.wait_for_load_state("networkidle")
        check("试算平衡页签有「发起试算」按钮", visible(page.get_by_role("button", name=re.compile(r"发起\s?试算"))))
        shot(page, "05-trial-balance-run")

        for path, name in (("/commercial/terms", "06-commercial-terms"), ("/commercial/workbench", "07-commercial-workbench")):
            page.goto(e2e_http.OPERATIONS + path)
            page.wait_for_load_state("networkidle")
            shot(page, name)
        check("商务页面回归可打开（价目、工作台）", True)

        relevant = [e for e in errors if "favicon" not in e]
        check(f"控制台无 JS 错误（{len(relevant)} 条）", not relevant)
        for e in relevant[:10]:
            print("  console:", e[:200])
        browser.close()
    passed = sum(1 for r in results if r[1])
    print(f"\n{passed}/{len(results)} 通过，截图：{OUT}")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
