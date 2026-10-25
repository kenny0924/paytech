#!/usr/bin/env python3
"""离线演练用：回滚全部执行后，把六个备份另行导入为 base_<库>，与回滚后的 <库> 逐表比较结构（SHOW CREATE TABLE）和数据
（逐行内容，忽略 update_time；行按主键排序后取 SHA-256）。只在 rehearse_offline.sh 中对断网容器使用。
用法：compare_with_backup.py <容器名> <备份目录>"""
import hashlib
import re
import subprocess
import sys
from pathlib import Path

CONTAINER, BACKUP = sys.argv[1], Path(sys.argv[2])
DBS = ["mor_compliance", "mor_client_master", "mor_commercial", "mor_acquiring", "mor_risk_decision", "mor_platform"]


def mysql(sql=None, db=None, stdin=None):
    """SQL 一律经标准输入传给容器内 mysql（避免 sh -c 把反引号当命令替换）。"""
    cmd = ["docker", "exec", "-i", "-e", "MYSQL_PWD=throwaway", CONTAINER, "mysql", "-uroot", "--default-character-set=utf8mb4", "-N", "-B"]
    cmd += [db] if db else []
    return subprocess.run(cmd, input=stdin if stdin is not None else sql, capture_output=True, text=True, check=True).stdout


def table_digest(db, table):
    cols = [c for c in mysql(f"SELECT column_name FROM information_schema.columns WHERE table_schema='{db}' AND table_name='{table}' "
                             "ORDER BY ordinal_position", ).split() if c != "update_time"]
    pk = mysql(f"SELECT column_name FROM information_schema.key_column_usage WHERE table_schema='{db}' AND table_name='{table}' "
               "AND constraint_name='PRIMARY' ORDER BY ordinal_position").split() or cols
    rows = mysql(f"SELECT {','.join('`%s`' % c for c in cols)} FROM `{db}`.`{table}` ORDER BY {','.join('`%s`' % c for c in pk)}")
    return len(rows.splitlines()), hashlib.sha256(rows.encode()).hexdigest()


def create(db, table):
    text = mysql(f"SHOW CREATE TABLE `{db}`.`{table}`").split("\t", 1)[1]
    return re.sub(r" AUTO_INCREMENT=\d+", "", text)


diffs = 0
for db in DBS:
    base = "base_" + db
    dump = (BACKUP / f"{db}-full.sql").read_text(encoding="utf-8")
    dump = dump.replace(f"CREATE DATABASE /*!32312 IF NOT EXISTS*/ `{db}`", f"CREATE DATABASE /*!32312 IF NOT EXISTS*/ `{base}`")
    dump = dump.replace(f"USE `{db}`;", f"USE `{base}`;")
    mysql(f"DROP DATABASE IF EXISTS {base}")
    mysql(stdin=dump)
    now = set(mysql(f"SELECT table_name FROM information_schema.tables WHERE table_schema='{db}'").split())
    then = set(mysql(f"SELECT table_name FROM information_schema.tables WHERE table_schema='{base}'").split())
    for t in sorted(now ^ then):
        print(f"{db}: table only in {'rolled-back' if t in now else 'backup'}: {t}"); diffs += 1
    for t in sorted(now & then):
        if create(db, t) != create(base, t):
            print(f"{db}.{t}: DDL differs"); diffs += 1
        a, b = table_digest(db, t), table_digest(base, t)
        if a != b:
            print(f"{db}.{t}: rows differ (rolled-back {a[0]} rows, backup {b[0]} rows; update_time ignored)"); diffs += 1
    routines = mysql(f"SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema='{db}'").strip()
    print(f"{db}: compared {len(now & then)} tables; leftover routines {routines}")
    mysql(f"DROP DATABASE {base}")
print(f"DIFFERENCES: {diffs}")
