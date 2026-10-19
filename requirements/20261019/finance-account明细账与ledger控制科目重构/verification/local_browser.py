"""阶段 5 浏览器验收（AC-14～AC-17、AC-19、AC-20）：经运营入口登录，按画板 04 / 05 / 07 / 08 / 09 / 11 / 13 / 14 / 15 走查并截图。

前置：local_acceptance.py 已运行且尚未清理（读取最新运行记录 local-environment/e2e/runtime/subledger-acceptance-*.json：
新 OE 日历与 OPEN 期间、OE / USD 账簿、CHANNEL_ACCOUNT 账户、两笔 ACCOUNT_POSTING 分录（事实 100.00 与替代 80.00）、
account Owner 账户 CLIENT_PAYABLE / CLIENT / PENDING 净 80.00）。本脚本只读，故意提交一次重复账户（400）与一次无账户余额查询（400）。
结构照抄 requirements/20261018/operations-console-财务基础资料与账本余额查询/verification/local_browser.py。
连接目标由 E2E_TARGET 决定（缺省 local）；不打印 Token 与口令。用法：python3 -I local_browser.py <截图目录>
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

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else None
ORIGIN = e2e_http.OPERATIONS
RUNTIME = ROOT / "local-environment/e2e/runtime"
LEDGER_OWNER_TYPES = ["OPERATING_ENTITY", "FINANCE_ENTITY", "CHANNEL_ACCOUNT", "CASH_ACCOUNT", "TAX_AUTHORITY"]
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
    """弹窗 / 抽屉状态只截视口：整页截图会把滚动后的页头画在遮罩之上。"""
    page.wait_for_timeout(700)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=full_page)


def row(scope, text):
    return scope.locator("tr.ant-table-row").filter(has_text=text).first


def field_selector(name):
    return f'input[id$="_{name}"], input[id="{name}"]'


def field(scope, name):
    return scope.locator(field_selector(name))


def open_select(page, scope, name):
    """点开 antd Select，直到下拉选项出现（首次点击偶尔只聚焦不展开），返回可见选项定位器。"""
    options = page.locator(".ant-select-dropdown:visible .ant-select-item-option")
    for _ in range(3):
        scope.locator(".ant-select").filter(has=page.locator(field_selector(name))).first.click()
        if visible(options, 3000):
            return options
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)
    return options


def option_texts(options):
    for _ in range(15):
        texts = [t for t in options.all_inner_texts() if t.strip()]
        if texts:
            return texts
        options.page.wait_for_timeout(200)
    return []


def pick(page, scope, name, text):
    """antd Select 选中标签等于 text 的选项。"""
    open_select(page, scope, name)
    page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text=re.compile("^" + re.escape(text))).first.click()
    page.wait_for_timeout(500)


def notices(page):
    texts = [t.replace("\n", " | ") for t in page.locator(".ant-notification-notice").all_inner_texts()]
    for close in page.locator(".ant-notification-notice-close").all():
        try:
            close.click(timeout=2000)
        except Exception:  # noqa: BLE001
            pass
    return texts


def wait_rows(page, count, within="", seconds=10):
    """等待 within 范围内（缺省整页）的表格行数等于 count。"""
    rows = page.locator(f"{within} tr.ant-table-row".strip())
    for _ in range(seconds * 5):
        if rows.count() == count:
            return True
        page.wait_for_timeout(200)
    return False


def right_aligned(locator):
    try:
        return locator.first.evaluate("e => getComputedStyle(e.closest('td')).textAlign") in ("right", "end")
    except Exception:  # noqa: BLE001
        return False


def idle(page):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(300)


def main():
    if OUT is None:
        raise SystemExit("用法：python3 -I local_browser.py <截图目录>")
    OUT.mkdir(parents=True, exist_ok=True)
    record = json.loads(sorted(RUNTIME.glob("subledger-acceptance-*.json"))[-1].read_text())
    entity_no, client_id, run = record["entityNo"], record["clientId"], record["run"]
    posting1, posting2 = record["postingIds"][:2]
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

        # 菜单
        page.get_by_role("menuitem", name="财务", exact=True).first.click()
        subs = [page.get_by_role("menuitem", name=n, exact=True) for n in ("账务基础资料", "账本查询", "余额查询", "明细账查询")]
        check("AC-17", "“财务”下有账务基础资料 / 账本查询 / 余额查询 / 明细账查询", all(visible(x) for x in subs))

        # 04 账簿与账户：只有平台类 Owner 与控制账户，桶 NONE
        subs[0].first.click()
        idle(page)
        page.get_by_role("tab", name="账簿与账户").click()
        idle(page)
        page.get_by_label("主体标识", exact=True).fill(entity_no)
        button(page, "查询").first.click()
        idle(page)
        row(page, entity_no).click()
        idle(page)
        accounts = page.locator(".finance-section tr.ant-table-row")
        visible(accounts)
        texts = accounts.all_inner_texts()
        check("AC-14", f"账户列表 {len(texts)} 行，Owner 类型均为 ledger 五值、余额桶均为 NONE",
              len(texts) >= 3 and all(any(t in text for t in LEDGER_OWNER_TYPES) and "NONE" in text
                                      and not re.search(r"\b(CLIENT|LOCAL_PARTNER)\b", text.replace("CLIENT_PAYABLE", "")) for text in texts))
        control = accounts.filter(has_text="CLIENT_PAYABLE")
        check("AC-14", f"CLIENT_PAYABLE 控制账户以 OPERATING_ENTITY {entity_no} 为核算对象、净额 80.00 右对齐",
              control.count() == 1 and entity_no in control.inner_text() and visible(control.locator(".finance-amount").filter(has_text="80.00"))
              and right_aligned(control.locator(".finance-amount")))
        shot(page, "04-setup-books-accounts")

        # 05 新建账户：Owner 类型五值、余额桶固定 NONE 且禁用、重复账户就地报错
        button(page, "新建账户").click()
        dialog = page.get_by_role("dialog")
        visible(field(dialog, "ownerId"))
        owner_options = option_texts(open_select(page, dialog, "ownerType"))
        page.keyboard.press("Escape")
        check("AC-14", f"核算对象类型下拉只有五值（{owner_options}）", owner_options == LEDGER_OWNER_TYPES)
        bucket = dialog.locator(".ant-select").filter(has=page.locator(field_selector("balanceBucket"))).first
        check("AC-14", "余额桶固定 NONE 且禁用", "NONE" in bucket.inner_text() and "ant-select-disabled" in (bucket.get_attribute("class") or ""))
        check("AC-14", "表单提示“Client / Partner 余额在明细账，不在总账开户”", visible(dialog.get_by_text(re.compile("Client / Partner 余额在明细账"))))
        channel_row = accounts.filter(has_text="CHANNEL_UNSETTLED_RECEIVABLE")
        channel_owner = re.search(r"\b(66\d{16})\b", channel_row.inner_text())
        open_select(page, dialog, "accountCode")
        page.keyboard.type("CHANNEL_UNSETTLED_RECEIVABLE")
        page.locator('.ant-select-dropdown:visible .ant-select-item-option[title^="CHANNEL_UNSETTLED_RECEIVABLE"]').first.click()
        page.wait_for_timeout(500)
        pick(page, dialog, "ownerType", "CHANNEL_ACCOUNT")
        field(dialog, "ownerId").fill(channel_owner.group(1) if channel_owner else "66")
        button(dialog, "保存").click()
        bucket_error = dialog.get_by_text(re.compile("ACCOUNT_ALREADY_EXISTS"))
        check("AC-14", "重复账户错误就地显示在余额桶字段下", visible(bucket_error)
              and bucket_error.first.evaluate("e => !!e.closest('.ant-form-item')?.querySelector('#balanceBucket, [id$=balanceBucket]')"))
        shot(page, "05-create-account-error", full_page=False)
        button(dialog, "取消").click()

        # 07 账本查询：账簿 › 账户
        page.get_by_role("menuitem", name="账本查询", exact=True).click()
        idle(page)
        page.get_by_label("主体标识", exact=True).fill(entity_no)
        button(page, "查询").first.click()
        idle(page)
        row(page, entity_no).click()
        idle(page)
        owner_filter = page.locator(".ant-select").filter(has=page.locator(field_selector("ownerType"))).first
        check("AC-14", "核算对象类型筛选默认“全部”", "全部" in owner_filter.inner_text())
        check("AC-15", "说明文指向「明细账查询」", visible(page.get_by_text(re.compile("Client / Partner 明细与余额桶在「明细账查询」"))))
        shot(page, "07-ledger-accounts")

        # 08 控制账户流水：ACCOUNT_POSTING 来源与批次序号
        row(page, "CLIENT_PAYABLE").click()
        idle(page)
        lines = page.locator("tr.ant-table-row")
        check("AC-15", f"控制账户流水 3 行（每条 account 流水一行），来源 ACCOUNT_POSTING 带批次 …{posting1[-4:]} · 序号 1 与 …{posting2[-4:]} · 序号 2",
              wait_rows(page, 3) and visible(lines.filter(has_text=f"批次 …{posting1[-4:]} · 序号 1"))
              and visible(lines.filter(has_text=f"批次 …{posting2[-4:]} · 序号 2")) and visible(page.get_by_text(re.compile("ACCOUNT_POSTING"))))
        check("AC-19", "流水金额与行后余额右对齐", right_aligned(lines.first.locator(".finance-amount")))
        shot(page, "08-ledger-journal-lines")

        # 09 分录抽屉：account 批次与控制科目行流水链接
        lines.filter(has_text="序号 1").first.locator("a.mono").click()
        drawer = page.locator(".ant-drawer")
        link = drawer.locator("a.finance-entry-link")
        check("AC-15", "分录抽屉显示 ACCOUNT_POSTING 来源、account 批次与借贷平衡",
              visible(drawer.get_by_text("借贷平衡")) and visible(drawer.get_by_text("account 批次"))
              and visible(drawer.get_by_text(re.compile("account 批次 ID"))))
        check("AC-15", f"控制科目行带“流水 …后 4 位 ↗”链接（{link.count()} 个）", visible(link) and link.count() == 1
              and re.fullmatch(r"流水 …\d{4} ↗", link.first.inner_text().strip()))
        shot(page, "09-journal-entry-drawer")

        # 15 从分录行跳到明细账批次抽屉
        link.first.click()
        idle(page)
        posting_drawer = page.locator(".ant-drawer").filter(has_text="入账批次")
        check("AC-20", f"跳转到 /finance/subledger 并带 posting / entry（{page.url.split('?')[-1][:80]}）",
              "/finance/subledger" in page.url and f"posting={posting1}" in page.url and "entry=" in page.url)
        check("AC-17", "批次抽屉：序号 1、ECONOMIC_FACT、MANUAL_ADJUSTMENT、已发布、1 行流水且被高亮",
              visible(posting_drawer.get_by_text("入账批次 · 序号 1")) and visible(posting_drawer.get_by_text(re.compile("ECONOMIC_FACT")))
              and visible(posting_drawer.get_by_text("MANUAL_ADJUSTMENT")) and posting_drawer.get_by_text("尚未发布").count() == 0
              and visible(posting_drawer.get_by_text("1 行流水")) and posting_drawer.locator("tr.finance-row-selected").count() == 1)
        shot(page, "15-subledger-posting-drawer", full_page=False)

        # 14 抽屉行「账户流水」进入 Owner 账户流水
        posting_drawer.get_by_text("账户流水").first.click()
        idle(page)
        crumb = page.locator(".finance-breadcrumb")
        check("AC-20", f"面包屑“明细账 › OPERATING_ENTITY · {entity_no} / USD › CLIENT_PAYABLE · CLIENT … · PENDING”",
              visible(crumb.get_by_text(f"OPERATING_ENTITY · {entity_no} / USD"))
              and visible(crumb.get_by_text(f"CLIENT_PAYABLE · CLIENT {client_id} · PENDING")))
        entries = page.locator(".table-panel tr.ant-table-row")
        check("AC-17", "账户流水 3 行：批次 1 贷 100.00，批次 2 冲回借 100.00（← 流水 …）与贷 80.00",
              wait_rows(page, 3, ".table-panel") and visible(entries.filter(has_text="← 流水 …"))
              and visible(entries.filter(has_text="80.00")) and entries.filter(has_text="100.00").count() == 2)
        summary = page.locator(".finance-summary")
        check("AC-20", "经分录「流水 …↗」→ 批次抽屉「账户流水」进入：摘要显示方向（贷 CREDIT）与当前净余额 80.00 USD（C-1 修复）",
              visible(summary.get_by_text("方向")) and visible(summary.get_by_text(re.compile("CREDIT")))
              and visible(summary.get_by_text(re.compile(r"80\.00 USD"))))
        shot(page, "14-subledger-entries")

        # 13 返回账户级
        crumb.get_by_text(f"OPERATING_ENTITY · {entity_no} / USD").click()
        idle(page)
        accounts = page.locator(".table-panel tr.ant-table-row")
        check("AC-17", "账户级列出 1 个 Owner 账户（CLIENT_PAYABLE / CLIENT / PENDING / 净 80.00）",
              wait_rows(page, 1, ".table-panel") and visible(accounts.filter(has_text="PENDING").filter(has_text="80.00")))
        check("AC-19", "净余额右对齐", right_aligned(accounts.first.locator(".finance-amount")))
        shot(page, "13-subledger-accounts")

        # AC-20 筛选保留：Owner 类型 CLIENT → 下钻 → 返回仍保留
        form = page.locator("form").filter(has=page.locator(field_selector("ownerType"))).first
        pick(page, form, "ownerType", "CLIENT")
        button(page, "查询").first.click()
        idle(page)
        check("AC-20", "Owner 类型筛选写入 URL", "ownerType=CLIENT" in page.url)
        page.locator(".table-panel tr.ant-table-row").first.click()
        idle(page)
        check("AC-17", "从账户列表进入流水：摘要显示方向与当前净余额 80.00 USD",
              visible(page.locator(".finance-summary").get_by_text(re.compile(r"80\.00 USD"))) and visible(page.locator(".finance-summary").get_by_text("方向")))
        page.locator(".finance-breadcrumb").get_by_text(f"OPERATING_ENTITY · {entity_no} / USD").click()
        idle(page)
        owner_select = page.locator(".ant-select").filter(has=page.locator(field_selector("ownerType"))).first
        check("AC-20", f"流水返回账户级仍保留 Owner 类型筛选（{page.url.split('?')[-1][:100]}）",
              "ownerType=CLIENT" in page.url and "CLIENT" in owner_select.inner_text() and wait_rows(page, 1, ".table-panel"))
        pick(page, form, "ownerType", "LOCAL_PARTNER")
        button(page, "查询").first.click()
        idle(page)
        check("AC-19", "无匹配账户显示空状态“没有符合条件的 Owner 账户”", visible(page.get_by_text("没有符合条件的 Owner 账户")))
        page.locator(".finance-breadcrumb").get_by_text("明细账", exact=True).click()
        idle(page)
        check("AC-19", "回到明细账首级显示未查询空状态", visible(page.get_by_text("选择主体类型并填写主体标识与币种后查询 Owner 账户")))

        seen = notices(page)
        check("AC-19", f"基础资料、账本与明细账下钻过程无错误通知（{seen}）", not seen)

        # 10 / 11 余额查询
        page.get_by_role("menuitem", name="余额查询", exact=True).click()
        idle(page)
        page.get_by_label("Owner ID", exact=True).fill(client_id)
        page.get_by_label("经营主体 entityNo", exact=True).fill(entity_no)
        page.get_by_label("币种", exact=True).click()
        page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="USD").first.click()
        button(page, "查询").click()
        idle(page)
        cards = page.locator(".finance-balance-card")
        check("AC-16", f"Client 余额五张卡，Pending 80.00、其余 0.00（{cards.count()}）",
              cards.count() == 5 and visible(cards.filter(has_text="80.00")) and cards.filter(has_text="0.00").count() >= 4)
        shot(page, "10-balances-result")
        page.get_by_label("Owner ID", exact=True).fill("8800000000000001")
        button(page, "查询").click()
        idle(page)
        check("AC-16", "无账户时 Owner ID 字段显示 OWNER_ACCOUNT_NOT_FOUND（而非零余额）",
              visible(page.get_by_text(f"该 Client 在 {entity_no} / USD 下没有账户（OWNER_ACCOUNT_NOT_FOUND）")) and cards.count() == 0)
        check("AC-19", "无账户空状态文案", visible(page.get_by_text(re.compile("该 Owner 在此经营主体与币种下没有账户"))))
        shot(page, "11-balances-not-found")

        expected = re.compile(r"^400 (POST .*/finance/ledger-books/v1/\d+/accounts|GET .*/finance/balances/v1/clients/\d+)$")
        unexpected = [f for f in failed if not expected.match(f)]
        check("AC-19", f"无意外错误响应（{unexpected[:3]}）", not unexpected)
        deprecations = sorted({e for e in errors if e.startswith("Warning: [antd:")})
        if deprecations:
            print(f"[说明] antd 开发模式弃用告警：{deprecations}")
        console = [e for e in errors if "400" not in e and not e.startswith("Warning: [antd:")]
        check("AC-19", f"控制台无错误（{console[:2]}）", not console)
        browser.close()
    passed = sum(1 for r in results if r[2])
    print(f"\n{passed}/{len(results)} 通过（运行 {run}）")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
