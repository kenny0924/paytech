"""验收反馈第二轮 F4–F6（ops-web capability-20261021e）浏览器验收：mor-test 真实运营入口，A 登录，只读（全部非 GET 请求拦截并计数）。
F4 接受条件第一行为必填的币种，汇总句以币种开头（DEMO2-CL-101 编辑 CARD）。
F5 币种行：AED / USD / SAR / EUR / GBP 快捷标签 + 可搜索「更多币种」下拉；搜「科威特」得「KWD 科威特第纳尔」，选中后标签、下拉与汇总同步；取消不保存。
F6 合同详情页签「参与方与签署 → 产品与开通（N）→ 条款版本」；按订阅产品分组开通项；已订阅无开通项的黄色提示；区头「+ 开通该产品的项」只列该产品；「修改订阅」弹窗。
复用 stage7_feedback_f.py 的登录、拦截与抽屉辅助；截图 assets/stage7/f2-*.png。不打印 Token 与口令。
用法：python3 -I stage7_feedback_f2.py <截图目录> <运行记录 JSON>
"""
import importlib.util
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("feedback_f", HERE / "stage7_feedback_f.py")
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)

from playwright.sync_api import sync_playwright  # noqa: E402

OUT, RECORD, ORIGIN, DRAWER = f.OUT, f.RECORD, f.ORIGIN, f.DRAWER
AG_101, AG_103 = "238411008014618624", "238411681049415680"
AG_F1105E = "238132079668301824"   # 已订阅 PAYOUT 但无出款开通项（只读查得）
check, button, card, row, summary = f.check, f.button, f.card, f.row, f.summary


def products_tab(page, agreement_id):
    page.goto(f"{ORIGIN}/commercial/agreements/{agreement_id}")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)
    tabs = [t.strip() for t in page.get_by_role("tab").all_inner_texts()]
    page.get_by_role("tab", name=re.compile("产品与开通")).first.click()
    page.wait_for_timeout(1500)
    return tabs


def block(page, product_code):
    return page.locator(".cc-product-block", has=page.locator(".commercial-block-head h3", has_text=product_code))


def selected_currency_options(page, drawer):
    """打开「更多币种」下拉，读出已选项文字后关闭。"""
    drawer.locator(".cc-currency-select").click()
    page.wait_for_timeout(500)
    selected = page.locator(".ant-select-dropdown:visible .ant-select-item-option-selected").all_inner_texts()
    page.keyboard.press("Escape")
    page.wait_for_timeout(300)
    return [s.strip() for s in selected]


def f4_f5(page):
    """F4 / F5：DEMO2-CL-101 编辑 CARD。"""
    products_tab(page, AG_101)
    block(page, "ACQUIRING").locator("tr", has=page.get_by_text("CARD", exact=True)).get_by_role("button", name=re.compile("编\\s?辑")).click()
    drawer = page.locator(DRAWER)
    drawer.wait_for(timeout=10000)
    page.wait_for_timeout(1000)
    labels = [re.sub(r"^\*\s*", "", label.strip()) for label in drawer.locator(".cc-condition-row label").all_inner_texts()]
    first_summary = summary(drawer)
    page.screenshot(path=str(OUT / "f2-01-101-edit-card-currency-first.png"))
    check("F4 接受条件第一行为必填的「币种」", labels[0] == "币种" and drawer.locator(".cc-condition-row").first.locator(".ant-form-item-required").count() == 1,
          labels)
    check("F4 汇总句以币种开头（接受：币种 AED · …）", first_summary.startswith("接受：币种 AED"), first_summary)

    currency = row(drawer, "币种")
    quick = [t.strip() for t in currency.locator(".ant-tag-checkable").all_inner_texts()]
    check("F5 币种快捷标签 AED / USD / SAR / EUR / GBP，并有「更多币种」下拉，无「不限」",
          quick == ["AED", "USD", "SAR", "EUR", "GBP"] and currency.locator(".cc-currency-select").count() == 1, quick)
    currency.locator(".cc-currency-select").click()
    page.keyboard.type("科威特")
    page.wait_for_timeout(600)
    options = [o.strip() for o in page.locator(".ant-select-dropdown:visible .ant-select-item-option").all_inner_texts()]
    page.screenshot(path=str(OUT / "f2-02-101-currency-search-kwd.png"))
    check("F5 搜「科威特」得「KWD 科威特第纳尔」", "KWD 科威特第纳尔" in options, options)
    page.locator(".ant-select-dropdown:visible .ant-select-item-option", has_text="KWD 科威特第纳尔").click()
    page.keyboard.press("Escape")
    page.wait_for_timeout(500)
    after_kwd = {"tags": [t.strip() for t in currency.locator(".ant-tag-checkable-checked").all_inner_texts()],
                 "select": selected_currency_options(page, drawer), "summary": summary(drawer)}
    check("F5 选中 KWD：快捷标签 AED 仍选中、下拉含 AED + KWD、汇总「币种 AED、KWD」",
          after_kwd["tags"] == ["AED"] and {"AED 阿联酋迪拉姆", "KWD 科威特第纳尔"} <= set(after_kwd["select"])
          and "币种 AED、KWD" in after_kwd["summary"], after_kwd)
    currency.locator(".ant-tag-checkable", has_text=re.compile("^USD$")).click()
    page.wait_for_timeout(400)
    after_usd = {"tags": [t.strip() for t in currency.locator(".ant-tag-checkable-checked").all_inner_texts()],
                 "select": selected_currency_options(page, drawer), "summary": summary(drawer)}
    page.screenshot(path=str(OUT / "f2-03-101-currency-synced.png"))
    check("F5 点快捷标签 USD：下拉同步含 USD，汇总「币种 AED、KWD、USD」",
          set(after_usd["tags"]) == {"AED", "USD"} and "USD 美元" in after_usd["select"] and "币种 AED、KWD、USD" in after_usd["summary"],
          after_usd)
    f.close_drawer(page, drawer)
    check("F5 全程未保存（无写请求）", not f.result["blockedWrites"] and not f.result["writes"], f.result["blockedWrites"])


def f6_103(page):
    """F6：DEMO2-CL-103 页签顺序、ACQUIRING 区块三条开通项、页底说明、区头开通抽屉只列该产品。"""
    tabs = products_tab(page, AG_103)
    check("F6 页签顺序：参与方与签署 → 产品与开通（3）→ 条款版本；无「订阅产品」「开通项」页签",
          len(tabs) == 3 and tabs[0].startswith("参与方与签署") and re.fullmatch(r"产品与开通\s*（3）", tabs[1]) and tabs[2] == "条款版本", tabs)
    acq = block(page, "ACQUIRING")
    rows = acq.locator("tbody tr.ant-table-row")
    data = [{"cells": [c.strip() for c in r.locator("td").all_inner_texts()]} for r in rows.all()]
    page.screenshot(path=str(OUT / "f2-04-103-products-and-capabilities.png"), full_page=True)
    text = " ".join(" ".join(d["cells"]) for d in data)
    head = acq.locator(".commercial-block-head").inner_text()
    check("F6 收单区块 3 个开通项：中文名 + 代码（银行卡 CARD、Google Pay GOOGLE_PAY、STC Pay STC_PAY）、条件标签、状态与编辑",
          rows.count() == 3 and "3 个开通项" in head and all(k in text for k in ("银行卡", "CARD", "Google Pay", "GOOGLE_PAY", "STC Pay", "STC_PAY"))
          and "币种 AED / USD" in text and text.count("开通") >= 3 and acq.get_by_role("button", name=re.compile("编\\s?辑")).count() == 3,
          {"head": head, "rows": data})
    foot = page.locator(".cc-product-footnote").inner_text()
    check("F6 页底列出未订阅 / 默认具备", "未订阅：" in foot and "默认具备：" in foot, foot)

    acq.get_by_role("button", name=re.compile("开通该产品的项")).click()
    drawer = page.locator(DRAWER)
    drawer.wait_for(timeout=10000)
    drawer.locator(".cc-card").first.wait_for(timeout=10000)
    page.wait_for_timeout(500)
    title = page.locator(".cc-command-drawer .ant-drawer-title").inner_text()
    cards = {c.locator(".cc-card-code").first.inner_text(): "is-existing" in (c.get_attribute("class") or "") for c in drawer.locator(".cc-card").all()}
    page.screenshot(path=str(OUT / "f2-05-103-open-acquiring-drawer.png"))
    check("F6 区头「+ 开通该产品的项」抽屉只列收单项，CARD / GOOGLE_PAY / STC_PAY 置灰",
          title.startswith("开通 ·") and set(cards) == {"CARD", "APPLE_PAY", "GOOGLE_PAY", "TOKEN_STORAGE", "RECURRING", "STC_PAY"}
          and {k for k, v in cards.items() if v} == {"CARD", "GOOGLE_PAY", "STC_PAY"}, {"title": title, "cards": cards})
    f.close_drawer(page, drawer)

    page.get_by_role("button", name=re.compile("修改订阅")).first.click()
    modal = page.locator(".ant-modal:visible").first
    modal.wait_for(timeout=8000)
    page.wait_for_timeout(500)
    modal_title = modal.locator(".ant-modal-title").inner_text()
    page.screenshot(path=str(OUT / "f2-06-103-subscription-modal.png"))
    check("F6 页签头「修改订阅」打开「订阅产品」弹窗", modal_title.strip() == "订阅产品", modal_title)
    button(modal, "取消").click()
    modal.wait_for(state="hidden", timeout=8000)


def f6_empty_product(page):
    """F6：CL-F1105E 已订阅 PAYOUT 而无出款开通项 → 黄色提示；PAYOUT 区头抽屉只列出款项。"""
    products_tab(page, AG_F1105E)
    payout = block(page, "PAYOUT")
    alert = payout.locator(".ant-alert-warning")
    text = alert.inner_text() if alert.count() else ""
    page.screenshot(path=str(OUT / "f2-07-f1105e-payout-empty-warning.png"), full_page=True)
    check("F6 已订阅无开通项（CL-F1105E · PAYOUT）显示黄色提示「已订阅，尚未开通任何项」与可开通项",
          "已订阅，尚未开通任何项" in text and "可开通：" in text and "0 个开通项" in payout.locator(".commercial-block-head").inner_text(), text)
    payout.get_by_role("button", name=re.compile("开通该产品的项")).click()
    drawer = page.locator(DRAWER)
    drawer.wait_for(timeout=10000)
    drawer.locator(".cc-card").first.wait_for(timeout=10000)
    page.wait_for_timeout(500)
    codes = drawer.locator(".cc-card-code").all_inner_texts()
    page.screenshot(path=str(OUT / "f2-08-f1105e-open-payout-drawer.png"))
    check("F6 PAYOUT 区头抽屉只列出款项（PAYOUT_LOCAL / PAYOUT_SWIFT），无收单项",
          set(codes) == {"PAYOUT_LOCAL", "PAYOUT_SWIFT"}, codes)
    f.close_drawer(page, drawer)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = f.open_page(browser, 1440, 900)
        for step in (lambda: f4_f5(page), lambda: f6_103(page), lambda: f6_empty_product(page)):
            try:
                step()
            except Exception as failure:  # noqa: BLE001
                check("步骤异常", False, str(failure)[:400])
                page.keyboard.press("Escape")
        browser.close()
    r = f.result
    check("全程控制台错误 0、HTTP ≥ 400 为 0、写请求 0", not r["consoleErrors"] and not r["http"] and not r["blockedWrites"] and not r["writes"],
          {"console": r["consoleErrors"][:5], "http": r["http"][:5], "blocked": r["blockedWrites"]})
    r["summary"] = {"passed": sum(c["ok"] for c in r["checks"]), "total": len(r["checks"]), "blockedWrites": len(r["blockedWrites"]),
                    "writes": len(r["writes"])}
    RECORD.write_text(json.dumps(r, ensure_ascii=False, indent=1))
    print(json.dumps(r["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
