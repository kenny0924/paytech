"""统一对外渠道出口第二段 · mor-test 只读浏览器检查（清单第 5 节"浏览器（只读）"）。

经运营入口 http://localhost:28080 用无头 Chromium 真实登录，依次打开渠道列表三个分段、支付渠道 DEMO_NI 的概览 / 协议配置 / 沙箱验证、
服务渠道 S7P2_SVC 的概览 / 凭据 / 调用授权（矩阵与列表）、出站地址编辑弹窗（只打开后取消）和旧接入方地址跳转。
判定：页面无错误提示，控制台无 error，无 4xx / 5xx / 失败请求。截图写 screens/，结果写 results/phase2-browser.json。
管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。结构改写自文件多存储并存的 stage7_browser.py。
用法：python3 -I ccu2_browser.py
"""
import json
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
ORIGIN = "http://localhost:28080"
OUT = HERE / "screens"
OUT.mkdir(exist_ok=True)
RESULT = HERE / "results" / "phase2-browser.json"
STATE = json.loads((HERE / "results" / "phase2-service-state.json").read_text())
DEMO_NI_ID = "238508277900615680"
ERROR_SELECTORS = ".ant-alert-error, .ant-result-error, .ant-result-500, .ant-result-403, .ant-result-404, " \
                  ".ant-message-error, .ant-notification-notice-error"


def credentials():
    text = (ROOT / "test-environment/private/access.md").read_text()
    return re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1)


def visible(locator, timeout=8000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def settle(page):
    try:
        page.wait_for_load_state("networkidle", timeout=20000)
    except Exception:  # noqa: BLE001
        pass
    page.wait_for_timeout(1200)


def main():
    email, password = credentials()
    results, console, network, shots = [], [], [], []

    def record(name, ok, note):
        results.append({"page": name, "ok": bool(ok), "note": note})
        print(f"[{'通过' if ok else '失败'}] {name}：{note}", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": 1512, "height": 900})
        page = context.new_page()
        current = {"path": "/"}
        page.on("console", lambda m: console.append((current["path"], m.type, m.text[:200])) if m.type == "error" else None)
        page.on("response", lambda r: network.append((current["path"], r.status, r.request.method, r.url.split("?")[0]))
                if r.status >= 400 else None)
        page.on("requestfailed", lambda r: network.append((current["path"], "FAILED", r.method, r.url.split("?")[0])))

        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(email)
        page.get_by_label("密码").fill(password)
        page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
        settle(page)
        logged_in = not visible(page.get_by_label("密码"), 2000)
        record("登录", logged_in, "管理员登录")
        if not logged_in:
            browser.close()
            return dump(results, console, network, shots)

        def visit(name, path, expect_text, shot_name):
            current["path"] = path
            page.goto(ORIGIN + path)
            settle(page)
            found = visible(page.get_by_text(expect_text), 10000)
            errs = [t.strip()[:160] for t in page.locator(ERROR_SELECTORS).all_inner_texts() if t.strip()]
            page.screenshot(path=str(OUT / f"{shot_name}.png"), full_page=True)
            shots.append(f"{shot_name}.png")
            record(name, found and not errs, f"期望文本「{expect_text}」{'可见' if found else '缺失'}" + (f"；错误：{errs}" if errs else ""))

        service_id = STATE["channelId"]
        visit("渠道列表 · 全部", "/channel/channels", "S7P2_SVC", "p2-01-list-all")
        visit("渠道列表 · 支付", "/channel/channels?type=PAYMENT", "DEMO_NI", "p2-02-list-payment")
        visit("渠道列表 · 服务", "/channel/channels?type=SERVICE", "S7P2_SVC", "p2-03-list-service")
        visit("支付渠道 · 概览", f"/channel/channels/{DEMO_NI_ID}", "出站地址", "p2-04-payment-overview")
        visit("支付渠道 · 协议配置", f"/channel/channels/{DEMO_NI_ID}?tab=protocol", "标准 API 实现", "p2-05-payment-protocol")
        visit("支付渠道 · 沙箱验证", f"/channel/channels/{DEMO_NI_ID}?tab=verify", "验证账号", "p2-06-payment-verify")
        visit("服务渠道 · 概览", f"/channel/channels/{service_id}", "s7p2-acceptance.example.invalid", "p2-07-service-overview")
        visit("服务渠道 · 凭据", f"/channel/channels/{service_id}?tab=credential", "凭据", "p2-08-service-credential")
        visit("服务渠道 · 调用授权矩阵", f"/channel/channels/{service_id}?tab=grants&view=matrix", "counterparty-service",
              "p2-09-service-grants-matrix")
        visit("服务渠道 · 调用授权列表", f"/channel/channels/{service_id}?tab=grants&view=list", "counterparty-service",
              "p2-10-service-grants-list")

        # 出站地址编辑：只打开弹窗再取消，不保存
        current["path"] = f"/channel/channels/{service_id}#endpoints"
        page.goto(ORIGIN + f"/channel/channels/{service_id}")
        settle(page)
        button = page.get_by_role("button", name="编辑出站地址")
        opened = visible(button, 8000)
        if opened:
            button.first.click()
            opened = visible(page.locator(".ant-modal"), 8000)
            page.screenshot(path=str(OUT / "p2-11-endpoints-edit.png"), full_page=True)
            shots.append("p2-11-endpoints-edit.png")
            page.get_by_role("button", name=re.compile(r"^取\s?消$")).first.click()
            page.wait_for_timeout(600)
        record("出站地址编辑弹窗", opened, "打开后取消，不保存")

        # 旧接入方地址跳转到渠道详情
        current["path"] = "/external-api/integrations/PAYERMAX"
        page.goto(ORIGIN + "/external-api/integrations/PAYERMAX")
        settle(page)
        redirected = "/channel/channels/" in page.url
        page.screenshot(path=str(OUT / "p2-12-legacy-redirect.png"), full_page=True)
        shots.append("p2-12-legacy-redirect.png")
        record("旧接入方地址跳转", redirected, page.url.replace(ORIGIN, ""))
        browser.close()
    return dump(results, console, network, shots)


def dump(results, console, network, shots):
    passed = sum(1 for r in results if r["ok"]) == len(results) and not console and not network
    RESULT.write_text(json.dumps({"results": results, "consoleErrors": console, "badResponses": network,
                                  "screens": shots, "passed": passed}, ensure_ascii=False, indent=1))
    print(f"浏览器 {sum(1 for r in results if r['ok'])}/{len(results)}；控制台错误 {len(console)}；4xx/5xx/失败请求 {len(network)}")
    for item in console + network:
        print("  ", item)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
