package com.paytech.acquiring.integration.commercial.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.CommercialFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.InitializationRequest;
import com.paytech.acquiring.domainservice.port.customer.CheckoutCommercialPort;
import com.paytech.commercial.api.checkout.CheckoutContextApi;
import com.paytech.commercial.api.checkout.request.ResolveCheckoutContextRequestBody;
import com.paytech.commercial.api.checkout.response.ResolveCheckoutContextResponseBody;
import com.paytech.commercial.api.enums.CheckoutErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;

import lombok.AllArgsConstructor;

/**
 * Checkout 商业上下文的 commercial 真实解析（acquiring.commercial.resolution-mode=REAL）
 * <li>事务外调用 {@code POST /checkout-context/v1/resolutions}，Header 恰好一个 X-Caller-Service: acquiring-service。</li>
 * <li>NO_ELIGIBLE_ENTITY、CCT_CONTEXT_AMBIGUOUS 返回 422 CLIENT_CONFIGURATION_NOT_READY；CLIENT_NOT_ELIGIBLE 返回 422
 * CHECKOUT_NOT_ELIGIBLE。</li>
 * <li>CCT_CONTEXT_CHANGED 以同一请求体（同一商业时点）重试一次，仍为 CHANGED 返回 503 DEPENDENCY_UNAVAILABLE。</li>
 * <li>503 OWNER_UNAVAILABLE、其他 5xx、连接失败与无可用实例返回 503 DEPENDENCY_UNAVAILABLE，读超时返回 504 DEPENDENCY_TIMEOUT（由
 * 客户端配置的依赖故障映射给出）。</li>
 * <li>CLIENT_NOT_FOUND、INVALID_CHECKOUT_QUERY（含未来商业时点）、其他 4xx 与不合协议的响应返回 502 UPSTREAM_PROTOCOL_ERROR。</li>
 * <li>日志只记订单 ID、Client ID 与 commercial 错误码，不记请求与 Problem 原文。</li>
 */
@AllArgsConstructor
public class CheckoutCommercialPortImpl implements CheckoutCommercialPort {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutCommercialPortImpl.class);

    /** commercial 只受理的调用方标识。 */
    static final String CALLER_SERVICE = "acquiring-service";

    /** commercial 服务发现名（与 app 的 CommercialClientConfiguration.SERVICE_ID 一致）。 */
    static final String COMMERCIAL_SERVICE_ID = "commercial-service";

    /** Spring Cloud 阻塞式负载均衡在服务发现中没有 commercial 实例时抛出的 IllegalStateException 原文。 */
    static final String NO_INSTANCE_MESSAGE = "No instances available for " + COMMERCIAL_SERVICE_ID;

    private final CheckoutContextApi checkoutContextApi;

    /** 事务外解析并转换为待冻结的商业事实；错误按类级说明转换为 Client API 合同。 */
    @Override
    public CommercialFacts resolve(InitializationRequest request) {
        ResolveCheckoutContextRequestBody body = CheckoutContextConverter.toRequest(request);
        return CheckoutContextConverter.toFacts(resolveWithRetry(request, body));
    }

    /**
     * 解析一次；commercial 复核期间配置变化（CCT_CONTEXT_CHANGED）时用同一请求体重试一次
     * <li>两次都变化时转为 503 DEPENDENCY_UNAVAILABLE 并保留第二次的原因。</li>
     */
    ResolveCheckoutContextResponseBody resolveWithRetry(InitializationRequest request,
        ResolveCheckoutContextRequestBody body) {
        try {
            return resolveOnce(body);
        } catch (FailException changed) {
            if (changed.getErrorCode() != CheckoutErrorCode.CCT_CONTEXT_CHANGED) {
                throw translate(request, changed);
            }
            LOG.warn("commercial 结账上下文复核期间配置变化，同一请求重试一次: orderId={}, clientId={}",
                request.getOrderId(), request.getClientId());
        }
        try {
            return resolveOnce(body);
        } catch (FailException failure) {
            if (failure.getErrorCode() != CheckoutErrorCode.CCT_CONTEXT_CHANGED) {
                throw translate(request, failure);
            }
            LOG.warn("commercial 结账上下文重试后配置仍在变化: orderId={}, clientId={}", request.getOrderId(),
                request.getClientId());
            throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, failure);
        }
    }

    /**
     * 调用一次解析
     * <li>合同错误码与公共 4xx 原样抛出，由调用方转换；依赖故障映射给出的 503 / 504 原样传播。</li>
     * <li>错误体不是 Problem 的 5xx（如代理返回的 HTML 或空体）与服务发现中没有 commercial 实例时转为依赖不可用；其他无效响应转为上游协议错误。</li>
     */
    ResolveCheckoutContextResponseBody resolveOnce(ResolveCheckoutContextRequestBody body) {
        try {
            return checkoutContextApi.resolve(CALLER_SERVICE, body);
        } catch (FailException rejected) {
            throw rejected;
        } catch (ErrorException failure) {
            if (failure.getErrorCode() == CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE
                || failure.getErrorCode() == CommerceCheckoutErrorCode.DEPENDENCY_TIMEOUT) {
                throw failure;
            }
            if (failure.getCause() instanceof RestClientResponseException response
                && response.getStatusCode().is5xxServerError()) {
                throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, failure);
            }
            throw new CheckoutUpstreamProtocolException("commercial 结账上下文响应无效", failure);
        } catch (IllegalStateException failure) {
            if (!NO_INSTANCE_MESSAGE.equals(failure.getMessage())) {
                throw failure;
            }
            throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, failure);
        }
    }

    /** commercial 业务拒绝转换为 Client API 错误；不在映射表中的拒绝属于对接缺陷，返回上游协议错误。 */
    RuntimeException translate(InitializationRequest request, FailException failure) {
        LOG.warn("commercial 结账上下文解析被拒绝: orderId={}, clientId={}, code={}", request.getOrderId(),
            request.getClientId(), failure.getCode());
        if (failure.getErrorCode() == CheckoutErrorCode.NO_ELIGIBLE_ENTITY
            || failure.getErrorCode() == CheckoutErrorCode.CCT_CONTEXT_AMBIGUOUS) {
            return new FailException(CommerceCheckoutErrorCode.CLIENT_CONFIGURATION_NOT_READY, null, failure);
        }
        if (failure.getErrorCode() == CheckoutErrorCode.CLIENT_NOT_ELIGIBLE) {
            return new FailException(CommerceCheckoutErrorCode.CHECKOUT_NOT_ELIGIBLE, null, failure);
        }
        return new CheckoutUpstreamProtocolException("commercial 拒绝结账上下文请求: " + failure.getCode(), failure);
    }
}
