// A1 我的待办（admin 执行人）：收件箱外观、计数、超时标记（浏览器时钟 +12h）、⚙ / ? 浮层、详情结构、
// I1 多动作逐个完成（必填前端拦截、修改已完成必选、上传 PDF、进入审批后修改隐藏）、I3 未通过文件（拦截 422 演示）。
import { assert, calls, close, network, open, openTask, settle, shot, state, step, summary, OPS, SCRATCH } from './lib.mjs';
import { join } from 'node:path';
const S = 'A1';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const { page, ctx } = await open('admin', { section: S });

await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);

await step(S, '收件箱：一组页签 待办 N / 已办，类型切换 全部/填写/审批 带数量', async () => {
  const tabs = await page.locator('.wf-page-head .wf-tabs .wf-tab').allInnerTexts();
  await page.waitForFunction(() => /审批\s*\d+/.test(document.querySelector('.wf-inbox-tools .ant-segmented')?.textContent ?? ''), null, { timeout: 15000 });
  const seg = await page.locator('.wf-inbox-tools .ant-segmented-item').allInnerTexts();
  const tabGroups = await page.locator('.wf-tabs').count();
  assert(tabs.length === 2 && /^待办\s*\d+$/.test(tabs[0].replace(/\n/g, ' ')) && tabs[1] === '已办', `tabs=${JSON.stringify(tabs)}`);
  assert(seg.length === 3 && /^全部 \d+$/.test(seg[0]) && /^填写 \d+$/.test(seg[1]) && /^审批 \d+$/.test(seg[2]), `seg=${JSON.stringify(seg)}`);
  const n = s => Number(s.match(/\d+/)[0]);
  assert(n(seg[0]) === n(seg[1]) + n(seg[2]) && n(tabs[0]) === n(seg[0]), '计数不一致');
  return `tabs=${JSON.stringify(tabs.map(t => t.replace(/\n/g, ' ')))} 类型=${JSON.stringify(seg)} 页签组=${tabGroups}；` + await shot(page, 'a1-inbox', { both: true });
});

await step(S, '列表项：实例标题在第一行、第二行「流程 · 节点」、无技术键、非超时显示灰色相对时间', async () => {
  const item = page.locator('.wf-inbox-item').first();
  const strong = await item.locator('.wf-inbox-body strong').innerText();
  const second = await item.locator('.wf-inbox-body .muted').innerText();
  const all = await page.locator('.wf-inbox-items').innerText();
  const overdue = await page.locator('.wf-inbox-item .wf-overdue').count();
  const times = await page.locator('.wf-inbox-item .wf-inbox-time').allInnerTexts();
  assert(!/merchantInfo|licenseFiles|执行 ·/.test(all), '列表露出技术键');
  return `首项 第一行=${strong} 第二行=${second} 超时标记=${overdue} 相对时间样例=${JSON.stringify(times.slice(0, 3))}`;
});

await step(S, '无常驻快捷键面板；⚙ 设置浮层与 ? 快捷键浮层', async () => {
  const visibleShortcut = await page.getByText('下一条 / 上一条').isVisible().catch(() => false);
  assert(!visibleShortcut, '快捷键说明常驻');
  await page.getByRole('button', { name: '待办设置' }).click(); await page.waitForTimeout(400);
  const settings = await page.locator('.ant-popover:visible').innerText();
  const f1 = await shot(page, 'a1-settings-popover');
  await page.keyboard.press('Escape'); await page.mouse.click(5, 300); await page.waitForTimeout(300);
  await page.getByRole('button', { name: '键盘快捷键' }).click(); await page.waitForTimeout(400);
  const keys = await page.locator('.ant-popover:visible').last().innerText();
  const f2 = await shot(page, 'a1-shortcuts-popover');
  await page.mouse.click(5, 300); await page.waitForTimeout(300);
  assert(settings.includes('处理完自动打开下一条') && keys.includes('下一条 / 上一条'), '浮层内容不对');
  return `设置=${settings.replace(/\s+/g, ' ').slice(0, 80)} 快捷键=${keys.replace(/\s+/g, ' ').slice(0, 80)}；${f1}, ${f2}`;
});

await step(S, '超时标记：浏览器时钟 +12h 后，等待 ≥24h 的待办显示红色「超时 N 天」，<24h 的仍为相对时间', async () => {
  const { page: p2, ctx: c2 } = await open('admin', { section: S });
  await p2.clock.setFixedTime(new Date(Date.now() + 12 * 3600_000));
  await p2.goto(`${OPS}/workflow/my-tasks`); await settle(p2, 1500);
  const overdue = await p2.locator('.wf-inbox-item .wf-overdue').allInnerTexts();
  const color = overdue.length ? await p2.locator('.wf-inbox-item .wf-overdue').first().evaluate(el => getComputedStyle(el).color) : '';
  const fresh = await p2.locator('.wf-inbox-item', { hasText: title('I1') }).locator('.wf-inbox-time').innerText().catch(() => '（I1 不在首页）');
  const file = await shot(p2, 'a1-overdue-clock+12h');
  await c2.close();
  assert(overdue.length > 0 && overdue.every(t => /^超时 \d+ (天|小时)$/.test(t)), `overdue=${JSON.stringify(overdue)}`);
  return `超时项=${overdue.length}（${overdue[0]}，color=${color}）；I1（刚发起）显示=${fresh}；${file}`;
});

let i1Item;
await step(S, 'I1 详情：头部 + 步骤条、背景（可折叠）、我的任务、底部固定操作栏；打开时无红色校验', async () => {
  i1Item = await openTask(page, title('I1'));
  const steps = await page.locator('.wf-stepper li').allInnerTexts();
  const sections = await page.locator('.wf-detail .wf-section-title').allInnerTexts();
  const red = await page.locator('.wf-detail .ant-form-item-explain-error:visible, .wf-detail .wf-danger-text:visible, .wf-detail .ant-alert-error:visible').count();
  const bar = await page.locator('.wf-actionbar').boundingBox();
  const vp = page.viewportSize();
  const barText = await page.locator('.wf-actionbar').innerText();
  const file = await shot(page, 'a1-detail-fill', { both: true });
  // 背景折叠
  await page.locator('.wf-section', { hasText: '背景' }).getByRole('button', { name: '收起' }).click();
  const collapsed = await page.locator('.wf-section', { hasText: '背景' }).innerText();
  await page.locator('.wf-section', { hasText: '背景' }).getByRole('button', { name: '展开' }).click();
  assert(red === 0, `打开即有 ${red} 处红色`);
  assert(Math.abs(bar.y + bar.height - vp.height) < 4, `操作栏不在底部 bar=${JSON.stringify(bar)}`);
  const order = sections.map(t => t.split('\n')[0]);
  assert(order.indexOf('背景') >= 0 && order.indexOf('背景') < order.findIndex(t => t.startsWith('我的任务')), `顺序=${order}`);
  return `步骤条=${JSON.stringify(steps.map(s => s.replace(/\n/g, '')))} 区块=${JSON.stringify(order)} 操作栏=${barText.replace(/\n/g, ' ')} bottom=${Math.round(bar.y + bar.height)}/${vp.height} 收起后=${collapsed.replace(/\s+/g, ' ').slice(0, 60)} 红色=0；${file}`;
});

const row = name => page.locator('.wf-action-row', { hasText: name });
await step(S, '表单动作：只填一个必填点「完成」→ 前端就地标红「必填」，不发请求', async () => {
  const r = row(`V2 商户资料 ${st.stamp}`);
  if (!await r.locator('.wf-action-row-body').count()) await r.locator('.wf-chevron').click();
  await r.getByLabel('商户名称').fill('浏览器商户');
  const before = Date.now();
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await page.waitForTimeout(800);
  const err = await r.locator('.ant-form-item-explain-error, .wf-danger-text').allInnerTexts();
  const posted = calls(n => n.method === 'POST' && n.path.includes('/submission'), before);
  const file = await shot(page, 'a1-required-inline');
  assert(err.some(t => t.includes('必填')) && posted.length === 0, `err=${JSON.stringify(err)} posted=${posted}`);
  return `字段错误=${JSON.stringify(err)} 提交请求=${posted.length}；${file}`;
});

await step(S, '表单动作：补齐后完成 → 已完成；节点仍 ACTIVE 时可「修改」并再次完成', async () => {
  const r = row(`V2 商户资料 ${st.stamp}`);
  await r.getByLabel('月交易量').fill('88000');
  let since = Date.now();
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1500);
  const first = calls(n => n.path.includes('/submission'), since);
  // 自动下一条可能跳到别的待办；回到 I1
  await openTask(page, title('I1'));
  const r2 = row(`V2 商户资料 ${st.stamp}`);
  const status = await r2.locator('.ant-tag').last().innerText();
  await r2.getByRole('button', { name: '修改' }).click(); await page.waitForTimeout(400);
  await r2.getByLabel('商户名称').fill('浏览器商户（改）');
  since = Date.now();
  await r2.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1500);
  const second = calls(n => n.path.includes('/submission'), since);
  await openTask(page, title('I1'));
  const file = await shot(page, 'a1-form-completed');
  assert(first.some(c => c.endsWith('200')) && second.some(c => c.endsWith('200')) && status === '已完成', `first=${first} status=${status} second=${second}`);
  return `首次 ${first.join(';')} 状态=${status}；修改后 ${second.join(';')}；${file}`;
});

await step(S, '上传动作：浏览器上传 PDF（扫描在后台）后完成 → 节点进入审批；必选「修改」隐藏，可选动作仍可完成', async () => {
  const r = row(`V2 资质文件 ${st.stamp}`);
  if (!await r.locator('.wf-action-row-body').count()) await r.locator('.wf-chevron').click();
  await r.locator('input[type=file]').setInputFiles(join(SCRATCH, 'wf-pdf', `v2-${st.stamp}.pdf`));
  await page.waitForTimeout(2500);
  const hint = await r.locator('.wf-action-foot').innerText();
  const since = Date.now();
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/submission') || n.path.includes('/attachments'), since - 4000);
  await openTask(page, title('I1'));
  const alert = await page.locator('.wf-detail .ant-alert').allInnerTexts();
  const formRow = row(`V2 商户资料 ${st.stamp}`);
  const editVisible = await formRow.getByRole('button', { name: '修改' }).count();
  const optional = await row(`V2 合规确认 ${st.stamp}`).innerText();
  const file = await shot(page, 'a1-approving-locked', { both: true });
  assert(editVisible === 0 && alert.some(t => t.includes('节点已进入审批')), `修改按钮=${editVisible} alert=${alert}`);
  return `上传区提示=${hint.replace(/\s+/g, ' ')}；请求=${posted.join(';')}；提示=${alert.join('|')}；必选修改按钮=${editVisible}；可选行=${optional.replace(/\s+/g, ' ').slice(0, 60)}；${file}`;
});

await step(S, '可选动作在 APPROVING 中仍可完成', async () => {
  const r = row(`V2 合规确认 ${st.stamp}`);
  if (!await r.locator('.wf-action-row-body').count()) await r.locator('.wf-chevron').click();
  await r.locator('textarea').fill('已核对制裁名单');
  const since = Date.now();
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/submission'), since);
  assert(posted.some(c => c.endsWith('200')), posted.join(';'));
  return posted.join(';');
});

await step(S, 'I3 未通过安全检查的文件（拦截完成请求，返回 BFF 实际形态的 422 output.files[0]）→ 标红并禁用「完成」', async () => {
  await openTask(page, title('I3'));
  const r = row(`V2 资质文件 ${st.stamp}`);
  if (!await r.locator('.wf-action-row-body').count()) await r.locator('.wf-chevron').click();
  await r.locator('input[type=file]').setInputFiles(join(SCRATCH, 'wf-pdf', `v2-${st.stamp}.pdf`));
  await page.waitForTimeout(2500);
  // BFF ConfigurationValidationConverter 把远端字段文案统一替换为「请检查此处配置」，这里按实际线上形态构造
  await page.route(/\/action-runs\/\d+\/submission$/, route => route.fulfill({ status: 422, contentType: 'application/problem+json',
    body: JSON.stringify({ type: 'about:blank', title: 'Unprocessable Content', status: 422, detail: '请求字段校验未通过', code: 'VALIDATION_ERROR',
      errors: [{ field: 'output.files[0]', message: '请检查此处配置' }] }) }));
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1000);
  await page.unroute(/\/action-runs\/\d+\/submission$/);
  const text = await r.innerText();
  const disabled = await r.getByRole('button', { name: /^完\s*成$/ }).isDisabled();
  const file = await shot(page, 'a1-scan-failed-blocked');
  assert(text.includes('未通过安全检查') && disabled, `行文字=${text.replace(/\s+/g, ' ').slice(0, 200)} 完成禁用=${disabled}`);
  return `标红并禁用；${file}`;
});

summary(S);
await ctx.close(); await close();
