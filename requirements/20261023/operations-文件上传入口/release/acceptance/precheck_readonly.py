"""运营控制台文件上传入口 mor-test 只读预检（阶段 6 已跑；阶段 7 第 ① 步复跑）。只做 docker inspect、临时 --network none 容器读镜像内文件、
compose config 渲染、只读 SELECT；不登录、不调用任何接口、不写数据。环境变量只比较是否一致，不输出值。

  1. 3 个运行容器 .Image = compose-rollback.yaml，healthy、重启 0；
  2. 3 个候选镜像存在且 = compose-candidate.yaml；两个 Java 镜像内 JAR SHA-256 = 阶段 6 构建；配置（Entrypoint / Cmd / User / WorkingDir /
     Env / ExposedPorts / Healthcheck / StopSignal）与对应运行镜像相同；
  3. 候选 / 回滚覆盖文件渲染后，3 个服务的环境与运行容器逐键、逐值相同；
  4. mor_platform：sql/00-precheck.sql 的 9 个占用计数为 0（块 136 的 ID 段、权限码、资源码、路由、菜单排序 86、(ops-bff, PUBLIC) 规则），
     角色 510、Target、父菜单 562 存在，S3_TEST 公开桶 ACTIVE、PUBLIC_DOCUMENT 有效修订 1 个；
  5. 写入规则现状：(*, PUBLIC) 指向 S3_TEST 公开桶 / PUBLIC_DOCUMENT；(operations-console-bff, PRIVATE) 不变（MINIO / OPERATIONS_BFF_IMPORT）；
  6. 多语言：operations-console 当前发布 READY、无进行中的发布；线上全部 ACTIVE key 都在本包输入中，英文基准、说明、命名空间与 zh-CN 翻译
     与输入逐条相同（证明导入只新增、不改已有翻译），本包只多 file.upload.* 与两个 nav.resource.*。
用法：python3 -I precheck_readonly.py [--after]（--after：阶段 7 第 ⑤—⑦ 步之后复核：容器 = 候选、块 136 已注册并激活、规则已建）
"""
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import fu7_common as o  # noqa: E402

c = o.c


def containers(after):
    want = o.CANDIDATE if after else o.ROLLBACK
    files = o.compose_images(o.RELEASE / ("compose-candidate.yaml" if after else "compose-rollback.yaml"))
    c.check("容器", f"{'compose-candidate' if after else 'compose-rollback'}.yaml 的 3 个镜像 = 常量", files == want, files)
    for service in o.SERVICES:
        image = o.container(service, "{{.Image}}")
        c.check("容器", f"{service} .Image {image[7:19]} = {'候选' if after else '回滚（现网）'}", image == want[service], image)
        c.check("容器", f"{service} healthy、重启 0", o.container(service, "{{.State.Health.Status}} {{.RestartCount}}") == "healthy 0")


def candidates():
    c.check("候选", "compose-candidate.yaml = 常量", o.compose_images(o.RELEASE / "compose-candidate.yaml") == o.CANDIDATE)
    for service, image in o.CANDIDATE.items():
        exists = o.docker("image", "inspect", image, "--format", "{{.Id}}").stdout.strip() == image
        if not c.check("候选", f"{service} 候选镜像存在 {image[7:19]}", exists):
            continue
        live = o.ROLLBACK[service]
        same = o.docker("image", "inspect", live, "--format", o.CONFIG).stdout == o.docker("image", "inspect", image, "--format", o.CONFIG).stdout
        c.check("候选", f"{service} 镜像配置与运行镜像相同（Env 只比较不输出）", same)
        if service in o.CANDIDATE_JAR:
            digest = o.docker("run", "--rm", "--network", "none", "--entrypoint", "sha256sum", image, "/app/application.jar").stdout[:64]
            c.check("候选", f"{service} JAR SHA-256 {o.CANDIDATE_JAR[service][:16]}", digest == o.CANDIDATE_JAR[service], digest)


def environment():
    for name in ("compose-candidate.yaml", "compose-rollback.yaml"):
        rendered = subprocess.run([str(Path(c.TEST_ENV) / "compose.sh"), "-f", str(o.RELEASE / name), "config", "--format", "json"],
                                  capture_output=True, text=True)
        if not c.check("环境", f"{name} 渲染", rendered.returncode == 0, rendered.stderr[:200]):
            continue
        services = json.loads(rendered.stdout)["services"]
        for service in o.SERVICES:
            svc = services[service]
            compose_env = {k: ("" if v is None else str(v)) for k, v in (svc.get("environment") or {}).items()}
            image_env = dict(e.split("=", 1) for e in json.loads(o.docker("image", "inspect", svc["image"], "--format", "{{json .Config.Env}}").stdout))
            expected = {**image_env, **compose_env}
            live = dict(e.split("=", 1) for e in json.loads(o.container(service, "{{json .Config.Env}}")))
            missing, extra = sorted(set(live) - set(expected)), sorted(set(expected) - set(live))
            differ = sorted(k for k in set(live) & set(expected) if live[k] != expected[k])
            c.check("环境", f"{name} {service} 0 缺 0 多 0 不同（compose {len(compose_env)} / 运行 {len(live)} 键）",
                    not (missing or extra or differ), f"缺 {missing} 多 {extra} 不同 {differ}")


def registration(after):
    rows = o.sql_file("00-precheck.sql")
    counts = {r[0]: int(r[1]) for r in rows if len(r) == 2 and r[1].isdigit() and r[0] != "routes_active"}
    if not after:
        for name, value in counts.items():
            c.check("SQL", f"00-precheck {name} = 0", value == 0, value)
    else:
        post = {r[0]: r for r in o.sql_file("99-postcheck.sql")}
        expect = {"permission": ("2", "ACTIVE"), "role_permission": ("2", "ACTIVE"), "console_resource": ("2", "ACTIVE"),
                  "gateway_route": ("5", "ACTIVE"), "route_header": ("18",), "route_query_param": ("5",), "guard_rule": ("30",)}
        for name, want in expect.items():
            got = post.get(name, [])
            c.check("SQL", f"99-postcheck {name} = {'/'.join(want)}", tuple(got[1:1 + len(want)]) == want, got)
    by_kind = {r[0]: r for r in rows}
    c.check("SQL", f"角色 900000000000000510 PLATFORM_ADMIN 存在（版本 {by_kind.get('role', [None] * 4)[3]}）",
            by_kind.get("role", [None, None, None])[2] == "PLATFORM_ADMIN")
    c.check("SQL", "Target 900000000000000001 operations-console-bff ACTIVE", by_kind.get("target", [])[2:4] == ["operations-console-bff", "ACTIVE"])
    c.check("SQL", "父菜单 900000000000000562 OPERATIONS_PLATFORM_CONFIGURATION ACTIVE",
            by_kind.get("menu_parent", [])[2:4] == ["OPERATIONS_PLATFORM_CONFIGURATION", "ACTIVE"])
    c.check("SQL", "S3_TEST / mor-test-platform-public-k7q2 为 PUBLIC 桶，桶与实例 ACTIVE",
            by_kind.get("bucket", [])[2:7] == ["S3_TEST", "mor-test-platform-public-k7q2", "PUBLIC", "ACTIVE", "ACTIVE"], by_kind.get("bucket"))
    c.check("SQL", "PUBLIC_DOCUMENT 有唯一 ACTIVE 修订", by_kind.get("policy", [None, None])[1] == "PUBLIC_DOCUMENT", by_kind.get("policy"))
    print(f"      记录 生效路由 {by_kind.get('routes_active', [None, None])[1]}")


def write_rules(after):
    rows = o.sql_rows("SELECT r.source_system, r.access_level, i.instance_code, b.bucket_name, r.scan_policy_code, r.max_upload_seconds "
                      "FROM t_file_storage_route r JOIN t_file_storage_bucket b ON b.bucket_id = r.bucket_id "
                      "JOIN t_file_storage_instance i ON i.instance_id = b.instance_id")
    rules = {(r[0], r[1]): tuple(r[2:]) for r in rows}
    instance, bucket, policy, seconds = o.TARGET
    c.check("规则", "(*, PUBLIC) → S3_TEST / mor-test-platform-public-k7q2 / PUBLIC_DOCUMENT（未建精确规则前的回落）",
            rules.get(("*", "PUBLIC"), ())[:3] == (instance, bucket, policy), rules.get(("*", "PUBLIC")))
    c.check("规则", "(operations-console-bff, PRIVATE) 不变：MINIO / mor-platform-files / OPERATIONS_BFF_IMPORT",
            rules.get((o.SOURCE, "PRIVATE"), ())[:3] == ("MINIO", "mor-platform-files", "OPERATIONS_BFF_IMPORT"), rules.get((o.SOURCE, "PRIVATE")))
    want = (instance, bucket, policy, str(seconds)) if after else None
    c.check("规则", f"(operations-console-bff, PUBLIC) {'= ' + '/'.join(map(str, want)) if after else '尚不存在'}",
            rules.get((o.SOURCE, "PUBLIC")) == want, rules.get((o.SOURCE, "PUBLIC")))


def localization(after):
    sums = dict(reversed(line.split(maxsplit=1)) for line in (o.RELEASE / "i18n-bundles/SHA256SUMS").read_text().splitlines())
    files = {}
    for locale in ("en", "zh-CN"):
        name = f"operations-console.{locale}.json"
        data = (o.RELEASE / "i18n-bundles" / name).read_bytes()
        c.check("多语言", f"{name} SHA-256 = SHA256SUMS", hashlib.sha256(data).hexdigest() == sums.get(name))
        files[locale] = json.loads(data)["namespaces"]
    source = {k: (ns, v if isinstance(v, str) else v["text"], None if isinstance(v, str) else v.get("description"))
              for ns, d in files["en"].items() for k, v in d.items()}
    zh = {k: v for d in files["zh-CN"].values() for k, v in d.items()}
    app = o.sql_rows("SELECT a.application_id, a.default_locale, r.release_no, r.status, r.key_count FROM t_i18n_application a "
                     "LEFT JOIN t_i18n_release r ON r.release_id = a.current_release_id WHERE a.application_code = 'operations-console'")
    if not c.check("多语言", "operations-console 存在且有当前发布", len(app) == 1 and app[0][2] != "NULL", app):
        return
    app_id, default_locale, release_no, release_status, key_count = app[0]
    c.check("多语言", f"当前发布 #{release_no} READY（{key_count} key，默认 {default_locale}）", release_status == "READY")
    pending = o.sql_rows(f"SELECT release_no, status FROM t_i18n_release WHERE application_id = {app_id} AND status NOT IN ('READY','FAILED')")
    c.check("多语言", "没有进行中的发布", not pending, pending)
    live = o.sql_json(
        "SELECT JSON_OBJECT('k', k.key_code, 's', k.source_text, 'd', k.description, 'st', k.status, 'ns', n.namespace_code, 'zh', t.text) "
        "FROM t_i18n_key k JOIN t_i18n_namespace n ON n.namespace_id = k.namespace_id "
        "LEFT JOIN t_i18n_translation t ON t.key_id = k.key_id AND t.locale_code = 'zh-CN' "
        f"WHERE k.application_id = {app_id}")
    active = {x["k"]: x for x in live if x["st"] == "ACTIVE"}
    namespaces = sorted(r[0] for r in o.sql_rows(f"SELECT namespace_code FROM t_i18n_namespace WHERE application_id = {app_id}"))
    c.check("多语言", f"线上命名空间 = 输入的 21 个（file、nav 已存在，不新建）", namespaces == sorted(files["en"]), sorted(set(namespaces) ^ set(files["en"])))
    c.check("多语言", f"线上 {len(active)} 个 ACTIVE key 全部在输入中（导入废弃 0）", not [k for k in active if k not in source],
            [k for k in active if k not in source][:5])
    changed = [k for k, x in active.items() if k in source and (source[k][0], source[k][1], source[k][2]) != (x["ns"], x["s"], x["d"])]
    c.check("多语言", "已有 key 的英文基准、说明与命名空间与输入逐条相同（基准变更 0）", not changed, changed[:5])
    zh_changed = [k for k, x in active.items() if x["zh"] is not None and zh.get(k) != x["zh"]]
    c.check("多语言", "已有 key 的线上 zh-CN 翻译与输入逐条相同（不覆盖已有翻译）", not zh_changed, zh_changed[:5])
    new = sorted(set(source) - set(active))
    expected_new = all(k.startswith("file.upload.") or k in ("nav.resource.OPERATIONS_FILE_UPLOAD", "nav.resource.OPERATIONS_FILE_UPLOAD_CREATE")
                       for k in new)
    if after:
        c.check("多语言", f"新增 key 已全部上线（输入 {len(source)}，线上 ACTIVE {len(active)}）", not new, new[:5])
    else:
        c.check("多语言", f"输入比线上多 {len(new)} 个 key，全部为 file.upload.* 与两个 nav.resource.OPERATIONS_FILE_UPLOAD*", new and expected_new,
                [k for k in new if not k.startswith("file.upload.")])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--after", action="store_true")
    args = parser.parse_args()
    containers(args.after)
    candidates()
    environment()
    registration(args.after)
    write_rules(args.after)
    localization(args.after)
    return 0 if c.summary() else 1


if __name__ == "__main__":
    sys.exit(main())
