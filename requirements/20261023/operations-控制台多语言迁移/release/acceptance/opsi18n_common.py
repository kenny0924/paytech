"""运营控制台多语言迁移阶段 7 公共件：复用多语言管理发布包的 i18n7_common（登录、Gateway 请求、导入文件上传、匿名读取公开桶、只读 SQL），
只改本需求的常量与输入目录，并补充本需求需要的少量帮助函数。

- 入口：运营 http://localhost:28080（Nginx → Gateway → operations-console-bff → platform-support）。管理员取 test-environment/private/access.md，不打印。
- 运行记录目录默认 ~/Code-MoR-backups/opsi18n-stage7（0700，文件 0600；可用 I18N7_STATE 覆盖），不含口令、存储密钥与预签名地址。
- 除本目录外只 import 前例目录的 i18n7_common.py（同仓库、已提交 ed361c26 的发布包）。
"""
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
RELEASE = HERE.parent
REQ = RELEASE.parent
ROOT = HERE.parents[4]
PRECEDENT = ROOT / "requirements/20261022/platform-support-多语言管理/release/acceptance"
os.environ.setdefault("I18N7_STATE", str(Path.home() / "Code-MoR-backups/opsi18n-stage7"))
sys.path.insert(0, str(PRECEDENT))
import i18n7_common as c  # noqa: E402

# 本需求只替换两个服务；i18n7_common 的发布包目录指向前例，这里改指本发布包
c.SERVICES = ("operations-console-bff", "operations-console-web")
c.JAVA_SERVICES = ("operations-console-bff",)
c.RELEASE, c.REQ = RELEASE, REQ
c.BUNDLE_DIR = RELEASE / "i18n-bundles"
BUNDLE_DIR = c.BUNDLE_DIR

APP = "operations-console"
APP_ID = "900000000000135011"
EXISTING_NAMESPACES = ["i18n-admin", "enum"]
# 19 个新命名空间（technique 3.1 顺序）；名称 ≤ 64 字符，排序号接在已有的 i18n-admin 10、enum 20 之后
NEW_NAMESPACES = [
    ("common", "通用"), ("auth", "登录与语言"), ("nav", "菜单与资源名"), ("access", "人员、角色与审计"),
    ("gateway", "API 路由与凭证"), ("channel", "渠道"), ("commercial", "商务"), ("finance", "财务"), ("recon", "对账"),
    ("risk", "风控"), ("workflow", "工作流"), ("data-query", "数据查询"), ("file", "文件存储"), ("notification", "通知"),
    ("webhook", "Webhook"), ("vault", "数据保护"), ("card", "卡 BIN 库"), ("client-review", "客户入驻审核"), ("currency", "币种"),
]
ALL_NAMESPACES = sorted(EXISTING_NAMESPACES + [code for code, _ in NEW_NAMESPACES])
EN_FILE, ZH_FILE = f"{APP}.en.json", f"{APP}.zh-CN.json"
KEY_CODE = re.compile(r"(?=.{3,191}$)[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+")  # Platform LocalizationCodes.KEY_CODE
NAMESPACE_CODE = re.compile(r"[a-z][a-z0-9-]{0,63}")
MAX_TEXT, MAX_FILE = 8000, 2 * 1024 * 1024
TRANSIENT = (502, 503, 504)
CANDIDATE = {  # 与 compose-candidate.yaml 一致；precheck 另行逐项核对
    "operations-console-bff": "sha256:3751638e5f34273844582b53ad0b5b2401a057fe8001bfc63d15241a8f8d487f",
    "operations-console-web": "sha256:d228e3c883fdc1524ddb63c2d10f291100fc72a8bf25c191f1e03074c7a33433",
}
CANDIDATE_JAR = "3c7e6f647261115cf0b7150710ba2fa8d5c331714924e639e36240b275f00ef6"


def text_of(value):
    return value if isinstance(value, str) else value.get("text")


def load_bundles():
    """读 i18n-bundles 两个文件并以 SHA256SUMS 核对；返回 {(locale): (bytes, doc)}。"""
    sums = {}
    for line in (BUNDLE_DIR / "SHA256SUMS").read_text().splitlines():
        digest, name = line.split(maxsplit=1)
        sums[name.strip()] = digest
    out = {}
    for locale, name in (("en", EN_FILE), ("zh-CN", ZH_FILE)):
        data = (BUNDLE_DIR / name).read_bytes()
        out[locale] = (data, json.loads(data), hashlib.sha256(data).hexdigest() == sums.get(name))
    return out


def flat(doc, locale):
    """{key: (namespace, text)}。"""
    return {key: (ns, text_of(value) if locale == "en" else value) for ns, entries in doc["namespaces"].items()
            for key, value in entries.items()}


def label(doc_en, doc_zh, key, locale):
    """按 key 取界面文案（en 文件或 zh-CN 文件），供浏览器断言使用。"""
    ns = key.split(".", 1)[0]
    if locale == "en":
        return text_of(doc_en["namespaces"][ns][key])
    return doc_zh["namespaces"][ns][key]


def translation_file(doc_zh, namespace):
    """按命名空间切出的 zh-CN 翻译导入文件（Platform 翻译导入只写文件中出现的 key，部分文件合法）。"""
    part = {"format": doc_zh["format"], "application": doc_zh["application"], "locale": doc_zh["locale"],
            "namespaces": {namespace: doc_zh["namespaces"][namespace]}}
    return (json.dumps(part, ensure_ascii=False, indent=2) + "\n").encode()


def retry_transient(call, what, attempts=8, pause=10):
    """网关 / BFF 读取超时（502 / 503 / 504、连接超时）后 Platform 仍可能完成本次写入：等待后由调用方重新核对状态。
    这里只负责把瞬时失败转为“稍后再试”，返回最后一次 (状态, 正文)。"""
    status, body = None, {}
    for attempt in range(attempts):
        try:
            status, body, _, _ = call()
        except OSError as failure:  # socket 超时等
            status, body = 599, {"detail": str(failure)[:200]}
        if status not in TRANSIENT and status != 599:
            return status, body
        print(f"      {what}：瞬时失败 {status}，{pause} 秒后重新核对（第 {attempt + 1} 次）", flush=True)
        time.sleep(pause)
    return status, body


def current_release(token):
    status, detail, _, _ = c.ops(token, "GET", f"/applications/{APP}")
    return (detail.get("currentRelease") or {}) if status == 200 else {}


def application(token):
    status, detail, _, _ = c.ops(token, "GET", f"/applications/{APP}")
    return status, detail


def releases(token):
    status, page, _, _ = c.ops(token, "GET", f"/applications/{APP}/releases?page=1&size=50")
    return page.get("items", []) if status == 200 else []


def sql_rows(statement):
    return c.sql("mor_platform", statement)
