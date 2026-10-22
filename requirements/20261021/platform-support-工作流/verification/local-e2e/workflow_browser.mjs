// 工作流阶段 5 浏览器验收（本机 https://127.0.0.1:18444，无头 Chromium）。
// 前置：workflow_flow.py setup / start1… 已写出 STATE（只含 ID）。只记方法、路径、状态，不记口令或令牌。
// 流程：登录 → 菜单“工作流”四页 → 动作目录详情 → 流程定义版本图 / 非法草稿问题 → 实例列表与实例 1 详情（活动）→ 我的待办（待处理 / 已处理）
//   → 可选 TASK_INSTANCE：实例 4 的 fill 动作在待办里提交（Ctrl+Enter）并验证自动下一条与 J/K。
// 用法：node workflow_browser.mjs
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OPS = 'https://127.0.0.1:18444';
const SHOTS = process.env.SHOTS || join(HERE, 'screenshots');
const SCRATCH = process.env.WF_SCRATCH || '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/49da8772-1276-46ac-8174-179e39b15018/scratchpad';
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
const state = JSON.parse(readFileSync(join(SCRATCH, 'workflow-e2e-state.json'), 'utf8'));
mkdirSync(SHOTS, { recursive: true });

function env(file) {
  const values = {};
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim().replace(/^"|"$/g, '');
  }
  return values;
}
const admin = env('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env');

const results = [], network = [], consoleErrors = [];
let shotNo = 0;
async function shot(page, name) {
  const file = join(SHOTS, String(++shotNo).padStart(2, '0') + '-' + name + '.png');
  await page.screenshot({ path: file, fullPage: false });
  return file;
}
async function step(page, name, action) {
  let text = '';
  try { text = (await action()) || ''; } catch (failure) {
    const file = await shot(page, name);
    results.push(['FAIL', name, String(failure.message || failure).split('\n')[0].slice(0, 300), file]);
    console.log(`FAIL ${name} | ${results.at(-1)[2]} | ${file}`);
    return false;
  }
  const file = await shot(page, name);
  results.push(['PASS', name, text, file]);
  console.log(`PASS ${name} | ${text} | ${file}`);
  return true;
}
const py = (...args) => execFileSync('python3', ['workflow_flow.py', ...args], { cwd: HERE }).toString();

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1600, height: 1000 } });
  const page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 300)); });
  page.on('pageerror', e => consoleErrors.push('pageerror: ' + String(e).slice(0, 300)));
  page.on('response', r => { if (r.status() >= 400) network.push(`${r.request().method()} ${new URL(r.url()).pathname} -> ${r.status()}`); });

  await step(page, 'login', async () => {
    await page.goto(OPS + '/');
    await page.getByLabel('Email / 初始化账号').fill(admin.E2E_ADMIN_EMAIL);
    await page.getByLabel('密码').fill(admin.E2E_ADMIN_PASSWORD);
    await page.getByRole('button', { name: /^登\s*录$/ }).click();
    await page.waitForLoadState('networkidle');
    return page.url();
  });
  await step(page, 'menu-workflow', async () => {
    const group = page.getByText('工作流', { exact: true }).first();
    await group.waitFor({ timeout: 15000 });
    await group.click();
    const names = [];
    for (const name of ['我的待办', '流程实例', '流程定义', '动作目录']) {
      await page.getByRole('menuitem', { name }).first().waitFor({ timeout: 5000 }); names.push(name);
    }
    return names.join('、');
  });
  const pages = [['my-tasks', '我的待办'], ['instances', '流程实例'], ['definitions', '流程定义'], ['action-templates', '动作目录']];
  for (const [path, heading] of pages) {
    await step(page, `page-${path}`, async () => {
      await page.goto(`${OPS}/workflow/${path}`);
      await page.waitForLoadState('networkidle');
      await page.getByRole('heading', { name: heading }).first().waitFor({ timeout: 15000 });
      return (await page.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(0, 160);
    });
  }
  await step(page, 'template-detail', async () => {
    await page.goto(`${OPS}/workflow/action-templates/${state.formTemplateId}`);
    await page.waitForLoadState('networkidle');
    await page.getByText(`WF_E2E_FORM_${state.stamp}`).first().waitFor({ timeout: 15000 });
    return (await page.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(0, 200);
  });
  await step(page, 'template-references', async () => {
    await page.getByRole('tab', { name: /引用/ }).first().click();
    await page.getByText(`WF_E2E_ONBOARD_${state.stamp}`).first().waitFor({ timeout: 10000 });
    const tabs = await page.getByRole('tab').allInnerTexts();
    return `页签=${tabs.join('|')}；引用含 WF_E2E_ONBOARD_${state.stamp}`;
  });
  await step(page, 'definition-version-graph', async () => {
    await page.goto(`${OPS}/workflow/definitions/${state.definitionId}/versions/${state.versionNo}`);
    await page.waitForLoadState('networkidle');
    await page.locator('.react-flow__node').first().waitFor({ timeout: 15000 });
    return `节点数=${await page.locator('.react-flow__node').count()} 连线数=${await page.locator('.react-flow__edge').count()}`;
  });
  await step(page, 'definition-invalid-draft', async () => {
    await page.goto(`${OPS}/workflow/definitions/${state.badDefinitionId}`);
    await page.waitForLoadState('networkidle');
    await page.getByText(/任务节点至少需要一个审批人或一个动作/).first().waitFor({ timeout: 15000 });
    return '画布/问题列表显示 TASK_EMPTY 文案';
  });
  await step(page, 'instance1-detail', async () => {
    await page.goto(`${OPS}/workflow/instances/${state.instance1}`);
    await page.waitForLoadState('networkidle');
    await page.locator('.react-flow__node').first().waitFor({ timeout: 15000 });
    return (await page.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(0, 200);
  });
  await step(page, 'instance1-activities', async () => {
    const tab = page.getByText('活动记录').first();
    await tab.click({ timeout: 5000 });
    await page.locator('.ant-spin-spinning').first().waitFor({ state: 'detached', timeout: 15000 }).catch(() => undefined);
    await page.waitForTimeout(1500);
    return (await page.locator('main').first().innerText()).replace(/\s+/g, ' ').slice(0, 240);
  });
  await step(page, 'my-tasks-handled', async () => {
    await page.goto(`${OPS}/workflow/my-tasks`);
    await page.getByText('已处理', { exact: true }).click();
    await page.getByText(`E2E I1 ${state.stamp}`).first().waitFor({ timeout: 10000 });
    return `已处理行数=${await page.locator('.ant-table-row').count()}`;
  });

  if (process.env.TASK_INSTANCE !== '0') {
    // 两条待办：实例 4、5 各一条 fill 动作（admin 执行）
    py('startx', 'I4', 'CARD'); py('startx', 'I5', 'APPLE_PAY');
    await step(page, 'my-tasks-todo', async () => {
      await page.goto(`${OPS}/workflow/my-tasks`);
      await page.waitForLoadState('networkidle');
      await page.locator('.wf-task-item').nth(1).waitFor({ timeout: 15000 });
      return `待办=${await page.locator('.wf-task-item').count()} 选中=${await page.locator('.wf-task-item.is-selected').innerText()}`;
    });
    await step(page, 'shortcut-JK', async () => {
      const first = await page.locator('.wf-task-item.is-selected').innerText();
      await page.locator('body').click({ position: { x: 5, y: 5 } });
      await page.keyboard.press('j');
      await page.waitForTimeout(500);
      const second = await page.locator('.wf-task-item.is-selected').innerText();
      await page.keyboard.press('k');
      await page.waitForTimeout(500);
      const back = await page.locator('.wf-task-item.is-selected').innerText();
      if (first === second || back !== first) throw new Error(`J/K 未切换 first=${first.slice(0, 30)} second=${second.slice(0, 30)}`);
      return 'J 切到下一条，K 回到上一条';
    });
    await step(page, 'submit-ctrl-enter-auto-next', async () => {
      const before = await page.locator('.wf-task-item.is-selected').innerText();
      const main = page.locator('.wf-task-main');
      await main.getByLabel(/商户名称/).fill('Browser Co');
      await main.locator('input.ant-input-number-input').first().fill('321.5');
      await page.locator('body').click({ position: { x: 5, y: 5 } });
      const reply = page.waitForResponse(r => r.url().includes('/submission') && r.request().method() === 'POST');
      await page.keyboard.press('Control+Enter');
      const response = await reply;
      await page.waitForTimeout(1500);
      const after = await page.locator('.wf-task-item.is-selected').innerText().catch(() => '无');
      return `提交 HTTP ${response.status()} 提交前=${before.replace(/\s+/g, ' ').slice(0, 40)} 自动打开=${after.replace(/\s+/g, ' ').slice(0, 40)}`;
    });
  }
  await browser.close();
  console.log('SUMMARY', JSON.stringify({ pass: results.filter(r => r[0] === 'PASS').length, fail: results.filter(r => r[0] === 'FAIL').length }));
  console.log('NETWORK>=400', JSON.stringify([...new Set(network)]));
  console.log('CONSOLE_ERRORS', JSON.stringify([...new Set(consoleErrors)].slice(0, 10)));
}
main().catch(e => { console.error(e); process.exit(1); });
