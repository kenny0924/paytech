"""阶段 7 mor-test 浏览器验收（D2=A）与运营页面只读回归。

前置：test_acceptance.py 已运行（合成 Client 880000000000079101 下有 1 个已停用的 example.com 端点）。
不点"发送测试"、不 Replay；密钥弹窗截图前遮盖明文。截图写入第一个参数指定的目录。
用法：E2E_TARGET=test python3 -I test_browser.py <截图目录> [regression]（第二个参数只跑页面回归）
"""
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "requirements/20260914/client-console-注册与客户入驻/verification/local-e2e"))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
ORIGIN = e2e_http.OPERATIONS
URL = "https://example.com/paytech-webhook-acceptance"
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def visible(locator, timeout=10000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001 - Playwright 超时即判定失败
        return False


def shot(page, name):
    page.wait_for_timeout(600)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)


def row(page):
    return page.locator("tr").filter(has=page.get_by_text(URL, exact=True)).first


def status_dialog(page, action):
    """启停弹窗：填写原因并确认。"""
    row(page).get_by_text(action, exact=True).click()
    dialog = page.get_by_role("dialog")
    dialog.get_by_label("原因（必填）").fill("阶段 7 浏览器验收")
    button(dialog, action).last.click()
    page.wait_for_load_state("networkidle")


def routes():
    text = (ROOT / "operations-console-web/src/navigation/pageCapabilities.ts").read_text()
    return re.findall(r'"frontendRoute": "([^"]+)"', text)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_context(viewport={"width": 1440, "height": 900}).new_page()
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.wait_for_load_state("networkidle")
        errors.clear()
        failed.clear()

        if REGRESSION_ONLY:
            return regression(page, browser, errors, failed)
        page.get_by_role("menuitem", name="平台配置").first.click()
        item = page.get_by_role("menuitem", name="Webhook 管理")
        check("AC-12", "平台配置下出现“Webhook 管理”菜单", visible(item))
        item.first.click()
        page.wait_for_load_state("networkidle")
        check("AC-12", "未选 Client 时显示空状态", visible(page.get_by_text("先选择一个 Client")))
        page.locator(".webhook-client-select").first.click()
        page.keyboard.type("E2E Webhook Acme")
        popup = page.locator(".ant-select-dropdown:visible")
        check("AC-02", "搜索结果含两个合成 Client 与暂停提示",
              visible(popup.get_by_text("E2E Webhook Acme Trading")) and visible(popup.get_by_text(re.compile("平台访问已暂停"))))
        shot(page, "02-select-client")
        popup.get_by_text("E2E Webhook Acme Trading").first.click()
        page.wait_for_load_state("networkidle")
        check("AC-02", "显示已停用的 example.com 端点", visible(page.get_by_text(URL, exact=True)) and visible(row(page).get_by_text("已停用")))

        row(page).get_by_text("修改", exact=True).click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("接收地址").fill("https://10.0.0.1/hook")
        dialog.get_by_label("原因（必填）").fill("阶段 7 浏览器验收")
        button(dialog, "保存").click()
        check("AC-04", "内网地址保存被拒，显示错误通知且弹窗保留",
              visible(page.get_by_text("BUSINESS_CHECK_FAILED")) and visible(dialog.get_by_label("接收地址")))
        shot(page, "03-endpoint-error")
        button(dialog, "取消").click()
        page.locator(".ant-notification-notice-close").first.click()

        status_dialog(page, "启用")
        check("AC-05", "启用后状态为“启用”", visible(row(page).get_by_text("启用", exact=True)))
        shot(page, "01-endpoint-list")
        row(page).get_by_text("轮换密钥", exact=True).click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("原因（必填）").fill("阶段 7 浏览器验收")
        button(dialog, "轮换").click()
        secret_dialog = page.get_by_role("dialog").filter(has_text="签名密钥已生成")
        visible(secret_dialog)
        secret_input = secret_dialog.locator("input").first
        secret = secret_input.input_value() if secret_input.count() else ""
        check("AC-03", "轮换后弹窗显示一次密钥", re.fullmatch(r"whsec_[A-Za-z0-9_-]{43}", secret) is not None)
        check("AC-13", "未勾选确认前不能关闭", button(secret_dialog, "关闭").first.is_disabled())
        secret_input.evaluate("e => { e.value = 'whsec_' + '•'.repeat(43); }")
        shot(page, "04-secret-once")
        secret_dialog.get_by_text("我已保存密钥并交给 Client").click()
        button(secret_dialog, "关闭").first.click()
        page.wait_for_load_state("networkidle")
        check("AC-03", "关闭后页面不再含密钥明文", secret and secret not in page.content())
        row(page).get_by_text("停用", exact=True).click()
        check("AC-05", "停用确认说明影响", visible(page.get_by_text(re.compile("停用后不再投递新事件"))))
        shot(page, "06-disable-endpoint")
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("原因（必填）").fill("阶段 7 浏览器验收")
        button(dialog, "停用").last.click()
        page.wait_for_load_state("networkidle")
        check("AC-05", "停用后状态为“已停用”", visible(row(page).get_by_text("已停用")))

        page.get_by_role("tab", name="投递记录").click()
        page.wait_for_load_state("networkidle")
        check("AC-12", "投递记录空状态（默认最近 24 小时）", "tab=deliveries" in page.url and visible(page.get_by_text(re.compile("共 0 条|暂无"))))
        shot(page, "08-delivery-list")
        own = [f for f in failed if not f.startswith("400 PUT")]
        check("AC-12", f"Webhook 管理内无意外错误响应（{own[:3]}）", not own)

        regression(page, browser, errors, failed)
        return finish()


def finish():
    print(f"\n{sum(results)}/{len(results)} 通过")
    return 0 if all(results) else 1


def regression(page, browser, errors, failed):
    """逐页整页加载；每页间隔 7 秒，避免 Gateway 对登录上下文接口的单账号限流（10 次/分钟）。"""
    failed.clear()
    errors.clear()
    visited = 0
    for route in routes():
        page.goto(ORIGIN + route)
        page.wait_for_load_state("networkidle")
        visited += 1
        page.wait_for_timeout(7000)
    check("回归", f"{visited} 个运营页面无 ≥400 请求（{failed[:3]}）", not failed)
    check("回归", f"{visited} 个运营页面无控制台错误（{errors[:2]}）", not errors)
    browser.close()
    return finish()


REGRESSION_ONLY = len(sys.argv) > 2 and sys.argv[2] == "regression"


if __name__ == "__main__":
    sys.exit(main())
