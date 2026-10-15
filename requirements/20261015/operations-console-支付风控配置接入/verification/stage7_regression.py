"""阶段 7 只读回归：以管理员登录 mor-test 运营入口，逐个打开侧栏全部页面，记录 HTTP ≥ 400 与控制台错误。

不做写操作；不打印凭据。经菜单在单页应用内跳转，不整页刷新，避免触发 auth/v1/context 限流。
用法：E2E_TARGET=test python3 -I stage7_regression.py
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

ORIGIN = e2e_http.OPERATIONS


def main():
    s = e2e_http.secrets()
    failed, errors, opened = [], [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" and not m.text.startswith("Warning: [antd:") else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
        page.wait_for_load_state("networkidle")
        groups = [g.strip() for g in page.locator(".ant-menu-submenu-title").all_inner_texts()]
        for group in groups:
            # 侧栏为手风琴：每组先展开，再逐项点击该组可见的页面。
            submenu = page.locator(".ant-menu-submenu", has=page.locator(".ant-menu-submenu-title", has_text=group)).first
            if "ant-menu-submenu-open" not in (submenu.get_attribute("class") or ""):
                submenu.locator(".ant-menu-submenu-title").click()
                page.wait_for_timeout(400)
            for name in [n.strip() for n in submenu.locator(".ant-menu-item").all_inner_texts()]:
                submenu.locator(".ant-menu-item", has_text=name).first.click()
                page.wait_for_load_state("networkidle")
                page.wait_for_timeout(1500)
                opened.append((f"{group}/{name}", page.url.replace(ORIGIN, "")))
        for name in [n.strip() for n in page.locator(".ant-menu-root > .ant-menu-item").all_inner_texts()]:
            page.locator(".ant-menu-root > .ant-menu-item", has_text=name).first.click()
            page.wait_for_load_state("networkidle")
            page.wait_for_timeout(1500)
            opened.append((name, page.url.replace(ORIGIN, "")))
        browser.close()
    for name, path in opened:
        print(f"打开 {name} {path}")
    print(f"共 {len(opened)} 页；HTTP ≥400：{failed}；控制台错误：{errors[:5]}")
    sys.exit(0 if opened and not failed and not errors else 1)


if __name__ == "__main__":
    main()
