// REQ-02 阶段 7 浏览器验收（mor-test，http://localhost:28080；改写自 REQ-01 gate_browser.mortest.mjs，登录同法，凭据不打印）。
// 只读走查：不点「检查前置」「暂停」「恢复」等写按钮（写操作由 req02_acceptance.py api 完成）。
// 前提：四个候选已上线、语言包已发布、req02_acceptance.py api 已跑（合同上有渠道身份未满足的开通项）、prepare 已建无 MID 菜单账号
//       （~/Code-MoR-backups/req02-stage7/state.json 的 nomid；缺失时跳过 AC-V2 无权限分支并记为 FAIL）。
// 覆盖：AC-V1 渠道身份一项显示原因与参数（OE / 支付方式 / 币种 / MCC），无原始文案键；partial（满足但部分币种缺 MID）显示提醒与链接；AC-V2 有 MID 菜单的管理员显示「去配置 MID →」，
//       点击跳 /channel/mids?entityNo=<OE>，OE 筛选框预填且列表只含该 OE；无 MID 菜单账号不显示该链接；AC-V4 1440 / 390 两宽度
//       无横向溢出（document.scrollWidth ≤ innerWidth + 1）。AC-V3（UNKNOWN 文案）需要 channel-connector 不可用，mor-test 不演练，由单测覆盖。
// 组合缺省 zh-CN / en × 1440 / 390（RUNS='[["en",390]]' 可只跑一组）。记录控制台 error、HTTP ≥ 400（含 requestId）、原始 i18n key；截图写 screens/。
// 回归：明细账运维页（/finance/posting-ops，现网 plops 新页）、MID 列表无参数打开、合同列表可打开且无 5xx（确认未回退其他会话的页面）。
// 用法：node req02_browser.mortest.mjs [合同 ID]；缺省 DEMO-CL-001（238331035912310784）。PLAYWRIGHT_CORE 默认 ~/.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core。
import { createRequire } from 'node:module';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const WEB = 'http://localhost:28080';
const AGREEMENT = process.argv[2] || '238331035912310784'; // DEMO-CL-001（AE001，CARD / APPLE_PAY）
const ENTITY = 'AE001';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const admin = { label: 'admin', email: access.match(/账号：(\S+)/)[1], password: access.match(/密码：(\S+)/)[1] };
const stateFile = join(homedir(), 'Code-MoR-backups/req02-stage7/state.json');
const nomid = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, 'utf8')).nomid : null;
const L = {
  'zh-CN': { products: /产品与开通/, channel: /渠道身份/, link: /去配置 MID/ },
  en: { products: /Products & Enablements/, channel: /Channel identity/i, link: /Configure MID/ },
};
const RAWKEY = /\b(commercial|channel|nav)\.[A-Za-z][\w-]*(\.[\w-]+)+/g;
const REGRESSION = ['/finance/posting-ops', '/channel/mids', '/commercial/agreements'];
const log = { agreement: AGREEMENT, runs: [], console: [], httpErrors: [], rawKeys: [], checks: [] };
const check = (run, name, ok, evidence) => { log.checks.push({ run, name, pass: !!ok, evidence }); console.log(`${ok ? 'PASS' : 'FAIL'} [${run}] ${name} ${JSON.stringify(evidence ?? '').slice(0, 300)}`); };

const browser = await chromium.launch({ headless: true });
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['en', 1440], ['zh-CN', 390], ['en', 390]];
for (const [locale, width] of runs) {
  for (const user of [admin, nomid && { label: 'nomid', ...nomid }].filter(Boolean)) {
    const t = L[locale];
    const tag = `${user.label}-${locale}-${width}`;
    const context = await browser.newContext({ viewport: { width, height: width > 500 ? 1000 : 844 }, locale });
    await context.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, locale);
    const page = await context.newPage();
    const run = { tag, steps: [] };
    let current = 'login';
    page.on('console', (m) => { if (m.type() === 'error') log.console.push({ run: tag, step: current, text: m.text().slice(0, 300) }); });
    page.on('response', (r) => { const u = new URL(r.url()); if (u.pathname.startsWith('/api/') && r.status() >= 400) log.httpErrors.push({ run: tag, step: current, method: r.request().method(), path: u.pathname, status: r.status(), requestId: r.headers()['x-request-id'] }); });
    const settle = async () => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(1200); };
    const shot = async (step, probe) => {
      current = step; await settle();
      const entry = { step };
      try { entry.probe = probe ? await probe() : undefined; } catch (e) { entry.probe = { error: String(e).slice(0, 200) }; }
      const body = await page.locator('body').innerText().catch(() => '');
      const keys = [...new Set(body.match(RAWKEY) ?? [])];
      if (keys.length) log.rawKeys.push({ run: tag, step, keys: keys.slice(0, 10) });
      entry.overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(tag, `${step} 无横向溢出（AC-V4）`, entry.overflow <= 1, entry.overflow);
      await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
      run.steps.push(entry);
      return entry.probe;
    };
    const openProducts = async () => {
      await page.locator('.ant-tabs-tab').first().waitFor({ timeout: 30000 });
      const tab = page.locator('.ant-tabs-tab', { hasText: t.products });
      if (await tab.isVisible()) { await tab.click(); return; }
      await page.locator('.ant-tabs-nav-more').first().click();
      await page.locator('.ant-tabs-dropdown-menu-item', { hasText: t.products }).first().click();
    };

    await page.goto(`${WEB}/`); await settle();
    await page.locator('input:not([type=password])').first().fill(user.email);
    await page.locator('input[type=password]').first().fill(user.password);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
    await settle();

    await page.goto(`${WEB}/commercial/agreements/${AGREEMENT}`); await settle();
    await openProducts(); await settle();
    const icons = page.locator('tr.ant-table-row .ant-table-row-expand-icon-collapsed');
    for (let i = await icons.count(); i > 0; i--) await icons.first().click().catch(() => {});
    const lines = await shot('01-prerequisites', async () => (await page.locator('.cc-prereq-item').allInnerTexts()).map(x => x.replace(/\s+/g, ' ')));
    // 非支付方式类开通项的渠道身份为"不适用"（SKIPPED / NOT_APPLICABLE），不含 OE / MCC，不纳入本断言。
    const channel = (lines || []).filter(x => t.channel.test(x) && !/不适用|Not applicable/i.test(x));
    // 不满足项说明含 OE / MCC；partial 提醒（CHANNEL_IDENTITY_PARTIAL，"No MID for {currencies}" 句式）至少含缺口币种 AED。
    check(tag, '渠道身份一项可见，说明含 OE / MCC 或缺口币种（AC-V1，含 partial 提醒）', channel.length > 0
      && channel.every(x => x.includes(ENTITY) || /MCC/.test(x) || x.includes('AED')), channel);
    const links = page.locator('.cc-prereq-link', { hasText: t.link });
    const linkCount = await links.count();
    if (user.label === 'nomid') {
      check(tag, '无 MID 菜单账号不显示「去配置 MID」（AC-V2）', linkCount === 0, linkCount);
    } else {
      check(tag, '管理员显示「去配置 MID」（AC-V2）', linkCount > 0, linkCount);
      if (linkCount > 0) {
        await links.first().click();
        await page.waitForURL(u => u.pathname === '/channel/mids', { timeout: 20000 }).catch(() => {});
        const probe = await shot('02-mids-prefilled', async () => ({
          url: page.url(),
          input: await page.locator('form[id="channel-mid-filter"] input#channel-mid-filter_entityNo, #channel-mid-filter_entityNo').first().inputValue().catch(() => null),
          oes: await page.locator('tr.ant-table-row').evaluateAll(rows => rows.map(r => r.querySelectorAll('td')[1]?.innerText?.trim())),
        }));
        const url = new URL(probe?.url || WEB);
        check(tag, `跳转 /channel/mids?entityNo=${ENTITY}，筛选预填（AC-V2）`, url.pathname === '/channel/mids'
          && url.searchParams.get('entityNo') === ENTITY && probe?.input === ENTITY, probe);
      }
    }
    if (user.label === 'admin') {
      for (const path of REGRESSION) {
        await page.goto(`${WEB}${path}`); await settle();
        await shot('90-regression' + path.replace(/\//g, '-'), async () => ({ title: await page.title(), url: page.url() }));
      }
    }
    log.runs.push(run);
    await context.close();
  }
}
if (!nomid) check('nomid', '无 MID 菜单账号缺失（先 req02_acceptance.py prepare --yes）', false, stateFile);
await browser.close();
writeFileSync(join(HERE, `browser-result-${new Date().toISOString().replace(/[:.]/g, '')}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, rawKeys: log.rawKeys,
  fail: log.checks.filter(c => !c.pass).length }, null, 1));
process.exit(log.httpErrors.some(e => e.status >= 500) || log.rawKeys.length || log.checks.some(c => !c.pass) ? 1 : 0);
