"""两需求合并发布后 mor-test 运营页面只读回归：逐页打开发布源码（集成 worktree）pageCapabilities.ts 中的全部页面，统计控制台错误、HTTP ≥ 400 与失败请求。
页面清单取自参数给出的 pageCapabilities.ts（主工作区含对账会话未发布页面，不能用）。每页停 7 秒避开 Gateway 登录上下文限流。不打印 Token 与口令。
用法：E2E_TARGET=test python3 -I stage7_regression.py <pageCapabilities.ts> <截图目录>"""
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

routes = re.findall(r'"frontendRoute": "([^"]+)"', Path(sys.argv[1]).read_text())
out = Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
s = e2e_http.secrets()
passed = 0
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page(viewport={"width": 1440, "height": 900})
    errors, failed = [], []
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
    page.on("requestfailed", lambda r: failed.append(f"FAILED {r.method} {r.url.split('?')[0]}"))
    page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
    page.goto(e2e_http.OPERATIONS + "/")
    page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
    page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
    page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
    page.get_by_label("Email / 初始化账号").wait_for(state="hidden", timeout=15000)
    page.wait_for_timeout(7000)
    for route in routes:
        errors.clear(); failed.clear()
        page.goto(e2e_http.OPERATIONS + route)
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(7000)
        denied = "无权限" in page.locator("body").inner_text()
        ok = not errors and not failed and not denied
        passed += ok
        print(f"[{'通过' if ok else '失败'}] {route}" + ("" if ok else f" errors={errors[:2]} http={failed[:3]} denied={denied}"))
        if route.startswith("/commercial"):
            page.screenshot(path=str(out / ("regression-" + route.strip("/").replace("/", "-") + ".png")))
    browser.close()
print(f"\n{passed}/{len(routes)} 通过")
