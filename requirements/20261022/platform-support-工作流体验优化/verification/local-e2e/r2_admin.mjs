// 第二轮复验（admin）：①待办合并与计数、J/K、详情展开顺序、自动下一条；②线性图标；③浏览器内 422 字段文案（真实 BFF 响应）；
// ⑤未通过文件只在文件上标红（拦截演示）；⑨我发起的时间线「你」。截图 screenshots/r2-*。
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';
import { assert, calls, close, open, openTask, problem422, settle, shot, state, step as runStep, summary, HERE, OPS, SCRATCH } from './lib.mjs';
// ONLY=4,7 只跑第 4、7 个步骤（1 起计），用于失败后补跑
const ONLY = process.env.ONLY ? process.env.ONLY.split(',').map(Number) : null;
let stepNo = 0;
const step = (section, name, fn) => (++stepNo, ONLY && !ONLY.includes(stepNo)) ? Promise.resolve(true) : runStep(section, name, fn);
const S = 'R2A';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const expected = process.env.ONLY ? {} : JSON.parse(execFileSync('python3', ['r2_mytasks.py'], { cwd: HERE }).toString().trim().split('\n').pop());
const { page, ctx } = await open('admin', { section: S });
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 2000);
const n = s => Number((s.match(/\d+/) ?? ['-1'])[0]);

await step(S, '①待办合并：多动作节点为一项「填写」带「N 个动作」；待办 / 全部 / 填写 / 审批按合并后列表项计数', async () => {
  await page.waitForFunction(() => /审批\s*\d+/.test(document.querySelector('.wf-inbox-tools .ant-segmented')?.textContent ?? ''), null, { timeout: 15000 });
  const tab = (await page.locator('.wf-page-head .wf-tab').first().innerText()).replace(/\s+/g, ' ');
  const seg = await page.locator('.wf-inbox-tools .ant-segmented-item').allInnerTexts();
  const items = await page.locator('.wf-inbox-item').count();
  const actionIcons = await page.locator('.wf-inbox-item .wf-inbox-icon.is-action').count();
  const approvalIcons = await page.locator('.wf-inbox-item .wf-inbox-icon.is-approval').count();
  const i1 = page.locator('.wf-inbox-item', { hasText: title('I1') });
  const i1Count = await i1.count();
  const i1Text = i1Count ? (await i1.first().innerText()).replace(/\s+/g, ' ') : '';
  const f = await shot(page, 'r2-inbox-grouped', { both: true });
  const counts = { 待办: n(tab), 全部: n(seg[0]), 填写: n(seg[1]), 审批: n(seg[2]), DOM项: items, 填写图标: actionIcons, 审批图标: approvalIcons };
  assert(counts.待办 === expected.all && counts.全部 === expected.all && counts.填写 === expected.action && counts.审批 === expected.approval, `counts=${JSON.stringify(counts)} expected=${JSON.stringify(expected)}`);
  assert(items === expected.all && actionIcons === expected.action && i1Count === 1 && /3 个动作/.test(i1Text), `items=${items} i1=${i1Count} ${i1Text}`);
  return `页面=${JSON.stringify(counts)}；API 按动作逐条 ${expected.raw} 条、合并后 ${expected.all}（填写 ${expected.action} / 审批 ${expected.approval}）；I1 项=「${i1Text}」；${f}`;
});

await step(S, '①详情：列出本节点全部动作（必选在前），默认展开我的第一个待完成必选动作', async () => {
  await openTask(page, title('I1'));
  const rows = page.locator('.wf-action-row');
  const heads = (await rows.locator('.wf-action-row-head').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const openIdx = await rows.evaluateAll(els => els.map((el, i) => el.querySelector('.wf-action-row-body') ? i : -1).filter(i => i >= 0));
  const title2 = (await page.locator('.wf-section-title', { hasText: '我的任务' }).innerText()).replace(/\s+/g, ' ');
  const f = await shot(page, 'r2-detail-grouped', { both: true });
  const reqIdx = heads.map((h, i) => /必选/.test(h) ? i : -1).filter(i => i >= 0);
  const optIdx = heads.map((h, i) => /可选/.test(h) ? i : -1).filter(i => i >= 0);
  assert(heads.length === 3 && Math.max(...reqIdx) < Math.min(...optIdx) && openIdx.length === 1 && openIdx[0] === 0 && /商户资料/.test(heads[0]), `heads=${JSON.stringify(heads)} open=${openIdx}`);
  return `标题=${title2}；动作=${JSON.stringify(heads)}；展开=${openIdx}；${f}`;
});

await step(S, '①J / K 在合并后的列表项之间移动', async () => {
  await page.locator('.wf-inbox-item.is-selected').focus().catch(() => {});
  await page.locator('.wf-page-head h1').click();
  const before = await page.locator('.wf-inbox-item.is-selected strong').innerText();
  await page.keyboard.press('j'); await settle(page, 900);
  const afterJ = await page.locator('.wf-inbox-item.is-selected strong').innerText();
  const indexJ = await page.locator('.wf-inbox-item').evaluateAll(els => els.findIndex(el => el.classList.contains('is-selected')));
  await page.keyboard.press('k'); await settle(page, 900);
  const afterK = await page.locator('.wf-inbox-item.is-selected strong').innerText();
  const indexK = await page.locator('.wf-inbox-item').evaluateAll(els => els.findIndex(el => el.classList.contains('is-selected')));
  assert(afterJ !== before && afterK === before && indexJ === indexK + 1, `before=${before} J=${afterJ}(${indexJ}) K=${afterK}(${indexK})`);
  return `${before} →J→ ${afterJ}（第 ${indexJ + 1} 项）→K→ ${afterK}（第 ${indexK + 1} 项）`;
});

await step(S, '②线性图标：头部搜索 / 设置 / 帮助、列表与动作类型为 SVG 图标，页面无文本符号', async () => {
  await openTask(page, title('I1'));
  const svgOf = async loc => (await loc.count()) ? loc.first().locator('svg').count() : -1;
  const icons = {
    搜索: await svgOf(page.getByRole('button', { name: '搜索' })),
    设置: await svgOf(page.getByRole('button', { name: '待办设置' })),
    帮助: await svgOf(page.getByRole('button', { name: '键盘快捷键' })),
    列表项: await page.locator('.wf-inbox-item .wf-inbox-icon svg').count(),
    动作类型: await page.locator('.wf-action-row .wf-action-icon svg').count(),
    步骤条: await page.locator('.wf-stepper svg').count(),
  };
  const text = await page.locator('.wf-page').innerText();
  const glyphs = [...new Set([...text].filter(c => /[←-⇿⌀-⏿①-➿⬀-⯿＋？]/.test(c) && c !== '→'))];
  assert(Object.values(icons).every(v => v > 0) && glyphs.length === 0, `icons=${JSON.stringify(icons)} glyphs=${glyphs.join('')}`);
  return `SVG 计数=${JSON.stringify(icons)}；文本符号=${glyphs.length ? glyphs.join('') : '无'}`;
});

const row = name => page.locator('.wf-action-row', { hasText: name });
await step(S, '③浏览器 422：页面填写合法值，请求改为 region=XX 发给真实 BFF → 字段下就地显示「不是可选值」', async () => {
  const r = row(`V2 商户资料 ${st.stamp}`);
  if (!await r.locator('.wf-action-row-body').count()) await r.locator('.wf-chevron').click();
  await r.getByLabel('商户名称').fill('浏览器商户 R2');
  await r.getByLabel('月交易量').fill('66000');
  await r.locator('.ant-radio-button-wrapper', { hasText: '海湾' }).click();
  const re = /\/action-runs\/\d+\/submission$/;
  await page.route(re, route => { const body = JSON.parse(route.request().postData()); body.output.region = 'XX'; route.continue({ postData: JSON.stringify(body) }); });
  const since = Date.now();
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1200);
  await page.unroute(re);
  const posted = calls(n => n.path.includes('/submission'), since);
  const errs = (await r.locator('.ant-form-item-explain-error, .wf-danger-text').allInnerTexts()).map(t => t.trim()).filter(Boolean);
  const f = await shot(page, 'r2-inline-422', { both: true });
  assert(posted.some(p => p.endsWith('422')) && errs.some(t => t.includes('不是可选值')), `posted=${posted} errs=${JSON.stringify(errs)}`);
  return `${posted.join(';')} 就地错误=${JSON.stringify(errs)}；${f}`;
});

await step(S, '③浏览器 422：请求去掉 monthlyVolume → 真实 BFF 422 output.monthlyVolume「必填」就地显示（页面已填值，非前端校验）', async () => {
  const r = row(`V2 商户资料 ${st.stamp}`);
  const re = /\/action-runs\/\d+\/submission$/;
  await page.route(re, route => { const body = JSON.parse(route.request().postData()); delete body.output.monthlyVolume; route.continue({ postData: JSON.stringify(body) }); });
  const since = Date.now();
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1200);
  await page.unroute(re);
  const posted = calls(n => n.path.includes('/submission'), since);
  const item = r.locator('.ant-form-item', { hasText: '月交易量' });
  const errs = (await item.locator('.ant-form-item-explain-error').allInnerTexts()).map(t => t.trim());
  assert(posted.some(p => p.endsWith('422')) && errs.some(t => t.includes('必填')), `posted=${posted} errs=${JSON.stringify(errs)}`);
  return `${posted.join(';')} 月交易量字段错误=${JSON.stringify(errs)}`;
});

await step(S, '③浏览器：含数据文案经 BFF 换成「请检查此处填写」后就地显示（真实 API 造不出，拦截返回 BFF 同形 422）', async () => {
  await openTask(page, title('I1'));
  const r = row(`V2 商户资料 ${st.stamp}`);
  if (!await r.getByLabel('月交易量').inputValue().catch(() => '')) { await r.getByLabel('商户名称').fill('浏览器商户 R2'); await r.getByLabel('月交易量').fill('66000'); }
  const re = /\/action-runs\/\d+\/submission$/;
  await page.route(re, route => route.fulfill(problem422([{ field: 'output.merchantName', message: '请检查此处填写' }])));
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1000);
  await page.unroute(re);
  const errs = (await r.locator('.ant-form-item', { hasText: '商户名称' }).locator('.ant-form-item-explain-error').allInnerTexts()).map(t => t.trim());
  assert(errs.some(t => t.includes('请检查此处填写')), JSON.stringify(errs));
  return `商户名称字段错误=${JSON.stringify(errs)}`;
});

await step(S, '①完成表单 → 自动展开下一个我的动作（上传）；上传完成 → 展开可选动作；可选完成 → 自动打开下一列表项', async () => {
  const r = row(`V2 商户资料 ${st.stamp}`);
  let since = Date.now();
  await r.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1500);
  const p1 = calls(n => n.path.includes('/submission'), since);
  const stillI1 = (await page.locator('.wf-detail h2').first().innerText()).includes(title('I1'));
  const openNow = (await page.locator('.wf-action-row:has(.wf-action-row-body) .wf-action-row-head').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const up = row(`V2 资质文件 ${st.stamp}`);
  await up.locator('input[type=file]').setInputFiles(join(SCRATCH, 'wf-pdf', `v2-${st.stamp}.pdf`));
  await page.waitForTimeout(2500);
  since = Date.now();
  await up.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1500);
  const p2 = calls(n => n.path.includes('/submission'), since);
  const openNow2 = (await page.locator('.wf-action-row:has(.wf-action-row-body) .wf-action-row-head').allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const opt = row(`V2 合规确认 ${st.stamp}`);
  await opt.locator('textarea').fill('已核对（R2）');
  const sel = await page.locator('.wf-inbox-item.is-selected strong').innerText();
  since = Date.now();
  await opt.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 2000);
  const p3 = calls(n => n.path.includes('/submission'), since);
  const next = await page.locator('.wf-inbox-item.is-selected strong').innerText().catch(() => '');
  const i1Left = await page.locator('.wf-inbox-item', { hasText: title('I1') }).count();
  const f = await shot(page, 'r2-auto-next');
  assert(p1.some(p => p.endsWith('200')) && stillI1 && openNow.length === 1 && /资质文件/.test(openNow[0]), `p1=${p1} stillI1=${stillI1} open=${openNow}`);
  assert(p2.some(p => p.endsWith('200')) && /合规确认/.test(openNow2[0] ?? ''), `p2=${p2} open2=${openNow2}`);
  assert(p3.some(p => p.endsWith('200')) && sel.includes(title('I1')) && next && next !== sel && i1Left === 0, `p3=${p3} sel=${sel} next=${next} i1Left=${i1Left}`);
  return `表单 ${p1} → 展开=${openNow}；上传 ${p2} → 展开=${openNow2}；可选 ${p3} → 自动打开「${next}」，I1 已移出待办；${f}`;
});

await step(S, '⑤未通过安全检查的文件（拦截返回 BFF 同形 422 output.files[0] 登记文案）→ 只在文件上标红，无重复汇总行', async () => {
  await openTask(page, title('I3'));
  const up = row(`V2 资质文件 ${st.stamp}`);
  if (!await up.locator('.wf-action-row-body').count()) await up.locator('.wf-chevron').click();
  await up.locator('input[type=file]').setInputFiles(join(SCRATCH, 'wf-pdf', `v2-B2-${st.stamp}.pdf`));
  await page.waitForTimeout(2500);
  const re = /\/action-runs\/\d+\/submission$/;
  await page.route(re, route => route.fulfill(problem422([{ field: 'output.files[0]', message: '文件未通过安全检查，请删除后重新上传' }])));
  await up.getByRole('button', { name: /^完\s*成$/ }).click(); await settle(page, 1000);
  await page.unroute(re);
  const marks = (await up.locator('.wf-danger-text, .ant-form-item-explain-error, .ant-alert-error').allInnerTexts()).map(t => t.replace(/\s+/g, ' ').trim()).filter(Boolean);
  const disabled = await up.getByRole('button', { name: /^完\s*成$/ }).isDisabled();
  const f = await shot(page, 'r2-scan-failed-single', { both: true });
  const mentions = marks.filter(t => t.includes('安全检查')).length;
  assert(mentions === 1 && disabled, `marks=${JSON.stringify(marks)} disabled=${disabled}`);
  return `红色文字=${JSON.stringify(marks)}（含「安全检查」${mentions} 处）完成禁用=${disabled}；${f}`;
});

await step(S, '⑨我发起的：时间线中本人显示「你」', async () => {
  await page.goto(`${OPS}/workflow/started`); await settle(page, 1500);
  const rowOf = page.locator('.wf-started-table tbody tr', { hasText: title('I1') });
  if (!await rowOf.count()) { await page.getByRole('button', { name: '搜索' }).click(); await page.locator('.wf-started-list input').fill(title('I1')); await page.locator('.wf-started-list input').press('Enter'); await settle(page, 1200); }
  await rowOf.first().click(); await settle(page, 1500);
  const timeline = (await page.locator('.wf-started-detail .wf-vtimeline').innerText()).replace(/\s+/g, ' ');
  const f = await shot(page, 'r2-started-you', { both: true });
  assert(/你/.test(timeline) && !/Kenny|admin@|236033080844881920/.test(timeline), timeline.slice(0, 200));
  return `时间线=${timeline.slice(0, 200)}；${f}`;
});
summary(S);
await ctx.close(); await close();
