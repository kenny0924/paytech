"""任务卡 P2-stage5 E1：本机最小回显服务（只用 Python 标准库），作为服务渠道 S5P2_ECHO 的出站地址。

用法：python3 -I stage5p2_echo_server.py <运行目录>
  - 监听 127.0.0.1 上的空闲端口，端口写入 <运行目录>/echo-port；收到的请求逐行追加到 <运行目录>/echo-journal.jsonl。
  - 每条记录：时间、方法、路径（不含查询串）、请求头名称列表、请求体。凭据头（authorization、x-api-key 等）与请求体凭据键
    （apiKey、secret、token、password）只记是否存在与末 4 位，不记原值。
  - 响应：200 {"code":0,"echo":<请求体 ping>,"seq":<序号>}；请求体不是 JSON 对象时 400。
  - 延迟：POST /__control {"delayMs": N} 设置之后每个请求的响应延迟（毫秒，0–30000），不进流水。
  - 停止：kill 进程（SIGTERM）。
"""
import json
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

CREDENTIAL_HEADERS = {"authorization", "x-api-key", "x-auth-token", "proxy-authorization"}
CREDENTIAL_KEYS = {"apikey", "secret", "token", "password"}
STATE = {"delayMs": 0, "seq": 0}
LOCK = threading.Lock()


def mask(value):
    text = str(value)
    return {"present": bool(text), "last4": text[-4:] if len(text) >= 8 else None}


def scrub(value):
    if isinstance(value, dict):
        return {k: (mask(v) if k.lower() in CREDENTIAL_KEYS else scrub(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [scrub(v) for v in value]
    return value


class Handler(BaseHTTPRequestHandler):
    journal = None

    def log_message(self, fmt, *args):  # 不写 stderr 访问日志
        return

    def reply(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def handle_any(self):
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        path = self.path.split("?", 1)[0]
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = None
        if path == "/__control":
            STATE["delayMs"] = max(0, min(30000, int((body or {}).get("delayMs", 0))))
            return self.reply(200, {"delayMs": STATE["delayMs"]})
        with LOCK:
            STATE["seq"] += 1
            seq = STATE["seq"]
        entry = {"seq": seq, "receivedAt": datetime.now(timezone.utc).isoformat(), "method": self.command, "path": path,
                 "headerNames": sorted(h.lower() for h in self.headers.keys()),
                 "credentialHeaders": {h.lower(): mask(v) for h, v in self.headers.items() if h.lower() in CREDENTIAL_HEADERS},
                 "body": scrub(body) if body is not None else (raw[:200].decode(errors="replace") if raw else None),
                 "delayMs": STATE["delayMs"]}
        with LOCK, open(self.journal, "a") as handle:
            handle.write(json.dumps(entry, ensure_ascii=False) + "\n")
        if STATE["delayMs"]:
            time.sleep(STATE["delayMs"] / 1000)
        if not isinstance(body, dict):
            return self.reply(400, {"code": 400, "message": "JSON object required"})
        return self.reply(200, {"code": 0, "echo": body.get("ping"), "seq": seq})

    do_GET = do_POST = do_PUT = do_DELETE = handle_any


def main():
    run_dir = Path(sys.argv[1])
    run_dir.mkdir(parents=True, exist_ok=True)
    Handler.journal = run_dir / "echo-journal.jsonl"
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    (run_dir / "echo-port").write_text(str(server.server_address[1]))
    server.serve_forever()


if __name__ == "__main__":
    main()
