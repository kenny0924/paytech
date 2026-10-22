// 复测 5 项修复（只读 + 一次被改写的模板保存请求），截图 screenshots/fix-*.png。
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'https://127.0.0.1:18444';
const SCRATCH = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/49da8772-1276-46ac-8174-179e39b15018/scratchpad';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const state = JSON.parse(readFileSync(join(SCRATCH, 'workflow-e2e-state.json'), 'utf8'));
const env = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8').split('\n')
  .filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim().replace(/^"|"$/g, '')]));
const shot = (page, name) => page.screenshot({ path: join(HERE, 'screenshots', `fix-${name}.png`) });
const log = (...a) => console.log(...a);
const errors = [];
const browser = await chromium.launch({ headless: true });
const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1600, height: 1000 } });
const page = await ctx.newPage();
page.on('pageerror', e => errors.push(String(e).slice(0, 200)));
await page.goto(OPS + '/');
await page.getByLabel('Email / 初始化账号').fill(env.E2E_ADMIN_EMAIL);
await page.getByLabel('密码').fill(env.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^登\s*录$/ }).click();
await page.waitForLoadState('networkidle');
const settle = async () => { await page.waitForLoadState('networkidle'); await page.waitForTimeout(1500); };
const count = async () => `节点=${await page.locator('.react-flow__node').count()} 连线=${await page.locator('.react-flow__edge').count()}`;

if (!process.env.ONLY4) {
// 1
await page.goto(`${OPS}/workflow/definitions/${state.definitionId}/versions/1`); await settle();
log('1a 版本只读页', await count()); await shot(page, '1a-version-graph');
await page.goto(`${OPS}/workflow/definitions`); await settle();
const card = page.locator('div', { hasText: `WF_E2E_PAR_${state.stamp}` }).filter({ has: page.locator('svg, .react-flow') }).last();
log('1b PAR 卡片含图形', await card.count(), '页面 react-flow 数', await page.locator('.react-flow').count(), 'svg 数', await page.locator('svg').count());
await shot(page, '1b-definition-cards');
await page.goto(`${OPS}/workflow/definitions/${state.definitionId}`); await settle();
log('1c 无草稿编辑页', await count(), (await page.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(0, 120));
await shot(page, '1c-editor-from-published');

// 2
await page.goto(`${OPS}/workflow/my-tasks`); await settle();
const item = page.locator('.wf-task-item', { hasText: `E2E I4 ${state.stamp}` }).first();
await item.click(); await settle();
const head = (await page.locator('.wf-task-head').first().innerText()).replace(/\s+/g, ' ');
log('2 I4 头部', head.slice(0, 260), '| 含无审批人=', head.includes('无审批人'), '| 含 w***=', head.includes('w***@example.test'));
await shot(page, '2-task-header');

// 3
for (const width of [1600, 1280]) {
  await page.setViewportSize({ width, height: 1000 });
  await page.goto(`${OPS}/workflow/action-templates/${state.formTemplateId}`); await settle();
  await page.getByRole('tab', { name: '字段设计' }).click().catch(() => undefined); await page.waitForTimeout(800);
  const label = page.getByText(/商户名称/).first();
  const box = await label.boundingBox({ timeout: 8000 }).catch(() => null);
  const main = await page.locator('main').first().innerText();
  log(`3 ${width}px 字段名框`, box ? `${Math.round(box.width)}x${Math.round(box.height)}` : '无', '| 含 #2360=', main.includes('#2360'), '| 修改人行', (main.match(/版本 \d+ · [^\n]{0,60}/) || [''])[0]);
  await shot(page, `3-field-designer-${width}`);
}
await page.setViewportSize({ width: 1600, height: 1000 });

}
// 4：保存时把请求体第 2 个字段类型改成 NOPE，看页面错误提示
await page.route('**/workflow-admin/v1/action-templates/*', async (route) => {
  const req = route.request();
  if (req.method() !== 'PUT') return route.continue();
  const body = JSON.parse(req.postData()); body.config.fields[1].type = 'NOPE';
  await route.continue({ postData: JSON.stringify(body) });
});
await page.goto(`${OPS}/workflow/action-templates/${state.formTemplateId}`); await settle();
await page.getByRole('tab', { name: '字段设计' }).click().catch(() => undefined); await page.waitForTimeout(800);
const display = page.locator('.wf-field', { hasText: '显示名称' }).locator('input').first();
await display.fill('商户名称（复测）').catch(e => log('4 改显示名称失败', String(e).slice(0, 120)));
const reply = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().includes('/action-templates/'), { timeout: 10000 }).catch(() => null);
await page.getByRole('button', { name: /^保\s*存$/ }).click().catch(e => log('4 点击保存失败', String(e).slice(0, 120)));
const response = await reply;
await page.waitForTimeout(1200);
const text = (await page.locator('body').innerText()); log('4 错误附近', (text.match(/[^\n]*(校验|config\.fields|请检查)[^\n]*/g) || []).slice(0, 5).join(' / '));
log('4 UI 保存 HTTP', response?.status(), '| 页面含 config.fields[1].type=', text.includes('config.fields[1].type'), '| 含“请检查此处配置”=', text.includes('请检查此处配置'));
await shot(page, '4-template-422');
log('pageerrors', JSON.stringify(errors));
await browser.close();
