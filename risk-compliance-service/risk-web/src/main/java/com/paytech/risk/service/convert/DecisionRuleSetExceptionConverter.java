package com.paytech.risk.service.convert;

import java.util.function.Supplier;
import com.paytech.risk.service.processor.RiskVariableManagementProcessor;

/** 在 HTTP 边界保留规则、字典和变量各自已登记的错误码。 */
public final class DecisionRuleSetExceptionConverter {
    /** 按明确的历史字段组合构造，未提供的新字段保持空值。 */
    private DecisionRuleSetExceptionConverter() { }
    /** 嵌套调用仅作异常映射，不执行其他用例。 */
    public static <T> T mapped(Supplier<T> operation) {
        return RiskVariableManagementProcessor.mapped(() -> DecisionReleaseExceptionConverter.mapped(
            () -> DecisionDictionaryExceptionConverter.mapped(operation)));
    }
}
