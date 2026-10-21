"""对账切片 1 阶段 5：浏览器验收 AC-V1–V6（无头 Chromium，经私有栈运营入口 https://127.0.0.1:18454 真实登录）。

续用 s1_flow.py 的运行记录（MAKER / CHECKER 合成人员、清算账户、银行账户）。场景 S2：
  夹具（API）：发布 PAYMENT 50.00（预估费 0.30）与 PAYMENT 60.00 两条 BillingFactRecorded；IAM 新建只有财务读权限的 NOPERM 人员；
  浏览器（MAKER）：菜单 → 上传本地校验失败 / 服务端拒绝 / A（含 1.00 调整行）/ B 四个渠道账单 → 重复上传看后端 detail → 账单 A 详情门禁 →
  异常列表筛选 → 提交 CLASSIFY；（CHECKER）复核页看提交人、内容与金额并批准；（MAKER）上传银行对账单（一笔 100.00）→ 人工多选匹配
  （超出不能提交 → 调整后提交）→ 汇总；（NOPERM）财务菜单无「对账」分组。
截图写 verification/screenshots/；不打印口令。用法：python3 -I browser_acceptance.py <run-*.json>
"""
import json
import re
import site
import sys
import time
import uuid
from datetime import date
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, str(Path(__file__).resolve().parent))
from playwright.sync_api import sync_playwright  # noqa: E402

import recon5_common as c  # noqa: E402

OUT = Path(__file__).resolve().parent / "screenshots"
OUT.mkdir(exist_ok=True)
FILES = c.STATE / "browser-files"
FILES.mkdir(exist_ok=True)
TODAY = date.today().isoformat()
R = "/finance/reconciliation/v1"
SEVEN = ("清算账户", "银行账户", "渠道账单", "平台流水", "银行对账", "对账异常", "在途汇总")


def visible(locator, timeout=10000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def shot(page, name):
    page.wait_for_timeout(1000)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
    return name


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def login(page, email, password):
    page.goto(c.ORIGIN + "/")
    page.get_by_label("Email / 初始化账号").fill(email)
    page.get_by_label("密码").fill(password)
    button(page, "登录").click()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(1500)


def goto(page, path):
    page.goto(c.ORIGIN + path)
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)


def choose(page, label, option_text, scope=None):
    scope = scope or page
    scope.get_by_label(label, exact=True).click(force=True)
    page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text=option_text).first.click()


def write_files(run, ca_code, opening):
    """S2 的四个渠道账单与一个银行对账单 CSV（两行 #HEADER 写法）。"""
    head = "#HEADER,clearing_account_code,channel_batch_ref,settlement_date,currency,gross_amount,fee_amount,net_amount,cash_direction\n"
    cols = "line_no,line_type,direction,amount,fee_amount,reference_type,external_transaction_id,transaction_date,terminal_id,remark\n"
    files = {
        "s2-local-invalid.csv": head + f"#HEADER,{ca_code},S2-{run}-L,{TODAY},USD,50.00,0.30,49.70,CREDIT\n" + cols
        + f"1,PAYMENT,CREDIT,abc,0.30,NI_ORDER_REF,NI-ORD-{run}-A,{TODAY},T01,\n2,PAYMENT,SIDEWAYS,1.00,0,NI_ORDER_REF,X,{TODAY},T01,\n",
        "s2-server-reject.csv": head + f"#HEADER,{ca_code},S2-{run}-R,{TODAY},USD,51.00,0.30,50.70,CREDIT\n" + cols
        + f"1,PAYMENT,CREDIT,50.00,0.30,NI_ORDER_REF,NI-ORD-{run}-A,{TODAY},T01,\n",
        "s2-A.csv": head + f"#HEADER,{ca_code},S2-{run}-A,{TODAY},USD,49.00,0.30,48.70,CREDIT\n" + cols
        + f"1,PAYMENT,CREDIT,50.00,0.30,NI_ORDER_REF,NI-ORD-{run}-A,{TODAY},T01,\n3,ADJUSTMENT,DEBIT,1.00,0,,,{TODAY},,service fee\n",
        "s2-B.csv": head + f"#HEADER,{ca_code},S2-{run}-B,{TODAY},USD,60.00,0,60.00,CREDIT\n" + cols
        + f"1,PAYMENT,CREDIT,60.00,0,NI_ORDER_REF,NI-ORD-{run}-B,{TODAY},T02,\n",
        "s2-bank.csv": "#HEADER,bank_account_last4,statement_ref,period_start,period_end,currency,opening_balance,closing_balance\n"
        + f"#HEADER,4321,ENBD-S2-{run},{TODAY},{TODAY},USD,{opening:.2f},{opening + 100:.2f}\n"
        + "line_no,bank_transaction_ref,value_date,direction,amount,counterparty_name,reference_text\n"
        + f"1,ENBD-S2-TXN-{run},{TODAY},CREDIT,100.00,PAYERMAX MOCK,SETTLEMENT S2-{run}-A S2-{run}-B\n",
    }
    # 账单 A 行号跳号（1、3）会被拒：行号必须连续 —— 改为 1、2
    files["s2-A.csv"] = files["s2-A.csv"].replace("\n3,ADJUSTMENT", "\n2,ADJUSTMENT")
    for name, text in files.items():
        (FILES / name).write_text(text)
    return {name: str(FILES / name) for name in files}


def upload(page, path, expect_text, timeout=30000):
    page.locator(".ant-drawer:visible input[type=file]").set_input_files(path)
    return visible(page.get_by_text(re.compile(expect_text)), timeout)


def close_drawer(page):
    page.locator(".ant-drawer:visible").get_by_role("button", name=re.compile(r"^关\s?闭$")).last.click()
    page.wait_for_timeout(500)


def main():
    record_path = Path(sys.argv[1])
    record = json.loads(record_path.read_text())
    run = uuid.uuid4().hex[:4].upper()
    record["browserRun"] = run
    people = record["people"]
    ca = record["clearingAccount"]
    cash_id = record["cashAccountId"]
    admin = c.admin_token()
    if "NOPERM" not in people:
        c.synthetic_person(admin, "NOPERM", [c.PERM["ACCOUNTING_READ"]], record["run"], record)
        people = record["people"]
    record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    facts = [c.fact("PAYMENT", ca["clearingAccountId"], "50.00", "NI_ORDER_REF", f"NI-ORD-{run}-A", channel_cost="0.30"),
             c.fact("PAYMENT", ca["clearingAccountId"], "60.00", "NI_ORDER_REF", f"NI-ORD-{run}-B")]
    c.publish_facts(facts)
    record.setdefault("browserFacts", []).extend(f["factId"] for f in facts)
    record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    last = c.recon_sql(f"SELECT closing_balance FROM t_bank_statement WHERE cash_account_id={cash_id} AND status='IMPORTED' "
                       "ORDER BY period_end DESC, create_time DESC LIMIT 1")
    files = write_files(run, ca["clearingAccountCode"], float(last[0][0]) if last else 1067.40)
    maker_tok = c.login(people["MAKER"]["email"], people["MAKER"]["password"])
    c.wait_until(lambda: c.recon_sql(f"SELECT COUNT(*) FROM t_platform_txn WHERE billing_fact_id IN ({facts[0]['factId']},{facts[1]['factId']})")[0][0] == "2", 30)
    shots = []
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch()

        def new_page():
            context = browser.new_context(viewport={"width": 1512, "height": 900}, ignore_https_errors=True)
            page = context.new_page()
            page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
            return page

        maker = new_page()
        login(maker, people["MAKER"]["email"], people["MAKER"]["password"])
        group = maker.get_by_role("menuitem", name="对账", exact=True)
        if not visible(group, 3000):
            maker.get_by_role("menuitem", name="财务", exact=True).first.click()
            maker.wait_for_timeout(500)
        has_group = visible(group, 5000)
        if has_group and not visible(maker.get_by_role("menuitem", name="清算账户", exact=True), 2000):
            group.first.click()
            maker.wait_for_timeout(500)
        entries = [n for n in SEVEN if visible(maker.get_by_role("menuitem", name=n, exact=True), 3000)]
        c.check("AC-V1", f"财务菜单下「对账」分组含七个入口（{len(entries)}：{'、'.join(entries)}）", has_group and len(entries) == 7)
        shots.append(shot(maker, "01-menu-recon-group"))

        # ---------- 渠道账单上传（AC-V2、AC-V6） ----------
        goto(maker, "/finance/reconciliation/statements")
        button(maker, "上传渠道账单").click()
        ok = upload(maker, files["s2-local-invalid.csv"], "未通过本地校验")
        rows = maker.locator(".ant-drawer:visible .ant-table-tbody tr").count()
        c.check("AC-V2", f"本地校验不通过：不上传，显示原因列表（{rows} 行，含行号）", ok and rows >= 1)
        shots.append(shot(maker, "02-upload-local-invalid"))
        ok = upload(maker, files["s2-server-reject.csv"], "被拒绝")
        reason_rows = maker.locator(".ant-drawer:visible .ant-table-tbody tr").all_inner_texts()
        c.check("AC-V2", f"服务端拒绝：显示批次号与原因列表（{len(reason_rows)} 行）", ok and len(reason_rows) >= 1)
        shots.append(shot(maker, "03-upload-server-rejected"))
        ok = upload(maker, files["s2-A.csv"], f"渠道账单 S2-{run}-A 已导入 2 行")
        c.check("AC-V2", f"上传成功：显示账单号 S2-{run}-A 与行数 2", ok)
        shots.append(shot(maker, "04-upload-success"))
        ok = upload(maker, files["s2-B.csv"], f"渠道账单 S2-{run}-B 已导入 1 行")
        c.check("AC-V2", f"第二张账单 S2-{run}-B 上传成功（1 行）", ok)
        ok = upload(maker, files["s2-A.csv"], "须先作废", 15000)
        c.check("AC-V6", "重复上传同批次：页面显示后端业务错误 detail（须先作废）", ok)
        shots.append(shot(maker, "05-business-error-detail"))
        close_drawer(maker)

        # ---------- 账单详情（AC-V3） ----------
        sid = c.recon_sql(f"SELECT id FROM t_channel_statement WHERE channel_batch_ref='S2-{run}-A' AND status<>'REJECTED'")[0][0]
        record.setdefault("statements", []).append(sid)
        c.wait_until(lambda: c.recon_sql(f"SELECT COUNT(*) FROM t_recon_exception WHERE statement_id={sid}")[0][0] == "1", 30)
        goto(maker, f"/finance/reconciliation/statements?statement={sid}")
        drawer = maker.locator(".ant-drawer:visible")
        gate_fail = drawer.locator(".recon-gate-item.is-fail").all_inner_texts()
        c.check("AC-V3", f"对平前显示未满足的门禁项（{[g.replace(chr(10), ' ') for g in gate_fail]}）",
                any("调整行" in g for g in gate_fail) and any("阻塞" in g for g in gate_fail))
        line_text = " ".join(drawer.locator(".ant-table-tbody tr").all_inner_texts())
        c.check("AC-V3", "按行显示匹配状态、对应平台流水与实际费用（行 1 已匹配 + 平台流水 ID + 0.30）",
                "已匹配" in line_text and "0.30" in line_text and bool(re.search(r"\d{15,}", line_text)))
        shots.append(shot(maker, "06-statement-detail-gate"))

        # ---------- 异常列表与处置（AC-V4） ----------
        goto(maker, "/finance/reconciliation/exceptions")
        total = maker.get_by_text(re.compile("按账龄从长到短"))
        c.check("AC-V4", "异常列表按账龄排序（分页说明「按账龄从长到短」，账龄列）", visible(total) and visible(maker.get_by_role("columnheader", name="账龄")))
        choose(maker, "类型", "调整行待归类")
        choose(maker, "清算账户", ca["clearingAccountCode"])
        button(maker, "查询").click()
        maker.wait_for_load_state("networkidle")
        maker.wait_for_timeout(800)
        rows = maker.locator(".ant-table-tbody tr.clickable-row").count()
        c.check("AC-V4", f"按类型 + 清算账户筛选（未结默认）：命中 {rows} 条（S2 调整行）", rows == 1)
        shots.append(shot(maker, "07-exceptions-filtered"))
        choose(maker, "状态", "已解决")
        button(maker, "查询").click()
        maker.wait_for_load_state("networkidle")
        maker.wait_for_timeout(800)
        resolved = maker.locator(".ant-table-tbody tr.clickable-row").count()
        c.check("AC-V4", f"按状态筛选「已解决」：S1 的调整行异常（{resolved} 条）", resolved == 1)
        exception_id = c.recon_sql(f"SELECT id FROM t_recon_exception WHERE statement_id={sid}")[0][0]
        goto(maker, f"/finance/reconciliation/exceptions?exception={exception_id}")
        drawer = maker.locator(".ant-drawer:visible")
        choose(maker, "处置方式", "归类", drawer)
        drawer.get_by_label("归类码", exact=True).click(force=True)
        maker.wait_for_timeout(400)
        options = maker.locator(".ant-select-dropdown:visible .ant-select-item-option").all_inner_texts()
        codes = sorted(re.findall(r"（([A-Z_]+)）", " ".join(options)))
        c.check("AC-V4", f"处置表单按来源对象（账单行）只列可用归类码（{codes}）",
                codes == sorted(["CHANNEL_FEE_OTHER", "CHANNEL_RESERVE_HOLD", "CHANNEL_RESERVE_RELEASE", "RECON_DIFFERENCE"]))
        shots.append(shot(maker, "08-resolution-form-codes"))
        maker.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="CHANNEL_FEE_OTHER").first.click()
        drawer.get_by_label("处置说明").fill("S2 服务费 1.00 归渠道其他费用")
        button(drawer, "提交复核").click()
        c.check("AC-V4", "Maker 提交处置成功（等待复核）", visible(maker.get_by_text("已提交处置，等待复核"), 10000))

        checker = new_page()
        login(checker, people["CHECKER"]["email"], people["CHECKER"]["password"])
        goto(checker, f"/finance/reconciliation/exceptions?exception={exception_id}")
        drawer = checker.locator(".ant-drawer:visible")
        note = drawer.locator(".finance-review-note").inner_text() if visible(drawer.locator(".finance-review-note"), 8000) else ""
        maker_id = people["MAKER"]["accountId"]
        c.check("AC-V4", f"复核页显示提交人、内容与金额（{note.strip()[:80]}）",
                "CHANNEL_FEE_OTHER" in note and "-1.00" in note and maker_id in note)
        c.check("AC-V1", "复核人（无操作权限）看不到提交处置表单", not visible(drawer.get_by_role("button", name=re.compile("提交复核")), 2000))
        shots.append(shot(checker, "09-checker-review"))
        button(drawer, "批准并应用").click()
        checker.locator(".ant-popover:visible").get_by_role("button", name=re.compile(r"^确\s?认$")).click()
        c.check("AC-V4", "Checker 批准并应用", visible(checker.get_by_text("已批准，处置已应用"), 10000))
        reconciled = c.wait_until(lambda: c.recon_sql(f"SELECT status FROM t_channel_statement WHERE id={sid}")[0][0] == "RECONCILED", 30)
        c.check("AC-V4", "批准后账单 A 对平", bool(reconciled))
        goto(checker, "/finance/reconciliation/statements")
        c.check("AC-V1", "复核人看不到「上传渠道账单」按钮（按钮资源按权限显示）", not visible(button(checker, "上传渠道账单"), 2000))

        # ---------- 银行对账与人工多选匹配（AC-V5） ----------
        goto(maker, f"/finance/reconciliation/bank?account={cash_id}&tab=statements")
        button(maker, "上传银行对账单").click()
        ok = upload(maker, files["s2-bank.csv"], f"银行对账单 ENBD-S2-{run} 已导入 1 行")
        c.check("AC-V2", f"银行对账单上传成功：显示编号 ENBD-S2-{run} 与行数、连续性", ok and visible(maker.get_by_text("连续"), 3000))
        shots.append(shot(maker, "10-bank-upload-success"))
        close_drawer(maker)
        line_id = c.recon_sql(f"SELECT id FROM t_bank_statement_line WHERE bank_transaction_ref='ENBD-S2-TXN-{run}'")[0][0]
        time.sleep(3)
        goto(maker, f"/finance/reconciliation/bank?account={cash_id}&tab=lines&line={line_id}")
        drawer = maker.locator(".ant-drawer:visible")
        c.check("AC-V6", "没有建议时显示说明文字", visible(drawer.get_by_text(re.compile("系统没有给出建议")), 8000))
        movements = {r[1]: r[0] for r in c.recon_sql(
            "SELECT m.id, s.channel_batch_ref FROM t_expected_bank_movement m JOIN t_channel_statement s ON s.id=m.source_id "
            f"WHERE s.channel_batch_ref IN ('S2-{run}-A','S2-{run}-B')")}
        rows = drawer.locator(".ant-table-tbody tr")
        for ref in (f"S2-{run}-A", f"S2-{run}-B"):
            row = drawer.locator("tr", has_text=movements[ref])
            row.locator(".ant-checkbox-input").check(force=True)
        a_row, b_row = drawer.locator("tr", has_text=movements[f"S2-{run}-A"]), drawer.locator("tr", has_text=movements[f"S2-{run}-B"])
        a_row.locator("input[inputmode=decimal]").fill("48.70")
        b_row.locator("input[inputmode=decimal]").fill("60.00")
        maker.wait_for_timeout(500)
        totals = drawer.locator(".recon-match-totals").inner_text()
        submit = drawer.get_by_role("button", name=re.compile("确认人工匹配"))
        c.check("AC-V5", f"多选两笔：实时带符号合计与剩余（{totals.replace(chr(10), ' ')}），超出时提交按钮不可用",
                "108.70" in totals and "超出流水金额" in totals and submit.is_disabled())
        shots.append(shot(maker, "11-manual-match-exceeds"))
        b_row.locator("input[inputmode=decimal]").fill("51.30")
        maker.wait_for_timeout(500)
        totals = drawer.locator(".recon-match-totals").inner_text()
        c.check("AC-V5", f"调整到合计 100.00、剩余 0.00 后可提交（{totals.replace(chr(10), ' ')}）",
                "100.00" in totals and "超出流水金额" not in totals and submit.is_enabled())
        shots.append(shot(maker, "12-manual-match-ok"))
        submit.click()
        maker.locator(".ant-popover:visible").get_by_role("button", name=re.compile(r"^确\s?认$")).click()
        c.check("AC-V5", "人工匹配提交成功（2 条）", visible(maker.get_by_text("已确认 2 条匹配"), 10000))
        matched = c.recon_sql(f"SELECT match_status, matched_amount FROM t_bank_statement_line WHERE id={line_id}")[0]
        c.check("AC-V5", f"银行流水已全部匹配（{matched}）", matched[0] == "MATCHED")

        # ---------- 其他页面与空列表（AC-V6）、无权限（AC-V1） ----------
        goto(maker, "/finance/reconciliation/summary")
        shots.append(shot(maker, "13-summary"))
        goto(maker, "/finance/reconciliation/clearing-accounts")
        shots.append(shot(maker, "14-clearing-accounts"))
        goto(maker, "/finance/reconciliation/cash-accounts")
        cash_text = maker.locator(".ant-table-tbody").inner_text()
        c.check("AC-03/AC-V", "银行账户页只显示末四位", "4321" in cash_text)
        shots.append(shot(maker, "15-cash-accounts"))
        goto(maker, "/finance/reconciliation/platform-txns")
        shots.append(shot(maker, "16-platform-txns"))
        maker.get_by_label("外部交易号").fill("NO-SUCH-" + run)
        button(maker, "查询").click()
        maker.wait_for_load_state("networkidle")
        c.check("AC-V6", "空列表有说明文字", visible(maker.locator(".ant-empty-description"), 5000)
                and len(maker.locator(".ant-empty-description").first.inner_text().strip()) > 4)
        shots.append(shot(maker, "17-empty-list"))

        noperm = new_page()
        login(noperm, people["NOPERM"]["email"], people["NOPERM"]["password"])
        finance = noperm.get_by_role("menuitem", name="财务", exact=True)
        has_finance = visible(finance, 5000)
        if has_finance and not visible(noperm.get_by_role("menuitem", name="账务基础资料", exact=True), 2000):
            finance.first.click()
            noperm.wait_for_timeout(500)
        c.check("AC-V1", "没有对账权限的人：财务菜单可见、无「对账」分组与七个入口",
                has_finance and not visible(noperm.get_by_role("menuitem", name="对账", exact=True), 2000)
                and not any(visible(noperm.get_by_role("menuitem", name=n, exact=True), 500) for n in SEVEN))
        shots.append(shot(noperm, "18-noperm-menu"))
        browser.close()

    # App.tsx 第 37 行 Spin tip 弃用警告为 HEAD 既有（与本需求无关），单列不计入
    known = [e for e in errors if "[antd: Spin] `tip` is deprecated" in e]
    relevant = [e for e in errors if "favicon" not in e and "status of 4" not in e and e not in known]
    record["browserKnownConsole"] = len(known)
    c.check("UI", f"控制台无非预期 JS 错误（{len(relevant)} 条；既有 Spin tip 弃用警告 {len(known)} 条单列）", not relevant)
    for e in sorted(set(relevant)):
        print(f"  console（{relevant.count(e)} 次）:", e[:300])
    record["browserConsole"] = sorted(set(relevant))
    record["browserResults"] = c.results
    record["browserShots"] = shots
    record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    passed = sum(1 for r in c.results if r[2])
    print(f"\n{passed}/{len(c.results)} 通过；截图 {OUT}")


if __name__ == "__main__":
    main()
