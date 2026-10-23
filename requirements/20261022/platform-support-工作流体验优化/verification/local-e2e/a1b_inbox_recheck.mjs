// A1b 修复后复验（a1_inbox_fill.mjs 首轮失败项）：计数（size=10）、超时标记、详情结构（I3）、未通过文件拦截与删除后恢复。
import { assert, close, open, openTask, problem422, settle, shot, state, step, summary, OPS, SCRATCH } from './lib.mjs';
import { join } from 'node:path';
const S = 'A1b';
const st = state();
const { page, ctx } = await open('admin', { section: S });
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);

await step(S, '收件箱计数：待办 N = 全部 N = 填写 + 审批', async () => {
  await page.waitForFunction(() => /审批\s*\d+/.test(document.querySelector('.wf-inbox-tools .ant-segmented')?.textContent ?? ''), null, { timeout: 20000 });
  const tabs = (await page.locator('.wf-page-head .wf-tabs .wf-tab').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const seg = await page.locator('.wf-inbox-tools .ant-segmented-item').allInnerTexts();
  const n = s => Number(s.match(/\d+/)[0]);
  assert(n(seg[0]) === n(seg[1]) + n(seg[2]) && n(tabs[0]) === n(seg[0]) && tabs[1] === '已办', `tabs=${tabs} seg=${seg}`);
  return `tabs=${JSON.stringify(tabs)} 类型=${JSON.stringify(seg)}；` + await shot(page, 'a1-inbox', { both: true });
});

await step(S, '超时标记：浏览器时钟 +20h，旧待办（等待 ≥24h）红色「超时 N 天」，新待办仍为相对时间', async () => {
  const { page: p2, ctx: c2 } = await open('admin', { section: S, clockOffsetMs: 20 * 3600_000 });
  await p2.goto(`${OPS}/workflow/my-tasks`); await settle(p2, 1500);
  await p2.getByRole('button', { name: '搜索' }).first().click();
  await p2.locator('.wf-inbox-search input').fill('E2E'); await p2.locator('.wf-inbox-search input').press('Enter'); await settle(p2, 1500);
  const overdue = await p2.locator('.wf-inbox-item .wf-overdue').allInnerTexts();
  const color = overdue.length ? await p2.locator('.wf-inbox-item .wf-overdue').first().evaluate(el => getComputedStyle(el).color) : '';
  const f1 = await shot(p2, 'a1-overdue-clock+20h');
  await p2.locator('.wf-inbox-search input').fill(`V2 I3 ${st.stamp}`); await p2.locator('.wf-inbox-search input').press('Enter'); await settle(p2, 1500);
  const fresh = await p2.locator('.wf-inbox-item').first().locator('.wf-inbox-time, .wf-overdue').innerText();
  await c2.close();
  assert(overdue.length > 0 && overdue.every(t => /^超时 \d+ (天|小时)$/.test(t)) && !/超时/.test(fresh), `overdue=${overdue} fresh=${fresh}`);
  return `旧待办 ${overdue.length} 项超时（${overdue[0]}，color=${color}）；I3（约 20 小时）显示「${fresh}」；${f1}`;
});

await step(S, 'I3 详情结构：步骤条、背景可折叠、我的任务、操作栏固定在详情底部；打开时无红色', async () => {
  await openTask(page, `V2 I3 ${st.stamp}`);
  const red = await page.locator('.wf-detail .ant-form-item-explain-error:visible, .wf-detail .wf-danger-text:visible, .wf-detail .ant-alert-error:visible').count();
  const steps = (await page.locator('.wf-stepper li').allInnerTexts()).map(s => s.replace(/\n/g, ''));
  const detail = await page.locator('.wf-detail').boundingBox();
  const bar = await page.locator('.wf-actionbar').boundingBox();
  // 滚动详情后操作栏位置不变
  await page.locator('.wf-detail-scroll').evaluate(el => el.scrollTo(0, el.scrollHeight));
  const bar2 = await page.locator('.wf-actionbar').boundingBox();
  const sections = (await page.locator('.wf-detail .wf-section-title').allInnerTexts()).map(t => t.split('\n')[0]);
  const file = await shot(page, 'a1-detail-fill', { both: true });
  assert(red === 0, `红色 ${red}`);
  assert(Math.abs(bar.y + bar.height - (detail.y + detail.height)) < 3 && Math.abs(bar2.y - bar.y) < 1, `bar=${JSON.stringify(bar)} detail=${JSON.stringify(detail)}`);
  return `步骤条=${JSON.stringify(steps)} 区块=${JSON.stringify(sections)} 操作栏贴详情底边（滚动不动）；${file}`;
});

await step(S, 'I3 未通过安全检查（拦截完成请求，返回与 BFF 线上同形的 422 output.files[0]「请检查此处配置」）→ 标红、禁用「完成」；删除后恢复', async () => {
  const r = page.locator('.wf-action-row', { hasText: `V2 资质文件 ${st.stamp}` });
  if (!await r.locator('.wf-action-row-body').count()) await r.locator('.wf-chevron').click();
  await r.locator('input[type=file]').setInputFiles(join(SCRATCH, 'wf-pdf', `v2-${st.stamp}.pdf`));
  await page.waitForTimeout(2500);
  await page.route(/\/action-runs\/\d+\/submission$/, route => route.fulfill(problem422([{ field: 'output.files[0]', message: '请检查此处配置' }])));
  await r.getByRole('button', { name: /完\s*成/ }).click(); await settle(page, 1000);
  await page.unroute(/\/action-runs\/\d+\/submission$/);
  const text = (await r.innerText()).replace(/\s+/g, ' ');
  const disabled = await r.getByRole('button', { name: /完\s*成/ }).isDisabled();
  const file = await shot(page, 'a1-scan-failed-blocked');
  await r.getByRole('button', { name: '移除' }).click(); await page.waitForTimeout(500);
  const after = await r.getByRole('button', { name: /完\s*成/ }).isDisabled();
  assert(text.includes('未通过安全检查，请删除后重新上传') && disabled && !after, `text=${text.slice(0, 200)} disabled=${disabled} afterRemove=${after}`);
  return `422 后：文件标红「未通过安全检查，请删除后重新上传」、完成禁用；移除后完成可用；${file}`;
});

summary(S);
await ctx.close(); await close();
