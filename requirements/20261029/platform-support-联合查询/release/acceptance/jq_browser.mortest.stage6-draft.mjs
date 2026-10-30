// 联合查询 阶段 7 浏览器验收（mor-test，http://localhost:28080；改写自阶段 5 .jq-notes/stage5/stage5_browser.mjs）。
// 默认只读：不启用任何模板、不拖动键类型、模板保存只截获 PUT 请求体后在浏览器内中止（不产生新版本）。
// 每种语言只登录一次（REQ-05 阶段 7 同一账号短时间多次登录被 Gateway 拦住），同一会话内切换 1440 / 390。
// 入口值由阶段 7 演示数据决定：ENTRY='{"refund":"…","attempt":"…","clientOrder":"…"}'。凭据读 test-environment/private/access.md，不打印。
// 用法：ENTRY='…' node jq_browser.mortest.mjs
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = process.env.ORIGIN || 'http://localhost:28080';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const ENTRY = { refund: '', attempt: '', clientOrder: '', ...JSON.parse(process.env.ENTRY || '{}') };
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const secrets = { E2E_ADMIN_EMAIL: access.match(/账号：(\S+)/)[1], E2E_ADMIN_PASSWORD: access.match(/密码：(\S+)/)[1] };
// 键类型名称是数据（键类型表维护的中文名），两种界面语言下相同。
const L = {
  'zh-CN': { refund: '退款单号', attempt: '支付尝试 ID', clientOrder: '客户订单号', coverage: '能查到哪些数据？', all: /^全部 \d+ 项$/, joint: '联合查询', save: /^保存为 v\d+$/, ok: '保存', versions: '版本历史' },
  en: { refund: '退款单号', attempt: '支付尝试 ID', clientOrder: '客户订单号', coverage: 'What data can I find?', all: /^All \d+$/, joint: 'Joint Query', save: /^Save as v\d+$/, ok: 'Save', versions: 'Version History' },
};
const canonical = value => JSON.stringify(value, (_, v) => v && typeof v === 'object' && !Array.isArray(v) ? Object.fromEntries(Object.entries(v).filter(([, x]) => x !== null && x !== undefined).sort(([a], [b]) => a.localeCompare(b))) : v);
let savedSession = null;
const WRITES = process.env.WRITES === '1';
const log = { runs: [], console: [], httpErrors: [], deliberate: [], writes: [], bundles: 0 };

const browser = await chromium.launch({ headless: true });
const runs = process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440], ['zh-CN', 390], ['en', 1440], ['en', 390]];
for (const [locale, width] of runs) {
  const t = L[locale]; const tag = `${locale}-${width}`; const desktop = width > 500;
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width, height: desktop ? 1000 : 844 }, locale });
  // 只登录一次：之后的组合把同一会话写入 sessionStorage（控制台会话只存在 sessionStorage）。
  if (savedSession) await context.addInitScript(value => { try { sessionStorage.setItem('paytech.operations.session.v1', value); } catch { /* 忽略 */ } }, savedSession);
  const page = await context.newPage();
  page.on('dialog', dialog => dialog.accept().catch(() => {}));
  const run = { locale, width, steps: [] };
  let current = 'login'; let deliberate = null;
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('response', r => {
    const url = new URL(r.url());
    if (/i18n/.test(url.pathname) && !url.pathname.startsWith('/api/')) log.bundles++;
    if (r.status() >= 400) (deliberate ? log.deliberate : log.httpErrors).push({ run: tag, step: current, why: deliberate ?? undefined, method: r.request().method(), path: url.pathname + url.search, status: r.status() });
  });
  page.on('requestfailed', r => { (deliberate ? log.deliberate : log.httpErrors).push({ run: tag, step: current, why: deliberate ?? undefined, method: r.method(), path: new URL(r.url()).pathname, status: 'failed:' + r.failure()?.errorText }); });
  const settle = async (ms = 900) => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); };
  const facts = async () => page.evaluate(() => ({
    rawKeys: [...new Set(document.body.innerText.match(/\b(data-query|nav|common)\.[A-Za-z]+\.[A-Za-z0-9_.]+/g) || [])].slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  const shot = async (step, check, { wait = true } = {}) => {
    current = step; if (wait) await settle();
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await facts();
    await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }).slice(0, 1200));
  };
  const text = async selector => (await page.locator(selector).first().innerText({ timeout: 3000 }).catch(() => '')).replace(/\s+/g, ' ').slice(0, 700);
  const go = async path => { current = path; await page.goto(`${ORIGIN}${path}${path.includes('?') ? '&' : '?'}lang=${locale}`); await settle(); };
  const api = (path, method = 'GET', body) => page.evaluate(async ([path, method, body, key]) => {
    const token = JSON.parse(sessionStorage.getItem('paytech.operations.session.v1')).accessToken;
    const response = await fetch('/api/operations' + path, { method, headers: { Authorization: 'Bearer ' + token, ...(body ? { 'Content-Type': 'application/json', 'Idempotency-Key': key } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}) });
    return { status: response.status, body: await response.json().catch(() => null) };
  }, [path, method, body ?? null, randomUUID()]);
  const pickType = async label => {
    await page.locator('.jq-type').click();
    await page.locator('.jq-type-menu input').fill(label);
    await page.locator('.jq-type-item', { hasText: label }).first().click();
  };
  const runQuery = async (label, value) => {
    await pickType(label);
    await page.locator('.jq-input textarea').fill(value);
    await page.locator('.jq-submit').click();
    await page.waitForTimeout(400);
    await page.waitForFunction(() => !document.querySelector('.jq-summary .ant-spin') && !document.querySelector('.jq-submit.ant-btn-dangerous'), null, { timeout: 70000 }).catch(() => {});
  };
  const graphNodes = async () => page.locator('.jq-card').evaluateAll(cards => cards.map(card => card.innerText.replace(/\s+/g, ' ')));

  // ---------- 登录 ----------
  if (!savedSession) {
    await page.goto(`${ORIGIN}/?lang=${locale}`);
    await page.locator('input:not([type=password])').first().fill(secrets.E2E_ADMIN_EMAIL);
    await page.locator('input[type=password]').first().fill(secrets.E2E_ADMIN_PASSWORD);
    const login = page.waitForResponse(r => r.url().includes('/auth/v1/login'), { timeout: 20000 }).catch(() => null);
    await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
    run.login = (await login)?.status() ?? null;
    await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
    await settle();
    savedSession = await page.evaluate(() => sessionStorage.getItem('paytech.operations.session.v1'));
  }

  // ---------- 01 空状态与类型菜单 ----------
  await go('/data-query/joint-query');
  await page.locator('.jq-search').waitFor({ timeout: 20000 }).catch(() => {});
  await shot('01a-empty', async () => ({ chips: await text('.jq-chips'), empty: await text('.jq-empty') }));
  await page.locator('.jq-type').click();
  await page.waitForTimeout(400);
  await shot('01b-type-menu', async () => text('.jq-type-menu'), { wait: false });
  await page.keyboard.press('Escape');

  // ---------- 08 格式提示与覆盖范围 ----------
  await pickType(t.refund);
  await page.locator('.jq-input textarea').fill(`${ENTRY.refund}\nRF20261029-0001`);
  await shot('08a-format-error', async () => ({ message: await text('.jq-form-message'), invalidLines: await page.locator('.jq-input-backdrop .is-invalid').count() }), { wait: false });
  await page.getByRole('button', { name: t.coverage }).click();
  await page.locator('.jq-coverage .ant-drawer-body').waitFor({ timeout: 10000 }).catch(() => {});
  await shot('08b-coverage', async () => text('.jq-coverage .ant-drawer-content'));
  await page.keyboard.press('Escape');
  await page.waitForTimeout(300);

  // ---------- 02 退款单号：总览与明细 ----------
  await runQuery(t.refund, ENTRY.refund);
  await shot('02a-refund-overview', async () => ({ summary: await text('.jq-summary'), nodes: desktop ? await graphNodes() : await text('.jq-mobile'), detail: await text('.jq-detail') }));
  if (desktop) {
    const truncated = page.locator('.jq-card.is-truncated').first();
    if (await truncated.count()) {
      await truncated.click();
      await page.waitForTimeout(500);
      await shot('02b-path-truncated', async () => ({ detail: await text('.jq-detail'), activeEdges: await page.locator('.jq-edge.is-active').count() }), { wait: false });
      const more = page.locator('.jq-detail-foot .ant-btn').first();
      if (await more.count()) { await more.click(); await settle(); }
      await shot('02c-load-more', async () => ({ rows: await page.locator('.jq-detail .jq-table tbody tr').count(), foot: await text('.jq-detail-foot') }));
    }
    const locate = page.locator('.jq-detail .jq-locate').first();
    if (await locate.count()) {
      await locate.click({ force: true });
      await page.waitForTimeout(500);
      await shot('02d-locate', async () => ({ selected: await text('.jq-card.is-selected'), detailTitle: await text('.jq-detail-title') }), { wait: false });
    }
    await page.locator('.ant-segmented-item', { hasText: t.all }).first().click();
    await page.waitForTimeout(500);
    await shot('02e-all-items', async () => ({ sections: await page.locator('.jq-detail').count() }), { wait: false });
  } else {
    await page.locator('.jq-mobile-row').first().click();
    await page.waitForTimeout(800);
    await shot('06b-mobile-detail', async () => text('.jq-mobile-detail .ant-drawer-body'), { wait: false });
    await page.keyboard.press('Escape');
    await page.waitForTimeout(400);
    await page.locator('.jq-mobile-toggle').click();
    await page.waitForTimeout(800);
    await shot('06c-mobile-graph', async () => ({ cards: await page.locator('.jq-card').count() }), { wait: false });
  }

  // ---------- 07 从支付尝试进入 + 维度“以此查询” ----------
  await runQuery(t.attempt, ENTRY.attempt);
  await shot('07a-attempt-overview', async () => ({
    summary: await text('.jq-summary'),
    lanes: desktop ? await page.locator('.jq-lane-title').allInnerTexts() : await page.locator('.jq-mobile-lane-head').allInnerTexts(),
    nodes: desktop ? await graphNodes() : undefined,
  }));
  if (desktop) {
    // 找到明细中带维度值的节点：逐个点击直到出现 .jq-dimension
    const count = await page.locator('.jq-card').count();
    for (let i = 0; i < count; i++) {
      await page.locator('.jq-card').nth(i).click();
      await page.waitForTimeout(250);
      if (await page.locator('.jq-detail .jq-dimension').count()) break;
    }
    await shot('07b-dimension-cell', async () => ({ detail: await text('.jq-detail'), dimensions: await page.locator('.jq-detail .jq-dimension').allInnerTexts() }), { wait: false });
    const dimension = page.locator('.jq-detail .jq-dimension').first();
    if (await dimension.count()) {
      await dimension.hover(); await page.waitForTimeout(400);
      await shot('07c-query-this-hover', async () => text('.ant-popover:visible'), { wait: false });
      await dimension.click();
      await page.waitForTimeout(400);
      await page.waitForFunction(() => !document.querySelector('.jq-summary .ant-spin'), null, { timeout: 60000 }).catch(() => {});
      await shot('07d-query-this-result', async () => ({ type: await text('.jq-type'), input: await page.locator('.jq-input textarea').inputValue(), summary: await text('.jq-summary') }));
    }
  }

  // ---------- 03 CO-1 命中多个客户 ----------
  await runQuery(t.clientOrder, ENTRY.clientOrder);
  await shot('03a-multi-client', async () => ({ summary: await text('.jq-summary'), clients: await page.locator('.jq-client').allInnerTexts() }));
  if (await page.locator('.jq-client').count() > 2) {
    await page.locator('.jq-client').nth(2).click();
    await page.waitForTimeout(500);
    await shot('03b-client-filtered', async () => ({ summary: await text('.jq-summary'), nodes: desktop ? await graphNodes() : await text('.jq-mobile'), detail: desktop ? await text('.jq-detail') : undefined }), { wait: false });
  }

  // ---------- 03 未查全 + 重试（只在 1440，JQ_SLOW_ORDER 临时启用） ----------
  // 写操作（临时启用慢查询模板）默认关闭；mor-test 只有 WRITES=1 且经用户批准才执行。
  if (desktop && WRITES) {
    const list = await api('/data-query-admin/v1/templates?keyword=JQ_SLOW_ORDER&page=1&size=10');
    const slow = list.body?.items?.find(item => item.templateCode === 'JQ_SLOW_ORDER');
    if (slow && slow.status === 'INACTIVE') {
      let version = slow.version;
      try {
        const on = await api(`/data-query-admin/v1/templates/${slow.templateId}/status`, 'POST', { status: 'ACTIVE', expectedVersion: version });
        log.writes.push({ run: tag, action: 'activate JQ_SLOW_ORDER', status: on.status });
        version = on.body?.version ?? version;
        await go('/data-query/joint-query');
        await runQuery(t.refund, ENTRY.refund);
        const chip = page.locator('.jq-issues-chip');
        if (await chip.count()) await chip.click();
        await shot('03c-gaps', async () => ({ summary: await text('.jq-summary'), issues: await text('.jq-issues'), nodes: await graphNodes() }));
        const failed = page.locator('.jq-card.is-failed').first();
        if (await failed.count()) {
          await failed.click(); await page.waitForTimeout(400);
          await shot('03d-failed-detail', async () => text('.jq-detail'), { wait: false });
          await page.locator('.jq-detail-problem .ant-btn').nth(1).click(); await page.waitForTimeout(300);
          await shot('03e-technical', async () => text('.jq-tech'), { wait: false });
          await page.locator('.jq-detail-problem .ant-btn-primary').click();
          await page.waitForTimeout(500);
          await page.waitForFunction(() => !document.querySelector('.jq-detail-problem .ant-btn-loading'), null, { timeout: 30000 }).catch(() => {});
          await shot('03f-after-retry', async () => ({ summary: await text('.jq-summary'), detail: await text('.jq-detail') }));
        }
      } finally {
        const latest = (await api('/data-query-admin/v1/templates?keyword=JQ_SLOW_ORDER&page=1&size=10')).body?.items?.find(item => item.templateCode === 'JQ_SLOW_ORDER');
        const off = await api(`/data-query-admin/v1/templates/${latest.templateId}/status`, 'POST', { status: 'INACTIVE', expectedVersion: latest.version });
        log.writes.push({ run: tag, action: 'deactivate JQ_SLOW_ORDER', status: off.status, finalStatus: (await api('/data-query-admin/v1/templates?keyword=JQ_SLOW_ORDER&page=1&size=10')).body?.items?.[0]?.status });
      }
    } else log.writes.push({ run: tag, action: 'skip slow scenario', found: slow?.status ?? 'missing' });
  }

  // ---------- 04 键类型 ----------
  await go('/data-query/key-types');
  await page.locator('.kt-row').first().waitFor({ timeout: 20000 }).catch(() => {});
  await shot('04a-key-types', async () => ({ groups: await page.locator('.kt-group-title').allInnerTexts(), rows: await page.locator('.kt-row').count() }));
  const used = page.locator('.kt-row', { hasText: 'ORDER_ID' }).first();
  await used.click(); await page.waitForTimeout(600);
  if (await page.locator('.kt-pattern-head').getAttribute('aria-expanded') !== 'true') await page.locator('.kt-pattern-head').click();
  await page.locator('.kt-pattern-test input').fill('2051880011734528801');
  await page.waitForTimeout(300);
  await shot('04b-editor-regex', async () => ({ head: await text('.kt-editor-head'), valueTypeDisabled: await page.locator('.kt-editor .ant-select-disabled').count(), test: await text('.kt-pattern-test'), help: await text('.kt-editor-body') }), { wait: false });
  await page.locator('.kt-pattern-test input').fill('20518800117345288x');
  await page.waitForTimeout(200);
  await shot('04c-regex-fail', async () => text('.kt-pattern-test'), { wait: false });
  if (desktop && locale === 'zh-CN' && WRITES) {
    const order = async () => (await api('/data-query-admin/v1/key-types?page=1&size=200')).body.items.map(item => item.keyTypeCode);
    const before = await order();
    const rows = page.locator('.kt-group').first().locator('.kt-row');
    const first = await rows.nth(0).innerText(); const second = await rows.nth(1).innerText();
    await rows.nth(1).dragTo(rows.nth(0)); await settle(1500);
    const moved = await order();
    await shot('04d-after-drag', async () => ({ firstRow: (await page.locator('.kt-group').first().locator('.kt-row').nth(0).innerText()).split('\n')[0], movedSwap: moved[0] !== before[0] || moved[1] !== before[1] }));
    const again = page.locator('.kt-group').first().locator('.kt-row');
    await again.nth(1).dragTo(again.nth(0)); await settle(1500);
    const after = await order();
    log.writes.push({ run: tag, action: 'drag reorder and back', first: first.split('\n')[0], second: second.split('\n')[0], changed: JSON.stringify(moved) !== JSON.stringify(before), restored: JSON.stringify(after) === JSON.stringify(before) });
  }

  // ---------- 05 模板编辑器“联合查询”页签 ----------
  const editorTemplate = process.env.TEMPLATE || 'JQ_ORDER_BY_ID';
  const templates = await api(`/data-query-admin/v1/templates?keyword=${editorTemplate}&page=1&size=10`);
  const target = templates.body?.items?.find(item => item.templateCode === editorTemplate);
  if (target) {
    await go(`/data-query/templates/${target.templateId}`);
    await page.locator('.dq-editor-tabs').waitFor({ timeout: 20000 }).catch(() => {});
    const tabs = await page.locator('.dq-editor-tabs .ant-tabs-tab').allInnerTexts();
    // 窄屏时标签可能收进 antd 的“…”菜单：标签不在视口内就从菜单进入（与用户操作一致）
    const jointTab = page.locator('.dq-editor-tabs .ant-tabs-tab', { hasText: t.joint }).first();
    const inView = await jointTab.evaluate(el => { const r = el.getBoundingClientRect(); return r.right <= document.documentElement.clientWidth && r.left >= 0; }).catch(() => false);
    const tabError = await (inView ? jointTab.click({ timeout: 8000 })
      : page.locator('.dq-editor-tabs .ant-tabs-nav-more').click().then(() => page.locator('.ant-tabs-dropdown-menu-item', { hasText: t.joint }).click()))
      .then(() => null, e => String(e).split('\n')[0]);
    run.steps.push({ step: '05-tab-route', check: { inView } });
    await settle();
    run.steps.push({ step: '05-tabs', check: { tabs, tabError } });
    await shot('05a-joint-tab', async () => text('.dq-joint'));
    if (desktop) {
      // 保存往返：只截获 PUT 请求体，比对 joint 后中止，不产生新版本
      const detail = (await api(`/data-query-admin/v1/templates/${target.templateId}`)).body;
      let captured = null;
      await page.route(new RegExp(`/api/operations/data-query-admin/v1/templates/${target.templateId}$`), route =>
        route.request().method() === 'PUT' ? (captured = route.request().postDataJSON(), route.abort()) : route.continue());
      await page.locator('.dq-description-input').fill(`${detail.description ?? ''} stage5`);
      await page.getByRole('button', { name: t.save }).click();
      deliberate = 'aborted PUT (save round-trip check)';
      await page.locator('.ant-modal .ant-btn-primary').last().click();
      await page.waitForTimeout(1500); deliberate = null;
      await shot('05b-save-roundtrip', async () => ({ jointEqual: canonical(captured?.joint) === canonical(detail.currentVersion?.joint ?? detail.joint), serverJoint: detail.currentVersion?.joint ?? detail.joint, sentJoint: captured?.joint ?? null }), { wait: false });
      await page.unroute(new RegExp(`/api/operations/data-query-admin/v1/templates/${target.templateId}$`));
      const after = (await api(`/data-query-admin/v1/templates/${target.templateId}`)).body;
      log.writes.push({ run: tag, action: 'template save intercepted', versionBefore: detail.currentVersionNo, versionAfter: after.currentVersionNo });
      await page.goto(`${ORIGIN}/data-query/templates/${target.templateId}?lang=${locale}`); await settle();
      await page.getByRole('button', { name: t.versions }).click();
      await page.locator('.dq-version-compare').waitFor({ timeout: 10000 }).catch(() => {});
      await shot('05c-version-joint', async () => text('.dq-version-compare'));
    }
  }

  log.runs.push(run);
  await context.close();
}
await browser.close();
writeFileSync(join(HERE, `stage5-browser-result${process.env.RUNS ? '-' + log.runs.map(r => r.locale + '-' + r.width).join('_') : ''}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, deliberate: log.deliberate.length, writes: log.writes, bundles: log.bundles }, null, 1));
