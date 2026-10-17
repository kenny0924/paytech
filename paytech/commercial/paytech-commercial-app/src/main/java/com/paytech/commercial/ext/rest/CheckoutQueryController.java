package com.paytech.commercial.ext.rest;

import static com.paytech.commercial.api.enums.CheckoutErrorCode.CLIENT_NOT_FOUND;

import java.time.Clock;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.commercial.api.checkout.CheckoutContextApi;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;
import com.paytech.commercial.domain.CheckoutQuery;
import com.paytech.commercial.service.processor.CheckoutResolutionProcessor;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;

/** 私有 HTTP 入站；部署网络承担服务认证，Caller Header 只防止调用范围误用。 */
@RestController
@AllArgsConstructor
public class CheckoutQueryController implements CheckoutContextApi {
    private final Clock clock;
    private final CheckoutResolutionProcessor processor;

    /** 校验调用范围并转换 Wire，完整解析经公共 Processor 入口执行。 */
    @Override
    public ResolveCheckoutContextResponseBody resolve(String callerService, ResolveCheckoutContextRequestBody request) {
        authorize(callerService);
        CheckoutQuery query =
            CheckoutQuery.resolve(request.getClientId(), request.getOrderNo(), request.getConsumerCountryCode(),
                request.getCurrency(), request.getLocale(), request.getContextAsOfTime(), clock.instant());
        return CheckoutContextConverter.convert(processor.process(query));
    }

    /** Caller Header 仅检查调用范围，实际身份由部署网络保证。 */
    private void authorize(String caller) {
        if (!"commerce-service".equals(caller)) {
            throw new FailException(CLIENT_NOT_FOUND);
        }
    }

}
