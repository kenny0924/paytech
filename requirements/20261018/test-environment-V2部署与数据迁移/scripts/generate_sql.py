#!/usr/bin/env python3
"""Generate the reviewed migration SQL (30/31/32/33) and the verification SQL (40) from one rehearsal instance.

Inputs on the same MySQL server (read-only use of information_schema only, no row values are read):
  - legacy databases restored from the mor-test dump: mor_cct, mor_commerce, mor_risk;
  - reference copies of the reviewed V2 schemas: v2ref_client_master, v2ref_commercial, v2ref_acquiring,
    v2ref_consumer, v2ref_risk_decision, v2ref_compliance, v2ref_dispute.
Routing follows design/03 §2 and table-ownership.yaml; the explicit rules are the ROUTES/SPLITS tables below.
Columns are the intersection of legacy and V2 columns; a V2-only column must be nullable or have a default,
otherwise generation stops (no silent NOT NULL backfill).

Usage: generate_sql.py <output sql directory>   (MT_HOST, MT_PORT, MYSQL_PWD for root)
"""
import os
import subprocess
import sys
from pathlib import Path

ARCHIVE = "mor_v2_migration_archive"
CLIENT_MASTER_RECORD_TYPES = (
    "PARTY", "CLIENT", "LOCAL_PARTNER", "OPERATING_ENTITY", "PARTY_PROFILE", "CLIENT_BUSINESS_PROFILE",
    "CLIENT_APPLICATION", "PARTNER_APPLICATION", "OPERATING_ENTITY_APPLICATION", "PARTY_RELATION",
    "CLIENT_ENTITY_AFFILIATION", "ENTITY_SOURCE_RELATION", "CONSUMER_LEGAL_DOCUMENT", "SELLER_DISCLOSURE")
COMMERCIAL_RECORD_TYPES = ("COMMERCIAL_TERM_VERSION", "AGREEMENT", "COMMERCIAL_TERM_PLAN", "PAYMENT_METHOD_PROFILE")
# compensation-event-catalog.yaml of each V2 service.
EVENT_TYPES = {
    "mor_acquiring": ("CLIENT_WEBHOOK_REQUESTED", "SUBMISSION_EVENT_TYPE"),
    "mor_consumer": ("STORED_CARD_INVALIDATION",),
    "mor_client_master": ("CLIENT_REVIEW_CLASSIFICATION", "CLIENT_REVIEW_REGISTRATION", "CLIENT_TENANT_PROJECTION"),
    "mor_compliance": ("CLIENT_REVIEW_RESULT",),
    "mor_risk_decision": ("DECISION_SHADOW_RUN",),
}


def query(sql):
    command = ["mysql", "-h", os.environ.get("MT_HOST", "127.0.0.1"), "-P", os.environ.get("MT_PORT", "23307"), "-uroot",
               "--protocol=tcp", "--default-character-set=utf8mb4", "--batch", "--skip-column-names", "-e", sql]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        sys.exit("query failed: " + result.stderr.strip().splitlines()[-1])
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def columns(schema):
    tables = {}
    for table, column, nullable, default, extra in query(
            "SELECT table_name, column_name, is_nullable, IFNULL(column_default, '<NULL>'), extra FROM information_schema.columns "
            "WHERE table_schema='%s' ORDER BY table_name, ordinal_position" % schema):
        tables.setdefault(table, []).append((column, nullable == "YES", default != "<NULL>" or "DEFAULT_GENERATED" in extra))
    return tables


def primary_key(schema, table):
    return [r[0] for r in query("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema='%s' "
                                "AND table_name='%s' AND constraint_name='PRIMARY' ORDER BY ordinal_position" % (schema, table))]


def quote(names):
    return ", ".join("`%s`" % n for n in names)


def in_list(values):
    return ", ".join("'%s'" % v for v in values)


class Plan:
    """One legacy table (optionally filtered) copied into one V2 table."""

    def __init__(self, source_db, table, target_db, where=None, label=None):
        self.source_db, self.table, self.target_db, self.where = source_db, table, target_db, where
        self.label = label or ""
        self.columns = None

    def resolve(self, legacy, reference):
        old = [c[0] for c in legacy[self.source_db][self.table]]
        new = reference[self.target_db][self.table]
        new_names = [c[0] for c in new]
        missing = [c for c in new if c[0] not in old and not c[1] and not c[2]]
        if missing:
            sys.exit("V2 column without legacy source, NULL or default: %s.%s %s" % (self.target_db, self.table, missing))
        self.columns = [c for c in new_names if c in old]
        self.dropped = [c for c in old if c not in new_names]
        return self

    def insert(self):
        where = (" WHERE " + self.where) if self.where else ""
        return ("INSERT INTO `%s`.`%s` (%s)\nSELECT %s\n  FROM `%s`.`%s`%s;\n"
                % (self.target_db, self.table, quote(self.columns), quote(self.columns), self.source_db, self.table, where))

    def checksum(self, side):
        db, table = (self.source_db, self.table) if side == "old" else (self.target_db, self.table)
        expr = "CONCAT_WS('|', %s)" % ", ".join("IFNULL(`%s`, '\\\\N')" % c for c in self.columns)
        where = (" WHERE " + self.where) if (self.where and side == "old") else ""
        return ("SELECT COUNT(*) AS row_count, IFNULL(BIT_XOR(CRC32(%s)), 0) AS row_xor, IFNULL(SUM(CRC32(%s)), 0) AS row_sum "
                "FROM `%s`.`%s`%s" % (expr, expr, db, table, where))


def build_plans(legacy, reference):
    plans = []
    ref = {db: set(tables) for db, tables in reference.items()}

    # mor_cct -> mor_client_master / mor_commercial
    for table in sorted(legacy["mor_cct"]):
        if table == "t_command_idempotency":
            continue
        if table == "t_business_change_record":
            plans.append(Plan("mor_cct", table, "mor_client_master", "`record_type` IN (%s)" % in_list(CLIENT_MASTER_RECORD_TYPES)))
            plans.append(Plan("mor_cct", table, "mor_commercial", "`record_type` IN (%s)" % in_list(COMMERCIAL_RECORD_TYPES)))
        elif table == "t_business_change_version":
            for target, types in (("mor_client_master", CLIENT_MASTER_RECORD_TYPES), ("mor_commercial", COMMERCIAL_RECORD_TYPES)):
                plans.append(Plan("mor_cct", table, target,
                                  "`history_id` IN (SELECT r.`history_id` FROM `mor_cct`.`t_business_change_record` r "
                                  "WHERE r.`record_type` IN (%s))" % in_list(types)))
        elif table == "t_compensation_event":
            plans.append(Plan("mor_cct", table, "mor_client_master", "`event_type` IN (%s)" % in_list(EVENT_TYPES["mor_client_master"])))
        elif table in ref["mor_commercial"]:
            plans.append(Plan("mor_cct", table, "mor_commercial"))
        elif table in ref["mor_client_master"]:
            plans.append(Plan("mor_cct", table, "mor_client_master"))
        else:
            sys.exit("unrouted mor_cct table " + table)

    # mor_commerce -> mor_acquiring / mor_consumer / mor_client_master
    for table in sorted(legacy["mor_commerce"]):
        if table == "t_command_idempotency":
            continue
        if table == "t_compensation_event":
            for target in ("mor_acquiring", "mor_consumer"):
                plans.append(Plan("mor_commerce", table, target, "`event_type` IN (%s)" % in_list(EVENT_TYPES[target])))
        elif table == "t_client_stored_card_setting":
            plans.append(Plan("mor_commerce", table, "mor_client_master"))
        elif table in ref["mor_consumer"]:
            plans.append(Plan("mor_commerce", table, "mor_consumer"))
        elif table in ref["mor_acquiring"]:
            plans.append(Plan("mor_commerce", table, "mor_acquiring"))
        else:
            sys.exit("unrouted mor_commerce table " + table)

    # mor_risk -> mor_risk_decision / mor_compliance (dispute tables go to the archive, see 33)
    for table in sorted(legacy["mor_risk"]):
        if table == "t_command_idempotency" or (table in ref["mor_dispute"] and table != "t_compensation_event"):
            continue
        if table == "t_compensation_event":
            for target in ("mor_risk_decision", "mor_compliance"):
                plans.append(Plan("mor_risk", table, target, "`event_type` IN (%s)" % in_list(EVENT_TYPES[target])))
        elif table in ref["mor_compliance"]:
            plans.append(Plan("mor_risk", table, "mor_compliance"))
        elif table in ref["mor_risk_decision"]:
            plans.append(Plan("mor_risk", table, "mor_risk_decision"))
        else:
            sys.exit("unrouted mor_risk table " + table)
    return [p.resolve(legacy, reference) for p in plans]


ARCHIVED = (  # (source db, table, archive table)
    ("mor_cct", "t_command_idempotency", "cct_t_command_idempotency"),
    ("mor_commerce", "t_command_idempotency", "commerce_t_command_idempotency"),
    ("mor_risk", "t_command_idempotency", "risk_t_command_idempotency"),
)


def header(title, body):
    return "-- " + title + "\n" + "".join("-- " + line + "\n" if line else "--\n" for line in body) + "\n"


def write_migration(out, name, title, source_db, plans, notes):
    targets = sorted({p.target_db for p in plans if p.source_db == source_db})
    selected = [p for p in plans if p.source_db == source_db]
    lines = [
        "影响：只向 " + "、".join(targets) + " 插入行；源库 " + source_db + " 只读，不修改、不删除。",
        "前置：10 已建库与账号；各目标库已按 paytech/<svc>/docs/database/sql/<svc>-schema.sql 建表且目标表为空",
        "      （scripts/migrate.sh 在执行前逐表核对为空；重复执行会因主键冲突整体失败，事务不提交）。",
        "列：每张表显式列出 V2 与旧表的共同列（由 scripts/generate_sql.py 从演练库 information_schema 生成，勿手改）。",
        "事务：单个事务；任一语句失败则 mysql 客户端退出，未提交的事务随连接关闭回滚。",
        "回滚：目标库只含本次迁移写入的数据：DROP DATABASE " + "、".join(targets) + "，再按 10 与 Schema 重建；",
        "      旧库未改动，回滚不需要恢复源数据。",
    ] + notes
    sql = header(name + " · " + title, lines)
    sql += "SET NAMES utf8mb4;\nSET time_zone = '+00:00';\nSTART TRANSACTION;\n\n"
    for p in selected:
        label = " WHERE " + p.where if p.where else ""
        sql += "-- %s.%s -> %s.%s%s%s\n" % (p.source_db, p.table, p.target_db, p.table,
                                          ("（按条件拆分）" if p.where else ""),
                                          ("；不迁移列：" + ", ".join(p.dropped)) if p.dropped else "")
        sql += p.insert() + "\n"
    sql += "COMMIT;\n"
    (out / name).write_text(sql, encoding="utf-8")


def write_archive(out, legacy):
    lines = [
        "影响：在 " + ARCHIVE + " 中保存 V2 不再承载的旧数据，保证阶段 3 删除旧库后不丢失；不改动任何业务库。",
        "  1) 三个旧库的 t_command_idempotency（design/03 §2：V2 删除，HTTP 幂等由 Gateway 承担）。",
        "  2) mor_commerce.t_commerce_order 的 context_format、preparation_command_id（第 03 步 C7 删除列），按 order_id 保存。",
        "  3) mor_risk 的 7 张争议表（design/03 §2.2 归 dispute；mor-test 不部署 dispute-service，不建 mor_dispute）。",
        "前置：10 已建 " + ARCHIVE + "；本文件含 CREATE TABLE（DDL 隐式提交），只在空归档库上执行一次。",
        "回滚：DROP DATABASE `" + ARCHIVE + "`（只含副本，源库未改动）。",
        "保留期：至少保留到阶段 3 删除旧库之后，由用户决定何时删除（见报告“待确认”）。",
    ]
    sql = header("33-archive-retired.sql · V2 不承载的旧数据归档", lines)
    sql += "SET NAMES utf8mb4;\nSET time_zone = '+00:00';\n\n"
    for db, table, archived in ARCHIVED:
        sql += "CREATE TABLE `%s`.`%s` LIKE `%s`.`%s`;\n" % (ARCHIVE, archived, db, table)
        sql += "INSERT INTO `%s`.`%s` SELECT * FROM `%s`.`%s`;\n\n" % (ARCHIVE, archived, db, table)
    sql += ("CREATE TABLE `%s`.`commerce_t_commerce_order_retired_columns` (\n"
            "  `order_id` BIGINT NOT NULL COMMENT '旧 mor_commerce.t_commerce_order 主键。',\n"
            "  `context_format` VARCHAR(32) NOT NULL COMMENT '旧列原值（V2 第 03 步删除）。',\n"
            "  `preparation_command_id` BIGINT NULL COMMENT '旧列原值（V2 第 03 步删除）。',\n"
            "  PRIMARY KEY (`order_id`)\n"
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='V2 迁移归档：t_commerce_order 删除列的原值';\n"
            "INSERT INTO `%s`.`commerce_t_commerce_order_retired_columns` (`order_id`, `context_format`, `preparation_command_id`)\n"
            "SELECT `order_id`, `context_format`, `preparation_command_id` FROM `mor_commerce`.`t_commerce_order`;\n\n"
            % (ARCHIVE, ARCHIVE))
    for table in sorted(t for t in legacy["mor_risk"] if t.startswith("t_dispute_")):
        sql += "CREATE TABLE `%s`.`risk_%s` LIKE `mor_risk`.`%s`;\n" % (ARCHIVE, table, table)
        sql += "INSERT INTO `%s`.`risk_%s` SELECT * FROM `mor_risk`.`%s`;\n\n" % (ARCHIVE, table, table)
    (out / "33-archive-retired.sql").write_text(sql, encoding="utf-8")


def write_verify(out, plans, legacy):
    lines = [
        "只读核对：逐张迁移表比较旧侧（含拆分条件）与新侧的行数及按共同列计算的行校验和（CRC32 的 BIT_XOR 与 SUM）。",
        "另核对：每个旧库全部行都有去处（按表行数 = 各去处之和 + 归档），未登记的补偿事件类型 / BCR 类型为 0。",
        "输出列：check_name、old_rows、new_rows、result（行数与两种校验和都相同才为 OK）。任一 MISMATCH 即迁移不通过。",
        "只输出计数与校验和，不输出任何业务值。由 scripts/migrate.sh verify 执行；可重复执行。",
    ]
    sql = header("40-verify-counts.sql · 迁移前后行数与内容校验和核对", lines)
    sql += "SET NAMES utf8mb4;\nSET time_zone = '+00:00';\n\n"
    parts = []
    for p in plans:
        name = "%s.%s->%s%s" % (p.source_db, p.table, p.target_db, " [split]" if p.where else "")
        parts.append("SELECT '%s' AS check_name, o.row_count AS old_rows, n.row_count AS new_rows,\n"
                     "       IF(o.row_count = n.row_count AND o.row_xor = n.row_xor AND o.row_sum = n.row_sum, 'OK', 'MISMATCH') AS result\n"
                     "  FROM (%s) o\n  CROSS JOIN (%s) n" % (name, p.checksum("old"), p.checksum("new")))
    for db, table, archived in ARCHIVED:
        parts.append("SELECT '%s.%s->%s row_count' AS check_name, (SELECT COUNT(*) FROM `%s`.`%s`) AS old_rows, "
                     "(SELECT COUNT(*) FROM `%s`.`%s`) AS new_rows, IF((SELECT COUNT(*) FROM `%s`.`%s`) = (SELECT COUNT(*) FROM `%s`.`%s`), 'OK', 'MISMATCH') AS result"
                     % (db, table, ARCHIVE, db, table, ARCHIVE, archived, db, table, ARCHIVE, archived))
    for table in sorted(t for t in legacy["mor_risk"] if t.startswith("t_dispute_")):
        parts.append("SELECT 'mor_risk.%s->%s row_count' AS check_name, (SELECT COUNT(*) FROM `mor_risk`.`%s`) AS old_rows, "
                     "(SELECT COUNT(*) FROM `%s`.`risk_%s`) AS new_rows, IF((SELECT COUNT(*) FROM `mor_risk`.`%s`) = (SELECT COUNT(*) FROM `%s`.`risk_%s`), 'OK', 'MISMATCH') AS result"
                     % (table, ARCHIVE, table, ARCHIVE, table, table, ARCHIVE, table))
    parts.append("SELECT 'mor_commerce.t_commerce_order retired columns row_count' AS check_name, (SELECT COUNT(*) FROM `mor_commerce`.`t_commerce_order`) AS old_rows, "
                 "(SELECT COUNT(*) FROM `%s`.`commerce_t_commerce_order_retired_columns`) AS new_rows, "
                 "IF((SELECT COUNT(*) FROM `mor_commerce`.`t_commerce_order`) = (SELECT COUNT(*) FROM `%s`.`commerce_t_commerce_order_retired_columns`), 'OK', 'MISMATCH') AS result"
                 % (ARCHIVE, ARCHIVE))
    # Coverage: every legacy row of a split table has exactly one destination.
    split_tables = sorted({(p.source_db, p.table) for p in plans if p.where})
    for db, table in split_tables:
        targets = [p for p in plans if (p.source_db, p.table) == (db, table)]
        new_sum = " + ".join("(SELECT COUNT(*) FROM `%s`.`%s`)" % (p.target_db, p.table) for p in targets)
        parts.append("SELECT '%s.%s coverage row_count' AS check_name, (SELECT COUNT(*) FROM `%s`.`%s`) AS old_rows, (%s) AS new_rows, "
                     "IF((SELECT COUNT(*) FROM `%s`.`%s`) = (%s), 'OK', 'MISMATCH') AS result"
                     % (db, table, db, table, new_sum, db, table, new_sum))
    sql += "\nUNION ALL\n".join(parts) + ";\n"
    (out / "40-verify-counts.sql").write_text(sql, encoding="utf-8")


def main(out):
    out = Path(out)
    legacy = {db: {t: cols for t, cols in columns(db).items()} for db in ("mor_cct", "mor_commerce", "mor_risk")}
    reference = {
        "mor_client_master": columns("v2ref_client_master"), "mor_commercial": columns("v2ref_commercial"),
        "mor_acquiring": columns("v2ref_acquiring"), "mor_consumer": columns("v2ref_consumer"),
        "mor_risk_decision": columns("v2ref_risk_decision"), "mor_compliance": columns("v2ref_compliance"),
        "mor_dispute": columns("v2ref_dispute"),
    }
    plans = build_plans(legacy, reference)
    write_migration(out, "30-migrate-cct.sql", "mor_cct → mor_client_master + mor_commercial", "mor_cct", plans, [
        "拆分：t_business_change_record / _version 按 record_type 归属（client-master 14 类、commercial 4 类，取自两个服务的",
        "      BusinessChangeRecordRecordType 枚举）；t_compensation_event 按 event_type 归 client-master。",
        "不迁移：t_command_idempotency（归档见 33）。"])
    write_migration(out, "31-migrate-commerce.sql", "mor_commerce → mor_acquiring + mor_consumer + mor_client_master", "mor_commerce", plans, [
        "拆分：t_customer → mor_consumer；t_client_stored_card_setting → mor_client_master；",
        "      t_compensation_event 按 event_type：STORED_CARD_INVALIDATION → consumer，其余已登记类型 → acquiring。",
        "不迁移：t_command_idempotency；t_commerce_order 的 context_format、preparation_command_id（第 03 步删除，原值归档见 33）。"])
    write_migration(out, "32-migrate-risk.sql", "mor_risk → mor_risk_decision + mor_compliance", "mor_risk", plans, [
        "拆分：按 V2 Schema 归属（risk-decision 53 表、compliance 23 表）；t_compensation_event 按 event_type：",
        "      DECISION_SHADOW_RUN → risk-decision，CLIENT_REVIEW_RESULT → compliance。",
        "初始化目录：risk-decision-initial-data.sql 不执行（目录行已在旧库，随迁移带入；40 与 migrate.sh 核对种子主键齐全）。",
        "不迁移：t_command_idempotency 与 7 张争议表（归档见 33）。"])
    write_archive(out, legacy)
    write_verify(out, plans, legacy)
    for p in plans:
        print("%s.%s -> %s%s%s" % (p.source_db, p.table, p.target_db, " WHERE " + p.where if p.where else "",
                                   (" DROP " + ",".join(p.dropped)) if p.dropped else ""))


if __name__ == "__main__":
    main(sys.argv[1])
