package com.paytech.acquiring.integration.risk.impl;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.domain.checkout.CheckoutUpstreamProtocolException;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.CommercialFacts;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.InitializationRequest;
import com.paytech.acquiring.domain.enums.RiskDecisionResult;
import com.paytech.acquiring.domainservice.port.customer.CheckoutRiskPort;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.risk.decision.api.evaluation.RiskDecisionApi;
import com.paytech.risk.decision.api.evaluation.request.CheckoutPrepareRiskInput;
import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluateDecisionResponseBody;

import lombok.AllArgsConstructor;

/**
 * 结账风控点（CHECKOUT_PREPARE v5）的 Risk 统一评估适配：按订单 ID 评估，结论与错误转换为 Client API 合同。
 * <li>结果未知（超时、连接失败、无可用实例、5xx、409）时用同一请求体重试一次；Risk 按业务身份重放，不会重复评估。</li>
 * <li>REVIEW、DENY 返回 422 CHECKOUT_NOT_ELIGIBLE；FAILED、TIMED_OUT 与两次未知返回 503 DEPENDENCY_UNAVAILABLE。</li>
 * <li>Risk 400/422 与不合协议的响应属于对接缺陷，不重试，返回 502 UPSTREAM_PROTOCOL_ERROR，不把 Risk 的 400 透给 Client。</li>
 * <li>日志只记订单 ID、Client ID、评估 ID、结论与异常类名，不记输入与 Problem 原文。</li>
 */
@Component
@AllArgsConstructor
public class CheckoutRiskPortImpl implements CheckoutRiskPort {
    private static final Logger LOG = LoggerFactory.getLogger(CheckoutRiskPortImpl.class);

    /** Risk 评估 ID：正整数十进制字符串。 */
    private static final Pattern EVALUATION_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** Spring Cloud 阻塞式负载均衡在服务发现中没有 Risk 实例时抛出的 IllegalStateException 原文。 */
    /** Risk 服务发现名（与 app 的 RiskClientConfiguration.SERVICE_ID 一致；第 06 步改名时同步）。 */
    static final String RISK_SERVICE_ID = "risk-decision-service";

    static final String NO_INSTANCE_MESSAGE = "No instances available for " + RISK_SERVICE_ID;

    private final RiskDecisionApi riskDecisionApi;

    /** 事务外评估本订单；放行返回评估 ID，其余结论按类级说明抛出对应错误。 */
    @Override
    public long assess(InitializationRequest request, CommercialFacts facts) {
        EvaluateDecisionRequestBody body = input(request, facts).toRequest(Long.toString(request.getOrderId()));
        return decide(request, evaluateWithRetry(request, body));
    }

    /**
     * 调用评估，结果未知时以同一请求体重试一次
     * <li>两次都未知时转为 503 DEPENDENCY_UNAVAILABLE 并保留第二次的原因。</li>
     */
    EvaluateDecisionResponseBody evaluateWithRetry(InitializationRequest request, EvaluateDecisionRequestBody body) {
        try {
            return evaluate(body);
        } catch (ServiceUnavailableException | ConcurrentRequestException unknown) {
            LOG.warn("结账风控评估结果未知，同一请求重试一次: orderId={}, clientId={}, error={}", request.getOrderId(),
                request.getClientId(), unknown.getClass().getSimpleName());
        }
        try {
            return evaluate(body);
        } catch (ServiceUnavailableException | ConcurrentRequestException unknown) {
            LOG.warn("结账风控评估重试后结果仍未知: orderId={}, clientId={}, error={}", request.getOrderId(),
                request.getClientId(), unknown.getClass().getSimpleName());
            throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE, unknown);
        }
    }

    /**
     * 调用一次评估
     * <li>结果未知的异常原样抛出，由调用方决定是否重试；错误体不是 Problem 的 5xx（如代理返回的 HTML 或空体）同样视为未知。</li>
     * <li>服务发现中没有 Risk 实例时，负载均衡抛出的异常转为依赖不可用；其他运行时异常原样传播。</li>
     * <li>Risk 400、422 及其他已解码的错误或无效响应转为上游协议错误。</li>
     */
    EvaluateDecisionResponseBody evaluate(EvaluateDecisionRequestBody body) {
        try {
            return riskDecisionApi.evaluate(body);
        } catch (ServiceUnavailableException | ConcurrentRequestException unknown) {
            throw unknown;
        } catch (ErrorException invalid) {
            if (invalid.getCause() instanceof RestClientResponseException response
                && response.getStatusCode().is5xxServerError()) {
                throw new ServiceUnavailableException(invalid);
            }
            throw new CheckoutUpstreamProtocolException("Risk 结账评估响应无效", invalid);
        } catch (FailException rejected) {
            throw new CheckoutUpstreamProtocolException("Risk 拒绝结账评估请求", rejected);
        } catch (IllegalStateException failure) {
            if (!NO_INSTANCE_MESSAGE.equals(failure.getMessage())) {
                throw failure;
            }
            throw new ServiceUnavailableException(failure);
        }
    }

    /** 校验响应并按结论放行或拒绝；结论未知或评估 ID 非法视为上游协议错误。 */
    long decide(InitializationRequest request, EvaluateDecisionResponseBody response) {
        RiskDecisionResult result = EnumUtils.getEnumByCode(RiskDecisionResult.class, response.getResult());
        long evaluationId = evaluationId(response.getEvaluationId());
        if (result == null) {
            throw new CheckoutUpstreamProtocolException("Risk 结账评估结论无效");
        }
        if (result == RiskDecisionResult.ALLOW) {
            return evaluationId;
        }
        LOG.warn("结账风控未放行: orderId={}, clientId={}, evaluationId={}, result={}", request.getOrderId(),
            request.getClientId(), evaluationId, result.getCode());
        if (result == RiskDecisionResult.FAILED || result == RiskDecisionResult.TIMED_OUT) {
            throw new ServiceUnavailableException(CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        throw new FailException(CommerceCheckoutErrorCode.CHECKOUT_NOT_ELIGIBLE);
    }

    /** 评估 ID 必须是 BIGINT 范围内的正整数十进制字符串。 */
    static long evaluationId(String value) {
        if (value == null || !EVALUATION_ID.matcher(value).matches()) {
            throw new CheckoutUpstreamProtocolException("Risk 结账评估 ID 无效");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new CheckoutUpstreamProtocolException("Risk 结账评估 ID 无效", overflow);
        }
    }

    /** ID 为十进制字符串，金额为去除尾随零的主单位字符串，行数为商品明细数；Local Partner 为空时由输入类省略。 */
    static CheckoutPrepareRiskInput input(InitializationRequest request, CommercialFacts facts) {
        return new CheckoutPrepareRiskInput(Long.toString(request.getClientId()), facts.entityNo(),
            facts.entitySourceType(), facts.localPartnerId() == null ? null : facts.localPartnerId().toString(),
            request.getCountry(), request.getCurrency(), amount(request.getMerchandiseAmount()),
            amount(request.getDiscountAmount()), amount(request.getNetAmount()), facts.industryCode(), facts.mccCode(),
            Integer.toString(request.getItems().size()));
    }

    /** 主单位十进制字符串，去除尾随零且不使用科学计数法。 */
    static String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
