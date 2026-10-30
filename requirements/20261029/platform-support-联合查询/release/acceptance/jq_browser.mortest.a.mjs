// 联合查询 阶段 7 浏览器验收（mor-test，http://localhost:28080；改写自阶段 5 Code-MoR-jq-m/.jq-notes/stage5/stage5_browser.mjs），对照 browser-plan.md B1—B10。
// 只读：不启用模板、不拖动键类型、不新建或修改键类型、不保存模板（模板编辑器的保存 PUT 在浏览器内截获比对后中止）。
// 税务回归：只读取订单、签发并 GET 一次收据下载地址（不保存文件），另验证未登录访问 /api/operations/tax/v1/** 返回 401。
// 每种语言只登录一次（Gateway 对同账号短时间多次登录会拦截），同语言的 390 组合复用会话（sessionStorage 注入）。
// 凭据读 test-environment/private/access.md，不打印。用法：OUT=<目录> node jq_browser.mortest.mjs；RUNS='[["zh-CN",1440]]' 只跑部分组合。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = process.env.ORIGIN || 'http://localhost:28080';
const OUT = process.env.OUT || join(HERE, 'browser');
const SHOTS = join(OUT, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const ENTRIES = {
  order: { code: 'ORDER_NO', value: 'ord_3LOyZii4ct3BgT48vPZhmHbyC302a8' },
  intent: { code: 'PAYMENT_INTENT_ID', value: '239094951181373440' },
  external: { code: 'EXTERNAL_TRANSACTION_ID', value: 'T2026102218153708006502' },
  refund: { code: 'REFUND_REQUEST_NO', value: 'rfd_3L5B25y5BGmxTcJDRyZcBx8OCN9BJK' },
  journal: { code: 'JOURNAL_ENTRY_ID', value: '241801561934753792' },
};
const TEMPLATE = process.env.TEMPLATE || 'JQ_ORDER_BY_NO';
const TAX_ORDER = 'ord_3LOyZii4ct3BgT48vPZhmHbyC302a8';
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const secrets = { email: access.match(/账号：(\S+)/)[1], password: access.match(/密码：(\S+)/)[1] };
const L = {
  'zh-CN': { coverage: '能查到哪些数据？', all: /^全部 \d+ 项$/, joint: '联合查询', save: /^保存为 v\d+$/ },
  en: { coverage: 'What data can I find?', all: /^All \d+$/, joint: 'Joint Query', save: /^Save as v\d+$/ },
};
const canonical = value => JSON.stringify(value, (_, v) => v && typeof v === 'object' && !Array.isArray(v)
  ? Object.fromEntries(Object.entries(v).filter(([, x]) => x !== null && x !== undefined).sort(([a], [b]) => a.localeCompare(b))) : v);
const log = { origin: ORIGIN, runs: [], console: [], httpErrors: [], deliberate: [], logins: [], tax: {} };
const sessions = {};

const browser = await chromium.launch({ headless: true });
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
for (const [locale, width] of runs) {
  const t = L[locale]; const tag = `${locale}-${width}`; const desktop = width > 500;
  const context = await browser.newContext({ viewport: { width, height: desktop ? 1000 : 844 }, locale });
  if (sessions[locale]) await context.addInitScript(value => { try { sessionStorage.setItem('paytech.operations.session.v1', value); } catch { /* 忽略 */ } }, sessions[locale]);
  const page = await context.newPage();
  page.on('dialog', dialog => dialog.accept().catch(() => {}));
  const run = { locale, width, steps: [] };
  let current = 'login'; let deliberate = null;
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('response', r => {
    const url = new URL(r.url());
    if (r.status() >= 400 && url.pathname.startsWith('/api/')) (deliberate ? log.deliberate : log.httpErrors).push({ run: tag, step: current, why: deliberate ?? undefined, method: r.request().method(), path: url.pathname, status: r.status() });
  });
  page.on('requestfailed', r => { const url = new URL(r.url()); if (url.pathname.startsWith('/api/')) (deliberate ? log.deliberate : log.httpErrors).push({ run: tag, step: current, why: deliberate ?? undefined, method: r.method(), path: url.pathname, status: 'failed:' + r.failure()?.errorText }); });
  const settle = async (ms = 900) => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); };
  const facts = async () => page.evaluate(() => ({
    rawKeys: [...new Set(document.body.innerText.match(/\b(data-query|nav|common|tax)\.[A-Za-z]+\.[A-Za-z0-9_.]+/g) || [])].slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
  }));
  const shot = async (step, check, { wait = true } = {}) => {
    current = step; if (wait) await settle();
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts();
    await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 900));
  };
  const text = async selector => (await page.locator(selector).first().innerText({ timeout: 3000 }).catch(() => '')).replace(/\s+/g, ' ').slice(0, 900);
  const go = async path => { current = path; await page.goto(`${ORIGIN}${path}${path.includes('?') ? '&' : '?'}lang=${locale}`); await settle(); };
  const api = (path, method = 'GET', auth = true) => page.evaluate(async ([path, method, auth]) => {
    const token = JSON.parse(sessionStorage.getItem('paytech.operations.session.v1') ?? '{}').accessToken;
    const response = await fetch('/api/operations' + path, { method, headers: auth && token ? { Authorization: 'Bearer ' + token } : {} });
    return { status: response.status, body: await response.json().catch(() => null) };
  }, [path, method, auth]);
  const pickType = async code => {
    await page.locator('.jq-type').click();
    await page.locator('.jq-type-menu input').fill(code);
    await page.locator('.jq-type-item').first().click();
  };
  const runQuery = async entry => {
    await pickType(entry.code);
    await page.locator('.jq-input textarea').fill(entry.value);
    await page.locator('.jq-submit').click();
    await page.waitForTimeout(500);
    await page.waitForFunction(() => !document.querySelector('.jq-summary .ant-spin') && !document.querySelector('.jq-submit.ant-btn-dangerous'), null, { timeout: 90000 }).catch(() => {});
  };
  const overview = async () => ({
    type: await text('.jq-type'), summary: await text('.jq-summary'), issues: await text('.jq-issues'),
    lanes: desktop ? await page.locator('.jq-lane-title').allInnerTexts() : await page.locator('.jq-mobile-lane-head').allInnerTexts(),
    nodes: desktop ? await page.locator('.jq-card').evaluateAll(cards => cards.map(card => card.innerText.replace(/\s+/g, ' '))) : undefined,
    detail: desktop ? await text('.jq-detail') : undefined, formMessage: await text('.jq-form-message'),
  });

  // ---------- 登录（每种语言一次） ----------
  if (!sessions[locale]) {
    await page.goto(`${ORIGIN}/?lang=${locale}`);
    await page.locator('input:not([type=password])').first().fill(secrets.email);
    await page.locator('input[type=password]').first().fill(secrets.password);
    const login = page.waitForResponse(r => r.url().includes('/auth/v1/login'), { timeout: 20000 }).catch(() => null);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    log.logins.push({ run: tag, status: (await login)?.status() ?? null });
    await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
    await settle();
    sessions[locale] = await page.evaluate(() => sessionStorage.getItem('paytech.operations.session.v1'));
  }

  // ---------- B1 / 01 菜单、空状态与类型菜单 ----------
  await go('/data-query/joint-query');
  await page.locator('.jq-search').waitFor({ timeout: 20000 }).catch(() => {});
  await shot('B1-01a-empty', async () => ({ menu: desktop ? await page.locator('a[href="/data-query/joint-query"], a[href="/data-query/key-types"]').allInnerTexts() : undefined,
    chips: await text('.jq-chips'), empty: await text('.jq-empty') }));
  await page.locator('.jq-type').click(); await page.waitForTimeout(400);
  await shot('B6-01b-type-menu', async () => ({ menu: await text('.jq-type-menu'), items: await page.locator('.jq-type-item').count() }), { wait: false });
  await page.keyboard.press('Escape'); await page.waitForTimeout(200);

  // ---------- B6 / 08 格式不符与覆盖范围 ----------
  await pickType('REFUND_REQUEST_NO');
  await page.locator('.jq-input textarea').fill('rfd_short');
  await page.locator('.jq-submit').click(); await page.waitForTimeout(600);
  await shot('B6-08a-format-error', async () => ({ message: await text('.jq-form-message'), invalidLines: await page.locator('.jq-input-backdrop .is-invalid').count() }), { wait: false });
  await page.getByRole('button', { name: t.coverage }).click();
  await page.locator('.jq-coverage .ant-drawer-body').waitFor({ timeout: 15000 }).catch(() => {});
  await shot('B6-08b-coverage', async () => text('.jq-coverage .ant-drawer-body'));
  await page.keyboard.press('Escape'); await page.waitForTimeout(300);

  // ---------- B3 交易订单号 ----------
  await runQuery(ENTRIES.order);
  await shot('B3-02a-order', overview);
  if (desktop) {
    const journal = page.locator('.jq-card').filter({ hasText: /凭证|Journal/i }).first();
    if (await journal.count()) { await journal.click(); await page.waitForTimeout(500);
      await shot('B3-02b-journal-detail', async () => ({ detail: await text('.jq-detail'), rows: await page.locator('.jq-detail .jq-table tbody tr').count(), activeEdges: await page.locator('.jq-edge.is-active').count() }), { wait: false }); }
    const truncated = page.locator('.jq-card.is-truncated').first();
    if (await truncated.count()) { await truncated.click(); await page.waitForTimeout(400);
      const more = page.locator('.jq-detail-foot .ant-btn').first();
      if (await more.count()) { await more.click(); await settle(); }
      await shot('B3-02c-load-more', async () => ({ rows: await page.locator('.jq-detail .jq-table tbody tr').count(), foot: await text('.jq-detail-foot') })); }
    const locate = page.locator('.jq-detail .jq-locate').first();
    if (await locate.count()) { await locate.click({ force: true }); await page.waitForTimeout(500);
      await shot('B3-02d-locate', async () => ({ selected: await text('.jq-card.is-selected'), title: await text('.jq-detail-title') }), { wait: false }); }
    await page.locator('.ant-segmented-item', { hasText: t.all }).first().click().catch(() => {}); await page.waitForTimeout(500);
    await shot('B3-02e-all-items', async () => ({ sections: await page.locator('.jq-detail').count() }), { wait: false });
    // B7 维度值：逐个节点找带维度值的明细
    await page.locator('.ant-segmented-item').first().click().catch(() => {});
    const count = await page.locator('.jq-card').count();
    for (let i = 0; i < count; i++) {
      await page.locator('.jq-card').nth(i).click(); await page.waitForTimeout(250);
      if (await page.locator('.jq-detail .jq-dimension').count()) break;
    }
    const dimension = page.locator('.jq-detail .jq-dimension').first();
    if (await dimension.count()) {
      await dimension.hover(); await page.waitForTimeout(400);
      await shot('B7-07a-dimension-hover', async () => ({ values: await page.locator('.jq-detail .jq-dimension').allInnerTexts(), popover: await text('.ant-popover:visible') }), { wait: false });
      await dimension.click(); await page.waitForTimeout(500);
      await page.waitForFunction(() => !document.querySelector('.jq-summary .ant-spin'), null, { timeout: 90000 }).catch(() => {});
      await shot('B7-07b-query-this', async () => ({ ...(await overview()), input: await page.locator('.jq-input textarea').inputValue() }));
    } else run.steps.push({ step: 'B7-no-dimension-cell' });
  } else {
    await page.locator('.jq-mobile-row').first().click(); await page.waitForTimeout(800);
    await shot('B10-06a-mobile-detail', async () => text('.jq-mobile-detail .ant-drawer-body'), { wait: false });
    await page.keyboard.press('Escape'); await page.waitForTimeout(400);
    await page.locator('.jq-mobile-toggle').click(); await page.waitForTimeout(900);
    await shot('B10-06b-mobile-graph', async () => ({ cards: await page.locator('.jq-card').count(), graphWidth: await page.locator('.jq-graph').evaluate(el => el.getBoundingClientRect().width).catch(() => null) }), { wait: false });
    await page.locator('.jq-mobile-toggle').click();
  }

  // ---------- B4 / B5 其他入口 ----------
  for (const [name, entry] of [['B4-intent', ENTRIES.intent], ['B4-external', ENTRIES.external], ['B5-refund', ENTRIES.refund], ['B5-journal', ENTRIES.journal]]) {
    await runQuery(entry);
    await shot(name, overview);
  }

  // ---------- B2 键类型（只读） ----------
  await go('/data-query/key-types');
  await page.locator('.kt-row').first().waitFor({ timeout: 20000 }).catch(() => {});
  await shot('B2-04a-key-types', async () => ({ groups: await page.locator('.kt-group-title').allInnerTexts(), rows: await page.locator('.kt-row').count() }));
  await page.locator('.kt-toolbar input[type=text]').fill('ORDER'); await page.waitForTimeout(300);
  await shot('B2-04b-search', async () => ({ rows: (await page.locator('.kt-row').allInnerTexts()).map(row => row.replace(/\s+/g, ' ')) }), { wait: false });
  await page.locator('.kt-toolbar input[type=text]').fill('');
  await page.locator('.kt-row', { hasText: 'ORDER_ID' }).first().click(); await page.waitForTimeout(600);
  if (await page.locator('.kt-pattern-head').getAttribute('aria-expanded').catch(() => null) !== 'true') await page.locator('.kt-pattern-head').click().catch(() => {});
  await page.locator('.kt-pattern-test input').fill('2051880011734528801').catch(() => {}); await page.waitForTimeout(300);
  await shot('B2-04c-editor', async () => ({ head: await text('.kt-editor-head'), valueTypeDisabled: await page.locator('.kt-editor .ant-select-disabled').count(), test: await text('.kt-pattern-test') }), { wait: false });

  // ---------- B8 模板编辑器“联合查询”页签（只读；保存 PUT 截获后中止） ----------
  const list = await api(`/data-query-admin/v1/templates?keyword=${TEMPLATE}&page=1&size=10`);
  const target = list.body?.items?.find(item => item.templateCode === TEMPLATE);
  if (target) {
    await go(`/data-query/templates/${target.templateId}`);
    await page.locator('.dq-editor-tabs').waitFor({ timeout: 20000 }).catch(() => {});
    const jointTab = page.locator('.dq-editor-tabs .ant-tabs-tab', { hasText: t.joint }).first();
    const inView = await jointTab.evaluate(el => { const r = el.getBoundingClientRect(); return r.right <= document.documentElement.clientWidth && r.left >= 0; }).catch(() => false);
    await (inView ? jointTab.click() : page.locator('.dq-editor-tabs .ant-tabs-nav-more').click().then(() => page.locator('.ant-tabs-dropdown-menu-item', { hasText: t.joint }).click())).catch(() => {});
    await settle();
    await shot('B8-05a-joint-tab', async () => ({ tabViaMenu: !inView, panel: await text('.dq-joint') }));
    if (desktop) {
      const detail = (await api(`/data-query-admin/v1/templates/${target.templateId}`)).body;
      let captured = null;
      const pattern = new RegExp(`/api/operations/data-query-admin/v1/templates/${target.templateId}$`);
      await page.route(pattern, route => route.request().method() === 'PUT' ? (captured = route.request().postDataJSON(), route.abort()) : route.continue());
      await page.locator('.dq-description-input').fill(`${detail.description ?? ''} .`);
      await page.getByRole('button', { name: t.save }).click();
      deliberate = 'aborted PUT (save round-trip check, no write)';
      await page.locator('.ant-modal .ant-btn-primary').last().click();
      await page.waitForTimeout(1500); deliberate = null;
      const after = (await api(`/data-query-admin/v1/templates/${target.templateId}`)).body;
      await shot('B8-05b-save-intercepted', async () => ({ putCaptured: !!captured, jointEqual: canonical(captured?.joint) === canonical(detail.currentVersion?.joint ?? detail.joint),
        versionBefore: detail.currentVersionNo, versionAfter: after.currentVersionNo }), { wait: false });
      await page.unroute(pattern);
    }
  } else run.steps.push({ step: 'B8-template-missing', check: { template: TEMPLATE, status: list.status } });

  // ---------- B9 回归 ----------
  await go(`/finance/tax?orderNo=${encodeURIComponent(TAX_ORDER)}`);
  await page.locator('.tax-result').first().waitFor({ timeout: 20000 }).catch(() => {});
  await shot('B9-tax', async () => {
    const body = await text('.tax-result');
    return { has105: body.includes('105.00'), has115: body.includes('115.00'), hasReceipt: body.includes('SA-RCPT-2026-000001'), excerpt: body.slice(0, 700) };
  });
  if (desktop && locale === 'zh-CN') {
    const order = await api(`/tax/v1/orders/${encodeURIComponent(TAX_ORDER)}`);
    const receipt = order.body?.receipt;
    log.tax.order = { status: order.status, receipt: receipt ? { documentNumber: receipt.documentNumber, downloadable: receipt.downloadable } : null };
    if (receipt?.documentId) {
      const grant = await api(`/tax/v1/orders/${encodeURIComponent(TAX_ORDER)}/documents/${receipt.documentId}/download`, 'POST');
      log.tax.grant = { status: grant.status, hasUrl: !!grant.body?.url };
      if (grant.body?.url) log.tax.fetch = await page.evaluate(async url => {
        const response = await fetch(url, { credentials: 'omit' });
        const bytes = (await response.arrayBuffer()).byteLength;
        return { status: response.status, contentType: response.headers.get('content-type'), bytes };
      }, grant.body.url).catch(e => ({ error: String(e).slice(0, 200) }));
    }
    deliberate = 'unauthenticated tax call (expect 401)';
    log.tax.unauthenticated = (await api(`/tax/v1/orders/${encodeURIComponent(TAX_ORDER)}`, 'GET', false)).status;
    deliberate = null;
  }
  for (const [name, path] of [['B9-underwriting', '/risk/underwriting'], ['B9-query-templates', '/data-query/templates'], ['B9-workflow', '/workflow/instances'], ['B9-recon', '/finance/reconciliation/summary']]) {
    await go(path);
    await shot(name, async () => ({ rows: await page.locator('.ant-table-row').count(), empty: await page.locator('.ant-empty').count() }));
  }

  log.runs.push(run);
  await context.close();
}
await browser.close();
writeFileSync(join(OUT, `browser-result${process.env.RUNS ? '-' + log.runs.map(r => r.locale + '-' + r.width).join('_') : ''}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, deliberate: log.deliberate, logins: log.logins, tax: log.tax }, null, 1));
