"""值格式约束浏览器验收（AC-9～12）：经 Nginx 运营入口登录，逐项检查列表、编辑抽屉、手动加密与变更详情。

须在 value_format_acceptance.py b 之后运行（EMAIL_ADDRESS_V1 已为 REGEX、CHANNEL_ACCOUNT_CONFIG_V1 已为 JSON）。
不保存任何数据类型；手动加密只提交一次不合格的值（被 Vault 拒绝，只留拒绝审计）。截图写入第一个参数指定的目录（不入库）。
用法：python3 -I value_format_browser.py <截图目录>
"""
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
ORIGIN = e2e_http.OPERATIONS
EMAIL_PATTERN = r"[^\s@\p{Lu}]{1,64}@[^\s@.\p{Lu}]+(?:\.[^\s@.\p{Lu}]+)+"
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(page, text):
    """antd 会在两个汉字的按钮文字中间插入空格。"""
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def pick(page, label, option):
    page.get_by_label(label, exact=True).click()
    page.locator(".ant-select-dropdown:visible .ant-select-item-option", has_text=option).first.click()
    page.wait_for_timeout(400)


def login(page):
    s = e2e_http.secrets()
    page.goto(ORIGIN + "/")
    page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
    page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
    button(page, "登录").click()
    page.wait_for_url(lambda u: "/login" not in u and u.rstrip("/") != ORIGIN, timeout=60000)
    page.wait_for_load_state("networkidle")


def data_types(page):
    page.goto(ORIGIN + "/vault/data-types")
    page.wait_for_load_state("networkidle")
    page.mouse.move(700, 880)
    check("AC-9", "列表有“值格式”列", page.get_by_role("columnheader", name="值格式").count() == 1)
    email = page.locator(".ant-table-row", has_text="EMAIL_ADDRESS_V1")
    check("AC-9", "REGEX 类型显示“正则”标签与等宽正则", email.get_by_text("正则", exact=True).count() == 1
          and email.locator(".vault-pattern").first.inner_text().strip() == EMAIL_PATTERN)
    config = page.locator(".ant-table-row", has_text="CHANNEL_ACCOUNT_CONFIG_V1")
    check("AC-9", "JSON 类型显示“JSON 对象”", config.get_by_text("JSON 对象").count() == 1)
    page.screenshot(path=OUT / "1-data-types.png", full_page=True)

    email.get_by_role("button").click()
    page.wait_for_timeout(600)
    drawer = page.get_by_role("dialog")
    check("AC-10", "编辑抽屉显示正则（已有值）与 RE2 说明", drawer.get_by_label("正则").input_value() == EMAIL_PATTERN
          and drawer.get_by_text("RE2 语法").count() == 1)
    check("AC-10", "测试样例提示勿填真实数据", drawer.get_by_placeholder("示例值，请勿填写真实数据").count() >= 1)
    page.screenshot(path=OUT / "2-edit-regex.png")
    drawer.get_by_label("正则").fill(EMAIL_PATTERN + "x?")
    drawer.get_by_label("变更理由").fill("浏览器验收：不保存")
    sent = []
    page.on("request", lambda r: sent.append(r.url) if r.method == "PUT" and "/data-types/" in r.url else None)
    button(page, "保存").click()
    page.wait_for_timeout(800)
    check("AC-10", "修改正则后不填样例：前端拦截，不发出保存请求", not sent and drawer.locator(".ant-form-item-explain-error").count() > 0)
    page.screenshot(path=OUT / "3-edit-sample-required.png")
    pick(page, "值格式", "JSON")
    page.wait_for_timeout(300)
    check("AC-10", "切换到 JSON 后隐藏正则与样例", drawer.get_by_label("正则").count() == 0 and drawer.get_by_text("测试样例").count() == 0)
    page.keyboard.press("Escape")


def manual_protect(page):
    page.goto(ORIGIN + "/vault/manual-protect")
    page.wait_for_load_state("networkidle")
    pick(page, "数据类型", "CHANNEL_ACCOUNT_CONFIG_V1")
    check("AC-11", "JSON 类型显示“必须是 JSON 对象”", page.get_by_text("必须是 JSON 对象").count() >= 1)
    sent = []
    page.on("request", lambda r: sent.append(r.url) if r.method == "POST" and "/manual/" in r.url else None)
    page.get_by_label("原值").fill("apiKey=abc")
    pick(page, "理由", "其他")
    page.get_by_label("工单号").fill("OPS-VF-BROWSER")
    button(page, "加密").click()
    page.wait_for_timeout(800)
    check("AC-11", "非 JSON 被前端预检拦截，不发出请求", not sent and page.locator(".ant-form-item-explain-error").count() > 0)
    page.screenshot(path=OUT / "4-manual-json-precheck.png")

    pick(page, "数据类型", "EMAIL_ADDRESS_V1")
    check("AC-11", "REGEX 类型显示“必须整值匹配正则”与正则", page.get_by_text("必须整值匹配正则").count() == 1
          and page.locator(".vault-format-pattern").inner_text().strip() == EMAIL_PATTERN)
    page.get_by_label("原值").fill("Upper@Example.test")
    button(page, "加密").click()
    page.locator(".ant-notification-notice, .ant-message-notice").first.wait_for(timeout=10_000)
    notice = page.locator(".ant-notification-notice, .ant-message-notice").first.inner_text()
    check("AC-11", "Vault 拒绝时提示“值不符合数据类型的正则”且不回显原值", "值不符合数据类型的正则" in notice and "Upper@Example.test" not in notice)
    check("AC-11", "拒绝后保留原值、没有 Token", page.get_by_label("原值").input_value() == "Upper@Example.test" and page.locator(".vault-result-value").count() == 0)
    page.screenshot(path=OUT / "5-manual-regex-rejected.png")


def policy_change(page):
    page.goto(ORIGIN + "/vault/policy-changes")
    page.wait_for_load_state("networkidle")
    row = page.locator(".ant-table-row", has_text="EMAIL_ADDRESS_V1").first
    row.get_by_role("button").click()
    page.wait_for_selector(".vault-diff-table")
    page.wait_for_timeout(600)
    table = page.locator(".vault-diff-table")
    check("AC-12", "变更详情含“值格式”“正则”两行", table.get_by_text("值格式", exact=True).count() == 1 and table.get_by_text("正则", exact=True).count() == 1)
    page.screenshot(path=OUT / "6-policy-change-detail.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" and not m.text.startswith("Warning: [antd:") else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        login(page)
        data_types(page)
        manual_protect(page)
        policy_change(page)
        browser.close()
    # 唯一预期的失败请求是故意提交的不合格原值（400）；浏览器会为它记一条资源加载错误。
    expected = [f for f in failed if f.startswith("400 POST") and "/manual/" in f]
    others = [e for e in errors if not (len(expected) == len(failed) == 1 and e.startswith("Failed to load resource"))]
    print("  失败请求:", failed)
    check("AC-11", f"浏览器控制台无其他错误（{len(others)} 条）", not others and len(expected) == len(failed) == 1)
    for e in others:
        print("  console:", e[:200])
    print(f"合计 {len(results)} 项，失败 {results.count(False)} 项")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
