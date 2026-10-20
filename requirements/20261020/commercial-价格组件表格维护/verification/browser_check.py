"""价格组件表格维护：浏览器核对 AC-01—AC-10。本地 Vite（worktree 代码）+ mor-test 数据，在验收用 DRAFT 版本上打开编辑抽屉。
保存请求（PUT …/prices）一律被 Playwright 截下：AC-01 比对请求体后中止，AC-07 返回模拟的 Owner 错误；不写入任何价格。不打印 Token 与口令。
本地 Vite 的 Origin/Host 不在 mor-test 运营入口白名单内，API 请求由 Playwright 转发到 e2e_http.OPERATIONS。
用法：E2E_TARGET=test python3 -I browser_check.py <origin> <DRAFT versionId> <截图目录>"""
import json
import re
import site
import sys
from decimal import Decimal
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

ORIGIN, VERSION, OUT = sys.argv[1], sys.argv[2], Path(sys.argv[3])
OUT.mkdir(parents=True, exist_ok=True)
results = []
captured = {}
put_mode = {"mode": "abort"}


def check(name, ok, detail=None):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {name}" + ("" if ok else f" {json.dumps(detail, ensure_ascii=False)[:600]}"))


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def norm(v):
    if v is None or v == "":
        return None
    try:
        return str(Decimal(v).normalize())
    except Exception:  # noqa: BLE001
        return v


def comp_key(c):
    return {k: norm(c.get(k)) if k in ("rate", "fixedAmount", "minAmount", "maxAmount") else c.get(k) for k in
            ("componentCode", "componentType", "triggerCode", "calculationBase", "refundBehavior", "rate", "fixedAmount", "currency",
             "minAmount", "maxAmount", "bandBasis", "bandMode")} | {
        "bands": [{k: norm(b.get(k)) for k in ("lowerBound", "upperBound", "rate", "fixedAmount")} for b in (c.get("bands") or [])]}


def price_key(p):
    """读模型把结算四字段放在 settlement 子对象，写请求平铺；非阶梯组件 bands 读回 []、提交 null，均视为无档位。"""
    st = p.get("settlement") or p
    return (p["entityNo"], p["currency"], p["pricingProfileCode"], p["priorityNo"], st.get("settlementBusinessDays"),
            st.get("businessCalendarCode"), st.get("settlementTimezone"), st.get("cutoffSecondOfDay"),
            json.dumps(sorted((comp_key(c) for c in p["components"]), key=lambda c: c["componentCode"]), sort_keys=True))


s = e2e_http.secrets()
with sync_playwright() as p:
    browser = p.chromium.launch()
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    page = ctx.new_page()
    errors, deprecations, expected = [], [], []  # expected：脚本故意中止 / 模拟 400 的保存请求
    page.on("console", lambda m: (deprecations if m.text.startswith("Warning: [antd") else (expected if m.text.startswith("Failed to load resource") else errors)).append(m.text) if m.type == "error" else None)
    original = {}

    def relay(route):
        req = route.request
        tail = req.url.split("/api/operations", 1)[1]
        if req.method == "PUT" and tail.split("?")[0].endswith("/prices"):
            captured["body"] = json.loads(req.post_data or "{}")
            if put_mode["mode"] == "passthrough":
                pass
            elif put_mode["mode"] == "owner-error":
                route.fulfill(status=400, content_type="application/problem+json", body=json.dumps({
                    "type": "about:blank", "title": "Bad Request", "status": 400, "code": "BUSINESS_CHECK_FAILED",
                    "detail": "同一 OE 与币种下优先级重复: AE001/USD, 优先级 1", "requestId": "browser-check", "timestamp": "2026-10-20T00:00:00Z"}))
            else:
                route.abort()
            if put_mode["mode"] != "passthrough":
                return
        url = e2e_http.OPERATIONS + "/api/operations" + tail
        host = e2e_http.OPERATIONS.split("//", 1)[1]
        resp = route.fetch(url=url, headers={**req.headers, "origin": e2e_http.OPERATIONS, "referer": e2e_http.OPERATIONS + "/", "host": host})
        if req.method == "GET" and f"/commercial-terms/versions/{VERSION}" in tail and "prices" in (resp.text() or "") and "body" not in original:
            original["body"] = resp.json()
        route.fulfill(response=resp)

    page.route("**/api/operations/**", relay)
    page.goto(ORIGIN + "/")
    page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
    page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
    button(page, "登录").click()
    page.get_by_label("Email / 初始化账号").wait_for(state="hidden", timeout=15000)
    page.goto(f"{ORIGIN}/commercial/terms/versions/{VERSION}")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(1000)

    def open_drawer():
        button(page, "编辑价格").first.click()
        page.locator(".commercial-drawer .pc-card").first.wait_for(timeout=10000)
        page.wait_for_timeout(500)
        return page.locator(".commercial-drawer")

    drawer = open_drawer()
    cards = drawer.locator(".pc-card")
    n = cards.count()
    check("AC-06 11 条价格且 > 3 条默认全部折叠", n == 11 and drawer.locator(".pc-card.is-open").count() == 0, {"cards": n})
    summaries = [cards.nth(i).locator(".pc-summary").inner_text() for i in range(n)]
    check("AC-06 摘要格式", all(re.match(r"^价格 \d+ · AE001 · (AED|USD) · \S+ · 优先级 \d+ · 6 个组件$", t) for t in summaries), summaries[:3])
    drawer.screenshot(path=str(OUT / "drawer-collapsed.png"))
    idx = next(i for i, t in enumerate(summaries) if "USD · CARD_STANDARD" in t)
    card = cards.nth(idx)
    card.locator(".pc-toggle").click()
    page.wait_for_timeout(400)
    rows = card.locator("tbody.ct-component")
    check("AC-01 展开后 6 行组件", rows.count() == 6, rows.count())
    sale = card.locator("tr.ct-row", has=page.locator("input[value='SALE_PCT']"))
    rate = sale.locator("td").nth(4).locator("input").input_value()
    check("AC-03 读回费率按百分比显示（SALE_PCT 0.029 → 2.9）", rate == "2.9", rate)
    fixed_row = card.locator("tr.ct-row", has=page.locator("input[value='CAPTURE_FIXED']"))
    check("AC-02 固定额行费率 / 最低 / 最高为「—」", fixed_row.locator("td.ct-na").count() == 3, fixed_row.locator("td.ct-na").count())
    scroll = card.locator(".ct-scroll").evaluate("e => [e.scrollWidth, e.clientWidth]")
    check("AC-08 1080 抽屉内组件表格无横向滚动", scroll[0] <= scroll[1], scroll)
    box = card.locator(".ct-table").bounding_box()
    check("AC-09 6 个组件的表格高度可在 900 视口内完整显示", box and box["height"] < 700, box)
    tier = card.locator(".ct-tier-toggle")
    check("AC-05 阶梯行显示「2 档」摘要", tier.count() == 1 and "2 档" in tier.inner_text(), tier.inner_text() if tier.count() else None)
    tier.click()
    page.wait_for_timeout(300)
    band_rates = [i.input_value() for i in card.locator(".ct-tier-row:not([hidden]) .ct-band-table input").all()]
    check("AC-03 档位费率按百分比显示（2.9 / 2.5）", "2.9" in band_rates and "2.5" in band_rates, band_rates)
    card.scroll_into_view_if_needed()
    drawer.screenshot(path=str(OUT / "drawer-expanded-tiered.png"))
    tier.click()
    page.wait_for_timeout(200)
    check("AC-05 档位收起", card.locator(".ct-tier-row:not([hidden])").count() == 0)
    # AC-01：不改任何值保存，请求体与原价格逐字段一致（截下后中止）
    button(drawer, "保存全部价格").click()
    page.wait_for_timeout(1500)
    body = captured.get("body")
    orig_prices = (original.get("body") or {}).get("prices") or []
    if body:
        Path(OUT / "ac01-sent.json").write_text(json.dumps(body["prices"], ensure_ascii=False, indent=1))
        Path(OUT / "ac01-original.json").write_text(json.dumps(orig_prices, ensure_ascii=False, indent=1))
    same = body and sorted(map(price_key, body["prices"])) == sorted(map(price_key, orig_prices))
    check("AC-01 不改值保存的请求体与原价格一致（含组件币种）", same and len(body["prices"]) == 11,
          {"sent": len(body["prices"]) if body else None, "orig": len(orig_prices),
           "onlySent": sorted(set(map(price_key, body["prices"])) - set(map(price_key, orig_prices)))[:1] if body else None,
           "onlyOrig": sorted(set(map(price_key, orig_prices)) - set(map(price_key, body["prices"])))[:1] if body else None})
    # AC-05 复制 / 删除
    before = rows.count()
    fixed_row.locator("button", has_text=re.compile("复\\s?制")).click()
    page.wait_for_timeout(300)
    codes = [i.input_value() for i in card.locator("tr.ct-row td:first-child input").all()]
    check("AC-05 复制插入下一行且代码加 _COPY", rows.count() == before + 1 and "CAPTURE_FIXED_COPY" in codes, codes)
    copy_row = card.locator("tr.ct-row", has=page.locator("input[value='CAPTURE_FIXED_COPY']"))
    copy_row.locator("button", has_text=re.compile("删\\s?除")).click()
    page.wait_for_timeout(300)
    check("AC-05 删除复制行", rows.count() == before)
    # AC-10 错误：清空 CAPTURE_FIXED 固定金额后保存，不发请求、行下显示错误
    captured.clear()
    amount = fixed_row.locator("td").nth(5).locator("input")
    amount.fill("")
    button(drawer, "保存全部价格").click()
    page.wait_for_timeout(1000)
    err = card.locator("tr.ct-error-row")
    check("AC-10 校验失败不发请求、行下显示错误", "body" not in captured and err.count() >= 1 and "固定金额" in err.first.inner_text(),
          err.first.inner_text() if err.count() else None)
    red = amount.evaluate("e => getComputedStyle(e.closest('.ant-input-affix-wrapper') || e).borderColor")
    print(f"[说明] 错误单元格边框颜色 {red}")
    drawer.screenshot(path=str(OUT / "drawer-row-error.png"))
    # AC-06 出错价格自动展开：收起该卡片后再保存
    card.locator(".pc-toggle").click()
    page.wait_for_timeout(200)
    button(drawer, "保存全部价格").click()
    page.wait_for_timeout(1000)
    check("AC-06 校验失败时出错价格自动展开", "is-open" in (card.get_attribute("class") or ""))
    button(drawer, "取消").first.click()
    page.wait_for_timeout(500)
    # AC-07 Owner 错误横幅：把价格 2 的优先级改成与同币种价格 1 相同，提交给 commercial，由 Owner 拒绝（拒绝即不写入）
    drawer = open_drawer()
    put_mode["mode"] = "passthrough"
    second = drawer.locator(".pc-card").nth(1)
    second.locator(".pc-toggle").click()
    page.wait_for_timeout(300)
    prio = second.locator(".pc-grid-price input.ant-input-number-input").first
    prio.fill("1")
    button(drawer, "保存全部价格").click()
    page.wait_for_timeout(2500)
    banner = drawer.locator(".commercial-banner")
    if banner.count() == 0:
        print("[调试] 抽屉文本：", drawer.inner_text()[:400].replace("\n", " | "))
        print("[调试] 页面消息：", page.locator(".ant-message, .ant-notification").all_inner_texts())
    check("AC-07 Owner 错误 detail 显示在抽屉顶部", banner.count() == 1 and "优先级重复" in banner.inner_text(), banner.inner_text() if banner.count() else None)
    drawer.screenshot(path=str(OUT / "drawer-owner-error.png"))
    check("控制台无错误（不含既有 antd 弃用警告）", not errors, errors[:5])
    print(f"[说明] 既有 antd 弃用警告：{sorted(set(deprecations))}")
    browser.close()
print(f"\n{sum(ok for _, ok in results)}/{len(results)} 通过")
