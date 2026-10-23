// 第二轮复验（user2 审批）：⑥「动作与提交内容」文件卡片 + 预览弹窗 INLINE PDF；未通过安全检查卡片（改写详情响应演示）；
// ⑩同意 I1（→ admin 复核）、退回修改 I2（→ 资料准备第 2 轮）。
import { assert, calls, close, open, openTask, py, settle, shot, state, step as runStep, summary, OPS } from './lib.mjs';
// ONLY=1,2 只跑指定步骤；INST=I3 文件卡片步骤改看该实例（I1 已同意后补跑用）
const ONLY = process.env.ONLY ? process.env.ONLY.split(',').map(Number) : null;
const INST = process.env.INST ?? 'I1';
let stepNo = 0;
const step = (section, name, fn) => (++stepNo, ONLY && !ONLY.includes(stepNo)) ? Promise.resolve(true) : runStep(section, name, fn);
const S = 'R2U';
const st = state();
const title = id => `V2 ${id} ${st.stamp}`;
const show = key => JSON.parse(py('show', key).split('[show] ')[1]);
const u = await open('user2', { section: S });
const page = u.page;
await page.goto(`${OPS}/workflow/my-tasks`); await settle(page, 1500);
const uploadRow = () => page.locator('.wf-submissions tbody tr', { hasText: `V2 资质文件 ${st.stamp}` }).first();

await step(S, '⑥展开上传动作 → 文件卡片（名称、大小、安全检查、预览）', async () => {
  await openTask(page, title(INST));
  await uploadRow().getByRole('button', { name: /查\s*看/ }).click(); await page.waitForTimeout(600);
  const cards = page.locator('.wf-submission-detail .wf-file-card');
  const texts = (await cards.allInnerTexts()).map(t => t.replace(/\s+/g, ' '));
  const preview = await cards.first().getByRole('button', { name: /预\s*览/ }).count();
  const f = await shot(page, 'r2-file-cards', { both: true });
  assert(texts.length === 1 && /\.pdf/.test(texts[0]) && /\d+(\.\d+)? ?(B|KB|MB)/.test(texts[0]) && preview === 1, `cards=${JSON.stringify(texts)} preview=${preview}`);
  return `卡片=${JSON.stringify(texts)} 预览按钮=${preview}；${f}`;
});

await step(S, '⑥「预览」→ 弹窗内 INLINE 预览，PDF 渲染', async () => {
  const since = Date.now();
  await page.locator('.wf-submission-detail .wf-file-card').first().getByRole('button', { name: /预\s*览/ }).click();
  await page.waitForTimeout(3500);
  const modal = page.locator('.ant-modal:visible');
  const grants = calls(n => n.path.includes('/download'), since);
  const iframe = modal.locator('iframe');
  const count = await iframe.count();
  const box = count ? await iframe.boundingBox() : null;
  const frame = count ? await iframe.elementHandle().then(h => h.contentFrame()) : null;
  const frameUrlOk = frame ? /^https?:/.test(frame.url()) : false;
  const embed = frame ? await frame.locator('embed, pdf-viewer, #viewer').count().catch(() => 0) : 0;
  const mTitle = await modal.locator('.ant-modal-title').innerText().catch(() => '');
  const f = await shot(page, 'r2-file-preview-modal', { both: true });
  await modal.locator('.ant-modal-close').click(); await page.waitForTimeout(400);
  assert(grants.some(g => g.includes('disposition=INLINE') && g.endsWith('200')) && count === 1 && box && box.height > 200, `grants=${grants} iframe=${count} box=${JSON.stringify(box)}`);
  return `弹窗标题=${mTitle} ${grants.join(';')} iframe=${Math.round(box.width)}x${Math.round(box.height)} 内嵌查看器元素=${embed} 帧地址可用=${frameUrlOk}；${f}`;
});

await step(S, '⑥未通过安全检查的文件卡片（改写实例详情响应中该文件 scanStatus=REJECTED 演示）→「未通过安全检查 · 不可下载」、无预览', async () => {
  const re = new RegExp(`/workflow/v1/instances/${st[INST]}$`);
  await page.route(re, async route => {
    const res = await route.fetch(); const body = await res.json();
    for (const file of body.files ?? []) file.scanStatus = 'REJECTED';
    await route.fulfill({ response: res, body: JSON.stringify(body) });
  });
  await page.reload(); await settle(page, 2000);
  await openTask(page, title(INST));
  await uploadRow().getByRole('button', { name: /查\s*看/ }).click(); await page.waitForTimeout(600);
  const card = page.locator('.wf-submission-detail .wf-file-card').first();
  const text = (await card.innerText()).replace(/\s+/g, ' ');
  const preview = await card.getByRole('button', { name: /预\s*览/ }).count();
  const cls = await card.getAttribute('class');
  const f = await shot(page, 'r2-file-card-failed', { both: true });
  await page.unroute(re);
  assert(/未通过安全检查 · 不可下载/.test(text) && preview === 0 && cls.includes('is-scan-failed'), `text=${text} preview=${preview}`);
  return `卡片=${text} 预览按钮=0；${f}`;
});

await step(S, '⑩user2 同意 I1「资料准备」→ 进入 admin「复核」', async () => {
  await page.reload(); await settle(page, 1500);
  await openTask(page, title('I1'));
  const since = Date.now();
  await page.locator('.wf-actionbar').getByRole('button', { name: /同\s*意/ }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/decision'), since);
  const s = show('I1');
  assert(posted.some(p => p.endsWith('200')) && s.nodes.some(n => n[0] === 'review' && n[2] === 'APPROVING'), `${posted} ${JSON.stringify(s.nodes)}`);
  return `${posted.join(';')} 节点=${JSON.stringify(s.nodes)}`;
});

await step(S, '⑩退回修改 I2：意见必填；提交后「资料准备」第 2 轮，动作回到待处理', async () => {
  await openTask(page, title('I2'));
  await page.locator('.wf-actionbar').getByRole('button', { name: '退回修改' }).click(); await page.waitForTimeout(400);
  const err = await page.locator('.wf-detail .wf-danger-text').allInnerTexts();
  await page.locator('.wf-detail textarea').fill('请补充区域（R2 退回修改）');
  const since = Date.now();
  await page.locator('.wf-actionbar').getByRole('button', { name: '退回修改' }).click(); await settle(page, 1500);
  const posted = calls(n => n.path.includes('/decision'), since);
  const s = show('I2');
  const fill = s.nodes.filter(n => n[0] === 'fill');
  assert(err.some(t => t.includes('必须填写意见')) && posted.some(p => p.endsWith('200')) && fill.length === 2 && fill[1][2] === 'ACTIVE', `err=${err} posted=${posted} fill=${JSON.stringify(fill)}`);
  return `空意见提示=${err}；${posted.join(';')}；节点=${JSON.stringify(fill)}`;
});
summary(S);
await u.ctx.close(); await close();
