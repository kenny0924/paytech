"""platform-support：workflow 按钮发起与数据验证 REQ-05 · mor-test 语言包发布（runbook 第 ⑩ 步），只发 operations-console。
改写自 REQ-01 release/gate_bundles.py（只换允许的命名空间、源码根、发布说明与状态目录）。
复用参考数据发布的 refdata_bundles.py（mor-test 已用于 #13、#15—#19），包装方式同国家包法务修订的 legal_bundles.py：
  a. 仓库源 = --web-root 的 operations-console-web（缺省 Code-MoR-wfdq-rel = 8d24df8a（含 REQ-01 ba0e15b5，dist 与现网 8aec3df8 逐字节相同）+ 本需求文件）
     + 参考数据发布的 refdata 语言包（KEY_IMPORT 是全集导入，必须带上 refdata 命名空间，否则会把它整体废弃）。
     现网 ops-web 的全部源码都在 8d24df8a 里，所以 KEY_IMPORT 的英文全集只在 workflow 命名空间与 #20 不同。
  b. 门禁：废弃 / 新增 / 基准变更 key 只允许在 workflow；zh-CN 导入集合只含 workflow 命名空间（#20 其余译文不被覆盖）；仓库 zh-CN 与线上不同却不会导入的 key 为 0（KNOWN_LIVE_ZH 例外）。
  c. 不带 --apply 时所有 SQL 在 READ ONLY 会话中执行，不登录、不调用写接口。
  d. 运行记录目录 I18N7_STATE（缺省 ~/Code-MoR-backups/wfdq-20261029-i18n）。
用法：python3 -I wfdq_bundles.py [--web-root <工作区>] [--accept-deprecations] [--apply]
回滚（发布不可变）：以现网源码重新发布——--web-root 指向 8d24df8a 的干净 worktree（ops-web 与现网 8aec3df8 dist 逐字节相同）→ 核对计划后加 --apply；
  本次只新增 / 改写 workflow key，旧 Web 读新包不受影响，一般不需要回滚语言包。
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
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/wfdq-20261029-i18n"))

spec = importlib.util.spec_from_file_location("refdata_bundles", REFDATA_RELEASE / "refdata_bundles.py")
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
c = base.c

base.REFDATA_BUNDLES = REFDATA_RELEASE / "i18n-bundles"
base.RELEASE_NOTE = "workflow：按钮发起与数据验证——按编码发起、DATA_QUERY 系统检查、检查状态与立即检查（2026-10-29）"
DEFAULT_WEB_ROOT = "/Users/kenny/Desktop/Workspace/Code-MoR-wfdq-rel"
APP = "operations-console"
ALLOWED_NS = {"workflow"}
# 线上 zh-CN 由用户批准的 R2 例外手工改为中文（operations-console #7），仓库种子仍是英文；保持线上，不导入（同法务修订）。
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
    ok &= base.check(p, f"仓库 zh-CN 与线上不同但不会导入的 key 为 0（{len(p['zhChangedNotImported'])}）",
                     not p["zhChangedNotImported"], p["zhChangedNotImported"][:10])
    ok &= base.check(p, f"不新建命名空间（{[n['code'] for n in p['newNamespaces']]}）", not p["newNamespaces"])
    print(json.dumps({"byNamespace": p["byNamespace"], "zhChangedNotImported": p["zhChangedNotImported"]}, ensure_ascii=False))
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
        argv = ["--app", APP] + argv
    if "--apply" not in argv:
        c.sql = read_only_sql
    sys.argv = [sys.argv[0]] + argv
    return base.main()


if __name__ == "__main__":
    sys.exit(main())
