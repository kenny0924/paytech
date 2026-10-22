"""阶段 7 mor-test 回归（acceptance-plan 第 4 节）：运营接口只读巡检 + CYBS / MPGS 授权 / 扣款沙箱各重跑一条 + 十个服务健康与日志。

子命令：
  pages                    运营台各页面依赖的列表接口（工作流、存储管理、数据查询、渠道、Gateway 配置）与标准 API paymentChannelRef
  sandbox                  I_CYBS_NI SBX_AUTH_CAPTURE、I_MPGS_NI SBX_AUTH_CAPTURE_CHAIN 各运行一次（code-mor-46 回归）
  health <sinceUTC>        十个服务 healthy / 重启次数 / 镜像 / 日志 ' ERROR ' 计数（docker 只读）
结果合并写入 accept-regression.json。
"""
import json
import subprocess
import sys
import time

import mt

OUT = "accept-regression.json"
SERVICES = ["risk-decision-service", "payment-service", "acquiring-service", "counterparty-service", "platform-support-service",
            "card-service", "channel-connector-service", "api-gateway", "operations-console-bff", "operations-console-web"]


def pages():
    token = mt.login()
    out = {}
    for name, path in [("navigation", "/auth/v1/me"), ("wfDefinitions", "/workflow-admin/v1/definitions?page=1&size=5"),
                       ("wfTemplates", "/workflow-admin/v1/action-templates?page=1&size=5"),
                       ("wfInstances", "/workflow/v1/instances?page=1&size=5"), ("wfTodos", "/workflow/v1/tasks?page=1&size=5"),
                       ("storageRoutes", "/storage-admin/v1/routes"), ("storageInstances", "/storage-admin/v1/instances"),
                       ("dataSources", "/data-query-admin/v1/data-sources?page=1&size=5"),
                       ("queryTemplates", "/data-query-admin/v1/templates?page=1&size=5"),
                       ("channels", "/channel-admin/v1/channels?page=1&size=20"),
                       ("standardApis", "/external-api-admin/v1/standard-apis?page=1&size=50"),
                       ("gatewayRoutes", "/gateway-admin/v1/routes?page=1&size=5")]:
        status, body = mt.ops(token, "GET", path)
        out[name] = {"http": status, "total": body.get("total") if isinstance(body, dict) else None,
                     "code": body.get("code") if isinstance(body, dict) else None}
    refs = {}
    for code in ("REFUND", "CAPTURE", "PAY", "PAY_QUERY", "QUERY"):
        status, body = mt.ops(token, "GET", f"/external-api-admin/v1/standard-apis/{code}")
        text = json.dumps(body, ensure_ascii=False)
        refs[code] = {"http": status, "version": body.get("version") if isinstance(body, dict) else None,
                      "paymentChannelRef": "paymentChannelRef" in text, "threeDsRequired": "threeDsRequired" in text}
    out["standardApiFields"] = refs
    mt.save(OUT, "pages-" + mt.now(), out)


def sandbox():
    token = mt.login()
    out = {}
    for code, case in (("I_CYBS_NI", "SBX_AUTH_CAPTURE"), ("I_MPGS_NI", "SBX_AUTH_CAPTURE_CHAIN")):
        status, body = mt.ops(token, "POST", f"/external-api-admin/v1/integrations/{code}/verify-cases/{case}/runs",
                              {"reason": "3DS + 卡 BIN 库 mor-test 发布回归（code-mor-46）"})
        run = body.get("runCode") or body.get("verifyRunCode") or body.get("runId")
        detail = {}
        for _ in range(40):
            if not run:
                break
            _, detail = mt.ops(token, "GET", f"/external-api-admin/v1/integrations/{code}/verify-runs/{run}")
            if detail.get("runStatus") not in ("RUNNING", "PENDING", None):
                break
            time.sleep(3)
        out[f"{code}/{case}"] = {"http": status, "start": {k: v for k, v in body.items() if k != "steps"},
                                 "runStatus": detail.get("runStatus"), "failureSummary": detail.get("failureSummary"),
                                 "steps": [{k: s.get(k) for k in ("stepNo", "standardApiCode", "stepStatus")}
                                           for s in detail.get("steps", [])]}
    mt.save(OUT, "sandbox-" + mt.now(), out)


def health(since):
    out = {}
    for svc in SERVICES:
        name = f"mor-test-{svc}-1"
        inspect = json.loads(subprocess.run(["docker", "inspect", name], capture_output=True, text=True, check=True).stdout)[0]
        logs = subprocess.run(["docker", "logs", "--since", since, name], capture_output=True, text=True)
        lines = [line for line in (logs.stdout + logs.stderr).splitlines() if " ERROR " in line]
        out[svc] = {"health": inspect["State"].get("Health", {}).get("Status"), "restarts": inspect["RestartCount"],
                    "image": inspect["Image"][:19], "startedAt": inspect["State"]["StartedAt"], "errorLines": len(lines),
                    "errorSamples": [line[:240] for line in lines[:5]]}
    mt.save(OUT, "health-" + mt.now(), {"since": since, "services": out})


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    {"pages": pages, "sandbox": sandbox, "health": health}[command](*args)
