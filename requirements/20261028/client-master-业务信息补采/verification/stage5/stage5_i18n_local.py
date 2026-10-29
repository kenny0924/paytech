"""REQ-12 阶段 5 本机 e2e 专用（由 REQ-05 同名脚本改写）：把 rq12 worktree 指定命名空间的文案发布到本机 Platform（mor_platform_v2e2e）的语言包。
client-console：onboarding-schema、onboarding；operations-console：onboarding-schema、client-review。

本机 Platform 上的 key 比 worktree 多（其他会话已导入），key 导入是"全集"语义（文件里没有的 ACTIVE key 会被废弃），因此：
  1 英文基准 = 本机 Platform 当前全部 ACTIVE key（只读查库得到，保留描述）∪ worktree workflow 命名空间（新增 key、改动的英文基准覆盖）；
    worktree 已删除的 workflow key 不废弃（共享 18301 实例的旧代码仍在用）。
  2 zh-CN 只导入 worktree workflow 命名空间的译文。
  3 预检通过后发布新版本，等待 READY。
经本机运营入口（Nginx 18444 → Gateway → ops-bff → Platform）调用；凭据读 local-environment/e2e/secrets.env，不打印。
用法：python3 -I stage5_i18n_local.py <client-console|operations-console> [preview]
"""
import json
import site
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

APPS = {"client-console": ("client-console-web", ["onboarding-schema", "onboarding"]),
        "operations-console": ("operations-console-web", ["onboarding-schema", "client-review"])}
APP = sys.argv[1]
WEB, NAMESPACES = APPS[APP]
ADMIN = "/localization-admin/v1"
OUT = Path(f"/Users/kenny/Desktop/Workspace/Code-MoR-rq12/{WEB}/i18n-out")
NOTE = "本机 e2e：REQ-12 业务信息补采阶段 5（" + "、".join(NAMESPACES) + " 命名空间）"
QUERY = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/3e08b044-fee7-45d0-ba22-072a21cafc69/scratchpad/stage5/q.sh")


def platform_keys():
    sql = ("SELECT n.namespace_code, k.key_code, k.source_text, k.description FROM t_i18n_key k "
           "JOIN t_i18n_namespace n ON n.namespace_id = k.namespace_id JOIN t_i18n_application a ON a.application_id = k.application_id "
           f"WHERE a.application_code = '{APP}' AND k.status = 'ACTIVE' ORDER BY n.namespace_code, k.key_code")
    rows = subprocess.run([str(QUERY), "mor_platform_v2e2e", "--batch", "--raw", "-N", "-e", sql], check=True,
                          capture_output=True, text=True).stdout.splitlines()
    result = {}
    for row in rows:
        ns, key, text, description = row.split("\t")
        result.setdefault(ns, {})[key] = text if description == "NULL" else {"text": text, "description": description}
    return result


def call(ops, method, path, body=None):
    try:
        return 200, ops.send(method, ADMIN + path, body) if method != "GET" else ops.get(ADMIN + path)
    except e2e_http.Failure as failure:
        text = str(failure)
        code = int(text.split()[1].rstrip(":")) if text.startswith("HTTP") else 0
        try:
            return code, json.loads(text.split(":", 1)[1])
        except ValueError:
            return code, {"raw": text[:300]}


def upload(ops, purpose, name, data):
    st, started = call(ops, "POST", "/uploads", {"purpose": purpose, "fileName": name, "sizeBytes": str(len(data))})
    assert st == 200, (st, started)
    grant = started["authorization"]
    request = urllib.request.Request(grant["url"], data=data, headers=dict(grant.get("headers") or {}), method=grant["method"])
    with urllib.request.urlopen(request, timeout=120, context=e2e_http.TLS) as response:
        assert response.status in (200, 201), response.status
    st, done = call(ops, "POST", f"/uploads/{started['uploadId']}/completion", None)
    assert st == 200, (st, done)
    return done["fileId"]


def until_ready(fn):
    for i in range(15):
        st, body = fn()
        if st == 409 and body.get("code") == "I18N_IMPORT_FILE_NOT_READY":
            time.sleep(min(i + 1, 5))
            continue
        return st, body
    return st, body


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    worktree_en = json.loads((OUT / f"{APP}.en.json").read_text())["namespaces"]
    worktree_zh = json.loads((OUT / f"{APP}.zh-CN.json").read_text())["namespaces"]
    namespaces = platform_keys()
    for ns in NAMESPACES:
        namespaces[ns] = {**namespaces.get(ns, {}), **worktree_en[ns]}
    source = json.dumps({"format": "code-mor-i18n-source/1", "application": APP, "namespaces": namespaces},
                        ensure_ascii=False, indent=2).encode()
    file_id = upload(ops, "KEY_IMPORT", f"{APP}.en.rq12.json", source)
    st, plan = until_ready(lambda: call(ops, "POST", f"/applications/{APP}/key-imports/previews", {"fileId": file_id}))
    counts = {k: len(plan.get(k) or []) for k in ("added", "sourceChanged", "deprecated", "restored")}
    print("preview", st, counts, plan.get("problems"), plan.get("missingNamespaces"), plan.get("code"))
    assert st == 200 and plan.get("importable") and counts["deprecated"] == 0, "preview not importable or would deprecate keys"
    print("added", plan.get("added"), "sourceChanged", plan.get("sourceChanged"))
    if len(sys.argv) > 2 and sys.argv[2] == "preview":
        return
    st, result = call(ops, "POST", f"/applications/{APP}/key-imports", {"fileId": file_id, "previewDigest": plan["previewDigest"]})
    print("key import", st, result.get("addedCount"), result.get("code"))
    translation = json.dumps({"format": "code-mor-i18n-translation/1", "application": APP, "locale": "zh-CN",
                              "namespaces": {ns: worktree_zh[ns] for ns in NAMESPACES}}, ensure_ascii=False, indent=2).encode()
    zh_id = upload(ops, "TRANSLATION_IMPORT", f"{APP}.zh-CN.rq12.json", translation)
    st, result = until_ready(lambda: call(ops, "POST", f"/applications/{APP}/translations/imports", {"fileId": zh_id, "locale": "zh-CN"}))
    print("zh-CN", NAMESPACES, st, result.get("updatedCount"), result.get("unchangedCount"), result.get("emptyCount"), result.get("code"))
    st, pre = call(ops, "GET", f"/applications/{APP}/release-precheck")
    print("precheck", st, pre.get("publishable"), pre.get("missingTotal"), pre.get("nextReleaseNo"), pre.get("problems"))
    assert pre.get("publishable"), pre
    st, release = call(ops, "POST", f"/applications/{APP}/releases", {"releaseNote": NOTE})
    number = release.get("releaseNo")
    print("release", st, number, release.get("status"), release.get("code"))
    for _ in range(300):
        st, detail = call(ops, "GET", f"/applications/{APP}/releases/{number}")
        if detail.get("release", {}).get("status") in ("READY", "FAILED"):
            break
        time.sleep(2)
    print("release state", detail.get("release", {}).get("status"), detail.get("release", {}).get("current"))


if __name__ == "__main__":
    main()
