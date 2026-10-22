"""文件多存储并存阶段 7 mor-test 验收清理（只删本次运行创建的数据）。

1. mor_platform.t_file_storage_bucket：删除运行记录中的 fs7-missing-<run> 桶行，条件为属于实例 MINIO、状态 DRAFT、创建时间不早于本次运行、
   没有路由引用；审计事件 t_audit_event 留痕不删。没有删除桶的接口（B1：只能停用），所以由本脚本按行删除。
2. mor_platform：按记录的合成只读人员与角色 ID 删除刷新令牌、会话、挑战、口令、成员角色、成员、账号、角色授权、角色（同币种发布清理）。
3. 本机 FS7_STATE/probe（探针 class）删除；旁路容器以 --rm 运行，容器内不留文件。
不动：实例 MINIO 的密钥更换（AC-07，正常运维记录）、桶 mor-platform-files、默认路由、块 130 激活与镜像（属于发布本身）。
用法：python3 -I stage7_cleanup.py <FS7_STATE/run-*.json> [--dry-run]
"""
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fs7_common as c  # noqa: E402


def missing_bucket(record):
    name = record.get("missingBucket")
    if not name:
        return None, "运行记录没有合成桶"
    if not name.startswith("fs7-missing-"):
        return None, f"桶名 {name} 不是验收合成桶，中止"
    rows = c.sql("mor_platform", "SELECT b.bucket_id, b.status, DATE_FORMAT(b.create_time, '%Y-%m-%dT%H:%i:%sZ'), "
                 "(SELECT COUNT(*) FROM t_file_storage_route r WHERE r.bucket_id=b.bucket_id), "
                 "(SELECT COUNT(*) FROM t_file_upload u WHERE u.storage_container_ref=b.bucket_name), "
                 "(SELECT COUNT(*) FROM t_file_object f WHERE f.storage_container_ref=b.bucket_name) "
                 "FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id=b.instance_id "
                 f"WHERE i.instance_code='{c.INSTANCE}' AND b.bucket_name='{name}'")
    if not rows:
        return None, f"桶 {name} 不存在"
    bucket_id, status, created, route_refs, uploads, files = rows[0]
    if status != "DRAFT" or created < record["startedAt"] or route_refs != "0" or uploads != "0" or files != "0":
        return None, f"桶 {name} 状态 {status}、创建 {created}、路由 {route_refs}、上传 {uploads}、文件 {files}，不满足删除条件，中止"
    return bucket_id, None


def main():
    record = json.loads(Path(sys.argv[1]).read_text())
    dry_run = "--dry-run" in sys.argv
    report = {"run": record["run"], "dryRun": dry_run}
    bucket_id, problem = missing_bucket(record)
    report["missingBucket"] = bucket_id or problem
    accounts = ",".join(str(a) for a in record.get("platformAccounts", []))
    roles = ",".join(str(r) for r in record.get("platformRoles", []))
    report.update(platformAccounts=accounts, platformRoles=roles)
    if dry_run:
        print(json.dumps(report, ensure_ascii=False, indent=1))
        return
    if problem and not problem.endswith("不存在") and problem != "运行记录没有合成桶":
        raise SystemExit(problem)
    if bucket_id:
        rows = c.sql("mor_platform", f"DELETE FROM t_file_storage_bucket WHERE bucket_id={int(bucket_id)} AND status='DRAFT' "
                     f"AND bucket_name='{record['missingBucket']}'; SELECT ROW_COUNT();")
        report["missingBucketDeleted"] = int(rows[0][0])
    if accounts and roles:
        statements = [
            ("refresh_token_generation", f"DELETE g FROM t_refresh_token_generation g JOIN t_auth_session s ON s.session_id=g.session_id WHERE s.account_id IN ({accounts})"),
            ("auth_session", f"DELETE FROM t_auth_session WHERE account_id IN ({accounts})"),
            ("authentication_challenge", f"DELETE FROM t_authentication_challenge WHERE account_id IN ({accounts})"),
            ("password_credential", f"DELETE FROM t_password_credential WHERE account_id IN ({accounts})"),
            ("tenant_member_role(member)", f"DELETE r FROM t_tenant_member_role r JOIN t_tenant_member m ON m.member_id=r.tenant_member_id WHERE m.account_id IN ({accounts})"),
            ("tenant_member_role(role)", f"DELETE FROM t_tenant_member_role WHERE role_id IN ({roles})"),
            ("tenant_member", f"DELETE FROM t_tenant_member WHERE account_id IN ({accounts})"),
            ("user_account", f"DELETE FROM t_user_account WHERE account_id IN ({accounts})"),
            ("role_permission", f"DELETE FROM t_role_permission WHERE role_id IN ({roles})"),
            ("role", f"DELETE FROM t_role WHERE role_id IN ({roles})"),
        ]
        rows = c.sql("mor_platform", " ".join(f"{s}; SELECT ROW_COUNT();" for _, s in statements))
        report["platformDeleted"] = dict(zip([n for n, _ in statements], [int(r[0]) for r in rows]))
    shutil.rmtree(c.STATE / "probe", ignore_errors=True)
    report["probeRemoved"] = True
    report["storageAfter"] = c.sql("mor_platform", "SELECT i.instance_code, i.status, b.bucket_name, b.status, "
                                   "(SELECT GROUP_CONCAT(r.source_system) FROM t_file_storage_route r WHERE r.bucket_id=b.bucket_id) "
                                   "FROM t_file_storage_instance i JOIN t_file_storage_bucket b ON b.instance_id=i.instance_id ORDER BY 1, 3")
    report["storageIntact"] = report["storageAfter"] == [[c.INSTANCE, "ACTIVE", c.BUCKET, "ACTIVE", c.DEFAULT_ROUTE]]
    print(json.dumps(report, ensure_ascii=False, indent=1))
    c.save(f"cleanup-{record['run']}.json", report)
    if not report["storageIntact"]:
        raise SystemExit("清理后存储登记不是只有 MINIO / mor-platform-files / 默认路由 *")


if __name__ == "__main__":
    main()
