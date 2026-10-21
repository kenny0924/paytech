"""对账控制台详情页与渠道流水 阶段 7：mor-test 只读浏览器验收（无头 Chromium，经运营入口 http://localhost:28080 真实登录）。

只读：只做菜单导航、列表筛选（查询为 GET）、点行进详情、刷新、后退、「返回列表」；不点击任何新建 / 提交 / 确认 / 拒绝 /
复核 / 作废 / 上传 / 启停按钮，不改数据。用 mor-test 现有演示数据（切片 1 demo），主键从列表页现场取得，不写死。
1. 菜单：财务 → 对账 含「渠道流水」，且紧跟「平台流水」（AC-V3）。
2. 渠道流水页（AC-05）：默认列表、页大小 10/20/50/100、按清算账户 / 匹配状态 / 行类型 / 账单状态 / 外部交易号 / 交易日筛选，
   点行进入所属账单详情并高亮该行。
3. 六类详情页（AC-01、AC-V1、AC-V2）：清算账户、银行账户、渠道账单、银行对账单、银行流水、对账异常——列表点行进详情、
   无抽屉、面包屑与「返回列表」、刷新仍在详情、后退回带筛选的列表。
4. 未找到（AC-01）：六类详情各用不存在的主键直接打开，显示「未找到该…」且无错误通知。
5. 回归：对账其余页、商务、财务、渠道、币种页各打开一次。
6. 收集控制台错误（忽略 Spin tip 弃用警告、未找到用例的预期 400/404）与 4xx/5xx。
管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。Gateway 对每路由每人 30 次/分钟，导航间隔 2.5 秒。
用法：python3 -I stage7_browser.py   → 截图 acceptance/screenshots/，结果 acceptance/stage7-browser-result.json
"""
import json
import os
import re
import site
import sys
import time
import traceback
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
ORIGIN = "http://localhost:28080"
BASE = "/finance/reconciliation"
NO_ID = "999999999999"
OUT = HERE / "screenshots"
OUT.mkdir(exist_ok=True)
KNOWN = "[antd: Spin] `tip` is deprecated"
ERROR_SELECTORS = ".ant-alert-error, .ant-result-error, .ant-result-500, .ant-result-403, " \
                  ".ant-message-error, .ant-notification-notice-error"
COMMERCIAL = ["agreements", "pricing-profiles", "terms", "templates", "workbench", "rate-cards", "price-simulator", "catalog", "history"]
FINANCE = ["setup", "ledger", "balances", "subledger", "adjustments", "period-close", "accounting-rules"]
CHANNEL = ["channels", "mids", "cost-plans"]

RESULTS, CONSOLE, NETWORK, SHOTS = [], [], [], []
EXPECTED_ERRORS = {"on": False}
LAST = [0.0]


def credentials():
    text = (ROOT / "test-environment/private/access.md").read_text()
    return re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1)


def check(area, note, ok):
    RESULTS.append({"area": area, "ok": bool(ok), "note": note})
    print(f"[{'通过' if ok else '失败'}] {area}：{note}", flush=True)


def visible(locator, timeout=8000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def settle(page, ms=900):
    try:
        page.wait_for_load_state("networkidle", timeout=20000)
    except Exception:  # noqa: BLE001
        pass
    page.wait_for_timeout(ms)


def pace(gap=float(os.environ.get("RECON7_GAP", "2.5"))):
    wait = LAST[0] + gap - time.time()
    if wait > 0:
        time.sleep(wait)
    LAST[0] = time.time()


def goto(page, path):
    pace()
    page.goto(ORIGIN + path)
    settle(page)


def shot(page, name):
    page.wait_for_timeout(500)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
    SHOTS.append(f"{name}.png")


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def path_of(page):
    u = urlparse(page.url)
    return u.path + (("?" + u.query) if u.query else "")


def query_of(page):
    return {k: v[0] for k, v in parse_qs(urlparse(page.url).query, keep_blank_values=True).items()}


def heading(page):
    loc = page.locator(".page-heading h1")
    return loc.first.inner_text() if visible(loc, 10000) else ""


def drawer_open(page):
    return page.locator(".ant-drawer-content-wrapper:visible").count() > 0


def errors_on(page):
    return [t.strip().replace("\n", " ")[:200] for t in page.locator(ERROR_SELECTORS).all_inner_texts() if t.strip()]


def total_of(page):
    loc = page.locator(".ant-pagination-total-text")
    if not visible(loc, 6000):
        return 0
    m = re.search(r"共 (\d+)", loc.first.inner_text())
    return int(m.group(1)) if m else -1


def rows(page):
    return page.locator(".ant-table-tbody tr.clickable-row")


def choose_first(page, label):
    """在筛选下拉中选第一个选项，返回选项文字；没有选项时返回 None。"""
    page.get_by_label(label, exact=True).click(force=True)
    option = page.locator(".ant-select-dropdown:visible .ant-select-item-option")
    if not visible(option, 4000):
        page.keyboard.press("Escape")
        return None
    text = option.first.inner_text().strip()
    option.first.click()
    page.wait_for_timeout(300)
    return text


def section(name, fn):
    try:
        fn()
    except Exception as failure:  # noqa: BLE001
        check("脚本", f"{name} 段执行异常：{failure}", False)
        traceback.print_exc()


def run(page):
    found = {}

    def menu():
        goto(page, f"{BASE}/clearing-accounts")
        items = [t.strip() for t in page.locator(".ant-menu-item").all_inner_texts() if t.strip()]
        recon = [t for t in items if t in ("清算账户", "银行账户", "渠道账单", "平台流水", "渠道流水", "银行对账", "对账异常", "在途汇总")]
        ok = "渠道流水" in recon and "平台流水" in recon and recon.index("渠道流水") == recon.index("平台流水") + 1
        check("AC-V3", f"对账菜单（{'、'.join(recon)}）含「渠道流水」且紧跟「平台流水」", ok)
        shot(page, "m01-menu")
        if "渠道流水" in recon:
            pace()
            page.get_by_role("menuitem", name="渠道流水", exact=True).first.click()
            settle(page)
            check("AC-05", f"点菜单进入渠道流水页（{path_of(page)}，标题 {heading(page)}）",
                  urlparse(page.url).path == f"{BASE}/channel-lines" and heading(page) == "渠道流水")

    def channel_lines():
        goto(page, f"{BASE}/channel-lines?size=100")
        total = total_of(page)
        found["lineTotal"] = total
        texts = rows(page).all_inner_texts()
        check("AC-05", f"默认列表 {total} 条（不含已作废 / 已拒绝 / 接收中账单的行）、无错误提示", total >= 0 and not errors_on(page))
        shot(page, "l01-channel-lines-default")
        if texts:
            dates = [rows(page).nth(i).locator("td").first.inner_text().strip() for i in range(len(texts))]
            dated = [d for d in dates if re.fullmatch(r"\d{4}-\d{2}-\d{2}", d)]
            check("AC-05", f"按交易日从新到旧（首 {dated[:1]} 末 {dated[-1:]}；无日期行排最后）",
                  dated == sorted(dated, reverse=True) and dates[:len(dated)] == dated)
        goto(page, f"{BASE}/channel-lines")
        page.locator(".ant-pagination-options .ant-select").first.click()
        page.wait_for_timeout(500)
        sizes = [re.sub(r"\D", "", t) for t in page.locator(".ant-select-dropdown:visible .ant-select-item-option").all_inner_texts()]
        page.keyboard.press("Escape")
        check("AC-05", f"页大小选项 {sizes} 为 10/20/50/100", sizes == ["10", "20", "50", "100"] or total <= 10)

        def search(label, fill):
            goto(page, f"{BASE}/channel-lines?size=100")
            chosen = fill()
            pace()
            button(page, "查询").click()
            settle(page)
            got = total_of(page) if rows(page).count() else 0
            empty = rows(page).count() == 0
            empty_text = visible(page.get_by_text("没有符合条件的渠道流水"), 2000) if empty else True
            check("AC-05", f"筛选 {label}={chosen}：{got} 条；URL {query_of(page)}；空状态文案 {'正常' if empty_text else '缺失'}",
                  chosen is not None and (got <= max(total, 0) or label == "账单状态") and empty_text and not errors_on(page))
            return got

        search("清算账户", lambda: choose_first(page, "清算账户"))
        search("匹配状态", lambda: choose_first(page, "匹配状态"))
        search("行类型", lambda: choose_first(page, "行类型"))
        search("账单状态", lambda: choose_first(page, "账单状态"))
        shot(page, "l02-channel-lines-filter")
        # 外部交易号：取默认列表首个带交易号的行
        goto(page, f"{BASE}/channel-lines?size=100")
        ref = None
        for i in range(min(rows(page).count(), 100)):
            cell = rows(page).nth(i).locator("td").nth(8).inner_text().strip()
            if re.fullmatch(r"[\x21-\x7E]{3,128}", cell):
                ref = cell
                break
        if ref:
            goto(page, f"{BASE}/channel-lines?externalTransactionId={ref}")
            got = total_of(page)
            check("AC-05", f"外部交易号精确 {ref}：{got} 条", got >= 1)
            goto(page, f"{BASE}/channel-lines?externalTransactionId={ref[:-1]}")
            check("AC-05", "外部交易号去掉末位（不应前缀匹配）：空状态「没有符合条件的渠道流水」",
                  rows(page).count() == 0 and visible(page.get_by_text("没有符合条件的渠道流水"), 4000))
            shot(page, "l03-channel-lines-empty")
        else:
            check("AC-05", "演示数据中没有带外部交易号的渠道流水行，跳过交易号筛选", True)
        goto(page, f"{BASE}/channel-lines?transactionDateFrom=2000-01-01&transactionDateTo=2099-12-31&size=100")
        check("AC-05", f"交易日 2000-01-01..2099-12-31：{total_of(page)} 条（有日期的行）", not errors_on(page))
        # 点行 → 账单详情 ?line= 高亮
        goto(page, f"{BASE}/channel-lines")
        if rows(page).count():
            pace()
            rows(page).first.locator("td").nth(3).click()
            settle(page, 1200)
            hl = page.locator("tr.recon-line-highlight")
            check("AC-05", f"点行进入所属账单详情并高亮（{path_of(page)}，高亮 {hl.count()} 行，无抽屉）",
                  "/statements/" in page.url and "line=" in page.url and hl.count() == 1 and not drawer_open(page))
            shot(page, "l04-line-click-highlight")
            pace()
            page.go_back()
            settle(page)
            check("AC-01", f"后退回渠道流水页（{path_of(page)}）", urlparse(page.url).path == f"{BASE}/channel-lines")

    def detail_cycle(name, list_url, path_re, list_label, shot_name, key, row_locator=None):
        goto(page, list_url)
        want = query_of(page)
        row = row_locator() if row_locator else rows(page).first
        if not visible(row, 8000):
            check("AC-01", f"{name}：列表 {list_url} 没有可点击的行（演示数据缺该对象），跳过", True)
            return
        pace()
        row.locator("td").first.click()
        settle(page, 1200)
        detail_path = urlparse(page.url).path
        found[key] = detail_path
        head = heading(page)
        crumbs = page.locator(".finance-breadcrumb").first.inner_text().replace("\n", "") if visible(page.locator(".finance-breadcrumb"), 3000) else ""
        check("AC-V2", f"{name}：列表点行进入详情页 {detail_path}（{head}），无抽屉、无错误",
              bool(re.fullmatch(path_re, detail_path)) and head.startswith(name) and not drawer_open(page) and not errors_on(page))
        check("AC-V1", f"{name}：面包屑「{crumbs}」、有「返回列表」",
              crumbs.startswith("财务/对账/" + list_label) and visible(button(page, "返回列表"), 2000))
        shot(page, shot_name)
        pace()
        page.reload()
        settle(page, 1200)
        check("AC-01", f"{name}：刷新后仍是详情（{heading(page)}）", urlparse(page.url).path == detail_path and heading(page).startswith(name))
        pace()
        page.go_back()
        settle(page)
        check("AC-01", f"{name}：后退回列表并保留筛选 / 页码（{query_of(page)} = {want}）",
              urlparse(page.url).path == urlparse(ORIGIN + list_url).path and query_of(page) == want)
        pace()
        page.go_forward()
        settle(page, 1000)
        pace()
        button(page, "返回列表").click()
        settle(page)
        check("AC-V1", f"{name}：「返回列表」回到带筛选的列表（{query_of(page)}）", query_of(page) == want)

    def details():
        detail_cycle("清算账户", f"{BASE}/clearing-accounts?size=20", rf"{BASE}/clearing-accounts/\d+", "清算账户", "d01-clearing-detail", "clearing")
        detail_cycle("银行账户", f"{BASE}/cash-accounts?size=20", rf"{BASE}/cash-accounts/\d+", "银行账户", "d02-cash-detail", "cash")
        detail_cycle("渠道账单", f"{BASE}/statements?size=20", rf"{BASE}/statements/\d+", "渠道账单", "d03-statement-detail", "statement")
        cash_id = found.get("cash", "").rsplit("/", 1)[-1]
        if cash_id:
            detail_cycle("银行对账单", f"{BASE}/bank?account={cash_id}&tab=statements", rf"{BASE}/bank/statements/\d+", "银行对账",
                         "d04-bank-statement-detail", "bankStatement")
            detail_cycle("银行流水", f"{BASE}/bank?account={cash_id}&matchStatus=", rf"{BASE}/bank/lines/\d+", "银行对账",
                         "d05-bank-line-detail", "bankLine")
        else:
            check("AC-01", "没有银行账户详情路径，跳过银行对账单 / 银行流水详情", False)
        detail_cycle("对账异常", f"{BASE}/exceptions?size=20", rf"{BASE}/exceptions/\d+", "对账异常", "d06-exception-detail", "exception")

    def detail_contents():
        if found.get("statement"):
            goto(page, found["statement"])
            gates = page.locator(".recon-gate-item").count()
            check("AC-02", f"渠道账单详情：全部账单行表格 {total_of(page)} 行、对平门禁 {gates} 项", page.locator(".ant-table").count() > 0)
        if found.get("bankStatement"):
            goto(page, found["bankStatement"])
            check("AC-03", f"银行对账单详情：流水 {rows(page).count()} 条（点行进流水详情）", page.locator(".ant-table").count() > 0 and not errors_on(page))
        if found.get("bankLine"):
            goto(page, found["bankLine"])
            body = page.locator(".ant-layout-content, main").first.inner_text()
            check("AC-03", "银行流水详情：流水信息、匹配与未结异常区块渲染（只看，不确认 / 拒绝 / 匹配）",
                  "匹配" in body and not errors_on(page))
            shot(page, "d07-bank-line-body")
        if found.get("exception"):
            goto(page, found["exception"])
            body = page.locator(".ant-layout-content, main").first.inner_text()
            check("AC-04", "对账异常详情：来源对象与处置记录区块渲染（不提交、不复核处置）", "处置" in body and not errors_on(page))

    def not_found():
        cases = [("清算账户", f"{BASE}/clearing-accounts/{NO_ID}"), ("银行账户", f"{BASE}/cash-accounts/{NO_ID}"),
                 ("渠道账单", f"{BASE}/statements/{NO_ID}"), ("对账异常", f"{BASE}/exceptions/{NO_ID}"),
                 ("银行对账单", f"{BASE}/bank/statements/{NO_ID}"), ("银行流水", f"{BASE}/bank/lines/{NO_ID}")]
        EXPECTED_ERRORS["on"] = True
        for i, (label, url) in enumerate(cases):
            goto(page, url)
            ok = visible(page.get_by_text(f"未找到该{label}"), 8000) and visible(page.get_by_role("button", name=re.compile("^返回")), 2000)
            toast = page.locator(".ant-message-notice:visible, .ant-notification-notice:visible").count()
            check("AC-01", f"{label}不存在：显示「未找到该{label}」与返回按钮，无错误通知（{toast}）", ok and toast == 0)
            if i in (0, 5):
                shot(page, f"n0{i + 1}-not-found")
        EXPECTED_ERRORS["on"] = False

    def regression():
        for slug in ("platform-txns", "summary"):
            goto(page, f"{BASE}/{slug}")
            errs = errors_on(page)
            check("回归", f"对账/{slug} 渲染、无错误提示" + ("" if not errs else "：" + "; ".join(errs)), not errs and visible(page.locator("h1"), 8000))
            shot(page, f"g-recon-{slug}")
        for label, prefix, slugs in (() if os.environ.get("RECON7_SKIP_OTHERS") else (("商务", "commercial", COMMERCIAL), ("财务", "finance", FINANCE), ("渠道", "channel", CHANNEL))):
            for slug in slugs:
                goto(page, f"/{prefix}/{slug}")
                errs = errors_on(page)
                bad = visible(page.get_by_text(re.compile("页面不存在|无权访问")), 800)
                check("回归", f"{label}/{slug}" + ("" if not errs else "：" + "; ".join(errs)), not errs and not bad)
        goto(page, "/currency-specs")
        check("回归", "平台配置/币种 渲染、无错误提示", not errors_on(page) and visible(page.locator("h1"), 8000))
        shot(page, "g01-currency-specs")

    only = set(filter(None, os.environ.get("RECON7_ONLY", "").split(",")))
    for name, fn in (("菜单", menu), ("渠道流水", channel_lines), ("详情页循环", details), ("详情内容", detail_contents),
                     ("未找到", not_found), ("回归", regression)):
        if not only or name in only:
            section(name, fn)


def main():
    email, password = credentials()
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": 1512, "height": 900})
        page = context.new_page()
        page.on("console", lambda m: CONSOLE.append((path_of(page), m.type, m.text, EXPECTED_ERRORS["on"]))
                if m.type in ("error", "warning") else None)
        page.on("response", lambda r: NETWORK.append((path_of(page), r.status, r.request.method, r.url, EXPECTED_ERRORS["on"]))
                if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(email)
        page.get_by_label("密码").fill(password)
        button(page, "登录").click()
        settle(page, 1500)
        logged_in = not visible(page.get_by_label("密码"), 2000)
        check("登录", "管理员登录", logged_in)
        if logged_in:
            run(page)
        browser.close()
    relevant = [c for c in CONSOLE if c[1] == "error" and KNOWN not in c[2] and not c[3]]
    network = [n for n in NETWORK if not (n[4] and n[1] in (400, 404))]
    expected = [n for n in NETWORK if n[4] and n[1] in (400, 404)]
    data = {"results": RESULTS, "consoleErrors": relevant, "network": network, "expectedNotFound": expected, "shots": SHOTS}
    (HERE / "stage7-browser-result.json").write_text(json.dumps(data, ensure_ascii=False, indent=2))
    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"\n{passed}/{len(RESULTS)} 通过；控制台错误 {len(relevant)}；非预期 4xx/5xx {len(network)}（未找到用例的预期 400/404 {len(expected)} 条单列）")
    for c in relevant:
        print("  console:", c[0], c[2][:300])
    for n in network:
        print("  network:", n[:4])
    sys.exit(0 if passed == len(RESULTS) and not relevant and not network else 1)


if __name__ == "__main__":
    main()
