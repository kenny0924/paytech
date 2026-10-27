// 运营控制台参考数据冒烟（本机 e2e，https://127.0.0.1:18444，1440px）：参考数据页 4 个页签（搜索 / 状态筛选 / 空状态 / 加载失败重试）、
// O03 分类（两级行业、建议 MCC、MCC 搜索改选、服务端 422 挂到控件）、入驻配置目录页的 Platform 参考数据标记、币种页（块 125 已注册时）。
// 管理员凭据取 local-environment/e2e/secrets.env，不打印。用法：node ops_refdata.mjs <en|zh-CN> <caseId> [--currency]
import { createRequire } from 'node:module';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = 'https://127.0.0.1:18444';
const MYSQL = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/8198caa5-69c6-4d42-8400-62a77c6b6af6/scratchpad/refdata-e2e/my.sh';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const [lang = 'en', caseId] = process.argv.slice(2).filter(a => !a.startsWith('--'));
const CURRENCY = process.argv.includes('--currency');
const zh = lang === 'zh-CN', tag = `ops-${lang}`;
const env = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8').split('\n')
  .filter(l => l.includes('=') && !l.startsWith('#')).map(l => { const i = l.indexOf('='); return [l.slice(0, i).trim(), l.slice(i + 1).trim().replace(/^"|"$/g, '')]; }));
mkdirSync(join(HERE, 'screens'), { recursive: true });
const LOG = join(HERE, `ops-findings-${lang}.jsonl`);
const sql = statement => execFileSync(MYSQL, ['mor_platform_v2e2e', '-N', '-B'], { input: statement }).toString().trim();

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true, locale: lang });
await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
const page = await ctx.newPage();
let current = 'login', shotNo = 0; const results = [];
const note = (kind, detail) => { const f = { tag, page: current, kind, ...detail }; appendFileSync(LOG, JSON.stringify(f) + '\n'); console.log('FIND', JSON.stringify(f).slice(0, 400)); };
const ok = (name, pass, evidence) => { results.push({ name, pass }); console.log(`${pass ? 'PASS' : 'FAIL'} ${tag} ${name}${evidence === undefined ? '' : ' | ' + JSON.stringify(evidence).slice(0, 400)}`); if (!pass) note('check-failed', { name, evidence }); };
page.on('pageerror', e => note('pageerror', { text: String(e).slice(0, 300) }));
page.on('console', m => { if (m.type() === 'error' && !/status of (422|503)/.test(m.text())) note('console-error', { text: m.text().slice(0, 300) }); });
page.on('response', r => { const u = new URL(r.url()); if (u.pathname.startsWith('/api/') && r.status() >= 500 && !page.__expect5xx) note('http-5xx', { url: u.pathname, status: r.status() }); });
const settle = async (ms = 700) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
const shot = async name => { const f = `screens/${tag}-${String(++shotNo).padStart(2, '0')}-${name}.png`; await page.screenshot({ path: join(HERE, f), fullPage: true }); return f; };
const RAWKEY = /\b(refdata|reference-data|client-review|currency|onboarding-config|onboarding-schema|nav)\.[A-Za-z][\w-]*(\.[\w-]+)+/g;
async function scan(name) {
  current = name;
  const body = await page.locator('body').innerText().catch(() => '');
  const keys = [...new Set(body.match(RAWKEY) ?? [])];
  const file = await shot(name);
  if (keys.length) note('raw-i18n-key', { keys: keys.slice(0, 10), screenshot: file });
  return body;
}
async function pick(select, text) {
  await select.locator('.ant-select-selector, .ant-select-content').first().click(); await page.waitForTimeout(300);
  const search = select.locator('input');
  if (await search.count() && await search.first().isEditable().catch(() => false)) { await search.first().fill(text); await page.waitForTimeout(300); }
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: text }).first().click(); await page.waitForTimeout(400);
}
const tab = name => page.getByRole('tab', { name });
// 元素是否完整落在容器内（列被截断 / 标签溢出时为 false）
async function inside(el, container, slack = 1) {
  const a = await el.boundingBox().catch(() => null), b = await container.boundingBox().catch(() => null);
  return !!(a && b && a.x >= b.x - slack && a.x + a.width <= b.x + b.width + slack);
}

// ---------- 登录 ----------
await page.goto(WEB + '/'); await settle();
await page.getByLabel(/Email \/ initial account|Email \/ 初始化账号|邮箱|账号/).first().fill(env.E2E_ADMIN_EMAIL);
await page.getByLabel(/^(Password|密码)$/).fill(env.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^(Sign In|登\s*录)$/ }).click();
await page.getByText(/Sign Out|退出/).first().waitFor({ timeout: 20000 });
ok('登录', true);

// ---------- 参考数据页 ----------
await page.goto(WEB + '/reference-data'); await settle(2500);
let body = await scan('refdata-countries');
const nav = await page.locator('nav, aside, .ant-menu').first().innerText().catch(() => '');
ok('菜单"平台配置 → 参考数据"可见、页面只读标记', (zh ? /参考数据/ : /Reference data/i).test(nav) && (zh ? /只读/ : /Read-only|read only/i).test(body));
const tabTexts = await page.getByRole('tab').allInnerTexts();
ok('4 个页签带条数（国家 249、行政区 47、行业 42、MCC 298）', ['249', '47', '42', '298'].every(n => tabTexts.some(t => t.includes(n))), tabTexts);
{ const panel = page.locator('.refdata-split .dq-panel:visible').first();
  const head = panel.locator('th', { hasText: zh ? '状态' : 'Status' }).first();
  ok('F-3 国家表"状态"列完整显示在面板内（1440）', await inside(head, panel), await head.innerText().catch(() => '')); }
ok('国家：本地化名称（Intl）+ 编码 / 三位码 / 数字码 / 区号 / 法定货币', (zh ? /阿富汗/ : /Afghanistan/).test(body) && /AFG/.test(body) && /004/.test(body) && /AFN/.test(body));
await page.locator('.refdata-search:visible input').first().fill(zh ? '沙特' : 'saudi'); await settle(400);
body = await scan('refdata-countries-search');
ok('国家搜索（本地化名）', /SAU/.test(body) && !/ARE/.test(body));
await page.locator('.refdata-search:visible input').first().fill('zzzz'); await settle(400);
body = await scan('refdata-countries-empty');
ok('无结果时有空状态', (zh ? /暂无|没有|无匹配|没有符合/ : /Nothing matches|No (data|match|result)/i).test(body));
await page.locator('.refdata-search:visible input').first().fill('');
await tab(zh ? /行政区/ : /Subdivision/i).click(); await settle(800);
body = await scan('refdata-subdivisions');
ok('行政区：按国家条、refdata 名称', (zh ? /迪拜/ : /Dubai/).test(body) && /AE-DU/.test(body));
await tab(zh ? /行业/ : /Industr/i).click(); await settle(800);
body = await scan('refdata-industries');
{ const cell = page.locator('td', { hasText: zh ? '服装与配饰' : 'Apparel & accessories' }).first();
  const overflow = await cell.evaluate(e => [...e.querySelectorAll('*')].concat([e]).some(n => n.scrollWidth > n.clientWidth + 1)).catch(() => true);
  ok('F-4 行业名称完整显示、不截断', await cell.count() === 1 && !overflow, await cell.innerText().catch(() => '')); }
ok('行业树：大类 + 细类 + 建议 MCC', (zh ? /零售/ : /Retail/).test(body) && /RETAIL_APPAREL|5651/.test(body));
await tab(/MCC/).click(); await settle(1500);
await page.locator('.refdata-search:visible input').first().fill(zh ? 'grocery' : 'grocery'); await settle(400);
body = await scan('refdata-mccs-search');
ok('MCC 搜索名称（grocery → 5411）', /5411/.test(body));
await page.locator('.refdata-search:visible input').first().fill('');
const statusSelect = page.locator('.refdata-filter:visible').last();
await pick(statusSelect, zh ? '停用' : 'Inactive');
body = await scan('refdata-mccs-inactive');
ok('MCC 状态筛选"停用" → 空状态', !/5411/.test(body));
// 加载失败与重试
await page.route('**/api/operations/reference-data-admin/v1/mccs**', route => route.fulfill({ status: 503, contentType: 'application/problem+json',
  body: JSON.stringify({ type: 'about:blank', title: 'Service Unavailable', status: 503, code: 'DEPENDENCY_UNAVAILABLE', detail: 'x', requestId: 'smoke' }) }));
page.__expect5xx = true;
await page.goto(WEB + '/reference-data?tab=mccs'); await settle(2000);
body = await scan('refdata-mccs-failure');
const retry = page.getByRole('button', { name: zh ? /重\s*试/ : /Retry/i }).first();
ok('MCC 加载失败有明确提示与重试', await retry.count() > 0, body.slice(0, 200));
await page.unroute('**/api/operations/reference-data-admin/v1/mccs**');
if (await retry.count()) { await retry.click(); await settle(2000); }
page.__expect5xx = false;
body = await scan('refdata-mccs-retried');
ok('重试后 MCC 列表恢复', /0742|5411/.test(body));

// ---------- 分类 ----------
if (caseId) {
  await page.goto(`${WEB}/client-onboarding/reviews/${caseId}`); await settle(2500);
  const seg = page.locator('label.ant-segmented-item', { hasText: zh ? '行业与 MCC' : 'Industry & MCC' }).first();
  for (let i = 0; i < 8 && !(await seg.getAttribute('class') ?? '').includes('selected'); i++) { await seg.click(); await page.waitForTimeout(1000); }
  await settle(800);
  const card = page.locator('.obc-classification');
  await card.scrollIntoViewIfNeeded().catch(() => {});
  body = await scan('classification-open');
  ok('分类卡片：已保存的细类显示名称与 MCC', await card.count() === 1 && /5699/.test(body), body.match(/RETAIL[^\n]*/)?.[0]);
  const row = card.locator('.obc-row').first();
  await pick(row.locator('.obc-category'), zh ? '零售' : 'Retail');
  await pick(row.locator('.obc-subcategory'), 'RETAIL_HOME');
  body = await card.innerText();
  await shot('classification-suggested');
  ok('选细类后带出建议 MCC 5712 并标"建议"', /5712/.test(body) && (zh ? /建议/ : /Suggested/i).test(body), body.slice(0, 300));
  { const tag = row.locator('.obc-row-tail .ant-tag').first();
    ok('F-6 "建议"标签不溢出分类行', await inside(tag, row), await tag.innerText().catch(() => ''));
    const basis = await card.locator('.obc-basis').innerText().catch(() => '');
    ok('F-6 细类说明按界面语言显示（refdata.industry.<CODE>.description）', zh ? /家具、家居陈设/.test(basis) : /Furniture, home furnishings/.test(basis), basis.slice(0, 160)); }
  await row.locator('.obc-mcc-field').click(); await page.waitForTimeout(300);
  await page.locator('.obc-mcc-popover input').fill('grocery'); await page.waitForTimeout(1200);
  await shot('classification-mcc-search');
  const item = page.locator('.obc-mcc-item', { hasText: '5411' }).first();
  ok('MCC 搜索（名称 grocery）出现 5411', await item.count() === 1);
  await item.click(); await page.waitForTimeout(400);
  body = await card.innerText();
  await shot('classification-mcc-changed');
  ok('改选后标"已改 · 建议 5712"', /5411/.test(body) && (zh ? /已改/ : /Changed/i).test(body), body.slice(0, 300));
  // 服务端 422：页面不知道 5411 被临时停用
  sql("UPDATE t_ref_mcc SET status='INACTIVE' WHERE mcc_code='5411'");
  try {
    await card.getByRole('button', { name: zh ? /保存分类/ : /Save classification/i }).click(); await settle(1500);
    body = await card.innerText();
    await shot('classification-save-422');
    const mccError = await row.locator('.obc-mcc-field.is-error').count();
    ok('保存被拒（MCC 停用）：错误挂在该行 MCC 控件、页脚显示保存未成功', mccError === 1 && (zh ? /保存未成功|未保存/ : /not saved|didn.t save|failed/i).test(body), body.slice(0, 400));
  } finally { sql("UPDATE t_ref_mcc SET status='ACTIVE' WHERE mcc_code='5411'"); }
  await card.getByRole('button', { name: zh ? /放弃/ : /Discard/i }).click().catch(() => {}); await settle(600);
}

// ---------- 入驻配置：目录来源 ----------
await page.goto(WEB + '/client-onboarding/config'); await settle(2000);
const catalogs = page.locator('label.ant-segmented-item', { hasText: zh ? /^目录$/ : /^Catalogs$/ }).first();
for (let i = 0; i < 8 && await catalogs.count() && !(await catalogs.getAttribute('class') ?? '').includes('selected'); i++) { await catalogs.click(); await page.waitForTimeout(1000); }
await settle(800);
body = await scan('onboarding-config-catalogs');
ok('入驻配置目录：REF_CURRENCY 标"Platform 参考数据"', /REF_CURRENCY/.test(body) && (zh ? /Platform 参考数据|参考数据/ : /Platform reference data|reference data/i).test(body));
ok('F-5 地址格式组件引用的 REF_COUNTRY_SUBDIVISION 列入目录清单', /REF_COUNTRY_SUBDIVISION/.test(body));
{ const subRow = page.locator('tr:visible', { hasText: 'REF_COUNTRY_SUBDIVISION' }).first();
  if (await subRow.count()) { await subRow.click(); await settle(1200); body = await scan('onboarding-config-subdivision-preview');
    ok('F-5 选中后右侧预览本辖区行政区', /AE-DU|迪拜|Dubai/.test(body)); }
  const tagEl = page.locator('tr:visible', { hasText: 'REF_CURRENCY' }).locator('.ant-tag').first(); const table = page.locator('tr:visible', { hasText: 'REF_CURRENCY' }).first();
  ok('F-5 "Platform 参考数据"标签不被列边缘截断', await inside(tagEl, table)); }

// ---------- 币种（块 125 已注册时） ----------
if (CURRENCY) {
  await page.goto(WEB + '/currency-specs'); await settle(2000);
  body = await scan('currency-list');
  ok('币种列表：数字码、名称（Intl）、符号列', /840/.test(body) && (zh ? /美元/ : /US Dollar/).test(body));
  { const head = page.locator('th', { hasText: zh ? '版本' : 'Version' }).first(); const panel = page.locator('table').first();
    ok('F-7 币种表"版本"列完整显示', await inside(head, page.locator('.ant-table-wrapper, .table-panel, section').filter({ has: head }).first()), await head.innerText().catch(() => '')); }
  await page.getByText('XTS').first().click(); await settle(800);
  body = await scan('currency-draft');
  ok('DRAFT 币种 XTS 编辑面板可改全部属性', await page.locator('.ant-drawer input:not([disabled]), .currency-panel input:not([disabled]), aside input:not([disabled])').count() >= 3);
  await page.getByText('USD').first().click(); await settle(800);
  body = await scan('currency-active');
  const change = page.getByRole('button', { name: zh ? /更换图标/ : /Change icon/i }).first();
  ok('ACTIVE 币种只读（锁标）并提供"更换图标"', await change.count() === 1);
  if (await change.count()) { await change.click(); await settle(800); await scan('currency-change-icon-modal'); await page.keyboard.press('Escape'); }
}

await browser.close();
const failed = results.filter(r => !r.pass).length;
console.log(`SUMMARY ${tag} pass=${results.length - failed} fail=${failed}`);
process.exit(failed ? 1 : 0);
