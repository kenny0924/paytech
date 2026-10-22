"""3DS AC-02 + BIN AC-04 / AC-08 / AC-09：MID 3DS 能力与卡组织路由（阶段 5，本机 e2e）。

渠道配置经运营入口（Nginx 18444 → Gateway → ops-bff → channel-connector）写入，结束后停用（可重复执行，沿用已建对象）：
  MID2  PAYERMAX_MOCK / 同一技术账号 / AE001，外部编号 PMX-S5-MC：能力 CARD + MASTERCARD + * + USD + 5999，ACTION.PAY=true，未声明 3DS_MODE
  MID1（原有，'*' 行 3DS_MODE=SUPPORTED）追加能力 CARD + MASTERCARD …，ACTION.PAY=false（使 MASTERCARD 卡只剩 MID2 一个候选）
场景（脚本以 acquiring-service 身份直连 Payment，同 stage5_3ds.py）：
  mc_3ds     US MASTERCARD + 标记金额 333（风控要求 3DS）→ 冻结 MID2（未声明 3DS）→ FAILED THREE_DS_CHANNEL_UNAVAILABLE，渠道零请求（AC-02）
  mc_plain   US MASTERCARD 10.00 → schemeCode=MASTERCARD 选中 MID2（AC-09）
  visa_plain US VISA 10.00 → MID1（'*' 行），Token 快照 VISA/CREDIT/US/NORTH_AMERICA/版本/6 位（AC-07/08 Payment 侧）
  unknown    未识别 BIN → 快照未识别、不传 schemeCode、只命中 '*'（MID1），交易不失败（AC-04、AC-09）
  ng_review  NG 发卡（需导入的 BIN 版本含 NG 号段；版本未激活时跳过）→ Risk 审核（REVIEW），不发渠道请求
输出 stage5-routing.json（不含卡号）。用法：python3 -I stage5_routing.py setup|run|teardown
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parents[2] / "20261020/channel-connector-统一对外渠道出口/verification"))
import stage5_3ds as t  # noqa: E402
import stage5_e2e as s5  # noqa: E402

OUT = HERE / "stage5-routing.json"
STATE = t.RUNTIME / "card3ds/routing-state.json"
CHANNEL_ID = "238501215359533056"
MID1 = "238501217200832512"
ADMIN = "/channel-admin/v1"


def capability(mid, pay, three_ds=None):
    details = [{"capabilityCode": "ACTION.PAY", "booleanValue": pay, "decimalValue": None, "codeValue": None},
               {"capabilityCode": "AMOUNT.MIN", "booleanValue": None, "decimalValue": "0.01", "codeValue": None},
               {"capabilityCode": "AMOUNT.MAX", "booleanValue": None, "decimalValue": "100000", "codeValue": None}]
    if three_ds:
        details.append({"capabilityCode": "SECURITY.3DS_MODE", "booleanValue": None, "decimalValue": None, "codeValue": three_ds})
    return {"midId": mid, "paymentMethodCode": "CARD", "schemeCode": "MASTERCARD", "countryCode": "*", "currency": "USD",
            "mccCode": "5999", "details": details}


def setup():
    token = s5.login()
    state = json.loads(STATE.read_text()) if STATE.exists() else {}
    if "mid2" not in state:
        mid = s5.must("create MID2", *s5.ops(token, "POST", f"{ADMIN}/mids", {
            "channelId": CHANNEL_ID, "channelAccountId": t.CHANNEL_ACCOUNT_ID, "externalMidCode": "PMX-S5-MC", "entityNo": t.ENTITY_NO}),
            (200, 201))
        state["mid2"] = mid["midId"]
    s5.must("activate MID2", *s5.ops(token, "POST", f"{ADMIN}/mids/{state['mid2']}/status",
                                     {"targetStatus": "ACTIVE", "reason": "3DS / BIN 阶段 5 本机路由"}), (200, 400, 409))
    for key, body in (("mid2Capability", capability(state["mid2"], True)), ("mid1McCapability", capability(MID1, False))):
        if key not in state:
            created = s5.must("create " + key, *s5.ops(token, "POST", f"{ADMIN}/mid-capabilities", body), (200, 201))
            state[key] = created["midCapabilityId"]
        else:
            s5.must("enable " + key, *s5.ops(token, "POST", f"{ADMIN}/mid-capabilities/{state[key]}/status",
                                              {"targetStatus": "ENABLED"}), (200, 400, 409))
    STATE.write_text(json.dumps(state))
    print("setup", state)
    return state


def teardown():
    token = s5.login()
    state = json.loads(STATE.read_text())
    out = {}
    for key in ("mid2Capability", "mid1McCapability"):
        out[key] = s5.ops(token, "POST", f"{ADMIN}/mid-capabilities/{state[key]}/status", {"targetStatus": "DISABLED"})[0]
    out["mid2"] = s5.ops(token, "POST", f"{ADMIN}/mids/{state['mid2']}/status",
                         {"targetStatus": "SUSPENDED", "reason": "3DS / BIN 阶段 5 本机路由结束"})[0]
    print("teardown", out)
    return out


def token_snapshot(sub):
    rows = t.psql("SELECT IFNULL(card_brand,''), IFNULL(funding_type,''), IFNULL(card_level,''), IFNULL(card_product,''), "
                  "IFNULL(issuer_country,''), IFNULL(issuer_region,''), IFNULL(bin_library_version_no,''), IFNULL(bin_match_length,'') "
                  f"FROM t_payment_instrument_token WHERE payment_intent_id={sub.intent} AND source_submission_id={sub.submission}")
    return dict(zip(("cardBrand", "fundingType", "cardLevel", "cardProduct", "issuerCountry", "issuerRegion", "binLibraryVersionNo",
                     "binMatchLength"), rows[0])) if rows else None


def routed(sub):
    rows = t.psql(f"SELECT selected_mid_id, decision_status, IFNULL(decision_reason_code,'') FROM t_routing_decision "
                  f"WHERE payment_attempt_id={sub.attempt}") if sub.attempt else []
    return rows[0] if rows else None


def case(card, amount):
    sub = t.Submission(amount)
    sub.apply(card)
    status, body = sub.pay()
    return {"payHttp": status, "payStatus": body.get("status"), "payCode": body.get("code"), "detail": str(body.get("detail"))[:160],
            "attemptId": sub.attempt, "token": token_snapshot(sub), "routing": routed(sub),
            "risk": t.risk_assessment(sub.attempt) if sub.attempt else None,
            "facts": sub.facts() if sub.attempt else None, "mock": t.journal_for(sub.attempt) if sub.attempt else []}


def run():
    state = json.loads(STATE.read_text())
    result = {"state": state,
              "mc_3ds": case("US_MC", "333.00"), "mc_plain": case("US_MC", "10.00"), "visa_plain": case("US_VISA", "10.00"),
              "unknown": case("UNRECOGNIZED", "10.00")}
    if t.h.sql("card", "SELECT COUNT(*) FROM t_card_bin_range r JOIN t_card_bin_library_version v ON v.version_id=r.version_id "
                       "WHERE v.active_slot=1 AND r.issuer_country='NG'")[0][0] != "0":
        result["ng_review"] = case("NG_MC", "10.00")
    previous = json.loads(OUT.read_text()) if OUT.exists() else {}
    previous[t.now()] = result
    text = json.dumps(previous, ensure_ascii=False, indent=2)
    for pan in t.CARDS.values():
        assert pan not in text
    OUT.write_text(text + "\n")
    print(json.dumps(result, ensure_ascii=False)[:6000])


if __name__ == "__main__":
    {"setup": setup, "run": run, "teardown": teardown}[sys.argv[1]]()
