package com.paytech.payment.execution.integration;

import com.paytech.payment.execution.domain.CardInstrumentCommand;
import com.paytech.payment.execution.domain.CardInstrumentResult;

/**
 * Execution 使用的 card-service 端口（design/05 §2.1、§2.2）：卡工具创建与一次性读卡票据，均在数据库事务外调用。
 * <li>HTTP 与响应异常按 Foundation 分类抛出：404 为 FailException(NOT_FOUND)，400 为 BizCheckFailException / InvalidParameterException，
 * 409 为 ConcurrentRequestException，网络失败、超时与 5xx 为 ServiceUnavailableException。</li>
 */
public interface PaymentCardClient {
    /**
     * 创建卡交易工具；同一 Client 同一来源提交重放返回原工具
     *
     * @param command 卡输入、Customer、保存意愿与期限上限
     * @return 工具引用、实际期限与风控属性
     */
    CardInstrumentResult createInstrument(CardInstrumentCommand command);

    /**
     * 为本次渠道资金动作签发一次性读卡票据
     *
     * @param instrumentRef Card 工具引用
     * @param attemptId 支付尝试 ID
     * @param actionId 当前资金动作 ID
     * @return 票据原值，只交给 Platform 标准 API，不落库、不记录日志
     */
    String issueMaterialTicket(String instrumentRef, long attemptId, long actionId);
}
