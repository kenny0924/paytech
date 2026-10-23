// C 动作目录（AC-04）：列表页签计数、被引用列、只读抽屉（预览 + 引用）、新建先选类型（数据查询不可选）、
// 表单编辑页（三栏、1280 下字段 ≥ 240px、添加 / 排序 / 复制、必填、枚举选项、保存 / 放弃、离开确认）、上传类型「至少上传一个」、停用二次确认。
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { assert, calls, close, open, settle, shot, state, step, summary, OPS, SCRATCH } from './lib.mjs';
const S = 'C';
const st = state();
const { page, ctx } = await open('admin', { section: S });
const out = { };

await step(S, '列表：类型页签带数量、表格列（名称 / 类型 / 被引用 / 状态 / 最近修改）、被引用显示「N 个流程」', async () => {
  await page.goto(`${OPS}/workflow/action-templates`); await settle(page, 2000);
  const tabs = (await page.locator('.wf-catalog-tabs .wf-tab').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const head = await page.locator('.wf-catalog-table thead th').allInnerTexts();
  const row = page.locator('.wf-catalog-table tbody tr', { hasText: `V2 商户资料 ${st.stamp}` });
  const cells = (await row.locator('td').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const f = await shot(page, 'c-catalog-list', { both: true });
  assert(tabs[0].startsWith('全部') && head.join('|').includes('被引用') && cells[2] === '1 个流程', `tabs=${tabs} head=${head} cells=${cells}`);
  return `页签=${JSON.stringify(tabs)} 表头=${head.join('|')} V2 商户资料行=${JSON.stringify(cells)}；${f}`;
});

await step(S, '点行 → 右侧只读抽屉：执行人看到的样子 + 被这些流程引用；底部 编辑 与 ⋯', async () => {
  await page.locator('.wf-catalog-table tbody tr', { hasText: `V2 商户资料 ${st.stamp}` }).click(); await settle(page, 1500);
  const drawer = page.locator('.wf-preview-drawer');
  const text = (await drawer.innerText()).replace(/\s+/g, ' ');
  const refs = await drawer.locator('.wf-reference-list li').allInnerTexts();
  const f = await shot(page, 'c-catalog-drawer', { both: true });
  await drawer.locator('.ant-drawer-close').click().catch(() => page.keyboard.press('Escape')); await page.waitForTimeout(400);
  assert(text.includes('执行人看到的样子') && refs.some(r => r.includes(`V2 多动作 ${st.stamp}`)), `refs=${refs}`);
  return `引用=${JSON.stringify(refs.map(r => r.replace(/\s+/g, ' ')))}；${f}`;
});

await step(S, '新建动作 → 先选类型：四类可选，数据查询置灰「即将支持」', async () => {
  await page.getByRole('button', { name: /新建动作/ }).click(); await page.waitForTimeout(600);
  const cards = page.locator('.wf-type-card');
  const info = [];
  for (let i = 0; i < await cards.count(); i++) info.push(`${(await cards.nth(i).locator('strong').innerText())}${await cards.nth(i).isDisabled() ? '(禁用)' : ''}`);
  const soon = await page.locator('.wf-type-card.is-data_query .wf-type-card-soon').innerText().catch(() => '');
  const f = await shot(page, 'c-type-picker', { both: true });
  assert(info.length === 5 && info.filter(t => t.includes('禁用')).length === 1 && info.find(t => t.includes('禁用')).startsWith('数据查询') && soon === '即将支持', JSON.stringify(info));
  return `类型=${info.join('、')}；${f}`;
});

const name = `V2 浏览器表单 ${st.stamp}`;
await step(S, '选「表单」进入新建编辑页：基本信息填写名称，编码自动建议', async () => {
  await page.locator('.wf-type-card', { hasText: '表单' }).first().click();
  await page.getByRole('button', { name: '下一步' }).click(); await settle(page, 1500);
  assert(page.url().includes('/workflow/action-templates/new?type=FORM'), page.url());
  await page.getByPlaceholder('如 商户资料表单').fill(name);
  const code = await page.locator('.wf-tpl-basic input').nth(1).inputValue();
  await page.locator('.wf-tpl-basic textarea').fill('阶段 5 浏览器新建');
  return `URL=${new URL(page.url()).pathname}${new URL(page.url()).search} 编码建议=${code}`;
});

await step(S, '字段设计：三栏；添加 单行文本 / 数字 / 枚举，改名、必填、枚举选项、上移排序、复制；1280 下字段宽 ≥ 240px', async () => {
  await page.locator('.wf-tpl-step', { hasText: '字段设计' }).click(); await page.waitForTimeout(500);
  const palette = page.locator('.wf-designer-palette');
  const label = page.locator('.wf-designer-props').getByPlaceholder('如 公司注册名称');
  await palette.getByRole('button', { name: /单行文本/ }).click(); await label.fill('Company Name');
  await page.locator('.wf-props-switch .ant-switch').click();
  await palette.getByRole('button', { name: /数字/ }).click(); await label.fill('Monthly Volume');
  await page.locator('.wf-props-switch .ant-switch').click();
  await palette.getByRole('button', { name: /枚举/ }).click(); await label.fill('Verdict');
  const opts = page.locator('.wf-designer-props .wf-option-row');
  await opts.nth(0).locator('input').nth(0).fill('PASS'); await opts.nth(0).locator('input').nth(1).fill('合格');
  await page.locator('.wf-designer-props').getByRole('button', { name: /添加选项/ }).click();
  await opts.nth(1).locator('input').nth(0).fill('FAIL'); await opts.nth(1).locator('input').nth(1).fill('不合格');
  await page.locator('.wf-props-switch .ant-switch').click();
  const keyText = await page.locator('.wf-key-value').innerText();
  // 上移：把 Verdict 移到第 2 位
  const fields = page.locator('.wf-canvas-field');
  await fields.nth(2).hover(); await fields.nth(2).getByRole('button', { name: '↑' }).click();
  const order1 = await page.locator('.wf-canvas-field .ant-form-item-label').allInnerTexts();
  // 复制第 1 个字段
  await fields.nth(0).hover(); await fields.nth(0).getByRole('button', { name: '⧉' }).click();
  const order2 = await page.locator('.wf-canvas-field .ant-form-item-label').allInnerTexts();
  // 删除复制出的字段
  await fields.nth(1).hover(); await fields.nth(1).getByRole('button', { name: '✕' }).click();
  const order3 = await page.locator('.wf-canvas-field .ant-form-item-label').allInnerTexts();
  const widths = await fields.evaluateAll(els => els.map(el => Math.round(el.getBoundingClientRect().width)));
  const cols = await page.locator('.wf-designer').evaluate(el => getComputedStyle(el).gridTemplateColumns);
  const dirty = await page.locator('.wf-dirty').innerText().catch(() => '');
  const f = await shot(page, 'c-field-designer', { both: true });
  await page.setViewportSize({ width: 1920, height: 1080 }); await page.waitForTimeout(600);
  const widths1920 = await fields.evaluateAll(els => els.map(el => Math.round(el.getBoundingClientRect().width)));
  await page.setViewportSize({ width: 1280, height: 800 }); await page.waitForTimeout(600);
  out.fieldWidths = { w1280: widths, w1920: widths1920 };
  assert(widths.every(w => w >= 240), `widths=${widths}`);
  assert(order1.join('|').replace(/\s/g, '') === 'CompanyName|Verdict|MonthlyVolume'.replace(/\s/g, '') || order1[1].includes('Verdict'), `order1=${order1}`);
  return `三栏=${cols}；排序后=${order1.map(t => t.trim()).join(' / ')}；复制后=${order2.length} 个；删除后=${order3.map(t => t.trim()).join(' / ')}；字段键=${keyText}；字段宽 1280=${widths} 1920=${widths1920}；${dirty}；${f}`;
});

await step(S, '创建 → 跳到该动作编辑页；修改后「未保存的修改」→ 放弃修改恢复', async () => {
  const since = Date.now();
  await page.getByRole('button', { name: /^创\s*建$/ }).click(); await settle(page, 2000);
  const posted = calls(n => n.method === 'POST' && n.path.endsWith('/action-templates'), since);
  out.formTemplateUrl = new URL(page.url()).pathname;
  await page.locator('.wf-tpl-step', { hasText: '基本信息' }).click();
  const input = page.getByPlaceholder('如 商户资料表单');
  await input.fill(name + '（改）');
  const dirty = await page.locator('.wf-dirty').count();
  await page.getByRole('button', { name: '放弃修改' }).click(); await page.waitForTimeout(300);
  const restored = await input.inputValue();
  assert(posted.some(p => /20[01]$/.test(p)) && /\/workflow\/action-templates\/\d+$/.test(out.formTemplateUrl) && dirty === 1 && restored === name, `posted=${posted} url=${out.formTemplateUrl} dirty=${dirty} restored=${restored}`);
  return `${posted.join(';')} → ${out.formTemplateUrl}；改动后「未保存的修改」显示；放弃后名称=${restored}`;
});

await step(S, '离开确认：有未保存修改时点面包屑「动作目录」弹确认；继续编辑留在本页；保存成功', async () => {
  await page.getByPlaceholder('如 商户资料表单').fill(name + ' v2');
  await page.locator('.wf-crumbs a', { hasText: '动作目录' }).click(); await page.waitForTimeout(600);
  const confirm = await page.locator('.ant-modal-confirm:visible .ant-modal-confirm-title').innerText().catch(() => '');
  const f = await shot(page, 'c-leave-confirm');
  await page.getByRole('button', { name: '继续编辑' }).click(); await page.waitForTimeout(400);
  const stayed = new URL(page.url()).pathname === out.formTemplateUrl;
  const since = Date.now();
  await page.getByRole('button', { name: /^保\s*存$/ }).click(); await settle(page, 1500);
  const put = calls(n => n.method === 'PUT' && n.path.includes('/action-templates/'), since);
  const foot = await page.locator('.wf-tpl-foot').innerText();
  assert(confirm.includes('离开当前动作') && stayed && put.some(p => p.endsWith('200')), `confirm=${confirm} stayed=${stayed} put=${put}`);
  return `确认框=${confirm}；继续编辑后仍在本页；${put.join(';')}；页脚=${foot}；${f}`;
});

await step(S, '上传文件类型：只有说明与「至少上传一个」开关；创建', async () => {
  await page.goto(`${OPS}/workflow/action-templates/new?type=FILE_UPLOAD`); await settle(page, 1500);
  await page.getByPlaceholder('如 商户资料表单').fill(`V2 浏览器上传 ${st.stamp}`);
  await page.locator('.wf-tpl-step', { hasText: '说明' }).click(); await page.waitForTimeout(400);
  const body = (await page.locator('.wf-tpl-body').innerText()).replace(/\s+/g, ' ');
  const sw = page.locator('.wf-tpl-body label.wf-inline', { hasText: '至少上传一个' });
  const before = await sw.locator('.ant-switch').getAttribute('aria-checked');
  if (before !== 'true') await sw.locator('.ant-switch').click();
  const preview = (await page.locator('.wf-preview-card').innerText()).replace(/\s+/g, ' ');
  const f = await shot(page, 'c-upload-editor', { both: true });
  const since = Date.now();
  await page.getByRole('button', { name: /^创\s*建$/ }).click(); await settle(page, 2000);
  const posted = calls(n => n.method === 'POST' && n.path.endsWith('/action-templates'), since);
  out.uploadTemplateUrl = new URL(page.url()).pathname;
  assert(body.includes('至少上传一个') && !/最多|文件类型|数量/.test(body.replace('不限数量与类型', '')) && posted.some(p => /20[01]$/.test(p)), `body=${body.slice(0, 200)} posted=${posted}`);
  return `编辑区=${body.slice(0, 120)}… 预览=${preview.slice(0, 60)}；${posted.join(';')} → ${out.uploadTemplateUrl}；${f}`;
});

await step(S, '停用二次确认：抽屉 ⋯ → 停用 → 确认弹窗（引用数）→ 确认停用 → 已停用', async () => {
  await page.goto(`${OPS}/workflow/action-templates`); await settle(page, 2000);
  await page.locator('.wf-catalog-table tbody tr', { hasText: `V2 浏览器上传 ${st.stamp}` }).click(); await settle(page, 1200);
  await page.locator('.wf-preview-drawer').getByRole('button', { name: '更多操作' }).click();
  await page.getByRole('menuitem', { name: '停用' }).click(); await page.waitForTimeout(600);
  const modal = page.locator('.ant-modal:visible');
  const text = (await modal.innerText()).replace(/\s+/g, ' ');
  const f = await shot(page, 'c-deactivate-confirm');
  const since = Date.now();
  await modal.getByRole('button', { name: '确认停用' }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.endsWith('/status'), since);
  const status = (await page.locator('.wf-catalog-table tbody tr', { hasText: `V2 浏览器上传 ${st.stamp}` }).innerText()).replace(/\s+/g, ' ');
  assert(text.includes('停用「') && posted.some(p => p.endsWith('200')) && status.includes('已停用'), `text=${text} posted=${posted} status=${status}`);
  return `确认框=${text.slice(0, 80)}…；${posted.join(';')}；行=${status}；${f}`;
});
writeFileSync(join(SCRATCH, 'workflow-v2-catalog.json'), JSON.stringify(out));
summary(S);
await ctx.close(); await close();
