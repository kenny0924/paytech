"""阶段 7 mor-test 浏览器验收（结账点实时累计）：经 test 运营入口（E2E_TARGET=test）登录，核对变量页按风控点目录选择累计与名单参数、评估查询显示累计取值。

前置：test Risk、Ops BFF、Web 为本需求镜像；mor_risk 已有 STAGE7V_ 名单与变量、合成结账 REVIEW 评估（订单 REVIEW_ORDER）。
另核对随本次上线的上一需求 Web（87fd57f）：执行发布页可选结账准备、决策流结账点提示。
写入：场景 6 经页面创建 1 个结账累计变量（STAGE7V_WEB_OE_COUNT_1H），由 release/test-acceptance-cleanup.sql 删除；其余只读。
不打印凭据、Token、Cookie。失败项截图保存到脚本所在目录（第一个参数目录，stage7v-*.png）。
用法：E2E_TARGET=test python3 -I test_browser.py <截图目录>
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
ORIGIN = e2e_http.OPERATIONS
REVIEW_ORDER = "991017100000000004"
WEB_VARIABLE = "STAGE7V_WEB_OE_COUNT_1H"
results, notes = [], []
STATE = {"page": None, "shots": 0}


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}{'' if ok or not detail else '：' + detail}")
    if not ok and STATE["page"] is not None:
        STATE["shots"] += 1
        shot(STATE["page"], f"stage7v-{STATE['shots']:02d}-failed")


def note(text):
    notes.append(text)
    print(f"[说明] {text}")


def button(page, text):
    """antd 会在两个汉字的按钮文字中间插入空格，按容忍空格的正则匹配。"""
    return page.get_by_role("button", name=re.compile("^" + r"\s?".join(re.escape(c) for c in text) + "$"))


def settle(page):
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(500)


def shot(page, name):
    try:
        page.screenshot(path=OUT / f"{name}.png", full_page=True)
    except Exception as failure:  # 截图失败不影响结论
        print(f"截图失败：{type(failure).__name__}")


def item(page, label):
    """表单项（按标签文字精确匹配）。"""
    return page.locator(".ant-form-item").filter(
        has=page.locator(".ant-form-item-label label", has_text=re.compile("^" + re.escape(label) + "$"))).first


def open_select(page, label):
    item(page, label).locator(".ant-select").first.click()
    page.wait_for_timeout(300)
    options = page.locator(".ant-select-dropdown:visible .ant-select-item-option")
    return options, [t.strip() for t in options.all_inner_texts()]


def choose(page, label, option_text):
    options, texts = open_select(page, label)
    options.filter(has_text=option_text).first.click()
    page.wait_for_timeout(300)
    return texts


def options_of(page, label):
    _, texts = open_select(page, label)
    page.keyboard.press("Escape")
    page.wait_for_timeout(200)
    return texts


def radios(page, label):
    group = item(page, label)
    return [t.strip() for t in group.locator(".ant-radio-button-wrapper").all_inner_texts()]


def radio(page, label, text):
    item(page, label).locator(".ant-radio-button-wrapper", has_text=text).first.click()
    page.wait_for_timeout(300)


def new_variable(page, source_label):
    page.goto(ORIGIN + "/risk/variables?view=create")
    settle(page)
    choose(page, "来源类型", source_label)


def velocity_page(page):
    # 1. 风控点下拉只列有目录的点，显示中文名称
    new_variable(page, "实时累计")
    points = options_of(page, "适用决策点")
    check("1. 实时累计：适用决策点只列结账准备与支付尝试（中文名称）", sorted(points) == sorted(["结账准备", "支付尝试"]),
          f"points={points}")

    # 2. 结账准备：事件、主体、统计方式、窗口、币种
    choose(page, "适用决策点", "结账准备")
    events = options_of(page, "统计事件")
    anchors = options_of(page, "统计主体")
    metrics = radios(page, "统计方式")
    hint = item(page, "统计方式").inner_text()
    check("2a. 结账：统计事件只有结账评估、结账被拦截", events == ["结账评估", "结账被拦截（审核或拒绝）"], f"events={events}")
    check("2b. 结账：统计主体只有 Client、运营主体", anchors == ["Client", "运营主体"], f"anchors={anchors}")
    check("2c. 结账 Client：无“不同主体数”，有限制提示且币种为 INPUT_CURRENCY",
          "不同主体数" not in metrics and "次数" in metrics and "INPUT_CURRENCY" in hint and "受限" in hint,
          f"metrics={metrics}")
    radio(page, "统计方式", "金额合计")
    windows = radios(page, "时间窗口")
    check("2d. 结账 Client 金额合计：窗口只有 10 分钟、1 小时", windows == ["10 分钟", "1 小时"], f"windows={windows}")
    radio(page, "统计方式", "次数")
    windows = radios(page, "时间窗口")
    check("2e. 结账 Client 次数：窗口无 7 天", "7 天" not in windows and "1 天" in windows, f"windows={windows}")

    # 3. 支付尝试：行为不变（卡指纹主体、7 天窗口、不同主体数）
    choose(page, "适用决策点", "支付尝试")
    events = options_of(page, "统计事件")
    anchors = options_of(page, "统计主体")
    choose(page, "统计主体", "卡指纹")
    metrics = radios(page, "统计方式")
    windows = radios(page, "时间窗口")
    check("3. 支付尝试：支付事件、五种主体含卡指纹；卡指纹有不同主体数与 7 天窗口",
          "支付尝试评估" in events and "卡指纹" in anchors and len(anchors) == 5 and "不同主体数" in metrics and "7 天" in windows,
          f"events={events} anchors={anchors} metrics={metrics} windows={windows}")


def list_page(page):
    # 4. 名单命中：结账点只列 Client/运营主体名单
    new_variable(page, "名单命中")
    choose(page, "适用决策点", "结账准备")
    page.wait_for_timeout(800)
    lists = options_of(page, "名单")
    check("4. 名单命中（结账）：名单下拉含 STAGE7V Client 与运营主体名单，不含卡指纹名单",
          any("STAGE7V_CLIENT_BLOCK" in t for t in lists) and any("STAGE7V_OE_BLOCK" in t for t in lists)
          and not any("CARD" in t for t in lists), f"lists={lists}")


def create_via_page(page):
    # 6. 经页面创建结账累计变量：运营主体 1 小时次数
    new_variable(page, "实时累计")
    item(page, "变量编码").locator("input").fill(WEB_VARIABLE)
    item(page, "变量名称").locator("input").fill("阶段7 页面创建：运营主体 1 小时结账评估次数")
    choose(page, "适用决策点", "结账准备")
    choose(page, "统计主体", "运营主体")
    radio(page, "统计方式", "次数")
    radio(page, "时间窗口", "1 小时")
    preview = page.locator(".feature-variable-fields .ant-alert").last.inner_text()
    button(page, "创建变量").click()
    settle(page)
    page.wait_for_timeout(800)
    created = "view=edit" in page.url and WEB_VARIABLE in page.url
    detail_point = item(page, "适用决策点").inner_text() if created else ""
    check("6. 页面创建结账累计变量成功，详情显示结账准备", created and "结账准备" in detail_point,
          f"url={page.url.split('?')[-1]} preview={preview[:80]}")
    note(f"口径预览：{preview.strip()[:120]}")


def evaluation_values(page):
    # 5. 评估查询：按订单 ID 查 REVIEW 结账，变量分区显示累计取值
    page.goto(f"{ORIGIN}/risk/evaluations?type=order&id={REVIEW_ORDER}")
    settle(page)
    page.get_by_role("tab", name=re.compile("^路径")).wait_for(timeout=15000)
    card = page.locator(".evaluation-conclusion").inner_text()
    page.get_by_role("tab", name=re.compile("^变量")).click()
    page.wait_for_timeout(500)
    panel = page.locator(".evaluation-tabs [role=tabpanel].ant-tabs-content-active").inner_text()
    ok = ("审核" in card and "STAGE7V_CLIENT_COUNT_10M" in panel and "171.6" in panel
          and "STAGE7V_CLIENT_BLOCKED" in panel)
    check("5. 评估查询（订单 ID）：结论审核；变量分区显示 Client 10 分钟次数、1 小时净额合计 171.6、名单命中", ok,
          f"card含审核={'审核' in card} panel片段={panel[:300]!r}")


def execution_releases(page):
    # 8. 87fd57f：执行发布页可选结账准备，生效发布详情无“认证要求变化”
    page.goto(ORIGIN + "/risk/execution-releases")
    settle(page)
    page.get_by_label("风控点").click()
    page.wait_for_timeout(300)
    options = page.locator(".ant-select-dropdown:visible .ant-select-item-option")
    texts = [t.strip() for t in options.all_inner_texts()]
    options.filter(has_text="结账准备").first.click()
    settle(page)
    body = page.locator(".execution-page").inner_text()
    active = page.locator(".execution-active")
    has_active = active.count() > 0
    detail = ""
    if has_active:
        active.get_by_role("button", name=re.compile("查看详情")).click()
        settle(page)
        detail = page.locator(".execution-page").inner_text()
    check("8. 执行发布（87fd57f）：可选结账准备、有生效发布、无“暂不支持执行发布”、详情无“认证要求变化”",
          any("结账准备" in t for t in texts) and has_active and "暂不支持执行发布" not in body and "认证要求变化" not in detail,
          f"options={texts} active={has_active}")


def decision_flows(page):
    # 9. 87fd57f：决策流结账点提示执行发布后生效
    page.goto(ORIGIN + "/risk/decision-flows")
    settle(page)
    page.get_by_label("决策点").click()
    page.wait_for_timeout(300)
    page.locator(".ant-select-dropdown:visible .ant-select-item-option").filter(has_text="结账准备").first.click()
    settle(page)
    links = page.locator(".flow-name-link")
    if links.count() == 0:
        check("9. 决策流结账点提示为执行发布后生效", False, "结账点没有决策流")
        return
    links.first.click()
    settle(page)
    hint = page.locator(".flow-workspace-actions").inner_text()
    check("9. 决策流（87fd57f）结账点提示“保存不会立即生效，需在【执行发布】中发布并激活后对该风控点的评估生效。”",
          "保存不会立即生效，需在【执行发布】中发布并激活后对该风控点的评估生效。" in hint, hint[:120])


def scenarios(page):
    velocity_page(page)
    list_page(page)
    evaluation_values(page)
    create_via_page(page)


def main():
    s = e2e_http.secrets()
    errors, failed = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
        page.on("console", lambda m: errors.append(m.text[:200]) if m.type == "error" and not m.text.startswith("Warning: [antd:") else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)
        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        settle(page)
        STATE["page"] = page
        for step in (velocity_page, list_page, evaluation_values, create_via_page, execution_releases, decision_flows):
            try:
                step(page)
            except Exception as failure:  # 记录失败并截图，继续下一个场景
                check(f"{step.__name__} 执行异常：{type(failure).__name__}: {str(failure).splitlines()[0][:160]}", False)
        STATE["page"] = None
        browser.close()
    check("10. 浏览器控制台无报错且没有 HTTP ≥400", not errors and not failed, f"console={errors[:5]} http={failed[:10]}")
    print(f"{sum(ok for _, ok in results)}/{len(results)} 通过")
    sys.exit(0 if all(ok for _, ok in results) else 1)


if __name__ == "__main__":
    main()
