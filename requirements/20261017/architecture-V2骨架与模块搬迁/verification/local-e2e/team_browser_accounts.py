"""团队浏览器验收的前置账号：已批准、工作区可访问的所有者，以及已验证邮箱、尚未加入的受邀账号。

写出 ACCOUNT_FILE（含两个账号的密码，只应放在会话 scratchpad，不入库）。
用法：python3 team_browser_accounts.py <输出 JSON>（阶段 5 V2 本机全链路，任务卡 14；由 requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/ 同名脚本复制改写，只跑本机）
"""
import json
import sys
import time
import uuid

import acceptance_flow as flow
import workspace_flow as wf
from acceptance_flow import Client
from e2e_http import Operations, secrets


def approved_owner(stamp, password):
    client = Client()
    email = f"team-owner-{stamp}@example.test"
    flow.register_and_login(client, email, password)
    application_id = flow.start_application(client)
    flow.fill_profile(client, application_id, flow.form_options(client), stamp)
    flow.upload_required(client, application_id, flow.read_application(client, application_id))
    submission_id = flow.submit(client, application_id, "")
    ops = Operations(secrets()["E2E_ADMIN_EMAIL"], secrets()["E2E_ADMIN_PASSWORD"])
    case_id, detail, evidence = flow.review_case(ops, application_id, submission_id, "")
    flow.approve_case(ops, case_id, detail, evidence)
    app = flow.wait_status(client, application_id, ("APPROVED",), 60)
    _, item = wf.wait_accessible(client, app["clientId"])
    if not item:
        raise SystemExit("工作区未在 60 秒内可访问")
    return {"email": email, "password": password, "clientId": app["clientId"], "memberId": item["memberId"]}


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    password = "Team-" + uuid.uuid4().hex[:16]
    owner = approved_owner(stamp, password)
    invitee = {"email": f"browser-invitee-{stamp}@example.test", "password": password}
    flow.register_and_login(Client(), invitee["email"], password)
    json.dump({"owner": owner, "invitee": invitee}, open(sys.argv[1], "w"))
    print(f"READY owner={owner['email']} clientId={owner['clientId']} invitee={invitee['email']}")


if __name__ == "__main__":
    main()
