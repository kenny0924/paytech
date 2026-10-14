package com.paytech.payment.execution.integration;

import com.paytech.payment.execution.domain.PaymentRiskCommand;
import com.paytech.payment.execution.domain.PaymentRiskResult;

/**
 * Execution 使用的 Risk 支付评估端口，由 Web 组合根以 HTTP 客户端实现，在数据库事务外调用。
 * <li>Risk 尚未提供支付评估接口：合同为 Payment 侧草案，待 Risk 确认后迁移到 Risk 发布的 API。</li>
 */
public interface PaymentRiskClient {
    /**
     * 评估一次支付尝试
     * <li>不可用、拒绝或响应无效时抛出异常，调用方按失败关闭处理，不默认通过。</li>
     *
     * @param command 评估输入
     * @return 风控结论
     */
    PaymentRiskResult assess(PaymentRiskCommand command);
}
