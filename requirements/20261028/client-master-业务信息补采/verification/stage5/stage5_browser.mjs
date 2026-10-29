// REQ-12 阶段 5 浏览器走查（本机 e2e）：rq12 worktree 的门户（127.0.0.1:18312 → Nginx 18443）与运营（127.0.0.1:18313 → Nginx 18444）。
// 组合：zh-CN / en × 1440 / 390。截图 screens/<语言>-<宽度>-<步骤>.png，结果 stage5-browser-result-<组合>.json。
// 写操作：E 的业务信息（移除并加回一个支付方式、改为即时交付后再改回预售 21 天；http 链接与 0 / 400 天只在本地报错、不保存）；
// D 首次运行时新建一份 AE 个人草稿（不提交）。E / D / F 都不提交。运营只读（退款政策链接点开后立即关闭新标签页）。
// 凭据：门户账号读 ~/Code-MoR-backups/rq12-stage5/accounts.json，运营读 local-environment/e2e/secrets.env；均不打印。
// 用法：node stage5_browser.mjs；RUNS='[["zh-CN",1440]]' 只跑部分组合；PARTS=client,ops 只跑部分。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const CLIENT = 'http://127.0.0.1:18312', OPS = 'http://127.0.0.1:18313';
const SHOTS = join(HERE, 'screens');
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });

const accounts = JSON.parse(readFileSync(join(homedir(), 'Code-MoR-backups/rq12-stage5/accounts.json'), 'utf8'));
const secrets = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
  .split('\n').filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const IDS = { caseB: '241533467257491456', caseC: '241533546651471872', clientA: '241533217348268033', caseV1: '240924025168089088' };
const F = (key) => '#f-' + key.replace(/[^A-Za-z0-9_-]/g, '-');
const RAWKEY = /\b(onboarding-schema|onboardingSchema|onboarding|client-review|master-data|common)\.[A-Za-z_][\w-]*(\.[\w-]+)+/g;
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
const parts = (process.env.PARTS ?? 'client,ops').split(',');

const browser = await chromium.launch({ headless: true });
for (const [locale, width] of runs) {
  const tag = `${locale}-${width}`;
  const result = { locale, width, steps: [], console: [], httpErrors: [], requests: [], bundles: 0, popups: [] };
  let current = 'start';

  async function context(origin) {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale, viewport: { width, height: width < 600 ? 844 : 1000 }, isMobile: width < 600, hasTouch: width < 600 });
    await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
    // MinIO 只放行 18311：两个 worktree 端口的语言包补 ACAO（同 REQ-05 阶段 5 默认做法）。
    await ctx.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
      const response = await route.fetch();
      await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': origin } });
    });
    const page = await ctx.newPage();
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) result.console.push({ step: current, type: m.type(), text: m.text().slice(0, 300) }); });
    page.on('pageerror', e => result.console.push({ step: current, type: 'pageerror', text: String(e).slice(0, 300) }));
    page.on('response', r => {
      const url = new URL(r.url());
      if (url.port === '19400') result.bundles++;
      if (r.status() >= 400) result.httpErrors.push({ step: current, method: r.request().method(), path: url.pathname, status: r.status() });
    });
    page.on('request', r => { const u = new URL(r.url()); if (/\/onboarding\/v1\//.test(u.pathname) && r.method() !== 'GET') result.requests.push({ step: current, method: r.method(), path: u.pathname, body: r.postData() }); });
    return { ctx, page };
  }
  const settle = async (page, ms = 900) => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); };
  const facts = page => page.evaluate((raw) => ({
    rawKeys: [...new Set(document.body.innerText.match(new RegExp(raw, 'g')) || [])].filter(k => !/example\.test/.test(k)).slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  }), RAWKEY.source);
  async function shot(page, step, check, { viewport = false } = {}) {
    current = step;
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts(page);
    await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: !viewport });
    result.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1500));
  }
  const text = async (page, selector) => (await page.locator(selector).first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 600);
  async function clientLogin(page, who) {
    current = 'login ' + who;
    await page.goto(`${CLIENT}/login?lang=${locale}`); await settle(page);
    await page.locator('#email').fill(accounts[who].email); await page.locator('#password').fill(accounts[who].password);
    await page.locator('button[type=submit]').click(); await settle(page, 1500);
  }
  const business = async page => page.evaluate(() => {
    const ids = ['business.expectedPaymentMethods', 'business.refundPolicyUrl', 'business.fulfillmentType', 'business.fulfillmentDays'];
    return Object.fromEntries(ids.map(id => { const el = document.getElementById('f-' + id.replace(/[^A-Za-z0-9_-]/g, '-'));
      const field = el?.closest('.ob-field');
      return [id.split('.')[1], el ? { label: field?.querySelector('label,legend')?.textContent, value: el.value ?? null,
        chips: [...(field?.querySelectorAll('.ob-chip-label') ?? [])].map(c => c.textContent), error: field?.querySelector('.ob-error')?.textContent ?? null } : null]; }));
  });
  async function setMethods(page, codes) {
    await page.locator(F('business.expectedPaymentMethods')).click(); await page.waitForTimeout(400);
    for (const option of await page.locator('.ob-multi-panel li[role=option]').all()) {
      const code = (await option.locator('.ob-ref-code').innerText()).trim();
      const selected = (await option.getAttribute('aria-selected')) === 'true';
      if (selected !== codes.includes(code)) { await option.click(); await page.waitForTimeout(150); }
    }
    if (width < 600) await page.locator('.ob-multi-done button').click(); else await page.keyboard.press('Escape');
    await page.waitForTimeout(1500);
  }
  const toBusiness = async page => { await page.locator(F('business.expectedPaymentMethods')).scrollIntoViewIfNeeded().catch(() => {}); };

  if (parts.includes('client')) {
    // ---------- E：v3 草稿编辑 ----------
    {
      const { ctx, page } = await context(CLIENT);
      await clientLogin(page, 'E');
      await page.goto(`${CLIENT}/onboarding/applications/${accounts.E.applicationId}/tasks/profile`); await settle(page, 1500);
      await toBusiness(page);
      await shot(page, 'E1-business', () => business(page));
      await page.locator(F('business.expectedPaymentMethods')).click(); await page.waitForTimeout(500);
      // 窄屏底部面板为 fixed 定位：拍视口截图（整页截图里面板不在可见位置）
      await shot(page, 'E2-multiselect-open', async () => ({ panel: await text(page, '.ob-multi-panel'), sheet: await page.locator('.ob-ref-panel.sheet').count() }), { viewport: width < 600 });
      if (width < 600) await page.locator('.ob-multi-done button').click(); else await page.keyboard.press('Escape');
      await page.waitForTimeout(300);
      result.chipsBefore = (await business(page)).expectedPaymentMethods?.chips;
      await page.locator('.ob-multi .ob-chip-remove').last().click(); await page.waitForTimeout(1500);
      await shot(page, 'E3-chip-removed', () => business(page));
      // 恢复为演示数据的原值 APPLE_PAY,CARD（逐项勾选 / 取消，与已选无关）
      await setMethods(page, ['APPLE_PAY', 'CARD']);
      result.chipsAfterRestore = (await business(page)).expectedPaymentMethods?.chips;
      const refund = page.locator(F('business.refundPolicyUrl'));
      await refund.fill('http://shop.example.test/refunds'); await refund.press('Tab'); await page.waitForTimeout(400);
      const days = page.locator(F('business.fulfillmentDays'));
      await days.fill('400'); await days.press('Tab'); await page.waitForTimeout(400);
      await shot(page, 'E4-https-and-400', () => business(page));
      // 带着两个本地错误点"继续"：校验拦截（不跳转、不保存），出现错误汇总（原型 B）
      await page.locator('main button.primary').last().click(); await page.waitForTimeout(1200);
      await shot(page, 'E4b-error-summary', async () => ({ url: page.url().split('/').slice(-2).join('/'), summary: await text(page, '.ob-summary') }));
      await days.fill('0'); await days.press('Tab'); await page.waitForTimeout(400);
      await shot(page, 'E5-days-0', () => business(page));
      await refund.fill(''); await refund.press('Tab');
      await days.fill('21'); await days.press('Tab'); await page.waitForTimeout(1500);
      await page.locator(F('business.fulfillmentType')).selectOption('INSTANT'); await page.waitForTimeout(1800);
      await shot(page, 'E6-instant-hides-days', () => business(page));
      await page.locator(F('business.fulfillmentType')).selectOption('PRE_ORDER'); await page.waitForTimeout(500);
      await page.locator(F('business.fulfillmentDays')).fill('21'); await page.locator(F('business.fulfillmentDays')).press('Tab'); await page.waitForTimeout(1800);
      result.endState = await business(page);
      await page.goto(`${CLIENT}/onboarding/applications/${accounts.E.applicationId}`); await settle(page, 1200);
      await shot(page, 'E7-home', () => text(page, 'main'));
      await page.goto(`${CLIENT}/onboarding/applications/${accounts.E.applicationId}/tasks/review`); await settle(page, 1500);
      await shot(page, 'E8-review', () => text(page, 'main'));
      await ctx.close();
    }
    // ---------- D：新建 v3 个人草稿（已有则打开） ----------
    {
      const { ctx, page } = await context(CLIENT);
      await clientLogin(page, 'D');
      await page.goto(`${CLIENT}/onboarding?lang=${locale}`); await settle(page, 1200);
      if (await page.locator('a.ob-row-link').count()) { await page.locator('a.ob-row-link').first().click(); await settle(page, 1500); }
      else {
        await page.goto(`${CLIENT}/onboarding/new`); await settle(page, 1200);
        await page.locator('.ob-enter .text-button').first().click(); await settle(page, 900);
        const codes = await page.evaluate(() => [...document.querySelectorAll('input[name=jurisdiction]')].map(i => i.parentElement.textContent));
        await page.locator('input[name=jurisdiction]').nth(codes.findIndex(c => /Emirates|阿联酋/.test(c))).check();
        await page.locator('input[name=client-type]').nth(1).check(); await settle(page, 900);
        if (await page.locator('#organization-type').count()) await page.locator('#organization-type').selectOption({ index: 1 });
        await shot(page, 'D0-entity', () => text(page, 'main'));
        await page.locator('button[type=submit]').click(); await settle(page, 1200);
        await page.locator('.ob-row-actions button.primary').click(); await settle(page, 2500);
      }
      result.applicationD = page.url().match(/applications\/(\d+)/)?.[1];
      await page.goto(`${CLIENT}/onboarding/applications/${result.applicationD}/tasks/profile`); await settle(page, 1500);
      await toBusiness(page);
      await shot(page, 'D1-individual-business', () => business(page));
      await ctx.close();
    }
    // ---------- F：冻结 v2 的草稿 ----------
    {
      const { ctx, page } = await context(CLIENT);
      await clientLogin(page, 'F');
      await page.goto(`${CLIENT}/onboarding/applications/${accounts.F.applicationId}/tasks/profile`); await settle(page, 1500);
      await shot(page, 'F1-v2-profile', async () => ({ business: await business(page), groups: await page.locator('.ob-group h2').allInnerTexts() }));
      await ctx.close();
    }
  }

  if (parts.includes('ops')) {
    const { ctx, page } = await context(OPS);
    current = 'ops login';
    await page.goto(`${OPS}/?lang=${locale}`);
    await page.locator('input:not([type=password])').first().fill(secrets.E2E_ADMIN_EMAIL);
    await page.locator('input[type=password]').first().fill(secrets.E2E_ADMIN_PASSWORD);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
    await settle(page);
    const businessGroup = () => page.evaluate(() => {
      const groups = [...document.querySelectorAll('.obw-values, .client-values')];
      const group = groups.find(g => g.querySelector('a.profile-link') || /Business|业务|经营/.test(g.querySelector('h3')?.textContent ?? '')) ?? null;
      if (!group) return null;
      return { title: group.querySelector('h3')?.textContent, items: [...group.querySelectorAll('.ant-descriptions-item')].map(i => (i.innerText || '').replace(/\s+/g, ' ')),
        links: [...group.querySelectorAll('a.profile-link')].map(a => ({ href: a.getAttribute('href'), target: a.getAttribute('target'), rel: a.getAttribute('rel') })) };
    });
    const review = async (caseId, step) => {
      await page.goto(`${OPS}/client-onboarding/reviews/${caseId}`); await settle(page, 1500);
      // 页签未出现（如运营入口限流）时截图留证，等待后重载一次
      if (!(await page.locator('.ant-segmented-item').first().isVisible({ timeout: 15000 }).catch(() => false))) {
        await page.screenshot({ path: join(SHOTS, `${tag}-${step}-retry.png`) });
        result.retries = [...(result.retries ?? []), { step, text: (await text(page, 'main')).slice(0, 200), httpErrors: result.httpErrors.slice(-3) }];
        await page.waitForTimeout(20000); await page.reload(); await settle(page, 1500);
      }
      await page.locator('.ant-segmented-item').first().click(); await settle(page, 800);
      const group = page.locator('.obw-values').filter({ has: page.locator('a.profile-link') }).first();
      if (await group.count()) await group.scrollIntoViewIfNeeded();
      await shot(page, step, businessGroup);
    };
    await review(IDS.caseB, 'O1-review-B');
    const link = page.locator('.obw-values a.profile-link').filter({ hasText: 'refunds' }).first();
    if (await link.count()) {
      const popupPromise = ctx.waitForEvent('page', { timeout: 8000 }).catch(() => null);
      await link.click();
      const popup = await popupPromise;
      result.popups.push({ opened: !!popup, url: popup?.url() ?? null, opener: popup ? await popup.evaluate(() => window.opener === null).catch(() => 'n/a') : null });
      await popup?.close();
    }
    await review(IDS.caseC, 'O2-review-C-v2');
    await review(IDS.caseV1, 'O3-review-v1');
    await page.goto(`${OPS}/master-data/clients/${IDS.clientA}`); await settle(page, 1500);
    await shot(page, 'O4-client-A', businessGroup);
    await ctx.close();
  }
  writeFileSync(join(HERE, `stage5-browser-result-${tag}.json`), JSON.stringify(result, null, 1));
}
await browser.close();
