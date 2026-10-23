// 体验优化阶段 5 浏览器验收公共部分：登录、截图（1280×800 / 1920×1080）、结果记录、网络与控制台收集。
// 只记方法、路径、状态码，不记口令、令牌或签名地址。
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const HERE = dirname(fileURLToPath(import.meta.url));
export const OPS = 'https://127.0.0.1:18444';
export const SHOTS = join(HERE, 'screenshots');
export const SCRATCH = '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/49da8772-1276-46ac-8174-179e39b15018/scratchpad';
const { chromium } = createRequire(import.meta.url)(join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core'));
mkdirSync(SHOTS, { recursive: true });
export const state = () => JSON.parse(readFileSync(join(SCRATCH, 'workflow-v2-state.json'), 'utf8'));
export const RESULTS = join(HERE, 'results.jsonl');

function env(file) {
  const values = {};
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim().replace(/^"|"$/g, '');
  }
  return values;
}
const adminEnv = env('/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/secrets.env');
const user2Cred = JSON.parse(readFileSync(join(SCRATCH, 'workflow-e2e-user2.json'), 'utf8'));
export const CRED = { admin: [adminEnv.E2E_ADMIN_EMAIL, adminEnv.E2E_ADMIN_PASSWORD], user2: [user2Cred.email, user2Cred.password] };

export const py = (...args) => execFileSync('python3', ['seed_v2.py', ...args], { cwd: HERE }).toString();

export const network = [], errors = [];
let browser;
export async function open(who, { width = 1280, height = 800, section = '', clockOffsetMs = 0 } = {}) {
  // PW_CHANNEL=chromium 用完整 Chromium 的新 headless（带 PDF 查看器），默认 headless shell 不渲染 PDF
  browser ??= await chromium.launch({ headless: true, ...(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL } : {}) });
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width, height }, acceptDownloads: true });
  const page = await ctx.newPage();
  // 浏览器时钟前移（登录前设置，令牌过期时间按同一时钟计算）
  if (clockOffsetMs) await page.clock.setFixedTime(new Date(Date.now() + clockOffsetMs));
  page.on('pageerror', e => errors.push(`${section}/${who} pageerror ${String(e).slice(0, 200)}`));
  page.on('console', m => { if (m.type() === 'error') errors.push(`${section}/${who} console.error ${m.text().slice(0, 220)}`); });
  ctx.on('response', r => {
    const u = new URL(r.url());
    if (u.pathname.startsWith('/api/')) network.push({ who, section, method: r.request().method(), path: u.pathname + (u.search.includes('disposition') ? u.search : ''), status: r.status(), at: Date.now() });
  });
  await login(page, who);
  return { ctx, page };
}
export async function login(page, who) {
  const [email, password] = CRED[who];
  for (let attempt = 1; attempt <= 3; attempt++) {
    await page.goto(OPS + '/'); await page.waitForLoadState('networkidle');
    await page.getByLabel('Email / 初始化账号').fill(email);
    await page.getByLabel('密码').fill(password);
    await page.getByRole('button', { name: /^登\s*录$/ }).click();
    if (await page.getByText('退出').first().waitFor({ timeout: 10000 }).then(() => true, () => false)) return;
  }
  throw new Error(`login ${who} failed`);
}
export const close = async () => { await browser?.close(); browser = undefined; };

export async function shot(page, name, { both = false } = {}) {
  const files = [];
  const size = page.viewportSize();
  const file = join(SHOTS, `${name}-${size.width}.png`);
  await page.screenshot({ path: file }); files.push(file.replace(HERE + '/', ''));
  if (both) {
    const other = size.width === 1280 ? { width: 1920, height: 1080 } : { width: 1280, height: 800 };
    await page.setViewportSize(other); await page.waitForTimeout(700);
    const f2 = join(SHOTS, `${name}-${other.width}.png`);
    await page.screenshot({ path: f2 }); files.push(f2.replace(HERE + '/', ''));
    await page.setViewportSize(size); await page.waitForTimeout(400);
  }
  return files.join(', ');
}

export async function step(section, name, fn) {
  const started = Date.now();
  let row;
  try {
    const evidence = (await fn()) ?? '';
    row = { section, name, result: 'PASS', evidence: String(evidence) };
  } catch (failure) {
    row = { section, name, result: 'FAIL', evidence: String(failure.message || failure).split('\n').slice(0, 3).join(' ').slice(0, 500) };
  }
  row.ms = Date.now() - started;
  appendFileSync(RESULTS, JSON.stringify(row) + '\n');
  console.log(`${row.result} [${section}] ${name} | ${row.evidence}`);
  return row.result === 'PASS';
}
export function assert(condition, text) { if (!condition) throw new Error(text); }
export const settle = async (page, ms = 600) => { await page.waitForLoadState('networkidle').catch(() => {}); await page.waitForTimeout(ms); };
export const calls = (pred, since = 0) => network.filter(n => n.at >= since && pred(n)).map(n => `${n.method} ${n.path} -> ${n.status}`);
export function summary(section) {
  const bad = network.filter(n => n.section === section && n.status >= 400).map(n => `${n.who} ${n.method} ${n.path} -> ${n.status}`);
  console.log(`NETWORK>=400 [${section}]`, JSON.stringify([...new Set(bad)]));
  console.log(`CONSOLE [${section}]`, JSON.stringify([...new Set(errors.filter(e => e.startsWith(section + '/')))].slice(0, 20)));
  appendFileSync(RESULTS, JSON.stringify({ section, name: '_network_console', result: 'INFO', evidence: JSON.stringify({ http4xx5xx: [...new Set(bad)], console: [...new Set(errors.filter(e => e.startsWith(section + '/')))].slice(0, 20) }) }) + '\n');
}
/** 取实例待办：列表里按标题点开，返回该项元素。 */
export async function openTask(page, title, { nth = 0 } = {}) {
  const item = page.locator('.wf-inbox-item', { hasText: title }).nth(nth);
  if (!await item.waitFor({ timeout: 6000 }).then(() => true, () => false)) {
    if (!await page.locator('.wf-inbox-search input').count()) await page.getByRole('button', { name: '搜索' }).first().click();
    const input = page.locator('.wf-inbox-search input');
    await input.fill(title); await input.press('Enter'); await settle(page, 800);
    await item.waitFor({ timeout: 15000 });
  }
  await item.click(); await settle(page, 800);
  return item;
}
/** 按 RFC 9457 Code-MoR 形态构造 422（与 BFF 线上响应同形：requestId 与 X-Request-Id 一致、无 status 字段、远端文案已替换）。 */
export function problem422(errors) {
  const requestId = 'e2e-' + Math.random().toString(16).slice(2, 14);
  const timestamp = new Date().toISOString().replace(/\.(\d{3})Z$/, '.$1000Z');
  return { status: 422, headers: { 'Content-Type': 'application/problem+json', 'X-Request-Id': requestId },
    body: JSON.stringify({ type: 'about:blank', title: 'Unprocessable Content', detail: '请求字段校验未通过', code: 'VALIDATION_ERROR', requestId, timestamp, errors }) };
}
