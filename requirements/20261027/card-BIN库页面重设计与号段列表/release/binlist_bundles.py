"""card：BIN 库页面重设计与号段列表 · mor-test 语言包发布（runbook 第 ⑧ 步），只处理 operations-console。
复用参考数据发布的 refdata_bundles.py（mor-test 已用于 ops #13，文件上传页面整理又用于 #15—#17），只覆盖：
  a. 应用只有 operations-console；仓库源 = --web-root 下的 operations-console-web（缺省 Code-MoR-binlist-webrel
     = c4b99e2d + 本需求 11 个 ops-web 文件，即现网 ops-web 源码 + 本需求页面）+ 参考数据发布的 refdata 语言包（KEY_IMPORT 是全集导入，
     必须带上 refdata 命名空间，否则会把它整体废弃）。
  b. 发布说明、运行记录目录（I18N7_STATE 缺省 ~/Code-MoR-backups/20261027-binlist-i18n）。
  c. 不带 --apply 时所有 SQL 在 READ ONLY 会话中执行（SET SESSION TRANSACTION READ ONLY），并且不登录、不调用写接口。
  d. 计划额外列出 zhChangedNotImported（除 KNOWN_LIVE_ZH 的 3 个 nav.sidebar 例外）：基准未变、线上已有译文、但仓库 zh-CN 与线上不同的 key——基础逻辑"线上已有且基准未变的不覆盖"
     不会导入它们；不为空时停下由用户决定（本需求预期为 0）。
其余步骤（建命名空间 → KEY_IMPORT → zh-CN 增量 → 复核未触及译文 → 确认基准已变更译文 → 预检发布 → 匿名 manifest）与可重跑性同原脚本。
用法：python3 -I binlist_bundles.py [--web-root <含 operations-console-web 的工作区>] [--out <目录>] [--accept-deprecations] [--apply]
回滚（发布不可变）：python3 -I binlist_bundles.py --web-root /Users/kenny/Desktop/Workspace/Code-MoR-binlist-c4b --accept-deprecations
  （= 现网源码：恢复本需求废弃的 card key、废弃本需求新增的 card key）→ 核对计划后加 --apply 发布新号。
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
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/20261027-binlist-i18n"))

spec = importlib.util.spec_from_file_location("refdata_bundles", REFDATA_RELEASE / "refdata_bundles.py")
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
c = base.c

APP = "operations-console"
base.APPS = {APP: "operations-console-web"}
base.REFDATA_BUNDLES = REFDATA_RELEASE / "i18n-bundles"
base.RELEASE_NOTE = "card：BIN 库页面重设计与号段列表——号段数据 / 版本页签、筛选与 BIN 匹配、操作人显示名（2026-10-27）"
DEFAULT_WEB_ROOT = "/Users/kenny/Desktop/Workspace/Code-MoR-binlist-webrel"
# 线上 zh-CN 由用户批准的 R2 例外手工改为中文（语言包 #7，2026-10-24，release/fix-20261024c），仓库种子仍是英文；保持线上，不导入。
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
        ns = k.split(".", 1)[0]
        p["addedByNamespace"][ns] = p["addedByNamespace"].get(ns, 0) + 1
    return p


check_plan_base = base.check_plan


def check_plan(p, problems, accept_deprecations):
    ok = check_plan_base(p, problems, accept_deprecations)
    outside = sorted(ns for ns in p["deprecationsByNamespace"] if ns != "card")
    ok &= base.check(p, f"废弃 key 只在 card 命名空间（{p['deprecationsByNamespace']}）", not outside, outside)
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
    argv = ["--app", APP] + argv
    if "--apply" not in argv:
        c.sql = read_only_sql
    sys.argv = [sys.argv[0]] + argv
    return base.main()


if __name__ == "__main__":
    sys.exit(main())
