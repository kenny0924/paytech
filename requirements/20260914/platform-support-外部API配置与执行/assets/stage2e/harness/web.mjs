// Real production Web build and a loopback ingress proxy; no API business stubs.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../../../..');
const runtime = process.env.E2E_RUNTIME;
const dist = path.join(root, 'operations-console-web/dist');
let sequence = 0;
const read = name => JSON.parse(fs.readFileSync(path.join(runtime, `${name}.json`), 'utf8'));
const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api/operations/')) {
    const gateway = read('gateway'), platform = read('platform');
    const requestId = `${platform.marker}-${(++sequence).toString(16)}`;
    const headers = { ...req.headers, 'x-real-ip': '127.0.0.1', 'x-request-id': requestId };
    for (const key of Object.keys(headers)) if (key.startsWith('x-gateway-') || key === 'forwarded' || key.startsWith('x-forwarded-')) delete headers[key];
    headers['x-entry-channel'] = 'OPERATIONS';
    const upstream = http.request({hostname:'127.0.0.1', port:gateway.port, path:req.url, method:req.method, headers}, response => {
      res.writeHead(response.statusCode, response.headers); response.pipe(res);
      fs.appendFileSync(path.join(runtime, 'http-journal.jsonl'), JSON.stringify({requestId:response.headers['x-request-id'], ingressRequestId:requestId, commandKey:req.headers['idempotency-key'] || null, method:req.method, path:req.url.split('?')[0], status:response.statusCode})+'\n');
    });
    upstream.on('error', () => { res.writeHead(502); res.end(); }); req.pipe(upstream); return;
  }
  // 本次不验证动态 Target；保留与前序一致的入口代理行为。
  if (req.url.split('?')[0] === '/stage5-observed-owner') {
    res.writeHead(200, {'content-type':'application/json'}); res.end(JSON.stringify({owner:'stage5-dynamic-target', requestId:req.headers['x-request-id']})); return;
  }
  const requested = decodeURIComponent(req.url.split('?')[0]);
  const file = path.resolve(dist, '.' + requested);
  const selected = file.startsWith(dist + path.sep) && fs.existsSync(file) && fs.statSync(file).isFile() ? file : path.join(dist,'index.html');
  const types = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.svg':'image/svg+xml', '.png':'image/png'};
  res.writeHead(200, {'content-type':types[path.extname(selected)] || 'application/octet-stream', 'cache-control':'no-store'});
  fs.createReadStream(selected).pipe(res);
});
const previous = process.env.STAGE5_REUSE_PORT === 'true' ? read('web') : null;
server.listen(previous ? new URL(previous.origin).port : 0, '127.0.0.1', () => {
  fs.writeFileSync(path.join(runtime,'web.json'), JSON.stringify({origin:`http://localhost:${server.address().port}`, dynamicService:previous?.dynamicService || `stage5-owner-${crypto.randomUUID().slice(0,8)}`}));
});
process.on('SIGTERM', () => server.close());
