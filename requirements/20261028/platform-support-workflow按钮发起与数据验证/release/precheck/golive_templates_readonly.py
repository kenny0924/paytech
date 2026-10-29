"""REQ-05 阶段 6：在 mor-test mor_commercial 上以只读会话（root，SET SESSION TRANSACTION READ ONLY）把 3 条 GOLIVE_* 模板的 #{...}
替换为字面量逐条执行，按 Client 推演 ACC_REQ05_GOLIVE_COMMERCIAL 的走向，用于挑选阶段 7 验收数据。只读；口令不打印。
用法：python3 -I golive_templates_readonly.py <mt-ro.sh 路径>"""
import re, subprocess, sys
from pathlib import Path
SQL = Path("/Users/kenny/Desktop/Workspace/Code-MoR-wfdq/requirements/20261028/commercial-开通项前置门禁/db/data-query-templates.sql")
text = "\n".join(l for l in SQL.read_text().splitlines() if not l.lstrip().startswith("--"))
drafted, active, collection = [s.strip() for s in text.split(";") if "SELECT" in s]
def run(sql):
    out = subprocess.run([sys.argv[1], "mor_commercial"], input=sql + ";\n", capture_output=True, text=True, check=True).stdout
    rows = [l for l in out.splitlines() if l.startswith("|")]
    head = [c.strip() for c in rows[0].strip("|").split("|")]; vals = [c.strip() for c in rows[1].strip("|").split("|")]
    return dict(zip(head, vals))
clients = run("SELECT GROUP_CONCAT(DISTINCT client_id ORDER BY client_id) ids FROM t_agreement WHERE agreement_type='CLIENT'")["ids"].split(",")
for cid in clients:
    d = run(drafted.replace("#{clientId}", cid))
    path = [f"N3 drafted sat={d['satisfied']} missing={d['missing']} mode={d['out_business_mode']} agreement={d['out_agreement_id']}"]
    if d["satisfied"] == "1" and d["out_business_mode"] == "MOR":
        a = run(active.replace("#{agreementId}", d["out_agreement_id"]))
        path.append(f"N4 active sat={a['satisfied']} missing={a['missing']}")
        if a["satisfied"] == "1":
            c = run(collection.replace("#{agreementId}", d["out_agreement_id"]))
            path.append(f"N8 collection sat={c['satisfied']} missing={c['missing']} pending={c['out_pending_capabilities']}")
    elif d["satisfied"] == "1":
        path.append("default → PSP 人工复核")
    print(f"CLIENT:{cid} | " + " → ".join(path))
