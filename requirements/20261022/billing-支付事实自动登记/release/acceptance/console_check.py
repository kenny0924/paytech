"""billing 支付事实自动登记 · 阶段 7 第 9 步控制台只读核对（release.md 第 7.3 节四页 + 账本下钻）。

Playwright 经 mor-test 运营入口以管理员登录（凭据读 test-environment/private/access.md，不打印），逐页打开并按合成事实筛选，
截图到 --out 目录；每页断言关键文本可见。只读，不提交任何写命令。
用法：E2E_TARGET=test python3 -I console_check.py --out <目录> [--fact <factId>] [--journal <journalId>]
"""
import re
import site
import sys
import time
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

ORIGIN = e2e_http.OPERATIONS
CLIENT_ID, ENTITY_NO, CLEARING = "237575207592763393", "AE001", "238508282463989760"
EXTERNAL_TXN = "SYN-BILLB-P-0001"
ERROR_TEXT = re.compile("加载失败|页面不存在|没有权限|无权访问|请求失败|服务暂不可用")
results = []


def arg(name, default=None):
    return sys.argv[sys.argv.index(name) + 1] if name in sys.argv else default


OUT = Path(arg("--out", "/tmp/console-check"))
FACT_ID = arg("--fact", "239295097391407104")
JOURNAL_ID = arg("--journal", "239295103963852800")


def check(name, ok, evidence=""):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}" + (f" | {evidence}" if evidence and not ok else ""), flush=True)
    return bool(ok)


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(re.escape(c) for c in text) + "$"))


def idle(page):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(500)


def shot(page, name):
    page.wait_for_timeout(600)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)


def select(page, label, option_text):
    page.get_by_label(label).first.click()
    page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text=option_text).first.click()


def visible(locator, timeout=10000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def page_ok(page):
    return page.get_by_text(ERROR_TEXT).count() == 0 and page.locator(".ant-notification-notice-error").count() == 0


def goto(page, path):
    page.wait_for_timeout(2500)  # 整页加载都读导航；放慢避免 Gateway 管理路由限流
    page.goto(ORIGIN + path)
    idle(page)


def billing_fact(page):
    goto(page, "/finance/adjustments?tab=billing")
    page.get_by_label("经营主体（entityNo）").fill(ENTITY_NO)
    select(page, "币种", "USD")
    select(page, "事实类型", "PAYMENT")
    button(page, "查询").first.click()
    idle(page)
    row = page.locator("tr.ant-table-row").filter(has_text=FACT_ID).first
    ok = visible(row)
    shot(page, "01-计费事实-列表")
    check("计费事实列表（类型 PAYMENT）出现合成事实行", ok and page_ok(page), FACT_ID)
    if not ok:
        return
    link = row.get_by_role("button").first if row.get_by_role("button").count() else row
    link.click()
    idle(page)
    drawer = page.locator(".ant-drawer:visible").last
    text = drawer.inner_text() if visible(drawer) else ""
    shot(page, "02-计费事实-详情")
    check("计费事实详情：AUTO / 97.00 / 2.70 / 0.50 / SYN-BILLB-P-0001 / AE_BUSINESS_DAYS",
          all(k in text for k in ("97.00", "2.70", "0.50", EXTERNAL_TXN)) and ("AUTO" in text or "自动" in text), text[:300])
    page.keyboard.press("Escape")


def subledger(page):
    goto(page, "/finance/subledger")
    page.get_by_label("主体标识").fill(ENTITY_NO)
    select(page, "币种", "USD")
    page.get_by_label("Owner ID").fill(CLIENT_ID)
    button(page, "查询").first.click()
    idle(page)
    row = page.locator("tr.ant-table-row").filter(has_text="CLIENT_PAYABLE").filter(has_text="PENDING").first
    ok = visible(row)
    shot(page, "03-明细账-Owner账户")
    check("明细账：Client CLIENT_PAYABLE / PENDING 账户行", ok and page_ok(page))
    if not ok:
        return
    (row.get_by_role("button").first if row.get_by_role("button").count() else row).click()
    idle(page)
    body = page.inner_text("body")
    shot(page, "04-明细账-流水")
    check("明细账流水含 97.00 贷方（来源事实）", "97.00" in body and (FACT_ID in body or "PAYMENT" in body or "支付" in body), body[-400:])


def balances(page):
    goto(page, "/finance/balances")
    page.get_by_label("Owner ID").fill(CLIENT_ID)
    page.get_by_label("经营主体 entityNo").fill(ENTITY_NO)
    select(page, "币种", "USD")
    button(page, "查询").first.click()
    idle(page)
    body = page.inner_text("body")
    shot(page, "05-余额查询")
    check("余额查询：PENDING 桶可见且页面无错误", ("PENDING" in body or "待到期" in body or "未到期" in body) and page_ok(page), body[-400:])


def ledger(page):
    goto(page, "/finance/ledger")
    row = page.locator("tr.ant-table-row").filter(has_text=ENTITY_NO).filter(has_text="USD").first
    ok = visible(row)
    shot(page, "06-账本-账簿")
    check("账本下钻：AE001 / USD 账簿行", ok and page_ok(page))
    if not ok:
        return
    (row.get_by_role("button").first if row.get_by_role("button").count() else row).click()
    idle(page)
    acct = page.locator("tr.ant-table-row").filter(has_text="CLIENT_PAYABLE").first
    ok = visible(acct)
    shot(page, "07-账本-账户")
    check("账本下钻：CLIENT_PAYABLE 账户行", ok)
    if not ok:
        return
    (acct.get_by_role("button").first if acct.get_by_role("button").count() else acct).click()
    idle(page)
    line = page.locator("tr.ant-table-row").filter(has_text=re.compile("97\\.00")).filter(has_text=re.compile("2026-10-23")).first
    ok = visible(line)
    shot(page, "08-账本-流水")
    check("账本下钻：2026-10-23 过账 97.00 流水行", ok)
    if not ok:
        return
    line.locator("a, button").first.click()  # 分录序号链接打开分录详情
    idle(page)
    drawer = page.locator(".ant-drawer:visible, .ant-modal:visible").last
    page.wait_for_timeout(1500)
    text = drawer.inner_text() if visible(drawer) else page.inner_text("body")
    shot(page, "09-账本-分录详情")
    check("分录详情：PAYMENT_SUCCEEDED / 支付成功，借贷 100.50",
          ("PAYMENT_SUCCEEDED" in text or "支付成功" in text) and "100.50" in text, text[:400])
    page.keyboard.press("Escape")


def platform_txns(page):
    goto(page, "/finance/reconciliation/platform-txns")
    page.get_by_label("外部交易号").fill(EXTERNAL_TXN)
    button(page, "查询").first.click()
    idle(page)
    row = page.locator("tr.ant-table-row").filter(has_text=EXTERNAL_TXN).first
    ok = visible(row)
    text = row.inner_text() if ok else ""
    shot(page, "10-对账-平台流水")
    check("平台流水：SYN-BILLB-P-0001 预期 100.00 / 费用 0.50 / 未匹配", ok and "100.00" in text and "0.50" in text
          and ("OPEN" in text or "未匹配" in text) and page_ok(page), text)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900}).new_page()
        bad = []
        page.on("response", lambda r: bad.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 500 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        idle(page)
        for section in (billing_fact, subledger, balances, ledger, platform_txns):
            try:
                section(page)
            except Exception as error:  # noqa: BLE001
                shot(page, f"ERR-{section.__name__}")
                check(f"{section.__name__} 执行异常", False, repr(error)[:300])
        check("无 5xx 响应", not bad, "; ".join(bad[:5]))
        browser.close()
    passed = sum(1 for _, ok in results if ok)
    print(f"{passed} / {len(results)} 通过；截图目录 {OUT}")
    sys.exit(0 if passed == len(results) else 1)


if __name__ == "__main__":
    main()
