"""本机 e2e：经运营「存储管理」登记本机 MinIO（复用文件多存储并存 stage7_register_storage.py，替换入口、账号、密钥、地址与证书）。
密钥取 local-environment/.env.local 的 MOR_MINIO_PLATFORM_ACCESS_KEY / SECRET_KEY，只在内存中使用，不打印。
运行记录写暂存区（0600，不含密钥）。用法：python3 file_register_storage.py
"""
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCRATCH = Path(os.environ.get("WF_SCRATCH", "/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/"
                                             "49da8772-1276-46ac-8174-179e39b15018/scratchpad"))
os.environ.setdefault("FS7_STATE", str(SCRATCH / "fs-register"))
ACC = HERE.parents[3] / "20261022/platform-support-文件多存储并存/release/acceptance"
sys.path.insert(0, str(ACC))
import fs7_common as c  # noqa: E402
import stage7_register_storage as r  # noqa: E402

LOCAL = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment")
c.ORIGIN = "https://127.0.0.1:18444"
c.ENDPOINT = "https://127.0.0.1:19400"
c.CA_FILE = LOCAL / "minio/certs/public.crt"
c.ENV = c.read_env(LOCAL / ".env.local")
_secrets = c.read_env(LOCAL / "e2e/secrets.env")
c.admin_token = lambda: c.login(_secrets["E2E_ADMIN_EMAIL"], _secrets["E2E_ADMIN_PASSWORD"])
# 本机 Nginx 自签证书
import ssl, urllib.request  # noqa: E402,E401
urllib.request.install_opener(urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))
r.REASON = "工作流阶段 5 带文件验收：登记本机 MinIO"
r.main()
