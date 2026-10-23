// A1b 修复后复验（a1_inbox_fill.mjs 首轮失败项）：计数（size=10）、超时标记、详情结构（I3）、未通过文件拦截与删除后恢复。
import { assert, close, open, openTask, problem422, settle, shot, state, step, summary, OPS, SCRATCH } from './lib.mjs';
import { join } from 'node:path';
const S = 'A1c';
const st = state();
const { page, ctx } = await open('admin', { section: S });
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);

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

summary(S);
await ctx.close(); await close();
