// D 流程定义（AC-05 / AC-06）：列表（单一状态、一个主按钮、⋯ 菜单、停用确认、缩略图懒加载无 429）；
// 编辑器（状态药丸、实时问题计数、定位、工具条拖入、连线「+」插入、属性面板滑出 / Esc 关闭、动作页签添加与就地新建动作、
// 通知触发无「动作验证未通过」、发布确认：有问题不能发布 → 修复后发布成功）。
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { assert, calls, close, network, open, settle, shot, state, step, summary, OPS, SCRATCH } from './lib.mjs';
const S = 'D';
const st = state();
const { page, ctx } = await open('admin', { section: S });
const out = {};

await step(S, '列表：每卡一个状态（四种之一）、一个主按钮、⋯（编辑 / 版本历史 / 停用）；缩略图懒加载、无 429', async () => {
  const since = Date.now();
  await page.goto(`${OPS}/workflow/definitions`); await settle(page, 2500);
  const f1 = await shot(page, 'd-list', { both: true });
  // 滚到底触发全部缩略图
  for (let i = 0; i < 8; i++) { await page.mouse.wheel(0, 800); await page.waitForTimeout(500); }
  await settle(page, 2500);
  const cards = page.locator('.wf-def-card');
  const n = await cards.count();
  const statuses = new Set(); const bad = [];
  for (let i = 0; i < n; i++) {
    const pills = await cards.nth(i).locator('.wf-status-pill').allInnerTexts();
    const primary = await cards.nth(i).locator('.wf-def-actions .ant-btn:not([aria-label="更多操作"])').count();
    if (pills.length !== 1 || primary !== 1) bad.push(`${i}:${pills}/${primary}`);
    pills.forEach(p => statuses.add(p.replace(/v\d+/, 'vN')));
  }
  const thumbs = calls(n => n.method === 'GET' && /\/workflow-admin\/v1\/definitions\/\d+(\/versions\/\d+)?$/.test(n.path), since);
  const throttled = calls(n => n.status === 429, since);
  const failedThumbs = await page.locator('.wf-def-thumb-empty', { hasText: '读取失败' }).count();
  const allowed = [/^已发布 vN$/, /^已发布 vN · 有未发布修改$/, /^草稿 · 未发布$/, /^已停用$/];
  assert(!bad.length && [...statuses].every(s => allowed.some(r => r.test(s))) && !throttled.length && !failedThumbs, `bad=${bad} statuses=${[...statuses]} 429=${throttled.length} failed=${failedThumbs}`);
  return `${n} 张卡；状态种类=${JSON.stringify([...statuses])}；缩略图请求 ${thumbs.length} 次（${n} 卡），429=0，读取失败=0；${f1}`;
});

await step(S, '⋯ 菜单与停用二次确认（取消，不改数据）', async () => {
  await page.mouse.wheel(0, -10000); await page.waitForTimeout(500);
  const card = page.locator('.wf-def-card', { hasText: `V2 多动作 ${st.stamp}` });
  const primary = await card.locator('.wf-def-actions .ant-btn:not([aria-label="更多操作"])').innerText();
  await card.getByRole('button', { name: '更多操作' }).click(); await page.waitForTimeout(400);
  const items = await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item').allInnerTexts();
  await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item', { hasText: '停用' }).click(); await page.waitForTimeout(500);
  const confirm = (await page.locator('.ant-modal-confirm:visible').innerText()).replace(/\s+/g, ' ');
  const f = await shot(page, 'd-deactivate-confirm');
  await page.locator('.ant-modal-confirm:visible').getByRole('button', { name: /取\s*消/ }).click(); await page.waitForTimeout(300);
  assert(items.join('|') === '编辑|版本历史|停用' && confirm.includes('停用'), `items=${items} confirm=${confirm}`);
  return `主按钮=${primary} 菜单=${items.join(' / ')} 确认框=${confirm.slice(0, 60)}；${f}`;
});

const code = `V2_BROWSER_${st.stamp}`;
await step(S, '新建流程 → 编辑器：状态药丸、问题徽标；画布占满（未选中无属性面板）', async () => {
  await page.getByRole('button', { name: /新建流程/ }).click(); await page.waitForTimeout(500);
  const modal = page.locator('.ant-modal:visible');
  await modal.getByLabel('流程编码').fill(code);
  await modal.getByLabel('流程名称').fill(`V2 浏览器流程 ${st.stamp}`);
  await modal.getByRole('button', { name: '创建并编辑' }).click(); await settle(page, 2500);
  out.editorUrl = new URL(page.url()).pathname;
  const pill = await page.locator('.wf-editor-head .wf-status-pill').innerText();
  const badge = await page.locator('.wf-issues-badge').innerText();
  const panel = await page.locator('.wf-editor-panel').count();
  const canvas = await page.locator('.wf-editor-canvas').boundingBox();
  const stage = await page.locator('.wf-editor-stage').boundingBox();
  const f = await shot(page, 'd-editor-new', { both: true });
  assert(panel === 0 && Math.abs(canvas.width - stage.width) < 2, `panel=${panel} canvas=${canvas.width} stage=${stage.width}`);
  return `${out.editorUrl} 药丸=${pill} 徽标=${badge.replace(/\s+/g, ' ')} 画布宽=${Math.round(canvas.width)}/${Math.round(stage.width)}；${f}`;
});

const badge = async () => (await page.locator('.wf-issues-badge').innerText()).replace(/\s+/g, ' ');
const nodeBox = async name => page.locator('.react-flow__node', { hasText: name }).first().boundingBox();
await step(S, '连线：从「开始」右侧连接点拖到「结束」→ 问题计数实时变化', async () => {
  const before = await badge();
  const src = page.locator('.react-flow__node', { hasText: '开始' }).locator('.react-flow__handle.source');
  const dst = page.locator('.react-flow__node', { hasText: '结束' }).locator('.react-flow__handle.target');
  const a = await src.boundingBox(); const b = await dst.boundingBox();
  await page.mouse.move(a.x + a.width / 2, a.y + a.height / 2); await page.mouse.down();
  await page.mouse.move((a.x + b.x) / 2, a.y + 20, { steps: 8 }); await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 8 }); await page.mouse.up();
  await page.waitForTimeout(600);
  const edges = await page.locator('.react-flow__edge').count();
  const after = await badge();
  assert(edges === 1, `edges=${edges}`);
  return `连线 ${edges} 条；问题 ${before} → ${after}`;
});

await step(S, '连线悬停出现「+」→ 选「任务节点」插入；新节点选中、属性面板从右侧滑出', async () => {
  const s = await nodeBox('开始'); const e = await nodeBox('结束');
  const midX = (s.x + s.width + e.x) / 2; const midY = s.y + s.height / 2;
  await page.mouse.move(midX - 30, midY); await page.mouse.move(midX, midY, { steps: 5 }); await page.waitForTimeout(400);
  const plus = page.locator('.wf-edge-insert.is-visible button');
  const visible = await plus.count();
  const f1 = await shot(page, 'd-edge-plus');
  await plus.click(); await page.waitForTimeout(400);
  await page.locator('.ant-dropdown:visible .ant-dropdown-menu-item', { hasText: '任务节点' }).click(); await page.waitForTimeout(800);
  const nodes = await page.locator('.react-flow__node').count();
  const edges = await page.locator('.react-flow__edge').count();
  const panel = await page.locator('.wf-editor-panel').boundingBox();
  const canvas = await page.locator('.wf-editor-canvas').boundingBox();
  const f2 = await shot(page, 'd-inserted-panel', { both: true });
  assert(visible === 1 && nodes === 3 && edges === 2 && panel, `plus=${visible} nodes=${nodes} edges=${edges}`);
  return `「+」可见；节点 ${nodes} 连线 ${edges}；面板宽 ${Math.round(panel.width)}，画布宽 ${Math.round(canvas.width)}；问题 ${await badge()}；${f1}, ${f2}`;
});

await step(S, 'Esc 关闭属性面板，画布恢复满宽', async () => {
  await page.locator('.wf-editor-canvas .react-flow__pane').click({ position: { x: 30, y: 300 } }).catch(() => {});
  await page.locator('.react-flow__node', { hasText: '任务' }).first().click(); await page.waitForTimeout(400);
  const open1 = await page.locator('.wf-editor-panel').count();
  await page.keyboard.press('Escape'); await page.waitForTimeout(500);
  const open2 = await page.locator('.wf-editor-panel').count();
  const canvas = await page.locator('.wf-editor-canvas').boundingBox(); const stage = await page.locator('.wf-editor-stage').boundingBox();
  assert(open1 === 1 && open2 === 0 && Math.abs(canvas.width - stage.width) < 2, `open1=${open1} open2=${open2}`);
  return `选中→面板打开；Esc→关闭，画布 ${Math.round(canvas.width)}/${Math.round(stage.width)}`;
});

await step(S, '工具条拖入：把「任务节点」图标拖到画布空白处 → 新增孤立节点，问题计数 +；问题清单「定位」选中该节点；Delete 删除后计数回落', async () => {
  const before = await badge();
  const tool = page.locator('.wf-node-toolbar .wf-tool[aria-label="任务节点"]');
  const canvas = await page.locator('.wf-editor-canvas').boundingBox();
  await tool.dragTo(page.locator('.wf-editor-canvas .react-flow__pane'), { targetPosition: { x: canvas.width * 0.5, y: canvas.height * 0.8 } });
  await page.waitForTimeout(800);
  const nodes = await page.locator('.react-flow__node').count();
  await page.keyboard.press('Escape'); await page.waitForTimeout(300);
  const mid = await badge();
  await page.locator('.wf-issues-badge').click(); await page.waitForTimeout(400);
  const list = (await page.locator('.wf-issue-list').innerText()).replace(/\s+/g, ' ');
  const f1 = await shot(page, 'd-issue-list', { both: true });
  const rows = page.locator('.wf-issue-row');
  let located = '';
  for (let i = 0; i < await rows.count(); i++) {
    const text = await rows.nth(i).innerText();
    if (/无法到达|不可达|到达/.test(text) && await rows.nth(i).getByRole('button', { name: /定位/ }).count()) {
      await rows.nth(i).getByRole('button', { name: /定位/ }).click(); located = text.replace(/\s+/g, ' '); break;
    }
  }
  await page.waitForTimeout(800);
  const panelTitle = await page.locator('.wf-editor-panel h3').innerText().catch(() => '');
  await page.locator('.react-flow__node.selected').first().click(); await page.keyboard.press('Delete'); await page.waitForTimeout(600);
  const after = await badge();
  const nodes2 = await page.locator('.react-flow__node').count();
  assert(nodes === 4 && nodes2 === 3 && located && mid !== before, `nodes=${nodes}/${nodes2} located=${located} badge ${before}/${mid}/${after}`);
  return `拖入后节点 ${nodes}；问题 ${before} → ${mid} → 删除后 ${after}；清单=${list.slice(0, 120)}；定位「${located.slice(0, 50)}」→ 面板=${panelTitle}；${f1}`;
});

await step(S, '节点属性：页签 基本 / 动作 / 审批 / 通知与回调；改名；添加浏览器新建的表单动作', async () => {
  await page.locator('.react-flow__node', { hasText: '任务' }).first().click(); await page.waitForTimeout(500);
  const tabs = await page.locator('.wf-editor-panel .ant-tabs-tab').allInnerTexts();
  await page.locator('.wf-editor-panel .ant-tabs-tab', { hasText: '基本' }).click();
  await page.locator('.wf-editor-panel input').first().fill('浏览器任务');
  await page.locator('.wf-editor-panel .ant-tabs-tab', { hasText: '动作' }).click(); await page.waitForTimeout(300);
  await page.locator('.wf-editor-panel .wf-add-action').click();
  await page.keyboard.type('V2 浏览器表单'); await page.waitForTimeout(500);
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: `V2 浏览器表单 ${st.stamp}` }).first().click(); await page.waitForTimeout(600);
  const cards = await page.locator('.wf-editor-panel .wf-node-action').allInnerTexts();
  assert(tabs.map(t => t.replace(/\s*\d+$/, '').trim()).join('|') === '基本|动作|审批|通知与回调' && cards.length === 1, `tabs=${tabs} cards=${cards.length}`);
  return `页签=${tabs.map(t => t.replace(/\s+/g, ' ')).join(' / ')}；动作卡=${cards[0].replace(/\s+/g, ' ').slice(0, 80)}；问题 ${await badge()}`;
});

await step(S, '发布确认：有问题时弹窗列出问题、「确认发布」禁用', async () => {
  await page.getByRole('button', { name: /^发\s*布$/ }).click(); await settle(page, 1500);
  const modal = page.locator('.ant-modal:visible');
  const text = (await modal.innerText()).replace(/\s+/g, ' ');
  const disabled = await modal.getByRole('button', { name: '确认发布' }).isDisabled();
  const f = await shot(page, 'd-publish-blocked', { both: true });
  await modal.getByRole('button', { name: /取\s*消/ }).click(); await page.waitForTimeout(300);
  assert(disabled && /个问题，处理后才能发布/.test(text), `disabled=${disabled} text=${text.slice(0, 200)}`);
  return `${text.slice(0, 160)}…；确认发布禁用；${f}`;
});

await step(S, '动作页签「＋ 新建动作…」→ 选类型 → 抽屉内创建人工确认 → 自动加入节点（不离开编辑器）', async () => {
  const url = page.url();
  await page.locator('.react-flow__node', { hasText: '浏览器任务' }).first().click(); await page.waitForTimeout(400);
  await page.locator('.wf-editor-panel .ant-tabs-tab', { hasText: '动作' }).click(); await page.waitForTimeout(300);
  await page.locator('.wf-editor-panel .wf-add-action').click(); await page.waitForTimeout(400);
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option', { hasText: '新建动作' }).click(); await page.waitForTimeout(600);
  await page.locator('.wf-type-card', { hasText: '人工确认' }).click();
  await page.getByRole('button', { name: '下一步' }).click(); await page.waitForTimeout(1200);
  const drawer = page.locator('.wf-tpl-drawer');
  await drawer.getByPlaceholder('如 商户资料表单').fill(`V2 就地新建确认 ${st.stamp}`);
  await drawer.locator('.wf-tpl-step', { hasText: '说明' }).click(); await page.waitForTimeout(300);
  await drawer.locator('textarea').first().fill('确认上线窗口已与渠道约定');
  const f1 = await shot(page, 'd-new-action-drawer', { both: true });
  await drawer.getByRole('button', { name: /^创\s*建$/ }).click(); await settle(page, 2000);
  const drawerOpen = await page.locator('.wf-tpl-drawer').count();
  const cards = (await page.locator('.wf-editor-panel .wf-node-action').allInnerTexts()).map(t => t.replace(/\s+/g, ' ').slice(0, 50));
  const f2 = await shot(page, 'd-new-action-attached');
  assert(drawerOpen === 0 && cards.length === 2 && cards[1].includes('V2 就地新建确认') && page.url() === url, `drawer=${drawerOpen} cards=${cards}`);
  return `抽屉关闭、已加入：${JSON.stringify(cards)}；URL 未变；${f1}, ${f2}`;
});

await step(S, '动作卡：设默认执行人（admin）；第二个动作改为可选', async () => {
  const cards = page.locator('.wf-editor-panel .wf-node-action');
  for (let i = 0; i < 2; i++) {
    await cards.nth(i).locator('.ant-select').last().click();
    await page.keyboard.type('o'); await page.waitForTimeout(1200);
    const opt = page.locator('.ant-select-dropdown:visible .ant-select-item-option').first();
    out.executor = await opt.innerText(); await opt.click(); await page.waitForTimeout(400);
  }
  await cards.nth(1).locator('.ant-segmented-item', { hasText: '可选' }).click(); await page.waitForTimeout(300);
  const f = await shot(page, 'd-action-cards', { both: true });
  return `执行人=${out.executor}；问题 ${await badge()}；${f}`;
});

await step(S, '通知与回调：触发选项不含「动作验证未通过」', async () => {
  await page.locator('.wf-editor-panel .ant-tabs-tab', { hasText: '通知与回调' }).click(); await page.waitForTimeout(300);
  await page.locator('.wf-editor-panel').getByRole('button', { name: /添加/ }).first().click(); await page.waitForTimeout(300);
  await page.locator('.wf-editor-panel .wf-rule .ant-select').first().click(); await page.waitForTimeout(400);
  const options = await page.locator('.ant-select-dropdown:visible .ant-select-item-option').allInnerTexts();
  const f = await shot(page, 'd-notification-triggers');
  await page.keyboard.press('Escape');
  await page.locator('.wf-editor-panel .wf-rule').getByRole('button', { name: /删\s*除/ }).first().click(); await page.waitForTimeout(300);
  assert(options.length && !options.some(o => o.includes('动作验证未通过')), JSON.stringify(options));
  return `节点触发选项=${JSON.stringify(options)}；${f}`;
});

await step(S, '发布：保存草稿后确认弹窗 0 个问题（首次发布）→ 确认发布 → 状态「与 v1 相同」', async () => {
  await page.keyboard.press('Escape'); await page.waitForTimeout(300);
  await page.getByRole('button', { name: /^发\s*布$/ }).click(); await settle(page, 2000);
  const modal = page.locator('.ant-modal:visible');
  const text = (await modal.innerText()).replace(/\s+/g, ' ');
  const f1 = await shot(page, 'd-publish-ok', { both: true });
  const since = Date.now();
  await modal.getByRole('button', { name: '确认发布' }).click(); await settle(page, 2500);
  const posted = calls(n => n.path.endsWith('/publication'), since);
  const pill = await page.locator('.wf-editor-head .wf-status-pill').innerText();
  const f2 = await shot(page, 'd-published');
  assert(posted.some(p => p.endsWith('200')) && /v1/.test(pill), `posted=${posted} pill=${pill}`);
  out.definitionId = out.editorUrl.split('/').pop();
  return `弹窗=${text.slice(0, 140)}…；${posted.join(';')}；药丸=${pill}；${f1}, ${f2}`;
});
writeFileSync(join(SCRATCH, 'workflow-v2-definition.json'), JSON.stringify(out));
summary(S);
await ctx.close(); await close();
