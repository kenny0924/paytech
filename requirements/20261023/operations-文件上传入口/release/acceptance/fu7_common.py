"""运营控制台文件上传入口阶段 6 / 7 公共件：复用文件公开桶发布包的 pb7_common（运营入口请求、登录、合成人员、只读 SQL、对象直传与匿名读取、
容器日志），只改本需求的常量与运行记录目录。

- 入口：运营 http://localhost:28080（Nginx → Gateway → operations-console-bff → platform-support）。管理员取 test-environment/private/access.md，不打印。
- 只读 SQL 经 docker exec mor-test-mysql-1（root，口令只经 MYSQL_PWD）。对象地址只接受 MinIO（test CA）与 *.amazonaws.com（系统信任库）。
- 运行记录目录默认 ~/Code-MoR-backups/fileupload-stage7（0700，文件 0600；可用 PB7_STATE 覆盖），含合成人员口令，不含存储密钥与预签名地址。
- 除本目录外只 import 前例目录的 pb7_common.py（同仓库、已提交 be688db7 的发布包）。
"""
import json
import os
import re
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # 不在发布包与前例目录留下 __pycache__
HERE = Path(__file__).resolve().parent
RELEASE = HERE.parent
REQ = RELEASE.parent
ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))  # 主工作区（test-environment 与前例发布包）
PRECEDENT = ROOT / "requirements/20261022/platform-support-文件公开桶/release/acceptance"
os.environ.setdefault("MOR_TEST_ENVIRONMENT_DIR", str(ROOT / "test-environment"))
os.environ.setdefault("PB7_STATE", str(Path.home() / "Code-MoR-backups/fileupload-stage7"))
sys.path.insert(0, str(PRECEDENT))
import pb7_common as c  # noqa: E402

SERVICES = ("platform-support-service", "operations-console-bff", "operations-console-web")
JAVA_SERVICES = SERVICES[:2]
UPLOAD = "/file-upload/v1"
SOURCE = "operations-console-bff"
REASON_CODE = "OPERATIONS_PUBLIC_ASSET"
TARGET = ("S3_TEST", "mor-test-platform-public-k7q2", "PUBLIC_DOCUMENT", 900)  # (实例, 桶, 检查策略, 上传期限秒) technique 第 9 节
PUBLIC_HOST = "mor-test-platform-public-k7q2.s3.ap-southeast-1.amazonaws.com"
PERM_FILE_UPLOAD_READ = "900000000000136101"
PERM_FILE_UPLOAD_CREATE = "900000000000136102"
PERM_PLATFORM_CONFIGURATION_READ = "900000000000000534"  # 父菜单「平台配置」562 的读权限（没有它导航不显示子页）
PERM_FILE_RECORD_READ = "900000000000134101"
ROUTE_CODES = ("operations-file-upload-constraints", "operations-file-upload-list", "operations-file-upload-detail",
               "operations-file-upload-start", "operations-file-upload-complete")
# 与 compose-candidate.yaml / compose-rollback.yaml 一致；precheck 另行逐项核对文件内容
CANDIDATE = {
    "platform-support-service": "sha256:4859efd78dd92de7c8f97dca29ef7894e7d2e1115bd72f471960553375075d17",
    "operations-console-bff": "sha256:0bc674106d7b75f4857f5e299d6fdfeacd030c6e8514d8f92dadb4bc2f51a3ed",
    "operations-console-web": "sha256:70d3fdfbb6be16ed48edd3883338c08264122b34432ad68246b938e3c10dffea",
}
CANDIDATE_JAR = {
    "platform-support-service": "be0abf79fc3ca9ebb29d7dab6f7abe60e68105cd43c80fb358d85407b2ef68b1",
    "operations-console-bff": "f84caa925b7105b6f8974f6c50a36a0758f23869bacebc995ec3eecd6f686629",
}
ROLLBACK = {
    "platform-support-service": "sha256:4803299926245956db24f0260e46f8ca98d31b7f55fdfce306cb19a156d33515",
    "operations-console-bff": "sha256:3751638e5f34273844582b53ad0b5b2401a057fe8001bfc63d15241a8f8d487f",
    "operations-console-web": "sha256:4c1f1ec39b641285f3dd87320da7311ed11bc8a7024d0e52d4c7cc3f5265ef7d",
}
CONFIG = ("{{json .Config.Entrypoint}}|{{json .Config.Cmd}}|{{.Config.User}}|{{.Config.WorkingDir}}|{{json .Config.Env}}|"
          "{{json .Config.ExposedPorts}}|{{json .Config.Healthcheck}}|{{.Config.StopSignal}}")


def docker(*args):
    return subprocess.run(["docker", *args], capture_output=True, text=True)


def container(service, fmt):
    return docker("inspect", f"mor-test-{service}-1", "--format", fmt).stdout.strip()


def compose_images(path):
    """读覆盖文件中的 image: sha256:…（不依赖 PyYAML）。"""
    images, current = {}, None
    for line in Path(path).read_text().splitlines():
        match = re.match(r"^  ([a-z0-9-]+):\s*$", line)
        if match:
            current = match.group(1)
        match = re.match(r"^    image:\s*(sha256:[0-9a-f]{64})", line)
        if match and current:
            images[current] = match.group(1)
    return images


def sql_rows(statement):
    return c.sql("mor_platform", statement)


def sql_json(statement):
    """只读 SELECT 单列 JSON（--raw：mysql -B 默认会转义反斜杠，JSON 字符串里的 \\n 需要原样取回）。"""
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={c.ENV['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql", "-uroot",
                             "-N", "-B", "--raw", "--default-character-set=utf8mb4", "mor_platform", "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [json.loads(line) for line in result.stdout.splitlines() if line]


def sql_file(name):
    """执行发布包 sql/ 下的只读脚本（00 / 99），返回行。"""
    return sql_rows((RELEASE / "sql" / name).read_text())


def problem(body):
    return {"code": body.get("code"), "detail": body.get("detail")}


def window(days_before=7, minutes_after=5):
    start, end = c.created_window(60 * 24 * days_before, minutes_after)
    return f"createdFrom={start}&createdTo={end}"


def constraints(token):
    status, body, _ = c.raw(token, "GET", f"{UPLOAD}/constraints")
    return status, body


def start_upload(token, name, mime, size):
    return c.raw(token, "POST", f"{UPLOAD}/uploads", {"filename": name, "mimeType": mime, "sizeBytes": str(size)})


def save_json(path, record):
    Path(path).write_text(json.dumps(record, ensure_ascii=False, indent=1, default=str) + "\n")
