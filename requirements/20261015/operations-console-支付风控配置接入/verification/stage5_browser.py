"""阶段 5 浏览器验收：经本机 Nginx 运营入口登录，按 Pencil 13 张画板逐页操作并截图。

前置：已运行 stage5_api.py（读取其 .state.json：名单、决策流、回滚发布）。只连接本机。
截图写入第一个参数指定的目录（文件名与 assets/stage3 画板一致）；不打印凭据、Token 与主体原值。
页面上的写操作：新建名单、加入成员、新建两个变量、对回滚发布发起影子运行并激活（即完成一次回滚）。
用法：python3 -I stage5_browser.py <截图目录> <stage5_api 证据文件>
"""
import json
import re
import site
import sys
import time
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HELPER = Path(__file__).resolve().parents[3] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

OUT = Path(sys.argv[1])
STATE = json.loads(Path(sys.argv[2] + ".state.json").read_text(encoding="utf-8"))
ORIGIN = e2e_http.OPERATIONS
SFX = str(int(time.time()))[-6:]
RAW = f"fp_s5_ui_{SFX}"
results = []


def check(name, ok):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}")


def button(page, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(re.escape(c) for c in text) + "$"))


def pick(page, select, option):
    select.click()
    page.locator(".ant-select-dropdown:visible .ant-select-item-option", has_text=option).first.click()
    page.wait_for_timeout(300)


def item_select(page, label):
    """按 Form.Item 标签定位其中的 Select。"""
    return page.locator(".ant-form-item", has=page.locator(f"label:text-is('{label}')")).locator(".ant-select").first


def item_input(page, label):
    return page.locator(".ant-form-item", has=page.locator(f"label:text-is('{label}')")).locator("input").first


def shot(page, name):
    page.mouse.move(1400, 880)
    page.wait_for_timeout(300)
    page.screenshot(path=OUT / f"{name}.png", full_page=True)


def settle(page):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(400)


def step(name, fn, page):
    try:
        fn(page)
    except Exception as failure:  # 单步失败不阻断后续页面；截图留证。
        check(f"{name}（异常：{type(failure).__name__}: {str(failure)[:160]}）", False)
        page.screenshot(path=OUT / f"error-{name}.png", full_page=True)
        page.keyboard.press("Escape")


def rule_sets(page):
    page.goto(ORIGIN + "/risk/rule-configuration")
    settle(page)
    pick(page, page.get_by_label("风控点", exact=True), re.compile("Payment|支付"))
    settle(page)
    check("01 规则集页切换到支付点后列出验收规则集", page.get_by_text(re.compile("S5_(BLOCK|AUTH)")).count() > 0)
    shot(page, "01-规则集列表-风控点切换")


def flow(page):
    page.goto(ORIGIN + f"/risk/decision-flows?view=detail&flowId={STATE['flow']['flowId']}")
    settle(page)
    check("02 画布上“放行（需要 3DS）”节点显示 3DS 标签",
          page.locator(".react-flow__node", has_text="放行（需要 3DS）").get_by_text("3DS", exact=True).count() > 0)
    page.locator(".react-flow__node", has_text="放行（需要 3DS）").first.click()
    page.wait_for_timeout(500)
    check("02 属性面板显示认证要求“需要 3DS”", page.get_by_text("认证要求").count() > 0 and page.get_by_text("需要 3DS").count() > 0)
    shot(page, "02-决策流-放行结束节点认证要求")


def velocity_variable(page):
    page.goto(ORIGIN + "/risk/variables?view=create")
    settle(page)
    pick(page, item_select(page, "来源类型"), "实时累计")
    item_input(page, "变量编码").fill(f"S5_UI_VELOCITY_{SFX}")
    item_input(page, "变量名称").fill("同卡 1 小时评估次数（页面）")
    check("03 实时累计表单显示口径预览", page.get_by_text(re.compile("^口径预览：")).count() > 0)
    shot(page, "03-变量-实时累计新建")
    button(page, "创建变量").click()
    settle(page)
    check("03 实时累计变量经页面创建成功", page.get_by_text("统计参数以创建时为准").count() > 0 or "code=" in page.url)


def list_variable(page):
    page.goto(ORIGIN + "/risk/variables?view=create")
    settle(page)
    pick(page, item_select(page, "来源类型"), "名单命中")
    item_input(page, "变量编码").fill(f"S5_UI_LISTED_{SFX}")
    item_input(page, "变量名称").fill("卡指纹在验收黑名单（页面）")
    pick(page, page.get_by_label("名单", exact=True), STATE["list"])
    check("04 名单命中表单显示口径预览", page.get_by_text(re.compile("^口径预览：评估时本次")).count() > 0)
    shot(page, "04-变量-名单命中新建")
    button(page, "创建变量").click()
    settle(page)
    check("04 名单命中变量经页面创建成功", "code=" in page.url)


def lists(page):
    page.goto(ORIGIN + "/risk/lists")
    settle(page)
    check("05 名单目录列出验收名单", page.get_by_text(STATE["list"]).count() > 0)
    shot(page, "05-风控名单列表")
    button(page, "＋ 新建名单").click()
    page.get_by_label("名单编码").fill(f"S5_UI_IP_{SFX}")
    page.get_by_label("名单名称").fill("页面新建 IP 黑名单")
    pick(page, page.get_by_label("主体类型"), "IP")
    shot(page, "05b-新建名单")
    page.locator(".ant-modal").get_by_role("button", name=re.compile("创\\s?建")).click()
    settle(page)
    check("05b 页面新建名单出现在目录中", page.get_by_text(f"S5_UI_IP_{SFX}").count() > 0)


def members(page):
    page.goto(ORIGIN + f"/risk/lists?listCode={STATE['list']}")
    settle(page)
    shot(page, "06-名单成员")
    button(page, "＋ 加入成员").click()
    page.get_by_label("名单主体值").fill(RAW)
    page.get_by_label("加入原因").fill("页面验收加入")
    page.evaluate("v => { for (const i of document.querySelectorAll('input')) if (i.value === v) i.setAttribute('data-raw','1'); }", RAW)
    page.locator("[data-raw='1']").evaluate("i => i.type = 'password'")  # 截图中遮住原值
    shot(page, "07-加入成员")
    page.locator(".ant-modal").get_by_role("button", name=re.compile("加\\s?入")).click()
    settle(page)
    check("07 加入后页面不显示原值", RAW not in page.content())
    page.get_by_label("主体值", exact=True).fill(RAW)
    button(page, "查询").click()
    settle(page)
    check("06 按原值查询：地址栏不含原值", RAW not in page.url)
    check("06 按原值查询：只剩 1 条且显示“已按主体值筛选”", page.locator(".ant-table-row").count() == 1
          and page.get_by_text("已按主体值筛选").count() > 0)
    check("06 查询后输入框已清空", page.get_by_label("主体值", exact=True).input_value() == "")
    check("06 页面全文不含原值", RAW not in page.content())


def releases(page):
    page.goto(ORIGIN + "/risk/execution-releases")
    settle(page)
    check("08 列表显示生效、已替代、影子中三个发布",
          all(page.get_by_text(t).count() > 0 for t in ["生效中", "已替代"]) and page.get_by_text(re.compile("影子")).count() > 0)
    check("08 已替代的发布提供“以此版本新建”", button(page, "以此版本新建").count() > 0)
    shot(page, "08-执行发布列表")
    button(page, "＋ 新建发布").click()
    page.wait_for_timeout(600)
    shot(page, "09-新建执行发布")
    page.locator(".ant-modal").get_by_role("button", name=re.compile("取\\s?消")).click()


def rollback(page):
    page.goto(ORIGIN + f"/risk/execution-releases?pointCode=PAYMENT_ATTEMPT_RISK&releaseId={STATE['rollback']}")
    settle(page)
    page.get_by_label("影子样本数").fill("20")
    button(page, "发起影子运行").click()
    page.wait_for_timeout(700)
    shot(page, "10a-执行发布详情-影子运行中")
    page.get_by_text("已完成").first.wait_for(timeout=60000)
    settle(page)
    check("10 影子运行完成（页面轮询刷新）", page.get_by_text("已完成").count() > 0)
    shot(page, "10-执行发布详情-影子运行完成")
    button(page, "激活").click()
    page.wait_for_timeout(500)
    modal = page.locator(".ant-modal:visible")
    confirm = modal.get_by_role("button", name=re.compile("确认激活"))
    check("11 激活确认醒目展示认证要求变化", modal.get_by_text("认证要求变化").count() > 0)
    check("11 有差异时未勾选不能激活", confirm.is_disabled())
    shot(page, "11-激活确认")
    modal.get_by_role("checkbox").check()
    check("11 勾选后可以激活", confirm.is_enabled())
    confirm.click()
    settle(page)
    page.get_by_text("生效中").first.wait_for(timeout=15000)
    check("11 回滚发布激活成功（生效中）", page.get_by_text("生效中").count() > 0)
    shot(page, "12-回滚发布已生效")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        # antd 弃用警告来自既有 App.tsx / Accounts.tsx，不属于本需求；其余控制台错误都计入。
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" and not m.text.startswith("Warning: [antd:") else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        settle(page)
        page.get_by_role("menuitem", name="风控管理").first.click()
        for item in ["风控名单", "执行发布", "决策流", "变量管理", "规则配置"]:
            check(f"菜单含“{item}”", page.get_by_role("menuitem", name=item).count() > 0)
        for name, fn in [("规则集", rule_sets), ("决策流", flow), ("实时累计变量", velocity_variable), ("名单命中变量", list_variable),
                         ("名单", lists), ("名单成员", members), ("执行发布列表", releases), ("回滚", rollback)]:
            step(name, fn, page)
        browser.close()
    check("浏览器控制台无报错", not errors)
    print("控制台错误：", errors[:5])
    print("HTTP ≥400：", failed[:10])
    print(f"{sum(ok for _, ok in results)}/{len(results)} 通过")
    sys.exit(0 if all(ok for _, ok in results) else 1)


if __name__ == "__main__":
    main()
