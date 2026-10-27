// 国旗 SVG → PNG（本机 Playwright 无头 Chromium，deviceScaleFactor 1）。由 flags.py convert 调用，不连任何环境。
// 用法：node render_png.mjs <svg 目录> <png 目录> <宽> <高> <国家代码,…>
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const [svgDir, pngDir, w, h, codes] = process.argv.slice(2);
const width = Number(w), height = Number(h);
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const browser = await chromium.launch({ headless: true });
const page = await (await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 1, offline: true })).newPage();
for (const cc of codes.split(',')) {
  const svg = readFileSync(join(svgDir, cc.toLowerCase() + '.svg'));
  const src = 'data:image/svg+xml;base64,' + svg.toString('base64');
  await page.setContent(`<!doctype html><html><body style="margin:0;background:transparent">`
    + `<img id="f" src="${src}" width="${width}" height="${height}" style="display:block"></body></html>`);
  await page.waitForFunction(() => { const i = document.getElementById('f'); return i.complete && i.naturalWidth > 0; });
  writeFileSync(join(pngDir, cc + '.png'), await page.locator('#f').screenshot({ omitBackground: true, type: 'png' }));
}
await browser.close();
console.log(`rendered ${codes.split(',').length}`);
