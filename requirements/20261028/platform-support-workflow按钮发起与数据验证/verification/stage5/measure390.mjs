// 390 宽横向溢出定位：列出右边界超出视口的元素（只读）。用法：node measure390.mjs <path>...
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const s = Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8').split('\n')
  .filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 390, height: 844 } });
const page = await context.newPage();
await page.goto('http://127.0.0.1:18311/?lang=zh-CN');
await page.locator('input:not([type=password])').first().fill(s.E2E_ADMIN_EMAIL);
await page.locator('input[type=password]').first().fill(s.E2E_ADMIN_PASSWORD);
await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
for (const path of process.argv.slice(2)) {
  await page.goto('http://127.0.0.1:18311' + path); await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(1500);
  if (process.env.CLICK) { await page.locator(process.env.CLICK).first().click().catch(() => {}); await page.waitForTimeout(1200); }
  const result = await page.evaluate(() => {
    const vw = document.documentElement.clientWidth;
    const over = [...document.querySelectorAll('body *')].filter(el => { const r = el.getBoundingClientRect(); return r.width && r.right > vw + 1; })
      .filter(el => !el.parentElement || el.parentElement.getBoundingClientRect().right <= vw + 1)
      .slice(0, 8).map(el => `${el.tagName.toLowerCase()}.${String(el.className).split(' ').slice(0, 3).join('.')} right=${Math.round(el.getBoundingClientRect().right)}`);
    return { scrollWidth: document.documentElement.scrollWidth, over };
  });
  console.log(path, JSON.stringify(result));
}
await browser.close();
