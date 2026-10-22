package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.IntentRequest;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.Status;
import com.paytech.acquiring.integration.payment.PaymentTerminationClient.TerminationReason;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.execution.request.TerminateIntentRequestBody;
import com.paytech.payment.api.execution.response.GetIntentResponseBody;
import com.paytech.payment.api.execution.response.TerminateIntentResponseBody;

/**
 * Payment 终止链路（按来源查询 Intent 与终止 Intent）的 HTTP Mock：按业务身份配置 DTO、404 或故障，记录请求。
 *
 * <p>
 * 终止按 intentId 匹配，按来源查询按订单 ID 匹配；未配置或来源类型不符的调用记为意外调用，由基类在清理前判定失败。
 */
@TestComponent
public class PaymentTerminationMocker implements Clear {
    /** Payment 此前已终止的决定；Payment API 未发布决定枚举，acquiring 端口也不区分该值。 */
    public static final String ALREADY_TERMINATED = "ALREADY_TERMINATED";

    private final Map<String, TerminateIntentResponseBody> terminations = new HashMap<>();
    private final Map<String, MockHttpFault> terminationFaults = new HashMap<>();
    private final Map<String, GetIntentResponseBody> sources = new HashMap<>();
    private final Map<String, MockHttpFault> sourceFaults = new HashMap<>();
    private final List<Termination> calls = new ArrayList<>();
    private final List<SourceQuery> sourceQueries = new ArrayList<>();
    private final List<String> unexpectedCalls = new ArrayList<>();

    /**
     * 一次终止请求
     *
     * @param intentId 路径中的 Intent ID
     * @param clientId 请求体中的 Client ID
     * @param reason 请求体中的终止原因 Code
     */
    public record Termination(String intentId, String clientId, String reason) {
        /** 以业务类型构造期望记录。 */
        public static Termination of(long intentId, long clientId, TerminationReason reason) {
            return new Termination(Long.toString(intentId), Long.toString(clientId), reason.getCode());
        }
    }

    /**
     * 一次按来源查询
     *
     * @param sourceType 路径中的来源类型
     * @param sourceId 路径中的来源业务 ID
     * @param clientId 查询参数中的 Client ID
     */
    public record SourceQuery(String sourceType, String sourceId, String clientId) {
        /** Commerce 订单来源的期望记录。 */
        public static SourceQuery of(long orderId, long clientId) {
            return new SourceQuery(IntentRequest.COMMERCE_ORDER_SOURCE_TYPE, Long.toString(orderId),
                Long.toString(clientId));
        }
    }

    /** 配置指定 Intent 的终止决定，响应 Intent 与请求一致。 */
    public synchronized void mockDecision(long intentId, Status decision) {
        mockDecision(intentId, decision.getCode());
    }

    /** 以 Payment 决定 Code 配置终止响应，用于端口不区分的 ALREADY_TERMINATED 或未知值负例。 */
    public synchronized void mockDecision(long intentId, String decision) {
        mockTermination(Long.toString(intentId), termination(Long.toString(intentId), decision));
    }

    /** 直接配置终止响应 DTO，允许构造错归属等负例。 */
    public synchronized void mockTermination(String intentId, TerminateIntentResponseBody response) {
        terminations.put(intentId, response);
        terminationFaults.remove(intentId);
    }

    /** 终止返回 Payment 404 Problem。 */
    public synchronized void mockTerminationNotFound(long intentId) {
        mockTerminationFault(intentId, notFound());
    }

    /** 终止返回指定 HTTP 故障，例如 Problem、断连。 */
    public synchronized void mockTerminationFault(long intentId, MockHttpFault fault) {
        terminationFaults.put(Long.toString(intentId), fault);
        terminations.remove(Long.toString(intentId));
    }

    /** 配置订单来源命中的 Intent。 */
    public synchronized void mockSource(long orderId, long intentId) {
        var body = new GetIntentResponseBody();
        body.setIntentId(Long.toString(intentId));
        body.setSourceType(IntentRequest.COMMERCE_ORDER_SOURCE_TYPE);
        body.setSourceId(Long.toString(orderId));
        body.setStatus("OPEN");
        sources.put(Long.toString(orderId), body);
        sourceFaults.remove(Long.toString(orderId));
    }

    /** 订单来源没有 Intent，返回 Payment 404 Problem。 */
    public synchronized void mockSourceNotFound(long orderId) {
        sourceFaults.put(Long.toString(orderId), notFound());
        sources.remove(Long.toString(orderId));
    }

    /** 由 Mock 终止接口调用：记录请求后返回配置的 DTO 或故障。 */
    public synchronized TerminateIntentResponseBody answerTermination(String intentId, TerminateIntentRequestBody body) {
        calls.add(new Termination(intentId, body.getClientId(), body.getReason()));
        var fault = terminationFaults.get(intentId);
        if (fault != null) {
            throw fault;
        }
        var response = terminations.get(intentId);
        if (response == null) {
            throw unexpected("未配置 Intent 的终止响应：" + intentId);
        }
        return response;
    }

    /** 由 Mock 按来源查询接口调用：记录请求后返回配置的 DTO 或故障。 */
    public synchronized GetIntentResponseBody answerSource(String sourceType, String sourceId, String clientId) {
        sourceQueries.add(new SourceQuery(sourceType, sourceId, clientId));
        if (!IntentRequest.COMMERCE_ORDER_SOURCE_TYPE.equals(sourceType)) {
            throw unexpected("来源类型不是 Commerce 订单：" + sourceType);
        }
        var fault = sourceFaults.get(sourceId);
        if (fault != null) {
            throw fault;
        }
        var response = sources.get(sourceId);
        if (response == null) {
            throw unexpected("未配置订单的按来源查询响应：" + sourceId);
        }
        return response;
    }

    /** 本例终止请求记录。 */
    public synchronized List<Termination> calls() {
        return List.copyOf(calls);
    }

    /** 本例按来源查询记录。 */
    public synchronized List<SourceQuery> sourceQueries() {
        return List.copyOf(sourceQueries);
    }

    /** 本地取消、提前拒绝和终态重提既不定位也不终止 Intent。 */
    public synchronized void assertNoCalls() {
        assertTrue(calls.isEmpty() && sourceQueries.isEmpty(),
            "不应调用 Payment 终止链路，实际终止：" + calls + "，按来源查询：" + sourceQueries);
    }

    /** 业务可能把 Mock 500 当作依赖不可用吞掉，清理前仍须让测试失败。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpectedCalls.isEmpty(), "存在未配置的 Payment 终止链路调用：" + unexpectedCalls);
    }

    /** 每例清除全部配置与记录。 */
    @Override
    public synchronized void clear() {
        terminations.clear();
        terminationFaults.clear();
        sources.clear();
        sourceFaults.clear();
        calls.clear();
        sourceQueries.clear();
        unexpectedCalls.clear();
    }

    /** 终止响应 DTO：已终止类决定的 Intent 状态为 CANCELLED，已收款为 SUCCEEDED，其余为 OPEN。 */
    public static TerminateIntentResponseBody termination(String intentId, String decision) {
        var body = new TerminateIntentResponseBody();
        body.setIntentId(intentId);
        body.setDecision(decision);
        if (Status.TERMINATED.getCode().equals(decision) || ALREADY_TERMINATED.equals(decision)) {
            body.setStatus("CANCELLED");
        } else if (Status.ALREADY_PAID.getCode().equals(decision)) {
            body.setStatus("SUCCEEDED");
        } else {
            body.setStatus("OPEN");
        }
        return body;
    }

    /** 记录意外调用并返回失败，Mock 服务据此返回 500。 */
    private AssertionError unexpected(String message) {
        unexpectedCalls.add(message);
        return new AssertionError(message);
    }

    /** Payment 合同的 404 Problem。 */
    private static MockHttpFault notFound() {
        return MockHttpFault.json(HttpStatus.NOT_FOUND.value(), new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Not Found", "支付意图不存在", "NOT_FOUND", "payment-test", "2026-09-08T00:00:00.000000Z", null));
    }
}
