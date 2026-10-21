"""对账控制台 mor-test 演示数据清理（仿 release/acceptance/stage7_cleanup.py；只删 demo_seed.py 记录里的对象）。

1. mor_reconciliation：先核对全部行都属于演示（清算账户编码、银行账户 ID、平台流水事实 ID 都在记录中），有外来行即中止并列出；
   通过后 15 张表逐表 DELETE 并核对为 0（演示前这 15 张表为空，补偿 / 消费记录也只来自演示）。
2. mor_channel_connector：按记录的渠道 ID 删除能力明细 / 能力组 / MID / 技术账号 / 渠道及其变更记录。
3. 接入方：只在演示脚本新建过（record.done.integration.created）时经 /external-api-admin/v1/integrations/{code} 按版本删除。
4. mor_platform：按记录的演示复核人与角色 ID 删除刷新令牌、会话、挑战、口令、成员角色、成员、账号、角色授权、角色；审计事件留痕不删。
5. Vault（结算 / 出款银行账号、渠道账号配置的 Protect 记录）按先例只计数不删。XXL 任务、执行日志不动。
用法：python3 -I demo_cleanup.py <~/Code-MoR-backups/recon-demo/demo-*.json> [--dry-run]
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "acceptance"))
import recon7_common as c  # noqa: E402

RECON_TABLES = ["t_bank_match", "t_bank_statement_line", "t_bank_statement", "t_expected_bank_movement", "t_recon_resolution",
                "t_recon_exception", "t_channel_statement_line", "t_channel_statement", "t_platform_txn", "t_clearing_account_mid",
                "t_clearing_account", "t_cash_account", "t_compensation_event", "t_consumed_event", "t_consumed_aggregate_version"]


def counts():
    return {t: int(c.sql("mor_reconciliation", f"SELECT COUNT(*) FROM {t}")[0][0]) for t in RECON_TABLES}


def foreign_rows(record):
    codes = set(record.get("clearingAccountCodes", []))
    cash = {str(x) for x in record.get("cashAccounts", [])}
    facts = {str(x) for x in record.get("facts", {}).values()}
    problems = []
    for code, in c.sql("mor_reconciliation", "SELECT clearing_account_code FROM t_clearing_account"):
        if code not in codes:
            problems.append(f"clearing_account {code}")
    for cid, in c.sql("mor_reconciliation", "SELECT id FROM t_cash_account"):
        if cid not in cash:
            problems.append(f"cash_account {cid}")
    for fid, in c.sql("mor_reconciliation", "SELECT billing_fact_id FROM t_platform_txn"):
        if fid not in facts:
            problems.append(f"platform_txn fact {fid}")
    return problems


def delete_channel(channel):
    sub_mid = f"SELECT mid_id FROM t_channel_mid WHERE channel_id={channel}"
    sub_cap = f"SELECT c.mid_capability_id FROM t_mid_capability c JOIN t_channel_mid m ON m.mid_id=c.mid_id WHERE m.channel_id={channel}"
    rows = c.sql("mor_channel_connector",
                 f"DELETE FROM t_channel_config_change WHERE (target_type='CHANNEL' AND target_id={channel})"
                 f" OR (target_type='CHANNEL_ACCOUNT' AND target_id IN (SELECT channel_account_id FROM t_channel_account WHERE channel_id={channel}))"
                 f" OR (target_type='CHANNEL_MID' AND target_id IN ({sub_mid})) OR (target_type='MID_CAPABILITY' AND target_id IN ({sub_cap}));"
                 " SELECT ROW_COUNT();"
                 f" DELETE d FROM t_mid_capability_detail d JOIN t_mid_capability c ON c.mid_capability_id=d.mid_capability_id"
                 f" JOIN t_channel_mid m ON m.mid_id=c.mid_id WHERE m.channel_id={channel}; SELECT ROW_COUNT();"
                 f" DELETE c FROM t_mid_capability c JOIN t_channel_mid m ON m.mid_id=c.mid_id WHERE m.channel_id={channel}; SELECT ROW_COUNT();"
                 f" DELETE FROM t_channel_mid WHERE channel_id={channel}; SELECT ROW_COUNT();"
                 f" DELETE FROM t_channel_account WHERE channel_id={channel}; SELECT ROW_COUNT();"
                 f" DELETE FROM t_payment_channel WHERE channel_id={channel}; SELECT ROW_COUNT();")
    return dict(zip(["config_change", "capability_detail", "capability", "mid", "account", "channel"], [int(r[0]) for r in rows]))


def delete_people(accounts, roles):
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
    return dict(zip([n for n, _ in statements], [int(r[0]) for r in rows]))


def delete_integration(code):
    admin = c.admin_token()
    status, body = c.raw(admin, "GET", f"/external-api-admin/v1/integrations/{code}")
    if status == 404:
        return "不存在"
    if status != 200:
        return f"读取失败 HTTP {status}"
    if body.get("apiCount") or body.get("scriptCount") or body.get("implementationCount"):
        return "已被他人加了 API / 脚本 / 实现，保留不删"
    status, body = c.raw(admin, "DELETE", f"/external-api-admin/v1/integrations/{code}",
                         {"expectedVersion": body["version"], "reason": "对账控制台演示数据清理：删除演示接入方"})
    return "已删除" if status in (200, 204) else f"删除失败 HTTP {status} {body.get('detail')}"


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    path = Path(sys.argv[1])
    record = json.loads(path.read_text())
    dry = "--dry-run" in sys.argv
    report = {"stamp": record["stamp"], "reconciliationBefore": counts()}
    problems = foreign_rows(record)
    if problems:
        print(json.dumps({"abort": "mor_reconciliation 有非演示的行，未删除任何数据", "rows": problems[:50]}, ensure_ascii=False, indent=1))
        sys.exit(1)
    channel = (record.get("channel") or {}).get("channelId") or record.get("channelId")
    accounts = ",".join(str(int(a)) for a in record.get("platformAccounts", []))
    roles = ",".join(str(int(r)) for r in record.get("platformRoles", []))
    integration_created = (record.get("done", {}).get("integration") or {}).get("created")
    if dry:
        print(json.dumps({**report, "channel": channel, "integration": record["channelCode"] if integration_created else None,
                          "platformAccounts": accounts, "platformRoles": roles}, ensure_ascii=False, indent=1))
        return

    c.sql("mor_reconciliation", " ".join(f"DELETE FROM {t};" for t in RECON_TABLES))
    report["reconciliationAfter"] = counts()
    if channel:
        report["channelConnectorDeleted"] = delete_channel(int(channel))
    if integration_created:
        report["integration"] = delete_integration(record["channelCode"])
    if accounts and roles:
        report["platformDeleted"] = delete_people(accounts, roles)
    report["vaultRecordsRetained"] = {k: int(v) for k, v in c.sql(
        "mor_vault", "SELECT data_type, COUNT(*) FROM t_vault_record WHERE data_type IN ('CASH_ACCOUNT_NUMBER_V1','CHANNEL_ACCOUNT_CONFIG_V1') GROUP BY data_type")}
    print(json.dumps(report, ensure_ascii=False, indent=1))
    out = path.parent / f"cleanup-{record['stamp']}.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=1))
    out.chmod(0o600)


if __name__ == "__main__":
    main()
