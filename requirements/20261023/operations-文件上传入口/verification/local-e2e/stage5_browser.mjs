// 阶段 5 本机浏览器 + 接口验收（AC-01—AC-08）。接口步骤调用 stage5_api.py；浏览器步骤用 playwright-core（headless Chromium）。
// 只记录方法、路径、状态码与必要字段，不记录口令、令牌或签名地址。结果写 stage5-result.json，截图写 screenshots/。
// 用法：node stage5_browser.mjs
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync, crc32 } from 'node:zlib';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'https://127.0.0.1:18444';
const SHOTS = join(HERE, 'screenshots');
const SCRATCH = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/b348a3f3-d233-4740-85e3-de4698ec8851/scratchpad';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });

function env(file) {
  const values = {};
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim().replace(/^"|"$/g, '');
  }
  return values;
}
const secrets = env('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env');
const api = (...args) => JSON.parse(execFileSync('python3', ['stage5_api.py', ...args], { cwd: HERE, timeout: 240_000 }).toString().trim().split('\n').at(-1));

// ---------- 测试文件 ----------
function png(rgb) {
  const chunk = (kind, data) => {
    const length = Buffer.alloc(4); length.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(kind), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body) >>> 0);
    return Buffer.concat([length, body, crc]);
  };
  const header = Buffer.alloc(13); header.writeUInt32BE(1, 0); header.writeUInt32BE(1, 4); header.set([8, 2, 0, 0, 0], 8);
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(Buffer.from([0, ...rgb]))), chunk('IEND', Buffer.alloc(0))]);
}
const run = new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 14);
const logo = { name: `stage5-logo-${run}.png`, mimeType: 'image/png', buffer: png([Math.floor(Math.random() * 255), 120, 200]) };
const fake = { name: `stage5-fake-${run}.png`, mimeType: 'image/png', buffer: Buffer.from('this is plain text, not a PNG image\n'.repeat(4)) };
const svg = { name: `stage5-vector-${run}.svg`, mimeType: 'image/svg+xml', buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>') };
const big = { name: `stage5-big-${run}.png`, mimeType: 'image/png', buffer: Buffer.alloc(20 * 1024 * 1024 + 1) };
const sha = buffer => createHash('sha256').update(buffer).digest('hex');

// ---------- 记录 ----------
const results = {}; const network = []; const consoleErrors = []; const shots = [];
const expected4xx = [];
function record(ac, ok, evidence) {
  results[ac] = { result: ok ? 'PASS' : 'FAIL', evidence };
  console.log(`${ok ? 'PASS' : 'FAIL'} ${ac} ${JSON.stringify(evidence).slice(0, 600)}`);
}
const ONLY = process.env.STAGE5_ONLY ? process.env.STAGE5_ONLY.split(',') : null;
async function guarded(ac, fn) {
  if (ONLY && !ONLY.includes(ac)) return;
  try { await fn(); } catch (failure) { record(ac, false, { error: String(failure.message || failure).split('\n').slice(0, 3).join(' ').slice(0, 400) }); }
}
const assert = (condition, text) => { if (!condition) throw new Error(text); };

let browser;
async function open(who, email, password) {
  browser ??= await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  page.on('pageerror', e => consoleErrors.push(`${who} pageerror ${String(e).slice(0, 200)}`));
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(`${who} console.error ${m.text().slice(0, 220)}`); });
  ctx.on('response', r => {
    const u = new URL(r.url());
    if (u.pathname.startsWith('/api/')) network.push({ who, method: r.request().method(), path: u.pathname, status: r.status(), at: Date.now() });
    else if (u.port === '19400') network.push({ who, method: r.request().method(), path: '(object storage) ' + u.pathname.split('/').slice(0, 2).join('/'), status: r.status(), at: Date.now() });
  });
  // 登录接口有限流（429）：未进入控制台时等 30 秒重试，最多 4 次
  for (let attempt = 1; ; attempt++) {
    await page.goto(OPS + '/'); await page.waitForLoadState('networkidle');
    await page.getByLabel(/Email \/ initial account|Email \/ 初始化账号/).fill(email);
    await page.getByLabel(/^(Password|密码)$/).fill(password);
    await page.getByRole('button', { name: /^(Sign In|登\s*录)$/ }).click();
    if (await page.getByText(/Sign Out|退出/).first().waitFor({ timeout: 15000 }).then(() => true, () => false)) return { ctx, page };
    if (attempt === 4) throw new Error(`login ${who} failed`);
    await page.waitForTimeout(30_000);
  }
}
const settle = async (page, ms = 600) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
async function shot(page, name) {
  const file = join(SHOTS, `${name}.png`); await page.screenshot({ path: file, fullPage: true }); shots.push(`screenshots/${name}.png`); return `screenshots/${name}.png`;
}
async function gotoUpload(page) {
  await page.goto(OPS + '/file-upload'); await settle(page, 800);
  await page.locator('.file-upload-page').waitFor({ timeout: 15000 });
}
const fileInput = page => page.locator('.file-upload-panel input[type=file]');
const resultCard = page => page.locator('.file-upload-result');
const startCalls = since => network.filter(n => n.at >= since && n.method === 'POST' && n.path.endsWith('/file-upload/v1/uploads'));

const logMark = api('log-mark').offset;
const started = new Date().toISOString();
const { page: admin } = ONLY && !ONLY.some(ac => ['AC-01', 'AC-02', 'AC-03', 'AC-05', 'AC-06', 'AC-08'].includes(ac)) ? { page: null }
  : await open('admin', secrets.E2E_ADMIN_EMAIL, secrets.E2E_ADMIN_PASSWORD);
let logoFileId = null;

// AC-01 上传 png：检查通过、三个地址、匿名读取相同字节
await guarded('AC-01', async () => {
  await gotoUpload(admin);
  await admin.locator('.file-upload-constraints.is-configured').waitFor({ timeout: 15000 });
  const bar = await admin.locator('.file-upload-constraints').innerText();
  await shot(admin, '01-main-before-upload');
  await fileInput(admin).setInputFiles(logo);
  await resultCard(admin).locator('.file-upload-address').first().waitFor({ timeout: 45000 });
  const labels = await resultCard(admin).locator('.file-upload-address-label').allInnerTexts();
  const values = await resultCard(admin).locator('.file-upload-address-value').allInnerTexts();
  const tag = await resultCard(admin).locator('.ant-tag').first().innerText();
  const publicUrl = values[0]; const fileAddress = values.at(-1);
  logoFileId = fileAddress.split('/').at(-1);
  const evidence = { constraintsBar: bar.replace(/\s+/g, ' ').slice(0, 300), tag, labels, publicUrl, versionedUrl: values[1], fileAddress,
    screenshot: await shot(admin, '01-result-available') };
  const read = api('public-get', publicUrl, sha(logo.buffer));
  evidence.anonymousGet = read;
  record('AC-01', labels.length === 3 && tag === 'Check Passed' && read.status === 200 && read.sha256Match && fileAddress === `/files/v1/${logoFileId}`, evidence);
});

// AC-06 内容与声明类型不符：未通过、没有地址（Platform 在完成上传时同步做格式检查，422 且不生成文件）
await guarded('AC-06', async () => {
  const since = Date.now();
  await fileInput(admin).setInputFiles(fake);
  await resultCard(admin).locator('.ant-alert-error').waitFor({ timeout: 45000 });
  const alert = (await resultCard(admin).locator('.ant-alert-error').innerText()).replace(/\s+/g, ' ');
  const addresses = await resultCard(admin).locator('.file-upload-address').count();
  const completion = network.filter(n => n.at >= since && n.path.endsWith('/completion')).map(n => n.status);
  const shotFile = await shot(admin, '06-result-failed');
  await gotoUpload(admin);
  await admin.locator('.file-upload-recent tr', { hasText: logo.name }).waitFor({ timeout: 15000 });
  const fakeRows = await admin.locator('.file-upload-recent tr', { hasText: fake.name }).count();
  expected4xx.push('POST /api/operations/file-upload/v1/uploads/{uploadId}/completion -> 422 (AC-06 content check)');
  record('AC-06', addresses === 0 && /content failed the check/.test(alert) && completion.includes(422) && fakeRows === 0,
    { alert, addresses, completionStatuses: completion, fakeFileInRecentUploads: fakeRows, screenshot: shotFile });
});

// AC-02 不允许类型与超过上限：页面预校验拦截（不调接口），接口直接调用 422 且不产生文件
await guarded('AC-02', async () => {
  const before = Date.now();
  await fileInput(admin).setInputFiles(svg);
  await resultCard(admin).locator('.ant-alert-error').waitFor({ timeout: 5000 });
  const svgReason = (await resultCard(admin).locator('.ant-alert-error').innerText()).replace(/\s+/g, ' ');
  const svgShot = await shot(admin, '02-precheck-type');
  await fileInput(admin).setInputFiles(big);
  await admin.waitForTimeout(800);
  const bigReason = (await resultCard(admin).locator('.ant-alert-error').innerText()).replace(/\s+/g, ' ');
  const bigShot = await shot(admin, '02-precheck-size');
  const calls = startCalls(before).length;
  const direct = api('precheck-reject');
  expected4xx.push('POST /api/operations/file-upload/v1/uploads -> 422 (AC-02 direct, script)');
  record('AC-02', calls === 0 && /not allowed/.test(svgReason) && /larger than/.test(bigReason) && direct.svg.status === 422
    && direct.oversize.status === 422 && direct.totalBefore === direct.totalAfter,
  { pageStartCalls: calls, svgReason, bigReason, direct, screenshots: [svgShot, bigShot] });
});

// AC-05 最近上传：包含刚上传的文件；只看我的；文件记录跳转
await guarded('AC-05', async () => {
  await gotoUpload(admin);
  const recent = admin.locator('.file-upload-recent');
  await recent.locator('tr', { hasText: logo.name }).waitFor({ timeout: 15000 });
  const row = recent.locator('tr', { hasText: logo.name });
  const rowText = (await row.innerText()).replace(/\s+/g, ' ');
  const allRows = await recent.locator('tbody tr.ant-table-row').count();
  const beforeMine = Date.now();
  await recent.locator('.file-upload-mine .ant-switch').click(); await settle(admin, 800);
  const mineCall = network.filter(n => n.at >= beforeMine && n.path.endsWith('/file-upload/v1/files')).map(n => n.status);
  const mineRows = await recent.locator('tbody tr.ant-table-row').count();
  const mineHasLogo = await recent.locator('tr', { hasText: logo.name }).count();
  const recentShot = await shot(admin, '05-recent-only-mine');
  await recent.locator('tr', { hasText: logo.name }).getByRole('button', { name: /File Record/ }).click();
  await admin.waitForURL(/\/file-records\?file=\d+/, { timeout: 10000 }); await settle(admin, 1200);
  const url = new URL(admin.url());
  const detailTitle = await admin.locator('.file-detail-title').innerText().catch(() => '');
  const detailShot = await shot(admin, '05-file-record-detail');
  record('AC-05', /Me/.test(rowText) && /Check Passed/.test(rowText) && mineHasLogo > 0 && mineCall.includes(200)
    && url.searchParams.get('file') === logoFileId && detailTitle.includes(logo.name),
  { rowText: rowText.slice(0, 300), rowsAll: allRows, rowsMine: mineRows, mineRequestStatuses: mineCall, detailUrl: url.pathname + url.search,
    detailTitle: detailTitle.replace(/\s+/g, ' ').slice(0, 120), screenshots: [recentShot, detailShot] });
});

// AC-08 回归：文件记录、存储管理、卡 BIN 库页面；卡 BIN 开始上传 201
await guarded('AC-08', async () => {
  const pages = {};
  for (const [path, probe, name] of [['/file-records', '.file-records-page .ant-table', '08-file-records'],
    ['/file-storage', '.ant-table', '08-file-storage'], ['/card-bin-library', '.ant-table', '08-card-bin-library']]) {
    const since = Date.now();
    await admin.goto(OPS + path); await settle(admin, 1200);
    const visible = await admin.locator(probe).first().waitFor({ timeout: 15000 }).then(() => true, () => false);
    const failures = network.filter(n => n.at >= since && n.status >= 400).map(n => `${n.method} ${n.path} -> ${n.status}`);
    pages[path] = { visible, failures, screenshot: await shot(admin, name) };
  }
  await admin.goto(OPS + '/file-storage'); await settle(admin, 1200);
  await admin.getByRole('tab', { name: 'Write Rules' }).click(); await settle(admin, 1000);
  const storageText = await admin.locator('body').innerText();
  const routesVisible = storageText.includes('operations-console-bff') && storageText.includes('mor-platform-public');
  pages['/file-storage#write-rules'] = { screenshot: await shot(admin, '08-file-storage-write-rules') };
  const card = api('card-start');
  record('AC-08', Object.values(pages).every(p => p.visible !== false && !(p.failures ?? []).length) && routesVisible && card.status === 201,
    { pages, storageShowsOpsBffRules: routesVisible, cardStart: card });
});

// AC-04 只有查看权限：限制条与最近上传可见、没有上传区；接口开始上传 403
// 4a 只授予 FILE_UPLOAD_READ：父菜单「平台配置」562 绑定 GATEWAY_CONFIG_READ（534），记录导航是否可达；
// 4b 再加 534（导航前提，与块 130 / 134 的验收做法相同）核对页面只读形态。
async function viewerCheck(label, extra, name) {
  const created = api('person', `S5${run.slice(-8)}`, label, ...extra);
  const person = JSON.parse(readFileSync(join(SCRATCH, 'stage5-people.json'), 'utf8'))[label];
  const { page: viewer, ctx } = await open(label, person.email, person.password);
  await viewer.goto(OPS + '/file-upload'); await settle(viewer, 1500);
  const rendered = await viewer.locator('.file-upload-page').waitFor({ timeout: 8000 }).then(() => true, () => false);
  if (rendered) { await viewer.locator('.file-upload-recent').waitFor({ timeout: 10000 }); await settle(viewer, 800); }
  const evidence = { person: { accountId: created.accountId, roleId: created.roleId, permissions: created.permissions }, pageRendered: rendered,
    constraintsBar: rendered ? await viewer.locator('.file-upload-constraints.is-configured').count() > 0 : null,
    recentUploads: rendered ? await viewer.locator('.file-upload-recent').count() > 0 : null,
    uploadWorkspaceCount: await viewer.locator('.file-upload-workspace').count(),
    fileRecordLinks: rendered ? await viewer.locator('.file-upload-recent').getByRole('button', { name: /File Record/ }).count() : null,
    menu: (await viewer.locator('.ant-menu').first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 200),
    screenshot: await shot(viewer, name) };
  evidence.direct = api('viewer-start', label);
  expected4xx.push(`POST /api/operations/file-upload/v1/uploads -> 403 (AC-04 ${label} direct, script)`);
  await ctx.close();
  return evidence;
}
await guarded('AC-04', async () => {
  const onlyRead = await viewerCheck('viewera', [], '04a-only-file-upload-read');
  const withNav = await viewerCheck('viewerb', ['900000000000000534'], '04b-read-only-with-platform-settings-nav');
  record('AC-04', withNav.pageRendered && withNav.constraintsBar && withNav.recentUploads && withNav.uploadWorkspaceCount === 0
    && withNav.direct.start.status === 403 && withNav.direct.constraints === 200 && withNav.direct.files === 200
    && onlyRead.direct.start.status === 403 && onlyRead.direct.constraints === 200,
  { note: 'With FILE_UPLOAD_READ alone the API works (constraints/list 200, start 403) but the page is unreachable: parent menu 562 requires 900000000000000534 GATEWAY_CONFIG_READ.',
    onlyFileUploadRead: onlyRead, withPlatformSettingsNav: withNav });
});

// AC-03 未配置：删除 (operations-console-bff, PUBLIC) 写入规则 → 页面未配置、上传区禁用、接口 503 → 恢复
await guarded('AC-03', async () => {
  const removed = api('unroute');
  assert(removed.status === 200 || removed.status === 204, 'route delete ' + JSON.stringify(removed));
  const off = api('wait-configured', 'false');
  expected4xx.push('POST /api/operations/file-upload/v1/uploads -> 503 (AC-03 direct, script)');
  await gotoUpload(admin);
  await admin.locator('.file-upload-constraints.ant-alert-error').waitFor({ timeout: 15000 });
  const notice = (await admin.locator('.file-upload-constraints').innerText()).replace(/\s+/g, ' ');
  const selectDisabled = await admin.locator('.file-upload-panel').getByRole('button', { name: 'Select File' }).isDisabled();
  const inputDisabled = await fileInput(admin).isDisabled();
  const goToStorage = await admin.getByRole('button', { name: 'Go to Storage' }).count();
  const offShot = await shot(admin, '03-not-configured');
  const restored = api('restore');
  const on = api('wait-configured', 'true');
  await gotoUpload(admin);
  const back = await admin.locator('.file-upload-constraints.is-configured').waitFor({ timeout: 15000 }).then(() => true, () => false);
  record('AC-03', off.configured === false && off.start?.status === 503 && /not configured/.test(notice) && selectDisabled && inputDisabled
    && restored.route.bucketName === 'mor-platform-public' && on.configured === true && back,
  { removed, unconfigured: off, notice, selectDisabled, inputDisabled, goToStorageButton: goToStorage > 0, screenshot: offShot,
    restored: { setupExit: restored.exit, route: restored.route, privateRoute: restored.privateRoute }, reconfigured: on, pageConfiguredAgain: back });
});

// AC-07 ops-bff 日志不含临时 PUT 授权地址
await guarded('AC-07', async () => {
  const scan = api('log-scan', String(logMark));
  record('AC-07', scan.markers.length === 0 && scan.bytesScanned > 0, { window: [started, new Date().toISOString()], ...scan });
});

const cleanup = (() => { try { return api('cleanup'); } catch (failure) { return { error: String(failure.message).slice(0, 200) }; } })();
await browser?.close();

const unexpected = network.filter(n => n.status >= 400).map(n => `${n.who} ${n.method} ${n.path} -> ${n.status}`);
const previous = ONLY ? JSON.parse(readFileSync(join(HERE, 'stage5-result.json'), 'utf8')) : null;
const summary = previous ? { ...previous, results: { ...previous.results, ...results },
  reruns: [...(previous.reruns ?? []), { acs: ONLY, run, window: [started, new Date().toISOString()], browserHttp4xx5xx: [...new Set(unexpected)],
    consoleErrors: [...new Set(consoleErrors)], syntheticCleanup: cleanup, screenshots: shots }] } : {
  run, window: [started, new Date().toISOString()], environment: 'local (https://127.0.0.1:18444, mor_platform_v2e2e)',
  language: 'English: the local i18n manifest publishes no locale, so zh-CN cannot be selected (screenshots are English)',
  results, browserHttp4xx5xx: [...new Set(unexpected)], scriptExpected4xx5xx: expected4xx, consoleErrors: [...new Set(consoleErrors)],
  syntheticCleanup: cleanup, screenshots: shots,
};
writeFileSync(join(HERE, 'stage5-result.json'), JSON.stringify(summary, null, 2) + '\n');
console.log('HTTP>=400', JSON.stringify(summary.browserHttp4xx5xx));
console.log('CONSOLE', JSON.stringify(summary.consoleErrors));
console.log('CLEANUP', JSON.stringify(cleanup));
