#!/usr/bin/env python3
"""Rehearsal-only read sampling after migration: SQL row counts on the migrated databases plus one HTTP read per item
against the V2 services started by rehearse-services.sh. Prints counts, HTTP status codes and OK/MISMATCH only; never
prints row values, IDs, tokens or response bodies.

Platform admin reads need a live Operations session. The migrated sessions have all expired, so this script extends the
newest ACTIVE Operations session in the rehearsal copy for the duration of the two Platform admin reads and restores its
original expiry afterwards. It refuses to run unless MT_PORT is the rehearsal port 23307 and the server is the
mor-mt1-mysql container (hostname check), so it can never touch mor-test.

Environment: MYSQL_PWD (rehearsal root), MT_PORT=23307.
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

PORT = os.environ.get("MT_PORT", "")
results = []


def query(sql):
    command = ["mysql", "-h127.0.0.1", "-P" + PORT, "-uroot", "--protocol=tcp", "--default-character-set=utf8mb4",
               "-N", "-B", "-e", sql]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        sys.exit("query failed: " + result.stderr.strip().splitlines()[-1])
    return [line.split("\t") for line in result.stdout.splitlines() if line]


def scalar(sql):
    return int(query(sql)[0][0])


def http(url, headers=None):
    request = urllib.request.Request(url, headers={"Accept": "application/json", **(headers or {})})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        body = error.read()
        try:
            return error.code, json.loads(body)
        except ValueError:
            return error.code, None


def check(item, sql_count, status, http_count, note=""):
    ok = status == 200 and (http_count is None or http_count == sql_count)
    results.append((item, sql_count, status, http_count, "OK" if ok else "MISMATCH", note))


def guard():
    if PORT != "23307":
        sys.exit("refusing: MT_PORT must be the rehearsal port 23307")
    hostname = query("SELECT @@hostname")[0][0]
    container = subprocess.run(["docker", "inspect", "-f", "{{.Config.Hostname}}", "mor-mt1-mysql"],
                               capture_output=True, text=True).stdout.strip()
    if not container or hostname != container:
        sys.exit("refusing: server is not the mor-mt1-mysql rehearsal container")


def with_operations_session(action):
    row = query("SELECT session_id, account_id, idle_expire_time, absolute_expire_time FROM mor_platform.t_auth_session "
                "WHERE realm='OPERATIONS' AND status='ACTIVE' ORDER BY issued_time DESC LIMIT 1")
    session_id, account_id, idle, absolute = row[0]
    query("UPDATE mor_platform.t_auth_session SET idle_expire_time = UTC_TIMESTAMP(6) + INTERVAL 10 MINUTE, "
          "absolute_expire_time = UTC_TIMESTAMP(6) + INTERVAL 10 MINUTE WHERE session_id = %s" % session_id)
    try:
        return action({"X-Gateway-Actor-Subject": account_id, "X-Gateway-Session-Id": session_id})
    finally:
        query("UPDATE mor_platform.t_auth_session SET idle_expire_time = '%s', absolute_expire_time = '%s' WHERE session_id = %s"
              % (idle, absolute, session_id))


def main():
    guard()
    actor = {"X-Gateway-Actor-Subject": "900000000000000001", "X-Gateway-Session-Id": "900000000000000002"}

    # Gateway 路由与 Target（Platform 实时编译的 Snapshot）
    status, body = http("http://127.0.0.1:18106/platform-support/gateway-config/v1/public-snapshot")
    check("Gateway routes", scalar("SELECT COUNT(*) FROM mor_platform.t_gateway_route WHERE status='ACTIVE'"), status,
          len(body["routes"]) if status == 200 else None)
    check("Gateway targets", scalar("SELECT COUNT(*) FROM mor_platform.t_gateway_target WHERE status='ACTIVE'"), status,
          len(body["targets"]) if status == 200 else None)
    acquiring = [t for t in (body or {}).get("targets", []) if t.get("discoveryServiceName") == "acquiring-service"]
    check("Gateway target commerce-client-api -> acquiring-service", 1, status, len(acquiring))

    # 币种参考数据（20 新增）
    status, body = http("http://127.0.0.1:18106/platform-support/platform/v1/currency-specs")
    check("Platform currency specs", scalar("SELECT COUNT(*) FROM mor_platform.t_currency_spec"), status,
          len(body["items"]) if status == 200 else None)

    # 外部 API 配置与运营账号（Platform 管理接口，需要运营会话）
    def platform_admin(headers):
        out = []
        out.append(http("http://127.0.0.1:18106/platform-support/external-api-admin/v1/integrations?page=1&size=100", headers))
        out.append(http("http://127.0.0.1:18106/platform-support/operations/v1/accounts?page=1&size=100", headers))
        return out
    (status_i, body_i), (status_a, body_a) = with_operations_session(platform_admin)
    check("External API integrations", scalar("SELECT COUNT(*) FROM mor_platform.t_integration"), status_i,
          body_i.get("total") if status_i == 200 else None)
    check("Operations accounts", scalar("SELECT COUNT(*) FROM mor_platform.t_user_account WHERE realm='OPERATIONS' "
                                        "AND account_type='HUMAN' AND status<>'CLOSED'"),
          status_a, body_a.get("total") if status_a == 200 else None, "API 口径：OPERATIONS 人工账号且未关闭")

    # 风控规则（risk-decision 管理读接口；Operations BFF 授权，服务不校验会话）
    flows = 0
    statuses = []
    for point in ("CHECKOUT_PREPARE", "PAYMENT_ATTEMPT_RISK"):
        status, body = http("http://127.0.0.1:18104/decision-flows/v1/flows?pointCode=%s&page=1&size=100" % point, actor)
        statuses.append(status)
        flows += body.get("total", 0) if status == 200 else 0
    check("Risk decision flows", scalar("SELECT COUNT(*) FROM mor_risk_decision.t_decision_flow WHERE status='CONFIGURABLE'"),
          max(statuses), flows, "API 口径：CONFIGURABLE，两个决策点合计")
    status, body = http("http://127.0.0.1:18104/decision-rule-sets/v1/rule-sets?page=1&size=100", actor)
    check("Risk rule sets", scalar("SELECT COUNT(*) FROM mor_risk_decision.t_rule_set rs JOIN mor_risk_decision.t_rule_set_version v "
                                   "ON v.rule_set_version_id = rs.current_rule_set_version_id WHERE rs.status='CONFIGURABLE'"), status,
          body.get("total") if status == 200 else None, "API 口径：CONFIGURABLE 且有当前版本")
    check("Risk rules (SQL only, mor_risk -> mor_risk_decision)", scalar("SELECT COUNT(*) FROM mor_risk.t_risk_rule"), 200,
          scalar("SELECT COUNT(*) FROM mor_risk_decision.t_risk_rule"))

    # 客户申请（compliance 审核案件 + 案件的申请快照；client-master 申请行数）
    status, body = http("http://127.0.0.1:18112/client-onboarding-admin/v1/cases?page=1&size=100", actor)
    check("Compliance review cases", scalar("SELECT COUNT(*) FROM mor_compliance.t_due_diligence_case "
                                            "WHERE source_namespace='CCT_CLIENT_APPLICATION' AND role_type='CLIENT'"), status,
          body.get("total") if status == 200 else None, "API 口径：Client 入驻案件")
    case_id = (body or {}).get("items", [{}])[0].get("caseId")
    status, _ = http("http://127.0.0.1:18112/client-onboarding-admin/v1/cases/%s" % case_id, actor)
    check("Compliance case detail (one case)", 1, status, None)
    check("Client applications (SQL only, mor_cct -> mor_client_master)",
          scalar("SELECT COUNT(*) FROM mor_cct.t_client_application"), 200,
          scalar("SELECT COUNT(*) FROM mor_client_master.t_client_application"))

    # 收单订单（acquiring Client API，经 Gateway 透传的 Client 头）
    order_no, client_id = query("SELECT order_no, client_id FROM mor_acquiring.t_commerce_order ORDER BY create_time DESC LIMIT 1")[0]
    status, _ = http("http://127.0.0.1:18102/commerce/client-gateway-api/v1/orders/%s" % order_no,
                     {"X-Gateway-Client-Id": client_id, "X-Gateway-Credential-Id": "1"})
    check("Acquiring order read (one order)", 1, status, None)

    # Vault 访问规则（管理接口，调用方 operations-console-bff）
    status, body = http("http://127.0.0.1:28197/vault/v1/admin/access-rules?callerService=client-master-service",
                        {"X-Caller-Service": "operations-console-bff", "X-Operator-Id": "mt1-rehearsal", "X-Request-Id": "mt1-sample-vault"})
    items = body.get("items") if isinstance(body, dict) else None
    check("Vault rules client-master-service",
          scalar("SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE caller_service='client-master-service'"), status,
          len(items) if items is not None else None)

    width = max(len(r[0]) for r in results)
    for item, sql_count, status, http_count, verdict, note in results:
        print("%-*s  sql=%-5s http=%s count=%-5s %s %s" % (width, item, sql_count, status, http_count if http_count is not None else "-", verdict, note))
    failed = [r for r in results if r[4] != "OK"]
    print("samples: %d, OK: %d, MISMATCH: %d" % (len(results), len(results) - len(failed), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
