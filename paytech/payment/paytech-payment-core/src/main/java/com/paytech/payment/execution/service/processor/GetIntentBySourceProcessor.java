package com.paytech.payment.execution.service.processor;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.processor.request.GetIntentBySourceRequest;
import com.paytech.payment.execution.service.processor.response.GetIntentResponse;

import lombok.AllArgsConstructor;

/**
 * 按来源业务查询支付意图
 * <li>只读当前事实，不加锁、不开事务；来源业务唯一由 uk_payment_intent_source 保证。</li>
 * <li>不存在与不属于当前 Client 统一返回未找到，与按 ID 查询一致。</li>
 */
@Component
@AllArgsConstructor
public class GetIntentBySourceProcessor extends AbstractProcessor<GetIntentBySourceRequest, GetIntentResponse> {
    private final PaymentIntentRepository paymentIntentRepository;

    @Override
    protected GetIntentResponse processRequest(GetIntentBySourceRequest request) {
        // 查询并校验归属
        PaymentIntent intent = paymentIntentRepository.findIntentBySource(request.getSourceType(),
            request.getSourceId());
        if (intent == null || !Objects.equals(intent.getClientId(), request.getClientId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在");
        }

        // 响应处理
        return GetIntentResponse.of(intent);
    }
}
