// 工作流附件内嵌预览跟进 · mor-test 验收（阶段 7，会写 mor-test 业务数据，执行前需用户同意）。
// 改编自 verification/local-e2e/inline_preview_browser.mjs（内嵌 / 下载检查）与 release/acceptance/wf8_browser.mjs（mor-test 登录）。
// 入口 http://localhost:28080（Gateway → ops-bff → platform-support）；管理员取 test-environment/private/access.md，口令不打印。
//
// 一、API 造数（数据前缀 WF-INLINE-20261022，编码前缀 WF_INLINE_20261022_）：
//   FILE_UPLOAD 模板（PDF / PNG）→ 定义（start → docs[FILE_UPLOAD，执行人 admin；审批人 admin] → end）→ 发布 v1 → 发起
//   → #26/#27 上传一份小 PDF 与一份小 PNG → 等扫描 AVAILABLE（只读 SELECT mor_platform.t_file_object）→ 提交动作。
//   mor-test 只有一个管理员账号：同一人提交后不能审批本节点（页面决定按钮禁用），但“文件预览”区照常显示，满足本验收。
// 二、API 检查（经 Gateway）：#28 ?disposition=INLINE → PDF / PNG 返回 disposition=INLINE；?disposition=ATTACHMENT 与不带参数 → ATTACHMENT；
//   ?disposition=BOGUS → 400。对签发地址 GET 一次，只打印存储响应头（Content-Type / Content-Disposition），不打印地址。
// 三、浏览器检查（需要新 ops-web；现网 ops-web 未替换前本段预期 FAIL，见 release.md 第 0 节 B1）：
//   我的待办 → 审批页“文件预览”：PDF 点“查看”→ INLINE + iframe；PNG 点“查看”→ INLINE + img；点“下载”→ ATTACHMENT。
// 四、收尾：终止实例（原因“WF-INLINE-20261022 验收结束”），停用模板与定义不在本脚本内（运营页面操作）。
// 状态（只含 ID）写同目录 inline-mortest-state.json；截图 screenshots/inline-mortest-*.png。
// 用法：node inline_mortest.mjs            （全部）
//       SKIP_BROWSER=1 node inline_mortest.mjs  （只做一、二、四）
//       REUSE_STATE=1 node inline_mortest.mjs   （复用已造数据，只做二、三）
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { deflateSync } from 'node:zlib';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../../../../..');
const OPS = 'http://localhost:28080';
const API = OPS + '/api/operations';
// 重跑时用 RUN 区分编码（如 RUN=B），首轮为空。
const RUN = process.env.RUN ? `_${process.env.RUN}` : '';
const P = `WF-INLINE-20261022${RUN.replace('_', '-')}`, C = `WF_INLINE_20261022${RUN}`;
const STATE = join(HERE, 'inline-mortest-state.json');
mkdirSync(join(HERE, 'screenshots'), { recursive: true });
const access = readFileSync(join(ROOT, 'test-environment/private/access.md'), 'utf8');
const email = access.match(/账号：(\S+)/)[1], password = access.match(/密码：(\S+)/)[1];
const log = (...a) => console.log(...a);
let failures = 0;
async function step(name, fn) {
  try { log('PASS', name, '|', (await fn()) ?? ''); }
  catch (e) { failures++; log('FAIL', name, '|', String(e.message || e).split('\n')[0].slice(0, 300)); }
}
const expect = (cond, text) => { if (!cond) throw new Error(text); };

// ---------- HTTP（与 e2e_http.call 一致：Origin、JSON、写命令带 Idempotency-Key，429 按 Retry-After 重试同一键） ----------
let token = null;
async function call(method, path, body) {
  const headers = { Origin: OPS, Accept: 'application/json' };
  if (body !== undefined || !['GET', 'HEAD', 'DELETE'].includes(method)) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = 'Bearer ' + token;
  if (method !== 'GET') headers['Idempotency-Key'] = randomUUID();
  for (let attempt = 0; attempt < 12; attempt++) {
    const r = await fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (r.status === 429 && attempt < 11) { await new Promise(s => setTimeout(s, 1000 * Number(r.headers.get('retry-after') || 10))); continue; }
    const text = await r.text();
    const json = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
    return { status: r.status, body: json };
  }
}
async function ok(method, path, body) {
  const r = await call(method, path, body);
  if (r.status >= 300) throw new Error(`${method} ${path.replace(/\?.*/, '')} -> HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 300)}`);
  return r.body;
}
/** 签发地址 GET 一次，只返回状态与两个响应头；地址不打印。 */
async function storageHeaders(url) {
  const out = storageCurl(url, ['-D', '-', '-o', '/dev/null']);
  const header = name => (out.match(new RegExp(`^${name}:\\s*(.*?)\\r?$`, 'im')) || [])[1] ?? null;
  return { status: Number((out.match(/^HTTP\/\S+\s+(\d+)/m) || [])[1]), contentType: header('content-type'), contentDisposition: header('content-disposition') };
}
/**
 * 存储签名地址经 curl 访问：Node 不解析 *.localhost，浏览器与 curl 解析为回环；--resolve 只改连接地址，Host 与签名不变。
 * 地址不打印。
 */
function storageCurl(url, args, input) {
  const u = new URL(url);
  const port = u.port || (u.protocol === 'https:' ? '443' : '80');
  const resolve = u.hostname.endsWith('.localhost') ? ['--resolve', `${u.hostname}:${port}:127.0.0.1`] : [];
  return execFileSync('curl', ['-sS', '-k', '--max-time', '30', ...resolve, ...args, url], { input, encoding: input ? undefined : 'utf8' }).toString();
}
/** 只读 SELECT（mor-test root，口令只经 MYSQL_PWD 传入容器）。 */
function roSql(statement) {
  const pw = readFileSync(join(ROOT, 'test-environment/.env.test'), 'utf8').split('\n')
    .find(l => l.startsWith('MOR_MYSQL_ROOT_PASSWORD=')).split('=').slice(1).join('=').trim();
  return execFileSync('docker', ['exec', '-i', '-e', 'MYSQL_PWD', 'mor-test-mysql-1', 'mysql', '-uroot', '-N', '-B',
    '--default-character-set=utf8mb4', 'mor_platform', '-e', statement], { env: { ...process.env, MYSQL_PWD: pw }, encoding: 'utf8' }).trim();
}

// ---------- 测试文件 ----------
function pdfBytes(text) {
  const content = `BT /F1 18 Tf 72 720 Td (${text}) Tj ET`;
  const objs = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${content.length} >>\nstream\n${content}\nendstream`, '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
  let out = '%PDF-1.4\n'; const offsets = [];
  objs.forEach((o, i) => { offsets.push(out.length); out += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = out.length;
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n` + offsets.map(o => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  out += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, 'latin1');
}
function crc32(buf) {
  let c, crc = 0xffffffff;
  for (const b of buf) { c = (crc ^ b) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; }
  return (crc ^ 0xffffffff) >>> 0;
}
function pngBytes(width = 240, height = 120) {
  const row = Buffer.alloc(1 + width * 3);
  for (let x = 0; x < width; x++) row.set(x < width / 2 ? [30, 90, 200] : [240, 140, 30], 1 + x * 3);
  const raw = Buffer.concat(Array.from({ length: height }, () => row));
  const chunk = (kind, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const kd = Buffer.concat([Buffer.from(kind), data]);
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(kd)); return Buffer.concat([len, kd, crc]); };
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr.set([8, 2, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
async function upload(name, mime, data) {
  const grant = await ok('POST', '/workflow/v1/attachments/uploads', { filename: name, mimeType: mime, sizeBytes: String(data.length) });
  const auth = grant.authorization;
  const headerArgs = Object.entries(auth.headers || {}).flatMap(([k, v]) => ['-H', `${k}: ${v}`]);
  const status = Number(storageCurl(auth.url, ['-X', 'PUT', ...headerArgs, '--data-binary', '@-', '-o', '/dev/null', '-w', '%{http_code}'], data));
  const put = { ok: status >= 200 && status < 300, status };
  expect(put.ok, `存储 PUT ${name} -> HTTP ${put.status}`);
  const done = await ok('POST', `/workflow/v1/attachments/uploads/${grant.uploadId}/completion`);
  log('upload', name, `uploadId=${grant.uploadId} PUT=${put.status} fileId=${done.fileId}`);
  return { fileId: done.fileId, fileAddress: done.fileAddress ?? `/files/v1/${done.fileId}` };
}
async function waitAvailable(fileId, timeoutMs = 120000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const row = roSql(`SELECT scan_status FROM t_file_object WHERE file_id=${BigInt(fileId)}`);
    if (row === 'AVAILABLE') return `${fileId} AVAILABLE ${((Date.now() - start) / 1000).toFixed(1)}s`;
    await new Promise(s => setTimeout(s, 1500));
  }
  throw new Error(`${fileId} 扫描超时`);
}

// ---------- 一、造数 ----------
const login = await ok('POST', '/auth/v1/login', { loginIdentifier: email, password });
token = login.accessToken;
let st;
if (process.env.REUSE_STATE === '1' && existsSync(STATE)) {
  st = JSON.parse(readFileSync(STATE, 'utf8'));
  log('reuse', JSON.stringify(st));
} else {
  const me = (await ok('GET', '/auth/v1/context')).accountId;
  st = { adminId: me, startedAt: new Date().toISOString() };
  const tpl = await ok('POST', '/workflow-admin/v1/action-templates', {
    templateCode: `${C}_UPLOAD`, templateName: `${P} 内嵌预览上传`, actionType: 'FILE_UPLOAD', description: 'mor-test 内嵌预览验收',
    config: { minFiles: 1, maxFiles: 3, mimeTypes: ['application/pdf', 'image/png'], instruction: '上传 PDF 与 PNG' } });
  st.templateId = tpl.objectId;
  const def = await ok('POST', '/workflow-admin/v1/definitions', { definitionCode: `${C}_FLOW`, definitionName: `${P} 内嵌预览流程`, description: 'mor-test 内嵌预览验收' });
  st.definitionId = def.objectId;
  const pos = (x, y) => ({ x, y });
  const graph = { nodes: [
    { key: 'start', type: 'START', name: '开始', position: pos(250, 0) },
    { key: 'docs', type: 'TASK', name: '上传待审文件', position: pos(250, 120), approval: { mode: 'ANY', approverIds: [me] },
      actions: [{ key: 'inlineUpload', templateId: st.templateId, required: true, executorId: me }] },
    { key: 'end', type: 'END', name: '结束', position: pos(250, 240) }],
  edges: [{ key: 'e1', from: 'start', to: 'docs' }, { key: 'e2', from: 'docs', to: 'end' }] };
  let det = await ok('GET', `/workflow-admin/v1/definitions/${st.definitionId}`);
  const draft = await ok('PUT', `/workflow-admin/v1/definitions/${st.definitionId}/draft`, {
    definitionName: `${P} 内嵌预览流程`, description: 'mor-test 内嵌预览验收', graph,
    startForm: { fields: [{ key: 'note', label: '说明', type: 'TEXT', required: true, rules: { maxLength: 64 } }] },
    expectedVersion: det.definition.version });
  log('draft problems', JSON.stringify(draft.problems ?? []));
  det = await ok('GET', `/workflow-admin/v1/definitions/${st.definitionId}`);
  await ok('POST', `/workflow-admin/v1/definitions/${st.definitionId}/publication`, { expectedVersion: det.definition.version });
  st.title = `${P} 内嵌预览实例`;
  st.instanceId = (await ok('POST', '/workflow/v1/instances', { definitionId: st.definitionId, versionNo: 1, title: st.title,
    businessKey: P, startValues: { note: '内嵌预览验收' } })).objectId;
  writeFileSync(STATE, JSON.stringify(st, null, 1));
  st.pdfName = `${C}.pdf`; st.pngName = `${C}.png`;
  const pdf = await upload(st.pdfName, 'application/pdf', pdfBytes(`${P} inline preview`));
  const png = await upload(st.pngName, 'image/png', pngBytes());
  Object.assign(st, { pdfFileId: pdf.fileId, pdfAddress: pdf.fileAddress, pngFileId: png.fileId, pngAddress: png.fileAddress });
  writeFileSync(STATE, JSON.stringify(st, null, 1));
  for (const id of [pdf.fileId, png.fileId]) log('scan', await waitAvailable(id));
  const detail = await ok('GET', `/workflow/v1/instances/${st.instanceId}`);
  const run = detail.actionRuns.find(x => x.actionKey === 'inlineUpload');
  const sub = await ok('POST', `/workflow/v1/action-runs/${run.actionRunId}/submission`, { output: { files: [pdf.fileAddress, png.fileAddress] } });
  log('submission', `status=${sub.status} reason=${sub.failureReason ?? ''}`);
  writeFileSync(STATE, JSON.stringify(st, null, 1));
}

// ---------- 二、API 检查（经 Gateway） ----------
const dl = (fileId, q) => call('POST', `/workflow/v1/instances/${st.instanceId}/files/${fileId}/download${q}`);
for (const [label, fileId, mime] of [['PDF', st.pdfFileId, 'application/pdf'], ['PNG', st.pngFileId, 'image/png']]) {
  await step(`API ${label} disposition=INLINE`, async () => {
    const r = await dl(fileId, '?disposition=INLINE');
    expect(r.status === 200, `HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
    expect(r.body.disposition === 'INLINE', `disposition=${r.body.disposition}`);
    const h = await storageHeaders(r.body.url);
    expect(h.status === 200 && (h.contentType || '').startsWith(mime) && /^inline/i.test(h.contentDisposition || ''), JSON.stringify(h));
    return `HTTP 200 disposition=INLINE；存储 ${JSON.stringify(h)}`;
  });
}
await step('API PDF disposition=ATTACHMENT', async () => {
  const r = await dl(st.pdfFileId, '?disposition=ATTACHMENT');
  expect(r.status === 200 && r.body.disposition === 'ATTACHMENT', `HTTP ${r.status} disposition=${r.body?.disposition}`);
  const h = await storageHeaders(r.body.url);
  expect(h.status === 200 && /^attachment/i.test(h.contentDisposition || ''), JSON.stringify(h));
  return `HTTP 200 disposition=ATTACHMENT；存储 ${JSON.stringify(h)}`;
});
await step('API PDF 不带 disposition（缺省 ATTACHMENT）', async () => {
  const r = await dl(st.pdfFileId, '');
  expect(r.status === 200 && r.body.disposition === 'ATTACHMENT', `HTTP ${r.status} disposition=${r.body?.disposition}`);
  return 'HTTP 200 disposition=ATTACHMENT';
});
await step('API PDF disposition=BOGUS → 400', async () => {
  const r = await dl(st.pdfFileId, '?disposition=BOGUS');
  expect(r.status === 400, `HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
  return `HTTP 400 ${r.body?.code ?? ''}`;
});

// ---------- 三、浏览器检查 ----------
if (process.env.SKIP_BROWSER !== '1') {
  const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
  const shot = (page, name) => page.screenshot({ path: join(HERE, 'screenshots', `inline-mortest-${name}.png`) });
  const network = [], errors = [], grants = [];
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ locale: 'zh-CN', viewport: { width: 1600, height: 1000 }, acceptDownloads: true });
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(`pageerror ${String(e).slice(0, 200)}`));
  ctx.on('response', async r => {
    const u = new URL(r.url());
    if (r.status() >= 400) network.push(`${r.request().method()} ${u.pathname} -> ${r.status()}`);
    if (u.pathname.endsWith('/download') && r.request().method() === 'POST' && u.pathname.includes('/workflow/v1/instances/')) {
      const body = await r.json().catch(() => ({}));
      grants.push({ query: u.search, status: r.status(), disposition: body.disposition });
    }
  });
  const settle = async () => { await page.waitForLoadState('networkidle'); await page.waitForTimeout(1000); };
  const grantText = g => g ? `POST …/download${g.query} -> HTTP ${g.status} disposition=${g.disposition}` : '无签发请求';
  await page.goto(OPS + '/');
  await page.getByLabel('Email / 初始化账号').fill(email);
  await page.getByLabel('密码').fill(password);
  await page.getByRole('button', { name: /^登\s*录$/ }).click();
  await settle();
  await page.goto(`${OPS}/workflow/my-tasks`); await settle();
  const preview = page.locator('.wf-preview');
  await step('我的待办打开审批页，出现“文件预览”', async () => {
    await page.locator('.wf-task-item', { hasText: st.title }).first().click({ timeout: 20000 }); await settle();
    await preview.waitFor({ timeout: 15000 });
    await shot(page, '01-approval-files');
    return (await preview.innerText()).replace(/\s+/g, ' ').slice(0, 160);
  });
  await step('PDF 查看 → INLINE + iframe', async () => {
    const n = grants.length;
    const signed = page.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 15000 });
    await preview.locator('li', { hasText: st.pdfName }).getByRole('button', { name: /^查\s*看$/ }).click();
    await signed; await page.waitForTimeout(3000);
    const g = grants.slice(n)[0];
    expect(g?.query === '?disposition=INLINE' && g.disposition === 'INLINE', grantText(g));
    expect(await preview.locator('.wf-file-preview iframe').count() === 1, '没有 iframe');
    await shot(page, '02-pdf-inline');
    return grantText(g) + '；iframe=1';
  });
  await step('PNG 查看 → INLINE + img', async () => {
    const n = grants.length;
    const signed = page.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 15000 });
    await preview.locator('li', { hasText: st.pngName }).getByRole('button', { name: /^查\s*看$/ }).click();
    await signed; await page.waitForTimeout(2500);
    const g = grants.slice(n)[0];
    expect(g?.query === '?disposition=INLINE' && g.disposition === 'INLINE', grantText(g));
    const img = preview.locator('.wf-file-preview img');
    expect(await img.count() === 1, '没有 img');
    const info = await img.evaluate(el => ({ complete: el.complete, w: el.naturalWidth, h: el.naturalHeight }));
    expect(info.complete && info.w === 240 && info.h === 120, JSON.stringify(info));
    await shot(page, '03-png-inline');
    return `${grantText(g)}；img ${info.w}x${info.h}`;
  });
  await step('下载 → ATTACHMENT', async () => {
    const n = grants.length;
    const popupP = ctx.waitForEvent('page', { timeout: 10000 }).catch(() => null);
    const signed = page.waitForResponse(r => r.url().includes('/download') && r.request().method() === 'POST', { timeout: 15000 });
    await preview.locator('li', { hasText: st.pdfName }).getByRole('button', { name: /^下\s*载$/ }).click();
    await signed;
    const popup = await popupP;
    const file = popup ? await popup.waitForEvent('download', { timeout: 8000 }).catch(() => null) : null;
    const g = grants.slice(n)[0];
    expect(g?.query === '?disposition=ATTACHMENT' && g.disposition === 'ATTACHMENT', grantText(g));
    await shot(page, '04-download');
    return `${grantText(g)}；新标签=${popup ? 'yes' : 'no'} 下载文件名=${file?.suggestedFilename() ?? '无'}`;
  });
  log('NETWORK>=400', JSON.stringify([...new Set(network)]));
  log('ERRORS', JSON.stringify([...new Set(errors)].slice(0, 20)));
  await browser.close();
}

// ---------- 四、收尾：终止实例 ----------
await step('终止验收实例', async () => {
  const d = await ok('GET', `/workflow/v1/instances/${st.instanceId}`);
  if (d.instance.status !== 'RUNNING') return `已是 ${d.instance.status}`;
  await ok('POST', `/workflow/v1/instances/${st.instanceId}/termination`, { reason: `${P} 验收结束` });
  return 'TERMINATED';
});
log(failures ? `DONE with ${failures} FAIL` : 'DONE all PASS');
process.exitCode = failures ? 1 : 0;
