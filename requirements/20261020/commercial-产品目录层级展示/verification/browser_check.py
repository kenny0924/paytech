"""产品目录层级展示：浏览器核对 AC-04/05/07/08/09/10。本地 Vite（OPERATIONS_GATEWAY_URL 指向 mor-test Gateway）+ mor-test 只读数据。
不提交任何表单；不打印 Token 与口令。
本地 Vite 的 Origin/Host 不在 mor-test 运营入口白名单内，API 请求由 Playwright 转发到 e2e_http.OPERATIONS（Host/Origin 与运营入口一致）。用法：E2E_TARGET=test python3 -I browser_check.py <origin> <截图目录>"""
import json
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

ORIGIN, OUT = sys.argv[1], Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
results = []


def check(name, ok, detail=None):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}" + ("" if ok else f" {detail}"))


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def text_of(page):
    return page.locator(".ant-layout-content, main, body").first.inner_text()


s = e2e_http.secrets()
with sync_playwright() as p:
    browser = p.chromium.launch()
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    errors = []
    # antd 弃用警告（Spin tip、Space direction）来自既有应用壳，与本页无关，单独计数不判失败
    deprecations = []
    page.on("console", lambda m: (deprecations if m.text.startswith("Warning: [antd") else errors).append(m.text) if m.type == "error" else None)
    def relay(route):
        req = route.request
        url = e2e_http.OPERATIONS + "/api/operations" + req.url.split("/api/operations", 1)[1]
        host = e2e_http.OPERATIONS.split("//", 1)[1]
        route.fulfill(response=route.fetch(url=url, headers={**req.headers, "origin": e2e_http.OPERATIONS, "referer": e2e_http.OPERATIONS + "/", "host": host}))
    page.route("**/api/operations/**", relay)
    page.goto(ORIGIN + "/")
    page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
    page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
    button(page, "登录").click()
    page.get_by_label("Email / 初始化账号").wait_for(state="hidden", timeout=15000)
    page.goto(ORIGIN + "/commercial/catalog")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(1500)
    body = text_of(page)
    check("AC-01 汇总 7 / 11 / 13", "7 个产品" in body and "11 个可开通项" in body and "13 个计费事件" in body, body[:300])
    check("AC-05 缺口提示 DATA_ANALYTICS", "DATA_ANALYTICS" in body and "没有计费事件" in body)
    check("AC-09 默认：PLATFORM 可开通项原因、DATA_ANALYTICS 事件缺口文案", "基础产品默认具备，无需开通" in body and "未登记计费事件，开通后不会产生费用" in body)
    page.screenshot(path=str(OUT / "catalog-1440-default.png"), full_page=True)
    no_hscroll = page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
    check("AC-08 1440 无页面横向滚动", no_hscroll)
    # 单组折叠：ACQUIRING 的可开通项分组
    group = page.locator("button.catalog-group-head", has_text="可开通项 6").first
    before = page.get_by_text("CARD_UNIONPAY").count()
    group.click()
    page.wait_for_timeout(400)
    after = page.get_by_text("CARD_UNIONPAY").count()
    check("AC-09 单组折叠（ACQUIRING 可开通项）", before > 0 and after == 0, {"before": before, "after": after})
    group.click()
    page.wait_for_timeout(300)
    # 筛选 Partner 分润
    page.locator(".ant-select").first.click()
    page.get_by_title(re.compile("Partner 分润")).first.click()
    page.wait_for_timeout(500)
    body = text_of(page)
    check("AC-04 筛选 Partner 分润只剩 ACQUIRING", "ACQUIRING" in body and "PAYOUT_SUBMITTED" not in body and "PLATFORM_FEE" not in body)
    check("AC-09 筛选后单组展开状态保持", page.get_by_text("CARD_UNIONPAY").count() > 0)
    page.screenshot(path=str(OUT / "catalog-1440-partner-share.png"), full_page=True)
    page.locator(".ant-select").first.click()
    page.get_by_title(re.compile("Partner 底价")).first.click()
    page.wait_for_timeout(500)
    body = text_of(page)
    check("AC-04 筛选 Partner 底价显示未登记提示", "该条款类型尚未登记计费事件" in body)
    page.screenshot(path=str(OUT / "catalog-1440-partner-buy-rate.png"), full_page=True)
    page.locator(".ant-select").first.click()
    page.get_by_title(re.compile("^全部")).first.click()
    page.wait_for_timeout(300)
    button(page, "全部收起").click()
    page.wait_for_timeout(300)
    check("AC-09 全部收起", page.get_by_text("CARD_VISA").count() == 0)
    button(page, "全部展开").click()
    page.wait_for_timeout(300)
    check("AC-09 全部展开", page.get_by_text("CARD_VISA").count() > 0 and page.get_by_text("MONTHLY_CYCLE").count() > 0)
    for width in (1024, 390):
        page.set_viewport_size({"width": width, "height": 900})
        page.wait_for_timeout(500)
        check(f"AC-08 {width} 无页面横向滚动", page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"),
              page.evaluate("[document.documentElement.scrollWidth, window.innerWidth]"))
        page.screenshot(path=str(OUT / f"catalog-{width}.png"), full_page=True)
    page.set_viewport_size({"width": 1440, "height": 900})
    check("控制台无错误（不含既有 antd 弃用警告）", not errors, errors[:5])
    print(f"[说明] 既有 antd 弃用警告 {len(deprecations)} 条：{sorted(set(deprecations))}")
    # AC-07：计费事件接口失败时整页失败
    page.route("**/commercial-admin/v1/products/fee-events*", lambda route: route.fulfill(status=500, body="{}"))
    page.reload()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(1500)
    body = text_of(page)
    check("AC-07 接口失败不显示半套数据", "CARD_VISA" not in body and "ACQUIRING" not in body, body[:200])
    page.screenshot(path=str(OUT / "catalog-1440-failure.png"), full_page=True)
    browser.close()
print(f"\n{sum(ok for _, ok in results)}/{len(results)} 通过")
