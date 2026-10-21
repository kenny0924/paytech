"""commercial 开通与定价模型调整（一期）阶段 7 浏览器验收：mor-test 真实运营入口（http://localhost:28080），A 登录。
1) 商务页截图（含新「标准价目」页、产品目录「新增开通项」、合同开通项条件弹窗、版本编辑器来源 / 破标、签署按钮与弹窗、相关性提示）到 assets/stage7/；
   弹窗只打开截图后取消，不提交任何写请求（所有非 GET 请求被拦截并中止，计数记录）。
2) 全部运营页只读回归：页面清单取发布源码 pageCapabilities.ts，每页停 7 秒，统计控制台错误、HTTP ≥ 400 与失败请求、无权限提示。
不打印 Token 与口令。用法：python3 -I stage7_browser.py <pageCapabilities.ts> <截图目录> <运行记录 JSON> pages|regression
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

CAPS, OUT, RECORD, MODE = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4]
OUT.mkdir(parents=True, exist_ok=True)
ORIGIN = e2e_http.OPERATIONS
IDS = {"agreement101": "238411008014618624", "version101": "238411010732527616", "version102": "238411680076337152",
       "version103": "238411682005716992", "version901v3": "238411686661394432", "versionFx901": "238411687579947008",
       "templateStdV2": "238411696840970240", "versionPt101": "238411683410808832"}
result = {"pages": [], "regression": [], "blockedWrites": []}
PAGE = []


def button(scope, text):
    return scope.get_by_role("button", name=re.compile("^" + r"\s?".join(text) + "$"))


def main():
    s = e2e_http.secrets()
    routes = re.findall(r'"frontendRoute": "([^"]+)"', CAPS.read_text())
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 1440, "height": 900})
        PAGE.append(page)
        errors, failed = [], []
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("requestfailed", lambda r: failed.append(f"FAILED {r.method} {r.url.split('?')[0]}")
                if r.url.split("?")[0] not in {b.split(" ", 1)[1] for b in result["blockedWrites"]} else None)
        page.on("response", lambda r: failed.append(f"{r.status} {r.request.method} {r.url.split('?')[0]}") if r.status >= 400 else None)

        def guard(route):
            req = route.request
            if req.method != "GET" and "/auth/v1/" not in req.url:
                result["blockedWrites"].append(f"{req.method} {req.url.split('?')[0]}")
                route.abort()
                return
            route.continue_()

        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        button(page, "登录").click()
        page.get_by_label("Email / 初始化账号").wait_for(state="hidden", timeout=15000)
        page.route("**/api/operations/**", guard)
        page.wait_for_timeout(7000)

        def visit(name, path, expect_texts=(), action=None):
            errors.clear()
            failed.clear()
            page.goto(ORIGIN + path)
            page.wait_for_load_state("networkidle")
            page.wait_for_timeout(2500)
            body = page.locator("body").inner_text()
            found = {t: (t in body) for t in expect_texts}
            page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
            extra = None
            if action:
                try:
                    extra = action()
                except Exception as failure:  # noqa: BLE001
                    extra = {"actionError": str(failure)[:300]}
            entry = {"name": name, "path": path, "found": found, "consoleErrors": errors[:5], "http": failed[:5],
                     "denied": "无权限" in body, "action": extra}
            entry["ok"] = all(found.values()) and not errors and not failed and not entry["denied"] and not (extra or {}).get("actionError")
            result["pages"].append(entry)
            print(f"[{'通过' if entry['ok'] else '失败'}] {name} {path}" + ("" if entry["ok"] else f" {json.dumps(entry, ensure_ascii=False)[:600]}"))
            page.wait_for_timeout(4500)

        def modal_shot(trigger, shot, close=("取消",)):
            trigger.first.click()
            dialog = page.locator(".ant-modal:visible, .ant-drawer-content:visible, .ant-drawer-section:visible").first
            dialog.wait_for(timeout=8000)
            page.wait_for_timeout(800)
            page.screenshot(path=str(OUT / f"{shot}.png"))
            text = dialog.inner_text()
            for label in close:
                candidate = button(dialog, label)
                if candidate.count():
                    candidate.first.click()
                    break
            else:
                page.keyboard.press("Escape")
            page.wait_for_timeout(600)
            return text

        if MODE == "pages":
            commercial_pages(visit, modal_shot)
        else:
            regression(page, routes, errors, failed)
        browser.close()
    result["summary"] = {"pagesPassed": sum(e["ok"] for e in result["pages"]), "pages": len(result["pages"]),
                         "regressionPassed": sum(e["ok"] for e in result["regression"]), "regression": len(result["regression"]),
                         "blockedWrites": len(result["blockedWrites"])}
    RECORD.write_text(json.dumps(result, ensure_ascii=False, indent=1))
    print(json.dumps(result["summary"], ensure_ascii=False))


def commercial_pages(visit, modal_shot):
    page = PAGE[0]
    if True:
        visit("01-workbench", "/commercial/workbench", ("待审批版本", "破标"))
        visit("02-catalog", "/commercial/catalog", ("新增开通项", "RECURRING", "STC_PAY", "TOKEN_STORAGE", "功能"),
              lambda: {"modal": modal_shot(page.get_by_role("button", name=re.compile("新增开通项")), "02b-catalog-new-capability-modal")[:300]})
        visit("03-templates", "/commercial/templates", ("标准价 · AE001", "优惠价 · AE001", "平台费标准价 · AE001", "外汇点差标准价 · AE001"))
        visit("04-template-version-std-v2", f"/commercial/templates?versionId={IDS['templateStdV2']}", ("标准价 · AE001",))

        def capability_modal():
            page.get_by_role("tab", name=re.compile("产品与开通|开通项")).first.click()
            page.wait_for_timeout(1500)
            page.screenshot(path=str(OUT / "05a-agreement-101-capabilities-tab.png"), full_page=True)
            tab_text = page.locator("body").inner_text()
            listed = {k: k in tab_text for k in ("CARD", "VISA", "AED", "3DS", "TOKEN_STORAGE")}
            listed["借记/DEBIT"] = "借记" in tab_text or "DEBIT" in tab_text
            card_row = page.locator("tr", has=page.get_by_text("CARD", exact=True))
            text = modal_shot(card_row.get_by_role("button", name=re.compile("编\\s?辑")), "05b-agreement-capability-conditions-modal")
            ok = all(listed.values()) and all(k in text for k in ("VISA", "AED")) and ("DEBIT" in text or "借记" in text) and "3DS" in text
            return {"tabListed": listed, "modalText": text[:400], **({} if ok else {"actionError": "开通项标签或条件弹窗缺少预期内容"})}

        visit("05-agreement-101", f"/commercial/agreements/{IDS['agreement101']}", ("DEMO2-CL-101",), capability_modal)
        visit("06-version-101-benchmark", f"/commercial/terms/versions/{IDS['version101']}", ("破标", "标准价已更新", "客户已签署"))
        visit("07-version-102-pending-signature", f"/commercial/terms/versions/{IDS['version102']}", ("登记客户签署",),
              lambda: {"modal": modal_shot(button(page, "登记客户签署"), "07b-version-102-sign-modal")[:300]})
        visit("08-version-901-relevance", f"/commercial/terms/versions/{IDS['version901v3']}", ("未对应开通项",))
        visit("09-version-901-fx-draft", f"/commercial/terms/versions/{IDS['versionFx901']}", ("从标准价目补齐",))

        def new_version_modal():
            text = modal_shot(button(page, "新建版本"), "10b-version-103-new-version-modal")
            return {"hasTemplateOption": "基于标准价目" in text, "text": text[:300]}

        visit("10-version-103", f"/commercial/terms/versions/{IDS['version103']}", ("DEMO2-CL-103",), new_version_modal)
        visit("11-version-pt101", f"/commercial/terms/versions/{IDS['versionPt101']}", ("DEMO2-PT-101",))
        visit("06c-version-101-v2-after-fix", "/commercial/terms/versions/238417967673577472", ("破标", "客户已签署"))
        visit("12-terms", "/commercial/terms", ("DEMO2-CL-101-ACQ",))
        visit("13-agreements", "/commercial/agreements", ("DEMO2-CL-101",))
        visit("14-rate-cards", "/commercial/rate-cards", ())
        visit("15-price-simulator", "/commercial/price-simulator", ())
        visit("16-pricing-profiles", "/commercial/pricing-profiles", ("CARD_VISA_DEBIT",))
        visit("17-history", "/commercial/history", ())


def regression(page, routes, errors, failed):
    if True:
        for route in routes:
            errors.clear()
            failed.clear()
            page.goto(ORIGIN + route)
            page.wait_for_load_state("networkidle")
            page.wait_for_timeout(7000)
            denied = "无权限" in page.locator("body").inner_text()
            ok = not errors and not failed and not denied
            result["regression"].append({"route": route, "ok": ok, "consoleErrors": errors[:3], "http": failed[:3], "denied": denied})
            print(f"[{'通过' if ok else '失败'}] 回归 {route}" + ("" if ok else f" errors={errors[:2]} http={failed[:3]} denied={denied}"))


if __name__ == "__main__":
    main()
