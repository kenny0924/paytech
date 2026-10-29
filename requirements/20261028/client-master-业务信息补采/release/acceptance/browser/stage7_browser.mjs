// REQ-12 阶段 7 · mor-test 浏览器走查（browser-plan.md B1—B12）：门户 https://localhost:28443、运营 http://localhost:28080。
// 普通浏览器行为：语言包按环境自身的 MinIO 地址与 CORS 加载，不补任何响应头；只忽略本机自签证书。
// 写操作（用户决定 P-2）：只用 API 验收账号 RQ12ACC 新建草稿（AE 机构，之后 AE 个人），编辑业务信息，结束时撤回；不提交、不审批。运营只读。
// 凭据：门户读 ~/Desktop/Workspace/Code-MoR-backups/rq12-20261029/acceptance-test.json，运营读 test-environment/private/access.md；均不打印。
// 用法：node stage7_browser.mjs <org|individual|ops|withdraw> ；RUNS='[["zh-CN",1440]]' 只跑部分组合。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const CLIENT = 'https://localhost:28443', OPS = 'http://localhost:28080';
const SHOTS = join(HERE, 'screens');
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });
const PHASE = process.argv[2];
const acct = JSON.parse(readFileSync(join(homedir(), 'Desktop/Workspace/Code-MoR-backups/rq12-20261029/acceptance-test.json'), 'utf8'));
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const ops = { email: access.match(/账号[:：]\s*(\S+)/)[1], password: access.match(/密码[:：]\s*(\S+)/)[1] };
const V1_CASE = '241049829650845696', LEGACY_CLIENT = '239260386824658945';
const F = (key) => '#f-' + key.replace(/[^A-Za-z0-9_-]/g, '-');
const RAWKEY = /\b(onboarding-schema|onboardingSchema|onboarding|client-review|master-data|common|workflow|commercial)\.[A-Za-z_][\w-]*(\.[\w-]+)+/g;
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
const W = { withdraw: /^(Withdraw application|撤回申请)$/, confirm: /^(Withdraw|撤回|确认撤回)$/ };

const browser = await chromium.launch({ headless: true });
const summary = { phase: PHASE, runs: [] };
for (const [locale, width] of (PHASE === 'withdraw' ? [['en', 1440]] : runs)) {
  const tag = `${PHASE}-${locale}-${width}`;
  const result = { locale, width, steps: [], console: [], httpErrors: [], requests: [], bundles: { ok: 0, failed: 0 } };
  let current = 'start';
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale, viewport: { width, height: width < 600 ? 844 : 1000 }, isMobile: width < 600, hasTouch: width < 600 });
  await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
  const page = await ctx.newPage();
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) result.console.push({ step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('pageerror', e => result.console.push({ step: current, type: 'pageerror', text: String(e).slice(0, 300) }));
  page.on('response', r => {
    const url = new URL(r.url());
    if (url.port === '29400') result.bundles[r.ok() ? 'ok' : 'failed']++;
    if (r.status() >= 400) result.httpErrors.push({ step: current, method: r.request().method(), path: url.pathname, status: r.status() });
  });
  page.on('requestfailed', r => { const u = new URL(r.url()); if (u.port === '29400') result.bundles.failed++;
    if (!/abort/i.test(r.failure()?.errorText ?? '')) result.httpErrors.push({ step: current, path: u.host + u.pathname, status: 'failed:' + r.failure()?.errorText }); });
  page.on('request', r => { const u = new URL(r.url()); if (/\/onboarding\/v1\//.test(u.pathname) && r.method() !== 'GET') result.requests.push({ step: current, method: r.method(), path: u.pathname, body: r.postData() }); });
  const settle = async (ms = 900) => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms + (PHASE === 'ops' ? 2500 : 0));
    if (PHASE === 'ops' && result.httpErrors.some(e => e.status === 429 && !e.retried)) {
      for (const e of result.httpErrors) e.retried = true;
      result.rateLimited = (result.rateLimited ?? 0) + 1; await page.waitForTimeout(30000); await page.reload();
      await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(2500); } };
  const facts = () => page.evaluate((raw) => ({
    rawKeys: [...new Set(document.body.innerText.match(new RegExp(raw, 'g')) || [])].filter(k => !/example\.test|\.(ae|sa|eg|com)$/.test(k)).slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  }), RAWKEY.source);
  async function shot(step, check, { viewport = false } = {}) {
    current = step;
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts();
    await page.screenshot({ path: join(SHOTS, `${locale}-${width}-${step}.png`), fullPage: !viewport });
    result.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1500));
  }
  const text = async (selector) => (await page.locator(selector).first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 700);
  const business = () => page.evaluate(() => {
    const ids = ['business.expectedPaymentMethods', 'business.refundPolicyUrl', 'business.fulfillmentType', 'business.fulfillmentDays'];
    return Object.fromEntries(ids.map(id => { const el = document.getElementById('f-' + id.replace(/[^A-Za-z0-9_-]/g, '-'));
      const field = el?.closest('.ob-field');
      return [id.split('.')[1], el ? { label: field?.querySelector('label,legend')?.textContent, value: el.value ?? null,
        chips: [...(field?.querySelectorAll('.ob-chip-label') ?? [])].map(c => c.textContent), error: field?.querySelector('.ob-error')?.textContent ?? null,
        options: el.tagName === 'SELECT' ? [...el.options].map(o => o.value).filter(Boolean) : undefined } : null]; }));
  });
  /** 登录；被限流时等待后重试（REQ-05 在 mor-test 遇到过）。 */
  async function login(origin, email, password, done) {
    for (let attempt = 1; attempt <= 4; attempt++) {
      current = 'login#' + attempt;
      await page.goto(origin === CLIENT ? `${CLIENT}/login?lang=${locale}` : `${OPS}/?lang=${locale}`); await settle();
      if (origin === CLIENT) { await page.locator('#email').fill(email); await page.locator('#password').fill(password); await page.locator('button[type=submit]').click(); }
      else { await page.locator('input:not([type=password])').first().fill(email); await page.locator('input[type=password]').first().fill(password);
        await page.locator('button[type=submit], form button.ant-btn-primary').first().click(); }
      await settle(2000);
      if (await done()) return;
      result.loginRetries = attempt; await page.waitForTimeout(30000);
    }
    throw new Error('login failed after retries');
  }
  async function setMethods(codes) {
    await page.locator(F('business.expectedPaymentMethods')).click(); await page.waitForTimeout(400);
    for (const option of await page.locator('.ob-multi-panel li[role=option]').all()) {
      const code = (await option.locator('.ob-ref-code').innerText()).trim();
      if (((await option.getAttribute('aria-selected')) === 'true') !== codes.includes(code)) { await option.click(); await page.waitForTimeout(150); }
    }
    if (width < 600) await page.locator('.ob-multi-done button').click(); else await page.keyboard.press('Escape');
    await page.waitForTimeout(1500);
  }
  /** 打开 RQ12ACC 的进行中申请；没有时按 clientType 新建 AE 草稿。 */
  async function openOrCreate(clientType) {
    await page.goto(`${CLIENT}/onboarding?lang=${locale}`); await settle(1200);
    const rows = page.locator('a.ob-row-link:has(.status-dot.active)');
    if (await rows.count()) { await rows.first().click(); await settle(1500); }
    else {
      await page.goto(`${CLIENT}/onboarding/new`); await settle(1200);
      await page.locator('.ob-enter .text-button').first().click(); await settle(900);
      const names = await page.evaluate(() => [...document.querySelectorAll('input[name=jurisdiction]')].map(i => i.parentElement.textContent));
      await page.locator('input[name=jurisdiction]').nth(names.findIndex(c => /Emirates|阿联酋/.test(c))).check();
      await page.locator('input[name=client-type]').nth(clientType === 'ORGANIZATION' ? 0 : 1).check(); await settle(900);
      if (await page.locator('#organization-type').count()) await page.locator('#organization-type').selectOption({ index: 1 });
      await shot('N0-new-' + clientType.toLowerCase(), () => text('main'));
      await page.locator('button[type=submit]').click(); await settle(1200);
      await page.locator('.ob-row-actions button.primary').click(); await settle(2500);
      result.created = page.url().match(/applications\/(\d+)/)?.[1];
    }
    return page.url().match(/applications\/(\d+)/)?.[1];
  }

  try {
    if (PHASE === 'org' || PHASE === 'individual' || PHASE === 'withdraw') {
      await login(CLIENT, acct.email, acct.password, async () => !/\/login/.test(page.url()));
    }
    if (PHASE === 'org') {
      const app = result.application = await openOrCreate('ORGANIZATION');
      const profile = `${CLIENT}/onboarding/applications/${app}/tasks/profile`;
      await page.goto(profile); await settle(1500);
      await page.locator(F('business.expectedPaymentMethods')).scrollIntoViewIfNeeded();
      await shot('B1-org-business', business);
      await page.locator(F('business.expectedPaymentMethods')).click(); await page.waitForTimeout(500);
      await shot('B1b-multiselect-open', async () => ({ options: await page.locator('.ob-multi-panel .ob-ref-code').allInnerTexts(), labels: await page.locator('.ob-multi-panel .ob-ref-label').allInnerTexts(),
        sheet: await page.locator('.ob-ref-panel.sheet').count() }), { viewport: width < 600 });
      if (width < 600) await page.locator('.ob-multi-done button').click(); else await page.keyboard.press('Escape');
      await page.waitForTimeout(800);
      // B2：选两项 → 移除一项 → 刷新回显
      await setMethods(['STC_PAY', 'CARD']);
      await shot('B2a-two-selected', business);
      await page.locator('.ob-multi .ob-chip-remove').first().click(); await page.waitForTimeout(1800);
      await page.reload(); await settle(1500);
      await page.locator(F('business.expectedPaymentMethods')).scrollIntoViewIfNeeded();
      await shot('B2b-after-remove-reload', business);
      // B3：预售 21 → 即时（天数隐藏）→ 预售（天数为空，服务端已清空）→ 刷新
      const type = page.locator(F('business.fulfillmentType'));
      await type.selectOption('PRE_ORDER'); await page.waitForTimeout(600);
      await page.locator(F('business.fulfillmentDays')).fill('21'); await page.locator(F('business.fulfillmentDays')).press('Tab'); await page.waitForTimeout(1800);
      await shot('B3a-pre-order-21', business);
      await type.selectOption('INSTANT'); await page.waitForTimeout(1800);
      await shot('B3b-instant-hides-days', business);
      await type.selectOption('PRE_ORDER'); await page.waitForTimeout(1500);
      await page.reload(); await settle(1500);
      await page.locator(F('business.fulfillmentType')).scrollIntoViewIfNeeded();
      await shot('B3c-pre-order-again-reload', business);
      // B4 / B5：http 链接、0 / 400 天（本地拦截、不保存）+ 继续时的错误汇总
      const refund = page.locator(F('business.refundPolicyUrl')), days = page.locator(F('business.fulfillmentDays'));
      await refund.fill('http://shop.example.test/refunds'); await refund.press('Tab');
      await days.fill('400'); await days.press('Tab'); await page.waitForTimeout(500);
      await shot('B4-https-and-400', business);
      await days.fill('0'); await days.press('Tab'); await page.waitForTimeout(400);
      await page.locator('main button.primary').last().click(); await page.waitForTimeout(1200);
      await shot('B5-days-0-summary', async () => ({ business: await business(), summary: await text('.ob-summary') }));
      await refund.fill(''); await refund.press('Tab'); await days.fill(''); await days.press('Tab'); await page.waitForTimeout(1500);
    }
    if (PHASE === 'individual') {
      const app = result.application = await openOrCreate('INDIVIDUAL');
      await page.goto(`${CLIENT}/onboarding/applications/${app}/tasks/profile`); await settle(1500);
      await page.locator(F('business.expectedPaymentMethods')).scrollIntoViewIfNeeded().catch(() => {});
      await shot('B7-individual-business', business);
    }
    if (PHASE === 'withdraw') {
      await page.goto(`${CLIENT}/onboarding?lang=${locale}`); await settle(1200);
      const rows = page.locator('a.ob-row-link:has(.status-dot.active)');
      result.withdrawn = [];
      if (await rows.count()) {
        await rows.first().click(); await settle(1500);
        const app = page.url().match(/applications\/(\d+)/)?.[1];
        await page.getByRole('button', { name: W.withdraw }).first().click(); await page.waitForTimeout(600);
        await page.getByRole('button', { name: W.confirm }).last().click(); await settle(2000);
        await shot('W-withdrawn-' + app, () => text('main'));
        result.withdrawn.push(app);
      }
    }
    if (PHASE === 'ops') {
      await login(OPS, ops.email, ops.password, async () => !/\/login/.test(page.url()) && await page.locator('input[type=password]').count() === 0);
      const businessGroup = () => page.evaluate(() => {
        const group = [...document.querySelectorAll('.obw-values, .client-values')].find(g => [...g.querySelectorAll('.ant-descriptions-item')].some(i => /expectedPaymentMethods|Payment Methods|支付方式/.test(i.innerText)));
        return group ? { title: group.querySelector('h3')?.textContent, items: [...group.querySelectorAll('.ant-descriptions-item')].map(i => (i.innerText || '').replace(/\s+/g, ' ')) } : null;
      });
      // B9：在途 v1 案件"申请资料"
      await page.goto(`${OPS}/client-onboarding/reviews/${V1_CASE}`); await settle(1500);
      if (await page.locator('.ant-segmented-item').first().isVisible({ timeout: 20000 }).catch(() => false)) { await page.locator('.ant-segmented-item').first().click(); await settle(800); }
      await shot('B9-v1-case-application', businessGroup);
      // B10：Client 详情（遗留 ACTIVE Client，四种组合同一个）
      await page.goto(`${OPS}/master-data/clients/${LEGACY_CLIENT}`); await settle(1800);
      result.clientDetail = page.url();
      await shot('B10-client-detail', businessGroup);
      // B11：入驻配置 · Schema（默认打开 ACTIVE 版本）
      await page.goto(`${OPS}/client-onboarding/config`); await settle(1800);
      await shot('B11a-config-schema', async () => ({ title: await text('.obw-card-title'), multi: await page.locator('td').filter({ hasText: /^(多选|Multi-select)$/ }).count(),
        rows: await page.locator('tr').filter({ hasText: 'expectedPaymentMethods' }).allInnerTexts() }));
      const schemaSelect = page.locator('.obw-card-title .ant-select').first();
      if (await schemaSelect.count()) { await schemaSelect.click(); await page.waitForTimeout(800);
        await shot('B11b-schema-versions', async () => page.locator('.ant-select-dropdown:visible .ant-select-item').allInnerTexts(), { viewport: true });
        await page.keyboard.press('Escape'); }
      // 回归：REQ-05 工作流页面与合同详情
      for (const [step, path] of [['R1-workflow-my-tasks', '/workflow/my-tasks'], ['R2-workflow-definitions', '/workflow/definitions'], ['R3-workflow-action-templates', '/workflow/action-templates']]) {
        await page.goto(`${OPS}${path}`); await settle(1500);
        await shot(step, () => text('main'));
      }
      await page.goto(`${OPS}/commercial/agreements`); await settle(1800);
      const agreement = page.locator('main a[href^="/commercial/agreements/"]').first();
      if (await agreement.count()) { await agreement.click(); await settle(1800); }
      else { const row = page.locator('main .ant-table-row').first(); if (await row.count()) { await row.click(); await settle(1800); } }
      result.agreementDetail = page.url();
      await shot('R4-contract-detail', () => text('main'));
    }
  } catch (e) {
    result.error = String(e).slice(0, 500);
    await page.screenshot({ path: join(SHOTS, `${locale}-${width}-${PHASE}-error.png`), fullPage: true }).catch(() => {});
    console.log('ERROR', tag, result.error);
  }
  summary.runs.push(result);
  writeFileSync(join(HERE, `result-${tag}.json`), JSON.stringify(result, null, 1));
  await ctx.close();
}
await browser.close();
