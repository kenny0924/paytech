package com.paytech.risk.decision.domain.runtime;

import java.util.List;

import com.paytech.risk.decision.domain.enums.DecisionValueType;

/**
 * 条件操作数在本次评估中的取值
 *
 * @param type 值类型
 * @param value 标量规范值；无值时为空
 * @param members 集合变量的成员规范值；标量为空
 * @param currency 金额的实际币种；非金额或无法确定时为空
 */
record ResolvedValue(DecisionValueType type, Object value, List<Object> members, String currency) {

    /** 标量取值。 */
    static ResolvedValue scalar(DecisionValueType type, Object value, String currency) {
        return new ResolvedValue(type, value, null, currency);
    }
}
