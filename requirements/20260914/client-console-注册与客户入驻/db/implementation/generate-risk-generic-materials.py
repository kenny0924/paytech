"""生成 Risk 通用材料程序初始化 DML；只输出文件，不连接数据库。

用法：python3 -I generate-risk-generic-materials.py [--check]
"""
import hashlib, json, pathlib, sys

HERE = pathlib.Path(__file__).resolve().parent
TARGET = HERE / "risk-generic-materials-init.sql"
# 固定 ID 取自不与雪花型运行 ID 冲突的保留区间，便于回滚语句精确定位。
PROGRAMS = [
    (9100000000000001, 9100000000000101, "CLIENT_INDIVIDUAL_GENERIC", "INDIVIDUAL_KYC", [
        (9100000000001001, "IDENTITY", "IDENTITY_DOCUMENT", True),
        (9100000000001002, "ADDRESS", "PROOF_OF_ADDRESS", False),
        (9100000000001003, "OTHER", "SUPPORTING_DOCUMENT", False)]),
    (9100000000000002, 9100000000000102, "CLIENT_ORGANIZATION_GENERIC", "ORGANIZATION_KYB", [
        (9100000000002001, "REGISTRATION", "REGISTRATION_CERTIFICATE", True),
        (9100000000002002, "OWNERSHIP", "OWNERSHIP_STRUCTURE", False),
        (9100000000002003, "AUTHORIZATION", "AUTHORIZATION_LETTER", False),
        (9100000000002004, "ADDRESS", "PROOF_OF_ADDRESS", False),
        (9100000000002005, "OTHER", "SUPPORTING_DOCUMENT", False)]),
    (9100000000000003, 9100000000000103, "RELATED_PERSON_GENERIC", "RELATED_PERSON_KYC", [
        (9100000000003001, "IDENTITY", "IDENTITY_DOCUMENT", True),
        (9100000000003002, "OTHER", "SUPPORTING_DOCUMENT", False)]),
]

def content_hash(program_id, requirements):
    """按 CODE-MOR:RISK:DD_PROGRAM_VERSION:1 计算；ID/版本为十进制字符串，键按 RFC 8785 排序。"""
    body = {
        "programId": str(program_id), "versionNo": "1", "reviewFrequencyCode": None,
        "requirements": [{"requirementCode": c, "documentType": t, "required": r}
                         for _, c, t, r in sorted(requirements, key=lambda x: x[1])],
        "checks": [], "screenings": []}
    raw = json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()

def render():
    lines = [
        "-- Risk 通用 Client 入驻材料程序初始化 DML（mor_risk）。由 generate-risk-generic-materials.py 生成，勿手改。",
        "-- 执行前必须取得精确执行授权；执行人先在同一会话 SET @approved_by=<真实 Operations 审批账号 ID>，未设置时 Risk 查询返回不可用。",
        "-- 回滚：按下方 ID 依次 DELETE t_dd_program_requirement / t_dd_program_version / t_dd_program。",
        "SET @now = UTC_TIMESTAMP(6);",
        "START TRANSACTION;",
    ]
    for pid, vid, code, qual, reqs in PROGRAMS:
        lines.append(f"INSERT INTO t_dd_program(program_id,program_code,qualification_type,status,create_time,update_time) "
                     f"VALUES({pid},'{code}','{qual}','ACTIVE',@now,@now);")
        lines.append(f"INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,review_frequency_code,status,active_slot,content_hash,approved_by,approved_time,create_time,update_time) "
                     f"VALUES({vid},{pid},1,NULL,'ACTIVE',1,'{content_hash(pid, reqs)}',@approved_by,@now,@now,@now);")
        for rid, rc, dt, req in reqs:
            lines.append(f"INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) "
                         f"VALUES({rid},{vid},'{rc}','{dt}',{1 if req else 0},@now);")
    lines.append("COMMIT;")
    return "\n".join(lines) + "\n"

if __name__ == "__main__":
    text = render()
    if "--check" in sys.argv:
        sys.exit(0 if TARGET.read_text(encoding="utf-8") == text else "生成结果与已提交 SQL 不一致")
    TARGET.write_text(text, encoding="utf-8")
