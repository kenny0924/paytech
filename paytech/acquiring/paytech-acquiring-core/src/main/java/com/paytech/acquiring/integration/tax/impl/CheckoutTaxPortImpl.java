package com.paytech.acquiring.integration.tax.impl;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxCalculation;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxParties;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.TaxQuoteRequest;
import com.paytech.acquiring.domainservice.port.customer.CheckoutTaxPort;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.tax.api.TaxCalculationApi;
import com.paytech.tax.api.enums.TaxErrorCode;
import com.paytech.tax.api.response.TaxCalculationResponseBody;

import lombok.AllArgsConstructor;

/**
 * 结账报价的 tax 计算适配：事务外调用 {@code POST /tax/v1/calculations}
 * <li>READY / BLOCKED / NOT_MANAGED 都是正常结果；BLOCKED 交给调用方决定订单不可支付，不在此兜底。</li>
 * <li>TAX_CALCULATION_CONFLICT（同一订单 + revision 内容不同，通常是并发保存）返回 409 并发冲突，由调用方重新提交。</li>
 * <li>读超时 504 DEPENDENCY_TIMEOUT、连接失败与 5xx 503 DEPENDENCY_UNAVAILABLE（由客户端配置的依赖故障映射给出）；不重试，
 * 同一 revision 的再次提交由 tax 返回原计算。</li>
 * <li>其他 4xx 与不合协议的响应属于对接缺陷，返回 502 UPSTREAM_PROTOCOL_ERROR，不把 tax 的 400 透给调用方。</li>
 * <li>没有商业关系快照的订单重算时按 ID 读取原计算（GET），取回冻结的业务模式；错误转换相同。</li>
 * <li>日志只记订单号或计算 ID、revision 与 tax 错误码，不记请求与 Problem 原文。</li>
 */
@Component
@AllArgsConstructor
public class CheckoutTaxPortImpl implements CheckoutTaxPort {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutTaxPortImpl.class);

    /** tax 服务发现名（与 app 的 TaxClientConfiguration.SERVICE_ID 一致）。 */
    static final String TAX_SERVICE_ID = "tax-service";

    /** Spring Cloud 阻塞式负载均衡在服务发现中没有 tax 实例时抛出的 IllegalStateException 原文。 */
    static final String NO_INSTANCE_MESSAGE = "No instances available for " + TAX_SERVICE_ID;

    private final TaxCalculationApi taxCalculationApi;

    /** 计算一次并核对回显；错误按类级说明转换。 */
    @Override
    public TaxCalculation calculate(TaxQuoteRequest request) {
        return CheckoutTaxConverter.toCalculation(request, call(request.getOrderNo(), request.getRevision(),
            () -> taxCalculationApi.calculateTax(CheckoutTaxConverter.toRequest(request))));
    }

    /** 按 ID 读取本订单已有计算，取回其冻结的计税主体；错误转换与计算相同。 */
    @Override
    public TaxParties frozenParties(long calculationId) {
        return CheckoutTaxConverter.toParties(calculationId, call(Long.toString(calculationId), 0,
            () -> taxCalculationApi.getTaxCalculation(Long.toString(calculationId))));
    }

    /**
     * 调用一次 tax
     * <li>依赖故障映射给出的 503 / 504 原样传播；错误体不是 Problem 的 5xx 与无可用实例转为依赖不可用。</li>
     *
     * @param subject 日志用的订单号或计算 ID
     * @param revision 日志用的 revision；读取时为 0
     * @param invocation 本次 tax 调用
     */
    TaxCalculationResponseBody call(String subject, int revision, Supplier<TaxCalculationResponseBody> invocation) {
        try {
            return invocation.get();
        } catch (FailException rejected) {
            throw translate(subject, revision, rejected);
        } catch (ErrorException failure) {
            if (failure.getErrorCode() == CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE
                || failure.getErrorCode() == CommerceCheckoutErrorCode.DEPENDENCY_TIMEOUT) {
                throw failure;
            }
            if (failure.getCause() instanceof RestClientResponseException response
                && response.getStatusCode().is5xxServerError()) {
                throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, failure);
            }
            throw new CheckoutUpstreamProtocolException("tax 计算响应无效", failure);
        } catch (IllegalStateException failure) {
            if (!NO_INSTANCE_MESSAGE.equals(failure.getMessage())) {
                throw failure;
            }
            throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, failure);
        }
    }

    /** 同身份冲突转为并发冲突；其他拒绝（含读取时计算不存在）属于对接缺陷。 */
    RuntimeException translate(String subject, int revision, FailException failure) {
        LOG.warn("tax 拒绝结账报价请求: subject={}, revision={}, code={}", subject, revision, failure.getCode());
        if (failure.getErrorCode() == TaxErrorCode.TAX_CALCULATION_CONFLICT) {
            return new ConcurrentRequestException("订单报价已被并发更新，请重新提交: " + subject);
        }
        return new CheckoutUpstreamProtocolException("tax 拒绝结账报价请求: " + failure.getCode(), failure);
    }
}
