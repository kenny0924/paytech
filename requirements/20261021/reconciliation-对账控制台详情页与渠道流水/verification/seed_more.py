"""补充种子：账单 F（CA2，25 行无对应平台流水的 PAYMENT → MATCHING），用于账单行分页 / ?line= 跨页高亮与渠道流水第 2 页。
用法：python3 -I seed_more.py <run-*.json>"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402
import seed  # noqa: E402

path = Path(sys.argv[1])
record = json.loads(path.read_text())
seed.RECORD, seed.record = path, record
maker = c.login(record["people"]["MAKER"]["email"], record["people"]["MAKER"]["password"])
run = record["run"]
lines = [(i, "PAYMENT", "CREDIT", f"{i}.00", "0", "NI_ORDER_REF", f"NI-F{i:02d}-{run}", seed.D(5 + i % 3), "T03", "") for i in range(1, 26)]
f = seed.import_statement(maker, seed.csv_statement(record["ca2"]["clearingAccountCode"], f"F-{run}", lines), "F.csv")
record["statements"]["F"] = f
path.write_text(json.dumps(record, ensure_ascii=False, indent=2))
print("F", f, seed.statement_status(maker, f))
