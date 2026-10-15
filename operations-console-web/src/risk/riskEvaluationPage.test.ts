import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';

const page = readFileSync(new URL('../pages/RiskEvaluations.tsx', import.meta.url), 'utf8');

test('评估查询页登记为风控只读页面并懒加载', () => {
  const registry = readFileSync(new URL('../navigation/ConsolePage.tsx', import.meta.url), 'utf8');
  assert.match(registry, /OPERATIONS_RISK_EVALUATIONS: lazy\(\(\) => import\('\.\.\/pages\/RiskEvaluations'\)/);
  const entry = pageCapabilities.find(p => p.componentKey === 'OPERATIONS_RISK_EVALUATIONS');
  assert.deepEqual(entry && { ...entry, actions: [...entry.actions] }, { componentKey: 'OPERATIONS_RISK_EVALUATIONS', displayName: '评估查询', frontendRoute: '/risk/evaluations', actions: [] });
});

test('页面状态：格式错误不发请求，404 显示空状态，403 刷新授权，影子评估隐藏影子分区并链接基线', () => {
  assert.match(page, /if \(!validId\) \{ setOutcome\(\{ state: 'invalid' \}\); return; \}/);
  assert.match(page, /if \(!id\) \{ setDraftError\(ID_FORMAT_MESSAGE\); return; \}/);
  assert.match(page, /description="没有找到该评估"/);
  assert.match(page, /status === 403\) onAuthorizationChanged\(\)/);
  assert.match(page, /\.\.\.\(shadow \? \[\] : \[\{ key: 'shadows'/);
  assert.match(page, /查看基线评估/);
  assert.match(page, /PATH_UNAVAILABLE/);
  assert.match(page, /CURRENT_VALUE_NOTE/);
  assert.match(page, /条件结构暂不可用，仍显示命中结果/);
});

test('查询条件只经地址栏传递，页面不使用浏览器存储或控制台输出受限输入', () => {
  assert.match(page, /navigate\(\{ search: evaluationQuerySearch\(next\) \}\)/);
  assert.doesNotMatch(page, /localStorage|sessionStorage|console\./);
});
