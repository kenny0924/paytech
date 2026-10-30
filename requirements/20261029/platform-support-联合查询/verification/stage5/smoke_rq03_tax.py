"""联合查询阶段 5：换上合并 ops-bff 后，REQ-03（承保）与税务查询接口的只读冒烟，经 Nginx 18444（管理员令牌）。不写任何数据。"""
import json
import site
import sys

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
rows = []


def probe(label, path, ok_statuses):
    try:
        body = ops.get(path)
        status = 200
    except e2e_http.Failure as failure:
        status = failure.status
        try:
            body = json.loads(failure.body)
        except ValueError:
            body = {"raw": failure.body[:120]}
    summary = {k: body.get(k) for k in ("total", "code")} if isinstance(body, dict) else {}
    rows.append((label, status, status in ok_statuses, summary))
    print(("PASS " if status in ok_statuses else "FAIL ") + label, status, json.dumps(summary, ensure_ascii=False))


probe("REQ-03 承保列表", "/risk-underwriting/v1/underwritings?page=1&size=10", {200})
probe("REQ-03 承保统计", "/risk-underwriting/v1/underwriting-statistics", {200})
probe("税务 S1 订单（已登记）", "/tax/v1/orders/ord_3LNgHd0TWGaDLjatKpMN9CnRwDu7TN", {200})
probe("税务 不存在订单 → 404", "/tax/v1/orders/ORD-NOTFOUND-20261029", {404})
probe("联合查询键类型列表（不需要 EXECUTE）", "/data-query-admin/v1/key-types?size=10", {200})
print(sum(1 for r in rows if r[2]), "/", len(rows), "passed")
