"""阶段 2 迁移数据抽样（mor-test，runbook 第 7 节最后一行）：经真实运营入口读取迁移数据，与 V2 库实时计数逐项比较。

运营入口 http://localhost:28080（Nginx → Gateway → Operations BFF → Platform / risk-decision / compliance），管理员登录；
SQL 计数用各服务自有账号只读查询（e2e_http.sql，E2E_TARGET=test 时为 127.0.0.1:23306 的 V2 正式库）。只读，不写任何数据。
阶段 1 演练的 sample_reads.py 只能指向演练库（端口 23307）；本脚本是它在 mor-test 上的对应物，口径相同：
  外部 API 接入方、运营人工账号（未关闭）、Gateway Target / 其中 acquiring-service、CLIENT_API 路由、风控决策流（两个决策点合计）、
  可配置规则集、Client 入驻审核案件与单案详情。验收运行会新增入驻案件，因此与实时计数比较（迁移时刻的数量见 verify-detail.txt）。
用法：E2E_TARGET=test python3 test_sample_reads.py；输出每项 OK / MISMATCH，不打印令牌或口令。
"""
import os
import sys
from pathlib import Path

if os.environ.get("E2E_TARGET") != "test":
    raise SystemExit("仅用于 mor-test：E2E_TARGET=test")

LOCAL_E2E = Path(__file__).resolve().parents[3] / "20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
sys.path.insert(0, str(LOCAL_E2E))

from e2e_http import Failure, Operations, secrets, sql  # noqa: E402

# 阶段 2 migrate.sh apply 在 mor-test 执行的时刻（migrate-apply.log，UTC）；之前创建的案件为迁移带入。
MIGRATED_BEFORE = "2026-10-18 13:35:00"
RESULTS = []


def scalar(service, statement):
    return int(sql(service, statement)[0][0])


def check(item, sql_count, http_count, note=""):
    ok = sql_count == http_count
    RESULTS.append(ok)
    print(f"{'OK      ' if ok else 'MISMATCH'} {item:<44} SQL={sql_count:<6} HTTP={http_count}  {note}")


def total(ops, path):
    try:
        body = ops.get(path)
    except Failure as failure:
        return f"HTTP {failure.status}"
    return body.get("total") if isinstance(body, dict) and "total" in body else len(body.get("items") or [])


def main():
    ops = Operations(secrets()["E2E_ADMIN_EMAIL"], secrets()["E2E_ADMIN_PASSWORD"])
    check("外部 API 接入方", scalar("platform", "SELECT COUNT(*) FROM t_integration"),
          total(ops, "/external-api-admin/v1/integrations?page=1&size=100"))
    check("运营人工账号（未关闭）",
          scalar("platform", "SELECT COUNT(*) FROM t_user_account WHERE realm='OPERATIONS' AND account_type='HUMAN' "
                             "AND status<>'CLOSED'"),
          total(ops, "/iam/v1/accounts?page=1&size=100"), "API 口径：OPERATIONS 人工账号且未关闭")
    targets = ops.get("/gateway-admin/v1/target-services")
    items = targets.get("items") if isinstance(targets, dict) else targets
    check("Gateway Target", scalar("platform", "SELECT COUNT(*) FROM t_gateway_target WHERE status='ACTIVE'"), len(items))
    check("Gateway Target 发现名 acquiring-service",
          scalar("platform", "SELECT COUNT(*) FROM t_gateway_target WHERE discovery_service_name='acquiring-service'"),
          sum(1 for t in items if t.get("discoveryServiceName") == "acquiring-service"))
    flows = 0
    for point in ("CHECKOUT_PREPARE", "PAYMENT_ATTEMPT_RISK"):
        value = total(ops, f"/decision-flows/v1/flows?pointCode={point}&page=1&size=100")
        flows = value if isinstance(value, str) else flows + value
        if isinstance(value, str):
            break
    check("风控决策流（CONFIGURABLE，两个决策点合计）",
          scalar("risk-decision", "SELECT COUNT(*) FROM t_decision_flow WHERE status='CONFIGURABLE'"), flows)
    check("风控规则集（CONFIGURABLE 且有当前版本）",
          scalar("risk-decision", "SELECT COUNT(*) FROM t_rule_set rs JOIN t_rule_set_version v "
                                  "ON v.rule_set_version_id = rs.current_rule_set_version_id WHERE rs.status='CONFIGURABLE'"),
          total(ops, "/decision-rule-sets/v1/rule-sets?page=1&size=100"))
    cases = ops.get("/client-onboarding-admin/v1/cases?page=1&size=100")
    check("Client 入驻审核案件",
          scalar("compliance", "SELECT COUNT(*) FROM t_due_diligence_case WHERE source_namespace='CCT_CLIENT_APPLICATION' "
                               "AND role_type='CLIENT'"), cases.get("total"))
    # 迁移带入的案件：create_time 早于迁移执行（迁移原值复制 create_time）。逐件读详情；非 RETURNED 案件要求申请快照可用。
    # RETURNED 案件的申请快照按现有行为可能为 UNAVAILABLE（迁移后新建的 RETURNED 案件同样如此），只要求详情可读。
    migrated = {row[0] for row in sql("compliance", "SELECT case_id FROM t_due_diligence_case "
                                                    f"WHERE source_namespace='CCT_CLIENT_APPLICATION' AND role_type='CLIENT' "
                                                    f"AND create_time < '{MIGRATED_BEFORE}'")}
    readable = 0
    for case in (c for c in cases["items"] if c["caseId"] in migrated):
        detail = ops.get(f"/client-onboarding-admin/v1/cases/{case['caseId']}")
        snapshot_ok = case["status"] == "RETURNED" or detail.get("applicationStatus") == "AVAILABLE"
        readable += (detail.get("review") or {}).get("caseId") == case["caseId"] and snapshot_ok
    check("迁移带入的入驻案件详情逐件可读（申请快照按状态）", len(migrated), readable, f"迁移前 {MIGRATED_BEFORE} UTC 之前创建")
    print(f"\n合计 {sum(RESULTS)}/{len(RESULTS)} 一致")
    raise SystemExit(0 if all(RESULTS) else 1)


if __name__ == "__main__":
    main()
