"""对账控制台详情页与渠道流水 阶段 5 本机验收清理（沿用切片 1 cleanup.py；本次未建渠道 / MID 时跳过 channel-connector）（只删本次运行创建的数据；先 stack.sh stop）。

1. mor_reconciliation_v2e2e：验收前 15 张表均为 0 行（全部由本次验收写入），逐表 DELETE 后核对为 0；
2. mor_channel_connector_v2e2e：按运行记录的渠道 ID 删除能力明细 / 能力组 / MID / 技术账号 / 渠道及其变更记录（照
   channel-connector 运营控制台阶段 5 acceptance-cleanup-local.sql 的顺序）；
3. mor_platform_v2e2e：按运行记录的合成人员与角色 ID 删除会话、口令、成员、角色授权、角色、账号（同上先例；审计事件留痕不删）；
4. RabbitMQ 临时 vhost /mor-recon5（含观察队列、DLQ）整体删除；Redis database 3 中本栈 Gateway 命名空间 local-recon6:* 删除；
5. Vault 记录（银行账号、渠道账号配置的 Protect 记录）按先例只计数不删。
用法：python3 -I cleanup.py <run-*.json>
"""
import json
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402

RECON_TABLES = ["t_bank_match", "t_bank_statement_line", "t_bank_statement", "t_expected_bank_movement", "t_recon_resolution",
                "t_recon_exception", "t_channel_statement_line", "t_channel_statement", "t_platform_txn", "t_clearing_account_mid",
                "t_clearing_account", "t_cash_account", "t_compensation_event", "t_consumed_event", "t_consumed_aggregate_version"]


def mysql(user, password, database, statement):
    result = subprocess.run(["mysql", "-h127.0.0.1", "-P13306", "--protocol=tcp", f"-u{user}", "-N", "-B", database, "-e", statement],
                            env={**os.environ, "MYSQL_PWD": password}, capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit(f"{database} 失败：{result.stderr[:300]}")
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def test_password(module):
    ddl = c.ROOT / f"paytech/{module}/paytech-{module}-test/src/test/resources/init/ddl.sql"
    return re.search(r"IDENTIFIED BY '([^']*)'", ddl.read_text()).group(1)


def main():
    record = json.loads(Path(sys.argv[1]).read_text())
    report = {}

    counts = {t: int(c.recon_sql(f"SELECT COUNT(*) FROM {t}")[0][0]) for t in RECON_TABLES}
    report["reconciliationBefore"] = counts
    for table in RECON_TABLES:
        c.recon_sql(f"DELETE FROM {table}")
    report["reconciliationAfter"] = {t: int(c.recon_sql(f"SELECT COUNT(*) FROM {t}")[0][0]) for t in RECON_TABLES}

    if "channel" not in record:
        report["channelConnectorDeleted"] = "本次未建渠道 / MID，跳过"
    else:
        cleanup_channel(record, report)
    platform_and_rest(record, report)


def cleanup_channel(record, report):
    channel = record["channel"]["channelId"]
    cc_pass = c.LOCAL.get("MOR_CHANNEL_CONNECTOR_DB_PASSWORD") or test_password("channel-connector")
    cc = lambda s: mysql("channel_connector_app", cc_pass, "mor_channel_connector_v2e2e", s)  # noqa: E731
    sub_mid = f"SELECT mid_id FROM t_channel_mid WHERE channel_id={channel}"
    sub_cap = f"SELECT c.mid_capability_id FROM t_mid_capability c JOIN t_channel_mid m ON m.mid_id=c.mid_id WHERE m.channel_id={channel}"
    rows = cc(
        "DELETE FROM t_channel_config_change WHERE (target_type='CHANNEL' AND target_id=" + channel + ")"
        f" OR (target_type='CHANNEL_ACCOUNT' AND target_id IN (SELECT channel_account_id FROM t_channel_account WHERE channel_id={channel}))"
        f" OR (target_type='CHANNEL_MID' AND target_id IN ({sub_mid})) OR (target_type='MID_CAPABILITY' AND target_id IN ({sub_cap}));"
        " SELECT ROW_COUNT();"
        f" DELETE d FROM t_mid_capability_detail d JOIN t_mid_capability c ON c.mid_capability_id=d.mid_capability_id"
        f" JOIN t_channel_mid m ON m.mid_id=c.mid_id WHERE m.channel_id={channel}; SELECT ROW_COUNT();"
        f" DELETE c FROM t_mid_capability c JOIN t_channel_mid m ON m.mid_id=c.mid_id WHERE m.channel_id={channel}; SELECT ROW_COUNT();"
        f" DELETE FROM t_channel_mid WHERE channel_id={channel}; SELECT ROW_COUNT();"
        f" DELETE FROM t_channel_account WHERE channel_id={channel}; SELECT ROW_COUNT();"
        f" DELETE FROM t_payment_channel WHERE channel_id={channel}; SELECT ROW_COUNT();")
    report["channelConnectorDeleted"] = dict(zip(["config_change", "capability_detail", "capability", "mid", "account", "channel"],
                                                 [int(r[0]) for r in rows]))



def platform_and_rest(record, report):
    accounts = ",".join(record["platformAccounts"])
    roles = ",".join(record["platformRoles"])
    pf = lambda s: mysql("platform_app", c.LOCAL["MOR_PLATFORM_DB_PASSWORD"], "mor_platform_v2e2e", s)  # noqa: E731
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
    rows = pf(" ".join(f"{sql}; SELECT ROW_COUNT();" for _, sql in statements))
    report["platformDeleted"] = dict(zip([name for name, _ in statements], [int(r[0]) for r in rows]))

    vault = mysql("vault_app", c.LOCAL["MOR_VAULT_DB_PASSWORD"], "mor_vault",
                  "SELECT data_type, COUNT(*) FROM t_vault_record WHERE data_type IN ('CASH_ACCOUNT_NUMBER_V1','CHANNEL_ACCOUNT_CONFIG_V1') "
                  "GROUP BY data_type")
    report["vaultRecordsRetained"] = {k: int(v) for k, v in vault}

    request = urllib.request.Request(f"{c.RMQ}/api/vhosts/{c.vhost_path()}", method="DELETE")
    import base64
    request.add_header("Authorization", "Basic " + base64.b64encode(f"{c.LOCAL['MOR_RABBITMQ_USER']}:{c.LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode())
    with urllib.request.urlopen(request, timeout=30) as response:
        report["vhostDeleted"] = response.status

    redis = ["docker", "exec", "-e", f"REDISCLI_AUTH={c.LOCAL['MOR_REDIS_PASSWORD']}", "mor-local-redis-1", "redis-cli", "-n", "3"]
    keys = subprocess.run(redis + ["--scan", "--pattern", "local-recon6:*"], capture_output=True, text=True).stdout.split()
    if keys:
        subprocess.run(redis + ["DEL", *keys], capture_output=True, text=True)
    report["redisKeysDeleted"] = len(keys)
    print(json.dumps(report, ensure_ascii=False, indent=1))
    (c.STATE / "cleanup-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
