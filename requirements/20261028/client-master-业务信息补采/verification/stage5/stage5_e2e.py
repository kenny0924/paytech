#!/usr/bin/env python3
"""REQ-12 业务信息补采 · 阶段 5 本机联调（API 级，经真实外部入口）。

链路：Client 门户 https://127.0.0.1:18443/api/client/...（Nginx → Gateway → client-console-bff → client-master → commercial）
      运营控制台 https://127.0.0.1:18444/api/operations/...（Nginx → Gateway → operations-console-bff → client-master / compliance）。
复用切片 1 验收脚本 onboarding_acceptance.py（注册、草稿取值、上传、材料、运营审核步骤）与 V2 e2e 公共件 e2e_http.py（E2E_TARGET=local）。

场景：
  F  form-schema（无申请）：AE 机构 / AE 个人 v3 的四个新字段、要求级别、天数可见条件、支付方式目录 = 本机 commercial ACTIVE 支付方式、交付方式目录
  O  机构申请 A：CODE_LIST 规范化、PRE_ORDER + 天数、改 INSTANT 清空天数、http 拒绝、天数 0 / 400 清单格式不符、退款政策 EVENTUALLY；填满提交
  I  个人申请 B：退款政策 OPTIONAL 无清单项；填满（PRE_ORDER 14 天 + https 退款政策）
  U  commercial 不可用（停本机 commercial，用后按原 argv / 环境恢复）：form-schema / 需求清单 / 提交 503 ONBOARDING_DEPENDENCY_UNAVAILABLE、
     申请保持 DRAFT；恢复后 B 提交成功
  L  在途旧版本申请 C：新建后仅把该测试申请的 schema_id 改指 v2（本机测试数据），保存草稿、清单无新字段、可提交，不依赖 commercial
  R  运营：案件详情 application.values.business 三项；Client 详情三项；在途 v1 申请的三项为空
  P  批准 A（四眼：运营 A 推荐、复核人 B 批准）→ t_party_profile.content_hash 等于按四键复算、去掉四键后不等
用法：python3 -I stage5_e2e.py [--only F,O,I,U,L,R,P]；结果写 stage5-e2e-result.json；退出码 0 表示无 FAIL。
不打印口令与令牌；测试账号凭据写 ~/Code-MoR-backups/rq12-stage5/accounts.json（0600），供浏览器走查使用。
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
WORKTREE = HERE.parents[4]
ACCEPTANCE = WORKTREE / "requirements/20261027/client-master-全球合规入驻与资格切片1/assets/acceptance"
V2_E2E = WORKTREE / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
STATE = Path.home() / "Code-MoR-backups/rq12-stage5"
SCRATCH = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/3e08b044-fee7-45d0-ba22-072a21cafc69/scratchpad")
os.environ["E2E_TARGET"] = "local"
os.environ.setdefault("ONB_STATE", str(Path.home() / "Code-MoR-backups/onboarding-slice1-acceptance"))
sys.path.insert(0, str(V2_E2E))
sys.path.insert(0, str(ACCEPTANCE))
import e2e_http as E  # noqa: E402
import acceptance_flow as V2  # noqa: E402
import onboarding_acceptance as OA  # noqa: E402

OA.E, OA.V2 = E, V2
check, require, record, skip, problem = OA.check, OA.require, OA.record, OA.skip, OA.problem
IDS = OA.IDS

V3_ORG, V3_IND, V2_IND = "9281200000900301", "9281200000900303", 9270000000900303
NEW_KEYS = ("expectedPaymentMethods", "refundPolicyUrl", "fulfillmentType", "fulfillmentDays")
NEW_FIELDS = tuple("business." + k for k in NEW_KEYS)
REFUND = "https://shop.example.test/refunds"
COMMERCIAL_READY = "http://127.0.0.1:19108/actuator/health/readiness"


def sql(statement):
    return E.sql("client-master", statement)


def field(schema, key):
    return next((f for _, _, f in OA.fields(schema) if f["fieldKey"] == key), None)


def items(body):
    return {i.get("requirementKey"): i for i in body.get("items") or []}


def business(client, application_id):
    return (OA.read_app(client, application_id).get("values") or {}).get("business") or {}


def patch(client, application_id, fields_):
    return client.call("PATCH", f"/onboarding/v1/applications/{application_id}/draft",
                       OA.patch_body({"business": fields_}))


def declare(client, ac, application_id, schema):
    """以 form-schema 下发的当前文档版本签署全部声明。"""
    for declaration in schema.get("declarations") or []:
        body = {"declarationType": declaration["declarationType"], "declaredValue": "TRUE"}
        if declaration.get("documentVersion"):
            body["termsVersion"] = declaration["documentVersion"]
        status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/declarations", body)
        require(ac, f"声明 {declaration['declarationType']}", status == 200 and reply.get("declarationId"),
                f"HTTP {status} {problem(reply)}")


def without_new(values):
    """主主体取值去掉本需求的四个字段（另行按场景填写）。"""
    values.get("business", {}).pop("fulfillmentDays", None)
    for key in NEW_KEYS:
        values.get("business", {}).pop(key, None)
    return values


def commercial_codes():
    status, text = E.private_call("commercial", "GET", "/products/v1/capabilities?status=ACTIVE",
                                  headers={"X-Caller-Service": "client-master-service"})
    try:
        body = json.loads(text) if status == 200 else {}
    except ValueError:
        body = {}
    return [c["capabilityCode"] for c in body.get("capabilities") or []
            if c.get("capabilityKind") == "PAYMENT_METHOD" and c.get("status") == "ACTIVE"], status


# ------------------------------------------------------------------------------------------------------------ 场景

def f_form_schema(ctx):
    client = ctx["clientA"]
    expected, status = commercial_codes()
    record("F", "本机 commercial ACTIVE 支付方式", "INFO", f"HTTP {status} {expected}")
    status, org = OA.form_schema(client, jurisdiction="AE", partyType="ORGANIZATION", organizationType="LLC")
    require("AC-01", "AE 机构 form-schema", status == 200, f"HTTP {status} {problem(org)}")
    status, ind = OA.form_schema(client, jurisdiction="AE", partyType="INDIVIDUAL")
    require("AC-01", "AE 个人 form-schema", status == 200, f"HTTP {status} {problem(ind)}")
    check("AC-01", "v3 Schema", str(org.get("schemaId")) == V3_ORG and str(ind.get("schemaId")) == V3_IND,
          f"org={org.get('schemaId')} v{org.get('versionNo')} ind={ind.get('schemaId')} v{ind.get('versionNo')}")
    for label, schema, refund_level in (("机构", org, "EVENTUALLY"), ("个人", ind, "OPTIONAL")):
        found = {k: field(schema, k) for k in NEW_FIELDS}
        check("AC-01", f"{label} BUSINESS 分区四个新字段", all(found.values()),
              {k: (f or {}).get("widget") for k, f in found.items()})
        if not all(found.values()):
            continue
        methods, refund, kind, days = (found[k] for k in NEW_FIELDS)
        check("AC-01", f"{label} 期望支付方式 MULTI_SELECT / COMMERCIAL_PAYMENT_METHOD / REQUIRED",
              methods["widget"] == "MULTI_SELECT" and methods["catalogCode"] == "COMMERCIAL_PAYMENT_METHOD"
              and methods["requirementLevel"] == "REQUIRED", methods)
        check("AC-01", f"{label} 退款政策 {refund_level}、https 正则", refund["requirementLevel"] == refund_level
              and (refund.get("validation") or {}).get("pattern") == "^https://\\S+$", refund)
        check("AC-01", f"{label} 交付方式 SELECT / FULFILLMENT_TYPE / REQUIRED；天数仅 PRE_ORDER 可见",
              kind["catalogCode"] == "FULFILLMENT_TYPE" and kind["requirementLevel"] == "REQUIRED"
              and days.get("visibleWhen") == "business.fulfillmentType=PRE_ORDER", {"type": kind, "days": days})
        catalogs = schema.get("catalogs") or {}
        codes = [i.get("code") for i in catalogs.get("COMMERCIAL_PAYMENT_METHOD") or []]
        labels = [i.get("labelKey") for i in catalogs.get("COMMERCIAL_PAYMENT_METHOD") or []]
        check("AC-02", f"{label} 支付方式选项 = 本机 commercial ACTIVE 支付方式（顺序一致）、文案键 paymentMethod.<代码>",
              codes == expected and labels == [f"onboardingSchema.paymentMethod.{c}" for c in codes], f"{codes} {labels}")
        check("AC-01", f"{label} 交付方式目录 4 项",
              [i.get("code") for i in catalogs.get("FULFILLMENT_TYPE") or []] == ["INSTANT", "PRE_ORDER", "SUBSCRIPTION", "SERVICE"],
              catalogs.get("FULFILLMENT_TYPE"))
    ctx["schemaOrg"], ctx["schemaInd"], ctx["paymentMethods"] = org, ind, expected


def o_organization(ctx):
    client, run = ctx["clientA"], ctx["run"]
    status, created = OA.create_application(client, "AC-01", "A", "AE", "ORGANIZATION", "LLC")
    require("AC-01", "A 创建机构申请", status in (200, 201), created)
    app_id = created["applicationId"]
    IDS["appA"], IDS["clientIdA"] = app_id, created.get("clientId")
    _, schema = OA.form_schema(client, applicationId=app_id)
    check("AC-01", "A 冻结 v3", str(schema.get("schemaId")) == V3_ORG, schema.get("schemaId"))

    status, reply = patch(client, app_id, {"expectedPaymentMethods": " card , apple_pay,CARD", "refundPolicyUrl": REFUND,
                                           "fulfillmentType": "PRE_ORDER", "fulfillmentDays": "007"})
    values = business(client, app_id)
    check("AC-02", "草稿 CODE_LIST 规范化与天数去前导零",
          status == 200 and values.get("expectedPaymentMethods") == "APPLE_PAY,CARD" and values.get("fulfillmentDays") == "7",
          f"HTTP {status} {problem(reply) if status != 200 else ''} values={ {k: values.get(k) for k in NEW_KEYS} }")
    status, reply = patch(client, app_id, {"fulfillmentType": "INSTANT"})
    values = business(client, app_id)
    check("AC-01", "改 INSTANT 同一写入清空天数", status == 200 and values.get("fulfillmentType") == "INSTANT"
          and values.get("fulfillmentDays") is None, f"HTTP {status} values={ {k: values.get(k) for k in NEW_KEYS} }")
    status, reply = patch(client, app_id, {"fulfillmentDays": "5"})
    check("AC-01", "非预售单独填天数 → 422 INVALID_DRAFT（business.fulfillmentDays）",
          status == 422 and reply.get("code") == "INVALID_DRAFT"
          and any(e.get("field") == "business.fulfillmentDays" for e in reply.get("errors") or []), f"HTTP {status} {problem(reply)}")
    status, reply = patch(client, app_id, {"refundPolicyUrl": "http://shop.example.test/refunds"})
    check("AC-03", "http 退款政策 → 422 INVALID_DRAFT（须为 https 网址）",
          status == 422 and any(e.get("field") == "business.refundPolicyUrl" and "https" in (e.get("message") or "")
                                for e in reply.get("errors") or []), f"HTTP {status} {problem(reply)}")
    status, reply = patch(client, app_id, {"expectedPaymentMethods": "CARD,apple-pay"})
    check("AC-02", "非法支付方式代码 → 422", status == 422, f"HTTP {status} {problem(reply)}")
    for days in ("0", "400"):
        status, reply = patch(client, app_id, {"fulfillmentType": "PRE_ORDER", "fulfillmentDays": days})
        _, body = OA.requirements(client, app_id)
        item = items(body).get("business.fulfillmentDays") or {}
        check("AC-01", f"天数 {days}：草稿可存，需求清单 FIELD_FORMAT_INVALID / CURRENTLY_DUE",
              status == 200 and item.get("errorCode") == "FIELD_FORMAT_INVALID" and item.get("status") == "CURRENTLY_DUE",
              f"HTTP {status} item={item}")
    status, reply = patch(client, app_id, {"fulfillmentType": "PRE_ORDER", "fulfillmentDays": None})
    _, body = OA.requirements(client, app_id)
    check("AC-01", "PRE_ORDER 缺天数 → CURRENTLY_DUE FIELD_MISSING",
          (items(body).get("business.fulfillmentDays") or {}).get("errorCode") == "FIELD_MISSING", items(body).get("business.fulfillmentDays"))
    status, reply = patch(client, app_id, {"fulfillmentType": "INSTANT", "refundPolicyUrl": None,
                                           "expectedPaymentMethods": ",".join(ctx["paymentMethods"][:2])})
    _, body = OA.requirements(client, app_id)
    refund = items(body).get("business.refundPolicyUrl") or {}
    check("AC-01", "机构缺退款政策 → EVENTUALLY_DUE（不阻断提交）", refund.get("status") == "EVENTUALLY_DUE", refund)
    check("AC-02", "支付方式与交付方式 SATISFIED", all((items(body).get(k) or {}).get("status") == "SATISFIED"
                                                    for k in ("business.expectedPaymentMethods", "business.fulfillmentType")),
          {k: items(body).get(k) for k in ("business.expectedPaymentMethods", "business.fulfillmentType")})

    org_ctx = OA.org_ctx(run, IDS["clientAEmail"], f"Bizinfo Trading {run} LLC")
    app = OA.read_app(client, app_id)
    OA.save_draft(client, "AC-01", app_id, without_new(OA.subject_values(schema, "PRIMARY", org_ctx)), "A 主主体")
    persons = []
    for spec in OA.org_people(run):
        person_ctx = {**org_ctx, "given": spec["given"], "family": spec["family"], "identifierValue": spec["passport"],
                      "identifierType": "PASSPORT", "birthDate": "1982-03-04"}
        first, *more = spec["relations"]
        party = OA.add_person(client, "AC-01", app_id, OA.subject_values(schema, "RELATED_PERSON", person_ctx), first, f"A {spec['label']}")
        for relation in more:
            OA.add_relation(client, "AC-01", app_id, party, relation, f"A {spec['label']}")
        persons.append({"partyId": party, "label": spec["label"], "roles": [r["relationType"] for r in spec["relations"]]})
    for party, material, owner in OA.material_targets(schema, app["subjectPartyId"], persons):
        OA.attach_document(client, "AC-01", app_id, party, material, f"A-{owner}-{material['requirementCode']}")
    declare(client, "AC-01", app_id, schema)
    _, body = OA.requirements(client, app_id)
    check("AC-01", "A 填满：ready，退款政策仍 EVENTUALLY_DUE", body.get("ready") is True
          and (items(body).get("business.refundPolicyUrl") or {}).get("status") == "EVENTUALLY_DUE",
          f"ready={body.get('ready')} open={OA.open_items(body)[:8]}")
    IDS["submissionA"] = OA.submit(client, "AC-01", app_id, "A")
    IDS["appAStatus"] = "UNDER_REVIEW"


def fill_individual(ctx, client, label, schema, app_id, business_fields):
    run = ctx["run"]
    ind_ctx = {"run": run, "jurisdiction": "AE", "organizationType": None, "legalName": None, "email": IDS[f"client{label}Email"],
               "given": "Layla", "family": "Bizinfo" + run[-4:]}
    app = OA.read_app(client, app_id)
    values = without_new(OA.subject_values(schema, "PRIMARY", ind_ctx))
    values.setdefault("business", {}).update(business_fields)
    OA.save_draft(client, "AC-01", app_id, values, f"{label} 主主体")
    for party, material, owner in OA.material_targets(schema, app["subjectPartyId"], []):
        OA.attach_document(client, "AC-01", app_id, party, material, f"{label}-{owner}-{material['requirementCode']}")
    declare(client, "AC-01", app_id, schema)


def i_individual(ctx):
    client = ctx["clientB"]
    status, created = OA.create_application(client, "AC-01", "B", "AE", "INDIVIDUAL")
    require("AC-01", "B 创建个人申请", status in (200, 201), created)
    app_id = created["applicationId"]
    IDS["appB"], IDS["clientIdB"] = app_id, created.get("clientId")
    _, schema = OA.form_schema(client, applicationId=app_id)
    _, body = OA.requirements(client, app_id)
    check("AC-01", "个人缺退款政策：无清单项（OPTIONAL）；支付方式 / 交付方式 CURRENTLY_DUE",
          "business.refundPolicyUrl" not in items(body)
          and all((items(body).get(k) or {}).get("status") == "CURRENTLY_DUE" for k in ("business.expectedPaymentMethods", "business.fulfillmentType")),
          {k: (items(body).get(k) or {}).get("status") for k in NEW_FIELDS})
    fill_individual(ctx, client, "B", schema, app_id, {"expectedPaymentMethods": ctx["paymentMethods"][-1], "refundPolicyUrl": REFUND,
                                                       "fulfillmentType": "PRE_ORDER", "fulfillmentDays": "14"})
    _, body = OA.requirements(client, app_id)
    check("AC-01", "B 填满后 ready", body.get("ready") is True, f"open={OA.open_items(body)[:8]}")


def u_commercial_unavailable(ctx):
    client, app_id = ctx["clientB"], IDS["appB"]
    pid = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/commercial.pid").read_text().strip()
    swap = [sys.executable, "-I", str(HERE / "swap.py")]
    subprocess.run(swap + ["capture", "commercial", pid], check=True)
    os.kill(int(pid), 15)
    stopped = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    for _ in range(40):
        try:
            os.kill(int(pid), 0)
            time.sleep(1)
        except OSError:
            break
    record("U", "停本机 commercial", "INFO", f"pid {pid} at {stopped}")
    try:
        status, body = OA.form_schema(client, jurisdiction="AE", partyType="INDIVIDUAL")
        check("U", "commercial 不可用：form-schema 503（门户 SERVICE_UNAVAILABLE；client-master ONBOARDING_DEPENDENCY_UNAVAILABLE）",
              unavailable(status, body), f"HTTP {status} {problem(body)}")
        status, body = OA.requirements(client, app_id)
        check("U", "commercial 不可用：需求清单 503（同上）", unavailable(status, body), f"HTTP {status} {problem(body)}")
        revision = OA.read_app(client, app_id).get("draftRevision")
        status, body = client.call("POST", f"/onboarding/v1/applications/{app_id}/submissions")
        app = OA.read_app(client, app_id)
        check("U", "commercial 不可用：提交 503（同上），申请仍 DRAFT、资料版本不变",
              unavailable(status, body) and app.get("status") == "DRAFT"
              and app.get("draftRevision") == revision, f"HTTP {status} {problem(body)} status={app.get('status')} revision={revision}")
        status, body = patch(client, app_id, {"refundPolicyUrl": REFUND + "?v=2"})
        check("U", "commercial 不可用时草稿保存不受影响", status == 200, f"HTTP {status} {problem(body)}")
    finally:
        subprocess.run(swap + ["restart", "commercial"], check=True)
        record("U", "恢复本机 commercial", "INFO", f"pid {Path('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/commercial.pid').read_text().strip()}")
    codes, status = commercial_codes()
    check("U", "commercial 恢复后目录照常", codes == ctx["paymentMethods"], f"HTTP {status} {codes}")
    declare(client, "U", app_id, OA.form_schema(client, applicationId=app_id)[1])
    deadline = time.time() + 60
    while time.time() < deadline:
        status, _ = OA.form_schema(client, applicationId=app_id)
        if status == 200:
            break
        time.sleep(3)
    IDS["submissionB"] = OA.submit(client, "U", app_id, "B（commercial 恢复后）")
    IDS["appBStatus"] = "UNDER_REVIEW"


def unavailable(status, body):
    """门户 503；client-console-bff 把上游 503 统一为 SERVICE_UNAVAILABLE（既有行为），client-master 日志同一请求为 ONBOARDING_DEPENDENCY_UNAVAILABLE。"""
    request_id = body.get("requestId")
    log = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/logs/client-master.log").read_text(errors="ignore")
    owner = any(request_id and request_id in line and "errorCode=ONBOARDING_DEPENDENCY_UNAVAILABLE" in line for line in log.splitlines()[-4000:])
    return status == 503 and body.get("code") == "SERVICE_UNAVAILABLE" and owner


def l_previous_version(ctx):
    client = ctx["clientC"]
    status, created = OA.create_application(client, "AC-04", "C", "AE", "INDIVIDUAL")
    require("AC-04", "C 创建个人申请", status in (200, 201), created)
    app_id = created["applicationId"]
    IDS["appC"], IDS["clientIdC"] = app_id, created.get("clientId")
    sql_text = f"UPDATE t_client_application SET schema_id={V2_IND} WHERE application_id={int(app_id)} AND status='DRAFT'"
    root_sql(sql_text)
    record("AC-04", "本机测试数据：C 改冻结 v2（模拟在途）", "INFO", sql_text)
    calls_before = commercial_calls()
    status, schema = OA.form_schema(client, applicationId=app_id)
    check("AC-04", "C form-schema 为 v2：无四个新字段、无 commercial 目录",
          status == 200 and str(schema.get("schemaId")) == str(V2_IND) and not any(field(schema, k) for k in NEW_FIELDS)
          and "COMMERCIAL_PAYMENT_METHOD" not in (schema.get("catalogs") or {}), f"HTTP {status} schemaId={schema.get('schemaId')}")
    fill_individual(ctx, client, "C", schema, app_id, {})
    _, body = OA.requirements(client, app_id)
    check("AC-04", "C 需求清单无新字段且 ready", body.get("ready") is True and not set(items(body)) & set(NEW_FIELDS),
          f"ready={body.get('ready')} open={OA.open_items(body)[:8]}")
    IDS["submissionC"] = OA.submit(client, "AC-04", app_id, "C（v2 在途）")
    IDS["appCStatus"] = "UNDER_REVIEW"
    calls_after = commercial_calls()
    # 本机 commercial 日志不记录请求路径（计数恒为 0），不能证明零调用：只记 INFO，零调用由 auto 测试
    # BusinessInfoCollectionTest.test_previousVersionApplicationUnaffected 断言。
    record("AC-04", "C 不调用 commercial（本机日志不记录请求路径，不作证据）", "INFO", f"before={calls_before} after={calls_after}")


def root_sql(statement):
    subprocess.run([str(SCRATCH / "myroot.sh"), "mor_client_master_v2e2e", "-e", statement], check=True)


def commercial_calls():
    log = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/logs/commercial.log")
    return log.read_text(errors="ignore").count("/products/v1/capabilities") if log.exists() else -1


def r_operations(ctx):
    ops = ctx["opsA"]
    case = OA.find_case(ops, IDS["appA"])
    require("AC-05", "A 案件出现在工作台", case, IDS["appA"])
    IDS["caseA"] = case["caseId"]
    status, detail = ops.get(f"{OA.CASE_ADMIN}/cases/{case['caseId']}")
    values = ((detail.get("application") or {}).get("values") or {}).get("business") or {}
    check("AC-05", "运营案件详情 application.values.business 三项",
          status == 200 and values.get("expectedPaymentMethods") == ",".join(sorted(ctx["paymentMethods"][:2]))
          and values.get("fulfillmentType") == "INSTANT" and values.get("refundPolicyUrl") is None and values.get("fulfillmentDays") is None,
          f"HTTP {status} { {k: values.get(k) for k in NEW_KEYS} }")
    case_b = OA.find_case(ops, IDS["appB"])
    if case_b:
        IDS["caseB"] = case_b["caseId"]
        _, detail_b = ops.get(f"{OA.CASE_ADMIN}/cases/{case_b['caseId']}")
        values_b = ((detail_b.get("application") or {}).get("values") or {}).get("business") or {}
        check("AC-05", "B 案件详情：PRE_ORDER 14 天、退款政策链接", values_b.get("fulfillmentType") == "PRE_ORDER"
              and values_b.get("fulfillmentDays") == "14" and (values_b.get("refundPolicyUrl") or "").startswith(REFUND),
              {k: values_b.get(k) for k in NEW_KEYS})
    status, page = ops.get(f"/client-admin/v1/clients/{IDS['clientIdA']}")
    admin_values = (OA.dig(page, "client", "values") or {}).get("business") or {}
    check("AC-05", "运营 Client 详情 values.business 三项", status == 200 and admin_values.get("fulfillmentType") == "INSTANT",
          f"HTTP {status} { {k: admin_values.get(k) for k in NEW_KEYS} }")
    legacy = sql("SELECT a.application_id, a.due_diligence_case_id FROM t_client_application a WHERE a.status='UNDER_REVIEW' "
                 "AND a.schema_id BETWEEN 9270000000700001 AND 9270000000700008 AND a.due_diligence_case_id IS NOT NULL "
                 "ORDER BY a.update_time DESC LIMIT 1")
    if legacy:
        legacy_app, legacy_case = legacy[0]
        IDS["legacyV1App"], IDS["legacyV1Case"] = str(legacy_app), str(legacy_case)
        status, detail = ops.get(f"{OA.CASE_ADMIN}/cases/{legacy_case}")
        values = ((detail.get("application") or {}).get("values") or {}).get("business") or {}
        check("AC-04", "在途 v1 案件详情：三项为空（页面显示“未采集”）",
              status == 200 and all(values.get(k) is None for k in NEW_KEYS), f"HTTP {status} case={legacy_case} { {k: values.get(k) for k in NEW_KEYS} }")
    else:
        skip("AC-04", "在途 v1 案件详情", "无 UNDER_REVIEW 的 v1 案件")


def p_approval(ctx):
    ops, reviewer, case_id = ctx["opsA"], ctx.get("opsB"), IDS["caseA"]
    if not reviewer:
        raise OA.Stop("no reviewer")
    OA.wait_screening(ops, case_id)
    OA.accept_and_pass(ops, "AC-06", case_id, None, "A")
    classify(ops, case_id)
    OA.rate(ops, case_id, None, "A")
    status, reply = OA.command(ops, "POST", f"/cases/{case_id}/recommendation",
                               {"recommendation": "APPROVE", "note": "rq12 stage5", "onBehalfOfPartnerId": None})
    check("AC-06", "运营 A 推荐 APPROVE", status == 200, f"HTTP {status} {problem(reply)}")
    blockers = OA.review_of(OA.case_detail(reviewer, case_id)).get("approvalBlockers") or []
    status, reply = OA.command(reviewer, "POST", f"/cases/{case_id}/approval",
                               {"reasonCode": "DOCUMENTS_VERIFIED", "reason": "rq12 stage5 four-eyes", "onBehalfOfPartnerId": None})
    require("AC-06", "复核人 B 批准", status == 200, f"HTTP {status} {problem(reply)} blockers={blockers}")
    app = OA.wait_app(ctx["clientA"], IDS["appA"], ("APPROVED",), 90)
    require("AC-06", "A 申请 APPROVED", app.get("status") == "APPROVED", app.get("status"))
    IDS["appAStatus"] = "APPROVED"
    deadline, stored = time.time() + 30, None
    while time.time() < deadline and not stored:
        rows = sql(f"SELECT f.content_hash, f.profile_id, p.party_id, a.jurisdiction_code FROM t_client_application a "
                   f"JOIN t_client c ON c.client_id=a.client_id JOIN t_party p ON p.party_id=c.party_id "
                   f"JOIN t_party_profile f ON f.party_id=p.party_id AND f.current_slot=1 WHERE a.application_id={int(IDS['appA'])}")
        stored = rows[0] if rows and rows[0][0] not in (None, "NULL", "") else None
        time.sleep(2)
    require("AC-06", "主体资料摘要已写入", stored, stored)
    content_hash, profile_id, party_id, jurisdiction = stored
    status, detail = ops.get(f"{OA.CASE_ADMIN}/cases/{case_id}")
    values = dict((detail.get("application") or {}).get("values") or {})
    expected = digest(values, profile_id, party_id, jurisdiction, organization=True)
    trimmed = dict(values)
    trimmed["business"] = {k: v for k, v in values["business"].items() if k not in NEW_KEYS}
    check("AC-06", "content_hash = 含四个新键的资料复算摘要；去掉四键后不等",
          expected == content_hash and digest(trimmed, profile_id, party_id, jurisdiction, organization=True) != content_hash,
          f"stored={content_hash[:16]}… recomputed={expected[:16]}…")


def classify(ops, case_id):
    """行业目录为大类 → 细类：取第一个细类及其默认 MCC，设置并确认分类。"""
    detail = OA.case_detail(ops, case_id)
    child = next(c for category in detail.get("industries") or [] for c in category.get("children") or [])
    status, reply = OA.command(ops, "PUT", f"/cases/{case_id}/classifications",
                               {"items": [{"industryCode": child["industryCode"], "mccCode": child.get("defaultMccCode") or "5999", "primary": True}]})
    check("AC-06", "设置分类", status == 200, f"HTTP {status} industry={child['industryCode']} {problem(reply) if status != 200 else ''}")
    status, reply = OA.command(ops, "POST", f"/cases/{case_id}/classification-confirmation", None)
    check("AC-06", "确认分类", status == 200, f"HTTP {status} {problem(reply)}")


INDIVIDUAL_KEYS = ("givenName", "middleName", "familyName", "birthDate", "nationalityCountry", "residenceCountry", "birthPlace",
                   "birthCountry", "gender", "sourceOfFundsCode", "pepSelfDeclared")


def digest(values, profile_id, party_id, jurisdiction, organization):
    """与 client-master 批准摘要同口径：RFC 8785（本脚本的键全为字符串或 null，排序键 + 紧凑 JSON 即规范形式）+ SHA-256。"""
    profile = dict(values)
    profile.pop("contact", None)
    attributes = profile.pop("attributes", None) or {}
    if organization:
        profile.setdefault("individual", {k: None for k in INDIVIDUAL_KEYS})
    content = {"profileId": str(profile_id), "partyId": str(party_id), "jurisdictionCode": jurisdiction, "values": profile,
               "attributes": attributes}
    canonical = json.dumps(content, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    return hashlib.sha256(canonical).hexdigest()


def save_accounts(ctx):
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE, 0o700)
    target = STATE / "accounts.json"
    data = {label: {"email": ctx[f"client{label}"].email, "password": ctx[f"client{label}"].password,
                    "accountId": ctx[f"client{label}"].account_id} for label in ("A", "B", "C") if f"client{label}" in ctx}
    target.write_text(json.dumps(data, indent=1) + "\n")
    os.chmod(target, 0o600)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only")
    args = parser.parse_args()
    started = datetime.now(timezone.utc)
    run = started.strftime("%m%d%H%M%S")
    selected = (args.only or "F,O,I,U,L,R,P").split(",")
    ctx = {"run": run, "args": argparse.Namespace(env="local", create_reviewer=True)}
    try:
        ctx["opsA"] = OA.ops_login(E.secrets()["E2E_ADMIN_EMAIL"], E.secrets()["E2E_ADMIN_PASSWORD"])
        ctx["opsB"] = OA.reviewer(ctx)
        for label in ("A", "B", "C"):
            ctx[f"client{label}"] = OA.client_register(f"RQ12{label}", run)
            ctx[f"client{label}"].email = IDS[f"clientRQ12{label}Email"]
            IDS[f"client{label}Email"] = ctx[f"client{label}"].email
            IDS[f"client{label}AccountId"] = ctx[f"client{label}"].account_id
        save_accounts(ctx)
    except OA.Stop as stop:
        record("SETUP", "前置", "FAIL", str(stop))
        return finish(started, 1)
    plan = [("F", f_form_schema), ("O", o_organization), ("I", i_individual), ("U", u_commercial_unavailable),
            ("L", l_previous_version), ("R", r_operations), ("P", p_approval)]
    for name, scenario in plan:
        if name not in selected:
            continue
        print(f"\n==== {name} {scenario.__name__}", flush=True)
        try:
            scenario(ctx)
        except OA.Stop as stop:
            record(name, "场景中止", "FAIL", str(stop))
        except Exception as failure:  # noqa: BLE001
            record(name, "场景异常", "FAIL", f"{type(failure).__name__}: {str(failure)[:300]}")
    return finish(started, 1 if any(r["verdict"] == "FAIL" for r in OA.RESULTS) else 0)


def finish(started, code):
    summary = {v: sum(1 for r in OA.RESULTS if r["verdict"] == v) for v in ("PASS", "FAIL", "SKIP", "INFO")}
    out = HERE / "stage5-e2e-result.json"
    out.write_text(json.dumps({"startedAt": started.isoformat(), "finishedAt": datetime.now(timezone.utc).isoformat(),
                               "summary": summary, "ids": IDS, "results": OA.RESULTS}, ensure_ascii=False, indent=1, default=str) + "\n")
    print(f"\n==== 汇总 {summary} → {out}")
    return code


if __name__ == "__main__":
    sys.exit(main())
