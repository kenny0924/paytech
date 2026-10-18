package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.runtime.PaymentRiskAssessmentContext;

/** 支付风控评估事实的本地保存。 */
public interface PaymentRiskAssessmentService {
    /**
     * 原子保存一次正式评估
     *
     * @param context 已构造的评估、输入快照、Feature 结果、轨迹与累计事件
     *         <li>同一支付尝试并发写入时由唯一索引冲突回滚，抛出并发冲突。</li>
     */
    void save(PaymentRiskAssessmentContext context);
}
