"""client-master：国家包法务修订 · mor-test 语言包发布（runbook 第 ⑨ 步），client-console 与 operations-console。
复用参考数据发布的 refdata_bundles.py（mor-test 已用于 #13、#15—#18），覆盖方式同 card-BIN 的 binlist_bundles.py：
  a. 仓库源 = --web-root 下两个 *-console-web（缺省 Code-MoR-legal-rel = e2185c1b + 本需求改动）+ 参考数据发布的 refdata 语言包
     （KEY_IMPORT 是全集导入，必须带上 refdata 命名空间，否则会把它整体废弃）。
  b. 发布说明、运行记录目录（I18N7_STATE 缺省 ~/Code-MoR-backups/legal-20261028-i18n）。
  c. 不带 --apply 时所有 SQL 在 READ ONLY 会话中执行，不登录、不调用写接口。
  d. 门禁：废弃 key 只允许在 ALLOWED_DEPRECATION_NS；仓库 zh-CN 与线上不同却不会导入的 key 为 0（KNOWN_LIVE_ZH 例外）。
用法：python3 -I legal_bundles.py [--app client-console|operations-console] [--web-root <工作区>] [--accept-deprecations] [--apply]
回滚（发布不可变）：以现网源码重新发布——--web-root 指向 e2185c1b 干净工作树（ops-web / client-web 与现网 dist 逐字节相同）→ 核对计划后加 --apply。
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
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/legal-20261028-i18n"))

spec = importlib.util.spec_from_file_location("refdata_bundles", REFDATA_RELEASE / "refdata_bundles.py")
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
c = base.c

base.REFDATA_BUNDLES = REFDATA_RELEASE / "i18n-bundles"
base.RELEASE_NOTE = "client-master：国家包法务修订——声明阅读与失效、法务修订字段、运营声明区与规则标签（2026-10-28）"
DEFAULT_WEB_ROOT = "/Users/kenny/Desktop/Workspace/Code-MoR-legal-rel"
ALLOWED_DEPRECATION_NS = {"onboarding-schema"}
# 线上 zh-CN 由用户批准的 R2 例外手工改为中文（operations-console #7），仓库种子仍是英文；保持线上，不导入。
KNOWN_LIVE_ZH = {"nav.sidebar.hide", "nav.sidebar.product", "nav.sidebar.show",
                 # client-console：线上 zh-CN 来自多语言管理 #1（2026-10-23，"申请不存在" / "重新读取"），切片 1（9f506f0f）后改了仓库种子但未导入；
                 # 与本需求无关，保持线上。
                 "onboarding.page.not_found", "onboarding.page.reload"}


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


def plan_for(app, en, zh, live_namespaces, max_sort, live, current, live_outdated):
    p = plan_for_base(app, en, zh, live_namespaces, max_sort, live, current, live_outdated)
    imported = {k for entries in p["translation"].values() for k in entries}
    zh_flat = {k: v for entries in zh.values() for k, v in entries.items()}
    p["zhChangedNotImported"] = sorted(
        k for k, r in live.items() if r["st"] == "ACTIVE" and r["zh"] is not None and k not in imported
        and k in zh_flat and str(zh_flat[k]) != r["zh"] and base.text_of(en.get(r["ns"], {}).get(k)) is not None
        and k not in KNOWN_LIVE_ZH)
    p["deprecationsByNamespace"] = {}
    for k in p["missingInRepo"]:
        ns = live[k]["ns"]
        p["deprecationsByNamespace"][ns] = p["deprecationsByNamespace"].get(ns, 0) + 1
    p["addedByNamespace"] = {}
    for k in p["added"]:
        ns = live[k]["ns"] if k in live else k.split(".", 1)[0]
        p["addedByNamespace"][ns] = p["addedByNamespace"].get(ns, 0) + 1
    return p


check_plan_base = base.check_plan


def check_plan(p, problems, accept_deprecations):
    ok = check_plan_base(p, problems, accept_deprecations)
    outside = sorted(ns for ns in p["deprecationsByNamespace"] if ns not in ALLOWED_DEPRECATION_NS)
    ok &= base.check(p, f"废弃 key 只在 {sorted(ALLOWED_DEPRECATION_NS)}（{p['deprecationsByNamespace']}）", not outside, outside)
    ok &= base.check(p, f"仓库 zh-CN 与线上不同但不会导入的 key 为 0（{len(p['zhChangedNotImported'])}）",
                     not p["zhChangedNotImported"], p["zhChangedNotImported"][:10])
    print(json.dumps({"addedByNamespace": p["addedByNamespace"], "deprecationsByNamespace": p["deprecationsByNamespace"],
                      "zhChangedNotImported": p["zhChangedNotImported"]}, ensure_ascii=False))
    return ok


base.plan_for = plan_for
base.check_plan = check_plan


def main():
    argv = sys.argv[1:]
    if "--web-root" not in argv:
        argv = ["--web-root", DEFAULT_WEB_ROOT] + argv
    if "--out" not in argv:
        argv = ["--out", str(HERE / "i18n-bundles")] + argv
    if "--apply" not in argv:
        c.sql = read_only_sql
    sys.argv = [sys.argv[0]] + argv
    return base.main()


if __name__ == "__main__":
    sys.exit(main())
