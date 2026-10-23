// D2：新建流程初始徽标（修复后应显示本地检查问题）；D1 再发布（变更摘要 + 运行中实例提示）；
// 从流程定义卡片主按钮「发起」新流程 v1，并在我的待办中完成浏览器新建的表单动作，实例走到完成。
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { assert, calls, close, open, openTask, py, settle, shot, state, step, summary, OPS, SCRATCH } from './lib.mjs';
const S = 'D2';
const st = state();
const def = JSON.parse(readFileSync(join(SCRATCH, 'workflow-v2-definition.json'), 'utf8'));
const { page, ctx } = await open('admin', { section: S });

await step(S, 'D1 再发布：编辑器读取 3 个引用模板无 429（修复复验）；改节点名 → 发布确认显示变更摘要与运行中实例提示 → 发布新版本', async () => {
  const since0 = Date.now();
  await page.goto(`${OPS}/workflow/definitions/${st.definitionId}`); await settle(page, 4000);
  const tpl = calls(n => n.path.includes('/action-templates/'), since0);
  await page.locator('.react-flow__node', { hasText: '资料准备' }).first().click(); await page.waitForTimeout(400);
  await page.locator('.wf-editor-panel .ant-tabs-tab', { hasText: '动作' }).click(); await page.waitForTimeout(300);
  const cardNames = (await page.locator('.wf-editor-panel .wf-node-action strong').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  assert(!tpl.some(c => c.endsWith('429')) && cardNames.length === 3 && !cardNames.some(n => n.includes('读取中')), `tpl=${tpl} cards=${cardNames}`);
  await page.keyboard.press('Escape');
  await page.locator('.react-flow__node', { hasText: '复核' }).first().click(); await page.waitForTimeout(400);
  await page.locator('.wf-editor-panel .ant-tabs-tab', { hasText: '基本' }).click();
  await page.locator('.wf-editor-panel input').first().fill('复核（v3）');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /^发\s*布$/ }).click(); await settle(page, 2500);
  const modal = page.locator('.ant-modal:visible');
  const text = (await modal.innerText()).replace(/\s+/g, ' ');
  const f1 = await shot(page, 'd2-publish-summary', { both: true });
  const since = Date.now();
  await modal.getByRole('button', { name: '确认发布' }).click(); await settle(page, 2500);
  const posted = calls(n => n.path.endsWith('/publication'), since);
  const pill = await page.locator('.wf-editor-head .wf-status-pill').innerText();
  assert(/变更摘要 与 v\d 相比/.test(text) && /复核/.test(text) && /个实例继续按 v\d 运行/.test(text) && posted.some(p => p.endsWith('200')), `text=${text}`);
  return `模板读取=${tpl.join(';')} 动作卡=${cardNames.join('、')}；弹窗=${text.slice(0, 260)}…；${posted.join(';')} 药丸=${pill}；${f1}`;
});

const title = `V2 BROWSER-FLOW-2 ${st.stamp}`;
let launchedId = '';
await step(S, '流程定义卡片主按钮「发起」→ 发起新流程 v1 实例', async () => {
  await page.goto(`${OPS}/workflow/definitions`); await settle(page, 2000);
  const card = page.locator('.wf-def-card', { hasText: `V2 浏览器流程 ${st.stamp}` });
  const status = await card.locator('.wf-status-pill').innerText();
  await card.locator('.wf-def-actions .ant-btn:not([aria-label="更多操作"])').click(); await page.waitForTimeout(1200);
  const modal = page.locator('.ant-modal:visible');
  await modal.getByLabel('实例标题').fill(title);
  await modal.getByRole('button', { name: /下一步：确认人员/ }).click(); await page.waitForTimeout(600);
  const since = Date.now();
  await modal.getByRole('button', { name: /^发\s*起$/ }).click(); await settle(page, 2500);
  const posted = calls(n => n.method === 'POST' && n.path.endsWith('/workflow/v1/instances'), since);
  launchedId = new URL(page.url()).pathname.split('/').pop();
  assert(posted.some(p => p.endsWith('200')), posted.join(';'));
  return `卡片状态=${status}；${posted.join(';')} → ${new URL(page.url()).pathname}`;
});

await step(S, '我的待办完成浏览器新建的表单动作（枚举 Verdict 选「合格」）→ 无审批人自动放行 → 实例完成', async () => {
  await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);
  await openTask(page, title);
  const row = page.locator('.wf-action-row', { hasText: `V2 浏览器表单 ${st.stamp}` });
  if (!await row.locator('.wf-action-row-body').count()) await row.locator('.wf-chevron').click();
  await row.getByLabel('Company Name').fill('Browser Co');
  await row.locator('.ant-radio-button-wrapper', { hasText: /^合格$/ }).click();
  await row.getByLabel('Monthly Volume').fill('1234.5');
  const f = await shot(page, 'd2-browser-form-task', { both: true });
  const since = Date.now();
  await row.getByRole('button', { name: /完\s*成/ }).click(); await settle(page, 2000);
  const posted = calls(n => n.path.includes('/submission'), since);
  const done = JSON.parse(py('show', launchedId).split('[show] ')[1]);
  assert(posted.some(p => p.endsWith('200')) && done.status === 'COMPLETED', `${posted} ${JSON.stringify(done)}`);
  return `${posted.join(';')}；实例 ${launchedId} ${done.status} 节点=${JSON.stringify(done.nodes)} 动作=${JSON.stringify(done.actions)}；${f}`;
});
summary(S);
await ctx.close(); await close();
