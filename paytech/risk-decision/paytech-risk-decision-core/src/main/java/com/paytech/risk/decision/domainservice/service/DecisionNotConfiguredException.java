package com.paytech.risk.decision.domainservice.service;

/** 风控点在当前环境没有生效的执行发布；入口层映射为 503，调用方视为确定失败。 */
public class DecisionNotConfiguredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 记录缺少发布的风控点与环境。 */
    public DecisionNotConfiguredException(String pointCode, String environment) {
        super("决策点没有生效的执行发布: " + pointCode + ", 环境: " + environment);
    }
}
