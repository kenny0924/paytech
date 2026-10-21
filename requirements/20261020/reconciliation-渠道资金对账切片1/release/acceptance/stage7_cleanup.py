"""对账切片 1 阶段 7 mor-test 验收清理（由阶段 5 verification/cleanup.py 改写；只删本次运行创建的数据）。

1. mor_reconciliation：先核对全部行都属于本次运行（清算账户编码含运行号、银行账户 ID 在记录中、平台流水的事实 ID 在记录中），
   有任何外来行即中止并列出；通过后 15 张表逐表 DELETE 并核对为 0（验收前 15 张表为空）。
2. mor_channel_connector：按记录的渠道 ID 删除能力明细 / 能力组 / MID / 技术账号 / 渠道及其变更记录。
3. mor_platform：按记录的合成人员与角色 ID 删除刷新令牌、会话、挑战、口令、成员角色、成员、账号、角色授权、角色；审计事件留痕不删。
4. RabbitMQ /mor-test：reconciliation.billing.dlq 只含本次缺引用事实时清空；删除观察队列 recon7.probe.reconciliation-events。
5. Vault（银行账号、渠道账号配置的 Protect 记录）按先例只计数不删。
XXL 任务、块 123 激活状态、镜像不动（属于发布本身）。
用法：python3 -I stage7_cleanup.py <RECON7_STATE/run-*.json> [--dry-run]
"""
import json
import sys
import urllib.error
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon7_common as c  # noqa: E402

RECON_TABLES = ["t_bank_match", "t_bank_statement_line", "t_bank_statement", "t_expected_bank_movement", "t_recon_resolution",
                "t_recon_exception", "t_channel_statement_line", "t_channel_statement", "t_platform_txn", "t_clearing_account_mid",
                "t_clearing_account", "t_cash_account", "t_compensation_event", "t_consumed_event", "t_consumed_aggregate_version"]


def counts():
    return {t: int(c.sql("mor_reconciliation", f"SELECT COUNT(*) FROM {t}")[0][0]) for t in RECON_TABLES}


def foreign_rows(record):
    run = record["run"]
    cash = {str(x) for x in record.get("cashAccounts", [])}
    facts = {str(x) for x in record.get("facts", {}).values()}
    problems = []
    for code, in c.sql("mor_reconciliation", "SELECT clearing_account_code FROM t_clearing_account"):
        if run not in code:
            problems.append(f"clearing_account {code}")
    for cid, in c.sql("mor_reconciliation", "SELECT id FROM t_cash_account"):
        if cid not in cash:
            problems.append(f"cash_account {cid}")
    for fid, in c.sql("mor_reconciliation", "SELECT billing_fact_id FROM t_platform_txn"):
        if fid not in facts:
            problems.append(f"platform_txn fact {fid}")
    return problems


def main():
    record = json.loads(Path(sys.argv[1]).read_text())
    dry = "--dry-run" in sys.argv
    report = {"run": record["run"], "reconciliationBefore": counts()}
    problems = foreign_rows(record)
    if problems:
        print(json.dumps({"abort": "mor_reconciliation 有非本次运行的行，未删除任何数据", "rows": problems[:50]}, ensure_ascii=False, indent=1))
        sys.exit(1)
    channel = record.get("channel", {}).get("channelId")
    accounts = ",".join(str(a) for a in record.get("platformAccounts", []))
    roles = ",".join(str(r) for r in record.get("platformRoles", []))
    if dry:
        print(json.dumps({**report, "channel": channel, "platformAccounts": accounts, "platformRoles": roles}, ensure_ascii=False, indent=1))
        return

    c.sql("mor_reconciliation", " ".join(f"DELETE FROM {t};" for t in RECON_TABLES))
    report["reconciliationAfter"] = counts()

    if channel:
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
        report["channelConnectorDeleted"] = dict(zip(["config_change", "capability_detail", "capability", "mid", "account", "channel"],
                                                     [int(r[0]) for r in rows]))

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

    vh = c.vhost_path()
    dlq = c.queue("reconciliation.billing.dlq") or {}
    if dlq.get("messages", 0):
        peek = c.rmq("POST", f"/api/queues/{vh}/reconciliation.billing.dlq/get",
                     {"count": 50, "ackmode": "ack_requeue_true", "encoding": "auto", "truncate": 500000})
        ids = {json.loads(m["payload"]).get("payload", {}).get("factId") for m in peek}
        if ids <= {str(record.get("facts", {}).get("missingRef"))}:
            c.rmq("DELETE", f"/api/queues/{vh}/reconciliation.billing.dlq/contents")
            report["dlqPurged"] = len(peek)
        else:
            report["dlqKept"] = sorted(str(i) for i in ids)
    try:
        c.rmq("DELETE", f"/api/queues/{vh}/{c.PROBE_QUEUE}")
        report["probeQueueDeleted"] = True
    except urllib.error.HTTPError as failure:
        report["probeQueueDeleted"] = f"HTTP {failure.code}"

    report["vaultRecordsRetained"] = {k: int(v) for k, v in c.sql(
        "mor_vault", "SELECT data_type, COUNT(*) FROM t_vault_record WHERE data_type IN ('CASH_ACCOUNT_NUMBER_V1','CHANNEL_ACCOUNT_CONFIG_V1') GROUP BY data_type")}
    print(json.dumps(report, ensure_ascii=False, indent=1))
    (c.STATE / f"cleanup-{record['run']}.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
