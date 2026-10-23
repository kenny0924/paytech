"""运营控制台多语言迁移阶段 7：AC-01 中文零变化复核（沿用 verification/zh_compare.py 的抓取方法）。

迁移前：基线源码 /Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-base（main 9f210b5c，= 现网旧 ops-web）的 Vite（127.0.0.1:18312），
  /api/operations/** 经 Playwright 只读中继到 mor-test http://localhost:28080（改写 Origin）；
迁移后：发布后的 mor-test 现网 ops-web http://localhost:28080，本地选择 i18n.locale=zh-CN（真实 manifest 与公开桶语言包）。
两边都只放行 GET 与登录 / 刷新，其余非 GET 一律拦截（返回 503 并计数）。逐个菜单页（pageCapabilities 的 frontendRoute）
抓取面包屑、主内容与首行详情的可见文字（同一会话内应用内跳转，避免整页刷新触发 Gateway 限流），再逐行比较。
数据驱动的差异（记录数、相对时间、刷新时间）需人工判读，脚本按行列出。

用法：
  python3 -I stage7_zh_compare.py capture --target base   # 若 18312 未监听，脚本自行在基线目录启动 Vite，结束时停止
  python3 -I stage7_zh_compare.py capture --target live
  python3 -I stage7_zh_compare.py diff                     # 写 zhcmp-stage7-diff.json，打印差异页数与行数
  python3 -I stage7_zh_compare.py --dry-run                # 只核对路径、路由清单与凭据文件，不启动浏览器
输出：acceptance/zhcmp-stage7-{base,live}.json、zhcmp-stage7-diff.json（只含界面文字，不含凭据）。
"""
import argparse
import json
import re
import site
import socket
import subprocess
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True  # 不在发布包与前例目录留下 __pycache__
sys.path.insert(0, str(Path(__file__).resolve().parent))
import opsi18n_common as o  # noqa: E402

c = o.c
HERE = Path(__file__).resolve().parent
BASE_WEB = Path("/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-base/operations-console-web")
BASE_PORT = 18312
LIVE = "http://localhost:28080"
ALLOWED = ("/api/operations/auth/v1/login", "/api/operations/auth/v1/refresh")
GRAB = """() => { const pick = s => [...document.querySelectorAll(s)].map(e => e.innerText).join('\\n');
  return { lang: document.documentElement.lang, trail: pick('.console-trail'), main: pick('.console-content'),
           overlay: pick('.ant-drawer-open .ant-drawer-content, .ant-modal-wrap:not([style*="display: none"]) .ant-modal-content') }; }"""


def routes():
    text = (BASE_WEB / "src/navigation/pageCapabilities.ts").read_text()
    return re.findall(r'"frontendRoute": "([^"]+)"', text)


def listening(port):
    with socket.socket() as sock:
        return sock.connect_ex(("127.0.0.1", port)) == 0


def credentials():
    text = (c.TEST_ENV / "private/access.md").read_text()
    return re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1)


def capture(target):
    sys.path.append(site.getusersitepackages())
    from playwright.sync_api import sync_playwright  # noqa: PLC0415
    vite = None
    if target == "base" and not listening(BASE_PORT):
        vite = subprocess.Popen(["npx", "vite", "--host", "127.0.0.1", "--port", str(BASE_PORT), "--strictPort"], cwd=BASE_WEB,
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(60):
            if listening(BASE_PORT):
                break
            time.sleep(1)
    origin = f"http://localhost:{BASE_PORT}" if target == "base" else LIVE
    blocked, result = [], {}

    def relay(route):
        request = route.request
        path = request.url.split(origin, 1)[1]
        if request.method != "GET" and path.split("?")[0] not in ALLOWED:
            blocked.append((request.method, path.split("?")[0]))
            return route.fulfill(status=503, body="{}", content_type="application/json")
        if target == "live":
            return route.continue_()
        headers = {k: v for k, v in request.headers.items() if k.lower() not in ("host", "origin", "referer")}
        headers["origin"] = LIVE
        return route.fulfill(response=route.fetch(url=LIVE + path, headers=headers, max_redirects=0))

    email, password = credentials()
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch()
            context = browser.new_context(viewport={"width": 1440, "height": 900}, timezone_id="Asia/Dubai", locale="zh-CN")
            context.add_init_script("try { localStorage.setItem('i18n.locale', 'zh-CN'); } catch (e) {}")
            page = context.new_page()
            page.route(re.compile(rf"^{re.escape(origin)}/api/operations/"), relay)
            page.goto(origin + "/")
            page.wait_for_timeout(3000)
            result["__login"] = page.evaluate("() => document.body.innerText")
            page.get_by_label(re.compile("账号")).first.fill(email)
            page.get_by_label(re.compile("^密码$")).fill(password)
            page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
            page.wait_for_selector(".console-layout", timeout=30000)
            page.wait_for_timeout(2500)
            for route in routes():
                page.evaluate("r => { window.history.pushState({}, '', r); window.dispatchEvent(new PopStateEvent('popstate')); }", route)
                page.wait_for_timeout(3500)
                entry = {"page": page.evaluate(GRAB)}
                row = page.query_selector(".console-content .ant-table-tbody tr.clickable-row, .console-content .ant-table-tbody tr[data-row-key]")
                if row:
                    try:
                        row.click(timeout=4000)
                        page.wait_for_timeout(2500)
                        entry["detail"] = page.evaluate(GRAB)
                    except Exception as error:  # noqa: BLE001
                        entry["detailError"] = str(error)[:120]
                    page.keyboard.press("Escape")
                    page.wait_for_timeout(500)
                result[route] = entry
                print(target, route, len(entry["page"]["main"]), "detail" if "detail" in entry else "", flush=True)
            browser.close()
    finally:
        if vite:
            vite.terminate()
    out = HERE / f"zhcmp-stage7-{target}.json"
    out.write_text(json.dumps({"target": target, "capturedAt": c.now_iso(), "results": result, "blocked": blocked}, ensure_ascii=False, indent=1))
    print(f"写入 {out}；拦截的非 GET {len(blocked)} 个")
    return 0


def lines(entry):
    out = []
    for part in ("page", "detail"):
        for field in ("trail", "main", "overlay"):
            out += [f"{part}.{field}: {line.strip()}" for line in ((entry.get(part) or {}).get(field) or "").splitlines() if line.strip()]
    return out


def diff():
    base = json.loads((HERE / "zhcmp-stage7-base.json").read_text())["results"]
    live = json.loads((HERE / "zhcmp-stage7-live.json").read_text())["results"]
    report, total = {}, 0
    for route in sorted(set(base) | set(live)):
        if route == "__login":
            continue
        a, b = lines(base.get(route) or {}), lines(live.get(route) or {})
        removed, added = [x for x in a if x not in b], [x for x in b if x not in a]
        langs = ((base.get(route) or {}).get("page", {}).get("lang"), (live.get(route) or {}).get("page", {}).get("lang"))
        if removed or added or langs[1] != "zh-CN":
            report[route] = {"lang": langs, "onlyBase": removed[:40], "onlyLive": added[:40]}
            total += len(removed) + len(added)
    (HERE / "zhcmp-stage7-diff.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))
    print(f"比较 {len(set(base) - {'__login'})} 个页面：有差异 {len(report)} 页、{total} 行（数据驱动差异需人工判读）→ zhcmp-stage7-diff.json")
    for route, item in report.items():
        print(f"  {route} lang={item['lang']} 基线独有 {len(item['onlyBase'])} 行、现网独有 {len(item['onlyLive'])} 行")
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", nargs="?", choices=("capture", "diff"))
    parser.add_argument("--target", choices=("base", "live"))
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if args.dry_run:
        found = routes()
        ok = BASE_WEB.joinpath("node_modules/.bin/vite").exists() and len(found) >= 60 and (c.TEST_ENV / "private/access.md").exists()
        print(f"干跑：基线目录 {BASE_WEB}（node_modules/.bin/vite {'有' if BASE_WEB.joinpath('node_modules/.bin/vite').exists() else '缺'}）、"
              f"菜单页 {len(found)} 个、凭据文件{'存在' if (c.TEST_ENV / 'private/access.md').exists() else '缺失'}；"
              f"18312 当前{'已' if listening(BASE_PORT) else '未'}监听")
        return 0 if ok else 1
    if args.action == "capture":
        if not args.target:
            parser.error("capture 需要 --target base|live")
        return capture(args.target)
    if args.action == "diff":
        return diff()
    parser.error("需要 capture / diff 或 --dry-run")


if __name__ == "__main__":
    sys.exit(main())
