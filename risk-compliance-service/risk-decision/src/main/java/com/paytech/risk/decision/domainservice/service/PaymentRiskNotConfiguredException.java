package com.paytech.risk.decision.domainservice.service;

/**
 * 本环境没有支付决策点的生效执行发布
 * <li>不写评估；入站适配器映射为 503 PAYMENT_RISK_NOT_CONFIGURED，Payment 据此确定失败而不重试。</li>
 */
public class PaymentRiskNotConfiguredException extends RuntimeException {
    /** 记录缺少发布的决策点与环境。 */
    public PaymentRiskNotConfiguredException(String pointCode, String environment) {
        super("决策点没有生效的执行发布: " + pointCode + ", 环境: " + environment);
    }
}
