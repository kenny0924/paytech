"""只读核对：以 root 经 docker exec mor-local-mysql-1 执行 SELECT，口令经 MYSQL_PWD 环境变量传入，不打印。

用法：python3 -I rodb.py "<SELECT ...>"；或 import rodb; rodb.q(sql)。只允许 SELECT / SHOW / DESC。
"""
import os
import subprocess
import sys
from pathlib import Path

ENV_LOCAL = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local")


def _root_password():
    for line in ENV_LOCAL.read_text().splitlines():
        if line.startswith("MOR_MYSQL_ROOT_PASSWORD="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit("缺少 MOR_MYSQL_ROOT_PASSWORD")


def q(statement):
    head = statement.lstrip().split(None, 1)[0].upper()
    if head not in ("SELECT", "SHOW", "DESC", "DESCRIBE", "WITH"):
        raise SystemExit("rodb 只允许只读语句")
    result = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-local-mysql-1", "mysql", "-uroot", "-N", "-B",
                             "--default-character-set=utf8mb4", "-e", statement],
                            env={**os.environ, "MYSQL_PWD": _root_password()}, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError("SQL 失败：" + result.stderr.strip()[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


if __name__ == "__main__":
    for row in q(sys.argv[1]):
        print("\t".join(row))
