"""对账控制台详情页与渠道流水 阶段 5：浏览器验收 AC-01–AC-05、AC-V1–V3（无头 Chromium，经私有栈运营入口 https://127.0.0.1:18454 真实登录）。

续用 seed.py / seed_more.py 的运行记录（MAKER / CHECKER、CA1 / CA2、银行账户、账单 A–F、银行对账单 3 行）。
截图写 verification/screenshots/；不打印口令。用法：python3 -I browser_detail.py <run-*.json>
"""
import json
import re
import site
import sys
import traceback
from datetime import date, timedelta
from pathlib import Path
from urllib.parse import parse_qs, urlparse

sys.path.append(site.getusersitepackages())
sys.path.insert(0, str(Path(__file__).resolve().parent))
from playwright.sync_api import sync_playwright  # noqa: E402

import recon5_common as c  # noqa: E402

OUT = Path(__file__).resolve().parent / "screenshots"
OUT.mkdir(exist_ok=True)
BASE = "/finance/reconciliation"
NO_ID = "999999999999"


def visible(locator, timeout=10000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def shot(page, name):
    page.wait_for_timeout(800)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def settle(page, ms=700):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(ms)


def login(page, email, password):
    page.goto(c.ORIGIN + "/")
    page.get_by_label("Email / 初始化账号").fill(email)
    page.get_by_label("密码").fill(password)
    button(page, "登录").click()
    settle(page, 1500)


LAST = [0.0]
THROTTLED = []


def pace(gap=2.5):
    """Gateway 对同一路由按 IP / 主体限 30 次/分钟（块 123 / 126 同档）：每次导航或查询前至少间隔 gap 秒，避免验收脚本自身触发 429。"""
    import time
    wait = LAST[0] + gap - time.time()
    if wait > 0:
        time.sleep(wait)
    LAST[0] = time.time()


def goto(page, path):
    pace()
    page.goto(c.ORIGIN + path)
    settle(page)


def path_of(page):
    u = urlparse(page.url)
    return u.path + (("?" + u.query) if u.query else "")


def query_of(page):
    return {k: v[0] for k, v in parse_qs(urlparse(page.url).query, keep_blank_values=True).items()}


def heading(page):
    return page.locator(".page-heading h1").first.inner_text(timeout=10000)


def drawer_open(page):
    return page.locator(".ant-drawer-content-wrapper:visible, .ant-drawer-open").count() > 0


def total_text(page):
    loc = page.locator(".ant-pagination-total-text")
    return loc.first.inner_text() if visible(loc, 8000) else ""


def total_of(page):
    m = re.search(r"共 (\d+)", total_text(page))
    return int(m.group(1)) if m else -1


def choose(page, label, option_text, scope=None):
    scope = scope or page
    scope.get_by_label(label, exact=True).click(force=True)
    page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text=option_text).first.click()
    page.wait_for_timeout(300)


def popconfirm(page, ok_text):
    pop = page.locator(".ant-popover:visible, .ant-popconfirm:visible").last
    pop.get_by_role("button", name=re.compile("^" + r"\s?".join(ok_text) + "$")).click()
    settle(page, 1200)


ONLY = [x for x in __import__("os").environ.get("SECTIONS", "").split(",") if x]


def section(name, fn):
    if ONLY and name not in ONLY:
        return
    try:
        fn()
    except Exception as failure:  # noqa: BLE001
        c.check("脚本", f"{name} 段执行异常：{failure}", False)
        traceback.print_exc()


def main():
    record_path = Path(sys.argv[1])
    record = json.loads(record_path.read_text())
    people, st, refs, run = record["people"], record["statements"], record["refs"], record["run"]
    ca1, ca2 = record["ca1"], record["ca2"]
    cash_id = record["cashAccountId"]
    bank_lines, bsid = record["bankLines"], record["bankStatementId"]
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch()

        def new_page():
            context = browser.new_context(viewport={"width": 1512, "height": 900}, ignore_https_errors=True)
            page = context.new_page()
            page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
            page.on("response", lambda r: THROTTLED.append(r.url) if r.status == 429 else None)
            return page

        mk = new_page()
        login(mk, people["MAKER"]["email"], people["MAKER"]["password"])

        # ---------- 菜单：渠道流水在平台流水之后（AC-V3 / Q-01） ----------
        def menu():
            group = mk.get_by_role("menuitem", name="对账", exact=True)
            if not visible(group, 3000):
                mk.get_by_role("menuitem", name="财务", exact=True).first.click()
                mk.wait_for_timeout(500)
            if not visible(mk.get_by_role("menuitem", name="清算账户", exact=True), 2000):
                group.first.click()
                mk.wait_for_timeout(500)
            items = [t.strip() for t in mk.locator(".ant-menu-sub .ant-menu-item").all_inner_texts() if t.strip()]
            recon = [t for t in items if t in ("清算账户", "银行账户", "渠道账单", "平台流水", "渠道流水", "银行对账", "对账异常", "在途汇总")]
            ok = "渠道流水" in recon and "平台流水" in recon and recon.index("渠道流水") == recon.index("平台流水") + 1
            c.check("AC-V3", f"对账菜单含「渠道流水」且紧跟「平台流水」（{'、'.join(recon)}）", ok)
            shot(mk, "01-menu-channel-lines")
            mk.get_by_role("menuitem", name="渠道流水", exact=True).first.click()
            settle(mk)
            c.check("AC-05", f"点菜单进入渠道流水页（{path_of(mk)}，标题 {heading(mk)}）",
                    urlparse(mk.url).path == f"{BASE}/channel-lines" and heading(mk) == "渠道流水")
        section("菜单", menu)

        # ---------- 渠道流水页（AC-05） ----------
        def channel_lines():
            goto(mk, f"{BASE}/channel-lines?size=100")
            n = total_of(mk)
            texts = mk.locator(".ant-table-tbody tr.clickable-row").all_inner_texts()
            c.check("AC-05", f"默认 {n} 条（A3+B1+C3+E2+F25=34），不含作废账单 D 的行，说明「按交易日期从新到旧」",
                    n == 34 and not any(re.search(rf"(?<![A-Z])D-{run}", t) for t in texts) and "按交易日期从新到旧" in total_text(mk), total_text(mk))
            dates = [mk.locator(".ant-table-tbody tr.clickable-row").nth(i).locator("td").first.inner_text().strip() for i in range(len(texts))]
            c.check("AC-05", f"按交易日倒序（首 {dates[:1]} 末 {dates[-1:]}）", dates == sorted(dates, reverse=True), dates)
            shot(mk, "02-channel-lines-default")
            goto(mk, f"{BASE}/channel-lines")
            mk.locator(".ant-pagination-options .ant-select-selector, .ant-pagination-options .ant-select").first.click()
            mk.wait_for_timeout(600)
            sizes = [t.strip() for t in mk.locator(".ant-select-dropdown:visible .ant-select-item-option").all_inner_texts()]
            mk.keyboard.press("Escape")
            c.check("AC-05", f"页大小选项（{sizes}）为 10/20/50/100、无 200", [re.sub(r"\D", "", s) for s in sizes] == ["10", "20", "50", "100"], sizes)

            def search(fill, expect, label, extra=None):
                goto(mk, f"{BASE}/channel-lines?size=100")
                fill()
                pace()
                button(mk, "查询").click()
                settle(mk)
                got = total_of(mk) if mk.locator(".ant-table-tbody tr.clickable-row").count() else 0
                ok = got == expect and (extra is None or extra())
                c.check("AC-05", f"筛选 {label}：{got} 条（期望 {expect}）；URL {query_of(mk)}", ok)
                return ok
            search(lambda: choose(mk, "清算账户", ca1["clearingAccountCode"]), 8, "清算账户 CA1")
            search(lambda: choose(mk, "匹配状态", "未匹配"), 26, "匹配状态=未匹配")
            search(lambda: choose(mk, "行类型", "调整"), 3, "行类型=调整")
            search(lambda: choose(mk, "账单状态", "已作废"), 2, "账单状态=已作废（显式查看 D）",
                   lambda: all(f"D-{run}" in t for t in mk.locator(".ant-table-tbody tr.clickable-row").all_inner_texts()))
            search(lambda: mk.get_by_label("外部交易号", exact=True).fill(refs["PAY-C"]), 1, "外部交易号精确 PAY-C")
            ok = search(lambda: mk.get_by_label("外部交易号", exact=True).fill(refs["PAY-C"][:-3]), 0, "外部交易号前缀（不命中）",
                        lambda: visible(mk.get_by_text("没有符合条件的渠道流水"), 3000))
            shot(mk, "03-channel-lines-empty")
            goto(mk, f"{BASE}/channel-lines?externalTransactionId={refs['PAY-C'][:-3]}")
            c.check("AC-V3", "对照：带筛选的地址直接打开时空状态为「没有符合条件的渠道流水…」",
                    visible(mk.get_by_text("没有符合条件的渠道流水"), 5000))
            shot(mk, "03b-channel-lines-empty-direct")
            d2, d1 = (date.today() - timedelta(days=2)).isoformat(), (date.today() - timedelta(days=1)).isoformat()
            search(lambda: (mk.get_by_label("交易日起", exact=True).fill(d2), mk.get_by_label("交易日止", exact=True).fill(d1)), 5,
                   f"交易日 {d2}..{d1}")
            shot(mk, "04-channel-lines-date-filter")
            # 外部交易号行点击 → 账单详情并高亮
            goto(mk, f"{BASE}/channel-lines?externalTransactionId={refs['PAY-C']}")
            mk.locator(".ant-table-tbody tr.clickable-row").first.locator("td").nth(4).click()
            settle(mk, 1200)
            hl = mk.locator("tr.recon-line-highlight")
            c.check("AC-05", f"点行进入账单 C 详情 ?line=1 并高亮（{path_of(mk)}；高亮 {hl.count()} 行）",
                    path_of(mk) == f"{BASE}/statements/{st['C']}?line=1" and hl.count() == 1 and hl.first.locator("td").first.inner_text().strip() == "1"
                    and not drawer_open(mk))
            shot(mk, "05-line-click-highlight")
            # 分页 + 后退恢复页码
            goto(mk, f"{BASE}/channel-lines")
            mk.locator(".ant-pagination-item-2").click()
            settle(mk)
            q = query_of(mk)
            first = mk.locator(".ant-table-tbody tr.clickable-row").first
            row_text = first.inner_text()
            rows = mk.locator(".ant-table-tbody tr.clickable-row").count()
            first.locator("td").nth(4).click()
            settle(mk, 1200)
            on_detail = "/statements/" in mk.url and "line=" in mk.url
            hl_ok = mk.locator("tr.recon-line-highlight").count() == 1
            mk.go_back()
            settle(mk)
            c.check("AC-01", f"渠道流水第 2 页（{rows} 行，URL {q}）→ 点行进详情（高亮 {hl_ok}）→ 浏览器后退回到第 2 页（URL {query_of(mk)}）",
                    q.get("page") == "2" and rows == 10 and on_detail and hl_ok and query_of(mk).get("page") == "2"
                    and mk.locator(".ant-pagination-item-active").inner_text() == "2"
                    and mk.locator(".ant-table-tbody tr.clickable-row").first.inner_text() == row_text)
        section("渠道流水", channel_lines)

        # ---------- 6 类详情：列表点击 / 直接打开 / 刷新 / 后退（AC-01、AC-V1、AC-V2） ----------
        direct = []

        def detail_cycle(name, list_url, row_text, path_re, head_re, list_label, shot_name, row_locator=None):
            goto(mk, list_url)
            want = query_of(mk)
            row = row_locator(mk) if row_locator else mk.locator(".ant-table-tbody tr.clickable-row").filter(has_text=row_text).first
            if not visible(row, 8000):
                c.check("AC-01", f"{name}：列表中找不到行 {row_text}", False)
                return None
            row.locator("td").first.click()
            settle(mk, 1200)
            detail_path = urlparse(mk.url).path
            head = heading(mk)
            crumbs = mk.locator(".finance-breadcrumb").inner_text().replace("\n", "")
            ok_open = bool(re.fullmatch(path_re, detail_path)) and bool(re.search(head_re, head)) and not drawer_open(mk)
            c.check("AC-V2", f"{name}：列表点行进入详情页 {detail_path}（{head}），无抽屉", ok_open)
            c.check("AC-V1", f"{name}：面包屑「{crumbs}」、有「返回列表」", crumbs.startswith("财务/对账/" + list_label) and
                    visible(button(mk, "返回列表"), 2000))
            shot(mk, shot_name)
            pace()
            mk.reload()
            settle(mk, 1200)
            c.check("AC-01", f"{name}：刷新后仍是详情（{heading(mk)}）", urlparse(mk.url).path == detail_path and re.search(head_re, heading(mk)))
            pace()
            mk.go_back()
            settle(mk)
            c.check("AC-01", f"{name}：浏览器后退回列表并保留筛选 / 页码（{query_of(mk)} = {want}）",
                    urlparse(mk.url).path == urlparse(c.ORIGIN + list_url).path and query_of(mk) == want)
            # 「返回列表」按钮同样回到带筛选的列表
            mk.go_forward()
            settle(mk, 1000)
            button(mk, "返回列表").click()
            settle(mk)
            c.check("AC-V1", f"{name}：「返回列表」回到带筛选的列表（{query_of(mk)}）", query_of(mk) == want)
            # 直接打开
            if not direct:
                direct.append(new_page())
                login(direct[0], people["MAKER"]["email"], people["MAKER"]["password"])
            page = direct[0]
            pace()
            page.goto(c.ORIGIN + detail_path)
            settle(page, 1200)
            c.check("AC-01", f"{name}：另一已登录会话直接输入地址打开（{heading(page)}）", bool(re.search(head_re, heading(page))))
            return detail_path

        def details():
            detail_cycle("清算账户", f"{BASE}/clearing-accounts?currency=USD&size=20", ca1["clearingAccountCode"],
                         rf"{BASE}/clearing-accounts/{ca1['clearingAccountId']}", "清算账户 · PMX", "清算账户", "10-clearing-detail")
            detail_cycle("银行账户", f"{BASE}/cash-accounts?entityId=AE001&size=20", f"AE001 settlement {run}",
                         rf"{BASE}/cash-accounts/{cash_id}", "银行账户 · AE001", "银行账户", "11-cash-detail")
            detail_cycle("渠道账单", f"{BASE}/statements?clearingAccountId={ca1['clearingAccountId']}&size=20", f"C-{run}",
                         rf"{BASE}/statements/{st['C']}", f"渠道账单 · C-{run}", "渠道账单", "12-statement-detail")
            detail_cycle("银行对账单", f"{BASE}/bank?account={cash_id}&tab=statements", f"ENBD-{run}",
                         rf"{BASE}/bank/statements/{bsid}", f"银行对账单 · ENBD-{run}", "银行对账", "13-bank-statement-detail")
            detail_cycle("银行流水", f"{BASE}/bank?account={cash_id}&matchStatus=", f"ENBD-3-{run}",
                         rf"{BASE}/bank/lines/{bank_lines['3']}", f"银行流水 · ENBD-3-{run}", "银行对账", "14-bank-line-detail")
            detail_cycle("对账异常", f"{BASE}/exceptions?statementId={st['C']}", "调整行待归类",
                         rf"{BASE}/exceptions/\d+", "对账异常 · 调整行待归类", "对账异常", "15-exception-detail")
        section("详情页循环", details)

        # ---------- 未找到（AC-01） ----------
        def not_found():
            cases = [("清算账户（既有 400）", f"{BASE}/clearing-accounts/{NO_ID}", "未找到该清算账户"),
                     ("银行账户（既有 400）", f"{BASE}/cash-accounts/{NO_ID}", "未找到该银行账户"),
                     ("渠道账单（既有 400）", f"{BASE}/statements/{NO_ID}", "未找到该渠道账单"),
                     ("对账异常（既有 400）", f"{BASE}/exceptions/{NO_ID}", "未找到该对账异常"),
                     ("银行对账单（新 404）", f"{BASE}/bank/statements/{NO_ID}", "未找到该银行对账单"),
                     ("银行流水（新 404）", f"{BASE}/bank/lines/{NO_ID}", "未找到该银行流水"),
                     ("非法主键 abc", f"{BASE}/statements/abc", "未找到该渠道账单")]
            for i, (label, url, text) in enumerate(cases):
                before = len(errors)
                goto(mk, url)
                ok = visible(mk.get_by_text(text), 8000) and visible(mk.get_by_role("button", name=re.compile("^返回")), 2000)
                toast = mk.locator(".ant-message-notice:visible, .ant-notification-notice:visible").count()
                c.check("AC-01", f"{label}：显示「{text}」与返回按钮，无错误通知（{toast}）", ok and toast == 0)
                if i in (0, 4):
                    shot(mk, f"16-not-found-{'old-400' if i == 0 else 'new-404'}")
                del errors[before:]  # 404 / 400 的资源加载错误是预期的
            before = len(errors)
            goto(mk, f"{BASE}/bank/lines/{NO_ID}")
            mk.get_by_role("button", name=re.compile("^返回银行对账")).click()
            settle(mk)
            del errors[before:]
            c.check("AC-01", f"未找到页「返回银行对账」回到列表（{path_of(mk)}）", urlparse(mk.url).path == f"{BASE}/bank")
        section("未找到", not_found)

        # ---------- 渠道账单详情：全部行分页 + 筛选 + ?line= + 门禁（AC-02） ----------
        def statement_detail():
            goto(mk, f"{BASE}/statements/{st['F']}")
            n = total_of(mk)
            c.check("AC-02", f"账单 F 详情列出全部行：{n} 行，第 1 页 {mk.locator('.ant-table-tbody tr.ant-table-row').count()} 行",
                    n == 25 and mk.locator(".ant-table-tbody tr.ant-table-row").count() == 20 and "共 25 行" in total_text(mk))
            mk.locator(".ant-pagination-item-2").click()
            settle(mk)
            c.check("AC-02", f"翻到第 2 页 {mk.locator('.ant-table-tbody tr.ant-table-row').count()} 行", mk.locator(".ant-table-tbody tr.ant-table-row").count() == 5)
            goto(mk, f"{BASE}/statements/{st['F']}?line=23")
            hl = mk.locator("tr.recon-line-highlight")
            c.check("AC-02", f"?line=23 自动翻到第 2 页并高亮第 23 行（当前页 {mk.locator('.ant-pagination-item-active').inner_text()}）",
                    hl.count() == 1 and hl.first.locator("td").first.inner_text().strip() == "23"
                    and mk.locator(".ant-pagination-item-active").inner_text() == "2")
            shot(mk, "20-statement-line-highlight-page2")
            goto(mk, f"{BASE}/statements/{st['C']}")
            gate = mk.locator(".recon-gate-item.is-fail").all_inner_texts()
            c.check("AC-02", f"匹配中账单 C 显示对平门禁未满足项（{[g.replace(chr(10), '') for g in gate]}）",
                    any("交易行" in g for g in gate) and any("调整行" in g for g in gate) and any("阻塞" in g for g in gate))
            sel = mk.locator(".recon-filters .ant-select")
            sel.nth(0).click()
            mk.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="未匹配").first.click()
            settle(mk)
            rows = mk.locator(".ant-table-tbody tr.ant-table-row").all_inner_texts()
            c.check("AC-02", f"匹配状态=未匹配：{len(rows)} 行（C 行 2）", len(rows) == 1 and rows[0].strip().startswith("2"))
            sel.nth(0).locator(".ant-select-clear").click(force=True) if sel.nth(0).locator(".ant-select-clear").count() else None
            settle(mk)
            sel.nth(1).click()
            mk.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="调整").first.click()
            settle(mk)
            rows = mk.locator(".ant-table-tbody tr.ant-table-row").all_inner_texts()
            c.check("AC-02", f"行类型=调整：{len(rows)} 行（C 行 3）", len(rows) == 1 and rows[0].strip().startswith("3"), rows)
            shot(mk, "21-statement-gate-filter")
        section("账单详情", statement_detail)

        # ---------- 跨链接：平台流水 ↔ 账单行（AC-V2 / 需求 §3.1） ----------
        def cross_links():
            goto(mk, f"{BASE}/statements/{st['A']}")
            row = mk.locator(".ant-table-tbody tr.ant-table-row").filter(has_text=refs["PAY-A"]).first
            row.get_by_role("link").first.click()
            settle(mk)
            q = query_of(mk)
            n = total_of(mk)
            c.check("链接", f"账单行 → 平台流水页（{urlparse(mk.url).path}，{q}，{n} 条）",
                    urlparse(mk.url).path == f"{BASE}/platform-txns" and q.get("externalTransactionId") == refs["PAY-A"] and n == 1)
            shot(mk, "30-line-to-platform-txn")
            mk.get_by_role("link", name="渠道流水").first.click()
            settle(mk)
            q = query_of(mk)
            n = total_of(mk)
            c.check("链接", f"平台流水 → 渠道流水页（{urlparse(mk.url).path}，{q}，{n} 条，账单 A）",
                    urlparse(mk.url).path == f"{BASE}/channel-lines" and q.get("externalTransactionId") == refs["PAY-A"] and n == 1
                    and f"A-{run}" in mk.locator(".ant-table-tbody").inner_text())
            shot(mk, "31-platform-txn-to-channel-lines")
            # 银行匹配 → 账单
            goto(mk, f"{BASE}/bank/lines/{bank_lines['1']}")
            link = mk.get_by_role("link", name=f"账单 {st['A']}")
            ok = visible(link, 5000)
            if ok:
                link.first.click()
                settle(mk)
            c.check("链接", f"银行流水已确认匹配 → 渠道账单 A 详情（{path_of(mk)}）", ok and urlparse(mk.url).path == f"{BASE}/statements/{st['A']}")
            shot(mk, "32-bank-match-to-statement")
            goto(mk, f"{BASE}/bank/statements/{bsid}")
            rows = mk.locator(".ant-table-tbody tr.clickable-row, .ant-table-tbody tr.ant-table-row")
            c.check("AC-03", f"银行对账单详情：期初 / 期末与全部 {rows.count()} 条流水", rows.count() == 3 and "1,000.00" in mk.content() or "1000.00" in mk.content())
            rows.filter(has_text=f"ENBD-2-{run}").first.locator("td").first.click()
            settle(mk)
            c.check("AC-03", f"对账单详情点流水进入流水详情（{path_of(mk)}）", urlparse(mk.url).path == f"{BASE}/bank/lines/{bank_lines['2']}")
        section("跨链接", cross_links)

        # ---------- 银行流水详情：确认建议、人工匹配区、开异常（AC-03） ----------
        def bank_line():
            goto(mk, f"{BASE}/bank/lines/{bank_lines['2']}")
            shot(mk, "40-bank-line-proposed")
            mk.get_by_role("button", name=re.compile(r"^确\s?认$")).first.click()
            popconfirm(mk, "确认")
            ok = c.wait_until(lambda: c.recon_sql(f"SELECT status FROM t_bank_match WHERE id={record['bankMatchProposed']}")[0][0] == "CONFIRMED", 15)
            mk.reload()
            settle(mk)
            c.check("AC-03", f"流水 2 确认建议 → 匹配已确认、流水已匹配（页面：{'已确认' in mk.locator('.finance-section').filter(has_text='匹配建议').inner_text()}）",
                    bool(ok) and "已确认" in mk.locator(".finance-section").filter(has_text="匹配建议").inner_text())
            shot(mk, "41-bank-line-confirmed")
            goto(mk, f"{BASE}/bank/lines/{bank_lines['3']}")
            c.check("AC-03", "未匹配流水 3 显示「人工多选匹配」区", visible(mk.get_by_text("人工多选匹配"), 5000))
            shot(mk, "42-bank-line-manual-match")
            button(mk, "开异常").click()
            popconfirm(mk, "开立")
            settle(mk, 1500)
            path = urlparse(mk.url).path
            c.check("AC-03", f"人工开异常后跳到异常详情（{path}，{heading(mk) if '/exceptions/' in path else ''}）",
                    re.fullmatch(rf"{BASE}/exceptions/\d+", path) is not None and "银行流水未匹配" in heading(mk))
            record["bankLineException"] = path.rsplit("/", 1)[-1]
            shot(mk, "43-bank-line-exception-opened")
            src = mk.get_by_role("link", name="查看银行流水")
            ok = visible(src, 5000)
            if ok:
                src.first.click()
                settle(mk)
            c.check("AC-04", f"异常来源「查看银行流水」跳回流水详情（{path_of(mk)}），流水详情列出该未结异常",
                    ok and urlparse(mk.url).path == f"{BASE}/bank/lines/{bank_lines['3']}" and visible(mk.get_by_text("未结异常（1）"), 5000))
        section("银行流水详情", bank_line)

        # ---------- 异常详情：来源跳转、提交处置（Maker）、复核（Checker）（AC-04） ----------
        def exception_detail():
            exc = c.recon_sql(f"SELECT id FROM t_recon_exception WHERE statement_id={st['C']} AND status='OPEN'")[0][0]
            goto(mk, f"{BASE}/exceptions/{exc}")
            src = mk.get_by_role("link", name=re.compile("^查看渠道账单行"))
            src.first.click()
            settle(mk, 1200)
            hl = mk.locator("tr.recon-line-highlight")
            c.check("AC-04", f"来源「查看渠道账单行」→ 账单 C 详情并高亮第 3 行（{path_of(mk)}）",
                    path_of(mk) == f"{BASE}/statements/{st['C']}?line=3" and hl.count() == 1)
            mk.go_back()
            settle(mk)
            button(mk, "提交处置").click()
            settle(mk, 500)
            drawer = mk.locator(".ant-drawer:visible")
            c.check("AC-04", "「提交处置」在抽屉中打开", visible(drawer, 3000))
            choose(mk, "处置方式", "归类", drawer)
            choose(mk, "归类码", "CHANNEL_FEE_OTHER", drawer)
            drawer.get_by_label("处置说明").fill("阶段 5 浏览器验收：保证金扣留归渠道其他费用")
            shot(mk, "50-resolution-drawer")
            button(drawer, "提交复核").click()
            settle(mk, 1500)
            res_text = mk.locator(".finance-section").filter(has_text="处置记录").inner_text()
            c.check("AC-04", f"提交后抽屉关闭、详情刷新出待复核处置（状态 {'待复核' in heading(mk) or '待复核' in res_text}）",
                    not visible(mk.locator(".ant-drawer:visible"), 1500) and "待复核" in res_text)
            shot(mk, "51-after-submit")
            ck = new_page()
            login(ck, people["CHECKER"]["email"], people["CHECKER"]["password"])
            goto(ck, f"{BASE}/exceptions/{exc}")
            button(ck, "复核处置").click()
            settle(ck, 500)
            drawer = ck.locator(".ant-drawer:visible")
            c.check("AC-04", "Checker：「复核处置」在抽屉中打开并显示提交内容", visible(drawer, 3000) and "CHANNEL_FEE_OTHER" in drawer.inner_text())
            shot(ck, "52-review-drawer")
            button(drawer, "批准并应用").click()
            popconfirm(ck, "确定") if visible(ck.locator(".ant-popover:visible").get_by_role("button", name=re.compile(r"^确\s?定$")), 2000) else popconfirm(ck, "确认")
            settle(ck, 1500)
            status = c.recon_sql(f"SELECT status FROM t_recon_exception WHERE id={exc}")[0][0]
            text = ck.locator(".recon-detail").inner_text()
            c.check("AC-04", f"批准后抽屉关闭、详情刷新为已解决（库 {status}）",
                    status == "RESOLVED" and not visible(ck.locator(".ant-drawer:visible"), 1500) and "已解决" in text and "已应用" in text)
            shot(ck, "53-after-review")
            ck.context.close()
        section("异常详情", exception_detail)

        # ---------- 作废匹配中账单（AC-02） ----------
        def void():
            goto(mk, f"{BASE}/statements/{st['E']}")
            button(mk, "作废").click()
            popconfirm(mk, "作废")
            status = c.wait_until(lambda: (lambda s: s if s == "VOIDED" else None)(
                c.recon_sql(f"SELECT status FROM t_channel_statement WHERE id={st['E']}")[0][0]), 15)
            text = mk.locator(".recon-detail").inner_text()
            c.check("AC-02", f"详情页作废匹配中账单 E → {status}，页面显示已作废、作废按钮消失",
                    status == "VOIDED" and "已作废" in text and not visible(button(mk, "作废"), 1500))
            shot(mk, "60-statement-voided")
            goto(mk, f"{BASE}/channel-lines?size=100")
            c.check("AC-05", f"作废后渠道流水默认 {total_of(mk)} 条（34 − E 2 = 32）", total_of(mk) == 32)
        section("作废", void)

        c.check("其他", f"本次运行未被 Gateway 限流（429 响应 {len(THROTTLED)} 次）", not THROTTLED, THROTTLED[:5])
        unexpected = [e for e in errors if "Spin" not in e and "tip" not in e]
        c.check("其他", f"控制台无非预期 JS 错误（{len(unexpected)}）", not unexpected, unexpected[:5])
        browser.close()
    record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    passed = sum(1 for r in c.results if r[2])
    print(f"\n浏览器验收 {passed}/{len(c.results)} 通过")
    (c.STATE / "browser-results.json").write_text(json.dumps(c.results, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
