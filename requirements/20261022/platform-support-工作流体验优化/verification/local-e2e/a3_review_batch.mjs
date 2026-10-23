// A3 admin「复核」审批：R1 驳回（回到资料准备新一轮）、T1 更多 → 转交给 user2、批量审批（显式进入 / 只列可批量 / 同意 N 项 / 退出恢复原选中）、
// I1 普通同意 → 实例完成；已办列表与只读详情。
import { assert, calls, close, open, openTask, py, settle, shot, state, step, summary, OPS } from './lib.mjs';
const S = 'A3';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const show = key => JSON.parse(py('show', key).split('[show] ')[1]);
const { page, ctx } = await open('admin', { section: S });
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);
const bar = () => page.locator('.wf-actionbar');

await step(S, 'R1 驳回：意见必填；驳回后回到「资料准备」第 2 轮', async () => {
  await openTask(page, title('R1'));
  const f = await shot(page, 'a3-review-pure-approval', { both: true });
  const returnDisabled = await bar().getByRole('button', { name: '退回修改' }).isDisabled();
  await page.locator('.wf-detail textarea').fill('材料与合同不一致，驳回到资料准备（e2e）');
  const since = Date.now();
  await bar().getByRole('button', { name: /驳\s*回/ }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/decision'), since);
  const s = show('R1');
  assert(posted.some(p => p.endsWith('200')) && s.nodes.some(n => n[0] === 'fill' && n[1] === 2 && n[2] === 'ACTIVE'), JSON.stringify(s.nodes));
  return `纯审批节点「退回修改」禁用=${returnDisabled}；${posted.join(';')} 节点=${JSON.stringify(s.nodes)}；${f}`;
});

await step(S, 'T1 更多 → 转交给 user2', async () => {
  await openTask(page, title('T1'));
  await bar().getByRole('button', { name: /更多/ }).click();
  await page.getByRole('menuitem', { name: '转交' }).click(); await page.waitForTimeout(500);
  const pop = page.locator('.ant-popover:visible .wf-transfer');
  await pop.locator('.ant-select').click();
  await page.keyboard.type('w'); await page.waitForTimeout(1200);
  const option = page.locator('.ant-select-dropdown:visible .ant-select-item-option:not(.ant-select-item-option-disabled)').first();
  const who = await option.innerText();
  await option.click();
  await pop.locator('textarea').fill('请 user2 复核（e2e 转交）');
  const f = await shot(page, 'a3-transfer-popover');
  const since = Date.now();
  await pop.getByRole('button', { name: /^转交给/ }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/transfer'), since);
  const s = show('T1');
  const approvals = s.approvals.slice(-2);
  assert(posted.some(p => p.endsWith('200')), posted.join(';'));
  return `转交给 ${who.replace(/\s+/g, ' ')}；${posted.join(';')} 审批=${JSON.stringify(approvals)}；${f}`;
});

let selectedBefore;
await step(S, '批量审批：显式进入，只列可批量项，勾选 B1—B3 同意 3 项，完成后退出恢复原选中项', async () => {
  await openTask(page, title('I1'));
  selectedBefore = new URL(page.url()).searchParams.get('task');
  await page.getByRole('button', { name: /批量审批/ }).click(); await settle(page, 2500);
  const strip = (await page.locator('.wf-batch-strip').innerText()).replace(/\s+/g, ' ');
  const note = (await page.locator('.wf-batch-note').innerText()).replace(/\s+/g, ' ');
  const listed = await page.locator('.wf-inbox-item .wf-inbox-body strong').allInnerTexts();
  const nonApproval = await page.locator('.wf-inbox-item .wf-inbox-icon.is-action').count();
  for (const id of ['B1', 'B2', 'B3']) await page.locator('.wf-inbox-item', { hasText: title(id) }).locator('.ant-checkbox-input').check();
  await page.waitForTimeout(800);
  const f1 = await shot(page, 'a3-batch-mode', { both: true });
  const btn = page.locator('.wf-actionbar').getByRole('button', { name: /同意 \d+ 项/ });
  const label = await btn.innerText();
  const since = Date.now();
  await btn.click(); await settle(page, 3000);
  const posted = calls(n => n.path.includes('/decision'), since);
  const result = (await page.locator('.wf-actionbar').innerText()).replace(/\s+/g, ' ');
  const f2 = await shot(page, 'a3-batch-done');
  await page.locator('.wf-actionbar').getByRole('button', { name: /完\s*成/ }).click(); await settle(page, 1200);
  const after = new URL(page.url()).searchParams.get('task');
  const stripGone = await page.locator('.wf-batch-strip').count();
  const states = ['B1', 'B2', 'B3'].map(id => show(id).status);
  assert(nonApproval === 0 && label.includes('3') && posted.filter(p => p.endsWith('200')).length === 3, `nonApproval=${nonApproval} label=${label} posted=${posted}`);
  assert(after === selectedBefore && stripGone === 0 && states.every(x => x === 'COMPLETED'), `before=${selectedBefore} after=${after} states=${states}`);
  return `进入=${strip}；${note}；列出=${listed.length} 项（${listed.slice(0, 4).join('、')}…）；按钮=${label}；${posted.length} 次决定 200；结果=${result}；退出后选中恢复=${after === selectedBefore}；B1—B3=${states}；${f1}, ${f2}`;
});

await step(S, 'I1 复核同意 → 实例完成', async () => {
  await openTask(page, title('I1'));
  const since = Date.now();
  await bar().getByRole('button', { name: /同\s*意/ }).click(); await settle(page, 1500);
  const s = show('I1');
  assert(s.status === 'COMPLETED', s.status);
  return `${calls(n => n.path.includes('/decision'), since).join(';')} 实例=${s.status}`;
});

await step(S, '已办：同风格列表（结果标签 + 实例状态），右侧只读（我的处理、背景、后续进展）', async () => {
  await page.locator('.wf-page-head .wf-tab', { hasText: '已办' }).click(); await settle(page, 1500);
  const items = (await page.locator('.wf-inbox-item').allInnerTexts()).slice(0, 5).map(t => t.replace(/\s+/g, ' '));
  await page.locator('.wf-inbox-item', { hasText: title('R1') }).first().click(); await settle(page, 1500);
  const sections = (await page.locator('.wf-detail .wf-section-title').allInnerTexts()).map(t => t.split('\n')[0]);
  const buttons = await page.locator('.wf-detail').getByRole('button', { name: /同\s*意|驳\s*回|完\s*成/ }).count();
  const mine = (await page.locator('.wf-handled-box').innerText()).replace(/\s+/g, ' ');
  const f = await shot(page, 'a3-handled', { both: true });
  assert(items.length > 0 && sections.includes('我的处理') && buttons === 0, `sections=${sections} buttons=${buttons}`);
  return `列表=${JSON.stringify(items.slice(0, 3))} 区块=${JSON.stringify(sections)} 我的处理=${mine.slice(0, 80)} 操作按钮=0；${f}`;
});
summary(S);
await ctx.close(); await close();
