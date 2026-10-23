"""文件公开桶阶段 7：mor-test 浏览器冒烟（无头 Chromium，经运营入口 http://localhost:28080 真实登录；改写自文件多存储并存的同名脚本）。

只读：不点检查、保存、激活、下载等写操作（这些由 stage7_acceptance.py 经接口覆盖）。
1. 平台配置 → 存储管理四个页签：存储实例（MINIO、S3_TEST）、桶（两个私有桶标“私有”，公开测试桶标“公开”并显示对外地址）、
   写入规则（来源 × 私有 / 公开矩阵，默认、client-console-bff、operations-console-bff 三行）、检查策略（两个存量策略，打开一个看修订时间线）；
2. 平台配置 → 文件记录：列表有数据；打开验收公开文件详情（?file=<id>），有版本历史、检查结果、上传记录与公开地址；
3. 审计日志页能打开并列出记录；
4. 判定：控制台错误 0，4xx/5xx/失败请求 0。截图写 screens/，结果写 stage7-browser-result.json。
管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。
用法：python3 -I stage7_browser.py [PB7_STATE/run-*.json]（缺省取最新一份；用其中的公开桶名与公开文件 ID）
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
STATE = Path(os.environ.get("PB7_STATE", Path.home() / "Code-MoR-backups/pubbucket-stage7"))
ERROR_SELECTORS = ".ant-alert-error, .ant-result-error, .ant-result-500, .ant-result-403, .ant-result-404, " \
                  ".ant-message-error, .ant-notification-notice-error"
KNOWN = "[antd: Spin] `tip` is deprecated"


def credentials():
    text = (ROOT / "test-environment/private/access.md").read_text()
    return re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1)


def run_record():
    if len(sys.argv) > 1:
        return json.loads(Path(sys.argv[1]).read_text())
    runs = sorted(STATE.glob("run-*.json"))
    if not runs:
        raise SystemExit("没有找到 stage7_acceptance.py 的运行记录；请先运行验收或传入 run-*.json")
    return json.loads(runs[-1].read_text())


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
    record = run_record()
    public_bucket = record.get("publicBucketName", "mor-platform-public")
    public_file = (record.get("files") or {}).get("public")
    results, console, network, shots = [], [], [], []

    def check(name, ok, note):
        results.append({"page": name, "ok": bool(ok), "note": note})
        print(f"[{'通过' if ok else '失败'}] {name}：{note}", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": 1512, "height": 900}, ignore_https_errors=True)
        page = context.new_page()
        current = {"path": "/"}
        page.on("console", lambda m: console.append((current["path"], m.type, m.text)) if m.type in ("error", "warning") else None)
        page.on("response", lambda r: network.append((current["path"], r.status, r.request.method, r.url.split("?")[0]))
                if r.status >= 400 else None)
        # 公开图片预览直接读公开地址（MinIO 私有 CA，已 ignore_https_errors）；其余失败都计入。
        page.on("requestfailed", lambda r: network.append((current["path"], "FAILED", r.method, r.url.split("?")[0] + " " + str(r.failure))))

        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(email)
        page.get_by_label("密码").fill(password)
        page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
        settle(page)
        logged_in = not visible(page.get_by_label("密码"), 2000)
        check("登录", logged_in, "管理员登录")
        if not logged_in:
            shots.append(shot(page, "00-login-failed"))
            browser.close()
            return dump(results, console, network, shots)

        def goto(path):
            current["path"] = path
            page.goto(ORIGIN + path)
            settle(page)

        def tab(name, path_hint):
            current["path"] = f"/file-storage#{path_hint}"
            page.get_by_role("tab", name=name).click()
            settle(page)

        # ---------- 1. 存储管理 ----------
        goto("/file-storage")
        rows = page.locator(".ant-table-tbody tr.ant-table-row")
        has_minio = visible(page.locator(".ant-table-tbody tr.ant-table-row", has_text="MINIO"), 10000)
        has_s3 = visible(page.locator(".ant-table-tbody tr.ant-table-row", has_text="S3_TEST"), 3000)
        tabs = [t for t in ("存储实例", "桶", "写入规则", "检查策略") if visible(page.get_by_role("tab", name=t), 3000)]
        shots.append(shot(page, "s01-instances"))
        check("存储管理/存储实例", has_minio and has_s3 and len(tabs) == 4 and not errors_on(page),
              f"MINIO {'有' if has_minio else '无'}、S3_TEST {'有' if has_s3 else '无'}；页签 {tabs}")

        tab("桶", "buckets")
        private_rows = [page.locator(".ant-table-tbody tr.ant-table-row", has_text=b) for b in ("mor-platform-files", "mor-test-platform-files-k7q2")]
        private_ok = all(visible(r, 8000) and "私有" in r.first.inner_text() for r in private_rows)
        public_row = page.locator(".ant-table-tbody tr.ant-table-row", has_text=public_bucket)
        public_text = public_row.first.inner_text().replace("\n", " ") if visible(public_row, 5000) else ""
        shots.append(shot(page, "s02-buckets"))
        check("存储管理/桶", private_ok and "公开" in public_text and "https://" in public_text and not errors_on(page),
              f"私有桶标签{'正确' if private_ok else '缺失'}；公开桶行：{public_text[:200]}")

        tab("写入规则", "routes")
        matrix = {s: visible(page.locator(".ant-table-tbody tr.ant-table-row", has_text=s), 5000)
                  for s in ("默认", "client-console-bff", "operations-console-bff")}
        headers = page.locator(".ant-table-thead").first.inner_text() if visible(page.locator(".ant-table-thead"), 3000) else ""
        shots.append(shot(page, "s03-routes"))
        check("存储管理/写入规则矩阵", all(matrix.values()) and "私有" in headers and "公开" in headers and not errors_on(page),
              f"行 {matrix}；表头含私有 / 公开：{'是' if '私有' in headers and '公开' in headers else '否'}")

        tab("检查策略", "policies")
        policies = {p_: visible(page.get_by_text(p_, exact=False), 8000) for p_ in ("CLIENT_ONBOARDING_FORMAT", "CARD_BIN_CSV")}
        opened = False
        if policies["CLIENT_ONBOARDING_FORMAT"]:
            page.get_by_text("CLIENT_ONBOARDING_FORMAT", exact=False).first.click()
            settle(page)
            opened = visible(page.get_by_text(re.compile(r"PLATFORM_FORMAT|修订")), 5000)
        shots.append(shot(page, "s04-scan-policies"))
        check("存储管理/检查策略", all(policies.values()) and opened and not errors_on(page),
              f"策略 {policies}；修订 / 检查要求{'可见' if opened else '未显示'}")

        # ---------- 2. 文件记录 ----------
        goto("/file-records")
        menu_entry = visible(page.locator(".ant-menu").get_by_text("文件记录"), 5000)
        listed = visible(page.locator(".ant-table-tbody tr.ant-table-row"), 10000)
        count = page.locator(".ant-table-tbody tr.ant-table-row").count()
        shots.append(shot(page, "f01-file-records"))
        check("文件记录/列表", menu_entry and listed and not errors_on(page), f"菜单{'可见' if menu_entry else '缺失'}；本页 {count} 行")
        if public_file:
            goto(f"/file-records?file={public_file}")
            sections = {s: visible(page.get_by_text(s, exact=True), 8000) for s in ("版本历史", "检查结果", "上传记录")}
            has_url = visible(page.get_by_text(re.compile(r"https://\S+")), 5000)
            shots.append(shot(page, "f02-file-record-detail"))
            check("文件记录/详情", all(sections.values()) and has_url and not errors_on(page),
                  f"分区 {sections}；公开地址{'可见' if has_url else '缺失'}")
        else:
            check("文件记录/详情", False, "运行记录没有公开文件 ID")

        # ---------- 3. 审计 ----------
        goto("/audit")
        audit_rows = visible(page.locator(".ant-table-tbody tr.ant-table-row"), 10000)
        shots.append(shot(page, "a01-audit"))
        check("审计日志", audit_rows and not errors_on(page), "列表" + ("有记录" if audit_rows else "为空"))
        browser.close()
    return dump(results, console, network, shots)


def dump(results, console, network, shots):
    relevant = [c for c in console if KNOWN not in c[2] and c[1] == "error"]
    warnings = [c for c in console if c[1] == "warning" and KNOWN not in c[2]]
    data = {"results": results, "consoleErrors": relevant, "consoleWarnings": warnings, "network": network, "shots": shots}
    (HERE / "stage7-browser-result.json").write_text(json.dumps(data, ensure_ascii=False, indent=2))
    passed = sum(1 for r in results if r["ok"])
    print(f"\n{passed}/{len(results)} 通过；控制台错误 {len(relevant)}，警告 {len(warnings)}；4xx/5xx/失败请求 {len(network)}")
    for c in relevant:
        print("  console:", c[0], c[2][:300])
    for n in network:
        print("  network:", n)
    if passed != len(results) or relevant or network:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
