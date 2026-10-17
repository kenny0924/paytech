"""只读权限验收（AC-11，阶段 7 遗留）：没有 Webhook 维护权限时，页面只读、写接口被拒。

做法：本机 mor_platform_e2e 插入合成租户，管理员建 1 个端点并提交 1 个事件得到投递；
随后临时撤销 PLATFORM_ADMIN 的 WEBHOOK_MANAGE 授权（900000000000090202）并递增角色版本，
重新登录检查菜单、按钮与写接口；结束时（含失败）恢复授权、再递增角色版本并清理合成数据。
前置：本机 Platform 放行 http 与 127.0.0.1（同阶段 5）。只连接本机；不打印密钥与凭据。
用法：python3 -I local_readonly.py <截图目录>
"""
import json
import re
import site
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright

sys.path.insert(0, str(Path(__file__).resolve().parent))
from local_acceptance import BASE, CLIENT, check, results, status_of, submit_event, wait_until, window  # noqa: E402
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
DB = Path(__file__).resolve().parents[1] / "db/platform-support-service"
URL = "http://127.0.0.1:18991/hook"
GRANT, ROLE = 900000000000090202, 900000000000000510


class Receiver(BaseHTTPRequestHandler):
    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length") or 0))
        self.send_response(204)
        self.end_headers()

    def log_message(self, *args):
        pass


def grant(status):
    e2e_http.platform_sql(
        "START TRANSACTION;"
        f"UPDATE t_role_permission SET status='{status}', version=version+1, update_time=UTC_TIMESTAMP(6)"
        f" WHERE role_permission_id={GRANT};"
        f"UPDATE t_role SET version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_id={ROLE};COMMIT;")


def prepare(ops):
    """合成租户、端点与一条已送达投递。"""
    e2e_http.platform_sql((DB / "acceptance-fixture-local.sql").read_text())
    check("准备", "Gateway 已加载 Webhook 路由", wait_until(lambda: status_of(lambda: ops.get(BASE + "/clients?keyword=E2E"))[0] == 200))
    ops.send("POST", f"{BASE}/clients/{CLIENT}/endpoints",
             {"url": URL, "eventTypes": ["order.paid"], "expectedVersion": 0, "reason": "只读验收"}, key=True)
    submit_event(f"evt_e2e_ro_{uuid.uuid4().hex[:8]}")
    delivered = lambda: any(d["status"] == "SUCCEEDED" for d in ops.get(f"{BASE}/deliveries?clientId={CLIENT}&{window()}")["items"])
    check("准备", "合成端点已有 1 条已送达投递", wait_until(delivered))


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
        row = page.locator("tr").filter(has=page.get_by_text(URL, exact=True)).first
        try:
            row.wait_for(state="visible", timeout=10000)
            listed = True
        except Exception:  # noqa: BLE001 - 超时即判定失败
            listed = False
        check("AC-11", "只读时仍可进入页面并查看端点", listed)
        check("AC-11", "不显示“新建端点”", button(page, "新建端点").count() == 0)
        hidden = [a for a in ["修改", "停用", "启用", "轮换密钥", "发送测试"] if button(row, a).count()]
        check("AC-11", f"端点行不显示维护操作（{hidden}）", not hidden)
        page.screenshot(path=str(OUT / "11-readonly-endpoints.png"), full_page=True)
        page.get_by_role("tab", name="投递记录").click()
        page.wait_for_load_state("networkidle")
        event = page.get_by_text(re.compile(r"^evt_e2e_ro_")).first
        event.wait_for(state="visible", timeout=10000)
        check("AC-11", "投递列表不显示 Replay 列", page.get_by_role("columnheader", name="操作").count() == 0)
        event.click()
        drawer = page.locator(".ant-drawer")
        drawer.get_by_text("尝试记录（最新在前）").wait_for(state="visible", timeout=10000)
        check("AC-11", "投递详情不显示 Replay", button(drawer, "Replay").count() == 0)
        page.screenshot(path=str(OUT / "12-readonly-delivery.png"), full_page=True)
        # antd 废弃属性警告来自共用的 App.tsx 等文件，与本页无关
        own = [e for e in errors if not e.startswith("Warning: [antd:")]
        check("AC-11", f"控制台无错误（{own[:2]}）", not own)
        browser.close()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(("127.0.0.1", 18991), Receiver)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    s = e2e_http.secrets()
    try:
        prepare(e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]))
        grant("REVOKED")
        time.sleep(3)
        ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
        codes = json.dumps(ops.get("/authorization/v1/navigation"), ensure_ascii=False)
        check("AC-11", "导航含菜单、不含维护按钮", "OPERATIONS_WEBHOOKS" in codes and "OPERATIONS_WEBHOOK_MANAGE" not in codes)
        code, _ = status_of(lambda: ops.send("POST", f"{BASE}/clients/{CLIENT}/endpoints",
                                             {"url": URL, "eventTypes": ["order.paid"], "expectedVersion": 0, "reason": "只读验收"}))
        check("AC-11", f"写接口被拒 403（{code}）", code == 403)
        check("AC-11", "读接口仍可用", status_of(lambda: ops.get(f"{BASE}/clients/{CLIENT}/endpoints"))[0] == 200)
        browse()
    finally:
        grant("ACTIVE")
        e2e_http.platform_sql((DB / "acceptance-cleanup-local.sql").read_text())
        left = e2e_http.platform_sql("SELECT (SELECT status FROM t_role_permission WHERE role_permission_id=900000000000090202),"
                                     " (SELECT COUNT(*) FROM t_access_tenant WHERE tenant_id IN (880000000000079001,880000000000079002))")
        check("收尾", f"授权已恢复、合成数据已清理（{left}）", left == [["ACTIVE", "0"]])
        server.shutdown()
    print(f"\n{sum(r[2] for r in results)}/{len(results)} 通过")
    return 0 if all(r[2] for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
