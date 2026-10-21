"""币种主数据阶段 7 mor-test 验收清理（只删本次运行创建的数据）。

1. mor_platform.t_currency_spec：只在 XTS 行 ID 等于运行记录、且创建时间不早于本次运行开始时删除；USD 必须仍为 2 位 HALF_UP ACTIVE 版本 1。
   审计事件 t_audit_event 留痕不删。ledger / billing 下一次刷新（≤ 60 秒）后快照里不再有 XTS。
2. mor_platform：按记录的合成只读人员与角色 ID 删除刷新令牌、会话、挑战、口令、成员角色、成员、账号、角色授权、角色（同对账切片 1 清理）。
3. ledger / billing 容器 /tmp/ccy7-probe（tmpfs）删除。
块 125 激活状态、镜像不动（属于发布本身）。
用法：python3 -I stage7_cleanup.py <CCY7_STATE/run-*.json> [--dry-run]
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ccy7_common as c  # noqa: E402


def xts_row(record):
    rows = c.sql("mor_platform", f"SELECT id, DATE_FORMAT(create_time, '%Y-%m-%dT%H:%i:%sZ') FROM t_currency_spec WHERE currency='{c.TEST_CURRENCY}'")
    if not rows:
        return None, "XTS 不存在"
    spec_id, created = rows[0]
    if spec_id != str(record.get("currencySpecId")):
        return None, f"XTS 行 ID {spec_id} 不是本次运行创建的 {record.get('currencySpecId')}，中止"
    if created < record["startedAt"]:
        return None, f"XTS 创建时间 {created} 早于本次运行 {record['startedAt']}，中止"
    return spec_id, None


def main():
    record = json.loads(Path(sys.argv[1]).read_text())
    dry_run = "--dry-run" in sys.argv
    report = {"run": record["run"], "dryRun": dry_run}
    spec_id, problem = xts_row(record)
    report["xts"] = spec_id or problem
    accounts = ",".join(str(a) for a in record.get("platformAccounts", []))
    roles = ",".join(str(r) for r in record.get("platformRoles", []))
    report.update(platformAccounts=accounts, platformRoles=roles)
    if dry_run:
        print(json.dumps(report, ensure_ascii=False, indent=1))
        return
    if problem and problem != "XTS 不存在":
        raise SystemExit(problem)
    if spec_id:
        rows = c.sql("mor_platform", f"DELETE FROM t_currency_spec WHERE id={spec_id} AND currency='{c.TEST_CURRENCY}'; SELECT ROW_COUNT();")
        report["xtsDeleted"] = int(rows[0][0])
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
    c.remove_probe()
    report["probeRemoved"] = True
    report["currencySpecsAfter"] = c.sql("mor_platform", "SELECT currency, minor_unit, rounding_mode, status, version FROM t_currency_spec ORDER BY currency")
    report["usdIntact"] = report["currencySpecsAfter"] == [["USD", "2", "HALF_UP", "ACTIVE", "1"]]
    print(json.dumps(report, ensure_ascii=False, indent=1))
    (c.STATE / f"cleanup-{record['run']}.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))
    if not report["usdIntact"]:
        raise SystemExit("清理后 t_currency_spec 不是只有原样的 USD 一行")


if __name__ == "__main__":
    main()
