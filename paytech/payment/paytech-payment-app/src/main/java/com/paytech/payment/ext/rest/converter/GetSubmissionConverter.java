package com.paytech.payment.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.payment.api.execution.response.GetSubmissionResponseBody;
import com.paytech.payment.execution.core.action.PayActionActor;
import com.paytech.payment.execution.service.processor.request.GetSubmissionRequest;
import com.paytech.payment.execution.service.processor.response.GetSubmissionResponse;

/** 按来源提交查询的协议转换 */
@Component
public class GetSubmissionConverter {
    /** 将已通过入口校验的字符串 ID 转成内部请求。 */
    public GetSubmissionRequest convertRequest(String intentId, String sourceSubmissionId, String clientId) {
        GetSubmissionRequest request = new GetSubmissionRequest();
        request.setIntentId(Long.valueOf(intentId));
        request.setSourceSubmissionId(Long.valueOf(sourceSubmissionId));
        request.setClientId(Long.valueOf(clientId));
        return request;
    }

    /** 转换稳定状态代码；金额按币种精度写成主单位十进制字符串，时间为 UTC RFC 3339。 */
    public GetSubmissionResponseBody convertResponse(GetSubmissionResponse response) {
        GetSubmissionResponseBody body = new GetSubmissionResponseBody();
        body.setSourceSubmissionId(response.getSourceSubmissionId().toString());
        body.setIntentId(response.getIntentId().toString());
        body.setAttemptId(response.getAttemptId().toString());
        body.setPaymentMethodCode(response.getPaymentMethodCode());
        body.setStatus(response.getStatus().getCode());
        body.setCapturedAmount(PayActionActor.amountText(response.getCapturedAmount(), response.getCurrency()));
        body.setCurrency(response.getCurrency());
        if (response.getInteractionType() != null) {
            GetSubmissionResponseBody.Interaction interaction = new GetSubmissionResponseBody.Interaction();
            interaction.setType(response.getInteractionType().getCode());
            interaction.setExpiresAt(response.getInteractionExpiresAt().toString());
            body.setInteraction(interaction);
        }
        body.setFailureCode(response.getFailureCode());
        body.setObservedAt(response.getObservedAt().toString());
        return body;
    }
}
