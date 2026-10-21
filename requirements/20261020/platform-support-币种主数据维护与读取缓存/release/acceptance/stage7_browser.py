"""币种主数据阶段 7：mor-test 只读浏览器冒烟（无头 Chromium，经运营入口 http://localhost:28080 真实登录）。

只读：只做菜单导航与列表查看，不点击新建 / 编辑 / 启用 / 停用，不改数据。
1. 平台配置 → 币种：列表含 USD（2 位、HALF_UP、启用），管理员可见新建与停用入口。
2. 回归：对账七页、商务九页、财务七页（含账簿与人工调整等用到币种的页面）、渠道三页各打开一次，确认无错误提示。
3. 收集控制台错误与 4xx/5xx 网络请求。
管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。改写自对账切片 1 的同名脚本。
用法：python3 -I stage7_browser.py
"""
import json
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
ORIGIN = "http://localhost:28080"
OUT = HERE / "screenshots"
OUT.mkdir(exist_ok=True)

RECON = ["clearing-accounts", "cash-accounts", "statements", "platform-txns", "bank", "exceptions", "summary"]
COMMERCIAL = ["agreements", "pricing-profiles", "terms", "templates", "workbench", "rate-cards",
              "price-simulator", "catalog", "history"]
FINANCE = ["setup", "ledger", "balances", "subledger", "adjustments", "period-close", "accounting-rules"]
CHANNEL = ["channels", "mids", "cost-plans"]
ERROR_SELECTORS = ".ant-alert-error, .ant-result-error, .ant-result-500, .ant-result-403, .ant-result-404, " \
                  ".ant-message-error, .ant-notification-notice-error"
KNOWN = "[antd: Spin] `tip` is deprecated"


def credentials():
    text = (ROOT / "test-environment/private/access.md").read_text()
    email = re.search(r"账号[:：]\s*(\S+)", text).group(1)
    password = re.search(r"密码[:：]\s*(\S+)", text).group(1)
    return email, password


def visible(locator, timeout=8000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def settle(page):
    try:
        page.wait_for_load_state("networkidle", timeout=20000)
    except Exception:  # noqa: BLE001
        pass
    page.wait_for_timeout(1200)


def shot(page, name):
    page.wait_for_timeout(600)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
    return f"{name}.png"


def errors_on(page):
    return [t.strip().replace("\n", " ")[:200] for t in page.locator(ERROR_SELECTORS).all_inner_texts() if t.strip()]


def main():
    email, password = credentials()
    results, console, network, shots = [], [], [], []

    def record(page_name, ok, note):
        results.append({"page": page_name, "ok": bool(ok), "note": note})
        print(f"[{'通过' if ok else '失败'}] {page_name}：{note}", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": 1512, "height": 900})
        page = context.new_page()
        current = {"path": "/"}
        page.on("console", lambda m: console.append((current["path"], m.type, m.text))
                if m.type in ("error", "warning") else None)
        page.on("response", lambda r: network.append((current["path"], r.status, r.request.method, r.url))
                if r.status >= 400 else None)
        page.on("requestfailed", lambda r: network.append((current["path"], "FAILED", r.method, r.url + " " + str(r.failure))))

        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(email)
        page.get_by_label("密码").fill(password)
        page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
        settle(page)
        logged_in = not visible(page.get_by_label("密码"), 2000)
        record("登录", logged_in, "管理员登录")
        if not logged_in:
            shots.append(shot(page, "00-login-failed"))
            browser.close()
            return dump(results, console, network, shots)

        def goto(path):
            current["path"] = path
            page.goto(ORIGIN + path)
            settle(page)

        # ---------- 1. 平台配置 → 币种 ----------
        goto("/currency-specs")
        notes = []
        table = visible(page.locator(".ant-table-tbody"), 10000)
        usd = page.locator(".ant-table-tbody tr.ant-table-row", has_text="USD")
        has_usd = visible(usd, 5000)
        usd_text = usd.first.inner_text().replace("\n", " ") if has_usd else ""
        notes.append("USD 行：" + usd_text[:120])
        create = visible(page.get_by_role("button", name=re.compile("新建")), 5000)
        deactivate = visible(usd.get_by_role("button", name=re.compile(r"停\s?用")), 3000) if has_usd else False
        notes.append("新建按钮" + ("可见" if create else "缺失") + "，USD 停用入口" + ("可见" if deactivate else "缺失"))
        errs = errors_on(page)
        shots.append(shot(page, "p01-currency-specs"))
        ok = table and has_usd and "HALF_UP" in usd_text and create and deactivate and not errs
        if errs:
            notes.append("错误提示：" + "; ".join(errs))
        record("平台配置/币种", ok, "，".join(notes))

        # ---------- 2. 回归 ----------
        groups = [("对账", "finance/reconciliation", RECON, "r"), ("商务", "commercial", COMMERCIAL, "c"), ("财务", "finance", FINANCE, "f"), ("渠道", "channel", CHANNEL, "ch")]
        for label, prefix, slugs, tag in groups:
            for index, slug in enumerate(slugs, 1):
                path = f"/{prefix}/{slug}"
                goto(path)
                errs = errors_on(page)
                body = page.locator(".ant-layout-content, main").first
                content = visible(body, 5000)
                not_found = visible(page.get_by_text(re.compile("页面不存在|无权访问|404")), 1000)
                shots.append(shot(page, f"{tag}{index:02d}-{slug}"))
                ok = content and not errs and not not_found
                note = "渲染正常" if ok else ("错误提示：" + "; ".join(errs) if errs else "未渲染/不存在或无权")
                record(f"{label}/{slug}", ok, note)

        current["path"] = "menu"
        menu = [t.strip() for t in page.locator(".ant-menu-item").all_inner_texts() if t.strip()]
        browser.close()
    return dump(results, console, network, shots, menu)


def dump(results, console, network, shots, menu=None):
    relevant = [c for c in console if KNOWN not in c[2] and c[1] == "error"]
    known = [c for c in console if KNOWN in c[2]]
    warnings = [c for c in console if c[1] == "warning" and KNOWN not in c[2]]
    data = {"results": results, "consoleErrors": relevant, "consoleWarnings": warnings, "knownSpinTip": len(known),
            "network": network, "shots": shots, "menu": menu}
    (HERE / "stage7-browser-result.json").write_text(json.dumps(data, ensure_ascii=False, indent=2))
    passed = sum(1 for r in results if r["ok"])
    print(f"\n{passed}/{len(results)} 通过；控制台错误 {len(relevant)}（Spin tip {len(known)} 条单列），"
          f"警告 {len(warnings)}；4xx/5xx/失败请求 {len(network)}")
    for c in relevant:
        print("  console:", c[0], c[2][:300])
    for n in network:
        print("  network:", n)


if __name__ == "__main__":
    main()
