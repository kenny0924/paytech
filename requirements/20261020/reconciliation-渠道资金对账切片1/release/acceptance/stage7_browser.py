"""对账切片 1 阶段 7：mor-test 只读浏览器验收（无头 Chromium，经运营入口 http://localhost:28080 真实登录）。

只读：只做菜单导航、列表查看、点击行首单元格打开详情抽屉、关闭抽屉；不点击任何新建/提交/批准/作废/删除/上传按钮，不改数据。
1. 财务 → 对账 七个页面：列表渲染、详情抽屉（查看）、账单详情门禁项、异常列表账龄。
2. 回归：商务菜单页、原财务七页、渠道三页各打开一次，确认无错误提示。
3. 收集控制台错误（忽略 App.tsx 既有 Spin tip 弃用警告）与 4xx/5xx 网络请求。
管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。
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

RECON = [
    ("clearing-accounts", "清算账户"), ("cash-accounts", "银行账户"), ("statements", "渠道账单"),
    ("platform-txns", "平台流水"), ("bank", "银行对账"), ("exceptions", "对账异常"), ("summary", "在途汇总"),
]
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


def open_first_row(page):
    """点击第一条可点击行的首个单元格（避开行内操作按钮），返回打开的抽屉或 None。"""
    rows = page.locator(".ant-table-tbody tr.clickable-row, .ant-table-tbody tr.ant-table-row")
    if rows.count() == 0:
        return None
    cell = rows.first.locator("td").first
    cell.click()
    drawer = page.locator(".ant-drawer:visible")
    return drawer if visible(drawer, 8000) else None


def close_drawer(page):
    page.keyboard.press("Escape")
    page.wait_for_timeout(600)


def handle_bank(page, index, notes, shots):
    """选择第一个银行账户，逐个查看「银行流水 / 对账单 / 预期银行流水」页签；在银行流水页签打开首行详情（只看）。"""
    page.locator(".ant-select").first.click()
    option = page.locator(".ant-select-dropdown:visible .ant-select-item-option")
    if not visible(option, 5000):
        notes.append("无可选银行账户")
        return False
    notes.append("银行账户：" + option.first.inner_text().strip())
    option.first.click()
    settle(page)
    ok = True
    for key, label in (("lines", "银行流水"), ("statements", "对账单"), ("movements", "预期银行流水")):
        page.get_by_role("tab", name=label, exact=True).click()
        settle(page)
        rows = page.locator(".ant-tabs-tabpane-active .ant-table-tbody tr.ant-table-row").count()
        notes.append(f"{label} {rows} 行")
        shots.append(shot(page, f"r{index:02d}-bank-{key}"))
        if key == "lines" and rows:
            drawer = open_first_row(page)
            notes.append("流水详情抽屉" + ("已打开" if drawer else "未打开"))
            ok = ok and drawer is not None
            if drawer is not None:
                settle(page)
                shots.append(shot(page, f"r{index:02d}-bank-line-detail"))
                close_drawer(page)
    return ok


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

        # ---------- 1. 对账七页 ----------
        for index, (slug, title) in enumerate(RECON, 1):
            path = f"/finance/reconciliation/{slug}"
            goto(path)
            notes = []
            table = page.locator(".ant-table-tbody")
            rendered = visible(table, 10000) or visible(page.locator(".ant-card, .ant-descriptions"), 3000)
            rows = page.locator(".ant-table-tbody tr.ant-table-row").count()
            notes.append(f"列表 {rows} 行")
            ok = rendered
            if slug == "exceptions":
                age = visible(page.get_by_role("columnheader", name="账龄"), 5000)
                notes.append("账龄列" + ("存在" if age else "缺失"))
                ok = ok and age
            if slug == "summary":
                # 只读查询（GET），币种默认 USD
                page.get_by_role("button", name=re.compile(r"^查\s?询$")).click()
                settle(page)
                rows = page.locator(".ant-table-tbody tr.ant-table-row").count()
                notes.append(f"查询后汇总 {rows} 行")
                ok = rows > 0
            if slug == "bank":
                ok = handle_bank(page, index, notes, shots)
            errs = errors_on(page)
            shots.append(shot(page, f"r{index:02d}-{slug}"))
            if rows and slug not in ("summary", "bank", "platform-txns"):
                drawer = open_first_row(page)
                notes.append("详情抽屉" + ("已打开" if drawer else "未打开"))
                ok = ok and drawer is not None
                if drawer is not None:
                    settle(page)
                    if slug == "statements":
                        gates = drawer.locator(".recon-gate-item").all_inner_texts()
                        notes.append(f"门禁项 {len(gates)} 条：" + " / ".join(g.replace(chr(10), ' ') for g in gates)[:200])
                        ok = ok and len(gates) > 0
                    errs += errors_on(page)
                    shots.append(shot(page, f"r{index:02d}-{slug}-detail"))
                    close_drawer(page)
            if slug == "platform-txns":
                notes.append("平台流水页按设计无详情抽屉（ReconPlatformTxns.tsx 无 Drawer）")
            if errs:
                notes.append("错误提示：" + "; ".join(errs))
                ok = False
            record(f"对账/{title}", ok, "，".join(notes))

        # ---------- 2. 回归 ----------
        groups = [("商务", "commercial", COMMERCIAL, "c"), ("财务", "finance", FINANCE, "f"), ("渠道", "channel", CHANNEL, "ch")]
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

        # 菜单中可见的商务入口（核对 8 个）
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
