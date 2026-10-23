"""第二轮复验：按前端规则（实例, 节点, 轮次, 类型）合并 admin 的待办，打印 JSON 计数（供浏览器比对）。"""
import json, sys
from seed_v2 import w
op = w.user2() if len(sys.argv) > 1 and sys.argv[1] == "user2" else w.admin()
items, page = [], 1
while True:
    res = op.get(f"/workflow/v1/my-tasks?page={page}&size=100")
    items += res["items"]
    if len(items) >= res["total"] or not res["items"]:
        break
    page += 1
keys = {}
for t in items:
    k = ("APPROVAL", t["taskId"]) if t["taskType"] == "APPROVAL" else ("ACTION", t["instanceId"], t["nodeKey"], t["roundNo"])
    keys.setdefault(k, []).append(t)
print(json.dumps({"raw": len(items), "all": len(keys), "action": sum(1 for k in keys if k[0] == "ACTION"),
                  "approval": sum(1 for k in keys if k[0] == "APPROVAL"),
                  "multi": {v[0]["instanceTitle"]: len(v) for k, v in keys.items() if len(v) > 1}}, ensure_ascii=False))
