"""合成筛查服务（全球合规入驻切片 1，R8 / S-06）：服务渠道 S_SCREENING_MOCK 的出站地址，只用 Python 标准库。

用法：python3 -I screening_mock_server.py [--host 0.0.0.0] [--port 18191] [--journal <文件>]
  GET  /health                     200 {"status":"UP"}
  POST /v1/screening/search        请求 JSON：{subjectType, fullName, birthYear?("YYYY"), countries[], screeningTypes[], clientRef}
       响应 200 {"code":0,"runRef":"MOCK-RUN-…","hits":[...]}；每条命中
         {externalRef, entityType, listTypes[], score(0–100，带小数点的数值，如 92.0), matchTypes[], riskLevel, matchName}
         externalRef 对同一 clientRef + fullName 稳定
         - fullName 含 TESTHIT（不区分大小写）      → 一条 PEP 命中
         - fullName 含 TESTSANCTION（不区分大小写） → 一条 SANCTIONS 命中（两者都含时返回两条）
         - 其他                                     → hits = []
       控制标记（供异常路径验证）：fullName 含 TESTFAIL → 200 {"code":4001,"errorCode":"SUBJECT_REJECTED","hits":[]}（渠道 FAIL）；
         含 TESTERROR → HTTP 500（结果未知 ERROR）；含 TESTSLOW → 先等待 --slow-ms 毫秒再按上面规则响应。
       请求体不是 JSON 对象，或缺 fullName / subjectType → 400 {"code":400,"errorCode":"INVALID_REQUEST"}。
  POST /__control {"delayMs": N}   之后每个筛查请求统一延迟 N 毫秒（0–30000），不进流水。
流水（--journal，可选）：每行一条 JSON，只记时间、路径、clientRef、subjectType、screeningTypes、命中数与姓名的 SHA-256 前 12 位，
不记姓名、生日、国家原值。凭据类请求头只记是否存在。
"""
import argparse
import hashlib
import json
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SEARCH_PATH = "/v1/screening/search"
MAX_BODY = 64 * 1024
STATE = {"delayMs": 0, "seq": 0}
LOCK = threading.Lock()


def hits_for(full_name, subject_type, client_ref):
    upper = full_name.upper()
    entity_type = "ORGANIZATION" if subject_type in ("ORG", "ORGANIZATION") else "PERSON"
    ref = hashlib.sha256((client_ref or "").encode() + b"|" + full_name.encode()).hexdigest()[:10].upper()
    hits = []
    if "TESTHIT" in upper:
        hits.append({"externalRef": "MOCK-PEP-" + ref, "entityType": entity_type, "listTypes": ["PEP"],
                     "score": 92.0, "matchTypes": ["NAME_EXACT"], "riskLevel": "MEDIUM", "matchName": full_name})
    if "TESTSANCTION" in upper:
        hits.append({"externalRef": "MOCK-SAN-" + ref, "entityType": entity_type, "listTypes": ["SANCTIONS"],
                     "score": 97.0, "matchTypes": ["NAME_EXACT"], "riskLevel": "HIGH", "matchName": full_name})
    return hits


class Handler(BaseHTTPRequestHandler):
    journal = None
    slow_ms = 3000
    server_version = "ScreeningMock/1"

    def log_message(self, fmt, *args):  # 不写访问日志（请求体含合成姓名）
        return

    def reply(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def record(self, entry):
        if not self.journal:
            return
        with LOCK, open(self.journal, "a") as handle:
            handle.write(json.dumps(entry, ensure_ascii=False) + "\n")

    def do_GET(self):
        if self.path.split("?", 1)[0] in ("/health", "/healthz"):
            return self.reply(200, {"status": "UP"})
        return self.reply(404, {"code": 404, "errorCode": "NOT_FOUND"})

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY:
            return self.reply(413, {"code": 413, "errorCode": "BODY_TOO_LARGE"})
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = None
        if path == "/__control":
            STATE["delayMs"] = max(0, min(30000, int((body or {}).get("delayMs", 0))))
            return self.reply(200, {"delayMs": STATE["delayMs"]})
        if path != SEARCH_PATH:
            return self.reply(404, {"code": 404, "errorCode": "NOT_FOUND"})
        with LOCK:
            STATE["seq"] += 1
            seq = STATE["seq"]
        valid = isinstance(body, dict) and isinstance(body.get("fullName"), str) and body.get("fullName") \
            and isinstance(body.get("subjectType"), str)
        full_name = body.get("fullName") if valid else ""
        upper = full_name.upper()
        entry = {"seq": seq, "receivedAt": datetime.now(timezone.utc).isoformat(), "path": path,
                 "headerNames": sorted(h.lower() for h in self.headers.keys()),
                 "clientRef": body.get("clientRef") if isinstance(body, dict) else None,
                 "subjectType": body.get("subjectType") if isinstance(body, dict) else None,
                 "screeningTypes": body.get("screeningTypes") if isinstance(body, dict) else None,
                 "nameSha256": hashlib.sha256(full_name.encode()).hexdigest()[:12] if full_name else None}
        delay = STATE["delayMs"] + (self.slow_ms if "TESTSLOW" in upper else 0)
        if delay:
            time.sleep(delay / 1000)
        if not valid:
            self.record({**entry, "outcome": "INVALID_REQUEST"})
            return self.reply(400, {"code": 400, "errorCode": "INVALID_REQUEST"})
        if "TESTERROR" in upper:
            self.record({**entry, "outcome": "HTTP_500"})
            return self.reply(500, {"code": 500, "errorCode": "MOCK_INTERNAL_ERROR"})
        if "TESTFAIL" in upper:
            self.record({**entry, "outcome": "FAIL"})
            return self.reply(200, {"code": 4001, "errorCode": "SUBJECT_REJECTED", "hits": []})
        hits = hits_for(full_name, body.get("subjectType"), body.get("clientRef"))
        self.record({**entry, "outcome": "SUCCESS", "hitCount": len(hits),
                     "listTypes": sorted({t for h in hits for t in h["listTypes"]})})
        run_ref = "MOCK-RUN-" + hashlib.sha256((body.get("clientRef") or "").encode()).hexdigest()[:12].upper()
        return self.reply(200, {"code": 0, "runRef": run_ref, "hits": hits})


def main():
    parser = argparse.ArgumentParser(description="合成筛查服务（S_SCREENING_MOCK 出站地址）")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18191)
    parser.add_argument("--journal", default=None, help="请求流水 JSONL 文件（可选）")
    parser.add_argument("--slow-ms", type=int, default=3000, help="TESTSLOW 标记的延迟毫秒数")
    args = parser.parse_args()
    Handler.journal = args.journal
    Handler.slow_ms = max(0, min(30000, args.slow_ms))
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    server.daemon_threads = True
    print(f"screening mock listening on {args.host}:{server.server_address[1]}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
