// 修订 b 补充截图（只读，一次登录）：交易订单号入口下 记账批次 / 会计凭证 / 平台流水 节点的“另有 N 个视图未执行 · 查看完整信息”展开（D-b2 子行模板）。
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const ORIGIN = 'http://localhost:28080';
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const browser = await chromium.launch({ headless: true });
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-CN' })).newPage();
const errors = []; page.on('response', r => { if (r.status() >= 400 && new URL(r.url()).pathname.startsWith('/api/')) errors.push(`${r.status()} ${new URL(r.url()).pathname}`); });
await page.goto(`${ORIGIN}/?lang=zh-CN`);
await page.locator('input:not([type=password])').first().fill(access.match(/账号：(\S+)/)[1]);
await page.locator('input[type=password]').first().fill(access.match(/密码：(\S+)/)[1]);
const login = page.waitForResponse(r => r.url().includes('/auth/v1/login'), { timeout: 20000 }).catch(() => null);
await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
console.log('login', (await login)?.status());
await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
await page.goto(`${ORIGIN}/data-query/joint-query?keyType=ORDER_NO&value=ord_3LOyZii4ct3BgT48vPZhmHbyC302a8&lang=zh-CN`);
await page.waitForTimeout(2500);
await page.waitForFunction(() => !document.querySelector('.jq-summary .ant-spin'), null, { timeout: 60000 }).catch(() => {});
await page.screenshot({ path: join(HERE, 'screens', 'zh-CN-1440-B11-capture-start.png') });
console.log('cards', await page.locator('.jq-card').count(), page.url());
const out = [];
for (const [tag, name] of [['posting', '记账批次'], ['journal', '会计凭证'], ['platform-txn', '平台流水']]) {
  await page.locator('.jq-card', { hasText: name }).first().click(); await page.waitForTimeout(400);
  const line = (await page.locator('.jq-detail .jq-views-line').first().innerText().catch(() => '')).replace(/\s+/g, ' ');
  const button = page.locator('.jq-detail .jq-views-line .ant-btn').first();
  if (await button.count()) { await button.click(); await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(800); }
  const sections = await page.locator('.jq-views .jq-detail-title h3').allInnerTexts();
  await page.screenshot({ path: join(HERE, 'screens', `zh-CN-1440-B11-views-${tag}.png`), fullPage: true });
  out.push({ node: name, line, sections: sections.map(item => item.replace(/\s+/g, ' ')), cards: await page.locator('.jq-card').count() });
}
writeFileSync(join(HERE, 'views-capture.json'), JSON.stringify({ out, errors }, null, 2));
console.log(JSON.stringify({ out, errors }));
await browser.close();
