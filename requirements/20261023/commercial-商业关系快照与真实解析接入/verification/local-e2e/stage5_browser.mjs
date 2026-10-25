// 阶段 5 浏览器验收 AC-12：主体资格 /compliance/qualifications、经营主体 /master-data/operating-entities[/:entityNo]、
// Local Partner /master-data/local-partners[/:id]。先英文（默认）完整走一遍（含真实写入），再切换简体中文核对展示与对话框（不写入）。
// 页面：worktree ops-web 的 Vite（http://127.0.0.1:18311，/api/operations 经 nginx 18444 → Gateway → ops-bff，Origin 改写为运营站点）。
// 语言包公开桶（MinIO 19400）的 CORS 只放行 https://127.0.0.1:18444：测试在浏览器上下文里为 19400 的响应补 ACAO（见 bundleCors），
// 不改应用与存储配置。只记录方法、路径、状态码与必要字段，不记录口令或令牌。结果写 stage5-browser-result.json，截图写 screenshots/。
// 用法：node stage5_browser.mjs
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = process.env.STAGE5_WEB ?? 'http://127.0.0.1:18311';
const SHOTS = join(HERE, 'screenshots');
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });

const PSP = '900000000001380201', MOR = '237161687952666625', PAYFAC = '900000000001370001';
function env(file) {
  const values = {};
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim().replace(/^"|"$/g, '');
  }
  return values;
}
const secrets = env('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env');
const api = (...args) => JSON.parse(execFileSync('python3', ['stage5_browser_api.py', ...args], { cwd: HERE, timeout: 120_000 }).toString().trim().split('\n').at(-1));

// ---------- 记录 ----------
const results = []; const network = []; const consoleErrors = []; const shots = []; const expected = [];
let lang = 'en';
function record(id, name, ok, evidence = {}) {
  results.push({ id, lang, name, result: ok ? 'PASS' : 'FAIL', evidence });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${lang}] ${id} ${name} ${JSON.stringify(evidence).slice(0, 400)}`);
}
async function guarded(id, name, fn) {
  try { await fn(); } catch (failure) {
    record(id, name, false, { error: String(failure.message || failure).split('\n').slice(0, 4).join(' ').slice(0, 500) });
    await shot(page, `${lang}-${id}-error`).catch(() => {});
    await page.keyboard.press('Escape').catch(() => {});
  }
}
const assert = (condition, text) => { if (!condition) throw new Error(text); };
const L = (en, zh) => (lang === 'en' ? en : zh);
// antd 在两个汉字的按钮文字中间插入空格（“取 消”），两字中文按钮允许字间空白
const escape = text => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const exact = text => new RegExp(`^\\s*${/^[\u4e00-\u9fff]{2}$/.test(text) ? [...text].join('\\s*') : escape(text)}\\s*$`);

// ---------- 浏览器 ----------
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 900 } });
// bundleCors：本地公开桶只放行运营站点 Origin；为测试端口补同样的 ACAO（内容、状态与其余头原样）。
await ctx.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
  const response = await route.fetch();
  await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': WEB } });
});
const page = await ctx.newPage();
page.on('pageerror', e => consoleErrors.push(`${lang} pageerror ${String(e).slice(0, 300)}`));
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(`${lang} console.error ${m.text().slice(0, 300)} @ ${new URL(page.url()).pathname}`); });
ctx.on('response', r => {
  const u = new URL(r.url());
  if (u.pathname.startsWith('/api/')) network.push({ lang, method: r.request().method(), path: u.pathname + (u.search ? '?' + u.searchParams.toString() : ''), status: r.status(), at: Date.now() });
  else if (u.port === '19400') network.push({ lang, method: r.request().method(), path: '(public bucket) ' + u.pathname.split('/').slice(0, 7).join('/'), status: r.status(), at: Date.now() });
});
ctx.on('requestfailed', r => network.push({ lang, method: r.method(), path: new URL(r.url()).pathname, status: 'FAILED ' + (r.failure()?.errorText ?? ''), at: Date.now() }));

const settle = async (ms = 600) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
async function shot(target, name) {
  const file = join(SHOTS, `${name}.png`); await target.screenshot({ path: file, fullPage: true }); shots.push(`screenshots/${name}.png`); return `screenshots/${name}.png`;
}
async function login() {
  for (let attempt = 1; ; attempt++) {
    await page.goto(WEB + '/'); await settle();
    await page.getByLabel(/Email \/ initial account|Email \/ 初始化账号|邮箱/).first().fill(secrets.E2E_ADMIN_EMAIL);
    await page.getByLabel(/^(Password|密码)$/).fill(secrets.E2E_ADMIN_PASSWORD);
    await page.getByRole('button', { name: /^(Sign In|登\s*录)$/ }).click();
    if (await page.getByText(/Sign Out|退出/).first().waitFor({ timeout: 15000 }).then(() => true, () => false)) return;
    if (attempt === 4) throw new Error('login failed');
    await page.waitForTimeout(30_000);
  }
}
const content = () => page.locator('.console-content');
const dialog = () => page.getByRole('dialog').last();
const formItem = (scope, label) => scope.locator('.ant-form-item').filter({ has: page.locator('.ant-form-item-label', { hasText: exact(label) }) });
async function pick(item, text) {
  await item.locator('.ant-select').first().click();
  const option = page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: exact(text) });
  await option.first().click();
}
async function dropdownOptions(item) {
  await item.locator('.ant-select').first().click(); await page.waitForTimeout(300);
  const values = await page.locator('.ant-select-dropdown:visible .ant-select-item-option').allInnerTexts();
  await page.keyboard.press('Escape');
  return values.map(v => v.trim());
}
async function messageText(expectText) {
  const notice = expectText ? page.locator('.ant-message-notice', { hasText: expectText }).last() : page.locator('.ant-message-notice').last();
  await notice.waitFor({ timeout: 15000 });
  return (await notice.innerText()).trim();
}
// 整页导航会重新请求 auth/v1/context（每分钟限流）；探针未出现时等待 20 秒后重载，最多 3 次
async function goto(path, probe) {
  for (let attempt = 1; ; attempt++) {
    await page.goto(WEB + path); await settle(900);
    if (!probe) return;
    const ok = await page.locator(probe).first().waitFor({ timeout: 15000, state: probe.includes('h1') ? 'attached' : 'visible' }).then(() => true, () => false);
    if (ok) return;
    if (attempt === 3) throw new Error(`page ${path} did not render ${probe}`);
    await page.waitForTimeout(20_000);
  }
}
const heading = async () => (await page.locator('.page-heading h1').first().textContent()).trim();
const menuText = async () => (await page.locator('.console-sidebar').innerText()).replace(/\s+/g, ' ');

/** 中文页面扫描：原样 key、以及不在白名单（规范术语 / 编码 / 业务数据）中的英文词。 */
const ALLOWED_LATIN = new Set(['Client', 'Local', 'Partner', 'KYB', 'KYC', 'MERCHANT', 'MoR', 'PSP', 'PayFac', 'Party', 'locale', 'MCC',
  'Relsnap', 'Synthetic', 'LLC', 'MOR', 'PAYFAC', 'USD', 'RETAIL', 'ID', 'OE', 'STAGE5_BROWSER_E2E', 'STAGE5_LOCAL_E2E', 'Webhook', 'BIN', 'API',
  'again']);  // again：阶段 5 后端联调写入的依据说明原文（业务数据，“AE001 again”）
async function scanZh(name, scope = content()) {
  const text = await scope.innerText();
  const rawKeys = [...new Set(text.match(/\b(qualification|master-data|nav|common|enum|auth)\.[A-Za-z][\w.-]+/g) ?? [])];
  const latin = [...new Set((text.match(/[A-Za-z][A-Za-z0-9_-]{2,}/g) ?? []).filter(w => !ALLOWED_LATIN.has(w) && !/^[A-Z0-9_-]+$/.test(w) && !/\d/.test(w)))];
  return { name, rawKeys, latin };
}
const zhScans = [];

// ---------- 用例 ----------
async function menus() {
  await guarded('M1', 'menus visible for the e2e admin', async () => {
    const seen = {};
    for (const [path, label, parent] of [['/compliance/qualifications', L('Qualifications', '主体资格'), L('Risk', '风控')],
      ['/master-data/operating-entities', L('Operating Entities', '经营主体'), L('Commercial', '商务')],
      ['/master-data/local-partners', L('Local Partners', 'Local Partner'), L('Commercial', '商务')]]) {
      await goto(path, '.page-heading h1');
      const menu = await menuText(); const h1 = await heading();
      seen[path] = { h1, menuHasLabel: menu.includes(label), menuHasParent: menu.includes(parent) };
    }
    const file = await shot(page, `${lang}-M1-menu-local-partners`);
    record('M1', 'menus visible for the e2e admin', Object.values(seen).every(v => v.menuHasLabel && v.menuHasParent), { seen, screenshot: file });
  });
}

async function qualificationPage(writes) {
  await guarded('Q1', 'qualification list and filters', async () => {
    await goto('/compliance/qualifications', '.qualification-page .ant-table');
    await page.locator('.qualification-page .ant-table-row').first().waitFor({ timeout: 15000 });
    const all = await page.locator('.qualification-page .ant-table-row').count();
    const listShot = await shot(page, `${lang}-Q1-list`);
    const search = page.locator('.qualification-search');
    await pick(formItem(search, L('Role', '角色')), L('Client', 'Client'));
    await pick(formItem(search, L('Qualification Type', '资格类型')), L('Organization KYB', '企业 KYB'));
    await pick(formItem(search, L('Stored Status', '存储状态')), L('Approved', '已批准'));
    await search.getByRole('button', { name: exact(L('Search', '查询')) }).click(); await settle(800);
    const url = new URL(page.url());
    const filtered = await page.locator('.qualification-page .ant-table-row').allInnerTexts();
    const filterShot = await shot(page, `${lang}-Q1-filtered-client-kyb-approved`);
    await search.getByRole('button', { name: exact(L('Reset', '重置')) }).click(); await settle(600);
    const reset = await page.locator('.qualification-page .ant-table-row').count();
    if (lang === 'zh-CN') zhScans.push(await scanZh('Q1 list'));
    record('Q1', 'qualification list and filters', all >= 4 && filtered.length === 2 && filtered.every(r => /Client/.test(r)) && reset === all
      && url.searchParams.get('roleType') === 'CLIENT' && url.searchParams.get('qualificationType') === 'ORGANIZATION_KYB' && url.searchParams.get('status') === 'APPROVED',
    { rowsAll: all, filterQuery: url.search, filteredRows: filtered.map(r => r.replace(/\s+/g, ' ').slice(0, 140)), rowsAfterReset: reset, screenshots: [listShot, filterShot] });
  });

  await guarded('Q2', 'find subject: OE AE001, Local Partner, Client', async () => {
    const found = {};
    for (const [role, ref, roleLabel, types] of [['OPERATING_ENTITY', 'AE001', L('Operating Entity', '经营主体'), [L('Entity Underwriting', '主体承保审核')]],
      ['LOCAL_PARTNER', PAYFAC, 'Local Partner', [L('Partner Due Diligence', 'Partner 尽调')]],
      ['CLIENT', PSP, 'Client', [L('Individual KYC', '个人 KYC'), L('Organization KYB', '企业 KYB')]]]) {
      await goto('/compliance/qualifications', '.qualification-finder');
      const finder = page.locator('.qualification-finder');
      if (role !== 'OPERATING_ENTITY') await pick(formItem(finder, L('Role', '角色')), roleLabel);
      await formItem(finder, L('Number / ID', '编号 / ID')).locator('input').fill(ref.toLowerCase());
      await finder.getByRole('button', { name: exact(L('Find', '查找')) }).click(); await settle(900);
      await page.locator('.qualification-subject-head').waitFor({ timeout: 15000 });
      const head = (await page.locator('.qualification-subject-head').innerText()).replace(/\s+/g, ' ');
      const slots = (await page.locator('.qualification-slot').allInnerTexts()).map(s => s.replace(/\s+/g, ' '));
      const url = new URL(page.url());
      found[role] = { head, slots, query: url.search, screenshot: await shot(page, `${lang}-Q2-subject-${role.toLowerCase()}`),
        ok: url.searchParams.get('roleId') === ref && slots.length === types.length && types.every((t, i) => slots[i].startsWith(t)) && head.includes(ref) };
    }
    if (lang === 'zh-CN') zhScans.push(await scanZh('Q2 subject client'));
    record('Q2', 'find subject: OE AE001, Local Partner, Client', Object.values(found).every(f => f.ok), found);
  });

  await guarded('Q3', 'history drawer (timeline: transition, source, actor, basis)', async () => {
    await goto(`/compliance/qualifications?roleType=OPERATING_ENTITY&roleId=AE001`, '.qualification-slot');
    await page.locator('.qualification-slot').first().getByRole('button', { name: exact(L('History', '历史')) }).click();
    const drawer = page.getByRole('dialog').last();
    await drawer.locator('.qualification-history-item').first().waitFor({ timeout: 15000 });
    await page.waitForTimeout(800);  // 抽屉滑入动画结束后再读取与截图
    const title = (await drawer.locator('.ant-drawer-title').first().innerText().catch(() => '')).trim();
    const items = (await drawer.locator('.qualification-history-item').allInnerTexts()).map(s => s.replace(/\s+/g, ' ').slice(0, 220));
    const file = await shot(page, `${lang}-Q3-history-drawer-ae001`);
    if (lang === 'zh-CN') zhScans.push(await scanZh('Q3 history drawer', drawer));
    await page.keyboard.press('Escape'); await page.waitForTimeout(400);
    // 首条为手工登记：触发方式与决定来源两个标签应不同（不再出现 “Manual Manual”）
    const tags = (items[0].match(L(/(Operations Action)\s*(Manual)/, /(运营操作)\s*(手工登记)/)) ?? []).slice(1);
    record('Q3', 'history drawer (timeline: transition, source, actor, basis)', items.length >= 1 && title.length > 0 && tags.length === 2 && tags[0] !== tags[1]
      && items.every(i => new RegExp(L('Manual|Onboarding Review|Automated|Override|Backfill', '手工|入驻审核|自动|人工改判|回填|审核')).test(i)), { title, firstItemTags: tags, items, screenshot: file });
  });

  await guarded('Q4', 'manual decision dialog: allowed types per role', async () => {
    const seen = {};
    for (const [role, ref, slotIndex] of [['OPERATING_ENTITY', 'AE001', 0], ['LOCAL_PARTNER', PAYFAC, 0], ['CLIENT', PSP, 0]]) {
      await goto(`/compliance/qualifications?roleType=${role}&roleId=${ref}`, '.qualification-slot');
      await page.locator('.qualification-slot').nth(slotIndex).getByRole('button', { name: exact(L('Register', '登记')) }).click();
      await dialog().waitFor();
      const typeItem = formItem(dialog(), L('Qualification Type', '资格类型'));
      const disabled = await typeItem.locator('.ant-select-disabled').count() > 0;
      const options = disabled ? [(await typeItem.locator('.ant-select').innerText()).trim()] : await dropdownOptions(typeItem);
      const file = await shot(page, `${lang}-Q4-decision-dialog-${role.toLowerCase()}`);
      if (lang === 'zh-CN' && role === 'CLIENT') zhScans.push(await scanZh('Q4 decision dialog', dialog()));
      await dialog().getByRole('button', { name: exact(L('Cancel', '取消')) }).click(); await page.waitForTimeout(400);
      seen[role] = { typeSelectDisabled: disabled, options, screenshot: file };
    }
    record('Q4', 'manual decision dialog: allowed types per role',
      JSON.stringify(seen.OPERATING_ENTITY.options) === JSON.stringify([L('Entity Underwriting', '主体承保审核')])
      && JSON.stringify(seen.LOCAL_PARTNER.options) === JSON.stringify([L('Partner Due Diligence', 'Partner 尽调')])
      && JSON.stringify(seen.CLIENT.options) === JSON.stringify([L('Individual KYC', '个人 KYC'), L('Organization KYB', '企业 KYB')]), seen);
  });

  const pspSlot = () => page.locator('.qualification-slot').filter({ hasText: L('Organization KYB', '企业 KYB') });
  async function openPsp() { await goto(`/compliance/qualifications?roleType=CLIENT&roleId=${PSP}`, '.qualification-slot'); }
  async function fillDecision() {
    await formItem(dialog(), L('Reason Code', '原因码')).locator('input').fill('STAGE5_BROWSER_E2E');
    await formItem(dialog(), L('Basis', '依据说明')).locator('textarea').fill(lang === 'en' ? 'Stage 5 browser acceptance (AC-12), synthetic PSP Client.' : '阶段 5 浏览器验收（AC-12），合成 PSP Client。');
    await dialog().getByRole('button', { name: exact(L('Next', '下一步')) }).click();
    await dialog().locator('.ant-alert-info').waitFor({ timeout: 5000 });
  }
  async function revokeViaUi(id) {
    await openPsp();
    await pspSlot().getByRole('button', { name: exact(L('Revoke', '撤销')) }).click(); await dialog().waitFor();
    const warning = (await dialog().locator('.ant-alert-warning').first().innerText()).replace(/\s+/g, ' ');
    await formItem(dialog(), L('Reason Code', '原因码')).locator('input').fill('STAGE5_BROWSER_E2E');
    await formItem(dialog(), L('Basis', '依据说明')).locator('textarea').fill('Stage 5 browser acceptance (AC-12): revoke synthetic PSP Client.');
    const file = await shot(page, `${lang}-${id}-revoke-dialog`);
    await dialog().getByRole('button', { name: exact(L('Revoke Qualification', '撤销资格')) }).click();
    const message = await messageText(L('Qualification revoked', '资格已撤销'));
    await page.locator('.ant-message-notice').first().waitFor({ state: 'detached', timeout: 10000 }).catch(() => {}); await settle(900);
    const slot = (await pspSlot().innerText()).replace(/\s+/g, ' ');
    return { warning, message, slot, screenshot: file, after: await shot(page, `${lang}-${id}-after-revoke`) };
  }

  if (writes) {
    await guarded('Q5', 'real revocation of PSP Client ORGANIZATION_KYB', async () => {
      const before = api('state').psp;
      const r = await revokeViaUi('Q5');
      const after = api('state').psp;
      record('Q5', 'real revocation of PSP Client ORGANIZATION_KYB', before.status === 'APPROVED' && after.status === 'REVOKED'
        && /Qualification revoked/.test(r.message) && /Revoked/.test(r.slot), { before, after, ...r });
    });

    await guarded('Q6', 'stale version: 409 message and refresh', async () => {
      await openPsp();
      await pspSlot().getByRole('button', { name: exact(L('Register', '登记')) }).click(); await dialog().waitFor();
      await fillDecision();
      const impact = (await dialog().locator('.ant-alert-info').innerText()).replace(/\s+/g, ' ');
      const confirmShot = await shot(page, `${lang}-Q6-decision-confirm-impact`);
      const concurrent = api('register-psp');   // 另一操作者抢先登记，页面持有的 expectedVersion 过期
      const since = Date.now();
      await dialog().getByRole('button', { name: exact(L('Confirm Registration', '确认登记')) }).click();
      await dialog().locator('.ant-alert-warning').waitFor({ timeout: 15000 });
      const banner = (await dialog().locator('.ant-alert-warning').innerText()).replace(/\s+/g, ' ');
      const statuses = network.filter(n => n.at >= since && n.path.endsWith('/manual-decisions')).map(n => n.status);
      const conflictShot = await shot(page, `${lang}-Q6-stale-409`);
      expected.push('POST /api/operations/qualification-admin/v1/qualifications/manual-decisions -> 409 (Q6 stale version, deliberate)');
      await dialog().getByRole('button', { name: exact(L('Refresh', '刷新')) }).click(); await settle(900);
      const slot = (await pspSlot().innerText()).replace(/\s+/g, ' ');
      record('Q6', 'stale version: 409 message and refresh', concurrent.status === 200 && statuses.includes(409) && /changed after you opened it/.test(banner)
        && /New order resolutions will treat this subject as qualified/.test(impact) && /Eligible/.test(slot),
      { impact, concurrentRegistration: concurrent, decisionStatuses: statuses, banner, slotAfterRefresh: slot, screenshots: [confirmShot, conflictShot] });
    });

    await guarded('Q7', 'revocation + re-registration through the page', async () => {
      const revoked = await revokeViaUi('Q7');
      await pspSlot().getByRole('button', { name: exact(L('Register', '登记')) }).click(); await dialog().waitFor();
      await fillDecision();
      await dialog().getByRole('button', { name: exact(L('Confirm Registration', '确认登记')) }).click();
      const message = await messageText('Qualification registered'); await settle(900);
      const slot = (await pspSlot().innerText()).replace(/\s+/g, ' ');
      const file = await shot(page, `${lang}-Q7-after-register`);
      const after = api('state').psp;
      record('Q7', 'revocation + re-registration through the page', /Revoked/.test(revoked.slot) && /Qualification registered/.test(message)
        && /Eligible/.test(slot) && after.status === 'APPROVED' && after.effectiveStatus === 'ELIGIBLE',
      { revokeMessage: revoked.message, slotAfterRevoke: revoked.slot, registerMessage: message, slotAfterRegister: slot, state: after, screenshot: file });
    });
  } else {
    await guarded('Q6', 'decision confirm step (impact notice) and revoke dialog, cancelled', async () => {
      await openPsp();
      await pspSlot().getByRole('button', { name: exact(L('Register', '登记')) }).click(); await dialog().waitFor();
      await fillDecision();
      const impact = (await dialog().locator('.ant-alert-info').innerText()).replace(/\s+/g, ' ');
      const confirmShot = await shot(page, `${lang}-Q6-decision-confirm-impact`);
      zhScans.push(await scanZh('Q6 decision confirm', dialog()));
      await dialog().getByRole('button', { name: exact('返回修改') }).click().catch(() => {});
      await page.keyboard.press('Escape'); await page.waitForTimeout(400);
      await pspSlot().getByRole('button', { name: exact('撤销') }).click(); await dialog().waitFor();
      const warning = (await dialog().locator('.ant-alert-warning').first().innerText()).replace(/\s+/g, ' ');
      const revokeShot = await shot(page, `${lang}-Q6-revoke-dialog`);
      zhScans.push(await scanZh('Q6 revoke dialog', dialog()));
      await dialog().getByRole('button', { name: exact('取消') }).click();
      record('Q6', 'decision confirm step (impact notice) and revoke dialog, cancelled', impact.includes('登记后新订单解析会把该主体视为合格') && warning.length > 0,
        { impact, revokeWarning: warning, screenshots: [confirmShot, revokeShot], state: api('state').psp });
    });
  }
}

async function entityPages(writes) {
  await guarded('E1', 'operating entity list with filters', async () => {
    await goto('/master-data/operating-entities', '.master-data-page .ant-table-row');
    const rows = (await page.locator('.ant-table-row').allInnerTexts()).map(r => r.replace(/\s+/g, ' '));
    const listShot = await shot(page, `${lang}-E1-entity-list`);
    const search = page.locator('.master-data-search');
    await pick(formItem(search, L('Source', '来源')), L('Partner', 'Partner'));
    await formItem(search, L('Country', '国家')).locator('input').fill('ae');
    await search.getByRole('button', { name: exact(L('Search', '查询')) }).click(); await settle(800);
    const url = new URL(page.url());
    const filtered = (await page.locator('.ant-table-row').allInnerTexts()).map(r => r.replace(/\s+/g, ' '));
    const filterShot = await shot(page, `${lang}-E1-entity-list-partner-ae`);
    await pick(formItem(search, L('Source', '来源')), L('Client', 'Client'));
    await search.getByRole('button', { name: exact(L('Search', '查询')) }).click(); await settle(800);
    const empty = (await page.locator('.ant-table-placeholder').innerText().catch(() => '')).trim();
    if (lang === 'zh-CN') zhScans.push(await scanZh('E1 entity list (empty filter)'));
    record('E1', 'operating entity list with filters', rows.some(r => r.includes('AE001') && r.includes(L('PayFac', 'PayFac'))) && filtered.length === 1
      && url.searchParams.get('sourceType') === 'PARTNER' && url.searchParams.get('country') === 'AE' && empty.length > 0,
    { rows, filterQuery: url.search, filtered, emptyText: empty, screenshots: [listShot, filterShot] });
  });

  await guarded('E2', 'AE001 detail: tier card, provider card, scopes, disclosures note, qualification card', async () => {
    await goto('/master-data/operating-entities', '.ant-table-row');
    await page.locator('.ant-table-row', { hasText: 'AE001' }).click(); await page.waitForURL(/\/master-data\/operating-entities\/AE001$/); await settle(900);
    await page.locator('.master-data-tier-list').waitFor({ timeout: 15000 });
    const current = (await page.locator('.master-data-tier-list .is-current').innerText()).replace(/\s+/g, ' ');
    const tiers = await page.locator('.master-data-tier-list > div').count();
    const provider = (await page.locator('.master-data-provider').innerText()).replace(/\s+/g, ' ');
    const sections = await page.locator('.master-data-section').all();
    const sectionText = {};
    for (const s of sections) { const t = (await s.innerText()).replace(/\s+/g, ' '); sectionText[t.split(' ').slice(0, 2).join(' ')] = t.slice(0, 400); }
    const scopes = await page.locator('.master-data-section', { hasText: L('Approved Scopes', '准入范围') }).locator('.ant-table-row').count();
    const disclosureSection = page.locator('.master-data-section', { hasText: L('Seller Disclosures', '卖方披露') });
    const disclosureNote = (await disclosureSection.locator('.ant-alert-info').innerText()).replace(/\s+/g, ' ');
    const disclosures = (await disclosureSection.locator('.ant-table-row').allInnerTexts()).map(r => r.replace(/\s+/g, ' '));
    const qualification = (await page.locator('.master-data-section', { hasText: L('Entity Qualification', '主体资格') }).locator('.qualification-summary').last().innerText()).replace(/\s+/g, ' ');
    const file = await shot(page, `${lang}-E2-ae001-detail`);
    if (lang === 'zh-CN') zhScans.push(await scanZh('E2 AE001 detail'));
    record('E2', 'AE001 detail: tier card, provider card, scopes, disclosures note, qualification card', tiers === 3 && current.includes('PayFac')
      && provider.includes('LP-PAYFAC-AE001') && provider.includes(L('Open Partner', '查看 Partner')) && scopes >= 1 && disclosures.length >= 1
      && disclosureNote.includes(L('exactly', '精确匹配')) && qualification.includes(L('Eligible', '合格')),
    { currentTier: current, tierCount: tiers, provider, scopes, disclosures, disclosureNote, qualification, screenshot: file });
  });

  await guarded('E3', 'acquiring qualification dialog: MERCHANT warning' + (writes ? '; PAYFAC -> MERCHANT_RESALE_APPROVED -> PAYFAC' : ' (cancelled)'), async () => {
    await goto('/master-data/operating-entities/AE001', '.master-data-tier-list');
    const before = api('state').ae001;
    const change = async () => { await page.locator('.master-data-section-head').first().getByRole('button', { name: exact(L('Change', '修改')) }).click(); await dialog().waitFor(); };
    await change();
    await dialog().locator('.master-data-tiers .ant-radio-wrapper', { hasText: L('Merchant', '商户') }).first().click();
    const warning = (await dialog().locator('.ant-alert-warning').innerText().catch(() => '')).replace(/\s+/g, ' ');
    const warnShot = await shot(page, `${lang}-E3-merchant-warning`);
    if (lang === 'zh-CN') zhScans.push(await scanZh('E3 acquiring dialog', dialog()));
    if (!writes) {
      await dialog().getByRole('button', { name: exact('取消') }).click();
      record('E3', 'acquiring qualification dialog: MERCHANT warning (cancelled)', warning.includes('不再承接 Client'), { warning, screenshot: warnShot, state: api('state').ae001 });
      return;
    }
    const steps = [];
    for (const [label, code] of [['Merchant (Resale Approved)', 'MERCHANT_RESALE_APPROVED'], ['PayFac', 'PAYFAC']]) {
      if (steps.length) await change();
      await dialog().locator('.master-data-tiers .ant-radio-wrapper').filter({ has: page.locator('strong', { hasText: exact(label) }) }).click();
      await page.waitForTimeout(400);  // Form.useWatch 在下一次渲染更新
      const warned = await dialog().locator('.ant-alert-warning').count();
      await formItem(dialog(), 'Reason').locator('textarea').fill(`Stage 5 browser acceptance (AC-12): set ${code}.`);
      await dialog().getByRole('button', { name: exact('Save') }).click();
      const message = await messageText('Acquiring qualification updated'); await settle(900);
      await page.locator('.ant-message-notice').first().waitFor({ state: 'detached', timeout: 10000 }).catch(() => {});
      const current = (await page.locator('.master-data-tier-list .is-current').innerText()).replace(/\s+/g, ' ');
      steps.push({ to: code, warningShown: warned > 0, message, current, state: api('state').ae001, screenshot: await shot(page, `${lang}-E3-after-${code.toLowerCase()}`) });
    }
    record('E3', 'acquiring qualification dialog: MERCHANT warning; PAYFAC -> MERCHANT_RESALE_APPROVED -> PAYFAC',
      /no longer accept Client orders/.test(warning) && steps[0].state.acquiringQualification === 'MERCHANT_RESALE_APPROVED' && !steps[0].warningShown
      && steps[1].state.acquiringQualification === 'PAYFAC' && steps.every(s => /Acquiring qualification updated/.test(s.message)) && before.acquiringQualification === 'PAYFAC',
    { before, warning, warningScreenshot: warnShot, steps });
  });

  await guarded('E4', 'provider card link opens the Partner detail; qualification card link pre-fills the subject', async () => {
    await goto('/master-data/operating-entities/AE001', '.master-data-provider');
    await page.locator('.master-data-provider').getByRole('button', { name: exact(L('Open Partner', '查看 Partner')) }).click();
    await page.waitForURL(new RegExp(`/master-data/local-partners/${PAYFAC}$`), { timeout: 10000 });
    const partnerUrl = new URL(page.url()).pathname;
    await goto('/master-data/operating-entities/AE001', '.qualification-summary');
    await page.locator('.master-data-section', { hasText: L('Entity Qualification', '主体资格') }).locator('.qualification-summary').last()
      .getByRole('button', { name: exact(L('Open in Qualifications', '在主体资格中查看')) }).click();
    await page.waitForURL(/\/compliance\/qualifications\?/, { timeout: 10000 }); await settle(800);
    const q = new URL(page.url()).searchParams;
    record('E4', 'provider card link opens the Partner detail; qualification card link pre-fills the subject', partnerUrl.endsWith(PAYFAC)
      && q.get('roleType') === 'OPERATING_ENTITY' && q.get('roleId') === 'AE001', { partnerUrl, qualificationLink: '?' + q.toString() });
  });
}

async function partnerPages(writes) {
  await guarded('P1', 'Local Partner list with filters', async () => {
    await goto('/master-data/local-partners', '.master-data-page .ant-table-row');
    const search = page.locator('.master-data-search');
    await pick(formItem(search, L('Roles', '角色')), L('OE Provider', 'OE 提供方'));
    await search.getByRole('button', { name: exact(L('Search', '查询')) }).click(); await settle(800);
    const url = new URL(page.url());
    const rows = (await page.locator('.ant-table-row').allInnerTexts()).map(r => r.replace(/\s+/g, ' '));
    const file = await shot(page, `${lang}-P1-partner-list-oe-provider`);
    if (lang === 'zh-CN') zhScans.push(await scanZh('P1 partner list'));
    record('P1', 'Local Partner list with filters', url.searchParams.get('role') === 'OE_PROVIDER' && rows.length === 1 && rows[0].includes('LP-PAYFAC-AE001'), { query: url.search, rows, screenshot: file });
  });

  await guarded('P2', 'PayFac LP detail: roles card, provided entities, qualification card', async () => {
    await page.locator('.ant-table-row', { hasText: 'LP-PAYFAC-AE001' }).click(); await page.waitForURL(new RegExp(`/local-partners/${PAYFAC}$`)); await settle(900);
    const roles = (await page.locator('.master-data-section').first().innerText()).replace(/\s+/g, ' ');
    const provided = (await page.locator('.master-data-section', { hasText: L('Provided Operating Entities', '提供的经营主体') }).locator('.ant-table-row').allInnerTexts()).map(r => r.replace(/\s+/g, ' '));
    const qualification = (await page.locator('.qualification-summary').innerText()).replace(/\s+/g, ' ');
    const file = await shot(page, `${lang}-P2-payfac-detail`);
    if (lang === 'zh-CN') zhScans.push(await scanZh('P2 partner detail'));
    record('P2', 'PayFac LP detail: roles card, provided entities, qualification card', roles.includes(L('OE Provider', 'OE 提供方')) && roles.includes(L('cannot be removed', '不能取消'))
      && provided.length === 1 && provided[0].includes('AE001') && qualification.includes(L('Eligible', '合格')), { roles, provided, qualification, screenshot: file });
  });

  await guarded('P3', 'roles dialog: OE_PROVIDER disabled with reason' + (writes ? '; add and remove REFERRER' : ' (cancelled)'), async () => {
    await goto(`/master-data/local-partners/${PAYFAC}`, '.master-data-section');
    const before = api('state').payfac;
    const open = async () => { await page.locator('.master-data-section-head').getByRole('button', { name: exact(L('Edit Roles', '维护角色')) }).click(); await dialog().waitFor(); };
    await open();
    const provider = dialog().locator('.ant-checkbox-wrapper', { hasText: L('OE Provider', 'OE 提供方') });
    const providerDisabled = (await provider.getAttribute('class')).includes('disabled');
    const providerChecked = await provider.locator('input').isChecked();
    const reason = (await dialog().locator('.ant-alert-info').innerText()).replace(/\s+/g, ' ');
    await provider.hover(); await page.waitForTimeout(500);
    const tooltip = (await page.locator('.ant-tooltip:visible').innerText().catch(() => '')).replace(/\s+/g, ' ');
    const dialogShot = await shot(page, `${lang}-P3-roles-dialog-locked`);
    if (lang === 'zh-CN') zhScans.push(await scanZh('P3 roles dialog', dialog()));
    if (!writes) {
      await dialog().getByRole('button', { name: exact('取消') }).click();
      record('P3', 'roles dialog: OE_PROVIDER disabled with reason (cancelled)', providerDisabled && providerChecked && reason.includes('不能取消'), { providerDisabled, providerChecked, reason, tooltip, screenshot: dialogShot });
      return;
    }
    const steps = [];
    for (const target of ['add', 'remove']) {
      if (steps.length) await open();
      await dialog().locator('.ant-checkbox-wrapper', { hasText: 'Referrer' }).click();
      await dialog().locator('textarea').fill(`Stage 5 browser acceptance (AC-12): ${target} REFERRER.`);
      await dialog().getByRole('button', { name: exact('Save') }).click();
      const message = await messageText('Partner roles updated'); await settle(900);
      await page.locator('.ant-message-notice').first().waitFor({ state: 'detached', timeout: 10000 }).catch(() => {});
      steps.push({ target, message, rolesCard: (await page.locator('.master-data-section').first().innerText()).replace(/\s+/g, ' '), state: api('state').payfac,
        screenshot: await shot(page, `${lang}-P3-after-${target}-referrer`) });
    }
    record('P3', 'roles dialog: OE_PROVIDER disabled with reason; add and remove REFERRER', providerDisabled && providerChecked && /cannot be removed/.test(reason)
      && JSON.stringify(steps[0].state.roles) === '["OE_PROVIDER","REFERRER"]' && JSON.stringify(steps[1].state.roles) === '["OE_PROVIDER"]'
      && steps.every(s => /Partner roles updated/.test(s.message)) && JSON.stringify(before.roles) === '["OE_PROVIDER"]',
    { before, providerDisabled, providerChecked, reason, tooltip, dialogScreenshot: dialogShot, steps });
  });
}

// ---------- 执行 ----------
const started = new Date().toISOString();
const initialState = api('state');
await login();
await menus();
await qualificationPage(true);
await entityPages(true);
await partnerPages(true);

// 切换简体中文（页面头部语言菜单；整页刷新后同一路由）
lang = 'zh-CN';
await page.waitForTimeout(60_000);  // 让 auth/v1/context 的每分钟限流窗口过去，再整页刷新切换语言
await guarded('L1', 'switch to Simplified Chinese from the header language menu', async () => {
  await goto('/compliance/qualifications', '.page-heading h1');
  await page.locator('button.language-switcher').click();
  await page.locator('.language-option', { hasText: '简体中文' }).click();
  await page.waitForLoadState('load'); await settle(1500);
  // 切换语言整页刷新；若 auth/v1/context 恰好被限流（429），等待后重载同一路由（语言选择已保存）
  for (let attempt = 1; !(await page.locator('.page-heading h1').first().waitFor({ state: 'attached', timeout: 15000 }).then(() => true, () => false)); attempt++) {
    if (attempt === 3) throw new Error('page did not render after the language switch');
    await page.waitForTimeout(20_000); await page.reload(); await settle(1500);
  }
  const h1 = await heading();
  const htmlLang = await page.evaluate(() => document.documentElement.lang);
  const bundles = network.filter(n => n.path.startsWith('(public bucket)') && /\/zh-CN(\/|$)/.test(n.path)).map(n => `${n.path.split('/').at(-1)} ${n.status}`);
  record('L1', 'switch to Simplified Chinese from the header language menu', h1 === '主体资格' && htmlLang === 'zh-CN', { h1, htmlLang, zhBundleRequests: bundles.length, screenshot: await shot(page, 'zh-CN-L1-after-switch') });
});
await menus();
await qualificationPage(false);
await entityPages(false);
await partnerPages(false);
await guarded('Z1', 'zh-CN: no raw i18n keys or untranslated English on the new pages', async () => {
  const rawKeys = zhScans.flatMap(s => s.rawKeys.map(k => `${s.name}: ${k}`));
  const latin = zhScans.flatMap(s => s.latin.map(w => `${s.name}: ${w}`));
  record('Z1', 'zh-CN: no raw i18n keys or untranslated English on the new pages', rawKeys.length === 0 && latin.length === 0, { scans: zhScans.length, rawKeys, latin });
});

// 恢复：PSP Client 资格的有效期起点回到阶段 5 后端联调时的 2026-10-01（页面登记以当前时间为起点）
const restore = api('restore-psp');
const finalState = api('state');
await browser.close();

const unexpected = network.filter(n => typeof n.status !== 'number' || n.status >= 400).map(n => `${n.lang} ${n.method} ${n.path} -> ${n.status}`);
const summary = { run: started, finished: new Date().toISOString(), web: WEB, environment: 'local e2e (gateway 18080 via nginx 18444, mor_platform_v2e2e block 137 active, i18n release #2)',
  initialState, finalState, restorePspValidity: restore, results, http4xx5xxAndFailed: [...new Set(unexpected)], expected4xx: expected,
  consoleErrors: [...new Set(consoleErrors)], screenshots: [...new Set(shots)] };
writeFileSync(join(HERE, 'stage5-browser-result.json'), JSON.stringify(summary, null, 2) + '\n');
console.log('PASS', results.filter(r => r.result === 'PASS').length, 'FAIL', results.filter(r => r.result === 'FAIL').length);
console.log('HTTP>=400', JSON.stringify(summary.http4xx5xxAndFailed));
console.log('CONSOLE', JSON.stringify(summary.consoleErrors));
console.log('STATE', JSON.stringify({ initialState, finalState, restore }));
