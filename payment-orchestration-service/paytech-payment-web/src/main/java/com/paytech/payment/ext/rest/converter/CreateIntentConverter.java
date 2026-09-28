package com.paytech.payment.ext.rest.converter;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.payment.api.execution.request.CreateIntentRequestBody;
import com.paytech.payment.api.execution.response.CreateIntentResponseBody;
import com.paytech.payment.execution.service.processor.request.CreateIntentRequest;
import com.paytech.payment.execution.service.processor.response.CreateIntentResponse;

/** 创建支付意图协议转换 */
@Component
public class CreateIntentConverter {

    /** 转换请求 */
    public CreateIntentRequest convertRequest(CreateIntentRequestBody body) {
        CreateIntentRequest request = new CreateIntentRequest();
        request.setClientId(Long.valueOf(body.getClientId()));
        request.setSourceType(body.getSourceType());
        request.setSourceId(Long.valueOf(body.getSourceId()));
        request.setEntityNo(body.getEntityNo());
        request.setClientPricingVersionId(Long.valueOf(body.getClientPricingVersionId()));
        request.setPartnerShareVersionId(body.getPartnerShareVersionId() == null ? null
            : Long.valueOf(body.getPartnerShareVersionId()));
        request.setPlatformChargeVersionId(body.getPlatformChargeVersionId() == null ? null
            : Long.valueOf(body.getPlatformChargeVersionId()));
        request.setIndustryCode(body.getIndustryCode());
        request.setMccCode(body.getMccCode());
        request.setConsumerCountryCode(body.getConsumerCountryCode());
        request.setAmount(Money.of(body.getCurrency(), body.getAmount()));
        request.setExpiresAt(parseExpiresAt(body.getExpiresAt()));
        return request;
    }

    /** 转换响应 */
    public CreateIntentResponseBody convertResponse(CreateIntentResponse response) {
        CreateIntentResponseBody body = new CreateIntentResponseBody();
        body.setIntentId(response.getIntentId().toString());
        body.setStatus(response.getStatus().getCode());
        return body;
    }

    /** 解析到期时间 */
    private Instant parseExpiresAt(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidParameterException("到期时间无效");
        }
    }
}
