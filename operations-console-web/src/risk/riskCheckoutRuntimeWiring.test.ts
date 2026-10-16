import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { AUTHENTICATION_OUTPUT_POINTS, hasAuthenticationOutput } from './riskPoints.ts';

const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');

test('只有支付尝试有认证要求输出；结账准备与未知点不显示认证相关指标', () => {
  assert.deepEqual(AUTHENTICATION_OUTPUT_POINTS, ['PAYMENT_ATTEMPT_RISK']);
  assert.equal(hasAuthenticationOutput('PAYMENT_ATTEMPT_RISK'), true);
  assert.equal(hasAuthenticationOutput('CHECKOUT_PREPARE'), false);
  assert.equal(hasAuthenticationOutput('UNKNOWN_POINT'), false);
});

test('执行发布页：认证要求变化按风控点显示，结账无生效发布追加 Commerce 提示，激活文案不限支付', () => {
  const page = source('../pages/RiskExecutionReleases.tsx');
  assert.equal(page.match(/\{hasAuthenticationOutput\(release\.pointCode\) && <Metric label="认证要求变化"/g)?.length, 2);
  assert.match(page, /pointCode === 'CHECKOUT_PREPARE' \? '，Commerce 创建结账会全部失败，请先激活基线发布' : ''/);
  assert.match(page, /对该风控点新的评估生效。/);
  assert.doesNotMatch(page, /支付评估|请切换到支付尝试/);
});

test('决策流页：保存提示按 EXECUTION_POINTS 判断，不再硬编码支付点', () => {
  const page = source('../pages/RiskDecisionFlows.tsx');
  assert.match(page, /\{EXECUTION_POINTS\.includes\(point\)\?'保存不会立即生效，需在【执行发布】中发布并激活后对该风控点的评估生效。'/);
  assert.match(page, /EXECUTION_POINTS\.includes\(draft\.pointCode\)\?'保存不会立即生效/);
  assert.doesNotMatch(page, /==='PAYMENT_ATTEMPT_RISK'\?'保存不会立即生效|对支付评估生效/);
});

test('附加动作：目录无附加动作且无已有动作时不显示；有已有动作时只保留删除并提示移除', () => {
  const editor = source('AdditionalActionEditor.tsx');
  assert.match(editor, /const unsupported = !catalog\.additionalActions\.length;\n  if \(unsupported && !actions\.length\) return null;/);
  assert.match(editor, /该风控点运行期不支持附加动作，请移除后再保存。/);
  const unsupportedBlock = editor.slice(editor.indexOf('if (unsupported) return'), editor.indexOf('</section>;'));
  assert.doesNotMatch(unsupportedBlock, /添加附加动作|<Select|<Form/);
  assert.match(unsupportedBlock, /aria-label=\{`移除附加动作 \$\{index \+ 1\}`\}/);
});

test('评估查询页：订单 ID 查结账准备，认证要求行与影子认证变化列按风控点显示，业务身份标签按点', () => {
  const page = source('../pages/RiskEvaluations.tsx');
  assert.match(page, /riskEvaluationApi\.find\(QUERY_POINTS\[kind\], id, signal\)/);
  assert.match(page, /\.\.\.\(hasAuthenticationOutput\(evaluation\.pointCode\) \? \[\{ key: 'authentication', label: '认证要求'/);
  assert.match(page, /authentication=\{hasAuthenticationOutput\(evaluation\.pointCode\)\}/);
  assert.match(page, /\.\.\.\(authentication \? \[\{ title: '认证要求变化'/);
  assert.match(page, /label: businessKeyLabel\(evaluation\.pointCode\)/);
  assert.match(page, /inputRows\(inputs, pointCode\)/);
  assert.match(page, /输入支付尝试 ID、订单 ID 或评估 ID，查看风控结论及其解释。/);
});
