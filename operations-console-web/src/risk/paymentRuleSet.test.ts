import assert from 'node:assert/strict';
import test from 'node:test';
import { editorToCreateRequest, isDenyAction, terminalActionLabels } from './ruleBuilder.ts';
import type { RuleSetEditorModel } from './ruleBuilder.ts';
import { CHECKOUT_PREPARE_AUTHORING_V1 } from './riskAuthoringCatalog.ts';
import type { AuthoringCatalog } from './riskAuthoringCatalog.ts';
import { knownPoints, mergeRiskPoints } from './riskPoints.ts';

const paymentCatalog: AuthoringCatalog = { ...CHECKOUT_PREPARE_AUTHORING_V1, descriptorCode: 'RISK_VARIABLE_AUTHORING', operandFormat: 'VARIABLE_REFERENCE', fields: [],
  actions: [{ actionCode: 'ALLOW_PAYMENT', label: 'Allow Payment' }, { actionCode: 'REVIEW_PAYMENT', label: 'Review Payment' }, { actionCode: 'DENY_PAYMENT', label: 'Deny Payment' }],
  additionalActions: [], targetBindings: [], actionParameters: [],
  variableQueryContext: { pointCode: 'PAYMENT_ATTEMPT_RISK', inputContractCode: 'PAYMENT_ATTEMPT', inputContractVersion: 1, usage: 'RULE_CONDITION' } };
const editor = (actionCode: string, reasonCode = '', reasonMessage?: string): RuleSetEditorModel => ({
  pointCode: 'PAYMENT_ATTEMPT_RISK', ruleSetCode: 'PAY_BLOCKLIST', ruleSetName: '支付黑名单', changeReason: '初始配置',
  rules: [{ id: 'r1', ruleCode: 'IP_BLOCKED', ruleName: 'IP 命中拦截名单', enabled: true, reasonCode, reasonMessage, actionCode, actionVersionNo: 1, additionalActions: [],
    condition: { id: 'c1', kind: 'CONDITION', fieldSide: 'LEFT', fieldCode: '', operator: 'EQ', values: [],
      operands: [{ operandRole: 'LEFT', valueSequence: 1, sourceType: 'VARIABLE', fieldCode: null, valueType: 'BOOLEAN', variableCode: 'IP_IN_BLOCK_LIST', literalValue: null, numericUnit: null, currencyCode: null, currencyVariableCode: null },
        { operandRole: 'RIGHT', valueSequence: 1, sourceType: 'LITERAL', fieldCode: null, valueType: 'BOOLEAN', variableCode: null, literalValue: true, numericUnit: null, currencyCode: null, currencyVariableCode: null }] } }],
});

test('payment point rule sets are built with the payment catalog and keep the selected point', () => {
  const request = editorToCreateRequest(editor('DENY_PAYMENT', 'FRAUD_LIST_IP', '命中拦截名单'), null, paymentCatalog);
  assert.equal(request.pointCode, 'PAYMENT_ATTEMPT_RISK');
  assert.equal(request.rules[0]!.action?.actionCode, 'DENY_PAYMENT');
});

test('deny rules on any point need reason code and message; non-deny rules cannot carry a message', () => {
  assert.throws(() => editorToCreateRequest(editor('DENY_PAYMENT'), null, paymentCatalog), /reject rule needs a reason code and rejection message/);
  assert.throws(() => editorToCreateRequest(editor('ALLOW_PAYMENT', '', '不应出现'), null, paymentCatalog), /Only reject rules can have a rejection message/);
  assert.equal(isDenyAction('DENY_CHECKOUT'), true);
  assert.equal(isDenyAction('REVIEW_PAYMENT'), false);
  assert.equal(terminalActionLabels.ALLOW_PAYMENT, 'PASS');
});

test('non-checkout points refuse the legacy field catalog', () => {
  assert.throws(() => editorToCreateRequest(editor('ALLOW_PAYMENT'), null, CHECKOUT_PREPARE_AUTHORING_V1), /only supports rules in the unified variable format/);
});

test('risk point catalog comes from the variable context with Chinese names for known points', () => {
  assert.deepEqual(mergeRiskPoints([{ pointCode: 'CHECKOUT_PREPARE', pointName: 'CHECKOUT_PREPARE' }, { pointCode: 'PAYMENT_ATTEMPT_RISK', pointName: 'PAYMENT_ATTEMPT_RISK' },
    { pointCode: 'PAYOUT_CREATION', pointName: 'Payout Creation' }]), [{ pointCode: 'CHECKOUT_PREPARE', pointName: 'Checkout Preparation' },
    { pointCode: 'PAYMENT_ATTEMPT_RISK', pointName: 'Payment Attempt' }, { pointCode: 'PAYOUT_CREATION', pointName: 'Payout Creation' }]);
  assert.deepEqual(mergeRiskPoints([]), knownPoints());
});
