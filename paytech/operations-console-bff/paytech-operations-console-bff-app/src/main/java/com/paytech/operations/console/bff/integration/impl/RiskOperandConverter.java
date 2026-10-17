package com.paytech.operations.console.bff.integration.impl;

import com.paytech.operations.console.bff.service.model.RiskRuleSetModels;
import com.paytech.risk.api.decision.DecisionRuleSetManagementModels;

/** 规则与流程共享同一 Wire 操作数投影，不解析或计算变量。 */
final class RiskOperandConverter {
    private RiskOperandConverter() { }

    static RiskRuleSetModels.OperandRequestBody local(DecisionRuleSetManagementModels.OperandRequestBody r) {
        return new RiskRuleSetModels.OperandRequestBody(r.getOperandRole(), r.getValueSequence(), r.getSourceType(),
            r.getFieldCode(), r.getValueType(), r.getLiteralValue(), r.getVariableCode(), r.getNumericUnit(),
            r.getCurrencyCode(), r.getCurrencyVariableCode());
    }

    static DecisionRuleSetManagementModels.OperandRequestBody owner(RiskRuleSetModels.OperandRequestBody r) {
        return new DecisionRuleSetManagementModels.OperandRequestBody(r.operandRole(), r.valueSequence(), r.sourceType(),
            r.fieldCode(), r.valueType(), r.literalValue(), r.variableCode(), r.numericUnit(),
            r.currencyCode(), r.currencyVariableCode());
    }
}
