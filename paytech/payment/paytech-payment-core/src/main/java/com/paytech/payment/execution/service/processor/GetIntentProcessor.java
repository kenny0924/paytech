package com.paytech.payment.execution.service.processor;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.execution.core.dal.dataobject.PaymentIntent;
import com.paytech.payment.execution.domainservice.repository.PaymentIntentRepository;
import com.paytech.payment.execution.service.processor.request.GetIntentRequest;
import com.paytech.payment.execution.service.processor.response.GetIntentResponse;

import lombok.AllArgsConstructor;

/** 查询支付意图 */
@Component
@AllArgsConstructor
public class GetIntentProcessor extends AbstractProcessor<GetIntentRequest, GetIntentResponse> {
    private final PaymentIntentRepository paymentIntentRepository;

    /** 查询当前事实，跨 Client 与不存在统一返回未找到。 */
    @Override
    protected GetIntentResponse processRequest(GetIntentRequest request) {
        // 查询并校验归属
        PaymentIntent intent = paymentIntentRepository.findIntent(request.getIntentId());
        if (intent == null || !Objects.equals(intent.getClientId(), request.getClientId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付意图不存在");
        }

        // 响应处理
        return GetIntentResponse.of(intent);
    }
}
