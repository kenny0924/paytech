"""多语言管理阶段 7 开始前一键只读复核（不写任何数据、不登录、不改容器）。输出 PASS / FAIL 计数，任一 FAIL 退出码 1。

1. sql/00-precheck.sql：第一段全部 ok=1（S / G / F 组）；
2. 5 个运行容器 .Image = compose-rollback.yaml、healthy、重启 0（基线未被其他会话替换）；
3. 5 个候选镜像在本机存在且 ID = compose-candidate.yaml；镜像内 JAR（3 个 Java）SHA-256 = 阶段 6 记录；
   Entrypoint / Cmd / User / WorkingDir / Env / ExposedPorts / Healthcheck 与对应运行镜像相同；
4. compose-candidate / compose-rollback 渲染后与 5 个运行容器的环境变量逐键、逐值相同（只比较，不输出值）；
5. MinIO 公开桶：两个控制台 Origin 得到 CORS 允许头、其他 Origin 没有；TLS 只信任 test CA 即可通过；
6. 两个 manifest 外部路径当前未上线（匿名 GET 不是 200；激活后才应为 200）；
7. --browser：无头 Chromium 严格证书校验，从两个控制台页面 fetch 公开桶地址（CORS + 证书）。
用法：python3 -I precheck_readonly.py [--browser]
"""
import argparse
import json
import site
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import i18n7_common as c  # noqa: E402

JAR_SHA = {  # 阶段 6 构建产物（release.md 第 1.4 节）
    "platform-support-service": "bfbb75a7bd195d66",
    "operations-console-bff": "d52e541baf7a163b",
    "client-console-bff": "948f2c7ad412de27",
}
CONFIG = ("{{json .Config.Entrypoint}}|{{json .Config.Cmd}}|{{.Config.User}}|{{.Config.WorkingDir}}|{{json .Config.Env}}|"
          "{{json .Config.ExposedPorts}}|{{json .Config.Healthcheck}}")
PROBE_URL = c.PUBLIC_BASE + "/i18n/precheck-probe-nonexistent.json"
counts = {"PASS": 0, "FAIL": 0}


def report(name, ok, note=""):
    counts["PASS" if ok else "FAIL"] += 1
    print(f"{'PASS' if ok else 'FAIL'}  {name}{('  | ' + note) if note and not ok else ''}", flush=True)


def docker(*args):
    return subprocess.run(["docker", *args], capture_output=True, text=True)


def sql_precheck():
    pw = c.env()["MOR_MYSQL_ROOT_PASSWORD"]
    result = subprocess.run(["docker", "exec", "-i", "-e", f"MYSQL_PWD={pw}", "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                             "--default-character-set=utf8mb4", "mysql"], input=(c.RELEASE / "sql/00-precheck.sql").read_text(),
                            capture_output=True, text=True)
    if result.returncode != 0:
        report("SQL 00-precheck 执行", False, result.stderr[:200])
        return
    rows = [line.split("\t") for line in result.stdout.splitlines() if line]
    checks = [r for r in rows if len(r) == 4]
    for item, actual, expect, ok in checks:
        report(f"SQL {item}", ok == "1", f"actual={actual} expect={expect}")
    for row in rows:
        if len(row) == 2:
            print(f"      记录 {row[0]} = {row[1]}")


def containers():
    rollback = c.compose_images(c.RELEASE / "compose-rollback.yaml")
    for service in c.SERVICES:
        image = c.container(service, "{{.Image}}")
        state = c.container(service, "{{.State.Health.Status}} {{.RestartCount}}")
        report(f"基线 {service} .Image = 回滚文件", image == rollback[service], image)
        report(f"基线 {service} healthy / 重启 0", state == "healthy 0", state)


def candidates():
    candidate = c.compose_images(c.RELEASE / "compose-candidate.yaml")
    for service in c.SERVICES:
        image = candidate[service]
        exists = docker("image", "inspect", image, "--format", "{{.Id}}").stdout.strip() == image
        report(f"候选 {service} 镜像存在 {image[7:19]}", exists)
        if not exists:
            continue
        live = c.container(service, "{{.Image}}")
        same = docker("image", "inspect", live, "--format", CONFIG).stdout == docker("image", "inspect", image, "--format", CONFIG).stdout
        report(f"候选 {service} 镜像配置与运行镜像相同", same)
        if service in JAR_SHA:
            digest = docker("run", "--rm", "--network", "none", "--entrypoint", "sha256sum", image, "/app/application.jar").stdout[:16]
            report(f"候选 {service} JAR SHA-256 {JAR_SHA[service]}", digest == JAR_SHA[service], digest)


def environment():
    for name in ("compose-candidate.yaml", "compose-rollback.yaml"):
        rendered = subprocess.run([str(c.TEST_ENV / "compose.sh"), "-f", str(c.RELEASE / name), "config", "--format", "json"],
                                  capture_output=True, text=True)
        if rendered.returncode != 0:
            report(f"{name} 渲染", False, rendered.stderr[:200])
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
            report(f"环境 {name} {service} 0 缺 0 多 0 不同（{len(compose_env)} 个 compose 键）", not (missing or extra or differ),
                   f"缺 {missing} 多 {extra} 不同 {differ}")


def storage_and_routes():
    for origin in (c.OPS, c.CLIENT):
        status, _, headers = c.public_get(PROBE_URL, origin=origin)
        report(f"MinIO 公开桶 CORS 允许 {origin}（TLS 只信任 test CA）", headers.get("access-control-allow-origin") == origin, str(status))
    status, _, headers = c.public_get(PROBE_URL, origin="https://evil.example")
    report("MinIO 公开桶对其他 Origin 不给 CORS 允许头", "access-control-allow-origin" not in headers, str(headers.get("access-control-allow-origin")))
    for base, path in ((c.OPS, c.OPS_MANIFEST), (c.CLIENT, c.CLIENT_MANIFEST)):
        status, _, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=path, base=base, key=False)
        report(f"{path} 发布前未上线（{status}）", status != 200)


def browser():
    sys.path.append(site.getusersitepackages())
    from playwright.sync_api import sync_playwright  # noqa: PLC0415
    with sync_playwright() as p:
        chromium = p.chromium.launch()
        for origin in (c.OPS, c.CLIENT):
            context = chromium.new_context(ignore_https_errors=False)
            page = context.new_page()
            try:
                page.goto(origin + "/", wait_until="domcontentloaded", timeout=20000)
                result = page.evaluate("""async (u) => { try { const r = await fetch(u, {cache: 'no-store', credentials: 'omit'});
                    return {status: r.status}; } catch (e) { return {error: String(e)}; } }""", PROBE_URL)
            except Exception as error:  # noqa: BLE001
                result = {"error": str(error)[:200]}
            report(f"浏览器（严格证书）从 {origin} fetch 公开桶：无 CORS / 证书错误", "status" in result, json.dumps(result))
            context.close()
        chromium.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--browser", action="store_true")
    args = parser.parse_args()
    sql_precheck()
    containers()
    candidates()
    environment()
    storage_and_routes()
    if args.browser:
        browser()
    print(f"\n== 只读预检：PASS {counts['PASS']}，FAIL {counts['FAIL']}")
    return 0 if counts["FAIL"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
