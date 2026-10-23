// 第二轮复验 ②：工作流各页面无文本符号（⚙ ? ✎ ⧉ ✕ ↑ ▸ 等），图标为 SVG。
import { assert, close, open, settle, shot, state, step, summary, OPS } from './lib.mjs';
const S = 'R2I';
const st = state();
const { page, ctx } = await open('admin', { section: S });
const pages = { 我发起的: '/workflow/started', 流程实例: '/workflow/instances', 流程定义: '/workflow/definitions', 动作目录: '/workflow/action-templates',
  流程编辑器: `/workflow/definitions/${st.definitionId}`, 动作编辑: `/workflow/action-templates/${st.uploadTemplateId}` };
await step(S, '②工作流各页无文本符号，按钮 / 类型图标为 SVG', async () => {
  const out = [];
  for (const [name, path] of Object.entries(pages)) {
    await page.goto(OPS + path); await settle(page, 2500);
    const text = await page.locator('main, .wf-page, body').first().innerText();
    const glyphs = [...new Set([...text].filter(c => /[←-⇿⌀-⏿①-➿⬀-⯿＋？]/.test(c) && c !== '→'))];
    const svgs = await page.locator('.wf-page svg, .wf-editor svg, .wf-tpl-page svg, main svg').count();
    out.push(`${name}: 符号=${glyphs.join('') || '无'} svg=${svgs}`);
  }
  const f = await shot(page, 'r2-icons-upload-editor');
  assert(out.every(line => line.includes('符号=无')), out.join('；'));
  return out.join('；') + '；' + f;
});
summary(S);
await ctx.close(); await close();
