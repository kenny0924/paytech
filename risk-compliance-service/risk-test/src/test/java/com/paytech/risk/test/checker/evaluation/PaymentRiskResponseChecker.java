package com.paytech.risk.test.checker.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 支付尝试风控评估的 HTTP 响应 Checker
 * <li>核对风控结果与认证要求，并返回服务端生成的交易评估与正式评估 ID 供落库核对。</li>
 *
 * @author Kenny
 */
public final class PaymentRiskResponseChecker extends AbstractHttpResponseChecker<PaymentRiskResponseChecker> {

    /**
     * 评估身份
     *
     * @param assessmentId 交易评估 ID
     * @param evaluationId 正式评估 ID
     */
    public record Assessed(long assessmentId, long evaluationId) { }

    public PaymentRiskResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected PaymentRiskResponseChecker self() {
        return this;
    }

    /** 200 响应：风控结果与认证要求符合期望。 */
    public Assessed assertResult(String riskResult, String authenticationRequirement) {
        assertStatus(200);
        JsonNode body = body();
        assertAll("评估结果",
            () -> assertTrue(body.path("assessmentId").asString().matches("[1-9][0-9]{0,18}"), "assessmentId"),
            () -> assertTrue(body.path("evaluationId").asString().matches("[1-9][0-9]{0,18}"), "evaluationId"),
            () -> assertEquals(riskResult, body.path("riskResult").asString(), "riskResult"),
            () -> assertEquals(authenticationRequirement,
                body.path("authenticationRequirement").isNull() ? null : body.path("authenticationRequirement").asString(),
                "authenticationRequirement"));
        return new Assessed(Long.parseLong(body.path("assessmentId").asString()),
            Long.parseLong(body.path("evaluationId").asString()));
    }
}
