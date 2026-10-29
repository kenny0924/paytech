// REQ-05 阶段 5：wfdq worktree ops-web（127.0.0.1:18311 → Nginx 18444 → Gateway → ops-bff → Platform）浏览器走查，截图对照原型 17—20。
// 组合：zh-CN / en × 1440 / 390。写操作只有：每个组合发起一个 ACC_REQ05_CHECK_FAILURE 实例拍「检查中」；zh-CN 1440 与 en 1440
// 各对 N3 未满足动作点一次「立即检查」。动作模板编辑页故意加一个未声明的输出并点保存，只调用校验接口（PUT 在浏览器内被拦截，不会保存）。
// 记录控制台 error / warning、HTTP >= 400、未翻译的 workflow.* 键与 390 宽横向滚动。凭据读 local-environment/e2e/secrets.env，不打印。
// 用法：node stage5_browser.mjs；RUNS='[["zh-CN",1440,true]]' 只跑部分组合；NO_BUNDLE_CORS=1 不补语言包 ACAO（MinIO 已放行 18311）。
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = 'http://127.0.0.1:18311';
const SHOTS = join(HERE, 'screens');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
mkdirSync(SHOTS, { recursive: true });

const IDS = {
  drafted: '241464538459803648', definition: '241464540657618944', completed: '241464542310174720', n8: '241464542679273472',
  n3: '241464542872211456', psp: '241464543132258304', failed: '241464545422348288', agreement: '900000000001390001',
};
const task = (instance, node) => `/workflow/my-tasks?task=${encodeURIComponent(`ACTION:${instance}:${node}:1`)}`;
const secrets = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
  .split('\n').filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const L = {
  'zh-CN': { save: '保存', discard: '放弃修改', addOutput: '添加输出', actions: /^动作/, checkNow: '立即检查', activity: '活动记录' },
  en: { save: 'Save', discard: 'Discard Changes', addOutput: 'Add output', actions: /^Actions/, checkNow: 'Check now', activity: 'Activity' },
};
const log = { runs: [], console: [], httpErrors: [], bundles: 0 };

const browser = await chromium.launch({ headless: true });
for (const [locale, width, writes] of (process.env.RUNS ? JSON.parse(process.env.RUNS) : [['zh-CN', 1440, true], ['zh-CN', 390, false], ['en', 1440, true], ['en', 390, false]])) {
  const t = L[locale];
  const tag = `${locale}-${width}`;
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width, height: width > 500 ? 1000 : 844 }, locale });
  if (!process.env.NO_BUNDLE_CORS) await context.route(/^https:\/\/127\.0\.0\.1:19400\//, async route => {
    const response = await route.fetch();
    await route.fulfill({ response, headers: { ...response.headers(), 'access-control-allow-origin': ORIGIN } });
  });
  // 保险：动作模板编辑页不能真的保存
  await context.route(/\/api\/operations\/workflow-admin\/v1\/action-templates\/\d+$/, route =>
    route.request().method() === 'PUT' ? route.abort() : route.continue());
  const page = await context.newPage();
  const run = { locale, width, steps: [] };
  let current = 'login';
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) log.console.push({ run: tag, step: current, type: m.type(), text: m.text().slice(0, 300) }); });
  page.on('response', (r) => {
    const url = new URL(r.url());
    if (url.port === '19400') log.bundles++;
    if (r.status() >= 400) log.httpErrors.push({ run: tag, step: current, method: r.request().method(), path: url.pathname, status: r.status() });
  });
  page.on('requestfailed', (r) => { if (!r.url().includes(':19400/')) log.httpErrors.push({ run: tag, step: current, method: r.method(), path: new URL(r.url()).pathname, status: 'failed:' + r.failure()?.errorText }); });
  const settle = async (ms = 1200) => { await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {}); await page.waitForTimeout(ms); };
  const pageFacts = async () => page.evaluate(() => ({
    rawKeys: [...new Set((document.body.innerText.match(/\bworkflow\.[A-Za-z]+\.[A-Za-z0-9_.]+/g) || []))].slice(0, 10),
    horizontalScroll: document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    scrollWidth: document.documentElement.scrollWidth,
    overflow: (() => { const vw = document.documentElement.clientWidth;
      return [...document.querySelectorAll('body *')].filter(el => { const r = el.getBoundingClientRect(); return r.width && r.right > vw + 1; })
        .filter(el => !el.parentElement || el.parentElement.getBoundingClientRect().right <= vw + 1).slice(0, 5)
        .map(el => `${el.tagName.toLowerCase()}.${String(el.className).split(' ').slice(0, 2).join('.')}:${Math.round(el.getBoundingClientRect().right)}`); })(),
  }));
  const shot = async (step, check, { wait = true } = {}) => {
    current = step; if (wait) await settle();
    const entry = { step };
    try { entry.check = check ? await check() : undefined; } catch (e) { entry.check = { error: String(e).slice(0, 200) }; }
    entry.page = await pageFacts();
    await page.screenshot({ path: join(SHOTS, `${tag}-${step}.png`), fullPage: true });
    run.steps.push(entry); console.log(JSON.stringify({ run: tag, ...entry }));
  };
  const text = async (selector) => (await page.locator(selector).first().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 900);
  const go = async (path) => { current = path; await page.goto(`${ORIGIN}${path}${path.includes('?') ? '&' : '?'}lang=${locale}`); await settle(); };

  await page.goto(`${ORIGIN}/?lang=${locale}`);
  await page.locator('input:not([type=password])').first().fill(secrets.E2E_ADMIN_EMAIL);
  await page.locator('input[type=password]').first().fill(secrets.E2E_ADMIN_PASSWORD);
  await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
  await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
  await settle();

  // ---------- 原型 17：动作目录 · 数据查询 ----------
  await go(`/workflow/action-templates/${IDS.drafted}`);
  await page.locator('.wf-dq-editor').waitFor({ timeout: 20000 }).catch(() => {});
  await shot('17a-template', async () => ({ meta: await text('.wf-dq-meta'), editor: await text('.wf-dq-editor'), contract: await text('.wf-dq-contract') }));
  await page.locator('.wf-dq-editor .ant-select').first().click();
  await page.waitForTimeout(1200);
  await shot('17b-picker', async () => text('.ant-select-dropdown:visible'), { wait: false });
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: t.addOutput }).click();
  await page.locator('.wf-dq-table').last().locator('tbody tr').last().locator('input').first().fill('client_name');
  await page.getByRole('button', { name: new RegExp('^' + t.save + '$') }).click();
  await page.waitForTimeout(2500);
  await shot('17c-validation', async () => ({ rows: await page.locator('.wf-dq-table tr.is-error').allInnerTexts(), alert: await text('.wf-dq-editor .ant-alert') }));
  await page.getByRole('button', { name: t.discard }).click();
  await page.waitForTimeout(500);

  // ---------- 原型 18：流程编辑器（只看，不发布） ----------
  await go(`/workflow/definitions/${IDS.definition}`);
  await page.locator('.react-flow__node[data-id="n3"]').waitFor({ timeout: 20000 }).catch(() => {});
  await page.locator('.react-flow__node[data-id="n3"]').click().catch(() => {});
  await page.waitForTimeout(600);
  const actionsTab = page.locator('.ant-tabs-tab', { hasText: t.actions }).first();
  if (await actionsTab.isVisible().catch(() => false)) await actionsTab.click();
  await shot('18a-node-actions', async () => text('.wf-node-actions'));
  await page.locator('.react-flow__node[data-id="g1"]').click().catch(() => {});
  await page.waitForTimeout(800);
  await shot('18b-gateway', async () => text('.wf-editor-panel, .wf-panel'));

  // ---------- 原型 19：我的待办 ----------
  await go(task(IDS.n8, 'n8'));
  await shot('19a-unsatisfied-n8', async () => text('.wf-detail'));
  await go(task(IDS.n3, 'n3'));
  await shot('19b-unsatisfied-n3', async () => text('.wf-detail'));
  if (writes) {
    await page.locator('.wf-dq-row').first().getByRole('button', { name: t.checkNow }).click();
    await page.waitForTimeout(400);
    await shot('19c-checking-now', async () => text('.wf-dq-row'), { wait: false });
    await settle(2500);
    await shot('19d-after-check', async () => text('.wf-dq-row'));
  }
  await go(task(IDS.failed, 'slow'));
  await shot('19e-failed', async () => text('.wf-detail'));
  await go(task(IDS.psp, 'psp'));
  await shot('19f-manual-confirm', async () => text('.wf-detail'));
  // 检查中：发起后尽快打开（首查约 1 秒后超时失败）
  const started = execFileSync('python3', ['-I', join(HERE, 'start_checking.py'), tag], { encoding: 'utf8' }).trim().split(' ')[0];
  current = '19g-checking';
  await page.goto(`${ORIGIN}${task(started, 'slow')}&lang=${locale}`);
  await page.locator('.wf-dq-row').first().waitFor({ timeout: 15000 }).catch(() => {});
  await shot('19g-checking', async () => ({ instance: started, row: await text('.wf-dq-row') }), { wait: false });

  // ---------- 原型 20：实例详情 ----------
  await go(`/workflow/instances/${IDS.completed}`);
  await page.locator('.ant-segmented-item', { hasText: t.activity }).first().click();
  await shot('20a-activity', async () => ({ timeline: await text('.wf-activity .wf-card'), aside: await text('.wf-activity aside') }));
  await go(`/workflow/instances/${IDS.n8}`);
  await page.locator('.react-flow__node[data-id="n3"]').click().catch(() => {});
  await page.waitForTimeout(1500);
  await shot('20b-node-drawer-satisfied', async () => text('.ant-drawer:visible'));
  await page.keyboard.press('Escape');
  await page.locator('.react-flow__node[data-id="n8"]').click().catch(() => {});
  await page.waitForTimeout(1500);
  await shot('20c-node-drawer-unsatisfied', async () => text('.ant-drawer:visible'));

  // ---------- 回归：REQ-01 合同详情 ----------
  await go(`/commercial/agreements/${IDS.agreement}`);
  await shot('99-req01-agreement', async () => text('main, .ant-layout-content'));

  log.runs.push(run);
  await context.close();
}
await browser.close();
writeFileSync(join(HERE, `stage5-browser-result${process.env.RUNS ? '-' + log.runs.map(r => r.locale + '-' + r.width).join('_') : ''}.json`), JSON.stringify(log, null, 2));
console.log(JSON.stringify({ console: log.console.length, httpErrors: log.httpErrors, bundles: log.bundles }, null, 1));
