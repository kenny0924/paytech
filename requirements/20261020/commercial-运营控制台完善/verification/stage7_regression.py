"""commercial 第二切片阶段 7 mor-test 运营页面只读回归（复制自首切片 stage7_regression.py）：登录后逐个打开 pageCapabilities.ts 中的全部运营页面，
只读不提交任何表单；再打开 STAGE7_EXTRA_ROUTES（逗号分隔）给出的深链接页面（版本对比、版本工作区、合同详情等），深链接另要求页面不出现「无权限」（冒烟 D1）。

每页 page.goto + networkidle，之后停 7 秒（避免 Gateway 对登录上下文接口的单账号限流 10 次/分钟）；
统计该页期间的控制台错误、HTTP ≥ 400 响应与失败请求，逐页打印，最后输出“N/N 通过”。
为商务八页、三个财务页与全部深链接截图，写入第一个参数指定的目录。页面清单读脚本所在仓库的 pageCapabilities.ts，必须在发布源码树中运行。
连接目标由 E2E_TARGET 决定（见 e2e_http.py）；不打印 Token 与口令。
用法：E2E_TARGET=test STAGE7_EXTRA_ROUTES=/commercial/terms/compare/<a>/<b>,... python3 -I stage7_regression.py <截图目录>
"""
import os
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

ORIGIN = e2e_http.OPERATIONS
FINANCE_SHOTS = {route: "regression-" + route.strip("/").replace("/", "-") for route in (
    "/commercial/workbench", "/commercial/agreements", "/commercial/terms", "/commercial/rate-cards", "/commercial/price-simulator",
    "/commercial/pricing-profiles", "/commercial/catalog", "/commercial/history", "/finance/setup", "/finance/ledger", "/finance/balances")}
EXTRA_ROUTES = [r for r in os.environ.get("STAGE7_EXTRA_ROUTES", "").split(",") if r]
PAUSE_MS = 7000


def button(scope, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def routes():
    text = (ROOT / "operations-console-web/src/navigation/pageCapabilities.ts").read_text()
    return re.findall(r'"frontendRoute": "([^"]+)"', text)


def login(page, s):
    page.goto(ORIGIN + "/")
    page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
    page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
    button(page, "登录").click()
    page.wait_for_load_state("networkidle")
    try:
        page.get_by_label("Email / 初始化账号").wait_for(state="hidden", timeout=10000)
        return True
    except Exception:  # noqa: BLE001 - 登录表单仍在即判定登录失败
        return False


def main():
    if len(sys.argv) < 2:
        raise SystemExit("用法：E2E_TARGET=test python3 -I stage7_regression.py <截图目录>")
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    pages = routes()
    if not pages:
        raise SystemExit("pageCapabilities.ts 中没有 frontendRoute")
    s = e2e_http.secrets()
    errors, failed = [], []
    passed = 0
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page = context.new_page()
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("requestfailed", lambda r: failed.append(f"FAILED {r.method} {r.url.split('?')[0]} {r.failure}"))
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        if not login(page, s):
            print(f"[失败] 登录 {ORIGIN}（登录表单未消失）")
            browser.close()
            return 1
        page.wait_for_timeout(PAUSE_MS)
        errors.clear()
        failed.clear()
        print(f"[通过] 登录 {ORIGIN}")

        for route in pages + EXTRA_ROUTES:
            error_mark, failed_mark = len(errors), len(failed)
            try:
                page.goto(ORIGIN + route)
                page.wait_for_load_state("networkidle")
                navigation = None
            except Exception as failure:  # noqa: BLE001 - 导航超时 / 中断计入本页失败
                navigation = str(failure).splitlines()[0][:200]
            denied = False
            if route in EXTRA_ROUTES and navigation is None:
                page.wait_for_timeout(1500)
                denied = "无权限" in page.inner_text("body")
                name = "deep-" + "-".join(part for part in route.strip("/").split("/")[:3])
                page.screenshot(path=str(out / f"{name}.png"), full_page=True)
            elif route in FINANCE_SHOTS and navigation is None:
                page.wait_for_timeout(600)
                page.screenshot(path=str(out / f"{FINANCE_SHOTS[route]}.png"), full_page=True)
            page.wait_for_timeout(PAUSE_MS)
            page_errors, page_failed = errors[error_mark:], failed[failed_mark:]
            ok = navigation is None and not page_errors and not page_failed and not denied
            passed += ok
            detail = "" if ok else f"（无权限：{denied}；导航：{navigation}；≥400 / 失败请求：{page_failed[:3]}；控制台错误：{page_errors[:2]}）"
            print(f"[{'通过' if ok else '失败'}] {route}{detail}")
        browser.close()
    total = len(pages) + len(EXTRA_ROUTES)
    print(f"\n{passed}/{total} 通过（pageCapabilities {len(pages)} 页 + 深链接 {len(EXTRA_ROUTES)} 页）")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
