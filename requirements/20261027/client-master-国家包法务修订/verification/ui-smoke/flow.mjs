// Client 门户入驻流程 UI 冒烟：登录 → 入驻码 → 主体选择（AE/SA/EG 组织、AE 个人清单）→ 创建 → 各任务页填写（含人员抽屉、上传小 PDF）→ 核对 →（提交 | 撤回 | 只浏览）。
// 用法：node client_flow.mjs <tag> <JURIS> <ORGANIZATION|INDIVIDUAL> <orgType|-> <submit|withdraw|explore> [--code] [--explore-entities]
// 账号取 scratchpad/ui-smoke-accounts.json；入驻码取 scratchpad/ui-smoke-code.txt；不打印口令与入驻码。
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { open, WEB, HERE, account, captureRequests } from './lib.mjs';
const SCRATCH = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/01ea5128-6d5a-487b-a761-584136eae2a1/scratchpad';
const [tag, J, TYPE, ORG, MODE] = process.argv.slice(2);
const USE_CODE = process.argv.includes('--code'), EXPLORE = process.argv.includes('--explore-entities');
const creds = account(process.env.ACCOUNT ?? tag);
const acct = { email: creds.email, password: creds.password, lang: process.env.LANG_UI ?? 'en', width: Number(process.env.WIDTH_UI ?? 1440) };
const s = await open({ lang: acct.lang, width: acct.width, tag: `${tag}-${acct.lang}-${acct.width}-${J}-${TYPE[0]}` });
const { page } = s;
captureRequests(s);
const zh = acct.lang === 'zh-CN';
const PDF = join(HERE, 'sample.pdf');
if (!existsSync(PDF)) writeFileSync(PDF, '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');
const daysAgo = n => new Date(Date.now() - n * 864e5).toISOString().slice(0, 10);
const btn = re => page.getByRole('button', { name: re });
const primary = () => page.locator('footer.ob-actions button.primary, .ob-drawer button.primary').last();

// ---------- 值 ----------
function luhn10(prefix) { // 10 位，Luhn 校验位
  for (let i = 0; i < 100000; i++) { const body = prefix + String(Math.floor(Math.random() * 1e8)).padStart(8, '0'); const sum = [...body].reverse().reduce((a, d, k) => { let v = +d; if (k % 2 === 0) { v *= 2; if (v > 9) v -= 9; } return a + v; }, 0); const c = (10 - sum % 10) % 10; return body + c; }
}
const PHONE = { AE: '+971501234567', SA: '+966501234567', EG: '+201001234567' }[J];
function personId(typeHint) {
  if (/EMIRATES/i.test(typeHint)) return '784' + String(Date.now()).slice(-12);
  if (/IQAMA/i.test(typeHint)) return luhn10('2');
  if (/SA_NATIONAL|National ID|国民身份/i.test(typeHint) && J === 'SA') return luhn10('1');
  if (/EG_NATIONAL|National ID|国民身份/i.test(typeHint) && J === 'EG') return '29001010112345';
  return 'X' + String(Date.now()).slice(-7);
}
function orgId() { return { AE: 'DED' + String(Date.now()).slice(-6), SA: '70' + String(Date.now()).slice(-8), EG: String(Date.now()).slice(-9) }[J]; }
function valueFor(name, type) {
  const n = name.toLowerCase();
  if (type === 'date') return /expir|validto|licenseexpiry/.test(n) ? '2028-10-26' : /birth/.test(n) ? '1985-04-12' : '2020-01-15';
  if (type === 'email' || /email/.test(n)) return `ops-${tag.toLowerCase()}@example.test`;
  if (type === 'tel' || /phone/.test(n)) return PHONE;
  if (type === 'url' || /website|url/.test(n)) return 'https://example.test';
  if (/license|licence/.test(n)) return 'DED' + String(Date.now()).slice(-6);
  if (/legalname/.test(n)) return `UI Smoke ${J} Trading ${tag} LLC`;
  if (/tradename/.test(n)) return `UI Smoke ${J}`;
  if (/givenname/.test(n)) return 'Layla';
  if (/familyname/.test(n)) return 'Haddad';
  if (/middlename/.test(n)) return '';
  if (/birthplace/.test(n)) return { AE: 'Dubai', SA: 'Riyadh', EG: 'Cairo' }[J];
  if (/volume/.test(n)) return '50000';
  if (/ticket/.test(n)) return '100';
  if (/description/.test(n)) return 'Sells home goods online through its own website (UI smoke test data).';
  if (/addressline1|street/.test(n)) return { AE: 'Unit 2504, Cluster X, JLT', SA: 'Rayhanah Bint Zaid', EG: '12 Tahrir Street' }[J];
  if (/addressline2/.test(n)) return '';
  if (/city/.test(n)) return { AE: 'Dubai', SA: 'Riyadh', EG: 'Cairo' }[J];
  if (/district/.test(n)) return { AE: 'JLT', SA: 'Al Arid', EG: 'Downtown' }[J];
  if (/pobox/.test(n)) return '340512';
  if (/shortaddress|short/.test(n)) return 'RRRD2929';
  if (/unit/.test(n)) return '';
  if (/postal/.test(n)) return { AE: '00000', SA: '13337', EG: '11511' }[J];
  if (/buildingnumber/.test(n)) return '2929';
  if (/additionalnumber/.test(n)) return '8118';
  if (/ae_ern|license_ern/.test(n)) return 'ERN-' + String(Date.now()).slice(-6);
  if (/freelance_document_number/.test(n)) return 'FL-ABC123';
  if (/authority_name/.test(n)) return 'Ajman Free Zone Authority';
  if (/trn|vat|tax/.test(n)) return { AE: '100123456700003', SA: '300123456700003', EG: '123456789' }[J];
  if (/title|job/.test(n)) return 'Director';
  return 'Test value';
}

/** 填写 scope 内可见、可编辑、为空的控件；identifier 按提示选号码。 */
async function fill(scope, { person = false } = {}) {
  // 布尔：选“否”（无关 PEP 等）；上市公司选否
  for (const fs of await scope.locator('fieldset.ob-field').all()) {
    const radios = fs.locator('input[type=radio]'); if (!(await radios.count())) continue;
    if (await radios.evaluateAll(rs => rs.some(r => r.checked))) continue;
    await radios.first().check({ force: true }).catch(() => {});
  }
  for (const sel of await scope.locator('select:visible').all()) {
    if (await sel.isDisabled() || await sel.inputValue()) continue;
    const opts = await sel.locator('option').evaluateAll(os => os.filter(o => o.value && !o.disabled).map(o => o.value));
    if (!opts.length) continue;
    const name = (await sel.getAttribute('name')) ?? '';
    const pick = opts.includes(J) && /country|nationality|residence|issuer|incorporation/i.test(name + (await sel.getAttribute('id'))) ? J
      : opts.includes('USD') ? 'USD' : opts[0];
    await sel.selectOption(pick); await page.waitForTimeout(150);
  }
  // 参考数据下拉（RefSelect）：国家优先选本辖区，币种选 USD，其余取第一项。
  for (const trigger of await scope.locator('button.ob-ref-trigger:visible').all()) {
    if (await trigger.isDisabled() || !(await trigger.locator('.ob-ref-placeholder').count())) continue;
    await trigger.click(); await page.waitForTimeout(250);
    const options = page.locator('.ob-ref.open li[role=option]');
    const codes = await options.locator('.ob-ref-code').allInnerTexts();
    const index = Math.max(0, codes.indexOf(J), codes.indexOf('USD'));
    if (codes.length) await options.nth(index).click(); else await page.keyboard.press('Escape');
    await page.waitForTimeout(250);
  }
  for (const input of await scope.locator('input:visible:not([type=radio]):not([type=checkbox]):not([type=file]), textarea:visible').all()) {
    if (await input.isDisabled() || await input.inputValue()) continue;
    const name = (await input.getAttribute('name')) ?? (await input.getAttribute('id')) ?? '';
    const type = (await input.getAttribute('type')) ?? 'text';
    if (type === 'date') { await input.fill(valueFor(name, type)); }
    else if (/identifier/i.test(name)) {
      const hint = (await scope.locator('input[type=radio][name$="-type"]:checked').first().evaluate(r => r.parentElement.textContent).catch(() => '')) + ' ' + name;
      await input.fill(person || TYPE === 'INDIVIDUAL' ? personId(hint) : orgId());
    } else if (/share/.test(name)) continue;
    else { const v = valueFor(name, type); if (v) await input.fill(v); }
    await input.blur().catch(() => {}); await page.waitForTimeout(250);
  }
}
async function next(label) {
  await primary().click(); await s.settle(1200);
  const summary = await page.locator('.ob-summary, [role=alert]').allInnerTexts().catch(() => []);
  if (summary.join('').trim()) s.note('validation-after-continue', { at: label, text: summary.map(x => x.replace(/\s+/g, ' ').slice(0, 300)) });
  return summary;
}
async function uploadSlots(scope, label) {
  const slots = scope.locator('.ob-slot'); const n = await slots.count();
  for (let i = 0; i < n; i++) {
    const slot = slots.nth(i); const file = slot.locator('input[type=file]');
    if (!(await slot.locator('.ob-drop').count())) continue;
    await file.setInputFiles(PDF); await page.waitForTimeout(500);
    const dates = slot.locator('.ob-dates input[type=date]');
    if (await dates.count()) {
      await dates.nth(0).fill(daysAgo(30));
      if (await dates.count() > 1) await dates.nth(1).fill('2028-10-26');
      if (i === 0) await s.scan(`${label} upload pending ${i}`);
      await slot.locator('.ob-pending-actions button.primary').click();
    }
    for (let w = 0; w < 30; w++) { await page.waitForTimeout(700); if (!(await slot.locator('.ob-pending, [class*=uploading]').count())) break; }
    await s.settle(600);
    const txt = (await slot.innerText()).replace(/\s+/g, ' ');
    if (/fail|失败|error|错误/i.test(txt)) s.note('upload-failed', { at: label, slot: i, text: txt.slice(0, 300) });
  }
}

// ---------- 流程 ----------
s.current = 'login';
await page.goto(WEB + '/login'); await s.settle();
await page.locator('#email').fill(acct.email); await page.locator('#password').fill(acct.password);
await page.locator('button[type=submit]').click(); await s.settle(2000);
await s.scan('after login');
if (!/onboarding\/new/.test(page.url())) { await page.goto(WEB + '/onboarding'); await s.settle(1200); await s.scan('onboarding start list'); }
const existing = page.locator('a.ob-row-link');
const VIEW = MODE.startsWith('existing');
const SKIP_TASKS = process.env.SKIP_TASKS === '1';
if (VIEW) { await existing.first().click(); await s.settle(1500); }
else if (!/onboarding\/new/.test(page.url()) && await page.getByRole('link', { name: /new application|新申请|开始新的/i }).count()) {
  await page.getByRole('link', { name: /new application|新申请|开始新的/i }).first().click(); await s.settle();
}
if (!VIEW && /onboarding\/new/.test(page.url())) {
  await s.scan('code step');
  if (USE_CODE) {
    await page.locator('#onboarding-code').fill('ZZZZ-ZZZ'); await s.scan('code step incomplete');
    await page.locator('#onboarding-code').fill(readFileSync(join(SCRATCH, 'ui-smoke-code.txt'), 'utf8').trim());
    await page.locator('form button[type=submit]').click();
  } else await page.locator('.ob-enter .text-button').first().click();
  await s.settle(900); await s.scan('entity step');
  await page.locator('button[type=submit]').click().catch(() => {}); await page.waitForTimeout(300);
  const combos = EXPLORE ? [['AE', 'ORGANIZATION'], ['SA', 'ORGANIZATION'], ['EG', 'ORGANIZATION'], ['AE', 'INDIVIDUAL'], [J, TYPE]] : [[J, TYPE]];
  for (const [ci, [j, type]] of combos.entries()) {
    const country = page.locator('input[name=jurisdiction]'); const codes = await page.evaluate(() => [...document.querySelectorAll('input[name=jurisdiction]')].map(i => i.parentElement.textContent));
    const idx = codes.findIndex(c => ({ AE: /Emirates|阿联酋/, SA: /Saudi|沙特/, EG: /Egypt|埃及/ })[j].test(c));
    if (idx < 0) { s.note('missing-jurisdiction', { j, codes }); continue; }
    await country.nth(idx).check(); await page.locator('input[name=client-type]').nth(type === 'ORGANIZATION' ? 0 : 1).check(); await s.settle(900);
    const orgSel = page.locator('#organization-type');
    let orgTypes = [];
    if (await orgSel.count()) {
      orgTypes = await orgSel.locator('option').evaluateAll(os => os.filter(o => o.value).map(o => `${o.value}=${o.textContent}`));
      const want = j === J && type === TYPE && ORG !== '-' ? ORG : orgTypes[0]?.split('=')[0];
      await orgSel.selectOption(want);
    }
    await s.scan(`entity ${j} ${type}`, { orgTypes });
    await page.locator('button[type=submit]').click(); await s.settle(1200);
    await s.scan(`checklist ${j} ${type}`);
    if (ci < combos.length - 1)
      { await page.locator('.ob-row-actions .text-button').first().click(); await s.settle(600); }
  }
  await page.locator('.ob-row-actions button.primary').click(); await s.settle(2500);
}
await s.scan('application home');
const appUrl = page.url(); const appId = appUrl.match(/applications\/(\d+)/)?.[1];
writeFileSync(join(HERE, `client-app-${tag}-${J}-${TYPE[0]}.txt`), `${appId}\n`);
if (VIEW && MODE === 'existing-view') {
  const links = await page.locator('main a[href*="/onboarding/applications/"], a.ob-rail-item').evaluateAll(as => [...new Set(as.map(a => a.getAttribute('href')).filter(h => /\/(profile|address|people|materials|declarations|review)$/.test(h)))]);
  for (const href of links) { await page.goto(WEB + href); await s.settle(1200); await s.scan('view ' + href.split('/').pop());
    const row = page.locator('tr[data-field^=person-]').first(); if (await row.count()) { await row.click(); await page.waitForTimeout(800); await s.scan('view people drawer'); await page.keyboard.press('Escape'); } }
  await page.goto(appUrl); await s.settle(1200); await s.scan('view home again');
}
if (MODE === 'withdraw' || MODE === 'existing-withdraw') {
  await btn(/Withdraw application|撤回申请/).first().click(); await page.waitForTimeout(500); await s.scan('withdraw confirm');
  await btn(/^(Withdraw|撤回|确认撤回)$/).last().click(); await s.settle(1500); await s.scan('withdrawn result');
  await s.close(); process.exit(0);
}
if (VIEW && MODE !== 'existing-fill') { await s.close(); process.exit(0); }
const tasks = await page.locator('a.ob-rail-item, .ob-task-list a, main a[href*="/onboarding/applications/"]').evaluateAll(as => [...new Set(as.map(a => a.getAttribute('href')).filter(h => /\/(profile|address|people|materials|declarations|review)$/.test(h)))]);
console.log('tasks', tasks);
for (const href of (SKIP_TASKS ? [] : tasks)) {
  const key = href.split('/').pop();
  try {
  await page.goto(WEB + href); await s.settle(1200); await s.scan(`task ${key} empty`);
  if (key === 'profile' || key === 'address') {
    await primary().click(); await s.settle(800); await s.scan(`task ${key} validation`);
    await fill(page.locator('main form.ob-form')); await s.scan(`task ${key} filled`);
    await next(key); await s.scan(`task ${key} after continue`);
  } else if (key === 'people') {
    const people = TYPE === 'ORGANIZATION' ? [['UBO', 'DIRECTOR', 'SIGNATORY'], ['UBO']] : [];
    for (const [pi, roles] of people.entries()) {
      await page.locator('.ob-add').click(); await page.waitForTimeout(600); await s.scan(`people drawer new ${pi}`);
      const drawer = page.locator('.ob-drawer').last();
      await drawer.locator('button.primary').click(); await page.waitForTimeout(500); await s.scan(`people drawer validation ${pi}`);
      const boxes = drawer.locator('#person-roles input[type=checkbox]'); const labels = await boxes.evaluateAll(bs => bs.map(b => b.parentElement.textContent));
      for (const [bi, l] of labels.entries()) if (roles.some(r => ({ UBO: /beneficial|受益/i, DIRECTOR: /director|董事/i, SIGNATORY: /signator|签字|授权/i })[r].test(l))) await boxes.nth(bi).check();
      if (await drawer.locator('#person-share').count()) await drawer.locator('#person-share').fill(pi === 0 ? '60' : '40');
      if (await drawer.locator('input[name=ubo-basis]').count()) await drawer.locator('input[name=ubo-basis]').first().check();
      if (pi === 1) { const c = drawer.locator('select[name="address.countryCode"]'); if (await c.count()) { await c.selectOption('GB'); await s.settle(600); await s.scan('people drawer GB address layout'); } }
      await fill(drawer.locator('.ob-drawer-body'), { person: true }); await s.scan(`people drawer filled ${pi}`);
      await drawer.locator('button.primary').click(); await s.settle(1500); await s.scan(`people after save ${pi}`);
      if (await page.locator('.ob-drawer').count()) { s.note('drawer-save-failed', { pi }); await page.locator('.ob-drawer button.secondary').first().click().catch(() => page.keyboard.press('Escape')); await s.settle(600); }
      // 重新打开以上传人员证件
      const row = page.locator('tr[data-field^=person-]').nth(pi);
      if (await row.count()) { await row.click(); await page.waitForTimeout(800); const d = page.locator('.ob-drawer').last(); await s.scan(`people drawer edit ${pi}`);
        await uploadSlots(d, `person ${pi}`); await s.scan(`people drawer uploaded ${pi}`);
        await d.locator('button.secondary').first().click().catch(() => page.keyboard.press('Escape')); await s.settle(800); }
    }
    for (const cb of await page.locator('main input[type=checkbox]').all()) if (!(await cb.isChecked()) && await cb.isEnabled()) { await cb.click(); await s.settle(800); if (!(await cb.isChecked())) s.note('checkbox-did-not-stick', { at: 'people confirmations' }); }
    if (TYPE === 'INDIVIDUAL') await uploadSlots(page.locator('main'), 'individual people');
    await s.scan('task people filled'); await next('people'); await s.scan('task people after continue');
  } else if (key === 'materials') {
    await uploadSlots(page.locator('main'), 'materials'); await s.scan('task materials uploaded');
    await next('materials'); await s.scan('task materials after continue');
  } else if (key === 'declarations') {
    for (const cb of await page.locator('main input[type=checkbox]').all()) if (!(await cb.isChecked()) && await cb.isEnabled()) { await cb.click(); await s.settle(800); if (!(await cb.isChecked())) s.note('checkbox-did-not-stick', { at: 'declarations' }); }
    await s.scan('task declarations checked'); await next('declarations');
  } else if (key === 'review') {
    await s.scan('task review');
  }
  } catch (e) { s.note('script-error', { task: key, text: String(e.message).split('\n')[0] }); await page.keyboard.press('Escape').catch(() => {}); }
}
await page.goto(appUrl); await s.settle(1200); await s.scan('home after tasks');
const AFTER = process.env.AFTER ?? MODE;
if (AFTER === 'submit' || AFTER === 'submit-withdraw') {
  await page.goto(appUrl.replace(/\/?$/, '') + '/tasks/review'); await s.settle(1200); await s.scan('review before submit');
  const submit = btn(/Submit for review|提交审核|提交/).last();
  if (await submit.count()) { await submit.click(); await s.settle(3000); await s.scan('after submit'); }
  else s.note('no-submit-button', { url: page.url() });
  await page.goto(appUrl); await s.settle(1200); await s.scan('in-review page');
  if (AFTER === 'submit-withdraw') {
    await btn(/Withdraw application|撤回申请/).first().click(); await page.waitForTimeout(500); await s.scan('in-review withdraw confirm');
    await btn(/^(Withdraw|撤回|确认撤回)$/).last().click(); await s.settle(1500); await s.scan('in-review withdrawn');
  }
}
writeFileSync(join(HERE, `requests-${s.tag}.json`), JSON.stringify(s.requests.filter(r => /declarations/.test(r.path)), null, 1));
console.log('app', appId, 'findings', s.findings.length);
await s.close();
