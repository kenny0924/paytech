"""文件多存储并存第二段阶段 7：mor-test 浏览器冒烟（无头 Chromium，真实登录；参照第一段 release/acceptance/stage7_browser.py）。

前置：stage7_s2_acceptance.py 已运行（用它的合成账号与草稿申请，运行记录取 FS2_STATE 最新 run-*.json）。
客户控制台 https://localhost:28443（test CA 签发，浏览器忽略证书校验，只影响本脚本）：
  1. 登录 → 打开草稿申请 /onboarding/applications/{id} → 在第一个“上传文件”处选择本机生成的 PDF，等待“材料已关联”；
  2. 材料列表看到新材料（含验收脚本关联的那份），点“查看”取得下载（浏览器下载或 MinIO 200）。
运营控制台 http://localhost:28080（管理员）：
  3. 客户入驻审核详情点一次材料下载（同第一段）；
  4. 平台配置 → 存储管理三个页签可打开；
  5. 工作流页（我的待办、流程实例）可打开（mor-test 目前没有工作流附件，附件下载由本机测试覆盖）。
判定：控制台错误 0、4xx/5xx 0（预签名下载转为下载时的 ERR_ABORTED 不计）。截图写 screens/，结果写 stage7-s2-browser-result.json。
写入：步骤 1 会新增一份上传与一条材料关联（同合成账号，清理脚本不处理这一条，release.md 第 7 节说明）。
用法：python3 -I stage7_s2_browser.py [run-*.json]
"""
import json
import re
import time
import site
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.append(site.getusersitepackages())
import s2_common as c  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

OUT = Path(__file__).resolve().parent / "screens-s2"
OUT.mkdir(exist_ok=True)
CLIENT = "https://localhost:28443"
OPS = "http://localhost:28080"
MINIO = "minio.mor-test.localhost:29400"
ERRORS = ".ant-alert-error, .ant-result-error, .ant-message-error, .ant-notification-notice-error, .error, [role=alert].error"


def run_record():
    if len(sys.argv) > 1:
        return json.loads(Path(sys.argv[1]).read_text())
    runs = sorted(c.STATE.glob("run-*.json"))
    if not runs:
        raise SystemExit("先运行 stage7_s2_acceptance.py")
    return json.loads(runs[-1].read_text())


def settle(page):
    try:
        page.wait_for_load_state("networkidle", timeout=20000)
    except Exception:  # noqa: BLE001
        pass
    page.wait_for_timeout(1200)


def visible(locator, timeout=8000):
    try:
        locator.first.wait_for(state="visible", timeout=timeout)
        return True
    except Exception:  # noqa: BLE001
        return False


def shot(page, name):
    page.wait_for_timeout(500)
    page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)


def cleanup_browser_documents(record, since):
    """只删本次浏览器运行关联的材料：同一申请、创建时间不早于本次运行；经客户端 C13（与 stage7_s2_cleanup.py 同一方法）。"""
    rows = c.sql("SELECT document_id FROM mor_client_master.t_application_document "
                 f"WHERE client_application_id={int(record['applicationId'])} "
                 f"AND create_time >= '{since.replace('T', ' ').rstrip('Z')}'")
    client = c.af.Client()
    _, grant = client.call("/auth/v1/login", "POST", {"email": record["email"], "password": record["password"]}, auth=False)
    client.token = grant["accessToken"]
    deleted = []
    for (document_id,) in rows:
        status, _ = c.client_call(client, f"/onboarding/v1/applications/{record['applicationId']}/documents/{document_id}",
                                  "DELETE", key=True)
        deleted.append((document_id, status))
    left = c.sql("SELECT COUNT(*) FROM mor_client_master.t_application_document "
                 f"WHERE client_application_id={int(record['applicationId'])} "
                 f"AND create_time >= '{since.replace('T', ' ').rstrip('Z')}'")[0][0]
    c.check("清理本次浏览器关联的材料（C13）", bool(rows) and all(s in (200, 204) for _, s in deleted) and left == "0",
            {"deleted": deleted, "left": left})


def main():
    record = run_record()
    since = c.now_iso()
    pdf = c.STATE / "browser-material.pdf"
    pdf.write_bytes(c.af.PDF)
    admin = c.http.secrets()
    console, network, minio = [], [], []

    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": 1512, "height": 900}, ignore_https_errors=True, accept_downloads=True)
        page = context.new_page()
        current = {"path": "/"}
        page.on("console", lambda m: console.append((current["path"], m.text)) if m.type == "error" else None)

        def on_response(r):
            if MINIO in r.url:
                minio.append((current["path"], r.status))
            if r.status >= 400:
                network.append((current["path"], r.status, r.request.method, r.url.split("?")[0]))

        def on_failed(r):
            if MINIO in r.url and "ERR_ABORTED" in str(r.failure):
                minio.append((current["path"], "ABORTED_TO_DOWNLOAD"))
                return
            network.append((current["path"], "FAILED", r.method, r.url.split("?")[0]))
        page.on("response", on_response)
        page.on("requestfailed", on_failed)

        # ---------- 客户控制台 ----------
        current["path"] = "client:/login"
        page.goto(CLIENT + "/login")
        page.get_by_label("邮箱").fill(record["email"])
        page.locator("#password").fill(record["password"])
        page.get_by_role("button", name="登录").click()
        settle(page)
        c.check("客户登录", "/login" not in page.url, page.url)
        current["path"] = "client:/onboarding/applications"
        page.goto(f"{CLIENT}/onboarding/applications/{record['applicationId']}")
        settle(page)
        shot(page, "c01-application")
        before = page.get_by_role("button", name="查看").count()
        upload = page.get_by_role("button", name="上传文件")
        if visible(upload, 8000):
            upload.first.click()
            # 上传在弹窗 <dialog class="editor"> 内完成：选文件 → 反复点主按钮（继续 / 检查扫描结果并关联）直到弹窗关闭。
            dialog = page.locator("dialog.editor")
            visible(dialog, 5000)
            dialog.locator("#material-file").set_input_files(str(pdf))
            phases, deadline = [], time.time() + 150
            while time.time() < deadline and dialog.count() and dialog.first.is_visible():
                status = dialog.get_by_role("status").inner_text() if dialog.get_by_role("status").count() else ""
                if not phases or phases[-1] != status:
                    phases.append(status)
                if status == "材料已关联":
                    break
                primary = dialog.locator("button.primary")
                if status != "处理中…" and primary.count() and primary.first.is_enabled():
                    if status == "文件已上传，等待扫描结果":
                        page.wait_for_timeout(5000)
                    primary.first.click()
                page.wait_for_timeout(1500)
                if len(phases) == 1:
                    shot(page, "c02-upload-dialog")
            closed = not (dialog.count() and dialog.first.is_visible())
            settle(page)
            shot(page, "c02-upload-done")
            c.check("客户页面上传：开始 → 直传 → 完成 → 扫描 → 关联（弹窗关闭）", closed or "材料已关联" in phases, " → ".join(phases))
        else:
            c.check("客户页面找到“上传文件”入口", False)
        views = page.get_by_role("button", name="查看")
        c.check("材料列表显示已关联材料（含本次上传）", views.count() >= before + 1, f"查看按钮 {views.count()} 个")
        downloaded = False
        if views.count():
            n = len(minio)
            try:
                with page.expect_download(timeout=15000) as dl:
                    views.first.click()
                downloaded = Path(dl.value.path()).stat().st_size > 0
            except Exception:  # noqa: BLE001
                page.wait_for_timeout(3000)
                downloaded = any(m[1] == 200 for m in minio[n:])
        shot(page, "c03-download")
        c.check("客户下载材料", downloaded, f"MinIO {[m[1] for m in minio]}")

        # ---------- 运营控制台 ----------
        current["path"] = "ops:/login"
        page.goto(OPS + "/")
        page.get_by_label("Email / 初始化账号").fill(admin["E2E_ADMIN_EMAIL"])
        page.locator("#password").fill(admin["E2E_ADMIN_PASSWORD"])
        page.get_by_role("button", name=re.compile(r"^登\s?录$")).click()
        settle(page)
        case_id = (record.get("o10") or {}).get("caseId")
        if case_id:
            current["path"] = f"ops:/client-onboarding/reviews/{case_id}"
            page.goto(f"{OPS}/client-onboarding/reviews/{case_id}")
            settle(page)
            button = page.get_by_role("button", name=re.compile(r"下载文件 \d+"))
            ok = False
            if visible(button, 10000):
                try:
                    with page.expect_download(timeout=15000) as dl:
                        button.first.click()
                    ok = Path(dl.value.path()).stat().st_size > 0
                except Exception:  # noqa: BLE001
                    ok = False
            shot(page, "o01-review-download")
            c.check("运营审核详情材料下载", ok)
        for path, name, marker in (("/file-storage", "o02-file-storage", "MINIO"), ("/workflow/my-tasks", "o03-workflow-tasks", None),
                                   ("/workflow/instances", "o04-workflow-instances", None)):
            current["path"] = "ops:" + path
            page.goto(OPS + path)
            settle(page)
            shot(page, name)
            ok = visible(page.locator(".ant-layout-content, main"), 8000) and not page.locator(ERRORS).count()
            if marker:
                ok = ok and visible(page.get_by_text(marker), 8000)
            c.check(f"运营页面 {path}", ok)
        browser.close()

    cleanup_browser_documents(record, since)
    c.check("控制台错误 0", not console, console[:3])
    c.check("4xx / 5xx / 失败请求 0", not network, network[:5])
    (Path(__file__).resolve().parent / "stage7-s2-browser-result.json").write_text(json.dumps(
        {"results": c.results, "console": console, "network": network, "minio": minio}, ensure_ascii=False, indent=1))
    if not c.summary():
        raise SystemExit(1)


if __name__ == "__main__":
    main()
