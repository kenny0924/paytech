#!/usr/bin/env python3
"""离线演练用：把 worktree 的 <svc>-schema.sql 装进一次性容器的 wt_<库>，与按发布 SQL 迁移后的 <库> 比较表、列（类型、可空、缺省、
字符集、注释）、索引与表注释。只在 rehearse_offline.sh 中对断网容器使用。用法：schema_drift.py <容器名> [<git 提交>]
给出提交时读该提交的 schema 文件（迁移前基线：备份库对 main 8a86a31c，区分既有漂移与本次迁移造成的差异）。"""
import subprocess
import sys
from pathlib import Path

CONTAINER = sys.argv[1]
REF = sys.argv[2] if len(sys.argv) > 2 else None
ROOT = Path(__file__).resolve().parents[5]
SERVICES = {"compliance": "mor_compliance", "client-master": "mor_client_master", "acquiring": "mor_acquiring",
            "risk-decision": "mor_risk_decision"}


def mysql(sql, db=None, stdin=None):
    cmd = ["docker", "exec", "-i", CONTAINER, "sh", "-c",
           "MYSQL_PWD=throwaway mysql -uroot --default-character-set=utf8mb4 -N -B" + (f" {db}" if db else "")
           + ("" if stdin else f" -e \"{sql}\"")]
    return subprocess.run(cmd, input=stdin, capture_output=True, text=True, check=True).stdout


def shape(db):
    cols = mysql("SELECT table_name, column_name, column_type, is_nullable, IFNULL(column_default,'<null>'), IFNULL(collation_name,''), "
                 f"column_comment FROM information_schema.columns WHERE table_schema='{db}' ORDER BY table_name, column_name")
    idx = mysql("SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics "
                f"WHERE table_schema='{db}' GROUP BY table_name, index_name, non_unique ORDER BY 1, 2")
    tabs = mysql(f"SELECT table_name, table_comment FROM information_schema.tables WHERE table_schema='{db}' ORDER BY 1")
    return set(cols.splitlines()), set(idx.splitlines()), set(tabs.splitlines())


total = 0
for svc, db in SERVICES.items():
    wt = "wt_" + db
    rel = f"paytech/{svc}/docs/database/sql/{svc}-schema.sql"
    schema = (subprocess.run(["git", "-C", str(ROOT), "show", f"{REF}:{rel}"], capture_output=True, text=True, check=True).stdout
              if REF else (ROOT / rel).read_text(encoding="utf-8"))
    mysql(f"DROP DATABASE IF EXISTS {wt}; CREATE DATABASE {wt} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    mysql(None, db=wt, stdin=schema)
    migrated, expected = shape(db), shape(wt)
    for kind, a, b in zip(("column", "index", "table"), migrated, expected):
        only_migrated, only_expected = sorted(a - b), sorted(b - a)
        for row in only_migrated:
            print(f"{db} {kind} only-after-migration: {row[:220]}")
        for row in only_expected:
            print(f"{db} {kind} only-in-schema-file: {row[:220]}")
        total += len(only_migrated) + len(only_expected)
    mysql(f"DROP DATABASE {wt}")
    print(f"{db}: compared columns/indexes/table comments with {svc}-schema.sql" + (f" @ {REF}" if REF else " (worktree)"))
print(f"SCHEMA DRIFT ROWS: {total}")
