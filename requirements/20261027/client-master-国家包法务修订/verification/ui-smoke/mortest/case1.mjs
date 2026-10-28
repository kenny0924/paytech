// 用例 1（AE 机构）：登记机关选"其他"→ 名称字段出现且必填；声明阅读面板；改资料后声明失效、提交被拒；重新确认后提交。
// 用法：node case1.mjs <account> <appId> [lang] [width]
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { open, WEB, HERE, account, login, captureRequests } from './lib.mjs';
const [acc, app, lang = 'en', width = '1440'] = process.argv.slice(2);
const s = await open({ lang, width: Number(width), tag: `C1-${acc}-${lang}-${width}` });
const { page } = s; captureRequests(s);
const base = `${WEB}/onboarding/applications/${app}`;
const result = {};
await login(s, account(acc));

// a. 登记机关"其他"
await page.goto(base + '/tasks/profile'); await s.settle(1500);
const authority = page.locator('#f-organization-registrationAuthorityCode');
await authority.selectOption('AE_OTHER'); await s.settle(800);
const nameField = page.locator('[data-field="f-attributes-AE_REGISTRATION_AUTHORITY_NAME"]');
result.otherNameVisible = await nameField.count() > 0;
result.otherNameOptionalMark = result.otherNameVisible ? await nameField.locator('.ob-optional').count() > 0 : null;
const nameInput = nameField.locator('input');
if (result.otherNameVisible) await nameInput.fill('');
await page.locator('footer.ob-actions button.primary').last().click(); await s.settle(1000);
result.otherNameError = result.otherNameVisible ? (await nameField.locator('.ob-error').allInnerTexts()).join(' ') : null;
await s.scan('profile other authority empty name');
if (result.otherNameVisible) { await nameInput.fill('Ajman Free Zone Authority'); await nameInput.blur(); await s.settle(2500); }
await s.scan('profile other authority filled');

// b. 声明失效显示与阅读面板
await page.goto(base + '/tasks/declarations'); await s.settle(1500);
result.staleNotices = await page.locator('.ob-declaration-stale').count();
result.uncheckedAfterEdit = await page.locator('.ob-declaration input[type=checkbox]:not(:checked)').count();
await s.scan('declarations stale after edit');
await page.locator('.ob-declaration-read').first().click(); await page.waitForTimeout(700);
result.panelText = (await page.locator('.ob-drawer').innerText()).replace(/\s+/g, ' ').slice(0, 260);
await s.scan('declarations read panel terms');
await page.keyboard.press('Escape'); await page.waitForTimeout(400);

// c. 未重新确认即提交
await page.goto(base + '/tasks/review'); await s.settle(1500);
await s.scan('review with stale declarations');
const submit = page.getByRole('button', { name: /Submit for review|提交审核|提交/ }).last();
result.submitEnabledWhileStale = await submit.count() ? await submit.isEnabled() : null;
if (result.submitEnabledWhileStale) { await submit.click(); await s.settle(2500); }
result.blockedText = (await page.locator('.ob-summary, [role=alert]').allInnerTexts()).join(' | ').replace(/\s+/g, ' ').slice(0, 600);
await s.scan('review submit blocked stale');

// d. 重新确认后提交
await page.goto(base + '/tasks/declarations'); await s.settle(1500);
for (const cb of await page.locator('.ob-declaration input[type=checkbox]').all())
  if (!(await cb.isChecked()) && await cb.isEnabled()) { await cb.click(); await s.settle(900); }
result.staleAfterReconfirm = await page.locator('.ob-declaration-stale').count();
await s.scan('declarations reconfirmed');
await page.goto(base + '/tasks/review'); await s.settle(1500);
await s.scan('review before submit');
const submit2 = page.getByRole('button', { name: /Submit for review|提交审核|提交/ }).last();
result.submitEnabledAfterReconfirm = await submit2.count() ? await submit2.isEnabled() : null;
// mor-test 补验：不提交，结束后撤回草稿
await page.goto(base); await s.settle(1200);
result.finalText = (await page.locator('main').innerText()).replace(/\s+/g, ' ').slice(0, 300);
await s.scan('home after reconfirm');
result.declarationRequests = s.requests.filter(r => /declarations/.test(r.path)).map(r => r.body);
result.findings = s.findings.map(f => `${f.kind}:${f.page}:${f.status ?? ''}`);
writeFileSync(join(HERE, `case1-${acc}-${lang}-${width}.json`), JSON.stringify(result, null, 1));
console.log(JSON.stringify(result, null, 1));
await s.close();
