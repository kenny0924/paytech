"""阶段 7 mor-test 页面核对：发送账号「投递事件」列与验收记录详情的事件结果，截图存证。

前置：test_acceptance.py 已通过并输出 requestId。只连接 mor-test；不打印 Token 与凭据。
用法：E2E_TARGET=test python3 -I test_browser.py <截图目录> <requestId>
"""
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

assert e2e_http.TARGET == "test", "只用于 mor-test：设置 E2E_TARGET=test"
OUT, REQUEST_ID = Path(sys.argv[1]), sys.argv[2]
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(page, text):
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.url}") if r.status >= 400 else None)
        page.goto(e2e_http.OPERATIONS + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.wait_for_load_state("networkidle")
        errors.clear(); failed.clear()

        page.get_by_role("menuitem", name="通知管理").first.click()
        page.get_by_role("menuitem", name="发送账号").click()
        page.wait_for_load_state("networkidle")
        row = page.locator("tr.ant-table-row", has_text="SES_PRIMARY")
        row.wait_for()
        text = row.inner_text()
        check("AC-12", "SES_PRIMARY 显示接收正常、最近拉取与最近事件", "接收正常" in text and "最近事件" in text)
        page.wait_for_timeout(800)
        page.screenshot(path=str(OUT / "stage7-01-provider-events.png"), full_page=True)

        page.get_by_role("menuitem", name="发送记录").click()
        page.wait_for_load_state("networkidle")
        record = page.locator("tr.ant-table-row", has_text="ses-events-acceptance")
        record.first.wait_for()
        record.first.get_by_text(re.compile("ses-events-acceptance")).click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_text("投递 3").wait_for()
        detail = dialog.first.inner_text()
        check("AC-13", f"记录详情为 {REQUEST_ID}", REQUEST_ID in detail)
        check("AC-13", "详情显示已送达、硬退信与分类、被投诉", "已送达" in detail and "硬退信" in detail
              and "Permanent/General" in detail and "被投诉" in detail and "abuse" in detail)
        check("AC-09", "详情不显示收件地址", "simulator.amazonses.com" not in detail)
        page.wait_for_timeout(800)
        dialog.first.screenshot(path=str(OUT / "stage7-02-record-detail-events.png"))
        browser.close()

    check("AC-12", f"无控制台错误与失败请求（{len(errors)} 条错误，{failed}）", not errors and not failed)
    print(f"\n共 {len(results)} 项，通过 {sum(results)}，失败 {len(results) - sum(results)}")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
