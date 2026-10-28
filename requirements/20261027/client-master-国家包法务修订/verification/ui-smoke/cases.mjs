// 用例 2–5 的字段级检查（申请已由 flow.mjs 创建并填写）。用法：node cases.mjs <case> <account> <appId> [lang] [width]
//   sa-org：无执照到期日、有 CR 年度确认日；sa-fl：FL 号有效 / 无效、到期日不能是过去；eg-org：统一 / 本地 CR 二选一、增值税单独字段；ae-ind：签发机关"其他"→ 名称
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { open, WEB, HERE, account, login } from './lib.mjs';
const [kase, acc, app, lang = 'en', width = '1440'] = process.argv.slice(2);
const s = await open({ lang, width: Number(width), tag: `${kase}-${lang}-${width}` });
const { page } = s;
const r = { case: kase, app, lang, width };
const field = key => page.locator(`[data-field="f-${key.replace(/\./g, '-')}"]`);
const fieldKeys = () => page.evaluate(() => [...document.querySelectorAll('.ob-field[data-field]')].map(f => f.getAttribute('data-field')));
const errorOf = async key => (await field(key).locator('.ob-error').allInnerTexts()).join(' ');
async function typeAndBlur(key, value) { const input = field(key).locator('input').first(); await input.fill(value); await input.blur(); await s.settle(1800); }
await login(s, account(acc));
await page.goto(`${WEB}/onboarding/applications/${app}/tasks/profile`); await s.settle(1500);
r.fields = await fieldKeys();
if (kase === 'sa-org') {
  r.licenseExpiryPresent = r.fields.some(k => /licenseExpiryDate/.test(k));
  r.annualConfirmationPresent = r.fields.includes('f-attributes-SA_CR_ANNUAL_CONFIRMATION_DATE');
  r.annualLabel = r.annualConfirmationPresent ? (await field('attributes.SA_CR_ANNUAL_CONFIRMATION_DATE').locator('label').innerText()) : null;
  await s.scan('sa org profile');
} else if (kase === 'sa-fl') {
  const fl = 'attributes.SA_FREELANCE_DOCUMENT_NUMBER', exp = 'attributes.SA_FREELANCE_DOCUMENT_EXPIRY_DATE';
  r.flPresent = r.fields.includes('f-attributes-SA_FREELANCE_DOCUMENT_NUMBER');
  await typeAndBlur(fl, 'AB123'); r.invalidError = await errorOf(fl); await s.scan('fl invalid AB123');
  await typeAndBlur(fl, 'FL-ABC123'); r.validError = await errorOf(fl);
  await typeAndBlur(exp, '2020-01-01'); r.pastExpiryErrorInline = await errorOf(exp);
  await page.reload(); await s.settle(2000); r.pastExpiryErrorAfterReload = await errorOf(exp);
  await s.scan('fl past expiry');
  await page.goto(`${WEB}/onboarding/applications/${app}/tasks/review`); await s.settle(2000);
  r.reviewText = (await page.locator('main').innerText()).replace(/\s+/g, ' ').slice(0, 500);
  await s.scan('fl past expiry review');
  await page.goto(`${WEB}/onboarding/applications/${app}/tasks/profile`); await s.settle(1500);
  await typeAndBlur(exp, '2028-10-26'); r.futureExpiryError = await errorOf(exp);
  await s.scan('fl valid');
} else if (kase === 'eg-org') {
  const id = page.locator('[data-field^="f-identifier-EG_"]').first();
  r.identifierChoices = await id.locator('input[type=radio]').evaluateAll(rs => rs.map(x => (x.parentElement?.textContent ?? '').trim()));
  r.identifierFieldCount = await page.locator('[data-field^="f-identifier-EG_UNIFIED_CR"], [data-field^="f-identifier-EG_LOCAL_CR"]').count();
  r.vatPresent = r.fields.includes('f-attributes-EG_VAT_REGISTRATION_NUMBER');
  r.taxCardPresent = r.fields.includes('f-attributes-EG_TAX_CARD_NUMBER');
  await s.scan('eg org profile');
  const radios = id.locator('input[type=radio]');
  if (await radios.count() > 1) { await radios.nth(1).check(); await s.settle(800); await s.scan('eg org local cr chosen'); }
} else if (kase === 'ae-ind') {
  const authority = page.locator('[data-field="f-attributes-AE_LICENSE_AUTHORITY"] select');
  r.authorityPresent = await authority.count() > 0;
  if (r.authorityPresent) {
    await authority.selectOption('AE_OTHER'); await s.settle(1000);
    r.nameVisible = await field('attributes.AE_LICENSE_AUTHORITY_NAME').count() > 0;
    r.nameLabel = r.nameVisible ? await field('attributes.AE_LICENSE_AUTHORITY_NAME').locator('label').innerText() : null;
    r.nameOptional = r.nameVisible ? await field('attributes.AE_LICENSE_AUTHORITY_NAME').locator('.ob-optional').count() > 0 : null;
    await page.locator('footer.ob-actions button.primary').last().click(); await s.settle(1000);
    r.nameEmptyError = r.nameVisible ? await errorOf('attributes.AE_LICENSE_AUTHORITY_NAME') : null;
    await s.scan('ae ind other authority');
    if (r.nameVisible) { await typeAndBlur('attributes.AE_LICENSE_AUTHORITY_NAME', 'Ajman Department of Economic Development'); }
    r.ernOptional = await field('attributes.AE_LICENSE_ERN').locator('.ob-optional').count() > 0;
    await s.scan('ae ind other authority filled');
  }
}
r.findings = s.findings.map(f => `${f.kind}:${f.page}:${f.status ?? ''}:${(f.url ?? '').slice(-60)}`);
writeFileSync(join(HERE, `case-${kase}-${lang}-${width}.json`), JSON.stringify(r, null, 1));
console.log(JSON.stringify(r, null, 1));
await s.close();
