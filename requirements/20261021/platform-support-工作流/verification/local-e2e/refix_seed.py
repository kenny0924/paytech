"""补 user2 的可批量审批待办：发起 4 个 PAR 实例并由 admin 提交 A 节点，使每个实例的 b2（纯审批，user2）进入审批。"""
import time
import workflow_flow as w
s = w.load(); a, b = w.admin(), w.user2(); made = []
for i in range(4):
    iid = a.send("POST", "/workflow/v1/instances", {"definitionId": s["parDefinitionId"], "versionNo": 1,
                                                    "title": f"E2E BATCH {i + 1} {time.strftime('%H%M%S')}",
                                                    "startValues": {"channelCode": "E2E-BATCH", "paymentMethod": "CARD"}})["objectId"]
    run = next(x for x in w.detail(a, iid)["actionRuns"] if x["actionKey"] == "aForm")
    a.send("POST", f"/workflow/v1/action-runs/{run['actionRunId']}/submission", {"output": {"merchantName": "Batch", "monthlyVolume": "1"}})
    made.append(iid)
s.setdefault("batchInstancesAll", []).extend(made); s["batchInstances"] = made; w.save(s)
t = [x for x in w.tasks(b) if x["taskType"] == "APPROVAL"]
w.log("seed", f"新建 PAR 实例 {made}；user2 审批待办 {len(t)} 条：{[(x['instanceTitle'], x['nodeKey']) for x in t]}")
