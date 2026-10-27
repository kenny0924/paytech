#!/usr/bin/env python3
"""全球合规入驻与资格 · 切片 1 · 验收脚本（AC-01—AC-15，API 级，经真实外部入口）。

链路：Client 门户 /api/client/...（Nginx → Gateway → client-console-bff → client-master / compliance / Platform 文件）
      运营控制台 /api/operations/...（Nginx → Gateway → operations-console-bff → client-master / compliance / Platform IAM）。
入口地址、管理员凭据、只读 SQL 账号沿用 V2 本机 / mor-test 全链路公共件
requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/e2e_http.py（E2E_TARGET=local|test）。

场景（每个 AC 打印 PASS / FAIL / SKIP 与证据 ID）：
  S0 运营配置只读（AC-15 API 部分、AC-01 运营侧）与入驻码签发 / 掩码 / 作废（AC-04）
  S1 Client 侧辖区与 form-schema：AE / SA / EG × 机构 / 个人、AE SOLE_ESTABLISHMENT、未知辖区 422（AC-01、AC-14 数据部分）
  SA 账号 A：AE 机构（PO Box 地址、UBO + 董事 / 签字人、证件 Token、材料、声明）→ 提交 → 运营全流程 → 四眼批准（AC-02—AC-08、AC-10、AC-13）
  SB 账号 B：关系人姓名含 TESTHIT → PEP 命中 → 拒绝材料 / 处置命中 / 退回（需求级 errors[] + 期限）→ 重提第 2 轮复用 → 再退回 → 撤回 RETURNED（AC-06、AC-07）
  SC 账号 C：无效 / 作废 / 用尽入驻码 422，平台自营 SA 个人（Luhn 校验位失败）→ 撤回 DRAFT，AE 个人 SOLE_ESTABLISHMENT 填满（AC-02、AC-04、AC-14 / Q6）
  SD 资格手工撤销 → Client SUSPENDED（AC-09）
  SE 日志不含证件原值（AC-03，mor-test 读容器日志；本机需 --log-dir）、两个应用 onboardingSchema 语言包（AC-14 / AC-15 文案）、
     遗留 ACTIVE Client 详情脱敏（迁移兼容）
只读 SQL：client-master / compliance 库扫描全部文本列不含证件原值（AC-03），读决定 / 资格冻结列（AC-08、AC-10）。
Platform 认证挑战铸造（mint_signup_link）是既有 e2e 机制：收不到验证邮件时改写本次注册挑战的 secret_hmac，只作用于本次新账号。

不删除任何数据；测试数据按运行时间戳唯一（邮箱、公司名、证件号）。合成复核人（--create-reviewer）只新增 IAM 角色与账号，
凭据写到 ~/Code-MoR-backups/onboarding-slice1-acceptance（0600），不进仓库、不打印。

用法：python3 -I onboarding_acceptance.py --env local|mor-test [--create-reviewer] [--partner-id <OE_PROVIDER Partner>]
                                          [--db-suffix <本机库后缀>] [--log-dir <本机服务日志目录>] [--only S0,SA,...]
复核人也可由环境变量 ONB_REVIEWER_EMAIL / ONB_REVIEWER_PASSWORD 提供（第二个运营账号，四眼批准用）。
结果：verification/acceptance-<env>-<UTC 时间>.json；退出码 0 表示无 FAIL。
"""
import argparse
import base64
import http.cookiejar
import json
import os
import random
import re
import secrets as pysecrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
WORKTREE = HERE.parents[4]
V2_E2E = WORKTREE / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
STATE = Path(os.environ.get("ONB_STATE", Path.home() / "Code-MoR-backups/onboarding-slice1-acceptance"))

PDF = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
       b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
CODE_FORMAT = re.compile(r"^[A-Z0-9]{4}-[A-Z0-9]{4}$")
CASE_ADMIN = "/client-onboarding-admin/v1"
SCREENING_CHANNEL = "S_SCREENING_MOCK"
BUNDLE_NAMESPACE = "onboarding-schema"  # Platform 命名空间；后端 labelKey onboardingSchema.X 映射为 onboarding-schema.X


def bundle_key(label_key):
    return BUNDLE_NAMESPACE + label_key[len("onboardingSchema"):] if label_key.startswith("onboardingSchema.") else label_key  # B7 / AC-13：provider_code 为 channel-connector 渠道码
COUNTRY = {"AE": "AE", "SA": "SA", "EG": "EG"}
CITY = {"AE": "Dubai", "SA": "Riyadh", "EG": "Cairo"}
TODAY = date.today()

# ---------------------------------------------------------------------------------------------------------------- 结果

RESULTS = []
IDS = {}
SENSITIVE = set()  # 本次提交的个人证件原值（只用于"库 / 日志 / 响应不含原值"核对，不打印）


class Stop(Exception):
    """当前场景遇到不能继续的失败，停止该场景的后续依赖步骤。"""


def record(ac, step, verdict, evidence=""):
    text = evidence if isinstance(evidence, str) else json.dumps(evidence, ensure_ascii=False, default=str)
    text = redact(text)[:700]
    RESULTS.append({"ac": ac, "step": step, "verdict": verdict, "evidence": text})
    print(f"{verdict:4} {ac:6} {step} | {text}", flush=True)


def check(ac, step, ok, evidence=""):
    record(ac, step, "PASS" if ok else "FAIL", evidence)
    return bool(ok)


def require(ac, step, ok, evidence=""):
    if not check(ac, step, ok, evidence):
        raise Stop(f"{ac} {step}")


def skip(ac, step, reason):
    record(ac, step, "SKIP", reason)


def redact(text):
    for value in SENSITIVE:
        text = text.replace(value, "****" + value[-4:])
    return text


def problem(body):
    if not isinstance(body, dict):
        return str(body)[:200]
    out = {k: body.get(k) for k in ("code", "detail", "requestId") if body.get(k) is not None}
    if body.get("errors"):
        out["errors"] = body["errors"][:12]
    return out


# ---------------------------------------------------------------------------------------------------------------- HTTP

class Session:
    """经外部入口的会话：Client 与运营各自的前缀；Cookie Jar 保留 Client Refresh Cookie；令牌只在内存。"""

    def __init__(self, origin, prefix, label):
        self.origin, self.prefix, self.label = origin, prefix, label
        self.token = None
        self.account_id = None
        self.opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=E.TLS),
                                                  urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method, path, body=None, key=True, json_empty=False, auth=True):
        """返回 (HTTP 状态, JSON 或 {})。写命令带 Idempotency-Key；429 按 Retry-After 重试同一幂等键。"""
        headers = {"Origin": self.origin, "Accept": "application/json"}
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        elif json_empty:
            headers["Content-Type"] = "application/json"
            data = b""
        if method != "GET" and key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
        if auth and self.token:
            headers["Authorization"] = "Bearer " + self.token
        for attempt in range(15):
            request = urllib.request.Request(self.origin + self.prefix + path, data=data, headers=headers, method=method)
            try:
                with self.opener.open(request, timeout=60) as response:
                    raw = response.read().decode()
                    return response.status, (json.loads(raw) if raw.strip() else {})
            except urllib.error.HTTPError as failure:
                if failure.code == 429 and attempt < 14:
                    time.sleep(int(failure.headers.get("Retry-After") or 10))
                    continue
                raw = failure.read().decode()
                try:
                    return failure.code, (json.loads(raw) if raw.strip() else {})
                except ValueError:
                    return failure.code, {"raw": raw[:300]}
        return 0, {}

    def get(self, path):
        return self.call("GET", path)


def ops_login(identifier, password):
    session = Session(E.OPERATIONS, "/api/operations", "ops")
    status, body = session.call("POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, key=False)
    if status != 200 or not body.get("accessToken"):
        raise Stop(f"运营登录失败 HTTP {status} {problem(body)}")
    session.token = body["accessToken"]
    session.account_id = (body.get("context") or {}).get("accountId") or (body.get("account") or {}).get("accountId")
    return session


def client_register(label, run):
    """注册 → 邮箱验证（Mailpit；收不到时用既有 e2e 铸造机制）→ 登录；返回已登录会话。"""
    session = Session(E.CLIENT, "/api/client", "client-" + label)
    email = f"onb-{label.lower()}-{run.lower()}@example.test"
    password = "Onb-" + pysecrets.token_urlsafe(14) + "-9a"
    since = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(time.time() - 5))
    status, body = session.call("POST", "/auth/v1/register", {"email": email, "password": password}, key=False, auth=False)
    require("SETUP", f"Client {label} 注册", status == 202 and body.get("accepted") is True, f"HTTP {status} {problem(body)}")
    message_id, challenge, secret = V2.verification_link(email, 10)
    if not challenge:
        challenge, secret = E.mint_signup_link(email, since)
        message_id = "minted"
    require("SETUP", f"Client {label} 验证链接", bool(challenge), f"source={message_id} challengeNo={challenge}")
    status, body = session.call("POST", "/auth/v1/email-verifications", {"challengeNo": challenge, "secret": secret}, key=False, auth=False)
    require("SETUP", f"Client {label} 邮箱验证", status == 200 and body.get("verified") is True, f"HTTP {status} {problem(body)}")
    status, body = session.call("POST", "/auth/v1/login", {"email": email, "password": password}, key=False, auth=False)
    require("SETUP", f"Client {label} 登录", status == 200 and body.get("accessToken"), f"HTTP {status} {problem(body)}")
    session.token = body["accessToken"]
    session.account_id = (body.get("context") or {}).get("accountId")
    session.email, session.password = email, password  # 只供 --provision-client 写 0600 文件，不打印
    IDS[f"client{label}AccountId"] = session.account_id
    IDS[f"client{label}Email"] = email
    return session


# ---------------------------------------------------------------------------------------------------------------- 取值

def rand_digits(n):
    return "".join(random.choice("0123456789") for _ in range(n))


def from_pattern(pattern, prefer=8):
    """按简单正则（字面量、[类]{m,n}）生成一个随机匹配值；只覆盖国家包 DML 中出现的形状。"""
    out = []
    for token, _, low, high in re.findall(r"(\[[^\]]+\]|\\.|.)(\{(\d+)(?:,(\d+))?\})?", pattern):
        count = 1 if not low else (int(low) if not high else max(int(low), min(int(high), prefer)))
        if token.startswith("["):
            body = token[1:-1]
            chars = []
            for a, b in re.findall(r"(.)-(.)", body):
                chars += [chr(c) for c in range(ord(a), ord(b) + 1)]
            if not chars:
                chars = [c for c in body if c != "-"]
            digits = [c for c in chars if c.isdigit()]
            pool = digits or chars
            out.append("".join(random.choice(pool) for _ in range(count)))
        else:
            out.append(token.lstrip("\\") * count)
    return "".join(out)


def luhn_digit(payload):
    total = 0
    for i, ch in enumerate(reversed(payload)):
        d = int(ch)
        if i % 2 == 0:
            d *= 2
            d -= 9 if d > 9 else 0
        total += d
    return str((10 - total % 10) % 10)


def catalog_first(schema, catalog, preferred=None):
    items = (schema.get("catalogs") or {}).get(catalog) or []
    codes = [i.get("code") or i.get("itemCode") for i in items]
    if preferred in codes:
        return preferred
    return codes[0] if codes else None


def fields(schema):
    for section in schema.get("sections") or []:
        for group in section.get("groups") or []:
            for field in group.get("fields") or []:
                yield section.get("section"), group.get("group"), field


SPECIAL_TEXT = {
    "organization.legalName": lambda ctx: ctx["legalName"],
    "organization.tradeName": lambda ctx: ctx["legalName"],
    "individual.givenName": lambda ctx: ctx["given"],
    "individual.familyName": lambda ctx: ctx["family"],
    "individual.birthPlace": lambda ctx: CITY[ctx["jurisdiction"]],
    "business.businessDescription": lambda ctx: "Sells home goods online through its own website (acceptance test data).",
    "business.websiteUrl": lambda ctx: "https://example.test",
    "business.expectedVolume": lambda ctx: "50000.00000000",
    "business.expectedTicket": lambda ctx: "100.00000000",
    "contact.email": lambda ctx: ctx["email"],
    "contact.phone": lambda ctx: "+971501234567",
    "organization.organizationType": lambda ctx: ctx["organizationType"],
    "individual.birthDate": lambda ctx: ctx.get("birthDate", "1985-05-15"),
    "organization.incorporationDate": lambda ctx: "2020-01-15",
    "individual.pepSelfDeclared": lambda ctx: "false",
}
PREFERRED_CODE = {"business.currency": "USD", "organization.registrationAuthorityCode": "AE_DED_DUBAI",
                  "attributes.AE_LICENSE_AUTHORITY": "AE_DED_DUBAI"}


def field_value(schema, field, ctx):
    key, widget = field["fieldKey"], field.get("widget")
    if key in SPECIAL_TEXT:
        return SPECIAL_TEXT[key](ctx)
    if widget == "SELECT":
        return catalog_first(schema, field.get("catalogCode"), PREFERRED_CODE.get(key))
    if widget == "COUNTRY":
        return COUNTRY[ctx["jurisdiction"]]
    if widget == "DATE":
        return (TODAY + timedelta(days=730)).isoformat() if "xpiry" in key else "2020-01-15"
    if widget == "BOOLEAN":
        return "false"
    if widget == "EMAIL":
        return ctx["email"]
    if widget == "PHONE":
        return "+971501234567"
    if widget == "URL":
        return "https://example.test"
    if widget == "MONEY":
        return "1000.00000000"
    pattern = (field.get("validation") or {}).get("pattern")
    if pattern:
        return from_pattern(pattern)
    return "Acceptance " + ctx["run"][-6:]


def address_values(schema, ctx):
    fmt = schema.get("addressFormat") or schema.get("fallbackAddressFormat") or {}
    defaults = {"addressLine1": "Building 7, Acceptance Street", "addressLine2": "Floor 3", "city": CITY[ctx["jurisdiction"]],
                "stateOrProvince": CITY[ctx["jurisdiction"]], "poBox": "12345", "buildingNumber": "1234",
                "additionalNumber": "5678", "district": "Al Olaya", "postalCode": "12345",
                "countryCode": COUNTRY[ctx["jurisdiction"]]}
    out = {}
    for comp in fmt.get("components") or []:
        name = comp["component"]
        if not comp.get("required") and name != "poBox":
            continue
        out[name] = catalog_first(schema, comp["catalogCode"]) if comp.get("catalogCode") else defaults.get(name, "Acceptance")
    return out


def identifier_values(schema, subject, ctx):
    """主主体取 Schema 中适用的第一个标识类型；关系人 / 个人优先 ctx 指定类型（缺省 PASSPORT）。"""
    candidates = [f["fieldKey"].split(".", 1)[1] for _, _, f in fields(schema)
                  if f.get("widget") == "IDENTIFIER" and f.get("appliesTo") in (subject, "BOTH")]
    wanted = ctx.get("identifierType") or ("PASSPORT" if "PASSPORT" in candidates else (candidates or [None])[0])
    types = {t["code"]: t for t in schema.get("identifierTypes") or []}
    rule = types.get(wanted) or {}
    value = ctx.get("identifierValue") or from_pattern(rule.get("pattern") or "[A-Z0-9]{10}", prefer=10)
    out = {"identifierType": wanted, "identifierValue": value}
    if rule.get("issuerScopeMode") == "COUNTRY":
        out["issuingCountry"] = ctx.get("issuingCountry", "GB")
    if rule.get("expiryRequired"):
        out["issueDate"] = "2022-01-10"
        out["expiryDate"] = (TODAY + timedelta(days=5 * 365)).isoformat()
    if rule.get("partyType") == "INDIVIDUAL" or wanted == "PASSPORT":
        SENSITIVE.add(value)
    return out


def subject_values(schema, subject, ctx):
    values = {}
    for _, _, field in fields(schema):
        if field.get("appliesTo") not in (subject, "BOTH") or field.get("requirementLevel") != "REQUIRED":
            continue
        if field.get("widget") in ("PERSON_LIST", "DOCUMENT_SLOT", "DECLARATION", "IDENTIFIER", "ADDRESS"):
            continue
        section, _, name = field["fieldKey"].partition(".")
        values.setdefault(section, {})[name] = field_value(schema, field, ctx)
    values["identifier"] = identifier_values(schema, subject, ctx)
    values["address"] = address_values(schema, ctx)
    for section, extra in (ctx.get("extra") or {}).items():
        values.setdefault(section, {}).update(extra)
    return values


def patch_body(values):
    return {"dirtyFields": [f"{s}.{k}" for s, kv in values.items() for k in kv], "values": values}


# ---------------------------------------------------------------------------------------------------------------- Client 侧步骤

def form_schema(client, **query):
    status, body = client.get("/onboarding/v1/form-schema?" + urllib.parse.urlencode(query))
    return status, body


def create_application(client, ac, label, jurisdiction, client_type, organization_type=None, code=None, expect=(200, 201)):
    body = {"clientType": client_type, "jurisdictionCode": jurisdiction}
    if organization_type:
        body["organizationTypeCode"] = organization_type
    if code:
        body["onboardingCode"] = code
    status, reply = client.call("POST", "/onboarding/v1/applications", body)
    ok = status in expect
    check(ac, f"{label} 创建申请（{jurisdiction} {client_type} {organization_type or ''}）", ok,
          f"HTTP {status} applicationId={reply.get('applicationId')} operation={reply.get('operation')} {problem(reply) if not ok else ''}")
    return status, reply


def read_app(client, application_id):
    status, app = client.get(f"/onboarding/v1/applications/{application_id}")
    if status != 200:
        raise Stop(f"读取申请失败 HTTP {status} {problem(app)}")
    return app


def wait_app(client, application_id, wanted, seconds=90):
    deadline, app = time.time() + seconds, {}
    while time.time() < deadline:
        app = read_app(client, application_id)
        if app.get("status") in wanted:
            return app
        time.sleep(3)
    return app


def save_draft(client, ac, application_id, values, label):
    status, reply = client.call("PATCH", f"/onboarding/v1/applications/{application_id}/draft", patch_body(values))
    require(ac, f"{label} 保存草稿", status == 200, f"HTTP {status} fields={len(patch_body(values)['dirtyFields'])} {problem(reply)}")


def add_person(client, ac, application_id, values, relation, label):
    status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/people",
                                {"person": patch_body(values), "relation": relation})
    require(ac, f"{label} 新增关系人（{relation['relationType']}）", status in (200, 201) and reply.get("partyId"),
            f"HTTP {status} partyId={reply.get('partyId')} relationId={reply.get('relationId')} {problem(reply)}")
    return reply["partyId"]


def add_relation(client, ac, application_id, party_id, relation, label):
    status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/relations", {"relatedPartyId": party_id, **relation})
    require(ac, f"{label} 追加关系（{relation['relationType']}）", status in (200, 201),
            f"HTTP {status} relationId={reply.get('relationId')} {problem(reply)}")


def upload_file(client, ac, label):
    status, grant = client.call("POST", "/onboarding-files/v1/uploads",
                                {"filename": f"{label.lower()}.pdf", "mimeType": "application/pdf", "sizeBytes": str(len(PDF))})
    require(ac, f"上传授权 {label}", status in (200, 201) and (grant.get("authorization") or {}).get("url"), f"HTTP {status} {problem(grant)}")
    auth = grant["authorization"]
    put_status, put_body, host = V2.presigned(auth.get("method", "PUT"), auth["url"], auth.get("headers"), PDF)
    require(ac, f"对象存储 PUT {label}", 200 <= put_status < 300, f"HTTP {put_status} host={host}")
    status, done = client.call("POST", f"/onboarding-files/v1/uploads/{grant['uploadId']}/completion")
    file_id, address = done.get("fileId"), done.get("fileAddress")
    require(ac, f"上传完成 {label}", status in (200, 201) and address == f"/files/v1/{file_id}", f"HTTP {status} fileAddress={address}")
    deadline, meta = time.time() + 90, {}
    while time.time() < deadline:
        _, meta = client.get(f"/onboarding-files/v1/files/{file_id}")
        if meta.get("scanStatus") not in ("PENDING_SCAN", "SCANNING", None):
            break
        time.sleep(3)
    require(ac, f"文件扫描 {label}", meta.get("scanStatus") == "AVAILABLE", f"scanStatus={meta.get('scanStatus')} fileId={file_id}")
    return address


def attach_document(client, ac, application_id, subject_party_id, material, label):
    address = upload_file(client, ac, label)
    body = {"subjectPartyId": subject_party_id, "requirementCode": material["requirementCode"],
            "documentType": (material.get("documentTypes") or ["OTHER"])[0], "fileAddress": address}
    if material.get("maxAgeDays"):
        body["issueDate"] = (TODAY - timedelta(days=5)).isoformat()
    status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/documents", body)
    require(ac, f"关联材料 {label}", status in (200, 201) and reply.get("documentId"),
            f"HTTP {status} documentId={reply.get('documentId')} {problem(reply)}")
    return reply["documentId"]


def material_targets(schema, primary_party, persons):
    """必需材料 → [(主体 partyId, 材料)]；PRIMARY 归主主体，ANY_RELATED / RELATED_PERSON 归每位关系人，其他角色码归担任该角色的人。"""
    targets = []
    for material in schema.get("materials") or []:
        if not material.get("required"):
            continue
        role = material.get("subjectRole")
        if role == "PRIMARY":
            targets.append((primary_party, material, "PRIMARY"))
            continue
        for person in persons:
            if role in ("ANY_RELATED", "RELATED_PERSON") or role in person["roles"]:
                targets.append((person["partyId"], material, person["label"]))
    return targets


def declare_all(client, ac, application_id, schema):
    for declaration in schema.get("declarations") or []:
        status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/declarations",
                                    {"declarationType": declaration["declarationType"], "declaredValue": "TRUE"})
        require(ac, f"声明 {declaration['declarationType']}", status == 200 and reply.get("declarationId"),
                f"HTTP {status} declarationId={reply.get('declarationId')} {problem(reply)}")


def requirements(client, application_id):
    status, body = client.get(f"/onboarding/v1/applications/{application_id}/requirements")
    return status, body


def open_items(body):
    return [(i.get("requirementKey"), i.get("errorCode"), i.get("status")) for i in body.get("items") or []
            if i.get("status") not in ("SATISFIED", "WAIVED", "PENDING_VERIFICATION")]


def fill_organization(client, ac, label, application_id, schema, ctx, people_spec):
    """主主体草稿 + 关系人（含追加关系）+ 必需材料 + 声明；返回 (primaryPartyId, persons)。"""
    app = read_app(client, application_id)
    primary = app["subjectPartyId"]
    save_draft(client, ac, application_id, subject_values(schema, "PRIMARY", ctx), f"{label} 主主体")
    persons = []
    for spec in people_spec:
        person_ctx = {**ctx, "given": spec["given"], "family": spec["family"], "identifierValue": spec["passport"],
                      "identifierType": "PASSPORT", "birthDate": "1982-03-04"}
        first, *more = spec["relations"]
        party = add_person(client, ac, application_id, subject_values(schema, "RELATED_PERSON", person_ctx), first, f"{label} {spec['label']}")
        for relation in more:
            add_relation(client, ac, application_id, party, relation, f"{label} {spec['label']}")
        persons.append({"partyId": party, "label": spec["label"], "roles": [r["relationType"] for r in spec["relations"]]})
    documents = {}
    for party, material, owner in material_targets(schema, primary, persons):
        documents[(party, material["requirementCode"])] = attach_document(
            client, ac, application_id, party, material, f"{label}-{owner}-{material['requirementCode']}")
    declare_all(client, ac, application_id, schema)
    return primary, persons, documents


def submit(client, ac, application_id, label):
    status, check_body = client.get(f"/onboarding/v1/applications/{application_id}/submission-check")
    require(ac, f"{label} 提交核对 ready", status == 200 and check_body.get("ready") is True,
            f"HTTP {status} ready={check_body.get('ready')} errors={(check_body.get('errors') or [])[:10]}")
    status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/submissions")
    require(ac, f"{label} 提交", status in (200, 202) and reply.get("status") == "SUBMITTED",
            f"HTTP {status} submissionId={reply.get('submissionId')} {problem(reply)}")
    app = wait_app(client, application_id, ("UNDER_REVIEW",), 60)
    require(ac, f"{label} 进入审核", app.get("status") == "UNDER_REVIEW", f"status={app.get('status')}")
    return reply["submissionId"]


MASKED = re.compile(r'"identifierMasked"\s*:\s*"([^"]+)"')
RAW_KEY = re.compile(r'"identifierValue"\s*:\s*"[^"]+"')


def masked_values(text):
    return MASKED.findall(text)


def masked_only(ac, label, payload):
    """响应里没有任何 identifierValue 原值键，也不含本次证件原值。"""
    text = json.dumps(payload, ensure_ascii=False)
    leaked = [v for v in SENSITIVE if v in text]
    keys = RAW_KEY.findall(text)
    return check(ac, f"{label} 证件只返回掩码", not leaked and not keys,
                 f"identifierMasked={masked_values(text)[:6]} leakedCount={len(leaked)} rawKeys={len(keys)}")


# ---------------------------------------------------------------------------------------------------------------- 运营侧步骤

def find_case(ops, application_id, previous_case_id=None, seconds=60):
    deadline, found = time.time() + seconds, None
    while time.time() < deadline:
        status, page = ops.get(f"{CASE_ADMIN}/cases?page=1&size=20&applicationId={application_id}")
        items = [i for i in (page.get("items") or []) if i.get("applicationId") == str(application_id)]
        if previous_case_id:
            items = [i for i in items if i.get("caseId") != previous_case_id]
        if items:
            found = max(items, key=lambda i: int(i.get("roundNo") or 0))
            return found
        time.sleep(3)
    return found


def case_detail(ops, case_id):
    status, body = ops.get(f"{CASE_ADMIN}/cases/{case_id}")
    if status != 200:
        raise Stop(f"案件详情 HTTP {status} {problem(body)}")
    return body


def review_of(detail):
    return detail.get("review") or detail


def flatten(review):
    reqs, evidence, checks, runs, hits = [], [], [], [], []
    for subject in review.get("subjects") or []:
        for req in subject.get("requirements") or []:
            reqs.append({**req, "caseId": subject.get("caseId"), "partyId": subject.get("partyId")})
            for item in req.get("evidence") or []:
                evidence.append({**item, "requirementCode": req.get("requirementCode"), "partyId": subject.get("partyId")})
        for item in subject.get("checks") or []:
            checks.append({**item, "caseId": subject.get("caseId")})
        for run in subject.get("screenings") or []:
            runs.append({**run, "caseId": subject.get("caseId")})
            for hit in run.get("hits") or []:
                hits.append({**hit, "caseId": subject.get("caseId"), "runId": run.get("runId")})
    return reqs, evidence, checks, runs, hits


def wait_screening(ops, case_id, seconds=150):
    deadline, review = time.time() + seconds, {}
    while time.time() < deadline:
        review = review_of(case_detail(ops, case_id))
        _, _, _, runs, _ = flatten(review)
        if runs and "SCREENING_INCOMPLETE" not in (review.get("approvalBlockers") or []) \
                and all(r.get("status") not in ("STARTED", "PENDING") for r in runs):
            return review
        time.sleep(4)
    return review


def command(ops, method, path, body):
    return ops.call(method, CASE_ADMIN + path, body, json_empty=body is None)


def blocked(status, body, code, blockers=()):
    """422 CLIENT_REVIEW_NOT_READY，且门禁码出现在 Problem detail 或同一查看人的案件详情 approvalBlockers 中。"""
    return status == 422 and body.get("code") == "CLIENT_REVIEW_NOT_READY" and (code in str(body.get("detail") or "") or code in blockers)


def try_approve(ops, case_id, partner):
    """先读该查看人视角的门禁，再尝试批准；返回 (状态, 响应, 门禁)。"""
    blockers = review_of(case_detail(ops, case_id)).get("approvalBlockers") or []
    status, reply = command(ops, "POST", f"/cases/{case_id}/approval",
                            {"reasonCode": "DOCUMENTS_VERIFIED", "reason": "acceptance", "onBehalfOfPartnerId": partner})
    return status, reply, blockers


def accept_and_pass(ops, ac, case_id, partner, label, reject=None):
    """接受全部待核验材料（reject：{requirementCode: rejectReasonCode} 改为拒绝）并把全部人工检查录为 PASS。"""
    review = review_of(case_detail(ops, case_id))
    reqs, evidence, checks, _, _ = flatten(review)
    for item in evidence:
        if item.get("status") != "RECEIVED":
            continue
        reason = (reject or {}).get(item["requirementCode"])
        body = {"outcome": "REJECTED", "rejectReasonCode": reason, "note": "acceptance reject"} if reason else {"outcome": "ACCEPTED"}
        body["onBehalfOfPartnerId"] = partner
        status, reply = command(ops, "POST", f"/cases/{case_id}/evidence/{item['evidenceId']}/review", body)
        check("AC-06", f"{label} 材料{'拒绝' if reason else '接受'} {item['requirementCode']}", status == 200,
              f"HTTP {status} evidenceId={item['evidenceId']} {problem(reply) if status != 200 else ''}")
    for item in checks:
        if item.get("status") == "COMPLETED":
            continue
        status, reply = command(ops, "POST", f"/cases/{case_id}/checks/{item['checkId']}/result",
                                {"outcome": "PASS", "reasonCode": "VERIFIED", "note": "acceptance manual check",
                                 "onBehalfOfPartnerId": partner})
        check("AC-06", f"{label} 人工检查 {item.get('checkCode')} PASS（case {item['caseId']}）", status == 200,
              f"HTTP {status} checkId={item['checkId']} {problem(reply) if status != 200 else ''}")
    return reqs, checks


def classify(ops, case_id, detail, partner, label):
    industries = detail.get("industries") or []
    if not industries:
        check("AC-06", f"{label} 行业目录可用", False, f"industriesStatus={detail.get('industriesStatus')}")
        return
    status, reply = command(ops, "PUT", f"/cases/{case_id}/classifications",
                            {"items": [{"industryCode": industries[0]["code"], "mccCode": "5999", "primary": True}]})
    check("AC-06", f"{label} 设置分类", status == 200, f"HTTP {status} industry={industries[0]['code']} {problem(reply) if status != 200 else ''}")
    status, reply = command(ops, "POST", f"/cases/{case_id}/classification-confirmation", None)
    check("AC-06", f"{label} 确认分类", status == 200 and reply.get("confirmed") is True, f"HTTP {status} {problem(reply)}")


def rate(ops, case_id, partner, label, rating="LOW"):
    status, reply = command(ops, "PUT", f"/cases/{case_id}/risk-rating",
                            {"riskRating": rating, "factors": [{"factorCode": "COUNTRY_RISK", "factorValue": "LOW"},
                                                               {"factorCode": "PEP", "factorValue": "NONE"}],
                             "note": "acceptance manual CRR", "onBehalfOfPartnerId": partner})
    check("AC-06", f"{label} CRR {rating} + 因子", status == 200, f"HTTP {status} {problem(reply)}")


# ---------------------------------------------------------------------------------------------------------------- 只读 SQL

def sql(service, statement):
    return E.sql(service, statement)


def scan_raw_values(ac, service, values):
    """扫描该服务库全部文本列，确认不含本次证件原值（LIKE，库小）。"""
    if not values:
        skip(ac, f"{service} 库无证件原值", "本次无个人证件原值")
        return
    database = E.DATABASES[service][0]
    columns = sql(service, "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                           "AND data_type IN ('char','varchar','text','mediumtext','longtext','json')")
    by_table = {}
    for table, column in columns:
        by_table.setdefault(table, []).append(column)
    hits = []
    likes = [v.replace("'", "") for v in values]
    for table, cols in by_table.items():
        where = " OR ".join(f"`{c}` LIKE '%{v}%'" for c in cols for v in likes)
        count = int(sql(service, f"SELECT COUNT(*) FROM `{table}` WHERE {where}")[0][0])
        if count:
            hits.append((table, count))
    check(ac, f"{service}（{database}）全部文本列不含证件原值", not hits,
          f"tables={len(by_table)} columns={len(columns)} values={len(values)} hits={hits}")


# ---------------------------------------------------------------------------------------------------------------- 场景

def s0_config_and_codes(ctx):
    ops = ctx["opsA"]
    status, jurisdictions = ops.get("/onboarding-config-admin/v1/jurisdictions")
    codes = sorted(j.get("jurisdictionCode") for j in jurisdictions) if isinstance(jurisdictions, list) else []
    check("AC-15", "运营 入驻配置：辖区包", status == 200 and {"AE", "SA", "EG"} <= set(codes), f"HTTP {status} jurisdictions={codes}")
    for jurisdiction in ("AE", "SA", "EG"):
        status, schemas = ops.get(f"/onboarding-config-admin/v1/onboarding-schemas?jurisdiction={jurisdiction}")
        check("AC-15", f"运营 入驻配置：{jurisdiction} Schema", status == 200 and isinstance(schemas, list) and schemas,
              f"HTTP {status} schemas={[(s.get('schemaId'), s.get('partyType'), s.get('organizationTypeCode'), s.get('status'), len(s.get('fields') or [])) for s in schemas or []] if isinstance(schemas, list) else problem(schemas)}")
        status, programs = ops.get(f"/onboarding-config-admin/v1/programs?jurisdiction={jurisdiction}")
        items = programs.get("items") if isinstance(programs, dict) else None
        check("AC-15", f"运营 入驻配置：{jurisdiction} 尽调程序", status == 200 and items,
              f"HTTP {status} programs={[(p.get('programCode'), p.get('versionStatus'), p.get('fourEyesRequired')) for p in items or []]}")
    status, items = ops.get("/onboarding-config-admin/v1/catalog-items?catalogCode=STATE_OR_PROVINCE&jurisdiction=EG")
    check("AC-15", "运营 入驻配置：目录项（EG 省）", status == 200 and isinstance(items, list) and items,
          f"HTTP {status} count={len(items) if isinstance(items, list) else problem(items)}")
    status, page = ops.get("/client-admin/v1/clients?page=1&size=10")
    check("AC-15", "运营 Client 列表", status == 200 and "items" in page, f"HTTP {status} total={page.get('total')}")

    status, partners = ops.get("/onboarding-code-admin/v1/local-partners")
    items = partners.get("items") or []
    partner = ctx["args"].partner_id or (items[0]["localPartnerId"] if items else None)
    require("AC-04", "运营 可签发入驻码的 PayFac（OE_PROVIDER）", status == 200 and partner,
            f"HTTP {status} partners={[(p.get('localPartnerId'), p.get('partnerNo'), p.get('status')) for p in items]}")
    ctx["partner"] = partner
    IDS["responsiblePartnerId"] = partner
    base = f"/onboarding-code-admin/v1/local-partners/{partner}/onboarding-codes"

    def issue(max_uses, note, extra=None):
        status, reply = ops.call("POST", base, {"maxUses": max_uses, "note": note, **(extra or {})})
        return status, reply

    status, reply = issue(50, f"acceptance {ctx['run']} main")
    code = reply.get("code") or ""
    issued = reply.get("issued") or {}
    require("AC-04", "签发入驻码：明文只在签发响应出现一次、格式 XXXX-XXXX、掩码末 4 位",
            status == 200 and CODE_FORMAT.match(code) and issued.get("codeMasked") == "****-" + code[-4:],
            f"HTTP {status} codeId={issued.get('codeId')} codeMasked={issued.get('codeMasked')} status={issued.get('status')}")
    ctx["code"], IDS["onboardingCodeId"] = code, issued.get("codeId")
    status, reply = issue(1, f"acceptance {ctx['run']} single-use")
    ctx["codeSingle"], IDS["onboardingCodeSingleUseId"] = reply.get("code"), (reply.get("issued") or {}).get("codeId")
    check("AC-04", "签发一次性入驻码（maxUses=1）", status == 200 and ctx["codeSingle"], f"HTTP {status} codeId={IDS['onboardingCodeSingleUseId']}")
    status, reply = issue(5, f"acceptance {ctx['run']} to-revoke")
    revoke_id, ctx["codeRevoked"] = (reply.get("issued") or {}).get("codeId"), reply.get("code")
    status, reply = ops.call("POST", f"{base}/{revoke_id}/revocation", None, json_empty=True)
    check("AC-04", "作废入驻码 → REVOKED，作废结果无明文", status == 200 and reply.get("status") == "REVOKED" and not reply.get("code"),
          f"HTTP {status} codeId={revoke_id} status={reply.get('status')} codeMasked={reply.get('codeMasked')}")
    past = datetime.now(timezone.utc) - timedelta(hours=2)
    status, reply = issue(5, f"acceptance {ctx['run']} expired",
                          {"effectiveFrom": past.strftime("%Y-%m-%dT%H:%M:%SZ"),
                           "effectiveTo": (past + timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%SZ")})
    if status == 200 and reply.get("code"):
        ctx["codeExpired"] = reply["code"]
        check("AC-04", "签发已过有效期的入驻码（用于过期校验）", True, f"codeId={(reply.get('issued') or {}).get('codeId')}")
    else:
        skip("AC-04", "签发已过有效期的入驻码", f"Owner 不接受过去的有效期（HTTP {status} {problem(reply)}）；过期由 OnboardingCodeTest 覆盖")
    status, listing = ops.get(base)
    text = json.dumps(listing)
    mine = [i for i in listing.get("items") or [] if i.get("codeId") == IDS["onboardingCodeId"]]
    check("AC-04", "入驻码列表只含掩码（无任何明文）",
          status == 200 and mine and mine[0].get("code") in (None, "") and mine[0].get("codeMasked") == "****-" + code[-4:]
          and code not in text and ctx["codeSingle"] not in text,
          f"HTTP {status} items={len(listing.get('items') or [])} mine={[(i.get('codeId'), i.get('codeMasked'), i.get('status'), i.get('useCount')) for i in mine]}")


def s1_form_schema(ctx):
    client = ctx["clientA"]
    status, body = client.get("/onboarding/v1/jurisdictions")
    items = body if isinstance(body, list) else body.get("items") or []
    codes = sorted(i.get("jurisdictionCode") for i in items)
    check("AC-01", "Client 可受理辖区", status == 200 and {"AE", "SA", "EG"} <= set(codes), f"HTTP {status} {[(i.get('jurisdictionCode'), i.get('acceptancePolicy')) for i in items]}")

    def summary(schema):
        return {"schemaId": schema.get("schemaId"), "sections": [s.get("section") for s in schema.get("sections") or []],
                "fields": sum(1 for _ in fields(schema)), "address": (schema.get("addressFormat") or {}).get("code"),
                "components": [(c["component"], c.get("required"), c.get("catalogCode")) for c in (schema.get("addressFormat") or {}).get("components") or []],
                "materials": [(m.get("requirementCode"), m.get("subjectRole"), m.get("required")) for m in schema.get("materials") or []],
                "declarations": [d.get("declarationType") for d in schema.get("declarations") or []],
                "identifierTypes": [(t.get("code"), t.get("checksumRule")) for t in schema.get("identifierTypes") or []],
                "groups": sorted({f.get("requirementGroup") for _, _, f in fields(schema) if f.get("requirementGroup")}),
                "relationRules": schema.get("relationRules")}

    shapes = {}
    for jurisdiction in ("AE", "SA", "EG"):
        for party, org in (("ORGANIZATION", "LLC"), ("INDIVIDUAL", None)):
            query = {"jurisdiction": jurisdiction, "partyType": party, **({"organizationType": org} if org else {})}
            status, schema = form_schema(client, **query)
            s = summary(schema) if status == 200 else problem(schema)
            shapes[(jurisdiction, party)] = schema if status == 200 else None
            complete = status == 200 and s["sections"] and s["materials"] and s["declarations"] and s["identifierTypes"] and s["address"]
            if party == "ORGANIZATION":
                complete = complete and s["relationRules"] and "ORGANIZATION_ID" in s["groups"]
            check("AC-01", f"form-schema {jurisdiction} {party}{' ' + org if org else ''}：分区 / 字段 / 标识 / 地址 / 关系规则 / 材料 / 声明", complete, s)
    ae = shapes.get(("AE", "ORGANIZATION")) or {}
    components = {c["component"]: c for c in (ae.get("addressFormat") or {}).get("components") or []}
    check("AC-14", "AE 地址格式 AE_POBOX 含 PO Box 组件", (ae.get("addressFormat") or {}).get("code") == "AE_POBOX" and "poBox" in components,
          list(components))
    sa = shapes.get(("SA", "ORGANIZATION")) or {}
    components = {c["component"]: c for c in (sa.get("addressFormat") or {}).get("components") or []}
    check("AC-14", "SA 国家地址：建筑号 / 附加号 / 区 / 邮编必填",
          (sa.get("addressFormat") or {}).get("code") == "SA_NATIONAL_ADDRESS"
          and all(components.get(c, {}).get("required") for c in ("buildingNumber", "additionalNumber", "district", "postalCode")),
          {k: v.get("required") for k, v in components.items()})
    eg = shapes.get(("EG", "ORGANIZATION")) or {}
    components = {c["component"]: c for c in (eg.get("addressFormat") or {}).get("components") or []}
    state = components.get("stateOrProvince") or {}
    governorates = (eg.get("catalogs") or {}).get(state.get("catalogCode") or "", [])
    check("AC-14", "EG 省（governorate）必填且有目录", state.get("required") is True and governorates,
          f"catalog={state.get('catalogCode')} items={len(governorates)}")
    sa_id = {t.get("code"): t.get("checksumRule") for t in (shapes.get(("SA", "INDIVIDUAL")) or {}).get("identifierTypes") or []}
    eg_id = {t.get("code"): t.get("checksumRule") for t in (shapes.get(("EG", "INDIVIDUAL")) or {}).get("identifierTypes") or []}
    check("AC-01", "标识类型校验位规则（SA NID LUHN、EG NID EG_NID）", sa_id.get("SA_NATIONAL_ID") == "LUHN" and eg_id.get("EG_NATIONAL_ID") == "EG_NID",
          {"SA": sa_id, "EG": eg_id})
    status, sole = form_schema(client, jurisdiction="AE", partyType="INDIVIDUAL", organizationType="SOLE_ESTABLISHMENT")
    keys = [f["fieldKey"] for _, _, f in fields(sole)] if status == 200 else []
    check("AC-14", "AE 个人 SOLE_ESTABLISHMENT Schema（执照属性）", status == 200 and "attributes.AE_TRADE_LICENSE_NUMBER" in keys,
          f"HTTP {status} schemaId={sole.get('schemaId')} organizationTypeCode={sole.get('organizationTypeCode')}")
    status, body = form_schema(client, jurisdiction="XX", partyType="ORGANIZATION")
    check("AC-01", "不存在的辖区 → 422 JURISDICTION_NOT_ACCEPTED", status == 422 and body.get("code") == "JURISDICTION_NOT_ACCEPTED",
          f"HTTP {status} {problem(body)}")
    skip("AC-01", "ACCEPTANCE_POLICY=CLOSED → 422", "AE / SA / EG 均为 OPEN，无 CLOSED 辖区可测（不改数据）；由 OnboardingFormSchemaTest.test_notAccepted 覆盖")
    ctx["schemas"] = shapes


def org_people(run, hit=False):
    """UBO1 60%（兼董事、签字人）+ UBO2 40%；hit=True 时 UBO1 的名含 TESTHIT（Mock 返回 PEP 命中）。"""
    p1, p2 = "P" + rand_digits(8), "P" + rand_digits(8)
    return [
        {"label": "UBO1", "given": "Testhit" if hit else "Amira", "family": "Accept" + run[-4:], "passport": p1,
         "relations": [{"relationType": "UBO", "ownershipPercentage": "60", "effectiveFrom": "2024-01-01", "uboBasis": "OWNERSHIP"},
                       {"relationType": "DIRECTOR", "effectiveFrom": "2024-01-01", "jobTitle": "Director"},
                       {"relationType": "SIGNATORY", "effectiveFrom": "2024-01-01", "jobTitle": "Authorised signatory"}]},
        {"label": "UBO2", "given": "Omar", "family": "Accept" + run[-4:], "passport": p2,
         "relations": [{"relationType": "UBO", "ownershipPercentage": "40", "effectiveFrom": "2024-01-01", "uboBasis": "OWNERSHIP"}]},
    ]


def org_ctx(run, client_email, legal_name, jurisdiction="AE", organization_type="LLC"):
    return {"run": run, "jurisdiction": jurisdiction, "organizationType": organization_type, "legalName": legal_name,
            "email": client_email, "given": "Amira", "family": "Accept"}


def sa_main_flow(ctx):
    """账号 A：AE 机构 → 提交 → 运营审核 → 四眼批准。"""
    client, ops, partner, run = ctx["clientA"], ctx["opsA"], ctx["partner"], ctx["run"]
    status, created = create_application(client, "AC-04", "A", "AE", "ORGANIZATION", "LLC", ctx["code"])
    require("AC-04", "A 使用有效入驻码创建", status in (200, 201) and created.get("applicationId"), created)
    application_id = created["applicationId"]
    IDS["appA"], IDS["clientA"] = application_id, created.get("clientId")
    app = read_app(client, application_id)
    check("AC-04", "A 申请冻结责任方 = 入驻码 PayFac、冻结 Schema",
          app.get("responsiblePartnerId") == partner and app.get("schemaId") and app.get("jurisdictionCode") == "AE",
          f"responsiblePartnerId={app.get('responsiblePartnerId')} name={app.get('responsiblePartnerName')} schemaId={app.get('schemaId')}")
    status, schema = form_schema(client, applicationId=application_id)
    require("AC-12", "A form-schema 按申请冻结版本", status == 200 and str(schema.get("schemaId")) == str(app.get("schemaId")),
            f"HTTP {status} schemaId={schema.get('schemaId')} app.schemaId={app.get('schemaId')}")

    status, before = requirements(client, application_id)
    codes = {i.get("errorCode") for i in before.get("items") or []}
    check("AC-02", "空草稿需求清单：缺字段 / 缺证件 / 缺人员关系 / 缺材料 / 缺声明",
          status == 200 and before.get("ready") is False and {"FIELD_MISSING", "DOCUMENT_MISSING", "DECLARATION_MISSING"} <= codes
          and codes & {"UBO_MISSING", "SIGNATORY_MISSING", "DIRECTOR_MISSING", "RELATED_PERSON_INCOMPLETE"},
          f"HTTP {status} ready={before.get('ready')} codes={sorted(c for c in codes if c)}")
    status, check_body = client.get(f"/onboarding/v1/applications/{application_id}/submission-check")
    due = {(i.get("requirementKey"), i.get("errorCode")) for i in before.get("items") or [] if i.get("status") == "CURRENTLY_DUE"}
    errors = {(e.get("requirementKey"), e.get("code")) for e in check_body.get("errors") or []}
    check("AC-02", "提交核对与需求清单同源（CURRENTLY_DUE ⊆ 核对 errors）", status == 200 and due and due <= errors,
          f"due={len(due)} errors={len(errors)} missingInCheck={sorted(due - errors)[:6]}")
    status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/submissions")
    check("AC-02", "不完整提交 → 422 SUBMISSION_INCOMPLETE", status == 422 and reply.get("code") == "SUBMISSION_INCOMPLETE",
          f"HTTP {status} {problem(reply)}")

    people = org_people(run)
    primary, persons, documents = fill_organization(client, "AC-03", "A", application_id, schema,
                                                    org_ctx(run, IDS["clientAEmail"], f"Acceptance Trading {run} LLC"), people)
    IDS["appAPersons"] = [p["partyId"] for p in persons]
    status, after = requirements(client, application_id)
    check("AC-02", "A 填满后需求清单 ready", status == 200 and after.get("ready") is True, f"ready={after.get('ready')} open={open_items(after)[:10]}")
    app = read_app(client, application_id)
    masked_only("AC-03", "A Client 读回", app)
    ctx["passportsA"] = [p["passport"] for p in people]
    submission_id = submit(client, "AC-05", application_id, "A")
    IDS["submissionA"] = submission_id

    found = find_case(ops, application_id)
    require("AC-05", "A 案件出现在工作台", found, f"applicationId={application_id}")
    case_id = found["caseId"]
    IDS["caseA"] = case_id
    check("AC-10", "A 案件冻结责任方", found.get("responsiblePartnerId") == partner,
          f"caseId={case_id} responsiblePartnerId={found.get('responsiblePartnerId')} roundNo={found.get('roundNo')}")
    status, page = ops.get(f"{CASE_ADMIN}/cases?page=1&size=50&responsiblePartnerId={partner}&applicationId={application_id}")
    status0, page0 = ops.get(f"{CASE_ADMIN}/cases?page=1&size=50&responsiblePartnerId=0&applicationId={application_id}")
    check("AC-10", "按 PayFac 过滤：本 PayFac 可见、平台自营（0）不可见",
          status == 200 and any(i.get("caseId") == case_id for i in page.get("items") or [])
          and status0 == 200 and not any(i.get("caseId") == case_id for i in page0.get("items") or []),
          f"partner={len(page.get('items') or [])} self={len(page0.get('items') or [])}")

    detail = case_detail(ops, case_id)
    review = review_of(detail)
    subjects = review.get("subjects") or []
    children = [s for s in subjects if s.get("caseId") != case_id]
    check("AC-05", "主案件 + 每个关系人子案件 + 依赖", len(children) == len(persons) and all(s.get("dependencyTypes") for s in children),
          f"subjects={[(s.get('caseId'), s.get('roleType'), s.get('dependencyTypes')) for s in subjects]}")
    check("AC-05", "案件冻结辖区 / 档次 / 程序版本", review.get("jurisdictionCode") == "AE" and review.get("tier") == "STANDARD" and review.get("programVersionId"),
          f"jurisdiction={review.get('jurisdictionCode')} tier={review.get('tier')} programVersionId={review.get('programVersionId')} fourEyes={review.get('fourEyesRequired')}")
    masked_only("AC-03", "A 运营案件详情", detail)

    review = wait_screening(ops, case_id)
    _, _, checks, runs, hits = flatten(review)
    check("AC-13", "Mock 筛查已执行：providerCode = S_SCREENING_MOCK、全部 SUCCEEDED、无命中（非 TESTHIT 姓名）",
          runs and all(r.get("providerCode") == SCREENING_CHANNEL and r.get("status") == "SUCCEEDED" for r in runs) and not hits,
          f"runs={[(r.get('caseId'), r.get('screeningType'), r.get('providerCode'), r.get('status')) for r in runs]} hits={len(hits)}")
    check("AC-13", "程序含 MANUAL_SCREENING 人工检查且参与门禁", any(c.get("checkCode") == "MANUAL_SCREENING" for c in checks)
          and "CHECK_NOT_PASSED" in (review.get("approvalBlockers") or []),
          f"checks={sorted({c.get('checkCode') for c in checks})} blockers={review.get('approvalBlockers')}")

    reqs, _ = accept_and_pass(ops, "AC-06", case_id, partner, "A")
    waivable = [r for r in flatten(review_of(case_detail(ops, case_id)))[0]
                if not r.get("required") and r.get("waivable") and r.get("status") == "PENDING" and not r.get("evidence")]
    if waivable:
        target = waivable[0]
        status, reply = command(ops, "POST", f"/cases/{case_id}/requirements/{target['requirementId']}/waiver",
                                {"reasonCode": "NOT_APPLICABLE", "note": "acceptance waiver", "onBehalfOfPartnerId": partner})
        check("AC-06", f"豁免非必需材料 {target.get('requirementCode')}", status == 200, f"HTTP {status} {problem(reply)}")
    else:
        skip("AC-06", "豁免非必需材料", "无 PENDING 的可豁免非必需要求")
    classify(ops, case_id, detail, partner, "A")

    status, reply, blockers = try_approve(ops, case_id, partner)
    check("AC-06", "未评 CRR 时批准被拒（RISK_RATING_MISSING）", blocked(status, reply, "RISK_RATING_MISSING", blockers),
          f"HTTP {status} {problem(reply)} blockers={blockers}")
    rate(ops, case_id, partner, "A")
    status, reply = command(ops, "POST", f"/cases/{case_id}/recommendation",
                            {"recommendation": "APPROVE", "note": "acceptance recommendation", "onBehalfOfPartnerId": partner})
    check("AC-06", "推荐 APPROVE（运营 A，代 PayFac）", status == 200, f"HTTP {status} {problem(reply)}")
    status, reply, blockers = try_approve(ops, case_id, partner)
    check("AC-06", "四眼：推荐人 = 决定人批准被拒（FOUR_EYES_SAME_PERSON）", blocked(status, reply, "FOUR_EYES_SAME_PERSON", blockers),
          f"HTTP {status} {problem(reply)} blockers={blockers}")
    reviewer = ctx.get("opsB")
    if not reviewer:
        skip("AC-08", "四眼批准（复核人 B）", "无第二个运营账号：设置 ONB_REVIEWER_EMAIL/ONB_REVIEWER_PASSWORD 或 --create-reviewer；AC-08/AC-09 后续步骤未执行")
        raise Stop("no reviewer")
    blockers = review_of(case_detail(reviewer, case_id)).get("approvalBlockers") or []
    check("AC-06", "复核人 B 视角无门禁", not blockers, f"blockers={blockers}")
    status, reply = command(reviewer, "POST", f"/cases/{case_id}/approval",
                            {"reasonCode": "DOCUMENTS_VERIFIED", "reason": "acceptance four-eyes approval", "onBehalfOfPartnerId": partner})
    require("AC-08", "复核人 B 批准（代 PayFac）", status == 200, f"HTTP {status} {problem(reply)}")

    app = wait_app(client, application_id, ("APPROVED",), 90)
    check("AC-08", "Client 看到批准", app.get("status") == "APPROVED" and (app.get("review") or {}).get("outcome") == "APPROVED",
          f"status={app.get('status')} review={app.get('review')}")
    client_id = app.get("clientId") or IDS["clientA"]
    deadline, page = time.time() + 60, {}
    while time.time() < deadline:
        status, page = ops.get(f"/client-admin/v1/clients/{client_id}")
        summary = dig(page, "client", "client") or {}
        if summary.get("status") == "ACTIVE":
            break
        time.sleep(3)
    summary = dig(page, "client", "client") or {}
    check("AC-08", "Client ACTIVE（ClientReviewDecided 同事务激活）、辖区与责任方",
          summary.get("status") == "ACTIVE" and summary.get("jurisdictionCode") == "AE" and summary.get("responsiblePartnerId") == partner,
          f"clientId={client_id} status={summary.get('status')} jurisdiction={summary.get('jurisdictionCode')} responsiblePartnerId={summary.get('responsiblePartnerId')}")
    qualifications = page.get("qualifications") or []
    check("AC-08", "资格投影 APPROVED", any(q.get("status") == "APPROVED" and q.get("roleType") == "CLIENT" for q in qualifications),
          f"qualificationsStatus={page.get('qualificationsStatus')} {[(q.get('qualificationId'), q.get('status'), q.get('validTo')) for q in qualifications]}")
    masked_only("AC-03", "A 运营 Client 详情", page)
    applications = dig(page, "client", "applications") or []
    decision_id = next((a.get("qualificationDecisionId") for a in applications if a.get("applicationId") == application_id), None)
    IDS["decisionA"] = decision_id
    review = review_of(case_detail(ops, case_id))
    timeline = review.get("timeline") or []
    check("AC-10", "时间线记 operated_by=PLATFORM + on_behalf_of_partner_id",
          any(t.get("operatedBy") == "PLATFORM" and t.get("onBehalfOfPartnerId") == partner for t in timeline),
          f"{[(t.get('actionType'), t.get('operatedBy'), t.get('onBehalfOfPartnerId')) for t in timeline][-6:]}")
    ctx["clientIdA"] = client_id
    db_decision_checks(ctx, case_id, client_id, partner)


def db_decision_checks(ctx, case_id, client_id, partner):
    try:
        rows = sql("compliance", "SELECT tier, risk_rating, jurisdiction_code, responsible_partner_id, recommended_by, decided_by, "
                                 "four_eyes_waived, operated_by, on_behalf_of_partner_id, outcome FROM t_qualification_decision "
                                 f"WHERE case_id = {int(case_id)} ORDER BY decision_version DESC LIMIT 1")
        row = rows[0] if rows else []
        check("AC-08", "决定冻结 tier / CRR / 辖区 / 责任方 / 推荐人 / 决定人（推荐人 ≠ 决定人）",
              row and row[0] == "STANDARD" and row[1] == "LOW" and row[2] == "AE" and row[3] == str(partner)
              and row[4] not in ("", "NULL") and row[5] not in ("", "NULL") and row[4] != row[5] and row[9] == "APPROVED",
              f"row={row}")
        check("AC-10", "决定记 operated_by=PLATFORM + on_behalf_of_partner_id", row and row[7] == "PLATFORM" and row[8] == str(partner), f"row={row[6:9] if row else row}")
        rows = sql("compliance", "SELECT status, tier, risk_rating, jurisdiction_code, responsible_partner_id, next_review_time, review_interval_months "
                                 f"FROM t_role_qualification WHERE role_type = 'CLIENT' AND role_id = {int(client_id)}")
        check("AC-08", "资格投影 APPROVED 带 next_review_time / 档次 / CRR / 责任方",
              rows and rows[0][0] == "APPROVED" and rows[0][5] not in ("", "NULL") and rows[0][4] == str(partner), f"rows={rows}")
        rows = sql("client-master", "SELECT event_type, COUNT(*) FROM t_consumed_event "
                                    "WHERE event_type IN ('ClientReviewDecided','QualificationChanged') GROUP BY event_type")
        check("AC-08", "client-master 已消费 ClientReviewDecided / QualificationChanged", dict(rows).get("ClientReviewDecided")
              and dict(rows).get("QualificationChanged"), f"consumed={rows}")
    except Exception as failure:  # noqa: BLE001  只读核对失败单独记录，不中断场景
        check("AC-08", "只读 SQL 核对决定 / 资格", False, f"{type(failure).__name__}: {str(failure)[:200]}")


def dig(value, *path):
    for key in path:
        if not isinstance(value, dict):
            return None
        value = value.get(key)
    return value


def sb_return_flow(ctx):
    """账号 B：TESTHIT → PEP 命中 → 退回 → 重提第 2 轮复用 → 再退回 → 撤回 RETURNED。"""
    client, ops, partner, run = ctx["clientB"], ctx["opsA"], ctx["partner"], ctx["run"]
    status, created = create_application(client, "AC-04", "B", "AE", "ORGANIZATION", "LLC", ctx["codeSingle"])
    require("AC-07", "B 使用一次性入驻码创建", status in (200, 201) and created.get("applicationId"), created)
    application_id = created["applicationId"]
    IDS["appB"] = application_id
    status, schema = form_schema(client, applicationId=application_id)
    people = org_people(run, hit=True)
    primary, persons, documents = fill_organization(client, "AC-07", "B", application_id, schema,
                                                    org_ctx(run, IDS["clientBEmail"], f"Return Trading {run} LLC"), people)
    submit(client, "AC-07", application_id, "B")
    status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/withdrawal")
    check("AC-07", "审核中撤回 → 409 APPLICATION_NOT_WITHDRAWABLE", status == 409 and reply.get("code") == "APPLICATION_NOT_WITHDRAWABLE",
          f"HTTP {status} {problem(reply)}")
    found = find_case(ops, application_id)
    require("AC-07", "B 案件出现", found, f"applicationId={application_id}")
    case_id = found["caseId"]
    IDS["caseB1"] = case_id
    review = wait_screening(ops, case_id)
    _, _, _, runs, hits = flatten(review)
    pep = [h for h in hits if "PEP" in (h.get("listTypes") or []) and h.get("status") == "OPEN"]
    check("AC-13", "TESTHIT 关系人产生 PEP 命中（OPEN）", pep, f"hits={[(h.get('hitId'), h.get('caseId'), h.get('listTypes'), h.get('status'), h.get('riskLevel')) for h in hits]}")
    status, reply, blockers = try_approve(ops, case_id, partner)
    check("AC-06", "有 OPEN 命中 / 必需材料未接受时批准被拒", blocked(status, reply, "SCREENING_HIT_OPEN", blockers)
          and blocked(status, reply, "REQUIRED_MATERIAL_NOT_ACCEPTED", blockers), f"HTTP {status} {problem(reply)} blockers={blockers}")
    for hit in pep:
        status, reply = command(ops, "POST", f"/cases/{case_id}/screening-hits/{hit['hitId']}/resolution",
                                {"resolution": "FALSE_POSITIVE", "reasonCode": "NAME_ONLY_MATCH", "note": "acceptance: synthetic name",
                                 "onBehalfOfPartnerId": partner})
        check("AC-06", f"命中处置 FALSE_POSITIVE（hit {hit['hitId']}）", status == 200, f"HTTP {status} {problem(reply)}")
    accept_and_pass(ops, "AC-06", case_id, partner, "B1", reject={"BANK": "DOCUMENT_UNREADABLE"})
    _, evidence, checks, _, _ = flatten(review_of(case_detail(ops, case_id)))
    rejected = [e for e in evidence if e.get("status") == "REJECTED"]
    check("AC-06", "材料拒绝记原因码", rejected and all(e.get("rejectReasonCode") == "DOCUMENT_UNREADABLE" for e in rejected),
          f"{[(e.get('requirementCode'), e.get('status'), e.get('rejectReasonCode')) for e in evidence]}")
    accepted_codes = {e.get("requirementCode") for e in evidence if e.get("status") == "ACCEPTED"}
    passed_checks = {(c.get("caseId"), c.get("checkCode")) for c in checks if c.get("outcome") == "PASS"}
    errors = [{"requirementKey": "document.BANK", "subjectPartyId": primary, "code": "DOCUMENT_UNREADABLE",
               "reason": "Bank letter is not readable, please upload a clear copy.", "remediation": "UPLOAD_DOCUMENT"}]
    status, reply = command(ops, "POST", f"/cases/{case_id}/return",
                            {"reasonCode": "DOCUMENT_UNREADABLE", "errors": errors, "dueDays": 7,
                             "publicNote": "Please re-upload the bank letter.", "onBehalfOfPartnerId": partner})
    require("AC-07", "退回带需求级 errors[] 与 dueDays", status == 200, f"HTTP {status} {problem(reply)}")
    app = wait_app(client, application_id, ("RETURNED",), 90)
    review = app.get("review") or {}
    client_errors = review.get("errors") or []
    check("AC-07", "Client 收到需求级 errors[] 与期限", app.get("status") == "RETURNED" and review.get("outcome") == "RETURNED"
          and any(e.get("requirementKey") == "document.BANK" and e.get("code") == "DOCUMENT_UNREADABLE" and e.get("dueTime") for e in client_errors)
          and app.get("reviewDueTime"),
          f"status={app.get('status')} roundNo={review.get('roundNo')} dueTime={review.get('dueTime')} errors={client_errors}")
    status, items = requirements(client, application_id)
    due = [i for i in items.get("items") or [] if i.get("dueTime")]
    bank = [i for i in items.get("items") or [] if i.get("requirementKey") == "document.BANK" and i.get("subjectPartyId") == primary]
    check("AC-07", "需求清单的退回项带 dueTime（B3）：document.BANK 恰一条、审核员错误码、CLIENT 待办、期限 = 案件期限",
          status == 200 and len(bank) == 1 and bank[0].get("errorCode") == "DOCUMENT_UNREADABLE"
          and bank[0].get("remediationCode") == "UPLOAD_DOCUMENT" and bank[0].get("awaitingActionFrom") == "CLIENT"
          and bank[0].get("status") in ("CURRENTLY_DUE", "PAST_DUE") and bank[0].get("dueTime")
          and bank[0].get("dueTime") == review.get("dueTime"),
          f"bank={[(i.get('errorCode'), i.get('remediationCode'), i.get('awaitingActionFrom'), i.get('status'), i.get('dueTime')) for i in bank]} "
          f"caseDueTime={review.get('dueTime')} allDue={[(i.get('requirementKey'), i.get('dueTime')) for i in due]}")

    bank = documents.get((primary, "BANK"))
    if bank:
        status, _ = client.call("DELETE", f"/onboarding/v1/applications/{application_id}/documents/{bank}")
        check("AC-07", "Client 删除被拒材料引用", status == 204, f"HTTP {status} documentId={bank}")
    material = next((m for m in schema.get("materials") or [] if m.get("requirementCode") == "BANK"), None)
    if material:
        attach_document(client, "AC-07", application_id, primary, material, "B-PRIMARY-BANK-v2")
    submit(client, "AC-07", application_id, "B 重提")
    found = find_case(ops, application_id, previous_case_id=case_id)
    require("AC-07", "重提生成新案件", found, f"applicationId={application_id}")
    case2 = found["caseId"]
    IDS["caseB2"] = case2
    review2 = wait_screening(ops, case2)
    reqs2, evidence2, checks2, _, _ = flatten(review2)
    check("AC-07", "第 2 轮：round_no=2、previous_case_id = 第 1 轮案件", str(review2.get("roundNo")) == "2" and review2.get("previousCaseId") == case_id,
          f"caseId={case2} roundNo={review2.get('roundNo')} previousCaseId={review2.get('previousCaseId')}")
    reused_reqs = [r.get("requirementCode") for r in reqs2 if r.get("reusedFromCaseId") and r.get("status") == "ACCEPTED"]
    check("AC-07", "已 ACCEPTED 的要求复用（reusedFromCaseId）", "REGISTRATION" in reused_reqs and accepted_codes & set(reused_reqs),
          f"reused={reused_reqs} round1Accepted={sorted(accepted_codes)}")
    reused_checks = [(c.get("checkCode"), c.get("reusedFromCaseId")) for c in checks2 if c.get("reusedFromCaseId") and c.get("outcome") == "PASS"]
    check("AC-07", "有效期内 PASS 的检查复用、不重做", reused_checks and len(reused_checks) >= min(1, len(passed_checks)),
          f"reusedChecks={reused_checks}")
    bank_req = [r for r in reqs2 if r.get("requirementCode") == "BANK" and r.get("partyId") == primary]
    check("AC-07", "被拒材料在第 2 轮重新待核验", bank_req and bank_req[0].get("status") != "ACCEPTED" and not bank_req[0].get("reusedFromCaseId"),
          f"{[(r.get('status'), r.get('reusedFromCaseId')) for r in bank_req]}")

    status, reply = command(ops, "POST", f"/cases/{case2}/return",
                            {"reasonCode": "INFORMATION_INCOMPLETE", "errors": errors, "dueDays": 1,
                             "publicNote": "Second return for withdrawal acceptance.", "onBehalfOfPartnerId": partner})
    require("AC-07", "第 2 轮再退回", status == 200, f"HTTP {status} {problem(reply)}")
    wait_app(client, application_id, ("RETURNED",), 90)
    status, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/withdrawal")
    check("AC-07", "撤回 RETURNED 申请 → WITHDRAWN", status == 200 and reply.get("status") == "WITHDRAWN", f"HTTP {status} {problem(reply)}")
    deadline, status_case = time.time() + 90, None
    while time.time() < deadline:
        status_case = review_of(case_detail(ops, case2)).get("status")
        if status_case == "WITHDRAWN":
            break
        time.sleep(4)
    check("AC-07", "撤回后案件 WITHDRAWN（补偿 CLIENT_REVIEW_WITHDRAWAL → I03）", status_case == "WITHDRAWN", f"caseId={case2} status={status_case}")
    app = read_app(client, application_id)
    check("AC-07", "Client 申请为 WITHDRAWN", app.get("status") == "WITHDRAWN", f"status={app.get('status')} review={app.get('review')}")


def sc_codes_and_individual(ctx):
    """账号 C：无效 / 作废 / 用尽 / 过期码 422；平台自营 SA 个人 → Luhn 失败 → 撤回 DRAFT；AE 个人 SOLE_ESTABLISHMENT 填满。"""
    client, run = ctx["clientC"], ctx["run"]
    invalid = [("不存在的码", "ZZZZ-" + rand_digits(4)), ("已作废的码", ctx.get("codeRevoked")), ("已用尽的一次性码", ctx.get("codeSingle"))]
    if ctx.get("codeExpired"):
        invalid.append(("已过期的码", ctx["codeExpired"]))
    for label, code in invalid:
        if not code:
            skip("AC-04", f"{label} → 422", "前置签发未完成")
            continue
        status, reply = client.call("POST", "/onboarding/v1/applications",
                                    {"clientType": "ORGANIZATION", "jurisdictionCode": "AE", "organizationTypeCode": "LLC", "onboardingCode": code})
        check("AC-04", f"{label} → 422 ONBOARDING_CODE_INVALID", status == 422 and reply.get("code") == "ONBOARDING_CODE_INVALID",
              f"HTTP {status} {problem(reply)}")
    status, created = create_application(client, "AC-04", "C", "SA", "INDIVIDUAL")
    require("AC-04", "C 无码创建（平台自营）", status in (200, 201) and created.get("applicationId"), created)
    sa_id = created["applicationId"]
    IDS["appC_SA"] = sa_id
    app = read_app(client, sa_id)
    check("AC-04", "无码 → responsiblePartnerId 为空（平台自营）", not app.get("responsiblePartnerId"), f"responsiblePartnerId={app.get('responsiblePartnerId')}")
    status, schema = form_schema(client, applicationId=sa_id)
    payload = "1" + rand_digits(8)
    bad = payload + str((int(luhn_digit(payload)) + 1) % 10)
    SENSITIVE.add(bad)
    ctx_sa = {"run": run, "jurisdiction": "SA", "organizationType": None, "legalName": "", "email": IDS["clientCEmail"],
              "given": "Layla", "family": "Accept" + run[-4:], "identifierType": "SA_NATIONAL_ID", "identifierValue": bad}
    save_draft(client, "AC-02", sa_id, subject_values(schema, "PRIMARY", ctx_sa), "C SA 个人（错误 Luhn）")
    status, items = requirements(client, sa_id)
    codes = {i.get("errorCode") for i in items.get("items") or []}
    check("AC-02", "SA NID Luhn 校验位失败 → IDENTIFIER_CHECKSUM_FAILED", "IDENTIFIER_CHECKSUM_FAILED" in codes,
          f"codes={sorted(c for c in codes if c)}")
    app = read_app(client, sa_id)
    check("AC-02", "草稿回显 identifierCheckResult", dig(app, "values", "identifier", "identifierCheckResult") == "IDENTIFIER_CHECKSUM_FAILED",
          f"identifier={dig(app, 'values', 'identifier')}")
    status, reply = client.call("POST", f"/onboarding/v1/applications/{sa_id}/withdrawal")
    check("AC-07", "撤回 DRAFT → WITHDRAWN", status == 200 and reply.get("status") == "WITHDRAWN", f"HTTP {status} {problem(reply)}")
    status, again = client.call("POST", f"/onboarding/v1/applications/{sa_id}/withdrawal")
    check("AC-07", "重复撤回返回原结果", status == 200 and again.get("withdrawnAt") == reply.get("withdrawnAt"), f"HTTP {status}")

    status, created = create_application(client, "AC-14", "C", "AE", "INDIVIDUAL", "SOLE_ESTABLISHMENT")
    require("AC-14", "C AE 个人 SOLE_ESTABLISHMENT 申请（Q6）", status in (200, 201) and created.get("applicationId"), created)
    ae_id = created["applicationId"]
    IDS["appC_AE"] = ae_id
    status, schema = form_schema(client, applicationId=ae_id)
    require("AC-14", "C 冻结 SOLE_ESTABLISHMENT Schema", status == 200 and schema.get("organizationTypeCode") == "SOLE_ESTABLISHMENT",
            f"schemaId={schema.get('schemaId')} organizationTypeCode={schema.get('organizationTypeCode')}")
    reused = (ctx.get("passportsA") or [None])[0]
    ctx_ae = {"run": run, "jurisdiction": "AE", "organizationType": "SOLE_ESTABLISHMENT", "legalName": "", "email": IDS["clientCEmail"],
              "given": "Hamad", "family": "Accept" + run[-4:], "identifierType": "PASSPORT",
              "identifierValue": reused or "P" + rand_digits(8), "extra": {"attributes": {"AE_TRADE_LICENSE_NUMBER": "SE-" + rand_digits(6)}}}
    save_draft(client, "AC-14", ae_id, subject_values(schema, "PRIMARY", ctx_ae), "C AE 个人")
    if reused:
        status, items = requirements(client, ae_id)
        codes = {i.get("errorCode") for i in items.get("items") or []}
        if "IDENTIFIER_CONFLICT" in codes:
            check("AC-03", "同一证件号绑定不同 Party → IDENTIFIER_CONFLICT（需求清单）", True, f"codes={sorted(c for c in codes if c)}")
        else:
            record("AC-03", "同一证件号绑定不同 Party → IDENTIFIER_CONFLICT（需求清单）", "INFO",
                   f"草稿阶段未报冲突（codes={sorted(c for c in codes if c)}）；冲突在正式提交时判定，由 SubmitClientApplicationTest.test_identityConflict 覆盖")
        fresh = "P" + rand_digits(8)
        SENSITIVE.add(fresh)
        save_draft(client, "AC-14", ae_id, {"identifier": {**identifier_values(schema, "PRIMARY", {**ctx_ae, "identifierValue": fresh})}}, "C 换新护照")
    for _, material, owner in material_targets(schema, read_app(client, ae_id)["subjectPartyId"], []):
        attach_document(client, "AC-14", ae_id, read_app(client, ae_id)["subjectPartyId"], material, f"C-{owner}-{material['requirementCode']}")
    declare_all(client, "AC-14", ae_id, schema)
    status, items = requirements(client, ae_id)
    check("AC-14", "AE 个人 SOLE_ESTABLISHMENT 填满后需求清单 ready", status == 200 and items.get("ready") is True,
          f"ready={items.get('ready')} open={open_items(items)[:10]}")
    masked_only("AC-03", "C Client 读回", read_app(client, ae_id))
    status, reply = client.call("POST", f"/onboarding/v1/applications/{ae_id}/withdrawal")
    check("AC-07", "C AE 个人草稿撤回（不留未完成申请）", status == 200 and reply.get("status") == "WITHDRAWN", f"HTTP {status} {problem(reply)}")


def sd_revoke(ctx):
    ops, client_id = ctx["opsA"], ctx.get("clientIdA")
    if not client_id:
        skip("AC-09", "资格撤销 → Client SUSPENDED", "A 未批准（前置失败或无复核人）")
        return
    status, page = ops.get(f"/qualification-admin/v1/qualifications?roleType=CLIENT&roleId={client_id}&page=1&size=10")
    items = [i.get("qualification") or i for i in page.get("items") or []]
    approved = [q for q in items if q.get("status") == "APPROVED"]
    require("AC-09", "运营 资格列表含 A 的 APPROVED 资格", status == 200 and approved, f"HTTP {status} {[(q.get('qualificationId'), q.get('status'), q.get('version')) for q in items]}")
    qualification = approved[0]
    status, reply = ops.call("POST", f"/qualification-admin/v1/qualifications/{qualification['qualificationId']}/revocation",
                             {"expectedVersion": str(qualification["version"]), "reasonCode": "MANUAL_REVOKED",
                              "basisNote": f"onboarding slice 1 acceptance {ctx['run']}: manual revocation"})
    require("AC-09", "手工撤销资格", status == 200 and reply.get("status") == "REVOKED", f"HTTP {status} {problem(reply)}")
    IDS["revokedQualificationId"] = qualification["qualificationId"]
    deadline, summary = time.time() + 90, {}
    while time.time() < deadline:
        _, page = ops.get(f"/client-admin/v1/clients/{client_id}")
        summary = dig(page, "client", "client") or {}
        if summary.get("status") == "SUSPENDED":
            break
        time.sleep(3)
    check("AC-09", "QualificationChanged(REVOKED) → Client SUSPENDED", summary.get("status") == "SUSPENDED", f"clientId={client_id} status={summary.get('status')}")


def se_logs_bundles_legacy(ctx):
    values = sorted(SENSITIVE)
    for service in ("client-master", "compliance"):
        try:
            scan_raw_values("AC-03", service, values)
        except Exception as failure:  # noqa: BLE001
            check("AC-03", f"{service} 库扫描", False, f"{type(failure).__name__}: {str(failure)[:200]}")
    if not values:
        skip("AC-03", "本次个人证件行 Token / 掩码与日志核对", "本次未提交个人证件（只跑 SE）")
    try:
        if not values:
            raise Stop("skip")
        rows = sql("client-master", "SELECT COUNT(*), SUM(identifier_vault_token IS NOT NULL), SUM(identifier_masked_value LIKE '****%') "
                                    "FROM t_party_profile_identifier WHERE identifier_type IN ('PASSPORT','SA_NATIONAL_ID') "
                                    f"AND create_time >= '{ctx['startedSql']}'")
        total, tokens, masked = (int(v or 0) for v in rows[0])
        check("AC-03", "本次个人证件行只有 Vault Token + 末 4 位掩码", total and tokens == total and masked == total, f"rows={total} tokens={tokens} masked={masked}")
    except Stop:
        pass
    except Exception as failure:  # noqa: BLE001
        check("AC-03", "证件行 Token / 掩码核对", False, f"{type(failure).__name__}: {str(failure)[:200]}")
    if not values:
        pass
    elif E.TARGET == "test":
        leaked = []
        for service in ("client-master-service", "compliance-service", "client-console-bff", "operations-console-bff", "channel-connector-service"):
            result = subprocess.run(["docker", "logs", "--since", ctx["startedIso"], f"mor-test-{service}-1"], capture_output=True, text=True)
            text = result.stdout + result.stderr
            leaked += [service for v in values if v in text]
        check("AC-03", "服务日志不含证件原值（5 个容器，自本次开始）", not leaked, f"leakedIn={sorted(set(leaked))} values={len(values)}")
    elif ctx["args"].log_dir:
        leaked = []
        for path in Path(ctx["args"].log_dir).rglob("*.log"):
            if path.stat().st_mtime >= ctx["startedEpoch"]:
                text = path.read_text(errors="ignore")
                leaked += [path.name for v in values if v in text]
        check("AC-03", f"本机日志不含证件原值（{ctx['args'].log_dir}）", not leaked, f"leakedIn={sorted(set(leaked))}")
    else:
        skip("AC-03", "服务日志不含证件原值", "本机未给 --log-dir；MQ 载荷不含原值由 I01 只传 Token（DraftIdentifierVaultTest、ClientReviewRegistrationApiAutoTest）覆盖")

    bundle_apps = (("client-console", E.CLIENT, "/api/client/i18n/v1/manifest"),
                   ("operations-console", E.OPERATIONS, "/api/operations/i18n/v1/manifest"))
    if E.TARGET != "test":
        skip("AC-14", "onboardingSchema 语言包", "本机未发布语言包；只在 mor-test 验收")
        bundle_apps = ()
    for app, origin, manifest_path in bundle_apps:
        for locale in ("en", "zh-CN"):
            status, manifest = public_json(origin, f"{manifest_path}?locale={locale}")
            bundle = dig(manifest, "bundles", locale, BUNDLE_NAMESPACE) or {}
            texts = {}
            if bundle.get("url"):
                url = bundle["url"] if bundle["url"].startswith("http") else origin + bundle["url"]
                _, texts = public_json(origin, url, absolute=True)
            label = texts.get(bundle_key("onboardingSchema.jurisdiction.AE")) if isinstance(texts, dict) else None
            label = label.get("text") if isinstance(label, dict) else label
            check("AC-15" if app == "operations-console" else "AC-14", f"{app} {locale} {BUNDLE_NAMESPACE} 语言包（AE 辖区标签已映射）",
                  status == 200 and bundle.get("url") and label,
                  f"releaseNo={manifest.get('releaseNo') if isinstance(manifest, dict) else None} keys={len(texts) if isinstance(texts, dict) else 0} AE={label}")

    ops = ctx["opsA"]
    try:
        rows = sql("client-master", "SELECT c.client_id, c.jurisdiction_code FROM t_client c JOIN t_client_application a ON a.client_id = c.client_id "
                                    "WHERE c.status = 'ACTIVE' AND a.schema_id IS NULL ORDER BY c.client_id LIMIT 1")
    except Exception as failure:  # noqa: BLE001
        rows = []
        check("MIGR", "只读 SQL 选取遗留 ACTIVE Client", False, f"{type(failure).__name__}: {str(failure)[:200]}")
    if rows:
        client_id, jurisdiction = rows[0]
        status, detail = ops.get(f"/client-admin/v1/clients/{client_id}")
        text = json.dumps(detail)
        summary = dig(detail, "client", "client") or {}
        legacy_apps = [a for a in dig(detail, "client", "applications") or [] if not a.get("schemaId")]
        check("MIGR", "遗留 ACTIVE Client（申请 schema_id 为空）运营详情 200、证件只有掩码",
              status == 200 and summary.get("status") == "ACTIVE" and legacy_apps and not RAW_KEY.search(text),
              f"clientId={client_id} jurisdiction={jurisdiction} HTTP {status} status={summary.get('status')} "
              f"legacyApplications={[(a.get('applicationId'), a.get('status'), a.get('jurisdictionCode')) for a in legacy_apps]} "
              f"identifierMasked={masked_values(text)[:3]} rawKeys={len(RAW_KEY.findall(text))}")
        IDS["legacyClientId"] = client_id
    else:
        skip("MIGR", "遗留 ACTIVE Client 运营详情", "该环境无申请 schema_id 为空的 ACTIVE Client；由 OnboardingAdminQueryTest.test_legacyClientReadable 覆盖")


def public_json(origin, path_or_url, absolute=False):
    url = path_or_url if absolute else origin + path_or_url
    request = urllib.request.Request(url, headers={"Origin": origin, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, context=E.TLS, timeout=30) as response:
            return response.status, json.loads(response.read().decode() or "{}")
    except urllib.error.HTTPError as failure:
        return failure.code, {}
    except (urllib.error.URLError, ValueError) as failure:
        return 0, {"error": str(failure)[:200]}


# ---------------------------------------------------------------------------------------------------------------- 复核人

def reviewer(ctx):
    email, password = os.environ.get("ONB_REVIEWER_EMAIL"), os.environ.get("ONB_REVIEWER_PASSWORD")
    if email and password:
        session = ops_login(email, password)
        record("SETUP", "复核人 B 登录（环境变量）", "PASS", "accessToken obtained")
        return session
    if not ctx["args"].create_reviewer:
        return None
    stored = STATE / f"reviewer-{ctx['args'].env}.json"
    if stored.exists():
        saved = json.loads(stored.read_text())
        try:
            session = ops_login(saved["email"], saved["password"])
            IDS.update(reviewerAccountId=saved.get("accountId"), reviewerRoleId=saved.get("roleId"))
            record("SETUP", "复核人 B 登录（复用已建的合成复核人）", "PASS", f"accountId={saved.get('accountId')}")
            return session
        except Stop as stop:
            record("SETUP", "复用合成复核人失败，改为新建", "INFO", str(stop))
    admin, run = ctx["opsA"], ctx["run"]
    status, options = admin.get("/iam/v1/role-permission-options")
    permission_ids = set()

    def walk(nodes, inside):
        for node in nodes or []:
            here = inside or "CLIENT_ONBOARDING" in (node.get("code") or "")
            if here and node.get("permissionId"):
                permission_ids.add(node["permissionId"])
            walk(node.get("children"), here)

    walk(options.get("nodes"), False)
    require("SETUP", "复核人权限（CLIENT_ONBOARDING 资源下全部权限）", status == 200 and permission_ids, f"HTTP {status} permissions={len(permission_ids)}")
    reason = f"onboarding slice 1 acceptance {run}: synthetic four-eyes reviewer"
    status, role = admin.call("POST", "/iam/v1/roles", {"roleCode": f"ONBOARDING_ACCEPTANCE_REVIEWER_{run}", "roleName": f"onboarding-acceptance-reviewer {run} (acceptance only)",
                                                        "permissionIds": sorted(permission_ids), "reason": reason})
    require("SETUP", "IAM 新建合成复核角色", status in (200, 201) and role.get("roleId"), f"HTTP {status} {problem(role)}")
    email = f"onboarding-acceptance-reviewer-{run.lower()}@example.test"
    initial = "Onb init " + pysecrets.token_urlsafe(16) + "!"
    final = "Onb rev " + pysecrets.token_urlsafe(16) + "!"
    status, account = admin.call("POST", "/iam/v1/accounts", {"email": email, "initialPassword": initial, "roleIds": [role["roleId"]], "reason": reason})
    require("SETUP", "IAM 新建合成复核人", status in (200, 201) and account.get("accountId"), f"HTTP {status} {problem(account)}")
    IDS.update(reviewerRoleId=role["roleId"], reviewerAccountId=account["accountId"], reviewerEmail=email)
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE, 0o700)
    secret_file = STATE / f"reviewer-{ctx['args'].env}.json"
    secret_file.write_text(json.dumps({"email": email, "password": final, "accountId": account["accountId"], "roleId": role["roleId"]}))
    os.chmod(secret_file, 0o600)
    first = ops_login(email, initial)
    status, _ = first.call("POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": final}, key=False)
    session = ops_login(email, final)
    check("SETUP", "合成复核人首登改密并登录", bool(session.token), f"accountId={account['accountId']} passwordChange=HTTP {status}")
    return session


# ---------------------------------------------------------------------------------------------------------------- 主流程

AUTO_ONLY = {
    "AC-01 CLOSED 辖区": ["OnboardingFormSchemaTest.test_notAccepted"],
    "AC-02 完整错误码矩阵（格式 / EG NID / 到期 / UBO 超 100 / 二选一）": ["SubmitClientApplicationTest.test_individualRuleMatrix",
                                                                    "SubmitClientApplicationTest.test_organizationRuleMatrix",
                                                                    "DraftIdentifierVaultTest.test_formatAndChecksumRecorded"],
    "AC-03 IDENTIFIER_CONFLICT（提交时）、I01 只传 Token、MQ 无原值": ["SubmitClientApplicationTest.test_identityConflict",
                                                              "DraftIdentifierVaultTest.test_vaultRequiredIdentifierStoresTokenOnly",
                                                              "ClientReviewRegistrationApiAutoTest.test_normalFlow_organization"],
    "AC-05 programs/resolve 无程序失败关闭": ["ProgramResolutionApiAutoTest.test_noDataFound", "ClientReviewRegistrationApiAutoTest.test_noDataFound_program"],
    "AC-07 退回超期 EXPIRED → 申请 WITHDRAWN": ["ClientReviewReturnApiAutoTest.test_normalFlow_expiry", "ClientReviewLifecycleV2Test.test_returnedThenExpired"],
    "AC-11 ZZ 国家包全链 + 字面量禁令": ["NewJurisdictionByDataTest.test_zzFullChain", "ZzCountryPackAutoTest.test_normalFlow_zzFullChain",
                                  "JurisdictionLiteralAutoTest.test_noJurisdictionLiterals", "ComplianceHardcodingGuardTest.test_businessRuleViolation_noCatalogLiteralsInProduction"],
    "AC-12 程序 / Schema 新版本不影响在途": ["ClientReviewCaseDetailApiAutoTest.test_edgeCase_versionFrozen", "OnboardingFormSchemaTest.test_byApplication"],
    "AC-13 Mock 只在 local / mor-test": ["渠道 S_SCREENING_MOCK 只在两环境登记（assets/harness/screening_channel_setup.py）"],
}
SCENARIOS = ["S0", "S1", "SA", "SB", "SC", "SD", "SE"]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--env", choices=("local", "mor-test"), required=True)
    parser.add_argument("--create-reviewer", action="store_true", help="经 IAM 新建合成复核人（四眼批准）")
    parser.add_argument("--partner-id", help="入驻码 PayFac（缺省取第一个 OE_PROVIDER Partner）")
    parser.add_argument("--db-suffix", help="本机库名后缀（缺省按 e2e_http：local=_v2e2e，mor-test 无）")
    parser.add_argument("--log-dir", help="本机服务日志目录（AC-03 日志核对）")
    parser.add_argument("--provision-client", metavar="LABEL",
                        help="只注册并验证一个 Client 账号，凭据写 $ONB_STATE/<label>-client.json（0600），不跑验收")
    parser.add_argument("--only", help="只跑这些场景（逗号分隔）：" + ",".join(SCENARIOS))
    args = parser.parse_args()

    os.environ["E2E_TARGET"] = "test" if args.env == "mor-test" else "local"
    sys.path.insert(0, str(V2_E2E))
    global E, V2
    import e2e_http as E  # noqa: E402
    import acceptance_flow as V2  # noqa: E402  （只用 verification_link / presigned）
    if args.db_suffix is not None:
        for service, (database, user, key) in list(E.DATABASES.items()):
            if service != "vault":
                E.DATABASES[service] = (f"mor_{service.replace('-', '_')}{args.db_suffix}", user, key)

    started = datetime.now(timezone.utc)
    run = started.strftime("%m%d%H%M%S")
    if args.provision_client:
        return provision_client(args, run)
    ctx = {"args": args, "run": run, "startedIso": started.strftime("%Y-%m-%dT%H:%M:%SZ"),
           "startedSql": started.strftime("%Y-%m-%d %H:%M:%S"), "startedEpoch": started.timestamp()}
    selected = args.only.split(",") if args.only else SCENARIOS
    print(f"env={args.env} run={run} scenarios={selected}", flush=True)
    try:
        ctx["opsA"] = ops_login(E.secrets()["E2E_ADMIN_EMAIL"], E.secrets()["E2E_ADMIN_PASSWORD"])
        record("SETUP", "运营 A（管理员）登录", "PASS", f"accountId={ctx['opsA'].account_id}")
        ctx["opsB"] = reviewer(ctx)
        ctx["clientA"] = client_register("A", run)
    except Stop as stop:
        record("SETUP", "前置", "FAIL", str(stop))
        return finish(args, started, 1)

    plan = [("S0", s0_config_and_codes), ("S1", s1_form_schema), ("SA", sa_main_flow), ("SB", sb_return_flow),
            ("SC", sc_codes_and_individual), ("SD", sd_revoke), ("SE", se_logs_bundles_legacy)]
    for name, scenario in plan:
        if name not in selected:
            continue
        print(f"\n==== {name} {scenario.__doc__.strip().splitlines()[0] if scenario.__doc__ else scenario.__name__}", flush=True)
        try:
            if name == "SB":
                ctx["clientB"] = client_register("B", run)
            if name == "SC":
                ctx["clientC"] = client_register("C", run)
            if name in ("SA", "SB", "SC") and "partner" not in ctx:
                skip(name, "场景", "依赖 S0 签发的入驻码（未运行 S0）")
                continue
            scenario(ctx)
        except Stop as stop:
            record(name, "场景中止", "FAIL" if "no reviewer" not in str(stop) else "SKIP", str(stop))
        except Exception as failure:  # noqa: BLE001  记录脚本 / 产品异常后继续下一场景
            record(name, "场景异常", "FAIL", f"{type(failure).__name__}: {str(failure)[:300]}")
    failed = any(r["verdict"] == "FAIL" for r in RESULTS)
    return finish(args, started, 1 if failed else 0)


def provision_client(args, run):
    """注册一个 Client、按既有 e2e 机制验证邮箱、登录确认，凭据只写本机 0600 文件。"""
    label = re.sub(r"[^A-Za-z0-9-]", "", args.provision_client)
    try:
        session = client_register(label, run)
    except Stop as stop:
        print(f"FAIL 预置 Client {label}: {stop}")
        return 1
    status, context = session.get("/auth/v1/context")
    if status != 200 or context.get("accountId") != session.account_id:
        print(f"FAIL 预置 Client {label} 登录确认 HTTP {status}")
        return 1
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE, 0o700)
    target = STATE / f"{label}-client.json"
    target.write_text(json.dumps({"env": args.env, "email": session.email, "password": session.password,
                                  "accountId": session.account_id, "emailVerified": True,
                                  "createdAt": datetime.now(timezone.utc).isoformat(),
                                  "purpose": "onboarding slice 1 ui-smoke (acceptance-only test client)"}, indent=1) + "\n")
    os.chmod(target, 0o600)
    print(f"PASS 预置 Client {label}: accountId={session.account_id} 已验证、登录确认 → {target}（0600）")
    return 0


def finish(args, started, code):
    finished = datetime.now(timezone.utc)
    out = REQ / "verification" / f"acceptance-{args.env}-{started.strftime('%Y%m%dT%H%M%SZ')}.json"
    summary = {v: sum(1 for r in RESULTS if r["verdict"] == v) for v in ("PASS", "FAIL", "SKIP", "INFO")}
    out.write_text(json.dumps({"env": args.env, "startedAt": started.isoformat(), "finishedAt": finished.isoformat(),
                               "summary": summary, "ids": IDS, "results": RESULTS, "autoTestOnly": AUTO_ONLY},
                              ensure_ascii=False, indent=1, default=str) + "\n")
    print(f"\n==== 汇总 {summary} → {out}")
    for item in RESULTS:
        if item["verdict"] == "FAIL":
            print(f"FAIL {item['ac']} {item['step']}")
    return code


if __name__ == "__main__":
    sys.exit(main())
