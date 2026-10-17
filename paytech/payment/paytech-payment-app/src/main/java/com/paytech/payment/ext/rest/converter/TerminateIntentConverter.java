package com.paytech.payment.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.payment.api.execution.request.TerminateIntentRequestBody;
import com.paytech.payment.api.execution.response.TerminateIntentResponseBody;
import com.paytech.payment.execution.service.processor.request.TerminateIntentRequest;
import com.paytech.payment.execution.service.processor.response.TerminateIntentResponse;

/** 终止支付意图协议转换 */
@Component
public class TerminateIntentConverter {
    /** 转换已通过字段校验的请求。 */
    public TerminateIntentRequest convertRequest(String intentId, TerminateIntentRequestBody body) {
        TerminateIntentRequest request = new TerminateIntentRequest();
        request.setIntentId(PayConverter.parseId(intentId));
        request.setClientId(Long.valueOf(body.getClientId()));
        return request;
    }

    /** 转换当前状态与终止决定。 */
    public TerminateIntentResponseBody convertResponse(TerminateIntentResponse response) {
        TerminateIntentResponseBody body = new TerminateIntentResponseBody();
        body.setIntentId(response.getIntentId().toString());
        body.setStatus(response.getStatus().getCode());
        body.setDecision(response.getDecision().getCode());
        return body;
    }
}
