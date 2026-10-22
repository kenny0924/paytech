"""文件多存储并存阶段 7 第 10 步：mor-test 浏览器冒烟（无头 Chromium，经运营入口 http://localhost:28080 真实登录）。

只读为主；唯一写操作是在「存储管理 → 桶」对 mor-platform-files 真实点一次“检查”（只回写最近检查时间与结果）。
1. 平台配置 → 存储管理：存储实例（MINIO、STATIC、密钥已配置）、桶（mor-platform-files 启用、检查 OK）、写入规则（默认、无删除按钮）三个页签；
   点“检查 → 开始检查”，BFF 响应 200 且 result=OK，弹窗显示“检查通过”，无 415。
2. 回归：客户入驻审核详情点一次材料下载（BFF 签发 200，浏览器取到 MinIO 对象 200）；币种页、渠道 MID 详情、外部接入方各打开一次。
3. 判定：控制台错误 0，4xx/5xx/失败请求 0。截图写 screens/，结果写 stage7-browser-result.json。
管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。改写自币种发布的同名脚本。
用法：python3 -I stage7_browser.py [审核任务 caseId]（缺省取 FS7_STATE 最新 run-*.json 的 legacyFile.caseId）
"""
import json
import os
import re
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
ORIGIN = "http://localhost:28080"
OUT = HERE / "screens"
OUT.mkdir(exist_ok=True)
STATE = Path(os.environ.get("FS7_STATE", Path.home() / "Code-MoR-backups/filestore-stage7"))
CHECK_PATH = "/storage-admin/v1/instances/MINIO/buckets/mor-platform-files/check"
MINIO = "minio.mor-test.localhost:29400"
ERROR_SELECTORS = ".ant-alert-error, .ant-result-error, .ant-result-500, .ant-result-403, .ant-result-404, " \
                  ".ant-message-error, .ant-notification-notice-error"
KNOWN = "[antd: Spin] `tip` is deprecated"


def credentials():
    text = (ROOT / "test-environment/private/access.md").read_text()
    return re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1)


def review_case():
    if len(sys.argv) > 1:
        return sys.argv[1]
    runs = sorted(STATE.glob("run-*.json"))
    for path in reversed(runs):
        legacy = json.loads(path.read_text()).get("legacyFile") or {}
        if legacy.get("caseId"):
            return legacy["caseId"]
    raise SystemExit("没有找到带已有材料的审核任务；请传入 caseId")


def visible(locator, timeout=8000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def settle(page):
    try:
        page.wait_for_load_state("networkidle", timeout=20000)
    except Exception:  # noqa: BLE001
        pass
    page.wait_for_timeout(1200)


def shot(page, name):
    page.wait_for_timeout(600)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
    return f"{name}.png"


def errors_on(page):
    return [t.strip().replace("\n", " ")[:200] for t in page.locator(ERROR_SELECTORS).all_inner_texts() if t.strip()]


def main():
    email, password = credentials()
    case_id = review_case()
    results, console, network, shots, minio = [], [], [], [], []

    def record(name, ok, note):
        results.append({"page": name, "ok": bool(ok), "note": note})
        print(f"[{'通过' if ok else '失败'}] {name}：{note}", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch()
        # MinIO 证书由 test-ca 签发，浏览器默认不信任；只影响下载地址，运营入口是 http。
        context = browser.new_context(viewport={"width": 1512, "height": 900}, ignore_https_errors=True, accept_downloads=True)
        page = context.new_page()
        current = {"path": "/"}
        page.on("console", lambda m: console.append((current["path"], m.type, m.text)) if m.type in ("error", "warning") else None)

        def on_response(r):
            if MINIO in r.url:
                minio.append((current["path"], r.status, r.request.method, r.url.split("?")[0]))
            if r.status >= 400:
                network.append((current["path"], r.status, r.request.method, r.url.split("?")[0]))
        page.on("response", on_response)
        def on_failed(r):
            # 预签名地址转为下载时 Chromium 把原请求记为 ERR_ABORTED（响应已是 200，见 minioResponses），不算失败。
            if MINIO in r.url and "ERR_ABORTED" in str(r.failure):
                minio.append((current["path"], "ABORTED_TO_DOWNLOAD", r.method, r.url.split("?")[0]))
                return
            network.append((current["path"], "FAILED", r.method, r.url.split("?")[0] + " " + str(r.failure)))
        page.on("requestfailed", on_failed)

        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(email)
        page.get_by_label("密码").fill(password)
        page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
        settle(page)
        logged_in = not visible(page.get_by_label("密码"), 2000)
        record("登录", logged_in, "管理员登录")
        if not logged_in:
            shots.append(shot(page, "00-login-failed"))
            browser.close()
            return dump(results, console, network, shots, minio)

        def goto(path):
            current["path"] = path
            page.goto(ORIGIN + path)
            settle(page)

        # ---------- 1. 存储管理 ----------
        goto("/file-storage")
        menu_entry = visible(page.locator(".ant-menu").get_by_text("存储管理"), 5000)
        row = page.locator(".ant-table-tbody tr.ant-table-row", has_text="MINIO")
        has_row = visible(row, 10000)
        text = row.first.inner_text().replace("\n", " ") if has_row else ""
        create = visible(page.get_by_role("button", name=re.compile("新建")), 3000)
        errs = errors_on(page)
        shots.append(shot(page, "s01-instances"))
        record("存储管理/存储实例", menu_entry and has_row and ("STATIC" in text or "固定" in text) and not errs,
               f"菜单{'可见' if menu_entry else '缺失'}；MINIO 行：{text[:160]}；新建按钮{'可见' if create else '缺失'}" + (f"；错误：{errs}" if errs else ""))

        current["path"] = "/file-storage#buckets"
        page.get_by_role("tab", name="桶").click()
        settle(page)
        bucket = page.locator(".ant-table-tbody tr.ant-table-row", has_text="mor-platform-files")
        has_bucket = visible(bucket, 8000)
        btext = bucket.first.inner_text().replace("\n", " ") if has_bucket else ""
        shots.append(shot(page, "s02-buckets"))
        record("存储管理/桶", has_bucket and "检查通过" in btext and not errors_on(page), f"mor-platform-files 行：{btext[:160]}")

        check_ok, note = False, "未执行"
        if has_bucket:
            bucket.first.get_by_role("button", name=re.compile(r"^检\s?查$")).click()
            dialog = page.locator(".ant-modal", has_text="检查桶")
            visible(dialog, 5000)
            try:
                with page.expect_response(lambda r: CHECK_PATH in r.url and r.request.method == "POST", timeout=30000) as info:
                    dialog.get_by_role("button", name=re.compile("开始检查")).click()
                response = info.value
                body = response.json() if response.status == 200 else {}
                shown = visible(dialog.locator(".ant-alert-success", has_text="检查通过"), 10000)
                check_ok = response.status == 200 and body.get("result") == "OK" and shown
                note = f"BFF {response.status}，result={body.get('result')}，弹窗{'显示检查通过' if shown else '未显示检查通过'}"
            except Exception as failure:  # noqa: BLE001
                note = f"未取得检查响应：{type(failure).__name__}"
            shots.append(shot(page, "s03-bucket-check"))
            dialog.locator(".ant-modal-footer").get_by_role("button", name=re.compile(r"^关\s?闭$")).click()
            settle(page)
        record("存储管理/桶检查（真实点击）", check_ok, note)

        current["path"] = "/file-storage#routes"
        page.get_by_role("tab", name="写入规则").click()
        settle(page)
        route = page.locator(".ant-table-tbody tr.ant-table-row", has_text="默认")
        has_route = visible(route, 8000)
        rtext = route.first.inner_text().replace("\n", " ") if has_route else ""
        deletable = visible(route.get_by_role("button", name=re.compile(r"^删\s?除$")), 1500) if has_route else False
        shots.append(shot(page, "s04-routes"))
        record("存储管理/写入规则", has_route and "mor-platform-files" in rtext and "CLIENT_ONBOARDING_FORMAT" in rtext and not deletable
               and not errors_on(page), f"默认行：{rtext[:160]}；删除按钮{'可见' if deletable else '不可见'}")

        # ---------- 2. 回归 ----------
        goto(f"/client-onboarding/reviews/{case_id}")
        button = page.get_by_role("button", name=re.compile(r"下载文件 \d+"))
        has_button = visible(button, 10000)
        shots.append(shot(page, "r01-review-detail"))
        download_ok, note, grant_body = False, "没有下载按钮", {}
        if has_button:
            label = button.first.inner_text()
            toasts = []
            try:
                with page.expect_response(lambda r: "/files/" in r.url and r.url.split("?")[0].endswith("/download"), timeout=20000) as info:
                    try:
                        with page.expect_download(timeout=15000) as dl:
                            button.first.click()
                            # 错误提示 3 秒后消失，点击后立即抓取。
                            page.wait_for_timeout(800)
                            toasts = errors_on(page)
                        download = dl.value
                        size = Path(download.path()).stat().st_size
                        fetched = f"浏览器下载 {download.suggested_filename}（{size} 字节）"
                        got = size > 0
                    except Exception:  # noqa: BLE001
                        fetched, got = "页面没有发起下载", False
                response = info.value
                grant_body = response.json() if response.status == 200 else {}
                download_ok = response.status == 200 and got and not toasts
                precise = bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?Z", grant_body.get("expiresAt", "")))
                note = (f"{label}：BFF 签发 {response.status}；{fetched}；错误提示 {toasts or '无'}；"
                        f"expiresAt={grant_body.get('expiresAt')}（ops-web 解码要求小数位 ≤ 6：{'满足' if precise else '不满足'}）")
            except Exception as failure:  # noqa: BLE001
                note = f"{label}：未取得签发响应 {type(failure).__name__}"
        record("回归/客户入驻审核材料下载（页面按钮）", download_ok, note)

        # 页面按钮之外，单独确认签发地址本身能在浏览器下载（区分签发 / 存储问题与前端解码问题）。
        url_ok, note = False, "没有签发地址"
        if grant_body.get("url"):
            try:
                with page.expect_download(timeout=15000) as dl:
                    page.evaluate("u => { const a = document.createElement('a'); a.href = u; a.download = 'probe';"
                                  " document.body.appendChild(a); a.click(); a.remove(); }", grant_body["url"])
                size = Path(dl.value.path()).stat().st_size
                url_ok = str(size) == str(grant_body.get("sizeBytes"))
                note = f"浏览器经签发地址下载 {size} 字节（登记 {grant_body.get('sizeBytes')}）"
            except Exception as failure:  # noqa: BLE001
                note = f"签发地址下载失败 {type(failure).__name__}"
        record("回归/客户入驻审核材料签发地址可下载", url_ok, note)

        goto("/currency-specs")
        usd = visible(page.locator(".ant-table-tbody tr.ant-table-row", has_text="USD"), 10000)
        shots.append(shot(page, "r02-currency-specs"))
        record("回归/币种", usd and not errors_on(page), "USD 行" + ("可见" if usd else "缺失"))

        goto("/channel/mids")
        detail = page.locator(".ant-table-tbody tr.ant-table-row").first.get_by_role("button", name="详情")
        opened = False
        if visible(detail, 10000):
            detail.click()
            settle(page)
            opened = visible(page.get_by_role("tab", name="能力组"), 8000)
        current["path"] = page.url.replace(ORIGIN, "")
        shots.append(shot(page, "r03-channel-mid-detail"))
        record("回归/渠道 MID 详情", opened and not errors_on(page), f"{current['path']}，能力组页签{'可见' if opened else '缺失'}")

        goto("/external-api/integrations")
        rows = page.locator(".ant-table-tbody tr.ant-table-row")
        listed = visible(rows, 10000)
        shots.append(shot(page, "r04-external-integrations"))
        record("回归/外部接入方", listed and not errors_on(page), f"列表 {rows.count()} 行")
        browser.close()
    return dump(results, console, network, shots, minio)


def dump(results, console, network, shots, minio):
    relevant = [c for c in console if KNOWN not in c[2] and c[1] == "error"]
    warnings = [c for c in console if c[1] == "warning" and KNOWN not in c[2]]
    data = {"results": results, "consoleErrors": relevant, "consoleWarnings": warnings, "network": network,
            "minioResponses": minio, "shots": shots}
    (HERE / "stage7-browser-result.json").write_text(json.dumps(data, ensure_ascii=False, indent=2))
    passed = sum(1 for r in results if r["ok"])
    print(f"\n{passed}/{len(results)} 通过；控制台错误 {len(relevant)}，警告 {len(warnings)}；4xx/5xx/失败请求 {len(network)}；"
          f"MinIO 响应 {[m[1] for m in minio]}")
    for c in relevant:
        print("  console:", c[0], c[2][:300])
    for n in network:
        print("  network:", n)
    if passed != len(results) or relevant or network:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
