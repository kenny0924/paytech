package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.api.evaluation.response.EvaluateDecisionResponseBody;

/**
 * Risk 统一评估的响应 DTO：按用例配置常驻结论，或按调用顺序配置一组响应（结论、Problem 故障、延迟），并记录每次请求。
 *
 * <p>
 * 请求记录保存合成测试值，供断言 17 个输入字段；顺序响应耗尽且没有常驻结论时记为意外调用。
 */
@TestComponent
public class RiskMocker implements Clear {
    /** Mock 返回的评估 ID */
    public static final String EVALUATION_ID = "7001";

    /** Problem 响应 Header 与 Body 共用的 Request ID */
    public static final String ERROR_REQUEST_ID = "risk-mock-request";

    private final List<EvaluateDecisionRequestBody> requests = new ArrayList<>();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private Reply standing;
    private int unexpected;

    /** 配置每次调用都返回的结论。 */
    public synchronized void respond(RiskResult result, AuthenticationRequirement authentication) {
        standing = result(result, authentication);
        replies.clear();
    }

    /** 按调用顺序配置响应，取代常驻结论；耗尽后的调用为意外调用。 */
    public synchronized void replies(Reply... ordered) {
        standing = null;
        replies.clear();
        replies.addAll(List.of(ordered));
    }

    /** 评估结论；ALLOW 带认证要求，其他结论输出为空对象。 */
    public static Reply result(RiskResult result, AuthenticationRequirement authentication) {
        var body = new EvaluateDecisionResponseBody();
        body.setEvaluationId(EVALUATION_ID);
        body.setResult(result.getCode());
        body.setOutputs(authentication == null ? Map.of()
            : Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT, authentication.getCode()));
        return new Reply(body, null, Duration.ZERO);
    }

    /** 原样返回的响应 DTO，用于无效结论等负例。 */
    public static Reply body(EvaluateDecisionResponseBody body) {
        return new Reply(body, null, Duration.ZERO);
    }

    /** 合规的第一方 Problem 错误；标题为该状态的标准短语。 */
    public static Reply problem(HttpStatus status, String code) {
        var problem = new ApiProblemResponse("about:blank", status.getReasonPhrase(), "Risk Mock 错误", code,
            ERROR_REQUEST_ID, "2026-10-15T00:00:00.000000Z", null);
        return new Reply(null, MockHttpFault.json(status.value(), problem), Duration.ZERO);
    }

    /** 延迟后再返回给定响应，用于超过 Payment 读取超时。 */
    public static Reply delayed(Duration delay, Reply reply) {
        return new Reply(reply.body(), reply.fault(), delay);
    }

    /** HTTP Mock 实际调用此处：记录请求并取出本次响应。 */
    public synchronized Reply next(EvaluateDecisionRequestBody body) {
        requests.add(copy(body));
        Reply reply = replies.isEmpty() ? standing : replies.poll();
        if (reply == null) {
            unexpected++;
            throw new AssertionError("未配置的 Risk 评估");
        }
        return reply;
    }

    /** 本例收到的全部评估请求，按调用顺序。 */
    public synchronized List<EvaluateDecisionRequestBody> requests() {
        return List.copyOf(requests);
    }

    /** 本例评估过的支付尝试（业务身份），按调用顺序。 */
    public synchronized List<String> attempts() {
        return requests.stream().map(EvaluateDecisionRequestBody::getBusinessKey).toList();
    }

    /** 每例结束检查没有未配置的评估。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 Risk 评估");
    }

    @Override
    public synchronized void clear() {
        requests.clear();
        replies.clear();
        standing = null;
        unexpected = 0;
    }

    /** 复制请求，避免后续修改影响记录。 */
    static EvaluateDecisionRequestBody copy(EvaluateDecisionRequestBody body) {
        var copy = new EvaluateDecisionRequestBody();
        copy.setPointCode(body.getPointCode());
        copy.setContractVersion(body.getContractVersion());
        copy.setBusinessKey(body.getBusinessKey());
        copy.setInputs(body.getInputs() == null ? null : new LinkedHashMap<>(body.getInputs()));
        return copy;
    }

    /**
     * 一次评估响应
     *
     * @param body 正常响应 DTO，故障时为空
     * @param fault HTTP 故障，正常时为空
     * @param delay 写出响应前的延迟
     */
    public record Reply(EvaluateDecisionResponseBody body, MockHttpFault fault, Duration delay) {
        /** 是否为 Problem 故障（需要写出与 Body 一致的 Request ID Header）。 */
        public boolean isProblem() {
            return fault != null;
        }
    }
}
