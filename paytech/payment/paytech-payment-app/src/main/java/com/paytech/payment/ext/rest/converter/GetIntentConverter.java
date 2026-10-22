package com.paytech.payment.ext.rest.converter;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.payment.api.execution.response.GetIntentResponseBody;
import com.paytech.payment.execution.service.processor.request.GetIntentBySourceRequest;
import com.paytech.payment.execution.service.processor.request.GetIntentRequest;
import com.paytech.payment.execution.service.processor.response.GetIntentResponse;

/** 支付意图查询协议转换 */
@Component
public class GetIntentConverter {
    /** 将已通过入口校验的字符串 ID 转成内部请求。 */
    public GetIntentRequest convertRequest(String intentId, String clientId) {
        GetIntentRequest request = new GetIntentRequest();
        request.setIntentId(Long.valueOf(intentId));
        request.setClientId(Long.valueOf(clientId));
        return request;
    }

    /** 将已通过入口校验的来源业务与 Client ID 转成内部请求。 */
    public GetIntentBySourceRequest convertBySourceRequest(String sourceType, String sourceId, String clientId) {
        GetIntentBySourceRequest request = new GetIntentBySourceRequest();
        request.setSourceType(sourceType);
        request.setSourceId(Long.valueOf(sourceId));
        request.setClientId(Long.valueOf(clientId));
        return request;
    }

    /** 转换稳定状态代码与可空尝试 ID。 */
    public GetIntentResponseBody convertResponse(GetIntentResponse response) {
        GetIntentResponseBody body = new GetIntentResponseBody();
        body.setIntentId(response.getIntentId().toString());
        body.setSourceType(response.getSourceType());
        body.setSourceId(response.getSourceId().toString());
        body.setStatus(response.getStatus().getCode());
        body.setActiveAttemptId(Objects.toString(response.getActiveAttemptId(), null));
        body.setSuccessfulAttemptId(Objects.toString(response.getSuccessfulAttemptId(), null));
        return body;
    }
}
