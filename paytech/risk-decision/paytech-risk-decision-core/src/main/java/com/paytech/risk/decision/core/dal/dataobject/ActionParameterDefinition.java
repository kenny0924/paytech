package com.paytech.risk.decision.core.dal.dataobject;

import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.OperandSource;
import java.time.Instant;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Action Version 允许的参数定义。
 * <li>只读数据库投影；访问方法沿用字段名（fluent），构造参数顺序与 Mapper 构造映射一致。</li>
 */
@Value
@Accessors(fluent = true)
public class ActionParameterDefinition {
    /** Action 参数定义的稳定业务标识。 */
    private final long parameterDefinitionId;

    /** 所属 Action 版本的稳定业务标识。 */
    private final long actionVersionId;

    /** Risk 受控目录中的参数编码。 */
    @NonNull
    private final String parameterCode;

    /** 参数的闭合值类型。 */
    @NonNull
    private final DecisionValueType valueType;

    /** 参数是否必需。 */
    private final boolean requiredFlag;

    /** 参数唯一允许的来源类型。 */
    @NonNull
    private final OperandSource allowedSourceType;

    /** 参数是否定义 Action 目标。 */
    private final boolean targetParameterFlag;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private final Instant createTime;
}
