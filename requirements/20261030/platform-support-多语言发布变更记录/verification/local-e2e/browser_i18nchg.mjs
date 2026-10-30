// 阶段 5 浏览器检查：发布记录页（含差异）与发布弹窗（预检摘要），1440 / 390，zh-CN 与 en。只读：打开弹窗后取消，不发布。
// 运行：node browser_i18nchg.mjs <截图目录>；默认经本机 Vite 18316（代理到运营入口 18444）。
// mor-test：I18NCHG_ORIGIN 指向运营入口，凭据经 I18NCHG_ADMIN_EMAIL / I18NCHG_ADMIN_PASSWORD 传入（不设时读本机 e2e secrets.env）。
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { homedir } from 'node:os';
import { join } from 'node:path';

const OUT = process.argv[2]; mkdirSync(OUT, { recursive: true });
const ORIGIN = process.env.I18NCHG_ORIGIN || 'http://127.0.0.1:18316';
const secrets = process.env.I18NCHG_ADMIN_EMAIL
  ? { E2E_ADMIN_EMAIL: process.env.I18NCHG_ADMIN_EMAIL, E2E_ADMIN_PASSWORD: process.env.I18NCHG_ADMIN_PASSWORD }
  : Object.fromEntries(readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env', 'utf8')
    .split('\n').filter(l => /^[A-Z0-9_]+=/.test(l)).map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const browser = await chromium.launch({ headless: true });
const result = [];
for (const [width, height] of [[1440, 900], [390, 844]]) for (const locale of ['zh-CN', 'en']) {
  const tag = `${width}-${locale}`;
  const ctx = await browser.newContext({ viewport: { width, height }, locale, ignoreHTTPSErrors: true });
  const page = await ctx.newPage();
  const errors = []; const slow = [];
  page.on('pageerror', e => errors.push(String(e).slice(0, 200)));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text().slice(0, 200)); });
  page.on('requestfinished', async req => { if (/release-precheck|\/diff/.test(req.url())) { const t = req.timing(); slow.push({ url: req.url().replace(/.*applications\//, ''), ms: Math.round(t.responseEnd) }); } });
  await page.goto(`${ORIGIN}/?lang=${locale}`);
  await page.waitForLoadState('networkidle');
  await page.locator('input:not([type=password])').first().fill(secrets.E2E_ADMIN_EMAIL);
  await page.locator('input[type=password]').first().fill(secrets.E2E_ADMIN_PASSWORD);
  await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
  await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
  await page.waitForLoadState('networkidle'); await page.waitForTimeout(1500);
  if (await page.locator('input[type=password]').count()) throw new Error(tag + ' 登录未成功');
  const shot = name => page.screenshot({ path: join(OUT, `${tag}-${name}.png`), fullPage: false });
  const modalText = async () => (await page.locator('.ant-modal').last().innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 400);
  // 1) 发布记录：选中非线上的第 18 版，详情展示相对上一版的变更
  await page.goto(`${ORIGIN}/i18n/releases?app=operations-console&release=18&lang=${locale}`);
  await page.waitForLoadState('networkidle'); await page.waitForTimeout(2500);
  await shot('releases-detail');
  const scroll = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  const detail = (await page.locator('main').innerText().catch(() => '')).replace(/\s+/g, ' ').slice(0, 600);
  // 2) 切回确认弹窗（以线上版本为基准的差异），只打开再取消
  let switchModal = false;
  const switchButton = page.getByRole('button', { name: /切换|切回|Switch/ }).first();
  if (await switchButton.isVisible().catch(() => false)) {
    const start = Date.now(); await switchButton.click();
    await page.locator('.ant-modal').last().waitFor({ timeout: 15000 }).catch(() => {});
    await page.waitForLoadState('networkidle'); await page.waitForTimeout(800);
    switchModal = { openMs: Date.now() - start, text: await modalText() };
    await shot('switch-modal'); await page.keyboard.press('Escape'); await page.waitForTimeout(400);
  }
  // 3) 翻译页的发布弹窗（预检摘要），只打开再取消
  let modal = false;
  await page.goto(`${ORIGIN}/i18n/translations?app=operations-console&lang=${locale}`);
  await page.waitForLoadState('networkidle'); await page.waitForTimeout(2000);
  const publish = page.getByRole('button', { name: /发布|Publish/ }).first();
  if (await publish.isVisible().catch(() => false)) {
    const start = Date.now(); await publish.click();
    await page.locator('.ant-modal').last().waitFor({ timeout: 15000 }).catch(() => {});
    await page.waitForLoadState('networkidle'); await page.waitForTimeout(800);
    modal = { openMs: Date.now() - start, text: await modalText() };
    await shot('publish-modal'); await page.keyboard.press('Escape');
  }
  const appErrors = errors.filter(e => !/ERR_FAILED|19400|tip` is deprecated/.test(e));
  result.push({ tag, horizontalOverflowPx: scroll, appErrors, otherErrors: errors.length - appErrors.length, requests: slow, detail, switchModal, modal });
  console.log(tag, 'overflow', scroll, 'appErrors', appErrors.length, JSON.stringify(slow), 'switch', switchModal && switchModal.openMs, 'publish', modal && modal.openMs);
  await ctx.close();
}
await browser.close();
writeFileSync(join(OUT, 'browser-result.json'), JSON.stringify(result, null, 1));
