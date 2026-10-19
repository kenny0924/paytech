"""阶段 5 本机运营页面只读回归：登录后逐个打开 pageCapabilities.ts 中的全部运营页面（本需求 33 → 34 页），只读不提交任何表单。

照抄 requirements/20261018/operations-console-财务基础资料与账本余额查询/verification/test_regression.py：
每页 page.goto + networkidle，之后停 7 秒（避免 Gateway 对登录上下文接口的单账号限流）；统计该页期间的控制台错误、
HTTP ≥ 400 响应与失败请求，逐页打印，最后输出“N/N 通过”，并断言页面总数为 34。
为四个财务页面（/finance/setup、/finance/ledger、/finance/balances、/finance/subledger）截图，写入第一个参数指定的目录。
连接目标由 E2E_TARGET 决定（缺省 local，见 e2e_http.py）；不打印 Token 与口令。
用法：python3 -I test_regression.py <截图目录>
"""
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
EXPECTED_PAGES = 34
FINANCE_SHOTS = {"/finance/setup": "regression-finance-setup", "/finance/ledger": "regression-finance-ledger",
                 "/finance/balances": "regression-finance-balances", "/finance/subledger": "regression-finance-subledger"}
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
        raise SystemExit("用法：python3 -I test_regression.py <截图目录>")
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    pages = routes()
    print(f"[{'通过' if len(pages) == EXPECTED_PAGES else '失败'}] pageCapabilities.ts 页面数 {len(pages)}/{EXPECTED_PAGES}，"
          f"含 /finance/subledger：{'/finance/subledger' in pages}")
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

        for route in pages:
            error_mark, failed_mark = len(errors), len(failed)
            try:
                page.goto(ORIGIN + route)
                page.wait_for_load_state("networkidle")
                navigation = None
            except Exception as failure:  # noqa: BLE001 - 导航超时 / 中断计入本页失败
                navigation = str(failure).splitlines()[0][:200]
            if route in FINANCE_SHOTS and navigation is None:
                page.wait_for_timeout(600)
                page.screenshot(path=str(out / f"{FINANCE_SHOTS[route]}.png"), full_page=True)
            page.wait_for_timeout(PAUSE_MS)
            page_errors, page_failed = errors[error_mark:], failed[failed_mark:]
            # antd 开发模式弃用告警（console.error 输出）不属于页面错误
            page_errors = [e for e in page_errors if not e.startswith("Warning: [antd:")]
            ok = navigation is None and not page_errors and not page_failed
            passed += ok
            detail = "" if ok else f"（导航：{navigation}；≥400 / 失败请求：{page_failed[:3]}；控制台错误：{page_errors[:2]}）"
            print(f"[{'通过' if ok else '失败'}] {route}{detail}")
        browser.close()
    print(f"\n{passed}/{len(pages)} 通过")
    return 0 if passed == len(pages) == EXPECTED_PAGES else 1


if __name__ == "__main__":
    sys.exit(main())
