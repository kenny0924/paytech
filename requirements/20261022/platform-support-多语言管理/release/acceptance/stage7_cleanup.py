"""多语言管理阶段 7：清理 stage7_acceptance.py 写入的验收数据（mor_platform，root SQL；按运行记录，只动本次的合成数据）。

缺省删除：
  1. 合成应用 qa-i18n-<run> 的全部多语言行（语言包、发布、翻译、key、命名空间、应用）；前提是这些发布没有未结束的
     I18N_RELEASE_ACTIVATE 补偿事件（INIT / PROCESSING / ERROR），否则停止；
  2. 私用语言 qaa；
  3. 合成只读人员与角色（同文件公开桶 stage7_cleanup.py 的删除顺序）。
保留：两个控制台的首批发布与验收中 operations-console 的“内容不变再发布”（当前发布不回退）；本次上传的导入文件与合成应用的
     语言包文件（文件记录与对象）；审计。
--purge-files：再删除本次合成应用相关文件的数据库行（文件、版本、上传、检查结果），并列出需在 MinIO 手工删除的对象
     （mor-platform-public 中 i18n/qa-i18n-<run>/ 前缀与 mor-platform-files 中的导入文件对象）。
用法：python3 -I stage7_cleanup.py [run-*.json] [--purge-files] [--dry-run]   （--dry-run 只打印将执行的 SQL 与计数，不写）
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import i18n7_common as c  # noqa: E402


def latest_record(path):
    if path:
        return json.loads(Path(path).read_text())
    runs = sorted(c.STATE.glob("run-*.json"))
    if not runs:
        raise SystemExit("没有运行记录：先运行 stage7_acceptance.py，或传入 run-*.json")
    return json.loads(runs[-1].read_text())


def run_sql(statements, dry, report, label):
    for name, statement in statements:
        if dry:
            print(f"[干跑] {label}/{name}: {statement}")
            continue
        rows = c.sql("mor_platform", f"{statement}; SELECT ROW_COUNT();")
        report[f"{label}/{name}"] = int(rows[-1][0])
        print(f"{label}/{name}: {rows[-1][0]} 行")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("record", nargs="?")
    parser.add_argument("--purge-files", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    record = latest_record(args.record)
    app = record["app"]
    if not app.startswith("qa-i18n-"):
        raise SystemExit(f"拒绝清理非合成应用: {app}")
    report = {}
    rows = c.sql("mor_platform", f"SELECT application_id FROM t_i18n_application WHERE application_code = '{app}'")
    app_id = int(rows[0][0]) if rows else None
    release_ids = [r[0] for r in c.sql("mor_platform", f"SELECT release_id FROM t_i18n_release WHERE application_id = {app_id}")] if app_id else []
    if release_ids:
        pending = c.sql("mor_platform", "SELECT COUNT(*) FROM t_compensation_event WHERE event_type = 'I18N_RELEASE_ACTIVATE' "
                                        f"AND execute_status IN ('INIT','PROCESSING','ERROR') AND event_key IN ({','.join(release_ids)})")
        if pending[0][0] != "0":
            raise SystemExit(f"合成应用还有 {pending[0][0]} 个未结束的上线补偿事件，稍后再清理")
    bundle_files = []
    if release_ids:
        bundle_files = [r[0].rsplit("/", 1)[-1] for r in c.sql("mor_platform", "SELECT file_address FROM t_i18n_release_bundle "
                                                                              f"WHERE release_id IN ({','.join(release_ids)})")]
    if app_id:
        run_sql([
            ("release_bundle", f"DELETE b FROM t_i18n_release_bundle b JOIN t_i18n_release r ON r.release_id = b.release_id WHERE r.application_id = {app_id}"),
            ("release", f"DELETE FROM t_i18n_release WHERE application_id = {app_id}"),
            ("translation", f"DELETE t FROM t_i18n_translation t JOIN t_i18n_key k ON k.key_id = t.key_id WHERE k.application_id = {app_id}"),
            ("key", f"DELETE FROM t_i18n_key WHERE application_id = {app_id}"),
            ("namespace", f"DELETE FROM t_i18n_namespace WHERE application_id = {app_id}"),
            ("application", f"DELETE FROM t_i18n_application WHERE application_id = {app_id} AND application_code LIKE 'qa-i18n-%'"),
        ], args.dry_run, report, "合成应用")
    if record.get("locale") == "qaa":
        run_sql([("locale", "DELETE FROM t_i18n_locale WHERE locale_code = 'qaa'")], args.dry_run, report, "语言")
    accounts = ",".join(str(int(a)) for a in record.get("platformAccounts", []))
    roles = ",".join(str(int(r)) for r in record.get("platformRoles", []))
    if accounts and roles:
        run_sql([
            ("refresh_token_generation", f"DELETE g FROM t_refresh_token_generation g JOIN t_auth_session s ON s.session_id=g.session_id WHERE s.account_id IN ({accounts})"),
            ("auth_session", f"DELETE FROM t_auth_session WHERE account_id IN ({accounts})"),
            ("authentication_challenge", f"DELETE FROM t_authentication_challenge WHERE account_id IN ({accounts})"),
            ("password_credential", f"DELETE FROM t_password_credential WHERE account_id IN ({accounts})"),
            ("tenant_member_role(member)", f"DELETE r FROM t_tenant_member_role r JOIN t_tenant_member m ON m.member_id=r.tenant_member_id WHERE m.account_id IN ({accounts})"),
            ("tenant_member_role(role)", f"DELETE FROM t_tenant_member_role WHERE role_id IN ({roles})"),
            ("tenant_member", f"DELETE FROM t_tenant_member WHERE account_id IN ({accounts})"),
            ("user_account", f"DELETE FROM t_user_account WHERE account_id IN ({accounts})"),
            ("role_permission", f"DELETE FROM t_role_permission WHERE role_id IN ({roles})"),
            ("role", f"DELETE FROM t_role WHERE role_id IN ({roles}) AND role_code LIKE 'QA\\_I18N7\\_%'"),
        ], args.dry_run, report, "合成人员")
    files = sorted({str(int(f)) for f in record.get("files", []) + bundle_files})
    if args.purge_files and files:
        ids = ",".join(files)
        objects = c.sql("mor_platform", f"SELECT storage_container_ref, storage_object_ref FROM t_file_object WHERE file_id IN ({ids})")
        run_sql([
            ("scan_result", f"DELETE FROM t_file_scan_result WHERE file_id IN ({ids})"),
            ("version", f"DELETE FROM t_file_version WHERE file_id IN ({ids})"),
            ("upload", f"DELETE u FROM t_file_upload u JOIN t_file_object f ON f.upload_id=u.upload_id WHERE f.file_id IN ({ids})"),
            ("object", f"DELETE FROM t_file_object WHERE file_id IN ({ids})"),
        ], args.dry_run, report, "文件")
        print("需在 MinIO 手工删除的对象（含全部版本）：")
        for bucket, key in objects:
            print(f"  {bucket}/{key}")
    elif files:
        print(f"保留本次 {len(files)} 个文件（导入文件 {len(record.get('files', []))}、合成应用语言包 {len(bundle_files)}）；需要时加 --purge-files")
    if not args.dry_run:
        left = c.sql("mor_platform", f"SELECT (SELECT COUNT(*) FROM t_i18n_application WHERE application_code = '{app}'), "
                                     "(SELECT COUNT(*) FROM t_i18n_locale WHERE locale_code = 'qaa'), "
                                     "(SELECT COUNT(*) FROM t_i18n_application WHERE application_code IN ('operations-console','client-console') "
                                     "AND current_release_id IS NOT NULL)")[0]
        print(f"核对：合成应用 {left[0]}（期望 0）、qaa {left[1]}（期望 0）、两个控制台当前发布 {left[2]}（期望 2，不受影响）")
        record["cleanup"] = report
        c.save(f"cleanup-{record['run']}.json", record)
    return 0


if __name__ == "__main__":
    sys.exit(main())
