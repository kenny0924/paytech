"""本机 e2e：经 Client 入口注册并验证测试账号（Mailpit，收不到时用 V2 公共件在本机库铸造本账号的验证秘密）。

用法：python3 -I register_mt.py（固定 E2E_TARGET=test） <tag> ...
账号与口令只写 scratchpad 的 0600 文件，不打印。邮箱为 example.test 测试地址。
"""
import json
import os
import secrets as pysecrets
import sys
import time
from pathlib import Path

sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
os.environ["E2E_TARGET"] = "test"
from acceptance_flow import Client, verification_link  # noqa: E402
from e2e_http import mint_signup_link  # noqa: E402

STORE = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/01ea5128-6d5a-487b-a761-584136eae2a1/scratchpad/mortest-legal/accounts.json")
accounts = json.loads(STORE.read_text()) if STORE.exists() else {}
stamp = time.strftime("%m%d%H%M%S", time.gmtime())
for tag in sys.argv[1:]:
    email = f"legal-mt-{tag.lower()}-{stamp}@example.test"
    password = "Lg-" + pysecrets.token_urlsafe(12) + "-9x"
    client = Client()
    since = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(time.time() - 5))
    status, reply = client.call("/auth/v1/register", "POST", {"email": email, "password": password}, auth=False)
    _, challenge, secret = verification_link(email, 8)
    source = "mailpit"
    if not challenge:
        challenge, secret = mint_signup_link(email, since)
        source = "minted"
    status2, verified = client.call("/auth/v1/email-verifications", "POST", {"challengeNo": challenge, "secret": secret}, auth=False)
    accounts[tag] = {"email": email, "password": password, "createdAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    STORE.write_text(json.dumps(accounts, indent=1))
    os.chmod(STORE, 0o600)
    print(tag, "register", status, "verify", status2, (verified or {}).get("verified"), "via", source)
