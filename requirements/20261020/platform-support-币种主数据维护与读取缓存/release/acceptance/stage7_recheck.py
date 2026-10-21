"""币种主数据阶段 7 补验：首轮的两项脚本缺陷修正后重验（2026-10-21）。

1. 审计按 resource_id=XTS（Platform 记币种代码，不记数据库 ID）统计成功事件。
2. 只读人员加父菜单「平台配置」权限 GATEWAY_CONFIG_READ 后，导航应有币种页、没有维护按钮。
新建的第二名合成人员 / 角色追加到同一运行记录，由 stage7_cleanup.py 一并删除。
用法：python3 -I stage7_recheck.py <run.json>
"""
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ccy7_common as c  # noqa: E402

path = Path(sys.argv[1])
record = json.loads(path.read_text())
audit = dict(c.sql("mor_platform", "SELECT action_code, COUNT(*) FROM t_audit_event WHERE resource_type='CURRENCY_SPEC' "
                                   f"AND resource_id='{c.TEST_CURRENCY}' AND result='SUCCEEDED' GROUP BY action_code"))
c.check("AC-01", "审计：新建 1、修改 1、启停 2（均 SUCCEEDED）", audit == {"OPERATIONS_CURRENCY_SPEC_CREATE": "1",
        "OPERATIONS_CURRENCY_SPEC_UPDATE": "1", "OPERATIONS_CURRENCY_SPEC_STATUS": "2"}, audit)
admin = c.admin_token()
reader = c.synthetic_reader(admin, record["run"] + "B", record)
record["people"]["READ_B"] = record["people"].pop("READ")
path.write_text(json.dumps(record, ensure_ascii=False, indent=1))
os.chmod(path, 0o600)
status, nav = c.raw(reader, "GET", "/authorization/v1/navigation")
text = json.dumps(nav, ensure_ascii=False)
c.check("AC-01", "只读人员（含平台配置读）导航有币种页、没有维护按钮", status == 200 and "OPERATIONS_CURRENCY_SPECS" in text
        and "OPERATIONS_CURRENCY_SPEC_MANAGE" not in text, {"status": status})
record["recheck"] = c.results
path.write_text(json.dumps(record, ensure_ascii=False, indent=1))
os.chmod(path, 0o600)
passed = sum(1 for _, _, ok in c.results if ok)
print(f"\n补验 {passed}/{len(c.results)} 通过")
sys.exit(0 if passed == len(c.results) else 1)
