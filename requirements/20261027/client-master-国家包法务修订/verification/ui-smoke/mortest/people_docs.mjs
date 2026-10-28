// 补传人员证件并确认人员页两项声明。用法：node people_docs.mjs <account> <appId>
import { join } from 'node:path';
import { open, WEB, HERE, account, login } from './lib.mjs';
const [acc, app] = process.argv.slice(2);
const s = await open({ lang: 'en', width: 1440, tag: `PD-${acc}` }); const { page } = s;
const PDF = join(HERE, 'sample.pdf');
await login(s, account(acc));
await page.goto(`${WEB}/onboarding/applications/${app}/tasks/people`); await s.settle(1500);
const rows = page.locator('tr[data-field^=person-]'); const n = await rows.count(); console.log('people', n);
for (let i = 0; i < n; i++) {
  await rows.nth(i).click(); await page.waitForTimeout(900);
  const d = page.locator('.ob-drawer').last();
  const slot = d.locator('.ob-slot').first();
  if (await slot.locator('.ob-drop').count()) {
    await slot.locator('input[type=file]').setInputFiles(PDF); await page.waitForTimeout(800);
    const dates = slot.locator('.ob-dates input[type=date]');
    if (await dates.count()) { await dates.nth(0).fill('2024-01-15'); if (await dates.count() > 1) await dates.nth(1).fill('2028-10-26'); }
    const go = slot.locator('.ob-pending-actions button.primary'); if (await go.count()) await go.click();
    let txt = '';
    for (let w = 0; w < 90; w++) { await page.waitForTimeout(1000); txt = (await slot.innerText()).replace(/\s+/g, ' '); if (!/Uploading|Saving|上传中/i.test(txt)) break; }
    console.log('slot', i, txt.slice(0, 200));
  }
  await s.scan(`person ${i} after upload`);
  await d.locator('button.primary').click(); await s.settle(2500);
  if (await page.locator('.ob-drawer').count()) { console.log('drawer still open', i); await page.keyboard.press('Escape'); await s.settle(800); }
}
for (const cb of await page.locator('main input[type=checkbox]').all()) if (!(await cb.isChecked()) && await cb.isEnabled()) { await cb.click(); await s.settle(1200); }
await s.scan('people final');
console.log((await page.locator('main').innerText()).replace(/\s+/g, ' ').slice(0, 700));
await s.close();
