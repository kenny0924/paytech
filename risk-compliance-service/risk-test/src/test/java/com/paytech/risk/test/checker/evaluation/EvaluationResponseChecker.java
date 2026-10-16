package com.paytech.risk.test.checker.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.api.evaluation.request.PaymentAttemptRiskInput;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 同步风控点统一评估的 HTTP 响应 Checker
 * <li>核对评估结论与附加输出（支付为认证要求，结账没有附加输出），返回服务端生成的评估 ID 供落库核对。</li>
 *
 * @author Kenny
 */
public final class EvaluationResponseChecker extends AbstractHttpResponseChecker<EvaluationResponseChecker> {

    /**
     * 评估身份
     *
     * @param evaluationId 正式评估 ID
     */
    public record Assessed(long evaluationId) { }

    public EvaluationResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected EvaluationResponseChecker self() {
        return this;
    }

    /**
     * 200 响应：结论符合期望且没有附加输出（结账评估、支付非放行或技术失败）
     *
     * @param result 期望结论 Code
     * @return 评估身份
     */
    public Assessed assertResult(String result) {
        return assertResult(result, null);
    }

    /**
     * 200 响应：结论与认证要求符合期望
     *
     * @param result 期望结论 Code
     * @param authenticationRequirement 期望认证要求；非放行时为空，此时附加输出必须为空对象
     * @return 评估身份
     */
    public Assessed assertResult(String result, String authenticationRequirement) {
        assertStatus(200);
        JsonNode body = body();
        Map<String, String> outputs = new TreeMap<>();
        body.path("outputs").properties().forEach(entry -> outputs.put(entry.getKey(), entry.getValue().asString()));
        assertAll("评估结论",
            () -> assertTrue(body.path("evaluationId").asString().matches("[1-9][0-9]{0,18}"), "evaluationId"),
            () -> assertEquals(result, body.path("result").asString(), "result"),
            () -> assertTrue(body.path("outputs").isObject(), "outputs"),
            () -> assertEquals(authenticationRequirement == null ? Map.of()
                : Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT, authenticationRequirement), outputs, "outputs"));
        return new Assessed(Long.parseLong(body.path("evaluationId").asString()));
    }
}
