#!/usr/bin/env python3
"""ui-smoke 预置：注册一个 Client 账号，建 AE 机构申请并按 Schema 填满，再把预计流水币种改为 XTS（DRAFT，非 ACTIVE）、
地址省州改为 EG-C（与地址国家 AE 不一致），供浏览器核对提交时的错误汇总与字段定位。
凭据只写 scratchpad 的 0600 文件，不打印。
用法：python3 -I ui_provision.py provision | reset
"""
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import refdata_acceptance as RA  # noqa: E402  （设置 E2E_TARGET=local 并装好切片 1 的辅助函数）

OA = RA.OA
STATE = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/8198caa5-69c6-4d42-8400-62a77c6b6af6/scratchpad/refdata-e2e/proc/ui-client.json")


def invalid(client, application_id, schema):
    address = OA.address_values(schema, OA.org_ctx("", "", "", "AE"))
    OA.save_draft(client, "UI", application_id, {"business": {"currency": "XTS"}, "address": {**address, "stateOrProvince": "EG-C"}},
                  "UI 置入 XTS 与 EG-C")
    body = RA.submission_check(client, application_id)
    print("submission-check errors:", RA.field_errors(body))


def provision():
    run = datetime.now(timezone.utc).strftime("%m%d%H%M%S")
    client = OA.client_register("UI", run)
    application_id, schema = RA.new_application(client, "UI", "UI", "AE")
    RA.fill(client, "UI", "UI", application_id, schema, "AE", run, OA.IDS["clientUIEmail"])
    invalid(client, application_id, schema)
    STATE.write_text(json.dumps({"email": client.email, "password": client.password, "applicationId": application_id}) + "\n")
    os.chmod(STATE, 0o600)
    print(f"provisioned applicationId={application_id} → {STATE}")


def reset():
    saved = json.loads(STATE.read_text())
    client = OA.Session(OA.E.CLIENT, "/api/client", "client-UI")
    status, body = client.call("POST", "/auth/v1/login", {"email": saved["email"], "password": saved["password"]}, key=False, auth=False)
    client.token = body["accessToken"]
    _, schema = OA.form_schema(client, applicationId=saved["applicationId"])
    invalid(client, saved["applicationId"], schema)


if __name__ == "__main__":
    {"provision": provision, "reset": reset}[sys.argv[1]]()
