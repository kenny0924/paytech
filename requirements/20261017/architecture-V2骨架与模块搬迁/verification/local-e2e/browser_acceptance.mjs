// Client 入驻浏览器端到端验收（本机全链路，无头 Chromium）。
//
// 流程：注册 → Mailpit 验证邮件 → 登录 → 个人申请填写并保存 → 上传身份证明（浏览器直传 MinIO，等待可用）→ 提交前核对 → 提交
//   → 运营登录 → 审核列表 → 详情 → 下载材料 → 退回（基础资料）→ 客户看到退回 → 改证件号 → 重提
//   → 运营最终拒绝 → 客户看到拒绝 → 同类型重新申请 → 资料带入、材料为空。
// 每个主要步骤截图到 SHOTS 目录；记录浏览器控制台错误与 HTTP >= 400 的请求（只记方法、路径、状态，不记 Header/Body）。
// 不打印密码、令牌、Cookie 或预签名地址。
//
// 用法：
//   PLAYWRIGHT_CORE=<playwright-core 目录> SHOTS=<截图目录> node browser_acceptance.mjs
//   - PLAYWRIGHT_CORE 默认 ~/.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core（1.58.1，对应缓存 chromium-1208）。
//   - 运营管理员读取 local-environment/e2e/secrets.env 的 E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD。
//   - 本机未配置 SES 时验证邮件不会到 Mailpit：可设置 CLIENT_EMAIL / CLIENT_PASSWORD 使用已验证、且没有进行中申请的客户账号跳过注册。
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
// V2（任务卡 14）：私密值在主仓库 local-environment（本 worktree 没有），E2E_LOCAL_ENV 可改。
const ROOT = resolve(process.env.E2E_LOCAL_ENV || '/Users/kenny/Desktop/Workspace/Code-MoR/local-environment', '..');
const CLIENT = 'https://127.0.0.1:18443';
const OPERATIONS = 'https://127.0.0.1:18444';
const MAILPIT = 'http://127.0.0.1:18025';
const SHOTS = process.env.SHOTS || join(HERE, 'browser-shots');
const CORE = process.env.PLAYWRIGHT_CORE || join(homedir(), '.npm/_npx/e41f203b7505f1fb/node_modules/playwright-core');
const { chromium } = createRequire(import.meta.url)(CORE);
const PDF = Buffer.from('%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
  + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n');
mkdirSync(SHOTS, { recursive: true });

const results = [], network = [], consoleErrors = [];
let shotNo = 0;
class Stop extends Error {}

function secrets() {
  const values = {};
  for (const line of readFileSync(join(ROOT, 'local-environment/e2e/secrets.env'), 'utf8').split('\n')) {
    const at = line.indexOf('=');
    if (at > 0 && !line.startsWith('#')) values[line.slice(0, at).trim()] = line.slice(at + 1).trim();
  }
  return values;
}

async function shot(page, name) {
  const file = join(SHOTS, String(++shotNo).padStart(2, '0') + '-' + name + '.png');
  await page.screenshot({ path: file, fullPage: true });
  return file;
}

/** 记录一步；失败时截图并停止后续依赖步骤。 */
async function step(page, name, ok, evidence = '') {
  const file = await shot(page, name.replace(/[^\w一-龥-]+/g, '_'));
  results.push([ok ? 'PASS' : 'FAIL', name, evidence, file]);
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name} | ${evidence} | ${file}`);
  if (!ok) throw new Stop(name);
}

async function attempt(page, name, action, evidence) {
  try {
    await action();
  } catch (failure) {
    if (failure instanceof Stop) throw failure;
    await step(page, name, false, String(failure.message || failure).split('\n')[0].slice(0, 300));
  }
  await step(page, name, true, typeof evidence === 'function' ? await evidence() : evidence || '');
}

function watch(page, label) {
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(`${label}: ${message.text().slice(0, 300)}`);
  });
  page.on('pageerror', error => consoleErrors.push(`${label} pageerror: ${String(error).slice(0, 300)}`));
  page.on('response', response => {
    if (response.status() >= 400) {
      const url = new URL(response.url());
      network.push(`${label}: ${response.request().method()} ${url.host}${url.pathname} -> ${response.status()}`);
    }
  });
  page.on('requestfailed', request => {
    const url = new URL(request.url());
    network.push(`${label}: ${request.method()} ${url.host}${url.pathname} FAILED ${request.failure()?.errorText}`);
  });
}

async function verificationLink(email) {
  const query = encodeURIComponent(`to:"${email}"`);
  for (let i = 0; i < 60; i++) {
    const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${query}&limit=5`)).json();
    for (const message of found.messages || []) {
      const detail = await (await fetch(`${MAILPIT}/api/v1/message/${message.ID}`)).json();
      const text = (detail.Text || '') + (detail.HTML || '');
      const match = text.match(/verify-email#challengeNo=([0-9a-f-]{36})(?:&amp;|&)secret=([A-Za-z0-9_-]{43})/);
      if (match) return `${CLIENT}/verify-email#challengeNo=${match[1]}&secret=${match[2]}`;
    }
    await new Promise(r => setTimeout(r, 1000));
  }
  return null;
}

// ---------------------------------------------------------------- Client
async function register(page, email, password) {
  await page.goto(`${CLIENT}/register`);
  await page.getByLabel('邮箱', { exact: true }).fill(email);
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByLabel('确认密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '创建账号' }).click();
  await page.getByRole('heading', { name: '请查收验证邮件' }).waitFor({ timeout: 20000 });
  await step(page, '注册', true, `email=${email}`);
  const link = await verificationLink(email);
  await step(page, 'Mailpit 收到验证邮件', !!link, link ? 'link found' : '60 秒内未收到验证邮件（本机认证邮件经 Delivery/SES，未配置时不投递）');
  await page.goto(link);
  await page.getByRole('button', { name: '验证邮箱' }).click();
  await page.getByRole('heading', { name: '邮箱验证成功' }).waitFor({ timeout: 20000 });
  await step(page, '邮箱验证', true);
}

async function login(page, email, password) {
  await page.goto(`${CLIENT}/login`);
  await page.getByLabel('邮箱', { exact: true }).fill(email);
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录' }).click();
  await page.waitForURL(/\/onboarding/, { timeout: 20000 });
  await page.getByRole('heading', { name: '我的工作区与申请' }).waitFor({ timeout: 20000 });
  await step(page, '客户登录', true, page.url());
}

async function pickFirst(select) {
  const values = await select.locator('option').evaluateAll(options => options.map(o => o.value).filter(Boolean));
  if (!values.length) throw new Error('下拉框没有可选项');
  await select.selectOption(values[0]);
  return values[0];
}

async function fillProfile(page, identifierValue) {
  const field = label => page.getByLabel(label, { exact: true });
  await field('名').fill('Browser');
  await field('姓').fill('Tester');
  await field('出生日期').fill('1990-01-15');
  await field('国籍').selectOption('AE');
  await field('居住国家 / 地区').selectOption('AE');
  await field('地址第一行').fill('1 Browser Street');
  await field('城市').fill('Dubai');
  await field('州 / 省').fill('Dubai');
  await field('邮政编码').fill('00000');
  await field('地址所在国家 / 地区').selectOption('AE');
  const type = await pickFirst(field('证件 / 登记证明类型'));
  await field('证件 / 注册编号').fill(identifierValue);
  const issuer = field('签发国家 / 地区');
  if (await issuer.evaluate(node => node.tagName === 'SELECT')) await issuer.selectOption('AE');
  await field('有效期开始').fill('2020-01-01');
  await field('有效期结束（不含当日，无固定期限可留空）').fill('2035-01-01');
  const model = await pickFirst(field('经营模式'));
  await field('预计月交易额').fill('50000');
  await field('预计客单价').fill('100');
  await field('交易币种').selectOption('USD');
  await field('业务联系邮箱').fill('browser-contact@example.test');
  await field('业务联系电话（含国际区号）').fill('+971501234567');
  await field('业务说明').fill('通过自有网站销售家居用品（浏览器验收测试数据）');
  await page.getByRole('button', { name: '保存资料' }).click();
  await page.getByText('资料已保存').waitFor({ timeout: 20000 });
  return `identifierType=${type} businessModel=${model}`;
}

async function uploadIdentity(page) {
  const row = page.locator('.material-row').filter({ hasText: '基础必需' }).first();
  await row.getByRole('button', { name: '上传文件' }).click();
  const dialog = page.locator('dialog.editor[open]');
  await dialog.locator('#material-file').setInputFiles({ name: 'identity.pdf', mimeType: 'application/pdf', buffer: PDF });
  const deadline = Date.now() + 120000;
  while (await dialog.count() && Date.now() < deadline) {
    const button = dialog.locator('button.primary');
    if (await button.isEnabled()) await button.click();
    await page.waitForTimeout(3000);
  }
  if (await dialog.count()) {
    const status = await dialog.locator('[role=status]').innerText().catch(() => '');
    throw new Error('上传对话框 120 秒未完成：' + status);
  }
  const doc = page.locator('.document-row').first();
  await doc.waitFor({ timeout: 20000 });
  const label = await doc.locator('small').innerText();
  if (!label.includes('可用')) throw new Error('材料状态：' + label);
  return `document=${label}`;
}

async function submit(page) {
  await page.getByRole('button', { name: '提交前核对' }).click();
  await page.getByText('资料和基础必需材料已齐全，可以提交。').waitFor({ timeout: 20000 });
  await page.getByRole('button', { name: '提交审核' }).click();
  await page.getByText('资料已提交，正在审核中。审核期间不能修改。').waitFor({ timeout: 30000 });
}

async function reloadUntil(page, text, seconds = 60) {
  const deadline = Date.now() + seconds * 1000;
  while (Date.now() < deadline) {
    await page.reload();
    if (await page.getByText(text).first().isVisible({ timeout: 5000 }).catch(() => false)) return true;
    await page.waitForTimeout(3000);
  }
  return false;
}

// ---------------------------------------------------------------- Operations
async function opsLogin(page, email, password) {
  await page.goto(OPERATIONS + '/');
  await page.getByLabel('Email / 初始化账号').fill(email);
  await page.getByLabel('密码').fill(password);
  // antd 会在两个汉字的按钮文字之间插入空格（“登 录”）。
  await page.getByRole('button', { name: /^登\s*录$/ }).click();
  await page.waitForLoadState('networkidle');
}

async function openCase(page, applicationId, statusLabel) {
  const deadline = Date.now() + 60000;
  while (Date.now() < deadline) {
    await page.goto(`${OPERATIONS}/client-onboarding/reviews?applicationId=${applicationId}&page=1&size=20`);
    const row = page.locator('tr.ant-table-row').filter({ hasText: statusLabel }).first();
    if (await row.isVisible({ timeout: 8000 }).catch(() => false)) {
      await row.getByRole('button', { name: /^审\s*核$/ }).click();
      await page.getByRole('heading', { name: '入驻审核详情' }).waitFor({ timeout: 20000 });
      return;
    }
    await page.waitForTimeout(3000);
  }
  throw new Error(`审核列表 60 秒内没有申请 ${applicationId} 的“${statusLabel}”任务`);
}

async function downloadMaterial(page) {
  const button = page.getByRole('button', { name: /^下载文件/ }).first();
  const download = page.waitForEvent('download', { timeout: 20000 }).catch(() => null);
  const popup = page.context().waitForEvent('page', { timeout: 20000 }).catch(() => null);
  const before = page.url();
  await button.click();
  const got = await Promise.race([download, popup, page.waitForURL(url => url.toString() !== before, { timeout: 20000 }).then(() => 'nav').catch(() => null)]);
  if (!got) throw new Error('点击下载 20 秒内既无下载也无跳转');
  if (got === 'nav') {
    const host = new URL(page.url()).host;
    await page.goBack();
    return `页面被导航到对象存储 host=${host}（未触发下载事件）`;
  }
  if (typeof got.suggestedFilename === 'function') {
    const path = await got.path();
    const head = readFileSync(path).subarray(0, 5).toString();
    if (head !== '%PDF-') throw new Error('下载内容不是 PDF：' + JSON.stringify(head));
    return `download=${got.suggestedFilename()} head=${head}`;
  }
  return `打开了新页面 host=${new URL(got.url()).host}`;
}

async function decide(page, buttonName, fields) {
  await page.getByRole('button', { name: buttonName, exact: true }).click();
  const modal = page.locator('.ant-modal').filter({ has: page.locator('.ant-modal-title') }).last();
  await modal.getByLabel('原因代码（内部）').fill(fields.reasonCode);
  if (fields.section) {
    await modal.getByLabel('需要修改的分区').click();
    await page.locator('.ant-select-item-option').filter({ hasText: fields.section }).click();
  }
  if (fields.reason) await modal.getByLabel('审核理由（内部，客户不可见）').fill(fields.reason);
  if (fields.publicNote) await modal.getByLabel(/客户可见说明/).fill(fields.publicNote);
  await modal.getByRole('button', { name: fields.confirm, exact: true }).click();
  await modal.waitFor({ state: 'hidden', timeout: 20000 });
}

// ---------------------------------------------------------------- 主流程
async function main() {
  const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
  const email = process.env.CLIENT_EMAIL || `browser-${stamp}@example.test`;
  const password = process.env.CLIENT_PASSWORD || 'Browser-Accept-' + stamp + '-pw';
  const admin = secrets();
  const browser = await chromium.launch({ headless: true });
  const clientContext = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1280, height: 900 } });
  const opsContext = await browser.newContext({ ignoreHTTPSErrors: true, locale: 'zh-CN', viewport: { width: 1440, height: 900 }, acceptDownloads: true });
  const client = await clientContext.newPage(), ops = await opsContext.newPage();
  client.on('dialog', d => d.accept());
  watch(client, 'client'); watch(ops, 'ops');
  let applicationId = null;
  try {
    if (!process.env.CLIENT_EMAIL) await register(client, email, password);
    await login(client, email, password);
    await attempt(client, '开始个人申请', async () => {
      await client.getByRole('button', { name: /以个人身份入驻/ }).click();
      await client.waitForURL(/\/onboarding\/applications\/\d+/, { timeout: 20000 });
      await client.getByRole('heading', { name: '个人入驻申请' }).waitFor({ timeout: 20000 });
      applicationId = client.url().match(/applications\/(\d+)/)[1];
    }, () => `applicationId=${applicationId}`);
    let evidence = '';
    await attempt(client, '填写并保存资料', async () => { evidence = await fillProfile(client, `BRW${stamp}`); }, () => evidence);
    await attempt(client, '上传身份证明并等待可用', async () => { evidence = await uploadIdentity(client); }, () => evidence);
    await attempt(client, '提交前核对并提交', () => submit(client), 'status=已提交');
    await attempt(client, '客户看到审核中', async () => {
      if (!await reloadUntil(client, '审核中', 40)) throw new Error('40 秒内状态未变为审核中');
    }, 'status=审核中');

    await attempt(ops, '运营登录', () => opsLogin(ops, admin.E2E_ADMIN_EMAIL, admin.E2E_ADMIN_PASSWORD), ops.url());
    await attempt(ops, '审核列表打开任务', () => openCase(ops, applicationId, '审核中'), () => ops.url());
    await attempt(ops, '下载材料', async () => { evidence = await downloadMaterial(ops); }, () => evidence);
    await attempt(ops, '退回修改（基础资料）', () => decide(ops, '退回修改', { reasonCode: 'IDENTIFIER_UNCLEAR', section: '基础资料',
      publicNote: '证件号码无法核对，请修改后重新提交。', confirm: '退回修改' }), '');

    await attempt(client, '客户看到退回', async () => {
      if (!await reloadUntil(client, '需要修改：基础资料', 60)) throw new Error('60 秒内未看到退回提示');
    }, 'banner=需要修改：基础资料');
    await attempt(client, '修改证件号并保存', async () => {
      await client.getByLabel('证件 / 注册编号', { exact: true }).fill(`BRX${stamp}`);
      await client.getByRole('button', { name: '保存资料' }).click();
      await client.getByText('资料已保存').waitFor({ timeout: 20000 });
    }, `identifier=BRX${stamp}`);
    await attempt(client, '重提', () => submit(client), 'status=已提交');

    await attempt(ops, '打开重提后的任务', () => openCase(ops, applicationId, '审核中'), () => ops.url());
    await attempt(ops, '最终拒绝', () => decide(ops, '最终拒绝', { reasonCode: 'BROWSER_ACCEPTANCE', reason: '浏览器验收：最终拒绝路径',
      publicNote: '很抱歉，本次申请未通过。', confirm: '确认最终拒绝' }), '');

    await attempt(client, '客户看到拒绝', async () => {
      if (!await reloadUntil(client, '申请未通过', 60)) throw new Error('60 秒内未看到拒绝提示');
    }, 'banner=申请未通过');
    let newId = null;
    await attempt(client, '同类型重新申请', async () => {
      await client.goto(`${CLIENT}/onboarding`);
      await client.getByRole('button', { name: /以个人身份入驻/ }).click();
      await client.waitForURL(url => /applications\/\d+/.test(url.toString()) && !url.toString().endsWith('/' + applicationId), { timeout: 20000 });
      newId = client.url().match(/applications\/(\d+)/)[1];
      await client.getByRole('heading', { name: '个人入驻申请' }).waitFor({ timeout: 20000 });
    }, () => `newApplicationId=${newId} old=${applicationId}`);
    await attempt(client, '资料带入且材料为空', async () => {
      const given = await client.getByLabel('名', { exact: true }).inputValue();
      const identifier = await client.getByLabel('证件 / 注册编号', { exact: true }).inputValue();
      const docs = await client.locator('.document-row').count();
      const status = await client.locator('.page-heading .status').innerText();
      if (given !== 'Browser' || identifier !== `BRX${stamp}` || docs !== 0 || status !== '填写中')
        throw new Error(`given=${given} identifier=${identifier} documents=${docs} status=${status}`);
    }, `given=Browser identifier=BRX${stamp} documents=0 status=填写中`);
  } catch (failure) {
    if (!(failure instanceof Stop)) {
      results.push(['FAIL', '未预期异常', String(failure.message || failure).split('\n')[0], '']);
      console.log('FAIL 未预期异常 | ' + String(failure.stack || failure).slice(0, 800));
    }
  } finally {
    await browser.close();
    console.log('\n==== 汇总 ====');
    console.log(`email=${email} applicationId=${applicationId} shots=${SHOTS}`);
    for (const [verdict, name] of results) console.log(`${verdict} ${name}`);
    console.log('\n---- HTTP >= 400 / 失败请求 ----');
    for (const line of network) console.log(line);
    console.log('\n---- 控制台错误 ----');
    for (const line of consoleErrors) console.log(line);
  }
}

await main();
