// Client 门户参考数据控件冒烟（本机 e2e，https://127.0.0.1:18443）：提交错误汇总 → 定位到字段 → 币种 / 国家（搜索名称与编码、国旗占位）→
// 酋长国随地址国家切换并清空 → 选项加载失败与重试。账号与申请由 ../local-e2e/ui_provision.py 预置（scratchpad 0600），不打印口令。
// 用法：node client_refdata.mjs <en|zh-CN> <1440|390>
import { createRequire } from 'node:module';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = join(dirname(fileURLToPath(import.meta.url)), '../../verification/ui-smoke');
const REL = dirname(fileURLToPath(import.meta.url));
const WEB = 'https://localhost:28443';  // mor-test（生成自本机版本）
const STATE = '/Users/kenny/Code-MoR-backups/refdata-20261027-ui/ui-client.json';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const [lang = 'en', widthArg = '1440'] = process.argv.slice(2);
const width = Number(widthArg), zh = lang === 'zh-CN', tag = `mortest-client-${lang}-${width}`;
const acct = JSON.parse(readFileSync(STATE, 'utf8'));
const app = acct.applicationId;
mkdirSync(join(HERE, 'screens'), { recursive: true });
const LOG = join(HERE, `mortest-client-findings-${lang}-${width}.jsonl`);

execFileSync('python3', ['-I', join(REL, 'ui_provision_mortest.py'), 'reset'], { stdio: 'ignore' });

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width, height: width < 600 ? 844 : 900 }, ignoreHTTPSErrors: true, isMobile: width < 600, hasTouch: width < 600, locale: lang });
await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
const page = await ctx.newPage();
let current = 'start', shotNo = 0;
const results = [];
const note = (kind, detail) => { const f = { tag, page: current, kind, ...detail }; appendFileSync(LOG, JSON.stringify(f) + '\n'); console.log('FIND', JSON.stringify(f).slice(0, 400)); };
const ok = (name, pass, evidence) => { results.push({ name, pass }); console.log(`${pass ? 'PASS' : 'FAIL'} ${tag} ${name}${evidence === undefined ? '' : ' | ' + JSON.stringify(evidence).slice(0, 400)}`); if (!pass) note('check-failed', { name, evidence }); };
page.on('pageerror', e => note('pageerror', { text: String(e).slice(0, 300) }));
page.on('console', m => { if (m.type() === 'error') note('console-error', { text: m.text().slice(0, 300) }); });
page.on('response', r => { const u = new URL(r.url()); if (u.pathname.startsWith('/api/') && r.status() >= 500) note('http-5xx', { url: u.pathname, status: r.status(), requestId: r.headers()['x-request-id'] }); });
const settle = async (ms = 600) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
const shot = async name => { const f = `screens/${tag}-${String(++shotNo).padStart(2, '0')}-${name}.png`; await page.screenshot({ path: join(HERE, f), fullPage: width >= 600 }); return f; };
const RAWKEY = /\b(refdata|onboarding-schema|onboardingSchema|onboarding)\.[A-Za-z_][\w-]*(\.[\w-]+)+/g;
async function scan(name) {
  current = name;
  const body = await page.locator('body').innerText().catch(() => '');
  const keys = [...new Set(body.match(RAWKEY) ?? [])].filter(k => !/example\.test/.test(k));
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  const file = await shot(name);
  if (keys.length) note('raw-i18n-key', { keys, screenshot: file });
  if (overflow > 2) note('horizontal-overflow', { px: overflow, screenshot: file });
  return { body, file };
}
const fieldByLabel = re => page.locator('.ob-field, .field, [class*="field"]').filter({ has: page.locator('label', { hasText: re }) }).first();
async function refTrigger(re) {
  const label = page.locator('label', { hasText: re }).first();
  const id = await label.getAttribute('for').catch(() => null);
  return id ? page.locator(`[id="${id}"]`) : fieldByLabel(re).locator('.ob-ref-trigger');
}
async function openRef(trigger) { await trigger.scrollIntoViewIfNeeded(); await trigger.click(); await page.waitForTimeout(250); return page.locator('.ob-ref-panel').last(); }
async function optionLabels(panel, n = 50) { return (await panel.locator('[role=option] .ob-ref-label').allInnerTexts()).slice(0, n); }

// ---------- 登录 ----------
await page.goto(WEB + '/login'); await settle();
await page.locator('#email').fill(acct.email); await page.locator('#password').fill(acct.password);
await page.locator('form button[type=submit], form button.primary').first().click();
await page.waitForURL(u => !/\/login/.test(u.pathname), { timeout: 20000 }).catch(() => {});
await settle(800);
ok('登录', !/\/login/.test(new URL(page.url()).pathname), page.url());

// ---------- 1. 需要处理的任务 → 任务页错误汇总 → 定位到字段 ----------
await page.goto(`${WEB}/onboarding/applications/${app}`); await settle(1200);
const home = await scan('home');
const attention = (home.body.match(zh ? /需要处理|需处理|待处理/g : /Needs attention/g) ?? []).length;
ok('首页：资料与地址两项标"需要处理"（服务端参考数据校验）', attention >= 2, { attention });
const currencyRe = zh ? /币种/ : /currency/i, stateRe = zh ? /酋长国|省|州|行政区/ : /emirate|state|province/i;
for (const [task, re, label] of [['profile', currencyRe, '币种'], ['address', stateRe, '省州']]) {
  await page.goto(`${WEB}/onboarding/applications/${app}/tasks/${task}`); await settle(1200);
  const before = await scan(`${task}-open`);
  const invalidOnOpen = await page.locator('.ob-ref-trigger[aria-invalid="true"]').count();
  const links = await page.locator('.ob-summary a').allInnerTexts();
  ok(`${task}：打开即显示页顶错误汇总（${label}）且控件标红、说明为当前语言`, links.some(t => re.test(t)) && invalidOnOpen >= 1 && (!zh || !links.some(t => /Select a value/.test(t))), { invalidOnOpen, links: links.slice(0, 6) });
  const link = page.locator('.ob-summary a', { hasText: re }).first();
  if (await link.count()) {
    await link.click(); await page.waitForTimeout(500);
    const focused = await page.evaluate(() => ({ id: document.activeElement?.id, invalid: document.activeElement?.getAttribute('aria-invalid') }));
    await shot(`${task}-summary-focus`);
    ok(`${task}：点汇总聚焦到${label}控件且标红`, focused.invalid === 'true', focused);
  }
}
await page.goto(`${WEB}/onboarding/applications/${app}/tasks/profile`); await settle(1200);

// ---------- 2. 币种 ----------
current = 'profile';
const currency = await refTrigger(zh ? /预计.*币种|币种/ : /currency/i);
let panel = await openRef(currency);
let labels = await optionLabels(panel);
await shot('currency-open');
const expectCurrency = zh ? ['阿联酋迪拉姆', '埃及镑', '沙特里亚尔', '美元'] : ['United Arab Emirates Dirham', 'Egyptian Pound', 'Saudi Riyal', 'US Dollar'];
ok('币种选项为 4 个 ACTIVE 币种、Intl 本地化名称', labels.length === 4 && expectCurrency.every(n => labels.some(l => l.includes(n))), labels);
await panel.locator('[role=option]', { hasText: 'AED' }).first().click(); await settle(900);
const currencyText = (await currency.innerText()).replace(/\s+/g, ' ');
ok('选中后显示"本地化名称 (AED)"', /AED/.test(currencyText) && (zh ? /迪拉姆/ : /Dirham/).test(currencyText), currencyText);

// ---------- 3. 国家（成立国 / 注册国） ----------
const country = await refTrigger(zh ? /成立.*国|注册.*国|国家/ : /incorporat|registration country|country/i);
panel = await openRef(country);
const total = await panel.locator('[role=option]').count();
const placeholders = await panel.locator('.ob-ref-icon.country.placeholder').count();
ok('国家列表来自 Platform（249 项），无国旗时显示中性占位', total === 249 && placeholders === total, { total, placeholders });
const search = panel.locator('input[type=search]');
await search.fill(zh ? '阿拉伯' : 'united'); await page.waitForTimeout(300);
labels = await optionLabels(panel, 10);
await shot('country-search-name');
ok(`按名称搜索（${zh ? '阿拉伯' : 'united'}）`, labels.length >= 1 && labels.some(l => (zh ? /阿拉伯联合酋长国|阿联酋/ : /United Arab Emirates/).test(l)), labels);
await search.fill(zh ? 'saudi' : 'émirates'); await page.waitForTimeout(300);
labels = await optionLabels(panel, 10);
ok(zh ? '中文界面按英文名搜索（saudi）' : '忽略变音符号搜索（émirates）', labels.length >= 1, labels);
await search.fill('eg'); await page.waitForTimeout(300);
const codes = (await panel.locator('[role=option] .ob-ref-code').allInnerTexts()).slice(0, 5);
await shot('country-search-code');
ok('按编码搜索（eg → EG 优先）', codes[0] === 'EG', codes);
await search.fill('AE'); await page.waitForTimeout(250); await page.keyboard.press('Enter'); await settle(700);
ok('Enter 选中 AE', (zh ? /阿拉伯联合酋长国|阿联酋/ : /United Arab Emirates/).test(await country.innerText()), await country.innerText());

// ---------- 4. 地址：酋长国随国家切换 ----------
await page.goto(`${WEB}/onboarding/applications/${app}/tasks/address`); await settle(1200);
await scan('address');
const emirate = await refTrigger(zh ? /酋长国|省|州/ : /emirate|state|province/i);
const emirateInvalid = await emirate.getAttribute('aria-invalid');
ok('EG-C（与 AE 不一致）按编码显示且标红', /EG-C/.test(await emirate.innerText()) && emirateInvalid === 'true', { text: await emirate.innerText(), emirateInvalid });
panel = await openRef(emirate);
labels = await optionLabels(panel);
await shot('emirate-open');
ok('酋长国 7 项，名称取 refdata（缺翻译回退英文 name）', labels.length === 7 && (zh ? labels.some(l => /迪拜/.test(l)) : labels.some(l => /Dubai/.test(l))), labels);
await panel.locator('[role=option]', { hasText: 'AE-DU' }).first().click(); await settle(900);
ok('选中 AE-DU', /AE-DU|迪拜|Dubai/.test(await emirate.innerText()), await emirate.innerText());
const addrCountry = await refTrigger(zh ? /^国家|国家\/地区|国家或地区/ : /^country/i);
panel = await openRef(addrCountry);
await panel.locator('input[type=search]').fill('GB'); await page.waitForTimeout(250); await page.keyboard.press('Enter'); await settle(1000);
const afterSwitch = await scan('address-country-gb');
const stateInputs = await page.locator('input[name="address.stateOrProvince"]').count();
const emirateGone = (await page.locator('.ob-ref-trigger', { hasText: /AE-DU|迪拜|Dubai/ }).count()) === 0;
ok('地址国家改 GB：已选酋长国清空，省州变为文本输入', emirateGone && stateInputs >= 1, { stateInputs, emirateGone });
panel = await openRef(addrCountry);
await panel.locator('input[type=search]').fill('AE'); await page.waitForTimeout(250); await page.keyboard.press('Enter'); await settle(1000);
const emirateBack = await refTrigger(zh ? /酋长国|省|州/ : /emirate|state|province/i);
ok('改回 AE：酋长国下拉回来且为空', (await emirateBack.count()) === 1 && !/AE-|迪拜|Dubai/.test(await emirateBack.innerText()), await emirateBack.innerText().catch(() => ''));
panel = await openRef(emirateBack);
await panel.locator('[role=option]', { hasText: 'AE-DU' }).first().click(); await settle(900);

// ---------- 5. 选项加载失败与重试 ----------
await page.route('**/api/client/onboarding/v1/form-schema**', async route => {
  const response = await route.fetch(); const json = await response.json();
  delete json.countries; if (json.catalogs) { delete json.catalogs.REF_CURRENCY; delete json.catalogs.REF_COUNTRY_SUBDIVISION; }
  await route.fulfill({ response, json });
});
await page.goto(`${WEB}/onboarding/applications/${app}/tasks/profile`); await settle(1200);
const unavailable = page.locator('.ob-ref-unavailable');
const nUnavailable = await unavailable.count();
await scan('options-unavailable');
ok('BFF 未下发选项时字段内显示"暂时无法加载 · 重试"，不用内置列表', nUnavailable >= 2, { nUnavailable, text: (await unavailable.first().innerText().catch(() => '')) });
await page.unroute('**/api/client/onboarding/v1/form-schema**');
await unavailable.first().locator('button').click(); await settle(1200);
ok('点重试后选项恢复', (await page.locator('.ob-ref-unavailable').count()) === 0 && (await page.locator('.ob-ref-trigger').count()) >= 2,
   { triggers: await page.locator('.ob-ref-trigger').count() });
await scan('options-retried');

// ---------- 6. 核对页：修正后不再有参考数据错误 ----------
await page.goto(`${WEB}/onboarding/applications/${app}/tasks/review`); await settle(1200);
const reviewBody = (await scan('review-fixed')).body;
ok('核对页显示本地化币种与酋长国', (zh ? /迪拉姆/ : /Dirham/).test(reviewBody) && (zh ? /迪拜/ : /Dubai/).test(reviewBody));
await browser.close();
const failed = results.filter(r => !r.pass).length;
console.log(`SUMMARY ${tag} pass=${results.length - failed} fail=${failed}`);
process.exit(failed ? 1 : 0);
