"""从 Risk 正式 Schema 生成本机入驻 4b 迁移 SQL；只输出文件，不连接数据库。

用法：python3 -I generate-risk-onboarding-alter.py [--check]
差异来源：2026-10-13 对本机 mor_risk 的只读核对（10 表存在、相关表为空）。
"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[5]
SCHEMA = ROOT / "risk-compliance-service/docs/database/sql/risk-compliance-schema.sql"
TARGET = pathlib.Path(__file__).resolve().parent / "risk-onboarding-alter.sql"
sql = SCHEMA.read_text(encoding="utf-8")

def table(name):
    m = re.search(r"CREATE TABLE `%s` \(.*?\n\) ENGINE[^;]*;" % name, sql, re.S)
    if not m:
        sys.exit("目标 Schema 缺少表 " + name)
    return m.group(0)

def column(name, col):
    for line in table(name).splitlines():
        if line.strip().startswith("`%s` " % col):
            return line.strip().rstrip(",")
    sys.exit("目标 Schema 缺少列 %s.%s" % (name, col))

def previous(name, col):
    cols = [re.match(r"\s*`(\w+)`", line).group(1) for line in table(name).splitlines()
            if re.match(r"\s*`\w+` ", line)]
    return cols[cols.index(col) - 1]

def add(name, col):
    return f"  ADD COLUMN {column(name, col)} AFTER `{previous(name, col)}`"

def key(name, k):
    for line in table(name).splitlines():
        if ("KEY `%s` " % k) in line:
            return line.strip().rstrip(",")
    sys.exit("目标 Schema 缺少索引 %s.%s" % (name, k))

def render():
    case, subject, decision = "t_due_diligence_case", "t_dd_case_subject", "t_qualification_decision"
    out = [
        "-- Client 入驻 4b：本机 mor_risk 结构迁移。由 generate-risk-onboarding-alter.py 从正式 Schema 生成，勿手改。",
        "-- 前置：相关表为空（2026-10-13 只读核对）；执行前须重新核对并取得精确授权。DDL 自动提交，不使用 --force。",
        "-- 回滚：DROP TABLE t_dd_case_classification；删除新增列并恢复 uk_dd_case_source 原定义（见执行清单）。",
        f"ALTER TABLE `{case}`",
        add(case, "source_submission_id") + ",",
        f"  MODIFY COLUMN {column(case, 'source_submission_version')},",
        f"  MODIFY COLUMN {column(case, 'source_submission_hash')},",
        f"  MODIFY COLUMN {column(case, 'status')},",
        add(case, "public_note") + ",",
        add(case, "resolution_note") + ",",
        add(case, "return_section") + ",",
        "  DROP INDEX `uk_dd_case_source`,",
        f"  ADD {key(case, 'uk_dd_case_source')};",
        f"ALTER TABLE `{subject}`",
        add(subject, "source_relation_id") + ",",
        f"  MODIFY COLUMN {column(subject, 'source_profile_version_no')};",
        f"ALTER TABLE `{decision}`",
        f"  MODIFY COLUMN {column(decision, 'decision_version')};",
        table("t_dd_case_classification"),
    ]
    return "\n".join(out) + "\n"

if __name__ == "__main__":
    text = render()
    if "--check" in sys.argv:
        sys.exit(0 if TARGET.read_text(encoding="utf-8") == text else "生成结果与已提交 SQL 不一致")
    TARGET.write_text(text, encoding="utf-8")
