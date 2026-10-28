// 视觉矩阵：同一申请在 en / zh-CN × 1440 / 390 下的声明页（含失效）、阅读面板、人员页与概览。用法：node views.mjs <account> <appId> <tasks,逗号分隔>
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { open, WEB, HERE, account, login } from './lib.mjs';
const [acc, app, tasks = 'declarations'] = process.argv.slice(2);
const out = [];
for (const lang of ['en', 'zh-CN']) for (const width of [1440, 390]) {
  const s = await open({ lang, width, tag: `V-${acc}-${lang}-${width}` });
  const { page } = s;
  await login(s, account(acc));
  await page.goto(`${WEB}/onboarding/applications/${app}`); await s.settle(1500);
  const home = await s.scan('overview');
  const row = { lang, width, overviewDeclarations: (home.body.match(/(Declarations|声明)\n[^\n]*\n?[^\n]*/) ?? [''])[0].replace(/\n/g, ' | ') };
  for (const task of tasks.split(',')) {
    await page.goto(`${WEB}/onboarding/applications/${app}/tasks/${task}`); await s.settle(1500);
    const v = await s.scan(task);
    if (task === 'declarations') {
      row.summary = (await page.locator('.ob-declarations-stale').allInnerTexts()).join(' ');
      row.rowMarkers = await page.locator('.ob-declaration-stale').count();
      const read = page.locator('.ob-declaration-read').first();
      if (await read.count()) { await read.click(); await page.waitForTimeout(700); await s.scan('read panel'); row.panelHead = (await page.locator('.ob-drawer header, .ob-legal-meta').allInnerTexts()).join(' / ').replace(/\s+/g, ' ');
        await page.keyboard.press('Escape'); await page.waitForTimeout(300); }
    }
    if (task === 'people') row.peopleIntro = (await page.locator('.ob-intro').first().innerText().catch(() => '')).slice(0, 160) + ' || ' + (await page.locator('.ob-checks').innerText().catch(() => '')).replace(/\n/g, ' ').slice(0, 200);
  }
  row.findings = s.findings.map(f => `${f.kind}:${f.page}:${JSON.stringify(f.keys ?? f.px ?? f.status ?? '')}`);
  out.push(row); await s.close();
}
writeFileSync(join(HERE, `views-${acc}.json`), JSON.stringify(out, null, 1));
console.log(JSON.stringify(out, null, 1));
