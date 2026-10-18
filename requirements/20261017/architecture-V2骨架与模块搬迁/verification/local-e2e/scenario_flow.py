"""2026-10-15 新增场景的 API 级端到端验收（阶段 5 V2 本机全链路，任务卡 14）：经 Nginx → Gateway → BFF → client-master / compliance / Platform。

由 requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/scenario_flow.py 复制改写：
身份键核对由 mor_cct（cct_app）改读 mor_client_master_v2e2e（client_master_app，表与列与原 CCT 相同：
t_party_identity_key、t_party_profile_identifier、t_party_profile）；只跑本机。

场景：
  A. 退回后修改证件号 → 重提成功，绑定新身份键、旧键保留（读 client-master 库核对）。
  B. 组织申请 + 关联人员：重复写（不再转发 Idempotency-Key）——C09 同内容重复、不同内容冲突，C11/C13 重复删除，
     C12 重复关联，F02 重复完成；随后最终拒绝 → 同类型重新申请复用原 Client、带入资料与人员、材料为空 → 重传 → 重提。

输出每步 PASS/FAIL 与证据；不输出密码、令牌、Cookie、预签名 URL 或证件原值。
用法：python3 scenario_flow.py
"""
import time
import uuid

import acceptance_flow as flow
from acceptance_flow import Client, Stop, problem, record
from e2e_http import Failure, Operations, secrets, sql


def client_master(statement):
    """只读查询 mor_client_master_v2e2e（口令经环境变量传入 mysql，不打印）。"""
    return sql("client-master", statement)


def identity_keys(party_id):
    return client_master(f"SELECT identity_key_id,normalized_identifier_value FROM t_party_identity_key WHERE party_id={int(party_id)} "
               "ORDER BY create_time")


def bound_key(party_id):
    rows = client_master("SELECT d.identity_key_id FROM t_party_profile_identifier d JOIN t_party_profile p ON p.profile_id=d.profile_id "
               f"WHERE p.party_id={int(party_id)}")
    # mysql -B 把空值输出为字符串 NULL。
    return rows[0][0] if rows and rows[0][0] != "NULL" else None


def new_account(label):
    stamp = time.strftime("%Y%m%d%H%M%S") + uuid.uuid4().hex[:4]
    email = f"scenario-{label}-{stamp}@example.test"
    client = Client()
    flow.register_and_login(client, email, "Scenario-" + uuid.uuid4().hex[:16])
    return client, stamp, email


# ---------------------------------------------------------------- A. 退回后改证件
def scenario_return_change_identifier(ops):
    client, stamp, email = new_account("ret")
    app_id = flow.start_application(client)
    options = flow.form_options(client)
    flow.fill_profile(client, app_id, options, stamp)
    app = flow.read_application(client, app_id)
    party = app["subjectPartyId"]
    flow.upload_required(client, app_id, app)
    submission = flow.submit(client, app_id, "（A）")
    case_id, _, _ = flow.review_case(ops, app_id, submission, "（A）")
    flow.return_case(ops, case_id)
    app = flow.wait_status(client, app_id, ("RETURNED",), 60)
    record("A 退回", app["status"] == "RETURNED" and app.get("editable") is True, f"status={app['status']} editable={app.get('editable')}")
    before = identity_keys(party)
    record("A 退回时身份键", len(before) == 1 and bound_key(party) == before[0][0], f"keys={len(before)} bound=原键")
    new_value = f"NEW{stamp}"
    flow.draft(client, app_id, {"identifier": {"identifierValue": new_value}}, "A C06 退回后改证件号")
    app = flow.read_application(client, app_id)
    record("A 改后读取", (app.get("values", {}).get("identifier") or {}).get("identifierValue") == new_value
           and bound_key(party) is None, f"identifierValue已更新 bound={bound_key(party)}")
    flow.submit(client, app_id, "（A 改证件后重提）")
    after = identity_keys(party)
    new_key = [k for k, norm in after if norm == new_value.upper()]
    record("A 重提绑定新键、旧键保留", len(after) == 2 and new_key and bound_key(party) == new_key[0]
           and any(k == before[0][0] for k, _ in after),
           f"keys={len(after)} boundIsNew={bool(new_key) and bound_key(party) == new_key[0]} oldKept={any(k == before[0][0] for k, _ in after)}")
    return {"A.email": email, "A.applicationId": app_id, "A.partyId": party}


# ---------------------------------------------------------------- B. 组织：重复写 + 拒绝后重新申请
def org_options(client):
    status, options = client.call("/onboarding/v1/form-options?clientType=ORGANIZATION")
    record("B C03 组织目录", status == 200 and options.get("identifierTypes"), f"HTTP {status} catalogStatus={options.get('catalogStatus')}")
    return options


def fill_organization(client, app_id, options, stamp):
    org_id = next(i for i in options["identifierTypes"] if i["code"] == "COMPANY_REGISTRATION_NUMBER")
    identifier = {"identifierType": org_id["code"], "identifierValue": f"ORG{stamp}"}
    if org_id.get("issuingCountryMode") == "REQUIRED":
        identifier["issuingCountry"] = "AE"
    org_type = (options.get("organizationTypes") or [{"code": "LIMITED_COMPANY"}])[0]["code"]
    flow.draft(client, app_id, {
        "organization": {"legalName": f"Scenario Org {stamp}", "tradeName": "Scenario", "incorporationCountry": "AE",
                         "organizationType": org_type, "incorporationDate": "2015-03-01"},
        "address": {"addressLine1": "2 Scenario Road", "city": "Dubai", "stateOrProvince": "Dubai",
                    "postalCode": "00000", "countryCode": "AE"},
        "identifier": identifier,
        "business": {"businessModelCode": options["businessModels"][0]["code"], "websiteUrl": "https://org.example.test",
                     "expectedVolume": "80000.00000000", "expectedTicket": "120.00000000", "currency": "USD",
                     "businessDescription": "组织场景验收数据"},
    }, "B C06 保存组织资料")
    flow.draft(client, app_id, {"contact": {"email": f"org-{stamp}@example.test", "phone": "+971501234568"}}, "B C06 保存联系方式")


def create_person(client, app_id, stamp):
    person = {"individual": {"givenName": "Ubo", "familyName": "Person", "birthDate": "1985-05-05",
                             "nationalityCountry": "AE", "residenceCountry": "AE"},
              "address": {"addressLine1": "3 Person Lane", "city": "Dubai", "stateOrProvince": "Dubai",
                          "postalCode": "00000", "countryCode": "AE"},
              "identifier": {"identifierType": "PASSPORT", "issuingCountry": "AE", "identifierValue": f"PP{stamp}"}}
    dirty = [g + "." + f for g, fs in person.items() for f in fs]
    try:
        status, receipt = client.command(f"/onboarding/v1/applications/{app_id}/people", "POST", {
            "person": {"dirtyFields": dirty, "values": person},
            "relation": {"relationType": "UBO", "ownershipPercentage": "40", "effectiveFrom": "2020-01-01", "effectiveTo": None}})
    except Failure as failure:
        record("B C07 新增人员", False, problem(failure))
    record("B C07 新增人员", status in (200, 201) and receipt.get("partyId"), f"HTTP {status} partyId={receipt.get('partyId')} relationId={receipt.get('relationId')}")
    return receipt["partyId"], receipt["relationId"]


def duplicate_relations(client, app_id, person):
    body = {"relatedPartyId": person, "relationType": "DIRECTOR", "ownershipPercentage": None,
            "effectiveFrom": "2021-01-01", "effectiveTo": None}
    first = client.command(f"/onboarding/v1/applications/{app_id}/relations", "POST", body)
    second = client.command(f"/onboarding/v1/applications/{app_id}/relations", "POST", body)
    record("B C09 同内容重复新增关系", first[1]["relationId"] == second[1]["relationId"],
           f"HTTP {first[0]}/{second[0]} relationId 相同={first[1]['relationId'] == second[1]['relationId']}")
    try:
        client.command(f"/onboarding/v1/applications/{app_id}/relations", "POST", {**body, "effectiveFrom": "2022-01-01"})
        record("B C09 不同内容冲突", False, "未返回冲突")
    except Failure as failure:
        record("B C09 不同内容冲突", failure.status == 409 and "RELATION_ALREADY_EXISTS" in failure.body, problem(failure))
    relation = first[1]["relationId"]
    codes = [client.command(f"/onboarding/v1/applications/{app_id}/relations/{relation}", "DELETE")[0] for _ in range(2)]
    record("B C11 重复删除关系", codes == [204, 204], f"HTTP {codes}")


def upload_with_duplicates(client, app_id, subject, requirement):
    """完整上传一份材料，并重复 F02 与 C12；返回 (fileId, documentId)。"""
    code = requirement["requirementCode"]
    status, grant = client.command("/onboarding-files/v1/uploads", "POST", {
        "applicationId": app_id, "subjectPartyId": subject, "filename": f"{code.lower()}.pdf",
        "mimeType": "application/pdf", "sizeBytes": str(len(flow.PDF))})
    auth = grant["authorization"]
    put_status, _, _ = flow.presigned("PUT", auth["url"], auth.get("headers"), flow.PDF)
    first = client.command(f"/onboarding-files/v1/uploads/{grant['uploadId']}/completion", "POST")
    second = client.command(f"/onboarding-files/v1/uploads/{grant['uploadId']}/completion", "POST")
    file_id = first[1]["fileId"]
    record(f"B F02 重复完成 {code}", put_status < 300 and file_id and second[1]["fileId"] == file_id,
           f"F01 HTTP {status} PUT {put_status} F02 HTTP {first[0]}/{second[0]} fileId 相同={second[1]['fileId'] == file_id}")
    deadline = time.time() + 75
    while time.time() < deadline:
        _, meta = client.call(f"/onboarding-files/v1/files/{file_id}")
        if meta.get("scanStatus") == "AVAILABLE":
            break
        time.sleep(3)
    body = {"subjectPartyId": subject, "requirementCode": code, "documentType": requirement["documentType"], "fileId": file_id}
    one = client.command(f"/onboarding/v1/applications/{app_id}/documents", "POST", body)
    two = client.command(f"/onboarding/v1/applications/{app_id}/documents", "POST", body)
    record(f"B C12 重复关联 {code}", one[1]["documentId"] == two[1]["documentId"],
           f"HTTP {one[0]}/{two[0]} documentId 相同={one[1]['documentId'] == two[1]['documentId']}")
    return file_id, one[1]["documentId"]


def required_targets(app):
    return [(s["subjectPartyId"], r) for s in app.get("materialSubjects") or []
            for r in s.get("requirements") or [] if r.get("requiredFlag")]


def reject_case(ops, case_id):
    try:
        result = ops.send("POST", f"/client-onboarding-admin/v1/cases/{case_id}/rejection", {
            "reasonCode": "RISK_NOT_ACCEPTED", "reason": "场景验收：最终拒绝", "publicNote": "很抱歉，您的申请未通过审核。"})
    except Failure as failure:
        record("B O09 最终拒绝", False, problem(failure))
    record("B O09 最终拒绝", result.get("status") == "REJECTED", f"result={result}")


def scenario_organization(ops):
    client, stamp, email = new_account("org")
    status, receipt = client.command("/onboarding/v1/applications", "POST", {"clientType": "ORGANIZATION"})
    app_id, client_id = receipt["applicationId"], receipt["clientId"]
    record("B C04 创建组织申请", status == 201, f"HTTP {status} applicationId={app_id} clientId={client_id}")
    options = org_options(client)
    fill_organization(client, app_id, options, stamp)
    person, ubo_relation = create_person(client, app_id, stamp)
    duplicate_relations(client, app_id, person)
    app = flow.read_application(client, app_id)
    targets = required_targets(app)
    record("B C05 材料要求", len(targets) >= 2, f"subjects={sorted({s for s, _ in targets})}")
    first = True
    for subject, requirement in targets:
        if first:
            upload_with_duplicates(client, app_id, subject, requirement)
            # C13：同一要求再关联一份并重复删除。
            extra = flow.upload_material(client, app_id, subject, requirement)
            app = flow.read_application(client, app_id)
            extra_doc = next(d["documentId"] for d in app["documents"] if d["fileId"] == extra)
            codes = [client.command(f"/onboarding/v1/applications/{app_id}/documents/{extra_doc}", "DELETE")[0] for _ in range(2)]
            record("B C13 重复删除材料", codes == [204, 204], f"HTTP {codes}")
            first = False
        else:
            flow.upload_material(client, app_id, subject, requirement)
    org_party = app["subjectPartyId"]
    submission = flow.submit(client, app_id, "（B）")
    case_id, _, _ = flow.review_case(ops, app_id, submission, "（B）")
    reject_case(ops, case_id)
    app = flow.wait_status(client, app_id, ("REJECTED",), 60)
    record("B Client 看到拒绝", app["status"] == "REJECTED" and app.get("editable") is False, f"status={app['status']}")
    keys_before = (len(identity_keys(org_party)), len(identity_keys(person)))

    status, start = client.call("/onboarding/v1/start")
    record("B C01 拒绝后无进行中申请", status == 200 and start.get("activeApplicationId") is None,
           f"activeApplicationId={start.get('activeApplicationId')} applications={len(start.get('applications') or [])}")
    status, again = client.command("/onboarding/v1/applications", "POST", {"clientType": "ORGANIZATION"})
    new_id = again["applicationId"]
    record("B C04 同类型重新申请复用原 Client", status == 201 and new_id != app_id and again["clientId"] == client_id,
           f"HTTP {status} newApplicationId={new_id} clientId相同={again['clientId'] == client_id} operation={again.get('operation')}")
    new_app = flow.read_application(client, new_id)
    people = [p["partyId"] for p in new_app.get("people") or []]
    relations = [(r["relatedPartyId"], r["relationType"]) for r in new_app.get("relations") or []]
    record("B 新申请带入资料与人员、材料为空",
           new_app["status"] == "DRAFT" and new_app.get("editable") is True and new_app["subjectPartyId"] == org_party
           and (new_app.get("values", {}).get("organization") or {}).get("legalName") == f"Scenario Org {stamp}"
           and people == [person] and relations == [(person, "UBO")] and not new_app.get("documents"),
           f"status={new_app['status']} sameParty={new_app['subjectPartyId'] == org_party} people={len(people)} "
           f"relations={relations} documents={len(new_app.get('documents') or [])}")
    status, old = client.call(f"/onboarding/v1/applications/{app_id}")
    record("B 旧申请保持 REJECTED", old["status"] == "REJECTED", f"status={old['status']}")
    flow.upload_required(client, new_id, new_app)
    flow.submit(client, new_id, "（B 重新申请）")
    keys_after = (len(identity_keys(org_party)), len(identity_keys(person)))
    record("B 重提复用身份键", keys_after == keys_before == (1, 1), f"before={keys_before} after={keys_after}")
    return {"B.email": email, "B.oldApplicationId": app_id, "B.newApplicationId": new_id, "B.clientId": client_id,
            "B.ubo": person, "B.uboRelation": ubo_relation}


def main():
    data = {}
    try:
        ops = Operations(secrets()["E2E_ADMIN_EMAIL"], secrets()["E2E_ADMIN_PASSWORD"])
        for scenario in (scenario_return_change_identifier, scenario_organization):
            try:
                data.update(scenario(ops))
            except Stop:
                pass
            except Failure as failure:
                flow.RESULTS.append((scenario.__name__ + " 未预期 HTTP 失败", "FAIL", problem(failure)))
                print("FAIL unexpected HTTP | " + problem(failure))
    finally:
        print("\n==== 汇总 ====")
        print(f"data={data}")
        for step, verdict, _ in flow.RESULTS:
            print(f"{verdict:4} {step}")
        passed = sum(1 for r in flow.RESULTS if r[1] == "PASS")
        print(f"\n合计 {passed}/{len(flow.RESULTS)} 通过")
    raise SystemExit(0 if flow.RESULTS and passed == len(flow.RESULTS) else 1)


if __name__ == "__main__":
    main()
