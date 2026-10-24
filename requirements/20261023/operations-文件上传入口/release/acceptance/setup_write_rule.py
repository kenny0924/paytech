"""运营控制台文件上传入口阶段 7 第 ⑥ 步：经运营「存储管理」接口新建写入规则 (operations-console-bff, PUBLIC) → S3_TEST /
mor-test-platform-public-k7q2、检查策略 PUBLIC_DOCUMENT、上传期限 900 秒（technique 第 9 节、需求 Q2）。写法同阶段 5 的 stage5_setup.py，
桶与检查策略在 mor-test 已存在（阶段 6 预检 SQL 核对），本脚本不登记桶、不建策略、不改任何已有规则。

  1. 读写入规则：(*, PUBLIC) 与 (operations-console-bff, PRIVATE) 记下原值；(operations-console-bff, PUBLIC) 已存在且目标相同则跳过，目标不同则停止；
  2. POST /storage-admin/v1/routes 新建；
  3. 复读：新规则目标正确，其他规则逐条不变；
  4. 等存储快照刷新（每 5 秒查一次 /file-upload/v1/constraints，最多 120 秒）：configured=true、routeSourceSystem=operations-console-bff、
     S3_TEST / mor-test-platform-public-k7q2、PUBLIC_DOCUMENT、允许 pdf / png / jpeg、20 MiB、900 秒。
回滚：DELETE /storage-admin/v1/routes/operations-console-bff/PUBLIC（正文 expectedVersion + reason，同阶段 5 stage5_api.py unroute），
删除后回落到 (*, PUBLIC)（目标相同），页面仍可上传。

用法：
  python3 -I setup_write_rule.py --dry-run   只打印计划（不登录、不调用任何 mor-test 接口）
  python3 -I setup_write_rule.py             阶段 7 执行（写数据；需在块 136 激活之后，第 4 步要读 /file-upload/v1/constraints）
  python3 -I setup_write_rule.py --rollback  删除这条规则（回滚）
"""
import argparse
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))
import fu7_common as o  # noqa: E402

c = o.c
REASON = "运营控制台文件上传入口阶段 7：本入口的公开写入规则（需求 Q2）"
INSTANCE, BUCKET, POLICY, SECONDS = o.TARGET
BODY = {"sourceSystem": o.SOURCE, "accessLevel": "PUBLIC", "instanceCode": INSTANCE, "bucketName": BUCKET, "scanPolicyCode": POLICY,
        "maxUploadSeconds": SECONDS, "reason": REASON}
FIELDS = ("instanceCode", "bucketName", "scanPolicyCode", "maxUploadSeconds")


def summary_of(route):
    return {k: route.get(k) for k in FIELDS} if route else None


def create(token, record):
    status, before, _ = c.routes(token)
    if not c.check("⑥", f"读写入规则（{status}，{len(before)} 条）", status == 200):
        raise SystemExit(1)
    record["before"] = {f"{s}/{l}": summary_of(r) for (s, l), r in before.items()}
    existing = before.get((o.SOURCE, "PUBLIC"))
    if existing:
        same = summary_of(existing) == {k: BODY[k] for k in FIELDS}
        c.check("⑥", f"({o.SOURCE}, PUBLIC) 已存在，目标{'相同，跳过' if same else '不同，停止'}", same, summary_of(existing))
        if not same:
            raise SystemExit(1)
    else:
        status, body, _ = c.raw(token, "POST", c.ADMIN + "/routes", BODY)
        c.check("⑥", f"新建 ({o.SOURCE}, PUBLIC) → {INSTANCE} / {BUCKET} / {POLICY} / {SECONDS} 秒（{status}）", status in (200, 201), o.problem(body))
        if status not in (200, 201):
            raise SystemExit(1)
    status, after, _ = c.routes(token)
    record["after"] = {f"{s}/{l}": summary_of(r) for (s, l), r in after.items()}
    c.check("⑥", "新规则目标正确", summary_of(after.get((o.SOURCE, "PUBLIC"))) == {k: BODY[k] for k in FIELDS}, summary_of(after.get((o.SOURCE, "PUBLIC"))))
    others = {k: v for k, v in record["after"].items() if k != f"{o.SOURCE}/PUBLIC"}
    c.check("⑥", f"其他 {len(others)} 条规则逐条不变", others == {k: v for k, v in record["before"].items() if k != f"{o.SOURCE}/PUBLIC"})


def wait_configured(token, record):
    began, body, status = time.time(), {}, None
    while time.time() - began <= 120:
        status, body = o.constraints(token)
        if status == 200 and body.get("configured") and body.get("routeSourceSystem") == o.SOURCE:
            break
        time.sleep(5)
    record["constraints"] = body
    c.check("⑥", f"写入限制（{round(time.time() - began)} 秒后）：configured、命中精确规则、{INSTANCE} / {BUCKET}、{POLICY} r"
                 f"{body.get('scanPolicyRevisionNo')}、{body.get('allowedMimeTypes')}、{body.get('maxSizeBytes')} 字节、{body.get('maxUploadSeconds')} 秒",
            status == 200 and body.get("configured") is True and body.get("routeSourceSystem") == o.SOURCE and body.get("instanceCode") == INSTANCE
            and body.get("bucketName") == BUCKET and body.get("scanPolicyCode") == POLICY and body.get("maxUploadSeconds") == SECONDS
            and body.get("allowedMimeTypes") == ["application/pdf", "image/png", "image/jpeg"] and body.get("maxSizeBytes") == str(20 * 1024 * 1024)
            and (body.get("publicBaseUrl") or "").startswith(f"https://{o.PUBLIC_HOST}"), body)


def rollback(token, record):
    status, routes, _ = c.routes(token)
    route = routes.get((o.SOURCE, "PUBLIC"))
    if not route:
        c.check("回滚", f"({o.SOURCE}, PUBLIC) 不存在，跳过", True)
        return
    status, body, _ = c.raw(token, "DELETE", f"{c.ADMIN}/routes/{o.SOURCE}/PUBLIC", {"expectedVersion": route.get("version"),
                                                                                       "reason": REASON + "（回滚）"})
    c.check("回滚", f"删除 ({o.SOURCE}, PUBLIC)（{status}）", status in (200, 204), o.problem(body))
    _, routes, _ = c.routes(token)
    record["afterRollback"] = {f"{s}/{l}": summary_of(r) for (s, l), r in routes.items()}
    c.check("回滚", "(*, PUBLIC) 仍在（页面回落到默认公开规则）", (("*", "PUBLIC") in routes) and (o.SOURCE, "PUBLIC") not in routes)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--rollback", action="store_true")
    args = parser.parse_args()
    if args.dry_run:
        print(f"计划：POST {c.ORIGIN}/api/operations{c.ADMIN}/routes {dict(BODY, reason='…')}；随后等待 {o.UPLOAD}/constraints "
              f"命中精确规则（≤ 120 秒）。回滚：DELETE {c.ADMIN}/routes/{o.SOURCE}/PUBLIC。")
        c.check("干跑", "目标为 technique 第 9 节：S3_TEST / mor-test-platform-public-k7q2 / PUBLIC_DOCUMENT / 900 秒",
                (INSTANCE, BUCKET, POLICY, SECONDS) == ("S3_TEST", "mor-test-platform-public-k7q2", "PUBLIC_DOCUMENT", 900))
        c.check("干跑", "请求体字段与运营存储管理创建写入规则合同一致（sourceSystem、accessLevel、instanceCode、bucketName、scanPolicyCode、"
                        "maxUploadSeconds、reason）", sorted(BODY) == sorted(("sourceSystem", "accessLevel", "instanceCode", "bucketName",
                                                                              "scanPolicyCode", "maxUploadSeconds", "reason")))
        return 0 if c.summary() else 1
    record = {"startedAt": c.now_iso()}
    name = f"write-rule-{'rollback-' if args.rollback else ''}{record['startedAt'].replace(':', '')}.json"
    try:
        token = c.admin_token()
        if args.rollback:
            rollback(token, record)
        else:
            create(token, record)
            wait_configured(token, record)
    finally:
        record["finishedAt"] = c.now_iso()
        print("运行记录：", c.save(name, record))
    return 0 if c.summary() else 1


if __name__ == "__main__":
    sys.exit(main())
