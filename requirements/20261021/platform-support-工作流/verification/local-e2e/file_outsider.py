"""非参与人下载：经 IAM 建只有 WORKFLOW_TASK_HANDLE 的角色与第三个运营账号（口令只存暂存区 0600），下载 API 实例文件应 404。"""
import json, os, secrets as ps, time
import workflow_flow as w, file_flow as ff
SECRET = w.SCRATCH / "workflow-e2e-user3.json"
a = w.admin(); s = ff.load()
if not SECRET.exists():
    stamp = time.strftime("%H%M%S")
    role = a.send("POST", "/iam/v1/roles", {"roleCode": f"WF_E2E_HANDLER_{stamp}", "roleName": f"E2E 工作流参与人 {stamp}",
                                           "permissionIds": ["900000000000127105"], "reason": "工作流带文件验收：非参与人下载"})
    rid = role.get("roleId") or role.get("objectId")
    email = f"wf-e2e-outsider-{stamp}@example.test"; p1 = "Wf!" + ps.token_urlsafe(18); p2 = "Wf!" + ps.token_urlsafe(18)
    acc = a.send("POST", "/iam/v1/accounts", {"email": email, "initialPassword": p1, "roleIds": [rid], "reason": "工作流带文件验收：非参与人"})
    w.Operations(email, p1).send("POST", "/auth/v1/password", {"currentPassword": p1, "newPassword": p2}, key=False)
    SECRET.write_text(json.dumps({"email": email, "password": p2})); os.chmod(SECRET, 0o600)
    s["outsiderRoleId"] = rid; s["outsiderAccountId"] = acc.get("accountId"); ff.save(s)
    w.log("outsider", f"角色 {rid} 账号 {acc.get('accountId')} {email[0]}***@example.test")
cred = json.loads(SECRET.read_text()); c = w.Operations(cred["email"], cred["password"])
for name, fn in [("非参与人下载", lambda: c.send("POST", f"/workflow/v1/instances/{s['apiInstance']}/files/{s['apiStartFile']}/download", None)),
                 ("非参与人看详情", lambda: c.get(f"/workflow/v1/instances/{s['apiInstance']}")),
                 ("参与人下载不属于本实例的文件", lambda: a.send("POST", f"/workflow/v1/instances/{s['apiInstance']}/files/{s['user2File']}/download", None))]:
    try: fn(); w.log("6", f"{name}：意外成功")
    except w.Failure as f: w.log("6", f"{name}：HTTP {f.status} {f.body[:200]}")
