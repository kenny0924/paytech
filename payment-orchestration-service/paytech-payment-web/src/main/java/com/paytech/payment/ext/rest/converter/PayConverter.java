package com.paytech.payment.ext.rest.converter;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.payment.api.execution.request.PayRequestBody;
import com.paytech.payment.api.execution.response.PayResponseBody;
import com.paytech.payment.execution.service.processor.request.PayRequest;
import com.paytech.payment.execution.service.processor.response.PayResponse;

/** 支付协议转换 */
@Component
public class PayConverter {

    /** 转换请求 */
    public PayRequest convertRequest(String intentId, String idempotencyKey, PayRequestBody body) {
        if (!("submission:" + body.getSourceSubmissionId()).equals(idempotencyKey)) {
            throw new InvalidParameterException("Idempotency-Key 必须对应来源提交");
        }
        PayRequest request = new PayRequest();
        request.setIntentId(parseId(intentId));
        request.setClientId(Long.valueOf(body.getClientId()));
        request.setSourceSubmissionId(Long.valueOf(body.getSourceSubmissionId()));
        request.setCaptureMode(body.getCaptureMode());
        PayRequest.PayMethod payMethod = new PayRequest.PayMethod();
        payMethod.setPaymentMethodCode(body.getPayMethod().getPaymentMethodCode());
        payMethod.setPaymentInstrumentToken(body.getPayMethod().getPaymentInstrumentToken());
        request.setPayMethod(payMethod);
        return request;
    }

    /** 转换响应 */
    public PayResponseBody convertResponse(PayResponse response) {
        PayResponseBody body = new PayResponseBody();
        body.setAttemptId(response.getAttemptId().toString());
        body.setStatus(response.getStatus().getCode());
        return body;
    }

    /** 解析路径 ID */
    static long parseId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException("支付标识必须为正整数字符串");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new InvalidParameterException("支付标识超出有效范围");
        }
    }

}
