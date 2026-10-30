"""T6 造数第 1 步：新 OE（ZQnnn）与 Finance Entity 的日历、当月 OPEN 期间、OE / FE USD 账簿（经运营入口，管理员）。状态写 t6-state.json。"""
import random
from datetime import datetime, timedelta, timezone
import t6lib as L

st = L.load()
tok = L.login()
if "entityNo" not in st:
    used = {r[0] for r in L.q("mor_ledger_v2e2e", "SELECT entity_id FROM t_ledger_book UNION SELECT entity_id FROM t_fiscal_calendar")}
    used |= {r[0] for r in L.q("mor_account_v2e2e", "SELECT entity_id FROM t_account_posting_stream UNION SELECT entity_id FROM t_owner_account")}
    used |= {r[0] for r in L.q("mor_billing_v2e2e", "SELECT DISTINCT entity_no FROM t_billing_fact")}
    st["entityNo"] = next(f"ZQ{n:03d}" for n in range(random.randint(100, 800), 1000) if f"ZQ{n:03d}" not in used)
    st["clientId"] = "88" + str(random.randint(10**15, 10**16 - 1))
    st["partnerId"] = "89" + str(random.randint(10**15, 10**16 - 1))
    st["legalPartyId"] = "87" + str(random.randint(10**15, 10**16 - 1))
    L.save(st)
en = st["entityNo"]
print("entity", en)

if "financeEntityId" not in st:
    s, b, _ = L.ops("POST", "/finance/finance-entities/v1", tok, {"entityCode": "PLOPS_" + en, "legalPartyId": st["legalPartyId"],
                                                                 "displayName": "Plops T6 FE " + en})
    print("FE", s, b)
    st["financeEntityId"] = b["financeEntityId"]
    L.save(st)

today = datetime.now(timezone(timedelta(hours=4))).date()
first = today.replace(day=1)
nxt = (first + timedelta(days=32)).replace(day=1)
for label, etype, eid in (("OE", "OPERATING_ENTITY", en), ("FE", "FINANCE_ENTITY", st["financeEntityId"])):
    k = f"{label}"
    rec = st.setdefault(k, {})
    if "calendarId" not in rec:
        s, b, _ = L.ops("POST", "/finance/fiscal-calendars/v1", tok, {"entityType": etype, "entityId": eid,
                        "calendarCode": f"PLOPS-{label}-{en}", "timezone": "Asia/Dubai", "yearStartMonth": 1})
        print(label, "calendar", s, b)
        rec["calendarId"] = b["fiscalCalendarId"]; L.save(st)
    if "periodId" not in rec:
        s, b, _ = L.ops("POST", f"/finance/fiscal-calendars/v1/{rec['calendarId']}/periods", tok,
                        {"fiscalYear": first.year, "periodNo": first.month, "startDate": first.isoformat(),
                         "endDate": (nxt - timedelta(days=1)).isoformat()})
        print(label, "period", s, b)
        rec["periodId"] = b["accountingPeriodId"]; L.save(st)
        s, b, _ = L.ops("POST", f"/finance/accounting-periods/v1/{rec['periodId']}/open", tok)
        print(label, "open", s, b.get("status") if isinstance(b, dict) else b)
    if "bookId" not in rec:
        s, b, _ = L.ops("POST", "/finance/ledger-books/v1", tok, {"entityType": etype, "entityId": eid, "currency": "USD", "chartCode": "STANDARD"})
        print(label, "book", s, b)
        rec["bookId"] = b["ledgerBookId"]; L.save(st)
print({k: st[k] for k in st})
