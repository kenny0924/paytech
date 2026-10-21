"""验收反馈 F1–F3（ops-web capability-20261021d）浏览器验收：mor-test 真实运营入口，A 登录。截图 assets/stage7/f-*.png。
F1 合同新增开通项：已订阅产品下 ACTIVE 项全部显示，该 OE 已开通的置灰标「已开通」，点击切换为编辑该条。
F2 接受条件逐维度一行：中文标签、枚举平铺标签含「不限」、币种标签 + 其他、3DS 开关在末行、底部汇总一句话。
F3 合同与产品目录的新增 / 编辑开通项为右侧抽屉（宽 760，分段，底部固定取消 / 保存）；390px 窄屏占满且无横向滚动。
除第 3 步（DEMO2-CL-901 反例合同上真实保存一次）外，所有非 GET 请求一律拦截并计数；DEMO2-CL-101/102/103/PT-101 只读。不打印 Token 与口令。
用法：python3 -I stage7_feedback_f.py <截图目录> <运行记录 JSON>
"""
import json
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

OUT, RECORD = Path(sys.argv[1]), Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
ORIGIN = e2e_http.OPERATIONS
AG_101, AG_901 = "238411008014618624", "238411684283224064"
DRAWER = ".cc-command-drawer .ant-drawer-section"
result = {"checks": [], "blockedWrites": [], "writes": [], "consoleErrors": [], "http": []}
state = {"allowWrites": False}


def check(name, ok, evidence=None):
    result["checks"].append({"name": name, "ok": bool(ok), "evidence": evidence})
    print(f"[{'通过' if ok else '失败'}] {name}" + ("" if ok else f" ← {json.dumps(evidence, ensure_ascii=False)[:500]}"))


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def open_page(browser, width, height):
    page = browser.new_page(viewport={"width": width, "height": height})
    page.on("console", lambda m: result["consoleErrors"].append(m.text) if m.type == "error" else None)
    page.on("response", lambda r: result["http"].append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)

    def guard(route):
        req = route.request
        if req.method == "GET" or "/auth/v1/" in req.url:
            route.continue_()
            return
        if not state["allowWrites"]:
            result["blockedWrites"].append(f"{req.method} {req.url.split('?')[0]}")
            route.abort()
            return
        resp = route.fetch()
        result["writes"].append({"method": req.method, "path": req.url.split("/api/operations", 1)[1].split("?")[0],
                                 "body": json.loads(req.post_data or "null"), "status": resp.status})
        route.fulfill(response=resp)

    s = e2e_http.secrets()
    page.goto(ORIGIN + "/")
    page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
    page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
    button(page, "登录").click()
    page.get_by_label("Email / 初始化账号").wait_for(state="hidden", timeout=15000)
    page.route("**/api/operations/**", guard)
    page.wait_for_timeout(3000)
    return page


def capabilities_tab(page, agreement_id):
    page.goto(f"{ORIGIN}/commercial/agreements/{agreement_id}")
    page.wait_for_load_state("networkidle")
    page.get_by_role("tab", name=re.compile("开通项")).first.click()
    page.wait_for_timeout(1200)


def open_new_drawer(page):
    page.get_by_role("button", name=re.compile("新增开通项")).first.click()
    drawer = page.locator(DRAWER)
    drawer.wait_for(timeout=10000)
    drawer.locator(".cc-card").first.wait_for(timeout=10000)
    page.wait_for_timeout(600)
    return drawer


def card(drawer, name):
    return drawer.locator(".cc-card", has=drawer.page.locator(".cc-card-name", has_text=re.compile(f"^{re.escape(name)}$")))


def row(drawer, label):
    return drawer.locator(".cc-condition-row", has=drawer.page.locator("label", has_text=re.compile(f"^{re.escape(label)}$")))


def checked_tags(row_locator):
    return [t.strip() for t in row_locator.locator(".ant-tag-checkable-checked").all_inner_texts()]


def summary(drawer):
    return drawer.locator(".cc-condition-summary").inner_text().strip()


def drawer_shape(page, drawer):
    box = drawer.bounding_box()
    footer = page.locator(".cc-command-drawer .ant-drawer-footer")
    return {"width": box and round(box["width"]), "viewport": page.viewport_size["width"],
            "footerButtons": footer.locator("button").all_inner_texts() if footer.count() else [],
            "sections": drawer.locator(".cc-drawer-section-head h3").all_inner_texts(),
            "bodyOverflowX": drawer.locator(".ant-drawer-body").evaluate("e => e.scrollWidth - e.clientWidth"),
            "pageOverflowX": page.evaluate("() => document.documentElement.scrollWidth - document.documentElement.clientWidth")}


def close_drawer(page, drawer):
    button(page.locator(".cc-command-drawer .ant-drawer-footer"), "取消").click()
    drawer.wait_for(state="hidden", timeout=8000)
    page.wait_for_timeout(400)


def f1_f2_101(page):
    """1) DEMO2-CL-101：新增抽屉卡片置灰；点 CARD 切换为编辑并回填；卡组织「不限」/ MASTERCARD 汇总变化；取消。"""
    capabilities_tab(page, AG_101)
    drawer = open_new_drawer(page)
    page.screenshot(path=str(OUT / "f-01-101-new-drawer-cards.png"))
    shape = drawer_shape(page, drawer)
    check("F3 合同新增开通项为右侧抽屉：宽 760、分段、底部固定取消 / 保存",
          shape["width"] == 760 and shape["footerButtons"] and re.sub(r"\s", "", shape["footerButtons"][0]) == "取消"
          and re.sub(r"\s", "", shape["footerButtons"][-1]) == "保存" and "基本" in shape["sections"], shape)
    cards = {c.locator(".cc-card-code").first.inner_text(): {"existing": "is-existing" in (c.get_attribute("class") or ""),
                                                             "mark": c.locator(".cc-card-mark").all_inner_texts()}
             for c in drawer.locator(".cc-card").all()}
    check("F1 已订阅产品（ACQUIRING）下 ACTIVE 项全部显示",
          {"CARD", "APPLE_PAY", "GOOGLE_PAY", "TOKEN_STORAGE", "RECURRING", "STC_PAY"} <= set(cards), sorted(cards))
    check("F1 该 OE 已开通的 CARD、TOKEN_STORAGE 置灰并标「已开通」，其余未置灰",
          all(cards[c]["existing"] and cards[c]["mark"] == ["已开通"] for c in ("CARD", "TOKEN_STORAGE"))
          and not any(v["existing"] for k, v in cards.items() if k not in ("CARD", "TOKEN_STORAGE")), cards)

    card(drawer, "银行卡").click()
    page.wait_for_timeout(1200)
    drawer = page.locator(DRAWER)
    title = page.locator(".cc-command-drawer .ant-drawer-title").inner_text()
    brand, funding, currency = row(drawer, "卡组织"), row(drawer, "借贷记类型"), row(drawer, "币种")
    three_ds = drawer.locator(".cc-condition-row").last
    values = {"title": title, "卡组织": checked_tags(brand), "借贷记类型": checked_tags(funding), "币种": checked_tags(currency),
              "3DS": three_ds.locator(".ant-switch").get_attribute("aria-checked"), "lastRow": three_ds.inner_text()[:20],
              "summary": summary(drawer)}
    page.screenshot(path=str(OUT / "f-02-101-edit-card-prefilled.png"))
    check("F1 点已开通的 CARD 切换为「编辑开通项 · AE001 / 银行卡」", title.startswith("编辑开通项") and "银行卡" in title, title)
    check("F2 回填 VISA + 借记 + AED + 3DS，3DS 开关在末行",
          values["卡组织"] == ["VISA"] and values["借贷记类型"] == ["借记"] and values["币种"] == ["AED"] and values["3DS"] == "true"
          and values["lastRow"].startswith("要求 3DS"), values)
    check("F2 底部汇总一句话正确", all(t in values["summary"] for t in ("接受：", "卡组织 VISA", "借贷记类型 借记", "币种 AED", "要求 3DS"))
          and "发卡区域不限" in values["summary"], values["summary"])
    labels = drawer.locator(".cc-condition-row label").all_inner_texts()
    check("F2 维度行为中文标签（代码在 Tooltip）", not any(re.fullmatch(r"[A-Z_]+", label.strip("* ").strip()) for label in labels), labels)
    brand_tags = brand.locator(".ant-tag-checkable").all_inner_texts()
    check("F2 枚举维度平铺标签含「不限」与「+ 其他」；币种无「不限」",
          brand_tags[0] == "不限" and brand.locator(".cc-other-tag").count() == 1
          and "不限" not in currency.locator(".ant-tag-checkable").all_inner_texts() and currency.locator(".cc-other-tag").count() == 1,
          {"卡组织": brand_tags, "币种": currency.locator(".ant-tag-checkable").all_inner_texts()})
    country = row(drawer, "消费者国家").locator(".ant-select")
    country.click()
    page.keyboard.type("阿联")
    page.wait_for_timeout(500)
    options = page.locator(".ant-select-dropdown:visible .ant-select-item-option").all_inner_texts()
    page.screenshot(path=str(OUT / "f-03-101-country-search.png"))
    page.keyboard.press("Escape")
    page.wait_for_timeout(300)
    check("F2 国家可搜索（输入「阿联」出现「AE 阿联酋」）", "AE 阿联酋" in options, options[:5])

    brand.locator(".ant-tag-checkable", has_text=re.compile("^不限$")).click()
    page.wait_for_timeout(300)
    after_unrestricted = summary(drawer)
    brand.locator(".ant-tag-checkable", has_text=re.compile("^MASTERCARD$")).click()
    page.wait_for_timeout(300)
    after_mc = summary(drawer)
    page.screenshot(path=str(OUT / "f-04-101-brand-changed-summary.png"))
    check("F2 点卡组织「不限」→ 汇总「卡组织不限」；再选 MASTERCARD → 「卡组织 MASTERCARD」（未保存）",
          "卡组织不限" in after_unrestricted and "卡组织 MASTERCARD" in after_mc and "VISA" not in after_mc,
          {"不限": after_unrestricted, "MASTERCARD": after_mc})
    close_drawer(page, drawer)


def f2_new_required(page):
    """2) 未开通项 Apple Pay：条件行渲染；不填币种点保存应提示必填且不发请求。"""
    drawer = open_new_drawer(page)
    card(drawer, "Apple Pay").click()
    page.wait_for_timeout(800)
    labels = [label.strip() for label in drawer.locator(".cc-condition-row label").all_inner_texts()]
    blocked_before = len(result["blockedWrites"])
    button(page.locator(".cc-command-drawer .ant-drawer-footer"), "保存").click()
    page.wait_for_timeout(1200)
    errors = drawer.locator(".ant-form-item-explain-error").all_inner_texts()
    page.screenshot(path=str(OUT / "f-05-101-applepay-currency-required.png"))
    check("F2 Apple Pay 条件行：币种 / 消费者国家 / 卡组织 / 借贷记类型 + 要求 3DS",
          {"币种", "消费者国家", "卡组织", "借贷记类型"} <= {re.sub(r"^\*\s*", "", label) for label in labels} and labels[-1] == "要求 3DS", labels)
    check("F2 不填币种点保存提示「币种必填」，未发出写请求",
          "币种必填" in errors and len(result["blockedWrites"]) == blocked_before and not result["writes"], {"errors": errors})
    close_drawer(page, drawer)


def f3_real_save(page):
    """3) DEMO2-CL-901：经抽屉真实保存一次（编辑 TOKEN_STORAGE，内容不变），确认 200、抽屉关闭、列表刷新。"""
    capabilities_tab(page, AG_901)
    drawer = open_new_drawer(page)
    card(drawer, "存卡").click()
    page.wait_for_timeout(1000)
    drawer = page.locator(DRAWER)
    title = page.locator(".cc-command-drawer .ant-drawer-title").inner_text()
    page.screenshot(path=str(OUT / "f-06-901-edit-token-storage.png"))
    state["allowWrites"] = True
    try:
        button(page.locator(".cc-command-drawer .ant-drawer-footer"), "保存").click()
        drawer.wait_for(state="hidden", timeout=15000)
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1000)
    finally:
        state["allowWrites"] = False
    page.screenshot(path=str(OUT / "f-07-901-after-save.png"))
    write = result["writes"][-1] if result["writes"] else None
    listed = "TOKEN_STORAGE" in page.locator("body").inner_text()
    check("F3 DEMO2-CL-901 经抽屉保存 TOKEN_STORAGE（内容不变）→ PUT 200，抽屉关闭，列表刷新",
          title.startswith("编辑开通项") and write and write["method"] == "PUT" and write["status"] == 200
          and "/capabilities/" in write["path"] and listed, {"title": title, "write": write})


def f3_catalog(page):
    """4) 产品目录：新增开通项抽屉与 RECURRING 编辑抽屉截图，取消。"""
    page.goto(f"{ORIGIN}/commercial/catalog")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(1000)
    page.get_by_role("button", name=re.compile("新增开通项")).first.click()
    drawer = page.locator(DRAWER)
    drawer.wait_for(timeout=10000)
    page.wait_for_timeout(600)
    shape = drawer_shape(page, drawer)
    page.screenshot(path=str(OUT / "f-08-catalog-new-drawer.png"))
    check("F3 产品目录新增开通项为右侧抽屉（宽 760，分段「基本 / 可配维度」，底部取消 / 创建）",
          shape["width"] == 760 and shape["sections"][:2] == ["基本", "可配维度"]
          and [re.sub(r"\s", "", b) for b in shape["footerButtons"]] == ["取消", "创建"], shape)
    close_drawer(page, drawer)
    recurring = page.locator("tr", has=page.get_by_text("RECURRING", exact=True))
    recurring.get_by_role("button", name=re.compile("编\\s?辑")).first.click()
    drawer = page.locator(DRAWER)
    drawer.wait_for(timeout=10000)
    page.wait_for_timeout(600)
    title = page.locator(".cc-command-drawer .ant-drawer-title").inner_text()
    shape = drawer_shape(page, drawer)
    page.screenshot(path=str(OUT / "f-09-catalog-edit-recurring-drawer.png"))
    check("F3 产品目录编辑 RECURRING 为右侧抽屉（标题「编辑开通项 · RECURRING」，底部取消 / 保存）",
          title.strip() == "编辑开通项 · RECURRING" and shape["width"] == 760
          and [re.sub(r"\s", "", b) for b in shape["footerButtons"]] == ["取消", "保存"], {"title": title, **shape})
    close_drawer(page, drawer)


def f3_narrow(browser):
    """5) 390px 窄屏：合同新增开通项抽屉占满且无横向滚动。"""
    page = open_page(browser, 390, 844)
    capabilities_tab(page, AG_101)
    drawer = open_new_drawer(page)
    card(drawer, "Apple Pay").click()
    page.wait_for_timeout(800)
    shape = drawer_shape(page, drawer)
    page.screenshot(path=str(OUT / "f-10-narrow-390-drawer.png"), full_page=False)
    check("F3 390px 窄屏抽屉占满且无横向滚动", shape["width"] == 390 and shape["bodyOverflowX"] <= 0 and shape["pageOverflowX"] <= 0, shape)
    close_drawer(page, drawer)
    page.close()


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = open_page(browser, 1440, 900)
        for step in (lambda: f1_f2_101(page), lambda: f2_new_required(page), lambda: f3_real_save(page), lambda: f3_catalog(page),
                     lambda: f3_narrow(browser)):
            try:
                step()
            except Exception as failure:  # noqa: BLE001
                check("步骤异常", False, str(failure)[:400])
                page.keyboard.press("Escape")
        browser.close()
    unexpected = result["http"]
    check("全程控制台错误 0、HTTP ≥ 400 为 0", not result["consoleErrors"] and not unexpected,
          {"console": result["consoleErrors"][:5], "http": unexpected[:5]})
    result["summary"] = {"passed": sum(c["ok"] for c in result["checks"]), "total": len(result["checks"]),
                         "blockedWrites": len(result["blockedWrites"]), "writes": len(result["writes"])}
    RECORD.write_text(json.dumps(result, ensure_ascii=False, indent=1))
    print(json.dumps(result["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
