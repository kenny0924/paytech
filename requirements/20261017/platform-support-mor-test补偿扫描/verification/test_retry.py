"""阶段 7 mor-test 验收：XXL-Job 补偿扫描使"等待重试"的 Webhook 投递自动重发。

前置：Platform 已开启执行器并临时放行 http 与 172.30.73.240/32；xxl_job 已登记 compensationEventScan；
接收容器 mor-test-webhook-receiver（172.30.73.240，/hook 在 /tmp/ok 存在时返回 204，否则 503）已启动且无 /tmp/ok。
合成租户与清理沿用 Operations Webhook 管理的 fixture / cleanup。不打印 Token、密钥与凭据。
用法：E2E_TARGET=test E2E_PLATFORM_IMAGE=<当前 Platform 镜像> python3 -I test_retry.py
"""
import json
import os
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
OPS = ROOT / "requirements/20261016/operations-console-Webhook管理"
sys.path.insert(0, str(OPS / "verification"))
from local_acceptance import BASE, CLIENT, check, results, status_of, submit_event, wait_until, window  # noqa: E402
import e2e_http  # noqa: E402

RECEIVER = "mor-test-webhook-receiver"
URL = "http://172.30.73.240/hook"
START = datetime.now(timezone.utc)


def xxl_sql(statement):
    env = e2e_http.read_env(ROOT / "test-environment/.env.test")
    out = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uxxl_job", "-N", "-B",
                          "xxl_job", "-e", statement], env={**os.environ, "MYSQL_PWD": env["MOR_XXL_JOB_DB_PASSWORD"]},
                         capture_output=True, text=True, check=True)
    return [row.split("\t") for row in out.stdout.splitlines() if row]


def delivery(ops):
    """最新一条投递；入口短暂不可用（如 Nginx 重建）时返回空，由轮询重试。"""
    try:
        items = ops.get(f"{BASE}/deliveries?clientId={CLIENT}&{window()}")["items"]
    except (e2e_http.Failure, OSError):
        return {}
    return items[0] if items else {}


def logs(container):
    out = subprocess.run(["docker", "logs", "--since", START.strftime("%Y-%m-%dT%H:%M:%SZ"), container],
                         capture_output=True, text=True)
    return out.stdout + out.stderr


def main():
    assert os.environ.get("E2E_TARGET") == "test", "只用于 mor-test"
    s = e2e_http.secrets()
    try:
        e2e_http.platform_sql((OPS / "db/platform-support-service/acceptance-fixture-local.sql").read_text())
        ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
        registry = xxl_sql("SELECT registry_value FROM xxl_job_registry WHERE registry_key='platform-support-service'")
        check("调度", f"Platform 执行器已注册（{registry}）", registry == [["http://172.30.73.21:9999/"]])
        code, created = status_of(lambda: ops.send("POST", f"{BASE}/clients/{CLIENT}/endpoints",
                                                  {"url": URL, "eventTypes": ["order.paid"], "expectedVersion": 0,
                                                   "reason": "补偿扫描验收"}, key=True))
        check("准备", f"临时放行生效，http 接收端可登记（{code}）", code == 200 and "endpointId" in created)
        event = f"evt_e2e_scan_{uuid.uuid4().hex[:8]}"
        check("准备", "以 commerce-service 提交 order.paid（204）", submit_event(event) == 204)
        check("重试", "首次投递 503 后进入等待重试",
              wait_until(lambda: delivery(ops).get("status") == "RETRY_WAIT" and delivery(ops).get("attemptCount") == 1))
        due = e2e_http.platform_sql("SELECT next_execute_at FROM t_compensation_event WHERE event_type='WEBHOOK_DELIVERY'"
                                    " AND execute_status='ERROR' ORDER BY id DESC LIMIT 1")
        print(f"  下一次到期（UTC）：{due}")
        subprocess.run(["docker", "exec", RECEIVER, "touch", "/tmp/ok"], check=True)
        began = time.monotonic()
        done = wait_until(lambda: delivery(ops).get("status") == "SUCCEEDED", seconds=900)
        final = delivery(ops)
        check("重试", f"扫描自动重发后已送达（尝试 {final.get('attemptCount')} 次，放行后 {int(time.monotonic() - began)} 秒）",
              done and final.get("attemptCount") == 2)
        runs = xxl_sql("SELECT COUNT(*), SUM(handle_code=200) FROM xxl_job_log l JOIN xxl_job_group g ON g.id=l.job_group"
                       f" WHERE g.app_name='platform-support-service' AND l.trigger_time >= '{START.strftime('%Y-%m-%d %H:%M:%S')}'")
        check("调度", f"扫描任务按周期执行且成功（调度 / 成功：{runs}）", runs and int(runs[0][0]) >= 2 and runs[0][0] == runs[0][1])
        access = logs(RECEIVER)
        check("重试", "接收端先 503 后 204", '"POST /hook HTTP/1.1" 503' in access and '"POST /hook HTTP/1.1" 204' in access)
        platform = logs("mor-test-platform-support-service-1")
        check("日志", "Platform 验收期间无 ERROR", " ERROR " not in platform)
    finally:
        e2e_http.platform_sql((OPS / "db/platform-support-service/acceptance-cleanup-local.sql").read_text())
        left = e2e_http.platform_sql("SELECT COUNT(*) FROM t_access_tenant WHERE tenant_id IN (880000000000079001,880000000000079002)")
        check("收尾", f"合成数据已清理（{left}）", left == [["0"]])
    print(f"\n{sum(r[2] for r in results)}/{len(results)} 通过")
    return 0 if all(r[2] for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
