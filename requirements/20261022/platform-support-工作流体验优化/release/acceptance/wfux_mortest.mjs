// 工作流体验优化 · mor-test 验收（阶段 7，会写 mor-test 业务数据，执行前需用户同意；本包准备时未运行）。
// 改编自 verification/local-e2e/（lib.mjs、seed_v2.py、r2_admin.mjs、r2_user2.mjs、b_started.mjs、c_catalog.mjs、d_definitions.mjs）
// 与 20261021/platform-support-工作流/release-inline/acceptance/inline_mortest.mjs（mor-test 登录、存储 curl --resolve、只读 SELECT）。
// 入口 http://localhost:28080（Gateway → ops-bff → platform-support）；管理员取 test-environment/private/access.md，口令不打印。
//
// mor-test 只有一个管理员：同一人提交过节点动作就不能审批该节点（B3），所以经 IAM 新建一个合成审批人
// （角色 QA_WFUX_APPROVER_<RUN> 只含 WORKFLOW_TASK_HANDLE 900000000000127105；首登改口令；口令只在内存中），收尾时停用该账号。
//
// 一、造数（API；数据前缀 WF-UX-20261023，编码前缀 WF_UX_20261023_）：
//   表单模板（商户名称 / 月交易量必填、区域枚举可选）、上传模板（required）、定义：
//   开始（启动表单：渠道编码必填）→ 资料准备（表单 + 上传两个必选动作，执行人 admin；审批人 = 合成审批人）
//   → 复核（纯审批，审批人 admin）→ 结束；发布 v1；发起 1 个实例；上传 1 份 PDF 并等扫描 AVAILABLE（只读 SELECT）。
// 二、API：发起缺必填启动字段 → 422 startValues.channelCode「必填」；完成表单缺键 → 422 output.monthlyVolume「必填」。
// 三、浏览器（admin）：我的待办收件箱（一项「填写」带「2 个动作」、页签 / 分段计数一致）→ 详情两行动作、默认展开表单 →
//   页面填写并「完成」表单（200）；上传动作经 API 用已扫描 PDF 完成 → 节点 APPROVING。
//   API：APPROVING 后再完成必选表单 → 409，detail 含「节点已进入审批，必选动作不能再完成或修改」与模板名称。
//   我发起的：菜单紧跟「我的待办」、页面列出实例、右侧小流程图与时间线、「完整详情」弹窗不跳转。
//   动作目录：页签计数、表单模板「1 个流程」、抽屉（执行人看到的样子 + 引用）、「编辑」进入编辑页（三栏设计器），不保存离开。
//   流程定义：卡片只有一个状态「已发布 v1」、一个主按钮「发起」、⋯ 菜单「编辑」进入画布编辑器（状态药丸、问题徽标），不保存离开。
// 四、浏览器（合成审批人）：待办「审批」项 → 动作与提交内容表 → 上传动作「查看」→ 文件卡片 → 「预览」INLINE + iframe；
//   「更多 → 查看完整流程」弹窗（流程图 + 活动，URL 不变）；「同意」→ 复核。
// 五、admin 经 API 同意「复核」→ 实例 COMPLETED；停用合成审批人账号（角色保留，见 release.md 第 7 节）。
// 状态（只含 ID）写同目录 wfux-mortest-state.json；截图 screenshots/wfux-*.png。
// 用法：node wfux_mortest.mjs        重跑时 RUN=B node wfux_mortest.mjs（编码与审批人邮箱加后缀）
//       SKIP_BROWSER=1               只跑 API 部分（浏览器步骤改由 API 推进，便于定位问题）
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, randomUUID } from 'node:crypto';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../../../../..');
const OPS = 'http://localhost:28080';
const API = OPS + '/api/operations';
const RUN = process.env.RUN ? `_${process.env.RUN}` : '';
const P = `WF-UX-20261023${RUN.replace('_', '-')}`, C = `WF_UX_20261023${RUN}`;
const STATE = join(HERE, 'wfux-mortest-state.json');
const SHOTS = join(HERE, 'screenshots');
const WORKFLOW_TASK_HANDLE = '900000000000127105';
const BROWSER = process.env.SKIP_BROWSER !== '1';
mkdirSync(SHOTS, { recursive: true });
const access = readFileSync(join(ROOT, 'test-environment/private/access.md'), 'utf8');
const ADMIN = { email: access.match(/账号[:：]\s*(\S+)/)[1], password: access.match(/密码[:：]\s*(\S+)/)[1] };
const log = (...a) => console.log(...a);
let failures = 0, passes = 0;
async function step(name, fn) {
  try { const evidence = await fn(); passes++; log('PASS', name, '|', evidence ?? ''); return true; }
  catch (e) { failures++; log('FAIL', name, '|', String(e.message || e).split('\n')[0].slice(0, 400)); return false; }
}
const expect = (cond, text) => { if (!cond) throw new Error(text); };
const save = st => writeFileSync(STATE, JSON.stringify(st, null, 1));

// ---------- HTTP（Origin、JSON、写命令带 Idempotency-Key；429 按 Retry-After 重试同一键） ----------
async function call(token, method, path, body) {
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
async function ok(token, method, path, body) {
  const r = await call(token, method, path, body);
  if (r.status >= 300) throw new Error(`${method} ${path.replace(/\?.*/, '')} -> HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 300)}`);
  return r.body;
}
async function login(email, password) {
  return (await ok(null, 'POST', '/auth/v1/login', { loginIdentifier: email, password })).accessToken;
}
/** 存储签名地址经 curl 访问：Node 不解析 *.localhost；--resolve 只改连接地址，Host 与签名不变。地址不打印。 */
function storageCurl(url, args, input) {
  const u = new URL(url);
  const port = u.port || (u.protocol === 'https:' ? '443' : '80');
  const res = u.hostname.endsWith('.localhost') ? ['--resolve', `${u.hostname}:${port}:127.0.0.1`] : [];
  return execFileSync('curl', ['-sS', '-k', '--max-time', '30', ...res, ...args, url], { input, encoding: input ? undefined : 'utf8' }).toString();
}
/** 只读 SELECT（mor-test root，口令只经 MYSQL_PWD 传入容器）。 */
function roSql(statement) {
  const pw = readFileSync(join(ROOT, 'test-environment/.env.test'), 'utf8').split('\n')
    .find(l => l.startsWith('MOR_MYSQL_ROOT_PASSWORD=')).split('=').slice(1).join('=').trim();
  return execFileSync('docker', ['exec', '-i', '-e', 'MYSQL_PWD', 'mor-test-mysql-1', 'mysql', '-uroot', '-N', '-B',
    '--default-character-set=utf8mb4', 'mor_platform', '-e', statement], { env: { ...process.env, MYSQL_PWD: pw }, encoding: 'utf8' }).trim();
}
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
async function upload(token, name, mime, data) {
  const grant = await ok(token, 'POST', '/workflow/v1/attachments/uploads', { filename: name, mimeType: mime, sizeBytes: String(data.length) });
  const auth = grant.authorization;
  const headerArgs = Object.entries(auth.headers || {}).flatMap(([k, v]) => ['-H', `${k}: ${v}`]);
  const status = Number(storageCurl(auth.url, ['-X', 'PUT', ...headerArgs, '--data-binary', '@-', '-o', '/dev/null', '-w', '%{http_code}'], data));
  expect(status >= 200 && status < 300, `存储 PUT ${name} -> HTTP ${status}`);
  const done = await ok(token, 'POST', `/workflow/v1/attachments/uploads/${grant.uploadId}/completion`);
  return { fileId: done.fileId, fileAddress: done.fileAddress ?? `/files/v1/${done.fileId}` };
}
async function waitAvailable(fileId, timeoutMs = 120000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (roSql(`SELECT scan_status FROM t_file_object WHERE file_id=${BigInt(fileId)}`) === 'AVAILABLE') return `${((Date.now() - start) / 1000).toFixed(1)}s`;
    await new Promise(s => setTimeout(s, 1500));
  }
  throw new Error(`${fileId} 扫描超时`);
}
const detail = (token, id) => ok(token, 'GET', `/workflow/v1/instances/${id}`);
function latestRuns(d, nodeKey) {
  const nodes = d.nodeRuns.filter(r => r.nodeKey === nodeKey);
  const last = nodes.reduce((m, r) => Math.max(m, r.roundNo), 1);
  const ids = new Set(nodes.filter(r => r.roundNo === last).map(r => r.nodeRunId));
  return Object.fromEntries(d.actionRuns.filter(a => ids.has(a.nodeRunId)).map(a => [a.actionKey, a]));
}
function pendingApproval(d, nodeKey) {
  const runs = Object.fromEntries(d.nodeRuns.map(r => [r.nodeRunId, r]));
  return d.approvals.filter(a => runs[a.nodeRunId].nodeKey === nodeKey && a.status === 'PENDING').at(-1);
}
const nodeStatus = (d, nodeKey) => d.nodeRuns.filter(r => r.nodeKey === nodeKey).at(-1)?.status;
const fieldErrors = body => (body?.errors ?? []).map(e => `${e.field}:${e.message}`);

// ---------- 一、造数 ----------
const adminToken = await login(ADMIN.email, ADMIN.password);
const st = { run: RUN || '-', startedAt: new Date().toISOString() };
st.adminId = (await ok(adminToken, 'GET', '/auth/v1/context')).accountId;
const reason = `${P} mor-test 验收：合成审批人（验收后停用）`;
const role = await ok(adminToken, 'POST', '/iam/v1/roles', {
  roleCode: `QA_WFUX_APPROVER${RUN}`, roleName: `${P} 合成审批人`, permissionIds: [WORKFLOW_TASK_HANDLE], reason });
st.approverRoleId = role.roleId; save(st);
const APPROVER = { email: `wfux-approver${RUN.toLowerCase().replace('_', '-')}-20261023@example.test`,
  password: 'Wf ux ' + randomBytes(18).toString('base64url') + '!' };
const initial = 'Wf init ' + randomBytes(18).toString('base64url') + '!';
const account = await ok(adminToken, 'POST', '/iam/v1/accounts', { email: APPROVER.email, initialPassword: initial, roleIds: [role.roleId], reason });
st.approverId = account.accountId; save(st);
{
  const first = await login(APPROVER.email, initial);
  const changed = await call(first, 'POST', '/auth/v1/password', { currentPassword: initial, newPassword: APPROVER.password });
  expect(changed.status < 300, `合成审批人改口令 HTTP ${changed.status}`);
}
const approverToken = await login(APPROVER.email, APPROVER.password);
log('approver', `role=${st.approverRoleId} account=${st.approverId}`);

const FORM_NAME = `${P} 商户资料`, UPLOAD_NAME = `${P} 资质文件`;
st.formTemplateId = (await ok(adminToken, 'POST', '/workflow-admin/v1/action-templates', {
  templateCode: `${C}_FORM`, templateName: FORM_NAME, actionType: 'FORM', description: 'mor-test 体验优化验收',
  config: { fields: [
    { key: 'merchantName', label: '商户名称', type: 'TEXT', required: true },
    { key: 'monthlyVolume', label: '月交易量', type: 'NUMBER', required: true },
    { key: 'region', label: '区域', type: 'ENUM', required: false,
      rules: { options: [{ value: 'GCC', label: '海湾' }, { value: 'EU', label: '欧洲' }] } }] } })).objectId;
st.uploadTemplateId = (await ok(adminToken, 'POST', '/workflow-admin/v1/action-templates', {
  templateCode: `${C}_UPLOAD`, templateName: UPLOAD_NAME, actionType: 'FILE_UPLOAD', description: 'mor-test 体验优化验收',
  config: { required: true, instruction: '上传营业执照 PDF' } })).objectId;
const DEF_NAME = `${P} 多动作流程`;
st.definitionId = (await ok(adminToken, 'POST', '/workflow-admin/v1/definitions', {
  definitionCode: `${C}_FLOW`, definitionName: DEF_NAME, description: 'mor-test 体验优化验收' })).objectId;
save(st);
const pos = (x, y) => ({ x, y });
const graph = { nodes: [
  { key: 'start', type: 'START', name: '开始', position: pos(0, 120) },
  { key: 'fill', type: 'TASK', name: '资料准备', position: pos(220, 100), approval: { mode: 'ANY', approverIds: [st.approverId] },
    actions: [{ key: 'merchantInfo', templateId: st.formTemplateId, required: true, executorId: st.adminId },
              { key: 'licenseFiles', templateId: st.uploadTemplateId, required: true, executorId: st.adminId }] },
  { key: 'review', type: 'TASK', name: '复核', position: pos(500, 100), approval: { mode: 'ANY', approverIds: [st.adminId] } },
  { key: 'end', type: 'END', name: '结束', position: pos(760, 120) }],
  edges: [{ key: 'e1', from: 'start', to: 'fill' }, { key: 'e2', from: 'fill', to: 'review' }, { key: 'e3', from: 'review', to: 'end' }] };
let def = await ok(adminToken, 'GET', `/workflow-admin/v1/definitions/${st.definitionId}`);
const draft = await ok(adminToken, 'PUT', `/workflow-admin/v1/definitions/${st.definitionId}/draft`, {
  definitionName: DEF_NAME, description: 'mor-test 体验优化验收', graph,
  startForm: { fields: [{ key: 'channelCode', label: '渠道编码', type: 'TEXT', required: true }] }, expectedVersion: def.definition.version });
log('draft problems', JSON.stringify(draft.problems ?? []));
def = await ok(adminToken, 'GET', `/workflow-admin/v1/definitions/${st.definitionId}`);
await ok(adminToken, 'POST', `/workflow-admin/v1/definitions/${st.definitionId}/publication`, { expectedVersion: def.definition.version });

// ---------- 二、API：422 startValues 与 output ----------
const TITLE = `${P} 实例`;
await step('API 发起缺必填启动字段 → 422 startValues.channelCode「必填」', async () => {
  const r = await call(adminToken, 'POST', '/workflow/v1/instances', { definitionId: st.definitionId, versionNo: 1, title: `${P} 缺字段`, startValues: {} });
  const errs = fieldErrors(r.body);
  expect(r.status === 422 && errs.some(e => e.startsWith('startValues.channelCode:') && e.includes('必填')), `HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 300)}`);
  return `HTTP 422 ${errs.join('; ')}`;
});
st.instanceId = (await ok(adminToken, 'POST', '/workflow/v1/instances', { definitionId: st.definitionId, versionNo: 1, title: TITLE,
  businessKey: P, startValues: { channelCode: 'CH-WFUX' } })).objectId;
save(st);
const pdf = await upload(adminToken, `${C}.pdf`, 'application/pdf', pdfBytes(`${P} license`));
Object.assign(st, { pdfName: `${C}.pdf`, pdfFileId: pdf.fileId, pdfAddress: pdf.fileAddress }); save(st);
log('scan', `${pdf.fileId} AVAILABLE ${await waitAvailable(pdf.fileId)}`);
let runs = latestRuns(await detail(adminToken, st.instanceId), 'fill');
await step('API 完成表单缺必填键 → 422 output.monthlyVolume「必填」，状态不变', async () => {
  const r = await call(adminToken, 'POST', `/workflow/v1/action-runs/${runs.merchantInfo.actionRunId}/submission`, { output: { merchantName: '缺月交易量' } });
  const errs = fieldErrors(r.body);
  const after = latestRuns(await detail(adminToken, st.instanceId), 'fill').merchantInfo.status;
  expect(r.status === 422 && errs.some(e => e.startsWith('output.monthlyVolume:') && e.includes('必填')) && after === 'PENDING', `HTTP ${r.status} ${errs} status=${after}`);
  return `HTTP 422 ${errs.join('; ')}；动作仍 ${after}`;
});

// ---------- 浏览器公共部分 ----------
let chromium, browser;
const network = [], pageErrors = [];
async function openAs(who, cred) {
  chromium ??= createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core')).chromium;
  browser ??= await chromium.launch({ headless: true, ...(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {}) });
  const ctx = await browser.newContext({ locale: 'zh-CN', viewport: { width: 1440, height: 900 }, acceptDownloads: true });
  const page = await ctx.newPage();
  page.on('pageerror', e => pageErrors.push(`${who} pageerror ${String(e).slice(0, 200)}`));
  page.on('console', m => { if (m.type() === 'error') pageErrors.push(`${who} console.error ${m.text().slice(0, 200)}`); });
  ctx.on('response', r => {
    const u = new URL(r.url());
    if (u.pathname.startsWith('/api/')) network.push({ who, method: r.request().method(), path: u.pathname + (u.search.includes('disposition') ? u.search : ''), status: r.status(), at: Date.now() });
  });
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto(OPS + '/'); await page.waitForLoadState('networkidle');
    await page.getByLabel('Email / 初始化账号').fill(cred.email);
    await page.getByLabel('密码').fill(cred.password);
    await page.getByRole('button', { name: /^登\s*录$/ }).click();
    if (await page.getByText('退出').first().waitFor({ timeout: 10000 }).then(() => true, () => false)) return { ctx, page };
  }
  throw new Error(`login ${who} failed`);
}
const settle = async (page, ms = 800) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
const shot = (page, name) => page.screenshot({ path: join(SHOTS, `wfux-${name}.png`) }).then(() => `screenshots/wfux-${name}.png`);
const calls = (pred, since) => network.filter(n => n.at >= since && pred(n)).map(n => `${n.method} ${n.path} -> ${n.status}`);
const num = s => Number((String(s).match(/\d+/) ?? ['-1'])[0]);
async function openTask(page, title) {
  const item = page.locator('.wf-inbox-item', { hasText: title }).first();
  if (!await item.waitFor({ timeout: 8000 }).then(() => true, () => false)) {
    if (!await page.locator('.wf-inbox-search input').count()) await page.getByRole('button', { name: '搜索' }).first().click();
    const input = page.locator('.wf-inbox-search input');
    await input.fill(title); await input.press('Enter'); await settle(page);
    await item.waitFor({ timeout: 15000 });
  }
  await item.click(); await settle(page);
  return item;
}

// ---------- 三、浏览器（admin） ----------
if (BROWSER) {
  const { page, ctx } = await openAs('admin', ADMIN);
  await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 2000);
  await step('我的待办：本实例为一项「填写」并注明「2 个动作」；待办 / 全部 / 填写 / 审批计数与列表项一致', async () => {
    await page.waitForFunction(() => /审批\s*\d+/.test(document.querySelector('.wf-inbox-tools .ant-segmented')?.textContent ?? ''), null, { timeout: 15000 });
    const tab = (await page.locator('.wf-page-head .wf-tab').first().innerText()).replace(/\s+/g, ' ');
    const seg = await page.locator('.wf-inbox-tools .ant-segmented-item').allInnerTexts();
    const items = await page.locator('.wf-inbox-item').count();
    const fills = await page.locator('.wf-inbox-item .wf-inbox-icon.is-action').count();
    const approvals = await page.locator('.wf-inbox-item .wf-inbox-icon.is-approval').count();
    const mine = page.locator('.wf-inbox-item', { hasText: TITLE });
    const mineText = (await mine.count()) ? (await mine.first().innerText()).replace(/\s+/g, ' ') : '';
    const counts = { 待办: num(tab), 全部: num(seg[0]), 填写: num(seg[1]), 审批: num(seg[2]), 列表项: items, 填写项: fills, 审批项: approvals };
    const f = await shot(page, '01-inbox');
    expect(await mine.count() === 1 && /2 个动作/.test(mineText), `本实例项=${await mine.count()} ${mineText}`);
    expect(counts.待办 === counts.全部 && counts.全部 === items && counts.填写 === fills && counts.审批 === approvals && fills + approvals === items, JSON.stringify(counts));
    return `${JSON.stringify(counts)}；本实例项「${mineText.slice(0, 80)}」；${f}`;
  });
  const row = name => page.locator('.wf-action-row', { hasText: name });
  await step('详情：两行动作（必选），默认展开表单；页面填写并「完成」→ 200', async () => {
    await openTask(page, TITLE);
    const heads = (await page.locator('.wf-action-row .wf-action-row-head').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
    const r = row(FORM_NAME);
    expect(heads.length === 2 && heads.every(h => /必选/.test(h)) && await r.locator('.wf-action-row-body').count() === 1, `heads=${JSON.stringify(heads)}`);
    await r.getByLabel('商户名称').fill('mor-test 商户');
    await r.getByLabel('月交易量').fill('120000');
    await r.locator('.ant-radio-button-wrapper', { hasText: '海湾' }).click().catch(() => {});
    const since = Date.now();
    await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1500);
    const posted = calls(n => n.path.includes('/submission'), since);
    const f = await shot(page, '02-form-completed');
    const status = latestRuns(await detail(adminToken, st.instanceId), 'fill').merchantInfo.status;
    expect(posted.some(p => p.endsWith('200')) && status === 'COMPLETED', `posted=${posted} status=${status}`);
    return `动作=${JSON.stringify(heads)}；${posted.join(';')}；表单 ${status}；${f}`;
  });
  await ctx.close();
} else {
  await ok(adminToken, 'POST', `/workflow/v1/action-runs/${runs.merchantInfo.actionRunId}/submission`,
    { output: { merchantName: 'mor-test 商户', monthlyVolume: '120000', region: 'GCC' } });
}
runs = latestRuns(await detail(adminToken, st.instanceId), 'fill');
await step('API 上传动作用已扫描 PDF 完成 → 节点进入 APPROVING', async () => {
  const res = await ok(adminToken, 'POST', `/workflow/v1/action-runs/${runs.licenseFiles.actionRunId}/submission`, { output: { files: [st.pdfAddress] } });
  const s = nodeStatus(await detail(adminToken, st.instanceId), 'fill');
  expect(res.status === 'COMPLETED' && s === 'APPROVING', `${JSON.stringify(res)} node=${s}`);
  return `动作 ${res.status}，节点 ${s}`;
});
await step('API APPROVING 后再完成必选表单 → 409，说明含模板名称', async () => {
  const r = await call(adminToken, 'POST', `/workflow/v1/action-runs/${runs.merchantInfo.actionRunId}/submission`,
    { output: { merchantName: '改', monthlyVolume: '1' } });
  const text = r.body?.detail ?? '';
  expect(r.status === 409 && text.includes('节点已进入审批，必选动作不能再完成或修改') && text.includes(FORM_NAME), `HTTP ${r.status} ${JSON.stringify(r.body).slice(0, 300)}`);
  return `HTTP 409 ${r.body?.code ?? ''}「${text}」`;
});

if (BROWSER) {
  const { page, ctx } = await openAs('admin', ADMIN);
  await step('菜单「工作流」下「我发起的」紧跟「我的待办」；进入 /workflow/started', async () => {
    await page.goto(`${OPS}/workflow/my-tasks`); await settle(page);
    const items = (await page.locator('.ant-menu-item').allInnerTexts()).map(t => t.trim());
    const wf = items.filter(t => ['我的待办', '我发起的', '流程实例', '流程定义', '动作目录'].includes(t));
    expect(wf.includes('我发起的') && wf.indexOf('我发起的') === wf.indexOf('我的待办') + 1, JSON.stringify(wf));
    await page.getByRole('menuitem', { name: '我发起的' }).click(); await settle(page, 1500);
    expect(page.url().includes('/workflow/started'), page.url());
    return `菜单=${wf.join(' / ')} → ${new URL(page.url()).pathname}`;
  });
  await step('我发起的：实例在「进行中」，当前步骤 · 等待人；右侧小流程图 + 时间线；「完整详情」弹窗不跳转', async () => {
    const rowOf = page.locator('.wf-started-table tbody tr', { hasText: TITLE });
    if (!await rowOf.count()) {
      await page.getByRole('button', { name: '搜索' }).click(); await page.locator('.wf-started-list input').fill(TITLE);
      await page.locator('.wf-started-list input').press('Enter'); await settle(page, 1200);
    }
    await rowOf.first().click(); await settle(page, 1500);
    const cells = (await rowOf.first().locator('td').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
    const detailBox = page.locator('.wf-started-detail');
    const canvas = await detailBox.locator('.react-flow').count();
    const timeline = (await detailBox.locator('.wf-vtimeline').innerText()).replace(/\s+/g, ' ');
    const f = await shot(page, '03-started');
    const url = page.url();
    await detailBox.getByRole('button', { name: '完整详情' }).click(); await page.waitForTimeout(1200);
    const modalFlow = await page.locator('.ant-modal:visible .react-flow').count();
    await page.locator('.ant-modal:visible .ant-modal-close').click(); await page.waitForTimeout(300);
    expect(/资料准备/.test(cells.join(' ')) && canvas === 1 && /你/.test(timeline) && modalFlow === 1 && page.url() === url,
      `cells=${cells} canvas=${canvas} modal=${modalFlow} timeline=${timeline.slice(0, 80)}`);
    return `行=${JSON.stringify(cells)}；时间线=${timeline.slice(0, 80)}；弹窗流程图=1、URL 不变；${f}`;
  });
  await step('动作目录：页签计数、表单模板「1 个流程」、抽屉（执行人看到的样子 + 引用）', async () => {
    await page.goto(`${OPS}/workflow/action-templates`); await settle(page, 2000);
    const tabs = (await page.locator('.wf-catalog-tabs .wf-tab').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
    const head = await page.locator('.wf-catalog-table thead th').allInnerTexts();
    let tr = page.locator('.wf-catalog-table tbody tr', { hasText: FORM_NAME });
    if (!await tr.count()) {
      await page.getByPlaceholder('搜索动作名称、编码').fill(FORM_NAME); await settle(page, 1200);
      tr = page.locator('.wf-catalog-table tbody tr', { hasText: FORM_NAME });
    }
    const cells = (await tr.first().locator('td').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
    await tr.first().click(); await settle(page, 1500);
    const drawer = page.locator('.wf-preview-drawer');
    const text = (await drawer.innerText()).replace(/\s+/g, ' ');
    const refs = (await drawer.locator('.wf-reference-list li').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
    const f = await shot(page, '04-catalog-drawer');
    expect(tabs[0]?.startsWith('全部') && head.join('|').includes('被引用') && cells.includes('1 个流程') && text.includes('执行人看到的样子') && refs.some(r => r.includes(DEF_NAME)),
      `tabs=${tabs} head=${head} cells=${cells} refs=${refs}`);
    return `页签=${JSON.stringify(tabs)}；行=${JSON.stringify(cells)}；引用=${JSON.stringify(refs)}；${f}`;
  });
  await step('动作目录「编辑」→ 编辑页：步骤导航、三栏字段设计（类型 / 画布 / 属性），不保存离开', async () => {
    await page.locator('.wf-preview-drawer').getByRole('button', { name: /^编\s*辑$/ }).click(); await settle(page, 1500);
    expect(page.url().includes(`/workflow/action-templates/${st.formTemplateId}`), page.url());
    await page.locator('.wf-tpl-step', { hasText: '字段设计' }).click(); await page.waitForTimeout(600);
    const columns = { 类型: await page.locator('.wf-designer-palette').count(), 字段: await page.locator('.wf-canvas-field').count(), 属性: await page.locator('.wf-designer-props').count() };
    const f = await shot(page, '05-template-editor');
    expect(columns.类型 === 1 && columns.字段 === 3 && columns.属性 === 1, JSON.stringify(columns));
    return `${new URL(page.url()).pathname} ${JSON.stringify(columns)}；${f}`;
  });
  await step('流程定义：卡片单一状态「已发布 v1」、一个主按钮「发起」；⋯ →「编辑」进入画布编辑器（状态药丸、问题徽标），不保存离开', async () => {
    await page.goto(`${OPS}/workflow/definitions`); await settle(page, 2500);
    const card = page.locator('.wf-def-card', { hasText: DEF_NAME }).first();
    await card.scrollIntoViewIfNeeded(); await settle(page, 1200);
    const pills = await card.locator('.wf-status-pill').allInnerTexts();
    const primary = await card.locator('.wf-def-actions .ant-btn:not([aria-label="更多操作"])').allInnerTexts();
    const f1 = await shot(page, '06-definition-card');
    await card.getByRole('button', { name: '更多操作' }).click(); await page.waitForTimeout(400);
    const menu = await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item').allInnerTexts();
    await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item', { hasText: '编辑' }).click(); await settle(page, 2500);
    const pill = await page.locator('.wf-editor-head .wf-status-pill').innerText().catch(() => '');
    const badge = (await page.locator('.wf-issues-badge').innerText().catch(() => '')).replace(/\s+/g, ' ');
    const nodes = await page.locator('.wf-editor-canvas .react-flow__node').count();
    const f2 = await shot(page, '07-definition-editor');
    expect(pills.length === 1 && /^已发布 v1$/.test(pills[0].trim()) && primary.length === 1 && /发\s*起/.test(primary[0]) && menu.includes('编辑') && nodes === 4,
      `pills=${pills} primary=${primary} menu=${menu} nodes=${nodes}`);
    return `状态=${pills} 主按钮=${primary} 菜单=${menu.join(' / ')}；编辑器 药丸=${pill} 徽标=${badge} 节点=${nodes}；${f1}, ${f2}`;
  });
  await ctx.close();
}

// ---------- 四、浏览器（合成审批人） ----------
if (BROWSER) {
  const { page, ctx } = await openAs('approver', APPROVER);
  await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 2000);
  const uploadRow = () => page.locator('.wf-submissions tbody tr', { hasText: UPLOAD_NAME }).first();
  await step('审批人待办：一项「审批」；详情「动作与提交内容」两行；上传动作「查看」→ 文件卡片（名称、大小、预览）', async () => {
    await openTask(page, TITLE);
    const rows = await page.locator('.wf-submissions tbody tr').count();
    const bar = (await page.locator('.wf-actionbar').innerText()).replace(/\s+/g, ' ');
    await uploadRow().getByRole('button', { name: /查\s*看/ }).click(); await page.waitForTimeout(800);
    const cards = page.locator('.wf-submission-detail .wf-file-card');
    const texts = (await cards.allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
    const preview = await cards.first().getByRole('button', { name: /预\s*览/ }).count();
    const f = await shot(page, '08-approval-file-card');
    expect(rows === 2 && /同\s*意/.test(bar) && texts.length === 1 && texts[0].includes(st.pdfName) && preview === 1, `rows=${rows} bar=${bar} cards=${JSON.stringify(texts)}`);
    return `提交内容 ${rows} 行；卡片=${JSON.stringify(texts)}；${f}`;
  });
  await step('文件卡片「预览」→ 弹窗 iframe，签发请求 ?disposition=INLINE 200', async () => {
    const since = Date.now();
    await page.locator('.wf-submission-detail .wf-file-card').first().getByRole('button', { name: /预\s*览/ }).click();
    await page.waitForTimeout(3500);
    const modal = page.locator('.ant-modal:visible');
    const grants = calls(n => n.path.includes('/download'), since);
    const iframe = await modal.locator('iframe').count();
    const box = iframe ? await modal.locator('iframe').boundingBox() : null;
    const f = await shot(page, '09-inline-preview');
    await modal.locator('.ant-modal-close').click(); await page.waitForTimeout(400);
    expect(grants.some(g => g.includes('disposition=INLINE') && g.endsWith('200')) && iframe === 1 && box && box.height > 200, `grants=${grants} iframe=${iframe}`);
    return `${grants.join(';')} iframe=${Math.round(box.width)}x${Math.round(box.height)}；${f}`;
  });
  await step('「更多 → 查看完整流程」：弹窗含流程图与活动，URL 不变', async () => {
    const url = page.url();
    await page.locator('.wf-actionbar').getByRole('button', { name: /更多/ }).click();
    await page.getByRole('menuitem', { name: '查看完整流程' }).click(); await page.waitForTimeout(1500);
    const modal = page.locator('.ant-modal:visible');
    const text = (await modal.innerText()).replace(/\s+/g, ' ');
    const canvas = await modal.locator('.react-flow').count();
    const f = await shot(page, '10-full-flow');
    await modal.locator('.ant-modal-close').click(); await page.waitForTimeout(400);
    expect(page.url() === url && canvas === 1 && text.includes('活动'), `url变化=${page.url() !== url} canvas=${canvas}`);
    return `弹窗=${text.slice(0, 80)}…；${f}`;
  });
  await step('审批人「同意」→ 资料准备通过，复核进入 APPROVING', async () => {
    const since = Date.now();
    await page.locator('.wf-actionbar').getByRole('button', { name: /^同\s*意$/ }).click(); await settle(page, 1500);
    const confirm = page.locator('.ant-modal:visible').getByRole('button', { name: /^(确\s*认|同\s*意)$/ });
    if (await confirm.count()) { await confirm.first().click(); await settle(page, 1500); }
    const posted = calls(n => n.path.includes('/decision'), since);
    const s = nodeStatus(await detail(adminToken, st.instanceId), 'review');
    expect(posted.some(p => p.endsWith('200')) && s === 'APPROVING', `posted=${posted} review=${s}`);
    return `${posted.join(';')}；复核 ${s}`;
  });
  await ctx.close();
} else {
  const a = pendingApproval(await detail(approverToken, st.instanceId), 'fill');
  await ok(approverToken, 'POST', `/workflow/v1/approvals/${a.approvalId}/decision`, { decision: 'APPROVE', comment: `${P} 资料无误` });
}

// ---------- 五、admin 同意复核 → COMPLETED；停用合成审批人 ----------
await step('admin 同意「复核」→ 实例 COMPLETED', async () => {
  const a = pendingApproval(await detail(adminToken, st.instanceId), 'review');
  expect(a, '没有待决定的复核审批');
  await ok(adminToken, 'POST', `/workflow/v1/approvals/${a.approvalId}/decision`, { decision: 'APPROVE', comment: `${P} 复核通过` });
  const d = await detail(adminToken, st.instanceId);
  expect(d.instance.status === 'COMPLETED', `status=${d.instance.status}`);
  return `实例 ${d.instance.status}`;
});
await step('停用合成审批人账号（角色保留，供历史显示）', async () => {
  let found = null;
  for (let page = 1; page <= 20 && !found; page++) {
    const list = await ok(adminToken, 'GET', `/iam/v1/accounts?page=${page}&size=100`);
    found = (list.items ?? []).find(a => a.accountId === st.approverId);
    if (!list.items?.length || list.items.length < 100) break;
  }
  expect(found, '账号列表中没有合成审批人');
  await ok(adminToken, 'POST', `/iam/v1/accounts/${st.approverId}/disable`, { expectedVersion: found.version, reason });
  return `账号 ${st.approverId} 已停用`;
});
st.finishedAt = new Date().toISOString(); save(st);
if (browser) {
  const bad = [...new Set(network.filter(n => n.status >= 400).map(n => `${n.who} ${n.method} ${n.path} -> ${n.status}`))];
  log('NETWORK>=400', JSON.stringify(bad));
  log('PAGE ERRORS', JSON.stringify([...new Set(pageErrors)].slice(0, 20)));
  await browser.close();
}
log(failures ? `DONE ${passes} PASS / ${failures} FAIL` : `DONE all ${passes} PASS`);
process.exitCode = failures ? 1 : 0;
