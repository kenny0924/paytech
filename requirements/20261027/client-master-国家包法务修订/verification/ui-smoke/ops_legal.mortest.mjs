// 运营控制台法务修订冒烟（mor-test，http://localhost:28080；改写自本机版 ops_legal.mjs，凭据取 test-environment/private/access.md，不打印）：入驻配置 AE / SA / EG 规则与 Schema 版本、案件详情声明区块（新案件与存量案件）。
// 管理员凭据取 local-environment/e2e/secrets.env，不打印。用法：node ops_legal.mjs <en|zh-CN> <1440|390> <newCaseId|-> [oldCaseId|-]
import { createRequire } from 'node:module';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = 'http://localhost:28080';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const [lang = 'en', widthArg = '1440', newCase = '-', oldCase = '-'] = process.argv.slice(2);
const width = Number(widthArg), zh = lang === 'zh-CN', tag = `ops-${lang}-${width}`;
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const env = { E2E_ADMIN_EMAIL: access.match(/账号：(\S+)/)[1], E2E_ADMIN_PASSWORD: access.match(/密码：(\S+)/)[1] };
mkdirSync(join(HERE, 'ops-screens'), { recursive: true });
const LOG = join(HERE, `ops-findings-${lang}-${width}.jsonl`);

const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ viewport: { width, height: 900 }, ignoreHTTPSErrors: true, locale: lang });
await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
const page = await ctx.newPage();
let current = 'login', shotNo = 0; const results = [];
const note = (kind, detail) => { const f = { tag, page: current, kind, ...detail }; appendFileSync(LOG, JSON.stringify(f) + '\n'); console.log('FIND', JSON.stringify(f).slice(0, 400)); };
const ok = (name, pass, evidence) => { results.push({ name, pass }); console.log(`${pass ? 'PASS' : 'FAIL'} ${tag} ${name}${evidence === undefined ? '' : ' | ' + JSON.stringify(evidence).slice(0, 500)}`); if (!pass) note('check-failed', { name, evidence }); };
page.on('pageerror', e => note('pageerror', { text: String(e).slice(0, 300) }));
page.on('console', m => { if (m.type() === 'error') note('console-error', { text: m.text().slice(0, 300) }); });
page.on('response', r => { const u = new URL(r.url()); if (u.pathname.startsWith('/api/') && r.status() >= 500) note('http-5xx', { url: u.pathname, status: r.status() }); });
const settle = async (ms = 800) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
const shot = async name => { const f = `ops-screens/${tag}-${name}.png`; await page.screenshot({ path: join(HERE, f), fullPage: true }); console.log('SHOT', f); return f; };
const RAWKEY = /\b(client-review|onboarding-schema|nav|common)\.[A-Za-z][\w-]*(\.[\w-]+)+/g;
async function scan(name, root = page.locator('body')) {
  current = name;
  const body = await root.innerText().catch(() => '');
  const keys = [...new Set(body.match(RAWKEY) ?? [])];
  const file = await shot(name);
  if (keys.length) note('raw-i18n-key', { keys: keys.slice(0, 10), screenshot: file });
  ok(`${name}: no raw i18n keys`, keys.length === 0, keys.slice(0, 5));
  // 布尔值作为独立单元格 / 取值出现才算违规；声明正文里的 "true and accurate" 是文案。
  const bool = /(^|\t|\n)\s*(true|false)\s*(\t|\n|$)/i;
  ok(`${name}: no true/false values`, !bool.test(body), (body.match(/.{0,40}(^|\t|\n)\s*(true|false)\s*(\t|\n|$).{0,40}/i) ?? [])[0]);
  return body;
}
const has = (body, re) => re.test(body);

// ---------- 登录 ----------
await page.goto(WEB + '/'); await settle();
await page.getByLabel(/Email \/ initial account|Email \/ 初始化账号|邮箱|账号/).first().fill(env.E2E_ADMIN_EMAIL);
await page.getByLabel(/^(Password|密码)$/).fill(env.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^(Sign In|登\s*录)$/ }).click();
await page.getByText(/Sign Out|退出/).first().waitFor({ timeout: 20000 }).catch(() => {});
await settle();

// ---------- 1. 入驻配置 ----------
const schemaResponses = {};
page.on('response', async r => { const u = new URL(r.url()); const m = u.pathname.endsWith('/onboarding-schemas') && u.searchParams.get('jurisdiction');
  if (m && r.status() === 200) schemaResponses[m] = await r.json().catch(() => null); });
await page.goto(WEB + '/client-onboarding/config'); await settle(2000);
const RULES = {
  AE: { rule: zh ? /文档版本/ : /Document version/, scope: zh ? /声明 · PLATFORM_TERMS_ACCEPTANCE/ : /Declaration · PLATFORM_TERMS_ACCEPTANCE/ },
  SA: { rule: zh ? /日期不得早于今天/ : /Date must not be in the past/, scope: zh ? /属性 · SA_FREELANCE_DOCUMENT_EXPIRY_DATE/ : /Attribute · SA_FREELANCE_DOCUMENT_EXPIRY_DATE/ },
  EG: { rule: zh ? /文档版本/ : /Document version/, scope: zh ? /声明 · PRIVACY_NOTICE_ACKNOWLEDGED/ : /Declaration · PRIVACY_NOTICE_ACKNOWLEDGED/ },
};
for (const code of ['AE', 'SA', 'EG']) {
  await page.locator('.obc-item', { hasText: code }).first().click(); await settle(1500);
  const card = page.locator('.obw-main .obw-card').first();
  const body = await scan(`config-${code}`, card);
  ok(`config ${code}: comparison row labelled`, has(body, zh ? /受益所有人门槛比较方式[\s\S]*达到门槛（或以上）/ : /Beneficial owner threshold comparison[\s\S]*At or above threshold/), body.slice(0, 300));
  ok(`config ${code}: document version rows`, has(body, RULES.AE.rule) && has(body, zh ? /声明 · / : /Declaration · /) && /2026-10-draft/.test(body));
  ok(`config ${code}: ${RULES[code].scope}`, has(body, RULES[code].rule) && has(body, RULES[code].scope));
  const rows = (await card.locator('tr').allInnerTexts()).map(r => r.replace(/\s+/g, ' ').trim());
  const FLAGS = /\b(POSTAL_CODE_REQUIRED|CONTROL_PERSON_REQUIRED|REQUIRES_MANAGER|REQUIRES_DIRECTORS|EXPIRY_REQUIRED|VAULT_REQUIRED|DATE_NOT_PAST)\b/;
  const flagRows = rows.filter(r => FLAGS.test(r)), badFlags = flagRows.filter(r => !(zh ? / (是|否)$/ : / (Yes|No)$/).test(r));
  ok(`config ${code}: ${flagRows.length} flag rules as words`, flagRows.length > 0 && badFlags.length === 0, badFlags.slice(0, 3));
  const numeric = rows.filter(r => /\b(MIN_AGE|UBO_THRESHOLD_PCT|MIN_SIGNATORIES)\b/.test(r));
  ok(`config ${code}: numeric thresholds stay numbers`, numeric.length > 0 && numeric.every(r => / \d+$/.test(r)), numeric.slice(0, 2));
  const schemas = schemaResponses[code]?.items ?? schemaResponses[code] ?? [];
  const v2 = schemas.filter(s => s.versionNo === 2 || s.versionNo === '2'), v1 = schemas.filter(s => s.versionNo === 1 || s.versionNo === '1');
  ok(`config ${code}: schemas v2 ACTIVE / v1 RETIRED`, v2.length > 0 && v2.every(s => s.status === 'ACTIVE')
    && v1.filter(s => !(code === 'SA' && s.organizationTypeCode === 'FREELANCER')).every(s => s.status === 'RETIRED'),
    schemas.map(s => `${s.partyType}/${s.organizationTypeCode ?? '*'}/v${s.versionNo}/${s.status}`));
  const select = page.locator('.obw-card-title .ant-select').first();
  if (await select.count()) {
    await select.click(); await page.waitForTimeout(500);
    const options = await page.locator('.ant-select-dropdown:visible .ant-select-item-option').allInnerTexts();
    await shot(`config-${code}-schema-list`);
    ok(`config ${code}: schema list shows v2 ${zh ? '启用' : 'Active'} and v1 ${zh ? '已退役' : 'Retired'}`,
      options.some(o => /v2/.test(o)) && options.some(o => /v1/.test(o) && (zh ? /退役|停用|已下线/ : /Retired/).test(o)), options);
    await page.keyboard.press('Escape'); await page.waitForTimeout(300);
  }
}

// ---------- 2 / 3. 案件详情声明 ----------
async function caseDetail(caseId, label, expectNew) {
  if (caseId === '-') return;
  let app = null;
  const handler = async r => { const u = new URL(r.url()); if (u.pathname.endsWith(`/cases/${caseId}`) && r.status() === 200) app = (await r.json().catch(() => null))?.application; };
  page.on('response', handler);
  await page.goto(`${WEB}/client-onboarding/reviews/${caseId}`); await settle(2500);
  page.off('response', handler);
  await page.locator('.ant-segmented-item', { hasText: zh ? '申请资料' : 'Application' }).first().click(); await settle(1000);
  const card = page.locator('.obw-main .obw-card').first();
  const body = await scan(`case-${label}`, card);
  const decl = app?.declarations ?? [];
  ok(`${label}: declarations section`, has(body, zh ? /声明/ : /Declarations/) && decl.length > 0, decl.map(d => `${d.declarationType}=${d.declaredValue}/${d.termsVersion}/${d.current}/${d.declaredByAccountId}`));
  ok(`${label}: answers are words`, has(body, zh ? /\t(是|否)\t/ : /\t(Yes|No)\t/));
  for (const d of decl) {
    ok(`${label}: ${d.declarationType} code shown`, body.includes(d.declarationType));
    if (d.declaredByAccountId) ok(`${label}: ${d.declarationType} declared-by ${d.declaredByAccountId}`, body.includes(d.declaredByAccountId));
  }
  if (expectNew) {
    ok(`${label}: terms version 2026-10-draft`, body.includes('2026-10-draft'));
    ok(`${label}: status matches submitted data`, has(body, zh ? /针对提交版本/ : /Matches submitted data/));
    ok(`${label}: new declaration labels`, has(body, zh ? /我接受平台使用条款/ : /Platform Terms of Use|platform terms of use/));
    ok(`${label}: new field labels (ERN / authority name)`, has(body, zh ? /经济登记编号（ERN）|登记机关名称|发照机关名称/ : /Economic Registration Number \(ERN\)|Registration Authority Name|Licensing Authority Name/),
      Object.keys(app?.values?.attributes ?? {}));
  } else {
    ok(`${label}: legacy declaration shown with label`, has(body, zh ? /我接受平台条款与隐私告知|我确认所提供的信息真实/ : /I accept the platform terms and privacy notice|information provided is true/));
  }
  // 声明区块单独截图
  const head = page.locator('h3.obw-subhead', { hasText: zh ? /^声明$/ : /^Declarations$/ });
  if (await head.count()) { await head.scrollIntoViewIfNeeded(); await page.screenshot({ path: join(HERE, `ops-screens/${tag}-case-${label}-declarations.png`) }); }
  if (width < 600) {
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    ok(`${label}: no horizontal page scroll at ${width}`, overflow <= 1, overflow);
  }
}
await caseDetail(newCase, 'new', true);
await caseDetail(oldCase, 'legacy', false);

await browser.close();
const failed = results.filter(r => !r.pass);
console.log(`SUMMARY ${tag} pass=${results.length - failed.length} fail=${failed.length}`);
