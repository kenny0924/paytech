package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.runtime.CheckoutRiskAssessmentContext;

/** 结账风控评估事实的本地保存。 */
public interface CheckoutRiskAssessmentService {
    /**
     * 原子保存一次正式结账评估
     *
     * @param context 已构造的评估、输入快照、Feature 结果与轨迹
     *         <li>同一订单并发写入时由唯一索引冲突回滚，抛出并发冲突。</li>
     */
    void save(CheckoutRiskAssessmentContext context);
}
