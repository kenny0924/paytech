"""阶段 5 浏览器验收（结账点运行期评估）：经本机 Nginx 运营入口登录，核对评估查询、执行发布、决策流的结账改动与支付回归。

前置：本机 Risk 已有结账正式评估（ALLOW/DENY/REVIEW）与结账执行发布（ACTIVE + SUPERSEDED 基线，ACTIVE 有已完成影子批次）。
只读：不修改业务数据；不打印凭据、Token、Cookie。失败项截图保存到脚本所在目录（stage5-*.png）。
用法：python3 -I stage5_browser.py
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

OUT = Path(__file__).resolve().parent
ORIGIN = e2e_http.OPERATIONS
CHECKOUT = "CHECKOUT_PREPARE"
DENY_ORDER, DENY_EVAL = "236938708333383680", "236938708564066304"
REVIEW_ORDER = "236938708765396992"
ALLOW_ORDER, ALLOW_EVAL = "236938206673653760", "236938207265046528"
ACTIVE_RELEASE, BASELINE_RELEASE = "236938662812598272", "236937225848242176"
EXPECTED_INPUTS = [("Client", None), ("运营主体", "AE001"), ("主体来源", "PLATFORM"), ("Local Partner", "未提供"),
                   ("消费者国家", "US"), ("币种", "USD"), ("商品金额", None), ("折扣金额", None), ("净额", "1003.9"),
                   ("行业", "RETAIL"), ("MCC", "5999"), ("明细行数", "2")]
results, notes = [], []
STATE = {"page": None, "shots": 0}


def check(name, ok, detail=""):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}{'' if ok or not detail else '：' + detail}")
    if not ok and STATE["page"] is not None:
        STATE["shots"] += 1
        shot(STATE["page"], f"stage5-{STATE['shots']:02d}-failed")


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
        page.mouse.move(1400, 880)
        page.wait_for_timeout(200)
        page.screenshot(path=OUT / f"{name}.png", full_page=True)
    except Exception as failure:  # 截图失败不影响结论
        print(f"截图失败：{type(failure).__name__}")


def choose(page, label, option_text):
    """打开 antd Select 并点选可见选项；返回下拉中的全部选项文字。"""
    page.get_by_label(label).click()
    page.wait_for_timeout(300)
    options = page.locator(".ant-select-dropdown:visible .ant-select-item-option")
    texts = [t.strip() for t in options.all_inner_texts()]
    options.filter(has_text=option_text).first.click()
    page.wait_for_timeout(300)
    return texts


def tab(page, name):
    page.get_by_role("tab", name=re.compile("^" + re.escape(name))).click()
    page.wait_for_timeout(400)


def active_panel(page):
    return page.locator(".evaluation-tabs [role=tabpanel].ant-tabs-content-active")


def open_query(page, kind, value):
    page.goto(f"{ORIGIN}/risk/evaluations?type={kind}&id={value}")
    settle(page)
    page.get_by_role("tab", name=re.compile("^路径")).wait_for(timeout=15000)
    page.wait_for_timeout(300)


def conclusion(page):
    return page.locator(".evaluation-conclusion").inner_text()


def descriptions(locator):
    labels = [t.strip() for t in locator.locator(".ant-descriptions-item-label").all_inner_texts()]
    values = [t.strip() for t in locator.locator(".ant-descriptions-item-content").all_inner_texts()]
    return list(zip(labels, values))


def evaluation_query(page):
    # 1. 下拉含“订单 ID”，按订单 ID 查拒绝订单
    page.goto(ORIGIN + "/risk/evaluations")
    settle(page)
    options = choose(page, "查询类型", "订单 ID")
    page.get_by_role("textbox", name="订单 ID").fill(DENY_ORDER)
    button(page, "查询").click()
    settle(page)
    page.get_by_role("tab", name=re.compile("^路径")).wait_for(timeout=15000)
    card = conclusion(page)
    pairs = dict(descriptions(page.locator(".evaluation-conclusion")))
    ok = ("订单 ID" in options and "type=order" in page.url and f"id={DENY_ORDER}" in page.url
          and "拒绝" in card and CHECKOUT in pairs.get("风控点", "") and pairs.get("订单") == DENY_ORDER
          and "CHECKOUT_STAGE5_HIGH_NET" in pairs.get("拒绝原因", "") and "认证要求" not in card)
    check("1. 按订单 ID 查拒绝：下拉含订单 ID、URL type=order、拒绝/CHECKOUT_PREPARE/订单标签/原因码、无认证要求", ok,
          f"options={options} url含order={'type=order' in page.url} labels={list(pairs)}")
    note(f"结论卡片风控点显示为“{pairs.get('风控点', '—')}”")
    shot(page, "stage5-01-deny-order")

    # 2. 输入分区 12 行
    tab(page, "输入")
    rows = descriptions(active_panel(page))
    expected_labels = [label for label, _ in EXPECTED_INPUTS]
    mismatched = [label for label, value in EXPECTED_INPUTS
                  if value is not None and value not in dict(rows).get(label, "")]
    empty = [label for label, value in EXPECTED_INPUTS if value is None and not dict(rows).get(label)]
    check("2. 输入分区 12 行：字段顺序与取值（Local Partner 未提供、AE001、PLATFORM、US、USD、净额 1003.9、RETAIL、5999、2 行）",
          [label for label, _ in rows] == expected_labels and not mismatched and not empty,
          f"labels={[label for label, _ in rows]} mismatched={mismatched} empty={empty}")

    # 3. 路径与规则
    tab(page, "路径")
    steps = [t.strip() for t in active_panel(page).locator(".evaluation-step-body strong").all_inner_texts()]
    path_ok = steps[:1] == ["开始"] and "拦截" in steps and steps[-1:] == ["拒绝"] and steps.index("拦截") < len(steps) - 1
    tab(page, "规则与条件")
    panel = active_panel(page)
    high = panel.locator(".evaluation-rule", has_text="HIGH_NET")
    country = panel.locator(".evaluation-rule", has_text="COUNTRY_REVIEW")
    high_hit = high.count() > 0 and "is-matched" in (high.first.get_attribute("class") or "")
    country_ok = country.count() == 0 or "is-matched" not in (country.first.get_attribute("class") or "")
    if country.count() == 0:
        note("COUNTRY_REVIEW 规则未出现在规则与条件分区（未求值）")
    check("3. 路径 开始 → 拦截 → 拒绝；HIGH_NET 命中、COUNTRY_REVIEW 未命中或未求值",
          path_ok and high_hit and country_ok, f"steps={steps} high_hit={high_hit} country_ok={country_ok}")
    shot(page, "stage5-02-deny-rules")
    shadows = {"DENY": shadow_header(page)}

    # 4. 审核订单与放行评估
    open_query(page, "order", REVIEW_ORDER)
    review_ok = "审核" in conclusion(page) and dict(descriptions(page.locator(".evaluation-conclusion"))).get("订单") == REVIEW_ORDER
    shadows["REVIEW"] = shadow_header(page)
    open_query(page, "evaluation", ALLOW_EVAL)
    allow_card = conclusion(page)
    allow_ok = "放行" in allow_card and ALLOW_EVAL in allow_card and "认证要求" not in allow_card
    shadows["ALLOW"] = shadow_header(page)
    check("4. 按订单 ID 查审核订单显示审核；按评估 ID 查放行评估显示放行", review_ok and allow_ok,
          f"review={review_ok} allow={allow_ok}")

    # 5. 影子评估列
    with_rows = {k: v for k, v in shadows.items() if v[0] > 0}
    if not with_rows:
        note("三笔结账正式评估均无影子行，仅核对表头")
    headers_ok = all(v[1] and "结论变化" in v[1] and "认证要求变化" not in v[1] for v in shadows.values())
    check("5. 结账评估影子分区表头不含“认证要求变化”", headers_ok,
          f"{ {k: v for k, v in shadows.items()} }")
    note("影子行数：" + "，".join(f"{k}={v[0]}" for k, v in shadows.items()))


def shadow_header(page):
    tab(page, "影子评估")
    panel = active_panel(page)
    headers = [t.strip() for t in panel.locator("thead th").all_inner_texts()]
    rows = panel.locator("tbody tr.ant-table-row").count()
    if rows:
        shot(page, "stage5-03-shadows")
    return rows, headers


def execution_releases(page):
    page.goto(ORIGIN + "/risk/execution-releases")
    settle(page)
    options = choose(page, "风控点", "结账准备")
    settle(page)
    body = page.locator(".execution-page").inner_text()
    rows = page.locator(".execution-page tbody tr.ant-table-row")
    row_count = rows.count()
    statuses = [t.strip() for t in rows.all_inner_texts()]
    page.locator(".execution-active").get_by_role("button", name=re.compile("查看详情")).click()
    settle(page)
    detail = page.locator(".execution-page").inner_text()
    url_ok = ACTIVE_RELEASE in page.url and f"pointCode={CHECKOUT}" in page.url
    metrics = [t.strip() for t in page.locator(".execution-metric > span").all_inner_texts()]
    ok = (any("结账准备" in o for o in options) and row_count == 2 and "暂不支持执行发布" not in body and url_ok
          and "已完成" in detail and "拒绝 → 放行" in metrics and "认证要求变化" not in detail
          and "2 / " in detail)
    check("6. 执行发布：可选结账准备、2 个发布、ACTIVE 详情有影子指标且无“认证要求变化”、无“暂不支持执行发布”", ok,
          f"options={options} rows={row_count} url_ok={url_ok} metrics={metrics}")
    note(f"执行发布风控点选项文案：{options}；列表行：{[s.replace(chr(10), ' ')[:60] for s in statuses]}")
    shot(page, "stage5-04-checkout-release")
    # 8 的一半：切回支付尝试
    page.goto(ORIGIN + "/risk/execution-releases")
    settle(page)
    choose(page, "风控点", "支付尝试")
    settle(page)
    active = page.locator(".execution-active")
    if active.count() == 0:
        note("支付尝试当前没有生效发布，跳过“认证要求变化”指标核对")
        return None
    active.get_by_role("button", name=re.compile("查看详情")).click()
    settle(page)
    detail = page.locator(".execution-page").inner_text()
    has_run = page.locator(".execution-metric").count() > 0
    if not has_run:
        note("支付生效发布没有影子批次（首发），详情不显示指标；“认证要求变化”无法在该发布上核对")
        return None
    return "认证要求变化" in detail


def decision_flows(page):
    page.goto(ORIGIN + "/risk/decision-flows")
    settle(page)
    choose(page, "决策点", "结账准备")
    settle(page)
    links = page.locator(".flow-name-link")
    if links.count() == 0:
        check("7. 决策流结账点提示为执行发布后生效", False, "结账点没有决策流")
        return
    links.first.click()
    settle(page)
    hint = page.locator(".flow-workspace-actions").inner_text()
    check("7. 决策流结账点提示“保存不会立即生效，需在【执行发布】中发布并激活后对该风控点的评估生效。”",
          "保存不会立即生效，需在【执行发布】中发布并激活后对该风控点的评估生效。" in hint and "本期仅" not in hint, hint[:120])
    shot(page, "stage5-05-flow-hint")


def scenarios(page):
    evaluation_query(page)
    payment_auth = execution_releases(page)
    decision_flows(page)
    page.goto(ORIGIN + "/risk/evaluations")
    settle(page)
    options = choose(page, "查询类型", "支付尝试 ID")
    check("8. 支付回归：执行发布支付详情仍有“认证要求变化”（若有影子批次）；评估查询仍有“支付尝试 ID”",
          payment_auth is not False and "支付尝试 ID" in options, f"payment_auth={payment_auth} options={options}")


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
        try:
            scenarios(page)
        except Exception as failure:  # 记录失败并截图，继续汇总
            check(f"场景执行异常：{type(failure).__name__}: {str(failure).splitlines()[0][:160]}", False)
        STATE["page"] = None
        browser.close()
    unexpected = [f for f in failed if not f.startswith("404 GET") or "/risk-decisions/v1/evaluations" not in f]
    check("9. 浏览器控制台无报错（404 资源错误除外）且除预期 404 外没有 HTTP ≥400",
          not [e for e in errors if "404" not in e] and not unexpected,
          f"console={errors[:5]} http={failed[:10]}")
    print("控制台错误：", errors[:5])
    print("HTTP ≥400：", failed[:10])
    print(f"{sum(ok for _, ok in results)}/{len(results)} 通过")
    sys.exit(0 if all(ok for _, ok in results) else 1)


if __name__ == "__main__":
    main()
