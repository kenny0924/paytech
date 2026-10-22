"""数据查询服务阶段 7：mor-test 浏览器验收（无头 Chromium，经运营入口 http://localhost:28080 真实登录）。

写法照搬文件多存储并存 / 币种发布的同名脚本。只经运营入口操作；mor-test MySQL 只做 root 只读 SELECT 核对。
场景：
 1 导航“数据查询 / 数据源 / 查询模板”；数据源列表。
 2 新建 ACC_FIXTURE_READER（默认库 mor_data_query_fixture，reader）：检查三步通过 → 保存 → 启用；详情密码“已保存（Vault）”。
 3 新建 ACC_FIXTURE_WRITER（writer）：账号只读不通过并列出 INSERT/UPDATE；保存为停用；启用被拒（预期 400）。
 4 新建 ACC_FIXTURE_INSTANCE（不填默认库，reader）：检查通过并启用。
 5 新建模板 ACC_REFUNDS：#{} / {{#if}} / {{#join}} / {{#each … separator=}} / @{}；STRING、LIST、FRAGMENT 参数；OFFSET；KEEP_EDGES 列；
   高亮、未闭合 {{#if 报错、点击定位、500 ms 防抖；改正后无错误；保存（变更说明）；启用状态。
 6 试运行：集合多值、片段下拉、分页与总数；实际 SQL、绑定值、结果表、屏蔽列。
 7 改正文保存为 v2；版本抽屉对比 v1/v2；以 v1 恢复为 v3。
 8 整实例模板 ACC_INSTANCE_NUMBERS：库.表 可保存可运行；未限定库名报错；mysql.user 报系统库错误。
 9 执行入口：ACC_REFUNDS 200 带行；超时模板（三表 CROSS JOIN SUM，超时 1 秒）400 DATA_QUERY_TIMEOUT。
数据库口令运行时从 test-environment/private/data-query-fixture.env、管理员口令从 test-environment/private/access.md 读取；
口令与 Token 只在内存中使用，不打印、不落盘。截图写 screens/，结果写 stage7-browser-result.json。
用法：python3 -I stage7_browser.py
"""
import json
import re
import site
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request
import uuid
from pathlib import Path

sys.path.append(site.getusersitepackages())
from playwright.sync_api import sync_playwright  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
ORIGIN = "http://localhost:28080"
OUT = HERE / "screens"
OUT.mkdir(exist_ok=True)
ADMIN = "/data-query-admin/v1"
FIXTURE_DB = "mor_data_query_fixture"
ERROR_SELECTORS = ".ant-alert-error, .ant-result-error, .ant-result-500, .ant-result-403, .ant-result-404, " \
                  ".ant-message-error, .ant-notification-notice-error"
KNOWN = "[antd: Spin] `tip` is deprecated"

REFUNDS_BODY_V1 = """SELECT refund_id, refund_no, client_id, refund_status, refund_amount, currency, create_time
FROM t_refund_fixture
{{#join " AND " prefix="WHERE "}}
  {{#if currency}}currency = #{currency}{{/if}}
  {{#if statuses}}({{#each statuses as s, i separator=" OR "}}refund_status = #{s}{{/each}}){{/if}}
{{/join}}
ORDER BY @{sortBy}"""
# 故意写错：第 4 行 {{#if currency}} 未闭合。
REFUNDS_BODY_BROKEN = REFUNDS_BODY_V1.replace("#{currency}{{/if}}", "#{currency}")
# 第 2 版：多返回一列 body_version，排序与条件不变。
REFUNDS_BODY_V2 = REFUNDS_BODY_V1.replace("refund_id, refund_no,", "refund_id, refund_no, 'v2' AS body_version,")
INSTANCE_BODY_OK = "SELECT n FROM mor_data_query_fixture.t_number_fixture\nWHERE n < 50\nORDER BY n"
INSTANCE_BODY_UNQUALIFIED = "SELECT n FROM t_number_fixture\nWHERE n < 50\nORDER BY n"
INSTANCE_BODY_SYSTEM = "SELECT user, host FROM mysql.user\nORDER BY user"
TIMEOUT_BODY = "SELECT SUM(a.n + b.n + c.n) AS total\nFROM t_number_fixture a\nCROSS JOIN t_number_fixture b\nCROSS JOIN t_number_fixture c"


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


def credentials():
    text = (ROOT / "test-environment/private/access.md").read_text()
    return re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1)


FIXTURE = read_env(ROOT / "test-environment/private/data-query-fixture.env")
TEST_ENV = read_env(ROOT / "test-environment/.env.test")


def sql_readonly(database, statement):
    """root 只读 SELECT；口令只经环境变量传入容器。"""
    assert statement.strip().upper().startswith(("SELECT", "SHOW"))
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={TEST_ENV['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql",
                             "-uroot", "-N", "-B", "--default-character-set=utf8mb4", database, "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def api(token, method, path, body=None, prefix="/api/operations"):
    headers = {"Origin": ORIGIN, "Accept": "application/json", "Authorization": "Bearer " + token}
    data = None
    if method != "GET":
        headers["Content-Type"] = "application/json"
        headers["Idempotency-Key"] = str(uuid.uuid4())
        data = json.dumps(body if body is not None else {}).encode()
    for attempt in range(10):
        request = urllib.request.Request(ORIGIN + prefix + path, data=data, headers=headers, method=method)
        started = time.time()
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                text = response.read().decode()
                return response.status, (json.loads(text) if text else {}), round(time.time() - started, 2)
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 9:
                time.sleep(int(failure.headers.get("Retry-After") or 5))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text), round(time.time() - started, 2)
            except ValueError:
                return failure.code, {"raw": text[:300]}, round(time.time() - started, 2)


def visible(locator, timeout=8000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def settle(page, extra=1000):
    try:
        page.wait_for_load_state("networkidle", timeout=15000)
    except Exception:  # noqa: BLE001
        pass
    page.wait_for_timeout(extra)


def errors_on(page):
    return [t.strip().replace("\n", " ")[:200] for t in page.locator(ERROR_SELECTORS).all_inner_texts() if t.strip()]


def choose(page, select, option):
    """antd Select：点开后点可见下拉中的选项。"""
    select.click()
    item = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option", has_text=option)
    item.first.wait_for(state="visible", timeout=5000)
    item.first.click()
    page.wait_for_timeout(200)


def main():
    email, password = credentials()
    state = {"path": "/", "token": None, "expected": []}
    results, console, network, aborted, shots, validations, keystrokes = [], [], [], [], [], [], []
    evidence = {}

    def record(name, ok, note):
        results.append({"scenario": name, "ok": bool(ok), "note": note})
        print(f"[{'通过' if ok else '失败'}] {name}：{note}", flush=True)

    def expect_error(method, path_part, status):
        """登记预期的 4xx：响应到达时按方法 + 路径片段 + 状态匹配一次。"""
        state["expected"].append({"method": method, "path": path_part, "status": status, "matched": None})

    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": 1680, "height": 1000})
        page = context.new_page()
        page.on("console", lambda m: console.append((state["path"], m.type, m.text)) if m.type in ("error", "warning") else None)
        page.on("pageerror", lambda e: console.append((state["path"], "pageerror", str(e))))
        page.on("dialog", lambda d: d.accept())  # 离开未保存草稿时浏览器的 beforeunload 确认

        def on_request(r):
            auth = r.headers.get("authorization")
            if auth and auth.startswith("Bearer ") and "/api/operations/" in r.url:
                state["token"] = auth[7:]
            if r.url.endswith(ADMIN + "/template-validations") and r.method == "POST":
                validations.append(time.time())
        page.on("request", on_request)

        def on_response(r):
            if r.status < 400:
                return
            url = r.url.split("?")[0]
            for item in state["expected"]:
                if item["matched"] is None and item["method"] == r.request.method and item["path"] in url and item["status"] == r.status:
                    try:
                        body = r.json()
                    except Exception:  # noqa: BLE001
                        body = {}
                    item["matched"] = {"url": url.replace(ORIGIN, ""), "code": body.get("code"), "detail": body.get("detail"),
                                       "errors": body.get("errors")}
                    return
            try:
                body = r.json()
            except Exception:  # noqa: BLE001
                body = {}
            network.append({"page": state["path"], "status": r.status, "method": r.request.method, "url": url.replace(ORIGIN, ""),
                            "code": body.get("code"), "detail": body.get("detail"), "errors": body.get("errors")})
        page.on("response", on_response)

        def on_failed(r):
            # 校验请求在新输入到达时被前端主动取消（AbortController），记为预期取消，不算失败。
            if r.url.endswith("/template-validations") and "ERR_ABORTED" in str(r.failure):
                aborted.append(r.url.replace(ORIGIN, ""))
                return
            network.append({"page": state["path"], "status": "FAILED", "method": r.method, "url": r.url.split("?")[0], "detail": str(r.failure)})
        page.on("requestfailed", on_failed)

        def shot(name):
            page.wait_for_timeout(500)
            page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
            shots.append(f"{name}.png")

        def goto(path):
            state["path"] = path
            page.goto(ORIGIN + path)
            settle(page)

        page.goto(ORIGIN + "/")
        page.get_by_label("Email / 初始化账号").fill(email)
        page.get_by_label("密码").fill(password)
        page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
        settle(page)
        logged_in = not visible(page.get_by_label("密码"), 2000)
        record("登录", logged_in, "管理员登录")
        if not logged_in:
            shot("00-login-failed")
            browser.close()
            return dump(results, console, network, aborted, shots, state, evidence)

        def run(name, fn):
            try:
                fn()
            except Exception as failure:  # noqa: BLE001
                record(name, False, f"脚本异常 {type(failure).__name__}: {str(failure)[:300]}")
                traceback.print_exc()
                try:
                    shot(f"x-{name.split(' ')[0]}-exception")
                    page.keyboard.press("Escape")
                except Exception:  # noqa: BLE001
                    pass

        source_ids = {}

        # ---------- 1. 导航与数据源列表 ----------
        def s1():
            goto("/data-query/data-sources")
            menu = page.locator(".ant-menu")
            group = visible(menu.get_by_text("数据查询", exact=True), 5000)
            subs = visible(menu.get_by_text("数据源", exact=True), 3000) and visible(menu.get_by_text("查询模板", exact=True), 3000)
            heading = visible(page.locator(".console-trail").get_by_text("数据源", exact=True), 5000)
            rows = page.locator(".dq-panel .ant-table-tbody tr.ant-table-row").count()
            empty = visible(page.get_by_text("没有符合条件的数据源"), 1000)
            errs = errors_on(page)
            shot("01-data-sources-list")
            record("1 导航与数据源列表", group and subs and heading and not errs,
                   f"菜单组{'可见' if group else '缺失'}，子页{'可见' if subs else '缺失'}，面包屑“数据查询 / 数据源”{'可见' if heading else '缺失'}；列表 {rows} 行"
                   f"{'（空状态）' if empty else ''}；错误提示 {errs or '无'}")
        run("1 导航与数据源列表", s1)

        def existing_source(code):
            status, body, _ = api(state["token"], "GET", ADMIN + f"/data-sources?keyword={code}&page=1&size=10")
            return next((item for item in body.get("items") or [] if item.get("dataSourceCode") == code), None) if status == 200 else None

        def create_source(code, name, database, user, secret, label):
            """经页面新建并测试连接；返回 (测试响应 JSON, 保存响应状态, dataSourceId)。"""
            page.get_by_role("button", name=re.compile("新建数据源")).click()
            modal = page.locator(".ant-modal", has_text="新建数据源")
            modal.wait_for(state="visible", timeout=5000)
            page.locator("#data-source-editor_dataSourceCode").fill(code)
            page.locator("#data-source-editor_dataSourceName").fill(name)
            page.locator("#data-source-editor_host").fill("mysql")
            page.locator("#data-source-editor_port").fill("3306")
            if database:
                page.locator("#data-source-editor_databaseName").fill(database)
            page.locator("#data-source-editor_username").fill(user)
            page.locator("#data-source-editor_password").fill(secret)
            page.locator("#data-source-editor_description").fill(f"阶段 7 浏览器验收：{label}")
            with page.expect_response(lambda r: r.url.endswith(ADMIN + "/data-source-connection-tests") and r.request.method == "POST",
                                      timeout=30000) as info:
                modal.get_by_role("button", name=re.compile("测试连接")).click()
            test = info.value.json() if info.value.status == 200 else {"status": info.value.status}
            page.wait_for_timeout(800)
            return modal, test

        def save_source(modal):
            with page.expect_response(lambda r: r.url.endswith(ADMIN + "/data-sources") and r.request.method == "POST", timeout=30000) as info:
                modal.locator(".dq-modal-footer").get_by_role("button", name=re.compile(r"^保\s?存$")).click()
            body = info.value.json() if info.value.status == 200 else {}
            settle(page, 800)
            return info.value.status, body

        def select_row(code):
            """新建后列表选中项不跟随新数据源（见缺陷记录），点行选中并等待右侧详情。"""
            settle(page, 500)
            page.locator(".dq-panel .ant-table-tbody tr.ant-table-row", has_text=code).first.click()
            detail = page.locator(".dq-detail")
            if not visible(detail.locator("h2", has_text=re.compile(rf"^{code}$")), 8000):
                raise RuntimeError(f"右侧详情没有切到 {code}")
            page.wait_for_timeout(500)
            return detail

        def enable_selected(code, expect_status=200):
            detail = select_row(code)
            detail.get_by_role("button", name=re.compile(r"^启\s?用$")).click()
            dialog = page.locator(".ant-modal", has_text=f"启用数据源 · {code}")
            dialog.wait_for(state="visible", timeout=5000)
            with page.expect_response(lambda r: "/data-sources/" in r.url and r.url.endswith("/status") and r.request.method == "POST",
                                      timeout=30000) as info:
                dialog.get_by_role("button", name=re.compile("确认启用")).click()
            page.wait_for_timeout(1200)
            response = info.value
            body = response.json() if response.status != 204 else {}
            return response.status, body, dialog

        def step_states():
            return [cls.split("is-")[-1] for cls in
                    page.locator(".dq-check-panel .dq-step").evaluate_all("els => els.map(e => e.className)")]

        # ---------- 2. reader 数据源 ----------
        def s2():
            goto("/data-query/data-sources")
            code = "ACC_FIXTURE_READER"
            found = existing_source(code)
            if found:
                source_ids[code] = found["dataSourceId"]
                record("2 新建 reader 数据源", found["status"] == "ACTIVE", f"已存在（重复运行），状态 {found['status']}，跳过新建")
                return
            modal, test = create_source(code, "验收 Fixture 只读", FIXTURE_DB, "data_query_fixture_reader",
                                        FIXTURE["MOR_DATA_QUERY_FIXTURE_READER_PASSWORD"], "reader 默认库")
            steps = step_states()
            shot("02-reader-editor-check-passed")
            ok_test = test.get("result") == "PASSED" and test.get("connected") and test.get("privilegesPassed") and steps == ["passed"] * 3
            status, saved = save_source(modal)
            source_ids[code] = saved.get("objectId")
            en_status, en_body, _ = enable_selected(code)
            settle(page)
            detail = page.locator(".dq-detail")
            vault = visible(detail.get_by_text("已保存（Vault）"), 5000)
            active = visible(detail.locator(".dq-detail-head .ant-tag", has_text="启用"), 5000)
            dbshown = visible(detail.get_by_text(FIXTURE_DB), 2000)
            shot("03-reader-detail-active")
            evidence["reader"] = {"test": {k: test.get(k) for k in ("result", "connected", "privilegesPassed", "grants", "violations")},
                                  "save": [status, saved], "enable": [en_status, en_body]}
            record("2 新建 reader 数据源", ok_test and status == 200 and saved.get("status") == "INACTIVE" and en_status == 200
                   and en_body.get("status") == "ACTIVE" and vault and active and dbshown and not errors_on(page),
                   f"检查 {test.get('result')}，三步 {steps}，grants={test.get('grants')}；保存 {status}/{saved.get('status')} v{saved.get('version')}；"
                   f"启用 {en_status}/{en_body.get('status')}；密码“已保存（Vault）”{'显示' if vault else '缺失'}；默认库{'显示' if dbshown else '缺失'}")
        run("2 新建 reader 数据源", s2)

        # ---------- 3. writer 数据源 ----------
        def s3():
            goto("/data-query/data-sources")
            code = "ACC_FIXTURE_WRITER"
            found = existing_source(code)
            created = None
            if not found:
                modal, draft_test = create_source(code, "验收 Fixture 读写（应拒绝启用）", FIXTURE_DB, "data_query_fixture_writer",
                                                  FIXTURE["MOR_DATA_QUERY_FIXTURE_WRITER_PASSWORD"], "writer 应不通过")
                shot("04a-writer-new-check-failed")
                save_enabled = modal.locator(".dq-modal-footer").get_by_role("button", name=re.compile(r"^保\s?存$")).is_enabled()
                status, saved = save_source(modal)
                created = {"draftTest": draft_test.get("result"), "saveEnabled": save_enabled, "save": [status, saved.get("status")]}
                found = existing_source(code)
            source_ids[code] = found["dataSourceId"]
            # 编辑弹窗：密码留空，用已保存的 writer 密码测试（带 dataSourceId，结果回写最后一次检查）。
            detail = select_row(code)
            detail.get_by_role("button", name=re.compile(r"^编\s?辑$")).click()
            modal = page.locator(".ant-modal", has_text=f"编辑数据源 · {code}")
            modal.wait_for(state="visible", timeout=5000)
            with page.expect_response(lambda r: r.url.endswith(ADMIN + "/data-source-connection-tests") and r.request.method == "POST",
                                      timeout=30000) as info:
                modal.get_by_role("button", name=re.compile("测试连接")).click()
            test = info.value.json() if info.value.status == 200 else {"status": info.value.status}
            page.wait_for_timeout(800)
            steps = step_states()
            red = page.locator(".dq-check-panel .dq-grant .is-violation").all_inner_texts()
            listed = page.locator(".dq-check-panel .dq-violations li").all_inner_texts()
            shot("04-writer-editor-check-failed")
            modal.locator(".dq-modal-footer").get_by_role("button", name=re.compile(r"^取\s?消$")).click()
            page.wait_for_timeout(500)
            violations = " ".join(test.get("violations") or []) + " " + " ".join(red)
            ok_test = (test.get("result") == "FAILED" and test.get("connected") and test.get("privilegesPassed") is False
                       and "INSERT" in violations and "UPDATE" in violations and steps[:2] == ["passed", "failed"])
            expect_error("POST", f"/data-sources/{found['dataSourceId']}/status", 400)
            en_status, en_body, dialog = enable_selected(code)
            dialog_error = dialog.locator(".dq-note, .ant-alert").all_inner_texts()
            shot("05-writer-enable-rejected")
            if visible(dialog, 500):
                dialog.get_by_role("button", name=re.compile(r"^取\s?消$")).click()
                page.wait_for_timeout(500)
            after = existing_source(code) or {}
            evidence["writer"] = {"created": created, "test": {k: test.get(k) for k in ("result", "connected", "privilegesPassed", "grants", "violations")},
                                  "uiRed": red, "uiViolations": listed, "enable": [en_status, en_body.get("code"), en_body.get("detail")],
                                  "dialog": dialog_error, "after": [after.get("status"), after.get("lastCheckResult"), after.get("lastCheckMessage")]}
            record("3 writer 数据源检查不通过", ok_test and en_status == 400 and after.get("status") == "INACTIVE" and after.get("lastCheckResult") == "FAILED",
                   f"新建 {created or '已存在'}；检查 {test.get('result')}，三步 {steps}，标红 {red}，违规 {test.get('violations')}；"
                   f"启用被拒 {en_status} {en_body.get('code')}：{en_body.get('detail')}；弹窗提示 {dialog_error}；"
                   f"最终 {after.get('status')}/{after.get('lastCheckResult')}：{after.get('lastCheckMessage')}")
        run("3 writer 数据源检查不通过", s3)

        # ---------- 4. 整实例数据源 ----------
        def s4():
            goto("/data-query/data-sources")
            code = "ACC_FIXTURE_INSTANCE"
            found = existing_source(code)
            created = None
            if not found:
                modal, test = create_source(code, "验收 Fixture 整实例", None, "data_query_fixture_reader",
                                            FIXTURE["MOR_DATA_QUERY_FIXTURE_READER_PASSWORD"], "reader 整实例")
                created = {"test": test.get("result"), "steps": step_states()}
                shot("06a-instance-new-check-passed")
                status, saved = save_source(modal)
                created["save"] = [status, saved.get("status")]
                found = existing_source(code)
            source_ids[code] = found["dataSourceId"]
            if found["status"] != "ACTIVE":
                en_status, en_body, _ = enable_selected(code)
            else:
                en_status, en_body = "已启用", {"status": "ACTIVE"}
            settle(page)
            select_row(code)
            whole = visible(page.locator(".dq-detail").get_by_text("整个实例"), 3000)
            row = page.locator(".dq-panel .ant-table-tbody tr.ant-table-row", has_text=code).first.inner_text().replace("\n", " ")
            shot("06-instance-detail-active")
            ok_create = created is None or (created["test"] == "PASSED" and created["steps"] == ["passed"] * 3 and created["save"][0] == 200)
            record("4 整实例数据源", ok_create and en_body.get("status") == "ACTIVE" and whole,
                   f"新建 {created or '已存在（上一轮已新建并检查通过）'}；启用 {en_status}/{en_body.get('status')}；列表行：{row[:140]}；"
                   f"详情“整个实例”{'显示' if whole else '缺失'}")
        run("4 整实例数据源", s4)

        # ---------- 复验 R2：列表已有数据源时新建，保存后右侧详情与列表高亮跟随新建项 ----------
        def r2():
            goto("/data-query/data-sources")
            code = "ACC_FIXTURE_SELECT2"
            found = existing_source(code)
            if found:
                # 重复运行时改为修改该数据源（说明字段），同样检查保存后选中跟随。
                select_row("ACC_FIXTURE_READER")
                select_row(code)
                page.locator(".dq-detail").get_by_role("button", name=re.compile(r"^编\s?辑$")).click()
                modal = page.locator(".ant-modal", has_text=f"编辑数据源 · {code}")
                modal.wait_for(state="visible", timeout=5000)
                page.locator("#data-source-editor_description").fill(f"阶段 7 复验：选中跟随 {time.strftime('%H:%M:%S')}")
                with page.expect_response(lambda r: f"/data-sources/{found['dataSourceId']}" in r.url and r.request.method == "PUT", timeout=30000) as info:
                    modal.locator(".dq-modal-footer").get_by_role("button", name=re.compile(r"^保\s?存$")).click()
                status, saved, how = info.value.status, info.value.json(), "修改"
                settle(page, 800)
            else:
                before_selected = page.locator(".dq-detail h2").inner_text() if page.locator(".dq-detail h2").count() else ""
                modal, test = create_source(code, "验收 选中跟随", FIXTURE_DB, "data_query_fixture_reader",
                                            FIXTURE["MOR_DATA_QUERY_FIXTURE_READER_PASSWORD"], "复验：新建后选中跟随（保存为停用）")
                status, saved = save_source(modal)
                how = f"新建（新建前选中 {before_selected}，检查 {test.get('result')}）"
            page.wait_for_timeout(1500)
            detail_code = page.locator(".dq-detail h2").inner_text() if page.locator(".dq-detail h2").count() else ""
            highlighted = page.locator(".dq-panel tr.dq-selected-row").inner_text().replace("\n", " ") if page.locator(".dq-panel tr.dq-selected-row").count() else ""
            rows = page.locator(".dq-panel .ant-table-tbody tr.ant-table-row").count()
            shot("07-data-sources-list-after")
            evidence["selectFollow"] = {"how": how, "save": [status, saved], "detail": detail_code, "highlighted": highlighted, "rows": rows}
            record("R2 新建/修改后选中跟随", status == 200 and detail_code == code and code in highlighted and rows > 1,
                   f"{how}：保存 {status}/{saved.get('status')} v{saved.get('version')}；右侧详情 {detail_code}；高亮行“{highlighted[:100]}”；列表 {rows} 行")
        run("R2 新建/修改后选中跟随", r2)

        # ---------- 编辑器工具 ----------
        def set_body(text):
            content = page.locator(".dq-code .cm-content")
            content.click()
            page.keyboard.press("ControlOrMeta+a")
            page.keyboard.press("Backspace")
            page.keyboard.insert_text(text)
            page.keyboard.press("Escape")  # 关闭可能弹出的补全

        def wait_validation(since_count, timeout=8000):
            deadline = time.time() + timeout / 1000
            while time.time() < deadline:
                if len(validations) > since_count and not visible(page.locator(".dq-problem-status"), 100):
                    page.wait_for_timeout(300)
                    return True
                page.wait_for_timeout(100)
            return False

        def problem_texts(level):
            return [t.replace("\n", " ") for t in page.locator(f".dq-problems .dq-problem.is-{level}").all_inner_texts()]

        def choose_source(code):
            choose(page, page.locator(".dq-source-select"), code)

        def add_parameter(name, kind):
            panel = page.locator(".dq-editor-right")
            panel.get_by_role("tab", name=re.compile("参数")).click()
            panel.get_by_role("button", name=re.compile("添加参数")).click()
            row = panel.locator(".dq-param-item").last
            row.locator(".dq-param-line input.ant-input").fill(name)
            if kind != "STRING":
                choose(page, row.locator(".dq-param-line .ant-select"), kind)
            return row

        def constraint_input(panel, label):
            return panel.locator(".dq-constraint").filter(has=page.locator("label.dq-labeled > span", has_text=re.compile(rf"^{re.escape(label)}$"))).locator("input").first

        def labeled_input(row, label):
            return row.locator("label.dq-labeled", has_text=label).locator("input").first

        def save_template(note, method):
            page.get_by_role("button", name=re.compile(r"保存为 v\d+")).click()
            modal = page.locator(".ant-modal", has=page.locator(".ant-modal-title", has_text=re.compile(r"保存为 v\d+")))
            modal.wait_for(state="visible", timeout=5000)
            modal.locator("textarea").fill(note)
            with page.expect_response(lambda r: "/data-query-admin/v1/templates" in r.url and r.request.method == method
                                      and "/status" not in r.url, timeout=30000) as info:
                modal.get_by_role("button", name=re.compile("保存并生效")).click()
            response = info.value
            body = response.json()
            settle(page, 1500)
            return response.status, body

        template_ids = {}

        def open_template(code):
            template_id = template_ids.get(code)
            goto(f"/data-query/templates/{template_id}")
            page.locator(".dq-code .cm-content").wait_for(state="visible", timeout=10000)
            page.wait_for_timeout(800)

        # ---------- 复验 R1：新建草稿默认约束与上限提示 ----------
        def r1():
            goto("/data-query/templates/new")
            before = len(validations)
            choose_source("ACC_FIXTURE_READER")
            wait_validation(before)
            page.wait_for_timeout(800)
            problems = problem_texts("error") + problem_texts("warning")
            badge = page.locator(".dq-bytes").inner_text()
            panel = page.locator(".dq-editor-right")
            panel.get_by_role("tab", name="约束").click()
            page.wait_for_timeout(300)
            timeout_value = constraint_input(panel, "超时（秒）").input_value()
            hint = panel.locator(".dq-constraint").filter(has=page.locator("label.dq-labeled > span", has_text=re.compile(r"^超时（秒）$"))).locator("small").inner_text()
            shot("08b-template-new-default-draft")
            evidence["defaultDraftProblems"] = problems
            evidence["defaultDraft"] = {"timeoutSeconds": timeout_value, "hint": hint, "badge": badge}
            record("R1 新建草稿默认约束", not any("timeoutSeconds" in t or "超时" in t for t in problems) and timeout_value == "5"
                   and hint.strip() == "1–8" and "/ 48 KB" in badge,
                   f"默认超时 {timeout_value}；提示“{hint}”；正文角标“{badge}”；问题面板 {problems}")
        run("R1 新建草稿默认约束", r1)

        # ---------- 5. 模板 ACC_REFUNDS ----------
        def editor_checks():
            # 故意写错：未闭合 {{#if
            before = len(validations)
            set_body(REFUNDS_BODY_BROKEN)
            wait_validation(before)
            page.wait_for_timeout(800)
            highlight = {cls: page.locator(f".dq-code .{cls}").count() for cls in ("dq-tpl-value", "dq-tpl-fragment", "dq-tpl-tag")}
            errors = problem_texts("error")
            lint = page.locator(".dq-code .cm-lintRange-error, .dq-code .cm-lint-marker-error").count()
            shot("09-template-editor-broken")
            located, active_line = False, ""
            if errors:
                page.locator(".dq-problems .dq-problem.is-error").first.get_by_role("button", name="定位").click()
                page.wait_for_timeout(500)
                active_line = page.locator(".dq-code .cm-activeLine").first.inner_text() if page.locator(".dq-code .cm-activeLine").count() else ""
                located = page.evaluate("() => !!document.activeElement && !!document.activeElement.closest('.cm-editor')")
            shot("10-template-editor-located")
            where = re.search(r"(\d+)\s*:\s*(\d+)", errors[0]) if errors else None
            expected_line = REFUNDS_BODY_BROKEN.splitlines()[int(where.group(1)) - 1].strip() if where else "?"

            # 防抖：逐键输入，按键间隔 80 ms；期间不应发出校验，停顿约 500 ms 后发出一次。
            page.locator(".dq-code .cm-content").click()
            page.keyboard.press("ControlOrMeta+End")
            before = len(validations)
            start = time.time()
            for ch in "\n{{!-- debounce --}}":
                page.keyboard.type(ch)
                keystrokes.append(time.time())
                page.wait_for_timeout(80)
            last_key = keystrokes[-1]
            during = [t for t in validations[before:] if t <= last_key + 0.05]
            wait_validation(before)
            after = [t for t in validations[before:] if t > last_key]
            delay = round((after[0] - last_key) * 1000) if after else None
            evidence["debounce"] = {"typingMillis": round((last_key - start) * 1000), "requestsDuringTyping": len(during),
                                    "requestsAfter": len(after), "firstDelayMillis": delay, "abortedSoFar": len(aborted)}
            # 改正
            before = len(validations)
            set_body(REFUNDS_BODY_V1)
            wait_validation(before)
            page.wait_for_timeout(800)
            fixed_errors = problem_texts("error")
            passed = visible(page.locator(".dq-problems .dq-ok", has_text="校验通过"), 3000)
            warnings_ui = problem_texts("warning")
            shot("11-template-editor-fixed")
            record("5a 编辑器高亮 / 问题面板 / 定位 / 防抖",
                   all(highlight.values()) and errors and located and active_line.strip() == expected_line and not fixed_errors
                   and len(during) == 0 and len(after) >= 1 and delay is not None and 450 <= delay <= 1500,
                   f"高亮 {highlight}；未闭合时错误 {errors}；lint 标记 {lint}；定位后光标行“{active_line.strip()[:80]}”（焦点在编辑器 {located}）；"
                   f"防抖 {evidence['debounce']}；改正后错误 {fixed_errors}，警告 {warnings_ui}，“校验通过”{'显示' if passed else '未显示'}")

        def s5():
            goto("/data-query/templates")
            shot("08-templates-list-before")
            status, body, _ = api(state["token"], "GET", ADMIN + "/templates?keyword=ACC_REFUNDS&page=1&size=10")
            found = next((i for i in body.get("items") or [] if i.get("templateCode") == "ACC_REFUNDS"), None)
            if found:
                # 重复运行：在已保存模板上只做编辑器检查（最后把正文改回与当前版本相同，不保存）。
                template_ids["ACC_REFUNDS"] = found["templateId"]
                open_template("ACC_REFUNDS")
                editor_checks()
                record("5b 保存 ACC_REFUNDS v1（启用）", found["status"] == "ACTIVE",
                       f"已于首轮保存（重复运行未再保存），当前 v{found['currentVersionNo']}，状态 {found['status']}")
                return
            page.get_by_role("button", name=re.compile("新建模板")).click()
            settle(page)
            state["path"] = "/data-query/templates/new"
            page.locator(".dq-code-input").fill("ACC_REFUNDS")
            page.locator(".dq-name-input").fill("验收：退款查询")
            page.locator(".dq-description-input").fill("阶段 7 浏览器验收：#{} / if / join / each / 片段 / 屏蔽")
            before = len(validations)
            choose_source("ACC_FIXTURE_READER")
            # 观察：新建草稿的默认约束（前端默认超时 10 秒，合同上限 8 秒）在校验中的表现。
            wait_validation(before)
            page.wait_for_timeout(600)
            evidence["defaultDraftProblems"] = problem_texts("error") + problem_texts("warning")
            shot("08b-template-new-default-draft")
            # 参数：STRING、LIST<STRING>、FRAGMENT
            add_parameter("currency", "STRING")
            row = add_parameter("statuses", "LIST")
            labeled_input(row, "最多个数").fill("10")
            row = add_parameter("sortBy", "FRAGMENT")
            row.locator(".dq-param-line .ant-checkbox-input").check()
            labeled_input(row, "默认值").fill("NEWEST")
            for value, fragment in (("NEWEST", "create_time DESC, refund_id DESC"), ("OLDEST", "create_time ASC, refund_id ASC")):
                row.get_by_role("button", name=re.compile("添加取值")).click()
                choice = row.locator(".dq-choice-row").last.locator("input")
                choice.nth(0).fill(value)
                choice.nth(1).fill(fragment)
            # 约束：OFFSET（默认），超时 5 秒，页大小上限 100，允许总数
            panel = page.locator(".dq-editor-right")
            panel.get_by_role("tab", name="约束").click()
            constraint_input(panel, "超时（秒）").fill("5")
            constraint_input(panel, "最大行数").fill("1000")
            offset = panel.locator(".dq-constraint", has_text="分页方式").locator(".ant-segmented-item", has_text="偏移")
            offset.click()
            # 列：refund_no 保留首 3 尾 2
            panel.get_by_role("tab", name=re.compile("列")).click()
            panel.get_by_role("button", name=re.compile("添加列")).click()
            col = panel.locator(".dq-column-row").last
            col.locator("input.ant-input").nth(0).fill("refund_no")
            col.locator("input.ant-input").nth(1).fill("退款号")
            choose(page, col.locator(".ant-select"), "保留首尾")
            keeps = col.locator(".dq-keep input")
            keeps.nth(0).fill("3")
            keeps.nth(1).fill("2")
            panel.get_by_role("tab", name=re.compile("参数")).click()

            editor_checks()
            status, saved = save_template("验收 v1：退款查询初版", "POST")
            template_ids["ACC_REFUNDS"] = saved.get("templateId")
            state["path"] = f"/data-query/templates/{saved.get('templateId')}"
            active = visible(page.locator(".dq-editor-title .ant-tag", has_text="启用"), 5000)
            v1 = visible(page.locator(".dq-editor-title .ant-tag", has_text="当前 v1"), 5000)
            shot("12-template-saved-v1")
            evidence["refundsSave"] = [status, saved]
            record("5b 保存 ACC_REFUNDS v1（启用）", status == 200 and saved.get("currentVersionNo") == 1 and saved.get("status") == "ACTIVE"
                   and active and v1 and "/data-query/templates/new" not in page.url,
                   f"保存 {status} {json.dumps(saved, ensure_ascii=False)[:300]}；顶栏 启用{'✓' if active else '✕'} 当前 v1{'✓' if v1 else '✕'}；URL {page.url.replace(ORIGIN, '')}")
        run("5 模板 ACC_REFUNDS", s5)

        # ---------- 6. 试运行 ----------
        def s6():
            open_template("ACC_REFUNDS")
            page.locator(".dq-bottom").get_by_role("tab", name="试运行").click()
            trial = page.locator(".dq-trial")
            trial.locator(".dq-trial-field", has_text="currency").locator("input").fill("USD")
            tags = trial.locator(".dq-trial-field", has_text="statuses").locator(".ant-select")
            tags.click()
            for value in ("SUCCEEDED", "PROCESSING"):
                page.keyboard.type(value)
                page.keyboard.press("Enter")
            page.keyboard.press("Escape")
            choose(page, trial.locator(".dq-trial-field", has_text="sortBy").locator(".ant-select"), "OLDEST")
            fragment_options = None
            paging = trial.locator(".dq-trial-paging input.ant-input-number-input")
            paging.nth(0).fill("1")
            paging.nth(1).fill("3")
            total_box = trial.locator(".dq-trial-paging .ant-checkbox-input")
            total_box.check()
            with page.expect_response(lambda r: r.url.endswith(ADMIN + "/template-trials") and r.request.method == "POST", timeout=30000) as info:
                trial.get_by_role("button", name=re.compile("试运行")).click()
            response = info.value
            result = response.json() if response.status == 200 else {"status": response.status, **response.json()}
            page.wait_for_timeout(1000)
            ui_sql = trial.locator("pre.dq-sql").first.inner_text() if trial.locator("pre.dq-sql").count() else ""
            ui_bind = trial.locator(".dq-bindings").inner_text() if trial.locator(".dq-bindings").count() else ""
            ui_rows = trial.locator(".dq-trial-output .ant-table-tbody tr.ant-table-row").count()
            ui_summary = trial.locator(".dq-trial-summary").inner_text().replace("\n", " ") if trial.locator(".dq-trial-summary").count() else ""
            shot("13-template-trial-result")
            expected = sql_readonly(FIXTURE_DB, "SELECT refund_no FROM t_refund_fixture WHERE currency='USD' AND refund_status IN "
                                                "('SUCCEEDED','PROCESSING') ORDER BY create_time ASC, refund_id ASC")
            names = [c.get("name") for c in result.get("columns") or []]
            idx = names.index("refund_no") if "refund_no" in names else None
            masked = [row[idx] for row in result.get("rows") or []] if idx is not None else []
            plain = [r[0] for r in expected[:3]]
            mask_ok = bool(masked) and all(m != o and m[:3] == o[:3] and m[-2:] == o[-2:] and "*" in m for m, o in zip(masked, plain))
            header = trial.locator(".dq-trial-output .ant-table-thead th").all_inner_texts()
            # 下一页
            with page.expect_response(lambda r: r.url.endswith(ADMIN + "/template-trials"), timeout=30000) as info2:
                trial.get_by_role("button", name=re.compile("下一页")).click()
            page2 = info2.value.json() if info2.value.status == 200 else {}
            page.wait_for_timeout(600)
            evidence["trial"] = {"sql": result.get("sql"), "countSql": result.get("countSql"), "bindings": result.get("bindings"),
                                 "columns": names, "rows": result.get("rows"), "total": result.get("total"), "hasMore": result.get("hasMore"),
                                 "page2Rows": page2.get("rows"), "expectedPlain": [r[0] for r in expected]}
            record("6 试运行", response.status == 200 and result.get("total") == len(expected) and len(result.get("rows") or []) == 3
                   and result.get("hasMore") is True and "LIMIT" in (result.get("sql") or "").upper() and "ASC" in (result.get("sql") or "")
                   and result.get("bindings", [])[:3] == ["USD", "SUCCEEDED", "PROCESSING"] and mask_ok and ui_rows == 3
                   and "?" in ui_sql and "USD" in ui_bind and "退款号" in " ".join(header) and len(page2.get("rows") or []) == 3,
                   f"{response.status}；SQL：{result.get('sql')}；绑定 {result.get('bindings')}；总数 {result.get('total')}（库中 {len(expected)}），"
                   f"hasMore={result.get('hasMore')}；refund_no 屏蔽 {masked} ↔ 原值 {plain}；表头 {header}；摘要“{ui_summary}”；第 2 页 {len(page2.get('rows') or [])} 行")
            _ = fragment_options
        run("6 试运行", s6)

        # ---------- 7. 第 2 版、对比、恢复为第 3 版 ----------
        def s7():
            open_template("ACC_REFUNDS")
            current = page.locator(".dq-editor-title .ant-tag", has_text=re.compile(r"当前 v\d+")).inner_text()
            base_no = int(current.split("v")[-1])
            before = len(validations)
            set_body(REFUNDS_BODY_V2)
            wait_validation(before)
            page.wait_for_timeout(600)
            status, saved = save_template("验收 v2：增加 body_version 列", "PUT")
            v2 = saved.get("currentVersionNo")
            page.locator(".dq-editor-title").get_by_role("button", name="版本历史").click()
            drawer = page.locator(".ant-drawer", has_text="版本历史")
            drawer.wait_for(state="visible", timeout=5000)
            settle(page, 800)
            items = drawer.locator(".dq-version-item")
            # 显式勾选 v(n-1) 与 vn（默认已选；先清空再勾选以真实操作复选框）
            for i in range(items.count()):
                box = items.nth(i).locator(".ant-checkbox-input")
                if box.is_checked():
                    box.uncheck()
            for no in (v2 - 1, v2):
                drawer.locator(".dq-version-item", has=page.locator("strong", has_text=re.compile(rf"^v{no}$"))).locator(".ant-checkbox-input").check()
            page.wait_for_timeout(1200)
            legend = drawer.locator(".dq-diff-legend").inner_text().replace("\n", " ") if drawer.locator(".dq-diff-legend").count() else ""
            added = drawer.locator(".dq-diff-cell.is-added").all_inner_texts()
            removed = drawer.locator(".dq-diff-cell.is-removed").all_inner_texts()
            shot("14-version-compare")
            restore_btn = drawer.get_by_role("button", name=re.compile(r"以 v\d+ 恢复为 v\d+"))
            label = restore_btn.inner_text() if restore_btn.count() else ""
            restore_btn.click()
            modal = page.locator(".ant-modal", has_text=re.compile(r"以 v\d+ 恢复为 v\d+")).last
            modal.wait_for(state="visible", timeout=5000)
            with page.expect_response(lambda r: r.url.endswith("/restoration") and r.request.method == "POST", timeout=30000) as info:
                modal.get_by_role("button", name=re.compile(r"^恢\s?复$")).click()
            restored = info.value.json() if info.value.status == 200 else {"status": info.value.status}
            settle(page, 1500)
            v3tag = visible(page.locator(".dq-editor-title .ant-tag", has_text=f"当前 v{v2 + 1}"), 5000)
            status_v, versions, _ = api(state["token"], "GET", ADMIN + f"/templates/{template_ids['ACC_REFUNDS']}/versions?page=1&size=10")
            top = (versions.get("items") or [{}])[0]
            _, v_new, _ = api(state["token"], "GET", ADMIN + f"/templates/{template_ids['ACC_REFUNDS']}/versions/{v2 + 1}")
            _, v_old, _ = api(state["token"], "GET", ADMIN + f"/templates/{template_ids['ACC_REFUNDS']}/versions/{v2 - 1}")
            same = v_new.get("body") == v_old.get("body")
            page.locator(".dq-editor-title").get_by_role("button", name="版本历史").click()
            drawer = page.locator(".ant-drawer", has_text="版本历史")
            drawer.wait_for(state="visible", timeout=5000)
            settle(page, 800)
            shot("15-version-history-after-restore")
            drawer.locator(".dq-drawer-footer").get_by_role("button", name=re.compile(r"^关\s?闭$")).click()
            page.wait_for_timeout(400)
            evidence["versions"] = {"save": [status, saved], "restore": restored, "list": versions.get("items")}
            record("7 第 2 版、对比与恢复", status == 200 and v2 == base_no + 1 and "body_version" in " ".join(added) and legend
                   and restored.get("currentVersionNo") == v2 + 1 and v3tag and top.get("restoredFromVersionNo") == v2 - 1 and same,
                   f"保存 {status} → v{v2}；对比“{legend}”，新增行 {[a.strip()[:60] for a in added]}，删除行 {[r.strip()[:60] for r in removed]}；"
                   f"按钮“{label}”；恢复 → v{restored.get('currentVersionNo')}；最新版本 restoredFrom=v{top.get('restoredFromVersionNo')}，正文与 v{v2 - 1} 相同 {same}")
        run("7 第 2 版、对比与恢复", s7)

        # ---------- 8. 整实例模板 ----------
        def s8():
            status, body, _ = api(state["token"], "GET", ADMIN + "/templates?keyword=ACC_INSTANCE_NUMBERS&page=1&size=10")
            found = next((i for i in body.get("items") or [] if i.get("templateCode") == "ACC_INSTANCE_NUMBERS"), None)
            if found:
                template_ids["ACC_INSTANCE_NUMBERS"] = found["templateId"]
                open_template("ACC_INSTANCE_NUMBERS")
            else:
                goto("/data-query/templates/new")
                page.locator(".dq-code-input").fill("ACC_INSTANCE_NUMBERS")
                page.locator(".dq-name-input").fill("验收：整实例数字表")
                choose_source("ACC_FIXTURE_INSTANCE")
                panel = page.locator(".dq-editor-right")
                panel.get_by_role("tab", name="约束").click()
                constraint_input(panel, "超时（秒）").fill("5")
            before = len(validations)
            set_body(INSTANCE_BODY_UNQUALIFIED)
            wait_validation(before)
            page.wait_for_timeout(800)
            unqualified = problem_texts("error")
            shot("16-instance-unqualified-error")
            before = len(validations)
            set_body(INSTANCE_BODY_SYSTEM)
            wait_validation(before)
            page.wait_for_timeout(800)
            system = problem_texts("error")
            shot("17-instance-system-schema-error")
            before = len(validations)
            set_body(INSTANCE_BODY_OK)
            wait_validation(before)
            page.wait_for_timeout(800)
            ok_errors = problem_texts("error")
            if found:
                status, saved = 200, {"currentVersionNo": found["currentVersionNo"], "note": "首轮已保存，重复运行未再保存"}
            else:
                status, saved = save_template("验收 v1：整实例 库.表", "POST")
                template_ids["ACC_INSTANCE_NUMBERS"] = saved.get("templateId")
                state["path"] = f"/data-query/templates/{saved.get('templateId')}"
            page.locator(".dq-bottom").get_by_role("tab", name="试运行").click()
            trial = page.locator(".dq-trial")
            trial.locator(".dq-trial-paging .ant-checkbox-input").check()
            with page.expect_response(lambda r: r.url.endswith(ADMIN + "/template-trials"), timeout=30000) as info:
                trial.get_by_role("button", name=re.compile("试运行")).click()
            result = info.value.json() if info.value.status == 200 else {"status": info.value.status}
            page.wait_for_timeout(800)
            shot("18-instance-trial-result")
            first = [r[0] for r in (result.get("rows") or [])[:3]]
            evidence["instance"] = {"unqualified": unqualified, "system": system, "save": [status, saved], "trialTotal": result.get("total"),
                                    "trialFirst": first, "sql": result.get("sql")}
            record("8 整实例模板", bool(unqualified) and bool(system) and not ok_errors and status == 200 and info.value.status == 200
                   and result.get("total") == 50 and first == ["0", "1", "2"],
                   f"未限定库名错误 {unqualified}；mysql.user 错误 {system}；改正后错误 {ok_errors}；保存 {status} v{saved.get('currentVersionNo')}；"
                   f"试运行 {info.value.status}，总数 {result.get('total')}，前 3 行 {first}，SQL {result.get('sql')}")
        run("8 整实例模板", s8)

        # ---------- 9. 执行入口 ----------
        def s9():
            token = state["token"]
            status, body, elapsed = api(token, "POST", "/data-query/v1/templates/ACC_REFUNDS/executions",
                                        {"params": {"currency": "USD", "statuses": ["SUCCEEDED"], "sortBy": "NEWEST"}, "page": 1, "size": 5,
                                         "withTotal": True})
            expected = sql_readonly(FIXTURE_DB, "SELECT refund_no FROM t_refund_fixture WHERE currency='USD' AND refund_status='SUCCEEDED' "
                                                "ORDER BY create_time DESC, refund_id DESC")
            rows = body.get("rows") or []
            evidence["execution"] = {"status": status, "versionNo": body.get("versionNo"), "columns": body.get("columns"), "rows": rows,
                                     "total": body.get("total"), "elapsedSeconds": elapsed}
            record("9a 执行 ACC_REFUNDS", status == 200 and len(rows) == len(expected) and body.get("total") == len(expected)
                   and isinstance(body.get("versionNo"), int) and body.get("versionNo") >= 1,
                   f"{status}，versionNo={body.get('versionNo')}，{len(rows)} 行（库中 {len(expected)}），total={body.get('total')}，首行 {rows[:1]}")
            # 超时模板：经管理接口建（数据源 reader），约束超时 1 秒。
            st, found, _ = api(token, "GET", ADMIN + "/templates?keyword=ACC_TIMEOUT_SUM&page=1&size=10")
            if not any(i.get("templateCode") == "ACC_TIMEOUT_SUM" for i in found.get("items") or []):
                st, created, _ = api(token, "POST", ADMIN + "/templates", {
                    "templateCode": "ACC_TIMEOUT_SUM", "templateName": "验收：超时（三表 CROSS JOIN）", "dataSourceId": source_ids.get("ACC_FIXTURE_READER"),
                    "body": TIMEOUT_BODY, "parameters": [], "columns": [],
                    "constraints": {"maxRows": 1, "timeoutSeconds": 1, "pagingMode": "NONE"}, "changeNote": "验收：超时 1 秒"})
                evidence["timeoutCreate"] = [st, created]
            status, body, elapsed = api(token, "POST", "/data-query/v1/templates/ACC_TIMEOUT_SUM/executions", {"params": {}})
            evidence["timeout"] = {"status": status, "code": body.get("code"), "detail": body.get("detail"), "elapsedSeconds": elapsed}
            record("9b 超时模板", status == 400 and body.get("code") == "DATA_QUERY_TIMEOUT" and elapsed < 6,
                   f"{status} {body.get('code')}：{body.get('detail')}；耗时 {elapsed}s；建模板 {evidence.get('timeoutCreate', ['已存在'])[0]}")
        run("9 执行入口", s9)

        # ---------- 10. 模板列表 ----------
        def s10():
            goto("/data-query/templates")
            rows = page.locator(".ant-table-tbody tr.ant-table-row")
            visible(rows, 8000)
            text = " | ".join(t.replace("\n", " ")[:160] for t in rows.all_inner_texts())
            shot("19-templates-list")
            record("10 模板列表", "ACC_REFUNDS" in text and "ACC_INSTANCE_NUMBERS" in text and not errors_on(page), text[:600])
        run("10 模板列表", s10)
        browser.close()
    return dump(results, console, network, aborted, shots, state, evidence)


def dump(results, console, network, aborted, shots, state, evidence):
    relevant = [c for c in console if KNOWN not in c[2] and c[1] in ("error", "pageerror")]
    # 浏览器对每个 4xx 响应都会打印一条 “Failed to load resource … status of N”；与已匹配的预期错误一一抵消，单独列出。
    expected_console = []
    for item in state["expected"]:
        if not item["matched"]:
            continue
        hit = next((c for c in relevant if f"status of {item['status']}" in c[2]), None)
        if hit:
            relevant.remove(hit)
            expected_console.append(hit)
    warnings = [c for c in console if c[1] == "warning" and KNOWN not in c[2]]
    expected = [{k: v for k, v in item.items()} for item in state["expected"]]
    data = {"results": results, "consoleErrors": relevant, "consoleWarnings": warnings, "unexpectedNetwork": network,
            "expectedErrors": expected, "expectedConsole": expected_console, "abortedValidations": len(aborted), "shots": shots, "evidence": evidence}
    previous = HERE / "stage7-browser-result.json"
    if previous.exists():
        old = json.loads(previous.read_text())
        earlier = old.pop("earlierRuns", {})
        earlier[f"run_before_{time.strftime('%Y-%m-%dT%H:%M')}"] = {k: old.get(k) for k in
                                                                     ("results", "evidence", "expectedErrors", "unexpectedNetwork", "consoleErrors")}
        data["earlierRuns"] = earlier
    (HERE / "stage7-browser-result.json").write_text(json.dumps(data, ensure_ascii=False, indent=2, default=str))
    passed = sum(1 for r in results if r["ok"])
    print(f"\n{passed}/{len(results)} 通过；控制台错误 {len(relevant)}，警告 {len(warnings)}；非预期 4xx/5xx/失败 {len(network)}；"
          f"预期错误 {[(e['status'], (e['matched'] or {}).get('code')) for e in expected]}；校验请求主动取消 {len(aborted)}")
    for c in relevant:
        print("  console:", c[0], c[2][:300])
    for n in network:
        print("  network:", json.dumps(n, ensure_ascii=False)[:400])
    if passed != len(results) or relevant or network:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
