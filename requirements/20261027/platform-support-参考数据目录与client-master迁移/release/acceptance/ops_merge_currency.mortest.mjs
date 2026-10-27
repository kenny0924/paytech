// 合并币种页检查：菜单无"币种"、参考数据默认币种页签、新建 / 编辑 / 换图标面板可用、/currency-specs 跳转、其他页签不变。
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url)), WEB = 'http://localhost:28080';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const env = { E2E_ADMIN_EMAIL: access.match(/账号：(\S+)/)[1], E2E_ADMIN_PASSWORD: access.match(/密码：(\S+)/)[1] };
const lang = process.argv[2] ?? 'en', zh = lang === 'zh-CN';
const browser = await chromium.launch({ headless: true }); let fails = 0;
const ok = (name, pass, info = '') => { if (!pass) fails++; console.log(`${pass ? 'PASS' : 'FAIL'} ${lang} ${name} ${info}`); };
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, ignoreHTTPSErrors: true, locale: lang });
await ctx.addInitScript(l => { try { localStorage.setItem('i18n.locale', l); } catch {} }, lang);
const page = await ctx.newPage();
page.on('pageerror', e => { fails++; console.log('PAGEERROR', String(e).slice(0, 300)); });
const settle = async (ms = 1500) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
await page.goto(WEB + '/'); await settle();
await page.getByLabel(/Email \/ initial account|Email \/ 初始化账号|邮箱|账号/).first().fill(env.E2E_ADMIN_EMAIL);
await page.getByLabel(/^(Password|密码)$/).fill(env.E2E_ADMIN_PASSWORD);
await page.getByRole('button', { name: /^(Sign In|登\s*录)$/ }).click();
await page.getByText(/Sign Out|退出/).first().waitFor({ timeout: 20000 });
await settle();
const side = page.locator('.console-sidebar');
await side.getByText(zh ? '平台配置' : 'Platform Settings', { exact: true }).first().click(); await page.waitForTimeout(600);
const sideText = await side.innerText();
ok('sidebar has no Currencies item', !(zh ? /(^|\n)币种(\n|$)/ : /(^|\n)Currencies(\n|$)/).test(sideText));
ok('sidebar has Reference Data', (zh ? /参考数据/ : /Reference Data/).test(sideText));
await side.getByText(zh ? '参考数据' : 'Reference Data', { exact: true }).first().click(); await settle(2500);
const activeTab = async () => (await page.locator('.ant-tabs-tab-active').first().innerText()).trim();
ok('opens on Currencies tab', /^(Currencies|币种)/.test(await activeTab()), await activeTab());
ok('tab order', (await page.locator('.ant-tabs-tab').allInnerTexts()).map(x => x.replace(/\s*\d+$/, '').trim()).join('|') === (zh ? '币种|国家|行政区|行业|MCC' : 'Currencies|Countries|Subdivisions|Industries|MCC'),
  (await page.locator('.ant-tabs-tab').allInnerTexts()).join('|'));
ok('no read-only lock on Currencies tab', await page.locator('.refdata-readonly').count() === 0);
ok('currency rows', await page.locator('.currency-page tbody tr.ant-table-row').count() >= 1);
await page.screenshot({ path: join(HERE, `merge-${lang}-currencies.png`) });
// new currency panel
await page.getByRole('button', { name: zh ? /新建币种/ : /New Currency/ }).click(); await page.waitForTimeout(800);
ok('new currency form opens', await page.locator('.currency-editor').count() === 1);
await page.getByRole('button', { name: zh ? /^取\s*消$/ : /^Cancel$/ }).first().click(); await page.waitForTimeout(500);
// change icon modal on an enabled currency
await page.locator('.currency-page tbody tr.ant-table-row').first().click(); await page.waitForTimeout(600);
const change = page.getByRole('button', { name: zh ? /更换图标/ : /Change Icon/ });
if (await change.count() && await change.isEnabled()) {
  await change.click(); await page.waitForTimeout(600);
  ok('change-icon modal opens', await page.locator('.ant-modal .currency-logo-compare').count() === 1);
  await page.screenshot({ path: join(HERE, `merge-${lang}-change-icon.png`) });
  await page.locator('.ant-modal').getByRole('button', { name: zh ? /^取\s*消$/ : /^Cancel$/ }).click(); await page.waitForTimeout(500);
} else ok('change-icon button present on enabled currency', false);
// other tabs
await page.getByRole('tab', { name: zh ? /^国家/ : /^Countries/ }).click(); await settle(1000);
ok('countries tab', /tab=countries/.test(page.url()) && await page.locator('.refdata-readonly').count() === 1 && await page.locator('tbody tr.ant-table-row').count() > 5, page.url());
await page.getByRole('tab', { name: zh ? /^行业/ : /^Industries/ }).click(); await settle(800);
ok('industries tab', /tab=industries/.test(page.url()) && (await page.locator('body').innerText()).includes('RETAIL_APPAREL'));
await page.screenshot({ path: join(HERE, `merge-${lang}-industries.png`) });
// legacy redirect
await page.goto(WEB + '/currency-specs'); await settle(2500);
ok('/currency-specs redirects', /\/reference-data\?tab=currencies$/.test(page.url()) && /^(Currencies|币种)/.test(await activeTab()), page.url());
await page.goto(WEB + '/reference-data?tab=subdivisions&country=EG'); await settle(2500);
ok('deep link subdivisions EG', /^(Subdivisions|行政区)/.test(await activeTab()) && (await page.locator('body').innerText()).includes('EG-C'));
await browser.close();
console.log('failures', fails);
