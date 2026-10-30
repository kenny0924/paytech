// 阶段 7 补充：等发布弹窗 / 切回确认真正出结果（不再显示「正在检查 / 正在对比」）再截图并计时。只读：打开后取消。
// 运行：I18NCHG_ADMIN_EMAIL / I18NCHG_ADMIN_PASSWORD 环境变量（mor-test 管理员，由 e2e_http.secrets() 取得）node browser_settle.mjs <截图目录>
import { createRequire } from 'node:module'; import { homedir } from 'node:os'; import { join } from 'node:path';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
const OUT = process.argv[2]; const O = 'http://localhost:28080';
const b = await chromium.launch({ headless: true });
for (const [w, h, loc] of [[1440, 900, 'zh-CN'], [390, 844, 'en']]) {
  const p = await (await b.newContext({ viewport: { width: w, height: h }, locale: loc })).newPage();
  await p.goto(`${O}/?lang=${loc}`); await p.waitForLoadState('networkidle');
  await p.locator('input:not([type=password])').first().fill(process.env.I18NCHG_ADMIN_EMAIL); await p.locator('input[type=password]').first().fill(process.env.I18NCHG_ADMIN_PASSWORD);
  await p.locator('button[type=submit], form button.ant-btn-primary').first().click(); await p.waitForTimeout(3000);
  for (const [name, url, btn, done] of [['switch', `/i18n/releases?app=operations-console&release=18&lang=${loc}`, /切换|切回|Switch/, /正在对比|Comparing/],
                                         ['publish', `/i18n/translations?app=operations-console&lang=${loc}`, /发布|Publish/, /正在检查|Checking/]]) {
    await p.goto(O + url); await p.waitForLoadState('networkidle'); await p.waitForTimeout(1500);
    const t0 = Date.now(); await p.getByRole('button', { name: btn }).first().click();
    const m = p.locator('.ant-modal').last(); await m.waitFor();
    await p.waitForFunction(re => !new RegExp(re).test(document.querySelector('.ant-modal:last-of-type')?.innerText || [...document.querySelectorAll('.ant-modal')].pop()?.innerText || ''), done.source, { timeout: 20000 });
    const ms = Date.now() - t0; await p.waitForTimeout(300);
    await p.screenshot({ path: join(OUT, `${w}-${loc}-${name}-settled.png`) });
    console.log(w, loc, name, 'settled', ms, 'ms |', (await m.innerText()).replace(/\s+/g, ' ').slice(0, 200));
    await p.keyboard.press('Escape'); await p.waitForTimeout(400);
  }
}
await b.close();
