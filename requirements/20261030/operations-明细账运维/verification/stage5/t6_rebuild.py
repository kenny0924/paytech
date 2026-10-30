"""AC-05 / 06 / 11：篡改 ZQ344 一个 Owner 账户余额 → 经运营入口重建 → 余额恢复、changedAccountCount=1；再次重建为 0；无过账流 400。"""
import json
import t6lib as L

st = L.load()
tok = L.login()
en = st["entityNo"]
sel = (f"SELECT id, debit_total, credit_total, net_balance, balance_version FROM t_owner_account WHERE entity_id='{en}' "
       "AND account_code='CLIENT_PAYABLE' AND balance_bucket='PENDING'")
before = L.q("mor_account_v2e2e", sel)[0]
print("before", before)
L.q("mor_account_v2e2e", f"UPDATE t_owner_account SET credit_total=credit_total+5, net_balance=net_balance+5 WHERE id={before[0]}")
tampered = L.q("mor_account_v2e2e", sel)[0]
print("tampered", tampered)
body = {"entityType": "OPERATING_ENTITY", "entityId": en, "currency": "USD"}
s1, b1, t1 = L.ops("POST", "/finance/posting-rebuilds/v1", tok, body)
after = L.q("mor_account_v2e2e", sel)[0]
print("rebuild#1", s1, b1, f"{t1:.2f}s", "after", after)
s2, b2, t2 = L.ops("POST", "/finance/posting-rebuilds/v1", tok, body)
print("rebuild#2", s2, b2, f"{t2:.2f}s", "after", L.q("mor_account_v2e2e", sel)[0])
s3, b3, _ = L.ops("POST", "/finance/posting-rebuilds/v1", tok, {"entityType": "FINANCE_ENTITY", "entityId": st["financeEntityId"], "currency": "USD"})
print("no-stream", s3, b3)
s4, b4, _ = L.ops("POST", "/finance/posting-rebuilds/v1", tok, {"entityType": "OPERATING_ENTITY", "entityId": en, "currency": "EUR"})
print("no-stream EUR", s4, b4)
st["rebuild"] = {"before": before, "tampered": tampered, "after": after, "r1": [s1, b1, round(t1, 2)], "r2": [s2, b2, round(t2, 2)],
                 "noStreamFE": [s3, b3], "noStreamEUR": [s4, b4]}
L.save(st)
