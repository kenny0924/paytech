// B 我发起的：菜单入口、进行中 / 已结束（已完成 / 已终止）、当前步骤 · 等待人、右侧小流程图与活动时间线、
// 撤回（终止实例）只对运行中可用、发起流程（启动表单含文件，AC-07）后留在本页并选中新实例。
import { assert, calls, close, open, py, settle, shot, state, step, summary, OPS, SCRATCH } from './lib.mjs';
import { join } from 'node:path';
const S = 'B';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const show = key => JSON.parse(py('show', key).split('[show] ')[1]);
const { page, ctx } = await open('admin', { section: S });

await step(S, '菜单「工作流」下有「我发起的」，与「我的待办」平级', async () => {
  await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1000);
  const items = await page.locator('.ant-menu-sub .ant-menu-item, .ant-menu-item').allInnerTexts();
  const wf = items.map(t => t.trim()).filter(t => ['我的待办', '我发起的', '流程实例', '流程定义', '动作目录'].includes(t));
  assert(wf.includes('我发起的') && wf.indexOf('我发起的') === wf.indexOf('我的待办') + 1, JSON.stringify(wf));
  await page.getByRole('menuitem', { name: '我发起的' }).click(); await settle(page, 1500);
  assert(page.url().includes('/workflow/started'), page.url());
  return `菜单顺序=${wf.join(' / ')} → ${new URL(page.url()).pathname}`;
});

const rowOf = id => page.locator('.wf-started-table tbody tr', { hasText: title(id) });
await step(S, '进行中：页签计数、当前步骤 · 等待人、已用时长；右侧小流程图 + 活动时间线 + 撤回', async () => {
  const tab = (await page.locator('.wf-tabs .wf-tab').first().innerText()).replace(/\s+/g, ' ');
  if (!await rowOf('W1').count()) { await page.getByRole('button', { name: '搜索' }).click(); await page.locator('.wf-started-list input').fill(title('W1')); await page.locator('.wf-started-list input').press('Enter'); await settle(page, 1200); }
  await rowOf('W1').click(); await settle(page, 1500);
  const cells = (await rowOf('W1').locator('td').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const t1 = (await rowOf('T1').count()) ? (await rowOf('T1').locator('td').nth(1).innerText()) : '（不在当前页）';
  const detail = page.locator('.wf-started-detail');
  const canvas = await detail.locator('.react-flow').count();
  const timeline = (await detail.locator('.wf-vtimeline').innerText()).replace(/\s+/g, ' ');
  const withdraw = await detail.getByRole('button', { name: '撤回（终止实例）' }).count();
  const f = await shot(page, 'b-started-running', { both: true });
  assert(/^进行中 \d+$/.test(tab) && /资料准备 · 等待/.test(cells[1]) && canvas === 1 && withdraw === 1, `tab=${tab} cells=${cells} canvas=${canvas}`);
  return `页签=${tab} W1 行=${JSON.stringify(cells)} T1 当前步骤=${t1} 时间线=${timeline.slice(0, 80)} 撤回按钮=1；${f}`;
});

await step(S, '「完整详情」打开只读弹窗（不跳转）', async () => {
  const url = page.url();
  await page.locator('.wf-started-detail').getByRole('button', { name: '完整详情' }).click(); await page.waitForTimeout(1200);
  const ok = await page.locator('.ant-modal:visible .react-flow').count();
  await page.locator('.ant-modal:visible .ant-modal-close').click(); await page.waitForTimeout(300);
  assert(ok === 1 && page.url() === url, 'modal');
  return '弹窗含流程图，URL 不变';
});

await step(S, '撤回（终止实例）W1 → 进入 已结束 / 已终止，且已结束实例不再显示撤回', async () => {
  await page.locator('.wf-started-detail').getByRole('button', { name: '撤回（终止实例）' }).click(); await page.waitForTimeout(500);
  const modal = page.locator('.ant-modal:visible');
  const mtitle = await modal.locator('.ant-modal-title').innerText();
  await modal.locator('textarea').fill('发起人撤回（e2e）');
  const since = Date.now();
  await modal.getByRole('button', { name: '确认终止' }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/termination'), since);
  await page.locator('.wf-tabs .wf-tab', { hasText: '已结束' }).click(); await settle(page, 1000);
  await page.locator('.ant-segmented-item', { hasText: '已终止' }).click(); await settle(page, 1500);
  await rowOf('W1').click(); await settle(page, 1200);
  const detail = (await page.locator('.wf-started-detail').innerText()).replace(/\s+/g, ' ');
  const withdraw = await page.locator('.wf-started-detail').getByRole('button', { name: '撤回（终止实例）' }).count();
  const f1 = await shot(page, 'b-started-terminated', { both: true });
  await page.locator('.ant-segmented-item', { hasText: '已完成' }).click(); await settle(page, 1500);
  const completed = await rowOf('I1').count();
  await rowOf('I1').click().catch(() => {}); await settle(page, 1000);
  const withdraw2 = await page.locator('.wf-started-detail').getByRole('button', { name: '撤回（终止实例）' }).count();
  const f2 = await shot(page, 'b-started-completed');
  assert(posted.some(p => p.endsWith('200')) && show('W1').status === 'TERMINATED' && withdraw === 0 && completed === 1 && withdraw2 === 0, `posted=${posted} withdraw=${withdraw} completed=${completed}`);
  return `弹窗标题=${mtitle} ${posted.join(';')}；已终止详情=${detail.slice(0, 90)}；撤回按钮 已终止=${withdraw} 已完成=${withdraw2}；${f1}, ${f2}`;
});

await step(S, '发起流程（启动表单含文件，AC-07）：三步弹窗 → 发起后留在本页并选中新实例', async () => {
  await page.getByRole('button', { name: /发起流程/ }).click(); await page.waitForTimeout(800);
  const modal = page.locator('.ant-modal:visible');
  await modal.locator('input').first().fill(`V2 多动作 ${st.stamp}`); await page.waitForTimeout(400);
  await modal.locator('.wf-pick', { hasText: `V2 多动作 ${st.stamp}` }).click();
  await modal.getByRole('button', { name: /下一步：填写信息/ }).click(); await settle(page, 1200);
  const newTitle = `V2 LAUNCH ${st.stamp}`;
  await modal.getByLabel('实例标题').fill(newTitle);
  await modal.getByLabel('渠道编码').fill('CH-LAUNCH');
  await modal.locator('input[type=file]').setInputFiles(join(SCRATCH, 'wf-pdf', `v2-${st.stamp}.pdf`));
  await page.waitForTimeout(3000);
  const f1 = await shot(page, 'b-launch-form', { both: true });
  await modal.getByRole('button', { name: /下一步：确认人员/ }).click(); await page.waitForTimeout(800);
  const since = Date.now();
  await modal.getByRole('button', { name: /^发\s*起$/ }).click(); await settle(page, 2000);
  const posted = calls(n => n.method === 'POST' && n.path.endsWith('/workflow/v1/instances'), since);
  const selected = (await page.locator('.wf-started-detail h2').innerText().catch(() => ''));
  const f2 = await shot(page, 'b-launch-done');
  assert(posted.some(p => p.endsWith('201') || p.endsWith('200')) && page.url().includes('/workflow/started') && selected === newTitle, `posted=${posted} url=${page.url()} selected=${selected}`);
  return `${posted.join(';')} 留在 ${new URL(page.url()).pathname} 选中=${selected}；${f1}, ${f2}`;
});
summary(S);
await ctx.close(); await close();
