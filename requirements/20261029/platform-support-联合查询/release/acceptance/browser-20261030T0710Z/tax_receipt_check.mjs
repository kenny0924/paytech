// 税务回归补测：收据下载地址（POST 带 Content-Type: application/json，与页面一致），只 GET 一次，不保存文件。一次登录。
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const ORIGIN = 'http://localhost:28080'; const ORDER = 'ord_3LOyZii4ct3BgT48vPZhmHbyC302a8';
const access = readFileSync('/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md', 'utf8');
const browser = await chromium.launch({ headless: true });
const page = await (await browser.newContext({ viewport: { width: 1440, height: 1000 } })).newPage();
await page.goto(`${ORIGIN}/?lang=zh-CN`);
await page.locator('input:not([type=password])').first().fill(access.match(/账号：(\S+)/)[1]);
await page.locator('input[type=password]').first().fill(access.match(/密码：(\S+)/)[1]);
const login = page.waitForResponse(r => r.url().includes('/auth/v1/login'), { timeout: 20000 }).catch(() => null);
await page.locator('button[type=submit], form button.ant-btn-primary').first().click();
const result = { login: (await login)?.status() ?? null };
await page.waitForURL(u => !u.pathname.startsWith('/login'), { timeout: 30000 }).catch(() => {});
Object.assign(result, await page.evaluate(async order => {
  const token = JSON.parse(sessionStorage.getItem('paytech.operations.session.v1')).accessToken;
  const auth = { Authorization: 'Bearer ' + token };
  const detail = await (await fetch(`/api/operations/tax/v1/orders/${order}`, { headers: auth })).json();
  const grantResponse = await fetch(`/api/operations/tax/v1/orders/${order}/documents/${detail.receipt.documentId}/download`, { method: 'POST', headers: { ...auth, 'Content-Type': 'application/json' } });
  const grant = await grantResponse.json().catch(() => null);
  const out = { documentNumber: detail.receipt.documentNumber, grantStatus: grantResponse.status, grantDocumentNumber: grant?.documentNumber ?? null, hasUrl: !!grant?.url };
  if (grant?.url) {
    const file = await fetch(grant.url, { credentials: 'omit' });
    const bytes = new Uint8Array(await file.arrayBuffer());
    Object.assign(out, { fileStatus: file.status, contentType: file.headers.get('content-type'), bytes: bytes.length, pdfMagic: String.fromCharCode(...bytes.slice(0, 5)) });
  }
  return out;
}, ORDER));
writeFileSync(join(dirname(fileURLToPath(import.meta.url)), 'tax-receipt-check.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify(result));
await browser.close();
