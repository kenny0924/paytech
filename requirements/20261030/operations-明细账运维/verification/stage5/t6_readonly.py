"""AC-09：创建只读运营账号（自定义角色仅 FINANCE_ACCOUNTING_READ），核对导航 / 按钮能力与两条 POST 路由 403；无查看权限账号（payout 阶段 5 的 noperm，仅 DATA_QUERY_READ）看不到菜单。
账号与口令写入 t6-state.json（本机合成账号，验证结束后保留并在报告中列出）。"""
import json
import secrets as pysecrets
import time
import t6lib as L

FINANCE_ACCOUNTING_READ = "900000000000097101"
st = L.load()
admin = L.login()
if "readonly" not in st:
    stamp = time.strftime("%m%d%H%M%S")
    s, role, _ = L.ops("POST", "/iam/v1/roles", admin, {"roleCode": f"PLOPS_T6_READONLY_{stamp}", "roleName": f"明细账运维只读 {stamp}",
                                                        "permissionIds": [FINANCE_ACCOUNTING_READ], "reason": "明细账运维阶段 5 只读反例"})
    print("role", s, role)
    role_id = role.get("roleId") or role.get("objectId")
    email = f"plops-t6-readonly-{stamp}@example.test"
    p1, p2 = "Pt6!" + pysecrets.token_urlsafe(18), "Pt6!" + pysecrets.token_urlsafe(18)
    s, acct, _ = L.ops("POST", "/iam/v1/accounts", admin, {"email": email, "initialPassword": p1, "roleIds": [role_id],
                                                           "reason": "明细账运维阶段 5 只读反例"})
    print("account", s, {k: v for k, v in acct.items() if k != "initialPassword"})
    t = L.login(email, p1)
    s, _, _ = L.ops("POST", "/auth/v1/password", t, {"currentPassword": p1, "newPassword": p2}, key=False)
    print("password", s)
    st["readonly"] = {"email": email, "password": p2, "accountId": acct.get("accountId"), "roleId": role_id}
    L.save(st)
