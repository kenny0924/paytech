package com.paytech.risk.decision.domain.runtime;

import java.util.Map;

import com.paytech.foundation.common.error.ErrorException;

/**
 * 某个 Feature 计算失败；携带失败前已完成的结果与失败项，供评估记录过程
 * <li>评估据此记为 FAILED + FEATURE_ERROR，不执行流程、不写累计事件。</li>
 */
public class FeatureComputationException extends ErrorException {
    /** 评估与 Feature 结果使用的失败代码。 */
    public static final String FEATURE_ERROR = "FEATURE_ERROR";

    private final transient Map<Long, FeatureValue> results;

    /**
     * 构造
     *
     * @param results 已完成的结果加失败项，按 Feature Version 索引
     * @param cause 计算异常
     */
    public FeatureComputationException(Map<Long, FeatureValue> results, Throwable cause) {
        super("Feature 计算失败", cause);
        this.results = Map.copyOf(results);
    }

    /** 已完成的结果加失败项。 */
    public Map<Long, FeatureValue> results() {
        return results;
    }
}
