"""观察 O2 / O3 修复（ops-web capability-20261021f）浏览器验收：mor-test 真实运营入口，A 登录，只读（全部非 GET 请求拦截并计数）。
O2 「更多币种」下拉不含快捷币种 AED / USD / SAR / EUR / GBP；选 KWD、QAR、BHD 后全部展开不折叠「+N」；汇总同步；切换快捷标签后下拉选择不丢；取消。
O3 DEMO2-CL-101「产品与开通」条件标签中文（借贷记类型 借记）、3DS 标签「要求 3DS」；费率预览页「要求 3DS」标签仍为蓝色。
复用 stage7_feedback_f.py 的登录、拦截与抽屉辅助；截图 assets/stage7/f3-*.png。不打印 Token 与口令。
用法：python3 -I stage7_feedback_f3.py <截图目录> <运行记录 JSON>
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
AG_101, CLIENT_101 = "238411008014618624", "237573476041465857"
QUICK = ["AED", "USD", "SAR", "EUR", "GBP"]
check, button, row, summary = f.check, f.button, f.row, f.summary


def products_tab(page):
    page.goto(f"{ORIGIN}/commercial/agreements/{AG_101}")
    page.wait_for_load_state("networkidle")
    page.get_by_role("tab", name=re.compile("产品与开通")).first.click()
    page.wait_for_timeout(1500)


def summary_currencies(text):
    match = re.search(r"币种 ([A-Z、]+)", text)
    return set(match.group(1).split("、")) if match else set()


def select_state(currency):
    select = currency.locator(".cc-currency-select")
    items = [t.strip() for t in select.locator(".ant-select-selection-item").all_inner_texts()]
    rest = select.locator(".ant-select-selection-overflow-item-rest")
    rest_text = " ".join(rest.all_inner_texts()).strip()
    return {"items": items, "rest": rest_text}


def o2(page):
    products_tab(page)
    page.locator("tr", has=page.get_by_text("CARD", exact=True)).get_by_role("button", name=re.compile("编\\s?辑")).first.click()
    drawer = page.locator(DRAWER)
    drawer.wait_for(timeout=10000)
    page.wait_for_timeout(1000)
    currency = row(drawer, "币种")
    currency.locator(".cc-currency-select").click()
    page.wait_for_timeout(500)
    options = [o.strip() for o in page.locator(".ant-select-dropdown:visible .ant-select-item-option").all_inner_texts()]
    page.screenshot(path=str(OUT / "f3-01-101-more-currencies-options.png"))
    codes = {o.split(" ", 1)[0] for o in options}
    check("O2「更多币种」下拉不含快捷币种 AED / USD / SAR / EUR / GBP", options and not (codes & set(QUICK)), options[:8])
    for code, name in (("KWD", "科威特"), ("QAR", "卡塔尔"), ("BHD", "巴林")):
        page.keyboard.type(name)
        page.wait_for_timeout(400)
        page.locator(".ant-select-dropdown:visible .ant-select-item-option", has_text=re.compile(f"^{code} ")).click()
        page.wait_for_timeout(300)
    page.keyboard.press("Escape")
    page.wait_for_timeout(500)
    state = select_state(currency)
    text = summary(drawer)
    page.screenshot(path=str(OUT / "f3-02-101-three-more-currencies-expanded.png"))
    check("O2 选 KWD、QAR、BHD 后下拉全部展开显示，不折叠为「+N」",
          len(state["items"]) == 3 and all(any(i.startswith(c) for i in state["items"]) for c in ("KWD", "QAR", "BHD")) and not state["rest"], state)
    check("O2 汇总同步：币种 AED、KWD、QAR、BHD", summary_currencies(text) == {"AED", "KWD", "QAR", "BHD"}, text)
    currency.locator(".ant-tag-checkable", has_text=re.compile("^USD$")).click()
    page.wait_for_timeout(400)
    currency.locator(".ant-tag-checkable", has_text=re.compile("^AED$")).click()
    page.wait_for_timeout(400)
    state2 = select_state(currency)
    tags2 = [t.strip() for t in currency.locator(".ant-tag-checkable-checked").all_inner_texts()]
    text2 = summary(drawer)
    page.screenshot(path=str(OUT / "f3-03-101-quick-toggle-keeps-more.png"))
    check("O2 切换快捷标签（选 USD、取消 AED）后下拉三项不丢，汇总「币种 USD、KWD、QAR、BHD」",
          tags2 == ["USD"] and len(state2["items"]) == 3 and summary_currencies(text2) == {"USD", "KWD", "QAR", "BHD"},
          {"tags": tags2, "select": state2, "summary": text2})
    f.close_drawer(page, drawer)
    check("O2 全程未保存（无写请求）", not f.result["blockedWrites"] and not f.result["writes"], f.result["blockedWrites"])


def o3(page):
    products_tab(page)
    acq = page.locator(".cc-product-block", has=page.locator(".commercial-block-head h3", has_text="ACQUIRING"))
    card_row = acq.locator("tr", has=page.get_by_text("CARD", exact=True))
    tags = [t.strip() for t in card_row.locator(".ant-tag").all_inner_texts()]
    blue = card_row.locator(".ant-tag", has_text="要求 3DS")
    page.screenshot(path=str(OUT / "f3-04-101-products-condition-tags.png"))
    check("O3「产品与开通」条件标签中文：卡组织 VISA、币种 AED、借贷记类型 借记、要求 3DS（蓝色）",
          {"卡组织 VISA", "币种 AED", "借贷记类型 借记", "要求 3DS"} <= set(tags) and not any("DEBIT" in t for t in tags)
          and "ant-tag-blue" in (blue.first.get_attribute("class") or ""), {"tags": tags, "class": blue.first.get_attribute("class") if blue.count() else None})

    page.goto(f"{ORIGIN}/commercial/rate-cards")
    page.wait_for_load_state("networkidle")
    page.locator(".ant-form-item", has_text="Client ID").locator("input").first.fill(CLIENT_101)
    page.keyboard.press("Escape")
    button(page, "预览").click()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(2000)
    tag = page.locator(".ant-tag", has_text=re.compile("^要求 3DS$"))
    rate_tags = [t.strip() for t in page.locator(".commercial-limits").first.locator(".ant-tag").all_inner_texts()] if page.locator(".commercial-limits").count() else []
    page.screenshot(path=str(OUT / "f3-05-rate-card-3ds-blue.png"), full_page=True)
    check("O3 费率预览页 DEMO2-CL-101 的「要求 3DS」标签为蓝色，条件标签中文",
          tag.count() >= 1 and "ant-tag-blue" in (tag.first.get_attribute("class") or "") and "借贷记类型 借记" in rate_tags,
          {"count": tag.count(), "class": tag.first.get_attribute("class") if tag.count() else None, "tags": rate_tags})


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = f.open_page(browser, 1440, 900)
        for step in (lambda: o2(page), lambda: o3(page)):
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
