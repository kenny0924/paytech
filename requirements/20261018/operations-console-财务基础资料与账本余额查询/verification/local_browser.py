"""浏览器验收（AC-02～AC-07、AC-11～AC-13）：经运营入口登录，按 Pencil 画板 01—12 逐步操作并截图。

前置：local_acceptance.py 已运行且尚未清理（读取最新运行记录 local-environment/e2e/runtime/finance-acceptance-*.json：
财务主体、OE 日历与两个期间（第一个 OPEN、第二个 FUTURE）、OE/USD 账簿、4 个账户与一笔 MANUAL 分录）。
本脚本会开放第二个期间（画板 03），并故意提交一次重复账户（400）与一次未知 entityNo 余额查询（400）。
连接目标由 E2E_TARGET 决定（缺省 local：https://127.0.0.1:18444；test：mor-test http://localhost:28080，见 e2e_http.py）；
运行记录目录两种目标相同。不打印 Token 与口令。用法：[E2E_TARGET=test] python3 -I local_browser.py <截图目录>
"""
import json
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
ORIGIN = e2e_http.OPERATIONS
RUNTIME = ROOT / "local-environment/e2e/runtime"
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def button(scope, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def visible(locator, timeout=10000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001 - Playwright 超时即判定失败
        return False


def shot(page, name, full_page=True):
    """弹窗状态只截视口：整页截图会把滚动后的页头画在遮罩之上。"""
    page.wait_for_timeout(700)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=full_page)


def row(scope, text):
    return scope.locator("tr.ant-table-row").filter(has_text=text).first


def field_selector(name):
    """表单字段 input：各表单带 name 后 id 为 <表单名>_<字段名>（无表单名时为字段名本身）。"""
    return f'input[id$="_{name}"], input[id="{name}"]'


def field(scope, name):
    """弹窗内按字段名定位 input。"""
    return scope.locator(field_selector(name))


def open_select(page, scope, name):
    """点开弹窗内的 antd Select，直到下拉选项出现（首次点击偶尔只聚焦不展开），返回可见选项定位器。"""
    options = page.locator(".ant-select-dropdown:visible .ant-select-item-option")
    for _ in range(3):
        scope.locator(".ant-select").filter(has=page.locator(field_selector(name))).first.click()
        if visible(options, 3000):
            return options
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)
    return options


def option_texts(options):
    """下拉展开动画期间 innerText 可能暂为空，轮询到非空（最多 3 秒）。"""
    for _ in range(15):
        texts = [t for t in options.all_inner_texts() if t.strip()]
        if texts:
            return texts
        options.page.wait_for_timeout(200)
    return []


def notices(page):
    """当前可见的错误通知文案（含 requestId），记录后关闭，避免遮挡后续操作。"""
    texts = [t.replace("\n", " | ") for t in page.locator(".ant-notification-notice").all_inner_texts()]
    for close in page.locator(".ant-notification-notice-close").all():
        try:
            close.click(timeout=2000)
        except Exception:  # noqa: BLE001
            pass
    return texts


def wait_rows(page, count, seconds=10):
    for _ in range(seconds * 5):
        if page.locator("tr.ant-table-row").count() == count:
            return True
        page.wait_for_timeout(200)
    return False


def choose(page, scope, name, text, search=True):
    """antd Select：点开后（可搜索时输入）选中标签包含 text 的选项。"""
    open_select(page, scope, name)
    if search:
        page.keyboard.type(text)
    selector = f'.ant-select-item-option[title^="{text}"]' if search else f'.ant-select-item-option[title="{text}"]'
    page.locator(f".ant-select-dropdown:visible {selector}").first.click()
    page.wait_for_timeout(500)  # 等下拉收起动画结束，避免下一个下拉命中正在淡出的选项


def right_aligned(locator):
    """金额所在单元格右对齐。"""
    try:
        return locator.first.evaluate("e => getComputedStyle(e.closest('td')).textAlign") in ("right", "end")
    except Exception:  # noqa: BLE001
        return False


def idle(page):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(300)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    record = json.loads(sorted(RUNTIME.glob("finance-acceptance-*.json"))[-1].read_text())
    entity_no, client_id, run = record["entityNo"], record["clientId"], record["run"]
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page = context.new_page()
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("requestfailed", lambda r: failed.append(f"FAILED {r.method} {r.url.split('?')[0]} {r.failure}"))
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        idle(page)
        errors.clear()
        failed.clear()

        # 菜单（AC-01 / AC-11）
        finance = page.get_by_role("menuitem", name="财务", exact=True)
        check("AC-11", "侧栏出现一级菜单“财务”", visible(finance))
        finance.first.click()
        subs = [page.get_by_role("menuitem", name=n, exact=True) for n in ("账务基础资料", "账本查询", "余额查询")]
        check("AC-11", "“财务”下有账务基础资料 / 账本查询 / 余额查询", all(visible(x) for x in subs))
        subs[0].first.click()
        idle(page)

        # 01 财务主体
        check("AC-01", "维护权限账号可见“新建财务主体”", visible(button(page, "新建财务主体")))
        entity_row = row(page, f"E2E-FIN-{run}")
        check("AC-02", "财务主体列表含本次主体与“启用”标签", visible(entity_row) and visible(entity_row.locator(".ant-tag").filter(has_text="启用")))
        shot(page, "01-setup-entities")

        # 02 日历与期间
        page.get_by_role("tab", name="日历与期间").click()
        idle(page)
        check("AC-11", "URL 记录标签 tab=calendars", "tab=calendars" in page.url)
        check("AC-11", "未选日历时显示空状态", visible(page.get_by_text("选择一个日历查看会计期间")))
        page.get_by_label("主体标识", exact=True).fill(entity_no)
        button(page, "查询").first.click()
        idle(page)
        row(page, entity_no).click()
        idle(page)
        periods = page.locator(".finance-section tr.ant-table-row")
        check("AC-03", "所选日历列出 2 个期间", visible(periods) and periods.count() == 2)
        future = periods.filter(has_text="FUTURE")
        check("AC-03", "只有 FUTURE 期间有“开放”按钮（OPEN 行无）",
              future.count() == 1 and future.get_by_role("button", name="开放").count() == 1
              and periods.filter(has_text="OPEN").get_by_role("button", name="开放").count() == 0)
        check("AC-12", "期间状态用标签显示", periods.first.locator(".ant-tag").count() >= 1)
        check("AC-01", "维护权限账号可见“新建日历”“新建期间”", visible(button(page, "新建日历")) and visible(button(page, "新建期间")))
        shot(page, "02-setup-calendars-periods")

        # 03 开放期间确认
        future.get_by_role("button", name="开放").click()
        dialog = page.get_by_role("dialog")
        check("AC-03", "开放确认弹窗显示日历、期间、当前状态与不可逆说明",
              visible(dialog.get_by_text(re.compile("不能回到 FUTURE"))) and visible(dialog.get_by_text("当前状态")))
        shot(page, "03-open-period-confirm", full_page=False)
        button(dialog, "确认开放").click()
        idle(page)
        check("AC-03", "确认后期间变为 OPEN，不再有“开放”按钮",
              visible(page.locator(".finance-section tr.ant-table-row").filter(has_text="OPEN").nth(1))
              and page.locator(".finance-section").get_by_role("button", name="开放").count() == 0)

        # 04 账簿与账户
        page.get_by_role("tab", name="账簿与账户").click()
        idle(page)
        check("AC-11", "未选账簿时显示空状态", visible(page.get_by_text("选择一个账簿查看账户")))
        page.get_by_label("主体标识", exact=True).fill(entity_no)
        button(page, "查询").first.click()
        idle(page)
        row(page, entity_no).click()
        idle(page)
        accounts = page.locator(".finance-section tr.ant-table-row")
        check("AC-02", "所选账簿列出 4 个账户", visible(accounts) and accounts.count() == 4)
        cash = accounts.filter(has_text="UNIDENTIFIED_CASH")
        check("AC-12", "金额按币种精度补零并右对齐（123.45）",
              visible(cash.locator(".finance-amount").filter(has_text="123.45")) and right_aligned(cash.locator(".finance-amount")))
        check("AC-12", "零金额补零显示 0.00", visible(accounts.filter(has_text="PENDING").locator(".finance-amount").filter(has_text="0.00")))
        check("AC-04", "账户行显示科目名称（待识别资金）", visible(cash.get_by_text("待识别资金")))
        shot(page, "04-setup-books-accounts")

        # 05 新建账户：重复与标识格式错误
        button(page, "新建账户").click()
        dialog = page.get_by_role("dialog")
        visible(field(dialog, "ownerId"))
        labels = dialog.first.evaluate("""d => {
            const ids = [...document.querySelectorAll('[id]')].map(e => e.id);
            const duplicates = [...new Set(ids.filter((id, i) => ids.indexOf(id) !== i))];
            const targets = [...d.querySelectorAll('label[for]')].map(l => {
                const target = document.getElementById(l.htmlFor);
                return { label: l.textContent, for: l.htmlFor, inside: !!target && d.contains(target) };
            });
            return { duplicates, targets };
        }""")
        outside = [t["for"] for t in labels["targets"] if not t["inside"]]
        check("AC-11", f"新建账户弹窗 {len(labels['targets'])} 个 label[for] 均指向弹窗内输入、页面无重复 id"
              f"（弹窗外：{outside}，重复：{labels['duplicates'][:5]}）",
              len(labels["targets"]) >= 4 and not outside and not labels["duplicates"])
        choose(page, dialog, "accountCode", "CLIENT_PAYABLE")
        choose(page, dialog, "ownerType", "CLIENT", search=False)
        field(dialog, "ownerId").fill(client_id)
        choose(page, dialog, "balanceBucket", "AVAILABLE", search=False)
        button(dialog, "保存").click()
        bucket_error = dialog.get_by_text("该核算对象与余额桶的账户已存在（ACCOUNT_ALREADY_EXISTS）")
        check("AC-02", "重复账户错误就地显示在余额桶字段下", visible(bucket_error)
              and bucket_error.first.evaluate("e => !!e.closest('.ant-form-item')?.querySelector('#balanceBucket, [id$=balanceBucket]')"))
        field(dialog, "ownerId").fill("abc")
        field(dialog, "ownerId").blur()
        check("AC-02", "核算对象标识格式错误就地显示", visible(dialog.get_by_text("必须是正十进制整数字符串")))
        shot(page, "05-create-account-error", full_page=False)
        open_select(page, dialog, "accountCode")
        page.keyboard.type("CLIENT_PAYABLE")
        page.wait_for_timeout(300)
        options_text = page.locator(".ant-select-dropdown:visible").inner_text()
        page.keyboard.press("Escape")
        check("AC-04", "科目下拉显示名称与正常方向", "Client 应付款" in options_text and "（贷）" in options_text)
        button(dialog, "取消").click()

        # 06 新建账簿
        button(page, "新建账簿").click()
        dialog = page.get_by_role("dialog").filter(has_text="新建账簿")
        field(dialog, "entityId").fill(entity_no)
        currency_options = option_texts(open_select(page, dialog, "currency"))
        check("AC-04", f"币种下拉只含启用项（{currency_options}）", currency_options == ["USD · 2 位小数"])
        page.locator(".ant-select-dropdown:visible .ant-select-item-option").first.click()
        page.wait_for_timeout(500)
        chart_options = option_texts(open_select(page, dialog, "chartCode"))
        page.locator(".ant-select-dropdown:visible .ant-select-item-option").first.click()
        check("AC-04", f"科目表下拉只含启用项（{chart_options}）", len(chart_options) == 1 and chart_options[0].startswith("STANDARD"))
        shot(page, "06-create-book", full_page=False)
        button(dialog, "取消").click()

        # 12 科目表与币种（只读）
        page.get_by_role("tab", name="科目表与币种").click()
        idle(page)
        check("AC-04", "科目表标签页无任何新建按钮", page.get_by_role("button", name=re.compile("^新建")).count() == 0)
        check("AC-04", "默认选中 STANDARD 并列出 29 个科目", visible(page.get_by_text(re.compile(r"STANDARD（29 个科目）"))))
        shot(page, "12-setup-chart-currencies")

        # 07 账本查询：账簿 › 账户
        page.get_by_role("menuitem", name="账本查询", exact=True).click()
        idle(page)
        page.get_by_label("主体标识", exact=True).fill(entity_no)
        button(page, "查询").first.click()
        idle(page)
        check("AC-13", "账簿筛选写入 URL", f"entityId={entity_no}" in page.url)
        row(page, entity_no).click()
        idle(page)
        crumb = page.locator(".finance-breadcrumb")
        check("AC-13", f"面包屑显示“账簿 › {entity_no} / USD”", visible(crumb.get_by_text(f"{entity_no} / USD")))
        check("AC-05", "账本查询账户列表只读（无“新建账户”）", button(page, "新建账户").count() == 0)
        page.get_by_label("余额桶", exact=True).click()
        page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="NONE").first.click()
        button(page, "查询").first.click()
        idle(page)
        check("AC-05", "账户按余额桶 NONE 筛选为 2 个且写入 URL",
              page.locator("tr.ant-table-row").count() == 2 and "balanceBucket=NONE" in page.url)
        shot(page, "07-ledger-accounts")

        # 08 流水
        row(page, "UNIDENTIFIED_CASH").click()
        idle(page)
        line = page.locator("tr.ant-table-row").first
        check("AC-06", "流水含 MANUAL 来源与人工调整单号", visible(line.get_by_text(re.compile("MANUAL · 人工调整")))
              and visible(line.get_by_text(re.compile(r"^MA\d+"))))
        check("AC-12", "流水金额与行后余额右对齐", line.locator(".finance-amount").count() == 2 and right_aligned(line.locator(".finance-amount")))
        check("AC-06", "页脚提示行后余额按全量历史累计", visible(page.get_by_text(re.compile("行后余额按全量历史累计"))))
        check("AC-13", "三级面包屑", crumb.locator(".ant-breadcrumb-link, li").filter(has_text="UNIDENTIFIED_CASH").count() >= 1)
        shot(page, "08-ledger-journal-lines")

        # 09 分录详情
        line.locator("a.mono").click()
        drawer = page.locator(".ant-drawer")
        check("AC-06", "分录详情抽屉显示人工调整单号与“借贷平衡”",
              visible(drawer.get_by_text("借贷平衡")) and visible(drawer.get_by_text(re.compile(r"^MA\d+"))))
        check("AC-06", "分录详情含 2 行", drawer.locator("tr.ant-table-row").count() == 2)
        check("AC-13", "URL 记录分录 entry=", "entry=" in page.url)
        shot(page, "09-journal-entry-drawer")
        drawer.locator(".ant-drawer-close").first.click()
        try:
            page.locator(".ant-drawer-content-wrapper").first.wait_for(state="hidden", timeout=5000)
        except Exception:  # noqa: BLE001 - 关闭按钮未生效时用 Esc 兜底
            page.keyboard.press("Escape")
            page.locator(".ant-drawer-content-wrapper").first.wait_for(state="hidden", timeout=5000)
        check("AC-13", "关闭抽屉后 URL 去掉 entry", "entry=" not in page.url)

        # AC-13 面包屑回退保留条件
        crumb.get_by_text(f"{entity_no} / USD").click()
        idle(page)
        check("AC-13", f"回到账户级仍保留余额桶筛选（{page.url.split('?')[-1]}）",
              "balanceBucket=NONE" in page.url and "account=" not in page.url and wait_rows(page, 2))
        crumb.get_by_text("账簿", exact=True).click()
        idle(page)
        check("AC-13", "回到账簿级仍保留主体标识筛选", f"entityId={entity_no}" in page.url and "book=" not in page.url
              and page.get_by_label("主体标识", exact=True).input_value() == entity_no)

        seen = notices(page)
        check("AC-11", f"基础资料与账本下钻过程无错误通知（{seen}）", not seen)

        # 10 / 11 余额查询
        page.get_by_role("menuitem", name="余额查询", exact=True).click()
        idle(page)
        check("AC-11", "未查询时显示空状态", visible(page.get_by_text(re.compile("填写 Owner ID、entityNo 与币种后查询"))))
        page.get_by_label("Owner ID", exact=True).fill(client_id)
        page.get_by_label("经营主体 entityNo", exact=True).fill(entity_no)
        page.get_by_label("币种", exact=True).click()
        page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="USD").first.click()
        button(page, "查询").click()
        idle(page)
        cards = page.locator(".finance-balance-card")
        check("AC-07", "Client 显示五张余额卡（含保证金）", cards.count() == 5 and visible(cards.filter(has_text="保证金")))
        check("AC-07", "提示“Available 不等于可出款资格”", visible(page.get_by_text(re.compile("Available 不等于可出款资格"))))
        shot(page, "10-balances-result")
        page.get_by_text("Local Partner", exact=True).click()
        button(page, "查询").click()
        idle(page)
        check("AC-07", "Partner 显示四张卡、无保证金", cards.count() == 4 and cards.filter(has_text="保证金").count() == 0)
        page.get_by_text("Client", exact=True).click()
        page.get_by_label("经营主体 entityNo", exact=True).fill("ZZ999")
        button(page, "查询").click()
        idle(page)
        check("AC-07", "账簿不存在时 entityNo 字段显示 LEDGER_BOOK_NOT_FOUND（而非零余额）",
              visible(page.get_by_text("未找到该经营主体与币种的账簿（LEDGER_BOOK_NOT_FOUND）")) and cards.count() == 0)
        check("AC-11", "账簿不存在时显示空状态", visible(page.get_by_text("未找到账簿，请确认 entityNo 与币种后重新查询")))
        shot(page, "11-balances-not-found")

        expected = re.compile(r"^400 (POST .*/finance/ledger-books/v1/\d+/accounts|GET .*/finance/balances/v1/clients/\d+)$")
        unexpected = [f for f in failed if not expected.match(f)]
        check("AC-11", f"无意外错误响应（{unexpected[:3]}）", not unexpected)
        # antd 开发模式的弃用告警（console.error 输出）不属于页面错误，单独列出
        deprecations = sorted({e for e in errors if e.startswith("Warning: [antd:")})
        if deprecations:
            print(f"[说明] antd 开发模式弃用告警：{deprecations}")
        console = [e for e in errors if "400" not in e and not e.startswith("Warning: [antd:")]
        check("AC-11", f"控制台无错误（{console[:2]}）", not console)
        browser.close()
    passed = sum(1 for r in results if r[2])
    print(f"\n{passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
