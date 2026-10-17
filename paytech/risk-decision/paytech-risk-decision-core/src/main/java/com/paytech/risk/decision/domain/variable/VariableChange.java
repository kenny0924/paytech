package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.decision.domainservice.convert.variable.RiskVariablePersistenceConverter.Values;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 待写入的可变字段；不复制变量根身份或 CAS 版本。 */
@Getter
@AllArgsConstructor
public class VariableChange {
    /** 规范化显示名称。 */
    private final String variableName;
    /** 规范化说明，可空。 */
    private final String description;
    /** 已规范化的类型化值列。 */
    private final Values values;
    /** 本次目标状态。 */
    private final VariableStatus status;
}
