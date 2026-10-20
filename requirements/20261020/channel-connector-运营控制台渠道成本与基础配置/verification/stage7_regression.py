"""阶段 7 第 D 节：既有页面回归（商务、财务）。经 mor-test 运营入口登录，按 Platform 导航返回的「商务」「财务」目录下全部 MENU 逐页打开：
标题可见、无错误态（加载失败 / 404 / 无权限）、无失败通知、无 5xx 响应；列表页有「详情」时打开第一行详情。每页截图到 assets/stage7/regression/。
只读，不提交任何写命令。不打印 Token 与口令。用法：E2E_TARGET=test python3 -I stage7_regression.py
"""
import json
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import local_acceptance as acc  # noqa: E402
import local_browser as b  # noqa: E402
from local_acceptance import e2e_http  # noqa: E402

OUT = HERE.parent / "assets/stage7/regression"
ERROR_TEXT = re.compile("加载失败|页面不存在|没有权限|无权访问|请求失败|服务暂不可用")
results = []


def check(section, name, ok, evidence=None):
    results.append((section, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {section} {name}" + ("" if ok or evidence is None else f" | {evidence}"), flush=True)


def menus(token):
    _, nav = acc.iam(token, "GET", "/authorization/v1/navigation")
    resources = nav.get("resources", [])
    out = {}
    for directory in ("OPERATIONS_COMMERCIAL", "OPERATIONS_FINANCE"):
        parent = next(r for r in resources if r["resourceCode"] == directory)
        children = sorted((r for r in resources if r.get("parentResourceId") == parent["resourceId"] and r["resourceType"] == "MENU"),
                          key=lambda r: r["sortOrder"])
        out[directory] = [(r["displayName"], r.get("frontendRoute") or r.get("route") or r.get("path"), r["resourceCode"]) for r in children]
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    token = acc.login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    tree = menus(token)
    print(json.dumps({k: [(n, r) for n, r, _ in v] for k, v in tree.items()}, ensure_ascii=False))
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900}).new_page()
        bad, limited = [], []
        page.on("response", lambda r: bad.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 500 else None)
        page.goto(b.ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        b.button(page, "登录").click()
        b.idle(page)
        for directory, label in (("OPERATIONS_COMMERCIAL", "商务"), ("OPERATIONS_FINANCE", "财务")):
            check("D", f"导航「{label}」目录 {len(tree[directory])} 个菜单：{[n for n, _, _ in tree[directory]]}", tree[directory])
            for index, (name, route, code) in enumerate(tree[directory], 1):
                bad.clear()
                if not route:
                    check("D", f"{label}/{name} 缺少前端路由", False, code)
                    continue
                page.wait_for_timeout(3000)  # 每页整页加载都会读导航；放慢节奏，避免 Gateway 管理路由限流
                page.goto(b.ORIGIN + route)
                b.idle(page)
                page.wait_for_timeout(800)
                if page.get_by_text("GATEWAY_RATE_LIMITED").count():
                    limited.append(route)
                    page.wait_for_timeout(15000)
                    bad.clear()
                    page.goto(b.ORIGIN + route)
                    b.idle(page)
                    page.wait_for_timeout(800)
                heading = page.locator(".page-heading h1, h1").first
                error = page.get_by_text(ERROR_TEXT)
                notices = page.locator(".ant-notification-notice").count()
                ok = b.visible(heading, 8000) and error.count() == 0 and notices == 0 and not bad
                page.screenshot(path=str(OUT / f"{label}-{index:02d}-{name.replace('/', '-')}.png"), full_page=True)
                check("D", f"{label}/{name}（{route}）打开正常", ok,
                      {"heading": heading.inner_text() if heading.count() else None, "errorText": error.count(), "notices": notices, "5xx": bad[:3]})
                detail = page.get_by_role("button", name=re.compile(r"^详\s?情$"))
                if detail.count():
                    bad.clear()
                    detail.first.click()
                    b.idle(page)
                    page.wait_for_timeout(800)
                    ok = page.get_by_text(ERROR_TEXT).count() == 0 and not bad and page.locator(".ant-notification-notice").count() == 0
                    page.screenshot(path=str(OUT / f"{label}-{index:02d}-{name.replace('/', '-')}-详情.png"), full_page=True)
                    check("D", f"{label}/{name} 第一行详情打开正常（{page.url.split(b.ORIGIN)[-1]}）", ok, {"5xx": bad[:3]})
        browser.close()
    passed = sum(1 for r in results if r[2])
    print(f"限流后重试的页面：{limited}")
    print(f"\n既有页面回归：{passed} / {len(results)} 通过；截图 {OUT}")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
