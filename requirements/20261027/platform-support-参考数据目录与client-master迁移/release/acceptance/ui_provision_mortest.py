#!/usr/bin/env python3
"""mor-test 浏览器冒烟预置（rel-dev 2026-10-27，主会话在 runbook 第 ⑬ 步执行）：注册一个验收 Client，建 AE 机构申请并按 Schema 填满，
再把预计流水币种改为 XYZ（不存在；本机用 DRAFT 币种 XTS，mor-test 不建测试币种）、地址省州改为 EG-C（与地址国家 AE 不一致），
供浏览器核对提交时的错误汇总与字段定位。凭据只写 ~/Code-MoR-backups/refdata-20261027-ui/ui-client.json（0600），不打印。
用法：python3 -I ui_provision_mortest.py provision | reset
"""
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import refdata_mortest  # noqa: E402

RA = refdata_mortest.load()
OA = RA.OA
STATE = Path.home() / "Code-MoR-backups/refdata-20261027-ui/ui-client.json"


def invalid(client, application_id, schema):
    address = OA.address_values(schema, OA.org_ctx("", "", "", "AE"))
    OA.save_draft(client, "UI", application_id, {"business": {"currency": "XYZ"}, "address": {**address, "stateOrProvince": "EG-C"}},
                  "UI 置入 XYZ 与 EG-C")
    body = RA.submission_check(client, application_id)
    print("submission-check errors:", [(e.get("path") or e.get("field")) if isinstance(e, dict) else e[0] for e in RA.field_errors(body)])


def provision():
    run = datetime.now(timezone.utc).strftime("%m%d%H%M%S")
    client = OA.client_register("UI", run)
    application_id, schema = RA.new_application(client, "UI", "UI", "AE")
    RA.fill(client, "UI", "UI", application_id, schema, "AE", run, OA.IDS["clientUIEmail"])
    invalid(client, application_id, schema)
    STATE.parent.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE.parent, 0o700)
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
