package com.paytech.risk.decision.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;
import com.paytech.risk.decision.domain.enums.OperandSource;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/** 规则 Action 的类型化参数绑定。 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@Accessors(fluent = true)
public class RuleActionParameter {

    /** 规则 Action 参数绑定的稳定业务标识。 */
    private long parameterId;

    /** 所属规则 Action 的稳定业务标识。 */
    private long ruleActionId;

    /** 被绑定参数定义的稳定业务标识。 */
    private long parameterDefinitionId;

    /** 同一定义下从一开始的值顺序。 */
    private int valueSequence;

    /** 参数值来源类型。 */
    @NonNull
    private OperandSource sourceType;

    /** FEATURE 来源引用的版本标识；其他来源为空。 */
    private Long featureVersionId;

    /** CONTEXT 来源在所属规则版本输入合同中的字段代码；其他来源为空。 */
    private String fieldCode;

    /** STRING Literal 值；其他形状为空。 */
    private String stringValue;

    /** NUMBER Literal 的 DECIMAL(24,8) 精确值；其他形状为空。 */
    private BigDecimal numberValue;

    /** BOOLEAN Literal 值；其他形状为空。 */
    private Boolean booleanValue;

    /** DATETIME Literal 的 UTC 微秒时间；其他形状为空。 */
    private Instant datetimeValue;

    /** DURATION Literal 的毫秒值；其他形状为空。 */
    private Long durationValue;

    /** 记录的 UTC 微秒创建时间。 */
    @NonNull
    private Instant createTime;

    /** 新配置动作主体的统一变量编码；旧绑定及字面量为空。 */
    private String variableCode;

    /** 旧参数记录不携带变量编码。 */
    public RuleActionParameter(long parameterId, long ruleActionId, long parameterDefinitionId, int valueSequence,
        OperandSource sourceType, Long featureVersionId, String fieldCode, String stringValue, BigDecimal numberValue,
        Boolean booleanValue, Instant datetimeValue, Long durationValue, Instant createTime) {
        this(parameterId, ruleActionId, parameterDefinitionId, valueSequence, sourceType, featureVersionId,
            fieldCode, stringValue, numberValue, booleanValue, datetimeValue, durationValue, createTime, null);
    }

}
