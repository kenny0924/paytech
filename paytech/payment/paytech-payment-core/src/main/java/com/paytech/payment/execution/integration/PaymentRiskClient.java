package com.paytech.payment.execution.integration;

import com.paytech.payment.execution.domain.PaymentRiskCommand;
import com.paytech.payment.execution.domain.PaymentRiskResult;

/**
 * Execution 使用的 Risk 支付评估端口，由 Web 组合根以 Risk 统一评估接口（RiskDecisionApi）实现，在数据库事务外调用。
 */
public interface PaymentRiskClient {
    /**
     * 评估一次支付尝试
     * <li>不重试；HTTP 与响应异常按 Foundation 分类抛出：409 为 ConcurrentRequestException，网络失败、超时与 5xx 为
     * ServiceUnavailableException，400 为 InvalidParameterException，422 与无效响应为 ErrorException。是否重试由调用方决定。</li>
     *
     * @param command 评估输入
     * @return Risk 已落库的评估结论
     */
    PaymentRiskResult evaluate(PaymentRiskCommand command);
}
