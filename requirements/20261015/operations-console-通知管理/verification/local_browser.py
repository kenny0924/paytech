"""浏览器验收（AC-10～AC-12）：经运营入口登录，逐页截图并走一遍内容编辑、按钮预览、冲突与启停确认。

前置：local_acceptance.py 已运行（存在 E2E_NOTIFY_ 模板与合成发送记录）。只连接本机；截图写入第一个参数指定的目录。
用法：python3 -I local_browser.py <截图目录> <模板编码>
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

OUT, TEMPLATE = Path(sys.argv[1]), sys.argv[2]
ORIGIN = e2e_http.OPERATIONS
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(page, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def visible(locator, timeout=10000):
    """等待元素出现（懒加载页面与列表请求完成）；超时视为不存在。"""
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

        # 登录后的落地页（人员管理）与登录检查的既有警告不属于本需求：只统计进入通知管理之后的错误
        errors.clear()
        failed.clear()
        group = page.get_by_role("menuitem", name="通知管理")
        check("AC-10", "侧边栏出现“通知管理”分组", group.count() > 0)
        group.first.click()
        page.get_by_role("menuitem", name="通知模板").click()
        page.wait_for_load_state("networkidle")
        check("AC-10", "通知模板列表含本次模板与“新建模板”按钮",
              visible(page.get_by_text(TEMPLATE)) and visible(button(page, "新建模板")))
        shot(page, "01-template-list")
        button(page, "新建模板").click()
        check("AC-10", "新建模板弹窗显示编码、介质与原因", page.get_by_label("模板编码").count() > 0
              and page.locator('input[value="EMAIL（本期仅支持邮件）"]').count() > 0)
        shot(page, "02-create-template")
        button(page, "取消").click()

        page.get_by_text(TEMPLATE).first.click()
        page.wait_for_load_state("networkidle")
        check("AC-10", "详情页显示发送路由卡片与语言标签页",
              visible(page.get_by_text("发送路由")) and visible(page.get_by_role("tab", name="zh-CN")))
        check("AC-11", "侧边栏在详情子路径仍高亮“通知模板”",
              page.locator(".ant-menu-item-selected", has_text="通知模板").count() > 0)
        body = page.get_by_label("正文模板")
        body.fill("订单 ${orderNo} 已付款，金额 ${amount}。")
        check("AC-11", "编辑区不再常驻预览，只有“预览”按钮", page.locator(".notification-mail").count() == 0 and button(page, "预览").count() == 1)
        shot(page, "03-template-detail-editing")
        button(page, "预览").click()
        dialog = page.get_by_role("dialog")
        visible(dialog.locator(".notification-sample input"))
        dialog.locator(".notification-sample input").first.fill("O-9")
        button(page, "更新预览").click()
        page.wait_for_timeout(1200)
        check("AC-11", "点击预览后弹窗渲染，已填参数被替换", "O-9" in dialog.locator(".notification-mail").inner_text())
        check("AC-11", "预览列出未提供的参数", "amount" in dialog.inner_text())
        shot(page, "03b-preview-modal")
        dialog.locator(".ant-modal-footer button").click()

        page.get_by_role("tab", name="+ 新增语言").click()
        check("AC-11", "有未保存修改时切换语言需确认", page.get_by_text("放弃未保存的修改？").count() > 0)
        button(page, "继续编辑").click()

        page.get_by_label("修改原因").fill("浏览器验收")
        other = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
        current = other.get(f"/notification-admin/v1/templates/{TEMPLATE}")["contents"][0]["version"]
        other.send("PUT", f"/notification-admin/v1/templates/{TEMPLATE}/contents/zh-CN",
                   {"subjectTemplate": f"付款成功（他人修改 {current}）", "bodyTemplate": "订单 ${orderNo} 已付款。",
                    "expectedVersion": current, "reason": "模拟并发"})
        page.get_by_role("button", name=re.compile(r"^保\s?存 zh-CN$")).click()
        page.wait_for_timeout(1200)
        check("AC-11", "保存冲突显示横幅并保留草稿",
              page.get_by_text("该内容已被他人修改").count() > 0 and "${amount}" in body.input_value())
        shot(page, "04-conflict")
        button(page, "重新加载").click()
        page.wait_for_timeout(1200)
        check("AC-11", "重新加载后显示最新内容", f"付款成功（他人修改 {current}）" in page.get_by_label("标题模板").input_value())

        button(page, "启用路由").click()
        check("AC-10", "启用路由弹窗要求填写原因", page.get_by_label("原因").count() > 0)
        shot(page, "05-route-status")
        button(page, "取消").click()

        page.get_by_role("menuitem", name="发送账号").click()
        page.wait_for_load_state("networkidle")
        check("AC-10", "发送账号列表显示本环境配置与被引用模板",
              visible(page.get_by_text("E2E_NOTIFY_EMAIL")) and visible(page.get_by_text(f"{TEMPLATE}（主）")))
        shot(page, "06-provider-list")
        page.locator("tr", has_text="E2E_NOTIFY_EMAIL").get_by_role("button").click()
        check("AC-10", "停用账号弹窗显示账号编码并要求原因",
              page.get_by_text("停用发送账号 E2E_NOTIFY_EMAIL？").count() > 0 and page.get_by_label("原因").count() > 0)
        shot(page, "07-disable-provider")
        button(page, "取消").click()

        page.get_by_role("menuitem", name="发送记录").click()
        page.wait_for_load_state("networkidle")
        check("AC-12", "发送记录默认最近 7 天，显示合成记录与“失败”标签",
              visible(page.get_by_text("refund:E2E-0001")) and visible(page.locator("tr", has_text="refund:E2E-0001").get_by_text("失败")))
        shot(page, "08-record-list")
        page.get_by_text("refund:E2E-0001").click()
        dialog = page.get_by_role("dialog")
        visible(dialog.get_by_text("投递 2"))
        drawer = dialog.first.inner_text()
        check("AC-12", "详情抽屉按投递列出尝试且不显示邮箱与参数值",
              "投递 1" in drawer and "投递 2" in drawer and "E2E-SECRET" not in drawer and "vlt_" not in drawer)
        shot(page, "09-record-detail")
        browser.close()

    for message in errors:
        print("控制台错误:", message[:200])
    # 冲突用例故意产生一次 409，浏览器会把它记成资源加载失败
    errors = [m for m in errors if "409" not in m]
    unexpected = [f for f in failed if not f.startswith("409 ")]
    check("AC-10", f"无控制台错误与意外失败请求（{len(errors)} 条错误，{unexpected}）", not errors and not unexpected)
    print(f"\n共 {len(results)} 项，通过 {sum(results)}，失败 {len(results) - sum(results)}")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
