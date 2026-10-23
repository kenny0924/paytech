"""Client 入驻 API 级端到端验收（阶段 5 V2 本机全链路，任务卡 14）：经 Nginx HTTPS 入口走注册→资料→材料→提交→运营退回→（可选）重提批准。

由 requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/acceptance_flow.py 复制改写，只跑本机：
链路 Nginx → Gateway → Client / 运营 BFF → client-master（原 CCT）/ compliance（原 Risk 入驻审核）/ Platform。
业务步骤只经 HTTP 入口（Client、Operations、Mailpit、MinIO 预签名地址）；V2 新增一步只读核对：运营退回 / 批准后，
compliance 发布的 ClientReviewDecided 信封（compliance.events）经 client-master.compliance.p0..p3 被 client-master 消费
（本步 vhost 中分区队列清空、每个分区有消费者、DLQ 为空，mor_client_master_v2e2e.t_consumed_event 计数增加）。
输出每步 PASS/FAIL 与关键证据（状态码、ID、Problem code、requestId）；不输出密码、令牌、Cookie 或完整预签名 URL。

用法：python3 acceptance_flow.py [--approve]
"""
import http.cookiejar
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

from e2e_http import CLIENT, MAILPIT, OPERATIONS, TLS, Failure, Operations, mint_signup_link, rabbit_queues, secrets, sql

PDF = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
       b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")
RESULTS = []


class Stop(Exception):
    """当前分支遇到不能由脚本修复的失败，停止后续依赖步骤。"""


def record(step, ok, evidence):
    RESULTS.append((step, "PASS" if ok else "FAIL", evidence))
    print(("PASS " if ok else "FAIL ") + step + " | " + evidence, flush=True)
    if not ok:
        raise Stop(step)


def problem(failure):
    """Problem 摘要：HTTP 状态、code、requestId、字段错误；不含请求体。"""
    try:
        body = json.loads(failure.body)
    except (ValueError, TypeError):
        return f"HTTP {failure.status} body={failure.body[:200]!r}"
    errors = [f"{e.get('field')}:{e.get('message')}" for e in body.get("errors") or []]
    return (f"HTTP {failure.status} code={body.get('code')} requestId={body.get('requestId')} "
            f"detail={str(body.get('detail'))[:200]}" + (f" errors={errors}" if errors else ""))


class Client:
    """Client 站点会话：Cookie Jar 保存 BFF 设置的 HttpOnly Refresh Cookie，Access Token 只留在内存。"""

    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPSHandler(context=TLS), urllib.request.HTTPCookieProcessor(self.jar))
        self.token = None

    def call(self, path, method="GET", body=None, key=None, auth=True):
        headers = {"Origin": CLIENT, "Accept": "application/json"}
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        if key:
            headers["Idempotency-Key"] = key
        if auth and self.token:
            headers["Authorization"] = "Bearer " + self.token
        for attempt in range(12):
            request = urllib.request.Request(CLIENT + "/api/client" + path, data=data, headers=headers, method=method)
            try:
                with self.opener.open(request, timeout=30) as response:
                    raw = response.read().decode()
                    return response.status, (json.loads(raw) if raw else None)
            except urllib.error.HTTPError as failure:
                if failure.code == 429 and attempt < 11:
                    time.sleep(int(failure.headers.get("Retry-After") or 10))
                    continue
                raise Failure(failure.code, failure.read().decode()) from None

    def command(self, path, method, body=None):
        return self.call(path, method, body, key=str(uuid.uuid4()))

    def refresh_cookie_present(self):
        return any(c.name == "__Secure-client-refresh" for c in self.jar)


def mailpit(path):
    with urllib.request.urlopen(MAILPIT + path, timeout=10) as response:
        return json.loads(response.read().decode())


def verification_link(email, attempts=30):
    """在 Mailpit 中按收件人找到验证邮件，从链接片段读取 challengeNo/secret。"""
    query = urllib.parse.quote(f'to:"{email}"')
    for _ in range(attempts):
        found = mailpit(f"/api/v1/search?query={query}&limit=5").get("messages") or []
        for message in found:
            detail = mailpit(f"/api/v1/message/{message['ID']}")
            text = (detail.get("Text") or "") + (detail.get("HTML") or "")
            match = re.search(r"verify-email#challengeNo=([0-9a-f-]{36})(?:&amp;|&)secret=([A-Za-z0-9_-]{43})", text)
            if match:
                return message["ID"], match.group(1), match.group(2)
        time.sleep(1)
    return None, None, None


def presigned(method, url, headers=None, data=None):
    """访问对象存储预签名地址（本机自签名或 test CA 签发的 MinIO）。"""
    host = urllib.parse.urlsplit(url).netloc
    request = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    try:
        with urllib.request.urlopen(request, context=TLS, timeout=30) as response:
            return response.status, response.read(), host
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read()[:300], host


# ---------------------------------------------------------------- 1. 注册与登录
def register_and_login(client, email, password):
    since = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(time.time() - 5))
    status, reply = client.call("/auth/v1/register", "POST", {"email": email, "password": password}, auth=False)
    record("A01 注册", status == 202 and reply.get("accepted") is True, f"HTTP {status} {reply}")
    message_id, challenge, secret = verification_link(email, 10)
    if not challenge:
        # 2026-10-15：Platform 认证邮件改经 Delivery（本机无 SES），收不到邮件时在本机库铸造已知秘密。
        challenge, secret = mint_signup_link(email, since)
        message_id = "minted-locally"
    record("验证链接（Mailpit 或本机铸造）", bool(challenge), f"messageId={message_id} challengeNo={challenge}")
    status, reply = client.call("/auth/v1/email-verifications", "POST", {"challengeNo": challenge, "secret": secret}, auth=False)
    record("A03 邮箱验证", status == 200 and reply.get("verified") is True, f"HTTP {status} {reply}")
    status, grant = client.call("/auth/v1/login", "POST", {"email": email, "password": password}, auth=False)
    client.token = grant["accessToken"]
    context = grant["context"]
    record("A04 登录", status == 200 and context.get("emailVerified") is True,
           f"HTTP {status} accountId={context.get('accountId')} contextType={context.get('contextType')} "
           f"refreshCookie={client.refresh_cookie_present()}")
    status, context = client.call("/auth/v1/context")
    record("A09 上下文", status == 200 and context.get("accountId") == grant["context"]["accountId"],
           f"HTTP {status} emailMasked={context.get('emailMasked')} allowedActions={context.get('allowedActions')}")
    status, refreshed = client.call("/auth/v1/refresh", "POST", {}, auth=False)
    client.token = refreshed["accessToken"]
    record("A05 刷新（Cookie）", status == 200 and refreshed["context"]["accountId"] == context["accountId"], f"HTTP {status}")
    return context


# ---------------------------------------------------------------- 2. 申请与资料
def start_application(client):
    status, start = client.call("/onboarding/v1/start")
    record("C01 Start", status == 200, f"HTTP {status} activeApplicationId={start.get('activeApplicationId')} "
                                       f"applications={len(start.get('applications') or [])}")
    status, receipt = client.command("/onboarding/v1/applications", "POST", {"clientType": "INDIVIDUAL"})
    record("C04 创建个人申请", status in (200, 201) and receipt.get("applicationId"),
           f"HTTP {status} applicationId={receipt.get('applicationId')} applicationNo={receipt.get('applicationNo')} "
           f"operation={receipt.get('operation')}")
    return receipt["applicationId"]


def form_options(client):
    status, options = client.call("/onboarding/v1/form-options?clientType=INDIVIDUAL")
    record("C03 表单目录", status == 200 and options.get("catalogStatus") == "AVAILABLE"
           and options.get("identifierTypes") and options.get("businessModels"),
           f"HTTP {status} catalogStatus={options.get('catalogStatus')} identifierTypes="
           f"{[(i['code'], i.get('issuingCountryMode')) for i in options.get('identifierTypes') or []]} "
           f"businessModels={[b['code'] for b in options.get('businessModels') or []]} "
           f"requirementsStatus={options.get('requirementsStatus')} "
           f"requirements={[(r['requirementCode'], r['requiredFlag']) for r in options.get('requirements') or []]}")
    return options


def draft(client, application_id, values, step):
    dirty = [g + "." + f for g, fields in values.items() for f in fields]
    try:
        status, receipt = client.command(f"/onboarding/v1/applications/{application_id}/draft", "PATCH",
                                         {"dirtyFields": dirty, "values": values})
    except Failure as failure:
        record(step, False, problem(failure))
    record(step, status == 200 and receipt.get("operation") == "SAVED", f"HTTP {status} fields={len(dirty)} operation={receipt.get('operation')}")


def fill_profile(client, application_id, options, stamp):
    identifier = options["identifierTypes"][0]
    identity = {"identifierType": identifier["code"], "identifierValue": f"ACC{stamp}",
                "validFrom": "2020-01-01", "validTo": "2035-01-01"}
    if identifier.get("issuingCountryMode") == "REQUIRED":
        identity["issuingCountry"] = "AE"
    draft(client, application_id, {
        "individual": {"givenName": "Accept", "familyName": "Tester", "birthDate": "1990-01-15",
                       "nationalityCountry": "AE", "residenceCountry": "AE"},
        "address": {"addressLine1": "1 Acceptance Street", "city": "Dubai", "stateOrProvince": "Dubai",
                    "postalCode": "00000", "countryCode": "AE"},
        "identifier": identity,
        "business": {"businessModelCode": options["businessModels"][0]["code"], "websiteUrl": "https://example.test",
                     "expectedVolume": "50000.00000000", "expectedTicket": "100.00000000", "currency": "USD",
                     "businessDescription": "通过自有网站销售家居用品（验收测试数据）"},
    }, "C06 保存资料（基础/地址/证件/经营）")
    # 单独保存联系邮箱和电话：经 CCT → Vault STABLE protect。
    draft(client, application_id, {"contact": {"email": f"contact-{stamp}@example.test", "phone": "+971501234567"}},
          "C06 保存联系邮箱/电话（CCT→Vault STABLE）")


def read_application(client, application_id):
    status, app = client.call(f"/onboarding/v1/applications/{application_id}")
    if status != 200:
        raise Stop("C05")
    return app


# ---------------------------------------------------------------- 3. 材料
def upload_material(client, application_id, subject_party_id, requirement):
    code = requirement["requirementCode"]
    try:
        # 2026-10-22 文件上传原则 P1（73a2460d）：上传只声明文件本身，不带申请与主体
        status, grant = client.command("/onboarding-files/v1/uploads", "POST", {
            "filename": f"{code.lower()}.pdf", "mimeType": "application/pdf", "sizeBytes": str(len(PDF))})
    except Failure as failure:
        record(f"F01 上传授权 {code}", False, problem(failure))
    authorization = grant["authorization"]
    record(f"F01 上传授权 {code}", status in (200, 201) and authorization.get("method") == "PUT",
           f"HTTP {status} uploadId={grant.get('uploadId')} headers={sorted(authorization.get('headers') or {})} "
           f"expiresAt={authorization.get('expiresAt')}")
    put_status, put_body, host = presigned("PUT", authorization["url"], authorization.get("headers"), PDF)
    record(f"PUT 对象存储 {code}", 200 <= put_status < 300, f"HTTP {put_status} host={host}"
           + ("" if 200 <= put_status < 300 else f" body={put_body[:200]!r}"))
    try:
        status, receipt = client.command(f"/onboarding-files/v1/uploads/{grant['uploadId']}/completion", "POST")
    except Failure as failure:
        record(f"F02 上传完成 {code}", False, problem(failure))
    file_id, file_address = receipt.get("fileId"), receipt.get("fileAddress")
    record(f"F02 上传完成 {code}", status in (200, 201) and file_id and file_address == f"/files/v1/{file_id}",
           f"HTTP {status} fileId={file_id} fileAddress={file_address}")
    deadline, meta = time.time() + 75, {}
    while time.time() < deadline:
        _, meta = client.call(f"/onboarding-files/v1/files/{file_id}")
        if meta.get("scanStatus") not in ("PENDING_SCAN", "SCANNING"):
            break
        time.sleep(3)
    record(f"F03 扫描结果 {code}", meta.get("scanStatus") == "AVAILABLE" and meta.get("objectStatus") == "ACTIVE",
           f"scanStatus={meta.get('scanStatus')} objectStatus={meta.get('objectStatus')} mimeType={meta.get('mimeType')} "
           f"allowedActions={meta.get('allowedActions')}")
    try:
        status, document = client.command(f"/onboarding/v1/applications/{application_id}/documents", "POST", {
            "subjectPartyId": subject_party_id, "requirementCode": code,
            "documentType": requirement["documentType"], "fileAddress": file_address})
    except Failure as failure:
        record(f"C12 关联材料 {code}", False, problem(failure))
    record(f"C12 关联材料 {code}", status in (200, 201), f"HTTP {status} documentId={document.get('documentId')}")
    return file_id


def upload_required(client, application_id, app):
    targets = [(s["subjectPartyId"], r) for s in app.get("materialSubjects") or []
               for r in s.get("requirements") or [] if r.get("requiredFlag")]
    record("C05 材料要求", bool(targets),
           f"materialSubjects={[(s['subjectPartyId'], s.get('requirementsStatus'), [r['requirementCode'] for r in s.get('requirements') or []]) for s in app.get('materialSubjects') or []]}")
    return [upload_material(client, application_id, subject, requirement) for subject, requirement in targets]


# ---------------------------------------------------------------- 4. 提交
def submit(client, application_id, label):
    status, check = client.call(f"/onboarding/v1/applications/{application_id}/submission-check")
    record(f"C16 提交核对{label}", status == 200 and check.get("ready") is True, f"HTTP {status} ready={check.get('ready')} issues={check.get('issues')}")
    try:
        status, submission = client.call(f"/onboarding/v1/applications/{application_id}/submissions", "POST")
    except Failure as failure:
        record(f"C14 提交{label}", False, problem(failure))
    record(f"C14 提交{label}", status == 202 and submission.get("status") == "SUBMITTED",
           f"HTTP {status} submissionId={submission.get('submissionId')} status={submission.get('status')}")
    app = wait_status(client, application_id, ("UNDER_REVIEW",), 40)
    record(f"C05 进入审核{label}", app["status"] == "UNDER_REVIEW", f"status={app['status']} editable={app.get('editable')}")
    return submission["submissionId"]


def wait_status(client, application_id, wanted, seconds):
    deadline, app = time.time() + seconds, None
    while time.time() < deadline:
        app = read_application(client, application_id)
        if app["status"] in wanted:
            return app
        time.sleep(3)
    return app


# ---------------------------------------------------------------- 5. 运营审核
def find_case(ops, application_id, submission_id):
    deadline = time.time() + 40
    while time.time() < deadline:
        page = ops.get("/client-onboarding-admin/v1/cases?page=1&size=50")
        for item in page.get("items") or []:
            if item["applicationId"] == application_id and item["sourceSubmissionId"] == submission_id:
                return item, page
        time.sleep(4)
    return None, page


def review_case(ops, application_id, submission_id, label):
    found, page = find_case(ops, application_id, submission_id)
    record(f"O01 案件列表{label}", found is not None,
           f"total={page.get('total')} caseId={found and found['caseId']} caseNo={found and found['caseNo']} status={found and found['status']}")
    case_id = found["caseId"]
    detail = ops.get(f"/client-onboarding-admin/v1/cases/{case_id}")
    review = detail["review"]
    evidence = [(e["evidenceId"], e["fileId"], e["status"]) for s in review["subjects"] for r in s["requirements"] for e in r["evidence"]]
    record(f"O02 案件详情{label}", detail["applicationStatus"] == "AVAILABLE" and detail["application"]["applicationId"] == application_id,
           f"caseStatus={review['status']} applicationStatus={detail['applicationStatus']} evidence={evidence} "
           f"classificationStatus={detail['classificationStatus']} industries={len(detail.get('industries') or [])} "
           f"blockers={review.get('approvalBlockers')}")
    return case_id, detail, evidence


def download_file(ops, case_id, file_id):
    try:
        grant = ops.send("POST", f"/client-onboarding-admin/v1/cases/{case_id}/files/{file_id}/download", key=False)
    except Failure as failure:
        record("O10 下载授权", False, problem(failure))
    ok = grant.get("fileId") == file_id and str(grant.get("url", "")).startswith("https://") and grant.get("expiresAt")
    record("O10 下载授权", bool(ok), f"fileId={grant.get('fileId')} mimeType={grant.get('mimeType')} expiresAt={grant.get('expiresAt')} "
                                   f"urlHost={urllib.parse.urlsplit(grant.get('url', '')).netloc}")
    status, data, host = presigned("GET", grant["url"])
    record("GET 预签名下载", status == 200 and data.startswith(b"%PDF"), f"HTTP {status} host={host} bytes={len(data)} head={data[:8]!r}")


def return_case(ops, case_id):
    try:
        result = ops.send("POST", f"/client-onboarding-admin/v1/cases/{case_id}/return", {
            "reasonCode": "DOCUMENT_UNREADABLE", "section": "MATERIALS", "publicNote": "身份证明文件不清晰，请重新上传清晰扫描件。"})
    except Failure as failure:
        record("O07 退回", False, problem(failure))
    record("O07 退回", result.get("caseId") == case_id, f"result={result}")


def approve_case(ops, case_id, detail, evidence):
    for evidence_id, _, status in evidence:
        if status != "RECEIVED":
            continue
        try:
            ops.send("POST", f"/client-onboarding-admin/v1/cases/{case_id}/evidence/{evidence_id}/review", {"outcome": "ACCEPTED"})
        except Failure as failure:
            record(f"O05 核验材料 {evidence_id}", False, problem(failure))
        record(f"O05 核验材料 {evidence_id}", True, "ACCEPTED")
    industry = detail["industries"][0]["code"]
    try:
        result = ops.send("PUT", f"/client-onboarding-admin/v1/cases/{case_id}/classifications",
                          {"items": [{"industryCode": industry, "mccCode": "5999", "primary": True}]})
        record("O03 设置分类", True, f"industry={industry} mcc=5999 confirmed={result.get('confirmed')}")
        result = ops.send("POST", f"/client-onboarding-admin/v1/cases/{case_id}/classification-confirmation")
        record("O04 确认分类", result.get("confirmed") is True, f"confirmedAt={result.get('confirmedAt')}")
    except Failure as failure:
        record("O03/O04 分类", False, problem(failure))
    deadline, review = time.time() + 40, {}
    while time.time() < deadline:
        review = ops.get(f"/client-onboarding-admin/v1/cases/{case_id}")["review"]
        if not review.get("approvalBlockers"):
            break
        time.sleep(4)
    record("O02 批准门禁", not review.get("approvalBlockers"), f"status={review.get('status')} blockers={review.get('approvalBlockers')}")
    try:
        result = ops.send("POST", f"/client-onboarding-admin/v1/cases/{case_id}/approval",
                          {"reasonCode": "DOCUMENTS_VERIFIED", "reason": "验收测试：材料与资料核验通过", "publicNote": None})
    except Failure as failure:
        record("O08 批准", False, problem(failure))
    record("O08 批准", result.get("caseId") == case_id, f"result={result}")


# ---------------------------------------------------------------- 6. V2：审核结论事件 compliance → client-master
REVIEW_EVENT = "ClientReviewDecided"
PARTITIONS = [f"client-master.compliance.p{i}" for i in range(4)]


def consumed_reviews():
    return int(sql("client-master", f"SELECT COUNT(*) FROM t_consumed_event WHERE event_type='{REVIEW_EVENT}'")[0][0])


def review_event_consumed(label, before):
    """Client 已看到审核结果后核对消息链路：分区队列清空且有消费者、DLQ 为空、client-master 登记了新的已消费事件。"""
    deadline, queues, after = time.time() + 30, {}, before
    while time.time() < deadline:
        queues = {name: (messages, consumers) for name, messages, consumers in rabbit_queues("client-master.compliance")}
        after = consumed_reviews()
        drained = all(queues.get(q, (1, 0))[0] == 0 and queues.get(q, (1, 0))[1] >= 1 for q in PARTITIONS)
        if drained and queues.get("client-master.compliance.dlq", (0, 0))[0] == 0 and after > before:
            break
        time.sleep(2)
    ok = (all(queues.get(q, (1, 0))[0] == 0 and queues.get(q, (1, 0))[1] >= 1 for q in PARTITIONS)
          and queues.get("client-master.compliance.dlq", (0, 0))[0] == 0 and after > before)
    record(f"MQ {REVIEW_EVENT} compliance→client-master 已消费{label}", ok,
           f"queues={queues} consumedEvents {before}→{after}")


# ---------------------------------------------------------------- 主流程
def main():
    approve = "--approve" in sys.argv
    stamp = time.strftime("%Y%m%d%H%M%S")
    email = f"acceptance-{stamp}@example.test"
    password = "Acceptance-" + uuid.uuid4().hex[:16]
    print(f"email={email}")
    client, data = Client(), {}
    try:
        register_and_login(client, email, password)
        application_id = start_application(client)
        data["applicationId"] = application_id
        options = form_options(client)
        fill_profile(client, application_id, options, stamp)
        app = read_application(client, application_id)
        record("C05 读取资料", app["status"] == "DRAFT" and app.get("values", {}).get("contact", {}).get("email") is not None,
               f"status={app['status']} applicationNo={app['applicationNo']} subjectPartyId={app['subjectPartyId']} "
               f"contactEmail={app.get('values', {}).get('contact', {}).get('email')} contactPhone={app.get('values', {}).get('contact', {}).get('phone')}")
        file_ids = upload_required(client, application_id, app)
        data["fileIds"] = file_ids
        submission_id = submit(client, application_id, "")
        ops = Operations(secrets()["E2E_ADMIN_EMAIL"], secrets()["E2E_ADMIN_PASSWORD"])
        record("运营登录", bool(ops.token), "accessToken obtained")
        case_id, detail, evidence = review_case(ops, application_id, submission_id, "")
        data["caseId"] = case_id
        download_file(ops, case_id, evidence[0][1] if evidence else file_ids[0])
        consumed = consumed_reviews()
        return_case(ops, case_id)
        app = wait_status(client, application_id, ("RETURNED",), 60)
        review = app.get("review") or {}
        record("Client 看到退回", app["status"] == "RETURNED" and app.get("reviewStatus") == "AVAILABLE"
               and review.get("outcome") == "RETURNED" and review.get("returnSection") == "MATERIALS" and review.get("publicNote"),
               f"status={app['status']} editable={app.get('editable')} reviewStatus={app.get('reviewStatus')} review={review}")
        review_event_consumed("（退回）", consumed)
        if not approve:
            return
        submission_id = submit(client, application_id, "（重提）")
        case_id, detail, evidence = review_case(ops, application_id, submission_id, "（重提）")
        data["resubmitCaseId"] = case_id
        consumed = consumed_reviews()
        approve_case(ops, case_id, detail, evidence)
        app = wait_status(client, application_id, ("APPROVED",), 60)
        record("Client 看到批准", app["status"] == "APPROVED" and (app.get("review") or {}).get("outcome") == "APPROVED",
               f"status={app['status']} reviewStatus={app.get('reviewStatus')} review={app.get('review')}")
        review_event_consumed("（批准）", consumed)
    except Stop:
        pass
    except Failure as failure:
        RESULTS.append(("未预期 HTTP 失败", "FAIL", problem(failure)))
        print("FAIL unexpected HTTP | " + problem(failure))
    finally:
        print("\n==== 汇总 ====")
        print(f"email={email} data={data}")
        for step, verdict, evidence in RESULTS:
            print(f"{verdict:4} {step}")
        passed = sum(1 for r in RESULTS if r[1] == "PASS")
        print(f"\n合计 {passed}/{len(RESULTS)} 通过")
    raise SystemExit(0 if RESULTS and passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
