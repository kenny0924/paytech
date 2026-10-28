// 探查：登录后打开某申请的任务页，列出可见字段（标签、控件 id / name、可选标记）。用法：node probe.mjs <account> <appId> <task> [lang] [width]
import { open, WEB, account, login } from './lib.mjs';
const [acc, app, task, lang = 'en', width = '1440'] = process.argv.slice(2);
const s = await open({ lang, width: Number(width), tag: `probe-${acc}` });
await login(s, account(acc));
await s.page.goto(`${WEB}/onboarding/applications/${app}/tasks/${task}`); await s.settle(1500);
const fields = await s.page.evaluate(() => [...document.querySelectorAll('.ob-field')].map(f => ({
  field: f.getAttribute('data-field'), label: (f.querySelector('label,legend')?.textContent ?? '').trim(),
  controls: [...f.querySelectorAll('input,select,textarea')].map(c => `${c.tagName.toLowerCase()}#${c.id}[${c.name}]${c.type ? ':' + c.type : ''}`).slice(0, 4) })));
console.log(JSON.stringify(fields, null, 0));
await s.scan(`probe ${task}`);
await s.close();
