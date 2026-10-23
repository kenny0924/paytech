"""运营控制台多语言迁移阶段 7 接口验收（第 ⑤ 步；只读：登录管理员后只发 GET 与只读 POST 查询“价格试算”）。

A. 运行：2 个容器为候选镜像、healthy、重启 0；BFF 自 --since 起 ERROR 0。
B. manifest（en、zh-CN）：默认语言 en、语言 en / zh-CN、各 21 个命名空间；每个语言包公开地址匿名 200、摘要 = hash、
   key 集合与内容 = 本包输入文件、CORS 允许运营 Origin；语言包对象 Cache-Control 只记录。
C. 新 BFF 合同：
   C1 角色权限树 GET /iam/v1/role-permission-options：每个节点带非空 code；资源节点 code 为资源编码（能在 nav.resource.* 中找到的计数），
      “其他权限”分组（若有）code 与 displayName 均为 OTHER_PERMISSIONS、不含中文；
   C2 审计详情 GET /audit/v1/events/{id}（取第一页前 10 条）：changes / attributes 只有 key 与值，不再有 label；
   C3 价格试算 POST /commercial-admin/v1/commercial-prices/simulate（BFF 注明“POST 查询，不写任何数据”）：候选输入取自
      mor_commercial 只读 SELECT 的 ACTIVE 条款价格；示例费用各组件只有 notes[{code, params}]（code 属于 9 个编码、params 为字符串表），
      没有 note 字段；至少一个组件给出说明，且每个说明编码在 commercial.simulator.note.* 中有 en / zh-CN 文案。
运行记录写 I18N7_STATE/stage7-api-<时间>.json（0600）。
用法：python3 -I stage7_api.py [--since <UTC ISO，默认第 ③ 步之前不检查日志>] [--dry-run]
  --dry-run：只核对本机输入、列出将调用的接口与试算候选（只读 SQL），不登录、不调用接口。
"""
import argparse
import hashlib
import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.dont_write_bytecode = True  # 不在发布包与前例目录留下 __pycache__
sys.path.insert(0, str(Path(__file__).resolve().parent))
import opsi18n_common as o  # noqa: E402

c = o.c
NOTE_CODES = {"BAND_HIT", "BAND_NONE", "PROGRESSIVE_BANDS", "MIN_APPLIED", "MAX_APPLIED", "BASE_DEPENDS_ON_FEE",
              "COMPONENT_UNSUPPORTED", "BAND_BASIS_PERIODIC", "BAND_MODE_UNSUPPORTED"}
SIMULATE = "/commercial-admin/v1/commercial-prices/simulate"


def runtime(since):
    for service in c.SERVICES:
        c.check("A", f"{service} 为候选镜像 {o.CANDIDATE[service][7:19]}", c.container(service, "{{.Image}}") == o.CANDIDATE[service])
        health = c.container(service, "{{.State.Health.Status}} {{.RestartCount}}")
        c.check("A", f"{service} healthy、重启 0", health == "healthy 0", health)
    if since:
        errors = c.errors_since("operations-console-bff", since)
        c.check("A", f"operations-console-bff 自 {since} 起 ERROR 0", not errors, errors[:3])


def manifests(files, record):
    expected = {"en": {ns: {k: o.text_of(v) for k, v in d.items()} for ns, d in files["en"][1]["namespaces"].items()},
                "zh-CN": files["zh-CN"][1]["namespaces"]}
    for locale in ("en", "zh-CN"):
        status, manifest, _, headers = c.raw(None, "GET", f"?locale={locale}", prefix=c.OPS_MANIFEST, base=c.OPS, key=False)
        record.setdefault("manifest", {})[locale] = {"releaseNo": manifest.get("releaseNo"), "cache": headers.get("Cache-Control")}
        c.check("B", f"manifest ?locale={locale}：200、默认 en、语言 en / zh-CN（#{manifest.get('releaseNo')}）",
                status == 200 and manifest.get("defaultLocale") == "en" and sorted(l["code"] for l in manifest.get("locales", [])) == ["en", "zh-CN"],
                {"status": status, "defaultLocale": manifest.get("defaultLocale")})
        bundles = manifest.get("bundles") or {}
        for loc in sorted(bundles):
            c.check("B", f"manifest({locale}) {loc} 列出 21 个命名空间", sorted(bundles[loc]) == o.ALL_NAMESPACES,
                    sorted(set(bundles[loc]) ^ set(o.ALL_NAMESPACES)))
        if locale != "zh-CN":
            continue
        for loc in ("en", "zh-CN"):
            for namespace, bundle in sorted(bundles.get(loc, {}).items()):
                st, body, hdrs = c.public_get(bundle["url"], origin=c.OPS)
                try:
                    content = json.loads(body)
                except ValueError:
                    content = None
                want = expected[loc][namespace]
                c.check("B", f"{loc}/{namespace} 匿名 200、摘要一致、key 集合与内容 = 输入文件（{len(want)}）、CORS",
                        st == 200 and hashlib.sha256(body).hexdigest() == bundle["hash"] and content == want
                        and hdrs.get("access-control-allow-origin") == c.OPS,
                        {"status": st, "keysMissing": sorted(set(want) - set(content or {}))[:3], "acao": hdrs.get("access-control-allow-origin")})
                record.setdefault("bundles", []).append({"locale": loc, "namespace": namespace, "bytes": len(body),
                                                         "cacheControl": hdrs.get("cache-control")})


def walk(nodes):
    for node in nodes or []:
        yield node
        yield from walk(node.get("children"))


def permission_tree(token, files, record):
    status, body, _, _ = c.raw(token, "GET", "/iam/v1/role-permission-options")
    nodes = list(walk(body.get("nodes")))
    c.check("C1", f"角色权限树 200、{len(nodes)} 个节点", status == 200 and nodes, {"status": status, "code": body.get("code")})
    missing = [n.get("nodeId") for n in nodes if not isinstance(n.get("code"), str) or not n.get("code")]
    c.check("C1", "每个节点都有非空 code", not missing, missing[:5])
    nav = {k.removeprefix("nav.resource.") for k in files["en"][1]["namespaces"]["nav"] if k.startswith("nav.resource.")}
    resources = [n for n in nodes if str(n.get("nodeId", "")).startswith("resource:")]
    translated = [n for n in resources if n["code"] in nav]
    c.check("C1", f"资源节点 code 为资源编码：{len(translated)}/{len(resources)} 个有 nav.resource 文案（其余回退 displayName）",
            resources and len(translated) * 2 >= len(resources), [n["code"] for n in resources if n["code"] not in nav][:5])
    others = [n for n in nodes if n.get("code") == "OTHER_PERMISSIONS"]
    if others:
        c.check("C1", "“其他权限”分组 code = displayName = OTHER_PERMISSIONS、无中文", all(
            n.get("displayName") == "OTHER_PERMISSIONS" and not c.CHINESE.search(n.get("displayName", "")) for n in others), others[0])
    else:
        c.unverified("C1", "“其他权限”分组", "当前没有未挂资源的权限，BFF 不生成该分组（本机 RolePermissionTreeAutoTest 覆盖）")
    groups = [n for n in nodes if not n.get("permissionId") and not str(n.get("nodeId", "")).startswith("resource:")]
    record["permissionTree"] = {"nodes": len(nodes), "resources": len(resources), "translated": len(translated), "otherGroup": bool(others),
                                "groupCodes": sorted({n["code"] for n in groups})[:10]}


def audit(token, record):
    status, page, _, _ = c.raw(token, "GET", "/audit/v1/events?page=1&size=50")
    items = page.get("items") or []
    c.check("C2", f"审计列表 200（{len(items)} 条）", status == 200 and items, {"status": status})
    for n in (2, 3):
        st, more, _, _ = c.raw(token, "GET", f"/audit/v1/events?page={n}&size=50")
        items += (more.get("items") or []) if st == 200 else []
    seen_keys, with_label, checked, fetched = set(), [], 0, 0
    for item in items:
        if checked >= 10 or fetched >= 60:
            break
        fetched += 1
        st, detail, _, _ = c.raw(token, "GET", f"/audit/v1/events/{item['auditEventId']}")
        if st != 200:
            c.check("C2", f"审计详情 {item['auditEventId']} 200", False, {"status": st, "code": detail.get("code")})
            continue
        parts = (detail.get("changes") or []) + (detail.get("attributes") or [])
        if not parts:
            continue
        checked += 1
        for part in parts:
            seen_keys.add(part.get("key"))
            if "label" in part:
                with_label.append(item["auditEventId"])
    c.check("C2", f"{checked} 条带字段的审计详情（查看 {fetched} 条）changes / attributes 无 label 字段（出现的 key {len(seen_keys)} 个）",
            checked > 0 and not with_label and seen_keys, {"withLabel": with_label[:3]})
    record["auditKeys"] = sorted(k for k in seen_keys if k)


def simulation_candidates():
    """mor_commercial 只读：ACTIVE 条款版本的价格，优先有上下限 / 阶梯 / 依赖其他费用（会产生说明）的组件。"""
    rows = c.sql("mor_commercial", """
        SELECT v.term_version_id, v.agreement_id, p.term_type, p.owner_type, IFNULL(p.client_id, ''), IFNULL(p.local_partner_id, ''),
               pr.entity_no, pr.currency, IFNULL(pf.payment_method_code, ''),
               SUM(cc.min_amount IS NOT NULL OR cc.max_amount IS NOT NULL OR cc.band_mode IS NOT NULL
                   OR cc.calculation_base IN ('ACTUAL_CLIENT_FEE', 'BUY_RATE_DIFFERENCE')) AS noted
        FROM t_commercial_term_version v JOIN t_commercial_term_plan p USING (term_plan_id)
        JOIN t_commercial_term_price pr USING (term_version_id) JOIN t_pricing_profile pf ON pf.pricing_profile_code = pr.pricing_profile_code
        JOIN t_commercial_term_component cc ON cc.term_price_id = pr.term_price_id
        WHERE v.status = 'ACTIVE' AND p.owner_type IN ('CLIENT', 'LOCAL_PARTNER') AND v.agreement_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM t_pricing_profile_cond pc WHERE pc.pricing_profile_id = pf.pricing_profile_id)
          AND v.effective_from <= UTC_TIMESTAMP(6) AND (v.effective_to IS NULL OR v.effective_to > UTC_TIMESTAMP(6))
        GROUP BY v.term_version_id, v.agreement_id, p.term_type, p.owner_type, p.client_id, p.local_partner_id, pr.entity_no, pr.currency,
                 pf.payment_method_code
        HAVING noted > 0 ORDER BY noted DESC, v.term_version_id LIMIT 8""")
    as_of = (datetime.now(timezone.utc) - timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    out = []
    for version, agreement, term_type, owner, client, partner, entity, currency, method, _ in rows:
        body = {"termVersionId": version, "agreementId": agreement, "termType": term_type, "entityNo": entity, "currency": currency,
                "contextAsOfTime": as_of, "paymentFacts": {"paymentMethodCode": method or None, "dimensions": {}}}
        body["clientId" if owner == "CLIENT" else "localPartnerId"] = client or partner
        for amount in ("1.00", "100000.00"):  # 小额触发最低金额、大额触发最高金额 / 高档位
            out.append({**body, "amount": amount})
    return out


def simulation(token, files, record):
    candidates = simulation_candidates()
    if not c.check("C3", f"试算候选（mor_commercial 只读）{len(candidates)} 个", candidates):
        return
    en_keys, zh_keys = files["en"][1]["namespaces"]["commercial"], files["zh-CN"][1]["namespaces"]["commercial"]
    notes, bad, legacy, ok_calls = [], [], [], 0
    for body in candidates:
        status, result, _, _ = c.raw(token, "POST", SIMULATE, body)
        if status != 200:
            record.setdefault("simulateRejected", []).append({"termVersionId": body["termVersionId"], "status": status, "code": result.get("code")})
            continue
        ok_calls += 1
        for event in (result.get("illustration") or {}).get("feeEvents") or []:
            for component in event.get("components") or []:
                if "note" in component:
                    legacy.append(component.get("componentCode"))
                for note in component.get("notes") or []:
                    notes.append(note)
                    if note.get("code") not in NOTE_CODES or not isinstance(note.get("params"), dict) \
                            or not all(isinstance(v, str) for v in note["params"].values()):
                        bad.append(note)
        if len({n.get("code") for n in notes}) >= 2:
            record["simulateSample"] = body
            break
    c.check("C3", f"价格试算 200（{ok_calls} 次）", ok_calls > 0, record.get("simulateRejected", [])[:3])
    c.check("C3", "组件不再返回 note 字符串", not legacy, legacy[:3])
    c.check("C3", f"notes 为 {{code, params}}：{len(notes)} 条，编码 {sorted({n.get('code') for n in notes})}", notes and not bad, bad[:3])
    missing = sorted({n["code"] for n in notes if f"commercial.simulator.note.{n['code']}" not in en_keys
                      or f"commercial.simulator.note.{n['code']}" not in zh_keys})
    c.check("C3", "每个说明编码在 commercial.simulator.note.* 有 en / zh-CN 文案", not missing, missing)
    record["simulateNotes"] = notes[:20]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--since")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    files = o.load_bundles()
    for locale, name in (("en", o.EN_FILE), ("zh-CN", o.ZH_FILE)):
        c.check("输入", f"{name} SHA256SUMS 一致", files[locale][2])
    if args.dry_run:
        candidates = simulation_candidates()
        c.check("干跑", f"试算候选（只读 SQL）{len(candidates)} 个：{sorted({(b['termType'], b['termVersionId']) for b in candidates})}", candidates)
        print("将调用：GET manifest ×2、公开桶语言包 ×42、GET /iam/v1/role-permission-options、GET /audit/v1/events 与前 10 条详情、"
              f"POST {SIMULATE}（只读查询）")
        return c.summary("接口验收（干跑，未调用接口）")
    record = {"startedAt": c.now_iso()}
    runtime(args.since)
    manifests(files, record)
    token = c.admin_token()
    permission_tree(token, files, record)
    audit(token, record)
    simulation(token, files, record)
    print("运行记录：", c.save(f"stage7-api-{record['startedAt'].replace(':', '')}.json", record))
    return c.summary("运营控制台多语言接口验收")


if __name__ == "__main__":
    sys.exit(main())
