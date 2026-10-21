"""O4 修复复验（ops-web capability-20261021g）：费率预览开通项只有 limits 时按维度合并为接受条件，3DS 不再遮蔽条件。只读。
DEMO2-CL-101（Client 237573476041465857）CARD 行：卡组织 VISA / 币种 AED / 借贷记类型 借记 / 要求 3DS（蓝）。
DEMO2-CL-103（Client 237573267559391233）不开 3DS 的 CARD / GOOGLE_PAY / STC_PAY 条件正常显示。截图 assets/stage7/f3-06 起。
用法：python3 -I stage7_feedback_o4.py <截图目录> <运行记录 JSON>
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

OUT, RECORD, ORIGIN = f.OUT, f.RECORD, f.ORIGIN
check, button = f.check, f.button


def rate_card(page, client_id, shot):
    page.goto(f"{ORIGIN}/commercial/rate-cards")
    page.wait_for_load_state("networkidle")
    page.locator(".ant-form-item", has_text="Client ID").locator("input").first.fill(client_id)
    page.keyboard.press("Escape")
    button(page, "预览").click()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(2000)
    page.screenshot(path=str(OUT / shot), full_page=True)
    rows = {}
    for tr in page.locator("tr.ant-table-row").all():
        cells = [c.strip() for c in tr.locator("td").all_inner_texts()]
        if len(cells) >= 5 and cells[1] == "AE001":
            tags = tr.locator(".commercial-limits .ant-tag")
            rows[cells[0].split("（")[0]] = {"tags": [t.strip() for t in tags.all_inner_texts()],
                                             "blue": [t.inner_text().strip() for t in tags.all() if "ant-tag-blue" in (t.get_attribute("class") or "")]}
    return rows


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = f.open_page(browser, 1440, 900)
        try:
            rows = rate_card(page, "237573476041465857", "f3-06-rate-card-101-card-conditions.png")
            card = rows.get("CARD", {})
            check("O4 DEMO2-CL-101 费率预览 CARD：卡组织 VISA / 币种 AED / 借贷记类型 借记 / 要求 3DS（蓝）",
                  set(card.get("tags", [])) == {"卡组织 VISA", "币种 AED", "借贷记类型 借记", "要求 3DS"} and card.get("blue") == ["要求 3DS"], rows)
            rows = rate_card(page, "237573267559391233", "f3-07-rate-card-103-no-3ds.png")
            check("O4 DEMO2-CL-103 不开 3DS：CARD 币种 AED / USD、GOOGLE_PAY 币种 AED、STC_PAY 币种 AED，无 3DS 标签",
                  rows.get("CARD", {}).get("tags") == ["币种 AED / USD"] and rows.get("GOOGLE_PAY", {}).get("tags") == ["币种 AED"]
                  and rows.get("STC_PAY", {}).get("tags") == ["币种 AED"], rows)
        except Exception as failure:  # noqa: BLE001
            check("步骤异常", False, str(failure)[:400])
        browser.close()
    r = f.result
    check("全程控制台错误 0、HTTP ≥ 400 为 0、写请求 0", not r["consoleErrors"] and not r["http"] and not r["blockedWrites"] and not r["writes"],
          {"console": r["consoleErrors"][:5], "http": r["http"][:5], "blocked": r["blockedWrites"]})
    r["summary"] = {"passed": sum(c["ok"] for c in r["checks"]), "total": len(r["checks"])}
    RECORD.write_text(json.dumps(r, ensure_ascii=False, indent=1))
    print(json.dumps(r["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
