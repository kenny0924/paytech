"""AC-10：依次短暂停止 billing、account、ledger（run-stack.sh stop / start），各取一次 B1，随后立即启动并确认 UP、B1 恢复。"""
import json
import subprocess
import time
import t6lib as L

st = L.load()
tok = L.login()
book = st["OE"]["bookId"]
RUN = str(L.HERE / "run-stack.sh")
out = {}


def b1():
    s, b, dt = L.ops("GET", f"/finance/posting-progress/v1/ledger-books/{book}", tok)
    if s == 200:
        return s, {"fact": b["factSegment"]["status"], "factCounts": b["factSegment"].get("billingPublishedCount"),
                   "stream": b["streamSegment"]["status"], "streams": len(b["streamSegment"]["streams"])}, round(dt, 2)
    return s, b, round(dt, 2)


for svc in ("billing", "account", "ledger"):
    r = subprocess.run(["bash", RUN, "stop", svc], capture_output=True, text=True)
    time.sleep(2)
    down = b1()
    t0 = time.monotonic()
    r2 = subprocess.run(["bash", RUN, "start", svc], capture_output=True, text=True)
    up_s = round(time.monotonic() - t0, 1)
    status = subprocess.run(["bash", RUN, "status", svc], capture_output=True, text=True).stdout.strip()
    again = b1()
    out[svc] = {"down": down, "startSeconds": up_s, "status": status, "after": again}
    print(svc, json.dumps(out[svc], ensure_ascii=False))
st["segments"] = out
L.save(st)
