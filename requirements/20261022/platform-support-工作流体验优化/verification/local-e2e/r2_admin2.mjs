// 第二轮复验（admin 第二段）：⑩批量审批 B1—B3、I1 复核同意 → 完成；⑦字段设计器 1280 工具条与步骤导航、动作编辑保存；
// ⑩发布流程新版本；⑧连线插入（两个相近节点之间）→ 下游右移、无小折角、新节点选中（不保存）。
import { assert, calls, close, open, openTask, py, settle, shot, state, step as runStep, summary, OPS } from './lib.mjs';
const ONLY = process.env.ONLY ? process.env.ONLY.split(',').map(Number) : null;
let stepNo = 0;
const step = (section, name, fn) => (++stepNo, ONLY && !ONLY.includes(stepNo)) ? Promise.resolve(true) : runStep(section, name, fn);
const S = 'R2B';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const show = key => JSON.parse(py('show', key).split('[show] ')[1]);
const { page, ctx } = await open('admin', { section: S });
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);
const bar = () => page.locator('.wf-actionbar');

await step(S, '⑩批量审批：进入批量，勾选 B1—B3 → 同意 3 项 → 完成退出', async () => {
  await page.getByRole('button', { name: /批量审批/ }).click(); await settle(page, 2500);
  const nonApproval = await page.locator('.wf-inbox-item .wf-inbox-icon.is-action').count();
  for (const id of ['B1', 'B2', 'B3']) await page.locator('.wf-inbox-item', { hasText: title(id) }).locator('.ant-checkbox-input').check();
  await page.waitForTimeout(1200);
  const btn = bar().getByRole('button', { name: /同意 \d+ 项/ });
  const label = await btn.innerText();
  const f1 = await shot(page, 'r2-batch-mode', { both: true });
  const since = Date.now();
  await btn.click(); await settle(page, 3000);
  const posted = calls(n => n.path.includes('/decision'), since);
  const result = (await bar().innerText()).replace(/\s+/g, ' ');
  await bar().getByRole('button', { name: /完\s*成/ }).click(); await settle(page, 1200);
  const states = ['B1', 'B2', 'B3'].map(id => show(id).status);
  assert(nonApproval === 0 && /3/.test(label) && posted.filter(p => p.endsWith('200')).length === 3 && states.every(x => x === 'COMPLETED'), `label=${label} posted=${posted} states=${states}`);
  return `按钮=${label}；${posted.length} 次决定 200；${result}；B1—B3=${states}；${f1}`;
});

await step(S, '⑩I1 复核同意 → 实例完成（完整一轮：填写 → 完成动作 → 审批 → 复核 → 完成）', async () => {
  await openTask(page, title('I1'));
  const since = Date.now();
  await bar().getByRole('button', { name: /同\s*意/ }).click(); await settle(page, 1500);
  const s = show('I1');
  assert(s.status === 'COMPLETED', s.status);
  return `${calls(n => n.path.includes('/decision'), since).join(';')} 实例=${s.status} 节点=${JSON.stringify(s.nodes)}`;
});

await step(S, '⑦字段设计器 1280：悬停工具条在字段框内、不压上一字段；步骤导航紧凑', async () => {
  await page.goto(`${OPS}/workflow/action-templates/${st.formTemplateId}`); await settle(page, 2000);
  await page.locator('.wf-tpl-step', { hasText: '字段设计' }).click(); await page.waitForTimeout(600);
  const fields = page.locator('.wf-canvas-field');
  await fields.nth(1).hover(); await page.waitForTimeout(300);
  const field = await fields.nth(1).boundingBox();
  const prev = await fields.nth(0).boundingBox();
  const tools = await fields.nth(1).locator('.wf-canvas-tools').boundingBox();
  const label = await fields.nth(1).locator('.ant-form-item-label').boundingBox();
  const nav = await page.locator('.wf-tpl-steps').boundingBox();
  const toolSvgs = await fields.nth(1).locator('.wf-canvas-tools svg').count();
  const f = await shot(page, 'r2-field-designer-hover');
  await page.setViewportSize({ width: 1920, height: 1080 }); await page.waitForTimeout(600);
  await fields.nth(1).hover(); await page.waitForTimeout(300);
  const nav1920 = await page.locator('.wf-tpl-steps').boundingBox();
  const f2 = await shot(page, 'r2-field-designer-hover');
  await page.setViewportSize({ width: 1280, height: 800 }); await page.waitForTimeout(500);
  const inside = tools.x >= field.x && tools.y >= field.y - 0.5 && tools.x + tools.width <= field.x + field.width + 0.5 && tools.y + tools.height <= field.y + field.height + 0.5;
  const noOverlapPrev = tools.y >= prev.y + prev.height - 0.5 || prev.x + prev.width <= tools.x;
  const labelClear = !label || label.x + label.width <= tools.x + 0.5 || label.y + label.height <= tools.y;
  assert(inside && noOverlapPrev && toolSvgs === 4 && nav.width <= 140, `field=${JSON.stringify(field)} tools=${JSON.stringify(tools)} prev=${JSON.stringify(prev)} nav=${nav.width}`);
  return `字段 y=${Math.round(field.y)}–${Math.round(field.y + field.height)}，工具条 y=${Math.round(tools.y)}–${Math.round(tools.y + tools.height)} x=${Math.round(tools.x)}–${Math.round(tools.x + tools.width)}（在框内=${inside}，上一字段底=${Math.round(prev.y + prev.height)}，不重叠=${noOverlapPrev}，标签不被遮=${labelClear}）；图标 ${toolSvgs} 个；步骤导航宽 1280=${Math.round(nav.width)} 1920=${Math.round(nav1920.width)}；${f}, ${f2}`;
});

await step(S, '⑩动作编辑保存：改说明 → 保存 → PUT 200、未保存标记消失', async () => {
  await page.locator('.wf-tpl-step', { hasText: '基本信息' }).click(); await page.waitForTimeout(300);
  await page.locator('.wf-tpl-basic textarea').fill(`体验优化 e2e（R2 保存 ${Date.now() % 100000}）`);
  const dirty = await page.locator('.wf-dirty').count();
  const since = Date.now();
  await page.getByRole('button', { name: /^保\s*存$/ }).click(); await settle(page, 1500);
  const put = calls(n => n.method === 'PUT' && n.path.includes('/action-templates/'), since);
  const dirtyAfter = await page.locator('.wf-dirty').count();
  const f = await shot(page, 'r2-template-saved');
  assert(dirty === 1 && put.some(p => p.endsWith('200')) && dirtyAfter === 0, `dirty=${dirty} put=${put} after=${dirtyAfter}`);
  return `${put.join(';')} 未保存标记 ${dirty}→${dirtyAfter}；${f}`;
});

await step(S, '⑩发布流程新版本：改「复核」名称 → 发布确认（变更摘要）→ 确认发布 200', async () => {
  await page.goto(`${OPS}/workflow/definitions/${st.definitionId}`); await settle(page, 4000);
  const tpl = calls(n => n.path.includes('/action-templates/') && n.status === 429);
  await page.locator('.react-flow__node', { hasText: '复核' }).first().click(); await page.waitForTimeout(400);
  await page.locator('.wf-editor-panel .ant-tabs-tab', { hasText: '基本' }).click();
  await page.locator('.wf-editor-panel input').first().fill('复核（R2）');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /^发\s*布$/ }).click(); await settle(page, 2500);
  const modal = page.locator('.ant-modal:visible');
  const text = (await modal.innerText()).replace(/\s+/g, ' ');
  const f1 = await shot(page, 'r2-publish-summary', { both: true });
  const since = Date.now();
  await modal.getByRole('button', { name: '确认发布' }).click(); await settle(page, 2500);
  const posted = calls(n => n.path.endsWith('/publication'), since);
  const pill = await page.locator('.wf-editor-head .wf-status-pill').innerText().catch(() => '');
  assert(/变更摘要/.test(text) && /复核/.test(text) && posted.some(p => p.endsWith('200')) && tpl.length === 0, `text=${text.slice(0, 300)} posted=${posted}`);
  return `弹窗=${text.slice(0, 220)}…；${posted.join(';')} 状态=${pill}；${f1}`;
});

/** 连线路径的折角：路径点的纵向跨度在 (0, 12) px 之间视为小折角。 */
const edgeShapes = () => page.locator('.react-flow__edge path.react-flow__edge-path').evaluateAll(paths => paths.map(p => {
  const nums = (p.getAttribute('d') ?? '').match(/-?\d+(\.\d+)?/g)?.map(Number) ?? [];
  const ys = nums.filter((_, i) => i % 2 === 1);
  return { span: Math.round((Math.max(...ys) - Math.min(...ys)) * 10) / 10, d: (p.getAttribute('d') ?? '').slice(0, 80) };
}));
await step(S, '⑧同一行两个相近任务（资料准备 → 复核，间距 280）之间插入任务节点：下游右移、新连线无折角、新节点选中（不保存）', async () => {
  await page.goto(`${OPS}/workflow/definitions/${st.definitionId}`); await settle(page, 3500);
  const box = async name => page.locator('.react-flow__node', { hasText: name }).first().boundingBox();
  const before = await edgeShapes();
  const s0 = await box('资料准备'); const fill0 = await box('复核'); const rev0 = await box('结束'); const end0 = rev0;
  const gap0 = Math.round(fill0.x - (s0.x + s0.width));
  const midX = (s0.x + s0.width + fill0.x) / 2; const midY = s0.y + 26;
  await page.mouse.move(midX - 10, midY); await page.mouse.move(midX, midY, { steps: 5 }); await page.waitForTimeout(500);
  await page.locator('.wf-edge-insert.is-visible button').click(); await page.waitForTimeout(300);
  await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item', { hasText: '任务节点' }).click(); await page.waitForTimeout(1000);
  const head = (await page.locator('.wf-editor-panel .wf-panel-head').innerText()).replace(/\s+/g, ' ');
  const selected = await page.locator('.react-flow__node.selected').count();
  const selText = (await page.locator('.react-flow__node.selected').first().innerText()).replace(/\s+/g, ' ');
  await page.keyboard.press('Escape'); await page.waitForTimeout(500);
  const s1 = await box('资料准备'); const fill1 = await box('复核'); const rev1 = await box('结束'); const end1 = rev1;
  const shapes = await edgeShapes();
  // 新节点两侧连线：插入后新增的一条 + 原连线（改指向新节点）
  const kinked = list => list.filter(e => e.span > 0 && e.span < 12).length;
  const kinks = kinked(shapes) > kinked(before) ? shapes.filter(e => e.span > 0 && e.span < 12) : [];
  const f = await shot(page, 'r2-insert-on-edge', { both: true });
  const shift = v => Math.round(v);
  assert(selected === 1 && /任务/.test(head) && !/→/.test(head), `head=${head} selected=${selected}`);
  assert(fill1.x - fill0.x > 20 && Math.abs((rev1.x - rev0.x) - (fill1.x - fill0.x)) < 2 && Math.abs((end1.x - end0.x) - (fill1.x - fill0.x)) < 2 && Math.abs(s1.x - s0.x) < 2, `shift fill=${fill1.x - fill0.x} rev=${rev1.x - rev0.x} end=${end1.x - end0.x}`);
  assert(kinks.length === 0, `kinks=${JSON.stringify(kinks)}`);
  return `原间距 ${gap0}px；面板=${head}；选中=${selText}；右移 复核 ${shift(fill1.x - fill0.x)} / 结束 ${shift(rev1.x - rev0.x)}（屏幕 px，资料准备不动）；连线纵向跨度 插入前=${JSON.stringify(before.map(e => e.span))} 插入后=${JSON.stringify(shapes.map(e => e.span))}（11 为种子数据开始 / 结束与任务行本就错开 11px）；${f}`;
});
summary(S);
await ctx.close(); await close();
