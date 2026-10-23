"""运营控制台多语言迁移：阶段 7 开始前一键只读复核（不写数据、不登录、不改容器）。输出 PASS / FAIL 计数，任一 FAIL 退出码 1。

1. 2 个运行容器 .Image = compose-rollback.yaml、healthy、重启 0（基线未被其他会话替换）；
2. 2 个候选镜像存在且 = compose-candidate.yaml；BFF 镜像内 JAR SHA-256 = 阶段 6 构建；Entrypoint / Cmd / User / WorkingDir / Env /
   ExposedPorts / Healthcheck / StopSignal 与运行镜像相同；
3. 候选 / 回滚覆盖文件渲染后与运行容器环境变量逐键、逐值相同（只比较，不输出值）；
4. mor_platform（只读 SELECT）：operations-console 应用 ENABLED；命名空间只有 i18n-admin、enum（第 ② 步之前）且 19 个新编码未占用；
   当前发布号 / 状态 / key 数、无进行中的发布；默认语言（第 ② 步前 zh-CN、之后 en）、必须语言 ["zh-CN"]、覆盖率阈值 100；
   en、zh-CN 语言 ENABLED；线上 i18n-admin、enum 的 409 个 key 的英文基准与中文翻译 = 本包输入文件（新语言包不改变旧页面文案）；
5. 匿名 manifest（en、zh-CN）200、发布号 = 当前发布、命名空间 = 线上；
6. 公开桶：当前 manifest 的一个语言包对运营 Origin 返回 200 与 CORS 允许头，其他 Origin 无允许头（TLS 只信任 test CA）；
7. 本机输入：i18n-bundles 两个文件 SHA256SUMS 一致；
8. --browser：无头 Chromium 严格证书校验，从运营控制台页面 fetch 公开桶语言包。
用法：python3 -I precheck_readonly.py [--browser] [--after-bundles]
  --after-bundles：第 ② 步之后复核（期望默认语言 en、21 个命名空间、当前发布 key 8523）。
"""
import argparse
import base64
import json
import site
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # 不在发布包与前例目录留下 __pycache__
sys.path.insert(0, str(Path(__file__).resolve().parent))
import opsi18n_common as o  # noqa: E402

c = o.c
CONFIG = ("{{json .Config.Entrypoint}}|{{json .Config.Cmd}}|{{.Config.User}}|{{.Config.WorkingDir}}|{{json .Config.Env}}|"
          "{{json .Config.ExposedPorts}}|{{json .Config.Healthcheck}}|{{json .Config.StopSignal}}")
counts = {"PASS": 0, "FAIL": 0}


def report(name, ok, note=""):
    counts["PASS" if ok else "FAIL"] += 1
    print(f"{'PASS' if ok else 'FAIL'}  {name}{('  | ' + note) if note and not ok else ''}", flush=True)
    return ok


def docker(*args):
    return subprocess.run(["docker", *args], capture_output=True, text=True)


def containers():
    rollback = c.compose_images(o.RELEASE / "compose-rollback.yaml")
    for service in c.SERVICES:
        image = c.container(service, "{{.Image}}")
        state = c.container(service, "{{.State.Health.Status}} {{.RestartCount}}")
        report(f"基线 {service} .Image = 回滚文件 {rollback.get(service, '')[7:19]}", image == rollback.get(service), image)
        report(f"基线 {service} healthy / 重启 0", state == "healthy 0", state)


def candidates():
    candidate = c.compose_images(o.RELEASE / "compose-candidate.yaml")
    report("候选覆盖文件镜像 = opsi18n_common.CANDIDATE", candidate == o.CANDIDATE, json.dumps(candidate))
    for service in c.SERVICES:
        image = candidate.get(service, "")
        exists = docker("image", "inspect", image, "--format", "{{.Id}}").stdout.strip() == image
        report(f"候选 {service} 镜像存在 {image[7:19]}", exists)
        if not exists:
            continue
        live = c.container(service, "{{.Image}}")
        same = docker("image", "inspect", live, "--format", CONFIG).stdout == docker("image", "inspect", image, "--format", CONFIG).stdout
        report(f"候选 {service} 镜像配置与运行镜像相同", same)
    digest = docker("run", "--rm", "--network", "none", "--entrypoint", "sha256sum", o.CANDIDATE["operations-console-bff"],
                    "/app/application.jar").stdout[:64]
    report(f"候选 operations-console-bff JAR SHA-256 {o.CANDIDATE_JAR[:16]}", digest == o.CANDIDATE_JAR, digest)


def environment():
    for name in ("compose-candidate.yaml", "compose-rollback.yaml"):
        rendered = subprocess.run([str(c.TEST_ENV / "compose.sh"), "-f", str(o.RELEASE / name), "config", "--format", "json"],
                                  capture_output=True, text=True)
        if not report(f"{name} 渲染", rendered.returncode == 0, rendered.stderr[:200]):
            continue
        services = json.loads(rendered.stdout)["services"]
        for service in c.SERVICES:
            svc = services[service]
            compose_env = {k: ("" if v is None else str(v)) for k, v in (svc.get("environment") or {}).items()}
            image_env = dict(e.split("=", 1) for e in json.loads(docker("image", "inspect", svc["image"], "--format", "{{json .Config.Env}}").stdout))
            expected = {**image_env, **compose_env}
            live = dict(e.split("=", 1) for e in json.loads(c.container(service, "{{json .Config.Env}}")))
            missing, extra = sorted(set(live) - set(expected)), sorted(set(expected) - set(live))
            differ = sorted(k for k in set(live) & set(expected) if live[k] != expected[k])
            report(f"环境 {name} {service} 0 缺 0 多 0 不同（compose {len(compose_env)} / 运行 {len(live)} 键）",
                   not (missing or extra or differ), f"缺 {missing} 多 {extra} 不同 {differ}")


def platform(after):
    rows = o.sql_rows("SELECT application_code, status, default_locale, required_locales, coverage_threshold_percent, current_release_id, version "
                      f"FROM t_i18n_application WHERE application_id = {o.APP_ID}")
    if not report("SQL operations-console 应用行存在（ID 900000000000135011）", len(rows) == 1 and rows[0][0] == o.APP):
        return None
    _, status, default_locale, required, threshold, current_id, version = rows[0]
    report("SQL 应用 ENABLED", status == "ENABLED", status)
    want = "en" if after else "zh-CN"
    report(f"SQL 默认语言 = {want}（第 ② 步{'后' if after else '前'}；现值 {default_locale}，版本 {version}）", default_locale == want, default_locale)
    report("SQL 必须语言 [\"zh-CN\"]、覆盖率阈值 100", json.loads(required) == ["zh-CN"] and threshold == "100", f"{required} {threshold}")
    locales = dict(o.sql_rows("SELECT locale_code, status FROM t_i18n_locale WHERE locale_code IN ('en','zh-CN')"))
    report("SQL 语言 en、zh-CN ENABLED", locales == {"en": "ENABLED", "zh-CN": "ENABLED"}, json.dumps(locales))
    namespaces = sorted(r[0] for r in o.sql_rows(f"SELECT namespace_code FROM t_i18n_namespace WHERE application_id = {o.APP_ID}"))
    expect_ns = o.ALL_NAMESPACES if after else sorted(o.EXISTING_NAMESPACES)
    report(f"SQL 命名空间 = {len(expect_ns)} 个（现有 {namespaces}）" if not after else "SQL 命名空间 = 21 个", namespaces == expect_ns,
           str(sorted(set(namespaces) ^ set(expect_ns))))
    releases = o.sql_rows(f"SELECT release_no, status, key_count, release_id = {current_id or 0} FROM t_i18n_release "
                          f"WHERE application_id = {o.APP_ID} ORDER BY release_no")
    current = [r for r in releases if r[3] == "1"]
    pending = [r for r in releases if r[1] not in ("READY", "FAILED")]
    print(f"      记录 发布 {[(r[0], r[1], r[2]) for r in releases]}；当前 #{current[0][0] if current else None}")
    report("SQL 当前发布存在且 READY", len(current) == 1 and current[0][1] == "READY", str(current))
    report(f"SQL 当前发布 key 数 = {8523 if after else 409}", bool(current) and current[0][2] == str(8523 if after else 409),
           str(current[0][2] if current else None))
    report("SQL 无进行中的发布（PENDING_CHECK 等）", not pending, str(pending))
    files = o.load_bundles()
    en, zh = o.flat(files["en"][1], "en"), o.flat(files["zh-CN"][1], "zh-CN")
    keys = o.sql_rows("SELECT k.key_code, n.namespace_code, k.status, REPLACE(TO_BASE64(k.source_text), CHAR(10), ''), "
                      "IFNULL(REPLACE(TO_BASE64(t.text), CHAR(10), ''), '') FROM t_i18n_key k JOIN t_i18n_namespace n USING (namespace_id) "
                      "LEFT JOIN t_i18n_translation t ON t.key_id = k.key_id AND t.locale_code = 'zh-CN' "
                      f"WHERE k.application_id = {o.APP_ID} AND n.namespace_code IN ('i18n-admin', 'enum')")
    decode = lambda value: base64.b64decode(value).decode()  # noqa: E731
    changed = [r[0] for r in keys if r[2] != "ACTIVE" or r[0] not in en or decode(r[3]) != en[r[0]][1] or decode(r[4]) != zh.get(r[0], (None, None))[1]]
    report(f"SQL 线上 i18n-admin / enum {len(keys)} 个 key 的英文基准与中文 = 本包输入（旧页面文案不受新发布影响）",
           len(keys) == 409 and not changed, str(changed[:5]))
    return {"current": current[0][0] if current else None, "namespaces": namespaces}


def manifest_and_bucket(state):
    sample = None
    for locale in ("en", "zh-CN"):
        status, body, _, headers = c.raw(None, "GET", f"?locale={locale}", prefix=c.OPS_MANIFEST, base=c.OPS, key=False)
        names = sorted({ns for per in (body.get("bundles") or {}).values() for ns in per})
        report(f"manifest ?locale={locale} 200、发布 #{body.get('releaseNo')} = 当前、命名空间 = 线上、no-store",
               status == 200 and state and str(body.get("releaseNo")) == state["current"] and names == state["namespaces"]
               and "no-store" in (headers.get("Cache-Control") or ""), f"{status} {body.get('releaseNo')} {names}")
        bundles = (body.get("bundles") or {}).get(locale) or {}
        if bundles:
            sample = next(iter(bundles.values()))["url"]
    if not report("manifest 给出语言包公开地址", bool(sample)):
        return
    status, _, headers = c.public_get(sample, origin=c.OPS)
    report(f"公开桶语言包对 {c.OPS} 200 + CORS 允许（TLS 只信任 test CA）", status == 200 and headers.get("access-control-allow-origin") == c.OPS,
           f"{status} {headers.get('access-control-allow-origin')}")
    status, _, headers = c.public_get(sample, origin="https://evil.example")
    report("公开桶对其他 Origin 不给 CORS 允许头", "access-control-allow-origin" not in headers, str(headers.get("access-control-allow-origin")))
    return sample


def inputs():
    files = o.load_bundles()
    for locale, name in (("en", o.EN_FILE), ("zh-CN", o.ZH_FILE)):
        report(f"本机输入 {name} SHA256SUMS 一致", files[locale][2])


def browser(sample):
    sys.path.append(site.getusersitepackages())
    from playwright.sync_api import sync_playwright  # noqa: PLC0415
    with sync_playwright() as p:
        chromium = p.chromium.launch()
        context = chromium.new_context(ignore_https_errors=False)
        page = context.new_page()
        try:
            page.goto(c.OPS + "/", wait_until="domcontentloaded", timeout=20000)
            result = page.evaluate("""async (u) => { try { const r = await fetch(u, {cache: 'no-store', credentials: 'omit'});
                return {status: r.status}; } catch (e) { return {error: String(e)}; } }""", sample)
        except Exception as error:  # noqa: BLE001
            result = {"error": str(error)[:200]}
        report(f"浏览器（严格证书）从 {c.OPS} fetch 公开桶语言包 200", result.get("status") == 200, json.dumps(result))
        context.close()
        chromium.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--after-bundles", action="store_true")
    args = parser.parse_args()
    containers()
    candidates()
    environment()
    state = platform(args.after_bundles)
    sample = manifest_and_bucket(state)
    inputs()
    if args.browser and sample:
        browser(sample)
    print(f"\n== 只读预检：PASS {counts['PASS']}，FAIL {counts['FAIL']}")
    return 0 if counts["FAIL"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
