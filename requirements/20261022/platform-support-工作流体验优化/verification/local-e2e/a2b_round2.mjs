// A2 审批（user2 审批 I1「资料准备」）：动作与提交内容 + INLINE PDF 预览、查看完整流程（弹窗不跳转）、退回修改 → 新一轮动作回到待处理；
// admin 第 2 轮带出上一轮内容逐个完成；user2 同意 → 进入 admin「复核」。下载 → ATTACHMENT。
import { assert, calls, close, open, openTask, py, settle, shot, state, step, summary, OPS } from './lib.mjs';
const S = 'A2b';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const show = key => JSON.parse(py('show', key).split('[show] ')[1]);
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
