"""任务卡 16 本机真实链路验收：保存资料时的字段级错误经 Nginx → Gateway → Client BFF → client-master 到达浏览器入口。

步骤：注册并登录新账号 → 创建个人申请 → PATCH 一个不带国家码的手机号和一个格式错误的出生日期，
期望 422、code=INVALID_DRAFT、errors 含 contact.phone 与 individual.birthDate，requestId 与响应头一致，
响应不回显提交的原值；再用合规手机号保存，期望 200 SAVED（证明校验未误伤正常值）。
输出每步 PASS/FAIL 与证据；不输出密码、令牌、Cookie。

用法：python3 draft_field_errors.py
"""
import json
import time
import uuid

import acceptance_flow as flow
from acceptance_flow import RESULTS, Client, Stop, problem, record
from e2e_http import Failure

BAD_PHONE = "13912345678"
BAD_BIRTH_DATE = "1990/05/01"


def invalid_patch(client, application_id):
    """坏手机号与坏日期一次提交，核对 BFF 返回的 Problem errors[]。"""
    body = {"dirtyFields": ["contact.phone", "individual.birthDate"],
            "values": {"contact": {"phone": BAD_PHONE}, "individual": {"birthDate": BAD_BIRTH_DATE}}}
    try:
        status, reply = client.command(f"/onboarding/v1/applications/{application_id}/draft", "PATCH", body)
    except Failure as failure:
        problem_body = json.loads(failure.body)
        fields = [e.get("field") for e in problem_body.get("errors") or []]
        leaked = BAD_PHONE in failure.body or BAD_BIRTH_DATE in failure.body
        record("C04 坏手机号与坏日期 → 422 INVALID_DRAFT + errors[]",
               failure.status == 422 and problem_body.get("code") == "INVALID_DRAFT"
               and fields == ["contact.phone", "individual.birthDate"] and not leaked,
               problem(failure) + f" fields={fields} leaked={leaked}")
        return
    record("C04 坏手机号与坏日期 → 422 INVALID_DRAFT + errors[]", False, f"HTTP {status} {reply}")


def valid_patch(client, application_id):
    """合规手机号保存成功。"""
    try:
        status, receipt = client.command(f"/onboarding/v1/applications/{application_id}/draft", "PATCH",
                                         {"dirtyFields": ["contact.phone"], "values": {"contact": {"phone": "+8613800138000"}}})
    except Failure as failure:
        record("C04 合规手机号 → 200 SAVED", False, problem(failure))
        return
    record("C04 合规手机号 → 200 SAVED", status == 200 and receipt.get("operation") == "SAVED",
           f"HTTP {status} operation={receipt.get('operation')}")


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    email = f"draft-errors-{stamp}@example.test"
    password = "Acceptance-" + uuid.uuid4().hex[:16]
    print(f"email={email}")
    client = Client()
    try:
        flow.register_and_login(client, email, password)
        application_id = flow.start_application(client)
        invalid_patch(client, application_id)
        valid_patch(client, application_id)
    except Stop:
        pass
    except Failure as failure:
        RESULTS.append(("未预期 HTTP 失败", "FAIL", problem(failure)))
        print("FAIL unexpected HTTP | " + problem(failure))
    finally:
        passed = sum(1 for r in RESULTS if r[1] == "PASS")
        print(f"\n合计 {passed}/{len(RESULTS)} 通过")
    raise SystemExit(0 if RESULTS and passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
