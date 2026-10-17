package com.paytech.acquiring.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.cloud.client.discovery.simple.InstanceProperties;
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.configuration.RiskClientConfiguration;
import com.paytech.acquiring.domain.enums.RiskDecisionResult;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.api.evaluation.response.EvaluateDecisionResponseBody;

/**
 * Risk 统一评估的响应 DTO：按用例配置常驻结论，或按调用顺序配置一组响应（结论、Problem、断连、延迟），并记录每次请求。
 *
 * <p>
 * 顺序响应耗尽且没有常驻结论时记为意外调用；请求记录只含合成测试值。
 */
@TestComponent
public class CheckoutRiskMocker implements Clear {
    /** 默认放行的评估 ID。 */
    public static final long DEFAULT_EVALUATION_ID = 7001L;

    /** Problem 响应 Header 与 Body 共用的 Request ID。 */
    public static final String ERROR_REQUEST_ID = "risk-mock-request";

    private final ObjectProvider<SimpleDiscoveryProperties> discovery;
    private final List<EvaluateDecisionRequestBody> requests = new ArrayList<>();
    private final Deque<Reply> replies = new ArrayDeque<>();
    private Reply standing;
    private int unexpected;
    private List<InstanceProperties> removedInstances;

    /** 延迟获取测试 SimpleDiscovery 配置（Mock 端口注册后才能绑定），用于制造无可用实例故障。 */
    public CheckoutRiskMocker(ObjectProvider<SimpleDiscoveryProperties> discovery) {
        this.discovery = discovery;
    }

    /** 从服务发现中移除 risk-service 实例，使真实负载均衡走无实例路径；每例清理时恢复。 */
    public synchronized void removeInstances() {
        if (removedInstances == null) {
            removedInstances = discovery.getObject().getInstances().remove(RiskClientConfiguration.SERVICE_ID);
        }
    }

    /** 每次调用都以给定评估 ID 放行。 */
    public synchronized void allow(long evaluationId) {
        standing = result(RiskDecisionResult.ALLOW, evaluationId);
        replies.clear();
    }

    /** 按调用顺序配置响应，取代常驻结论；耗尽后的调用为意外调用。 */
    public synchronized void replies(Reply... ordered) {
        standing = null;
        replies.clear();
        replies.addAll(List.of(ordered));
    }

    /** 评估结论；结账输出恒为空对象。 */
    public static Reply result(RiskDecisionResult result, long evaluationId) {
        var body = new EvaluateDecisionResponseBody();
        body.setEvaluationId(Long.toString(evaluationId));
        body.setResult(result.getCode());
        body.setOutputs(Map.of());
        return body(body);
    }

    /** 原样返回的响应 DTO，用于结论未知、评估 ID 非法等负例。 */
    public static Reply body(EvaluateDecisionResponseBody body) {
        return new Reply(body, null, Duration.ZERO);
    }

    /** 合规的第一方 Problem 错误；标题为该状态的标准短语。 */
    public static Reply problem(HttpStatus status, String code) {
        var problem = new ApiProblemResponse("about:blank", status.getReasonPhrase(), "Risk Mock 错误", code,
            ERROR_REQUEST_ID, "2026-10-16T00:00:00.000000Z", null);
        return new Reply(null, MockHttpFault.json(status.value(), problem), Duration.ZERO);
    }

    /** 错误体不是 Problem 的 HTTP 错误，例如代理返回的 HTML 或空体。 */
    public static Reply raw(HttpStatus status, String body) {
        return new Reply(null, MockHttpFault.raw(status.value(), body), Duration.ZERO);
    }

    /** 不发送响应直接断开连接。 */
    public static Reply disconnect() {
        return new Reply(null, MockHttpFault.disconnect(), Duration.ZERO);
    }

    /** 延迟后再返回给定响应，用于超过 Commerce 的读取超时。 */
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

    /** 断言本例的评估调用次数；零次表示未调用 Risk。 */
    public synchronized void assertCalls(int count) {
        assertEquals(count, requests.size(), "Risk 评估调用次数");
    }

    /** 每例结束检查没有未配置的评估。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 Risk 评估");
    }

    /** 每例清理响应配置、调用与意外调用记录，并恢复被移除的服务实例。 */
    @Override
    public synchronized void clear() {
        if (removedInstances != null) {
            discovery.getObject().getInstances().put(RiskClientConfiguration.SERVICE_ID, removedInstances);
            removedInstances = null;
        }
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
        /** 是否为 HTTP 故障（Problem 需写出与 Body 一致的 Request ID Header）。 */
        public boolean isFault() {
            return fault != null;
        }
    }
}
