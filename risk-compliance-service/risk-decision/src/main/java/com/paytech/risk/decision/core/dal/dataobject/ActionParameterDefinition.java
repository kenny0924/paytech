package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.OperandSource;
import java.time.Instant;
import java.util.Objects;

/** Action Version 允许的参数定义。 */
public record ActionParameterDefinition(
    /** Action 参数定义的稳定业务标识。 */
    long parameterDefinitionId,
    /** 所属 Action 版本的稳定业务标识。 */
    long actionVersionId,
    /** Risk 受控目录中的参数编码。 */
    String parameterCode,
    /** 参数的闭合值类型。 */
    DecisionValueType valueType,
    /** 参数是否必需。 */
    boolean requiredFlag,
    /** 参数唯一允许的来源类型。 */
    OperandSource allowedSourceType,
    /** 参数是否定义 Action 目标。 */
    boolean targetParameterFlag,
    /** 记录的 UTC 微秒创建时间。 */
    Instant createTime
) {
    public ActionParameterDefinition {
        Objects.requireNonNull(parameterCode, "parameterCode");
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(allowedSourceType, "allowedSourceType");
        Objects.requireNonNull(createTime, "createTime");
    }
}
