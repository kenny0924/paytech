// A2 审批（user2 审批 I1「资料准备」）：动作与提交内容 + INLINE PDF 预览、查看完整流程（弹窗不跳转）、退回修改 → 新一轮动作回到待处理；
// admin 第 2 轮带出上一轮内容逐个完成；user2 同意 → 进入 admin「复核」。下载 → ATTACHMENT。
import { assert, calls, close, open, openTask, py, settle, shot, state, step, summary, OPS } from './lib.mjs';
const S = 'A2';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const show = key => JSON.parse(py('show', key).split('[show] ')[1]);
const u = await open('user2', { section: S });
const page = u.page;
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);

await step(S, '审批详情：动作与提交内容（执行人、状态、完成时间、摘要）；打开时无红色；底部 驳回 / 退回修改 / 同意 + 更多', async () => {
  await openTask(page, title('I1'));
  const head = await page.locator('.wf-submissions thead').innerText();
  const rows = (await page.locator('.wf-submissions tbody tr').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const bar = (await page.locator('.wf-actionbar').innerText()).replace(/\s+/g, ' ');
  const red = await page.locator('.wf-detail .wf-danger-text:visible, .wf-detail .ant-alert-error:visible').count();
  const file = await shot(page, 'a2-approval', { both: true });
  assert(rows.length === 3 && /驳\s*回/.test(bar) && /退回修改/.test(bar) && /同\s*意/.test(bar) && /更多/.test(bar) && red === 0, `rows=${rows} bar=${bar} red=${red}`);
  return `表头=${head.replace(/\s+/g, '|')} 行=${JSON.stringify(rows)} 操作栏=${bar}；${file}`;
});

await step(S, '「查看」展开上传动作 → 文件「查看」以 INLINE 签发并 iframe 内嵌 PDF', async () => {
  const row = page.locator('.wf-submissions tbody tr', { hasText: `V2 资质文件 ${st.stamp}` });
  await row.getByRole('button', { name: /查\s*看/ }).click(); await page.waitForTimeout(500);
  const since = Date.now();
  await page.locator('.wf-submission-detail').getByRole('button', { name: /查\s*看/ }).first().click();
  await page.waitForTimeout(3500);
  const grants = calls(n => n.path.includes('/download'), since);
  const iframe = await page.locator('.wf-preview-pane iframe').count();
  const box = iframe ? await page.locator('.wf-preview-pane iframe').boundingBox() : null;
  const file = await shot(page, 'a2-inline-pdf', { both: true });
  assert(grants.some(g => g.includes('disposition=INLINE') && g.endsWith('200')) && iframe === 1, `grants=${grants} iframe=${iframe}`);
  return `${grants.join(';')} iframe=${iframe} ${Math.round(box?.width ?? 0)}x${Math.round(box?.height ?? 0)}；${file}`;
});

await step(S, '下载 → ATTACHMENT 签发，新窗口下载', async () => {
  const since = Date.now();
  const popupP = u.ctx.waitForEvent('page', { timeout: 10000 }).catch(() => null);
  await page.locator('.wf-submission-detail').getByRole('button', { name: /下\s*载/ }).first().click();
  const popup = await popupP;
  const dl = popup ? await popup.waitForEvent('download', { timeout: 8000 }).catch(() => null) : null;
  await page.waitForTimeout(800);
  const grants = calls(n => n.path.includes('/download'), since);
  if (popup) await popup.close().catch(() => {});
  assert(grants.some(g => g.endsWith('200')) && grants.every(g => !g.includes('INLINE')), `grants=${grants}`);
  return `${grants.join(';')}（无 disposition 参数即 ATTACHMENT）新窗口=${!!popup} 下载文件名=${dl?.suggestedFilename() ?? '无'}`;
});

await step(S, '更多 → 查看完整流程：弹窗显示流程图与活动，不跳转', async () => {
  const url = page.url();
  await page.locator('.wf-actionbar').getByRole('button', { name: /更多/ }).click();
  await page.getByRole('menuitem', { name: '查看完整流程' }).click(); await page.waitForTimeout(1500);
  const modal = page.locator('.ant-modal:visible');
  const text = (await modal.innerText()).replace(/\s+/g, ' ');
  const canvas = await modal.locator('.react-flow').count();
  const file = await shot(page, 'a2-full-flow-modal', { both: true });
  await modal.locator('.ant-modal-close').click(); await page.waitForTimeout(400);
  assert(page.url() === url && canvas === 1 && text.includes('活动'), `url变化=${page.url() !== url} canvas=${canvas}`);
  return `弹窗=${text.slice(0, 90)}… URL 不变；${file}`;
});

await step(S, '退回修改：不填意见被拦截；填写后提交 → 新一轮，动作回到待处理', async () => {
  await page.locator('.wf-actionbar').getByRole('button', { name: '退回修改' }).click(); await page.waitForTimeout(400);
  const err = await page.locator('.wf-detail .wf-danger-text').allInnerTexts();
  await page.locator('.wf-detail textarea').fill('请补充区域为欧洲（e2e 退回修改）');
  const since = Date.now();
  await page.locator('.wf-actionbar').getByRole('button', { name: '退回修改' }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/decision'), since);
  const s = show('I1');
  const fill = s.nodes.filter(n => n[0] === 'fill');
  assert(err.some(t => t.includes('必须填写意见')) && posted.some(p => p.endsWith('200')), `err=${err} posted=${posted}`);
  assert(fill.length === 2 && fill[1][1] === 2 && fill[1][2] === 'ACTIVE', JSON.stringify(fill));
  return `空意见提示=${err}；${posted.join(';')}；节点=${JSON.stringify(fill)} 动作=${JSON.stringify(s.actions.slice(-3))}`;
});
summary(S);
await u.ctx.close();

// admin 第 2 轮
const a = await open('admin', { section: S });
const ap = a.page;
await ap.goto(`${OPS}/workflow/my-tasks`); await settle(ap, 1500);
await step(S, 'admin 第 2 轮：退回原因横幅、动作待处理、上一轮内容带出；逐个完成', async () => {
  await openTask(ap, title('I1'));
  const banner = await ap.locator('.wf-revision').innerText();
  const statuses = await ap.locator('.wf-action-row-head .ant-tag:last-child').allInnerTexts();
  const form = ap.locator('.wf-action-row', { hasText: `V2 商户资料 ${st.stamp}` });
  if (!await form.locator('.wf-action-row-body').count()) await form.locator('.wf-chevron').click();
  const carried = await form.getByLabel('商户名称').inputValue();
  await form.locator('.ant-radio-button-wrapper', { hasText: '欧洲' }).click();
  const f1 = await shot(ap, 'a2-round2-executor', { both: true });
  await form.getByRole('button', { name: /完\s*成/ }).click(); await settle(ap, 1500);
  await openTask(ap, title('I1'));
  const up = ap.locator('.wf-action-row', { hasText: `V2 资质文件 ${st.stamp}` });
  if (!await up.locator('.wf-action-row-body').count()) await up.locator('.wf-chevron').click();
  const carriedFiles = (await up.innerText()).replace(/\s+/g, ' ');
  await up.getByRole('button', { name: /完\s*成/ }).click(); await settle(ap, 1500);
  const s = show('I1');
  const fill = s.nodes.filter(n => n[0] === 'fill');
  assert(banner.includes('第 2 轮') && statuses.filter(t => t === '待处理').length === 3 && carried.includes('浏览器商户'), `banner=${banner} statuses=${statuses} carried=${carried}`);
  assert(fill[1][2] === 'APPROVING', JSON.stringify(fill));
  return `横幅=${banner.replace(/\s+/g, ' ')} 状态=${statuses} 带出商户名称=${carried} 上传带出=${/v2-.*\.pdf/.test(carriedFiles)} 节点=${JSON.stringify(fill[1])}；${f1}`;
});
summary(S);
await a.ctx.close();

const u2 = await open('user2', { section: S });
await u2.page.goto(`${OPS}/workflow/my-tasks`); await settle(u2.page, 1500);
await step(S, 'user2 第 2 轮：横幅显示上轮退回原因，提交内容可对比上一轮；同意 → 进入复核', async () => {
  await openTask(u2.page, title('I1'));
  const banner = await u2.page.locator('.wf-revision').innerText().catch(() => '');
  const row = u2.page.locator('.wf-submissions tbody tr', { hasText: `V2 商户资料 ${st.stamp}` });
  await row.getByRole('button', { name: /查\s*看/ }).click(); await u2.page.waitForTimeout(400);
  const compare = await u2.page.locator('.wf-compare').count();
  const f = await shot(u2.page, 'a2-round2-approver', { both: true });
  const since = Date.now();
  await u2.page.locator('.wf-actionbar').getByRole('button', { name: /同\s*意/ }).click(); await settle(u2.page, 1500);
  const posted = calls(n => n.path.includes('/decision'), since);
  const s = show('I1');
  assert(posted.some(p => p.endsWith('200')) && s.nodes.some(n => n[0] === 'review' && n[2] === 'APPROVING'), JSON.stringify(s.nodes));
  return `横幅=${banner.replace(/\s+/g, ' ')} 对比区=${compare} ${posted.join(';')} 节点=${JSON.stringify(s.nodes)}；${f}`;
});
summary(S);
await close();
