"""浏览器验收（AC-V01–V04 与三页主要操作）：经运营入口登录，按 Pencil 画板 01–15 逐步操作并截图到 assets/stage5/。

前置：local_acceptance.py 已运行且尚未清理（读取最新 channel-acceptance-*.json：主渠道 ACTIVE + 已配置账号 + ACTIVE MID 与能力组、
已关闭的无接入方渠道、成本方案 v1 ACTIVE / v2 INACTIVE / v3 REJECTED）。本脚本另经页面新建一个无接入方渠道（画板 03 / 05，激活被拒）、
新建方案的 v4（画板 11，保存并提交审批），以管理员打开批准对话框演示编制人自批被拒（画板 14），再以 IAM 合成复核人经接口批准，
回到页面作废 v4（画板 15）。新建的渠道、合成人员与角色追加进运行记录，由 local_acceptance.py --cleanup 一并清理。
不打印 Token 与口令。用法：python3 -I local_browser.py [截图目录，缺省 assets/stage5]
"""
import json
import re
import site
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import local_acceptance as acc  # noqa: E402
from local_acceptance import e2e_http  # noqa: E402

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE.parent / "assets/stage5"
ORIGIN = e2e_http.OPERATIONS
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}", flush=True)
    return bool(ok)


def button(scope, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(re.escape(c) for c in text) + "$"))


def visible(locator, timeout=10000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001 - Playwright 超时即判定失败
        return False


def shot(page, name, full_page=True):
    page.wait_for_timeout(700)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=full_page)


def idle(page):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(400)


def row(scope, text):
    return scope.locator("tr.ant-table-row").filter(has_text=text).first


def dialog(page):
    return page.locator(".ant-modal:visible").last


def goto(page, path):
    page.goto(ORIGIN + path)
    idle(page)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    path, rec = acc.latest_record()
    acc.record, acc.RECORD_PATH = rec, path  # 新建的渠道 / 合成人员追加进同一运行记录，供 --cleanup 清理
    run, code = rec["run"], acc.MAIN_CODE
    ch, acct, mid = rec["channelIds"], rec["accountIds"], rec["midIds"]
    plan = rec["ids"]["costPlans"][0]
    s = e2e_http.secrets()
    errors = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page = context.new_page()
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        idle(page)
        errors.clear()

        # AC-V01 菜单与入口
        menu = page.get_by_role("menuitem", name="渠道", exact=True)
        check("AC-V01", "侧栏出现一级菜单「渠道」", visible(menu))
        menu.first.click()
        subs = [page.get_by_role("menuitem", name=n, exact=True) for n in ("渠道与账号", "MID 与能力", "渠道成本")]
        check("AC-V01", "「渠道」下依次为渠道与账号 / MID 与能力 / 渠道成本", all(visible(x) for x in subs))
        subs[0].first.click()
        idle(page)
        check("AC-V01", "渠道与账号页标题与路由", visible(page.get_by_role("heading", name="渠道与账号")) and page.url.endswith("/channel/channels"))

        # 01 渠道列表
        check("B1", f"列表含 {code}（ACTIVE）", visible(row(page, code)) and visible(row(page, code).locator(".ant-tag").filter(has_text="ACTIVE")))
        check("AC-V04", "ACTIVE 行主动作为「暂停」", visible(row(page, code).get_by_role("button", name="暂停")))
        shot(page, "01-渠道列表")
        page.get_by_label("渠道编码 / 名称").fill(f"ZZNONE{run}")
        button(page, "查询").click()
        idle(page)
        check("AC-V01", "空列表文案「没有符合条件的渠道」", visible(page.get_by_text("没有符合条件的渠道")))
        button(page, "重置").click()
        idle(page)

        # 03 新建渠道：内联校验（AC-V02）后新建无接入方渠道
        button(page, "新建渠道").click()
        modal = dialog(page)
        modal.get_by_label("渠道编码").fill("9bad")
        modal.get_by_label("名称").fill(f"浏览器验收 {run}")
        button(modal, "创建").click()
        check("AC-V02", "非法编码在提交前内联提示", visible(modal.get_by_text("大写字母开头，大写字母、数字或下划线，2–64 位")))
        browser_code = f"BROWSER_{run}_{int(time.time()) % 100000}"
        modal.get_by_label("渠道编码").fill(browser_code)
        shot(page, "03-新建渠道", full_page=False)
        button(modal, "创建").click()
        idle(page)
        detail_url = page.url
        created_id = detail_url.rstrip("/").split("/")[-1]
        if re.fullmatch(r"[1-9][0-9]{0,18}", created_id):
            acc.remember("channels", created_id)
        check("B1", f"创建后进入详情页 DRAFT（{created_id}）", visible(page.locator(".ant-tag").filter(has_text="DRAFT")))
        check("AC-V04", "DRAFT 只显示「激活」「关闭」（无暂停 / 恢复）", visible(button(page, "激活")) and visible(button(page, "关闭"))
              and button(page, "暂停").count() == 0 and button(page, "恢复").count() == 0)

        # 05 渠道激活被拒（Owner detail）
        button(page, "激活").click()
        modal = dialog(page)
        button(modal, "激活").click()
        idle(page)
        check("AC-B01", f"激活被拒，对话框显示「Platform 不存在同编码接入方: {browser_code}」",
              visible(modal.get_by_text(f"Platform 不存在同编码接入方: {browser_code}")))
        shot(page, "05-渠道激活被拒", full_page=False)
        button(modal, "取消").click()

        # 02 渠道详情｜账号；04 账号配置
        goto(page, f"/channel/channels/{ch['main']}")
        heading = page.locator(".page-heading")
        check("AC-V04", "ACTIVE 渠道详情标题区显示「暂停」「关闭」，无「激活」", visible(button(heading, "暂停")) and visible(button(heading, "关闭"))
              and button(heading, "激活").count() == 0)
        account_row = row(page, "PayerMax 主账号")
        suffix = rec.get("vaultTokenSuffix", "")
        check("AC-B02", f"账号行显示已配置与 Token 末 4 位 {suffix}", visible(account_row) and visible(account_row.get_by_text(re.compile(re.escape(suffix)))))
        shot(page, "02-渠道详情-账号")
        account_row.get_by_role("button", name="配置").click()
        modal = dialog(page)
        check("AC-V03", "已配置时显示「已配置」与 Token 末 4 位，提供「重新填写」",
              visible(modal.get_by_text("已配置")) and visible(modal.get_by_text(re.compile("Token 末 4 位 " + re.escape(suffix))))
              and visible(button(modal, "重新填写")))
        button(modal, "重新填写").click()
        page.wait_for_timeout(500)
        check("AC-V03", "重新填写后凭据值为密码型输入", modal.locator('input[type="password"]').count() >= 1)
        check("AC-V03", "页面任何输入框都不回显已保存的值", all(not (i.input_value() or "") for i in modal.locator("input").all()))
        shot(page, "04-账号配置-参数与凭据", full_page=False)
        button(modal, "取消").click()

        # 13 渠道详情｜变更记录
        page.get_by_role("tab", name="变更记录").click()
        idle(page)
        changes = page.locator("tr.ant-table-row").filter(has_text="渠道")
        status_row = changes.filter(has_text="状态变更").filter(has_text="DRAFT")
        check("AC-B06", f"变更记录可见（{changes.count()} 行：创建 / 状态变更 DRAFT → ACTIVE / 修改，含操作者与时间）",
              visible(status_row) and visible(changes.filter(has_text="创建")) and rec["adminAccountId"] in status_row.inner_text().replace("\n", ""))
        shot(page, "13-渠道详情-变更记录")

        # AC-V04 CLOSED 后无操作
        goto(page, f"/channel/channels/{ch['orphan']}")
        check("AC-V04", "CLOSED 渠道无编辑与状态按钮", visible(page.locator(".ant-tag").filter(has_text="CLOSED"))
              and all(button(page, t).count() == 0 for t in ("编辑", "激活", "暂停", "恢复", "关闭")))

        # 06 MID 列表（外部 MID 前缀筛选）
        goto(page, "/channel/mids")
        check("AC-V01", "MID 与能力页标题", visible(page.get_by_role("heading", name="MID 与能力")))
        page.get_by_label("外部 MID").fill("PMX-E2E-")
        button(page, "查询").click()
        idle(page)
        check("B3", "外部 MID 前缀筛选命中本次 MID", visible(row(page, f"PMX-E2E-{run}")))
        shot(page, "06-MID列表")

        # 07 MID 详情｜能力组；08 新建能力组（MCC 内联校验）
        goto(page, f"/channel/mids/{mid['main']}")
        groups = page.locator("tr.ant-table-row")
        check("B4", "MID 详情列出 USD（ENABLED）与 EUR（DISABLED）能力组",
              visible(groups.filter(has_text="USD").filter(has_text="ENABLED")) and visible(groups.filter(has_text="EUR").filter(has_text="DISABLED")))
        shot(page, "07-MID详情-能力组与明细")
        button(page, "新建能力组").click()
        modal = dialog(page)
        modal.get_by_label("支付方式").fill("CARD")
        modal.get_by_label("MCC").fill("59")
        modal.get_by_label("MCC").press("Tab")
        check("AC-V02", "MCC 非四位数字在提交前内联提示", visible(modal.locator(".ant-form-item-explain-error")))
        check("AC-B04", "明细未选能力代码时「保存」禁用并提示未注册", visible(modal.get_by_text(re.compile("未在目录中注册")))
              and button(modal, "保存").is_disabled())
        shot(page, "08-新建能力组", full_page=False)
        button(modal, "取消").click()

        # 09 成本方案列表；10 方案详情｜版本列表
        goto(page, "/channel/cost-plans")
        check("AC-V01", "渠道成本页标题", visible(page.get_by_role("heading", name="渠道成本")))
        check("AC-A01", f"方案列表含 {plan}", visible(row(page, plan)))
        shot(page, "09-渠道成本方案列表")
        goto(page, f"/channel/cost-plans/{plan}")
        versions = page.locator("tr.ant-table-row")
        check("AC-A04", "ACTIVE / INACTIVE / REJECTED 行都没有「作废」", visible(versions) and versions.get_by_role("button", name="作废").count() == 0)
        shot(page, "10-方案详情-版本列表")

        # 12 版本对比（v1 → v2）
        v1, v2 = rec["costVersions"]["v1"], rec["costVersions"]["v2"]
        goto(page, f"/channel/cost-plans/{plan}/versions/{v2}?compareWith={v1}")
        check("AC-A05", "版本对比显示新增与修改标记", visible(page.get_by_text("新增")) and visible(page.get_by_text("修改")))
        shot(page, "12-版本对比")

        # 11 版本编制：v4（生效时间 3 天后），保存并提交审批
        goto(page, f"/channel/cost-plans/{plan}/versions/new")
        check("AC-A02", "版本编制页有添加范围 / 添加组成", visible(page.get_by_text("+ 添加范围")) and visible(page.get_by_text("+ 添加组成")))
        shot(page, "11-版本编制-范围与组成")
        v4 = create_v4_via_editor(page, plan, run)
        check("AC-A02", f"保存并提交审批 → PENDING_APPROVAL（v{v4}）", v4 is not None)

        # 14 批准确认：管理员（编制人）自批 → Owner detail
        goto(page, f"/channel/cost-plans/{plan}")
        if v4:
            row(page, f"v{v4}").get_by_role("button", name="批准").click()
            modal = dialog(page)
            check("A4", "批准对话框展示内容摘要与编制人", visible(modal.get_by_text(re.compile("内容摘要"))))
            button(modal, "批准").click()
            idle(page)
            check("AC-A03", "编制人自批被拒并显示 Owner detail「复核人不能与编制人相同」", visible(modal.get_by_text(re.compile("复核人不能与编制人相同"))))
            shot(page, "14-批准版本确认", full_page=False)
            button(modal, "取消").click()
            approved = approve_with_checker(plan, v4, run)
            check("AC-A03", f"合成复核人经接口批准 v{v4} → APPROVED", approved)
            goto(page, f"/channel/cost-plans/{plan}")
            target = row(page, f"v{v4}")
            check("AC-A04", "未到期 APPROVED 行出现「作废」", visible(target.get_by_role("button", name="作废")))
            target.get_by_role("button", name="作废").click()
            modal = dialog(page)
            button(modal, "确认作废").click()
            check("AC-V02", "作废原因必填（内联提示）", visible(modal.locator(".ant-form-item-explain-error")))
            modal.get_by_label("作废原因").fill(f"浏览器验收作废 {run}")
            shot(page, "15-作废版本确认", full_page=False)
            button(modal, "确认作废").click()
            idle(page)
            check("AC-A04", "作废后 v4 为 INACTIVE 且无「作废」", visible(row(page, f"v{v4}").locator(".ant-tag").filter(has_text="INACTIVE"))
                  and row(page, f"v{v4}").get_by_role("button", name="作废").count() == 0)
        # 预期：本脚本故意触发的 400（渠道激活被拒、编制人自批被拒）；既有 App.tsx 登录检查 Spin `tip` 弃用警告（非本需求）。
        unexpected = [e for e in errors if "status of 400" not in e and "`tip` is deprecated" not in e]
        check("AC-V01", f"页面无意外控制台错误（预期 400 {sum('status of 400' in e for e in errors)} 次，其余 {unexpected[:3]}）", not unexpected)
        browser.close()
    passed = sum(1 for r in results if r[2])
    rec["browser"] = {"passed": passed, "total": len(results), "failed": [f"{r[0]} {r[1]}" for r in results if not r[2]]}
    acc.save()
    print(f"\n浏览器验收：{passed} / {len(results)} 通过；截图 {OUT}")
    return 0 if passed == len(results) else 1


def create_v4_via_editor(page, plan, run):
    """在编制页填生效时间（3 天后）、复用第一个范围默认值并保存并提交审批；返回版本号。"""
    when = (datetime.now() + timedelta(days=3)).replace(second=0, microsecond=0)
    picker = page.locator(".ant-picker input").first
    picker.click()
    picker.fill(when.strftime("%Y-%m-%d %H:%M:%S"))
    page.keyboard.press("Enter")
    page.wait_for_timeout(300)
    fill_if_empty(page, "渠道合同编号", f"PMX-AGR-{run}")
    fill_if_empty(page, "AE", "US")
    fill_if_empty(page, "AED", "USD")
    fill_if_empty(page, "CARD", "CARD")
    fill_if_empty(page, "PAY", "SALE")
    mid_select = page.locator(".ant-select").filter(has=page.locator('[aria-label="MID"]')).first
    if mid_select.count():
        mid_select.click()
        page.locator(".ant-select-dropdown:visible .ant-select-item-option").first.click()
    button(page, "保存并提交审批").click()
    idle(page)
    match = re.search(r"/versions/(\d+)", page.url)
    return match.group(1) if match else None


def fill_if_empty(page, placeholder, value):
    for field in page.get_by_placeholder(placeholder, exact=True).all():
        if not field.input_value():
            field.fill(value)


def approve_with_checker(plan, cost_ver, run):
    admin = acc.login(*(lambda s: (s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]))(e2e_http.secrets()))
    acc.RUN = f"{run}B{int(time.time()) % 100000}"
    checker, _ = acc.synthetic_person(admin, "BCHECKER", [acc.PERM["CONFIG_READ"], acc.PERM["COST_READ"], acc.PERM["COST_APPROVE"]])
    if not checker:
        return False
    _, version = acc.raw(admin, "GET", f"/channel-cost-plans/{plan}/versions/{cost_ver}")
    status, approved = acc.raw(checker, "POST", f"/channel-cost-plans/{plan}/versions/{cost_ver}/approve",
                               {"approvedContentHash": version.get("contentHash")})
    return status == 200 and approved.get("status") == "APPROVED"


if __name__ == "__main__":
    sys.exit(main())
