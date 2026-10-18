package com.paytech.compliance.integration;

import com.paytech.compliance.domain.ClientReviewResult;

/** 审核结果业务事实的出站端口；只能在数据库事务外调用。 */
public interface ClientReviewResultPublisher {
    /**
     * 发布审核结果
     * <li>收到 Broker 确认才返回；未确认、退回或超时抛出运行时异常，由补偿任务重试。</li>
     *
     * @param result 已结束的审核结果
     */
    void publish(ClientReviewResult result);
}
