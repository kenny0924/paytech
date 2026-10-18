"""阶段 5 本机全链路（任务卡 14）：保证 risk-decision 的 TEST 环境对结账点与支付点各有一个生效执行发布。

为什么需要：acquiring 建单调用 CHECKOUT_PREPARE、Payment 每个 Attempt 先调用 PAYMENT_ATTEMPT_RISK；没有生效发布时 risk-decision 返回
503 RISK_DECISION_NOT_CONFIGURED，建单 503、支付失败关闭（ActiveExecutionReleaseReader#require）。*_v2e2e 新库只有初始化目录数据，没有发布。

做法：经真实运营入口（Nginx 18444 → Gateway → Operations BFF → risk-decision）为缺少生效发布的点保存一个“开始 → 放行”决策流，
创建执行发布 → 校验（SHADOWING）→ 激活（ACTIVE）。已有 ACTIVE 发布的点不动（可重复执行）。不建规则集、名单或变量。
用法：python3 risk_setup.py；输出每点的发布 ID 与状态，不打印令牌或口令。
"""
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from e2e_http import Failure, Operations, secrets  # noqa: E402

POINTS = ["CHECKOUT_PREPARE", "PAYMENT_ATTEMPT_RISK"]
EXECUTIONS = "/decision-releases/v1/executions"


def allow_graph():
    """与 risk-decision 测试 FlowBuilder.GraphSpec.direct(ALLOW) 相同的形态。"""
    return {"schemaVersion": 1,
            "nodes": [{"nodeKey": "start", "type": "START", "name": "Start", "position": {"x": "-120", "y": "0"}},
                      {"nodeKey": "finish", "type": "END", "name": "Allow", "position": {"x": "240", "y": "-60"},
                       "decisionCode": "ALLOW"}],
            "edges": [{"edgeKey": "next", "sourceNodeKey": "start", "targetNodeKey": "finish", "type": "UNCONDITIONAL",
                       "priority": 0, "name": "Next"}]}


def active_release(ops, point):
    page = ops.get(f"{EXECUTIONS}?pointCode={point}&status=ACTIVE&page=1&size=10")
    items = (page or {}).get("items") or []
    return items[0] if items else None


def ensure(ops, point):
    found = active_release(ops, point)
    if found:
        print(f"exists  {point} releaseId={found.get('releaseId')} status={found.get('status')}")
        return found.get("releaseId")
    code = f"E2E_V2_ALLOW_{point}_{time.strftime('%Y%m%d%H%M%S')}"
    flow = ops.send("POST", "/decision-flows/v1/flows", {
        "pointCode": point, "flowCode": code[:64], "flowName": "本机全链路放行", "baseFlowVersionId": None,
        "expectedVersion": None, "changeReason": "阶段 5 本机全链路：最小放行流程", "graph": allow_graph()})
    flow_version_id = flow["flowVersionId"]
    release = ops.send("POST", EXECUTIONS, {"pointCode": point, "flowVersionId": str(flow_version_id)})
    release_id = release["releaseId"]
    validated = ops.send("POST", f"{EXECUTIONS}/{release_id}/validate", {})
    if validated.get("status") != "SHADOWING":
        raise SystemExit(f"{point} 发布校验未通过：status={validated.get('status')} "
                         f"checks={json.dumps(validated.get('staticChecks') or validated.get('validation'), ensure_ascii=False)[:400]}")
    activated = ops.send("POST", f"{EXECUTIONS}/{release_id}/activate", {})
    print(f"created {point} flowVersionId={flow_version_id} releaseId={release_id} status={activated.get('status')}")
    return release_id


def main():
    s = secrets()
    ops = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    try:
        result = {point: ensure(ops, point) for point in POINTS}
    except Failure as failure:
        raise SystemExit(f"FAIL {failure}") from None
    print("RISK", json.dumps(result))


if __name__ == "__main__":
    main()
