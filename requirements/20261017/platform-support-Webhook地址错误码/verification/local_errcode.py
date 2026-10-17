"""阶段 5 本机验收：端点地址被拒时返回专用错误码，运营页面在"接收地址"字段下显示原因。

经运营入口（Nginx → Gateway → BFF → Platform），本机 mor_platform_e2e；合成租户与清理沿用 Operations Webhook 管理的
fixture / cleanup。本机 Platform 放行 http 与 127.0.0.1。只连接本机；不打印密钥与凭据。
用法：python3 -I local_errcode.py <截图目录>
"""
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[4]
OPS = ROOT / "requirements/20261016/operations-console-Webhook管理"
sys.path.insert(0, str(OPS / "verification"))
from local_acceptance import BASE, CLIENT, check, results, status_of  # noqa: E402
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
VALID = "http://127.0.0.1:18993/hook"
CASES = [("http://10.0.0.1/hook", "WEBHOOK_URL_NOT_PUBLIC", "端点地址指向内网或保留地址"),
         ("https://hooks.code-mor.invalid/hook", "WEBHOOK_URL_UNRESOLVABLE", "端点主机名无法解析"),
         ("ftp://hooks.example.com/hook", "WEBHOOK_URL_NOT_ALLOWED", "地址格式不正确或协议不允许")]
# 页面：ftp 先被前端格式校验拦下（不发请求），协议类拒绝改用能通过前端格式、被 Platform 拒绝的带用户信息地址
PAGE_CASES = CASES[:2] + [("https://user:pass@hooks.example.com/hook", "WEBHOOK_URL_NOT_ALLOWED", "地址格式不正确或协议不允许"),
                          ("ftp://hooks.example.com/hook", None, "请填写完整的 URL；允许的协议与公网地址由 Platform 校验")]


def body(url, version=0):
    return {"url": url, "eventTypes": ["order.paid"], "expectedVersion": version, "reason": "错误码验收"}


def api(ops):
    """新建与修改各验证三种地址；成功建一个端点供页面修改。"""
    for url, code, message in CASES:
        status, problem = status_of(lambda: ops.send("POST", f"{BASE}/clients/{CLIENT}/endpoints", body(url), key=True))
        check("AC-01", f"新建 {url} → {status} {problem.get('code')}",
              status == 400 and problem.get("code") == code and problem.get("detail") == message)
    created = ops.send("POST", f"{BASE}/clients/{CLIENT}/endpoints", body(VALID), key=True)
    endpoint = created["endpointId"]
    status, problem = status_of(lambda: ops.send("PUT", f"{BASE}/endpoints/{endpoint}", body(CASES[0][0], 1), key=True))
    check("AC-01", f"修改为内网地址 → {status} {problem.get('code')}", status == 400 and problem.get("code") == CASES[0][1])
    status, problem = status_of(lambda: ops.send("POST", f"{BASE}/clients/880000000000079199/endpoints", body(VALID), key=True))
    check("AC-03", f"其他业务拒绝仍为公共错误（{problem.get('code')}：{problem.get('detail')}）",
          status == 400 and problem.get("code") == "BUSINESS_CHECK_FAILED" and problem.get("detail") == "业务校验未通过")


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def browse():
    s = e2e_http.secrets()
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900}).new_page()
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.goto(e2e_http.OPERATIONS + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.wait_for_load_state("networkidle")
        page.goto(e2e_http.OPERATIONS + f"/webhooks?clientId={CLIENT}")
        page.wait_for_load_state("networkidle")
        row = page.locator("tr").filter(has=page.get_by_text(VALID, exact=True)).first
        row.wait_for(state="visible", timeout=10000)
        button(row, "修改").click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("原因（必填）").fill("错误码验收")
        for index, (url, _, message) in enumerate(PAGE_CASES):
            dialog.get_by_label("接收地址").fill(url)
            button(dialog, "保存").click()
            shown = dialog.locator(".ant-form-item-explain-error").filter(has_text=message)
            try:
                shown.first.wait_for(state="visible", timeout=10000)
                ok = True
            except Exception:  # noqa: BLE001 - 超时即判定失败
                ok = False
            check("AC-02", f"{url}：地址字段下显示“{message}”，弹窗保持打开", ok and dialog.get_by_label("接收地址").is_visible())
            check("AC-02", f"{url}：不弹通用错误通知", page.locator(".ant-notification-notice").count() == 0)
            page.screenshot(path=str(OUT / f"0{index + 1}-url-error.png"), full_page=True)
        own = [e for e in errors if not e.startswith("Warning: [antd:") and "400" not in e]
        check("AC-02", f"控制台无其他错误（{own[:2]}）", not own)
        browser.close()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    try:
        e2e_http.platform_sql((OPS / "db/platform-support-service/acceptance-fixture-local.sql").read_text())
        api(e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]))
        browse()
    finally:
        e2e_http.platform_sql((OPS / "db/platform-support-service/acceptance-cleanup-local.sql").read_text())
        left = e2e_http.platform_sql("SELECT COUNT(*) FROM t_access_tenant WHERE tenant_id IN (880000000000079001,880000000000079002)")
        check("收尾", f"合成数据已清理（{left}）", left == [["0"]])
    print(f"\n{sum(r[2] for r in results)}/{len(results)} 通过")
    return 0 if all(r[2] for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
