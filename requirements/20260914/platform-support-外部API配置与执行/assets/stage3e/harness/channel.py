"""外部 API 3e：本机合成渠道（127.0.0.1 随机端口），不代表真实渠道协议。

- POST /api/outlets/{outlet}/orders：先校验 signature = HMAC-SHA256(apiKey, orderRef)，不符返回 401；
  orderRef 末位 1 → 201 CAPTURED，2 → 201 DECLINED，3 → 504（结果未知，供补查）。
- GET /api/orders/{orderRef}：返回 200 CAPTURED（补查成功）。
API Key 从 E2E_RUNTIME/platform.json 读取；请求日志只记方法、路径、状态与验签结果，不记 Header 值和报文。"""
import hashlib, hmac, json, os, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

runtime = Path(os.environ['E2E_RUNTIME'])
api_key = json.loads((runtime / 'platform.json').read_text())['apiKey'].encode()
journal = runtime / 'channel-journal.jsonl'
lock = threading.Lock()


class Channel(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, status, body, signed=None):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        with lock, journal.open('a') as out:
            out.write(json.dumps({'method': self.command, 'path': self.path, 'status': status, 'signatureValid': signed}) + '\n')

    def do_POST(self):
        parts = self.path.split('?')[0].strip('/').split('/')
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', '0'))) or b'{}')
        if parts[:2] != ['api', 'outlets'] or parts[-1] != 'orders':
            return self.reply(404, {'message': 'unknown path'})
        order = str(body.get('orderRef', ''))
        expected = hmac.new(api_key, order.encode(), hashlib.sha256).hexdigest()
        signed = hmac.compare_digest(expected, self.headers.get('signature', ''))
        if not signed:
            return self.reply(401, {'message': 'bad signature'}, signed)
        outcome = order[-1:]
        if outcome == '1':
            return self.reply(201, {'reference': 'ref-' + order, 'state': 'CAPTURED'}, signed)
        if outcome == '2':
            return self.reply(201, {'state': 'DECLINED', 'errorCode': 'E51'}, signed)
        return self.reply(504, {'message': 'upstream timeout'}, signed)

    def do_GET(self):
        parts = self.path.split('?')[0].strip('/').split('/')
        if parts[:2] != ['api', 'orders'] or len(parts) != 3:
            return self.reply(404, {'message': 'unknown path'})
        return self.reply(200, {'reference': 'ref-' + parts[2], 'state': 'CAPTURED'})


server = ThreadingHTTPServer(('127.0.0.1', 0), Channel)
(runtime / 'channel.json').write_text(json.dumps({'port': server.server_address[1]}))
server.serve_forever()
