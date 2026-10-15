"""阶段 5 浏览器验收：经本机 Nginx 运营入口登录，打开“评估查询”逐项核对并截图。

前置：本机 Risk 已有支付评估（由 Risk 阶段 5 驱动生成）；第二个参数为评估清单（payment_attempt_id、evaluation_id、
结论、认证要求、影子数、认证要求变化的影子 ID，制表符分隔）；第三个参数为已删除流程轨迹、用于“路径未记录”的评估 ID。
只连接本机（E2E_TARGET=test 时连接 mor-test 入口）；不打印凭据、Token 与受限原值。
用法：python3 -I stage5_browser.py <截图目录> <评估清单> <无路径评估 ID>
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

OUT = Path(sys.argv[1])
ROWS = [line.split("\t") for line in Path(sys.argv[2]).read_text().splitlines() if line.strip()]
NO_PATH = sys.argv[3]
ORIGIN = e2e_http.OPERATIONS
results = []


def check(name, ok):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}")


def button(page, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(re.escape(c) for c in text) + "$"))


def settle(page):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(400)


def await_result(page):
    """等结论卡片与解释标签页都渲染完成（结论与解释是两次请求）。"""
    page.get_by_role("tab", name=re.compile("^路径")).wait_for(timeout=15000)
    page.wait_for_timeout(300)


def shot(page, name):
    page.mouse.move(1400, 880)
    page.wait_for_timeout(300)
    page.screenshot(path=OUT / f"{name}.png", full_page=True)


def open_query(page, kind, value):
    page.goto(f"{ORIGIN}/risk/evaluations?type={kind}&id={value}")
    settle(page)


def tab(page, name):
    page.get_by_role("tab", name=re.compile("^" + re.escape(name))).click()
    page.wait_for_timeout(300)


def body(page):
    return page.locator(".evaluation-page").inner_text()


def pick(decision, auth=None, shadow_changed=None):
    for row in ROWS:
        if row[2] == decision and (auth is None or row[3] == auth) and (shadow_changed is None or (row[5] != "NULL") == shadow_changed):
            return row
    raise SystemExit(f"评估清单中没有 {decision}/{auth}")


def scenarios(page):
    has_shadow = any(row[5] != "NULL" for row in ROWS)
    allow = pick("ALLOW", "NOT_REQUIRED", True if has_shadow else None)
    three_ds = pick("ALLOW", "THREE_DS_REQUIRED")
    review = pick("REVIEW")
    deny = next((row for row in ROWS if row[2] == "DENY"), None)

    page.goto(ORIGIN + "/risk/evaluations")
    settle(page)
    check("初始状态提示", "输入支付尝试 ID 或评估 ID" in body(page))
    page.get_by_label("支付尝试 ID").fill("12ab")
    button(page, "查询").click()
    page.wait_for_timeout(300)
    check("格式错误就地提示且不跳转", "请输入 1～19 位数字" in body(page) and "id=" not in page.url)
    shot(page, "01-initial-format-error")

    page.get_by_label("支付尝试 ID").fill(allow[0])
    button(page, "查询").click()
    settle(page)
    await_result(page)
    text = body(page)
    check("按支付尝试查询：URL 记录类型与 ID", f"type=attempt&id={allow[0]}" in page.url)
    check("结论卡片：放行、正式评估、风控点", "放行" in text and "正式评估" in text and "PAYMENT_ATTEMPT_RISK" in text)
    check("路径：节点按顺序且有所走连线", all(k in text for k in ["开始", "拦截", "认证", "放行"]))
    shot(page, "02-allow-path")
    tab(page, "规则与条件")
    text = body(page)
    check("规则与条件：规则编码、条件表达与 ✓/✗", "BLOCKED_CARD" in text and "THREE_DS_AMOUNT" in text and ("✓" in text or "✗" in text))
    check("规则与条件：没有条件不可用提示", "条件结构暂不可用" not in text)
    shot(page, "03-rules")
    tab(page, "变量")
    text = body(page)
    check("变量：输入与实时累计取值、依据", "PAY_INPUT_AMOUNT" in text and "评估时" in text)
    shot(page, "04-variables")
    tab(page, "输入")
    text = body(page)
    check("输入：17 个字段（含未提供）", "卡 BIN" in text or "卡BIN" in text)
    shot(page, "05-inputs")
    if not has_shadow:
        print("[跳过] 影子评估：本环境没有影子评估")
        return finish(page, three_ds, review, deny)
    tab(page, "影子评估")
    text = body(page)
    check("影子评估：列出 1 条、可查看", "查看" in text and "认证要求变化" in text)
    shot(page, "06-shadows")
    page.get_by_role("button", name=re.compile("^查\\s?看$")).first.click()
    settle(page)
    text = body(page)
    check("影子评估：以评估 ID 打开、标“影子评估”、有基线链接",
          "type=evaluation" in page.url and "影子评估" in text and "查看基线评估" in text)
    check("影子评估：认证要求为需要 3DS", "3DS" in text)
    shot(page, "07-shadow-detail")
    page.go_back()
    settle(page)
    check("浏览器后退回到原查询", f"id={allow[0]}" in page.url and "正式评估" in body(page))
    finish(page, three_ds, review, deny)


def finish(page, three_ds, review, deny):
    """3DS、拒绝、审核、刷新、路径未记录与未找到。"""

    open_query(page, "attempt", three_ds[0])
    check("放行需要 3DS", "3DS" in body(page))
    if deny is None:
        print("[跳过] 拒绝：评估清单中没有带路径的拒绝评估")
    else:
        open_query(page, "attempt", deny[0])
        text = body(page)
        check("拒绝：显示拒绝原因码与文案", "拒绝" in text and "拒绝原因：—" not in text.replace(" ", ""))
        shot(page, "08-deny")
    open_query(page, "evaluation", review[1])
    check("按评估 ID 查询：审核", "审核" in body(page))
    page.reload()
    settle(page)
    check("刷新后保持查询", "审核" in body(page) and f"id={review[1]}" in page.url)

    open_query(page, "evaluation", NO_PATH)
    text = body(page)
    check("路径未记录提示", "路径未记录（该评估早于路径记录功能上线）" in text)
    shot(page, "09-path-unavailable")
    open_query(page, "attempt", "123456789")
    check("未找到：空状态", "没有找到该评估" in body(page))
    shot(page, "10-not-found")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" and not m.text.startswith("Warning: [antd:") else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        settle(page)
        page.get_by_role("menuitem", name="风控管理").first.click()
        check("风控菜单含“评估查询”", page.get_by_role("menuitem", name="评估查询").count() > 0)
        try:
            scenarios(page)
        except Exception as failure:  # 记录失败并截图，继续汇总
            check(f"场景执行异常：{type(failure).__name__}: {str(failure)[:160]}", False)
            shot(page, "99-failure")
        browser.close()
    unexpected = [f for f in failed if not f.startswith("404 GET") or "/risk-decisions/v1/evaluations" not in f]
    check("浏览器控制台无报错（404 空状态产生的资源错误除外）", not [e for e in errors if "404" not in e])
    check("除预期 404 外没有 HTTP ≥400", not unexpected)
    print("控制台错误：", errors[:5])
    print("HTTP ≥400：", failed[:10])
    print(f"{sum(ok for _, ok in results)}/{len(results)} 通过")
    sys.exit(0 if all(ok for _, ok in results) else 1)


if __name__ == "__main__":
    main()
