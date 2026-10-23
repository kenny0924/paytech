// D3 修复复验：连线「+」插入节点后，属性面板显示新节点（而非原连线）。只改本地画布，不保存草稿。
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { assert, close, open, settle, shot, step, summary, OPS, SCRATCH } from './lib.mjs';
const S = 'D3';
const def = JSON.parse(readFileSync(join(SCRATCH, 'workflow-v2-definition.json'), 'utf8'));
const { page, ctx } = await open('admin', { section: S });
await step(S, '连线「+」插入排他网关 → 新节点被选中、属性面板显示该网关', async () => {
  await page.goto(`${OPS}${def.editorUrl}`); await settle(page, 3000);
  const t = await page.locator('.react-flow__node', { hasText: '浏览器任务' }).first().boundingBox();
  const e = await page.locator('.react-flow__node', { hasText: '结束' }).first().boundingBox();
  const midX = (t.x + t.width + e.x) / 2; const midY = t.y + t.height / 2;
  await page.mouse.move(midX - 20, midY); await page.mouse.move(midX, midY, { steps: 5 }); await page.waitForTimeout(400);
  await page.locator('.wf-edge-insert.is-visible button').click(); await page.waitForTimeout(300);
  await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item', { hasText: '排他网关' }).click(); await page.waitForTimeout(800);
  const head = (await page.locator('.wf-editor-panel .wf-panel-head').innerText()).replace(/\s+/g, ' ');
  const selected = await page.locator('.react-flow__node.selected').count();
  const f = await shot(page, 'd3-insert-selects-node', { both: true });
  assert(/排他网关/.test(head) && !/→/.test(head) && selected === 1, `head=${head} selected=${selected}`);
  return `面板=${head}；${f}`;
});
summary(S);
await ctx.close(); await close();
