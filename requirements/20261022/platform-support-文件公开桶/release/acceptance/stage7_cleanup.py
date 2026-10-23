"""文件公开桶阶段 7 mor-test 验收清理（只删本次运行创建的数据，不动任何既有配置）。

缺省会删：
1. 本次新建的写入规则：经运营接口 DELETE /routes/{来源}/{级别}（pubbucket-qa/PUBLIC、残留的 pubbucket-qa-policy/PRIVATE）；
2. 本次检查策略 QA_PB7_<run>：先经接口停用（如仍 ACTIVE），再按行删除 t_file_scan_requirement / t_file_scan_policy
   （条件：代码以 QA_PB7_ 开头、没有上传 / 文件 / 版本 / 扫描结果引用其修订、没有写入规则引用）；
3. 合成人员与角色：按记录的 ID 删除刷新令牌、会话、挑战、口令、成员角色、成员、账号、角色授权、角色（同前置需求清理）；
4. 本机 PB7_STATE/probe（探针 class）。
缺省保留（与前置需求一致，见 release.md 第 9 节）：
  · 公开测试桶登记与其 ACTIVE 状态（是否保留由主会话决定，见第 11 节）；
  · 本次上传的文件行与存储对象（文件服务不提供删除；公开对象可被匿名读取，内容为 1×1 合成 PNG）；
  · 审计事件、存量桶的最近检查时间与结果。
可选：
  --purge-files：按运行记录中的文件 ID（只限本次合成来源与回归上传）删除 t_file_scan_result / t_file_version / t_file_object /
                 t_file_upload 行，并列出需在存储侧手工删除的对象（实例、桶、对象名）；不删除存储对象本身。
  --remove-public-bucket：在 --purge-files 之后，公开桶没有任何写入规则与文件行时，按行删除该桶登记（需主会话决定后才用）。
用法：python3 -I stage7_cleanup.py <PB7_STATE/run-*.json> [--dry-run] [--purge-files] [--remove-public-bucket]
"""
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pb7_common as c  # noqa: E402

REASON = "文件公开桶阶段 7 mor-test 验收清理"


def delete_routes(admin, record, report):
    status, items, _ = c.routes(admin)
    done = {}
    for source, level in record.get("routes", []):
        if not source.startswith("pubbucket-qa"):
            done[f"{source}/{level}"] = "不是验收合成来源，跳过"
            continue
        route = items.get((source, level))
        if not route:
            done[f"{source}/{level}"] = "已不存在"
            continue
        status, body, _ = c.raw(admin, "DELETE", f"{c.ADMIN}/routes/{source}/{level}",
                                {"expectedVersion": route.get("version"), "reason": REASON})
        done[f"{source}/{level}"] = status
    report["routes"] = done


def delete_policy(admin, record, report):
    code = record.get("policy")
    if not code:
        report["policy"] = "运行记录没有合成策略"
        return
    if not code.startswith("QA_PB7_"):
        raise SystemExit(f"策略 {code} 不是验收合成策略，中止")
    status, detail, _ = c.raw(admin, "GET", f"{c.ADMIN}/scan-policies/{code}")
    active = next((r for r in detail.get("revisions") or [] if r.get("status") == "ACTIVE"), None)
    if active:
        c.raw(admin, "POST", f"{c.ADMIN}/scan-policies/{code}/retirement", {"expectedVersion": active.get("version"), "reason": REASON})
    refs = c.sql("mor_platform", "SELECT "
                 f"(SELECT COUNT(*) FROM t_file_upload u JOIN t_file_scan_policy p ON p.policy_id=u.scan_policy_id WHERE p.policy_code='{code}'),"
                 f"(SELECT COUNT(*) FROM t_file_object f JOIN t_file_scan_policy p ON p.policy_id=f.scan_policy_id WHERE p.policy_code='{code}'),"
                 f"(SELECT COUNT(*) FROM t_file_version v JOIN t_file_scan_policy p ON p.policy_id=v.scan_policy_id WHERE p.policy_code='{code}'),"
                 f"(SELECT COUNT(*) FROM t_file_scan_result s JOIN t_file_scan_policy p ON p.policy_id=s.scan_policy_id WHERE p.policy_code='{code}'),"
                 f"(SELECT COUNT(*) FROM t_file_storage_route WHERE scan_policy_code='{code}'),"
                 f"(SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_code='{code}' AND status='ACTIVE')")[0]
    if any(value != "0" for value in refs):
        report["policy"] = f"{code} 仍被引用或 ACTIVE（上传 / 文件 / 版本 / 结果 / 路由 / ACTIVE = {refs}），未删除"
        return
    rows = c.sql("mor_platform", "DELETE q FROM t_file_scan_requirement q JOIN t_file_scan_policy p ON p.policy_id=q.policy_id "
                 f"WHERE p.policy_code='{code}'; SELECT ROW_COUNT(); DELETE FROM t_file_scan_policy WHERE policy_code='{code}'; "
                 "SELECT ROW_COUNT();")
    report["policy"] = {"code": code, "requirementsDeleted": int(rows[0][0]), "revisionsDeleted": int(rows[1][0])}


def delete_people(record, report):
    accounts = ",".join(str(int(a)) for a in record.get("platformAccounts", []))
    roles = ",".join(str(int(r)) for r in record.get("platformRoles", []))
    if not (accounts and roles):
        report["platformDeleted"] = "运行记录没有合成人员"
        return
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
        ("role", f"DELETE FROM t_role WHERE role_id IN ({roles}) AND role_code LIKE 'QA\\_PB7\\_%'"),
    ]
    rows = c.sql("mor_platform", " ".join(f"{s}; SELECT ROW_COUNT();" for _, s in statements))
    report["platformDeleted"] = dict(zip([n for n, _ in statements], [int(r[0]) for r in rows]))


def purge_files(record, report):
    ids = [str(int(v)) for v in (record.get("files") or {}).values()]
    if not ids:
        report["filesPurged"] = "运行记录没有文件"
        return
    joined = ",".join(ids)
    since = record["startedAt"].replace("T", " ").rstrip("Z")
    owned = c.sql("mor_platform", f"SELECT file_id FROM t_file_object WHERE file_id IN ({joined}) AND create_time >= '{since}' "
                  f"AND (source_system LIKE 'pubbucket-qa%' OR reason_code IN ('CLIENT_ONBOARDING_MATERIAL','WORKFLOW_ATTACHMENT','CARD_BIN_LIBRARY_IMPORT'))")
    owned_ids = ",".join(r[0] for r in owned)
    if not owned_ids:
        report["filesPurged"] = "没有满足条件（本次创建）的文件行"
        return
    report["objectsToDeleteManually"] = c.sql("mor_platform", "SELECT f.storage_provider, f.storage_container_ref, f.storage_object_ref, "
                                              f"COUNT(v.version_id) FROM t_file_object f LEFT JOIN t_file_version v ON v.file_id=f.file_id "
                                              f"WHERE f.file_id IN ({owned_ids}) GROUP BY f.file_id")
    statements = [
        ("scan_result", f"DELETE FROM t_file_scan_result WHERE file_id IN ({owned_ids})"),
        ("version", f"DELETE FROM t_file_version WHERE file_id IN ({owned_ids})"),
        ("upload(replacement)", f"DELETE FROM t_file_upload WHERE replaces_file_id IN ({owned_ids})"),
        ("upload(original)", f"DELETE u FROM t_file_upload u JOIN t_file_object f ON f.upload_id=u.upload_id WHERE f.file_id IN ({owned_ids})"),
        ("object", f"DELETE FROM t_file_object WHERE file_id IN ({owned_ids})"),
    ]
    # 上传行要在文件行之前按 upload_id 关联删除，因此先取 upload_id 再删文件。
    rows = c.sql("mor_platform", " ".join(f"{s}; SELECT ROW_COUNT();" for _, s in statements))
    report["filesPurged"] = dict(zip([n for n, _ in statements], [int(r[0]) for r in rows]))


def remove_public_bucket(admin, record, report):
    instance_code, bucket = record.get("publicInstance"), record.get("publicBucketName")
    refs = c.sql("mor_platform", "SELECT b.bucket_id, "
                 "(SELECT COUNT(*) FROM t_file_storage_route r WHERE r.bucket_id=b.bucket_id), "
                 f"(SELECT COUNT(*) FROM t_file_object f WHERE f.storage_container_ref='{bucket}'), "
                 f"(SELECT COUNT(*) FROM t_file_upload u WHERE u.storage_container_ref='{bucket}') "
                 "FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id=b.instance_id "
                 f"WHERE i.instance_code='{instance_code}' AND b.bucket_name='{bucket}' AND b.access_level='PUBLIC'")
    if not refs:
        report["publicBucket"] = "不存在"
        return
    bucket_id, route_refs, files, uploads = refs[0]
    if (route_refs, files, uploads) != ("0", "0", "0"):
        report["publicBucket"] = f"仍有路由 {route_refs}、文件 {files}、上传 {uploads}，未删除（先 --purge-files）"
        return
    rows = c.sql("mor_platform", f"DELETE FROM t_file_storage_bucket WHERE bucket_id={int(bucket_id)} AND access_level='PUBLIC'; "
                 "SELECT ROW_COUNT();")
    report["publicBucket"] = {"deleted": int(rows[0][0])}


def main():
    record = json.loads(Path(sys.argv[1]).read_text())
    flags = set(sys.argv[2:])
    report = {"run": record["run"], "flags": sorted(flags), "routes": record.get("routes"), "policy": record.get("policy"),
              "files": record.get("files"), "platformAccounts": record.get("platformAccounts"), "platformRoles": record.get("platformRoles")}
    if "--dry-run" in flags:
        print(json.dumps(report, ensure_ascii=False, indent=1))
        return
    admin = c.admin_token()
    delete_routes(admin, record, report)
    delete_policy(admin, record, report)
    delete_people(record, report)
    if "--purge-files" in flags:
        purge_files(record, report)
    if "--remove-public-bucket" in flags:
        remove_public_bucket(admin, record, report)
    shutil.rmtree(c.STATE / "probe", ignore_errors=True)
    report["routesAfter"] = c.sql("mor_platform", "SELECT r.source_system, r.access_level, b.bucket_name, r.scan_policy_code "
                                  "FROM t_file_storage_route r JOIN t_file_storage_bucket b ON b.bucket_id=r.bucket_id ORDER BY 1, 2")
    expected = sorted([[s, l, b, p] for (s, l), (_, b, p) in c.EXISTING_ROUTES.items()])
    report["routesIntact"] = sorted(report["routesAfter"]) == expected
    print(json.dumps(report, ensure_ascii=False, indent=1, default=str))
    c.save(f"cleanup-{record['run']}.json", report)
    if not report["routesIntact"]:
        raise SystemExit("清理后写入规则不是存量三条（*、client-console-bff、operations-console-bff，均 PRIVATE）")


if __name__ == "__main__":
    main()
