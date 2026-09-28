package com.paytech.commerce.integration.cct.impl;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Set;

import lombok.AllArgsConstructor;

import com.paytech.client.contract.api.checkout.CheckoutEligibilityApi;
import com.paytech.client.contract.api.checkout.CheckoutEligibilityModels.PrecheckResponseBody;
import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.commerce.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.commerce.integration.cct.CctPrecheckClient;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;

/** 解释 CCT 资格与错误；HTTP、MDC、JSON 和公共异常由 Foundation 处理。 */
@AllArgsConstructor
public final class CctPrecheckClientImpl implements CctPrecheckClient {
    private static final Set<String> REASONS = Set.of("CLIENT_NOT_ACTIVE", "CLASSIFICATION_NOT_READY", "NO_ELIGIBLE_ENTITY");
    private final CheckoutEligibilityApi api;

    @Override
    public void verify(long clientId, String country, String currency) {
        var response = precheck(clientId, country, currency);
        validateEligibility(response);
        if (!response.eligible()) {
            throw new FailException(CommerceCheckoutErrorCode.CLIENT_CONFIGURATION_NOT_READY);
        }
    }

    private PrecheckResponseBody precheck(long clientId, String country, String currency) {
        try {
            return api.precheck("commerce-service", Long.toString(clientId), country, currency);
        } catch (ConcurrentRequestException exception) {
            throw exception;
        } catch (ErrorException exception) {
            if (CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode().equals(exception.getCode())) {
                throw new CheckoutUpstreamProtocolException("CCT 返回值不符合 Checkout 合同", exception);
            }
            throw exception;
        } catch (FailException exception) {
            throw switch (exception.getCode()) {
                case "CLIENT_NOT_FOUND" -> new FailException(CommerceCheckoutErrorCode.CLIENT_CONFIGURATION_NOT_READY,
                    null, exception);
                case "CCT_CONTEXT_AMBIGUOUS" -> new ServiceUnavailableException(
                    CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, exception);
                case "CCT_CONTEXT_CHANGED" -> new ConcurrentRequestException("CCT 预检期间配置变化", exception);
                default -> new CheckoutUpstreamProtocolException("CCT 返回了预检合同以外的业务拒绝", exception);
            };
        } catch (IllegalStateException exception) {
            // Spring BlockingLoadBalancerClient 在发现结果为空时抛出此异常。
            throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, exception);
        }
    }

    private void validateEligibility(PrecheckResponseBody response) {
        if (response.evaluatedAt() == null || !response.evaluatedAt()
            .matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9](\\.[0-9]{1,6})?Z")
            || response.eligible() && response.reasonCode() != null
            || !response.eligible() && !REASONS.contains(response.reasonCode() == null ? "" : response.reasonCode())) {
            throw new CheckoutUpstreamProtocolException("CCT 基础预检响应不完整");
        }
        try {
            Instant.parse(response.evaluatedAt());
        } catch (DateTimeException exception) {
            throw new CheckoutUpstreamProtocolException("CCT 预检时间不符合合同", exception);
        }
    }
}
