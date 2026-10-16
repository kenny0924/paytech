/** 评估查询测试数据：BFF 页面模型（Risk 结论/解释 + 条件树）；受限输入均为合成值。 */
export const evaluationWire = {
  evaluationId: '236600000000000001', pointCode: 'PAYMENT_ATTEMPT_RISK', businessKey: '991015000000000002', mode: 'PRIMARY', result: 'ALLOW',
  reasonCode: null, reasonMessage: null, outputs: { AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' }, failureCode: null,
  releaseId: '236518950932942848', environment: 'TEST', flowId: '236518950786142208', flowCode: 'PAY_ATTEMPT_FLOW', flowVersionId: '236518950786142209', flowVersionNo: 3,
  evaluationTime: '2026-10-15T19:14:02.383274Z', elapsedMillis: 18, baselineEvaluationId: null, shadowRunId: null, pathAvailable: true,
};

const variable = (variableCode: string, variableName: string | null, valueType = 'NUMBER') =>
  ({ operandRole: 'LEFT', sourceType: 'VARIABLE', valueType, variableCode, variableName });
const blockedCard = { nodeKey: '9101', nodeType: 'PREDICATE', comparisonOperator: 'EQ', result: false,
  operands: [variable('CARD_BLOCKED', '卡指纹在黑名单', 'BOOLEAN'), { operandRole: 'RIGHT', sourceType: 'LITERAL', valueType: 'BOOLEAN', literalValue: 'true' }] };

export const explanationWire = {
  evaluationId: '236600000000000001',
  inputs: {
    paymentIntentId: '235900000000000101', riskActionId: '235900000000000102', orderId: '235900000000000103', clientId: '235900000000000104', entityNo: 'AE001',
    paymentMethodCode: 'CARD', captureMode: 'AUTOMATIC', amount: '600', currency: 'USD', industryCode: 'GAMING', mccCode: '5816', consumerCountryCode: 'AE',
    consumerReference: null, clientIpAddress: '203.0.113.9', cardFingerprint: 'fp_synthetic', cardBin: '',
  },
  pathAvailable: true,
  path: [
    { stepNo: 1, nodeKey: 'start', nodeType: 'START', nodeName: null, exitEdgeKey: 'e1', exitEdgeName: null },
    { stepNo: 2, nodeKey: 'n_block', nodeType: 'RULE_SET', nodeName: '拦截', ruleSetVersionId: '236500000000000002', ruleSetCode: 'PAY_BLOCK', ruleSetVersionNo: 2,
      matched: false, decision: null, reused: false, exitEdgeKey: 'e2', exitEdgeName: '其他情况' },
    { stepNo: 3, nodeKey: 'n_auth', nodeType: 'RULE_SET', nodeName: '认证', ruleSetVersionId: '236500000000000003', ruleSetCode: 'PAY_AUTH', ruleSetVersionNo: 1,
      matched: true, decision: 'ALLOW', reused: true, exitEdgeKey: 'e3', exitEdgeName: '需要认证' },
    { stepNo: 4, nodeKey: 'end_3ds', nodeType: 'END', nodeName: '放行（需要 3DS）', decision: 'ALLOW' },
  ],
  rules: [
    { ruleSetVersionId: '236500000000000002', ruleId: '236500000000000021', ruleCode: 'BLOCKED_CARD', ruleName: '黑名单卡', priorityNo: 10, matched: false, terminal: false,
      action: 'DENY_PAYMENT', conditionAvailable: true, condition: blockedCard as Record<string, unknown> | null },
    { ruleSetVersionId: '236500000000000002', ruleId: '236500000000000022', ruleCode: 'LARGE_AMOUNT', ruleName: '大额审核', priorityNo: 30, matched: false, terminal: false,
      action: 'REVIEW_PAYMENT', conditionAvailable: true, condition: { nodeKey: '9201', nodeType: 'PREDICATE', comparisonOperator: 'GT', result: null,
        operands: [variable('PAY_INPUT_AMOUNT', null), { operandRole: 'RIGHT', sourceType: 'LITERAL', valueType: 'NUMBER', literalValue: '5000', numericUnit: 'MONEY', currencyVariableCode: 'PAY_INPUT_CURRENCY' }] } },
    { ruleSetVersionId: '236500000000000003', ruleId: '236500000000000031', ruleCode: 'THREE_DS_AMOUNT', ruleName: '大于 500 需 3DS', priorityNo: 10, matched: true, terminal: true,
      action: 'ALLOW_PAYMENT', conditionAvailable: true, condition: { nodeKey: '9301', nodeType: 'GROUP', groupOperator: 'AND', result: true, children: [
        { nodeKey: '9302', nodeType: 'PREDICATE', comparisonOperator: 'GT', result: true,
          operands: [variable('PAY_INPUT_AMOUNT', null), { operandRole: 'RIGHT', sourceType: 'LITERAL', valueType: 'NUMBER', literalValue: '500', numericUnit: 'MONEY', currencyVariableCode: 'PAY_INPUT_CURRENCY' }] },
        { nodeKey: '9303', nodeType: 'GROUP', groupOperator: 'NOT', result: true, children: [{ ...blockedCard, nodeKey: '9304' }] },
      ] } },
  ],
  variables: [
    { variableCode: 'PAY_INPUT_AMOUNT', variableName: '支付金额', sourceType: 'INPUT', valueType: 'NUMBER', value: '600', valueBasis: 'EVALUATION' },
    { variableCode: 'PAY_INPUT_CURRENCY', variableName: '支付币种', sourceType: 'INPUT', valueType: 'STRING', value: 'USD', valueBasis: 'EVALUATION' },
    { variableCode: 'CARD_COUNT_1H', variableName: '同卡 1 小时次数', sourceType: 'VELOCITY', valueType: 'NUMBER', value: '1', valueBasis: 'EVALUATION', technicalStatus: 'SUCCEEDED', elapsedMillis: 2 },
    { variableCode: 'CARD_LIMIT', variableName: '同卡次数上限', sourceType: 'CONSTANT', valueType: 'NUMBER', value: '5', valueBasis: 'CURRENT' },
    { variableCode: 'HIGH_RISK_COUNTRIES', variableName: '高风险国家', sourceType: 'COLLECTION', valueType: 'STRING', value: null, values: ['AE', 'SA'], valueBasis: 'CURRENT' },
    { variableCode: 'CARD_BLOCKED', variableName: '卡指纹在黑名单', sourceType: 'LIST', valueType: 'BOOLEAN', value: null, valueBasis: 'EVALUATION', technicalStatus: 'FAILED', failureCode: 'FEATURE_ERROR' },
  ],
  shadows: [{ evaluationId: '236600000000000007', shadowRunId: '236600000000000100', result: 'ALLOW', decisionChanged: false, outputChanged: true, severity: 'LOW' }],
};

/** 结账准备评估：没有认证要求输出，业务身份为订单 ID；输入为结账合同 v5 的 12 个字段（非 PARTNER 来源无 Local Partner）。 */
export const checkoutEvaluationWire = {
  ...evaluationWire, evaluationId: '236600000000000201', pointCode: 'CHECKOUT_PREPARE', businessKey: '991016000000000123', result: 'DENY',
  reasonCode: 'HIGH_RISK_COUNTRY', reasonMessage: '高风险国家', outputs: {}, flowCode: 'CHECKOUT_FLOW', flowVersionNo: 2,
};
export const checkoutExplanationWire = {
  ...explanationWire, evaluationId: '236600000000000201',
  inputs: {
    clientId: '235900000000000104', entityNo: 'AE001', entitySourceType: 'PLATFORM', localPartnerId: null, consumerCountryCode: 'AE', currency: 'USD',
    merchandiseAmount: '120', discountAmount: '20', netAmount: '100', industryCode: 'GAMING', mccCode: '5816', lineCount: '3',
  },
  shadows: [{ evaluationId: '236600000000000207', shadowRunId: '236600000000000300', result: 'DENY', decisionChanged: false, outputChanged: false, severity: null }],
};
