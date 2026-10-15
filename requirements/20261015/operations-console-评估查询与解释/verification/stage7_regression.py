"""阶段 7 回归（应用内切换路由，避开登录上下文的单操作者限流）：管理员登录 mor-test 运营入口，逐个打开 pageCapabilities 中的全部页面，统计失败请求与控制台错误（只读）。
用法：E2E_TARGET=test python3 -I stage7_regression.py <路由清单文件>
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

ROUTES = [r for r in Path(sys.argv[1]).read_text().split() if r]


def main():
    s = e2e_http.secrets()
    bad = {}
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        current = {"route": "login"}
        page.on("response", lambda r: bad.setdefault(current["route"], []).append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.on("console", lambda m: bad.setdefault(current["route"], []).append("console: " + m.text[:120]) if m.type == "error" and not m.text.startswith("Warning: [antd:") else None)
        page.goto(e2e_http.OPERATIONS + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        page.get_by_role("button", name=re.compile("^登\\s?录$")).click()
        page.wait_for_load_state("networkidle")
        for route in ROUTES:
            current["route"] = route
            # 应用内切换路由，不整页重载：登录上下文只读取一次，避免触发其单操作者限流。
            page.evaluate("r => { history.pushState({}, '', r); dispatchEvent(new PopStateEvent('popstate')); }", route)
            page.wait_for_load_state("networkidle")
            page.wait_for_timeout(1500)
            if not page.url.split("?")[0].endswith(route):  # 页面可能补默认查询参数
                bad.setdefault(route, []).append("未能切换到该路由: " + page.url)
        browser.close()
    for route in ROUTES:
        print(("[失败] " if route in bad else "[通过] ") + route + ("：" + "; ".join(bad[route][:3]) if route in bad else ""))
    ok = sum(1 for r in ROUTES if r not in bad)
    print(f"{ok}/{len(ROUTES)} 页面无失败请求")
    sys.exit(0 if ok == len(ROUTES) else 1)


if __name__ == "__main__":
    main()
