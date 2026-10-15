package com.paytech.risk.decision.domain.runtime;

import com.paytech.risk.decision.domain.enums.DecisionValueType;

/**
 * 一次评估中一个 Feature 的结果
 *
 * @param featureVersionId Feature Version ID
 * @param executionSequence 执行顺序，从 1 开始
 * @param valueType 结果值类型
 * @param value 规范值：NUMBER 为去尾零的十进制文本，BOOLEAN 为 Boolean；本次没有该主体等无值情况或计算失败时为空
 * @param elapsedMillis 计算耗时毫秒
 * @param failureCode 计算失败代码；成功为空
 */
public record FeatureValue(long featureVersionId, int executionSequence, DecisionValueType valueType, Object value,
    int elapsedMillis, String failureCode) {

    /** 计算成功的结果。 */
    public FeatureValue(long featureVersionId, int executionSequence, DecisionValueType valueType, Object value,
        int elapsedMillis) {
        this(featureVersionId, executionSequence, valueType, value, elapsedMillis, null);
    }

    /** 是否计算失败。 */
    public boolean failed() {
        return failureCode != null;
    }
}
