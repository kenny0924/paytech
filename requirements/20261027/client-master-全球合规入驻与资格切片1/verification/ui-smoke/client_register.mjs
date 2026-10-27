// 经页面注册 Client 账号（en / zh-CN 各自截图），口令写入 scratchpad（不入库、不打印）。用法：node client_register.mjs <tag:lang:width> ...
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { open, WEB } from './client_lib.mjs';
const SECRETS = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/01ea5128-6d5a-487b-a761-584136eae2a1/scratchpad/ui-smoke-accounts.json';
const accounts = existsSync(SECRETS) ? JSON.parse(readFileSync(SECRETS, 'utf8')) : {};
const ts = new Date().toISOString().replace(/\D/g, '').slice(4, 14);
for (const spec of process.argv.slice(2)) {
  const [tag, lang, width] = spec.split(':');
  const s = await open({ lang, width: Number(width), tag });
  const email = `ui-smoke-${tag}-${ts}@example.test`, password = 'Ui-' + randomBytes(9).toString('base64url') + '-7q';
  await s.page.goto(WEB + '/login'); await s.settle(); await s.scan('login page');
  await s.page.goto(WEB + '/register'); await s.settle(); await s.scan('register page');
  // 先空提交看校验文案
  await s.page.locator('button[type=submit]').click(); await s.page.waitForTimeout(500); await s.scan('register empty submit');
  await s.page.locator('#email').fill(email); await s.page.locator('#password').fill(password); await s.page.locator('#confirm-password').fill(password);
  if (await s.page.locator('#terms').count()) await s.page.locator('#terms').check();
  await s.page.locator('button[type=submit]').click(); await s.settle(1500);
  await s.scan('register submitted');
  accounts[tag] = { email, password, lang, width: Number(width), registeredAt: new Date().toISOString(), url: s.page.url() };
  writeFileSync(SECRETS, JSON.stringify(accounts, null, 1), { mode: 0o600 });
  console.log('registered', tag, email, s.page.url());
  await s.close();
}
