"""阶段 7 缺陷 D2 修复后复验（commercial a9828758）并在同一镜像上回归 D1。
D2：模板版本读回带结算四字段；外汇点差模板 TPL-AE001-FX 复制 v1 建 v2，按读回内容原样保存成功，复核 → 排期 → 生效（不留 DRAFT）。
D1 回归：DEMO2-CL-101 v2 无相关性提示、v1 两条 USD 存卡费行有提示；DEMO2-CL-901（CARD VISA/MC + AED，存卡已开通）基于标准价 v2 生成新版本，
只有 AED 行（留一个 DRAFT 在反例合同上）。复用 stage7_acceptance.py 的辅助函数；不打印 Token、口令。用法：python3 -I stage7_recheck_d2.py
"""
import importlib.util
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("stage7", HERE / "stage7_acceptance.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
m.OUT = m.MAIN / f"local-environment/e2e/runtime/commercial-capability-stage7-recheck-d2-{m.STAMP}.json"

BASE = m.BASE
FX_PLAN, STD_PLAN = "238410806939684864", "238410803311611904"
PLAN_101, V1_101, V2_101 = "238411010594115584", "238411010732527616", "238417967673577472"
PLAN_901, AG_901 = "238411684908175360", "238411684283224064"
ROUTE = "commercial-terms/template-versions"
SETTLEMENT = {"settlementBusinessDays": 2, "businessCalendarCode": "AE_BUSINESS_DAYS", "settlementTimezone": "UTC", "cutoffSecondOfDay": 86399}


def settlement_ok(ws):
    return all(p.get("settlement") == SETTLEMENT for p in ws["prices"]) and len(ws["prices"]) > 0


def d2(admin, checker):
    rows = {t["planId"]: t for t in m.templates(admin)}
    std, fx = rows[STD_PLAN], rows[FX_PLAN]
    ws = m.workspace(admin, std["activeVersionId"])
    m.check("D2", "修复后：标准价 v2 读回每条价格带结算四字段（2 / AE_BUSINESS_DAYS / UTC / 86399）", settlement_ok(ws),
            [p.get("settlement") for p in ws["prices"][:2]])
    if fx["latestVersionNo"] != "1":
        m.skipped("D2", "外汇点差模板 v2", f"最新版本 v{fx['latestVersionNo']}（{fx['latestVersionStatus']}），跳过写入")
        return
    v = m.must(admin, "POST", f"{BASE}/commercial-terms/templates/{FX_PLAN}/versions",
               {"expectedChangeVersion": fx["changeVersion"], "copyFromVersionId": fx["activeVersionId"]}, "外汇点差模板 v2 复制 v1")
    m.record["ids"]["fxTemplateV2"] = v["versionId"]
    ws = m.workspace(admin, v["versionId"])
    m.check("D2", "修复后：复制得到的模板 DRAFT 版本读回带结算四字段", settlement_ok(ws), [p.get("settlement") for p in ws["prices"]])
    prices = [m.write_price(p) for p in ws["prices"]]   # 原样，不补任何字段
    status, saved = m.http(admin, "PUT", f"{BASE}/{ROUTE}/{v['versionId']}/prices",
                           {"expectedChangeVersion": ws["version"]["changeVersion"], "prices": prices})
    m.check("D2", "修复后：模板版本按读回内容原样保存 → 200", status == 200,
            {"status": status, "detail": m.detail(saved) if status >= 400 else None})
    if status != 200:
        return
    after = m.workspace(admin, v["versionId"])
    m.check("D2", "原样保存后价格与 v1 逐字段一致", m.price_signature(after) == m.price_signature(m.workspace(admin, fx["activeVersionId"])),
            None)
    v = m.submit_approve(admin, checker, saved, "外汇点差模板 v2", route=ROUTE)
    m.schedule(admin, v, "外汇点差模板 v2", route=ROUTE)


def d1(admin):
    ws2 = m.workspace(admin, V2_101)
    m.check("AC-05", "DEMO2-CL-101 v2（ACTIVE）只有 AED 三行、无相关性提示",
            ws2["version"]["status"] == "ACTIVE" and m.price_rows(ws2) == [("AED", "CARD_INTERNATIONAL"), ("AED", "CARD_STANDARD"),
                                                                           ("AED", "CARD_VISA_DEBIT")]
            and ws2["version"]["relevanceWarnings"] == [], {"status": ws2["version"]["status"], "rows": m.price_rows(ws2),
                                                            "warnings": ws2["version"]["relevanceWarnings"]})
    ws1 = m.workspace(admin, V1_101)
    warnings = sorted((w["currency"], w["pricingProfileCode"], w["reasonCode"]) for w in ws1["version"]["relevanceWarnings"] or [])
    m.check("AC-06", "DEMO2-CL-101 v1（SUPERSEDED，修复前证据）两条 USD 存卡费行返回 CURRENCY_NOT_ENABLED",
            ws1["version"]["status"] == "SUPERSEDED" and warnings == [("USD", "CARD_STANDARD", "CURRENCY_NOT_ENABLED"),
                                                                       ("USD", "CARD_VISA_DEBIT", "CURRENCY_NOT_ENABLED")],
            {"status": ws1["version"]["status"], "warnings": warnings})
    std_v2 = next(t for t in m.templates(admin) if t["planId"] == STD_PLAN)["activeVersionId"]
    if any(r["versionNo"] == "5" for r in m.versions(admin, PLAN_901)):
        m.skipped("AC-05", "DEMO2-CL-901 v5", "已存在，跳过写入")
        return
    change = m.plan_change(admin, AG_901, PLAN_901)
    v = m.must(admin, "POST", f"{BASE}/commercial-terms/plans/{PLAN_901}/versions",
               {"expectedChangeVersion": change, "sourceTemplateVersionId": std_v2}, "DEMO2-CL-901 基于标准价 v2 生成 v5（DRAFT）")
    m.record["ids"]["version901v5"] = v["versionId"]
    ws = m.workspace(admin, v["versionId"])
    rows = m.price_rows(ws)
    m.check("AC-05", "CARD 限 VISA/MC + AED、存卡已开通：基于标准价生成只有 AED 行（Visa 借记 / 境外卡 / 兜底），无 USD 存卡费行、无 Amex",
            rows == [("AED", "CARD_INTERNATIONAL"), ("AED", "CARD_STANDARD"), ("AED", "CARD_VISA_DEBIT")]
            and ws["version"]["relevanceWarnings"] == [], {"rows": rows, "warnings": ws["version"]["relevanceWarnings"]})


def main():
    s = m.e2e_http.secrets()
    admin = m.login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])["accessToken"]
    try:
        checker = m.create_checker(admin)
        for fn in (lambda: d1(admin), lambda: d2(admin, checker)):
            try:
                fn()
            except m.Stop:
                m.check("复验", "步骤失败", False, m.record["notes"][-1])
        fx_v2 = m.record["ids"].get("fxTemplateV2")
        activated = m.activate_pending(admin)
        if fx_v2:
            m.check("D2", "外汇点差模板 v2 排期后生效 ACTIVE（v1 被取代）", activated.get(fx_v2) == "ACTIVE", activated.get(fx_v2))
    except m.Stop:
        print("[中止] 前置步骤失败")
    finally:
        try:
            m.disable_checker(admin)
        except m.Stop:
            m.record["notes"].append({"warning": "合成审批人停用失败"})
        checks = m.record["checks"]
        m.record["summary"] = {"passed": sum(c["ok"] is True for c in checks), "failed": sum(c["ok"] is False for c in checks),
                               "skipped": sum(c["ok"] is None for c in checks)}
        m.save()
        print(m.record["summary"], m.OUT)


if __name__ == "__main__":
    main()
