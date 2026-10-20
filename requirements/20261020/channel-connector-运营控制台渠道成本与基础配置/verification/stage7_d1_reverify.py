"""阶段 7 D1 最小重验（mor-test）：经运营接口登记一个 DRAFT 渠道与一个未配置账号，浏览器打开渠道详情「技术账号」，
确认「配置」列显示红色「未配置」（不再是 REJECTED），截图 assets/stage7/D1-未配置账号-配置列.png；顺带打开商务「合同」与财务「账务基础资料」各一页。
运行记录写 channel-acceptance-test-<ts>.json，之后 `E2E_TARGET=test python3 -I local_acceptance.py --cleanup` 按 ID 清理。
用法：E2E_TARGET=test python3 -I stage7_d1_reverify.py
"""
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import local_acceptance as acc  # noqa: E402
import local_browser as b  # noqa: E402
from local_acceptance import e2e_http  # noqa: E402
from local_browser import sync_playwright  # noqa: E402

OUT = HERE.parent / "assets/stage7"


def main():
    s = e2e_http.secrets()
    admin = acc.login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    code = f"D1CHK_{acc.RUN}"
    status, channel = acc.raw(admin, "POST", "/channels", {"channelCode": code, "channelName": f"D1 重验 {acc.RUN}"})
    acc.remember("channels", channel.get("channelId"))
    acc.check("D1", f"新建渠道 {code} → 201（{status}）", status == 201, channel)
    status, account = acc.raw(admin, "POST", "/channel-accounts", {"channelId": channel.get("channelId"), "entityNo": "AE001",
                                                                   "accountName": f"D1 未配置账号 {acc.RUN}"})
    acc.check("D1", f"新建未配置账号 → 201 configured=false（{status}）", status == 201 and account.get("configured") is False, account)
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900}).new_page()
        page.goto(b.ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(s["E2E_ADMIN_EMAIL"])
        page.get_by_label("密码").fill(s["E2E_ADMIN_PASSWORD"])
        b.button(page, "登录").click()
        b.idle(page)
        bundle = page.evaluate("() => [...document.scripts].map(s => s.src).find(src => /index-.*\\.js/.test(src)) || ''")
        acc.check("D1", f"页面加载新入口包（{bundle.split('/')[-1]}）", bundle.endswith("index-H8mAJBt-.js"))
        b.goto(page, f"/channel/channels/{channel.get('channelId')}")
        row = b.row(page, f"D1 未配置账号 {acc.RUN}")
        b.visible(row)
        cell = row.locator("td").nth(3)
        tag = cell.locator(".ant-tag")
        text = cell.inner_text().strip()
        red = tag.count() == 1 and "ant-tag-red" in (tag.first.get_attribute("class") or "")
        acc.check("D1", f"「配置」列为红色「未配置」（文本={text!r}，红色标签={red}）", text == "未配置" and red)
        acc.check("D1", "账号行不再出现 REJECTED", "REJECTED" not in row.inner_text())
        page.wait_for_timeout(500)
        page.screenshot(path=str(OUT / "D1-未配置账号-配置列.png"), full_page=True)
        for name, route in (("商务/合同", "/commercial/agreements"), ("财务/账务基础资料", "/finance/setup")):
            page.wait_for_timeout(3000)
            b.goto(page, route)
            ok = b.visible(page.locator(".page-heading h1, h1").first, 8000) and page.locator(".ant-notification-notice").count() == 0 \
                and page.get_by_text(re.compile("加载失败|暂时无法加载|没有权限")).count() == 0
            acc.check("D1", f"{name}（{route}）打开正常", ok)
        browser.close()
    return acc.finish()


if __name__ == "__main__":
    sys.exit(main())
