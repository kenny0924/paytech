"""阶段 5 浏览器验收（AC-10）：经 Nginx 运营入口登录，逐页截图并走一遍手动加密→解密→30 秒自动隐藏。

只连接本机；截图写入第一个参数指定的目录（不入库）。不打印凭据、Token 与原值。
用法：python3 -I local_browser.py <截图目录> <人工加/解密用的数据类型编码>
"""
import re
import site
import sys
import time
from pathlib import Path

# python -I 不加载用户 site-packages；Playwright 装在用户目录。
sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

OUT, DATA_TYPE = Path(sys.argv[1]), sys.argv[2]
ORIGIN = e2e_http.OPERATIONS
VALUE = "s5-browser-" + str(int(time.time()))
results = []


def check(name, ok):
    results.append(ok)
    print(f"[{'通过' if ok else '失败'}] AC-10 {name}")


def button(page, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def pick(page, label, option):
    """在 antd Select 中按标签选择选项。"""
    page.get_by_label(label, exact=True).click()
    page.locator(".ant-select-dropdown:visible .ant-select-item-option", has_text=option).first.click()
    page.wait_for_timeout(400)  # 等下拉收起动画结束，避免截图遮挡


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        failed = []
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.wait_for_load_state("networkidle")

        menu = page.get_by_role("menuitem", name="数据保护")
        check("菜单出现“数据保护”", menu.count() > 0)
        menu.first.click()
        for item in ["数据类型", "访问规则", "策略变更记录", "手动加密", "手动解密", "人工操作记录"]:
            check(f"菜单含“{item}”", page.get_by_role("menuitem", name=item).count() > 0)

        for route, heading, shot in [("/vault/data-types", "数据类型", "1-data-types"),
                                     ("/vault/access-rules", "访问规则", "2-access-rules"),
                                     ("/vault/policy-changes", "策略变更记录", "3-policy-changes"),
                                     ("/vault/manual-events", "人工操作记录", "6-manual-events")]:
            page.goto(ORIGIN + route)
            page.wait_for_load_state("networkidle")
            page.mouse.move(700, 880)  # 鼠标移出侧栏，避免悬停样式进入截图
            ok = page.get_by_role("heading", name=heading).count() > 0 and page.locator(".ant-table-row").count() > 0
            check(f"{heading}页加载并有数据", ok)
            page.screenshot(path=OUT / f"{shot}.png", full_page=True)

        # 变更详情抽屉：前后对比与差异高亮
        page.goto(ORIGIN + "/vault/policy-changes")
        page.wait_for_load_state("networkidle")
        button(page, "详情").first.click()
        page.wait_for_selector(".vault-diff-table")
        page.wait_for_timeout(600)  # 等抽屉滑入动画结束
        check("变更详情抽屉显示字段对比", page.locator(".vault-diff-table .ant-table-row").count() > 0)
        page.screenshot(path=OUT / "3b-policy-change-detail.png")
        page.keyboard.press("Escape")

        # 数据类型编辑抽屉（只看，不保存）
        page.goto(ORIGIN + "/vault/data-types")
        page.wait_for_load_state("networkidle")
        button(page, "编辑").first.click()
        page.wait_for_timeout(500)
        page.screenshot(path=OUT / "1b-data-type-edit.png")
        page.keyboard.press("Escape")

        # 手动加密
        page.goto(ORIGIN + "/vault/manual-protect")
        page.wait_for_load_state("networkidle")
        pick(page, "数据类型", DATA_TYPE)
        page.get_by_label("原值").fill(VALUE)
        pick(page, "理由", "其他")
        page.get_by_label("工单号").fill("OPS-S5-BROWSER")
        page.screenshot(path=OUT / "4-manual-protect-form.png")
        button(page, "加密").click()
        page.get_by_text("加密结果（Token）").wait_for()
        token = page.locator(".vault-result-value").inner_text().strip()
        check("加密结果显示在表单下方且原值已清空", len(token) == 43 and page.get_by_label("原值").input_value() == "")
        page.locator(".vault-result-value").evaluate("e => e.textContent = '（截图已遮盖 Token）'")
        page.screenshot(path=OUT / "4b-manual-protect-result.png")

        # 手动解密：默认遮罩，显示后倒计时，30 秒后自动清除
        page.goto(ORIGIN + "/vault/manual-reveal")
        page.wait_for_load_state("networkidle")
        page.get_by_label("Token").fill(token)
        pick(page, "理由", "其他")
        page.get_by_label("工单号").fill("OPS-S5-BROWSER")
        button(page, "解密").click()
        page.locator(".vault-result-value").wait_for()
        masked = page.locator(".vault-result-value").inner_text()
        check("解密结果默认遮罩", VALUE not in masked and set(masked) == {"•"})
        check("提交后 Token 输入框已清空", page.get_by_label("Token").input_value() == "")
        page.screenshot(path=OUT / "5-manual-reveal-masked.png")
        button(page, "显示").click()
        shown = page.locator(".vault-result-value").inner_text()
        check("点击显示后可见原值并倒计时", shown == VALUE and page.get_by_text("秒后自动隐藏并清除").count() > 0)
        page.locator(".vault-result-value").evaluate("e => e.textContent = '（截图已遮盖原值）'")
        page.screenshot(path=OUT / "5b-manual-reveal-visible.png")
        page.wait_for_timeout(31_500)
        check("30 秒后自动隐藏并清除结果", page.locator(".vault-result-value").count() == 0
              and VALUE not in page.content())

        # 错误提示：不存在的 Token
        page.get_by_label("Token").fill("A" * 43)
        pick(page, "理由", "其他")
        page.get_by_label("工单号").fill("OPS-S5-BROWSER")
        button(page, "解密").click()
        page.locator(".ant-notification-notice, .ant-message-notice").first.wait_for(timeout=10_000)
        check("Token 不存在时显示错误提示且无结果", page.locator(".vault-result-value").count() == 0)
        page.screenshot(path=OUT / "5c-manual-reveal-error.png")
        browser.close()
    # antd 弃用警告来自既有页面（App.tsx、Accounts.tsx 的 tip/direction），单独列出，不计为本需求的错误。
    deprecated = sorted({e[:120] for e in errors if e.startswith("Warning: [antd:")})
    others = sorted({e[:200] for e in errors if not e.startswith("Warning: [antd:")})
    # 唯一预期的失败请求是故意用不存在的 Token 解密（404；有密钥类权限时页面走密钥路由）；浏览器会为它记一条资源加载错误。
    expected = [f for f in failed if f.startswith("404 POST") and f.endswith(("/vault-admin/v1/manual/reveal", "/vault-admin/v1/manual/secret-reveal"))]
    print("  失败请求:", failed)
    if len(expected) == len(failed) == 1:
        others = [e for e in others if not e.startswith("Failed to load resource: the server responded with a status of 404")]
    check(f"浏览器控制台无其他错误（{len(others)} 条）", not others)
    for e in others + [f"（既有弃用警告）{d}" for d in deprecated]:
        print("  console:", e)
    print(f"合计 {len(results)} 项，失败 {results.count(False)} 项")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
