"""任务卡 P2-stage5 G（主会话追加）：出站地址摘要修复后的复验。

channel-connector 已换为含 UpdateChannelEndpointsProcessor.endpointsDigest 的新 JAR 并重启。本脚本：
  1. 启动回显服务，经控制台（Nginx 18444 → Gateway → ops-bff）连续两次修改 S5P2_ECHO 出站地址：
     第 1 次把 ECHO 指向本次回显端口；第 2 次保留 ECHO 并新增 ALT（https://echo-alt.example.test，保存时不解析 DNS）。
  2. 读取这两条 CHANNEL / CONFIGURATION 变更记录的 change_detail，核对后一条的 before 等于前一条的 after，
     并按写入格式（endpointsJson：endpointCode、baseUrl，sandbox 仅为 true 时输出）本地复算两次 after 摘要。
  3. 通用入口调用一次，确认重启后执行正常；最后把 ALT 去掉，恢复为只有 ECHO（第 3 条记录同样核对链式摘要）。
结果并入 stage5p2-service.json 的 results（键 G.*）。用法：python3 -I stage5p2_g.py
"""
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import stage5p2_service as sv  # noqa: E402
from stage5_e2e import h, ops, login  # noqa: E402


def compact(endpoints):
    rows = []
    for e in endpoints:
        row = {"endpointCode": e["endpointCode"], "baseUrl": e["baseUrl"]}
        if e.get("sandbox"):
            row["sandbox"] = True
        rows.append(row)
    return json.dumps(rows, separators=(",", ":"), ensure_ascii=False)


def sha(text):
    return hashlib.sha256(text.encode()).hexdigest()


def changes(channel_id, after_version):
    rows = h.sql("channel-connector", "SELECT change_detail FROM t_channel_config_change WHERE target_type='CHANNEL' "
                                      f"AND change_type='CONFIGURATION' AND target_id={int(channel_id)} ORDER BY create_time")
    details = [json.loads(r[0]) for r in rows if r[0] and r[0] != "NULL"]
    return [d for d in details if d.get("afterVersion", 0) > after_version]


def main():
    out = HERE / "stage5p2-service.json"
    data = json.loads(out.read_text())
    sv.RESULTS.update(data["results"])
    state = json.loads((sv.RUN / "state.json").read_text())
    cid = state["channelId"]
    token = login()
    proc, port = sv.echo_start()
    try:
        _, detail = ops(token, "GET", f"{sv.CHANNEL_ADMIN}/channels/{cid}")
        start_version = detail["version"]
        echo = {"endpointCode": "ECHO", "baseUrl": f"http://127.0.0.1:{port}", "sandbox": False}
        alt = {"endpointCode": "ALT", "baseUrl": "https://echo-alt.example.test", "sandbox": False}
        plans = [[echo], [echo, alt], [echo]]
        versions = []
        for index, endpoints in enumerate(plans):
            status, body = ops(token, "PUT", f"{sv.CHANNEL_ADMIN}/channels/{cid}/endpoints",
                               {"expectedVersion": start_version + index, "endpoints": endpoints})
            versions.append([status, body.get("version")])
        records = changes(cid, start_version)
        digests = [[r["beforeVersion"], r["afterVersion"], (r["changes"][0].get("before") or "")[:12],
                    (r["changes"][0].get("after") or "")[:12]] for r in records]
        expected_after = [sha(compact(p)) for p in plans]
        chained = len(records) == 3 and all(records[i]["changes"][0]["before"] == records[i - 1]["changes"][0]["after"]
                                           for i in (1, 2))
        recomputed = len(records) == 3 and all(records[i]["changes"][0]["after"] == expected_after[i] for i in range(3))
        sv.record("G.endpointsDigestChained", chained and recomputed and all(v[0] == 200 for v in versions),
                  startVersion=start_version, puts=versions, records=digests,
                  beforeEqualsPreviousAfter=chained, afterMatchesLocalRecompute=recomputed)
        status, body, seen, access = sv.call_and_observe("afterfix")
        sv.record("G.serviceCallAfterRestart", status == 200 and body.get("executeStatus") == "SUCCESS" and len(seen) == 1,
                  **sv.result_view(status, body), echoRequests=len(seen), accessLog=access)
        sv.write(out, state)
    finally:
        sv.echo_stop(proc)


if __name__ == "__main__":
    main()
