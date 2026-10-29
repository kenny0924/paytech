#!/usr/bin/env python3
"""REQ-12 业务信息补采 · mor-test 发布后 API 验收（runbook 第 ⑩ 步；阶段 5 的 API 子集）。

链路：Client 门户 /api/client/...（Nginx → Gateway → client-console-bff → client-master → commercial）；运营控制台 /api/operations/...（只读 GET）。
复用切片 1 验收脚本 onboarding_acceptance.py 与 V2 e2e 公共件 e2e_http.py（--env test → E2E_TARGET=test；--env local 用于本机演练）。
不停服务、不执行 SQL 写入（注册邮件收不到时沿用既有 mint_signup_link 机制改写本次新账号自己的注册挑战，同法务修订验收）；
不提交、不审批任何申请；本次新建的两份草稿申请最后撤回（WITHDRAWN）。

场景：
  F  form-schema（AE 机构 / AE 个人 v3）：四个新字段、要求级别、天数可见条件；支付方式选项 = commercial ACTIVE 支付方式（只读 SQL 取期望值）；交付方式目录
  O  新账号 AE 机构草稿：CODE_LIST 规范化、PRE_ORDER + 天数、改 INSTANT 清空天数、http 422、非法代码 422、天数 0 / 400 清单 FIELD_FORMAT_INVALID、
     退款政策 EVENTUALLY_DUE → 撤回
  I  同一账号 AE 个人草稿：退款政策 OPTIONAL 无清单项、支付方式 / 交付方式 CURRENTLY_DUE → 撤回
  R  运营只读：在途 v1 审核案件 application.values.business 四键为空（页面"未采集"）；运营 Schema 查看 v3 ACTIVE、v2 RETIRED
用法：python3 -I rq12_api.py --env test|local；结果 result-<env>-<UTC>.json；退出码 0 表示无 FAIL。账号凭据写 ~/Code-MoR-backups/rq12-20261029/acceptance-<env>.json（0600）。
"""
import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
WORKTREE = HERE.parents[4]
ACCEPTANCE = WORKTREE / "requirements/20261027/client-master-全球合规入驻与资格切片1/assets/acceptance"
V2_E2E = WORKTREE / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
STATE = Path.home() / "Desktop/Workspace/Code-MoR-backups/rq12-20261029"

V3_ORG, V3_IND = "9281200000900301", "9281200000900303"
NEW_KEYS = ("expectedPaymentMethods", "refundPolicyUrl", "fulfillmentType", "fulfillmentDays")
NEW_FIELDS = tuple("business." + k for k in NEW_KEYS)
REFUND = "https://shop.example.test/refunds"


def setup(env):
    os.environ["E2E_TARGET"] = "test" if env == "test" else "local"
    os.environ.setdefault("ONB_STATE", str(STATE))
    sys.path.insert(0, str(V2_E2E))
    sys.path.insert(0, str(ACCEPTANCE))
    global E, V2, OA
    import e2e_http as E  # noqa: E402
    import acceptance_flow as V2  # noqa: E402
    import onboarding_acceptance as OA  # noqa: E402
    OA.E, OA.V2 = E, V2


def field(schema, key):
    return next((f for _, _, f in OA.fields(schema) if f["fieldKey"] == key), None)


def items(body):
    return {i.get("requirementKey"): i for i in body.get("items") or []}


def patch(client, application_id, fields):
    return client.call("PATCH", f"/onboarding/v1/applications/{application_id}/draft", OA.patch_body({"business": fields}))


def business(client, application_id):
    return (OA.read_app(client, application_id).get("values") or {}).get("business") or {}


def expected_methods():
    rows = E.sql("commercial", "SELECT capability_code FROM t_product_capability WHERE capability_kind='PAYMENT_METHOD' "
                               "AND status='ACTIVE' ORDER BY product_capability_id")
    return [r[0] for r in rows]


def f_form_schema(ctx):
    check, client = OA.check, ctx["client"]
    expected = expected_methods()
    OA.record("F", "commercial ACTIVE 支付方式（只读 SQL）", "INFO", expected)
    for label, query, schema_id, refund_level in (("机构", {"jurisdiction": "AE", "partyType": "ORGANIZATION", "organizationType": "LLC"}, V3_ORG, "EVENTUALLY"),
                                                  ("个人", {"jurisdiction": "AE", "partyType": "INDIVIDUAL"}, V3_IND, "OPTIONAL")):
        status, schema = OA.form_schema(client, **query)
        OA.require("AC-01", f"{label} form-schema", status == 200, f"HTTP {status} {OA.problem(schema)}")
        check("AC-01", f"{label} 为 v3", str(schema.get("schemaId")) == schema_id, f"schemaId={schema.get('schemaId')} v{schema.get('versionNo')}")
        found = {k: field(schema, k) for k in NEW_FIELDS}
        if not check("AC-01", f"{label} 四个新字段", all(found.values()), {k: (f or {}).get("widget") for k, f in found.items()}):
            continue
        methods, refund, kind, days = (found[k] for k in NEW_FIELDS)
        check("AC-01", f"{label} 控件 / 目录 / 级别", methods["widget"] == "MULTI_SELECT" and methods["catalogCode"] == "COMMERCIAL_PAYMENT_METHOD"
              and refund["requirementLevel"] == refund_level and kind["catalogCode"] == "FULFILLMENT_TYPE"
              and days.get("visibleWhen") == "business.fulfillmentType=PRE_ORDER",
              {"methods": methods["widget"], "refund": refund["requirementLevel"], "days": days.get("visibleWhen")})
        catalogs = schema.get("catalogs") or {}
        codes = [i.get("code") for i in catalogs.get("COMMERCIAL_PAYMENT_METHOD") or []]
        check("AC-02", f"{label} 支付方式选项 = commercial ACTIVE（顺序一致）", codes == expected, codes)
        check("AC-01", f"{label} 交付方式目录 4 项", [i.get("code") for i in catalogs.get("FULFILLMENT_TYPE") or []]
              == ["INSTANT", "PRE_ORDER", "SUBSCRIPTION", "SERVICE"], catalogs.get("FULFILLMENT_TYPE"))
    ctx["methods"] = expected


def o_organization(ctx):
    check, client = OA.check, ctx["client"]
    status, created = OA.create_application(client, "AC-01", "RQ12", "AE", "ORGANIZATION", "LLC")
    OA.require("AC-01", "创建 AE 机构草稿", status in (200, 201), created)
    app_id = created["applicationId"]
    ctx["ids"]["organizationApplication"] = app_id
    status, reply = patch(client, app_id, {"expectedPaymentMethods": " card , apple_pay,CARD", "refundPolicyUrl": REFUND,
                                           "fulfillmentType": "PRE_ORDER", "fulfillmentDays": "007"})
    values = business(client, app_id)
    check("AC-02", "CODE_LIST 规范化与天数去前导零", status == 200 and values.get("expectedPaymentMethods") == "APPLE_PAY,CARD"
          and values.get("fulfillmentDays") == "7", f"HTTP {status} {values}")
    status, reply = patch(client, app_id, {"fulfillmentType": "INSTANT"})
    values = business(client, app_id)
    check("AC-01", "改 INSTANT 清空天数", status == 200 and values.get("fulfillmentDays") is None, values)
    status, reply = patch(client, app_id, {"refundPolicyUrl": "http://shop.example.test/refunds"})
    check("AC-03", "http 退款政策 422", status == 422 and any(e.get("field") == "business.refundPolicyUrl" for e in reply.get("errors") or []),
          f"HTTP {status} {OA.problem(reply)}")
    status, reply = patch(client, app_id, {"expectedPaymentMethods": "CARD,apple-pay"})
    check("AC-02", "非法支付方式代码 422", status == 422, f"HTTP {status} {OA.problem(reply)}")
    for days in ("0", "400"):
        status, _ = patch(client, app_id, {"fulfillmentType": "PRE_ORDER", "fulfillmentDays": days})
        _, body = OA.requirements(client, app_id)
        item = items(body).get("business.fulfillmentDays") or {}
        check("AC-01", f"天数 {days}：清单 FIELD_FORMAT_INVALID", status == 200 and item.get("errorCode") == "FIELD_FORMAT_INVALID", item)
    status, _ = patch(client, app_id, {"fulfillmentType": "INSTANT", "refundPolicyUrl": None, "expectedPaymentMethods": ctx["methods"][0]})
    _, body = OA.requirements(client, app_id)
    check("AC-01", "机构缺退款政策 EVENTUALLY_DUE；支付方式 / 交付方式 SATISFIED",
          (items(body).get("business.refundPolicyUrl") or {}).get("status") == "EVENTUALLY_DUE"
          and all((items(body).get(k) or {}).get("status") == "SATISFIED" for k in ("business.expectedPaymentMethods", "business.fulfillmentType")),
          {k: (items(body).get(k) or {}).get("status") for k in NEW_FIELDS})
    withdraw(ctx, app_id, "机构草稿")


def i_individual(ctx):
    check, client = OA.check, ctx["client"]
    status, created = OA.create_application(client, "AC-01", "RQ12", "AE", "INDIVIDUAL")
    OA.require("AC-01", "创建 AE 个人草稿", status in (200, 201), created)
    app_id = created["applicationId"]
    ctx["ids"]["individualApplication"] = app_id
    _, body = OA.requirements(client, app_id)
    check("AC-01", "个人缺退款政策无清单项；支付方式 / 交付方式 CURRENTLY_DUE", "business.refundPolicyUrl" not in items(body)
          and all((items(body).get(k) or {}).get("status") == "CURRENTLY_DUE" for k in ("business.expectedPaymentMethods", "business.fulfillmentType")),
          {k: (items(body).get(k) or {}).get("status") for k in NEW_FIELDS})
    withdraw(ctx, app_id, "个人草稿")


def withdraw(ctx, app_id, label):
    status, reply = ctx["client"].call("POST", f"/onboarding/v1/applications/{app_id}/withdrawal")
    OA.check("CLEAN", f"撤回{label}", status in (200, 202) and OA.read_app(ctx["client"], app_id).get("status") == "WITHDRAWN",
             f"HTTP {status} {OA.problem(reply) if status >= 300 else ''}")


def r_operations(ctx):
    check, ops = OA.check, ctx["ops"]
    status, page = ops.get(f"{OA.CASE_ADMIN}/cases?page=1&size=50")
    legacy = None
    for item in page.get("items") or []:
        status, detail = ops.get(f"{OA.CASE_ADMIN}/cases/{item['caseId']}")
        application = detail.get("application") or {}
        if status == 200 and str(application.get("schemaId") or "").startswith("92700000007"):
            legacy = (item["caseId"], application)
            break
    if not legacy:
        OA.skip("AC-04", "在途 v1 案件详情", "前 50 个案件中无 v1 申请")
    else:
        values = (legacy[1].get("values") or {}).get("business") or {}
        ctx["ids"]["legacyCase"] = legacy[0]
        check("AC-04", "在途 v1 案件 application.values.business 四键为空（页面\"未采集\"）",
              all(k in values and values[k] is None for k in NEW_KEYS), f"case={legacy[0]} { {k: values.get(k, 'missing') for k in NEW_KEYS} }")
    status, schemas = ops.get("/onboarding-config-admin/v1/onboarding-schemas?jurisdiction=AE")
    rows = schemas if isinstance(schemas, list) else (schemas.get("items") or [])
    by_id = {str(r.get("schemaId")): r for r in rows}
    check("AC-05", "运营 Schema 查看：v3 ACTIVE、v2 RETIRED、v3 含 MULTI_SELECT",
          (by_id.get(V3_ORG) or {}).get("status") == "ACTIVE" and (by_id.get("9270000000900301") or {}).get("status") == "RETIRED"
          and any(f.get("widgetCode") == "MULTI_SELECT" for f in (by_id.get(V3_ORG) or {}).get("fields") or []),
          f"HTTP {status} v3={(by_id.get(V3_ORG) or {}).get('status')} v2={(by_id.get('9270000000900301') or {}).get('status')}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", choices=("test", "local"), required=True)
    parser.add_argument("--only")
    args = parser.parse_args()
    setup(args.env)
    started = datetime.now(timezone.utc)
    run = started.strftime("%m%d%H%M%S")
    ctx = {"ids": {}}
    try:
        ctx["ops"] = OA.ops_login(E.secrets()["E2E_ADMIN_EMAIL"], E.secrets()["E2E_ADMIN_PASSWORD"])
        ctx["client"] = OA.client_register("RQ12ACC", run)
        STATE.mkdir(parents=True, exist_ok=True)
        os.chmod(STATE, 0o700)
        target = STATE / f"acceptance-{args.env}.json"
        target.write_text(json.dumps({"email": ctx["client"].email, "password": ctx["client"].password,
                                      "accountId": ctx["client"].account_id, "run": run}, indent=1) + "\n")
        os.chmod(target, 0o600)
        ctx["ids"].update(accountId=ctx["client"].account_id, email=ctx["client"].email)
    except OA.Stop as stop:
        OA.record("SETUP", "前置", "FAIL", str(stop))
        return finish(args, started, ctx)
    selected = (args.only or "F,O,I,R").split(",")
    for name, scenario in (("F", f_form_schema), ("O", o_organization), ("I", i_individual), ("R", r_operations)):
        if name not in selected:
            continue
        print(f"\n==== {name} {scenario.__name__}", flush=True)
        try:
            scenario(ctx)
        except OA.Stop as stop:
            OA.record(name, "场景中止", "FAIL", str(stop))
        except Exception as failure:  # noqa: BLE001
            OA.record(name, "场景异常", "FAIL", f"{type(failure).__name__}: {str(failure)[:300]}")
    return finish(args, started, ctx)


def finish(args, started, ctx):
    summary = {v: sum(1 for r in OA.RESULTS if r["verdict"] == v) for v in ("PASS", "FAIL", "SKIP", "INFO")}
    out = HERE / f"result-{args.env}-{started.strftime('%Y%m%dT%H%M%SZ')}.json"
    out.write_text(json.dumps({"env": args.env, "startedAt": started.isoformat(), "finishedAt": datetime.now(timezone.utc).isoformat(),
                               "summary": summary, "ids": ctx.get("ids"), "results": OA.RESULTS}, ensure_ascii=False, indent=1, default=str) + "\n")
    print(f"\n==== 汇总 {summary} → {out}")
    return 1 if summary["FAIL"] else 0


if __name__ == "__main__":
    sys.exit(main())
