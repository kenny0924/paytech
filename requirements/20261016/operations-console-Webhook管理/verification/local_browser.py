"""浏览器验收（AC-02～AC-08、AC-12～AC-14）：经运营入口登录，按 Pencil 画板逐步操作并截图。

前置：local_acceptance.py 已运行（合成 Client 880000000000079101 下有 5 个端点与若干投递）；本机 Platform 放行 http 与 127.0.0.1。
脚本在 127.0.0.1:18990 起临时接收端（/hook 返回 204）。只连接本机；不打印密钥。
用法：python3 -I local_browser.py <截图目录>
"""
import re
import site
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "requirements/20260914/client-console-注册与客户入驻/verification/local-e2e"))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
ORIGIN = e2e_http.OPERATIONS
MAIN = "http://127.0.0.1:18990/hook"
results = []


class Receiver(BaseHTTPRequestHandler):
    """临时接收端：/hook 开头返回 204，其余 503。"""

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length") or 0))
        self.send_response(204 if self.path.startswith("/hook") else 503)
        self.end_headers()

    def log_message(self, *args):
        pass


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(scope, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
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


def row(page, text):
    """表格中包含指定文字的第一行（精确地址匹配主端点）。"""
    return page.locator("tr").filter(has=page.get_by_text(text, exact=True)).first


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(("127.0.0.1", 18990), Receiver)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page = context.new_page()
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.wait_for_load_state("networkidle")
        errors.clear()
        failed.clear()

        # 入口与空状态
        page.get_by_role("menuitem", name="平台配置").first.click()
        item = page.get_by_role("menuitem", name="Webhook 管理")
        check("AC-12", "平台配置下出现“Webhook 管理”菜单", visible(item))
        item.first.click()
        page.wait_for_load_state("networkidle")
        check("AC-12", "未选 Client 时显示空状态", visible(page.get_by_text("先选择一个 Client")))

        # Client 搜索与选择
        page.locator(".webhook-client-select").first.click()
        page.keyboard.type("E2E Webhook Acme")
        popup = page.locator(".ant-select-dropdown:visible")
        check("AC-02", "搜索结果含两个合成 Client 与暂停提示",
              visible(popup.get_by_text("E2E Webhook Acme Trading")) and visible(popup.get_by_text(re.compile("平台访问已暂停"))))
        shot(page, "02-select-client")
        popup.get_by_text("E2E Webhook Acme Trading").first.click()
        page.wait_for_load_state("networkidle")
        check("AC-02", "选择后显示端点表与当前 Client", visible(page.get_by_text(MAIN, exact=True)))
        check("AC-02", "URL 记录 clientId", "clientId=880000000000079101" in page.url)
        check("AC-04", "已满 5 个端点时“新建端点”不可用", button(page, "新建端点").first.is_disabled())
        shot(page, "01-endpoint-list")

        # 修改：内网地址就地报错
        row(page, MAIN).get_by_text("修改", exact=True).click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("接收地址").fill("https://10.0.0.1/hook")
        dialog.get_by_label("原因（必填）").fill("浏览器验收")
        button(dialog, "保存").click()
        # BFF 对 Owner 业务错误统一返回公共说明（不透出上游 detail），页面以错误通知展示 code 与 requestId
        check("AC-04", "内网地址保存被拒，显示错误通知（BUSINESS_CHECK_FAILED）且弹窗保留",
              visible(page.get_by_text("BUSINESS_CHECK_FAILED")) and visible(dialog.get_by_label("接收地址")))
        shot(page, "03-endpoint-error")
        button(dialog, "取消").click()
        page.locator(".ant-notification-notice-close").first.click()

        # 轮换与一次性密钥
        row(page, MAIN).get_by_text("轮换密钥", exact=True).click()
        dialog = page.get_by_role("dialog")
        check("AC-06", "轮换弹窗默认宽限 1 小时", visible(dialog.get_by_text("旧密钥再签名 1 小时（推荐）")))
        shot(page, "05-rotate-secret")
        dialog.get_by_label("原因（必填）").fill("浏览器验收")
        button(dialog, "轮换").click()
        secret_dialog = page.get_by_role("dialog").filter(has_text="签名密钥已生成")
        visible(secret_dialog)
        secret_input = secret_dialog.locator("input").first
        secret = secret_input.input_value() if secret_input.count() else ""
        check("AC-03", "轮换后弹窗显示一次密钥", re.fullmatch(r"whsec_[A-Za-z0-9_-]{43}", secret) is not None)
        close = button(secret_dialog, "关闭")
        check("AC-13", "未勾选确认前不能关闭", close.first.is_disabled())
        button(secret_dialog, "复制").click()
        check("AC-13", "复制后有反馈", visible(secret_dialog.get_by_text(re.compile("已复制|无法访问剪贴板"))))
        # 截图前把密钥值遮盖，避免明文进入证据文件
        secret_input.evaluate("e => { e.value = 'whsec_' + '•'.repeat(43); }")
        shot(page, "04-secret-once")
        secret_dialog.get_by_text("我已保存密钥并交给 Client").click()
        close.first.click()
        page.wait_for_load_state("networkidle")
        check("AC-03", "关闭后页面不再含密钥明文", secret not in page.content())
        check("AC-06", "列表显示新序号与旧密钥截止时间", visible(row(page, MAIN).get_by_text(re.compile("旧密钥有效至"))))

        # 停用确认（取消）与测试事件
        row(page, MAIN).get_by_text("停用", exact=True).click()
        check("AC-05", "停用确认说明影响", visible(page.get_by_text(re.compile("停用后不再投递新事件"))))
        shot(page, "06-disable-endpoint")
        button(page.get_by_role("dialog"), "取消").click()
        row(page, MAIN).get_by_text("发送测试", exact=True).click()
        check("AC-07", "测试事件显示成功与 HTTP 204", visible(page.get_by_role("dialog").get_by_text(re.compile("204")), 20000))
        shot(page, "07-test-result")
        button(page.get_by_role("dialog"), "关闭").first.click()

        # 投递记录、详情与 Replay
        page.get_by_role("tab", name="投递记录").click()
        page.wait_for_load_state("networkidle")
        check("AC-08", "URL 记录投递标签", "tab=deliveries" in page.url)
        event = page.get_by_text(re.compile(r"^evt_e2e_ops_")).first
        check("AC-08", "投递列表默认最近 24 小时含本次事件", visible(event))
        check("AC-14", "状态标签显示“已送达”", visible(page.get_by_text("已送达")))
        shot(page, "08-delivery-list")
        event.click()
        drawer = page.locator(".ant-drawer")
        check("AC-08", "详情抽屉含 Envelope 与尝试记录",
              visible(drawer.get_by_text("事件内容（Envelope）")) and visible(drawer.get_by_text("尝试记录（最新在前）")))
        shot(page, "09-delivery-detail")
        button(drawer, "Replay").first.click()
        dialog = page.get_by_role("dialog").filter(has_text="Replay 投递")
        dialog.get_by_label("原因（必填）").fill("浏览器验收")
        shot(page, "10-replay-confirm")
        button(dialog, "Replay").last.click()
        check("AC-08", "Replay 成功提示新投递", visible(page.get_by_text(re.compile("已生成新投递|新投递"))))

        unexpected = [f for f in failed if not f.startswith("400 PUT")]
        check("AC-12", f"进入 Webhook 管理后无意外错误响应（{unexpected[:3]}）", not unexpected)
        check("AC-12", f"控制台无错误（{errors[:2]}）", not [e for e in errors if "400" not in e])
        browser.close()
    server.shutdown()
    print(f"\n{sum(results)}/{len(results)} 通过")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
