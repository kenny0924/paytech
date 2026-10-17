"""mor-test 验收用 Webhook 接收端：HTTPS 收下每个 POST，原样落盘后返回 204。

只供 Client API 联通验收使用（requirements/20261017/commerce-ClientAPI测试环境联通）。
签名与内容校验在验收脚本中完成；本程序不记录日志正文，也不解析载荷。
"""
import base64
import json
import os
import ssl
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

INBOX = Path(os.environ.get("RECEIVER_INBOX", "/data/inbox"))
CERT = os.environ.get("RECEIVER_CERT", "/run/receiver/tls.crt")
KEY = os.environ.get("RECEIVER_KEY", "/run/receiver/tls.key")
PORT = int(os.environ.get("RECEIVER_PORT", "8443"))
MAX_BODY = 65536


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY:
            self.send_response(413)
            self.end_headers()
            return
        body = self.rfile.read(length)
        record = {
            "receivedAt": time.time(),
            "path": self.path,
            "headers": {name: value for name, value in self.headers.items()},
            "body": base64.b64encode(body).decode("ascii"),
        }
        name = f"{time.time_ns()}-{os.getpid()}.json"
        temporary = INBOX / f".{name}"
        temporary.write_text(json.dumps(record), encoding="utf-8")
        temporary.rename(INBOX / name)
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        # 只有健康检查。
        self.send_response(200 if self.path == "/healthz" else 404)
        self.end_headers()

    def log_message(self, format, *args):
        # 访问日志只记方法、路径与状态，不记头部与正文。
        print(f"{self.command} {self.path} {args[1] if len(args) > 1 else ''}", flush=True)


def main():
    INBOX.mkdir(parents=True, exist_ok=True)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(CERT, KEY)
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
