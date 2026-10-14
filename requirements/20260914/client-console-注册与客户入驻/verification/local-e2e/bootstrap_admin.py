"""步骤 3.1：引导管理员改密 → 创建首个真人管理员 → 新管理员改密。只输出步骤结果，不输出密码或令牌。"""
from e2e_http import Failure, Operations, secrets

s = secrets()
admin = Operations("admin", s["E2E_BOOTSTRAP_PASSWORD"])
context = admin.get("/auth/v1/context")
print("bootstrap context:", {k: context.get(k) for k in ("accountType", "passwordChangeRequired", "bootstrapAdministratorRequired")})
admin.send("POST", "/auth/v1/password",
           {"currentPassword": s["E2E_BOOTSTRAP_PASSWORD"], "newPassword": s["E2E_BOOTSTRAP_NEW_PASSWORD"]}, key=False)
print("bootstrap password changed")

admin = Operations("admin", s["E2E_BOOTSTRAP_NEW_PASSWORD"])
admin.send("POST", "/auth/v1/bootstrap/administrator",
           {"email": s["E2E_ADMIN_EMAIL"], "initialPassword": s["E2E_ADMIN_INITIAL_PASSWORD"]}, key=False)
print("first administrator created")

operator = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_INITIAL_PASSWORD"])
operator.send("POST", "/auth/v1/password",
              {"currentPassword": s["E2E_ADMIN_INITIAL_PASSWORD"], "newPassword": s["E2E_ADMIN_PASSWORD"]}, key=False)
print("administrator password changed")

operator = Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
context = operator.get("/auth/v1/context")
print("administrator context:", {k: context.get(k) for k in ("accountId", "accountType", "passwordChangeRequired")})
try:
    Operations("admin", s["E2E_BOOTSTRAP_NEW_PASSWORD"])
    print("WARNING: bootstrap admin still able to log in")
except Failure as failure:
    print("bootstrap admin login after handover:", failure.status)
