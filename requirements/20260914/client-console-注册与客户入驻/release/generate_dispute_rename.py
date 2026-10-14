"""D2：生成 mor-test Risk 争议 4 表 Provider→Channel 的 ALTER 与回滚 SQL（“支付渠道命名统一”的结构迁移）。

目标定义逐行取自正式 Risk Schema；回滚定义逐行取自 mor-test 当前 SHOW CREATE TABLE（只读）。
列按 provider_→channel_ 一一改名（CHANGE COLUMN，类型/可空不变），注释不同的列 MODIFY，
索引列或注释不同的先 DROP 再按 Schema ADD，表注释同步。只适用于这 4 张表为空的环境。
用法：python3 -I generate_dispute_rename.py   → 写出 risk-dispute-channel-rename.sql 与 -rollback.sql
"""
import re
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
SCHEMA = ROOT / "risk-compliance-service/docs/database/sql/risk-compliance-schema.sql"
TABLES = ("t_dispute_case", "t_dispute_liability_decision", "t_dispute_outcome", "t_dispute_submission_request")


def password():
    for line in (ROOT / "test-environment/.env.test").read_text().splitlines():
        if line.startswith("MOR_MYSQL_ROOT_PASSWORD="):
            return line.split("=", 1)[1]
    raise SystemExit("缺少 MOR_MYSQL_ROOT_PASSWORD")


def live_create(table):
    result = subprocess.run(
        ["docker", "exec", "-i", "-e", "MYSQL_PWD=" + password(), "mor-test-mysql-1", "mysql", "-uroot",
         "--default-character-set=utf8mb4", "-N", "-B", "--raw", "mor_risk"],
        input=f"SHOW CREATE TABLE `{table}`;", text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise SystemExit("读取 SHOW CREATE TABLE 失败")
    return result.stdout.split("\t", 1)[1]


def parse(create):
    """→ (列名→定义行, 列顺序, 索引名→定义行, 表注释字面量)。"""
    body, options = re.search(r"\((.*)\n\)\s*([^;]*)", create, re.S).groups()
    columns, order, indexes = {}, [], {}
    for raw in body.split("\n"):
        line = raw.strip().rstrip(",")
        if not line:
            continue
        index = re.match(r"(PRIMARY KEY|UNIQUE KEY `(\w+)`|KEY `(\w+)`)", line)
        if index:
            indexes["PRIMARY" if index.group(1) == "PRIMARY KEY" else (index.group(2) or index.group(3))] = line
            continue
        name = re.match(r"`(\w+)`", line).group(1)
        columns[name] = line
        order.append(name)
    comment = re.search(r"COMMENT\s*=\s*('(?:[^']|'')*')", options).group(1)
    return columns, order, indexes, comment


def same(left, right):
    """忽略书写差异（大小写、BOOLEAN/tinyint(1)、显式 NULL/DEFAULT NULL、索引列间空格）比较两条定义。"""
    def canonical(line):
        head, _, comment = line.partition(" COMMENT ")
        head = head.lower().replace("boolean", "tinyint(1)").replace("`, `", "`,`")
        head = re.sub(r" default null\b", "", head)
        head = re.sub(r"(?<!not) null\b", "", head)
        return head.strip() + " COMMENT " + comment
    return left is not None and right is not None and canonical(left) == canonical(right)


def schema_create(text, table):
    return re.search(rf"CREATE TABLE\s+`{table}`\s*\((.*?)\n\)\s*[^;]*;", text, re.S).group(0)


def alter(table, source, target, rename):
    """生成把 source 结构变为 target 的单条 ALTER；rename 为 source 名→target 名。"""
    s_cols, s_order, s_idx, s_comment = source
    t_cols, t_order, t_idx, t_comment = target
    clauses = [f"DROP INDEX `{name}`" for name in s_idx
               if name != "PRIMARY" and not same(s_idx[name], t_idx.get(name))]
    previous = None
    for name in t_order:
        old = next((o for o, n in rename.items() if n == name), name)
        position = f" AFTER `{previous}`" if previous else " FIRST"
        if old != name:
            clauses.append(f"CHANGE COLUMN `{old}` {t_cols[name]}{position}")
        elif not same(s_cols[name], t_cols[name]):
            clauses.append(f"MODIFY COLUMN {t_cols[name]}{position}")
        previous = name
    clauses += [f"ADD {t_idx[name]}" for name in t_idx if name != "PRIMARY" and not same(t_idx[name], s_idx.get(name))]
    if s_comment != t_comment:
        clauses.append(f"COMMENT = {t_comment}")
    return f"ALTER TABLE `{table}`\n  " + ",\n  ".join(clauses) + ";\n"


def main():
    schema = SCHEMA.read_text(encoding="utf-8")
    forward, backward = [], []
    for table in TABLES:
        live = parse(live_create(table))
        target = parse(schema_create(schema, table))
        rename = {old: "channel_" + old[len("provider_"):] for old in live[1] if old.startswith("provider_")}
        missing = [new for new in rename.values() if new not in target[0]]
        if missing or set(target[1]) != {rename.get(c, c) for c in live[1]}:
            raise SystemExit(f"{table}: 列集合不是纯改名，停止（{missing}）")
        forward.append(alter(table, live, target, rename))
        backward.append(alter(table, target, live, {v: k for k, v in rename.items()}))
    header = ("-- mor-test Risk 争议 4 表 Provider→Channel（D2，用户 2026-10-14 批准发布清单）。由 generate_dispute_rename.py 生成，勿手改。\n"
              "-- 前置：4 表均为 0 行；DDL 自动提交，逐条执行，不使用 --force。\n")
    (HERE / "risk-dispute-channel-rename.sql").write_text(header + "\n".join(forward), encoding="utf-8")
    (HERE / "risk-dispute-channel-rename-rollback.sql").write_text(
        header.replace("Provider→Channel", "回滚 Channel→Provider（回退旧 Risk 镜像前执行）") + "\n".join(backward),
        encoding="utf-8")
    print("written: risk-dispute-channel-rename.sql, risk-dispute-channel-rename-rollback.sql")


if __name__ == "__main__":
    main()
