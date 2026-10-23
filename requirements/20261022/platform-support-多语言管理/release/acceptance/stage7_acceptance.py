"""多语言管理阶段 7：mor-test 接口验收（经 Gateway 走真实运营 / 客户入口；写数据，结束后用 stage7_cleanup.py 清理）。

前置：release.md 第 6 节第 1—13 步完成（5 个候选镜像 healthy、块 135 已激活、first_bundles.py 已为两个控制台发布首批语言包）。
数据隔离：破坏性场景全部在合成应用 qa-i18n-<run> 与私用语言 qaa 上做；真实应用只做一次“内容不变的再发布 + 切回 + 切回来”
（operations-console，界面文案不变），用于 manifest 地址随发布变化与切回（AC-05、AC-06）。
覆盖：AC-01 语言目录；AC-02 应用 / 命名空间 / key 导入（缺命名空间阻止、预览过期、文件检查中 409）；AC-03 翻译保存与 ICU 错误、
版本冲突；批量保存（多条一次保存、超过 Gateway 65536 字节被拒）；AC-04 预检与覆盖不足拒绝、发布 → 检查中 → 上线；
AC-09 导出再导入无差异、非法文案拒绝、带 BOM 文件被检查拒绝；AC-10 差异；AC-05 切回与语言包不变；AC-06 manifest 地址与公开读取；
权限（只读人员写入 403）；运行期间 5 个服务健康、Java ERROR 0。
运行记录：I18N7_STATE/run-<run>.json（0600），清理脚本据此删除。

用法：
  python3 -I stage7_acceptance.py --dry-run   只做本机自检（构造全部请求体与文件、ICU 样例），不调用任何接口
  python3 -I stage7_acceptance.py             阶段 7 第 14 步执行
"""
import argparse
import hashlib
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import i18n7_common as c  # noqa: E402

QA_LOCALE = "qaa"  # ISO 639 私用区代码，不会与真实语言冲突
SOURCE_V1 = {
    "common": {"common.greet": "Hello {name}", "common.items": "{count, plural, one {# item} other {# items}}",
               "common.save": "Save"},
    "team": {"team.title": "Team", "team.legacy": "Legacy members"},
    "billing": {"billing.title": "Billing"},
}
SOURCE_V2 = {
    "common": {"common.greet": "Hello, {name}!", "common.items": "{count, plural, one {# item} other {# items}}",
               "common.save": "Save", "common.cancel": "Cancel"},
    "team": {"team.title": "Team"},
    "billing": {"billing.title": "Billing"},
}
ZH = {"common.greet": "你好，{name}！", "common.items": "{count, plural, other {# 项}}", "common.save": "保存",
      "common.cancel": "取消", "team.title": "团队", "billing.title": "账单"}
BAD_PLURAL = "{count, plural, one {# 项}}"     # 缺 other 分支
BAD_PARAM = "你好，{名字}！"                     # 参数名被翻译


def source_file(app, namespaces):
    return json.dumps({"format": "code-mor-i18n-source/1", "application": app, "namespaces": namespaces},
                      ensure_ascii=False).encode()


def translation_file(app, namespaces):
    return json.dumps({"format": "code-mor-i18n-translation/1", "application": app, "locale": "zh-CN",
                       "namespaces": namespaces}, ensure_ascii=False).encode()


def grouped(texts):
    out = {}
    for key, text in texts.items():
        out.setdefault(key.split(".", 1)[0], {})[key] = text
    return out


def entries(token, app, locale="zh-CN"):
    status, page, _, _ = c.ops(token, "GET", f"/applications/{app}/translations?locale={locale}&page=1&size=200")
    return {item["keyCode"]: item for item in page.get("items", [])} if status == 200 else {}


def runtime_ok(since):
    for service in c.SERVICES:
        health = c.container(service, "{{.State.Health.Status}} {{.RestartCount}}")
        c.check("运行", f"{service} healthy、重启 0", health == "healthy 0", health)
    for service in c.JAVA_SERVICES:
        errors = c.errors_since(service, since)
        c.check("运行", f"{service} 自 {since} 起 ERROR 0", not errors, errors[:3])


def preconditions():
    expected = c.compose_images(c.RELEASE / "compose-candidate.yaml")
    for service in c.SERVICES:
        image = c.container(service, "{{.Image}}")
        c.check("前置", f"{service} 为候选镜像 {expected[service][7:19]}", image == expected[service], image)
    rows = c.sql("mysql", "SELECT COUNT(*) FROM mor_platform.t_gateway_route WHERE route_id BETWEEN 900000000000135401 "
                          "AND 900000000000135427 AND status='ACTIVE'")
    c.check("前置", "块 135 路由 27 条 ACTIVE", rows[0][0] == "27", rows)
    rows = c.sql("mysql", "SELECT COUNT(*) FROM mor_platform.t_i18n_application WHERE application_code IN "
                          "('operations-console','client-console') AND current_release_id IS NOT NULL")
    c.check("前置", "两个控制台都有当前发布（first_bundles.py 已执行）", rows[0][0] == "2", rows)


# ---------- AC-01 语言目录 ----------

def locales(admin, record):
    status, listed, _, _ = c.ops(admin, "GET", "/locales")
    codes = {item["localeCode"]: item for item in listed.get("items", [])}
    c.check("AC-01", "语言目录含 en、zh-CN 且 ENABLED", status == 200 and codes.get("en", {}).get("status") == "ENABLED"
            and codes.get("zh-CN", {}).get("status") == "ENABLED", list(codes))
    if QA_LOCALE in codes:
        raise SystemExit(f"语言 {QA_LOCALE} 已存在（上次验收未清理），先运行 stage7_cleanup.py")
    body = {"localeCode": QA_LOCALE, "nativeName": "QA 私用", "englishName": "QA private use", "textDirection": "LTR", "sortOrder": 990}
    status, created, _, _ = c.ops(admin, "POST", "/locales", body)
    c.check("AC-01", f"新增语言 {QA_LOCALE}（{status}）", status in (200, 201) and created.get("status") == "ENABLED", created)
    record["locale"] = QA_LOCALE
    c.save(f"run-{record['run']}.json", record)
    status, dup, _, _ = c.ops(admin, "POST", "/locales", body)
    c.check("AC-01", f"重复语言被拒（{status}）", c.rejected(status, dup, status) and status in (400, 409, 422), dup)
    status, bad, _, _ = c.ops(admin, "POST", "/locales", {**body, "localeCode": "EN_us"})
    c.check("AC-01", f"非 BCP 47 代码被拒（{status}）", status in (400, 422), bad)
    status, rtl, _, _ = c.ops(admin, "PUT", f"/locales/{QA_LOCALE}", {"expectedVersion": created.get("version", 1), "nativeName": "QA 私用",
                                                                    "englishName": "QA private use", "textDirection": "RTL",
                                                                    "status": "DISABLED", "sortOrder": 990})
    c.check("AC-01", f"设为 RTL 并停用（{status}）", status == 200 and rtl.get("textDirection") == "RTL" and rtl.get("status") == "DISABLED", rtl)
    status, stale, _, _ = c.ops(admin, "PUT", f"/locales/{QA_LOCALE}", {"expectedVersion": created.get("version", 1), "nativeName": "x",
                                                                      "englishName": "x", "textDirection": "LTR", "status": "ENABLED",
                                                                      "sortOrder": 990})
    c.check("AC-01", f"过期版本修改语言 409（{status}）", status == 409, stale)
    en = codes.get("en", {})
    status, en_off, _, _ = c.ops(admin, "PUT", "/locales/en", {"expectedVersion": en.get("version", 1), "nativeName": en.get("nativeName"),
                                                             "englishName": en.get("englishName"), "textDirection": "LTR",
                                                             "status": "DISABLED", "sortOrder": en.get("sortOrder", 10)})
    c.check("AC-01", f"基准语言 en 不能停用（{status}）", c.rejected(status, en_off, status) and status in (400, 409, 422), en_off)


# ---------- AC-02 应用、命名空间、key 导入 ----------

def application(admin, record):
    app = record["app"]
    status, bad, _, _ = c.ops(admin, "POST", "/applications", {"applicationCode": app + "-x", "applicationName": "QA",
                                                              "defaultLocale": "en", "requiredLocales": [QA_LOCALE],
                                                              "coverageThresholdPercent": 100})
    c.check("AC-01", f"停用语言不能设为必须覆盖（{status}）", c.rejected(status, bad, status) and status in (400, 409, 422), bad)
    body = {"applicationCode": app, "applicationName": f"多语言验收 {record['run']}", "defaultLocale": "en",
            "requiredLocales": ["zh-CN"], "coverageThresholdPercent": 100}
    status, created, _, _ = c.ops(admin, "POST", "/applications", body)
    c.check("AC-02", f"新建合成应用 {app}（{status}）", status in (200, 201) and created.get("applicationCode") == app, created)
    record["applicationCreated"] = status in (200, 201)
    c.save(f"run-{record['run']}.json", record)
    status, dup, _, _ = c.ops(admin, "POST", "/applications", body)
    c.check("AC-02", f"重复应用编码被拒（{status}）", status in (400, 409, 422), dup)
    status, ns, _, _ = c.ops(admin, "POST", f"/applications/{app}/namespaces", {"namespaceCode": "Common", "namespaceName": "x", "sortOrder": 1})
    c.check("AC-02", f"非法命名空间编码被拒（{status}）", status in (400, 422), ns)
    for order, code in enumerate(("common", "tmp"), start=1):
        status, created, _, _ = c.ops(admin, "POST", f"/applications/{app}/namespaces",
                                      {"namespaceCode": code, "namespaceName": code, "description": "验收", "sortOrder": order * 10})
        c.check("AC-02", f"新建命名空间 {code}（{status}）", status in (200, 201), created)
        if code == "tmp":
            status, gone, _, _ = c.ops(admin, "DELETE", f"/applications/{app}/namespaces/tmp?expectedVersion={created.get('version', 1)}")
            c.check("AC-02", f"删除空命名空间 tmp（{status}）", status in (200, 204), gone)


def key_imports(admin, record):
    app = record["app"]
    data = source_file(app, SOURCE_V1)
    completed = c.upload_json(admin, "KEY_IMPORT", "qa-source-v1.json", data)
    record.setdefault("files", []).append(completed["fileId"])
    status, first, _, _ = c.ops(admin, "POST", f"/applications/{app}/key-imports/previews", {"fileId": completed["fileId"]})
    if status == 409 and first.get("code") == "I18N_IMPORT_FILE_NOT_READY":
        c.check("AC-02", "文件检查中预览返回 409 I18N_IMPORT_FILE_NOT_READY（中文原因）", c.rejected(status, first, 409), first)
    else:
        c.unverified("AC-02", "文件检查中预览 409", f"完成上传时扫描已是 {completed.get('scanStatus')}，mor-test 扫描过快无法构造；由本机测试覆盖")
    status, preview, _ = c.with_file_ready(lambda: c.ops(admin, "POST", f"/applications/{app}/key-imports/previews",
                                                        {"fileId": completed["fileId"]}))
    c.check("AC-02", "引用未创建命名空间：预览不可导入并列出 team、billing", status == 200 and preview.get("importable") is False
            and sorted(preview.get("missingNamespaces") or []) == ["billing", "team"], preview)
    status, blocked, _, _ = c.ops(admin, "POST", f"/applications/{app}/key-imports",
                                  {"fileId": completed["fileId"], "previewDigest": preview.get("previewDigest", "0" * 64)})
    c.check("AC-02", f"缺命名空间时确认被拒 I18N_NAMESPACE_NOT_FOUND（{status}）", status == 422
            and blocked.get("code") == "I18N_NAMESPACE_NOT_FOUND"
            and {e.get("field") for e in blocked.get("errors") or []} >= {"namespaces.team", "namespaces.billing"}, blocked)
    for order, code in ((20, "team"), (30, "billing")):
        status, created, _, _ = c.ops(admin, "POST", f"/applications/{app}/namespaces",
                                      {"namespaceCode": code, "namespaceName": code, "sortOrder": order})
        c.check("AC-02", f"补建命名空间 {code}（{status}）", status in (200, 201), created)
    status, preview, _ = c.with_file_ready(lambda: c.ops(admin, "POST", f"/applications/{app}/key-imports/previews",
                                                        {"fileId": completed["fileId"]}))
    c.check("AC-02", f"重新预览可导入：新增 {len(preview.get('added') or [])}", status == 200 and preview.get("importable")
            and len(preview.get("added") or []) == 6, preview)
    status, result, _, _ = c.ops(admin, "POST", f"/applications/{app}/key-imports",
                                 {"fileId": completed["fileId"], "previewDigest": preview["previewDigest"]})
    c.check("AC-02", f"确认导入 6 个 key（{status}）", status == 200 and result.get("addedCount") == 6, result)
    # v2：变更 1、废弃 1、新增 1
    completed = c.upload_json(admin, "KEY_IMPORT", "qa-source-v2.json", source_file(app, SOURCE_V2))
    record["files"].append(completed["fileId"])
    status, preview, _ = c.with_file_ready(lambda: c.ops(admin, "POST", f"/applications/{app}/key-imports/previews",
                                                        {"fileId": completed["fileId"]}))
    keys = {kind: [i["keyCode"] for i in preview.get(kind) or []] for kind in ("added", "sourceChanged", "deprecated", "restored")}
    c.check("AC-02", f"第二次预览差异 {keys}", status == 200 and keys["added"] == ["common.cancel"]
            and keys["sourceChanged"] == ["common.greet"] and keys["deprecated"] == ["team.legacy"] and preview.get("unchangedCount") == 4,
            preview)
    status, stale, _, _ = c.ops(admin, "POST", f"/applications/{app}/key-imports", {"fileId": completed["fileId"], "previewDigest": "0" * 64})
    c.check("AC-02", f"预览摘要不符确认 409 I18N_IMPORT_PREVIEW_STALE（{status}）", status == 409
            and stale.get("code") == "I18N_IMPORT_PREVIEW_STALE", stale)
    status, result, _, _ = c.ops(admin, "POST", f"/applications/{app}/key-imports",
                                 {"fileId": completed["fileId"], "previewDigest": preview["previewDigest"]})
    c.check("AC-02", "确认第二次导入 1 / 1 / 1", status == 200 and result.get("addedCount") == 1 and result.get("sourceChangedCount") == 1
            and result.get("deprecatedCount") == 1, result)


# ---------- AC-03 翻译与批量保存 ----------

def translations(admin, record):
    app = record["app"]
    listed = entries(admin, app)
    c.check("AC-03", f"翻译列表 zh-CN 含 6 个在用 key（{sorted(listed)}）", set(listed) == set(ZH), sorted(listed))
    greet = listed.get("common.greet", {})
    c.check("AC-03", "列表给出基准文案与参数 name", greet.get("sourceText") == "Hello, {name}!" and greet.get("parameters") == ["name"], greet)
    body = {"locale": "zh-CN", "translations": [{"keyCode": "common.greet", "text": ZH["common.greet"], "expectedVersion": greet.get("version", 0)}]}
    status, saved, _, _ = c.ops(admin, "POST", f"/applications/{app}/translations/batch", body)
    c.check("AC-03", f"合法翻译保存（{status}）", status == 200 and saved["translations"][0]["version"] == greet.get("version", 0) + 1, saved)
    after = entries(admin, app)["common.greet"]
    c.check("AC-03", "保存后记录操作人与版本", after.get("text") == ZH["common.greet"] and after.get("updatedBy") and after.get("updateTime"), after)
    status, stale, _, _ = c.ops(admin, "POST", f"/applications/{app}/translations/batch", body)
    c.check("AC-03", f"过期版本保存 409（{status}）", status == 409, stale)
    for label, text, key in (("plural 缺 other", BAD_PLURAL, "common.items"), ("参数名被翻译", BAD_PARAM, "common.greet")):
        version = entries(admin, app)[key].get("version", 0)
        status, bad, _, _ = c.ops(admin, "POST", f"/applications/{app}/translations/batch",
                                  {"locale": "zh-CN", "translations": [{"keyCode": key, "text": text, "expectedVersion": version}]})
        errors = bad.get("errors") or []
        c.check("AC-03", f"ICU 错误（{label}）422 I18N_MESSAGE_INVALID，指出 translations[0].text 与中文原因",
                status == 422 and bad.get("code") == "I18N_MESSAGE_INVALID" and errors and errors[0].get("field") == "translations[0].text"
                and c.CHINESE.search(errors[0].get("message", "")), bad)
    # 批量：剩余 5 条一次保存（ops-web 批量粘贴按 48 KB / 200 条切批后调用同一接口）
    current = entries(admin, app)
    batch = [{"keyCode": k, "text": ZH[k], "expectedVersion": current[k]["version"]} for k in sorted(ZH) if k != "common.greet"]
    status, saved, _, _ = c.ops(admin, "POST", f"/applications/{app}/translations/batch", {"locale": "zh-CN", "translations": batch})
    c.check("AC-03", f"批量保存 {len(batch)} 条（{status}）", status == 200 and len(saved.get("translations") or []) == len(batch), saved)
    big = [{"keyCode": k, "text": "长" * 8000, "expectedVersion": entries(admin, app)[k]["version"]} for k in sorted(ZH)[:3]]
    payload = json.dumps({"locale": "zh-CN", "translations": big}, ensure_ascii=False).encode()
    status, refused, _, _ = c.ops(admin, "POST", f"/applications/{app}/translations/batch", data=payload)
    unchanged = all(entries(admin, app)[k]["text"] == ZH[k] for k in sorted(ZH)[:3])
    c.check("AC-03", f"超过 65536 字节的批量请求被拒（{len(payload)} 字节，{status}），文案未变", status in (400, 413, 422) and unchanged,
            {"status": status, "code": refused.get("code")})


# ---------- AC-09 导出 / 导入 ----------

def export_import(admin, record):
    app = record["app"]
    status, exported, text, _ = c.ops(admin, "GET", f"/applications/{app}/translations/export?locale=zh-CN")
    c.check("AC-09", f"导出 zh-CN（{status}）", status == 200 and exported.get("format") == "code-mor-i18n-translation/1"
            and exported.get("namespaces", {}).get("common", {}).get("common.greet") == ZH["common.greet"], exported)
    completed = c.upload_json(admin, "TRANSLATION_IMPORT", "qa-export-roundtrip.json", text.encode())
    record["files"].append(completed["fileId"])
    status, result, _ = c.with_file_ready(lambda: c.ops(admin, "POST", f"/applications/{app}/translations/imports",
                                                       {"fileId": completed["fileId"], "locale": "zh-CN"}))
    c.check("AC-09", "导出文件原样导入无差异（更新 0）", status == 200 and result.get("updatedCount") == 0
            and result.get("unchangedCount") == len(ZH), result)
    bad = translation_file(app, {"common": {"common.items": BAD_PLURAL}})
    completed = c.upload_json(admin, "TRANSLATION_IMPORT", "qa-bad-icu.json", bad)
    record["files"].append(completed["fileId"])
    status, refused, _ = c.with_file_ready(lambda: c.ops(admin, "POST", f"/applications/{app}/translations/imports",
                                                        {"fileId": completed["fileId"], "locale": "zh-CN"}))
    c.check("AC-09", "导入非法文案 422 I18N_MESSAGE_INVALID（定位 namespaces.common.common.items）", status == 422
            and refused.get("code") == "I18N_MESSAGE_INVALID"
            and any(e.get("field") == "namespaces.common.common.items" for e in refused.get("errors") or []), refused)
    bom = b"\xef\xbb\xbf" + translation_file(app, {"common": {"common.save": "保存"}})
    status, started, _, _ = c.ops(admin, "POST", "/uploads", {"purpose": "TRANSLATION_IMPORT", "fileName": "qa-bom.json", "sizeBytes": str(len(bom))})
    grant = started.get("authorization") or {}
    if not c.check("AC-09", f"带 BOM 文件开始上传（{status}）", status == 201 and grant.get("url"), started):
        return
    put_status, _, _ = c.storage_request(grant["method"], grant["url"], data=bom, headers=dict(grant.get("headers") or {}))
    status, refused, _, _ = c.ops(admin, "POST", f"/uploads/{started['uploadId']}/completion", typed_empty=True)
    c.check("AC-09", f"带 BOM 的 JSON 完成上传被检查拒绝（PUT {put_status}，完成 {status} {refused.get('code')}）",
            put_status in (200, 201) and status == 422, refused)


# ---------- AC-04 发布、AC-10 差异、AC-05 切回 ----------

def bundle_public(admin, app, release_no, detail):
    """语言包经文件记录取公开地址，匿名读取摘要 = 发布记录摘要。"""
    found = {}
    for bundle in detail.get("bundles") or []:
        file_id = bundle["fileAddress"].rsplit("/", 1)[-1]
        status, record, _, _ = c.raw(admin, "GET", f"/storage-admin/v1/files/{file_id}")
        url = record.get("publicUrl") or ""
        st, body, _ = c.public_get(url) if url else (0, b"", {})
        found[(bundle["locale"], bundle["namespaceCode"])] = (bundle["fileAddress"], bundle["contentHash"])
        c.check("AC-04", f"#{release_no} {bundle['locale']}/{bundle['namespaceCode']} 公开文件：PUBLIC、对象键 i18n/{app}/{release_no}/…、匿名读取摘要一致",
                status == 200 and record.get("accessLevel") == "PUBLIC" and f"/i18n/{app}/{release_no}/" in url
                and st == 200 and hashlib.sha256(body).hexdigest() == bundle["contentHash"] and record.get("mimeType") == "application/json",
                {"status": status, "access": record.get("accessLevel"), "url": url, "get": st})
    return found


def releases(admin, record):
    app = record["app"]
    status, precheck, _, _ = c.ops(admin, "GET", f"/applications/{app}/release-precheck")
    c.check("AC-04", "全部已翻译时预检可发布、首次发布无基准", status == 200 and precheck.get("publishable") and precheck.get("missingTotal") == 0
            and (precheck.get("changeSummary") or {}).get("baseReleaseNo") is None, precheck)
    # 新增命名空间 extra 与 1 个未翻译 key，制造覆盖缺口
    status, added, _, _ = c.ops(admin, "POST", f"/applications/{app}/namespaces",
                                {"namespaceCode": "extra", "namespaceName": "extra", "sortOrder": 40})
    c.check("AC-04", f"新建命名空间 extra（{status}）", status in (200, 201), added)
    completed = c.upload_json(admin, "KEY_IMPORT", "qa-source-v3.json", source_file(app, {**SOURCE_V2, "extra": {"extra.note": "Note"}}))
    record["files"].append(completed["fileId"])
    status, preview, _ = c.with_file_ready(lambda: c.ops(admin, "POST", f"/applications/{app}/key-imports/previews", {"fileId": completed["fileId"]}))
    c.ops(admin, "POST", f"/applications/{app}/key-imports", {"fileId": completed["fileId"], "previewDigest": preview.get("previewDigest", "0" * 64)})
    status, precheck, _, _ = c.ops(admin, "GET", f"/applications/{app}/release-precheck")
    c.check("AC-04", f"新增 key 未翻译：预检不可发布并列出缺口（{precheck.get('missingTotal')}）", status == 200 and not precheck.get("publishable")
            and {"locale": "zh-CN", "keyCode": "extra.note"} in (precheck.get("missing") or []), precheck)
    status, refused, _, _ = c.ops(admin, "POST", f"/applications/{app}/releases", {"releaseNote": "应被拒绝"})
    c.check("AC-04", f"覆盖不足发布被拒 422 I18N_COVERAGE_INSUFFICIENT（{status}）", status == 422
            and refused.get("code") == "I18N_COVERAGE_INSUFFICIENT"
            and any(e.get("field") == "missing.zh-CN" and (e.get("message") or "").split(" ")[0] == "extra.note" for e in refused.get("errors") or []), refused)
    version = entries(admin, app)["extra.note"]["version"]
    c.ops(admin, "POST", f"/applications/{app}/translations/batch",
          {"locale": "zh-CN", "translations": [{"keyCode": "extra.note", "text": "备注", "expectedVersion": version}]})
    status, first, _, _ = c.ops(admin, "POST", f"/applications/{app}/releases", {"releaseNote": "验收第 1 版"})
    c.check("AC-04", f"发布 #1 → PENDING_CHECK（{status}）", status in (200, 201) and first.get("status") == "PENDING_CHECK"
            and first.get("releaseNo") == 1, first)
    detail1 = c.wait_release(admin, app, 1)
    c.check("AC-04", "#1 检查通过上线 READY、为当前、语言包 8/8", detail1.get("release", {}).get("status") == "READY"
            and detail1["release"].get("current") and (detail1.get("checkProgress") or {}).get("available") == 8, detail1.get("checkProgress"))
    record["release1"] = {f"{k[0]}/{k[1]}": v for k, v in bundle_public(admin, app, 1, detail1).items()}
    c.save(f"run-{record['run']}.json", record)
    # 第 2 版：只改一条中文
    version = entries(admin, app)["common.save"]["version"]
    c.ops(admin, "POST", f"/applications/{app}/translations/batch",
          {"locale": "zh-CN", "translations": [{"keyCode": "common.save", "text": "保存修改", "expectedVersion": version}]})
    status, precheck, _, _ = c.ops(admin, "GET", f"/applications/{app}/release-precheck")
    summary = precheck.get("changeSummary") or {}
    c.check("AC-04", "第 2 版预检摘要以 #1 为基准、zh-CN 修改 1", summary.get("baseReleaseNo") == 1
            and [t.get("changedCount") for t in summary.get("translationChanges") or [] if t.get("locale") == "zh-CN"] == [1], summary)
    status, second, _, _ = c.ops(admin, "POST", f"/applications/{app}/releases", {"releaseNote": "验收第 2 版"})
    detail2 = c.wait_release(admin, app, 2)
    c.check("AC-04", "#2 上线 READY 并成为当前", detail2.get("release", {}).get("status") == "READY" and detail2["release"].get("current"),
            detail2.get("release"))
    status, diff, _, _ = c.ops(admin, "GET", f"/applications/{app}/releases/2/diff?baseReleaseNo=1")
    c.check("AC-10", "差异 #2 对 #1：只有 zh-CN common.save 修改", status == 200 and not diff.get("added") and not diff.get("removed")
            and [(d["locale"], d["keyCode"], d["before"], d["after"]) for d in diff.get("changed") or []]
            == [("zh-CN", "common.save", "保存", "保存修改")], diff)
    status, diff1, _, _ = c.ops(admin, "GET", f"/applications/{app}/releases/1/diff")
    c.check("AC-10", "#1 无基准：全部为新增", status == 200 and diff1.get("baseReleaseNo") is None and len(diff1.get("added") or []) == 14
            and not diff1.get("changed") and not diff1.get("removed"), {k: len(diff1.get(k) or []) for k in ("added", "changed", "removed")})
    status, switched, _, _ = c.ops(admin, "POST", f"/applications/{app}/current-release", {"releaseNo": 1, "reason": "验收：切回第 1 版"})
    c.check("AC-05", f"切回 #1（{status}）", status == 200 and switched.get("releaseNo") == 1, switched)
    status, detail, _, _ = c.ops(admin, "GET", f"/applications/{app}")
    c.check("AC-05", "应用当前发布为 #1", detail.get("application", {}).get("currentReleaseNo") == 1, detail.get("application"))
    status, again, _, _ = c.ops(admin, "GET", f"/applications/{app}/releases/1")
    same = {f"{b['locale']}/{b['namespaceCode']}": [b["fileAddress"], b["contentHash"]] for b in again.get("bundles") or []}
    c.check("AC-05", "#1 语言包文件地址与摘要与首次发布一致（不重新生成）",
            same == {k: list(v) for k, v in record["release1"].items()}, same)
    bundle_public(admin, app, 1, again)
    status, refused, _, _ = c.ops(admin, "POST", f"/applications/{app}/current-release", {"releaseNo": 99, "reason": "不存在"})
    c.check("AC-05", f"切到不存在的发布被拒（{status}）", status in (400, 404), refused)
    status, back, _, _ = c.ops(admin, "POST", f"/applications/{app}/current-release", {"releaseNo": 2, "reason": "验收：切回第 2 版"})
    c.check("AC-05", f"切回 #2（{status}）", status == 200 and back.get("releaseNo") == 2, back)


# ---------- AC-05 / AC-06：真实控制台 manifest ----------

def manifests(admin, record):
    path, base = c.OPS_MANIFEST, c.OPS
    status, before, _, headers = c.raw(None, "GET", "?locale=zh-CN", prefix=path, base=base, key=False)
    current = before.get("releaseNo")
    c.check("AC-06", f"operations-console 匿名 manifest 200、当前 #{current}", status == 200 and current, before)
    record["manifestCacheControl"] = headers.get("Cache-Control")
    c.check("AC-06", f"manifest 经 Gateway 的缓存头为 {headers.get('Cache-Control')}（B-report 7.1 已接受 Gateway 统一 no-store）",
            "no-store" in (headers.get("Cache-Control") or ""), headers.get("Cache-Control"))
    zh = before.get("bundles", {}).get("zh-CN", {})
    st, body, hdrs = c.public_get(next(iter(zh.values()))["url"], origin=base) if zh else (0, b"", {})
    record["bundleCacheControl"] = hdrs.get("cache-control")
    print(f"   语言包对象响应 Cache-Control = {hdrs.get('cache-control')!r}（AC-06“长期缓存头”，记录实际值，见 release.md 第 9 节）")
    status, release, _, _ = c.ops(admin, "POST", "/applications/operations-console/releases", {"releaseNote": "阶段 7 验收：内容不变的再发布（切回测试）"})
    c.check("AC-06", f"operations-console 内容不变再发布 #{release.get('releaseNo')}（{status}）", status in (200, 201), release)
    new_no = release.get("releaseNo")
    record["opsReleases"] = {"before": current, "new": new_no}
    c.save(f"run-{record['run']}.json", record)
    detail = c.wait_release(admin, "operations-console", new_no)
    c.check("AC-06", f"#{new_no} READY 并成为当前", detail.get("release", {}).get("status") == "READY" and detail["release"].get("current"),
            detail.get("release"))
    _, after, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=path, base=base, key=False)
    old_urls = {(l, n): b["url"] for l, m in before["bundles"].items() for n, b in m.items()}
    new_urls = {(l, n): b["url"] for l, m in after["bundles"].items() for n, b in m.items()}
    old_hash = {(l, n): b["hash"] for l, m in before["bundles"].items() for n, b in m.items()}
    new_hash = {(l, n): b["hash"] for l, m in after["bundles"].items() for n, b in m.items()}
    c.check("AC-06", "发布号变化后地址全部变化、内容摘要不变（内容未改）", after.get("releaseNo") == new_no and set(old_urls) == set(new_urls)
            and all(old_urls[k] != new_urls[k] for k in old_urls) and old_hash == new_hash, {"old": current, "new": after.get("releaseNo")})
    for key, url in sorted(new_urls.items()):
        st1, body1, _ = c.public_get(url)
        st2, body2, _ = c.public_get(url)
        c.check("AC-06", f"{key[0]}/{key[1]} 同一地址重复读取内容不变、摘要 = hash", st1 == st2 == 200 and body1 == body2
                and hashlib.sha256(body1).hexdigest() == new_hash[key], {"status": [st1, st2]})
    _, preview, _, _ = c.raw(None, "GET", f"?locale=zh-CN&releaseNo={current}", prefix=path, base=base, key=False)
    c.check("AC-06", f"manifest?releaseNo={current} 返回旧发布地址（预览）", preview.get("releaseNo") == current
            and {(l, n): b["url"] for l, m in preview["bundles"].items() for n, b in m.items()} == old_urls, preview.get("releaseNo"))
    status, missing, _, _ = c.raw(None, "GET", "?locale=zh-CN&releaseNo=9999", prefix=path, base=base, key=False)
    c.check("AC-06", f"manifest 指定不存在的发布 404（{status}）", status == 404, missing)
    status, switched, _, _ = c.ops(admin, "POST", "/applications/operations-console/current-release",
                                   {"releaseNo": current, "reason": "阶段 7 验收：切回测试"})
    _, rolled, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=path, base=base, key=False)
    c.check("AC-05", f"切回 #{current} 后 manifest 立即返回旧发布地址（{status}）", status == 200 and rolled.get("releaseNo") == current
            and {(l, n): b["url"] for l, m in rolled["bundles"].items() for n, b in m.items()} == old_urls, rolled.get("releaseNo"))
    status, back, _, _ = c.ops(admin, "POST", "/applications/operations-console/current-release",
                               {"releaseNo": new_no, "reason": "阶段 7 验收：切回测试结束"})
    _, final, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=path, base=base, key=False)
    c.check("AC-05", f"再切到 #{new_no}（{status}）", status == 200 and final.get("releaseNo") == new_no, final.get("releaseNo"))
    codes = sorted(l["code"] for l in final.get("locales") or [])
    c.check("AC-01", f"停用的 {QA_LOCALE} 不出现在 manifest 语言列表（{codes}）", QA_LOCALE not in codes and codes == ["en", "zh-CN"], codes)
    status, client, _, _ = c.raw(None, "GET", "?locale=zh-CN", prefix=c.CLIENT_MANIFEST, base=c.CLIENT, key=False)
    zh = client.get("bundles", {}).get("zh-CN", {})
    st, body, hdrs = c.public_get(next(iter(zh.values()))["url"], origin=c.CLIENT) if zh else (0, b"", {})
    c.check("AC-06", f"client-console 匿名 manifest #{client.get('releaseNo')}、语言包 CORS 允许 {c.CLIENT}", status == 200
            and client.get("application") == "client-console" and st == 200 and hdrs.get("access-control-allow-origin") == c.CLIENT,
            {"status": status, "get": st, "acao": hdrs.get("access-control-allow-origin")})


# ---------- 权限 ----------

def permissions(admin, record):
    viewer = c.synthetic_person(admin, record["run"], record, "viewer", [c.PERM_VIEW])
    app = record["app"]
    status, _, _, _ = c.ops(viewer, "GET", f"/applications/{app}")
    c.check("权限", f"只读人员（I18N_VIEW）可查看应用（{status}）", status == 200)
    version = entries(admin, app)["common.save"]["version"]
    status, _, _, _ = c.ops(viewer, "POST", f"/applications/{app}/translations/batch",
                            {"locale": "zh-CN", "translations": [{"keyCode": "common.save", "text": "x", "expectedVersion": version}]})
    c.check("权限", f"只读人员保存翻译 403（{status}）", status == 403)
    status, _, _, _ = c.ops(viewer, "POST", f"/applications/{app}/releases", {"releaseNote": "x"})
    c.check("权限", f"只读人员发布 403（{status}）", status == 403)
    status, _, _, _ = c.ops(viewer, "POST", "/uploads", {"purpose": "KEY_IMPORT", "fileName": "x.json", "sizeBytes": "10"})
    c.check("权限", f"只读人员开始上传 403（{status}）", status == 403)


def dry_run():
    for label, payload in (("source v1", source_file("qa-i18n-dry", SOURCE_V1)), ("source v2", source_file("qa-i18n-dry", SOURCE_V2)),
                           ("translations", translation_file("qa-i18n-dry", grouped(ZH)))):
        doc = json.loads(payload)
        c.check("干跑", f"{label} JSON 合法（{len(payload)} 字节）", isinstance(doc, dict) and doc["namespaces"])
    v1 = {k for ns in SOURCE_V1.values() for k in ns}
    v2 = {k for ns in SOURCE_V2.values() for k in ns}
    c.check("干跑", "v1 → v2 差异：新增 common.cancel、废弃 team.legacy、变更 common.greet、不变 4",
            v2 - v1 == {"common.cancel"} and v1 - v2 == {"team.legacy"} and len((v1 & v2) - {"common.greet"}) == 4)
    c.check("干跑", "中文翻译覆盖 v2 全部 key", set(ZH) == v2)
    big = json.dumps({"locale": "zh-CN", "translations": [{"keyCode": k, "text": "长" * 8000, "expectedVersion": 1} for k in sorted(ZH)[:3]]},
                     ensure_ascii=False).encode()
    c.check("干跑", f"超限批量请求体 {len(big)} 字节 > 65536", len(big) > 65536)
    c.check("干跑", "候选覆盖文件可解析 5 个镜像", len(c.compose_images(c.RELEASE / "compose-candidate.yaml")) == 5)
    c.check("干跑", "test CA 存在（存储直传与客户入口 TLS）", c.CA_FILE.exists())
    return c.summary("接口验收（干跑，未调用任何接口）")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if args.dry_run:
        return dry_run()
    run = datetime.now(timezone.utc).strftime("%Y%m%d%H%M")
    since = c.now_iso()
    record = {"run": run, "app": f"qa-i18n-{run}", "startedAt": since, "files": []}
    c.save(f"run-{run}.json", record)
    preconditions()
    admin = c.admin_token()
    locales(admin, record)
    application(admin, record)
    key_imports(admin, record)
    translations(admin, record)
    export_import(admin, record)
    releases(admin, record)
    manifests(admin, record)
    permissions(admin, record)
    runtime_ok(since)
    record["finishedAt"] = c.now_iso()
    record["results"] = [{"ac": ac, "name": name, "ok": ok} for ac, name, ok in c.results]
    print("运行记录：", c.save(f"run-{run}.json", record))
    return c.summary("接口验收")


if __name__ == "__main__":
    sys.exit(main())
