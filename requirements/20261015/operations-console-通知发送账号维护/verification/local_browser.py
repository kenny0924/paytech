"""阶段 5 浏览器验收（AC-12～AC-14）：经运营入口登录，走一遍新建账号、编辑、录入密钥、测试发送与启用，并逐步截图。

只连接本机（或 E2E_TARGET 指定的环境）；截图写入第一个参数指定的目录。新建账号编码以 E2E_PROV_ 开头，验收后执行清理 SQL。
用法：python3 -I local_browser.py <截图目录>
"""
import re
import site
import sys
import uuid
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
ORIGIN = e2e_http.OPERATIONS
RUN = uuid.uuid4().hex[:6].upper()
PROVIDER = f"E2E_PROV_UI_{RUN}"
SECRET = f"E2E-UI-SECRET-{RUN}"
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(scope, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def visible(locator, timeout=10000):
    """等待元素出现；超时视为不存在。"""
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001 - Playwright 超时即判定失败
        return False


def shot(page, name):
    page.wait_for_timeout(500)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.wait_for_load_state("networkidle")
        errors.clear()
        failed.clear()

        page.get_by_role("menuitem", name="通知管理").first.click()
        page.get_by_role("menuitem", name="发送账号").click()
        page.wait_for_load_state("networkidle")
        check("AC-12", "发送账号页有「新建账号」按钮与新列", visible(page.get_by_role("button", name="+ 新建账号"))
              and visible(page.get_by_text("密钥 / 配置完整")))
        shot(page, "01-provider-list")

        # 新建
        page.get_by_role("button", name="+ 新建账号").click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("账号编码").fill(PROVIDER)
        dialog.get_by_label("区域").fill("eu-west-1")
        dialog.get_by_label("发件地址").fill("ops@example.com")
        dialog.get_by_label("原因").fill("浏览器验收")
        check("AC-12", "新建弹窗说明「新建后为停用」", visible(dialog.get_by_text("新建后为「停用」")))
        shot(page, "02-create-provider")
        button(dialog, "创建账号").click()
        row = page.locator("tr", has_text=PROVIDER)
        check("AC-12", "新建后列表显示未录入与配置不完整",
              visible(row.get_by_text("未录入")) and visible(row.get_by_text("配置不完整：缺少密钥")))
        check("AC-14", "配置不完整时「启用」按钮不可用", row.get_by_role("button", name=re.compile(r"^启\s?用$")).is_disabled())

        # 编辑
        row.get_by_role("button", name=re.compile(r"^编\s?辑$")).click()
        dialog = page.get_by_role("dialog")
        check("AC-14", "编辑弹窗编码只读", dialog.get_by_label("账号编码").is_disabled())
        dialog.get_by_label("Configuration Set（可选）").fill("prod-events")
        dialog.get_by_label("原因").fill("浏览器验收")
        shot(page, "03-edit-provider")
        button(dialog, "保存").click()
        page.wait_for_timeout(800)

        # 录入密钥
        row.get_by_role("button", name="录入密钥").click()
        dialog = page.get_by_role("dialog")
        check("AC-13", "密钥输入框为密码样式且说明保存后无法查看",
              dialog.get_by_label("Secret Access Key").get_attribute("type") == "password"
              and visible(dialog.get_by_text("保存后任何人都无法查看")))
        dialog.get_by_label("Access Key ID").fill("AKIAE2EUISYNTH" + RUN)
        dialog.get_by_label("Secret Access Key").fill(SECRET)
        dialog.get_by_label("原因").fill("浏览器验收")
        shot(page, "04-credential")
        button(dialog, "保存密钥").click()
        check("AC-12", "录入后显示已录入与配置完整", visible(row.get_by_text("已录入")) and visible(row.get_by_text("配置完整")))
        check("AC-13", "页面不显示密钥", SECRET not in page.content())
        shot(page, "05-after-credential")

        # 测试发送：本环境未启用该服务商时按钮不可用（mor-test 开启 SES 后再验证实际发送）
        test_send = row.get_by_role("button", name="测试发送")
        if row.get_by_text("本环境未启用该服务商").count():
            check("AC-14", "本环境未启用服务商时「测试发送」不可用", test_send.is_disabled())
        else:
            # 合成数据不调用真实服务商：只核对弹窗内容，不点发送
            test_send.click()
            dialog = page.get_by_role("dialog")
            check("AC-14", "测试发送弹窗说明不生成发送记录、只记审计",
                  visible(dialog.get_by_label("收件邮箱")) and visible(dialog.get_by_text("不生成发送记录")))
            shot(page, "06-test-send-dialog")
            button(dialog.locator(".ant-modal-footer"), "关闭").click()

        # 启用
        row.get_by_role("button", name=re.compile(r"^启\s?用$")).click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("原因").fill("浏览器验收")
        button(dialog, "确认启用").click()
        check("AC-12", "配置完整后可以启用", visible(row.locator(".ant-tag", has_text="启用")))
        shot(page, "07-enabled")
        browser.close()

    for message in errors:
        print("控制台错误:", message[:200])
    unexpected = failed
    check("AC-12", f"无控制台错误与意外失败请求（{len(errors)} 条错误，{unexpected}）", not errors and not unexpected)
    print(f"\n共 {len(results)} 项，通过 {sum(results)}，失败 {len(results) - sum(results)}；本次账号 {PROVIDER}")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
