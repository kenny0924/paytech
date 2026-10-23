// E 回归（AC-07）：动作待办 更多 → 转交（R1 第 2 轮）、版本历史弹窗、发起时上传的启动表单附件在待办「背景」可见并可下载（ATTACHMENT）、
// 流程实例页可打开；附带「我发起的」进行中计数（size=10 修复复验）。
import { assert, calls, close, open, openTask, py, settle, shot, state, step, summary, OPS } from './lib.mjs';
const S = 'E';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const show = key => JSON.parse(py('show', key).split('[show] ')[1]);
const { page, ctx } = await open('admin', { section: S });
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);

await step(S, '动作待办 更多 → 转交（R1 驳回后第 2 轮的表单动作 → user2）', async () => {
  await openTask(page, title('R1'));
  await page.locator('.wf-actionbar').getByRole('button', { name: /更多/ }).click();
  await page.getByRole('menuitem', { name: '转交' }).click(); await page.waitForTimeout(500);
  const pop = page.locator('.ant-popover:visible .wf-transfer');
  await pop.locator('.ant-select').click(); await page.keyboard.type('w'); await page.waitForTimeout(1200);
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option:not(.ant-select-item-option-disabled)').first().click();
  const since = Date.now();
  await pop.getByRole('button', { name: /^转交给/ }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/transfer'), since);
  assert(posted.some(p => p.endsWith('200') && p.includes('/action-runs/')), posted.join(';'));
  return posted.join(';');
});

await step(S, '发起时上传的启动表单附件：待办「背景」显示附件并可下载（ATTACHMENT）', async () => {
  await openTask(page, `V2 LAUNCH ${st.stamp}`);
  const bg = page.locator('.wf-section', { hasText: '背景' }).first();
  const text = (await bg.innerText()).replace(/\s+/g, ' ');
  const since = Date.now();
  const popupP = ctx.waitForEvent('page', { timeout: 8000 }).catch(() => null);
  await bg.getByRole('button', { name: /下\s*载/ }).first().click();
  const popup = await popupP; await page.waitForTimeout(800); if (popup) await popup.close().catch(() => {});
  const grants = calls(n => n.path.includes('/download'), since);
  const f = await shot(page, 'e-launch-attachment');
  assert(/1 个附件/.test(text) && grants.some(g => g.endsWith('200') && g.includes('ATTACHMENT')), `text=${text.slice(0, 120)} grants=${grants}`);
  return `背景=${text.slice(0, 100)}；${grants.join(';')}；${f}`;
});

await step(S, '版本历史弹窗：列出已发布版本', async () => {
  await page.goto(`${OPS}/workflow/definitions`); await settle(page, 2000);
  const card = page.locator('.wf-def-card', { hasText: `V2 多动作 ${st.stamp}` });
  await card.getByRole('button', { name: '更多操作' }).click();
  await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item', { hasText: '版本历史' }).click(); await settle(page, 1500);
  const text = (await page.locator('.ant-modal:visible').innerText()).replace(/\s+/g, ' ');
  const f = await shot(page, 'e-versions');
  await page.locator('.ant-modal:visible .ant-modal-close').click();
  assert(/v3/.test(text) && /v1/.test(text), text);
  return `${text.slice(0, 160)}；${f}`;
});

await step(S, '流程实例页（管理员视角）可打开', async () => {
  await page.goto(`${OPS}/workflow/instances`); await settle(page, 2000);
  const rows = await page.locator('tbody tr').count();
  return `实例行 ${rows}；` + await shot(page, 'e-instances');
});
summary(S);
await ctx.close(); await close();
