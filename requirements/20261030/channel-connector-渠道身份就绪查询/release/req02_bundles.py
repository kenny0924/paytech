"""REQ-02 渠道身份就绪查询 · mor-test 语言包发布（runbook 语言包步），只发 operations-console 一个版本。
改写自 payout release/payout_bundles.py（换允许的命名空间、源码根、发布说明与状态目录；不新建命名空间；废弃恰好 2 个预期 key）；
底层复用参考数据发布的 refdata_bundles.py：
  a. 仓库源 = --web-root 下的 operations-console-web（缺省候选 worktree Code-MoR-req02-rel = main 331d8e0e
     + 明细账运维 ops-web 18 个文件（现网 plops-20261030a 的源码）+ 本需求 68 个文件）
     + 参考数据发布的 refdata 语言包（KEY_IMPORT 是全集导入，必须带上 refdata 命名空间，否则会把它整体废弃）。
     **必须以候选树为源**：若以不含明细账运维文件的树为源，会把现网 finance / nav 的明细账运维键标为废弃（门禁会拦下）。
  b. 门禁：新增 / 基准变更 / 废弃 / zh-CN 导入只允许在 commercial；不新建命名空间；
     废弃恰好为 EXPECTED_DEPRECATIONS 两个 key（需 --accept-deprecations）；仓库 zh-CN 与线上不同却不会导入的 key 为 0（KNOWN_LIVE_ZH 例外）。
  c. 不带 --apply 时所有 SQL 在 READ ONLY 会话中执行，不登录、不调用写接口。发布号由 Platform 取下一个空闲号，本脚本不写死号码
     （现网运营包 #30，#31 已由 REQ-06 预约，本需求的号与先后顺序由主会话协调）；若 REQ-06 先发，重新干跑，计划应只剩本需求差异。
  d. 运行记录目录 I18N7_STATE（缺省 ~/Code-MoR-backups/req02-stage6/i18n）。
  client-console：本需求不涉及 Client 门户，不发 client-console 语言包。
用法：python3 -I req02_bundles.py [--web-root <工作区>] --accept-deprecations [--apply]
回滚（发布不可变）：本次只改 commercial 命名空间（新增 17、基准变更 3、废弃 2）；旧 Web 读到的新基准文案语义兼容（"暂不核对"、
  渠道身份 select 分支），一般不需要回滚语言包；必须恢复时以现网源码（Code-MoR-plops-rel 的 operations-console-web，
  dist 与现网 plops-20261030a 5784fa81 逐字节相同）为 --web-root 重新发布（会把本需求 17 键标为废弃、恢复 2 个旧键）。
"""
import importlib.util
import json
import os
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
MOR_ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))
REFDATA_RELEASE = MOR_ROOT / "requirements/20261027/platform-support-参考数据目录与client-master迁移/release"
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/req02-stage6/i18n"))

spec = importlib.util.spec_from_file_location("refdata_bundles", REFDATA_RELEASE / "refdata_bundles.py")
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
c = base.c

base.REFDATA_BUNDLES = REFDATA_RELEASE / "i18n-bundles"
base.RELEASE_NOTE = "REQ-02：渠道身份就绪查询——商务 · 合同详情\"还差什么\"渠道身份原因与去配置 MID 链接（2026-10-30）"
DEFAULT_WEB_ROOT = "/Users/kenny/Desktop/Workspace/Code-MoR-req02-rel"
ALLOWED = {"operations-console": {"commercial"}}
EXPECTED_NEW_NAMESPACES = []
EXPECTED_DEPRECATIONS = [
    "commercial.enum.CapabilityPrerequisiteReason.CHANNEL_IDENTITY_NOT_CONNECTED",
    "commercial.prerequisite.shortReason.CHANNEL_IDENTITY_NOT_CONNECTED",
]
ALLOWED_NS = set()
# 线上 zh-CN 由用户批准的 R2 例外手工改为中文（operations-console #7），仓库种子仍是英文；保持线上，不导入（同 REQ-03 / tax / REQ-04 / payout / 明细账运维）。
KNOWN_LIVE_ZH = {"nav.sidebar.hide", "nav.sidebar.product", "nav.sidebar.show"}


def read_only_sql(database, statement):
    """dry-run 专用：READ ONLY 会话，任何写语句都会被 MySQL 拒绝（ERROR 1792）。"""
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={c.env()['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql",
                             "-uroot", "-N", "-B", "--default-character-set=utf8mb4",
                             "--init-command=SET SESSION TRANSACTION READ ONLY", database, "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


plan_for_base = base.plan_for


def namespace_of(key, live, source_ns):
    if key in source_ns:
        return source_ns[key]
    return live[key]["ns"] if key in live else key.split(".", 1)[0]


def plan_for(app, en, zh, live_namespaces, max_sort, live, current, live_outdated):
    p = plan_for_base(app, en, zh, live_namespaces, max_sort, live, current, live_outdated)
    source_ns = {k: ns for ns, entries in en.items() for k in entries}
    imported = {k for entries in p["translation"].values() for k in entries}
    zh_flat = {k: v for entries in zh.values() for k, v in entries.items()}
    p["zhChangedNotImported"] = sorted(
        k for k, r in live.items() if r["st"] == "ACTIVE" and r["zh"] is not None and k not in imported
        and k in zh_flat and str(zh_flat[k]) != r["zh"] and base.text_of(en.get(r["ns"], {}).get(k)) is not None
        and k not in KNOWN_LIVE_ZH)
    p["byNamespace"] = {}
    for label, keys in (("added", p["added"]), ("sourceChanged", p["sourceChanged"]), ("deprecated", p["missingInRepo"]),
                        ("restored", p["restored"]), ("zhImport", sorted(imported))):
        counts = {}
        for k in keys:
            ns = namespace_of(k, live, source_ns)
            counts[ns] = counts.get(ns, 0) + 1
        p["byNamespace"][label] = counts
    return p


check_plan_base = base.check_plan


def check_plan(p, problems, accept_deprecations):
    ok = check_plan_base(p, problems, accept_deprecations)
    for label in ("added", "sourceChanged", "deprecated", "restored", "zhImport"):
        outside = sorted(ns for ns in p["byNamespace"][label] if ns not in ALLOWED_NS)
        ok &= base.check(p, f"{label} 只在 {sorted(ALLOWED_NS)}（{p['byNamespace'][label]}）", not outside, outside)
    ok &= base.check(p, f"废弃恰好为预期 {len(EXPECTED_DEPRECATIONS)} 个 key（{sorted(p['missingInRepo'])}）",
                     sorted(p["missingInRepo"]) == sorted(EXPECTED_DEPRECATIONS), sorted(p["missingInRepo"])[:10])
    ok &= base.check(p, f"仓库 zh-CN 与线上不同但不会导入的 key 为 0（{len(p['zhChangedNotImported'])}）",
                     not p["zhChangedNotImported"], p["zhChangedNotImported"][:10])
    new_codes = [n["code"] for n in p["newNamespaces"]]
    ok &= base.check(p, f"新建命名空间恰好为 {EXPECTED_NEW_NAMESPACES}（{p['newNamespaces']}）", new_codes == EXPECTED_NEW_NAMESPACES)
    print(json.dumps({"byNamespace": p["byNamespace"], "zhChangedNotImported": p["zhChangedNotImported"],
                      "added": p["added"], "sourceChanged": p["sourceChanged"], "deprecated": p["missingInRepo"]}, ensure_ascii=False))
    return ok


base.plan_for = plan_for
base.check_plan = check_plan


def main():
    argv = sys.argv[1:]
    if "--web-root" not in argv:
        argv = ["--web-root", DEFAULT_WEB_ROOT] + argv
    if "--out" not in argv:
        argv = ["--out", str(HERE / "i18n-bundles")] + argv
    if "--app" not in argv:
        argv = ["--app", "operations-console"] + argv
    ALLOWED_NS.update(ALLOWED[argv[argv.index("--app") + 1]])
    if "--apply" not in argv:
        c.sql = read_only_sql
    sys.argv = [sys.argv[0]] + argv
    return base.main()


if __name__ == "__main__":
    sys.exit(main())
