"""浏览器验收（AC-08、AC-12、AC-13）：经运营入口登录，检查发送账号「投递事件」列、编辑弹窗的事件队列 URL 与发送记录的事件显示。

前置：local_acceptance.py 已运行（存在 E2E_SES_EVT_* 账号与合成发送记录）。只连接本机；截图写入第一个参数指定的目录。
用法：python3 -I local_browser.py <截图目录>
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

OUT = Path(sys.argv[1])
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(page, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def row(page, code):
    return page.locator("tr.ant-table-row", has_text=code)


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
        row(page, "E2E_SES_EVT_LIVE").wait_for()
        heads = page.locator("thead th").all_inner_texts()
        check("AC-12", "账号列表有「投递事件」列", any("投递事件" in h for h in heads))
        ok, wait = row(page, "E2E_SES_EVT_OK").inner_text(), row(page, "E2E_SES_EVT_WAIT").inner_text()
        none, live = row(page, "E2E_SES_EVT_NONE").inner_text(), row(page, "E2E_SES_EVT_LIVE").inner_text()
        check("AC-12", "正常接收显示最近拉取与最近事件", "接收正常" in ok and "最近拉取" in ok and "最近事件" in ok)
        check("AC-12", "有队列未拉取显示等待首次拉取", "等待首次拉取" in wait)
        check("AC-12", "无队列显示未配置", "未配置" in none)
        check("AC-12", "拉取失败显示错误码与从未成功", "拉取失败：SQS_UNAVAILABLE" in live and "从未成功" in live)
        page.wait_for_timeout(800)  # 等淡入与弹窗动画结束再截图
        page.screenshot(path=str(OUT / "01-provider-events.png"), full_page=True)

        row(page, "E2E_SES_EVT_LIVE").get_by_role("button", name=re.compile(r"^编\s?辑$")).click()
        dialog = page.get_by_role("dialog")
        field = dialog.get_by_label("事件队列 URL（可选）")
        check("AC-08", "编辑弹窗回填事件队列 URL", field.input_value().startswith("https://sqs.us-east-1.amazonaws.com/"))
        field.fill("https://example.com/queue")
        field.blur()
        hint = dialog.get_by_text(re.compile("^格式如 https://sqs"))
        hint.wait_for(timeout=5000)
        check("AC-08", "非 SQS 地址在页面即提示格式", hint.is_visible())
        page.wait_for_timeout(800)  # 等淡入与弹窗动画结束再截图
        page.screenshot(path=str(OUT / "02-edit-queue-invalid.png"))
        field.fill("")
        field.blur()
        page.wait_for_timeout(300)
        check("AC-08", "清空后格式提示消失（可选字段）", hint.count() == 0)
        button(page, "取消").click()

        page.get_by_role("menuitem", name="发送记录").click()
        page.wait_for_load_state("networkidle")
        record = page.locator("tr.ant-table-row", has_text="order:E2E-0001")
        record.wait_for()
        check("AC-13", "发送记录列表显示合成记录", record.is_visible())
        page.wait_for_timeout(800)  # 等淡入与弹窗动画结束再截图
        page.screenshot(path=str(OUT / "03-record-list.png"), full_page=True)
        page.get_by_text("order:E2E-0001").click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_text("投递 4").wait_for()
        text = dialog.first.inner_text()
        check("AC-13", "详情显示已送达、硬退信与分类", "投递结果" in text and "已送达" in text and "硬退信" in text
              and "Permanent/General" in text)
        check("AC-13", "详情显示软退信与被投诉", "软退信" in text and "被投诉" in text and "abuse" in text)
        check("AC-13", "经事件确认的尝试显示来源「投递事件」与确认码", "来源 投递事件" in text and "确认 EVENT_DELIVERY" in text)
        check("AC-09", "详情不显示地址 Token、参数值与消息标识",
              "vlt_" not in text and "E2E-SECRET" not in text and "e2e-ses-msg" not in text)
        page.wait_for_timeout(800)  # 等淡入与弹窗动画结束再截图
        dialog.first.screenshot(path=str(OUT / "04-record-detail-events.png"))

        browser.close()

    for message in errors:
        print("控制台错误:", message[:200])
    check("AC-12", f"无控制台错误与失败请求（{len(errors)} 条错误，{failed}）", not errors and not failed)
    print(f"\n共 {len(results)} 项，通过 {sum(results)}，失败 {len(results) - sum(results)}")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
