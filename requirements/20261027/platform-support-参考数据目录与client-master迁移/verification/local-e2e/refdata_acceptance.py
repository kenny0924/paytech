#!/usr/bin/env python3
"""参考数据目录与 client-master 迁移 · 阶段 5 本机验收（API 级，AC-01—AC-10、AC-13/14 的接口部分）。

链路：Platform 私网直连（新查询接口、旧接口形状）；运营 /api/operations（Nginx → Gateway → ops-bff）；Client /api/client（Nginx → Gateway →
client-console-bff → client-master / Platform）。Client 注册、草稿填写、材料、提交、案件查找复用切片 1 验收脚本的辅助函数（import，不复制）。

场景：
  P  Platform 5 个新接口：全字段、status / codes / level / parentCode / 分页筛选、400、不返回本地化名称（AC-03—AC-05）；旧接口形状（AC-02）
  R  运营参考数据接口 4 个（经 Gateway 块 139 路由）
  C  币种管理：列表新字段、ACTIVE 改冻结字段 422、logo 非公开 / 不存在 422、DRAFT（XTS）可改全部属性（AC-13 接口部分）
  F  Client form-schema AE / SA / EG：REF_ 目录补全、国家列表、旧三类目录已移除（AC-06、AC-11 数据部分）
  A  AE 机构：未知 / 停用国家、币种、行政区与国家不一致 → 字段级错误、提交 422 且仍 DRAFT；修正后提交通过（AC-07）
  K  O03 分类：大类 422、未知行业 422、未知 / 停用 MCC 422、细类 + 建议 MCC 保存、改选 MCC 保存、MCC 搜索、存量 RETAIL 可读（AC-08、AC-10）
  U  Platform 不可用：form-schema、提交、分类保存都返回明确错误且不写入（AC-09）；随后恢复
  S  SA、EG 机构提交通过（AC-07）
停用场景用 root 临时把 1 行参考数据置为 INACTIVE，结束（含异常）时恢复并核对。
结果：verification/acceptance-local-<UTC>.json。
"""
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REQ = HERE.parents[1]
MOR = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
SLICE1 = MOR / "requirements/20261027/client-master-全球合规入驻与资格切片1/assets/acceptance"
V2_E2E = MOR / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
os.environ["E2E_TARGET"] = "local"
sys.path[:0] = [str(SLICE1), str(V2_E2E)]
import e2e_http as E  # noqa: E402
import acceptance_flow as V2  # noqa: E402
import onboarding_acceptance as OA  # noqa: E402

OA.E, OA.V2 = E, V2
check, record, skip, problem, Stop = OA.check, OA.record, OA.skip, OA.problem, OA.Stop
PLATFORM = "http://127.0.0.1:18106/platform-support/platform/v1"
SWAP = HERE / "swap.py"
MYSQL = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/8198caa5-69c6-4d42-8400-62a77c6b6af6/scratchpad/refdata-e2e/my.sh")
LOCALIZED_KEYS = {"localizedName", "displayName", "nameZh", "nameEn", "label", "labelKey", "translations", "i18n"}
CASE = "/client-onboarding-admin/v1"
FLIPS = []


def root_sql(db, statement):
    out = subprocess.run([str(MYSQL), db, "-N", "-B"], input=statement, capture_output=True, text=True)
    if out.returncode:
        raise Stop("SQL 失败：" + out.stderr[:300])
    return [r.split("\t") for r in out.stdout.splitlines() if r]


def flip(table, column, code, status):
    """临时改参考数据状态；返回原状态并登记恢复。"""
    before = root_sql("mor_platform_v2e2e", f"SELECT status FROM {table} WHERE {column}='{code}'")[0][0]
    root_sql("mor_platform_v2e2e", f"UPDATE {table} SET status='{status}' WHERE {column}='{code}'")
    FLIPS.append((table, column, code, before))
    record("SETUP", f"临时 {table}.{code} {before} → {status}", "INFO")
    return before


def restore_flips():
    while FLIPS:
        table, column, code, before = FLIPS.pop()
        root_sql("mor_platform_v2e2e", f"UPDATE {table} SET status='{before}' WHERE {column}='{code}'")
        now = root_sql("mor_platform_v2e2e", f"SELECT status FROM {table} WHERE {column}='{code}'")[0][0]
        check("SETUP", f"恢复 {table}.{code} → {before}", now == before, now)


def platform(path):
    request = urllib.request.Request(PLATFORM + path, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.loads(response.read().decode() or "{}")
    except urllib.error.HTTPError as failure:
        text = failure.read().decode()
        return failure.code, (json.loads(text) if text.startswith("{") else {"raw": text[:200]})


def keys_of(items):
    return sorted({k for i in items for k in i})


def no_localized(items):
    return not (set(keys_of(items)) & LOCALIZED_KEYS)


# ---------------------------------------------------------------------------------------------------------------- P

def p_platform(ctx):
    rd = "/reference-data"
    st, cur = platform(rd + "/currencies")
    items = cur.get("items") or []
    check("AC-03", "currencies 全量：4 个 ACTIVE、标准属性齐全、无本地化名称",
          st == 200 and {i["currencyCode"] for i in items if i["status"] == "ACTIVE"} >= {"USD", "AED", "SAR", "EGP"}
          and all(i.get("numericCode") and i.get("name") and i.get("symbol") for i in items) and no_localized(items),
          {"keys": keys_of(items), "codes": [(i["currencyCode"], i["numericCode"], i["status"]) for i in items]})
    st, body = platform(rd + "/currencies?status=ACTIVE&codes=usd,%20AED,XXX")
    check("AC-03", "currencies codes 去空白转大写、不存在编码不报错", st == 200 and [i["currencyCode"] for i in body["items"]] == ["AED", "USD"],
          body)
    st, body = platform(rd + "/currencies?status=INACTIVE")
    check("AC-03", "currencies status=INACTIVE 筛选", st == 200 and all(i["status"] == "INACTIVE" for i in body["items"]), len(body.get("items") or []))
    st, body = platform(rd + "/countries")
    items = body.get("items") or []
    check("AC-04", "countries 249 行全部有三位码 / 数字码 / 英文名；区号 242；法定货币 248",
          st == 200 and len(items) == 249 and all(i.get("alpha3Code") and i.get("numericCode") and i.get("name") for i in items)
          and sum(1 for i in items if i.get("callingCode")) == 242 and sum(1 for i in items if i.get("defaultCurrencyCode")) == 248
          and no_localized(items), {"keys": keys_of(items), "noCalling": [i["countryCode"] for i in items if not i.get("callingCode")],
                                    "noCurrency": [i["countryCode"] for i in items if not i.get("defaultCurrencyCode")]})
    ctx["countries"] = items
    for country, expected in (("AE", 7), ("SA", 13), ("EG", 27)):
        st, body = platform(rd + f"/country-subdivisions?countryCode={country}&status=ACTIVE")
        sub = body.get("items") or []
        check("AC-03", f"country-subdivisions {country}：{expected} 行、ISO 连字符编码、国家一致",
              st == 200 and len(sub) == expected and all(i["subdivisionCode"].startswith(country + "-") and i["countryCode"] == country for i in sub)
              and no_localized(sub), {"keys": keys_of(sub), "sample": sub[:1]})
    st, body = platform(rd + "/industries?level=1")
    level1 = body.get("items") or []
    st2, body2 = platform(rd + "/industries?level=2&status=ACTIVE")
    level2 = body2.get("items") or []
    st3, retail = platform(rd + "/industries?parentCode=RETAIL")
    check("AC-05", "industries：大类 10、细类 32、RETAIL 下 5 个细类，细类都有建议 MCC",
          st == st2 == st3 == 200 and len(level1) == 10 and len(level2) == 32 and len(retail["items"]) == 5
          and all(i.get("defaultMccCode") and i.get("parentIndustryCode") for i in level2) and no_localized(level2),
          {"keys": keys_of(level2), "retail": [i["industryCode"] for i in retail["items"]]})
    mcc_codes = sorted({i["defaultMccCode"] for i in level2})
    st, body = platform(rd + "/mccs?codes=" + ",".join(mcc_codes) + "&page=1&size=50")
    found = {i["mccCode"]: i["status"] for i in body.get("items") or []}
    check("AC-05", f"每个细类的建议 MCC 存在且 ACTIVE（{len(mcc_codes)} 个）", st == 200 and all(found.get(c) == "ACTIVE" for c in mcc_codes),
          {"missing": [c for c in mcc_codes if found.get(c) != "ACTIVE"]})
    st, p1 = platform(rd + "/mccs?page=1&size=200")
    st2, p2 = platform(rd + "/mccs?page=2&size=200")
    st3, p9 = platform(rd + "/mccs?page=9&size=200")
    all_codes = [i["mccCode"] for i in (p1.get("items") or []) + (p2.get("items") or [])]
    check("AC-03", "mccs 分页：共 298、两页衔接无重复且升序、越界页为空但保留总数",
          st == st2 == st3 == 200 and len(all_codes) == len(set(all_codes)) == 298 and all_codes == sorted(all_codes)
          and p9.get("items") == [] and str(p9.get("total") or p9.get("totalCount") or p1.get("total")) == "298",
          {"page1": {k: v for k, v in p1.items() if k != "items"}, "page9": {k: v for k, v in p9.items() if k != "items"}})
    st, body = platform(rd + "/mccs?keyword=grocery&page=1&size=10")
    st2, body2 = platform(rd + "/mccs?keyword=541&page=1&size=10")
    check("AC-03", "mccs keyword：名称包含 grocery、编码前缀 541",
          st == st2 == 200 and any(i["mccCode"] == "5411" for i in body["items"]) and all(i["mccCode"].startswith("541") for i in body2["items"]),
          {"grocery": [i["mccCode"] for i in body["items"]], "541": [i["mccCode"] for i in body2["items"]]})
    high = [i["mccCode"] for i in (p1.get("items") or []) + (p2.get("items") or []) if i.get("networkHighRisk")]
    check("AC-03", "mccs 高风险标记 14 个", len(high) == 14, high)
    bad = [("/currencies?status=BOGUS", "未知状态"), ("/countries?codes=A1", "编码格式"), ("/countries?codes=AE,,SA", "连续逗号"),
           ("/countries?codes=" + ",".join(c["countryCode"] for c in items[:51]), "超过 50 个编码"),
           ("/industries?level=3", "level 越界"), ("/mccs?page=1&size=5", "size 非允许值"), ("/mccs?keyword=" + "x" * 65, "keyword 过长"),
           ("/country-subdivisions?countryCode=ARE", "countryCode 格式")]
    for path, label in bad:
        st, body = platform(rd + path)
        check("AC-03", f"400：{label}", st == 400 and body.get("code") == "INVALID_REQUEST", f"HTTP {st} {problem(body)}")
    # 旧接口形状（S-01 / AC-02）
    st, body = platform("/currency-specs")
    check("AC-02", "旧 /currency-specs 外层 items、元素字段不变", st == 200 and list(body) == ["items"]
          and keys_of(body["items"]) == ["currency", "minorUnit", "roundingMode", "status", "updateTime", "version"], keys_of(body.get("items") or []))
    st, body = platform("/country-regions")
    check("AC-02", "旧 /country-regions 249 行、元素只有 countryCode / regionCode", st == 200 and list(body) == ["items"]
          and len(body["items"]) == 249 and keys_of(body["items"]) == ["countryCode", "regionCode"], len(body.get("items") or []))


# ---------------------------------------------------------------------------------------------------------------- R / C

def r_ops_reference(ctx):
    ops = ctx["ops"]
    for path, expected in (("/countries", 249), ("/country-subdivisions?countryCode=SA", 13), ("/industries", 42), ("/mccs?page=1&size=200", 200)):
        st, body = ops.get("/reference-data-admin/v1" + path)
        items = body.get("items") or []
        check("AC-13", f"运营参考数据 {path}：{expected} 行、无本地化名称", st == 200 and len(items) == expected and no_localized(items),
              {"status": st, "count": len(items), "keys": keys_of(items)[:16], **({} if st == 200 else problem(body))})
    st, body = ops.get("/reference-data-admin/v1/mccs?status=WRONG")
    check("AC-13", "运营参考数据 非法 Query → 400", st == 400, f"HTTP {st} {problem(body)}")


def c_currency(ctx):
    ops = ctx["ops"]
    st, body = ops.get("/currency-admin/v1/currency-specs")
    items = {i["currency"]: i for i in body.get("items") or []}
    check("AC-13", "币种列表带数字码 / 名称 / 符号 / 图标字段", st == 200 and {"numericCode", "name", "symbol"} <= set(items.get("USD", {}))
          and {"USD", "AED", "SAR", "EGP"} <= set(items), {"keys": sorted(items.get("USD", {})), "codes": sorted(items)})
    usd = items["USD"]
    base = {"numericCode": usd["numericCode"], "name": usd["name"], "symbol": usd["symbol"], "minorUnit": usd["minorUnit"],
            "roundingMode": usd["roundingMode"], "expectedVersion": usd["version"], "reason": "stage5 acceptance"}
    st, body = ops.call("PUT", "/currency-admin/v1/currency-specs/USD", {**base, "name": "US Dollar (changed)", "symbol": "US$"})
    fields = sorted(e.get("field") for e in body.get("errors") or [])
    check("AC-13", "ACTIVE 币种改冻结字段 → 422，逐字段错误（name、symbol）", st == 422 and fields == ["name", "symbol"], f"HTTP {st} {problem(body)}")
    st, body = ops.call("PUT", "/currency-admin/v1/currency-specs/USD", {**base, "logoFileAddress": "/files/v1/999999999"})
    check("AC-13", "logo 指向不存在 / 非公开文件 → 422（logoFileAddress）", st == 422 and any(e.get("field") == "logoFileAddress" for e in body.get("errors") or []),
          f"HTTP {st} {problem(body)}")
    st, after = ops.get("/currency-admin/v1/currency-specs")
    now = {i["currency"]: i for i in after.get("items") or []}["USD"]
    check("AC-13", "被拒的修改未写入（USD 版本、名称不变）", now["version"] == usd["version"] and now["name"] == usd["name"], now)
    if "XTS" not in items:
        st, body = ops.call("POST", "/currency-admin/v1/currency-specs",
                            {"currency": "XTS", "numericCode": "963", "name": "Testing Currency Code", "symbol": "XTS", "minorUnit": 2,
                             "roundingMode": "HALF_UP", "reason": "stage5 acceptance draft (ISO test code)"})
        check("AC-13", "新建 DRAFT 币种 XTS（ISO 测试代码，保留供页面冒烟）", st in (200, 201), f"HTTP {st} {problem(body)}")
    st, body = ops.get("/currency-admin/v1/currency-specs")
    xts = {i["currency"]: i for i in body.get("items") or []}.get("XTS") or {}
    st, body = ops.call("PUT", "/currency-admin/v1/currency-specs/XTS",
                        {"numericCode": "963", "name": "Testing Currency Code", "symbol": "XT$" if xts.get("symbol") != "XT$" else "XTS",
                         "minorUnit": 3 if xts.get("minorUnit") == 2 else 2, "roundingMode": "HALF_UP", "expectedVersion": xts.get("version"),
                         "reason": "stage5 acceptance draft edit"})
    check("AC-13", "DRAFT 币种可改全部标准属性（符号、小数位）", st == 200 and xts.get("status") == "DRAFT", f"HTTP {st} {problem(body)} status={xts.get('status')}")
    ctx["xts"] = True


# ---------------------------------------------------------------------------------------------------------------- F / A

def schema_of(client, jurisdiction):
    st, schema = OA.form_schema(client, jurisdiction=jurisdiction, partyType="ORGANIZATION", organizationType="LLC")
    if st != 200:
        raise Stop(f"form-schema {jurisdiction} HTTP {st} {problem(schema)}")
    return schema


def f_form_schema(ctx):
    client = ctx["clientA"]
    for jurisdiction in ("AE", "SA", "EG"):
        schema = schema_of(client, jurisdiction)
        catalogs = schema.get("catalogs") or {}
        countries = schema.get("countries") or []
        currency = catalogs.get("REF_CURRENCY") or []
        components = {c["component"]: c for c in (schema.get("addressFormat") or {}).get("components") or []}
        state = components.get("stateOrProvince") or {}
        subdivisions = catalogs.get(state.get("catalogCode") or "", [])
        legacy = sorted(set(catalogs) & {"INDUSTRY", "CURRENCY", "STATE_OR_PROVINCE"})
        expected_sub = {"AE": 7, "SA": 13, "EG": 27}[jurisdiction]
        check("AC-11", f"form-schema {jurisdiction}：国家 249（含名称）、REF_CURRENCY 4 个 ACTIVE 带名称、无旧三类目录",
              len(countries) == 249 and all(c.get("code") and c.get("name") for c in countries)
              and sorted(i["code"] for i in currency) == ["AED", "EGP", "SAR", "USD"] and all(i.get("name") for i in currency) and not legacy,
              {"countrySample": countries[:1], "currency": currency, "legacy": legacy, "catalogs": sorted(catalogs)})
        if state.get("catalogCode"):
            check("AC-11", f"form-schema {jurisdiction}：省州组件 {state.get('catalogCode')} 补全 {expected_sub} 个本国行政区（countryCode、name）",
                  state["catalogCode"] == "REF_COUNTRY_SUBDIVISION" and len([s for s in subdivisions if s.get("countryCode") == jurisdiction]) == expected_sub
                  and all(s.get("name") for s in subdivisions), {"count": len(subdivisions), "sample": subdivisions[:2]})
        else:
            record("AC-11", f"form-schema {jurisdiction}：本国地址格式不含省州目录（{(schema.get('addressFormat') or {}).get('code')}）", "INFO",
                   {"components": list(components)})
        ctx.setdefault("schemas", {})[jurisdiction] = schema


def field_errors(body):
    return [(e.get("requirementKey") or e.get("field"), e.get("code")) for e in body.get("errors") or []]


def submission_check(client, application_id):
    st, body = client.get(f"/onboarding/v1/applications/{application_id}/submission-check")
    if st != 200:
        raise Stop(f"submission-check HTTP {st} {problem(body)}")
    return body


def expect_field_error(ac, client, application_id, label, key_part, check_body=None):
    body = check_body or submission_check(client, application_id)
    errs = field_errors(body)
    hit = [e for e in errs if e[0] and key_part in e[0] and e[1] == "FIELD_VALUE_NOT_IN_CATALOG"]
    check(ac, f"{label} → FIELD_VALUE_NOT_IN_CATALOG 挂在 {key_part}", body.get("ready") is False and hit,
          {"hit": hit, "errors": errs[:8]})


def country_field(schema):
    for _, _, field in OA.fields(schema):
        if field.get("widget") == "COUNTRY" and field.get("appliesTo") in ("PRIMARY", "BOTH"):
            return field["fieldKey"]
    return None


def new_application(client, ac, label, jurisdiction):
    st, created = OA.create_application(client, ac, label, jurisdiction, "ORGANIZATION", "LLC")
    if st not in (200, 201):
        raise Stop(f"{label} 创建失败 {problem(created)}")
    application_id = created["applicationId"]
    st, schema = OA.form_schema(client, applicationId=application_id)
    return application_id, schema


def fill(client, ac, label, application_id, schema, jurisdiction, run, email):
    people = OA.org_people(run + label[-1:])
    OA.fill_organization(client, ac, label, application_id, schema,
                         OA.org_ctx(run, email, f"Refdata {label} {run} LLC", jurisdiction=jurisdiction), people)


def a_ae_submission(ctx):
    client, run = ctx["clientA"], ctx["run"]
    application_id, schema = new_application(client, "AC-07", "AE", "AE")
    OA.IDS["appAE"] = application_id
    fill(client, "AC-07", "AE", application_id, schema, "AE", run, OA.IDS["clientAEmail"])
    body = submission_check(client, application_id)
    if not check("AC-07", "AE 合法填写后提交核对 ready", body.get("ready") is True, field_errors(body)[:8]):
        raise Stop("AE 未 ready")
    address = OA.address_values(schema, OA.org_ctx(run, "", "", "AE"))
    valid_state = address.get("stateOrProvince")
    ckey = country_field(schema)

    def patch(values, label):
        OA.save_draft(client, "AC-07", application_id, values, label)

    patch({"business": {"currency": "XYZ"}}, "AE 币种=XYZ（不存在）")
    expect_field_error("AC-07", client, application_id, "币种不存在", "business.currency")
    flip("t_ref_currency", "currency_code", "EGP", "INACTIVE")
    try:
        patch({"business": {"currency": "EGP"}}, "AE 币种=EGP（临时停用）")
        expect_field_error("AC-07", client, application_id, "币种停用", "business.currency")
    finally:
        restore_flips()
    patch({"business": {"currency": "AED"}}, "AE 币种恢复 AED")
    patch({"address": {**address, "stateOrProvince": "AE-ZZ"}}, "AE 省州=AE-ZZ（不存在）")
    expect_field_error("AC-07", client, application_id, "行政区不存在", "stateOrProvince")
    patch({"address": {**address, "stateOrProvince": "EG-C"}}, "AE 地址填 EG-C（国家不一致）")
    expect_field_error("AC-07", client, application_id, "行政区与地址国家不一致", "stateOrProvince")
    flip("t_ref_country_subdivision", "subdivision_code", "AE-UQ", "INACTIVE")
    try:
        patch({"address": {**address, "stateOrProvince": "AE-UQ"}}, "AE 省州=AE-UQ（临时停用）")
        expect_field_error("AC-07", client, application_id, "行政区停用", "stateOrProvince")
    finally:
        restore_flips()
    patch({"address": {**address, "stateOrProvince": valid_state}}, "AE 省州恢复")
    if ckey:
        section, name = ckey.split(".", 1)
        original = (OA.read_app(client, application_id).get("values") or {}).get(section, {}).get(name) or "AE"
        patch({section: {name: "XX"}}, f"AE {ckey}=XX（不存在）")
        check_body = submission_check(client, application_id)
        expect_field_error("AC-07", client, application_id, "国家不存在", ckey, check_body)
        st, reply = client.call("POST", f"/onboarding/v1/applications/{application_id}/submissions")
        app = OA.read_app(client, application_id)
        check("AC-07", "带不存在国家正式提交 → 422 SUBMISSION_INCOMPLETE，申请仍 DRAFT、无提交号",
              st == 422 and reply.get("code") == "SUBMISSION_INCOMPLETE" and app.get("status") == "DRAFT" and not app.get("submissionId"),
              {"status": st, "problem": problem(reply), "app": app.get("status")})
        flip("t_ref_country", "country_code", "AQ", "INACTIVE")
        try:
            patch({section: {name: "AQ"}}, f"AE {ckey}=AQ（临时停用）")
            expect_field_error("AC-07", client, application_id, "国家停用", ckey)
        finally:
            restore_flips()
        patch({section: {name: original}}, f"AE {ckey} 恢复 {original}")
    else:
        skip("AC-07", "国家不存在 / 停用", "AE LLC Schema 主主体没有 COUNTRY 控件")
    st, reply = client.call("PATCH", f"/onboarding/v1/applications/{application_id}/draft",
                            OA.patch_body({"address": {**address, "stateOrProvince": "AE_DU"}}))
    check("AC-07", "草稿保存：旧下划线省州 AE_DU 格式不合法 → 422", st == 422, f"HTTP {st} {problem(reply)}")
    body = submission_check(client, application_id)
    if not check("AC-07", "修正后提交核对 ready", body.get("ready") is True, field_errors(body)[:8]):
        raise Stop("AE 修正后未 ready")
    OA.submit(client, "AC-07", application_id, "AE")
    found = OA.find_case(ctx["ops"], application_id)
    if not check("AC-07", "AE 案件出现在运营工作台", found, application_id):
        raise Stop("无案件")
    ctx["caseAE"] = found["caseId"]
    OA.IDS["caseAE"] = found["caseId"]


# ---------------------------------------------------------------------------------------------------------------- K

def classifications(detail):
    """BFF 审核详情：当前分类在 classification.items（client-master O03 回执）。"""
    return (detail.get("classification") or {}).get("items") or []


def k_classification(ctx):
    ops, case_id = ctx["ops"], ctx["caseAE"]
    detail = OA.case_detail(ops, case_id)
    industries = detail.get("industries") or []
    children = [c for p in industries for c in p.get("children") or []]
    check("AC-08", "审核详情行业树：大类 10、细类 32、细类带建议 MCC（来自 Platform）",
          len(industries) == 10 and len(children) == 32 and all(c.get("defaultMccCode") for c in children),
          {"industriesStatus": detail.get("industriesStatus"), "sample": industries[:1]})
    check("AC-08", "审核详情 mccs 带建议 MCC 名称", detail.get("mccsStatus") in (None, "AVAILABLE") and len(detail.get("mccs") or []) >= 32,
          {"mccsStatus": detail.get("mccsStatus"), "count": len(detail.get("mccs") or [])})
    st, body = ops.get(CASE + "/mccs?keyword=apparel&page=1&size=10")
    st2, body2 = ops.get(CASE + "/mccs?keyword=565&page=1&size=10")
    check("AC-12", "分类页 MCC 搜索：名称 apparel、编码前缀 565", st == st2 == 200 and body.get("items") and all(
        i["mccCode"].startswith("565") for i in body2.get("items") or []) and body2.get("items"),
          {"apparel": [i["mccCode"] for i in body.get("items") or []], "565": [i["mccCode"] for i in body2.get("items") or []]})

    def put(items):
        return OA.command(ops, "PUT", f"/cases/{case_id}/classifications", {"items": items})

    before = classifications(detail)

    cases = [("只选大类 RETAIL", [{"industryCode": "RETAIL", "mccCode": "5651", "primary": True}], "items[0].industryCode"),
             ("未知行业", [{"industryCode": "RETAIL_NOPE", "mccCode": "5651", "primary": True}], "items[0].industryCode"),
             ("未知 MCC 0001", [{"industryCode": "RETAIL_APPAREL", "mccCode": "0001", "primary": True}], "items[0].mccCode"),
             ("第 2 行大类", [{"industryCode": "RETAIL_APPAREL", "mccCode": "5651", "primary": True},
                           {"industryCode": "TRAVEL", "mccCode": "4722", "primary": False}], "items[1].industryCode")]
    for label, items, path in cases:
        st, body = put(items)
        fields = [e.get("field") for e in body.get("errors") or []]
        check("AC-08", f"分类 {label} → 422，错误挂 {path}", st == 422 and path in fields, f"HTTP {st} {problem(body)}")
    flip("t_ref_mcc", "mcc_code", "5655", "INACTIVE")
    try:
        st, body = put([{"industryCode": "RETAIL_APPAREL", "mccCode": "5655", "primary": True}])
        check("AC-08", "分类 停用 MCC 5655 → 422（items[0].mccCode）", st == 422 and "items[0].mccCode" in [e.get("field") for e in body.get("errors") or []],
              f"HTTP {st} {problem(body)}")
    finally:
        restore_flips()
    after = classifications(OA.case_detail(ops, case_id))
    check("AC-08", "被拒的保存未写入（与拒绝前一致）", after == before, {"before": before, "after": after})
    suggested = next(c["defaultMccCode"] for c in children if c["code"] == "RETAIL_APPAREL") if children and "code" in children[0] else "5651"
    st, body = put([{"industryCode": "RETAIL_APPAREL", "mccCode": suggested, "primary": True}])
    saved = classifications(OA.case_detail(ops, case_id))
    check("AC-08", f"细类 RETAIL_APPAREL + 建议 MCC {suggested} 保存", st == 200 and any(
        c.get("industryCode") == "RETAIL_APPAREL" and c.get("mccCode") == suggested for c in saved), {"status": st, "saved": saved})
    st, body = put([{"industryCode": "RETAIL_APPAREL", "mccCode": "5699", "primary": True}])
    saved = classifications(OA.case_detail(ops, case_id))
    check("AC-08", "改选 MCC 5699 后保存的是运营选择", st == 200 and any(c.get("mccCode") == "5699" for c in saved), {"status": st, "saved": saved})
    ctx["savedClassification"] = saved
    # AC-10 存量一级行业可读
    rows = root_sql("mor_client_master_v2e2e", "SELECT c.industry_code, c.business_profile_id FROM t_client_business_classification c "
                                                "WHERE c.industry_code='RETAIL' LIMIT 20")
    legacy = None
    st, page = ops.get(CASE + "/cases?page=1&size=50")
    for item in page.get("items") or []:
        if str(item.get("caseId")) == str(case_id):
            continue
        st, detail = ops.get(f"{CASE}/cases/{item['caseId']}")
        if st == 200 and any(c.get("industryCode") == "RETAIL" for c in classifications(detail)):
            legacy = (item["caseId"], classifications(detail))
            break
    if legacy:
        check("AC-10", f"存量一级行业 RETAIL 案件 {legacy[0]} 详情可读", True, legacy[1])
    else:
        skip("AC-10", "存量一级行业案件详情", f"库中 RETAIL 分类 {len(rows)} 行，但工作台前 50 个案件中没有找到对应案件")


# ---------------------------------------------------------------------------------------------------------------- U / S

REF_OFF = (("t_ref_country", "t_ref_country__stage5_off"), ("t_ref_industry", "t_ref_industry__stage5_off"))


def u_platform_down(ctx):
    """只让 Platform 参考数据接口不可用：临时改名 t_ref_country / t_ref_industry（Platform、Gateway 认证、文件照常），结束时改回。"""
    client, ops = ctx["clientB"], ctx["ops"]
    eg_id, case_id = ctx["egApp"], ctx["caseAE"]
    before = classifications(OA.case_detail(ops, case_id))
    app_before = OA.read_app(client, eg_id)
    root_sql("mor_platform_v2e2e", "RENAME TABLE " + ", ".join(f"{a} TO {b}" for a, b in REF_OFF))
    record("SETUP", "临时改名 t_ref_country、t_ref_industry（参考数据接口不可用）", "INFO")
    try:
        st, body = platform("/reference-data/countries?codes=AE")
        check("AC-09", "前提：Platform 参考数据接口失败、Platform 本身在线", st >= 500, f"HTTP {st} {problem(body)}")
        st, body = OA.form_schema(client, jurisdiction="EG", partyType="ORGANIZATION", organizationType="LLC")
        check("AC-09", "form-schema → 503 DEPENDENCY_UNAVAILABLE（不返回缺选项的 Schema）", st == 503 and body.get("code") == "DEPENDENCY_UNAVAILABLE",
              f"HTTP {st} {problem(body)}")
        st, body = client.get(f"/onboarding/v1/applications/{eg_id}/submission-check")
        check("AC-09", "提交核对 → 503", st == 503, f"HTTP {st} {problem(body)}")
        st, body = client.call("POST", f"/onboarding/v1/applications/{eg_id}/submissions")
        app = OA.read_app(client, eg_id)
        check("AC-09", "正式提交 → 503，申请仍 DRAFT、无提交号、版本不变",
              st == 503 and app.get("status") == "DRAFT" and not app.get("submissionId") and app.get("version") == app_before.get("version"),
              {"status": st, "problem": problem(body), "app": app.get("status"), "version": [app_before.get("version"), app.get("version")]})
        st, body = OA.command(ops, "PUT", f"/cases/{case_id}/classifications",
                              {"items": [{"industryCode": "RETAIL_HOME", "mccCode": "5712", "primary": True}]})
        after = classifications(OA.case_detail(ops, case_id))
        check("AC-09", "分类保存 → 503 DEPENDENCY_UNAVAILABLE，原分类不变", st == 503 and body.get("code") == "DEPENDENCY_UNAVAILABLE" and after == before,
              {"status": st, "problem": problem(body), "before": before, "after": after})
        detail = OA.case_detail(ops, case_id)
        check("AC-09", "审核详情仍可打开，行业部分标 UNAVAILABLE", detail.get("industriesStatus") == "UNAVAILABLE",
              {"industriesStatus": detail.get("industriesStatus"), "mccsStatus": detail.get("mccsStatus")})
    finally:
        root_sql("mor_platform_v2e2e", "RENAME TABLE " + ", ".join(f"{b} TO {a}" for a, b in REF_OFF))
        st, body = platform("/reference-data/countries?codes=AE")
        check("SETUP", "改回 t_ref_country、t_ref_industry，参考数据接口恢复", st == 200 and body.get("items"), f"HTTP {st}")
    st, body = OA.form_schema(client, jurisdiction="EG", partyType="ORGANIZATION", organizationType="LLC")
    check("AC-09", "恢复后 form-schema 200", st == 200, f"HTTP {st}")


def prepare_eg(ctx):
    client = ctx["clientB"]
    application_id, schema = new_application(client, "AC-07", "EG", "EG")
    OA.IDS["appEG"] = application_id
    fill(client, "AC-07", "EG", application_id, schema, "EG", ctx["run"], OA.IDS["clientBEmail"])
    body = submission_check(client, application_id)
    if not check("AC-07", "EG 合法填写后提交核对 ready", body.get("ready") is True, field_errors(body)[:8]):
        raise Stop("EG 未 ready")
    ctx["egApp"] = application_id
    ctx["clientA_backup"] = ctx["clientA"]


def s_submit_sa_eg(ctx):
    OA.submit(ctx["clientB"], "AC-07", ctx["egApp"], "EG")
    client = ctx["clientC"]
    application_id, schema = new_application(client, "AC-07", "SA", "SA")
    OA.IDS["appSA"] = application_id
    fill(client, "AC-07", "SA", application_id, schema, "SA", ctx["run"], OA.IDS["clientCEmail"])
    body = submission_check(client, application_id)
    if not check("AC-07", "SA 合法填写后提交核对 ready", body.get("ready") is True, field_errors(body)[:8]):
        raise Stop("SA 未 ready")
    OA.submit(client, "AC-07", application_id, "SA")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--only", help="P,R,C,F,A,K,U,S")
    parser.add_argument("--case-ae", help="复用已提交的 AE 案件（只跑 K / U 时）")
    args = parser.parse_args()
    started = datetime.now(timezone.utc)
    run = started.strftime("%m%d%H%M%S")
    selected = (args.only or "P,R,C,F,A,K,U,S").split(",")
    ctx = {"run": run}
    if args.case_ae:
        ctx["caseAE"] = args.case_ae
    try:
        ctx["ops"] = OA.ops_login(E.secrets()["E2E_ADMIN_EMAIL"], E.secrets()["E2E_ADMIN_PASSWORD"])
        if set(selected) & {"F", "A", "K", "U", "S"}:
            ctx["clientA"] = OA.client_register("A", run)
        if set(selected) & {"U", "S"}:
            ctx["clientB"] = OA.client_register("B", run)
        if "S" in selected:
            ctx["clientC"] = OA.client_register("C", run)
    except Stop as stop:
        record("SETUP", "前置", "FAIL", str(stop))
        selected = []
    plan = [("P", p_platform), ("R", r_ops_reference), ("C", c_currency), ("F", f_form_schema), ("A", a_ae_submission),
            ("K", k_classification), ("E", prepare_eg), ("U", u_platform_down), ("S", s_submit_sa_eg)]
    for name, scenario in plan:
        if name not in selected and not (name == "E" and set(selected) & {"U", "S"}):
            continue
        print(f"\n==== {name} {scenario.__name__}", flush=True)
        try:
            scenario(ctx)
        except Stop as stop:
            record(name, "场景中止", "FAIL", str(stop))
        except Exception as failure:  # noqa: BLE001
            record(name, "场景异常", "FAIL", f"{type(failure).__name__}: {str(failure)[:400]}")
        finally:
            restore_flips()
    out = REQ / "verification" / f"acceptance-local-{started.strftime('%Y%m%dT%H%M%SZ')}.json"
    summary = {v: sum(1 for r in OA.RESULTS if r["verdict"] == v) for v in ("PASS", "FAIL", "SKIP", "INFO")}
    out.write_text(json.dumps({"env": "local", "startedAt": started.isoformat(), "finishedAt": datetime.now(timezone.utc).isoformat(),
                               "summary": summary, "ids": OA.IDS, "results": OA.RESULTS}, ensure_ascii=False, indent=1, default=str) + "\n")
    print(f"\n==== 汇总 {summary} → {out}")
    return 1 if summary["FAIL"] else 0


if __name__ == "__main__":
    sys.exit(main())
